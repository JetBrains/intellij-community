//! Worker lifecycle: the readiness gates every lease operation runs first, the `pool` commands that create, start
//! and stop a worker, and the lease document.
//!
//! The readiness *contract* is one contract for every backend. A lease operation needs a worker whose guest answers,
//! whose console is logged in, and whose parity layout describes this checkout. It needs that whether the machine
//! underneath is a `tart run` process, a VM the operator made or a container the controller created. How a backend
//! brings a worker there is the body in the file of that backend.
//!
//! # Where each part lives
//!
//! - `worker/lifecycle.rs`: the one dispatch of the lifecycle operations (the readiness gates, start, stop and the
//!   `pool` operations), each a `match` on the [`Machine`], and the lease guard of a lifecycle operation.
//! - `worker/tart.rs`: the Tart lifecycle, Unix only as the Tart backend is. It also writes the two pieces of host state that only a Tart worker has.
//!   The run process's pid receipt records the identity that recognises the process again. Reading it back is
//!   [`Tart::read_process_identity`]. The suspended-state record says what the worker was suspended *with*. The
//!   next start reads it to tell a safe resume from a resume against a changed device declaration.
//! - `worker/parallels.rs`: the Parallels lifecycle, `prlctl` against a VM the operator made. Unix only.
//! - `worker/docker.rs`: the Docker lifecycle, the image and the container of each slot.
//! - `worker/container_linux.rs`: the container-linux lifecycle, the one container the skill's script starts for this
//!   checkout.
//! - `worker/pool.rs`: the `pool` commands. `pool init` and `pool recycle` take the locks and check the leases,
//!   then ask the lifecycle. `pool gc` locks each slot inside the backend. `pool start` and `pool stop` take their
//!   lock in `worker/lifecycle.rs`.
//!
//! This file keeps what every backend shares: the lifecycle locks, the guest channel, the share refresh, and the
//! lease document.
//!
//! The lease document is not the lease *policy*, which is [`crate::worker::lease`]. It is the file, read beside the pid
//! receipt and the suspended state in the same worker directory. The stop path has to know whether a worker is
//! leased, so the lower module must be able to read one without the policy that writes it.

use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::Duration;

use async_trait::async_trait;
use avl_base::{Backend, Config, Exit, GuestOs, Refusal, Reporter, SCHEMA_VERSION, Scope};
use avl_host_sys::fs::{prepare_runtime_dirs, secret_matches};
use avl_host_sys::guest::{BazelHost, Guest, PeerChannels, ShareMount};
use avl_host_sys::lock::LockManager;
use avl_host_sys::{Captured, Channel, Ctx, GuestStream, Runner, SpawnOptions};
use avl_wire::verb::AgentVerb;
use futures::future::BoxFuture;
use serde::{Deserialize, Serialize};

use crate::worker::channel::ControlPortChannel;
use crate::worker::container_linux::ContainerLinux;
use crate::worker::docker::Docker;
use crate::worker::hypervisor::Machine;
#[cfg(unix)]
use crate::worker::parallels::Parallels;
use crate::worker::pin::PinnedTools;
use crate::worker::secure::read_json_record;
#[cfg(unix)]
use crate::worker::tart::Tart;

mod container_linux;
mod docker;
mod idle_stop;
mod lifecycle;
#[cfg(unix)]
mod parallels;
mod pool;
#[cfg(unix)]
mod tart;

// The worker suite drives Tart pools, and a Windows host has the Docker backend only.
#[cfg(test)]
#[cfg(unix)]
mod tests;

#[cfg(test)]
pub(crate) use idle_stop::IdleStopRequest;
#[cfg(all(test, unix))]
pub(crate) use idle_stop::{IdleStopRecord, IdleStopVerdict};
pub(crate) use idle_stop::{read_idle_stop_record, remove_idle_stop_record};
pub(crate) use lifecycle::{StartState, StopState, Timings};
pub(crate) use pool::{PoolCommand, PoolTarget};

// --- the lease document ----------------------------------------------------------------------------------------

