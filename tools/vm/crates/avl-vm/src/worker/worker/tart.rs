//! The Tart lifecycle: the `tart run` argv, the detached run process and its identity, and the suspended state.
//! It holds the host's concurrent-VM limit, the boot sequence, and the orphaned run process. It also holds the clone of a slot,
//! `pool init`, `pool gc`, and the unmaking of a slot that `pool recycle` runs.

use std::collections::BTreeMap;
use std::fs::OpenOptions;
use std::os::unix::fs::OpenOptionsExt;
use std::path::Path;
use std::time::Duration;

use avl_base::SystemClock;
use avl_base::format::words;
use avl_base::{Exit, OrRefuse, Outcome, Refusal, SCHEMA_VERSION, validate_name};
use avl_host_sys::guest::{ensure_host_paths, read_init_receipt};
use avl_host_sys::share::shares;
use avl_host_sys::{Backoff, Ctx, Poll, PsField, SpawnOptions};
use nix::sys::signal::{Signal, kill, killpg};
use nix::unistd::Pid;
use serde::{Deserialize, Serialize};
use serde_json::json;

use super::pool::render_list;
use super::{GUEST_PROBE_TIMEOUT, Lease, Manager, StartState, StopState, probe_timeout, read_lease};
use crate::worker::secure::write_json_line;
use crate::worker::tart::{ProcessIdentity, TART_CLONE_TIMEOUT, TART_DELETE_TIMEOUT, TART_SET_TIMEOUT, TART_STOP_TIMEOUT, Tart, VmState};

/// What `tart run` prints when Virtualization.framework refuses another machine. Matched as prose because it is the
/// only signal: the run process exits with a status that says nothing else.
const HOST_VM_LIMIT_MESSAGE: &str = "The number of VMs exceeds the system limit";

/// What a suspended worker was suspended with: `<worker>/suspended.json`.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SuspendedState {
    pub(crate) schema_version: u32,
    pub(crate) worker: String,
    pub(crate) run_argv: Vec<String>,
}

impl Manager {
    fn tart_program(&self) -> String {
        self.tart().map(Tart::program).unwrap_or_default()
    }

    // --- the run process -------------------------------------------------------------------------------------

    /// The whole `tart run` command line for one worker.
    ///
    /// A function rather than an inline array because it is also the record of what a running worker was launched
    /// with: `pool stop` may only suspend a VM whose next start would declare the same devices, and resuming with a
    /// different share set would attach VirtioFS devices the saved guest state does not know about.
    pub(crate) fn tart_run_argv(&self, worker: &str) -> Result<Vec<String>, Refusal> {
        let settings = &self.settings;
        let mut argv: Vec<String> = [
            self.tart_program().as_str(),
            "run",
            "--no-graphics",
            "--no-audio",
            "--no-clipboard",
            "--vnc-experimental",
        ]
        .map(str::to_owned)
        .to_vec();
        // Required for `tart suspend`.
        if settings.vm_suspendable {
            argv.push("--suspendable".to_owned());
        }
        // The network mode. `nat` declares nothing and is the default; the other two are alternatives rather than
        // layers - Softnet runs on top of Apple's shared vmnet bridge, and bridged mode is the one way off it.
        match settings.vm_network.as_str() {
            // Softnet confines the guest to globally routable IPv4 and its own bridge gateway: the real-agent CLIs
            // still reach the internet, the local network does not answer, and one worker cannot ARP-spoof another.
            "softnet" => argv.push("--net-softnet".to_owned()),
            // Bridged mode attaches the guest to a host interface instead of the shared vmnet bridge, whose
            // `InternetSharing` makes `mDNSResponder` hold DNS port 53 - so a VPN client that runs its own DNS proxy
            // cannot start while a worker is up.
            "bridged" => argv.push(format!(
                "--net-bridged={}",
                settings.vm_bridged_interface.as_deref().unwrap_or_default()
            )),
            _ => {}
        }
        // The worker's disk is disposable - a copy-on-write clone whose writable state is all rebuildable - so it is
        // worth trading crash durability for the fsync path. Recovery from a corrupt worker is `pool recycle`.
        if !settings.vm_root_disk_opts.is_empty() {
            argv.push(format!("--root-disk-opts={}", settings.vm_root_disk_opts));
        }
        // The `--dir` grammar is the backend's: it refuses a path it cannot spell, which is what keeps a share
        // declared at "" from reaching a VM.
        argv.extend(Tart::share_arguments(&shares(settings)?)?);
        argv.push(worker.to_owned());
        Ok(argv)
    }

    /// What a worker was suspended with, or `None` for every kind of wrong: the caller's question is "may I resume
    /// against my current declaration", and no version of "the record is damaged" should answer yes to it.
    pub(super) fn read_suspended_state(&self, worker: &str) -> Option<SuspendedState> {
        let content = std::fs::read(self.settings.suspended_state_path(worker)).ok()?;
        let state: SuspendedState = serde_json::from_slice(&content).ok()?;
        (state.schema_version == SCHEMA_VERSION && state.worker == worker).then_some(state)
    }

