//! The command line: the clap tree, which renders the help, and how a parse becomes an invocation or a refusal.
//!
//! Three things about it are worth knowing before editing it.
//!
//! # A parse failure answers in the form the caller asked for
//!
//! A refusal from the parse itself - an argument clap cannot place, an option with no value - has to be reported
//! in the form the caller asked for, and the parse that would have discovered the form is the one that just
//! failed. So clap reads a failed command line a second time, with `ignore_errors` ([`read_partially`]): it keeps
//! what it parsed, and an argument it cannot place is left out of that reading, so the reading reaches a later
//! `--text` and names the command. Without it, `vm status --no-such-option --text` would answer a JSON envelope to
//! a caller reading prose. clap's grammar decides what a value is: a token that it reads as an option is not the
//! value of the option before it. The options clap cannot refuse by shape (`--backend`, `--lease-file`) are read as
//! plain values and refused after a successful parse, when the form is known.
//!
//! # The value of a command option stays that value
//!
//! Global options are accepted before or after the command and anywhere before a literal `--`, but a value is
//! never one: in `lease acquire --holder --text`, `--text` is the holder. The tokens after `--` belong to the
//! command (`exec` and `peekaboo` pass them to the guest), so nothing reads them.
//!
//! # PROGRAM is a literal path
//!
//! The help text names [`PROGRAM`], the wrapper's repository-relative path, everywhere a command is spelled. The
//! usage text is documentation an agent copies verbatim into its next command, and the repository's
//! tool-permission rules match literal argv prefixes (`community/.ai/tool-permissions.json`), so only this spelling
//! runs without a prompt. The prose *error* prefix stays `vm:`, the reporter's program name, because that prefix
//! says which tool spoke.

use std::ffi::OsString;
use std::path::PathBuf;
use std::str::FromStr;

use crate::bench::BenchVerb;
use crate::daemon::flake::{DEFAULT_FLAKE_RESET, FlakeArgs, flake_reset_names};
use crate::daemon::shard::{MAX_USEFUL_SHARDS, ShardArgs};
use crate::daemon::{DaemonVerb, Junit5FilterKind, RunCommandArgs};
use crate::lane::{LsArgs, PeekabooArgs};
use crate::worker::lease::{AcquireRequest, LeaseCommand, parse_count};
use crate::worker::worker::{PoolCommand, PoolTarget};
use avl_base::{Environment, Exit, Refusal, Selection};
use clap::error::{ContextKind, ContextValue, ErrorKind};
use clap::{CommandFactory, FromArgMatches, Parser, Subcommand};

use crate::image::ImageAction;
use crate::terminal::{Form, Output, TerminalFacts};
use avl_base::RefusalExt;

#[cfg(test)]
mod tests;

/// How the help text spells this controller: the wrapper, by its repository-relative path.
///
/// Not `argv[0]`, which under Bazel is a path inside an output tree, and not `vm`, which is not a command anyone
/// can run.
pub(crate) const PROGRAM: &str = "./community/tools/vm.cmd";

/// The paragraphs under the help of `vm`: the pools, the global options, and the form of the output.
const AFTER_HELP: &str = "--backend names a pool: docker (the default) is the Ubuntu guest in a container, with its own \
air-docker-N slots and an image the controller builds; on macOS its engine is a Lima VM the controller starts, and \
elsewhere it is the engine DOCKER_HOST names or the host's own. tart is a sealed macOS guest with its own \
air-macos-N slots, and parallels is the single pre-existing macOS VM. A command without --backend, such as \
pool stop, acts on the docker pool. A lease receipt records the selection, so later receipt-bearing commands \
infer it.

Global options, accepted before or after the command and anywhere before a literal --: --json and --text choose the \
output form, --stream adds NDJSON progress, --backend selects the pool, and --lease-file passes the receipt. So \
\"run --stream flow-x\" and \"--stream run flow-x\" are the same command. The value of a command option stays that \
value: in \"lease acquire --holder --text\", --text is the holder. --help (-h), before the command, prints this text \
- as JSON data under --json, as prose under --text; after the command it prints that command's own help the same \
way.

