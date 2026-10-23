#![expect(
    clippy::float_cmp,
    reason = "durations are whole milliseconds, exact in f64, so an epsilon would hide a wrong conversion"
)]

use std::path::Path;

use avl_wire::daemon::{
    ContainerFailed, ExecutionIdentity, PlanStarted, Skipped, TestFinished, TestStarted, TypedFailure, WatchdogExpired,
};
use avl_wire::report::{ScenarioReport, decode_run_report};
use pretty_assertions::assert_eq;

use super::*;
use crate::testing::settings;

// --- fixtures -------------------------------------------------------------------------------------------------

const CLASS: &str = "com.intellij.air.AirFlowSmokeUiTest";

fn summary(started: u32, failed: u32, skipped: u32, containers_skipped: u32, container_failures: u32) -> Summary {
    Summary {
        iteration_id: "iteration-1".to_owned(),
        daemon_boot_stamp: "2026-08-22T09:59:00Z".to_owned(),
        started_at: "2026-08-22T10:00:00Z".to_owned(),
        tests_started: started,
        tests_failed: failed,
        tests_skipped: skipped,
        containers_skipped,
        container_failures,
        has_failures: failed > 0 || container_failures > 0,
        ide_running: true,
    }
}

fn input(summary: Option<Summary>, events: Vec<RunEvent>) -> Input {
    Input {
        iteration_id: "iteration-1".to_owned(),
        daemon_run_id: "run-ui-daemon-0001".to_owned(),
        daemon_boot_stamp: "2026-08-22T09:59:00Z".to_owned(),
        selection: CLASS.to_owned(),
        started_at: "2026-08-22T10:00:00Z".to_owned(),
        completed_at: "2026-08-22T10:00:30Z".to_owned(),
        summary,
        events,
        evidence: Vec::new(),
        protocol_diagnostic: None,
        notes: Vec::new(),
        traces: Vec::new(),
        traces_error: None,
        tree: None,
        tree_error: None,
    }
}

/// Builds a report and proves the wire accepts what this module produced.
fn built(from: Input) -> RunReport {
    let document = build(from);
    document
        .validate()
        .unwrap_or_else(|error| panic!("this module produced a document its own wire refuses: {error}"));
    document
}

fn diagnostic(document: &RunReport) -> &str {
    document.verdict_diagnostic.as_deref().unwrap_or("<absent>")
}

fn identity(id: &str, display_name: &str, class_name: Option<&str>) -> ExecutionIdentity {
    ExecutionIdentity {
        id: id.to_owned(),
        display_name: display_name.to_owned(),
        class_name: class_name.map(str::to_owned),
        method_name: None,
    }
}

fn started(id: &str, display_name: &str, class_name: Option<&str>, timestamp: &str) -> RunEvent {
    RunEvent::synthesized(RunEventKind::TestStarted(TestStarted {
        timestamp: timestamp.to_owned(),
        execution: identity(id, display_name, class_name),
    }))
}

/// A finished test whose failure, when `message` is given, holds only that message.
fn finished(id: &str, display_name: &str, status: &str, class_name: Option<&str>, message: Option<&str>) -> RunEvent {
    let failure = TypedFailure {
        message: message.map(str::to_owned),
        ..TypedFailure::default()
    };
    finished_with(id, display_name, status, class_name, failure)
}

fn finished_with(id: &str, display_name: &str, status: &str, class_name: Option<&str>, failure: TypedFailure) -> RunEvent {
    RunEvent::synthesized(RunEventKind::TestFinished(TestFinished {
        timestamp: "t".to_owned(),
        execution: identity(id, display_name, class_name),
        status: status.to_owned(),
        duration_ms: 12_500,
        failure,
        report: ScenarioReport::default(),
    }))
}

/// One scenario of [`CLASS`] that started and ended with `status`.
fn scenario(id: &str, status: &str, message: Option<&str>) -> Vec<RunEvent> {
    vec![
        started(id, "smoke", Some(CLASS), "2026-08-22T10:00:01Z"),
        finished(id, "smoke", status, Some(CLASS), message),
    ]
}

fn skipped(id: &str, display_name: &str, reason: &str) -> RunEvent {
    RunEvent::synthesized(RunEventKind::TestSkipped(Skipped {
        timestamp: "t".to_owned(),
        execution: identity(id, display_name, None),
        reason: reason.to_owned(),
    }))
}

fn container_skipped(id: &str, class_name: &str, reason: &str) -> RunEvent {
    RunEvent::synthesized(RunEventKind::ContainerSkipped(Skipped {
        timestamp: "t".to_owned(),
        execution: identity(id, class_name, Some(class_name)),
        reason: reason.to_owned(),
    }))
}

fn container_failed(id: &str, failure: TypedFailure) -> RunEvent {
    RunEvent::synthesized(RunEventKind::ContainerFailed(ContainerFailed {
        timestamp: "t".to_owned(),
        execution: identity(id, id, Some(id)),
        failure,
    }))
}

