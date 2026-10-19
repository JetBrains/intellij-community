//! The `pool` commands: `init`, `start`, `stop`, `gc`, `recycle`.
//!
//! What a command line says is parsed by `avl-vm`; what arrives here is a [`PoolCommand`].

use std::collections::BTreeMap;
use std::fmt;
use std::path::{Path, PathBuf};
use std::str::FromStr;

use avl_base::{Exit, Outcome, Refusal};
use avl_host_sys::Ctx;
use avl_host_sys::guest::ensure_host_paths;
use serde_json::{Value, json};

use super::{Lease, Manager, StartState, StopState, read_lease};
use crate::lane::ide::{IdeRetention, gc_guest_ides, gc_note};
use crate::worker::lease::{receipt_for_path, remove_lease_receipts};
use avl_base::RefusalExt;

/// One `pool` invocation.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum PoolCommand {
    /// Materializes the pool. `golden` names the sealed golden a macOS pool is cloned from instead of the configured
    /// one; a Linux or Parallels pool takes none.
    Init {
        golden: Option<String>,
    },
    Start(PoolTarget),
    Stop(PoolTarget),
    Gc,
    /// Rebuilds workers from scratch. The target is never defaulted, where `start` and `stop` default it to the
    /// whole pool: this one deletes clones, so recycling every worker on the host is something an operator types.
    Recycle(PoolTarget),
    /// The detached process of the idle stop that a lease release started ([`Manager::pool_idle_stop`]).
    IdleStop {
        worker: String,
        nonce: String,
    },
}

/// Which workers of the pool a command is about.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum PoolTarget {
    All,
    Worker(String),
}

impl FromStr for PoolTarget {
    type Err = Refusal;

    /// `all` or a worker name. The name is checked against the pool by the command, which knows the pool.
    fn from_str(value: &str) -> Result<Self, Refusal> {
        match value {
            "" => Err(Refusal::usage("a pool target is all or a worker name")),
            "all" => Ok(Self::All),
            worker => Ok(Self::Worker(worker.to_owned())),
        }
    }
}

impl fmt::Display for PoolTarget {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::All => f.write_str("all"),
            Self::Worker(worker) => f.write_str(worker),
        }
    }
}

/// Which of `pool start` and `pool stop` one invocation is: the two share one driver.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Switch {
    Start,
    Stop,
}

impl Switch {
    /// The `action` of the outcome.
    const fn as_str(self) -> &'static str {
        match self {
            Self::Start => "start",
            Self::Stop => "stop",
        }
    }

    /// The word for a changed worker in the message of an incomplete verb.
    const fn done_word(self) -> &'static str {
        match self {
            Self::Start => "started",
            Self::Stop => "stopped",
        }
    }

    /// The refusal of a verb over `all` that did not change every worker.
    const fn incomplete_code(self) -> &'static str {
        match self {
            Self::Start => "start_incomplete",
            Self::Stop => "stop_incomplete",
        }
    }
}

/// What a `pool` verb did to each worker of its target: the workers that it changed, and the refusal of each other
/// worker.
///
/// Over `all`, a refused worker does not stop the verb. The job of the verb is the whole pool, so it goes on with the
/// next worker and refuses at the end with [`PartialPool::incomplete`]. A worker that it could not change is that job
/// unfinished. For `pool gc`, a kept busy slot is success, so the gc does not refuse at the end.
struct PartialPool {
    /// Whether the target is `all`. One named worker keeps its own refusal.
    whole_pool: bool,
    /// The changed workers, in pool order.
    done: Vec<String>,
    /// The code and the message of each refused worker.
    refused: BTreeMap<String, Value>,
    /// `<worker> (<code>)` for each refused worker, in pool order.
    names: Vec<String>,
    /// Whether each refusal was temporary.
    retryable: bool,
}

impl PartialPool {
    fn new(target: &PoolTarget) -> Self {
        Self {
            whole_pool: *target == PoolTarget::All,
            done: Vec::new(),
            refused: BTreeMap::new(),
            names: Vec::new(),
            retryable: true,
        }
    }

