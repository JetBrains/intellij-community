//! The Docker lifecycle: the image, the container of a slot and its reconcile, the boot and the stop. It also
//! holds `pool init`, `pool gc`, the unmaking of a slot that `pool recycle` runs, and the stop and the delete of the
//! Lima engine that `pool stop` and `pool recycle all` run.

use std::collections::BTreeMap;

use avl_base::SystemClock;
use avl_base::{Exit, Outcome, Refusal};
use avl_host_sys::guest::ensure_host_paths;
use avl_host_sys::{Backoff, Ctx, Poll};
use serde_json::json;

use super::pool::render_list;
use super::{GUEST_PROBE_TIMEOUT, Lease, Manager, StartState, StopState, probe_timeout, read_lease};
use crate::worker::docker::{ContainerState, Docker, container_unusable, validate_argv};
use crate::worker::lima::{EngineState, Lima};
use avl_base::RefusalExt;

#[cfg(test)]
mod tests;

impl Manager {
    // --- the readiness gates ---------------------------------------------------------------------------------

    /// [`Manager::require_ready`] for a Docker worker. Lazy like Tart: a slot with no container, or one that does not
    /// run or does not answer, is started, and the start makes the container.
    ///
    /// A container that is not current, because its record declares other shares or another image or the engine
    /// gives the name another id, is made again by the start. The caller's own lease authorizes the stop, as for
    /// [`Manager::share_declaration_stale`]: a second checkout on the same host is the ordinary way to get here. The
    /// volume keeps `WorkerData`, so the recreate costs a start, not a staging.
    pub(super) async fn require_docker_ready(&self, ctx: &Ctx, docker: &Docker, lease: &Lease) -> Result<(), Refusal> {
        let worker = lease.worker.as_str();
        docker.require_available_for(ctx, Some(lease)).await?;
        if !docker.running(ctx, worker).await?
            || !docker.container_is_current(ctx, worker).await?
            || !self.guest_answers(ctx, worker, GUEST_PROBE_TIMEOUT).await
        {
            return self.start_docker(ctx, docker, worker, Some(lease)).await.map(drop);
        }
        let channel = self.channel(worker);
        let guest = self.guest(ctx, channel.as_ref());
        if guest.parity_broken().await? {
            guest.provision_worker(self.share_mount()).await?;
        }
        guest.ensure_ready(self).await
    }

