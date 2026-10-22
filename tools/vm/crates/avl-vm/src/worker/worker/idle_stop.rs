//! The idle stop of a worker on the Apple `container` engine ([`Config::idle_stop`]).
//!
//! The controller is a command line with no resident process. So a lease release writes a record with a deadline
//! and starts a detached `pool idle-stop` process. That process waits for the deadline and then stops the container
//! when no lease took the worker. A lease acquisition and a start remove the record, so the process then finds no
//! record of its nonce and exits. A process that died before its deadline stops nothing, so each release also stops
//! the other workers whose deadline passed without their process.

use std::io::Write;
use std::sync::Arc;
use std::time::Duration;

use avl_base::clock::stamp;
use avl_base::fs::write_atomically;
use avl_base::{Config, Exit, Outcome, Refusal, RefusalExt, SCHEMA_VERSION, new_id};
use avl_host_sys::Ctx;
use avl_host_sys::private::{PrivateOpen, open_private_file};
use jiff::{SignedDuration, Timestamp};
use serde::{Deserialize, Serialize};
use serde_json::json;

use super::{Manager, StopState, read_lease};
use crate::worker::secure::read_json_record;

/// How often a waiting `pool idle-stop` process reads the record again. A record that a later operation removed or
/// replaced ends the process at the next read, so a busy pool keeps few waiting processes.
pub(crate) const IDLE_STOP_POLL: Duration = Duration::from_secs(30);

/// The record of one scheduled idle stop, `<worker>/idle-stop.json`.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct IdleStopRecord {
    pub schema_version: u32,
    pub worker: String,
    /// When the lease release wrote the record.
    pub released_at: String,
    /// When the worker stops, unless a lease or a start takes it first.
    pub deadline: String,
    /// The identity of the detached process that waits for this record. A record of another nonce belongs to a later
    /// release.
    pub nonce: String,
}

/// What the detached process of one idle stop is started with: `pool idle-stop <worker> --nonce <nonce>`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct IdleStopRequest {
    pub worker: String,
    pub nonce: String,
}

/// Starts the detached process of an idle stop. The controller passes [`spawn_detached_idle_stop`]. A suite passes a
/// recorder, because a test binary cannot run the `vm` command line.
pub(crate) type IdleStopSpawner = Arc<dyn Fn(&Manager, &IdleStopRequest) -> Result<(), Refusal> + Send + Sync>;

/// The record of the worker, or `None` when there is none or it cannot be read as a record of this worker.
pub(crate) fn read_idle_stop_record(settings: &Config, worker: &str) -> Option<IdleStopRecord> {
    let record: IdleStopRecord = read_json_record(&settings.idle_stop_record_path(worker)).ok().flatten()?;
    (record.schema_version == SCHEMA_VERSION && record.worker == worker).then_some(record)
}

/// Removes the record of the worker, so a waiting process stops nothing. A missing record is no error.
pub(crate) fn remove_idle_stop_record(settings: &Config, worker: &str) -> Result<(), Refusal> {
    let path = settings.idle_stop_record_path(worker);
    match std::fs::remove_file(&path) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(Refusal::new(
            "state_write_failed",
            Exit::FAILURE,
            format!("cannot remove {}: {error}", path.display()),
        )),
    }
}

/// What one decision of an idle stop did.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum IdleStopVerdict {
    /// The container ran and was stopped.
    Stopped,
    /// The container did not run, or the engine did not run. The record is gone all the same.
    AlreadyStopped,
    /// The record is gone or has another nonce: a later operation took the worker.
    Superseded,
    /// A lease holds the worker.
    Leased,
    /// Another operation holds the lifecycle lock of the worker. The process asks again later.
    Busy,
}

impl IdleStopVerdict {
    /// The word of the outcome.
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::Stopped => "stopped",
            Self::AlreadyStopped => "already-stopped",
            Self::Superseded => "superseded",
            Self::Leased => "leased",
            Self::Busy => "busy",
        }
    }
}

impl Manager {
    /// Sets the start of the detached process, for a suite.
    #[cfg(test)]
    pub(crate) fn with_idle_stop_spawner(mut self, spawner: IdleStopSpawner) -> Self {
        self.idle_stop = spawner;
        self
    }

