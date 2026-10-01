//! The command line, parsed by clap.

use std::ffi::OsString;
use std::path::PathBuf;
use std::time::Duration;

use bt_core::{Refusal, exit, fail_usage};
use clap::error::ErrorKind;
use clap::{Parser, Subcommand, ValueEnum};

use crate::arm::Arm;

/// One parsed invocation.
#[derive(Clone, Debug, PartialEq, Eq, Parser)]
#[command(
    name = "startup-bench",
    disable_version_flag = true,
    arg_required_else_help = true,
    about = "Measures the start-up of an IDE from the Bazel dev distribution.",
    after_help = "Exit codes: 0 success, 2 usage (a bad invocation, no license, no run passed the gate), 6 infrastructure."
)]
pub(crate) struct Args {
    #[command(subcommand)]
    pub(crate) command: Command,
}

#[derive(Clone, Debug, PartialEq, Eq, Subcommand)]
pub(crate) enum Command {
    /// Starts the IDE without a project and measures the time to the welcome screen, modal and non-modal.
    ///
    /// The session builds the target once, writes a sandbox template, primes each arm once, and then runs the
    /// measured runs with the arms interleaved. Each measured run starts from a copy of the primed sandbox.
    Welcome(WelcomeArgs),
    /// Parses a finished session again and prints its digest. No IDE starts.
    Replay(ReplayArgs),
    /// Starts the IDE without a project, then opens <PROJECT> in the running IDE and measures its editor.
    OpenProject(OpenProjectArgs),
}

/// The options of a command that starts the IDE.
#[derive(Clone, Debug, PartialEq, Eq, clap::Args)]
pub(crate) struct LaunchArgs {
    /// The Bazel launcher target of the IDE.
    #[arg(long, default_value = "//build:idea", value_parser = flag_value)]
    pub(crate) target: String,
    /// The time to wait after the welcome event, before the quit: `3s`, `500ms`, `1m` or `0`.
    #[arg(long, default_value = "3s", value_parser = parse_hold)]
    pub(crate) hold: Duration,
    /// The session directory. The default is `out/startup-bench/runs/<yyyymmdd-HHMMSS>` under the checkout root.
    #[arg(long, value_name = "DIR")]
    pub(crate) session: Option<PathBuf>,
    /// Writes only the `{ok, data}` envelope to stdout. The digest goes to stderr.
    #[arg(long)]
    pub(crate) json: bool,
}

#[derive(Clone, Debug, PartialEq, Eq, clap::Args)]
pub(crate) struct WelcomeArgs {
    /// The measured runs per arm.
    #[arg(long, default_value_t = 5, value_parser = clap::value_parser!(u32).range(1..=50))]
    pub(crate) runs: u32,
    #[arg(long, value_enum, default_value_t = ArmChoice::Both)]
    pub(crate) arm: ArmChoice,
    /// Starts each measured run from a fresh copy of the unprimed template, and skips the prime runs.
    #[arg(long)]
    pub(crate) cold: bool,
    /// Samples the CPU with async-profiler and reports the top frames of the EDT.
    #[arg(long)]
    pub(crate) profile: bool,
    #[command(flatten)]
    pub(crate) launch: LaunchArgs,
}

#[derive(Clone, Debug, PartialEq, Eq, clap::Args)]
pub(crate) struct OpenProjectArgs {
    /// The project directory that the running IDE opens.
    pub(crate) project: PathBuf,
    /// The measured runs.
    #[arg(long, default_value_t = 3, value_parser = clap::value_parser!(u32).range(1..=50))]
    pub(crate) runs: u32,
    #[command(flatten)]
    pub(crate) launch: LaunchArgs,
}

#[derive(Clone, Debug, PartialEq, Eq, clap::Args)]
pub(crate) struct ReplayArgs {
    /// The session directory of a finished `welcome` or `open-project` command.
    pub(crate) session: PathBuf,
    /// Writes only the `{ok, data}` envelope to stdout. The digest goes to stderr.
    #[arg(long)]
    pub(crate) json: bool,
}

