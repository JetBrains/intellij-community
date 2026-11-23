//! The standard output of the controlled proxy, which a `closeStdout` command closes while the process runs on.

use std::io::{self, Write};

/// The standard output of the process, open until [AgentStdout::close].
pub(crate) struct AgentStdout {
    open: bool,
}

impl AgentStdout {
    pub(crate) const fn new() -> Self {
        Self { open: true }
    }

    /// Writes `bytes` and flushes them. A write after the close fails.
    pub(crate) fn write(&self, bytes: &[u8]) -> io::Result<()> {
        if !self.open {
            return Err(io::Error::new(io::ErrorKind::BrokenPipe, "Cannot write after stdout closed"));
        }
        let mut stdout = io::stdout().lock();
        stdout.write_all(bytes)?;
        stdout.flush()
    }

    /// Closes the standard output, so the IDE reads its end, and answers whether this call closed it. The process
    /// keeps a handle of the null device in its place, so no later file or socket takes the number of the output.
    pub(crate) fn close(&mut self) -> io::Result<bool> {
        if !self.open {
            return Ok(false);
        }
        self.open = false;
        io::stdout().flush()?;
        replace_with_null()?;
        Ok(true)
    }
}

/// Puts the null device in the place of file descriptor 1, which closes the pipe of the output.
#[cfg(unix)]
fn replace_with_null() -> io::Result<()> {
    let null = std::fs::OpenOptions::new().write(true).open("/dev/null")?;
    nix::unistd::dup2_stdout(&null).map_err(io::Error::from)
}

/// Puts the null device in the place of the standard output handle, and closes the handle of the pipe.
#[cfg(windows)]
fn replace_with_null() -> io::Result<()> {
    use std::os::windows::io::IntoRawHandle;

    use windows_sys::Win32::Foundation::CloseHandle;
    use windows_sys::Win32::System::Console::{GetStdHandle, STD_OUTPUT_HANDLE, SetStdHandle};

    let null = std::fs::OpenOptions::new().write(true).open("NUL")?.into_raw_handle();
    // SAFETY: GetStdHandle reads the handle table of this process and takes no pointer.
    let pipe = unsafe { GetStdHandle(STD_OUTPUT_HANDLE) };
    // SAFETY: `null` is an open handle that this process owns from now on, as the standard output.
    if unsafe { SetStdHandle(STD_OUTPUT_HANDLE, null) } == 0 {
        return Err(io::Error::last_os_error());
    }
    // SAFETY: `pipe` was the standard output handle, and no code of this process uses it after the swap above.
    if unsafe { CloseHandle(pipe) } == 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(())
}
