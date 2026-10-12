//! The Tart backend: the binary itself, the sealed golden image a worker is cloned from, the provenance receipt that
//! ties a running worker back to that seal, and how a share is declared to `tart run`.
//!
//! Parallels has no equivalent of the last three - it provisions lazily from a VM the operator already owns - and
//! declares its shares through `prlctl set` instead. Most of what is here exists to make a *sealed* image
//! trustworthy, and to recognise the controller's own run process again after the fact.
//!
//! # A worker is a host process here
//!
//! A Parallels worker is a VM the hypervisor keeps for you and `prlctl status` will tell you about; a Tart worker is
//! a long-lived `tart run` process this controller spawned, and when that process is gone the machine is gone with
//! it. So liveness is not a hypervisor query, it is process identification - and identifying a process by pid alone
//! is how a recycled pid gets mistaken for a live worker. Hence the triple in [`ProcessIdentity`]: the pid, the
//! process's start time, and its command line, all three of which have to still agree.

use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::Duration;

use avl_base::config::{TART_LABEL, TART_VERSION_OVERRIDE_VARIABLE};
use avl_base::{Config, Exit, OrRefuse, Refusal, Reporter, SCHEMA_VERSION, Scope};
use avl_host_sys::fs::real_path;
use avl_host_sys::share::SharedFolder;
use avl_host_sys::{Ctx, ProbeExit, ProbeOutput, PsField, Runner, SpawnOptions, probe_unanswered};
use regex::Regex;
use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};
use serde_json::json;
use tokio::sync::OnceCell;

use crate::worker::hypervisor::unsupported;
use crate::worker::pin::{PinnedTool, PinnedTools, parse_version};
use crate::worker::secure::{Requirements, SecureReadError, read_private_file, write_json_line};
use avl_base::RefusalExt;

/// The timeout of a Tart query: `tart list` and `tart --version` read the local VM directories and answer at once.
pub(crate) const TART_QUERY_TIMEOUT: Duration = Duration::from_mins(1);

/// The timeout of a `tart set`. It rewrites the configuration of one VM, and a disk resize grows a sparse file.
pub(crate) const TART_SET_TIMEOUT: Duration = Duration::from_mins(2);

/// The timeout of `tart suspend`. It writes about 6 GB of guest state, two seconds on 2026-08-12, and the stop path
/// then waits up to five minutes for the run process.
const TART_SUSPEND_TIMEOUT: Duration = Duration::from_mins(10);

/// The timeout of `tart stop`. Tart asks the guest to shut down and kills the VM after its own 30 s grace.
pub(crate) const TART_STOP_TIMEOUT: Duration = Duration::from_mins(2);

/// The timeout of `tart clone`. A worker is a copy-on-write clone of the local golden, so a clone pulls nothing from
/// a registry. The bound is an upper limit and not an estimate.
pub(crate) const TART_CLONE_TIMEOUT: Duration = Duration::from_hours(1);

/// The timeout of `tart delete`. It removes the directory of one VM, its disk included.
pub(crate) const TART_DELETE_TIMEOUT: Duration = Duration::from_mins(5);

#[cfg(test)]
mod pin_tests;
#[cfg(test)]
mod tests;

/// The oldest Tart that carries every subcommand and flag the controller uses: `tart exec` and `tart ip
/// --resolver=agent` (guest agent), `--root-disk-opts`, `--net-softnet`, `--suspendable` and `tart suspend`.
///
/// There is deliberately no upper bound. An exact-minor pin is how a routine `brew upgrade` to 2.35 turned every
/// Tart command into `unsupported_tart` at exit 69, on a host where nothing was actually wrong.
///
/// The pinned Tart ([`TART_LABEL`]) is newer, and a test keeps it so. The floor still gates a `TART_BIN` override.
/// `TART_MIN_VERSION` in `provision/versions.env` is the image pipeline's copy, and a test keeps the two equal.
pub(crate) const MINIMUM_VERSION: &str = "2.32.1";

/// The Tart backend, one of the two a [`Machine`](crate::worker::hypervisor::Machine) is.
///
/// It holds the settings and one runner rather than taking them per call, because the runner carries the scrubbed
/// environment: `TART_VM_TOKEN` and `TART_VM_WORKER` must not reach a child.
pub(crate) struct Tart {
    settings: Arc<Config>,
    runner: Runner,
    reporter: Reporter,
    /// Resolves the pinned Tart when `TART_BIN` names none, with the other pinned tools of the pool.
    pinned: Arc<PinnedTools>,
    /// The executable every Tart command runs: `TART_BIN`, or the pinned Tart once the gate resolved it. Held here
    /// rather than written back into the settings, which are shared and immutable; the cell also serializes the one
    /// resolution when several workers pass the gate at once.
    executable: OnceCell<PathBuf>,
}

impl Tart {
    pub(crate) fn new(settings: Arc<Config>, runner: Runner, reporter: Reporter, pinned: Arc<PinnedTools>) -> Self {
        let executable = OnceCell::new_with(settings.tart.clone());
        Self {
            settings,
            runner,
            reporter,
            pinned,
            executable,
        }
    }

