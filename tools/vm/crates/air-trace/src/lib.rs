//! `air-trace`: Playwright-style traces of the AIR UI scenarios, run through `community/tools/trace.cmd`. The
//! recorder a lane starts is not here: it is avl-record's own binary, `air-trace-record`.
//!
//! It builds for Unix and Windows, like the controller (ADR 0186). No lane target links it. [plan] answers what an
//! input covers and how to record new traces of it, and [serve] is the local server behind the trace viewer. Both
//! read bundles with `avl_trace_tools::discover`, so a bundle is named the same way by the planner, the server and
//! the controller's trace sync. The planner reads the UI lanes from `avl_affected::lanes`, the table the controller
//! reads. `pack` is `avl_trace_tools::pack`. Each subcommand owns its runtime and its signals: the server shuts down,
//! while `pack` and `plan` keep the default disposition.
//!
//! No subcommand may exit with 78: `trace.cmd` reports its own failures with it, so that "the tool did not start"
//! cannot be mistaken for an answer.
//!
//! An expected failure is a [`Refusal`]: a code from [`code`], the message, and an [`Exit`]. A command line
//! prints `air-trace <command>: <message>` and leaves by the exit; the planner route answers `{error, code}`.

use std::ffi::OsString;
use std::io::{Read, Write};
use std::path::PathBuf;

use avl_trace_tools::pack::{PackOptions, pack, write_report};
use avl_trace_tools::viewer::{DEFAULT_SERVE_PORT, runs_url};
use clap::Parser;

use crate::plan::{PLAN_ABOUT, PlanArgs, run_plan};
use crate::serve::{ServeArgs, serve_main};

mod plan;
mod serve;

#[cfg(test)]
mod tests;

/// The exit codes of every subcommand, a closed set. None is 78, which `trace.cmd` keeps for its own failures.
///
/// A type and not numbers, so that a refusal of `bt`, whose 1 to 6 mean other things, cannot leave by an exit of
/// this program without a map.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Exit {
    /// The command answered.
    Ok = 0,
    /// The command could not do its work: a pack, a plan or a server that failed.
    Broken = 1,
    /// The command line, or a value it names, is wrong.
    Usage = 2,
    /// `plan`: an answer that reached no scenario, class or bundle. The answer is still printed, with every input's
    /// reason, because the reason is what the caller acts on.
    Nothing = 3,
}

impl Exit {
    /// The status as a number.
    pub(crate) const fn code(self) -> u8 {
        self as u8
    }
}

/// The refusal codes that more than one place gives.
pub(crate) mod code {
    /// A command line, or a value it names, is wrong.
    pub(crate) const USAGE: &str = "usage";
    /// The checkout is unknown, and the command needs it.
    pub(crate) const NO_CHECKOUT: &str = "no_checkout";
    /// The planner could not answer an input.
    pub(crate) const PLAN_FAILED: &str = "plan_failed";
    /// The site build failed.
    pub(crate) const SITE_BUILD_FAILED: &str = "site_build_failed";
}

/// An expected failure of `air-trace`: the shared refusal, with the exit codes of [`Exit`].
pub(crate) type Refusal = refusal::Refusal<Exit>;

/// The refusal of a command line, or of a value it names.
pub(crate) fn usage(message: impl Into<String>) -> Refusal {
    Refusal::new(code::USAGE, Exit::Usage, message)
}

/// A refusal of `bt`'s crates as one of `air-trace`, with its code and message. `bt`'s usage is this program's usage,
/// and every other exit of `bt` is a failure to do the work.
pub(crate) fn from_bt(refused: refusal::Refusal) -> Refusal {
    let exit = if refused.exit == bt_core::exit::USAGE {
        Exit::Usage
    } else {
        Exit::Broken
    };
    Refusal::new(refused.code, exit, refused.message)
}

/// A refusal of the controller's crates as this program's, with the code and the message kept. A usage refusal stays a
/// usage refusal, and every other one is [`Exit::Broken`].
pub(crate) fn from_controller(refused: avl_base::Refusal) -> Refusal {
    let exit = if refused.exit == avl_base::Exit::USAGE {
        Exit::Usage
    } else {
        Exit::Broken
    };
    Refusal::new(refused.code, exit, refused.message)
}

/// Prints a refusal as `air-trace <command>: <message>` and answers its exit. A line that cannot be written has
/// nowhere else to go; the exit still says what happened.
pub(crate) fn report(stderr: &mut dyn Write, command: &str, refused: &Refusal) -> u8 {
    let _ = writeln!(stderr, "air-trace {command}: {}", refused.message);
    refused.exit.code()
}

/// `air-trace`'s command line.
#[derive(Parser, Debug)]
#[command(
    name = "air-trace",
    about = "Playwright-style traces of the AIR UI scenarios",
    arg_required_else_help = true,
    after_help = after_help()
)]
pub(crate) struct Cli {
    #[command(subcommand)]
    pub command: Command,
}

