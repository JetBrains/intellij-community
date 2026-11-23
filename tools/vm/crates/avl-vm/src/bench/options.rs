//! The `bench` verbs and their options. The output form, `--json`, `--text` and `--stream`, is global to `vm`.

use std::path::PathBuf;
use std::time::Duration;

use clap::{Subcommand, ValueEnum};

use super::arm::{Arm, ArmKind, Property, Twin, check_plugin_id, parse_arm};
use super::gc::DEFAULT_KEEP;
use super::profile::ProfileEvent;
use super::record::{CLASS_ANCHORS, FIND_QUERY_SEPARATOR};

/// The plugin id of Air, which owns the gate span of the empty-editor arm.
const AIR_PLUGIN_ID: &str = "com.intellij.air";

/// The default distribution: the self-contained `.dist` of the `idea` row.
pub(crate) const DEFAULT_TARGET: &str = "//build:idea_dist";

/// The long help of `bench`: what a session does, the lock, and the age of an `AIR_VM_BIN` binary.
pub(crate) const LONG_ABOUT: &str = "Measures the start-up of the IDE on this macOS host: its time, its memory, its classes and \
the code that loads them. It starts the IDE from a staged copy of its dev distribution, and takes no worker and no lease.

welcome, project and open-project start the IDE and write a session. ls, compare, classes, triggers, activities, trace \
and replay read a session and start no IDE. gc removes old generations.

A verb that starts the IDE builds the self-contained distribution --target with the host Bazel build. It stages the \
distribution with its JBR as an immutable generation under the runtime root, keyed by its digest. Every IDE run starts \
from that generation, never from bazel-out. The answer is the summary, also in <session>/summary.json, and a text digest.

After the gate, a run writes <run>/memory.json: the physical footprint and the resident size of the IDE, of its \
descendants, and of its WebKit processes. The digest shows them in the group `memory at the gate (MB)`. A failed sample \
is a note of the run.

A session holds a lock from its build to its summary, so a second session or a gc on this host is refused with \
bench_busy. When AIR_VM_BIN names the vm binary, a session notes its age. A source under crates/ that is newer is a \
warning of the session.";

/// The guide under the long help of `bench`: the order of a class attribution, the measurement of a change, and the
/// host. It is the one place that states how to use the verbs together, so a skill or a failure text only names
/// `bench --help`. Each line is shorter than the wrap width of the help, so a command line stays one line.
const GUIDE: &str = "Find what loads the classes of a plugin at start-up, here of Air (com.intellij.air):
  1. Run one session of the arm that shows the plugin. A class count needs no quiet host.
    ./community/tools/vm.cmd bench project markdown --arm empty-editor --runs 3 --hold 15s
  2. Read the classes of each module of the plugin, and the rank of the plugin.
    ./community/tools/vm.cmd bench classes latest --plugin com.intellij.air --at quit --kinds
  3. Read the frame that woke each loader of the plugin.
    ./community/tools/vm.cmd bench triggers latest --plugin com.intellij.air --at quit
  4. For the code behind each class of a loader, run a session with --class-causes. The stacks
    slow it down ten times, so give it ten times the hold, and read it against the session of
    step 1 as the baseline.
    ./community/tools/vm.cmd bench project markdown --arm empty-editor --hold 150s --class-causes
    ./community/tools/vm.cmd bench causes latest --baseline aaaa1111 \\
      --plugin com.intellij.air --at quit
  5. Read the post-startup activities by class and by plugin.
    ./community/tools/vm.cmd bench activities latest
  6. Read the classes that one span loads, such as the build of the Air composer.
    ./community/tools/vm.cmd bench classes latest --span air.emptyState.createContent --thread all

The anchor decides the count:
  - The default anchor is the main anchor of the arm, such as emptyStateBuilt of the empty-editor
    arm.
  - A test that counts when the IDE is idle counts later. The Air start-up class budget of the
    suite startup-class-loading is such a test.
  - For a count near the count of such a test, run the session with --hold 15s and read it with
    --at quit.

