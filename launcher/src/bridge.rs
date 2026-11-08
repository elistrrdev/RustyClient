use std::collections::HashMap;
use std::sync::{Arc, Mutex, OnceLock};

use cef::*;
use serde_json::{Value, json};


pub const HOST: &str = "app.rustyclient.internal";

static TOKEN: OnceLock<String> = OnceLock::new();

const ICONS: &str = include_str!("../ui/icons.js");
const OUTFIT: &[u8] = include_bytes!("../ui/fonts/outfit.woff2");
const MONO: &[u8] = include_bytes!("../ui/fonts/jetbrains-mono.woff2");

#[cfg(not(feature = "setup"))]
const PAGE: &str = include_str!("../ui/index.html");
#[cfg(not(feature = "setup"))]
const STYLE: &str = include_str!("../ui/app.css");
#[cfg(not(feature = "setup"))]
const SCRIPT: &str = include_str!("../ui/app.js");

#[cfg(feature = "setup")]
const PAGE: &str = include_str!("../ui/setup.html");
#[cfg(feature = "setup")]
const STYLE: &str = include_str!("../ui/setup.css");
#[cfg(feature = "setup")]
const SCRIPT: &str = include_str!("../ui/setup.js");

pub struct Incoming {
    pub path: String,
    pub query: HashMap<String, String>,
    pub token: String,
    pub body: Vec<u8>,
}

impl Incoming {
    pub fn json<T: serde::de::DeserializeOwned>(&self) -> Option<T> {
        serde_json::from_slice(&self.body).ok()
    }

    pub fn param(&self, key: &str) -> &str {
        self.query.get(key).map(String::as_str).unwrap_or_default()
    }
}

pub struct Reply {
    pub status: i32,
    pub mime: &'static str,
    pub body: Vec<u8>,
    pub cache: &'static str,
}

impl Reply {
    pub fn json(value: Value) -> Self {
        Self {
            status: 200,
            mime: "application/json",
            body: serde_json::to_vec(&value).unwrap_or_default(),
            cache: "no-store",
        }
    }

    pub fn error(message: impl Into<String>) -> Self {
        Self {
            status: 400,
            ..Self::json(json!({ "error": message.into() }))
        }
    }

    pub fn status(status: i32) -> Self {
        Self {
            status,
            mime: "text/plain",
            body: Vec::new(),
            cache: "no-store",
        }
    }

    pub fn bytes(mime: &'static str, body: Vec<u8>, cache: &'static str) -> Self {
        Self {
            status: 200,
            mime,
            body,
            cache,
        }
    }
}

pub fn runtime() -> &'static tokio::runtime::Runtime {
    static RUNTIME: OnceLock<tokio::runtime::Runtime> = OnceLock::new();
    RUNTIME.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .worker_threads(4)
            .enable_all()
            .thread_name("rusty-worker")
            .build()
            .expect("tokio runtime")
    })
}

pub fn start_url() -> String {
    let token = TOKEN.get_or_init(|| uuid::Uuid::new_v4().simple().to_string());
    format!("https://{HOST}/#{token}")
}

pub fn register() {
    let mut factory = RustySchemeFactory::new();
    register_scheme_handler_factory(
        Some(&CefString::from("https")),
        Some(&CefString::from(HOST)),
        Some(&mut factory),
    );
}

fn static_asset(path: &str) -> Option<Reply> {
    Some(match path {
        "/" | "/index.html" => Reply::bytes("text/html", PAGE.as_bytes().to_vec(), "no-store"),
        "/app.css" => Reply::bytes("text/css", STYLE.as_bytes().to_vec(), "no-store"),
        "/app.js" => Reply::bytes("text/javascript", SCRIPT.as_bytes().to_vec(), "no-store"),
        "/icons.js" => Reply::bytes("text/javascript", ICONS.as_bytes().to_vec(), "no-store"),
        "/fonts/outfit.woff2" => Reply::bytes("font/woff2", OUTFIT.to_vec(), "max-age=31536000"),
        "/fonts/jetbrains-mono.woff2" => Reply::bytes("font/woff2", MONO.to_vec(), "max-age=31536000"),
        _ => return None,
    })
}

async fn handle(request: Incoming) -> Reply {
    if let Some(asset) = static_asset(&request.path) {
        return asset;
    }
    let Some(route) = request.path.strip_prefix("/api") else {
        return Reply::status(404);
    };
    let expected = TOKEN.get().map(String::as_str).unwrap_or_default();
    let authorised = !expected.is_empty() && (request.token == expected || request.param("t") == expected);
    if !authorised {
        return Reply::status(403);
    }
    let route = route.to_string();
    #[cfg(feature = "setup")]
    {
        crate::setup::route(&route, &request).await
    }
    #[cfg(not(feature = "setup"))]
    {
        crate::api::route(&route, &request).await
    }
}

