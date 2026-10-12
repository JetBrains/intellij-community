//! The `pool` commands: `init`, `start`, `stop`, `gc`, `recycle`.
//!
//! What a command line says is parsed by `avl-vm`; what arrives here is a [`PoolCommand`].

use std::collections::BTreeMap;
use std::fmt;
use std::str::FromStr;

use avl_base::{Exit, Outcome, Refusal};
use avl_host_sys::Ctx;
use avl_host_sys::guest::ensure_host_paths;
use serde_json::{Value, json};

use super::{Manager, read_lease};
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
}

impl Manager {
    /// The `pool` command.
    pub(crate) async fn pool(&self, ctx: &Ctx, command: PoolCommand) -> Result<Outcome, Refusal> {
        match command {
            PoolCommand::Init { golden } => self.pool_init(ctx, golden.as_deref()).await,
            PoolCommand::Gc => self.pool_gc(ctx).await,
            PoolCommand::Recycle(target) => self.pool_recycle(ctx, &target).await,
            PoolCommand::Start(target) => self.pool_start_or_stop(ctx, Switch::Start, &target).await,
            PoolCommand::Stop(target) => self.pool_start_or_stop(ctx, Switch::Stop, &target).await,
        }
    }

    fn targets(&self, target: &PoolTarget) -> Vec<String> {
        match target {
            PoolTarget::All => self.settings.workers.clone(),
            PoolTarget::Worker(worker) => vec![worker.clone()],
        }
    }

    async fn pool_start_or_stop(&self, ctx: &Ctx, switch: Switch, target: &PoolTarget) -> Result<Outcome, Refusal> {
        self.prepare_runtime_dirs()?;
        let mut states = BTreeMap::new();
        let mut rendered = Vec::new();
        for worker in self.targets(target) {
            let state = match switch {
                Switch::Start => self.start(ctx, &worker).await?.as_str(),
                Switch::Stop => self.stop(ctx, &worker).await?.as_str(),
            };
            rendered.push(format!("{worker}={state}"));
            states.insert(worker, state);
        }
        let mut data = json!({ "backend": self.settings.backend, "action": switch.as_str(), "workers": states });
        if switch == Switch::Stop
            && let Some(engine) = self.stop_pool_engine(ctx).await?
        {
            rendered.push(format!("engine={engine}"));
            data["engine"] = Value::from(engine);
        }
        Ok(Outcome {
            data,
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

    /// Empties a worker's host directory, keeping the lifecycle lock file.
    ///
    /// Everything else in there describes the guest that has just been deleted - the pid receipt, the
    /// suspended-state record, the parity and agent receipts, the daemon's stage, the reports of runs that happened
    /// inside it - so none of it may outlive the VM. The lock file is the exception, and it is the lock module's
    /// rule: a `flock` binds to the inode, so unlinking the file this operation holds lets a contender lock a fresh
    /// one at the same path, and both would believe they hold the worker.
    pub(super) fn clear_worker_state(&self, worker: &str) -> Result<(), Refusal> {
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
            if path == lock {
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
    pub(crate) async fn pool_recycle(&self, ctx: &Ctx, target: &PoolTarget) -> Result<Outcome, Refusal> {
        self.require_recyclable()?;
        // Before the gate, which would start an engine that the delete is about to remove.
        if *target == PoolTarget::All {
            self.delete_pool_engine(ctx).await?;
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
        let mut recycled = Vec::new();
        let mut refused = BTreeMap::new();
        let mut names = Vec::new();
        let mut retryable = true;
        for worker in self.targets(target) {
            let result = self
                .with_lifecycle_lock(ctx, &worker, "pool-recycle", self.recycle_one_worker(ctx, &worker))
                .await;
            let refusal = match result {
                Ok(()) => {
                    recycled.push(worker);
                    continue;
                }
                // One named worker is an instruction about that worker, so its own refusal is the answer: the code a
                // caller branches on, and the evidence only that refusal has - `tart_exited` carries the run log's
                // tail, `worker_leased` names the release command.
                Err(refusal) if *target != PoolTarget::All => return Err(refusal),
                Err(refusal) => refusal,
            };
            names.push(format!("{worker} ({})", refusal.code));
            // Read off the exit code rather than a list of codes to keep in step: a temporary failure is the refusal
            // whose distinguishing property is that retrying later is reasonable.
            retryable &= refusal.exit == Exit::TEMP_FAIL;
            refused.insert(worker, json!({ "code": refusal.code, "message": refusal.message }));
        }
        let data: Value = json!({
            "backend": self.settings.backend, "action": "recycle", "target": target.to_string(),
            "recycled": recycled, "refused": refused,
        });
        if !refused.is_empty() {
            // `recycle all` recycles what it can and then refuses, because its job is a usable pool and a worker it
            // could not touch is that job unfinished - unlike `pool gc`, for which keeping a busy slot *is* success.
            // The exit code separates what a caller does next: temporary when every worker it left alone was left
            // alone for a reason that goes away on its own, a failure when something is actually broken.
            let exit = if retryable { Exit::TEMP_FAIL } else { Exit::FAILURE };
            return Err(Refusal::new(
                "recycle_incomplete",
                exit,
                format!("recycled {}; not recycled: {}", render_list(&recycled), names.join(", ")),
            )
            .with_details(data));
        }
        Ok(Outcome {
            data,
            text: format!("recycled={}", recycled.join(",")),
        })
    }

    /// The repair itself, for a caller holding the worker's lifecycle lock.
    ///
    /// The lease guard is first and it is the one `pool stop` uses, so a recycle refuses another session's worker with
    /// the message that names the release command. Nothing is deleted until that guard has passed, so a refusal
    /// leaves the slot exactly as it was found. The backend then unmakes the slot: see [`Manager::unmake_worker`].
    /// Tart stops the machine before the delete, so a stop that timed out also leaves the slot as it was.
    async fn recycle_one_worker(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        self.require_unleased(worker, "recycle", None)?;
        self.unmake_worker(ctx, worker).await?;
        self.start_without_lifecycle_lock(ctx, worker).await.map(drop)
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
