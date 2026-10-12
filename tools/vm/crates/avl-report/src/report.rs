//! Building one run's report out of the two accounts of that run, and publishing it where it cannot be
//! overwritten.
//!
//! The document itself lives in `avl_wire::report`; what is here is the arithmetic that decides what goes in it.
//! Two accounts arrive of every run - the JUnit XML the JVM wrote, and the NDJSON progress stream the daemon
//! watched - and they disagree usefully. The XML is authoritative when it is complete and worthless when it is
//! not, which is why nothing here reads a count off a truncated document: [`build`] takes the progress stream's
//! failures whenever the source is anything but `complete`, every disagreement it can see becomes a diagnostic,
//! and every diagnostic makes the run an `infrastructure_error`.
//!
//! That is the whole design rule, and it is a refusal rather than a preference. A count read off a document that
//! was cut off when the JVM was killed looks exactly as authoritative as a real one, and the run it describes
//! reads green. Every branch below that adds a diagnostic instead of a number exists because some run once
//! reported the wrong thing that way.

use std::collections::HashSet;
use std::fs;
use std::io;
use std::path::PathBuf;

use avl_base::RefusalExt;
use avl_base::format::first_line;
use avl_base::fs::{PublishError, create_private_dir};
use avl_base::{Config, Exit, OrRefuse, Refusal, validate_name};
use avl_wire::daemon::{RunEvent, RunEventKind, Summary};
use avl_wire::report::{
    ActiveTest, Case, CaseStatus, EvidenceArtifact, ExecutionCounts, Failure, FailureKind, FailureSource, Integrity,
    MAX_FAILURE_DETAIL_CHARS, MAX_FAILURE_MESSAGE_CHARS, MAX_RELEVANT_FRAMES, ORDERING, RUN_REPORT_SCHEMA_VERSION, RetrievedIntegrity,
    RetrievedXml, RunReport, SkippedContainer, Source, Status, Suite, TraceArchive, Tree, Watchdog, WatchdogExpiry, WatchdogState,
    XmlCounts, safe_evidence_paths,
};

#[cfg(test)]
mod tests;

/// Everything one run leaves behind, as the controller collected it.
///
/// `summary` is optional and `events` is not: a stream that ended without a summary is the normal shape of a
/// killed run and has to stay representable, while a run with no events at all never started and is reported
/// from its source and its watchdog alone.
#[derive(Clone, Debug)]
pub struct Input {
    pub iteration_id: String,
    pub daemon_run_id: String,
    pub daemon_boot_stamp: String,
    pub selection: String,
    pub started_at: String,
    pub completed_at: String,
    /// `None` when the stream ended without one, which is itself a diagnostic; see
    /// [`avl_wire::daemon::find_summary`].
    pub summary: Option<Summary>,
    pub events: Vec<RunEvent>,
    pub source: RetrievedXml,
    /// The guest files the controller managed to fetch, or the reason it could not. Every entry names a path
    /// [`safe_evidence_paths`] admitted.
    pub evidence: Vec<EvidenceArtifact>,
    /// An empty diagnostic and none are different facts on the document: the first says something wrote a reason
    /// and lost it.
    pub protocol_diagnostic: Option<String>,
    /// A jar this iteration ran was rebuilt on the host before the iteration finished, so the verdict describes
    /// bytes the checkout no longer produces. A diagnostic rather than a note, by the rule this module opens with.
    pub hot_jar_drift: Option<String>,
    /// The iteration's scenario traces as the controller pulled them, and why it could not. Carried verbatim and
    /// never read by anything that decides the verdict: a trace explains a run.
    pub traces: Vec<TraceArchive>,
    pub traces_error: Option<String>,
    /// The host checkout the build read, and why it could not be read; carried verbatim by the same rule.
    pub tree: Option<Tree>,
    pub tree_error: Option<String>,
}

