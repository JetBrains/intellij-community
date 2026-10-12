//! The observation commands' fixture. Hermetic: no VM, no network, no Bazel, and no host process.
//!
//! The pool is [`HostPool`] and the guests are [`FakeGuests`], one channel per worker of the pool: the manager never
//! asks for another one. This suite drives both backends, so the pool installs both of the fake's binaries into one
//! directory. A Tart test and a Parallels test therefore seed the same answer names, and both read one call log.
//!
//! The runner probes [`FakeProcesses`] and not the host. So a ready worker reads as running on a loaded host too.

use std::ops::Deref;
use std::path::PathBuf;
use std::sync::Arc;

use crate::worker::docker::CreateRecord;
use crate::worker::hypervisor::Machine;
use crate::worker::lease::write_lease_receipt;
use crate::worker::tart::MINIMUM_VERSION;
use crate::worker::worker::{Dependencies, Lease, Manager, builds_nothing};
use avl_base::{Backend, GuestOs, SCHEMA_VERSION};
use avl_host_sys::guest::write_init_receipt;
use avl_host_sys::lock::LockManager;
use avl_host_sys::{Ctx, ProcessTable};
use avl_host_testkit::{FakeChannel, FakeGuests, FakeProcesses, HostPool, answer_guest, quiet, said};
use avl_testkit::tartfake::Answer;

/// The id the fake engine reports for the container of a ready Docker worker.
const CONTAINER_ID: &str = "fake-container-1";

/// A pool over the fake hypervisor, fake guests and a temporary runtime root.
pub(crate) struct Fixture {
    pool: HostPool,
    pub(crate) manager: Manager,
    guests: Arc<FakeGuests>,
    processes: Arc<FakeProcesses>,
}

impl Deref for Fixture {
    type Target = HostPool;

    fn deref(&self) -> &HostPool {
        &self.pool
    }
}

impl Fixture {
    /// The pool whose host paths are already declared - what every command that resolves them first leaves behind,
    /// and the state all but three of these tests want. Declared rather than resolved: resolving runs a real
    /// `git rev-parse`, which a hermetic suite must not.
    pub(crate) fn new(backend: Backend, guest_os: GuestOs) -> Self {
        Self::build(backend, guest_os, None, false)
    }

    /// A Docker pool on the Lima engine, whose Bazel resolves the pinned `limactl` to the fake.
    #[cfg(unix)]
    pub(crate) fn docker_lima() -> Self {
        Self::build(Backend::Docker, GuestOs::Linux, None, true)
    }

    /// The Linux pool: one Docker slot over the fake `docker`, on an external engine.
    pub(crate) fn docker() -> Self {
        Self::new(Backend::Docker, GuestOs::Linux)
    }

    /// The pool the macOS-only half of `status` applies to: TCC admission, the Aqua session, and the SSH host key
    /// two clones of one sealed image start life sharing.
    pub(crate) fn tart_macos() -> Self {
        Self::new(Backend::Tart, GuestOs::Macos)
    }

    /// The pool as a command finds it: the two host paths unresolved, and a fake `git` that does or does not call
    /// the configured checkout a working tree. The pool sets `AIR_VM_HOST_REPO`, so resolution takes the override
    /// path and asks only `--is-inside-work-tree`.
    pub(crate) fn before_host_paths(backend: Backend, guest_os: GuestOs, inside_work_tree: bool) -> Self {
        Self::build(backend, guest_os, Some(inside_work_tree), false)
    }

    fn build(backend: Backend, guest_os: GuestOs, host_git: Option<bool>, lima: bool) -> Self {
        // Both binaries, because a `status` over a Parallels pool reaches `prlctl` while every other test here
        // reaches `tart`, and the two names are what keep the two `exec` grammars apart.
        let mut builder = HostPool::builder(backend, guest_os, MINIMUM_VERSION).with_parallels();
        if host_git.is_some() {
            builder = builder.with_git().unresolved_host_paths();
        }
        if lima {
            builder = builder.with_lima_engine();
        }
        if backend == Backend::Tart {
            // The production names of the Tart pool, whose guest is macOS.
            builder = builder.env("AIR_VM_WORKERS", "air-macos-1,air-macos-2");
        }
        let pool = builder.build();
        if host_git == Some(false) {
            pool.git().outside_work_tree();
        }
        let guests = FakeGuests::of(&pool.settings.workers);
        let processes = Arc::new(FakeProcesses::default());
        let runner = pool.runner().with_process_table(Arc::clone(&processes) as Arc<dyn ProcessTable>);
        #[cfg(unix)]
        let bazel = lima.then(|| Arc::new(pool.pinned_bazel()) as Arc<dyn avl_host_sys::guest::BazelHost>);
        #[cfg(windows)]
        let bazel = None;
        let manager = Manager::new(Dependencies {
            settings: Arc::clone(&pool.settings),
            locks: Arc::new(LockManager::new(runner.clone())),
            runner,
            reporter: quiet(),
            channel: Some(guests.factory()),
            bazel,
            build_guest_boot: builds_nothing(),
        });
        manager.prepare_runtime_dirs().expect("the runtime directories are created");
        Self {
            pool,
            manager,
            guests,
            processes,
        }
    }

