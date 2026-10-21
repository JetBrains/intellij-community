//! The JetBrains Central account of a live run.
//!
//! A live run takes the accounts of its agents from the Central login of the developer. The run hands the guest that
//! login as a run input file. Before the lane IDE launches, [`Central::provision`] runs the declared Central CLI as the
//! guest account, with the account home as `HOME`. It pins the configuration, imports the login on stdin, starts the
//! proxy, wires each agent and checks that the proxy runs on the pinned port. At the end of the iteration,
//! [`Central::tear_down`] stops the proxy, ends the processes that the agents left, and deletes the Central state and
//! every agent home that `central add` wrote, each whole. `central logout` never runs, because it may revoke the
//! imported session.
//!
//! No value of the login reaches an argv, a note, a refusal or a report. The login file is read once and deleted, and
//! every copy of its bytes is zeroed after the import. A failure quotes only the last line of the standard error of
//! the step, with every part of the login and every token-like run redacted.

use std::borrow::Cow;
use std::fs;
use std::io::{self, Seek, SeekFrom, Write};
use std::os::unix::fs::MetadataExt;
use std::os::unix::process::ExitStatusExt;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::sync::LazyLock;
use std::thread;
use std::time::{Duration, Instant};

use avl_wire::daemon::RUN_SECRETS_VARIABLE;
use nix::sys::signal::{Signal, kill};
use nix::unistd::{Pid, getuid};
use regex::Regex;

use crate::reply::{AgentRefusal, AgentRefusalExt};

#[cfg(test)]
mod tests;

/// The refusal code of a live run that cannot provision Central or tear it down.
pub(crate) const CENTRAL_CODE: &str = "lane_live_central";

/// The bound of every Central step. A `central add` answers in seconds, and a prompt would wait forever.
pub(crate) const STEP_TIMEOUT: Duration = Duration::from_secs(30);

/// The Central CLI's state directory under a home: its login, its proxy and its agents.
pub(crate) const CENTRAL_STATE: &str = ".jetbrains-central";

/// What the teardown deletes under the account home, each whole. `central add` writes beyond the wiring files: the
/// plugins of Claude, the plugin cache of Codex, the extensions of Junie and the cache of Central.
pub(crate) const TEARDOWN_ENTRIES: [&str; 6] = [
    CENTRAL_STATE,
    ".claude",
    ".claude.json",
    ".codex",
    ".junie",
    ".cache/jetbrains-central",
];

/// The executable names of the processes that the teardown ends: the agent CLIs, the ACP adapters that they ship as,
/// and the daemon of Central. A Junie TUI leaves a gateway process, an app server of Codex outlives its scenario, an
/// ACP Junie outlives its IDE, and `proxy stop` can leave `jetbrainsd`. In a warm guest they add up across iterations
/// until the kernel ends the lane IDE for memory.
pub(crate) const AGENT_PROCESS_NAMES: [&str; 5] = ["junie", "codex", "claude", "central", "jetbrainsd"];

/// The pause between two looks at a child or at the process table.
const POLL: Duration = Duration::from_millis(20);

/// How long a child that exited may take to let its stdin writer end.
const STDIN_DRAIN: Duration = Duration::from_secs(1);

/// The text that stands for a redacted part of a failure line.
const REDACTED: &str = "<redacted>";

/// The longest failure line, in characters.
const MAX_LINE_CHARS: usize = 200;

/// The shortest part of a secret that is redacted. A shorter part would match ordinary words of the line.
const MIN_SECRET_PART: usize = 16;

/// The colour escapes that the Central CLI writes even into a file.
static ANSI_SGR: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\x1b\[[0-9;]*m").expect("a literal pattern compiles"));

/// A run that could be a token or a key.
static TOKEN_LIKE: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"[A-Za-z0-9_+/=.-]{24,}").expect("a literal pattern compiles"));

/// What ends a token of a line of the secret: white space, quotes, and the punctuation of JSON and of `key=value`.
static TOKEN_SEPARATOR: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r#"[\s"'`\\,:;=&?{}\[\]()<>]+"#).expect("a literal pattern compiles"));

/// The Proxy line of `central status` for a running proxy. The CLI has no machine-readable output, so this pins the
/// text of the version that the lane was written against.
static RUNNING_PROXY: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"Proxy\s+running on port (\d+)").expect("a literal pattern compiles"));