/// Who holds one worker, as `<worker>/lease.json` records it.
///
/// Every field is required: this controller writes all of them, and a lease missing one is corrupt.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Lease {
    pub schema_version: u32,
    pub backend: Backend,
    pub guest_os: GuestOs,
    pub worker: String,
    pub token: String,
    pub holder: String,
    pub acquired_at: String,
}

impl Lease {
    /// Whether every field a lease must carry is there. Shared with the receipt, which is a lease plus a marker.
    /// Whether `other` is this lease: the same worker and the same token. The one comparison of two leases. The
    /// tokens are secrets, so they are compared in constant time ([`secret_matches`]).
    pub(crate) fn same_lease(&self, other: &Self) -> bool {
        self.worker == other.worker && secret_matches(&self.token, &other.token)
    }

    pub(crate) const fn is_complete(&self) -> bool {
        self.schema_version == SCHEMA_VERSION
            && !self.worker.is_empty()
            && !self.token.is_empty()
            && !self.holder.is_empty()
            && !self.acquired_at.is_empty()
    }
}

/// The lease holding a worker, or `None` for a free one.
///
/// A missing file is a free worker and is not an error; anything present that cannot be read as a lease is
/// `corrupt_lease` and refuses. That asymmetry is the whole point: an unreadable lease must never be reported as
/// "free", because the next thing the caller does with a free worker is hand it to somebody.
pub(crate) fn read_lease(path: &Path) -> Result<Option<Lease>, Refusal> {
    let corrupt = |reason: String| {
        Refusal::new(
            "corrupt_lease",
            Exit::FAILURE,
            format!("invalid lease state at {}{reason}", path.display()),
        )
    };
    match read_json_record::<Lease>(path).map_err(|error| corrupt(format!(": {error}")))? {
        Some(lease) if !lease.is_complete() => Err(corrupt(String::new())),
        lease => Ok(lease),
    }
}

// --- the worker manager ----------------------------------------------------------------------------------------

/// Builds the guest channel into one worker, replacing the hypervisor-backed one. A test seam: [`Channel`] exists so
/// a suite can answer a guest command without a VM behind it.
pub(crate) type ChannelFactory = Arc<dyn Fn(&str) -> Arc<dyn Channel> + Send + Sync>;

/// Builds the host output a boot installs into a guest, the guest agent, before any worker is touched. `avl-vm` wires
/// it to the lane's Bazel build. The seam prevents the reverse dependency, this crate reaching into the lane.
pub(crate) type GuestBootBuilder = Arc<dyn Fn(Ctx) -> BoxFuture<'static, Result<(), Refusal>> + Send + Sync>;

/// The [`GuestBootBuilder`] that builds nothing: what a hermetic suite passes, and what every `AIR_VM_*_SOURCE`
/// override already implies, since freshness is then the operator's.
#[cfg(test)]
pub(crate) fn builds_nothing() -> GuestBootBuilder {
    Arc::new(|_| Box::pin(async { Ok(()) }))
}

/// The collaborators a worker manager cannot build for itself.
pub(crate) struct Dependencies {
    pub settings: Arc<Config>,
    pub runner: Runner,
    pub reporter: Reporter,
    pub locks: Arc<LockManager>,
    /// `None` is the operating case: the hypervisor-backed channel, or the control-port channel of a container-linux pool.
    /// `Some` is only ever a test's fake guest (see [`ChannelFactory`]); a test that wants the real channel over a
    /// fake hypervisor passes `None` on purpose.
    pub channel: Option<ChannelFactory>,
    /// How the guest agent's and the pinned Tart's host files are located.
    ///
    /// `None` is what a hermetic suite passes, and safe: its fixtures set `TART_BIN` and `AIR_VM_GUEST_AGENT_SOURCE`,
    /// and a named source is answered by a stat without asking Bazel anything. The two
    /// readers answer an unnamed one differently, which is why this stays an `Option` rather than a stand-in: an
    /// install is refused `bazel_unavailable` rather than spawning a 24 s `cquery`, and the Tart backend refuses
    /// `tart_missing` before it spawns anything.
    pub bazel: Option<Arc<dyn BazelHost>>,
    /// What a boot builds before it touches a worker. A hermetic suite passes [`builds_nothing`].
    pub build_guest_boot: GuestBootBuilder,
}

