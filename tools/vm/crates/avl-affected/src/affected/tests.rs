use std::collections::BTreeMap;

use pretty_assertions::assert_eq;

use bt_core::exit;
use bt_core::fake::{FakeRuntime, REPO_ROOT, area, document_path, flow_catalog_tree, kdoc, lanes, reaching, refusal, suite_document_text};
use bt_core::selector::{Selector, SelectorKind};
use bt_core::suites::{AffectedSuite, UNMAPPED_LINES_PER_REASON, affected_text, classes_of_lane, named_suites, reached_by_module_only};

use super::*;
use crate::bridge::air_area;
use crate::specs::glob_pattern;

/// The authored suites file of the fixture catalog.
fn authored_file() -> &'static str {
    lanes().catalog().expect("the fixture names a catalog").authored_suites_file
}

type Tree = BTreeMap<String, String>;

fn put(tree: &mut Tree, path: &str, text: impl Into<String>) {
    tree.insert(path.to_owned(), text.into());
}

fn affected_of(fake: &FakeRuntime, paths: &[&str]) -> Affected {
    let paths: Vec<String> = paths.iter().map(ToString::to_string).collect();
    affected_suites(fake, area(), &paths).unwrap_or_else(|failure| panic!("the resolution refused: {failure}"))
}

fn classes_of(affected: &Affected) -> String {
    affected
        .suites
        .iter()
        .map(|suite| format!("{}/{}", suite.lane, suite.class))
        .collect::<Vec<_>>()
        .join(",")
}

fn reasons_of(affected: &Affected) -> String {
    affected
        .unmapped
        .iter()
        .map(|unmapped| unmapped.reason.as_str())
        .collect::<Vec<_>>()
        .join(",")
}

fn catalog() -> FakeRuntime {
    FakeRuntime::with_tree(&flow_catalog_tree())
}

const LAUNCH_PRESET_BOTH: &str = "gui-chat/AirManageLaunchPresetGeneratedFlowUiTest,ui/AirManageLaunchPresetQuickStartGeneratedFlowUiTest";
const QUICK_START: &str = "ui/AirManageLaunchPresetQuickStartGeneratedFlowUiTest";
const TERMINAL: &str = "ui-real/AirNewSessionTerminalGeneratedFlowUiTest";

#[test]
fn a_profiles_suite_attribute_reaches_that_suite_exactly() {
    let affected = affected_of(&catalog(), &["plugins/air/frontend/src/Surfaces.kt"]);
    // The declaring file reaches both suites of the flow it declares, the UI one twice over: by its own suite
    // attribute and by the flow relation.
    assert_eq!(classes_of(&affected), LAUNCH_PRESET_BOTH);
    assert_eq!(
        affected.suites[1].via,
        ["flow:flow-manage-launch-preset", "suite:manage-launch-preset-quick-start"]
    );
}

#[test]
fn the_catalogs_lane_vocabulary_becomes_the_controllers_own() {
    let fake = catalog();
    fake.put("plugins/air/frontend/src/Terminal.kt", &kdoc(&["@flow flow-new-session-terminal"]));
    let affected = affected_of(&fake, &["plugins/air/frontend/src/Terminal.kt"]);
    // UI_REAL must arrive as the lane name a caller passes.
    assert_eq!(classes_of(&affected), TERMINAL);
    assert_eq!(affected.lanes, ["ui-real"]);
}

#[test]
fn two_lanes_are_reported_rather_than_chosen_between() {
    let affected = affected_of(&catalog(), &["plugins/air/frontend/src/Rows.kt"]);
    // One flow, two lanes: the answer says so, and the caller that holds a worker decides. A resolution that
    // silently dropped the GUI-chat suite would run it on a Linux worker the next time the flow moved.
    assert_eq!(affected.lanes, ["gui-chat", "ui"]);
    assert_eq!(
        classes_of_lane(&affected, "ui"),
        ["AirManageLaunchPresetQuickStartGeneratedFlowUiTest"]
    );
    assert_eq!(classes_of_lane(&affected, "ui-real"), Vec::<String>::new());
}

#[test]
fn an_implementation_flow_is_reported_as_declaring_no_suite() {
    let fake = catalog();
    fake.put("plugins/air/frontend/src/Launch.kt", &kdoc(&["@flow flow-new-session-launch"]));
    let affected = affected_of(&fake, &["plugins/air/frontend/src/Launch.kt"]);
    assert_eq!(affected.suites, []);
    // The flow id is what separates this case from a typo, so it travels with the reason.
    assert_eq!(
        affected.unmapped,
        [UnmappedPath {
            path: "plugins/air/frontend/src/Launch.kt".to_owned(),
            reason: reason::FLOW_DECLARES_NO_SUITE.to_owned(),
            flows: vec!["flow-new-session-launch".to_owned()],
            module: None,
        }]
    );
}

#[test]
fn a_file_the_flow_model_does_not_name_is_unmapped_with_its_own_reason() {
    let affected = affected_of(
        &catalog(),
        &["plugins/air/frontend/src/Plain.kt", "plugins/air/frontend/src/Absent.kt"],
    );
    // A mistyped path must not withhold the answer for the others, so it is data rather than a refusal.
    assert_eq!(
        reasons_of(&affected),
        format!("{},{}", reason::NO_FLOW_TAG, reason::PATH_UNREADABLE)
    );
}

