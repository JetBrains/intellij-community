//! The controller's runs, as their journals say: `<runs directory>/<runId>/events.ndjson`.
//!
//! A journal grows while its run goes on. The listing gives one summary of each run, and `GET /__air/run/<id>`
//! gives the records of one run from a byte offset. `run-appended` tells the viewer that a journal grew, so a run
//! page reads only the new records.
//!
//! The runs directories are the one of the runtime root and the one of the VM export of the checkout. The first
//! directory that holds a run directory owns the run, so a local run wins over an exported run with the same id. An
//! exported run comes from another host, and its records name the paths of that host. The run route maps the trace
//! zips and the reports of such a run to their copies in the export, see [localize].

use std::collections::{BTreeSet, HashMap};
use std::fs::{self, File};
use std::io::{self, BufRead, BufReader};
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{Duration, SystemTime};

use avl_base::journal::FILE_NAME as JOURNAL_FILE;
use avl_trace_tools::discover::{SourceKind, VM_RUN_REPORTS_DIR, VM_RUN_TRACES_DIR, bundle_id};
use avl_wire::daemon::Event as DaemonEvent;
use avl_wire::progress::{Kind, RunFinished};
use serde::{Deserialize, Serialize};
use serde_json::value::RawValue;

use super::{RUN_ROUTE, read_at_most};

/// Where a controller run is, beside the exit: a run without `runFinished` is running, until its journal has been
/// silent for [STOPPED_AFTER].
#[derive(Serialize, Clone, Copy, PartialEq, Eq, Debug)]
#[serde(rename_all = "lowercase")]
pub(crate) enum RunStatus {
    Running,
    Passed,
    Failed,
    Stopped,
}

impl RunStatus {
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::Running => "running",
            Self::Passed => "passed",
            Self::Failed => "failed",
            Self::Stopped => "stopped",
        }
    }
}

/// How long a journal without `runFinished` can stay unchanged and still be a running run. A long host build writes
/// nothing for minutes, and a controller killed outright writes no last record.
pub(crate) const STOPPED_AFTER: Duration = Duration::from_hours(1);

/// One run of the controller, in the listing: what its journal says so far.
#[derive(Serialize, Clone, PartialEq, Eq, Debug)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ControllerRun {
    pub run_id: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub command: String,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub args: Vec<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub checkout: Option<String>,
    /// The time of `runStarted`.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub started_at: Option<String>,
    /// The time of the last record.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub updated_at: Option<String>,
    pub status: RunStatus,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub finished: Option<RunFinished>,
    /// Counts `iterationReported`.
    pub iterations: u32,
    pub tests_started: u32,
    pub tests_passed: u32,
    pub tests_failed: u32,
    /// Counts `traceReady`.
    pub traces: u32,
    /// The run's records, `/__air/run/<runId>`.
    pub url: String,
    /// Why the journal could not be read.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
    /// Whether the run comes from the VM export of the checkout, so it ran on another host.
    #[serde(skip_serializing_if = "std::ops::Not::not")]
    pub exported: bool,
}

/// The part of a progress record a summary reads.
#[derive(Deserialize)]
struct JournalRecord<'a> {
    event: String,
    #[serde(default)]
    at: String,
    #[serde(borrow, default)]
    data: Option<&'a RawValue>,
}

/// The parts of `runStarted` a summary shows. Read leniently: the journal is evidence, and an older controller's
/// record without one of these still names its run.
#[derive(Deserialize, Default)]
#[serde(rename_all = "camelCase", default)]
struct StartedFacts {
    command: String,
    args: Vec<String>,
    checkout: Option<String>,
}

/// The parts of a forwarded daemon record a summary counts.
#[derive(Deserialize, Default)]
#[serde(default)]
struct DaemonFacts {
    event: String,
    status: String,
}

/// The JUnit Platform's result statuses, as the daemon's `testFinished` carries them.
const TEST_SUCCESSFUL: &str = "SUCCESSFUL";
const TEST_FAILED: &str = "FAILED";
const TEST_ABORTED: &str = "ABORTED";

