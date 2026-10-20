//! The daemon's boot and retirement: a fixed phase sequence from agent install to a healthy `/status`, each step
//! timed so `daemon start`'s table can say where a slow boot went.

use std::time::Duration;

use avl_base::format::{clip, words};
use avl_base::{Backend, Config, Exit, Refusal, Scope};
use avl_host_sys::guest::{GUEST_COMMAND_TIMEOUT, Guest, ShareMount, SupervisorOptions, guest_join, user_argv};
use avl_host_sys::{Backoff, Channel, Ctx, Poll, SpawnOptions};
use avl_wire::daemon::{self as wire, StateFile};
use avl_wire::progress::Phase;
use avl_wire::supervisor::{Command, Phase as RunPhase, RunState};
use avl_wire::verb::AgentVerb;
use serde_json::{Value, json};

use crate::daemon::build::PreparedBuild;
use crate::daemon::host::Host;
use crate::daemon::http::{StatusProbe, protocol_refusal, require_supported_protocol};
use crate::daemon::state::{HostState, guest_home, guest_state_dir, guest_state_file};
use crate::lane::ide::{IdeRetention, gc_guest_ides, gc_note, guest_ide_root};
use avl_base::RefusalExt;
use avl_wire::ide::IdeGcResult;

#[cfg(test)]
#[cfg(unix)]
mod tests;

/// How much of a guest document a refusal quotes.
const QUOTED_BYTES: usize = 200;
/// The fixed pause of the boot poll. A probe is two exec round trips, so a shorter pause spends the guest on probes.
const BOOT_PROBE_PAUSE: Duration = Duration::from_secs(3);
/// The fixed pause of the health poll. A probe is one `/status` exchange over the relay.
const HEALTH_PROBE_PAUSE: Duration = Duration::from_secs(2);
/// How long a supervisor cancel may take: its default grace of 10 s, plus the wait for the finish it reports.
const RETIRE_TIMEOUT: Duration = Duration::from_secs(45);
/// How many lines of a failed boot's log its refusal carries, when no record lets `daemon log` read them.
const LOG_TAIL: &str = "40";
/// The timeout of the supervisor verb that reads the tail of a failed boot's log: a read of one file in the guest.
const LOG_TIMEOUT: Duration = Duration::from_secs(10);
/// The timeout of the supervisor start. The guest supervisor waits at most 15 s for the launcher of the daemon, so
/// two minutes covers the exec round trip of a loaded guest.
const SUPERVISOR_START_TIMEOUT: Duration = Duration::from_mins(2);
/// The timeout of one supervisor status probe of the boot poll: a read of the state file of the run.
const STATUS_TIMEOUT: Duration = Duration::from_secs(15);

/// The daemon's own runtime descriptor as the guest sees it: the label's package path and name under the guest
/// runfiles root. The leading `//` and the first `:` are replaced once; a label cannot legally carry a second `:`.
pub(crate) fn self_location(prep: &PreparedBuild) -> String {
    let label = prep.label.strip_prefix("//").unwrap_or(prep.label).replacen(':', "/", 1);
    guest_join(&guest_join(&prep.guest_runfiles_root, "_main"), &format!("{label}.runtime.json"))
}

/// How often a poll probed, and how long after it started the last unsuccessful probe returned: the gap between
/// that and the successful one is the sleep nobody needed to pay. Recorded however the poll ended.
struct PollRecord<'a> {
    ctx: &'a Ctx,
    probes: u32,
    last_unsuccessful: Option<Duration>,
}

impl Drop for PollRecord<'_> {
    fn drop(&mut self) {
        self.ctx.phase().record_poll(self.probes, self.last_unsuccessful);
    }
}

/// How a health poll ended.
enum Health {
    Healthy,
    /// The budget ran out; `last` is what the final probe saw.
    Failed {
        probes: u32,
        last: StatusProbe,
    },
    Interrupted,
}

/// Whether the shares of a worker are current for a build: its last daemon was mounted for exactly this launch and
/// this mount. See [`Host::refresh_shares_if_host_bytes_moved`].
fn shares_current(previous: Option<&HostState>, prep: &PreparedBuild) -> bool {
    previous.is_some_and(|state| state.launch_digest == prep.launch_digest && state.last_mount_digest == prep.mount_digest)
}

