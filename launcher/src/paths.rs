use std::path::{Path, PathBuf};
use std::sync::OnceLock;

static ROOT: OnceLock<PathBuf> = OnceLock::new();

pub fn init() {
    let base = std::env::var_os("APPDATA")
        .map(PathBuf::from)
        .unwrap_or_else(|| exe_dir().to_path_buf());
    let root = base.join(".rustyclient");
    let _ = std::fs::create_dir_all(&root);
    let _ = ROOT.set(root);
}

pub fn root() -> &'static Path {
    ROOT.get().expect("paths not initialised")
}

pub fn exe_dir() -> PathBuf {
    std::env::current_exe()
        .ok()
        .and_then(|p| p.parent().map(Path::to_path_buf))
        .unwrap_or_else(|| PathBuf::from("."))
}

pub fn cef_cache() -> PathBuf {
    root().join("cef")
}

pub fn cache() -> PathBuf {
    root().join("cache")
}

pub fn meta() -> PathBuf {
    root().join("meta")
}

pub fn versions() -> PathBuf {
    root().join("versions")
}

pub fn libraries() -> PathBuf {
    root().join("libraries")
}

pub fn assets() -> PathBuf {
    root().join("assets")
}

pub fn runtimes() -> PathBuf {
    root().join("runtime")
}

pub fn instances() -> PathBuf {
    root().join("instances")
}

pub fn instance(major: &str) -> PathBuf {
    instances().join(sanitize(major))
}

pub fn create_instance_folders() {
    for major in crate::content::SUPPORTED_MAJORS {
        let dir = instance(major);
        for name in ["mods", "resourcepacks", "saves", "screenshots", "config", "logs"] {
            let _ = std::fs::create_dir_all(dir.join(name));
        }
    }
}

pub fn mods(major: &str) -> PathBuf {
    instance(major).join("mods")
}

pub fn game_pid_file() -> PathBuf {
    root().join("game.pid")
}

pub fn settings_file() -> PathBuf {
    root().join("settings.json")
}

pub fn accounts_file() -> PathBuf {
    root().join("accounts.dat")
}

pub fn bundled_client_mods(major: &str) -> PathBuf {
    exe_dir().join("client").join(sanitize(major))
}

pub fn sanitize(name: &str) -> String {
    name.chars()
        .filter(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '_'))
        .collect()
}
