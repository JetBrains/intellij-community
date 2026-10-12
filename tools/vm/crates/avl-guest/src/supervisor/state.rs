//! The run's state on disk: the documents, the durable writes that publish them, the active-run pointer that is
//! the slot's mutual exclusion, and the reconciler that answers what a recorded state means *now*.

use std::fs::{self, File, OpenOptions};
use std::io::{self, Write};
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::{Path, PathBuf};
use std::time::Duration;

use avl_wire::supervisor::{
    CancellationRecord, CancellationRequest, Outcome, Phase, RunState, SCHEMA_VERSION, decode_run_state, is_run_id,
    validate_outcome_to_write,
};
use serde::{Deserialize, Serialize};

use super::identity::{child_is_alive, supervisor_is_alive};
use super::{POLL_INTERVAL, START_TIMEOUT, System, deadline};
use crate::clock::{parse_stamp, stamp};
use crate::reply::AgentRefusalExt;
use crate::reply::{AgentRefusal, quoted};

/// The run slot's owner, as `active.json` records it.
///
/// Deliberately not in `avl_wire`: this file never crosses to the host. It is the guest's own mutual exclusion,
/// read only by the next supervisor to race for the slot, and the host learns who owns the slot from the `active`
/// reply instead. It carries the shared schema version so the two move together.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ActivePointer {
    pub schema_version: u32,
    pub run_id: String,
    pub supervisor_pid: i32,
    pub supervisor_pgid: i32,
    pub supervisor_start: String,
    pub claimed_at: String,
}

// --- paths and validation ----------------------------------------------------------------------------------

pub(crate) struct RunPaths {
    pub directory: PathBuf,
    pub state: PathBuf,
    pub spec: PathBuf,
    pub log: PathBuf,
    pub supervisor_log: PathBuf,
    pub cancel: PathBuf,
}

impl RunPaths {
    /// The files of one run. `run_id` is a validated id, so it is one path component.
    pub(super) fn new(root: &Path, run_id: &str) -> Self {
        let directory = root.join(run_id);
        Self {
            state: directory.join("state.json"),
            spec: directory.join("spec.json"),
            log: directory.join("run.log"),
            supervisor_log: directory.join("supervisor.log"),
            cancel: directory.join("cancel.json"),
            directory,
        }
    }
}

pub(crate) fn validate_run_id(run_id: &str) -> Result<(), AgentRefusal> {
    if is_run_id(run_id) {
        return Ok(());
    }
    Err(AgentRefusal::usage(format!("{} is not a valid run id", quoted(run_id))).with_code("invalid_run_id"))
}

/// Makes `root` an absolute, private directory and answers it.
pub(crate) fn prepare_root(root: &Path) -> Result<PathBuf, AgentRefusal> {
    let absolute = std::path::absolute(root).map_err(AgentRefusal::internal)?;
    fs::create_dir_all(&absolute).map_err(AgentRefusal::internal)?;
    fs::set_permissions(&absolute, fs::Permissions::from_mode(0o700)).map_err(AgentRefusal::internal)?;
    Ok(absolute)
}

// --- durable writes ----------------------------------------------------------------------------------------

fn fsync_directory(path: &Path) -> io::Result<()> {
    File::open(path)?.sync_all()
}

fn parent_of(path: &Path) -> &Path {
    path.parent().unwrap_or(Path::new("."))
}

fn encode(value: &impl Serialize) -> io::Result<Vec<u8>> {
    let mut encoded = serde_json::to_vec(value)?;
    encoded.push(b'\n');
    Ok(encoded)
}

/// Publishes `value` at `path` so a reader sees the old document or the new one, never half of one: a private
/// temporary beside it, fsynced, renamed, and the directory fsynced so the rename survives a hard reset.
pub(crate) fn write_json_atomic(path: &Path, value: &impl Serialize) -> io::Result<()> {
    let parent = parent_of(path);
    let name = path.file_name().map(|name| name.to_string_lossy().into_owned()).unwrap_or_default();
    let mut temporary = tempfile::Builder::new()
        .prefix(&format!(".{name}."))
        .suffix(".tmp")
        .permissions(fs::Permissions::from_mode(0o600))
        .tempfile_in(parent)?;
    temporary.write_all(&encode(value)?)?;
    temporary.as_file().sync_all()?;
    let mut temporary = temporary.into_temp_path();
    fs::rename(&temporary, path)?;
    temporary.disable_cleanup(true);
    fsync_directory(parent)
}

