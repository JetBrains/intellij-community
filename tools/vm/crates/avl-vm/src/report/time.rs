//! `report time`: where the time of a run went.
//!
//! Three kinds of files answer it, and the verb reads each one the way its writer wrote it:
//!
//! - the run journal, `<runtime root>/runs/<runId>/events.ndjson`: the reports of the iterations, the launches and the
//!   exits of the lane IDE, and the trace zips;
//! - the report of each iteration: its duration and the duration of each test case;
//! - the spans of each trace bundle: the `restart` span of each relaunch, and the self time of every lane span.
//!
//! A file that cannot be read is a note of the answer, and the rest is still measured: the answer is evidence about a
//! run, and one lost zip must not hide the other numbers.

use std::collections::{BTreeMap, BTreeSet};
use std::fmt::Write as _;
use std::fs::{self, File};
use std::io::{BufRead, BufReader, Read};
use std::path::{Path, PathBuf};

use avl_base::format::seconds;
use avl_base::journal::{self, FILE_NAME};
use avl_base::{Exit, Outcome, Refusal};
use avl_trace::otlp::{IDE_SPAN_KIND, Span, attr, decode_traces_line, lookup_str};
use avl_trace::protocol::SpanKind;
use avl_trace_tools::discover::VM_RUNS_DIR;
use avl_wire::daemon::{RunEventKind, decode_run_event};
use avl_wire::progress::{IterationReported, Kind, Record, TraceReady};
use avl_wire::report::{RunReport, decode_run_report};
use serde::Serialize;

#[cfg(test)]
mod tests;

/// The long help of `report time`.
pub(super) const LONG_ABOUT: &str = "Reads the journal of a run, the report of each of its iterations and the spans of \
its trace bundles, and answers where the time went: the total, the time inside the test cases and outside them, the \
launches of the lane IDE, the time of the relaunches (the restart spans), the ten longest test cases, and the ten lane \
spans with the most self time.

The argument is a run id, the directory name under <runtime root>/runs, or an iteration id, which names that iteration \
of the newest run that reported it. With --baseline another run or iteration, the answer adds the ratio of each test \
class, this run over the baseline, and the median ratio over the classes that took more than 5 s in the baseline. \
It needs no worker and no lease.";

/// The title of the root span of a bundle in the top list: its self time is the scenario outside every lane span.
const ROOT_TITLE: &str = "[scenario] outside every lane span";

/// How many test cases and spans each top list holds.
const TOP: usize = 10;

/// The classes that the median ratio is taken over took more than this in the baseline.
const MEDIAN_FLOOR_MS: f64 = 5_000.0;

/// The arguments of `report time`.
#[derive(clap::Args, Debug)]
pub(crate) struct TimeArgs {
    /// A run id, or an iteration id.
    #[arg(value_name = "RUN_OR_ITERATION")]
    pub(crate) run: String,
    /// Another run id or iteration id: the answer adds the ratio of each test class against it.
    #[arg(long, value_name = "RUN_OR_ITERATION")]
    pub(crate) baseline: Option<String>,
}

/// The answer of `report time`.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct TimeReport {
    pub(crate) run: RunTime,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(crate) baseline: Option<Baseline>,
}

/// Where the time of one run, or of one iteration of it, went.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct RunTime {
    pub(crate) run_id: String,
    /// The iterations measured, in the order the journal reported them.
    pub(crate) iterations: Vec<String>,
    /// The sum of the durations of the iteration reports.
    pub(crate) total_ms: f64,
    /// The sum of the durations of the test cases.
    pub(crate) inside_cases_ms: f64,
    /// The total less the time inside the test cases: the suite prologues, the factories and every gap between two
    /// cases.
    pub(crate) outside_cases_ms: f64,
    pub(crate) ide_launches: usize,
    pub(crate) ide_exits: usize,
    /// The `restart` spans: a restart a scenario asked for and a recycle.
    pub(crate) relaunches: Relaunches,
    pub(crate) top_cases: Vec<CaseTime>,
    pub(crate) top_spans: Vec<SpanTime>,
    /// The test classes by their time, the sum of their cases.
    pub(crate) classes: BTreeMap<String, f64>,
    pub(crate) bundles: usize,
    /// What could not be read, by file.
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub(crate) notes: Vec<String>,
}

