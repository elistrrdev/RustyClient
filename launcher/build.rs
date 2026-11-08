use std::fs::File;
use std::io::{BufWriter, Write};
use std::path::{Path, PathBuf};

use sha1::{Digest, Sha1};

const RUNTIME_EXTENSIONS: [&str; 4] = ["dll", "dat", "bin", "pak"];

fn main() {
    let portable = std::env::var_os("CARGO_FEATURE_PORTABLE").is_some();
    let setup = std::env::var_os("CARGO_FEATURE_SETUP").is_some();

    println!("cargo::rerun-if-changed=RustyClient.rc");
    println!("cargo::rerun-if-changed=RustyClient.manifest");
    println!("cargo::rerun-if-changed=RustyClientSetup.rc");
    println!("cargo::rerun-if-changed=RustyClientSetup.manifest");
    println!("cargo::rerun-if-changed=ui");
    println!("cargo::rerun-if-env-changed=DEP_CEF_DLL_WRAPPER_CEF_DIR");
    println!("cargo::rerun-if-env-changed=RUSTY_SETUP_PAYLOAD");

    let resource = if setup { "RustyClientSetup.rc" } else { "RustyClient.rc" };
    embed_resource::compile(resource, embed_resource::NONE)
        .manifest_required()
        .unwrap();

    println!("cargo::rustc-link-arg-bins=/DELAYLOAD:libcef.dll");
    println!("cargo::rustc-link-lib=delayimp");

    let out_dir = PathBuf::from(std::env::var("OUT_DIR").unwrap());

    if portable || setup {
        let cef_dir = PathBuf::from(std::env::var("DEP_CEF_DLL_WRAPPER_CEF_DIR").expect("cef directory from cef-dll-sys"));
        let files = runtime_files(&cef_dir);
        let mut key = Sha1::new();
        for (path, name) in &files {
            println!("cargo::rerun-if-changed={}", path.display());
            let meta = std::fs::metadata(path).expect("cef runtime file");
            key.update(name.as_bytes());
            key.update(meta.len().to_le_bytes());
            if let Ok(since) = meta.modified().map(|m| m.duration_since(std::time::UNIX_EPOCH)) {
                if let Ok(since) = since {
                    key.update(since.as_secs().to_le_bytes());
                }
            }
        }
        let id = hex::encode(key.finalize())[..16].to_string();
        println!("cargo::rustc-env=RUSTY_CEF_ID={id}");

        let pack = out_dir.join("cef-runtime.zst");
        let stamp = out_dir.join("cef-runtime.id");
        let current = std::fs::read_to_string(&stamp).unwrap_or_default();
        if current.trim() != id || !pack.exists() {
            write_pack(&files, &pack);
            std::fs::write(&stamp, &id).unwrap();
        }
    }

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

fn write_pack(files: &[(PathBuf, String)], target: &Path) {
    let partial = target.with_extension("partial");
    let output = BufWriter::new(File::create(&partial).unwrap());
    let mut encoder = zstd::stream::Encoder::new(output, 19).unwrap();
    let workers = std::thread::available_parallelism().map(|n| n.get() as u32).unwrap_or(4);
    encoder.multithread(workers).unwrap();
    encoder.long_distance_matching(true).unwrap();
    encoder.window_log(27).unwrap();

    encoder.write_all(b"RCEF").unwrap();
    encoder.write_all(&(files.len() as u32).to_le_bytes()).unwrap();
    for (path, name) in files {
        let size = std::fs::metadata(path).unwrap().len();
        encoder.write_all(&(name.len() as u16).to_le_bytes()).unwrap();
        encoder.write_all(name.as_bytes()).unwrap();
        encoder.write_all(&size.to_le_bytes()).unwrap();
    }
    for (path, _) in files {
        let mut input = File::open(path).unwrap();
        std::io::copy(&mut input, &mut encoder).unwrap();
    }
    encoder.finish().unwrap().flush().unwrap();
    std::fs::rename(&partial, target).unwrap();
}
