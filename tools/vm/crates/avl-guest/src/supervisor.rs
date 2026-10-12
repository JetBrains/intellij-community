//! The run supervisor: one long-lived process slot in the guest, owned from the host.
//!
//! A run is a directory under `--root` holding a spec, a state file and the child's log. One `active.json` per
//! root is the whole mutual exclusion, claimed with an exclusive create plus a link, so two supervisors racing for
//! the slot cannot both win.
//!
//! Every signal is identity-checked first. A pid alone is not an identity - it is reused - so a pid is only ever
//! signalled when its process-group id and its start time still match what was recorded. The cost of getting that
//! wrong is killing an unrelated process on a shared worker.
//!
//! The wire this half writes is declared in `avl_wire::supervisor`, and nothing here restates any of it.

mod identity;
mod launch;
mod log;
mod state;
mod supervise;

#[cfg(test)]
mod tests;

use std::fs::{self, File, OpenOptions};
use std::io::{self, Read, Write};
use std::os::unix::fs::{DirBuilderExt, OpenOptionsExt};
use std::path::{Path, PathBuf};
use std::thread;
use std::time::Duration;

use avl_wire::supervisor::{
    ActiveReply, CancellationRecord, CancellationRequest, Contract, LogReply, Outcome, Phase, RunState, SCHEMA_VERSION, Spec,
};
use avl_wire::verb::AgentVerb;
use jiff::Timestamp;
use nix::sys::signal::Signal;
use sha2::{Digest, Sha256};

use crate::cli::{CancelArgs, LogArgs, RootArgs, StartArgs};
use crate::clock::stamp;
use crate::reply::{self, AgentRefusal, Streams};

pub(crate) use launch::LaunchHost;
pub(crate) use supervise::supervise;

use crate::reply::AgentRefusalExt;
use identity::{ProcessIdentity, identity_matches, supervisor_is_alive};
use state::{FinishExtra, RunPaths, finish_state, prepare_root, read_state, reconcile};

pub(crate) const POLL_INTERVAL: Duration = Duration::from_millis(50);
pub(crate) const START_TIMEOUT: Duration = Duration::from_secs(15);
pub(crate) const FINISH_TIMEOUT: Duration = Duration::from_secs(10);
/// The cancel client and the detached supervisor are scheduled independently. Leave time for the supervisor's
/// final fsync and rename after its own grace and process-group deadlines expire.
pub(crate) const CANCEL_HANDOFF_TIMEOUT: Duration = Duration::from_secs(5);

/// The processes and the clock the supervisor observes, as one seam, so the reconciler's branches are tested
/// against a scripted world instead of real processes and real sleeps.
pub(crate) trait System {
    /// The identity of `pid`, or `None` when there is no such process.
    fn identity(&self, pid: i32) -> Option<ProcessIdentity>;
    /// Whether any process of `pgid` is still there.
    fn group_alive(&self, pgid: i32) -> bool;
    fn signal_group(&self, pgid: i32, signal: Signal);
    fn now(&self) -> Timestamp;
    fn sleep(&self, duration: Duration);
}

/// The real processes and the real clock.
pub(crate) struct LiveSystem;

impl System for LiveSystem {
    fn identity(&self, pid: i32) -> Option<ProcessIdentity> {
        identity::read_identity(pid)
    }

    fn group_alive(&self, pgid: i32) -> bool {
        identity::group_alive(pgid)
    }

    fn signal_group(&self, pgid: i32, signal: Signal) {
        identity::signal_group(pgid, signal);
    }

    fn now(&self) -> Timestamp {
        Timestamp::now()
    }

    fn sleep(&self, duration: Duration) {
        thread::sleep(duration);
    }
}

/// The time `duration` from now on `system`'s clock.
pub(crate) fn deadline(system: &dyn System, duration: Duration) -> Timestamp {
    let now = system.now();
    now.checked_add(duration).unwrap_or(Timestamp::MAX)
}

