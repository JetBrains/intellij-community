use avl_host_testkit::{path_runner, quiet, refusal};
use std::fs;
use std::path::Path;

use avl_affected::air_area;
use avl_base::format::words as paths;
use avl_base::{Backend, Exit, GuestOs};
use avl_host_sys::Ctx;
use bt_core::VIA_MODULE;
use bt_core::lanes::Catalog;
use pretty_assertions::assert_eq;
use serde_json::json;
use tempfile::TempDir;

use super::*;
use crate::lane::testing::settings;

/// The suite catalog of the Air lane table.
fn catalog() -> Catalog<'static> {
    avl_affected::bridge::install_fixture();
    air_area().catalog().expect("the Air lane table names a catalog")
}

/// Moved here with the two helpers from `shard`, where they used to live: one owner for the escaping rule means one
/// test for it too.
#[test]
fn a_class_name_is_embedded_as_an_anchored_literal_and_an_unpatternable_one_is_left_out() {
    assert_eq!(
        class_name_pattern("com.intellij.air.flow.AirFlowUiTest"),
        r"^\Qcom.intellij.air.flow.AirFlowUiTest\E$"
    );
    // A nested class carries `$`, which is a regex metacharacter and a literal inside `\Q…\E`.
    assert_eq!(class_name_pattern("a.B$Nested"), r"^\Qa.B$Nested\E$");
    assert!(is_patternable_class_name("a.B$Nested"));
    // A backslash is the one character that could end the quoted span early and widen the filter. No JVM class
    // name has one, so such a name means a corrupt report.
    assert!(!is_patternable_class_name(r"a.B\Ec"));
    assert!(!is_patternable_class_name(""));
}

// --- the affected-suite selection -----------------------------------------------------------------------------

/// A checkout holding what the join reads and nothing else: the committed suite documents, production files
/// carrying the flow tags, and one `.iml` for the module relation.
struct Checkout {
    repo: TempDir,
}

impl Checkout {
    fn new() -> Self {
        let checkout = Self {
            repo: tempfile::tempdir().unwrap(),
        };
        // These cases are about what the controller does with a resolution, so a tag is the whole answer for every
        // path under `frontend`. One module endpoint is on two of the documents too, because a coarse answer is
        // what the refusal has to state as such. The relations themselves are `bt`'s to cover.
        let document = |suite: &str, lane: &str, class: &str, flow: &str, modules: &str| {
            format!(
                r#"{{"suite": "{suite}", "lane": "{lane}", "testClassName": "{class}", "modules": [{modules}], "implementationFlows": [], "profiles": [{{"name": "{suite}-one", "flow": "{flow}", "steps": []}}]}}"#
            )
        };
        checkout.profile(
            "quick-start",
            &document(
                "quick-start",
                "UI",
                "AirQuickStartGeneratedFlowUiTest",
                "flow-preset",
                r#""intellij.air.session""#,
            ),
        );
        checkout.profile(
            "second-ui",
            &document("second-ui", "UI", "AirSecondGeneratedFlowUiTest", "flow-preset", ""),
        );
        checkout.profile(
            "chat",
            &document(
                "chat",
                "GUI_CHAT",
                "AirChatGeneratedFlowUiTest",
                "flow-chat",
                r#""intellij.air.session""#,
            ),
        );
        let source = "plugins/air/frontend/src";
        // One flow, two UI suites.
        checkout.write(
            &format!("{source}/Preset.kt"),
            "package air\n\n/**\n * @flow flow-preset\n */\nclass Preset\n",
        );
        // One flow reaching a UI suite and a GUI-chat one, which is the two-lane case.
        checkout.write(
            &format!("{source}/Both.kt"),
            "package air\n\n/**\n * @flow flow-preset\n * @flow flow-chat\n */\nclass Both\n",
        );
        // A file the flow model does not name. No `.iml` sits above it, so the module relation cannot answer for
        // it either.
        checkout.write(&format!("{source}/Plain.kt"), "package air\n\nclass Plain\n");
        // A file with no tag in a module two suites of two lanes walk: the two-lane answer the coarse relation alone
        // gives.
        checkout.write("plugins/air/session/intellij.air.session.iml", "<module />");
        checkout.write("plugins/air/session/src/Coarse.kt", "package air\n\nclass Coarse\n");
        checkout
    }

    fn write(&self, relative: &str, content: &str) {
        let path = self.repo.path().join(relative);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, content).unwrap();
    }

    fn profile(&self, suite: &str, content: &str) {
        self.write(&format!("{}/{suite}.json", catalog().flow_profile_dir), content);
    }

    fn path(&self) -> &Path {
        self.repo.path()
    }

    fn settings(&self) -> Config {
        settings(Backend::Docker, GuestOs::Linux, self.path())
    }
}

