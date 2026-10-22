//! The `bench` verbs and their options. The output form, `--json`, `--text` and `--stream`, is global to `vm`.

use std::path::PathBuf;
use std::time::Duration;

use clap::{Subcommand, ValueEnum};

use super::arm::{Arm, ArmKind, check_plugin_id, parse_arm};
use super::gc::DEFAULT_KEEP;
use super::profile::ProfileEvent;
use super::record::CLASS_ANCHORS;

/// The plugin id of Air, which owns the gate span of the empty-editor arm.
const AIR_PLUGIN_ID: &str = "com.intellij.air";

/// The default distribution: the self-contained `.dist` of the `idea` row.
pub(crate) const DEFAULT_TARGET: &str = "//build:idea_dist";

/// The long help of `bench`: what a session does, the lock, and the age of an `AIR_VM_BIN` binary.
pub(crate) const LONG_ABOUT: &str = "Measures the start-up of the IDE on this macOS host, from a staged copy of its dev \
distribution, and takes no worker and no lease.

welcome, open-project and project build the self-contained distribution --target with the host Bazel build. They \
stage it with its JBR as an immutable generation under the runtime root, keyed by its digest. Every IDE run starts \
from that generation, never from bazel-out. The answer is the summary, also in <session>/summary.json, and a text digest.

A session holds a lock from its build to its summary, so a second session or a gc on this host is refused with \
bench_busy. When AIR_VM_BIN names the vm binary, a session notes its age. A source under crates/ that is newer is a \
warning of the session.";

/// The paragraph under the help of `bench`: the exit codes of every verb.
pub(crate) const EXIT_CODES: &str = "Exit codes:
  0   every arm has a valid run
  1   a tool failed
  2   a bad invocation, no license, or a target that the bench cannot stage
  5   the build failed
  6   an arm has no run that passed the gate; error.details holds the summary
  70  an interrupt, which leaves no IDE running
  75  the host is busy, or a session runs";

/// The forms of a session argument of `replay`, `trace`, `activities`, `classes` and `compare`.
const SESSION_FORMS: &str = "`latest`, a directory, a session name under `<runtime root>/bench/runs`, or a part of one name";