/// Assembles one run's report and decides its verdict.
///
/// The one refusal is an inconsistent [`Input`]: a source that claims `available` and carries no XML is not a
/// run this function can describe, because the integrity it would publish is the parser's verdict on a document
/// that is not there.
pub fn build(input: Input) -> Result<RunReport, Refusal> {
    let (parsed, integrity) = read_source(&input.source)?;
    let suites = parsed.as_ref().map_or(&[][..], |report| report.suites.as_slice());

    let execution = execution_counts(input.summary.as_ref(), &input.events);
    let reported = xml_counts(suites);
    let progress = progress_state(&input.events);
    let watchdog = watchdog_report(&input.events);
    let unreported = unreported_classes(&input.events);

    // The listener serializes a failed container as a testcase, so complete XML already owns its own diagnostic.
    // Progress remains the fallback only when the XML is unavailable or cannot be trusted.
    let failures = if integrity == Integrity::Complete {
        xml_failures(suites)
    } else {
        progress.failures
    };

    let diagnostics = verdict_diagnostics(&input, parsed.as_ref(), integrity, &execution, &reported, &watchdog, &unreported);
    let status = decide_status(&diagnostics, &execution, &reported);
    let source = Source {
        guest_path: input.source.guest_path.clone(),
        retrieval: input.source.retrieval,
        integrity,
        // The retriever's diagnostic outranks the parser's: a document truncated on the way off the guest is
        // explained by the transfer, and the parse error is a consequence.
        diagnostic: input
            .source
            .diagnostic
            .clone()
            .or_else(|| parsed.as_ref().and_then(|report| report.integrity.message.clone())),
    };

    Ok(RunReport {
        report_schema_version: RUN_REPORT_SCHEMA_VERSION,
        duration_ms: elapsed_ms(&input.started_at, &input.completed_at),
        ordering: ORDERING.to_owned(),
        status,
        execution,
        xml: reported,
        source,
        suites: reported_suites(suites),
        failures,
        skipped_containers: skipped_containers(&input.events),
        active_tests: progress.active_tests,
        unreported_classes: unreported,
        watchdog,
        verdict_diagnostic: (!diagnostics.is_empty()).then(|| diagnostics.join("; ")),
        iteration_id: input.iteration_id,
        daemon_run_id: input.daemon_run_id,
        daemon_boot_stamp: input.daemon_boot_stamp,
        selection: input.selection,
        started_at: input.started_at,
        completed_at: input.completed_at,
        // Absent when the daemon named nothing, which is what a run that finished on time looks like.
        evidence: input.evidence,
        trace_archives: input.traces,
        traces_error: input.traces_error,
        tree: input.tree,
        tree_error: input.tree_error,
        // Carried verbatim rather than folded into the verdict diagnostic: a protocol complaint is about this
        // controller's channel to the daemon, a verdict diagnostic about the run, and a reader acting on them goes
        // to different machines.
        protocol_diagnostic: input.protocol_diagnostic,
    })
}

/// The integrity of a document the JUnit reader parsed: the first four integrities of the report are the reader's.
const fn integrity_of(status: bt_junit::IntegrityStatus) -> Integrity {
    match status {
        bt_junit::IntegrityStatus::Complete => Integrity::Complete,
        bt_junit::IntegrityStatus::Empty => Integrity::Empty,
        bt_junit::IntegrityStatus::Truncated => Integrity::Truncated,
        bt_junit::IntegrityStatus::Malformed => Integrity::Malformed,
    }
}

/// The parsed document and the integrity the report publishes, the one place the parser's verdict replaces the
/// retriever's. `available` means the bytes arrived; what a reader needs is whether they parse. Anything else is
/// a document that was never read.
fn read_source(source: &RetrievedXml) -> Result<(Option<bt_junit::Report>, Integrity), Refusal> {
    match source.integrity {
        RetrievedIntegrity::Available => {
            let Some(xml) = &source.xml else {
                return Err(Refusal::new(
                    "report_source_inconsistent",
                    Exit::FAILURE,
                    format!("the retrieval of {} claims available and carries no XML", source.guest_path),
                ));
            };
            let parsed = bt_junit::parse_report(xml);
            let integrity = integrity_of(parsed.integrity.status);
            Ok((Some(parsed), integrity))
        }
        RetrievedIntegrity::Missing => Ok((None, Integrity::Missing)),
        RetrievedIntegrity::Oversized => Ok((None, Integrity::Oversized)),
    }
}

// --- the two accounts of a run --------------------------------------------------------------------------------

/// The two `testFinished` statuses this module branches on; a typo in either is a silent zero in a count a
/// verdict is read off.
const STATUS_FAILED: &str = "FAILED";
const STATUS_ABORTED: &str = "ABORTED";

