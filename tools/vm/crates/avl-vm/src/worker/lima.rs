//! The Lima engine: the Linux VM that runs `dockerd` for a Docker pool on a macOS host, so a Mac needs no Docker
//! installation.
//!
//! The controller owns one VM per machine, [`LIMA_ENGINE_INSTANCE`], under `LIMA_HOME` = [`Config::lima_home`].
//! `limactl` is the Bazel pin of `lima.MODULE.bazel`, resolved at the first engine command as the pinned Tart is.
//! The VM is made from the embedded [`ENGINE_TEMPLATE`], rendered with the home, the sizes and the image pins. The
//! Docker CLI reaches the forwarded socket through `DOCKER_HOST` ([`docker_host`]).
//!
//! The VM mounts the whole home read-only, at the same path, as Lima's default template does. So the read-only shares
//! of every checkout of the user are visible, and a container binds them with `--mount source=<host path>` as on any
//! engine. A checkout switch does not change the template. A repository or a Bazel output user root outside the home
//! is refused `share_outside_home`, because the engine cannot see it.
//!
//! The template digest is the engine's identity, the Lima twin of the Docker create record: a start whose rendered
//! template differs from the recorded digest makes the VM again, unless another holder leases a container of the
//! pool. Only the home, the sizes and the image pins move the digest.
//!
//! Every change of the engine runs under the pool-wide lock [`Config::lima_engine_lock_path`], because the bodies of a
//! leased run reach the gate of their workers at once.
//!
//! The engine rule ([`avl_base::DockerEngine::decide`]) chooses this engine only on a macOS host. The module
//! compiles on every host, so its suite runs over the fake `limactl` on every Unix host.

use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::Duration;

use avl_base::SystemClock;
use avl_base::config::{LIMA_ENGINE_INSTANCE, limactl_label, pins};
use avl_base::{Config, Exit, GuestArch, Refusal, Reporter, SCHEMA_VERSION};
use avl_host_sys::fs::real_path;
use avl_host_sys::guest::ensure_host_paths;
use avl_host_sys::lock::{LockGuard, LockManager};
use avl_host_sys::{Backoff, Ctx, Poll, ProcError, Runner, SpawnOptions};
use serde::{Deserialize, Serialize};
use sha2::{Digest as _, Sha256};
use tokio::sync::OnceCell;

use crate::worker::docker::{log_options, remove_if_present};
use crate::worker::pin::{PinnedTool, PinnedTools};
use crate::worker::secure::write_json_line;
use crate::worker::worker::{HeldLeases, Lease, lease_authorizes, read_lease};

#[cfg(test)]
mod pin_tests;
#[cfg(test)]
mod tests;

/// The engine template, embedded. Four levels up from this source file (`worker`, `src`, `avl-vm`, `crates`) is the
/// workspace root; Bazel names the file in `compile_data`.
pub(crate) const ENGINE_TEMPLATE: &str = include_str!("../../../../docker/engine.lima.yaml");

/// The oldest Lima that reads [`ENGINE_TEMPLATE`]: its `minimumLimaVersion`. A test holds the Bazel pin at or above
/// it, and another test holds the template to the same value.
#[cfg(test)]
pub(crate) const MINIMUM_LIMA_VERSION: &str = "2.0.2";

/// How often the start looks for the forwarded socket after `limactl start` returned. Fixed and short, because the
/// probe is a `stat` and costs no process.
const SOCKET_POLL: Duration = Duration::from_millis(200);

/// The least budget of a start that creates the engine VM. The first start downloads the Ubuntu image and installs
/// Docker: 147 s on 2026-09-30, of which the download took 103 s. A warm start took 20 s, and `AIR_VM_BOOT_TIMEOUT`
/// bounds it. A larger `AIR_VM_BOOT_TIMEOUT` raises this budget too.
pub(crate) const FIRST_START_BUDGET: Duration = Duration::from_mins(15);

