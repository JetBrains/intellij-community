//! The seam of the local child processes: git and ssh. The unit tests answer each call from a script, so they need no
//! git, no ssh and no network.
//!
//! Every call has a limit: a deadline for the whole call, or a quiet limit on its output. A child that runs past its
//! limit, or that the caller stops, is killed with its process group and answers no exit code. The group matters: a
//! grandchild that holds a pipe, such as the `ProxyCommand` of ssh, would keep the read open. A child that exits while
//! a grandchild still holds its pipes gets [`DRAIN`] for the rest of its output, then its group is killed too.
//!
//! Each child runs in a process group of its own, so the Ctrl+C of the terminal does not reach it. The caller stops
//! it through the stop of the call, which the client answers from the count of the presses.

use std::fs::File;
use std::io::{self, Read, Write as _};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, ExitStatus, Stdio};
use std::sync::{Mutex, mpsc};
use std::thread;
use std::time::{Duration, Instant};

/// How often the watch of a child asks whether it ended.
const POLL: Duration = Duration::from_millis(20);

/// How much of the stderr a call keeps: the end, where git and ssh say why they failed.
const KEPT_STDERR: usize = 64 * 1024;

/// How long a call reads the pipes of a child that has exited.
pub const DRAIN: Duration = Duration::from_secs(5);

/// Answers `true` when the caller wants the child to stop now, such as after Ctrl+C.
pub type Stop<'a> = &'a (dyn Fn() -> bool + Sync);

/// The stop of a caller that never stops a child early.
pub const fn never() -> bool {
    false
}

/// One child process.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Call {
    pub program: String,
    pub args: Vec<String>,
    /// The working directory, or the one of this process.
    pub dir: Option<PathBuf>,
    /// The bytes on the stdin of the child. An empty input is a stdin of `/dev/null`.
    pub input: Vec<u8>,
    pub limit: Limit,
    /// Variables that the child gets on top of the environment of this process.
    pub env: Vec<(String, String)>,
}

/// When the watch of a child stops it.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Limit {
    /// The whole call.
    Deadline(Duration),
    /// The time without a byte on the stdout or the stderr, and no limit on the whole call. For a child that writes
    /// progress, such as `git pack-objects --progress`.
    Quiet(Duration),
}

impl Call {
    pub fn new<S: AsRef<str>>(program: &str, args: &[S], deadline: Duration) -> Self {
        Self {
            program: program.to_owned(),
            args: args.iter().map(|arg| arg.as_ref().to_owned()).collect(),
            dir: None,
            input: Vec::new(),
            limit: Limit::Deadline(deadline),
            env: Vec::new(),
        }
    }

    #[must_use]
    pub fn in_dir(mut self, dir: PathBuf) -> Self {
        self.dir = Some(dir);
        self
    }

    #[must_use]
    pub fn with_input(mut self, input: Vec<u8>) -> Self {
        self.input = input;
        self
    }

    #[must_use]
    pub const fn with_limit(mut self, limit: Limit) -> Self {
        self.limit = limit;
        self
    }

    #[must_use]
    pub fn with_env(mut self, name: &str, value: &str) -> Self {
        self.env.push((name.to_owned(), value.to_owned()));
        self
    }

    /// The program and its arguments, joined by spaces, for a message and for the scripts of the tests. The text has no
    /// variables of `env`.
    pub fn text(&self) -> String {
        std::iter::once(self.program.as_str())
            .chain(self.args.iter().map(String::as_str))
            .collect::<Vec<_>>()
            .join(" ")
    }
}

/// Why the watch stopped a child.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Stopped {
    /// The call ran past its deadline, or its output was quiet past its quiet limit.
    Limit,
    /// The caller asked to stop, after Ctrl+C.
    Asked,
}

/// A child that ended.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Ran {
    /// `None` when the child did not start, was stopped, or ended by a signal. `stderr` then says why.
    pub code: Option<i32>,
    pub stdout: Vec<u8>,
    pub stderr: String,
    pub stopped: Option<Stopped>,
}

impl Ran {
    pub const fn ok(&self) -> bool {
        matches!(self.code, Some(0))
    }

