//! The agent-facing result documents: one run's report, and the two aggregates built over many of them.
//!
//! The run stream of the daemon is the one account of a run. The report, the JUnit XML and the TeamCity messages are
//! its three readers. What a caller of this controller receives - in both the successful and the failed command
//! envelope - is the bounded document declared here. An agent reads
//! these bytes, so a field renamed here is a field renamed for every reader, and `reportSchemaVersion` is the
//! promise that says so. A report inside its `reportSchemaVersion` stays readable, and an optional field added
//! later defaults, because the shard baseline and the trace viewer read reports from earlier runs.
//!
//! Three properties are load-bearing, and each of them is a refusal rather than a convention:
//!
//! - **A schema version is checked, not carried.** A document at a version this module does not understand is
//!   refused. Decoding it anyway and answering zeroes for the fields that moved is how a report with no failures
//!   in it comes to mean "nothing failed".
//! - **Absent is not empty.** An absent `verdictDiagnostic` means the run needed no explanation, while an empty
//!   one means something wrote a reason and lost it. An explicit `null` is refused.
//! - **A declared array is always an array**, never `null`.
//!
//! What this module deliberately does *not* hold is the arithmetic: building a report from a run's event stream and
//! its traces, and folding many reports into a verdict or a flake rate, are decisions about evidence and live with
//! the host. This is the shape the decisions are written in.
//!
//! Every refusal carries an agent-facing code (`report_schema_*`, `aggregate_schema_*`). A document is decoded
//! in the order that keeps each code honest: the JSON object, the version, the top-level shape, the vocabulary
//! words, the typed decode, and the invariants a type cannot state.

use std::collections::BTreeMap;
use std::fmt;
use std::str::FromStr;

use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};

use crate::daemon::{ExecutionIdentity, RunEvent, RunEventKind};
use crate::json::{Kind, Shape};

mod aggregate;
#[cfg(test)]
mod tests;

pub use aggregate::*;

/// The version of the single-run document. Separate from [`AGGREGATE_SCHEMA_VERSION`]: an aggregate is a
/// different noun with a different audience, and it embeds these reports rather than pretending to be a bigger
/// one of them.
pub const RUN_REPORT_SCHEMA_VERSION: u32 = 3;

// The bounds a builder clips a failure to, declared beside the fields that report having been clipped: a reader
// who wants to know what `messageTruncated` lost needs the number.
pub const MAX_FAILURE_MESSAGE_CHARS: usize = 8 * 1024;
pub const MAX_FAILURE_DETAIL_CHARS: usize = 32 * 1024;
pub const MAX_RELEVANT_FRAMES: usize = 8;

/// The one order cases are ever written in: by the instant of their first record, and in the order of the run stream
/// for two cases of one instant. Never by timestamp text.
pub const ORDERING: &str = "case_start_ascending";

// --- the vocabularies -------------------------------------------------------------------------------------

vocabulary! {
    /// A run's verdict. An undeclared status is refused: the aggregates decide what is authoritative by matching
    /// these names, and one they do not know would silently fall out of both the numerator and the denominator.
    pub enum Status {
        Passed = "passed",
        Failed = "failed",
        /// A guard, not good news: a run that discovered nothing measured nothing. It is the shape a broken
        /// selector takes, which is why an aggregate must not fold it in as a green trial.
        NoTests = "no_tests",
        AllSkipped = "all_skipped",
        /// The verdict every false-green guard collapses into - an absent summary, a summary that contradicts the
        /// stream, a recorder that contradicts the stream, a class that never reported, a watchdog expiry. An
        /// aggregate re-uses it instead of re-deriving it, because a second derivation is a second opinion.
        InfrastructureError = "infrastructure_error",
    }
}

vocabulary! {
    /// What failed: a test, or a container - a prologue of a suite document, or a JUnit class that failed outside its
    /// tests.
    pub enum FailureKind {
        Container = "container",
        Test = "test",
    }
}

