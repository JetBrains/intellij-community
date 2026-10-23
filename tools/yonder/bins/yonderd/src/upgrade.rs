//! The upgrade of the agent binary without ssh. Two ways lead here, and both end in [`Install::run`]:
//!
//! - [`Request::Upgrade`] of a client: `serve` stages the binary of the byte stream, installs it, answers, waits for its
//!   other requests, and exits. launchd then starts the new binary, because the LaunchAgent has `KeepAlive`.
//! - `yonderd upgrade <binary>`: a run of a client starts it as the user, with a binary that the host built. An agent
//!   without [`feature::UPGRADE`] knows no upgrade request, so a client takes this way once. The command restarts the
//!   agent with `launchctl kickstart -k`.
//!
//! The install stages the new binary beside the binary of the LaunchAgent, checks its `version` line, keeps the current
//! binary as `<binary>.previous`, and renames the staged binary over the binary. Then it removes the listening file and
//! starts `yonderd watchdog` in a session of its own. `serve` writes the listening file with its SHA-256 once it listens.
//! When no `serve` of the new binary listens in time, the watchdog renames `<binary>.previous` back and restarts the
//! agent. The watchdog runs from the previous binary when that binary has the feature, else from the new one, which
//! passed its check.
//!
//! [`Request::Upgrade`]: yonder_link::proto::Request::Upgrade

use std::fs::{self, File, OpenOptions, Permissions};
use std::io::{self, Write};
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::sync::atomic::{AtomicU64, Ordering};
use std::thread;
use std::time::{Duration, Instant};

use serde::{Deserialize, Serialize};
use yonder_link::digest::{Sha256, sha256_file};
use yonder_link::proto::{MAX_BINARY, SCHEMA_VERSION, feature};

use crate::child;
use crate::files::write_private;
use crate::home::Home;
use crate::refused::{BAD_BINARY, LAUNCHD_FAILED, Refusal, UPGRADE_FAILED, refused};
use crate::setup::{launchctl, plist_program, service};

/// The deadline of the `version` call of a binary.
const SELF_CHECK: Duration = Duration::from_secs(10);

/// How often the watchdog reads the listening file.
const WATCH_EVERY: Duration = Duration::from_millis(200);

/// The mode of a staged binary: the agent user reads and runs it.
const BINARY_MODE: u32 = 0o700;

/// The count of the staged files of this process, so two stagings never share a name.
static STAGINGS: AtomicU64 = AtomicU64::new(0);

/// What `serve` writes into the listening file once it listens.
#[derive(Debug, PartialEq, Eq, Serialize, Deserialize)]
pub(crate) struct Listening {
    pub(crate) sha256: String,
    pub(crate) pid: u32,
    pub(crate) version: String,
}

/// What an upgrade did: the answer of the request and the line of the command.
#[derive(Debug, PartialEq, Eq, Serialize)]
pub(crate) struct Upgraded {
    pub(crate) previous_sha256: String,
    pub(crate) sha256: String,
    pub(crate) version: String,
}

/// Writes the listening file of a `serve` of the binary `sha256` with the pid of this process.
pub(crate) fn write_listening(home: &Home, sha256: &str) -> io::Result<()> {
    let listening = Listening {
        sha256: sha256.to_owned(),
        pid: std::process::id(),
        version: env!("CARGO_PKG_VERSION").to_owned(),
    };
    let json = serde_json::to_vec(&listening).map_err(io::Error::other)?;
    write_private(&home.listening_file(), &json)
}

/// The listening file, or `None` when it is missing or not of its shape.
pub(crate) fn read_listening(home: &Home) -> Option<Listening> {
    serde_json::from_slice(&fs::read(home.listening_file()).ok()?).ok()
}

/// Refuses an upgrade on a host without launchd: nothing would start the new binary there.
pub(crate) fn check_launchd() -> Result<(), Refusal> {
    if cfg!(target_os = "macos") {
        Ok(())
    } else {
        Err(refused(
            UPGRADE_FAILED,
            "the agent upgrades itself only under launchd on macOS; `yonder prepare` installs a new agent here",
        ))
    }
}

/// The binary that the LaunchAgent of `home` runs.
pub(crate) fn launchd_binary(home: &Home) -> Result<PathBuf, Refusal> {
    let plist = home.launch_agent_file();
    let text = fs::read_to_string(&plist).map_err(|error| {
        refused(
            UPGRADE_FAILED,
            format!(
                "cannot read the LaunchAgent {}: {error}; `yonder prepare` writes it",
                plist.display()
            ),
        )
    })?;
    plist_program(&text).ok_or_else(|| refused(UPGRADE_FAILED, format!("the LaunchAgent {} names no program", plist.display())))
}

