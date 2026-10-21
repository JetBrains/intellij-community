use std::fs;
use std::path::Path;

use avl_wire::program::lane::PerformanceSeries;
use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;

/// A lane document with no agent, and `series` as its performance series when it names any.
fn lane_document(series: Option<Value>) -> Value {
    let mut document = json!({
        "schemaVersion": 2, "lane": "UI_PERF", "launchProperties": [], "requirements": [],
        "agents": [], "registryAgents": [], "disabledPluginIds": [], "cliRuntimes": [], "services": {},
    });
    if let Some(series) = series {
        document["performanceSeries"] = series;
    }
    document
}

fn write_lane(root: &Path, lane: &str, document: &Value) {
    let directory = root.join(avl_wire::program::LANE_DOCUMENT_DIRECTORY);
    fs::create_dir_all(&directory).unwrap();
    fs::write(directory.join(format!("{lane}.json")), document.to_string()).unwrap();
}

#[test]
fn a_lane_that_names_series_plans_them_with_the_build_and_another_lane_plans_nothing() {
    let root = tempfile::tempdir().unwrap();
    let series = json!([{
        "testClassName": "AirManyAgentsPerformanceUiTest", "scenario": "many-agents", "project": "air/many-agents",
        "method": "com.intellij.air.integration.acp.AirManyAgentsPerformanceUiTest#manyAgents", "artifactPath": "air-many-agents",
    }]);
    write_lane(root.path(), "ui-perf", &lane_document(Some(series)));
    write_lane(root.path(), "ui", &lane_document(None));
    let build = Ok("271.SNAPSHOT".to_owned());
    assert_eq!(
        plan_of(root.path(), "ui-perf", &build).unwrap(),
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
    assert_eq!(plan_of(root.path(), "ui", &Err("no build".to_owned())).unwrap(), None);
}

#[test]
fn a_plan_that_cannot_be_made_says_why() {
    let root = tempfile::tempdir().unwrap();
    let series = json!([{"testClassName": "A", "scenario": "s", "project": "p", "method": "m", "artifactPath": "a"}]);
    write_lane(root.path(), "ui-perf", &lane_document(Some(series)));
    assert_eq!(
        plan_of(root.path(), "ui-perf", &Err("cannot read build.txt".to_owned())).unwrap_err(),
        "cannot read build.txt"
    );
    assert_eq!(
        plan_of(root.path(), "gui-chat", &Ok("1".to_owned())).unwrap_err(),
        "the lane documents hold no gui-chat.json"
    );
}
