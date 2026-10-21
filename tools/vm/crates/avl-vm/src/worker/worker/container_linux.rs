//! The container-linux lifecycle: the one container the skill's script starts for this checkout, its boot and its stop.
//! It also holds `pool init`, `pool gc` and the unmaking of the slot that `pool recycle` runs.

use avl_base::{Exit, Outcome, Refusal, SystemClock};
use avl_host_sys::guest::ensure_host_paths;
use avl_host_sys::{Backoff, Ctx, Poll};
use serde_json::json;

use super::pool::render_list;
use super::{GUEST_PROBE_TIMEOUT, Lease, Manager, StartState, StopState, probe_timeout, read_lease};
use crate::worker::container_linux::ContainerLinux;
use crate::worker::docker::validate_argv;
use avl_base::RefusalExt;

impl Manager {
    // --- the readiness gates ---------------------------------------------------------------------------------

    /// [`Manager::require_ready`] for a container-linux worker. Lazy like Docker: a container that does not run or does
    /// not answer is started, and the start makes the container. A running container whose start record names
    /// another argv, other mounts or another published port, is stopped first: the script makes a container again
    /// only when none runs. The caller's lease authorizes the stop, as on Docker.
    pub(super) async fn require_container_linux_ready(
        &self,
        ctx: &Ctx,
        container_linux: &ContainerLinux,
        lease: &Lease,
    ) -> Result<(), Refusal> {
        let worker = lease.worker.as_str();
        container_linux.require_available(ctx, worker).await?;
        let running = container_linux.running(ctx, worker).await?;
        if running && !container_linux.start_record_is_current(worker)? {
            container_linux.stop(ctx, worker).await?;
            return self.start_container_linux(ctx, container_linux, worker).await.map(drop);
        }
        if !running || !self.guest_answers(ctx, worker, GUEST_PROBE_TIMEOUT).await {
            return self.start_container_linux(ctx, container_linux, worker).await.map(drop);
        }
        let channel = self.channel(worker);
        let guest = self.guest(ctx, channel.as_ref());
        if guest.parity_broken().await? {
            guest.provision_worker(self.share_mount()).await?;
        }
        guest.ensure_ready(self).await
    }

    /// [`Manager::require_release_ready`] for a container-linux worker: the Docker refusals, because a Linux guest has
    /// no console login.
    pub(super) async fn require_container_linux_release_ready(
        &self,
        ctx: &Ctx,
        container_linux: &ContainerLinux,
        worker: &str,
    ) -> Result<(), Refusal> {
        container_linux.require_available(ctx, worker).await?;
        if !container_linux.running(ctx, worker).await? {
            return Err(Refusal::new(
                "worker_stopped",
                Exit::FAILURE,
                format!("worker {worker} is stopped; run pool start {worker}"),
            ));
        }
        if !self.guest_answers(ctx, worker, GUEST_PROBE_TIMEOUT).await {
            return Err(self.guest_agent_unavailable(worker));
        }
        Ok(())
    }

    // --- start -----------------------------------------------------------------------------------------------

    /// Brings a container-linux worker up: the script's `start`, which keeps a container that runs with the same image
    /// and mounts, then the Linux boot every Linux worker runs.
    ///
    /// The host paths first, because the start argv names the shares. The boot build before
    /// the start, because the boot installs the agent it builds. The script returns once the guest is ready, so the
    /// probe loop after it is short: it proves that the control port runs a command.
    pub(super) async fn start_container_linux(
        &self,
        ctx: &Ctx,
        container_linux: &ContainerLinux,
        worker: &str,
    ) -> Result<StartState, Refusal> {
        container_linux.require_available(ctx, worker).await?;
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        (self.build_boot)(ctx.clone()).await?;
        let already_running = container_linux.running(ctx, worker).await?;
        container_linux.start(ctx, worker).await?;
        if !already_running {
            // A container the script made has a fresh disk: what the host recorded about the previous guest describes
            // nothing.
            self.forget_guest(worker)?;
            self.note(worker, format!("started the testing-ui container of {worker}"));
        }
        container_linux.record_start(worker)?;
        let mut boot = Poll::start(&SystemClock, self.boot_budget(), Backoff::fixed(self.timings.boot_poll));
        loop {
            if ctx.is_cancelled() {
                return Err(self.container_linux_interrupted(worker));
            }
            if self.guest_answers(ctx, worker, probe_timeout(boot.left())).await {
                break;
            }
            if !boot.pause(ctx).await {
                if ctx.is_cancelled() {
                    return Err(self.container_linux_interrupted(worker));
                }
                return Err(Refusal::new(
                    "boot_timeout",
                    Exit::FAILURE,
                    format!(
                        "timed out waiting {}s for the control port of {worker} to run a command; AIR_VM_BOOT_TIMEOUT \
                         raises the budget",
                        self.settings.boot_timeout_seconds
                    ),
                ));
            }
        }
        let channel = self.channel(worker);
        let guest = self.guest(ctx, channel.as_ref());
        guest.provision_linux(self.bazel(), &validate_argv(&self.settings)).await?;
        if guest.parity_broken().await? {
            guest.provision_worker(self.share_mount()).await?;
        }
        guest.ensure_ready(self).await?;
        Ok(if already_running {
            StartState::AlreadyRunning
        } else {
            StartState::Started
        })
    }

