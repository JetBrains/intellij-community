//! The controller's progress wire: the facts a command publishes while it works.
//!
//! One stream serves every reader. The terminal renders it as lines and a status footer, `--stream` writes it to
//! stderr as NDJSON, and a journal keeps it on disk. A fact is published once, as an [`Event`], and each reader
//! derives its own form from it, so the prose a person reads and the records an agent parses cannot disagree.
//!
//! A [`Record`] is one NDJSON line, `{schemaVersion, event, at, worker, message, data}`: the shape `--stream`
//! always had, with `at` added, so a reader that parsed the old records still parses these.

use std::borrow::Cow;
use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};
use serde_json::value::RawValue;

use crate::daemon::RunEvent;
use crate::report::Status;

#[cfg(test)]
mod tests;

/// The version every record carries: the controller-wide number.
pub const SCHEMA_VERSION: u32 = crate::supervisor::SCHEMA_VERSION;

vocabulary! {
    /// A record's `event` field.
    pub enum Kind {
        /// A line of information that belongs to no phase and no test.
        Note = "progress",
        /// A phase that started, reported progress, finished or failed.
        Phase = "phase",
        /// A structured record of the run: a daemon event forwarded as the daemon wrote it, or a record of a
        /// command's own, such as a shard that started. Its `data.event` names which.
        Run = "runProgress",
        /// The first record of a run's journal: the run's id and the command line.
        RunStarted = "runStarted",
        /// What the run will execute: one entry for each iteration.
        RunPlanned = "runPlanned",
        /// An iteration's report, persisted on the host.
        IterationReported = "iterationReported",
        /// The last record of a run's journal: how the command left.
        RunFinished = "runFinished",
        /// One scenario's trace, pulled to the host while the run goes on.
        TraceReady = "traceReady",
        /// A choice the controller made about the worker, the daemon or the IDE, and why.
        Decision = "decision",
        /// The checkout the host build read: its HEAD and its uncommitted paths.
        Checkout = "checkout",
        /// How long this work took in earlier runs on this machine.
        Expected = "expected",
        /// What the tests of a `run` or a `shard` concluded, once its reports were read.
        Verdict = "verdict",
        /// What the host build did: the actions that ran, the actions a cache answered, and the critical path.
        BuildSummary = "buildSummary",
    }
}

vocabulary! {
    /// One stage of the work a command does. The set is closed: a new phase is a code change.
    pub enum Phase {
        /// The `bazel build` of the lane on the host.
        HostBuild = "host-build",
        /// Taking a worker. A worker that is not warm is cloned, booted and provisioned here.
        Lease = "lease",
        /// Staging the guest runtime and starting the daemon and its IDE.
        DaemonStart = "daemon-start",
        /// The daemon running the selected tests.
        Iteration = "iteration",
        /// Packing the iteration's scenario traces on the guest and pulling them to the host.
        Traces = "traces",
        /// Giving a self-leased worker back.
        Release = "release",
    }
}

impl Phase {
    /// How a person reads the phase, in the present participle: "building on the host".
    pub const fn label(self) -> &'static str {
        match self {
            Self::HostBuild => "building on the host",
            Self::Lease => "leasing a worker",
            Self::DaemonStart => "starting the daemon",
            Self::Iteration => "running the tests",
            Self::Traces => "pulling the traces",
            Self::Release => "releasing the worker",
        }
    }
}

vocabulary! {
    /// Where a phase is.
    pub enum PhaseState {
        Started = "started",
        Progress = "progress",
        Finished = "finished",
        Failed = "failed",
    }
}

/// One fact a command publishes. Closed: a renderer that matches over it sees every case.
#[derive(Clone, Debug, PartialEq)]
pub enum Event {
    /// A line of information.
    Note(String),
    Phase(PhaseChange),
    /// One record of the daemon's run stream, forwarded unchanged: the record's data is the daemon's own bytes,
    /// so a reader of `--stream` sees exactly what the daemon wrote.
    Daemon(RunEvent),
    /// A command's own record, such as a shard that started or a flake trial that finished, and the one line a
    /// person reads for it. `data` is an object with an `event` field. An empty `line` prints nothing, for a
    /// record whose fact the final answer already states.
    Structured {
        data: serde_json::Value,
        line: String,
    },
    RunStarted(RunStarted),
    RunPlanned(RunPlanned),
    IterationReported(IterationReported),
    RunFinished(RunFinished),
    TraceReady(TraceReady),
    Decision(Decision),
    Checkout(Checkout),
    Expected(Expected),
    Verdict(Box<Verdict>),
    BuildSummary(BuildSummary),
}

