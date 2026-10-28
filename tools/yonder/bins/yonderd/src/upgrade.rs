//! The upgrade of the agent binary without ssh: [`Request::Upgrade`] of a client. `serve` stages the binary of the byte
//! stream, installs it with [`Install::run`], answers, waits for its other requests, and exits. launchd then starts the
//! new binary, because the LaunchAgent has `KeepAlive`.
//!
//! The install refuses while the watchdog of an earlier upgrade lives. It stages the new binary beside the binary of the
//! LaunchAgent and checks it with `<staged> check --plist <plist>`: the schema, the feature, and the LaunchAgent that the
//! new binary would write, which must be the one on the host. It refuses a lower version without `force`. It keeps the
//! current binary as `<binary>.previous`, and renames the staged binary over the binary. Then it removes the listening
//! file and starts `yonderd watchdog` in a session of its own. `serve` writes the listening file with its SHA-256 once it
//! listens. When no `serve` of the new binary listens in time, the watchdog renames `<binary>.previous` back, restarts
//! the agent, and checks that the port takes connections again. It writes its outcome to `upgrade.json`. The watchdog
//! runs from the previous binary when that binary has the feature, else from the new one, which passed its check.
//!
//! The upgrade never changes the identity, the allow list or the LaunchAgent: those stay with `setup`.
//!
//! [`Request::Upgrade`]: yonder_link::proto::Request::Upgrade

use std::ffi::OsStr;
use std::fs::{self, File, OpenOptions, Permissions};
use std::io::{self, Write};
use std::net::{Ipv4Addr, SocketAddr, TcpStream};
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::sync::atomic::{AtomicU64, Ordering};
use std::thread;
use std::time::{Duration, Instant, SystemTime};

use serde::{Deserialize, Serialize};
use yonder_link::digest::{Sha256, sha256_file};
use yonder_link::proto::{SCHEMA_VERSION, feature};

use crate::child;
use crate::files::{open_private, private_dir, try_flock, try_flock_shared, write_private};
use crate::home::Home;
use crate::refused::{BAD_BINARY, DOWNGRADE, LAUNCHD_FAILED, PLIST_CHANGED, Refusal, UPGRADE_FAILED, refused};
use crate::run::epoch_ms;
use crate::setup::{PlistValues, launchctl, service};

/// The deadline of the `version` call of a binary.
const SELF_CHECK: Duration = Duration::from_secs(10);

/// How often the watchdog reads the listening file.
const WATCH_EVERY: Duration = Duration::from_millis(200);

/// How long a new watchdog waits for the flock of the one before it.
const LOCK_WAIT: Duration = Duration::from_secs(5);

/// The connect deadline of a check of the port of the agent.
const CONNECT: Duration = Duration::from_secs(1);

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

/// What an upgrade did: the answer of the request.
#[derive(Debug, PartialEq, Eq)]
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

/// The values of the LaunchAgent of `home`: the binary that launchd runs, and its port.
pub(crate) fn launch_agent(home: &Home) -> Result<PlistValues, Refusal> {
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
    PlistValues::parse(&text).ok_or_else(|| {
        refused(
            UPGRADE_FAILED,
            format!(
                "the LaunchAgent {} is not of the shape that `setup` writes; `yonder prepare` writes it",
                plist.display()
            ),
        )
    })
}

/// Whether this build writes the LaunchAgent `plist` as it is: `same`, `changed`, or `unknown` for a file that is missing
/// or not of the shape of `setup`. `yonderd check` prints it for the install of a newer agent.
pub(crate) fn plist_state(plist: &Path) -> &'static str {
    match fs::read_to_string(plist)
        .ok()
        .and_then(|text| PlistValues::parse(&text).map(|values| (values, text)))
    {
        Some((values, text)) if values.text() == text => "same",
        Some(_) => "changed",
        None => "unknown",
    }
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

/// The line of `yonderd version` or `yonderd check` of an agent binary.
#[derive(Deserialize)]
struct VersionLine {
    version: String,
    schema_version: u32,
    #[serde(default)]
    features: Vec<String>,
    /// [`plist_state`], from `check` only.
    #[serde(default)]
    plist: Option<String>,
}