The form follows the reader: text when stdout is a terminal, JSON when it is not. --json and --text choose it. In \
JSON, success writes one envelope to stdout and failure writes one to stderr; --stream additionally emits NDJSON \
progress on stderr without changing the final envelope. In text, progress is prose on stderr, with a status footer \
redrawn in place when stderr is a terminal too. Acquisition returns a mode-0600 receipt path; the receipt is the \
only accepted worker handle.";

/// The help of `vm`, which a command line that names no command is refused with.
pub(crate) fn help() -> String {
    Cli::command().render_help().to_string().trim_end().to_owned()
}

/// The long help of `run`, with the lists it does not own interpolated live: the lane names and the JUnit5 filter
/// options each have one owner, and each appears in a refusal message as well as here.
fn run_long_about() -> String {
    let lanes = avl_affected::ui_lane_names().join("|");
    let explicit = avl_affected::explicit_only_lane_names().join(", ");
    let filters = Junit5FilterKind::names();
    format!(
        "Builds the target on the host and runs one selection in the guest's persistent daemon against its warm IDE. \
Test-code changes are pushed over HTTP and need no remount, and the IDE relaunches only when product inputs change.

The selection is a flow id, a suite id, --changed PATH..., a TestClass or FQN[#method], or --lane {lanes}. A flow id \
selects every generated suite whose scenarios tell or walk the flow, and a suite id selects that one suite, as one \
lane's tag and one class filter per suite. An unknown id names the nearest ids, and a flow no generated suite tests \
is refused, because an iteration that runs nothing is not a passing run. With --changed, every argument that is not \
an option is a changed path, so \"--changed A B\" and \"--changed A --changed B\" are the same run, and a change no \
generated suite covers is refused, naming each path.

For a flow, a suite or --changed without --lane: one iteration per reached lane, in the order {lanes}, on one worker \
of the selected backend, in one reply. More than one lane answers {{worker, lanes: [{{lane, exitCode, code, message, \
run}}], notRun, hasFailures, lease}}. The exit code is the worst lane: 0 green, 6 red, and any other refusal above \
both. An iteration that leaves no report stops the run, and notRun names each lane it did not reach. --lane L runs \
only lane L. An explicit-only lane ({explicit}) runs only when --lane or its own class names it: no flow, suite or \
--changed reaches it, and shard and flake refuse it with explicit_only_lane, because each scenario spends a billed turn.

Without --lease-file, run builds on the host first, then leases one worker for the iteration and releases it on \
every exit; the reply adds lease {{holder, worker, disposition}}. An interrupt keeps the lease and names its receipt. \
With --lease-file it runs on that receipt's worker: the warm inner loop.

--filter narrows what the iteration runs, on top of whatever the lane or selector already asks for; it is \
repeatable, and OPTION is one of {filters}.

--test-env NAME=@- hands the guest test JVM a secret read from a pipe, as in \"central login export | vm.cmd run \
--lane ui-live --test-env AIR_LIVE_CENTRAL_LOGIN=@-\", so no host file exists. --test-env NAME=@FILE reads a host \
file instead. The guest gets a file, never a variable, because the daemon's environment is fixed at boot. The option \
is repeatable and run's alone, and @- may appear once; stdin on a terminal is refused. Each source is read once before \
any lease, and refused when it is missing, not a regular file, over 1 MiB, or holds fewer than 16 bytes. It travels \
on the exec channel's stdin to AIR_VM_RUN_SECRETS/NAME, a 0600 file on the guest's tmpfs, and is removed when the \
iteration ends or an interrupt abandons it. A fetched artifact that holds a value refuses the run with \
secret_in_artifact and is moved aside."
    )
}

/// The long help of `shard`, with the shard cap it does not own.
fn shard_long_about() -> String {
    let explicit = avl_affected::explicit_only_lane_names().join(", ");
    format!(
        "Leases up to N workers itself and splits the lane by measured class duration: shards 1..N-1 include their \
classes and shard N excludes theirs, so the union is the unsharded lane for any class list. With no measurement on \
disk it refuses rather than splitting by count, and N is capped at {MAX_USEFUL_SHARDS}. An explicit-only lane ({explicit}) \
is refused with explicit_only_lane. It builds on the host first, and an interrupt keeps the leases and names the receipts."
    )
}

/// The long help of `flake`, with the reset policies it does not own.
fn flake_long_about() -> String {
    let resets = flake_reset_names().join("|");
    let default_reset = DEFAULT_FLAKE_RESET.as_str();
    let explicit = avl_affected::explicit_only_lane_names().join(", ");
    format!(
        "K repeat trials of one selection folded into a per-class flake rate, never a sharded lane. --reset is one of \
{resets} (default --reset {default_reset}), and --workers is 1 by default. A selection of an explicit-only lane \
({explicit}) is refused with explicit_only_lane. A receipt is optional and supplies the first worker, and only the leases \
this run acquires are released. It builds on the host first, and an interrupt keeps the leases and names the receipts."
    )
}

// --- the tree ----------------------------------------------------------------------------------------------------

/// The whole command line.
#[derive(Parser, Debug)]
#[command(
    name = "vm",
    bin_name = PROGRAM,
    about = "The Air UI-lane controller: workers, leases, and runs of a UI lane in a worker's warm daemon.",
    after_help = AFTER_HELP,
    disable_help_subcommand = true,
    args_override_self = true
)]
pub(crate) struct Cli {
    #[command(flatten)]
    pub global: Global,
    #[command(subcommand)]
    pub command: Option<Cmd>,
}

