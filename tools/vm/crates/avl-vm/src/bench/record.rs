//! One run: the files that the IDE wrote into the run directory, the gate, and the metrics.
//!
//! [`collect`] reads only files. The launch writes the run directory and `replay` reads it again with the same call.

use std::collections::{BTreeMap, BTreeSet};
use std::path::Path;

use serde::{Deserialize, Serialize};

use super::arm::{Arm, ArmKind, Gate};
use super::classload::{self, ClassLoadLog, ClockAnchor, MAIN_CLASS, ProcessStart};
use super::files;
use super::fus;
use super::load::HostLoad;
use super::pluginlog::{self, Owner, PluginLog, Tally};
use super::profile::{self, EdtProfile, ExcludedSamples, FrameSamples, GATE_PROFILE, ProfileScope, WHOLE_PROFILE};
use super::session::{CLASS_LOAD_LOG, TRACE_FILE};
use super::stats::{self, StartupStats};
use super::timeline::QUIT_SPAN;
use super::trace::{self, Trace, as_f64, micros_to_ms, tenth};
use super::triggers;

/// The line of `idea.log` that tells that the IDE opened the welcome project.
pub(crate) const WELCOME_PROJECT_LOG_LINE: &str = "Opened the welcome screen project";

/// The metric keys of the FUS events.
pub(crate) const WELCOME_BECAME_VISIBLE: &str = "welcomeBecameVisible";
pub(crate) const FRAME_BECAME_VISIBLE: &str = "frameBecameVisible";
pub(crate) const FRAME_BECAME_INTERACTIVE: &str = "frameBecameInteractive";

/// The metric keys of the report. A start without a project has no end of its start-up, so its report, which the IDE
/// writes at the exit, can lack [`TOTAL_DURATION`] or hold a negative one. The record keeps neither, with a note.
pub(crate) const TOTAL_DURATION: &str = "totalDuration";
/// `jvm.loadingTime` of the report: the start of the process clock minus the start time of `RuntimeMXBean`.
pub(crate) const JVM_LOADING_TIME: &str = "jvm.loadingTime";
/// The start of the process clock on the JVM clock of the class-load log, in ms: the load of
/// [`classload::MAIN_CLASS`]. The class anchors use it, see [`classload::process_start`].
pub(crate) const JVM_PROCESS_START: &str = "jvm.processStart";
/// The class requests of `ClassPath`, which the report writes as `classLoading.count`. A request is not a class: one
/// class can need many requests.
pub(crate) const CLASS_REQUESTS: &str = "classLoading.requests";
pub(crate) const CLASS_TIME: &str = "classLoading.time";
pub(crate) const CLASS_EDT_TIME: &str = "classLoading.edtTime";

/// The lines of `plugin-classes.txt`: the classes that a plugin class loader defined.
pub(crate) const PLUGIN_CLASSES: &str = "pluginClasses";
/// The milliseconds that the IDE spent on the writes of `plugin-classes.txt`, from the trailer of a v2 log. A log
/// without the trailer gives no metric.
pub(crate) const PLUGIN_LOG_WRITE_MS: &str = "pluginLog.writeMs";
/// The milliseconds that the IDE spent on the capture of the activation stacks, from the same trailer.
pub(crate) const PLUGIN_LOG_STACK_MS: &str = "pluginLog.stackMs";
/// The class log of the plugin class loaders, which `plugin.classloader.debug` names. See [`pluginlog`].
pub(crate) const PLUGIN_CLASSES_FILE: &str = "plugin-classes.txt";

/// The metric keys of the JVM class-load log, see [`classload`]. A named class is a class that is not hidden.
pub(crate) const CLASSES_NAMED: &str = "classes.named";
pub(crate) const CLASSES_HIDDEN: &str = "classes.hidden";
/// The named classes from the CDS archive, the module image or the boot loader.
pub(crate) const CLASSES_JDK: &str = "classes.jdk";
/// The named classes from a jar or a directory URL, before the IDE class loaders run.
pub(crate) const CLASSES_JAR: &str = "classes.jar";
/// The named classes that a class loader defined and that no plugin owns: the classes of the core loader.
pub(crate) const CLASSES_PLATFORM: &str = "classes.platform";
/// The named classes that a plugin owns in `plugin-classes.txt`.
pub(crate) const CLASSES_PLUGINS: &str = "classes.plugins";

/// The anchor of the start of the quit, the span [`QUIT_SPAN`] of the trace.
pub(crate) const QUIT_ANCHOR: &str = "quit";

/// The anchors of the class counts, in the order of the start-up. Each one except [`QUIT_ANCHOR`] is a metric in
/// milliseconds from the process start. A run has the metric [`named_at`] for each anchor that it has.
pub(crate) const CLASS_ANCHORS: [&str; 6] = [
    FRAME_BECAME_VISIBLE,
    EMPTY_STATE_BUILT,
    WELCOME_BECAME_VISIBLE,
    WELCOME_SCREEN_PAINTED,
    EDITOR_HIGHLIGHTED,
    QUIT_ANCHOR,
];

/// The metric key of the named classes before `anchor`, such as `classes.named@editor highlighting completed`.
pub(crate) fn named_at(anchor: &str) -> String {
    format!("{CLASSES_NAMED}@{anchor}")
}

/// The named classes that the EDT loaded, the classes of the platform and of the plugins. A run needs a class-load log
/// that knows the EDT for it, see [`classload::Threads::knows_edt`].
pub(crate) const CLASSES_EDT: &str = "classes.edt";

