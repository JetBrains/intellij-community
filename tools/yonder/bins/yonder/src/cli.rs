//! The command line of `yonder`. A run leaves by the exit code of its command. A refusal says `yonder: <code>: <message>`
//! on the stderr and leaves by the exit of its [`Exit`]: 75 when the host did not run the command, so the caller can
//! run it locally instead, 130 after Ctrl+C before the command, and 255 for any other refusal. A command line that clap
//! refuses leaves by 2.

use std::ffi::OsString;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::{Mutex, PoisonError};
use std::time::Duration;

use clap::error::ErrorKind;
use clap::{Parser, Subcommand};
use yonder_client::{
    Call, Client, Config, DEFAULT_PORT, Exit, PrepareRequest, Processes, Refusal, RunInfo, RunOptions, RunRequest, UpgradeRequest,
    UpgradeWay, code, holder, interrupt, never, no_monorepo, phases_line, phases_short,
};

use crate::skill;

/// The holder prefix of a checkout when `--holder` names none.
const HOLDER_PREFIX: &str = "yonder";

/// The exit code of a command line that clap refuses.
const USAGE_EXIT: u8 = 2;

/// The niceness of a run when `--nice` names none: a run of `yonder` yields the CPU to the runs of `bt --host`, which
/// run at the niceness of the agent.
const DEFAULT_NICE: u8 = 10;

/// The deadline of the local git call that finds the checkout root.
const GIT_DEADLINE: Duration = Duration::from_secs(30);

#[derive(Debug, Parser)]
#[command(
    name = "yonder",
    version,
    about = "Runs a command of the working tree of this checkout on another machine, in a warm Branch Deck lease there"
)]
struct Cli {
    #[command(subcommand)]
    command: Command,
}

#[derive(Debug, Subcommand)]
enum Command {
    /// Runs a command of the working tree of this checkout on the host: HEAD plus the tracked and untracked changes that
    /// git does not ignore. The output streams as it comes, and the exit code is the one of the command, or 75 when the
    /// host did not run it. Ctrl+C cancels the run on the host.
    Run {
        /// The alias of the host file, which `yonder prepare` writes.
        alias: String,
        /// The main repository on the host (default: the one of the same name as this one beside the repository of the
        /// host file, such as `~/projects/branch-deck`, else the one of the host file).
        #[arg(long)]
        repo: Option<String>,
        /// Who the run comes from (default: `yonder-<hash of the checkout path>`): the host lends the slot that this
        /// holder used last first, and keeps the base of the thin packs per holder.
        #[arg(long)]
        holder: Option<String>,
        /// A directory relative to the slot root whose new entries come back to this checkout. Repeatable.
        #[arg(long = "fetch", value_name = "DIR")]
        fetch: Vec<String>,
        /// Run the committed HEAD only, and refuse a checkout with local changes.
        #[arg(long)]
        head: bool,
        /// The niceness of the command on the host, 0 to 19. 0 runs it at the priority of `bt --host`.
        #[arg(long, default_value_t = DEFAULT_NICE, value_parser = clap::value_parser!(u8).range(0..=19))]
        nice: u8,
        /// Apply the changes that the command made to the files, such as a formatter or new snapshots, to this working
        /// tree. Without it, nothing changes here but the fetched files and the outputs of `.yonder.toml`.
        #[arg(long)]
        apply: bool,
        /// The command and its arguments, after `--`.
        #[arg(last = true, required = true)]
        command: Vec<String>,
    },
    /// Follows the output of a run from its first line. Ctrl+C leaves, and the run goes on.
    Attach { alias: String, run: String },
    /// Asks the host to interrupt a run, and returns when the host has the request. `attach` shows its end.
    Cancel { alias: String, run: String },
    /// Copies the files of a run into this checkout, for a run whose files did not come. The record of the run in this
    /// checkout says which files come.
    Fetch {
        alias: String,
        run: String,
        /// A directory relative to the slot root that each staged file must be below. Repeatable; without it, the fetch
        /// directories of the run record. Without a record, only the staged files below it come.
        #[arg(long = "fetch", value_name = "DIR")]
        fetch: Vec<String>,
    },
    /// Removes a run and its staged files on the host.
    Forget { alias: String, run: String },
    /// Lists the runs that the agent keeps, each with the steps of the host that took 0.1 s or more.
    Runs { alias: String },
    /// Installs the agent on the host over ssh, adds this client to its allow list, and writes the host file. In a
    /// checkout of the monorepo, `yonder prepare <alias>` is the whole command: it builds the agent here first.
    Prepare {
        /// The ssh alias of the host.
        alias: String,
        /// A local `yonderd` binary for the host (default: the client builds `@community//tools/yonder:yonderd` with
        /// `./bazel.cmd` in the root of this checkout of the monorepo).
        #[arg(long)]
        agent: Option<PathBuf>,
        #[arg(long, default_value_t = DEFAULT_PORT)]
        port: u16,
        /// The main repository on the host, for the host file.
        #[arg(long, default_value = "~/projects/idea-1")]
        repo: String,
        /// The name of this client in the allow list of the agent (default: `yonder on <host name>`).
        #[arg(long)]
        name: Option<String>,
    },
    /// Updates the agent on the host over the link, without ssh. The agent installs the new binary and restarts, and a
    /// watchdog there brings back the previous binary when the new one does not start. An agent that knows no upgrade
    /// request refuses: `prepare` installs a new one.
    Upgrade {
        alias: String,
        /// A local `yonderd` binary for the host (default: the client builds `@community//tools/yonder:yonderd` with
        /// `./bazel.cmd` in the root of this checkout of the monorepo).
        #[arg(long)]
        agent: Option<PathBuf>,
        /// Send the binary that the agent runs already too, and let the agent take a binary of a lower version.
        #[arg(long)]
        force: bool,
    },
    /// The agent skill of yonder of this build: `SKILL.md` and the API documents, for an agent harness such as Claude
    /// Code. `upgrade` also replaces an installed copy of another build that has no local edits.
    Skill {
        #[command(subcommand)]
        command: SkillCommand,
    },
}

