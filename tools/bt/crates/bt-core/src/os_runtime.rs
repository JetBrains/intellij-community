//! [`Runtime`] over the real operating system, and the only production implementation of that seam.
//!
//! Three behaviours differ between the consumers, and those are injected rather than branched on: the spawn,
//! because the lane controller resolves a selector from inside a held lease and must not start bazel while `bt`'s
//! whole job is to run one; and the two output sinks, because the controller routes them into its own
//! worker-tagged reporter while `bt` writes to the process's descriptors. The pinned accio is no injected spawn:
//! the resolution of each consumer runs it ([`Runtime::accio`]).
//!
//! Portable on purpose, with no unix-only call anywhere below, because `bt.cmd` runs on Windows.

use std::ffi::{OsStr, OsString};
use std::fs::File;
use std::io::{self, BufRead, BufReader, Read, Write};
use std::path::{Path, PathBuf};
use std::process::{Command, ExitStatus, Stdio};
use std::sync::mpsc;
use std::thread;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use refusal::Refusal;

use crate::accio::accio_failed;
use crate::runtime::{CheckoutTree, DirEntry, Heartbeat, Platform, Runtime, SpawnResult};

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
    host_slot: bool,
    self_build_ms: Option<u64>,
    write: Sink,
    write_error: Sink,
}

/// Builds an [`OsRuntime`]. Everything a consumer does not set is the operating system's.
pub struct OsRuntimeBuilder {
    repo_root: PathBuf,
    spawner: Spawner,
    host_slot: bool,
    self_build_ms: Option<u64>,
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
            host_slot: false,
            self_build_ms: None,
            write: None,
            write_error: None,
        }
    }

    /// The runtime a caller resolves selectors and suites through without running anything: the controller and the
    /// trace planner.
    ///
    /// It spawns nothing but the pinned accio ([`Spawner::Refuse`]), because such a caller resolves from the checkout
    /// and its accio index, and a resolution that reached for bazel would start it from inside a held lease or a
    /// server request. Its digest
    /// goes nowhere, since a runtime that refuses to spawn bazel has no test run to digest, and its diagnostics go
    /// to `notes`, the caller's own reporter rather than the process's stderr.
    pub fn resolver(repo_root: impl Into<PathBuf>, notes: impl Fn(&str) + Send + Sync + 'static) -> Self {
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

    /// Whether the checkout is a host slot of `brd-host`. A host slot is a linked work tree too, but no `brd lease pick`
    /// lands it: the `bt --host` client records the tree of the commit that the host ran. So the runtime of a host slot
    /// answers no tree, and the run skips the two scans of the untracked files that a tree costs. The default is
    /// `false`.
    #[must_use]
    pub const fn host_slot(mut self, host_slot: bool) -> Self {
        self.host_slot = host_slot;
        self
    }

    /// How long the wrapper built this binary, see [`Runtime::self_build_ms`]. The default is `None`.
    #[must_use]
    pub const fn self_build_ms(mut self, self_build_ms: Option<u64>) -> Self {
        self.self_build_ms = self_build_ms;
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
            host_slot: self.host_slot,
            self_build_ms: self.self_build_ms,
            // A handle per call rather than a held lock: the heartbeat writes from its own thread while the run is
            // in progress.
            write: self.write.unwrap_or_else(|| Box::new(|text| write_line(&mut io::stdout(), text))),
            write_error: self
                .write_error
                .unwrap_or_else(|| Box::new(|text| write_line(&mut io::stderr(), text))),
        }
    }
}

/// The variables that tie a git process to one repository, as `git rev-parse --local-env-vars` lists them.
///
/// A parent git exports some of them to its children, such as `git rebase --exec` for each step. A child git that
/// inherits them works on that repository and not on the one of its `-C`.
pub const GIT_LOCAL_ENV_VARS: [&str; 15] = [
    "GIT_ALTERNATE_OBJECT_DIRECTORIES",
    "GIT_CONFIG",
    "GIT_CONFIG_PARAMETERS",
    "GIT_CONFIG_COUNT",
    "GIT_OBJECT_DIRECTORY",
    "GIT_DIR",
    "GIT_WORK_TREE",
    "GIT_IMPLICIT_WORK_TREE",
    "GIT_GRAFT_FILE",
    "GIT_INDEX_FILE",
    "GIT_NO_REPLACE_OBJECTS",
    "GIT_REPLACE_REF_BASE",
    "GIT_PREFIX",
    "GIT_SHALLOW_FILE",
    "GIT_COMMON_DIR",
];

