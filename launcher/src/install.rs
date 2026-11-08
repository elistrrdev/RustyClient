use std::collections::HashSet;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::sync::atomic::{AtomicU64, AtomicUsize, Ordering};
use std::time::Duration;

use futures_util::StreamExt;
use serde_json::{Value, json};
use sha1::{Digest, Sha1};
use tokio::io::AsyncWriteExt;

use crate::config::{Settings, write_atomic};
use crate::content::{self, VersionEntry};
use crate::paths;
use crate::state::Core;
use crate::win;

const JAVA_RUNTIMES: &str =
    "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
const RESOURCES: &str = "https://resources.download.minecraft.net";
const FABRIC_META: &str = "https://meta.fabricmc.net/v2";
const MODRINTH: &str = "https://api.modrinth.com/v2";
const FABRIC_API_PROJECT: &str = "P7dR8mSH";

#[derive(Clone, Debug)]
pub struct Download {
    pub url: String,
    pub path: PathBuf,
    pub sha1: Option<String>,
    pub size: u64,
}

pub struct Prepared {
    pub version: Value,
    pub fabric: Option<Value>,
    pub classpath: Vec<PathBuf>,
    pub java: PathBuf,
    pub natives_dir: PathBuf,
    pub game_dir: PathBuf,
    pub logging_arg: Option<String>,
}

#[derive(Default)]
pub struct Features {
    pub custom_resolution: bool,
}

pub fn rules_allow(rules: &Value, features: &Features) -> bool {
    let Some(rules) = rules.as_array() else {
        return true;
    };
    let mut allowed = false;
    for rule in rules {
        if os_matches(&rule["os"]) && features_match(&rule["features"], features) {
            allowed = rule["action"].as_str() == Some("allow");
        }
    }
    allowed
}

fn os_matches(os: &Value) -> bool {
    if os.is_null() {
        return true;
    }
    if let Some(name) = os["name"].as_str() {
        if name != "windows" {
            return false;
        }
    }
    if let Some(arch) = os["arch"].as_str() {
        if arch != "x86_64" && arch != "x64" && arch != "amd64" {
            return false;
        }
    }
    let (major, minor, build) = win::os_version();
    let current = [major as u64, minor as u64, build as u64];
    if let Some(min) = os["versionRange"]["min"].as_str() {
        if compare_versions(&current, min) == std::cmp::Ordering::Less {
            return false;
        }
    }
    if let Some(max) = os["versionRange"]["max"].as_str() {
        if compare_versions(&current, max) != std::cmp::Ordering::Less {
            return false;
        }
    }
    true
}

fn compare_versions(current: &[u64], other: &str) -> std::cmp::Ordering {
    let other: Vec<u64> = other.split('.').map(|p| p.parse().unwrap_or(0)).collect();
    for i in 0..current.len().max(other.len()) {
        let a = current.get(i).copied().unwrap_or(0);
        let b = other.get(i).copied().unwrap_or(0);
        match a.cmp(&b) {
            std::cmp::Ordering::Equal => continue,
            ordering => return ordering,
        }
    }
    std::cmp::Ordering::Equal
}

fn features_match(required: &Value, features: &Features) -> bool {
    let Some(required) = required.as_object() else {
        return true;
    };
    required.iter().all(|(key, value)| {
        let ours = match key.as_str() {
            "has_custom_resolution" => features.custom_resolution,
            _ => false,
        };
        value.as_bool() == Some(ours)
    })
}

fn maven_path(name: &str) -> Option<String> {
    let (coords, extension) = match name.split_once('@') {
        Some((coords, ext)) => (coords, ext),
        None => (name, "jar"),
    };
    let parts: Vec<&str> = coords.split(':').collect();
    if parts.len() < 3 {
        return None;
    }
    let group = parts[0].replace('.', "/");
    let artifact = parts[1];
    let version = parts[2];
    let file = match parts.get(3) {
        Some(classifier) => format!("{artifact}-{version}-{classifier}.{extension}"),
        None => format!("{artifact}-{version}.{extension}"),
    };
    Some(format!("{group}/{artifact}/{version}/{file}"))
}

fn library_key(name: &str) -> String {
    let parts: Vec<&str> = name.split('@').next().unwrap_or(name).split(':').collect();
    match parts.len() {
        0..=2 => name.to_string(),
        3 => format!("{}:{}", parts[0], parts[1]),
        _ => format!("{}:{}:{}", parts[0], parts[1], parts[3]),
    }
}