    /// Adds the answer of `worker`. It answers the value of a changed worker, and `None` for a refusal that the verb
    /// keeps for its end.
    ///
    /// Two kinds of refusal end the verb at once, as its answer. The first is the refusal of one named worker, which
    /// is about that worker. It carries the code that a caller branches on, and evidence that only it has.
    /// `tart_exited` carries the tail of the run log, and `worker_leased` names the release command. The second is a
    /// refusal under a cancelled context, which belongs to the interrupt. The operator stopped the verb, and the
    /// runner asks each new child to stop at once. So the next worker would get half an operation.
    fn add<T>(&mut self, ctx: &Ctx, worker: &str, answer: Result<T, Refusal>) -> Result<Option<T>, Refusal> {
        let refusal = match answer {
            Ok(value) => {
                self.done.push(worker.to_owned());
                return Ok(Some(value));
            }
            Err(refusal) if !self.whole_pool || ctx.is_cancelled() => return Err(refusal),
            Err(refusal) => refusal,
        };
        self.names.push(format!("{worker} ({})", refusal.code));
        // The exit code decides, so no list of codes has to follow a new refusal. A temporary refusal is one that a
        // later retry can pass.
        self.retryable &= refusal.exit == Exit::TEMP_FAIL;
        self.refused
            .insert(worker.to_owned(), json!({ "code": refusal.code, "message": refusal.message }));
        Ok(None)
    }

    /// The refusal `code` of a verb that did not change every worker, with `data` as its details. The message names
    /// the changed workers after `done_word`, and each other worker with the code of its refusal.
    ///
    /// The exit tells a caller what to do next. It is temporary when each refusal was temporary, because each such
    /// cause goes away without help. It is a failure when a worker is broken.
    fn incomplete(self, code: &'static str, done_word: &str, data: Value) -> Refusal {
        let exit = if self.retryable { Exit::TEMP_FAIL } else { Exit::FAILURE };
        Refusal::new(
            code,
            exit,
            format!(
                "{done_word} {}; not {done_word}: {}",
                render_list(&self.done),
                self.names.join(", ")
            ),
        )
        .with_details(data)
    }
}

impl Manager {
    /// The `pool` command. `lease_file` is `--lease-file`, which only `recycle` reads: see [`Manager::pool_recycle`].
    pub(crate) async fn pool(&self, ctx: &Ctx, command: PoolCommand, lease_file: Option<&Path>) -> Result<Outcome, Refusal> {
        match command {
            PoolCommand::Init { golden } => self.pool_init(ctx, golden.as_deref()).await,
            PoolCommand::Gc => self.pool_gc(ctx).await,
            PoolCommand::Recycle(target) => self.pool_recycle(ctx, &target, lease_file).await,
            PoolCommand::Start(target) => self.pool_start_or_stop(ctx, Switch::Start, &target).await,
            PoolCommand::Stop(target) => self.pool_start_or_stop(ctx, Switch::Stop, &target).await,
            PoolCommand::IdleStop { worker, nonce } => self.pool_idle_stop(ctx, &worker, &nonce).await,
        }
    }

    fn targets(&self, target: &PoolTarget) -> Vec<String> {
        match target {
            PoolTarget::All => self.settings.workers.clone(),
            PoolTarget::Worker(worker) => vec![worker.clone()],
        }
    }

