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
pub(crate) mod thread_dump;

#[cfg(test)]
mod tests;

use std::fs::{self, File, OpenOptions};
use std::io::{self, Read, Write};
use std::os::unix::fs::{DirBuilderExt, OpenOptionsExt};
use std::path::{Path, PathBuf};
use std::thread;
use std::time::Duration;

use avl_wire::ide::{ABANDONED_FILE, AbandonedRun};
use avl_wire::supervisor::{
    ActiveReply, CancellationRecord, CancellationRequest, Contract, EnvironmentPolicy, LogReply, Outcome, Phase, RunState, SCHEMA_VERSION,
    Spec,
};
use avl_wire::verb::AgentVerb;
use jiff::Timestamp;
use nix::sys::signal::Signal;
use sha2::{Digest, Sha256};

use crate::cli::{CancelArgs, LogArgs, RootArgs, StartArgs};
use crate::clock::stamp;
use crate::reply::{self, AgentRefusal, Streams};

pub(crate) use launch::{LaunchHost, reap_ended};
pub(crate) use supervise::supervise;
#[cfg(test)]
pub(crate) use supervise::{CONTEXT_UTF8_LOCALE, ENVIRONMENT_ALLOWLIST, child_path, has_utf8_locale};

use crate::reply::AgentRefusalExt;
use identity::{GroupMember, ProcessIdentity, child_is_alive, identity_matches, supervisor_is_alive};
use state::{FinishExtra, RunPaths, finish_state, prepare_root, read_state, reconcile};

pub(crate) const POLL_INTERVAL: Duration = Duration::from_millis(50);
pub(crate) const START_TIMEOUT: Duration = Duration::from_secs(15);
pub(crate) const FINISH_TIMEOUT: Duration = Duration::from_secs(10);
/// The cancel client and the detached supervisor are scheduled independently. Leave time for the supervisor's
/// final fsync and rename after its own grace and process-group deadlines expire.
pub(crate) const CANCEL_HANDOFF_TIMEOUT: Duration = Duration::from_secs(5);

/// The logs of the run `run_id` in the run slot `root`: `run.log`, the standard output and error of its process, and
/// `supervisor.log`, the log of its supervisor.
pub(crate) fn run_logs(root: &Path, run_id: &str) -> [PathBuf; 2] {
    let paths = RunPaths::new(root, run_id);
    [paths.log, paths.supervisor_log]
}

/// The processes and the clock the supervisor observes, as one seam, so the reconciler's branches are tested
/// against a scripted world instead of real processes and real sleeps.
pub(crate) trait System {
    /// The identity of `pid`, or `None` when there is no such process.
    fn identity(&self, pid: i32) -> Option<ProcessIdentity>;
    /// Whether any process of `pgid` is still there.
    fn group_alive(&self, pgid: i32) -> bool;
    /// The processes of `pgid` that are still there.
    fn group_members(&self, pgid: i32) -> Vec<GroupMember>;
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

    fn group_members(&self, pgid: i32) -> Vec<GroupMember> {
        identity::group_members(pgid)
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
            self_exe: own_image(cfg!(target_os = "linux"), std::env::current_exe()),
            host: LaunchHost::current(),
        }
    }
}

/// The path of the image of this process on Linux, which a spawn executes also after its file is gone.
pub(crate) const LINUX_OWN_IMAGE: &str = "/proc/self/exe";

/// The binary that this process spawns as a supervisor.
///
/// On Linux it is [`LINUX_OWN_IMAGE`]. A lane daemon is a process of the installed agent, and an install renames a new
/// binary onto the agent path while the daemon runs. `current` reads the link of [`LINUX_OWN_IMAGE`], which then names
/// `<path> (deleted)`, and a spawn of that finds no file. Elsewhere it is `current`, the path of the binary: a rename
/// there gives the new build, which still starts. An unreadable path surfaces as a spawn failure naming the empty path.
pub(crate) fn own_image(linux: bool, current: io::Result<PathBuf>) -> PathBuf {
    if linux {
        PathBuf::from(LINUX_OWN_IMAGE)
    } else {
        current.unwrap_or_default()
    }
}

