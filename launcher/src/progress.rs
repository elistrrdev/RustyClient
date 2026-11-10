use std::ptr::{null, null_mut};
use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::{Arc, Mutex};

use windows_sys::Win32::Foundation::*;
use windows_sys::Win32::Graphics::Gdi::*;
use windows_sys::Win32::System::LibraryLoader::GetModuleHandleW;
use windows_sys::Win32::UI::HiDpi::*;
use windows_sys::Win32::UI::WindowsAndMessaging::*;

use crate::win::{self, wide};

const CLASS_NAME: &str = "RustyClientProgress";
const WM_APP_DONE: u32 = WM_APP + 40;
const SHOW_TIMER: usize = 1;
const PAINT_TIMER: usize = 2;
const WIDTH: f64 = 420.0;
const HEIGHT: f64 = 116.0;
const PADDING: f64 = 24.0;

#[derive(Default)]
pub struct Task {
    state: Mutex<(f64, String)>,
    cancel: AtomicBool,
}

impl Task {
    pub fn set(&self, fraction: f64, detail: &str) {
        let mut state = self.state.lock().unwrap();
        state.0 = fraction.clamp(0.0, 1.0);
        if state.1 != detail {
            state.1 = detail.to_string();
        }
    }

    pub fn cancelled(&self) -> bool {
        self.cancel.load(Ordering::Relaxed)
    }
}

static CURRENT: Mutex<Option<Arc<Task>>> = Mutex::new(None);
static HEADLINE: Mutex<&'static str> = Mutex::new("");
static ACCENT: AtomicU32 = AtomicU32::new(0);

pub fn run<T: Send + 'static>(title: &'static str, headline: &'static str, work: impl FnOnce(&Task) -> T + Send + 'static) -> T {
    let task = Arc::new(Task::default());
    *CURRENT.lock().unwrap() = Some(task.clone());
    *HEADLINE.lock().unwrap() = headline;
    ACCENT.store(accent(), Ordering::Relaxed);

    let hwnd = create(title);
    let handle = hwnd as isize;
    let worker_task = task.clone();
    let worker = std::thread::spawn(move || {
        let result = work(&worker_task);
        if handle != 0 {
            unsafe {
                PostMessageW(handle as HWND, WM_APP_DONE, 0, 0);
            }
        }
        result
    });
    if !hwnd.is_null() {
        unsafe {
            let mut message: MSG = std::mem::zeroed();
            while GetMessageW(&mut message, null_mut(), 0, 0) > 0 {
                TranslateMessage(&message);
                DispatchMessageW(&message);
            }
        }
    }
    let result = worker.join().expect("runtime worker");
    *CURRENT.lock().unwrap() = None;
    result
}

fn rgb(r: u8, g: u8, b: u8) -> u32 {
    r as u32 | ((g as u32) << 8) | ((b as u32) << 16)
}

fn accent() -> u32 {
    let fallback = rgb(0x1a, 0xb0, 0xdd);
    let Some(appdata) = std::env::var_os("APPDATA") else {
        return fallback;
    };
    let file = std::path::PathBuf::from(appdata).join(".rustyclient").join("settings.json");
    let Some(settings) = std::fs::read(file).ok().and_then(|bytes| serde_json::from_slice::<serde_json::Value>(&bytes).ok()) else {
        return fallback;
    };
    let Some(hex) = settings["accent"].as_str().and_then(|a| a.strip_prefix('#')) else {
        return fallback;
    };
    if hex.len() != 6 {
        return fallback;
    }
    let Ok(value) = u32::from_str_radix(hex, 16) else {
        return fallback;
    };
    rgb((value >> 16) as u8, (value >> 8) as u8, value as u8)
}