/// The daemon's own count of what it ran, and the reconstruction used when it never said.
///
/// The reconstruction is not a substitute: a stream with no summary is already a diagnostic, so these counts
/// describe a run that is reported as broken, never one that passes. `ABORTED` counts as skipped because that is
/// what the daemon's own summary does with it: a test the run gave up on did not fail.
fn execution_counts(summary: Option<&Summary>, events: &[RunEvent]) -> ExecutionCounts {
    if let Some(summary) = summary {
        return ExecutionCounts {
            tests_started: summary.tests_started,
            tests_failed: summary.tests_failed,
            tests_skipped: summary.tests_skipped,
            containers_skipped: summary.containers_skipped,
            container_failures: summary.container_failures,
        };
    }
    let mut counts = ExecutionCounts::default();
    for event in events {
        match &event.kind {
            RunEventKind::TestStarted(_) => counts.tests_started += 1,
            RunEventKind::TestFinished(finished) if finished.status == STATUS_FAILED => {
                counts.tests_failed += 1;
            }
            RunEventKind::TestFinished(finished) if finished.status == STATUS_ABORTED => {
                counts.tests_skipped += 1;
            }
            RunEventKind::TestSkipped(_) => counts.tests_skipped += 1,
            RunEventKind::ContainerSkipped(_) => counts.containers_skipped += 1,
            RunEventKind::ContainerFailed(_) => counts.container_failures += 1,
            _ => {}
        }
    }
    counts
}

/// The classes a condition ruled out before anything started.
///
/// Load-bearing for the summary-versus-XML check: a discovery-time skip has no `testStarted`, so the XML holds a
/// testcase the daemon's `testsStarted` never counted. A skip of a test that *had* started is already inside
/// `testsStarted`, and counting it twice would manufacture a mismatch on a healthy run.
fn pre_start_skipped_count(events: &[RunEvent]) -> u32 {
    let mut started = HashSet::new();
    let mut skipped = 0;
    for event in events {
        match &event.kind {
            RunEventKind::TestStarted(test) => {
                started.insert(test.execution.id.as_str());
            }
            RunEventKind::TestSkipped(test) if !started.contains(test.execution.id.as_str()) => {
                skipped += 1;
            }
            _ => {}
        }
    }
    skipped
}

/// What the XML said about itself, summed over the per-suite counts the document publishes, so a reader adding
/// up `suites[].tests` reaches `xml.tests`.
fn xml_counts(suites: &[bt_junit::Suite]) -> XmlCounts {
    suites.iter().fold(XmlCounts::default(), |total, suite| XmlCounts {
        tests: total.tests.saturating_add(suite.tests),
        failures: total.failures.saturating_add(suite.failures),
        errors: total.errors.saturating_add(suite.errors),
        skipped: total.skipped.saturating_add(suite.skipped),
    })
}

/// The parser's suites in the shape the document declares, in the order [`ORDERING`] promises - which is the
/// order [`bt_junit::parse_report`] already put them in.
fn reported_suites(suites: &[bt_junit::Suite]) -> Vec<Suite> {
    suites
        .iter()
        .map(|suite| Suite {
            name: suite.name.clone(),
            timestamp: suite.timestamp.clone(),
            duration_ms: millis_of(suite.time_seconds),
            document_index: suite.document_index,
            tests: suite.tests,
            failures: suite.failures,
            errors: suite.errors,
            skipped: suite.skipped,
            cases: suite
                .cases
                .iter()
                .map(|case| Case {
                    class_name: case.class_name.clone(),
                    name: case.name.clone(),
                    status: match case.outcome {
                        bt_junit::Outcome::Passed => CaseStatus::Passed,
                        bt_junit::Outcome::Failed => CaseStatus::Failed,
                        bt_junit::Outcome::Skipped => CaseStatus::Skipped,
                    },
                    duration_ms: millis_of(case.time_seconds),
                })
                .collect(),
        })
        .collect()
}

/// A JUnit `time` in seconds as the milliseconds the document publishes.
fn millis_of(seconds: f64) -> f64 {
    (seconds * 1_000.0).round()
}

/// Every failed case in the XML, as failure entries.
fn xml_failures(suites: &[bt_junit::Suite]) -> Vec<Failure> {
    suites
        .iter()
        .flat_map(|suite| {
            suite
                .cases
                .iter()
                .filter(|case| case.outcome == bt_junit::Outcome::Failed)
                .map(move |case| failure_from_xml(suite, case))
        })
        .collect()
}