/// The metric key of the named classes that the EDT loaded before `anchor`, such as `classes.edt@quit`.
pub(crate) fn edt_at(anchor: &str) -> String {
    format!("{CLASSES_EDT}@{anchor}")
}

/// The thread dump of the IDE that a run takes after its gate, with `jcmd <pid> Thread.print`. It names the Java
/// threads by the tid of the class-load log.
pub(crate) const THREAD_DUMP_FILE: &str = "threads.txt";

/// The platform spans. The metric key is the span name.
pub(crate) const PLATFORM_SPANS: [&str; 11] = [
    "bootstrap",
    "app initialization",
    "ProjectManager.openAsync",
    "project frame creating",
    "toolwindow creating",
    "toolwindow init pending tasks processing",
    "restoreEditors",
    "editor restoring",
    EDITOR_PAINT_SPAN,
    "post open editors",
    "project post-startup dumb-aware activities",
];

/// The span that ends when the non-modal welcome screen paints. It starts at the process start, so its duration is
/// the offset of the paint.
pub(crate) const WELCOME_SCREEN_PAINTED: &str = "welcome screen painted";

/// The spans of the non-modal welcome screen, in the order of the digest. The metric key is the span name, and its
/// value is the sum of the spans of the name, as the perf test of the welcome screen sums them
/// (`WelcomeScreenSpanPostProcessor`). [`CREATE_CONTENT_PREFIX`] adds one metric per feature key, which the digest
/// shows after the feature ids.
pub(crate) const WELCOME_SPANS: [&str; 11] = [
    WELCOME_SCREEN_PAINTED,
    "welcome screen project opening",
    "welcome left panel creating",
    "welcome recent projects collecting",
    "welcome left toolbar first fill",
    "welcome left panel painted",
    "welcome right tab creating",
    "welcome right tab body: feature ids",
    "welcome right tab body: EDT build",
    "welcome right tab painted",
    "readme opening check",
];

/// The prefix of the per-feature spans of the right tab. Each metric is the sum of the spans of its name, as for
/// [`WELCOME_SPANS`].
pub(crate) const CREATE_CONTENT_PREFIX: &str = "welcome right tab body: createContent ";

/// The spans of the modal welcome screen, in the order of the digest. Each metric is the sum of the spans of its name,
/// as for [`WELCOME_SPANS`].
pub(crate) const MODAL_SPANS: [&str; 6] = [
    "welcome frame EDT waiting",
    "welcome frame creating",
    "welcome menu bar creating",
    "welcome tabs creating",
    "welcome quick access panel creating",
    "welcome frame showing",
];

/// The spans whose end is a metric, in the order of the digest. See [`span_end`].
pub(crate) const END_SPANS: [&str; 13] = [
    "app initialization",
    "app initialized callback",
    "app frame created callback",
    "welcome screen project opening",
    "project prepare",
    "project add to opened",
    "toolwindow init command creation",
    "toolwindow creating",
    "toolwindow default pane registered hook",
    "welcome left panel creating",
    "restoreEditors",
    "frame allocator foreground",
    "welcome frame creating",
];

/// The metric key of the end of the first span `name`, such as `toolwindow creating end`: the milliseconds from the
/// process start to the end, on the clock of the anchors.
pub(crate) fn span_end(name: &str) -> String {
    format!("{name} end")
}

/// The metric keys of the second project of `open-project`.
pub(crate) const OPEN_FRAME: &str = "open: project frame creating";
pub(crate) const OPEN_EDITOR_PAINT: &str = "open: editor restoring till paint";
pub(crate) const OPEN_HIGHLIGHTED: &str = "open: editor highlighting completed";

/// The instant event of the highlighted editor, and the metric key of the project arm: the milliseconds from the
/// process start to the event.
pub(crate) const EDITOR_HIGHLIGHTED: &str = "editor highlighting completed";

/// The span of the Air composer that an empty editor area builds, from its start to the built card. The gate of the
/// empty-editor arm. Air writes it only as a span, because it ends after the start-up report.
pub(crate) const EMPTY_STATE_SPAN: &str = "air.emptyState.createComponent";

/// The tag that the IDE adds to a span that ended with an error, with the string value `true`.
const ERROR_TAG: &str = "error";

/// The metric key of the empty-editor arm: the milliseconds from the process start to the end of
/// [`EMPTY_STATE_SPAN`].
pub(crate) const EMPTY_STATE_BUILT: &str = "emptyStateBuilt";

/// The prefix of the spans of the Air empty-state composer. Each span is a metric with its duration.
pub(crate) const EMPTY_STATE_PREFIX: &str = "air.emptyState.";

/// The prefix of the prologue steps of the composer, which the digest shows after [`EMPTY_STATE_SPANS`].
pub(crate) const EMPTY_STATE_STEP_PREFIX: &str = "air.emptyState.prologue.";

/// The spans of the composer with a fixed name, in the order of the digest: the whole build, the off-EDT prologue,
/// the EDT build, and its two halves.
pub(crate) const EMPTY_STATE_SPANS: [&str; 5] = [
    EMPTY_STATE_SPAN,
    "air.emptyState.prologue",
    "air.emptyState.buildOnEdt",
    "air.emptyState.createContent",
    "air.emptyState.initializeContent",
];

/// The trace and report names of the second project.
const FRAME_SPAN: &str = "project frame creating";
const EDITOR_PAINT_SPAN: &str = "editor restoring till paint";

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) enum RunKind {
    /// The first start of an arm. It fills the caches and counts for nothing.
    Prime,
    Measured,
}

