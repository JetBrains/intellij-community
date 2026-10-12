//! The two aggregates: N shards of one lane folded into one verdict, and K repeat trials folded into a per-class
//! flake rate.
//!
//! One rule shapes both, and it is the reason they are documents rather than summaries: **an aggregate may only
//! ever be as green as its worst entry.** Every false-green guard has already been folded into some entry's
//! [`Status::InfrastructureError`], so the aggregates re-use that verdict rather than re-deriving one. The
//! arithmetic - the merge ladder, the Wilson intervals, the bucketing - belongs to the host. What is here is the
//! shape, the vocabularies, and the refusals.

use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};

use super::{
    ExecutionCounts, Failure, Family, RUN_REPORT_SCHEMA_VERSION, RunReport, SchemaError, SkippedContainer, Status, Suite, XmlCounts,
    check_run_report_vocabulary, declared_word, object, require_version, typed,
};
use crate::json::{Kind, Shape};

#[cfg(test)]
mod tests;

/// The version of both aggregate documents: the same noun at two shapes. Unrelated to the run report's version,
/// because an aggregate embeds reports and the two move independently.
pub const AGGREGATE_SCHEMA_VERSION: u32 = 1;

/// The only confidence a Wilson interval is published at. A constant, so a caller cannot quietly ask for a nicer
/// one: a 90% interval on the same sample is narrower and says less.
pub const CONFIDENCE_95: f64 = 0.95;

// --- the vocabularies -------------------------------------------------------------------------------------

vocabulary! {
    /// Which aggregate a document is.
    pub enum AggregateKind {
        Shard = "shard",
        Flake = "flake",
    }
}

vocabulary! {
    /// One run's status inside an aggregate: each run report [`Status`], plus the one thing a run report cannot
    /// say about itself.
    pub enum EntryStatus {
        Passed = "passed",
        Failed = "failed",
        NoTests = "no_tests",
        AllSkipped = "all_skipped",
        InfrastructureError = "infrastructure_error",
        /// A run that never produced a report at all - a lease failure, a host build failure, a daemon that died
        /// before it wrote anything. Not one of the daemon's verdicts, so not filed under
        /// `infrastructure_error`, which describes a run that did report.
        NoReport = "no_report",
    }
}

impl From<Status> for EntryStatus {
    fn from(status: Status) -> Self {
        match status {
            Status::Passed => Self::Passed,
            Status::Failed => Self::Failed,
            Status::NoTests => Self::NoTests,
            Status::AllSkipped => Self::AllSkipped,
            Status::InfrastructureError => Self::InfrastructureError,
        }
    }
}

impl EntryStatus {
    /// The run report's own status, or `None` for a run that never reported.
    pub const fn report_status(self) -> Option<Status> {
        match self {
            Self::Passed => Some(Status::Passed),
            Self::Failed => Some(Status::Failed),
            Self::NoTests => Some(Status::NoTests),
            Self::AllSkipped => Some(Status::AllSkipped),
            Self::InfrastructureError => Some(Status::InfrastructureError),
            Self::NoReport => None,
        }
    }
}

vocabulary! {
    /// What a flake trial did to the worker before it ran. Not interchangeable: `warm` measures the lane as a
    /// developer experiences it and is the only policy that can observe an ordering carrier, while
    /// `fresh_worker` costs a cold reset and buys trial independence.
    pub enum ResetPolicy {
        Warm = "warm",
        FreshIde = "fresh_ide",
        FreshWorker = "fresh_worker",
    }
}

vocabulary! {
    /// Why a shard verdict is not green. Every non-green verdict but `failed` carries one, and the codes are not
    /// equally cheap to act on: `shard_overlap` is a balancer bug in this controller, while
    /// `shard_infrastructure_error` is a worker to go and look at.
    pub enum ShardDiagnosticCode {
        /// No shard was planned at all, which is a planner bug and never a lane result.
        SetEmpty = "shard_set_empty",
        MissingReport = "shard_missing_report",
        InfrastructureError = "shard_infrastructure_error",
        IncompleteSource = "shard_incomplete_source",
        DiscoveredNothing = "shard_discovered_nothing",
        /// Two shards ran the same class. The merged counts are double-counted and the split is not a
        /// partition, so the verdict is refused rather than published with a footnote.
        Overlap = "shard_overlap",
        ExecutedNothing = "shard_executed_nothing",
    }
}

