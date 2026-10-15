use std::fs;

use avl_base::report::Buffer;
use avl_base::{Environment, Reporter, Scope};
use avl_wire::daemon::{RunEvent, decode_run_event};
use avl_wire::progress::{Event, IterationReported, RunFinished, TraceReady};
use avl_wire::report::Status;
use pretty_assertions::assert_eq;

use super::*;

/// One iteration of the lane daemon: a green scenario and a failed one.
const STREAM: &str = r#"{"event":"runStarted","iterationId":"it-1","daemonBootStamp":"boot","startedAt":"2026-10-15T10:00:00.000Z"}
{"event":"planStarted","timestamp":"2026-10-15T10:00:01.000Z","discoveredExecutions":2,"classNames":["AirComposerGeneratedFlowUiTest"]}
{"event":"testStarted","timestamp":"2026-10-15T10:00:02.000Z","id":"s1","displayName":"send-prompt","className":"AirComposerGeneratedFlowUiTest"}
{"event":"testFinished","timestamp":"2026-10-15T10:00:03.000Z","id":"s1","displayName":"send-prompt","className":"AirComposerGeneratedFlowUiTest","status":"SUCCESSFUL","durationMs":1000}
{"event":"testStarted","timestamp":"2026-10-15T10:00:04.000Z","id":"s2","displayName":"attach-file","className":"AirComposerGeneratedFlowUiTest"}
{"event":"testFinished","timestamp":"2026-10-15T10:00:05.000Z","id":"s2","displayName":"attach-file","className":"AirComposerGeneratedFlowUiTest","status":"FAILED","durationMs":1000,"type":"assertion","message":"the chip is not visible"}
{"event":"summary","iterationId":"it-1","daemonBootStamp":"boot","startedAt":"2026-10-15T10:00:00.000Z","testsStarted":2,"testsFailed":1,"testsSkipped":0,"containersSkipped":0,"containerFailures":0,"hasFailures":true,"ideRunning":true}"#;

fn stream() -> Vec<RunEvent> {
    STREAM
        .lines()
        .map(|line| decode_run_event(line.as_bytes()).expect("a fixture record"))
        .collect()
}

/// Publishes the iteration of `worker`, its report at `report_path`, and the end of the run.
fn publish_run(reporter: &Reporter, worker: &str, report_path: &str) {
    let scope = Scope::worker(worker);
    for event in stream() {
        reporter.publish(Event::Daemon(event), Some(&scope));
    }
    reporter.publish(
        Event::IterationReported(IterationReported {
            iteration_id: "it-1".to_owned(),
            daemon_run_id: "run-1".to_owned(),
            selection: "AirComposerGeneratedFlowUiTest".to_owned(),
            status: Status::Failed,
            report_path: report_path.to_owned(),
            tests_failed: 1,
            tests_started: 2,
        }),
        Some(&scope),
    );
    reporter.publish(
        Event::RunFinished(RunFinished {
            exit_code: 1,
            code: Some("run_failed".to_owned()),
            message: None,
        }),
        None,
    );
}

#[test]
fn a_bazel_test_gets_the_xml_of_the_command_and_each_report_gets_its_own() {
    let directory = tempfile::tempdir().expect("a temporary directory");
    let command_xml = directory.path().join("test.xml");
    let report = directory.path().join("it-1.json");
    let (reporter, _stdout, _stderr) = Reporter::in_memory("vm");
    let environment = Environment::from_pairs([
        ("XML_OUTPUT_FILE", command_xml.to_string_lossy().into_owned()),
        ("TEAMCITY_VERSION", "2026.1".to_owned()),
    ]);
    let messages = Buffer::new();
    {
        let _results = attach_to(&reporter, &environment, SharedWriter::new(messages.clone()));
        publish_run(&reporter, "air-linux-1", &report.to_string_lossy());
    }
    let events = stream();
    let expected = junit::render([events.as_slice()]);
    assert_eq!(fs::read_to_string(&command_xml).expect("the command XML"), expected);
    assert_eq!(
        fs::read_to_string(directory.path().join("it-1.xml")).expect("the XML beside the report"),
        expected
    );
    assert!(messages.is_empty(), "a Bazel test reports through its XML: {}", messages.text());
}

