use std::cell::{Cell, RefCell};
use std::path::PathBuf;
use std::sync::OnceLock;
#[cfg(feature = "setup")]
use std::sync::{Arc, Mutex};

use cef::*;
use windows_sys::Win32::Foundation::HWND;
use windows_sys::Win32::UI::WindowsAndMessaging::{WS_MAXIMIZEBOX, WS_MINIMIZEBOX};

use crate::{auth, bridge, win};

static START_URL: OnceLock<String> = OnceLock::new();
static WINDOW: OnceLock<win::WindowSpec> = OnceLock::new();
#[cfg(feature = "setup")]
static BROWSE: Mutex<Option<(String, tokio::sync::oneshot::Sender<Option<String>>)>> = Mutex::new(None);

thread_local! {
    static CLIENT: RefCell<Option<Client>> = const { RefCell::new(None) };
    static MAIN_BROWSER: RefCell<Option<Browser>> = const { RefCell::new(None) };
    static LOGIN_BROWSER: RefCell<Option<Browser>> = const { RefCell::new(None) };
    static MAIN_ID: Cell<i32> = const { Cell::new(0) };
    static LOGIN_ID: Cell<i32> = const { Cell::new(0) };
    static LOGIN_PENDING: Cell<bool> = const { Cell::new(false) };
}

pub fn run(main_args: &MainArgs, spec: win::WindowSpec, root_cache: PathBuf) {
    let _ = START_URL.set(bridge::start_url());
    let _ = WINDOW.set(spec);
    let cache = root_cache.join("default");
    let _ = std::fs::create_dir_all(&cache);

    let runtime_dir = crate::runtime::dir();
    let settings = Settings {
        no_sandbox: 1,
        resources_dir_path: CefString::from(runtime_dir.to_string_lossy().as_ref()),
        locales_dir_path: CefString::from(runtime_dir.join("locales").to_string_lossy().as_ref()),
        root_cache_path: CefString::from(root_cache.to_string_lossy().as_ref()),
        cache_path: CefString::from(cache.to_string_lossy().as_ref()),
        persist_session_cookies: 1,
        log_severity: LogSeverity::DISABLE,
        background_color: 0xff131211,
        locale: CefString::from("en-US"),
        accept_language_list: CefString::from("en-US,en"),
        ..Default::default()
    };

    let mut app = RustyApp::new();
    if initialize(Some(main_args), Some(&settings), Some(&mut app), std::ptr::null_mut()) != 1 {
        return;
    }
    run_message_loop();
    CLIENT.with(|c| c.borrow_mut().take());
    shutdown();
}

fn browser_hwnd(browser: &Browser) -> HWND {
    browser
        .host()
        .map(|host| host.window_handle().0 as HWND)
        .unwrap_or(std::ptr::null_mut())
}

pub fn focus_main_browser() {
    MAIN_BROWSER.with(|b| {
        if let Some(host) = b.borrow().as_ref().and_then(|browser| browser.host()) {
            host.set_focus(1);
        }
    });
}

pub fn request_close() -> bool {
    MAIN_BROWSER.with(|b| match b.borrow().as_ref().and_then(|browser| browser.host()) {
        Some(host) => {
            host.close_browser(0);
            true
        }
        None => false,
    })
}

pub fn on_main_window_destroyed() {
    if MAIN_BROWSER.with(|b| b.borrow().is_none()) {
        quit_message_loop();
    }
}

pub fn open_login_window() {
    let existing = LOGIN_BROWSER.with(|b| b.borrow().clone());
    if let Some(browser) = existing {
        let hwnd = browser_hwnd(&browser);
        if !hwnd.is_null() {
            unsafe {
                windows_sys::Win32::UI::WindowsAndMessaging::SetForegroundWindow(hwnd);
            }
        }
        return;
    }
    if LOGIN_PENDING.with(|p| p.get()) {
        return;
    }
    let Some(mut client) = CLIENT.with(|c| c.borrow().clone()) else {
        auth::deliver(Err("The sign-in window could not be opened.".into()));
        return;
    };
    let (x, y, width, height) = win::centered_popup_bounds(520, 680);
    let parent = cef::sys::HWND(win::main_hwnd().cast());
    let mut info = WindowInfo {
        runtime_style: RuntimeStyle::ALLOY,
        ..Default::default()
    }
    .set_as_popup(parent, "Sign in to Microsoft");
    info.style &= !(WS_MAXIMIZEBOX | WS_MINIMIZEBOX);
    info.bounds = Rect { x, y, width, height };
    let settings = BrowserSettings {
        background_color: 0xffffffff,
        ..Default::default()
    };
    let url = CefString::from(auth::authorize_url().as_str());
    LOGIN_PENDING.with(|p| p.set(true));
    if browser_host_create_browser(Some(&info), Some(&mut client), Some(&url), Some(&settings), None, None) != 1 {
        LOGIN_PENDING.with(|p| p.set(false));
        auth::deliver(Err("The sign-in window could not be opened.".into()));
    }
}