Measure a change:
  - Two sessions: run one on the base commit and one on the change, then compare them. compare
    tells whether the medians moved by more than the noise of the host.
  - One session: --suppress-plugin or --with-property adds a twin of each arm. The runs of a twin
    interleave with the runs of its arm.
  - A/B/A: run A1 on the base and B on the change. Then run A2 over the generation of A1 with
    --generation. The second line of the digest of A1 names the digest of that generation, and
    so does ls. A2 against A1 is the noise of the host.
    ./community/tools/vm.cmd bench welcome
    ./community/tools/vm.cmd bench welcome --generation 5f1c0d9e2b7a
    ./community/tools/vm.cmd bench compare aaaa1111 bbbb2222 cccc3333

The host:
  - A time needs a quiet host, and a class count does not.
  - --max-load refuses a session on a busy host. A load below that limit still moves a time: on a
    host of 20 cores, a load of 10 moved the medians of a 2 s start-up by 100 to 200 ms against a
    load of 7. So compare sessions of one load.
  - For a quiet host, run the session on another machine as one exclusive run of brd-host.
    `brd-host run --help` states the option.
  - Put the bench commands into the argv of that run. brd-host ships no file that git ignores,
    such as a script under out/.
  - A session stays on the host that ran it, so run the verbs that read it on that host too.";

/// The text under the long help of `bench`: the guide, then the exit codes.
pub(crate) fn after_long_help() -> String {
    format!("{GUIDE}\n\n{EXIT_CODES}")
}