impl Event {
    pub const fn kind(&self) -> Kind {
        match self {
            Self::Note(_) => Kind::Note,
            Self::Phase(_) => Kind::Phase,
            Self::Daemon(_) | Self::Structured { .. } => Kind::Run,
            Self::RunStarted(_) => Kind::RunStarted,
            Self::RunPlanned(_) => Kind::RunPlanned,
            Self::IterationReported(_) => Kind::IterationReported,
            Self::RunFinished(_) => Kind::RunFinished,
            Self::TraceReady(_) => Kind::TraceReady,
            Self::Decision(_) => Kind::Decision,
            Self::Checkout(_) => Kind::Checkout,
            Self::Expected(_) => Kind::Expected,
            Self::Verdict(_) => Kind::Verdict,
            Self::BuildSummary(_) => Kind::BuildSummary,
        }
    }

    /// The record's top-level message: the one line a reader of the old `progress` notes still has.
    pub fn message(&self) -> Option<Cow<'_, str>> {
        match self {
            Self::Note(message) => Some(Cow::Borrowed(message)),
            Self::Decision(decision) => Some(Cow::Owned(decision.line())),
            Self::Verdict(verdict) => Some(Cow::Borrowed(&verdict.summary)),
            Self::BuildSummary(summary) => Some(Cow::Owned(summary.line())),
            _ => None,
        }
    }

    /// The record's `data`, or `None` for a note.
    pub fn data(&self) -> Option<Box<RawValue>> {
        fn raw<T: Serialize>(value: &T) -> Box<RawValue> {
            // Every payload of this module serializes to a JSON object with string keys, which cannot fail.
            serde_json::value::to_raw_value(value).expect("a progress payload always serializes")
        }
        Some(match self {
            Self::Note(_) => return None,
            Self::Phase(change) => raw(change),
            Self::Daemon(event) => event.to_raw(),
            Self::Structured { data, .. } => raw(data),
            Self::RunStarted(started) => raw(started),
            Self::RunPlanned(planned) => raw(planned),
            Self::IterationReported(reported) => raw(reported),
            Self::RunFinished(finished) => raw(finished),
            Self::TraceReady(ready) => raw(ready),
            Self::Decision(decision) => raw(decision),
            Self::Checkout(checkout) => raw(checkout),
            Self::Expected(expected) => raw(expected),
            Self::Verdict(verdict) => raw(verdict),
            Self::BuildSummary(summary) => raw(summary),
        })
    }
}

/// One step of a [`Phase`]. `elapsed_ms` counts from the phase's start, and `error` is set only for
/// [`PhaseState::Failed`].
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PhaseChange {
    pub phase: Phase,
    pub state: PhaseState,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub detail: Option<String>,
    pub elapsed_ms: u64,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

/// Opens a run. `run_id` names the run's directory under the runtime root, `runs/<runId>`, and it is also the
/// holder of a lease the run takes for itself.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RunStarted {
    pub run_id: String,
    pub command: String,
    pub args: Vec<String>,
    /// The repository the run builds.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub checkout: Option<String>,
}

/// One iteration a run will execute. `lane` is absent for a class, an FQN or a whole lane.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct PlannedIteration {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub lane: Option<String>,
    pub selection: String,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub classes: Vec<String>,
}

/// The plan of a run, once its selection resolved.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct RunPlanned {
    pub iterations: Vec<PlannedIteration>,
}

/// An iteration whose report the host persisted.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IterationReported {
    pub iteration_id: String,
    pub daemon_run_id: String,
    pub selection: String,
    pub status: Status,
    pub report_path: String,
    pub tests_failed: u32,
    pub tests_started: u32,
}

/// Closes a run. `exit_code` is the process's exit code; `code` is the refusal's code, absent for a green run.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RunFinished {
    pub exit_code: i32,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub code: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub message: Option<String>,
}

/// One scenario's trace on the host. `zip` is the real path of the zip that holds it, `entry` the bundle's
/// directory in the zip, and `bundle_id` the trace viewer's id for the two: the viewer opens it as
/// `/air/runs/trace?src=<bundleId>`.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TraceReady {
    pub iteration_id: String,
    pub bundle_id: String,
    pub test_class: String,
    pub scenario: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub flow: Option<String>,
    pub status: String,
    pub has_video: bool,
    pub zip: String,
    pub entry: String,
}

/// One NDJSON line of the stream.
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Record {
    pub schema_version: u32,
    pub event: Kind,
    /// UTC, milliseconds, `Z`: `2026-09-24T12:00:00.005Z`.
    pub at: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub worker: Option<String>,
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub message: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub data: Option<Box<RawValue>>,
}