vocabulary! {
    /// What K trials concluded about one class.
    ///
    /// Six buckets and not two, because "flaky" is only meaningful once the classes that are *not* flaky have
    /// been separated from the classes nobody measured. A class that failed every trial is broken, not flaky.
    pub enum FlakeBucket {
        Stable = "stable",
        Flaky = "flaky",
        /// Failed every trial it executed in. Excluded from the rate: it is not a coin.
        Broken = "broken",
        /// Never executed in an authoritative trial.
        NotMeasured = "notMeasured",
        /// Sometimes ran and sometimes was ruled out by a condition: a statement about the guest's installed CLIs
        /// rather than about the test.
        UnstablePrerequisite = "unstablePrerequisite",
        /// Executed too few times for either answer.
        InsufficientEvidence = "insufficientEvidence",
    }
}

vocabulary! {
    /// The direct probe of the warm-IDE ordering question: whether a class only fails on the first trial of a
    /// chain, or only after one. Optional on a class, because an absent signature is "not this class's story".
    pub enum OrderSignature {
        FirstTrialOnly = "first_trial_only",
        AfterFirstTrial = "after_first_trial",
    }
}

// --- the shared entry -------------------------------------------------------------------------------------

/// The CLI error an attempt failed with.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct AggregateError {
    pub code: String,
    pub message: String,
}

/// One run inside an aggregate.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Entry {
    pub worker: String,
    pub status: EntryStatus,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub iteration_id: Option<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub daemon_run_id: Option<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub report_path: Option<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub verdict_diagnostic: Option<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub error: Option<AggregateError>,
    /// The whole run report, inlined only when the entry is not green.
    ///
    /// A passing run is recoverable from `report_path` and nobody reads it; the reader of an aggregate is looking
    /// for the entry that went wrong, and that one has to be readable without a second file open. `no_tests` and
    /// `all_skipped` count as not green here on purpose.
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub report: Option<Box<RunReport>>,
}

impl Entry {
    pub fn new(worker: impl Into<String>, status: EntryStatus) -> Self {
        Self {
            worker: worker.into(),
            status,
            iteration_id: None,
            daemon_run_id: None,
            report_path: None,
            verdict_diagnostic: None,
            error: None,
            report: None,
        }
    }
}

// --- the shard verdict ------------------------------------------------------------------------------------

/// One shard's run. The entry's fields are flat on the wire beside `shardIndex`.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ShardEntry {
    #[serde(flatten)]
    pub entry: Entry,
    pub shard_index: u32,
}

/// Which classes a shard actually ran, so the split can be audited after the fact rather than trusted.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ShardCoverage {
    pub shard_index: u32,
    pub worker: String,
    /// Classes with at least one test case in this shard's XML, whatever the outcome.
    #[serde(default)]
    pub classes: Vec<String>,
    /// Ruled out by a condition here. Not coverage, and not part of the overlap check: two shards may both skip a
    /// class for the same missing CLI without either having run it.
    #[serde(default)]
    pub skipped_classes: Vec<String>,
}

/// One class that more than one shard ran.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ShardOverlapEntry {
    pub class_name: String,
    #[serde(default)]
    pub shard_indexes: Vec<u32>,
}

/// N shards of one lane, merged.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ShardVerdict {
    pub aggregate_schema_version: u32,
    pub kind: AggregateKind,
    pub status: Status,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub code: Option<ShardDiagnosticCode>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub diagnostic: Option<String>,
    pub shard_count: u32,
    #[serde(default)]
    pub entries: Vec<ShardEntry>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub started_at: Option<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub completed_at: Option<String>,
    /// First start to last completion, which is what a sharded lane actually cost.
    pub wall_duration_ms: f64,
    /// The sum of the shards' own durations: the machine time. The two together are the only honest statement
    /// of what sharding bought.
    pub total_duration_ms: f64,
    pub execution: ExecutionCounts,
    pub xml: XmlCounts,
    /// Always [`super::ORDERING`].
    pub ordering: String,
    #[serde(default)]
    pub suites: Vec<Suite>,
    #[serde(default)]
    pub failures: Vec<Failure>,
    #[serde(default)]
    pub skipped_containers: Vec<SkippedContainer>,
    #[serde(default)]
    pub coverage: Vec<ShardCoverage>,
    /// The union of every shard's executed classes: what this verdict actually speaks for, which is narrower
    /// than the lane whenever a shard failed to report.
    #[serde(default)]
    pub executed_classes: Vec<String>,
    /// Non-empty only for [`ShardDiagnosticCode::Overlap`], and then it names the balancer bug.
    #[serde(default)]
    pub overlaps: Vec<ShardOverlapEntry>,
}