    /// The `tart` executable as the head of a host-side argv, or the empty string before the gate resolved the
    /// pinned one: an argv with an empty head is refused `hypervisor_unresolved` rather than spawned.
    ///
    /// Only for host commands that already passed [`Tart::require_available`]. A guest command line never uses
    /// it: [`Tart::guest_argv`] resolves the head itself, so a verb that skipped the gate still reaches the guest.
    ///
    /// The gate resolves the pinned Tart once, and [`Config::tart`] stays `None` unless `TART_BIN` named one, so a layer that runs its own Tart command after
    /// `manager.machine().require_available(..)` (the daemon's `tart ip`, `status`'s `tart list`, the image script's
    /// `PATH`) takes the path from [`crate::worker::worker::Manager::tart_executable`], not from the settings, and never
    /// builds a second [`Tart`] over the same settings, which would ask Bazel again.
    pub(crate) fn program(&self) -> String {
        self.executable
            .get()
            .map(|path| path.to_string_lossy().into_owned())
            .unwrap_or_default()
    }

    /// The resolved `tart` executable: `TART_BIN`, or the pinned Tart once the gate resolved it; `None` before that.
    pub(crate) fn executable(&self) -> Option<&Path> {
        self.executable.get().map(PathBuf::as_path)
    }

    /// The `tart` executable as the head of an argv, resolved now when no gate resolved it yet.
    ///
    /// The lookup is memoized: after the first resolution it reads the cell and spawns nothing. It refuses
    /// `tart_missing` when neither `TART_BIN` nor Bazel names a Tart. Unlike [`Tart::program`] it never answers
    /// the empty string, so a host command built from it cannot run before the pinned Tart is known.
    pub(crate) async fn resolved_program(&self, ctx: &Ctx) -> Result<String, Refusal> {
        Ok(self.resolve_executable(ctx).await?.to_string_lossy().into_owned())
    }

    fn command(&self, arguments: &[&str]) -> Vec<String> {
        std::iter::once(self.program())
            .chain(arguments.iter().map(|argument| (*argument).to_owned()))
            .collect()
    }

    // --- the version gate ------------------------------------------------------------------------------------

    /// Writes the pinned Tart's real path into the executable cell when `TART_BIN` named none.
    ///
    /// It runs in the gate, because every worker and `status` operation passes the gate before its first Tart
    /// command. A usage refusal therefore asks Bazel nothing. The first resolution in a checkout fetches the archive,
    /// about 23 MB; after that the repository cache answers, also after `bazel clean --expunge`.
    ///
    /// The real path and not the output-base path that Bazel prints, for two reasons. The executable finds its
    /// bundle, with the signature and the provisioning profile, from the path it was started by. And
    /// [`ProcessIdentity`] compares `ps -o command=`, so the spelling must not change between two commands.
    async fn resolve_executable(&self, ctx: &Ctx) -> Result<&Path, Refusal> {
        self.executable
            .get_or_try_init(|| async {
                let Some(resolved) = self.pinned.path(ctx, PinnedTool::Tart).await? else {
                    return Err(Refusal::new(
                        "tart_missing",
                        Exit::UNAVAILABLE,
                        format!("TART_BIN names no Tart and this command has no Bazel to resolve {TART_LABEL}"),
                    ));
                };
                real_path(&resolved).map_err(|error| {
                    Refusal::new(
                        "tart_missing",
                        Exit::UNAVAILABLE,
                        format!(
                            "the pinned Tart is not on disk at {}: {error}. Fetch it with `./bazel.cmd cquery {TART_LABEL}`.",
                            resolved.display()
                        ),
                    )
                })
            })
            .await
            .map(PathBuf::as_path)
    }

    // --- liveness --------------------------------------------------------------------------------------------

    /// The recorded identity of a worker's run process, or `None`.
    ///
    /// `None` for every kind of wrong - absent file, unparseable JSON, a receipt naming another worker, a schema this
    /// build does not speak - because the caller's next question is always "is this worker running", and there is
    /// no version of "the record is damaged" that should answer yes to it.
    pub(crate) fn read_process_identity(&self, worker: &str) -> Option<ProcessIdentity> {
        let content = std::fs::read(self.settings.pid_path(worker)).ok()?;
        let identity: ProcessIdentity = serde_json::from_slice(&content).ok()?;
        let valid = identity.schema_version == SCHEMA_VERSION
            && identity.worker == worker
            && identity.pid > 0
            && !identity.process_start.is_empty()
            && !identity.process_command.is_empty();
        valid.then_some(identity)
    }

    /// Whether the process this identity describes is still the one that is running.
    ///
    /// A defunct process is dead here even though `ps` still answers for it. The spawn releases the run process
    /// without reaping it, so while the spawning invocation lives, a run process that exits is a zombie with a stable
    /// start time and a `<defunct>` command - and an identity recorded during that window matches the zombie's own
    /// answers indefinitely, which read a worker that died at boot as running.
    ///
    /// A `ps` without an answer is the refusal `probe_unanswered`, and not a dead process.
    pub(crate) async fn process_alive(&self, ctx: &Ctx, identity: Option<&ProcessIdentity>) -> Result<bool, Refusal> {
        let Some(identity) = identity else {
            return Ok(false);
        };
        Ok(
            host_process_start(ctx, &self.runner, identity.pid).await?.as_deref() == Some(identity.process_start.as_str())
                && host_process_command(ctx, &self.runner, identity.pid).await?.as_deref() == Some(identity.process_command.as_str())
                && !host_process_defunct(ctx, &self.runner, identity.pid).await?,
        )
    }

