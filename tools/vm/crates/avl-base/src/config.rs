//! Every setting the controller reads from its environment, resolved once into one value.
//!
//! Resolving them together has two consequences: a caller cannot compose a pair the controller rejects (a
//! Parallels backend with a Linux guest is refused here, once, rather than at whichever macOS-shaped step
//! reached it first), and the whole set is one value a test can construct from an [`Environment`] it names.
//!
//! # What is a profile and what is a branch
//!
//! [`GuestOsProfile`] holds the guest-side differences that are only spelling - where the share is mounted, how
//! `ln` spells "do not follow an existing link", and whether a `sudo` and a `chown` are part of a step at all
//! ([`GuestOsProfile::privileged`]). Anything with behavioural weight is a branch on [`Config::guest_os`] instead: a
//! login session to wait for, TCC, an APFS container to grow, a sealed golden to clone.
//!
//! # Empty means unset
//!
//! An exported-but-empty variable is absent: `export AIR_VM_DATA=` in a wrapper script must not turn a guest path
//! into `/out` instead of `$AIR_VM_DATA/out`.
//!
//! # Two kinds of path
//!
//! A *guest* path names a file inside the worker VM, and every guest is POSIX, so it is a `String` joined with
//! `/` whatever the host is. A *host* path names a file the controller opens itself, so it is a [`PathBuf`].

use std::collections::HashMap;
use std::fmt;
use std::path::{Path, PathBuf};
use std::str::FromStr;
use std::sync::OnceLock;
use std::time::Duration;

use avl_trace_tools::discover::VM_EXPORT_DIR;
use serde::{Deserialize, Serialize};

use crate::refusal::RefusalExt;
use crate::refusal::{Exit, Refusal};

#[cfg(test)]
mod tests;

/// The directory of the controller's workspace, relative to the root of an ultimate checkout. The image pipeline is
/// `provision/` in it.
pub const WORKSPACE_DIR: &str = "community/tools/vm";

/// The most output one captured subprocess may return before it is truncated.
pub const CAPTURE_LIMIT_BYTES: usize = 8 << 20;

/// The bounds on the `git status` probe a worker's parity check runs.
pub const DEFAULT_GIT_STATUS_PROBE_TIMEOUT_MS: u64 = 5_000;
pub const MAX_GIT_STATUS_PROBE_TIMEOUT_MS: u64 = 30_000;

/// The pinned Tart executable, declared in `tart.MODULE.bazel` of this workspace. The Tart backend asks Bazel
/// for it at its first command, unless `TART_BIN` names another executable.
///
/// Each pin is an alias in `BUILD.bazel` of this workspace, named after its repository. The root cannot see a
/// repository of the community module, so the controller asks for the alias.
pub const TART_LABEL: &str = "@community//tools/vm:air_tart";

/// The pinned Docker CLI of a macOS host of `arch`, declared in `docker.MODULE.bazel` of this workspace. The Docker
/// backend asks Bazel for it at its first command, unless `DOCKER_BIN` names another executable. No archive is
/// pinned for a Linux or a Windows host.
pub fn docker_cli_label(arch: GuestArch) -> String {
    format!("@community//tools/vm:air_docker_darwin_{}", darwin_arch(arch))
}

/// The pinned `docker-buildx` CLI plugin of a macOS host of `arch`, declared in `docker.MODULE.bazel` of this
/// workspace. The static macOS CLI has no plugin, and the image build needs BuildKit, so the Docker backend asks
/// Bazel for the plugin of the pinned CLI.
pub fn docker_buildx_label(arch: GuestArch) -> String {
    format!("@community//tools/vm:air_docker_buildx_darwin_{}", darwin_arch(arch))
}

/// The pinned Apple `container` CLI, declared in `container.MODULE.bazel` of this workspace. Apple builds it for
/// Apple silicon only, so there is one label. The Docker backend asks Bazel for it when it runs on the Apple
/// `container` engine ([`DockerEngine::AppleContainer`]), unless `CONTAINER_BIN` names another executable.
pub const CONTAINER_LABEL: &str = "@community//tools/vm:air_container_darwin_arm64";

/// The first major version of macOS on which Apple `container` is the default engine. Apple `container` 1.5.0 needs
/// macOS 26. An older macOS keeps the host engine ([`DockerEngine::decide`]).
pub const CONTAINER_MACOS_MAJOR: u32 = 26;

/// The file in which macOS keeps its release. [`MacosHost::read`] reads `ProductVersion` from it, so the load starts
/// no `sw_vers`.
const SYSTEM_VERSION_PLIST: &str = "/System/Library/CoreServices/SystemVersion.plist";

/// The default memory of one worker on the Apple `container` engine, in MiB. Each container is a VM of its own, and
/// the live lane's container peaked at 6.3 GiB on 2026-10-04 (ADR 0200). A running VM returns no memory to the host
/// until it stops, so the limit is the price of a running worker.
pub const CONTAINER_WORKER_MEMORY_MIB: u32 = 8_192;

/// The slot count of a pool that no setting sizes, and the floor of the host memory rule ([`default_docker_slots`]).
pub const DEFAULT_POOL_SLOTS: u32 = 2;

/// The most slots a pool may have, through `AIR_VM_MAX_WORKERS` or through the host memory rule.
pub const MAX_POOL_SLOTS: u32 = 16;

/// The workers of an Apple `container` pool get at most one part in this many of the host memory
/// ([`default_docker_slots`]).
pub const HOST_MEMORY_SHARE_DIVISOR: u64 = 4;

/// The grace period after a lease release before an idle Apple `container` worker stops ([`Config::idle_stop`]).
pub const IDLE_STOP_DEFAULT: Duration = Duration::from_hours(1);

/// The idle stop setting: seconds, or `off`.
pub const IDLE_STOP_VARIABLE: &str = "AIR_VM_IDLE_STOP";

/// The target name of a label: what follows the last `:`. A pin label names an alias, and the alias has the name of
/// the repository that it forwards to.
pub fn label_target(label: &str) -> &str {
    label.rsplit_once(':').map_or(label, |(_, target)| target)
}

/// The architecture as the repository names of `docker.MODULE.bazel` spell it.
const fn darwin_arch(arch: GuestArch) -> &'static str {
    match arch {
        GuestArch::Arm64 => "arm64",
        GuestArch::X86_64 => "x86_64",
    }
}

/// The guest directory of the run secrets on a Linux guest: `/dev/shm` is a tmpfs, so a secret never reaches the
/// persistent data volume.
pub const LINUX_RUN_SECRETS_DIR: &str = "/dev/shm/air-run-secrets";

/// The guest directory of the run secrets: [`LINUX_RUN_SECRETS_DIR`] on Linux, and `<vm_tmp>/run-secrets` on macOS,
/// which has no tmpfs.
///
/// These two forms are the only ones the controller writes, and so the only ones `daemon stop` and a lease release
/// may hand to `rm -rf`; see [`Config::removable_run_secrets_dir`].
pub fn run_secrets_dir(guest_os: GuestOs, vm_tmp: &str) -> String {
    match guest_os {
        GuestOs::Linux => LINUX_RUN_SECRETS_DIR.to_owned(),
        GuestOs::Macos => format!("{vm_tmp}/run-secrets"),
    }
}

/// Skips the Tart version floor entirely when true, for bisecting a Tart regression. One const for the reader and
/// for the refusal that names it.
pub const TART_VERSION_OVERRIDE_VARIABLE: &str = "AIR_VM_TART_VERSION_OVERRIDE";

/// Turns the viewer start off when its value is `off`.
pub const VIEWER_OFF_VARIABLE: &str = "AIR_VM_VIEWER";

/// Turns the live dashboard off when its value is `off`; the run then prints plain lines under a one-line footer.
pub const DASHBOARD_OFF_VARIABLE: &str = "AIR_VM_DASHBOARD";

/// Names the terminal's side, `light` or `dark`, for a terminal that does not answer a colour query. The dashboard
/// then derives its palette from a plain foreground and background of that side instead of asking.
pub const THEME_VARIABLE: &str = "AIR_VM_THEME";

/// The image pins of `provision/versions.env` of this workspace, read at compile time, so the controller and the image
/// pipeline cannot hold two copies that drift: a Tart golden default had already drifted once.
pub mod pins {
    use std::collections::BTreeMap;
    use std::sync::LazyLock;

    /// The file, embedded. Three levels up from this source file (`src`, `avl-base`, `crates`) is the workspace
    /// directory; Bazel names the file in `compile_data`.
    pub(crate) const VERSIONS_ENV: &str = include_str!("../../../provision/versions.env");

    pub(crate) static PINS: LazyLock<BTreeMap<String, String>> =
        LazyLock::new(|| parse(VERSIONS_ENV).unwrap_or_else(|error| panic!("versions.env: {error}")));

    /// `NAME=value` assignments, with a `${NAME}` reference expanded from the assignments above it, which is
    /// what the shell that sources the file does. An unexpanded reference is an error rather than a literal.
    pub(crate) fn parse(text: &str) -> Result<BTreeMap<String, String>, String> {
        let mut pins: BTreeMap<String, String> = BTreeMap::new();
        for line in text.lines().map(str::trim) {
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            let Some((name, value)) = line.split_once('=') else {
                continue;
            };
            let mut value = value.to_owned();
            for (known, resolved) in &pins {
                value = value.replace(&format!("${{{known}}}"), resolved);
            }
            if value.contains("${") {
                return Err(format!("{name}={value} names something not declared above it"));
            }
            pins.insert(name.to_owned(), value);
        }
        Ok(pins)
    }

    /// # Panics
    /// When the embedded file lacks the name: the crate's own unit test reads every name this module uses.
    fn pin(name: &str) -> &'static str {
        PINS.get(name)
            .map(String::as_str)
            .filter(|value| !value.is_empty())
            .unwrap_or_else(|| panic!("versions.env declares no {name}"))
    }

    /// The sealed golden a Tart macOS worker is cloned from when `AIR_VM_GOLDEN_VM` names none (`GOLDEN_VM`, the
    /// name `build-golden.sh` gives the built VM).
    pub fn tart_golden_vm() -> &'static str {
        pin("GOLDEN_VM")
    }

    /// The Node major of a worker's guest, one number for both guests (`NODE_MAJOR`). The macOS golden installs
    /// this major from Homebrew. The Docker image installs a Node of this major, and a unit test checks the
    /// Dockerfile against it. A current `@openai/codex` refuses an older major.
    ///
    /// # Panics
    /// When `NODE_MAJOR` is not a number; the unit test reads it.
    pub fn guest_node_major() -> u32 {
        let major = pin("NODE_MAJOR");
        major
            .parse()
            .unwrap_or_else(|_| panic!("versions.env NODE_MAJOR={major} is not a number"))
    }

    /// The Ubuntu image a Docker worker's image is built on, addressed by digest (`DOCKER_BASE_IMAGE`). The pin
    /// names Ubuntu 26.04. It is an input of the image tag, so a digest bump builds the image again.
    pub fn docker_base_image() -> &'static str {
        pin("DOCKER_BASE_IMAGE")
    }
}

// --- the axes --------------------------------------------------------------------------------------------