fn filters_of(selection: &AffectedSelection) -> Vec<&str> {
    selection.selection.test_env["JB_TEST_JUNIT5_FILTERS"].split(';').collect()
}

const PRESET: &str = "plugins/air/frontend/src/Preset.kt";
const BOTH: &str = "plugins/air/frontend/src/Both.kt";
const PLAIN: &str = "plugins/air/frontend/src/Plain.kt";
const COARSE: &str = "plugins/air/session/src/Coarse.kt";

#[test]
fn one_lanes_suites_become_the_lanes_tag_and_one_class_filter_each() {
    let checkout = Checkout::new();

    let resolved = resolve_affected_selection(&checkout.settings(), &paths([PRESET]), None, &quiet()).unwrap();

    // The lane's tag comes first and the class filters after it - the order `shard` uses, and the reason one
    // iteration can carry both. Each pattern accepts any package, because the catalog names a suite by its simple
    // class name while JUnit matches the fully qualified one. An anchored literal of a simple name selected nothing
    // at all, which a live run on a Linux worker reported as "matched no tests in the hot tier" on 2026-08-26.
    assert_eq!(
        filters_of(&resolved),
        [
            "include-tag=air-flow-ui",
            r"include-classname=(^|.*\.)AirQuickStartGeneratedFlowUiTest$",
            r"include-classname=(^|.*\.)AirSecondGeneratedFlowUiTest$",
        ]
    );
    assert_eq!(resolved.lane, "ui");
    assert_eq!(resolved.selection.lane.as_deref(), Some("ui"));
    assert_eq!(resolved.selection.label, "//plugins/air/tests/integration/ui:ui_test");
    assert_eq!(resolved.classes.len(), 2);
}

#[test]
fn a_two_lane_change_is_refused_rather_than_chosen_for() {
    let checkout = Checkout::new();

    let refused = refusal(resolve_affected_selection(&checkout.settings(), &paths([BOTH]), None, &quiet()));

    assert_eq!((refused.code.as_ref(), refused.exit), ("affected_lanes_ambiguous", Exit::USAGE));
    // A GUI-chat suite may drive the physical pointer, so the message has to name both lanes rather than quietly
    // running the one that happens to sort first. The counts are how a caller chooses: this flow has two UI suites
    // and one GUI-chat suite.
    assert!(
        refused.message.contains("3 suite(s): ui 2, gui-chat 1"),
        "the refusal must subtotal each lane, was {:?}",
        refused.message
    );
    // A tag answered here, so the coarse clause of a module-only answer must not appear.
    assert!(
        !refused.message.contains(&format!("{VIA_MODULE} endpoint")),
        "a tagged path is not a coarse answer, was {:?}",
        refused.message
    );
}

/// The refusal states facts, and the caller decides. Naming a lane here would recommend one, and a GUI-chat suite
/// may drive the physical pointer, so the lane belongs to the user who chose the guest.
#[test]
fn the_ambiguity_refusal_recommends_no_lane() {
    let checkout = Checkout::new();

    let refused = refusal(resolve_affected_selection(&checkout.settings(), &paths([BOTH]), None, &quiet()));

    let message = &refused.message;
    for word in ["recommend", "prefer", "dominant", "default", "widest", "instead"] {
        assert!(!message.contains(word), "the refusal ranks the lanes with {word:?}: {message:?}");
    }
    // The subtotals arrive in the declared lane order. An order by size would rank the lanes, which is the same
    // recommendation by another route.
    assert!(message.find("ui 2").unwrap() < message.find("gui-chat 1").unwrap(), "{message:?}");
    // Every reached lane is named, so the caller sees the whole choice it is being asked to make.
    assert!(message.contains("--lane with one of ui, gui-chat"), "{message:?}");
}

/// A caller that lands on the coarse relation gets suites of every lane the module reaches, which is wider than its
/// change. The refusal says so, because the next action is a flow tag rather than a lane.
#[test]
fn a_coarse_two_lane_answer_says_it_came_from_a_module_endpoint() {
    let checkout = Checkout::new();

    let refused = refusal(resolve_affected_selection(&checkout.settings(), &paths([COARSE]), None, &quiet()));

    assert_eq!(
        (refused.code.as_ref(), refused.exit),
        ("affected_lanes_ambiguous", Exit::USAGE),
        "{}",
        refused.message
    );
    assert!(
        refused.message.contains(&format!("every suite came from a {VIA_MODULE} endpoint")),
        "the refusal must state the coarse relation, was {:?}",
        refused.message
    );
    // The subtotals travel with it, so the caller can still choose a lane on this answer.
    assert!(refused.message.contains("2 suite(s): ui 1, gui-chat 1"), "{:?}", refused.message);
}

