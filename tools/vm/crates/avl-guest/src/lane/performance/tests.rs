use std::fs;
use std::path::Path;

use avl_wire::daemon::decode_run_event;
use avl_wire::program::lane::PerformanceSeries;
use avl_wire::program::{LANE_DOCUMENT_DIRECTORY, parse_suite_program};
use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;

const GENERATED: &str = "Wed, 21 Oct 2026 03:00:00 GMT";
const CLASS: &str = "AirLongSessionPerformanceUiTest";
const SCENARIO: &str = "a session of size M opens, scrolls, continues, gets a long answer and takes a long prompt";
const ARTIFACT_PATH: &str = "air-long-session/M";

fn plan() -> Plan {
    Plan {
        ide_build: "271.SNAPSHOT".to_owned(),
        series: vec![PerformanceSeries {
            test_class_name: CLASS.to_owned(),
            scenario: SCENARIO.to_owned(),
            project: "air/long-session/M".to_owned(),
            method: format!("com.intellij.air.integration.acp.{CLASS}#longSession"),
            artifact_path: ARTIFACT_PATH.to_owned(),
        }],
    }
}

/// The run stream of one scenario of [`CLASS`] that ends with `status` and reports `openMs`.
fn stream(status: &str) -> Vec<RunEvent> {
    let id = "[engine:lane-daemon]/[suite:long-session-performance]/[scenario:size-m]";
    let finished = json!({"event": "testFinished", "timestamp": "2026-10-21T03:10:00.000Z", "id": id, "displayName": SCENARIO,
        "className": format!("com.intellij.air.integration.acp.{CLASS}"), "status": status, "durationMs": 600_000,
        "message": "the part measure-size-m failed", "report": {"metrics": {"openMs": 1234.4}}});
    [
        json!({"event": "runStarted", "iterationId": "it-boot1-1", "daemonBootStamp": "boot1", "startedAt": "2026-10-21T03:00:00.000Z"}),
        json!({"event": "testStarted", "timestamp": "2026-10-21T03:00:01.000Z", "id": id, "displayName": SCENARIO,
            "className": format!("com.intellij.air.integration.acp.{CLASS}")}),
        finished,
    ]
    .iter()
    .map(|line| decode_run_event(line.to_string().as_bytes()).unwrap())
    .collect()
}

/// The suite document of [`CLASS`] with one scenario of the parts `stage-size-m` and `measure-size-m`.
fn suite() -> SuiteProgram {
    let part = |part: &str| {
        json!({"action": "authored-step", "args": {"part": part, "scenario": "size-m", "suite": "long-session-performance",
            "timeoutMs": 180_000}, "uses": {"takes": [], "answers": []}})
    };
    let authored = json!({"side": "ide", "timeoutMs": 180_000, "restarts": 0, "answers": [], "mayAnswer": [], "takes": [],
        "mayTake": [], "params": {"part": {"type": "enum", "values": ["measure-size-m", "stage-size-m"], "optional": false},
            "scenario": {"type": "enum", "values": ["size-m"], "optional": false},
            "suite": {"type": "enum", "values": ["long-session-performance"], "optional": false},
            "timeoutMs": {"type": "int", "optional": false}}});
    let document = json!({
        "schemaVersion": 2, "suite": "long-session-performance", "lane": "UI_PERF", "testClassName": CLASS, "authored": true,
        "labels": [], "restartsIde": true, "modules": [], "implementationFlows": [],
        "vocabulary": {"runnerAnswers": [], "actions": {"authored-step": authored}, "assertions": {}},
        "reset": {"flagKeys": [], "laneFlags": {}, "agentLoginAcpAgentIds": []},
        "fixtures": {"authored": {}},
        "profiles": [{"name": SCENARIO, "key": "size-m", "fixture": "authored", "flags": {}, "flagKeys": [],
            "requiredExecutables": [], "setups": [], "steps": [],
            "program": [part("stage-size-m"), {"action": "restart-ide"}, part("measure-size-m")]}],
    });
    parse_suite_program(document.to_string().as_bytes()).unwrap()
}

fn write_file(path: &Path, text: &str) {
    fs::create_dir_all(path.parent().unwrap()).unwrap();
    fs::write(path, text).unwrap();
}

fn read(path: &Path) -> String {
    fs::read_to_string(path).unwrap_or_else(|error| panic!("{}: {error}", path.display()))
}

// On TeamCity, lane-run writes the file of each planned case below its artifact path and publishes it there.
#[test]
fn the_files_are_written_and_published_on_teamcity() {
    let out = tempfile::tempdir().unwrap();
    let directory = out.path().join(DIR);
    let (published, problems) = write(&plan(), &stream("SUCCESSFUL"), &[], &directory, GENERATED);
    assert_eq!(problems, Vec::<String>::new());
    let file = directory.join(ARTIFACT_PATH).join("metrics.performance.json");
    assert_eq!(
        published,
        [Published {
            file: file.to_string_lossy().into_owned(),
            artifact_path: ARTIFACT_PATH.to_owned(),
        }]
    );
    let document: Value = serde_json::from_str(&read(&file)).unwrap();
    assert_eq!(document["build"], "271.SNAPSHOT");
    assert_eq!(document["metrics"], json!([{"n": "openMs", "d": 1234, "v": 1234}]));
    let mut lines = Vec::new();
    announce(&published, &directory, true, &mut lines);
    assert_eq!(
        String::from_utf8(lines).unwrap(),
        format!("##teamcity[publishArtifacts '{} => {ARTIFACT_PATH}']\n", file.display())
    );
}

