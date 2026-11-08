use std::cell::{Cell, RefCell};
use std::ffi::c_void;
use std::ptr::{null, null_mut};
use std::sync::OnceLock;
use std::sync::atomic::{AtomicBool, AtomicIsize, Ordering};

use windows_sys::Win32::Foundation::*;
use windows_sys::Win32::Graphics::Dwm::*;
use windows_sys::Win32::Graphics::Gdi::*;
use windows_sys::Win32::Security::Cryptography::*;
use windows_sys::Win32::System::LibraryLoader::GetModuleHandleW;
use windows_sys::Win32::System::SystemInformation::*;
use windows_sys::Win32::System::Threading::CreateMutexW;
use windows_sys::Win32::UI::HiDpi::*;
use windows_sys::Win32::UI::Shell::*;
use windows_sys::Win32::UI::WindowsAndMessaging::*;

pub const WM_APP_MINIMIZE: u32 = WM_APP + 1;
pub const WM_APP_MAXIMIZE: u32 = WM_APP + 2;
pub const WM_APP_CLOSE: u32 = WM_APP + 3;
pub const WM_APP_HIDE: u32 = WM_APP + 4;
pub const WM_APP_RESTORE: u32 = WM_APP + 5;
pub const WM_APP_LOGIN_OPEN: u32 = WM_APP + 10;
pub const WM_APP_LOGIN_CLOSE: u32 = WM_APP + 11;
#[cfg(feature = "setup")]
pub const WM_APP_BROWSE: u32 = WM_APP + 12;

pub struct WindowSpec {
    pub title: &'static str,
    pub width: i32,
    pub height: i32,
    pub min_width: i32,
    pub min_height: i32,
    pub resizable: bool,
}

#[cfg(not(feature = "setup"))]
const CLASS_NAME: &str = "RustyClientWindow";
#[cfg(feature = "setup")]
const CLASS_NAME: &str = "RustyClientSetupWindow";
const RESIZE_BAND: f64 = 6.0;
const CORNER_RADIUS: f64 = 8.0;
const SHOW_TIMER: usize = 1;

static MAIN_HWND: AtomicIsize = AtomicIsize::new(0);
static WIN11: OnceLock<bool> = OnceLock::new();
static MIN_SIZE: OnceLock<(i32, i32)> = OnceLock::new();
static RESIZABLE: AtomicBool = AtomicBool::new(true);
pub static ALLOW_CLOSE: AtomicBool = AtomicBool::new(false);

thread_local! {
    static DRAG_REGIONS: RefCell<Vec<(i32, i32, i32, i32, bool)>> = const { RefCell::new(Vec::new()) };
    static BROWSER_HWND: Cell<isize> = const { Cell::new(0) };
    static SHOWN: Cell<bool> = const { Cell::new(false) };
}

pub fn wide(s: &str) -> Vec<u16> {
    s.encode_utf16().chain(std::iter::once(0)).collect()
}

#[repr(C)]
struct OsVersionInfo {
    size: u32,
    major: u32,
    minor: u32,
    build: u32,
    platform: u32,
    csd: [u16; 128],
}

#[link(name = "ntdll")]
unsafe extern "system" {
    fn RtlGetVersion(info: *mut OsVersionInfo) -> i32;
}

pub fn os_version() -> (u32, u32, u32) {
    unsafe {
        let mut info: OsVersionInfo = std::mem::zeroed();
        info.size = std::mem::size_of::<OsVersionInfo>() as u32;
        if RtlGetVersion(&mut info) == 0 {
            (info.major, info.minor, info.build)
        } else {
            (10, 0, 19045)
        }
    }
}

pub fn is_win11() -> bool {
    *WIN11.get_or_init(|| {
        let (major, _, build) = os_version();
        major >= 10 && build >= 22000
    })
}

pub fn total_memory_mb() -> u32 {
    unsafe {
        let mut status: MEMORYSTATUSEX = std::mem::zeroed();
        status.dwLength = std::mem::size_of::<MEMORYSTATUSEX>() as u32;
        if GlobalMemoryStatusEx(&mut status) != 0 {
            (status.ullTotalPhys / (1024 * 1024)) as u32
        } else {
            8192
        }
    }
}

pub fn protect(data: &[u8]) -> Option<Vec<u8>> {
    crypt(data, true)
}

