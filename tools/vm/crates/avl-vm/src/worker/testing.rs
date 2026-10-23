//! The fixtures the worker and lease suites share. Hermetic: no VM, no network, no Bazel.
//!
//! Two seams, and they are the two the production code already has. The hypervisor is the shared fake `tart` of
//! [`HostPool`], and the guest is [`FakeGuests`]: every worker's channel answers one pool-wide answer and records
//! into one log. What is left here is what only this crate can build: a [`Manager`] with the suite's poll bounds.
//!
//! A container-linux pool keeps the production channel: the manager builds the control-port channel itself, and the
//! pool's fake control port answers from the same [`FakeGuests`], so a suite seeds and reads them as on Docker.
//!
//! The fakes are shell scripts, so the Tart and container-linux fixtures and the run-process helpers are Unix only.
//!
//! On Unix the runner asks [`FakeProcesses`] about each pid of the fake table, and the host about every other pid. A
//! stand-in run process is a process of the fake table, so a worker reads as running on a loaded host too. A `tart
//! run` that the fake hypervisor spawns is a real child, and the host answers for it: a start and a stale run need a
//! real exit and a real zombie.

use std::ops::Deref;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};

use avl_base::{Backend, Config, GuestArch, GuestOs, Outcome, Refusal, SCHEMA_VERSION};
use avl_host_sys::Ctx;
use avl_host_sys::Runner;
use avl_host_sys::guest::BazelHost;
use avl_host_sys::lock::LockManager;
use avl_host_testkit::{ChannelFactory, FakeGuests, HostPool, HostPoolBuilder, quiet};
use avl_testkit::tartfake::{Answer, Fake};

use crate::worker::docker::Docker;

#[cfg(unix)]
use {
    crate::worker::run_marker::{RunMarker, write_marker_by_hand},
    crate::worker::tart::{MINIMUM_VERSION, PidReceipt},
    avl_base::Reporter,
    avl_base::config::MacosHost,
    avl_base::phase::Timeline,
    avl_base::report::SinkGuard,
    avl_host_sys::interrupt::Interrupts,
    avl_host_sys::{ProcessIdentity, ProcessTable},
    avl_host_testkit::{FakeProcesses, PinnedBazel},
    avl_wire::progress::{Kind, Record},
    nix::sys::signal::{Signal, killpg},
    nix::unistd::Pid,
    std::time::Duration,
};

use crate::worker::worker::{
    Dependencies, GuestBootBuilder, IdleStopRequest, Lease, Manager, PoolCommand, PoolTarget, StartState, Timings, builds_nothing,
};

/// The context of a suite step: the background one, which nothing cancels.
pub(crate) fn ctx() -> Ctx {
    Ctx::background()
}

/// The Docker backend of a Docker pool.
pub(crate) fn docker(fixture: &Fixture) -> &Docker {
    fixture.manager.machine().docker().expect("a Docker pool")
}

/// Spawns `guest_line` and a `git rev-parse` in one phase of `ctx`, and asserts that `timeline`, the timeline of
/// `ctx`, counts one guest call and one host call. The guest line was built with `ctx`, so its head is declared.
#[cfg(unix)]
pub(crate) fn assert_counts_one_guest_call(ctx: &Ctx, timeline: &Timeline, guest_line: &[String]) {
    let (inner, phase) = ctx.begin("probe");
    inner.phase().record_subprocess(guest_line, Duration::from_secs(1));
    inner.phase().record_subprocess(&["git", "rev-parse"], Duration::from_secs(1));
    drop(phase);
    let timing = &timeline.take()[0];
    assert_eq!((timing.guest_calls, timing.host_calls), (1, 1), "{timing:?}");
}