impl Host {
    /// Refreshes the shares ([`Manager::refresh_shares`]) unless the last daemon on this worker was already mounted
    /// for exactly this build.
    ///
    /// The refresh is what makes the guest see the host's current bytes. On Tart and Parallels it is the VirtioFS
    /// remount sweep: the guest keeps a dead node for any file the host rewrote after the mount, so `stat` answers
    /// from cache while `open` fails. On a warm `daemon restart` that sweep cost 16.1 s, the steadiest cost in the
    /// whole phase table. On Docker it is a settle of about two seconds for the attribute cache of the bind mounts.
    /// If the host rewrote nothing, there is nothing to refresh, on either kind.
    ///
    /// Two digests rather than one. The mount digest is the share-backed data and the product identity; the launch
    /// digest is added because a start can be reached *because* the build moved, and a new build means new Bazel
    /// outputs that the guest is about to read through the same share.
    ///
    /// No prior state means refresh. A fresh daemon's worker may have been rebuilt against by another controller,
    /// may have rebooted since its mount was made, or may be one whose `daemon stop` deleted the only record of what
    /// it was mounted for - and the cost of guessing wrong is a test executed against an IDE built from stale bytes.
    /// Nothing can prove a mount is current; only a record of having made it can.
    ///
    /// A record kept by a start whose health poll failed still names a mount that start made, so it may skip too.
    ///
    /// Skipping cannot leave a share *unmounted*: the readiness gate runs before every start and fails closed on
    /// the parity probes, which only pass over a live mount, and the `parity-probe` phase re-checks the one file
    /// this start is about to read.
    ///
    /// [`Manager::refresh_shares`]: crate::worker::worker::Manager::refresh_shares
    pub(crate) async fn refresh_shares_if_host_bytes_moved(
        &self,
        ctx: &Ctx,
        worker: &str,
        previous: Option<&HostState>,
        prep: &PreparedBuild,
    ) -> Result<(), Refusal> {
        if shares_current(previous, prep) {
            self.reporter.note(
                format!("no host bytes moved under {worker}'s mount since its last daemon; not refreshing the shares"),
                Some(&Scope::worker(worker)),
            );
            return Ok(());
        }
        self.manager.refresh_shares(ctx, worker).await
    }

    /// Retires the daemon a state names: a polite shutdown, then the supervisor's cancel, then the record itself.
    ///
    /// The shutdown's failure is swallowed: an unreachable daemon is cancelled through the supervisor. The cancel's
    /// is not, because the record is the only thing that names the run. It goes when the supervisor answered the run
    /// finished, or when the slot shows the run no longer holds it; a recycled guest answers `invalid_state` to a
    /// cancel for a run it never had, and that record must still go. Otherwise, and when the slot cannot be read, the
    /// stop refuses `daemon_retire_failed` and the record stays, so `daemon stop` can retry it rather than orphan a
    /// live run.
    ///
    /// An interrupt fails both stops without either having happened: the shutdown answers "interrupted" at once and
    /// the cancel's guest exec is signalled. The daemon run may then still hold the guest's run slot, and the record
    /// is the only thing that lets the next `run` recognise that run as its own parked daemon, so it stays and the
    /// stop is refused.
    ///
    /// It stops no lane IDE. Each IDE is a supervisor run of its own, so it survives the daemon run. The callers that
    /// must not keep an IDE stop it through [`gc_guest_ides`]: `daemon stop`, a lease release, a pool recycle and a
    /// daemon start.
    pub(crate) async fn stop_daemon(&self, ctx: &Ctx, worker: &str, state: Option<&HostState>) -> Result<(), Refusal> {
        let Some(state) = state else {
            return Ok(());
        };
        let _ = self.daemon.http(ctx, state, &wire::SHUTDOWN, None).await;
        let cancelled = self.cancel_daemon_run(ctx, worker, &state.run_id).await;
        if ctx.is_cancelled() {
            let cause = self.interrupts.received().map_or("a cancellation", |signal| signal.name());
            return Err(Refusal::new(
                "daemon_stop_interrupted",
                Exit::SOFTWARE,
                format!(
                    "{cause} interrupted the daemon stop on {worker}; its record is kept, so `daemon stop` can \
                     finish it"
                ),
            ));
        }
        if let Err(failure) = cancelled {
            let channel = self.channel(worker);
            // A slot that cannot be read may still hold the run, so it counts as held.
            let holds = match self.guest(ctx, channel.as_ref()).active_run().await {
                Ok(active) => active.is_some_and(|active| active.run_id == state.run_id),
                Err(_) => true,
            };
            if holds || ctx.is_cancelled() {
                return Err(failure);
            }
            self.reporter.note(
                format!(
                    "daemon run {} no longer holds {worker}'s run slot; forgetting its record",
                    state.run_id
                ),
                Some(&Scope::worker(worker)),
            );
        }
        HostState::remove(&self.settings, worker)
    }