fn foreign_native(name: &str) -> bool {
    let lower = name.to_ascii_lowercase();
    lower.contains("arm64") || lower.contains("aarch_64") || lower.ends_with("-x86") || lower.ends_with(":natives-windows-x86")
}

async fn fetch_json(core: &Core, url: &str) -> Result<Value, String> {
    let response = core
        .http
        .get(url)
        .send()
        .await
        .map_err(|_| "Could not reach the download servers. Check your internet connection.".to_string())?;
    if !response.status().is_success() {
        return Err(format!("Download server answered with {}", response.status().as_u16()));
    }
    response
        .json()
        .await
        .map_err(|_| "Received broken metadata from the download server.".to_string())
}

async fn read_json(path: &Path) -> Option<Value> {
    let bytes = tokio::fs::read(path).await.ok()?;
    serde_json::from_slice(&bytes).ok()
}

fn sha1_hex(bytes: &[u8]) -> String {
    hex::encode(Sha1::digest(bytes))
}

async fn file_matches(path: &Path, sha1: Option<&str>, size: u64, deep: bool) -> bool {
    let Ok(meta) = tokio::fs::metadata(path).await else {
        return false;
    };
    if size > 0 && meta.len() != size {
        return false;
    }
    if !deep {
        return true;
    }
    match sha1 {
        Some(expected) => match tokio::fs::read(path).await {
            Ok(bytes) => sha1_hex(&bytes).eq_ignore_ascii_case(expected),
            Err(_) => false,
        },
        None => true,
    }
}

async fn json_file(core: &Core, url: &str, path: &Path, sha1: Option<&str>) -> Result<Value, String> {
    if let Some(expected) = sha1 {
        if let Ok(bytes) = tokio::fs::read(path).await {
            if sha1_hex(&bytes).eq_ignore_ascii_case(expected) {
                if let Ok(value) = serde_json::from_slice(&bytes) {
                    return Ok(value);
                }
            }
        }
    }
    match fetch_json(core, url).await {
        Ok(value) => {
            if let Ok(bytes) = serde_json::to_vec(&value) {
                let _ = write_atomic(path, &bytes);
            }
            Ok(value)
        }
        Err(error) => read_json(path).await.ok_or(error),
    }
}