/// The options every command accepts, before or after it.
///
/// `--backend` and `--lease-file` are read as plain strings and validated after the parse: a refusal from either
/// must be answered in the form the same command line chose, which only a successful parse knows.
#[derive(clap::Args, Debug, Default)]
pub(crate) struct Global {
    /// Answers one JSON envelope.
    #[arg(long, global = true, overrides_with = "text")]
    pub json: bool,
    /// Answers prose.
    #[arg(long, global = true, overrides_with = "json")]
    pub text: bool,
    /// Adds NDJSON progress on stderr.
    #[arg(long, global = true)]
    pub stream: bool,
    /// The pool: tart, parallels or docker. docker is the Ubuntu guest in a container.
    #[arg(long, global = true, value_name = "tart|parallels|docker")]
    pub backend: Option<String>,
    /// The mode-0600 lease receipt.
    #[arg(long = "lease-file", global = true, value_name = "FILE")]
    pub lease_file: Option<PathBuf>,
}

/// The commands.
#[derive(Subcommand, Debug)]
pub(crate) enum Cmd {
    /// Validates or builds the sealed macOS golden image.
    Image {
        #[arg(value_enum, default_value_t = ImageAction::Validate)]
        action: ImageAction,
    },
    /// Materializes, starts, stops, collects or recycles the pool.
    Pool {
        #[command(subcommand)]
        verb: PoolVerb,
    },
    /// The pool's workers and their state.
    Status,
    /// Acquires, shows or releases worker leases; a bare `lease` shows.
    ///
    /// `lease release` needs --lease-file. `lease show` is read-only; with a receipt it also reports that worker's
    /// holder.
    Lease {
        #[command(subcommand)]
        verb: Option<LeaseVerb>,
    },
    /// Which generated suites the changed paths reach; runs nothing.
    ///
    /// Read-only, and the one command that needs no worker and no lease: it answers which generated suites the named
    /// paths reach, with a subtotal per lane, and names every path it could not map with the reason. A UI lane caches
    /// nothing, so this is how a run is scoped to a change instead of re-running the whole lane.
    Suites {
        #[arg(value_name = "CHANGED_PATH")]
        paths: Vec<String>,
    },
    /// Builds on the host and runs one selection in a worker's warm daemon.
    #[command(long_about = run_long_about())]
    Run(RunCommandArgs),
    /// Splits one lane over several leased workers by measured class duration.
    #[command(long_about = shard_long_about())]
    Shard(ShardArgs),
    /// Repeats one selection K times and folds the trials into a per-class flake rate.
    #[command(long_about = flake_long_about())]
    Flake(FlakeArgs),
    /// Drives the guest daemon's lifecycle deliberately.
    ///
    /// Every verb but `warm` needs --lease-file. `daemon warm` builds and stamps the host half of the lane and takes
    /// no worker and no lease, so the next daemon start on any worker of this guest pays neither Bazel's analysis of
    /// the lane graph nor the build. One guest configuration at a time: a linux warm does not warm macOS, and warming
    /// the other guest evicts this one's analysis.
    Daemon {
        #[command(subcommand)]
        verb: DaemonVerb,
    },
    /// Runs one command in the leased worker; needs --lease-file.
    Exec {
        #[arg(last = true, value_name = "COMMAND")]
        command: Vec<String>,
    },
    /// Runs the guest's Peekaboo CLI as the logged-in user (Parallels only); needs --lease-file.
    Peekaboo(PeekabooArgs),
    /// Lists a guest directory; needs --lease-file.
    ///
    /// Read-only. Like pull it survives the daemon holding the supervisor's run slot, so a failing lane can be
    /// diagnosed without stopping the warm IDE. It takes the worker's operation lock, so use it between iterations, not
    /// during one.
    Ls(LsArgs),
    /// Copies one guest file into the worker's artifact directory; needs --lease-file.
    Pull {
        #[arg(value_name = "GUEST_FILE")]
        source: String,
        #[arg(value_name = "RELATIVE_ARTIFACT_PATH")]
        destination: String,
    },
    /// Where the worker's VNC endpoint is; needs --lease-file.
    Vnc,
    /// Measures the start-up of the IDE on this host, from a staged copy of its dev distribution.
    #[command(long_about = crate::bench::LONG_ABOUT, after_help = crate::bench::EXIT_CODES)]
    Bench {
        #[command(subcommand)]
        verb: BenchVerb,
    },
}