fn plan(class_names: &[&str]) -> RunEvent {
    RunEvent::synthesized(RunEventKind::PlanStarted(PlanStarted {
        timestamp: "t0".to_owned(),
        discovered_executions: u32::try_from(class_names.len()).unwrap(),
        class_names: class_names.iter().map(|name| (*name).to_owned()).collect(),
        executions: Vec::new(),
    }))
}

fn watchdog_state(phase: &str, timestamp: &str, active: i64, gap: i64) -> RunEvent {
    RunEvent::synthesized(RunEventKind::WatchdogState(avl_wire::daemon::WatchdogState {
        timestamp: timestamp.to_owned(),
        phase: phase.to_owned(),
        active_execution_timeout_ms: active,
        progress_gap_timeout_ms: gap,
        phase_deadline: None,
        emergency_deadline: None,
        next_deadline_in_ms: None,
        active_execution: None,
    }))
}

fn expired(reason: &str, detail: Option<&str>, evidence: &[&str]) -> WatchdogExpired {
    WatchdogExpired {
        reason: reason.to_owned(),
        deadline: "d".to_owned(),
        expired_at: "e".to_owned(),
        detail: detail.map(str::to_owned),
        active_execution: None,
        evidence: evidence.iter().map(|path| (*path).to_owned()).collect(),
    }
}

fn bundle(test_class: &str, scenario: &str, status: &str) -> TraceBundle {
    TraceBundle {
        id: format!("{test_class}-{scenario}"),
        entry: format!("r/{test_class}/{scenario}/"),
        test_class: test_class.to_owned(),
        scenario: scenario.to_owned(),
        flow: None,
        status: status.to_owned(),
        has_video: false,
    }
}

fn archive(bundles: Vec<TraceBundle>) -> TraceArchive {
    TraceArchive {
        path: "/host/runs/r/traces/iteration-1/001.zip".to_owned(),
        bytes: 2048,
        bundles,
    }
}

// --- the verdict ladder ---------------------------------------------------------------------------------------

#[test]
fn a_healthy_run_is_passed_and_explains_nothing() {
    let mut events = vec![plan(&[CLASS])];
    events.extend(scenario("1", "SUCCESSFUL", None));
    let document = built(input(Some(summary(1, 0, 0, 0, 0)), events));
    assert_eq!(document.status, Status::Passed, "{}", diagnostic(&document));
    assert_eq!(document.verdict_diagnostic, None);
    // 30 s by instant, and the ordering promise is the document's own.
    assert_eq!(document.duration_ms, 30_000.0);
    assert_eq!(document.ordering, ORDERING);
    let cases: Vec<(&str, &str, CaseStatus, f64)> = document
        .cases
        .iter()
        .map(|case| (case.class_name.as_str(), case.name.as_str(), case.status, case.duration_ms))
        .collect();
    assert_eq!(cases, [(CLASS, "smoke", CaseStatus::Passed, 12_500.0)]);
}

// A skipped case says why it did not run, as its `testSkipped` record does, and a case that ran says no reason. The
// reason reaches the document and survives the wire.
#[test]
fn a_skipped_case_carries_the_reason_of_its_skip() {
    let reason = "scenario 'later' never ran: the shared IDE was recycled 3 time(s) and isolation still failed";
    let mut events = vec![plan(&[CLASS])];
    events.extend(scenario("1", "SUCCESSFUL", None));
    events.push(RunEvent::synthesized(RunEventKind::TestSkipped(Skipped {
        timestamp: "2026-08-22T10:00:02Z".to_owned(),
        execution: identity("2", "later", Some(CLASS)),
        reason: reason.to_owned(),
    })));
    let document = built(input(Some(summary(1, 0, 1, 0, 0)), events));
    assert_eq!(document.status, Status::Passed, "{}", diagnostic(&document));
    let reasons: Vec<(&str, CaseStatus, Option<&str>)> = document
        .cases
        .iter()
        .map(|case| (case.name.as_str(), case.status, case.reason.as_deref()))
        .collect();
    assert_eq!(
        reasons,
        [("smoke", CaseStatus::Passed, None), ("later", CaseStatus::Skipped, Some(reason))]
    );
    let decoded = decode_run_report(&serde_json::to_vec(&document).unwrap()).unwrap();
    assert_eq!(decoded.cases[1].reason.as_deref(), Some(reason));
}

/// The note that a test jar moved on the host while the iteration ran.
const DRIFT: &str = "1 test jar(s) were rebuilt on the host while this iteration ran: ui_test_lib.jar";

/// A test jar that the host rebuilt while the iteration ran is a note. The guest ran the pushed jars, so the
/// status follows the counts, and the note reaches the document and survives the wire.
#[test]
fn a_host_rebuilt_jar_is_a_note_and_the_status_follows_the_counts() {
    for (status_word, failed, status) in [("SUCCESSFUL", 0, Status::Passed), ("FAILED", 1, Status::Failed)] {
        let mut from = input(Some(summary(1, failed, 0, 0, 0)), scenario("1", status_word, Some("boom")));
        from.notes = vec![DRIFT.to_owned()];
        let document = built(from);
        assert_eq!(document.status, status, "{}", diagnostic(&document));
        assert_eq!(document.verdict_diagnostic, None);
        assert_eq!(document.notes, vec![DRIFT.to_owned()]);
        let decoded = decode_run_report(&serde_json::to_vec(&document).unwrap()).unwrap();
        assert_eq!(decoded.notes, document.notes);
    }
}

