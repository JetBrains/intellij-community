//! The run marker: `<worker>/run.json` names the run in flight on a worker, its lease and its process.
//!
//! A run writes its marker under the lifecycle lock of its worker, and it removes the marker under the lock at its
//! end ([`crate::worker::lease::with_run_marker`]). Between the two ends the lock is free, so a read of the worker
//! answers during the run. Every lifecycle operation of another process refuses while the marker names a live
//! process: [`Manager::require_no_run`] is that guard. A marker whose process is gone is stale, and the next
//! operation under the lock removes it with a note.
//!
//! The marker of this process is never in the way, because a run restarts its own worker under its own marker.

use avl_base::clock::stamp;
use avl_base::fs::write_atomically;
use avl_base::{Config, Exit, Refusal, RefusalExt, SCHEMA_VERSION, Scope};
use avl_host_sys::{Ctx, ProcessIdentity};
use avl_report::digest::sha256_text;
use serde::{Deserialize, Serialize};
use serde_json::json;

use crate::worker::docker::remove_if_present;
use crate::worker::secure::read_json_record;
use crate::worker::worker::{Lease, Manager};

#[cfg(test)]
#[cfg(unix)]
mod tests;

/// The run in flight on a worker, as `<worker>/run.json` records it. Every field is required: this controller writes
/// all of them, and a marker without one is corrupt.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct RunMarker {
    pub schema_version: u32,
    pub worker: String,
    /// The id of the run, which its journal also names.
    pub run_id: String,
    /// The lifecycle lock records the same word: `run`, `shard-run` or `flake`.
    pub operation: String,
    /// The hex SHA-256 of the lease token, so the marker holds no second clear copy of the secret.
    pub lease_digest: String,
    /// The process of the run.
    pub process: ProcessIdentity,
    pub started_at: String,
}

impl RunMarker {
    /// Whether every field is there, for the marker of `worker`.
    fn is_complete(&self, worker: &str) -> bool {
        self.schema_version == SCHEMA_VERSION
            && self.worker == worker
            && !self.run_id.is_empty()
            && !self.operation.is_empty()
            && !self.lease_digest.is_empty()
            && self.process.pid > 0
            && !self.process.process_start.is_empty()
            && !self.process.process_command.is_empty()
            && !self.started_at.is_empty()
    }

    /// Whether the run of this marker holds `lease`.
    pub(crate) fn names_lease(&self, lease: &Lease) -> bool {
        self.worker == lease.worker && self.lease_digest == lease_digest(lease)
    }
}

/// The digest of the lease token that a run marker records.
pub(crate) fn lease_digest(lease: &Lease) -> String {
    sha256_text(&lease.token)
}

/// The run marker on `worker`, or `None` when the worker has no run marker.
///
/// A missing file is no marker. Any other file that does not read as a complete marker is `corrupt_run_marker`, as an
/// unreadable lease is `corrupt_lease`. A marker that cannot be read must never read as "no run".
pub(crate) fn read_run_marker(settings: &Config, worker: &str) -> Result<Option<RunMarker>, Refusal> {
    let path = settings.run_marker_path(worker);
    let corrupt = |reason: String| {
        Refusal::new(
            "corrupt_run_marker",
            Exit::FAILURE,
            format!("invalid run marker at {}{reason}", path.display()),
        )
    };
    match read_json_record::<RunMarker>(&path).map_err(|error| corrupt(format!(": {error}")))? {
        Some(marker) if !marker.is_complete(worker) => Err(corrupt(String::new())),
        marker => Ok(marker),
    }
}

/// Removes the run marker of `worker`. A worker without a marker is no error.
pub(crate) fn remove_run_marker(settings: &Config, worker: &str) -> Result<(), Refusal> {
    remove_if_present(&settings.run_marker_path(worker))
}

/// What the run marker of a worker says.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum RunMarkerState {
    /// The worker has no run marker.
    None,
    /// The marker names this process: a run of this process is in flight.
    Ours(RunMarker),
    /// The marker names a live process: a run of another process is in flight.
    Live(RunMarker),
    /// The marker names a process that is gone.
    Stale(RunMarker),
}

impl Manager {
    /// Judges the run marker of `worker`, and changes nothing.
    ///
    /// A marker of the process that this manager speaks for is [`RunMarkerState::Ours`], with no liveness probe. The
    /// whole identity decides, so a marker of a dead process whose pid the host gave to this process is not ours.
    /// Every other marker is live while its process is ([`avl_host_sys::Runner::identity_alive`]). A probe without an
    /// answer is the refusal `probe_unanswered`, and not a stale marker.
    pub(crate) async fn judge_run_marker(&self, ctx: &Ctx, worker: &str) -> Result<RunMarkerState, Refusal> {
        let Some(marker) = read_run_marker(self.settings(), worker)? else {
            return Ok(RunMarkerState::None);
        };
        if self.is_this_process(ctx, &marker.process).await? {
            return Ok(RunMarkerState::Ours(marker));
        }
        Ok(if self.runner().identity_alive(ctx, &marker.process).await? {
            RunMarkerState::Live(marker)
        } else {
            RunMarkerState::Stale(marker)
        })
    }