impl Cmd {
    /// The command's name, as the envelope's `command` field spells it.
    pub(crate) const fn name(&self) -> &'static str {
        match self {
            Self::Image { .. } => "image",
            Self::Pool { .. } => "pool",
            Self::Status => "status",
            Self::Lease { .. } => "lease",
            Self::Suites { .. } => "suites",
            Self::Run(_) => "run",
            Self::Shard(_) => "shard",
            Self::Flake(_) => "flake",
            Self::Daemon { .. } => "daemon",
            Self::Exec { .. } => "exec",
            Self::Peekaboo(_) => "peekaboo",
            Self::Ls(_) => "ls",
            Self::Pull { .. } => "pull",
            Self::Vnc => "vnc",
            Self::Bench { .. } => "bench",
        }
    }

    /// Whether the command is a run, with a journal and traces a person opens in the viewer.
    pub(crate) const fn is_run(&self) -> bool {
        matches!(self, Self::Run(_) | Self::Shard(_) | Self::Flake(_))
    }
}

/// The `pool` verbs.
#[derive(Subcommand, Debug)]
pub(crate) enum PoolVerb {
    /// Materializes the pool; a macOS pool may name the sealed golden it clones.
    ///
    /// The golden VM is the macOS pool's alone; naming one anywhere else is a usage error.
    Init {
        #[arg(value_name = "GOLDEN_VM")]
        golden: Option<String>,
    },
    /// Starts every worker, or one.
    Start {
        #[arg(value_name = "all|worker", default_value = "all", value_parser = pool_target)]
        target: PoolTarget,
    },
    /// Stops every worker, or one.
    Stop {
        #[arg(value_name = "all|worker", default_value = "all", value_parser = pool_target)]
        target: PoolTarget,
    },
    /// Deletes the clones behind idle, unleased slots.
    ///
    /// Parallels owns its own VM and has none. On docker it removes the stopped, unleased containers and keeps their
    /// volumes.
    Gc,
    /// Rebuilds workers from scratch; the target is never defaulted.
    ///
    /// The repair for a worker whose guest is unusable: stop, delete the clone, re-clone, start, provision. The target
    /// is never defaulted, because this deletes clones. A leased worker is refused when it is named, and reported
    /// unrecycled by "all", which recycles the rest and then refuses: 75 when a lease was the only thing in its way, 1
    /// when something is broken. On the macOS pool the sealed golden and the per-worker provenance still hold: a
    /// recycle is pool gc plus a re-clone, not a way around the image.
    Recycle {
        #[arg(value_name = "all|worker", value_parser = pool_target)]
        target: PoolTarget,
    },
}

