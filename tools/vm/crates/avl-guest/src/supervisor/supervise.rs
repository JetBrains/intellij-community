//! The detached supervisor: claims the slot, launches the child in a session of its own, records its identity,
//! honours one cancellation request, and writes the terminal state.
//!
//! Single-threaded: the child is polled with `try_wait` between sleeps, so there is no channel and no signal
//! handler; the supervisor is only ever the *target* of a signal.

use std::collections::BTreeMap;
use std::ffi::OsString;
use std::fs::{self, File};
use std::io::Write;
use std::path::Path;
use std::process::{Child, Command, Stdio};
use std::time::Duration;

use avl_wire::ide::{BIN_DIR, HOME_DIR};
use avl_wire::supervisor::{CancellationRecord, EnvironmentPolicy, Outcome, Phase, RunState, SCHEMA_VERSION, Spec};
use nix::sys::signal::Signal;

use super::identity::{ExitOutcome, ProcessIdentity, classify_exit, identity_matches};
use super::launch::{CHANNEL_VARIABLES, LaunchHost, detach_into_own_session};
use super::state::{
    ActivePointer, FinishExtra, RunPaths, claim_active, clear_active, finish_state, prepare_root, publish_state, read_active,
    read_cancellation_request, read_state, reconcile, require_active_ownership,
};
use super::{FINISH_TIMEOUT, POLL_INTERVAL, System, deadline, open_append, wait_for_identity};
use crate::clock::stamp;
use crate::reply::AgentRefusal;
use crate::reply::AgentRefusalExt;

/// How long a process is given to become identifiable through `ps`.
const IDENTITY_TIMEOUT: Duration = Duration::from_secs(2);
/// The exit a rejected run records: the slot belongs to another run.
const REJECTED_EXIT: i32 = 75;
/// How long the members that outlived the child get to leave the process table after their KILL.
const LEFTOVER_EXIT_TIMEOUT: Duration = Duration::from_secs(2);
/// The most characters of a command line that the supervisor log gives for one leftover member.
const LEFTOVER_COMMAND_CHARS: usize = 200;

/// The PATH a supervised child starts with: the system directories of the guest OS, and Homebrew's only on macOS.
///
/// It names no Node. For the UI daemon the controller resolves the pinned Node of this guest and puts its directory
/// first in the PATH that the start argv sets through `/usr/bin/env`. For the lane IDE the caller puts its launchers into
/// the `bin` directory of the context. So a Node directory here would be a second answer.
pub(crate) const fn child_path(host: LaunchHost) -> &'static str {
    match host {
        LaunchHost::Macos => "/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin",
        LaunchHost::Linux => "/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin",
    }
}

/// The directory the home of an account is in, on the guest OS.
const fn home_parent(host: LaunchHost) -> &'static str {
    match host {
        LaunchHost::Macos => "/Users/",
        LaunchHost::Linux => "/home/",
    }
}

/// The variables that a child of the [`EnvironmentPolicy::Context`] policy copies from the environment of the
/// supervisor, when the supervisor has them.
///
/// The list is closed: a variable that is not here does not reach the IDE or its children, so a credential in the
/// environment of the guest account stays out of them. A name joins the list only when a child of the IDE needs it
/// and no file of the context can carry it. Add it here and to the test of the context policy.
pub(crate) const ENVIRONMENT_ALLOWLIST: [&str; 10] = [
    "DISPLAY",
    "XAUTHORITY",
    "LANG",
    "LC_ALL",
    "TMPDIR",
    "TZ",
    "USER",
    "LOGNAME",
    "XDG_RUNTIME_DIR",
    "DBUS_SESSION_BUS_ADDRESS",
];

/// The locale that a child of the [`EnvironmentPolicy::Context`] policy gets as `LC_ALL` when the supervisor has no UTF-8 one.
///
/// The JVM encodes a file name in the character set of the locale. Under the POSIX locale it cannot represent a
/// non-ASCII name, so the IDE refuses such a file. Every guest OS of the lane has this locale.
pub(crate) const CONTEXT_UTF8_LOCALE: &str = "C.UTF-8";