/// How long the host waits for `limactl start` past its `--timeout`: the budget bounds the start inside `limactl`,
/// and this grace lets it report the timeout itself before the runner kills it.
const START_GRACE: Duration = Duration::from_mins(1);

/// The timeout of `limactl list`, which reads the instance directory under the Lima home.
const LIMA_LIST_TIMEOUT: Duration = Duration::from_mins(1);

/// The timeout of `limactl stop`. It shuts down the engine VM, and Docker and its containers with it.
const LIMA_STOP_TIMEOUT: Duration = Duration::from_mins(5);

/// The timeout of `limactl delete --force`. It stops the engine VM and removes its disk.
const LIMA_DELETE_TIMEOUT: Duration = Duration::from_mins(5);

/// The settings the engine template is rendered with.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct EngineInputs {
    pub cpus: u32,
    pub memory_mib: u32,
    pub disk_gb: u32,
    /// The home of the user, as a real path: the one read-only mount of the VM.
    pub home: String,
    /// The cloud image of each architecture: its URL and its sha256.
    pub image_arm64: (String, String),
    pub image_x86_64: (String, String),
}

impl EngineInputs {
    /// The inputs of this invocation: the sizes of a Linux worker, the home, and the image pins.
    ///
    /// Refuses before the host paths are resolved. Refuses `share_outside_home` when the repository or the Bazel output
    /// user root is not under the home, and a home that is not UTF-8, because the template is text. The paths are
    /// compared as real paths, because the host paths are real paths and `HOME` may name a symlink.
    pub(crate) fn of(settings: &Config) -> Result<Self, Refusal> {
        let real = |path: &Path| real_path(path).unwrap_or_else(|_| path.to_owned());
        let home = real(&settings.home);
        for (path, what) in [
            (settings.host_repo()?, "the repository"),
            (settings.host_bazel_user_root()?, "the Bazel output user root"),
        ] {
            if !real(path).starts_with(&home) {
                return Err(Refusal::new(
                    "share_outside_home",
                    Exit::USAGE,
                    format!(
                        "{what} {} is not under the home {}, and the Lima engine mounts only the home; move it under \
                         the home, or set DOCKER_HOST or DOCKER_BIN to use another engine",
                        path.display(),
                        home.display()
                    ),
                ));
            }
        }
        let home = home.to_str().map(str::to_owned).ok_or_else(|| {
            Refusal::new(
                "unsafe_share_path",
                Exit::DATA_ERR,
                format!("the Lima engine cannot mount the home {}: the path is not UTF-8", home.display()),
            )
        })?;
        let image = |arch| {
            let (url, sha256) = pins::lima_base_image(arch);
            (url.to_owned(), sha256.to_owned())
        };
        Ok(Self {
            cpus: settings.vm_cpu,
            memory_mib: settings.vm_memory_mib,
            disk_gb: settings.vm_root_disk_gb,
            home,
            image_arm64: image(GuestArch::Arm64),
            image_x86_64: image(GuestArch::X86_64),
        })
    }

    /// The value of one marker, or `None` for a name the template must not carry.
    fn value(&self, name: &str) -> Option<String> {
        let quoted = |text: &str| serde_json::Value::from(text).to_string();
        let digest = |sha256: &str| quoted(&format!("sha256:{sha256}"));
        Some(match name {
            "CPUS" => self.cpus.to_string(),
            "MEMORY" => quoted(&format!("{}MiB", self.memory_mib)),
            "DISK" => quoted(&format!("{}GiB", self.disk_gb)),
            "HOME" => quoted(&self.home),
            "IMAGE_ARM64_URL" => quoted(&self.image_arm64.0),
            "IMAGE_ARM64_DIGEST" => digest(&self.image_arm64.1),
            "IMAGE_X86_64_URL" => quoted(&self.image_x86_64.0),
            "IMAGE_X86_64_DIGEST" => digest(&self.image_x86_64.1),
            _ => return None,
        })
    }
}

