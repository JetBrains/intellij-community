//! The command line of `yonder`. A run leaves by the exit code of its command. A refusal leaves by 255, with
//! `yonder: <code>: <message>` on the stderr. A command line that clap refuses leaves by 255 too, because no command
//! ran.

use std::ffi::OsString;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::time::Duration;

use clap::error::ErrorKind;
use clap::{Parser, Subcommand};
use yonder_client::{Call, Client, Config, DEFAULT_PORT, Exit, PrepareRequest, Processes, Refusal, RunRequest, code, holder, interrupt};

/// The holder prefix of a checkout when `--holder` names none.
const HOLDER_PREFIX: &str = "yonder";

/// The deadline of the local git call that finds the checkout root.
const GIT_DEADLINE: Duration = Duration::from_secs(30);

#[derive(Debug, Parser)]
#[command(
    name = "yonder",
    version,
    about = "Runs a command of the committed HEAD of this checkout on another machine, in a warm Branch Deck lease there"
)]
struct Cli {
    #[command(subcommand)]
    command: Command,
}

#[derive(Debug, Subcommand)]
enum Command {
    /// Runs a command of the committed HEAD of this checkout on the host. The stderr streams as it comes, the stdout
    /// comes at the end, and the exit code is the one of the command. Ctrl+C cancels the run on the host.
    Run {
        /// The alias of the host file, which `yonder prepare` writes.
        alias: String,
        /// The main repository on the host (default: the one of the host file).
        #[arg(long)]
        repo: Option<String>,
        /// The lease holder on the host (default: `yonder-<hash of the checkout path>`).
        #[arg(long)]
        holder: Option<String>,
        /// A directory relative to the slot root whose new entries come back to this checkout. Repeatable.
        #[arg(long = "fetch", value_name = "DIR")]
        fetch: Vec<String>,
        /// A lease of another holder that no run used for this many hours goes back to the pool before the run.
        #[arg(long, default_value_t = 8)]
        idle_hours: u32,
        /// The command and its arguments, after `--`.
        #[arg(last = true, required = true)]
        command: Vec<String>,
    },
    /// Follows the output of a run from its first line. Ctrl+C leaves, and the run goes on.
    Attach { alias: String, run: String },
    /// Asks the host to interrupt a run, and returns when the host has the request. `attach` shows its end.
    Cancel { alias: String, run: String },
    /// Copies the staged files of a run into this checkout, for a run whose files did not come.
    Fetch {
        alias: String,
        run: String,
        /// A directory relative to the slot root that each file must be below. Repeatable; without it, any plain
        /// relative path is accepted.
        #[arg(long = "fetch", value_name = "DIR")]
        fetch: Vec<String>,
    },
    /// Removes a run and its staged files on the host.
    Forget { alias: String, run: String },
    /// Lists the runs that the agent keeps.
    Runs { alias: String },
    /// Takes the warm lease of this checkout without a run.
    Lease {
        alias: String,
        #[arg(long)]
        repo: Option<String>,
        #[arg(long)]
        holder: Option<String>,
    },
    /// Returns the lease of this checkout.
    Release {
        alias: String,
        #[arg(long)]
        repo: Option<String>,
        #[arg(long)]
        holder: Option<String>,
    },
    /// Installs the agent on the host over ssh, adds this client to its allow list, and writes the host file.
    Prepare {
        /// The ssh alias of the host.
        alias: String,
        /// The local `yonderd` binary for the host.
        #[arg(long)]
        agent: PathBuf,
        #[arg(long, default_value_t = DEFAULT_PORT)]
        port: u16,
        /// The main repository on the host, for the host file.
        #[arg(long, default_value = "~/projects/idea-1")]
        repo: String,
        /// The name of this client in the allow list of the agent (default: `yonder on <host name>`).
        #[arg(long)]
        name: Option<String>,
    },
}

/// One invocation, with the program name first. Answers the exit code.
pub(crate) fn run<I, T>(
    argv: I,
    config: Result<Config, Refusal>,
    processes: &dyn Processes,
    stdout: &mut dyn Write,
    say: &(dyn Fn(&str) + Sync),
) -> u8
where
    I: IntoIterator<Item = T>,
    T: Into<OsString> + Clone,
{
    let cli = match Cli::try_parse_from(argv) {
        Ok(cli) => cli,
        Err(error) => {
            let text = error.render().to_string();
            if matches!(error.kind(), ErrorKind::DisplayHelp | ErrorKind::DisplayVersion) {
                let _ = write!(stdout, "{text}");
                return 0;
            }
            say(text.trim_end());
            return Exit::Refused.code();
        }
    };
    match config.and_then(|config| execute(cli.command, config, processes, stdout, say)) {
        Ok(code) => code,
        Err(refusal) => {
            say(&format!("yonder: {}: {}", refusal.code, refusal.message));
            refusal.exit.code()
        }
    }
}