    /// Cancels one daemon run through the guest supervisor, and answers `Ok` only when the supervisor reports it
    /// finished. No `/shutdown` first: the lane IDE is a run of its own that the cancel does not reach, so a TERM loses
    /// nothing a polite stop would have saved.
    async fn cancel_daemon_run(&self, ctx: &Ctx, worker: &str, run_id: &str) -> Result<(), Refusal> {
        let channel = self.channel(worker);
        let reply = self
            .guest(ctx, channel.as_ref())
            .supervisor_run_state_reply(
                Command::Cancel,
                &words(["--root", &self.settings.vm_runs_root, "--run", run_id]),
                Some(run_id),
                SupervisorOptions {
                    aqua: false,
                    timeout: RETIRE_TIMEOUT,
                },
            )
            .await;
        let why = match reply {
            Ok(state) if state.phase == RunPhase::Finished => return Ok(()),
            Ok(state) => format!("it is still {}", state.phase),
            Err(refusal) => format!("{}: {}", refusal.code, refusal.message),
        };
        Err(Refusal::new(
            "daemon_retire_failed",
            Exit::SOFTWARE,
            format!("the guest supervisor did not finish daemon run {run_id} on {worker} ({why}); `daemon stop` retries it"),
        )
        .with_details(json!({ "runId": run_id })))
    }

    /// The refusal of a start an interrupt stopped after the supervisor started its run. The run is not cancelled,
    /// because the interrupt would fail the cancel's guest exec too, so the refusal names the verb that retires it.
    fn start_interrupted(&self, worker: &str, run_id: &str) -> Refusal {
        let cause = self.interrupts.received().map_or("a cancellation", |signal| signal.name());
        Refusal::new(
            "daemon_start_interrupted",
            Exit::SOFTWARE,
            format!(
                "{cause} interrupted the daemon start on {worker}; its run {run_id} may still hold the run slot, and \
                 `daemon stop` retires it"
            ),
        )
        .with_details(json!({ "runId": run_id }))
    }

    /// Retires a daemon run that holds the worker's slot while no host record names it, and refuses any other
    /// holder. Answers the run it retired.
    ///
    /// Such a run is a failed start's that could not cancel its own run, or a dead controller's. Trusting the prefix
    /// is safe under the guard every caller holds: the lease and the worker's lifecycle lock, which exclude a start
    /// in flight for this runtime root. Only a start sends a `run-ui-daemon-` id, so a holder with that prefix and no
    /// record is this pool's orphan.
    pub(crate) async fn retire_unrecorded_daemon(&self, ctx: &Ctx, worker: &str, operation: &str) -> Result<Option<String>, Refusal> {
        let channel = self.channel(worker);
        let Some(active) = self.guest(ctx, channel.as_ref()).active_run().await? else {
            return Ok(None);
        };
        let id = active.run_id;
        if !wire::is_daemon_run(&id) {
            return Err(Refusal::new(
                "run_active",
                Exit::FAILURE,
                format!("cannot {operation}; worker {worker} has active run {id}"),
            ));
        }
        self.reporter.note(
            format!("retiring daemon run {id} on {worker}, which no host record names"),
            Some(&Scope::worker(worker)),
        );
        self.cancel_daemon_run(ctx, worker, &id).await?;
        Ok(Some(id))
    }

    /// Builds nothing and boots everything, under the `daemon-start` progress phase. It stops every lane IDE of the
    /// worker: see [`Host::start_daemon_keeping`].
    pub(crate) async fn start_daemon(&self, ctx: &Ctx, worker: &str, prep: &PreparedBuild) -> Result<HostState, Refusal> {
        self.start_daemon_keeping(ctx, worker, prep, IdeRetention::StopAll)
            .await
            .map(|(state, _)| state)
    }

