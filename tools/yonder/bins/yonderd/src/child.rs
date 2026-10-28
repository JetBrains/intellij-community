//! Child processes with a deadline: git, bd and launchctl. A watchdog kills a child at its deadline, also while the
//! caller writes to its stdin, so no call waits for ever. A caller can also stop a child early through a [`Stop`].
//!
//! Each child runs in a process group of its own, and a kill reaches the whole group: a `bd lease get` that a cancel
//! stops leaves no clone or git of its own behind.

use std::fmt;
use std::io::{self, Read};
use std::os::fd::RawFd;
use std::os::unix::process::{CommandExt, ExitStatusExt};
use std::process::{Child, ChildStdin, Command, ExitStatus, Stdio};
use std::sync::mpsc::{self, Receiver, RecvTimeoutError, Sender};
use std::sync::{Arc, Mutex, PoisonError};
use std::thread;
use std::time::{Duration, Instant};

/// How often a wait looks at the child and at its stop.
const POLL: Duration = Duration::from_millis(10);

/// How long a wait reads the output of a child that has exited. A grandchild can keep the pipes open.
const DRAIN: Duration = Duration::from_secs(5);

/// Answers `true` when the caller wants the child to stop now.
pub(crate) type Stop<'a> = &'a dyn Fn() -> bool;

/// The stop of a caller that never stops a child early.
pub(crate) const fn never() -> bool {
    false
}

/// The output of a child that exited.
#[derive(Debug)]
pub(crate) struct Output {
    pub(crate) status: ExitStatus,
    pub(crate) stdout: Vec<u8>,
    pub(crate) stderr: Vec<u8>,
}

impl Output {
    pub(crate) fn stdout_text(&self) -> String {
        String::from_utf8_lossy(&self.stdout).trim_end().to_owned()
    }

    /// The stderr, or the stdout when the stderr is empty, for a message.
    pub(crate) fn diagnostic(&self) -> String {
        let stderr = String::from_utf8_lossy(&self.stderr).trim().to_owned();
        if stderr.is_empty() { self.stdout_text() } else { stderr }
    }
}

/// Why a child gave no output.
#[derive(Debug)]
pub(crate) enum ChildError {
    Spawn {
        what: String,
        error: io::Error,
    },
    TimedOut {
        what: String,
        deadline: Duration,
    },
    /// The stop of the caller answered `true`, and the child was killed.
    Stopped {
        what: String,
    },
    Io {
        what: String,
        error: io::Error,
    },
}

impl fmt::Display for ChildError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Spawn { what, error } => write!(f, "cannot start `{what}`: {error}"),
            Self::TimedOut { what, deadline } => write!(f, "`{what}` did not end in {} s and was killed", deadline.as_secs()),
            Self::Stopped { what } => write!(f, "`{what}` was stopped"),
            Self::Io { what, error } => write!(f, "cannot wait for `{what}`: {error}"),
        }
    }
}

/// A child with piped stdout and stderr, which a reader thread each collects, and a watchdog that kills it at its
/// deadline.
#[derive(Debug)]
pub(crate) struct Running {
    child: Arc<Mutex<Child>>,
    stdin: Option<ChildStdin>,
    what: String,
    started: Instant,
    deadline: Duration,
    stdout: Collected,
    stderr: Collected,
    /// The lines of the stderr as they come, when the caller asked for them.
    lines: Option<Receiver<String>>,
    /// Dropped with the `Running`, which ends the watchdog.
    _watchdog: Sender<()>,
}

/// Starts `command` with a piped stdin when `piped_stdin` is set, and with `/dev/null` otherwise.
pub(crate) fn spawn(command: &mut Command, piped_stdin: bool, deadline: Duration) -> Result<Running, ChildError> {
    start(command, piped_stdin, deadline, false)
}

/// Starts `command` as [`spawn`] does, and keeps each line of its stderr for [`Running::wait_with_lines`].
pub(crate) fn spawn_with_lines(command: &mut Command, piped_stdin: bool, deadline: Duration) -> Result<Running, ChildError> {
    start(command, piped_stdin, deadline, true)
}

