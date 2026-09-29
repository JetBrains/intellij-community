//! [`Runtime`] over the real operating system, and the only production implementation of that seam.
//!
//! Three behaviours differ between the consumers, and those are injected rather than branched on: the spawn,
//! because the lane controller resolves a selector from inside a held lease and must not start a subprocess while
//! `bt`'s whole job is to run one; and the two output sinks, because the controller routes them into its own
//! worker-tagged reporter while `bt` writes to the process's descriptors.
//!
//! Portable on purpose, with no unix-only call anywhere below, because `bt.cmd` runs on Windows.

use std::ffi::OsString;
use std::fs::File;
use std::io::{self, BufRead, BufReader, Read, Write};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::sync::mpsc;
use std::thread;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use crate::runtime::{DirEntry, Heartbeat, Platform, Runtime, SpawnResult};

/// How much of a child's output is kept.
///
/// Bazel writes stdout and stderr nobody ever shows unless something went wrong, so only the tail is worth holding:
/// it is what a `--verbose` run echoes and what an INFRA digest quotes.
pub const MAX_RETAINED_OUTPUT: usize = 256 * 1024;

/// A duration in whole milliseconds, saturating.
fn millis(duration: Duration) -> u64 {
    u64::try_from(duration.as_millis()).unwrap_or(u64::MAX)
}

/// How often a running child reports that it is still running.
const HEARTBEAT_INTERVAL: Duration = Duration::from_secs(30);

/// What spawning means for a consumer.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub enum Spawner {
    /// Answers exit 127 with the argv it was asked to run, and starts nothing; see [`refuse_spawn`]. The default,
    /// and the restrictive choice on purpose: a consumer that forgot to say what spawning means for it gets a
    /// diagnosable refusal rather than a bazel run it did not ask for.
    #[default]
    Refuse,
    /// Runs the child in the process's own working directory.
    Child,
    /// Runs the child in this directory. `bt` wants the checkout: bazel resolves a target pattern against the
    /// workspace it was started in.
    ChildIn(PathBuf),
}

/// An output sink. It is handed text without a trailing newline.
pub type Sink = Box<dyn Fn(&str) + Send + Sync>;

/// The `Runtime` over the operating system.
pub struct OsRuntime {
    repo_root: PathBuf,
    spawner: Spawner,
    write: Sink,
    write_error: Sink,
}

/// Builds an [`OsRuntime`]. Everything a consumer does not set is the operating system's.
pub struct OsRuntimeBuilder {
    repo_root: PathBuf,
    spawner: Spawner,
    write: Option<Sink>,
    write_error: Option<Sink>,
}

impl OsRuntime {
    /// `repo_root` is the checkout root, answered verbatim. Never derived from the process working directory: under
    /// the runfiles tree of a `bazel run` launcher the two are different directories, and every path `bt` builds is
    /// repo-relative.
    pub fn builder(repo_root: impl Into<PathBuf>) -> OsRuntimeBuilder {
        OsRuntimeBuilder {
            repo_root: repo_root.into(),
            spawner: Spawner::Refuse,
            write: None,
            write_error: None,
        }
    }

    /// The runtime a caller resolves selectors and suites through without running anything: the controller and the
    /// trace planner.
    ///
    /// It cannot spawn ([`Spawner::Refuse`]), because such a caller resolves from the checkout alone, and a
    /// resolution that reached for bazel would start it from inside a held lease or a server request. Its digest
    /// goes nowhere, since a runtime that refuses to spawn bazel has no test run to digest, and its diagnostics go
    /// to `notes`, the caller's own reporter rather than the process's stderr.
    pub fn resolver(
        repo_root: impl Into<PathBuf>,
        notes: impl Fn(&str) + Send + Sync + 'static,
    ) -> Self {
        Self::builder(repo_root)
            .spawn(Spawner::Refuse)
            .write(|_| {})
            .write_error(notes)
            .build()
    }
}

impl OsRuntimeBuilder {
    #[must_use]
    pub fn spawn(mut self, spawner: Spawner) -> Self {
        self.spawner = spawner;
        self
    }

    /// The digest's destination. The default is the process's stdout, one line per call.
    #[must_use]
    pub fn write(mut self, sink: impl Fn(&str) + Send + Sync + 'static) -> Self {
        self.write = Some(Box::new(sink));
        self
    }

