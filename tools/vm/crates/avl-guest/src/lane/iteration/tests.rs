use std::fs;
use std::path::Path;

use expect_test::expect;
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
        "labels": [], "modules": [], "implementationFlows": [],
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
    let selectors: Vec<String> = selectors.iter().map(|selector| (*selector).to_owned()).collect();
    select(&SelectionInputs {
        lane,
        lane_documents: root,
        suite_documents: root,
        selectors: &selectors,
    })
}

// The lane document names the lane, and the suite documents of that lane are selected in class order. A selector
// narrows them by the test class, with or without a package and a scenario.
#[test]
fn a_selection_is_the_documents_of_the_lane_narrowed_by_the_selectors() {
    let root = documents();
    let all = selected(root.path(), "ui", &[]).unwrap();
    assert_eq!(all.lane.lane, "UI");
    assert_eq!(
        all.suites.iter().map(|suite| suite.test_class_name.as_str()).collect::<Vec<_>>(),
        ["AirAddToAgentContextGeneratedFlowUiTest", "AirRenameSessionGeneratedFlowUiTest"]
    );
    let one = selected(
        root.path(),
        "ui",
        &["com.intellij.air.integration.ui.AirRenameSessionGeneratedFlowUiTest#scenario 1"],
    )
    .unwrap();
    assert_eq!(
        one.suites.iter().map(|suite| suite.suite.as_str()).collect::<Vec<_>>(),
        ["rename-session"]
    );
    let other_lane = selected(root.path(), "ui", &["AirChatReplyGeneratedFlowUiTest"]).unwrap_err();
    assert!(other_lane.contains("no suite document of the lane UI"), "{other_lane}");
    let unknown = selected(root.path(), "ui-perf", &[]).unwrap_err();
    assert!(unknown.contains("hold no ui-perf.json"), "{unknown}");
}

// Until the program runner lands, a selected document ends as one container failure of the type `not_implemented`,
// which names the suite.
#[test]
fn a_selected_document_fails_as_not_implemented() {
    let root = documents();
    let selection = selected(root.path(), "ui", &["AirRenameSessionGeneratedFlowUiTest"]).unwrap();
    let RunEventKind::ContainerFailed(failed) = not_implemented(&selection.suites[0]) else {
        panic!("not a container failure");
    };
    let mut encoded = serde_json::to_value(RunEventKind::ContainerFailed(failed)).unwrap();
    encoded["timestamp"] = json!("t");
    expect![[r#"{"className":"AirRenameSessionGeneratedFlowUiTest","displayName":"AirRenameSessionGeneratedFlowUiTest","event":"containerFailed","id":"[engine:lane-daemon]/[suite:rename-session]","message":"the lane daemon runs no suite document yet, so the suite 'rename-session' did not run","timestamp":"t","type":"not_implemented"}"#]]
        .assert_eq(&encoded.to_string());
}

// A stream that a client stopped reading takes no record more, and the iteration goes on.
#[test]
fn a_broken_stream_drops_the_later_records() {
    struct Broken;
    impl Write for Broken {
        fn write(&mut self, _: &[u8]) -> std::io::Result<usize> {
            Err(std::io::ErrorKind::BrokenPipe.into())
        }
        fn flush(&mut self) -> std::io::Result<()> {
            Ok(())
        }
    }
    let mut out = Broken;
    let mut stream = Stream::new(&mut out);
    stream.note("first");
    assert!(stream.broken);
    stream.note("second");
    let mut kept = Vec::new();
    let mut stream = Stream::new(&mut kept);
    stream.note("line");
    assert_eq!(String::from_utf8(kept).unwrap(), "{\"event\":\"output\",\"text\":\"line\"}\n");
}
