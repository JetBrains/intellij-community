//! The arithmetic over many run reports: N shards of one lane folded into one verdict, and K repeat trials folded
//! into a per-class flake rate.
//!
//! One rule shapes every function here, and it is why both halves are ladders rather than sums: **an aggregate may
//! only ever be as green as its worst entry.** Every false-green guard this controller owns - missing, stale,
//! truncated, malformed or oversized XML, an absent summary, a summary-versus-XML count mismatch, a watchdog
//! expiry - has already been folded into one entry's `infrastructure_error` by [`crate::report`], so this module
//! *re-uses that verdict* instead of re-deriving one. A second derivation would be a second opinion, and two
//! opinions that must agree eventually will not.
//!
//! The second rule is that no branch reaches a caller unnamed. A red aggregate carries a [`ShardDiagnosticCode`],
//! because the diagnoses are not equally cheap to act on: `shard_overlap` is a balancer bug in this controller and
//! `shard_infrastructure_error` is a worker to go and look at.

use std::collections::{BTreeMap, BTreeSet};

use avl_base::format::whole_percent;
use avl_wire::report::{
    AGGREGATE_SCHEMA_VERSION, AggregateError, AggregateKind, CONFIDENCE_95, CaseStatus, Entry, EntryStatus, ExecutionCounts, FlakeBucket,
    FlakeClass, FlakeExclusion, FlakeSummary, FlakeTrial, Integrity, ORDERING, OrderSignature, ResetPolicy, RunReport, ShardCoverage,
    ShardDiagnosticCode, ShardEntry, ShardOverlapEntry, ShardVerdict, Status, WilsonInterval, XmlCounts,
};
use bt_junit::{Suite, compare_suites_by_timestamp};

use crate::report::instant_millis;

mod persist;
#[cfg(test)]
mod tests;

pub use persist::{aggregate_path, persist_flake_summary, persist_shard_verdict};

/// The two-sided 95% normal quantile, to the precision an `f64` holds. Written out rather than computed: an
/// approximation of the inverse normal CDF would make the published interval depend on which approximation was
/// in the file.
pub const WILSON_Z_95: f64 = 1.959_963_984_540_054;

/// How many shards or classes a diagnostic names before it counts the rest. Twelve labels on one line is not more
/// information than eight and a count; it is the same information no one reads to the end of.
pub const MAX_DIAGNOSTIC_ITEMS: usize = 8;

/// The share of discarded trials past which a flake rate is not a flake rate.
pub const MAX_EXCLUDED_TRIAL_FRACTION: f64 = 0.2;

/// The share of observed classes below which the lane rate describes a remnant, not the lane.
///
/// The `air-linux-1` run of 2026-08-23 that decided this measured 1 of 18 observed classes - trial 1 truncated the
/// lane and trials 2-12 honestly reported `all_skipped` over a lost IDE - and still published `reportable: true`
/// with a 100% lane flake rate, because the excluded-trial guard never fires when the empty trials count as
/// authoritative. Half, so the published rate always describes at least as many classes as it omits.
pub const MIN_MEASURED_CLASS_FRACTION: f64 = 0.5;

/// The statuses that mean "this run measured the tests".
///
/// `no_tests` is deliberately absent: a run that discovered nothing measured nothing, and folding it in as a
/// trial with zero failures is how a broken selector becomes evidence of stability. `all_skipped` is present
/// because a class a condition ruled out is a fact about the guest the trial genuinely observed; it counts as a
/// skip, never as a pass.
const fn is_authoritative(status: Status) -> bool {
    matches!(status, Status::Passed | Status::Failed | Status::AllSkipped)
}

// --- what a caller hands in -----------------------------------------------------------------------------------

/// One run as the controller attempted it: the report if there is one, and otherwise why there is not.
///
/// `report` is `None` for a run that never reported at all - a lease failure, a host build failure, a daemon that
/// died before writing anything - which is a different fact from a run that reported an `infrastructure_error`,
/// and the two get different names in an aggregate. The identity fields fall back to the report's own answer when
/// the caller did not say.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Attempt {
    pub worker: String,
    pub iteration_id: Option<String>,
    pub daemon_run_id: Option<String>,
    pub report_path: Option<String>,
    pub report: Option<RunReport>,
    /// The refusal the attempt failed with; the only account of why a run is missing. A trial may carry both, when
    /// the command that produced a report still failed.
    pub error: Option<AggregateError>,
}

/// One shard's run of a partitioned lane.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct ShardAttempt {
    pub attempt: Attempt,
    pub shard_index: u32,
}