pub fn unprotect(data: &[u8]) -> Option<Vec<u8>> {
    crypt(data, false)
}

fn crypt(data: &[u8], seal: bool) -> Option<Vec<u8>> {
    unsafe {
        let input = CRYPT_INTEGER_BLOB {
            cbData: data.len() as u32,
            pbData: data.as_ptr() as *mut u8,
        };
        let mut output = CRYPT_INTEGER_BLOB {
            cbData: 0,
            pbData: null_mut(),
        };
        let ok = if seal {
            CryptProtectData(&input, null(), null(), null(), null(), CRYPTPROTECT_UI_FORBIDDEN, &mut output)
        } else {
            CryptUnprotectData(&input, null_mut(), null(), null(), null(), CRYPTPROTECT_UI_FORBIDDEN, &mut output)
        };
        if ok == 0 || output.pbData.is_null() {
            return None;
        }
        let bytes = std::slice::from_raw_parts(output.pbData, output.cbData as usize).to_vec();
        LocalFree(output.pbData as HLOCAL);
        Some(bytes)
    }
}

pub fn acquire_single_instance() -> bool {
    unsafe {
        let name = if cfg!(feature = "setup") {
            wide("Local\\RustyClientSetupInstance")
        } else {
            wide("Local\\RustyClientLauncherInstance")
        };
        let handle = CreateMutexW(null(), 0, name.as_ptr());
        if handle.is_null() {
            return true;
        }
        if GetLastError() == ERROR_ALREADY_EXISTS {
            let class = wide(CLASS_NAME);
            let existing = FindWindowW(class.as_ptr(), null());
            if !existing.is_null() {
                ShowWindow(existing, SW_SHOW);
                if IsIconic(existing) != 0 {
                    ShowWindow(existing, SW_RESTORE);
                }
                SetForegroundWindow(existing);
            }
            return false;
        }
        true
    }
}

pub fn shell_open(target: &str) -> bool {
    unsafe {
        let verb = wide("open");
        let file = wide(target);
        let result = ShellExecuteW(null_mut(), verb.as_ptr(), file.as_ptr(), null(), null(), SW_SHOWNORMAL);
        result as isize > 32
    }
}

pub fn main_hwnd() -> HWND {
    MAIN_HWND.load(Ordering::SeqCst) as HWND
}

pub fn post(message: u32) {
    let hwnd = main_hwnd();
    if !hwnd.is_null() {
        unsafe {
            PostMessageW(hwnd, message, 0, 0);
        }
    }
}

pub fn is_maximized() -> bool {
    let hwnd = main_hwnd();
    !hwnd.is_null() && unsafe { IsZoomed(hwnd) != 0 }
}

fn scale_for(hwnd: HWND) -> f64 {
    let dpi = unsafe { GetDpiForWindow(hwnd) };
    if dpi == 0 { 1.0 } else { dpi as f64 / 96.0 }
}

fn frame_thickness(dpi: u32) -> i32 {
    unsafe { GetSystemMetricsForDpi(SM_CXFRAME, dpi) + GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi) }
}

fn rgb(r: u8, g: u8, b: u8) -> u32 {
    r as u32 | ((g as u32) << 8) | ((b as u32) << 16)
}

pub fn apply_dark_frame(hwnd: HWND) {
    unsafe {
        let dark: i32 = 1;
        DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE as u32, &dark as *const _ as *const c_void, 4);
        if is_win11() {
            let caption = rgb(0x0f, 0x0e, 0x0d);
            DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR as u32, &caption as *const _ as *const c_void, 4);
            let border = rgb(0x24, 0x23, 0x22);
            DwmSetWindowAttribute(hwnd, DWMWA_BORDER_COLOR as u32, &border as *const _ as *const c_void, 4);
        }
    }
}