/// One journal's summary at one state of its file.
struct JournalFacts {
    size: u64,
    modified: SystemTime,
    run: ControllerRun,
}

#[derive(Default)]
struct JournalsState {
    facts: HashMap<String, JournalFacts>,
    /// The journal size of each run that `run-appended` last named.
    announced: HashMap<String, u64>,
    /// The run directories that the last listing read.
    listed: BTreeSet<String>,
    /// The listed run directories that had no journal yet.
    without_journal: BTreeSet<String>,
}

/// One directory of controller runs: one directory for each run, with its journal.
#[derive(Clone, Debug)]
pub(crate) struct RunsDir {
    pub path: PathBuf,
    /// Whether the runs are the VM export of the checkout, copied from another host.
    pub exported: bool,
}

/// The journal of one run, as the run route reads it.
pub(crate) struct RunJournal {
    /// The real path of the journal.
    pub path: PathBuf,
    pub exported: bool,
}

/// The journals under the runs directories, each read again only when its file changed.
pub(crate) struct Journals {
    dirs: Vec<RunsDir>,
    state: Mutex<JournalsState>,
}

/// Whether a run id can name a directory under the runs directory: the controller's own rule for names, and
/// neither `.` nor `..`.
pub(crate) fn valid_run_id(run_id: &str) -> bool {
    avl_base::validate_name(run_id, "run id").is_ok() && !run_id.trim_matches('.').is_empty()
}

impl Journals {
    pub(crate) fn new(dirs: Vec<RunsDir>) -> Self {
        Self {
            dirs,
            state: Mutex::default(),
        }
    }

    /// Whether the server reads no controller runs.
    pub(crate) fn is_empty(&self) -> bool {
        self.dirs.is_empty()
    }