/// One failed case.
///
/// The message falls back through three sources, and the order is the contract: the `message` attribute even
/// when it is empty - an empty message is a writer that had nothing to say, not a missing one - then the first
/// line of the detail, then a constant.
fn failure_from_xml(suite: &bt_junit::Suite, case: &bt_junit::TestCase) -> Failure {
    let failure = case.failure.as_ref();
    let detail_text = failure.map_or("", |failure| failure.detail.as_str());
    let raw = match failure {
        Some(bt_junit::CaseFailure {
            message: Some(message), ..
        }) => message.as_str(),
        _ if !detail_text.is_empty() => first_line(detail_text),
        _ => "test failed",
    };
    let (message, message_truncated) = clip(raw, MAX_FAILURE_MESSAGE_CHARS);
    let (detail, detail_truncated) = clip(detail_text, MAX_FAILURE_DETAIL_CHARS);
    Failure {
        source: FailureSource::JunitXml,
        suite: Some(suite.name.clone()),
        suite_timestamp: suite.timestamp.clone(),
        class_name: Some(case.class_name.clone()),
        test_name: case.name.clone(),
        kind: match failure.map(|failure| failure.kind) {
            Some(bt_junit::FailureKind::Error) => FailureKind::Error,
            _ => FailureKind::Failure,
        },
        r#type: failure.and_then(|failure| failure.r#type.clone()),
        message,
        relevant_frames: relevant_frames(&detail),
        detail: non_empty(detail),
        message_truncated,
        detail_truncated,
    }
}

/// The two things one pass over the stream answers.
struct Progress {
    failures: Vec<Failure>,
    active_tests: Vec<ActiveTest>,
}

/// The daemon's account of the run: what failed, and what never finished.
///
/// The active set is the point. A test that started and never finished is what a killed run leaves behind, and it
/// is the only evidence naming *which* test the worker died on - the XML for it was never written.
///
/// Insertion order is kept, and re-starting an id that is still active keeps its original position; a start after
/// a finish is a new entry at the end. The list reaches an agent verbatim.
fn progress_state(events: &[RunEvent]) -> Progress {
    let mut active: Vec<ActiveTest> = Vec::new();
    let mut failures = Vec::new();
    for event in events {
        let (execution, error, kind) = match &event.kind {
            RunEventKind::TestStarted(started) => {
                let test = ActiveTest {
                    id: started.execution.id.clone(),
                    display_name: started.execution.display_name.clone(),
                    class_name: started.execution.class_name.clone(),
                    method_name: started.execution.method_name.clone(),
                    started_at: Some(started.timestamp.clone()),
                };
                match active.iter_mut().find(|known| known.id == test.id) {
                    Some(known) => *known = test,
                    None => active.push(test),
                }
                continue;
            }
            RunEventKind::TestSkipped(skipped) => {
                active.retain(|known| known.id != skipped.execution.id);
                continue;
            }
            RunEventKind::TestFinished(finished) => {
                active.retain(|known| known.id != finished.execution.id);
                if finished.status != STATUS_FAILED {
                    continue;
                }
                (&finished.execution, &finished.error, FailureKind::Test)
            }
            RunEventKind::ContainerFailed(failed) => (&failed.execution, &failed.error, FailureKind::Container),
            _ => continue,
        };
        let raw = error.clone().unwrap_or_else(|| format!("{} failed", execution.display_name));
        let (message, message_truncated) = clip(first_line(&raw), MAX_FAILURE_MESSAGE_CHARS);
        let (detail, detail_truncated) = clip(&raw, MAX_FAILURE_DETAIL_CHARS);
        failures.push(Failure {
            source: FailureSource::Progress,
            suite: None,
            suite_timestamp: None,
            class_name: execution.class_name.clone(),
            test_name: execution.display_name.clone(),
            kind,
            r#type: None,
            message,
            relevant_frames: relevant_frames(&detail),
            detail: non_empty(detail),
            message_truncated,
            detail_truncated,
        });
    }
    Progress {
        failures,
        active_tests: active,
    }
}