/// One pool's worker lifecycle: one backend, one lock manager, one set of slots.
///
/// What this type owns is *lifecycle* - what exists, what is running, what is suspended - and the lease policy that
/// decides who may use one of them is a layer above it.
pub(crate) struct Manager {
    settings: Arc<Config>,
    runner: Runner,
    reporter: Reporter,
    locks: Arc<LockManager>,
    machine: Arc<Machine>,
    channel: Option<ChannelFactory>,
    bazel: Option<Arc<dyn BazelHost>>,
    build_boot: GuestBootBuilder,
    timings: Timings,
    /// How a lease release starts the detached process of an idle stop ([`idle_stop::spawn_detached_idle_stop`]).
    idle_stop: idle_stop::IdleStopSpawner,
}

impl Manager {
    /// A worker manager for the configured backend.
    pub(crate) fn new(dependencies: Dependencies) -> Self {
        Self::with_timings(dependencies, Timings::default())
    }

    /// A worker manager with other poll bounds. A suite passes [`Timings::fast`] here.
    pub(crate) fn with_timings(dependencies: Dependencies, timings: Timings) -> Self {
        let Dependencies {
            settings,
            runner,
            reporter,
            locks,
            channel,
            bazel,
            build_guest_boot,
        } = dependencies;
        // The pinned CLI finds its `docker-buildx` through its own configuration directory. The variable is on the
        // manager's runner, so the guest channel's `docker exec` and every layer that spawns through this runner run
        // the same CLI. The Apple `container` engine runs no Docker CLI, so it does not get it.
        let docker_config = (settings.backend == Backend::Docker && settings.docker.is_none() && !settings.runs_container_engine())
            .then(|| settings.docker_config_dir().to_string_lossy().into_owned());
        let runner = match docker_config.as_deref() {
            Some(directory) => runner.with_overrides(&[("DOCKER_CONFIG", directory)]),
            None => runner,
        };
        // One resolver for the pinned tools of every backend, so a pool resolves them all with one query.
        let pinned = Arc::new(PinnedTools::new(Arc::clone(&settings), runner.clone(), bazel.clone()));
        let machine = match settings.backend {
            #[cfg(unix)]
            Backend::Parallels => Machine::Parallels(Parallels::new(Arc::clone(&settings), runner.clone())),
            #[cfg(unix)]
            Backend::Tart => Machine::Tart(Tart::new(Arc::clone(&settings), runner.clone(), reporter.clone(), pinned)),
            Backend::Docker => Machine::Docker(Docker::new(
                Arc::clone(&settings),
                runner.clone(),
                reporter.clone(),
                Arc::clone(&locks),
                pinned,
            )),
            Backend::ContainerLinux => {
                Machine::ContainerLinux(ContainerLinux::new(Arc::clone(&settings), runner.clone(), reporter.clone()))
            }
            #[cfg(windows)]
            Backend::Tart | Backend::Parallels => {
                unreachable!("Config::load refuses the {} backend on Windows", settings.backend)
            }
        };
        Self {
            settings,
            runner,
            reporter,
            locks,
            machine: Arc::new(machine),
            channel,
            bazel,
            build_boot: build_guest_boot,
            timings,
            idle_stop: Arc::new(idle_stop::spawn_detached_idle_stop),
        }
    }

    /// The configuration this controller runs with, which every caller of it also needs.
    pub(crate) const fn settings(&self) -> &Arc<Config> {
        &self.settings
    }

    /// Where progress goes, so that a caller layered on top reports through the same one.
    pub(crate) const fn reporter(&self) -> &Reporter {
        &self.reporter
    }

    /// The runner, whose environment is already scrubbed.
    pub(crate) const fn runner(&self) -> &Runner {
        &self.runner
    }

    /// The lock manager, for the one lock that is not a worker's: the pool-wide acquisition lock `lease acquire`
    /// queues on. One manager per invocation, so every lock of it carries one owner record.
    pub(crate) const fn locks(&self) -> &Arc<LockManager> {
        &self.locks
    }

    /// The configured backend, for the questions the backends merely answer differently.
    pub(crate) fn machine(&self) -> &Machine {
        &self.machine
    }