/// The `start` verb: [`launch_run`] with the [`EnvironmentPolicy::Inherit`] policy.
pub(crate) fn start(system: &dyn System, launcher: &Launcher, args: &StartArgs) -> Result<RunState, AgentRefusal> {
    launch_run(
        system,
        launcher,
        &args.run.root.root,
        &args.run.run_id,
        &args.cwd,
        args.argv.clone(),
        args.snapshot_id.clone(),
        EnvironmentPolicy::Inherit,
    )
}

/// Creates the run `run_id` under `root`, launches its detached supervisor, and waits until the child is running (or
/// already done). The child runs `argv` in `cwd`, with the environment of `environment`.
pub(crate) fn launch_run(
    system: &dyn System,
    launcher: &Launcher,
    root: &Path,
    run_id: &str,
    cwd: &Path,
    argv: Vec<String>,
    snapshot_id: Option<String>,
    environment: EnvironmentPolicy,
) -> Result<RunState, AgentRefusal> {
    let root = prepare_root(root)?;
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
    let cwd = std::path::absolute(cwd).map_err(AgentRefusal::internal)?;
    let cwd = cwd.to_string_lossy().into_owned();
    let created_at = stamp(system.now());
    let spec = Spec {
        schema_version: SCHEMA_VERSION,
        run_id: run_id.to_owned(),
        snapshot_id: snapshot_id.clone(),
        cwd: cwd.clone(),
        argv: argv.clone(),
        environment,
        created_at: created_at.clone(),
    };
    state::write_json_exclusive(&paths.spec, &spec).map_err(AgentRefusal::internal)?;
    let mut starting = RunState::new(run_id, Phase::Starting);
    starting.snapshot_id = snapshot_id;
    starting.argv = argv;
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
fn orphan_cancel(system: &dyn System, root: &Path, state: RunState, request: &CancellationRequest) -> Result<CancelEnd, AgentRefusal> {
    let expected = ProcessIdentity {
        pid: state.pid.unwrap_or_default(),
        pgid: state.pgid.unwrap_or_default(),
        process_start: state.process_start.clone().unwrap_or_default(),
    };
    if !identity_matches(system, &expected) {
        return reconcile(system, root, state).map(CancelEnd::Finished);
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
        return abandon(system, root, &updated, expected.pgid, cancellation);
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
    Ok(CancelEnd::Finished(finished))
}

/// How a cancel ended.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum CancelEnd {
    /// The run finished: its supervisor ended it, the orphan cancel ended it, or it had ended before.
    Finished(RunState),
    /// The supervisor did not end the cancel in time, so the agent killed the supervisor and the process group of the
    /// child.
    Killed(RunState),
    /// The processes of the run outlived the kill. The agent freed the slot and wrote [`ABANDONED_FILE`] in its root.
    Abandoned(RunState),
}

impl CancelEnd {
    pub(crate) fn into_state(self) -> RunState {
        match self {
            Self::Finished(state) | Self::Killed(state) | Self::Abandoned(state) => state,
        }
    }
}

/// Cancels a run and answers its finished state. A cancel never leaves the slot held: see [`cancel_to_end`].
pub(crate) fn cancel(system: &dyn System, args: &CancelArgs) -> Result<RunState, AgentRefusal> {
    cancel_to_end(system, args).map(CancelEnd::into_state)
}

/// Cancels a run. The supervisor of the run gets the grace of the request and its own deadlines to end it. Past them,
/// the agent kills the supervisor and the process group of the child itself. A group that outlives that kill is
/// abandoned: the slot is free, and the record of the abandoned run names its processes.
pub(crate) fn cancel_to_end(system: &dyn System, args: &CancelArgs) -> Result<CancelEnd, AgentRefusal> {
    let root = prepare_root(&args.run.root.root)?;
    let run_id = args.run.run_id.as_str();
    let paths = RunPaths::new(&root, run_id);
    let current = reconcile(system, &root, read_state(&root, run_id)?)?;
    if current.phase == Phase::Finished {
        return Ok(CancelEnd::Finished(current));
    }
    if let Some(jcmd) = &args.thread_dump {
        dump_threads(system, &root, &paths, &current, jcmd);
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
            return Ok(CancelEnd::Finished(latest));
        }
        // A heavily loaded host can resume this process just after the supervisor's terminal rename, so the
        // state is observed once more after the deadline before the agent acts itself.
        if system.now() >= until {
            return kill_after_deadline(system, &root, &latest, &request);
        }
        system.sleep(POLL_INTERVAL);
    }
}

