//! One run: the files that the IDE wrote into the run directory, the gate, and the metrics.
//!
//! [`collect`] reads only files. The launch writes the run directory and `replay` reads it again with the same call.

use std::collections::BTreeMap;
use std::path::Path;

use serde::{Deserialize, Serialize};

use crate::arm::Arm;
use crate::classes::{self, ClassCounts, Count};
use crate::files;
use crate::fus;
use crate::profile::{self, EdtProfile, FrameSamples};
use crate::stats::{self, StartupStats};
use crate::trace::{self, Trace};

/// The line of `idea.log` that tells that the IDE opened the welcome project.
pub(crate) const WELCOME_PROJECT_LOG_LINE: &str = "Opened the welcome screen project";

/// The metric keys of the FUS events.
pub(crate) const WELCOME_BECAME_VISIBLE: &str = "welcomeBecameVisible";
pub(crate) const FRAME_BECAME_VISIBLE: &str = "frameBecameVisible";
pub(crate) const FRAME_BECAME_INTERACTIVE: &str = "frameBecameInteractive";

/// The metric keys of the report.
pub(crate) const TOTAL_DURATION: &str = "totalDuration";
pub(crate) const CLASS_COUNT: &str = "classLoading.count";
pub(crate) const CLASS_TIME: &str = "classLoading.time";
pub(crate) const CLASS_EDT_TIME: &str = "classLoading.edtTime";
pub(crate) const PLUGIN_CLASSES: &str = "pluginClasses";

/// The platform spans. The metric key is the span name.
pub(crate) const PLATFORM_SPANS: [&str; 9] = [
    "bootstrap",
    "app initialization",
    "ProjectManager.openAsync",
    "project frame creating",
    "toolwindow creating",
    "toolwindow init pending tasks processing",
    "restoreEditors",
    "post open editors",
    "project post-startup dumb-aware activities",
];

/// The spans of the non-modal welcome screen, in the order of the digest. The metric key is the span name.
/// [`CREATE_CONTENT_PREFIX`] adds one metric per feature key, which the digest shows after the feature ids.
pub(crate) const WELCOME_SPANS: [&str; 9] = [
    "welcome screen painted",
    "welcome screen project opening",
    "welcome left panel creating",
    "welcome recent projects collecting",
    "welcome left toolbar first fill",
    "welcome right tab creating",
    "welcome right tab body: feature ids",
    "welcome right tab body: EDT build",
    "readme opening check",
];

/// The prefix of the per-feature spans of the right tab.
pub(crate) const CREATE_CONTENT_PREFIX: &str = "welcome right tab body: createContent ";

/// The metric keys of the second project of `open-project`.
pub(crate) const OPEN_FRAME: &str = "open: project frame creating";
pub(crate) const OPEN_EDITOR_PAINT: &str = "open: editor restoring till paint";
pub(crate) const OPEN_HIGHLIGHTED: &str = "open: editor highlighting completed";

/// The trace and report names of the second project.
const FRAME_SPAN: &str = "project frame creating";
const EDITOR_PAINT_SPAN: &str = "editor restoring till paint";
const HIGHLIGHTED_EVENT: &str = "editor highlighting completed";

/// The suffixes of the coroutine helper spans, which are no measurement.
const HELPER_SUFFIXES: [&str; 2] = [": scheduled", ": completing"];

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) enum RunKind {
    /// The first start of an arm. It fills the caches and counts for nothing.
    Prime,
    Measured,
}