/// The binary that an upgrade of `target` keeps, and that the watchdog restores.
pub(crate) fn previous_of(target: &Path) -> PathBuf {
    PathBuf::from(format!("{}.previous", target.display()))
}

/// A binary that arrives into a staged file beside its target. It hashes what it writes, and it drops the bytes after
/// `limit`, so a stream longer than its request still ends and [`Staged::check`] refuses it.
pub(crate) struct Staging {
    staged: Staged,
    file: File,
    digest: Sha256,
    written: u64,
    limit: u64,
}

impl Staging {
    /// A new staged file in the directory of `target`, with the mode 0700.
    pub(crate) fn create(target: &Path, limit: u64) -> Result<Self, Refusal> {
        let dir = target.parent().unwrap_or_else(|| Path::new("."));
        let name = target.file_name().map_or_else(|| "yonderd".into(), |name| name.to_string_lossy());
        let path = dir.join(format!(
            ".{name}.upgrade-{}-{}",
            std::process::id(),
            STAGINGS.fetch_add(1, Ordering::Relaxed)
        ));
        let file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .mode(BINARY_MODE)
            .open(&path)
            .and_then(|file| file.set_permissions(Permissions::from_mode(BINARY_MODE)).map(|()| file))
            .map_err(|error| {
                refused(
                    UPGRADE_FAILED,
                    format!("cannot stage the new binary at {}: {error}", path.display()),
                )
            })?;
        Ok(Self {
            staged: Staged {
                path,
                size: 0,
                sha256: String::new(),
            },
            file,
            digest: Sha256::default(),
            written: 0,
            limit,
        })
    }

    /// The staged file with its size and its SHA-256, once all bytes are on the disk.
    pub(crate) fn finish(mut self) -> Result<Staged, Refusal> {
        let path = self.staged.path.clone();
        self.file
            .sync_all()
            .map_err(|error| refused(UPGRADE_FAILED, format!("cannot write the new binary {}: {error}", path.display())))?;
        self.staged.size = self.written;
        self.staged.sha256 = self.digest.hex();
        Ok(self.staged)
    }
}

impl Write for Staging {
    fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
        let room = self.limit.saturating_sub(self.written);
        let kept = &bytes[..bytes.len().min(usize::try_from(room).unwrap_or(usize::MAX))];
        self.file.write_all(kept)?;
        self.digest.update(kept);
        self.written += bytes.len() as u64;
        Ok(bytes.len())
    }

    fn flush(&mut self) -> io::Result<()> {
        self.file.flush()
    }
}

/// The new binary in its staged file. The file goes when the value drops, unless the install renamed it.
#[derive(Debug)]
pub(crate) struct Staged {
    path: PathBuf,
    size: u64,
    sha256: String,
}

impl Staged {
    /// Refuses a binary whose size or SHA-256 is not the one of its request.
    pub(crate) fn check(&self, size: u64, sha256: &str) -> Result<(), Refusal> {
        if self.size != size {
            return Err(refused(
                BAD_BINARY,
                format!("the binary has {} bytes, not the {size} of the request", self.size),
            ));
        }
        if self.sha256 != sha256 {
            return Err(refused(
                BAD_BINARY,
                format!("the binary has the SHA-256 {}, not the {sha256} of the request", self.sha256),
            ));
        }
        Ok(())
    }
}

impl Staged {
    /// The path of the staged file for a rename. The file then stays when the value drops.
    fn into_path(mut self) -> PathBuf {
        std::mem::take(&mut self.path)
    }
}

impl Drop for Staged {
    fn drop(&mut self) {
        if !self.path.as_os_str().is_empty() {
            let _ = fs::remove_file(&self.path);
        }
    }
}

/// The `version` line of an agent binary.
#[derive(Deserialize)]
struct VersionLine {
    version: String,
    schema_version: u32,
    #[serde(default)]
    features: Vec<String>,
}