/// [`ENGINE_TEMPLATE`] with every `@@NAME@@` marker replaced, in one pass: text a value brings in is never read as a
/// marker. A marker of an unknown name stays as it is, and a test holds the template to known names.
///
/// A string arrives as a JSON string, which is a YAML double-quoted scalar, so a path with a quote or a colon cannot
/// change the document. The marker spelling cannot collide with Lima's own `{{.Dir}}` and `{{.User}}`, which stay.
pub(crate) fn render_template(inputs: &EngineInputs) -> String {
    const MARK: &str = "@@";
    let mut rendered = String::with_capacity(ENGINE_TEMPLATE.len());
    let mut rest = ENGINE_TEMPLATE;
    while let Some(start) = rest.find(MARK) {
        let after = &rest[start + MARK.len()..];
        let replaced = after.find(MARK).and_then(|end| {
            let name = &after[..end];
            inputs.value(name).map(|value| (value, end))
        });
        rendered.push_str(&rest[..start]);
        if let Some((value, end)) = replaced {
            rendered.push_str(&value);
            rest = &after[end + MARK.len()..];
        } else {
            rendered.push_str(MARK);
            rest = after;
        }
    }
    rendered.push_str(rest);
    rendered
}

/// The identity of a rendered template: its sha256 in hex.
pub(crate) fn template_digest(rendered: &str) -> String {
    hex::encode(Sha256::digest(rendered.as_bytes()))
}

/// The `DOCKER_HOST` that reaches the engine of these settings: `unix://` and [`Config::lima_socket_path`].
pub(crate) fn docker_host(settings: &Config) -> String {
    format!("unix://{}", settings.lima_socket_path().display())
}

/// The template digest the engine VM was created from: `<runtime root>/lima-engine.json`.
///
/// A record about another instance, of another schema, or one that does not parse vouches for nothing, and the VM
/// is made again.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct EngineRecord {
    pub schema_version: u32,
    pub template_digest: String,
    pub instance: String,
}

/// What `limactl list` says about the engine VM.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum EngineState {
    /// No instance of that name.
    Absent,
    Stopped,
    Running,
    /// Every other word of Lima (`Broken`, `Unknown`), kept as Lima said it.
    Other(String),
}

impl EngineState {
    /// `limactl list --format '{{.Status}}' <instance>`, parsed. An empty answer names no instance.
    pub(crate) fn parse(answer: &str) -> Self {
        match answer.trim() {
            "" => Self::Absent,
            "Running" => Self::Running,
            "Stopped" => Self::Stopped,
            word => Self::Other(word.to_owned()),
        }
    }

    /// The word `status` prints: Lima's own word, or `Absent`.
    pub(crate) fn as_str(&self) -> &str {
        match self {
            Self::Absent => "Absent",
            Self::Stopped => "Stopped",
            Self::Running => "Running",
            Self::Other(word) => word,
        }
    }
}

/// The budget of one engine start, and where it comes from, for the refusal that names it.
#[derive(Clone, Copy, Debug)]
struct StartBudget {
    duration: Duration,
    /// Whether the start creates the VM, which takes the larger of [`FIRST_START_BUDGET`] and `AIR_VM_BOOT_TIMEOUT`.
    first: bool,
}

impl StartBudget {
    fn of(settings: &Config, first: bool) -> Self {
        let warm = Duration::from_secs(u64::from(settings.boot_timeout_seconds));
        let duration = if first { warm.max(FIRST_START_BUDGET) } else { warm };
        Self { duration, first }
    }

    /// What a refusal says about the budget that applied.
    fn describe(self) -> String {
        let seconds = self.duration.as_secs();
        if !self.first {
            return format!("the budget was {seconds} s from AIR_VM_BOOT_TIMEOUT, which bounds the start of an engine that exists");
        }
        let source = if self.duration > FIRST_START_BUDGET {
            "AIR_VM_BOOT_TIMEOUT, which is larger than the first-start budget".to_owned()
        } else {
            format!(
                "the first-start budget of {} s, because this start created the engine, which downloads the Ubuntu \
                 image and installs Docker",
                FIRST_START_BUDGET.as_secs()
            )
        };
        format!("the budget was {seconds} s from {source}")
    }
}

