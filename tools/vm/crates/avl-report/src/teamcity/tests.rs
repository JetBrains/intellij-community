use std::collections::HashMap;

use avl_wire::daemon::{ExecutionIdentity, RunEvent, RunEventKind, TestStarted};
use avl_wire::progress::TraceReady;
use pretty_assertions::assert_eq;

use super::*;
use crate::testing::{run_stream, testdata};

const WORKER: &str = "air-linux-1";

fn trace(scenario: &str, zip: &str, has_video: bool) -> TraceReady {
    TraceReady {
        iteration_id: "it-boot1-1".to_owned(),
        bundle_id: format!("bundle-{scenario}"),
        test_class: "AirComposerGeneratedFlowUiTest".to_owned(),
        scenario: scenario.to_owned(),
        flow: None,
        status: "failed".to_owned(),
        has_video,
        zip: zip.to_owned(),
        entry: format!("run-1/AirComposerGeneratedFlowUiTest/{scenario}/"),
    }
}

fn started(id: &str, class: &str) -> RunEvent {
    RunEvent::synthesized(RunEventKind::TestStarted(TestStarted {
        timestamp: "t".to_owned(),
        execution: ExecutionIdentity {
            id: id.to_owned(),
            display_name: id.to_owned(),
            class_name: Some(class.to_owned()),
            method_name: None,
        },
    }))
}

#[test]
fn the_recorded_stream_renders_its_golden() {
    let mut messages = Messages::default();
    let mut lines = Vec::new();
    for (index, event) in run_stream().iter().enumerate() {
        lines.extend(messages.record(WORKER, event));
        // The trace of the failed scenario arrives while the next record streams.
        if index == 5 {
            lines.extend(messages.trace(
                WORKER,
                &trace("attach-file", "/runs/run-1/traces/it-boot1-1/001.zip", true),
                "video.mp4",
            ));
        }
    }
    lines.extend(messages.finish());
    expect_test::expect_file![testdata("run-stream.teamcity.txt")].assert_eq(&(lines.join("\n") + "\n"));
}

#[test]
fn a_zip_is_published_once_for_its_bundles() {
    let mut messages = Messages::default();
    let zip = "/runs/run-1/traces/it-boot1-1/001.zip";
    let first = messages.trace(WORKER, &trace("a", zip, false), "video.mp4");
    let second = messages.trace(WORKER, &trace("b", zip, false), "video.mp4");
    let published = |lines: &[String]| lines.iter().filter(|line| line.starts_with("##teamcity[publishArtifacts")).count();
    assert_eq!((published(&first), published(&second)), (1, 0));
    assert_eq!(second.len(), 1, "a bundle with no video names only its trace: {second:?}");
}

#[test]
fn each_worker_is_its_own_flow() {
    let mut messages = Messages::default();
    let mut lines = messages.record("w1", &started("a", "First"));
    lines.extend(messages.record("w2", &started("b", "Second")));
    lines.extend(messages.finish());
    let flows: Vec<&str> = lines
        .iter()
        .map(|line| line.rsplit("flowId='").next().unwrap_or_default().trim_end_matches("']"))
        .collect();
    assert_eq!(flows, ["w1", "w1", "w2", "w2", "w1", "w1", "w1", "w2", "w2", "w2"]);
    assert!(lines[4].starts_with("##teamcity[testFailed name='First.a'"), "{}", lines[4]);
}

#[test]
fn the_build_step_is_a_teamcity_agent_outside_a_bazel_test() {
    let environment = |pairs: &[(&str, &str)]| -> HashMap<String, String> {
        pairs
            .iter()
            .map(|(name, value)| ((*name).to_owned(), (*value).to_owned()))
            .collect()
    };
    let step = environment(&[(TEAMCITY_VERSION, "2026.1")]);
    assert!(is_build_step(|name| step.get(name).cloned()));
    let bazel = environment(&[(TEAMCITY_VERSION, "2026.1"), (XML_OUTPUT_FILE, "/x/test.xml")]);
    assert!(!is_build_step(|name| bazel.get(name).cloned()));
    let blank = environment(&[(TEAMCITY_VERSION, "")]);
    assert!(!is_build_step(|name| blank.get(name).cloned()));
}