pub fn create_main_window(spec: &WindowSpec) -> HWND {
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
            hIcon: null_mut(),
            hCursor: LoadCursorW(null_mut(), IDC_ARROW),
            hbrBackground: CreateSolidBrush(rgb(0x13, 0x12, 0x11)),
            lpszMenuName: null(),
            lpszClassName: class.as_ptr(),
            hIconSm: null_mut(),
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

        let work = info.rcWork;
        let work_w = work.right - work.left;
        let work_h = work.bottom - work.top;
        let _ = MIN_SIZE.set((spec.min_width, spec.min_height));
        RESIZABLE.store(spec.resizable, Ordering::SeqCst);
        let mut width = ((spec.width as f64) * scale).round() as i32;
        let mut height = ((spec.height as f64) * scale).round() as i32;
        width = width.min(work_w - 32);
        height = height.min(work_h - 32);
        let (extra_w, extra_h) = if is_win11() {
            let frame = frame_thickness(dpi_x);
            (frame * 2, frame)
        } else {
            (0, 0)
        };
        let total_w = width + extra_w;
        let total_h = height + extra_h;
        let x = work.left + (work_w - width) / 2 - extra_w / 2;
        let y = work.top + (work_h - height) / 2;

        let title = wide(spec.title);
        let style = if spec.resizable {
            WS_OVERLAPPEDWINDOW | WS_CLIPCHILDREN
        } else {
            (WS_OVERLAPPEDWINDOW & !WS_MAXIMIZEBOX) | WS_CLIPCHILDREN
        };
        let hwnd = CreateWindowExW(
            WS_EX_APPWINDOW,
            class.as_ptr(),
            title.as_ptr(),
            style,
            x,
            y,
            total_w,
            total_h,
            null_mut(),
            null_mut(),
            instance,
            null(),
        );
        MAIN_HWND.store(hwnd as isize, Ordering::SeqCst);

        apply_dark_frame(hwnd);
        if is_win11() {
            let corner = DWMWCP_ROUND;
            DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE as u32, &corner as *const _ as *const c_void, 4);
        }
        SetWindowPos(hwnd, null_mut(), 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_FRAMECHANGED);
        update_region(hwnd);
        SetTimer(hwnd, SHOW_TIMER, 4000, None);
        hwnd
    }
}

pub fn client_size(hwnd: HWND) -> (i32, i32) {
    unsafe {
        let mut rect: RECT = std::mem::zeroed();
        GetClientRect(hwnd, &mut rect);
        (rect.right - rect.left, rect.bottom - rect.top)
    }
}

pub fn set_browser_hwnd(hwnd: HWND) {
    BROWSER_HWND.with(|b| b.set(hwnd as isize));
    let main = main_hwnd();
    if !main.is_null() {
        resize_browser(main);
        subclass_children(main);
    }
}

pub fn show_main_window() {
    if SHOWN.with(|s| s.replace(true)) {
        return;
    }
    let hwnd = main_hwnd();
    if hwnd.is_null() {
        return;
    }
    unsafe {
        KillTimer(hwnd, SHOW_TIMER);
        ShowWindow(hwnd, SW_SHOW);
        SetForegroundWindow(hwnd);
    }
    subclass_children(hwnd);
}

pub fn set_drag_regions(regions: Vec<(i32, i32, i32, i32, bool)>) {
    DRAG_REGIONS.with(|r| *r.borrow_mut() = regions);
    let hwnd = main_hwnd();
    if !hwnd.is_null() {
        subclass_children(hwnd);
    }
}

pub fn subclass_children(hwnd: HWND) {
    unsafe {
        EnumChildWindows(hwnd, Some(subclass_one), 0);
    }
}

unsafe extern "system" fn subclass_one(child: HWND, _: LPARAM) -> windows_sys::core::BOOL {
    unsafe {
        SetWindowSubclass(child, Some(child_proc), 0x5275_7374, 0);
    }
    1
}

unsafe extern "system" fn child_proc(
    hwnd: HWND,
    msg: u32,
    wparam: WPARAM,
    lparam: LPARAM,
    _id: usize,
    _data: usize,
) -> LRESULT {
    unsafe {
        if msg == WM_NCHITTEST {
            let main = main_hwnd();
            if !main.is_null() {
                let point = POINT {
                    x: (lparam & 0xffff) as i16 as i32,
                    y: ((lparam >> 16) & 0xffff) as i16 as i32,
                };
                if custom_hit_test(main, point) != HTCLIENT {
                    return HTTRANSPARENT as LRESULT;
                }
            }
        }
        DefSubclassProc(hwnd, msg, wparam, lparam)
    }
}