/// The subcommands, with the names `trace.cmd` and the docs call them by.
#[derive(clap::Subcommand, Debug)]
pub(crate) enum Command {
    /// Zip every bundle under SOURCE_DIR, media stored so they can be served by byte range.
    Pack(PackArgs),
    /// Serve the trace viewer, the bundles and the VM runs on this machine; --detach starts one server for this
    /// machine in the background, or finds the one that runs.
    Serve(ServeArgs),
    /// The scenarios INPUT covers, their bundles on this machine, and what to run for new ones.
    #[command(long_about = PLAN_ABOUT)]
    Plan(PlanArgs),
}

/// The paragraph under the top-level help: what INPUT is, and where `serve` answers.
fn after_help() -> String {
    format!(
        "INPUT is a bundle, a flow-*.txt, a flow profile, a test.xml, a vm.cmd report, a source path or name, a \
         class name or a flow id. `air-trace serve` answers at {}. Each subcommand prints its full usage with \
         --help.",
        runs_url(DEFAULT_SERVE_PORT)
    )
}

/// `air-trace pack`'s command line.
#[derive(clap::Args, Clone, Debug, PartialEq, Eq)]
pub(crate) struct PackArgs {
    /// The trace root to pack.
    #[arg(value_name = "SOURCE_DIR")]
    pub source: PathBuf,
    /// The zip to write, atomically; it must not be inside SOURCE_DIR.
    #[arg(value_name = "DESTINATION_ZIP")]
    pub destination: PathBuf,
}

/// The name of the binary, which the usage gives.
const PROGRAM: &str = "air-trace";

/// Runs `air-trace`, the whole program behind `main`: `args` are the arguments after the program name. It answers
/// the exit code.
///
/// `pack` and `plan` answer on the three streams. `serve` writes its lines to the process's own standard output and
/// standard error, because the tasks of the server outlive a borrowed stream, and it reads no input.
pub fn run(args: impl IntoIterator<Item = OsString>, stdin: &mut dyn Read, stdout: &mut dyn Write, stderr: &mut dyn Write) -> u8 {
    let cli = match Cli::try_parse_from(std::iter::once(OsString::from(PROGRAM)).chain(args)) {
        Ok(cli) => cli,
        Err(error) => {
            let rendered = error.render().to_string();
            // A usage that cannot be written has nowhere else to go; the exit code still says what happened.
            let _ = if error.use_stderr() {
                stderr.write_all(rendered.as_bytes())
            } else {
                stdout.write_all(rendered.as_bytes())
            };
            return parse_exit(&error);
        }
    };
    match cli.command {
        Command::Pack(args) => run_pack(&args, stdout, stderr),
        Command::Serve(args) => serve_main(args),
        Command::Plan(args) => run_plan(&args, stdin, stdout, stderr),
    }
}

/// The exit code of a command line clap did not run: 0 for `--help`, [`Exit::Usage`] for everything else, the bare
/// `air-trace` included, whose help is an answer to a missing subcommand.
pub(crate) fn parse_exit(error: &clap::Error) -> u8 {
    let exit = if error.use_stderr() { Exit::Usage } else { Exit::Ok };
    exit.code()
}

/// Runs `air-trace pack`: 0 with a summary line on stdout, 1 with the reason on stderr.
pub(crate) fn run_pack(args: &PackArgs, stdout: &mut dyn Write, stderr: &mut dyn Write) -> u8 {
    let packed = pack(&args.source, &args.destination, &PackOptions::default())
        .map_err(|error| Refusal::new("pack_failed", Exit::Broken, error.to_string()))
        .and_then(|report| {
            write_report(&report, stdout, stderr).map_err(|error| Refusal::new("pack_report_failed", Exit::Broken, error.to_string()))
        });
    match packed {
        Ok(()) => Exit::Ok.code(),
        Err(refused) => report(stderr, "pack", &refused),
    }
}

/// The controller's runtime root, as the controller resolves it from this process's environment: where its
/// per-iteration and per-scenario zips, its run journals and the detached viewer's files live. A refusal keeps the
/// controller's code, and a usage refusal of the controller is one of `air-trace` too.
pub(crate) fn vm_runtime_root() -> Result<PathBuf, Refusal> {
    avl_base::Config::load(
        avl_base::Selection::DEFAULT,
        &avl_base::Environment::from_os(),
        std::path::Path::new(""),
    )
    .map(|config| config.runtime_root)
    .map_err(|refused| {
        let exit = if refused.exit == avl_base::Exit::USAGE {
            Exit::Usage
        } else {
            Exit::Broken
        };
        Refusal::new(refused.code, exit, refused.message)
    })
}