#[test]
fn an_absent_or_empty_note_leaves_a_healthy_run_passing_with_no_notes() {
    for notes in [Vec::new(), vec![String::new()]] {
        let mut from = input(Some(summary(1, 0, 0, 0, 0)), scenario("1", "SUCCESSFUL", None));
        from.notes = notes.clone();
        let document = built(from);
        assert_eq!(document.status, Status::Passed, "notes {notes:?}");
        assert!(document.notes.is_empty(), "notes {notes:?}");
        let encoded = serde_json::to_string(&document).unwrap();
        assert!(!encoded.contains("\"notes\""), "an empty list is absent on the wire: {encoded}");
    }
}

/// The lane daemon types its failure: the kind, the message and the detail reach the document as they were sent.
#[test]
fn a_typed_failure_keeps_its_type_message_and_detail() {
    let typed = TypedFailure {
        kind: Some("com.intellij.air.integration.AirFlowAssertionError".to_owned()),
        message: Some("instruction 4 assert-chip-visible: the chip is not visible".to_owned()),
        detail: Some("caused by: no chip\n\tat com.intellij.air.integration.bridge.AirChip.visible(AirChip.kt:41)".to_owned()),
    };
    let events = vec![
        started("1", "attach-file", Some(CLASS), "2026-08-22T10:00:01Z"),
        finished_with("1", "attach-file", "FAILED", Some(CLASS), typed),
    ];
    let document = built(input(Some(summary(1, 1, 0, 0, 0)), events));
    assert_eq!(document.status, Status::Failed, "{}", diagnostic(&document));
    let failure = &document.failures[0];
    assert_eq!(
        (failure.class_name.as_deref(), failure.test_name.as_str(), failure.kind),
        (Some(CLASS), "attach-file", FailureKind::Test)
    );
    assert_eq!(
        failure.r#type.as_deref(),
        Some("com.intellij.air.integration.AirFlowAssertionError")
    );
    assert_eq!(failure.message, "instruction 4 assert-chip-visible: the chip is not visible");
    assert_eq!(
        failure.relevant_frames,
        ["at com.intellij.air.integration.bridge.AirChip.visible(AirChip.kt:41)"]
    );
}

#[test]
fn an_absent_summary_is_an_infrastructure_error() {
    let document = built(input(None, scenario("1", "SUCCESSFUL", None)));
    assert_eq!(document.status, Status::InfrastructureError);
    assert!(diagnostic(&document).contains("ended without a summary"));
}

/// Each count of the summary is compared with the records of its own stream, because each names a different defect.
#[test]
fn the_summary_and_the_stream_are_reconciled_field_by_field() {
    let document = built(input(Some(summary(2, 1, 1, 0, 0)), scenario("1", "SUCCESSFUL", None)));
    let text = diagnostic(&document);
    for want in [
        "summary/stream testsStarted mismatch: summary=2, records=1",
        "summary/stream testsFailed mismatch: summary=1, records=0",
        "summary/stream testsSkipped mismatch: summary=1, records=0",
    ] {
        assert!(text.contains(want), "the diagnostic does not name {want:?}: {text}");
    }
    // The fourth comparison agrees here, and is asserted absent so the other three are not read as noise.
    assert!(!text.contains("hasFailures"), "{text}");
    assert_eq!(document.status, Status::InfrastructureError);

    // And the guard itself: a summary that disagrees with its own counters.
    let mut lying = summary(1, 0, 0, 0, 0);
    lying.has_failures = true;
    let document = built(input(Some(lying), scenario("1", "SUCCESSFUL", None)));
    assert!(
        diagnostic(&document).contains("summary hasFailures mismatch: reported=true, expected=false"),
        "{}",
        diagnostic(&document)
    );
}

/// A run that failed as a whole sends `hasFailures` with no failed case. Its own record is the diagnostic, and the
/// summary check adds no second line about it.
#[test]
fn a_run_that_failed_as_a_whole_explains_its_has_failures() {
    let mut whole = summary(0, 0, 0, 0, 0);
    whole.has_failures = true;
    for record in [
        RunEventKind::RunFailed(avl_wire::daemon::RunFailed {
            error: "readiness: the IDE never reached READY".to_owned(),
        }),
        RunEventKind::WatchdogExpired(expired("progress gap", None, &[])),
    ] {
        let mut from = input(Some(whole.clone()), vec![RunEvent::synthesized(record)]);
        from.protocol_diagnostic = Some("readiness: the IDE never reached READY".to_owned());
        let document = built(from);
        assert_eq!(document.status, Status::InfrastructureError);
        assert!(!diagnostic(&document).contains("hasFailures"), "{}", diagnostic(&document));
    }
    // Without such a record, the same summary contradicts its counts.
    let document = built(input(Some(whole), Vec::new()));
    assert!(
        diagnostic(&document).contains("summary hasFailures mismatch: reported=true, expected=false"),
        "{}",
        diagnostic(&document)
    );
}