fn custom_hit_test(hwnd: HWND, screen: POINT) -> u32 {
    unsafe {
        let mut point = screen;
        ScreenToClient(hwnd, &mut point);
        let (width, height) = client_size(hwnd);
        let scale = scale_for(hwnd);
        let maximized = IsZoomed(hwnd) != 0;
        if !maximized && RESIZABLE.load(Ordering::SeqCst) {
            let band = (RESIZE_BAND * scale).round() as i32;
            let corner = band * 2;
            let top = point.y < band;
            let bottom = point.y >= height - band;
            let left = point.x < band;
            let right = point.x >= width - band;
            if is_win11() {
                if top {
                    if point.x < corner {
                        return HTTOPLEFT;
                    }
                    if point.x >= width - corner {
                        return HTTOPRIGHT;
                    }
                    return HTTOP;
                }
            } else {
                let near_top = point.y < corner;
                let near_bottom = point.y >= height - corner;
                let near_left = point.x < corner;
                let near_right = point.x >= width - corner;
                if (top && near_left) || (left && near_top) {
                    return HTTOPLEFT;
                }
                if (top && near_right) || (right && near_top) {
                    return HTTOPRIGHT;
                }
                if (bottom && near_left) || (left && near_bottom) {
                    return HTBOTTOMLEFT;
                }
                if (bottom && near_right) || (right && near_bottom) {
                    return HTBOTTOMRIGHT;
                }
                if top {
                    return HTTOP;
                }
                if bottom {
                    return HTBOTTOM;
                }
                if left {
                    return HTLEFT;
                }
                if right {
                    return HTRIGHT;
                }
            }
        }
        let css_x = (point.x as f64 / scale) as i32;
        let css_y = (point.y as f64 / scale) as i32;
        let draggable = DRAG_REGIONS.with(|regions| {
            let mut inside = false;
            for &(x, y, w, h, drag) in regions.borrow().iter() {
                if css_x >= x && css_x < x + w && css_y >= y && css_y < y + h {
                    inside = drag;
                }
            }
            inside
        });
        if draggable { HTCAPTION } else { HTCLIENT }
    }
}

fn resize_browser(hwnd: HWND) {
    let browser = BROWSER_HWND.with(|b| b.get()) as HWND;
    if browser.is_null() {
        return;
    }
    let (width, height) = client_size(hwnd);
    unsafe {
        SetWindowPos(browser, null_mut(), 0, 0, width, height, SWP_NOZORDER | SWP_NOACTIVATE);
    }
}

fn update_region(hwnd: HWND) {
    if is_win11() {
        return;
    }
    unsafe {
        if IsZoomed(hwnd) != 0 {
            SetWindowRgn(hwnd, null_mut(), 1);
            return;
        }
        let mut rect: RECT = std::mem::zeroed();
        GetWindowRect(hwnd, &mut rect);
        let width = rect.right - rect.left;
        let height = rect.bottom - rect.top;
        let diameter = ((CORNER_RADIUS * 2.0) * scale_for(hwnd)).round() as i32;
        let region = CreateRoundRectRgn(0, 0, width + 1, height + 1, diameter, diameter);
        SetWindowRgn(hwnd, region, 1);
    }
}

fn toggle_maximize(hwnd: HWND) {
    unsafe {
        if IsZoomed(hwnd) != 0 {
            ShowWindow(hwnd, SW_RESTORE);
        } else {
            ShowWindow(hwnd, SW_MAXIMIZE);
        }
    }
}

