//! The process table of a macOS host and the memory of one process, from libproc. Other hosts answer
//! [`io::ErrorKind::Unsupported`].

use std::io;

#[cfg(test)]
#[cfg(target_os = "macos")]
mod tests;

/// One process of the host table.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct HostProcess {
    pub pid: i32,
    pub parent_pid: i32,
    /// See [`super::responsible_pid`].
    pub responsible_pid: Option<i32>,
    /// The name of the process, at most 32 bytes, as `proc_name` answers it.
    pub name: String,
}

/// The memory of one process.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct ProcessMemory {
    /// The physical footprint, `ri_phys_footprint` of `proc_pid_rusage`: the dirty memory that the process owns, also
    /// the part in the compressor and in the swap. Activity Monitor shows it as the memory of a process.
    pub footprint_bytes: u64,
    /// The resident size, `ri_resident_size`: the pages in RAM, also the shared pages of the libraries.
    pub resident_bytes: u64,
}

/// Every process of the host that libproc names. A process that ends while the call reads the table is left out.
pub fn host_processes() -> io::Result<Vec<HostProcess>> {
    imp::host_processes()
}

/// The memory of `pid`.
pub fn process_memory(pid: i32) -> io::Result<ProcessMemory> {
    imp::process_memory(pid)
}

/// The path of the executable of `pid`, as `proc_pidpath` answers it.
pub fn process_path(pid: i32) -> io::Result<String> {
    imp::process_path(pid)
}

#[cfg(target_os = "macos")]
mod imp {
    use std::ffi::{CStr, c_int};
    use std::io;
    use std::mem::MaybeUninit;

    use super::{HostProcess, ProcessMemory};
    use crate::proc::responsible_pid;

    /// The room for the pids that start between the count and the list.
    const SPARE_PIDS: usize = 64;

    pub(super) fn host_processes() -> io::Result<Vec<HostProcess>> {
        // SAFETY: a null buffer asks for the count of the pids.
        let count = unsafe { libc::proc_listallpids(std::ptr::null_mut(), 0) };
        let count = usize::try_from(count).map_err(|_negative| io::Error::last_os_error())?;
        let mut pids: Vec<libc::pid_t> = vec![0; count + SPARE_PIDS];
        let size = c_int::try_from(pids.len() * size_of::<libc::pid_t>()).map_err(|_overflow| io::Error::from_raw_os_error(libc::E2BIG))?;
        // SAFETY: the buffer holds `size` bytes of pids.
        let listed = unsafe { libc::proc_listallpids(pids.as_mut_ptr().cast(), size) };
        let listed = usize::try_from(listed).map_err(|_negative| io::Error::last_os_error())?;
        pids.truncate(listed.min(pids.len()));
        Ok(pids.into_iter().filter(|&pid| pid > 0).filter_map(host_process).collect())
    }

    fn host_process(pid: libc::pid_t) -> Option<HostProcess> {
        let mut info = MaybeUninit::<libc::proc_bsdinfo>::zeroed();
        let size = c_int::try_from(size_of::<libc::proc_bsdinfo>()).ok()?;
        // SAFETY: the buffer is one `proc_bsdinfo`, and `size` is its size.
        let read = unsafe { libc::proc_pidinfo(pid, libc::PROC_PIDTBSDINFO, 0, info.as_mut_ptr().cast(), size) };
        if read != size {
            return None;
        }
        // SAFETY: the call filled the whole structure.
        let info = unsafe { info.assume_init() };
        Some(HostProcess {
            pid,
            parent_pid: i32::try_from(info.pbi_ppid).ok()?,
            responsible_pid: responsible_pid(pid),
            name: name(pid).unwrap_or_else(|| text(&info.pbi_comm)),
        })
    }

    /// The name that `proc_name` answers: the long name of the process, else its command.
    fn name(pid: libc::pid_t) -> Option<String> {
        let mut buffer: [libc::c_char; 64] = [0; 64];
        let size = u32::try_from(buffer.len()).ok()?;
        // SAFETY: the buffer holds `size` bytes.
        let length = unsafe { libc::proc_name(pid, buffer.as_mut_ptr().cast(), size) };
        (length > 0).then(|| text(&buffer)).filter(|name| !name.is_empty())
    }

    fn text(characters: &[libc::c_char]) -> String {
        let bytes: Vec<u8> = characters
            .iter()
            .map(|&character| character.cast_unsigned())
            .take_while(|&byte| byte != 0)
            .collect();
        String::from_utf8_lossy(&bytes).into_owned()
    }

    pub(super) fn process_memory(pid: i32) -> io::Result<ProcessMemory> {
        let mut usage = MaybeUninit::<libc::rusage_info_v4>::zeroed();
        // SAFETY: the buffer is one `rusage_info_v4`, the structure of the flavor `RUSAGE_INFO_V4`.
        let status = unsafe { libc::proc_pid_rusage(pid, libc::RUSAGE_INFO_V4, usage.as_mut_ptr().cast()) };
        if status != 0 {
            return Err(io::Error::last_os_error());
        }
        // SAFETY: the call filled the structure.
        let usage = unsafe { usage.assume_init() };
        Ok(ProcessMemory {
            footprint_bytes: usage.ri_phys_footprint,
            resident_bytes: usage.ri_resident_size,
        })
    }

    pub(super) fn process_path(pid: i32) -> io::Result<String> {
        let mut buffer = vec![0_u8; usize::try_from(libc::PROC_PIDPATHINFO_MAXSIZE).unwrap_or(4096)];
        let size = u32::try_from(buffer.len()).map_err(|_overflow| io::Error::from_raw_os_error(libc::E2BIG))?;
        // SAFETY: the buffer holds `size` bytes.
        let length = unsafe { libc::proc_pidpath(pid, buffer.as_mut_ptr().cast(), size) };
        if length <= 0 {
            return Err(io::Error::last_os_error());
        }
        let path = CStr::from_bytes_until_nul(&buffer).map_err(|_no_nul| io::Error::from_raw_os_error(libc::EINVAL))?;
        Ok(path.to_string_lossy().into_owned())
    }
}

#[cfg(not(target_os = "macos"))]
mod imp {
    use std::io;

    use super::{HostProcess, ProcessMemory};

    fn unsupported() -> io::Error {
        io::Error::new(io::ErrorKind::Unsupported, "the process table needs libproc, which only macOS has")
    }

    pub(super) fn host_processes() -> io::Result<Vec<HostProcess>> {
        Err(unsupported())
    }

    pub(super) fn process_memory(_pid: i32) -> io::Result<ProcessMemory> {
        Err(unsupported())
    }

    pub(super) fn process_path(_pid: i32) -> io::Result<String> {
        Err(unsupported())
    }
}