/// Waits until `pid` has an identity, or `None` at the deadline.
fn wait_for_identity(system: &dyn System, pid: i32, within: Duration) -> Option<ProcessIdentity> {
    let until = deadline(system, within);
    while system.now() < until {
        if let Some(identity) = system.identity(pid) {
            return Some(identity);
        }
        system.sleep(POLL_INTERVAL);
    }
    None
}

// --- start ---------------------------------------------------------------------------------------------------

/// What `start` spawns the supervisor with: this binary and the launch host it runs on. Gathered once, so a test
/// can hand `start` another binary and another host.
pub(crate) struct Launcher {
    pub self_exe: PathBuf,
    pub host: LaunchHost,
}

impl Launcher {
    pub(crate) fn current() -> Self {
        Self {
            // An unreadable executable path surfaces as a spawn failure naming the empty path.
            self_exe: std::env::current_exe().unwrap_or_default(),
            host: LaunchHost::current(),
        }
    }
}

/// Creates the run, launches its detached supervisor, and waits until the child is running (or already done).
pub(crate) fn start(system: &dyn System, launcher: &Launcher, args: &StartArgs) -> Result<RunState, AgentRefusal> {
    let root = prepare_root(&args.run.root.root)?;
    let run_id = args.run.run_id.as_str();
    let paths = RunPaths::new(&root, run_id);
    match fs::DirBuilder::new().mode(0o700).create(&paths.directory) {
        Ok(()) => {}
        Err(error) if error.kind() == io::ErrorKind::AlreadyExists => {
            return Err(AgentRefusal::new(
                "run_exists",
                avl_wire::supervisor::AgentExit::CantCreate,
                format!("run {run_id} already exists"),
            ));
        }
        Err(error) => return Err(AgentRefusal::internal(error)),
    }
    let cwd = std::path::absolute(&args.cwd).map_err(AgentRefusal::internal)?;
    let cwd = cwd.to_string_lossy().into_owned();
    let created_at = stamp(system.now());
    let spec = Spec {
        schema_version: SCHEMA_VERSION,
        run_id: run_id.to_owned(),
        snapshot_id: args.snapshot_id.clone(),
        cwd: cwd.clone(),
        argv: args.argv.clone(),
        created_at: created_at.clone(),
    };
    state::write_json_exclusive(&paths.spec, &spec).map_err(AgentRefusal::internal)?;
    let mut starting = RunState::new(run_id, Phase::Starting);
    starting.snapshot_id = args.snapshot_id.clone();
    starting.argv = args.argv.clone();
    starting.cwd = Some(cwd);
    starting.created_at = Some(created_at);
    state::write_json_atomic(&paths.state, &starting).map_err(AgentRefusal::internal)?;

    let supervisor_log = open_append(&paths.supervisor_log).map_err(AgentRefusal::internal)?;
    // One spawn point for every guest: the released spawn of the supervisor.
    let launch = launch::supervise_launch_for(&launcher.self_exe, &root, run_id);
    // The host goes into the log before the spawn, in the one file that outlives a run that died early.
    writeln!(
        &supervisor_log,
        "{} start {run_id}: launch host {}, supervisor in the released spawn",
        stamp(system.now()),
        launcher.host.as_str()
    )
    .map_err(AgentRefusal::internal)?;
    launch.start(&supervisor_log)?;
    drop(supervisor_log);

    let until = deadline(system, START_TIMEOUT);
    while system.now() < until {
        let state = read_state(&root, run_id)?;
        if matches!(state.phase, Phase::Running | Phase::Finished) {
            return Ok(state);
        }
        system.sleep(POLL_INTERVAL);
    }
    let message = format!("supervisor did not start {run_id} within {} ms", START_TIMEOUT.as_millis());
    Err(AgentRefusal::failure("start_timeout", message))
}

pub(crate) fn open_append(path: &Path) -> io::Result<File> {
    OpenOptions::new().create(true).append(true).mode(0o600).open(path)
}