/// The `bench` verbs.
#[derive(Clone, Debug, PartialEq, Subcommand)]
pub(crate) enum BenchVerb {
    /// Starts the IDE without a project and measures the time to the welcome screen, modal and non-modal.
    ///
    /// The session writes a sandbox template and primes each arm once, unless --cold. Then it runs the measured runs
    /// with the arms interleaved. Each measured run starts from a copy of the primed sandbox.
    Welcome(WelcomeArgs),
    /// Parses a finished session again and prints its digest. No IDE starts.
    Replay(ReplayArgs),
    /// Starts the IDE without a project, then opens <PROJECT> in the running IDE and measures its editor.
    OpenProject(OpenProjectArgs),
    /// Starts the IDE on PROJECT and measures the time to the highlighted editor, or to the built Air composer of an
    /// empty editor area. No welcome screen.
    ///
    /// In the project arm, the prime run opens the project with its first file, so the IDE saves the editor state. Each
    /// measured run opens the project directory and the IDE restores the editor.
    ///
    /// In the empty-editor arm, every run opens the project directory, and the IDE opens no README. The IDE restores no
    /// editor, so the empty editor area builds the Air composer, and the gate is the end of its
    /// air.emptyState.createComponent span. --cold starts each run of this arm from the unprimed template.
    Project(ProjectArgs),
    /// Removes the old generations: all except the --keep newest and those a session of the last 24 hours names.
    Gc(GcArgs),
    /// Prints the spans of one run that start in a window, with their start and end, and the anchors of the run. No
    /// IDE starts.
    ///
    /// The default window starts at frameBecameVisible and ends at the welcome screen paint. A run without
    /// frameBecameVisible, such as a modal run, has the window from the process start to the main anchor of its arm:
    /// welcomeBecameVisible, the highlighted editor or the built composer. A missing end widens the window to the end of
    /// the run. --overlap also keeps the spans that start before the window and run into it. A span over 4 s is marked
    /// `waits`. A span that runs into the quit, `application.exit`, is marked `cut by the quit`.
    Trace(TraceArgs),
    /// Prints the post-startup activities of one run by class and by plugin. No IDE starts.
    ///
    /// An activity is a `run activity` or a `run init activity <name>` span, without its helper twins. The default
    /// window is the whole run. The first table holds the longest activities with their start and the marks of
    /// `trace`. The second one holds the plugins by the sum of the durations of their activities.
    Activities(ActivitiesArgs),
    /// Prints the loaded classes of one arm by plugin and by module, as the JVM logged them, at an anchor of the run. No
    /// IDE starts.
    ///
    /// A count is the median over the valid runs of the arm. The totals hold the named classes of the run by their
    /// source, and the named classes before the anchor. The plugin table holds the classes of each plugin before the
    /// anchor, its modules with a class, and the EDT and BG ms that its loaders spent in findClass. The module table
    /// holds the same for each main module and content module, with its plugin. A main module is `<plugin id> (main)`.
    /// A plugin-classes.txt of the old shape has no time, so a time is `-`. A run needs class-load.log and
    /// plugin-classes.txt for an anchor. --entries lists the first classes of each plugin of the plugin table before
    /// the anchor, from the median run of the arm.
    ///
    /// The process clock starts at the load of com.intellij.idea.Main in class-load.log. A log without that class takes
    /// jvm.loadingTime of startup-stats.json, which puts the start about 100 ms too early, with a note.
    ///
    /// --span counts the class loads in the window of one span instead of before an anchor, on the EDT by default.
    Classes(ClassesArgs),
    /// Prints the plugin loaders of one run by the frame that triggered each one. No IDE starts.
    ///
    /// A loader is the main module or a content module of a plugin. The debug log of the plugin class loaders writes the
    /// stack of the first class of each loader. The trigger frame is the frame nearest to the top of that stack whose
    /// class has another owner than the loader. The search skips the frames of the runtime, the language libraries,
    /// the service container, the extension points and the message bus: java., jdk., sun., kotlin., kotlinx.,
    /// com.intellij.serviceContainer., com.intellij.openapi.extensions. and com.intellij.util.messages. A class without
    /// an owner in plugin-classes.txt is a platform class. A row holds a trigger frame, its loaders, their classes and
    /// EDT ms before the anchor, and the first load of its earliest loader. The loaders of each trigger follow the
    /// table. A loader without a stack is in the row `(no activation stack)`.
    Triggers(TriggersArgs),
    /// Prints the medians of several sessions per arm, with the delta of each session to the first one. No IDE starts.
    ///
    /// The first session is A. A line tells whether the noise arm, the first arm both sessions have, moved from A by
    /// more than twice the noise of A. The noise arm needs 3 valid runs with its main metric per session. The noise of
    /// a session is 1.4826 times the median absolute deviation of the main metric of its noise arm. A session whose own
    /// noise is above 20 % of the median of its noise arm is too noisy to compare, and the table still follows. A delta needs 3 valid runs per session. A later session of the distribution of A
    /// is an A/A run, and its move is the noise of this host. A session of another measurement model than A, such as a
    /// session from before the model, gets a line that names both models.
    Compare(CompareArgs),
    /// Lists the sessions of this host, newest first, with their inputs and their medians. No IDE starts.
    ///
    /// The order is the one of `latest`: by the mtime of `session.json`. A line holds the name, the time, the
    /// command, the commit and the dist digest. Per arm it holds the valid runs and the median of its main metric from
    /// `summary.json`. Each name is a session argument of the other verbs.
    Ls(ListArgs),
}