pub async fn prepare(core: &Arc<Core>, entry: &VersionEntry, major: &str, settings: &Settings) -> Result<Prepared, String> {
    core.set_task("Preparing", format!("Loading Minecraft {}", entry.id), 0.0);

    let version_dir = paths::versions().join(&entry.id);
    let marker = version_dir.join(".rusty-verified");
    let verified = marker.exists();
    let version = json_file(
        core,
        &entry.url,
        &version_dir.join(format!("{}.json", entry.id)),
        Some(entry.sha1.as_str()).filter(|s| !s.is_empty()),
    )
    .await?;

    let features = Features {
        custom_resolution: settings.custom_resolution,
    };
    let mut downloads: Vec<Download> = Vec::new();
    let mut classpath: Vec<PathBuf> = Vec::new();
    let mut seen: HashSet<String> = HashSet::new();

    core.set_task("Preparing", "Resolving Fabric", 0.02);
    let fabric = fabric_profile(core, &entry.id).await;
    if let Some(profile) = &fabric {
        for lib in profile["libraries"].as_array().into_iter().flatten() {
            let Some(name) = lib["name"].as_str() else {
                continue;
            };
            if !seen.insert(library_key(name)) {
                continue;
            }
            let Some(path) = maven_path(name) else {
                continue;
            };
            let base = lib["url"].as_str().unwrap_or("https://maven.fabricmc.net/");
            let base = if base.ends_with('/') { base.to_string() } else { format!("{base}/") };
            let target = paths::libraries().join(&path);
            downloads.push(Download {
                url: format!("{base}{path}"),
                path: target.clone(),
                sha1: lib["sha1"].as_str().map(str::to_string),
                size: lib["size"].as_u64().unwrap_or(0),
            });
            classpath.push(target);
        }
    }

    for lib in version["libraries"].as_array().into_iter().flatten() {
        let Some(name) = lib["name"].as_str() else {
            continue;
        };
        if !rules_allow(&lib["rules"], &features) || foreign_native(name) {
            continue;
        }
        if !seen.insert(library_key(name)) {
            continue;
        }
        let artifact = &lib["downloads"]["artifact"];
        let (url, path, sha1, size) = if artifact.is_object() {
            (
                artifact["url"].as_str().unwrap_or_default().to_string(),
                artifact["path"].as_str().map(str::to_string).or_else(|| maven_path(name)).unwrap_or_default(),
                artifact["sha1"].as_str().map(str::to_string),
                artifact["size"].as_u64().unwrap_or(0),
            )
        } else {
            let Some(path) = maven_path(name) else {
                continue;
            };
            let base = lib["url"].as_str().unwrap_or("https://libraries.minecraft.net/");
            (format!("{base}{path}"), path, None, 0)
        };
        if path.is_empty() {
            continue;
        }
        let target = paths::libraries().join(&path);
        if !url.is_empty() {
            downloads.push(Download {
                url,
                path: target.clone(),
                sha1,
                size,
            });
        }
        classpath.push(target);
    }

    let client = &version["downloads"]["client"];
    let client_jar = version_dir.join(format!("{}.jar", entry.id));
    downloads.push(Download {
        url: client["url"].as_str().unwrap_or_default().to_string(),
        path: client_jar.clone(),
        sha1: client["sha1"].as_str().map(str::to_string),
        size: client["size"].as_u64().unwrap_or(0),
    });
    classpath.push(client_jar);

    core.set_task("Preparing", "Loading asset index", 0.04);
    let index_info = &version["assetIndex"];
    let index_id = index_info["id"].as_str().unwrap_or("legacy").to_string();
    let index = json_file(
        core,
        index_info["url"].as_str().unwrap_or_default(),
        &paths::assets().join("indexes").join(format!("{index_id}.json")),
        index_info["sha1"].as_str(),
    )
    .await?;
    let mut seen_objects = HashSet::new();
    for object in index["objects"].as_object().into_iter().flat_map(|o| o.values()) {
        let Some(hash) = object["hash"].as_str() else {
            continue;
        };
        if hash.len() < 2 || !seen_objects.insert(hash.to_string()) {
            continue;
        }
        let prefix = &hash[..2];
        downloads.push(Download {
            url: format!("{RESOURCES}/{prefix}/{hash}"),
            path: paths::assets().join("objects").join(prefix).join(hash),
            sha1: Some(hash.to_string()),
            size: object["size"].as_u64().unwrap_or(0),
        });
    }

    let mut logging_arg = None;
    let logging = &version["logging"]["client"];
    if let (Some(id), Some(url)) = (logging["file"]["id"].as_str(), logging["file"]["url"].as_str()) {
        let target = paths::assets().join("log_configs").join(id);
        downloads.push(Download {
            url: url.to_string(),
            path: target.clone(),
            sha1: logging["file"]["sha1"].as_str().map(str::to_string),
            size: logging["file"]["size"].as_u64().unwrap_or(0),
        });
        if let Some(argument) = logging["argument"].as_str() {
            logging_arg = Some(argument.replace("${path}", &target.to_string_lossy()));
        }
    }

    let custom_java = settings.custom_java && Path::new(settings.java_path.trim()).is_file();
    let mut java = PathBuf::from(settings.java_path.trim());
    let mut java_marker = None;
    if !custom_java {
        core.set_task("Preparing", "Checking Java runtime", 0.06);
        let component = version["javaVersion"]["component"].as_str().unwrap_or("java-runtime-epsilon").to_string();
        let (files, path, marker_info) = java_runtime(core, &component).await?;
        downloads.extend(files);
        java = path;
        java_marker = marker_info;
    }

    let deep = !verified;
    core.set_task("Checking files", "Verifying game files", 0.08);
    let checked = Arc::new(AtomicUsize::new(0));
    let total_checks = downloads.len().max(1);
    let missing: Vec<Download> = futures_util::stream::iter(downloads.into_iter().map(|download| {
        let checked = checked.clone();
        let core = core.clone();
        async move {
            let ok = !download.url.is_empty()
                && file_matches(&download.path, download.sha1.as_deref(), download.size, deep).await;
            let done = checked.fetch_add(1, Ordering::Relaxed) + 1;
            if done % 200 == 0 {
                core.set_progress("Verifying game files", 0.08 + 0.07 * (done as f64 / total_checks as f64));
            }
            if ok || download.url.is_empty() { None } else { Some(download) }
        }
    }))
    .buffer_unordered(64)
    .filter_map(|d| async move { d })
    .collect()
    .await;

    if !missing.is_empty() {
        download_all(core, missing).await?;
    }

    if let Some((marker_path, marker_value)) = java_marker {
        let _ = write_atomic(&marker_path, marker_value.as_bytes());
    }
    let _ = write_atomic(&marker, b"ok");

    if !java.is_file() {
        return Err("Java could not be found. Check the Java settings.".into());
    }

    let natives_dir = version_dir.join("natives");
    for sub in ["java", "jna", "lwjgl", "netty"] {
        let _ = tokio::fs::create_dir_all(natives_dir.join(sub)).await;
    }

    let game_dir = paths::instance(major);
    let _ = tokio::fs::create_dir_all(game_dir.join("mods")).await;

    core.set_task("Preparing", "Updating mods", 0.98);
    if fabric.is_some() {
        if settings.auto_fabric_api {
            let _ = sync_fabric_api(core, &entry.id, major).await;
        }
        sync_bundled_mods(major).await;
    }

    Ok(Prepared {
        version,
        fabric,
        classpath,
        java,
        natives_dir,
        game_dir,
        logging_arg,
    })
}

