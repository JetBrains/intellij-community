//! The Parallels backend: `prlctl` and nothing else.
//!
//! What it must do to its own VM - read its state, wait for its guest agent and its Aqua session, declare the
//! read-only shared folders, power-cycle it when the share set changes, and hand it back suspended. The guest side
//! of a run is backend-neutral and is not here.
//!
//! # A worker here is a VM the controller does not own
//!
//! The mirror of Tart. There the machine is a run process the controller spawned and can identify; here it is a
//! VM an operator made, whose disk, CPU count, display and *login configuration* are not this controller's to set:
//!
//! - liveness is `prlctl status` and nothing better, because there is no process of ours to recognise;
//! - a share change is a mutation of stored configuration that only takes at boot, so it costs a power cycle - and
//!   a power cycle costs the Aqua session, which is why [`Parallels::require_survives_reboot`] refuses one on a
//!   guest with no automatic login rather than stranding it at a login window;
//! - there is no sealed golden, no provenance receipt, and no disk to grow, because the VM was not made here and
//!   must not be rebuilt here.

use std::collections::BTreeMap;
use std::fmt;
use std::path::{Component, Path, PathBuf};
use std::sync::{Arc, LazyLock};
use std::time::Duration;

use avl_base::{Backend, Config, Exit, Refusal, SystemClock};
use regex::Regex;
use serde::de::{Deserializer, MapAccess, Visitor};
use serde::{Deserialize, Serialize};

use avl_host_sys::share::SharedFolder;
use avl_host_sys::{Backoff, Ctx, Poll, PollClock, ProbeExit, ProbeOutput, Runner, SpawnOptions, probe_unanswered};

use crate::worker::hypervisor::unsupported;

#[cfg(test)]
mod tests;

/// How long the wait loops sleep between probes. Two seconds, matching what operators are used to.
const POLL_INTERVAL: Duration = Duration::from_secs(2);

/// The tighter poll while waiting for a `prlctl suspend` that already returned zero: the state file is being
/// written; this is not a boot.
const SUSPEND_SETTLE_INTERVAL: Duration = Duration::from_millis(500);

/// The timeout of a `prlctl` query: `list`, `status` and `--version` ask the Parallels service and answer at once.
const PRLCTL_QUERY_TIMEOUT: Duration = Duration::from_mins(1);

/// The timeout of a `prlctl set` of the shared folders. It changes the configuration of a VM that is stopped.
const PRLCTL_SET_TIMEOUT: Duration = Duration::from_mins(2);

/// The timeout of `prlctl start`. It boots or resumes the VM, and the controller then polls the guest under the boot
/// budget.
pub(crate) const PRLCTL_START_TIMEOUT: Duration = Duration::from_mins(10);

/// The timeout of `prlctl suspend`. It writes the memory of the guest to disk before it returns.
const PRLCTL_SUSPEND_TIMEOUT: Duration = Duration::from_mins(10);

/// The timeout of `prlctl stop`. It shuts the guest down before it returns.
const PRLCTL_STOP_TIMEOUT: Duration = Duration::from_mins(5);

/// The timeout of one probe through `prlctl exec`: a `/usr/bin/true` or a `who` that a guest which answers runs at
/// once. A wait counts its budget on its poll clock, not on the clock of the probe, so a probe in flight carries a wait
/// past its budget by this timeout at most.
const PRLCTL_EXEC_PROBE_TIMEOUT: Duration = Duration::from_secs(30);

/// The timeout of one read of the login settings through `prlctl exec`: a `defaults read` or a `test -f`.
const PRLCTL_EXEC_READ_TIMEOUT: Duration = Duration::from_mins(1);

/// A VM state `prlctl list -j` reports. The three this controller acts on are variants, because the only decisions
/// here are "is there a saved state" and "is it off"; every other state keeps `prlctl`'s own word, because `vm
/// status` prints it verbatim and "paused" or "resuming" is what an agent reading that row needs to see.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum VmState {
    Running,
    Stopped,
    Suspended,
    Other(String),
}

impl VmState {
    /// `prlctl`'s spelling of the state.
    pub(crate) fn as_str(&self) -> &str {
        match self {
            Self::Running => "running",
            Self::Stopped => "stopped",
            Self::Suspended => "suspended",
            Self::Other(raw) => raw,
        }
    }
}