    /// Whether `process` is the process that this manager speaks for, by its whole identity.
    async fn is_this_process(&self, ctx: &Ctx, process: &ProcessIdentity) -> Result<bool, Refusal> {
        if process.pid != self.locks().pid() {
            return Ok(false);
        }
        Ok(self.locks().identity(ctx).await?.is_some_and(|this| this == process))
    }

    /// Refuses `lease_busy` while a run of another live process is in flight on `worker`. A stale marker is removed
    /// with a note, and a marker of this process passes. `operation` names the refused operation in the details.
    ///
    /// The caller holds the lifecycle lock of the worker, or its own run marker is on the worker. Without either, a run
    /// can write its marker between this check and the operation.
    pub(crate) async fn require_no_run(&self, ctx: &Ctx, worker: &str, operation: &str) -> Result<(), Refusal> {
        match self.judge_run_marker(ctx, worker).await? {
            RunMarkerState::None | RunMarkerState::Ours(_) => Ok(()),
            RunMarkerState::Stale(marker) => self.remove_stale_run_marker(worker, &marker),
            RunMarkerState::Live(marker) => Err(run_in_flight(worker, &marker, operation)),
        }
    }

    /// Removes the marker of a run whose process is gone, and notes it. The caller holds the lifecycle lock.
    pub(crate) fn remove_stale_run_marker(&self, worker: &str, marker: &RunMarker) -> Result<(), Refusal> {
        remove_run_marker(self.settings(), worker)?;
        self.reporter().note(
            format!(
                "removed the run marker of {} on {worker}: its process {} is gone",
                marker.run_id, marker.process.pid
            ),
            Some(&Scope::worker(worker)),
        );
        Ok(())
    }

    /// Writes the run marker of the run `run_id`, which holds `lease`, and answers it. The caller holds the lifecycle
    /// lock of the worker and found no run marker on it.
    ///
    /// The marker names the identity of the process that this manager speaks for. The lock manager finds it once, and
    /// its lock records name the same identity ([`avl_host_sys::lock::LockManager::identity`]).
    pub(crate) async fn write_run_marker(&self, ctx: &Ctx, lease: &Lease, run_id: &str, operation: &str) -> Result<RunMarker, Refusal> {
        let process = self.locks().identity(ctx).await?.cloned().ok_or_else(|| {
            Refusal::internal(format!(
                "the process table holds no process {}, which runs this controller",
                self.locks().pid()
            ))
        })?;
        let marker = RunMarker {
            schema_version: SCHEMA_VERSION,
            worker: lease.worker.clone(),
            run_id: run_id.to_owned(),
            operation: operation.to_owned(),
            lease_digest: lease_digest(lease),
            process,
            started_at: stamp(jiff::Timestamp::now()),
        };
        let mut encoded =
            serde_json::to_vec(&marker).map_err(|error| Refusal::internal(format!("cannot encode the run marker: {error}")))?;
        encoded.push(b'\n');
        write_atomically(&self.settings().run_marker_path(&lease.worker), &encoded, 0o600)?;
        Ok(marker)
    }
}

/// Writes a run marker of `process` for `lease` by hand, as another controller writes one, and answers it. For a suite.
#[cfg(all(test, unix))]
pub(crate) fn write_marker_by_hand(settings: &Config, lease: &Lease, run_id: &str, process: ProcessIdentity) -> RunMarker {
    let marker = RunMarker {
        schema_version: SCHEMA_VERSION,
        worker: lease.worker.clone(),
        run_id: run_id.to_owned(),
        operation: "run".to_owned(),
        lease_digest: lease_digest(lease),
        process,
        started_at: "2026-10-13T12:00:00.000Z".to_owned(),
    };
    let encoded = serde_json::to_vec(&marker).expect("a run marker encodes");
    std::fs::write(settings.run_marker_path(&lease.worker), encoded).expect("the run marker is written");
    marker
}

/// The refusal of `operation` while the run of `marker`, of another live process, is in flight on `worker`.
fn run_in_flight(worker: &str, marker: &RunMarker, operation: &str) -> Refusal {
    Refusal::new(
        "lease_busy",
        Exit::TEMP_FAIL,
        format!(
            "worker {worker} has run {} in flight (pid {} since {}); wait for it to finish",
            marker.run_id, marker.process.pid, marker.started_at
        ),
    )
    .with_details(json!({
        "worker": worker,
        "operation": operation,
        "runId": marker.run_id,
        "pid": marker.process.pid,
        "startedAt": marker.started_at,
    }))
}