/// One repeat trial of the same selection.
#[derive(Clone, Debug, PartialEq)]
pub struct TrialAttempt {
    pub attempt: Attempt,
    pub trial_ordinal: u32,
    pub reset_policy: ResetPolicy,
    /// The wall time the controller measured, which exists even for a trial that never produced a report. `None`
    /// because zero is a measurement and absent is not.
    pub duration_ms: Option<f64>,
    pub guest_free_bytes_before: Option<i64>,
    pub guest_free_bytes_after: Option<i64>,
}

// --- the interval ---------------------------------------------------------------------------------------------

/// The Wilson score interval, closed form, two-sided 95%.
///
/// 1/3 and 3/9 are the same point estimate and very different evidence, and a flake rate published without that
/// distinction invites a decision the sample cannot support. Zero trials answer the whole unit interval rather
/// than a division by zero: nothing is known, and a lane with no measured classes must not publish NaN.
pub fn wilson_interval(failures: u32, trials: u32) -> WilsonInterval {
    if trials == 0 {
        return WilsonInterval {
            low: 0.0,
            high: 1.0,
            confidence: CONFIDENCE_95,
        };
    }
    let n = f64::from(trials);
    let proportion = f64::from(failures) / n;
    let z_squared = WILSON_Z_95 * WILSON_Z_95;
    let denominator = 1.0 + z_squared / n;
    let center = (proportion + z_squared / (2.0 * n)) / denominator;
    let margin = (WILSON_Z_95 / denominator) * (proportion * (1.0 - proportion) / n + z_squared / (4.0 * n * n)).sqrt();
    WilsonInterval {
        low: (center - margin).clamp(0.0, 1.0),
        high: (center + margin).clamp(0.0, 1.0),
        confidence: CONFIDENCE_95,
    }
}

// --- what a report says about classes -------------------------------------------------------------------------

/// A run that started nothing and failed only as containers ran no class at all.
///
/// The `air-linux-1` run of 2026-08-23 that decided this: a class genuinely ran and failed once, in trial 1, and in
/// eleven more trials it "failed" only in the sense that there was no IDE left to run it on - bucketed `broken` on
/// one real observation. Such a run's failure entries are statements about the daemon, not about any class.
const fn ran_nothing(document: &RunReport) -> bool {
    document.execution.tests_started == 0 && document.execution.container_failures > 0
}

/// Every class with at least one case in the XML, whatever the outcome: what this run actually covered.
///
/// The failures are unioned in, and that is not belt-and-braces: a failure whose class never reached the XML - a
/// container that died before writing one - would otherwise be failed and not executed, which lets `f > n`
/// through to [`wilson_interval`] as a NaN. A run [`ran_nothing`] describes answers the empty set here *and* in
/// [`failed_classes`], so executed ⊇ failed still holds.
pub fn executed_classes(document: &RunReport) -> BTreeSet<String> {
    if ran_nothing(document) {
        return BTreeSet::new();
    }
    let cases = document
        .suites
        .iter()
        .flat_map(|suite| &suite.cases)
        .map(|case| case.class_name.clone());
    let failures = document.failures.iter().filter_map(|failure| failure.class_name.clone());
    cases.chain(failures).collect()
}

/// Every class this run has a failed case or a failure entry for: a complete XML owns its failures, and a run
/// whose XML never arrived has only the progress stream's. The same [`ran_nothing`] guard as
/// [`executed_classes`], or `f > n` gets back in by the side door.
pub fn failed_classes(document: &RunReport) -> BTreeSet<String> {
    if ran_nothing(document) {
        return BTreeSet::new();
    }
    let cases = document
        .suites
        .iter()
        .flat_map(|suite| &suite.cases)
        .filter(|case| case.status == CaseStatus::Failed)
        .map(|case| case.class_name.clone());
    let failures = document.failures.iter().filter_map(|failure| failure.class_name.clone());
    cases.chain(failures).collect()
}

/// Each class a condition ruled out, with the reason JUnit gave, named by its class name where JUnit gave one and
/// by its display name otherwise - the only key a parameterized or dynamic container has.
fn skipped_class_entries(document: &RunReport) -> impl Iterator<Item = (&str, &str)> {
    document.skipped_containers.iter().map(|container| {
        (
            container.class_name.as_deref().unwrap_or(&container.display_name),
            container.reason.as_str(),
        )
    })
}

// --- the shared entry -----------------------------------------------------------------------------------------