    /// [`Host::start_daemon`], which keeps the lane IDEs that `retention` names. Answers what the `ide-gc` phase did.
    ///
    /// A kept IDE outlives the daemon, and the new daemon attaches to it. An IDE cannot be kept over a refresh of a
    /// VirtioFS share, because the refresh unmounts the share that the IDE runs from. So the start stops every IDE when
    /// its refresh is a remount.
    pub(crate) async fn start_daemon_keeping(
        &self,
        ctx: &Ctx,
        worker: &str,
        prep: &PreparedBuild,
        retention: IdeRetention<'_>,
    ) -> Result<(HostState, IdeGcResult), Refusal> {
        let phase = self.reporter.start_phase(
            Phase::DaemonStart,
            "check the guest, then launch the daemon",
            Some(&Scope::worker(worker)),
        );
        let started = self.boot_daemon(ctx, worker, prep, retention).await;
        phase.finish(&started);
        started
    }

    async fn boot_daemon(
        &self,
        ctx: &Ctx,
        worker: &str,
        prep: &PreparedBuild,
        retention: IdeRetention<'_>,
    ) -> Result<(HostState, IdeGcResult), Refusal> {
        let boot_budget = self.settings.daemon.boot;
        let health_budget = self.settings.daemon.health;
        let channel = self.channel(worker);
        let channel: &dyn Channel = channel.as_ref();
        let settings = &self.settings;
        // Every step runs under a named phase of the context's timeline, so the `daemon start` table can attribute
        // its cost. Each block's guard closes its phase.
        let agent_digest = {
            let (step, _phase) = ctx.begin("install-agent");
            self.guest(&step, channel).install_agent(self.bazel.as_ref()).await?
        };
        let previous = HostState::read(settings, worker);
        {
            let (step, _phase) = ctx.begin("stop-daemon");
            self.stop_daemon(&step, worker, previous.as_ref()).await?;
        }
        let ides = {
            // After the stop of the old daemon, so no daemon launches an IDE while the gc runs, and before the
            // remount, which an IDE that runs from a share would make busy.
            let (step, _phase) = ctx.begin("ide-gc");
            let remounts = !shares_current(previous.as_ref(), prep) && self.manager.share_mount() == ShareMount::VirtioFs;
            let retention = match retention {
                IdeRetention::KeepProduct(_) if remounts => {
                    self.reporter.note(
                        format!("stopping the lane IDEs of {worker}: the start remounts the shares they run from"),
                        Some(&Scope::worker(worker)),
                    );
                    IdeRetention::StopAll
                }
                retention => retention,
            };
            let ides = gc_guest_ides(&self.guest(&step, channel), retention).await?;
            if let Some(note) = gc_note(&ides) {
                self.reporter.note(format!("{note} on {worker}"), Some(&Scope::worker(worker)));
            }
            ides
        };
        {
            let (step, _phase) = ctx.begin("reject-active-run");
            self.retire_unrecorded_daemon(&step, worker, "start the daemon").await?;
        }
        {
            // The phase keeps its name on every backend: the phase table is a contract, and the note of the refresh
            // says what it did.
            let (step, _phase) = ctx.begin("remount");
            self.refresh_shares_if_host_bytes_moved(&step, worker, previous.as_ref(), prep)
                .await?;
        }

        let location = self_location(prep);
        let visible = {
            let (step, _phase) = ctx.begin("parity-probe");
            let guest = self.guest(&step, channel);
            // The guest builds its own tree first, so the probe below asks about the tree the daemon runs from.
            // Inside this phase, because the phase table is a contract.
            guest.ensure_runfiles_tree(&prep.guest_tree).await?;
            guest
                .succeeds(&user_argv(settings, &words(["/bin/test", "-f", &location])), GUEST_COMMAND_TIMEOUT)
                .await
        };
        if !visible {
            return Err(Refusal::new(
                "guest_runtime_unreachable",
                Exit::DATA_ERR,
                format!(
                    "{location} is not visible inside {worker}; its parity layout is stale — pool start rebuilds it \
                     on Tart, and the next lease operation rebuilds it on Parallels"
                ),
            ));
        }
        {
            // This is the free-space check of a start. It comes before the lane daemon and its IDE write anything. The
            // removal of the retired roots comes first, so the check counts the space that they held. It
            // is inside this phase, because the phase table is a contract.
            let (step, _phase) = ctx.begin("free-space");
            let guest = self.guest(&step, channel);
            let scope = Scope::worker(worker);
            self.remove_retired_roots(&guest, Some(&scope)).await;
            self.require_guest_free_space(&guest).await?;
        }

        let run_id = wire::run_id(&avl_base::new_id());
        let state_dir = guest_state_dir(settings);
        let mut arguments = words([
            "--root",
            &settings.vm_runs_root,
            "--run",
            &run_id,
            "--cwd",
            &state_dir,
            "--",
            "/usr/bin/env",
        ]);
        arguments.extend(crate::lane::guest_run_environment(settings, &prep.daemon_environment));
        arguments.extend(daemon_command(settings, prep, &state_dir, &location));
        let started = {
            let (step, _phase) = ctx.begin("supervisor-start");
            let guest = self.guest(&step, channel);
            // The state directory is the working directory of the run, so it must exist before the supervisor starts
            // the run. The mkdir also makes `<vmData>/state`, where the agent lives. A worker without that directory
            // is a worker that nothing else here can reach either.
            let agent_state = guest_join(&settings.vm_data, "state");
            guest
                .as_user(
                    &words(["/bin/mkdir", "-p", &state_dir, &agent_state]),
                    &SpawnOptions::within(GUEST_COMMAND_TIMEOUT),
                )
                .await?;
            // The previous daemon leaves its state file, and the new daemon removes it only late in its boot. The
            // phases before this one proved that no daemon holds the slot, so from here only the daemon of this run
            // writes the file that the boot poll reads.
            guest
                .as_user(
                    &words(["/bin/rm", "-f", &guest_state_file(settings)]),
                    &SpawnOptions::within(GUEST_COMMAND_TIMEOUT),
                )
                .await?;
            guest
                .supervisor_run_state_reply(
                    Command::Start,
                    &arguments,
                    Some(&run_id),
                    SupervisorOptions {
                        aqua: true,
                        timeout: SUPERVISOR_START_TIMEOUT,
                    },
                )
                .await
        };
        // From here the guest may hold a run this start launched, so every refusal retires it.
        let state = match self
            .await_healthy_daemon(
                ctx,
                worker,
                channel,
                &run_id,
                prep,
                &agent_digest,
                started,
                boot_budget,
                health_budget,
            )
            .await
        {
            Ok(state) => state,
            Err(refusal) => {
                return Err(self.retire_failed_boot(ctx, worker, &run_id, refusal).await);
            }
        };
        self.reporter.note(
            format!("daemon is up on {worker} port {} (run {run_id})", state.port),
            Some(&Scope::worker(worker)),
        );
        Ok((state, ides))
    }

