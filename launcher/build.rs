use std::fs::File;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};

use sha1::{Digest, Sha1};

const RUNTIME_EXTENSIONS: [&str; 4] = ["dll", "dat", "bin", "pak"];

fn main() {
    let portable = std::env::var_os("CARGO_FEATURE_PORTABLE").is_some();
    let setup = std::env::var_os("CARGO_FEATURE_SETUP").is_some();

    println!("cargo::rerun-if-changed=RustyClient.rc");
    println!("cargo::rerun-if-changed=RustyClient.ico");
    println!("cargo::rerun-if-changed=RustyClientWindow.ico");
    println!("cargo::rerun-if-changed=RustyClient.manifest");
    println!("cargo::rerun-if-changed=RustyClientSetup.rc");
    println!("cargo::rerun-if-changed=RustyClientSetup.manifest");
    println!("cargo::rerun-if-changed=ui");
    println!("cargo::rerun-if-env-changed=DEP_CEF_DLL_WRAPPER_CEF_DIR");
    println!("cargo::rerun-if-env-changed=RUSTY_SETUP_PAYLOAD");

    let rustc = std::env::var("RUSTC").unwrap_or_else(|_| "rustc".into());
    let rustc_version = std::process::Command::new(rustc)
        .arg("--version")
        .output()
        .ok()
        .and_then(|o| String::from_utf8(o.stdout).ok())
        .and_then(|v| v.split_whitespace().nth(1).map(str::to_string))
        .unwrap_or_default();
    println!("cargo::rustc-env=RUSTY_RUSTC_VERSION={rustc_version}");

    let resource = if setup { "RustyClientSetup.rc" } else { "RustyClient.rc" };
    embed_resource::compile(resource, embed_resource::NONE)
        .manifest_required()
        .unwrap();

    println!("cargo::rustc-link-arg-bins=/DELAYLOAD:libcef.dll");
    println!("cargo::rustc-link-lib=delayimp");

    let out_dir = PathBuf::from(std::env::var("OUT_DIR").unwrap());

    if portable || setup {
        let cef_dir = PathBuf::from(std::env::var("DEP_CEF_DLL_WRAPPER_CEF_DIR").expect("cef directory from cef-dll-sys"));
        let archive_json = cef_dir.join("archive.json");
        println!("cargo::rerun-if-changed={}", archive_json.display());
        let files = runtime_files(&cef_dir);
        let mut key = Sha1::new();
        key.update(std::fs::read(&archive_json).expect("archive.json in the cef directory"));
        for (path, name) in &files {
            println!("cargo::rerun-if-changed={}", path.display());
            let meta = std::fs::metadata(path).expect("cef runtime file");
            key.update(name.as_bytes());
            key.update(meta.len().to_le_bytes());
            if let Ok(Ok(since)) = meta.modified().map(|m| m.duration_since(std::time::UNIX_EPOCH)) {
                key.update(since.as_secs().to_le_bytes());
            }
        }
        let key = hex::encode(key.finalize());

        let table = out_dir.join("cef_runtime.rs");
        let stamp = out_dir.join("cef_runtime.key");
        let current = std::fs::read_to_string(&stamp).unwrap_or_default();
        if current.trim() != key || !table.exists() {
            write_runtime_table(&archive_json, &files, &table);
            std::fs::write(&stamp, &key).unwrap();
        }
    }

    write_client_mods(&out_dir, setup);

    if setup {
        let payload = PathBuf::from(std::env::var("RUSTY_SETUP_PAYLOAD").expect("RUSTY_SETUP_PAYLOAD must point at the installed build"));
        println!("cargo::rerun-if-changed={}", payload.display());
        let bytes = std::fs::read(&payload).expect("read setup payload");
        let compressed = zstd::stream::encode_all(bytes.as_slice(), 19).unwrap();
        std::fs::write(out_dir.join("payload.zst"), compressed).unwrap();
    }
}