/// Ends a run whose supervisor did not end the cancel in time: a KILL to the supervisor, so it writes no state after
/// this one, then a KILL to the process group of the child. Each signal is identity-checked or group-checked first.
fn kill_after_deadline(
    system: &dyn System,
    root: &Path,
    state: &RunState,
    request: &CancellationRequest,
) -> Result<CancelEnd, AgentRefusal> {
    if supervisor_is_alive(system, state)
        && let Some(pgid) = state.supervisor_pgid
    {
        system.signal_group(pgid, Signal::SIGKILL);
    }
    let pgid = state.pgid.unwrap_or_default();
    let cancellation = CancellationRecord {
        requested_at: request.requested_at.clone(),
        grace_ms: request.grace_ms,
        term_sent_at: state.cancellation.as_ref().and_then(|record| record.term_sent_at.clone()),
        kill_sent_at: Some(stamp(system.now())),
    };
    if system.group_alive(pgid) {
        system.signal_group(pgid, Signal::SIGKILL);
        let kill_deadline = deadline(system, FINISH_TIMEOUT);
        while system.now() < kill_deadline && system.group_alive(pgid) {
            system.sleep(POLL_INTERVAL);
        }
    }
    if system.group_alive(pgid) {
        return abandon(system, root, state, pgid, cancellation);
    }
    let finished = finish_state(
        system,
        root,
        state,
        None,
        Some(Signal::SIGKILL.as_str().to_owned()),
        Outcome::CanceledOrphan,
        FinishExtra {
            cancellation: Some(cancellation),
            failure: Some(format!(
                "the supervisor did not end the cancel within its deadline, so the agent killed it and the process group \
                 {pgid}"
            )),
            ..FinishExtra::default()
        },
    )?;
    state::clear_active(root, &state.run_id);
    Ok(CancelEnd::Killed(finished))
}

/// Gives up a run whose process group `pgid` outlived a KILL: the run finishes as canceled with the survivors named,
/// [`ABANDONED_FILE`] records them in the slot root, and the slot is free. Nothing a later signal can do frees the
/// processes; a recycle of the worker discards them.
fn abandon(
    system: &dyn System,
    root: &Path,
    state: &RunState,
    pgid: i32,
    cancellation: CancellationRecord,
) -> Result<CancelEnd, AgentRefusal> {
    let members: Vec<String> = system
        .group_members(pgid)
        .into_iter()
        .map(|member| format!("{} {}", member.pid, member.command))
        .collect();
    let record = AbandonedRun {
        run_id: state.run_id.clone(),
        pgid,
        members: members.clone(),
        abandoned_at: stamp(system.now()),
    };
    state::write_json_atomic(&root.join(ABANDONED_FILE), &record).map_err(AgentRefusal::internal)?;
    let finished = finish_state(
        system,
        root,
        state,
        None,
        Some(Signal::SIGKILL.as_str().to_owned()),
        Outcome::CanceledOrphan,
        FinishExtra {
            cancellation: Some(cancellation),
            failure: Some(format!(
                "the process group {pgid} outlived SIGKILL, so the agent abandoned the run and freed its slot; a recycle of \
                 the worker discards {members:?}"
            )),
            ..FinishExtra::default()
        },
    )?;
    state::clear_active(root, &state.run_id);
    Ok(CancelEnd::Abandoned(finished))
}

/// Takes the thread dump of the live child of `state` before the cancellation request exists, so the dump comes
/// before the TERM on both paths: the supervisor's and the orphan cancel's. The supervisor log of the run names the
/// dump file or the failure. A failed dump never stops the cancel: the cancel is the reason the dump is taken.
fn dump_threads(system: &dyn System, root: &Path, paths: &RunPaths, state: &RunState, jcmd: &Path) {
    let Some(pid) = state.pid.filter(|_| state.phase == Phase::Running && child_is_alive(system, state)) else {
        return;
    };
    let directory = thread_dump::directory(root, &state.run_id);
    let line = match thread_dump::capture(jcmd, pid, &directory, system.now()) {
        Ok(file) => format!("thread dump of pid {pid} in {}", file.display()),
        Err(error) => format!("thread dump of pid {pid} failed: {error}"),
    };
    if let Ok(log) = open_append(&paths.supervisor_log) {
        let _ = writeln!(&log, "{} cancel {}: {line}", stamp(system.now()), state.run_id);
    }
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