/// The hypervisor, or the container engine: what runs the worker.
///
/// Docker is a backend and not a guest OS, because what differs is how a worker is made, started and reached. The
/// Docker worker is the Linux guest: an Ubuntu with the guest agent, the parity layout and the Linux paths.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Backend {
    Tart,
    Parallels,
    Docker,
    #[serde(rename = "container-linux")]
    ContainerLinux,
}

impl Backend {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Tart => "tart",
            Self::Parallels => "parallels",
            Self::Docker => "docker",
            Self::ContainerLinux => "container-linux",
        }
    }
}

impl fmt::Display for Backend {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

/// The guest's operating system, a separate axis from the hypervisor running it. Written into lease and worker
/// state files as `macos` / `linux`.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum GuestOs {
    Macos,
    Linux,
}

impl GuestOs {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Macos => "macos",
            Self::Linux => "linux",
        }
    }

    /// The guest's spellings on a backend whose guest the controller administers as root. Infallible: the set of
    /// guests is closed, so a guest with no profile cannot exist. [`Selection::profile`] picks the profile of a
    /// selection, which is another one for the testing-ui container.
    pub fn profile(self) -> &'static GuestOsProfile {
        match self {
            Self::Macos => &MACOS_PROFILE,
            Self::Linux => &LINUX_PROFILE,
        }
    }
}

impl fmt::Display for GuestOs {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

/// The guest's CPU architecture. Nothing selects it: it follows the backend and the host.
///
/// Tart and Parallels run Apple-silicon guests. A Docker worker shares the kernel of the engine's Linux VM, so it
/// runs the host's own architecture natively: x86_64 on any x86_64 host (a Windows x64 PC, a Linux CI agent, an
/// Intel Mac), and arm64 on any arm64 host (an Apple-silicon Mac, a Windows arm64 PC). The testing-ui container
/// runs the host's architecture for the same reason. The Docker backend's engine gate checks that the engine runs
/// this architecture, so a mismatch (an amd64 engine on an Apple-silicon Mac, a remote `DOCKER_HOST` of another
/// architecture) is a named refusal and never a lane built for the wrong guest. The
/// Starlark side of the same rule is two keys: `//plugins/air/tests/integration/ide:air_lane_guest_linux_on_host_linux_x64` for a Linux x86_64
/// host, whose own build is the guest's, and `//plugins/air/tests/integration/ide:air_lane_guest_linux_x64_cross` for another x86_64 host.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum GuestArch {
    Arm64,
    X86_64,
}

impl GuestArch {
    /// The architecture of the guests `backend` runs on this host.
    pub const fn of(backend: Backend) -> Self {
        match backend {
            Backend::Docker | Backend::ContainerLinux if cfg!(target_arch = "x86_64") => Self::X86_64,
            Backend::Docker | Backend::ContainerLinux | Backend::Tart | Backend::Parallels => Self::Arm64,
        }
    }

    /// Rust's `target_arch` spelling, which is also `uname -m` on Linux.
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Arm64 => "aarch64",
            Self::X86_64 => "x86_64",
        }
    }

    /// The OCI spelling of an image index and of `docker version`.
    pub const fn oci_arch(self) -> &'static str {
        match self {
            Self::Arm64 => "arm64",
            Self::X86_64 => "amd64",
        }
    }
}

impl fmt::Display for GuestArch {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

/// The operating system of the host the controller runs on. Nothing selects it: it is the target of the build.
///
/// A value and not a `cfg!` test at each use, so a test can ask about a host it does not run on. A Unix host that is
/// not macOS counts as Linux, because the Linux defaults are the XDG ones.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum HostOs {
    Macos,
    Linux,
    Windows,
}

impl HostOs {
    /// The host this controller is built for.
    pub const CURRENT: Self = if cfg!(target_os = "macos") {
        Self::Macos
    } else if cfg!(windows) {
        Self::Windows
    } else {
        Self::Linux
    };

    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Macos => "macos",
            Self::Linux => "linux",
            Self::Windows => "windows",
        }
    }

    /// Whether the controller drives `backend` from this host. Tart and Parallels need a macOS or a Linux host. A
    /// Windows host reaches Docker through `docker.exe`, and the testing-ui container through the skill's `wslc`.
    pub const fn drives(self, backend: Backend) -> bool {
        matches!(backend, Backend::Docker | Backend::ContainerLinux) || !matches!(self, Self::Windows)
    }

    /// Orders two environment variable names as this host compares them. Windows compares the names without case,
    /// so `Path` and `PATH` are one variable. A Unix host compares the bytes.
    pub fn compare_variables(self, left: &str, right: &str) -> std::cmp::Ordering {
        match self {
            Self::Windows => left
                .bytes()
                .map(|byte| byte.to_ascii_uppercase())
                .cmp(right.bytes().map(|byte| byte.to_ascii_uppercase())),
            Self::Macos | Self::Linux => left.cmp(right),
        }
    }

    /// The name of a host program for a message: the last component of its path. On Windows either slash separates
    /// the components, and a trailing `.exe` in any case is not part of the name.
    pub fn program_name(self, program: &str) -> &str {
        match self {
            Self::Windows => {
                let name = program.rsplit(['/', '\\']).next().unwrap_or(program);
                name.len()
                    .checked_sub(".exe".len())
                    .filter(|&stem| name.is_char_boundary(stem))
                    .filter(|&stem| name[stem..].eq_ignore_ascii_case(".exe"))
                    .map_or(name, |stem| &name[..stem])
            }
            Self::Macos | Self::Linux => program.rsplit('/').next().unwrap_or(program),
        }
    }

    /// The variables the home is read from, the first set one wins. Windows sets `USERPROFILE`, and a POSIX shell
    /// such as Git Bash also sets `HOME`.
    const fn home_variables(self) -> &'static [&'static str] {
        match self {
            Self::Windows => &["HOME", "USERPROFILE"],
            Self::Macos | Self::Linux => &["HOME"],
        }
    }

    /// The default runtime root: it holds the mode-0600 receipts, the golden seal and live leases, all checked
    /// fail-closed.
    ///
    /// On macOS it keeps its `macos-vm-ui-tests` name from before the skill was renamed, because the live pool state
    /// is there and the image pipeline keeps its Packer state in the same directory. A Linux host has no such history
    /// and gets the default location of the XDG state directory, under the current name of the skill. A Windows host
    /// keeps it in the local application data, `LOCALAPPDATA`, which is `AppData\Local` under the home when unset.
    fn runtime_root(self, home: &Path, environment: &Environment) -> PathBuf {
        match self {
            Self::Macos => home.join("Library/Application Support/JetBrains/macos-vm-ui-tests"),
            Self::Linux => home.join(".local/state/JetBrains/air-vm-ui-tests"),
            Self::Windows => environment
                .get("LOCALAPPDATA")
                .map_or_else(|| home.join("AppData").join("Local"), PathBuf::from)
                .join("JetBrains")
                .join("air-vm-ui-tests"),
        }
    }

    /// The default Bazel output user root: the `startup:<os> --output_user_root` of `community/common.bazelrc`,
    /// which every `bazel.cmd` of this checkout starts with. It is under the home on macOS and Linux, and a fixed
    /// path on Windows. `AIR_VM_BAZEL_USER_ROOT` overrides it, for a host whose user `.bazelrc` moves the root.
    fn bazel_user_root(self, home: &Path) -> PathBuf {
        match self {
            Self::Macos => home.join("Library/Caches/JetBrains/MonorepoBazel"),
            Self::Linux => home.join(".cache/JetBrains/MonorepoBazel"),
            Self::Windows => PathBuf::from("C:/ProgramData/_bazel"),
        }
    }
}

/// A macOS host as the engine rule reads it ([`DockerEngine::decide`]).
///
/// [`Config::load`] reads it once from the host. A test pins it, as it pins the [`HostOs`].
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct MacosHost {
    /// The major version of the release: 26 for macOS 26.0.1.
    pub major: u32,
    /// Whether the processor is Apple silicon. Apple builds `container` for Apple silicon only.
    pub apple_silicon: bool,
}

impl MacosHost {
    /// This host, or `None` when it is not macOS or when its version file names no version.
    pub fn read() -> Option<Self> {
        if HostOs::CURRENT != HostOs::Macos {
            return None;
        }
        let plist = std::fs::read_to_string(SYSTEM_VERSION_PLIST).ok()?;
        Some(Self {
            major: product_major(&plist)?,
            apple_silicon: cfg!(target_arch = "aarch64"),
        })
    }

    /// Whether Apple `container` is the default engine of this host: macOS [`CONTAINER_MACOS_MAJOR`] or newer, on
    /// Apple silicon.
    pub const fn runs_apple_container(self) -> bool {
        self.apple_silicon && self.major >= CONTAINER_MACOS_MAJOR
    }
}

/// The facts of the host that the settings read and that this crate does not probe itself.
///
/// The caller of [`Config::load`] hands them in. The memory probe needs `libc`, which this crate does not link, so
/// `avl_host_sys::host::memory_mib` answers it. A test pins both facts, as it pins the [`HostOs`].
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct HostFacts {
    /// The macOS release ([`MacosHost::read`]), or `None` on another host.
    pub macos: Option<MacosHost>,
    /// The physical memory of the host in MiB, or `None` when nothing probed it. The default pool size of the Apple
    /// `container` engine follows it ([`default_docker_slots`]).
    pub memory_mib: Option<u64>,
}

impl HostFacts {
    /// The macOS release of this host and no memory: the facts of a caller that sizes no pool.
    pub fn without_memory() -> Self {
        Self {
            macos: MacosHost::read(),
            memory_mib: None,
        }
    }
}

/// The default slot count of a pool on the Apple `container` engine: one part in [`HOST_MEMORY_SHARE_DIVISOR`] of the
/// host memory, divided by the memory of one worker, from [`DEFAULT_POOL_SLOTS`] to [`MAX_POOL_SLOTS`].
///
/// A host of 128 GiB with workers of 8 GiB gets 4 slots, a host of 64 GiB gets 2, and a host of 256 GiB gets 8. A host
/// of unknown memory gets [`DEFAULT_POOL_SLOTS`].
pub fn default_docker_slots(memory_mib: Option<u64>, worker_mib: u32) -> u32 {
    let Some(host_mib) = memory_mib else {
        return DEFAULT_POOL_SLOTS;
    };
    let slots = host_mib / (HOST_MEMORY_SHARE_DIVISOR * u64::from(worker_mib.max(1)));
    u32::try_from(slots)
        .unwrap_or(MAX_POOL_SLOTS)
        .clamp(DEFAULT_POOL_SLOTS, MAX_POOL_SLOTS)
}

/// Why the pool has its slot count, as `status` prints it.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PoolRule {
    /// `AIR_VM_WORKERS` names the slots.
    Named,
    /// `AIR_VM_MAX_WORKERS` sets the count.
    MaxWorkers,
    /// The Apple `container` default follows the host memory ([`default_docker_slots`]).
    HostMemory { host_mib: u64, worker_mib: u32 },
    /// The Apple `container` default on a host of unknown memory: [`DEFAULT_POOL_SLOTS`].
    UnknownHostMemory,
    /// The default of every other engine and backend: [`DEFAULT_POOL_SLOTS`].
    Default,
    /// The one VM that `AIR_VM_PARALLELS_VM` names.
    ParallelsVm,
}