/// What the controller saw while the IDE ran. The launch writes it, and `replay` reads it back from `result.json`.
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct LaunchFacts {
    /// The digest of the generation that the IDE started from. A run of a session before the generation has none.
    #[serde(default)]
    pub(crate) dist_digest: Option<String>,
    /// The load of the host when the run started, so a noisy session shows.
    #[serde(default)]
    pub(crate) load: Option<HostLoad>,
    /// Why the run did not reach its event: an early exit or a timeout.
    pub(crate) failure: Option<String>,
    /// The quit fell back to a signal: SIGTERM to the process group, then SIGKILL.
    pub(crate) terminated: bool,
    /// The exit code of the IDE process, when it had one.
    pub(crate) ide_exit: Option<i32>,
    /// The milliseconds from the start of the IDE to its event.
    pub(crate) waited_ms: u64,
    /// The microseconds since the epoch of the open request of `open-project`.
    pub(crate) open_request_us: Option<i64>,
    /// Why the run did not stop the profiler at its gate, so it keeps the whole-run profile. A run without a profile,
    /// or one that stopped it, has none.
    #[serde(default)]
    pub(crate) profile_stop_failure: Option<String>,
    /// Why the run wrote no thread dump after its gate, see [`THREAD_DUMP_FILE`]. A run before the dump, and a run that
    /// wrote one, has none.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub(crate) thread_dump_failure: Option<String>,
}

/// One run.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct RunRecord {
    pub(crate) arm: Arm,
    pub(crate) kind: RunKind,
    /// From 1. A prime run has 0.
    pub(crate) index: u32,
    /// The run directory, relative to the session directory.
    pub(crate) dir: String,
    pub(crate) launch: LaunchFacts,
    /// The run passed its gate. Only a valid run has metrics.
    pub(crate) valid: bool,
    pub(crate) reason: Option<String>,
    /// What the run lacks without a failure: a missing file, a truncated trace.
    pub(crate) notes: Vec<String>,
    /// Milliseconds, or a count for the class metrics.
    pub(crate) metrics: BTreeMap<String, f64>,
    /// The class maps of the run. [`Summary::build`](super::summary::Summary::build) drops them, so only `result.json`
    /// holds them.
    #[serde(flatten)]
    pub(crate) class_maps: ClassMaps,
    /// The span of the run that its profile covers, when the run has a profile.
    #[serde(default)]
    pub(crate) profile_scope: Option<ProfileScope>,
    /// The samples of the profiler's own symbol parsing, which the profile leaves out, when the run has a profile.
    #[serde(default)]
    pub(crate) excluded_samples: Option<ExcludedSamples>,
    /// The EDT samples, when the run has a profile.
    pub(crate) edt_samples: Option<u64>,
    pub(crate) edt_frames: Vec<FrameSamples>,
    /// The class-loading triggers with the most samples, over all threads, when the run has a profile.
    #[serde(default)]
    pub(crate) triggers: Vec<FrameSamples>,
    /// The part of all samples that define a class, from 0 to 1, when the run has a profile with samples.
    #[serde(default)]
    pub(crate) define_share: Option<f64>,
    /// The whole EDT profile, for the session summary. Not in `result.json`.
    #[serde(skip)]
    pub(crate) profile: Option<EdtProfile>,
}

/// The class maps of a run: the JVM class-load log joined with `plugin-classes.txt`, else `plugin-classes.txt` alone.
/// A map before an anchor needs the class-load log, the plugin log and the report.
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ClassMaps {
    /// The named classes of each plugin over the whole run.
    #[serde(default, skip_serializing_if = "BTreeMap::is_empty")]
    pub(crate) classes_by_plugin: BTreeMap<String, u64>,
    /// The named classes of each plugin before each anchor of [`CLASS_ANCHORS`] that the run has, by the anchor.
    #[serde(default, skip_serializing_if = "BTreeMap::is_empty")]
    pub(crate) classes_by_plugin_at: BTreeMap<String, BTreeMap<String, u64>>,
    /// The named classes of each loader over the whole run, in the order of the loader.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub(crate) classes_by_loader: Vec<LoaderClasses>,
    /// The named classes of each loader before each anchor that the run has, by the anchor.
    #[serde(default, skip_serializing_if = "BTreeMap::is_empty")]
    pub(crate) classes_by_loader_at: BTreeMap<String, Vec<LoaderClasses>>,
    /// The first class of each loader and the stack that loaded it, in the order of the loader.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub(crate) activations: Vec<LoaderActivation>,
}

/// The named classes of one loader: the main module or a content module of a plugin.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct LoaderClasses {
    pub(crate) plugin: String,
    /// The content module. The main module of a plugin has none.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub(crate) module: Option<String>,
    pub(crate) classes: u64,
    /// The defines of the classes, when the plugin log times each one. A log of the old shape has none.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub(crate) times: Option<DefineTimes>,
}

/// The defines of the classes of a loader, by the thread that defined them. A time is the sum of the `findClass`
/// nanos that the plugin log writes.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct DefineTimes {
    pub(crate) edt_classes: u64,
    pub(crate) edt_nanos: u64,
    pub(crate) bg_nanos: u64,
}

impl LoaderClasses {
    fn of(owner: &Owner, tally: &Tally) -> Self {
        Self {
            plugin: owner.plugin.clone(),
            module: owner.module.clone(),
            classes: tally.classes,
            times: tally.is_timed().then_some(DefineTimes {
                edt_classes: tally.edt_classes,
                edt_nanos: tally.edt_nanos,
                bg_nanos: tally.bg_nanos,
            }),
        }
    }
}