#[tokio::test]
async fn the_suites_command_subtotals_each_lane_and_carries_the_counts_as_data() {
    let checkout = Checkout::new();

    let outcome = command_suites(&Ctx::background(), &checkout.settings(), &path_runner(), paths([BOTH]), &quiet())
        .await
        .unwrap();

    // The last line is the aggregate: the total, the subtotal of each lane, and the command to run next. A caller
    // reads the sizes off it rather than counting the suite lines above it.
    assert_eq!(
        outcome.text.lines().last(),
        Some("3 suite(s): ui 2, gui-chat 1; next: run --changed <the same paths> (runs ui, gui-chat)")
    );
    // The subtotal is a new key beside the resolution, so a caller reading `suites` or `lanes` sees what it always
    // saw. The key order is the envelope's business, not this command's, so it is compared as a set.
    let mut keys: Vec<&String> = outcome.data.as_object().unwrap().keys().collect();
    keys.sort();
    assert_eq!(keys, ["laneCounts", "lanes", "suites", "unmapped"]);
    assert_eq!(outcome.data["suites"].as_array().unwrap().len(), 3);
    assert_eq!(outcome.data["lanes"], json!(["gui-chat", "ui"]));
    assert_eq!(
        outcome.data["laneCounts"],
        json!([{"lane": "ui", "suites": 2}, {"lane": "gui-chat", "suites": 1}])
    );
}

#[tokio::test]
async fn one_lanes_suites_need_no_lane_in_the_command_to_run_next() {
    let checkout = Checkout::new();

    let outcome = command_suites(&Ctx::background(), &checkout.settings(), &path_runner(), paths([PRESET]), &quiet())
        .await
        .unwrap();

    // One lane settles itself, so the next command carries no `--lane` for the caller to fill in.
    assert_eq!(
        outcome.text.lines().last(),
        Some("2 suite(s): ui 2; next: run --changed <the same paths>")
    );
}

#[tokio::test]
async fn the_suites_command_needs_a_path() {
    let checkout = Checkout::new();

    let refused = command_suites(&Ctx::background(), &checkout.settings(), &path_runner(), Vec::new(), &quiet())
        .await
        .unwrap_err();

    assert_eq!(refused.code, "usage");
    assert!(refused.message.contains("suites <changed-path>..."), "{}", refused.message);
}

#[test]
fn the_requested_lane_settles_a_two_lane_change() {
    let checkout = Checkout::new();

    let resolved = resolve_affected_selection(&checkout.settings(), &paths([BOTH]), Some("gui-chat"), &quiet()).unwrap();

    assert_eq!(resolved.lane, "gui-chat");
    assert_eq!(resolved.classes, ["AirChatGeneratedFlowUiTest"]);
    // The GUI-chat lane's own tag selects it.
    assert_eq!(filters_of(&resolved)[0], "include-tag=air-gui-chat");
}

#[test]
fn a_lane_the_change_does_not_reach_is_refused_with_the_lanes_it_does() {
    let checkout = Checkout::new();

    let refused = refusal(resolve_affected_selection(
        &checkout.settings(),
        &paths([PRESET]),
        Some("ui-real"),
        &quiet(),
    ));

    assert_eq!(refused.code, "no_affected_suite");
    assert!(refused.message.contains("they reach ui"), "{}", refused.message);
}

#[test]
fn a_change_no_suite_covers_is_refused_with_each_paths_reason() {
    let checkout = Checkout::new();

    let refused = refusal(resolve_affected_selection(&checkout.settings(), &paths([PLAIN]), None, &quiet()));

    // An iteration that runs nothing is the false green this controller refuses everywhere else too, so the reason
    // travels in the message rather than the caller having to ask a second command for it.
    assert_eq!(refused.code, "no_affected_suite");
    assert!(
        refused
            .message
            .contains(&format!("{PLAIN} ({})", avl_affected::reason::NO_FLOW_TAG)),
        "{}",
        refused.message
    );
}

#[test]
fn the_resolution_travels_with_the_refusal_as_data() {
    let checkout = Checkout::new();

    let refused = refusal(resolve_affected_selection(&checkout.settings(), &paths([BOTH]), None, &quiet()));

    // A caller reading JSON must not have to parse the prose to learn what was found.
    let details = refused.details().expect("the refusal carries details");
    assert_eq!(details["affected"]["lanes"], json!(["gui-chat", "ui"]));
    assert_eq!(details["affected"]["suites"].as_array().unwrap().len(), 3);
}

// --- the same selection for a flow and a suite ------------------------------------------------------------------