vocabulary! {
    /// One test case's outcome, as the report spells it.
    pub enum CaseStatus {
        Passed = "passed",
        Failed = "failed",
        Skipped = "skipped",
    }
}

// --- the document -----------------------------------------------------------------------------------------

/// The daemon's own count of what it ran.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExecutionCounts {
    pub tests_started: u32,
    pub tests_failed: u32,
    pub tests_skipped: u32,
    /// Classes a JUnit condition ruled out, which produce no test of their own to count. A lane whose
    /// prerequisite CLI is missing is all of these and none of the others.
    pub containers_skipped: u32,
    pub container_failures: u32,
}

/// One case of the run: a scenario, a failed container or a skipped container, as the run stream reports it.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Case {
    pub class_name: String,
    pub name: String,
    pub status: CaseStatus,
    pub duration_ms: f64,
    /// The `timestamp` of the first record of the case.
    pub started_at: String,
    /// The status the recorder closed the trace bundle of the scenario with, or `truncated` for a bundle it never
    /// closed: the second account of the case. Absent when no bundle of the scenario arrived.
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub recorded: Option<String>,
    /// Why a skipped case did not run, as its `testSkipped` record says. Absent for a case that ran.
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub reason: Option<String>,
}

/// What the authored parts of one scenario report beside their values: the notes and the measurements of the
/// scenario, merged over its parts. The `testFinished` of the scenario carries it. A note and a metric explain a run and
/// never decide its verdict. The TeamCity messages turn a note into a text `testMetadata` and a metric into a `number`
/// one.
///
/// A metric is a JSON number rather than an `f64`, so the records of the run stream keep their equality.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct ScenarioReport {
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub notes: Vec<String>,
    #[serde(default, skip_serializing_if = "BTreeMap::is_empty")]
    pub metrics: BTreeMap<String, serde_json::Number>,
}

impl ScenarioReport {
    /// Whether the scenario reported nothing, which is how the record leaves the field out.
    pub fn is_empty(&self) -> bool {
        self.notes.is_empty() && self.metrics.is_empty()
    }
}

/// One thing that went wrong, as the run stream reports it.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Failure {
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub class_name: Option<String>,
    pub test_name: String,
    pub kind: FailureKind,
    #[serde(
        rename = "type",
        default,
        deserialize_with = "crate::json::non_null",
        skip_serializing_if = "Option::is_none"
    )]
    pub r#type: Option<String>,
    pub message: String,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub detail: Option<String>,
    /// The stack reduced to the frames that name Air, the hot classpath or the starter - and the whole stack
    /// when none of them do, because a failure with no recognizable frame still has to be readable. Capped at
    /// [`MAX_RELEVANT_FRAMES`].
    #[serde(default)]
    pub relevant_frames: Vec<String>,
    pub message_truncated: bool,
    pub detail_truncated: bool,
}

/// One class a JUnit condition ruled out, with the reason JUnit gave: the only account of why a suite ran
/// nothing, so it belongs in the document rather than only in a stream the caller may not have kept.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SkippedContainer {
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub class_name: Option<String>,
    pub display_name: String,
    pub reason: String,
}

/// A test the stream started and never finished, which is what a killed run leaves behind.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ActiveTest {
    /// The test, with the fields of the record that started it.
    #[serde(flatten)]
    pub execution: ExecutionIdentity,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub started_at: Option<String>,
}

/// The last progress state the daemon published.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WatchdogState {
    pub phase: String,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub observed_at: Option<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub phase_deadline: Option<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub emergency_deadline: Option<String>,
    /// Absent means the daemon named no next deadline, and zero means it expires now.
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub next_deadline_in_ms: Option<i64>,
    pub active_execution_timeout_ms: i64,
    pub progress_gap_timeout_ms: i64,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub active_execution: Option<ExecutionIdentity>,
}