impl From<String> for VmState {
    fn from(raw: String) -> Self {
        match raw.as_str() {
            "running" => Self::Running,
            "stopped" => Self::Stopped,
            "suspended" => Self::Suspended,
            _ => Self::Other(raw),
        }
    }
}

impl<'de> Deserialize<'de> for VmState {
    fn deserialize<D: Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
        String::deserialize(deserializer).map(Self::from)
    }
}

impl Serialize for VmState {
    fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        serializer.serialize_str(self.as_str())
    }
}

impl fmt::Display for VmState {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

/// The subset of `prlctl list -a -i -j` this controller reads.
///
/// Every field is optional because each is a fact the listing may simply not carry, and each absence means
/// something different from a default: no `Type` is not "some other hypervisor type", it is a listing this
/// controller does not understand, and refusing is the only safe answer.
#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
pub(crate) struct VmInfo {
    #[serde(rename = "ID")]
    pub id: Option<String>,
    #[serde(rename = "Name")]
    pub name: Option<String>,
    #[serde(rename = "Type")]
    pub kind: Option<String>,
    #[serde(rename = "State")]
    pub state: Option<VmState>,
    #[serde(rename = "OS")]
    pub os: Option<String>,
    /// `prlctl`'s own spelling, spaces included.
    #[serde(rename = "Host Shared Folders")]
    pub shared_folders: Option<SharedFolders>,
}

impl VmInfo {
    /// One share as the VM has it, or `None` when the VM has no such share.
    pub(crate) fn shared_folder(&self, name: &str) -> Option<&SharedFolderEntry> {
        self.shared_folders.as_ref()?.folders.get(name)
    }

    /// The shared-folder master switch, an absent one read as off.
    pub(crate) fn automount_enabled(&self) -> bool {
        self.shared_folders.as_ref().is_some_and(|folders| folders.enabled == Some(true))
    }
}

/// One share as the VM currently has it.
#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
#[serde(default)]
pub(crate) struct SharedFolderEntry {
    pub enabled: Option<bool>,
    pub path: Option<String>,
    pub mode: Option<String>,
}

/// The shared-folder object, which mixes one flag in with the shares themselves.
///
/// `prlctl` puts the master switch at `"enabled"` *beside* the per-share objects, so a share genuinely named
/// `enabled` would be indistinguishable from the switch. That is the hypervisor's shape; the controller's own share
/// names are validated names and neither is that word.
///
/// An entry that is JSON `null` is no share at all: that is the branch that decides `--shf-host-add` versus
/// `--shf-host-set`, and getting it backwards makes `prlctl` refuse the whole call. An entry that is present but not
/// an object *is* a share, with nothing known about it, so it is re-set rather than added.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct SharedFolders {
    /// The master switch, `None` when the listing did not carry it as a boolean.
    pub enabled: Option<bool>,
    pub folders: BTreeMap<String, SharedFolderEntry>,
}

impl<'de> Deserialize<'de> for SharedFolders {
    fn deserialize<D: Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
        struct FoldersVisitor;

        impl<'de> Visitor<'de> for FoldersVisitor {
            type Value = SharedFolders;

            fn expecting(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
                formatter.write_str("the shared-folder object")
            }

            fn visit_map<A: MapAccess<'de>>(self, mut map: A) -> Result<SharedFolders, A::Error> {
                let mut folders = SharedFolders::default();
                while let Some((name, value)) = map.next_entry::<String, serde_json::Value>()? {
                    if name == "enabled" {
                        folders.enabled = value.as_bool();
                        continue;
                    }
                    if value.is_null() {
                        continue;
                    }
                    let entry = serde_json::from_value(value).unwrap_or_default();
                    folders.folders.insert(name, entry);
                }
                Ok(folders)
            }
        }

        deserializer.deserialize_map(FoldersVisitor)
    }
}

