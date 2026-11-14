use std::path::Path;
use std::sync::Mutex;
use std::time::Duration;

use futures_util::StreamExt;
use serde::Serialize;
use serde_json::Value;
use tokio::io::AsyncWriteExt;
use windows_sys::Win32::Foundation::CloseHandle;
use windows_sys::Win32::System::Threading::{
    GetExitCodeProcess, OpenProcess, PROCESS_NAME_WIN32, PROCESS_QUERY_LIMITED_INFORMATION, QueryFullProcessImageNameW,
};

use crate::state::{core, runtime};
use crate::{paths, win};

pub const VERSION: &str = env!("CARGO_PKG_VERSION");
const LATEST: &str = "https://api.github.com/repos/elistrrdev/RustyClient/releases/latest";
const ASSET: &str = "RustyClient-Setup.exe";
const STILL_ACTIVE: u32 = 259;

#[derive(Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateStatus {
    pub state: String,
    pub version: String,
    pub progress: f64,
    pub error: String,
}

static STATUS: Mutex<UpdateStatus> = Mutex::new(UpdateStatus {
    state: String::new(),
    version: String::new(),
    progress: 0.0,
    error: String::new(),
});

pub fn status() -> UpdateStatus {
    STATUS.lock().unwrap().clone()
}

pub fn installing() -> bool {
    STATUS.lock().unwrap().state == "installing"
}

fn set(state: &str, version: &str, progress: f64, error: &str) {
    let mut status = STATUS.lock().unwrap();
    status.state = state.into();
    status.version = version.into();
    status.progress = progress.clamp(0.0, 1.0);
    status.error = error.into();
}

pub fn start() {
    if cfg!(feature = "test") {
        set("test", "", 0.0, "");
        return;
    }
    runtime().spawn(async {
        clean_leftovers().await;
        if game_running_elsewhere() {
            set("skipped", "", 0.0, "");
            return;
        }
        set("checking", "", 0.0, "");
        match run().await {
            Ok(Some(version)) => set("installing", &version, 1.0, ""),
            Ok(None) => {
                if STATUS.lock().unwrap().state == "checking" {
                    set("current", "", 0.0, "");
                }
            }
            Err(error) => {
                let version = STATUS.lock().unwrap().version.clone();
                set("error", &version, 0.0, &error);
            }
        }
    });
}

fn parse_version(text: &str) -> Option<Vec<u64>> {
    let text = text.trim().trim_start_matches(['v', 'V']);
    let core_part = text.split(['-', '+']).next()?;
    let parts: Option<Vec<u64>> = core_part.split('.').map(|p| p.parse().ok()).collect();
    let mut parts = parts?;
    if parts.is_empty() {
        return None;
    }
    parts.resize(3, 0);
    Some(parts)
}

fn newer(latest: &str) -> bool {
    match (parse_version(latest), parse_version(VERSION)) {
        (Some(latest), Some(current)) => latest > current,
        _ => false,
    }
}

async fn clean_leftovers() {
    let _ = tokio::fs::remove_file(std::env::temp_dir().join("RustyClient-Setup-update.exe")).await;
}

pub fn game_running_elsewhere() -> bool {
    let file = paths::game_pid_file();
    let Some(pid) = std::fs::read_to_string(&file).ok().and_then(|t| t.trim().parse::<u32>().ok()) else {
        return false;
    };
    let running = unsafe {
        let handle = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, 0, pid);
        if handle.is_null() {
            false
        } else {
            let mut code = 0u32;
            let alive = GetExitCodeProcess(handle, &mut code) != 0 && code == STILL_ACTIVE;
            let mut buffer = [0u16; 1024];
            let mut length = buffer.len() as u32;
            let named = QueryFullProcessImageNameW(handle, PROCESS_NAME_WIN32, buffer.as_mut_ptr(), &mut length) != 0;
            CloseHandle(handle);
            let image = String::from_utf16_lossy(&buffer[..length as usize]).to_ascii_lowercase();
            alive && named && (image.ends_with("\\javaw.exe") || image.ends_with("\\java.exe"))
        }
    };
    if !running {
        let _ = std::fs::remove_file(file);
    }
    running
}

