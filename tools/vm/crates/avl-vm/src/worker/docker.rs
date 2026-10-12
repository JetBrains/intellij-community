//! The Docker backend: the `docker` CLI against containers the controller creates and owns by name.
//!
//! A Docker worker is the Linux guest, in a container. The guest agent, the parity layout, the guest paths and the
//! relay over the exec channel (ADR 0182) are those of a VM worker. What differs is how the worker is made and
//! reached:
//!
//! - **the image** is built from `community/tools/vm/docker/Dockerfile` and its entrypoint `air-display`, both embedded here.
//!   The tag is a digest of what the image is built from ([`image_tag_for`]), so a change to the Dockerfile, the
//!   entrypoint, the base pin or [`GUEST_PACKAGES`] builds a new image, and no stale image can answer for a new
//!   tree. The image installs the packages and the pinned Node, and its entrypoint starts the display, so the boot
//!   only installs the agent and runs `validate-guest` ([`validate_argv`]). Before a build, the controller pulls the
//!   tag from `AIR_VM_DOCKER_REGISTRY` (ADR 0184): the tag names the same bytes wherever the image was built, and the
//!   image's `org.opencontainers.image.revision` label carries the tag digest, so a pulled image that does not say
//!   the digest is dropped and built instead.
//! - **the container** is made by `docker create` with the shares as read-only bind mounts and `WorkerData` in a
//!   named volume. The shares are arguments of the create, as they are arguments of `tart run` on Tart, so a
//!   share-set change makes the container again. The create arguments are recorded ([`CreateRecord`]), and a record
//!   that differs from the current arguments is how that change is seen. The volume survives a recreate, so the
//!   staged daemon runtime, the download cache and the guest agent stay.
//! - **liveness** is `docker inspect`. There is no process of the controller's to identify, as there is none on
//!   Parallels: the container is the engine's, found by name.
//!
//! Smaller than the Tart backend on purpose: there is no seal, no provenance and no process identity, because a
//! container is made from an image this controller built from files in this checkout.
//!
//! # A bind mount serves fresh bytes within a second
//!
//! Measured on 2026-09-29 under OrbStack (Docker Engine 29.4, linux/arm64): a bind mount appears in the container
//! as `virtiofs ro,relatime`. After the host replaces a file by a rename, which is what Bazel does to its outputs,
//! `cat` in the container returns the new bytes at once, `stat` answers the old size for about a second, and `open`
//! never fails with ENOENT.
//!
//! Measured on 2026-09-30 on the Lima engine (Lima 2.2.0, vz, virtiofs, kernel 6.8, `docker.io` 29.1.3): a path the
//! guest looked up in the second before the rename keeps a dead node, so `open` fails with ENOENT and `stat` answers
//! the old size. The dead node clears by itself in under a second, in the VM and in the container.
//!
//! So on both engines the share refresh is a short settle, not a remount (see `Manager::refresh_shares`).

use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::Duration;

use avl_base::config::{docker_buildx_label, docker_cli_label};
use avl_base::{Config, Exit, GuestArch, OrRefuse, Refusal, Reporter, SCHEMA_VERSION, Scope};
use avl_host_sys::fs::real_path;
use avl_host_sys::guest::share_mount_path;
use avl_host_sys::lock::LockManager;
use avl_host_sys::share::{SHARE_MODE, SharedFolder, shares};
use avl_host_sys::{Captured, Ctx, ProbeExit, ProbeOutput, ProcError, Runner, SpawnOptions, probe_unanswered};
use serde::{Deserialize, Serialize};
use sha2::{Digest as _, Sha256};
use tokio::sync::OnceCell;

use crate::worker::hypervisor::unsupported;
use crate::worker::lima::Lima;
use crate::worker::pin::{PinnedTool, PinnedTools};
use crate::worker::secure::write_json_line;
use crate::worker::worker::{HeldLeases, Lease};

/// The timeout of `docker version`, the gate that asks the engine. An engine that answers does so at once, and one
/// that does not answer within it is `docker_missing`, which a caller retries against.
const DOCKER_VERSION_TIMEOUT: Duration = Duration::from_mins(1);

/// The timeout of a query or a metadata change of the engine: `inspect`, `image inspect`, `logs --tail` and `tag`.
/// The engine answers each at once.
const DOCKER_QUERY_TIMEOUT: Duration = Duration::from_mins(1);

/// The timeout of `docker create`. The engine makes the container and its volume from an image it holds.
const DOCKER_CREATE_TIMEOUT: Duration = Duration::from_mins(2);

/// The timeout of `docker start`. The engine starts the entrypoint, and the boot poll then waits for the display
/// under the boot budget.
const DOCKER_START_TIMEOUT: Duration = Duration::from_mins(2);

/// The timeout of `docker stop --time 10`, which kills the container ten seconds after its SIGTERM.
const DOCKER_STOP_TIMEOUT: Duration = Duration::from_mins(1);

/// The timeout of a `docker rm` or a `docker image rm`. The engine removes the writable layer of a container, or the
/// layers of an image.
const DOCKER_REMOVE_TIMEOUT: Duration = Duration::from_mins(5);

/// The timeout of `docker volume rm`. The volume holds what the worker staged: the daemon runtimes and the caches.
const DOCKER_VOLUME_REMOVE_TIMEOUT: Duration = Duration::from_mins(10);

/// The timeout of `docker build`. The first build installs the guest packages over the base image and takes minutes.
const DOCKER_BUILD_TIMEOUT: Duration = Duration::from_hours(1);

/// The timeout of `docker pull` of the published image, which downloads its layers from the registry.
const DOCKER_PULL_TIMEOUT: Duration = Duration::from_mins(30);

/// The timeout of the publish. It builds the image for each published platform, the other one under emulation, and
/// pushes them.
const DOCKER_PUBLISH_TIMEOUT: Duration = Duration::from_hours(2);

#[cfg(test)]
#[cfg(unix)]
mod pin_tests;
#[cfg(test)]
mod tests;

/// The oldest Docker CLI with the `--format` spellings the backend uses. The pinned CLI of `docker.MODULE.bazel` is
/// newer, and a test keeps it so. The floor is not a gate: a `DOCKER_BIN` override is the operator's choice. The pin
/// tests that read it are Unix only.
#[cfg(all(test, unix))]
pub(crate) const MINIMUM_DOCKER_CLI_VERSION: &str = "29.0.0";

/// The oldest `docker-buildx` plugin the image build and the publish use. The pinned plugin of `docker.MODULE.bazel` is
/// newer, and a test keeps it so. The pin tests that read it are Unix only.
#[cfg(all(test, unix))]
pub(crate) const MINIMUM_BUILDX_VERSION: &str = "0.17.0";

/// The image definition, embedded. Four levels up from this source file (`worker`, `src`, `avl-vm`, `crates`) is the
/// workspace root; Bazel names the file in `compile_data`.
pub(crate) const DOCKERFILE: &str = include_str!("../../../../docker/Dockerfile");

