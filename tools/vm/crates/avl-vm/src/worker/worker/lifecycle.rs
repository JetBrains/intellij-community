//! The worker lifecycle and its one dispatch. This file holds these parts:
//!
//! - [`Timings`], the bounds of the poll loops;
//! - [`StartState`] and [`StopState`], what a start or a stop found;
//! - the lifecycle operations of [`Manager`]. Each is one `match` on the [`Machine`], and each arm calls the body in
//!   the file of its backend: `worker/tart.rs`, `worker/parallels.rs` and `worker/docker.rs`;
//! - [`Manager::require_unleased`], the lease guard of a lifecycle operation.
//!
//! A backend that cannot do an operation answers [`unsupported`], which keeps the refusal code a caller branches on.

use std::sync::{Arc, Mutex};
use std::time::Duration;

use avl_base::sync::lock;
use avl_base::{Exit, Outcome, Refusal};
use avl_host_sys::guest::ensure_host_paths;
use avl_host_sys::{Backoff, Ctx};
use serde::Serialize;

#[cfg(unix)]
use super::parallels::{NO_GC, NO_RECYCLE};
use super::{Lease, Manager, read_lease};
use crate::worker::hypervisor::Machine;
#[cfg(unix)]
use crate::worker::hypervisor::unsupported;

/// Whether `authorized`, the lease a caller holds, is the lease `held` that the worker's lease file records: the same
/// worker and the same token. The one test of "this lease is the caller's own" for every guard.
pub(crate) fn lease_authorizes(authorized: Option<&Lease>, held: &Lease) -> bool {
    authorized.is_some_and(|authorized| authorized.same_lease(held))
}

/// The leases this process holds while it runs one body per leased worker, such as the shards of `shard`.
///
/// One per invocation, as the manager is. An operation that reaches every worker of the pool, the recreate of the
/// Lima engine, does not stop for a lease of this set: the sibling bodies of the same command wait for that operation
/// under the same lock, and none of them has a container that another session uses. A lease of another process still
/// stops it.
///
/// No `Debug`: the tokens are secrets.
#[derive(Default)]
pub(crate) struct HeldLeases {
    leases: Mutex<Vec<Lease>>,
}

impl HeldLeases {
    /// Records `leases` as held until the answer is dropped.
    pub(crate) fn hold(self: &Arc<Self>, leases: &[Lease]) -> HeldLeasesGuard {
        lock(&self.leases).extend(leases.iter().cloned());
        HeldLeasesGuard {
            owner: Arc::clone(self),
            leases: leases.to_vec(),
        }
    }

    /// Whether `held`, the lease a worker's lease file records, is one of this process's leases.
    pub(crate) fn contains(&self, held: &Lease) -> bool {
        lock(&self.leases).iter().any(|lease| lease_authorizes(Some(lease), held))
    }
}

/// Keeps a set of leases in [`HeldLeases`], and takes them out again when dropped.
#[must_use = "the leases count as held only while the guard lives"]
pub(crate) struct HeldLeasesGuard {
    owner: Arc<HeldLeases>,
    leases: Vec<Lease>,
}

impl Drop for HeldLeasesGuard {
    fn drop(&mut self) {
        let mut held = lock(&self.owner.leases);
        for lease in &self.leases {
            if let Some(index) = held.iter().position(|candidate| candidate.same_lease(lease)) {
                held.remove(index);
            }
        }
    }
}