fn runtime_files(cef_dir: &Path) -> Vec<(PathBuf, String)> {
    let mut files = Vec::new();
    for entry in std::fs::read_dir(cef_dir).expect("read cef directory") {
        let entry = entry.unwrap();
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let name = entry.file_name().to_string_lossy().to_string();
        let extension = path.extension().map(|e| e.to_string_lossy().to_ascii_lowercase()).unwrap_or_default();
        let keep = RUNTIME_EXTENSIONS.contains(&extension.as_str()) || name.eq_ignore_ascii_case("vk_swiftshader_icd.json");
        if keep {
            files.push((path, name));
        }
    }
    let locale = cef_dir.join("locales").join("en-US.pak");
    if locale.is_file() {
        files.push((locale, "locales/en-US.pak".to_string()));
    }
    files.sort_by(|a, b| a.1.cmp(&b.1));
    assert!(files.iter().any(|(_, n)| n == "libcef.dll"), "libcef.dll missing from the cef directory");
    files
}

fn write_runtime_table(archive_json: &Path, files: &[(PathBuf, String)], target: &Path) {
    let archive: serde_json::Value = serde_json::from_slice(&std::fs::read(archive_json).unwrap()).expect("valid archive.json");
    let name = archive["name"].as_str().expect("archive name in archive.json");
    let sha1 = archive["sha1"].as_str().expect("archive sha1 in archive.json");
    assert!(name.ends_with(".tar.bz2") && sha1.len() == 40, "unexpected archive.json");

    let mut rows = String::new();
    let mut buffer = vec![0u8; 1 << 20];
    for (path, file) in files {
        let mut input = File::open(path).unwrap();
        let mut hash = Sha1::new();
        let mut size = 0u64;
        loop {
            let read = input.read(&mut buffer).unwrap();
            if read == 0 {
                break;
            }
            hash.update(&buffer[..read]);
            size += read as u64;
        }
        rows.push_str(&format!("    (\"{file}\", {size}, \"{}\"),\n", hex::encode(hash.finalize())));
    }
    let source = format!(
        "pub const ID: &str = \"{}\";\npub const ARCHIVE: &str = \"{name}\";\npub const ARCHIVE_SHA1: &str = \"{sha1}\";\npub const FILES: &[(&str, u64, &str)] = &[\n{rows}];\n",
        &sha1[..16]
    );
    std::fs::write(target, source).unwrap();
}

fn write_client_mods(out_dir: &Path, setup: bool) {
    println!("cargo::rerun-if-env-changed=RUSTY_CLIENT_JARS");
    let client = PathBuf::from(std::env::var("CARGO_MANIFEST_DIR").unwrap()).join("..").join("client");
    let properties = client.join("gradle.properties");
    println!("cargo::rerun-if-changed={}", properties.display());
    let mod_version = std::fs::read_to_string(&properties)
        .unwrap_or_default()
        .lines()
        .find_map(|line| line.trim().strip_prefix("mod_version=").map(|v| v.trim().to_string()))
        .unwrap_or_default();

    let required = std::env::var_os("RUSTY_CLIENT_JARS").map(PathBuf::from);
    let dir = required.clone().unwrap_or_else(|| client.join("build").join("jars"));
    println!("cargo::rerun-if-changed={}", dir.display());

    let mut jars: Vec<(String, PathBuf)> = Vec::new();
    if !setup {
        if let Ok(entries) = std::fs::read_dir(&dir) {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                if let Some(major) = name.strip_prefix("rustyclient-").and_then(|n| n.strip_suffix(".jar")) {
                    println!("cargo::rerun-if-changed={}", entry.path().display());
                    jars.push((major.to_string(), entry.path()));
                }
            }
        }
        jars.sort();
        if required.is_some() && jars.is_empty() {
            panic!("no client jars found in {}", dir.display());
        }
    }

    let mut table = String::new();
    let mut output = zstd::stream::Encoder::new(Vec::new(), 19).unwrap();
    for (major, path) in &jars {
        let bytes = std::fs::read(path).expect("read client jar");
        table.push_str(&format!("    (\"{major}\", {}, \"{}\"),\n", bytes.len(), hex::encode(Sha1::digest(&bytes))));
        output.write_all(&bytes).unwrap();
    }
    let pack = output.finish().unwrap();
    std::fs::write(out_dir.join("client-mods.zst"), pack).unwrap();
    let source = format!("pub const MOD_VERSION: &str = \"{mod_version}\";\npub const JARS: &[(&str, u64, &str)] = &[\n{table}];\n");
    std::fs::write(out_dir.join("client_mods.rs"), source).unwrap();
}