    /// Schedules the idle stop of a worker whose lease the caller just removed, under its lifecycle lock. Answers the
    /// deadline, or `None` when the pool has no idle stop.
    ///
    /// A grace of zero stops the worker here. Otherwise the record goes first and the process second, so the process
    /// always finds its record. A process that cannot start is a note and no refusal: the release is done, and the
    /// worker then runs until `pool stop`.
    ///
    /// Then it stops the other workers whose process died ([`Manager::stop_stranded_idle_workers`]).
    pub(crate) async fn schedule_idle_stop(&self, ctx: &Ctx, worker: &str) -> Result<Option<String>, Refusal> {
        let Some(grace) = self.settings.idle_stop else {
            return Ok(None);
        };
        let scheduled = self.schedule_own_idle_stop(ctx, worker, grace).await?;
        self.stop_stranded_idle_workers(ctx, worker).await;
        Ok(scheduled)
    }

    /// The idle stop of the released worker itself, for [`Manager::schedule_idle_stop`].
    async fn schedule_own_idle_stop(&self, ctx: &Ctx, worker: &str, grace: Duration) -> Result<Option<String>, Refusal> {
        let now = Timestamp::now();
        if grace.is_zero() {
            remove_idle_stop_record(&self.settings, worker)?;
            self.stop_without_lifecycle_lock(ctx, worker, None).await?;
            self.note(worker, format!("stopped {worker} at its release, because AIR_VM_IDLE_STOP is 0"));
            return Ok(Some(stamp(now)));
        }
        let deadline = SignedDuration::try_from(grace)
            .ok()
            .and_then(|grace| now.checked_add(grace).ok())
            .unwrap_or(Timestamp::MAX);
        let record = IdleStopRecord {
            schema_version: SCHEMA_VERSION,
            worker: worker.to_owned(),
            released_at: stamp(now),
            deadline: stamp(deadline),
            nonce: new_id(),
        };
        let mut encoded =
            serde_json::to_vec(&record).map_err(|error| Refusal::internal(format!("cannot encode the idle stop record: {error}")))?;
        encoded.push(b'\n');
        write_atomically(&self.settings.idle_stop_record_path(worker), &encoded, 0o600)?;
        let request = IdleStopRequest {
            worker: worker.to_owned(),
            nonce: record.nonce.clone(),
        };
        if let Err(refusal) = (self.idle_stop)(self, &request) {
            remove_idle_stop_record(&self.settings, worker)?;
            self.note(worker, format!("{}; {worker} keeps running until pool stop", refusal.message));
            return Ok(None);
        }
        Ok(Some(record.deadline))
    }

    /// Stops each worker of the pool other than `released` whose record passed its deadline more than one
    /// [`IDLE_STOP_POLL`] ago. A live process decides within one poll after its deadline, so such a record has no
    /// process: a kill, a crash or a logout ended it.
    ///
    /// The decision is [`Manager::idle_stop_worker`], under the lifecycle lock of that worker, which it only tries
    /// to take. So the caller can hold the lock of `released`, and a busy worker is kept. The nonce and the lease
    /// check keep a worker that a later operation took, and a process that is only late then finds no record. A
    /// failure is a note and no refusal, because the release of `released` is done.
    async fn stop_stranded_idle_workers(&self, ctx: &Ctx, released: &str) {
        let now = Timestamp::now();
        for worker in self.settings.workers.iter().filter(|worker| *worker != released) {
            let Some(record) = read_idle_stop_record(&self.settings, worker) else {
                continue;
            };
            let Ok(deadline) = record.deadline.parse::<Timestamp>() else {
                continue;
            };
            let overdue = Duration::try_from(now.duration_since(deadline)).unwrap_or(Duration::ZERO);
            if overdue <= IDLE_STOP_POLL {
                continue;
            }
            match self.idle_stop_worker(ctx, worker, &record.nonce).await {
                Ok(IdleStopVerdict::Stopped) => self.note(
                    worker,
                    format!(
                        "stopped {worker}, whose idle stop passed its deadline at {} without its detached process",
                        record.deadline
                    ),
                ),
                Ok(_) => {}
                Err(refusal) => self.note(
                    worker,
                    format!(
                        "cannot stop {worker}, whose idle stop passed its deadline at {} without its detached process: {}",
                        record.deadline, refusal.message
                    ),
                ),
            }
        }
    }

