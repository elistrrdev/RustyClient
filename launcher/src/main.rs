#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]
#![allow(non_snake_case)]
#![cfg_attr(feature = "setup", allow(dead_code, unused_imports))]

mod api;
mod app;
mod auth;
mod bridge;
mod client;
mod config;
mod content;
mod install;
mod launch;
mod mods;
mod paths;
#[cfg(any(feature = "portable", feature = "setup"))]
mod progress;
#[cfg(not(feature = "portable"))]
mod registry;
mod runtime;
#[cfg(feature = "setup")]
mod setup;
mod state;
mod storage;
mod update;
#[cfg(not(any(feature = "portable", feature = "setup")))]
mod uninstall;
mod win;

use cef::*;

fn main() {
    let is_browser = !std::env::args().any(|arg| arg.starts_with("--type="));
    if is_browser {
        win::wait_for_previous_instance();
    }

    #[cfg(not(any(feature = "portable", feature = "setup")))]
    if is_browser && std::env::args().any(|arg| arg == "--uninstall") {
        uninstall::run();
        return;
    }

    #[cfg(not(feature = "setup"))]
    paths::init();

    if !runtime::load(is_browser) {
        std::process::exit(1);
    }

    #[cfg(feature = "setup")]
    if is_browser && std::env::args().any(|arg| arg == "--update") {
        setup::update();
        return;
    }

    #[cfg(not(feature = "setup"))]
    if is_browser {
        storage::clear_browser_cache_if_requested();
    }
    let _ = api_hash(sys::CEF_API_VERSION_LAST, 0);
    let args = args::Args::new();
    let code = execute_process(Some(args.as_main_args()), None, std::ptr::null_mut());
    if !is_browser {
        std::process::exit(code.max(0));
    }
    if !win::acquire_single_instance() {
        return;
    }

    #[cfg(feature = "setup")]
    setup::run(args.as_main_args());

    #[cfg(not(feature = "setup"))]
    {
        let _ = state::core();
        paths::create_instance_folders();
        state::runtime().spawn(async {
            let _ = content::get(false).await;
        });
        auth::refresh_profiles_in_background();
        update::start();
        app::run(
            args.as_main_args(),
            win::WindowSpec {
                title: "Rusty Client",
                width: 1120,
                height: 680,
                min_width: 960,
                min_height: 600,
                resizable: true,
            },
            paths::cef_cache(),
        );
    }
}
