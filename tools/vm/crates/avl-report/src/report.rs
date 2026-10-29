//! Building one run's report out of the two accounts of that run, and publishing it where it cannot be
//! overwritten.
//!
//! The document itself lives in `avl_wire::report`; what is here is the arithmetic that decides what goes in it.
//! The run stream of the daemon is the first account of a run, and [`avl_junit::cases`] reads it as cases. The recorder
//! is the second account: it closes the trace bundle of each scenario with a status of its own. Every disagreement
//! that [`build`] can see becomes a diagnostic, and every diagnostic makes the run an `infrastructure_error`.
//!
//! That is the whole design rule, and it is a refusal rather than a preference. A stream that was cut off when the IDE
//! was killed looks exactly as authoritative as a whole one, and the run it describes reads green. Every branch below
//! that adds a diagnostic instead of a number exists because some run once reported the wrong thing that way.
//!
//! A note is the other kind of fact. It explains the run and never decides the verdict. A test jar that the host
//! rebuilt while the iteration ran is a note. The guest ran the jars that the iteration pushed, so the execution and
//! its counts stay true. Only the attribution to the checkout changes, and the note names the jars.

use std::fs;
use std::io;
use std::path::PathBuf;

use avl_base::RefusalExt;
use avl_base::fs::{PublishError, create_private_dir};
use avl_base::{Config, Exit, OrRefuse, Refusal, validate_name};
use avl_wire::daemon::{RunEvent, RunEventKind, Summary};
use avl_wire::report::{
    ActiveTest, Case, CaseStatus, EvidenceArtifact, ExecutionCounts, Failure, FailureKind, MAX_FAILURE_DETAIL_CHARS,
    MAX_FAILURE_MESSAGE_CHARS, MAX_RELEVANT_FRAMES, ORDERING, RUN_REPORT_SCHEMA_VERSION, RunReport, SkippedContainer, Status, TraceArchive,
    TraceBundle, Tree, Watchdog, WatchdogExpiry, WatchdogState, expiry_evidence_paths,
};

use avl_junit::cases::{
    self, CaseOutcome, FailureOrigin, STATUS_ABORTED, STATUS_FAILED, StreamCase, simple_class_name, unreported_classes,
};

#[cfg(test)]
mod tests;

/// Everything one run leaves behind, as the controller collected it.
///
/// `summary` is optional and `events` is not: a stream that ended without a summary is the normal shape of a
/// killed run and has to stay representable, while a run with no events at all never started and is reported
/// from its watchdog alone.
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
    /// The guest files the controller managed to fetch, or the reason it could not. Every entry names a path
    /// [`avl_wire::report::safe_evidence_paths`] admitted.
    pub evidence: Vec<EvidenceArtifact>,
    /// An empty diagnostic and none are different facts on the document: the first says something wrote a reason
    /// and lost it.
    pub protocol_diagnostic: Option<String>,
    /// The facts the report carries as [`RunReport::notes`], such as a test jar that the host rebuilt while the
    /// iteration ran. A note never enters the verdict, and [`build`] drops an empty one.
    pub notes: Vec<String>,
    /// The iteration's scenario traces as the controller pulled them, and why it could not. The status of a bundle
    /// that arrived is the second account of its scenario. A missing archive and the error never decide the verdict.
    pub traces: Vec<TraceArchive>,
    pub traces_error: Option<String>,
    /// The host checkout the build read, and why it could not be read; carried verbatim and never read by the verdict.
    pub tree: Option<Tree>,
    pub tree_error: Option<String>,
}

/// Assembles one run's report and decides its verdict.
pub fn build(input: Input) -> RunReport {
    let execution = execution_counts(input.summary.as_ref(), &input.events);
    let stream_cases = cases::cases(&input.events);
    let reported = report_cases(&stream_cases, &input.traces);
    let failures = failures_of(&stream_cases);
    let watchdog = watchdog_report(&input.events);
    let unreported = unreported_classes(&input.events);

    let diagnostics = verdict_diagnostics(&input, &execution, &watchdog, &unreported, &reported);
    let status = decide_status(&diagnostics, &execution, &failures);

    RunReport {
        report_schema_version: RUN_REPORT_SCHEMA_VERSION,
        duration_ms: elapsed_ms(&input.started_at, &input.completed_at),
        ordering: ORDERING.to_owned(),
        status,
        execution,
        cases: reported,
        failures,
        skipped_containers: skipped_containers(&input.events),
        active_tests: active_tests(&input.events),
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
        notes: input.notes.into_iter().filter(|note| !note.is_empty()).collect(),
        // Carried verbatim rather than folded into the verdict diagnostic: a protocol complaint is about this
        // controller's channel to the daemon, a verdict diagnostic about the run, and a reader acting on them goes
        // to different machines.
        protocol_diagnostic: input.protocol_diagnostic,
    }
}

// --- the two accounts of a run --------------------------------------------------------------------------------