/// The examples under the long help of `bench project`. Each line is shorter than the wrap width of the help.
pub(crate) const PROJECT_EXAMPLES: &str = "Examples:
  The start-up to the built Air composer, with a hold for a class count at the quit:
    ./community/tools/vm.cmd bench project markdown --arm empty-editor --runs 3 --hold 15s
  The Find in Files searches of JetBrains Light on this checkout:
    ./community/tools/vm.cmd bench project . --in-place --arm find-in-files \\
      --target //build:jetbrains_light_all_plugins_dist \\
      --context-plugins //platform/unified:performance_testing_plugin_dir \\
      --query createSearcher --query FindModel --directory platform";

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
    /// Starts the IDE on PROJECT and measures the time to the highlighted editor, or to the built Air composer of an
    /// empty editor area. No welcome screen.
    ///
    /// In the project arm, the prime run opens the project with its first file, so the IDE saves the editor state. Each
    /// measured run opens the project directory and the IDE restores the editor.
    ///
    /// In the empty-editor arm, every run opens the project directory, and the IDE opens no README. The IDE restores no
    /// editor, so the empty editor area builds the Air composer, and the gate is the end of its
    /// air.emptyState.createComponent span. --cold starts each run of this arm from the unprimed template.
    ///
    /// In the cargo-sync arm, every run is a first start: it starts from the unprimed template, with empty caches and
    /// no prime run, and the IDE opens no README, as RustRoverIndexingTest does. The gate is the first line
    /// `CargoSyncTask started` of idea.log. Its metric cargoSyncStarted is in ms from the process start, and cargoSyncStarted.ideaLog is
    /// the ms of the log, the value of rust_duration_from_start_to_cargo_sync of the RustRover perf test.
    ///
    /// In the find-in-files arm, every run opens the project directory, and the IDE opens no README. The command
    /// %findInFiles of the performance plugin then searches the project scope for each --query in turn. The gate is the
    /// end of the span `findInFiles#search: <query>` of each query. The metric findInFiles.search is the sum of their
    /// durations, and each span is a metric with its duration and its result count. The IDE needs the performance
    /// plugin: JetBrains Light gets it through --context-plugins. --in-place opens a large tree, such as a checkout,
    /// where it is.
    #[command(after_long_help = PROJECT_EXAMPLES)]
    Project(ProjectArgs),
    /// Starts the IDE without a project, then opens <PROJECT> in the running IDE and measures its editor.
    ///
    /// The verb reports no number yet. The IDE opens the second project without a trace span, and it writes
    /// `editor highlighting completed` only once, in its start-up report. So each run fails with a named reason, and
    /// the verb exits with 6. Use `project` for the time to the highlighted editor.
    OpenProject(OpenProjectArgs),
    /// Lists the sessions of this host, newest first, with their inputs and their medians. No IDE starts.
    ///
    /// The order is the one of `latest`: by the mtime of `session.json`. A line holds the name, the time, the
    /// command, the commit and the dist digest. Per arm it holds the valid runs and the median of its main metric from
    /// `summary.json`. Each name is a session argument of the other verbs.
    Ls(ListArgs),
    /// Prints the medians of several sessions per arm, with the delta of each session to the first one. No IDE starts.
    ///
    /// The first session is A. A line tells whether the noise arm, the first arm both sessions have, moved from A by
    /// more than twice the noise of A. The noise arm needs 3 valid runs with its main metric per session. The noise of
    /// a session is 1.4826 times the median absolute deviation of the main metric of its noise arm. A session whose own
    /// noise is above 20 % of the median of its noise arm is too noisy to compare, and the table still follows. A delta needs 3 valid runs per session. A later session of the distribution of A
    /// is an A/A run, and its move is the noise of this host. A session of another measurement model than A, such as a
    /// session from before the model, gets a line that names both models. A twin is never the noise arm.
    Compare(CompareArgs),
    /// Prints the loaded classes of one arm by plugin and by module, as the JVM logged them, at an anchor of the run. No
    /// IDE starts.
    ///
    /// A count is the median over the valid runs of the arm. The totals hold the named classes of the run by their
    /// source, and the named classes before the anchor. The plugin table holds the classes of each plugin before the
    /// anchor, its modules with a class, and the EDT and BG ms that its loaders spent in findClass. The module table
    /// holds the same for each main module and content module, with its plugin. A main module is `<plugin id> (main)`.
    /// A plugin-classes.txt of the old shape has no time, so a time is `-`. A run needs class-load.log and
    /// plugin-classes.txt for an anchor. --entries lists the first classes of each plugin of the plugin table before
    /// the anchor, from the median run of the arm. --kinds counts the classes of each module of the module table by
    /// their kind, such as a lambda or a Companion, from the same run.
    ///
    /// The process clock starts at the load of com.intellij.idea.Main in class-load.log. A log without that class takes
    /// jvm.loadingTime of startup-stats.json, which puts the start about 100 ms too early, with a note.
    ///
    /// --span counts the class loads in the window of one span instead of before an anchor, on the EDT by default.
    ///
    /// The help of `bench` states the order of a class attribution: the session, then `classes`, `triggers`,
    /// `activities` and --span.
    Classes(ClassesArgs),
    /// Prints the plugin loaders of one run by the frame that triggered each one. No IDE starts.
    ///
    /// A loader is the main module or a content module of a plugin. The debug log of the plugin class loaders writes the
    /// stack of the first class of each loader. The trigger frame is the frame nearest to the top of that stack whose
    /// class has another owner than the loader. The search skips the frames of the runtime, the language libraries,
    /// the class loaders, the service and instance containers, the extension points and the message bus: java., jdk.,
    /// sun., kotlin., kotlinx., com.intellij.util.lang., com.intellij.ide.plugins.cl., com.intellij.serviceContainer.,
    /// com.intellij.platform.instanceContainer., com.intellij.openapi.components.,
    /// com.intellij.openapi.extensions. and com.intellij.util.messages. A class without
    /// an owner in plugin-classes.txt is a platform class. A row holds a trigger frame, its loaders, their classes and
    /// EDT ms before the anchor, and the first load of its earliest loader. The loaders of each trigger follow the
    /// table. A loader without a stack is in the row `(no activation stack)`.
    ///
    /// The help of `bench` states the order of a class attribution.
    Triggers(TriggersArgs),
    /// Prints the classes of the plugin loaders of one run by the frame that entered the loader of each class. No IDE
    /// starts.
    ///
    /// The run needs class-causes.log, which a session with --class-causes writes: the JVM stack of each class load.
    /// The entry frame of a class is the frame nearest to the bottom of its stack whose class has the owner of the class.
    /// So it is the first code of the loader that the thread ran, such as a startup activity, a service constructor or
    /// the body of a coroutine. A class whose stack has no frame of its loader has the frame of another owner nearest to
    /// the top instead, as for `triggers`, with the same skipped frames. The caller of a row is the most common frame
    /// of another owner below its entry frame. A frame keeps its class and method and drops its file and line. A row
    /// holds an entry frame, its classes before the anchor, its loaders and its first classes. A class of the plugin
    /// log without a stack in the log is in the row `(no stack)`.
    ///
    /// The help of `bench` states the order of a class attribution.
    Causes(CausesArgs),
    /// Prints the post-startup activities of one run by class and by plugin. No IDE starts.
    ///
    /// An activity is a `run activity` or a `run init activity <name>` span, without its helper twins. The default
    /// window is the whole run. The first table holds the longest activities with their start and the marks of
    /// `trace`. The second one holds the plugins by the sum of the durations of their activities.
    Activities(ActivitiesArgs),
    /// Prints the spans of one run that start in a window, with their start and end, and the anchors of the run. No
    /// IDE starts.
    ///
    /// The default window starts at frameBecameVisible and ends at the welcome screen paint. A run without
    /// frameBecameVisible, such as a modal run, has the window from the process start to the main anchor of its arm:
    /// welcomeBecameVisible, the highlighted editor or the built composer. A missing end widens the window to the end of
    /// the run. --overlap also keeps the spans that start before the window and run into it. A span over 4 s is marked
    /// `waits`. A span that runs into the quit, `application.exit`, is marked `cut by the quit`.
    Trace(TraceArgs),
    /// Parses a finished session again and prints its digest. No IDE starts.
    Replay(ReplayArgs),
    /// Removes the old generations: all except the --keep newest and those a session of the last 24 hours names.
    Gc(GcArgs),
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
    /// non-modal, open-project, project or empty-editor, or a twin: `<arm>-without-<plugin id>` of --suppress-plugin, or
    /// `<arm>-with-<name>=<value>` of --with-property.
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
    /// modal, non-modal, open-project, project or empty-editor, or a twin: `<arm>-without-<plugin id>` of
    /// --suppress-plugin, or `<arm>-with-<name>=<value>` of --with-property.
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
    /// for the empty-editor arm. An arm without it takes its last anchor. `quit` is the start of application.exit. A test
    /// that counts when the IDE is idle counts later than the main anchor: for a count near its count, read `quit` of a
    /// session that ran with --hold 15s.
    #[arg(long, value_name = "ANCHOR", value_parser = clap::builder::PossibleValuesParser::new(CLASS_ANCHORS))]
    pub(crate) at: Option<String>,
    /// Lists the first N classes of each plugin of the plugin table that the median run of the arm loaded before the
    /// anchor, with their ms from the process start. The run needs class-load.log and plugin-classes.txt.
    #[arg(long, value_name = "N", value_parser = clap::value_parser!(u32).range(1..))]
    pub(crate) entries: Option<u32>,
    /// Adds the kinds of the classes of each module of the module table that the median run of the arm loaded before
    /// the anchor: top-level classes, file facades (`*Kt`), named nested classes, numbered classes (`$1`: a lambda or
    /// the coroutine of a function), `Companion`, `WhenMappings` and `$$serializer`. The counts are of the median run,
    /// not medians. The run needs class-load.log and plugin-classes.txt.
    #[arg(long)]
    pub(crate) kinds: bool,
    /// Counts the class loads in the window of the span NAME instead: from the start to the end of the first span NAME
    /// of each run, such as air.emptyState.createContent. The tables hold the loads by plugin, by module, and by
    /// package for a class without a plugin, with the findClass ms of the plugin classes. A hidden class counts with
    /// the class that defines it. A run needs class-load.log, plugin-classes.txt, opentelemetry.json and, for the EDT,
    /// threads.txt.
    #[arg(long, value_name = "NAME", conflicts_with_all = ["at", "entries", "kinds", "plugins"])]
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
pub(crate) struct CausesArgs {
    #[command(flatten)]
    pub(crate) selection: RunSelection,
    /// Keeps only the classes of this plugin id. Repeat it for more plugins.
    #[arg(long = "plugin", value_name = "ID")]
    pub(crate) plugins: Vec<String>,
    /// Keeps only the classes of this content module, or of the main module of the plugin id. Repeat it for more
    /// modules.
    #[arg(long = "module", value_name = "NAME")]
    pub(crate) modules: Vec<String>,
    /// The anchor: a class counts when the JVM loaded it before the anchor. The default is the main anchor of the arm,
    /// as for `classes`. `quit` is the start of application.exit.
    #[arg(long, value_name = "ANCHOR", value_parser = clap::builder::PossibleValuesParser::new(CLASS_ANCHORS))]
    pub(crate) at: Option<String>,
    /// The rows of the entry table at most.
    #[arg(long, value_name = "N", default_value_t = DEFAULT_TOP, value_parser = clap::value_parser!(u32).range(1..))]
    pub(crate) top: u32,
    /// The session of the same arm without --class-causes whose classes the report attributes. The stacks slow a run
    /// down, so the run with the stacks can load other classes before the anchor than a normal run. With BASELINE the
    /// report takes the classes of the baseline run and only the stacks of the run of SESSION, and it counts the
    /// classes that only one of the two runs loaded.
    #[arg(long, value_name = "BASELINE")]
    pub(crate) baseline: Option<String>,
    /// The first classes of each row at most.
    #[arg(long, value_name = "N", default_value_t = DEFAULT_CAUSE_CLASSES, value_parser = clap::value_parser!(u32).range(0..))]
    pub(crate) classes: u32,
}