/// Every class a condition ruled out, with the reason JUnit gave. Read from the stream because a ruled-out class
/// writes no testcase: the reason - which for a real-CLI prerequisite names the missing command and the machine
/// that was asked - exists nowhere else.
fn skipped_containers(events: &[RunEvent]) -> Vec<SkippedContainer> {
    events
        .iter()
        .filter_map(|event| match &event.kind {
            RunEventKind::ContainerSkipped(skipped) => Some(SkippedContainer {
                class_name: skipped.execution.class_name.clone(),
                display_name: skipped.execution.display_name.clone(),
                reason: skipped.reason.clone(),
            }),
            _ => None,
        })
        .collect()
}

/// Every class the plan discovered that the run then said nothing about at all.
///
/// The one account of a suite that never ran: `planStarted` names what JUnit selected, and every later record
/// names a class it reached. A class in the first list and in none of the rest is a suite the run dropped, which
/// is what a watchdog that kills the IDE leaves behind. Read from the stream and not from the suites, because the
/// XML is written in one burst at the end of the plan, so the run this describes is exactly the run whose XML is
/// incomplete or absent.
///
/// A class the run merely did not *finish* started, so it is among the active tests instead. The two lists are
/// disjoint: what broke, and what never got a turn. The daemon's own order is kept.
fn unreported_classes(events: &[RunEvent]) -> Vec<String> {
    let Some(plan) = events.iter().rev().find_map(|event| match &event.kind {
        RunEventKind::PlanStarted(plan) => Some(plan),
        _ => None,
    }) else {
        return Vec::new();
    };
    let reached: HashSet<&str> = events
        .iter()
        .filter_map(|event| match &event.kind {
            RunEventKind::TestStarted(started) => started.execution.class_name.as_deref(),
            RunEventKind::TestFinished(finished) => finished.execution.class_name.as_deref(),
            RunEventKind::TestSkipped(skipped) | RunEventKind::ContainerSkipped(skipped) => skipped.execution.class_name.as_deref(),
            RunEventKind::ContainerFailed(failed) => failed.execution.class_name.as_deref(),
            _ => None,
        })
        .collect();
    plan.class_names
        .iter()
        .filter(|class_name| !reached.contains(class_name.as_str()))
        .cloned()
        .collect()
}

/// What the daemon's watchdog had to say, read from the *last* record of each kind: a watchdog publishes
/// repeatedly and may expire more than once, and an early expiry the run recovered from must not be reported as
/// the reason it stopped.
fn watchdog_report(events: &[RunEvent]) -> Watchdog {
    let last_state = events.iter().rev().find_map(|event| match &event.kind {
        RunEventKind::WatchdogState(state) => Some(WatchdogState {
            phase: state.phase.clone(),
            observed_at: Some(state.timestamp.clone()),
            phase_deadline: state.phase_deadline.clone(),
            emergency_deadline: state.emergency_deadline.clone(),
            next_deadline_in_ms: state.next_deadline_in_ms,
            active_execution_timeout_ms: state.active_execution_timeout_ms,
            progress_gap_timeout_ms: state.progress_gap_timeout_ms,
            active_execution: state.active_execution.clone(),
        }),
        _ => None,
    });
    let expired = events.iter().rev().find_map(|event| match &event.kind {
        RunEventKind::WatchdogExpired(expired) => Some(WatchdogExpiry {
            reason: expired.reason.clone(),
            // An empty detail is dropped: absent reads as "the reason says it all", empty as a detail that was
            // written and lost.
            detail: expired.detail.clone().filter(|detail| !detail.is_empty()),
            deadline: Some(expired.deadline.clone()),
            expired_at: Some(expired.expired_at.clone()),
            active_execution: expired.active_execution.clone(),
            evidence: safe_evidence_paths(events),
        }),
        _ => None,
    });
    Watchdog { last_state, expired }
}

// --- the verdict ----------------------------------------------------------------------------------------------