/// The run of a session that a verb reads.
#[derive(Clone, Debug, PartialEq, Eq, clap::Args)]
pub(crate) struct RunSelection {
    #[arg(value_name = "SESSION", help = format!("The session: {SESSION_FORMS}"))]
    pub(crate) session: String,
    /// The run directory, such as `non-modal-run-03`. The default is the median run of the arm that --arm names, else
    /// of the first arm that is not modal, else of the modal arm.
    #[arg(long, value_name = "DIR")]
    pub(crate) run: Option<String>,
    /// The arm whose median run the verb reads. A run that --run names must be a run of this arm. An arm is modal,
    /// non-modal, open-project, project or empty-editor, or the twin `<arm>-without-<plugin id>` of --suppress-plugin.
    #[arg(long, value_name = "ARM", value_parser = parse_arm)]
    pub(crate) arm: Option<Arm>,
}

/// A window of a run, in milliseconds from the process start. A span is in the window when it starts in it.
#[derive(Clone, Copy, Debug, PartialEq, clap::Args)]
pub(crate) struct Window {
    /// The start of the window, in ms from the process start.
    #[arg(long, value_name = "MS", value_parser = parse_ms)]
    pub(crate) from: Option<f64>,
    /// The end of the window, in ms from the process start. A span that starts at the end is not in the window.
    #[arg(long, value_name = "MS", value_parser = parse_ms)]
    pub(crate) to: Option<f64>,
}

#[derive(Clone, Debug, PartialEq, clap::Args)]
pub(crate) struct TraceArgs {
    #[command(flatten)]
    pub(crate) selection: RunSelection,
    #[command(flatten)]
    pub(crate) window: Window,
    /// Leaves out the spans shorter than MS.
    #[arg(long, value_name = "MS", default_value_t = DEFAULT_MIN_MS, value_parser = parse_ms)]
    pub(crate) min: f64,
    /// Keeps the coroutine helper twins, `<span>: scheduled` and `<span>: completing`.
    #[arg(long)]
    pub(crate) all: bool,
    /// Keeps every span that runs in the window: a span that starts before its end and ends after its start, also one
    /// that starts before the window.
    #[arg(long)]
    pub(crate) overlap: bool,
}

/// The shortest span that `bench trace` prints by default.
pub(crate) const DEFAULT_MIN_MS: f64 = 10.0;

#[derive(Clone, Debug, PartialEq, clap::Args)]
pub(crate) struct ActivitiesArgs {
    #[command(flatten)]
    pub(crate) selection: RunSelection,
    #[command(flatten)]
    pub(crate) window: Window,
    /// The rows of each table at most.
    #[arg(long, value_name = "N", default_value_t = DEFAULT_TOP, value_parser = clap::value_parser!(u32).range(1..))]
    pub(crate) top: u32,
}

/// The rows of each table of `bench activities`, `bench classes` and `bench triggers` by default.
pub(crate) const DEFAULT_TOP: u32 = 25;