/// The relaunches that the trace bundles show.
#[derive(Clone, Debug, Default, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Relaunches {
    pub(crate) count: usize,
    pub(crate) total_ms: f64,
    /// By span key: `restart:ide` and `restart:recycle`.
    pub(crate) by_key: BTreeMap<String, KeyTime>,
    /// The `relaunch` spans by key, such as `relaunch:quit/exit` or `relaunch:ready/phase`: where the time of the
    /// relaunches went.
    pub(crate) parts: BTreeMap<String, KeyTime>,
}

/// The count and the time of the spans of one key.
#[derive(Clone, Debug, Default, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct KeyTime {
    pub(crate) count: usize,
    pub(crate) total_ms: f64,
}

/// One test case and its duration.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct CaseTime {
    pub(crate) class_name: String,
    pub(crate) name: String,
    pub(crate) duration_ms: f64,
}

/// One lane span and its self time: its duration less the time of the lane spans under it.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SpanTime {
    pub(crate) scenario: String,
    /// The span key, `<kind>:<id>`, or `scenario` for the root of the bundle.
    pub(crate) key: String,
    pub(crate) title: String,
    pub(crate) self_ms: f64,
    pub(crate) total_ms: f64,
}

/// This run against a baseline, class by class.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Baseline {
    pub(crate) run_id: String,
    pub(crate) iterations: Vec<String>,
    pub(crate) total_ms: f64,
    /// The classes of both runs, the largest ratio first.
    pub(crate) classes: Vec<ClassRatio>,
    /// The median ratio over the classes that took more than 5 s in the baseline, or `None` when none did.
    pub(crate) median_ratio: Option<f64>,
    /// How many classes the median is taken over.
    pub(crate) median_classes: usize,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub(crate) only_in_run: Vec<String>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub(crate) only_in_baseline: Vec<String>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub(crate) notes: Vec<String>,
}

/// One class of both runs.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ClassRatio {
    pub(crate) class_name: String,
    pub(crate) duration_ms: f64,
    pub(crate) baseline_ms: f64,
    /// This run over the baseline, or `None` when the baseline took no time.
    pub(crate) ratio: Option<f64>,
}

/// Answers `report time` from the files under `runtime_root`.
pub(crate) fn command_time(runtime_root: &Path, args: &TimeArgs) -> Result<Outcome, Refusal> {
    let run = measure(&RunFiles::read(runtime_root, &args.run)?);
    let baseline = match &args.baseline {
        Some(name) => Some(compare(&run, &measure(&RunFiles::read(runtime_root, name)?))),
        None => None,
    };
    let answer = TimeReport { run, baseline };
    Outcome::new(&answer, answer.text())
}

// --- the files ---------------------------------------------------------------------------------------------------

/// The files of one run, or of one iteration of it, as read.
struct RunFiles {
    run_id: String,
    reports: Vec<(String, RunReport)>,
    ide_launches: usize,
    ide_exits: usize,
    /// The trace bundles: a zip and the directory of the bundle inside it, ending in `/`.
    bundles: BTreeSet<(PathBuf, String)>,
    notes: Vec<String>,
}

impl RunFiles {
    /// Reads the run that `name` names: a run id, or an iteration id of the newest run that reported it.
    fn read(runtime_root: &Path, name: &str) -> Result<Self, Refusal> {
        let run_dir = journal::run_dir(runtime_root, name)?;
        let journal = run_dir.join(FILE_NAME);
        if journal.is_file() {
            return read_journal(name, &journal, None);
        }
        let Some((run_id, journal)) = find_iteration(runtime_root, name) else {
            return Err(Refusal::new(
                "report_run_unknown",
                Exit::NO_INPUT,
                format!(
                    "no run and no iteration is named '{name}' under {}: give a directory name of it, or an iteration \
                     id that a journal there reported",
                    runtime_root.join(VM_RUNS_DIR).display()
                ),
            ));
        };
        read_journal(&run_id, &journal, Some(name))
    }
}

