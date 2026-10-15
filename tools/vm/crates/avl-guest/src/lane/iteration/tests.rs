use std::fs;
use std::path::Path;

use pretty_assertions::assert_eq;
use serde_json::json;
use tempfile::TempDir;

use super::*;

/// A suite document of `lane` with the test class `class` and `scenarios` scenarios that run nothing.
fn suite(name: &str, lane: &str, class: &str, scenarios: usize) -> String {
    let profiles: Vec<_> = (0..scenarios)
        .map(|index| {
            json!({"name": format!("scenario {index}"), "fixture": "fixture", "flow": "flow-one", "flags": {},
                "followsLastUsedPreset": false, "requiresPromptProbe": false, "setups": [], "steps": []})
        })
        .collect();
    json!({
        "suite": name, "lane": lane, "testClassName": class, "restartsIde": false,
        "labels": [{"name": "testClass", "value": format!("com.intellij.air.integration.flow.generated.ui.{class}")}],
        "modules": [], "implementationFlows": [],
        "vocabulary": {"runnerAnswers": [], "actions": {}, "assertions": {}},
        "profiles": profiles,
    })
    .to_string()
}

/// A resource root with the committed lane document of the lane `UI` and three suite documents: two of `UI`, one of
/// `GUI_CHAT`.
fn documents() -> TempDir {
    let root = TempDir::new().unwrap();
    let lanes = root.path().join(avl_wire::program::LANE_DOCUMENT_DIRECTORY);
    let suites = root.path().join(avl_wire::program::SUITE_DOCUMENT_DIRECTORY);
    fs::create_dir_all(&lanes).unwrap();
    fs::create_dir_all(&suites).unwrap();
    let lane = avl_testkit::crate_path!("testdata/lane-context/ui.json");
    fs::copy(&lane, lanes.join("ui.json")).unwrap();
    for (name, lane, class, scenarios) in [
        ("rename-session", "UI", "AirRenameSessionGeneratedFlowUiTest", 2),
        ("add-to-agent-context", "UI", "AirAddToAgentContextGeneratedFlowUiTest", 3),
        ("chat-reply", "GUI_CHAT", "AirChatReplyGeneratedFlowUiTest", 1),
    ] {
        fs::write(suites.join(format!("{name}.json")), suite(name, lane, class, scenarios)).unwrap();
    }
    root
}

fn selected(root: &Path, lane: &str, selectors: &[&str]) -> Result<Selection, String> {
    filtered(root, lane, selectors, &[])
}

fn filtered(root: &Path, lane: &str, selectors: &[&str], filters: &[&str]) -> Result<Selection, String> {
    let owned = |texts: &[&str]| texts.iter().map(|text| (*text).to_owned()).collect::<Vec<String>>();
    select(&SelectionInputs {
        lane,
        lane_documents: root,
        suite_documents: root,
        selectors: &owned(selectors),
        junit5_filters: &owned(filters),
    })
}

fn classes(selection: &Selection) -> Vec<&str> {
    selection
        .suites
        .iter()
        .map(|suite| suite.program.test_class_name.as_str())
        .collect()
}

// The class filter of a suite keeps the document whose class it matches as a whole, in any package. An exclude
// filter and a package filter narrow too. A tag filter and a filter that keeps nothing are refusals.
#[test]
fn the_junit5_filters_narrow_the_documents_by_their_class() {
    let root = documents();
    let suite = filtered(
        root.path(),
        "ui",
        &[],
        &[r"include-classname=(^|.*\.)AirRenameSessionGeneratedFlowUiTest$"],
    )
    .unwrap();
    assert_eq!(classes(&suite), ["AirRenameSessionGeneratedFlowUiTest"]);
    let shard = filtered(root.path(), "ui", &[], &["include-classname=.*(AirRename|AirAddToAgent).*"]).unwrap();
    assert_eq!(classes(&shard).len(), 2);
    let excluded = filtered(root.path(), "ui", &[], &["exclude-classname=.*AirRename.*"]).unwrap();
    assert_eq!(classes(&excluded), ["AirAddToAgentContextGeneratedFlowUiTest"]);
    let package = filtered(root.path(), "ui", &[], &["include-package=com.intellij.air.integration.flow"]).unwrap();
    assert_eq!(classes(&package).len(), 2);
    let other = filtered(root.path(), "ui", &[], &["include-package=com.intellij.air.integration.flo"]).unwrap_err();
    assert!(other.contains("select no suite document of the lane UI"), "{other}");
    let tag = filtered(root.path(), "ui", &[], &["include-tag=air-flow-ui"]).unwrap_err();
    assert!(tag.contains("reads no JUnit tag"), "{tag}");
    let simple = filtered(root.path(), "ui", &[], &["include-classname=AirRenameSessionGeneratedFlowUiTest"]).unwrap_err();
    assert!(simple.contains("select no suite document"), "{simple}");
}

// The lane document names the lane, and the suite documents of that lane are selected in class order. A selector
// narrows them by the test class, with or without a package, and a `#<scenario>` part keeps that scenario only.
#[test]
fn a_selection_is_the_documents_of_the_lane_narrowed_by_the_selectors() {
    let root = documents();
    let all = selected(root.path(), "ui", &[]).unwrap();
    assert_eq!(all.lane.lane, "UI");
    assert_eq!(
        all.suites
            .iter()
            .map(|suite| suite.program.test_class_name.as_str())
            .collect::<Vec<_>>(),
        ["AirAddToAgentContextGeneratedFlowUiTest", "AirRenameSessionGeneratedFlowUiTest"]
    );
    let one = selected(
        root.path(),
        "ui",
        &["com.intellij.air.integration.ui.AirRenameSessionGeneratedFlowUiTest#scenario 1"],
    )
    .unwrap();
    assert_eq!(
        one.suites.iter().map(|suite| suite.program.suite.as_str()).collect::<Vec<_>>(),
        ["rename-session"]
    );
    assert_eq!(
        one.suites[0]
            .program
            .profiles
            .iter()
            .map(|profile| profile.name.as_str())
            .collect::<Vec<_>>(),
        ["scenario 1"]
    );
    assert_eq!(one.suites[0].raw_profiles.len(), 1);
    assert!(one.suites[0].raw_profiles[0].get().contains("\"scenario 1\""));
    let whole = selected(
        root.path(),
        "ui",
        &[
            "AirRenameSessionGeneratedFlowUiTest#scenario 1",
            "AirRenameSessionGeneratedFlowUiTest",
        ],
    )
    .unwrap();
    assert_eq!(whole.suites[0].program.profiles.len(), 2);
    let other_lane = selected(root.path(), "ui", &["AirChatReplyGeneratedFlowUiTest"]).unwrap_err();
    assert!(other_lane.contains("no suite document of the lane UI"), "{other_lane}");
    let unknown = selected(root.path(), "ui-perf", &[]).unwrap_err();
    assert!(unknown.contains("hold no ui-perf.json"), "{unknown}");
}