    /// The decision of one idle stop, under the lifecycle lock of the worker: stop the container when the record of
    /// `nonce` is there and no lease holds the worker.
    ///
    /// The engine is reached and never started ([`crate::worker::docker::Docker::reach_engine`]), so a server that is
    /// down answers [`IdleStopVerdict::AlreadyStopped`].
    pub(crate) async fn idle_stop_worker(&self, ctx: &Ctx, worker: &str, nonce: &str) -> Result<IdleStopVerdict, Refusal> {
        let decided = self
            .try_with_lifecycle_lock(ctx, worker, "idle-stop", async {
                if read_idle_stop_record(&self.settings, worker).is_none_or(|record| record.nonce != nonce) {
                    return Ok(IdleStopVerdict::Superseded);
                }
                if read_lease(&self.settings.lease_path(worker))?.is_some() {
                    return Ok(IdleStopVerdict::Leased);
                }
                let stopped = self.stop_without_lifecycle_lock(ctx, worker, None).await?;
                remove_idle_stop_record(&self.settings, worker)?;
                Ok(match stopped {
                    StopState::Stopped => IdleStopVerdict::Stopped,
                    StopState::AlreadyStopped => IdleStopVerdict::AlreadyStopped,
                })
            })
            .await?;
        Ok(decided.unwrap_or(IdleStopVerdict::Busy))
    }

    /// `pool idle-stop <worker> --nonce <nonce>`, the detached process of one idle stop: wait for the deadline of the
    /// record, then decide ([`Manager::idle_stop_worker`]).
    ///
    /// It reads the record again every [`IDLE_STOP_POLL`], and it exits as soon as the record is gone or has another
    /// nonce. A busy lifecycle lock makes it ask again after the same pause.
    pub(crate) async fn pool_idle_stop(&self, ctx: &Ctx, worker: &str, nonce: &str) -> Result<Outcome, Refusal> {
        self.settings.require_pool_worker(worker)?;
        let verdict = loop {
            let Some(record) = read_idle_stop_record(&self.settings, worker).filter(|record| record.nonce == nonce) else {
                break IdleStopVerdict::Superseded;
            };
            let deadline: Timestamp = record.deadline.parse().map_err(|error| {
                Refusal::new(
                    "corrupt_idle_stop",
                    Exit::FAILURE,
                    format!("the idle stop record of {worker} has the deadline {:?}: {error}", record.deadline),
                )
            })?;
            let left = Duration::try_from(deadline.duration_since(Timestamp::now())).unwrap_or(Duration::ZERO);
            let pause = if left.is_zero() {
                match self.idle_stop_worker(ctx, worker, nonce).await? {
                    IdleStopVerdict::Busy => IDLE_STOP_POLL,
                    decided => break decided,
                }
            } else {
                left.min(IDLE_STOP_POLL)
            };
            if !ctx.sleep(pause).await {
                return Err(Refusal::new(
                    "idle_stop_interrupted",
                    Exit::SOFTWARE,
                    format!("an interrupt ended the idle stop of {worker}"),
                ));
            }
        };
        Ok(Outcome {
            data: json!({ "worker": worker, "idleStop": verdict.as_str() }),
            text: format!("{worker}: idle_stop={}", verdict.as_str()),
        })
    }
}

/// Starts `<this executable> --backend docker pool idle-stop <worker> --nonce <nonce>` in a session of its own,
/// through the runner of the manager, with its output appended to `<worker>/idle-stop.log`.
///
/// The process outlives the controller, as the detached trace viewer does ([`avl_host_sys::Runner::spawn_session`]).
/// The environment of the runner carries every setting, so the process resolves the same pool. It runs in the
/// directory of the worker, which holds the record and the log. The directory of the release is a checkout or a
/// lease slot, and the process must not hold it for the whole grace.
pub(crate) fn spawn_detached_idle_stop(manager: &Manager, request: &IdleStopRequest) -> Result<(), Refusal> {
    let settings = manager.settings();
    let failed = |what: String| Refusal::new("idle_stop_start_failed", Exit::CANT_CREATE, what);
    let program = std::env::current_exe().map_err(|error| failed(format!("cannot name the controller executable: {error}")))?;
    let log_path = settings.idle_stop_log_path(&request.worker);
    let unusable = |error: std::io::Error| failed(format!("cannot open {}: {error}", log_path.display()));
    let mut log = open_private_file(&log_path, PrivateOpen::Append).map_err(unusable)?;
    let log_err = log.try_clone().map_err(unusable)?;
    // A header the log cannot take costs only the header.
    let _ = writeln!(log, "\n--- {}: the idle stop of {} waits", stamp(Timestamp::now()), request.worker);
    let argv = [
        program.into_os_string(),
        "--backend".into(),
        settings.backend.as_str().into(),
        "pool".into(),
        "idle-stop".into(),
        request.worker.clone().into(),
        "--nonce".into(),
        request.nonce.clone().into(),
    ];
    manager
        .runner()
        .spawn_session(&argv, &settings.worker_dir(&request.worker), log, log_err)
        .map(drop)
        .map_err(|error| failed(format!("cannot start the idle stop of {}: {error}", request.worker)))
}