/// The run whose journal reported the iteration `iteration_id`, the newest one when several did.
fn find_iteration(runtime_root: &Path, iteration_id: &str) -> Option<(String, PathBuf)> {
    let entries = fs::read_dir(runtime_root.join(VM_RUNS_DIR)).ok()?;
    let mut found: Vec<(std::time::SystemTime, String, PathBuf)> = Vec::new();
    for entry in entries.filter_map(Result::ok) {
        let journal = entry.path().join(FILE_NAME);
        let Ok(modified) = fs::metadata(&journal).and_then(|metadata| metadata.modified()) else {
            continue;
        };
        let reports = journal_records(&journal).into_iter().filter_map(|record| reported(&record));
        if reports.into_iter().any(|reported| reported.iteration_id == iteration_id) {
            found.push((modified, entry.file_name().to_string_lossy().into_owned(), journal));
        }
    }
    found
        .into_iter()
        .max_by(|a, b| a.0.cmp(&b.0))
        .map(|(_, run_id, journal)| (run_id, journal))
}

/// Every record of a journal that decodes. A journal of a run in flight ends with a partial line, which is skipped.
fn journal_records(journal: &Path) -> Vec<Record> {
    let Ok(file) = File::open(journal) else {
        return Vec::new();
    };
    BufReader::new(file)
        .split(b'\n')
        .map_while(Result::ok)
        .filter_map(|line| serde_json::from_slice::<Record>(&line).ok())
        .collect()
}

fn reported(record: &Record) -> Option<IterationReported> {
    if record.event != Kind::IterationReported {
        return None;
    }
    serde_json::from_str(record.data.as_ref()?.get()).ok()
}

/// Reads the journal of `run_id`, and of the iteration `only` when it is set.
///
/// A record of the daemon belongs to the iteration that the last `runStarted` of its worker named, so the launches of
/// the IDE are counted for the iteration that made them.
fn read_journal(run_id: &str, journal: &Path, only: Option<&str>) -> Result<RunFiles, Refusal> {
    if !journal.is_file() {
        return Err(Refusal::new(
            "report_run_unknown",
            Exit::NO_INPUT,
            format!("the run journal {} is not a file", journal.display()),
        ));
    }
    let mut files = RunFiles {
        run_id: run_id.to_owned(),
        reports: Vec::new(),
        ide_launches: 0,
        ide_exits: 0,
        bundles: BTreeSet::new(),
        notes: Vec::new(),
    };
    let wanted = |iteration: Option<&str>| only.is_none_or(|only| iteration == Some(only));
    let mut iteration_of_worker: BTreeMap<String, String> = BTreeMap::new();
    for record in journal_records(journal) {
        let worker = record.worker.clone().unwrap_or_default();
        match record.event {
            Kind::Run => {
                let Some(daemon) = record.data.as_ref().and_then(|data| decode_run_event(data.get().as_bytes()).ok()) else {
                    continue;
                };
                match daemon.kind {
                    RunEventKind::RunStarted(started) => {
                        iteration_of_worker.insert(worker, started.iteration_id);
                    }
                    RunEventKind::IdeLaunched(_) if wanted(iteration_of_worker.get(&worker).map(String::as_str)) => {
                        files.ide_launches += 1;
                    }
                    RunEventKind::IdeExited(_) if wanted(iteration_of_worker.get(&worker).map(String::as_str)) => files.ide_exits += 1,
                    _ => {}
                }
            }
            Kind::IterationReported => {
                let Some(reported) = reported(&record) else {
                    continue;
                };
                if !wanted(Some(&reported.iteration_id)) {
                    continue;
                }
                match read_report(Path::new(&reported.report_path)) {
                    Ok(report) => {
                        for archive in &report.trace_archives {
                            for bundle in &archive.bundles {
                                files.bundles.insert((PathBuf::from(&archive.path), bundle.entry.clone()));
                            }
                        }
                        files.reports.push((reported.iteration_id, report));
                    }
                    Err(note) => files.notes.push(note),
                }
            }
            Kind::TraceReady => {
                let Some(ready) = record
                    .data
                    .as_ref()
                    .and_then(|data| serde_json::from_str::<TraceReady>(data.get()).ok())
                else {
                    continue;
                };
                if wanted(Some(&ready.iteration_id)) {
                    files.bundles.insert((PathBuf::from(ready.zip), ready.entry));
                }
            }
            _ => {}
        }
    }
    if files.reports.is_empty() {
        files.notes.push(format!("the journal {} reported no iteration", journal.display()));
    }
    Ok(files)
}

