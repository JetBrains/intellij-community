//! The host-side fixtures the controller's suites share: `avl-vm` lists this crate as a dev-dependency, and no crate
//! lists it as a normal one.
//!
//! What is here stands on `avl-host-sys` and below, never on `avl-vm`. A crate cannot take a dev-dependency on a
//! crate that depends on it: the fixture would link the normal `avl_vm`, and the controller suite's own is the one
//! compiled with `cfg(test)`, so the two `Manager` types would differ. So each module assembles its `Manager` itself,
//! in its own `cfg(test)` module, over [`HostPool`] and [`FakeGuests`]. `avl-host-sys` keeps a minimal fake channel
//! of its own for the same reason.
//!
//! The host process table of a probe is [`FakeProcesses`] for a suite that gives one to its runner.
//!
//! The hypervisor is [`avl_testkit::tartfake`]'s fake, and the host `git` is [`FakeGit`]. On Unix both are shell
//! scripts. A Windows host runs no shell script and has the Docker backend only, so there the fake `docker` and the
//! fake `git` are the program of [`avl_testkit::fakebin`], and a pool of another backend does not load.

use std::fmt::Debug;
use std::path::Path;
use std::sync::Arc;

use avl_base::config::{HostFacts, HostOs, MacosHost, WORKSPACE_DIR};
use avl_base::report::Buffer;
use avl_base::{Backend, Config, Environment, GuestOs, Outcome, Refusal, Reporter, Selection};
use avl_host_sys::{Interrupts, Runner};

pub mod agent;
pub mod answer;
#[cfg(unix)]
pub mod bazel;
pub mod channel;
#[cfg(unix)]
pub mod control_port;
pub mod git;
pub mod pool;
pub mod probe;
pub mod process;

pub use answer::{Answer, Verbs, answer_exit, answer_guest, answer_text, failed, handler, has, said};
#[cfg(unix)]
pub use bazel::PinnedBazel;
pub use channel::{Call, ChannelFactory, ConnectHandler, FakeChannel, FakeGuests, serve_on_connect};
#[cfg(unix)]
pub use control_port::FakeControlPort;
pub use git::FakeGit;
pub use pool::{HostPool, HostPoolBuilder};
pub use probe::FakeProbe;
pub use process::FakeProcesses;

/// A runner over `environment`, registering with a service no signal reaches.
pub fn runner(environment: &[(String, String)]) -> Runner {
    Runner::new(environment.iter().cloned(), Interrupts::detached())
}

/// A runner over this process's `PATH` and nothing else.
pub fn path_runner() -> Runner {
    runner(&[("PATH".to_owned(), std::env::var("PATH").unwrap_or_default())])
}

/// A reporter whose prose the suite can ignore without it reaching the terminal.
pub fn quiet() -> Reporter {
    Reporter::in_memory("vm-test").0
}

/// [`Reporter::in_memory_prose`] of `vm`, so a test can read what was said.
pub fn prose() -> (Reporter, Buffer) {
    Reporter::in_memory_prose("vm")
}

/// Settings over an environment, with the skill directory under `root`.
pub fn load_settings(backend: Backend, guest_os: GuestOs, environment: &[(String, String)], root: &Path) -> Arc<Config> {
    Arc::new(load_config(backend, guest_os, environment, root))
}

/// [`load_settings`] before it is shared, for a fixture that pins a field first.
pub fn load_config(backend: Backend, guest_os: GuestOs, environment: &[(String, String)], root: &Path) -> Config {
    load_config_on(HostOs::CURRENT, backend, guest_os, environment, root)
}

/// The macOS release every fixture loads on: macOS 26 on Apple silicon, the first release that runs Apple `container`
/// by default. Pinned and not read from the host, so a suite resolves the same engine on every macOS release. A suite
/// that needs one engine still chooses it: [`crate::pool::HostPoolBuilder::with_container_engine`] runs Apple
/// `container`, and `DOCKER_BIN` or `DOCKER_HOST` names the host engine.
pub const FIXTURE_MACOS: MacosHost = MacosHost {
    major: 26,
    apple_silicon: true,
};

/// The host memory every fixture loads on: 64 GiB, so the Apple `container` pool keeps its floor of two slots
/// (`avl_base::config::default_docker_slots`) and no suite depends on the memory of the test host.
pub const FIXTURE_HOST_MEMORY_MIB: u64 = 64 * 1024;

/// [`load_config`] as a controller on `host` resolves it, for a fixture of a macOS host on every host.
pub fn load_config_on(host: HostOs, backend: Backend, guest_os: GuestOs, environment: &[(String, String)], root: &Path) -> Config {
    load_config_on_release(host, FIXTURE_MACOS, backend, guest_os, environment, root)
}

/// [`load_config_on`] on the macOS release `macos` instead of [`FIXTURE_MACOS`], for a suite of an older macOS.
pub fn load_config_on_release(
    host: HostOs,
    macos: MacosHost,
    backend: Backend,
    guest_os: GuestOs,
    environment: &[(String, String)],
    root: &Path,
) -> Config {
    let environment = Environment::from_pairs(environment.iter().cloned());
    Config::load_on(
        host,
        HostFacts {
            macos: Some(macos),
            memory_mib: Some(FIXTURE_HOST_MEMORY_MIB),
        },
        Selection { backend, guest_os },
        &environment,
        &root.join(WORKSPACE_DIR),
    )
    .unwrap_or_else(|refusal| panic!("the environment was refused on {host}: {refusal:?}"))
}

/// The refusal a call was expected to answer.
pub fn refusal<T: Debug>(result: Result<T, Refusal>) -> Refusal {
    match result {
        Ok(value) => panic!("the call was expected to refuse and answered {value:?}"),
        Err(refusal) => refusal,
    }
}

/// The outcome a command was expected to answer.
pub fn outcome_of(result: Result<Outcome, Refusal>) -> Outcome {
    result.unwrap_or_else(|refusal| panic!("the command refused: {refusal}"))
}
