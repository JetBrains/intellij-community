//! `run`, `shard`, `flake` and `daemon`: staging, the hot-jar push, iterations, the verdict, the trace pull, and
//! the leased-run driver the three share.
//!
//! One long-lived guest JVM (`plugins/air/tests/integration/uiDaemon`) holds the warm IDE across host iterations.
//! Bazel publishes its launch graph as a runtime descriptor; the controller stages the stable classpath and JBR
//! guest-locally, pushes rebuilt hot jars over HTTP, and streams JUnit back as NDJSON. Share-backed product and data
//! changes quiesce the daemon around a refresh of the shares, while immutable runtime or boot setting changes restart
//! it.
//!
//! `run` is self-sufficient: it starts or restarts the daemon whenever the build it just prepared does not match the
//! one running. The `daemon` subcommands exist to drive that lifecycle deliberately - to pay the boot cost up front,
//! to read a dead daemon's tail, or to hand the supervisor's single run slot back to `exec`.
//!
//! It runs on every host that `avl-host-sys` supports: Unix and Windows.
//!
//! # Where things are
//!
//! - [`host`]: the [`Host`] every operation hangs off, the Bazel seam and the watchdog policy;
//! - [`state`]: what this controller remembers about the daemon it last started on a worker;
//! - [`http`]: the control channel, which reaches the daemon through a relay over the worker's exec channel;
//! - [`build`]: the one host build and its four identities;
//! - [`stage`], [`start`]: the guest runtime generation and the daemon's boot;
//! - [`space`], [`tree`], [`slot`]: the guest disk, the host checkout, the parked-daemon probe;
//! - [`run`]: the option grammar, the selection it resolves to, the jar push and the streamed run with its transport
//!   watchdog;
//! - [`iterate`], [`traces`]: one iteration as a value, and the trace pull beside its stream;
//! - [`verdict`], [`command`]: what a report concluded, and the `run` and `daemon` commands;
//! - [`shard`], [`flake`]: the two commands that fan a lane out over several workers;
//! - `leased`: the journaled run and the leased-run driver `run`, `shard` and `flake` share.

pub(crate) mod build;
pub(crate) mod command;
pub(crate) mod flake;
pub(crate) mod host;
pub(crate) mod http;
pub(crate) mod iterate;
mod leased;
pub(crate) mod run;
pub(crate) mod shard;
pub(crate) mod slot;
pub(crate) mod space;
pub(crate) mod stage;
pub(crate) mod start;
pub(crate) mod state;
pub(crate) mod traces;
pub(crate) mod tree;
pub(crate) mod verdict;

// The fixture is a Tart pool over the fake `tart`, a shell script, so the suites that drive it are Unix only, and
// each of their `tests` modules says so.
#[cfg(test)]
#[cfg(unix)]
mod fixture;
#[cfg(test)]
#[cfg(unix)]
mod testing;

pub(crate) use build::PreparedBuild;
pub(crate) use command::DaemonVerb;
pub(crate) use host::Host;
pub(crate) use iterate::RunAttempt;
pub(crate) use run::{Junit5FilterKind, ParsedRun, RunArgs, RunCommandArgs, RunSelection};
pub(crate) use slot::parked_daemon_probe;
pub(crate) use space::guest_free_bytes;
pub(crate) use state::HostState;
