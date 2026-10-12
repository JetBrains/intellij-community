//! The fixtures the worker and lease suites share. Hermetic: no VM, no network, no Bazel.
//!
//! Two seams, and they are the two the production code already has. The hypervisor is the shared fake `tart` of
//! [`HostPool`], and the guest is [`FakeGuests`]: every worker's channel answers one pool-wide answer and records
//! into one log. What is left here is what only this crate can build: a [`Manager`] with the suite's poll bounds.
//!
//! A Windows host has the Docker pool only, so the Tart fixtures and the run-process helpers are Unix only.
//!
//! On Unix the runner asks [`FakeProcesses`] about each pid of the fake table, and the host about every other pid. A
//! stand-in run process is a process of the fake table, so a worker reads as running on a loaded host too. A `tart
//! run` that the fake hypervisor spawns is a real child, and the host answers for it: a start and a stale run need a
//! real exit and a real zombie.

use std::ops::Deref;
use std::sync::Arc;

use avl_base::{Backend, Config, GuestArch, GuestOs, SCHEMA_VERSION};
use avl_host_sys::Runner;
use avl_host_sys::guest::BazelHost;
#[cfg(unix)]
use avl_host_sys::interrupt::Interrupts;
use avl_host_sys::lock::LockManager;
#[cfg(unix)]
use avl_host_sys::{Ctx, ProcessTable};
use avl_host_testkit::{ChannelFactory, FakeGuests, HostPool, HostPoolBuilder, quiet};
#[cfg(unix)]
use avl_host_testkit::{FakeProcesses, PinnedBazel};
#[cfg(unix)]
use avl_testkit::tartfake::Answer;
#[cfg(unix)]
use nix::sys::signal::{Signal, killpg};
#[cfg(unix)]
use nix::unistd::Pid;

#[cfg(unix)]
use crate::worker::tart::{MINIMUM_VERSION, ProcessIdentity};
use crate::worker::worker::{Dependencies, GuestBootBuilder, Lease, Manager, Timings, builds_nothing};

/// The version the fake `tart` answers. A Windows host has no fake `tart`, and its fake `docker` reads no version.
#[cfg(unix)]
const TART_VERSION: &str = MINIMUM_VERSION;
#[cfg(windows)]
const TART_VERSION: &str = "";

/// A manager over a [`HostPool`] of Tart workers, with every guest command answered by [`FakeGuests`].
///
/// The host paths are declared rather than resolved: resolving runs a real `git rev-parse`, and a config that already
/// carries its paths answers without one.
pub(crate) struct Fixture {
    pool: HostPool,
    pub(crate) manager: Manager,
    pub(crate) guest: Arc<FakeGuests>,
    /// What the runner of [`Fixture::manager`] probes in place of the host, for the pids of the table.
    #[cfg(unix)]
    processes: Arc<FakeProcesses>,
    /// The Bazel that resolves the pinned tools to the fakes, for a fixture built with one.
    #[cfg(unix)]
    pub(crate) bazel: Option<Arc<PinnedBazel>>,
}

impl Deref for Fixture {
    type Target = HostPool;

    fn deref(&self) -> &HostPool {
        &self.pool
    }
}

pub(crate) struct FixtureBuilder {
    pool: HostPoolBuilder,
    build_boot: GuestBootBuilder,
    /// Whether the manager resolves the pinned tools through [`HostPool::pinned_bazel`], a Unix-only fake.
    #[cfg(unix)]
    pinned_bazel: bool,
}

impl Fixture {
    /// A Docker pool of one slot over the fake `docker`, which `DOCKER_BIN` names.
    pub(crate) fn docker() -> Self {
        Self::docker_builder().build()
    }

    pub(crate) fn docker_builder() -> FixtureBuilder {
        FixtureBuilder {
            pool: HostPool::builder(Backend::Docker, GuestOs::Linux, TART_VERSION),
            build_boot: builds_nothing(),
            #[cfg(unix)]
            pinned_bazel: false,
        }
    }

    /// A Docker pool of one slot on the Lima engine: the fake `limactl`, resolved as the pinned one, starts the
    /// engine, and the fake `docker` of `DOCKER_BIN` answers the backend. The engine is pinned in the settings, so the
    /// fixture runs on every Unix host.
    #[cfg(unix)]
    pub(crate) fn docker_lima() -> Self {
        Self::docker_builder().lima_engine().build()
    }

    pub(crate) fn worker(&self, index: usize) -> &str {
        &self.settings.workers[index]
    }

