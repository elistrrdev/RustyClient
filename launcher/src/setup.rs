use std::io::{Read, Write};
use std::os::windows::process::CommandExt;
use std::path::{Path, PathBuf};
use std::sync::{Mutex, OnceLock};

use cef::MainArgs;
use serde::{Deserialize, Serialize};
use serde_json::json;
use windows_sys::Win32::Storage::FileSystem::GetDiskFreeSpaceExW;
use windows_sys::Win32::UI::Shell::ShellExecuteW;
use windows_sys::Win32::UI::WindowsAndMessaging::{FindWindowW, SW_SHOWNORMAL};

use crate::bridge::{Incoming, Reply};
use crate::registry::{self, Value};
use crate::runtime::downloaded;
use crate::{app, win};

static PAYLOAD: &[u8] = include_bytes!(concat!(env!("OUT_DIR"), "/payload.zst"));
const MANIFEST: &str = "installed-files.txt";
const DEFAULT_FOLDER: &str = "rusty-client";
const SHORTCUT: &str = "Rusty Client.lnk";
const CREATE_NO_WINDOW: u32 = 0x0800_0000;

#[derive(Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
struct Status {
    active: bool,
    done: bool,
    progress: f64,
    detail: String,
    error: String,
    installed: String,
}

static STATUS: Mutex<Status> = Mutex::new(Status {
    active: false,
    done: false,
    progress: 0.0,
    detail: String::new(),
    error: String::new(),
    installed: String::new(),
});

fn launcher() -> &'static [u8] {
    static BYTES: OnceLock<Vec<u8>> = OnceLock::new();
    BYTES.get_or_init(|| {
        let mut out = Vec::new();
        if let Ok(mut decoder) = zstd::stream::Decoder::new(PAYLOAD) {
            let _ = decoder.read_to_end(&mut out);
        }
        out
    })
}

pub fn update() {
    if let Some(location) = registry::read("InstallLocation") {
        let target = PathBuf::from(location);
        let desktop = registry::read("RustyDesktopShortcut").is_some();
        let start_menu = registry::read("RustyStartMenuShortcut").is_some();
        for _ in 0..80 {
            if !launcher_running() {
                break;
            }
            std::thread::sleep(std::time::Duration::from_millis(250));
        }
        match install(&target, desktop, start_menu) {
            Ok(()) => launch_as_user(&target.join("RustyClient.exe").to_string_lossy()),
            Err(error) => crate::runtime::fail(&format!("Rusty Client could not be updated.\n\n{error}")),
        }
    }
    remove_temp_runtime();
}

fn remove_temp_runtime() {
    let script = format!("ping 127.0.0.1 -n 4 >nul & rmdir /s /q \"{}\"", downloaded::base().to_string_lossy());
    let _ = std::process::Command::new("cmd")
        .arg("/c")
        .raw_arg(script)
        .current_dir(std::env::temp_dir())
        .creation_flags(CREATE_NO_WINDOW)
        .spawn();
}

fn launch_as_user(exe: &str) {
    let verb = win::wide("open");
    let explorer = win::wide("explorer.exe");
    let args = win::wide(&format!("\"{exe}\""));
    unsafe {
        ShellExecuteW(std::ptr::null_mut(), verb.as_ptr(), explorer.as_ptr(), args.as_ptr(), std::ptr::null(), SW_SHOWNORMAL);
    }
}

pub fn run(main_args: &MainArgs) {
    app::run(
        main_args,
        win::WindowSpec {
            title: "Rusty Client Setup",
            width: 532,
            height: 500,
            min_width: 532,
            min_height: 500,
            resizable: false,
        },
        downloaded::base().join("cache"),
    );
    remove_temp_runtime();
}

fn program_files() -> String {
    std::env::var("ProgramW6432")
        .or_else(|_| std::env::var("ProgramFiles"))
        .unwrap_or_else(|_| "C:\\Program Files".into())
}

fn saved_accent() -> Option<String> {
    let file = PathBuf::from(std::env::var_os("APPDATA")?).join(".rustyclient").join("settings.json");
    let settings: serde_json::Value = serde_json::from_slice(&std::fs::read(file).ok()?).ok()?;
    let accent = settings["accent"].as_str()?;
    let valid = accent.len() == 7 && accent.starts_with('#') && accent[1..].chars().all(|c| c.is_ascii_hexdigit());
    valid.then(|| accent.to_string())
}

