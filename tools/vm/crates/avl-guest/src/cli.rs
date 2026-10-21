//! The verbs as clap types, and the dispatch from a parsed verb to the code that answers it.
//!
//! Every verb declares exactly the arguments it reads, so a flag another verb owns is refused rather than
//! ignored: `--tail` belongs to `log` alone and `--grace-ms` to `cancel` alone, and either one accepted elsewhere
//! would read as configured while doing nothing. Every refusal of an argv is the `usage` envelope with exit 64,
//! which the host reads as "this agent is older than its controller".

use std::ffi::OsString;
use std::path::{Path, PathBuf};

use avl_wire::supervisor::{is_run_id, is_snapshot_id};
use avl_wire::verb::AgentVerb;
use clap::error::ErrorKind;
use clap::{Args, ColorChoice, Parser, Subcommand};

use crate::reply::AgentRefusalExt;
use crate::reply::{self, AgentRefusal, Streams, answer_bare, answer_enveloped};
use crate::step::SystemRunner;
use crate::{ide, image, lane, linux, read_file, relay, runfiles, shape, supervisor, tracepack};

#[cfg(test)]
mod tests;

#[derive(Parser, Debug)]
#[command(
    name = "vm-guest-agent",
    about = "The Air UI-test guest agent: everything the host controller needs inside a worker VM.",
    color = ColorChoice::Never,
    disable_version_flag = true,
    disable_help_subcommand = true,
    after_help = AFTER_HELP
)]
pub(crate) struct Agent {
    #[command(subcommand)]
    pub verb: Verb,
}

// One line per paragraph: clap wraps to the terminal.
const AFTER_HELP: &str = "validate-guest takes the X display as its one positional argument, and it is required.

trace-pack-ready zips only the finished bundles that LEDGER does not name yet, or with --all every bundle that it \
does not name, and adds them to LEDGER. When nothing is new it writes no zip.

relay copies bytes both ways between its standard streams and 127.0.0.1:PORT until the guest side closes. It \
writes nothing on stdout that the guest side did not send.

read-file copies PATH to standard output unchanged. Then it writes its envelope on standard error, which names the \
size and the SHA-256 of what it wrote.

runfiles-tree reads a JSON request on standard input: the MANIFEST text, the host-to-guest path table and a \
destination directory. It builds one link per MANIFEST line under DESTINATION/DIGEST, copies a package directory of a \
node_modules store, and answers that root.

ide-gc cancels every live IDE under ROOT with --stop-all, or every one whose launch record names another product with \
--keep-product. It keeps the newest --keep-logs log directories of each context.

lane-daemon serves the control channel of the lane daemon on --bind:--port until a POST /shutdown. It reads the lane IDE \
inputs from the runtime descriptor, publishes STATE_DIR/daemon.json with its token, and owns the lane services and the \
lane IDE. It writes its log on standard error.

lane-run runs one iteration of --lane from the runfiles of --descriptor, with no control channel. It writes the run \
stream on standard output and into RUNTIME_ROOT/run.ndjson, quits the IDE, and exits 0 when the run is green. With \
--plan-only it stages the inputs, writes the plan of the run as one JSON document on standard output, and launches no \
IDE. --jvm_flag takes the -D flags of a TeamCity UI job: its runner index and count split the lane between the runners, \
and -Dair.flow.trace.allure=on asks the recorder for the Allure results.

cancel --thread-dump JCMD runs JCMD PID Thread.print before the TERM, and writes the dump into the log directory of an \
IDE launch or into the run directory.

The internal supervise verb is not a public interface.";