/// Why the daemon gave up on a run.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WatchdogExpiry {
    pub reason: String,
    /// What the daemon observed, when the reason is not a budget running out.
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub detail: Option<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub deadline: Option<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub expired_at: Option<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub active_execution: Option<ExecutionIdentity>,
    /// Guest files; the copies the controller managed to fetch appear in [`RunReport::evidence`]. Filtered by
    /// [`safe_evidence_paths`] before anything is fetched.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub evidence: Vec<String>,
}

/// What the daemon's progress watchdog had to say. Both halves are optional: a run that finished on time
/// expired nothing, and a run that never started published no state.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Watchdog {
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub last_state: Option<WatchdogState>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub expired: Option<WatchdogExpiry>,
}

/// One guest file the daemon named, and where the controller put it.
///
/// Exactly one of `artifact_path` and `error` is set. A named file that could not be fetched is reported with
/// the reason rather than dropped, because evidence that silently disappears reads as evidence never offered.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct EvidenceArtifact {
    pub guest_path: String,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub artifact_path: Option<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

/// One iteration on one worker. Field order is the order on the wire.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RunReport {
    pub report_schema_version: u32,
    pub iteration_id: String,
    pub daemon_run_id: String,
    pub daemon_boot_stamp: String,
    pub selection: String,
    pub status: Status,
    pub started_at: String,
    pub completed_at: String,
    pub duration_ms: f64,
    /// Always [`ORDERING`].
    pub ordering: String,
    pub execution: ExecutionCounts,
    /// Every case of the run in [`ORDERING`]. A case that started and never finished is in `active_tests` instead.
    #[serde(default)]
    pub cases: Vec<Case>,
    #[serde(default)]
    pub failures: Vec<Failure>,
    #[serde(default)]
    pub skipped_containers: Vec<SkippedContainer>,
    #[serde(default)]
    pub active_tests: Vec<ActiveTest>,
    /// Every class the plan discovered that the run then said nothing about at all: lost coverage, named.
    ///
    /// It comes from the daemon's `planStarted` record, which is what makes it survive the case it exists for: when a
    /// watchdog kills the IDE, the suites queued behind the failure write no record, and a run that dropped half a lane
    /// used to read exactly like a run that covered it. A non-empty list is a diagnostic, so it can only make a verdict
    /// worse.
    #[serde(default)]
    pub unreported_classes: Vec<String>,
    pub watchdog: Watchdog,
    /// Present only when the daemon named evidence; every entry is either fetched or explained.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub evidence: Vec<EvidenceArtifact>,
    /// The iteration's scenario traces as they arrived on the host, one zip per pull, and why a pull failed. Both
    /// can be set: the zips that arrived before a pull failed stay named.
    ///
    /// An archive that did not arrive never touches `status` or `verdict_diagnostic`: a verdict that turned red
    /// because a video did not arrive would be evidence deciding the verdict it exists to explain. A bundle that did
    /// arrive is the second account of its scenario: its status is the case's `recorded`, and a status that
    /// contradicts the stream is a diagnostic.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub trace_archives: Vec<TraceArchive>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub traces_error: Option<String>,
    /// The host checkout the build read, or why it could not be read; exactly one of the two is set. Several
    /// agent sessions share one working copy, so a red lane can come from another session's edits. Neither
    /// touches the verdict.
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub tree: Option<Tree>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub tree_error: Option<String>,
    /// The facts about the run that a reader must see and that do not change the verdict. An example is a test jar
    /// that the host rebuilt while the iteration ran. Carried verbatim, and never read by anything that decides
    /// `status` or `verdict_diagnostic`, as `traces_error` is.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub notes: Vec<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub protocol_diagnostic: Option<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub verdict_diagnostic: Option<String>,
}

/// The maximum number of paths [`Tree::uncommitted`] names; [`Tree::uncommitted_count`] counts all of them, so
/// a dirty tree cannot make the report large.
pub const MAX_TREE_PATHS: usize = 50;