fn create(title: &str) -> HWND {
    unsafe {
        let instance = GetModuleHandleW(null());
        let class = wide(CLASS_NAME);
        let wc = WNDCLASSEXW {
            cbSize: std::mem::size_of::<WNDCLASSEXW>() as u32,
            style: CS_HREDRAW | CS_VREDRAW,
            lpfnWndProc: Some(window_proc),
            cbClsExtra: 0,
            cbWndExtra: 0,
            hInstance: instance,
            hIcon: LoadImageW(instance, 2 as _, IMAGE_ICON, GetSystemMetrics(SM_CXICON), GetSystemMetrics(SM_CYICON), LR_DEFAULTCOLOR) as HICON,
            hCursor: LoadCursorW(null_mut(), IDC_ARROW),
            hbrBackground: null_mut(),
            lpszMenuName: null(),
            lpszClassName: class.as_ptr(),
            hIconSm: LoadImageW(instance, 2 as _, IMAGE_ICON, GetSystemMetrics(SM_CXSMICON), GetSystemMetrics(SM_CYSMICON), LR_DEFAULTCOLOR) as HICON,
        };
        RegisterClassExW(&wc);

        let mut cursor = POINT { x: 0, y: 0 };
        GetCursorPos(&mut cursor);
        let monitor = MonitorFromPoint(cursor, MONITOR_DEFAULTTOPRIMARY);
        let mut info: MONITORINFO = std::mem::zeroed();
        info.cbSize = std::mem::size_of::<MONITORINFO>() as u32;
        GetMonitorInfoW(monitor, &mut info);
        let mut dpi_x = 96u32;
        let mut dpi_y = 96u32;
        GetDpiForMonitor(monitor, MDT_EFFECTIVE_DPI, &mut dpi_x, &mut dpi_y);
        let scale = dpi_x as f64 / 96.0;

        let style = WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX;
        let mut rect = RECT {
            left: 0,
            top: 0,
            right: (WIDTH * scale).round() as i32,
            bottom: (HEIGHT * scale).round() as i32,
        };
        AdjustWindowRectExForDpi(&mut rect, style, 0, 0, dpi_x);
        let width = rect.right - rect.left;
        let height = rect.bottom - rect.top;
        let work = info.rcWork;
        let x = work.left + (work.right - work.left - width) / 2;
        let y = work.top + (work.bottom - work.top - height) / 2;

        let title = wide(title);
        let hwnd = CreateWindowExW(0, class.as_ptr(), title.as_ptr(), style, x, y, width, height, null_mut(), null_mut(), instance, null());
        if hwnd.is_null() {
            return hwnd;
        }
        win::apply_dark_frame(hwnd);
        SetTimer(hwnd, SHOW_TIMER, 250, None);
        SetTimer(hwnd, PAINT_TIMER, 33, None);
        hwnd
    }
}

unsafe extern "system" fn window_proc(hwnd: HWND, msg: u32, wparam: WPARAM, lparam: LPARAM) -> LRESULT {
    unsafe {
        match msg {
            WM_CLOSE => {
                if let Some(task) = CURRENT.lock().unwrap().as_ref() {
                    task.cancel.store(true, Ordering::Relaxed);
                    task.set(task.state.lock().unwrap().0, "Cancelling");
                }
                0
            }
            WM_TIMER => {
                if wparam == SHOW_TIMER {
                    KillTimer(hwnd, SHOW_TIMER);
                    ShowWindow(hwnd, SW_SHOW);
                    SetForegroundWindow(hwnd);
                } else if IsWindowVisible(hwnd) != 0 {
                    InvalidateRect(hwnd, null(), 0);
                }
                0
            }
            WM_APP_DONE => {
                DestroyWindow(hwnd);
                0
            }
            WM_DESTROY => {
                PostQuitMessage(0);
                0
            }
            WM_ERASEBKGND => 1,
            WM_PAINT => {
                paint(hwnd);
                0
            }
            WM_DPICHANGED => {
                let suggested = &*(lparam as *const RECT);
                SetWindowPos(
                    hwnd,
                    null_mut(),
                    suggested.left,
                    suggested.top,
                    suggested.right - suggested.left,
                    suggested.bottom - suggested.top,
                    SWP_NOZORDER | SWP_NOACTIVATE,
                );
                0
            }
            _ => DefWindowProcW(hwnd, msg, wparam, lparam),
        }
    }
}

unsafe fn font(height: i32, weight: i32) -> HFONT {
    let face = wide("Segoe UI");
    unsafe {
        CreateFontW(
            -height,
            0,
            0,
            0,
            weight,
            0,
            0,
            0,
            DEFAULT_CHARSET as u32,
            OUT_DEFAULT_PRECIS as u32,
            CLIP_DEFAULT_PRECIS as u32,
            CLEARTYPE_QUALITY as u32,
            0,
            face.as_ptr(),
        )
    }
}