/// A command of `program` without the inherited variables of [`GIT_LOCAL_ENV_VARS`].
///
/// Every child of bt starts from it: git, accio, bazel, `tests.cmd` and the shell of `brd-host`. A test JVM or a
/// `cargo test` under such a child runs git too, and with an inherited `GIT_DIR` that git writes the repository of
/// the parent git and not the one of its working directory.
pub fn isolated_command(program: impl AsRef<OsStr>) -> Command {
    let mut command = Command::new(program);
    for name in GIT_LOCAL_ENV_VARS {
        command.env_remove(name);
    }
    command
}

/// A `git` command without the inherited variables of [`GIT_LOCAL_ENV_VARS`], so its `-C` alone names its
/// repository. Every git spawn of this crate starts from it, the tests included.
pub fn git_command() -> Command {
    isolated_command("git")
}

impl OsRuntime {
    /// The stdout of one git command in the checkout, or an error with what git said.
    ///
    /// Not through the injected spawn, for the reason of [`Runtime::list_files`]: a read of the checkout is not a
    /// subprocess that the controller must keep out of a held lease.
    fn git(&self, args: &[&str]) -> io::Result<String> {
        let ran = git_command()
            .arg("-C")
            .arg(&self.repo_root)
            .args(args)
            .stdin(Stdio::null())
            .output();
        let refused = |said: String| io::Error::other(format!("git {} failed in {}: {said}", args.join(" "), self.repo_root.display()));
        let ran = ran.map_err(|error| refused(error.to_string()))?;
        if !ran.status.success() {
            let stderr = String::from_utf8_lossy(&ran.stderr).trim().to_owned();
            return Err(refused(if stderr.is_empty() { ran.status.to_string() } else { stderr }));
        }
        Ok(String::from_utf8_lossy(&ran.stdout).into_owned())
    }
}

/// Writes one line, appending the newline the runtime's callers leave to the sink.
///
/// A failed write to stdout leaves nowhere to report it, the descriptor a report would go to being the one that just
/// failed, so it is dropped rather than crashed over.
pub(crate) fn write_line(out: &mut dyn Write, text: &str) {
    let _ = writeln!(out, "{text}");
}

/// The accio wrappers that [`Runtime::accio`] looks for, relative to the checkout root: the one of an ultimate checkout,
/// then the one of a community checkout.
pub const ACCIO_WRAPPERS: [&str; 2] = ["community/tools/accio.cmd", "tools/accio.cmd"];

/// The command line of one accio call through the wrapper `wrapper`, a path relative to the checkout root, as
/// [`crate::lanes::checkout_script_command`] runs it. The project and the format come after `args`.
pub fn accio_command(repo_root: &Path, platform: Platform, wrapper: &str, args: &[&str]) -> Vec<OsString> {
    let mut command = crate::lanes::checkout_script_command(repo_root, platform, wrapper);
    command.extend(args.iter().map(OsString::from));
    command.extend([OsString::from("--project"), repo_root.as_os_str().to_owned()]);
    command.extend(["--format", "json"].map(OsString::from));
    command
}

/// The seconds that one accio call of bt may wait for the index of the checkout, as `ACCIO_WAIT`.
///
/// accio builds a missing index in the background and waits for it up to 90 s by default. That default fits the
/// shell timeout of a coding agent. bt has no such timeout, and the run after the call takes minutes anyway. A host
/// slot or a lease without an index under a high load needs more than 90 s, and past the budget accio exits 1.
pub const ACCIO_WAIT_SECS: u32 = 1800;

