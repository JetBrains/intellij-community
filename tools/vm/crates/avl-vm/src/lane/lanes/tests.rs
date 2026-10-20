use avl_host_testkit::{quiet, refusal};
use std::fs;
use std::path::Path;

use avl_affected::{air_area, ui_lane_names};
use avl_base::{Backend, Exit, GuestOs};
use bt_core::Lanes;
use pretty_assertions::assert_eq;

use super::*;
use crate::lane::testing::settings;

fn lanes() -> &'static Lanes {
    avl_affected::bridge::install_fixture();
    air_area().lanes()
}

// --- lane selection ---------------------------------------------------------------------------------------------

/// The IDE lanes are what a VM worker may run, and their labels are the IDE targets those lanes select.
#[test]
fn only_the_ide_lanes_are_runnable() {
    avl_affected::bridge::install_fixture();
    assert_eq!(ui_lane_names(), ["ui", "ui-real", "ui-live", "ui-perf", "jcp", "gui-chat"]);
    for lane in ui_lanes() {
        assert!(
            lanes().get(lane.name).is_some(),
            "{} is not a lane bt knows, so its --test_env values cannot be read",
            lane.name
        );
        assert!(!lane.label.is_empty(), "{lane:?}");
    }
    let refused = refusal(lane_selection("fast"));
    assert_eq!(refused.exit, Exit::USAGE);
    assert!(
        refused.message.contains("ui, ui-real, ui-live, ui-perf, jcp, gui-chat"),
        "the refusal does not name the runnable lanes: {}",
        refused.message
    );
}

/// A whole lane has no filter: the lane daemon selects the documents of the lane by its name, and the documents carry
/// no JUnit tag. An explicit-only lane is a lane `--lane` selects like any other.
#[test]
fn a_lane_selects_its_documents_by_its_name_and_has_no_filter() {
    avl_affected::bridge::install_fixture();
    let selection = lane_selection("gui-chat").unwrap();
    assert_eq!(
        selection,
        Selection {
            label: "//plugins/air/tests/integration/gui-chat:gui-chat_lane_test".to_owned(),
            lane: Some("gui-chat".to_owned()),
            selectors: Vec::new(),
            junit5_filters: Vec::new(),
        }
    );
    let live = lane_selection("ui-live").unwrap();
    assert_eq!(live.label, "//plugins/air/tests/integration/ui-live:ui-live_lane_test");
    assert_eq!((live.selectors, live.junit5_filters), (Vec::new(), Vec::new()));
    // The live lane runs only in the lane daemon of a Linux guest, with the Central login piped in, so it declares no
    // host passthrough.
    assert!(
        lanes().get("ui-live").unwrap().extra.is_empty(),
        "the ui-live lane declares a host passthrough again"
    );
}

// --- selector resolution --------------------------------------------------------------------------------------

/// A repository shaped the way resolution requires, and no more: one module with a test source root, its
/// `jps_test`, and one test class. The module is the one of the jcp lane, the one IDE lane whose row still names a
/// JUnit target. When the JVM runner goes (E2), a class of a lane resolves through its suite document alone, and
/// this fixture goes with the JUnit path of the resolution.
fn fixture_repo(repo: &Path) {
    let module = repo.join("plugins/air/tests/integration/jcp");
    fs::create_dir_all(module.join("testSrc/flow")).unwrap();
    fs::write(
        module.join("intellij.air.jcp.iml"),
        r#"<module><component><content url="file://$MODULE_DIR$"><sourceFolder url="file://$MODULE_DIR$/testSrc" isTestSource="true" /></content></component></module>"#,
    )
    .unwrap();
    fs::write(module.join("BUILD.bazel"), "jps_test(\n  name = \"jcp_test\",\n)\n").unwrap();
    fs::write(
        module.join("testSrc/flow/AirFlowSmokeTest.kt"),
        "package com.intellij.air.integration.flow\n\nclass AirFlowSmokeTest\n",
    )
    .unwrap();
}