fn pool_target(value: &str) -> Result<PoolTarget, Refusal> {
    PoolTarget::from_str(value)
}

impl From<PoolVerb> for PoolCommand {
    fn from(verb: PoolVerb) -> Self {
        match verb {
            PoolVerb::Init { golden } => Self::Init { golden },
            PoolVerb::Start { target } => Self::Start(target),
            PoolVerb::Stop { target } => Self::Stop(target),
            PoolVerb::Gc => Self::Gc,
            PoolVerb::Recycle { target } => Self::Recycle(target),
        }
    }
}

/// The `lease` verbs.
#[derive(Subcommand, Debug)]
pub(crate) enum LeaseVerb {
    /// Takes at most N workers, atomically, one receipt each.
    ///
    /// The holder defaults to <user>-lease-<uuid>; name one only to recover an interrupted acquisition, which answers
    /// the existing lease with a new receipt. --count is at most N: it takes min(N, free) workers, one receipt each,
    /// atomically; --exact requires all N.
    Acquire {
        /// A unique holder id; any value, even one spelled like an option. Absent, it is `<user>-lease-<uuid>`.
        #[arg(long, value_name = "UNIQUE_ID", allow_hyphen_values = true)]
        holder: Option<String>,
        /// At most this many workers.
        #[arg(long, value_name = "N", default_value = "1", value_parser = parse_count)]
        count: u8,
        /// Requires all N workers.
        #[arg(long)]
        exact: bool,
    },
    /// The pool's leases; with a receipt, that worker's holder too.
    Show,
    /// Releases the receipt's lease.
    Release,
}

impl LeaseVerb {
    /// The lease layer's request, validated by its own rules. An acquisition that names no holder takes
    /// [`avl_base::actor_id`] for `lease`, the shape a self-leased `run` uses, so nothing has to invent one.
    pub(crate) fn into_command(self, environment: &Environment) -> Result<LeaseCommand, Refusal> {
        Ok(match self {
            Self::Acquire { holder, count, exact } => LeaseCommand::Acquire(AcquireRequest::new(
                holder.unwrap_or_else(|| avl_base::actor_id(environment, "lease")),
                count,
                exact,
            )?),
            Self::Show => LeaseCommand::Show,
            Self::Release => LeaseCommand::Release,
        })
    }
}

// --- the parse ---------------------------------------------------------------------------------------------------

/// What one command line asks for, once it parsed and its global options were validated.
#[derive(Debug)]
pub(crate) struct Invocation {
    pub form: Form,
    pub stream: bool,
    /// `None` when no `--backend` was given, which is the fact the backend resolution needs: a flag that merely
    /// defaulted cannot be told apart from an absent one, and the precedence rule turns on exactly that.
    pub selection: Option<Selection>,
    pub lease_file: Option<PathBuf>,
    /// `None` when the command line named no command.
    pub command: Option<Cmd>,
}

/// The outcome of reading a command line.
#[derive(Debug)]
pub(crate) enum Parsed {
    Invocation(Invocation),
    /// `--help` before the command, or a command's own `--help`.
    Help {
        form: Form,
        text: String,
    },
    /// A refusal from the parse itself. `command` is `None` when the refusal came before a command was chosen.
    Refused {
        form: Form,
        command: Option<String>,
        refusal: Refusal,
    },
}

/// The options that are global, which a parse error about one of them reports with no command named.
const GLOBAL_OPTIONS: [&str; 6] = ["--json", "--text", "--stream", "--backend", "--lease-file", "--help"];

/// Reads a command line.
///
/// `--help` before the command is clap's own, answering the help of `vm`; after it, the command's help. Both arrive as
/// clap's `DisplayHelp` and are answered as data or prose like any other answer.
pub(crate) fn parse(argv: &[OsString], facts: &TerminalFacts) -> Parsed {
    let mut root = Cli::command();
    root.build();
    let matches = root
        .clone()
        .try_get_matches_from(std::iter::once(OsString::from("vm")).chain(argv.iter().cloned()));
    let cli = matches.and_then(|mut matches| Cli::from_arg_matches_mut(&mut matches));
    match cli {
        Ok(cli) => validate(cli, facts),
        Err(error) => refused_parse(&error, argv, &root, facts),
    }
}

