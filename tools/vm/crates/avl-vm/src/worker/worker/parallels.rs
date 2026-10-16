//! The Parallels lifecycle: `prlctl` against a VM the operator made. The controller resumes it on demand, provisions
//! its shares and parity lazily, and suspends it on a stop. It owns no clone, so nothing is created and nothing is
//! deleted: `pool gc` and `pool recycle` refuse.

use avl_base::{Outcome, Refusal};
use avl_host_sys::guest::ensure_host_paths;
use avl_host_sys::share::shares;
use avl_host_sys::{Ctx, SpawnOptions};
use serde_json::json;

use super::{GUEST_PROBE_TIMEOUT, Manager, StartState, StopState};
use crate::worker::parallels::{PRLCTL_START_TIMEOUT, Parallels, VmState as ParallelsState};
use avl_base::RefusalExt;

/// What `pool recycle` answers on Parallels, before any lock and again for one slot.
pub(super) const NO_RECYCLE: &str = "pool recycle re-clones Tart workers; Parallels owns its own VM";

/// What `pool gc` answers on Parallels.
pub(super) const NO_GC: &str = "pool gc removes Tart worker clones; Parallels owns its own VM";

impl Manager {
    // --- the readiness gates ---------------------------------------------------------------------------------

    /// [`Manager::require_ready`] for a Parallels worker. Fully lazy: resume the suspended VM and provision shares
    /// and parity on first use, so no pool ceremony has to run before a lease operation.
    pub(super) async fn require_parallels_ready(&self, ctx: &Ctx, parallels: &Parallels, worker: &str) -> Result<(), Refusal> {
        parallels.require_info(ctx, worker).await?;
        if !self.ready_for_parallels(ctx, parallels, worker).await? {
            self.start_without_lifecycle_lock(ctx, worker).await?;
        }
        let channel = self.channel(worker);
        let guest = self.guest(ctx, channel.as_ref());
        guest.require_console_login().await?;
        guest.ensure_ready(self).await
    }

    /// [`Manager::require_release_ready`] for a Parallels worker. A Parallels worker has no guest checkout to clean.
    /// The VM resumes on demand only to prove that no run is still active.
    pub(super) async fn require_parallels_release_ready(&self, ctx: &Ctx, parallels: &Parallels, worker: &str) -> Result<(), Refusal> {
        parallels.require_info(ctx, worker).await?;
        if !parallels.running(ctx, worker).await? || !self.guest_answers(ctx, worker, GUEST_PROBE_TIMEOUT).await {
            self.start_without_lifecycle_lock(ctx, worker).await?;
        }
        Ok(())
    }

    /// Whether a Parallels worker needs nothing done to it before a lease operation. The three conditions are in
    /// this order because each is more expensive than the last: a `prlctl status`, a guest round-trip, the parity
    /// probes.
    async fn ready_for_parallels(&self, ctx: &Ctx, parallels: &Parallels, worker: &str) -> Result<bool, Refusal> {
        if !parallels.running(ctx, worker).await? || !self.guest_answers(ctx, worker, GUEST_PROBE_TIMEOUT).await {
            return Ok(false);
        }
        let channel = self.channel(worker);
        Ok(!self.guest(ctx, channel.as_ref()).parity_broken().await?)
    }

    // --- start and stop --------------------------------------------------------------------------------------

    /// A Parallels start takes no worker away, so it reads no lease.
    pub(super) async fn start_parallels(&self, ctx: &Ctx, parallels: &Parallels, worker: &str) -> Result<StartState, Refusal> {
        parallels.require_info(ctx, worker).await?;
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        let already_running = parallels.running(ctx, worker).await? && self.guest_answers(ctx, worker, GUEST_PROBE_TIMEOUT).await;
        if !already_running {
            self.runner
                .checked(
                    ctx,
                    &[self.settings.parallels.clone(), "start".to_owned(), worker.to_owned()],
                    &SpawnOptions::within(PRLCTL_START_TIMEOUT),
                )
                .await?;
            parallels.wait_for_guest_execution(ctx, worker).await?;
        }
        parallels.wait_for_console_login(ctx, worker).await?;
        // Provisioning is lazy: the first start (or a share or repository change) repairs everything in place.
        let channel = self.channel(worker);
        let guest = self.guest(ctx, channel.as_ref());
        if guest.parity_broken().await? {
            self.provision_parallels_worker(ctx, parallels, worker).await?;
            // A share-set change power-cycled the VM; wait for the Aqua session to come back.
            parallels.wait_for_console_login(ctx, worker).await?;
        }
        guest.ensure_ready(self).await?;
        Ok(if already_running {
            StartState::AlreadyRunning
        } else {
            StartState::Started
        })
    }