/// One attempt as an aggregate embeds it. The report is inlined only when the entry is not green: the reader of
/// an aggregate is looking for the one entry that went wrong. `no_tests` and `all_skipped` are not green here -
/// they are the guards, not the good news.
fn aggregate_entry(attempt: &Attempt) -> Entry {
    let mut entry = Entry::new(attempt.worker.clone(), EntryStatus::NoReport);
    entry.iteration_id = attempt.iteration_id.clone();
    entry.daemon_run_id = attempt.daemon_run_id.clone();
    entry.report_path = attempt.report_path.clone();
    entry.error = attempt.error.clone();
    if let Some(report) = &attempt.report {
        entry.status = report.status.into();
        entry.verdict_diagnostic = report.verdict_diagnostic.clone();
        entry.iteration_id.get_or_insert_with(|| report.iteration_id.clone());
        entry.daemon_run_id.get_or_insert_with(|| report.daemon_run_id.clone());
        if report.status != Status::Passed {
            entry.report = Some(Box::new(report.clone()));
        }
    }
    entry
}

/// What a diagnostic is about, bounded by [`MAX_DIAGNOSTIC_ITEMS`].
fn describe(items: &[String]) -> String {
    if items.len() <= MAX_DIAGNOSTIC_ITEMS {
        return items.join(", ");
    }
    format!(
        "{}, and {} more",
        items[..MAX_DIAGNOSTIC_ITEMS].join(", "),
        items.len() - MAX_DIAGNOSTIC_ITEMS
    )
}

/// The chronologically first or last stamp, by instant rather than by text. A stamp that cannot be read as an
/// instant contributes nothing: an aggregate whose `startedAt` nobody can parse is worse than one with none.
fn extreme<'a>(stamps: impl Iterator<Item = &'a str>, keep_later: bool) -> Option<String> {
    let readable = stamps.filter_map(|stamp| instant_millis(stamp).map(|millis| (millis, stamp)));
    // The first of equal instants wins either way, so the spelling that is kept does not depend on the direction.
    readable
        .reduce(|chosen, candidate| {
            let better = if keep_later {
                candidate.0 > chosen.0
            } else {
                candidate.0 < chosen.0
            };
            if better { candidate } else { chosen }
        })
        .map(|(_, stamp)| stamp.to_owned())
}

/// First start to last completion, and zero when either end is unknown or the pair is inverted.
fn span_ms(started_at: Option<&str>, completed_at: Option<&str>) -> f64 {
    match (started_at.and_then(instant_millis), completed_at.and_then(instant_millis)) {
        (Some(started), Some(completed)) if completed > started => (completed - started) as f64,
        _ => 0.0,
    }
}

/// Items of many reports in timestamp order, with a *global* position as the tiebreak.
///
/// Every report numbers its own suites from zero, so tiebreaking on the per-report index would interleave two
/// shards arbitrarily. The position here is entry order then in-report order, which is stable; each suite keeps
/// its own `documentIndex`, the only pointer back into the XML it came from. The comparison itself is
/// `bt_junit::compare_suites_by_timestamp`, the one report-order definition, applied to a key suite that
/// carries the stamp and the global position: items with no readable instant keep their position, last.
fn in_timestamp_order<T>(items: Vec<(Option<String>, T)>) -> Vec<T> {
    let mut keyed: Vec<(Suite, T)> = items
        .into_iter()
        .enumerate()
        .map(|(position, (timestamp, value))| (order_key(timestamp, position), value))
        .collect();
    keyed.sort_by(|(left, _), (right, _)| compare_suites_by_timestamp(left, right));
    keyed.into_iter().map(|(_, value)| value).collect()
}

const fn order_key(timestamp: Option<String>, position: usize) -> Suite {
    Suite {
        name: String::new(),
        timestamp,
        time_seconds: 0.0,
        document_index: position,
        tests: 0,
        failures: 0,
        errors: 0,
        skipped: 0,
        cases: Vec::new(),
        bucketing_stub: false,
        wrapper_exit_code: None,
    }
}

// --- the shard verdict ----------------------------------------------------------------------------------------

/// The verdict half of a merge: a status, and the name of the branch that chose it.
struct Decision {
    status: Status,
    code: Option<ShardDiagnosticCode>,
    diagnostic: Option<String>,
}

const fn decided(status: Status, code: ShardDiagnosticCode, diagnostic: String) -> Decision {
    Decision {
        status,
        code: Some(code),
        diagnostic: Some(diagnostic),
    }
}

fn shard_label(attempt: &ShardAttempt) -> String {
    format!("shard {} ({})", attempt.shard_index, attempt.attempt.worker)
}

/// Why a shard has no report, in the order the reasons are trustworthy: the controller's own refusal, then
/// whatever the report would have said, then an admission.
fn reason_of(attempt: &Attempt) -> String {
    match (&attempt.error, &attempt.report) {
        (Some(error), _) => error.message.clone(),
        (
            None,
            Some(RunReport {
                verdict_diagnostic: Some(diagnostic),
                ..
            }),
        ) => diagnostic.clone(),
        _ => "no reason given".to_owned(),
    }
}