impl fmt::Display for PoolRule {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match *self {
            Self::Named => f.write_str("AIR_VM_WORKERS"),
            Self::MaxWorkers => f.write_str("AIR_VM_MAX_WORKERS"),
            Self::HostMemory { host_mib, worker_mib } => {
                write!(f, "host {}, {} per worker", gib(host_mib), gib(u64::from(worker_mib)))
            }
            Self::UnknownHostMemory => f.write_str("host memory unknown"),
            Self::Default => f.write_str("default"),
            Self::ParallelsVm => f.write_str("AIR_VM_PARALLELS_VM"),
        }
    }
}

/// What a run copies into the export directory of the checkout, [`VM_EXPORT_DIR`] (`AIR_VM_EXPORT`).
///
/// The export is for a run on another host: `[host]` of `.branchdeck.toml` sets the variable, and `bd-host` copies the
/// directory back.
/// The runtime root of this host keeps the same files, so a run here exports nothing by default.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Export {
    /// No export.
    Off,
    /// The journal, the reports and the trace zips of the scenarios that did not pass.
    Failed,
    /// The journal, the reports and every trace zip.
    All,
}

/// A size in MiB as GiB: `128 GiB`, or `7.5 GiB` when it is not a whole number.
fn gib(mib: u64) -> String {
    if mib.is_multiple_of(1024) {
        format!("{} GiB", mib / 1024)
    } else {
        format!("{}.{} GiB", mib / 1024, mib % 1024 * 10 / 1024)
    }
}

/// The major version of `ProductVersion` in the XML of `SystemVersion.plist`: the digits before the first dot of the
/// string that follows the key.
fn product_major(plist: &str) -> Option<u32> {
    let (_, after_key) = plist.split_once("<key>ProductVersion</key>")?;
    let (version, _) = after_key.trim_start().strip_prefix("<string>")?.split_once("</string>")?;
    version.trim().split('.').next()?.parse().ok()
}

/// The engine the containers of a Docker pool run on. It follows the environment and the host.
///
/// When the environment names an engine, through `DOCKER_BIN` or `DOCKER_HOST`, the backend runs that CLI against
/// that engine. When it names none, a Mac on macOS 26 or newer, on Apple silicon, runs Apple `container`, which needs
/// no Docker installation. Every other host keeps the engine of its host environment.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum DockerEngine {
    /// The engine of the host environment: `DOCKER_HOST`, or the default context of the CLI.
    Host,
    /// Apple `container`: one VM per container, driven through its own CLI, the pin at [`CONTAINER_LABEL`] or
    /// `CONTAINER_BIN`. The server is the one of the login session, on the default data root of the tool.
    AppleContainer,
}

impl DockerEngine {
    /// The engine rule, as a pure function of the host, of its macOS release and of the two variables that name an
    /// engine. A Mac of an older or an unknown release, and a Mac with an Intel processor, keep the host engine.
    pub const fn decide(host: HostOs, macos: Option<MacosHost>, docker_bin_set: bool, docker_host_set: bool) -> Self {
        let names_engine = docker_bin_set || docker_host_set;
        if !names_engine && matches!(host, HostOs::Macos) && matches!(macos, Some(macos) if macos.runs_apple_container()) {
            Self::AppleContainer
        } else {
            Self::Host
        }
    }

    /// The word `status` prints: `host` for the engine of the host environment, `container` for Apple `container`.
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Host => "host",
            Self::AppleContainer => "container",
        }
    }
}

impl fmt::Display for DockerEngine {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl fmt::Display for HostOs {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

impl FromStr for GuestOs {
    type Err = Refusal;

    fn from_str(text: &str) -> Result<Self, Refusal> {
        match text {
            "macos" => Ok(Self::Macos),
            "linux" => Ok(Self::Linux),
            _ => Err(Refusal::usage(format!("this controller has no profile for a {text:?} guest"))),
        }
    }
}

/// The guest-side differences that are only spelling.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GuestOsProfile {
    pub os: GuestOs,
    /// Whether the controller administers the guest as root: it then prefixes a guest command with `sudo` and
    /// hands a directory that root made to the worker account with `chown`. An unprivileged guest runs every
    /// command as the one account it has, which owns what it writes, so both steps are left out.
    pub privileged: bool,
    /// Where the controller mounts the shared-folder device. Space-free, so a guest path is one shell word.
    pub share_mount: &'static str,
    pub chown: &'static str,
    /// The `ln` flags that do not dereference an existing link: BSD spells it `-h`, GNU `-n`.
    pub link_flags: &'static str,
    /// The VirtioFS device and its remount sweep, or `None` for a guest whose shares are bind mounts. Only a macOS
    /// guest has one: Tart and Parallels run a macOS guest, and a Docker worker's shares are bind mounts.
    pub virtiofs: Option<&'static VirtiofsMount>,
    /// Whether the shares are mounted at their host paths, so a host path is a guest path with no parity layout
    /// between them: the testing-ui container's are.
    pub shares_at_host_paths: bool,
}

/// How a guest's remount sweep reads and mounts the shared-folder device of Tart and Parallels.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct VirtiofsMount {
    /// How `mount` names the shared-folder filesystem in its output.
    pub mounted_filesystem: &'static str,
    /// Absolute because the sweep runs under `sudo -H`, whose `secure_path` on a macOS guest has no `/sbin`. A bare
    /// `mount` is "command not found", the sweep unmounts nothing, and the mount that follows fails with "Resource
    /// busy".
    pub mount_binary: &'static str,
    pub umount_binary: &'static str,
    /// Mounts [`GuestOsProfile::share_mount`] from the tag, given the mount point in `$MOUNT`.
    pub mount_shares: &'static str,
}

static MACOS_VIRTIOFS: VirtiofsMount = VirtiofsMount {
    mounted_filesystem: "AppleVirtIOFS",
    mount_binary: "/sbin/mount",
    umount_binary: "/sbin/umount",
    mount_shares: r#"/sbin/mount_virtiofs com.apple.virtio-fs.automount "$MOUNT""#,
};

static MACOS_PROFILE: GuestOsProfile = GuestOsProfile {
    os: GuestOs::Macos,
    privileged: true,
    share_mount: "/Volumes/AirVmShares",
    chown: "/usr/sbin/chown",
    link_flags: "-sfh",
    virtiofs: Some(&MACOS_VIRTIOFS),
    shares_at_host_paths: false,
};

static LINUX_PROFILE: GuestOsProfile = GuestOsProfile {
    os: GuestOs::Linux,
    privileged: true,
    // Not /Volumes: that is a macOS convention, and /mnt is where a Linux guest expects an operator mount.
    share_mount: "/mnt/AirVmShares",
    chown: "/bin/chown",
    link_flags: "-sfn",
    // A Docker worker's shares are bind mounts, with no device to sweep.
    virtiofs: None,
    shares_at_host_paths: false,
};

/// The Linux guest of the testing-ui container. The skill's script mounts the Bazel share read-only at its host path,
/// and the container runs as its `ubuntu` account with no `sudo`.
static CONTAINER_LINUX_PROFILE: GuestOsProfile = GuestOsProfile {
    privileged: false,
    shares_at_host_paths: true,
    ..LINUX_PROFILE
};

/// Which pool a message is about, in the spelling `--backend` accepts.
///
/// One value rather than two fields resolved separately: the axes are not independent, and falling back to them
/// one at a time can compose a pair [`Config::load`] rejects. The guest's architecture is not here: nothing selects
/// it, see [`GuestArch::of`].
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub struct Selection {
    pub backend: Backend,
    pub guest_os: GuestOs,
}

impl Selection {
    /// Where an invocation that names no backend and carries no lease receipt runs: the Docker pool, on every host.
    /// ADR 0190 records the choice.
    ///
    /// A Linux guest and not a macOS one: on 2026-08-23 the same `--lane ui` was 23 of 23 on `air-linux-1` and 8 of
    /// 24 on `air-macos-1`, all 16 macOS failures at a window-activation mechanism the lane's IDE cannot satisfy in a
    /// worker's Aqua session (IJAI-1228).
    ///
    /// Docker and not Tart, on each host for its own reason:
    ///
    /// - a macOS host on macOS 26 or newer, on Apple silicon, runs Apple `container` ([`DockerEngine::decide`]), so it
    ///   needs no installation, as Tart needs none. Another Mac uses the engine of its host environment;
    /// - a Linux host has no Tart at all, because Tart runs only on macOS, so a Tart default there named a backend
    ///   that cannot exist;
    /// - a Windows host drives Docker only ([`HostOs::drives`]).
    ///
    /// The Tart macOS pool and the Parallels VM stay one `--backend` away. A Windows host still
    /// refuses a Tart or a Parallels selection that a flag or a receipt names.
    pub const DEFAULT: Self = Self {
        backend: Backend::Docker,
        guest_os: GuestOs::Linux,
    };

    /// The spelling `--backend` accepts for this selection. A Linux guest in a container is `docker`, the default
    /// pool ([`Selection::DEFAULT`]), or `container-linux` in the skill's container.
    pub const fn label(self) -> &'static str {
        self.backend.as_str()
    }

    /// The guest's spellings for this selection: the profile of its guest OS ([`GuestOs::profile`]), or the
    /// profile of the testing-ui container, whose shares sit at their host paths and whose account has no root.
    pub fn profile(self) -> &'static GuestOsProfile {
        match self.backend {
            Backend::ContainerLinux => &CONTAINER_LINUX_PROFILE,
            Backend::Tart | Backend::Parallels | Backend::Docker => self.guest_os.profile(),
        }
    }
}

impl Default for Selection {
    fn default() -> Self {
        Self::DEFAULT
    }
}

impl fmt::Display for Selection {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.label())
    }
}

/// The `--backend` flag: one flag, two axes. `tart` and `parallels` are a macOS guest, `docker` is a Linux guest in
/// a container, and `container-linux` is the Linux guest of the `testing-ui` skill's container. A wider flag names
/// pairs that no worker serves.
impl FromStr for Selection {
    type Err = Refusal;

    fn from_str(value: &str) -> Result<Self, Refusal> {
        let (backend, guest_os) = match value {
            "tart" => (Backend::Tart, GuestOs::Macos),
            "parallels" => (Backend::Parallels, GuestOs::Macos),
            "docker" => (Backend::Docker, GuestOs::Linux),
            "container-linux" => (Backend::ContainerLinux, GuestOs::Linux),
            _ => {
                return Err(Refusal::usage("--backend must be tart, parallels, docker or container-linux"));
            }
        };
        Ok(Self { backend, guest_os })
    }
}

// --- reading the environment -----------------------------------------------------------------------------

/// The variables [`Config::load`] reads: a value rather than the process's environment, so a test names exactly
/// what is set. An entry whose value is empty counts as unset.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Environment(HashMap<String, String>);

impl Environment {
    /// The process's own environment. Entries that are not UTF-8 are skipped: no setting here can be one. Of two
    /// entries with one name the first is kept, as `getenv(3)` and a child's runner keep it.
    pub fn from_os() -> Self {
        let mut variables = HashMap::new();
        for (name, value) in std::env::vars_os() {
            if let (Ok(name), Ok(value)) = (name.into_string(), value.into_string())
                && !name.is_empty()
            {
                variables.entry(name).or_insert(value);
            }
        }
        Self(variables)
    }