    /// Records the identity the controller will recognise this worker's run process by.
    ///
    /// Written atomically. A half-written receipt reads as "no run process of mine is alive", which sends the next
    /// start into a cold boot over a *running* VM and straight into the host's concurrent-VM limit.
    pub(crate) async fn write_process_identity(&self, ctx: &Ctx, worker: &str, pid: i32) -> Result<ProcessIdentity, Refusal> {
        let mut wait = Poll::start(&SystemClock, self.timings.identity_budget, self.timings.identity_backoff);
        let answers = loop {
            let answers = (
                self.runner.probe_process(ctx, pid, PsField::StartTime).await?,
                self.runner.probe_process(ctx, pid, PsField::Command).await?,
            );
            if (answers.0.is_some() && answers.1.is_some()) || !wait.pause(ctx).await {
                break answers;
            }
        };
        let (Some(process_start), Some(process_command)) = answers else {
            return Err(Refusal::new(
                "process_identity_unavailable",
                Exit::FAILURE,
                format!("cannot identify Tart process for {worker}"),
            ));
        };
        let identity = ProcessIdentity {
            schema_version: SCHEMA_VERSION,
            worker: worker.to_owned(),
            pid,
            process_start,
            process_command,
        };
        write_json_line(
            &self.settings.pid_path(worker),
            &identity,
            &format!("the Tart process for {worker}"),
        )?;
        Ok(identity)
    }

    /// Starts the long-lived `tart run` process and records the identity the controller recognizes it by.
    ///
    /// The one detached spawn in this crate: this child has to outlive the invocation that started it. A new session
    /// is what makes that true of a Ctrl-C as well - a run process in the controller's own process group would take
    /// the terminal's SIGINT with it - and it is deliberately not registered with the interrupt service: the stop
    /// path brings it down with `tart stop` and identity polling instead. It is never reaped, so a run process that
    /// died at once is still there for the identity probe and the host-limit retry to read.
    async fn spawn_run(&self, ctx: &Ctx, worker: &str, argv: &[String]) -> Result<ProcessIdentity, Refusal> {
        let log_path = self.settings.tart_log_path(worker);
        let log = OpenOptions::new()
            .create(true)
            .write(true)
            .truncate(true)
            .mode(0o600)
            .open(&log_path)
            .or_refuse("state_write_failed", Exit::FAILURE, || {
                format!("cannot open the Tart log for {worker}")
            })?;
        let log_for_stderr = log.try_clone().or_refuse("state_write_failed", Exit::FAILURE, || {
            format!("cannot share the Tart log for {worker}")
        })?;
        let program = argv.first().map(String::as_str).unwrap_or_default();
        let pid = self
            .runner
            .spawn_detached(argv, None, log, log_for_stderr)
            .or_refuse("spawn_failed", Exit::UNAVAILABLE, || {
                format!("cannot start {} for {worker}", program.rsplit('/').next().unwrap_or(program))
            })?;
        // A pid of 0 would make the failure path below signal the controller's own process group, so an
        // unrepresentable pid is refused rather than defaulted. It cannot happen on unix, where `pid_t` is an i32.
        let Ok(pid) = i32::try_from(pid) else {
            return Err(Refusal::new(
                "spawn_failed",
                Exit::SOFTWARE,
                format!("the run process for {worker} has a pid out of range: {pid}"),
            ));
        };
        match self.write_process_identity(ctx, worker, pid).await {
            Ok(identity) => Ok(identity),
            Err(refusal) => {
                // Running and unidentifiable is worse than no process at all: nothing could ever recognise it again,
                // so it would hold a VM slot until the host was rebooted. The whole session goes, because the spawn
                // made it one.
                if killpg(Pid::from_raw(pid), Signal::SIGTERM).is_err() {
                    let _ = kill(Pid::from_raw(pid), Signal::SIGTERM);
                }
                Err(refusal)
            }
        }
    }

    /// Starts the run process, waiting out the host's concurrent-VM limit rather than failing on it.
    ///
    /// Virtualization.framework caps how many VMs one host may run, and it counts a machine that is still shutting
    /// down. Since the pool grows on demand and shares the host with whatever else uses that framework - the
    /// Parallels worker included - hitting the cap mid-teardown is ordinary, and short-lived. A cap that is genuinely
    /// full still fails, just after this window instead of instantly.
    async fn spawn_retrying_the_host_limit(
        &self,
        ctx: &Ctx,
        tart: &Tart,
        worker: &str,
        argv: &[String],
    ) -> Result<ProcessIdentity, Refusal> {
        let mut window = Poll::start(
            &SystemClock,
            self.timings.host_limit_window,
            Backoff::fixed(self.timings.host_limit_retry),
        );
        let mut attempt = 0;
        loop {
            let identity = self.spawn_run(ctx, worker, argv).await?;
            // The failure is immediate: tart validates against the limit before the guest starts booting.
            let mut settle = Poll::start(&SystemClock, self.timings.settle_budget, self.timings.process_backoff);
            while tart.process_alive(ctx, Some(&identity)).await? && settle.pause(ctx).await {}
            if tart.process_alive(ctx, Some(&identity)).await? {
                return Ok(identity);
            }
            let said = std::fs::read_to_string(self.settings.tart_log_path(worker)).unwrap_or_default();
            // Answered rather than refused: the boot loop reads the log and reports what Tart said, which is a
            // better message than anything this function could build about a limit that was not hit.
            if !said.contains(HOST_VM_LIMIT_MESSAGE) || window.spent() {
                return Ok(identity);
            }
            if attempt == 0 {
                self.note(
                    worker,
                    "the host is at its concurrent-VM limit; waiting for a slot to free up".to_owned(),
                );
            }
            attempt += 1;
            // The boot loop names the interrupt; the record stays for whatever the probe could not see.
            if ctx.is_cancelled() {
                return Ok(identity);
            }
            let _ = std::fs::remove_file(self.settings.pid_path(worker));
            if !window.pause(ctx).await {
                return Ok(identity);
            }
        }
    }