    /// Progress and diagnostics. The default is the process's stderr, one line per call. The split is the whole
    /// reason `--json` stdout stays machine-clean.
    #[must_use]
    pub fn write_error(mut self, sink: impl Fn(&str) + Send + Sync + 'static) -> Self {
        self.write_error = Some(Box::new(sink));
        self
    }

    pub fn build(self) -> OsRuntime {
        OsRuntime {
            repo_root: self.repo_root,
            spawner: self.spawner,
            // A handle per call rather than a held lock: the heartbeat writes from its own thread while the run is
            // in progress.
            write: self
                .write
                .unwrap_or_else(|| Box::new(|text| write_line(&mut io::stdout(), text))),
            write_error: self
                .write_error
                .unwrap_or_else(|| Box::new(|text| write_line(&mut io::stderr(), text))),
        }
    }
}

/// Writes one line, appending the newline the runtime's callers leave to the sink.
///
/// A failed write to stdout leaves nowhere to report it, the descriptor a report would go to being the one that just
/// failed, so it is dropped rather than crashed over.
pub(crate) fn write_line(out: &mut dyn Write, text: &str) {
    let _ = writeln!(out, "{text}");
}

/// The controller's spawn: exit 127 with the argv it was asked to run.
///
/// A selector is resolved from the checkout alone, and a resolution that reached for bazel would start it from
/// inside a held lease. [`Runtime::spawn`] cannot fail by contract, so the refusal arrives as the 127 the classifier
/// reads as infrastructure. A panic would be worse: the controller holds a lease and a worker lock.
pub fn refuse_spawn(command: &[OsString]) -> SpawnResult {
    SpawnResult {
        exit_code: 127,
        output: format!(
            "selector resolution must not spawn subprocesses: {}",
            joined(command)
        ),
    }
}

fn joined(command: &[OsString]) -> String {
    command
        .iter()
        .map(|part| part.to_string_lossy())
        .collect::<Vec<_>>()
        .join(" ")
}

/// Runs a child to completion, in `dir` when one is given.
///
/// Never fails: a command that could not start is exit 127, a run outcome the classifier can reason about rather
/// than an error every caller would have to re-classify. A child killed by a signal has no code and answers -1,
/// which classifies as infrastructure too.
pub fn spawn_child(
    dir: Option<&Path>,
    command: &[OsString],
    heartbeat: Option<Heartbeat<'_>>,
) -> SpawnResult {
    match command_for(dir, command) {
        Some(child) => run_to_end(child, HEARTBEAT_INTERVAL, heartbeat),
        None => SpawnResult {
            exit_code: 127,
            output: "nothing to spawn: the command line is empty".to_owned(),
        },
    }
}

fn command_for(dir: Option<&Path>, command: &[OsString]) -> Option<Command> {
    let (program, args) = command.split_first()?;
    let mut child = Command::new(program);
    child.args(args).stdin(Stdio::null());
    if let Some(dir) = dir {
        child.current_dir(dir);
    }
    Some(child)
}

fn could_not_start(child: &Command, error: &io::Error) -> SpawnResult {
    SpawnResult {
        exit_code: 127,
        output: format!("{}: {error}", child.get_program().to_string_lossy()),
    }
}