/// The image's entrypoint, embedded beside the Dockerfile and copied into the build context.
pub(crate) const ENTRYPOINT: &str = include_str!("../../../../docker/air-display");

/// The packages a worker needs beyond the base image. The image installs them at build time.
///
/// `x11-utils` is not decoration: `XorgWindowManagerHandler` shells out to `xprop` to decide whether a window
/// manager is registered, and reads its absence as no window manager at all.
///
/// **No Node here, and no `npm`.** The lane's tests need both, and the base does not supply them in a usable form.
/// Ubuntu 26.04 has `nodejs` 22 (the `apt-cache policy` candidate on 2026-09-29). That is below `NODE_MAJOR`, and a
/// current `@openai/codex` refuses an older major. So the Dockerfile installs a checksum-pinned Node archive from
/// nodejs.org, which carries `npm` with it.
///
/// The second group is what the IDE's *own* native libraries link against, which a server image does not carry:
/// the JBR ships the `.so` files, not their dependencies. Measured with `ldd` on a Linux worker (2026-08-19):
/// `libEGL.so.1` is the one Skiko needs, and the other five are what `libcef.so` and `libjcef.so` need. Their
/// absence is quiet in the worst way. `Failed to preload Skiko` is one SEVERE line in `idea.log`, and the lane keeps
/// running. So add to this list rather than letting a guest discover it.
///
/// `t64` suffixes are the 64-bit-`time_t` rename of Ubuntu 24.04, and Ubuntu 26.04 keeps them. On 2026-09-29,
/// `apt-cache policy` resolved every name here in an arm64 container of the Docker base (26.04). A base-image bump
/// is a reason to re-check every name here.
pub(crate) const GUEST_PACKAGES: &[&str] = &[
    "xvfb",
    "fluxbox",
    "x11-utils",
    // Skiko, which the IDE preloads for Compose rendering.
    "libegl1",
    // The JBR's own GTK lookup, which otherwise prints "Looking for GTK3 library... Not found." on every launch.
    "libgtk-3-0t64",
    // JCEF.
    "libxdamage1",
    "libxfixes3",
    "libasound2t64",
    "libatk1.0-0t64",
    "libatk-bridge2.0-0t64",
    "libatspi2.0-0t64",
    // `libcef.so` and `libjcef.so` link NSS: `libnss3.so`, `libnssutil3.so`, `libsmime3.so` and `libnspr4.so`, all
    // of which this one package carries. `validate-guest` refused a worker without it on 2026-09-29.
    "libnss3",
    // The merge-conflict and worktree scenarios spawn `git` from the lane IDE. The base does not ship it, and five
    // `ui` scenarios failed without it on 2026-09-30.
    "git",
    // No video encoder: `air-trace-record` encodes the per-scenario video itself.
];

/// What the guest agent's `validate-guest` verb is told: the display, and the runtime root whose shared objects it
/// runs `ldd` over.
///
/// Neither is the package list: the validator reads what the guest *has*, and one handed the list would need a
/// soname-to-package mapping to use it. The root is [`Config::guest_runtime_root`], where the JBR and JCEF are, not
/// the IDE root, where nothing is staged and the sweep would find nothing on every boot.
pub(crate) fn validate_argv(settings: &Config) -> Vec<String> {
    vec![settings.guest_display.clone(), settings.guest_runtime_root()]
}

/// The build argument that carries [`GUEST_PACKAGES`]. The Dockerfile must read it, and a unit test holds it to that.
pub(crate) const PACKAGES_BUILD_ARG: &str = "AIR_GUEST_PACKAGES";

/// The build argument that carries the pinned base image.
pub(crate) const BASE_BUILD_ARG: &str = "AIR_BASE_IMAGE";

/// The build argument that carries the tag digest into [`REVISION_LABEL`].
pub(crate) const REVISION_BUILD_ARG: &str = "AIR_IMAGE_REVISION";

/// The OCI label that holds the tag digest. A pulled image is checked against it before a worker runs on it.
pub(crate) const REVISION_LABEL: &str = "org.opencontainers.image.revision";

/// The platforms a publish builds the tag for, as one image index: an arm64 host and an x86_64 host. The local build
/// is for the engine's own platform only; the publish builds both, so a pull on either kind of host finds its half
/// under the one content tag.
pub(crate) const PUBLISHED_PLATFORMS: [&str; 2] = ["linux/arm64", "linux/amd64"];

/// Whether an engine platform, as `docker version` spells `{{.Server.Os}}/{{.Server.Arch}}`, runs `arch`
/// containers natively. An engine may spell the same architecture the OCI way (`arm64`, `amd64`) or the `uname -m`
/// way (`aarch64`, `x86_64`); both are accepted. Any other platform is emulated or not Linux, and the guest agent
/// and the recorder the controller stages are built for `arch`.
fn runs_natively(arch: GuestArch, platform: &str) -> bool {
    platform
        .strip_prefix("linux/")
        .is_some_and(|engine| engine == arch.oci_arch() || engine == arch.as_str())
}

/// What `docker inspect` says about a worker's container.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum ContainerState {
    /// No container of that name.
    Absent,
    /// Made by `docker create` and never started.
    Created,
    Running,
    /// Stopped, with the exit code of its entrypoint: 0 after a `docker stop`, 1 when `air-display` gave up.
    Exited(i32),
    /// Every other word of the engine (`paused`, `restarting`, `removing`, `dead`), kept as the engine said it.
    Other(String),
}

impl ContainerState {
    /// `docker inspect --type container --format {{.State.Status}}/{{.State.ExitCode}}`, parsed. A status this
    /// controller does not act on keeps its word. An answer that is not a lowercase status word, a `/` and an exit
    /// code is no state, and gives `None`: an empty answer, a cut answer, or a word without a code.
    fn parse(answer: &str) -> Option<Self> {
        let (status, code) = answer.trim().split_once('/')?;
        let code = code.parse::<i32>().ok()?;
        if status.is_empty() || !status.bytes().all(|byte| byte.is_ascii_lowercase()) {
            return None;
        }
        Some(match status {
            "created" => Self::Created,
            "running" => Self::Running,
            "exited" => Self::Exited(code),
            _ => Self::Other(status.to_owned()),
        })
    }

    /// The word `status` prints for the state.
    pub(crate) fn as_str(&self) -> &str {
        match self {
            Self::Absent => "absent",
            Self::Created => "created",
            Self::Running => "running",
            Self::Exited(_) => "exited",
            Self::Other(word) => word,
        }
    }
}

/// The `docker create` argv a worker's container was made with, and the id the engine gave that container:
/// `<worker>/docker-create.json`.
///
/// The Docker twin of the suspended-state record: the next start compares it with the argv it would create, and a
/// difference means the container declares other shares or another image. The id binds the record to one
/// container. A container of the same name made by hand, or by another controller, has another id, so the record
/// cannot vouch for it. A record written before the id was recorded parses with an empty id and vouches for
/// nothing.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct CreateRecord {
    pub schema_version: u32,
    pub worker: String,
    pub argv: Vec<String>,
    #[serde(default)]
    pub container_id: String,
}