#[derive(Clone, Debug, PartialEq, Eq, clap::Args)]
pub(crate) struct ClassesArgs {
    #[arg(value_name = "SESSION", help = format!("The session: {SESSION_FORMS}"))]
    pub(crate) session: String,
    /// The arm. The default is the first arm that is not modal and has a valid run, else the modal arm. An arm is
    /// modal, non-modal, open-project, project or empty-editor, or the twin `<arm>-without-<plugin id>` of
    /// --suppress-plugin.
    #[arg(long, value_name = "ARM", value_parser = parse_arm)]
    pub(crate) arm: Option<Arm>,
    /// The rows of each table at most. With --plugin, the plugin table holds each named plugin.
    #[arg(long, value_name = "N", default_value_t = DEFAULT_TOP, value_parser = clap::value_parser!(u32).range(1..))]
    pub(crate) top: u32,
    /// Keeps only this plugin id in the plugin table and its modules in the module table, with 0 when it has no class,
    /// and prints its rank among all plugins. Repeat it for more plugins.
    #[arg(long = "plugin", value_name = "ID")]
    pub(crate) plugins: Vec<String>,
    /// The anchor of the plugin table, of the module table and of the last total. The default is the main anchor of the arm:
    /// welcomeBecameVisible for a welcome arm, `editor highlighting completed` for the project arm and emptyStateBuilt
    /// for the empty-editor arm. An arm without it takes its last anchor. `quit` is the start of application.exit.
    #[arg(long, value_name = "ANCHOR", value_parser = clap::builder::PossibleValuesParser::new(CLASS_ANCHORS))]
    pub(crate) at: Option<String>,
    /// Lists the first N classes of each plugin of the plugin table that the median run of the arm loaded before the
    /// anchor, with their ms from the process start. The run needs class-load.log and plugin-classes.txt.
    #[arg(long, value_name = "N", value_parser = clap::value_parser!(u32).range(1..))]
    pub(crate) entries: Option<u32>,
    /// Counts the class loads in the window of the span NAME instead: from the start to the end of the first span NAME
    /// of each run, such as air.emptyState.createContent. The tables hold the loads by plugin, by module, and by
    /// package for a class without a plugin, with the findClass ms of the plugin classes. A hidden class counts with
    /// the class that defines it. A run needs class-load.log, plugin-classes.txt, opentelemetry.json and, for the EDT,
    /// threads.txt.
    #[arg(long, value_name = "NAME", conflicts_with_all = ["at", "entries", "plugins"])]
    pub(crate) span: Option<String>,
    /// The thread of the loads of --span: `edt`, the EDT, or `all`, every thread. With `edt`, the BG columns give the
    /// loads of the other threads.
    #[arg(long, value_name = "THREAD", value_enum, default_value_t = SpanThread::Edt, requires = "span")]
    pub(crate) thread: SpanThread,
    /// The run directory of --span, such as `empty-editor-run-05`. The default is every valid run of the arm, and a
    /// number is the median over the runs with the span.
    #[arg(long, value_name = "DIR", requires = "span")]
    pub(crate) run: Option<String>,
    /// Adds a timeline of the window of --span in buckets of MS: the loads of each bucket and its first classes. The
    /// run is the run of --run, else the median run of the arm.
    #[arg(long, value_name = "MS", value_parser = clap::value_parser!(u32).range(1..), requires = "span")]
    pub(crate) timeline: Option<u32>,
}

/// The `--thread` value of `bench classes --span`.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, ValueEnum, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) enum SpanThread {
    /// The EDT, `AWT-EventQueue-0`.
    #[default]
    Edt,
    /// Every thread.
    All,
}

#[derive(Clone, Debug, PartialEq, Eq, clap::Args)]
pub(crate) struct TriggersArgs {
    #[command(flatten)]
    pub(crate) selection: RunSelection,
    /// Keeps only the loaders of this plugin id. Repeat it for more plugins.
    #[arg(long = "plugin", value_name = "ID")]
    pub(crate) plugins: Vec<String>,
    /// The anchor: a loader counts when it has a class before it, and its classes and EDT ms are those before it. The
    /// default is the main anchor of the arm, as for `classes`. `quit` is the start of application.exit.
    #[arg(long, value_name = "ANCHOR", value_parser = clap::builder::PossibleValuesParser::new(CLASS_ANCHORS))]
    pub(crate) at: Option<String>,
    /// The rows of the trigger table at most, and the loaders of each trigger at most.
    #[arg(long, value_name = "N", default_value_t = DEFAULT_TOP, value_parser = clap::value_parser!(u32).range(1..))]
    pub(crate) top: u32,
}

#[derive(Clone, Debug, PartialEq, Eq, clap::Args)]
pub(crate) struct CompareArgs {
    #[arg(
        value_name = "SESSION",
        required = true,
        num_args = 2..=MAX_COMPARED,
        help = format!("The sessions, 2 to {MAX_COMPARED}, each one {SESSION_FORMS}. The first one is A, the base of each delta")
    )]
    pub(crate) sessions: Vec<String>,
    /// Refuses two sessions of one distribution with same_dist and the exit code 2.
    #[arg(long)]
    pub(crate) expect_different_dist: bool,
}

