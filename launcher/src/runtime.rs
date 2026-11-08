use std::path::PathBuf;

use windows_sys::Win32::System::LibraryLoader::{LOAD_WITH_ALTERED_SEARCH_PATH, LoadLibraryExW, SetDllDirectoryW};
use windows_sys::Win32::UI::WindowsAndMessaging::{MB_ICONERROR, MB_OK, MessageBoxW};

use crate::win::wide;

pub fn dir() -> PathBuf {
    #[cfg(any(feature = "portable", feature = "setup"))]
    {
        embedded::dir()
    }
    #[cfg(not(any(feature = "portable", feature = "setup")))]
    {
        crate::paths::exe_dir()
    }
}

pub fn load(is_browser: bool) -> bool {
    #[cfg(any(feature = "portable", feature = "setup"))]
    if let Err(error) = embedded::ensure() {
        if is_browser {
            fail(&format!("Rusty Client could not unpack its browser runtime.\n\n{error}"));
        }
        return false;
    }
    let target = dir();
    let library = target.join("libcef.dll");
    if !library.is_file() {
        if is_browser {
            fail("Rusty Client is missing libcef.dll. Please reinstall Rusty Client.");
        }
        return false;
    }
    unsafe {
        let folder = wide(&target.to_string_lossy());
        SetDllDirectoryW(folder.as_ptr());
        let path = wide(&library.to_string_lossy());
        if LoadLibraryExW(path.as_ptr(), std::ptr::null_mut(), LOAD_WITH_ALTERED_SEARCH_PATH).is_null() {
            if is_browser {
                fail("Rusty Client could not load its browser runtime. Please reinstall Rusty Client.");
            }
            return false;
        }
    }
    #[cfg(any(feature = "portable", feature = "setup"))]
    if is_browser {
        embedded::clean_old();
    }
    true
}

pub fn fail(message: &str) {
    let text = wide(message);
    let title = wide("Rusty Client");
    unsafe {
        MessageBoxW(std::ptr::null_mut(), text.as_ptr(), title.as_ptr(), MB_OK | MB_ICONERROR);
    }
}

#[cfg(any(feature = "portable", feature = "setup"))]
pub mod embedded {
    use std::fs::File;
    use std::io::{BufWriter, Read, Write};
    use std::path::{Path, PathBuf};
    use std::ptr::null;

    use windows_sys::Win32::Foundation::{CloseHandle, WAIT_ABANDONED, WAIT_OBJECT_0};
    use windows_sys::Win32::System::Threading::{CreateMutexW, INFINITE, ReleaseMutex, WaitForSingleObject};

    use crate::win::wide;

    static PACK: &[u8] = include_bytes!(concat!(env!("OUT_DIR"), "/cef-runtime.zst"));
    const RUNTIME_ID: &str = env!("RUSTY_CEF_ID");
    const MARKER: &str = ".complete";

    pub fn base() -> PathBuf {
        #[cfg(feature = "setup")]
        {
            std::env::temp_dir().join("rusty-client-setup")
        }
        #[cfg(not(feature = "setup"))]
        {
            crate::paths::root().join("cef-runtime")
        }
    }

    pub fn dir() -> PathBuf {
        base().join(RUNTIME_ID)
    }

    pub fn ensure() -> Result<(), String> {
        let target = dir();
        if target.join(MARKER).is_file() {
            return Ok(());
        }
        unsafe {
            let name = wide("Local\\RustyClientRuntimeUnpack");
            let mutex = CreateMutexW(null(), 0, name.as_ptr());
            let locked = !mutex.is_null() && matches!(WaitForSingleObject(mutex, INFINITE), WAIT_OBJECT_0 | WAIT_ABANDONED);
            let result = if target.join(MARKER).is_file() { Ok(()) } else { extract(&target) };
            if locked {
                ReleaseMutex(mutex);
            }
            if !mutex.is_null() {
                CloseHandle(mutex);
            }
            result
        }
    }