    /// Makes sure the next `tart run` either resumes a guest state that matches what it is about to declare, or
    /// resumes nothing at all.
    ///
    /// Restoring against a changed declaration is not rejected by Tart: both shares ride one virtio-fs device, so
    /// dropping or repointing one changes the device's *contents* rather than the machine's topology and
    /// `restoreMachineStateFrom` succeeds. Measured on 2026-08-12 - a resume declaring one of the two shares came up
    /// without a word. The only safe move is to spend the saved state deliberately: resume with the argv that
    /// produced it (or, when nothing was recorded, with the current one, where the restore either takes or fails
    /// loudly), shut the guest down cleanly, and let the caller cold-boot.
    pub(super) async fn discard_incompatible_suspended_state(
        &self,
        ctx: &Ctx,
        tart: &Tart,
        worker: &str,
        argv: &[String],
    ) -> Result<(), Refusal> {
        if tart.vm_state(ctx, worker).await? != Some(VmState::Suspended) {
            let _ = std::fs::remove_file(self.settings.suspended_state_path(worker));
            return Ok(());
        }
        let recorded = self.read_suspended_state(worker);
        let resume_argv = match &recorded {
            Some(state) if state.run_argv == argv => return Ok(()),
            Some(state) => {
                self.note(
                    worker,
                    format!(
                        "{worker} was suspended with a different device declaration; spending that state before a \
                         cold start"
                    ),
                );
                state.run_argv.clone()
            }
            None => {
                self.note(
                    worker,
                    format!(
                        "{worker} is suspended but the controller did not record how; spending that state before \
                         a cold start"
                    ),
                );
                argv.to_vec()
            }
        };
        let identity = self.spawn_run(ctx, worker, &resume_argv).await?;
        let mut boot = Poll::start(&SystemClock, self.boot_budget(), Backoff::fixed(self.timings.boot_poll));
        while !boot.spent() && tart.process_alive(ctx, Some(&identity)).await? {
            if self.guest_answers(ctx, worker, probe_timeout(boot.left())).await || !boot.pause(ctx).await {
                break;
            }
        }
        // The resumed process is in its own session, so the interrupt did not reach it, and the `tart stop` below
        // would be signalled before it ran.
        if ctx.is_cancelled() {
            return Err(self.interrupted(worker, "start"));
        }
        if tart.process_alive(ctx, Some(&identity)).await? {
            self.runner
                .checked(
                    ctx,
                    &[tart.program(), "stop".to_owned(), worker.to_owned()],
                    &SpawnOptions::within(TART_STOP_TIMEOUT),
                )
                .await?;
            self.wait_for_exit(ctx, tart, &identity, self.timings.teardown_grace).await?;
            if ctx.is_cancelled() {
                return Err(self.interrupted(worker, "start"));
            }
        }
        let _ = std::fs::remove_file(self.settings.pid_path(worker));
        let _ = std::fs::remove_file(self.settings.suspended_state_path(worker));
        self.wait_for_vm_teardown(ctx, tart, worker).await
    }

    /// Polls until the identified process is gone, the grace runs out, or the operation is cancelled.
    async fn wait_for_exit(&self, ctx: &Ctx, tart: &Tart, identity: &ProcessIdentity, grace: Duration) -> Result<(), Refusal> {
        let mut wait = Poll::start(&SystemClock, grace, self.timings.process_backoff);
        while tart.process_alive(ctx, Some(identity)).await? && wait.pause(ctx).await {}
        Ok(())
    }

    /// Waits until Tart stops calling the VM running.
    ///
    /// Our own run process going away is not the end of the machine: Virtualization.framework is still tearing it
    /// down, and it counts against the host's concurrent-VM limit until it is gone. Starting the next one straight
    /// away fails with the host-limit message - observed on 2026-08-12 spending a suspended worker's state and
    /// immediately cold-booting it.
    async fn wait_for_vm_teardown(&self, ctx: &Ctx, tart: &Tart, worker: &str) -> Result<(), Refusal> {
        let mut wait = Poll::start(&SystemClock, self.timings.teardown_grace, self.timings.process_backoff);
        while tart.vm_state(ctx, worker).await? == Some(VmState::Running) && wait.pause(ctx).await {}
        Ok(())
    }

    // --- start -----------------------------------------------------------------------------------------------

    /// Brings a Tart worker up. A Tart start takes no worker away, so it reads no lease. A refusal of a cancelled
    /// start is the interrupt's ([`Manager::unless_interrupted`]).
    pub(super) async fn start_tart(&self, ctx: &Ctx, tart: &Tart, worker: &str) -> Result<StartState, Refusal> {
        let started = self.boot_tart(ctx, tart, worker).await;
        started.map_err(|refusal| self.unless_interrupted(ctx, worker, "start", refusal))
    }