#[derive(Subcommand, Debug)]
pub(crate) enum Verb {
    /// Start a supervised run and wait until its child is running.
    Start(StartArgs),
    /// The detached supervisor `start` spawns.
    #[command(hide = true)]
    Supervise(RunArgs),
    /// Answer a run's state, reconciled with the processes that are actually there.
    Status(RunArgs),
    /// Answer the run that owns the slot, or that it is free.
    Active(RootArgs),
    /// Answer the tail of a run's log.
    Log(LogArgs),
    /// Cancel a run: TERM its process group, then KILL it after the grace period.
    Cancel(CancelArgs),
    /// Turn a freshly cloned macOS base into the golden image.
    ProvisionImage(ImagePins),
    /// Prove a finished macOS golden image.
    ValidateImage(ImagePins),
    /// Prove a Linux worker: the C library, the display and the window manager.
    ValidateGuest(ValidateGuestArgs),
    /// Zip the trace bundles no earlier call packed.
    TracePackReady(TracePackReadyArgs),
    /// Answer this binary's own wire and the digest of its bytes.
    Contract,
    /// Bridge standard input and output to a guest loopback port.
    Relay(RelayArgs),
    /// Build the runfiles tree of a host MANIFEST; the request is read from standard input.
    RunfilesTree,
    /// Copy one file to standard output unchanged, and name it on standard error.
    ReadFile(ReadFileArgs),
    /// Cancel or keep the live IDEs under an IDE root, and remove their old log directories.
    IdeGc(IdeGcArgs),
    /// Serve the control channel of the lane daemon until its shutdown.
    LaneDaemon(LaneDaemonArgs),
    /// Run one iteration of a lane from a runfiles tree, with no control channel.
    LaneRun(LaneRunArgs),
}

impl Verb {
    /// The wire verb this is: what the command line and every envelope spell.
    pub(crate) const fn name(&self) -> AgentVerb {
        match self {
            Self::Start(_) => AgentVerb::Start,
            Self::Supervise(_) => AgentVerb::Supervise,
            Self::Status(_) => AgentVerb::Status,
            Self::Active(_) => AgentVerb::Active,
            Self::Log(_) => AgentVerb::Log,
            Self::Cancel(_) => AgentVerb::Cancel,
            Self::ProvisionImage(_) => AgentVerb::ProvisionImage,
            Self::ValidateImage(_) => AgentVerb::ValidateImage,
            Self::ValidateGuest(_) => AgentVerb::ValidateGuest,
            Self::TracePackReady(_) => AgentVerb::TracePackReady,
            Self::Contract => AgentVerb::Contract,
            Self::Relay(_) => AgentVerb::Relay,
            Self::RunfilesTree => AgentVerb::RunfilesTree,
            Self::ReadFile(_) => AgentVerb::ReadFile,
            Self::IdeGc(_) => AgentVerb::IdeGc,
            Self::LaneDaemon(_) => AgentVerb::LaneDaemon,
            Self::LaneRun(_) => AgentVerb::LaneRun,
        }
    }
}

// --- the run supervisor ------------------------------------------------------------------------------------

#[derive(Args, Debug, Clone)]
pub(crate) struct RootArgs {
    /// The directory holding one run slot and its runs.
    #[arg(long, value_name = "DIR", value_parser = non_empty_path)]
    pub root: PathBuf,
}

#[derive(Args, Debug, Clone)]
pub(crate) struct RunArgs {
    #[command(flatten)]
    pub root: RootArgs,
    #[arg(long = "run", value_name = "RUN_ID", value_parser = run_id)]
    pub run_id: String,
}

#[derive(Args, Debug, Clone)]
pub(crate) struct StartArgs {
    #[command(flatten)]
    pub run: RunArgs,
    /// The child's working directory.
    #[arg(long, value_name = "DIR", value_parser = non_empty_path)]
    pub cwd: PathBuf,
    /// The source snapshot the run was built from; a host-built run has none.
    #[arg(long = "snapshot", value_name = "SNAPSHOT_ID", value_parser = snapshot_id)]
    pub snapshot_id: Option<String>,
    /// The child's argv, after `--`.
    #[arg(last = true, required = true, value_name = "COMMAND")]
    pub argv: Vec<String>,
}

#[derive(Args, Debug, Clone)]
pub(crate) struct LogArgs {
    #[command(flatten)]
    pub run: RunArgs,
    /// Answer only the last LINES complete lines.
    #[arg(long, value_name = "LINES", value_parser = clap::value_parser!(u32).range(1..=10_000))]
    pub tail: Option<u32>,
}

#[derive(Args, Debug, Clone)]
pub(crate) struct CancelArgs {
    #[command(flatten)]
    pub run: RunArgs,
    /// How long the process group has between TERM and KILL.
    #[arg(long, value_name = "MILLIS", default_value_t = 10_000,
          value_parser = clap::value_parser!(u64).range(0..=60_000))]
    pub grace_ms: u64,
    /// Run `JCMD <pid> Thread.print` before the TERM, when the child is still alive.
    #[arg(long, value_name = "JCMD", value_parser = absolute_path)]
    pub thread_dump: Option<PathBuf>,
}

// --- the lane IDE -------------------------------------------------------------------------------------------