/// Whether a share the VM currently has is the one the controller wants.
///
/// Four conditions, and each one has been wrong in practice: a share that exists but is disabled, one whose mode
/// drifted to `rw`, one with no path at all, and one whose path is a different directory. The VM's side is resolved
/// first because `prlctl` reports back whatever it was given, and a relative path that names the right directory is
/// still the right directory.
pub(crate) fn shared_folder_matches(existing: Option<&SharedFolderEntry>, share: &SharedFolder) -> bool {
    let Some(existing) = existing else {
        return false;
    };
    existing.enabled == Some(true)
        && existing.mode.as_deref() == Some(share.mode)
        && existing
            .path
            .as_deref()
            .is_some_and(|path| !path.is_empty() && resolved_path(Path::new(path)) == share.path)
}

/// A host path made absolute against the working directory with `.` and `..` collapsed lexically, for comparing a
/// path a hypervisor reported against one the controller declared: a hypervisor reports a share's path in whatever
/// form it was given. A path that cannot be made absolute is answered as it is; the comparison it feeds simply
/// does not match.
fn resolved_path(path: &Path) -> PathBuf {
    let Ok(absolute) = std::path::absolute(path) else {
        return path.to_owned();
    };
    let mut resolved = PathBuf::new();
    for component in absolute.components() {
        match component {
            Component::ParentDir => {
                // `..` at the root is the root, as the kernel has it.
                if resolved.parent().is_some() {
                    resolved.pop();
                }
            }
            Component::CurDir => {}
            other => resolved.push(other),
        }
    }
    resolved
}

/// The Parallels backend, one of the two a [`Machine`](crate::worker::hypervisor::Machine) is.
pub(crate) struct Parallels {
    settings: Arc<Config>,
    runner: Runner,
    /// What the wait loops read the time from and sleep on. Every loop is bounded only by the boot timeout, so a
    /// test over the fake `prlctl` polls on a fake clock rather than spend the operator's minutes.
    clock: Arc<dyn PollClock>,
}

impl Parallels {
    pub(crate) fn new(settings: Arc<Config>, runner: Runner) -> Self {
        Self {
            settings,
            runner,
            clock: Arc::new(SystemClock),
        }
    }

    /// The same backend polling on `clock`: for a suite over a call-driven fake, and for nothing else.
    #[cfg(test)]
    #[must_use]
    pub(crate) fn with_clock(mut self, clock: Arc<dyn PollClock>) -> Self {
        self.clock = clock;
        self
    }

    fn prlctl(&self, arguments: &[&str]) -> Vec<String> {
        std::iter::once(self.settings.parallels.as_str())
            .chain(arguments.iter().copied())
            .map(str::to_owned)
            .collect()
    }

    fn guest_line(&self, ctx: &Ctx, worker: &str, argv: &[&str]) -> Vec<String> {
        let argv: Vec<String> = argv.iter().map(|word| (*word).to_owned()).collect();
        self.guest_argv(ctx, worker, &argv)
    }

    // --- what prlctl says about the VM ----------------------------------------------------------------------

    /// Everything `prlctl` will say about one VM.
    ///
    /// Two refusals with two exit codes, because they are two different problems for whoever is reading. A failing
    /// `prlctl list` means the VM is not registered - the operator has not made it, or named it something else -
    /// which is [`Exit::UNAVAILABLE`], what a caller retries or reconfigures against. Output that is not one VM's
    /// worth of JSON is [`Exit::SOFTWARE`]: `prlctl` answered something this controller does not speak.
    pub(crate) async fn info(&self, ctx: &Ctx, worker: &str) -> Result<VmInfo, Refusal> {
        let argv = self.prlctl(&["list", "-a", "-i", "-j", worker]);
        let captured = self.runner.capture(ctx, &argv, &SpawnOptions::within(PRLCTL_QUERY_TIMEOUT)).await?;
        if matches!(captured.probe_exit(), ProbeExit::CannotRun | ProbeExit::Killed) {
            return Err(probe_unanswered(&argv, &captured, ProbeOutput::Quoted));
        }
        if captured.exit_code != 0 {
            return Err(Refusal::new(
                "parallels_vm_missing",
                Exit::UNAVAILABLE,
                format!("Parallels VM {worker} is not registered"),
            ));
        }
        let protocol = |reason: String| {
            Refusal::new(
                "parallels_protocol",
                Exit::SOFTWARE,
                format!("could not parse Parallels VM information: {reason}"),
            )
        };
        let values: Vec<VmInfo> = serde_json::from_str(&captured.stdout).map_err(|error| protocol(error.to_string()))?;
        let count = values.len();
        let mut values = values.into_iter();
        match (values.next(), count) {
            (Some(info), 1) => Ok(info),
            _ => Err(protocol(format!("expected one VM, got {count}"))),
        }
    }