fn execute(
    command: Command,
    config: Config,
    processes: &dyn Processes,
    stdout: &mut dyn Write,
    say: &(dyn Fn(&str) + Sync),
) -> Result<u8, Refusal> {
    let presses = interrupt::presses;
    let client = Client::new(config, processes, say, &presses);
    match command {
        Command::Run {
            alias,
            repo,
            holder: named,
            fetch,
            idle_hours,
            command,
        } => {
            let checkout = checkout_root(processes)?;
            let holder = named.unwrap_or_else(|| holder(HOLDER_PREFIX, &checkout));
            if let Err(said) = interrupt::install() {
                say(&format!(
                    "yonder: {said}; Ctrl+C ends this process, and the run goes on on the host"
                ));
            }
            let request = RunRequest {
                checkout: &checkout,
                repo: repo.as_deref(),
                holder: &holder,
                command: &command,
                fetch: &fetch,
                idle_hours,
            };
            let outcome = client.run(&alias, &request)?;
            let _ = write!(stdout, "{}", outcome.stdout);
            if !outcome.fetched.is_empty() {
                say(&format!(
                    "yonder: copied {} files of the run {} into {}",
                    outcome.fetched.len(),
                    outcome.run,
                    checkout.display()
                ));
            }
            Ok(exit_code(outcome.code))
        }
        Command::Attach { alias, run } => {
            let followed = client.attach(&alias, &run)?;
            let _ = write!(stdout, "{}", followed.stdout);
            Ok(exit_code(followed.code))
        }
        Command::Cancel { alias, run } => Ok(answer(stdout, &client.cancel(&alias, &run)?)),
        Command::Fetch { alias, run, fetch } => {
            let checkout = checkout_root(processes)?;
            let fetched = client.fetch(&alias, &run, &checkout, &fetch)?;
            Ok(answer(
                stdout,
                &format!("copied {} files of the run {run} into {}", fetched.len(), checkout.display()),
            ))
        }
        Command::Forget { alias, run } => Ok(answer(stdout, &client.forget(&alias, &run)?)),
        Command::Runs { alias } => {
            for run in client.runs(&alias)? {
                let state = run.exit.map_or_else(|| "running".to_owned(), |code| format!("exit {code}"));
                let commit = run.commit.get(..12).unwrap_or(&run.commit);
                let _ = writeln!(stdout, "{}  {state}  {}  {commit}  {}", run.run, run.holder, run.command.join(" "));
            }
            Ok(0)
        }
        Command::Lease {
            alias,
            repo,
            holder: named,
        } => {
            let holder = holder_of(named, processes)?;
            let leased = client.lease(&alias, repo.as_deref(), &holder)?;
            let fresh = if leased.fresh { "new" } else { "warm" };
            Ok(answer(
                stdout,
                &format!("the lease {} of {holder} is {} ({fresh})", leased.lease_id, leased.slot),
            ))
        }
        Command::Release {
            alias,
            repo,
            holder: named,
        } => {
            let holder = holder_of(named, processes)?;
            Ok(answer(stdout, &client.release(&alias, repo.as_deref(), &holder)?))
        }
        Command::Prepare {
            alias,
            agent,
            port,
            repo,
            name,
        } => {
            let name = name.unwrap_or_else(|| format!("yonder on {}", local_name(processes)));
            let prepared = client.prepare(&PrepareRequest {
                alias: &alias,
                agent: &agent,
                port,
                repo: &repo,
                name: &name,
            })?;
            let copied = if prepared.copied {
                format!("copied {} to {alias}:~/.local/bin/yonderd", agent.display())
            } else {
                format!("{alias}:~/.local/bin/yonderd is current")
            };
            let info = &prepared.agent;
            Ok(answer(
                stdout,
                &format!(
                    "{copied}\nlaunchd: {}\nyonderd {} answers on {} ({} {})\nhost file {}",
                    prepared.launchd,
                    info.version,
                    info.address,
                    info.os,
                    info.arch,
                    prepared.host_file.display()
                ),
            ))
        }
    }
}

fn answer(stdout: &mut dyn Write, text: &str) -> u8 {
    let _ = writeln!(stdout, "{text}");
    0
}

/// The exit code of a command as an exit code of this process. A code outside 0 to 255 leaves by 255.
fn exit_code(code: i32) -> u8 {
    u8::try_from(code).unwrap_or(Exit::Refused.code())
}

fn holder_of(named: Option<String>, processes: &dyn Processes) -> Result<String, Refusal> {
    match named {
        Some(holder) => Ok(holder),
        None => Ok(holder(HOLDER_PREFIX, &checkout_root(processes)?)),
    }
}

/// The root of the checkout of the working directory.
fn checkout_root(processes: &dyn Processes) -> Result<PathBuf, Refusal> {
    let ran = processes.run(&Call::new("git", &["rev-parse", "--show-toplevel"], GIT_DEADLINE));
    let root = ran.stdout_text().trim().to_owned();
    if !ran.ok() || root.is_empty() {
        return Err(Refusal::new(
            code::GIT_FAILED,
            Exit::Refused,
            format!("the working directory is in no git checkout: {}", ran.said()),
        ));
    }
    Ok(Path::new(&root).to_path_buf())
}

/// The host name of this machine for the allow list of the agent, or `client`.
fn local_name(processes: &dyn Processes) -> String {
    let ran = processes.run(&Call::new("hostname", &[] as &[&str], GIT_DEADLINE));
    let name = ran.stdout_text().trim().to_owned();
    if ran.ok() && !name.is_empty() { name } else { "client".to_owned() }
}

#[cfg(test)]
mod tests;