// --- the flake summary ------------------------------------------------------------------------------------

/// A two-sided 95% score interval on a failure rate.
#[derive(Clone, Copy, Debug, Default, PartialEq, Serialize, Deserialize)]
pub struct WilsonInterval {
    pub low: f64,
    pub high: f64,
    pub confidence: f64,
}

/// What K trials concluded about one class.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FlakeClass {
    pub class_name: String,
    pub bucket: FlakeBucket,
    /// `n`: authoritative trials in which this class executed.
    pub executed_trials: u32,
    /// `f`: of those, the ones in which it failed. Never greater than `n`.
    pub failed_trials: u32,
    /// Authoritative trials in which a condition ruled the class out instead of running it.
    pub skipped_trials: u32,
    #[serde(default)]
    pub executed_ordinals: Vec<u32>,
    #[serde(default)]
    pub failed_ordinals: Vec<u32>,
    #[serde(default)]
    pub skipped_ordinals: Vec<u32>,
    /// `f/n`, and only for [`FlakeBucket::Flaky`]: every other bucket is excluded from the rate. A rate on a
    /// broken class would be 1.0 and would read as maximal flakiness.
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub rate: Option<f64>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub interval: Option<WilsonInterval>,
    /// Every distinct reason JUnit gave for ruling this class out, which names the missing CLI and the host.
    #[serde(default)]
    pub skip_reasons: Vec<String>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub order_signature: Option<OrderSignature>,
}

/// One trial: both the aggregate's embedded entry and the flake matrix's row. One record and not two, because
/// two records that must agree eventually will not.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FlakeTrial {
    #[serde(flatten)]
    pub entry: Entry,
    pub ordinal: u32,
    pub reset_policy: ResetPolicy,
    /// Wall time, which a trial that never reported still has.
    pub duration_ms: f64,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub guest_free_bytes_before: Option<i64>,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub guest_free_bytes_after: Option<i64>,
}

/// One trial thrown out, with the code that threw it out.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct FlakeExclusion {
    pub ordinal: u32,
    pub worker: String,
    pub code: String,
    pub message: String,
}

/// K trials of one selection, folded into a per-class rate.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FlakeSummary {
    pub aggregate_schema_version: u32,
    pub kind: AggregateKind,
    pub attempted_trials: u32,
    pub authoritative_trials: u32,
    #[serde(default)]
    pub trials: Vec<FlakeTrial>,
    /// Every trial thrown out: the denominator of the honesty guard.
    #[serde(default)]
    pub infrastructure_trials: Vec<FlakeExclusion>,
    #[serde(default)]
    pub classes: Vec<FlakeClass>,
    #[serde(default)]
    pub flake_set: Vec<String>,
    #[serde(default)]
    pub broken_set: Vec<String>,
    #[serde(default)]
    pub stable_set: Vec<String>,
    #[serde(default)]
    pub not_measured: Vec<String>,
    #[serde(default)]
    pub unstable_prerequisite: Vec<String>,
    #[serde(default)]
    pub insufficient_evidence: Vec<String>,
    #[serde(default)]
    pub order_suspects: Vec<String>,
    /// Flaky classes over measured classes (stable plus flaky). Nothing else is in either half.
    pub lane_flake_rate: f64,
    pub measured_classes: u32,
    pub lane_flake_interval: WilsonInterval,
    /// Whether this summary is a number anyone may quote.
    ///
    /// Excluded trials are not missing at random: a daemon that dies during the relaunch-heavy classes removes
    /// exactly the classes most likely to flake, so past a fraction of them the survivors are a biased sample and
    /// the rate reads *low*. A summary that is not reportable still publishes every class; what it withholds is
    /// the headline. Required, because either default would be a guess.
    pub reportable: bool,
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub not_reportable_reason: Option<String>,
}