    // --- what Tart knows about a VM --------------------------------------------------------------------------

    /// Whether Tart knows a local VM by this name.
    ///
    /// `--quiet` rather than the JSON listing: the quiet one is a line-for-line name list, which is all this needs.
    pub(crate) async fn exists(&self, ctx: &Ctx, name: &str) -> Result<bool, Refusal> {
        let captured = self
            .runner
            .checked(
                ctx,
                &self.command(&["list", "--source", "local", "--quiet"]),
                &SpawnOptions::within(TART_QUERY_TIMEOUT),
            )
            .await?;
        Ok(captured.stdout.lines().any(|line| line.trim_end_matches('\r') == name))
    }

    /// The local VMs Tart knows, or a refusal if it did not answer JSON.
    async fn list(&self, ctx: &Ctx) -> Result<Vec<ListEntry>, Refusal> {
        let captured = self
            .runner
            .checked(
                ctx,
                &self.command(&["list", "--source", "local", "--format", "json"]),
                &SpawnOptions::within(TART_QUERY_TIMEOUT),
            )
            .await?;
        serde_json::from_str(&captured.stdout).or_refuse("tart_list_unreadable", Exit::UNAVAILABLE, || {
            "tart list did not return JSON".to_owned()
        })
    }

    async fn list_entry(&self, ctx: &Ctx, name: &str) -> Result<Option<ListEntry>, Refusal> {
        Ok(self.list(ctx).await?.into_iter().find(|entry| entry.name.as_deref() == Some(name)))
    }

    /// What Tart says about a VM, or `None` when it does not know the name.
    ///
    /// Anything that is neither `running` nor `suspended` is [`VmState::Stopped`], deliberately: Tart has several
    /// words for not-running and the controller's only question is whether there is a guest state to spend.
    pub(crate) async fn vm_state(&self, ctx: &Ctx, name: &str) -> Result<Option<VmState>, Refusal> {
        Ok(self.list_entry(ctx, name).await?.map(|entry| match entry.state.as_deref() {
            Some("running") => VmState::Running,
            Some("suspended") => VmState::Suspended,
            _ => VmState::Stopped,
        }))
    }

    // --- shares ----------------------------------------------------------------------------------------------

    /// The `--dir=` fragments a worker's `tart run` must carry, applying nothing.
    ///
    /// Pure, and it has to be: a Tart worker's shares are arguments to a process that does not exist yet, so there is
    /// nothing to mutate and nothing to power-cycle. A share-set change on Tart means restarting the run process the
    /// controller owns.
    pub(crate) fn share_arguments(shares: &[SharedFolder]) -> Result<Vec<String>, Refusal> {
        shares.iter().map(share_argument).collect()
    }

    // --- clone inputs and their signatures -------------------------------------------------------------------

    /// The identity of one file inside a VM's directory, refusing anything that is not a plain file.
    ///
    /// The refusals are about what a clone input may be, not about tidiness: a symlink here would let the signature
    /// describe one file while the clone reads another, which is precisely the substitution the seal exists to
    /// detect.
    pub(crate) fn vm_file_metadata(&self, vm: &str, input: CloneInput) -> Result<FileMetadata, Refusal> {
        let path = self.vm_directory(vm).join(input.file_name());
        let metadata = std::fs::symlink_metadata(&path).map_err(|error| {
            Refusal::new(
                "vm_input_missing",
                Exit::NO_INPUT,
                format!("Tart VM clone input is not readable: {}", path.display()),
            )
            .with_details(json!({ "cause": error_cause(&error) }))
        })?;
        // `symlink_metadata`, so a symlink already fails `is_file`.
        if !metadata.file_type().is_file() {
            return Err(Refusal::new(
                "vm_input_unsafe",
                Exit::NO_INPUT,
                format!("Tart VM clone input must be a regular non-symlink file: {}", path.display()),
            ));
        }
        Ok(FileMetadata::of(&metadata))
    }

    /// [`FileMetadata::signature`] for one clone input of a VM.
    pub(crate) fn vm_file_signature(&self, vm: &str, input: CloneInput) -> Result<String, Refusal> {
        self.vm_file_metadata(vm, input).map(|metadata| metadata.signature())
    }

    /// The part of a worker's disk identity that survives being used.
    ///
    /// Three fields of the five: the modification and change times move every time the guest writes a byte, so
    /// including them would make a worker's provenance receipt stale after its first boot. Inode, size and birth
    /// time do not move while the same clone exists, and all three change when it is replaced - which is the only
    /// event this has to notice.
    pub(crate) fn stable_worker_disk_identity(&self, worker: &str) -> Result<String, Refusal> {
        let metadata = self.vm_file_metadata(worker, CloneInput::Disk)?;
        Ok(format!("{}:{}:{}", metadata.inode, metadata.size, metadata.created_sec))
    }