async fn java_runtime(core: &Core, component: &str) -> Result<(Vec<Download>, PathBuf, Option<(PathBuf, String)>), String> {
    let root = paths::runtimes().join(component);
    let javaw = root.join("bin").join("javaw.exe");
    let marker = root.join(".rusty-runtime");
    let manifest_cache = paths::meta().join(format!("runtime-{component}.json"));

    let runtimes = match fetch_json(core, JAVA_RUNTIMES).await {
        Ok(value) => value,
        Err(error) => {
            if javaw.is_file() {
                return Ok((Vec::new(), javaw, None));
            }
            return Err(error);
        }
    };
    let Some(info) = runtimes["windows-x64"][component].as_array().and_then(|a| a.first()).cloned() else {
        if javaw.is_file() {
            return Ok((Vec::new(), javaw, None));
        }
        return Err(format!("Mojang does not offer the {component} Java runtime for this system."));
    };
    let manifest_sha = info["manifest"]["sha1"].as_str().unwrap_or_default().to_string();
    let installed = tokio::fs::read_to_string(&marker).await.unwrap_or_default();
    if installed.trim() == manifest_sha && javaw.is_file() {
        return Ok((Vec::new(), javaw, None));
    }
    let manifest = json_file(
        core,
        info["manifest"]["url"].as_str().unwrap_or_default(),
        &manifest_cache,
        Some(manifest_sha.as_str()),
    )
    .await?;
    let mut downloads = Vec::new();
    for (relative, file) in manifest["files"].as_object().into_iter().flatten() {
        let target = root.join(relative);
        match file["type"].as_str() {
            Some("directory") => {
                let _ = tokio::fs::create_dir_all(&target).await;
            }
            Some("file") => {
                let raw = &file["downloads"]["raw"];
                if let Some(url) = raw["url"].as_str() {
                    downloads.push(Download {
                        url: url.to_string(),
                        path: target,
                        sha1: raw["sha1"].as_str().map(str::to_string),
                        size: raw["size"].as_u64().unwrap_or(0),
                    });
                }
            }
            _ => {}
        }
    }
    Ok((downloads, javaw, Some((marker, manifest_sha))))
}

async fn fabric_profile(core: &Core, game: &str) -> Option<Value> {
    let cache = paths::meta().join("fabric").join(format!("{game}.json"));
    let fresh = async {
        let loaders = fetch_json(core, &format!("{FABRIC_META}/versions/loader/{game}")).await.ok()?;
        let list = loaders.as_array()?;
        let chosen = list
            .iter()
            .find(|l| l["loader"]["stable"].as_bool() == Some(true))
            .or_else(|| list.first())?;
        let loader = chosen["loader"]["version"].as_str()?;
        fetch_json(core, &format!("{FABRIC_META}/versions/loader/{game}/{loader}/profile/json"))
            .await
            .ok()
    }
    .await;
    match fresh {
        Some(profile) if profile["mainClass"].is_string() => {
            if let Ok(bytes) = serde_json::to_vec(&profile) {
                let _ = write_atomic(&cache, &bytes);
            }
            Some(profile)
        }
        _ => read_json(&cache).await,
    }
}