    /// How long the end of a run waits for the lifecycle lock to remove its run marker.
    pub(crate) const fn run_marker_lock_wait(&self) -> Duration {
        self.timings.run_marker_lock_wait
    }

    /// The Tart backend, when this pool is a Tart one: `status` reads VM states and root disks through it.
    #[cfg(unix)]
    pub(crate) fn tart(&self) -> Option<&Tart> {
        match self.machine.as_ref() {
            Machine::Tart(tart) => Some(tart),
            Machine::Parallels(_) | Machine::Docker(_) | Machine::ContainerLinux(_) => None,
        }
    }

    /// The `tart` executable the version gate resolved, for the layers that run a Tart command of their own after
    /// `self.machine().require_available(..)`: the settings keep the configured `TART_BIN` only (see
    /// [`Tart::program`]). `None` for a Parallels pool, and for a Tart pool before the gate ran.
    #[cfg(unix)]
    pub(crate) fn tart_executable(&self) -> Option<&Path> {
        self.tart().and_then(Tart::executable)
    }

    /// Where the guest agent's host binary is looked up, for this crate and for the layers above that install it
    /// too. One handle for the boot install and the release install, so the two cannot resolve through different
    /// Bazels.
    ///
    /// A manager built without a Bazel answers a stand-in that refuses by name. Every guest step that may resolve a
    /// host file takes one, and a hermetic suite that named its sources (`AIR_VM_GUEST_AGENT_SOURCE` and the other
    /// overrides, answered by a stat) never reaches the refusal.
    pub(crate) fn bazel(&self) -> &dyn BazelHost {
        self.bazel.as_deref().unwrap_or(&NoBazel)
    }

    fn note(&self, worker: &str, message: String) {
        self.reporter.note(message, Some(&Scope::worker(worker)));
    }

    /// Creates this pool's private state directories, which every lease command does before touching a receipt.
    pub(crate) fn prepare_runtime_dirs(&self) -> Result<(), Refusal> {
        let directories: Vec<PathBuf> = self
            .settings
            .workers
            .iter()
            .map(|worker| self.settings.worker_dir(worker))
            .collect();
        prepare_runtime_dirs(&self.settings.runtime_root, &directories)
    }

    // --- the worker lifecycle lock ---------------------------------------------------------------------------

    /// The lock every operation on one worker takes.
    ///
    /// One lock for lease operations and lifecycle operations alike, which is what makes the `_without_lifecycle_lock`
    /// variants safe to call from inside a lease operation: the caller already holds this, or the worker's run marker,
    /// which excludes every other lifecycle operation. Taking the lock again would refuse against itself. Never
    /// deleted, and kept by `pool recycle`: a `flock` binds to the inode.
    pub(crate) fn lifecycle_lock_path(&self, worker: &str) -> PathBuf {
        self.settings.worker_dir(worker).join("lease-operation.lock")
    }

    /// Runs `action` holding one worker's lifecycle lock, or refuses `lease_busy` when another operation holds it.
    pub(crate) async fn with_lifecycle_lock<T>(
        &self,
        ctx: &Ctx,
        worker: &str,
        operation: &str,
        action: impl Future<Output = Result<T, Refusal>>,
    ) -> Result<T, Refusal> {
        let _held = self
            .locks
            .acquire(
                ctx,
                &self.lifecycle_lock_path(worker),
                operation,
                &format!("worker {worker} is handling another lifecycle operation"),
            )
            .await?;
        action.await
    }

    /// Runs `action` holding one worker's lifecycle lock, or answers `None` without running it when another operation
    /// holds the lock.
    pub(crate) async fn try_with_lifecycle_lock<T>(
        &self,
        ctx: &Ctx,
        worker: &str,
        operation: &str,
        action: impl Future<Output = Result<T, Refusal>>,
    ) -> Result<Option<T>, Refusal> {
        let Some(_held) = self.locks.try_acquire(ctx, &self.lifecycle_lock_path(worker), operation).await? else {
            return Ok(None);
        };
        action.await.map(Some)
    }