/// The guest account of a live run.
///
/// Only [`GuestAccount::of_lane_daemon`] makes one, from the marker of the controller, so a developer host never gets a
/// [`Central`]: its teardown would delete the agent homes of the developer.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct GuestAccount {
    /// The account home, `HOME` of the lane daemon: an absolute directory below the root.
    home: PathBuf,
    /// The directory of the run inputs, which [`RUN_SECRETS_VARIABLE`] names.
    run_secrets: PathBuf,
}

impl GuestAccount {
    /// The guest account of the lane daemon whose environment `variable` reads, or `None` without the marker of the
    /// controller: [`RUN_SECRETS_VARIABLE`]. The controller sets it for the lane daemon of a guest only, and only the
    /// verb `lane-daemon` asks.
    ///
    /// A guest that is not Linux is refused: the Docker worker is the one Linux guest, and the live lane runs there. A
    /// home that is not an absolute directory below the root is refused too.
    pub(crate) fn of_lane_daemon(variable: impl Fn(&str) -> Option<String>, linux: bool) -> Result<Option<Self>, AgentRefusal> {
        let Some(run_secrets) = variable(RUN_SECRETS_VARIABLE).filter(|value| !value.trim().is_empty()) else {
            return Ok(None);
        };
        if !linux {
            return Err(refused(
                "the live lane runs in a Linux guest only, the Docker worker: a macOS guest has the Keychain of its account",
            ));
        }
        let home = PathBuf::from(variable("HOME").unwrap_or_default());
        if !home.is_absolute() || home.parent().is_none() {
            return Err(refused(format!(
                "the account home of a live run must be an absolute directory below the root: '{}'",
                home.display()
            )));
        }
        Ok(Some(Self {
            home,
            run_secrets: PathBuf::from(run_secrets),
        }))
    }

    /// The account home.
    pub(crate) fn home(&self) -> &Path {
        &self.home
    }

    /// The file of the run input `name`. It may be absent, because a run names its inputs.
    pub(crate) fn input(&self, name: &str) -> PathBuf {
        self.run_secrets.join(name)
    }
}

/// What one Central step writes to the output that [`Central`] reads.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum StepOutput {
    /// Only the standard error is kept, and the standard output is discarded: a step may print the login.
    Stderr,
    /// Both streams are kept, in one text: `central status` writes its report to one of them.
    Merged,
}

/// How one Central step ended: its exit code, `None` when it outlived its bound, and the text of its kept output.
#[derive(Debug, Default)]
pub(crate) struct Outcome {
    pub exit_code: Option<i32>,
    pub text: String,
}

/// How [`Central`] runs one step of the Central CLI. The tests pass a fake CLI to [`ProcessRunner`].
pub(crate) trait CentralRunner {
    /// Runs `argv` with `HOME` set to `home` over the environment of this process, with `stdin` on its standard input
    /// or nothing, and ends it after `timeout`.
    fn run(&mut self, argv: &[String], home: &Path, stdin: Option<&[u8]>, output: StepOutput, timeout: Duration) -> io::Result<Outcome>;
}

/// How the teardown ends the processes that the agents left.
pub(crate) trait AgentProcesses {
    /// Ends every process of this account whose executable file has a name of `names`, except this process and its
    /// ancestors, and waits up to `timeout` for them to go. Answers one `<name> pid <pid>` for each process.
    fn stop(&mut self, names: &[&str], timeout: Duration) -> Vec<String>;
}

impl<T: AgentProcesses + ?Sized> AgentProcesses for &mut T {
    fn stop(&mut self, names: &[&str], timeout: Duration) -> Vec<String> {
        (**self).stop(names, timeout)
    }
}

impl<T: AgentProcesses + ?Sized> AgentProcesses for Box<T> {
    fn stop(&mut self, names: &[&str], timeout: Duration) -> Vec<String> {
        (**self).stop(names, timeout)
    }
}

/// The Central CLI and the account that a live run provisions.
pub(crate) struct Central<'a, R: CentralRunner, P: AgentProcesses> {
    pub account: &'a GuestAccount,
    /// The declared Central CLI, an absolute path. Never a lookup on `PATH`, where the Central guard of the lane answers.
    pub cli: &'a Path,
    /// The declared Central version. The release track pins it, so the CLI never updates itself in a run.
    pub pinned_version: &'a str,
    /// The port that the configuration pins.
    pub port: u16,
    /// The agents that `central add` wires, in its own spelling and in this order.
    pub agents: &'a [String],
    pub step_timeout: Duration,
    pub runner: R,
    pub processes: P,
}