/// The first class of one loader, when the JVM loaded it, and the stack that loaded it.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct LoaderActivation {
    pub(crate) plugin: String,
    /// The content module. The main module of a plugin has none.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub(crate) module: Option<String>,
    /// The class of the record with the stack, else the class of the first record of the loader.
    pub(crate) first_class: String,
    /// Milliseconds from the process start, to a tenth, of the first class of the loader in the class-load log. A run
    /// without the class-load log or without `jvm.loadingTime` has none.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub(crate) first_load_ms: Option<f64>,
    /// The frames, the top frame first. A plugin log of the old shape has none.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub(crate) stack: Vec<String>,
    /// The trigger frame of the stack, see [`triggers::trigger_frame`]. A loader without a stack has none.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub(crate) trigger: Option<String>,
}

/// The rows of `tallies`, in the order of the loader.
fn loader_rows(tallies: &BTreeMap<Owner, Tally>) -> Vec<LoaderClasses> {
    tallies.iter().map(|(owner, tally)| LoaderClasses::of(owner, tally)).collect()
}

/// The top lists of a run.
const TOP_LIMIT: usize = 10;

/// The identity of a run inside its session.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct RunId {
    pub(crate) arm: Arm,
    pub(crate) kind: RunKind,
    pub(crate) index: u32,
}

impl RunId {
    /// The directory name: `<arm>-prime` or `<arm>-run-NN`.
    pub(crate) fn dir_name(&self) -> String {
        match self.kind {
            RunKind::Prime => format!("{}-prime", self.arm.label()),
            RunKind::Measured => format!("{}-run-{:02}", self.arm.label(), self.index),
        }
    }

    /// The identity of a run directory name.
    pub(crate) fn parse(dir_name: &str) -> Option<Self> {
        if let Some(label) = dir_name.strip_suffix("-prime") {
            return Arm::from_label(label).map(|arm| Self {
                arm,
                kind: RunKind::Prime,
                index: 0,
            });
        }
        let (label, index) = dir_name.rsplit_once("-run-")?;
        let index = index.parse::<u32>().ok().filter(|index| *index > 0)?;
        Arm::from_label(label).map(|arm| Self {
            arm,
            kind: RunKind::Measured,
            index,
        })
    }
}

/// Reads one run directory and applies the gate.
pub(crate) fn collect(run_dir: &Path, id: &RunId, launch: LaunchFacts) -> RunRecord {
    let mut record = RunRecord {
        arm: id.arm.clone(),
        kind: id.kind,
        index: id.index,
        dir: id.dir_name(),
        launch,
        valid: false,
        reason: None,
        notes: Vec::new(),
        metrics: BTreeMap::new(),
        class_maps: ClassMaps::default(),
        profile_scope: None,
        excluded_samples: None,
        edt_samples: None,
        edt_frames: Vec::new(),
        triggers: Vec::new(),
        define_share: None,
        profile: None,
    };
    match measure(run_dir, &mut record) {
        Ok(()) => record.valid = true,
        Err(reason) => {
            record.reason = Some(reason);
            record.metrics.clear();
        }
    }
    record
}