/// The Lima engine of a Docker pool.
///
/// Its runner carries `LIMA_HOME`, so every `limactl` command reads and writes the controller's own Lima state and
/// never the user's `~/.lima`.
pub(crate) struct Lima {
    settings: Arc<Config>,
    runner: Runner,
    reporter: Reporter,
    /// The locks of this invocation, for the pool-wide engine lock.
    locks: Arc<LockManager>,
    /// Resolves the pinned `limactl`, with the other pinned tools of the pool. A pool with no Bazel refuses
    /// `lima_missing` at the first command that needs it.
    pinned: Arc<PinnedTools>,
    /// The real path of the pinned `limactl`, once resolved. The cell also serializes the one resolution.
    executable: OnceCell<PathBuf>,
    /// The leases this invocation holds, which do not stop a recreate ([`Lima::require_no_lease`]).
    held: Arc<HeldLeases>,
}

impl Lima {
    pub(crate) fn new(
        settings: Arc<Config>,
        runner: &Runner,
        reporter: Reporter,
        locks: Arc<LockManager>,
        pinned: Arc<PinnedTools>,
        held: Arc<HeldLeases>,
    ) -> Self {
        let lima_home = settings.lima_home.to_string_lossy().into_owned();
        let runner = runner.with_overrides(&[("LIMA_HOME", &lima_home)]);
        Self {
            settings,
            runner,
            reporter,
            locks,
            pinned,
            executable: OnceCell::new(),
            held,
        }
    }

    /// The host end of the forwarded Docker socket.
    pub(crate) fn socket_path(&self) -> PathBuf {
        self.settings.lima_socket_path()
    }

    /// The `DOCKER_HOST` that reaches this engine.
    pub(crate) fn docker_host(&self) -> String {
        docker_host(&self.settings)
    }

    /// The real path of the pinned `limactl`, resolved through Bazel at the first call.
    ///
    /// The real path, because `limactl` finds `../share/lima`, with the guest agent, from the path it was started
    /// by. The first resolution in a checkout fetches the archive, about 38 MB. The archive's `limactl` is ad-hoc
    /// signed with the virtualization entitlement, so it starts a `vz` VM without a signing step.
    async fn resolve_executable(&self, ctx: &Ctx) -> Result<&Path, Refusal> {
        self.executable
            .get_or_try_init(|| async {
                let label = limactl_label(self.settings.guest_arch);
                let Some(resolved) = self.pinned.path(ctx, PinnedTool::Limactl).await? else {
                    return Err(lima_missing(format!(
                        "this command has no Bazel to resolve {label}, the pinned limactl of the Docker engine; set \
                         DOCKER_BIN or DOCKER_HOST to use another engine"
                    )));
                };
                real_path(&resolved).map_err(|error| {
                    lima_missing(format!(
                        "the pinned limactl is not on disk at {}: {error}. Fetch it with `./bazel.cmd cquery {label}`.",
                        resolved.display()
                    ))
                })
            })
            .await
            .map(PathBuf::as_path)
    }

    async fn command(&self, ctx: &Ctx, arguments: &[&str]) -> Result<Vec<String>, Refusal> {
        let head = self.resolve_executable(ctx).await?.to_string_lossy().into_owned();
        Ok(std::iter::once(head)
            .chain(arguments.iter().map(|argument| (*argument).to_owned()))
            .collect())
    }

    /// The pool-wide engine lock. A waiter waits for the longest start another holder can run, then finds the engine
    /// that start left.
    async fn lock(&self, ctx: &Ctx) -> Result<LockGuard, Refusal> {
        let path = self.settings.lima_engine_lock_path();
        let wait = StartBudget::of(&self.settings, true).duration;
        self.locks
            .acquire_queued(
                ctx,
                &path,
                "lima-engine",
                wait,
                "engine_busy",
                &format!(
                    "another command held the Lima engine lock {} for longer than {} s; the engine log is {}",
                    path.display(),
                    wait.as_secs(),
                    self.settings.lima_engine_log_path().display()
                ),
            )
            .await
    }