    /// `pool start` and `pool stop`. Over `all`, the verb changes each worker that it can. It then refuses
    /// `start_incomplete` or `stop_incomplete` when it did not change every worker ([`PartialPool`]).
    async fn pool_start_or_stop(&self, ctx: &Ctx, switch: Switch, target: &PoolTarget) -> Result<Outcome, Refusal> {
        self.prepare_runtime_dirs()?;
        let mut pool = PartialPool::new(target);
        let mut states = BTreeMap::new();
        let mut rendered = Vec::new();
        for worker in self.targets(target) {
            let answer = match switch {
                Switch::Start => self.start(ctx, &worker).await.map(StartState::as_str),
                Switch::Stop => self.stop(ctx, &worker).await.map(StopState::as_str),
            };
            if let Some(state) = pool.add(ctx, &worker, answer)? {
                rendered.push(format!("{worker}={state}"));
                states.insert(worker, state);
            }
        }
        if !pool.refused.is_empty() {
            let data = json!({
                "backend": self.settings.backend, "action": switch.as_str(), "target": target.to_string(),
                "workers": states, "refused": pool.refused,
            });
            return Err(pool.incomplete(switch.incomplete_code(), switch.done_word(), data));
        }
        Ok(Outcome {
            data: json!({ "backend": self.settings.backend, "action": switch.as_str(), "workers": states }),
            text: rendered.join("\n"),
        })
    }

    // --- pool init -------------------------------------------------------------------------------------------

    /// Materializes the pool, refusing while any of it is leased.
    pub(crate) async fn pool_init(&self, ctx: &Ctx, golden: Option<&str>) -> Result<Outcome, Refusal> {
        self.prepare_runtime_dirs()?;
        let workers = self.settings.workers.clone();
        self.with_lifecycle_locks(ctx, &workers, "pool-init", async {
            for worker in &workers {
                if read_lease(&self.settings.lease_path(worker))?.is_some() {
                    return Err(Refusal::new(
                        "worker_leased",
                        Exit::TEMP_FAIL,
                        format!("refusing to initialize leased worker {worker}"),
                    ));
                }
            }
            self.pool_init_without_lifecycle_lock(ctx, golden).await
        })
        .await
    }

    // --- pool gc ---------------------------------------------------------------------------------------------

    /// Runs `gc_one` for each worker of the pool under the lifecycle lock of that worker, and renders the answer of
    /// `pool gc`. `gc_one` answers `removed`, or the word of a kept worker.
    ///
    /// A worker whose lock another operation holds is kept as `busy`, and the gc goes on with the next worker. For a
    /// gc, a kept busy slot is success.
    pub(super) async fn gc_each_worker(
        &self,
        ctx: &Ctx,
        gc_one: impl AsyncFn(&str) -> Result<String, Refusal>,
    ) -> Result<Outcome, Refusal> {
        let mut removed = Vec::new();
        let mut kept = BTreeMap::new();
        for worker in &self.settings.workers {
            let outcome = self
                .try_with_lifecycle_lock(ctx, worker, "pool-gc", gc_one(worker))
                .await?
                .unwrap_or_else(|| "busy".to_owned());
            if outcome == "removed" {
                removed.push(worker.clone());
            } else {
                kept.insert(worker.clone(), outcome);
            }
        }
        let text = format!("removed={}", render_list(&removed));
        Ok(Outcome {
            data: json!({
                "backend": self.settings.backend, "action": "gc", "removed": removed, "kept": kept,
            }),
            text,
        })
    }

    /// Empties a worker's host directory, keeping the lifecycle lock file.
    ///
    /// Everything else in there describes the guest that has just been deleted - the pid receipt, the
    /// suspended-state record, the parity and agent receipts, the daemon record, the reports of runs that happened
    /// inside it - so none of it may outlive the VM. The lock file is the exception, and it is the lock module's
    /// rule: a `flock` binds to the inode, so unlinking the file this operation holds lets a contender lock a fresh
    /// one at the same path, and both would believe they hold the worker.
    pub(super) fn clear_worker_state(&self, worker: &str) -> Result<(), Refusal> {
        self.remove_worker_state_except(worker, &[])
    }