fn default_location() -> (String, String, bool) {
    if let Some(existing) = registry::read("InstallLocation") {
        let path = PathBuf::from(&existing);
        if let (Some(parent), Some(name)) = (path.parent(), path.file_name()) {
            return (parent.to_string_lossy().to_string(), name.to_string_lossy().to_string(), true);
        }
    }
    (program_files(), DEFAULT_FOLDER.into(), false)
}

fn required_bytes() -> u64 {
    downloaded::files().iter().map(|(_, size)| size).sum::<u64>() + launcher().len() as u64
}

fn free_bytes(path: &Path) -> Option<u64> {
    let mut probe = path.to_path_buf();
    while !probe.exists() {
        probe = probe.parent()?.to_path_buf();
    }
    let wide_path = win::wide(&probe.to_string_lossy());
    let mut free: u64 = 0;
    let ok = unsafe { GetDiskFreeSpaceExW(wide_path.as_ptr(), &mut free, std::ptr::null_mut(), std::ptr::null_mut()) };
    (ok != 0).then_some(free)
}

fn validate(parent: &str, folder: &str) -> Result<PathBuf, String> {
    let parent = parent.trim();
    let folder = folder.trim();
    if folder.is_empty() {
        return Err("Enter a name for the install folder.".into());
    }
    if folder.chars().any(|c| matches!(c, '\\' | '/' | ':' | '*' | '?' | '"' | '<' | '>' | '|')) || folder == "." || folder == ".." {
        return Err("The folder name contains characters Windows does not allow.".into());
    }
    let parent_path = PathBuf::from(parent);
    let bytes = parent.as_bytes();
    let absolute = (bytes.len() >= 3 && bytes[1] == b':' && (bytes[2] == b'\\' || bytes[2] == b'/')) || parent.starts_with("\\\\");
    if !absolute {
        return Err("Choose a full location, for example C:\\Program Files.".into());
    }
    let target = parent_path.join(folder);
    let windows = std::env::var("SystemRoot").unwrap_or_else(|_| "C:\\Windows".into()).to_ascii_lowercase();
    if target.to_string_lossy().to_ascii_lowercase().starts_with(&windows) {
        return Err("Rusty Client cannot be installed inside the Windows folder.".into());
    }
    if target.is_file() {
        return Err("A file with that name already exists in this location.".into());
    }
    Ok(target)
}

pub async fn route(route: &str, request: &Incoming) -> Reply {
    match route {
        "/boot" => {
            let (parent, folder, existing) = default_location();
            let target = PathBuf::from(&parent).join(&folder);
            Reply::json(json!({
                "frame": if win::is_win11() { "win11" } else { "win10" },
                "parent": parent,
                "folder": folder,
                "existing": existing,
                "required": required_bytes(),
                "free": free_bytes(&target),
                "accent": saved_accent(),
            }))
        }
        "/check" => check(request),
        "/browse" => browse(request).await,
        "/install" => start_install(request),
        "/status" => Reply::json(serde_json::to_value(STATUS.lock().unwrap().clone()).unwrap_or_default()),
        "/finish" => finish(request),
        "/window/minimize" => {
            win::post(win::WM_APP_MINIMIZE);
            Reply::json(json!({}))
        }
        "/window/close" => {
            if STATUS.lock().unwrap().active {
                return Reply::error("Please wait until the installation is finished.");
            }
            win::post(win::WM_APP_CLOSE);
            Reply::json(json!({}))
        }
        _ => Reply::status(404),
    }
}

#[derive(Deserialize)]
struct Location {
    parent: String,
    folder: String,
}

fn check(request: &Incoming) -> Reply {
    let Some(location) = request.json::<Location>() else {
        return Reply::error("Invalid request.");
    };
    match validate(&location.parent, &location.folder) {
        Ok(target) => Reply::json(json!({
            "valid": true,
            "path": target.to_string_lossy(),
            "free": free_bytes(&target),
            "required": required_bytes(),
        })),
        Err(message) => Reply::json(json!({ "valid": false, "message": message })),
    }
}