/// Reads the files of a run into `record`. An error is the reason that the run is not valid.
fn measure(run_dir: &Path, record: &mut RunRecord) -> Result<(), String> {
    if let Some(failure) = &record.launch.failure {
        return Err(failure.clone());
    }
    let gate = record.arm.gate();
    let events = match files::read_optional(&run_dir.join("fus.jsonl")) {
        Ok(Some(text)) => fus::parse(&text).map_err(|error| format!("fus.jsonl: {error:#}"))?,
        // The highlighted gate and a span gate read no FUS event, so the welcome events are only extra metrics.
        Ok(None) if !matches!(gate, Gate::Welcome { .. }) => {
            record.notes.push("no fus.jsonl".to_owned());
            Vec::new()
        }
        Ok(None) => return Err("no fus.jsonl".to_owned()),
        Err(error) => return Err(format!("{error:#}")),
    };
    if let Gate::Welcome { modal } = gate {
        welcome_gate(run_dir, &record.arm, modal, &events)?;
    }
    let metrics = &mut record.metrics;
    for (key, id) in [
        (WELCOME_BECAME_VISIBLE, fus::WELCOME_BECAME_VISIBLE),
        (FRAME_BECAME_VISIBLE, fus::FRAME_BECAME_VISIBLE),
        (FRAME_BECAME_INTERACTIVE, fus::FRAME_BECAME_INTERACTIVE),
    ] {
        if let Some(duration) = fus::first(&events, id).and_then(fus::Event::duration_ms) {
            metrics.insert(key.to_owned(), duration);
        }
    }

    let stats = read_file(run_dir, "startup-stats.json", stats::parse, &mut record.notes)?;
    let trace = read_file(run_dir, TRACE_FILE, trace::parse, &mut record.notes)?;
    if trace.as_ref().is_some_and(|trace| trace.truncated) {
        record.notes.push(format!("{TRACE_FILE} is truncated; the reader closed it"));
    }
    if let Some(stats) = &stats {
        match stats.total_duration {
            Some(total_ms) if total_ms >= 0 => {
                metrics.insert(TOTAL_DURATION.to_owned(), as_f64(total_ms));
            }
            Some(_) => record.notes.push(
                "startup-stats.json has a negative totalDuration, as a start without a project writes it, so the run has none".to_owned(),
            ),
            None => record.notes.push("startup-stats.json has no totalDuration".to_owned()),
        }
        if let Some(loading_ms) = stats.jvm_loading_ms() {
            metrics.insert(JVM_LOADING_TIME.to_owned(), as_f64(loading_ms));
        }
        metrics.insert(CLASS_REQUESTS.to_owned(), as_f64(stats.class_loading.count));
        metrics.insert(CLASS_TIME.to_owned(), as_f64(stats.class_loading.time));
        metrics.insert(CLASS_EDT_TIME.to_owned(), as_f64(stats.edt_class_loading_ms()));
    }
    for name in PLATFORM_SPANS {
        if let Some(duration) = span_ms(trace.as_ref(), stats.as_ref(), name) {
            metrics.insert(name.to_owned(), duration);
        }
    }
    for name in summed_names(trace.as_ref()) {
        if let Some(sum) = span_sum(trace.as_ref(), stats.as_ref(), &name) {
            if let Some(note) = sum.note(&name) {
                record.notes.push(note);
            }
            metrics.insert(name, sum.ms);
        }
    }
    for name in END_SPANS {
        if let Some(end) = span_end_ms(trace.as_ref(), stats.as_ref(), name) {
            metrics.insert(span_end(name), end);
        }
    }
    if let Some(trace) = &trace {
        empty_state_metrics(trace, metrics);
    }
    if let Gate::Span { name, missing } = gate {
        span_gate(trace.as_ref(), name, missing)?;
    }
    if record.arm.kind == ArmKind::OpenProject {
        open_project_metrics(record.launch.open_request_us, trace.as_ref(), stats.as_ref(), metrics)?;
    }
    if gate == Gate::Highlighted {
        if trace.is_none() && stats.is_none() {
            return Err(format!("no {TRACE_FILE} and no startup-stats.json"));
        }
        // The prime run opens the file from the command line, so the report does not wait for its highlighting, and
        // the event seldom reaches a file. The prime counts for nothing, so the report alone passes its gate.
        match (highlighted_ms(trace.as_ref(), stats.as_ref()), record.kind) {
            (Some(highlighted), _) => {
                metrics.insert(EDITOR_HIGHLIGHTED.to_owned(), highlighted);
            }
            (None, RunKind::Prime) => {}
            (None, RunKind::Measured) => return Err(format!("no \"{EDITOR_HIGHLIGHTED}\" in the trace or the report")),
        }
    }

    // The class logs and the profile add detail to a run. A shape error in one of them is a note, not a failed run.
    let plugin_log = detail(
        read_file(run_dir, PLUGIN_CLASSES_FILE, pluginlog::parse, &mut record.notes),
        &mut record.notes,
    );
    if let Some(failure) = &record.launch.thread_dump_failure {
        record
            .notes
            .push(format!("no {THREAD_DUMP_FILE}, so a Java thread has no name: {failure}"));
    }
    let mut class_log = detail(
        read_file(run_dir, CLASS_LOAD_LOG, classload::parse, &mut record.notes),
        &mut record.notes,
    );
    // The dump names the Java threads, the EDT among them, which the class-load log names only when they end. A run
    // from before the dump has none, and its classes then have no thread.
    if let (Some(log), Ok(Some(dump))) = (class_log.as_mut(), files::read_optional(&run_dir.join(THREAD_DUMP_FILE))) {
        log.threads.add_dump(&dump);
    }
    let quit_ms = trace.as_ref().and_then(|trace| {
        let origin = trace.origin_us()?;
        trace.first(QUIT_SPAN).map(|span| span.offset_ms(origin))
    });
    class_metrics(record, plugin_log.as_ref(), class_log.as_ref(), stats.as_ref(), quit_ms);
    profile_metrics(run_dir, record);
    Ok(())
}

/// Reads the profile of a run: [`GATE_PROFILE`], else [`WHOLE_PROFILE`] with a note that names why the run did not stop
/// the profiler at its gate. A profile of another shape is a note.
fn profile_metrics(run_dir: &Path, record: &mut RunRecord) {
    let (name, scope) = if run_dir.join(GATE_PROFILE).exists() {
        (GATE_PROFILE, ProfileScope::Gate)
    } else if run_dir.join(WHOLE_PROFILE).exists() {
        (WHOLE_PROFILE, ProfileScope::WholeRun)
    } else {
        return;
    };
    match files::read_text(&run_dir.join(name)).and_then(|text| profile::parse(&text)) {
        Ok(profile) => {
            record.profile_scope = Some(scope);
            record.excluded_samples = Some(profile.excluded);
            record.edt_samples = Some(profile.samples);
            record.edt_frames = profile.top(TOP_LIMIT);
            record.triggers = profile.triggers.top(TOP_LIMIT);
            record.define_share = profile.define_share();
            if scope == ProfileScope::WholeRun {
                let why = record
                    .launch
                    .profile_stop_failure
                    .clone()
                    .unwrap_or_else(|| format!("the run has no {GATE_PROFILE}"));
                record.notes.push(format!(
                    "{WHOLE_PROFILE} is the whole-run profile, with the hold and the quit: {why}"
                ));
            }
            if profile.all_samples > 0 && profile.samples == 0 {
                record.notes.push(format!("{name} has no AWT-EventQueue stack"));
            }
            record.profile = Some(profile);
        }
        Err(error) => record.notes.push(format!("{name}: {error:#}")),
    }
}