    /// Forgets what the host recorded about a guest that is gone, for a worker that stays: a container-linux
    /// container the script made again has a fresh disk, so the agent and the daemon records of the previous one
    /// describe nothing. The lease and the run marker are the caller's, because the gate of a run calls this.
    /// `reports` holds what earlier runs pulled out.
    pub(super) fn forget_guest(&self, worker: &str) -> Result<(), Refusal> {
        let settings = &self.settings;
        self.remove_worker_state_except(
            worker,
            &[
                settings.lease_path(worker),
                settings.run_marker_path(worker),
                settings.worker_dir(worker).join("reports"),
            ],
        )
    }

    /// Removes a worker's host state but the lifecycle lock file and `kept`.
    fn remove_worker_state_except(&self, worker: &str, kept: &[PathBuf]) -> Result<(), Refusal> {
        let directory = self.settings.worker_dir(worker);
        let entries = match std::fs::read_dir(&directory) {
            Ok(entries) => entries,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
            Err(error) => {
                return Err(state_write_failed(format!("cannot read {}: {error}", directory.display())));
            }
        };
        let lock = self.lifecycle_lock_path(worker);
        for entry in entries {
            let entry = entry.map_err(|error| state_write_failed(format!("cannot read {}: {error}", directory.display())))?;
            let path = entry.path();
            if path == lock || kept.contains(&path) {
                continue;
            }
            let removed = match entry.file_type() {
                Ok(kind) if kind.is_dir() => std::fs::remove_dir_all(&path),
                _ => std::fs::remove_file(&path),
            };
            removed.map_err(|error| state_write_failed(format!("cannot remove {}: {error}", path.display())))?;
        }
        Ok(())
    }

    // --- pool recycle ----------------------------------------------------------------------------------------

    /// Rebuilds a worker from scratch: stop, delete the clone, re-clone, start, provision.
    ///
    /// The repair for a guest that is no longer worth fixing in place - a corrupt worker after a host crash, a guest
    /// whose provisioning is half-applied, a machine that boots and answers nothing. Every step already existed; the
    /// sequence is what is named, because two commands with a rule between them is a repair an operator has to
    /// derive.
    ///
    /// What makes it cheap is that a worker holds nothing: no checkout, no credentials, no cache. On the macOS pool
    /// the golden image's guarantees are untouched: the re-clone goes through [`Manager::materialize_tart_worker`]
    /// like every other one, so the seal is re-read and the new worker's provenance receipt is written.
    ///
    /// Under `--lease-file`, the holder recycles the worker of its own lease, and only that worker. The recycle then
    /// also gives the lease back. It is the repair for a lease that `lease release` cannot give back, because the
    /// guest refuses: the recycle discards that guest, so it asks the guest nothing it must answer.
    pub(crate) async fn pool_recycle(&self, ctx: &Ctx, target: &PoolTarget, lease_file: Option<&Path>) -> Result<Outcome, Refusal> {
        self.require_recyclable()?;
        let authorized = lease_file.map(|path| self.recycle_receipt(path, target)).transpose()?;
        // Before the gate: the builder delete leaves an Apple `container` server that is down as it is, and the gate
        // would start it.
        if *target == PoolTarget::All {
            self.delete_pool_builder(ctx).await?;
        }
        self.machine.require_available(ctx, "").await?;
        // Both the delete and the start declare this worker's shares, so the host paths must be resolved first.
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        self.prepare_runtime_dirs()?;
        if let PoolTarget::Worker(worker) = target {
            // Up front: the lifecycle lock lives in the worker's own directory, so a name this pool does not contain
            // must be refused before anything reaches for that path.
            self.settings.require_pool_worker(worker)?;
        }
        let mut pool = PartialPool::new(target);
        for worker in self.targets(target) {
            let answer = self
                .with_lifecycle_lock(
                    ctx,
                    &worker,
                    "pool-recycle",
                    self.recycle_one_worker(ctx, &worker, authorized.as_ref()),
                )
                .await;
            pool.add(ctx, &worker, answer)?;
        }
        let mut data: Value = json!({
            "backend": self.settings.backend, "action": "recycle", "target": target.to_string(),
            "recycled": pool.done, "refused": pool.refused,
        });
        if let Some(lease) = &authorized
            && pool.done.contains(&lease.worker)
        {
            data["leaseReleased"] = json!(lease.worker);
        }
        if !pool.refused.is_empty() {
            return Err(pool.incomplete("recycle_incomplete", "recycled", data));
        }
        Ok(Outcome {
            data,
            text: format!("recycled={}", pool.done.join(",")),
        })
    }