    /// Runs `action` holding the lifecycle lock of every named worker.
    ///
    /// The pool-wide operations need the whole set before deciding anything, because a run that resized one worker
    /// and then refused the next would leave the pool half-changed.
    async fn with_lifecycle_locks<T>(
        &self,
        ctx: &Ctx,
        workers: &[String],
        operation: &str,
        action: impl Future<Output = Result<T, Refusal>>,
    ) -> Result<T, Refusal> {
        let paths: Vec<PathBuf> = workers.iter().map(|worker| self.lifecycle_lock_path(worker)).collect();
        let _held = self
            .locks
            .acquire_all(ctx, &paths, operation, "a worker is handling another lifecycle operation")
            .await?;
        action.await
    }

    // --- the guest channel -----------------------------------------------------------------------------------

    /// The exec channel into one worker: the hypervisor exec, or the control port of the testing-ui container.
    pub(crate) fn channel(&self, worker: &str) -> Arc<dyn Channel> {
        if let Some(factory) = &self.channel {
            return factory(worker);
        }
        if let Machine::ContainerLinux(_) = self.machine.as_ref() {
            return Arc::new(ControlPortChannel::new(Arc::clone(&self.settings), worker));
        }
        Arc::new(HypervisorChannel {
            machine: Arc::clone(&self.machine),
            runner: self.runner.clone(),
            settings: Arc::clone(&self.settings),
            worker: worker.to_owned(),
        })
    }

    fn guest<'a>(&'a self, ctx: &'a Ctx, channel: &'a dyn Channel) -> Guest<'a> {
        Guest {
            ctx,
            settings: &self.settings,
            channel,
            reporter: &self.reporter,
        }
    }

    /// Whether the guest runs a `/usr/bin/true` within `timeout`. A probe outside a wait passes
    /// [`GUEST_PROBE_TIMEOUT`], and a wait passes [`probe_timeout`] of what is left of it, so a probe in flight cannot
    /// carry the wait past its budget.
    async fn guest_answers(&self, ctx: &Ctx, worker: &str, timeout: Duration) -> bool {
        let channel = self.channel(worker);
        self.guest(ctx, channel.as_ref())
            .succeeds(&["/usr/bin/true".to_owned()], timeout)
            .await
    }

    // --- the shares ------------------------------------------------------------------------------------------

    /// How the guest holds this pool's shares. The backend answers it ([`Machine::share_mount`]).
    pub(crate) fn share_mount(&self) -> ShareMount {
        self.machine.share_mount()
    }

    /// Makes the worker's guest see the host's current bytes after a host build.
    ///
    /// The one entry point for it on every backend, and [`Manager::share_mount`] picks the behaviour. A VirtioFS
    /// guest, on Tart and Parallels, keeps a dead node for a file the host replaced, so the guest remounts the device
    /// ([`Guest::remount_shares`]). Bind mounts, on Docker, serve the new bytes within a second, so the refresh on
    /// Docker is a short settle and no remount. Two engines were measured:
    ///
    /// - OrbStack, on 2026-09-29: `cat` read the new content at once, `open` never failed with ENOENT, and only `stat`
    ///   answered the old size for about a second.
    /// - Apple `container` 1.5.0 (virtiofs), on 2026-10-09: a path that the guest looked up in the second before the
    ///   host rename kept a dead node, so `open` failed with ENOENT and `stat` answered the old size. The dead node
    ///   expired in under 60 ms.
    ///
    /// The settle of two seconds covers both.
    pub(crate) async fn refresh_shares(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        match self.share_mount() {
            ShareMount::VirtioFs => {
                let channel = self.channel(worker);
                self.guest(ctx, channel.as_ref()).remount_shares().await
            }
            ShareMount::Bind => {
                let settle = self.timings.docker_share_settle;
                if !ctx.sleep(settle).await {
                    return Err(Refusal::new(
                        "worker_interrupted",
                        Exit::SOFTWARE,
                        format!("the share refresh of {worker} was interrupted"),
                    ));
                }
                self.note(
                    worker,
                    format!(
                        "{worker}: bind mounts serve fresh bytes; settled {} s for the virtiofs dead nodes and \
                         attribute cache (measured on OrbStack on 2026-09-29, and on Apple container on 2026-10-09, \
                         where a dead node lives under 60 ms)",
                        settle.as_secs_f64()
                    ),
                );
                Ok(())
            }
        }
    }

    /// The budget of a boot poll: `AIR_VM_BOOT_TIMEOUT`.
    fn boot_budget(&self) -> Duration {
        Duration::from_secs(u64::from(self.settings.boot_timeout_seconds))
    }

    fn guest_agent_unavailable(&self, worker: &str) -> Refusal {
        Refusal::new(
            "guest_agent_unavailable",
            Exit::FAILURE,
            format!("{} guest execution is not ready in {worker}", self.settings.backend),
        )
    }
}

