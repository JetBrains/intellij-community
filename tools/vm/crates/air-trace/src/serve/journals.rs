//! The controller's runs, as their journals say: `<runtime root>/runs/<runId>/events.ndjson`.
//!
//! A journal grows while its run goes on. The listing gives one summary of each run, and `GET /__air/run/<id>`
//! gives the records of one run from a byte offset. `run-appended` tells the viewer that a journal grew, so a run
//! page reads only the new records.

use std::collections::HashMap;
use std::fs::{self, File};
use std::io::{self, BufRead, BufReader};
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{Duration, SystemTime};

use avl_base::journal::FILE_NAME as JOURNAL_FILE;
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
}

/// The journals under one directory, each read again only when its file changed.
pub(crate) struct Journals {
    dir: Option<PathBuf>,
    state: Mutex<JournalsState>,
}

/// Whether a run id can name a directory under the runs directory: the controller's own rule for names, and
/// neither `.` nor `..`.
pub(crate) fn valid_run_id(run_id: &str) -> bool {
    avl_base::validate_name(run_id, "run id").is_ok() && !run_id.trim_matches('.').is_empty()
}

impl Journals {
    pub(crate) fn new(dir: Option<PathBuf>) -> Self {
        Self {
            dir,
            state: Mutex::default(),
        }
    }

    pub(crate) fn dir(&self) -> Option<&Path> {
        self.dir.as_deref()
    }

    fn lock(&self) -> std::sync::MutexGuard<'_, JournalsState> {
        avl_base::sync::lock(&self.state)
    }

    /// The journal of a run.
    pub(crate) fn journal_path(&self, run_id: &str) -> Option<PathBuf> {
        Some(self.dir.as_ref()?.join(run_id).join(JOURNAL_FILE))
    }

    /// A summary of every journal, newest run first, and the directories to watch: the runs directory, and the
    /// directory of each run still running, for its journal's writes.
    pub(crate) fn list(&self, now: SystemTime) -> (Vec<ControllerRun>, Vec<PathBuf>) {
        let Some(dir) = &self.dir else {
            return (Vec::new(), Vec::new());
        };
        let Ok(entries) = fs::read_dir(dir) else {
            return (Vec::new(), Vec::new());
        };
        let mut state = self.lock();
        let mut runs = Vec::new();
        // The runs directory itself, so a new run is seen when its directory appears.
        let mut watch = vec![dir.clone()];
        let mut seen = std::collections::HashSet::new();
        for entry in entries.filter_map(Result::ok) {
            let Ok(name) = entry.file_name().into_string() else {
                continue;
            };
            if !entry.file_type().is_ok_and(|kind| kind.is_dir()) || !valid_run_id(&name) {
                continue;
            }
            let Some(run) = self.summary_locked(&mut state, &name, now) else {
                continue;
            };
            if run.status == RunStatus::Running {
                watch.push(dir.join(&name));
            }
            seen.insert(name);
            runs.push(run);
        }
        state.facts.retain(|run_id, _| seen.contains(run_id));
        runs.sort_by(|left, right| right.started_at.cmp(&left.started_at).then_with(|| left.run_id.cmp(&right.run_id)));
        (runs, watch)
    }

    /// One run's summary, or `None` when the run has no journal.
    pub(crate) fn summary(&self, run_id: &str, now: SystemTime) -> Option<ControllerRun> {
        let mut state = self.lock();
        self.summary_locked(&mut state, run_id, now)
    }

    fn summary_locked(&self, state: &mut JournalsState, run_id: &str, now: SystemTime) -> Option<ControllerRun> {
        let path = self.journal_path(run_id)?;
        let info = fs::metadata(&path).ok().filter(fs::Metadata::is_file)?;
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

    /// A run's journal as a real path under the runs directory's real path, or why it cannot be: a missing journal
    /// (an error), or a link out of the runs directory (`Ok(None)`).
    pub(crate) fn real_journal(&self, run_id: &str) -> io::Result<Option<PathBuf>> {
        let (Some(dir), Some(path)) = (&self.dir, self.journal_path(run_id)) else {
            return Err(io::ErrorKind::NotFound.into());
        };
        let real_dir = fscopy::resolve_links(dir)?;
        let real = fscopy::resolve_links(&path)?;
        Ok(real.starts_with(&real_dir).then_some(real))
    }
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
