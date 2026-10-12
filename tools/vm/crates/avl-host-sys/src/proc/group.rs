//! The process group of a child: the one seam between a spawn and the operating system that ends a process tree.
//!
//! A child of the runner starts children of its own, so a stop, a kill or a forwarded signal must reach the whole
//! tree and not only the child. The rest of `proc` and the interrupt service ask a [`ProcessGroup`] and name no
//! operating system.
//!
//! - On Unix the child leads a process group of its own. A stop is SIGTERM to the group, a kill is SIGKILL, and a
//!   forwarded signal is that signal. The group id is the pid of the leader, so the kernel can give it to another
//!   process when the group has no member left. The guard of a spawn disarms before that can happen.
//! - On Windows the child leads a console process group of its own, and a job object holds the child and every
//!   process it starts. A stop and a forwarded signal are CTRL_BREAK to the console process group, and a kill ends
//!   the job. The job closes with the last handle to it and ends every member still in it. A job handle cannot
//!   name another job, so the id reuse of Unix does not occur. The child is assigned to its job just after the
//!   spawn, so a grandchild that the child starts in that short interval is not in the job.

use std::ffi::OsStr;
use std::fs::File;
use std::io;
use std::path::Path;
use std::process::{ExitStatus, Stdio};

use tokio::process::{Child, Command};

use crate::interrupt::Signal;

#[cfg(unix)]
use unix as imp;
#[cfg(windows)]
use windows as imp;

#[cfg(test)]
mod tests;

/// The process group of one child the runner started. A clone is another handle to the same group.
#[derive(Clone, Debug)]
pub(crate) struct ProcessGroup(imp::Group);

impl ProcessGroup {
    /// Makes `command` start its child as the leader of a group of its own.
    pub(crate) fn prepare(command: &mut Command) {
        imp::prepare(command);
    }

    /// The group of a child that [`ProcessGroup::prepare`] made, or `None` when the child has already ended and
    /// there is nothing left to reach. An error is a running child that the group could not take, and the caller
    /// must not keep that child.
    pub(crate) fn attach(child: &Child) -> io::Result<Option<Self>> {
        Ok(imp::Group::attach(child)?.map(Self))
    }

    /// Asks the whole group to stop: SIGTERM on Unix, CTRL_BREAK on Windows.
    pub(crate) fn stop(&self) {
        self.forward(Signal::Terminate);
    }

    /// Forwards a signal the controller received to the whole group. Windows has one event for a process group,
    /// CTRL_BREAK, so both signals are that event there.
    pub(crate) fn forward(&self, signal: Signal) {
        self.0.forward(signal);
    }

    /// Ends every member of the group at once: SIGKILL on Unix, the end of the job on Windows.
    pub(crate) fn kill(&self) {
        self.0.kill();
    }

    /// Ends the group of a spawn that was dropped before it was done. On Unix that is SIGTERM, and the drop of the
    /// child kills the leader. On Windows the job ends, because its handle closes with the spawn in any case.
    pub(crate) fn abandon(&self) {
        self.0.abandon();
    }

    /// Whether the group has no member left. A probe that fails answers `true`, because the caller then stops
    /// reaching the group, and that is the safe side.
    pub(crate) fn is_empty(&self) -> bool {
        self.0.is_empty()
    }
}

/// Starts `command` in a session of its own, detached from the controller, for [`crate::proc::Runner::spawn_session`].
///
/// On Unix the child calls `setsid` before it runs the program, so it leads a new session and a new process group.
/// On Windows the child has no console and leads a console process group of its own, and it breaks away from the
/// job of the controller when that job permits it. A job that forbids the breakaway refuses the spawn with "access
/// denied", and then the child starts in that job.
pub(crate) fn spawn_session(
    program: &OsStr,
    arguments: &[impl AsRef<OsStr>],
    environment: &[(String, String)],
    directory: Option<&Path>,
    stdout: File,
    stderr: File,
) -> io::Result<std::process::Child> {
    let mut command = std::process::Command::new(program);
    command
        .args(arguments)
        .env_clear()
        .envs(environment.iter().map(|(name, value)| (name, value)))
        .stdin(Stdio::null())
        .stdout(stdout)
        .stderr(stderr);
    if let Some(directory) = directory {
        command.current_dir(directory);
    }
    imp::spawn_session(command)
}

/// The exit code of a child as a shell reports it. On Unix a child that a signal ended has no exit code of its own,
/// and -1 would read as a status, so it is 128 plus the signal number. A caller that compares against `$?` of the
/// same command then agrees. A Windows process always has an exit code.
pub(crate) fn exit_code(status: ExitStatus) -> i32 {
    imp::exit_code(status)
}