/// The budgets and the pauses of the poll loops, each of which runs through [`Poll`](avl_host_sys::Poll). The
/// numbers are not interchangeable: the difference between the stop grace and the suspend grace is two orders of
/// magnitude and it is the whole reason both exist. A value rather than constants so a suite does not spend an
/// operator's seconds on each loop.
///
/// Public only for a suite in another crate. Production takes [`Timings::default`] through [`Manager::new`], and a
/// suite takes [`Timings::fast`] through [`Manager::with_timings`]. The fields stay private to this crate, so no
/// caller can build a third set.
#[derive(Clone, Copy, Debug)]
#[cfg_attr(
    windows,
    expect(
        dead_code,
        reason = "most bounds are the Tart loops, and a Windows host has the Docker backend only"
    )
)]
pub(crate) struct Timings {
    /// Bounds the wait for `ps` to answer about a process that has just been forked: a race with the kernel rather
    /// than with a boot, so one second, and the first probes come milliseconds apart.
    pub(crate) identity_budget: Duration,
    pub(crate) identity_backoff: Backoff,
    /// How long a start keeps re-trying while the host is at its concurrent-VM limit. See
    /// [`Manager::spawn_retrying_the_host_limit`].
    pub(crate) host_limit_window: Duration,
    pub(crate) host_limit_retry: Duration,
    /// Bounds the probe for "did the run process just spawned die immediately", which is what the host's VM limit
    /// looks like: tart validates against it before the guest boots. Two seconds.
    pub(crate) settle_budget: Duration,
    /// The pause of the waits that probe a host process with `ps` or the VM state with `tart list`: 100 ms doubling to
    /// 2 s, so an answer that comes at once is seen at once, and a five-minute suspend probes about 150 times and not
    /// 3 000.
    pub(crate) process_backoff: Backoff,
    /// The guest-readiness poll. The probe is a whole `tart exec` round-trip, so a tighter loop would spend the boot
    /// budget on process spawns.
    pub(crate) boot_poll: Duration,
    /// The grace a `tart stop` gets before the run process is declared stuck: ten seconds.
    pub(crate) stop_grace: Duration,
    /// The grace a `tart suspend` gets: five minutes, because it writes about 6 GB of guest state before the process
    /// exits, measured on 2026-08-12.
    pub(crate) suspend_grace: Duration,
    /// Bounds the wait for Virtualization.framework to finish with a machine whose run process is already gone:
    /// thirty seconds.
    pub(crate) teardown_grace: Duration,
    /// What a Docker worker's share refresh waits: two seconds. A bind mount answered a replaced file's old size for
    /// about a second on OrbStack (2026-09-29), and kept a dead node for under a second on the Lima engine
    /// (2026-09-30). See [`Manager::refresh_shares`].
    pub(crate) docker_share_settle: Duration,
    /// How long a Docker start waits for the pool-wide image lock that another start holds while it builds. Thirty
    /// minutes, because the first build installs the guest packages and takes minutes; the waiter then finds the
    /// image and builds nothing.
    pub(crate) docker_image_wait: Duration,
}

impl Default for Timings {
    fn default() -> Self {
        Self {
            identity_budget: Duration::from_secs(1),
            identity_backoff: Backoff::doubling(Duration::from_millis(20), Duration::from_millis(200)),
            host_limit_window: Duration::from_secs(120),
            host_limit_retry: Duration::from_secs(2),
            settle_budget: Duration::from_secs(2),
            process_backoff: Backoff::doubling(Duration::from_millis(100), Duration::from_secs(2)),
            boot_poll: Duration::from_secs(2),
            stop_grace: Duration::from_secs(10),
            suspend_grace: Duration::from_mins(5),
            teardown_grace: Duration::from_secs(30),
            docker_share_settle: Duration::from_secs(2),
            docker_image_wait: Duration::from_mins(30),
        }
    }
}

impl Timings {
    /// The bounds a hermetic suite runs with: the same loops, at a fraction of an operator's seconds. The fakes answer
    /// at once, so each wait here only has to outlast a process spawn.
    #[cfg(test)]
    pub(crate) const fn fast() -> Self {
        Self {
            identity_budget: Duration::from_millis(250),
            identity_backoff: Backoff::doubling(Duration::from_millis(5), Duration::from_millis(40)),
            host_limit_window: Duration::from_secs(1),
            host_limit_retry: Duration::from_millis(50),
            settle_budget: Duration::from_millis(50),
            process_backoff: Backoff::doubling(Duration::from_millis(10), Duration::from_millis(80)),
            boot_poll: Duration::from_millis(50),
            stop_grace: Duration::from_millis(500),
            suspend_grace: Duration::from_millis(500),
            teardown_grace: Duration::from_millis(200),
            docker_share_settle: Duration::from_millis(10),
            docker_image_wait: Duration::from_millis(100),
        }
    }
}

/// What a start found, which is not the same as what it did.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "kebab-case")]
pub(crate) enum StartState {
    Started,
    AlreadyRunning,
}

impl StartState {
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::Started => "started",
            Self::AlreadyRunning => "already-running",
        }
    }
}