/// The environment a supervised child is launched with, out of the supervisor's own on the guest `host`, by `policy`.
///
/// No agent-CLI guesses here: the controller resolves them in this guest and passes the answer with the child's
/// argv, so a second candidate table would be a second answer. The variables of the exec channel never pass.
pub(crate) fn child_environment(
    host: LaunchHost,
    inherited: impl IntoIterator<Item = (OsString, OsString)>,
    policy: &EnvironmentPolicy,
) -> BTreeMap<OsString, OsString> {
    let mut environment = match policy {
        EnvironmentPolicy::Inherit => inherited_environment(host, inherited),
        EnvironmentPolicy::Context { context_dir } => context_environment(host, inherited, Path::new(context_dir)),
    };
    environment.insert("IJ_PRIVATE_PACKAGES_AUTHORIZER_SKIP".into(), "true".into());
    for name in CHANNEL_VARIABLES {
        environment.remove(&OsString::from(name));
    }
    environment
}

/// The environment of the supervisor, with the PATH of the guest OS and a `HOME` when it has none.
fn inherited_environment(host: LaunchHost, inherited: impl IntoIterator<Item = (OsString, OsString)>) -> BTreeMap<OsString, OsString> {
    let mut environment: BTreeMap<OsString, OsString> = inherited.into_iter().collect();
    if environment.get(&OsString::from("HOME")).is_none_or(|home| home.is_empty()) {
        let user = environment
            .get(&OsString::from("USER"))
            .filter(|user| !user.is_empty())
            .cloned()
            .unwrap_or_else(|| "admin".into());
        let mut home = OsString::from(home_parent(host));
        home.push(user);
        environment.insert("HOME".into(), home);
    }
    environment.insert("PATH".into(), child_path(host).into());
    environment
}

/// The closed environment of the IDE of `context`: the [`ENVIRONMENT_ALLOWLIST`] subset of the supervisor's own,
/// `HOME` on the home of the context, and the `bin` directory of the context first in the PATH of the guest OS.
///
/// An inherited UTF-8 locale stays as it is. Any other locale, or none, gets `LC_ALL` set to [`CONTEXT_UTF8_LOCALE`].
fn context_environment(
    host: LaunchHost,
    inherited: impl IntoIterator<Item = (OsString, OsString)>,
    context: &Path,
) -> BTreeMap<OsString, OsString> {
    let mut environment: BTreeMap<OsString, OsString> = inherited
        .into_iter()
        .filter(|(name, _)| name.to_str().is_some_and(|name| ENVIRONMENT_ALLOWLIST.contains(&name)))
        .collect();
    environment.insert("HOME".into(), context.join(HOME_DIR).into_os_string());
    let mut path = context.join(BIN_DIR).into_os_string();
    path.push(":");
    path.push(child_path(host));
    environment.insert("PATH".into(), path);
    if !has_utf8_locale(&environment) {
        environment.insert("LC_ALL".into(), CONTEXT_UTF8_LOCALE.into());
    }
    environment
}

/// Whether the locale that `environment` gives to the character type is a UTF-8 one. `LC_ALL` decides when it is set
/// and not empty, else `LANG`. The context policy passes no `LC_CTYPE`.
pub(crate) fn has_utf8_locale(environment: &BTreeMap<OsString, OsString>) -> bool {
    ["LC_ALL", "LANG"]
        .into_iter()
        .filter_map(|name| environment.get(&OsString::from(name)))
        .find(|value| !value.is_empty())
        .and_then(|value| value.to_str())
        .is_some_and(|value| value.to_ascii_lowercase().replace('-', "").contains("utf8"))
}

/// Adapts an argv the way an interactive shell would: repository `.cmd` launchers are shell/cmd polyglots without
/// a shebang, which a shell runs through its ENOEXEC fallback and a bare exec refuses.
pub(crate) fn child_spawn_argv(argv: &[String]) -> Vec<String> {
    match argv.first() {
        Some(program) if program.ends_with(".cmd") => {
            let mut adapted = vec!["/bin/sh".to_owned()];
            adapted.extend(argv.iter().cloned());
            adapted
        }
        _ => argv.to_vec(),
    }
}

fn read_spec(paths: &RunPaths, run_id: &str) -> Result<Spec, AgentRefusal> {
    let raw = fs::read(&paths.spec)
        .map_err(|error| AgentRefusal::failure("invalid_state", format!("spec for {run_id} is missing or invalid: {error}")))?;
    let spec: Spec = serde_json::from_slice(&raw)
        .ok()
        .filter(|spec: &Spec| spec.schema_version == SCHEMA_VERSION)
        .ok_or_else(|| AgentRefusal::failure("invalid_state", format!("spec for {run_id} is missing or invalid")))?;
    if spec.run_id != run_id || spec.argv.is_empty() || spec.cwd.is_empty() {
        return Err(AgentRefusal::failure("invalid_spec", format!("spec for {run_id} is invalid")));
    }
    Ok(spec)
}