#[derive(Debug, Subcommand)]
enum SkillCommand {
    /// Writes the skill into `~/.claude/skills/yonder/`. A copy with local edits stays, unless `--force`.
    Install {
        /// The skill directory (default: `~/.claude/skills/yonder`).
        #[arg(long)]
        dir: Option<PathBuf>,
        /// Replace a copy with local edits too.
        #[arg(long)]
        force: bool,
    },
    /// Prints `SKILL.md` of this build.
    Print,
}

/// One invocation, with the program name first. `skill_dir` is the skill directory of the user, `None` without a home
/// directory. Answers the exit code.
pub(crate) fn run<I, T>(
    argv: I,
    config: Result<Config, Refusal>,
    skill_dir: Option<&Path>,
    processes: &dyn Processes,
    stdout: &mut (dyn Write + Send),
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
            return USAGE_EXIT;
        }
    };
    match config.and_then(|config| execute(cli.command, config, skill_dir, processes, stdout, say)) {
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
    skill_dir: Option<&Path>,
    processes: &dyn Processes,
    stdout: &mut (dyn Write + Send),
    say: &(dyn Fn(&str) + Sync),
) -> Result<u8, Refusal> {
    let presses = interrupt::presses;
    let stdout = Mutex::new(stdout);
    // The stdout lines of a run go out as they come.
    let line_out = |line: &str| {
        let mut stdout = stdout.lock().unwrap_or_else(PoisonError::into_inner);
        let _ = writeln!(stdout, "{line}").and_then(|()| stdout.flush());
    };
    let client = Client::new(config, processes, say, &presses).with_stdout(&line_out);
    let answer = |text: &str| {
        let _ = writeln!(stdout.lock().unwrap_or_else(PoisonError::into_inner), "{text}");
        0
    };
    match command {
        Command::Run {
            alias,
            repo,
            holder: named,
            fetch,
            head,
            nice,
            apply,
            command,
        } => {
            let checkout = checkout_root(processes)?;
            let holder = holder_of(named, &checkout);
            install_interrupt(say, "Ctrl+C ends this process, and the run goes on on the host");
            let request = RunRequest {
                checkout: &checkout,
                repo: repo.as_deref(),
                holder: &holder,
                command: &command,
                fetch: &fetch,
            };
            let options = RunOptions {
                working_tree: !head,
                nice: Some(nice),
                outputs: true,
                apply,
                repo_by_name: repo.is_none(),
            };
            let outcome = client.run_with(&alias, &request, &options)?;
            if !outcome.fetched.is_empty() {
                say(&format!(
                    "yonder: copied {} files of the run {} into {}",
                    outcome.fetched.len(),
                    outcome.run,
                    checkout.display()
                ));
            }
            say(&phases_line(&outcome.phases));
            Ok(exit_code(outcome.code))
        }
        Command::Attach { alias, run } => {
            let followed = client.attach(&alias, &run)?;
            if !followed.phases.is_empty() {
                say(&phases_line(&followed.phases));
            }
            Ok(exit_code(followed.code))
        }
        Command::Cancel { alias, run } => Ok(answer(&client.cancel(&alias, &run)?)),
        Command::Fetch { alias, run, fetch } => {
            let checkout = checkout_root(processes)?;
            let fetched = client.fetch(&alias, &run, &checkout, &fetch)?;
            Ok(answer(&format!(
                "copied {} files of the run {run} into {}",
                fetched.len(),
                checkout.display()
            )))
        }
        Command::Forget { alias, run } => Ok(answer(&client.forget(&alias, &run)?)),
        Command::Runs { alias } => {
            let runs = client.runs(&alias)?;
            if runs.is_empty() {
                answer(&format!("no runs on {alias}"));
            }
            for run in runs {
                answer(&run_line(&run));
            }
            Ok(0)
        }
        Command::Prepare {
            alias,
            agent,
            port,
            repo,
            name,
        } => {
            // Each child runs in a group of its own, so only the client can stop the Bazel build and the ssh calls.
            install_interrupt(say, "Ctrl+C ends this process, and a child such as the Bazel build goes on");
            let agent = if let Some(agent) = agent {
                agent
            } else {
                let root = checkout_root(processes).map_err(|refusal| no_monorepo(&refusal.message))?;
                client.build_agent(&root)?
            };
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
            Ok(answer(&format!(
                "{copied}\nlaunchd: {}\nyonderd {} answers on {} ({} {})\nhost file {}",
                prepared.launchd,
                info.version,
                info.address,
                info.os,
                info.arch,
                prepared.host_file.display()
            )))
        }
        Command::Upgrade { alias, agent, force } => {
            install_interrupt(say, "Ctrl+C ends this process, and a child such as the Bazel build goes on");
            // The skill is local, so its refresh comes first and does not wait for the host.
            if let Some(said) = skill_dir.and_then(skill::refresh) {
                say(&said);
            }
            let checkout = checkout_root(processes).ok();
            let upgraded = client.upgrade(&UpgradeRequest {
                alias: &alias,
                agent: agent.as_deref(),
                checkout: checkout.as_deref(),
                force,
            })?;
            Ok(answer(&upgraded_text(&alias, &upgraded)))
        }
        Command::Skill {
            command: SkillCommand::Install { dir, force },
        } => {
            let dir = dir.or_else(|| skill_dir.map(Path::to_path_buf)).ok_or_else(|| {
                Refusal::new(
                    code::CONFIG,
                    Exit::Refused,
                    "this process has no home directory for ~/.claude/skills",
                )
            })?;
            let installed = skill::install(&dir, force)?;
            Ok(answer(&skill::installed_text(&dir, &installed)))
        }
        Command::Skill {
            command: SkillCommand::Print,
        } => Ok(answer(skill::SKILL.trim_end())),
    }
}

