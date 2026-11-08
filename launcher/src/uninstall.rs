use std::os::windows::process::CommandExt;
use std::ptr::null_mut;

use windows_sys::Win32::UI::Shell::{IsUserAnAdmin, ShellExecuteW};
use windows_sys::Win32::UI::WindowsAndMessaging::*;

use crate::{paths, registry, win::wide};

const CREATE_NO_WINDOW: u32 = 0x0800_0000;
pub const MANIFEST: &str = "installed-files.txt";

fn message(text: &str, flags: MESSAGEBOX_STYLE) -> i32 {
    let text = wide(text);
    let title = wide("Uninstall Rusty Client");
    unsafe { MessageBoxW(null_mut(), text.as_ptr(), title.as_ptr(), flags) }
}

pub fn run() {
    let exe = std::env::current_exe().unwrap_or_default();
    if unsafe { IsUserAnAdmin() } == 0 {
        let verb = wide("runas");
        let file = wide(&exe.to_string_lossy());
        let args = wide("--uninstall");
        unsafe {
            ShellExecuteW(null_mut(), verb.as_ptr(), file.as_ptr(), args.as_ptr(), null_mut(), SW_SHOWNORMAL);
        }
        return;
    }

    let class = wide("RustyClientWindow");
    if !unsafe { FindWindowW(class.as_ptr(), std::ptr::null()) }.is_null() {
        message("Rusty Client is still open. Close it and run the uninstaller again.", MB_OK | MB_ICONWARNING);
        return;
    }

    let answer = message(
        "Do you want to remove Rusty Client from this PC?\n\nYour worlds, settings and downloads in %APPDATA%\\.rustyclient are kept.",
        MB_YESNO | MB_ICONQUESTION | MB_DEFBUTTON2,
    );
    if answer != IDYES {
        return;
    }

    for value in ["RustyDesktopShortcut", "RustyStartMenuShortcut"] {
        if let Some(path) = registry::read(value) {
            if path.to_ascii_lowercase().ends_with(".lnk") {
                let _ = std::fs::remove_file(path);
            }
        }
    }
    registry::remove();

    let install_dir = paths::exe_dir();
    let manifest = install_dir.join(MANIFEST);
    let mut folders = std::collections::BTreeSet::new();
    if let Ok(list) = std::fs::read_to_string(&manifest) {
        for line in list.lines().map(str::trim).filter(|l| !l.is_empty()) {
            if line.contains("..") || line.contains(':') || line.starts_with(['/', '\\']) {
                continue;
            }
            let path = install_dir.join(line);
            if path == exe {
                continue;
            }
            let _ = std::fs::remove_file(&path);
            if let Some(parent) = path.parent() {
                if parent != install_dir {
                    folders.insert(parent.to_path_buf());
                }
            }
        }
    }
    let _ = std::fs::remove_file(&manifest);

    message("Rusty Client has been removed from your PC.", MB_OK | MB_ICONINFORMATION);

    let mut script = format!("ping 127.0.0.1 -n 3 >nul & del /f /q \"{}\"", exe.to_string_lossy());
    for folder in folders.iter().rev() {
        script.push_str(&format!(" & rmdir \"{}\"", folder.to_string_lossy()));
    }
    script.push_str(&format!(" & rmdir \"{}\"", install_dir.to_string_lossy()));
    let _ = std::process::Command::new("cmd")
        .arg("/c")
        .raw_arg(script)
        .current_dir(std::env::temp_dir())
        .creation_flags(CREATE_NO_WINDOW)
        .spawn();
}