/// The attempts that reported, with their reports, when every attempt did.
fn all_reported(attempts: &[ShardAttempt]) -> Option<Vec<(&ShardAttempt, &RunReport)>> {
    attempts
        .iter()
        .map(|attempt| attempt.attempt.report.as_ref().map(|report| (attempt, report)))
        .collect()
}

fn labels<'a>(
    attempts: impl Iterator<Item = (&'a ShardAttempt, &'a RunReport)>,
    label: impl Fn(&ShardAttempt, &RunReport) -> String,
) -> Vec<String> {
    attempts.map(|(attempt, report)| label(attempt, report)).collect()
}

/// The merge ladder: first match wins, and every branch names itself.
///
/// Each rule above another exists because the two diagnoses it separates are opposite instructions to the reader,
/// and the cheaper check would answer with the wrong one.
fn decide_shard_status(
    attempts: &[ShardAttempt],
    coverage: &[ShardCoverage],
    overlaps: &[ShardOverlapEntry],
    union_size: usize,
) -> Decision {
    if attempts.is_empty() {
        return decided(
            Status::InfrastructureError,
            ShardDiagnosticCode::SetEmpty,
            "no shard reported at all".to_owned(),
        );
    }

    // 1. A dead shard can never be averaged away: nothing ran, so nothing about it is green.
    let Some(reported) = all_reported(attempts) else {
        let missing: Vec<String> = attempts
            .iter()
            .filter(|attempt| attempt.attempt.report.is_none())
            .map(|attempt| format!("{}: {}", shard_label(attempt), reason_of(&attempt.attempt)))
            .collect();
        return decided(
            Status::InfrastructureError,
            ShardDiagnosticCode::MissingReport,
            format!("no report from {}", describe(&missing)),
        );
    };
    let with_status = |status: Status| reported.iter().copied().filter(move |(_, report)| report.status == status);

    // 2. Where every single-run guard arrives, already folded into one word. Re-used, not re-derived.
    let broken = labels(with_status(Status::InfrastructureError), |attempt, report| {
        format!(
            "{}: {}",
            shard_label(attempt),
            report.verdict_diagnostic.as_deref().unwrap_or("no diagnostic")
        )
    });
    if !broken.is_empty() {
        return decided(
            Status::InfrastructureError,
            ShardDiagnosticCode::InfrastructureError,
            format!("infrastructure error in {}", describe(&broken)),
        );
    }

    // 3. Belt over rule 2's braces. Redundant today - the report builder turns every incomplete source into an
    // `infrastructure_error` - and kept because it is the invariant rather than a consequence of one diagnostic
    // list. If that list ever loses an entry, this catches the hole instead of shipping a verdict read off a
    // truncated XML.
    let incomplete = labels(
        reported
            .iter()
            .copied()
            .filter(|(_, report)| report.source.integrity != Integrity::Complete),
        |attempt, report| format!("{}: {}", shard_label(attempt), report.source.integrity),
    );
    if !incomplete.is_empty() {
        return decided(
            Status::InfrastructureError,
            ShardDiagnosticCode::IncompleteSource,
            format!("incomplete JUnit source in {}", describe(&incomplete)),
        );
    }

    // 4. Every shard finding nothing means the selection matched nothing. *One* shard finding nothing while its
    // siblings ran tests is lost coverage wearing a green hat: a different bug with a different fix.
    let empty_selection = labels(with_status(Status::NoTests), |attempt, _| shard_label(attempt));
    if empty_selection.len() == attempts.len() {
        return decided(
            Status::NoTests,
            ShardDiagnosticCode::DiscoveredNothing,
            format!("all {} shard(s) discovered no tests", attempts.len()),
        );
    }
    if !empty_selection.is_empty() {
        return decided(
            Status::InfrastructureError,
            ShardDiagnosticCode::DiscoveredNothing,
            format!(
                "{} discovered no tests while {} other shard(s) ran some",
                describe(&empty_selection),
                attempts.len() - empty_selection.len()
            ),
        );
    }

    // 5. All ruled out is a lane-wide prerequisite verdict, named with the code rule 6 would have reached; some
    // ruled out just means those shards contribute their reasons and the verdict comes from the rest.
    if with_status(Status::AllSkipped).count() == attempts.len() {
        return decided(
            Status::AllSkipped,
            ShardDiagnosticCode::ExecutedNothing,
            format!("all {} shard(s) had every class ruled out by a condition", attempts.len()),
        );
    }

    // 6. The coverage invariant, which nothing in the single-run path can provide: a split is only a split if the
    // pieces are disjoint and non-empty. Plain summing would hide a duplicated class as extra green tests, and a
    // balancer that dropped every class as an empty, passing lane.
    if !overlaps.is_empty() {
        let named: Vec<String> = overlaps
            .iter()
            .map(|overlap| {
                let shards: Vec<String> = overlap.shard_indexes.iter().map(u32::to_string).collect();
                format!("{} in shards {}", overlap.class_name, shards.join("/"))
            })
            .collect();
        return decided(
            Status::InfrastructureError,
            ShardDiagnosticCode::Overlap,
            format!("class(es) executed by more than one shard: {}", describe(&named)),
        );
    }
    if union_size == 0 {
        return decided(
            Status::InfrastructureError,
            ShardDiagnosticCode::ExecutedNothing,
            format!("{} shard(s) reported, and not one executed a class", coverage.len()),
        );
    }

    // 7. Only `passed`, `failed` and `all_skipped` are left, and the skipped ones have no verdict to give. The one
    // branch that reaches a caller unnamed, deliberately: every diagnostic code names a way the *lane* went wrong,
    // and a lane that ran perfectly and found a bug is described by its `failures`.
    let status = if with_status(Status::Failed).next().is_some() {
        Status::Failed
    } else {
        Status::Passed
    };
    Decision {
        status,
        code: None,
        diagnostic: None,
    }
}