/// Asserts that `pool init` of `fixture` refuses a golden as `usage`: only a Tart pool clones its workers from one.
pub(crate) async fn assert_init_refuses_a_golden(fixture: &Fixture) {
    let golden = Some("air-macos-golden".to_owned());
    let refusal = fixture.run_pool(PoolCommand::Init { golden }).await.unwrap_err();
    assert_eq!(refusal.code, "usage");
}

/// Starts the first worker of `fixture`, writes a stale receipt into its directory, and runs `pool recycle` of the
/// worker. Answers the worker, the receipt and the hypervisor calls of the recycle.
pub(crate) async fn recycle_with_a_stale_receipt(fixture: &Fixture) -> (String, PathBuf, Vec<String>) {
    let worker = fixture.worker(0).to_owned();
    fixture.manager.start_without_lifecycle_lock(&ctx(), &worker).await.unwrap();
    let receipt = fixture.settings.worker_dir(&worker).join("stale-receipt.json");
    std::fs::write(&receipt, "{}").unwrap();
    fixture.fake.forget_calls();
    fixture
        .run_pool(PoolCommand::Recycle(PoolTarget::Worker(worker.clone())))
        .await
        .unwrap();
    (worker, receipt, fixture.fake.calls())
}

/// Asserts that `probe` refuses `probe_unanswered` with the exit status while `fake` answers `verb` as a process that a
/// signal ended, and then as one that exited 0 and printed nothing. Neither of them is an answer.
pub(crate) async fn assert_probe_unanswered<T: std::fmt::Debug>(fake: &Fake, verb: &str, probe: impl AsyncFn() -> Result<T, Refusal>) {
    for (answer, code) in [(Answer::KilledVerb, 137), (Answer::SilentVerb, 0)] {
        fake.answer(answer, verb);
        let refusal = probe().await.unwrap_err();
        assert_eq!(refusal.code, "probe_unanswered", "{verb} answered as {answer:?}: {refusal:?}");
        assert!(refusal.message.contains(&format!("exited with {code}")), "{}", refusal.message);
        fake.forget(answer);
    }
}

/// The index of the first of the hypervisor `calls` that starts with `prefix`. The test fails with the calls when none
/// does.
pub(crate) fn position(calls: &[String], prefix: &str) -> usize {
    calls
        .iter()
        .position(|call| call.starts_with(prefix))
        .unwrap_or_else(|| panic!("no {prefix:?} call in {calls:#?}"))
}

/// Starts the first worker of `fixture`, asserts that the start started it, and answers the worker.
pub(crate) async fn start_first(fixture: &Fixture) -> &str {
    let worker = fixture.worker(0);
    assert_eq!(
        fixture.manager.start_without_lifecycle_lock(&ctx(), worker).await.unwrap(),
        StartState::Started
    );
    worker
}

/// Starts `worker` of `fixture` again, asserts that it was already running, and answers the hypervisor calls of this
/// second start.
pub(crate) async fn start_again(fixture: &Fixture, worker: &str) -> Vec<String> {
    let before = fixture.fake.calls().len();
    assert_eq!(
        fixture.manager.start_without_lifecycle_lock(&ctx(), worker).await.unwrap(),
        StartState::AlreadyRunning
    );
    fixture.fake.calls()[before..].to_vec()
}

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
    /// Every detached idle stop process that the manager was asked to start. The manager of a fixture starts none.
    #[cfg_attr(
        windows,
        expect(
            dead_code,
            reason = "the suites of the idle stop drive the fake Apple container, which is Unix only"
        )
    )]
    pub(crate) idle_stops: Arc<Mutex<Vec<IdleStopRequest>>>,
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

    /// A Docker pool of one slot on the Apple `container` engine, over the fake `container`. The settings load as a
    /// Mac of macOS 26 loads them, so the fixture runs on every Unix host.
    #[cfg(unix)]
    pub(crate) fn docker_container() -> Self {
        Self::docker_builder().container_engine().build()
    }

    /// The container-linux pool of one slot over the fake `container.cmd`, laid out as the fixture checkout's skill. Needs
    /// a tokio runtime, because the pool's fake control port is a tokio server.
    #[cfg(unix)]
    pub(crate) fn container_linux() -> Self {
        Self::container_linux_builder().build()
    }

    #[cfg(unix)]
    pub(crate) fn container_linux_builder() -> FixtureBuilder {
        FixtureBuilder {
            pool: HostPool::builder(Backend::ContainerLinux, GuestOs::Linux, TART_VERSION),
            build_boot: builds_nothing(),
            pinned_bazel: false,
        }
    }

    pub(crate) fn worker(&self, index: usize) -> &str {
        &self.settings.workers[index]
    }

    /// Runs the pool `command` of `vm pool` without a lease file.
    pub(crate) async fn run_pool(&self, command: PoolCommand) -> Result<Outcome, Refusal> {
        self.manager.pool(&ctx(), command, None).await
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
        write_lease_file(&self.settings, &lease);
        lease
    }
}