#[test]
fn only_the_flow_tag_itself_matches_and_never_its_siblings() {
    let fake = catalog();
    // Every other tag in the vocabulary starts with `@flow` too. A pattern matching on a word boundary would read
    // `@flowStep manage-preset-…` as a flow named `manage-preset-…`.
    fake.put(
        "plugins/air/frontend/src/Siblings.kt",
        &kdoc(&[
            "@flowStep flow-manage-launch-preset ext:user -> module:x : click",
            "@flowAfter flow-manage-launch-preset",
            "@flowChoice flow-manage-launch-preset",
            "@flowVariant flow-manage-launch-preset",
            "@flowDo flow-manage-launch-preset",
            "@flowDescription flow-manage-launch-preset",
        ]),
    );
    let affected = affected_of(&fake, &["plugins/air/frontend/src/Siblings.kt"]);
    assert_eq!(affected.suites, []);
    assert_eq!(reasons_of(&affected), reason::NO_FLOW_TAG);
}

#[test]
fn one_suite_is_answered_once_however_many_paths_reach_it() {
    let affected = affected_of(
        &catalog(),
        &["plugins/air/frontend/src/Surfaces.kt", "plugins/air/frontend/src/Rows.kt"],
    );
    assert_eq!(classes_of(&affected), LAUNCH_PRESET_BOTH);
}

#[test]
fn an_absolute_path_is_accepted_beside_a_repo_relative_one() {
    // What an agent has in hand after an edit is an absolute path; what a document names is a relative one. Both
    // name the same file, so the same suites are answered once.
    let absolute = format!("{REPO_ROOT}/plugins/air/frontend/src/Rows.kt");
    let affected = affected_of(&catalog(), &[&absolute, "./plugins/air/frontend/src/Rows.kt"]);
    assert_eq!(classes_of(&affected), LAUNCH_PRESET_BOTH);
    assert_eq!(affected.unmapped, []);
}

#[test]
fn naming_no_path_is_a_usage_refusal() {
    let failure = refusal(affected_suites(&catalog(), area(), &[]));
    assert_eq!(failure.exit, exit::USAGE);
}

#[test]
fn an_unreadable_catalog_refuses_rather_than_answering_nothing() {
    let fake = FakeRuntime::new([("plugins/air/frontend/src/Rows.kt", kdoc(&["@flow flow-x"]).as_str())]);
    // An empty answer and a missing catalog look identical to a caller, and one of them means "run nothing".
    let failure = refusal(affected_suites(&fake, area(), &["plugins/air/frontend/src/Rows.kt".to_owned()]));
    assert_eq!(failure.exit, exit::INFRA);
}

#[test]
fn a_lane_the_controller_does_not_run_is_refused_by_name() {
    let fake = catalog();
    fake.put(
        &document_path("future"),
        &suite_document_text("future", "HEADLESS", "AirFutureGeneratedFlowUiTest", "flow-manage-launch-preset"),
    );
    let failure = refusal(affected_suites(&fake, area(), &["plugins/air/frontend/src/Rows.kt".to_owned()]));
    assert_eq!(failure.exit, exit::INFRA);
    assert!(failure.message.contains("HEADLESS"), "{}", failure.message);
}

#[test]
fn a_malformed_suite_document_is_refused_by_name() {
    let fake = catalog();
    fake.put(&document_path("broken"), "{\"suite\": \"broken\", ");
    let failure = refusal(affected_suites(&fake, area(), &["plugins/air/frontend/src/Rows.kt".to_owned()]));
    assert!(failure.message.contains("broken.json"), "{}", failure.message);
}

/// The base catalog plus the two facts the module relation needs: modules that a route walks, and the `.iml` files
/// that say which module a path belongs to.
fn reach_tree() -> Tree {
    let mut tree = flow_catalog_tree();
    reaching(&mut tree, "manage-launch-preset", &["intellij.air.session.view"], &[]);
    // A module and its test module in one directory, which is the ordinary shape in this tree.
    put(&mut tree, "plugins/air/session/intellij.air.session.iml", "<module />");
    put(&mut tree, "plugins/air/session/intellij.air.session.tests.iml", "<module />");
    put(&mut tree, "plugins/air/session/src/Quiet.kt", "package air\n\nclass Quiet\n");
    put(
        &mut tree,
        "plugins/air/session/testSrc/QuietTest.kt",
        "package air\n\nclass QuietTest\n",
    );
    // A module no route walks.
    put(&mut tree, "plugins/air/notes/intellij.air.notes.iml", "<module />");
    put(&mut tree, "plugins/air/notes/src/Notes.kt", "package air\n\nclass Notes\n");
    tree
}

fn reach() -> FakeRuntime {
    FakeRuntime::with_tree(&reach_tree())
}

#[test]
fn a_path_reaches_the_suites_that_walk_its_own_module() {
    // No tag, and no step declares this file. Its module is `intellij.air.session`, which one suite walks by that
    // exact name and the other only below it, over `intellij.air.session.view`.
    let affected = affected_of(&reach(), &["plugins/air/session/src/Quiet.kt"]);
    assert_eq!(classes_of(&affected), QUICK_START);
    assert_eq!(affected.suites[0].via, ["module:intellij.air.session"]);
}