#[derive(Args, Debug, Clone)]
pub(crate) struct IdeGcArgs {
    /// The IDE root: one context directory per launch key.
    #[arg(long, value_name = "DIR", value_parser = absolute_path)]
    pub root: PathBuf,
    /// Cancel every live IDE.
    #[arg(long, conflicts_with = "keep_product")]
    pub stop_all: bool,
    /// Cancel every live IDE whose launch record does not name DIGEST, and keep the others.
    #[arg(long, value_name = "DIGEST", value_parser = non_empty_string)]
    pub keep_product: Option<String>,
    /// How many log directories of each context stay.
    #[arg(long, value_name = "COUNT", default_value_t = 5,
          value_parser = clap::value_parser!(u32).range(1..=1_000))]
    pub keep_logs: u32,
    /// How long the process group of a canceled IDE has between TERM and KILL.
    #[arg(long, value_name = "MILLIS", default_value_t = 10_000,
          value_parser = clap::value_parser!(u64).range(0..=60_000))]
    pub grace_ms: u64,
}

// --- the lane daemon ----------------------------------------------------------------------------------------

#[derive(Args, Debug, Clone)]
pub(crate) struct LaneDaemonArgs {
    /// The state directory: the state file, the jar store and the iterations.
    #[arg(long, value_name = "DIR", value_parser = absolute_path)]
    pub state_dir: PathBuf,
    /// The port of the control channel.
    #[arg(long, value_name = "PORT", value_parser = clap::value_parser!(u16).range(1..))]
    pub port: u16,
    /// The address of the control channel. A container-linux guest binds every interface.
    #[arg(long, value_name = "ADDRESS", default_value = "127.0.0.1")]
    pub bind: std::net::IpAddr,
    /// The launch digest that the controller prepared the daemon for. A run of another one is refused.
    #[arg(long, value_name = "DIGEST", value_parser = non_empty_string)]
    pub launch_digest: String,
    /// The runtime descriptor in the guest runfiles tree.
    #[arg(long, value_name = "FILE", value_parser = absolute_path)]
    pub descriptor: PathBuf,
    /// The root of the guest runfiles tree, which every runfile of the descriptor is below.
    #[arg(long, value_name = "DIR", value_parser = absolute_path)]
    pub runfiles_root: PathBuf,
    /// The parent of every context of the lane IDE.
    #[arg(long, value_name = "DIR", value_parser = absolute_path)]
    pub ide_root: PathBuf,
    /// The empty home that the IDE gets as idea.dev.project.root. The controller passes a directory in the state directory.
    #[arg(long, value_name = "DIR", value_parser = absolute_path)]
    pub home: PathBuf,
}

#[derive(Args, Debug, Clone)]
pub(crate) struct LaneRunArgs {
    /// The lane of the run, such as ui: the lane document lanes/<lane>.json.
    #[arg(long, value_name = "LANE", value_parser = non_empty_string)]
    pub lane: String,
    /// The runtime descriptor in the runfiles tree.
    #[arg(long, value_name = "FILE", value_parser = absolute_path)]
    pub descriptor: PathBuf,
    /// The root of the runfiles tree. The default is the root that holds the descriptor.
    #[arg(long, value_name = "DIR", value_parser = absolute_path)]
    pub runfiles_root: Option<PathBuf>,
    /// The directory of the run: the jar store, the IDE contexts, the home and the run stream. The default is
    /// $AIR_LANE_RUNTIME_ROOT, and then $TEST_TMPDIR/air-lane.
    #[arg(long, value_name = "DIR", value_parser = absolute_path)]
    pub runtime_root: Option<PathBuf>,
    /// A test class, with or without a package, and an optional #<scenario>. Repeat it to name more. A Bazel
    /// --test_filter, $TESTBRIDGE_TEST_ONLY, adds its comma-separated selectors.
    #[arg(long = "selector", value_name = "CLASS[#SCENARIO]")]
    pub selectors: Vec<String>,
    /// A JUnit5 filter <option>=<value>. Repeat it to name more. $JB_TEST_JUNIT5_FILTERS adds its filters, separated by
    /// ';'.
    #[arg(long = "junit5-filter", value_name = "FILTER")]
    pub junit5_filters: Vec<String>,
    /// The budget of one scenario, in seconds.
    #[arg(long, value_name = "SECONDS", default_value_t = 1_800)]
    pub active_execution_timeout_sec: u64,
    /// The budget between two records of the run, in seconds.
    #[arg(long, value_name = "SECONDS", default_value_t = 300)]
    pub progress_gap_timeout_sec: u64,
    /// The budget of the whole run, in seconds. lane-run takes the smaller of it and $TEST_TIMEOUT of a Bazel test, less
    /// a teardown reserve of 180 s. At the end of the budget the watchdog ends the run. A minute later lane-run writes
    /// the JUnit XML of the records so far itself, cancels the lane IDE and exits 1. On a TeamCity agent both ends also
    /// write a buildProblem on standard output, so a muted case cannot leave the build green.
    #[arg(long, value_name = "SECONDS")]
    pub run_budget_sec: Option<u64>,
    /// Stage the inputs, select the suite documents, write the plan of the run on standard output, and launch no IDE.
    #[arg(long)]
    pub plan_only: bool,
    /// A JVM flag of the JUnit runner, -D<name>=<value>, as the UI job of TeamCity passes it with
    /// --test_arg=--jvm_flag=<flag>. Repeat it to name more. The values of idea.test.runner.index and
    /// idea.test.runners.count split the selected suites of the lane between the runners of the job.
    /// -Dair.flow.trace.allure=on asks the recorder for the Allure results. java.awt.headless, idea.bucketing.season
    /// and idea.tests.duration.bucketing.enabled are ignored, each with a note in the run stream. Another flag is refused
    /// by name.
    #[arg(long = "jvm_flag", value_name = "FLAG", allow_hyphen_values = true)]
    pub jvm_flags: Vec<String>,
}