    pub fn stdout_text(&self) -> String {
        String::from_utf8_lossy(&self.stdout).into_owned()
    }

    /// Why the child failed, in one line for a message.
    pub fn said(&self) -> String {
        let stderr = self.stderr.trim();
        // A progress line ends with `\r`, so the last part after one is the newest text.
        let last = stderr
            .lines()
            .rev()
            .flat_map(|line| line.rsplit('\r'))
            .find(|line| !line.trim().is_empty())
            .unwrap_or_default()
            .trim();
        match (self.code, last) {
            (_, "") => self.code.map_or_else(|| "no exit code".to_owned(), |code| format!("exit {code}")),
            (Some(code), last) => format!("exit {code}: {last}"),
            (None, last) => last.to_owned(),
        }
    }
}

/// The local child processes of the client.
pub trait Processes: Sync {
    /// Runs a child to its end or its limit, stops it as soon as `stop` answers `true`, and answers its stdout and
    /// stderr.
    fn run(&self, call: &Call, stop: Stop<'_>) -> Ran;

    /// Runs a child with its stdout into the file `stdout`, as [`Processes::run`] does. The answer holds no stdout. A
    /// seam that answers from memory keeps this default, which writes the answered stdout into the file.
    fn run_to_file(&self, call: &Call, stdout: &Path, stop: Stop<'_>) -> Ran {
        let ran = self.run(call, stop);
        if let Err(error) = std::fs::write(stdout, &ran.stdout) {
            return Ran {
                stderr: format!("cannot write {}: {error}", stdout.display()),
                ..Ran::default()
            };
        }
        Ran { stdout: Vec::new(), ..ran }
    }
}

/// The child processes of the operating system.
#[derive(Clone, Copy, Debug, Default)]
pub struct OsProcesses;

impl Processes for OsProcesses {
    fn run(&self, call: &Call, stop: Stop<'_>) -> Ran {
        spawn(call, Stdout::Pipe, stop)
    }

    fn run_to_file(&self, call: &Call, stdout: &Path, stop: Stop<'_>) -> Ran {
        match File::create(stdout) {
            Ok(file) => spawn(call, Stdout::File(file), stop),
            Err(error) => Ran {
                stderr: format!("cannot create {}: {error}", stdout.display()),
                ..Ran::default()
            },
        }
    }
}

enum Stdout {
    Pipe,
    File(File),
}

/// The activity of a child: the time of its last output byte.
struct Activity(Mutex<Instant>);

impl Activity {
    fn touch(&self) {
        *self.0.lock().unwrap_or_else(std::sync::PoisonError::into_inner) = Instant::now();
    }

