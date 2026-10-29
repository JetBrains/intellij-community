use std::time::Duration;

use avl_testkit::ClosedPipe;
use expect_test::expect;
use pretty_assertions::assert_eq;

use super::*;

fn case(name: &str) -> CaseIdentity {
    CaseIdentity {
        suite: "rename-session".to_owned(),
        class_name: "com.intellij.air.integration.flow.generated.ui.AirRenameSessionGeneratedFlowUiTest".to_owned(),
        method_name: Some("generatedFlowScenarios".to_owned()),
        name: name.to_owned(),
    }
}

/// Writes three cases to `stream`: `passes` that passes in 1.5 s, `never` that is skipped, and the failed container
/// `chat-reply`.
fn write_three_cases(stream: &Stream<'_>) {
    stream.started(&case("passes"));
    stream.finished(
        &case("passes"),
        CaseStatus::Successful,
        Duration::from_millis(1_500),
        None,
        ScenarioReport::default(),
    );
    stream.skipped(&case("never"), "scenario 'never' never ran: budget".to_owned());
    stream.container_failed(
        "chat-reply",
        "AirChatReplyGeneratedFlowUiTest",
        "com.example.AirChatReplyGeneratedFlowUiTest",
        TypedFailure {
            kind: Some("lane_document".to_owned()),
            message: Some("no reset".to_owned()),
            detail: None,
        },
    );
}

// A stream that a client stopped reading takes no record more, and the iteration goes on.
#[test]
fn a_broken_stream_drops_the_later_records() {
    let mut out = ClosedPipe;
    let stream = Stream::new(&mut out);
    stream.note("first");
    assert!(stream.lock().broken);
    stream.note("second");
    let mut kept = Vec::new();
    let stream = Stream::new(&mut kept);
    stream.note("line");
    drop(stream);
    assert_eq!(String::from_utf8(kept).unwrap(), "{\"event\":\"output\",\"text\":\"line\"}\n");
}

// The stream keeps its cases for the JUnit XML, also after its reader went away. A prologue failure is a failed case
// named after its suite, and a skip is a skipped case.
#[test]
fn the_cases_of_a_broken_stream_still_render_the_junit_xml() {
    let mut out = ClosedPipe;
    let stream = Stream::new(&mut out);
    stream.note("the reader is gone after this record");
    write_three_cases(&stream);
    let xml = stream.junit_xml();
    assert!(
        xml.contains(r#"<testsuites tests="3" failures="1" errors="0" skipped="1""#),
        "{xml}"
    );
    assert!(
        xml.contains(
            r#"<testcase classname="com.intellij.air.integration.flow.generated.ui.AirRenameSessionGeneratedFlowUiTest" name="passes" time="1.500"/>"#
        ),
        "{xml}"
    );
    assert!(xml.contains(r#"<failure message="no reset" type="lane_document">"#), "{xml}");
}

// A scenario is one case with the class and the factory method of its JUnit node, and an id below the engine of the
// lane daemon. The summary counts a failure, a skip and a prologue failure apart.
#[test]
fn the_records_of_a_case_name_its_class_and_method_and_the_counts_add_up() {
    let mut out = Vec::new();
    let stream = Stream::new(&mut out);
    write_three_cases(&stream);
    assert_eq!(
        stream.counts(),
        Counts {
            started: 1,
            failed: 0,
            skipped: 1,
            container_failures: 1,
        }
    );
    drop(stream);
    let records: Vec<serde_json::Value> = String::from_utf8(out)
        .unwrap()
        .lines()
        .map(|line| {
            let mut record: serde_json::Value = serde_json::from_str(line).unwrap();
            record["timestamp"] = "t".into();
            record
        })
        .collect();
    expect![[r#"
        {"className":"com.intellij.air.integration.flow.generated.ui.AirRenameSessionGeneratedFlowUiTest","displayName":"passes","event":"testStarted","id":"[engine:lane-daemon]/[suite:rename-session]/[scenario:passes]","methodName":"generatedFlowScenarios","timestamp":"t"}
        {"className":"com.intellij.air.integration.flow.generated.ui.AirRenameSessionGeneratedFlowUiTest","displayName":"passes","durationMs":1500,"event":"testFinished","id":"[engine:lane-daemon]/[suite:rename-session]/[scenario:passes]","methodName":"generatedFlowScenarios","status":"SUCCESSFUL","timestamp":"t"}
        {"className":"com.intellij.air.integration.flow.generated.ui.AirRenameSessionGeneratedFlowUiTest","displayName":"never","event":"testSkipped","id":"[engine:lane-daemon]/[suite:rename-session]/[scenario:never]","methodName":"generatedFlowScenarios","reason":"scenario 'never' never ran: budget","timestamp":"t"}
        {"className":"com.example.AirChatReplyGeneratedFlowUiTest","displayName":"AirChatReplyGeneratedFlowUiTest","event":"containerFailed","id":"[engine:lane-daemon]/[suite:chat-reply]","message":"no reset","timestamp":"t","type":"lane_document"}
    "#]]
    .assert_eq(&records.iter().map(|record| format!("{record}\n")).collect::<String>());
}

/// One attempt of the scenario `name` with `status`.
fn attempt(stream: &Stream<'_>, name: &str, status: CaseStatus) {
    stream.started(&case(name));
    let failure = (status != CaseStatus::Successful).then(|| TypedFailure {
        kind: Some("lane_bridge".to_owned()),
        message: Some(format!("{name} timed out")),
        detail: None,
    });
    stream.finished(&case(name), status, Duration::from_secs(2), failure, ScenarioReport::default());
}

// A rerun is a second case of the same id. Its pass takes the failure of the first attempt out of the counts, so the
// summary of a run whose every failure passed on its rerun has no failure. A scenario that fails twice counts once,
// and the JUnit XML keeps every attempt.
#[test]
fn a_later_pass_of_a_failed_scenario_clears_its_failure_in_the_counts() {
    let mut out = Vec::new();
    let stream = Stream::new(&mut out);
    attempt(&stream, "flaky", CaseStatus::Failed);
    attempt(&stream, "broken", CaseStatus::Failed);
    assert_eq!(stream.counts().failed, 2);
    attempt(&stream, "flaky", CaseStatus::Successful);
    attempt(&stream, "broken", CaseStatus::Failed);
    attempt(&stream, "passes", CaseStatus::Successful);
    assert_eq!(
        stream.counts(),
        Counts {
            started: 5,
            failed: 1,
            skipped: 0,
            container_failures: 0,
        }
    );
    let xml = stream.junit_xml();
    assert_eq!(xml.matches(r#"name="flaky""#).count(), 2, "{xml}");
    assert_eq!(xml.matches(r#"name="broken""#).count(), 2, "{xml}");
}