/// The child being watched, and how it ended once it has.
struct Watched {
    child: Child,
    outcome: Option<ExitOutcome>,
}

impl Watched {
    fn settled(&mut self) -> bool {
        if self.outcome.is_none() {
            self.outcome = match self.child.try_wait() {
                Ok(Some(status)) => Some(classify_exit(status)),
                Ok(None) => None,
                // A child this process can no longer wait for has ended in a way nobody observed.
                Err(_) => Some(ExitOutcome::failed_to_start()),
            };
        }
        self.outcome.is_some()
    }

    /// Waits up to `duration` for the child to end, returning as soon as it does. Once it has ended this is a
    /// plain sleep, which is what a loop waiting on the child's *group* needs.
    fn await_or_sleep(&mut self, system: &dyn System, duration: Duration) {
        if self.settled() {
            system.sleep(duration);
            return;
        }
        let until = deadline(system, duration);
        while !self.settled() && system.now() < until {
            system.sleep(duration.min(Duration::from_millis(10)));
        }
    }
}

/// Supervises the run `run_id` under `root`: the `supervise` verb.
pub(crate) fn supervise(system: &dyn System, root: &Path, run_id: &str) -> Result<(), AgentRefusal> {
    let root = prepare_root(root)?;
    let root = root.as_path();
    let paths = RunPaths::new(root, run_id);
    let spec = read_spec(&paths, run_id)?;

    let own_pid = i32::try_from(std::process::id()).map_err(AgentRefusal::internal)?;
    let own = wait_for_identity(system, own_pid, IDENTITY_TIMEOUT)
        .ok_or_else(|| AgentRefusal::failure("identity_unavailable", "cannot identify supervisor process"))?;
    let mut current = read_state(root, run_id)?;
    if current.phase != Phase::Starting {
        return Ok(());
    }
    current.supervisor_pid = Some(own.pid);
    current.supervisor_pgid = Some(own.pgid);
    current.supervisor_start = Some(own.process_start.clone());
    // A reconciler must be able to prove that a starting supervisor is alive before the active pointer makes this
    // run visible as the global owner.
    publish_state(root, &current);

    let active = ActivePointer {
        schema_version: SCHEMA_VERSION,
        run_id: run_id.to_owned(),
        supervisor_pid: own.pid,
        supervisor_pgid: own.pgid,
        supervisor_start: own.process_start,
        claimed_at: stamp(system.now()),
    };
    if !claim_run_slot(system, root, run_id, &active)? {
        return Ok(());
    }

    let log = open_append(&paths.log).map_err(AgentRefusal::internal)?;
    // Intentionally adjacent to the launch: once the active pointer is lost or replaced, this supervisor must
    // never publish another child.
    if let Err(lost) = require_active_ownership(root, &active) {
        finish_state(
            system,
            root,
            &current,
            Some(REJECTED_EXIT),
            None,
            Outcome::Rejected,
            FinishExtra {
                failure: Some(lost.message),
                ..FinishExtra::default()
            },
        )?;
        return Ok(());
    }
    let child = match spawn_child(&spec, log) {
        Ok(child) => child,
        Err(error) => {
            finish_failed_to_start(system, root, &current, error.to_string())?;
            return Ok(());
        }
    };
    let child_pid = i32::try_from(child.id()).map_err(AgentRefusal::internal)?;
    let mut watched = Watched { child, outcome: None };

    let child_identity = identify_child(system, &mut watched, child_pid);
    if let Some(outcome) = watched.outcome.clone() {
        // The spawn put the child into a session of its own, so its pid is the id of its group.
        end_leftover_members(system, child_pid, &paths, run_id);
        current.phase = Phase::Running;
        current.started_at = Some(stamp(system.now()));
        finish_state(
            system,
            root,
            &current,
            outcome.code,
            outcome.signal,
            exit_result(outcome.code),
            FinishExtra::default(),
        )?;
        clear_active(root, run_id);
        return Ok(());
    }
    let Some(child_identity) = child_identity.filter(|identity| identity.pgid == child_pid) else {
        let _ = watched.child.kill();
        watched.await_or_sleep(system, Duration::from_secs(1));
        finish_failed_to_start(
            system,
            root,
            &current,
            "child did not become an identifiable process-group leader".to_owned(),
        )?;
        return Ok(());
    };
    current.phase = Phase::Running;
    current.started_at = Some(stamp(system.now()));
    current.pid = Some(child_identity.pid);
    current.pgid = Some(child_identity.pgid);
    current.process_start = Some(child_identity.process_start.clone());
    publish_state(root, &current);

    let cancellation = watch_until_settled(system, root, &mut current, &child_identity, &paths, &mut watched);
    end_leftover_members(system, child_identity.pgid, &paths, run_id);
    let outcome = watched.outcome.clone().unwrap_or_else(ExitOutcome::failed_to_start);
    let (result, code, signal) = finish_of(cancellation.as_ref(), outcome);
    finish_state(
        system,
        root,
        &current,
        code,
        signal,
        result,
        FinishExtra {
            cancellation,
            ..FinishExtra::default()
        },
    )?;
    clear_active(root, run_id);
    Ok(())
}