/// Runs `<binary> <args>` under the deadline of a check, and answers its JSON line.
fn version_line(binary: &Path, args: &[&OsStr]) -> Result<VersionLine, Refusal> {
    let shown = format!(
        "{} {}",
        binary.display(),
        args.iter().map(|arg| arg.to_string_lossy()).collect::<Vec<_>>().join(" ")
    );
    let output = child::run(Command::new(binary).args(args), SELF_CHECK, &child::never)
        .map_err(|error| refused(BAD_BINARY, format!("the binary does not run here: {error}")))?;
    if !output.status.success() {
        return Err(refused(
            BAD_BINARY,
            format!("`{shown}` failed: {} ({})", output.diagnostic(), output.status),
        ));
    }
    let text = output.stdout_text();
    text.lines()
        .rev()
        .find_map(|line| serde_json::from_str(line).ok())
        .ok_or_else(|| refused(BAD_BINARY, format!("`{shown}` printed no version line: {text}")))
}

/// Whether `binary` answers `version` with [`feature::UPGRADE`], so it can run the watchdog.
fn can_watch(binary: &Path) -> bool {
    version_line(binary, &[OsStr::new("version")]).is_ok_and(|line| line.features.iter().any(|known| known == feature::UPGRADE))
}

/// Checks a new binary with `<binary> check --plist <plist>`, and answers its version. Refuses a binary that does not run
/// here, that speaks another schema, that lacks [`feature::UPGRADE`], because no later upgrade could replace it, or that
/// would write another LaunchAgent.
pub(crate) fn check_new(binary: &Path, plist: &Path) -> Result<String, Refusal> {
    let line = version_line(binary, &[OsStr::new("check"), OsStr::new("--plist"), plist.as_os_str()])?;
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
    if line.plist.as_deref() != Some("same") {
        return Err(refused(
            PLIST_CHANGED,
            format!(
                "the binary {} writes another LaunchAgent than {}; `yonder prepare` installs it",
                line.version,
                plist.display()
            ),
        ));
    }
    Ok(line.version)
}

/// Whether the version `new` is lower than `current`. A version that is not three numbers, or `0.0.0` of a build that
/// names no version, is not ordered.
fn is_lower(new: &str, current: &str) -> bool {
    let numbers = |version: &str| -> Option<Vec<u64>> {
        let numbers: Option<Vec<u64>> = version.split('.').map(|part| part.parse().ok()).collect();
        numbers.filter(|numbers| numbers.len() == 3 && numbers.iter().any(|number| *number > 0))
    };
    matches!((numbers(new), numbers(current)), (Some(new), Some(current)) if new < current)
}

/// Refuses while the watchdog of an earlier upgrade holds its flock.
fn check_no_watchdog(home: &Home) -> Result<(), Refusal> {
    let failed = |error: io::Error| refused(UPGRADE_FAILED, format!("cannot probe the watchdog lock: {error}"));
    private_dir(&home.cache_dir()).map_err(failed)?;
    let lock = open_private(&home.upgrade_lock_file()).map_err(failed)?;
    if try_flock_shared(&lock).map_err(failed)? {
        Ok(())
    } else {
        Err(refused(
            UPGRADE_FAILED,
            format!(
                "the watchdog of an earlier upgrade still waits; {} has its outcome when it ends",
                home.upgrade_file().display()
            ),
        ))
    }
}

/// The install of a staged binary over `target`, and the watchdog that checks it.
#[derive(Debug)]
pub(crate) struct Install<'a> {
    pub(crate) home: &'a Home,
    pub(crate) target: &'a Path,
    pub(crate) launchctl: &'a Path,
    /// How long the watchdog waits for a `serve` of the new binary.
    pub(crate) wait: Duration,
    /// Take a binary of a lower version too.
    pub(crate) force: bool,
}

