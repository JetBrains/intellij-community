//! The host a pool runs on: a temporary root, the Tart fake, the environment naming both, and the settings loaded
//! from it. Hermetic: no VM, no network, no Bazel.
//!
//! The hypervisor is [`avl_testkit::tartfake`]'s shell script with the binary variable pointed at it, kept a real
//! process because the argv shape is half of what has to be checked. A Docker pool gets the fake `docker` beside
//! the fake `tart`, named by `DOCKER_BIN`. The guest agent is a plain file named by
//! `AIR_VM_GUEST_AGENT_SOURCE`, so nothing asks Bazel where a binary is: a named source is answered by a stat.
//!
//! A Docker pool can also leave `DOCKER_BIN` unset and run the pinned CLI: then [`HostPool::pinned_bazel`] resolves
//! the pinned Docker CLI to the fake of the same directory. Or it runs on the Apple `container` engine, with the fake
//! `container` of the same directory named by `CONTAINER_BIN`, and the fake `curl` of the same directory named by
//! `AIR_VM_HOST_CURL`, which stands in for the file mirror.
//!
//! A container-linux pool lays the fakes out as a checkout holds the testing-ui skill, `.agents/skills/testing-ui/scripts`
//! under the temporary root, with `out/testing-ui` beside it: the backend and the fake script derive both from the checkout. The pool
//! also gets a [`FakeControlPort`], which the fake script's `start` names in `container.ctl_port`. The guests behind
//! that port are [`HostPool::control_port`]'s, so a suite over the production channel seeds the same guest answers as
//! one over a scripted channel.
//!
//! A Windows host has no fake `tart` and the Docker backend only. There the directory holds the fake `docker` alone,
//! and `TART_BIN` is not set.

use std::path::{Path, PathBuf};
use std::sync::Arc;

use avl_base::config::{HostOs, MacosHost};
use avl_base::{Backend, Config, GuestArch, GuestOs};
use avl_host_sys::Runner;
use avl_testkit::tartfake::{Answer, Binary, Fake};
use tempfile::TempDir;

#[cfg(unix)]
use crate::bazel::PinnedBazel;
#[cfg(unix)]
use crate::channel::FakeGuests;
#[cfg(unix)]
use crate::control_port::FakeControlPort;
use crate::git::FakeGit;

/// Settings over a fake hypervisor and a temporary root, and the environment they were loaded from.
pub struct HostPool {
    pub settings: Arc<Config>,
    /// The fake `tart`, and `prlctl` beside it when the pool was built [`HostPoolBuilder::with_parallels`], and
    /// `docker` beside it in a Docker pool, and `container.cmd` beside it in a container-linux pool: one directory, so
    /// they share every answer and one call log.
    pub fake: Fake,
    /// Every variable the settings were loaded from, for a runner or a second load.
    pub environment: Vec<(String, String)>,
    git: Option<FakeGit>,
    /// The control port of a container-linux pool, and the guests behind it.
    #[cfg(unix)]
    control_port: Option<FakeControlPort>,
    root: TempDir,
}

/// How a [`HostPool`] differs from the default: a Tart fake named by `TART_BIN`, host paths declared as the root,
/// an arm64 guest whatever the host runs on.
#[expect(
    clippy::struct_excessive_bools,
    reason = "each flag is one independent option a builder method sets"
)]
pub struct HostPoolBuilder {
    backend: Backend,
    guest_os: GuestOs,
    guest_arch: GuestArch,
    macos: MacosHost,
    tart_version: String,
    parallels: bool,
    git: bool,
    tart_bin: bool,
    docker_bin: bool,
    names_engine: bool,
    container_engine: bool,
    host_paths: bool,
    overrides: Vec<(String, String)>,
}

impl HostPool {
    /// A pool of one backend and guest, over a fake `tart` that answers `--version` with `tart_version`.
    pub fn builder(backend: Backend, guest_os: GuestOs, tart_version: &str) -> HostPoolBuilder {
        HostPoolBuilder {
            backend,
            guest_os,
            guest_arch: GuestArch::Arm64,
            macos: crate::FIXTURE_MACOS,
            tart_version: tart_version.to_owned(),
            parallels: false,
            git: false,
            tart_bin: true,
            docker_bin: true,
            names_engine: true,
            container_engine: false,
            host_paths: true,
            overrides: Vec::new(),
        }
    }