    fn vm_directory(&self, vm: &str) -> PathBuf {
        self.settings.tart_home.join("vms").join(vm)
    }

    // --- the golden provenance pins --------------------------------------------------------------------------

    /// The pins a sealed-golden receipt must agree with, from `<image root>/versions.env`.
    ///
    /// The pins live beside the image build rather than in this controller because they are what the *build*
    /// produced: the Cirrus base digest it pulled and the macOS release it installed. A receipt that agrees with a
    /// stale copy of those numbers is the failure this indirection prevents - the golden and the controller had
    /// fallen a macOS release apart once, and each was internally consistent.
    pub(crate) fn expected_golden_provenance(&self) -> Result<GoldenProvenance, Refusal> {
        let path = self.settings.image_root.join("versions.env");
        let content = std::fs::read_to_string(&path).map_err(|error| {
            Refusal::new(
                "golden_versions_unreadable",
                Exit::NO_INPUT,
                format!("golden provenance pins are not readable: {}", path.display()),
            )
            .with_details(json!({ "cause": error_cause(&error) }))
        })?;
        let schema = provenance_pin(&content, "GOLDEN_SEAL_SCHEMA")?;
        // A plain base-ten integer: `GOLDEN_SEAL_SCHEMA=0x1` is refused rather than read as 1, which is the better
        // answer for a pin that is compared for equality against a receipt's integer.
        let schema_version = schema
            .trim()
            .parse::<i64>()
            .or_refuse("golden_versions_invalid", Exit::DATA_ERR, || {
                "GOLDEN_SEAL_SCHEMA must be an integer".to_owned()
            })?;
        Ok(GoldenProvenance {
            schema_version,
            base_digest: provenance_pin(&content, "CIRRUS_BASE_DIGEST")?,
            macos_version: provenance_pin(&content, "MACOS_VERSION")?,
        })
    }

    // --- the seal and the provenance -------------------------------------------------------------------------

    /// Where a golden's seal lives: inside the VM's own directory, so that copying the image copies the claim about
    /// it and the two cannot be separated. `lib.sh write_golden_seal` writes it.
    pub(crate) fn seal_path(&self, golden: &str) -> PathBuf {
        self.vm_directory(golden).join(".air-seal.json")
    }

    /// Refuses unless this golden is the audited image, unchanged since it was audited.
    ///
    /// Two separate verdicts, and they are worth different words. `golden_seal_invalid` means the receipt does not
    /// describe this image or does not agree with the build's own pins - a receipt from somewhere else.
    /// `golden_seal_stale` means the receipt is the right one and the *image moved underneath it*, which is what
    /// happens when someone boots the golden by hand: recovery is re-sealing, not finding another receipt.
    pub(crate) fn require_sealed_golden(&self, golden: &str) -> Result<SealedGolden, Refusal> {
        let seal = self.seal_path(golden);
        let receipt: SealedGolden = read_secure_json_receipt(
            &seal,
            "sealed-golden receipt",
            ReceiptCodes {
                missing: "golden_seal_missing",
                unreadable: "golden_seal_unreadable",
                unsafe_: "golden_seal_unsafe",
                invalid: "golden_seal_invalid",
            },
        )?;
        let expected = self.expected_golden_provenance()?;
        let identifies = receipt.schema_version == SCHEMA_VERSION
            && i64::from(receipt.schema_version) == expected.schema_version
            && receipt.golden == golden
            && receipt.base_digest == expected.base_digest
            && receipt.macos_version == expected.macos_version
            && !receipt.audited_at.is_empty();
        if !identifies {
            return Err(Refusal::new(
                "golden_seal_invalid",
                Exit::DATA_ERR,
                format!("sealed-golden receipt does not identify {golden}: {}", seal.display()),
            ));
        }
        for (input, recorded) in [
            (CloneInput::Disk, &receipt.disk_signature),
            (CloneInput::Config, &receipt.config_signature),
            (CloneInput::Nvram, &receipt.nvram_signature),
        ] {
            if self.vm_file_signature(golden, input)? != *recorded {
                return Err(Refusal::new(
                    "golden_seal_stale",
                    Exit::DATA_ERR,
                    format!("sealed-golden receipt no longer matches {golden}'s clone inputs"),
                ));
            }
        }
        Ok(receipt)
    }

    /// Records which sealed golden this worker came from, at mode 0600.
    ///
    /// Written atomically because it is read back fail-closed: a half-written receipt is not a receipt that describes
    /// a slightly different worker, it is one that refuses the worker outright and costs a re-clone.
    pub(crate) fn write_worker_provenance(&self, worker: &str, golden: &SealedGolden) -> Result<WorkerProvenance, Refusal> {
        let provenance = WorkerProvenance {
            schema_version: SCHEMA_VERSION,
            worker: worker.to_owned(),
            golden: golden.golden.clone(),
            golden_disk_signature: golden.disk_signature.clone(),
            golden_config_signature: golden.config_signature.clone(),
            golden_nvram_signature: golden.nvram_signature.clone(),
            base_digest: golden.base_digest.clone(),
            macos_version: golden.macos_version.clone(),
            worker_disk_identity: self.stable_worker_disk_identity(worker)?,
        };
        write_json_line(
            &self.settings.worker_provenance_path(worker),
            &provenance,
            &format!("the provenance for {worker}"),
        )?;
        Ok(provenance)
    }

