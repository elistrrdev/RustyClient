use std::ptr::null_mut;

use windows_sys::Win32::Foundation::ERROR_SUCCESS;
use windows_sys::Win32::System::Registry::*;

use crate::win::wide;

pub const UNINSTALL_KEY: &str = "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\RustyClient";

#[cfg(feature = "setup")]
pub enum Value<'a> {
    Text(&'a str),
    Number(u32),
}

#[cfg(feature = "setup")]
pub fn write(values: &[(&str, Value)]) -> Result<(), String> {
    unsafe {
        let key_name = wide(UNINSTALL_KEY);
        let mut key: HKEY = null_mut();
        let status = RegCreateKeyExW(
            HKEY_LOCAL_MACHINE,
            key_name.as_ptr(),
            0,
            std::ptr::null(),
            REG_OPTION_NON_VOLATILE,
            KEY_WRITE | KEY_WOW64_64KEY,
            std::ptr::null(),
            &mut key,
            null_mut(),
        );
        if status != ERROR_SUCCESS {
            return Err("Setup could not register Rusty Client with Windows.".into());
        }
        for (name, value) in values {
            let name = wide(name);
            match value {
                Value::Text(text) => {
                    let data = wide(text);
                    RegSetValueExW(key, name.as_ptr(), 0, REG_SZ, data.as_ptr().cast(), (data.len() * 2) as u32);
                }
                Value::Number(number) => {
                    RegSetValueExW(key, name.as_ptr(), 0, REG_DWORD, (number as *const u32).cast(), 4);
                }
            }
        }
        RegCloseKey(key);
        Ok(())
    }
}

pub fn read(name: &str) -> Option<String> {
    unsafe {
        let key_name = wide(UNINSTALL_KEY);
        let value_name = wide(name);
        let mut size: u32 = 0;
        let flags = RRF_RT_REG_SZ | RRF_SUBKEY_WOW6464KEY;
        if RegGetValueW(HKEY_LOCAL_MACHINE, key_name.as_ptr(), value_name.as_ptr(), flags, null_mut(), null_mut(), &mut size)
            != ERROR_SUCCESS
            || size == 0
        {
            return None;
        }
        let mut buffer = vec![0u16; (size as usize).div_ceil(2)];
        if RegGetValueW(
            HKEY_LOCAL_MACHINE,
            key_name.as_ptr(),
            value_name.as_ptr(),
            flags,
            null_mut(),
            buffer.as_mut_ptr().cast(),
            &mut size,
        ) != ERROR_SUCCESS
        {
            return None;
        }
        let end = buffer.iter().position(|&c| c == 0).unwrap_or(buffer.len());
        let text = String::from_utf16_lossy(&buffer[..end]);
        (!text.is_empty()).then_some(text)
    }
}

pub fn remove() {
    unsafe {
        let key_name = wide(UNINSTALL_KEY);
        RegDeleteKeyExW(HKEY_LOCAL_MACHINE, key_name.as_ptr(), KEY_WOW64_64KEY, 0);
    }
}