    /// The temporary root every host path of the pool is under; also the host checkout.
    pub fn root(&self) -> &Path {
        self.root.path()
    }

    /// A runner over the pool's environment, registering with a service no signal reaches.
    pub fn runner(&self) -> Runner {
        crate::runner(&self.environment)
    }

    /// A Bazel that resolves every pinned tool to its fake in the pool's directory: the Tart, and the Docker CLI of the
    /// pool's architecture with a stand-in `docker-buildx`.
    #[cfg(unix)]
    pub fn pinned_bazel(&self) -> PinnedBazel {
        let arch = self.settings.guest_arch;
        let plugin = self.root().join("docker-buildx");
        std::fs::write(&plugin, "a stand-in plugin\n").expect("the stand-in plugin is written");
        PinnedBazel::tart(self.fake.executable())
            .with_docker(arch, &self.fake.directory().join(Binary::Docker.file_name()))
            .with_buildx(arch, &plugin)
    }

    /// The fake host `git` of a pool built [`HostPoolBuilder::with_git`].
    pub const fn git(&self) -> &FakeGit {
        self.git.as_ref().expect("the pool was built without a fake git")
    }

    /// The fake control port of a pool built for [`Backend::ContainerLinux`], and the guests behind it.
    #[cfg(unix)]
    pub const fn control_port(&self) -> &FakeControlPort {
        self.control_port
            .as_ref()
            .expect("the pool was built for another backend than container-linux")
    }

    /// Runs the fake `container.cmd start` of a container-linux pool, so the container reads as running and the files of
    /// its control port are there, without a boot. The call is forgotten with every earlier call, so a suite that
    /// seeds a running container first reads only its own calls.
    #[cfg(unix)]
    pub fn start_container_linux_container(&self) {
        let script = self.fake.directory().join(Binary::ContainerLinux.file_name());
        let worker = &self.settings.workers[0];
        let status = std::process::Command::new(&script)
            .arg("start")
            .arg(self.settings.worker_dir(worker))
            .envs(self.environment.iter().map(|(name, value)| (name, value)))
            .status()
            .unwrap_or_else(|error| panic!("spawn {}: {error}", script.display()));
        assert!(status.success(), "the fake container.cmd start exited with {status}");
        self.fake.forget_calls();
    }
}

impl HostPoolBuilder {
    /// Installs the fake `prlctl` beside `tart` and names it by `AIR_VM_PARALLELS_BIN`.
    #[must_use]
    pub const fn with_parallels(mut self) -> Self {
        self.parallels = true;
        self
    }

    /// Pins the guest's architecture, and with it the platform the fake `docker` engine reports.
    ///
    /// Pinned to arm64 by default and not left to [`GuestArch::of`], so that a suite asserts the same labels and
    /// the same engine answers on an Apple-silicon developer host and on a Linux x86_64 CI agent.
    #[must_use]
    pub const fn guest_arch(mut self, guest_arch: GuestArch) -> Self {
        self.guest_arch = guest_arch;
        self
    }

    /// Loads the settings on the macOS release `macos` instead of [`crate::FIXTURE_MACOS`]. Only a pool that loads as a
    /// macOS host reads it.
    #[must_use]
    pub const fn on_macos(mut self, macos: MacosHost) -> Self {
        self.macos = macos;
        self
    }

    /// Installs a [`FakeGit`] and names it by `AIR_VM_HOST_GIT`.
    #[must_use]
    pub const fn with_git(mut self) -> Self {
        self.git = true;
        self
    }

    /// Leaves `TART_BIN` unset, so the Tart backend resolves the pinned Tart through Bazel.
    #[must_use]
    pub const fn without_tart_bin(mut self) -> Self {
        self.tart_bin = false;
        self
    }

    /// Leaves `DOCKER_BIN` unset and loads the settings as a macOS host does, so the Docker backend resolves the pinned
    /// CLI through Bazel. `DOCKER_HOST` names an engine, so the engine rule keeps the host engine. The fake `docker`
    /// stays in the directory for [`HostPool::pinned_bazel`].
    #[must_use]
    pub const fn without_docker_bin(mut self) -> Self {
        self.docker_bin = false;
        self
    }