fn read_report(path: &Path) -> Result<RunReport, String> {
    let bytes = fs::read(path).map_err(|error| format!("cannot read the report {}: {error}", path.display()))?;
    decode_run_report(&bytes).map_err(|error| format!("the report {} is not readable: {}", path.display(), error.message))
}

// --- the measurement ---------------------------------------------------------------------------------------------

/// One lane span of a bundle, as the measurement needs it.
struct LaneSpan {
    id: String,
    parent: String,
    key: String,
    kind: Option<String>,
    title: String,
    duration_ms: f64,
}

fn measure(files: &RunFiles) -> RunTime {
    let mut notes = files.notes.clone();
    let total_ms: f64 = files.reports.iter().map(|(_, report)| report.duration_ms).sum();
    let mut cases: Vec<CaseTime> = Vec::new();
    let mut classes: BTreeMap<String, f64> = BTreeMap::new();
    for (_, report) in &files.reports {
        for case in report.suites.iter().flat_map(|suite| &suite.cases) {
            *classes.entry(case.class_name.clone()).or_default() += case.duration_ms;
            cases.push(CaseTime {
                class_name: case.class_name.clone(),
                name: case.name.clone(),
                duration_ms: case.duration_ms,
            });
        }
    }
    let inside_cases_ms: f64 = cases.iter().map(|case| case.duration_ms).sum();
    cases.sort_by(|a, b| b.duration_ms.total_cmp(&a.duration_ms));
    cases.truncate(TOP);

    let mut relaunches = Relaunches::default();
    let mut spans: Vec<SpanTime> = Vec::new();
    for (zip, entry) in &files.bundles {
        let lane = match read_lane_spans(zip, entry) {
            Ok(lane) => lane,
            Err(note) => {
                notes.push(note);
                continue;
            }
        };
        let scenario = lane.iter().find(|span| span.parent.is_empty()).map_or_else(
            || entry.trim_end_matches('/').rsplit('/').next().unwrap_or(entry).to_owned(),
            |root| root.title.clone(),
        );
        let mut children: BTreeMap<&str, f64> = BTreeMap::new();
        for span in &lane {
            *children.entry(span.parent.as_str()).or_default() += span.duration_ms;
        }
        for span in &lane {
            if span.kind.as_deref() == Some(SpanKind::Restart.as_str()) {
                relaunches.count += 1;
                relaunches.total_ms += span.duration_ms;
                let by_key = relaunches.by_key.entry(span.key.clone()).or_default();
                by_key.count += 1;
                by_key.total_ms += span.duration_ms;
            }
            if span.kind.as_deref() == Some(SpanKind::Relaunch.as_str()) {
                let part = relaunches.parts.entry(span.key.clone()).or_default();
                part.count += 1;
                part.total_ms += span.duration_ms;
            }
            let under = children.get(span.id.as_str()).copied().unwrap_or(0.0);
            // The self time of the root is the time of the scenario outside every lane span.
            let title = if span.parent.is_empty() {
                ROOT_TITLE.to_owned()
            } else {
                span.title.clone()
            };
            spans.push(SpanTime {
                scenario: scenario.clone(),
                key: span.key.clone(),
                title,
                self_ms: (span.duration_ms - under).max(0.0),
                total_ms: span.duration_ms,
            });
        }
    }
    spans.sort_by(|a, b| b.self_ms.total_cmp(&a.self_ms));
    spans.truncate(TOP);
    RunTime {
        run_id: files.run_id.clone(),
        iterations: files.reports.iter().map(|(iteration, _)| iteration.clone()).collect(),
        total_ms,
        inside_cases_ms,
        outside_cases_ms: (total_ms - inside_cases_ms).max(0.0),
        ide_launches: files.ide_launches,
        ide_exits: files.ide_exits,
        relaunches,
        top_cases: cases,
        top_spans: spans,
        classes,
        bundles: files.bundles.len(),
        notes,
    }
}