    /// Refuses unless this worker is a clone of the sealed golden, still unchanged.
    ///
    /// `golden` is explicit because `pool start` checks a slot against the golden it is about to clone from rather
    /// than against today's configured one; every other caller passes [`Config::golden_vm`].
    ///
    /// Note the order: the receipt's own shape first, then the golden's seal, then the worker's disk. A stale *golden*
    /// has to be reported as a golden problem, so the seal is re-read here rather than trusted from whatever read it
    /// last.
    pub(crate) fn require_worker_provenance(&self, worker: &str, golden: &str) -> Result<WorkerProvenance, Refusal> {
        let path = self.settings.worker_provenance_path(worker);
        let receipt: WorkerProvenance = read_secure_json_receipt(
            &path,
            &format!("worker provenance for {worker}"),
            ReceiptCodes {
                missing: "worker_provenance_missing",
                unreadable: "worker_provenance_unreadable",
                unsafe_: "worker_provenance_unsafe",
                invalid: "worker_provenance_invalid",
            },
        )?;
        if receipt.schema_version != SCHEMA_VERSION || receipt.worker != worker || receipt.golden != golden {
            return Err(Refusal::new(
                "worker_provenance_invalid",
                Exit::DATA_ERR,
                format!("worker provenance does not identify {worker}: {}", path.display()),
            ));
        }
        let sealed = self.require_sealed_golden(golden)?;
        let disk_identity = self.stable_worker_disk_identity(worker)?;
        let matches = receipt.golden_disk_signature == sealed.disk_signature
            && receipt.golden_config_signature == sealed.config_signature
            && receipt.golden_nvram_signature == sealed.nvram_signature
            && receipt.base_digest == sealed.base_digest
            && receipt.macos_version == sealed.macos_version
            && receipt.worker_disk_identity == disk_identity;
        if !matches {
            return Err(Refusal::new(
                "worker_provenance_stale",
                Exit::DATA_ERR,
                format!("worker provenance no longer matches {worker} or {golden}"),
            ));
        }
        Ok(receipt)
    }
}

// --- what the machine asks of the backend ------------------------------------------------------------------

impl Tart {
    /// Resolves the `tart` executable and refuses unless it is new enough.
    ///
    /// The worker name is ignored: unlike Parallels, a Tart pool slot is a name the controller may use rather than a
    /// VM that must already exist, so there is nothing about the worker to check here. The first operation on a free
    /// slot clones it.
    pub(crate) async fn require_available(&self, ctx: &Ctx, _worker: &str) -> Result<(), Refusal> {
        let program = self.resolve_executable(ctx).await?.to_owned();
        let argv = self.command(&["--version"]);
        let captured = self.runner.capture(ctx, &argv, &SpawnOptions::within(TART_QUERY_TIMEOUT)).await?;
        // A tool that cannot run is a missing tool here. A `tart` that a signal ended, or that printed no version, gave
        // no answer.
        if captured.probe_exit() == ProbeExit::Killed || (captured.exit_code == 0 && captured.stdout.trim().is_empty()) {
            return Err(probe_unanswered(&argv, &captured, ProbeOutput::Quoted));
        }
        if captured.exit_code != 0 {
            return Err(Refusal::new(
                "tart_missing",
                Exit::UNAVAILABLE,
                format!(
                    "{} --version exited with {}: {}",
                    program.display(),
                    captured.exit_code,
                    captured.stderr.trim()
                ),
            ));
        }
        if self.settings.tart_version_override {
            return Ok(());
        }
        let reported = captured.stdout.trim();
        let minimum = parse_version(MINIMUM_VERSION);
        match parse_version(reported) {
            Some(version) if Some(version) >= minimum => Ok(()),
            _ => {
                let found = if reported.is_empty() { "unknown" } else { reported };
                Err(Refusal::new(
                    "unsupported_tart",
                    Exit::UNAVAILABLE,
                    format!(
                        "Tart {MINIMUM_VERSION} or newer is required; found {found:?}. Set \
                         {TART_VERSION_OVERRIDE_VARIABLE}=1 to run anyway (for bisecting a Tart regression)."
                    ),
                ))
            }
        }
    }

    /// `tart exec [-i] <worker> <argv…>`.
    ///
    /// The one spelling of a Tart guest command line, and the one place its head is declared to phase timing, so a
    /// spawn that did not come through here is a host call by construction. The head is not read from the settings:
    /// the pinned Tart resolved by the gate lives in this backend, not in the shared settings.
    ///
    /// The head comes from [`Tart::resolved_program`], not from the gate: `daemon stop` and `daemon log` reach the
    /// guest without passing [`Tart::require_available`], and an empty head would reach the spawn.
    pub(crate) async fn guest_argv(&self, ctx: &Ctx, worker: &str, argv: &[String], interactive: bool) -> Result<Vec<String>, Refusal> {
        let head = self.resolved_program(ctx).await?;
        ctx.timeline().declare_guest_head(&head);
        let mut line = vec![head, "exec".to_owned()];
        if interactive {
            line.push("-i".to_owned());
        }
        line.push(worker.to_owned());
        line.extend_from_slice(argv);
        Ok(line)
    }