    /// The part of a start between the supervisor's answer and a healthy `/status`: every step that can fail while
    /// the guest holds the run this start launched. `agent_digest` is the guest agent that the start installed.
    async fn await_healthy_daemon(
        &self,
        ctx: &Ctx,
        worker: &str,
        channel: &dyn Channel,
        run_id: &str,
        prep: &PreparedBuild,
        agent_digest: &str,
        started: Result<RunState, Refusal>,
        boot_budget: Duration,
        health_budget: Duration,
    ) -> Result<HostState, Refusal> {
        let started = started?;
        if started.run_id != run_id || !matches!(started.phase, RunPhase::Running | RunPhase::Starting) {
            return Err(Refusal::new(
                "daemon_start_failed",
                Exit::FAILURE,
                format!("guest supervisor did not start {run_id}"),
            ));
        }
        let published = {
            let (step, _phase) = ctx.begin("boot-poll");
            let state_file = guest_state_file(&self.settings);
            self.boot_poll(&self.guest(&step, channel), worker, run_id, &state_file, boot_budget)
                .await?
        };
        // Before the first request, and before this controller has read a single event: the state file is the
        // earliest the two protocol versions can be compared.
        require_supported_protocol(published.protocol_version)?;

        let state = HostState {
            run_id: run_id.to_owned(),
            port: published.port,
            token: published.token,
            worker: worker.to_owned(),
            daemon_boot_stamp: published.daemon_boot_stamp,
            launch_digest: prep.launch_digest.clone(),
            last_product_digest: prep.product_digest.clone(),
            last_mount_digest: prep.mount_digest.clone(),
            last_plugins_digest: prep.plugins_digest.clone(),
            agent_digest: agent_digest.to_owned(),
        };
        // From here a failed poll leaves a record, so `daemon log` reads the boot, and `daemon stop` and the next
        // start retire it.
        state.write(&self.settings, worker)?;
        let (step, _phase) = ctx.begin("health-poll");
        match self.health_poll(&step, &state, health_budget).await? {
            Health::Healthy => Ok(state),
            Health::Interrupted => Err(self.start_interrupted(worker, run_id)),
            Health::Failed { probes, last } => Err(never_healthy(&state, health_budget, probes, &last)),
        }
    }