/// The daemon's own count of what it ran, and the reconstruction from the records when it never said.
///
/// The reconstruction is not a substitute: a stream with no summary is already a diagnostic, so these counts
/// describe a run that is reported as broken, never one that passes. `ABORTED` counts as skipped because that is
/// what the daemon's own summary does with it: a test the run gave up on did not fail.
fn execution_counts(summary: Option<&Summary>, events: &[RunEvent]) -> ExecutionCounts {
    match summary {
        Some(summary) => ExecutionCounts {
            tests_started: summary.tests_started,
            tests_failed: summary.tests_failed,
            tests_skipped: summary.tests_skipped,
            containers_skipped: summary.containers_skipped,
            container_failures: summary.container_failures,
        },
        None => stream_counts(events),
    }
}

/// The counts that the records of the stream add up to.
fn stream_counts(events: &[RunEvent]) -> ExecutionCounts {
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

/// The cases of the stream in the shape the document declares, in [`ORDERING`], each with the status the recorder
/// gave its bundle. A case that never finished is an active test instead.
fn report_cases(stream_cases: &[StreamCase], archives: &[TraceArchive]) -> Vec<Case> {
    let bundles: Vec<&TraceBundle> = archives.iter().flat_map(|archive| &archive.bundles).collect();
    let mut cases: Vec<Case> = stream_cases
        .iter()
        .filter_map(|case| {
            let (status, reason) = match &case.outcome {
                CaseOutcome::Passed => (CaseStatus::Passed, None),
                CaseOutcome::Failed(_) => (CaseStatus::Failed, None),
                CaseOutcome::Skipped(reason) => (CaseStatus::Skipped, Some(reason.clone())),
                CaseOutcome::Unfinished => return None,
            };
            Some(Case {
                recorded: recorded_status(&bundles, case),
                reason,
                class_name: case.class_name.clone(),
                name: case.name.clone(),
                status,
                #[expect(clippy::cast_precision_loss, reason = "a duration in milliseconds is far below 2^53")]
                duration_ms: case.duration_ms as f64,
                started_at: case.started_at.clone(),
            })
        })
        .collect();
    // Stable, so two cases of one instant, and cases whose stamp is not an instant, keep the order of the stream.
    cases.sort_by_key(|case| instant_millis(&case.started_at).unwrap_or(i64::MAX));
    cases
}

/// The status of the last bundle of the scenario of `case`. A recorder may name the class without its package, so
/// the simple names are compared.
fn recorded_status(bundles: &[&TraceBundle], case: &StreamCase) -> Option<String> {
    bundles
        .iter()
        .rev()
        .find(|bundle| bundle.scenario == case.name && simple_class_name(&bundle.test_class) == simple_class_name(&case.class_name))
        .map(|bundle| bundle.status.clone())
}

/// Every failed case of the stream, as failure entries, in the order of the stream.
fn failures_of(stream_cases: &[StreamCase]) -> Vec<Failure> {
    stream_cases
        .iter()
        .filter_map(|case| {
            let CaseOutcome::Failed(failure) = &case.outcome else {
                return None;
            };
            let (message, message_truncated) = clip(&failure.message, MAX_FAILURE_MESSAGE_CHARS);
            let (detail, detail_truncated) = clip(&failure.detail, MAX_FAILURE_DETAIL_CHARS);
            Some(Failure {
                class_name: Some(case.class_name.clone()),
                test_name: case.name.clone(),
                kind: match failure.kind {
                    FailureOrigin::Test => FailureKind::Test,
                    FailureOrigin::Container => FailureKind::Container,
                },
                r#type: failure.r#type.clone(),
                message,
                relevant_frames: relevant_frames(&detail),
                detail: non_empty(detail),
                message_truncated,
                detail_truncated,
            })
        })
        .collect()
}

/// The tests the stream started and never finished: what a killed run leaves behind.
///
/// The active set is the point. It is the only evidence naming *which* test the worker died on. Insertion order is
/// kept, and re-starting an id that is still active keeps its original position; a start after a finish is a new
/// entry at the end. The list reaches an agent verbatim.
fn active_tests(events: &[RunEvent]) -> Vec<ActiveTest> {
    let mut active: Vec<ActiveTest> = Vec::new();
    for event in events {
        match &event.kind {
            RunEventKind::TestStarted(started) => {
                let test = ActiveTest {
                    execution: started.execution.clone(),
                    started_at: Some(started.timestamp.clone()),
                };
                match active.iter_mut().find(|known| known.execution.id == test.execution.id) {
                    Some(known) => *known = test,
                    None => active.push(test),
                }
            }
            RunEventKind::TestSkipped(skipped) => active.retain(|known| known.execution.id != skipped.execution.id),
            RunEventKind::TestFinished(finished) => active.retain(|known| known.execution.id != finished.execution.id),
            _ => {}
        }
    }
    active
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
            evidence: expiry_evidence_paths(events),
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
    execution: &ExecutionCounts,
    watchdog: &Watchdog,
    unreported: &[String],
    cases: &[Case],
) -> Vec<String> {
    let mut diagnostics = Vec::new();
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
    // The summary is compared with the records only when it exists: an absent one is already named above, and the
    // counts are then the records' own.
    if let Some(summary) = &input.summary {
        diagnostics.extend(summary_mismatches(
            summary,
            execution,
            &stream_counts(&input.events),
            failed_as_a_whole(&input.events),
        ));
    }
    diagnostics.extend(recorder_mismatch(cases));
    diagnostics
}

/// The statuses a recorder closes a scenario with: its `done` command.
const DONE_STATUSES: [&str; 3] = ["passed", "failed", "aborted"];

/// Whether the stream names a failure of the run as a whole: a `runFailed` or a `watchdogExpired` record. Such a
/// run has no failed case to count, and each record is a diagnostic of its own.
fn failed_as_a_whole(events: &[RunEvent]) -> bool {
    events
        .iter()
        .any(|event| matches!(event.kind, RunEventKind::RunFailed(_) | RunEventKind::WatchdogExpired(_)))
}

/// The daemon's summary against the records of its own stream.
///
/// Each count is compared on its own, because each names a different defect: a record the decoder lost, a summary
/// that counts another way, or a `hasFailures` that contradicts its own counts. A run that failed as a whole has
/// failures and no failed case, so it may say `hasFailures` with no failed count.
fn summary_mismatches(summary: &Summary, execution: &ExecutionCounts, records: &ExecutionCounts, whole_run_failed: bool) -> Vec<String> {
    let mut mismatches = Vec::new();
    let expected_has_failures = execution.tests_failed > 0 || execution.container_failures > 0;
    if summary.has_failures != expected_has_failures && !(summary.has_failures && whole_run_failed) {
        mismatches.push(format!(
            "summary hasFailures mismatch: reported={}, expected={expected_has_failures}",
            summary.has_failures
        ));
    }
    let counts = [
        ("testsStarted", execution.tests_started, records.tests_started),
        ("testsFailed", execution.tests_failed, records.tests_failed),
        ("testsSkipped", execution.tests_skipped, records.tests_skipped),
        ("containersSkipped", execution.containers_skipped, records.containers_skipped),
        ("containerFailures", execution.container_failures, records.container_failures),
    ];
    for (name, summarized, recorded) in counts {
        if summarized != recorded {
            mismatches.push(format!("summary/stream {name} mismatch: summary={summarized}, records={recorded}"));
        }
    }
    mismatches
}

/// The recorder's account of each case against the stream's: one diagnostic that names every case the two
/// accounts disagree on.
///
/// Only a status of the recorder's `done` is an account. A case with no bundle is not compared, because a trace that
/// did not arrive explains a run and never decides it. Nor is a bundle the recorder never closed, such as one the last
/// pull took while the recorder still wrote it: it holds no `done` at all.
fn recorder_mismatch(cases: &[Case]) -> Option<String> {
    let contradicted: Vec<String> = cases
        .iter()
        .filter_map(|case| {
            let recorded = case.recorded.as_deref().filter(|status| DONE_STATUSES.contains(status))?;
            let expected = match case.status {
                CaseStatus::Passed => "passed",
                CaseStatus::Failed => "failed",
                CaseStatus::Skipped => "aborted",
            };
            (recorded != expected).then(|| format!("{}.{} (stream {}, recorder {recorded})", case.class_name, case.name, case.status))
        })
        .collect();
    (!contradicted.is_empty()).then(|| {
        format!(
            "the recorder contradicts the run stream on {} case(s): {}",
            contradicted.len(),
            describe_classes(&contradicted)
        )
    })
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

/// The single-run status ladder: first match wins, and the order is the whole point.
///
/// - A diagnostic outranks everything. A run nobody can believe has no verdict to give.
/// - A container that *failed* is `failed`, and it is asked *before* the skipped-container branch: both are true
///   of a lane that breaks on its first class, and the wrong one reports "every class was deliberately ruled out"
///   for a lane whose first class crashed - a missing prerequisite CLI rather than a broken IDE launch. Measured on
///   air-linux-1 on 2026-08-22: a `@BeforeAll` timeout came back as `no_tests` with advice to check the FQN.
/// - A class a condition ruled out is `all_skipped`, not `no_tests`: the reason it carries is the answer.
const fn decide_status(diagnostics: &[String], execution: &ExecutionCounts, failures: &[Failure]) -> Status {
    let nothing_ran = execution.tests_started == 0 && execution.tests_skipped == 0;
    if !diagnostics.is_empty() {
        Status::InfrastructureError
    } else if nothing_ran && execution.container_failures > 0 {
        Status::Failed
    } else if nothing_ran && execution.containers_skipped > 0 {
        Status::AllSkipped
    } else if nothing_ran {
        Status::NoTests
    } else if execution.tests_failed > 0 || execution.container_failures > 0 || !failures.is_empty() {
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
