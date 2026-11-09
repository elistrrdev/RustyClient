"use strict";

const TOKEN = location.hash.slice(1);
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

const state = {
  page: 0,
  desktop: true,
  startMenu: true,
  launch: true,
  parent: "",
  folder: "",
  required: 0,
  valid: true,
  installing: false,
  installed: false,
};

function icon(name, size = 16, stroke = 2) {
  const body = ICONS[name] || "";
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${stroke}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${body}</svg>`;
}

function hydrateIcons(root = document) {
  $$("[data-icon]", root).forEach((el) => {
    el.innerHTML = icon(el.dataset.icon, Number(el.dataset.size || 16), el.dataset.stroke || 2);
  });
}

async function api(path, body) {
  const options = { method: body === undefined ? "GET" : "POST", headers: { "x-rusty-token": TOKEN } };
  if (body !== undefined) {
    options.headers["content-type"] = "application/json";
    options.body = JSON.stringify(body);
  }
  const response = await fetch("/api" + path, options);
  let data = {};
  try {
    data = await response.json();
  } catch (_) {
    data = {};
  }
  if (!response.ok) throw new Error(data.error || "Something went wrong.");
  return data;
}

function formatBytes(bytes) {
  if (bytes == null) return "unknown";
  if (bytes >= 1073741824) return `${(bytes / 1073741824).toFixed(1)} GB`;
  if (bytes >= 1048576) return `${Math.round(bytes / 1048576)} MB`;
  return `${Math.round(bytes / 1024)} KB`;
}

function showPage(index) {
  const previous = state.page;
  state.page = index;
  $$(".page").forEach((page) => {
    const n = Number(page.dataset.page);
    page.classList.toggle("active", n === index);
    page.classList.toggle("left", n < index);
  });
  $('[data-window="close"]').disabled = index === 3 && state.installing;
  const focus = $(`.page[data-page="${index}"] .button.primary`);
  if (focus && index !== previous) setTimeout(() => focus.focus({ preventScroll: true }), 260);
}

function setOption(name, value) {
  state[name] = value;
  const el = $(`[data-option="${name}"]`);
  el.classList.toggle("checked", value);
  el.setAttribute("aria-checked", value);
}

let checkTimer = 0;
let checkSeq = 0;
function scheduleCheck() {
  clearTimeout(checkTimer);
  checkTimer = setTimeout(runCheck, 160);
}

async function runCheck() {
  const seq = ++checkSeq;
  state.parent = $("#parent").value;
  state.folder = $("#folder").value;
  let data;
  try {
    data = await api("/check", { parent: state.parent, folder: state.folder });
  } catch (error) {
    data = { valid: false, message: error.message };
  }
  if (seq !== checkSeq) return;
  state.valid = Boolean(data.valid);
  const target = $("#target");
  const space = $("#space");
  target.classList.toggle("invalid", !state.valid);
  if (state.valid) {
    $(".target-icon", target).innerHTML = icon("hard-drive", 16);
    $("#target-path").textContent = data.path;
    const low = data.free != null && data.free < data.required + 67108864;
    space.classList.toggle("low", low);
    space.innerHTML = `Needs <strong>${formatBytes(data.required)}</strong>${data.free != null ? ` - <strong>${formatBytes(data.free)}</strong> free on this drive` : ""}`;
    $("#install-button").disabled = low;
  } else {
    $(".target-icon", target).innerHTML = icon("circle-alert", 16);
    $("#target-path").textContent = data.message || "This location cannot be used.";
    space.innerHTML = "";
    $("#install-button").disabled = true;
  }
}

async function browse() {
  try {
    const data = await api("/browse", { current: $("#parent").value });
    if (data.path) {
      $("#parent").value = data.path;
      runCheck();
    }
  } catch (_) {
  }
}

let pollTimer = 0;
async function install() {
  if (!state.valid || state.installing) return;
  $("#install-error").classList.remove("visible");
  $("#error-back").classList.add("hidden");
  $("#error-retry").classList.add("hidden");
  $("#install-title").textContent = "Installing Rusty Client";
  $("#install-lead").textContent = "Copying files to your PC. This usually takes a few seconds.";
  $("#progress").style.display = "";
  $("#progress-fill").style.width = "0%";
  $("#progress-pct").textContent = "0%";
  $("#progress-detail").textContent = "Preparing";
  state.installing = true;
  showPage(3);
  try {
    await api("/install", { parent: state.parent, folder: state.folder, desktop: state.desktop, startMenu: state.startMenu });
  } catch (error) {
    failInstall(error.message);
    return;
  }
  poll();
}

async function poll() {
  clearTimeout(pollTimer);
  let status;
  try {
    status = await api("/status");
  } catch (_) {
    pollTimer = setTimeout(poll, 300);
    return;
  }
  const pct = Math.round((status.progress || 0) * 100);
  $("#progress-fill").style.width = `${pct}%`;
  $("#progress-pct").textContent = `${pct}%`;
  $("#progress-detail").textContent = status.detail || "";
  if (status.error) {
    failInstall(status.error);
    return;
  }
  if (status.done) {
    state.installing = false;
    state.installed = true;
    $("#progress-fill").style.width = "100%";
    $("#progress-pct").textContent = "100%";
    $("#done-text").textContent = `Installed to ${status.installed.replace(/\\RustyClient\.exe$/i, "")}`;
    setTimeout(() => showPage(4), 450);
    return;
  }
  pollTimer = setTimeout(poll, 120);
}

function failInstall(message) {
  state.installing = false;
  $("#install-title").textContent = "Installation failed";
  $("#install-lead").textContent = "Nothing is broken. Fix the problem below and try again.";
  $("#progress").style.display = "none";
  const box = $("#install-error");
  box.textContent = message;
  box.classList.add("visible");
  $("#error-back").classList.remove("hidden");
  $("#error-retry").classList.remove("hidden");
  $('[data-window="close"]').disabled = false;
}

function openDialog() {
  $("#dialog").classList.add("open");
  setTimeout(() => $('[data-action="keep"]').focus(), 50);
}

function closeDialog() {
  $("#dialog").classList.remove("open");
}

function requestClose() {
  if (state.installing) return;
  if (state.installed) {
    finish();
    return;
  }
  openDialog();
}

async function finish() {
  try {
    await api("/finish", { launch: state.installed && state.launch });
  } catch (_) {
  }
}

function bind() {
  document.addEventListener("click", (event) => {
    const windowButton = event.target.closest("[data-window]");
    if (windowButton) {
      if (windowButton.dataset.window === "close") requestClose();
      else api("/window/minimize", {}).catch(() => {});
      return;
    }
    const option = event.target.closest("[data-option]");
    if (option) {
      setOption(option.dataset.option, !state[option.dataset.option]);
      return;
    }
    const action = event.target.closest("[data-action]");
    if (!action) {
      if (event.target === $("#dialog")) closeDialog();
      return;
    }
    switch (action.dataset.action) {
      case "next":
        showPage(state.page + 1);
        if (state.page === 2) runCheck();
        break;
      case "back":
        showPage(Math.max(0, state.page - 1));
        break;
      case "cancel":
        openDialog();
        break;
      case "browse":
        browse();
        break;
      case "install":
        install();
        break;
      case "retry":
        install();
        break;
      case "back-from-error":
        showPage(2);
        runCheck();
        break;
      case "finish":
        finish();
        break;
      case "keep":
        closeDialog();
        break;
      case "quit":
        closeDialog();
        api("/window/close", {}).catch(() => {});
        break;
    }
  });

  document.addEventListener("input", (event) => {
    if (event.target.id === "parent" || event.target.id === "folder") scheduleCheck();
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      if ($("#dialog").classList.contains("open")) closeDialog();
      else requestClose();
      return;
    }
    if (event.key === "Enter" && !$("#dialog").classList.contains("open")) {
      const primary = $(`.page[data-page="${state.page}"] .button.primary:not(:disabled):not(.hidden)`);
      if (primary && document.activeElement?.tagName !== "BUTTON") {
        event.preventDefault();
        primary.click();
      }
    }
    if (event.ctrlKey && ["+", "-", "=", "0"].includes(event.key)) event.preventDefault();
    if (event.key === "F5" || (event.ctrlKey && event.key.toLowerCase() === "r")) event.preventDefault();
  });

  document.addEventListener("wheel", (event) => {
    if (event.ctrlKey) event.preventDefault();
  }, { passive: false });
  document.addEventListener("dragover", (event) => event.preventDefault());
  document.addEventListener("drop", (event) => event.preventDefault());
}

async function boot() {
  hydrateIcons();
  bind();
  let data;
  for (let attempt = 0; attempt < 20 && !data; attempt++) {
    try {
      data = await api("/boot");
    } catch (_) {
      await new Promise((r) => setTimeout(r, 200));
    }
  }
  if (!data) return;
  document.body.classList.add(data.frame);
  if (data.accent) document.documentElement.style.setProperty("--accent", data.accent);
  state.parent = data.parent;
  state.folder = data.folder;
  state.required = data.required;
  $("#parent").value = data.parent;
  $("#folder").value = data.folder;
  if (data.existing) {
    $("#welcome-text").textContent = "Rusty Client is already installed on this PC. Setup will update it to this build.";
  }
  showPage(0);
}

boot();