/// Claims the global run slot for `active`, and answers whether it holds it. A slot that a live run holds rejects
/// this run, and so does a second claim that lost the race to another supervisor; both are answered here and the
/// caller stops.
fn claim_run_slot(system: &dyn System, root: &Path, run_id: &str, active: &ActivePointer) -> Result<bool, AgentRefusal> {
    if claim_active(root, active)? {
        return Ok(true);
    }
    if let Some(existing) = read_active(root)?
        && let Ok(existing_state) = read_state(root, &existing.run_id)
        && let Ok(reconciled) = reconcile(system, root, existing_state)
        && reconciled.phase != Phase::Finished
    {
        reject(system, root, run_id, format!("active run {}", existing.run_id))?;
        return Ok(false);
    }
    // The owner finished and freed the slot while this supervisor looked; one more claim decides it.
    if !claim_active(root, active)? {
        reject(system, root, run_id, "active run claim raced".to_owned())?;
        return Ok(false);
    }
    Ok(true)
}

/// Starts the child of `spec` in a session of its own, with the run log as both of its streams.
fn spawn_child(spec: &Spec, log: File) -> std::io::Result<Child> {
    let argv = child_spawn_argv(&spec.argv);
    let mut command = Command::new(&argv[0]);
    command
        .args(&argv[1..])
        .current_dir(&spec.cwd)
        .env_clear()
        .envs(child_environment(LaunchHost::current(), std::env::vars_os(), &spec.environment))
        .stdin(Stdio::null());
    let stdout = log.try_clone()?;
    command.stdout(stdout).stderr(log);
    detach_into_own_session(&mut command);
    command.spawn()
}

/// Waits for the child to be identifiable, at most [`IDENTITY_TIMEOUT`], or to settle first; answers its identity
/// when it has one.
fn identify_child(system: &dyn System, watched: &mut Watched, child_pid: i32) -> Option<ProcessIdentity> {
    let identity_deadline = deadline(system, IDENTITY_TIMEOUT);
    while !watched.settled() && system.now() < identity_deadline {
        if let Some(identity) = system.identity(child_pid) {
            return Some(identity);
        }
        watched.await_or_sleep(system, POLL_INTERVAL);
    }
    None
}

/// Watches the child until it settles, honouring the first cancellation request; answers the cancellation record
/// when one was honoured.
fn watch_until_settled(
    system: &dyn System,
    root: &Path,
    current: &mut RunState,
    child_identity: &ProcessIdentity,
    paths: &RunPaths,
    watched: &mut Watched,
) -> Option<CancellationRecord> {
    let mut cancellation: Option<CancellationRecord> = None;
    while !watched.settled() {
        if cancellation.is_none()
            && let Some(request) = read_cancellation_request(&paths.cancel)
        {
            cancellation = Some(honour_cancellation(system, root, current, child_identity, &request, watched));
        }
        if !watched.settled() {
            watched.await_or_sleep(system, POLL_INTERVAL);
        }
    }
    cancellation
}

// --- the decisions of a supervised run: values in, an answer out, no I/O ---------------------------------------

/// What an exit means for a run that no cancellation reached: success for exit 0, failure for every other exit.
pub(crate) fn exit_result(code: Option<i32>) -> Outcome {
    if code == Some(0) { Outcome::Succeeded } else { Outcome::Failed }
}

/// The finish of a run that was watched to its end: its result, and the exit code and the signal it records. A
/// cancellation is the result whatever the exit was, and a KILL that the cancellation sent is the signal the record
/// names, with no exit code.
pub(crate) fn finish_of(cancellation: Option<&CancellationRecord>, outcome: ExitOutcome) -> (Outcome, Option<i32>, Option<String>) {
    let result = if cancellation.is_some() {
        Outcome::Canceled
    } else {
        exit_result(outcome.code)
    };
    if cancellation.is_some_and(|record| record.kill_sent_at.is_some()) {
        return (result, None, Some(Signal::SIGKILL.as_str().to_owned()));
    }
    (result, outcome.code, outcome.signal)
}

