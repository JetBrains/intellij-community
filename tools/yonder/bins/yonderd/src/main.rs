//! `yonderd`: the agent of yonder on a host. `API.md` of this crate lists its commands and their refusals.
//!
//! - `serve`: what launchd runs. It answers the requests of the clients, one per connection.
//! - `supervise <run dir>`: one run, in a session of its own, started by `serve`.
//! - `setup`: adds a client to the allow list and starts `serve` under launchd.
//! - `version`: the version and the protocol schema.
//! - `upgrade <binary>`: installs a new binary over the one of the LaunchAgent and restarts the agent.
//! - `watchdog <binary>`: restores the previous binary when the new one does not listen in time.
//! - `dump-env <file>`: the last command of the login shell that `login_env` starts.

mod awake;
mod bd;
mod child;
mod cleanup;
mod cli;
mod config;
mod files;
mod git;
mod home;
mod lease;
mod login_env;
mod prepare;
mod refused;
mod run;
mod run_env;
mod run_requests;
mod serve;
mod setup;
mod supervise;
#[cfg(test)]
mod testing;
mod tuning;
mod upgrade;
mod watch;

use std::process::ExitCode;

fn main() -> ExitCode {
    ExitCode::from(cli::run(std::env::args_os(), &mut std::io::stdout(), &mut std::io::stderr()))
}