/// Writes `lease` as the plain lease file of its worker, with no lease policy and no atomic move.
pub(crate) fn write_lease_file(settings: &Config, lease: &Lease) {
    let mut encoded = serde_json::to_vec(lease).expect("a lease encodes");
    encoded.push(b'\n');
    std::fs::write(settings.lease_path(&lease.worker), encoded).expect("the lease is written");
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
    pub(crate) async fn run_as_fake_process(&self, worker: &str) -> PidReceipt {
        let pid = self.processes.start(&format!("stand-in for the run process of {worker}"));
        self.manager
            .write_process_identity(&Ctx::background(), worker, pid)
            .await
            .expect("the fake process can be identified")
    }

    /// Declares a process of the fixture's process table whose every probe gives no answer, and answers its pid.
    pub(crate) fn start_unanswered_process(&self) -> i32 {
        self.processes.start_unanswered()
    }

    /// The identity of a live process of another controller: a new process of the fixture's process table.
    pub(crate) async fn another_process(&self) -> ProcessIdentity {
        let pid = self.processes.start("vm run, a controller in another checkout");
        self.manager
            .runner()
            .identify(&Ctx::background(), pid)
            .await
            .expect("the fake table answers")
            .expect("a declared process has an identity")
    }

    /// The identity of a process that is gone: the fixture's process table holds the pid, and declares no process
    /// for it.
    pub(crate) fn gone_process() -> ProcessIdentity {
        ProcessIdentity {
            pid: 0x7FFF_FFF0,
            process_start: "Sun Aug 23 00:00:00 2026".to_owned(),
            process_command: "vm run, a controller that died".to_owned(),
        }
    }

    /// Writes a run marker of `process` for `lease` by hand, as another controller writes one, and answers it.
    pub(crate) fn write_run_marker(&self, lease: &Lease, run_id: &str, process: ProcessIdentity) -> RunMarker {
        write_marker_by_hand(&self.settings, lease, run_id, process)
    }

    /// A second controller over the same pool and the same guests, as another process sees the pool. Its lock manager
    /// speaks for a live process of the fixture's process table. So a run marker of either manager is a live run of
    /// another process to the other one. It starts no idle stop process.
    pub(crate) fn another_controller(&self) -> Manager {
        let runner = self.runner_with(&Interrupts::detached());
        let pid = self.processes.start("vm, another controller of the suite");
        let channel = (self.settings.backend != Backend::ContainerLinux).then(|| self.guest.factory());
        Manager::with_timings(
            Dependencies {
                settings: Arc::clone(&self.settings),
                locks: Arc::new(LockManager::for_process(runner.clone(), pid)),
                runner,
                reporter: quiet(),
                channel,
                bazel: self.bazel.clone().map(|bazel| bazel as Arc<dyn BazelHost>),
                build_guest_boot: builds_nothing(),
            },
            Timings::fast(),
        )
        .with_idle_stop_spawner(Arc::new(|_: &Manager, _: &IdleStopRequest| Ok(())))
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

    /// Loads the settings on the macOS release `macos`; see [`HostPoolBuilder::on_macos`].
    #[cfg(unix)]
    pub(crate) fn on_macos(mut self, macos: MacosHost) -> Self {
        self.pool = self.pool.on_macos(macos);
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

    /// Runs the Docker pool on the Apple `container` engine, over the fake `container` that `CONTAINER_BIN` names.
    #[cfg(unix)]
    pub(crate) fn container_engine(mut self) -> Self {
        self.pool = self.pool.with_container_engine();
        self
    }

    /// Leaves `DOCKER_BIN` and `DOCKER_HOST` unset, with a Bazel that resolves the pinned Docker CLI to the fake. On a
    /// release of [`FixtureBuilder::on_macos`] that runs no Apple `container`, the pool keeps the host engine and names
    /// none.
    #[cfg(unix)]
    pub(crate) fn unnamed_engine(mut self) -> Self {
        self.pool = self.pool.without_named_engine();
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
        // A container-linux pool is reached through the production channel, which the manager builds when it is given
        // none; the fake control port of the pool answers from the pool's guests.
        #[cfg(unix)]
        let (guest, channel) = if pool.settings.backend == Backend::ContainerLinux {
            (Arc::clone(pool.control_port().guests()), None)
        } else {
            scripted_guests()
        };
        #[cfg(windows)]
        let (guest, channel) = scripted_guests();
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
        let idle_stops = Arc::new(Mutex::new(Vec::new()));
        let recorder = Arc::clone(&idle_stops);
        let manager = manager_with_bazel(&pool.settings, runner, channel, self.build_boot, host).with_idle_stop_spawner(Arc::new(
            move |_: &Manager, request: &IdleStopRequest| {
                avl_base::sync::lock(&recorder).push(request.clone());
                Ok(())
            },
        ));
        manager.prepare_runtime_dirs().expect("the runtime directories are created");
        Fixture {
            pool,
            manager,
            guest,
            idle_stops,
            #[cfg(unix)]
            processes,
            #[cfg(unix)]
            bazel,
        }
    }
}

/// Fresh scripted guests, and the channel dependency that hands them to a manager.
fn scripted_guests() -> (Arc<FakeGuests>, Option<ChannelFactory>) {
    let guests = FakeGuests::new();
    let factory = guests.factory();
    (guests, Some(factory))
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
    pub(crate) identity: PidReceipt,
}

#[cfg(unix)]
impl Drop for StaleRun {
    fn drop(&mut self) {
        let _ = killpg(Pid::from_raw(self.identity.process.pid), Signal::SIGKILL);
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
            if u32::try_from(identity.process.pid).ok() != Some(std::process::id()) {
                let _ = killpg(Pid::from_raw(identity.process.pid), Signal::SIGKILL);
            }
        }
    }
}

#[cfg(unix)]
fn read_identity(path: &std::path::Path) -> Option<PidReceipt> {
    serde_json::from_slice(&std::fs::read(path).ok()?).ok()
}

/// Every note that a reporter publishes while this lives.
#[cfg(unix)]
pub(crate) struct Notes {
    notes: Arc<Mutex<Vec<String>>>,
    _sink: SinkGuard,
}

#[cfg(unix)]
impl Notes {
    pub(crate) fn start(reporter: &Reporter) -> Self {
        let notes: Arc<Mutex<Vec<String>>> = Arc::default();
        let sink = {
            let notes = Arc::clone(&notes);
            reporter.add_sink(move |record: &Record| {
                if record.event == Kind::Note {
                    avl_base::sync::lock(&notes).push(record.message.clone());
                }
            })
        };
        Self { notes, _sink: sink }
    }

    /// The notes so far, in order.
    pub(crate) fn all(&self) -> Vec<String> {
        avl_base::sync::lock(&self.notes).clone()
    }
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