    // --- the state ---------------------------------------------------------------------------------------

    /// What Lima says about the engine VM.
    ///
    /// An instance directory that does not exist is [`EngineState::Absent`] without a `limactl` call, so `status` on a
    /// host that never started the engine resolves nothing and downloads nothing.
    pub(crate) async fn state(&self, ctx: &Ctx) -> Result<EngineState, Refusal> {
        if !self.settings.lima_home.join(LIMA_ENGINE_INSTANCE).is_dir() {
            return Ok(EngineState::Absent);
        }
        let argv = self
            .command(ctx, &["list", "--format", "{{.Status}}", LIMA_ENGINE_INSTANCE])
            .await?;
        let captured = self.runner.capture(ctx, &argv, &SpawnOptions::within(LIMA_LIST_TIMEOUT)).await?;
        if captured.exit_code == 0 {
            return Ok(EngineState::parse(&captured.stdout));
        }
        // `limactl list <name>` of an unknown name warns "No instance matching" and exits 1.
        if captured.stderr.contains("No instance matching") {
            return Ok(EngineState::Absent);
        }
        Err(Refusal::new(
            "subprocess_failed",
            Exit::FAILURE,
            format!(
                "limactl list {LIMA_ENGINE_INSTANCE} exited with {}: {}",
                captured.exit_code,
                captured.stderr.trim()
            ),
        ))
    }

    /// Whether the engine runs, from the one question that never changes it. The commands that must not start or make
    /// the engine again (`pool stop`, `pool gc`, `status`) ask this.
    pub(crate) async fn is_running(&self, ctx: &Ctx) -> Result<bool, Refusal> {
        Ok(self.state(ctx).await? == EngineState::Running)
    }

    fn read_record(&self) -> Option<EngineRecord> {
        let content = std::fs::read(self.settings.lima_engine_record_path()).ok()?;
        let record: EngineRecord = serde_json::from_slice(&content).ok()?;
        (record.schema_version == SCHEMA_VERSION && record.instance == LIMA_ENGINE_INSTANCE).then_some(record)
    }

    fn write_record(&self, digest: &str) -> Result<(), Refusal> {
        write_json_line(
            &self.settings.lima_engine_record_path(),
            &EngineRecord {
                schema_version: SCHEMA_VERSION,
                template_digest: digest.to_owned(),
                instance: LIMA_ENGINE_INSTANCE.to_owned(),
            },
            "the Lima engine record",
        )
    }

    // --- the lifecycle -----------------------------------------------------------------------------------

