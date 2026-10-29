//! The wait of a follow for a change in its run: a write to `events.log`, or a new file in the run directory, such as
//! `exit.json`. On macOS a kqueue watches both, with `EVFILT_VNODE`. Elsewhere, and after any kqueue error, the wait
//! is a poll of [`POLL`].
//!
//! A follow makes its watch before its first read of the run files. A change after the watch exists ends the next
//! wait, also when it came before that wait started.

use std::path::Path;
use std::thread;
use std::time::Duration;

/// The pause of a wait without a kqueue.
pub(crate) const POLL: Duration = Duration::from_millis(200);

/// The watch of one run.
#[derive(Debug)]
pub(crate) struct Watch {
    /// `None` for a poll.
    queue: Option<kqueue::Queue>,
}

impl Watch {
    /// Watches the files `paths`. Without a kqueue, or when a file cannot be opened, the watch polls.
    pub(crate) fn new(paths: &[&Path]) -> Self {
        Self {
            queue: kqueue::Queue::open(paths).ok(),
        }
    }

    /// Whether a change ends a wait at once. `false` means a poll.
    #[cfg(test)]
    pub(crate) const fn is_pushed(&self) -> bool {
        self.queue.is_some()
    }

    /// Waits for a change, up to `timeout`. A poll waits up to [`POLL`]. A kqueue error turns this watch into a poll.
    pub(crate) fn wait(&mut self, timeout: Duration) {
        if let Some(queue) = &self.queue {
            if queue.wait(timeout).is_ok() {
                return;
            }
            self.queue = None;
        }
        thread::sleep(timeout.min(POLL));
    }
}

/// No kqueue outside macOS: each open fails, so each watch polls.
#[cfg(not(target_os = "macos"))]
mod kqueue {
    use std::io;
    use std::path::Path;
    use std::time::Duration;

    #[derive(Debug)]
    pub(super) enum Queue {}

    impl Queue {
        pub(super) fn open(_paths: &[&Path]) -> io::Result<Self> {
            Err(io::Error::from(io::ErrorKind::Unsupported))
        }

        pub(super) const fn wait(&self, _timeout: Duration) -> io::Result<()> {
            match *self {}
        }
    }
}

#[cfg(target_os = "macos")]
mod kqueue {
    use std::ffi::CString;
    use std::io;
    use std::os::fd::{AsRawFd, FromRawFd, OwnedFd};
    use std::os::unix::ffi::OsStrExt;
    use std::path::Path;
    use std::time::Duration;

    /// The changes of a file that end a wait: a write, a growth, and a change of its metadata.
    const CHANGES: u32 = libc::NOTE_WRITE | libc::NOTE_EXTEND | libc::NOTE_ATTRIB;

    /// A kqueue with a vnode filter on each watched file.
    #[derive(Debug)]
    pub(super) struct Queue {
        queue: OwnedFd,
        /// The watched files. The filter of each one lives while its descriptor is open.
        _files: Vec<OwnedFd>,
    }

    impl Queue {
        pub(super) fn open(paths: &[&Path]) -> io::Result<Self> {
            // SAFETY: kqueue takes no arguments.
            let queue = unsafe { libc::kqueue() };
            if queue == -1 {
                return Err(io::Error::last_os_error());
            }
            // SAFETY: kqueue answered a new descriptor that nothing else owns.
            let queue = unsafe { OwnedFd::from_raw_fd(queue) };
            let mut files = Vec::new();
            let mut changes = Vec::new();
            for path in paths {
                let path = CString::new(path.as_os_str().as_bytes()).map_err(io::Error::other)?;
                // SAFETY: `path` is a NUL-terminated string that lives for the call. O_EVTONLY opens for events only.
                let file = unsafe { libc::open(path.as_ptr(), libc::O_EVTONLY | libc::O_CLOEXEC) };
                if file == -1 {
                    return Err(io::Error::last_os_error());
                }
                // SAFETY: open answered a new descriptor that nothing else owns.
                let file = unsafe { OwnedFd::from_raw_fd(file) };
                changes.push(libc::kevent {
                    ident: usize::try_from(file.as_raw_fd()).map_err(io::Error::other)?,
                    filter: libc::EVFILT_VNODE,
                    flags: libc::EV_ADD | libc::EV_CLEAR,
                    fflags: CHANGES,
                    data: 0,
                    udata: std::ptr::null_mut(),
                });
                files.push(file);
            }
            let count = libc::c_int::try_from(changes.len()).map_err(io::Error::other)?;
            // SAFETY: `changes` holds `count` events and lives for the call; the call reads no events back.
            let added = unsafe {
                libc::kevent(
                    queue.as_raw_fd(),
                    changes.as_ptr(),
                    count,
                    std::ptr::null_mut(),
                    0,
                    std::ptr::null(),
                )
            };
            if added == -1 {
                return Err(io::Error::last_os_error());
            }
            Ok(Self { queue, _files: files })
        }

        /// Waits for a change of a watched file, up to `timeout`. An interrupt by a signal counts as a change.
        pub(super) fn wait(&self, timeout: Duration) -> io::Result<()> {
            let timeout = libc::timespec {
                tv_sec: libc::time_t::try_from(timeout.as_secs()).unwrap_or(libc::time_t::MAX),
                tv_nsec: libc::c_long::from(timeout.subsec_nanos()),
            };
            // SAFETY: an all-zero kevent is a valid value of the plain C struct.
            let mut events: [libc::kevent; 4] = unsafe { std::mem::zeroed() };
            let capacity = libc::c_int::try_from(events.len()).map_err(io::Error::other)?;
            // SAFETY: `events` has room for `capacity` events and `timeout` lives for the call.
            let ready = unsafe {
                libc::kevent(
                    self.queue.as_raw_fd(),
                    std::ptr::null(),
                    0,
                    events.as_mut_ptr(),
                    capacity,
                    &raw const timeout,
                )
            };
            if ready == -1 {
                let error = io::Error::last_os_error();
                if error.kind() != io::ErrorKind::Interrupted {
                    return Err(error);
                }
            }
            Ok(())
        }
    }
}

#[cfg(test)]
mod tests;