/// The host checkout at the moment the host build started.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Tree {
    /// The commit `git rev-parse HEAD` gave.
    pub head: String,
    /// The modified, added, deleted and untracked paths under the lane's source roots, relative to the
    /// repository root and in the order of `git status`; at most [`MAX_TREE_PATHS`].
    #[serde(default)]
    pub uncommitted: Vec<String>,
    pub uncommitted_count: usize,
}

/// One pull of an iteration's scenario traces: one zip on the host, holding a bundle for each scenario under
/// `<runId>/<TestClass>/<scenario>/`.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct TraceArchive {
    /// Absolute and with its links resolved, on the host that ran the controller.
    pub path: String,
    /// Its size, so a reader can tell an empty archive from a full one without opening it.
    pub bytes: i64,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub bundles: Vec<TraceBundle>,
}

/// One scenario's trace inside a [`TraceArchive`].
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TraceBundle {
    /// The trace viewer's id for the bundle.
    pub id: String,
    /// The bundle's directory inside the zip, ending in `/`.
    pub entry: String,
    pub test_class: String,
    pub scenario: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub flow: Option<String>,
    /// The manifest's status, or `truncated` for a bundle whose recorder never finished it.
    pub status: String,
    pub has_video: bool,
}

// --- the evidence guard -----------------------------------------------------------------------------------

/// How many guest files one expiry may cause the controller to fetch.
pub const MAX_EVIDENCE_FILES: usize = 4;

/// How many dump files the slow steps of one run may cause the controller to fetch: two for each of the four steps
/// that the daemon dumps.
pub const MAX_SLOW_STEP_FILES: usize = 8;

/// Reduces the guest files a run's last expiry named to what is safe to fetch.
///
/// The list arrives from inside the guest, so it is input rather than fact: absolute POSIX paths of printable
/// ASCII at most 512 characters after the slash, no traversal, a capture extension, no duplicates, and a hard
/// count cap. Anything else is dropped silently, because evidence is a convenience on top of a verdict that
/// already stands on its own. The *last* expiry, because the one that ended the run describes the end.
///
/// The guard holds no directory list, so it admits the files of an IDE context under `<vm_data>/ide/` as it admits
/// every other guest directory: the heartbeat screenshots of `log/<launchName>` and the thread dump that a
/// `cancel --thread-dump` writes there before the kill.
///
/// The dumps that each `slowStep` record names follow the files of the expiry, in the order of the records, with a cap
/// of their own, [`MAX_SLOW_STEP_FILES`], so a run with slow steps keeps the evidence of its end.
pub fn safe_evidence_paths(events: &[RunEvent]) -> Vec<String> {
    let mut safe = expiry_evidence_paths(events);
    let dumps = events.iter().filter_map(|event| match &event.kind {
        RunEventKind::SlowStep(slow) => Some(slow.dumps()),
        _ => None,
    });
    let expiry_files = safe.len();
    admit(&mut safe, dumps.flatten(), expiry_files + MAX_SLOW_STEP_FILES);
    safe
}

/// The part of [`safe_evidence_paths`] that the last expiry named: what the report's expiry names as its evidence.
pub fn expiry_evidence_paths(events: &[RunEvent]) -> Vec<String> {
    let named = events.iter().rev().find_map(|event| match &event.kind {
        RunEventKind::WatchdogExpired(expired) => Some(expired.evidence.as_slice()),
        _ => None,
    });
    let mut safe: Vec<String> = Vec::new();
    admit(&mut safe, named.unwrap_or_default().iter(), MAX_EVIDENCE_FILES);
    safe
}

/// Adds each safe entry that `safe` does not hold yet, until `safe` holds `cap` entries.
fn admit<'a>(safe: &mut Vec<String>, entries: impl Iterator<Item = &'a String>, cap: usize) {
    for entry in entries {
        if safe.len() >= cap {
            break;
        }
        if is_safe_evidence_path(entry) && !safe.contains(entry) {
            safe.push(entry.clone());
        }
    }
}