/// Creates `path` holding `value`, refusing with [`io::ErrorKind::AlreadyExists`] when it is already there.
pub(crate) fn write_json_exclusive(path: &Path, value: &impl Serialize) -> io::Result<()> {
    let mut file = OpenOptions::new().write(true).create_new(true).mode(0o600).open(path)?;
    file.write_all(&encode(value)?)?;
    file.sync_all()?;
    drop(file);
    fs::set_permissions(path, fs::Permissions::from_mode(0o600))?;
    fsync_directory(parent_of(path))
}

// --- reading state -----------------------------------------------------------------------------------------

pub(crate) fn read_state(root: &Path, run_id: &str) -> Result<RunState, AgentRefusal> {
    validate_run_id(run_id)?;
    let invalid = |detail: String| AgentRefusal::failure("invalid_state", detail);
    let raw = fs::read(RunPaths::new(root, run_id).state)
        .map_err(|error| invalid(format!("state for {run_id} is missing or invalid: {error}")))?;
    decode_run_state(&raw, run_id).map_err(|error| match error {
        avl_wire::supervisor::RunStateError::Json(error) => invalid(format!("state for {run_id} is missing or invalid: {error}")),
        other => invalid(other.to_string()),
    })
}

pub(crate) fn read_cancellation_request(path: &Path) -> Option<CancellationRequest> {
    let raw = fs::read(path).ok()?;
    let request: CancellationRequest = serde_json::from_slice(&raw).ok()?;
    (request.schema_version == SCHEMA_VERSION).then_some(request)
}

// --- the active-run pointer --------------------------------------------------------------------------------

fn active_path(root: &Path) -> PathBuf {
    root.join("active.json")
}

pub(crate) fn read_active(root: &Path) -> Result<Option<ActivePointer>, AgentRefusal> {
    let invalid = |error: &dyn std::fmt::Display| {
        AgentRefusal::failure("invalid_state", format!("active run pointer is missing or invalid: {error}"))
    };
    let raw = match fs::read(active_path(root)) {
        Ok(raw) => raw,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(invalid(&error)),
    };
    let active: ActivePointer = serde_json::from_slice(&raw).map_err(|error| invalid(&error))?;
    if active.schema_version != SCHEMA_VERSION {
        return Err(AgentRefusal::failure(
            "invalid_state",
            "active run pointer has an unsupported schema",
        ));
    }
    validate_run_id(&active.run_id)?;
    Ok(Some(active))
}

/// Claims the slot for `active`, answering whether this caller won.
///
/// A complete private file first, then `link(2)` onto the final name: the link either creates `active.json` whole
/// or fails with EEXIST, so two supervisors racing for the slot cannot both win and a reader never sees a partial
/// pointer.
pub(crate) fn claim_active(root: &Path, active: &ActivePointer) -> Result<bool, AgentRefusal> {
    let candidate = tempfile::Builder::new()
        .prefix(".active.")
        .suffix(".tmp")
        .permissions(fs::Permissions::from_mode(0o600))
        .tempfile_in(root)
        .map_err(AgentRefusal::internal)?;
    let mut file = candidate.as_file();
    encode(active)
        .and_then(|encoded| file.write_all(&encoded))
        .and_then(|()| file.sync_all())
        .map_err(AgentRefusal::internal)?;
    // The candidate is removed when it drops, whichever way the link went.
    match fs::hard_link(candidate.path(), active_path(root)) {
        Ok(()) => {}
        Err(error) if error.kind() == io::ErrorKind::AlreadyExists => return Ok(false),
        Err(error) => return Err(AgentRefusal::internal(error)),
    }
    fsync_directory(root).map_err(AgentRefusal::internal)?;
    Ok(true)
}