#[cfg(feature = "setup")]
pub fn request_folder(default: String) -> tokio::sync::oneshot::Receiver<Option<String>> {
    let (tx, rx) = tokio::sync::oneshot::channel();
    *BROWSE.lock().unwrap() = Some((default, tx));
    win::post(win::WM_APP_BROWSE);
    rx
}

#[cfg(feature = "setup")]
pub fn open_folder_dialog() {
    let Some((default, tx)) = BROWSE.lock().unwrap().take() else {
        return;
    };
    let sender = Arc::new(Mutex::new(Some(tx)));
    let host = MAIN_BROWSER.with(|b| b.borrow().as_ref().and_then(|browser| browser.host()));
    let Some(host) = host else {
        if let Some(tx) = sender.lock().unwrap().take() {
            let _ = tx.send(None);
        }
        return;
    };
    let mut callback = FolderPicked::new(sender);
    host.run_file_dialog(
        FileDialogMode::OPEN_FOLDER,
        Some(&CefString::from("Choose where to install Rusty Client")),
        Some(&CefString::from(default.as_str())),
        None,
        Some(&mut callback),
    );
}

#[cfg(feature = "setup")]
wrap_run_file_dialog_callback! {
    struct FolderPicked {
        sender: Arc<Mutex<Option<tokio::sync::oneshot::Sender<Option<String>>>>>,
    }

    impl RunFileDialogCallback {
        fn on_file_dialog_dismissed(&self, file_paths: Option<&mut CefStringList>) {
            let picked = file_paths.and_then(|list| list.clone().into_iter().next()).filter(|p| !p.is_empty());
            if let Some(tx) = self.sender.lock().unwrap().take() {
                let _ = tx.send(picked);
            }
        }
    }
}

pub fn close_login_window() {
    LOGIN_BROWSER.with(|b| {
        if let Some(host) = b.borrow().as_ref().and_then(|browser| browser.host()) {
            host.close_browser(1);
        }
    });
}

wrap_app! {
    struct RustyApp;

    impl App {
        fn on_before_command_line_processing(
            &self,
            process_type: Option<&CefString>,
            command_line: Option<&mut CommandLine>,
        ) {
            let is_browser = process_type.map(|p| p.to_string().is_empty()).unwrap_or(true);
            let Some(command_line) = command_line else {
                return;
            };
            if is_browser {
                command_line.append_switch(Some(&CefString::from("disable-pinch")));
                command_line.append_switch(Some(&CefString::from("disable-spell-checking")));
                command_line.append_switch_with_value(
                    Some(&CefString::from("overscroll-history-navigation")),
                    Some(&CefString::from("0")),
                );
            }
        }

        fn browser_process_handler(&self) -> Option<BrowserProcessHandler> {
            Some(RustyBrowserProcessHandler::new())
        }
    }
}

wrap_browser_process_handler! {
    struct RustyBrowserProcessHandler;

    impl BrowserProcessHandler {
        fn on_context_initialized(&self) {
            bridge::register();
            let Some(spec) = WINDOW.get() else {
                return;
            };
            let hwnd = win::create_main_window(spec);
            let mut client = RustyClientHandler::new();
            CLIENT.with(|c| *c.borrow_mut() = Some(client.clone()));
            let (width, height) = win::client_size(hwnd);
            let info = WindowInfo {
                runtime_style: RuntimeStyle::ALLOY,
                ..Default::default()
            }
            .set_as_child(
                cef::sys::HWND(hwnd.cast()),
                &Rect {
                    x: 0,
                    y: 0,
                    width,
                    height,
                },
            );
            let settings = BrowserSettings {
                background_color: 0xff131211,
                ..Default::default()
            };
            let url = CefString::from(START_URL.get().map(String::as_str).unwrap_or("about:blank"));
            browser_host_create_browser(Some(&info), Some(&mut client), Some(&url), Some(&settings), None, None);
        }
    }
}

wrap_client! {
    struct RustyClientHandler;

    impl Client {
        fn life_span_handler(&self) -> Option<LifeSpanHandler> {
            Some(RustyLifeSpanHandler::new())
        }

        fn display_handler(&self) -> Option<DisplayHandler> {
            Some(RustyDisplayHandler::new())
        }

        fn load_handler(&self) -> Option<LoadHandler> {
            Some(RustyLoadHandler::new())
        }

        fn drag_handler(&self) -> Option<DragHandler> {
            Some(RustyDragHandler::new())
        }

        fn context_menu_handler(&self) -> Option<ContextMenuHandler> {
            Some(RustyContextMenuHandler::new())
        }
    }
}

fn is_main(browser: &Browser) -> bool {
    let id = browser.identifier();
    id != 0 && MAIN_ID.with(|m| m.get()) == id
}

fn is_login(browser: &Browser) -> bool {
    let id = browser.identifier();
    id != 0 && LOGIN_ID.with(|m| m.get()) == id
}