    /// Cancels the run a failed start launched, and answers the start's refusal with what became of that run.
    ///
    /// The record is never removed here. A start that read the state file wrote one, and it is what lets `daemon
    /// log` read the failed boot; the next start's stop-daemon removes it after a fast `/shutdown` failure and a
    /// cancel that answers finished at once. A run that will not finish makes that stop-daemon refuse
    /// `daemon_retire_failed` with the record kept. A start with no record fetches the end of the run's log instead,
    /// since nothing else can read it later.
    ///
    /// An interrupt answers `daemon_start_interrupted` whatever the refusal was, because an interrupted guest exec
    /// surfaces as an arbitrary refusal, and it skips the cancel, which the interrupt would fail too.
    async fn retire_failed_boot(&self, ctx: &Ctx, worker: &str, run_id: &str, refusal: Refusal) -> Refusal {
        if ctx.is_cancelled() {
            return self.start_interrupted(worker, run_id);
        }
        let (step, _phase) = ctx.begin("retire-failed-boot");
        let recorded = HostState::read(&self.settings, worker).is_some_and(|state| state.run_id == run_id);
        let tail = if recorded {
            None
        } else {
            let channel = self.channel(worker);
            self.guest(&step, channel.as_ref())
                .supervisor_log_reply(
                    &words(["--root", &self.settings.vm_runs_root, "--run", run_id, "--tail", LOG_TAIL]),
                    SupervisorOptions {
                        aqua: false,
                        timeout: LOG_TIMEOUT,
                    },
                )
                .await
                .ok()
                .map(|reply| reply.content)
        };
        let retired = self.cancel_daemon_run(&step, worker, run_id).await;
        if ctx.is_cancelled() {
            return self.start_interrupted(worker, run_id);
        }
        let mut refusal = refusal;
        match &retired {
            Ok(()) => refusal.message.push_str(&format!("; its run {run_id} was retired")),
            Err(failure) => refusal.message.push_str(&format!(
                "; retiring its run failed ({}), and `daemon stop` retires it",
                failure.code
            )),
        }
        let mut extra = json!({ "runId": run_id, "retired": retired.is_ok() });
        if let Some(tail) = tail {
            extra["logTail"] = Value::String(tail);
        }
        merge_details(refusal, extra)
    }