#[cfg(unix)]
mod unix {
    use std::io;
    use std::os::unix::process::{CommandExt, ExitStatusExt};
    use std::process::ExitStatus;

    use nix::sys::signal::{Signal as NixSignal, killpg};
    use nix::unistd::Pid;
    use tokio::process::{Child, Command};

    use crate::interrupt::Signal;

    #[derive(Clone, Copy, Debug)]
    pub(super) struct Group {
        pgid: i32,
    }

    pub(super) fn prepare(command: &mut Command) {
        command.process_group(0);
    }

    impl Group {
        /// The group is the pid of the child, since the child leads its own. A child that ended so fast that tokio
        /// already reaped it has no id, and nothing left to signal.
        #[expect(clippy::unnecessary_wraps, reason = "the Windows side can fail")]
        pub(super) fn attach(child: &Child) -> io::Result<Option<Self>> {
            Ok(child
                .id()
                .and_then(|pid| i32::try_from(pid).ok())
                .filter(|&pgid| pgid > 0)
                .map(|pgid| Self { pgid }))
        }

        pub(super) fn forward(self, signal: Signal) {
            self.signal(match signal {
                Signal::Interrupt => NixSignal::SIGINT,
                Signal::Terminate => NixSignal::SIGTERM,
            });
        }

        pub(super) fn kill(self) {
            self.signal(NixSignal::SIGKILL);
        }

        pub(super) fn abandon(self) {
            self.signal(NixSignal::SIGTERM);
        }

        /// Signal 0 only probes the group. Any error is an empty group: `ESRCH` is an empty group, and `EPERM` is a
        /// group with a member this process may not signal. No child of the runner changes its credentials, so
        /// such a group is not this group any more.
        pub(super) fn is_empty(self) -> bool {
            killpg(Pid::from_raw(self.pgid), None).is_err()
        }

        /// A group that is already gone is not an error: the signal was meant to end it.
        fn signal(self, signal: NixSignal) {
            let _ = killpg(Pid::from_raw(self.pgid), signal);
        }
    }

    pub(super) fn spawn_session(mut command: std::process::Command) -> io::Result<std::process::Child> {
        // SAFETY: the closure runs between fork and exec, and calls only setsid(2), which is async-signal-safe and
        // touches no memory of the parent.
        unsafe {
            command.pre_exec(|| nix::unistd::setsid().map(drop).map_err(io::Error::from));
        }
        command.spawn()
    }

    pub(super) fn exit_code(status: ExitStatus) -> i32 {
        status.code().or_else(|| status.signal().map(|signal| 128 + signal)).unwrap_or(1)
    }
}

#[cfg(windows)]
mod windows {
    use std::fmt;
    use std::io;
    use std::os::windows::process::CommandExt as _;
    use std::process::ExitStatus;
    use std::sync::Arc;

    use tokio::process::{Child, Command};
    use windows_sys::Win32::Foundation::{CloseHandle, ERROR_ACCESS_DENIED, HANDLE, STILL_ACTIVE};
    use windows_sys::Win32::System::Console::{CTRL_BREAK_EVENT, GenerateConsoleCtrlEvent};
    use windows_sys::Win32::System::JobObjects::{
        AssignProcessToJobObject, CreateJobObjectW, JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE, JOBOBJECT_BASIC_ACCOUNTING_INFORMATION,
        JOBOBJECT_EXTENDED_LIMIT_INFORMATION, JobObjectBasicAccountingInformation, JobObjectExtendedLimitInformation,
        QueryInformationJobObject, SetInformationJobObject, TerminateJobObject,
    };
    use windows_sys::Win32::System::Threading::{
        CREATE_BREAKAWAY_FROM_JOB, CREATE_NEW_PROCESS_GROUP, DETACHED_PROCESS, GetExitCodeProcess,
    };

    use crate::interrupt::Signal;

    /// The exit status of a child whose job ended because of a stop, where no signal ends a process: the status a
    /// shell reports for SIGTERM, so a caller that reads it sees the Unix value.
    const STOPPED_STATUS: u32 = 128 + 15;
    /// The same for a kill, SIGKILL.
    const KILLED_STATUS: u32 = 128 + 9;

    #[derive(Clone, Debug)]
    pub(super) struct Group {
        /// The pid of the child, which is also the id of its console process group.
        leader: u32,
        job: Arc<Job>,
    }

    pub(super) fn prepare(command: &mut Command) {
        command.creation_flags(CREATE_NEW_PROCESS_GROUP);
    }