    pub(crate) fn channel(&self, worker: &str) -> Arc<FakeChannel> {
        self.guests.channel(worker)
    }

    pub(crate) fn channels(&self) -> Vec<Arc<FakeChannel>> {
        self.guests.channels()
    }

    /// Puts one worker into the state the readiness gate accepts without booting anything, and writes an init
    /// receipt for the fixture's paths.
    ///
    /// On Tart it is a run-process identity that names a live process of the fake table. The command is no `tart
    /// run`, so no path takes the process for an orphaned run. On Docker it is a running container, made with the
    /// argv a create uses now, from an image the engine has.
    pub(crate) async fn mark_ready(&self, worker: &str) {
        if let Machine::Docker(docker) = self.manager.machine() {
            self.fake.answer(Answer::ContainerState, "running/0\n");
            self.fake.answer(Answer::ContainerId, format!("{CONTAINER_ID}\n"));
            self.fake.answer(Answer::ImagePresent, "");
            let record = CreateRecord {
                schema_version: SCHEMA_VERSION,
                worker: worker.to_owned(),
                argv: docker.create_argv(worker).expect("the create argv"),
                container_id: CONTAINER_ID.to_owned(),
            };
            let path = self.settings.docker_create_record_path(worker);
            std::fs::create_dir_all(path.parent().expect("a worker directory")).expect("the worker directory is created");
            let mut encoded = serde_json::to_vec(&record).expect("the record encodes");
            encoded.push(b'\n');
            std::fs::write(path, encoded).expect("the create record is written");
            write_init_receipt(&self.settings, worker).expect("the init receipt is written");
            return;
        }
        let pid = self.processes.start(&format!("stand-in for the run process of {worker}"));
        self.manager
            .write_process_identity(&Ctx::background(), worker, pid)
            .await
            .expect("this process can be identified");
        write_init_receipt(&self.settings, worker).expect("the init receipt is written");
    }

    /// Places a lease the way an acquisition would, and writes the caller's secure receipt for it.
    pub(crate) fn lease_receipt(&self, worker: &str) -> PathBuf {
        let lease = Lease {
            schema_version: SCHEMA_VERSION,
            backend: self.settings.backend,
            guest_os: self.settings.guest_os,
            worker: worker.to_owned(),
            token: "token-1".to_owned(),
            holder: "suite".to_owned(),
            acquired_at: "2026-08-23T00:00:00.000Z".to_owned(),
        };
        let mut encoded = serde_json::to_vec(&lease).expect("a lease encodes");
        encoded.push(b'\n');
        std::fs::write(self.settings.lease_path(worker), encoded).expect("the lease is written");
        write_lease_receipt(&self.settings, &lease).expect("the receipt is written")
    }
}

/// `prlctl list -a -i -j` for one registered Apple-Virtualization macOS VM, shares enabled - a worker `require_info`
/// accepts.
pub(crate) const PARALLELS_INFO_JSON: &str = r#"[{"ID":"{11111111-2222}","Name":"macOS","Type":"APPLE_VZ_VM","State":"running","OS":"macosx","Host Shared Folders":{"enabled":true}}]"#;

/// A leased, ready Parallels worker over the fake `prlctl` and a fake guest whose console is logged in.
pub(crate) fn parallels_fixture() -> (Fixture, PathBuf) {
    let fixture = Fixture::new(Backend::Parallels, GuestOs::Macos);
    fixture.fake.answer(Answer::ListJson, PARALLELS_INFO_JSON);
    fixture.fake.answer(Answer::Status, "VM macOS exist running\n");
    write_init_receipt(&fixture.settings, "macOS").expect("the init receipt is written");
    fixture
        .channel("macOS")
        .answer(answer_guest(vec![("/usr/bin/who", said("test  console  Aug 23 10:00\n"))]));
    let receipt = fixture.lease_receipt("macOS");
    (fixture, receipt)
}