/// The first classes of a row of `bench causes` by default.
pub(crate) const DEFAULT_CAUSE_CLASSES: u32 = 3;

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
    /// The time to wait after the event of the gate, before the quit: `3s`, `500ms`, `1m` or `0`. The quit cuts the
    /// post-startup activities that still run. A longer hold, such as `15s`, lets them end, so the anchor `quit` counts
    /// their classes.
    #[arg(long, value_name = "DURATION", default_value = DEFAULT_HOLD, value_parser = parse_hold)]
    pub(crate) hold: Duration,
    /// The session directory. The default is `<runtime root>/bench/runs/<actor id>`.
    #[arg(long, value_name = "DIR")]
    pub(crate) session: Option<PathBuf>,
    /// Refuses the session with host_busy before the build when the 1-minute load average of the host is above LOAD.
    /// The default is the CPU count. A host without a load average passes. A time needs a quiet host, and a class
    /// count does not: the help of `bench` states what a load does to a time.
    #[arg(long, value_name = "LOAD", value_parser = parse_load)]
    pub(crate) max_load: Option<f64>,
    /// Runs the session over the staged generation whose digest starts with DIGEST, under
    /// `<runtime root>/bench/generations`, and skips the build and the stage. The session names the commit that built
    /// the generation, so an A/B/A run measures the base again from its own bytes.
    #[arg(long, value_name = "DIGEST", value_parser = parse_digest_prefix, conflicts_with = "target")]
    pub(crate) generation: Option<String>,
    /// Passes `-D<NAME>=<VALUE>` to every run of the session, also to the prime runs, before the properties of the
    /// bench and of a twin, so both win over a property of the same name. Repeatable. A value can name a path, such as
    /// accio.binary=/opt/accio/bin/accio.
    #[arg(long = "property", value_name = "NAME=VALUE", value_parser = Property::parse_session)]
    pub(crate) properties: Vec<Property>,
}