/// A flow id and a suite id end in [`select_suites`] too, so a flow selects exactly what a path tagged with that
/// flow selects: the lane's tag, then one class filter per suite, in the same order.
#[test]
fn a_flow_selects_what_a_path_tagged_with_it_selects() {
    let checkout = Checkout::new();
    let settings = checkout.settings();

    let by_path = resolve_affected_selection(&settings, &paths([PRESET]), None, &quiet()).unwrap();
    let by_flow = resolve_named_suite_selection(&settings, "flow-preset", None, &quiet()).unwrap();

    assert_eq!(filters_of(&by_flow), filters_of(&by_path));
    assert_eq!(by_flow.lane, by_path.lane);
    assert_eq!(by_flow.selection.label, by_path.selection.label);
    for suite in &by_flow.affected.suites {
        assert_eq!(suite.via, ["flow:flow-preset"], "{}", suite.suite);
    }

    let by_suite = resolve_named_suite_selection(&settings, "second-ui", None, &quiet()).unwrap();
    assert_eq!(
        filters_of(&by_suite),
        [
            "include-tag=air-flow-ui",
            r"include-classname=(^|.*\.)AirSecondGeneratedFlowUiTest$"
        ]
    );
}

/// A flow of two lanes gets the one two-lane refusal, worded for the flow, and `--lane` settles it.
#[test]
fn a_flow_of_two_lanes_gets_the_same_refusal_and_lane_settles_it() {
    let checkout = Checkout::new();
    checkout.profile(
        "chat-preset",
        r#"{"suite": "chat-preset", "lane": "GUI_CHAT", "testClassName": "AirChatPresetGeneratedFlowUiTest", "modules": [], "profiles": [{"name": "chat-preset-one", "flow": "flow-preset", "steps": []}]}"#,
    );
    let settings = checkout.settings();

    let refused = refusal(resolve_named_suite_selection(&settings, "flow-preset", None, &quiet()));
    assert_eq!(refused.code, "affected_lanes_ambiguous");
    assert!(
        refused
            .message
            .contains("the scenarios of flow-preset reach 3 suite(s): ui 2, gui-chat 1"),
        "{}",
        refused.message
    );

    let settled = resolve_named_suite_selection(&settings, "flow-preset", Some("gui-chat"), &quiet()).unwrap();
    assert_eq!(settled.lane, "gui-chat");
    assert_eq!(settled.classes, ["AirChatPresetGeneratedFlowUiTest"]);
}

/// A refusal of `bt` keeps its own code through the controller, so a flow no suite covers is `no_affected_suite`
/// on the host and in the VM alike, and an unknown id is `usage` with the nearest ids.
#[test]
fn a_flow_refusal_keeps_bts_own_code() {
    let checkout = Checkout::new();
    checkout.write(&format!("{}/flow-uncovered.txt", catalog().flow_text_dir), "uncovered\n");
    let settings = checkout.settings();

    let uncovered = refusal(resolve_named_suite_selection(&settings, "flow-uncovered", None, &quiet()));
    assert_eq!((uncovered.code.as_ref(), uncovered.exit), ("no_affected_suite", Exit::USAGE));
    assert!(
        uncovered.message.contains(bt_core::REASON_NO_SUITE_TESTS_FLOW),
        "{}",
        uncovered.message
    );

    let unknown = refusal(resolve_named_suite_selection(&settings, "flow-prest", None, &quiet()));
    assert_eq!(unknown.code, "usage");
    assert!(unknown.message.contains("did you mean  flow-preset"), "{}", unknown.message);

    assert!(is_named_suite_selector("flow-preset"));
    assert!(is_named_suite_selector("quick-start"));
    assert!(!is_named_suite_selector("AirQuickStartGeneratedFlowUiTest"));
    assert!(!is_named_suite_selector("Bad-Name"));
}

// --- every reached lane ---------------------------------------------------------------------------------------

/// Without `--lane`, every reached lane is one selection, in the declared lane order, and each is the selection
/// `--lane` would make for it. No backend leaves a lane out.
#[test]
fn every_lane_selects_each_reached_lane_in_lane_order() {
    let checkout = Checkout::new();
    let settings = checkout.settings();
    let reached = resolve_changed_suites(&settings, &paths([BOTH]), &quiet()).unwrap();

    let planned = reached.every_lane(None).unwrap();
    let planned_lanes: Vec<&str> = planned.iter().map(|one| one.lane.as_str()).collect();
    assert_eq!(planned_lanes, ["ui", "gui-chat"]);
    assert_eq!(planned[1].classes, ["AirChatGeneratedFlowUiTest"]);
    let alone = reached.one_lane(Some("gui-chat")).unwrap();
    assert_eq!(filters_of(&planned[1]), filters_of(&alone));

    // `--lane` settles the answer to that one lane.
    let named = reached.every_lane(Some("gui-chat")).unwrap();
    assert_eq!(named.len(), 1);
    assert_eq!(named[0].lane, "gui-chat");

    // An answer with no suite keeps its own refusal.
    let nothing = resolve_changed_suites(&settings, &paths([PLAIN]), &quiet()).unwrap();
    assert_eq!(refusal(nothing.every_lane(None)).code, "no_affected_suite");
}