/// Every reason this run cannot be believed, in the order a reader should read them.
///
/// The list *is* the verdict: a non-empty one makes the run an `infrastructure_error` and nothing else can. That
/// is what lets the shard and flake aggregates re-use one word instead of re-deriving a second opinion.
fn verdict_diagnostics(
    input: &Input,
    parsed: Option<&bt_junit::Report>,
    integrity: Integrity,
    execution: &ExecutionCounts,
    reported: &XmlCounts,
    watchdog: &Watchdog,
    unreported: &[String],
) -> Vec<String> {
    let mut diagnostics = Vec::new();
    // First, because it explains every other diagnostic: a jar that moved under the run is a reason the counts,
    // the XML and the stream can all disagree.
    if let Some(drift) = input.hot_jar_drift.as_ref().filter(|drift| !drift.is_empty()) {
        diagnostics.push(drift.clone());
    }
    if integrity != Integrity::Complete {
        let explained = input
            .source
            .diagnostic
            .clone()
            .or_else(|| parsed.and_then(|report| report.integrity.message.clone()));
        diagnostics.push(explained.unwrap_or_else(|| format!("JUnit result is {integrity}")));
    }
    if input.summary.is_none() {
        diagnostics.push("daemon stream ended without a summary".to_owned());
    }
    if let Some(protocol) = input.protocol_diagnostic.as_ref().filter(|protocol| !protocol.is_empty()) {
        diagnostics.push(protocol.clone());
    }
    if let Some(expired) = &watchdog.expired {
        diagnostics.push(describe_expiry(expired, &input.evidence));
    }
    // Lost coverage is a diagnostic and not a note, so the run it describes can never be reported as passed.
    if !unreported.is_empty() {
        diagnostics.push(format!(
            "{} class(es) were selected and never reported: {}",
            unreported.len(),
            describe_classes(unreported)
        ));
    }
    // The two accounts are only worth comparing when both are trustworthy: comparing a complete XML against an
    // absent summary, or a summary against a truncated document, would send the reader to reconcile counts that
    // were never meant to agree about a run whose real problem is already named above.
    if let Some(summary) = &input.summary
        && integrity == Integrity::Complete
    {
        diagnostics.extend(count_mismatches(summary, &input.events, execution, reported));
    }
    diagnostics
}

/// The watchdog's expiry as one diagnostic line. Detail, the running test and the evidence ride on this line
/// rather than becoming diagnostics of their own: none of the three is ever a reason on its own.
fn describe_expiry(expired: &WatchdogExpiry, artifacts: &[EvidenceArtifact]) -> String {
    let mut line = format!("daemon watchdog expired: {}", expired.reason);
    if let Some(detail) = &expired.detail {
        line.push_str(" — ");
        line.push_str(detail);
    }
    if let Some(active) = &expired.active_execution {
        line.push_str(" while ");
        line.push_str(&active.describe());
    }
    line + &describe_evidence(artifacts, &expired.evidence)
}

/// How many classes one diagnostic spells before it counts the rest.
///
/// A lane that loses its IDE on its first suite drops twenty-odd classes, and twenty-odd names ahead of a verdict
/// bury the verdict. The whole list is on the document.
pub const MAX_NAMED_CLASSES: usize = 8;

fn describe_classes(names: &[String]) -> String {
    if names.len() <= MAX_NAMED_CLASSES {
        return names.join(", ");
    }
    format!(
        "{}, and {} more",
        names[..MAX_NAMED_CLASSES].join(", "),
        names.len() - MAX_NAMED_CLASSES
    )
}

/// Where the screenshots ended up, so nobody has to know the heartbeat capture exists to find them. A file the
/// daemon named and the controller could not fetch is reported with its reason rather than dropped.
fn describe_evidence(artifacts: &[EvidenceArtifact], guest_paths: &[String]) -> String {
    if !artifacts.is_empty() {
        let named: Vec<String> = artifacts
            .iter()
            .map(|artifact| match (&artifact.artifact_path, &artifact.error) {
                (Some(path), _) => path.clone(),
                (None, Some(error)) => format!("{} (not pulled: {error})", artifact.guest_path),
                (None, None) => format!("{} (not pulled: unknown)", artifact.guest_path),
            })
            .collect();
        return format!("; evidence: {}", named.join(", "));
    }
    if !guest_paths.is_empty() {
        return format!("; evidence on the guest: {}", guest_paths.join(", "));
    }
    String::new()
}