    async fn boot_tart(&self, ctx: &Ctx, tart: &Tart, worker: &str) -> Result<StartState, Refusal> {
        tart.require_available(ctx, worker).await?;
        // The shares are arguments to the `tart run` process started below, so the host paths must be resolved before
        // it is spawned: `pool start` reaches here without a lease operation's readiness gate.
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        // **Before the clone, because a boot runs verbs of the agent it installs.** `pool start` and `pool recycle`
        // build no lane, so they used to install whatever the output base already held - and a cold clone then
        // answered a boot verb with `exited with 64: Usage:`, `EX_USAGE` from an agent older than this controller. Here
        // rather than in the two `pool` commands, so the lazy start of an unused slot is covered too. A lease-only
        // operation builds nothing: it runs no boot verb and must not spend a build under a held lock.
        (self.build_boot)(ctx.clone()).await?;
        // A run process whose VM Tart no longer lists boots nothing a new clone could use, and it reads as running, so
        // without this the start would clone, spawn nothing and wait out the boot budget.
        if let Some(identity) = tart.read_process_identity(worker)
            && tart.process_alive(ctx, Some(&identity)).await?
            && self.is_orphaned_run_process(ctx, tart, worker, &identity).await?
        {
            self.end_orphaned_run_process(ctx, tart, worker, &identity).await?;
        }
        // A slot that has never been used is cloned here rather than in a separate `pool init` step.
        self.materialize_tart_worker(ctx, tart, worker, &self.settings.golden_vm).await?;
        let argv = self.tart_run_argv(worker)?;
        self.discard_incompatible_suspended_state(ctx, tart, worker, &argv).await?;
        let mut identity = tart.read_process_identity(worker);
        let already_running = tart.process_alive(ctx, identity.as_ref()).await?;
        if !already_running {
            identity = Some(self.spawn_retrying_the_host_limit(ctx, tart, worker, &argv).await?);
            self.note(worker, format!("starting {worker} headlessly with host-side VNC diagnostics"));
        }

        let mut boot = Poll::start(&SystemClock, self.boot_budget(), Backoff::fixed(self.timings.boot_poll));
        // Whether the guest answered `tart exec` even once: the whole discriminator behind the two timeouts. See
        // [`Manager::boot_timed_out`].
        let mut answered = false;
        loop {
            // Checked first: an interrupted guest probe is no evidence about the run process either way.
            if ctx.is_cancelled() {
                return Err(self.interrupted(worker, "start"));
            }
            if !tart.process_alive(ctx, identity.as_ref()).await? {
                return Err(self.tart_exited(worker));
            }
            if self.guest_answers(ctx, worker, probe_timeout(boot.left())).await {
                answered = true;
                if self.finish_tart_start(ctx, tart, worker).await? {
                    return Ok(if already_running {
                        StartState::AlreadyRunning
                    } else {
                        StartState::Started
                    });
                }
            }
            if !boot.pause(ctx).await {
                if ctx.is_cancelled() {
                    return Err(self.interrupted(worker, "start"));
                }
                return Err(self.boot_timed_out(worker, answered));
            }
        }
    }

    /// Names which of the two boots ran out of budget: one whose guest never spoke, or one whose guest spoke and then
    /// failed a later gate.
    ///
    /// **The first is what a base image with no `tart-guest-agent` looks like, and it is a host-side refusal because
    /// it cannot be anything else.** `tart exec` *is* the guest agent, and every verb of `vm-guest-agent` is invoked
    /// through it, so a guest-side check for the agent could never run on the guest that needs it. The host says
    /// what it observed and names the requirement that explains it - and the other explanation too, because a host
    /// under enough load boots slower than any budget. It costs a healthy boot nothing: the discriminator is a
    /// boolean the poll loop already had.
    ///
    /// `boot_timeout` keeps its name for the other case, which is real on one guest only: a macOS worker whose `tart
    /// exec` answers well before its window server has an Aqua session.
    pub(super) fn boot_timed_out(&self, worker: &str, answered: bool) -> Refusal {
        let budget = self.settings.boot_timeout_seconds;
        if answered {
            return Refusal::new("boot_timeout", Exit::FAILURE, format!("timed out waiting {budget}s for {worker}"));
        }
        Refusal::new(
            "guest_agent_never_answered",
            Exit::FAILURE,
            format!(
                "{worker} ran for {budget}s and never answered a guest command, so no verb of the guest agent could \
                 be sent to it. `tart exec` is tart-guest-agent, so an image without that service preinstalled and \
                 enabled stays silent here forever; the pinned base image and the sealed macOS golden both carry \
                 it. The other explanation is a host too loaded to boot a VM in that budget, which \
                 AIR_VM_BOOT_TIMEOUT raises"
            ),
        )
    }

    /// Runs everything that needs a guest, answering false when the console is not logged in yet.
    ///
    /// The console login is the one failure that is expected during a boot rather than fatal to it: the guest
    /// answers `tart exec` well before its window server has a session, so `console_login_required` means "poll
    /// again", and every other refusal means the start failed.
    ///
    /// # The sequence, and the one rule it enforces
    ///
    /// The console login, then the TCC check, then the worker storage, then the parity layout over the two read-only
    /// shares. Each step's precondition is the step in front of it, and a step that reads a *host* path through a
    /// share must come after the step that mounts one.
    pub(super) async fn finish_tart_start(&self, ctx: &Ctx, tart: &Tart, worker: &str) -> Result<bool, Refusal> {
        let channel = self.channel(worker);
        let guest = self.guest(ctx, channel.as_ref());
        if !guest.has_console_login().await? {
            return Ok(false);
        }
        guest.require_clean_worker_tcc().await?;
        let root_disk_gb = tart.root_disk_gb(ctx, worker).await?.unwrap_or(self.settings.vm_root_disk_gb);
        // Before the parity layout, not after: the parity script creates the data directory itself, and a storage
        // step that checks for it would then never grow a fresh clone's container.
        guest.ensure_worker_storage(root_disk_gb).await?;
        // The shares this process declared are mounted by the parity provisioning, and the layout is built over
        // them here. They do *not* attach on their own at guest boot. A macOS worker stages no Node: its Node comes
        // from the sealed golden image.
        if guest.parity_broken().await? {
            guest.provision_worker(self.share_mount()).await?;
        }
        guest.ensure_ready(self).await?;
        Ok(true)
    }