/// Validates the global options a successful parse read as plain values, in the form that parse chose.
fn validate(cli: Cli, facts: &TerminalFacts) -> Parsed {
    let Cli { global, command } = cli;
    let requested = if global.json {
        Some(Output::Json)
    } else if global.text {
        Some(Output::Text)
    } else {
        None
    };
    let form = Form::of(requested, facts);
    let refused = |refusal: Refusal| Parsed::Refused {
        form,
        command: None,
        refusal,
    };
    let selection = match global.backend.as_deref().map(Selection::from_str) {
        None => None,
        Some(Ok(selection)) => Some(selection),
        Some(Err(refusal)) => return refused(refusal),
    };
    if global.lease_file.as_ref().is_some_and(|path| path.as_os_str().is_empty()) {
        return refused(lease_file_needs_a_value());
    }
    Parsed::Invocation(Invocation {
        form,
        stream: global.stream,
        selection,
        lease_file: global.lease_file,
        command,
    })
}

fn lease_file_needs_a_value() -> Refusal {
    Refusal::usage("--lease-file needs a value")
}

/// Turns a clap failure into the refusal the envelope reports, in the form the command line asked for.
fn refused_parse(error: &clap::Error, argv: &[OsString], root: &clap::Command, facts: &TerminalFacts) -> Parsed {
    let scan = read_partially(argv);
    let form = Form::of(scan.output, facts);
    if error.kind() == ErrorKind::DisplayHelp {
        return Parsed::Help {
            form,
            text: error.render().to_string().trim_end().to_owned(),
        };
    }
    let invalid_arg = context_string(error, ContextKind::InvalidArg);
    let names_global = invalid_arg
        .as_deref()
        .and_then(|arg| arg.split([' ', '=']).next())
        .is_some_and(|name| GLOBAL_OPTIONS.contains(&name));
    let command = if names_global { None } else { scan.command };
    // clap's message names the wrapper only when it carries a usage line; one that does not gets the command's.
    let clap_usage = |message: String| {
        let usage_line = command
            .as_deref()
            .and_then(|name| root.find_subcommand(name))
            .filter(|_| !message.contains("Usage:"))
            .map(|subcommand| subcommand.clone().render_usage().to_string());
        match usage_line {
            Some(line) => Refusal::usage(format!("{message}\n\n{line}")),
            None => Refusal::usage(message),
        }
    };
    let refusal = match error.kind() {
        ErrorKind::InvalidSubcommand => {
            let name = context_string(error, ContextKind::InvalidSubcommand).unwrap_or_default();
            // An unknown word after a known command (`pool status`) is that command's usage error: the command was
            // ours, only its verb was not.
            if let Some(parent) = command
                .as_deref()
                .filter(|parent| *parent != name && root.find_subcommand(parent).is_some())
            {
                return Parsed::Refused {
                    form,
                    command: Some(parent.to_owned()),
                    refusal: clap_usage(message_of(error)),
                };
            }
            let mut message = format!("unknown command {name:?}");
            if let Some(suggested) = context_string(error, ContextKind::SuggestedSubcommand) {
                message.push_str(&format!("; did you mean {suggested:?}?"));
            }
            return Parsed::Refused {
                form,
                command: Some(name),
                refusal: Refusal::new("unknown_command", Exit::USAGE, message),
            };
        }
        // A value parser that refuses with a code of its own (`--count`, a pool target) keeps it.
        ErrorKind::ValueValidation => std::error::Error::source(error)
            .and_then(|source| source.downcast_ref::<Refusal>())
            .cloned()
            .unwrap_or_else(|| clap_usage(message_of(error))),
        // An option named with nothing after it.
        ErrorKind::InvalidValue if context_string(error, ContextKind::InvalidValue).is_none_or(|v| v.is_empty()) => {
            match invalid_arg.as_deref().and_then(|arg| arg.split(' ').next()) {
                Some("--lease-file") => lease_file_needs_a_value(),
                Some(name) => Refusal::usage(format!("{name} needs a value")),
                None => clap_usage(message_of(error)),
            }
        }
        _ => clap_usage(message_of(error)),
    };
    Parsed::Refused { form, command, refusal }
}