/// Runs a prepared command to completion, beating every `interval` while it runs.
///
/// One pipe for both descriptors, so the tail keeps the order the child wrote in: bazel puts its progress on stderr
/// and its results on stdout, and a tail that has lost which came first is the one a human reads when a run went
/// wrong.
fn run_to_end(
    mut child: Command,
    interval: Duration,
    heartbeat: Option<Heartbeat<'_>>,
) -> SpawnResult {
    let pipe = io::pipe().and_then(|(reader, writer)| Ok((reader, writer.try_clone()?, writer)));
    let (mut reader, stdout, stderr) = match pipe {
        Ok(pipe) => pipe,
        Err(error) => return could_not_start(&child, &error),
    };
    child.stdout(stdout).stderr(stderr);
    let spawned = child.spawn();
    // The command holds the pipe's write ends; until it is gone the read below never sees the end of the stream.
    let program = child.get_program().to_owned();
    drop(child);
    let mut process = match spawned {
        Ok(process) => process,
        Err(error) => {
            return SpawnResult {
                exit_code: 127,
                output: format!("{}: {error}", program.to_string_lossy()),
            };
        }
    };

    let started = Instant::now();
    let (stop, stopped) = mpsc::channel::<()>();
    thread::scope(|scope| {
        // Ordering, not politeness: the callback writes a progress line through a sink the caller owns, and one
        // arriving after the run was reported would interleave with the digest. The scope joins it before `spawn`
        // answers.
        if let Some(beat) = heartbeat {
            scope.spawn(move || {
                while stopped.recv_timeout(interval) == Err(mpsc::RecvTimeoutError::Timeout) {
                    beat(millis(started.elapsed()));
                }
            });
        }
        let mut tail = Tail::new(MAX_RETAINED_OUTPUT);
        let mut chunk = vec![0u8; 64 * 1024];
        loop {
            match reader.read(&mut chunk) {
                Ok(0) => break,
                Ok(read) => tail.push(&chunk[..read]),
                Err(error) if error.kind() == io::ErrorKind::Interrupted => {}
                // The child keeps running, and waiting for it is still the right answer; what it said is what
                // arrived before the pipe broke.
                Err(_) => break,
            }
        }
        let output = tail.text();
        let waited = process.wait();
        drop(stop);
        match waited {
            Ok(status) => SpawnResult {
                exit_code: status.code().unwrap_or(-1),
                output,
            },
            // The child started but could not be reaped, so there is no exit status to report. Reading it as green
            // would report a run nobody observed the end of.
            Err(error) => SpawnResult {
                exit_code: 127,
                output: if output.is_empty() {
                    error.to_string()
                } else {
                    format!("{output}\n{error}")
                },
            },
        }
    })
}

/// The last `limit` bytes of a stream.
struct Tail {
    limit: usize,
    bytes: Vec<u8>,
    /// A prefix is gone, which makes the leading bytes of the tail suspect.
    dropped: bool,
}

impl Tail {
    const fn new(limit: usize) -> Self {
        Self {
            limit,
            bytes: Vec::new(),
            dropped: false,
        }
    }

    fn push(&mut self, chunk: &[u8]) {
        self.bytes.extend_from_slice(chunk);
        // Trimmed at twice the limit rather than at every write, so a run that produces megabytes copies a bounded
        // number of times instead of once per chunk.
        if self.bytes.len() > 2 * self.limit {
            let cut = self.bytes.len() - self.limit;
            self.bytes.drain(..cut);
            self.dropped = true;
        }
    }

    /// The retained tail, cut to the limit and never starting mid-character: the output travels inside a JSON
    /// envelope an agent reads, and a cut that split a multi-byte character would put a replacement character at
    /// the head of it.
    fn text(&self) -> String {
        let mut tail = self.bytes.as_slice();
        let dropped = if tail.len() > self.limit {
            tail = &tail[tail.len() - self.limit..];
            true
        } else {
            self.dropped
        };
        if dropped {
            let start = tail
                .iter()
                .position(|byte| byte & 0xc0 != 0x80)
                .unwrap_or(tail.len());
            tail = &tail[start..];
        }
        String::from_utf8_lossy(tail).into_owned()
    }
}

/// The lines of a file, streaming: the file it exists for is bazel's BEP, which is tens of MB on a lane run and holds
/// single JSON lines of any length.
///
/// The final line is yielded whether or not it is empty, which is what splitting on `\n` does: a file ending in a
/// newline has an empty last line, and a file bazel was killed mid-write has a truncated one. A `\r` is stripped
/// only before a newline, where it was a line ending.
struct Lines {
    reader: Option<BufReader<File>>,
}

impl Iterator for Lines {
    type Item = String;

    fn next(&mut self) -> Option<String> {
        let reader = self.reader.as_mut()?;
        let mut line = Vec::new();
        match reader.read_until(b'\n', &mut line) {
            Ok(_) if line.last() == Some(&b'\n') => {
                line.pop();
                if line.last() == Some(&b'\r') {
                    line.pop();
                }
            }
            // End of file, or a read that failed partway: either way what was read is the last line there is.
            _ => self.reader = None,
        }
        Some(String::from_utf8_lossy(&line).into_owned())
    }
}

impl Runtime for OsRuntime {
    fn repo_root(&self) -> &Path {
        &self.repo_root
    }

    fn platform(&self) -> Platform {
        Platform::current()
    }

    fn read_text_file(&self, path: &Path) -> io::Result<String> {
        std::fs::read_to_string(path)
    }