/// Runs one accio command line in the checkout root, and answers its stdout.
///
/// A command that cannot start, and a command that exits with another code than 0, is a refusal. The refusal names
/// the tail of the stderr and the tail of the stdout of the command, because accio writes its error to stdout. accio
/// builds or refreshes its index by itself and waits for it up to [`ACCIO_WAIT_SECS`], so the call has no retry.
fn run_accio(repo_root: &Path, command: &[OsString]) -> Result<String, Refusal> {
    let Some((program, args)) = command.split_first() else {
        return Err(accio_failed("nothing to run: the accio command line is empty"));
    };
    let ran = isolated_command(program)
        .args(args)
        .env("ACCIO_WAIT", ACCIO_WAIT_SECS.to_string())
        .current_dir(repo_root)
        .stdin(Stdio::null())
        .output()
        .map_err(|error| accio_failed(format!("cannot start {}: {error}", command_line(command))))?;
    if !ran.status.success() {
        let code = ran.status.code().map_or_else(|| "a signal".to_owned(), |code| code.to_string());
        return Err(accio_failed(format!(
            "{} exited with {code}{}",
            command_line(command),
            failed_output(&ran.stdout, &ran.stderr)
        )));
    }
    Ok(String::from_utf8_lossy(&ran.stdout).into_owned())
}

/// The output of a failed accio call for its refusal: the last lines of the stderr, then the last lines of the stdout.
/// An empty stream has no section. A call without output says so, so the refusal is never an empty text.
fn failed_output(stdout: &[u8], stderr: &[u8]) -> String {
    const TAIL_LINES: usize = 20;
    let tail = |bytes: &[u8]| {
        let text = String::from_utf8_lossy(bytes);
        let lines: Vec<&str> = text.lines().filter(|line| !line.trim().is_empty()).collect();
        lines[lines.len().saturating_sub(TAIL_LINES)..].join("\n")
    };
    let sections: Vec<String> = [("stderr", tail(stderr)), ("stdout", tail(stdout))]
        .into_iter()
        .filter(|(_, text)| !text.is_empty())
        .map(|(stream, text)| format!("\n{stream}:\n{text}"))
        .collect();
    if sections.is_empty() {
        return ", and printed nothing on stdout or stderr".to_owned();
    }
    sections.concat()
}

/// The controller's spawn: exit 127 with the argv it was asked to run.
///
/// A selector is resolved from the checkout and the pinned accio, and a resolution that reached for bazel would start
/// it from inside a held lease. [`Runtime::spawn`] cannot fail by contract, so the refusal arrives as the 127 the classifier
/// reads as infrastructure. A panic would be worse: the controller holds a lease and a worker lock.
pub fn refuse_spawn(command: &[OsString]) -> SpawnResult {
    SpawnResult {
        exit_code: 127,
        output: format!("selector resolution must not spawn subprocesses: {}", command_line(command)),
    }
}

/// The argv as one line, each part separated by a space.
pub fn command_line(command: &[OsString]) -> String {
    command.iter().map(|part| part.to_string_lossy()).collect::<Vec<_>>().join(" ")
}

/// Runs a child to completion, in `dir` when one is given.
///
/// Never fails: a command that could not start is exit 127, a run outcome the classifier can reason about rather
/// than an error every caller would have to re-classify. A child killed by a signal has no code and answers -1,
/// which classifies as infrastructure too.
pub fn spawn_child(dir: Option<&Path>, command: &[OsString], heartbeat: Option<Heartbeat<'_>>) -> SpawnResult {
    command_for(dir, command).map_or_else(nothing_to_spawn, |child| run_to_end(child, HEARTBEAT_INTERVAL, heartbeat))
}

/// Runs a child to completion, in `dir` when one is given, with the stdin and the streams of
/// [`Runtime::spawn_streaming`]: each stderr line goes to `error_line` as it comes, and the output is the whole stdout.
///
/// Never fails, as [`spawn_child`].
pub fn spawn_streaming_child(dir: Option<&Path>, command: &[OsString], error_line: &(dyn Fn(&str) + Sync)) -> SpawnResult {
    command_for(dir, command).map_or_else(nothing_to_spawn, |child| stream_to_end(child, error_line))
}