    pub fn from_pairs<N: Into<String>, V: Into<String>>(pairs: impl IntoIterator<Item = (N, V)>) -> Self {
        Self(pairs.into_iter().map(|(name, value)| (name.into(), value.into())).collect())
    }

    pub fn set(&mut self, name: impl Into<String>, value: impl Into<String>) -> &mut Self {
        self.0.insert(name.into(), value.into());
        self
    }

    /// Every variable, empty values included: what a child's environment is built from.
    pub fn pairs(&self) -> impl Iterator<Item = (String, String)> + '_ {
        self.0.iter().map(|(name, value)| (name.clone(), value.clone()))
    }

    /// The value, or `None` when the variable is absent or empty.
    pub fn get(&self, name: &str) -> Option<&str> {
        self.0.get(name).map(String::as_str).filter(|value| !value.is_empty())
    }
}

/// Resolves variables and keeps the first refusal, in the order the readers run, so one invalid environment
/// produces one message.
struct Reader<'a> {
    environment: &'a Environment,
    refusal: Option<Refusal>,
}

impl<'a> Reader<'a> {
    const fn new(environment: &'a Environment) -> Self {
        Reader {
            environment,
            refusal: None,
        }
    }

    fn refuse(&mut self, refusal: Refusal) {
        self.refusal.get_or_insert(refusal);
    }

    fn finish<T>(self, value: T) -> Result<T, Refusal> {
        match self.refusal {
            Some(refusal) => Err(refusal),
            None => Ok(value),
        }
    }

    fn set(&self, name: &str) -> Option<&'a str> {
        self.environment.get(name)
    }

    fn string(&self, name: &str, fallback: impl Into<String>) -> String {
        self.set(name).map_or_else(|| fallback.into(), str::to_owned)
    }

    fn optional(&self, name: &str) -> Option<String> {
        self.set(name).map(str::to_owned)
    }

    fn path(&self, name: &str, fallback: impl FnOnce() -> PathBuf) -> PathBuf {
        self.set(name).map_or_else(fallback, PathBuf::from)
    }

    /// A count, in base ten only: `8.0`, `0x10`, `1e3` and `1_000` are refused rather than reinterpreted. The
    /// surrounding whitespace of a shell here-doc is a real spelling and is trimmed.
    fn positive_int(&mut self, name: &str, fallback: u32) -> u32 {
        let Some(raw) = self.set(name) else {
            return fallback;
        };
        let trimmed = raw.trim();
        match trimmed.parse::<u32>() {
            Ok(value) if value > 0 && trimmed.bytes().all(|byte| byte.is_ascii_digit()) => value,
            _ => {
                self.refuse(Refusal::invalid_environment(format!("{name} must be a positive integer")));
                fallback
            }
        }
    }

    /// A positive count of seconds.
    fn seconds(&mut self, name: &str, fallback: u32) -> Duration {
        Duration::from_secs(u64::from(self.positive_int(name, fallback)))
    }

    /// A count of seconds that may be zero, or `off`, which answers `None`.
    fn seconds_or_off(&mut self, name: &str, fallback: Option<Duration>) -> Option<Duration> {
        let Some(raw) = self.set(name) else {
            return fallback;
        };
        let trimmed = raw.trim();
        if trimmed.eq_ignore_ascii_case("off") {
            return None;
        }
        match trimmed.parse::<u32>() {
            Ok(value) if trimmed.bytes().all(|byte| byte.is_ascii_digit()) => Some(Duration::from_secs(u64::from(value))),
            _ => {
                self.refuse(Refusal::invalid_environment(format!(
                    "{name} must be a count of seconds or `off`, not {raw:?}"
                )));
                fallback
            }
        }
    }

    fn bounded_positive_int(&mut self, name: &str, fallback: u32, maximum: u32) -> u32 {
        let value = self.positive_int(name, fallback);
        if value > maximum {
            self.refuse(Refusal::invalid_environment(format!("{name} must not exceed {maximum}")));
            return fallback;
        }
        value
    }

    fn boolean(&mut self, name: &str, fallback: bool) -> bool {
        let Some(raw) = self.set(name) else {
            return fallback;
        };
        let is = |words: [&str; 4]| words.iter().any(|word| raw.eq_ignore_ascii_case(word));
        if is(["1", "true", "yes", "on"]) {
            return true;
        }
        if is(["0", "false", "no", "off"]) {
            return false;
        }
        self.refuse(Refusal::invalid_environment(format!(
            "{name} must be a boolean (1/0, true/false, yes/no, on/off)"
        )));
        fallback
    }

    fn name(&mut self, value: &str, label: &str) {
        if let Err(refusal) = validate_name(value, label) {
            self.refuse(refusal);
        }
    }
}

/// Refuses a name a shell would reinterpret: `^[A-Za-z0-9._-]+$`.
///
/// Every worker name, share name, prefix and run id goes through it, because they end up inside a guest command
/// line built by concatenation, and a run id becomes a path component. The class is what a VM name, a
/// virtio-fs tag and a POSIX path component can all carry unquoted.
pub fn validate_name(value: &str, label: &str) -> Result<(), Refusal> {
    let safe = !value.is_empty()
        && value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_' | b'-'));
    if safe {
        Ok(())
    } else {
        Err(Refusal::new(
            "unsafe_name",
            Exit::USAGE,
            format!("{label} contains unsupported characters: {value:?}"),
        ))
    }
}

/// `caching=cached,sync=none`: comma-separated `word` or `word=word`, lowercase. The value is concatenated into a
/// `tart run` argv, so a shell metacharacter here is an injection site rather than a strange setting.
fn is_root_disk_options(value: &str) -> bool {
    let word = |text: &str| !text.is_empty() && text.bytes().all(|byte| byte.is_ascii_lowercase());
    value.split(',').all(|option| match option.split_once('=') {
        Some((name, setting)) => word(name) && word(setting),
        None => word(option),
    })
}

/// The size of a Tart worker's root disk. The floor is the image plus room for the writable state (`tmp`, the
/// runfiles trees, the daemon's jar store). A Tart macOS worker grows its disk to this size, and its image is
/// 50 GB. Only ever applied upwards: `tart set --disk-size` cannot shrink a disk.
fn tart_root_disk_gb(reader: &mut Reader<'_>) -> u32 {
    const FLOOR: u32 = 80;
    const FALLBACK: u32 = 120;
    let value = reader.bounded_positive_int("AIR_VM_ROOT_DISK_GB", FALLBACK, 4_000);
    if value < FLOOR {
        reader.refuse(Refusal::invalid_environment(format!(
            "AIR_VM_ROOT_DISK_GB must be at least {FLOOR} to hold a macOS worker's writable state"
        )));
        return FALLBACK;
    }
    value
}

/// The pool's slot names on a backend that makes its own workers. A slot is a name the controller may use, not a VM
/// or a container that exists: `lease acquire` and `pool start` make a missing one. `AIR_VM_WORKERS` names the slots
/// and fixes the pool at that size; otherwise slots are `<prefix>-1 … <prefix>-N`, with a prefix distinct per pool,
/// because a name collision would hand a Linux lease a macOS worker.
///
/// A pool has [`DEFAULT_POOL_SLOTS`] slots unless `AIR_VM_MAX_WORKERS` says otherwise. On the Apple `container` engine
/// the default follows the host memory instead ([`default_docker_slots`]), because each worker is a VM of
/// `worker_mib`. A container on the host engine has no cap, so that pool keeps the fixed default (the decision
/// `2026-10-10-the-container-pool-follows-the-host-memory-and-the-builder-stops`).
///
/// A container-linux pool has one slot, `container-linux-1`: the skill's script runs one container, so an
/// `AIR_VM_MAX_WORKERS` other than 1 is refused there.
fn worker_slots(
    reader: &mut Reader<'_>,
    backend: Backend,
    engine: DockerEngine,
    worker_mib: u32,
    memory_mib: Option<u64>,
) -> (Vec<String>, PoolRule) {
    if let Some(explicit) = reader.set("AIR_VM_WORKERS") {
        let workers: Vec<String> = explicit
            .split(',')
            .map(str::trim)
            .filter(|worker| !worker.is_empty())
            .map(str::to_owned)
            .collect();
        if workers.is_empty() {
            reader.refuse(Refusal::new(
                "invalid_worker_pool",
                Exit::USAGE,
                "AIR_VM_WORKERS is set but names no workers",
            ));
            return (Vec::new(), PoolRule::Named);
        }
        let mut seen = std::collections::HashSet::new();
        if !workers.iter().all(|worker| seen.insert(worker)) {
            reader.refuse(Refusal::new(
                "invalid_worker_pool",
                Exit::USAGE,
                "AIR_VM_WORKERS must name distinct workers",
            ));
            return (Vec::new(), PoolRule::Named);
        }
        return (workers, PoolRule::Named);
    }
    let (default_prefix, default_count) = match backend {
        Backend::Docker => ("air-docker", 2),
        Backend::ContainerLinux => ("container-linux", 1),
        Backend::Tart | Backend::Parallels => ("air-macos", 2),
    };
    let prefix = reader.string("AIR_VM_WORKER_PREFIX", default_prefix);
    if let Err(refusal) = validate_name(&prefix, "worker name prefix") {
        reader.refuse(refusal);
        return (Vec::new(), PoolRule::Default);
    }
    let (fallback, default_rule) = match (backend, engine, memory_mib) {
        (Backend::Docker, DockerEngine::AppleContainer, Some(host_mib)) => (
            default_docker_slots(memory_mib, worker_mib),
            PoolRule::HostMemory { host_mib, worker_mib },
        ),
        (Backend::Docker, DockerEngine::AppleContainer, None) => (DEFAULT_POOL_SLOTS, PoolRule::UnknownHostMemory),
        _ => (default_count, PoolRule::Default),
    };
    let rule = if reader.set("AIR_VM_MAX_WORKERS").is_some() {
        PoolRule::MaxWorkers
    } else {
        default_rule
    };
    let max_workers = reader.bounded_positive_int("AIR_VM_MAX_WORKERS", fallback, MAX_POOL_SLOTS);
    if backend == Backend::ContainerLinux && max_workers != 1 {
        reader.refuse(Refusal::new(
            "invalid_worker_pool",
            Exit::USAGE,
            format!("AIR_VM_MAX_WORKERS must be 1 for the {backend} backend, whose skill runs one container"),
        ));
        return (Vec::new(), rule);
    }
    ((1..=max_workers).map(|index| format!("{prefix}-{index}")).collect(), rule)
}

// --- the resolved settings -------------------------------------------------------------------------------

/// The host paths resolved after [`Config::load`]: they come from `git rev-parse --show-toplevel` and a realpath,
/// which config resolution has no business running.
#[derive(Clone, Debug, PartialEq, Eq)]
struct HostPaths {
    repo: PathBuf,
    bazel_user_root: PathBuf,
}

/// Every setting one invocation runs with. Shared as `Arc<Config>`; the host paths are set once, later.
#[derive(Clone, Debug)]
pub struct Config {
    pub backend: Backend,
    pub guest_os: GuestOs,
    /// Fixed by the backend and this host ([`GuestArch::of`]); a test fixture may pin it.
    pub guest_arch: GuestArch,
    pub guest: &'static GuestOsProfile,