impl Install<'_> {
    /// Checks the staged binary, keeps `target` as its previous binary, renames the staged binary over `target`, and
    /// starts the watchdog. A watchdog that cannot start undoes the swap.
    pub(crate) fn run(&self, staged: Staged) -> Result<Upgraded, Refusal> {
        check_no_watchdog(self.home)?;
        let version = check_new(&staged.path, &self.home.launch_agent_file())?;
        if !self.force
            && let Ok(current) = version_line(self.target, &[OsStr::new("version")])
            && is_lower(&version, &current.version)
        {
            return Err(refused(
                DOWNGRADE,
                format!(
                    "the binary {version} is older than the agent {}; `--force` installs it",
                    current.version
                ),
            ));
        }
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
        let program = if can_watch(&previous) {
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
    /// No `serve` of the new binary listened in time, and the previous binary is back. `listens` tells whether the port
    /// took connections again within the wait.
    RolledBack { listens: bool },
}

/// The outcome of a watchdog in `upgrade.json`.
#[derive(Debug, PartialEq, Eq, Serialize, Deserialize)]
pub(crate) struct Outcome {
    pub(crate) sha256: String,
    /// `listened`, `rolled_back`, or `rolled_back_not_listening`.
    pub(crate) outcome: String,
    pub(crate) at_ms: u64,
}

/// The flock that a watchdog holds while it lives, so a later upgrade refuses. The one of an earlier watchdog gets up
/// to [`LOCK_WAIT`].
fn hold_watch_lock(home: &Home) -> Result<File, Refusal> {
    let failed = |error: io::Error| refused(UPGRADE_FAILED, format!("cannot take the watchdog lock: {error}"));
    private_dir(&home.cache_dir()).map_err(failed)?;
    let lock = open_private(&home.upgrade_lock_file()).map_err(failed)?;
    let started = Instant::now();
    while !try_flock(&lock).map_err(failed)? {
        if started.elapsed() >= LOCK_WAIT {
            return Err(refused(UPGRADE_FAILED, "the watchdog of an earlier upgrade still waits"));
        }
        thread::sleep(WATCH_EVERY);
    }
    Ok(lock)
}

/// Whether the agent takes a TCP connection on `port` of the loopback address. `None`, a LaunchAgent without a port,
/// counts as yes.
fn takes_connections(port: Option<u16>) -> bool {
    port.is_none_or(|port| TcpStream::connect_timeout(&SocketAddr::from((Ipv4Addr::LOCALHOST, port)), CONNECT).is_ok())
}

/// The watchdog of an upgrade of `target` to the binary `sha256`: it waits up to `wait` for the listening file of a
/// live `serve` of that binary whose port takes connections. Else it restores the previous binary, restarts the agent
/// through `launchctl`, and waits up to `wait` for the port again. It writes its outcome to `upgrade.json`. `say` takes
/// each line for the log.
pub(crate) fn watchdog(
    home: &Home,
    target: &Path,
    sha256: &str,
    wait: Duration,
    launchctl_program: &Path,
    say: &mut dyn FnMut(&str),
) -> Result<Watched, Refusal> {
    let _lock = hold_watch_lock(home)?;
    let port = launch_agent(home).ok().map(|values| values.port);
    let short = sha256.get(..12).unwrap_or(sha256);
    let outcome = |outcome: &str| {
        let outcome = Outcome {
            sha256: sha256.to_owned(),
            outcome: outcome.to_owned(),
            at_ms: epoch_ms(SystemTime::now()),
        };
        serde_json::to_vec(&outcome)
            .map_err(io::Error::other)
            .and_then(|json| write_private(&home.upgrade_file(), &json))
    };
    say(&format!("watchdog: waits {} s for a serve of the binary {short}", wait.as_secs()));
    let started = Instant::now();
    loop {
        if let Some(listening) = read_listening(home)
            && listening.sha256 == sha256
            && is_alive(listening.pid)
            && takes_connections(port)
        {
            say(&format!("watchdog: the binary {short} listens with the pid {}", listening.pid));
            if let Err(error) = outcome("listened") {
                say(&format!("watchdog: cannot write {}: {error}", home.upgrade_file().display()));
            }
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
    let restarted = Instant::now();
    let listens = loop {
        if takes_connections(port) {
            break true;
        }
        if restarted.elapsed() >= wait {
            break false;
        }
        thread::sleep(WATCH_EVERY);
    };
    say(&if listens {
        "watchdog: the previous agent takes connections again".to_owned()
    } else {
        format!("watchdog: the previous agent does not take connections after {} s", wait.as_secs())
    });
    let written = outcome(if listens { "rolled_back" } else { "rolled_back_not_listening" });
    if let Err(error) = written {
        say(&format!("watchdog: cannot write {}: {error}", home.upgrade_file().display()));
    }
    Ok(Watched::RolledBack { listens })
}

/// Whether a process of `pid` lives.
fn is_alive(pid: u32) -> bool {
    let Ok(pid) = libc::pid_t::try_from(pid) else {
        return false;
    };
    // SAFETY: kill with the signal 0 sends nothing, takes plain integers, and touches no memory of this process.
    unsafe { libc::kill(pid, 0) == 0 }
}

#[cfg(test)]
mod tests;