#[derive(Clone, Debug, PartialEq, clap::Args)]
pub(crate) struct WelcomeArgs {
    /// The measured runs per arm.
    #[arg(long, value_name = "N", default_value_t = DEFAULT_WELCOME_RUNS, value_parser = clap::value_parser!(u32).range(1..=50))]
    pub(crate) runs: u32,
    /// The arms of the session.
    #[arg(long, value_enum, default_value_t = ArmChoice::Both)]
    pub(crate) arm: ArmChoice,
    #[command(flatten)]
    pub(crate) measure: MeasureArgs,
    #[command(flatten)]
    pub(crate) launch: LaunchArgs,
}

impl WelcomeArgs {
    /// The arms of the session, in the interleave order: each arm of --arm, and after it its twins of
    /// --suppress-plugin and --with-property.
    pub(crate) fn arms(&self) -> Vec<Arm> {
        self.measure.twins.with_twins(self.arm.arms())
    }
}

/// The options of each measured run of a session of arms: the start from the template, the profile and the twins. A
/// verb can replace the help of an option with its own text, which names its arms.
#[derive(Clone, Debug, PartialEq, Eq, clap::Args)]
pub(crate) struct MeasureArgs {
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
    /// Makes the JVM of each run write class-causes.log: the Java stack of each class load, for `bench
    /// causes`. The stacks slow a start down by seconds, so a time of such a session does not compare with a time of
    /// another session. A class count stays valid.
    #[arg(long)]
    pub(crate) class_causes: bool,
    #[command(flatten)]
    pub(crate) twins: TwinArgs,
}