    /// Nothing for a file that is not there, which is the runtime's contract rather than a swallowed failure: bazel
    /// never wrote that file when it died during option parsing.
    fn read_lines(&self, path: &Path) -> Box<dyn Iterator<Item = String> + '_> {
        Box::new(Lines {
            reader: File::open(path).ok().map(BufReader::new),
        })
    }

    fn read_dir(&self, path: &Path) -> io::Result<Vec<DirEntry>> {
        std::fs::read_dir(path)?
            .map(|entry| {
                let entry = entry?;
                Ok(DirEntry {
                    name: entry.file_name().to_string_lossy().into_owned(),
                    is_dir: entry.file_type()?.is_dir(),
                })
            })
            .collect()
    }

    /// Asks git, because git alone knows which files under a directory the checkout ignores.
    ///
    /// Not through the injected spawn. That spawn refuses for the lane controller, which must not start bazel from
    /// inside a held lease. One `git ls-files` over a directory the caller named is a read of the checkout that
    /// takes milliseconds, the same kind of call as a directory listing. `-z` keeps a name with a space or a quote
    /// verbatim, and an unmerged file that the index holds three times is listed once.
    fn list_files(&self, dir: &Path) -> io::Result<Vec<PathBuf>> {
        let listed = Command::new("git")
            .arg("-C")
            .arg(dir)
            .args([
                "ls-files",
                "-z",
                "--cached",
                "--others",
                "--exclude-standard",
            ])
            .stdin(Stdio::null())
            .output();
        let refused = |said: String| {
            io::Error::other(format!(
                "git cannot list the files under {}: {said}",
                dir.display()
            ))
        };
        let listed = listed.map_err(|error| refused(error.to_string()))?;
        if !listed.status.success() {
            let stderr = String::from_utf8_lossy(&listed.stderr).trim().to_owned();
            return Err(refused(if stderr.is_empty() {
                listed.status.to_string()
            } else {
                stderr
            }));
        }
        let stdout = String::from_utf8_lossy(&listed.stdout);
        let mut names: Vec<&str> = stdout.split('\0').filter(|name| !name.is_empty()).collect();
        names.sort_unstable();
        names.dedup();
        let mut files: Vec<PathBuf> = names
            .into_iter()
            .map(|name| {
                let name = if cfg!(windows) {
                    name.replace('/', "\\")
                } else {
                    name.to_owned()
                };
                dir.join(name)
            })
            // A tracked file deleted from the disk is still in the index, and a nested checkout is listed as its
            // directory. Neither is a file a caller can have edited.
            .filter(|path| path.is_file())
            .collect();
        files.sort();
        Ok(files)
    }

    fn exists(&self, path: &Path) -> bool {
        path.exists()
    }

    fn spawn(&self, command: &[OsString], heartbeat: Option<Heartbeat<'_>>) -> SpawnResult {
        match &self.spawner {
            Spawner::Refuse => refuse_spawn(command),
            Spawner::Child => spawn_child(None, command, heartbeat),
            Spawner::ChildIn(dir) => spawn_child(Some(dir), command, heartbeat),
        }
    }

    /// A fresh file in the system temporary directory, created so no second process can pick the same name.
    fn temp_file(&self, prefix: &str) -> PathBuf {
        match tempfile::Builder::new()
            .prefix(&format!("{prefix}-"))
            .suffix(".json")
            .tempfile()
        {
            Ok(mut file) => {
                // The file outlives the handle: the caller removes it, and bazel writes it meanwhile.
                file.disable_cleanup(true);
                file.path().to_path_buf()
            }
            // Only reachable when the temporary directory is unwritable, and a temporary name is not worth refusing
            // an invocation over; bazel then reports the path it cannot write, which is the real problem.
            Err(_) => std::env::temp_dir().join(format!(
                "{prefix}-{}-{}.json",
                std::process::id(),
                self.now_ms()
            )),
        }
    }

    /// Deletes one file and is content for it to be absent already.
    fn remove(&self, path: &Path) -> io::Result<()> {
        match std::fs::remove_file(path) {
            Err(error) if error.kind() != io::ErrorKind::NotFound => Err(error),
            _ => Ok(()),
        }
    }

    fn now_ms(&self) -> u64 {
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_or(0, millis)
    }

    fn write(&self, text: &str) {
        (self.write)(text);
    }

    fn write_error(&self, text: &str) {
        (self.write_error)(text);
    }
}

#[cfg(test)]
mod tests;