/// What a stop found.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "kebab-case")]
pub(crate) enum StopState {
    Stopped,
    AlreadyStopped,
}

impl StopState {
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::Stopped => "stopped",
            Self::AlreadyStopped => "already-stopped",
        }
    }
}

impl Manager {
    // --- the readiness gates ---------------------------------------------------------------------------------

    /// Brings the worker one lease operation is about into the state that operation needs.
    ///
    /// It takes the whole lease rather than its worker name because one of the repairs is a restart, and a restart of
    /// a leased worker has to be authorized: the lease that authorizes it is the caller's own. The caller holds the
    /// worker's lifecycle lock, so every start and stop here is the lock-free one.
    pub(crate) async fn require_ready(&self, ctx: &Ctx, lease: &Lease) -> Result<(), Refusal> {
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        match self.machine.as_ref() {
            #[cfg(unix)]
            Machine::Tart(tart) => self.require_tart_ready(ctx, tart, lease).await,
            #[cfg(unix)]
            Machine::Parallels(parallels) => self.require_parallels_ready(ctx, parallels, &lease.worker).await,
            Machine::Docker(docker) => self.require_docker_ready(ctx, docker, lease).await,
        }
    }

    /// Brings a worker into the state a lease *release* needs, which is less than a run does.
    ///
    /// The asymmetry between the backends is the point. A Parallels worker is resumed on demand purely to prove no run
    /// is still active. A stopped Tart worker is refused here instead - and `lease release` never reaches this for
    /// one, because releasing a stopped worker's lease without starting it is the whole repair for a lease that
    /// outlived a crashed holder.
    ///
    /// The host paths are resolved first because the caller goes on to install the guest agent, and an install that
    /// has to resolve the host binary asks Bazel about it under the repository path, which the settings refuse to
    /// answer before this runs.
    pub(crate) async fn require_release_ready(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        match self.machine.as_ref() {
            #[cfg(unix)]
            Machine::Tart(tart) => self.require_tart_release_ready(ctx, tart, worker).await,
            #[cfg(unix)]
            Machine::Parallels(parallels) => self.require_parallels_release_ready(ctx, parallels, worker).await,
            Machine::Docker(docker) => self.require_docker_release_ready(ctx, docker, worker).await,
        }
    }

    /// Whether a lease release may skip the guest because the worker runs nothing: a Tart worker with no run process,
    /// or a Docker container that does not run. A Parallels worker is resumed for the release instead, so it answers
    /// false.
    ///
    /// A gate, not [`Machine::running`]: a standalone `lease release` passed no other gate, and the Docker question
    /// needs the CLI that its engine gate resolves ([`Docker::stopped`]). Tart answers from its process identity.
    ///
    /// [`Docker::stopped`]: crate::worker::docker::Docker::stopped
    pub(crate) async fn stopped_for_release(&self, ctx: &Ctx, worker: &str) -> Result<bool, Refusal> {
        match self.machine.as_ref() {
            #[cfg(unix)]
            Machine::Tart(tart) => Ok(!tart.running(ctx, worker).await?),
            #[cfg(unix)]
            Machine::Parallels(_) => Ok(false),
            Machine::Docker(docker) => docker.stopped(ctx, worker).await,
        }
    }

    /// Whether the worker of `lease` still runs the guest this checkout declares. A warm iteration asks it after the
    /// engine gate, because it skips the readiness gate while a healthy daemon answers.
    ///
    /// On Docker the image tag, the shares and the display are arguments of `docker create`. A change of any of them,
    /// such as a new guest package that moves the image tag, needs a new container. Without this question a warm
    /// daemon would go on serving the lanes from the container of the previous declaration. The container is current
    /// when its create record declares the argv a create would use now, and the engine gives the name the recorded id
    /// ([`crate::worker::docker::Docker::container_is_current`]). The caller passed the engine gate just before, so the
    /// CLI is resolved. A Tart or a Parallels worker declares its shares at its start, and the readiness gate checks
    /// them, so it answers true.
    pub(crate) async fn guest_declaration_current(&self, ctx: &Ctx, lease: &Lease) -> Result<bool, Refusal> {
        match self.machine.as_ref() {
            Machine::Docker(docker) => docker.container_is_current(ctx, &lease.worker).await,
            #[cfg(unix)]
            Machine::Tart(_) | Machine::Parallels(_) => Ok(true),
        }
    }

