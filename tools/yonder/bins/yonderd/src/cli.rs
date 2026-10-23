//! The command line of the agent, and the report of a refusal: one JSON line `{"refused": {"code": …, "message": …}}`
//! on stderr and the exit code 1. A usage error exits with 2, as clap reports it.

use std::ffi::OsString;
use std::io::Write;
use std::net::{IpAddr, Ipv4Addr};
use std::path::PathBuf;
use std::time::Duration;

use clap::{Parser, Subcommand};
use yonder_link::proto::{SCHEMA_VERSION, features};
use yonder_link::{DEFAULT_PORT, Fingerprint};

use crate::home::Home;
use crate::refused::{AGENT_ERROR, Refusal, UPGRADE_FAILED, refused};
use crate::run::{ExitRecord, RunDir};
use crate::serve::log;
use crate::tuning;
use crate::upgrade::{self, Watched};

#[derive(Debug, Parser)]
#[command(
    name = "yonderd",
    version,
    about = "The agent of yonder: runs the commands of the clients in Branch Deck leases."
)]
struct Options {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Debug, Subcommand)]
enum Commands {
    /// Answers the requests of the clients. launchd runs it.
    Serve {
        /// The TCP port.
        #[arg(long, default_value_t = DEFAULT_PORT)]
        port: u16,
        /// The address to listen on. A test listens on the loopback address.
        #[arg(long, default_value_t = IpAddr::V4(Ipv4Addr::UNSPECIFIED), hide = true)]
        bind: IpAddr,
        /// The launchctl program of the watchdog of an upgrade. A test gives a fake one.
        #[arg(long, default_value = "launchctl", hide = true)]
        launchctl: PathBuf,
    },
    /// Runs one run to its end. `serve` starts it for each start request.
    #[command(hide = true)]
    Supervise {
        /// The run directory.
        run_dir: PathBuf,
        /// The descriptor of the flock of the run, which `serve` took and hands over.
        #[arg(long)]
        lock_fd: Option<i32>,
    },
    /// Adds a client to the allow list and starts the agent under launchd.
    Setup {
        /// The fingerprint of the client: 64 hex digits.
        #[arg(long)]
        client: Fingerprint,
        /// A name of the client for a person.
        #[arg(long)]
        name: String,
        /// The TCP port of the agent.
        #[arg(long, default_value_t = DEFAULT_PORT)]
        port: u16,
        /// The launchctl program. A test gives a fake one.
        #[arg(long, default_value = "launchctl", hide = true)]
        launchctl: PathBuf,
        /// How long to wait for the started agent to listen, in milliseconds. A test with a fake launchctl gives 0.
        #[arg(long, default_value_t = 60_000, hide = true)]
        listen_wait_ms: u64,
    },
    /// Prints the version and the protocol schema as one JSON line.
    Version,
    /// Installs a new agent binary over the one of the LaunchAgent, starts the watchdog, and restarts the agent. A run
    /// of `yonder upgrade` starts it on a host whose agent knows no upgrade request.
    #[command(hide = true)]
    Upgrade {
        /// The new binary.
        binary: PathBuf,
        /// The launchctl program. A test gives a fake one.
        #[arg(long, default_value = "launchctl", hide = true)]
        launchctl: PathBuf,
    },
    /// Waits for a `serve` of the new binary to listen, and else restores the previous binary and restarts the agent.
    /// An upgrade starts it.
    #[command(hide = true)]
    Watchdog {
        /// The binary of the LaunchAgent.
        binary: PathBuf,
        /// The SHA-256 of the new binary.
        #[arg(long)]
        sha256: String,
        /// How long to wait, in milliseconds.
        #[arg(long)]
        wait_ms: u64,
        #[arg(long, default_value = "launchctl")]
        launchctl: PathBuf,
    },
    /// Writes the environment of this process into a file as JSON. The login shell of the user runs it, see
    /// `login_env.rs`.
    #[command(hide = true)]
    DumpEnv {
        /// The file to write.
        file: PathBuf,
    },
}

/// Runs the command of `args`, the first of which is the program name. Answers the exit code.
pub(crate) fn run(args: impl IntoIterator<Item = OsString>, stdout: &mut dyn Write, stderr: &mut dyn Write) -> u8 {
    let options = match Options::try_parse_from(args) {
        Ok(options) => options,
        Err(error) => {
            let usage = error.use_stderr();
            let target: &mut dyn Write = if usage { stderr } else { stdout };
            let _ = write!(target, "{}", error.render());
            return if usage { 2 } else { 0 };
        }
    };
    match execute(options.command, stdout) {
        Ok(()) => 0,
        Err(refusal) => {
            let line = serde_json::json!({"refused": {"code": refusal.code, "message": refusal.message}});
            let _ = writeln!(stderr, "{line}");
            refusal.exit
        }
    }
}

fn execute(command: Commands, stdout: &mut dyn Write) -> Result<(), Refusal> {
    match command {
        Commands::Serve { port, bind, launchctl } => crate::serve::serve(Home::from_env()?, bind, port, launchctl, stdout),
        Commands::Supervise { run_dir, lock_fd } => {
            match Home::from_env() {
                Ok(home) => crate::supervise::supervise(&home, &run_dir, lock_fd),
                Err(refusal) => {
                    let _ = RunDir::new(run_dir).write_exit(&ExitRecord::refused(&refusal));
                }
            }
            Ok(())
        }
        Commands::Setup {
            client,
            name,
            port,
            launchctl,
            listen_wait_ms,
        } => {
            let exe = std::env::current_exe().map_err(|error| refused(AGENT_ERROR, format!("cannot find the agent binary: {error}")))?;
            let path = std::env::var("PATH").unwrap_or_default();
            let setup = crate::setup::Setup {
                client,
                name: &name,
                port,
                exe: &exe,
                launchctl: &launchctl,
                path: &path,
                listen_wait: Duration::from_millis(listen_wait_ms),
            };
            crate::setup::setup(&Home::from_env()?, &setup, stdout)
        }
        Commands::Version => {
            let line = serde_json::json!({"version": env!("CARGO_PKG_VERSION"), "schema_version": SCHEMA_VERSION, "features": features()});
            writeln!(stdout, "{line}").map_err(|error| refused(AGENT_ERROR, format!("cannot write stdout: {error}")))
        }
        Commands::Upgrade { binary, launchctl } => {
            let upgraded = upgrade::upgrade_from_file(&Home::from_env()?, &binary, &launchctl, tuning::upgrade_wait())?;
            let line = serde_json::to_string(&upgraded).map_err(|error| refused(AGENT_ERROR, error.to_string()))?;
            writeln!(stdout, "{line}").map_err(|error| refused(AGENT_ERROR, format!("cannot write stdout: {error}")))
        }
        Commands::Watchdog {
            binary,
            sha256,
            wait_ms,
            launchctl,
        } => {
            let home = Home::from_env()?;
            match upgrade::watchdog(&home, &binary, &sha256, Duration::from_millis(wait_ms), &launchctl, &mut |line| {
                log(line);
            })? {
                Watched::Listened(_) => Ok(()),
                Watched::RolledBack => Err(refused(UPGRADE_FAILED, format!("no serve of the binary {sha256} listened"))),
            }
        }
        Commands::DumpEnv { file } => {
            crate::login_env::dump_env(&file).map_err(|error| refused(AGENT_ERROR, format!("cannot write {}: {error}", file.display())))
        }
    }
}