#[test]
fn a_teamcity_build_step_gets_the_messages_of_the_stream_and_of_each_trace() {
    let directory = tempfile::tempdir().expect("a temporary directory");
    let report = directory.path().join("it-1.json");
    let (reporter, _stdout, _stderr) = Reporter::in_memory("vm");
    let environment = Environment::from_pairs([("TEAMCITY_VERSION", "2026.1")]);
    let messages = Buffer::new();
    {
        let _results = attach_to(&reporter, &environment, SharedWriter::new(messages.clone()));
        reporter.publish(
            Event::TraceReady(TraceReady {
                iteration_id: "it-1".to_owned(),
                bundle_id: "b".to_owned(),
                test_class: "AirComposerGeneratedFlowUiTest".to_owned(),
                scenario: "attach-file".to_owned(),
                flow: None,
                status: "failed".to_owned(),
                has_video: true,
                zip: "/runs/run-1/001.zip".to_owned(),
                entry: "run-1/AirComposerGeneratedFlowUiTest/attach-file/".to_owned(),
            }),
            Some(&Scope::worker("air-linux-1")),
        );
        publish_run(&reporter, "air-linux-1", &report.to_string_lossy());
    }
    let text = messages.text();
    let kinds: Vec<&str> = text
        .lines()
        .map(|line| {
            line.strip_prefix("##teamcity[")
                .and_then(|rest| rest.split(' ').next())
                .unwrap_or(line)
        })
        .collect();
    assert_eq!(
        kinds,
        [
            "publishArtifacts",
            "testMetadata",
            "testMetadata",
            "testSuiteStarted",
            "testStarted",
            "testFinished",
            "testStarted",
            "testFailed",
            "testFinished",
            "testSuiteFinished",
        ]
    );
    assert!(text.contains(&format!("name='video' type='video' value='air-traces/air-linux-1/it-1/001.zip!/run-1/AirComposerGeneratedFlowUiTest/attach-file/{VIDEO_FILE}'")), "{text}");
    assert!(!directory.path().join("test.xml").exists());
}

#[test]
fn a_command_outside_bazel_and_teamcity_writes_only_the_xml_beside_each_report() {
    let directory = tempfile::tempdir().expect("a temporary directory");
    let report = directory.path().join("it-1.json");
    let (reporter, stdout, _stderr) = Reporter::in_memory("vm");
    let messages = Buffer::new();
    {
        let _results = attach_to(
            &reporter,
            &Environment::from_pairs::<&str, &str>([]),
            SharedWriter::new(messages.clone()),
        );
        publish_run(&reporter, "air-linux-1", &report.to_string_lossy());
    }
    assert!(directory.path().join("it-1.xml").exists());
    assert!(messages.is_empty());
    assert!(!stdout.text().contains("##teamcity"), "{}", stdout.text());
}

#[test]
fn a_write_that_fails_is_noted_when_the_readers_detach() {
    let directory = tempfile::tempdir().expect("a temporary directory");
    let (reporter, _stdout, stderr) = Reporter::in_memory("vm");
    reporter.set_mode(avl_base::report::Mode::Stream);
    let missing = directory.path().join("absent").join("test.xml");
    let environment = Environment::from_pairs([("XML_OUTPUT_FILE", missing.to_string_lossy().into_owned())]);
    {
        let _results = attach_to(&reporter, &environment, SharedWriter::new(Buffer::new()));
        publish_run(&reporter, "air-linux-1", &directory.path().join("it-1.json").to_string_lossy());
    }
    assert!(stderr.text().contains("cannot write the JUnit XML"), "{}", stderr.text());
}
