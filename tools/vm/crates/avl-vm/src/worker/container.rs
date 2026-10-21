//! The Apple `container` engine: the engine of the Docker backend on a macOS host of macOS 26 or newer, on Apple
//! silicon, that names no other engine ([`avl_base::DockerEngine::AppleContainer`], the decision
//! `2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend`, and ADR 0224).
//!
//! Apple `container` runs each container in a VM of its own. Its CLI has the shape of the Docker CLI, so the backend
//! keeps its lifecycle and renders each command in the dialect of this CLI (`Dialect` in the Docker module). This
//! module owns what the engine adds: the API server of the login session, the builder VM, and the nameserver.
//!
//! - **One server per login session.** The label `com.apple.container.apiserver` is fixed, so the controller uses the
//!   server that runs, with the default data root of the tool, and never passes `--app-root`. It never runs `system
//!   stop`: that stops every container of the session, also the containers of another tool. A server down after a
//!   reboot is started again with `system start --enable-kernel-install`, under the pool-wide image lock.
//! - **A foreign server.** `system start` exits 0 when another install holds the label. So every gate reads `system
//!   status` and compares the install root of the server with the install root of this CLI, the grandparent of its
//!   real path. A server of another install and of another major version is refused `container_engine_foreign`.
//! - **The nameserver.** The DNS proxy of the engine does not answer on every host. Each build and each container
//!   gets `--dns` ([`AppleContainer::nameserver`]).
//!
//! The engine rule chooses this engine only on a macOS host. The module compiles on every host, so its suite runs
//! over the fake `container` on every Unix host.

use std::net::IpAddr;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;

use avl_base::config::CONTAINER_MACOS_MAJOR;
use avl_base::{Config, Exit, GuestArch, Refusal, Reporter};
use avl_host_sys::lock::{LockGuard, LockManager};
use avl_host_sys::{Captured, Ctx, ProbeExit, ProbeOutput, ProcError, Runner, SpawnOptions, probe_unanswered};
use serde::Deserialize;
use tokio::sync::OnceCell;

use crate::worker::docker::{log_options, named_engine_remedy, remove_if_present};
use crate::worker::hypervisor::unsupported;

#[cfg(test)]
mod tests;

/// The budget of `system start`. The first start downloads the kernel, about 700 MB from GitHub: 108.8 s on
/// 2026-10-09. A start with the kernel present took 0.2 s.
pub(crate) const SYSTEM_START_BUDGET: Duration = Duration::from_mins(15);

/// The timeout of `system status`, of `builder stop` and of `builder delete`. The server answers each at once.
const SYSTEM_QUERY_TIMEOUT: Duration = Duration::from_mins(1);

/// The timeout of `scutil --dns`, which reads the resolver configuration of the host.
const SCUTIL_TIMEOUT: Duration = Duration::from_secs(10);

/// The nameserver when `AIR_VM_DNS` names none and the host has no nameserver outside the loopback.
pub(crate) const FALLBACK_NAMESERVER: &str = "1.1.1.1";

/// The memory of the builder VM, which every build names: the builder of a build without it has 2 GiB.
pub(crate) const BUILDER_MEMORY: &str = "4g";

