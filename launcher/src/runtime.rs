use std::path::PathBuf;

use windows_sys::Win32::System::LibraryLoader::{LOAD_WITH_ALTERED_SEARCH_PATH, LoadLibraryExW, SetDllDirectoryW};
use windows_sys::Win32::UI::WindowsAndMessaging::{MB_ICONERROR, MB_OK, MessageBoxW};

use crate::win::wide;

pub fn dir() -> PathBuf {
    #[cfg(any(feature = "portable", feature = "setup"))]
    {
        downloaded::dir()
    }
    #[cfg(not(any(feature = "portable", feature = "setup")))]
    {
        crate::paths::exe_dir()
    }
}

pub fn load(is_browser: bool) -> bool {
    #[cfg(any(feature = "portable", feature = "setup"))]
    if !downloaded::ensure(is_browser) {
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
        downloaded::clean_old();
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
pub mod downloaded {
    use std::collections::HashMap;
    use std::fs::File;
    use std::io::{BufReader, BufWriter, Read, Seek, SeekFrom, Write};
    use std::path::{Path, PathBuf};
    use std::ptr::null;
    use std::sync::OnceLock;
    use std::sync::atomic::{AtomicU64, Ordering};

    use sha1::{Digest, Sha1};
    use windows_sys::Win32::Foundation::{CloseHandle, WAIT_ABANDONED, WAIT_OBJECT_0};
    use windows_sys::Win32::System::Threading::{CreateMutexW, INFINITE, ReleaseMutex, WaitForSingleObject};
    use windows_sys::Win32::UI::WindowsAndMessaging::{IDRETRY, MB_ICONERROR, MB_RETRYCANCEL, MessageBoxW};

    use crate::progress::{self, Task};
    use crate::win::wide;

    mod table {
        include!(concat!(env!("OUT_DIR"), "/cef_runtime.rs"));
    }
    use table::{ARCHIVE, ARCHIVE_SHA1, FILES, ID};

    const CDN: &str = "https://cef-builds.spotifycdn.com/";
    const MARKER: &str = ".complete";
    const DIR_VARIABLE: &str = "RUSTY_CEF_DIR";
    const DOWNLOAD_SHARE: f64 = 0.8;

    static DIR: OnceLock<PathBuf> = OnceLock::new();

    enum Failure {
        Cancelled,
        Message(String),
    }

    impl From<String> for Failure {
        fn from(message: String) -> Self {
            Failure::Message(message)
        }
    }

    impl From<std::io::Error> for Failure {
        fn from(error: std::io::Error) -> Self {
            Failure::Message(error.to_string())
        }
    }

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

    fn own_dir() -> PathBuf {
        base().join(ID)
    }

    pub fn dir() -> PathBuf {
        DIR.get().cloned().unwrap_or_else(own_dir)
    }

    #[cfg(feature = "setup")]
    pub fn files() -> Vec<(String, u64)> {
        FILES.iter().map(|(name, size, _)| (name.to_string(), *size)).collect()
    }

    fn use_dir(path: PathBuf) {
        unsafe {
            std::env::set_var(DIR_VARIABLE, &path);
        }
        let _ = DIR.set(path);
    }

    pub fn ensure(is_browser: bool) -> bool {
        if !is_browser {
            if let Some(path) = std::env::var_os(DIR_VARIABLE) {
                let _ = DIR.set(PathBuf::from(path));
            }
            return dir().join("libcef.dll").is_file();
        }
        if own_dir().join(MARKER).is_file() {
            use_dir(own_dir());
            return true;
        }
        loop {
            match progress::run(title(), headline(), prepare) {
                Ok(path) => {
                    use_dir(path);
                    return true;
                }
                Err(Failure::Cancelled) => return false,
                Err(Failure::Message(error)) => {
                    let text = wide(&format!(
                        "Rusty Client could not download its browser runtime. Check your internet connection and try again.\n\n{error}"
                    ));
                    let caption = wide("Rusty Client");
                    let answer = unsafe {
                        MessageBoxW(std::ptr::null_mut(), text.as_ptr(), caption.as_ptr(), MB_RETRYCANCEL | MB_ICONERROR)
                    };
                    if answer != IDRETRY {
                        return false;
                    }
                }
            }
        }
    }

    fn title() -> &'static str {
        if cfg!(feature = "setup") { "Rusty Client Setup" } else { "Rusty Client" }
    }

    fn headline() -> &'static str {
        if cfg!(feature = "setup") { "Preparing Rusty Client Setup" } else { "Getting Rusty Client ready" }
    }

    fn prepare(task: &Task) -> Result<PathBuf, Failure> {
        unsafe {
            let name = wide("Local\\RustyClientRuntimeUnpack");
            let mutex = CreateMutexW(null(), 0, name.as_ptr());
            let locked = !mutex.is_null() && matches!(WaitForSingleObject(mutex, INFINITE), WAIT_OBJECT_0 | WAIT_ABANDONED);
            let result = locate(task);
            if locked {
                ReleaseMutex(mutex);
            }
            if !mutex.is_null() {
                CloseHandle(mutex);
            }
            result
        }
    }

    fn locate(task: &Task) -> Result<PathBuf, Failure> {
        let target = own_dir();
        if target.join(MARKER).is_file() {
            return Ok(target);
        }
        task.set(0.0, "Checking files");
        for candidate in candidates() {
            if task.cancelled() {
                return Err(Failure::Cancelled);
            }
            if !matches(&candidate, task) {
                continue;
            }
            #[cfg(feature = "setup")]
            return Ok(candidate);
            #[cfg(not(feature = "setup"))]
            if adopt(&candidate, &target).is_ok() {
                return Ok(target);
            }
        }
        if task.cancelled() {
            return Err(Failure::Cancelled);
        }
        let archive = download(task)?;
        extract(&archive, &target, task)?;
        let _ = std::fs::remove_file(&archive);
        Ok(target)
    }

    fn candidates() -> Vec<PathBuf> {
        let mut out = Vec::new();
        #[cfg(feature = "setup")]
        {
            if let Some(location) = crate::registry::read("InstallLocation") {
                out.push(PathBuf::from(location));
            }
            if let Some(appdata) = std::env::var_os("APPDATA") {
                out.extend(runtime_folders(&PathBuf::from(appdata).join(".rustyclient").join("cef-runtime")));
            }
        }
        #[cfg(not(feature = "setup"))]
        out.extend(runtime_folders(&base()));
        out
    }

    fn runtime_folders(base: &Path) -> Vec<PathBuf> {
        let Ok(entries) = std::fs::read_dir(base) else {
            return Vec::new();
        };
        entries
            .filter_map(Result::ok)
            .map(|entry| entry.path())
            .filter(|path| path.is_dir() && path.join("libcef.dll").is_file())
            .filter(|path| !path.extension().is_some_and(|e| e == "unpacking"))
            .collect()
    }

    fn matches(folder: &Path, task: &Task) -> bool {
        let sized = FILES.iter().all(|(name, size, _)| {
            std::fs::metadata(folder.join(name)).map(|m| m.is_file() && m.len() == *size).unwrap_or(false)
        });
        if !sized {
            return false;
        }
        let total: u64 = FILES.iter().map(|(_, size, _)| size).sum();
        let mut done = 0u64;
        let mut buffer = vec![0u8; 1 << 20];
        for (name, _, expected) in FILES {
            let Ok(mut input) = File::open(folder.join(name)) else {
                return false;
            };
            let mut hash = Sha1::new();
            loop {
                if task.cancelled() {
                    return false;
                }
                let Ok(read) = input.read(&mut buffer) else {
                    return false;
                };
                if read == 0 {
                    break;
                }
                hash.update(&buffer[..read]);
                done += read as u64;
                task.set(done as f64 / total as f64, "Checking files");
            }
            if hex::encode(hash.finalize()) != *expected {
                return false;
            }
        }
        true
    }

    #[cfg(not(feature = "setup"))]
    fn adopt(source: &Path, target: &Path) -> Result<(), String> {
        let _ = std::fs::remove_dir_all(target);
        if std::fs::rename(source, target).is_err() {
            let staging = base().join(format!("{ID}.unpacking"));
            let _ = std::fs::remove_dir_all(&staging);
            for (name, _, _) in FILES {
                let destination = staging.join(name);
                if let Some(parent) = destination.parent() {
                    std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
                }
                std::fs::copy(source.join(name), &destination).map_err(|e| e.to_string())?;
            }
            std::fs::rename(&staging, target).map_err(|e| e.to_string())?;
        }
        std::fs::write(target.join(MARKER), ID).map_err(|e| e.to_string())
    }

    fn file_sha1(path: &Path) -> Option<String> {
        let mut input = File::open(path).ok()?;
        let mut hash = Sha1::new();
        let mut buffer = vec![0u8; 1 << 20];
        loop {
            let read = input.read(&mut buffer).ok()?;
            if read == 0 {
                break;
            }
            hash.update(&buffer[..read]);
        }
        Some(hex::encode(hash.finalize()))
    }

    fn download(task: &Task) -> Result<PathBuf, Failure> {
        let folder = base();
        std::fs::create_dir_all(&folder)?;
        let archive = folder.join(format!("{ID}.tar.bz2"));
        if archive.is_file() {
            if file_sha1(&archive).as_deref() == Some(ARCHIVE_SHA1) {
                return Ok(archive);
            }
            let _ = std::fs::remove_file(&archive);
        }
        let part = folder.join(format!("{ID}.tar.bz2.rusty-part"));
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .map_err(|e| e.to_string())?;
        runtime.block_on(fetch(&part, task))?;
        if file_sha1(&part).as_deref() != Some(ARCHIVE_SHA1) {
            let _ = std::fs::remove_file(&part);
            return Err(Failure::Message("The download was damaged. Please try again.".into()));
        }
        std::fs::rename(&part, &archive)?;
        Ok(archive)
    }

    async fn fetch(part: &Path, task: &Task) -> Result<(), Failure> {
        use futures_util::StreamExt;

        let client = reqwest::Client::builder()
            .user_agent(crate::state::USER_AGENT)
            .connect_timeout(std::time::Duration::from_secs(20))
            .read_timeout(std::time::Duration::from_secs(60))
            .build()
            .map_err(|e| e.to_string())?;
        let url = format!("{CDN}{ARCHIVE}");
        let existing = std::fs::metadata(part).map(|m| m.len()).unwrap_or(0);
        let mut request = client.get(&url);
        if existing > 0 {
            request = request.header(reqwest::header::RANGE, format!("bytes={existing}-"));
        }
        let response = request.send().await.map_err(|e| e.to_string())?;
        let status = response.status();
        if status == reqwest::StatusCode::RANGE_NOT_SATISFIABLE && existing > 0 {
            return Ok(());
        }
        if !status.is_success() {
            return Err(Failure::Message(format!("The download server answered {status}.")));
        }
        let resumed = status == reqwest::StatusCode::PARTIAL_CONTENT;
        let mut file = std::fs::OpenOptions::new().create(true).write(true).truncate(false).open(part)?;
        let mut done = if resumed {
            file.seek(SeekFrom::End(0))?
        } else {
            file.set_len(0)?;
            0
        };
        let total = done + response.content_length().unwrap_or(0);
        let mut output = BufWriter::with_capacity(1 << 20, file);
        let mut stream = response.bytes_stream();
        while let Some(chunk) = stream.next().await {
            if task.cancelled() {
                output.flush()?;
                return Err(Failure::Cancelled);
            }
            let chunk = chunk.map_err(|e| e.to_string())?;
            output.write_all(&chunk)?;
            done += chunk.len() as u64;
            let fraction = if total > 0 { done as f64 / total as f64 } else { 0.0 };
            task.set(fraction * DOWNLOAD_SHARE, &downloaded_text(done, total));
        }
        output.flush()?;
        if total > 0 && done != total {
            return Err(Failure::Message("The download ended early. Please try again.".into()));
        }
        Ok(())
    }

    fn downloaded_text(done: u64, total: u64) -> String {
        let mb = |bytes: u64| (bytes as f64 / 1_048_576.0).round() as u64;
        if total > 0 {
            format!("Downloading {} of {} MB", mb(done), mb(total))
        } else {
            format!("Downloading {} MB", mb(done))
        }
    }

    struct Counting<R> {
        inner: R,
        read: &'static AtomicU64,
    }

    impl<R: Read> Read for Counting<R> {
        fn read(&mut self, buffer: &mut [u8]) -> std::io::Result<usize> {
            let count = self.inner.read(buffer)?;
            self.read.fetch_add(count as u64, Ordering::Relaxed);
            Ok(count)
        }
    }

    fn extract(archive: &Path, target: &Path, task: &Task) -> Result<(), Failure> {
        static READ: AtomicU64 = AtomicU64::new(0);
        READ.store(0, Ordering::Relaxed);
        let total = std::fs::metadata(archive)?.len().max(1);
        let wanted: HashMap<&str, u64> = FILES.iter().map(|(name, size, _)| (*name, *size)).collect();

        let staging = base().join(format!("{ID}.unpacking"));
        let _ = std::fs::remove_dir_all(&staging);
        std::fs::create_dir_all(&staging)?;

        task.set(DOWNLOAD_SHARE, "Unpacking");
        let input = Counting {
            inner: BufReader::with_capacity(1 << 20, File::open(archive)?),
            read: &READ,
        };
        let mut tar = tar::Archive::new(bzip2::read::MultiBzDecoder::new(input));
        let mut found = 0usize;
        for entry in tar.entries()? {
            if task.cancelled() {
                return Err(Failure::Cancelled);
            }
            let mut entry = entry?;
            let progress = READ.load(Ordering::Relaxed) as f64 / total as f64;
            task.set(DOWNLOAD_SHARE + (1.0 - DOWNLOAD_SHARE) * progress, "Unpacking");
            let path = entry.path()?.to_string_lossy().replace('\\', "/");
            let Some(name) = runtime_name(&path) else {
                continue;
            };
            let Some(size) = wanted.get(name.as_str()).copied() else {
                continue;
            };
            let destination = staging.join(&name);
            if let Some(parent) = destination.parent() {
                std::fs::create_dir_all(parent)?;
            }
            let mut output = BufWriter::with_capacity(1 << 20, File::create(&destination)?);
            let copied = std::io::copy(&mut entry, &mut output)?;
            output.flush()?;
            if copied != size {
                return Err(Failure::Message(format!("{name} in the download has the wrong size.")));
            }
            found += 1;
        }
        if found != FILES.len() {
            return Err(Failure::Message("The download is missing files.".into()));
        }
        std::fs::write(staging.join(MARKER), ID)?;
        let _ = std::fs::remove_dir_all(target);
        std::fs::rename(&staging, target)?;
        task.set(1.0, "Starting");
        Ok(())
    }

    fn runtime_name(path: &str) -> Option<String> {
        let (_, inside) = path.split_once('/')?;
        if let Some(name) = inside.strip_prefix("Release/") {
            return (!name.contains('/')).then(|| name.to_string());
        }
        inside.strip_prefix("Resources/").map(str::to_string)
    }

    pub fn clean_old() {
        let Ok(entries) = std::fs::read_dir(base()) else {
            return;
        };
        for entry in entries.filter_map(Result::ok) {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.starts_with(ID) {
                continue;
            }
            let path = entry.path();
            if path.is_dir() {
                let _ = std::fs::remove_dir_all(path);
            } else {
                let _ = std::fs::remove_file(path);
            }
        }
    }
}