/// Runs `<binary> version` under its deadline, and answers the version. Refuses a binary that does not run here, that
/// speaks another schema, or that lacks [`feature::UPGRADE`], because no later upgrade could replace it.
pub(crate) fn self_check(binary: &Path) -> Result<String, Refusal> {
    let shown = binary.display();
    let output = child::run(Command::new(binary).arg("version"), SELF_CHECK, &child::never)
        .map_err(|error| refused(BAD_BINARY, format!("the binary does not run here: {error}")))?;
    if !output.status.success() {
        return Err(refused(
            BAD_BINARY,
            format!("`{shown} version` failed: {} ({})", output.diagnostic(), output.status),
        ));
    }
    let text = output.stdout_text();
    let line: VersionLine = text
        .lines()
        .rev()
        .find_map(|line| serde_json::from_str(line).ok())
        .ok_or_else(|| refused(BAD_BINARY, format!("`{shown} version` printed no version line: {text}")))?;
    if line.schema_version != SCHEMA_VERSION {
        return Err(refused(
            BAD_BINARY,
            format!(
                "the binary speaks the schema {}, not {SCHEMA_VERSION}; `yonder prepare` installs it",
                line.schema_version
            ),
        ));
    }
    if !line.features.iter().any(|known| known == feature::UPGRADE) {
        return Err(refused(
            BAD_BINARY,
            format!(
                "the binary {} has no feature {}, so no later upgrade could replace it; `yonder prepare` installs it",
                line.version,
                feature::UPGRADE
            ),
        ));
    }
    Ok(line.version)
}

/// The install of a staged binary over `target`, and the watchdog that checks it.
#[derive(Debug)]
pub(crate) struct Install<'a> {
    pub(crate) home: &'a Home,
    pub(crate) target: &'a Path,
    pub(crate) launchctl: &'a Path,
    /// How long the watchdog waits for a `serve` of the new binary.
    pub(crate) wait: Duration,
}

impl Install<'_> {
    /// Checks the staged binary, keeps `target` as its previous binary, renames the staged binary over `target`, and
    /// starts the watchdog. A watchdog that cannot start undoes the swap.
    pub(crate) fn run(&self, staged: Staged) -> Result<Upgraded, Refusal> {
        let version = self_check(&staged.path)?;
        let sha256 = staged.sha256.clone();
        let previous_sha256 = swap(self.target, staged)?;
        if let Err(refusal) = self.start_watchdog(&sha256) {
            let _ = restore(self.target);
            return Err(refusal);
        }
        Ok(Upgraded {
            previous_sha256,
            sha256,
            version,
        })
    }

    /// Starts `yonderd watchdog` in a session of its own, with its output in the log of the agent. The listening file
    /// goes first, so only a `serve` that starts later can write it.
    fn start_watchdog(&self, sha256: &str) -> Result<(), Refusal> {
        let failed = |error: io::Error| refused(UPGRADE_FAILED, format!("cannot start the watchdog of the upgrade: {error}"));
        match fs::remove_file(self.home.listening_file()) {
            Err(error) if error.kind() != io::ErrorKind::NotFound => return Err(failed(error)),
            _ => {}
        }
        let log_file = self.home.launchd_log_file();
        if let Some(dir) = log_file.parent() {
            fs::create_dir_all(dir).map_err(failed)?;
        }
        let log = OpenOptions::new().create(true).append(true).open(&log_file).map_err(failed)?;
        let previous = previous_of(self.target);
        let program = if self_check(&previous).is_ok() {
            previous
        } else {
            self.target.to_path_buf()
        };
        let mut command = Command::new(program);
        command
            .arg("watchdog")
            .arg(self.target)
            .arg("--sha256")
            .arg(sha256)
            .arg("--wait-ms")
            .arg(self.wait.as_millis().to_string())
            .arg("--launchctl")
            .arg(self.launchctl)
            .stdin(Stdio::null())
            .stdout(log.try_clone().map_err(failed)?)
            .stderr(log);
        child::detach(&mut command, None);
        let mut child = command.spawn().map_err(failed)?;
        // The thread reaps the watchdog when this process lives longer than it.
        thread::spawn(move || child.wait());
        Ok(())
    }
}

/// Keeps `target` as `<target>.previous`, through a link or a copy, and renames the staged binary over `target`. Answers
/// the SHA-256 of the previous binary.
fn swap(target: &Path, staged: Staged) -> Result<String, Refusal> {
    let failed = |what: &str, error: io::Error| refused(UPGRADE_FAILED, format!("cannot {what}: {error}"));
    let previous_sha256 = sha256_file(target).map_err(|error| failed(&format!("read the binary {}", target.display()), error))?;
    let previous = previous_of(target);
    let kept = PathBuf::from(format!("{}.tmp-{}", previous.display(), std::process::id()));
    let _ = fs::remove_file(&kept);
    fs::hard_link(target, &kept)
        .or_else(|_| fs::copy(target, &kept).map(drop))
        .and_then(|()| fs::rename(&kept, &previous))
        .map_err(|error| {
            let _ = fs::remove_file(&kept);
            failed(&format!("keep the binary as {}", previous.display()), error)
        })?;
    let staged = staged.into_path();
    fs::rename(&staged, target).map_err(|error| {
        let _ = fs::remove_file(&staged);
        failed(&format!("rename the new binary to {}", target.display()), error)
    })?;
    Ok(previous_sha256)
}