/// What `container system status --format json` says, the fields the controller reads.
#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
pub(crate) struct SystemStatus {
    /// `running` for a server that answers. A server that is down says `unregistered`, and exits 1.
    #[serde(default)]
    pub status: String,
    #[serde(default)]
    pub paths: StatusPaths,
    #[serde(default)]
    pub client: Version,
    #[serde(default)]
    pub server: Version,
    #[serde(default)]
    pub host: StatusHost,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct StatusPaths {
    #[serde(default)]
    pub app_root: String,
    #[serde(default)]
    pub install_root: String,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
pub(crate) struct Version {
    #[serde(default)]
    pub version: String,
}

#[derive(Clone, Debug, Default, PartialEq, Eq, Deserialize)]
pub(crate) struct StatusHost {
    #[serde(default)]
    pub architecture: String,
}

impl SystemStatus {
    /// Whether the server runs.
    pub(crate) fn running(&self) -> bool {
        self.status == "running"
    }
}

/// The major version of a `major.minor.patch` text, or `None`.
pub(crate) fn major_version(version: &str) -> Option<u32> {
    version.trim().split('.').next()?.parse().ok()
}

/// The first nameserver of `scutil --dns` that is neither a loopback nor an unspecified address. A scoped address
/// (`fe80::1%en0`) does not parse as an address, so it is skipped too. `None` when the host has no such nameserver:
/// a VPN client that answers on `127.0.2.2` is the common case.
pub(crate) fn first_routable_nameserver(scutil: &str) -> Option<IpAddr> {
    scutil.lines().find_map(|line| {
        let (key, value) = line.trim().split_once(" : ")?;
        if !key.starts_with("nameserver[") {
            return None;
        }
        let address: IpAddr = value.trim().parse().ok()?;
        (!address.is_loopback() && !address.is_unspecified()).then_some(address)
    })
}

/// The install root of a CLI: the grandparent of its real path, `<root>/bin/container`. The tool finds its plugins
/// and its server there.
pub(crate) fn install_root_of(program: &Path) -> PathBuf {
    program.parent().and_then(Path::parent).map(Path::to_path_buf).unwrap_or_default()
}

/// Two install roots compare as paths, so the trailing `/` that `system status` prints does not matter.
fn same_root(left: &Path, right: &str) -> bool {
    left.components().eq(Path::new(right).components())
}

/// The Apple `container` engine of a Docker pool.
pub(crate) struct AppleContainer {
    settings: Arc<Config>,
    runner: Runner,
    reporter: Reporter,
    /// The locks of this invocation, for the pool-wide image lock that a server start holds.
    locks: Arc<LockManager>,
    /// The nameserver of every build and every container, derived once.
    nameserver: OnceCell<String>,
    /// Set once the note about a server of another install was given.
    foreign_noted: AtomicBool,
}

impl AppleContainer {
    pub(crate) fn new(settings: Arc<Config>, runner: Runner, reporter: Reporter, locks: Arc<LockManager>) -> Self {
        Self {
            settings,
            runner,
            reporter,
            locks,
            nameserver: OnceCell::new(),
            foreign_noted: AtomicBool::new(false),
        }
    }

    // --- the server --------------------------------------------------------------------------------------

    /// `system status --format json` of the server that `program` reaches.
    ///
    /// A server that is down exits 1 and says `unregistered`, which is a status and no error. A CLI that cannot run is
    /// `container_missing`. An answer that is no JSON, and a CLI that a signal ended, are `probe_unanswered`.
    pub(crate) async fn status(&self, ctx: &Ctx, program: &Path) -> Result<SystemStatus, Refusal> {
        let argv = command(program, &["system", "status", "--format", "json"]);
        let captured = match self.runner.capture(ctx, &argv, &SpawnOptions::within(SYSTEM_QUERY_TIMEOUT)).await {
            Ok(captured) => captured,
            Err(ProcError::SpawnFailed(refusal)) => {
                return Err(container_missing(format!(
                    "cannot run {}: {}; {}",
                    program.display(),
                    refusal.message,
                    install_remedy(&self.settings)
                )));
            }
            Err(other) => return Err(other.into()),
        };
        match captured.probe_exit() {
            ProbeExit::Answered | ProbeExit::Negative => {}
            ProbeExit::CannotRun => {
                return Err(container_missing(format!(
                    "{} system status exited with {}; {}",
                    program.display(),
                    captured.exit_code,
                    install_remedy(&self.settings)
                )));
            }
            ProbeExit::Killed => return Err(probe_unanswered(&argv, &captured, ProbeOutput::Quoted)),
        }
        let parsed: Option<SystemStatus> = serde_json::from_str(captured.stdout.trim()).ok();
        match parsed {
            Some(status) if captured.exit_code == 0 || !status.running() => Ok(status),
            // A nonzero exit with no JSON is a server that is not there, with the reason on stderr.
            None if captured.exit_code != 0 => Ok(SystemStatus::default()),
            _ => Err(probe_unanswered(&argv, &captured, ProbeOutput::Quoted)),
        }
    }

    /// Whether the server runs, asked without a change: the question of `pool stop`, `pool gc`, a lease release and
    /// `status`, which never start the server.
    pub(crate) async fn is_running(&self, ctx: &Ctx, program: &Path) -> Result<bool, Refusal> {
        Ok(self.status(ctx, program).await?.running())
    }

    /// Refuses a host that cannot run the engine, before any `container` command runs. So a host without the CLI gets
    /// this refusal, and not `container_missing`.
    ///
    /// - A host that is not Apple silicon is refused `unsupported_backend_operation`: Apple builds the engine for it
    ///   only.
    /// - A macOS older than [`CONTAINER_MACOS_MAJOR`] is refused `container_macos_too_old`, exit 2: Apple `container`
    ///   1.5.0 needs macOS 26. A Mac whose version file names no version passes.
    ///
    /// The engine rule keeps the host engine on such a host, so only settings that pin the engine or the host facts
    /// get here. The remedy is [`named_engine_remedy`].
    pub(crate) fn require_supported_host(&self) -> Result<(), Refusal> {
        if self.settings.guest_arch != GuestArch::Arm64 {
            return Err(unsupported(format!(
                "the Apple container engine runs on Apple silicon only; {}",
                named_engine_remedy()
            )));
        }
        match self.settings.macos {
            Some(macos) if macos.major < CONTAINER_MACOS_MAJOR => Err(Refusal::new(
                "container_macos_too_old",
                Exit::USAGE,
                format!(
                    "the Apple container engine needs macOS {CONTAINER_MACOS_MAJOR} or newer, and this host runs macOS {}; {}",
                    macos.major,
                    named_engine_remedy()
                ),
            )),
            _ => Ok(()),
        }
    }

    /// Brings the server up and refuses a server this controller must not use.
    ///
    /// 1. A host that cannot run the engine is refused ([`AppleContainer::require_supported_host`]).
    /// 2. A server that runs is checked ([`AppleContainer::require_own_server`]).
    /// 3. Otherwise the start runs under the pool-wide image lock, so two slots do not download the kernel at once.
    ///    The status is read again under the lock, and only a server that is still down is started:
    ///    `system start --enable-kernel-install`, with its log in [`Config::container_system_log_path`].
    /// 4. The status after the start must say `running`, and the server must pass the check.
    pub(crate) async fn ensure_running(&self, ctx: &Ctx, program: &Path) -> Result<(), Refusal> {
        self.require_supported_host()?;
        let status = self.status(ctx, program).await?;
        if status.running() {
            return self.require_own_server(&status, program);
        }
        let _held = self.lock(ctx).await?;
        let status = self.status(ctx, program).await?;
        if status.running() {
            return self.require_own_server(&status, program);
        }
        self.start(ctx, program).await?;
        let status = self.status(ctx, program).await?;
        if !status.running() {
            return Err(engine_start_failed(format!(
                "{} system start exited 0, and system status still says {:?}; the start log is {}",
                program.display(),
                status.status,
                self.settings.container_system_log_path().display()
            )));
        }
        self.require_own_server(&status, program)
    }

    /// Refuses a server of another install root with another major version, `container_engine_foreign`. A server of
    /// another install root with the same major version is accepted, with one note: the XPC interface is compatible
    /// within a major version, and the data root is the default one for both.
    ///
    /// The `client` of the status is the CLI that asked, so the comparison needs no `--version` call.
    pub(crate) fn require_own_server(&self, status: &SystemStatus, program: &Path) -> Result<(), Refusal> {
        if !status.host.architecture.is_empty() && status.host.architecture != "arm64" {
            return Err(unsupported(format!(
                "the Apple container server runs on {:?}; a Docker worker runs this host's own architecture, \
                 linux/arm64",
                status.host.architecture
            )));
        }
        let own = install_root_of(program);
        if same_root(&own, &status.paths.install_root) {
            return Ok(());
        }
        let server = major_version(&status.server.version);
        if server.is_none() || server != major_version(&status.client.version) {
            return Err(Refusal::new(
                "container_engine_foreign",
                Exit::UNAVAILABLE,
                format!(
                    "the Apple container server of this login session runs from {} at version {:?}, and this \
                     controller runs the CLI of {} at version {:?}; the server belongs to another install, and one \
                     session has one server. Stop that server when nothing else uses it, or name its CLI in \
                     CONTAINER_BIN",
                    status.paths.install_root,
                    status.server.version,
                    own.display(),
                    status.client.version
                ),
            ));
        }
        if !self.foreign_noted.swap(true, Ordering::Relaxed) {
            self.reporter.note(
                format!(
                    "the Apple container server runs from {} at version {}, not from the install of this CLI ({}); \
                     the major version is the same, so the controller uses it",
                    status.paths.install_root,
                    status.server.version,
                    own.display()
                ),
                None,
            );
        }
        Ok(())
    }

    async fn start(&self, ctx: &Ctx, program: &Path) -> Result<(), Refusal> {
        let log = self.settings.container_system_log_path();
        remove_if_present(&log)?;
        self.reporter.note(
            format!(
                "starting the Apple container server; the first start downloads the kernel, about 700 MB, and takes \
                 minutes (log: {})",
                log.display()
            ),
            None,
        );
        let argv = command(program, &["system", "start", "--enable-kernel-install"]);
        self.runner
            .checked_to_file(ctx, &argv, &log, &log_options(SYSTEM_START_BUDGET))
            .await
            .map_err(|error| match error {
                ProcError::Exited { refusal, .. } => engine_start_failed(format!(
                    "{}; the start log is {}; the engine needs macOS {CONTAINER_MACOS_MAJOR} or newer",
                    refusal.message,
                    log.display()
                )),
                other => other.into(),
            })
            .map(drop)
    }

    /// The pool-wide image lock. The server start holds it, as the image build does, because the first start
    /// downloads the kernel and the first build pulls the builder image.
    async fn lock(&self, ctx: &Ctx) -> Result<LockGuard, Refusal> {
        let path = self.settings.docker_image_lock_path();
        self.locks
            .acquire_queued(
                ctx,
                &path,
                "container-system",
                SYSTEM_START_BUDGET,
                "engine_busy",
                &format!(
                    "another command held the Docker image lock {} for longer than {} s; the server start log is {}",
                    path.display(),
                    SYSTEM_START_BUDGET.as_secs(),
                    self.settings.container_system_log_path().display()
                ),
            )
            .await
    }

    // --- the builder -------------------------------------------------------------------------------------

    /// Stops the builder VM, after each image build and before a delete. A stopped builder returns its
    /// [`BUILDER_MEMORY`] to the host and keeps its image, so the next build starts it again without a pull. A builder
    /// that does not exist is no error: `builder stop` then exits 1 with `notFound`.
    pub(crate) async fn stop_builder(&self, ctx: &Ctx, program: &Path) -> Result<(), Refusal> {
        let stop = command(program, &["builder", "stop"]);
        let stopped = self.runner.capture(ctx, &stop, &SpawnOptions::within(SYSTEM_QUERY_TIMEOUT)).await?;
        if stopped.exit_code != 0 && !is_not_found(&stopped) {
            return Err(subprocess_failed(&stop, &stopped));
        }
        Ok(())
    }

    /// Stops and deletes the builder VM before `pool recycle all`. The next build makes it again. A builder that does
    /// not exist is no error: `builder stop` then exits 1 with `notFound`, and `builder delete` exits 0.
    ///
    /// Never `system stop`: it stops every container of the login session.
    pub(crate) async fn delete_builder(&self, ctx: &Ctx, program: &Path) -> Result<(), Refusal> {
        self.stop_builder(ctx, program).await?;
        let delete = command(program, &["builder", "delete"]);
        let deleted = self
            .runner
            .capture(ctx, &delete, &SpawnOptions::within(SYSTEM_QUERY_TIMEOUT))
            .await?;
        if deleted.exit_code != 0 && !is_not_found(&deleted) {
            return Err(subprocess_failed(&delete, &deleted));
        }
        Ok(())
    }

    // --- the nameserver ----------------------------------------------------------------------------------

    /// The nameserver of every build and every container, derived once per invocation: `AIR_VM_DNS`, else the first
    /// nameserver of `scutil --dns` outside the loopback, else [`FALLBACK_NAMESERVER`].
    ///
    /// A host whose resolver answers on the loopback, such as a VPN client, has no nameserver a guest can reach, so the
    /// fallback is a public one. A `scutil` that cannot run gives the fallback too.
    pub(crate) async fn resolve_nameserver(&self, ctx: &Ctx) -> Result<&str, Refusal> {
        self.nameserver
            .get_or_try_init(|| async {
                if let Some(configured) = &self.settings.vm_dns {
                    return Ok::<_, Refusal>(configured.clone());
                }
                let argv = vec!["scutil".to_owned(), "--dns".to_owned()];
                let answer = match self.runner.capture(ctx, &argv, &SpawnOptions::within(SCUTIL_TIMEOUT)).await {
                    Ok(captured) if captured.exit_code == 0 => first_routable_nameserver(&captured.stdout),
                    Ok(_) | Err(ProcError::SpawnFailed(_)) => None,
                    Err(other) => return Err(other.into()),
                };
                Ok(answer.map_or_else(|| FALLBACK_NAMESERVER.to_owned(), |address| address.to_string()))
            })
            .await
            .map(String::as_str)
    }

    /// The nameserver, once [`AppleContainer::resolve_nameserver`] derived it, or the empty string before: an argv
    /// with an empty `--dns` value is refused by the CLI rather than run with the wrong nameserver.
    pub(crate) fn nameserver(&self) -> &str {
        self.nameserver.get().map_or("", String::as_str)
    }
}

fn command(program: &Path, arguments: &[&str]) -> Vec<String> {
    std::iter::once(program.to_string_lossy().into_owned())
        .chain(arguments.iter().map(|argument| (*argument).to_owned()))
        .collect()
}

/// Whether the CLI said that the object does not exist. The CLI exits 1 for every error, so the text decides.
pub(crate) fn is_not_found(captured: &Captured) -> bool {
    let said = format!("{}{}", captured.stdout, captured.stderr);
    said.contains("not found") || said.contains("notFound")
}

fn subprocess_failed(argv: &[String], captured: &Captured) -> Refusal {
    Refusal::new(
        "subprocess_failed",
        Exit::FAILURE,
        format!(
            "{} exited with {}: {}",
            argv[1..].join(" "),
            captured.exit_code,
            captured.stderr.trim()
        ),
    )
}

/// What `container_missing` tells the operator to do.
pub(crate) fn install_remedy(settings: &Config) -> String {
    if settings.container.is_none() {
        let label = avl_base::config::CONTAINER_LABEL;
        return format!("the pinned CLI is {label}; fetch it with `./bazel.cmd cquery {label}`, or name another CLI in CONTAINER_BIN");
    }
    "install Apple container 1.x, or name its CLI in CONTAINER_BIN".to_owned()
}

pub(crate) fn container_missing(message: String) -> Refusal {
    Refusal::new("container_missing", Exit::UNAVAILABLE, message)
}

fn engine_start_failed(message: String) -> Refusal {
    Refusal::new("engine_start_failed", Exit::UNAVAILABLE, message)
}