/// The answer of `yonder upgrade`.
fn upgraded_text(alias: &str, upgraded: &yonder_client::Upgraded) -> String {
    let short = |sha256: &str| sha256.get(..12).unwrap_or(sha256).to_owned();
    let info = &upgraded.agent;
    let answers = format!(
        "yonderd {} answers on {} ({} {})",
        upgraded.version, info.address, info.os, info.arch
    );
    let how = match &upgraded.way {
        UpgradeWay::Current => return format!("yonderd {} on {alias} is current\n{answers}", short(&upgraded.sha256)),
        UpgradeWay::Request => "over the link",
    };
    let previous = upgraded
        .previous_sha256
        .as_deref()
        .map_or_else(|| "an older build".to_owned(), short);
    format!(
        "upgraded yonderd on {alias} from {previous} to {} {how}\n{answers}",
        short(&upgraded.sha256)
    )
}

/// One run of `yonder runs`: its id, its state, its holder, its commit, its command, and the steps that took 0.1 s or
/// more.
fn run_line(run: &RunInfo) -> String {
    let state = run.exit.map_or_else(|| "running".to_owned(), |code| format!("exit {code}"));
    let commit = run.commit.get(..12).unwrap_or(&run.commit);
    let mut line = format!("{}  {state}  {}  {commit}  {}", run.run, run.holder, run.command.join(" "));
    let phases = phases_short(&run.phases);
    if !phases.is_empty() {
        line.push_str(&format!("  ({phases})"));
    }
    line
}

/// Installs the Ctrl+C handler, and says `without` when it cannot.
fn install_interrupt(say: &(dyn Fn(&str) + Sync), without: &str) {
    if let Err(said) = interrupt::install() {
        say(&format!("yonder: {said}; {without}"));
    }
}

/// The exit code of a command as an exit code of this process. A code outside 0 to 255 leaves by 255.
fn exit_code(code: i32) -> u8 {
    u8::try_from(code).unwrap_or(Exit::Refused.code())
}

/// The holder of `--holder`, or the one of the checkout.
fn holder_of(named: Option<String>, checkout: &Path) -> String {
    named.unwrap_or_else(|| holder(HOLDER_PREFIX, checkout))
}

/// The root of the checkout of the working directory.
fn checkout_root(processes: &dyn Processes) -> Result<PathBuf, Refusal> {
    let ran = processes.run(&Call::new("git", &["rev-parse", "--show-toplevel"], GIT_DEADLINE), &never);
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
    let ran = processes.run(&Call::new("hostname", &[] as &[&str], GIT_DEADLINE), &never);
    let name = ran.stdout_text().trim().to_owned();
    if ran.ok() && !name.is_empty() { name } else { "client".to_owned() }
}

#[cfg(test)]
mod tests;
