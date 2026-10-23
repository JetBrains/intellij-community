use std::time::Duration;

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

/// A writer whose reader is gone: each write fails with a broken pipe.
struct Broken;

impl Write for Broken {
    fn write(&mut self, _: &[u8]) -> std::io::Result<usize> {
        Err(std::io::ErrorKind::BrokenPipe.into())
    }

    fn flush(&mut self) -> std::io::Result<()> {
        Ok(())
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
    let mut out = Broken;
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
    let mut out = Broken;
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