fn game_busy() -> bool {
    let core = core();
    core.game.lock().unwrap().running || core.task.lock().unwrap().active
}

fn installed_here() -> bool {
    let Some(location) = crate::registry::read("InstallLocation") else {
        return false;
    };
    let exe_dir = paths::exe_dir();
    let normalise = |p: &Path| p.to_string_lossy().trim_end_matches('\\').to_ascii_lowercase();
    normalise(Path::new(&location)) == normalise(&exe_dir)
}

async fn run() -> Result<Option<String>, String> {
    if !installed_here() {
        return Ok(None);
    }
    let http = &core().http;
    let response = http
        .get(LATEST)
        .header("Accept", "application/vnd.github+json")
        .timeout(Duration::from_secs(20))
        .send()
        .await
        .map_err(|_| "Could not reach GitHub.".to_string())?;
    if !response.status().is_success() {
        return Ok(None);
    }
    let release: Value = response.json().await.map_err(|_| "GitHub sent an unreadable answer.".to_string())?;
    if release["draft"].as_bool() == Some(true) || release["prerelease"].as_bool() == Some(true) {
        return Ok(None);
    }
    let tag = release["tag_name"].as_str().unwrap_or_default().to_string();
    if !newer(&tag) {
        return Ok(None);
    }
    let version = tag.trim_start_matches(['v', 'V']).to_string();
    let Some(asset) = release["assets"]
        .as_array()
        .into_iter()
        .flatten()
        .find(|a| a["name"].as_str() == Some(ASSET))
    else {
        return Ok(None);
    };
    let url = asset["browser_download_url"].as_str().unwrap_or_default().to_string();
    let size = asset["size"].as_u64().unwrap_or(0);
    if url.is_empty() || game_busy() {
        return Ok(None);
    }

    let download = std::env::temp_dir().join("RustyClient-Setup-update.exe");

    set("downloading", &version, 0.0, "");
    if let Err(error) = fetch(&url, size, &download, &version).await {
        let _ = tokio::fs::remove_file(&download).await;
        return Err(error);
    }
    if game_busy() {
        let _ = tokio::fs::remove_file(&download).await;
        set("idle", &version, 0.0, "");
        return Ok(None);
    }
    set("installing", &version, 1.0, "");
    if let Err(error) = apply(&download) {
        let _ = std::fs::remove_file(&download);
        return Err(error);
    }
    Ok(Some(version))
}

async fn fetch(url: &str, size: u64, target: &Path, version: &str) -> Result<(), String> {
    let response = core()
        .http
        .get(url)
        .timeout(Duration::from_secs(1800))
        .send()
        .await
        .map_err(|_| "The update download failed.".to_string())?;
    if !response.status().is_success() {
        return Err("The update download failed.".into());
    }
    let total = response.content_length().unwrap_or(size).max(1);
    let mut file = tokio::fs::File::create(target)
        .await
        .map_err(|_| "Rusty Client cannot write next to itself. Move it to a folder you can write to.".to_string())?;
    let mut stream = response.bytes_stream();
    let mut written = 0u64;
    let mut head: Vec<u8> = Vec::with_capacity(2);
    while let Some(chunk) = stream.next().await {
        let chunk = chunk.map_err(|_| "The update download was interrupted.".to_string())?;
        if head.len() < 2 {
            head.extend(chunk.iter().take(2 - head.len()));
        }
        file.write_all(&chunk).await.map_err(|_| "Could not save the update.".to_string())?;
        written += chunk.len() as u64;
        set("downloading", version, written as f64 / total as f64, "");
    }
    file.flush().await.map_err(|_| "Could not save the update.".to_string())?;
    drop(file);
    if head != b"MZ" || (size > 0 && written != size) {
        return Err("The downloaded update is damaged.".into());
    }
    Ok(())
}

fn apply(download: &Path) -> Result<(), String> {
    if !win::shell_run(download, &format!("--update --after-update={}", std::process::id())) {
        return Err("The update was cancelled. It will be offered again on the next start.".into());
    }
    win::post(win::WM_APP_CLOSE);
    Ok(())
}
