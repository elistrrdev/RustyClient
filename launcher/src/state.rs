use std::sync::{Arc, Mutex, OnceLock, RwLock};

use serde::Serialize;
use tokio::sync::oneshot;

use crate::config::{AccountStore, Settings};

static CORE: OnceLock<Arc<Core>> = OnceLock::new();

pub const USER_AGENT: &str = "RustyClient/1.0 (+https://github.com/elistrrdev/RustyClient)";
pub const BROWSER_AGENT: &str =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36";

#[derive(Clone, Debug, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AuthStatus {
    pub state: String,
    pub message: String,
}

#[derive(Clone, Debug, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TaskStatus {
    pub active: bool,
    pub phase: String,
    pub detail: String,
    pub progress: f64,
    pub error: String,
}

#[derive(Clone, Debug, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Crash {
    pub id: u64,
    pub version: String,
    pub code: i32,
    pub log: Vec<String>,
    pub report: String,
}

#[derive(Clone, Debug, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GameStatus {
    pub running: bool,
    pub version: String,
    pub major: String,
    pub started: u64,
    pub crash: Option<Crash>,
}

pub struct Core {
    pub http: reqwest::Client,
    pub settings: RwLock<Settings>,
    pub accounts: Mutex<AccountStore>,
    pub auth: Mutex<AuthStatus>,
    pub task: Mutex<TaskStatus>,
    pub game: Mutex<GameStatus>,
    pub kill: Mutex<Option<oneshot::Sender<()>>>,
    pub refresh_lock: tokio::sync::Mutex<()>,
}

pub use crate::bridge::runtime;

pub fn core() -> &'static Arc<Core> {
    CORE.get_or_init(|| {
        let http = reqwest::Client::builder()
            .user_agent(USER_AGENT)
            .connect_timeout(std::time::Duration::from_secs(15))
            .timeout(std::time::Duration::from_secs(120))
            .pool_max_idle_per_host(32)
            .build()
            .expect("http client");
        Arc::new(Core {
            http,
            settings: RwLock::new(Settings::load()),
            accounts: Mutex::new(AccountStore::load()),
            auth: Mutex::new(AuthStatus {
                state: "idle".into(),
                message: String::new(),
            }),
            task: Mutex::new(TaskStatus::default()),
            game: Mutex::new(GameStatus::default()),
            kill: Mutex::new(None),
            refresh_lock: tokio::sync::Mutex::new(()),
        })
    })
}

impl Core {
    pub fn settings(&self) -> Settings {
        self.settings.read().unwrap().clone()
    }

    pub fn set_auth(&self, state: &str, message: impl Into<String>) {
        let mut auth = self.auth.lock().unwrap();
        auth.state = state.into();
        auth.message = message.into();
    }

    pub fn set_task(&self, phase: &str, detail: impl Into<String>, progress: f64) {
        let mut task = self.task.lock().unwrap();
        task.active = true;
        task.phase = phase.into();
        task.detail = detail.into();
        task.progress = progress.clamp(0.0, 1.0);
        task.error.clear();
    }

    pub fn set_progress(&self, detail: impl Into<String>, progress: f64) {
        let mut task = self.task.lock().unwrap();
        task.detail = detail.into();
        task.progress = progress.clamp(0.0, 1.0);
    }

    pub fn finish_task(&self, error: Option<String>) {
        let mut task = self.task.lock().unwrap();
        task.active = false;
        task.progress = if error.is_some() { 0.0 } else { 1.0 };
        task.error = error.unwrap_or_default();
    }
}

pub fn now_secs() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}