/// Runs a prepared command to completion, with the stdin and the streams of [`spawn_streaming_child`].
fn stream_to_end(mut child: Command, error_line: &(dyn Fn(&str) + Sync)) -> SpawnResult {
    let mut process = match spawn_piped(&mut child) {
        Ok(process) => process,
        Err(refusal) => return refusal,
    };
    // Held, never written, and dropped only after the child is reaped: the end of the stdin is the signal that this
    // process is gone.
    let stdin = process.stdin.take();
    let (Some(mut stdout), Some(stderr)) = (process.stdout.take(), process.stderr.take()) else {
        return SpawnResult {
            exit_code: 127,
            output: format!("{}: the pipes of the child are missing", child.get_program().to_string_lossy()),
        };
    };
    let output = thread::scope(|scope| {
        scope.spawn(move || {
            let mut reader = BufReader::new(stderr);
            let mut line = Vec::new();
            while matches!(reader.read_until(b'\n', &mut line), Ok(read) if read > 0) {
                let text = String::from_utf8_lossy(&line);
                error_line(text.trim_end_matches(['\n', '\r']));
                line.clear();
            }
        });
        let mut bytes = Vec::new();
        // What arrived before a broken pipe is what the child said; waiting for it is still the right answer.
        let _ = stdout.read_to_end(&mut bytes);
        String::from_utf8_lossy(&bytes).into_owned()
    });
    let waited = process.wait();
    drop(stdin);
    reaped(waited, output)
}

fn command_for(dir: Option<&Path>, command: &[OsString]) -> Option<Command> {
    let (program, args) = command.split_first()?;
    let mut child = isolated_command(program);
    child.args(args).stdin(Stdio::null());
    if let Some(dir) = dir {
        child.current_dir(dir);
    }
    Some(child)
}

fn nothing_to_spawn() -> SpawnResult {
    SpawnResult {
        exit_code: 127,
        output: "nothing to spawn: the command line is empty".to_owned(),
    }
}

/// Starts a prepared command with a pipe on each of its three descriptors.
fn spawn_piped(child: &mut Command) -> Result<std::process::Child, SpawnResult> {
    child.stdin(Stdio::piped()).stdout(Stdio::piped()).stderr(Stdio::piped());
    child.spawn().map_err(|error| could_not_start(child, &error))
}

/// Starts a prepared command with stdout and stderr on one pipe, and answers the child, the read end and the program.
fn spawn_on_pipe(mut child: Command) -> Result<(std::process::Child, io::PipeReader, OsString), SpawnResult> {
    let pipe = io::pipe().and_then(|(reader, writer)| Ok((reader, writer.try_clone()?, writer)));
    let (reader, stdout, stderr) = match pipe {
        Ok(pipe) => pipe,
        Err(error) => return Err(could_not_start(&child, &error)),
    };
    child.stdout(stdout).stderr(stderr);
    let spawned = child.spawn();
    // The command holds the pipe's write ends; until it is gone the read never sees the end of the stream.
    let program = child.get_program().to_owned();
    drop(child);
    match spawned {
        Ok(process) => Ok((process, reader, program)),
        Err(error) => Err(SpawnResult {
            exit_code: 127,
            output: format!("{}: {error}", program.to_string_lossy()),
        }),
    }
}

/// The result of a reaped child with the output it wrote.
fn reaped(waited: io::Result<ExitStatus>, output: String) -> SpawnResult {
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
fn run_to_end(child: Command, interval: Duration, heartbeat: Option<Heartbeat<'_>>) -> SpawnResult {
    let (mut process, mut reader, _) = match spawn_on_pipe(child) {
        Ok(spawned) => spawned,
        Err(refusal) => return refusal,
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
        reaped(waited, output)
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
            let start = tail.iter().position(|byte| byte & 0xc0 != 0x80).unwrap_or(tail.len());
            tail = &tail[start..];
        }
        String::from_utf8_lossy(tail).into_owned()
    }
}

/// The environment and the stdin of one [`git`] command.
#[derive(Clone, Copy, Default)]
struct Git<'a> {
    /// The index file the command reads and writes, in place of the index of the checkout.
    index: Option<&'a Path>,
    input: Option<&'a [u8]>,
}