    /// Waits for the daemon to publish its state file, or for its supervisor to say it died.
    ///
    /// The poll accepts only a file that names the pid of the run. The supervisor names the pid of its child, and the
    /// lane daemon is that child. A file of another pid is not the file of this run, so the poll goes on. A budget that
    /// ends on such a file refuses `daemon_state_file_foreign` and names both pids.
    ///
    /// Counted, not just timed. Two exec round-trips and a 3 s sleep per turn, so this loop's elapsed is the JVM's
    /// boot *plus* up to 3 s of quantization plus the round-trips - three costs with three different fixes.
    async fn boot_poll(
        &self,
        guest: &Guest<'_>,
        worker: &str,
        run_id: &str,
        state_file: &str,
        budget: Duration,
    ) -> Result<StateFile, Refusal> {
        let mut wait = Poll::start(self.clock.as_ref(), budget, Backoff::fixed(BOOT_PROBE_PAUSE));
        let mut record = PollRecord {
            ctx: guest.ctx,
            probes: 0,
            last_unsuccessful: None,
        };
        // The pid that the last file of another process named, and the pid of the run then.
        let mut foreign: Option<(i64, i32)> = None;
        loop {
            record.probes += 1;
            let probe = guest
                .supervisor_run_state_reply(
                    Command::Status,
                    &words(["--root", &self.settings.vm_runs_root, "--run", run_id]),
                    Some(run_id),
                    SupervisorOptions {
                        aqua: false,
                        timeout: STATUS_TIMEOUT,
                    },
                )
                .await?;
            if probe.phase == RunPhase::Finished {
                return Err(exited_during_boot(&probe, run_id));
            }
            // Two outcomes, kept apart on purpose. A file that is not there yet is the normal case and keeps the
            // loop going; a file that is there and cannot be read used to be indistinguishable from the first, so it
            // spent the whole boot budget before reporting the wrong error. The read gets what is left of the budget
            // at most, so a guest that stops answering cannot carry the poll past its budget.
            let published = guest
                .as_user(
                    &words(["/bin/cat", state_file]),
                    &SpawnOptions::within(GUEST_COMMAND_TIMEOUT.min(wait.left())),
                )
                .await
                .map(|captured| captured.stdout)
                .unwrap_or_default();
            if !published.trim_ascii().is_empty() {
                // The daemon writes this file to a temporary path and moves it with ATOMIC_MOVE, so a reader sees a
                // whole document or no file at all. That makes every failure below a fault rather than a race.
                let state = read_state_file(state_file, &published)?;
                match probe.pid {
                    Some(pid) if i64::from(pid) == state.pid => return Ok(state),
                    // A run that is still starting names no pid, so its own file and another file look the same.
                    None => {}
                    Some(pid) => foreign = Some((state.pid, pid)),
                }
            }
            record.last_unsuccessful = Some(wait.elapsed());
            if !wait.pause(guest.ctx).await {
                if guest.ctx.is_cancelled() {
                    return Err(self.start_interrupted(worker, run_id));
                }
                break;
            }
        }
        if let Some((file_pid, run_pid)) = foreign {
            return Err(Refusal::new(
                "daemon_state_file_foreign",
                Exit::SOFTWARE,
                format!(
                    "the state file at {state_file} names pid {file_pid}, but run {run_id} is pid {run_pid}: another \
                     process wrote it, and the daemon of this run published none in time"
                ),
            )
            .with_details(json!({ "runId": run_id, "filePid": file_pid, "runPid": run_pid })));
        }
        Err(Refusal::new(
            "daemon_boot_timeout",
            Exit::SOFTWARE,
            "the daemon did not publish its state file in time",
        ))
    }

    /// Probes the daemon's own status endpoint until it names this start's daemon or the budget runs out, keeping
    /// what the last probe saw.
    ///
    /// Each probe keeps the 5 s `/status` budget rather than a longer one: iterate and the parked probe judge the
    /// daemon with that budget, so a daemon slower than that would be restarted by the next `run`. A loaded host
    /// buys more probes instead. The loop probes at least once, because the budget is a positive integer.
    async fn health_poll(&self, ctx: &Ctx, state: &HostState, budget: Duration) -> Result<Health, Refusal> {
        let mut wait = Poll::start(self.clock.as_ref(), budget, Backoff::fixed(HEALTH_PROBE_PAUSE));
        let mut record = PollRecord {
            ctx,
            probes: 0,
            last_unsuccessful: None,
        };
        loop {
            record.probes += 1;
            let last = match self.daemon.probe_status(ctx, state).await? {
                StatusProbe::Healthy(_) => return Ok(Health::Healthy),
                StatusProbe::Interrupted => return Ok(Health::Interrupted),
                other => other,
            };
            record.last_unsuccessful = Some(wait.elapsed());
            if !wait.pause(ctx).await {
                if ctx.is_cancelled() {
                    return Ok(Health::Interrupted);
                }
                return Ok(Health::Failed {
                    probes: record.probes,
                    last,
                });
            }
        }
    }
}

