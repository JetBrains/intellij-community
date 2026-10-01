//! `startup-bench`: the process boundary. It reads the argv, the checkout root and the working directory, and
//! gives its own descriptors to [`cli::run`], where a test can give all of them.
//!
//! `BUILD_WORKSPACE_DIRECTORY` is the checkout root: `community/tools/startup-bench.cmd` exports it.
//!
//! The contract:
//!
//! - The digest is advisory and can change.
//! - The `--json` envelope and `summary.json` are the stable contract.
//! - The exit codes are those of [`bt_core::exit`]: 0, `USAGE` (2) and `INFRA` (6).

mod arm;
mod bench;
mod classes;
mod cli;
mod digest;
mod files;
mod fus;
mod launch;
mod options;
mod profile;
mod record;
mod session;
mod stats;
mod summary;
#[cfg(test)]
mod testkit;
mod trace;

use std::path::PathBuf;
use std::process::ExitCode;

fn main() -> ExitCode {
    let repo_root = std::env::var_os("BUILD_WORKSPACE_DIRECTORY").map(PathBuf::from);
    let working_dir = std::env::current_dir().unwrap_or_default();
    ExitCode::from(cli::run(
        std::env::args_os().skip(1),
        repo_root.as_deref(),
        &working_dir,
        &mut std::io::stdout(),
        &mut std::io::stderr(),
    ))
}