    fn lock(&self) -> std::sync::MutexGuard<'_, JournalsState> {
        avl_base::sync::lock(&self.state)
    }

    /// The runs directory that owns a run: the first one that holds its run directory.
    fn home(&self, run_id: &str) -> Option<&RunsDir> {
        self.dirs.iter().find(|dir| dir.path.join(run_id).is_dir())
    }

    /// The journal of a run.
    pub(crate) fn journal_path(&self, run_id: &str) -> Option<PathBuf> {
        Some(self.home(run_id)?.path.join(run_id).join(JOURNAL_FILE))
    }

    /// A run's journal and its metadata, or `None` when the run has no journal file.
    fn journal(&self, run_id: &str) -> Option<(PathBuf, fs::Metadata)> {
        let path = self.journal_path(run_id)?;
        let info = fs::metadata(&path).ok().filter(fs::Metadata::is_file)?;
        Some((path, info))
    }

    /// A summary of every journal, newest run first, and the directories to watch: the runs directory, the
    /// directory of each run still running, for its journal's writes, and each run directory without a journal, for
    /// the journal's creation.
    pub(crate) fn list(&self, now: SystemTime) -> (Vec<ControllerRun>, Vec<PathBuf>) {
        let mut watch = Vec::new();
        // The run directories, each in the runs directory that owns it.
        let mut homes: Vec<(String, &Path)> = Vec::new();
        let mut listed = BTreeSet::new();
        for dir in &self.dirs {
            let Some(names) = run_dirs(&dir.path) else {
                continue;
            };
            // The runs directory itself, so a new run is seen when its directory appears.
            watch.push(dir.path.clone());
            for name in names {
                if listed.insert(name.clone()) {
                    homes.push((name, &dir.path));
                }
            }
        }
        let mut state = self.lock();
        let mut runs = Vec::new();
        let mut without_journal = BTreeSet::new();
        for (name, dir) in &homes {
            let Some(run) = self.summary_locked(&mut state, name, now) else {
                // A run creates its directory before its journal.
                watch.push(dir.join(name));
                without_journal.insert(name.clone());
                continue;
            };
            if run.status == RunStatus::Running {
                watch.push(dir.join(name));
            }
            runs.push(run);
        }
        state
            .facts
            .retain(|run_id, _| listed.contains(run_id) && !without_journal.contains(run_id));
        state.listed = listed;
        state.without_journal = without_journal;
        runs.sort_by(|left, right| right.started_at.cmp(&left.started_at).then_with(|| left.run_id.cmp(&right.run_id)));
        (runs, watch)
    }

    /// Whether the runs directory differs from what the last listing read: a run directory came or went, or a
    /// run directory that had no journal has one now. A file event can come late or not at all, and this check reads
    /// the directory itself.
    pub(crate) fn listing_is_stale(&self) -> bool {
        if self.dirs.is_empty() {
            return false;
        }
        let names: BTreeSet<String> = self.dirs.iter().filter_map(|dir| run_dirs(&dir.path)).flatten().collect();
        let state = self.lock();
        names != state.listed || state.without_journal.iter().any(|run_id| self.journal(run_id).is_some())
    }

    /// One run's summary, or `None` when the run has no journal.
    pub(crate) fn summary(&self, run_id: &str, now: SystemTime) -> Option<ControllerRun> {
        let mut state = self.lock();
        self.summary_locked(&mut state, run_id, now)
    }

    fn summary_locked(&self, state: &mut JournalsState, run_id: &str, now: SystemTime) -> Option<ControllerRun> {
        let exported = self.home(run_id)?.exported;
        let (path, info) = self.journal(run_id)?;
        let modified = info.modified().unwrap_or(SystemTime::UNIX_EPOCH);
        let current = state
            .facts
            .get(run_id)
            .is_some_and(|facts| facts.size == info.len() && facts.modified == modified);
        if !current {
            state.facts.insert(
                run_id.to_owned(),
                JournalFacts {
                    size: info.len(),
                    modified,
                    run: read_summary(run_id, &path),
                },
            );
        }
        let mut run = state.facts[run_id].run.clone();
        run.exported = exported;
        let silent = now.duration_since(modified).unwrap_or_default();
        if run.status == RunStatus::Running && run.error.is_none() && silent > STOPPED_AFTER {
            run.status = RunStatus::Stopped;
        }
        Some(run)
    }

    /// Records a journal's size, and answers whether it differs from the size `run-appended` last named.
    pub(crate) fn observe(&self, run_id: &str, size: u64) -> bool {
        let mut state = self.lock();
        let previous = state.announced.insert(run_id.to_owned(), size);
        previous != Some(size)
    }

    /// The ids of the runs the last listing read as running.
    pub(crate) fn running(&self) -> Vec<String> {
        self.lock()
            .facts
            .iter()
            .filter(|(_, facts)| facts.run.status == RunStatus::Running)
            .map(|(run_id, _)| run_id.clone())
            .collect()
    }

    /// A run's journal as a real path under the real path of its runs directory, or why it cannot be: a missing
    /// journal (an error), or a link out of the runs directory (`Ok(None)`).
    pub(crate) fn real_journal(&self, run_id: &str) -> io::Result<Option<RunJournal>> {
        let Some(dir) = self.home(run_id) else {
            return Err(io::ErrorKind::NotFound.into());
        };
        let real_dir = fscopy::resolve_links(&dir.path)?;
        let real = fscopy::resolve_links(&dir.path.join(run_id).join(JOURNAL_FILE))?;
        Ok(real.starts_with(&real_dir).then_some(RunJournal {
            path: real,
            exported: dir.exported,
        }))
    }
}

/// The run directories under the runs directory, or `None` when it cannot be read. A run directory is a directory
/// whose name is a valid run id.
fn run_dirs(dir: &Path) -> Option<BTreeSet<String>> {
    let entries = fs::read_dir(dir).ok()?;
    let names = entries
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_ok_and(|kind| kind.is_dir()))
        .filter_map(|entry| entry.file_name().into_string().ok())
        .filter(|name| valid_run_id(name))
        .collect();
    Some(names)
}

