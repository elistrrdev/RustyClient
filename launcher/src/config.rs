use crate::{paths, win};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

pub const DEFAULT_ACCENT: &str = "#1ab0dd";

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct Settings {
    pub memory_mb: u32,
    pub custom_resolution: bool,
    pub width: u32,
    pub height: u32,
    pub fullscreen: bool,
    pub jvm_args: String,
    pub on_launch: String,
    pub close_on_exit: bool,
    pub language: String,
    pub accent: String,
    pub selected_major: String,
    pub selected_versions: HashMap<String, String>,
    pub client_id: String,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            memory_mb: default_memory(),
            custom_resolution: false,
            width: 1280,
            height: 720,
            fullscreen: false,
            jvm_args: String::new(),
            on_launch: "hide".into(),
            close_on_exit: false,
            language: String::new(),
            accent: DEFAULT_ACCENT.into(),
            selected_major: String::new(),
            selected_versions: HashMap::new(),
            client_id: uuid::Uuid::new_v4().simple().to_string(),
        }
    }
}

fn default_memory() -> u32 {
    let total = win::total_memory_mb();
    if total >= 16 * 1024 {
        6144
    } else if total >= 8 * 1024 {
        4096
    } else {
        2048
    }
}

impl Settings {
    pub fn load() -> Self {
        let mut settings: Settings = std::fs::read(paths::settings_file())
            .ok()
            .and_then(|bytes| serde_json::from_slice(&bytes).ok())
            .unwrap_or_default();
        settings.sanitize();
        settings.save();
        settings
    }

    pub fn save(&self) {
        if let Ok(json) = serde_json::to_vec_pretty(self) {
            let _ = write_atomic(&paths::settings_file(), &json);
        }
    }

    pub fn sanitize(&mut self) {
        let total = win::total_memory_mb().max(2048);
        self.memory_mb = self.memory_mb.clamp(1024, total);
        self.width = self.width.clamp(320, 15360);
        self.height = self.height.clamp(240, 8640);
        if !matches!(self.on_launch.as_str(), "keep" | "minimize" | "hide") {
            self.on_launch = "hide".into();
        }
        if !is_hex_color(&self.accent) {
            self.accent = DEFAULT_ACCENT.into();
        }
        if self.client_id.is_empty() {
            self.client_id = uuid::Uuid::new_v4().simple().to_string();
        }
        self.jvm_args = self.jvm_args.replace(['\r', '\n'], " ");
        if !is_language_code(&self.language) {
            self.language.clear();
        }
    }
}

fn is_language_code(value: &str) -> bool {
    let Some((lang, region)) = value.split_once('_') else {
        return false;
    };
    (2..=3).contains(&lang.len())
        && (2..=4).contains(&region.len())
        && value.chars().all(|c| c.is_ascii_lowercase() || c == '_')
}

fn is_hex_color(value: &str) -> bool {
    value.len() == 7 && value.starts_with('#') && value[1..].chars().all(|c| c.is_ascii_hexdigit())
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Account {
    pub uuid: String,
    pub name: String,
    pub skin_url: String,
    pub xuid: String,
    pub mc_token: String,
    pub mc_expires: u64,
    pub msa_refresh: String,
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct AccountStore {
    pub active: String,
    pub accounts: Vec<Account>,
}

impl AccountStore {
    pub fn load() -> Self {
        let Ok(bytes) = std::fs::read(paths::accounts_file()) else {
            return Self::default();
        };
        win::unprotect(&bytes)
            .and_then(|plain| serde_json::from_slice::<AccountStore>(&plain).ok())
            .unwrap_or_default()
    }

    pub fn save(&self) {
        let Ok(json) = serde_json::to_vec(self) else {
            return;
        };
        if let Some(sealed) = win::protect(&json) {
            let _ = write_atomic(&paths::accounts_file(), &sealed);
        }
    }

    pub fn active(&self) -> Option<&Account> {
        self.accounts
            .iter()
            .find(|a| a.uuid == self.active)
            .or_else(|| self.accounts.first())
    }

    pub fn upsert(&mut self, account: Account) {
        self.active = account.uuid.clone();
        match self.accounts.iter_mut().find(|a| a.uuid == account.uuid) {
            Some(existing) => *existing = account,
            None => self.accounts.push(account),
        }
    }

    pub fn remove(&mut self, uuid: &str) {
        self.accounts.retain(|a| a.uuid != uuid);
        if self.active == uuid {
            self.active = self.accounts.first().map(|a| a.uuid.clone()).unwrap_or_default();
        }
    }
}

pub fn write_atomic(path: &std::path::Path, bytes: &[u8]) -> std::io::Result<()> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    let mut tmp_name = path.file_name().unwrap_or_default().to_os_string();
    tmp_name.push(".tmp");
    let tmp = path.with_file_name(tmp_name);
    std::fs::write(&tmp, bytes)?;
    std::fs::rename(&tmp, path)
}