/// Where the pool's image came from.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub(crate) enum ImageSource {
    /// Pulled from the registry by its tag, and its revision label said the tag digest.
    Pulled,
    /// Built by this controller from the embedded Dockerfile.
    Built,
}

/// The pool-wide record of the image `ensure_image` last made available: `<runtime root>/docker-image.json`.
///
/// `status` reads it to say `pulled` or `local`, which the engine cannot tell apart: after a pull the remote
/// reference is tagged with the local name, and after a build with a push the local image is tagged with the
/// remote one, so both names exist in both cases. A record about another tag says nothing about this one.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ImageRecord {
    pub schema_version: u32,
    pub tag: String,
    pub source: ImageSource,
}

/// The Docker backend, one of the three a [`Machine`](crate::worker::hypervisor::Machine) is.
///
/// The runner the manager hands over already carries `DOCKER_HOST` when the engine is the controller's Lima VM, and
/// `DOCKER_CONFIG` with the pinned CLI, on every engine. So every `docker` command of the backend and of the guest
/// channel reaches that engine, and the pinned CLI has the pinned `docker-buildx` plugin
/// ([`Docker::install_cli_plugins`]).
pub(crate) struct Docker {
    settings: Arc<Config>,
    runner: Runner,
    reporter: Reporter,
    /// Resolves the pinned CLI and its plugin when [`Config::docker`] is `None`, with the other pinned tools of the
    /// pool.
    pinned: Arc<PinnedTools>,
    /// The executable every Docker command runs: [`Config::docker`], or the pinned CLI once the gate resolved it.
    executable: OnceCell<PathBuf>,
    /// The Lima VM that is the engine, when [`Config::runs_lima_engine`]. `None` for the engine of the host
    /// environment.
    engine: Option<Lima>,
    /// Set once the pinned `docker-buildx` plugin is named in [`Config::docker_config_dir`].
    cli_plugins: OnceCell<()>,
}

impl Docker {
    pub(crate) fn new(
        settings: Arc<Config>,
        runner: Runner,
        reporter: Reporter,
        locks: Arc<LockManager>,
        pinned: Arc<PinnedTools>,
        held: Arc<HeldLeases>,
    ) -> Self {
        let executable = OnceCell::new_with(settings.docker.clone());
        let engine = settings
            .runs_lima_engine()
            .then(|| Lima::new(Arc::clone(&settings), &runner, reporter.clone(), locks, Arc::clone(&pinned), held));
        Self {
            settings,
            runner,
            reporter,
            pinned,
            executable,
            engine,
            cli_plugins: OnceCell::new(),
        }
    }

    /// The controller's Lima engine, or `None` for the engine of the host environment.
    pub(crate) const fn engine(&self) -> Option<&Lima> {
        self.engine.as_ref()
    }

    /// The `docker` executable as the head of a host argv, or the empty string before the gate resolved the pinned
    /// CLI: an argv with an empty head is refused rather than spawned.
    ///
    /// Only for host commands that already passed [`Docker::require_available`]. A guest command line resolves the
    /// head itself ([`Docker::guest_argv`]). The create record leaves the head out, so a record written before the
    /// gate compares the same.
    pub(crate) fn program(&self) -> String {
        self.executable
            .get()
            .map(|path| path.to_string_lossy().into_owned())
            .unwrap_or_default()
    }

    /// The `docker` executable as the head of an argv, resolved now when no gate resolved it yet. Memoized, as on
    /// Tart. It refuses `docker_missing` when neither `DOCKER_BIN` nor Bazel names a CLI.
    pub(crate) async fn resolved_program(&self, ctx: &Ctx) -> Result<String, Refusal> {
        Ok(self.resolve_executable(ctx).await?.to_string_lossy().into_owned())
    }

    /// Writes the pinned CLI's real path into the executable cell when [`Config::docker`] named none: the Tart shape
    /// (`Tart::resolve_executable`). The first resolution in a checkout fetches the archive, about 19 MB.
    async fn resolve_executable(&self, ctx: &Ctx) -> Result<&Path, Refusal> {
        self.executable
            .get_or_try_init(|| async {
                let label = docker_cli_label(self.settings.guest_arch);
                let Some(resolved) = self.pinned.path(ctx, PinnedTool::DockerCli).await? else {
                    return Err(docker_missing(format!(
                        "DOCKER_BIN names no Docker CLI and this command has no Bazel to resolve {label}"
                    )));
                };
                real_path(&resolved).map_err(|error| {
                    docker_missing(format!(
                        "the pinned Docker CLI is not on disk at {}: {error}. Fetch it with `./bazel.cmd cquery {label}`.",
                        resolved.display()
                    ))
                })
            })
            .await
            .map(PathBuf::as_path)
    }

    fn command(&self, arguments: &[&str]) -> Vec<String> {
        std::iter::once(self.program())
            .chain(arguments.iter().map(|argument| (*argument).to_owned()))
            .collect()
    }

    fn note(&self, worker: Option<&str>, message: String) {
        self.reporter.note(message, worker.map(Scope::worker).as_ref());
    }

    // --- what the machine asks of the backend ------------------------------------------------------------

    /// Refuses unless an engine answers and runs containers of the guest's architecture natively. The caller holds no
    /// lease: [`Docker::require_available_for`] with none.
    ///
    /// The worker name is ignored, as on Tart: a slot is a name the controller may use, and the first operation on a
    /// free slot creates its container.
    pub(crate) async fn require_available(&self, ctx: &Ctx, _worker: &str) -> Result<(), Refusal> {
        self.require_available_for(ctx, None).await
    }

