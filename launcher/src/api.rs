use serde::Deserialize;
use serde_json::{Value, json};

use crate::bridge::{Incoming, Reply};
use crate::config::Settings;
use crate::state::core;
use crate::{auth, content, launch, mods, paths, win};

pub async fn route(route: &str, request: &Incoming) -> Reply {
    match route {
        "/boot" => boot(),
        "/content" => Reply::json(serde_json::to_value(content::get(request.param("force") == "1").await).unwrap_or(Value::Null)),
        "/state" => Reply::json(state_json()),
        "/window/minimize" => window(win::WM_APP_MINIMIZE),
        "/window/maximize" => window(win::WM_APP_MAXIMIZE),
        "/window/close" => window(win::WM_APP_CLOSE),
        "/open" => open(request),
        "/settings" => save_settings(request),
        "/auth/login" => {
            auth::start_login();
            Reply::json(state_json())
        }
        "/auth/cancel" => {
            auth::cancel_login();
            Reply::json(state_json())
        }
        "/auth/dismiss" => dismiss_auth(),
        "/auth/select" => select_account(request),
        "/auth/remove" => remove_account(request),
        "/launch" => start_launch(request),
        "/launch/stop" => {
            launch::stop();
            Reply::json(state_json())
        }
        "/launch/dismiss" => dismiss_task(),
        "/crash/dismiss" => {
            core().game.lock().unwrap().crash = None;
            Reply::json(state_json())
        }
        "/mods" => with_major(request.param("major"), |major| Reply::json(json!({ "mods": mods::list(major) }))),
        "/mods/icon" => mod_icon(request),
        "/mods/toggle" => toggle_mod(request),
        "/mods/delete" => delete_mod(request),
        "/mods/upload" => upload_mod(request),
        _ => Reply::status(404),
    }
}

fn window(message: u32) -> Reply {
    win::post(message);
    Reply::json(json!({}))
}

fn accounts_json() -> Value {
    let store = core().accounts.lock().unwrap();
    let active = store.active().map(|a| a.uuid.clone()).unwrap_or_default();
    json!({
        "active": active,
        "list": store.accounts.iter().map(|a| json!({
            "uuid": a.uuid,
            "name": a.name,
            "skinUrl": a.skin_url,
        })).collect::<Vec<_>>(),
    })
}

fn state_json() -> Value {
    let core = core();
    json!({
        "task": core.task.lock().unwrap().clone(),
        "game": core.game.lock().unwrap().clone(),
        "auth": core.auth.lock().unwrap().clone(),
        "accounts": accounts_json(),
        "maximized": win::is_maximized(),
    })
}

fn boot() -> Reply {
    let mut state = state_json();
    state["frame"] = json!(if win::is_win11() { "win11" } else { "win10" });
    state["totalMemory"] = json!(win::total_memory_mb());
    state["settings"] = serde_json::to_value(core().settings()).unwrap_or(Value::Null);
    state["content"] = serde_json::to_value(content::cached()).unwrap_or(Value::Null);
    Reply::json(state)
}

#[derive(Deserialize)]
struct OpenRequest {
    url: Option<String>,
    folder: Option<String>,
    major: Option<String>,
    path: Option<String>,
}

fn open(request: &Incoming) -> Reply {
    let Some(open) = request.json::<OpenRequest>() else {
        return Reply::error("Invalid request.");
    };
    if let Some(url) = open.url {
        if !(url.starts_with("https://") || url.starts_with("http://")) {
            return Reply::error("Only web links can be opened.");
        }
        win::shell_open(&url);
        return Reply::json(json!({}));
    }
    let major = open.major.unwrap_or_default();
    let known = content::SUPPORTED_MAJORS.contains(&major.as_str());
    let target = match open.folder.as_deref() {
        Some("game") if known => paths::instance(&major),
        Some("mods") if known => paths::mods(&major),
        Some("logs") if known => paths::instance(&major).join("logs"),
        Some("crash") => {
            let path = std::path::PathBuf::from(open.path.unwrap_or_default());
            if !path.starts_with(paths::instances()) || !path.is_file() {
                return Reply::error("That crash report no longer exists.");
            }
            win::shell_open(&path.to_string_lossy());
            return Reply::json(json!({}));
        }
        Some("launcher") => paths::root().to_path_buf(),
        _ => return Reply::error("Unknown folder."),
    };
    let _ = std::fs::create_dir_all(&target);
    win::shell_open(&target.to_string_lossy());
    Reply::json(json!({}))
}