/// The daemon's account of the run against the XML's.
///
/// Four separate comparisons, because each names a different producer bug. The expectations are not guesses: the
/// XML counts every test that *started*, including aborted ones; a discovery-time skip never started; and a failed
/// container is serialized as its own testcase, so it appears in both the test count and the failure count.
fn count_mismatches(summary: &Summary, events: &[RunEvent], execution: &ExecutionCounts, reported: &XmlCounts) -> Vec<String> {
    let expected_tests = execution
        .tests_started
        .saturating_add(pre_start_skipped_count(events))
        .saturating_add(execution.container_failures);
    let expected_failures = execution.tests_failed.saturating_add(execution.container_failures);
    let expected_has_failures = execution.tests_failed > 0 || execution.container_failures > 0;
    let reported_failures = reported.failures.saturating_add(reported.errors);

    let mut mismatches = Vec::new();
    if summary.has_failures != expected_has_failures {
        mismatches.push(format!(
            "summary hasFailures mismatch: reported={}, expected={expected_has_failures}",
            summary.has_failures
        ));
    }
    if reported.tests != expected_tests {
        mismatches.push(format!(
            "summary/XML test-count mismatch: summary={expected_tests}, XML={}",
            reported.tests
        ));
    }
    if reported_failures != expected_failures {
        mismatches.push(format!(
            "summary/XML failure-count mismatch: summary={expected_failures}, XML={reported_failures}"
        ));
    }
    if reported.skipped != execution.tests_skipped {
        mismatches.push(format!(
            "summary/XML skip-count mismatch: summary={}, XML={}",
            execution.tests_skipped, reported.skipped
        ));
    }
    mismatches
}

/// The single-run status ladder: first match wins, and the order is the whole point.
///
/// - A diagnostic outranks everything. A run nobody can believe has no verdict to give.
/// - A container that *failed* is `failed`, and it is asked *before* the skipped-container branch: both are true
///   of a lane that breaks on its first class, and the wrong one reports "every class was deliberately ruled out"
///   for a lane whose first class crashed - a missing prerequisite CLI rather than a broken IDE launch. Measured on
///   air-linux-1 on 2026-08-22: a `@BeforeAll` timeout came back as `no_tests` with advice to check the FQN.
/// - A class a condition ruled out is `all_skipped`, not `no_tests`: the reason it carries is the answer.
const fn decide_status(diagnostics: &[String], execution: &ExecutionCounts, reported: &XmlCounts) -> Status {
    let nothing_ran = execution.tests_started == 0 && execution.tests_skipped == 0;
    if !diagnostics.is_empty() {
        Status::InfrastructureError
    } else if nothing_ran && execution.container_failures > 0 {
        Status::Failed
    } else if nothing_ran && execution.containers_skipped > 0 {
        Status::AllSkipped
    } else if nothing_ran {
        Status::NoTests
    } else if execution.tests_failed > 0 || execution.container_failures > 0 || reported.failures > 0 || reported.errors > 0 {
        Status::Failed
    } else {
        Status::Passed
    }
}

// --- text bounds ----------------------------------------------------------------------------------------------

const TRUNCATION_MARKER: &str = "\n… truncated …";

/// Cuts a value to the bound the document declares, in characters, and says whether it cut. A failure message is
/// routinely not ASCII, so a byte bound would clip it at a third of the declared length; the cut always lands on a
/// character boundary.
fn clip(value: &str, maximum: usize) -> (String, bool) {
    match value.char_indices().nth(maximum) {
        Some((cut, _)) => (format!("{}{TRUNCATION_MARKER}", &value[..cut]), true),
        None => (value.to_owned(), false),
    }
}

/// A stack trace reduced to the frames worth reading first.
///
/// An Air UI test failure arrives with a stack whose top forty frames are the JUnit platform and the coroutine
/// machinery, and the frame that names the defect is below them. When no frame names Air, the hot classpath or
/// the starter, the whole stack is kept: an empty list would read as "no stack".
fn relevant_frames(detail: &str) -> Vec<String> {
    let frames: Vec<&str> = detail.lines().map(str::trim).filter(|line| line.starts_with("at ")).collect();
    let relevant: Vec<&str> = frames
        .iter()
        .copied()
        .filter(|frame| {
            frame.contains("com.intellij.air.") || frame.contains("air-ui-hot//") || frame.contains("com.intellij.ide.starter.")
        })
        .collect();
    let chosen = if relevant.is_empty() { frames } else { relevant };
    chosen.into_iter().take(MAX_RELEVANT_FRAMES).map(str::to_owned).collect()
}

fn non_empty(value: String) -> Option<String> {
    (!value.is_empty()).then_some(value)
}