    #[cfg(feature = "setup")]
    pub fn files() -> Vec<(String, u64)> {
        let root = dir();
        let mut out = Vec::new();
        let mut stack = vec![root.clone()];
        while let Some(folder) = stack.pop() {
            let Ok(entries) = std::fs::read_dir(&folder) else {
                continue;
            };
            for entry in entries.filter_map(Result::ok) {
                let path = entry.path();
                if path.is_dir() {
                    stack.push(path);
                    continue;
                }
                let Ok(relative) = path.strip_prefix(&root) else {
                    continue;
                };
                let relative = relative.to_string_lossy().replace('\\', "/");
                if relative == MARKER {
                    continue;
                }
                let size = entry.metadata().map(|m| m.len()).unwrap_or(0);
                out.push((relative, size));
            }
        }
        out
    }

    fn extract(target: &Path) -> Result<(), String> {
        let staging = base().join(format!("{RUNTIME_ID}.unpacking"));
        let _ = std::fs::remove_dir_all(&staging);
        std::fs::create_dir_all(&staging).map_err(|e| e.to_string())?;

        let mut decoder = zstd::stream::Decoder::new(PACK).map_err(|e| e.to_string())?;
        decoder.window_log_max(27).map_err(|e| e.to_string())?;

        let mut magic = [0u8; 4];
        decoder.read_exact(&mut magic).map_err(|e| e.to_string())?;
        if &magic != b"RCEF" {
            return Err("The embedded runtime is damaged.".into());
        }
        let count = read_u32(&mut decoder)? as usize;
        let mut entries = Vec::with_capacity(count);
        for _ in 0..count {
            let len = read_u16(&mut decoder)? as usize;
            let mut name = vec![0u8; len];
            decoder.read_exact(&mut name).map_err(|e| e.to_string())?;
            let name = String::from_utf8(name).map_err(|_| "The embedded runtime is damaged.".to_string())?;
            if name.contains("..") || name.starts_with('/') || name.contains(':') {
                return Err("The embedded runtime is damaged.".into());
            }
            let size = read_u64(&mut decoder)?;
            entries.push((name, size));
        }
        for (name, size) in entries {
            let path = staging.join(&name);
            if let Some(parent) = path.parent() {
                std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
            }
            let mut output = BufWriter::with_capacity(1 << 20, File::create(&path).map_err(|e| e.to_string())?);
            let copied = std::io::copy(&mut (&mut decoder).take(size), &mut output).map_err(|e| e.to_string())?;
            if copied != size {
                return Err("The embedded runtime is incomplete.".into());
            }
            output.flush().map_err(|e| e.to_string())?;
        }
        std::fs::write(staging.join(MARKER), RUNTIME_ID).map_err(|e| e.to_string())?;
        let _ = std::fs::remove_dir_all(target);
        std::fs::rename(&staging, target).map_err(|e| e.to_string())
    }

    fn read_u16(reader: &mut impl Read) -> Result<u16, String> {
        let mut buffer = [0u8; 2];
        reader.read_exact(&mut buffer).map_err(|e| e.to_string())?;
        Ok(u16::from_le_bytes(buffer))
    }

    fn read_u32(reader: &mut impl Read) -> Result<u32, String> {
        let mut buffer = [0u8; 4];
        reader.read_exact(&mut buffer).map_err(|e| e.to_string())?;
        Ok(u32::from_le_bytes(buffer))
    }

    fn read_u64(reader: &mut impl Read) -> Result<u64, String> {
        let mut buffer = [0u8; 8];
        reader.read_exact(&mut buffer).map_err(|e| e.to_string())?;
        Ok(u64::from_le_bytes(buffer))
    }

    pub fn clean_old() {
        let Ok(entries) = std::fs::read_dir(base()) else {
            return;
        };
        for entry in entries.filter_map(Result::ok) {
            let name = entry.file_name().to_string_lossy().to_string();
            if !name.starts_with(RUNTIME_ID) {
                let _ = std::fs::remove_dir_all(entry.path());
            }
        }
    }
}