    fn quiet_for(&self) -> Duration {
        self.0.lock().unwrap_or_else(std::sync::PoisonError::into_inner).elapsed()
    }
}

fn spawn(call: &Call, stdout: Stdout, stop: Stop<'_>) -> Ran {
    let mut command = Command::new(&call.program);
    command
        .args(&call.args)
        .stdin(if call.input.is_empty() { Stdio::null() } else { Stdio::piped() })
        .stderr(Stdio::piped());
    match stdout {
        Stdout::Pipe => command.stdout(Stdio::piped()),
        Stdout::File(file) => command.stdout(file),
    };
    if let Some(dir) = &call.dir {
        command.current_dir(dir);
    }
    command.envs(call.env.iter().map(|(name, value)| (name, value)));
    #[cfg(unix)]
    std::os::unix::process::CommandExt::process_group(&mut command, 0);
    let mut child = match command.spawn() {
        Ok(child) => child,
        Err(error) => {
            return Ran {
                stderr: format!("cannot start {}: {error}", call.program),
                ..Ran::default()
            };
        }
    };
    let stdin = child.stdin.take();
    let child_stdout = child.stdout.take();
    let child_stderr = child.stderr.take();
    let activity = Activity(Mutex::new(Instant::now()));
    thread::scope(|scope| {
        if let Some(mut stdin) = stdin {
            let input = &call.input;
            // A child that ends before it reads all of its input closes the pipe; its exit code tells the rest.
            scope.spawn(move || {
                let _ = stdin.write_all(input);
            });
        }
        let activity = &activity;
        let (read, readers) = mpsc::channel::<()>();
        let errors = {
            let read = read.clone();
            scope.spawn(move || {
                let _read = read;
                read_all(child_stderr, activity, KEPT_STDERR)
            })
        };
        let output = scope.spawn(move || {
            let _read = read;
            read_all(child_stdout, activity, usize::MAX)
        });
        let (status, stopped) = watch(&mut child, call.limit, activity, stop);
        // A grandchild that holds a pipe after the exit gets the drain time; then its group ends, and the pipe with it.
        let drained = Instant::now() + DRAIN;
        let ended = (0..2).all(|_| readers.recv_timeout(drained.saturating_duration_since(Instant::now())).is_ok());
        if !ended {
            stop_group(&mut child);
        }
        let stdout = output.join().unwrap_or_default();
        let mut stderr = String::from_utf8_lossy(&errors.join().unwrap_or_default()).into_owned();
        if let Some(stopped) = stopped {
            if !stderr.is_empty() && !stderr.ends_with('\n') {
                stderr.push('\n');
            }
            stderr.push_str(&match (stopped, call.limit) {
                (Stopped::Asked, _) => format!("{} stopped after Ctrl+C", call.program),
                (Stopped::Limit, Limit::Deadline(limit)) => {
                    format!("{} stopped after {} s, its deadline", call.program, limit.as_secs())
                }
                (Stopped::Limit, Limit::Quiet(limit)) => {
                    format!("{} stopped: no output for {} s", call.program, limit.as_secs())
                }
            });
        }
        Ran {
            code: if stopped.is_some() {
                None
            } else {
                status.and_then(|status| status.code())
            },
            stdout,
            stderr,
            stopped,
        }
    })
}

/// Reads a pipe to its end, notes each chunk as activity, and keeps the last `keep` bytes.
fn read_all(pipe: Option<impl Read>, activity: &Activity, keep: usize) -> Vec<u8> {
    let mut kept = Vec::new();
    let Some(mut pipe) = pipe else {
        return kept;
    };
    let mut chunk = vec![0u8; 16 * 1024];
    loop {
        match pipe.read(&mut chunk) {
            Ok(0) => break,
            Ok(read) => {
                activity.touch();
                kept.extend_from_slice(&chunk[..read]);
                if kept.len() > keep {
                    kept.drain(..kept.len() - keep);
                }
            }
            Err(error) if error.kind() == io::ErrorKind::Interrupted => {}
            Err(_) => break,
        }
    }
    kept
}

/// Waits for a child up to its limit, and kills it with its group there or when `stop` answers `true`. Answers its
/// status and why it was stopped.
fn watch(child: &mut Child, limit: Limit, activity: &Activity, stop: &dyn Fn() -> bool) -> (Option<ExitStatus>, Option<Stopped>) {
    let started = Instant::now();
    loop {
        match child.try_wait() {
            Ok(Some(status)) => return (Some(status), None),
            Ok(None) => {}
            Err(error) if error.kind() == io::ErrorKind::Interrupted => continue,
            Err(_) => return (None, None),
        }
        let reached = match limit {
            Limit::Deadline(deadline) => started.elapsed() >= deadline,
            Limit::Quiet(quiet) => activity.quiet_for() >= quiet,
        };
        let stopped = if stop() {
            Some(Stopped::Asked)
        } else if reached {
            Some(Stopped::Limit)
        } else {
            None
        };
        if let Some(stopped) = stopped {
            stop_group(child);
            let _ = child.wait();
            return (None, Some(stopped));
        }
        thread::sleep(POLL);
    }
}

/// Kills a child and, on Unix, every process of its group.
fn stop_group(child: &mut Child) {
    #[cfg(unix)]
    {
        let _ = Command::new("kill")
            .args(["-KILL", "--", &format!("-{}", child.id())])
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status();
    }
    let _ = child.kill();
}

#[cfg(test)]
mod tests;
