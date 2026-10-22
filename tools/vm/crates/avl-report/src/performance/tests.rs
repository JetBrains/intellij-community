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

fn plan() -> Plan {
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
    let files = files(&plan(), &run_stream(), GENERATED);
    assert_eq!(files.len(), 1, "only send-prompt reported metrics");
    assert_eq!(files[0].artifact_path, "air-send-prompt");
    expect_test::expect_file![testdata(FILE_NAME)].assert_eq(&files[0].text);
}

#[test]
fn a_case_with_metrics_and_no_series_writes_no_file() {
    let line = r#"{"event":"testFinished","timestamp":"2026-10-15T10:00:33.345Z","id":"x","displayName":"cold-open","className":"AirSessionColdOpenUiTest","status":"SUCCESSFUL","durationMs":5,"report":{"metrics":{"openMs":12}}}"#;
    let mut events = run_stream();
    events.push(decode_run_event(line.as_bytes()).unwrap());
    let files = files(&plan(), &events, GENERATED);
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
        &metrics(json!({"firstShowMs": 1234.5, "growth.heapMiB": 0.125, "rows": 300, "frames": 2.4, "driftMs": -2.5})),
    );
    let document: Value = serde_json::from_str(&text).unwrap();
    assert_eq!(
        document["metrics"],
        json!([
            {"n": "driftMs", "d": -2, "v": -2},
            {"n": "firstShowMs", "d": 1235, "v": 1235},
            {"n": "frames", "c": 2, "v": 2},
            {"n": "growth.heapMiB.x100", "c": 13, "v": 13},
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
    let record = plan().record();
    assert_eq!(record["event"], PLAN_EVENT);
    assert_eq!(Plan::read(&record), Some(plan()));
    assert_eq!(Plan::read(&json!({"event": "shardStarted", "ideBuild": "1", "series": []})), None);
}