// --- decoding ---------------------------------------------------------------------------------------------

const SHARD_VERDICT_SHAPE: Shape = Shape {
    required: &[
        ("aggregateSchemaVersion", Kind::Number),
        ("kind", Kind::String),
        ("status", Kind::String),
        ("shardCount", Kind::Number),
        ("wallDurationMs", Kind::Number),
        ("totalDurationMs", Kind::Number),
        ("ordering", Kind::String),
        ("execution", Kind::Object),
        ("xml", Kind::Object),
    ],
    optional: &[("code", Kind::String), ("diagnostic", Kind::String)],
};

const FLAKE_SUMMARY_SHAPE: Shape = Shape {
    required: &[
        ("aggregateSchemaVersion", Kind::Number),
        ("kind", Kind::String),
        ("attemptedTrials", Kind::Number),
        ("authoritativeTrials", Kind::Number),
        ("laneFlakeRate", Kind::Number),
        ("measuredClasses", Kind::Number),
        ("laneFlakeInterval", Kind::Object),
        ("reportable", Kind::Boolean),
    ],
    optional: &[("notReportableReason", Kind::String)],
};

/// Reads one shard verdict and refuses anything it cannot read as one.
pub fn decode_shard_verdict(document: &[u8]) -> Result<ShardVerdict, SchemaError> {
    let label = "shard verdict";
    let fields = aggregate_fields(document, &SHARD_VERDICT_SHAPE, AggregateKind::Shard, label)?;
    declared_word::<Status>(&fields["status"], |word| {
        SchemaError::new(
            "aggregate_schema_unknown_status",
            format_args!("a shard verdict has the unknown status {word:?}"),
        )
    })?;
    if let Some(code) = fields.get("code") {
        declared_word::<ShardDiagnosticCode>(code, |word| {
            SchemaError::new(
                "aggregate_schema_unknown_code",
                format_args!("a shard verdict has the unknown diagnostic {word:?}"),
            )
        })?;
    }
    for entry in array(&fields, "entries") {
        check_entry_vocabulary(entry, label)?;
    }
    let verdict: ShardVerdict = typed(fields, Family::Aggregate, label)?;

    if verdict.ordering != super::ORDERING {
        return Err(SchemaError::new(
            "aggregate_schema_unknown_ordering",
            format_args!(
                "a shard verdict claims the ordering {:?}, and this controller only writes {:?}",
                verdict.ordering,
                super::ORDERING
            ),
        ));
    }
    // Every status except `failed` must name a diagnostic, and the exception is the point rather than a gap.
    //
    // `failed` is self-describing: the reader has a `failures` array naming each test, which is the diagnosis. A
    // code beside it could only restate the status, and a member meaning "the tests failed" would be the one
    // entry describing the *lane* working correctly. Every other non-green status is a guard whose cause is not
    // in the document, and a reader who found one without a code would have a red result and nothing to act on.
    if !matches!(verdict.status, Status::Passed | Status::Failed) && verdict.code.is_none() {
        return Err(SchemaError::new(
            "aggregate_schema_unnamed_verdict",
            format_args!("a shard verdict is {:?} and names no diagnostic code", verdict.status.as_str()),
        ));
    }
    for entry in &verdict.entries {
        validate_entry(&entry.entry)?;
    }
    Ok(verdict)
}