    // --- start -----------------------------------------------------------------------------------------------

    /// Brings one worker up, taking its lifecycle lock.
    pub(crate) async fn start(&self, ctx: &Ctx, worker: &str) -> Result<StartState, Refusal> {
        self.settings.require_pool_worker(worker)?;
        self.with_lifecycle_lock(ctx, worker, "pool-start", self.start_without_lifecycle_lock(ctx, worker))
            .await
    }

    /// Brings one worker up for a caller that already holds its lifecycle lock. Every lease operation is such a
    /// caller: the lock is shared between lease and lifecycle operations.
    ///
    /// No caller authorizes a lease here. A Docker readiness gate starts the container itself with the caller's own
    /// lease, which may make a running container again ([`Manager::reconcile_docker_container`]).
    pub(crate) async fn start_without_lifecycle_lock(&self, ctx: &Ctx, worker: &str) -> Result<StartState, Refusal> {
        self.settings.require_pool_worker(worker)?;
        match self.machine.as_ref() {
            #[cfg(unix)]
            Machine::Tart(tart) => self.start_tart(ctx, tart, worker).await,
            #[cfg(unix)]
            Machine::Parallels(parallels) => self.start_parallels(ctx, parallels, worker).await,
            Machine::Docker(docker) => self.start_docker(ctx, docker, worker, None).await,
        }
    }

    // --- stop ------------------------------------------------------------------------------------------------

    /// Refuses a lifecycle operation on a worker another session is holding.
    ///
    /// `authorized` is the lease the caller holds, when it holds one; `operation` is the `pool` verb, which the
    /// refusal names twice - once for what was refused and once for the command to run again once the lease is gone.
    /// One guard for `pool stop` and `pool recycle` because it is one policy: neither may pull a worker out from
    /// under another session's run, and a recycle is a stop that does not give the worker back.
    pub(super) fn require_unleased(&self, worker: &str, operation: &str, authorized: Option<&Lease>) -> Result<(), Refusal> {
        let Some(lease) = read_lease(&self.settings.lease_path(worker))? else {
            return Ok(());
        };
        if lease_authorizes(authorized, &lease) {
            return Ok(());
        }
        // Deliberately no holder name: `status` and `lease show` hide it from an unauthorized caller too.
        Err(Refusal::new(
            "worker_leased",
            Exit::TEMP_FAIL,
            format!(
                "refusing to {operation} {worker}: it is leased by another holder. Wait for that lease to end, or \
                 release it with `--lease-file FILE lease release`, then run `pool {operation} {worker}`"
            ),
        ))
    }

    /// Stops one worker, taking its lifecycle lock and authorizing nothing: `pool stop` refuses a leased worker.
    pub(crate) async fn stop(&self, ctx: &Ctx, worker: &str) -> Result<StopState, Refusal> {
        self.settings.require_pool_worker(worker)?;
        self.with_lifecycle_lock(ctx, worker, "pool-stop", self.stop_without_lifecycle_lock(ctx, worker, None))
            .await
    }

    /// Stops (or suspends) a worker, refusing to do it to a lease that is not the caller's.
    ///
    /// The guard exists so that `pool stop` cannot pull a worker out from under another holder; it was never meant to
    /// stop a holder from restarting the worker it leased, which is what a checkout switch needs.
    pub(crate) async fn stop_without_lifecycle_lock(
        &self,
        ctx: &Ctx,
        worker: &str,
        authorized: Option<&Lease>,
    ) -> Result<StopState, Refusal> {
        self.settings.require_pool_worker(worker)?;
        self.require_unleased(worker, "stop", authorized)?;
        match self.machine.as_ref() {
            #[cfg(unix)]
            Machine::Tart(tart) => self.stop_tart(ctx, tart, worker).await,
            #[cfg(unix)]
            Machine::Parallels(parallels) => self.stop_parallels(ctx, parallels, worker).await,
            Machine::Docker(docker) => self.stop_docker(ctx, docker, worker).await,
        }
    }