    /// The `tart` executable `TART_BIN` names, or `None` for the pinned Tart at [`TART_LABEL`].
    pub tart: Option<PathBuf>,
    pub parallels: String,
    pub parallels_peekaboo: String,
    /// The `docker` executable `DOCKER_BIN` names, or `None` for the pinned CLI at [`docker_cli_label`].
    ///
    /// Only a macOS host has a pinned CLI. On a Linux or a Windows host an unset `DOCKER_BIN` is `docker` on `PATH`,
    /// because no archive is pinned for those hosts. Any engine that speaks the Docker CLI serves: the backend runs
    /// nothing that is OrbStack's or Docker Desktop's own.
    pub docker: Option<PathBuf>,
    /// The engine address `DOCKER_HOST` names. The runner passes the variable to every child as it is. The
    /// setting takes part in the engine rule ([`DockerEngine::decide`]) and in the remedy of an engine that does not
    /// answer.
    pub docker_host: Option<String>,
    /// The engine the containers of a Docker pool run on ([`DockerEngine::decide`]); a test fixture may pin it.
    pub docker_engine: DockerEngine,
    /// The macOS release that the load read ([`MacosHost::read`]), or `None` on another host and when the version file
    /// names no version. The gate of the Apple `container` engine refuses a release older than
    /// [`CONTAINER_MACOS_MAJOR`].
    pub macos: Option<MacosHost>,
    /// The Apple `container` executable `CONTAINER_BIN` names, or `None` for the pinned CLI at [`CONTAINER_LABEL`].
    /// Only the Apple `container` engine reads it.
    pub container: Option<PathBuf>,
    /// The nameserver of a build and of a container on the Apple `container` engine (`AIR_VM_DNS`), or `None` for the
    /// default the engine derives from the host. The engine's own DNS proxy does not answer on every host.
    pub vm_dns: Option<String>,

    /// The repository a Docker worker's image is tagged in (`AIR_VM_DOCKER_IMAGE`). The tag itself is a digest of
    /// what the image is built from, so the repository is the only part an operator names.
    pub docker_image: String,
    /// What a Docker worker's image is built `FROM`, pinned by digest ([`pins::docker_base_image`]).
    pub docker_base_image: String,
    /// The registry path a Docker worker's image is pulled from before a build, and pushed to after one
    /// (`AIR_VM_DOCKER_REGISTRY`). The remote reference is `<registry>/<docker_image>:<tag>`, so the content tag
    /// stays the only pin. `None` when the operator set `off`: the controller then builds and never pulls.
    pub docker_registry: Option<String>,
    /// Whether the controller publishes the image it builds to [`Config::docker_registry`] (`AIR_VM_DOCKER_PUSH`),
    /// for every published platform. With it on, the controller always builds and never pulls. Off by default: a
    /// developer's build stays local, and a CI job or an operator opts in. On the Apple `container` engine the
    /// publish goes to [`Config::image_mirror`] instead, for the platform of the host only.
    pub docker_push: bool,
    /// The file mirror the Apple `container` engine pulls the worker image from, as an OCI archive
    /// (`AIR_VM_IMAGE_MIRROR`). The archive is `<mirror>/<docker_image>/<tag digest>-linux-<arch>.tar`. `None` when
    /// the operator set `off`: that engine then builds and never pulls. The other engines pull from
    /// [`Config::docker_registry`].
    pub image_mirror: Option<String>,
    /// The bearer token of an upload to [`Config::image_mirror`] (`AIR_VM_IMAGE_MIRROR_TOKEN`). Only a publish on the
    /// Apple `container` engine reads it, and a publish without it is refused.
    pub image_mirror_token: Option<Secret>,
    /// The host `curl` that downloads and uploads the image archive (`AIR_VM_HOST_CURL`).
    pub host_curl: String,

    /// The name of the one share of a worker, the Bazel output user root (`AIR_VM_BAZEL_SHARE_NAME`).
    pub bazel_share_name: String,

    configured_bazel_user_root: PathBuf,
    host_paths: OnceLock<HostPaths>,
    pub host_repo_override: Option<PathBuf>,

    pub vm_tmp: String,
    /// The guest directory that holds the files of `run --test-env NAME=@FILE` for one run (`AIR_VM_RUN_SECRETS`).
    ///
    /// The daemon's environment carries this path and never a value, because that environment is fixed at boot and
    /// is part of the launch digest. A Linux guest keeps it on `/dev/shm`, a tmpfs, so a secret never reaches the
    /// persistent data volume. A macOS guest has no tmpfs and keeps it under [`Config::vm_tmp`].
    pub vm_run_secrets: String,

    pub git: String,
    /// The guest's Node. The lane's tests need it rather than the controller: the daemon puts its directory first
    /// on the guest PATH, because a lane drives real agent CLIs that are Node programs.
    pub vm_node: String,

    pub vm_user: String,
    /// The uid an Aqua launch runs `launchctl asuser` against. The Docker image's `admin` is 1000.
    pub vm_uid: String,
    pub vm_home: String,
    pub vm_data: String,

    pub vm_runs_root: String,

    pub vm_ssh_host_key_fingerprint: String,
    /// Where the guest agent is installed.
    pub vm_agent: String,
    /// A prebuilt guest agent on the host to push instead of the one Bazel built.
    pub vm_agent_source: Option<PathBuf>,

    pub vm_cpu: u32,
    /// The memory of a worker VM in MiB (`AIR_VM_MEMORY_MB`): 32768 for a macOS guest.
    ///
    /// On the Apple `container` engine it is the memory of one worker, and the default is
    /// [`CONTAINER_WORKER_MEMORY_MIB`]. A container on the host engine has no cap of its own, so nothing reads the
    /// value there.
    pub vm_memory_mib: u32,
    /// The macOS guest's screen, `tart set --display` (`AIR_VM_RESOLUTION`). Not the Linux X display, which is
    /// [`Config::guest_display`].
    pub vm_display: String,
    /// The disk a Tart worker grows to (`AIR_VM_ROOT_DISK_GB`). Zero for every other backend: Parallels owns its own
    /// VM, and a container has no disk that this controller sizes.
    pub vm_root_disk_gb: u32,
    /// The X screen geometry a Docker worker's entrypoint gives Xvfb (`AIR_VM_SCREEN`), or `None` for its default.
    pub vm_screen: Option<String>,

    pub boot_timeout_seconds: u32,
    pub golden_vm: String,
    /// The X display a Linux worker's IDE opens on, so a display the guest already runs is picked up instead of
    /// a fresh headless one per IDE: `:88` on a Tart or a Docker worker, and `:1`, the Xvnc of the testing-ui
    /// container.
    pub guest_display: String,

    pub tart_home: PathBuf,
    /// Skips the version floor entirely, for bisecting a Tart regression.
    pub tart_version_override: bool,
    /// Drops the fsync path of the worker's disposable disk; empty for none.
    pub vm_root_disk_opts: String,
    /// `nat`, `softnet` or `bridged`.
    pub vm_network: String,
    /// The host interface `bridged` mode attaches the guest to; required in that mode.
    pub vm_bridged_interface: Option<String>,
    /// Required for `tart suspend`, which is what makes a lease acquisition cost seconds instead of a boot.
    pub vm_suspendable: bool,

    pub runtime_root: PathBuf,
    pub workers: Vec<String>,
    /// Why [`Config::workers`] has its length, for `status`.
    pub pool_rule: PoolRule,
    /// How long an unleased worker on the Apple `container` engine keeps running after a lease release
    /// (`AIR_VM_IDLE_STOP`), or `None` when it keeps running until `pool stop`.
    ///
    /// The default is [`IDLE_STOP_DEFAULT`]. Zero stops the worker inside the release. A running worker VM returns no
    /// memory to the host until it stops, so the stop is what gives the memory back. Every other engine and backend
    /// reads `None`: the host engine belongs to the operator, and a Tart worker suspends with `pool stop`.
    pub idle_stop: Option<Duration>,
    /// The physical memory of the host in MiB that the load read ([`HostFacts::memory_mib`]).
    pub host_memory_mib: Option<u64>,
    pub image_root: PathBuf,

    /// The `container.cmd` of the `testing-ui` skill, which starts and drives the testing-ui container: the script of
    /// the checkout that holds the workspace.
    pub container_linux_script: PathBuf,
    /// The output root of the `testing-ui` skill, where the controller reads the control port, its bearer and the
    /// display description: `out/testing-ui` of the checkout that holds the workspace.
    pub container_linux_root: PathBuf,
    /// What a run exports (`AIR_VM_EXPORT`).
    pub export: Export,
    /// Where a run exports to: [`VM_EXPORT_DIR`] of the checkout that holds the workspace.
    pub export_root: PathBuf,
    /// The host loopback port at which the container-linux `start` publishes the daemon's guest port
    /// (`AIR_VM_DAEMON_HOST_PORT`). The default derives from the checkout path, so two checkouts never share it and it
    /// stays the same across restarts.
    pub daemon_host_port: u16,

    /// The guest daemon's port and the budgets of its boot, health poll and watchdog (`AIR_VM_DAEMON_*`).
    pub daemon: DaemonBudgets,
}

/// The guest daemon's port and budgets, read once with every other setting.
///
/// The port is an input of the `@controller-boot` launch digest, so a changed port restarts the daemon: the
/// digest a previous process recorded is composed from the port it was configured with, and the next process
/// compares its own. The health budget is read on the host only and stays out of that digest.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct DaemonBudgets {
    /// The guest port the daemon listens on (`AIR_VM_DAEMON_PORT`).
    pub port: u16,
    /// From the supervisor start to a published state file (`AIR_VM_DAEMON_BOOT_TIMEOUT`).
    pub boot: Duration,
    /// From a published state file to the first `/status` that names this start's daemon
    /// (`AIR_VM_DAEMON_HEALTH_TIMEOUT`).
    ///
    /// The daemon binds its listener before it writes the state file, and `/status` takes no lock, so a guest with
    /// CPU to spare answers the first probe. The budget is for a guest that has none: at a host load above 50 on
    /// 2026-09-27, 60 s was not enough three times in a row.
    pub health: Duration,
    /// The per-execution watchdog budget (`AIR_VM_DAEMON_EXECUTION_TIMEOUT`).
    pub active_execution: Duration,
    /// The between-execution watchdog budget (`AIR_VM_DAEMON_PROGRESS_TIMEOUT`).
    pub progress_gap: Duration,
}

impl DaemonBudgets {
    fn read(reader: &mut Reader<'_>) -> Self {
        let port = reader.bounded_positive_int("AIR_VM_DAEMON_PORT", 27_100, u32::from(u16::MAX));
        Self {
            port: u16::try_from(port).unwrap_or(27_100),
            boot: reader.seconds("AIR_VM_DAEMON_BOOT_TIMEOUT", 1_200),
            health: reader.seconds("AIR_VM_DAEMON_HEALTH_TIMEOUT", 180),
            active_execution: reader.seconds("AIR_VM_DAEMON_EXECUTION_TIMEOUT", 1_800),
            progress_gap: reader.seconds("AIR_VM_DAEMON_PROGRESS_TIMEOUT", 300),
        }
    }
}

/// The side of a terminal that does not answer a colour query ([`THEME_VARIABLE`]).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Theme {
    Light,
    Dark,
}

