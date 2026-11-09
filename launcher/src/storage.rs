use std::path::{Path, PathBuf};

use serde_json::{Value, json};

use crate::paths;
use crate::state::core;

const BROWSER_MARKER: &str = "clear-browser-cache";

fn size_of(path: &Path) -> u64 {
    let mut total = 0u64;
    let mut stack = vec![path.to_path_buf()];
    while let Some(dir) = stack.pop() {
        let Ok(entries) = std::fs::read_dir(&dir) else {
            if let Ok(meta) = std::fs::metadata(&dir) {
                if meta.is_file() {
                    total += meta.len();
                }
            }
            continue;
        };
        for entry in entries.filter_map(Result::ok) {
            let Ok(meta) = entry.metadata() else {
                continue;
            };
            if meta.is_dir() {
                stack.push(entry.path());
            } else {
                total += meta.len();
            }
        }
    }
    total
}

fn browser_clear_pending() -> bool {
    paths::root().join(BROWSER_MARKER).exists()
}

fn launcher_paths() -> Vec<PathBuf> {
    let mut list = vec![paths::cache(), paths::meta()];
    if !browser_clear_pending() {
        list.push(paths::cef_cache());
    }
    list
}

fn game_paths() -> Vec<PathBuf> {
    let mut list = Vec::new();
    if let Ok(entries) = std::fs::read_dir(paths::instances()) {
        for entry in entries.filter_map(Result::ok) {
            let dir = entry.path();
            for name in ["logs", "crash-reports", "debug", ".fabric"] {
                list.push(dir.join(name));
            }
        }
    }
    if let Ok(entries) = std::fs::read_dir(paths::versions()) {
        for entry in entries.filter_map(Result::ok) {
            list.push(entry.path().join("natives"));
        }
    }
    list
}

pub fn sizes() -> Value {
    json!({
        "launcher": launcher_paths().iter().map(|p| size_of(p)).sum::<u64>(),
        "game": game_paths().iter().map(|p| size_of(p)).sum::<u64>(),
        "browserPending": browser_clear_pending(),
    })
}

fn remove(path: &Path) {
    if path.is_dir() {
        let _ = std::fs::remove_dir_all(path);
    } else if path.is_file() {
        let _ = std::fs::remove_file(path);
    }
}

pub fn clear_launcher() -> Result<u64, String> {
    let before = launcher_paths().iter().map(|p| size_of(p)).sum::<u64>();
    remove(&paths::cache());
    remove(&paths::meta());
    let _ = std::fs::write(paths::root().join(BROWSER_MARKER), b"");
    let after = [paths::cache(), paths::meta()].iter().map(|p| size_of(p)).sum::<u64>();
    Ok(before.saturating_sub(after))
}

pub fn clear_game() -> Result<u64, String> {
    {
        let core = core();
        if core.game.lock().unwrap().running || core.task.lock().unwrap().active || crate::update::game_running_elsewhere() {
            return Err("Close Minecraft before clearing the game cache.".into());
        }
    }
    let list = game_paths();
    let before = list.iter().map(|p| size_of(p)).sum::<u64>();
    for path in &list {
        remove(path);
    }
    let after = list.iter().map(|p| size_of(p)).sum::<u64>();
    Ok(before.saturating_sub(after))
}

pub fn clear_browser_cache_if_requested() {
    let marker = paths::root().join(BROWSER_MARKER);
    if marker.exists() {
        remove(&paths::cef_cache());
        let _ = std::fs::remove_file(marker);
    }
}