    impl Group {
        /// A child that the job cannot take and that has ended is a child with nothing left to reach. A child that
        /// the job cannot take and that still runs is an error: no stop, kill or timeout could reach its tree.
        pub(super) fn attach(child: &Child) -> io::Result<Option<Self>> {
            let (Some(leader), Some(process)) = (child.id(), child.raw_handle()) else {
                return Ok(None);
            };
            let process: HANDLE = process.cast();
            let job = Job::new()?;
            // SAFETY: both handles are open: the job is ours, and the child holds its process handle until it is
            // dropped, which the borrow outlives.
            if unsafe { AssignProcessToJobObject(job.0, process) } == 0 {
                let error = io::Error::last_os_error();
                let mut status = 0;
                // SAFETY: as above, and `status` is a valid place for the exit code.
                let ended = unsafe { GetExitCodeProcess(process, &raw mut status) } != 0 && status != STILL_ACTIVE.cast_unsigned();
                return if ended { Ok(None) } else { Err(error) };
            }
            Ok(Some(Self {
                leader,
                job: Arc::new(job),
            }))
        }

        /// CTRL_BREAK to the console process group of the child. The event is sent only while the job has a
        /// member, because Windows sends an event for a group with no process in it to every process of the
        /// console, and so to the controller. A controller without a console cannot send the event at all, and then
        /// the job ends instead, which is a stop that the child cannot refuse.
        pub(super) fn forward(&self, _signal: Signal) {
            if self.is_empty() {
                return;
            }
            // SAFETY: plain values, and the call touches no memory of ours.
            if unsafe { GenerateConsoleCtrlEvent(CTRL_BREAK_EVENT, self.leader) } == 0 {
                self.job.terminate(STOPPED_STATUS);
            }
        }

        pub(super) fn kill(&self) {
            self.job.terminate(KILLED_STATUS);
        }

        pub(super) fn abandon(&self) {
            self.job.terminate(STOPPED_STATUS);
        }

        pub(super) fn is_empty(&self) -> bool {
            let mut accounting = JOBOBJECT_BASIC_ACCOUNTING_INFORMATION::default();
            // SAFETY: the job handle is open, and the buffer is the structure the class names, with its own size.
            let queried = unsafe {
                QueryInformationJobObject(
                    self.job.0,
                    JobObjectBasicAccountingInformation,
                    (&raw mut accounting).cast(),
                    size_of_u32::<JOBOBJECT_BASIC_ACCOUNTING_INFORMATION>(),
                    std::ptr::null_mut(),
                )
            };
            queried == 0 || accounting.ActiveProcesses == 0
        }
    }

    /// An open job object that ends its members when it closes.
    struct Job(HANDLE);

    // SAFETY: a job handle is a kernel object reference. Any thread may use it or close it.
    unsafe impl Send for Job {}
    // SAFETY: as above. Every call through it is a system call, which Windows serializes on the object.
    unsafe impl Sync for Job {}

    impl Job {
        fn new() -> io::Result<Self> {
            // SAFETY: no name and no security attributes, so both pointers may be null.
            let handle = unsafe { CreateJobObjectW(std::ptr::null(), std::ptr::null()) };
            if handle.is_null() {
                return Err(io::Error::last_os_error());
            }
            let job = Self(handle);
            let mut limits = JOBOBJECT_EXTENDED_LIMIT_INFORMATION::default();
            limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
            // SAFETY: the handle is open, and the buffer is the structure the class names, with its own size.
            let set = unsafe {
                SetInformationJobObject(
                    job.0,
                    JobObjectExtendedLimitInformation,
                    (&raw const limits).cast(),
                    size_of_u32::<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>(),
                )
            };
            if set == 0 {
                return Err(io::Error::last_os_error());
            }
            Ok(job)
        }

        /// A job whose members have already ended is not an error: the call was meant to end them.
        fn terminate(&self, status: u32) {
            // SAFETY: the handle is open until this value drops.
            let _ = unsafe { TerminateJobObject(self.0, status) };
        }
    }

    impl Drop for Job {
        fn drop(&mut self) {
            // SAFETY: the handle is open, and nothing uses it after this.
            let _ = unsafe { CloseHandle(self.0) };
        }
    }

    impl fmt::Debug for Job {
        fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
            f.debug_tuple("Job").finish_non_exhaustive()
        }
    }

    fn size_of_u32<T>() -> u32 {
        u32::try_from(size_of::<T>()).expect("a job structure is small")
    }

    pub(super) fn spawn_session(mut command: std::process::Command) -> io::Result<std::process::Child> {
        let detached = DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP;
        command.creation_flags(detached | CREATE_BREAKAWAY_FROM_JOB);
        match command.spawn() {
            Err(error) if error.raw_os_error() == i32::try_from(ERROR_ACCESS_DENIED).ok() => {
                command.creation_flags(detached);
                command.spawn()
            }
            spawned => spawned,
        }
    }

    pub(super) fn exit_code(status: ExitStatus) -> i32 {
        status.code().unwrap_or(1)
    }
}
