//! `bt`: the process boundary and nothing else. Its argv, the two environment variables that say where the checkout
//! is and whether it is a host slot, and its own descriptors are the values a test cannot be given; everything `bt`
//! decides is in [`cli::run`], where a test supplies all of them.
//!
//! `BUILD_WORKSPACE_DIRECTORY` is the checkout root by construction rather than by search: `bt.cmd` exports it, and a
//! `bazel run` of this target sets it. `BD_HOST_RUN` names the run of `brd-host`, whose agent sets it for every command
//! that it runs in a host slot.
//!
//! The contract:
//!
//! - The digest is advisory and may be reformatted.
//! - The `--json` payload is the STABLE contract.
//! - `<system-out>`/`<system-err>` bodies are never parsed or printed. Only a log path is offered.
//! - The six exit codes of [`bt_core::exit`] are a stable contract too: a caller automates against them.

mod build;
mod check;
mod cli;
mod credentials;
mod digest;
mod execute;
mod options;
mod prepare;
mod remote;
mod ssh;

use std::path::PathBuf;
use std::process::ExitCode;

fn main() -> ExitCode {
    let repo_root = std::env::var_os("BUILD_WORKSPACE_DIRECTORY").map(PathBuf::from);
    let host_slot = std::env::var_os("BD_HOST_RUN").is_some_and(|run| !run.is_empty());
    // Unlocked handles: the heartbeat writes to stderr from its own thread while a run is in progress.
    ExitCode::from(cli::run(
        std::env::args_os().skip(1),
        repo_root.as_deref(),
        host_slot,
        &mut std::io::stdout(),
        &mut std::io::stderr(),
    ))
}