    /// [`Parallels::require_available`] that also hands back the listing it already read: the listing is not
    /// cheap, and every caller of the gate needs it next (the share reconciliation wants the folders, `pool init`
    /// the state to restore).
    ///
    /// The shape check is not defensive: every macOS-guest path here assumes Apple Virtualization - the Aqua
    /// session, `launchctl asuser`, the autologin guard before a power cycle, TCC. A Parallels-hosted Linux or
    /// Windows guest would fail far downstream with a message about a mount or a session, so it is refused at the
    /// gate with a message about the VM.
    pub(crate) async fn require_info(&self, ctx: &Ctx, worker: &str) -> Result<VmInfo, Refusal> {
        let argv = self.prlctl(&["--version"]);
        let version = self.runner.capture(ctx, &argv, &SpawnOptions::within(PRLCTL_QUERY_TIMEOUT)).await?;
        // A tool that cannot run is a missing tool here, as a `prlctl` that is not installed.
        if version.probe_exit() == ProbeExit::Killed {
            return Err(probe_unanswered(&argv, &version, ProbeOutput::Quoted));
        }
        if version.exit_code != 0 {
            return Err(Refusal::new(
                "parallels_missing",
                Exit::UNAVAILABLE,
                "Parallels Desktop is not installed",
            ));
        }
        let info = self.info(ctx, worker).await?;
        if info.kind.as_deref() != Some("APPLE_VZ_VM") || info.os.as_deref() != Some("macosx") {
            return Err(Refusal::new(
                "parallels_vm_incompatible",
                Exit::DATA_ERR,
                format!("{worker} is not an Apple Virtualization macOS VM"),
            ));
        }
        Ok(info)
    }

    /// Whether a trivial command runs inside the guest within `timeout`. Quietly: guest output can echo the UI-test
    /// bridge token, so it must not travel in a refusal, even one about to be discarded.
    async fn guest_answers(&self, ctx: &Ctx, worker: &str, timeout: Duration) -> bool {
        self.runner
            .checked_quietly(
                ctx,
                &self.guest_line(ctx, worker, &["/usr/bin/true"]),
                &SpawnOptions::within(timeout),
            )
            .await
            .is_ok()
    }

    // --- shares ----------------------------------------------------------------------------------------------

    /// Writes the share set into the VM's configuration.
    ///
    /// `--shf-host-set` for a share that exists and `--shf-host-add` for one that does not, and the two are not
    /// interchangeable: `prlctl` refuses an add for a name it already has, and a set for one it does not. The
    /// master switch and automount are applied unconditionally, because a share set that is correct while sharing
    /// itself is off is the one drift that looks right in a listing.
    async fn apply_shares(&self, ctx: &Ctx, worker: &str, info: &VmInfo, shares: &[SharedFolder]) -> Result<(), Refusal> {
        for share in shares {
            let existing = info.shared_folder(&share.name);
            if shared_folder_matches(existing, share) {
                continue;
            }
            let verb = if existing.is_some() { "--shf-host-set" } else { "--shf-host-add" };
            let path = share.path.to_string_lossy();
            self.runner
                .checked(
                    ctx,
                    &self.prlctl(&["set", worker, verb, &share.name, "--path", &path, "--mode", share.mode, "--enable"]),
                    &SpawnOptions::within(PRLCTL_SET_TIMEOUT),
                )
                .await?;
        }
        self.runner
            .checked(
                ctx,
                &self.prlctl(&["set", worker, "--shf-host", "on", "--shf-host-automount", "on"]),
                &SpawnOptions::within(PRLCTL_SET_TIMEOUT),
            )
            .await?;
        Ok(())
    }

