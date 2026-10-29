use std::fs;

use avl_wire::daemon::decode_run_event;
use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;

const CLASS: &str = "com.intellij.air.integration.acp.AirManyAgentsPerformanceUiTest";

/// The run stream of one passed scenario and one failed scenario whose failure is `message` and `detail`.
fn stream(message: &str, detail: &str) -> Vec<RunEvent> {
    let finished = |scenario: &str, status: &str| {
        json!({"event": "testFinished", "timestamp": "2026-10-21T03:10:00.000Z", "id": scenario, "displayName": scenario,
            "className": CLASS, "status": status, "durationMs": 5, "message": message, "detail": detail})
    };
    [
        json!({"event": "runStarted", "iterationId": "it-boot1-1", "daemonBootStamp": "boot1", "startedAt": "2026-10-21T03:00:00.000Z"}),
        finished("warm-up", "SUCCESSFUL"),
        finished("many-agents", "FAILED"),
    ]
    .iter()
    .map(|line| decode_run_event(line.to_string().as_bytes()).unwrap())
    .collect()
}

// The run result names each failed test with the text of its failure, and the summary of each part by its scenario and
// part. A text keeps at most 4000 bytes, cut at a character boundary.
#[test]
fn the_run_result_names_the_failed_tests_and_the_summaries() {
    let out = tempfile::tempdir().unwrap();
    let artifacts = out.path().join("authored-artifacts");
    let part = artifacts.join("many-agents-performance/many-agents/measure");
    fs::create_dir_all(&part).unwrap();
    fs::write(part.join("summary.txt"), "first show 1234 ms\n").unwrap();
    let long = artifacts.join("many-agents-performance/many-agents/stage");
    fs::create_dir_all(&long).unwrap();
    fs::write(long.join("summary.txt"), "é".repeat(TEXT_CAP)).unwrap();
    let detail = format!("caused by: {}", "ü".repeat(TEXT_CAP));
    let performance = vec![Published {
        file: "/out/performance/air-many-agents/metrics.performance.json".to_owned(),
        artifact_path: "air-many-agents".to_owned(),
    }];
    let result = run_result(
        "ui-perf",
        false,
        &stream("  the tab did not open ", &detail),
        Some(&artifacts),
        performance.clone(),
    );
    assert_eq!(result.failed_tests.len(), 1, "{:?}", result.failed_tests);
    assert_eq!(result.failed_tests[0].test, format!("{CLASS} many-agents"));
    let failure = &result.failed_tests[0].failure;
    assert!(failure.starts_with("the tab did not open \ncaused by: üü"), "{failure}");
    assert_eq!(failure.len(), TEXT_CAP - 1, "the cut keeps whole characters");
    assert_eq!(result.summaries["many-agents/measure"], "first show 1234 ms\n");
    assert_eq!(result.summaries["many-agents/stage"].len(), TEXT_CAP);
    assert_eq!(result.summaries.len(), 2);
    assert_eq!(result.performance, performance);

    let file = path(Some(out.path()), Path::new("/unused"));
    write(&file, &result).unwrap();
    let text = fs::read_to_string(&file).unwrap();
    assert!(text.ends_with("}\n"), "{text}");
    let document: Value = serde_json::from_str(&text).unwrap();
    assert_eq!(
        document.as_object().unwrap().keys().collect::<Vec<_>>(),
        ["failedTests", "green", "lane", "performance", "summaries"]
    );
    assert_eq!(document["lane"], "ui-perf");
    assert_eq!(document["green"], false);
    assert_eq!(document["failedTests"][0]["test"], format!("{CLASS} many-agents"));
    assert_eq!(
        document["performance"],
        json!([{"file": "/out/performance/air-many-agents/metrics.performance.json", "artifactPath": "air-many-agents"}])
    );
}

// A green run has no failed test, and a run without authored artifacts has no summary.
#[test]
fn a_green_run_has_no_failed_test() {
    let events: Vec<RunEvent> = stream("", "").into_iter().take(2).collect();
    let result = run_result("ui-perf", true, &events, None, Vec::new());
    assert_eq!(
        result,
        RunResult {
            lane: "ui-perf".to_owned(),
            green: true,
            failed_tests: Vec::new(),
            summaries: BTreeMap::new(),
            performance: Vec::new(),
            refusal: None,
        }
    );
}

// A run that lane-run refused before its end names the code and the message of the refusal, and no test. The field is
// absent from the result of a run that ended.
#[test]
fn a_refused_run_names_its_refusal() {
    let out = tempfile::tempdir().unwrap();
    let result = refused("ui-perf", "linux_window_manager_missing", "  the display :0 has no window manager ");
    assert!(!result.green);
    assert_eq!(result.failed_tests, []);
    let file = path(Some(out.path()), Path::new("/unused"));
    write(&file, &result).unwrap();
    let document: Value = serde_json::from_str(&fs::read_to_string(&file).unwrap()).unwrap();
    assert_eq!(
        document["refusal"],
        json!({"code": "linux_window_manager_missing", "message": "the display :0 has no window manager"})
    );
    let ended = serde_json::to_value(run_result("ui-perf", true, &[], None, Vec::new())).unwrap();
    assert!(ended.get("refusal").is_none(), "{ended}");
}

// The run result goes to the evidence directory of a Bazel test, else to the run directory.
#[test]
fn the_run_result_goes_to_the_lane_outputs_or_to_the_run_directory() {
    assert_eq!(
        path(Some(Path::new("/out")), Path::new("/tmp/air-lane")),
        Path::new("/out/lane/run-result.json")
    );
    assert_eq!(path(None, Path::new("/tmp/air-lane")), Path::new("/tmp/air-lane/run-result.json"));
}
