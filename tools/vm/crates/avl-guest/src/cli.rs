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
use crate::{ide, image, linux, read_file, relay, runfiles, shape, stage, supervisor, tracepack};

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
const AFTER_HELP: &str = "validate-guest takes its arguments positionally, and every one of them is required.

trace-pack-ready zips only the finished bundles that LEDGER does not name yet, or with --all every bundle that it \
does not name, and adds them to LEDGER. When nothing is new it writes no zip.

relay copies bytes both ways between its standard streams and 127.0.0.1:PORT until the guest side closes. It \
writes nothing on stdout that the guest side did not send.

stage reads its manifest on standard input, or from the file MANIFEST names. It refuses a manifest in both places.

read-file copies PATH to standard output unchanged. Then it writes its envelope on standard error, which names the \
size and the SHA-256 of what it wrote.

runfiles-tree reads a JSON request on standard input: the MANIFEST text, the host-to-guest path table and a \
destination directory. It builds one link per MANIFEST line under DESTINATION/DIGEST, copies a package directory of a \
node_modules store, and answers that root.

ide-prepare reads the IDE context document on standard input and lays out the context IDE_ROOT/LAUNCH_KEY: the data \
directories, the project, the home and the bin directory of the IDE. The caller then seeds the home, the bin directory \
and the config files.

ide-launch reads the IDE launch document on standard input. It composes the argument file of the IDE and starts the \
run run-ide-LAUNCH_NAME on the context. The IDE gets HOME and PATH from the context and a fixed list of variables from \
the agent, and nothing else.

ide-gc cancels every live IDE under ROOT with --stop-all, or every one whose launch record names another product with \
--keep-product. It keeps the newest --keep-logs log directories of each context.

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
    /// Stage the daemon's runtime generation.
    Stage(StageArgs),
    /// Answer whether a staged runtime generation can be reused as it is.
    StageCheck(StageCheckArgs),
    /// Prepare one daemon launch; the request is read from standard input.
    LaunchPrep(LaunchPrepArgs),
    /// Remove staged generations, keeping the two newest and the requested ones.
    Gc(GcArgs),
    /// Turn a freshly cloned macOS base into the golden image.
    ProvisionImage(ImagePins),
    /// Prove a finished macOS golden image.
    ValidateImage(ImagePins),
    /// Prove a Linux worker: the display, the window manager and the shared objects of the runtime.
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
    /// Lay out one context of the lane IDE; the context document is read from standard input.
    IdePrepare,
    /// Start the lane IDE on a prepared context; the launch document is read from standard input.
    IdeLaunch,
    /// Cancel or keep the live IDEs under an IDE root, and remove their old log directories.
    IdeGc(IdeGcArgs),
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
            Self::Stage(_) => AgentVerb::Stage,
            Self::StageCheck(_) => AgentVerb::StageCheck,
            Self::LaunchPrep(_) => AgentVerb::LaunchPrep,
            Self::Gc(_) => AgentVerb::Gc,
            Self::ProvisionImage(_) => AgentVerb::ProvisionImage,
            Self::ValidateImage(_) => AgentVerb::ValidateImage,
            Self::ValidateGuest(_) => AgentVerb::ValidateGuest,
            Self::TracePackReady(_) => AgentVerb::TracePackReady,
            Self::Contract => AgentVerb::Contract,
            Self::Relay(_) => AgentVerb::Relay,
            Self::RunfilesTree => AgentVerb::RunfilesTree,
            Self::ReadFile(_) => AgentVerb::ReadFile,
            Self::IdePrepare => AgentVerb::IdePrepare,
            Self::IdeLaunch => AgentVerb::IdeLaunch,
            Self::IdeGc(_) => AgentVerb::IdeGc,
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

// --- the stager ---------------------------------------------------------------------------------------------
//
// The runtime root and every value after it are checked inside the verb rather than here, so a malformed one
// is the verb's own refusal (`guest_stage_failed`, exit 70), as it always was.

#[derive(Args, Debug, Clone)]
pub(crate) struct StageArgs {
    pub runtime_root: String,
    /// A manifest file. Without it, the manifest is read from standard input.
    #[arg(value_name = "MANIFEST")]
    pub manifest: Option<PathBuf>,
}

#[derive(Args, Debug, Clone)]
pub(crate) struct StageCheckArgs {
    pub runtime_root: String,
    pub digest: String,
    pub stable_count: String,
    pub java_home_suffix: String,
    pub classpath_sha256: String,
}

#[derive(Args, Debug, Clone)]
pub(crate) struct LaunchPrepArgs {
    pub runtime_root: String,
}

#[derive(Args, Debug, Clone)]
pub(crate) struct GcArgs {
    pub root: String,
    /// Generations to keep whatever their age.
    pub keep: Vec<String>,
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
    #[arg(value_parser = absolute_path)]
    pub runtime_root: PathBuf,
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
        Verb::Stage(args) => {
            let result = stage::runtime::stage(&args.runtime_root, args.manifest.as_deref(), streams.stdin);
            answer_bare(streams, name, result)
        }
        Verb::StageCheck(args) => answer_bare(streams, name, stage::runtime::check(&args)),
        Verb::LaunchPrep(args) => {
            let result = stage::launch_prep::read_request(streams.stdin)
                .and_then(|request| stage::launch_prep::prepare(&args.runtime_root, &request));
            answer_bare(streams, name, result)
        }
        Verb::Gc(args) => answer_bare(streams, name, stage::gc::collect(&args.root, &args.keep)),
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
        Verb::IdePrepare => {
            let result = ide::prepare::read_document(streams.stdin).and_then(|document| ide::prepare::prepare(&system, &document));
            answer_bare(streams, name, result)
        }
        Verb::IdeLaunch => {
            let launcher = supervisor::Launcher::current();
            let guest = ide::launch::Guest::current();
            let result =
                ide::launch::read_document(streams.stdin).and_then(|document| ide::launch::launch(&system, &launcher, &guest, &document));
            answer_bare(streams, name, result)
        }
        Verb::IdeGc(args) => answer_bare(streams, name, ide::gc::collect(&system, &args)),
    }
}
