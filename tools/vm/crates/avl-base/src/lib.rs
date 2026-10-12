//! The controller's base: refusals and exits, the envelope, settings, phases, ids, the journal and history,
//! portable file publication.
//!
//! Portable on purpose: nothing here touches a unix-only API outside a `cfg(unix)` block, so every program of the
//! lane, including the ones built for Windows, can stand on it. The operation context that carries cancellation,
//! the open phase and the run id through async host code is `avl-host-sys`'s; this crate supplies its parts
//! ([`phase::Timeline`], [`phase::PhaseHandle`]) and stays free of tokio.

pub mod clock;
pub mod config;
pub mod format;
pub mod fs;
pub mod history;
pub mod journal;
pub mod phase;
pub mod plain;
pub mod refusal;
pub mod report;
pub mod sync;

pub use self::refusal::{Exit, OrRefuse, Refusal, RefusalExt, descriptor_refusal};
pub use clock::{Clock, FakeClock, SystemClock};
pub use config::{Backend, Config, DockerEngine, Environment, GuestArch, GuestOs, Selection, validate_name};
pub use format::posix_shell_quote;
pub use report::{Outcome, PhaseRun, Reporter, SCHEMA_VERSION, Scope};

/// A fresh random id (UUID v4): run ids, holders, temporary names.
pub fn new_id() -> String {
    uuid::Uuid::new_v4().to_string()
}

/// The id that names one invocation of `command` by the person who ran it: `<user>-<command>-<uuid>`.
///
/// It is the run id of `run`, `shard` and `flake`, the holder of every lease such a run takes for itself, and the
/// holder `lease acquire` picks when the caller names none. One shape, so a `status` row reads the same whoever took
/// the worker. The user is `USER`, or `USERNAME` on Windows, which sets no `USER`. An unset or empty one is `agent`.
pub fn actor_id(environment: &Environment, command: &str) -> String {
    let user = environment
        .get("USER")
        .or_else(|| environment.get("USERNAME").filter(|_| cfg!(windows)))
        .unwrap_or("agent");
    format!("{user}-{command}-{}", new_id())
}