fn is_safe_evidence_path(path: &str) -> bool {
    let Some(rest) = path.strip_prefix('/') else {
        return false;
    };
    let lower = path.to_ascii_lowercase();
    (1..=512).contains(&rest.len())
        && rest.bytes().all(|byte| (0x20..=0x7e).contains(&byte))
        && !path.contains("..")
        && [".png", ".jpg", ".jpeg", ".txt"].iter().any(|extension| lower.ends_with(extension))
}

// --- decoding ---------------------------------------------------------------------------------------------

/// A refusal to read a document.
///
/// Distinct from the daemon's protocol error because the two name different failures: a protocol error says
/// this controller and a guest daemon disagree, while this says a document on disk is not the document this
/// controller writes. Aiming a reader at `daemon log` for a malformed report file would send them to the wrong
/// machine.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct SchemaError {
    pub code: &'static str,
    pub message: String,
}

impl SchemaError {
    pub(crate) fn new(code: &'static str, detail: impl fmt::Display) -> Self {
        Self {
            code,
            message: format!(
                "{detail}. This is a result document written by the VM UI-lane controller (run reports are \
                 v{RUN_REPORT_SCHEMA_VERSION}, aggregates v{AGGREGATE_SCHEMA_VERSION}); a version mismatch or a \
                 hand-edited file is the usual cause."
            ),
        }
    }
}

impl fmt::Display for SchemaError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}

impl std::error::Error for SchemaError {}

/// The document family a code belongs to, because a caller branches on the code, and a stale shard aggregate
/// answering `report_schema_unsupported` would send them looking at the wrong file.
#[derive(Clone, Copy)]
pub(crate) enum Family {
    Report,
    Aggregate,
}

impl Family {
    pub(crate) const fn version_missing(self) -> &'static str {
        match self {
            Self::Report => "report_schema_version_missing",
            Self::Aggregate => "aggregate_schema_version_missing",
        }
    }

    pub(crate) const fn unsupported(self) -> &'static str {
        match self {
            Self::Report => "report_schema_unsupported",
            Self::Aggregate => "aggregate_schema_unsupported",
        }
    }

    pub(crate) const fn unreadable(self) -> &'static str {
        match self {
            Self::Report => "report_schema_unreadable",
            Self::Aggregate => "aggregate_schema_unreadable",
        }
    }

    pub(crate) const fn field_missing(self) -> &'static str {
        match self {
            Self::Report => "report_schema_field_missing",
            Self::Aggregate => "aggregate_schema_field_missing",
        }
    }
}

/// The document as a JSON object, or the family's `unreadable` refusal.
pub(crate) fn object(document: &[u8], family: Family, label: &str) -> Result<Map<String, Value>, SchemaError> {
    serde_json::from_slice(document)
        .map_err(|error| SchemaError::new(family.unreadable(), format_args!("a {label} is not a JSON object ({error})")))
}

/// Refuses a document whose declared version is absent, not a number, or not the one this module writes.
pub(crate) fn require_version(
    fields: &Map<String, Value>,
    field: &str,
    expected: u32,
    label: &str,
    family: Family,
) -> Result<(), SchemaError> {
    let Some(raw) = fields.get(field) else {
        return Err(SchemaError::new(
            family.version_missing(),
            format_args!("a {label} declares no {field}"),
        ));
    };
    let Some(declared) = raw.as_i64() else {
        return Err(SchemaError::new(
            family.version_missing(),
            format_args!("a {label} declares the non-numeric {field} {raw}"),
        ));
    };
    if declared != i64::from(expected) {
        return Err(SchemaError::new(
            family.unsupported(),
            format_args!("a {label} declares {field} {declared}, and this controller reads {expected}"),
        ));
    }
    Ok(())
}

fn text(value: &Value) -> &str {
    value.as_str().unwrap_or_default()
}