/// How a run that a person reads is presented: the viewer, the dashboard and its palette.
///
/// Its own loader and not a part of [`Config::load`], because the dashboard is chosen before a selection, and so a
/// config, exists. Lenient by design: only `off` turns the viewer or the dashboard off, and a theme other than
/// `light` or `dark` is no theme, so the terminal is asked.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Presentation {
    /// Whether a prose run starts the trace viewer ([`VIEWER_OFF_VARIABLE`]).
    pub viewer: bool,
    /// Whether a run on a redrawn terminal draws the live dashboard ([`DASHBOARD_OFF_VARIABLE`]).
    pub dashboard: bool,
    pub theme: Option<Theme>,
}

impl Default for Presentation {
    fn default() -> Self {
        Self {
            viewer: true,
            dashboard: true,
            theme: None,
        }
    }
}

impl Presentation {
    pub fn load(environment: &Environment) -> Self {
        Self {
            viewer: environment.get(VIEWER_OFF_VARIABLE) != Some("off"),
            dashboard: environment.get(DASHBOARD_OFF_VARIABLE) != Some("off"),
            theme: match environment.get(THEME_VARIABLE) {
                Some("light") => Some(Theme::Light),
                Some("dark") => Some(Theme::Dark),
                _ => None,
            },
        }
    }
}

/// The setting of the repository share, which the controller no longer has. A set value is refused.
pub const REMOVED_REPO_SHARE_VARIABLE: &str = "AIR_VM_REPO_SHARE_NAME";

/// The setting that chose the engine of the Docker backend, which the controller no longer has: the macOS version
/// chooses it ([`DockerEngine::decide`]). A set value is refused.
pub const REMOVED_DOCKER_ENGINE_VARIABLE: &str = "AIR_VM_DOCKER_ENGINE";

/// The setting of the home of the Lima engine, which went with the engine. A set value is refused.
pub const REMOVED_LIMA_HOME_VARIABLE: &str = "AIR_VM_LIMA_HOME";

impl Config {
    /// Resolves one invocation's settings, or refuses the environment. `workspace_dir` is [`WORKSPACE_DIR`] of the
    /// checkout, which the image pipeline is found relative to. The caller reads the facts of this host once and
    /// hands them in ([`HostFacts`]).
    pub fn load(facts: HostFacts, selection: Selection, environment: &Environment, workspace_dir: &Path) -> Result<Self, Refusal> {
        Self::load_on(HostOs::CURRENT, facts, selection, environment, workspace_dir)
    }