/// Zero tests ran in all three, and the three answers are different instructions to the caller.
#[test]
fn nothing_running_is_three_different_verdicts() {
    let probes = [
        (
            "a condition ruled every class out",
            vec![
                container_skipped("c1", "air.A", "no CLI"),
                container_skipped("c2", "air.B", "no CLI"),
            ],
            summary(0, 0, 0, 2, 0),
            Status::AllSkipped,
        ),
        (
            "a prologue failed",
            vec![container_failed("air.A", TypedFailure::default())],
            summary(0, 0, 0, 0, 1),
            Status::Failed,
        ),
        ("the selector matched nothing", Vec::new(), summary(0, 0, 0, 0, 0), Status::NoTests),
    ];
    for (name, events, counts, want) in probes {
        let document = built(input(Some(counts), events.clone()));
        assert_eq!(document.status, want, "{name}: {}", diagnostic(&document));
        // The same records with no summary are always an infrastructure error.
        assert_eq!(
            built(input(None, events)).status,
            Status::InfrastructureError,
            "{name} without a summary"
        );
    }
}

// --- the two accounts -----------------------------------------------------------------------------------------

/// The recorder closes the bundle of each scenario with its own status. A status that contradicts the stream makes
/// the run unbelievable; one that agrees, a bundle the recorder never closed, and a scenario with no bundle decide
/// nothing.
#[test]
fn the_recorder_is_the_second_account_of_each_case() {
    let mut events = scenario("1", "SUCCESSFUL", None);
    events.extend([
        started("2", "attach", Some(CLASS), "2026-08-22T10:00:02Z"),
        finished("2", "attach", "FAILED", Some(CLASS), Some("boom")),
        started("3", "aborted", Some(CLASS), "2026-08-22T10:00:03Z"),
        finished("3", "aborted", "ABORTED", Some(CLASS), Some("no codex")),
        started("4", "untraced", Some(CLASS), "2026-08-22T10:00:04Z"),
        finished("4", "untraced", "SUCCESSFUL", Some(CLASS), None),
    ]);
    let counts = summary(4, 1, 1, 0, 0);
    // The recorder names the class without its package.
    let agreeing = vec![archive(vec![
        bundle("AirFlowSmokeUiTest", "smoke", "passed"),
        bundle("AirFlowSmokeUiTest", "attach", "failed"),
        bundle("AirFlowSmokeUiTest", "aborted", "aborted"),
    ])];
    let mut from = input(Some(counts.clone()), events.clone());
    from.traces = agreeing;
    let document = built(from);
    assert_eq!(document.status, Status::Failed, "{}", diagnostic(&document));
    let recorded: Vec<Option<&str>> = document.cases.iter().map(|case| case.recorded.as_deref()).collect();
    assert_eq!(recorded, [Some("passed"), Some("failed"), Some("aborted"), None]);

    let mut from = input(Some(counts), events);
    from.traces = vec![archive(vec![
        bundle("AirFlowSmokeUiTest", "smoke", "failed"),
        bundle("AirFlowSmokeUiTest", "attach", "passed"),
        bundle("AirFlowSmokeUiTest", "aborted", "truncated"),
    ])];
    let document = built(from);
    assert_eq!(document.status, Status::InfrastructureError);
    assert_eq!(
        diagnostic(&document),
        "the recorder contradicts the run stream on 2 case(s): com.intellij.air.AirFlowSmokeUiTest.smoke (stream passed, \
         recorder failed), com.intellij.air.AirFlowSmokeUiTest.attach (stream failed, recorder passed)"
    );
    // The bundle the recorder never closed is named on its case, and compared with nothing.
    assert_eq!(document.cases[2].recorded.as_deref(), Some("truncated"));
}

/// A test that started and never finished is the only evidence naming which test the worker died on, and it is an
/// active test rather than a case.
#[test]
fn the_stream_names_the_test_a_killed_run_died_on() {
    let document = built(input(
        None,
        vec![
            started("1", "first", None, "t1"),
            finished("1", "first", "SUCCESSFUL", None, None),
            started("2", "second", None, "t2"),
            started("3", "third", None, "t3"),
        ],
    ));
    let ids: Vec<&str> = document.active_tests.iter().map(|test| test.execution.id.as_str()).collect();
    assert_eq!(ids, ["2", "3"]);
    assert_eq!(document.active_tests[0].started_at.as_deref(), Some("t2"));
    let cases: Vec<&str> = document.cases.iter().map(|case| case.name.as_str()).collect();
    assert_eq!(cases, ["first"]);
}