/// Refuses a vocabulary word `V` does not declare. A value that is not a string is left for the typed decode.
pub(crate) fn declared_word<V: FromStr>(value: &Value, refusal: impl FnOnce(&str) -> SchemaError) -> Result<(), SchemaError> {
    match value.as_str() {
        Some(word) if word.parse::<V>().is_err() => Err(refusal(word)),
        _ => Ok(()),
    }
}

/// The typed decode, after the version, the shape and the vocabulary have been checked.
pub(crate) fn typed<T: DeserializeOwned>(fields: Map<String, Value>, family: Family, label: &str) -> Result<T, SchemaError> {
    serde_json::from_value(Value::Object(fields)).map_err(|error| {
        SchemaError::new(
            family.unreadable(),
            format_args!("a {label} has a field of the wrong shape ({error})"),
        )
    })
}

/// What a run report must carry to be worth decoding: the identity of the run, its verdict, and the two count
/// blocks a verdict is judged against. Anything past that is checked by its vocabulary or its type.
const RUN_REPORT_SHAPE: Shape = Shape {
    required: &[
        ("reportSchemaVersion", Kind::Number),
        ("iterationId", Kind::String),
        ("daemonRunId", Kind::String),
        ("daemonBootStamp", Kind::String),
        ("selection", Kind::String),
        ("status", Kind::String),
        ("startedAt", Kind::String),
        ("completedAt", Kind::String),
        ("durationMs", Kind::Number),
        ("ordering", Kind::String),
        ("execution", Kind::Object),
        ("watchdog", Kind::Object),
    ],
    optional: &[
        ("tracesError", Kind::String),
        ("tree", Kind::Object),
        ("treeError", Kind::String),
        ("protocolDiagnostic", Kind::String),
        ("verdictDiagnostic", Kind::String),
    ],
};

/// Reads one run report and refuses anything it cannot read as one.
///
/// The version is checked before the fields: a v2 document that happens to satisfy v3's shape is still a
/// document whose meaning this code does not know, and "it decoded" is not the same as "it means what I think".
pub fn decode_run_report(document: &[u8]) -> Result<RunReport, SchemaError> {
    let fields = object(document, Family::Report, "run report")?;
    require_version(
        &fields,
        "reportSchemaVersion",
        RUN_REPORT_SCHEMA_VERSION,
        "run report",
        Family::Report,
    )?;
    if let Some(detail) = RUN_REPORT_SHAPE.check(&fields, "a run report") {
        return Err(SchemaError::new(Family::Report.field_missing(), detail));
    }
    check_run_report_vocabulary(&fields)?;
    let report: RunReport = typed(fields, Family::Report, "run report")?;
    report.validate()?;
    Ok(report)
}

/// Refuses a word of a run report's vocabularies that this module does not declare, each with its own code.
pub(crate) fn check_run_report_vocabulary(report: &Map<String, Value>) -> Result<(), SchemaError> {
    let null = Value::Null;
    let field = |name: &str| report.get(name).unwrap_or(&null);
    declared_word::<Status>(field("status"), |word| {
        SchemaError::new(
            "report_schema_unknown_status",
            format_args!("a run report has the unknown status {word:?}"),
        )
    })?;
    for case in field("cases").as_array().into_iter().flatten() {
        declared_word::<CaseStatus>(&case["status"], |word| {
            SchemaError::new(
                "report_schema_unknown_case_status",
                format_args!(
                    "a run report has the unknown case status {word:?} on {}.{}",
                    text(&case["className"]),
                    text(&case["name"])
                ),
            )
        })?;
    }
    for failure in field("failures").as_array().into_iter().flatten() {
        declared_word::<FailureKind>(&failure["kind"], |word| {
            SchemaError::new(
                "report_schema_unknown_failure_kind",
                format_args!(
                    "a run report has the unknown failure kind {word:?} on {}",
                    text(&failure["testName"])
                ),
            )
        })?;
    }
    Ok(())
}