/// The dotted-prefix rule runs from the endpoint downwards, never upwards: an endpoint deeper than the path's own
/// module says the route walks a part of it, which is not the same as walking the path.
#[test]
fn an_endpoint_below_the_paths_module_does_not_reach_it() {
    let mut tree = reach_tree();
    reaching(&mut tree, "manage-launch-preset-quick-start", &["intellij.air.session.view"], &[]);
    reaching(&mut tree, "manage-launch-preset", &["intellij.air.sessionx"], &[]);
    reaching(&mut tree, "new-session-terminal", &[], &[]);
    let affected = affected_of(&FakeRuntime::with_tree(&tree), &["plugins/air/session/src/Quiet.kt"]);
    assert_eq!(affected.suites, []);
    assert_eq!(reasons_of(&affected), reason::MODULE_NOT_WALKED);
    assert_eq!(affected.unmapped[0].module.as_deref(), Some("intellij.air.session"));
}

/// A directory holds a module and its test module. The shortest name is a dotted prefix of the other, so the
/// covering rule answers the same suites either way, and a report names the module rather than its test module.
#[test]
fn a_test_modules_path_reaches_what_its_module_reaches() {
    let affected = affected_of(&reach(), &["plugins/air/session/testSrc/QuietTest.kt"]);
    assert_eq!(classes_of(&affected), QUICK_START);
}

#[test]
fn a_module_no_route_walks_is_its_own_reason() {
    let affected = affected_of(&reach(), &["plugins/air/notes/src/Notes.kt"]);
    assert_eq!(affected.suites, []);
    assert_eq!(reasons_of(&affected), reason::MODULE_NOT_WALKED);
    assert_eq!(affected.unmapped[0].module.as_deref(), Some("intellij.air.notes"));
}

/// A tag is the exact fact, so a path that carries one is answered by it alone. Consulting the reach as well would
/// widen an exact answer into a coarse one.
#[test]
fn a_tagged_path_is_never_widened_by_the_reach() {
    let mut tree = reach_tree();
    put(
        &mut tree,
        "plugins/air/session/src/Quiet.kt",
        kdoc(&["@flowTest terminal-start [suite: new-session-terminal] [fixture: terminal]"]),
    );
    let affected = affected_of(&FakeRuntime::with_tree(&tree), &["plugins/air/session/src/Quiet.kt"]);
    assert_eq!(classes_of(&affected), TERMINAL);
}

/// A tag naming a flow no suite walks keeps its own reason: the flow model names this path, which is a different
/// fact from the reach.
#[test]
fn a_flow_that_declares_no_suite_is_not_answered_by_the_reach() {
    let mut tree = reach_tree();
    put(
        &mut tree,
        "plugins/air/session/src/Quiet.kt",
        kdoc(&["@flow flow-new-session-launch"]),
    );
    let affected = affected_of(&FakeRuntime::with_tree(&tree), &["plugins/air/session/src/Quiet.kt"]);
    assert_eq!(affected.suites, []);
    assert_eq!(reasons_of(&affected), reason::FLOW_DECLARES_NO_SUITE);
}

/// A path that declares only an implementation flow used to reach nothing, which made `run --changed` on it a false
/// green. The story flow of a suite's routes declares which implementation flows it implements.
#[test]
fn a_path_that_declares_only_an_implementation_flow_reaches_the_story_flows_suite() {
    let mut tree = flow_catalog_tree();
    reaching(
        &mut tree,
        "manage-launch-preset-quick-start",
        &["intellij.air.session"],
        &["flow-new-session-launch"],
    );
    put(
        &mut tree,
        "plugins/air/backend/acp/src/AcpSession.kt",
        kdoc(&["@flow flow-new-session-launch"]),
    );
    let affected = affected_of(&FakeRuntime::with_tree(&tree), &["plugins/air/backend/acp/src/AcpSession.kt"]);
    assert_eq!(classes_of(&affected), QUICK_START);
    assert_eq!(affected.unmapped, []);
    // One `flow:` spelling serves both kinds: the entry names the flow the path declares either way.
    assert_eq!(affected.suites[0].via, ["flow:flow-new-session-launch"]);
}

/// The link is many-to-many: two story flows can declare one implementation flow, and a change in it can break
/// either story.
#[test]
fn one_implementation_flow_reaches_the_suites_of_every_story_flow_that_claims_it() {
    let mut tree = flow_catalog_tree();
    reaching(
        &mut tree,
        "manage-launch-preset-quick-start",
        &["intellij.air.session"],
        &["flow-new-session-launch"],
    );
    reaching(
        &mut tree,
        "new-session-terminal",
        &["intellij.air.terminal"],
        &["flow-new-session-launch"],
    );
    put(
        &mut tree,
        "plugins/air/backend/acp/src/AcpSession.kt",
        kdoc(&["@flow flow-new-session-launch"]),
    );
    let affected = affected_of(&FakeRuntime::with_tree(&tree), &["plugins/air/backend/acp/src/AcpSession.kt"]);
    assert_eq!(classes_of(&affected), format!("{QUICK_START},{TERMINAL}"));
    // Two lanes are a caller decision rather than an answer, and this widening can produce them.
    assert_eq!(affected.lanes, ["ui", "ui-real"]);
}

