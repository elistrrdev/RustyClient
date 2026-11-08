use std::collections::HashMap;
use std::time::Duration;

use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::config::write_atomic;
use crate::paths;
use crate::state::{BROWSER_AGENT, core, now_secs};

pub const SUPPORTED_MAJORS: [&str; 3] = ["26.3", "26.2", "26.1"];
const MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const LAUNCHER_CONTENT: &str = "https://launchercontent.mojang.com";
const REFRESH_AFTER: u64 = 30 * 60;

static FETCH_LOCK: tokio::sync::Mutex<()> = tokio::sync::Mutex::const_new(());

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VersionEntry {
    pub id: String,
    pub url: String,
    pub sha1: String,
    pub release_time: String,
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VersionGroup {
    pub major: String,
    pub versions: Vec<VersionEntry>,
    pub image: String,
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NewsItem {
    pub id: String,
    pub title: String,
    pub category: String,
    pub date: String,
    pub image: String,
    pub url: String,
    pub text: String,
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(default, rename_all = "camelCase")]
pub struct Content {
    pub fetched: u64,
    pub groups: Vec<VersionGroup>,
    pub news: Vec<NewsItem>,
    pub images: HashMap<String, String>,
}

fn cache_file() -> std::path::PathBuf {
    paths::cache().join("content.json")
}

pub fn cached() -> Content {
    std::fs::read(cache_file())
        .ok()
        .and_then(|bytes| serde_json::from_slice(&bytes).ok())
        .unwrap_or_default()
}

pub async fn get(force: bool) -> Content {
    let _guard = FETCH_LOCK.lock().await;
    let cached = cached();
    let fresh_enough = now_secs().saturating_sub(cached.fetched) < REFRESH_AFTER;
    if !force && fresh_enough && !cached.groups.is_empty() && !cached.news.is_empty() {
        return cached;
    }
    match tokio::time::timeout(Duration::from_secs(25), fetch(&cached)).await {
        Ok(Some(content)) => {
            if let Ok(json) = serde_json::to_vec(&content) {
                let _ = write_atomic(&cache_file(), &json);
            }
            content
        }
        _ => cached,
    }
}

async fn fetch_json(url: &str) -> Option<Value> {
    core().http.get(url).send().await.ok()?.error_for_status().ok()?.json().await.ok()
}

async fn fetch(previous: &Content) -> Option<Content> {
    let news_url = format!("{LAUNCHER_CONTENT}/v2/news.json");
    let patch_url = format!("{LAUNCHER_CONTENT}/v2/javaPatchNotes.json");
    let (manifest, news, patch_notes) = tokio::join!(
        fetch_json(MANIFEST_URL),
        fetch_json(&news_url),
        fetch_json(&patch_url),
    );

    let groups = match &manifest {
        Some(manifest) => {
            if let Ok(bytes) = serde_json::to_vec(manifest) {
                let _ = write_atomic(&paths::meta().join("version_manifest_v2.json"), &bytes);
            }
            build_groups(manifest)
        }
        None => previous.groups.clone(),
    };
    if groups.is_empty() {
        return None;
    }

    let patch_entries: Vec<Value> = patch_notes
        .as_ref()
        .and_then(|p| p["entries"].as_array().cloned())
        .unwrap_or_default();

    let mut images = previous.images.clone();
    let mut lookups = Vec::new();
    for group in &groups {
        if !images.contains_key(&group.major) {
            lookups.push(group.major.clone());
        }
    }
    let found = futures_util::future::join_all(lookups.iter().map(|major| article_image(major))).await;
    for (major, image) in lookups.into_iter().zip(found) {
        if let Some(image) = image {
            images.insert(major, image);
        }
    }

    let groups = groups
        .into_iter()
        .map(|mut group| {
            group.image = images
                .get(&group.major)
                .cloned()
                .or_else(|| patch_image(&patch_entries, &group.major))
                .unwrap_or_default();
            group
        })
        .collect();

    let mut items = Vec::new();
    match &news {
        Some(news) => items.extend(build_news(news)),
        None => items.extend(previous.news.iter().filter(|n| !n.id.starts_with("patch-")).cloned()),
    }
    if patch_notes.is_some() {
        items.extend(build_patch_news(&patch_entries));
    } else {
        items.extend(previous.news.iter().filter(|n| n.id.starts_with("patch-")).cloned());
    }
    items.sort_by(|a, b| b.date.cmp(&a.date));
    items.truncate(30);

    Some(Content {
        fetched: now_secs(),
        groups,
        news: items,
        images,
    })
}

fn version_major(id: &str) -> Option<String> {
    let parts: Vec<&str> = id.split('.').collect();
    if !(2..=3).contains(&parts.len()) || !parts.iter().all(|p| !p.is_empty() && p.chars().all(|c| c.is_ascii_digit())) {
        return None;
    }
    let major = format!("{}.{}", parts[0], parts[1]);
    SUPPORTED_MAJORS.contains(&major.as_str()).then_some(major)
}

fn build_groups(manifest: &Value) -> Vec<VersionGroup> {
    let mut groups: Vec<VersionGroup> = SUPPORTED_MAJORS
        .iter()
        .map(|major| VersionGroup {
            major: major.to_string(),
            ..Default::default()
        })
        .collect();
    for version in manifest["versions"].as_array().into_iter().flatten() {
        if version["type"].as_str() != Some("release") {
            continue;
        }
        let id = version["id"].as_str().unwrap_or_default();
        let Some(major) = version_major(id) else {
            continue;
        };
        if let Some(group) = groups.iter_mut().find(|g| g.major == major) {
            group.versions.push(VersionEntry {
                id: id.to_string(),
                url: version["url"].as_str().unwrap_or_default().to_string(),
                sha1: version["sha1"].as_str().unwrap_or_default().to_string(),
                release_time: version["releaseTime"].as_str().unwrap_or_default().to_string(),
            });
        }
    }
    for group in &mut groups {
        group.versions.sort_by(|a, b| b.release_time.cmp(&a.release_time));
    }
    groups.retain(|g| !g.versions.is_empty());
    groups
}

pub fn version_entry(id: &str) -> Option<VersionEntry> {
    let content = cached();
    content
        .groups
        .iter()
        .flat_map(|g| g.versions.iter())
        .find(|v| v.id == id)
        .cloned()
        .or_else(|| {
            let bytes = std::fs::read(paths::meta().join("version_manifest_v2.json")).ok()?;
            let manifest: Value = serde_json::from_slice(&bytes).ok()?;
            build_groups(&manifest)
                .into_iter()
                .flat_map(|g| g.versions)
                .find(|v| v.id == id)
        })
}

pub fn major_of(id: &str) -> Option<String> {
    version_major(id)
}

async fn article_image(major: &str) -> Option<String> {
    let slug = major.replace('.', "-");
    let url = format!("https://www.minecraft.net/en-us/article/minecraft-java-edition-{slug}");
    let html = core()
        .http
        .get(&url)
        .header("User-Agent", BROWSER_AGENT)
        .header("Accept-Language", "en-US,en;q=0.9")
        .send()
        .await
        .ok()?
        .error_for_status()
        .ok()?
        .text()
        .await
        .ok()?;
    let marker = "property=\"og:image\"";
    let start = html.find(marker)?;
    let tail = &html[start..];
    let content_at = tail.find("content=\"")? + "content=\"".len();
    let end = tail[content_at..].find('"')?;
    let image = tail[content_at..content_at + end].trim().to_string();
    if image.starts_with("https://") {
        Some(image)
    } else if image.starts_with('/') {
        Some(format!("https://www.minecraft.net{image}"))
    } else {
        None
    }
}

fn absolute(path: &str) -> String {
    if path.starts_with("http") {
        path.to_string()
    } else {
        format!("{LAUNCHER_CONTENT}{path}")
    }
}

fn patch_image(entries: &[Value], version: &str) -> Option<String> {
    entries
        .iter()
        .find(|e| e["version"].as_str() == Some(version))
        .and_then(|e| e["image"]["url"].as_str())
        .map(absolute)
}

fn build_news(news: &Value) -> Vec<NewsItem> {
    news["entries"]
        .as_array()
        .into_iter()
        .flatten()
        .filter(|entry| {
            entry["newsType"]
                .as_array()
                .map(|types| types.iter().any(|t| t.as_str() == Some("Java")))
                .unwrap_or(false)
        })
        .filter_map(|entry| {
            let title = entry["title"].as_str()?.trim().to_string();
            let url = entry["readMoreLink"].as_str()?.to_string();
            let image = entry["newsPageImage"]["url"]
                .as_str()
                .or_else(|| entry["playPageImage"]["url"].as_str())
                .map(absolute)
                .unwrap_or_default();
            let types: Vec<&str> = entry["newsType"]
                .as_array()
                .map(|t| t.iter().filter_map(Value::as_str).collect())
                .unwrap_or_default();
            let category = if title.to_lowercase().contains("realms") {
                "Realms"
            } else if title.to_lowercase().contains("minecraft live") {
                "Event"
            } else if types.contains(&"Bedrock") {
                "Minecraft"
            } else {
                "Java Edition"
            };
            Some(NewsItem {
                id: entry["id"].as_str().unwrap_or(&title).to_string(),
                title,
                category: category.into(),
                date: entry["date"].as_str().unwrap_or_default().chars().take(10).collect(),
                image,
                url,
                text: entry["text"].as_str().unwrap_or_default().trim().to_string(),
            })
        })
        .collect()
}

fn article_slug(version: &str, release: bool) -> String {
    let dashed = version.replace('.', "-");
    if release {
        return format!("minecraft-java-edition-{dashed}");
    }
    let dashed = dashed.replace("-pre-", "-pre-release-").replace("-rc-", "-release-candidate-");
    format!("minecraft-{dashed}")
}

fn build_patch_news(entries: &[Value]) -> Vec<NewsItem> {
    entries
        .iter()
        .filter_map(|entry| {
            let version = entry["version"].as_str()?;
            let kind = entry["type"].as_str()?;
            let release = kind == "release";
            if !release && kind != "snapshot" {
                return None;
            }
            let category = if release {
                "Release"
            } else if version.contains("-pre-") {
                "Pre-release"
            } else if version.contains("-rc-") {
                "Release candidate"
            } else {
                "Snapshot"
            };
            let title = if release {
                format!("Minecraft {version} is out now")
            } else {
                entry["title"].as_str().unwrap_or(version).to_string()
            };
            Some(NewsItem {
                id: format!("patch-{version}"),
                title,
                category: category.into(),
                date: entry["date"].as_str().unwrap_or_default().chars().take(10).collect(),
                image: entry["image"]["url"].as_str().map(absolute).unwrap_or_default(),
                url: format!("https://www.minecraft.net/en-us/article/{}", article_slug(version, release)),
                text: entry["shortText"].as_str().unwrap_or_default().to_string(),
            })
        })
        .take(10)
        .collect()
}