    /// Refuses unless the VM already has exactly the shares the controller declared.
    ///
    /// The read-only check for the paths that must not reconfigure anything - a run, a `status` - so a worker whose
    /// shares drifted is reported as such instead of quietly reading the wrong checkout. The message names the
    /// repair, because the repair is automatic and the caller's next question is what to run.
    #[expect(
        clippy::unused_self,
        reason = "a question of the backend, asked on it like its siblings; it reads only the VM info it gets"
    )]
    pub(crate) fn require_shares_configured(&self, worker: &str, info: &VmInfo, shares: &[SharedFolder]) -> Result<(), Refusal> {
        match shares
            .iter()
            .find(|share| !shared_folder_matches(info.shared_folder(&share.name), share))
        {
            None => Ok(()),
            Some(share) => Err(Refusal::new(
                "parallels_share_mismatch",
                Exit::DATA_ERR,
                format!(
                    "the read-only {} shared folder of {worker} does not match its host directory; provisioning \
                     reconfigures it automatically",
                    share.name
                ),
            )),
        }
    }

    // --- waiting ---------------------------------------------------------------------------------------------

    /// Waits until `prlctl` reports the VM in `state`.
    pub(crate) async fn wait_for_state(&self, ctx: &Ctx, worker: &str, state: VmState) -> Result<(), Refusal> {
        let message = format!(
            "timed out waiting {}s for {worker} to become {state}",
            self.settings.boot_timeout_seconds
        );
        let state = &state;
        self.until(ctx, "parallels_state_timeout", message, || async move {
            Ok(self.info(ctx, worker).await?.state.as_ref() == Some(state))
        })
        .await
    }

    /// Waits until a command runs inside the guest: the first thing that becomes true after a start, long before a
    /// login session, because `prlctl exec` works from the guest agent, which comes up with the OS.
    pub(crate) async fn wait_for_guest_execution(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        let message = format!("timed out waiting {}s for {worker}", self.settings.boot_timeout_seconds);
        self.until(ctx, "boot_timeout", message, || async move {
            Ok(self.guest_answers(ctx, worker, PRLCTL_EXEC_PROBE_TIMEOUT).await)
        })
        .await
    }

    /// Waits until the guest has an Aqua session, and says what to do when it never does.
    ///
    /// The refusal is long on purpose: this is the one failure on this backend that a human has to fix at the VM's
    /// own window. Telling the operator to enable automatic login and disable sleep is the difference between a
    /// one-off manual unlock and a worker that needs no human next time.
    pub(crate) async fn wait_for_console_login(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        let message = format!(
            "worker {worker} reached no Aqua session; log in as {} at its Parallels window, or configure automatic \
             login and disable sleep/lock so a boot needs no human",
            self.settings.vm_user
        );
        self.until(ctx, "console_login_required", message, || async move {
            if !self.guest_answers(ctx, worker, PRLCTL_EXEC_PROBE_TIMEOUT).await {
                return Ok(false);
            }
            let who = self
                .runner
                .capture(
                    ctx,
                    &self.guest_line(ctx, worker, &["/usr/bin/who"]),
                    &SpawnOptions::within(PRLCTL_EXEC_PROBE_TIMEOUT),
                )
                .await;
            Ok(who.is_ok_and(|who| who.exit_code == 0 && has_console_session(&who.stdout)))
        })
        .await
    }

    /// Polls `ready` until it answers true, the boot budget runs out, or the operation is cancelled.
    ///
    /// The timeout code is the caller's: `boot_timeout` and `console_login_required` mean different things to
    /// whoever reads the envelope. The refusals keep exit 1 rather than the temporary-failure status a timeout
    /// arguably deserves: these codes are already in callers' hands, and changing their status is a contract change.
    async fn until<F>(&self, ctx: &Ctx, code: &'static str, message: String, mut ready: impl FnMut() -> F) -> Result<(), Refusal>
    where
        F: Future<Output = Result<bool, Refusal>>,
    {
        let mut wait = self.boot_wait(POLL_INTERVAL);
        loop {
            if ready().await? {
                return Ok(());
            }
            if !wait.pause(ctx).await {
                return Err(Refusal::new(code, Exit::FAILURE, message));
            }
        }
    }

    /// A wait of the boot budget on the poll clock, with `pause` between two probes. A cancelled wait ends as a
    /// spent one: the refusal keeps the code of the wait.
    fn boot_wait(&self, pause: Duration) -> Poll<'_> {
        let budget = Duration::from_secs(u64::from(self.settings.boot_timeout_seconds));
        Poll::start(self.clock.as_ref(), budget, Backoff::fixed(pause))
    }

    // --- suspend ---------------------------------------------------------------------------------------------

    /// Leaves the VM holding its guest state, starting it first if that is the only way to get there.
    ///
    /// `start_if_stopped` permits starting a stopped VM in order to suspend it: `pool init` hands a VM back in the
    /// state it found it, and `pool stop` passes false because a stopped worker is already the answer.
    ///
    /// Each branch of the loop is a different situation:
    ///
    /// - already suspended: nothing to do, the common case on a second call;
    /// - stopped: there is no state to save, so `start_if_stopped` decides between booting it to suspend it and
    ///   refusing. It is tried exactly once - a VM that keeps returning to stopped is broken, and looping on it
    ///   would spend the whole boot budget discovering that;
    /// - running and answering: `prlctl suspend`, then a tighter poll while gigabytes of state are written.
    ///
    /// A `prlctl suspend` that fails is not fatal: it is what a VM still finishing its boot answers, and the next
    /// pass through the loop is the retry.
    pub(crate) async fn suspend(&self, ctx: &Ctx, worker: &str, start_if_stopped: bool) -> Result<(), Refusal> {
        let mut wait = self.boot_wait(POLL_INTERVAL);
        let timed_out = || {
            Refusal::new(
                "suspend_timeout",
                Exit::FAILURE,
                format!("timed out restoring {worker} to suspended state"),
            )
        };
        let mut restarted = false;
        loop {
            match self.info(ctx, worker).await?.state {
                Some(VmState::Suspended) => return Ok(()),
                Some(VmState::Stopped) => {
                    if !start_if_stopped || restarted {
                        return Err(Refusal::new(
                            "suspend_failed",
                            Exit::FAILURE,
                            format!("Parallels VM {worker} stopped before it could be suspended"),
                        ));
                    }
                    self.runner
                        .checked(ctx, &self.prlctl(&["start", worker]), &SpawnOptions::within(PRLCTL_START_TIMEOUT))
                        .await?;
                    restarted = true;
                }
                _ if self.guest_answers(ctx, worker, PRLCTL_EXEC_PROBE_TIMEOUT).await => {
                    let suspended = self
                        .runner
                        .capture(
                            ctx,
                            &self.prlctl(&["suspend", worker]),
                            &SpawnOptions::within(PRLCTL_SUSPEND_TIMEOUT),
                        )
                        .await?;
                    if suspended.exit_code == 0 {
                        // The tighter pause of the settle, on what is left of the same budget.
                        let mut settle = Poll::start(self.clock.as_ref(), wait.left(), Backoff::fixed(SUSPEND_SETTLE_INTERVAL));
                        loop {
                            if self.info(ctx, worker).await?.state == Some(VmState::Suspended) {
                                return Ok(());
                            }
                            if !settle.pause(ctx).await {
                                return Err(timed_out());
                            }
                        }
                    }
                }
                _ => {}
            }
            if !wait.pause(ctx).await {
                return Err(timed_out());
            }
        }
    }

    /// Refuses a power cycle that the guest could not come back from unattended.
    ///
    /// Checked *before* the VM is stopped, and that ordering is the whole value: the alternative is discarding a
    /// working Aqua session for one nobody can restore. Both halves are required - `autoLoginUser` names the
    /// account and `/etc/kcpassword` holds the credential macOS needs to use it, and a guest with the first and not
    /// the second stops at the login window exactly as if neither were set.
    pub(crate) async fn require_survives_reboot(&self, ctx: &Ctx, worker: &str, reason: &str) -> Result<(), Refusal> {
        // A guest read that gave no answer says nothing about the login, so it is no reason to refuse the power cycle
        // either way. `defaults read` answers on stdout, and `test -f` by its exit alone. The guest output stays out of
        // the refusal.
        let read = |argv: Vec<String>, answers_on_stdout: bool| async move {
            let captured = self
                .runner
                .capture(ctx, &argv, &SpawnOptions::within(PRLCTL_EXEC_READ_TIMEOUT))
                .await?;
            match captured.probe_exit() {
                ProbeExit::Answered if answers_on_stdout && captured.stdout.trim().is_empty() => {
                    Err(probe_unanswered(&argv, &captured, ProbeOutput::Withheld))
                }
                ProbeExit::Answered | ProbeExit::Negative => Ok(captured),
                ProbeExit::CannotRun | ProbeExit::Killed => Err(probe_unanswered(&argv, &captured, ProbeOutput::Withheld)),
            }
        };
        let auto_login_user = read(
            self.guest_line(
                ctx,
                worker,
                &[
                    "/usr/bin/defaults",
                    "read",
                    "/Library/Preferences/com.apple.loginwindow",
                    "autoLoginUser",
                ],
            ),
            true,
        )
        .await?;
        if auto_login_user.exit_code == 0 && auto_login_user.stdout.trim() == self.settings.vm_user {
            let password = read(self.guest_line(ctx, worker, &["/bin/test", "-f", "/etc/kcpassword"]), false).await?;
            if password.exit_code == 0 {
                return Ok(());
            }
        }
        Err(Refusal::new(
            "parallels_reboot_needs_autologin",
            Exit::DATA_ERR,
            format!(
                "{reason} requires a power cycle, but {worker} has no automatic login for {user}: it would stop at \
                 the login window. Enable automatic login in the guest (System Settings > Users & Groups > \
                 Automatic login), then run this command again.",
                user = self.settings.vm_user
            ),
        ))
    }
}