// --- the image and Linux worker verbs -----------------------------------------------------------------------

/// The three pins the golden image is built to, every one of them required, in any order. Named because the
/// Packer template passes them by name (`air-macos.pkr.hcl`); a repeated flag is refused rather than last-wins.
#[derive(Args, Debug, Clone, PartialEq, Eq)]
pub(crate) struct ImagePins {
    #[arg(long, value_name = "V", value_parser = non_empty_string)]
    pub macos_version: String,
    #[arg(long, value_name = "N", value_parser = clap::value_parser!(u32).range(1..))]
    pub node_major: u32,
    #[arg(long, value_name = "V", value_parser = non_empty_string)]
    pub junie_version: String,
}

#[derive(Args, Debug, Clone)]
pub(crate) struct ValidateGuestArgs {
    #[arg(value_parser = display)]
    pub display: String,
}

// --- the trace verb -----------------------------------------------------------------------------------------

#[derive(Args, Debug, Clone)]
pub(crate) struct TracePackReadyArgs {
    /// The trace directory, an absolute guest path.
    #[arg(value_parser = absolute_path)]
    pub source: PathBuf,
    /// The zip to write, an absolute guest path.
    #[arg(value_parser = absolute_path)]
    pub destination: PathBuf,
    /// The guest file naming every bundle already packed, one per line.
    #[arg(value_parser = absolute_path)]
    pub ledger: PathBuf,
    /// Pack unfinished bundles too: the last call of an iteration.
    #[arg(long)]
    pub all: bool,
}

// --- the relay --------------------------------------------------------------------------------------------

#[derive(Args, Debug, Clone)]
pub(crate) struct RelayArgs {
    /// The guest loopback port to connect to.
    pub port: u16,
}

// --- the pull ----------------------------------------------------------------------------------------------

#[derive(Args, Debug, Clone)]
pub(crate) struct ReadFileArgs {
    /// The guest file to copy.
    #[arg(value_name = "PATH", value_parser = non_empty_path)]
    pub path: PathBuf,
}

// --- value parsers ------------------------------------------------------------------------------------------

fn non_empty_string(value: &str) -> Result<String, String> {
    if value.is_empty() {
        return Err("an empty value is not accepted".to_owned());
    }
    Ok(value.to_owned())
}

fn non_empty_path(value: &str) -> Result<PathBuf, String> {
    non_empty_string(value).map(PathBuf::from)
}

/// An absolute guest path: the verbs run with whatever working directory the exec channel gives them.
fn absolute_path(value: &str) -> Result<PathBuf, String> {
    if !Path::new(value).is_absolute() {
        return Err(format!("expected an absolute path, not {}", reply::quoted(value)));
    }
    Ok(PathBuf::from(value))
}

fn run_id(value: &str) -> Result<String, String> {
    if !is_run_id(value) {
        return Err(format!("{} is not a valid run id", reply::quoted(value)));
    }
    Ok(value.to_owned())
}