unsafe fn text(dc: HDC, value: &str, rect: RECT, color: u32, flags: DRAW_TEXT_FORMAT) {
    let mut buffer: Vec<u16> = value.encode_utf16().collect();
    let mut rect = rect;
    unsafe {
        SetTextColor(dc, color);
        DrawTextW(dc, buffer.as_mut_ptr(), buffer.len() as i32, &mut rect, flags | DT_SINGLELINE | DT_VCENTER | DT_NOPREFIX);
    }
}

unsafe fn rounded(dc: HDC, left: i32, top: i32, right: i32, bottom: i32, color: u32) {
    unsafe {
        let brush = CreateSolidBrush(color);
        let old_brush = SelectObject(dc, brush as HGDIOBJ);
        let old_pen = SelectObject(dc, GetStockObject(NULL_PEN));
        let diameter = bottom - top;
        RoundRect(dc, left, top, right + 1, bottom + 1, diameter, diameter);
        SelectObject(dc, old_pen);
        SelectObject(dc, old_brush);
        DeleteObject(brush as HGDIOBJ);
    }
}

unsafe fn paint(hwnd: HWND) {
    let (fraction, detail) = CURRENT
        .lock()
        .unwrap()
        .as_ref()
        .map(|task| task.state.lock().unwrap().clone())
        .unwrap_or_default();
    let headline = *HEADLINE.lock().unwrap();
    unsafe {
        let dpi = GetDpiForWindow(hwnd);
        let scale = if dpi == 0 { 1.0 } else { dpi as f64 / 96.0 };
        let px = |value: f64| (value * scale).round() as i32;

        let mut ps: PAINTSTRUCT = std::mem::zeroed();
        let dc = BeginPaint(hwnd, &mut ps);
        let mut client: RECT = std::mem::zeroed();
        GetClientRect(hwnd, &mut client);
        let width = client.right;
        let height = client.bottom;

        let memory = CreateCompatibleDC(dc);
        let bitmap = CreateCompatibleBitmap(dc, width, height);
        let old_bitmap = SelectObject(memory, bitmap as HGDIOBJ);

        let background = CreateSolidBrush(rgb(0x13, 0x12, 0x11));
        FillRect(memory, &client, background);
        DeleteObject(background as HGDIOBJ);
        SetBkMode(memory, TRANSPARENT as _);

        let left = px(PADDING);
        let right = width - px(PADDING);

        let headline_font = font(px(17.0), 600);
        let old_font = SelectObject(memory, headline_font as HGDIOBJ);
        let headline_rect = RECT { left, top: px(20.0), right: right - px(56.0), bottom: px(46.0) };
        text(memory, headline, headline_rect, rgb(0xf2, 0xee, 0xea), DT_LEFT | DT_END_ELLIPSIS);

        let detail_font = font(px(13.0), 400);
        SelectObject(memory, detail_font as HGDIOBJ);
        let percent_rect = RECT { left: right - px(56.0), top: px(20.0), right, bottom: px(46.0) };
        text(memory, &format!("{}%", (fraction * 100.0).floor() as i32), percent_rect, rgb(0xc2, 0xbe, 0xba), DT_RIGHT);
        let detail_rect = RECT { left, top: px(48.0), right, bottom: px(68.0) };
        text(memory, &detail, detail_rect, rgb(0x94, 0x8c, 0x85), DT_LEFT | DT_END_ELLIPSIS);
        SelectObject(memory, old_font);
        DeleteObject(headline_font as HGDIOBJ);
        DeleteObject(detail_font as HGDIOBJ);

        let bar_top = px(82.0);
        let bar_bottom = bar_top + px(6.0).max(2);
        rounded(memory, left, bar_top, right, bar_bottom, rgb(0x2a, 0x28, 0x27));
        if fraction > 0.0 {
            let span = right - left;
            let fill = ((span as f64 * fraction).round() as i32).max(bar_bottom - bar_top).min(span);
            rounded(memory, left, bar_top, left + fill, bar_bottom, ACCENT.load(Ordering::Relaxed));
        }

        BitBlt(dc, 0, 0, width, height, memory, 0, 0, SRCCOPY);
        SelectObject(memory, old_bitmap);
        DeleteObject(bitmap as HGDIOBJ);
        DeleteDC(memory);
        EndPaint(hwnd, &ps);
    }
}