    /// [`Manager::require_release_ready`] for a Docker worker: the Tart refusals without the console-login wait,
    /// because a Linux guest has no console login.
    ///
    /// A release never starts the Lima engine and never makes it again ([`Docker::reach_engine`]). A stopped engine
    /// runs no container, so the release is refused `worker_stopped` at once, and a template change waits for the next
    /// start. Otherwise the lease under release would refuse its own release with `worker_leased`.
    pub(super) async fn require_docker_release_ready(&self, ctx: &Ctx, docker: &Docker, worker: &str) -> Result<(), Refusal> {
        if docker.stopped(ctx, worker).await? {
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

    /// Brings a Docker worker up: the image, the container, the start, then the Linux boot every Linux worker runs.
    ///
    /// The order is the Tart order, for the Tart reasons. The host paths first, because the create argv names the
    /// shares. The boot build before anything is made, because the boot installs the agent it builds. The image
    /// before the container, because the create names the image tag.
    ///
    /// `authorized` is the lease of a caller that may make a running container again, as for
    /// [`Manager::reconcile_docker_container`]. `pool start` passes none.
    pub(super) async fn start_docker(
        &self,
        ctx: &Ctx,
        docker: &Docker,
        worker: &str,
        authorized: Option<&Lease>,
    ) -> Result<StartState, Refusal> {
        docker.require_available_for(ctx, authorized).await?;
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        (self.build_boot)(ctx.clone()).await?;
        self.ensure_docker_image(ctx, docker).await?;
        self.reconcile_docker_container(ctx, docker, worker, authorized).await?;
        let already_running = match docker.state(ctx, worker).await? {
            ContainerState::Running => true,
            ContainerState::Other(word) => return Err(container_unusable(worker, &word)),
            ContainerState::Absent | ContainerState::Created | ContainerState::Exited(_) => false,
        };
        if !already_running {
            docker.start(ctx, worker).await?;
            self.note(worker, format!("started the container {worker}"));
        }
        let mut boot = Poll::start(&SystemClock, self.boot_budget(), Backoff::fixed(self.timings.boot_poll));
        loop {
            if ctx.is_cancelled() {
                return Err(self.docker_interrupted(worker));
            }
            if self.guest_answers(ctx, worker, probe_timeout(boot.left())).await {
                break;
            }
            // The entrypoint exits when the display or the window manager does not come up, and a stopped
            // container never answers: the refusal says why at once instead of after the whole budget. A paused
            // container answers no `docker exec` either, and nothing in the start unpauses it.
            match docker.state(ctx, worker).await? {
                ContainerState::Exited(code) => {
                    let tail = docker.log_tail(ctx, worker).await;
                    return Err(Refusal::new(
                        "container_exited",
                        Exit::FAILURE,
                        format!("the container {worker} exited with {code} while starting:\n{tail}"),
                    ));
                }
                ContainerState::Other(word) => return Err(container_unusable(worker, &word)),
                ContainerState::Absent => {
                    return Err(Refusal::new(
                        "container_exited",
                        Exit::FAILURE,
                        format!("the container {worker} was removed while starting"),
                    ));
                }
                ContainerState::Created | ContainerState::Running => {}
            }
            if !boot.pause(ctx).await {
                if ctx.is_cancelled() {
                    return Err(self.docker_interrupted(worker));
                }
                return Err(Refusal::new(
                    "boot_timeout",
                    Exit::FAILURE,
                    format!(
                        "timed out waiting {}s for the container {worker} to answer `docker exec`; \
                         AIR_VM_BOOT_TIMEOUT raises the budget",
                        self.settings.boot_timeout_seconds
                    ),
                ));
            }
        }
        self.finish_docker_start(ctx, worker).await?;
        Ok(if already_running {
            StartState::AlreadyRunning
        } else {
            StartState::Started
        })
    }

    /// The guest half of a Docker boot: the agent install and `validate-guest`, then the parity layout over the two
    /// read-only shares, then the readiness a run needs.
    ///
    /// A Linux guest has no console login to wait for, so there is no "poll again" answer here: every refusal means
    /// the start failed. The image installs the packages and the pinned Node, and its entrypoint starts the display,
    /// so the boot has no provisioning verb.
    async fn finish_docker_start(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        let channel = self.channel(worker);
        let guest = self.guest(ctx, channel.as_ref());
        guest.provision_linux(self.bazel(), &validate_argv(&self.settings)).await?;
        if guest.parity_broken().await? {
            guest.provision_worker(self.share_mount()).await?;
        }
        guest.ensure_ready(self).await
    }

    /// The image every container of the pool runs, pulled or built under the pool-wide image lock.
    ///
    /// The logs, the build context and the image record are pool-wide, and a build first empties its context. Two
    /// slots that start at once would each delete the other's context and logs. The lock makes the second start
    /// wait. It then finds the image the first one pulled or built, and does neither.
    pub(super) async fn ensure_docker_image(&self, ctx: &Ctx, docker: &Docker) -> Result<String, Refusal> {
        let _held = self
            .locks
            .acquire_queued(
                ctx,
                &self.settings.docker_image_lock_path(),
                "docker-image",
                self.timings.docker_image_wait,
                "docker_image_busy",
                &format!(
                    "another start held the Docker image lock {} for longer than {} s; its pull log is {} and \
                     its build log is {}",
                    self.settings.docker_image_lock_path().display(),
                    self.timings.docker_image_wait.as_secs(),
                    self.settings.docker_pull_log_path().display(),
                    self.settings.docker_build_log_path().display()
                ),
            )
            .await?;
        docker.ensure_image(ctx).await
    }

    /// Makes the worker's container match the create argv of this checkout: made when absent, made again when it is
    /// not current, kept otherwise. Answers whether it made the container.
    ///
    /// A container is current when its record declares the current argv and the engine's id for the name is the
    /// recorded one ([`Docker::container_is_current`]). A container with no record, or of another id, is made again
    /// rather than trusted: the record is the only evidence of the shares and the image it declares, and a container
    /// of that name is not proof it is this controller's.
    ///
    /// A running container is made again only for a caller that may take it away. That is an unleased worker, or the
    /// holder of its lease, which `authorized` names. The pool is per machine, so a second checkout declares other
    /// shares, and its `pool start` must not stop the container that another session runs its tests in. It gets
    /// `worker_leased`, as `pool stop` does. A container that does not run is made again for any caller: nothing runs
    /// in it, its volume stays, and the next start of the lease holder would bring it up the same way.
    ///
    /// A paused, restarting or dead container is refused with its state word. The start cannot use it, and making it
    /// again would hide the state from the operator who has to look at it.
    pub(super) async fn reconcile_docker_container(
        &self,
        ctx: &Ctx,
        docker: &Docker,
        worker: &str,
        authorized: Option<&Lease>,
    ) -> Result<bool, Refusal> {
        let state = docker.state(ctx, worker).await?;
        match &state {
            ContainerState::Absent => {}
            ContainerState::Other(word) => return Err(container_unusable(worker, word)),
            ContainerState::Created | ContainerState::Exited(_) | ContainerState::Running => {
                if docker.container_is_current(ctx, worker).await? {
                    return Ok(false);
                }
                if state == ContainerState::Running {
                    self.require_unleased(worker, "start", authorized)?;
                }
                self.note(
                    worker,
                    format!(
                        "the container {worker} does not declare the current shares and image, or this controller \
                         did not create it; creating it again"
                    ),
                );
                if state == ContainerState::Running {
                    docker.stop(ctx, worker).await?;
                }
                docker.remove_stopped(ctx, worker).await?;
            }
        }
        docker.create(ctx, worker).await?;
        Ok(true)
    }

    fn docker_interrupted(&self, worker: &str) -> Refusal {
        let cause = self.runner.interrupts().received().map_or("a cancellation", |signal| signal.name());
        Refusal::new(
            "worker_interrupted",
            Exit::SOFTWARE,
            format!("{cause} interrupted the start of the container {worker}"),
        )
    }

    // --- stop ------------------------------------------------------------------------------------------------

    /// Stops a Docker worker's container. No suspend: the container keeps its volume, so the next start keeps the
    /// staging, and only the daemon starts cold.
    ///
    /// The engine is reached and never changed ([`Docker::reach_engine`]): a Lima engine that does not run holds no
    /// running container, so the stop is done, and a Lima engine of another template is not made again.
    pub(super) async fn stop_docker(&self, ctx: &Ctx, docker: &Docker, worker: &str) -> Result<StopState, Refusal> {
        if !docker.reach_engine(ctx).await? {
            return Ok(StopState::AlreadyStopped);
        }
        match docker.state(ctx, worker).await? {
            ContainerState::Running => {}
            // A paused container is not stopped, and `AlreadyStopped` would say it is.
            ContainerState::Other(word) => return Err(container_unusable(worker, &word)),
            ContainerState::Absent | ContainerState::Created | ContainerState::Exited(_) => {
                return Ok(StopState::AlreadyStopped);
            }
        }
        docker.stop(ctx, worker).await?;
        Ok(StopState::Stopped)
    }

    // --- the Lima engine --------------------------------------------------------------------------------------

    /// Stops the Lima engine after `pool stop`, when nothing of the pool needs it: no worker is leased and no
    /// container runs. Nothing stays warm. Answers the engine's state word after the hook.
    ///
    /// The check and the stop run under the lifecycle lock of every slot, because a lease acquisition takes the lock
    /// of its slot: no lease can start between the check and the stop. An engine that does not run is left as it is,
    /// and no `docker` command asks it.
    pub(super) async fn stop_docker_engine(&self, ctx: &Ctx, docker: &Docker, engine: &Lima) -> Result<String, Refusal> {
        let workers = self.settings.workers.clone();
        self.with_lifecycle_locks(ctx, &workers, "pool-stop-engine", async {
            if !docker.reach_engine(ctx).await? {
                return Ok(engine.state(ctx).await?.as_str().to_owned());
            }
            for worker in &workers {
                let busy = if read_lease(&self.settings.lease_path(worker))?.is_some() {
                    Some("is leased")
                } else if docker.state(ctx, worker).await? == ContainerState::Running {
                    Some("runs its container")
                } else {
                    None
                };
                if let Some(reason) = busy {
                    self.note(worker, format!("the Lima engine keeps running, because {worker} {reason}"));
                    return Ok(EngineState::Running.as_str().to_owned());
                }
            }
            Ok(engine.stop(ctx).await?.as_str().to_owned())
        })
        .await
    }

    /// Deletes the Lima engine before `pool recycle all`, unless a worker is leased. The delete takes every container
    /// and volume of the pool, and the recycle of each slot then makes the engine and the containers again. A leased
    /// worker keeps the engine: its own recycle is refused `worker_leased`, and the other slots are recycled on the
    /// engine that runs.
    pub(super) async fn delete_docker_engine(&self, ctx: &Ctx, engine: &Lima) -> Result<(), Refusal> {
        self.prepare_runtime_dirs()?;
        let workers = self.settings.workers.clone();
        self.with_lifecycle_locks(ctx, &workers, "pool-recycle-engine", async {
            if let Err(refusal) = engine.require_no_lease("delete the Lima engine", None) {
                self.reporter
                    .note(format!("{}; recycling the slots on the engine that runs", refusal.message), None);
                return Ok(());
            }
            engine.delete(ctx).await
        })
        .await
    }

    // --- pool init and pool gc -------------------------------------------------------------------------------

    /// Materializes a Docker pool: the image, then a container per slot that has none or has a stale one. Nothing is
    /// started, as `pool init` on Tart boots nothing: the first lease operation starts a slot. A Docker pool takes no
    /// `golden`, because its image is built from the Dockerfile.
    ///
    /// The caller holds every slot's lifecycle lock. The reconcile authorizes no lease, so `pool init` refuses to make
    /// a running container of another session again, as `pool start` does.
    pub(super) async fn pool_init_docker(&self, ctx: &Ctx, docker: &Docker, golden: Option<&str>) -> Result<Outcome, Refusal> {
        if golden.is_some() {
            return Err(Refusal::usage(
                "pool init on a Docker pool takes no golden VM; the image is built from the Dockerfile",
            ));
        }
        docker.require_available(ctx, "").await?;
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        self.prepare_runtime_dirs()?;
        let image = self.ensure_docker_image(ctx, docker).await?;
        let mut created = Vec::new();
        for worker in &self.settings.workers {
            if self.reconcile_docker_container(ctx, docker, worker, None).await? {
                created.push(worker.clone());
            }
        }
        let settings = &self.settings;
        Ok(Outcome {
            data: json!({
                "backend": settings.backend,
                "guestOs": settings.guest_os,
                "image": image,
                "baseImage": settings.docker_base_image,
                "workers": settings.workers,
                "created": created,
            }),
            text: format!(
                "image={image}\nbase_image={}\nworkers={}\ncreated={}",
                settings.docker_base_image,
                settings.workers.join(","),
                render_list(&created)
            ),
        })
    }

    /// Removes the stopped, unleased containers of a Docker pool and keeps their volumes.
    ///
    /// The volume is what makes the next start cheap: the staged daemon runtime, the download cache and the guest
    /// agent survive, so a slot that gc removed starts again warm. `pool recycle` is the command that drops a volume.
    ///
    /// The state is read under the slot's lifecycle lock, after the lease check, and not before the lock. A
    /// `pool start` that ends between an earlier read and the lock would otherwise leave a running container that gc
    /// takes for a stopped one. Only a created, exited or dead container is removed. The removal is `docker rm`
    /// without `--force`, so the engine refuses a container that runs after all. A paused, restarting or removing
    /// container is kept with its state word.
    ///
    /// The engine is reached and never changed ([`Docker::reach_engine`]). A Lima engine that does not run is left
    /// stopped, and every slot is kept with the engine's word.
    pub(super) async fn pool_gc_docker(&self, ctx: &Ctx, docker: &Docker) -> Result<Outcome, Refusal> {
        self.prepare_runtime_dirs()?;
        if !docker.reach_engine(ctx).await? {
            let word = match docker.engine() {
                Some(engine) => format!("engine-{}", engine.state(ctx).await?.as_str().to_lowercase()),
                None => "engine-unreachable".to_owned(),
            };
            let kept: BTreeMap<String, String> = self.settings.workers.iter().map(|worker| (worker.clone(), word.clone())).collect();
            return Ok(Outcome {
                data: json!({
                    "backend": self.settings.backend, "action": "gc", "removed": [], "kept": kept,
                }),
                text: "removed=(none)".to_owned(),
            });
        }
        let mut removed = Vec::new();
        let mut kept = BTreeMap::new();
        for worker in &self.settings.workers {
            let outcome = self
                .with_lifecycle_lock(ctx, worker, "pool-gc", async {
                    if read_lease(&self.settings.lease_path(worker))?.is_some() {
                        return Ok("leased".to_owned());
                    }
                    let state = docker.state(ctx, worker).await?;
                    match &state {
                        ContainerState::Absent => Ok("absent".to_owned()),
                        ContainerState::Running => Ok("running".to_owned()),
                        ContainerState::Created | ContainerState::Exited(_) => {
                            docker.remove_stopped(ctx, worker).await?;
                            Ok("removed".to_owned())
                        }
                        ContainerState::Other(word) if word == "dead" => {
                            docker.remove_stopped(ctx, worker).await?;
                            Ok("removed".to_owned())
                        }
                        ContainerState::Other(word) => Ok(word.clone()),
                    }
                })
                .await?;
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

    // --- pool recycle ----------------------------------------------------------------------------------------

    /// Removes the container and its volume, then empties the host directory, because its receipts describe that
    /// volume. A recycle is the one command that drops the volume: a guest that is not worth a repair in place can
    /// be broken in what it staged.
    ///
    /// No stop runs first, and the removal uses `--force`. A paused, restarting or dead container cannot be stopped,
    /// and it is the case a recycle exists for. The caller passed the lease guard, so nothing that runs in the
    /// container belongs to another session.
    pub(super) async fn unmake_docker_worker(&self, ctx: &Ctx, docker: &Docker, worker: &str) -> Result<(), Refusal> {
        docker.require_available(ctx, worker).await?;
        if docker.state(ctx, worker).await? != ContainerState::Absent {
            docker.remove(ctx, worker).await?;
        }
        docker.remove_volume(ctx, worker).await?;
        self.clear_worker_state(worker)
    }
}
