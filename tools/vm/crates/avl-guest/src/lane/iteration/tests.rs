use std::fs;
use std::path::Path;

use pretty_assertions::assert_eq;
use serde_json::json;
use tempfile::TempDir;

use avl_wire::daemon::LaneClass;

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
        "schemaVersion": 2, "suite": name, "lane": lane, "testClassName": class, "restartsIde": false,
        "labels": [{"name": "testClass", "value": format!("com.intellij.air.integration.flow.generated.ui.{class}")}],
        "modules": [], "implementationFlows": [],
        "vocabulary": {"runnerAnswers": [], "actions": {}, "assertions": {}},
        "profiles": profiles,
    })
    .to_string()
}

/// The flow vocabulary file of [`documents`], and its digest.
const VOCABULARY: &str = "{\"actions\":{\n}}\n";
const VOCABULARY_DIGEST: &str = "9a94d68734a668df684ca70106e71aed9da1748f2bacabd12481cd152595f675";

/// A resource root with the committed lane document of the lane `UI`, a flow vocabulary file, and three suite
/// documents: two of `UI`, one of `GUI_CHAT`.
fn documents() -> TempDir {
    let root = TempDir::new().unwrap();
    let lanes = root.path().join(avl_wire::program::LANE_DOCUMENT_DIRECTORY);
    let suites = root.path().join(avl_wire::program::SUITE_DOCUMENT_DIRECTORY);
    fs::create_dir_all(&lanes).unwrap();
    fs::create_dir_all(&suites).unwrap();
    let lane = avl_testkit::crate_path!("testdata/lane-context/ui.json");
    fs::copy(&lane, lanes.join("ui.json")).unwrap();
    fs::write(root.path().join(avl_wire::program::FLOW_VOCABULARY_FILE), VOCABULARY).unwrap();
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
    planned(root, lane, selectors, filters, &[])
}

fn planned(root: &Path, lane: &str, selectors: &[&str], filters: &[&str], lane_classes: &[LaneClass]) -> Result<Selection, String> {
    let owned = |texts: &[&str]| texts.iter().map(|text| (*text).to_owned()).collect::<Vec<String>>();
    select(&SelectionInputs {
        lane,
        lane_documents: root,
        suite_documents: root,
        selectors: &owned(selectors),
        junit5_filters: &owned(filters),
        lane_classes,
        split: RunnerSplit::WHOLE,
    })
}

fn lane_class(suite: &str, test_class_name: &str) -> LaneClass {
    LaneClass {
        suite: suite.to_owned(),
        test_class_name: test_class_name.to_owned(),
    }
}

// The bridge of the lane must serve the vocabulary of the documents, so the selection carries the digest of the file. A
// root without the file is refused, so a stale documents jar fails before the IDE launches.
#[test]
fn the_selection_carries_the_digest_of_the_flow_vocabulary_file_and_a_root_without_it_is_refused() {
    let root = documents();
    assert_eq!(selected(root.path(), "ui", &[]).unwrap().vocabulary_digest, VOCABULARY_DIGEST);

    fs::remove_file(root.path().join(avl_wire::program::FLOW_VOCABULARY_FILE)).unwrap();
    assert_eq!(
        selected(root.path(), "ui", &[]).unwrap_err(),
        format!("{} holds no flow-vocabulary.json", root.path().display())
    );
}

// The plan names every suite of the lane that the controller names. A suite with no suite document is planned as an
// undocumented suite, and the selectors and the class filters narrow it as they narrow a document.
#[test]
fn a_lane_class_without_a_suite_document_is_planned_and_narrowed_like_a_document() {
    let root = documents();
    let lane_classes = [
        lane_class("add-to-agent-context", "AirAddToAgentContextGeneratedFlowUiTest"),
        lane_class("genui-host", "AirGenUiHostUiTest"),
    ];
    let undocumented =
        |selection: &Selection| -> Vec<String> { selection.undocumented.iter().map(|class| class.test_class_name.clone()).collect() };

    let all = planned(root.path(), "ui", &[], &[], &lane_classes).unwrap();
    assert_eq!(
        all.class_names(),
        [
            "AirAddToAgentContextGeneratedFlowUiTest",
            "AirGenUiHostUiTest",
            "AirRenameSessionGeneratedFlowUiTest"
        ]
    );
    assert_eq!(undocumented(&all), ["AirGenUiHostUiTest"]);

    let selected = planned(root.path(), "ui", &["com.intellij.air.AirGenUiHostUiTest"], &[], &lane_classes).unwrap();
    assert!(selected.suites.is_empty());
    assert_eq!(selected.class_names(), ["AirGenUiHostUiTest"]);

    let by_class = planned(
        root.path(),
        "ui",
        &[],
        &[r"include-classname=(^|.*\.)AirGenUiHostUiTest$"],
        &lane_classes,
    )
    .unwrap();
    assert!(by_class.suites.is_empty());
    assert_eq!(undocumented(&by_class), ["AirGenUiHostUiTest"]);

    let excluded = planned(
        root.path(),
        "ui",
        &[],
        &[r"exclude-classname=(^|.*\.)AirGenUiHostUiTest$"],
        &lane_classes,
    )
    .unwrap();
    assert!(excluded.undocumented.is_empty());
    assert_eq!(
        classes(&excluded),
        ["AirAddToAgentContextGeneratedFlowUiTest", "AirRenameSessionGeneratedFlowUiTest"]
    );
}

// A suite with no suite document fails as unsupported, and the reason says why the lane daemon cannot run it.
#[test]
fn an_undocumented_suite_fails_as_unsupported_with_its_reason() {
    let undocumented = undocumented_failure(&lane_class("composer", "AirComposerGeneratedFlowUiTest"), "ui").typed();
    assert_eq!(undocumented.kind.as_deref(), Some("lane_unsupported"));
    assert!(
        undocumented.message.as_deref().unwrap().contains("hold no document of it"),
        "{undocumented:?}"
    );
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

// A TeamCity job splits a lane across its runners. Of the selected suites in the order of the lane, each runner takes
// each suite whose position is its index modulo the count. So the runners of one job run each selected suite once, and
// one runner runs the whole lane.
#[test]
fn the_runners_of_a_split_run_each_selected_suite_once_and_one_runner_runs_the_whole_lane() {
    let root = documents();
    let share = |index: u32, count: u32| {
        select(&SelectionInputs {
            lane: "ui",
            lane_documents: root.path(),
            suite_documents: root.path(),
            selectors: &[],
            junit5_filters: &[],
            lane_classes: &[],
            split: RunnerSplit { index, count },
        })
        .unwrap()
    };

    let whole = share(0, 1);
    assert_eq!(
        whole.class_names(),
        ["AirAddToAgentContextGeneratedFlowUiTest", "AirRenameSessionGeneratedFlowUiTest"]
    );
    assert_eq!(whole.class_names(), selected(root.path(), "ui", &[]).unwrap().class_names());
    for count in 1..=3 {
        let mut runs = Vec::new();
        for index in 0..count {
            let part = share(index, count);
            assert_eq!(part.selected, 2, "runner {index} of {count}");
            runs.extend(part.class_names());
        }
        runs.sort();
        assert_eq!(runs, whole.class_names(), "the {count} runner(s) run each suite once");
    }
    assert_eq!(share(0, 2).class_names(), ["AirAddToAgentContextGeneratedFlowUiTest"]);
    assert_eq!(share(1, 2).class_names(), ["AirRenameSessionGeneratedFlowUiTest"]);
    assert_eq!(
        share(2, 3).class_names(),
        Vec::<String>::new(),
        "a runner past the suites runs none"
    );
}