// --- what the machine asks of the backend ------------------------------------------------------------------

impl Parallels {
    /// Refuses unless the VM is registered and is an Apple-Virtualization macOS machine.
    pub(crate) async fn require_available(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        self.require_info(ctx, worker).await.map(drop)
    }

    /// The host command line that runs `argv` inside the worker: `prlctl exec <worker> '<one shell string>'`.
    ///
    /// Parallels takes one shell string rather than an argv, so every argument is quoted and joined. The argv head
    /// is declared on the timeline here, which is what lets phase timing classify the spawn as a guest call without
    /// any caller opting in; Tart's backend declares its own head the same way. There is no interactive flag:
    /// Parallels' interactive spelling lives on the current-user path ([`parallels_current_user_argv`]).
    pub(crate) fn guest_argv(&self, ctx: &Ctx, worker: &str, argv: &[String]) -> Vec<String> {
        ctx.timeline().declare_guest_head(&self.settings.parallels);
        vec![
            self.settings.parallels.clone(),
            "exec".to_owned(),
            worker.to_owned(),
            quote_parallels_argv(argv),
        ]
    }

    /// `prlctl status`, the best this backend can do. A VM the controller did not start counts, deliberately: the
    /// operator's own VM *is* the worker, and a controller that only recognised machines it started would cold-boot
    /// over a running one.
    ///
    /// A `prlctl status` that exits 0 prints the state, so an empty answer is no answer, as is a `prlctl` that cannot
    /// run or that a signal ended: the refusal `probe_unanswered`. A nonzero exit is a VM that is not running.
    pub(crate) async fn running(&self, ctx: &Ctx, worker: &str) -> Result<bool, Refusal> {
        let argv = self.prlctl(&["status", worker]);
        let captured = self.runner.capture(ctx, &argv, &SpawnOptions::within(PRLCTL_QUERY_TIMEOUT)).await?;
        match captured.probe_exit() {
            ProbeExit::Answered if !captured.stdout.trim().is_empty() => Ok(status_says_running(&captured.stdout)),
            ProbeExit::Negative => Ok(false),
            ProbeExit::Answered | ProbeExit::CannotRun | ProbeExit::Killed => Err(probe_unanswered(&argv, &captured, ProbeOutput::Quoted)),
        }
    }

