use avl_wire::daemon::decode_run_event;
use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;
use crate::testing::{run_stream, testdata};

const GENERATED: &str = "Thu, 15 Oct 2026 12:00:00 +0200";

fn series(class: &str, scenario: &str, artifact_path: &str) -> PerformanceSeries {
    PerformanceSeries {
        test_class_name: class.to_owned(),
        scenario: scenario.to_owned(),
        project: format!("air/{scenario}"),
        method: format!("com.intellij.air.integration.{}#{scenario}", simple_class_name(class)),
        artifact_path: artifact_path.to_owned(),
    }
}

fn send_prompt_plan() -> Plan {
    Plan {
        ide_build: "271.SNAPSHOT".to_owned(),
        series: vec![series(
            "com.intellij.air.integration.AirComposerGeneratedFlowUiTest",
            "send-prompt",
            "air-send-prompt",
        )],
    }
}

fn metrics(value: Value) -> BTreeMap<String, serde_json::Number> {
    serde_json::from_value(value).unwrap()
}

#[test]
fn the_recorded_stream_renders_its_golden() {
    let files = files(&send_prompt_plan(), &run_stream(), GENERATED);
    assert_eq!(files.len(), 1, "only send-prompt reported metrics");
    assert_eq!(files[0].artifact_path, "air-send-prompt");
    expect_test::expect_file![testdata(FILE_NAME)].assert_eq(&files[0].text);
}

#[test]
fn a_case_with_metrics_and_no_series_writes_no_file() {
    let line = r#"{"event":"testFinished","timestamp":"2026-10-15T10:00:33.345Z","id":"x","displayName":"cold-open","className":"AirSessionColdOpenUiTest","status":"SUCCESSFUL","durationMs":5,"report":{"metrics":{"openMs":12}}}"#;
    let mut events = run_stream();
    events.push(decode_run_event(line.as_bytes()).unwrap());
    let files = files(&send_prompt_plan(), &events, GENERATED);
    assert_eq!(
        files.iter().map(|file| file.artifact_path.as_str()).collect::<Vec<_>>(),
        ["air-send-prompt"],
        "the cold open reported metrics and has no series"
    );
}

#[test]
fn a_duration_a_growth_and_a_counter_keep_whole_numbers() {
    let text = render(
        &series("AirManyAgentsUiTest", "many-agents", "air-many-agents"),
        "271.1",
        GENERATED,
        &metrics(json!({
            "firstShowMs": 1234.5,
            "growth.heapMiB": 0.125,
            "growth.open.coverRemovedMs": 2.437,
            "rows": 300,
            "frames": 2.4,
            "driftMs": -2.5
        })),
    );
    let document: Value = serde_json::from_str(&text).unwrap();
    assert_eq!(
        document["metrics"],
        json!([
            {"n": "driftMs", "d": -2, "v": -2},
            {"n": "firstShowMs", "d": 1235, "v": 1235},
            {"n": "frames", "c": 2, "v": 2},
            {"n": "growth.heapMiB.x100", "c": 13, "v": 13},
            {"n": "growth.open.coverRemovedMs.x100", "c": 244, "v": 244},
            {"n": "rows", "c": 300, "v": 300},
        ])
    );
    assert_eq!(document["projectURL"], "");
    assert_eq!(document["projectDescription"], "Air performance test: air/many-agents");
    assert_eq!(document["buildDate"], GENERATED);
}

#[test]
fn a_value_out_of_the_range_of_an_int_is_held_to_it() {
    assert_eq!(whole(1e12), i32::MAX);
    assert_eq!(whole(-1e12), i32::MIN);
    assert_eq!(whole(-0.4), 0);
    assert_eq!(whole(2.5), 3);
}

#[test]
fn the_build_drops_the_product_code_and_keeps_every_other_form() {
    assert_eq!(build_without_product_code("IU-271.1234\n"), "271.1234");
    assert_eq!(build_without_product_code("271.SNAPSHOT"), "271.SNAPSHOT");
    assert_eq!(build_without_product_code("rider-2026.3"), "rider-2026.3");
}