/// An implementation flow no story flow claims reaches nothing, and that is the correct answer rather than a gap.
/// The reason must still fire, because an iteration that runs no test is not a passing iteration.
#[test]
fn an_implementation_flow_no_story_flow_claims_still_declares_no_suite() {
    let mut tree = flow_catalog_tree();
    reaching(
        &mut tree,
        "manage-launch-preset-quick-start",
        &["intellij.air.session"],
        &["flow-new-session-launch"],
    );
    put(
        &mut tree,
        "plugins/air/backend/acp/src/AcpAgentAutoUpdater.kt",
        kdoc(&["@flow flow-acp-agent-auto-update"]),
    );
    let affected = affected_of(
        &FakeRuntime::with_tree(&tree),
        &["plugins/air/backend/acp/src/AcpAgentAutoUpdater.kt"],
    );
    assert_eq!(affected.suites, []);
    assert_eq!(reasons_of(&affected), reason::FLOW_DECLARES_NO_SUITE);
    assert_eq!(affected.unmapped[0].flows, ["flow-acp-agent-auto-update"]);
}

/// An answer the coarse relation alone produced is wider than the change. The prefix of each `via` entry is the
/// whole fact, so nothing here resolves twice.
#[test]
fn an_answer_the_module_relation_alone_gave_is_reported_as_coarse() {
    let fake = reach();
    let coarse = affected_of(&fake, &["plugins/air/session/src/Quiet.kt"]);
    let tagged = affected_of(&fake, &["plugins/air/frontend/src/Rows.kt"]);
    let mixed = affected_of(&fake, &["plugins/air/session/src/Quiet.kt", "plugins/air/frontend/src/Rows.kt"]);
    assert!(reached_by_module_only(&coarse), "{:?}", coarse.suites);
    assert!(!reached_by_module_only(&tagged), "{:?}", tagged.suites);
    // One tagged path among several makes the answer no longer coarse: a tag reached part of it exactly.
    assert!(!reached_by_module_only(&mixed), "{:?}", mixed.suites);
    // An answer with no suite reached nothing at all, which is a different report.
    assert!(!reached_by_module_only(&Affected::default()));
}

/// The reach is the suite document, so a suite that leaves takes its modules and its implementation flows with it.
#[test]
fn a_departed_suite_takes_its_reach_with_it() {
    let mut tree = reach_tree();
    tree.remove(&document_path("manage-launch-preset-quick-start"));
    let affected = affected_of(&FakeRuntime::with_tree(&tree), &["plugins/air/session/src/Quiet.kt"]);
    assert_eq!(affected.suites, []);
    assert_eq!(reasons_of(&affected), reason::MODULE_NOT_WALKED);
}

/// A suite document that states no module narrows the module relation rather than failing it: the digest gate in
/// `AirArchitectureModelTest` owns catalog freshness, not this join.
#[test]
fn a_suite_that_states_no_module_narrows_rather_than_refuses() {
    let mut tree = reach_tree();
    reaching(&mut tree, "manage-launch-preset-quick-start", &[], &[]);
    let affected = affected_of(&FakeRuntime::with_tree(&tree), &["plugins/air/session/src/Quiet.kt"]);
    assert_eq!(affected.suites, []);
    assert_eq!(reasons_of(&affected), reason::MODULE_NOT_WALKED);
}

/// A directory is the files under it, each joined as if the caller had named it. A file the checkout ignores is not
/// one of them, and a file named beside its directory is answered once.
#[test]
fn a_directory_reaches_what_its_files_reach() {
    let fake = reach();
    fake.put("plugins/air/frontend/src/out/Built.kt", &kdoc(&["@flow flow-new-session-terminal"]));
    fake.ignore("plugins/air/frontend/src/out/Built.kt");
    let affected = affected_of(&fake, &["plugins/air/frontend/src/", "plugins/air/frontend/src/Rows.kt"]);
    // The ignored file must not reach the terminal suite.
    assert_eq!(classes_of(&affected), LAUNCH_PRESET_BOTH);
    // `Plain.kt` is under the directory too, and it keeps its own reason under its own path.
    assert_eq!(
        affected.unmapped,
        [UnmappedPath::new("plugins/air/frontend/src/Plain.kt", reason::NO_FLOW_TAG)]
    );
}

/// An absolute directory is what an agent has in hand, and it is the same directory as its relative spelling.
#[test]
fn an_absolute_directory_is_listed_as_its_relative_spelling_is() {
    let affected = affected_of(&reach(), &[&format!("{REPO_ROOT}/plugins/air/session")]);
    assert_eq!(classes_of(&affected), QUICK_START);
    for unmapped in &affected.unmapped {
        assert!(
            !unmapped.path.starts_with('/'),
            "a listed file must be answered repo-relative, was {}",
            unmapped.path
        );
    }
}