    /// Brings the engine VM up and waits for its forwarded Docker socket. `authorized` is the lease of the caller, as
    /// for the container reconcile: its own worker does not stop a recreate.
    ///
    /// A Lima home too long for the forwarded socket is refused first, `lima_home_too_long`. Then the steps, in order,
    /// under the engine lock:
    ///
    /// 1. Render the template for this invocation and compare its digest with the record.
    /// 2. An engine of another digest, or of no record, is deleted. A worker of the pool that another holder leases
    ///    refuses that with `worker_leased`, because the delete takes every container of the engine with it.
    /// 3. An absent engine is created from the template, and a stopped one is started. A socket file left from an
    ///    earlier start is removed first. The output of `limactl start` goes to [`Config::lima_engine_log_path`].
    /// 4. The start waits for the host socket. The budget covers `limactl start` (its `--timeout`) and the wait for
    ///    the socket. It is `AIR_VM_BOOT_TIMEOUT`, and at least [`FIRST_START_BUDGET`] when the start creates the VM.
    ///
    /// A failed start is `engine_start_failed`, and it names the log and the budget that applied. A `Broken` engine of
    /// the current digest is `engine_unusable`, and `pool recycle all` makes it again.
    pub(crate) async fn ensure_running(&self, ctx: &Ctx, authorized: Option<&Lease>) -> Result<(), Refusal> {
        self.settings.require_short_lima_socket()?;
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        let rendered = render_template(&EngineInputs::of(&self.settings)?);
        let digest = template_digest(&rendered);
        let _held = self.lock(ctx).await?;
        let mut state = self.state(ctx).await?;
        let current = self.read_record().is_some_and(|record| record.template_digest == digest);
        if state != EngineState::Absent && !current {
            self.require_no_lease("make the Docker engine again", authorized)?;
            self.note(format!(
                "the Lima engine {LIMA_ENGINE_INSTANCE} does not declare the current home, sizes and images, or this \
                 controller did not create it; creating it again"
            ));
            self.delete_under_lock(ctx).await?;
            state = EngineState::Absent;
        }
        let budget = StartBudget::of(&self.settings, state == EngineState::Absent);
        // One wait for the start and the socket: the socket gets what the start left of the budget.
        let mut wait = Poll::start(&SystemClock, budget.duration, Backoff::fixed(SOCKET_POLL));
        let timeout = format!("--timeout={}s", budget.duration.as_secs());
        match state {
            EngineState::Running => return self.wait_for_socket(ctx, &mut wait, budget).await,
            EngineState::Stopped => {
                self.start(ctx, &["start", "--tty=false", &timeout, LIMA_ENGINE_INSTANCE], budget)
                    .await?;
            }
            EngineState::Absent => {
                let template = self.settings.lima_template_path();
                avl_base::fs::write_atomically(&template, rendered.as_bytes(), 0o600)?;
                let template = template.to_string_lossy().into_owned();
                self.note(format!(
                    "creating the Lima engine {LIMA_ENGINE_INSTANCE}; the first start downloads the Ubuntu image and \
                     installs Docker, and takes minutes (log: {})",
                    self.settings.lima_engine_log_path().display()
                ));
                self.start(
                    ctx,
                    &["start", "--tty=false", &timeout, "--name", LIMA_ENGINE_INSTANCE, &template],
                    budget,
                )
                .await?;
                self.write_record(&digest)?;
            }
            EngineState::Other(word) => {
                return Err(Refusal::new(
                    "engine_unusable",
                    Exit::FAILURE,
                    format!(
                        "the Lima engine {LIMA_ENGINE_INSTANCE} is {word}; `pool recycle all` deletes it and makes it \
                         again, and `limactl` under LIMA_HOME={} shows it",
                        self.settings.lima_home.display()
                    ),
                ));
            }
        }
        self.wait_for_socket(ctx, &mut wait, budget).await
    }

    /// `limactl stop` of a running engine, under the engine lock. Answers the state after the call.
    pub(crate) async fn stop(&self, ctx: &Ctx) -> Result<EngineState, Refusal> {
        let _held = self.lock(ctx).await?;
        let state = self.state(ctx).await?;
        if state != EngineState::Running {
            return Ok(state);
        }
        let argv = self.command(ctx, &["stop", LIMA_ENGINE_INSTANCE]).await?;
        self.runner.checked(ctx, &argv, &SpawnOptions::within(LIMA_STOP_TIMEOUT)).await?;
        Ok(EngineState::Stopped)
    }

    /// `limactl delete --force` of the engine, running or not, and its record, under the engine lock. Every container
    /// and volume of the engine goes with it.
    pub(crate) async fn delete(&self, ctx: &Ctx) -> Result<(), Refusal> {
        let _held = self.lock(ctx).await?;
        self.delete_under_lock(ctx).await
    }

    async fn delete_under_lock(&self, ctx: &Ctx) -> Result<(), Refusal> {
        if self.state(ctx).await? != EngineState::Absent {
            let argv = self.command(ctx, &["delete", "--force", LIMA_ENGINE_INSTANCE]).await?;
            self.runner.checked(ctx, &argv, &SpawnOptions::within(LIMA_DELETE_TIMEOUT)).await?;
        }
        remove_if_present(&self.settings.lima_engine_record_path())
    }