    /// The controller's own run process, identified by the whole triple.
    ///
    /// Never a hypervisor query, and that is not an optimisation. `tart list` knows whether a VM is running, but it
    /// cannot tell a worker this controller started from one an operator started by hand in another checkout, and
    /// the pool is per *machine*. The process identity is what makes "mine, and still the same one" answerable.
    pub(crate) async fn running(&self, ctx: &Ctx, worker: &str) -> Result<bool, Refusal> {
        let identity = self.read_process_identity(worker);
        self.process_alive(ctx, identity.as_ref()).await
    }

    /// Signals the run process to save its guest state.
    ///
    /// No `start_if_stopped`, and the asymmetry with Parallels is real: there, a stopped VM still exists and can be
    /// started in order to be suspended. Here the machine *is* the run process, so a stopped worker has nothing to
    /// suspend and starting one would be a cold boot followed immediately by a suspend.
    ///
    /// Waiting for the process to go away stays with the caller, which is the half that holds the identity and the
    /// timing: a suspend writes gigabytes before the process exits and gets a five-minute grace.
    pub(crate) async fn suspend(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        if !self.settings.vm_suspendable {
            return Err(unsupported(format!(
                "suspending {worker} needs the `--suspendable` device set, which AIR_VM_SUSPENDABLE turned off; \
                 the worker can only be stopped"
            )));
        }
        self.runner
            .checked(
                ctx,
                &self.command(&["suspend", worker]),
                &SpawnOptions::within(TART_SUSPEND_TIMEOUT),
            )
            .await?;
        Ok(())
    }

    /// The root-disk size Tart reports, or `None` if it does not know the VM or did not say.
    ///
    /// "Tart did not report a disk size" and "Tart reported 0 GB" are different facts, and only the first one means
    /// "ask again after the clone".
    pub(crate) async fn root_disk_gb(&self, ctx: &Ctx, worker: &str) -> Result<Option<u32>, Refusal> {
        Ok(self.list_entry(ctx, worker).await?.and_then(|entry| entry.disk))
    }

    /// Raises a worker's root disk to `want_gb`, answering whether it asked Tart for anything.
    ///
    /// Grow only. `tart set --disk-size` refuses to shrink, so a worker made when the default was 400 GB stays 400 GB
    /// and asking for today's 120 would fail the whole `set` rather than being ignored - which is why the current
    /// size is read first and an over-sized worker is reported rather than repaired.
    pub(crate) async fn grow_root_disk(&self, ctx: &Ctx, worker: &str, want_gb: u32) -> Result<bool, Refusal> {
        if let Some(current) = self.root_disk_gb(ctx, worker).await?
            && current >= want_gb
        {
            if current > want_gb {
                self.reporter.note(
                    format!("{worker} already has a {current} GB root disk; leaving it (tart cannot shrink one)"),
                    Some(&Scope::worker(worker)),
                );
            }
            return Ok(false);
        }
        let size = want_gb.to_string();
        self.runner
            .checked(
                ctx,
                &self.command(&["set", worker, "--disk-size", &size]),
                &SpawnOptions::within(TART_SET_TIMEOUT),
            )
            .await?;
        Ok(true)
    }
}

// --- liveness --------------------------------------------------------------------------------------------------

/// How the controller recognises its own `tart run` process again: `<worker>/tart.pid`.
///
/// Three fields and not one, because a pid on its own is not an identity. `process_start` is `ps -o lstart=` with
/// whitespace collapsed and `process_command` is `ps -o command=` raw; together with the pid they are unique in
/// practice, and all three must still agree for the worker to count as running.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ProcessIdentity {
    pub schema_version: u32,
    pub worker: String,
    pub pid: i32,
    pub process_start: String,
    pub process_command: String,
}

/// `ps -o lstart=` for a pid, whitespace-collapsed, or `None` when there is no such process. Collapsed because the
/// string is compared for equality: `lstart` pads the day of the month.
pub(crate) async fn host_process_start(ctx: &Ctx, runner: &Runner, pid: i32) -> Result<Option<String>, Refusal> {
    runner.probe_process(ctx, pid, PsField::StartTime).await
}

/// `ps -o command=` for a pid, or `None` when there is no such process. Not collapsed: the command line's own
/// spacing is part of what makes it distinguishing.
pub(crate) async fn host_process_command(ctx: &Ctx, runner: &Runner, pid: i32) -> Result<Option<String>, Refusal> {
    runner.probe_process(ctx, pid, PsField::Command).await
}

/// Whether the pid names a zombie: exited, unreaped, and still in the process table. The zombie flag is the leading
/// `Z` of `stat=` under both the BSD ps and procps.
async fn host_process_defunct(ctx: &Ctx, runner: &Runner, pid: i32) -> Result<bool, Refusal> {
    Ok(runner
        .probe_process(ctx, pid, PsField::State)
        .await?
        .is_some_and(|state| state.starts_with('Z')))
}