/// A directory that holds no file the checkout tracks is still reported, because an answer that dropped the only
/// path named would read as "reaches nothing" for the wrong reason.
#[test]
fn a_directory_with_no_listed_file_is_reported_by_name() {
    let fake = reach();
    fake.put("plugins/air/frontend/out/Built.kt", &kdoc(&["@flow flow-manage-launch-preset"]));
    fake.ignore("plugins/air/frontend/out/Built.kt");
    let affected = affected_of(&fake, &["plugins/air/frontend/out"]);
    assert_eq!(affected.suites, []);
    assert_eq!(
        affected.unmapped,
        [UnmappedPath::new("plugins/air/frontend/out", reason::EMPTY_DIRECTORY)]
    );
}

/// A directory can leave hundreds of unmapped files. The text states each reason once past a few paths, and the
/// answer itself keeps every path.
#[test]
fn the_text_groups_many_unmapped_paths_by_reason() {
    let mut unmapped: Vec<UnmappedPath> = (0..=UNMAPPED_LINES_PER_REASON)
        .map(|index| UnmappedPath {
            module: Some("intellij.air.notes".to_owned()),
            ..UnmappedPath::new(&format!("plugins/air/notes/src/Note{index}.kt"), reason::MODULE_NOT_WALKED)
        })
        .collect();
    unmapped.push(UnmappedPath::new("plugins/air/Gone.kt", reason::PATH_UNREADABLE));
    let text = affected_text(&Affected {
        unmapped,
        ..Affected::default()
    });
    assert_eq!(
        text,
        "unmapped 11 paths: module_not_walked (intellij.air.notes), the first plugins/air/notes/src/Note0.kt\n\
         unmapped plugins/air/Gone.kt: path_unreadable"
    );
}

/// A suite document states no `@flow` tag, and no route walks its module, so it reached nothing. It is the suite,
/// so it reaches that suite alone.
#[test]
fn a_suite_document_reaches_its_own_suite() {
    let affected = affected_of(&reach(), &[&document_path("new-session-terminal")]);
    assert_eq!(classes_of(&affected), TERMINAL);
    assert_eq!(affected.suites[0].via, ["suite:new-session-terminal"]);
}

/// The source of a suite's test class is the suite's body. It is found by its file name, and it answers alone,
/// whatever else the file declares.
#[test]
fn a_test_class_source_reaches_its_own_suite() {
    let fake = reach();
    let source = "plugins/air/session/testSrc/AirNewSessionTerminalGeneratedFlowUiTest.kt";
    fake.put(source, &kdoc(&["@flow flow-manage-launch-preset"]));
    assert_eq!(classes_of(&affected_of(&fake, &[source])), TERMINAL);
}

/// A document that no longer exists is no suite's file. It stays unreadable like any other path nothing is at.
#[test]
fn a_deleted_suite_document_is_unreadable() {
    let affected = affected_of(&reach(), &[&document_path("departed")]);
    assert_eq!(affected.suites, []);
    assert_eq!(reasons_of(&affected), reason::PATH_UNREADABLE);
}

/// The base catalog plus the layout of the lane harness: the root integration module with a lane rule under it, a
/// shared harness module, a lane leaf, a headless module beside them, and this controller and BT outside the area.
fn harness_tree() -> Tree {
    let mut tree = reach_tree();
    put(
        &mut tree,
        "plugins/air/tests/integration/intellij.air.integrationTests.iml",
        "<module />",
    );
    put(&mut tree, LANE_RULE, "def ui_lane_ide():\n    pass\n");
    // The controller spells real flow ids in its tests. They must not answer.
    put(&mut tree, CONTROLLER_SOURCE, "//! the join of @flow flow-new-session-terminal\n");
    put(&mut tree, BT_SOURCE, "//! the lane table\n");
    put(
        &mut tree,
        "plugins/air/tests/integration/flow/intellij.air.integrationTests.flow.iml",
        "<module />",
    );
    // The harness spells real flow ids in its comments and fixtures. They must not answer.
    put(
        &mut tree,
        "plugins/air/tests/integration/flow/src/flow/AirScenarioRunner.kt",
        kdoc(&["@flow flow-new-session-terminal"]),
    );
    put(
        &mut tree,
        "plugins/air/tests/integration/gui-chat/intellij.air.integrationTests.guiChat.iml",
        "<module />",
    );
    put(&mut tree, GUI_CHAT_GENERATED, "package air\n");
    put(
        &mut tree,
        "plugins/air/tests/integration/gui-chat/test/AirManageLaunchPresetGeneratedFlowUiTest.kt",
        "package air\n",
    );
    put(
        &mut tree,
        "plugins/air/tests/integration/headless/acp/intellij.air.integrationTests.headless.acp.iml",
        "<module />",
    );
    put(
        &mut tree,
        "plugins/air/tests/integration/headless/acp/test/AcpRegistryDownloadTest.kt",
        "package air\n",
    );
    tree
}

const LANE_RULE: &str = "plugins/air/tests/integration/ui_lane_ide.bzl";
const CONTROLLER_SOURCE: &str = "community/tools/vm/crates/avl-affected/src/affected.rs";
const BT_SOURCE: &str = "community/tools/bt/crates/bt-core/src/lanes.rs";
const GUI_CHAT_GENERATED: &str = "plugins/air/tests/integration/gui-chat/testGen/AirGuiChatFlowTests.generated.kt";