    /// The gate for a caller that holds `authorized`, a lease of this pool.
    ///
    /// The CLI is resolved first, then the Lima engine is started when it is the engine, and only then does `docker
    /// version` ask it. The lease is the caller's own, so its worker does not stop the Lima engine from being made
    /// again after a template change ([`Lima::ensure_running`]). A missing binary and an engine that does not answer are
    /// both `docker_missing` at [`Exit::UNAVAILABLE`], which is what a caller retries against: OrbStack that is not
    /// started yet is the common case.
    pub(crate) async fn require_available_for(&self, ctx: &Ctx, authorized: Option<&Lease>) -> Result<(), Refusal> {
        self.resolve_executable(ctx).await?;
        self.install_cli_plugins(ctx).await?;
        if let Some(engine) = &self.engine {
            engine.ensure_running(ctx, authorized).await?;
        }
        let argv = self.command(&["version", "--format", "{{.Server.Os}}/{{.Server.Arch}}"]);
        let captured = match self.runner.capture(ctx, &argv, &SpawnOptions::within(DOCKER_VERSION_TIMEOUT)).await {
            Ok(captured) => captured,
            Err(ProcError::SpawnFailed(refusal)) => {
                return Err(docker_missing(format!(
                    "cannot run {}: {}; {}",
                    self.program(),
                    refusal.message,
                    self.install_remedy()
                )));
            }
            Err(ProcError::TimedOut { timeout, .. }) => {
                return Err(docker_missing(format!(
                    "{} version did not answer within {} s; {}",
                    self.program(),
                    timeout.as_secs(),
                    self.engine_remedy()
                )));
            }
            Err(other) => return Err(other.into()),
        };
        // A CLI that cannot run is a missing CLI here. A CLI that a signal ended, or that printed no
        // `<os>/<arch>`, gave no answer, and an empty answer is no other architecture.
        if captured.probe_exit() == ProbeExit::Killed {
            return Err(probe_unanswered(&argv, &captured, ProbeOutput::Quoted));
        }
        if captured.exit_code != 0 {
            return Err(docker_missing(format!(
                "{} version exited with {}: {}; {}",
                self.program(),
                captured.exit_code,
                captured.stderr.trim(),
                self.engine_remedy()
            )));
        }
        let platform = captured.stdout.trim();
        if !platform
            .split_once('/')
            .is_some_and(|(os, arch)| !os.is_empty() && !arch.is_empty())
        {
            return Err(probe_unanswered(&argv, &captured, ProbeOutput::Quoted));
        }
        let arch = self.settings.guest_arch;
        if runs_natively(arch, platform) {
            return Ok(());
        }
        Err(unsupported(format!(
            "the Docker engine runs {platform:?} containers; a Docker worker runs this host's own architecture, \
             linux/{}, because the guest agent and the trace recorder are built for it",
            arch.oci_arch()
        )))
    }

    /// `docker exec [-i] <worker> <argv…>`.
    ///
    /// `-i` keeps stdin open and is passed exactly when the caller has stdin, as Tart's `-i` is. Never `-t`: a tty
    /// would rewrite the relay's bytes and the file writes that `tee` carries. `docker exec` lands as root, so the
    /// `sudo -H -u admin` prefix every guest step already has applies unchanged. The head is declared on the
    /// timeline, so phase timing counts the spawn as a guest call.
    ///
    /// The head comes from [`Docker::resolved_program`], not from the gate: a verb that skipped the gate still spawns
    /// the pinned CLI.
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

    /// Whether the engine runs and answers, asked without a change to the engine: the question of `pool stop`, `pool
    /// gc` and the engine stop, which must never start the Lima engine or make it again.
    ///
    /// The Lima engine answers from `limactl list`, and when it runs the CLI is resolved, so the host commands that
    /// follow have their head. The engine of the host environment passes the whole gate.
    pub(crate) async fn reach_engine(&self, ctx: &Ctx) -> Result<bool, Refusal> {
        let Some(engine) = &self.engine else {
            self.require_available(ctx, "").await?;
            return Ok(true);
        };
        if !engine.is_running(ctx).await? {
            return Ok(false);
        }
        self.resolve_executable(ctx).await?;
        self.install_cli_plugins(ctx).await?;
        Ok(true)
    }

    /// Whether the worker's container is down, asked through [`Docker::reach_engine`] and never with a change to the
    /// engine: the question of a lease release.
    ///
    /// The engine is reached first because a standalone `lease release` is the first command of its process, so no
    /// gate has resolved the CLI that `docker inspect` needs. A Lima engine that does not run holds no running
    /// container, so its worker is down without a `docker` command. A CLI that cannot be resolved is `docker_missing`.
    pub(crate) async fn stopped(&self, ctx: &Ctx, worker: &str) -> Result<bool, Refusal> {
        Ok(!self.reach_engine(ctx).await? || !self.running(ctx, worker).await?)
    }

    /// Names the pinned `docker-buildx` plugin to the pinned CLI, once, on every engine. A CLI that `DOCKER_BIN` names
    /// is the operator's, with its own plugins, and nothing is done for it.
    ///
    /// `<runtime root>/docker-config/config.json` gets `"cliPluginsExtraDirs": ["<directory of docker-buildx>"]`, and
    /// the manager's runner carries `DOCKER_CONFIG=<runtime root>/docker-config`. The key is merged into the file: every
    /// other key stays, such as the `auths` of a `docker login`. A file that is not a JSON object is replaced, and a
    /// note says so.
    ///
    /// The static macOS CLI ships no plugin, and the image build needs BuildKit (`COPY --chmod`), so `docker build`
    /// and the publish need the plugin. The directory is the one Bazel downloaded the plugin to, where the file is
    /// named `docker-buildx`, as the CLI requires.
    ///
    /// The pinned CLI then reads this directory and not `~/.docker/config.json`. So a publish (`AIR_VM_DOCKER_PUSH`)
    /// needs a login into this directory first: `DOCKER_CONFIG=<runtime root>/docker-config docker login <registry>`.
    async fn install_cli_plugins(&self, ctx: &Ctx) -> Result<(), Refusal> {
        if self.settings.docker.is_some() {
            return Ok(());
        }
        self.cli_plugins
            .get_or_try_init(|| async {
                let label = docker_buildx_label(self.settings.guest_arch);
                let Some(plugin) = self.pinned.path(ctx, PinnedTool::DockerBuildx).await? else {
                    return Err(docker_missing(format!(
                        "this command has no Bazel to resolve {label}, the pinned docker-buildx plugin of the pinned \
                         Docker CLI; name another CLI in DOCKER_BIN"
                    )));
                };
                if !plugin.is_file() {
                    return Err(docker_missing(format!(
                        "the pinned docker-buildx plugin is not on disk at {}. Fetch it with `./bazel.cmd cquery \
                         {label}`.",
                        plugin.display()
                    )));
                }
                let directory = plugin.parent().unwrap_or(Path::new("")).to_owned();
                let config = self.settings.docker_config_dir();
                std::fs::create_dir_all(&config).or_refuse("state_write_failed", Exit::FAILURE, || {
                    format!("cannot create the Docker configuration directory {}", config.display())
                })?;
                let path = config.join("config.json");
                let existing = std::fs::read(&path).ok();
                let (merged, replaced) = with_cli_plugin_directory(existing.as_deref(), &directory);
                if replaced {
                    self.note(
                        None,
                        format!(
                            "{} is not a JSON object; replacing it with one that names the pinned docker-buildx",
                            path.display()
                        ),
                    );
                }
                write_json_line(&path, &merged, "the Docker CLI configuration")
            })
            .await
            .map(drop)
    }

    /// What `docker_missing` tells the operator to do when the CLI does not run. The pinned CLI names its label, as
    /// `tart_missing` does. A Linux or a Windows host has no pinned CLI and no Lima engine, so there the remedy is an
    /// installation.
    fn install_remedy(&self) -> String {
        if self.settings.docker.is_none() {
            let label = docker_cli_label(self.settings.guest_arch);
            return format!(
                "the pinned CLI is {label}; fetch it with `./bazel.cmd cquery {label}`, or name another CLI in \
                 DOCKER_BIN"
            );
        }
        "install a Docker engine (OrbStack, Docker Desktop, or Docker Engine on Linux) or name its CLI in DOCKER_BIN".to_owned()
    }