// --- instants -------------------------------------------------------------------------------------------------

/// How long the run took, and zero when either stamp cannot be read as an instant or the completion is not after
/// the start: a negative duration reads as a corrupt document rather than as a clock that moved.
fn elapsed_ms(started_at: &str, completed_at: &str) -> f64 {
    match (instant_millis(started_at), instant_millis(completed_at)) {
        (Some(started), Some(completed)) if completed > started => (completed - started) as f64,
        _ => 0.0,
    }
}

/// One of this controller's timestamps as an instant in Unix milliseconds, or `None` when it is not one.
///
/// An instant and not text: `2026-08-22T12:00:00+02:00` is *before* `2026-08-22T11:00:00Z`, and comparing the two
/// as strings says the opposite. A stamp with no offset is refused rather than read as local time, so the same
/// text cannot mean two instants on two machines. The aggregates fold many reports' stamps through this same
/// reading.
pub fn instant_millis(stamp: &str) -> Option<i64> {
    stamp.parse::<jiff::Timestamp>().ok().map(jiff::Timestamp::as_millisecond)
}

// --- persistence ----------------------------------------------------------------------------------------------

/// The one owner of the per-run artifact layout, `<worker>/reports/<daemonRunId>`, so a report and its evidence
/// never disagree about where.
///
/// The run id is validated rather than trusted: it reaches this controller from a lease receipt and from a flag,
/// and it ends up as a path component.
pub fn directory(settings: &Config, worker: &str, daemon_run_id: &str) -> Result<PathBuf, Refusal> {
    validate_name(daemon_run_id, "daemon run id")?;
    Ok(settings.worker_dir(worker).join("reports").join(daemon_run_id))
}

/// Where one iteration's fetched guest files go, beside the report that names them.
pub fn evidence_directory(settings: &Config, worker: &str, daemon_run_id: &str, iteration_id: &str) -> Result<PathBuf, Refusal> {
    let directory = directory(settings, worker, daemon_run_id)?;
    validate_name(iteration_id, "iteration id")?;
    Ok(directory.join(format!("{iteration_id}-evidence")))
}

fn destination_exists(destination: &std::path::Path) -> Refusal {
    Refusal::new(
        "report_destination_exists",
        Exit::CANT_CREATE,
        format!("refusing to overwrite VM run report: {}", destination.display()),
    )
}

/// Writes the exact report the CLI envelope carries, privately, and refuses to overwrite an earlier one.
///
/// The refusal is the point, and the filesystem enforces it: the publish is a no-clobber move, so two runs that
/// claimed the same daemon run id and iteration id cannot silently lose one run's evidence between them. The
/// check before it only makes the refusal name the file before any bytes are written.
///
/// Indented with two spaces and terminated with one newline, which is what agents and `diff` read.
pub fn persist(settings: &Config, worker: &str, document: &RunReport) -> Result<PathBuf, Refusal> {
    let directory = directory(settings, worker, &document.daemon_run_id)?;
    validate_name(&document.iteration_id, "iteration id")?;
    create_private_dir(&directory)?;

    let destination = directory.join(format!("{}.json", document.iteration_id));
    match fs::symlink_metadata(&destination) {
        Ok(_) => return Err(destination_exists(&destination)),
        Err(error) if error.kind() == io::ErrorKind::NotFound => {}
        Err(error) => {
            return Err(Refusal::new(
                "state_read_failed",
                Exit::FAILURE,
                format!("cannot inspect {}: {error}", destination.display()),
            ));
        }
    }

    let mut encoded = serde_json::to_vec_pretty(document).or_refuse("report_encode_failed", Exit::FAILURE, || {
        format!("cannot encode the run report for {}", document.iteration_id)
    })?;
    encoded.push(b'\n');
    match avl_base::fs::write_exclusively(&destination, &encoded, 0o600) {
        Ok(()) => Ok(destination),
        // The same code as the check above, for the same collision reached a moment later: which of the two
        // noticed is not something the caller should have to distinguish.
        Err(PublishError::DestinationExists(_)) => Err(destination_exists(&destination)),
        Err(error) => Err(Refusal::new(
            "report_publish_failed",
            Exit::NO_PERM,
            format!("could not atomically publish VM run report: {}", destination.display()),
        )
        .with_details(serde_json::json!({ "cause": error.to_string() }))),
    }
}