    /// The refusal for a run process that died while the worker was starting, carrying the last of what it said.
    ///
    /// The tail is the whole value of this refusal: `tart run` reports an unusable share, a missing image and the
    /// host's VM limit only on its own stderr.
    pub(super) fn tart_exited(&self, worker: &str) -> Refusal {
        let tail = std::fs::read_to_string(self.settings.tart_log_path(worker))
            .map(|content| {
                let content = content.replace("\r\n", "\n");
                let lines: Vec<&str> = content.split('\n').collect();
                let start = lines.len().saturating_sub(40);
                lines[start..].join("\n")
            })
            .unwrap_or_default();
        let tail = if tail.is_empty() { String::new() } else { format!(":\n{tail}") };
        Refusal::new("tart_exited", Exit::FAILURE, format!("Tart exited while starting {worker}{tail}"))
    }

    /// A refusal from a cancelled operation is the interrupt's: a `tart stop` the interrupt signalled failed because
    /// of it, and naming that failure would send the operator after a Tart problem that is not there.
    fn unless_interrupted(&self, ctx: &Ctx, worker: &str, operation: &str, refusal: Refusal) -> Refusal {
        if ctx.is_cancelled() {
            self.interrupted(worker, operation)
        } else {
            refusal
        }
    }

    /// The refusal for a start or stop that a signal (or a cancelled parent operation) cut short.
    ///
    /// Everything that identifies the worker's run process stays on disk. The run process lives in its own session,
    /// so the interrupt did not reach it, and a stop path that could not finish waiting cannot tell a process that
    /// went from one it stopped watching. A record removed then leaves a running VM nothing recognises again: the
    /// next stop answers already-stopped and the next start boots a second one beside it.
    fn interrupted(&self, worker: &str, operation: &str) -> Refusal {
        let cause = self.runner.interrupts().received().map_or("a cancellation", |signal| signal.name());
        Refusal::new(
            "worker_interrupted",
            Exit::SOFTWARE,
            format!(
                "{cause} interrupted the {operation} of {worker}; its run process record is kept, so `pool stop \
                 {worker}` still finds it"
            ),
        )
    }

    // --- the readiness gates ---------------------------------------------------------------------------------

    /// [`Manager::require_ready`] for a Tart worker.
    ///
    /// Lazy: a pool slot is a name the controller may use, not a VM that must already exist. The first operation on
    /// a free slot clones it from the golden and boots it. The first operation on a suspended slot resumes it. The
    /// caller holds the worker's lifecycle lock, so every start and stop here is the lock-free one.
    pub(super) async fn require_tart_ready(&self, ctx: &Ctx, tart: &Tart, lease: &Lease) -> Result<(), Refusal> {
        let worker = lease.worker.as_str();
        tart.require_available(ctx, worker).await?;
        if !tart.running(ctx, worker).await? || !self.guest_answers(ctx, worker, GUEST_PROBE_TIMEOUT).await {
            return self.start_without_lifecycle_lock(ctx, worker).await.map(drop);
        }
        tart.require_worker_provenance(worker, &self.settings.golden_vm)?;
        let channel = self.channel(worker);
        let guest = self.guest(ctx, channel.as_ref());
        guest.require_console_login().await?;
        // A Tart worker's shares are arguments to the `tart run` process the controller owns, so a share-set change
        // means restarting that process. The pool is per *machine*, not per checkout, so this is the ordinary path
        // for a second working copy on the same host - which is why the stop is authorized by the caller's own
        // lease rather than refused.
        if self.share_declaration_stale(worker) {
            self.note(
                worker,
                format!("{worker} was started for a different repository; restarting it with the current shares"),
            );
            self.stop_without_lifecycle_lock(ctx, worker, Some(lease)).await?;
            return self.start_without_lifecycle_lock(ctx, worker).await.map(drop);
        }
        if guest.parity_broken().await? {
            guest.provision_worker(self.share_mount()).await?;
        }
        guest.ensure_ready(self).await
    }

    /// Whether the running worker was launched with shares that no longer describe the host.
    ///
    /// The init receipt is the record of what it was launched for. Every way of not having one - never provisioned,
    /// a receipt naming another worker, a schema this build does not speak - answers false, because the layout is
    /// then built in place and no restart is needed.
    fn share_declaration_stale(&self, worker: &str) -> bool {
        let (Ok(receipt), Ok(repo), Ok(bazel_user_root)) = (
            read_init_receipt(&self.settings, worker),
            self.settings.host_repo(),
            self.settings.host_bazel_user_root(),
        ) else {
            return false;
        };
        Path::new(&receipt.host_repo) != repo || Path::new(&receipt.host_bazel_user_root) != bazel_user_root
    }