#[derive(Deserialize)]
struct BrowseRequest {
    current: String,
}

async fn browse(request: &Incoming) -> Reply {
    let current = request.json::<BrowseRequest>().map(|b| b.current).unwrap_or_default();
    let picked = app::request_folder(current).await.ok().flatten();
    Reply::json(json!({ "path": picked }))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct InstallRequest {
    parent: String,
    folder: String,
    desktop: bool,
    start_menu: bool,
}

fn start_install(request: &Incoming) -> Reply {
    let Some(choice) = request.json::<InstallRequest>() else {
        return Reply::error("Invalid request.");
    };
    let target = match validate(&choice.parent, &choice.folder) {
        Ok(target) => target,
        Err(message) => return Reply::error(message),
    };
    if let Some(free) = free_bytes(&target) {
        if free < required_bytes() + 64 * 1024 * 1024 {
            return Reply::error("There is not enough free space on this drive.");
        }
    }
    {
        let mut status = STATUS.lock().unwrap();
        if status.active {
            return Reply::error("Setup is already installing.");
        }
        *status = Status {
            active: true,
            detail: "Preparing".into(),
            ..Default::default()
        };
    }
    std::thread::spawn(move || {
        let result = install(&target, choice.desktop, choice.start_menu);
        let mut status = STATUS.lock().unwrap();
        status.active = false;
        match result {
            Ok(()) => {
                status.done = true;
                status.progress = 1.0;
                status.detail = "Done".into();
                status.installed = target.join("RustyClient.exe").to_string_lossy().to_string();
            }
            Err(error) => {
                status.error = error;
                status.progress = 0.0;
            }
        }
    });
    Reply::json(json!({}))
}

fn set_progress(detail: &str, progress: f64) {
    let mut status = STATUS.lock().unwrap();
    status.detail = detail.into();
    status.progress = progress.clamp(0.0, 1.0);
}

fn launcher_running() -> bool {
    let class = win::wide("RustyClientWindow");
    !unsafe { FindWindowW(class.as_ptr(), std::ptr::null()) }.is_null()
}

fn install(target: &Path, desktop: bool, start_menu: bool) -> Result<(), String> {
    if launcher_running() {
        return Err("Rusty Client is open right now. Close it and try again.".into());
    }
    std::fs::create_dir_all(target)
        .map_err(|_| "Setup could not create the install folder. Choose another location.".to_string())?;

    let files = downloaded::files();
    let payload = launcher();
    if payload.is_empty() {
        return Err("The installer is damaged. Download it again.".into());
    }
    let total = files.iter().map(|(_, size)| *size).sum::<u64>() + payload.len() as u64;
    let mut done = 0u64;
    let source_root = downloaded::dir();
    let mut installed = Vec::with_capacity(files.len() + 1);

    let exe = target.join("RustyClient.exe");
    set_progress("RustyClient.exe", 0.0);
    write_file(&exe, payload)?;
    done += payload.len() as u64;
    installed.push("RustyClient.exe".to_string());

    let in_place = same_folder(&source_root, target);
    let mut buffer = vec![0u8; 1 << 20];
    for (relative, size) in &files {
        if in_place {
            done += size;
            installed.push(relative.clone());
            continue;
        }
        let source = source_root.join(relative);
        let destination = target.join(relative);
        if let Some(parent) = destination.parent() {
            std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
        let name = relative.rsplit('/').next().unwrap_or(relative);
        let mut input = std::fs::File::open(&source).map_err(|e| format!("Could not read {name}: {e}"))?;
        let mut output = std::fs::File::create(&destination).map_err(|_| locked_message(name))?;
        loop {
            let read = input.read(&mut buffer).map_err(|e| e.to_string())?;
            if read == 0 {
                break;
            }
            output.write_all(&buffer[..read]).map_err(|e| format!("Could not write {name}: {e}"))?;
            done += read as u64;
            set_progress(name, 0.95 * done as f64 / total as f64);
        }
        installed.push(relative.clone());
    }
    std::fs::write(target.join(MANIFEST), installed.join("\r\n")).map_err(|e| e.to_string())?;

    set_progress("Creating shortcuts", 0.96);
    let desktop_link = std::env::var("PUBLIC")
        .map(|p| PathBuf::from(p).join("Desktop").join(SHORTCUT))
        .unwrap_or_default();
    let start_link = std::env::var("ProgramData")
        .map(|p| PathBuf::from(p).join("Microsoft\\Windows\\Start Menu\\Programs").join(SHORTCUT))
        .unwrap_or_default();
    let mut values: Vec<(String, String)> = Vec::new();
    for (wanted, link, key) in [
        (desktop, &desktop_link, "RustyDesktopShortcut"),
        (start_menu, &start_link, "RustyStartMenuShortcut"),
    ] {
        if link.as_os_str().is_empty() {
            continue;
        }
        if wanted {
            create_shortcut(&exe, link)?;
            values.push((key.into(), link.to_string_lossy().to_string()));
        } else {
            let _ = std::fs::remove_file(link);
        }
    }

    set_progress("Registering with Windows", 0.99);
    let exe_text = exe.to_string_lossy().to_string();
    let location = target.to_string_lossy().to_string();
    let uninstall = format!("\"{exe_text}\" --uninstall");
    let icon = format!("{exe_text},0");
    let size_kb = (total / 1024) as u32;
    let mut entries = vec![
        ("DisplayName", Value::Text("Rusty Client")),
        ("DisplayIcon", Value::Text(&icon)),
        ("Publisher", Value::Text("Rusty Client")),
        ("InstallLocation", Value::Text(&location)),
        ("UninstallString", Value::Text(&uninstall)),
        ("NoModify", Value::Number(1)),
        ("NoRepair", Value::Number(1)),
        ("EstimatedSize", Value::Number(size_kb)),
    ];
    for (key, value) in &values {
        entries.push((key.as_str(), Value::Text(value)));
    }
    registry::write(&entries)
}

fn same_folder(a: &Path, b: &Path) -> bool {
    match (std::fs::canonicalize(a), std::fs::canonicalize(b)) {
        (Ok(a), Ok(b)) => a.to_string_lossy().eq_ignore_ascii_case(&b.to_string_lossy()),
        _ => false,
    }
}

fn locked_message(name: &str) -> String {
    format!("Could not write {name}. Close Rusty Client if it is open and try again.")
}

fn write_file(path: &Path, bytes: &[u8]) -> Result<(), String> {
    let name = path.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default();
    std::fs::write(path, bytes).map_err(|_| locked_message(&name))
}

fn create_shortcut(target: &Path, link: &Path) -> Result<(), String> {
    use windows::Win32::System::Com::{
        CLSCTX_INPROC_SERVER, COINIT_APARTMENTTHREADED, CoCreateInstance, CoInitializeEx, CoUninitialize, IPersistFile,
    };
    use windows::Win32::UI::Shell::{IShellLinkW, ShellLink};
    use windows::core::{HSTRING, Interface};

    if let Some(parent) = link.parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    unsafe {
        let initialised = CoInitializeEx(None, COINIT_APARTMENTTHREADED).is_ok();
        let result = (|| -> windows::core::Result<()> {
            let shell_link: IShellLinkW = CoCreateInstance(&ShellLink, None, CLSCTX_INPROC_SERVER)?;
            shell_link.SetPath(&HSTRING::from(target.as_os_str()))?;
            if let Some(dir) = target.parent() {
                shell_link.SetWorkingDirectory(&HSTRING::from(dir.as_os_str()))?;
            }
            shell_link.SetDescription(&HSTRING::from("Rusty Client"))?;
            shell_link.SetIconLocation(&HSTRING::from(target.as_os_str()), 0)?;
            let file: IPersistFile = shell_link.cast()?;
            file.Save(&HSTRING::from(link.as_os_str()), true)?;
            Ok(())
        })();
        if initialised {
            CoUninitialize();
        }
        result.map_err(|_| "Setup could not create a shortcut.".to_string())
    }
}

#[derive(Deserialize)]
struct FinishRequest {
    launch: bool,
}

fn finish(request: &Incoming) -> Reply {
    let launch = request.json::<FinishRequest>().map(|f| f.launch).unwrap_or(false);
    let installed = STATUS.lock().unwrap().installed.clone();
    if launch && !installed.is_empty() {
        launch_as_user(&installed);
    }
    win::post(win::WM_APP_CLOSE);
    Reply::json(json!({}))
}