/// Folds one lane's shards into one verdict.
///
/// Shards are put in `shardIndex` order first, so the same set of reports always produces the same verdict -
/// including the same merged suite order for suites whose timestamps tie or are missing.
pub fn merge_shard_verdict(attempts: &[ShardAttempt]) -> ShardVerdict {
    let mut ordered: Vec<&ShardAttempt> = attempts.iter().collect();
    ordered.sort_by_key(|attempt| attempt.shard_index);
    let ordered: Vec<ShardAttempt> = ordered.into_iter().cloned().collect();

    let coverage: Vec<ShardCoverage> = ordered
        .iter()
        .map(|attempt| {
            let report = attempt.attempt.report.as_ref();
            ShardCoverage {
                shard_index: attempt.shard_index,
                worker: attempt.attempt.worker.clone(),
                classes: report
                    .map(|report| executed_classes(report).into_iter().collect())
                    .unwrap_or_default(),
                skipped_classes: report
                    .map(|report| {
                        skipped_class_entries(report)
                            .map(|(class_name, _)| class_name.to_owned())
                            .collect::<BTreeSet<_>>()
                            .into_iter()
                            .collect()
                    })
                    .unwrap_or_default(),
            }
        })
        .collect();

    // Overlap is checked over *executed* classes only. A ruled-out container ran nothing, so it is not coverage,
    // and counting it would turn one missing guest CLI - which rules the same classes out on every worker at once -
    // into an overlap on every class, burying a real prerequisite verdict under a fake bug.
    let mut owners: BTreeMap<&str, Vec<u32>> = BTreeMap::new();
    for shard in &coverage {
        for class_name in &shard.classes {
            owners.entry(class_name).or_default().push(shard.shard_index);
        }
    }
    let overlaps: Vec<ShardOverlapEntry> = owners
        .iter()
        .filter(|(_, shard_indexes)| shard_indexes.len() > 1)
        .map(|(class_name, shard_indexes)| ShardOverlapEntry {
            class_name: (*class_name).to_owned(),
            shard_indexes: shard_indexes.clone(),
        })
        .collect();

    let decision = decide_shard_status(&ordered, &coverage, &overlaps, owners.len());

    let reports: Vec<&RunReport> = ordered.iter().filter_map(|attempt| attempt.attempt.report.as_ref()).collect();
    let mut execution = ExecutionCounts::default();
    let mut xml = XmlCounts::default();
    let mut total_duration_ms = 0.0;
    let mut suites = Vec::new();
    let mut failures = Vec::new();
    let mut skipped_containers = Vec::new();
    // Each count is the guest's, so any u32 is possible: saturate, because a wrapped sum would be a small, wrong
    // count, and an ill-defined count is the one thing a count in a verdict may not be.
    for report in &reports {
        execution.tests_started = execution.tests_started.saturating_add(report.execution.tests_started);
        execution.tests_failed = execution.tests_failed.saturating_add(report.execution.tests_failed);
        execution.tests_skipped = execution.tests_skipped.saturating_add(report.execution.tests_skipped);
        execution.containers_skipped = execution.containers_skipped.saturating_add(report.execution.containers_skipped);
        execution.container_failures = execution.container_failures.saturating_add(report.execution.container_failures);
        xml.tests = xml.tests.saturating_add(report.xml.tests);
        xml.failures = xml.failures.saturating_add(report.xml.failures);
        xml.errors = xml.errors.saturating_add(report.xml.errors);
        xml.skipped = xml.skipped.saturating_add(report.xml.skipped);
        total_duration_ms += report.duration_ms;
        suites.extend(report.suites.iter().map(|suite| (suite.timestamp.clone(), suite.clone())));
        failures.extend(
            report
                .failures
                .iter()
                .map(|failure| (failure.suite_timestamp.clone(), failure.clone())),
        );
        skipped_containers.extend(report.skipped_containers.iter().cloned());
    }
    let started_at = extreme(reports.iter().map(|report| report.started_at.as_str()), false);
    let completed_at = extreme(reports.iter().map(|report| report.completed_at.as_str()), true);

    ShardVerdict {
        aggregate_schema_version: AGGREGATE_SCHEMA_VERSION,
        kind: AggregateKind::Shard,
        status: decision.status,
        code: decision.code,
        diagnostic: decision.diagnostic,
        shard_count: u32::try_from(ordered.len()).unwrap_or(u32::MAX),
        entries: ordered
            .iter()
            .map(|attempt| ShardEntry {
                entry: aggregate_entry(&attempt.attempt),
                shard_index: attempt.shard_index,
            })
            .collect(),
        // Two durations, because they answer different questions: the wall clock is what the sharded lane cost,
        // and the sum is the machine time. Together they are the only honest statement of what sharding bought.
        wall_duration_ms: span_ms(started_at.as_deref(), completed_at.as_deref()),
        started_at,
        completed_at,
        total_duration_ms,
        execution,
        xml,
        ordering: ORDERING.to_owned(),
        suites: in_timestamp_order(suites),
        failures: in_timestamp_order(failures),
        skipped_containers,
        executed_classes: owners.keys().map(|class_name| (*class_name).to_owned()).collect(),
        coverage,
        overlaps,
    }
}