    /// [`Manager::require_release_ready`] for a Tart worker. A stopped worker is refused, and `lease release` never
    /// reaches this for one. A lease can outlive a crashed holder. The release of such a lease on a stopped worker
    /// without a start is the whole repair.
    pub(super) async fn require_tart_release_ready(&self, ctx: &Ctx, tart: &Tart, worker: &str) -> Result<(), Refusal> {
        tart.require_available(ctx, worker).await?;
        if !tart.running(ctx, worker).await? {
            return Err(Refusal::new(
                "worker_stopped",
                Exit::FAILURE,
                format!("worker {worker} is stopped; run pool start {worker}"),
            ));
        }
        if !self.guest_answers(ctx, worker, GUEST_PROBE_TIMEOUT).await {
            return Err(self.guest_agent_unavailable(worker));
        }
        let channel = self.channel(worker);
        self.guest(ctx, channel.as_ref()).require_console_login().await
    }

    // --- stop ------------------------------------------------------------------------------------------------

    /// Stops or suspends a Tart worker. The caller passed the lease guard. A refusal of a cancelled stop is the
    /// interrupt's ([`Manager::unless_interrupted`]).
    pub(super) async fn stop_tart(&self, ctx: &Ctx, tart: &Tart, worker: &str) -> Result<StopState, Refusal> {
        let stopped = self.halt_tart(ctx, tart, worker).await;
        stopped.map_err(|refusal| self.unless_interrupted(ctx, worker, "stop", refusal))
    }

    async fn halt_tart(&self, ctx: &Ctx, tart: &Tart, worker: &str) -> Result<StopState, Refusal> {
        let Some(identity) = tart.read_process_identity(worker) else {
            return Ok(StopState::AlreadyStopped);
        };
        if !tart.process_alive(ctx, Some(&identity)).await? {
            return Ok(StopState::AlreadyStopped);
        }
        tart.require_available(ctx, worker).await?;
        // Stopping writes down the declaration this worker was started with, and that names the host paths.
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        // `tart stop` and `tart suspend` find a VM by name, so neither reaches a run process whose VM Tart no longer
        // lists; it is signalled directly.
        if self.is_orphaned_run_process(ctx, tart, worker, &identity).await? {
            self.end_orphaned_run_process(ctx, tart, worker, &identity).await?;
            return Ok(StopState::Stopped);
        }
        // Suspending keeps what the worker is expensive to rebuild: the warm UI-test daemon and the IDE it holds open
        // come back with the guest. Measured on 2026-08-12 that costs about two seconds and a ~6 GB state file, and
        // resuming takes 29 s against a 32 s cold boot - so the reason to prefer it is the warm guest.
        let suspending = self.settings.vm_suspendable;
        if suspending {
            // Written first: it is the input to the compatibility check the next start makes, and a state file with
            // no record beside it costs a whole extra boot.
            let record = SuspendedState {
                schema_version: SCHEMA_VERSION,
                worker: worker.to_owned(),
                run_argv: self.tart_run_argv(worker)?,
            };
            write_json_line(
                &self.settings.suspended_state_path(worker),
                &record,
                &format!("the suspended state of {worker}"),
            )?;
            tart.suspend(ctx, worker).await?;
        } else {
            self.runner
                .checked(
                    ctx,
                    &[tart.program(), "stop".to_owned(), worker.to_owned()],
                    &SpawnOptions::within(TART_STOP_TIMEOUT),
                )
                .await?;
        }
        // Both work by signalling the run process, so either way the controller waits on its own process going away;
        // a suspend writes gigabytes first, so it gets a far longer grace.
        let grace = if suspending {
            self.timings.suspend_grace
        } else {
            self.timings.stop_grace
        };
        self.wait_for_exit(ctx, tart, &identity, grace).await?;
        // Before the liveness check: a suspend still writing its state is alive, and an interrupted wait is not a
        // stop that timed out.
        if ctx.is_cancelled() {
            return Err(self.interrupted(worker, "stop"));
        }
        if tart.process_alive(ctx, Some(&identity)).await? {
            return Err(Refusal::new(
                "stop_timeout",
                Exit::FAILURE,
                format!("Tart process {} for {worker} remained alive after stop", identity.pid),
            ));
        }
        let _ = std::fs::remove_file(self.settings.pid_path(worker));
        if suspending && tart.vm_state(ctx, worker).await? != Some(VmState::Suspended) {
            // The signal was delivered and the process is gone, but no state was saved: a record left behind would
            // make the next start spend a state that is not there.
            let _ = std::fs::remove_file(self.settings.suspended_state_path(worker));
        }
        Ok(StopState::Stopped)
    }

    // --- the orphaned run process ----------------------------------------------------------------------------

    /// Whether a live recorded run process is a `tart run` of `worker` whose VM Tart no longer lists.
    ///
    /// The command shape is checked before `tart list`, so a process that is not a `tart run` of this worker is
    /// never signalled as an orphan, and asks Tart nothing. Only such a process can outlive its VM: `tart delete`, or
    /// a peer checkout's recycle, removed the directory while the process kept the unlinked disk.
    pub(super) async fn is_orphaned_run_process(
        &self,
        ctx: &Ctx,
        tart: &Tart,
        worker: &str,
        identity: &ProcessIdentity,
    ) -> Result<bool, Refusal> {
        if !is_tart_run_of(identity, worker) {
            return Ok(false);
        }
        Ok(!tart.exists(ctx, worker).await?)
    }