/// Reads one journal whole. A line that does not decode is skipped: it is the last line, still being written, or
/// damage the run page shows as it is.
fn read_summary(run_id: &str, path: &Path) -> ControllerRun {
    let mut run = ControllerRun {
        run_id: run_id.to_owned(),
        command: String::new(),
        args: Vec::new(),
        checkout: None,
        started_at: None,
        updated_at: None,
        status: RunStatus::Running,
        finished: None,
        iterations: 0,
        tests_started: 0,
        tests_passed: 0,
        tests_failed: 0,
        traces: 0,
        url: format!("{RUN_ROUTE}{run_id}"),
        error: None,
        exported: false,
    };
    let file = match File::open(path) {
        Ok(file) => file,
        Err(error) => {
            run.error = Some(error.to_string());
            return run;
        }
    };
    let mut reader = BufReader::with_capacity(64 << 10, file);
    let mut line = Vec::new();
    loop {
        line.clear();
        // A line without its newline is still being written.
        match reader.read_until(b'\n', &mut line) {
            Ok(_) if line.last() == Some(&b'\n') => {}
            _ => break,
        }
        let Ok(record) = serde_json::from_slice::<JournalRecord<'_>>(&line) else {
            continue;
        };
        run.updated_at = Some(record.at.clone()).filter(|at| !at.is_empty());
        apply_record(&mut run, &record);
    }
    run
}

/// Adds one record to a summary.
fn apply_record(run: &mut ControllerRun, record: &JournalRecord<'_>) {
    let data = record.data.map_or("null", RawValue::get);
    match record.event.parse::<Kind>().ok() {
        Some(Kind::RunStarted) => {
            if let Ok(started) = serde_json::from_str::<StartedFacts>(data) {
                run.command = started.command;
                run.args = started.args;
                run.checkout = started.checkout;
            }
            run.started_at = Some(record.at.clone()).filter(|at| !at.is_empty());
        }
        Some(Kind::IterationReported) => run.iterations += 1,
        Some(Kind::TraceReady) => run.traces += 1,
        Some(Kind::Run) => {
            let Ok(forwarded) = serde_json::from_str::<DaemonFacts>(data) else {
                return;
            };
            match forwarded.event.parse::<DaemonEvent>().ok() {
                Some(DaemonEvent::TestStarted) => run.tests_started += 1,
                Some(DaemonEvent::TestFinished) => match forwarded.status.as_str() {
                    TEST_SUCCESSFUL => run.tests_passed += 1,
                    TEST_FAILED | TEST_ABORTED => run.tests_failed += 1,
                    _ => {}
                },
                _ => {}
            }
        }
        Some(Kind::RunFinished) => {
            let Ok(finished) = serde_json::from_str::<RunFinished>(data) else {
                return;
            };
            run.status = if finished.exit_code == 0 {
                RunStatus::Passed
            } else {
                RunStatus::Failed
            };
            run.finished = Some(finished);
        }
        _ => {}
    }
}

/// `GET /__air/run/<runId>?from=<offset>`: the run's summary and its complete records from the offset on.
#[derive(Serialize, Debug)]
pub(crate) struct RunRecords {
    pub run: Option<ControllerRun>,
    /// Where the first record starts. It is 0, and `reset` is true, when the asked offset is past the end of the
    /// journal, which a replaced journal causes.
    pub from: u64,
    #[serde(skip_serializing_if = "std::ops::Not::not")]
    pub reset: bool,
    /// The offset to ask for next. `more` is true when records past it are already there.
    pub next: u64,
    #[serde(skip_serializing_if = "std::ops::Not::not")]
    pub more: bool,
    pub records: Vec<Box<RawValue>>,
}

/// Bounds one answer of the run route. A longer journal is read in several requests.
pub(crate) const MAX_RECORDS_READ: u64 = 4 << 20;