fn snapshot_id(value: &str) -> Result<String, String> {
    if !is_snapshot_id(value) {
        return Err(format!("{} is not a valid snapshot id", reply::quoted(value)));
    }
    Ok(value.to_owned())
}

/// A display like `:88`, anchored at both ends: `:88x` makes Xvfb refuse the name only after a 60 s wait.
fn display(value: &str) -> Result<String, String> {
    if !value.strip_prefix(':').is_some_and(shape::is_digits) {
        return Err(format!("expected a display like :88, not {}", reply::quoted(value)));
    }
    Ok(value.to_owned())
}

// --- parsing and dispatch -----------------------------------------------------------------------------------

pub(crate) fn parse(args: &[OsString]) -> Result<Agent, clap::Error> {
    Agent::try_parse_from(args)
}

/// Answers an argv clap refused: help on stdout with exit 0 when help was asked for, and the `usage` envelope
/// with exit 64 otherwise. The envelope echoes the verb the argv named, when it named one.
pub(crate) fn answer_parse_error(error: &clap::Error, args: &[OsString], streams: &mut Streams<'_>) -> u8 {
    if matches!(error.kind(), ErrorKind::DisplayHelp | ErrorKind::DisplayVersion) {
        let _ = streams.stdout.write_all(error.render().to_string().as_bytes());
        return 0;
    }
    let verb = args
        .get(1)
        .and_then(|word| word.to_str())
        .filter(|word| !word.starts_with('-'))
        .unwrap_or_default();
    let message = error.render().to_string();
    reply::fail(streams, verb, AgentRefusal::usage(message.trim_end()))
}

pub(crate) fn dispatch(verb: Verb, streams: &mut Streams<'_>) -> u8 {
    let name = verb.name();
    let system = supervisor::LiveSystem;
    match verb {
        Verb::Start(args) => {
            let launcher = supervisor::Launcher::current();
            answer_enveloped(streams, name, supervisor::start(&system, &launcher, &args))
        }
        Verb::Supervise(args) => match supervisor::supervise(&system, &args.root.root, &args.run_id) {
            Ok(()) => 0,
            Err(refusal) => reply::fail(streams, name.as_str(), refusal),
        },
        Verb::Status(args) => answer_enveloped(streams, name, supervisor::status(&system, &args.root.root, &args.run_id)),
        Verb::Active(args) => answer_enveloped(streams, name, supervisor::active(&system, &args)),
        Verb::Log(args) => answer_enveloped(streams, name, supervisor::log(&system, &args)),
        Verb::Cancel(args) => answer_enveloped(streams, name, supervisor::cancel(&system, &args)),
        Verb::ProvisionImage(pins) => {
            let mut provisioner = image::provision::ImageProvisioner::new(&pins, SystemRunner);
            let result = provisioner.provision();
            answer_enveloped(streams, name, result)
        }
        Verb::ValidateImage(pins) => {
            let surface = image::validate::LiveImage::new(image::validate::search_path(pins.node_major));
            let result = image::validate::ImageValidator::new(&pins, surface).validate();
            answer_enveloped(streams, name, result)
        }
        Verb::ValidateGuest(args) => {
            let result = linux::validate::GuestValidator::new(&args, SystemRunner).validate();
            answer_enveloped(streams, name, result)
        }
        Verb::TracePackReady(args) => answer_enveloped(streams, name, tracepack::pack_ready(&args)),
        Verb::Contract => supervisor::contract(streams),
        // The input moves to a thread of its own, and a borrowed stream cannot move. So the relay reads the
        // process's own standard input.
        Verb::Relay(args) => relay::relay(args.port, std::io::stdin(), streams),
        Verb::RunfilesTree => {
            let result = runfiles::build_from(streams.stdin);
            answer_enveloped(streams, name, result)
        }
        Verb::ReadFile(args) => read_file::read_file(&args.path, streams),
        Verb::IdeGc(args) => answer_bare(streams, name, ide::gc::collect(&system, &args)),
        Verb::LaneDaemon(args) => match lane::channel::serve(&args, &|name| std::env::var(name).ok()) {
            Ok(exit) => exit,
            Err(refusal) => reply::fail(streams, name.as_str(), refusal),
        },
        Verb::LaneRun(args) => match lane::run::run(&args, &|name| std::env::var(name).ok()) {
            Ok(exit) => exit,
            Err(refusal) => reply::fail(streams, name.as_str(), refusal),
        },
    }
}