    /// Makes `shares` the worker's share set, power-cycling the VM if that is what it takes.
    ///
    /// `prlctl` accepts share changes on a running VM, but the guest's single VirtioFS share device attaches at
    /// *boot*, so the new set only becomes visible after a stop and a start. So: decide whether anything has to
    /// change, then stop, then mutate, then start - mutating first would leave a VM running with a configuration
    /// its guest cannot see, which reads on the host as success. The stop is gated by
    /// [`Parallels::require_survives_reboot`].
    ///
    /// Answers whether this call stopped and started the VM. A share is stored in the VM, not spelled on a command
    /// line, so there is nothing else to hand back.
    pub(crate) async fn declare_shares(&self, ctx: &Ctx, worker: &str, shares: &[SharedFolder]) -> Result<bool, Refusal> {
        let info = self.require_info(ctx, worker).await?;
        let needs_change = !info.automount_enabled()
            || shares
                .iter()
                .any(|share| !shared_folder_matches(info.shared_folder(&share.name), share));
        if !needs_change {
            return Ok(false);
        }

        let cycled = info.state != Some(VmState::Stopped);
        if cycled {
            self.require_survives_reboot(ctx, worker, "reconfiguring the shared folders")
                .await?;
            self.runner
                .checked(ctx, &self.prlctl(&["stop", worker]), &SpawnOptions::within(PRLCTL_STOP_TIMEOUT))
                .await?;
            self.wait_for_state(ctx, worker, VmState::Stopped).await?;
        }
        self.apply_shares(ctx, worker, &info, shares).await?;
        if cycled {
            self.runner
                .checked(ctx, &self.prlctl(&["start", worker]), &SpawnOptions::within(PRLCTL_START_TIMEOUT))
                .await?;
            self.wait_for_guest_execution(ctx, worker).await?;
        }
        Ok(cycled)
    }
}