/// Runs one git command in `root`, and answers its stdout. An error names the command and gives its stderr.
///
/// `GIT_OPTIONAL_LOCKS=0` keeps `git status` from a refresh of the index, which would take `index.lock` from a commit
/// that runs at the same time.
fn git(root: &Path, args: &[&str], with: &Git<'_>) -> io::Result<Vec<u8>> {
    let mut command = git_command();
    command
        .arg("-C")
        .arg(root)
        .args(args)
        .env("GIT_OPTIONAL_LOCKS", "0")
        .stdin(if with.input.is_some() { Stdio::piped() } else { Stdio::null() })
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    if let Some(index) = with.index {
        command.env("GIT_INDEX_FILE", index);
    }
    let refused = |said: String| io::Error::other(format!("git {} in {}: {said}", args.join(" "), root.display()));
    let mut child = command.spawn().map_err(|error| refused(error.to_string()))?;
    let stdin = child.stdin.take();
    // From a thread, so a child that writes before it reads to the end cannot block on a full pipe.
    let finished = thread::scope(|scope| {
        if let (Some(mut stdin), Some(input)) = (stdin, with.input) {
            scope.spawn(move || {
                // A child that exits before it reads everything closes the pipe; its exit code tells the rest.
                let _ = stdin.write_all(input);
            });
        }
        child.wait_with_output()
    });
    let finished = finished.map_err(|error| refused(error.to_string()))?;
    if !finished.status.success() {
        let stderr = String::from_utf8_lossy(&finished.stderr).trim().to_owned();
        return Err(refused(if stderr.is_empty() { finished.status.to_string() } else { stderr }));
    }
    Ok(finished.stdout)
}