    /// Suspends a running Parallels worker.
    pub(super) async fn stop_parallels(&self, ctx: &Ctx, parallels: &Parallels, worker: &str) -> Result<StopState, Refusal> {
        parallels.require_info(ctx, worker).await?;
        if !parallels.running(ctx, worker).await? {
            return Ok(StopState::AlreadyStopped);
        }
        parallels.suspend(ctx, worker, false).await?;
        Ok(StopState::Stopped)
    }

    // --- pool init -------------------------------------------------------------------------------------------

    /// Creates nothing, and provisions again the VM that the operator made. A Parallels pool takes no `golden`.
    pub(super) async fn pool_init_parallels(&self, ctx: &Ctx, parallels: &Parallels, golden: Option<&str>) -> Result<Outcome, Refusal> {
        if golden.is_some() {
            return Err(Refusal::usage("pool init on Parallels takes no golden VM"));
        }
        let Some(worker) = self.settings.workers.first().cloned() else {
            return Err(Refusal::internal("a Parallels pool names no VM"));
        };
        let initial = parallels.require_info(ctx, &worker).await?;
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        // Optional for Parallels - every lease operation provisions on demand - but the explicit way to re-provision
        // now and hand the VM back in the state it was found in.
        let restore_suspended = initial.state == Some(ParallelsState::Suspended);
        if !parallels.running(ctx, &worker).await? || !self.guest_answers(ctx, &worker, GUEST_PROBE_TIMEOUT).await {
            self.runner
                .checked(
                    ctx,
                    &[self.settings.parallels.clone(), "start".to_owned(), worker.clone()],
                    &SpawnOptions::within(PRLCTL_START_TIMEOUT),
                )
                .await?;
            parallels.wait_for_guest_execution(ctx, &worker).await?;
        }
        let mut provisioned = self.provision_parallels_worker(ctx, parallels, &worker).await;
        if restore_suspended {
            // Unconditionally, and before the provisioning error is returned: the VM was handed over suspended and
            // must go back suspended whether or not the re-provisioning took.
            let suspended = parallels.suspend(ctx, &worker, true).await;
            if provisioned.is_ok() {
                provisioned = suspended;
            }
        }
        provisioned?;
        let repo = self.settings.host_repo()?;
        let bazel_user_root = self.settings.host_bazel_user_root()?;
        Ok(Outcome {
            data: json!({
                "backend": self.settings.backend,
                "workers": [worker],
                "hostRepo": repo,
                "hostBazelUserRoot": bazel_user_root,
                "bazelShare": self.settings.bazel_share_name,
            }),
            text: format!(
                "worker={worker}\nhost_repo={}\nbazel_user_root={}",
                repo.display(),
                bazel_user_root.display()
            ),
        })
    }

    /// Reconciles the VM's stored share set and then builds the guest layout over it. Two calls, because the share
    /// declaration is the hypervisor's and the layout is the guest's - and on this backend the first can power-cycle
    /// the machine, so it has to complete before the second is attempted.
    pub(super) async fn provision_parallels_worker(&self, ctx: &Ctx, parallels: &Parallels, worker: &str) -> Result<(), Refusal> {
        parallels.declare_shares(ctx, worker, &shares(&self.settings)?).await?;
        let channel = self.channel(worker);
        self.guest(ctx, channel.as_ref()).provision_worker(self.share_mount()).await
    }
}