/// The class metrics and the class maps of a run. The plugin log alone gives `pluginClasses`, the maps and the
/// activations. The class-load log gives the counts by source, and with the plugin log the owners of its classes. With
/// the start of the process clock, see [`classload::process_start`], it gives the counts before each anchor and the
/// first load of each loader: a bound on the process clock plus that start is a bound on the JVM clock of the log.
fn class_metrics(
    record: &mut RunRecord,
    plugin_log: Option<&PluginLog>,
    class_log: Option<&ClassLoadLog>,
    stats: Option<&StartupStats>,
    quit_ms: Option<f64>,
) {
    let maps = &mut record.class_maps;
    if let Some(plugin_log) = plugin_log {
        record.metrics.insert(PLUGIN_CLASSES.to_owned(), count(plugin_log.counts.total));
        if let Some(cost) = plugin_log.cost {
            record.metrics.insert(PLUGIN_LOG_WRITE_MS.to_owned(), nanos_to_ms(cost.write_nanos));
            record.metrics.insert(PLUGIN_LOG_STACK_MS.to_owned(), nanos_to_ms(cost.stack_nanos));
        }
        maps.classes_by_plugin.clone_from(&plugin_log.counts.by_plugin);
        maps.classes_by_loader = loader_rows(&plugin_log.counts.by_loader);
    }
    let Some(log) = class_log else {
        maps.activations = plugin_log.map(|owners| activations(owners, None)).unwrap_or_default();
        return;
    };
    let kinds = log.by_source_kind();
    let named = log.named().fold(0, |named, _| named + 1);
    for (key, value) in [
        (CLASSES_NAMED, named),
        (CLASSES_HIDDEN, kinds.hidden),
        (CLASSES_JDK, kinds.jdk),
        (CLASSES_JAR, kinds.jar),
    ] {
        record.metrics.insert(key.to_owned(), count(value));
    }
    if let Some(owners) = plugin_log {
        maps.classes_by_plugin = classload::by_plugin(log, owners, None);
        maps.classes_by_loader = loader_rows(&classload::by_loader(log, owners, None));
        let platform = classload::platform_count(log, owners, None);
        let plugins = maps.classes_by_plugin.values().sum();
        record.metrics.insert(CLASSES_PLATFORM.to_owned(), count(platform));
        record.metrics.insert(CLASSES_PLUGINS.to_owned(), count(plugins));
    }
    let start = classload::process_start(log, stats.and_then(StartupStats::jvm_loading_ms));
    match start.map(|start| start.anchor) {
        Some(ClockAnchor::MainClass) => {}
        Some(ClockAnchor::LoadingTime) => record.notes.push(format!(
            "class-load.log has no {MAIN_CLASS}, so the class anchors start the process clock at {}",
            ClockAnchor::LoadingTime.text()
        )),
        None if stats.is_some() => record.notes.push(format!(
            "class-load.log has no {MAIN_CLASS} and startup-stats.json has no jvm.loadingTime, so the class counts have no anchors"
        )),
        None => record.notes.push(format!(
            "class-load.log has no {MAIN_CLASS} and the run has no startup-stats.json, so the class counts have no anchors"
        )),
    }
    if let Some(start) = start.filter(|start| start.anchor == ClockAnchor::MainClass) {
        record.metrics.insert(JVM_PROCESS_START.to_owned(), start.uptime_ms);
    }
    let offset_ms = start.map(|start| start.uptime_ms);
    maps.activations = plugin_log
        .map(|owners| {
            let first_loads = offset_ms.map(|offset_ms| (classload::first_loads(log, owners), offset_ms));
            activations(owners, first_loads.as_ref())
        })
        .unwrap_or_default();
    let Some(offset_ms) = offset_ms else {
        return;
    };
    for anchor in CLASS_ANCHORS {
        let anchor_ms = if anchor == QUIT_ANCHOR {
            quit_ms
        } else {
            record.metrics.get(anchor).copied()
        };
        let Some(anchor_ms) = anchor_ms else {
            continue;
        };
        let bound = anchor_ms + offset_ms;
        record.metrics.insert(named_at(anchor), count(log.count_before(bound)));
        if let Some(edt) = log.edt_count_before(bound) {
            record.metrics.insert(edt_at(anchor), count(edt));
        }
        if let Some(owners) = plugin_log {
            maps.classes_by_plugin_at
                .insert(anchor.to_owned(), classload::by_plugin(log, owners, Some(bound)));
            maps.classes_by_loader_at
                .insert(anchor.to_owned(), loader_rows(&classload::by_loader(log, owners, Some(bound))));
        }
    }
}

/// The start of the process clock on the JVM clock of `log`, the class-load log of the run directory `run_dir`, see
/// [`classload::process_start`]. It reads `startup-stats.json` of the run only when the log has no [`MAIN_CLASS`]. A
/// report of another shape is an error.
pub(crate) fn process_start_of(run_dir: &Path, log: &ClassLoadLog) -> anyhow::Result<Option<ProcessStart>> {
    if let Some(start) = classload::process_start(log, None) {
        return Ok(Some(start));
    }
    let loading_ms = match files::read_optional(&run_dir.join("startup-stats.json"))? {
        Some(text) => stats::parse(&text)?.jvm_loading_ms(),
        None => None,
    };
    Ok(classload::process_start(log, loading_ms))
}

/// The activation of each loader of the plugin log, with its trigger frame. With the first loads on the JVM clock
/// and the offset of that clock, each one has its first load on the process clock.
fn activations(owners: &PluginLog, first_loads: Option<&(BTreeMap<Owner, f64>, f64)>) -> Vec<LoaderActivation> {
    owners
        .activations
        .iter()
        .map(|(owner, activation)| LoaderActivation {
            plugin: owner.plugin.clone(),
            module: owner.module.clone(),
            first_class: activation.first_class.clone(),
            first_load_ms: first_loads.and_then(|(loads, offset_ms)| loads.get(owner).map(|uptime_ms| tenth(uptime_ms - offset_ms))),
            stack: activation.stack.clone(),
            trigger: triggers::trigger_frame(owner, &activation.stack, owners),
        })
        .collect()
}