    fn container_linux_interrupted(&self, worker: &str) -> Refusal {
        let cause = self.runner.interrupts().received().map_or("a cancellation", |signal| signal.name());
        Refusal::new(
            "worker_interrupted",
            Exit::SOFTWARE,
            format!("{cause} interrupted the start of the testing-ui container of {worker}"),
        )
    }

    // --- stop ------------------------------------------------------------------------------------------------

    /// Stops a container-linux worker: the script's `stop` removes the container and the files of its control port. The
    /// data directory is on the container's disk, so a stop discards the guest-local state and the next start makes it
    /// again.
    pub(super) async fn stop_container_linux(
        &self,
        ctx: &Ctx,
        container_linux: &ContainerLinux,
        worker: &str,
    ) -> Result<StopState, Refusal> {
        container_linux.require_available(ctx, worker).await?;
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        if !container_linux.running(ctx, worker).await? {
            return Ok(StopState::AlreadyStopped);
        }
        container_linux.stop(ctx, worker).await?;
        Ok(StopState::Stopped)
    }

    // --- pool init and pool gc -------------------------------------------------------------------------------

    /// Materializes a container-linux pool: the start of its one slot, because the container is all the pool holds. A
    /// container-linux pool takes no `golden`: the skill's script builds the image.
    pub(super) async fn pool_init_container_linux(
        &self,
        ctx: &Ctx,
        container_linux: &ContainerLinux,
        golden: Option<&str>,
    ) -> Result<Outcome, Refusal> {
        if golden.is_some() {
            return Err(Refusal::usage(
                "pool init on a container-linux pool takes no golden VM; the testing-ui skill builds the image",
            ));
        }
        let mut started = Vec::new();
        for worker in &self.settings.workers {
            if self.start_container_linux(ctx, container_linux, worker).await? == StartState::Started {
                started.push(worker.clone());
            }
        }
        let settings = &self.settings;
        Ok(Outcome {
            data: json!({
                "backend": settings.backend,
                "guestOs": settings.guest_os,
                "workers": settings.workers,
                "started": started,
            }),
            text: format!("workers={}\nstarted={}", settings.workers.join(","), render_list(&started)),
        })
    }

    /// Stops the unleased container of a container-linux pool. The container is what an idle slot holds. The data directory
    /// is on the container's disk, so a stop discards the guest-local state and the next start makes it again.
    ///
    /// The state is read under the slot's lifecycle lock, after the lease check, as on Docker: a start that ends
    /// between an earlier read and the lock would otherwise be stopped under a lease.
    pub(super) async fn pool_gc_container_linux(&self, ctx: &Ctx, container_linux: &ContainerLinux) -> Result<Outcome, Refusal> {
        container_linux.require_available(ctx, "").await?;
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        self.gc_each_worker(ctx, async |worker: &str| {
            if read_lease(&self.settings.lease_path(worker))?.is_some() {
                return Ok("leased".to_owned());
            }
            if !container_linux.running(ctx, worker).await? {
                return Ok("stopped".to_owned());
            }
            container_linux.stop(ctx, worker).await?;
            Ok("removed".to_owned())
        })
        .await
    }

    // --- pool recycle ----------------------------------------------------------------------------------------

    /// Stops the container and empties the host directory of the slot. The data directory is on the container's disk,
    /// so a stop discards the guest-local state and the next start makes it again.
    pub(super) async fn unmake_container_linux_worker(
        &self,
        ctx: &Ctx,
        container_linux: &ContainerLinux,
        worker: &str,
    ) -> Result<(), Refusal> {
        container_linux.require_available(ctx, worker).await?;
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        if container_linux.running(ctx, worker).await? {
            container_linux.stop(ctx, worker).await?;
        }
        self.clear_worker_state(worker)
    }
}

#[cfg(test)]
#[cfg(unix)]
mod tests;