    /// Whether the worker's container runs. A container that does not exist is not running, and is no error.
    ///
    /// Read from the status word and not from `.State.Running`: the engine says `true` there for a paused
    /// container, which answers no `docker exec`. A paused container is not running for this controller.
    pub(crate) async fn running(&self, ctx: &Ctx, worker: &str) -> Result<bool, Refusal> {
        Ok(self.state(ctx, worker).await? == ContainerState::Running)
    }

    /// The full id of the worker's container, or `None` when the engine has no container of that name.
    pub(crate) async fn container_id(&self, ctx: &Ctx, worker: &str) -> Result<Option<String>, Refusal> {
        Ok(self
            .inspect(ctx, worker, "{{.Id}}")
            .await?
            .map(|captured| captured.stdout.trim().to_owned()))
    }

    /// What the engine says about the worker's container: [`ContainerState::Absent`] when the engine has no container
    /// of that name, which [`Docker::inspect`] tells. An answer that is not a `<status>/<exit code>` is the refusal
    /// `probe_unanswered`, never a state.
    ///
    /// Every `inspect` of a worker says `--type container`. Without it the engine answers for any object of that
    /// name, and an image, a volume or a network called like the worker would read as a container.
    pub(crate) async fn state(&self, ctx: &Ctx, worker: &str) -> Result<ContainerState, Refusal> {
        const FORMAT: &str = "{{.State.Status}}/{{.State.ExitCode}}";
        let Some(captured) = self.inspect(ctx, worker, FORMAT).await? else {
            return Ok(ContainerState::Absent);
        };
        ContainerState::parse(&captured.stdout)
            .ok_or_else(|| probe_unanswered(&self.inspect_argv(worker, FORMAT), &captured, ProbeOutput::Quoted))
    }

    /// `docker inspect --type container --format <format>` of the worker's container: the capture of an answer, or
    /// `None` when the engine has no container of that name. [`Docker::require_available`] already proved that the
    /// engine answers.
    ///
    /// The engine tells a missing name with its own nonzero exit ([`ProbeExit::Negative`]). An exit 0 with nothing
    /// printed, a CLI that cannot run, and a CLI that a signal ended are the refusal `probe_unanswered`. A timeout is
    /// the refusal of the runner.
    async fn inspect(&self, ctx: &Ctx, worker: &str, format: &str) -> Result<Option<Captured>, Refusal> {
        let argv = self.inspect_argv(worker, format);
        let captured = self.runner.capture(ctx, &argv, &SpawnOptions::within(DOCKER_QUERY_TIMEOUT)).await?;
        match captured.probe_exit() {
            ProbeExit::Answered if !captured.stdout.trim().is_empty() => Ok(Some(captured)),
            ProbeExit::Negative => Ok(None),
            ProbeExit::Answered | ProbeExit::CannotRun | ProbeExit::Killed => Err(probe_unanswered(&argv, &captured, ProbeOutput::Quoted)),
        }
    }

    fn inspect_argv(&self, worker: &str, format: &str) -> Vec<String> {
        self.command(&["inspect", "--type", "container", "--format", format, worker])
    }

    // --- the image ---------------------------------------------------------------------------------------

    /// The image every worker of this checkout runs: `<AIR_VM_DOCKER_IMAGE>:<12 hex digits>`.
    pub(crate) fn image_tag(&self) -> String {
        image_tag_for(
            &self.settings.docker_image,
            DOCKERFILE,
            ENTRYPOINT,
            &self.settings.docker_base_image,
            GUEST_PACKAGES,
        )
    }

    /// `docker build` of the image tag for the engine's own platform, with the pinned base, the package list and
    /// the tag digest as build arguments.
    pub(crate) fn build_argv(&self, tag: &str, context: &Path) -> Vec<String> {
        let mut argv = self.command(&["build", "--tag", tag]);
        argv.extend(self.build_arguments(tag));
        argv.push(context.to_string_lossy().into_owned());
        argv
    }

    /// `docker buildx build --push` of the registry reference for every platform of [`PUBLISHED_PLATFORMS`], from
    /// the same context and build arguments as the local build. BuildKit shares the layers of the engine's own
    /// platform with the local build, and builds the other platform under the engine's emulation. No provenance
    /// attestation: it adds an `unknown/unknown` manifest per platform to the index, which older clients list as a
    /// platform, and the tag digest is the provenance this controller reads.
    pub(crate) fn publish_argv(&self, tag: &str, remote: &str, context: &Path) -> Vec<String> {
        let mut argv = self.command(&[
            "buildx",
            "build",
            "--platform",
            &PUBLISHED_PLATFORMS.join(","),
            "--provenance=false",
            "--push",
            "--tag",
            remote,
        ]);
        argv.extend(self.build_arguments(tag));
        argv.push(context.to_string_lossy().into_owned());
        argv
    }

    /// The `--build-arg` pairs every build of the tag passes: the base pin, the package list and the tag digest.
    fn build_arguments(&self, tag: &str) -> Vec<String> {
        vec![
            "--build-arg".to_owned(),
            format!("{BASE_BUILD_ARG}={}", self.settings.docker_base_image),
            "--build-arg".to_owned(),
            format!("{PACKAGES_BUILD_ARG}={}", GUEST_PACKAGES.join(" ")),
            "--build-arg".to_owned(),
            format!("{REVISION_BUILD_ARG}={}", tag_digest(tag)),
        ]
    }

    /// The registry reference of the image tag, `<registry>/<repository>:<digest>`, or `None` when the registry
    /// is off. The digest is the whole pin: the same tag names the same bytes at the registry and on the engine.
    pub(crate) fn remote_reference(&self, tag: &str) -> Option<String> {
        self.settings
            .docker_registry
            .as_ref()
            .map(|registry| format!("{registry}/{}:{}", self.settings.docker_image, tag_digest(tag)))
    }

    /// `docker pull` of a registry reference.
    pub(crate) fn pull_argv(&self, remote: &str) -> Vec<String> {
        self.command(&["pull", remote])
    }

    /// `docker image inspect` that prints the revision label of an image, and nothing else.
    pub(crate) fn revision_argv(&self, reference: &str) -> Vec<String> {
        self.command(&[
            "image",
            "inspect",
            "--format",
            &format!("{{{{index .Config.Labels {REVISION_LABEL:?}}}}}"),
            reference,
        ])
    }

    /// The record of where the pool's image came from, or `None` for every kind of wrong: absent, unparseable,
    /// another schema.
    pub(crate) fn read_image_record(&self) -> Option<ImageRecord> {
        let content = std::fs::read(self.settings.docker_image_record_path()).ok()?;
        let record: ImageRecord = serde_json::from_slice(&content).ok()?;
        (record.schema_version == SCHEMA_VERSION).then_some(record)
    }