// --- status, active, log ---------------------------------------------------------------------------------------

pub(crate) fn status(system: &dyn System, root: &Path, run_id: &str) -> Result<RunState, AgentRefusal> {
    let root = prepare_root(root)?;
    reconcile(system, &root, read_state(&root, run_id)?)
}

pub(crate) fn active(system: &dyn System, args: &RootArgs) -> Result<ActiveReply, AgentRefusal> {
    let root = prepare_root(&args.root)?;
    let Some(pointer) = state::read_active(&root)? else {
        return Ok(ActiveReply { active: None });
    };
    let reconciled = reconcile(system, &root, read_state(&root, &pointer.run_id)?)?;
    Ok(ActiveReply {
        active: (reconciled.phase != Phase::Finished).then_some(reconciled),
    })
}

pub(crate) fn log(system: &dyn System, args: &LogArgs) -> Result<LogReply, AgentRefusal> {
    let run_id = args.run.run_id.as_str();
    // The status first: a log of a run whose state is unreadable is refused like the state is, and a reconcile
    // that finished the run makes the reply describe the run as it now is.
    status(system, &args.run.root.root, run_id)?;
    let root = prepare_root(&args.run.root.root)?;
    let paths = RunPaths::new(&root, run_id);
    let tail = args.tail.map(|tail| tail as usize);
    let (raw, truncated) = log::read_log_suffix(&paths.log, tail).map_err(AgentRefusal::internal)?;
    let (content, more_lines) = match tail {
        Some(tail) => log::last_complete_lines(&raw, tail),
        None => (raw, false),
    };
    Ok(LogReply {
        run_id: run_id.to_owned(),
        log_path: paths.log.to_string_lossy().into_owned(),
        content,
        truncated: truncated || more_lines,
    })
}

// --- cancel ----------------------------------------------------------------------------------------------------

/// Drops the cancellation request for the supervisor, or answers the one already there: whoever wrote it first
/// owns the grace period.
fn request_cancellation(system: &dyn System, paths: &RunPaths, run_id: &str, grace_ms: u64) -> CancellationRequest {
    let request = CancellationRequest {
        schema_version: SCHEMA_VERSION,
        run_id: run_id.to_owned(),
        requested_at: stamp(system.now()),
        grace_ms,
    };
    match state::write_json_exclusive(&paths.cancel, &request) {
        Err(error) if error.kind() == io::ErrorKind::AlreadyExists => state::read_cancellation_request(&paths.cancel).unwrap_or(request),
        _ => request,
    }
}

/// Cancels a run whose supervisor is gone, synthesizing the exit status nobody reaped.
fn orphan_cancel(system: &dyn System, root: &Path, state: RunState, request: &CancellationRequest) -> Result<RunState, AgentRefusal> {
    let expected = ProcessIdentity {
        pid: state.pid.unwrap_or_default(),
        pgid: state.pgid.unwrap_or_default(),
        process_start: state.process_start.clone().unwrap_or_default(),
    };
    if !identity_matches(system, &expected) {
        return reconcile(system, root, state);
    }
    system.signal_group(expected.pgid, Signal::SIGTERM);
    let mut cancellation = CancellationRecord {
        requested_at: request.requested_at.clone(),
        grace_ms: request.grace_ms,
        term_sent_at: Some(stamp(system.now())),
        kill_sent_at: None,
    };
    let mut updated = state;
    updated.cancellation = Some(cancellation.clone());
    state::publish_state(root, &updated);
    let grace = deadline(system, Duration::from_millis(request.grace_ms));
    while system.now() < grace && identity_matches(system, &expected) {
        system.sleep(POLL_INTERVAL);
    }
    let mut signal = Signal::SIGTERM;
    if identity_matches(system, &expected) {
        system.signal_group(expected.pgid, Signal::SIGKILL);
        cancellation.kill_sent_at = Some(stamp(system.now()));
        signal = Signal::SIGKILL;
        let kill_deadline = deadline(system, FINISH_TIMEOUT);
        while system.now() < kill_deadline && identity_matches(system, &expected) {
            system.sleep(POLL_INTERVAL);
        }
    }
    if identity_matches(system, &expected) {
        return Err(AgentRefusal::failure(
            "cancel_timeout",
            format!("process group for {} did not exit", updated.run_id),
        ));
    }
    let finished = finish_state(
        system,
        root,
        &updated,
        None,
        Some(signal.as_str().to_owned()),
        Outcome::CanceledOrphan,
        FinishExtra {
            cancellation: Some(cancellation),
            failure: Some("supervisor was unavailable; exit status synthesized after identity-safe cancellation".to_owned()),
            ..FinishExtra::default()
        },
    )?;
    state::clear_active(root, &updated.run_id);
    Ok(finished)
}

