"use strict";

const TOKEN = location.hash.slice(1);
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

const ACCENTS = ["#1ab0dd", "#e8693a", "#f2c14e", "#5fcf8f", "#8f7cf7", "#f2638c"];

const state = {
  frame: "win11",
  maximized: false,
  totalMemory: 8192,
  settings: null,
  content: { groups: [], news: [] },
  accounts: { active: "", list: [] },
  task: { active: false, phase: "", detail: "", progress: 0, error: "" },
  game: { running: false, version: "", major: "", crash: null },
  update: {},
  aboutBuild: "",
  auth: { state: "idle", message: "" },
  view: "home",
  modal: null,
  shownCrash: 0,
  accountsKey: "",
};

function esc(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

function icon(name, size = 16, stroke = 2) {
  const body = ICONS[name] || "";
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${stroke}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${body}</svg>`;
}

const PLAY_ICON = `<svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true"><circle cx="10" cy="10" r="10" fill="currentColor"/><path d="M8.3 6.7v6.6l5.4-3.3z" fill="var(--accent)" stroke="var(--accent)" stroke-width="1.5" stroke-linejoin="round"/></svg>`;
const STOP_ICON = `<svg width="18" height="18" viewBox="0 0 20 20" aria-hidden="true"><rect x="4" y="4" width="12" height="12" rx="3" fill="currentColor"/></svg>`;

function hydrateIcons(root = document) {
  $$("[data-icon]", root).forEach((el) => {
    const size = Number(el.dataset.size || 18);
    el.innerHTML = icon(el.dataset.icon, size, el.dataset.stroke || 2);
  });
}

async function api(path, { method = "GET", body, raw } = {}) {
  const headers = { "x-rusty-token": TOKEN };
  let payload;
  if (raw !== undefined) {
    payload = raw;
    headers["content-type"] = "application/octet-stream";
  } else if (body !== undefined) {
    payload = JSON.stringify(body);
    headers["content-type"] = "application/json";
  }
  const response = await fetch("/api" + path, { method, headers, body: payload });
  let data = {};
  try {
    data = await response.json();
  } catch (_) {
    data = {};
  }
  if (!response.ok) {
    throw new Error(data.error || "Something went wrong. Please try again.");
  }
  return data;
}

function toast(message, kind = "info", duration = 4800) {
  const host = $("#toasts");
  const el = document.createElement("div");
  const glyph = kind === "error" ? "circle-alert" : kind === "success" ? "check" : "info";
  el.className = `toast ${kind}`;
  el.innerHTML = `<span class="toast-icon">${icon(glyph, 16)}</span><span>${esc(message)}</span>`;
  host.appendChild(el);
  const remove = () => {
    el.classList.add("leaving");
    setTimeout(() => el.remove(), 220);
  };
  el.addEventListener("click", remove);
  setTimeout(remove, duration);
  while (host.children.length > 4) host.firstElementChild.remove();
}

function ago(value) {
  if (!value) return "";
  const date = new Date(value.length === 10 ? value + "T12:00:00Z" : value);
  const days = Math.floor((Date.now() - date.getTime()) / 86400000);
  if (Number.isNaN(days)) return "";
  if (days <= 0) return "Today";
  if (days === 1) return "Yesterday";
  if (days < 7) return `${days} days ago`;
  if (days < 30) {
    const weeks = Math.floor(days / 7);
    return weeks === 1 ? "1 week ago" : `${weeks} weeks ago`;
  }
  if (days < 365) {
    const months = Math.floor(days / 30);
    return months === 1 ? "1 month ago" : `${months} months ago`;
  }
  const years = Math.floor(days / 365);
  return years === 1 ? "1 year ago" : `${years} years ago`;
}

function formatMemory(mb) {
  return `${(mb / 1024).toFixed(1)} GB`;
}

function formatSize(bytes) {
  if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} MB`;
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${bytes} B`;
}

function fadeImage(img) {
  const show = () => requestAnimationFrame(() => requestAnimationFrame(() => img.classList.add("shown")));
  if (img.complete && img.naturalWidth) show();
  else img.addEventListener("load", show, { once: true });
}

function groups() {
  return state.content.groups || [];
}

function selectedGroup() {
  const list = groups();
  return list.find((g) => g.major === state.settings?.selectedMajor) || list[0] || null;
}

function selectedVersion() {
  const group = selectedGroup();
  if (!group) return null;
  const chosen = state.settings?.selectedVersions?.[group.major];
  return group.versions.some((v) => v.id === chosen) ? chosen : group.versions[0]?.id || null;
}

function activeAccount() {
  const list = state.accounts.list || [];
  return list.find((a) => a.uuid === state.accounts.active) || list[0] || null;
}

let saveTimer = 0;
function saveSettings(immediate = false) {
  clearTimeout(saveTimer);
  const run = async () => {
    try {
      await api("/settings", { method: "POST", body: state.settings });
    } catch (error) {
      toast(error.message, "error");
    }
  };
  if (immediate) run();
  else saveTimer = setTimeout(run, 350);
}

function applyAccent() {
  document.documentElement.style.setProperty("--accent", state.settings?.accent || ACCENTS[0]);
}

function setView(view) {
  state.view = view;
  if (view === "settings") loadStorage();
  if (view === "about") renderAbout();
  $$(".view").forEach((el) => el.classList.toggle("active", el.dataset.view === view));
  const nav = view === "news" ? "home" : view;
  $$(".nav-item").forEach((el) => el.classList.toggle("active", el.dataset.nav === nav));
  closeMenus();
}

function closeMenus() {
  closeSelect();
  $("#version-picker").classList.remove("open");
  $("#account-menu").classList.remove("open");
  $("#account").classList.remove("open");
}

function renderWindowButtons() {
  document.body.classList.toggle("maximized", state.maximized);
  const minimize = $('[data-window="minimize"]');
  const maximize = $('[data-window="maximize"]');
  const close = $('[data-window="close"]');
  if (!minimize.innerHTML) minimize.innerHTML = icon("minus", 16);
  if (!close.innerHTML) close.innerHTML = icon("x", 16);
  const want = state.maximized ? "copy" : "square";
  if (maximize.dataset.glyph !== want) {
    maximize.dataset.glyph = want;
    maximize.innerHTML = icon(want, 13);
    maximize.setAttribute("aria-label", state.maximized ? "Restore" : "Maximize");
  }
}

let heroUrl = null;
function renderHero() {
  const group = selectedGroup();
  const url = group?.image || "";
  if (url === heroUrl) return;
  heroUrl = url;
  const media = $("#hero-media");
  const previous = $$("img", media);
  if (!url) {
    previous.forEach((img) => img.classList.remove("shown"));
    media.classList.toggle("skeleton", groups().length === 0);
    return;
  }
  media.classList.remove("skeleton");
  const img = new Image();
  img.alt = "";
  img.draggable = false;
  img.decoding = "async";
  img.onload = () => {
    if (heroUrl !== url) return;
    media.appendChild(img);
    fadeImage(img);
    setTimeout(() => previous.forEach((old) => old.remove()), 600);
  };
  img.src = url;
}

function renderTiles() {
  const host = $("#tiles");
  const list = groups();
  if (!list.length) {
    host.innerHTML = '<div class="tile skeleton"></div>'.repeat(3);
    return;
  }
  const current = selectedGroup()?.major;
  const key = list.map((g) => g.major + g.image).join("|");
  if (host.dataset.key !== key) {
    host.dataset.key = key;
    host.innerHTML = list
      .map(
        (g) => `<button class="tile" data-major="${esc(g.major)}">${g.image ? `<img src="${esc(g.image)}" alt="" draggable="false">` : ""}<span class="tile-label">${esc(g.major)}</span></button>`
      )
      .join("");
    $$("img", host).forEach(fadeImage);
  }
  $$(".tile", host).forEach((el) => el.classList.toggle("active", el.dataset.major === current));
}

function renderVersionPicker() {
  const button = $("#version-button");
  const menu = $("#version-menu");
  const group = selectedGroup();
  const version = selectedVersion();
  button.innerHTML = `<span>${esc(version || "--")}</span><span class="chev">${icon("chevron-down", 14, 2.4)}</span>`;
  if (!group) {
    menu.innerHTML = "";
    return;
  }
  menu.innerHTML = group.versions
    .map(
      (v) => `<button class="version-option ${v.id === version ? "selected" : ""}" data-version="${esc(v.id)}"><span>${esc(v.id)}</span>${v.id === version ? `<span class="check">${icon("check", 14, 2.6)}</span>` : ""}</button>`
    )
    .join("");
}

let launchMarkup = "";
function renderLaunch() {
  const button = $("#launch-button");
  const content = $("#launch-content");
  const fill = $("#launch-fill");
  const status = $("#launch-status");
  let markup;
  let cls = "launch-button";
  let statusHtml = "";

  if (state.game.running) {
    cls += " running";
    markup = `<span class="idle-label">Playing</span><span class="hover-label">Stop</span>${STOP_ICON}`;
    statusHtml = `<span>Minecraft ${esc(state.game.version)} is running</span>`;
  } else if (state.task.active) {
    cls += " busy";
    const pct = Math.round((state.task.progress || 0) * 100);
    markup = `<span>${esc(state.task.phase || "Preparing")}</span><span class="pct">${pct}%</span>`;
    fill.style.width = `${pct}%`;
    statusHtml = `<span>${esc(state.task.detail || "Getting things ready")}</span>`;
  } else {
    markup = `<span>Launch</span>${PLAY_ICON}`;
    fill.style.width = "0%";
  }

  button.className = cls;
  if (markup !== launchMarkup) {
    launchMarkup = markup;
    content.innerHTML = markup;
  }
  if (statusHtml) status.innerHTML = statusHtml;
  status.classList.toggle("visible", Boolean(statusHtml));
}

function newsCard(item) {
  return `<button class="news-card" data-url="${esc(item.url)}" title="${esc(item.text || item.title)}">
    <div class="news-image">${item.image ? `<img src="${esc(item.image)}" alt="" draggable="false" loading="lazy">` : ""}</div>
    <div class="news-body">
      <div class="news-category">${esc(item.category)}</div>
      <div class="news-title">${esc(item.title)}</div>
      <div class="news-date">${esc(ago(item.date))}</div>
    </div>
  </button>`;
}

function renderNews() {
  const news = state.content.news || [];
  const preview = $("#news-preview");
  const all = $("#news-all");
  if (!news.length) {
    preview.innerHTML = '<div class="news-card skeleton"></div>'.repeat(3);
    all.innerHTML = '<div class="news-card skeleton"></div>'.repeat(6);
    return;
  }
  const key = news.map((n) => n.id).join("|");
  if (preview.dataset.key === key) return;
  preview.dataset.key = key;
  preview.innerHTML = news.slice(0, 3).map(newsCard).join("");
  all.innerHTML = news.map(newsCard).join("");
  $$(".news-image img").forEach(fadeImage);
}

function avatarMarkup(account, extra = "") {
  if (!account) return `<span class="avatar placeholder ${extra}">${icon("user-plus", 15)}</span>`;
  return `<span class="avatar ${extra}" data-skin="${esc(account.skinUrl || "")}" data-name="${esc(account.name)}"></span>`;
}

const skinCache = new Map();
function loadSkin(url) {
  if (!skinCache.has(url)) {
    skinCache.set(
      url,
      new Promise((resolve, reject) => {
        const img = new Image();
        img.onload = () => resolve(img);
        img.onerror = reject;
        img.src = url;
      })
    );
  }
  return skinCache.get(url);
}

function paintAvatars(root = document) {
  $$(".avatar[data-name]", root).forEach((el) => {
    if (el.dataset.painted) return;
    el.dataset.painted = "1";
    const letter = () => {
      el.classList.add("placeholder");
      el.innerHTML = `<span style="font-weight:700;font-size:12px">${esc((el.dataset.name || "?").charAt(0).toUpperCase())}</span>`;
    };
    const url = el.dataset.skin;
    if (!url) return letter();
    loadSkin(url)
      .then((img) => {
        const canvas = document.createElement("canvas");
        canvas.width = 8;
        canvas.height = 8;
        const ctx = canvas.getContext("2d");
        ctx.imageSmoothingEnabled = false;
        ctx.drawImage(img, 8, 8, 8, 8, 0, 0, 8, 8);
        ctx.drawImage(img, 40, 8, 8, 8, 0, 0, 8, 8);
        el.innerHTML = "";
        el.appendChild(canvas);
      })
      .catch(letter);
  });
}

function renderAccount() {
  const account = activeAccount();
  const button = $("#account");
  if (!account) {
    button.innerHTML = `${avatarMarkup(null)}<span class="account-text"><span class="account-name">Sign in</span><span class="account-sub">Microsoft account</span></span><span class="account-chevron">${icon("chevron-right", 15)}</span>`;
  } else {
    button.innerHTML = `${avatarMarkup(account)}<span class="account-text"><span class="account-name">${esc(account.name)}</span><span class="account-sub">Microsoft account</span></span><span class="account-chevron">${icon("chevron-down", 15)}</span>`;
  }
  const menu = $("#account-menu");
  const list = state.accounts.list || [];
  menu.innerHTML =
    `<div class="menu-label">Accounts</div>` +
    list
      .map(
        (a) => `<button class="menu-item ${a.uuid === account?.uuid ? "selected" : ""}" data-select="${esc(a.uuid)}">${avatarMarkup(a)}<span class="grow">${esc(a.name)}</span>${a.uuid === account?.uuid ? `<span class="check">${icon("check", 14, 2.6)}</span>` : ""}</button>`
      )
      .join("") +
    `<div class="menu-sep"></div>
     <button class="menu-item" data-action="add-account">${icon("user-plus", 16)}<span class="grow">Add account</span></button>
     ${account ? `<button class="menu-item danger" data-action="sign-out">${icon("log-out", 16)}<span class="grow">Sign out</span></button>` : ""}`;
  paintAvatars(button);
  paintAvatars(menu);
}

function settingRow(title, hint, control, extra = "") {
  return `<div class="row ${extra}"><div class="row-text"><span class="row-title">${title}</span>${hint ? `<span class="row-hint">${hint}</span>` : ""}</div>${control}</div>`;
}

function toggleMarkup(key, on) {
  return `<button class="toggle ${on ? "on" : ""}" data-toggle="${key}" role="switch" aria-checked="${on}"></button>`;
}

function maxMemory() {
  return Math.max(2048, Math.floor(state.totalMemory / 512) * 512);
}

function renderSettings() {
  const s = state.settings;
  const max = maxMemory();
  const page = $("#settings-page");
  page.innerHTML = `
  <div class="page-head"><h1>Settings</h1></div>
  <div class="settings-grid">
    <div class="settings-col">
      <div class="group-label">Game</div>
      <div class="row stack">
        <div class="slider-head"><span class="row-title">Memory</span><span class="slider-value" id="memory-value">${formatMemory(s.memoryMb)}</span></div>
        <div class="slider" id="memory-slider" data-min="1024" data-max="${max}" data-step="512">
          <div class="slider-track"><div class="slider-fill"></div></div>
          <div class="slider-thumb"></div>
        </div>
        <div class="slider-scale"><span>1 GB</span><span>${Math.round(max / 1024)} GB</span></div>
      </div>
      ${settingRow("Fullscreen", "Start Minecraft in fullscreen", toggleMarkup("fullscreen", s.fullscreen))}
      ${settingRow("Custom resolution", "Window size when the game opens", toggleMarkup("customResolution", s.customResolution), s.fullscreen ? "disabled" : "")}
      <div class="row ${s.customResolution && !s.fullscreen ? "" : "disabled"}" id="resolution-row">
        <div class="row-text"><span class="row-title">Window size</span></div>
        <div class="resolution">
          <input class="field" id="res-width" type="text" inputmode="numeric" maxlength="5" value="${s.width}">
          <span class="times">x</span>
          <input class="field" id="res-height" type="text" inputmode="numeric" maxlength="4" value="${s.height}">
        </div>
      </div>
      <div class="row stack">
        <span class="row-title">JVM arguments</span>
        <input class="field mono" id="jvm-args" placeholder="-XX:+UnlockExperimentalVMOptions" value="${esc(s.jvmArgs)}" spellcheck="false">
      </div>
      ${settingRow("Game language", "Applied every time Minecraft starts", `<button class="select-button" id="language-select"><span class="grow">${esc(languageLabel(s.language))}</span>${icon("chevron-down", 15)}</button>`)}
    </div>

    <div class="settings-col">
      <div class="group-label">Launcher</div>
      <div class="row stack">
        <span class="row-title">When the game starts</span>
        <div class="segmented" id="on-launch">
          ${[["keep", "Keep open"], ["minimize", "Minimize"], ["hide", "Hide"]]
            .map(([v, label]) => `<button class="segment ${s.onLaunch === v ? "active" : ""}" data-value="${v}">${label}</button>`)
            .join("")}
        </div>
      </div>
      ${settingRow("Close when the game closes", "Quits Rusty Client after Minecraft exits normally", toggleMarkup("closeOnExit", s.closeOnExit))}
      <div class="row stack">
        <span class="row-title">Accent color</span>
        <div class="swatches" id="swatches">
          ${ACCENTS.map((c) => `<button class="swatch" data-color="${c}" style="background:${c}" aria-label="${c}"></button>`).join("")}
          <button class="swatch picker" id="accent-picker" title="Custom color" aria-label="Custom color">${icon("palette", 15)}</button>
        </div>
      </div>

      <div class="group-label">Storage</div>
      ${settingRow("Launcher cache", '<span id="storage-launcher">Calculating</span>', `<button class="chip-button" data-clear="launcher">${icon("trash-2", 14)}Clear</button>`)}
      ${settingRow("Game cache", '<span id="storage-game">Calculating</span>', `<button class="chip-button" data-clear="game">${icon("trash-2", 14)}Clear</button>`)}
    </div>
  </div>`;
  updateSlider();
  updateSwatches();
}

function updateSlider() {
  const slider = $("#memory-slider");
  if (!slider) return;
  const min = Number(slider.dataset.min);
  const max = Number(slider.dataset.max);
  const value = Math.min(max, Math.max(min, state.settings.memoryMb));
  const pct = ((value - min) / (max - min)) * 100;
  $(".slider-fill", slider).style.width = `${pct}%`;
  $(".slider-thumb", slider).style.left = `${pct}%`;
  $("#memory-value").textContent = formatMemory(value);
}

function updateSwatches() {
  const current = (state.settings.accent || "").toLowerCase();
  $$("#swatches .swatch[data-color]").forEach((el) => el.classList.toggle("active", el.dataset.color === current));
  const picker = $("#swatches .picker");
  if (picker) picker.classList.toggle("active", !ACCENTS.includes(current));
}

function setAccent(hex) {
  state.settings.accent = hex.toLowerCase();
  applyAccent();
  updateSwatches();
  saveSettings();
}

function hexToRgb(hex) {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex || "");
  if (!m) return null;
  const n = parseInt(m[1], 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function rgbToHex(r, g, b) {
  return "#" + [r, g, b].map((v) => Math.round(v).toString(16).padStart(2, "0")).join("");
}

function rgbToHsv(r, g, b) {
  r /= 255;
  g /= 255;
  b /= 255;
  const max = Math.max(r, g, b);
  const d = max - Math.min(r, g, b);
  let h = 0;
  if (d) {
    if (max === r) h = ((g - b) / d) % 6;
    else if (max === g) h = (b - r) / d + 2;
    else h = (r - g) / d + 4;
  }
  return [(h * 60 + 360) % 360, max ? d / max : 0, max];
}

function hsvToRgb(h, s, v) {
  const f = (n) => {
    const k = (n + h / 60) % 6;
    return (v - v * s * Math.max(0, Math.min(k, 4 - k, 1))) * 255;
  };
  return [f(5), f(3), f(1)];
}

let colorPicker = null;

function openColorPicker(anchor) {
  const rgb = hexToRgb(state.settings.accent) || hexToRgb(ACCENTS[0]);
  const [h, s, v] = rgbToHsv(...rgb);
  const pop = document.createElement("div");
  pop.className = "color-pop";
  pop.innerHTML = `
    <div class="color-area"><div class="color-area-thumb"></div></div>
    <div class="color-hue"><div class="color-hue-thumb"></div></div>
    <div class="color-row">
      <span class="color-preview"></span>
      <label class="color-hex"><span>#</span><input id="color-hex" maxlength="6" spellcheck="false" autocomplete="off"></label>
      ${window.EyeDropper ? `<button class="color-tool" id="color-eyedropper" title="Pick from screen">${icon("pipette", 15)}</button>` : ""}
    </div>`;
  document.body.appendChild(pop);
  colorPicker = { pop, anchor, h, s, v, top: anchor.getBoundingClientRect().top };
  paintColorPicker(true);
  placeColorPicker();
  anchor.classList.add("open");
  requestAnimationFrame(() => pop.classList.add("open"));

  const drag = (el, apply) => {
    el.addEventListener("pointerdown", (event) => {
      event.preventDefault();
      el.setPointerCapture(event.pointerId);
      const move = (e) => {
        const rect = el.getBoundingClientRect();
        apply(Math.min(1, Math.max(0, (e.clientX - rect.left) / rect.width)), Math.min(1, Math.max(0, (e.clientY - rect.top) / rect.height)));
        paintColorPicker(true);
        commitColorPicker();
      };
      const up = () => {
        el.removeEventListener("pointermove", move);
        el.removeEventListener("pointerup", up);
        el.removeEventListener("pointercancel", up);
        el.classList.remove("dragging");
      };
      move(event);
      el.classList.add("dragging");
      el.addEventListener("pointermove", move);
      el.addEventListener("pointerup", up);
      el.addEventListener("pointercancel", up);
    });
  };
  drag($(".color-area", pop), (x, y) => {
    colorPicker.s = x;
    colorPicker.v = 1 - y;
  });
  drag($(".color-hue", pop), (x) => {
    colorPicker.h = x * 359.9;
  });

  const hex = $("#color-hex", pop);
  hex.addEventListener("input", () => {
    const clean = hex.value.replace(/[^0-9a-f]/gi, "").slice(0, 6);
    if (clean !== hex.value) hex.value = clean;
    const value = hexToRgb(clean);
    if (!value) return;
    [colorPicker.h, colorPicker.s, colorPicker.v] = rgbToHsv(...value);
    paintColorPicker(false);
    commitColorPicker();
  });
  hex.addEventListener("keydown", (event) => {
    if (event.key === "Enter") closeColorPicker();
  });
  hex.addEventListener("blur", () => paintColorPicker(true));

  const dropper = $("#color-eyedropper", pop);
  if (dropper) {
    dropper.addEventListener("click", async () => {
      try {
        const result = await new EyeDropper().open();
        const value = hexToRgb(result.sRGBHex);
        if (value && colorPicker) {
          [colorPicker.h, colorPicker.s, colorPicker.v] = rgbToHsv(...value);
          paintColorPicker(true);
          commitColorPicker();
        }
      } catch (_) {}
    });
  }
}

function currentPickerHex() {
  const { h, s, v } = colorPicker;
  return rgbToHex(...hsvToRgb(h, s, v));
}

function paintColorPicker(updateField) {
  if (!colorPicker) return;
  const { pop, h, s, v } = colorPicker;
  const hex = currentPickerHex();
  pop.style.setProperty("--hue", `hsl(${h} 100% 50%)`);
  pop.style.setProperty("--picked", hex);
  const thumb = $(".color-area-thumb", pop);
  thumb.style.left = `${s * 100}%`;
  thumb.style.top = `${(1 - v) * 100}%`;
  $(".color-hue-thumb", pop).style.left = `${(h / 360) * 100}%`;
  if (updateField) $("#color-hex", pop).value = hex.slice(1);
}

function commitColorPicker() {
  if (!colorPicker) return;
  const hex = currentPickerHex();
  if (hex !== state.settings.accent) setAccent(hex);
}

function placeColorPicker() {
  if (!colorPicker) return;
  const { pop, anchor } = colorPicker;
  const rect = anchor.getBoundingClientRect();
  let left = rect.left;
  if (left + pop.offsetWidth > window.innerWidth - 12) left = rect.right - pop.offsetWidth;
  let top = rect.bottom + 8;
  if (top + pop.offsetHeight > window.innerHeight - 12) top = rect.top - 8 - pop.offsetHeight;
  pop.style.left = `${Math.max(12, left)}px`;
  pop.style.top = `${Math.max(12, top)}px`;
}

function closeColorPicker() {
  if (!colorPicker) return;
  const { pop, anchor } = colorPicker;
  colorPicker = null;
  anchor.classList.remove("open");
  pop.classList.remove("open");
  setTimeout(() => pop.remove(), 180);
}


const LANGUAGES = [
  ["", "Set in game"],
  ["en_us", "English (US)"],
  ["en_gb", "English (UK)"],
  ["de_de", "Deutsch"],
  ["fr_fr", "Français"],
  ["es_es", "Español (España)"],
  ["es_mx", "Español (México)"],
  ["it_it", "Italiano"],
  ["pt_br", "Português (Brasil)"],
  ["pt_pt", "Português (Portugal)"],
  ["nl_nl", "Nederlands"],
  ["pl_pl", "Polski"],
  ["cs_cz", "Čeština"],
  ["sk_sk", "Slovenčina"],
  ["hu_hu", "Magyar"],
  ["ro_ro", "Română"],
  ["sv_se", "Svenska"],
  ["nb_no", "Norsk bokmål"],
  ["da_dk", "Dansk"],
  ["fi_fi", "Suomi"],
  ["tr_tr", "Türkçe"],
  ["el_gr", "Ελληνικά"],
  ["ru_ru", "Русский"],
  ["uk_ua", "Українська"],
  ["bg_bg", "Български"],
  ["ja_jp", "日本語"],
  ["ko_kr", "한국어"],
  ["zh_cn", "简体中文"],
  ["zh_tw", "繁體中文"],
  ["ar_sa", "العربية"],
  ["he_il", "עברית"],
  ["hi_in", "हिन्दी"],
  ["th_th", "ไทย"],
  ["vi_vn", "Tiếng Việt"],
  ["id_id", "Bahasa Indonesia"],
];

const LINKS = [
  ["github", "GitHub repository", "https://github.com/elistrrdev/RustyClient"],
  ["tag", "Releases", "https://github.com/elistrrdev/RustyClient/releases"],
  ["bug", "Report a bug", "https://github.com/elistrrdev/RustyClient/issues/new"],
  ["scale", "License", "https://github.com/elistrrdev/RustyClient/blob/main/LICENSE"],
];

const FOLDERS = [
  ["hard-drive", "App data", "root"],
  ["gamepad-2", "Game folder", "game"],
  ["puzzle", "Mods", "mods"],
  ["image", "Resource packs", "resourcepacks"],
  ["earth", "Worlds", "saves"],
  ["camera", "Screenshots", "screenshots"],
  ["sliders-horizontal", "Config", "config"],
  ["scroll-text", "Game logs", "logs"],
];

function languageLabel(code) {
  return (LANGUAGES.find(([c]) => c === code) || LANGUAGES[0])[1];
}

let selectPop = null;

function openSelect(anchor, options, current, onPick) {
  closeSelect();
  const pop = document.createElement("div");
  pop.className = "select-pop";
  pop.innerHTML = options
    .map(([value, label]) => `<button class="select-option ${value === current ? "selected" : ""}" data-value="${esc(value)}"><span class="grow">${esc(label)}</span>${value === current ? `<span class="check">${icon("check", 14, 2.6)}</span>` : ""}</button>`)
    .join("");
  document.body.appendChild(pop);
  const rect = anchor.getBoundingClientRect();
  pop.style.width = `${Math.max(rect.width, 200)}px`;
  let left = Math.min(rect.left, window.innerWidth - 12 - pop.offsetWidth);
  let top = rect.bottom + 6;
  const room = window.innerHeight - 12 - top;
  if (room < Math.min(pop.scrollHeight, 180)) {
    pop.style.maxHeight = `${Math.min(pop.scrollHeight, rect.top - 18)}px`;
    top = rect.top - 6 - pop.offsetHeight;
  } else {
    pop.style.maxHeight = `${Math.min(300, room)}px`;
  }
  pop.style.left = `${Math.max(12, left)}px`;
  pop.style.top = `${Math.max(12, top)}px`;
  anchor.classList.add("open");
  selectPop = { pop, anchor, top: rect.top };
  const selected = $(".select-option.selected", pop);
  if (selected) pop.scrollTop = selected.offsetTop - pop.clientHeight / 2 + selected.offsetHeight / 2;
  pop.addEventListener("click", (event) => {
    const option = event.target.closest(".select-option");
    if (!option) return;
    onPick(option.dataset.value);
    closeSelect();
  });
  requestAnimationFrame(() => pop.classList.add("open"));
}

function closeSelect() {
  if (!selectPop) return;
  const { pop, anchor } = selectPop;
  selectPop = null;
  anchor.classList.remove("open");
  pop.classList.remove("open");
  setTimeout(() => pop.remove(), 160);
}

async function loadStorage(data) {
  try {
    const sizes = data || (await api("/storage"));
    const launcher = $("#storage-launcher");
    const game = $("#storage-game");
    if (launcher) launcher.textContent = `${formatSize(sizes.launcher)} - news, version lists and browser data${sizes.browserPending ? ". Browser data clears on the next start" : ""}`;
    if (game) game.textContent = `${formatSize(sizes.game)} - game logs, crash reports and Fabric cache`;
  } catch (_) {}
}

async function clearStorage(button) {
  const target = button.dataset.clear;
  button.disabled = true;
  try {
    const data = await api("/storage/clear", { method: "POST", body: { target } });
    await loadStorage(data);
    toast(data.freed > 0 ? `Freed ${formatSize(data.freed)}` : "Nothing to clear right now", "success");
  } catch (error) {
    toast(error.message, "error");
  } finally {
    button.disabled = false;
  }
}

function updateText(update) {
  switch (update?.state) {
    case "checking":
      return "Checking for updates";
    case "current":
      return "Up to date";
    case "downloading":
      return `Downloading ${update.version}`;
    case "installing":
      return `Installing ${update.version}`;
    case "error":
      return "Could not update";
    case "skipped":
      return "Update check skipped while Minecraft runs";
    default:
      return "";
  }
}

let updateKey = "";
function renderUpdate(update) {
  const key = JSON.stringify(update || {});
  if (key === updateKey) return;
  updateKey = key;
  state.update = update || {};
  const pill = $("#update-pill");
  const active = update && (update.state === "downloading" || update.state === "installing");
  pill.classList.toggle("visible", !!active);
  if (active) {
    const pct = Math.round((update.progress || 0) * 100);
    pill.innerHTML = `<div class="update-head"><span class="update-icon">${icon(update.state === "installing" ? "refresh-cw" : "download", 14)}</span><span class="grow">${update.state === "installing" ? "Restarting to update" : `Updating to ${esc(update.version)}`}</span>${update.state === "downloading" ? `<span class="update-pct">${pct}%</span>` : ""}</div><div class="update-track"><div class="update-fill" style="width:${update.state === "installing" ? 100 : pct}%"></div></div>`;
  }
  const status = $("#about-update");
  if (status) status.textContent = updateText(update) || `${state.aboutBuild || ""} build`;
}

function brand(file, kind = "") {
  return `<img class="dep-brand${kind ? ` ${kind}` : ""}" src="/brands/${file}" alt="" draggable="false">`;
}

function dependencyCard(iconMarkup, name, sub, value, id = "") {
  return `<div class="dep-card"><span class="dep-icon">${iconMarkup}</span><div class="dep-text"><span class="dep-name">${esc(name)}</span><span class="dep-sub" ${id ? `id="${id}"` : ""}>${esc(sub)}</span></div><span class="dep-value ${value ? "" : "missing"}">${esc(value || "Not installed yet")}</span></div>`;
}

async function renderAbout() {
  const page = $("#about-page");
  const group = selectedGroup();
  const version = selectedVersion() || "";
  if (!page.innerHTML) page.innerHTML = `<div class="page-head"><h1>About</h1></div><div class="empty"><div class="spinner"></div></div>`;
  let info;
  try {
    info = await api(`/about?version=${encodeURIComponent(version)}`);
  } catch (error) {
    toast(error.message, "error");
    return;
  }
  state.aboutBuild = info.build;
  const major = group?.major || "";
  page.innerHTML = `
  <div class="page-head"><h1>About</h1></div>
  <div class="group-label">Dependencies</div>
  <div class="about-grid">
    ${dependencyCard('<span class="dep-logo"></span>', "Rusty Client", updateText(state.update) || `${info.build} build`, info.launcher, "about-update")}
    ${dependencyCard(brand("chromium.svg"), "Chromium", `Embedded through CEF ${info.cef}`, info.chromium)}
    ${dependencyCard(brand("rust.svg"), "Rust", "Compiler the launcher was built with", info.rust)}
    ${dependencyCard(brand("java.svg"), "Java", "Mojang runtime, downloaded for you", info.java)}
    ${dependencyCard(brand("minecraft.png", "fill"), "Minecraft", "Selected version", info.minecraft)}
    ${dependencyCard(brand("fabric.png", "pixel"), "Fabric Loader", `Latest stable for ${info.minecraft || "the selected version"}`, info.fabricLoader)}
    ${dependencyCard(brand("fabric.png", "pixel"), "Fabric API", "Installed and updated on every launch", info.fabricApi)}
    ${dependencyCard(brand(info.windows === "Windows 11" ? "windows11.svg" : "windows10.svg", "windows"), info.windows, "Operating system build", info.windowsBuild)}
  </div>
  <div class="group-label">Folders${major ? ` <span class="badge">${esc(major)}</span>` : ""}</div>
  <div class="about-grid">
    ${FOLDERS.map(([glyph, label, folder]) => `<button class="about-button" data-open="${folder}" ${folder !== "root" && !major ? "disabled" : ""}>${icon(glyph, 16)}<span>${label}</span></button>`).join("")}
  </div>
  <div class="group-label">Quick links</div>
  <div class="about-grid">
    ${LINKS.map(([glyph, label, url]) => `<button class="about-button" data-link="${url}">${icon(glyph, 16)}<span>${label}</span>${icon("external-link", 13)}</button>`).join("")}
  </div>
  <p class="about-foot">Rusty Client is free software under the GPL-3.0 license. Not affiliated with Mojang Studios or Microsoft.</p>`;
}

function anchorMoved(popup) {
  return Math.abs(popup.anchor.getBoundingClientRect().top - popup.top) > 24;
}

document.addEventListener(
  "scroll",
  (event) => {
    if (selectPop && !selectPop.pop.contains(event.target) && anchorMoved(selectPop)) closeSelect();
    if (colorPicker && !colorPicker.pop.contains(event.target) && anchorMoved(colorPicker)) closeColorPicker();
  },
  true
);

function setSetting(key, value) {
  state.settings[key] = value;
  saveSettings();
}

function openModal(markup, kind, wide = false) {
  const layer = $("#modal-layer");
  state.modal = kind;
  layer.innerHTML = `<div class="modal ${wide ? "wide" : ""}" data-kind="${kind}">${markup}</div>`;
  hydrateIcons(layer);
  requestAnimationFrame(() => layer.classList.add("open"));
}

function closeModal(kind) {
  if (kind && state.modal !== kind) return;
  const layer = $("#modal-layer");
  state.modal = null;
  layer.classList.remove("open");
  setTimeout(() => {
    if (!state.modal) layer.innerHTML = "";
  }, 220);
}

function renderAuthModal() {
  const waiting = state.auth.state === "waiting";
  const markup = `<div class="modal-center">
    <div class="spinner"></div>
    <h3>${waiting ? "Sign in with Microsoft" : "Signing you in"}</h3>
    <p>${esc(state.auth.message || "")}</p>
    ${waiting ? '<div class="modal-actions"><button class="chip-button" data-action="cancel-login">Cancel</button></div>' : ""}
  </div>`;
  if (state.modal === "auth") {
    $(".modal", $("#modal-layer")).innerHTML = markup;
  } else {
    openModal(markup, "auth");
  }
}

function handleAuth(previous) {
  const current = state.auth.state;
  if (current === "waiting" || current === "working") {
    if (previous !== current || state.modal !== "auth") renderAuthModal();
    return;
  }
  if (state.modal === "auth") closeModal("auth");
  if (current === "done") {
    toast(state.auth.message || "Signed in", "success");
    api("/auth/dismiss", { method: "POST" }).catch(() => {});
  } else if (current === "error") {
    toast(state.auth.message || "Sign-in failed", "error", 7000);
    api("/auth/dismiss", { method: "POST" }).catch(() => {});
  }
}

function handleCrash() {
  const crash = state.game.crash;
  if (!crash || crash.id === state.shownCrash) return;
  state.shownCrash = crash.id;
  const log = (crash.log || []).join("\n");
  openModal(
    `<div class="modal-center">
      <div class="modal-icon danger">${icon("circle-alert", 24)}</div>
      <h3>Minecraft closed unexpectedly</h3>
      <p>Minecraft ${esc(crash.version)} stopped with exit code ${esc(crash.code)}.</p>
      ${log ? `<div class="log-box">${esc(log)}</div>` : ""}
      <div class="modal-actions">
        ${crash.report ? `<button class="chip-button" data-crash-report="${esc(crash.report)}">${icon("external-link", 14)}Crash report</button>` : ""}
        <button class="chip-button" data-open="logs">${icon("folder-open", 14)}Logs</button>
        <button class="chip-button accent" data-action="close-crash">Close</button>
      </div>
    </div>`,
    "crash"
  );
  const box = $(".log-box");
  if (box) box.scrollTop = box.scrollHeight;
}

let lastTaskError = "";
function handleTaskError() {
  const error = state.task.error;
  if (!state.task.active && error && error !== lastTaskError) {
    lastTaskError = error;
    toast(error, "error", 8000);
    api("/launch/dismiss", { method: "POST" }).catch(() => {});
  }
  if (!error) lastTaskError = "";
}

function applyState(data) {
  const previousAuth = state.auth.state;
  if (data.task) state.task = data.task;
  if (data.game) state.game = data.game;
  if (data.auth) state.auth = data.auth;
  if (typeof data.maximized === "boolean") state.maximized = data.maximized;
  if (data.accounts) {
    const key = JSON.stringify(data.accounts);
    state.accounts = data.accounts;
    if (key !== state.accountsKey) {
      state.accountsKey = key;
      renderAccount();
    }
  }
  if (data.update) renderUpdate(data.update);
  renderWindowButtons();
  renderLaunch();
  handleAuth(previousAuth);
  handleCrash();
  handleTaskError();
}

function busy() {
  return state.task.active || state.auth.state === "waiting" || state.auth.state === "working";
}

async function poll() {
  try {
    applyState(await api("/state"));
  } catch (_) {
  } finally {
    setTimeout(poll, busy() ? 250 : 1000);
  }
}

function ensureSelection() {
  const list = groups();
  if (!list.length || !state.settings) return;
  if (!list.some((g) => g.major === state.settings.selectedMajor)) {
    state.settings.selectedMajor = list[0].major;
    saveSettings();
  }
}

function renderContent() {
  ensureSelection();
  renderHero();
  renderTiles();
  renderVersionPicker();
  renderNews();
}

let contentRetry = 0;
async function refreshContent(force = false) {
  clearTimeout(contentRetry);
  try {
    const content = await api(`/content${force ? "?force=1" : ""}`);
    if (content && content.groups) {
      state.content = content;
      renderContent();
    }
  } catch (_) {
  }
  const empty = !groups().length || !(state.content.news || []).length;
  contentRetry = setTimeout(() => refreshContent(), empty ? 15000 : 30 * 60 * 1000);
}

function selectMajor(major) {
  state.settings.selectedMajor = major;
  saveSettings();
  renderContent();
}

function selectVersion(version) {
  const group = selectedGroup();
  if (!group) return;
  state.settings.selectedVersions = { ...(state.settings.selectedVersions || {}), [group.major]: version };
  saveSettings();
  renderVersionPicker();
}

async function startLogin() {
  closeMenus();
  try {
    applyState(await api("/auth/login", { method: "POST" }));
  } catch (error) {
    toast(error.message, "error");
  }
}

async function launch() {
  if (state.game.running) {
    openModal(
      `<div class="modal-center">
        <div class="modal-icon danger">${icon("power", 24)}</div>
        <h3>Stop Minecraft?</h3>
        <p>Anything you have not saved in game will be lost.</p>
        <div class="modal-actions">
          <button class="chip-button" data-action="close-modal">Cancel</button>
          <button class="chip-button accent" data-action="confirm-stop">Stop game</button>
        </div>
      </div>`,
      "confirm"
    );
    return;
  }
  if (state.task.active) return;
  if (!activeAccount()) {
    toast("Sign in with your Microsoft account to play.");
    startLogin();
    return;
  }
  const version = selectedVersion();
  if (!version) {
    toast("The version list is still loading. Check your internet connection.", "error");
    return;
  }
  try {
    applyState(await api("/launch", { method: "POST", body: { version } }));
  } catch (error) {
    toast(error.message, "error");
  }
}

let modsMajor = null;
async function openMods() {
  const group = selectedGroup();
  if (!group) {
    toast("The version list is still loading.", "error");
    return;
  }
  modsMajor = group.major;
  openModal(
    `<div class="modal-head">
      <h3>Mods</h3><span class="badge">${esc(group.major)}</span><span class="spacer"></span>
      <label class="chip-button accent">${icon("plus", 14)}Add mods<input type="file" id="mod-input" accept=".jar" multiple hidden></label>
      <button class="chip-button" data-open="mods">${icon("folder-open", 14)}Folder</button>
      <button class="icon-button" data-action="close-modal" aria-label="Close">${icon("x", 16)}</button>
    </div>
    <div class="mods-body" id="mods-body"><div class="empty"><div class="spinner"></div></div></div>
    <div class="drop-zone" id="drop-zone">Drop .jar files to install them</div>`,
    "mods",
    true
  );
  const modal = $(".modal", $("#modal-layer"));
  modal.style.position = "relative";
  try {
    renderMods((await api(`/mods?major=${encodeURIComponent(modsMajor)}`)).mods);
  } catch (error) {
    toast(error.message, "error");
  }
}

let modsKey = "";
function renderMods(mods) {
  const body = $("#mods-body");
  if (!body) return;
  modsKey = JSON.stringify(mods);
  if (!mods.length) {
    body.innerHTML = `<div class="empty"><div class="modal-icon">${icon("package", 24)}</div><strong>No mods installed yet</strong><span>Drop .jar files here or use Add mods. Fabric API is installed for you on launch.</span></div>`;
    return;
  }
  body.innerHTML = mods
    .map(
      (m) => `<div class="mod-row ${m.enabled ? "" : "off"}" data-file="${esc(m.file)}">
        <div class="mod-icon">${m.icon ? `<img src="/api/mods/icon?major=${encodeURIComponent(modsMajor)}&file=${encodeURIComponent(m.file)}&t=${TOKEN}" alt="" draggable="false">` : icon("package", 18)}</div>
        <div class="mod-text">
          <div class="mod-title"><span class="mod-name">${esc(m.name)}</span>${m.version ? `<span class="mod-version">${esc(m.version)}</span>` : ""}${m.managed ? '<span class="badge">Auto</span>' : ""}</div>
          <div class="mod-desc">${esc(m.description || m.file)} <span style="color:var(--faint)">- ${formatSize(m.size)}</span></div>
        </div>
        ${m.managed ? "" : `<button class="toggle ${m.enabled ? "on" : ""}" data-mod-toggle role="switch" aria-checked="${m.enabled}"></button>
        <button class="icon-button danger" data-mod-delete title="Remove">${icon("trash-2", 15)}</button>`}
      </div>`
    )
    .join("");
  $$(".mod-icon img", body).forEach((img) => {
    img.addEventListener("load", () => {
      if (img.naturalWidth <= 32) img.classList.add("pixel");
    });
    img.addEventListener("error", () => {
      img.parentElement.innerHTML = icon("package", 18);
    });
  });
}

async function refreshMods() {
  if (state.modal !== "mods" || !modsMajor || document.hidden) return;
  const major = modsMajor;
  try {
    const data = await api(`/mods?major=${encodeURIComponent(major)}`);
    if (state.modal === "mods" && modsMajor === major && JSON.stringify(data.mods) !== modsKey) renderMods(data.mods);
  } catch (_) {}
}

setInterval(refreshMods, 1500);
window.addEventListener("focus", refreshMods);

async function uploadMods(files) {
  const jars = [...files].filter((f) => f.name.toLowerCase().endsWith(".jar"));
  if (!jars.length) {
    toast("Only .jar files can be installed as mods.", "error");
    return;
  }
  for (const file of jars) {
    try {
      const data = await api(`/mods/upload?major=${encodeURIComponent(modsMajor)}&name=${encodeURIComponent(file.name)}`, {
        method: "POST",
        raw: await file.arrayBuffer(),
      });
      renderMods(data.mods);
    } catch (error) {
      toast(error.message, "error");
    }
  }
  toast(jars.length === 1 ? `Installed ${jars[0].name}` : `Installed ${jars.length} mods`, "success");
}

async function openFolder(folder, extra = {}) {
  let major = selectedGroup()?.major || state.game.major;
  if (state.modal === "mods" && modsMajor) major = modsMajor;
  if (state.modal === "crash" && state.game.major) major = state.game.major;
  try {
    await api("/open", { method: "POST", body: { folder, major, ...extra } });
  } catch (error) {
    toast(error.message, "error");
  }
}

function bindSlider() {
  document.addEventListener("pointerdown", (event) => {
    const slider = event.target.closest(".slider");
    if (!slider) return;
    const min = Number(slider.dataset.min);
    const max = Number(slider.dataset.max);
    const step = Number(slider.dataset.step);
    const update = (x) => {
      const rect = slider.getBoundingClientRect();
      const ratio = Math.min(1, Math.max(0, (x - rect.left) / rect.width));
      const value = Math.round((min + ratio * (max - min)) / step) * step;
      state.settings.memoryMb = Math.min(max, Math.max(min, value));
      updateSlider();
    };
    slider.setPointerCapture(event.pointerId);
    slider.classList.add("dragging");
    update(event.clientX);
    const move = (e) => update(e.clientX);
    const up = () => {
      slider.classList.remove("dragging");
      slider.removeEventListener("pointermove", move);
      slider.removeEventListener("pointerup", up);
      slider.removeEventListener("pointercancel", up);
      saveSettings();
    };
    slider.addEventListener("pointermove", move);
    slider.addEventListener("pointerup", up);
    slider.addEventListener("pointercancel", up);
  });
}

function bindEvents() {
  document.addEventListener("click", async (event) => {
    const target = event.target;
    if (colorPicker && !target.closest(".color-pop") && !target.closest("#accent-picker")) closeColorPicker();
    if (selectPop && !target.closest(".select-pop") && !target.closest("#language-select")) closeSelect();

    const windowButton = target.closest("[data-window]");
    if (windowButton) {
      api(`/window/${windowButton.dataset.window}`, { method: "POST" }).catch(() => {});
      return;
    }

    const nav = target.closest("[data-nav]");
    if (nav) {
      setView(nav.dataset.nav);
      return;
    }

    if (target.closest("#view-all")) {
      setView("news");
      return;
    }

    const card = target.closest(".news-card[data-url]");
    if (card) {
      api("/open", { method: "POST", body: { url: card.dataset.url } }).catch((e) => toast(e.message, "error"));
      return;
    }

    const tile = target.closest(".tile[data-major]");
    if (tile) {
      closeMenus();
      selectMajor(tile.dataset.major);
      return;
    }

    if (target.closest("#version-button")) {
      $("#account-menu").classList.remove("open");
      $("#version-picker").classList.toggle("open");
      return;
    }

    const option = target.closest("[data-version]");
    if (option) {
      selectVersion(option.dataset.version);
      $("#version-picker").classList.remove("open");
      return;
    }

    if (target.closest("#launch-button")) {
      closeMenus();
      launch();
      return;
    }

    if (target.closest("#mods-button")) {
      closeMenus();
      openMods();
      return;
    }

    if (target.closest("#account")) {
      if (!activeAccount()) {
        startLogin();
        return;
      }
      $("#version-picker").classList.remove("open");
      const open = !$("#account-menu").classList.contains("open");
      $("#account-menu").classList.toggle("open", open);
      $("#account").classList.toggle("open", open);
      return;
    }

    const select = target.closest("[data-select]");
    if (select) {
      closeMenus();
      try {
        applyState(await api("/auth/select", { method: "POST", body: { uuid: select.dataset.select } }));
      } catch (error) {
        toast(error.message, "error");
      }
      return;
    }

    const action = target.closest("[data-action]");
    if (action) {
      const name = action.dataset.action;
      if (name === "add-account") startLogin();
      if (name === "sign-out") {
        closeMenus();
        const account = activeAccount();
        if (account) {
          try {
            applyState(await api("/auth/remove", { method: "POST", body: { uuid: account.uuid } }));
            toast(`Signed out of ${account.name}`);
          } catch (error) {
            toast(error.message, "error");
          }
        }
      }
      if (name === "cancel-login") {
        api("/auth/cancel", { method: "POST" }).then(applyState).catch(() => {});
      }
      if (name === "close-crash") {
        closeModal("crash");
        api("/crash/dismiss", { method: "POST" }).catch(() => {});
      }
      if (name === "close-modal") closeModal();
      if (name === "confirm-stop") {
        closeModal();
        api("/launch/stop", { method: "POST" }).then(applyState).catch(() => {});
      }
      return;
    }

    const languageButton = target.closest("#language-select");
    if (languageButton) {
      if (selectPop) {
        closeSelect();
        return;
      }
      openSelect(languageButton, LANGUAGES, state.settings.language || "", (value) => {
        $(".grow", languageButton).textContent = languageLabel(value);
        setSetting("language", value);
      });
      return;
    }

    const clear = target.closest("[data-clear]");
    if (clear) {
      clearStorage(clear);
      return;
    }

    const link = target.closest("[data-link]");
    if (link) {
      api("/open", { method: "POST", body: { url: link.dataset.link } }).catch((e) => toast(e.message, "error"));
      return;
    }

    const open = target.closest("[data-open]");
    if (open) {
      openFolder(open.dataset.open);
      return;
    }

    const report = target.closest("[data-crash-report]");
    if (report) {
      openFolder("crash", { path: report.dataset.crashReport });
      return;
    }

    const toggle = target.closest("[data-toggle]");
    if (toggle) {
      const key = toggle.dataset.toggle;
      const value = !state.settings[key];
      toggle.classList.toggle("on", value);
      toggle.setAttribute("aria-checked", value);
      setSetting(key, value);
      if (key === "customResolution" || key === "fullscreen") {
        $("#resolution-row").classList.toggle("disabled", !(state.settings.customResolution && !state.settings.fullscreen));
        const resToggle = $('[data-toggle="customResolution"]').closest(".row");
        resToggle.classList.toggle("disabled", state.settings.fullscreen);
      }
      return;
    }

    const segment = target.closest("#on-launch .segment");
    if (segment) {
      $$("#on-launch .segment").forEach((el) => el.classList.toggle("active", el === segment));
      setSetting("onLaunch", segment.dataset.value);
      return;
    }

    const swatch = target.closest(".swatch[data-color]");
    if (swatch) {
      setAccent(swatch.dataset.color);
      closeColorPicker();
      return;
    }

    const pickerButton = target.closest("#accent-picker");
    if (pickerButton) {
      if (colorPicker) closeColorPicker();
      else openColorPicker(pickerButton);
      return;
    }

    const modToggle = target.closest("[data-mod-toggle]");
    if (modToggle) {
      const row = modToggle.closest(".mod-row");
      const enabled = !modToggle.classList.contains("on");
      try {
        renderMods((await api("/mods/toggle", { method: "POST", body: { major: modsMajor, file: row.dataset.file, enabled } })).mods);
      } catch (error) {
        toast(error.message, "error");
      }
      return;
    }

    const modDelete = target.closest("[data-mod-delete]");
    if (modDelete) {
      if (!modDelete.classList.contains("confirm")) {
        modDelete.classList.add("confirm");
        modDelete.textContent = "Remove";
        setTimeout(() => {
          if (modDelete.isConnected) {
            modDelete.classList.remove("confirm");
            modDelete.innerHTML = icon("trash-2", 15);
          }
        }, 3000);
        return;
      }
      const row = modDelete.closest(".mod-row");
      try {
        renderMods((await api("/mods/delete", { method: "POST", body: { major: modsMajor, file: row.dataset.file } })).mods);
      } catch (error) {
        toast(error.message, "error");
      }
      return;
    }

    if (event.target === $("#modal-layer") && (state.modal === "mods" || state.modal === "confirm")) {
      closeModal();
      return;
    }

    if (!target.closest(".version-picker") && !target.closest(".account-wrap")) closeMenus();
  });

  document.addEventListener("change", (event) => {
    if (event.target.id === "mod-input") {
      uploadMods(event.target.files);
      event.target.value = "";
    }
    if (event.target.id === "res-width" || event.target.id === "res-height") {
      const width = Math.min(15360, Math.max(320, Number($("#res-width").value) || 1280));
      const height = Math.min(8640, Math.max(240, Number($("#res-height").value) || 720));
      $("#res-width").value = width;
      $("#res-height").value = height;
      state.settings.width = width;
      state.settings.height = height;
      saveSettings();
    }
  });

  document.addEventListener("input", (event) => {
    if (event.target.id === "res-width" || event.target.id === "res-height") {
      const clean = event.target.value.replace(/\D/g, "");
      if (clean !== event.target.value) event.target.value = clean;
    }
    if (event.target.id === "jvm-args") setSetting("jvmArgs", event.target.value);
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      closeMenus();
      closeColorPicker();
      if (state.modal === "mods" || state.modal === "confirm") closeModal();
    }
    if (event.ctrlKey && ["+", "-", "=", "0"].includes(event.key)) event.preventDefault();
    if (event.key === "F5" || (event.ctrlKey && event.key.toLowerCase() === "r")) event.preventDefault();
  });

  document.addEventListener("wheel", (event) => {
    if (event.ctrlKey) event.preventDefault();
  }, { passive: false });

  let dragDepth = 0;
  document.addEventListener("dragenter", (event) => {
    event.preventDefault();
    if (state.modal !== "mods") return;
    dragDepth++;
    $("#drop-zone")?.classList.add("visible");
  });
  document.addEventListener("dragleave", () => {
    if (state.modal !== "mods") return;
    dragDepth = Math.max(0, dragDepth - 1);
    if (!dragDepth) $("#drop-zone")?.classList.remove("visible");
  });
  document.addEventListener("dragover", (event) => event.preventDefault());
  document.addEventListener("drop", (event) => {
    event.preventDefault();
    dragDepth = 0;
    $("#drop-zone")?.classList.remove("visible");
    if (state.modal === "mods" && event.dataTransfer?.files?.length) uploadMods(event.dataTransfer.files);
  });

  let resizeTimer = 0;
  window.addEventListener("resize", () => {
    closeColorPicker();
    closeSelect();
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(() => api("/state").then(applyState).catch(() => {}), 120);
  });
}

async function boot() {
  hydrateIcons();
  bindEvents();
  bindSlider();
  let data;
  for (let attempt = 0; attempt < 20 && !data; attempt++) {
    try {
      data = await api("/boot");
    } catch (_) {
      await new Promise((r) => setTimeout(r, 250));
    }
  }
  if (!data) return;
  document.body.classList.add(data.frame);
  state.frame = data.frame;
  state.totalMemory = data.totalMemory || 8192;
  state.settings = data.settings;
  if (data.content && data.content.groups) state.content = data.content;
  applyAccent();
  renderSettings();
  renderContent();
  applyState(data);
  renderAccount();
  refreshContent();
  poll();
}

boot();