/// A class name resolves to the lane's own target, and its test filter becomes the selector of the run request.
#[test]
fn a_selector_inside_a_lane_resolves_to_its_filter() {
    let repo = tempfile::tempdir().unwrap();
    fixture_repo(repo.path());
    let settings = settings(Backend::Docker, GuestOs::Linux, repo.path());

    let selection = resolve_selector_selection(&settings, "AirFlowSmokeTest", &quiet()).unwrap();

    // A selector names no lane, and the daemon branches on that: a lane iteration is selected by tag.
    assert_eq!(
        selection,
        Selection {
            label: "//plugins/air/tests/integration/jcp:jcp_test".to_owned(),
            lane: None,
            selectors: vec!["com.intellij.air.integration.flow.AirFlowSmokeTest".to_owned()],
            junit5_filters: Vec::new(),
        }
    );
}

/// A class that a suite document names runs in the lane of the document, though no module of the checkout holds it and
/// no test target runs it. The test label of the lane row is the label, so the lane test of the row runs the class.
#[test]
fn a_class_that_a_suite_document_names_resolves_to_its_lane_with_no_test_target() {
    avl_affected::bridge::install_fixture();
    let repo = tempfile::tempdir().unwrap();
    let catalog = air_area().lanes().catalog().expect("the Air lane table names a catalog");
    let documents = repo.path().join(catalog.flow_profile_dir);
    fs::create_dir_all(&documents).unwrap();
    fs::write(
        documents.join("rename-session.json"),
        r#"{"suite": "rename-session", "lane": "GUI_CHAT", "testClassName": "AirRenameSessionUiTest",
  "labels": [{"name": "testClass", "value": "com.intellij.air.integration.acp.AirRenameSessionUiTest"}], "profiles": []}"#,
    )
    .unwrap();
    let settings = settings(Backend::Docker, GuestOs::Linux, repo.path());

    let selection = resolve_selector_selection(&settings, "AirRenameSessionUiTest", &quiet()).unwrap();

    assert_eq!(
        selection,
        Selection {
            label: ui_lane("gui-chat").unwrap().label.to_owned(),
            lane: None,
            selectors: vec!["com.intellij.air.integration.acp.AirRenameSessionUiTest".to_owned()],
            junit5_filters: Vec::new(),
        }
    );
}

/// Everything else in the repository is host-runnable, and an arbitrary Bazel invocation has no meaning in a guest
/// with no checkout and no Bazel.
#[test]
fn a_target_outside_the_ide_lanes_is_refused() {
    let repo = tempfile::tempdir().unwrap();
    fixture_repo(repo.path());
    let settings = settings(Backend::Docker, GuestOs::Linux, repo.path());
    for selector in [
        "//plugins/air/tests/integration/headless/claude:claude_test",
        // A pattern can hit more than one target, so no single label - and no filter - can describe it.
        "//plugins/air/tests/integration/...",
        "NoSuchTest",
    ] {
        let refused = refusal(resolve_selector_selection(&settings, selector, &quiet()));
        assert_eq!(
            (refused.code.as_ref(), refused.exit),
            ("usage", Exit::USAGE),
            "{selector}: {}",
            refused.message
        );
    }
}

/// The lane's own label resolves without a tree scan at all, which is what keeps a `--lane` start off the ~250 ms
/// the scan costs.
#[test]
fn the_lane_label_itself_is_accepted_as_a_selector() {
    let empty = tempfile::tempdir().unwrap();
    let settings = settings(Backend::Docker, GuestOs::Linux, empty.path());
    let label = ui_lane("ui-real").unwrap().label;

    let selection = resolve_selector_selection(&settings, label, &quiet()).unwrap();

    assert_eq!(selection.label, label);
    assert!(
        selection.selectors.is_empty() && selection.junit5_filters.is_empty(),
        "{selection:?}"
    );
}