/// Milliseconds of nanos, to a tenth.
fn nanos_to_ms(nanos: u64) -> f64 {
    #[expect(clippy::cast_precision_loss, reason = "a sum of write nanos stays far below 2^53")]
    let nanos = nanos as f64;
    tenth(nanos / 1e6)
}

/// A class count as a metric value. The counts stay far below 2^53.
#[expect(clippy::cast_precision_loss, reason = "a class count stays far below 2^53")]
const fn count(value: u64) -> f64 {
    value as f64
}

/// The parsed file of a detail of the run, such as a class log. A file of another shape is a note, not a failed run.
fn detail<T>(read: Result<Option<T>, String>, notes: &mut Vec<String>) -> Option<T> {
    read.unwrap_or_else(|reason| {
        notes.push(reason);
        None
    })
}

/// The welcome gate: the welcome event with the `is_modal` of the arm, and the welcome project for a non-modal arm.
fn welcome_gate(run_dir: &Path, arm: &Arm, expected: bool, events: &[fus::Event]) -> Result<(), String> {
    let visible: Vec<&fus::Event> = events
        .iter()
        .filter(|event| event.group.id == fus::WELCOME_GROUP && event.event.id == fus::WELCOME_BECAME_VISIBLE)
        .collect();
    if visible.is_empty() {
        return Err(format!("no {} event in fus.jsonl", fus::WELCOME_BECAME_VISIBLE));
    }
    if !visible.iter().any(|event| event.is_modal() == Some(expected)) {
        let seen: Vec<String> = visible
            .iter()
            .map(|event| event.is_modal().map_or_else(|| "absent".to_owned(), |modal| modal.to_string()))
            .collect();
        return Err(format!(
            "{} has is_modal={}, and the {} arm expects is_modal={expected}",
            fus::WELCOME_BECAME_VISIBLE,
            seen.join(","),
            arm.label()
        ));
    }
    if !expected {
        let log = files::read_optional(&run_dir.join("log").join("idea.log")).map_err(|error| format!("{error:#}"))?;
        if !log.is_some_and(|log| log.contains(WELCOME_PROJECT_LOG_LINE)) {
            return Err(format!("log/idea.log has no line \"{WELCOME_PROJECT_LOG_LINE}\""));
        }
    }
    Ok(())
}

/// The span gate: the first span `name` of the trace, ended without an error and before the quit. The IDE ends the
/// span of a build that threw or that the quit cancelled too, so its end is when the build stopped, not when it
/// finished. A build that threw has the tag [`ERROR_TAG`]. A cancelled build has no tag, and its end is at or after
/// the start of [`QUIT_SPAN`].
fn span_gate(trace: Option<&Trace>, name: &str, missing: &str) -> Result<(), String> {
    let Some(trace) = trace else {
        return Err(format!("no {TRACE_FILE}, so no {name} span"));
    };
    let Some(span) = trace.first(name) else {
        return Err(format!("no {name} span in {TRACE_FILE}: {missing}"));
    };
    if span.tag(ERROR_TAG) == Some("true") {
        return Err(format!("the {name} span ended with an error"));
    }
    if let Some(quit) = trace.first(QUIT_SPAN)
        && span.start_time + span.duration >= quit.start_time
    {
        return Err(format!(
            "the {name} span did not end before the {QUIT_SPAN} span started, so the quit cut it short"
        ));
    }
    Ok(())
}

/// The metrics of the second project, measured from the open request.
fn open_project_metrics(
    request_us: Option<i64>,
    trace: Option<&Trace>,
    stats: Option<&StartupStats>,
    metrics: &mut BTreeMap<String, f64>,
) -> Result<(), String> {
    let Some(request_us) = request_us else {
        return Err("result.json has no open request time".to_owned());
    };
    let Some(trace) = trace else {
        return Err(format!("no {TRACE_FILE}"));
    };
    if let Some(span) = trace.first_after(FRAME_SPAN, request_us) {
        metrics.insert(OPEN_FRAME.to_owned(), micros_to_ms(span.duration));
    }
    if let Some(span) = trace.first_after(EDITOR_PAINT_SPAN, request_us) {
        metrics.insert(OPEN_EDITOR_PAINT.to_owned(), micros_to_ms(span.duration));
    }
    let highlighted =
        highlighted_at_us(trace, stats, request_us).ok_or_else(|| format!("no \"{EDITOR_HIGHLIGHTED}\" after the open request"))?;
    metrics.insert(OPEN_HIGHLIGHTED.to_owned(), micros_to_ms(highlighted - request_us));
    Ok(())
}

/// The microseconds since the epoch of the first `editor highlighting completed` after `request_us`: a span of the
/// trace, else an instant event of the report.
pub(crate) fn highlighted_at_us(trace: &Trace, stats: Option<&StartupStats>, request_us: i64) -> Option<i64> {
    if let Some(span) = trace.first_after(EDITOR_HIGHLIGHTED, request_us) {
        return Some(span.start_time);
    }
    let origin = trace.origin_us()?;
    stats?
        .trace_events
        .iter()
        .filter(|event| event.name == EDITOR_HIGHLIGHTED)
        .map(|event| origin + event.ts)
        .find(|at| *at >= request_us)
}