    /// A lease of this pool for one worker, the way an acquisition would build it.
    pub(crate) fn new_lease(&self, worker: &str, token: &str, holder: &str) -> Lease {
        Lease {
            schema_version: SCHEMA_VERSION,
            backend: self.settings.backend,
            guest_os: self.settings.guest_os,
            worker: worker.to_owned(),
            token: token.to_owned(),
            holder: holder.to_owned(),
            acquired_at: "2026-08-23T00:00:00.000Z".to_owned(),
        }
    }

    /// Places a lease on a worker as a plain file, so the guards can be exercised without the lease policy.
    pub(crate) fn write_lease(&self, worker: &str, token: &str) -> Lease {
        let lease = self.new_lease(worker, token, "suite");
        let mut encoded = serde_json::to_vec(&lease).expect("a lease encodes");
        encoded.push(b'\n');
        std::fs::write(self.settings.lease_path(worker), encoded).expect("the lease is written");
        lease
    }
}

/// The Tart pool and the run process of a Tart worker.
#[cfg(unix)]
impl Fixture {
    pub(crate) fn macos() -> Self {
        Self::builder(GuestOs::Macos).build()
    }

    /// A boot installs the guest agent, and naming its source is what keeps the "no Bazel" promise: a named source
    /// is answered by a stat.
    pub(crate) fn builder(guest_os: GuestOs) -> FixtureBuilder {
        FixtureBuilder {
            pool: HostPool::builder(Backend::Tart, guest_os, MINIMUM_VERSION),
            build_boot: builds_nothing(),
            #[cfg(unix)]
            pinned_bazel: false,
        }
    }

    /// A runner over the pool's environment whose children register with `interrupts`. Its process probes ask the
    /// fixture's process table, as the runner of [`Fixture::manager`] does.
    pub(crate) fn runner_with(&self, interrupts: &Interrupts) -> Runner {
        Runner::new(self.environment.iter().cloned(), interrupts.clone())
            .with_process_table(Arc::clone(&self.processes) as Arc<dyn ProcessTable>)
    }

    /// Makes a worker read as running: a live process of the fake table stands in for its `tart run` process. Nothing
    /// ends that process, and a `tart stop` does not end it either. Its command is no `tart run`, so no path takes it
    /// for an orphaned run process.
    pub(crate) async fn run_as_fake_process(&self, worker: &str) -> ProcessIdentity {
        let pid = self.processes.start(&format!("stand-in for the run process of {worker}"));
        self.manager
            .write_process_identity(&Ctx::background(), worker, pid)
            .await
            .expect("the fake process can be identified")
    }

    /// Makes a worker read as running on a `tart run` of its own whose VM Tart does not list: the fake's `run`,
    /// sleeping in its own process group, recorded as the worker's run process. What a peer checkout's recycle or a
    /// `tart delete` under a live run leaves behind. The answer kills the group when dropped.
    pub(crate) async fn run_stale_tart_process(&self, worker: &str) -> StaleRun {
        use std::os::unix::process::CommandExt as _;
        self.fake.answer(Answer::RunSleeps, "yes");
        let child = std::process::Command::new(self.fake.executable())
            .args(["run", "--no-graphics", worker])
            .process_group(0)
            .stdout(std::process::Stdio::null())
            .spawn()
            .expect("the fake tart run is spawned");
        let pid = i32::try_from(child.id()).expect("a pid fits");
        let identity = self
            .manager
            .write_process_identity(&Ctx::background(), worker, pid)
            .await
            .expect("the fake run process can be identified");
        StaleRun { child, identity }
    }

    /// Makes every slot of this Tart macOS pool bootable over the fakes: [`Fixture::seal_macos_golden`], then
    /// [`Fixture::answer_macos_boot`].
    pub(crate) fn make_macos_bootable(&self) {
        self.seal_macos_golden();
        self.answer_macos_boot();
    }