/// The lane spans of the bundle at `entry` of `zip`: every span but those of the IDE, whose spans overlap.
fn read_lane_spans(zip: &Path, entry: &str) -> Result<Vec<LaneSpan>, String> {
    let name = format!("{entry}{}", avl_trace::bundle::SPANS_FILE);
    let unreadable = |error: &dyn std::fmt::Display| format!("cannot read {name} of {}: {error}", zip.display());
    let file = File::open(zip).map_err(|error| unreadable(&error))?;
    let mut archive = zip::ZipArchive::new(file).map_err(|error| unreadable(&error))?;
    let mut bytes = Vec::new();
    archive
        .by_name(&name)
        .map_err(|error| unreadable(&error))?
        .read_to_end(&mut bytes)
        .map_err(|error| unreadable(&error))?;
    let mut lane = Vec::new();
    for line in bytes.split(|byte| *byte == b'\n').filter(|line| !line.is_empty()) {
        let traces = decode_traces_line(line).map_err(|error| unreadable(&error))?;
        let spans = traces
            .resource_spans
            .into_iter()
            .flat_map(|resource| resource.scope_spans)
            .flat_map(|scope| scope.spans);
        lane.extend(spans.filter_map(lane_span));
    }
    Ok(lane)
}

fn lane_span(span: Span) -> Option<LaneSpan> {
    let kind = lookup_str(&span.attributes, attr::SPAN_KIND).map(str::to_owned);
    if kind.as_deref() == Some(IDE_SPAN_KIND) {
        return None;
    }
    let start = span.start_time_unix_nano.0;
    let end = span.end_time_unix_nano.0;
    let key = if span.parent_span_id.is_empty() {
        "scenario".to_owned()
    } else {
        lookup_str(&span.attributes, attr::SPAN_KEY).unwrap_or_default().to_owned()
    };
    Some(LaneSpan {
        id: span.span_id,
        parent: span.parent_span_id,
        key,
        kind,
        title: span.name,
        duration_ms: end.saturating_sub(start) as f64 / 1_000_000.0,
    })
}

fn compare(run: &RunTime, baseline: &RunTime) -> Baseline {
    let mut classes: Vec<ClassRatio> = run
        .classes
        .iter()
        .filter_map(|(class_name, duration_ms)| {
            let baseline_ms = *baseline.classes.get(class_name)?;
            Some(ClassRatio {
                class_name: class_name.clone(),
                duration_ms: *duration_ms,
                baseline_ms,
                ratio: (baseline_ms > 0.0).then(|| duration_ms / baseline_ms),
            })
        })
        .collect();
    classes.sort_by(|a, b| {
        b.ratio
            .unwrap_or(f64::INFINITY)
            .total_cmp(&a.ratio.unwrap_or(f64::INFINITY))
            .then_with(|| a.class_name.cmp(&b.class_name))
    });
    let mut over_floor: Vec<f64> = classes
        .iter()
        .filter(|class| class.baseline_ms > MEDIAN_FLOOR_MS)
        .filter_map(|class| class.ratio)
        .collect();
    over_floor.sort_by(f64::total_cmp);
    let median_ratio = median(&over_floor);
    let only = |of: &RunTime, other: &RunTime| -> Vec<String> {
        of.classes
            .keys()
            .filter(|class| !other.classes.contains_key(*class))
            .cloned()
            .collect()
    };
    Baseline {
        run_id: baseline.run_id.clone(),
        iterations: baseline.iterations.clone(),
        total_ms: baseline.total_ms,
        median_classes: over_floor.len(),
        median_ratio,
        only_in_run: only(run, baseline),
        only_in_baseline: only(baseline, run),
        classes,
        notes: baseline.notes.clone(),
    }
}

/// The median of sorted values, the mean of the middle two for an even count.
fn median(sorted: &[f64]) -> Option<f64> {
    let middle = sorted.len() / 2;
    match sorted.len() {
        0 => None,
        count if count % 2 == 1 => Some(sorted[middle]),
        _ => Some(f64::midpoint(sorted[middle - 1], sorted[middle])),
    }
}

// --- the text ----------------------------------------------------------------------------------------------------

/// A share of a total as a whole percentage, `0%` of no total.
fn share(part: f64, total: f64) -> String {
    if total > 0.0 {
        format!("{:.0}%", part * 100.0 / total)
    } else {
        "0%".to_owned()
    }
}