    fn write_image_record(&self, tag: &str, source: ImageSource) -> Result<(), Refusal> {
        write_json_line(
            &self.settings.docker_image_record_path(),
            &ImageRecord {
                schema_version: SCHEMA_VERSION,
                tag: tag.to_owned(),
                source,
            },
            "the image record",
        )
    }

    /// Tells whether the engine has the image `tag`.
    ///
    /// `docker image inspect` exits 0 for a known image and 1 for an unknown one. Every exit of the CLI itself that is
    /// not 0 counts as absent ([`ProbeExit::Negative`]), because the next `docker build` then names the real problem
    /// in its log. A missing binary is still a refusal, as for every other Docker command, and a CLI that cannot run
    /// or that a signal ended is the refusal `probe_unanswered`: it did not say that the image is absent.
    pub(crate) async fn image_present(&self, ctx: &Ctx, tag: &str) -> Result<bool, Refusal> {
        let argv = self.command(&["image", "inspect", tag]);
        let inspected = self.runner.capture(ctx, &argv, &SpawnOptions::within(DOCKER_QUERY_TIMEOUT)).await?;
        match inspected.probe_exit() {
            ProbeExit::Answered => Ok(true),
            ProbeExit::Negative => Ok(false),
            ProbeExit::CannotRun | ProbeExit::Killed => Err(probe_unanswered(&argv, &inspected, ProbeOutput::Quoted)),
        }
    }

    /// Makes the image tag available on the engine and answers it: present, else pulled, else built.
    ///
    /// A pull is tried whenever a registry is configured, and a pull that fails, or hands over an image whose
    /// revision label is not the tag digest, is a note and a build, not a refusal: an unreachable registry must not
    /// stop a start that can build. The first build installs the guest packages over the base and takes minutes;
    /// after that `image inspect` answers and nothing is pulled or built. Every log is kept on failure, because a
    /// failure is when someone reads it.
    ///
    /// With `AIR_VM_DOCKER_PUSH` the operator wants the registry to hold what this checkout builds, so the
    /// controller builds whether or not the engine has the tag, never pulls, and publishes the tag for every
    /// platform of [`PUBLISHED_PLATFORMS`] after the build.
    pub(crate) async fn ensure_image(&self, ctx: &Ctx) -> Result<String, Refusal> {
        let tag = self.image_tag();
        if !self.settings.docker_push {
            if self.image_present(ctx, &tag).await? {
                return Ok(tag);
            }
            if let Some(remote) = self.remote_reference(&tag)
                && self.pull(ctx, &tag, &remote).await?
            {
                self.write_image_record(&tag, ImageSource::Pulled)?;
                return Ok(tag);
            }
        }
        let context = self.write_build_context(&tag)?;
        let log = self.settings.docker_build_log_path();
        remove_if_present(&log)?;
        self.note(
            None,
            format!(
                "building the Docker image {tag}; the first build installs the guest packages and takes minutes \
                 (log: {})",
                log.display()
            ),
        );
        self.runner
            .checked_to_file(ctx, &self.build_argv(&tag, &context), &log, &log_options(DOCKER_BUILD_TIMEOUT))
            .await
            .map_err(|error| match error {
                ProcError::Exited { refusal, .. } => Refusal::new(
                    "docker_build_failed",
                    refusal.exit,
                    format!("{}; the build log is {}", refusal.message, log.display()),
                ),
                other => other.into(),
            })?;
        self.write_image_record(&tag, ImageSource::Built)?;
        if self.settings.docker_push
            && let Some(remote) = self.remote_reference(&tag)
        {
            self.publish(ctx, &tag, &remote, &context).await?;
        }
        Ok(tag)
    }

    /// Pulls `remote`, checks its revision label against the tag digest, and tags it with the local name. Answers
    /// whether the engine now has the local tag.
    ///
    /// `false` is a pull that failed, or an image that does not say the digest, and both leave a note: the failed
    /// pull names its log, and the mismatch (`docker_image_mismatch`) names both digests and is removed from the
    /// engine, so no later `image inspect` finds it. A refusal is a host problem, a missing CLI or an interrupt.
    async fn pull(&self, ctx: &Ctx, tag: &str, remote: &str) -> Result<bool, Refusal> {
        let log = self.settings.docker_pull_log_path();
        remove_if_present(&log)?;
        self.note(None, format!("pulling the Docker image {remote} (log: {})", log.display()));
        match self
            .runner
            .checked_to_file(ctx, &self.pull_argv(remote), &log, &log_options(DOCKER_PULL_TIMEOUT))
            .await
        {
            Ok(_) => {}
            Err(ProcError::Exited { refusal, .. }) => {
                self.note(
                    None,
                    format!("{}; building the image instead; the pull log is {}", refusal.message, log.display()),
                );
                return Ok(false);
            }
            Err(other) => return Err(other.into()),
        }
        let revision = self
            .runner
            .checked(ctx, &self.revision_argv(remote), &SpawnOptions::within(DOCKER_QUERY_TIMEOUT))
            .await?;
        let digest = tag_digest(tag);
        if revision.stdout.trim() != digest {
            self.note(
                None,
                format!(
                    "docker_image_mismatch: the pulled image {remote} says revision {:?} and the tag digest is \
                     {digest}; removing it and building the image instead",
                    revision.stdout.trim()
                ),
            );
            self.checked(ctx, &["image", "rm", remote], DOCKER_REMOVE_TIMEOUT).await?;
            return Ok(false);
        }
        self.checked(ctx, &["tag", remote, tag], DOCKER_QUERY_TIMEOUT).await?;
        Ok(true)
    }

    /// Builds the tag for every published platform and pushes the index as `remote`. A failure is
    /// `docker_push_failed`: the operator asked for the publish, so a publish that did not happen is not a build
    /// that succeeded.
    async fn publish(&self, ctx: &Ctx, tag: &str, remote: &str, context: &Path) -> Result<(), Refusal> {
        let log = self.settings.docker_push_log_path();
        remove_if_present(&log)?;
        self.note(
            None,
            format!(
                "publishing the Docker image {remote} for {}; the other platform builds under emulation and takes \
                 minutes (log: {})",
                PUBLISHED_PLATFORMS.join(" and "),
                log.display()
            ),
        );
        self.runner
            .checked_to_file(
                ctx,
                &self.publish_argv(tag, remote, context),
                &log,
                &log_options(DOCKER_PUBLISH_TIMEOUT),
            )
            .await
            .map_err(|error| match error {
                ProcError::Exited { refusal, .. } => Refusal::new(
                    "docker_push_failed",
                    refusal.exit,
                    format!("{}; the push log is {}", refusal.message, log.display()),
                ),
                other => other.into(),
            })
            .map(drop)
    }