/// The suites a killed run never reached are named, and naming them is what makes the run unbelievable. On
/// 2026-08-27 a `--lane ui` watchdog killed the IDE on one suite and every suite queued behind it reported nothing
/// at all.
#[test]
fn the_suites_a_run_never_reached_are_named() {
    let document = built(input(
        None,
        vec![
            plan(&["air.AlphaTest", "air.BetaTest", "air.GammaTest", "air.DeltaTest"]),
            // Alpha ran and failed, Beta started and never finished, Gamma was ruled out. Delta never got a turn.
            started("1", "a", Some("air.AlphaTest"), "t1"),
            finished("1", "a", "FAILED", Some("air.AlphaTest"), None),
            started("2", "b", Some("air.BetaTest"), "t2"),
            container_skipped("3", "air.GammaTest", "no agent CLI"),
        ],
    ));
    // Only Delta: a class that started is in `activeTests`, and the two lists must not both claim it.
    assert_eq!(document.unreported_classes, ["air.DeltaTest"]);
    assert_eq!(document.status, Status::InfrastructureError);
    assert!(
        diagnostic(&document).contains("1 class(es) were selected and never reported: air.DeltaTest"),
        "{}",
        diagnostic(&document)
    );
    // The failure carries its fallback message.
    assert_eq!(document.failures[0].message, "a failed");
    assert_eq!(document.skipped_containers[0].reason, "no agent CLI");
}

#[test]
fn a_whole_plan_that_ran_names_no_unreported_class() {
    let mut events = vec![plan(&[CLASS])];
    events.extend(scenario("1", "SUCCESSFUL", None));
    let document = built(input(Some(summary(1, 0, 0, 0, 0)), events));
    assert!(document.unreported_classes.is_empty());
    assert_eq!(document.status, Status::Passed, "{}", diagnostic(&document));
}

/// The lane daemon plans by simple names and records by FQNs. Measured on 2026-10-15: the first daemon run read every
/// class as unreported, though 76 scenarios finished.
#[test]
fn a_plan_of_simple_names_is_covered_by_records_of_fqns() {
    let mut events = vec![plan(&["AirFlowSmokeUiTest"])];
    events.extend(scenario("1", "SUCCESSFUL", None));
    let document = built(input(Some(summary(1, 0, 0, 0, 0)), events));
    assert_eq!(document.unreported_classes, Vec::<String>::new());
    assert_eq!(document.status, Status::Passed, "{}", diagnostic(&document));
}

/// A daemon from before the record existed, or one that died in discovery: nothing selected to compare against.
#[test]
fn a_stream_with_no_plan_record_names_nothing() {
    let document = built(input(Some(summary(1, 0, 0, 0, 0)), scenario("1", "SUCCESSFUL", None)));
    assert!(document.unreported_classes.is_empty());
}

#[test]
fn the_diagnostic_names_eight_classes_and_counts_the_rest() {
    let selected: Vec<String> = (0..MAX_NAMED_CLASSES + 3).map(|index| format!("air.Suite{index:02}Test")).collect();
    let names: Vec<&str> = selected.iter().map(String::as_str).collect();
    let document = built(input(None, vec![plan(&names)]));
    assert_eq!(document.unreported_classes, selected);
    assert!(
        diagnostic(&document).contains("air.Suite07Test, and 3 more"),
        "{}",
        diagnostic(&document)
    );
}

/// The cases are in the order of the instants of their first records, whatever order the records came in.
#[test]
fn the_cases_are_in_start_order() {
    let events = vec![
        started("2", "second", Some(CLASS), "2026-08-22T10:00:02Z"),
        started("1", "first", Some(CLASS), "2026-08-22T10:00:01+00:00"),
        finished("2", "second", "SUCCESSFUL", Some(CLASS), None),
        finished("1", "first", "SUCCESSFUL", Some(CLASS), None),
    ];
    let document = built(input(Some(summary(2, 0, 0, 0, 0)), events));
    let names: Vec<&str> = document.cases.iter().map(|case| case.name.as_str()).collect();
    assert_eq!(names, ["first", "second"]);
}

/// A restart of a still-active id keeps its place with the newer record; a restart after a finish is a new entry
/// at the end. The list reaches an agent verbatim.
#[test]
fn the_active_set_keeps_insertion_order() {
    let active = active_tests(&[
        started("a", "a1", None, "t1"),
        started("b", "b1", None, "t2"),
        started("a", "a2", None, "t3"),
    ]);
    let shown: Vec<(&str, &str)> = active
        .iter()
        .map(|test| (test.execution.id.as_str(), test.execution.display_name.as_str()))
        .collect();
    assert_eq!(shown, [("a", "a2"), ("b", "b1")]);

    let restarted = active_tests(&[
        started("a", "a1", None, "t1"),
        started("b", "b1", None, "t2"),
        finished("a", "a1", "SUCCESSFUL", None, None),
        started("a", "a3", None, "t4"),
    ]);
    let ids: Vec<&str> = restarted.iter().map(|test| test.execution.id.as_str()).collect();
    assert_eq!(ids, ["b", "a"]);
}

/// `ABORTED` is a skip, because that is what the daemon's own summary does with it.
#[test]
fn the_counts_are_reconstructed_from_the_stream_when_the_summary_is_gone() {
    let counts = execution_counts(
        None,
        &[
            started("1", "1", None, "t"),
            finished("1", "1", "FAILED", None, None),
            started("2", "2", None, "t"),
            finished("2", "2", "ABORTED", None, None),
            skipped("3", "3", "r"),
            container_skipped("c1", "C1", "r"),
            container_failed("c2", TypedFailure::default()),
        ],
    );
    assert_eq!(
        counts,
        ExecutionCounts {
            tests_started: 2,
            tests_failed: 1,
            tests_skipped: 2,
            containers_skipped: 1,
            container_failures: 1,
        }
    );
}