    /// Leaves `DOCKER_BIN` and `DOCKER_HOST` unset and loads the settings as a macOS host does, so the engine rule
    /// decides by the macOS release ([`HostPoolBuilder::on_macos`]). An older release keeps the host engine with the
    /// pinned CLI, which [`HostPool::pinned_bazel`] resolves. The fake `docker` stays in the directory for it.
    #[must_use]
    pub const fn without_named_engine(mut self) -> Self {
        self.docker_bin = false;
        self.names_engine = false;
        self
    }

    /// Runs the Docker pool on the Apple `container` engine: the settings load as a macOS host of
    /// [`crate::FIXTURE_MACOS`] loads them, with neither `DOCKER_BIN` nor `DOCKER_HOST`, so the real engine rule
    /// chooses the engine. `CONTAINER_BIN` names the fake `container` beside the fake `docker`, and `AIR_VM_DNS` names
    /// a nameserver, so no suite reads the resolver of its host. `AIR_VM_HOST_CURL` names the fake `curl`, so no suite
    /// downloads from the file mirror. `AIR_VM_IDLE_STOP` is `off`, so no release starts a detached process.
    #[must_use]
    pub const fn with_container_engine(mut self) -> Self {
        self.container_engine = true;
        self.docker_bin = false;
        self.names_engine = false;
        self
    }

    /// Leaves the host paths unresolved, for the gates that must resolve them themselves. Declared ones are what
    /// every command that resolves them first leaves behind, and resolving would run a real `git rev-parse`.
    #[must_use]
    pub const fn unresolved_host_paths(mut self) -> Self {
        self.host_paths = false;
        self
    }

    /// Sets one variable, replacing the pool's own value of it.
    #[must_use]
    pub fn env(mut self, name: &str, value: &str) -> Self {
        self.overrides.push((name.to_owned(), value.to_owned()));
        self
    }