/// The sessions that `bench compare` takes at most: one letter each, from A to Z.
pub(crate) const MAX_COMPARED: usize = 26;

#[derive(Clone, Copy, Debug, PartialEq, Eq, clap::Args)]
pub(crate) struct ListArgs {
    /// The newest sessions to list at most.
    #[arg(long, value_name = "N", default_value_t = DEFAULT_LIMIT, value_parser = clap::value_parser!(u32).range(1..))]
    pub(crate) limit: u32,
}

/// The sessions that `bench ls` lists by default.
pub(crate) const DEFAULT_LIMIT: u32 = 10;

/// The `--hold` of a verb that starts the IDE by default.
pub(crate) const DEFAULT_HOLD: &str = "3s";

/// The measured runs per arm of `bench welcome` by default.
pub(crate) const DEFAULT_WELCOME_RUNS: u32 = 5;

/// The measured runs of `bench open-project` and of `bench project` by default.
pub(crate) const DEFAULT_OPEN_PROJECT_RUNS: u32 = 3;

/// The options of a verb that starts the IDE.
#[derive(Clone, Debug, PartialEq, clap::Args)]
pub(crate) struct LaunchArgs {
    /// The Bazel label of the self-contained distribution. The IDE starts with the JBR inside the distribution.
    #[arg(long, value_name = "LABEL", default_value = DEFAULT_TARGET, value_parser = flag_value)]
    pub(crate) target: String,
    /// The time to wait after the event of the gate, before the quit: `3s`, `500ms`, `1m` or `0`.
    #[arg(long, value_name = "DURATION", default_value = DEFAULT_HOLD, value_parser = parse_hold)]
    pub(crate) hold: Duration,
    /// The session directory. The default is `<runtime root>/bench/runs/<actor id>`.
    #[arg(long, value_name = "DIR")]
    pub(crate) session: Option<PathBuf>,
    /// Refuses the session with host_busy before the build when the 1-minute load average of the host is above LOAD.
    /// The default is the CPU count. A host without a load average passes.
    #[arg(long, value_name = "LOAD", value_parser = parse_load)]
    pub(crate) max_load: Option<f64>,
    /// Runs the session over the staged generation whose digest starts with DIGEST, under
    /// `<runtime root>/bench/generations`, and skips the build and the stage. The session names the commit that built
    /// the generation, so an A/B/A run measures the base again from its own bytes.
    #[arg(long, value_name = "DIGEST", value_parser = parse_digest_prefix, conflicts_with = "target")]
    pub(crate) generation: Option<String>,
}

#[derive(Clone, Debug, PartialEq, clap::Args)]
pub(crate) struct WelcomeArgs {
    /// The measured runs per arm.
    #[arg(long, value_name = "N", default_value_t = DEFAULT_WELCOME_RUNS, value_parser = clap::value_parser!(u32).range(1..=50))]
    pub(crate) runs: u32,
    /// The arms of the session.
    #[arg(long, value_enum, default_value_t = ArmChoice::Both)]
    pub(crate) arm: ArmChoice,
    /// Starts each measured run from a fresh copy of the unprimed template, and skips the prime runs.
    #[arg(long)]
    pub(crate) cold: bool,
    /// Samples with async-profiler from the JVM start to the gate of each run, and reports the top frames of the EDT. A
    /// run whose JBR has no jcmd keeps the profile of the whole run.
    #[arg(long)]
    pub(crate) profile: bool,
    /// The event of --profile: `cpu`, the CPU time of each thread, or `wall`, the wall clock, which samples a waiting
    /// thread too.
    #[arg(long, value_name = "EVENT", value_enum, default_value_t = ProfileEvent::Cpu, requires = "profile")]
    pub(crate) profile_event: ProfileEvent,
    #[command(flatten)]
    pub(crate) suppress: SuppressArgs,
    #[command(flatten)]
    pub(crate) launch: LaunchArgs,
}

