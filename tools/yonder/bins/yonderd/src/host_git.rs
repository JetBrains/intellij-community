//! The git of the host. The agent, bd and the commands of the runs use the git that the `PATH` finds first, and each
//! of them needs at least [`MINIMUM`].
//!
//! - At its start, before its first thread, `serve` puts the `bin` directory of Homebrew before `/usr/bin` in its own
//!   `PATH` ([`put_brew_first`]). So each child of the agent finds the git of Homebrew before the one of the system:
//!   git, bd, the supervisors, and through them the prepare steps and the commands.
//! - Then `serve` checks the git of its `PATH` ([`HostGit::settle`]). A git older than [`MINIMUM`] gets `brew install
//!   git` when Homebrew has no git, else `brew upgrade git`, on a thread of its own. While Homebrew works, `serve` holds
//!   the flock of `~/.cache/yonderd/git.lock`. A run waits for it before it borrows a slot, with a line in its log at the
//!   start and once a minute, and the keeper of the slots waits too. `~/.cache/yonderd/git.json` keeps the last state.
//! - A host without Homebrew refuses each start with `old_git`. The message names the install of Homebrew on macOS, and
//!   the package manager of the host elsewhere. A start after a refusal checks again, so a git that the user installed
//!   counts at once.
//! - A run puts the same directory before `/usr/bin` in the `PATH` of its login environment, checks the git of that
//!   `PATH` as well, and names its version in the line before the command.
//!
//! Homebrew is the tool of macOS only. Elsewhere the agent looks for no Homebrew. `YONDERD_TEST_BREW` names a fake
//! Homebrew for a test, and `none` names none. `YONDERD_TEST_GIT_MINIMUM` lowers or raises the minimum for a test.

use std::cmp::Ordering;
use std::collections::BTreeMap;
use std::env;
use std::ffi::{OsStr, OsString};
use std::fmt;
use std::fs::{self, File};
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::{Arc, Mutex, MutexGuard, PoisonError};
use std::thread;
use std::time::{Duration, Instant, SystemTime};

use serde::{Deserialize, Serialize};

use crate::child::{self, Stop, never};
use crate::files::{open_private, try_flock, try_flock_shared, write_private};
use crate::home::Home;
use crate::refused::{LOCK_TIMEOUT, OLD_GIT, Refusal, refused};
use crate::run::epoch_ms;
use crate::serve::log;

/// The lowest git of a host. It is the newest release of git when the user set the policy (2026-10-27): yonder runs
/// on hosts of any repository, and one floor that moves only on purpose is simpler than a check of each git feature
/// that the agent, bd and the commands use. Homebrew has the newest release, so `brew upgrade git` reaches it.
pub(crate) const MINIMUM: Version = Version::new(2, 56, 0);

/// The variable that names a fake Homebrew for a test, or `none`.
pub(crate) const TEST_BREW: &str = "YONDERD_TEST_BREW";

/// The variable that sets another minimum for a test, such as `0.0.0` or `99.0.0`.
pub(crate) const TEST_MINIMUM: &str = "YONDERD_TEST_GIT_MINIMUM";

/// The places of Homebrew on macOS beside the `PATH`: Apple silicon, then Intel.
const BREW_PLACES: [&str; 2] = ["/opt/homebrew/bin/brew", "/usr/local/bin/brew"];

/// The deadline of `git --version`.
const VERSION_DEADLINE: Duration = Duration::from_secs(30);

/// The deadline of `brew install git` and `brew upgrade git`: an update of Homebrew, a download and an install.
const BREW_DEADLINE: Duration = Duration::from_mins(30);

/// How long a run waits for an upgrade of git: the deadline of Homebrew and a minute.
const WAIT: Duration = Duration::from_mins(31);

/// How often a run that waits for an upgrade of git looks again.
const WAIT_POLL: Duration = Duration::from_secs(2);

/// How often the log of a run that waits for an upgrade of git says so again.
const WAIT_SAY_EVERY: Duration = Duration::from_secs(60);

/// How often a wait looks at its stop.
const STOP_POLL: Duration = Duration::from_millis(100);

/// The most bytes of the output of Homebrew in a refusal: its end, where Homebrew says what failed.
const BREW_OUTPUT_TAIL: usize = 2000;

/// A version of git: `2.56.0`, or `2.56.0-rc1` for a release candidate, which comes before its release.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Version {
    major: u32,
    minor: u32,
    patch: u32,
    rc: Option<u32>,
}

impl Version {
    pub(crate) const fn new(major: u32, minor: u32, patch: u32) -> Self {
        Self {
            major,
            minor,
            patch,
            rc: None,
        }
    }