#[test]
fn the_time_is_rfc_1123_with_gmt_for_a_zero_offset() {
    let time = |text: &str| text.parse::<jiff::Zoned>().unwrap();
    assert_eq!(
        rfc_1123(&time("2026-10-05T15:49:56+02:00[+02:00]")),
        "Mon, 5 Oct 2026 15:49:56 +0200"
    );
    assert_eq!(rfc_1123(&time("2026-10-15T13:49:56+00:00[UTC]")), "Thu, 15 Oct 2026 13:49:56 GMT");
}

#[test]
fn a_plan_reads_back_from_its_record_and_another_record_is_none() {
    let record = send_prompt_plan().record();
    assert_eq!(record["event"], PLAN_EVENT);
    assert_eq!(Plan::read(&record), Some(send_prompt_plan()));
    assert_eq!(Plan::read(&json!({"event": "shardStarted", "ideBuild": "1", "series": []})), None);
}

/// The lane documents with `lane.json` for each of `lanes`, a lane document with no agent and with `series` as its
/// performance series when it names any.
fn lane_documents(lanes: &[(&str, Option<Value>)]) -> BTreeMap<String, Vec<u8>> {
    lanes
        .iter()
        .map(|(lane, series)| {
            let mut document = json!({
                "schemaVersion": 2, "lane": "UI_PERF", "launchProperties": [], "requirements": [],
                "agents": [], "registryAgents": [], "disabledPluginIds": [], "cliRuntimes": [], "services": {},
            });
            if let Some(series) = series {
                document["performanceSeries"] = series.clone();
            }
            (format!("{lane}.json"), document.to_string().into_bytes())
        })
        .collect()
}

#[test]
fn a_lane_that_names_series_plans_them_with_the_build_and_another_lane_plans_nothing() {
    let series = json!([{
        "testClassName": "AirManyAgentsPerformanceUiTest", "scenario": "many-agents", "project": "air/many-agents",
        "method": "com.intellij.air.integration.acp.AirManyAgentsPerformanceUiTest#manyAgents", "artifactPath": "air-many-agents",
    }]);
    let documents = lane_documents(&[("ui-perf", Some(series)), ("ui", None)]);
    assert_eq!(
        plan(&documents, "ui-perf", Ok("271.SNAPSHOT".to_owned())).unwrap(),
        Some(Plan {
            ide_build: "271.SNAPSHOT".to_owned(),
            series: vec![PerformanceSeries {
                test_class_name: "AirManyAgentsPerformanceUiTest".to_owned(),
                scenario: "many-agents".to_owned(),
                project: "air/many-agents".to_owned(),
                method: "com.intellij.air.integration.acp.AirManyAgentsPerformanceUiTest#manyAgents".to_owned(),
                artifact_path: "air-many-agents".to_owned(),
            }],
        })
    );
    assert_eq!(plan(&documents, "ui", Err("no build".to_owned())).unwrap(), None);
}

#[test]
fn a_plan_that_cannot_be_made_says_why() {
    let series = json!([{"testClassName": "A", "scenario": "s", "project": "p", "method": "m", "artifactPath": "a"}]);
    let documents = lane_documents(&[("ui-perf", Some(series))]);
    assert_eq!(
        plan(&documents, "ui-perf", Err("cannot read build.txt".to_owned())).unwrap_err(),
        "cannot read build.txt"
    );
    assert_eq!(
        plan(&documents, "gui-chat", Ok("1".to_owned())).unwrap_err(),
        "the lane documents hold no gui-chat.json"
    );
}

#[test]
fn the_ide_build_of_a_build_file_drops_the_product_code_and_names_a_read_failure_and_an_empty_file() {
    let path = Path::new("/dist/build.txt");
    assert_eq!(ide_build(path, Ok("IU-271.1234\n".to_owned())), Ok("271.1234".to_owned()));
    assert_eq!(
        ide_build(path, Err(io::Error::new(io::ErrorKind::NotFound, "gone"))),
        Err("cannot read /dist/build.txt: gone".to_owned())
    );
    assert_eq!(ide_build(path, Ok(" \n".to_owned())), Err("/dist/build.txt is empty".to_owned()));
}

#[test]
fn a_file_lies_below_its_artifact_path() {
    let file = File {
        artifact_path: "air-long-session/M".to_owned(),
        text: String::new(),
    };
    assert_eq!(
        file.path_below(Path::new("/out/performance")),
        Path::new("/out/performance/air-long-session/M/metrics.performance.json")
    );
}
