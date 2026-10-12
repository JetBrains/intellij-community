#![expect(
    clippy::float_cmp,
    reason = "durations are whole milliseconds, exact in f64, so an epsilon would hide a wrong conversion"
)]

use std::path::Path;

use avl_base::{Environment, Selection};
use avl_wire::daemon::{ContainerFailed, ExecutionIdentity, PlanStarted, Skipped, TestFinished, TestStarted, WatchdogExpired};
use avl_wire::report::{Retrieval, TraceBundle, decode_run_report};
use bt_junit::IntegrityStatus;
use pretty_assertions::assert_eq;

use super::*;

// --- fixtures -------------------------------------------------------------------------------------------------

/// One passing case in one complete document, which is what a healthy iteration leaves behind.
const PASSING_XML: &str = concat!(
    r#"<testsuites>"#,
    r#"<testsuite name="AirFlowSmokeUiTest" timestamp="2026-08-22T10:00:00Z" tests="1" failures="0" errors="0" skipped="0" time="12.5">"#,
    r#"<testcase classname="com.intellij.air.AirFlowSmokeUiTest" name="smoke" time="12.5"/>"#,
    r#"</testsuite></testsuites>"#,
);

const FAILING_XML: &str = concat!(
    r#"<testsuites>"#,
    r#"<testsuite name="AirFlowSmokeUiTest" timestamp="2026-08-22T10:00:00Z" tests="1" failures="1" errors="0" skipped="0" time="3">"#,
    r#"<testcase classname="com.intellij.air.AirFlowSmokeUiTest" name="smoke" time="3">"#,
    r#"<failure message="expected: &lt;&quot;/a&quot;&gt; but was: &lt;&quot;/b&quot;&gt;" type="java.lang.AssertionError">"#,
    "at org.junit.jupiter.api.Assertions.fail(Assertions.java:1)\n",
    "at com.intellij.air.AirFlowSmokeUiTest.smoke(AirFlowSmokeUiTest.kt:42)\n",
    r#"</failure></testcase></testsuite></testsuites>"#,
);

fn available(xml: &str) -> RetrievedXml {
    RetrievedXml {
        guest_path: "/Users/air/out/junit.xml".to_owned(),
        retrieval: Retrieval::DaemonHttp,
        integrity: RetrievedIntegrity::Available,
        xml: Some(xml.to_owned()),
        diagnostic: None,
    }
}

fn missing() -> RetrievedXml {
    RetrievedXml {
        guest_path: "/x".to_owned(),
        retrieval: Retrieval::Unavailable,
        integrity: RetrievedIntegrity::Missing,
        xml: None,
        diagnostic: None,
    }
}

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

fn input(source: RetrievedXml, summary: Option<Summary>, events: Vec<RunEvent>) -> Input {
    Input {
        iteration_id: "iteration-1".to_owned(),
        daemon_run_id: "run-ui-daemon-0001".to_owned(),
        daemon_boot_stamp: "2026-08-22T09:59:00Z".to_owned(),
        selection: "com.intellij.air.AirFlowSmokeUiTest".to_owned(),
        started_at: "2026-08-22T10:00:00Z".to_owned(),
        completed_at: "2026-08-22T10:00:30Z".to_owned(),
        summary,
        events,
        source,
        evidence: Vec::new(),
        protocol_diagnostic: None,
        hot_jar_drift: None,
        traces: Vec::new(),
        traces_error: None,
        tree: None,
        tree_error: None,
    }
}