    /// The version of a text such as `2.56.0`, `2.56.0.rc1`, `2.56.0-rc1` or `2.45.1.windows.1`. A part after the
    /// third number counts only when it names a release candidate.
    pub(crate) fn parse(text: &str) -> Option<Self> {
        let parts: Vec<&str> = text.split(['.', '-']).collect();
        let number = |index: usize| parts.get(index).and_then(|part| part.parse::<u32>().ok());
        let rc = parts
            .iter()
            .skip(2)
            .find_map(|part| part.strip_prefix("rc").and_then(|rest| rest.parse().ok()));
        Some(Self {
            major: number(0)?,
            minor: number(1)?,
            patch: number(2).unwrap_or(0),
            rc,
        })
    }

    /// The version of an answer of `git --version`: `git version 2.50.1 (Apple Git-155)`.
    pub(crate) fn of_answer(answer: &str) -> Option<Self> {
        Self::parse(answer.trim().strip_prefix("git version ")?.split_whitespace().next()?)
    }

    /// A release ranks after each of its release candidates.
    fn key(self) -> (u32, u32, u32, u32) {
        (self.major, self.minor, self.patch, self.rc.unwrap_or(u32::MAX))
    }
}

impl Ord for Version {
    fn cmp(&self, other: &Self) -> Ordering {
        self.key().cmp(&other.key())
    }
}

impl PartialOrd for Version {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl fmt::Display for Version {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}.{}.{}", self.major, self.minor, self.patch)?;
        if let Some(rc) = self.rc {
            write!(f, "-rc{rc}")?;
        }
        Ok(())
    }
}

/// The minimum of this process: the one of [`TEST_MINIMUM`], else [`MINIMUM`].
pub(crate) fn minimum() -> Version {
    env::var(TEST_MINIMUM)
        .ok()
        .and_then(|text| Version::parse(&text))
        .unwrap_or(MINIMUM)
}

/// The git that a `PATH` finds first.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Found {
    pub(crate) path: PathBuf,
    /// The version of its answer, `None` when the answer names none.
    pub(crate) version: Option<Version>,
    /// Its answer without `git version `, such as `2.50.1 (Apple Git-155)`, or why it gave none.
    pub(crate) text: String,
}

impl Found {
    fn is_new_enough(&self, minimum: Version) -> bool {
        self.version.is_some_and(|version| version >= minimum)
    }
}

impl fmt::Display for Found {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "git {} at {}", self.text, self.path.display())
    }
}

/// The git that `path` finds first, with its version. `None` when no directory of `path` has a git.
pub(crate) fn find(path: &OsStr) -> Option<Found> {
    let git = which("git", path)?;
    let answer = child::run(Command::new(&git).arg("--version"), VERSION_DEADLINE, &never);
    let (version, text) = match answer {
        Ok(output) if output.status.success() => {
            let answer = output.stdout_text();
            let text = answer.strip_prefix("git version ").unwrap_or(&answer).to_owned();
            (Version::of_answer(&answer), text)
        }
        Ok(output) => (None, format!("that fails `--version`: {} ({})", output.diagnostic(), output.status)),
        Err(error) => (None, format!("that fails `--version`: {error}")),
    };
    Some(Found { path: git, version, text })
}

/// The first executable file `name` in a directory of `path`. An empty entry counts as none.
pub(crate) fn which(name: &str, path: &OsStr) -> Option<PathBuf> {
    env::split_paths(path)
        .filter(|dir| !dir.as_os_str().is_empty())
        .map(|dir| dir.join(name))
        .find(|candidate| is_executable(candidate))
}

fn is_executable(path: &Path) -> bool {
    fs::metadata(path).is_ok_and(|metadata| metadata.is_file() && metadata.permissions().mode() & 0o111 != 0)
}

/// The `brew` program: the one of [`TEST_BREW`], else on macOS the first of `path`, else one of [`BREW_PLACES`].
pub(crate) fn brew(path: &OsStr) -> Option<PathBuf> {
    if let Some(named) = env::var_os(TEST_BREW) {
        return (!named.is_empty() && named != "none").then(|| PathBuf::from(named));
    }
    if !cfg!(target_os = "macos") {
        return None;
    }
    which("brew", path).or_else(|| BREW_PLACES.iter().map(PathBuf::from).find(|place| is_executable(place)))
}

/// The directory of the programs of Homebrew, `$(brew --prefix)/bin`: Homebrew keeps `brew` itself there.
fn brew_bin(brew: &Path) -> Option<&Path> {
    brew.parent().filter(|bin| !bin.as_os_str().is_empty())
}