    pub fn build(self) -> HostPool {
        let root = tempfile::tempdir().expect("a temporary root");
        let fake = if self.backend == Backend::ContainerLinux {
            // The checkout layout, because the backend and the fake script both derive their paths from it.
            Fake::install_in(root.path().join(".agents/skills/testing-ui/scripts"), &self.tart_version)
        } else if cfg!(unix) {
            Fake::install(&self.tart_version)
        } else {
            Fake::install_binary(Binary::Docker, &self.tart_version)
        };
        // An empty JSON listing rather than no output: Tart knowing no VMs is `[]`, and no output is a protocol
        // failure. The quiet listing stays unseeded, so a slot does not exist and is cloned before it is run.
        fake.answer(Answer::ListJson, "[]");
        let path = |relative: &str| root.path().join(relative).to_string_lossy().into_owned();
        let agent = stand_in_agent(root.path());
        let mut environment: Vec<(String, String)> = vec![
            ("HOME".to_owned(), path("")),
            ("PATH".to_owned(), std::env::var("PATH").unwrap_or_default()),
            ("TART_HOME".to_owned(), path("tart-home")),
            ("AIR_VM_IMAGE_ROOT".to_owned(), path("")),
            ("AIR_VM_RUNTIME_ROOT".to_owned(), path("runtime")),
            ("AIR_VM_HOST_REPO".to_owned(), path("")),
            ("AIR_VM_BAZEL_USER_ROOT".to_owned(), path("")),
            ("AIR_VM_GUEST_AGENT_SOURCE".to_owned(), agent.to_string_lossy().into_owned()),
        ];
        if self.tart_bin && cfg!(unix) {
            environment.push(("TART_BIN".to_owned(), fake.executable().to_string_lossy().into_owned()));
        }
        if self.backend == Backend::Docker {
            let docker = if cfg!(unix) {
                fake.install_beside(Binary::Docker)
            } else {
                fake.clone()
            };
            if self.docker_bin {
                environment.push(("DOCKER_BIN".to_owned(), docker.executable().to_string_lossy().into_owned()));
            } else if self.names_engine {
                // The pinned CLI against an engine the environment names, so a macOS host keeps the host engine on
                // every release. The fake `docker` reads no variable.
                environment.push(("DOCKER_HOST".to_owned(), "unix:///nonexistent/docker.sock".to_owned()));
            }
            if self.container_engine {
                let container = fake.install_beside(Binary::Container);
                let curl = fake.install_beside(Binary::Curl);
                environment.extend([
                    ("CONTAINER_BIN".to_owned(), container.executable().to_string_lossy().into_owned()),
                    ("AIR_VM_DNS".to_owned(), "192.0.2.53".to_owned()),
                    ("AIR_VM_HOST_CURL".to_owned(), curl.executable().to_string_lossy().into_owned()),
                    // No idle stop, because a release would start a detached process. A suite of the idle stop sets it.
                    ("AIR_VM_IDLE_STOP".to_owned(), "off".to_owned()),
                ]);
            }
            // One slot, because the fake `docker` holds one container. The production pool has two or more; a suite that
            // needs more slots names them with `AIR_VM_MAX_WORKERS`.
            environment.push(("AIR_VM_MAX_WORKERS".to_owned(), "1".to_owned()));
            // The engine runs the pinned architecture natively, so the gate passes unless a suite reseeds it.
            fake.answer(Answer::DockerVersion, format!("linux/{}\n", self.guest_arch.oci_arch()));
        }
        #[cfg(unix)]
        if self.backend == Backend::ContainerLinux {
            drop(fake.install_beside(Binary::ContainerLinux));
            std::fs::create_dir_all(root.path().join("out").join("testing-ui")).expect("the testing-ui root is created");
            // The one slot the skill's script runs; the settings refuse another count.
            environment.push(("AIR_VM_MAX_WORKERS".to_owned(), "1".to_owned()));
        }
        if self.parallels {
            let parallels = fake.install_beside(Binary::Parallels);
            environment.push((
                "AIR_VM_PARALLELS_BIN".to_owned(),
                parallels.executable().to_string_lossy().into_owned(),
            ));
        }
        let git = self.git.then(FakeGit::install);
        if let Some(git) = &git {
            environment.push(("AIR_VM_HOST_GIT".to_owned(), git.path().to_string_lossy().into_owned()));
        }
        for (name, value) in self.overrides {
            environment.retain(|(existing, _)| *existing != name);
            environment.push((name, value));
        }
        // The pinned CLI and the Apple `container` engine are what a macOS host resolves, so such a pool loads as one.
        let host = if self.backend == Backend::Docker && !self.docker_bin {
            HostOs::Macos
        } else {
            HostOs::CURRENT
        };
        let mut settings = crate::load_config_on_release(host, self.macos, self.backend, self.guest_os, &environment, root.path());
        settings.guest_arch = self.guest_arch;
        let settings = Arc::new(settings);
        if self.host_paths {
            settings
                .set_host_paths(root.path(), root.path())
                .expect("the host paths are declared");
        }
        // After the settings, which name the one worker the exec route answers for. The `list` line of the fake
        // names the root as the checkout, which is the testing-ui root without its `out/testing-ui` tail.
        #[cfg(unix)]
        let control_port = (self.backend == Backend::ContainerLinux).then(|| {
            let control_port = FakeControlPort::start(&settings.workers[0], FakeGuests::of(&settings.workers));
            fake.answer(Answer::ContainerLinuxPort, control_port.port().to_string());
            fake.answer(Answer::ContainerLinuxRoot, root.path().to_string_lossy().into_owned());
            control_port
        });
        HostPool {
            settings,
            fake,
            environment,
            git,
            #[cfg(unix)]
            control_port,
            root,
        }
    }
}

/// The stand-in guest agent, which a boot only stats and copies into the guest. An executable script on Unix, and a
/// plain file on Windows, which runs no script.
fn stand_in_agent(root: &Path) -> PathBuf {
    #[cfg(unix)]
    let agent = avl_testkit::fake_executable(root, "vm-guest-agent", "exit 0\n");
    #[cfg(windows)]
    let agent = {
        let agent = root.join("vm-guest-agent");
        std::fs::write(&agent, "exit 0\n").map(|()| agent)
    };
    agent.expect("the stand-in agent is written")
}
