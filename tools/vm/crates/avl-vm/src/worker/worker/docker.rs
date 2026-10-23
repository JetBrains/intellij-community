//! The Docker lifecycle: the image, the container of a slot and its reconcile, the boot and the stop. It also
//! holds `pool init`, `pool gc` and the unmaking of a slot that `pool recycle` runs. On the Apple `container` engine
//! `pool recycle all` deletes the builder VM. `pool stop` stops the containers only, on every engine.

use std::collections::BTreeMap;

use avl_base::SystemClock;
use avl_base::{Exit, Outcome, Refusal};
use avl_host_sys::guest::ensure_host_paths;
use avl_host_sys::{Backoff, Ctx, Poll};
use serde_json::json;

use super::pool::render_list;
use super::{GUEST_PROBE_TIMEOUT, Lease, Manager, StartState, StopState, probe_timeout, read_lease};
use crate::worker::docker::{ContainerState, Docker, container_unusable};
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
    /// volume keeps `WorkerData`, so the recreate costs a start and no new guest-local state.
    pub(super) async fn require_docker_ready(&self, ctx: &Ctx, docker: &Docker, lease: &Lease) -> Result<(), Refusal> {
        let worker = lease.worker.as_str();
        docker.require_available(ctx, worker).await?;
        if !docker.running(ctx, worker).await?
            || !docker.container_is_current(ctx, worker).await?
            || !self.guest_answers(ctx, worker, GUEST_PROBE_TIMEOUT).await
        {
            return self.start_docker(ctx, docker, worker, Some(lease)).await.map(drop);
        }
        let channel = self.channel(worker);
        self.ready_guest(&self.guest(ctx, channel.as_ref())).await
    }

    /// [`Manager::require_release_ready`] for a Docker worker: the Tart refusals without the console-login wait,
    /// because a Linux guest has no console login.
    ///
    /// A release never starts the engine ([`Docker::reach_engine`]). An Apple `container` server that does not run
    /// holds no running container, so the release is refused `worker_stopped` at once.
    pub(super) async fn require_docker_release_ready(&self, ctx: &Ctx, docker: &Docker, worker: &str) -> Result<(), Refusal> {
        self.require_running_guest(ctx, worker, !docker.stopped(ctx, worker).await?).await
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
        // A start keeps the worker running, so the idle stop of the last release finds no record.
        super::remove_idle_stop_record(&self.settings, worker)?;
        docker.require_available(ctx, worker).await?;
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        (self.build_boot)(ctx.clone()).await?;
        self.ensure_docker_image(ctx, docker).await?;
        self.reconcile_docker_container(ctx, docker, worker, authorized).await?;
        let already_running = match docker.state(ctx, worker).await? {
            ContainerState::Running => true,
            ContainerState::Other(word) => return Err(container_unusable(worker, &word)),
            ContainerState::Absent | ContainerState::Created | ContainerState::Exited(_) | ContainerState::Stopped => false,
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
                // The Apple engine keeps no exit code in `inspect`. Its boot log has it, in the `vminitd` line
                // `status: <n> managed process exit`, so the refusal quotes both logs. Both are read here, before
                // anything starts the container again, because a start clears them.
                ContainerState::Stopped => {
                    let tail = docker.log_tail(ctx, worker).await;
                    let boot = docker.boot_log_tail(ctx, worker).await;
                    return Err(Refusal::new(
                        "container_exited",
                        Exit::FAILURE,
                        format!("the container {worker} stopped while starting:\n{tail}\nthe boot log of the container:\n{boot}"),
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
        self.boot_linux_guest(ctx, worker).await?;
        Ok(if already_running {
            StartState::AlreadyRunning
        } else {
            StartState::Started
        })
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
            ContainerState::Created | ContainerState::Exited(_) | ContainerState::Stopped | ContainerState::Running => {
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
        self.interrupted_by(|cause| format!("{cause} interrupted the start of the container {worker}"))
    }

    // --- stop ------------------------------------------------------------------------------------------------

    /// Stops a Docker worker's container. No suspend: the container keeps its volume, so the next start keeps the
    /// guest-local state, and only the daemon starts cold.
    ///
    /// The engine is reached and never started ([`Docker::reach_engine`]): an Apple `container` server that does not
    /// run holds no running container, so the stop is done.
    pub(super) async fn stop_docker(&self, ctx: &Ctx, docker: &Docker, worker: &str) -> Result<StopState, Refusal> {
        if !docker.reach_engine(ctx).await? {
            return Ok(StopState::AlreadyStopped);
        }
        match docker.state(ctx, worker).await? {
            ContainerState::Running => {}
            // A paused container is not stopped, and `AlreadyStopped` would say it is.
            ContainerState::Other(word) => return Err(container_unusable(worker, &word)),
            ContainerState::Absent | ContainerState::Created | ContainerState::Exited(_) | ContainerState::Stopped => {
                return Ok(StopState::AlreadyStopped);
            }
        }
        docker.stop(ctx, worker).await?;
        Ok(StopState::Stopped)
    }

    // --- the builder of the Apple `container` engine ---------------------------------------------------------

    /// Deletes the builder VM of the Apple `container` engine before `pool recycle all`, under the pool-wide image lock,
    /// so no build of another start runs in it. The next build makes it again. The containers of the pool go with the
    /// recycle of each slot, and the server stays: a `system stop` would stop every container of the login session.
    pub(super) async fn delete_docker_builder(&self, ctx: &Ctx, docker: &Docker) -> Result<(), Refusal> {
        self.prepare_runtime_dirs()?;
        let _held = self
            .locks
            .acquire_queued(
                ctx,
                &self.settings.docker_image_lock_path(),
                "docker-image",
                self.timings.docker_image_wait,
                "docker_image_busy",
                &format!(
                    "another start held the Docker image lock {} for longer than {} s; its build log is {}",
                    self.settings.docker_image_lock_path().display(),
                    self.timings.docker_image_wait.as_secs(),
                    self.settings.docker_build_log_path().display()
                ),
            )
            .await?;
        docker.delete_builder(ctx).await
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
    /// The volume is what makes the next start cheap: the runfiles trees, the download cache and the guest agent
    /// survive, so a slot that gc removed starts again warm. `pool recycle` is the command that drops a volume.
    ///
    /// The state is read under the slot's lifecycle lock, after the lease check, and not before the lock. A
    /// `pool start` that ends between an earlier read and the lock would otherwise leave a running container that gc
    /// takes for a stopped one. Only a created, exited or dead container is removed. The removal is `docker rm`
    /// without `--force`, so the engine refuses a container that runs after all. A paused, restarting or removing
    /// container is kept with its state word.
    ///
    /// The engine is reached and never started ([`Docker::reach_engine`]). An Apple `container` server that does not
    /// run stays stopped, and every slot is kept with the word `engine-stopped`. The engine of the host environment
    /// answers or refuses, so it has no such word.
    pub(super) async fn pool_gc_docker(&self, ctx: &Ctx, docker: &Docker) -> Result<Outcome, Refusal> {
        self.prepare_runtime_dirs()?;
        if !docker.reach_engine(ctx).await? {
            let kept: BTreeMap<String, String> = self
                .settings
                .workers
                .iter()
                .map(|worker| (worker.clone(), "engine-stopped".to_owned()))
                .collect();
            return Ok(Outcome {
                data: json!({
                    "backend": self.settings.backend, "action": "gc", "removed": [], "kept": kept,
                }),
                text: "removed=(none)".to_owned(),
            });
        }
        self.gc_each_worker(ctx, async |worker: &str| {
            if read_lease(&self.settings.lease_path(worker))?.is_some() {
                return Ok("leased".to_owned());
            }
            let state = docker.state(ctx, worker).await?;
            match &state {
                ContainerState::Absent => Ok("absent".to_owned()),
                ContainerState::Running => Ok("running".to_owned()),
                ContainerState::Created | ContainerState::Exited(_) | ContainerState::Stopped => {
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
        .await
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