/// What a provisioning set up, for the run notes. It names a port, never a token.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct Provisioned {
    pub port: u16,
    /// The note of the provisioning: `central provisioning: login import ok, proxy :<port>, add <agents> ok`.
    pub note: String,
}

impl<R: CentralRunner, P: AgentProcesses> Central<'_, R, P> {
    /// Tears down what an earlier run left, pins the configuration, imports the login of the file `login` and deletes
    /// that file, starts the proxy, wires every agent, and checks that the proxy runs on the pinned port. Each line of
    /// what it did goes to `note`.
    ///
    /// A daemon that was killed before its close leaves the proxy and the login of its last run, so they go first. A
    /// failed step fails the provisioning, and the teardown of the iteration removes what it set up.
    pub(crate) fn provision(&mut self, login: &Path, note: &mut dyn FnMut(&str)) -> Result<Provisioned, AgentRefusal> {
        let removed = self.tear_down(note)?;
        if !removed.is_empty() {
            note(&format!(
                "central provisioning: removed what an earlier run left: {}",
                joined(&removed)
            ));
        }
        let port = self.port.to_string();
        self.step("config set release-track", &["config", "set", "release-track", self.pinned_version])?;
        self.step("config set google-analytics", &["config", "set", "google-analytics", "off"])?;
        self.step("config set port", &["config", "set", "port", &port])?;
        self.import_login(login)?;
        self.step("proxy start", &["proxy", "start"])?;
        for agent in self.agents {
            self.step(&format!("add {agent}"), &["add", agent])?;
        }
        self.require_proxy_on_port()?;
        let provisioned = Provisioned {
            port: self.port,
            note: format!(
                "central provisioning: login import ok, proxy :{}, add {} ok",
                self.port,
                self.agents.join("|")
            ),
        };
        note(&provisioned.note);
        Ok(provisioned)
    }

    /// Stops the proxy, ends the processes that the agents left, then deletes the Central state and every agent home of
    /// [`TEARDOWN_ENTRIES`], each whole. Answers the deleted entries.
    ///
    /// Nothing is read before it is deleted. A link is deleted, never followed, also on the way to an entry. A proxy
    /// that does not stop is a note. A path that cannot be deleted is passed over, and every such path is refused
    /// together at the end, so a close never stops halfway. The processes go before the homes, because a process that
    /// outlives its scenario writes its home again.
    pub(crate) fn tear_down(&mut self, note: &mut dyn FnMut(&str)) -> Result<Vec<PathBuf>, AgentRefusal> {
        let home = self.account.home().to_owned();
        if home.join(CENTRAL_STATE).symlink_metadata().is_ok() {
            let stopped = self
                .runner
                .run(&self.argv(&["proxy", "stop"]), &home, None, StepOutput::Stderr, self.step_timeout);
            match stopped {
                Ok(Outcome { exit_code: Some(0), .. }) => {}
                Ok(Outcome { exit_code: Some(code), .. }) => note(&format!("central proxy stop answered exit {code}")),
                Ok(Outcome { exit_code: None, .. }) => note(&format!(
                    "central proxy stop did not finish within {} s",
                    self.step_timeout.as_secs()
                )),
                Err(error) => note(&format!("central proxy stop failed to start: {}", error.kind())),
            }
        }
        let ended = self.processes.stop(&AGENT_PROCESS_NAMES, self.step_timeout);
        if !ended.is_empty() {
            note(&format!(
                "central provisioning: ended {} leftover agent process(es): {}",
                ended.len(),
                ended.join(", ")
            ));
        }
        let mut failures = Vec::new();
        let removed: Vec<PathBuf> = TEARDOWN_ENTRIES
            .iter()
            .map(|entry| home.join(entry))
            .filter(|entry| delete_whole(&home, entry, &mut failures))
            .collect();
        if failures.is_empty() {
            return Ok(removed);
        }
        let removed_text = if removed.is_empty() {
            String::new()
        } else {
            format!("; removed {}", joined(&removed))
        };
        Err(refused(format!(
            "central provisioning: the teardown left {} path(s) under {}: {}{removed_text}",
            failures.len(),
            home.display(),
            failures.join("; ")
        )))
    }

    /// `central login import` with the bytes of `login` on stdin, never on argv. The file is deleted whatever the
    /// outcome, and the bytes are zeroed after the step.
    fn import_login(&mut self, login: &Path) -> Result<(), AgentRefusal> {
        let read = fs::read(login);
        let _ = fs::remove_file(login);
        let value = Secret(read.map_err(|error| {
            refused(format!(
                "central provisioning: the run input {} cannot be read ({})",
                login.display(),
                error.kind()
            ))
        })?);
        if value.0.iter().all(u8::is_ascii_whitespace) {
            return Err(refused(format!("central provisioning: the run input {} is empty", login.display())));
        }
        let text = String::from_utf8_lossy(&value.0);
        let parts = SecretParts::of(&text);
        if let Cow::Owned(copy) = text {
            wipe(copy);
        }
        self.step_with("login import", &["login", "import"], Some(&value.0), &parts)
    }

    /// Requires that `central status` reports the proxy as running on the pinned port.
    fn require_proxy_on_port(&mut self) -> Result<(), AgentRefusal> {
        let home = self.account.home().to_owned();
        let outcome = self
            .runner
            .run(&self.argv(&["status"]), &home, None, StepOutput::Merged, self.step_timeout)
            .map_err(|error| refused(format!("central provisioning: `central status` failed to start: {}", error.kind())))?;
        let port = (outcome.exit_code == Some(0)).then(|| running_proxy_port(&outcome.text)).flatten();
        wipe(outcome.text);
        match port {
            Some(port) if port == self.port => Ok(()),
            Some(port) => Err(refused(format!(
                "central provisioning: `central status` reports the proxy on port {port}, the pinned port is {}",
                self.port
            ))),
            None => Err(refused(
                "central provisioning: `central status` reports no running proxy after `central proxy start`",
            )),
        }
    }

    fn step(&mut self, name: &str, arguments: &[&str]) -> Result<(), AgentRefusal> {
        self.step_with(name, arguments, None, &SecretParts::default())
    }

    /// Runs one step, and refuses a failure with the name of the step, how it ended and its last stderr line, with each
    /// part of `secret` redacted.
    fn step_with(&mut self, name: &str, arguments: &[&str], stdin: Option<&[u8]>, secret: &SecretParts) -> Result<(), AgentRefusal> {
        let home = self.account.home().to_owned();
        let outcome = self
            .runner
            .run(&self.argv(arguments), &home, stdin, StepOutput::Stderr, self.step_timeout)
            .map_err(|error| {
                refused(format!(
                    "central provisioning failed at `central {name}`: it cannot start ({})",
                    error.kind()
                ))
            })?;
        if outcome.exit_code == Some(0) {
            wipe(outcome.text);
            return Ok(());
        }
        let reason = outcome.exit_code.map_or_else(
            || format!("no exit within {} s", self.step_timeout.as_secs()),
            |code| format!("exit {code}"),
        );
        let line = last_line(&outcome.text, secret);
        wipe(outcome.text);
        Err(refused(format!(
            "central provisioning failed at `central {name}`: {reason}{}",
            line.map(|line| format!("; stderr: {line}")).unwrap_or_default()
        )))
    }

    fn argv(&self, arguments: &[&str]) -> Vec<String> {
        std::iter::once(self.cli.display().to_string())
            .chain(arguments.iter().map(|argument| (*argument).to_owned()))
            .collect()
    }
}

