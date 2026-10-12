use avl_host_testkit::{quiet, refusal};
use std::fs;
use std::path::Path;

use avl_affected::ui_lane_names;
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
    assert_eq!(ui_lane_names(), ["ui", "ui-real", "ui-live", "gui-chat"]);
    for lane in ui_lanes() {
        assert!(
            lanes().get(lane.name).is_some(),
            "{} is not a lane bt knows, so its --test_env values cannot be read",
            lane.name
        );
        assert!(!lane.label.is_empty() && !lane.junit_tag.is_empty(), "{lane:?}");
    }
    let refused = refusal(lane_selection("fast"));
    assert_eq!(refused.exit, Exit::USAGE);
    assert!(
        refused.message.contains("ui, ui-real, ui-live, gui-chat"),
        "the refusal does not name the runnable lanes: {}",
        refused.message
    );
}

/// A lane iteration is selected by JUnit tag, because the daemon is one warm JVM holding every lane's classpath and
/// a Bazel tag filter cannot reach inside it.
#[test]
fn a_lane_selects_itself_by_junit_tag() {
    avl_affected::bridge::install_fixture();
    let selection = lane_selection("gui-chat").unwrap();
    assert_eq!(
        selection,
        Selection {
            label: "//plugins/air/tests/integration/gui-chat:gui-chat_test".to_owned(),
            lane: Some("gui-chat".to_owned()),
            test_env: BTreeMap::from([("JB_TEST_JUNIT5_FILTERS".to_owned(), "include-tag=air-gui-chat".to_owned())]),
        }
    );
}

/// Bazel's valueless `--test_env=NAME` form means "forward the client's NAME", and a host value names a machine
/// the guest is not. `ui-live` keeps its host passthroughs for `BT --lane ui-live`, so the selection drops every
/// valueless form: none reaches a guest's test environment, nor the daemon's union of them.
#[test]
fn a_valueless_test_env_form_never_reaches_the_selection() {
    let live = &lanes().get("ui-live").unwrap().extra;
    assert!(
        live.iter().any(|extra| extra == "--test_env=CLAUDE_BIN"),
        "the ui-live lane no longer declares a host passthrough, so this test proves nothing: {live:?}"
    );
    for lane in ui_lanes() {
        let test_env = lane_selection(lane.name).unwrap().test_env;
        for extra in &lanes().get(lane.name).unwrap().extra {
            if let Some(name) = extra.strip_prefix("--test_env=").filter(|name| !name.contains('=')) {
                assert!(!test_env.contains_key(name), "the {} lane forwards the client's {name}", lane.name);
            }
        }
        for name in test_env.keys() {
            assert!(!name.ends_with("_BIN"), "{name} reached the guest's test environment from the host");
        }
    }
    for name in crate::lane::env::environment_union().keys() {
        assert!(
            !name.ends_with("_BIN") && !name.ends_with("_API_KEY"),
            "{name} reached the daemon's environment"
        );
    }
}

/// An explicit-only lane is a lane `--lane` selects like any other, by its JUnit tag.
#[test]
fn the_explicit_only_lane_selects_itself_by_junit_tag() {
    avl_affected::bridge::install_fixture();
    let selection = lane_selection("ui-live").unwrap();
    assert_eq!(selection.label, "//plugins/air/tests/integration/ui-live:ui-live_test");
    assert_eq!(
        selection.test_env,
        BTreeMap::from([("JB_TEST_JUNIT5_FILTERS".to_owned(), "include-tag=air-flow-ui-live".to_owned())])
    );
}

// --- selector resolution --------------------------------------------------------------------------------------

/// A repository shaped the way resolution requires, and no more: one module with a test source root, its
/// `jps_test`, and one test class.
fn fixture_repo(repo: &Path) {
    let module = repo.join("plugins/air/tests/integration/ui");
    fs::create_dir_all(module.join("testSrc/flow")).unwrap();
    fs::write(
        module.join("intellij.air.ui.iml"),
        r#"<module><component><content url="file://$MODULE_DIR$"><sourceFolder url="file://$MODULE_DIR$/testSrc" isTestSource="true" /></content></component></module>"#,
    )
    .unwrap();
    fs::write(module.join("BUILD.bazel"), "jps_test(\n  name = \"ui_test\",\n)\n").unwrap();
    fs::write(
        module.join("testSrc/flow/AirFlowSmokeTest.kt"),
        "package com.intellij.air.integration.flow\n\nclass AirFlowSmokeTest\n",
    )
    .unwrap();
}

/// A class name resolves to the lane's own target, and its filter travels the way Bazel would send it.
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
            label: "//plugins/air/tests/integration/ui:ui_test".to_owned(),
            lane: None,
            test_env: BTreeMap::from([(
                "TESTBRIDGE_TEST_ONLY".to_owned(),
                "com.intellij.air.integration.flow.AirFlowSmokeTest".to_owned()
            )]),
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
    assert!(selection.test_env.is_empty(), "{selection:?}");
}