/// TERMs the child's group, waits out the grace period, and KILLs what is left. Refuses to signal at all when the
/// recorded identity no longer matches: that pid may now be someone else's process.
fn honour_cancellation(
    system: &dyn System,
    root: &Path,
    current: &mut RunState,
    child: &ProcessIdentity,
    request: &avl_wire::supervisor::CancellationRequest,
    watched: &mut Watched,
) -> CancellationRecord {
    let mut record = CancellationRecord {
        requested_at: request.requested_at.clone(),
        grace_ms: request.grace_ms,
        term_sent_at: None,
        kill_sent_at: None,
    };
    if !identity_matches(system, child) {
        current.cancellation = Some(record.clone());
        current.failure = Some("process identity changed before TERM".to_owned());
        publish_state(root, current);
        return record;
    }
    system.signal_group(child.pgid, Signal::SIGTERM);
    record.term_sent_at = Some(stamp(system.now()));
    current.cancellation = Some(record.clone());
    publish_state(root, current);
    let grace = deadline(system, Duration::from_millis(request.grace_ms));
    while system.group_alive(child.pgid) && system.now() < grace {
        watched.await_or_sleep(system, POLL_INTERVAL);
    }
    if system.group_alive(child.pgid) {
        system.signal_group(child.pgid, Signal::SIGKILL);
        record.kill_sent_at = Some(stamp(system.now()));
        current.cancellation = Some(record.clone());
        publish_state(root, current);
        let kill_deadline = deadline(system, FINISH_TIMEOUT);
        while system.group_alive(child.pgid) && system.now() < kill_deadline {
            system.sleep(POLL_INTERVAL);
        }
    }
    record
}

/// Kills the members of the group `pgid` that outlived the reaped child, waits up to [`LEFTOVER_EXIT_TIMEOUT`] for
/// them to end, and names them in the supervisor log of the run.
///
/// Such a member is a process that the child started without a group of its own, as the IDE starts its JCEF helper
/// and its agent processes. The kernel gives no new process the id of a group that still exists, so `pgid` names only
/// this group, and the KILL reaches no other process. A group that outlives the wait stays for the reconcile, which
/// records the run as orphaned.
pub(crate) fn end_leftover_members(system: &dyn System, pgid: i32, paths: &RunPaths, run_id: &str) {
    if !system.group_alive(pgid) {
        return;
    }
    let members = system.group_members(pgid);
    system.signal_group(pgid, Signal::SIGKILL);
    let until = deadline(system, LEFTOVER_EXIT_TIMEOUT);
    while system.group_alive(pgid) && system.now() < until {
        system.sleep(POLL_INTERVAL);
    }
    let result = if system.group_alive(pgid) {
        format!("the group still has members after {} ms", LEFTOVER_EXIT_TIMEOUT.as_millis())
    } else {
        "the group is gone".to_owned()
    };
    let named = members
        .iter()
        .map(|member| {
            format!(
                "{} {}",
                member.pid,
                member.command.chars().take(LEFTOVER_COMMAND_CHARS).collect::<String>()
            )
        })
        .collect::<Vec<_>>()
        .join("; ");
    if let Ok(log) = open_append(&paths.supervisor_log) {
        let _ = writeln!(
            &log,
            "{} finish {run_id}: KILL to group {pgid}, members that outlived the child: {} [{named}]; {result}",
            stamp(system.now()),
            members.len()
        );
    }
}

fn reject(system: &dyn System, root: &Path, run_id: &str, failure: String) -> Result<(), AgentRefusal> {
    let rejected = read_state(root, run_id)?;
    finish_state(
        system,
        root,
        &rejected,
        Some(REJECTED_EXIT),
        None,
        Outcome::Rejected,
        FinishExtra {
            failure: Some(failure),
            ..FinishExtra::default()
        },
    )?;
    Ok(())
}

fn finish_failed_to_start(system: &dyn System, root: &Path, current: &RunState, failure: String) -> Result<(), AgentRefusal> {
    let failed = ExitOutcome::failed_to_start();
    finish_state(
        system,
        root,
        current,
        failed.code,
        None,
        Outcome::FailedToStart,
        FinishExtra {
            failure: Some(failure),
            ..FinishExtra::default()
        },
    )?;
    clear_active(root, &current.run_id);
    Ok(())
}