    /// The pid of a Tart worker's recorded run process when that process is a `tart run` of the worker and Tart no
    /// longer lists the VM, else `None`. The caller has seen the process alive.
    ///
    /// No run can be in flight in such a worker: `tart exec` reaches a guest only through its VM, so nothing can
    /// start, observe or collect a run in it. `lease release` frees it without the guest check for that reason.
    pub(crate) async fn missing_vm_run_process(&self, ctx: &Ctx, worker: &str) -> Result<Option<i32>, Refusal> {
        let Some(tart) = self.tart() else {
            return Ok(None);
        };
        let Some(identity) = tart.read_process_identity(worker) else {
            return Ok(None);
        };
        if !is_tart_run_of(&identity, worker) {
            return Ok(None);
        }
        tart.require_available(ctx, worker).await?;
        Ok((!tart.exists(ctx, worker).await?).then_some(identity.pid))
    }

    /// Ends a run process whose VM Tart no longer lists, the way `tart stop` ends one it can find: an interrupt to
    /// its process group, then a kill once the stop grace is spent.
    async fn end_orphaned_run_process(&self, ctx: &Ctx, tart: &Tart, worker: &str, identity: &ProcessIdentity) -> Result<(), Refusal> {
        self.note(
            worker,
            format!(
                "Tart no longer lists {worker}, but its run process {} is alive; ending it",
                identity.pid
            ),
        );
        let signal = |signal: Signal| {
            let pid = Pid::from_raw(identity.pid);
            if killpg(pid, signal).is_err() {
                let _ = kill(pid, signal);
            }
        };
        signal(Signal::SIGINT);
        self.wait_for_exit(ctx, tart, identity, self.timings.stop_grace).await?;
        if !ctx.is_cancelled() && tart.process_alive(ctx, Some(identity)).await? {
            signal(Signal::SIGKILL);
            self.wait_for_exit(ctx, tart, identity, self.timings.teardown_grace).await?;
        }
        if ctx.is_cancelled() {
            return Err(self.interrupted(worker, "stop"));
        }
        if tart.process_alive(ctx, Some(identity)).await? {
            return Err(Refusal::new(
                "stop_timeout",
                Exit::FAILURE,
                format!(
                    "Tart process {} for {worker} remained alive after SIGKILL; its VM is no longer listed",
                    identity.pid
                ),
            ));
        }
        let _ = std::fs::remove_file(self.settings.pid_path(worker));
        let _ = std::fs::remove_file(self.settings.suspended_state_path(worker));
        Ok(())
    }

    // --- pool init -------------------------------------------------------------------------------------------

    /// Clones the sealed golden into every slot and audits it. An empty `golden` is the configured golden.
    pub(super) async fn pool_init_tart(&self, ctx: &Ctx, tart: &Tart, golden: Option<&str>) -> Result<Outcome, Refusal> {
        let golden = golden.filter(|golden| !golden.is_empty()).unwrap_or(&self.settings.golden_vm);
        self.pool_init_macos(ctx, tart, golden).await
    }

    async fn pool_init_macos(&self, ctx: &Ctx, tart: &Tart, golden: &str) -> Result<Outcome, Refusal> {
        validate_name(golden, "golden VM name")?;
        tart.require_available(ctx, "").await?;
        let seal = tart.require_sealed_golden(golden)?;
        if !tart.exists(ctx, golden).await? {
            return Err(Refusal::new(
                "golden_vm_missing",
                Exit::FAILURE,
                format!("golden Tart VM {golden} does not exist locally"),
            ));
        }
        self.prepare_runtime_dirs()?;
        // Every existing worker is vetted before any of them is touched: a run that resized one worker and then
        // refused the next would leave the pool half-changed.
        for worker in &self.settings.workers {
            if tart.exists(ctx, worker).await? {
                tart.require_worker_provenance(worker, golden)?;
            }
        }
        let mut created = Vec::new();
        for worker in &self.settings.workers {
            if self.materialize_tart_worker(ctx, tart, worker, golden).await? {
                created.push(worker.clone());
            }
        }
        if tart.require_sealed_golden(golden)? != seal {
            return Err(Refusal::new(
                "golden_seal_changed",
                Exit::DATA_ERR,
                format!("sealed-golden receipt changed while initializing workers from {golden}"),
            ));
        }
        let settings = &self.settings;
        Ok(Outcome {
            data: json!({
                "golden": golden,
                "workers": settings.workers,
                "created": created,
                "rootDiskGb": settings.vm_root_disk_gb,
            }),
            text: format!("workers={}\nroot_disk_gb={}", settings.workers.join(","), settings.vm_root_disk_gb),
        })
    }