/// The one step of `avl_host_sys::guest` that reaches a *second* worker - the SSH host-key uniqueness check - asks this, and
/// it is answered here because "is that worker running" is a backend question the guest module does not ask.
#[async_trait]
impl PeerChannels for Manager {
    async fn peer(&self, ctx: &Ctx, worker: &str) -> Result<Option<Arc<dyn Channel>>, Refusal> {
        Ok(self.machine.running(ctx, worker).await?.then(|| self.channel(worker)))
    }
}

/// Reaches inside one worker through whichever backend is running it.
///
/// It asks for a tty exactly when there is stdin to carry, and that condition is the reason this type exists: the
/// one caller that forwards stdin - the guest file write - would otherwise produce an empty file and exit 0.
struct HypervisorChannel {
    machine: Arc<Machine>,
    runner: Runner,
    /// Names the installed guest agent, whose `relay` verb carries a connect.
    settings: Arc<Config>,
    worker: String,
}

#[async_trait]
impl Channel for HypervisorChannel {
    async fn exec(&self, ctx: &Ctx, argv: &[String], options: &SpawnOptions) -> Result<Captured, Refusal> {
        let line = self.machine.guest_argv(ctx, &self.worker, argv, options.stdin.is_some()).await?;
        Ok(self.runner.capture(ctx, &line, options).await?)
    }

    /// Runs `<installed agent> relay <port>` in the guest, with stdin carried, and answers its stdin and stdout.
    ///
    /// The agent runs as the account the exec channel lands in and not through the worker prefix: a loopback
    /// connect needs no account, and a `sudo` in front of it would be one more process to hold the pipes.
    async fn connect(&self, ctx: &Ctx, port: u16) -> Result<GuestStream, Refusal> {
        let relay = vec![
            self.settings.vm_agent.clone(),
            AgentVerb::Relay.as_str().to_owned(),
            port.to_string(),
        ];
        let line = self.machine.guest_argv(ctx, &self.worker, &relay, true).await?;
        Ok(self.runner.spawn_piped(ctx, &line)?.into())
    }

    fn worker(&self) -> &str {
        &self.worker
    }
}

/// The timeout of one guest probe, a `/usr/bin/true` over the exec channel. A guest that answers runs it in about a
/// second, so a guest that has not answered in half a minute is not answering.
const GUEST_PROBE_TIMEOUT: Duration = Duration::from_secs(30);

/// The timeout of a guest probe inside a wait with `left` of its budget: [`GUEST_PROBE_TIMEOUT`], or what is left
/// when that is less.
fn probe_timeout(left: Duration) -> Duration {
    GUEST_PROBE_TIMEOUT.min(left)
}

/// The [`BazelHost`] of a manager built without one. Every question is refused by name, which is the failure worth
/// having when a suite that promised to name its sources did not.
struct NoBazel;

#[async_trait]
impl BazelHost for NoBazel {
    async fn query(&self, _ctx: &Ctx, command: &str, _args: &[String]) -> Result<String, Refusal> {
        Err(no_bazel(command))
    }

    async fn execution_root(&self, _ctx: &Ctx) -> Result<PathBuf, Refusal> {
        Err(no_bazel("info execution_root"))
    }
}

fn no_bazel(command: &str) -> Refusal {
    Refusal::new(
        "bazel_unavailable",
        Exit::SOFTWARE,
        format!("this command has no Bazel to run `{command}` with; name the host file through its AIR_VM_* override"),
    )
}