unsafe extern "system" fn window_proc(hwnd: HWND, msg: u32, wparam: WPARAM, lparam: LPARAM) -> LRESULT {
    unsafe {
        match msg {
            WM_NCCALCSIZE if wparam != 0 => {
                let params = &mut *(lparam as *mut NCCALCSIZE_PARAMS);
                if IsZoomed(hwnd) != 0 {
                    let frame = frame_thickness(GetDpiForWindow(hwnd));
                    let rect = &mut params.rgrc[0];
                    rect.left += frame;
                    rect.top += frame;
                    rect.right -= frame;
                    rect.bottom -= frame;
                    return 0;
                }
                if is_win11() {
                    let top = params.rgrc[0].top;
                    let result = DefWindowProcW(hwnd, msg, wparam, lparam);
                    params.rgrc[0].top = top;
                    return result;
                }
                0
            }
            WM_NCHITTEST => {
                if is_win11() {
                    let native = DefWindowProcW(hwnd, msg, wparam, lparam);
                    if native as u32 != HTCLIENT {
                        if !RESIZABLE.load(Ordering::SeqCst) {
                            return HTBORDER as LRESULT;
                        }
                        return native;
                    }
                }
                let point = POINT {
                    x: (lparam & 0xffff) as i16 as i32,
                    y: ((lparam >> 16) & 0xffff) as i16 as i32,
                };
                custom_hit_test(hwnd, point) as LRESULT
            }
            WM_NCACTIVATE if !is_win11() => DefWindowProcW(hwnd, msg, wparam, -1),
            WM_SIZE => {
                resize_browser(hwnd);
                update_region(hwnd);
                0
            }
            WM_GETMINMAXINFO => {
                let info = &mut *(lparam as *mut MINMAXINFO);
                let scale = scale_for(hwnd);
                let extra = if is_win11() { frame_thickness(GetDpiForWindow(hwnd)) } else { 0 };
                let (min_width, min_height) = MIN_SIZE.get().copied().unwrap_or((640, 400));
                info.ptMinTrackSize.x = (min_width as f64 * scale) as i32 + extra * 2;
                info.ptMinTrackSize.y = (min_height as f64 * scale) as i32 + extra;
                0
            }
            WM_DPICHANGED => {
                let rect = &*(lparam as *const RECT);
                SetWindowPos(
                    hwnd,
                    null_mut(),
                    rect.left,
                    rect.top,
                    rect.right - rect.left,
                    rect.bottom - rect.top,
                    SWP_NOZORDER | SWP_NOACTIVATE,
                );
                0
            }
            WM_SETFOCUS => {
                crate::app::focus_main_browser();
                0
            }
            WM_ERASEBKGND => 1,
            WM_TIMER if wparam == SHOW_TIMER => {
                show_main_window();
                0
            }
            WM_CLOSE => {
                if !ALLOW_CLOSE.load(Ordering::SeqCst) && crate::app::request_close() {
                    return 0;
                }
                DefWindowProcW(hwnd, msg, wparam, lparam)
            }
            WM_DESTROY => {
                crate::app::on_main_window_destroyed();
                0
            }
            WM_APP_MINIMIZE => {
                ShowWindow(hwnd, SW_MINIMIZE);
                0
            }
            WM_APP_MAXIMIZE => {
                if RESIZABLE.load(Ordering::SeqCst) {
                    toggle_maximize(hwnd);
                }
                0
            }
            WM_APP_CLOSE => {
                PostMessageW(hwnd, WM_CLOSE, 0, 0);
                0
            }
            WM_APP_HIDE => {
                ShowWindow(hwnd, SW_HIDE);
                0
            }
            WM_APP_RESTORE => {
                ShowWindow(hwnd, SW_SHOW);
                if IsIconic(hwnd) != 0 {
                    ShowWindow(hwnd, SW_RESTORE);
                }
                SetForegroundWindow(hwnd);
                0
            }
            WM_APP_LOGIN_OPEN => {
                crate::app::open_login_window();
                0
            }
            WM_APP_LOGIN_CLOSE => {
                crate::app::close_login_window();
                0
            }
            #[cfg(feature = "setup")]
            WM_APP_BROWSE => {
                crate::app::open_folder_dialog();
                0
            }
            _ => DefWindowProcW(hwnd, msg, wparam, lparam),
        }
    }
}

pub fn centered_popup_bounds(width: i32, height: i32) -> (i32, i32, i32, i32) {
    let hwnd = main_hwnd();
    unsafe {
        let scale = if hwnd.is_null() { 1.0 } else { scale_for(hwnd) };
        let w = (width as f64 * scale) as i32;
        let h = (height as f64 * scale) as i32;
        let mut rect: RECT = std::mem::zeroed();
        if hwnd.is_null() || GetWindowRect(hwnd, &mut rect) == 0 {
            return (CW_USEDEFAULT, CW_USEDEFAULT, w, h);
        }
        let x = rect.left + ((rect.right - rect.left) - w) / 2;
        let y = rect.top + ((rect.bottom - rect.top) - h) / 2;
        (x, y.max(0), w, h)
    }
}