fn start(command: &mut Command, piped_stdin: bool, deadline: Duration, with_lines: bool) -> Result<Running, ChildError> {
    let what = describe(command);
    command
        .stdin(if piped_stdin { Stdio::piped() } else { Stdio::null() })
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .process_group(0);
    let mut child = command.spawn().map_err(|error| ChildError::Spawn { what: what.clone(), error })?;
    let stdout = collect(child.stdout.take(), None);
    let (lines, sent) = if with_lines {
        let (send, receive) = mpsc::channel();
        (Some(receive), Some(send))
    } else {
        (None, None)
    };
    let stderr = collect(child.stderr.take(), sent);
    let stdin = child.stdin.take();
    let child = Arc::new(Mutex::new(child));
    let (watchdog, ended) = mpsc::channel::<()>();
    let watched = Arc::clone(&child);
    thread::spawn(move || {
        if ended.recv_timeout(deadline) == Err(RecvTimeoutError::Timeout) {
            kill_group(&mut watched.lock().unwrap_or_else(PoisonError::into_inner));
        }
    });
    Ok(Running {
        child,
        stdin,
        what,
        started: Instant::now(),
        deadline,
        stdout,
        stderr,
        lines,
        _watchdog: watchdog,
    })
}

/// Runs `command` with stdin `/dev/null` to its end, or until `stop` answers `true`.
pub(crate) fn run(command: &mut Command, deadline: Duration, stop: Stop<'_>) -> Result<Output, ChildError> {
    spawn(command, false, deadline)?.wait(stop)
}

/// Runs `command` with stdin `/dev/null` to its end, or until `stop` answers `true`. Each line of its stderr goes to
/// `line` on this thread while the child runs.
pub(crate) fn run_with_lines(
    command: &mut Command,
    deadline: Duration,
    stop: Stop<'_>,
    line: &mut dyn FnMut(&str),
) -> Result<Output, ChildError> {
    spawn_with_lines(command, false, deadline)?.wait_with_lines(stop, line)
}

impl Running {
    /// The stdin of a child that [`spawn`] started with a pipe. Dropping it ends the input. A write to it fails once
    /// the watchdog has killed the child.
    pub(crate) const fn take_stdin(&mut self) -> Option<ChildStdin> {
        self.stdin.take()
    }

    /// Waits for the end of the child, up to its deadline, or until `stop` answers `true`. A child past the deadline
    /// or stopped is killed.
    pub(crate) fn wait(self, stop: Stop<'_>) -> Result<Output, ChildError> {
        self.wait_with_lines(stop, &mut |_| {})
    }

    /// Waits as [`Running::wait`] does. Each line of the stderr of a child of [`spawn_with_lines`] goes to `line` on
    /// this thread as it comes.
    pub(crate) fn wait_with_lines(mut self, stop: Stop<'_>, line: &mut dyn FnMut(&str)) -> Result<Output, ChildError> {
        drop(self.stdin.take());
        let lines = self.lines.take();
        let mut pass = |wait: Duration| {
            if let Some(lines) = &lines {
                if let Ok(first) = lines.recv_timeout(wait) {
                    line(&first);
                }
                for next in lines.try_iter() {
                    line(&next);
                }
            }
        };
        let status = loop {
            let mut child = self.child.lock().unwrap_or_else(PoisonError::into_inner);
            match child.try_wait() {
                Ok(Some(status)) => break status,
                Ok(None) => {
                    let failure = if stop() {
                        Some(ChildError::Stopped { what: self.what.clone() })
                    } else if self.started.elapsed() >= self.deadline {
                        Some(self.timed_out())
                    } else {
                        None
                    };
                    if let Some(failure) = failure {
                        kill_group(&mut child);
                        let _ = child.wait();
                        return Err(failure);
                    }
                }
                Err(error) => return Err(ChildError::Io { what: self.what, error }),
            }
            drop(child);
            if lines.is_some() {
                pass(POLL);
            } else {
                thread::sleep(POLL);
            }
        };
        // The watchdog killed a child that a caller fed for too long.
        if status.signal() == Some(libc::SIGKILL) && self.started.elapsed() >= self.deadline {
            return Err(self.timed_out());
        }
        let drained = Instant::now() + DRAIN;
        let stdout = self.stdout.take(drained);
        let stderr = self.stderr.take(drained);
        pass(Duration::ZERO);
        Ok(Output { status, stdout, stderr })
    }

    fn timed_out(&self) -> ChildError {
        ChildError::TimedOut {
            what: self.what.clone(),
            deadline: self.deadline,
        }
    }
}