// --- the flake summary ----------------------------------------------------------------------------------------

/// One authoritative trial's account of the classes it ran.
struct Observation<'a> {
    ordinal: u32,
    executed: BTreeSet<String>,
    failed: BTreeSet<String>,
    skipped: BTreeMap<&'a str, &'a str>,
}

/// Why a trial does not get to vote, in the vocabulary of the honesty guard, or `None` when it does.
///
/// The same ladder the shard merge climbs, one level down: a trial that discovered nothing measured nothing, and a
/// zero-failure trial over zero classes is the most flattering thing you can hand a rate.
fn exclusion_of(attempt: &TrialAttempt) -> Option<FlakeExclusion> {
    let exclusion = |code: &str, message: String| FlakeExclusion {
        ordinal: attempt.trial_ordinal,
        worker: attempt.attempt.worker.clone(),
        code: code.to_owned(),
        message,
    };
    let Some(report) = &attempt.attempt.report else {
        return Some(match &attempt.attempt.error {
            Some(error) => exclusion(&error.code, error.message.clone()),
            None => exclusion("trial_missing_report", "the trial produced no report at all".to_owned()),
        });
    };
    if report.status == Status::InfrastructureError {
        return Some(exclusion(
            "trial_infrastructure_error",
            report
                .verdict_diagnostic
                .clone()
                .unwrap_or_else(|| "the trial did not produce an authoritative result".to_owned()),
        ));
    }
    if !is_authoritative(report.status) {
        return Some(exclusion(
            "trial_discovered_nothing",
            format!("the trial reported {}", report.status),
        ));
    }
    if report.source.integrity != Integrity::Complete {
        return Some(exclusion(
            "trial_incomplete_source",
            format!("the trial's JUnit source is {}", report.source.integrity),
        ));
    }
    None
}

/// The direct probe of the ordering defect confirmed on `air-linux-1` on 2026-08-22: running one class and then
/// any other on the same warm daemon timed the second class's `@BeforeAll` out, and `--fresh-ide` fixed it.
///
/// Failing only on the first trial, or only after it, is a claim about *position in the sequence* rather than about
/// the class. A class that failed in every trial it ran carries no positional signal, and one that ran once
/// carries no sequence at all.
fn order_signature_of(executed: &[u32], failed: &[u32]) -> Option<OrderSignature> {
    if executed.len() < 2 || failed.is_empty() || failed.len() == executed.len() {
        return None;
    }
    let first = executed[0];
    if failed.iter().all(|ordinal| *ordinal == first) {
        Some(OrderSignature::FirstTrialOnly)
    } else if failed.iter().all(|ordinal| *ordinal != first) {
        Some(OrderSignature::AfterFirstTrial)
    } else {
        None
    }
}