/// A plan of simple names and records of FQNs name one class, so the end of the iteration fails no class of the plan.
#[test]
fn a_plan_of_simple_names_is_reached_by_records_of_fqns() {
    let mut messages = Messages::default();
    let plan = RunEvent::synthesized(RunEventKind::PlanStarted(PlanStarted {
        timestamp: "t".to_owned(),
        discovered_executions: 1,
        class_names: vec!["AgentProjectWorkflowsUiTest".to_owned(), "Dropped".to_owned()],
        executions: Vec::new(),
    }));
    let mut lines = messages.record(WORKER, &plan);
    lines.extend(messages.record(
        WORKER,
        &started("a", "com.intellij.air.integration.flow.workflows.AgentProjectWorkflowsUiTest"),
    ));
    lines.extend(messages.finish());
    let failed: Vec<&String> = lines.iter().filter(|line| line.starts_with("##teamcity[testFailed")).collect();
    assert_eq!(failed.len(), 2, "the unfinished scenario and the dropped class only: {failed:?}");
    assert!(failed[1].contains("name='Dropped.Dropped'"), "{}", failed[1]);
}

#[test]
fn a_file_is_published_once_into_its_directory() {
    let mut messages = Messages::default();
    let file = "/runs/it-boot1-1.performance/air-long-session/SMALL/metrics.performance.json";
    assert_eq!(
        messages.artifact(file, "air-long-session/SMALL"),
        vec![format!("##teamcity[publishArtifacts '{file} => air-long-session/SMALL']")]
    );
    assert_eq!(messages.artifact(file, "air-long-session/SMALL"), Vec::<String>::new());
}

/// A step after the tests names each trace by the case of its own XML, with the class's package. It adds the bundle, the
/// viewer page, the video and the failure picture of a trace with no flow. Two lanes whose XMLs hold one test name in
/// two packages name each trace by its own lane's case. A trace whose case its XML lacks keeps its own names. A file
/// published at the top of the artifacts is published once, with no target.
fn published_trace<'a>(scenario: &'a str, trace: &'a str, cases: &'a [(String, String)]) -> PublishedTrace<'a> {
    PublishedTrace {
        trace,
        test_class: "AirComposerUiTest",
        scenario,
        video: None,
        still: None,
        link: None,
        cases,
    }
}

#[test]
fn a_published_trace_names_the_case_of_its_own_xml() {
    let lane = |package: &str| {
        vec![
            (format!("{package}.AirComposerUiTest"), "sends a prompt".to_owned()),
            (format!("{package}.AirComposerUiTest"), "attaches a file".to_owned()),
        ]
    };
    let (ui, real) = (lane("com.example.ui"), lane("com.example.real"));
    let published = published_trace;
    let mut messages = Messages::default();
    let failed = "air-traces-failed/ui_lane_test/iter-1/AirComposerUiTest/attaches_a_file";
    let (video, still) = (format!("{failed}/video.mp4"), format!("{failed}/failure.webp"));
    let mut lines = messages.published(&PublishedTrace {
        video: Some(&video),
        still: Some(&still),
        link: Some(
            "https://teamcity.example/repository/download/Air_Ui/7:id/air-traces.zip!/index.html#/trace?zip=ui_lane_test/iter-1/AirComposerUiTest/attaches_a_file.zip",
        ),
        ..published(
            "attaches a file",
            "air-traces.zip!/ui_lane_test/iter-1/AirComposerUiTest/attaches_a_file.zip",
            &ui,
        )
    });
    lines.extend(messages.published(&published(
        "attaches a file",
        "air-traces.zip!/ui-real_lane_test/iter-2/AirComposerUiTest/attaches_a_file.zip",
        &real,
    )));
    lines.extend(messages.published(&published(
        "sends a prompt",
        "air-traces.zip!/ui_lane_test/iter-1/AirComposerUiTest/sends_a_prompt.zip",
        &ui,
    )));
    lines.extend(messages.published(&published(
        "never ran",
        "air-traces.zip!/ui_lane_test/iter-1/AirComposerUiTest/never_ran.zip",
        &ui,
    )));
    lines.extend(messages.artifact_file("/out/air-traces.zip"));
    lines.extend(messages.artifact_file("/out/air-traces.zip"));
    expect_test::expect_file![testdata("published.teamcity.txt")].assert_eq(&(lines.join("\n") + "\n"));
}

/// A warning is a `message` with the status `WARNING`, with its text escaped.
#[test]
fn a_warning_is_a_message_with_the_warning_status() {
    assert_eq!(
        warning("no viewer: 'x' [y] | z"),
        "##teamcity[message text='no viewer: |'x|' |[y|] || z' status='WARNING']"
    );
}