/// The `--arm` value.
#[derive(Clone, Copy, Debug, PartialEq, Eq, ValueEnum)]
pub(crate) enum ArmChoice {
    Both,
    Modal,
    NonModal,
}

impl ArmChoice {
    /// The arms in the interleave order.
    pub(crate) fn arms(self) -> Vec<Arm> {
        match self {
            Self::Both => vec![Arm::Modal, Arm::NonModal],
            Self::Modal => vec![Arm::Modal],
            Self::NonModal => vec![Arm::NonModal],
        }
    }
}

impl Command {
    /// Tells whether the caller asked for the JSON envelope.
    pub(crate) const fn json(&self) -> bool {
        match self {
            Self::Welcome(args) => args.launch.json,
            Self::OpenProject(args) => args.launch.json,
            Self::Replay(args) => args.json,
        }
    }
}

/// A value of an option, refused when it is another long option.
fn flag_value(value: &str) -> Result<String, String> {
    if value.starts_with("--") {
        return Err(format!("a value is required, and {value} is not one"));
    }
    Ok(value.to_owned())
}

/// Parses `--hold`: a whole number with the unit `ms`, `s` or `m`, or `0`.
pub(crate) fn parse_hold(value: &str) -> Result<Duration, String> {
    if value == "0" {
        return Ok(Duration::ZERO);
    }
    let (number, unit) = value
        .find(|character: char| !character.is_ascii_digit())
        .map_or((value, ""), |index| value.split_at(index));
    let Ok(amount) = number.parse::<u64>() else {
        return Err(format!("must be a whole number with a unit (ms, s or m), got: {value}"));
    };
    let duration = match unit {
        "ms" => Duration::from_millis(amount),
        "s" => Duration::from_secs(amount),
        "m" => Duration::from_secs(amount.saturating_mul(60)),
        "" => return Err(format!("needs a unit (ms, s or m), got: {value}")),
        other => return Err(format!("unknown unit {other}: use ms, s or m")),
    };
    if duration > Duration::from_secs(600) {
        return Err(format!("must be at most 10m, got: {value}"));
    }
    Ok(duration)
}

/// What a command line asks for: a command, or the help text with the exit code.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum Invocation {
    Run(Command),
    Help { text: String, exit: u8 },
}

/// Parses a command line without the program name. A parse error is a usage refusal with the message of clap.
pub(crate) fn parse_args<I, T>(argv: I) -> Result<Invocation, Refusal>
where
    I: IntoIterator<Item = T>,
    T: Into<OsString> + Clone,
{
    let argv = std::iter::once(OsString::from("startup-bench")).chain(argv.into_iter().map(Into::into));
    match Args::try_parse_from(argv) {
        Ok(args) => Ok(Invocation::Run(args.command)),
        Err(error) => match error.kind() {
            ErrorKind::DisplayHelp | ErrorKind::DisplayVersion => Ok(Invocation::Help {
                text: error.render().to_string(),
                exit: exit::GREEN,
            }),
            ErrorKind::DisplayHelpOnMissingArgumentOrSubcommand => Ok(Invocation::Help {
                text: error.render().to_string(),
                exit: exit::USAGE,
            }),
            _ => Err(fail_usage(usage_message(&error))),
        },
    }
}

/// The message of clap without its `Usage:` tail and its hint lines.
fn usage_message(error: &clap::Error) -> String {
    let rendered = error.render().to_string();
    let message = rendered.split("\n\nUsage:").next().unwrap_or(&rendered);
    let message = message
        .lines()
        .filter(|line| {
            let line = line.trim_start();
            !line.starts_with("tip:") && !line.starts_with("For more information, try")
        })
        .collect::<Vec<_>>()
        .join("\n");
    let message = message.trim();
    message.strip_prefix("error: ").unwrap_or(message).to_owned()
}

#[cfg(test)]
mod tests;