/// `path` with `dir` before `/usr/bin`. A `path` that has `dir` before `/usr/bin` already stays as it is. Else each
/// `dir` goes, and one `dir` comes right before the first `/usr/bin`, or at the end when there is none.
pub(crate) fn with_first(path: &OsStr, dir: &Path) -> OsString {
    if path.is_empty() {
        return dir.as_os_str().to_owned();
    }
    let system = Path::new("/usr/bin");
    let entries: Vec<PathBuf> = env::split_paths(path).collect();
    let first_system = entries.iter().position(|entry| entry == system);
    let first_own = entries.iter().position(|entry| entry == dir);
    if first_own.is_some_and(|own| first_system.is_none_or(|system| own < system)) {
        return path.to_owned();
    }
    let mut entries: Vec<PathBuf> = entries.into_iter().filter(|entry| entry != dir).collect();
    let at = entries.iter().position(|entry| entry == system).unwrap_or(entries.len());
    entries.insert(at, dir.to_path_buf());
    env::join_paths(entries).unwrap_or_else(|_| path.to_owned())
}

/// Puts the `bin` directory of Homebrew before `/usr/bin` in the `PATH` of this process, so each child finds the git of
/// Homebrew first. A host without Homebrew keeps its `PATH`.
///
/// # Safety
///
/// No other thread may read or write the environment during the call: `serve` calls it before its first thread.
pub(crate) unsafe fn put_brew_first() {
    let path = env::var_os("PATH").unwrap_or_default();
    let Some(brew) = brew(&path) else {
        return;
    };
    let Some(bin) = brew_bin(&brew) else {
        return;
    };
    let first = with_first(&path, bin);
    if first != path {
        // SAFETY: the caller makes sure that no other thread uses the environment.
        unsafe { env::set_var("PATH", first) };
    }
}

/// The environment of a run with the `bin` directory of Homebrew before `/usr/bin` in its `PATH`, as in the `PATH` of
/// the agent. A login shell can put `/usr/bin` first again.
pub(crate) fn put_brew_first_in(run_env: &mut BTreeMap<OsString, OsString>) {
    let Some(brew) = brew(&env::var_os("PATH").unwrap_or_default()) else {
        return;
    };
    let Some(bin) = brew_bin(&brew) else {
        return;
    };
    let path = run_env.get(OsStr::new("PATH")).cloned().unwrap_or_default();
    run_env.insert(OsString::from("PATH"), with_first(&path, bin));
}

/// The git that the command of a run gets, for the line before the command: `git 2.56.0`. A git older than the minimum
/// refuses the run with `old_git`.
pub(crate) fn for_command(run_env: &BTreeMap<OsString, OsString>) -> Result<String, Refusal> {
    let path = run_env.get(OsStr::new("PATH")).cloned().unwrap_or_default();
    let minimum = minimum();
    match find(&path) {
        Some(found) if found.is_new_enough(minimum) => Ok(format!("git {}", found.text)),
        Some(found) => Err(refused(
            OLD_GIT,
            format!(
                "the PATH of the run finds {found}, older than {minimum}, the minimum of yonder. Put a newer git first in the login PATH of the host"
            ),
        )),
        None => Err(refused(
            OLD_GIT,
            format!("the PATH of the run finds no git: {}", path.to_string_lossy()),
        )),
    }
}

/// What the agent does about the git of its `PATH`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum Plan {
    /// The git is new enough.
    Ready,
    /// `brew install git`: Homebrew has no git yet.
    Install,
    /// `brew upgrade git`: the git of Homebrew is too old.
    Upgrade,
    /// The agent cannot get a new git. The message says what the user does.
    Refuse(String),
}

impl Plan {
    /// The arguments of `brew` for an install or an upgrade.
    const fn brew_args(&self) -> Option<[&'static str; 2]> {
        match self {
            Self::Install => Some(["install", "git"]),
            Self::Upgrade => Some(["upgrade", "git"]),
            Self::Ready | Self::Refuse(_) => None,
        }
    }
}