wrap_life_span_handler! {
    struct RustyLifeSpanHandler;

    impl LifeSpanHandler {
        fn on_before_popup(
            &self,
            _browser: Option<&mut Browser>,
            _frame: Option<&mut Frame>,
            _popup_id: ::std::os::raw::c_int,
            target_url: Option<&CefString>,
            _target_frame_name: Option<&CefString>,
            _target_disposition: WindowOpenDisposition,
            _user_gesture: ::std::os::raw::c_int,
            _popup_features: Option<&PopupFeatures>,
            _window_info: Option<&mut WindowInfo>,
            _client: Option<&mut Option<Client>>,
            _settings: Option<&mut BrowserSettings>,
            _extra_info: Option<&mut Option<DictionaryValue>>,
            _no_javascript_access: Option<&mut ::std::os::raw::c_int>,
        ) -> ::std::os::raw::c_int {
            if let Some(url) = target_url.map(|u| u.to_string()) {
                if url.starts_with("https://") || url.starts_with("http://") {
                    win::shell_open(&url);
                }
            }
            1
        }

        fn on_after_created(&self, browser: Option<&mut Browser>) {
            let Some(browser) = browser else {
                return;
            };
            let browser = browser.clone();
            if MAIN_BROWSER.with(|b| b.borrow().is_none()) {
                MAIN_ID.with(|m| m.set(browser.identifier()));
                let hwnd = browser_hwnd(&browser);
                MAIN_BROWSER.with(|b| *b.borrow_mut() = Some(browser));
                win::set_browser_hwnd(hwnd);
            } else {
                LOGIN_ID.with(|m| m.set(browser.identifier()));
                LOGIN_PENDING.with(|p| p.set(false));
                let hwnd = browser_hwnd(&browser);
                if !hwnd.is_null() {
                    win::apply_dark_frame(hwnd);
                }
                LOGIN_BROWSER.with(|b| *b.borrow_mut() = Some(browser));
            }
        }

        fn do_close(&self, browser: Option<&mut Browser>) -> ::std::os::raw::c_int {
            if let Some(browser) = browser {
                if is_main(browser) {
                    win::ALLOW_CLOSE.store(true, std::sync::atomic::Ordering::SeqCst);
                }
            }
            0
        }

        fn on_before_close(&self, browser: Option<&mut Browser>) {
            let Some(browser) = browser else {
                return;
            };
            if is_main(browser) {
                close_login_window();
                MAIN_BROWSER.with(|b| b.borrow_mut().take());
                quit_message_loop();
            } else if is_login(browser) {
                LOGIN_BROWSER.with(|b| b.borrow_mut().take());
                LOGIN_ID.with(|m| m.set(0));
                auth::deliver(Err(auth::CANCELLED.into()));
            }
        }
    }
}

wrap_display_handler! {
    struct RustyDisplayHandler;

    impl DisplayHandler {
        fn on_address_change(
            &self,
            browser: Option<&mut Browser>,
            frame: Option<&mut Frame>,
            url: Option<&CefString>,
        ) {
            let (Some(browser), Some(frame), Some(url)) = (browser, frame, url) else {
                return;
            };
            if !is_login(browser) || frame.is_main() == 0 {
                return;
            }
            let url = url.to_string();
            if let Some(result) = auth::parse_redirect(&url) {
                auth::deliver(result);
                if let Some(host) = browser.host() {
                    host.close_browser(1);
                }
            }
        }
    }
}

wrap_load_handler! {
    struct RustyLoadHandler;

    impl LoadHandler {
        fn on_load_end(
            &self,
            browser: Option<&mut Browser>,
            frame: Option<&mut Frame>,
            _http_status_code: ::std::os::raw::c_int,
        ) {
            let (Some(browser), Some(frame)) = (browser, frame) else {
                return;
            };
            if is_main(browser) && frame.is_main() != 0 {
                win::show_main_window();
            }
        }
    }
}

wrap_drag_handler! {
    struct RustyDragHandler;

    impl DragHandler {
        fn on_draggable_regions_changed(
            &self,
            browser: Option<&mut Browser>,
            _frame: Option<&mut Frame>,
            regions: Option<&[DraggableRegion]>,
        ) {
            let Some(browser) = browser else {
                return;
            };
            if !is_main(browser) {
                return;
            }
            let list = regions
                .unwrap_or(&[])
                .iter()
                .map(|r| (r.bounds.x, r.bounds.y, r.bounds.width, r.bounds.height, r.draggable != 0))
                .collect();
            win::set_drag_regions(list);
        }
    }
}

wrap_context_menu_handler! {
    struct RustyContextMenuHandler;

    impl ContextMenuHandler {
        fn on_before_context_menu(
            &self,
            _browser: Option<&mut Browser>,
            _frame: Option<&mut Frame>,
            params: Option<&mut ContextMenuParams>,
            model: Option<&mut MenuModel>,
        ) {
            let editable = params.map(|p| p.is_editable() != 0).unwrap_or(false);
            if let Some(model) = model {
                if !editable {
                    model.clear();
                }
            }
        }
    }
}