fn read_request(request: &Request) -> Incoming {
    let url = CefString::from(&request.url()).to_string();
    let token = CefString::from(&request.header_by_name(Some(&CefString::from("x-rusty-token")))).to_string();
    let parsed = url::Url::parse(&url).ok();
    let path = parsed.as_ref().map(|u| u.path().to_string()).unwrap_or_else(|| "/".into());
    let query = parsed
        .as_ref()
        .map(|u| u.query_pairs().map(|(k, v)| (k.to_string(), v.to_string())).collect())
        .unwrap_or_default();
    let mut body = Vec::new();
    if let Some(post) = request.post_data() {
        let mut elements = Vec::with_capacity(post.element_count());
        post.elements(Some(&mut elements));
        for element in elements.into_iter().flatten() {
            let size = element.bytes_count();
            if size == 0 {
                continue;
            }
            let start = body.len();
            body.resize(start + size, 0);
            let copied = element.bytes(size, body[start..].as_mut_ptr());
            body.truncate(start + copied);
        }
    }
    Incoming {
        path,
        query,
        token,
        body,
    }
}

struct Pending {
    reply: Option<Reply>,
    offset: usize,
}

struct SendCallback(Callback);

unsafe impl Send for SendCallback {}

wrap_scheme_handler_factory! {
    struct RustySchemeFactory;

    impl SchemeHandlerFactory {
        fn create(
            &self,
            _browser: Option<&mut Browser>,
            _frame: Option<&mut Frame>,
            _scheme_name: Option<&CefString>,
            _request: Option<&mut Request>,
        ) -> Option<ResourceHandler> {
            Some(RustyResourceHandler::new(Arc::new(Mutex::new(Pending {
                reply: None,
                offset: 0,
            }))))
        }
    }
}

wrap_resource_handler! {
    struct RustyResourceHandler {
        pending: Arc<Mutex<Pending>>,
    }

    impl ResourceHandler {
        fn open(
            &self,
            request: Option<&mut Request>,
            handle_request: Option<&mut ::std::os::raw::c_int>,
            callback: Option<&mut Callback>,
        ) -> ::std::os::raw::c_int {
            let (Some(request), Some(callback)) = (request, callback) else {
                return 0;
            };
            let incoming = read_request(request);
            if let Some(handle_request) = handle_request {
                *handle_request = 0;
            }
            let pending = self.pending.clone();
            let callback = SendCallback(callback.clone());
            runtime().spawn(async move {
                let callback = callback;
                let reply = handle(incoming).await;
                pending.lock().unwrap().reply = Some(reply);
                callback.0.cont();
            });
            1
        }

        fn response_headers(
            &self,
            response: Option<&mut Response>,
            response_length: Option<&mut i64>,
            _redirect_url: Option<&mut CefString>,
        ) {
            let pending = self.pending.lock().unwrap();
            let Some(reply) = pending.reply.as_ref() else {
                return;
            };
            if let Some(response) = response {
                response.set_status(reply.status);
                response.set_mime_type(Some(&CefString::from(reply.mime)));
                if reply.mime.starts_with("text/") || reply.mime == "application/json" {
                    response.set_charset(Some(&CefString::from("utf-8")));
                }
                response.set_header_by_name(
                    Some(&CefString::from("Cache-Control")),
                    Some(&CefString::from(reply.cache)),
                    1,
                );
            }
            if let Some(length) = response_length {
                *length = reply.body.len() as i64;
            }
        }

        fn read(
            &self,
            data_out: *mut u8,
            bytes_to_read: ::std::os::raw::c_int,
            bytes_read: Option<&mut ::std::os::raw::c_int>,
            _callback: Option<&mut ResourceReadCallback>,
        ) -> ::std::os::raw::c_int {
            let mut pending = self.pending.lock().unwrap();
            let offset = pending.offset;
            let Some(reply) = pending.reply.as_ref() else {
                return 0;
            };
            let remaining = reply.body.len().saturating_sub(offset);
            let count = remaining.min(bytes_to_read.max(0) as usize);
            if count == 0 || data_out.is_null() {
                if let Some(bytes_read) = bytes_read {
                    *bytes_read = 0;
                }
                return 0;
            }
            unsafe {
                std::ptr::copy_nonoverlapping(reply.body[offset..].as_ptr(), data_out, count);
            }
            pending.offset += count;
            if let Some(bytes_read) = bytes_read {
                *bytes_read = count as i32;
            }
            1
        }

        fn cancel(&self) {}
    }
}