async fn download_all(core: &Arc<Core>, items: Vec<Download>) -> Result<(), String> {
    let total_bytes: u64 = items.iter().map(|d| d.size.max(1)).sum();
    let total_files = items.len();
    let done_bytes = Arc::new(AtomicU64::new(0));
    let done_files = Arc::new(AtomicUsize::new(0));
    core.set_task("Downloading", format!("0 of {total_files} files"), 0.15);

    let ticker = {
        let core = core.clone();
        let done_bytes = done_bytes.clone();
        let done_files = done_files.clone();
        tokio::spawn(async move {
            loop {
                tokio::time::sleep(Duration::from_millis(120)).await;
                let bytes = done_bytes.load(Ordering::Relaxed);
                let files = done_files.load(Ordering::Relaxed);
                let fraction = (bytes as f64 / total_bytes as f64).min(1.0);
                core.set_progress(
                    format!("{} of {} - {files} of {total_files} files", human_size(bytes), human_size(total_bytes)),
                    0.15 + 0.8 * fraction,
                );
            }
        })
    };

    let results: Vec<Result<(), String>> = futures_util::stream::iter(items.into_iter().map(|item| {
        let done_bytes = done_bytes.clone();
        let done_files = done_files.clone();
        let core = core.clone();
        async move {
            let result = download_one(&core, &item, &done_bytes).await;
            done_files.fetch_add(1, Ordering::Relaxed);
            result
        }
    }))
    .buffer_unordered(24)
    .collect()
    .await;
    ticker.abort();

    let failures: Vec<String> = results.into_iter().filter_map(Result::err).collect();
    if let Some(first) = failures.first() {
        return Err(if failures.len() == 1 {
            first.clone()
        } else {
            format!("{} files failed to download. {first}", failures.len())
        });
    }
    Ok(())
}

async fn download_one(core: &Core, item: &Download, progress: &AtomicU64) -> Result<(), String> {
    let name = item
        .path
        .file_name()
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_default();
    let mut last_error = String::new();
    for attempt in 0..4 {
        if attempt > 0 {
            tokio::time::sleep(Duration::from_millis(400 * attempt)).await;
        }
        let mut counted = 0u64;
        match try_download(core, item, progress, &mut counted).await {
            Ok(()) => {
                if item.size > counted {
                    progress.fetch_add(item.size - counted, Ordering::Relaxed);
                }
                return Ok(());
            }
            Err(error) => {
                progress.fetch_sub(counted.min(progress.load(Ordering::Relaxed)), Ordering::Relaxed);
                last_error = error;
            }
        }
    }
    Err(format!("Could not download {name}: {last_error}"))
}

async fn try_download(core: &Core, item: &Download, progress: &AtomicU64, counted: &mut u64) -> Result<(), String> {
    if let Some(parent) = item.path.parent() {
        tokio::fs::create_dir_all(parent).await.map_err(|e| e.to_string())?;
    }
    let response = core
        .http
        .get(&item.url)
        .send()
        .await
        .map_err(|_| "connection failed".to_string())?;
    if !response.status().is_success() {
        return Err(format!("server answered {}", response.status().as_u16()));
    }
    let partial = item.path.with_extension("rusty-part");
    let mut file = tokio::fs::File::create(&partial).await.map_err(|e| e.to_string())?;
    let mut hasher = Sha1::new();
    let mut stream = response.bytes_stream();
    while let Some(chunk) = stream.next().await {
        let chunk = chunk.map_err(|_| "connection interrupted".to_string())?;
        hasher.update(&chunk);
        file.write_all(&chunk).await.map_err(|e| e.to_string())?;
        let len = chunk.len() as u64;
        if *counted + len <= item.size.max(1) {
            progress.fetch_add(len, Ordering::Relaxed);
            *counted += len;
        }
    }
    file.flush().await.map_err(|e| e.to_string())?;
    drop(file);
    if let Some(expected) = &item.sha1 {
        let actual = hex::encode(hasher.finalize());
        if !actual.eq_ignore_ascii_case(expected) {
            let _ = tokio::fs::remove_file(&partial).await;
            return Err("checksum mismatch".into());
        }
    }
    let _ = tokio::fs::remove_file(&item.path).await;
    tokio::fs::rename(&partial, &item.path).await.map_err(|e| e.to_string())
}