    /// [`Config::load`] as a controller on `host` with the facts `facts` resolves it, so a test can resolve the
    /// settings of another host. Only a macOS host reads the macOS release.
    pub fn load_on(
        host: HostOs,
        facts: HostFacts,
        selection: Selection,
        environment: &Environment,
        workspace_dir: &Path,
    ) -> Result<Self, Refusal> {
        let Selection { backend, guest_os } = selection;
        if !host.drives(backend) {
            return Err(Refusal::new(
                "unsupported_host_backend",
                Exit::USAGE,
                format!(
                    "a {host} host drives the Docker and the container-linux backends, and --backend {selection} needs \
                     a macOS or a Linux host; pass --backend docker or container-linux"
                ),
            ));
        }
        // A limit of this controller, not of the hypervisor: what is macOS-shaped is the path (`launchctl asuser`,
        // the console-login wait, the autologin guard, Peekaboo behind TCC grants).
        if backend == Backend::Parallels && guest_os != GuestOs::Macos {
            return Err(Refusal::new(
                "unsupported_backend_operation",
                Exit::USAGE,
                "this controller's Parallels path is macOS-only; Parallels itself hosts other guests, but none is \
                 provisioned here and the path assumes an Aqua session",
            ));
        }
        // A limit of the engine: a container shares the kernel of the engine's Linux VM, so it cannot be macOS.
        let container = match backend {
            Backend::Docker => Some("Docker"),
            Backend::ContainerLinux => Some("container-linux"),
            Backend::Tart | Backend::Parallels => None,
        };
        if let Some(container) = container
            && guest_os != GuestOs::Linux
        {
            return Err(Refusal::new(
                "unsupported_backend_operation",
                Exit::USAGE,
                format!("a {container} worker is a Linux container; a macOS guest needs a VM, which --backend tart gives"),
            ));
        }
        // The Tart backend runs the sealed macOS golden only. The Linux guest is a Docker worker.
        if backend == Backend::Tart && guest_os == GuestOs::Linux {
            return Err(Refusal::new(
                "unsupported_backend_operation",
                Exit::USAGE,
                "a Tart worker is a macOS guest; a Linux guest is a Docker worker, which --backend docker gives",
            ));
        }
        let HostFacts { macos, memory_mib } = facts;
        let mut reader = Reader::new(environment);
        let linux = guest_os == GuestOs::Linux;
        let tart = backend == Backend::Tart;
        let container_linux = backend == Backend::ContainerLinux;
        let checkout = checkout_root(workspace_dir);

        // The home comes from the environment handed in, never from the process's own, so a caller's environment
        // is the whole input. Without one the runtime root, the Tart home and the Bazel user root would be relative
        // to the working directory, which names state nobody wrote; that is refused instead.
        let home_variables = host.home_variables();
        let home = if let Some(home) = home_variables.iter().find_map(|&variable| environment.get(variable)) {
            PathBuf::from(home)
        } else {
            let unset = match home_variables {
                [variable] => format!("{variable} is not set"),
                variables => format!("{} are not set", variables.join(" and ")),
            };
            reader.refuse(Refusal::invalid_environment(format!(
                "{unset}; the runtime root and the Tart home are built from it"
            )));
            PathBuf::new()
        };

        let runtime_root = reader.path("AIR_VM_RUNTIME_ROOT", || host.runtime_root(&home, environment));

        // The engine rule reads the two variables as they are set, before a default fills `docker`.
        let docker_bin = reader.optional("DOCKER_BIN").map(PathBuf::from);
        let docker_host = reader.optional("DOCKER_HOST");
        // The macOS version chooses the engine, and the controller has no Lima engine. A set value of a removed setting
        // is refused and not ignored.
        if reader.set(REMOVED_DOCKER_ENGINE_VARIABLE).is_some() {
            reader.refuse(Refusal::invalid_environment(format!(
                "{REMOVED_DOCKER_ENGINE_VARIABLE} is removed: the macOS version chooses the engine of the Docker backend \
                 (the decision 2026-10-13-the-lima-engine-is-retired of community/tools/vm/docs/decisions); unset it, or \
                 name an engine in DOCKER_BIN or DOCKER_HOST"
            )));
        }
        if reader.set(REMOVED_LIMA_HOME_VARIABLE).is_some() {
            reader.refuse(Refusal::invalid_environment(format!(
                "{REMOVED_LIMA_HOME_VARIABLE} is removed with the Lima engine (the decision \
                 2026-10-13-the-lima-engine-is-retired of community/tools/vm/docs/decisions); unset it"
            )));
        }
        let docker_engine = DockerEngine::decide(host, macos, docker_bin.is_some(), docker_host.is_some());
        let vm_dns = reader.optional("AIR_VM_DNS");
        if let Some(dns) = vm_dns.as_deref()
            && dns.parse::<std::net::IpAddr>().is_err()
        {
            reader.refuse(Refusal::invalid_environment(format!(
                "AIR_VM_DNS must be one IPv4 or IPv6 address of a nameserver, not {dns:?}"
            )));
        }
        let docker = docker_bin.or_else(|| (host != HostOs::Macos).then(|| PathBuf::from("docker")));

        // The memory cap of a worker, which holds the lane daemon and the lane IDE. An Apple `container` worker is a
        // VM of its own; see [`Config::vm_memory_mib`]. It resolves before the slots, because the Apple `container`
        // pool size follows it.
        let vm_memory_fallback = match (backend, docker_engine) {
            (Backend::Docker, DockerEngine::AppleContainer) => CONTAINER_WORKER_MEMORY_MIB,
            _ => 32_768,
        };
        let vm_memory_mib = reader.positive_int("AIR_VM_MEMORY_MB", vm_memory_fallback);

        // Read on every pool, so a malformed value is refused everywhere; only the Apple `container` engine uses it.
        let idle_stop = reader.seconds_or_off(IDLE_STOP_VARIABLE, Some(IDLE_STOP_DEFAULT));
        let idle_stop = idle_stop.filter(|_| backend == Backend::Docker && docker_engine == DockerEngine::AppleContainer);

        let (workers, pool_rule) = match backend {
            Backend::Tart | Backend::Docker | Backend::ContainerLinux => {
                worker_slots(&mut reader, backend, docker_engine, vm_memory_mib, memory_mib)
            }
            Backend::Parallels => (vec![reader.string("AIR_VM_PARALLELS_VM", "macOS")], PoolRule::ParallelsVm),
        };
        for worker in &workers {
            reader.name(worker, "worker name");
        }

        // Only the default differs per backend: the Parallels VM was set up with another account, and the testing-ui
        // container runs as the `ubuntu` account of the skill's image. The Docker image makes the account `admin`,
        // which the macOS golden has too, so the guest scripts see one layout.
        let default_user = match backend {
            Backend::Parallels => "test",
            Backend::ContainerLinux => "ubuntu",
            Backend::Tart | Backend::Docker => "admin",
        };
        let vm_user = reader.string("AIR_VM_USER", default_user);
        let home_root = if linux { "/home" } else { "/Users" };
        let vm_home = reader.string("AIR_VM_HOME", format!("{home_root}/{vm_user}"));
        let vm_data = reader.string("AIR_VM_DATA", format!("{vm_home}/WorkerData"));

        let network = reader.string("AIR_VM_NETWORK", "nat");
        if !matches!(network.as_str(), "nat" | "softnet" | "bridged") {
            reader.refuse(Refusal::invalid_environment(format!(
                r#"AIR_VM_NETWORK must be "nat", "softnet" or "bridged", not {network:?}"#
            )));
        }
        // Bridged mode names one host interface, and nothing here can pick it: a connected VPN puts the default
        // route on a `utun` device, which is not bridgeable. A wrong name is answered by tart itself, whose
        // refusal lists every interface the host can bridge onto.
        let bridged_interface = reader.optional("AIR_VM_BRIDGED_INTERFACE");
        if network == "bridged" && bridged_interface.is_none() {
            reader.refuse(Refusal::invalid_environment(
                "AIR_VM_NETWORK=bridged also needs AIR_VM_BRIDGED_INTERFACE, the host interface to bridge onto \
                 (for example \"en0\"); `tart run --net-bridged=list <vm>` lists the candidates",
            ));
        }

        let export = match reader.string("AIR_VM_EXPORT", "off").as_str() {
            "off" => Export::Off,
            "failed" => Export::Failed,
            "all" => Export::All,
            other => {
                reader.refuse(Refusal::invalid_environment(format!(
                    r#"AIR_VM_EXPORT must be "off", "failed" or "all", not {other:?}"#
                )));
                Export::Off
            }
        };

        let root_disk_opts = reader.string("AIR_VM_ROOT_DISK_OPTS", "caching=cached,sync=none");
        if !is_root_disk_options(&root_disk_opts) {
            reader.refuse(Refusal::invalid_environment(format!(
                "AIR_VM_ROOT_DISK_OPTS must be comma-separated tart root-disk options, not {root_disk_opts:?}"
            )));
        }

        // A worker has no repository share, because the guest reads no checkout. A set value would name a share that
        // nothing declares, so it is refused and not ignored.
        if reader.set(REMOVED_REPO_SHARE_VARIABLE).is_some() {
            reader.refuse(Refusal::invalid_environment(format!(
                "{REMOVED_REPO_SHARE_VARIABLE} is removed: a worker has no repository share, because the guest reads no \
                 checkout (the decision 2026-10-10-the-guest-reads-no-checkout of community/tools/vm/docs/decisions); unset it"
            )));
        }
        let bazel_share_name = reader.string("AIR_VM_BAZEL_SHARE_NAME", "air-macos-bazel");
        reader.name(&bazel_share_name, "Bazel share name");

        let vm_node_fallback = if linux {
            // The Docker image installs the pinned Node into `/usr/local`.
            "/usr/local/bin/node".to_owned()
        } else if tart {
            // The Tart golden image installs the pinned Node at a versioned Homebrew prefix.
            format!("/opt/homebrew/opt/node@{}/bin/node", pins::guest_node_major())
        } else {
            "/opt/homebrew/bin/node".to_owned()
        };
        let vm_uid_fallback = if linux { "1000" } else { "501" };
        let vm_root_disk_gb = if tart { tart_root_disk_gb(&mut reader) } else { 0 };

        let daemon = DaemonBudgets::read(&mut reader);

        let docker_image = reader.string("AIR_VM_DOCKER_IMAGE", "air-ui-worker");
        if !is_image_repository(&docker_image) {
            reader.refuse(Refusal::invalid_environment(format!(
                "AIR_VM_DOCKER_IMAGE must be an image repository without a tag (lowercase letters, digits, '.', \
                 '_', '-' and '/'), not {docker_image:?}; the controller adds the tag"
            )));
        }
        // `off` and not an empty value, because an empty variable reads as unset and would restore the default.
        let docker_registry =
            Some(reader.string("AIR_VM_DOCKER_REGISTRY", DOCKER_REGISTRY_DEFAULT)).filter(|registry| registry != DOCKER_REGISTRY_OFF);
        if let Some(registry) = docker_registry.as_deref()
            && !is_image_registry(registry)
        {
            reader.refuse(Refusal::invalid_environment(format!(
                "AIR_VM_DOCKER_REGISTRY must be a registry path (a lowercase host with an optional :port, then \
                 path components) or `{DOCKER_REGISTRY_OFF}`, not {registry:?}"
            )));
        }
        let image_mirror = Some(reader.string("AIR_VM_IMAGE_MIRROR", IMAGE_MIRROR_DEFAULT)).filter(|mirror| mirror != IMAGE_MIRROR_OFF);
        if let Some(mirror) = image_mirror.as_deref()
            && !is_mirror_url(mirror)
        {
            reader.refuse(Refusal::invalid_environment(format!(
                "AIR_VM_IMAGE_MIRROR must be an http:// or https:// URL without a trailing '/' and without \
                 whitespace, or `{IMAGE_MIRROR_OFF}`, not {mirror:?}"
            )));
        }
        let docker_push = reader.boolean("AIR_VM_DOCKER_PUSH", false);
        // The Apple `container` engine publishes to the file mirror, and every other engine to the registry.
        if docker_push && backend == Backend::Docker && docker_engine == DockerEngine::AppleContainer {
            if image_mirror.is_none() {
                reader.refuse(Refusal::invalid_environment(format!(
                    "AIR_VM_DOCKER_PUSH asks for a push and AIR_VM_IMAGE_MIRROR={IMAGE_MIRROR_OFF} names no file \
                     mirror to upload to; the Apple container engine publishes there"
                )));
            }
        } else if docker_push && docker_registry.is_none() {
            reader.refuse(Refusal::invalid_environment(format!(
                "AIR_VM_DOCKER_PUSH asks for a push and AIR_VM_DOCKER_REGISTRY={DOCKER_REGISTRY_OFF} names no \
                 registry to push to"
            )));
        }
        let vm_screen = reader.optional("AIR_VM_SCREEN");
        if let Some(screen) = vm_screen.as_deref()
            && !is_screen_geometry(screen)
        {
            reader.refuse(Refusal::invalid_environment(format!(
                "AIR_VM_SCREEN must be <width>x<height>x<depth> in digits, like 1920x1080x24, not {screen:?}"
            )));
        }

        let vm_tmp = reader.string("AIR_VM_TMP", format!("{vm_data}/tmp"));
        // Derived and never overridden: `daemon stop` and a lease release remove this directory whole, so it names
        // only what the controller itself writes. The variable is the daemon's, which reads the path from it; on the
        // host it may only repeat the derived path.
        let vm_run_secrets = run_secrets_dir(guest_os, &vm_tmp);
        if let Some(given) = reader.optional("AIR_VM_RUN_SECRETS")
            && given != vm_run_secrets
        {
            reader.refuse(Refusal::invalid_environment(format!(
                "AIR_VM_RUN_SECRETS is not a setting: the controller derives it ({vm_run_secrets} for this guest) and \
                 removes it whole, so it refuses {given:?}; unset it"
            )));
        }

        let config = Self {
            backend,
            guest_os,
            guest_arch: GuestArch::of(backend),
            guest: selection.profile(),
            // `TART_BIN` and `TART_HOME` keep the Tart ecosystem's names: `TART_HOME` is Tart's own variable, which
            // the tart binary and the image scripts read too, and ADR 0158 records `TART_BIN` as its override.
            tart: reader.optional("TART_BIN").map(PathBuf::from),
            parallels: reader.string("AIR_VM_PARALLELS_BIN", "prlctl"),
            parallels_peekaboo: reader.string("AIR_VM_PEEKABOO_BIN", "/opt/homebrew/bin/peekaboo"),
            docker,
            docker_host,
            docker_engine,
            macos: macos.filter(|_| host == HostOs::Macos),
            container: reader.optional("CONTAINER_BIN").map(PathBuf::from),
            vm_dns,
            docker_image,
            docker_base_image: pins::docker_base_image().to_owned(),
            docker_registry,
            docker_push,
            image_mirror,
            image_mirror_token: reader.optional("AIR_VM_IMAGE_MIRROR_TOKEN").map(Secret),
            host_curl: reader.string("AIR_VM_HOST_CURL", "curl"),
            bazel_share_name,
            configured_bazel_user_root: reader.path("AIR_VM_BAZEL_USER_ROOT", || host.bazel_user_root(&home)),
            host_paths: OnceLock::new(),
            host_repo_override: reader.optional("AIR_VM_HOST_REPO").map(PathBuf::from),
            vm_tmp,
            vm_run_secrets,
            git: reader.string("AIR_VM_HOST_GIT", "git"),
            vm_node: reader.string("AIR_VM_NODE", vm_node_fallback),
            vm_user,
            vm_uid: reader.string("AIR_VM_UID", vm_uid_fallback),
            vm_home,
            vm_runs_root: reader.string("AIR_VM_RUNS_ROOT", format!("{vm_data}/state/ui-runs")),
            vm_ssh_host_key_fingerprint: reader.string(
                "AIR_VM_SSH_HOST_KEY_FINGERPRINT",
                format!("{vm_data}/state/ssh-host-key-fingerprint"),
            ),
            vm_agent: reader.string("AIR_VM_GUEST_AGENT", format!("{vm_data}/state/vm-guest-agent")),
            vm_agent_source: reader.optional("AIR_VM_GUEST_AGENT_SOURCE").map(PathBuf::from),
            vm_data,
            vm_cpu: reader.positive_int("AIR_VM_CPU", 8),
            vm_memory_mib,
            vm_display: reader.string("AIR_VM_RESOLUTION", "1920x1080px"),
            vm_root_disk_gb,
            vm_screen,
            boot_timeout_seconds: reader.positive_int("AIR_VM_BOOT_TIMEOUT", 180),
            golden_vm: reader.string("AIR_VM_GOLDEN_VM", pins::tart_golden_vm()),
            guest_display: reader.string("AIR_VM_DISPLAY", if container_linux { ":1" } else { ":88" }),
            tart_home: reader.path("TART_HOME", || home.join(".tart")),
            tart_version_override: reader.boolean(TART_VERSION_OVERRIDE_VARIABLE, false),
            vm_root_disk_opts: root_disk_opts,
            vm_network: network,
            vm_bridged_interface: bridged_interface,
            vm_suspendable: reader.boolean("AIR_VM_SUSPENDABLE", true),
            runtime_root,
            workers,
            pool_rule,
            idle_stop,
            host_memory_mib: memory_mib,
            image_root: reader.path("AIR_VM_IMAGE_ROOT", || workspace_dir.join("provision")),
            container_linux_script: checkout.join(".agents/skills/testing-ui/scripts/container.cmd"),
            container_linux_root: checkout.join("out/testing-ui"),
            export,
            export_root: checkout.join(VM_EXPORT_DIR),
            daemon_host_port: {
                let fallback = derived_host_port(&checkout);
                let port = reader.bounded_positive_int("AIR_VM_DAEMON_HOST_PORT", u32::from(fallback), u32::from(u16::MAX));
                u16::try_from(port).unwrap_or(fallback)
            },
            daemon,
        };
        reader.finish(config)
    }

    // --- host paths --------------------------------------------------------------------------------------

    /// The host checkout the guest reads through the read-only share, refused before it has been resolved: an
    /// empty repository path does not fail loudly, it declares a share at `""` and a mount fails much later for a
    /// reason naming nothing.
    pub fn host_repo(&self) -> Result<&Path, Refusal> {
        self.resolved("the host repository").map(|paths| paths.repo.as_path())
    }

    /// The host Bazel output root the second share exposes, resolved with the repository and refused before then.
    pub fn host_bazel_user_root(&self) -> Result<&Path, Refusal> {
        self.resolved("the host Bazel user root")
            .map(|paths| paths.bazel_user_root.as_path())
    }

    fn resolved(&self, what: &str) -> Result<&HostPaths, Refusal> {
        self.host_paths.get().ok_or_else(|| {
            Refusal::new(
                "host_paths_unresolved",
                Exit::FAILURE,
                format!("{what} has not been resolved; ensure_host_paths must run before a share is declared"),
            )
        })
    }

    /// Records the resolved host paths, both at once: a config with one of them is a config no reader can trust.
    /// Set once; setting other paths later is refused rather than silently replacing what readers already saw.
    pub fn set_host_paths(&self, repo: impl Into<PathBuf>, bazel_user_root: impl Into<PathBuf>) -> Result<(), Refusal> {
        let paths = HostPaths {
            repo: repo.into(),
            bazel_user_root: bazel_user_root.into(),
        };
        if paths.repo.as_os_str().is_empty() || paths.bazel_user_root.as_os_str().is_empty() {
            return Err(Refusal::new(
                "host_paths_unresolved",
                Exit::FAILURE,
                format!(
                    "a host path resolved to nothing (repo \"{}\", bazel user root \"{}\")",
                    paths.repo.display(),
                    paths.bazel_user_root.display()
                ),
            ));
        }
        let set = self.host_paths.get_or_init(|| paths.clone());
        if *set != paths {
            return Err(Refusal::new(
                "host_paths_conflict",
                Exit::FAILURE,
                format!(
                    "the host paths were already resolved to {} and {}, not {} and {}",
                    set.repo.display(),
                    set.bazel_user_root.display(),
                    paths.repo.display(),
                    paths.bazel_user_root.display()
                ),
            ));
        }
        Ok(())
    }

