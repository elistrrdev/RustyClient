use std::io::Read;
use std::path::PathBuf;

use sha1::{Digest, Sha1};

use crate::config::write_atomic;
use crate::paths;

include!(concat!(env!("OUT_DIR"), "/client_mods.rs"));

static PACK: &[u8] = include_bytes!(concat!(env!("OUT_DIR"), "/client-mods.zst"));

pub fn version() -> Option<&'static str> {
    (!JARS.is_empty()).then_some(MOD_VERSION)
}

pub fn path(major: &str) -> PathBuf {
    paths::root()
        .join("client")
        .join(format!("rustyclient-{}.jar", paths::sanitize(major)))
}

fn sha1_hex(bytes: &[u8]) -> String {
    hex::encode(Sha1::digest(bytes))
}

pub fn install(major: &str) -> Result<PathBuf, String> {
    let missing = || format!("This build of Rusty Client does not include the in-game client for Minecraft {major}.");
    let index = JARS.iter().position(|(m, _, _)| *m == major).ok_or_else(missing)?;
    let (_, size, sha1) = JARS[index];
    let target = path(major);
    if let Ok(bytes) = std::fs::read(&target) {
        if bytes.len() as u64 == size && sha1_hex(&bytes) == sha1 {
            return Ok(target);
        }
    }
    let offset: u64 = JARS[..index].iter().map(|(_, size, _)| size).sum();
    let mut decoder = zstd::stream::Decoder::new(PACK).map_err(|_| missing())?;
    std::io::copy(&mut (&mut decoder).take(offset), &mut std::io::sink()).map_err(|_| missing())?;
    let mut jar = Vec::with_capacity(size as usize);
    decoder.take(size).read_to_end(&mut jar).map_err(|_| missing())?;
    if jar.len() as u64 != size || sha1_hex(&jar) != sha1 {
        return Err(missing());
    }
    write_atomic(&target, &jar)
        .map_err(|e| format!("Could not write the Rusty Client mod to {}: {e}", target.display()))?;
    Ok(target)
}
