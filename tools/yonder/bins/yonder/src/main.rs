//! `yonder`: the process boundary. [`cli::run`] holds every decision, and a test calls it with its own configuration
//! directory and its own sinks.

mod cli;

use std::io::Write as _;
use std::process::ExitCode;

fn main() -> ExitCode {
    let say = |line: &str| {
        let mut stderr = std::io::stderr().lock();
        let _ = writeln!(stderr, "{line}");
    };
    ExitCode::from(cli::run(
        std::env::args_os(),
        yonder_client::Config::of_user(),
        &yonder_client::OsProcesses,
        &mut std::io::stdout(),
        &say,
    ))
}
