use std::collections::{HashMap, VecDeque};
use std::io::{LineWriter, Write};
use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::sync::{Arc, Mutex};

use serde_json::Value;
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::sync::oneshot;

use crate::config::{Account, Settings, write_atomic};
use crate::install::{self, Features, Prepared, rules_allow};
use crate::state::{Core, Crash, core, now_secs, runtime};
use crate::{auth, paths, win};

const CREATE_NO_WINDOW: u32 = 0x0800_0000;

pub fn start(version_id: String) -> Result<(), String> {
    let core = core().clone();
    {
        let task = core.task.lock().unwrap();
        if task.active {
            return Err("Minecraft is already being prepared.".into());
        }
    }
    if core.game.lock().unwrap().running {
        return Err("Minecraft is already running.".into());
    }
    if crate::update::installing() {
        return Err("Rusty Client is installing an update and restarts in a moment.".into());
    }
    let Some((entry, major)) = install::resolve_entry(&version_id) else {
        return Err("This version is not available yet. Check your internet connection.".into());
    };
    core.set_task("Preparing", "Starting up", 0.0);
    core.game.lock().unwrap().crash = None;
    runtime().spawn(async move {
        let settings = core.settings();
        let result = async {
            core.set_task("Signing in", "Checking your account", 0.0);
            let account = auth::active_account(&core).await?;
            let prepared = install::prepare(&core, &entry, &major, &settings).await?;
            core.set_task("Starting", "Launching Minecraft", 1.0);
            spawn_game(&core, &entry.id, &major, &settings, &account, prepared).await
        }
        .await;
        if let Err(error) = result {
            core.finish_task(Some(error));
        }
    });
    Ok(())
}

pub fn stop() {
    if let Some(tx) = core().kill.lock().unwrap().take() {
        let _ = tx.send(());
    }
}

fn substitute(argument: &str, vars: &HashMap<&'static str, String>) -> String {
    let mut out = argument.to_string();
    if !out.contains("${") {
        return out;
    }
    for (key, value) in vars {
        let pattern = format!("${{{key}}}");
        if out.contains(&pattern) {
            out = out.replace(&pattern, value);
        }
    }
    out
}

fn collect(list: &Value, features: &Features, out: &mut Vec<String>) {
    for item in list.as_array().into_iter().flatten() {
        if let Some(text) = item.as_str() {
            out.push(text.to_string());
            continue;
        }
        if !rules_allow(&item["rules"], features) {
            continue;
        }
        match &item["value"] {
            Value::String(text) => out.push(text.clone()),
            Value::Array(values) => out.extend(values.iter().filter_map(Value::as_str).map(str::to_string)),
            _ => {}
        }
    }
}

fn split_args(input: &str) -> Vec<String> {
    let mut args = Vec::new();
    let mut current = String::new();
    let mut quoted = false;
    let mut has_token = false;
    for c in input.chars() {
        match c {
            '"' => {
                quoted = !quoted;
                has_token = true;
            }
            c if c.is_whitespace() && !quoted => {
                if has_token {
                    args.push(std::mem::take(&mut current));
                    has_token = false;
                }
            }
            c => {
                current.push(c);
                has_token = true;
            }
        }
    }
    if has_token {
        args.push(current);
    }
    args
}

fn is_heap_flag(arg: &str) -> bool {
    arg.starts_with("-Xmx") || arg.starts_with("-Xms")
}