/// The port of the proxy that `central status` reports as running, or `None` when its Proxy line says anything else.
/// The CLI colours its output even into a file, so the escapes go first.
pub(crate) fn running_proxy_port(status: &str) -> Option<u16> {
    RUNNING_PROXY.captures(&ANSI_SGR.replace_all(status, ""))?[1].parse().ok()
}

/// The last non-blank line of `stderr`, with every part of `secret` and every token-like run redacted, at most
/// [`MAX_LINE_CHARS`] characters long, or `None` for a blank `stderr`.
pub(crate) fn last_line(stderr: &str, secret: &SecretParts) -> Option<String> {
    let plain = ANSI_SGR.replace_all(stderr, "");
    let found = plain.lines().map(str::trim).rfind(|line| !line.is_empty()).map(str::to_owned);
    if let Cow::Owned(copy) = plain {
        wipe(copy);
    }
    let mut line = found?;
    for part in &secret.0 {
        line = line.replace(part.as_str(), REDACTED);
    }
    let redacted = TOKEN_LIKE.replace_all(&line, REDACTED).chars().take(MAX_LINE_CHARS).collect();
    wipe(line);
    Some(redacted)
}

/// The parts of a secret that a failure line must not hold, longest first, so that a line goes whole before its tokens:
/// the value trimmed, each of its lines trimmed, and each token of a line that holds a letter and a digit. A part
/// shorter than [`MIN_SECRET_PART`] stays, because it would match ordinary words. Each part is zeroed on drop.
#[derive(Debug, Default)]
pub(crate) struct SecretParts(Vec<String>);