/// The *last* state and the *last* expiry: an early expiry a run recovered from is not the reason it stopped.
#[test]
fn the_watchdog_is_read_from_its_last_word() {
    let watchdog = watchdog_report(&[
        watchdog_state("starting", "t1", 1, 2),
        RunEvent::synthesized(RunEventKind::WatchdogExpired(expired("first", None, &[]))),
        watchdog_state("running", "t2", 3, 4),
        RunEvent::synthesized(RunEventKind::WatchdogExpired(expired(
            "second",
            Some(""),
            &["/tmp/shot.png", "/tmp/../etc/passwd.txt", "/tmp/shot.png", "/tmp/notes.md"],
        ))),
    ]);
    let state = watchdog.last_state.unwrap();
    assert_eq!(
        (
            state.phase.as_str(),
            state.observed_at.as_deref(),
            state.active_execution_timeout_ms
        ),
        ("running", Some("t2"), 3)
    );
    let expiry = watchdog.expired.unwrap();
    assert_eq!(expiry.reason, "second");
    // An empty detail is dropped: absent reads as "the reason says it all", empty as a detail written and lost.
    assert_eq!(expiry.detail, None);
    // Traversal, a duplicate and a non-capture extension are all dropped; the guard is the wire's.
    assert_eq!(expiry.evidence, ["/tmp/shot.png"]);
}

/// Detail, the running test and the evidence ride on one line.
#[test]
fn an_expiry_is_one_diagnostic_naming_where_the_screenshots_went() {
    let mut record = expired("progress gap", Some("no record for 300 s"), &["/Users/air/shot.png"]);
    record.active_execution = Some(ExecutionIdentity {
        id: "1".to_owned(),
        display_name: "smoke()".to_owned(),
        class_name: Some("com.intellij.air.A".to_owned()),
        method_name: Some("smoke".to_owned()),
    });
    let mut from = input(
        Some(summary(0, 0, 0, 0, 0)),
        vec![RunEvent::synthesized(RunEventKind::WatchdogExpired(record))],
    );
    from.evidence = vec![
        EvidenceArtifact {
            guest_path: "/Users/air/shot.png".to_owned(),
            artifact_path: Some("/host/artifacts/shot.png".to_owned()),
            error: None,
        },
        EvidenceArtifact {
            guest_path: "/Users/air/late.png".to_owned(),
            artifact_path: None,
            error: Some("pull timed out".to_owned()),
        },
    ];
    let document = built(from.clone());
    assert_eq!(
        diagnostic(&document),
        "daemon watchdog expired: progress gap — no record for 300 s while com.intellij.air.A#smoke; \
         evidence: /host/artifacts/shot.png, /Users/air/late.png (not pulled: pull timed out)"
    );
    // With nothing fetched, the guest paths are named instead - the caller can still go and look.
    from.evidence = Vec::new();
    let document = built(from);
    assert!(
        diagnostic(&document).ends_with("; evidence on the guest: /Users/air/shot.png"),
        "{}",
        diagnostic(&document)
    );
}

// --- bounds ---------------------------------------------------------------------------------------------------

/// The bound is characters, so a message of em dashes is not clipped at a third of it, and a cut never lands inside
/// a character.
#[test]
fn clip_counts_characters_and_never_splits_one() {
    let dashes = "—".repeat(100);
    assert_eq!(clip(&dashes, 100), (dashes.clone(), false));
    let (clipped, truncated) = clip(&dashes, 99);
    assert!(truncated);
    assert_eq!(clipped, format!("{}{TRUNCATION_MARKER}", "—".repeat(99)));

    let (clipped, truncated) = clip("🎉🎉", 1);
    assert!(truncated);
    assert_eq!(clipped, format!("🎉{TRUNCATION_MARKER}"));
}

/// When no frame names Air, the hot classpath or the starter, the whole stack is kept, capped.
#[test]
fn relevant_frames_fall_back_to_the_whole_stack_and_are_capped() {
    let mut platform = vec!["java.lang.AssertionError: boom".to_owned()];
    platform.extend((0..12).map(|index| format!("\tat org.junit.platform.Frame{index}(Frame.java:{index})")));
    let frames = relevant_frames(&platform.join("\r\n"));
    assert_eq!(frames.len(), MAX_RELEVANT_FRAMES);
    assert_eq!(frames[0], "at org.junit.platform.Frame0(Frame.java:0)");

    platform.extend([
        "\tat com.intellij.air.AirFlowSmokeUiTest.smoke(A.kt:1)".to_owned(),
        "\tat air-ui-hot//com.example.Hot.run(Hot.kt:2)".to_owned(),
        "\tat com.intellij.ide.starter.Runner.run(Runner.kt:3)".to_owned(),
    ]);
    assert_eq!(
        relevant_frames(&platform.join("\n")),
        [
            "at com.intellij.air.AirFlowSmokeUiTest.smoke(A.kt:1)",
            "at air-ui-hot//com.example.Hot.run(Hot.kt:2)",
            "at com.intellij.ide.starter.Runner.run(Runner.kt:3)",
        ]
    );
}

