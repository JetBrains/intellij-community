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
    publish_iteration(reporter, worker, report_path, stream());
}

/// [`publish_run`] with the records `events`.
fn publish_iteration(reporter: &Reporter, worker: &str, report_path: &str, events: Vec<RunEvent>) {
    let scope = Scope::worker(worker);
    for event in events {
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

/// The results of a TeamCity build step while `publish` publishes the run of the report path that it gets: the
/// directory of that report and the service messages.
fn teamcity_build_step(publish: impl FnOnce(&Reporter, &str)) -> (tempfile::TempDir, String) {
    let directory = tempfile::tempdir().expect("a temporary directory");
    let report = directory.path().join("it-1.json");
    let (reporter, _stdout, _stderr) = Reporter::in_memory("vm");
    let environment = Environment::from_pairs([("TEAMCITY_VERSION", "2026.1")]);
    let messages = Buffer::new();
    {
        let _results = attach_to(&reporter, &environment, SharedWriter::new(messages.clone()));
        publish(&reporter, &report.to_string_lossy());
    }
    (directory, messages.text())
}

#[test]
fn a_teamcity_build_step_gets_the_messages_of_the_stream_and_of_each_trace() {
    let (directory, text) = teamcity_build_step(|reporter, report| {
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
        publish_run(reporter, "air-linux-1", report);
    });
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
    assert!(
        !directory.path().join("it-1.performance").exists(),
        "a run with no plan writes no IJ Perf file"
    );
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

/// [`stream`] with the metrics of `send-prompt`.
fn stream_with_metrics() -> Vec<RunEvent> {
    let finished = r#""status":"SUCCESSFUL","durationMs":1000}"#;
    let with_report = STREAM.replacen(
        finished,
        r#""status":"SUCCESSFUL","durationMs":1000,"report":{"metrics":{"replyMs":1840}}}"#,
        1,
    );
    assert_ne!(with_report, STREAM);
    with_report
        .lines()
        .map(|line| decode_run_event(line.as_bytes()).expect("a fixture record"))
        .collect()
}

/// Publishes the IJ Perf plan of `worker` with one series for the scenario `scenario`.
fn publish_plan(reporter: &Reporter, worker: &str, scenario: &str) {
    let plan = Plan {
        ide_build: "271.SNAPSHOT".to_owned(),
        series: vec![avl_wire::program::lane::PerformanceSeries {
            test_class_name: "AirComposerGeneratedFlowUiTest".to_owned(),
            scenario: scenario.to_owned(),
            project: "air/composer".to_owned(),
            method: "com.intellij.air.integration.AirComposerGeneratedFlowUiTest#sendPrompt".to_owned(),
            artifact_path: "air-composer/send".to_owned(),
        }],
    };
    reporter.publish(
        Event::Structured {
            data: plan.record(),
            line: String::new(),
        },
        Some(&Scope::worker(worker)),
    );
}

#[test]
fn a_teamcity_build_step_writes_and_publishes_the_ij_perf_file_of_each_planned_scenario() {
    let (directory, text) = teamcity_build_step(|reporter, report| {
        publish_plan(reporter, "air-linux-1", "send-prompt");
        publish_iteration(reporter, "air-linux-1", report, stream_with_metrics());
    });
    let file = directory
        .path()
        .join("it-1.performance/air-composer/send")
        .join(performance::FILE_NAME);
    let document: serde_json::Value = serde_json::from_str(&fs::read_to_string(&file).expect("the IJ Perf file")).unwrap();
    assert_eq!(document["project"], "air/composer");
    assert_eq!(document["build"], "271.SNAPSHOT");
    assert_eq!(document["metrics"], serde_json::json!([{"n": "replyMs", "d": 1840, "v": 1840}]));
    let published = format!("##teamcity[publishArtifacts '{} => air-composer/send']", file.display());
    assert_eq!(text.lines().filter(|line| *line == published).count(), 1, "{text}");
}

#[test]
fn a_scenario_with_metrics_and_no_series_gets_no_file_and_no_note() {
    let directory = tempfile::tempdir().expect("a temporary directory");
    let (reporter, _stdout, stderr) = Reporter::in_memory("vm");
    reporter.set_mode(avl_base::report::Mode::Stream);
    {
        let _results = attach_to(
            &reporter,
            &Environment::from_pairs::<&str, &str>([]),
            SharedWriter::new(Buffer::new()),
        );
        publish_plan(&reporter, "air-linux-1", "attach-file");
        let report = directory.path().join("it-1.json");
        publish_iteration(&reporter, "air-linux-1", &report.to_string_lossy(), stream_with_metrics());
    }
    assert!(!directory.path().join("it-1.performance").exists());
    assert!(!stderr.text().contains(performance::FILE_NAME), "{}", stderr.text());
}