#[test]
fn a_harness_path_reaches_every_suite_of_every_lane() {
    let affected = affected_of(
        &FakeRuntime::with_tree(&harness_tree()),
        &["plugins/air/tests/integration/flow/src/flow/AirScenarioRunner.kt"],
    );
    assert_eq!(classes_of(&affected), format!("{LAUNCH_PRESET_BOTH},{TERMINAL}"));
    for suite in &affected.suites {
        // The entry names the harness module and nothing its text matched.
        assert_eq!(suite.via, [format!("{VIA_LANE_WIDE}intellij.air.integrationTests.flow")]);
    }
}

/// A file without a module of its own belongs to the module above it. For a lane `.bzl` rule that is the root
/// integration module, which serves every lane.
#[test]
fn a_lane_rule_is_the_harness_of_every_lane() {
    let affected = affected_of(&FakeRuntime::with_tree(&harness_tree()), &[LANE_RULE]);
    assert_eq!(affected.lanes, ["gui-chat", "ui", "ui-real"]);
    assert_eq!(affected.unmapped, []);
}

/// The controller has no module of the area. The lane table names its directory as a lane-wide path, which serves
/// every lane, and the entry names the directory.
#[test]
fn the_controller_is_the_harness_of_every_lane() {
    let affected = affected_of(&FakeRuntime::with_tree(&harness_tree()), &[CONTROLLER_SOURCE]);
    assert_eq!(affected.lanes, ["gui-chat", "ui", "ui-real"]);
    assert_eq!(affected.unmapped, []);
    for suite in &affected.suites {
        assert_eq!(suite.via, [format!("{VIA_LANE_WIDE}community/tools/vm")]);
    }
}

/// A tool beside the controller is not in the lane-wide path, so it is not the harness.
#[test]
fn a_tool_beside_the_controller_is_not_the_harness() {
    let affected = affected_of(&FakeRuntime::with_tree(&harness_tree()), &[BT_SOURCE]);
    assert_eq!(affected.suites, []);
    assert_eq!(reasons_of(&affected), reason::NO_FLOW_TAG);
}

/// A lane leaf serves its own lane only, and a suite's own test class in it is the more exact fact.
#[test]
fn a_lane_leaf_reaches_its_own_lane_and_a_test_class_in_it_its_own_suite() {
    let fake = FakeRuntime::with_tree(&harness_tree());
    // A second GUI-chat suite, so "its own suite" and "its whole lane" are different answers.
    fake.put(
        &document_path("chat-other"),
        &suite_document_text("chat-other", "GUI_CHAT", "AirChatOtherGeneratedFlowUiTest", "flow-other"),
    );
    let leaf = affected_of(&fake, &[GUI_CHAT_GENERATED]);
    let own = affected_of(
        &fake,
        &["plugins/air/tests/integration/gui-chat/test/AirManageLaunchPresetGeneratedFlowUiTest.kt"],
    );
    assert_eq!(
        classes_of(&leaf),
        "gui-chat/AirChatOtherGeneratedFlowUiTest,gui-chat/AirManageLaunchPresetGeneratedFlowUiTest"
    );
    assert_eq!(classes_of(&own), "gui-chat/AirManageLaunchPresetGeneratedFlowUiTest");
    assert_eq!(own.suites[0].via, ["suite:manage-launch-preset"]);
}

/// The harness list matches a module by its exact name. The dotted-prefix rule would make the root integration
/// module cover every headless module under it, and those run in no UI lane.
#[test]
fn a_headless_module_under_the_harness_is_not_the_harness() {
    let affected = affected_of(
        &FakeRuntime::with_tree(&harness_tree()),
        &["plugins/air/tests/integration/headless/acp/test/AcpRegistryDownloadTest.kt"],
    );
    assert_eq!(affected.suites, []);
    assert_eq!(reasons_of(&affected), reason::MODULE_NOT_WALKED);
}

/// A lane the harness list names must be a lane the controller runs, or the entry reaches nothing and says so
/// nowhere.
#[test]
fn every_lane_the_harness_list_names_is_a_lane_the_controller_runs() {
    crate::bridge::install_fixture();
    let lanes = air_area().lanes();
    let integration = lanes.integration_lane_names();
    for (entry, served) in lanes.lane_wide_modules().chain(lanes.lane_wide_paths()) {
        for lane in served {
            assert!(
                integration.contains(&lane.as_str()),
                "{entry} names lane {lane}; the lanes are {integration:?}"
            );
        }
    }
}