    /// Brings one pool slot into existence as a VM and re-applies its sizing, answering whether it had to be cloned.
    ///
    /// A slot is only a name: `pool init` materializes the whole pool up front, but a lease acquisition or a `pool
    /// start` on a slot that has never been used materializes just that one, which is what makes the pool size a
    /// knob. The seal is re-read for every clone so a golden that changed under us cannot be the source of half a
    /// pool.
    pub(super) async fn materialize_tart_worker(&self, ctx: &Ctx, tart: &Tart, worker: &str, golden: &str) -> Result<bool, Refusal> {
        let settings = &self.settings;
        let exists = tart.exists(ctx, worker).await?;
        // A worker comes from a locally sealed golden whose clone inputs are recorded and re-checked.
        if exists {
            tart.require_worker_provenance(worker, golden)?;
        }
        let seal = tart.require_sealed_golden(golden)?;
        let program = tart.program();
        if !exists {
            self.note(worker, format!("cloning {golden} -> {worker}"));
            // TART_NO_AUTO_PRUNE keeps the clone from evicting another worker's image to make room for this one.
            self.runner
                .with_overrides(&[("TART_NO_AUTO_PRUNE", "1")])
                .checked(
                    ctx,
                    &words([&program, "clone", golden, worker]),
                    &SpawnOptions::within(TART_CLONE_TIMEOUT),
                )
                .await?;
            self.runner
                .checked(
                    ctx,
                    &words([&program, "set", worker, "--random-mac", "--random-serial"]),
                    &SpawnOptions::within(TART_SET_TIMEOUT),
                )
                .await?;
        }
        // The disk is the one setting that cannot be re-applied freely, which is why it is the hypervisor's own
        // grow-only operation rather than another flag on the `set` below.
        tart.grow_root_disk(ctx, worker, settings.vm_root_disk_gb).await?;
        let (cpu, memory) = (settings.vm_cpu.to_string(), settings.vm_memory_mib.to_string());
        self.runner
            .checked(
                ctx,
                &words([
                    &program,
                    "set",
                    worker,
                    "--cpu",
                    &cpu,
                    "--memory",
                    &memory,
                    "--display",
                    &settings.vm_display,
                    "--no-display-refit",
                ]),
                &SpawnOptions::within(TART_SET_TIMEOUT),
            )
            .await?;
        tart.write_worker_provenance(worker, &seal)?;
        Ok(!exists)
    }

    // --- pool gc ---------------------------------------------------------------------------------------------

    /// Deletes the VMs behind idle pool slots, so the pool's size can go back down.
    ///
    /// A worker is a copy-on-write clone that grows towards its root-disk size as it is used, so a pool that only
    /// ever grows eventually fills the host. Only a slot that exists, holds no lease and has no live run process is
    /// removed.
    pub(super) async fn pool_gc_tart(&self, ctx: &Ctx, tart: &Tart) -> Result<Outcome, Refusal> {
        tart.require_available(ctx, "").await?;
        // Spending a suspended worker's state re-declares its shares, so the host paths must be resolved.
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        self.prepare_runtime_dirs()?;
        let mut removed = Vec::new();
        let mut kept = BTreeMap::new();
        for worker in &self.settings.workers {
            let outcome = self
                .with_lifecycle_lock(ctx, worker, "pool-gc", self.gc_one_worker(ctx, tart, worker))
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

    async fn gc_one_worker(&self, ctx: &Ctx, tart: &Tart, worker: &str) -> Result<&'static str, Refusal> {
        if read_lease(&self.settings.lease_path(worker))?.is_some() {
            return Ok("leased");
        }
        if !tart.exists(ctx, worker).await? {
            return Ok("absent");
        }
        if tart.running(ctx, worker).await? {
            return Ok("running");
        }
        self.delete_tart_worker(ctx, tart, worker).await?;
        Ok("removed")
    }

    /// The unmaking of a slot that `pool recycle` runs: the stop, then the VM and its host state. The caller passed
    /// the lease guard, so the stop authorizes no lease.
    pub(super) async fn unmake_tart_worker(&self, ctx: &Ctx, tart: &Tart, worker: &str) -> Result<(), Refusal> {
        self.stop_without_lifecycle_lock(ctx, worker, None).await?;
        self.delete_tart_worker(ctx, tart, worker).await
    }

    /// Unmakes one pool slot: the VM behind it, and the host state that described that VM.
    ///
    /// One spelling of "this slot is gone", shared by `pool gc` - which stops there - and `pool recycle`, which clones
    /// the slot again afterwards. A suspended worker is spent first, deliberately, even though the VM is about to go:
    /// `tart delete` under a saved guest state leaves that state's record behind for a slot that no longer exists.
    async fn delete_tart_worker(&self, ctx: &Ctx, tart: &Tart, worker: &str) -> Result<(), Refusal> {
        if tart.exists(ctx, worker).await? {
            if tart.vm_state(ctx, worker).await? == Some(VmState::Suspended) {
                let argv = self.tart_run_argv(worker)?;
                self.discard_incompatible_suspended_state(ctx, tart, worker, &argv).await?;
            }
            self.runner
                .checked(
                    ctx,
                    &words([&tart.program(), "delete", worker]),
                    &SpawnOptions::within(TART_DELETE_TIMEOUT),
                )
                .await?;
        }
        self.clear_worker_state(worker)
    }
}

/// Whether a recorded command is the shape [`Manager::tart_run_argv`] writes for `worker`: a `tart` executable, then
/// `run`, and the worker last. A `/bin/sh` in front is allowed, which is how a script standing in for `tart` shows.
fn is_tart_run_of(identity: &ProcessIdentity, worker: &str) -> bool {
    let tokens: Vec<&str> = identity.process_command.split_whitespace().collect();
    tokens.last() == Some(&worker)
        && tokens
            .windows(2)
            .any(|pair| pair[1] == "run" && pair[0].rsplit('/').next() == Some("tart"))
}