impl WelcomeArgs {
    /// The arms of the session, in the interleave order: each arm of --arm, and after it its twin of
    /// --suppress-plugin.
    pub(crate) fn arms(&self) -> Vec<Arm> {
        self.suppress.with_twins(self.arm.arms())
    }
}

/// The plugin that the twin arms of a session suppress.
#[derive(Clone, Debug, PartialEq, Eq, clap::Args)]
pub(crate) struct SuppressArgs {
    /// Adds a twin to each arm: the arm `<arm>-without-<ID>`, whose runs pass `-Didea.suppressed.plugins.id=<ID>`, so the
    /// IDE loads no plugin of that id. A twin has its own prime run and its own sandbox, and its runs interleave with
    /// the runs of its arm.
    #[arg(long = "suppress-plugin", value_name = "ID", value_parser = plugin_id)]
    pub(crate) plugin: Option<String>,
}

impl SuppressArgs {
    /// The arms, each one followed by its twin when a plugin is suppressed.
    pub(crate) fn with_twins(&self, arms: Vec<Arm>) -> Vec<Arm> {
        let Some(plugin) = &self.plugin else {
            return arms;
        };
        arms.into_iter()
            .flat_map(|arm| {
                let twin = arm.without(plugin);
                [arm, twin]
            })
            .collect()
    }
}

/// Parses `--suppress-plugin`: a plugin id that [`check_plugin_id`] accepts.
fn plugin_id(value: &str) -> Result<String, String> {
    check_plugin_id(value)?;
    Ok(value.to_owned())
}

#[derive(Clone, Debug, PartialEq, clap::Args)]
pub(crate) struct OpenProjectArgs {
    /// The project directory that the running IDE opens.
    #[arg(value_name = "PROJECT")]
    pub(crate) project: PathBuf,
    /// The measured runs.
    #[arg(long, value_name = "N", default_value_t = DEFAULT_OPEN_PROJECT_RUNS, value_parser = clap::value_parser!(u32).range(1..=50))]
    pub(crate) runs: u32,
    #[command(flatten)]
    pub(crate) launch: LaunchArgs,
}

#[derive(Clone, Debug, PartialEq, clap::Args)]
pub(crate) struct ProjectArgs {
    /// The project: `markdown`, a generated project of Markdown files only, or a directory to copy into the sandbox.
    #[arg(value_name = "PROJECT")]
    pub(crate) project: String,
    /// The measured runs per arm.
    #[arg(long, value_name = "N", default_value_t = DEFAULT_OPEN_PROJECT_RUNS, value_parser = clap::value_parser!(u32).range(1..=50))]
    pub(crate) runs: u32,
    /// The arms of the session. `both` interleaves the project arm and the empty-editor arm.
    #[arg(long, value_enum, default_value_t = ProjectArmChoice::Project)]
    pub(crate) arm: ProjectArmChoice,
    /// Starts each measured run from a fresh copy of the unprimed template, with empty caches, and skips the prime
    /// run. Only the empty-editor arm takes it: the project arm restores the editor of its prime run.
    #[arg(long)]
    pub(crate) cold: bool,
    /// Samples with async-profiler from the JVM start to the gate of each run, and reports the top frames of the EDT. The
    /// gate of the empty-editor arm is the end of air.emptyState.createComponent. The profile has no time of a sample,
    /// so it covers the whole start-up to the gate, not one span. A run whose JBR has no jcmd keeps the profile of the
    /// whole run.
    #[arg(long)]
    pub(crate) profile: bool,
    /// The event of --profile: `cpu`, the CPU time of each thread, or `wall`, the wall clock, which samples a waiting
    /// thread too.
    #[arg(long, value_name = "EVENT", value_enum, default_value_t = ProfileEvent::Cpu, requires = "profile")]
    pub(crate) profile_event: ProfileEvent,
    #[command(flatten)]
    pub(crate) suppress: SuppressArgs,
    #[command(flatten)]
    pub(crate) launch: LaunchArgs,
}