    /// What the environment named for the Bazel user root, before resolution: what the resolver realpaths.
    pub fn configured_bazel_user_root(&self) -> &Path {
        &self.configured_bazel_user_root
    }

    // --- guest paths -------------------------------------------------------------------------------------

    /// [`Config::vm_run_secrets`], once it is proven to be the form [`run_secrets_dir`] derives for this guest, an
    /// absolute path with no `.` or `..` component: what `daemon stop` and a lease release hand to `rm -rf`.
    ///
    /// The load derives nothing else, but a `Config` is a plain value, and a removal of the whole directory must not
    /// rest on every constructor having gone through the load.
    pub fn removable_run_secrets_dir(&self) -> Result<&str, Refusal> {
        let directory = self.vm_run_secrets.as_str();
        let derived = run_secrets_dir(self.guest_os, &self.vm_tmp);
        let plain = directory.starts_with('/') && directory.split('/').skip(1).all(|part| !matches!(part, "" | "." | ".."));
        if directory == derived && plain {
            Ok(directory)
        } else {
            Err(Refusal::internal(format!(
                "the run secrets directory {directory:?} is not the controller's own ({derived:?} for a {} guest), so \
                 it is not removed",
                self.guest_os.as_str()
            )))
        }
    }

    /// Whether the guest has an Aqua session, TCC, and the rest of the macOS-only path.
    pub fn is_macos_guest(&self) -> bool {
        self.guest_os == GuestOs::Macos
    }

    // --- the runtime root --------------------------------------------------------------------------------

    /// The directory key one worker gets under the runtime root, for its state and its artifacts alike. A
    /// Parallels worker is `parallels-<name>`, because its name comes from the user's own VM and could collide
    /// with a Tart slot. A Docker worker is `docker-<name>` for the same reason: `AIR_VM_WORKER_PREFIX` can give a
    /// container the name of a Tart slot. A container-linux worker is `container-linux-<name>`, for the same reason again.
    pub fn worker_key(&self, worker: &str) -> String {
        match self.backend {
            Backend::Parallels => format!("parallels-{worker}"),
            Backend::Docker => format!("docker-{worker}"),
            Backend::ContainerLinux => format!("container-linux-{worker}"),
            Backend::Tart => worker.to_owned(),
        }
    }

    /// Where the build a boot performs writes its log: pool-wide, because what it produces (the guest agent) is the
    /// same for every worker of a guest.
    pub fn guest_boot_build_log_path(&self) -> PathBuf {
        self.runtime_root.join("guest-boot-build.log")
    }

    /// A worker's private host directory: `workers/<key>`.
    pub fn worker_dir(&self, worker: &str) -> PathBuf {
        self.runtime_root.join("workers").join(self.worker_key(worker))
    }

    pub fn worker_provenance_path(&self, worker: &str) -> PathBuf {
        self.worker_dir(worker).join("provenance.json")
    }

    pub fn pid_path(&self, worker: &str) -> PathBuf {
        self.worker_dir(worker).join("tart.pid")
    }

    pub fn tart_log_path(&self, worker: &str) -> PathBuf {
        self.worker_dir(worker).join("tart.log")
    }

    pub fn suspended_state_path(&self, worker: &str) -> PathBuf {
        self.worker_dir(worker).join("suspended.json")
    }

    pub fn lease_path(&self, worker: &str) -> PathBuf {
        self.worker_dir(worker).join("lease.json")
    }

    /// The run marker of a worker: it names the run in flight on the worker, its lease and its process.
    pub fn run_marker_path(&self, worker: &str) -> PathBuf {
        self.worker_dir(worker).join("run.json")
    }

    /// The `docker create` arguments a Docker worker's container was made with. The Docker twin of
    /// [`Config::suspended_state_path`]: a record that differs from the current arguments means the container
    /// declares other shares or another image, and is made again.
    pub fn docker_create_record_path(&self, worker: &str) -> PathBuf {
        self.worker_dir(worker).join("docker-create.json")
    }

    /// The deadline of the idle stop of an unleased worker, which a lease release writes ([`Config::idle_stop`]). A
    /// lease acquisition and a start remove it.
    pub fn idle_stop_record_path(&self, worker: &str) -> PathBuf {
        self.worker_dir(worker).join("idle-stop.json")
    }

    /// Where the detached process of the idle stop writes its output.
    pub fn idle_stop_log_path(&self, worker: &str) -> PathBuf {
        self.worker_dir(worker).join("idle-stop.log")
    }

    /// Where the image build writes its log: pool-wide, because every Docker worker runs the one image.
    pub fn docker_build_log_path(&self) -> PathBuf {
        self.runtime_root.join("docker-build.log")
    }

    /// Where the image pull writes its log. A failed pull is what an operator reads when a start built instead.
    pub fn docker_pull_log_path(&self) -> PathBuf {
        self.runtime_root.join("docker-pull.log")
    }

    /// Where the image push writes its log.
    pub fn docker_push_log_path(&self) -> PathBuf {
        self.runtime_root.join("docker-push.log")
    }

    /// The record of where the pool's image came from, pulled or built: pool-wide, like the image.
    pub fn docker_image_record_path(&self) -> PathBuf {
        self.runtime_root.join("docker-image.json")
    }

    /// The lock around the image build: pool-wide, like the build log and the build context it guards.
    pub fn docker_image_lock_path(&self) -> PathBuf {
        self.runtime_root.join("docker-image.lock")
    }

    /// Whether this pool is a Docker pool on the Apple `container` engine.
    pub fn runs_container_engine(&self) -> bool {
        self.backend == Backend::Docker && self.docker_engine == DockerEngine::AppleContainer
    }

    /// Where `container system start` writes its log.
    pub fn container_system_log_path(&self) -> PathBuf {
        self.runtime_root.join("container-system.log")
    }

    /// The `DOCKER_CONFIG` of the pinned CLI, on every engine. Its `config.json` names the directory of the pinned
    /// `docker-buildx` plugin. A `docker login` for a publish goes to this directory too, because the CLI does not
    /// read `~/.docker/config.json` then.
    pub fn docker_config_dir(&self) -> PathBuf {
        self.runtime_root.join("docker-config")
    }

    /// Refuses a worker this pool does not contain. A worker name arrives from a lease receipt, a flag and an
    /// aggregate, and not checking it is operating on a VM of another pool, or of the user.
    pub fn require_pool_worker(&self, worker: &str) -> Result<(), Refusal> {
        if self.workers.iter().any(|known| known == worker) {
            return Ok(());
        }
        Err(Refusal::new(
            "unknown_worker",
            Exit::USAGE,
            format!("{worker} is not in this pool ({})", self.workers.join(", ")),
        ))
    }
}

/// An image repository as `docker build --tag` takes it before the `:tag`: lowercase path components joined by `/`.
/// A `:` is refused because the controller appends the tag, and a second one would name another image.
fn is_image_repository(value: &str) -> bool {
    !value.is_empty()
        && value.split('/').all(|component| {
            !component.is_empty()
                && component
                    .bytes()
                    .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit() || matches!(byte, b'.' | b'_' | b'-'))
        })
}

/// The registry path a worker image is pulled from and pushed to unless `AIR_VM_DOCKER_REGISTRY` says otherwise.
/// Anonymous pull works there, so a lane host needs no login; a push needs one.
pub const DOCKER_REGISTRY_DEFAULT: &str = "registry.jetbrains.team/p/ij/containers-public";

/// The `AIR_VM_DOCKER_REGISTRY` value that turns the pull and the push off.
pub const DOCKER_REGISTRY_OFF: &str = "off";

/// The file mirror the Apple `container` engine pulls the worker image archive from unless `AIR_VM_IMAGE_MIRROR` says
/// otherwise. Anonymous download works there, so a lane host needs no token; an upload needs one.
pub const IMAGE_MIRROR_DEFAULT: &str = "https://cache-redirector.jetbrains.com/files/p/ij/intellij-build-dependencies";

/// The `AIR_VM_IMAGE_MIRROR` value that turns the mirror pull and the mirror publish off.
pub const IMAGE_MIRROR_OFF: &str = "off";

/// A mirror URL the controller appends `/<repository>/<archive>` to: an `http://` or `https://` URL with a host, no
/// trailing `/`, and no whitespace or control character, because the value goes into an argv and a note.
fn is_mirror_url(value: &str) -> bool {
    let rest = value
        .strip_prefix("https://")
        .or_else(|| value.strip_prefix("http://"))
        .unwrap_or_default();
    !rest.is_empty()
        && !rest.starts_with('/')
        && !rest.ends_with('/')
        && !rest.chars().any(|character| character.is_whitespace() || character.is_control())
}

/// A credential read from the environment. Its `Debug` hides the value, because a [`Config`] can be printed into a
/// log or a refusal.
#[derive(Clone, PartialEq, Eq)]
pub struct Secret(String);

impl Secret {
    /// The value, for the one place that hands it to the service it is for.
    pub fn expose(&self) -> &str {
        &self.0
    }
}

impl fmt::Debug for Secret {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str("Secret(..)")
    }
}

/// A registry path as the head of an image reference: a host, an optional `:port` on it, then repository path
/// components in the grammar of [`is_image_repository`]. No trailing `/`, because the controller joins with one.
fn is_image_registry(value: &str) -> bool {
    let (host, path) = match value.split_once('/') {
        Some((host, path)) => (host, Some(path)),
        None => (value, None),
    };
    let host_name = match host.split_once(':') {
        Some((name, port)) => {
            if port.is_empty() || !port.bytes().all(|byte| byte.is_ascii_digit()) {
                return false;
            }
            name
        }
        None => host,
    };
    is_image_repository(host_name) && path.is_none_or(is_image_repository)
}

/// An X screen geometry as `air-display` accepts it: three digit groups joined by `x`, like `1920x1080x24`.
///
/// The same grammar as the entrypoint's, so a bad value is refused here, before a container is made. The entrypoint
/// would refuse it too, but only after the container starts, and then the start waits for a guest that never comes.
fn is_screen_geometry(value: &str) -> bool {
    let groups: Vec<&str> = value.split('x').collect();
    groups.len() == 3
        && groups
            .iter()
            .all(|group| !group.is_empty() && group.bytes().all(|byte| byte.is_ascii_digit()))
}

/// The checkout that holds the controller workspace `community/tools/vm`: its third ancestor. A shallower directory,
/// which only a test fixture passes, is its own checkout.
fn checkout_root(workspace_dir: &Path) -> PathBuf {
    workspace_dir.ancestors().nth(3).unwrap_or(workspace_dir).to_path_buf()
}

/// A loopback port in 12000..20000 from the checkout path, FNV-1a over its bytes: stable across restarts, different
/// between checkouts, and clear of the skill's own derived ports and of the ephemeral range.
fn derived_host_port(checkout: &Path) -> u16 {
    let hash = checkout.to_string_lossy().bytes().fold(0xcbf2_9ce4_8422_2325_u64, |hash, byte| {
        (hash ^ u64::from(byte)).wrapping_mul(0x0100_0000_01b3)
    });
    12_000 + u16::try_from(hash % 8_000).unwrap_or_default()
}