    /// What `pool stop` does for the whole pool after it stopped its targets. Answers the word the outcome reports
    /// for the pool's engine, or `None` when the backend has no engine of its own. Only the Docker backend on its
    /// Lima engine has one, and the engine of the host environment is the operator's: no command of this controller
    /// stops it.
    pub(super) async fn stop_pool_engine(&self, ctx: &Ctx) -> Result<Option<String>, Refusal> {
        match self.machine.as_ref() {
            Machine::Docker(docker) => match docker.engine() {
                Some(engine) => self.stop_docker_engine(ctx, docker, engine).await.map(Some),
                None => Ok(None),
            },
            #[cfg(unix)]
            Machine::Tart(_) | Machine::Parallels(_) => Ok(None),
        }
    }

    // --- pool init, pool gc and pool recycle -----------------------------------------------------------------

    /// `pool init` for a caller that already holds the pool's locks.
    ///
    /// Four different operations behind one verb. A Tart macOS pool clones a sealed golden and audits it; a Tart
    /// Linux pool clones a public image by tag, where there is no seal because there is nothing baked in to audit; a
    /// Parallels pool has nothing to create at all and re-provisions the VM the operator made; a Docker pool builds
    /// its image and creates every slot's container. `golden` is the golden VM that the operator named, and only a
    /// Tart macOS pool takes one.
    pub(crate) async fn pool_init_without_lifecycle_lock(&self, ctx: &Ctx, golden: Option<&str>) -> Result<Outcome, Refusal> {
        match self.machine.as_ref() {
            #[cfg(unix)]
            Machine::Tart(tart) => self.pool_init_tart(ctx, tart, golden).await,
            #[cfg(unix)]
            Machine::Parallels(parallels) => self.pool_init_parallels(ctx, parallels, golden).await,
            Machine::Docker(docker) => self.pool_init_docker(ctx, docker, golden).await,
        }
    }

    /// Removes what the idle slots of the pool hold, so the pool's size can go back down. Each backend decides what
    /// an idle slot is and what goes. Parallels owns no clone, so it refuses.
    pub(crate) async fn pool_gc(&self, ctx: &Ctx) -> Result<Outcome, Refusal> {
        match self.machine.as_ref() {
            #[cfg(unix)]
            Machine::Tart(tart) => self.pool_gc_tart(ctx, tart).await,
            #[cfg(unix)]
            Machine::Parallels(_) => Err(unsupported(NO_GC)),
            Machine::Docker(docker) => self.pool_gc_docker(ctx, docker).await,
        }
    }

    /// Refuses before any lock is taken when `pool recycle` has no meaning on this backend: Parallels owns its VM.
    #[cfg_attr(
        windows,
        expect(
            clippy::unnecessary_wraps,
            reason = "a Windows host has the Docker backend only, and a Docker pool is recyclable"
        )
    )]
    pub(super) fn require_recyclable(&self) -> Result<(), Refusal> {
        match self.machine.as_ref() {
            #[cfg(unix)]
            Machine::Parallels(_) => Err(unsupported(NO_RECYCLE)),
            #[cfg(unix)]
            Machine::Tart(_) => Ok(()),
            Machine::Docker(_) => Ok(()),
        }
    }

    /// What `pool recycle all` does for the whole pool before it unmakes the slots. Only the Docker backend on its
    /// Lima engine does something: it deletes the engine, so the recycle makes the whole pool again, the engine VM
    /// included.
    pub(super) async fn delete_pool_engine(&self, ctx: &Ctx) -> Result<(), Refusal> {
        match self.machine.as_ref() {
            Machine::Docker(docker) => match docker.engine() {
                Some(engine) => self.delete_docker_engine(ctx, engine).await,
                None => Ok(()),
            },
            #[cfg(unix)]
            Machine::Tart(_) | Machine::Parallels(_) => Ok(()),
        }
    }

    /// The unmaking of one slot for `pool recycle`, after the lease guard: the VM, or the container and its volume,
    /// and the host state.
    pub(super) async fn unmake_worker(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        match self.machine.as_ref() {
            #[cfg(unix)]
            Machine::Tart(tart) => self.unmake_tart_worker(ctx, tart, worker).await,
            #[cfg(unix)]
            Machine::Parallels(_) => Err(unsupported(NO_RECYCLE)),
            Machine::Docker(docker) => self.unmake_docker_worker(ctx, docker, worker).await,
        }
    }
}