/// One class's bucket, in the order the buckets are decided.
///
/// `broken` outranks `unstablePrerequisite` because a class that failed every time it ran is a deterministic
/// defect whatever else happened to it. `unstablePrerequisite` outranks `flaky` because a class whose prerequisite
/// comes and goes has no denominator worth publishing: its `n` counts the trials that happened to admit it.
const fn bucket_of(executed: usize, failed: usize, skipped: usize) -> FlakeBucket {
    if executed == 0 {
        FlakeBucket::NotMeasured
    } else if failed == executed && executed >= 2 {
        FlakeBucket::Broken
    } else if skipped > 0 {
        FlakeBucket::UnstablePrerequisite
    } else if failed == 0 {
        FlakeBucket::Stable
    } else if failed == executed {
        // One trial, one failure: not provably deterministic and not measurably intermittent. Counting it broken
        // hides a flake, counting it 1/1 flaky puts an unrepeatable observation into a published rate.
        FlakeBucket::InsufficientEvidence
    } else {
        FlakeBucket::Flaky
    }
}

fn count(items: usize) -> u32 {
    u32::try_from(items).unwrap_or(u32::MAX)
}

/// Folds K repeat trials of one selection into a per-class rate, with its own honesty attached.
pub fn flake_summary(attempts: &[TrialAttempt]) -> FlakeSummary {
    let mut ordered: Vec<&TrialAttempt> = attempts.iter().collect();
    ordered.sort_by_key(|attempt| attempt.trial_ordinal);

    let mut infrastructure_trials = Vec::new();
    let mut observations = Vec::new();
    for attempt in &ordered {
        if let Some(exclusion) = exclusion_of(attempt) {
            infrastructure_trials.push(exclusion);
            continue;
        }
        let Some(report) = &attempt.attempt.report else {
            continue;
        };
        observations.push(Observation {
            ordinal: attempt.trial_ordinal,
            executed: executed_classes(report),
            failed: failed_classes(report),
            skipped: skipped_class_entries(report).collect(),
        });
    }

    // The universe is what the authoritative trials saw, executed or ruled out. A class only an *excluded* trial
    // mentioned is absent: the trial that saw it is not evidence, so a bucket for it would be a verdict with no
    // trial behind it.
    let universe: BTreeSet<&str> = observations
        .iter()
        .flat_map(|observation| {
            observation
                .executed
                .iter()
                .map(String::as_str)
                .chain(observation.skipped.keys().copied())
        })
        .collect();

    let classes: Vec<FlakeClass> = universe
        .into_iter()
        .map(|class_name| {
            let mut executed_ordinals = Vec::new();
            let mut failed_ordinals = Vec::new();
            let mut skipped_ordinals = Vec::new();
            // Insertion-ordered rather than sorted: the reasons are a sequence of what the guest said, and the
            // order they were observed in is the only order that means anything about a prerequisite that came
            // and went.
            let mut skip_reasons: Vec<String> = Vec::new();
            for observation in &observations {
                // A class that both executed and was ruled out in one trial counts as executed, which is what makes
                // `executedTrials + skippedTrials` a count of trials rather than of records.
                if observation.executed.contains(class_name) {
                    executed_ordinals.push(observation.ordinal);
                    if observation.failed.contains(class_name) {
                        failed_ordinals.push(observation.ordinal);
                    }
                } else if let Some(reason) = observation.skipped.get(class_name) {
                    skipped_ordinals.push(observation.ordinal);
                    if !skip_reasons.iter().any(|known| known == reason) {
                        skip_reasons.push((*reason).to_owned());
                    }
                }
            }
            let bucket = bucket_of(executed_ordinals.len(), failed_ordinals.len(), skipped_ordinals.len());
            // Only `flaky` publishes a rate: a rate on a `broken` class would be 1.0, which reads as maximal
            // flakiness for a class that is simply red.
            let (rate, interval) = if bucket == FlakeBucket::Flaky {
                let failed = count(failed_ordinals.len());
                let executed = count(executed_ordinals.len());
                (
                    Some(f64::from(failed) / f64::from(executed)),
                    Some(wilson_interval(failed, executed)),
                )
            } else {
                (None, None)
            };
            FlakeClass {
                class_name: class_name.to_owned(),
                bucket,
                executed_trials: count(executed_ordinals.len()),
                failed_trials: count(failed_ordinals.len()),
                skipped_trials: count(skipped_ordinals.len()),
                order_signature: order_signature_of(&executed_ordinals, &failed_ordinals),
                executed_ordinals,
                failed_ordinals,
                skipped_ordinals,
                rate,
                interval,
                skip_reasons,
            }
        })
        .collect();

    let named = |bucket: FlakeBucket| -> Vec<String> {
        classes
            .iter()
            .filter(|class| class.bucket == bucket)
            .map(|class| class.class_name.clone())
            .collect()
    };
    let flake_set = named(FlakeBucket::Flaky);
    let stable_set = named(FlakeBucket::Stable);
    // The lane's rate is over the classes this run could judge. `broken` is deterministic, `notMeasured` never
    // ran, `unstablePrerequisite` has no trustworthy denominator and `insufficientEvidence` has one observation:
    // none of them belongs in either half of a fraction about intermittency.
    let measured_classes = count(stable_set.len() + flake_set.len());
    let flaky = count(flake_set.len());

    let trials = ordered
        .iter()
        .map(|attempt| FlakeTrial {
            entry: aggregate_entry(&attempt.attempt),
            ordinal: attempt.trial_ordinal,
            reset_policy: attempt.reset_policy,
            // A trial with neither measurement is zero rather than absent: the field is not optional.
            duration_ms: attempt
                .duration_ms
                .or_else(|| attempt.attempt.report.as_ref().map(|report| report.duration_ms))
                .unwrap_or_default(),
            guest_free_bytes_before: attempt.guest_free_bytes_before,
            guest_free_bytes_after: attempt.guest_free_bytes_after,
        })
        .collect();

    let lane_flake_rate = if measured_classes > 0 {
        f64::from(flaky) / f64::from(measured_classes)
    } else {
        0.0
    };
    let not_reportable_reason = reportability(ordered.len(), infrastructure_trials.len(), measured_classes as usize, classes.len());

    FlakeSummary {
        aggregate_schema_version: AGGREGATE_SCHEMA_VERSION,
        kind: AggregateKind::Flake,
        attempted_trials: count(ordered.len()),
        authoritative_trials: count(observations.len()),
        trials,
        infrastructure_trials,
        broken_set: named(FlakeBucket::Broken),
        not_measured: named(FlakeBucket::NotMeasured),
        unstable_prerequisite: named(FlakeBucket::UnstablePrerequisite),
        insufficient_evidence: named(FlakeBucket::InsufficientEvidence),
        order_suspects: classes
            .iter()
            .filter(|class| class.order_signature.is_some())
            .map(|class| class.class_name.clone())
            .collect(),
        lane_flake_interval: wilson_interval(flaky, measured_classes),
        classes,
        flake_set,
        stable_set,
        lane_flake_rate,
        measured_classes,
        reportable: not_reportable_reason.is_none(),
        not_reportable_reason,
    }
}