pub fn human_size(bytes: u64) -> String {
    let b = bytes as f64;
    if b >= 1024.0 * 1024.0 * 1024.0 {
        format!("{:.1} GB", b / (1024.0 * 1024.0 * 1024.0))
    } else if b >= 1024.0 * 1024.0 {
        format!("{:.1} MB", b / (1024.0 * 1024.0))
    } else if b >= 1024.0 {
        format!("{:.0} KB", b / 1024.0)
    } else {
        format!("{bytes} B")
    }
}

fn managed_file(major: &str) -> PathBuf {
    paths::instance(major).join(".rustyclient").join("managed.json")
}

async fn sync_fabric_api(core: &Core, game: &str, major: &str) -> Option<()> {
    let mods = paths::mods(major);
    let record_path = managed_file(major);
    let mut record = read_json(&record_path).await.unwrap_or_else(|| json!({}));
    let current_file = record["fabricApi"]["file"].as_str().map(str::to_string);
    let current_game = record["fabricApi"]["game"].as_str().map(str::to_string);

    if let Some(file) = &current_file {
        if mods.join(format!("{file}.disabled")).exists() {
            return Some(());
        }
    } else if let Ok(mut entries) = tokio::fs::read_dir(&mods).await {
        while let Ok(Some(entry)) = entries.next_entry().await {
            let name = entry.file_name().to_string_lossy().to_ascii_lowercase();
            if name.starts_with("fabric-api") {
                return Some(());
            }
        }
    }

    let query = format!(
        "{MODRINTH}/project/{FABRIC_API_PROJECT}/version?loaders={}&game_versions={}",
        urlencode("[\"fabric\"]"),
        urlencode(&format!("[\"{game}\"]"))
    );
    let versions = fetch_json(core, &query).await.ok()?;
    let list = versions.as_array()?;
    let chosen = list
        .iter()
        .find(|v| v["version_type"].as_str() == Some("release"))
        .or_else(|| list.first())?;
    let files = chosen["files"].as_array()?;
    let file = files
        .iter()
        .find(|f| f["primary"].as_bool() == Some(true))
        .or_else(|| files.first())?;
    let filename = file["filename"].as_str()?.to_string();
    let url = file["url"].as_str()?.to_string();

    let target = mods.join(&filename);
    let up_to_date = current_file.as_deref() == Some(filename.as_str())
        && current_game.as_deref() == Some(game)
        && target.exists();
    if up_to_date {
        return Some(());
    }
    let download = Download {
        url,
        path: target,
        sha1: file["hashes"]["sha1"].as_str().map(str::to_string),
        size: file["size"].as_u64().unwrap_or(0),
    };
    let counter = AtomicU64::new(0);
    download_one(core, &download, &counter).await.ok()?;
    if let Some(old) = current_file {
        if old != filename {
            let _ = tokio::fs::remove_file(mods.join(old)).await;
        }
    }
    record["fabricApi"] = json!({ "file": filename, "game": game });
    let _ = write_atomic(&record_path, &serde_json::to_vec_pretty(&record).ok()?);
    Some(())
}

async fn sync_bundled_mods(major: &str) {
    let source = paths::bundled_client_mods(major);
    let Ok(mut entries) = tokio::fs::read_dir(&source).await else {
        return;
    };
    let mods = paths::mods(major);
    while let Ok(Some(entry)) = entries.next_entry().await {
        let name = entry.file_name().to_string_lossy().to_string();
        if !name.to_ascii_lowercase().ends_with(".jar") {
            continue;
        }
        let target = mods.join(&name);
        let source_len = entry.metadata().await.map(|m| m.len()).unwrap_or(0);
        let target_len = tokio::fs::metadata(&target).await.map(|m| m.len()).ok();
        if target_len != Some(source_len) && !mods.join(format!("{name}.disabled")).exists() {
            let _ = tokio::fs::copy(entry.path(), &target).await;
        }
    }
}

fn urlencode(value: &str) -> String {
    url::form_urlencoded::byte_serialize(value.as_bytes()).collect()
}

pub fn resolve_entry(id: &str) -> Option<(VersionEntry, String)> {
    let entry = content::version_entry(id)?;
    let major = content::major_of(id)?;
    Some((entry, major))
}