    /// The lease of the receipt `path`, when the recycle `target` names the worker of that lease. A receipt authorizes
    /// the recycle of its own worker only, so `all` and another worker are refused.
    fn recycle_receipt(&self, path: &Path, target: &PoolTarget) -> Result<Lease, Refusal> {
        let (_, lease) = receipt_for_path(&self.settings, path)?;
        match target {
            PoolTarget::Worker(worker) if *worker == lease.worker => Ok(lease),
            PoolTarget::Worker(_) | PoolTarget::All => Err(Refusal::usage(format!(
                "--lease-file authorizes the recycle of its own worker only; run `--lease-file FILE pool recycle {}`",
                lease.worker
            ))),
        }
    }

    /// The repair itself, for a caller holding the worker's lifecycle lock.
    ///
    /// The run guard and the lease guard come first, as for `pool stop`. A recycle refuses another session's worker
    /// with the message that names the release command. `authorized` is the lease of the caller's receipt, which
    /// passes the lease guard. Nothing is deleted until both guards have passed, so a refusal leaves the slot exactly
    /// as it was found. The backend then unmakes the slot: see [`Manager::unmake_worker`]. Tart stops the machine
    /// before the delete, so a stop that timed out also leaves the slot as it was.
    ///
    /// The unmake removes the lease file with the rest of the host state. An authorized recycle also removes the
    /// receipts of the lease, so the lease is given back and no receipt names it.
    async fn recycle_one_worker(&self, ctx: &Ctx, worker: &str, authorized: Option<&Lease>) -> Result<(), Refusal> {
        self.require_no_run(ctx, worker, "recycle").await?;
        self.require_unleased(worker, "recycle", authorized)?;
        self.stop_lane_ides(ctx, worker).await;
        self.unmake_worker(ctx, worker).await?;
        if let Some(lease) = authorized {
            remove_lease_receipts(&self.settings, lease);
            self.note(worker, format!("the recycle gave back the lease of {worker}"));
        }
        self.start_without_lifecycle_lock(ctx, worker).await.map(drop)
    }

    /// Stops the lane IDEs of a running worker before the recycle unmakes its slot, through the guest agent's
    /// `ide-gc`. The gc sends each IDE a TERM and gives it the grace of a cancel to shut down, which the unmake does
    /// not do.
    ///
    /// It is best effort, because a recycle repairs a worker whose guest may not answer, and the unmake ends every
    /// process of the slot. A worker that is stopped, or whose state cannot be read, gets no guest call. A refusal of
    /// the gc is a note.
    async fn stop_lane_ides(&self, ctx: &Ctx, worker: &str) {
        if !matches!(self.runs_nothing(ctx, worker).await, Ok(false)) {
            return;
        }
        let channel = self.channel(worker);
        match gc_guest_ides(&self.guest(ctx, channel.as_ref()), IdeRetention::StopAll).await {
            Ok(ides) => {
                if let Some(note) = gc_note(&ides) {
                    self.note(worker, format!("{note} before the recycle"));
                }
            }
            Err(refusal) => self.note(
                worker,
                format!(
                    "the lane IDEs of {worker} were not stopped before the recycle ({}); the unmake ends them",
                    refusal.code
                ),
            ),
        }
    }
}

/// A possibly empty worker list for an operator, where an empty one has to say so.
pub(super) fn render_list(workers: &[String]) -> String {
    if workers.is_empty() {
        "(none)".to_owned()
    } else {
        workers.join(",")
    }
}

fn state_write_failed(message: String) -> Refusal {
    Refusal::new("state_write_failed", Exit::FAILURE, message)
}