    /// Gives the golden its clone inputs, the pins of the image build and a seal, and gives every slot a disk. The
    /// fake `tart clone` copies nothing, and the provenance of a clone reads the disk.
    pub(crate) fn seal_macos_golden(&self) {
        use crate::worker::tart::{CloneInput, SealedGolden};
        use std::os::unix::fs::PermissionsExt;
        let settings = &self.settings;
        let tart = self.manager.tart().expect("a Tart pool");
        let golden = settings.golden_vm.as_str();
        let write_vm_file = |vm: &str, name: &str, content: &str| {
            let directory = settings.tart_home.join("vms").join(vm);
            std::fs::create_dir_all(&directory).expect("a VM directory");
            std::fs::write(directory.join(name), content).expect("a VM file");
        };
        for input in [CloneInput::Disk, CloneInput::Config, CloneInput::Nvram] {
            write_vm_file(golden, input.file_name(), &format!("the golden's {}\n", input.file_name()));
        }
        let (base_digest, macos_version) = ("sha256:fixture", "26.6.2");
        std::fs::write(
            settings.image_root.join("versions.env"),
            format!("GOLDEN_SEAL_SCHEMA=1\nCIRRUS_BASE_DIGEST={base_digest}\nMACOS_VERSION={macos_version}\n"),
        )
        .expect("the image pins are written");
        let signature = |input| tart.vm_file_signature(golden, input).expect("a clone input signature");
        let seal = SealedGolden {
            schema_version: SCHEMA_VERSION,
            golden: golden.to_owned(),
            disk_signature: signature(CloneInput::Disk),
            config_signature: signature(CloneInput::Config),
            nvram_signature: signature(CloneInput::Nvram),
            base_digest: base_digest.to_owned(),
            macos_version: macos_version.to_owned(),
            audited_at: "2026-08-09T00:00:00Z".to_owned(),
        };
        let seal_path = tart.seal_path(golden);
        std::fs::write(&seal_path, serde_json::to_vec(&seal).expect("a seal encodes")).expect("the seal is written");
        std::fs::set_permissions(&seal_path, std::fs::Permissions::from_mode(0o600)).expect("the seal is private");
        for worker in &settings.workers {
            write_vm_file(worker, CloneInput::Disk.file_name(), &format!("the disk of {worker}\n"));
        }
    }

    /// Makes every guest answer the macOS boot: a console login, clean TCC databases and an SSH host key of its own.
    ///
    /// These are answers of each channel, and a channel's own answer wins over the pool's. So a test that needs a
    /// guest that fails does not call this.
    pub(crate) fn answer_macos_boot(&self) {
        use avl_host_testkit::{answer_guest, said};
        for (index, worker) in self.settings.workers.iter().enumerate() {
            self.guest.channel(worker).answer(answer_guest(vec![
                ("/usr/bin/who", said(CONSOLE_LOGIN)),
                ("/usr/bin/sqlite3", said("0\n")),
                // The script that makes the key and the read of a peer's marker both name the marker path.
                ("ssh-host-key-fingerprint", said(&format!("SHA256:worker{index}\n"))),
            ]));
        }
    }

    /// Kills the detached `tart run` of every worker when dropped: one that outlived the suite would hold a VM slot
    /// on the developer's host until a reboot.
    pub(crate) fn kill_run_processes_on_drop(&self) -> RunProcessReaper {
        RunProcessReaper {
            settings: Arc::clone(&self.settings),
        }
    }
}

impl FixtureBuilder {
    /// Pins the guest's architecture; see [`HostPoolBuilder::guest_arch`].
    pub(crate) fn guest_arch(mut self, guest_arch: GuestArch) -> Self {
        self.pool = self.pool.guest_arch(guest_arch);
        self
    }

    pub(crate) fn env(mut self, name: &str, value: &str) -> Self {
        self.pool = self.pool.env(name, value);
        self
    }

    #[cfg(unix)]
    pub(crate) fn build_boot(mut self, build: GuestBootBuilder) -> Self {
        self.build_boot = build;
        self
    }

    /// Runs the Docker pool on the Lima engine, with a Bazel that resolves the pinned `limactl`.
    #[cfg(unix)]
    pub(crate) fn lima_engine(mut self) -> Self {
        self.pool = self.pool.with_lima_engine();
        self.pinned_bazel = true;
        self
    }

    /// Leaves `DOCKER_BIN` unset, with a Bazel that resolves the pinned Docker CLI to the fake.
    #[cfg(unix)]
    pub(crate) fn pinned_docker(mut self) -> Self {
        self.pool = self.pool.without_docker_bin();
        self.pinned_bazel = true;
        self
    }

    /// Leaves `DOCKER_BIN` unset with no Bazel to resolve the pinned CLI: the host of a command that can name no
    /// Docker CLI at all.
    #[cfg(unix)]
    pub(crate) fn unresolvable_docker(mut self) -> Self {
        self.pool = self.pool.without_docker_bin();
        self.pinned_bazel = false;
        self
    }