/// The authored suites file in the shape `AirAuthoredSuitesDocumentTest` writes, one suite per `id lane class`.
fn authored_suites_text(triples: &[[&str; 3]]) -> String {
    let entries: Vec<String> = triples
        .iter()
        .map(|[suite, lane, class]| format!(r#"{{ "suite": "{suite}", "lane": "{lane}", "testClassName": "{class}" }}"#))
        .collect();
    format!(r#"{{ "source": "AirJourneySuites", "suites": [{}] }}"#, entries.join(", "))
}

fn authored_tree() -> Tree {
    let mut tree = harness_tree();
    put(
        &mut tree,
        authored_file(),
        authored_suites_text(&[
            ["problem-feedback", "GUI_CHAT", "AcpProblemFeedbackUiTest"],
            ["session-tool-window-composer", "UI", "AgentSessionToolWindowComposerUiTest"],
        ]),
    );
    put(&mut tree, AUTHORED_SOURCE, "package air\n");
    tree
}

const AUTHORED_SOURCE: &str = "plugins/air/tests/integration/gui-chat/test/AcpProblemFeedbackUiTest.kt";

/// An authored suite has no document, so its test class source is the only file that is the suite.
#[test]
fn an_authored_test_class_reaches_its_own_suite() {
    let affected = affected_of(&FakeRuntime::with_tree(&authored_tree()), &[AUTHORED_SOURCE]);
    assert_eq!(
        affected.suites,
        [AffectedSuite {
            suite: "problem-feedback".to_owned(),
            lane: "gui-chat".to_owned(),
            class: "AcpProblemFeedbackUiTest".to_owned(),
            via: vec!["suite:problem-feedback".to_owned()],
            authored: true,
        }]
    );
}

/// The harness serves the authored suites of its lanes as it serves the generated ones.
#[test]
fn a_harness_path_reaches_the_authored_suites_of_its_lane() {
    let affected = affected_of(&FakeRuntime::with_tree(&authored_tree()), &[GUI_CHAT_GENERATED]);
    assert_eq!(
        classes_of(&affected),
        "gui-chat/AirManageLaunchPresetGeneratedFlowUiTest,gui-chat/AcpProblemFeedbackUiTest"
    );
}

/// A suite id names a suite whichever kind it is, so `run <authored suite id>` selects its class.
#[test]
fn a_suite_selector_names_an_authored_suite() {
    let affected = named_suites(
        &FakeRuntime::with_tree(&authored_tree()),
        area(),
        &Selector::new(SelectorKind::Suite, "session-tool-window-composer"),
    )
    .expect("named");
    assert_eq!(classes_of(&affected), "ui/AgentSessionToolWindowComposerUiTest");
    assert!(affected.suites[0].authored);
}

/// One id or one class must name one suite. An authored suite that clashes with a generated one would make a path
/// that reaches one answer the other.
#[test]
fn an_authored_suite_that_clashes_with_a_generated_one_is_refused() {
    let fake = FakeRuntime::with_tree(&authored_tree());
    fake.put(
        authored_file(),
        &authored_suites_text(&[["authored-copy", "UI", "AirNewSessionTerminalGeneratedFlowUiTest"]]),
    );
    let failure = refusal(affected_suites(&fake, area(), &["plugins/air/frontend/src/Rows.kt".to_owned()]));
    assert_eq!(failure.exit, exit::INFRA);
    assert!(failure.message.contains("authored-copy"), "{}", failure.message);
}

/// A file that is there and cannot be parsed is refused, and the refusal says where the text to commit comes from.
#[test]
fn a_malformed_authored_suite_file_is_refused_with_its_regeneration() {
    let fake = FakeRuntime::with_tree(&authored_tree());
    fake.put(authored_file(), r#"{ "suites": [ { "suite": "problem-feedback" } ] }"#);
    let failure = refusal(affected_suites(&fake, area(), &["plugins/air/frontend/src/Rows.kt".to_owned()]));
    assert_eq!(failure.exit, exit::INFRA);
    assert!(failure.message.contains("prints the text to commit"), "{}", failure.message);
}

const FEEDBACK_SPEC: &str = "plugins/air/spec/core/feedback.spec.md";
const GENERATED_SPEC: &str = "plugins/air/spec/other/generated.spec.md";
const GENERATED_LINK: &str = "  [@test] ../../tests/integration/gui-chat/testGen/AirGuiChatFlowTests.generated.kt";

/// The authored catalog plus two specs: one owns a directory by a glob and links an authored test class and a suite
/// document, and one links only a lane's generated file.
fn spec_tree() -> Tree {
    let mut tree = authored_tree();
    put(
        &mut tree,
        FEEDBACK_SPEC,
        [
            "---",
            "name: Feedback",
            "targets:",
            "  - ../../frontend/src/*.kt",
            "  - ../../notes/src",
            "style: plain-1",
            "---",
            "",
            "# Feedback",
            "",
            "- The dialog opens.",
            "  [@test] ../../tests/integration/gui-chat/test/AcpProblemFeedbackUiTest.kt",
            "  [@test] ../../tests/integration/flow-profiles/resources/flow-profiles/new-session-terminal.json",
        ]
        .join("\n"),
    );
    put(
        &mut tree,
        GENERATED_SPEC,
        [
            "---",
            "name: Generated",
            "targets:",
            "  - ../../session/src/Quiet.kt",
            "---",
            "",
            GENERATED_LINK,
        ]
        .join("\n"),
    );
    tree
}

const FEEDBACK_SUITES: &str = "gui-chat/AcpProblemFeedbackUiTest,ui-real/AirNewSessionTerminalGeneratedFlowUiTest";

/// A path a spec owns reaches the suites the spec links, authored or generated, and the entry names the spec.
#[test]
fn a_path_a_spec_owns_reaches_the_suites_it_links() {
    let affected = affected_of(&FakeRuntime::with_tree(&spec_tree()), &["plugins/air/notes/src/Notes.kt"]);
    assert_eq!(classes_of(&affected), FEEDBACK_SUITES);
    for suite in &affected.suites {
        assert_eq!(suite.via, [format!("{VIA_SPEC}{FEEDBACK_SPEC}")]);
    }
    // The module that no route walks is covered by the spec now.
    assert_eq!(affected.unmapped, []);
}

/// A tag is the exact fact. The spec relation would widen a tagged answer, often by a lane.
#[test]
fn a_tagged_path_is_not_widened_by_its_spec() {
    let affected = affected_of(&FakeRuntime::with_tree(&spec_tree()), &["plugins/air/frontend/src/Rows.kt"]);
    assert_eq!(classes_of(&affected), LAUNCH_PRESET_BOTH);
}

/// With no tag, the spec relation and the module relation both answer: they reach different suites, and an
/// authored suite has no module to be reached by.
#[test]
fn the_spec_relation_and_the_module_relation_both_answer_an_untagged_path() {
    let mut tree = spec_tree();
    let relinked = tree[GENERATED_SPEC].replace(
        GENERATED_LINK,
        "  [@test] ../../tests/integration/gui-chat/test/AcpProblemFeedbackUiTest.kt",
    );
    put(&mut tree, GENERATED_SPEC, relinked);
    let affected = affected_of(&FakeRuntime::with_tree(&tree), &["plugins/air/session/src/Quiet.kt"]);
    assert_eq!(classes_of(&affected), format!("gui-chat/AcpProblemFeedbackUiTest,{QUICK_START}"));
}

/// A link to a lane's generated file names every suite of the lane, not one, so it links no suite.
#[test]
fn a_link_to_a_generated_lane_file_links_no_suite() {
    let affected = affected_of(&FakeRuntime::with_tree(&spec_tree()), &["plugins/air/session/src/Quiet.kt"]);
    for suite in &affected.suites {
        for via in &suite.via {
            assert!(
                !via.starts_with(VIA_SPEC),
                "the generated file must link nothing, {} came via {via}",
                suite.class
            );
        }
    }
}

/// A path declaring only a flow no suite reaches keeps its reason unless a spec that owns it links a suite.
#[test]
fn an_unclaimed_flow_is_answered_by_its_spec() {
    let fake = FakeRuntime::with_tree(&spec_tree());
    fake.put("plugins/air/frontend/src/Launch.kt", &kdoc(&["@flow flow-new-session-launch"]));
    fake.put("plugins/air/backend/Launch.kt", &kdoc(&["@flow flow-new-session-launch"]));
    assert_eq!(
        classes_of(&affected_of(&fake, &["plugins/air/frontend/src/Launch.kt"])),
        FEEDBACK_SUITES
    );
    assert_eq!(
        reasons_of(&affected_of(&fake, &["plugins/air/backend/Launch.kt"])),
        reason::FLOW_DECLARES_NO_SUITE
    );
}

/// The glob rule is `AirRepoScan.globToRegex`, so the spec gate and this join agree on what a target owns.
#[test]
fn a_spec_glob_matches_as_the_spec_gate_matches() {
    for (glob, path, want) in [
        ("plugins/air/a/*.kt", "plugins/air/a/X.kt", true),
        ("plugins/air/a/*.kt", "plugins/air/a/b/X.kt", false),
        ("plugins/air/a/**/*.kt", "plugins/air/a/X.kt", true),
        ("plugins/air/a/**/*.kt", "plugins/air/a/b/c/X.kt", true),
        ("plugins/air/a/**/*.kt", "plugins/air/X.kt", false),
        ("plugins/air/a/**", "plugins/air/a/b/X.md", true),
        ("plugins/air/a/Row?.kt", "plugins/air/a/Rows.kt", true),
        ("plugins/air/a/Row?.kt", "plugins/air/a/Row.kt", false),
        ("plugins/air/a.b/(x)/*.kt", "plugins/air/a.b/(x)/Y.kt", true),
        ("plugins/air/a.b/(x)/*.kt", "plugins/air/aXb/(x)/Y.kt", false),
    ] {
        assert_eq!(glob_pattern(glob).is_match(path), want, "{glob} against {path}");
    }
}

/// The `unmapped` and `lanes` arrays are always present, `authored`, `flows` and `module` only when they say
/// something: the JSON the lane controller and the trace planner pass on verbatim. It crosses as text, because BT's
/// crates link another build of `serde`.
#[test]
fn the_answer_serializes_with_the_agent_facing_names() {
    let affected = affected_of(
        &FakeRuntime::with_tree(&authored_tree()),
        &[AUTHORED_SOURCE, "plugins/air/notes/src/Notes.kt"],
    );
    let json: serde_json::Value = serde_json::from_str(&affected.to_json_text()).expect("the text is JSON");
    assert_eq!(
        json,
        serde_json::json!({
            "suites": [{"suite": "problem-feedback", "lane": "gui-chat", "class": "AcpProblemFeedbackUiTest",
                        "via": ["suite:problem-feedback"], "authored": true}],
            "unmapped": [{"path": "plugins/air/notes/src/Notes.kt", "reason": "module_not_walked",
                          "module": "intellij.air.notes"}],
            "lanes": ["gui-chat"],
        })
    );
    assert_eq!(Affected::default().to_json_text(), r#"{"suites":[],"unmapped":[],"lanes":[]}"#);
}