/// What the controller saw while the IDE ran. The launch writes it, and `replay` reads it back from `result.json`.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct LaunchFacts {
    /// Why the run did not reach its event: an early exit or a timeout.
    pub(crate) failure: Option<String>,
    /// The quit fell back to a signal.
    pub(crate) terminated: bool,
    /// The exit code of the IDE process, when it had one.
    pub(crate) ide_exit: Option<i32>,
    /// The milliseconds from the start of the IDE to its event.
    pub(crate) waited_ms: u64,
    /// The microseconds since the epoch of the open request of `open-project`.
    pub(crate) open_request_us: Option<i64>,
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
    pub(crate) top_plugins: Vec<Count>,
    pub(crate) top_modules: Vec<Count>,
    /// The EDT samples, when the run has a profile.
    pub(crate) edt_samples: Option<u64>,
    pub(crate) edt_frames: Vec<FrameSamples>,
    /// The whole EDT profile, for the session summary. Not in `result.json`.
    #[serde(skip)]
    pub(crate) profile: Option<EdtProfile>,
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
        arm: id.arm,
        kind: id.kind,
        index: id.index,
        dir: id.dir_name(),
        launch,
        valid: false,
        reason: None,
        notes: Vec::new(),
        metrics: BTreeMap::new(),
        top_plugins: Vec::new(),
        top_modules: Vec::new(),
        edt_samples: None,
        edt_frames: Vec::new(),
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
    let events = match files::read_optional(&run_dir.join("fus.jsonl")) {
        Ok(Some(text)) => fus::parse(&text).map_err(|error| format!("fus.jsonl: {error:#}"))?,
        Ok(None) => return Err("no fus.jsonl".to_owned()),
        Err(error) => return Err(format!("{error:#}")),
    };
    gate(run_dir, record.arm, &events)?;
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
    let trace = read_file(run_dir, "opentelemetry.json", trace::parse, &mut record.notes)?;
    if trace.as_ref().is_some_and(|trace| trace.truncated) {
        record
            .notes
            .push("opentelemetry.json is truncated; the reader closed it".to_owned());
    }
    if let Some(stats) = &stats {
        metrics.insert(TOTAL_DURATION.to_owned(), as_f64(stats.total_duration));
        metrics.insert(CLASS_COUNT.to_owned(), as_f64(stats.class_loading.count));
        metrics.insert(CLASS_TIME.to_owned(), as_f64(stats.class_loading.time));
        metrics.insert(CLASS_EDT_TIME.to_owned(), as_f64(stats.edt_class_loading_ms()));
    }
    for name in PLATFORM_SPANS.iter().chain(WELCOME_SPANS.iter()) {
        if let Some(duration) = span_ms(trace.as_ref(), stats.as_ref(), name) {
            metrics.insert((*name).to_owned(), duration);
        }
    }
    if let Some(trace) = &trace {
        for span in &trace.spans {
            let name = span.operation_name.as_str();
            if name.starts_with(CREATE_CONTENT_PREFIX) && !HELPER_SUFFIXES.iter().any(|suffix| name.ends_with(suffix)) {
                metrics.entry(name.to_owned()).or_insert_with(|| micros_to_ms(span.duration));
            }
        }
    }
    if record.arm == Arm::OpenProject {
        open_project_metrics(record.launch.open_request_us, trace.as_ref(), stats.as_ref(), metrics)?;
    }

    // The class log and the profile add detail to a run. A shape error in one of them is a note, not a failed run.
    match read_file(run_dir, "plugin-classes.txt", classes::parse, &mut record.notes) {
        Ok(Some(counts)) => {
            metrics.insert(PLUGIN_CLASSES.to_owned(), counts.total as f64);
            record.top_plugins = ClassCounts::top(&counts.by_plugin, TOP_LIMIT);
            record.top_modules = ClassCounts::top(&counts.by_module, TOP_LIMIT);
        }
        Ok(None) => {}
        Err(reason) => record.notes.push(reason),
    }
    let profile_path = run_dir.join("cpu.collapsed");
    if profile_path.exists() {
        let parsed = files::read_text(&profile_path).and_then(|text| profile::parse(&text));
        match parsed {
            Ok(profile) => {
                record.edt_samples = Some(profile.samples);
                record.edt_frames = profile.top(TOP_LIMIT);
                if profile.all_samples > 0 && profile.samples == 0 {
                    record.notes.push("cpu.collapsed has no AWT-EventQueue stack".to_owned());
                }
                record.profile = Some(profile);
            }
            Err(error) => record.notes.push(format!("cpu.collapsed: {error:#}")),
        }
    }
    Ok(())
}

/// The gate: the welcome event of the arm, and the welcome project for a non-modal arm.
fn gate(run_dir: &Path, arm: Arm, events: &[fus::Event]) -> Result<(), String> {
    let expected = arm.expects_modal();
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
    if arm.opens_welcome_project() {
        let log = files::read_optional(&run_dir.join("log").join("idea.log")).map_err(|error| format!("{error:#}"))?;
        if !log.is_some_and(|log| log.contains(WELCOME_PROJECT_LOG_LINE)) {
            return Err(format!("log/idea.log has no line \"{WELCOME_PROJECT_LOG_LINE}\""));
        }
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
        return Err("no opentelemetry.json".to_owned());
    };
    if let Some(span) = trace.first_after(FRAME_SPAN, request_us) {
        metrics.insert(OPEN_FRAME.to_owned(), micros_to_ms(span.duration));
    }
    if let Some(span) = trace.first_after(EDITOR_PAINT_SPAN, request_us) {
        metrics.insert(OPEN_EDITOR_PAINT.to_owned(), micros_to_ms(span.duration));
    }
    let highlighted =
        highlighted_at_us(trace, stats, request_us).ok_or_else(|| format!("no \"{HIGHLIGHTED_EVENT}\" after the open request"))?;
    metrics.insert(OPEN_HIGHLIGHTED.to_owned(), micros_to_ms(highlighted - request_us));
    Ok(())
}

/// The microseconds since the epoch of the first `editor highlighting completed` after `request_us`: a span of the
/// trace, else an instant event of the report.
pub(crate) fn highlighted_at_us(trace: &Trace, stats: Option<&StartupStats>, request_us: i64) -> Option<i64> {
    if let Some(span) = trace.first_after(HIGHLIGHTED_EVENT, request_us) {
        return Some(span.start_time);
    }
    let origin = trace.origin_us()?;
    stats?
        .trace_events
        .iter()
        .filter(|event| event.name == HIGHLIGHTED_EVENT)
        .map(|event| origin + event.ts)
        .find(|at| *at >= request_us)
}

/// The duration of a span: the trace when it has the span, else the report item.
fn span_ms(trace: Option<&Trace>, stats: Option<&StartupStats>, name: &str) -> Option<f64> {
    trace
        .and_then(|trace| trace.first(name))
        .map(|span| micros_to_ms(span.duration))
        .or_else(|| stats.and_then(|stats| stats.item(name)).map(|item| as_f64(item.duration)))
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

/// Microseconds as milliseconds, to a tenth.
pub(crate) fn micros_to_ms(micros: i64) -> f64 {
    (as_f64(micros) / 100.0).round() / 10.0
}

/// A count or a duration as `f64`. The values stay far below 2^53.
const fn as_f64(value: i64) -> f64 {
    value as f64
}

#[cfg(test)]
mod tests;