impl SecretParts {
    pub(crate) fn of(secret: &str) -> Self {
        let trimmed = secret.trim();
        let mut parts: Vec<String> = Vec::new();
        let mut add = |part: &str| {
            if part.chars().count() >= MIN_SECRET_PART && !parts.iter().any(|known| known == part) {
                parts.push(part.to_owned());
            }
        };
        add(trimmed);
        for line in trimmed.lines().map(str::trim) {
            add(line);
            for token in TOKEN_SEPARATOR.split(line) {
                if token.chars().any(char::is_alphabetic) && token.chars().any(|character| character.is_ascii_digit()) {
                    add(token);
                }
            }
        }
        parts.sort_by_key(|part| std::cmp::Reverse(part.chars().count()));
        Self(parts)
    }
}

impl Drop for SecretParts {
    fn drop(&mut self) {
        for part in &mut self.0 {
            wipe(std::mem::take(part));
        }
    }
}

/// The bytes of a secret, zeroed on drop.
struct Secret(Vec<u8>);

impl Drop for Secret {
    fn drop(&mut self) {
        self.0.fill(0);
        std::hint::black_box(&self.0);
    }
}

/// Zeroes the bytes of `text`, which may hold a secret that a child echoed.
fn wipe(text: String) {
    drop(Secret(text.into_bytes()));
}

/// Deletes `entry` and everything below it without following a link, and answers whether it existed. A link between
/// `home` and `entry` skips the entry. Every path that cannot be deleted goes to `failures`, and the walk goes on.
fn delete_whole(home: &Path, entry: &Path, failures: &mut Vec<String>) -> bool {
    let mut parent = entry.parent();
    while let Some(directory) = parent.filter(|directory| *directory != home) {
        if directory.symlink_metadata().is_ok_and(|metadata| metadata.file_type().is_symlink()) {
            failures.push(format!(
                "{} (not followed through the link {})",
                entry.display(),
                directory.display()
            ));
            return false;
        }
        parent = directory.parent();
    }
    let Ok(metadata) = entry.symlink_metadata() else {
        return false;
    };
    if !metadata.is_dir() {
        if let Err(error) = fs::remove_file(entry) {
            failures.push(format!("{} ({})", entry.display(), error.kind()));
        }
        return true;
    }
    for item in walkdir::WalkDir::new(entry).follow_links(false).contents_first(true) {
        match item {
            Ok(item) => {
                let deleted = if item.file_type().is_dir() {
                    fs::remove_dir(item.path())
                } else {
                    fs::remove_file(item.path())
                };
                if let Err(error) = deleted {
                    failures.push(format!("{} ({})", item.path().display(), error.kind()));
                }
            }
            Err(error) => failures.push(format!(
                "{} ({})",
                error.path().unwrap_or(entry).display(),
                error
                    .io_error()
                    .map_or(Cow::Borrowed("walk failed"), |error| Cow::Owned(error.kind().to_string()))
            )),
        }
    }
    true
}

fn joined(paths: &[PathBuf]) -> String {
    paths.iter().map(|path| path.display().to_string()).collect::<Vec<_>>().join(", ")
}

fn refused(message: impl Into<String>) -> AgentRefusal {
    AgentRefusal::refused(CENTRAL_CODE, message)
}

/// The real runner. The kept output goes to an unnamed file of this account rather than a pipe: `proxy start` leaves a
/// proxy that inherits the descriptor, and a pipe that it holds would never reach its end. The file is emptied after the
/// read.
///
/// The stdin is written on a thread of its own, inside the same bound: a child that never reads it would block a write
/// larger than the pipe. Ending the child ends that write with an error.
pub(crate) struct ProcessRunner;