fn build_arguments(
    id: &str,
    settings: &Settings,
    account: &Account,
    prepared: &Prepared,
) -> (Vec<String>, String) {
    let features = Features {
        custom_resolution: settings.custom_resolution && !settings.fullscreen,
    };
    let classpath = prepared
        .classpath
        .iter()
        .map(|p| p.to_string_lossy().to_string())
        .collect::<Vec<_>>()
        .join(";");

    let mut vars: HashMap<&'static str, String> = HashMap::new();
    vars.insert("auth_player_name", account.name.clone());
    vars.insert("version_name", id.to_string());
    vars.insert("game_directory", prepared.game_dir.to_string_lossy().to_string());
    vars.insert("assets_root", paths::assets().to_string_lossy().to_string());
    vars.insert("game_assets", paths::assets().to_string_lossy().to_string());
    vars.insert(
        "assets_index_name",
        prepared.version["assetIndex"]["id"].as_str().unwrap_or("legacy").to_string(),
    );
    vars.insert("auth_uuid", account.uuid.clone());
    vars.insert("auth_access_token", account.mc_token.clone());
    vars.insert("auth_session", format!("token:{}:{}", account.mc_token, account.uuid));
    vars.insert("clientid", settings.client_id.clone());
    vars.insert("auth_xuid", if account.xuid.is_empty() { "0".into() } else { account.xuid.clone() });
    vars.insert("user_type", "msa".into());
    vars.insert("user_properties", "{}".into());
    vars.insert("version_type", "release".into());
    vars.insert("resolution_width", settings.width.to_string());
    vars.insert("resolution_height", settings.height.to_string());
    vars.insert("natives_directory", prepared.natives_dir.to_string_lossy().to_string());
    vars.insert("launcher_name", "RustyClient".into());
    vars.insert("launcher_version", "1.0".into());
    vars.insert("classpath", classpath.clone());
    vars.insert("classpath_separator", ";".into());
    vars.insert("library_directory", paths::libraries().to_string_lossy().to_string());

    let mut jvm = Vec::new();
    let mut defaults = Vec::new();
    collect(&prepared.version["arguments"]["default-user-jvm"], &features, &mut defaults);
    jvm.extend(defaults.into_iter().filter(|a| !is_heap_flag(a)));
    let memory = settings.memory_mb.max(1024);
    jvm.push(format!("-Xmx{memory}M"));
    jvm.push(format!("-Xms{}M", memory.min(2048)));
    collect(&prepared.version["arguments"]["jvm"], &features, &mut jvm);
    if let Some(fabric) = &prepared.fabric {
        collect(&fabric["arguments"]["jvm"], &features, &mut jvm);
    }
    if let Some(logging) = &prepared.logging_arg {
        jvm.push(logging.clone());
    }
    jvm.push(format!("-Dfabric.addMods={}", prepared.client_mod.to_string_lossy()));
    jvm.extend(split_args(&settings.jvm_args));

    let main_class = prepared
        .fabric
        .as_ref()
        .and_then(|f| f["mainClass"].as_str())
        .or_else(|| prepared.version["mainClass"].as_str())
        .unwrap_or("net.minecraft.client.main.Main")
        .to_string();

    let mut game = Vec::new();
    collect(&prepared.version["arguments"]["game"], &features, &mut game);
    if let Some(fabric) = &prepared.fabric {
        collect(&fabric["arguments"]["game"], &features, &mut game);
    }
    if settings.fullscreen {
        game.push("--fullscreen".into());
    }

    let mut args: Vec<String> = jvm.iter().map(|a| substitute(a, &vars)).collect();
    args.push(main_class);
    args.extend(game.iter().map(|a| substitute(a, &vars)));
    (args, classpath)
}

fn use_argfile(args: Vec<String>, classpath: &str, version_dir: &Path) -> Vec<String> {
    let Some(index) = args.iter().position(|a| a == classpath) else {
        return args;
    };
    if index == 0 || args[index - 1] != "-cp" {
        return args;
    }
    let file = version_dir.join("classpath.txt");
    let content = format!("-cp \"{}\"", classpath.replace('\\', "/"));
    if write_atomic(&file, content.as_bytes()).is_err() {
        return args;
    }
    let mut out = Vec::with_capacity(args.len() - 1);
    out.extend_from_slice(&args[..index - 1]);
    out.push(format!("@{}", file.to_string_lossy()));
    out.extend_from_slice(&args[index + 1..]);
    out
}

fn newest_crash_report(game_dir: &Path, since: u64) -> Option<PathBuf> {
    let entries = std::fs::read_dir(game_dir.join("crash-reports")).ok()?;
    entries
        .filter_map(Result::ok)
        .filter_map(|e| {
            let modified = e
                .metadata()
                .ok()?
                .modified()
                .ok()?
                .duration_since(std::time::UNIX_EPOCH)
                .ok()?
                .as_secs();
            (modified + 2 >= since).then_some((modified, e.path()))
        })
        .max_by_key(|(m, _)| *m)
        .map(|(_, p)| p)
}