/// UTC with milliseconds and `Z` (`2026-09-28T12:00:00.000Z`): the spelling of a record's `at`, and of every other
/// instant the controller writes into a document or a receipt, so one reader parses all of them.
pub fn stamp(at: jiff::Timestamp) -> String {
    at.strftime("%Y-%m-%dT%H:%M:%S%.3fZ").to_string()
}

impl Record {
    /// The line an event is written as, stamped at `at`. `worker` is `None` for an event of no worker.
    pub fn new(event: &Event, at: jiff::Timestamp, worker: Option<&str>) -> Self {
        Self {
            schema_version: SCHEMA_VERSION,
            event: event.kind(),
            at: stamp(at),
            worker: worker.map(str::to_owned),
            message: event.message().map(Cow::into_owned).unwrap_or_default(),
            data: event.data(),
        }
    }
}

vocabulary! {
    /// What a [`Decision`] is about.
    pub enum Subject {
        Worker = "worker",
        Daemon = "daemon",
        Shares = "shares",
        Ide = "ide",
        Jars = "jars",
    }
}

/// A choice the controller made, and why: a daemon that restarts, shares that remount, an IDE that is reused.
/// `action` is what was done, such as `restart` or `reuse`. The record also carries the one line a person reads
/// as its message, so a reader of the old `progress` notes still has the text.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct Decision {
    pub subject: Subject,
    pub action: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub reason: Option<String>,
}

impl Decision {
    /// The decision as one line of prose: `daemon restart: the stable runtime or JBR changed`.
    pub fn line(&self) -> String {
        match &self.reason {
            Some(reason) => format!("{} {}: {reason}", self.subject, self.action),
            None => format!("{} {}", self.subject, self.action),
        }
    }
}

/// The checkout the host build read. `uncommitted` counts the uncommitted paths under the lane's source roots,
/// because a red lane on a dirty tree can come from another session's edits. `error` is set, and the other
/// fields are empty, when the checkout could not be read.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct Checkout {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub head: Option<String>,
    pub uncommitted: u32,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

/// The median of the newest measurements of one thing, and how many measurements it is the median of.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct Typical {
    pub ms: u64,
    pub samples: u32,
}

/// How long a phase took in earlier runs. `key` is the phase's started detail for a phase whose cost depends on
/// what it runs (the iteration and the traces), and absent for every other phase.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct PhaseTypical {
    pub phase: Phase,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub key: Option<String>,
    #[serde(flatten)]
    pub typical: Typical,
}

/// How long this run's work took in earlier runs on this machine, read from their journals. `run` is the whole
/// command, for the same command line. A class's time is its wall time in the iteration, from the end of the
/// class before it to its last test, so it includes the fixture time that no test case reports.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct Expected {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub run: Option<Typical>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub phases: Vec<PhaseTypical>,
    #[serde(default, skip_serializing_if = "BTreeMap::is_empty")]
    pub classes: BTreeMap<String, Typical>,
}

impl Expected {
    /// The typical time of a phase with the key, or `None` when no earlier run measured it.
    pub fn phase(&self, phase: Phase, key: Option<&str>) -> Option<&Typical> {
        self.phases
            .iter()
            .find(|typical| typical.phase == phase && typical.key.as_deref() == key)
            .map(|typical| &typical.typical)
    }
}

/// What the tests of a `run` or a `shard` concluded, as one fact. The command publishes it once, when its
/// reports are read, and each human form renders it; a renderer that rendered a verdict has answered the
/// command, so no second text follows.
///
/// `summary` is the verdict as one line: the record's message, and the message of the command's refusal. A
/// refusal that has no report, such as a failed build, has no verdict; neither has `flake`, whose answer is a
/// measurement of the tests and not a verdict on them.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Verdict {
    /// For a run of two or more lanes, the status of the worst lane.
    pub status: Status,
    /// The refusal's code; absent for a verdict that passed.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub code: Option<String>,
    pub summary: String,
    pub counts: VerdictCounts,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub failures: Vec<Failure>,
    /// One command for each failed class, as the arguments after the program: `run <FQN>`.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub rerun: Vec<String>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub skipped: Vec<Skip>,
    /// Each class that the plan selected and the run never reported.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub unreported: Vec<String>,
    /// One entry for each lane of a run of two or more lanes.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub lanes: Vec<LaneVerdict>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub shards: Option<ShardSplit>,
    /// The lease the command took for itself; absent under the caller's receipt.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub lease: Option<VerdictLease>,
    /// The checkout that the build read, as the report states it.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub checkout: Option<Checkout>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub traces: Option<VerdictTraces>,
    /// The iteration's timing line, such as `build 244.9s stamp 1.7s tests 77.3s`.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub timing: Option<String>,
    /// The iteration's report on the host, or the shard aggregate.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub report: Option<String>,
}