impl CentralRunner for ProcessRunner {
    fn run(&mut self, argv: &[String], home: &Path, stdin: Option<&[u8]>, output: StepOutput, timeout: Duration) -> io::Result<Outcome> {
        let Some((program, arguments)) = argv.split_first() else {
            return Err(io::Error::new(io::ErrorKind::InvalidInput, "an empty command"));
        };
        let mut kept = tempfile::tempfile()?;
        let stdout = match output {
            StepOutput::Stderr => Stdio::null(),
            StepOutput::Merged => Stdio::from(kept.try_clone()?),
        };
        let mut child = Command::new(program)
            .args(arguments)
            .env("HOME", home)
            .stdin(if stdin.is_some() { Stdio::piped() } else { Stdio::null() })
            .stdout(stdout)
            .stderr(Stdio::from(kept.try_clone()?))
            .spawn()?;
        let writer = match (child.stdin.take(), stdin) {
            (Some(mut pipe), Some(bytes)) => {
                let bytes = Secret(bytes.to_vec());
                Some(thread::spawn(move || {
                    let _ = pipe.write_all(&bytes.0);
                }))
            }
            _ => None,
        };
        let deadline = Instant::now() + timeout;
        let exit_code = loop {
            if let Some(status) = child.try_wait()? {
                break Some(status.code().unwrap_or_else(|| 128 + status.signal().unwrap_or_default()));
            }
            if Instant::now() >= deadline {
                let _ = child.kill();
                let _ = child.wait();
                break None;
            }
            thread::sleep(POLL);
        };
        if let Some(writer) = writer {
            let until = Instant::now() + STDIN_DRAIN;
            while !writer.is_finished() && Instant::now() < until {
                thread::sleep(POLL);
            }
        }
        let mut bytes = Vec::new();
        kept.seek(SeekFrom::Start(0))?;
        io::copy(&mut kept, &mut bytes)?;
        kept.set_len(0)?;
        let text = String::from_utf8_lossy(&bytes).into_owned();
        drop(Secret(bytes));
        Ok(Outcome { exit_code, text })
    }
}

/// The real processes, from `/proc`: those of this account whose executable file has one of the names. This process and
/// its ancestors, the lane daemon and the supervisor among them, are never among them. A guest without `/proc` has none.
pub(crate) struct LiveAgentProcesses;

impl AgentProcesses for LiveAgentProcesses {
    fn stop(&mut self, names: &[&str], timeout: Duration) -> Vec<String> {
        let Ok(entries) = fs::read_dir("/proc") else {
            return Vec::new();
        };
        let uid = getuid().as_raw();
        let spared = ancestors();
        let matched: Vec<(i32, String)> = entries
            .filter_map(Result::ok)
            .filter_map(|entry| {
                let pid: i32 = entry.file_name().to_str()?.parse().ok()?;
                if spared.contains(&pid) || entry.metadata().ok()?.uid() != uid {
                    return None;
                }
                let name = executable_name(pid)?;
                names.contains(&name.as_str()).then_some((pid, name))
            })
            .collect();
        for (pid, _) in &matched {
            let _ = kill(Pid::from_raw(*pid), Signal::SIGKILL);
        }
        let deadline = Instant::now() + timeout;
        while matched.iter().any(|(pid, _)| Path::new(&format!("/proc/{pid}")).exists()) && Instant::now() < deadline {
            thread::sleep(POLL);
        }
        matched.into_iter().map(|(pid, name)| format!("{name} pid {pid}")).collect()
    }
}

/// The file name of the executable of the process `pid`, as `/proc/<pid>/exe` names it.
fn executable_name(pid: i32) -> Option<String> {
    let target = fs::read_link(format!("/proc/{pid}/exe")).ok()?;
    let name = target.file_name()?.to_str()?;
    Some(name.strip_suffix(" (deleted)").unwrap_or(name).to_owned())
}

/// This process and each of its ancestors, by the `PPid` lines of `/proc/<pid>/status`.
fn ancestors() -> Vec<i32> {
    let mut chain = Vec::new();
    let mut pid = i32::try_from(std::process::id()).ok();
    while let Some(current) = pid.filter(|current| *current > 0 && !chain.contains(current)) {
        chain.push(current);
        pid = fs::read_to_string(format!("/proc/{current}/status")).ok().and_then(|status| {
            status
                .lines()
                .find_map(|line| line.strip_prefix("PPid:"))
                .and_then(|value| value.trim().parse().ok())
        });
    }
    chain
}