/// `+02:00` is *before* the same wall time at `Z`, and comparing the two as strings reported a negative wall
/// clock as zero.
#[test]
fn durations_are_measured_by_instant_and_not_by_text() {
    let mut from = input(Some(summary(1, 0, 0, 0, 0)), scenario("1", "SUCCESSFUL", None));
    from.started_at = "2026-08-22T12:00:00+02:00".to_owned();
    from.completed_at = "2026-08-22T10:00:30Z".to_owned();
    assert_eq!(built(from.clone()).duration_ms, 30_000.0);
    from.completed_at = "not a timestamp".to_owned();
    assert_eq!(built(from.clone()).duration_ms, 0.0);
    from.started_at = "2026-08-22T10:00:30Z".to_owned();
    from.completed_at = "2026-08-22T10:00:00Z".to_owned();
    assert_eq!(built(from).duration_ms, 0.0);
    // A stamp with no offset would mean two instants on two machines.
    assert_eq!(instant_millis("2026-08-22T10:00:00"), None);
}

// --- persistence ----------------------------------------------------------------------------------------------

#[cfg(unix)]
fn mode(path: &Path) -> u32 {
    use std::os::unix::fs::PermissionsExt;
    fs::symlink_metadata(path).unwrap().permissions().mode() & 0o777
}