/// [`git`] for a command that answers one line of text.
fn git_text(root: &Path, args: &[&str], with: &Git<'_>) -> io::Result<String> {
    Ok(String::from_utf8_lossy(&git(root, args, with)?).trim_end().to_owned())
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

    /// A temporary file beside the target, renamed over it: a rename within one directory replaces the file at once.
    fn write_text_file(&self, path: &Path, text: &str) -> io::Result<()> {
        let dir = path.parent().unwrap_or_else(|| Path::new("."));
        std::fs::create_dir_all(dir)?;
        let mut file = tempfile::NamedTempFile::new_in(dir)?;
        file.write_all(text.as_bytes())?;
        let mut temp = file.into_temp_path();
        std::fs::rename(&temp, path)?;
        temp.disable_cleanup(true);
        Ok(())
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

    /// Reads the index and the ignore files of the checkout through `checkout_files`, which answers as
    /// `git ls-files --cached --others --exclude-standard` does, without the git executable. The paths start with `dir`
    /// as the caller spelled it.
    ///
    /// Not through the injected spawn. That spawn refuses for the lane controller, which must not start bazel from
    /// inside a held lease. The listing of a directory the caller named is a read of the checkout, the same kind of
    /// call as a directory listing. An unmerged file that the index holds three times is listed once.
    fn list_files(&self, dir: &Path) -> io::Result<Vec<PathBuf>> {
        let refused = |said: String| io::Error::other(format!("cannot list the files under {}: {said}", dir.display()));
        if !dir.is_dir() {
            return Err(refused("it is not a directory".to_owned()));
        }
        // The real path of the directory, as `git -C` reads it: a link or a `..` in `dir` does not change the answer.
        let (checkout, root) = checkout_files::locate(dir).map_err(|error| refused(format!("{error:#}")))?;
        let names = checkout_files::list(&checkout, &[&root]).map_err(|error| refused(format!("{error:#}")))?;
        let prefix = if root.is_empty() { String::new() } else { format!("{root}/") };
        let mut files: Vec<PathBuf> = names
            .iter()
            .filter_map(|name| name.strip_prefix(&prefix))
            .map(|name| {
                let name = if cfg!(windows) { name.replace('/', "\\") } else { name.to_owned() };
                dir.join(name)
            })
            // A symbolic link is listed as a path of its own. Only one to a file is a file a caller can have edited.
            .filter(|path| path.is_file())
            .collect();
        files.sort();
        Ok(files)
    }

    /// A blob that git cannot read is a file that the revision does not hold, when the revision itself resolves.
    fn revision_text(&self, revision: &str, path: &str) -> io::Result<Option<String>> {
        if let Ok(blob) = self.git(&["cat-file", "blob", &format!("{revision}:{path}")]) {
            return Ok(Some(blob));
        }
        self.git(&["rev-parse", "--verify", "--quiet", &format!("{revision}^{{commit}}")])?;
        Ok(None)
    }

    fn upstream_base(&self) -> io::Result<String> {
        let tracked = self
            .git(&["symbolic-ref", "--quiet", "HEAD"])
            .ok()
            .and_then(|branch| self.git(&["for-each-ref", "--format=%(upstream)", branch.trim()]).ok())
            .map(|upstream| upstream.trim().to_owned())
            .filter(|upstream| !upstream.is_empty());
        let upstream = match tracked {
            Some(upstream) => upstream,
            None => self
                .git(&["rev-parse", "--verify", "--quiet", "refs/remotes/origin/HEAD"])?
                .trim()
                .to_owned(),
        };
        Ok(self.git(&["merge-base", "HEAD", &upstream])?.trim().to_owned())
    }

    fn exists(&self, path: &Path) -> bool {
        path.exists()
    }

    /// Asks git, not through the injected spawn, for the reason of [`OsRuntime::list_files`].
    ///
    /// A checkout whose `.git` is no file is not a linked work tree, and it costs one stat. A clean linked work tree
    /// costs one `git status`. With local changes, a copy of the index takes the changed paths that the status names,
    /// and `git write-tree` writes the tree of the copy. The index of the checkout stays as it was.
    ///
    /// Two cases differ from `git add -A`. An untracked nested checkout (`?? dir/`) is left out, where `add -A` would
    /// add a gitlink. A directory replaced by a symbolic link makes `update-index` fail, so the answer is an error.
    fn checkout_tree(&self) -> io::Result<Option<CheckoutTree>> {
        let root = &self.repo_root;
        if self.host_slot || !root.join(".git").is_file() {
            return Ok(None);
        }
        // `HEAD:` is the root tree of HEAD.
        let named = git_text(root, &["rev-parse", "HEAD", "HEAD:", "--git-path", "index"], &Git::default())?;
        let mut lines = named.lines();
        let (Some(head), Some(head_tree), Some(index)) = (lines.next(), lines.next(), lines.next()) else {
            return Err(io::Error::other(format!(
                "git rev-parse named no HEAD, tree and index in {}",
                root.display()
            )));
        };
        let status = git(
            root,
            &["status", "--porcelain=v1", "-z", "--untracked-files=all", "--no-renames"],
            &Git::default(),
        )?;
        // Each entry is `XY <path>`. A path that ends with `/` is a nested checkout, which `update-index` refuses.
        let mut input = Vec::new();
        let mut changed = Vec::new();
        for entry in status.split(|byte| *byte == 0) {
            if let Some(path) = entry.get(3..).filter(|path| !path.is_empty() && !path.ends_with(b"/")) {
                input.extend_from_slice(path);
                input.push(0);
                changed.push(String::from_utf8_lossy(path).into_owned());
            }
        }
        if changed.is_empty() {
            return Ok(Some(CheckoutTree {
                head: head.to_owned(),
                tree: head_tree.to_owned(),
                changed,
            }));
        }
        // Git replaces its index by a rename, so the copy is the index before a concurrent commit or after it.
        let copy = tempfile::Builder::new().prefix("bt-check-index-").tempfile()?;
        std::fs::copy(root.join(index), copy.path())?;
        let with_copy = Git {
            index: Some(copy.path()),
            input: None,
        };
        git(
            root,
            &["update-index", "--add", "--remove", "-z", "--stdin"],
            &Git {
                input: Some(&input),
                ..with_copy
            },
        )?;
        let tree = git_text(root, &["write-tree"], &with_copy)?;
        Ok(Some(CheckoutTree {
            head: head.to_owned(),
            tree,
            changed,
        }))
    }

    fn commit_tree(&self, commit: &str) -> io::Result<String> {
        git_text(
            &self.repo_root,
            &["rev-parse", "--verify", &format!("{commit}^{{tree}}")],
            &Git::default(),
        )
    }

    fn git_path(&self, name: &str) -> io::Result<PathBuf> {
        let path = git_text(&self.repo_root, &["rev-parse", "--git-path", name], &Git::default())?;
        // Relative to the checkout root in a main checkout, absolute in a linked work tree.
        Ok(self.repo_root.join(path))
    }

    /// A checkout root without `.git` is outside git, and it costs one stat.
    fn git_common_path(&self, name: &str) -> io::Result<Option<PathBuf>> {
        if !self.repo_root.join(".git").exists() {
            return Ok(None);
        }
        let dir = git_text(&self.repo_root, &["rev-parse", "--git-common-dir"], &Git::default())?;
        // Relative to the checkout root in a main checkout, absolute in a linked work tree.
        Ok(Some(self.repo_root.join(dir).join(name)))
    }

    fn spawn(&self, command: &[OsString], heartbeat: Option<Heartbeat<'_>>) -> SpawnResult {
        match &self.spawner {
            Spawner::Refuse => refuse_spawn(command),
            Spawner::Child => spawn_child(None, command, heartbeat),
            Spawner::ChildIn(dir) => spawn_child(Some(dir), command, heartbeat),
        }
    }

    fn spawn_streaming(&self, command: &[OsString]) -> SpawnResult {
        let error_line = |line: &str| (self.write_error)(line);
        match &self.spawner {
            Spawner::Refuse => refuse_spawn(command),
            Spawner::Child => spawn_streaming_child(None, command, &error_line),
            Spawner::ChildIn(dir) => spawn_streaming_child(Some(dir), command, &error_line),
        }
    }

    /// Runs the first wrapper of [`ACCIO_WRAPPERS`] that the checkout holds, through [`accio_command`].
    fn accio(&self, args: &[&str]) -> Result<String, Refusal> {
        let Some(wrapper) = ACCIO_WRAPPERS.iter().find(|wrapper| self.repo_root.join(wrapper).is_file()) else {
            return Err(accio_failed(format!(
                "the checkout {} has no accio wrapper: {}",
                self.repo_root.display(),
                ACCIO_WRAPPERS.join(" or ")
            )));
        };
        run_accio(&self.repo_root, &accio_command(&self.repo_root, Platform::current(), wrapper, args))
    }

    /// A fresh file in the system temporary directory, created so no second process can pick the same name.
    fn temp_file(&self, prefix: &str) -> PathBuf {
        match tempfile::Builder::new().prefix(&format!("{prefix}-")).suffix(".json").tempfile() {
            Ok(mut file) => {
                // The file outlives the handle: the caller removes it, and bazel writes it meanwhile.
                file.disable_cleanup(true);
                file.path().to_path_buf()
            }
            // Only reachable when the temporary directory is unwritable, and a temporary name is not worth refusing
            // an invocation over; bazel then reports the path it cannot write, which is the real problem.
            Err(_) => std::env::temp_dir().join(format!("{prefix}-{}-{}.json", std::process::id(), self.now_ms())),
        }
    }

    /// Deletes one file and is content for it to be absent already.
    fn remove(&self, path: &Path) -> io::Result<()> {
        match std::fs::remove_file(path) {
            Err(error) if error.kind() != io::ErrorKind::NotFound => Err(error),
            _ => Ok(()),
        }
    }

    fn copy_file(&self, from: &Path, to: &Path) -> io::Result<()> {
        if let Some(parent) = to.parent() {
            std::fs::create_dir_all(parent)?;
        }
        // A new file rather than `std::fs::copy`: bazel's outputs are read-only, and a read-only copy is one that
        // Windows refuses to delete when the retention removes an old run.
        io::copy(&mut File::open(from)?, &mut File::create(to)?).map(|_| ())
    }

    fn create_dir_all(&self, path: &Path) -> io::Result<()> {
        std::fs::create_dir_all(path)
    }

    fn remove_dir_all(&self, path: &Path) -> io::Result<()> {
        match std::fs::remove_dir_all(path) {
            Err(error) if error.kind() != io::ErrorKind::NotFound => Err(error),
            _ => Ok(()),
        }
    }

    fn home_dir(&self) -> Option<PathBuf> {
        std::env::home_dir()
    }

    fn now_ms(&self) -> u64 {
        SystemTime::now().duration_since(UNIX_EPOCH).map_or(0, millis)
    }

    fn self_build_ms(&self) -> Option<u64> {
        self.self_build_ms
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