/// The milliseconds from the process start to the first `editor highlighting completed`. A trace with a span gives
/// the origin, see [`highlighted_at_us`]. Without one, the report alone gives the time of its instant event.
pub(crate) fn highlighted_ms(trace: Option<&Trace>, stats: Option<&StartupStats>) -> Option<f64> {
    if let Some(trace) = trace
        && let Some(origin) = trace.origin_us()
    {
        return highlighted_at_us(trace, stats, origin).map(|at| micros_to_ms(at - origin));
    }
    stats?
        .trace_events
        .iter()
        .find(|event| event.name == EDITOR_HIGHLIGHTED)
        .map(|event| micros_to_ms(event.ts))
}

/// The metrics of the Air empty-state composer: the duration of the first span of each name under
/// [`EMPTY_STATE_PREFIX`], and [`EMPTY_STATE_BUILT`], the end of the first [`EMPTY_STATE_SPAN`]. A composer that the
/// IDE builds again, after an editor closed, adds no metric: the first span of a name counts.
fn empty_state_metrics(trace: &Trace, metrics: &mut BTreeMap<String, f64>) {
    for span in &trace.spans {
        let name = span.operation_name.as_str();
        if name.starts_with(EMPTY_STATE_PREFIX)
            && !span.is_helper()
            && !metrics.contains_key(name)
            && let Some(first) = trace.first(name)
        {
            metrics.insert(name.to_owned(), micros_to_ms(first.duration));
        }
    }
    if let (Some(origin), Some(span)) = (trace.origin_us(), trace.first(EMPTY_STATE_SPAN)) {
        metrics.insert(EMPTY_STATE_BUILT.to_owned(), span.end_ms(origin));
    }
}

/// The duration of a span: the trace when it has the span, else the report item.
fn span_ms(trace: Option<&Trace>, stats: Option<&StartupStats>, name: &str) -> Option<f64> {
    trace
        .and_then(|trace| trace.first(name))
        .map(|span| micros_to_ms(span.duration))
        .or_else(|| stats.and_then(|stats| stats.item(name)).map(|item| as_f64(item.duration)))
}

/// The names whose metric is a sum, see [`span_sum`]: [`WELCOME_SPANS`], [`MODAL_SPANS`], and each name of the trace
/// under [`CREATE_CONTENT_PREFIX`] that is not a helper twin, in name order.
fn summed_names(trace: Option<&Trace>) -> Vec<String> {
    let features: BTreeSet<&str> = trace
        .into_iter()
        .flat_map(|trace| &trace.spans)
        .filter(|span| span.operation_name.starts_with(CREATE_CONTENT_PREFIX) && !span.is_helper())
        .map(|span| span.operation_name.as_str())
        .collect();
    WELCOME_SPANS
        .into_iter()
        .chain(MODAL_SPANS)
        .chain(features)
        .map(str::to_owned)
        .collect()
}

/// The durations of the spans of one name, summed.
struct SpanSum {
    /// Milliseconds, to a tenth.
    ms: f64,
    /// The spans of the name.
    count: usize,
    /// The file that has them.
    file: &'static str,
}

impl SpanSum {
    /// The note of a name that occurs more than once, whose metric is thus a sum of several spans.
    fn note(&self, name: &str) -> Option<String> {
        (self.count > 1).then(|| format!("{} has {} \"{name}\" spans; the metric is their sum", self.file, self.count))
    }
}

/// The sum of the durations of the spans `name`: the spans of the trace when it has the name, else the items of the
/// report. A helper twin has a name of its own, so it never counts.
fn span_sum(trace: Option<&Trace>, stats: Option<&StartupStats>, name: &str) -> Option<SpanSum> {
    let spans: Vec<i64> = trace
        .into_iter()
        .flat_map(|trace| &trace.spans)
        .filter(|span| span.operation_name == name)
        .map(|span| span.duration)
        .collect();
    if !spans.is_empty() {
        return Some(SpanSum {
            ms: micros_to_ms(spans.iter().sum()),
            count: spans.len(),
            file: TRACE_FILE,
        });
    }
    let items: Vec<i64> = stats
        .into_iter()
        .flat_map(|stats| &stats.items)
        .filter(|item| item.name == name)
        .map(|item| item.duration)
        .collect();
    (!items.is_empty()).then(|| SpanSum {
        ms: as_f64(items.iter().sum()),
        count: items.len(),
        file: "startup-stats.json",
    })
}

/// The end of the first span `name` in milliseconds from the process start: the trace when it has the span, else the
/// report item, whose times are on the same clock. [`EMPTY_STATE_BUILT`] is such an end too.
fn span_end_ms(trace: Option<&Trace>, stats: Option<&StartupStats>, name: &str) -> Option<f64> {
    trace
        .and_then(|trace| Some(trace.first(name)?.end_ms(trace.origin_us()?)))
        .or_else(|| {
            stats
                .and_then(|stats| stats.item(name))
                .map(|item| as_f64(item.start + item.duration))
        })
}

/// Reads and parses an optional file of the run. A missing file is a note. A file of another shape is the reason
/// that the run is not valid.
fn read_file<T>(
    run_dir: &Path,
    name: &str,
    parse: impl Fn(&str) -> anyhow::Result<T>,
    notes: &mut Vec<String>,
) -> Result<Option<T>, String> {
    match files::read_optional(&run_dir.join(name)) {
        Ok(Some(text)) => parse(&text).map(Some).map_err(|error| format!("{name}: {error:#}")),
        Ok(None) => {
            notes.push(format!("no {name}"));
            Ok(None)
        }
        Err(error) => Err(format!("{error:#}")),
    }
}

#[cfg(test)]
mod tests;