impl TimeReport {
    pub(crate) fn text(&self) -> String {
        let run = &self.run;
        let mut text = String::new();
        let iterations = counted(run.iterations.len(), "iteration");
        let _ = writeln!(text, "run {}: {iterations}, {}", run.run_id, seconds(run.total_ms));
        let _ = writeln!(
            text,
            "  inside the test cases   {:>9}  {}",
            seconds(run.inside_cases_ms),
            share(run.inside_cases_ms, run.total_ms)
        );
        let _ = writeln!(
            text,
            "  outside the test cases  {:>9}  {}",
            seconds(run.outside_cases_ms),
            share(run.outside_cases_ms, run.total_ms)
        );
        let _ = writeln!(text, "  IDE launches            {:>9}  {} exits", run.ide_launches, run.ide_exits);
        let _ = writeln!(
            text,
            "  relaunches              {:>9}  {} in {}",
            seconds(run.relaunches.total_ms),
            counted(run.relaunches.count, "restart span"),
            counted(run.bundles, "bundle")
        );
        for (key, time) in &run.relaunches.by_key {
            let _ = writeln!(text, "    {key:<22}{:>9}  {}", seconds(time.total_ms), time.count);
        }
        if !run.relaunches.parts.is_empty() {
            text.push_str(
                "the parts of the relaunches
",
            );
            for (key, time) in &run.relaunches.parts {
                let average = time.total_ms / time.count.max(1) as f64;
                let _ = writeln!(
                    text,
                    "  {key:<28}{:>9}  {}, {} each",
                    seconds(time.total_ms),
                    counted(time.count, "span"),
                    seconds(average)
                );
            }
        }
        if !run.top_cases.is_empty() {
            text.push_str("the longest test cases\n");
            for case in &run.top_cases {
                let _ = writeln!(
                    text,
                    "  {:>9}  {}  {}",
                    seconds(case.duration_ms),
                    short_class(&case.class_name),
                    case.name
                );
            }
        }
        if !run.top_spans.is_empty() {
            text.push_str("the lane spans with the most self time\n");
            for span in &run.top_spans {
                let _ = writeln!(
                    text,
                    "  {:>9}  {}  {}  ({} in all)",
                    seconds(span.self_ms),
                    span.title,
                    span.scenario,
                    seconds(span.total_ms)
                );
            }
        }
        for note in &run.notes {
            let _ = writeln!(text, "note: {note}");
        }
        if let Some(baseline) = &self.baseline {
            let _ = writeln!(text, "against {}: {}", baseline.run_id, seconds(baseline.total_ms));
            for class in &baseline.classes {
                let ratio = class.ratio.map_or_else(|| "-".to_owned(), |ratio| format!("{ratio:.2}"));
                let _ = writeln!(
                    text,
                    "  {ratio:>6}  {:>9} / {:>9}  {}",
                    seconds(class.duration_ms),
                    seconds(class.baseline_ms),
                    short_class(&class.class_name)
                );
            }
            match baseline.median_ratio {
                Some(median) => {
                    let _ = writeln!(
                        text,
                        "  median ratio {median:.2} over {} classes above 5 s in the baseline",
                        baseline.median_classes
                    );
                }
                None => text.push_str("  no class took more than 5 s in the baseline, so there is no median ratio\n"),
            }
            if !baseline.only_in_run.is_empty() {
                let _ = writeln!(text, "  only in this run: {}", baseline.only_in_run.join(", "));
            }
            if !baseline.only_in_baseline.is_empty() {
                let _ = writeln!(text, "  only in the baseline: {}", baseline.only_in_baseline.join(", "));
            }
            for note in &baseline.notes {
                let _ = writeln!(text, "note: {note}");
            }
        }
        text.trim_end().to_owned()
    }
}

/// `1 bundle` or `2 bundles`.
fn counted(count: usize, noun: &str) -> String {
    if count == 1 {
        format!("1 {noun}")
    } else {
        format!("{count} {noun}s")
    }
}

/// A class name without its package, which every class of a lane shares.
fn short_class(class_name: &str) -> &str {
    class_name.rsplit('.').next().unwrap_or(class_name)
}