impl ProjectArgs {
    /// The arms of the session, in the interleave order: each arm of --arm, and after it its twin of
    /// --suppress-plugin. A cold session of the project arm is refused: without a prime run, no editor state is there
    /// to restore. A twin of the empty-editor arm without Air is refused too: its gate is a span of the Air composer.
    pub(crate) fn arms(&self) -> Result<Vec<Arm>, String> {
        let arms = self.suppress.with_twins(self.arm.arms());
        if self.cold && arms.iter().any(|arm| arm.kind == ArmKind::Project) {
            return Err(
                "--cold needs --arm empty-editor: the project arm restores the editor that its prime run saved, and a cold session has no prime run"
                    .to_owned(),
            );
        }
        if arms
            .iter()
            .any(|arm| arm.kind == ArmKind::EmptyEditor && arm.suppressed.as_deref() == Some(AIR_PLUGIN_ID))
        {
            return Err(format!(
                "--suppress-plugin {AIR_PLUGIN_ID} needs --arm project: the gate of the empty-editor arm is the span of the Air composer, so a run without Air never passes it"
            ));
        }
        Ok(arms)
    }
}

#[derive(Clone, Debug, PartialEq, Eq, clap::Args)]
pub(crate) struct ReplayArgs {
    #[arg(
        value_name = "SESSION",
        help = format!("The session of a finished welcome, open-project or project: {SESSION_FORMS}")
    )]
    pub(crate) session: String,
}

#[derive(Clone, Debug, PartialEq, Eq, clap::Args)]
pub(crate) struct GcArgs {
    /// The newest generations, by the mtime of their directory, that stay whatever names them.
    #[arg(long, value_name = "N", default_value_t = DEFAULT_KEEP)]
    pub(crate) keep: u32,
}

/// The `--arm` value of `bench welcome`.
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
            Self::Both => vec![Arm::MODAL, Arm::NON_MODAL],
            Self::Modal => vec![Arm::MODAL],
            Self::NonModal => vec![Arm::NON_MODAL],
        }
    }
}

/// The `--arm` value of `bench project`.
#[derive(Clone, Copy, Debug, PartialEq, Eq, ValueEnum)]
pub(crate) enum ProjectArmChoice {
    Project,
    EmptyEditor,
    Both,
}

impl ProjectArmChoice {
    /// The arms in the interleave order.
    pub(crate) fn arms(self) -> Vec<Arm> {
        match self {
            Self::Project => vec![Arm::PROJECT],
            Self::EmptyEditor => vec![Arm::EMPTY_EDITOR],
            Self::Both => vec![Arm::PROJECT, Arm::EMPTY_EDITOR],
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

/// Parses a value in milliseconds: a number that is finite and not negative.
pub(crate) fn parse_ms(value: &str) -> Result<f64, String> {
    match value.parse::<f64>() {
        Ok(ms) if ms.is_finite() && ms >= 0.0 => Ok(ms),
        _ => Err(format!("must be a number of milliseconds, 0 or more, got: {value}")),
    }
}

/// Parses `--generation`: the start of a generation digest, one or more lowercase hex digits.
pub(crate) fn parse_digest_prefix(value: &str) -> Result<String, String> {
    if !value.is_empty() && value.chars().all(|character| matches!(character, '0'..='9' | 'a'..='f')) {
        Ok(value.to_owned())
    } else {
        Err(format!(
            "must be the start of a generation digest, lowercase hex digits, got: {value}"
        ))
    }
}

/// Parses `--max-load`: a number that is finite and above 0.
pub(crate) fn parse_load(value: &str) -> Result<f64, String> {
    match value.parse::<f64>() {
        Ok(load) if load.is_finite() && load > 0.0 => Ok(load),
        _ => Err(format!("must be a load average above 0, got: {value}")),
    }
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

#[cfg(test)]
mod tests;