fn save_settings(request: &Incoming) -> Reply {
    let Some(mut incoming) = request.json::<Settings>() else {
        return Reply::error("Invalid settings.");
    };
    let core = core();
    let mut settings = core.settings.write().unwrap();
    if incoming.client_id.is_empty() {
        incoming.client_id = settings.client_id.clone();
    }
    incoming.sanitize();
    *settings = incoming;
    settings.save();
    Reply::json(serde_json::to_value(settings.clone()).unwrap_or(Value::Null))
}

fn dismiss_auth() -> Reply {
    let core = core();
    let state = core.auth.lock().unwrap().state.clone();
    if state == "error" || state == "done" {
        core.set_auth("idle", "");
    }
    Reply::json(state_json())
}

#[derive(Deserialize)]
struct AccountRequest {
    uuid: String,
}

fn select_account(request: &Incoming) -> Reply {
    if let Some(select) = request.json::<AccountRequest>() {
        let mut store = core().accounts.lock().unwrap();
        if store.accounts.iter().any(|a| a.uuid == select.uuid) {
            store.active = select.uuid;
            store.save();
        }
    }
    Reply::json(state_json())
}

fn remove_account(request: &Incoming) -> Reply {
    if let Some(remove) = request.json::<AccountRequest>() {
        let mut store = core().accounts.lock().unwrap();
        store.remove(&remove.uuid);
        store.save();
    }
    Reply::json(state_json())
}

#[derive(Deserialize)]
struct LaunchRequest {
    version: String,
}

fn start_launch(request: &Incoming) -> Reply {
    let Some(launch) = request.json::<LaunchRequest>() else {
        return Reply::error("Invalid request.");
    };
    match launch::start(launch.version) {
        Ok(()) => Reply::json(state_json()),
        Err(error) => Reply::error(error),
    }
}

fn dismiss_task() -> Reply {
    {
        let core = core();
        let mut task = core.task.lock().unwrap();
        if !task.active {
            task.error.clear();
            task.phase.clear();
            task.detail.clear();
            task.progress = 0.0;
        }
    }
    Reply::json(state_json())
}

fn with_major(major: &str, run: impl FnOnce(&str) -> Reply) -> Reply {
    if content::SUPPORTED_MAJORS.contains(&major) {
        run(major)
    } else {
        Reply::error("Unknown version.")
    }
}

fn mod_icon(request: &Incoming) -> Reply {
    let major = request.param("major");
    if !content::SUPPORTED_MAJORS.contains(&major) {
        return Reply::status(404);
    }
    match mods::icon(major, request.param("file")) {
        Some(bytes) => Reply::bytes("image/png", bytes, "max-age=600"),
        None => Reply::status(404),
    }
}

#[derive(Deserialize)]
struct ModRequest {
    major: String,
    file: String,
    enabled: Option<bool>,
}

fn toggle_mod(request: &Incoming) -> Reply {
    let Some(change) = request.json::<ModRequest>() else {
        return Reply::error("Invalid request.");
    };
    with_major(&change.major, |major| match mods::set_enabled(major, &change.file, change.enabled.unwrap_or(true)) {
        Ok(()) => Reply::json(json!({ "mods": mods::list(major) })),
        Err(error) => Reply::error(error),
    })
}

fn delete_mod(request: &Incoming) -> Reply {
    let Some(change) = request.json::<ModRequest>() else {
        return Reply::error("Invalid request.");
    };
    with_major(&change.major, |major| match mods::delete(major, &change.file) {
        Ok(()) => Reply::json(json!({ "mods": mods::list(major) })),
        Err(error) => Reply::error(error),
    })
}

fn upload_mod(request: &Incoming) -> Reply {
    with_major(request.param("major"), |major| match mods::add(major, request.param("name"), &request.body) {
        Ok(()) => Reply::json(json!({ "mods": mods::list(major) })),
        Err(error) => Reply::error(error),
    })
}