/// Reads the complete lines of a journal from an offset, up to [MAX_RECORDS_READ] bytes. A line that is not JSON is
/// left out, so the answer is always valid JSON.
pub(crate) fn read_records(path: &Path, from: u64) -> io::Result<RunRecords> {
    let file = File::open(path)?;
    let size = file.metadata()?.len();
    let mut answer = RunRecords {
        run: None,
        from,
        reset: false,
        next: from,
        more: false,
        records: Vec::new(),
    };
    if from > size {
        answer.from = 0;
        answer.reset = true;
    }
    let length = (size - answer.from).min(MAX_RECORDS_READ);
    let chunk = read_at_most(&file, answer.from, length)?;
    let Some(end) = chunk.iter().rposition(|byte| *byte == b'\n') else {
        // One line longer than an answer can read is no record the page can show; skip it rather than stall on it,
        // as `read_appended` does. The next answer starts inside that line, and its rest is not JSON, so it is left out.
        if length == MAX_RECORDS_READ {
            answer.next = answer.from + length;
            answer.more = answer.next < size;
        }
        return Ok(answer);
    };
    answer.next = answer.from + end as u64 + 1;
    answer.more = answer.next < size && length == MAX_RECORDS_READ;
    answer.records = chunk[..end]
        .split(|byte| *byte == b'\n')
        .filter_map(|line| std::str::from_utf8(line).ok())
        .filter_map(|line| RawValue::from_string(line.trim().to_owned()).ok())
        .collect();
    Ok(answer)
}

/// Maps the paths of another host in the records of an exported run to the copies in the export, whose run directory
/// is `run_dir`. A `traceReady` names the copy of its zip, and the bundle id that the scan of the export gives that
/// copy. An `iterationReported` names the copy of its report. A record whose copy is missing stays as it is, and so
/// does every other record.
pub(crate) fn localize(records: &mut [Box<RawValue>], run_dir: &Path) {
    for record in records {
        let Ok(head) = serde_json::from_str::<JournalRecord<'_>>(record.get()) else {
            continue;
        };
        let kind = head.event.parse::<Kind>().ok();
        if !matches!(kind, Some(Kind::TraceReady | Kind::IterationReported)) {
            continue;
        }
        let Ok(mut value) = serde_json::from_str::<serde_json::Value>(record.get()) else {
            continue;
        };
        let Some(data) = value.get_mut("data").and_then(serde_json::Value::as_object_mut) else {
            continue;
        };
        let changed = if kind == Some(Kind::TraceReady) {
            localize_trace(data, run_dir)
        } else {
            localize_report(data, run_dir)
        };
        if changed && let Ok(local) = serde_json::value::to_raw_value(&value) {
            *record = local;
        }
    }
}

type Fields = serde_json::Map<String, serde_json::Value>;

/// The file name of the path in a string field, when it is one valid path component.
fn file_name_of<'a>(data: &'a Fields, field: &str) -> Option<&'a str> {
    let path = data.get(field)?.as_str()?;
    let name = path.rsplit(['/', '\\']).next()?;
    valid_run_id(name).then_some(name)
}

fn localize_trace(data: &mut Fields, run_dir: &Path) -> bool {
    let (Some(iteration_id), Some(name), Some(entry)) = (
        data.get("iterationId").and_then(serde_json::Value::as_str),
        file_name_of(data, "zip"),
        data.get("entry").and_then(serde_json::Value::as_str),
    ) else {
        return false;
    };
    if !valid_run_id(iteration_id) {
        return false;
    }
    let copy = run_dir.join(VM_RUN_TRACES_DIR).join(iteration_id).join(name);
    let Ok(real) = fscopy::resolve_links(&copy) else {
        return false;
    };
    let zip = real.to_string_lossy().into_owned();
    let id = bundle_id(SourceKind::Zip, &zip, entry);
    data.insert("bundleId".to_owned(), id.into());
    data.insert("zip".to_owned(), zip.into());
    true
}

fn localize_report(data: &mut Fields, run_dir: &Path) -> bool {
    let Some(name) = file_name_of(data, "reportPath") else {
        return false;
    };
    let copy = run_dir.join(VM_RUN_REPORTS_DIR).join(name);
    if !copy.is_file() {
        return false;
    }
    data.insert("reportPath".to_owned(), copy.to_string_lossy().into_owned().into());
    true
}