/// Renames `<target>.previous` back over `target`.
fn restore(target: &Path) -> io::Result<()> {
    fs::rename(previous_of(target), target)
}

/// How the watchdog ended.
#[derive(Debug, PartialEq, Eq)]
pub(crate) enum Watched {
    /// A `serve` of the new binary listens with this pid.
    Listened(u32),
    /// No `serve` of the new binary listened in time, and the previous binary runs again.
    RolledBack,
}

/// The watchdog of an upgrade of `target` to the binary `sha256`: it waits up to `wait` for the listening file of a
/// live `serve` of that binary. Else it restores the previous binary and restarts the agent through `launchctl`.
/// `say` takes each line for the log.
pub(crate) fn watchdog(
    home: &Home,
    target: &Path,
    sha256: &str,
    wait: Duration,
    launchctl_program: &Path,
    say: &mut dyn FnMut(&str),
) -> Result<Watched, Refusal> {
    let short = sha256.get(..12).unwrap_or(sha256);
    say(&format!("watchdog: waits {} s for a serve of the binary {short}", wait.as_secs()));
    let started = Instant::now();
    loop {
        if let Some(listening) = read_listening(home)
            && listening.sha256 == sha256
            && is_alive(listening.pid)
        {
            say(&format!("watchdog: the binary {short} listens with the pid {}", listening.pid));
            return Ok(Watched::Listened(listening.pid));
        }
        if started.elapsed() >= wait {
            break;
        }
        thread::sleep(WATCH_EVERY);
    }
    say(&format!(
        "watchdog: no serve of the binary {short} listened in {} s, so the previous binary comes back",
        wait.as_secs()
    ));
    restore(target).map_err(|error| {
        refused(
            UPGRADE_FAILED,
            format!("cannot rename {} to {}: {error}", previous_of(target).display(), target.display()),
        )
    })?;
    let (_, service) = service();
    launchctl(launchctl_program, &["kickstart", "-k", &service]).map_err(|message| refused(LAUNCHD_FAILED, message))?;
    say(&format!("watchdog: restored {} and restarted {service}", target.display()));
    Ok(Watched::RolledBack)
}

/// Whether a process of `pid` lives.
fn is_alive(pid: u32) -> bool {
    let Ok(pid) = libc::pid_t::try_from(pid) else {
        return false;
    };
    // SAFETY: kill with the signal 0 sends nothing, takes plain integers, and touches no memory of this process.
    unsafe { libc::kill(pid, 0) == 0 }
}

/// `yonderd upgrade <binary>`: installs `binary` over the binary of the LaunchAgent, starts the watchdog, and restarts
/// the agent.
pub(crate) fn upgrade_from_file(home: &Home, binary: &Path, launchctl_program: &Path, wait: Duration) -> Result<Upgraded, Refusal> {
    check_launchd()?;
    let target = launchd_binary(home)?;
    let unreadable = |error: io::Error| refused(BAD_BINARY, format!("cannot read {}: {error}", binary.display()));
    let mut source = File::open(binary).map_err(unreadable)?;
    let size = source.metadata().map_err(unreadable)?.len();
    if size > MAX_BINARY {
        return Err(refused(
            BAD_BINARY,
            format!("{} has {size} bytes, more than {MAX_BINARY}", binary.display()),
        ));
    }
    let mut staging = Staging::create(&target, MAX_BINARY)?;
    io::copy(&mut source, &mut staging).map_err(|error| {
        refused(
            UPGRADE_FAILED,
            format!("cannot copy {} to {}: {error}", binary.display(), target.display()),
        )
    })?;
    let staged = staging.finish()?;
    let upgraded = Install {
        home,
        target: &target,
        launchctl: launchctl_program,
        wait,
    }
    .run(staged)?;
    let (_, service) = service();
    launchctl(launchctl_program, &["kickstart", "-k", &service]).map_err(|message| refused(LAUNCHD_FAILED, message))?;
    Ok(upgraded)
}

#[cfg(test)]
mod tests;