fn context_string(error: &clap::Error, kind: ContextKind) -> Option<String> {
    match error.get(kind)? {
        ContextValue::String(value) => Some(value.clone()),
        ContextValue::Strings(values) => values.first().cloned(),
        _ => None,
    }
}

/// clap's own account of a failure, without its `error:` label and its pointer at `--help`: the usage line it
/// carries is kept, because it names the wrapper and the arguments the command takes.
fn message_of(error: &clap::Error) -> String {
    let rendered = error.render().to_string();
    let message = rendered.strip_prefix("error: ").unwrap_or(&rendered);
    let message = message
        .lines()
        .filter(|line| !line.starts_with("For more information"))
        .collect::<Vec<_>>()
        .join("\n");
    message.trim().to_owned()
}

/// What a failed parse can still tell: the form asked for and the command named.
#[derive(Debug, Default, PartialEq, Eq)]
pub(crate) struct PartialRead {
    /// The last of `--json` and `--text`, if either.
    pub output: Option<Output>,
    /// The command, when clap reached one.
    pub command: Option<String>,
}

/// clap's second reading of a command line whose parse failed, with `ignore_errors`.
///
/// clap keeps what it parsed, but it stops at an argument it cannot place, so an argument that the strict parse
/// names as unknown is left out and the line is read again. The grammar is clap's alone: a value stays a value, a
/// token that clap reads as an option is that option, and nothing after `--` is read.
pub(crate) fn read_partially(argv: &[OsString]) -> PartialRead {
    // Without the help flags, so that `--help` is one more argument that the reading leaves out, and not an answer.
    fn without_help(command: clap::Command) -> clap::Command {
        command.disable_help_flag(true).mut_subcommands(without_help)
    }
    // From the tree as declared: a built command has its help flags already.
    let strict = without_help(Cli::command());
    let lenient = strict.clone().ignore_errors(true);
    let line = |argv: &[OsString]| {
        std::iter::once(OsString::from("vm"))
            .chain(argv.iter().cloned())
            .collect::<Vec<_>>()
    };
    let mut argv = argv.to_vec();
    loop {
        let Ok(matches) = lenient.clone().try_get_matches_from(line(&argv)) else {
            return PartialRead::default();
        };
        let refused = match strict.clone().try_get_matches_from(line(&argv)) {
            Err(error) => refused_tokens(&error, &argv),
            Ok(_) => None,
        };
        let Some(refused) = refused else {
            return PartialRead {
                output: if matches.get_flag("json") {
                    Some(Output::Json)
                } else if matches.get_flag("text") {
                    Some(Output::Text)
                } else {
                    None
                },
                command: matches.subcommand_name().map(str::to_owned),
            };
        };
        argv.drain(refused);
    }
}

/// The tokens that a strict parse refused, which the second reading leaves out: an argument clap cannot place, or an
/// option whose value clap refused, with that value when it is a token of its own. A token after `--` is never one.
fn refused_tokens(error: &clap::Error, argv: &[OsString]) -> Option<std::ops::Range<usize>> {
    let takes_value = match error.kind() {
        ErrorKind::UnknownArgument => false,
        ErrorKind::InvalidValue | ErrorKind::ValueValidation => true,
        _ => return None,
    };
    let named = context_string(error, ContextKind::InvalidArg)?;
    let name = named.split([' ', '=']).next()?;
    let inline = format!("{name}=");
    let before_separator = argv.iter().take_while(|argument| *argument != "--").count();
    let position = argv[..before_separator]
        .iter()
        .position(|argument| argument.to_str().is_some_and(|text| text == name || text.starts_with(&inline)))?;
    let value_follows = takes_value
        && argv[position].to_str() == Some(name)
        && argv[..before_separator]
            .get(position + 1)
            .and_then(|argument| argument.to_str())
            .is_some_and(|text| !text.starts_with('-'));
    Some(position..position + 1 + usize::from(value_follows))
}
