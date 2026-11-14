<p align="center">
  <img src="assets/logo.png" alt="Rusty Client" width="140">
</p>

<h1 align="center">Rusty Client</h1>

<p align="center">
  <a href="https://www.rust-lang.org"><img src="https://img.shields.io/badge/Rust-Edition%202024-b7410e?style=for-the-badge&logo=rust&logoColor=white" alt="Rust"></a>
  <a href="https://github.com/chromiumembedded/cef"><img src="https://img.shields.io/badge/CEF-Chromium-1ab0dd?style=for-the-badge" alt="CEF"></a>
  <a href="#requirements"><img src="https://img.shields.io/badge/Windows-10%20%7C%2011-0078d4?style=for-the-badge" alt="Windows 10 and 11"></a>
  <a href="https://github.com/elistrrdev/RustyClient/releases/latest"><img src="https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2Felistrrdev%2FRustyClient%2Fmain%2Flauncher%2FCargo.toml&query=%24.package.version&label=version&style=for-the-badge&color=1ab0dd" alt="Version"></a>
  <a href="https://fabricmc.net"><img src="https://img.shields.io/badge/Fabric-Loader%20%2B%20API-c6bca5?style=for-the-badge" alt="Fabric"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Proprietary-555555?style=for-the-badge" alt="Proprietary license"></a>
</p>

A fast, lightweight Minecraft launcher written in Rust. Sign in with your Microsoft account, pick a version and play. Java, Fabric, Fabric API and the optimization mods are all set up for you.

## Features

- **One click play** for every release of Minecraft 26.1, 26.2 and 26.3
- **Microsoft sign-in** with support for multiple accounts
- **Java included**: the right Java runtime is downloaded automatically, nothing to install yourself
- **Fabric ready**: Fabric Loader and Fabric API are installed and kept up to date on every launch
- **Fast out of the box**: Sodium, Lithium, FerriteCore, ImmediatelyFast and EntityCulling are installed and updated on every launch, and the in-game Performance page adds FPS limits, entity distances and particle toggles
- **Mod manager**: drop `.jar` files in, turn mods on and off or remove them, per version
- **Official news** and version art straight from Mojang
- **Your look**: pick an accent color for the whole launcher
- **Crash help**: when the game crashes you get the reason and the crash report in one click

## Screenshots

### Launcher

<p align="center">
  <a href="assets/screenshots/Launcher-1.png"><img src="assets/screenshots/Launcher-1.png?v=1.0.3-2" alt="Launcher home page with the version picker and Minecraft news" width="100%"></a>
</p>
<p align="center">
  <a href="assets/screenshots/Launcher-2.png"><img src="assets/screenshots/Launcher-2.png?v=1.0.3-2" alt="Launcher settings" width="49%"></a>
  <a href="assets/screenshots/Launcher-3.png"><img src="assets/screenshots/Launcher-3.png?v=1.0.3-2" alt="About page with dependencies and folders" width="49%"></a>
</p>

### In-game menu

Every launch also loads the Rusty Client mod. Press Right Shift in game to open its menu with all modules, your profile, the client settings, your config profiles (per version or for all versions), the performance options and your waypoints.

<p align="center">
  <a href="assets/screenshots/Menu-1.png"><img src="assets/screenshots/Menu-1.png?v=1.0.3-2" alt="In-game menu with the module grid" width="100%"></a>
</p>
<p align="center">
  <a href="assets/screenshots/Menu-2.png"><img src="assets/screenshots/Menu-2.png?v=1.0.3-2" alt="Profile page of the in-game menu" width="49%"></a>
  <a href="assets/screenshots/Menu-3.png"><img src="assets/screenshots/Menu-3.png?v=1.0.3-2" alt="Settings page of the in-game menu" width="49%"></a>
</p>
<p align="center">
  <a href="assets/screenshots/Menu-4.png"><img src="assets/screenshots/Menu-4.png?v=1.0.3-2" alt="Profiles page with the default profile of the version, its look and the parts it includes" width="49%"></a>
  <a href="assets/screenshots/Menu-5.png"><img src="assets/screenshots/Menu-5.png?v=1.0.3-2" alt="Performance page with FPS limits, particle toggles and entity distances" width="49%"></a>
</p>

## Download

Grab `RustyClient-Setup.exe` from the [Releases](../../releases) page and run it. It installs Rusty Client, adds desktop and Start menu shortcuts and an entry in Windows Apps for uninstalling. The installed launcher keeps itself up to date.

## Requirements

- Windows 10 or 11, 64-bit
- A Microsoft account that owns Minecraft: Java Edition
- About 1 GB of free space for the game, Java and assets

## Where your data lives

Everything Rusty Client saves goes into `%APPDATA%\.rustyclient`: settings, accounts (encrypted with Windows), game files and one game folder per version with its own worlds, resource packs, screenshots and mods. Uninstalling the launcher keeps this folder, so nothing is lost.

## Fair play

Rusty Client is meant for normal play on any server. It does not include, and will not add, anything that gives an unfair advantage.

## License

Rusty Client is proprietary software. Copyright (c) 2026 elistrrdev, all rights reserved. The source code is published so everyone can see how Rusty Client works, but it is not open source: using Rusty Client is covered by the [Rusty Client End User License Agreement](LICENSE). Versions up to 1.0.3 were released under the GPL-3.0. Bundled fonts, icons and adapted code, and the Chromium Embedded Framework that Rusty Client downloads on first start, are listed in [THIRD_PARTY.md](THIRD_PARTY.md).

Rusty Client is not affiliated with Mojang Studios or Microsoft.