pub(crate) fn cancel(system: &dyn System, args: &CancelArgs) -> Result<RunState, AgentRefusal> {
    let root = prepare_root(&args.run.root.root)?;
    let run_id = args.run.run_id.as_str();
    let paths = RunPaths::new(&root, run_id);
    let current = reconcile(system, &root, read_state(&root, run_id)?)?;
    if current.phase == Phase::Finished {
        return Ok(current);
    }
    let request = request_cancellation(system, &paths, run_id, args.grace_ms);
    if current.phase == Phase::Running && !supervisor_is_alive(system, &current) {
        return orphan_cancel(system, &root, current, &request);
    }
    let until = deadline(
        system,
        Duration::from_millis(args.grace_ms) + FINISH_TIMEOUT + CANCEL_HANDOFF_TIMEOUT,
    );
    loop {
        let latest = reconcile(system, &root, read_state(&root, run_id)?)?;
        if latest.phase == Phase::Finished {
            return Ok(latest);
        }
        // A heavily loaded host can resume this process just after the supervisor's terminal rename, so the
        // state is observed once more after the deadline before a timeout is reported.
        if system.now() >= until {
            break;
        }
        system.sleep(POLL_INTERVAL);
    }
    Err(AgentRefusal::failure(
        "cancel_timeout",
        format!("run {run_id} did not finish after cancellation"),
    ))
}

// --- contract --------------------------------------------------------------------------------------------------

/// The sha256 of the bytes this process was started from, or empty.
///
/// From the executable rather than a build stamp: a stamp can disagree with the bytes actually installed, which is
/// the one thing the host is asking about. Never fatal: an empty answer never equals a host digest, so the
/// controller reinstalls.
pub(crate) fn self_digest() -> String {
    let Ok(path) = std::env::current_exe() else {
        return String::new();
    };
    digest_file(&path).unwrap_or_default()
}

fn digest_file(path: &Path) -> io::Result<String> {
    let mut file = File::open(path)?;
    let mut hasher = Sha256::new();
    let mut buffer = vec![0; 64 * 1024];
    loop {
        let read = file.read(&mut buffer)?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
    }
    Ok(hex::encode(hasher.finalize()))
}

/// Answers this binary's own wire, so a controller can find out what an *already installed* agent speaks before
/// deciding whether to reinstall it. The digest is on the fast path of every lease operation.
pub(crate) fn contract(streams: &mut Streams<'_>) -> u8 {
    let Some(line) = reply::json_line(&Contract::current(self_digest())) else {
        return reply::fail(
            streams,
            AgentVerb::Contract.as_str(),
            AgentRefusal::for_verb(AgentVerb::Contract, "the contract could not be encoded"),
        );
    };
    match streams.stdout.write_all(&line) {
        Ok(()) => 0,
        Err(error) => reply::fail(
            streams,
            AgentVerb::Contract.as_str(),
            AgentRefusal::for_verb(AgentVerb::Contract, error.to_string()),
        ),
    }
}