/// The report of a run that told nothing: an infrastructure error with no identity, no case and no evidence. It has
/// the current schema version and [`ORDERING`], so it validates.
impl Default for RunReport {
    fn default() -> Self {
        Self {
            report_schema_version: RUN_REPORT_SCHEMA_VERSION,
            iteration_id: String::new(),
            daemon_run_id: String::new(),
            daemon_boot_stamp: String::new(),
            selection: String::new(),
            status: Status::InfrastructureError,
            started_at: String::new(),
            completed_at: String::new(),
            duration_ms: 0.0,
            ordering: ORDERING.to_owned(),
            execution: ExecutionCounts::default(),
            cases: Vec::new(),
            failures: Vec::new(),
            skipped_containers: Vec::new(),
            active_tests: Vec::new(),
            unreported_classes: Vec::new(),
            watchdog: Watchdog::default(),
            evidence: Vec::new(),
            trace_archives: Vec::new(),
            traces_error: None,
            tree: None,
            tree_error: None,
            notes: Vec::new(),
            protocol_diagnostic: None,
            verdict_diagnostic: None,
        }
    }
}

impl RunReport {
    /// Refuses a report whose invariants no type states: its version, its ordering, its trace archives and its
    /// tree pair.
    ///
    /// Separate from [`decode_run_report`] so that the same refusals apply to a report an aggregate embedded,
    /// and to one a builder is about to write.
    pub fn validate(&self) -> Result<(), SchemaError> {
        if self.report_schema_version != RUN_REPORT_SCHEMA_VERSION {
            return Err(SchemaError::new(
                Family::Report.unsupported(),
                format_args!(
                    "a run report declares reportSchemaVersion {}, and this controller reads {RUN_REPORT_SCHEMA_VERSION}",
                    self.report_schema_version
                ),
            ));
        }
        if self.ordering != ORDERING {
            return Err(SchemaError::new(
                "report_schema_unknown_ordering",
                format_args!(
                    "a run report claims the ordering {:?}, and this controller only writes {ORDERING:?}",
                    self.ordering
                ),
            ));
        }
        self.validate_traces()?;
        self.validate_tree()
    }

    /// Refuses an archive this controller would not have written: no path, a negative size, or a bundle with no
    /// id.
    fn validate_traces(&self) -> Result<(), SchemaError> {
        for archive in &self.trace_archives {
            if archive.path.is_empty() || archive.bytes < 0 {
                return Err(SchemaError::new(
                    "report_schema_traces_inconsistent",
                    format_args!(
                        "a run report names the traces archive {:?} of {} bytes",
                        archive.path, archive.bytes
                    ),
                ));
            }
            if archive.bundles.iter().any(|bundle| bundle.id.is_empty()) {
                return Err(SchemaError::new(
                    "report_schema_traces_inconsistent",
                    format_args!("a run report names a bundle with no id in {}", archive.path),
                ));
            }
        }
        Ok(())
    }

    /// Refuses a tree pair this controller does not write: both halves at once, a tree with no head, more paths
    /// than [`MAX_TREE_PATHS`], or a count smaller than the paths it names.
    fn validate_tree(&self) -> Result<(), SchemaError> {
        if self.tree.is_some() && self.tree_error.is_some() {
            return Err(SchemaError::new(
                "report_schema_tree_inconsistent",
                "a run report names both a checkout tree and the reason there is none",
            ));
        }
        if let Some(tree) = &self.tree
            && (tree.head.is_empty() || tree.uncommitted.len() > MAX_TREE_PATHS || tree.uncommitted_count < tree.uncommitted.len())
        {
            return Err(SchemaError::new(
                "report_schema_tree_inconsistent",
                format_args!(
                    "a run report names the checkout tree {:?} with {} of {} uncommitted paths",
                    tree.head,
                    tree.uncommitted.len(),
                    tree.uncommitted_count
                ),
            ));
        }
        Ok(())
    }
}