    /// Writes the Dockerfile and the entrypoint into a fresh build context under the runtime root, one directory per
    /// image digest. Fresh, so a context left by an interrupted build cannot add a file the digest does not cover.
    fn write_build_context(&self, tag: &str) -> Result<PathBuf, Refusal> {
        let context = self.settings.runtime_root.join("docker-context").join(tag_digest(tag));
        let failed = || format!("cannot write the Docker build context {}", context.display());
        match std::fs::remove_dir_all(&context) {
            Ok(()) => {}
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(error) => return Err(state_write_failed(format!("{}: {error}", failed()))),
        }
        std::fs::create_dir_all(&context).or_refuse("state_write_failed", Exit::FAILURE, failed)?;
        std::fs::write(context.join("Dockerfile"), DOCKERFILE).or_refuse("state_write_failed", Exit::FAILURE, failed)?;
        // The Dockerfile sets the mode of the entrypoint with `COPY --chmod`, so the file here needs none. A Windows
        // host has no mode to give it.
        std::fs::write(context.join("air-display"), ENTRYPOINT).or_refuse("state_write_failed", Exit::FAILURE, failed)?;
        Ok(context)
    }

    // --- the container -----------------------------------------------------------------------------------

    /// The `--mount` pairs that bind the read-only shares at the guest paths the parity script expects.
    ///
    /// The third renderer of [`SharedFolder`], beside Tart's `--dir=` and Parallels' `--shf-host-add`. Pure, as on
    /// Tart: the shares are arguments of a container that does not exist yet. `--mount` and not `-v`, because the
    /// `-v` grammar splits at `:`, and a Windows host path starts with `C:`. The `--mount` grammar is one CSV
    /// record of `key=value` fields, so a host path with a `,`, a `"` or a `=` could change the record; it is
    /// refused, and an empty path, which would mean the host paths were never resolved, is refused with it.
    pub(crate) fn share_arguments(shares: &[SharedFolder], settings: &Config) -> Result<Vec<String>, Refusal> {
        let mut arguments = Vec::with_capacity(shares.len() * 2);
        for share in shares {
            let Some(path) = share
                .path
                .to_str()
                .filter(|path| !path.is_empty() && !path.contains([',', '"', '=']))
            else {
                return Err(Refusal::new(
                    "unsafe_share_path",
                    Exit::DATA_ERR,
                    format!(
                        "Docker cannot bind the {} share for {:?}: its --mount grammar is one CSV record, so the \
                         host path must be non-empty and free of ',', '\"' and '='",
                        share.name,
                        share.path.display().to_string()
                    ),
                ));
            };
            let read_only = if share.mode == SHARE_MODE { ",readonly" } else { "" };
            arguments.push("--mount".to_owned());
            arguments.push(format!(
                "type=bind,source={path},target={}{read_only}",
                share_mount_path(settings, &share.name)
            ));
        }
        Ok(arguments)
    }

    /// The named volume that holds a worker's `WorkerData`.
    pub(crate) fn volume_name(worker: &str) -> String {
        format!("air-{worker}-data")
    }

    /// The whole `docker create` argv for one worker.
    ///
    /// `--init` makes Docker's init process pid 1, so `docker stop` is one SIGTERM that it forwards to `air-display`.
    /// `--shm-size 2g` because the IDE's JCEF needs more `/dev/shm` than the engine default of 64 MB; OrbStack
    /// already gives more, and other engines do not. No `--memory`: one container shares the engine VM, and a limit
    /// here would be a second memory budget nothing measures yet. The display is passed so the entrypoint starts
    /// the X server on the display the IDE is told to use.
    pub(crate) fn create_argv(&self, worker: &str) -> Result<Vec<String>, Refusal> {
        let settings = &self.settings;
        let mut argv = self.command(&[
            "create",
            "--name",
            worker,
            "--hostname",
            worker,
            "--init",
            "--shm-size",
            "2g",
            "--ulimit",
            "nofile=65536:65536",
        ]);
        argv.extend(Self::share_arguments(&shares(settings)?, settings)?);
        argv.extend([
            "--mount".to_owned(),
            format!("type=volume,source={},target={}", Self::volume_name(worker), settings.vm_data),
            "-e".to_owned(),
            format!("AIR_VM_DISPLAY={}", settings.guest_display),
        ]);
        if let Some(screen) = &settings.vm_screen {
            argv.extend(["-e".to_owned(), format!("AIR_VM_SCREEN={screen}")]);
        }
        argv.push(self.image_tag());
        Ok(argv)
    }

    /// What a worker's container was created with, or `None` for every kind of wrong: absent, unparseable, another
    /// worker's, another schema. The caller's question is "may I keep this container", and no damaged record may
    /// answer yes.
    pub(crate) fn read_create_record(&self, worker: &str) -> Option<CreateRecord> {
        let content = std::fs::read(self.settings.docker_create_record_path(worker)).ok()?;
        let record: CreateRecord = serde_json::from_slice(&content).ok()?;
        (record.schema_version == SCHEMA_VERSION && record.worker == worker && !record.argv.is_empty()).then_some(record)
    }

    /// Whether the worker's record declares the argv a create would use now: the same shares, the same image tag,
    /// the same display.
    ///
    /// The program is left out of the comparison. `DOCKER_BIN` names the CLI, and `docker` and `/usr/local/bin/docker`
    /// make the same container, so a respelling must not make it again. This reads the record only; it does not ask
    /// the engine which container the record is about ([`Docker::container_is_current`] does).
    pub(crate) fn create_record_is_current(&self, worker: &str) -> Result<bool, Refusal> {
        let current = self.create_argv(worker)?;
        Ok(self
            .read_create_record(worker)
            .is_some_and(|record| record.argv.get(1..) == current.get(1..)))
    }

    /// Whether the container of that name is the one this controller created with the current argv: the record is
    /// current, and the engine's id for the name is the recorded id. The question a start asks before it keeps a
    /// container.
    pub(crate) async fn container_is_current(&self, ctx: &Ctx, worker: &str) -> Result<bool, Refusal> {
        let Some(record) = self.read_create_record(worker) else {
            return Ok(false);
        };
        if record.container_id.is_empty() || !self.create_record_is_current(worker)? {
            return Ok(false);
        }
        Ok(self.container_id(ctx, worker).await?.as_deref() == Some(record.container_id.as_str()))
    }

    /// Creates the worker's container and records the argv it was created with.
    pub(crate) async fn create(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        let argv = self.create_argv(worker)?;
        self.note(Some(worker), format!("creating the container {worker} from {}", self.image_tag()));
        let created = self
            .runner
            .checked(ctx, &argv, &SpawnOptions::within(DOCKER_CREATE_TIMEOUT))
            .await?;
        // `docker create` prints the id of the container it made, and nothing else, on stdout.
        let container_id = created.stdout.trim().to_owned();
        if container_id.is_empty() {
            return Err(Refusal::new(
                "subprocess_failed",
                Exit::FAILURE,
                format!(
                    "{} create {worker} printed no container id, so its record could not name the container",
                    self.program()
                ),
            ));
        }
        write_json_line(
            &self.settings.docker_create_record_path(worker),
            &CreateRecord {
                schema_version: SCHEMA_VERSION,
                worker: worker.to_owned(),
                argv,
                container_id,
            },
            &format!("the create record of {worker}"),
        )
    }