// --- the prlctl guest line ----------------------------------------------------------------------------------

/// A command that runs as the logged-in Aqua user, which only Parallels can do.
pub(crate) fn parallels_current_user_argv(
    settings: &Config,
    worker: &str,
    argv: &[String],
    interactive: bool,
    dir: Option<&str>,
) -> Result<Vec<String>, Refusal> {
    if settings.backend != Backend::Parallels {
        return Err(unsupported("Peekaboo is currently available only on Parallels"));
    }
    let mut command = format!("exec {}", quote_parallels_argv(argv));
    if let Some(dir) = dir.filter(|dir| !dir.is_empty()) {
        command = format!("cd {} && {command}", quote_parallels_arg(dir));
    }
    let mut line = vec![
        settings.parallels.clone(),
        "exec".to_owned(),
        worker.to_owned(),
        "--current-user".to_owned(),
    ];
    if interactive {
        line.push("--use-advanced-terminal".to_owned());
    }
    line.push(command);
    Ok(line)
}

/// Renders an argv as the one shell string `prlctl exec` takes.
fn quote_parallels_argv(argv: &[String]) -> String {
    argv.iter()
        .map(|argument| quote_parallels_arg(argument))
        .collect::<Vec<_>>()
        .join(" ")
}

/// Quotes one argument for the single shell string `prlctl exec` takes.
///
/// `'…'"'"'…'` and not `'…'\''…'`: both are correct POSIX and they are used in different places on purpose. This
/// one survives being embedded in a command that is itself built by concatenation on the way to `prlctl`, which is
/// the path a Peekaboo passthrough takes. [`avl_base::posix_shell_quote`] is the other.
fn quote_parallels_arg(value: &str) -> String {
    format!("'{}'", value.replace('\'', r#"'"'"'"#))
}

/// `prlctl status`'s verdict as a word rather than a substring: `notrunning` and `runningx` are not verdicts. `\b`
/// treats `-` as a boundary, so a VM literally named `air-running-1` would match its own name; tolerable only
/// because this reads one command's status line. Anything that needs the state as a value reads
/// [`Parallels::info`].
fn status_says_running(status: &str) -> bool {
    static RUNNING_WORD: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\brunning\b").expect("a constant pattern compiles"));
    RUNNING_WORD.is_match(status)
}

/// Whether `who` reports a session on the console, which is what an Aqua session is. Anchored on the line and
/// requiring the word to end there, because `who` also reports ssh sessions and pseudo-terminals: a lane needs the
/// *seat*, and a worker with an ssh session and no console has no window server to open a window on.
fn has_console_session(who: &str) -> bool {
    static CONSOLE_SESSION: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"^\S+\s+console(?:\s|$)").expect("a constant pattern compiles"));
    who.lines()
        .map(|line| line.trim_end_matches('\r'))
        .any(|line| CONSOLE_SESSION.is_match(line))
}