// --- what Tart knows about a VM --------------------------------------------------------------------------------

/// Tart's own view of a VM, which is the only thing that knows about a saved guest state.
///
/// The controller's process identity can only say "no run process of mine is alive", and a suspended worker and a
/// shut-down one answer that identically - so spending or keeping a saved state has to be decided from here.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub(crate) enum VmState {
    Running,
    Stopped,
    Suspended,
}

impl VmState {
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::Running => "running",
            Self::Stopped => "stopped",
            Self::Suspended => "suspended",
        }
    }
}

/// The subset of `tart list --format json` this controller reads. Tart capitalises its keys.
#[derive(Debug, Deserialize)]
struct ListEntry {
    #[serde(rename = "Name")]
    name: Option<String>,
    #[serde(rename = "Disk")]
    disk: Option<u32>,
    #[serde(rename = "State")]
    state: Option<String>,
}

// --- shares ----------------------------------------------------------------------------------------------------

/// One read-only share as `tart run` takes it: `--dir=name:path:options`.
///
/// Both details of the grammar matter. The *name prefix* is what names the share's directory under the guest mount
/// point - without it Tart falls back to the host directory's basename, which is not what the guest's share mount
/// looks for. And no `tag=` option is passed, deliberately: a tag moves the share onto its own virtio-fs device,
/// while the controller's remount mounts only Apple's `com.apple.virtio-fs.automount`, so a tagged share is
/// invisible inside the guest.
///
/// The fields are positional and colon-separated, so a host path containing a colon would re-split into a different
/// share rather than fail - hence the refusal rather than a trusting join. An empty path means the caller spawned
/// `tart run` before the host paths were resolved; the VM would boot with a share pointing nowhere.
pub(crate) fn share_argument(share: &SharedFolder) -> Result<String, Refusal> {
    match share.path.to_str() {
        Some(path) if !path.is_empty() && !path.contains(':') => Ok(format!("--dir={}:{path}:{}", share.name, share.mode)),
        _ => Err(Refusal::new(
            "unsafe_share_path",
            Exit::DATA_ERR,
            format!(
                "Tart cannot declare the {} share for {:?}: its --dir grammar is name:path:options, so the host \
                 path must be non-empty and free of ':'",
                share.name,
                share.path.display().to_string()
            ),
        )),
    }
}

// --- clone inputs and their signatures -------------------------------------------------------------------------

/// The three files of a VM a clone copies, and so the three a seal records.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum CloneInput {
    Disk,
    Config,
    Nvram,
}

impl CloneInput {
    pub(crate) const fn file_name(self) -> &'static str {
        match self {
            Self::Disk => "disk.img",
            Self::Config => "config.json",
            Self::Nvram => "nvram.bin",
        }
    }
}

/// The identity of one clone input, in whole seconds.
///
/// Seconds and not milliseconds, because that is what the seals already on disk record - see
/// [`FileMetadata::signature`] for why the truncation has to be reproduced exactly rather than merely closely.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct FileMetadata {
    pub inode: u64,
    pub size: u64,
    pub modified_sec: i64,
    pub changed_sec: i64,
    /// The birth time, which only macOS records; zero elsewhere, where the backend does not run.
    pub created_sec: i64,
}

impl FileMetadata {
    fn of(metadata: &std::fs::Metadata) -> Self {
        use std::os::unix::fs::MetadataExt;
        Self {
            inode: metadata.ino(),
            size: metadata.size(),
            // The seconds fields of `struct stat`: the truncation is the contract, see `signature`.
            modified_sec: metadata.mtime(),
            changed_sec: metadata.ctime(),
            created_sec: birth_seconds(metadata),
        }
    }

    /// `inode:size:mtime:ctime:birthtime`, in whole seconds: exactly what `lib.sh` records with
    /// `stat -f '%i:%z:%m:%c:%B'`.
    ///
    /// The truncation is the contract. Rounding, or going through a time value that rounds, would move roughly half
    /// of all signatures by one second and make every sealed golden and every worker provenance receipt already on
    /// disk read as stale. Recovering from that is re-sealing the image.
    pub(crate) fn signature(&self) -> String {
        format!(
            "{}:{}:{}:{}:{}",
            self.inode, self.size, self.modified_sec, self.changed_sec, self.created_sec
        )
    }
}

/// The birth time's seconds, truncated. macOS is the one host the Tart backend runs on and the one that records it.
#[cfg(target_os = "macos")]
fn birth_seconds(metadata: &std::fs::Metadata) -> i64 {
    metadata
        .created()
        .ok()
        .and_then(|created| created.duration_since(std::time::UNIX_EPOCH).ok())
        .and_then(|since| i64::try_from(since.as_secs()).ok())
        .unwrap_or(0)
}

/// Zero off macOS: Linux's `stat` has no birth time, and the backend there exists only so the controller compiles
/// and its tests run on the Linux hosts it also has to run on.
#[cfg(not(target_os = "macos"))]
const fn birth_seconds(_metadata: &std::fs::Metadata) -> i64 {
    0
}