/// The command of the daemon in the run slot, after the environment: the `lane-daemon` verb of the installed guest
/// agent.
///
/// The verb reads the runtime descriptor at `location` itself, and the lane IDE inputs from it. Its arguments name the
/// state directory, the port, the launch digest, the IDE root and the home in the state directory, which the IDE gets
/// as `idea.dev.project.root`. The boot of the verb makes those directories and removes the state file of the previous
/// daemon.
pub(crate) fn daemon_command(settings: &Config, prep: &PreparedBuild, state_dir: &str, location: &str) -> Vec<String> {
    let mut command = words([
        &settings.vm_agent,
        AgentVerb::LaneDaemon.as_str(),
        "--state-dir",
        state_dir,
        "--port",
        &settings.daemon.port.to_string(),
        "--launch-digest",
        &prep.launch_digest,
        "--descriptor",
        location,
        "--runfiles-root",
        &prep.guest_runfiles_root,
        "--ide-root",
        &guest_ide_root(settings),
        "--home",
        &guest_home(settings),
    ]);
    if settings.backend == Backend::ContainerLinux {
        // A published port forwards to the address of the container, not to its loopback.
        command.extend(words(["--bind", "0.0.0.0"]));
    }
    command
}

/// The refusal of a daemon the health poll never reached, naming what the last probe saw. Only a relay that never
/// opened blames the path to the daemon: once the state file exists the daemon's listener is bound, so anything else
/// is a starved guest or a daemon that is not this start's.
fn never_healthy(state: &HostState, budget: Duration, probes: u32, last: &StatusProbe) -> Refusal {
    let address = format!("{}:{}", state.worker, state.port);
    let seconds = budget.as_secs();
    let detail = last.detail();
    let message = match last {
        StatusProbe::NoConnection(_) => format!(
            "the daemon published its state file, but no relay to 127.0.0.1:{} inside {} opened in {seconds} s \
             ({probes} probes; last: {detail}); read `daemon log`",
            state.port, state.worker
        ),
        StatusProbe::NotAStatus(_) => {
            format!("{address} answered /status with {detail} ({probes} probes in {seconds} s); read `daemon log`")
        }
        StatusProbe::OtherDaemon(_) => {
            format!("{address} answered for another daemon ({detail}); read `daemon log`")
        }
        // No reply, a broken exchange, and the two a failed poll cannot end on.
        _ => format!(
            "{address} accepted connections, but no /status answered within 5 s in {seconds} s ({probes} probes; \
             last: {detail}); the guest is starved or the daemon is wedged: read `daemon log`, and on a loaded host \
             raise AIR_VM_DAEMON_HEALTH_TIMEOUT"
        ),
    };
    Refusal::new("daemon_unreachable", Exit::UNAVAILABLE, message).with_details(json!({
        "runId": state.run_id,
        "probes": probes,
        "budgetSeconds": seconds,
        "reason": last.reason(),
        "lastProbe": detail,
    }))
}

/// A refusal with more details, merged key by key into the ones it already carries, which win.
fn merge_details(refusal: Refusal, extra: Value) -> Refusal {
    let mut merged = match refusal.details() {
        Some(Value::Object(fields)) => fields,
        Some(other) => {
            let mut fields = serde_json::Map::new();
            fields.insert("detail".to_owned(), other);
            fields
        }
        None => serde_json::Map::new(),
    };
    if let Value::Object(extra) = extra {
        for (key, value) in extra {
            merged.entry(key).or_insert(value);
        }
    }
    refusal.with_details(Value::Object(merged))
}

fn exited_during_boot(probe: &RunState, run_id: &str) -> Refusal {
    let outcome = probe.outcome.as_ref().map_or_else(|| "no outcome".to_owned(), ToString::to_string);
    let exit = probe.exit_code.map_or_else(|| "unknown".to_owned(), |code| code.to_string());
    Refusal::new(
        "daemon_start_failed",
        Exit::SOFTWARE,
        format!("the daemon exited during boot ({outcome}, exit {exit}); the end of its log is the refusal's logTail detail"),
    )
    .with_details(json!({ "runId": run_id }))
}

/// Reads the state file the daemon published, refusing one that is there and cannot be read.
fn read_state_file(path: &str, published: &str) -> Result<StateFile, Refusal> {
    let unreadable = |detail: String| {
        Refusal::new(
            wire::CODE_STATE_FILE_UNREADABLE,
            Exit::SOFTWARE,
            format!("the daemon's state file at {path} {detail}"),
        )
    };
    let parsed: Value =
        serde_json::from_str(published).map_err(|error| unreadable(format!("is not JSON: {} ({error})", clip(published, QUOTED_BYTES))))?;
    if !parsed.is_object() {
        return Err(unreadable(format!("is {parsed} rather than an object")));
    }
    wire::decode_state_file(published.as_bytes()).map_err(protocol_refusal)
}