impl Verdict {
    pub fn passed(&self) -> bool {
        self.status == Status::Passed
    }
}

const fn is_zero(count: &u32) -> bool {
    *count == 0
}

/// The daemon's count of what ran. A passed test is a started test that did not fail.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VerdictCounts {
    pub started: u32,
    pub failed: u32,
    pub skipped: u32,
    #[serde(default, skip_serializing_if = "is_zero")]
    pub containers_skipped: u32,
    #[serde(default, skip_serializing_if = "is_zero")]
    pub container_failures: u32,
}

impl VerdictCounts {
    /// The started tests that did not fail.
    pub const fn passed(&self) -> u32 {
        self.started.saturating_sub(self.failed)
    }
}

impl std::ops::Add for VerdictCounts {
    type Output = Self;

    /// The counts of two iterations together.
    fn add(self, other: Self) -> Self {
        Self {
            started: self.started + other.started,
            failed: self.failed + other.failed,
            skipped: self.skipped + other.skipped,
            containers_skipped: self.containers_skipped + other.containers_skipped,
            container_failures: self.container_failures + other.container_failures,
        }
    }
}

/// One failed test or class of a [`Verdict`]. `class` is the class's FQN, or the suite when the report names no
/// class; `message` is the first line of the failure; `trace` is the scenario's trace, when one arrived.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct Failure {
    pub class: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub name: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub message: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub trace: Option<TraceRef>,
}

/// One scenario's trace, as the trace viewer names it.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TraceRef {
    pub bundle_id: String,
    pub test_class: String,
    pub scenario: String,
    pub status: String,
    #[serde(default, skip_serializing_if = "std::ops::Not::not")]
    pub has_video: bool,
}

/// One class that a JUnit condition ruled out, and why.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct Skip {
    pub name: String,
    pub reason: String,
}

/// One lane of a run of two or more lanes: the verdict a run of that lane alone would give, and its report.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct LaneVerdict {
    pub lane: String,
    pub status: Status,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub code: Option<String>,
    pub summary: String,
    pub counts: VerdictCounts,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub report: Option<String>,
}

/// How a `shard` divided its lane: the shard count, one label for each shard such as `1:air-docker-1:84s`, the
/// wall time from the first start to the last end, and the machine time of all shards.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ShardSplit {
    pub count: u32,
    pub labels: Vec<String>,
    pub wall_ms: u64,
    pub machine_ms: u64,
}

vocabulary! {
    /// What became of the leases a command took for itself.
    pub enum LeaseDisposition {
        Released = "released",
        ReleaseFailed = "release_failed",
        Kept = "kept",
    }
}

/// The leases a command took for itself; `receipts` is the receipt of each worker that is still held.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct VerdictLease {
    pub workers: Vec<String>,
    pub disposition: LeaseDisposition,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub receipts: Vec<String>,
}

impl VerdictLease {
    /// Whether every worker was given back.
    pub fn released(&self) -> bool {
        self.disposition == LeaseDisposition::Released
    }
}

/// The scenario traces of the run: how many arrived, the ones that did not pass, how many have a video, and the
/// directory they are in. `error` is why a pull failed.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VerdictTraces {
    pub scenarios: u32,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub not_passed: Vec<TraceRef>,
    pub with_video: u32,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub directory: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

/// What a host build did, from the lines Bazel writes at its end. A reader names it beside a build that was
/// slower than usual: a build that ran 900 actions and one that ran none take different times for a good reason.
///
/// `processes` is Bazel's count of processes; `ran` the processes that executed, without the cache hits and the
/// internal ones; `cached` the actions a cache answered (action, disk and remote cache).
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BuildSummary {
    pub processes: u32,
    pub ran: u32,
    pub cached: u32,
    pub critical_path_ms: u64,
}

impl BuildSummary {
    /// The summary as one line of prose: `970 actions ran, 6684 cached, critical path 187.7s`. A build whose log
    /// named no critical path has none on the line.
    pub fn line(&self) -> String {
        let mut line = format!("{} actions ran, {} cached", self.ran, self.cached);
        if self.critical_path_ms > 0 {
            line.push_str(&format!(", critical path {:.1}s", self.critical_path_ms as f64 / 1000.0));
        }
        line
    }
}