    /// Refuses when a worker of the pool holds a lease of another process: deleting the engine deletes every container
    /// and volume in it, that worker's too, whether its container runs or not.
    ///
    /// The rule: a lease of this process does not count. That is `authorized`, the caller's own, and every lease the
    /// invocation holds ([`HeldLeases`]): the sibling bodies of one `shard` or `flake` hold one lease each. Skipping
    /// them is safe, because the recreate runs under the engine lock and reads the state again. The first body makes
    /// the engine again before any body has started its container, and the next body finds the digest current. A lease
    /// of another process is a session whose tests may run in that container, and it still refuses.
    ///
    /// The caller holds at most the lifecycle lock of one worker, so a lease that starts after this read is not seen.
    /// A recreate is rare, because it follows a change of the home, the sizes or the image pins.
    pub(crate) fn require_no_lease(&self, operation: &str, authorized: Option<&Lease>) -> Result<(), Refusal> {
        for worker in &self.settings.workers {
            let Some(held) = read_lease(&self.settings.lease_path(worker))? else {
                continue;
            };
            if lease_authorizes(authorized, &held) || self.held.contains(&held) {
                continue;
            }
            return Err(Refusal::new(
                "worker_leased",
                Exit::TEMP_FAIL,
                format!(
                    "refusing to {operation}: {worker} holds a lease of another process, and deleting the engine \
                     deletes that worker's container and volume. Wait for that lease to end, or release it, then run \
                     the command again"
                ),
            ));
        }
        Ok(())
    }

    async fn start(&self, ctx: &Ctx, arguments: &[&str], budget: StartBudget) -> Result<(), Refusal> {
        let log = self.settings.lima_engine_log_path();
        remove_if_present(&log)?;
        // A socket file of an earlier start would pass the wait before the host agent forwards the new one.
        remove_if_present(&self.socket_path())?;
        let argv = self.command(ctx, arguments).await?;
        self.runner
            .checked_to_file(ctx, &argv, &log, &log_options(budget.duration + START_GRACE))
            .await
            .map_err(|error| match error {
                ProcError::Exited { refusal, .. } => engine_start_failed(format!(
                    "{}; the engine log is {}; {}",
                    refusal.message,
                    log.display(),
                    budget.describe()
                )),
                other => other.into(),
            })
            .map(drop)
    }

    /// Waits for the forwarded socket until `wait` spends the start budget. `limactl start` returns when the probes
    /// pass, and the host agent forwards the socket a moment later.
    async fn wait_for_socket(&self, ctx: &Ctx, wait: &mut Poll<'_>, budget: StartBudget) -> Result<(), Refusal> {
        let socket = self.socket_path();
        loop {
            if socket.exists() {
                return Ok(());
            }
            if !wait.pause(ctx).await {
                if ctx.is_cancelled() {
                    return Err(Refusal::new(
                        "worker_interrupted",
                        Exit::SOFTWARE,
                        format!("an interrupt stopped the wait for the Lima engine {LIMA_ENGINE_INSTANCE}"),
                    ));
                }
                return Err(engine_start_failed(format!(
                    "the Lima engine {LIMA_ENGINE_INSTANCE} runs, and its Docker socket {} did not appear; the engine \
                     log is {}; {}",
                    socket.display(),
                    self.settings.lima_engine_log_path().display(),
                    budget.describe()
                )));
            }
        }
    }

    fn note(&self, message: String) {
        self.reporter.note(message, None);
    }
}

fn lima_missing(message: String) -> Refusal {
    Refusal::new("lima_missing", Exit::UNAVAILABLE, message)
}

fn engine_start_failed(message: String) -> Refusal {
    Refusal::new("engine_start_failed", Exit::UNAVAILABLE, message)
}