/// Frees the slot, but only when `run_id` still owns it.
pub(crate) fn clear_active(root: &Path, run_id: &str) {
    if let Ok(Some(active)) = read_active(root)
        && active.run_id == run_id
    {
        let _ = fs::remove_file(active_path(root));
        let _ = fsync_directory(root);
    }
}

pub(crate) fn require_active_ownership(root: &Path, expected: &ActivePointer) -> Result<(), AgentRefusal> {
    let lost = |message: String| AgentRefusal::refused("active_ownership_lost", message);
    let observed = read_active(root).map_err(|error| lost(format!("cannot verify active ownership for {}: {error}", expected.run_id)))?;
    let same = observed.is_some_and(|observed| {
        observed.run_id == expected.run_id
            && observed.supervisor_pid == expected.supervisor_pid
            && observed.supervisor_pgid == expected.supervisor_pgid
            && observed.supervisor_start == expected.supervisor_start
    });
    if !same {
        return Err(lost(format!(
            "active ownership changed before {} could launch its child",
            expected.run_id
        )));
    }
    Ok(())
}

// --- terminal states ---------------------------------------------------------------------------------------

/// Writes `state` as the run's current state. A failed write is not reported: the next reconcile reads whatever
/// is on disk and repairs it, which is the only recovery there is for a disk that refuses a write.
pub(crate) fn publish_state(root: &Path, state: &RunState) {
    let _ = write_json_atomic(&RunPaths::new(root, &state.run_id).state, state);
}

pub(crate) fn mark_orphaned(system: &dyn System, root: &Path, state: &RunState, failure: &str) -> RunState {
    let mut orphaned = state.clone();
    orphaned.phase = Phase::Orphaned;
    orphaned.orphaned_at.get_or_insert_with(|| stamp(system.now()));
    if orphaned.pending_outcome.is_none() {
        orphaned.pending_outcome = state.outcome.clone();
    }
    orphaned.outcome = Some(Outcome::Orphaned);
    orphaned.failure = Some(failure.to_owned());
    publish_state(root, &orphaned);
    orphaned
}

/// What a terminal write adds beyond the exit.
#[derive(Default)]
pub(crate) struct FinishExtra {
    pub failure: Option<String>,
    pub cancellation: Option<CancellationRecord>,
    pub orphaned_resolved_at: Option<String>,
}

/// Writes the run's terminal state, refusing an outcome nobody declared.
///
/// An outcome the host does not know reaches it as a run that merely looks odd, so it is refused here, where the
/// caller that invented it is still on the stack.
pub(crate) fn finish_state(
    system: &dyn System,
    root: &Path,
    running: &RunState,
    code: Option<i32>,
    signal: Option<String>,
    outcome: Outcome,
    extra: FinishExtra,
) -> Result<RunState, AgentRefusal> {
    validate_outcome_to_write(&outcome).map_err(|error| AgentRefusal::failure("invalid_outcome", error.to_string()))?;
    let mut finished = running.clone();
    if extra.failure.is_some() {
        finished.failure = extra.failure;
    }
    if extra.cancellation.is_some() {
        finished.cancellation = extra.cancellation;
    }
    if extra.orphaned_resolved_at.is_some() {
        finished.orphaned_resolved_at = extra.orphaned_resolved_at;
    }
    finished.phase = Phase::Finished;
    finished.outcome = Some(outcome);
    finished.exit_code = Some(super::identity::normalized_exit_code(code, signal.as_deref()));
    // Present-and-null when absent: the host asserts on the explicit nulls of a terminal state.
    finished.native_exit_code = Some(code);
    finished.signal = Some(signal);
    finished.finished_at = Some(stamp(system.now()));
    publish_state(root, &finished);
    Ok(finished)
}

/// How long a reconcile waits for a supervisor that reaped its child to make its terminal state visible.
const HANDOFF_GRACE: Duration = Duration::from_millis(500);