/// Builds a report and proves the wire accepts what this module produced.
fn built(from: Input) -> RunReport {
    let document = build(from).unwrap_or_else(|refusal| panic!("the report was refused: {refusal:?}"));
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

fn finished(id: &str, display_name: &str, status: &str, class_name: Option<&str>, error: Option<&str>) -> RunEvent {
    RunEvent::synthesized(RunEventKind::TestFinished(TestFinished {
        timestamp: "t".to_owned(),
        execution: identity(id, display_name, class_name),
        status: status.to_owned(),
        duration_ms: 1,
        error: error.map(str::to_owned),
    }))
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

fn container_failed(id: &str) -> RunEvent {
    RunEvent::synthesized(RunEventKind::ContainerFailed(ContainerFailed {
        timestamp: "t".to_owned(),
        execution: identity(id, id, None),
        error: None,
    }))
}

fn plan(class_names: &[&str]) -> RunEvent {
    RunEvent::synthesized(RunEventKind::PlanStarted(PlanStarted {
        timestamp: "t0".to_owned(),
        discovered_executions: u32::try_from(class_names.len()).unwrap(),
        class_names: class_names.iter().map(|name| (*name).to_owned()).collect(),
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

// --- the verdict ladder ---------------------------------------------------------------------------------------

#[test]
fn a_healthy_run_is_passed_and_explains_nothing() {
    let document = built(input(available(PASSING_XML), Some(summary(1, 0, 0, 0, 0)), vec![]));
    assert_eq!(document.status, Status::Passed, "{}", diagnostic(&document));
    assert_eq!(document.verdict_diagnostic, None);
    assert_eq!((document.xml.tests, document.execution.tests_started), (1, 1));
    // 30 s by instant, and the ordering promise is the document's own.
    assert_eq!(document.duration_ms, 30_000.0);
    assert_eq!(document.ordering, ORDERING);
    assert_eq!(document.suites[0].duration_ms, 12_500.0);
}

/// A jar rebuilt on the host under a running iteration takes the verdict away, even from a document that is
/// complete and agrees with the summary: the execution was clean, and only its subject moved.
#[test]
fn a_host_rebuilt_jar_is_an_infrastructure_error_and_is_explained_first() {
    let mut from = input(available(PASSING_XML), Some(summary(1, 0, 0, 0, 0)), vec![]);
    from.hot_jar_drift = Some("1 test jar(s) were rebuilt on the host while this iteration ran: ui_test_lib.jar".to_owned());
    let document = built(from);
    assert_eq!(document.status, Status::InfrastructureError);
    assert!(
        diagnostic(&document).starts_with("1 test jar(s) were rebuilt"),
        "the drift is not the first thing a reader sees: {}",
        diagnostic(&document)
    );
}

#[test]
fn an_absent_or_empty_drift_leaves_a_healthy_run_passing() {
    for drift in [None, Some(String::new())] {
        let mut from = input(available(PASSING_XML), Some(summary(1, 0, 0, 0, 0)), vec![]);
        from.hot_jar_drift = drift.clone();
        assert_eq!(built(from).status, Status::Passed, "drift {drift:?}");
    }
}

#[test]
fn a_truncated_source_is_an_infrastructure_error_and_takes_its_failures_from_the_stream() {
    // The shape a killed JVM leaves: the document stops mid-element, so its counts are unusable even though it
    // holds a case.
    let cut = PASSING_XML.strip_suffix("</testsuite></testsuites>").unwrap();
    let class = Some("com.intellij.air.AirFlowSmokeUiTest");
    let document = built(input(
        available(cut),
        Some(summary(1, 1, 0, 0, 0)),
        vec![
            started("1", "smoke", class, "2026-08-22T10:00:01Z"),
            finished(
                "1",
                "smoke",
                "FAILED",
                class,
                Some("java.lang.AssertionError: boom\n\tat com.intellij.air.AirFlowSmokeUiTest.smoke(A.kt:1)"),
            ),
        ],
    ));
    assert_eq!(document.status, Status::InfrastructureError);
    assert_eq!(document.source.integrity, Integrity::Truncated);
    assert_eq!(document.failures.len(), 1);
    let failure = &document.failures[0];
    assert_eq!(failure.source, FailureSource::Progress);
    assert_eq!(failure.message, "java.lang.AssertionError: boom");
    assert_eq!(failure.relevant_frames, ["at com.intellij.air.AirFlowSmokeUiTest.smoke(A.kt:1)"]);
    // A mismatch against a document nobody can trust would send the reader to reconcile counts that were never
    // meant to agree.
    assert!(!diagnostic(&document).contains("mismatch"), "{}", diagnostic(&document));
}

#[test]
fn a_complete_source_owns_its_failures() {
    let document = built(input(
        available(FAILING_XML),
        Some(summary(1, 1, 0, 0, 0)),
        // A progress failure for the same test, which must not be published beside the XML's.
        vec![
            started("1", "smoke", None, "2026-08-22T10:00:01Z"),
            finished("1", "smoke", "FAILED", None, Some("boom")),
        ],
    ));
    assert_eq!(document.status, Status::Failed, "{}", diagnostic(&document));
    assert_eq!(document.failures.len(), 1);
    let failure = &document.failures[0];
    assert_eq!(failure.source, FailureSource::JunitXml);
    assert_eq!(failure.message, r#"expected: <"/a"> but was: <"/b">"#);
    assert_eq!(failure.r#type.as_deref(), Some("java.lang.AssertionError"));
    // The stack is reduced to the frame that names Air; the JUnit platform frame above it is dropped.
    assert_eq!(
        failure.relevant_frames,
        ["at com.intellij.air.AirFlowSmokeUiTest.smoke(AirFlowSmokeUiTest.kt:42)"]
    );
    assert_eq!(failure.suite.as_deref(), Some("AirFlowSmokeUiTest"));
    assert_eq!(failure.suite_timestamp.as_deref(), Some("2026-08-22T10:00:00Z"));
}

#[test]
fn an_absent_summary_is_an_infrastructure_error() {
    let document = built(input(available(PASSING_XML), None, vec![]));
    assert_eq!(document.status, Status::InfrastructureError);
    assert!(diagnostic(&document).contains("ended without a summary"));
}

/// Four separate comparisons, because each names a different producer bug.
#[test]
fn the_summary_and_the_xml_are_reconciled_field_by_field() {
    let document = built(input(available(PASSING_XML), Some(summary(2, 1, 1, 0, 0)), vec![]));
    let text = diagnostic(&document);
    for want in [
        "summary/XML test-count mismatch: summary=2, XML=1",
        "summary/XML failure-count mismatch: summary=1, XML=0",
        "summary/XML skip-count mismatch: summary=1, XML=0",
    ] {
        assert!(text.contains(want), "the diagnostic does not name {want:?}: {text}");
    }
    // The fourth comparison agrees here, and is asserted absent so the other three are not read as noise.
    assert!(!text.contains("hasFailures"), "{text}");
    assert_eq!(document.status, Status::InfrastructureError);

    // And the guard itself: a summary that disagrees with its own counters.
    let mut lying = summary(1, 0, 0, 0, 0);
    lying.has_failures = true;
    let document = built(input(available(PASSING_XML), Some(lying), vec![]));
    assert!(
        diagnostic(&document).contains("summary hasFailures mismatch: reported=true, expected=false"),
        "{}",
        diagnostic(&document)
    );
}

/// A discovery-time skip has no `testStarted`, so the XML holds a testcase `testsStarted` never counted.
#[test]
fn a_discovery_time_skip_is_expected_in_the_xml_count() {
    let xml = concat!(
        r#"<testsuites>"#,
        r#"<testsuite name="S" timestamp="2026-08-22T10:00:00Z" tests="2" failures="0" errors="0" skipped="1" time="1">"#,
        r#"<testcase classname="com.intellij.air.S" name="ran" time="1"/>"#,
        r#"<testcase classname="com.intellij.air.S" name="ruled out" time="0"><skipped/></testcase>"#,
        r#"</testsuite></testsuites>"#,
    );
    let document = built(input(
        available(xml),
        Some(summary(1, 0, 1, 0, 0)),
        vec![skipped("2", "ruled out", "no CLI")],
    ));
    assert_eq!(document.status, Status::Passed, "{}", diagnostic(&document));
}

/// Zero tests ran in all three, and the three answers are different instructions to the caller.
#[test]
fn nothing_running_is_three_different_verdicts() {
    let probes = [
        // A ruled-out container writes no testcase; a container that *failed* is serialized as one, which is why
        // the same zero-test run has a one-failure document.
        (
            "a condition ruled every class out",
            "<testsuites></testsuites>",
            Some(summary(0, 0, 0, 2, 0)),
            Status::AllSkipped,
        ),
        ("a @BeforeAll threw", FAILING_XML, Some(summary(0, 0, 0, 0, 1)), Status::Failed),
        (
            "the selector matched nothing",
            "<testsuites></testsuites>",
            Some(summary(0, 0, 0, 0, 0)),
            Status::NoTests,
        ),
    ];
    for (name, xml, counts, want) in probes {
        let document = built(input(available(xml), counts.clone(), vec![]));
        assert_eq!(document.status, want, "{name}: {}", diagnostic(&document));
        // The same counts over a source nobody can read are always an infrastructure error.
        assert_eq!(
            built(input(missing(), counts, vec![])).status,
            Status::InfrastructureError,
            "{name} over a missing document"
        );
    }
}

// --- the two accounts -----------------------------------------------------------------------------------------

/// A test that started and never finished is the only evidence naming which test the worker died on.
#[test]
fn the_stream_names_the_test_a_killed_run_died_on() {
    let document = built(input(
        missing(),
        None,
        vec![
            started("1", "first", None, "t1"),
            finished("1", "first", "SUCCESSFUL", None, None),
            started("2", "second", None, "t2"),
            started("3", "third", None, "t3"),
        ],
    ));
    let ids: Vec<&str> = document.active_tests.iter().map(|test| test.id.as_str()).collect();
    assert_eq!(ids, ["2", "3"]);
    assert_eq!(document.active_tests[0].started_at.as_deref(), Some("t2"));
}

/// The suites a killed run never reached are named, and naming them is what makes the run unbelievable. On
/// 2026-08-27 a `--lane ui` watchdog killed the IDE on one suite and every suite queued behind it reported nothing
/// at all.
#[test]
fn the_suites_a_run_never_reached_are_named() {
    let document = built(input(
        missing(),
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
    // The progress failure carries its fallback message.
    assert_eq!(document.failures[0].message, "a failed");
    assert_eq!(document.skipped_containers[0].reason, "no agent CLI");
}

#[test]
fn a_whole_plan_that_ran_names_no_unreported_class() {
    let class = Some("com.intellij.air.AirFlowSmokeUiTest");
    let document = built(input(
        available(PASSING_XML),
        Some(summary(1, 0, 0, 0, 0)),
        vec![
            plan(&["com.intellij.air.AirFlowSmokeUiTest"]),
            started("1", "smoke", class, "t1"),
            finished("1", "smoke", "SUCCESSFUL", class, None),
        ],
    ));
    assert!(document.unreported_classes.is_empty());
    assert_eq!(document.status, Status::Passed, "{}", diagnostic(&document));
}

/// A daemon from before the record existed, or one that died in discovery: nothing selected to compare against.
#[test]
fn a_stream_with_no_plan_record_names_nothing() {
    let document = built(input(available(PASSING_XML), Some(summary(1, 0, 0, 0, 0)), vec![]));
    assert!(document.unreported_classes.is_empty());
}

#[test]
fn the_diagnostic_names_eight_classes_and_counts_the_rest() {
    let selected: Vec<String> = (0..MAX_NAMED_CLASSES + 3).map(|index| format!("air.Suite{index:02}Test")).collect();
    let names: Vec<&str> = selected.iter().map(String::as_str).collect();
    let document = built(input(missing(), None, vec![plan(&names)]));
    assert_eq!(document.unreported_classes, selected);
    assert!(
        diagnostic(&document).contains("air.Suite07Test, and 3 more"),
        "{}",
        diagnostic(&document)
    );
}

/// A restart of a still-active id keeps its place with the newer record; a restart after a finish is a new entry
/// at the end. The list reaches an agent verbatim.
#[test]
fn the_active_set_keeps_insertion_order() {
    let state = progress_state(&[
        started("a", "a1", None, "t1"),
        started("b", "b1", None, "t2"),
        started("a", "a2", None, "t3"),
    ]);
    let shown: Vec<(&str, &str)> = state
        .active_tests
        .iter()
        .map(|test| (test.id.as_str(), test.display_name.as_str()))
        .collect();
    assert_eq!(shown, [("a", "a2"), ("b", "b1")]);

    let restarted = progress_state(&[
        started("a", "a1", None, "t1"),
        started("b", "b1", None, "t2"),
        finished("a", "a1", "SUCCESSFUL", None, None),
        started("a", "a3", None, "t4"),
    ]);
    let ids: Vec<&str> = restarted.active_tests.iter().map(|test| test.id.as_str()).collect();
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
            container_failed("c2"),
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
        available(PASSING_XML),
        Some(summary(1, 0, 0, 0, 0)),
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

/// A count attribute that is not an integer is not trusted: it reads as nothing, which then disagrees with the
/// daemon's own count, and the report never contradicts itself about the totals.
#[test]
fn a_count_that_is_not_an_integer_is_not_trusted() {
    let xml = concat!(
        r#"<testsuites><testsuite name="S" tests="1e20" failures="1.9" errors="0" skipped="0" time="0">"#,
        r#"<testcase classname="com.intellij.air.S" name="t" time="0"/>"#,
        r#"</testsuite></testsuites>"#,
    );
    let document = built(input(available(xml), Some(summary(1, 0, 0, 0, 0)), vec![]));
    assert_eq!((document.xml.tests, document.xml.failures), (0, 0));
    assert_eq!(document.suites[0].tests, document.xml.tests);
    assert_eq!(document.status, Status::InfrastructureError);
    assert!(
        diagnostic(&document).contains("summary/XML test-count mismatch: summary=1, XML=0"),
        "{}",
        diagnostic(&document)
    );
}

/// `+02:00` is *before* the same wall time at `Z`, and comparing the two as strings reported a negative wall
/// clock as zero.
#[test]
fn durations_are_measured_by_instant_and_not_by_text() {
    let mut from = input(available(PASSING_XML), Some(summary(1, 0, 0, 0, 0)), vec![]);
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

// --- refusals -------------------------------------------------------------------------------------------------

#[test]
fn an_inconsistent_retrieval_is_refused_rather_than_reported() {
    let mut source = available(PASSING_XML);
    source.xml = None;
    let refusal = build(input(source, Some(summary(1, 0, 0, 0, 0)), vec![])).unwrap_err();
    assert_eq!(refusal.code, "report_source_inconsistent");
}

// --- persistence ----------------------------------------------------------------------------------------------

fn settings(root: &Path) -> Config {
    let home = root.join("home");
    let runtime = root.join("runtime");
    let environment = Environment::from_pairs([
        ("HOME", home.to_str().unwrap()),
        ("TART_BIN", "tart"),
        ("AIR_VM_RUNTIME_ROOT", runtime.to_str().unwrap()),
    ]);
    Config::load(Selection::DEFAULT, &environment, Path::new("/repo/scripts"))
        .unwrap_or_else(|refusal| panic!("the environment was refused: {refusal:?}"))
}

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
    let document = built(input(available(FAILING_XML), Some(summary(1, 1, 0, 0, 0)), vec![]));

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

/// The traces pair is carried verbatim and decides nothing (scenario-trace.spec: a trace never decides a verdict).
#[test]
fn traces_are_carried_and_never_decide_the_verdict() {
    let passing = input(available(PASSING_XML), Some(summary(1, 0, 0, 0, 0)), vec![]);
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

    let archive = TraceArchive {
        path: "/host/runs/r/traces/iteration-1/001.zip".to_owned(),
        bytes: 2048,
        bundles: vec![TraceBundle {
            id: "a".to_owned(),
            entry: "r/T/s/".to_owned(),
            test_class: "T".to_owned(),
            scenario: "s".to_owned(),
            flow: None,
            status: "failed".to_owned(),
            has_video: false,
        }],
    };
    let mut failing = input(available(FAILING_XML), Some(summary(1, 1, 0, 0, 0)), vec![]);
    failing.traces = vec![archive.clone()];
    let document = built(failing);
    assert_eq!(document.status, Status::Failed);
    assert_eq!(document.verdict_diagnostic, None);
    assert_eq!(document.trace_archives, [archive]);
}

/// The tree pair goes onto the document verbatim and decides nothing: a green run on a dirty tree stays green, and
/// so does one whose tree could not be read.
#[test]
fn the_tree_is_carried_and_never_decides_the_verdict() {
    let passing = input(available(PASSING_XML), Some(summary(1, 0, 0, 0, 0)), vec![]);
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
    let document = built(input(missing(), None, vec![]));
    let encoded = serde_json::to_string(&document).unwrap();
    for field in ["suites", "failures", "skippedContainers", "activeTests", "unreportedClasses"] {
        assert!(
            encoded.contains(&format!(r#""{field}":[]"#)),
            "{field} is not an empty array: {encoded}"
        );
    }
    assert!(!encoded.contains(r#""evidence""#), "{encoded}");
}

/// The document a real truncation writes: one container failure recorded as a suite-level error, and nothing
/// started. It gets *past* the summary-versus-XML count guard, which is why the ordering behind that guard had to
/// be right rather than merely unreached.
const BEFORE_ALL_FAILED_XML: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="air.SetupBrokenTest" timestamp="2026-08-22T18:00:00Z" tests="1" failures="0" errors="1" skipped="0" time="30">
    <testcase classname="air.SetupBrokenTest" name="air.SetupBrokenTest" time="30">
      <error type="java.util.concurrent.TimeoutException" message="Before all[timeout=30s] failed">timed out</error>
    </testcase>
  </testsuite>
</testsuites>"#;

/// A lane truncated at its first class is a failure, not a lane that was ruled out. On 2026-08-23 a `--lane ui`
/// lost the IDE its suites share when one class failed, and every class after it came back as a skipped container;
/// on the first class both `containerFailures > 0` and `containersSkipped > 0` are true.
#[test]
fn a_lane_truncated_at_its_first_class_is_a_failure() {
    let document = built(input(available(BEFORE_ALL_FAILED_XML), Some(summary(0, 0, 0, 15, 1)), vec![]));
    assert_eq!(document.status, Status::Failed, "{}", diagnostic(&document));
    // The counts that distinguish a truncated lane from a filtered one survive the verdict.
    assert_eq!(
        (document.execution.container_failures, document.execution.containers_skipped),
        (1, 15)
    );
    assert_eq!(document.failures[0].kind, FailureKind::Error);
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
    let reported = XmlCounts {
        tests: 1,
        errors: 1,
        ..XmlCounts::default()
    };
    assert_eq!(decide_status(&[], &execution, &reported), Status::Failed);
}

// The first four integrities are the JUnit reader's own, spelled once.
#[test]
fn the_first_four_integrities_are_the_junit_readers() {
    for status in [
        IntegrityStatus::Complete,
        IntegrityStatus::Empty,
        IntegrityStatus::Truncated,
        IntegrityStatus::Malformed,
    ] {
        assert_eq!(integrity_of(status).as_str(), status.as_str());
    }
}