/// `<worker>/reports/<run>/<iteration>.json`, private, one trailing newline, read back by the wire - and never
/// overwritten, because replacing evidence about a run that already happened is the one thing a publish must not
/// do.
#[test]
fn persist_publishes_privately_and_refuses_to_overwrite() {
    let root = tempfile::tempdir().unwrap();
    let resolved = settings(root.path());
    let worker = resolved.workers[0].clone();
    let document = built(input(
        Some(summary(1, 1, 0, 0, 0)),
        scenario("1", "FAILED", Some("expected: <\"/a\"> but was: <\"/b\">")),
    ));

    let path = persist(&resolved, &worker, &document).unwrap();
    assert_eq!(
        path,
        resolved
            .worker_dir(&worker)
            .join("reports")
            .join("run-ui-daemon-0001")
            .join("iteration-1.json")
    );
    #[cfg(unix)]
    {
        assert_eq!(mode(&path), 0o600);
        assert_eq!(mode(path.parent().unwrap()), 0o700);
        // The `reports` directory it had to create is private as well: it lists the run ids.
        assert_eq!(mode(path.parent().unwrap().parent().unwrap()), 0o700);
    }
    // No temporary is left behind.
    assert_eq!(fs::read_dir(path.parent().unwrap()).unwrap().count(), 1);

    let content = fs::read_to_string(&path).unwrap();
    assert!(content.ends_with("}\n") && !content.ends_with("\n\n"));
    assert!(content.contains("\n  \"iterationId\": \"iteration-1\""), "{content}");
    let decoded = decode_run_report(content.as_bytes()).unwrap();
    assert_eq!(decoded, document);
    assert_eq!(decoded.failures[0].message, r#"expected: <"/a"> but was: <"/b">"#);

    let refusal = persist(&resolved, &worker, &document).unwrap_err();
    assert_eq!(refusal.code, "report_destination_exists");
    assert_eq!(refusal.exit, Exit::CANT_CREATE);
    assert_eq!(fs::read_to_string(&path).unwrap(), content);
}

/// Both name components end up as path components, and both reach this controller as input.
#[test]
fn the_report_path_refuses_a_name_a_shell_would_reinterpret() {
    let root = tempfile::tempdir().unwrap();
    let resolved = settings(root.path());
    let worker = &resolved.workers[0];
    assert_eq!(directory(&resolved, worker, "../../etc").unwrap_err().code, "unsafe_name");
    assert_eq!(
        evidence_directory(&resolved, worker, "run-1", "a b").unwrap_err().code,
        "unsafe_name"
    );
    let evidence = evidence_directory(&resolved, worker, "run-1", "iteration-1").unwrap();
    assert!(
        evidence.ends_with(Path::new("reports").join("run-1").join("iteration-1-evidence")),
        "{}",
        evidence.display()
    );
}

// --- what never decides the verdict ---------------------------------------------------------------------------

/// A traces error and an archive that did not arrive decide nothing (scenario-trace.spec: a trace never decides a
/// verdict). A bundle that agrees with its case decides nothing either.
#[test]
fn traces_are_carried_and_never_decide_the_verdict() {
    let passing = input(Some(summary(1, 0, 0, 0, 0)), scenario("1", "SUCCESSFUL", None));
    let baseline = built(passing.clone());
    let reason = "trace-pack-ready in air-linux-1 exited with 70".to_owned();
    let mut with_error = passing;
    with_error.traces_error = Some(reason.clone());
    let with_error = built(with_error);
    assert_eq!(
        (with_error.status, &with_error.verdict_diagnostic),
        (baseline.status, &baseline.verdict_diagnostic)
    );
    assert_eq!(with_error.traces_error, Some(reason));
    assert!(with_error.trace_archives.is_empty());

    let traces = archive(vec![bundle(CLASS, "smoke", "failed")]);
    let mut failing = input(Some(summary(1, 1, 0, 0, 0)), scenario("1", "FAILED", Some("boom")));
    failing.traces = vec![traces.clone()];
    let document = built(failing);
    assert_eq!(document.status, Status::Failed);
    assert_eq!(document.verdict_diagnostic, None);
    assert_eq!(document.trace_archives, [traces]);
}

/// The tree pair goes onto the document verbatim and decides nothing: a green run on a dirty tree stays green, and
/// so does one whose tree could not be read.
#[test]
fn the_tree_is_carried_and_never_decides_the_verdict() {
    let passing = input(Some(summary(1, 0, 0, 0, 0)), scenario("1", "SUCCESSFUL", None));
    let baseline = built(passing.clone());
    assert_eq!((&baseline.tree, &baseline.tree_error), (&None, &None));

    let dirty = Tree {
        head: "1e46d8efe7b9e".to_owned(),
        uncommitted: vec!["plugins/air/a.kt".to_owned()],
        uncommitted_count: 1,
    };
    let mut with_tree = passing.clone();
    with_tree.tree = Some(dirty.clone());
    let with_tree = built(with_tree);
    assert_eq!(with_tree.tree, Some(dirty));
    assert_eq!((with_tree.status, &with_tree.verdict_diagnostic), (Status::Passed, &None));

    let reason = "git rev-parse --verify HEAD exited with 128: fatal: not a git repository".to_owned();
    let mut with_error = passing;
    with_error.tree_error = Some(reason.clone());
    let with_error = built(with_error);
    assert_eq!((with_error.tree, with_error.tree_error), (None, Some(reason)));
    assert_eq!((with_error.status, with_error.verdict_diagnostic), (Status::Passed, None));
}

/// Every declared array is present as an array, which is what a reader of these bytes is promised; evidence is
/// absent for a run that named none.
#[test]
fn every_declared_array_is_an_array() {
    let document = built(input(None, vec![]));
    let encoded = serde_json::to_string(&document).unwrap();
    for field in ["cases", "failures", "skippedContainers", "activeTests", "unreportedClasses"] {
        assert!(
            encoded.contains(&format!(r#""{field}":[]"#)),
            "{field} is not an empty array: {encoded}"
        );
    }
    assert!(!encoded.contains(r#""evidence""#), "{encoded}");
}

/// A lane truncated at its first class is a failure, not a lane that was ruled out. On 2026-08-23 a `--lane ui`
/// lost the IDE its suites share when one class failed, and every class after it came back as a skipped container;
/// on the first class both `containerFailures > 0` and `containersSkipped > 0` are true.
#[test]
fn a_lane_truncated_at_its_first_class_is_a_failure() {
    let mut events = vec![container_failed(
        "air.SetupBrokenTest",
        TypedFailure {
            kind: Some("java.util.concurrent.TimeoutException".to_owned()),
            message: Some("Before all[timeout=30s] failed".to_owned()),
            detail: Some("timed out".to_owned()),
        },
    )];
    events.extend((0..15).map(|index| container_skipped(&format!("c{index}"), &format!("air.Later{index}Test"), "the IDE is gone")));
    let document = built(input(Some(summary(0, 0, 0, 15, 1)), events));
    assert_eq!(document.status, Status::Failed, "{}", diagnostic(&document));
    // The counts that distinguish a truncated lane from a filtered one survive the verdict.
    assert_eq!(
        (document.execution.container_failures, document.execution.containers_skipped),
        (1, 15)
    );
    assert_eq!(document.failures[0].kind, FailureKind::Container);
    assert_eq!(document.failures[0].message, "Before all[timeout=30s] failed");
}

/// The adversarial half: the case above must fail against the *old* branch order, or it is not testing the
/// ordering at all.
#[test]
fn the_truncation_verdict_depends_on_the_branch_order() {
    let execution = ExecutionCounts {
        containers_skipped: 15,
        container_failures: 1,
        ..ExecutionCounts::default()
    };
    let nothing_ran = execution.tests_started == 0 && execution.tests_skipped == 0;
    assert!(nothing_ran, "the fixture no longer describes a lane where nothing ran");
    // Both branches are true of this lane, which is the whole hazard.
    assert!(execution.container_failures > 0 && execution.containers_skipped > 0);
    let old_order = if nothing_ran && execution.containers_skipped > 0 {
        Status::AllSkipped
    } else if nothing_ran && execution.container_failures > 0 {
        Status::Failed
    } else {
        Status::Passed
    };
    assert_eq!(
        old_order,
        Status::AllSkipped,
        "the old order no longer demonstrates the defect this guards"
    );
    assert_eq!(decide_status(&[], &execution, &[]), Status::Failed);
}