/// The plan for the git `found` that the `PATH` finds first, with the `brew` program and whether the `bin` directory of
/// Homebrew has a git. `macos` selects the message of a host without Homebrew.
pub(crate) fn plan(found: Option<&Found>, minimum: Version, brew: Option<&Path>, brew_has_git: bool, macos: bool) -> Plan {
    if found.is_some_and(|found| found.is_new_enough(minimum)) {
        return Plan::Ready;
    }
    let what = found.map_or_else(|| "missing: the PATH of the agent finds no git".to_owned(), ToString::to_string);
    let Some(brew) = brew else {
        return Plan::Refuse(if macos {
            format!(
                "the git of this host is {what}, older than {minimum}, the minimum of yonder. The agent upgrades git with Homebrew, and this host has no Homebrew. At the host, install Homebrew once (it asks for the password of an administrator): /bin/bash -c \"$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\". Then run `brew install git`"
            )
        } else {
            format!(
                "the git of this host is {what}, older than {minimum}, the minimum of yonder. Install git {minimum} or later with the package manager of the host, so that the PATH of the agent finds it first"
            )
        });
    };
    let brew_git = brew_bin(brew).map(|bin| bin.join("git"));
    match found {
        Some(found) if brew_has_git && brew_git.as_ref() != Some(&found.path) => Plan::Refuse(format!(
            "the git of this host is {what}, older than {minimum}, the minimum of yonder, and the PATH of the agent finds it before the git of Homebrew in {}. Remove it, or put a newer git first",
            brew_bin(brew).unwrap_or(brew).display()
        )),
        _ if brew_has_git => Plan::Upgrade,
        _ => Plan::Install,
    }
}

/// The state of the git of the host in `serve`.
#[derive(Clone, Debug, PartialEq, Eq)]
enum State {
    /// `serve` did not check yet, as an agent of a unit test: a start goes on.
    Unchecked,
    Ready,
    /// Homebrew installs or upgrades git.
    Upgrading,
    /// The message of the refusal.
    Refused(String),
}

/// `~/.cache/yonderd/git.json`: the last state, for the runs.
#[derive(Debug, Serialize, Deserialize)]
struct StateFile {
    /// `ready`, `upgrading` or `refused`.
    state: String,
    /// The git of a ready state, the command of Homebrew of an upgrade, the message of a refusal.
    message: String,
    at_ms: u64,
}

/// The git of the host, as `serve` checks and upgrades it.
#[derive(Debug)]
pub(crate) struct HostGit {
    home: Home,
    state: Arc<Mutex<State>>,
}

impl HostGit {
    /// The git of an agent that did not check it yet.
    pub(crate) fn new(home: Home) -> Self {
        Self {
            home,
            state: Arc::new(Mutex::new(State::Unchecked)),
        }
    }

    fn lock(&self) -> MutexGuard<'_, State> {
        self.state.lock().unwrap_or_else(PoisonError::into_inner)
    }

    /// Checks the git of the `PATH` of the agent and acts on the plan: an install or an upgrade starts on a thread of
    /// its own. An upgrade that goes on stays.
    pub(crate) fn settle(&self) {
        let mut state = self.lock();
        if *state == State::Upgrading {
            return;
        }
        let path = env::var_os("PATH").unwrap_or_default();
        let found = find(&path);
        let brew = brew(&path);
        let brew_has_git = brew
            .as_deref()
            .and_then(brew_bin)
            .is_some_and(|bin| is_executable(&bin.join("git")));
        let plan = plan(found.as_ref(), minimum(), brew.as_deref(), brew_has_git, cfg!(target_os = "macos"));
        if let Plan::Refuse(message) = &plan {
            if !matches!(&*state, State::Refused(said) if said == message) {
                log(message);
            }
            write_state(&self.home, "refused", message);
            *state = State::Refused(message.clone());
            return;
        }
        let what = found.map_or_else(|| "missing".to_owned(), |found| found.to_string());
        let (Some(args), Some(brew)) = (plan.brew_args(), brew) else {
            if *state != State::Ready {
                log(&format!("the git of the host is {what}"));
            }
            write_state(&self.home, "ready", &what);
            *state = State::Ready;
            return;
        };
        let command = format!("brew {}", args.join(" "));
        log(&format!("the git of the host is {what}, older than {}: {command}", minimum()));
        write_state(&self.home, "upgrading", &command);
        *state = State::Upgrading;
        let lock = hold_lock(&self.home);
        let (home, shared) = (self.home.clone(), Arc::clone(&self.state));
        thread::spawn(move || {
            let next = match upgrade(&brew, args, &command) {
                Ok(found) => {
                    log(&format!("{command} ended: the git of the host is {found}"));
                    write_state(&home, "ready", &found.to_string());
                    State::Ready
                }
                Err(message) => {
                    log(&format!("cannot upgrade the git of the host: {message}"));
                    write_state(&home, "refused", &message);
                    State::Refused(message)
                }
            };
            *shared.lock().unwrap_or_else(PoisonError::into_inner) = next;
            drop(lock);
        });
    }

    /// Whether a run can start: `old_git` while the last check refused, after one more check. A run that starts during
    /// an upgrade waits for it in its supervisor.
    pub(crate) fn check(&self) -> Result<(), Refusal> {
        if matches!(*self.lock(), State::Refused(_)) {
            self.settle();
        }
        match &*self.lock() {
            State::Refused(message) => Err(refused(OLD_GIT, message.clone())),
            State::Unchecked | State::Ready | State::Upgrading => Ok(()),
        }
    }
}