// --- the golden provenance pins --------------------------------------------------------------------------------

/// What `provision/versions.env` pins a sealed golden to.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct GoldenProvenance {
    pub schema_version: i64,
    pub base_digest: String,
    pub macos_version: String,
}

/// One `NAME=value` line of `versions.env`.
///
/// Asked for by key rather than parsed whole: the file is a shell fragment with comments and blank lines, not a
/// document. The trailing `\r?` keeps a CRLF file readable, so the image pipeline does not have to care how its
/// editor ends lines.
fn provenance_pin(content: &str, name: &str) -> Result<String, Refusal> {
    let pattern =
        Regex::new(&format!(r"(?m)^{}=([^\r\n]+)\r?$", regex::escape(name)))
            .or_refuse("internal_error", Exit::FAILURE, || format!("the {name} pattern"))?;
    pattern
        .captures(content)
        .and_then(|captures| captures.get(1))
        .map(|value| value.as_str().to_owned())
        .ok_or_else(|| {
            Refusal::new(
                "golden_versions_invalid",
                Exit::DATA_ERR,
                format!("golden provenance pin {name} is missing"),
            )
        })
}

// --- sealed receipts -------------------------------------------------------------------------------------------

/// The four refusals a secure receipt read can answer, named by its caller so that a golden seal and a worker
/// provenance failure are distinguishable in an envelope without matching on prose.
#[derive(Clone, Copy, Debug)]
pub(crate) struct ReceiptCodes {
    pub missing: &'static str,
    pub unreadable: &'static str,
    pub unsafe_: &'static str,
    pub invalid: &'static str,
}

/// Decodes a receipt only if it is a private regular file the controller opened without following a symlink.
///
/// These receipts are the controller's whole trust story for an image: the seal says a golden is the one that was
/// audited, and the provenance says a worker was cloned from that seal. A receipt anyone could write is not
/// evidence of anything. A decode failure and a missing field answer the same code, so a caller branching on it
/// does not have to care which of the two it was.
pub(crate) fn read_secure_json_receipt<T: DeserializeOwned>(path: &Path, label: &str, codes: ReceiptCodes) -> Result<T, Refusal> {
    let unreadable = |error: &std::io::Error| {
        Refusal::new(
            codes.unreadable,
            Exit::NO_INPUT,
            format!("{label} is not readable: {}", path.display()),
        )
        .with_details(json!({ "cause": error_cause(error) }))
    };
    let content = read_private_file(
        path,
        Requirements {
            mode: 0o600,
            owned_by_caller: false,
        },
    )
    .map_err(|error| match error {
        SecureReadError::Missing => Refusal::new(codes.missing, Exit::NO_INPUT, format!("{label} is missing: {}", path.display())),
        SecureReadError::Symlink => Refusal::new(
            codes.unsafe_,
            Exit::NO_INPUT,
            format!("{label} must not be a symlink: {}", path.display()),
        ),
        SecureReadError::Unreadable(error) => unreadable(&error),
        SecureReadError::NotRegular | SecureReadError::WrongMode | SecureReadError::WrongOwner => Refusal::new(
            codes.unsafe_,
            Exit::NO_INPUT,
            format!("{label} must be a regular mode-0600 file: {}", path.display()),
        ),
    })?;
    serde_json::from_slice(&content).or_refuse(codes.invalid, Exit::DATA_ERR, || {
        format!("{label} is not valid JSON: {}", path.display())
    })
}

/// A validated sealed-golden receipt: the image was audited, and these are the clone inputs it was audited against.
///
/// Every field is required on read, and a missing one is `golden_seal_invalid`: an empty signature is a string the
/// seal stated, an absent one is a receipt that names nothing. Equality is identity: two receipts are the same claim
/// about the same image exactly when every field agrees.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SealedGolden {
    pub schema_version: u32,
    pub golden: String,
    pub disk_signature: String,
    pub config_signature: String,
    pub nvram_signature: String,
    pub base_digest: String,
    pub macos_version: String,
    pub audited_at: String,
}

/// Ties one worker back to the sealed golden it was cloned from: `<worker>/provenance.json`.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct WorkerProvenance {
    pub schema_version: u32,
    pub worker: String,
    pub golden: String,
    pub golden_disk_signature: String,
    pub golden_config_signature: String,
    pub golden_nvram_signature: String,
    pub base_digest: String,
    pub macos_version: String,
    pub worker_disk_identity: String,
}

/// The errno text a refusal carries as evidence: whether a receipt was absent or unreadable is in the details,
/// which the message alone does not say. Lower-cased at the head ("no such file or directory"), so it reads as the
/// continuation of a sentence.
fn error_cause(error: &std::io::Error) -> serde_json::Value {
    match error.raw_os_error() {
        Some(errno) => {
            let text = nix::errno::Errno::from_raw(errno).desc();
            let mut chars = text.chars();
            let lowered: String = chars
                .next()
                .map(|head| head.to_lowercase().chain(chars).collect())
                .unwrap_or_default();
            json!(lowered)
        }
        None => serde_json::Value::Null,
    }
}