/// The help of `--cold` of `bench project`, which names the arms that take it.
const PROJECT_COLD_HELP: &str = "Starts each measured run from a fresh copy of the unprimed template, with empty caches, and \
    skips the prime run. Only the empty-editor arm takes it: the project arm restores the editor of its prime run. The \
    cargo-sync arm is always cold.";

/// The help of `--profile` of `bench project`, which also names the gate of the empty-editor arm.
const PROJECT_PROFILE_HELP: &str = "Samples with async-profiler from the JVM start to the gate of each run, and reports the top \
    frames of the EDT. The gate of the empty-editor arm is the end of air.emptyState.createComponent. The profile has no time \
    of a sample, so it covers the whole start-up to the gate, not one span. A run whose JBR has no jcmd keeps the profile of \
    the whole run.";

/// The twins of each arm of a session: the arm without a plugin, and the arm with a system property.
#[derive(Clone, Debug, PartialEq, Eq, clap::Args)]
pub(crate) struct TwinArgs {
    /// Adds a twin to each arm: the arm `<arm>-without-<ID>`, whose runs pass `-Didea.suppressed.plugins.id=<ID>`, so the
    /// IDE loads no plugin of that id. A twin has its own prime run and its own sandbox, and its runs interleave with
    /// the runs of its arm.
    #[arg(long = "suppress-plugin", value_name = "ID", value_parser = plugin_id)]
    pub(crate) plugin: Option<String>,
    /// Adds a twin to each arm: the arm `<arm>-with-<NAME>=<VALUE>`, whose runs pass `-D<NAME>=<VALUE>` after the
    /// properties of the bench, so the property wins over a property of the same name. The twin has its own prime run
    /// and its own sandbox, and its runs interleave with the runs of its arm, as a twin of --suppress-plugin.
    #[arg(long = "with-property", value_name = "NAME=VALUE", value_parser = Property::parse)]
    pub(crate) property: Option<Property>,
}

impl TwinArgs {
    /// The change of each twin, in the order of the twins of an arm: the suppressed plugin, then the property.
    fn twins(&self) -> Vec<Twin> {
        let without = self.plugin.clone().map(Twin::Without);
        let with = self.property.clone().map(Twin::With);
        without.into_iter().chain(with).collect()
    }