/// Answers what a run's recorded state means *now*, repairing it when the truth has moved.
///
/// Five branches, and none of them is redundant. A supervisor can vanish between any two of its own writes, and
/// the process group it was watching can outlive it - JCEF's helpers do exactly that - so a terminal state with
/// live members is a slot that must not be handed out.
pub(crate) fn reconcile(system: &dyn System, root: &Path, state: RunState) -> Result<RunState, AgentRefusal> {
    let group_alive = |pgid: Option<i32>| pgid.is_some_and(|pgid| system.group_alive(pgid));
    match state.phase {
        Phase::Finished => {
            if group_alive(state.pgid) {
                return Ok(mark_orphaned(
                    system,
                    root,
                    &state,
                    "recorded process group still has members after terminal state",
                ));
            }
            clear_active(root, &state.run_id);
            Ok(state)
        }
        Phase::Orphaned => {
            if group_alive(state.pgid) {
                return Ok(state);
            }
            let outcome = state.pending_outcome.clone().unwrap_or(Outcome::SupervisorLost);
            let finished = finish_state(
                system,
                root,
                &state,
                Some(state.exit_code.unwrap_or(255)),
                state.signal.clone().flatten(),
                outcome,
                FinishExtra {
                    orphaned_resolved_at: Some(stamp(system.now())),
                    ..FinishExtra::default()
                },
            )?;
            clear_active(root, &state.run_id);
            Ok(finished)
        }
        Phase::Starting => {
            let any_identity = state.supervisor_pid.is_some() || state.supervisor_pgid.is_some() || state.supervisor_start.is_some();
            let complete_identity = state.supervisor_pid.is_some_and(|pid| pid > 0)
                && state.supervisor_pgid.is_some_and(|pgid| pgid > 0)
                && state.supervisor_start.as_deref().is_some_and(|start| !start.is_empty());
            if any_identity && !complete_identity {
                return Err(AgentRefusal::failure(
                    "invalid_state",
                    format!("starting state for {} has incomplete supervisor identity", state.run_id),
                ));
            }
            if complete_identity && supervisor_is_alive(system, &state) {
                return Ok(state);
            }
            // A supervisor that has not recorded itself yet gets the start budget from the run's creation.
            let created = state.created_at.as_deref().and_then(parse_stamp);
            if created.is_some_and(|created| system.now() < created.checked_add(START_TIMEOUT).unwrap_or(created)) {
                return Ok(state);
            }
            let finished = finish_state(
                system,
                root,
                &state,
                Some(255),
                None,
                Outcome::SupervisorLost,
                FinishExtra {
                    failure: Some("supervisor exited before recording the child identity".to_owned()),
                    ..FinishExtra::default()
                },
            )?;
            clear_active(root, &state.run_id);
            Ok(finished)
        }
        Phase::Running => {
            if child_is_alive(system, &state) || supervisor_is_alive(system, &state) {
                return Ok(state);
            }
            // The supervisor can exit immediately after reaping the child and just before its atomic
            // terminal-state rename becomes visible. Give that handoff a bounded grace period before
            // synthesizing supervisor_lost.
            let handoff = deadline(system, HANDOFF_GRACE);
            while system.now() < handoff {
                system.sleep(POLL_INTERVAL);
                let latest = read_state(root, &state.run_id)?;
                if latest.phase == Phase::Finished {
                    clear_active(root, &state.run_id);
                    return Ok(latest);
                }
                if child_is_alive(system, &latest) || supervisor_is_alive(system, &latest) {
                    return Ok(latest);
                }
            }
            if group_alive(state.pgid) {
                return Ok(mark_orphaned(
                    system,
                    root,
                    &state,
                    "leader and supervisor identities vanished while the recorded process group still has members",
                ));
            }
            let finished = finish_state(
                system,
                root,
                &state,
                Some(255),
                None,
                Outcome::SupervisorLost,
                FinishExtra {
                    failure: Some("child exited without an observable wait status".to_owned()),
                    ..FinishExtra::default()
                },
            )?;
            clear_active(root, &state.run_id);
            Ok(finished)
        }
    }
}
