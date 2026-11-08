use std::io::Read;
use std::path::{Path, PathBuf};

use serde::Serialize;
use serde_json::Value;

use crate::paths;

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ModInfo {
    pub file: String,
    pub name: String,
    pub version: String,
    pub description: String,
    pub enabled: bool,
    pub size: u64,
    pub icon: bool,
    pub managed: bool,
}

fn base_name(file: &str) -> &str {
    file.strip_suffix(".disabled").unwrap_or(file)
}

fn valid_file(file: &str) -> bool {
    !file.is_empty()
        && !file.contains(['/', '\\', ':'])
        && !file.contains("..")
        && base_name(file).to_ascii_lowercase().ends_with(".jar")
}

fn read_metadata(path: &Path) -> Option<Value> {
    let file = std::fs::File::open(path).ok()?;
    let mut archive = zip::ZipArchive::new(file).ok()?;
    let mut entry = archive.by_name("fabric.mod.json").ok()?;
    let mut text = String::new();
    entry.read_to_string(&mut text).ok()?;
    let cleaned: String = text.chars().map(|c| if c == '\n' || c == '\r' || c == '\t' { ' ' } else { c }).collect();
    serde_json::from_str(&cleaned).ok()
}

fn icon_path(meta: &Value) -> Option<String> {
    match &meta["icon"] {
        Value::String(path) => Some(path.clone()),
        Value::Object(sizes) => sizes
            .iter()
            .max_by_key(|(size, _)| size.parse::<u32>().unwrap_or(0))
            .and_then(|(_, path)| path.as_str().map(str::to_string)),
        _ => None,
    }
}

fn managed_files(major: &str) -> Vec<String> {
    let record = std::fs::read(paths::instance(major).join(".rustyclient").join("managed.json"))
        .ok()
        .and_then(|bytes| serde_json::from_slice::<Value>(&bytes).ok())
        .unwrap_or(Value::Null);
    let mut files = Vec::new();
    if let Some(file) = record["fabricApi"]["file"].as_str() {
        files.push(file.to_string());
    }
    if let Ok(entries) = std::fs::read_dir(paths::bundled_client_mods(major)) {
        files.extend(entries.filter_map(Result::ok).map(|e| e.file_name().to_string_lossy().to_string()));
    }
    files
}

pub fn list(major: &str) -> Vec<ModInfo> {
    let dir = paths::mods(major);
    let managed = managed_files(major);
    let Ok(entries) = std::fs::read_dir(&dir) else {
        return Vec::new();
    };
    let mut mods: Vec<ModInfo> = entries
        .filter_map(Result::ok)
        .filter_map(|entry| {
            let file = entry.file_name().to_string_lossy().to_string();
            if !valid_file(&file) || !entry.path().is_file() {
                return None;
            }
            let meta = read_metadata(&entry.path());
            let fallback = base_name(&file).trim_end_matches(".jar").to_string();
            let name = meta
                .as_ref()
                .and_then(|m| m["name"].as_str())
                .map(str::to_string)
                .unwrap_or(fallback);
            Some(ModInfo {
                enabled: !file.ends_with(".disabled"),
                managed: managed.iter().any(|m| m == base_name(&file)),
                name,
                version: meta.as_ref().and_then(|m| m["version"].as_str()).unwrap_or_default().to_string(),
                description: meta
                    .as_ref()
                    .and_then(|m| m["description"].as_str())
                    .unwrap_or_default()
                    .chars()
                    .take(160)
                    .collect(),
                icon: meta.as_ref().and_then(icon_path).is_some(),
                size: entry.metadata().map(|m| m.len()).unwrap_or(0),
                file,
            })
        })
        .collect();
    mods.sort_by_key(|m| m.name.to_lowercase());
    mods
}

fn mod_path(major: &str, file: &str) -> Option<PathBuf> {
    valid_file(file).then(|| paths::mods(major).join(file))
}

pub fn icon(major: &str, file: &str) -> Option<Vec<u8>> {
    let path = mod_path(major, file)?;
    let meta = read_metadata(&path)?;
    let icon = icon_path(&meta)?;
    let reader = std::fs::File::open(&path).ok()?;
    let mut archive = zip::ZipArchive::new(reader).ok()?;
    let mut entry = archive.by_name(icon.trim_start_matches('/')).ok()?;
    let mut bytes = Vec::new();
    entry.read_to_end(&mut bytes).ok()?;
    Some(bytes)
}

pub fn set_enabled(major: &str, file: &str, enabled: bool) -> Result<(), String> {
    let path = mod_path(major, file).ok_or("Invalid mod file.")?;
    let base = base_name(file);
    let target = if enabled {
        paths::mods(major).join(base)
    } else {
        paths::mods(major).join(format!("{base}.disabled"))
    };
    if path == target {
        return Ok(());
    }
    std::fs::rename(&path, &target).map_err(|e| format!("Could not change the mod: {e}"))
}

pub fn delete(major: &str, file: &str) -> Result<(), String> {
    let path = mod_path(major, file).ok_or("Invalid mod file.")?;
    std::fs::remove_file(path).map_err(|e| format!("Could not remove the mod: {e}"))
}

pub fn add(major: &str, name: &str, bytes: &[u8]) -> Result<(), String> {
    let name: String = name
        .chars()
        .filter(|c| !matches!(c, '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|'))
        .collect();
    if !name.to_ascii_lowercase().ends_with(".jar") || name.contains("..") {
        return Err("Only .jar mod files can be added.".into());
    }
    if bytes.len() < 4 || &bytes[..2] != b"PK" {
        return Err(format!("{name} is not a valid mod file."));
    }
    let dir = paths::mods(major);
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    std::fs::write(dir.join(&name), bytes).map_err(|e| format!("Could not save {name}: {e}"))
}