/// Reads one flake summary and refuses anything it cannot read as one.
pub fn decode_flake_summary(document: &[u8]) -> Result<FlakeSummary, SchemaError> {
    let label = "flake summary";
    let fields = aggregate_fields(document, &FLAKE_SUMMARY_SHAPE, AggregateKind::Flake, label)?;
    for class in array(&fields, "classes") {
        let class_name = class["className"].as_str().unwrap_or_default();
        declared_word::<FlakeBucket>(&class["bucket"], |word| {
            SchemaError::new(
                "aggregate_schema_unknown_bucket",
                format_args!("a flake summary puts {class_name} in the unknown bucket {word:?}"),
            )
        })?;
        declared_word::<OrderSignature>(&class["orderSignature"], |word| {
            SchemaError::new(
                "aggregate_schema_unknown_order_signature",
                format_args!("a flake summary gives {class_name} the unknown order signature {word:?}"),
            )
        })?;
    }
    for trial in array(&fields, "trials") {
        check_entry_vocabulary(trial, label)?;
        declared_word::<ResetPolicy>(&trial["resetPolicy"], |word| {
            SchemaError::new(
                "aggregate_schema_unknown_reset",
                format_args!("a flake summary has a trial with the unknown reset policy {word:?}"),
            )
        })?;
    }
    let summary: FlakeSummary = typed(fields, Family::Aggregate, label)?;

    if summary.lane_flake_interval.confidence != CONFIDENCE_95 {
        return Err(SchemaError::new(
            "aggregate_schema_unknown_confidence",
            format_args!(
                "a flake summary publishes a {} interval, and this controller only writes {CONFIDENCE_95}",
                summary.lane_flake_interval.confidence
            ),
        ));
    }
    for class in &summary.classes {
        // `f > n` is not a reading of a strange document, it is arithmetic that cannot have happened, and it makes
        // every rate and interval derived from the pair meaningless.
        if class.failed_trials > class.executed_trials {
            return Err(SchemaError::new(
                "aggregate_schema_impossible_counts",
                format_args!(
                    "a flake summary says {} failed {} of {} trials",
                    class.class_name, class.failed_trials, class.executed_trials
                ),
            ));
        }
    }
    for trial in &summary.trials {
        validate_entry(&trial.entry)?;
    }
    Ok(summary)
}

/// The shared front half of both decoders: the JSON object, the version, the declared shape, and the kind.
fn aggregate_fields(document: &[u8], shape: &Shape, expected: AggregateKind, label: &str) -> Result<Map<String, Value>, SchemaError> {
    let fields = object(document, Family::Aggregate, label)?;
    require_version(
        &fields,
        "aggregateSchemaVersion",
        AGGREGATE_SCHEMA_VERSION,
        label,
        Family::Aggregate,
    )?;
    if let Some(detail) = shape.check(&fields, &format!("a {label}")) {
        return Err(SchemaError::new(Family::Aggregate.field_missing(), detail));
    }
    let kind = &fields["kind"];
    if kind.as_str() != Some(expected.as_str()) {
        return Err(SchemaError::new(
            "aggregate_schema_wrong_kind",
            format_args!("a {label} declares the kind {kind}"),
        ));
    }
    Ok(fields)
}

fn array<'a>(fields: &'a Map<String, Value>, name: &str) -> impl Iterator<Item = &'a Value> {
    fields.get(name).and_then(Value::as_array).into_iter().flatten()
}

/// Refuses an entry's unknown status, and an embedded report at another version or with an unknown word: a
/// document this controller refuses on its own must not become readable by being wrapped. The version comes
/// first, so an older report says which rather than which word moved.
fn check_entry_vocabulary(entry: &Value, label: &str) -> Result<(), SchemaError> {
    declared_word::<EntryStatus>(&entry["status"], |word| {
        SchemaError::new(
            "aggregate_schema_unknown_entry_status",
            format_args!(
                "a {label} has an entry on {} with the unknown status {word:?}",
                entry["worker"].as_str().unwrap_or_default()
            ),
        )
    })?;
    if let Value::Object(report) = &entry["report"] {
        if let Some(version) = report.get("reportSchemaVersion").and_then(Value::as_i64)
            && version != i64::from(RUN_REPORT_SCHEMA_VERSION)
        {
            return Err(embedded_version(label, version));
        }
        check_run_report_vocabulary(report)?;
    }
    Ok(())
}

fn validate_entry(entry: &Entry) -> Result<(), SchemaError> {
    match &entry.report {
        Some(report) => report.validate(),
        None => Ok(()),
    }
}

fn embedded_version(label: &str, version: i64) -> SchemaError {
    SchemaError::new(
        Family::Report.unsupported(),
        format_args!("a {label} embeds a run report at version {version}, and this controller reads {RUN_REPORT_SCHEMA_VERSION}"),
    )
}