    /// The arms, each one followed by its twins.
    pub(crate) fn with_twins(&self, arms: Vec<Arm>) -> Vec<Arm> {
        let twins = self.twins();
        arms.into_iter()
            .flat_map(|arm| {
                let arm_twins: Vec<Arm> = twins.iter().map(|twin| arm.twin(twin.clone())).collect();
                std::iter::once(arm).chain(arm_twins)
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
#[command(mut_arg("cold", |arg| arg.help(PROJECT_COLD_HELP)), mut_arg("profile", |arg| arg.help(PROJECT_PROFILE_HELP)))]
pub(crate) struct ProjectArgs {
    /// The project: `markdown`, a generated project of Markdown files only, or a directory to copy into the sandbox.
    #[arg(value_name = "PROJECT")]
    pub(crate) project: String,
    /// The measured runs per arm.
    #[arg(long, value_name = "N", default_value_t = DEFAULT_OPEN_PROJECT_RUNS, value_parser = clap::value_parser!(u32).range(1..=50))]
    pub(crate) runs: u32,
    /// The arms of the session. `both` interleaves the project arm and the empty-editor arm. `cargo-sync` needs an IDE
    /// with the Rust plugin, such as --target //build:rustrover_dist, and a Cargo project. `find-in-files` needs --query
    /// and an IDE with the performance plugin.
    #[arg(long, value_enum, default_value_t = ProjectArmChoice::Project)]
    pub(crate) arm: ProjectArmChoice,
    /// A text that each run of the find-in-files arm finds in the project scope, repeatable, in the order of the
    /// searches. A query cannot hold `;`, which separates the queries of %findInFiles.
    #[arg(long = "query", value_name = "TEXT", value_parser = find_query)]
    pub(crate) queries: Vec<String>,
    /// A directory of the project, relative to it, or `.` for the project directory. Each run of the find-in-files arm
    /// then searches it too, with its subdirectories, after the project scope. The span of such a search is `findInFiles#searchInDirectory: <query>`.
    #[arg(long, value_name = "DIR", value_parser = find_directory)]
    pub(crate) directory: Option<PathBuf>,
    /// Opens the project directory where it is, in every run, and copies nothing into the sandbox. A tree too large to
    /// copy, such as a checkout, needs it. The IDE writes its project files into the `.idea` of the tree.
    #[arg(long)]
    pub(crate) in_place: bool,
    /// The label of a `context_plugin_dir`, such as //platform/unified:performance_testing_plugin_dir. The session builds it from
    /// the working tree, also with --generation, and each sandbox holds its plugin directories. JetBrains Light does
    /// not bundle the performance plugin that the find-in-files arm needs.
    #[arg(long, value_name = "LABEL", value_parser = flag_value)]
    pub(crate) context_plugins: Option<String>,
    #[command(flatten)]
    pub(crate) measure: MeasureArgs,
    #[command(flatten)]
    pub(crate) launch: LaunchArgs,
}

impl ProjectArgs {
    /// Tells whether each measured run starts from the unprimed template and the session has no prime run: `--cold`,
    /// or the cargo-sync arm, whose metric in CI is a first start.
    pub(crate) fn cold(&self) -> bool {
        self.measure.cold || self.arm == ProjectArmChoice::CargoSync
    }

    /// The arms of the session, in the interleave order: each arm of --arm, and after it its twins of
    /// --suppress-plugin and --with-property. A cold session of the project arm is refused: without a prime run, no
    /// editor state is there to restore. A twin of the empty-editor arm without Air is refused too: its gate is a span
    /// of the Air composer.
    pub(crate) fn arms(&self) -> Result<Vec<Arm>, String> {
        let arms = self.measure.twins.with_twins(self.arm.arms());
        match (self.arm == ProjectArmChoice::FindInFiles, self.queries.is_empty()) {
            (true, true) => return Err("--arm find-in-files needs --query: a run searches for each query".to_owned()),
            (false, false) => return Err("--query needs --arm find-in-files".to_owned()),
            (true, false) | (false, true) => {}
        }
        if self.directory.is_some() && self.arm != ProjectArmChoice::FindInFiles {
            return Err("--directory needs --arm find-in-files".to_owned());
        }
        if self.measure.cold && arms.iter().any(|arm| arm.kind == ArmKind::Project) {
            return Err(
                "--cold needs --arm empty-editor: the project arm restores the editor that its prime run saved, and a cold session has no prime run"
                    .to_owned(),
            );
        }
        if arms
            .iter()
            .any(|arm| arm.kind == ArmKind::EmptyEditor && arm.suppressed_plugin() == Some(AIR_PLUGIN_ID))
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
    CargoSync,
    FindInFiles,
}

impl ProjectArmChoice {
    /// The arms in the interleave order.
    pub(crate) fn arms(self) -> Vec<Arm> {
        match self {
            Self::Project => vec![Arm::PROJECT],
            Self::EmptyEditor => vec![Arm::EMPTY_EDITOR],
            Self::Both => vec![Arm::PROJECT, Arm::EMPTY_EDITOR],
            Self::CargoSync => vec![Arm::CARGO_SYNC],
            Self::FindInFiles => vec![Arm::FIND_IN_FILES],
        }
    }
}

/// Parses `--query`: a text that is not empty, has no space at its ends, and holds no `;` and no control character.
fn find_query(value: &str) -> Result<String, String> {
    if value.is_empty() || value.trim() != value {
        return Err(format!("the query `{value}` is empty or has a space at an end"));
    }
    if let Some(character) = value
        .chars()
        .find(|character| *character == FIND_QUERY_SEPARATOR || character.is_control())
    {
        return Err(format!(
            "the query `{value}` holds `{}`, which separates the queries of %findInFiles or ends its line",
            character.escape_default()
        ));
    }
    Ok(value.to_owned())
}

/// Parses `--directory`: a relative path without a space, which ends the path in the script, and without `..`.
fn find_directory(value: &str) -> Result<PathBuf, String> {
    let path = PathBuf::from(value);
    if value.is_empty() || path.is_absolute() || value.chars().any(|character| character.is_whitespace() || character.is_control()) {
        return Err(format!("the directory `{value}` must be a relative path without a space"));
    }
    if path.components().any(|component| component == std::path::Component::ParentDir) {
        return Err(format!("the directory `{value}` leaves the project"));
    }
    Ok(path)
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