/// Why this summary is not a number anyone may quote, or `None` when it is.
///
/// The trial guard is about sampling. Excluded trials are not missing at random: a daemon that dies during the
/// relaunch-heavy classes removes exactly the classes most likely to flake, so past a fraction of them the
/// survivors are a biased sample and the rate reads *low*. The class floor is about the denominator:
/// `all_skipped` trials are authoritative, so a truncated lane can pass the trial guard while judging almost none
/// of its classes. A summary that is not reportable still publishes every class; what it withholds is the
/// headline.
fn reportability(attempted_trials: usize, excluded_trials: usize, measured_classes: usize, observed_classes: usize) -> Option<String> {
    if attempted_trials == 0 {
        return Some("no trials were attempted".to_owned());
    }
    let excluded_fraction = excluded_trials as f64 / attempted_trials as f64;
    if excluded_fraction > MAX_EXCLUDED_TRIAL_FRACTION {
        return Some(format!(
            "{excluded_trials} of {attempted_trials} trials were excluded as infrastructure failures \
             ({}% > {}%): the survivors are not a random sample of the lane, so this is not a flake rate",
            whole_percent(excluded_fraction),
            whole_percent(MAX_EXCLUDED_TRIAL_FRACTION)
        ));
    }
    if measured_classes == 0 {
        return Some("no class was measured in a trial that could judge it".to_owned());
    }
    let measured_fraction = measured_classes as f64 / observed_classes as f64;
    if measured_fraction < MIN_MEASURED_CLASS_FRACTION {
        return Some(format!(
            "only {measured_classes} of {observed_classes} observed classes were measured in trials that could \
             judge them ({}% < {}%): a lane rate over that remnant would misrepresent the lane",
            whole_percent(measured_fraction),
            whole_percent(MIN_MEASURED_CLASS_FRACTION)
        ));
    }
    None
}