// Off TeamCity, lane-run writes the same files and prints no service message.
#[test]
fn off_teamcity_the_files_are_written_and_not_published() {
    let out = tempfile::tempdir().unwrap();
    let directory = out.path().join(DIR);
    let (published, _) = write(&plan(), &stream("SUCCESSFUL"), &[], &directory, GENERATED);
    assert!(directory.join(ARTIFACT_PATH).join("metrics.performance.json").is_file());
    let mut lines = Vec::new();
    announce(&published, &directory, false, &mut lines);
    assert!(lines.is_empty(), "{}", String::from_utf8_lossy(&lines));
}

// Without the outputs directory of a Bazel test, the files go below the run directory.
#[test]
fn without_bazel_outputs_the_files_go_to_the_run_directory() {
    assert_eq!(directory(None, Path::new("/tmp/air-lane")), Path::new("/tmp/air-lane/performance"));
    assert_eq!(
        directory(Some(Path::new("/out")), Path::new("/tmp/air-lane")),
        Path::new("/out/performance")
    );
}

// A failed part reports the metrics of its performance file, so the file of a failed case holds them too.
#[test]
fn a_failed_part_keeps_its_metrics_in_the_file() {
    let out = tempfile::tempdir().unwrap();
    let (published, _) = write(&plan(), &stream("FAILED"), &[], out.path(), GENERATED);
    assert_eq!(published.len(), 1);
    let document: Value = serde_json::from_str(&read(Path::new(&published[0].file))).unwrap();
    assert_eq!(document["metrics"], json!([{"n": "openMs", "d": 1234, "v": 1234}]));
}

// A plan that cannot be made is a note with the reason, and a lane that names no series is no note.
#[test]
fn a_plan_that_cannot_be_made_is_a_note() {
    let root = tempfile::tempdir().unwrap();
    let documents = root.path().join("lane-documents");
    let lane = |series: Value| {
        json!({"schemaVersion": 2, "lane": "UI_PERF", "launchProperties": [], "requirements": [], "agents": [],
            "registryAgents": [], "disabledPluginIds": [], "cliRuntimes": [], "services": {}, "performanceSeries": series})
        .to_string()
    };
    let series = json!([{"testClassName": CLASS, "scenario": SCENARIO, "project": "air/long-session/M",
        "method": "m", "artifactPath": ARTIFACT_PATH}]);
    write_file(&documents.join(LANE_DOCUMENT_DIRECTORY).join("ui-perf.json"), &lane(series));
    write_file(&documents.join(LANE_DOCUMENT_DIRECTORY).join("ui.json"), &lane(json!([])));
    let dist = root.path().join("dist");
    let mut notes = Vec::new();
    assert_eq!(plan_or_note(&documents, &dist, "ui-perf", &mut notes), None);
    assert_eq!(plan_or_note(&documents, &dist, "ui", &mut notes), None);
    let build_txt = dist.join("build.txt");
    assert_eq!(notes.len(), 1, "{notes:?}");
    assert!(
        notes[0].starts_with(&format!(
            "no IJ Perf file for this iteration: cannot read {}: ",
            build_txt.display()
        )),
        "{notes:?}"
    );
    write_file(&build_txt, "IU-271.1234\n");
    let made = plan_or_note(&documents, &dist, "ui-perf", &mut notes).expect("a plan");
    assert_eq!(made.ide_build, "271.1234");
}

// The part files of a case go beside its IJ Perf file and publish into its artifact path. The parts are copied in the
// order of the program, so the later part wins a name that both parts wrote, and a line names the file.
#[test]
fn the_part_files_are_copied_beside_the_file_and_the_later_part_wins() {
    let out = tempfile::tempdir().unwrap();
    let artifacts = out.path().join("iterations/it-boot1-1/authored-artifacts");
    let suite = suite();
    let scenarios = scenario_parts([&suite], &artifacts);
    let part = |part: &str| artifacts.join("long-session-performance/size-m").join(part);
    assert_eq!(
        scenarios,
        [ScenarioParts {
            class_name: CLASS.to_owned(),
            scenario: SCENARIO.to_owned(),
            parts: vec![part("stage-size-m"), part("measure-size-m")],
        }]
    );
    write_file(&part("stage-size-m").join("summary.txt"), "the stage\n");
    write_file(&part("measure-size-m").join("summary.txt"), "the measure\n");
    write_file(&part("measure-size-m").join("air-performance.json"), "{\"openMs\": 1234.4}\n");
    write_file(&part("measure-size-m").join("screenshot.png"), "not a part file");
    let directory = out.path().join(DIR);
    let (published, problems) = write(&plan(), &stream("SUCCESSFUL"), &scenarios, &directory, GENERATED);
    let target = directory.join(ARTIFACT_PATH);
    assert_eq!(
        published.iter().map(|file| file.file.clone()).collect::<Vec<_>>(),
        ["metrics.performance.json", "summary.txt", "air-performance.json"].map(|name| target.join(name).to_string_lossy().into_owned())
    );
    assert!(published.iter().all(|file| file.artifact_path == ARTIFACT_PATH));
    assert_eq!(read(&target.join("summary.txt")), "the measure\n");
    assert!(!target.join("screenshot.png").exists());
    assert_eq!(problems.len(), 1, "{problems:?}");
    assert!(problems[0].starts_with("summary.txt of the part "), "{problems:?}");
}