/// Runs `brew <args>`, then answers the git of the `PATH` when it is new enough, else the message of the refusal.
fn upgrade(brew: &Path, args: [&str; 2], command: &str) -> Result<Found, String> {
    let mut call = Command::new(brew);
    // Each run of Homebrew updates its formulae first, so the upgrade sees the newest git.
    call.args(args).current_dir("/").env("HOMEBREW_AUTO_UPDATE_SECS", "0");
    let output = child::run(&mut call, BREW_DEADLINE, &never).map_err(|error| format!("`{command}` failed: {error}"))?;
    if !output.status.success() {
        let said = output.diagnostic();
        let tail = said
            .char_indices()
            .rev()
            .nth(BREW_OUTPUT_TAIL)
            .map_or(said.as_str(), |(at, _)| &said[at..]);
        return Err(format!("`{command}` failed ({}): {tail}", output.status));
    }
    let minimum = minimum();
    match find(&env::var_os("PATH").unwrap_or_default()) {
        Some(found) if found.is_new_enough(minimum) => Ok(found),
        Some(found) => Err(format!(
            "`{command}` ended, and the PATH of the agent still finds {found}, older than {minimum}, the minimum of yonder: Homebrew has no newer git, or another git comes first"
        )),
        None => Err(format!("`{command}` ended, and the PATH of the agent still finds no git")),
    }
}

fn write_state(home: &Home, state: &str, message: &str) {
    let file = StateFile {
        state: state.to_owned(),
        message: message.to_owned(),
        at_ms: epoch_ms(SystemTime::now()),
    };
    let written = serde_json::to_vec(&file)
        .map_err(std::io::Error::other)
        .and_then(|json| write_private(&home.git_file(), &json));
    if let Err(error) = written {
        log(&format!("cannot write {}: {error}", home.git_file().display()));
    }
}

fn read_state(home: &Home) -> Option<StateFile> {
    serde_json::from_slice(&fs::read(home.git_file()).ok()?).ok()
}

/// The flock of an upgrade, which the runs and the keeper see. A lock that cannot be taken is a line of the log: the
/// upgrade goes on, and only the runs do not wait for it.
fn hold_lock(home: &Home) -> Option<File> {
    let path = home.git_lock_file();
    match open_private(&path).and_then(|file| try_flock(&file).map(|taken| taken.then_some(file))) {
        Ok(Some(file)) => Some(file),
        Ok(None) => {
            log(&format!("another process holds {}", path.display()));
            None
        }
        Err(error) => {
            log(&format!("cannot lock {}: {error}", path.display()));
            None
        }
    }
}

/// Whether `serve` upgrades git now: another process holds the flock of `git.lock`.
pub(crate) fn upgrading(home: &Home) -> bool {
    File::open(home.git_lock_file()).is_ok_and(|file| matches!(try_flock_shared(&file), Ok(false)))
}

/// Waits while `serve` upgrades git: a line in the log of the run at the start and once a minute, `lock_timeout` after
/// [`WAIT`]. Then a refused upgrade refuses the run with `old_git`. Answers `false` when `stop` ended the wait.
pub(crate) fn wait(home: &Home, stop: Stop<'_>, say: &mut dyn FnMut(&str)) -> Result<bool, Refusal> {
    let started = Instant::now();
    let mut said: Option<Instant> = None;
    while upgrading(home) {
        if said.is_none_or(|at| at.elapsed() >= WAIT_SAY_EVERY) {
            let command = read_state(home)
                .filter(|file| file.state == "upgrading")
                .map_or_else(|| "Homebrew".to_owned(), |file| format!("`{}`", file.message));
            say(&format!(
                "waiting for the upgrade of git on the host: {command} runs since {} s",
                started.elapsed().as_secs()
            ));
            said = Some(Instant::now());
        }
        if started.elapsed() >= WAIT {
            return Err(refused(
                LOCK_TIMEOUT,
                format!("the upgrade of git on the host did not end in {} s; giving up", WAIT.as_secs()),
            ));
        }
        let next = Instant::now() + WAIT_POLL;
        while Instant::now() < next {
            if stop() {
                return Ok(false);
            }
            thread::sleep(STOP_POLL);
        }
    }
    match read_state(home) {
        Some(file) if file.state == "refused" => Err(refused(OLD_GIT, file.message)),
        _ => Ok(true),
    }
}

#[cfg(test)]
mod tests;