    pub(crate) async fn start(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        self.checked(ctx, &["start", worker], DOCKER_START_TIMEOUT).await
    }

    /// `docker stop --time 10`: a SIGTERM to the entrypoint, then a SIGKILL after ten seconds.
    pub(crate) async fn stop(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        self.checked(ctx, &["stop", "--time", "10", worker], DOCKER_STOP_TIMEOUT).await
    }

    /// Removes the worker's container, running or not, and its create record. The volume stays.
    pub(crate) async fn remove(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        self.checked(ctx, &["rm", "--force", worker], DOCKER_REMOVE_TIMEOUT).await?;
        remove_if_present(&self.settings.docker_create_record_path(worker))
    }

    /// Removes the worker's container only when it does not run, and its create record. The volume stays.
    ///
    /// `docker rm` without `--force` is refused by the engine for a running container. That makes the engine the last
    /// check, after the caller's own: a container that a start brought up after the caller read its state is not
    /// killed.
    pub(crate) async fn remove_stopped(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        self.checked(ctx, &["rm", worker], DOCKER_REMOVE_TIMEOUT).await?;
        remove_if_present(&self.settings.docker_create_record_path(worker))
    }

    /// Removes the worker's volume, which is everything the worker had staged. A missing volume is no error.
    pub(crate) async fn remove_volume(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        let volume = Self::volume_name(worker);
        let captured = self
            .runner
            .capture(
                ctx,
                &self.command(&["volume", "rm", "--force", &volume]),
                &SpawnOptions::within(DOCKER_VOLUME_REMOVE_TIMEOUT),
            )
            .await?;
        if captured.exit_code == 0 {
            return Ok(());
        }
        Err(Refusal::new(
            "subprocess_failed",
            Exit::FAILURE,
            format!(
                "{} volume rm {volume} exited with {}: {}",
                self.program(),
                captured.exit_code,
                captured.stderr.trim()
            ),
        ))
    }

    /// The last lines the container's entrypoint printed, for a refusal about a container that exited. Empty when
    /// the engine does not answer: the refusal is still the refusal.
    ///
    /// The withholding contract of `Guest::checked` keeps guest output out of a refusal, because a guest process can
    /// echo the UI-test bridge token. The container log is exempt from that contract. `docker logs` holds only what
    /// pid 1 and its children write: `docker-init`, `air-display`, Xvfb and fluxbox. The engine never sends the output
    /// of a `docker exec` there, so the IDE, the daemon and every guest step stay out of it. The whole tail is quoted,
    /// and not only the `air-display:` lines, because the Xvfb error is the line that says why the display failed.
    pub(crate) async fn log_tail(&self, ctx: &Ctx, worker: &str) -> String {
        self.runner
            .capture(
                ctx,
                &self.command(&["logs", "--tail", "40", worker]),
                &SpawnOptions::within(DOCKER_QUERY_TIMEOUT),
            )
            .await
            .map(|captured| format!("{}{}", captured.stdout, captured.stderr).trim().to_owned())
            .unwrap_or_default()
    }

    async fn checked(&self, ctx: &Ctx, arguments: &[&str], timeout: Duration) -> Result<(), Refusal> {
        self.runner
            .checked(ctx, &self.command(arguments), &SpawnOptions::within(timeout))
            .await?;
        Ok(())
    }

    /// What a refusal of `docker version` tells the operator to do: look at the Lima engine, or start the engine.
    fn engine_remedy(&self) -> String {
        match &self.engine {
            Some(engine) => format!(
                "the Lima engine does not answer at {}; its log is {}",
                engine.docker_host(),
                self.settings.lima_engine_log_path().display()
            ),
            None => "start the Docker engine".to_owned(),
        }
    }
}

/// A Docker CLI configuration with `cliPluginsExtraDirs` set to `directory`, and every other key of `existing` kept.
/// Answers whether `existing` had to be replaced, because it was present and not a JSON object.
pub(crate) fn with_cli_plugin_directory(existing: Option<&[u8]>, directory: &Path) -> (serde_json::Value, bool) {
    let parsed = existing.map(serde_json::from_slice::<serde_json::Value>);
    let (mut object, replaced) = match parsed {
        None => (serde_json::Map::new(), false),
        Some(Ok(serde_json::Value::Object(object))) => (object, false),
        Some(_) => (serde_json::Map::new(), true),
    };
    object.insert("cliPluginsExtraDirs".to_owned(), serde_json::json!([directory]));
    (serde_json::Value::Object(object), replaced)
}

/// The image tag for a repository and the inputs of the image, as a pure function so a test can vary one input.
///
/// The digest covers every byte the image is built from: the Dockerfile, the entrypoint, the pinned base and the
/// package list. The list is sorted, so a reordered list gives the same tag. A NUL separates the inputs, so text
/// moved from one input to the next gives another digest. Twelve hex digits, as Docker
/// shortens an image id.
pub(crate) fn image_tag_for(repository: &str, dockerfile: &str, entrypoint: &str, base_image: &str, packages: &[&str]) -> String {
    let mut sorted = packages.to_vec();
    sorted.sort_unstable();
    let mut hash = Sha256::new();
    for part in [dockerfile, entrypoint, base_image] {
        hash.update(part.as_bytes());
        hash.update([0]);
    }
    hash.update(sorted.join(" ").as_bytes());
    let digest = hex::encode(hash.finalize());
    format!("{repository}:{}", &digest[..12])
}

/// The digest half of an image tag: what follows the last `:`.
fn tag_digest(tag: &str) -> &str {
    tag.rsplit(':').next().unwrap_or(tag)
}

/// The spawn options of a command whose output is a log: the diagnostics join it, and it stays on failure.
pub(crate) const fn log_options(timeout: Duration) -> SpawnOptions {
    let mut options = SpawnOptions::within(timeout);
    options.merge_stderr_into_file = true;
    options.keep_destination_on_failure = true;
    options
}

/// The refusal for a container in a state the controller does not act on: `paused`, `restarting`, `removing` or
/// `dead`. The word is the engine's, so the operator reads what `docker ps` would say.
pub(crate) fn container_unusable(worker: &str, word: &str) -> Refusal {
    Refusal::new(
        "container_unusable",
        Exit::FAILURE,
        format!(
            "the container {worker} is {word}, and this controller neither uses nor stops a {word} container; \
             `docker unpause {worker}` resumes a paused one, and `pool recycle {worker}` makes any of them again"
        ),
    )
}

fn docker_missing(message: String) -> Refusal {
    Refusal::new("docker_missing", Exit::UNAVAILABLE, message)
}

fn state_write_failed(message: String) -> Refusal {
    Refusal::new("state_write_failed", Exit::FAILURE, message)
}

pub(crate) fn remove_if_present(path: &Path) -> Result<(), Refusal> {
    match std::fs::remove_file(path) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(state_write_failed(format!("cannot remove {}: {error}", path.display()))),
    }
}