    /// Leaves the host paths unresolved, for the gates that must resolve them themselves.
    #[cfg(unix)]
    pub(crate) fn unresolved_host_paths(mut self) -> Self {
        self.pool = self.pool.unresolved_host_paths();
        self
    }

    pub(crate) fn build(self) -> Fixture {
        let pool = self.pool.build();
        let guest = FakeGuests::new();
        #[cfg(unix)]
        let bazel = self.pinned_bazel.then(|| Arc::new(pool.pinned_bazel()));
        #[cfg(unix)]
        let host = bazel.clone().map(|bazel| bazel as Arc<dyn BazelHost>);
        #[cfg(windows)]
        let host = None;
        #[cfg(unix)]
        let processes = Arc::new(FakeProcesses::beside_the_host());
        #[cfg(unix)]
        let runner = pool.runner().with_process_table(Arc::clone(&processes) as Arc<dyn ProcessTable>);
        #[cfg(windows)]
        let runner = pool.runner();
        let manager = manager_with_bazel(&pool.settings, runner, Some(guest.factory()), self.build_boot, host);
        manager.prepare_runtime_dirs().expect("the runtime directories are created");
        Fixture {
            pool,
            manager,
            guest,
            #[cfg(unix)]
            processes,
            #[cfg(unix)]
            bazel,
        }
    }
}

/// A manager over settings with the suite's poll bounds, reaching the guest through `channel` when one is given.
///
/// Its callers are the worker lifecycle suite, which is Unix only, so a Windows test build has no use for it.
#[cfg(unix)]
pub(crate) fn manager_over(
    settings: &Arc<Config>,
    runner: Runner,
    channel: Option<ChannelFactory>,
    build_boot: GuestBootBuilder,
) -> Manager {
    manager_with_bazel(settings, runner, channel, build_boot, None)
}

/// [`manager_over`] with a Bazel that resolves the pinned tools.
fn manager_with_bazel(
    settings: &Arc<Config>,
    runner: Runner,
    channel: Option<ChannelFactory>,
    build_boot: GuestBootBuilder,
    bazel: Option<Arc<dyn BazelHost>>,
) -> Manager {
    Manager::with_timings(
        Dependencies {
            settings: Arc::clone(settings),
            locks: Arc::new(LockManager::new(runner.clone())),
            runner,
            reporter: quiet(),
            channel,
            bazel,
            build_guest_boot: build_boot,
        },
        Timings::fast(),
    )
}

/// A fake `tart run` a suite spawned as a stale run process. Dropping it kills its group and reaps it.
#[cfg(unix)]
pub(crate) struct StaleRun {
    child: std::process::Child,
    pub(crate) identity: ProcessIdentity,
}

#[cfg(unix)]
impl Drop for StaleRun {
    fn drop(&mut self) {
        let _ = killpg(Pid::from_raw(self.identity.pid), Signal::SIGKILL);
        let _ = self.child.wait();
    }
}

/// Kills the recorded run process group of every worker of a pool when dropped.
#[cfg(unix)]
pub(crate) struct RunProcessReaper {
    settings: Arc<Config>,
}

#[cfg(unix)]
impl Drop for RunProcessReaper {
    fn drop(&mut self) {
        for worker in &self.settings.workers {
            let Some(identity) = read_identity(&self.settings.pid_path(worker)) else {
                continue;
            };
            // Never this test process, which a suite of the real `ps` probe can record.
            if u32::try_from(identity.pid).ok() != Some(std::process::id()) {
                let _ = killpg(Pid::from_raw(identity.pid), Signal::SIGKILL);
            }
        }
    }
}

#[cfg(unix)]
fn read_identity(path: &std::path::Path) -> Option<ProcessIdentity> {
    serde_json::from_slice(&std::fs::read(path).ok()?).ok()
}

/// What `/usr/bin/who` answers in a macOS guest whose console is logged in.
#[cfg(unix)]
pub(crate) const CONSOLE_LOGIN: &str = "admin    console  Aug 23 10:00\n";

/// Finds `step` in the guest transcript at or after `from`, answering its index.
pub(crate) fn find_step(argvs: &[String], step: &str, from: usize) -> usize {
    let found = argvs
        .iter()
        .position(|argv| argv.contains(step))
        .unwrap_or_else(|| panic!("a boot must run {step:?}; guest calls were {argvs:#?}"));
    assert!(found >= from, "{step:?} ran out of order, at call {found} of {argvs:#?}");
    found
}
