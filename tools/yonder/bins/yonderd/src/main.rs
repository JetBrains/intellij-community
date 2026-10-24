//! `yonderd`: the agent of yonder on a host. `API.md` of this crate lists its commands and their refusals.
//!
//! - `serve`: what launchd runs. It answers the requests of the clients, one per connection.
//! - `supervise <run dir>`: one run, in a session of its own, started by `serve`.
//! - `setup`: adds a client to the allow list and starts `serve` under launchd.
//! - `version`: the version and the protocol schema.

mod bd;
mod child;
mod cleanup;
mod cli;
mod files;
mod git;
mod home;
mod lease;
mod refused;
mod run;
mod run_requests;
mod serve;
mod setup;
mod supervise;
mod tuning;
mod watch;

use std::process::ExitCode;

fn main() -> ExitCode {
    ExitCode::from(cli::run(std::env::args_os(), &mut std::io::stdout(), &mut std::io::stderr()))
}