/// Kills a child that has not been reaped, and each process of its group. A child that has ended keeps its pid as a
/// zombie until it is reaped, so the group id is still its own. std does not signal a child that it has reaped, so the
/// pid of another process is never hit.
fn kill_group(child: &mut Child) {
    if !matches!(child.try_wait(), Ok(None)) {
        return;
    }
    if let Ok(group) = libc::pid_t::try_from(child.id()) {
        // SAFETY: killpg takes plain integers and touches no memory of this process.
        unsafe {
            libc::killpg(group, libc::SIGKILL);
        }
    }
    let _ = child.kill();
}

/// The output of one pipe of a child, which a thread collects as it comes.
#[derive(Debug)]
struct Collected {
    bytes: Arc<Mutex<Vec<u8>>>,
    /// Closed at the end of the pipe.
    ended: Receiver<()>,
}

impl Collected {
    /// The bytes of the pipe once it ends, or the bytes that came up to `deadline`. A grandchild that holds the pipe
    /// open, such as a helper of bd, then costs no more than the wait.
    fn take(&self, deadline: Instant) -> Vec<u8> {
        let _ = self.ended.recv_timeout(deadline.saturating_duration_since(Instant::now()));
        std::mem::take(&mut *self.bytes.lock().unwrap_or_else(PoisonError::into_inner))
    }
}

/// Reads all of `pipe` on a thread, chunk by chunk, into the shared buffer of the answer. With `lines`, each complete
/// line goes there too, without its line end.
fn collect(pipe: Option<impl Read + Send + 'static>, lines: Option<Sender<String>>) -> Collected {
    let (ends, ended) = mpsc::channel();
    let bytes = Arc::new(Mutex::new(Vec::new()));
    if let Some(mut pipe) = pipe {
        let bytes = Arc::clone(&bytes);
        thread::spawn(move || {
            let _ends = ends;
            let mut chunk = vec![0u8; 16 * 1024];
            let mut partial = Vec::new();
            loop {
                match pipe.read(&mut chunk) {
                    Ok(0) => break,
                    Ok(read) => {
                        bytes
                            .lock()
                            .unwrap_or_else(PoisonError::into_inner)
                            .extend_from_slice(&chunk[..read]);
                        if let Some(lines) = &lines {
                            partial.extend_from_slice(&chunk[..read]);
                            while let Some(end) = partial.iter().position(|&byte| byte == b'\n') {
                                let line: Vec<u8> = partial.drain(..=end).collect();
                                let _ = lines.send(String::from_utf8_lossy(&line[..end]).into_owned());
                            }
                        }
                    }
                    Err(error) if error.kind() == io::ErrorKind::Interrupted => {}
                    Err(_) => break,
                }
            }
            if let Some(lines) = &lines
                && !partial.is_empty()
            {
                let _ = lines.send(String::from_utf8_lossy(&partial).into_owned());
            }
        });
    }
    Collected { bytes, ended }
}

/// Makes the child of `command` start in a session of its own, so it outlives this process and each signal to its
/// process group. The child keeps the descriptor `keep` across the exec.
pub(crate) fn detach(command: &mut Command, keep: Option<RawFd>) {
    let prepare = move || {
        // SAFETY: setsid takes no arguments and is async-signal-safe.
        if unsafe { libc::setsid() } == -1 {
            return Err(io::Error::last_os_error());
        }
        if let Some(descriptor) = keep {
            // SAFETY: fcntl takes plain integers and is async-signal-safe. The cleared flag keeps the descriptor open
            // across the exec.
            let cleared = unsafe { libc::fcntl(descriptor, libc::F_SETFD, 0) };
            if cleared == -1 {
                return Err(io::Error::last_os_error());
            }
        }
        Ok(())
    };
    // SAFETY: the closure runs in the child between fork and exec, and calls only setsid and fcntl.
    unsafe {
        command.pre_exec(prepare);
    }
}

/// The program and the arguments of `command`, for a message.
fn describe(command: &Command) -> String {
    let mut text = command.get_program().to_string_lossy().into_owned();
    for argument in command.get_args() {
        text.push(' ');
        text.push_str(&argument.to_string_lossy());
    }
    text
}

#[cfg(test)]
mod tests;