async fn spawn_game(
    core: &Arc<Core>,
    id: &str,
    major: &str,
    settings: &Settings,
    account: &Account,
    prepared: Prepared,
) -> Result<(), String> {
    apply_language(&prepared.game_dir, &settings.language);
    let (args, classpath) = build_arguments(id, settings, account, &prepared);
    let args = use_argfile(args, &classpath, &paths::versions().join(id));
    let output = open_output_log(&prepared, &args, account);
    let mut command = tokio::process::Command::new(&prepared.java);
    command
        .args(&args)
        .current_dir(&prepared.game_dir)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .creation_flags(CREATE_NO_WINDOW)
        .kill_on_drop(false);
    let mut child = match command.spawn() {
        Ok(child) => child,
        Err(error) => {
            write_line(&output, &format!("could not start java: {error}"));
            return Err(format!("Could not start Java: {error}"));
        }
    };

    let started = now_secs();
    if let Some(pid) = child.id() {
        let _ = std::fs::write(paths::game_pid_file(), pid.to_string());
    }
    let (kill_tx, mut kill_rx) = oneshot::channel();
    *core.kill.lock().unwrap() = Some(kill_tx);
    {
        let mut game = core.game.lock().unwrap();
        game.running = true;
        game.version = id.to_string();
        game.major = major.to_string();
        game.started = started;
        game.crash = None;
    }
    core.finish_task(None);

    let on_launch = settings.on_launch.clone();
    match on_launch.as_str() {
        "minimize" => win::post(win::WM_APP_MINIMIZE),
        "hide" => win::post(win::WM_APP_HIDE),
        _ => {}
    }

    let log: Arc<Mutex<VecDeque<String>>> = Arc::new(Mutex::new(VecDeque::with_capacity(400)));
    let mut readers = Vec::new();
    if let Some(stdout) = child.stdout.take() {
        readers.push(tokio::spawn(pump(BufReader::new(stdout), log.clone(), output.clone())));
    }
    if let Some(stderr) = child.stderr.take() {
        readers.push(tokio::spawn(pump(BufReader::new(stderr), log.clone(), output.clone())));
    }

    let mut killed = false;
    let status = tokio::select! {
        status = child.wait() => status.ok(),
        _ = &mut kill_rx => {
            killed = true;
            let _ = child.kill().await;
            child.wait().await.ok()
        }
    };
    for reader in readers {
        let _ = tokio::time::timeout(std::time::Duration::from_secs(2), reader).await;
    }
    core.kill.lock().unwrap().take();
    let _ = std::fs::remove_file(paths::game_pid_file());

    let code = status.and_then(|s| s.code()).unwrap_or(-1);
    write_line(&output, &format!("process exited with code {code}"));
    let crashed = !killed && code != 0;
    {
        let mut game = core.game.lock().unwrap();
        game.running = false;
        if crashed {
            let lines: Vec<String> = log.lock().unwrap().iter().rev().take(80).rev().cloned().collect();
            game.crash = Some(Crash {
                id: now_secs(),
                version: id.to_string(),
                code,
                log: lines,
                report: newest_crash_report(&prepared.game_dir, started)
                    .map(|p| p.to_string_lossy().to_string())
                    .unwrap_or_default(),
            });
        }
    }
    if !crashed && core.settings().close_on_exit {
        win::post(win::WM_APP_CLOSE);
    } else if on_launch != "keep" || crashed {
        win::post(win::WM_APP_RESTORE);
    }
    Ok(())
}

fn apply_language(game_dir: &Path, language: &str) {
    if language.is_empty() {
        return;
    }
    let file = game_dir.join("options.txt");
    let current = std::fs::read_to_string(&file).unwrap_or_default();
    let mut lines: Vec<String> = current
        .lines()
        .filter(|line| !line.starts_with("lang:"))
        .map(str::to_string)
        .collect();
    lines.push(format!("lang:{language}"));
    let _ = std::fs::create_dir_all(game_dir);
    let _ = write_atomic(&file, (lines.join("\n") + "\n").as_bytes());
}

type OutputLog = Arc<Mutex<Option<LineWriter<std::fs::File>>>>;

const OUTPUT_LOG: &str = "rustyclient-launch.log";

fn open_output_log(prepared: &Prepared, args: &[String], account: &Account) -> OutputLog {
    let dir = prepared.game_dir.join("logs");
    let _ = std::fs::create_dir_all(&dir);
    let file = std::fs::File::create(dir.join(OUTPUT_LOG)).ok().map(LineWriter::new);
    let output: OutputLog = Arc::new(Mutex::new(file));
    let hide = |text: &str| {
        if account.mc_token.is_empty() {
            text.to_string()
        } else {
            text.replace(&account.mc_token, "<hidden>")
        }
    };
    write_line(&output, &format!("rusty client {}", crate::update::VERSION));
    write_line(&output, &format!("java: {}", prepared.java.to_string_lossy()));
    write_line(&output, &format!("arguments: {}", hide(&args.join(" "))));
    write_line(&output, "");
    output
}

fn write_line(output: &OutputLog, line: &str) {
    if let Some(file) = output.lock().unwrap().as_mut() {
        let _ = writeln!(file, "{line}");
    }
}

async fn pump<R: tokio::io::AsyncRead + Unpin>(reader: BufReader<R>, log: Arc<Mutex<VecDeque<String>>>, output: OutputLog) {
    let mut lines = reader.lines();
    while let Ok(Some(line)) = lines.next_line().await {
        write_line(&output, &line);
        let mut log = log.lock().unwrap();
        if log.len() >= 400 {
            log.pop_front();
        }
        log.push_back(line);
    }
}
