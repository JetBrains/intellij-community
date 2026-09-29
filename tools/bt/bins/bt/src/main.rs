//! `bt`: the process boundary and nothing else. Its argv, the one environment variable that says where the checkout
//! is, and its own descriptors are the values a test cannot be given; everything `bt` decides is in [`cli::run`],
//! where a test supplies all of them.
//!
//! `BUILD_WORKSPACE_DIRECTORY` is the checkout root by construction rather than by search: `bt.cmd` exports it, and a
//! `bazel run` of this target sets it.
//!
//! The contract:
//!
//! - The digest is advisory and may be reformatted.
//! - The `--json` payload is the STABLE contract.
//! - `<system-out>`/`<system-err>` bodies are never parsed or printed. Only a log path is offered.
//! - The six exit codes of [`bt_core::exit`] are a stable contract too: a caller automates against them.

mod cli;
mod digest;
mod execute;
mod options;

use std::path::PathBuf;
use std::process::ExitCode;

fn main() -> ExitCode {
    let repo_root = std::env::var_os("BUILD_WORKSPACE_DIRECTORY").map(PathBuf::from);
    // Unlocked handles: the heartbeat writes to stderr from its own thread while a run is in progress.
    ExitCode::from(cli::run(
        std::env::args_os().skip(1),
        repo_root.as_deref(),
        &mut std::io::stdout(),
        &mut std::io::stderr(),
    ))
}
