//! The facts of this host that the settings read and that a portable crate cannot probe.
//!
//! `avl-base` links no `libc` and holds no `unsafe` (ADR 0224), so the probe lives here, and the caller of
//! `Config::load` hands the answer in as `avl_base::HostFacts`.

#[cfg(test)]
mod tests;

/// The physical memory of this host in MiB, or `None` when the probe fails.
///
/// macOS answers `hw.memsize` through `sysctlbyname`, Linux answers `MemTotal` of `/proc/meminfo`, and Windows answers
/// `GlobalMemoryStatusEx`.
pub fn memory_mib() -> Option<u64> {
    imp::memory_bytes().map(|bytes| bytes >> 20).filter(|&mib| mib > 0)
}

/// The value of `MemTotal` in a `/proc/meminfo` text, in bytes. The kernel prints it in kB, which is KiB.
#[cfg(any(target_os = "linux", test))]
fn meminfo_total_bytes(meminfo: &str) -> Option<u64> {
    let line = meminfo.lines().find_map(|line| line.strip_prefix("MemTotal:"))?;
    let kib: u64 = line.trim().strip_suffix("kB")?.trim().parse().ok()?;
    kib.checked_mul(1024)
}

#[cfg(target_os = "macos")]
mod imp {
    pub(super) fn memory_bytes() -> Option<u64> {
        let mut bytes: u64 = 0;
        let mut size = size_of::<u64>();
        // SAFETY: the name is a NUL-terminated C string, `bytes` and `size` are valid out pointers, `size` holds the
        // length of `bytes`, and the call passes no new value.
        let status = unsafe {
            libc::sysctlbyname(
                c"hw.memsize".as_ptr(),
                (&raw mut bytes).cast(),
                &raw mut size,
                std::ptr::null_mut(),
                0,
            )
        };
        (status == 0 && size == size_of::<u64>()).then_some(bytes)
    }
}

#[cfg(target_os = "linux")]
mod imp {
    pub(super) fn memory_bytes() -> Option<u64> {
        super::meminfo_total_bytes(&std::fs::read_to_string("/proc/meminfo").ok()?)
    }
}

#[cfg(windows)]
mod imp {
    use windows_sys::Win32::System::SystemInformation::{GlobalMemoryStatusEx, MEMORYSTATUSEX};

    pub(super) fn memory_bytes() -> Option<u64> {
        // SAFETY: `MEMORYSTATUSEX` is plain data, and a zeroed value is valid before the call fills it.
        let mut status: MEMORYSTATUSEX = unsafe { std::mem::zeroed() };
        status.dwLength = u32::try_from(size_of::<MEMORYSTATUSEX>()).ok()?;
        // SAFETY: `status` is a valid out pointer, and its `dwLength` holds the size of the structure.
        let filled = unsafe { GlobalMemoryStatusEx(&raw mut status) };
        (filled != 0).then_some(status.ullTotalPhys)
    }
}

#[cfg(not(any(target_os = "macos", target_os = "linux", windows)))]
mod imp {
    pub(super) const fn memory_bytes() -> Option<u64> {
        None
    }
}
