use pretty_assertions::assert_eq;

use bt_core::fake::{FakeRuntime, area, base_suite_fixtures, classes_of, document_path, flow_catalog_tree};
use bt_core::suites::{Affected, UnmappedPath};

use super::*;
use crate::affected::{affected_suites, reason};

const BRIDGE: &str = "plugins/air/tests/integration/bridge";
const MODULE: &str = "intellij.air.integrationTests.bridge";
const DRIVER: &str = "plugins/air/tests/integration/bridge/tests/Driver.kt";
const SHARED: &str = "plugins/air/tests/integration/bridge/tests/Resets.kt";
const BODIES: &str = "plugins/air/tests/integration/bridge/testGen/Bodies.generated.kt";
const VOCABULARY: &str = "plugins/air/tests/integration/bridge/resources/flow-vocabulary.json";
const BASE: &str = "base";

const QUICK_START: &str = "ui/AirManageLaunchPresetQuickStartGeneratedFlowUiTest";
const PRESET: &str = "gui-chat/AirManageLaunchPresetGeneratedFlowUiTest";
const TERMINAL: &str = "ui-real/AirNewSessionTerminalGeneratedFlowUiTest";

const DRIVER_TEXT: &str = r#"package com.intellij.air.integration.bridge

internal class Driver {
  fun openPreset() {
    click("preset")
  }

  fun openTerminal() {
    click("terminal")
  }

  private fun click(id: String) = Unit
}

/** A type that no request creates, with a member name that every request calls. */
internal class Idle {
  fun run(bridges: Bridges) = Unit
}
"#;

const VOCABULARY_TEXT: &str =
    r#"{"actions":{"open-preset":{},"open-terminal":{}},"assertions":{},"authored":{"manage-launch-preset":["part"]},"runnerAnswers":[]}"#;

/// The document of a base suite with its vocabulary: the actions that run inside the IDE, and one on the host.
fn document(suite: &'static str, actions: &[&str], authored: bool) -> String {
    let text = base_suite_fixtures()[suite].text();
    let mut entries: Vec<String> = actions.iter().map(|action| format!(r#""{action}": {{ "side": "ide" }}"#)).collect();
    entries.push(r#""host-check": { "side": "host" }"#.to_owned());
    text.replacen(
        r#""suite":"#,
        &format!(
            r#""vocabulary": {{ "actions": {{ {} }}, "assertions": {{}} }}, "authored": {authored}, "suite":"#,
            entries.join(", ")
        ),
        1,
    )
}

/// A bridge with a sealed request type, a reset that every suite runs, two flow requests for two drivers, the authored
/// step with its dispatcher, and the descriptors. The quick start opens the preset, the authored preset suite runs one
/// part that opens the preset too, and the terminal suite opens the terminal.
fn bridge_tree() -> FakeRuntime {
    let mut tree = flow_catalog_tree();
    tree.insert(
        document_path("manage-launch-preset-quick-start"),
        document("manage-launch-preset-quick-start", &["open-preset"], false),
    );
    tree.insert(
        document_path("manage-launch-preset"),
        document("manage-launch-preset", &["authored-step"], true),
    );
    tree.insert(
        document_path("new-session-terminal"),
        document("new-session-terminal", &["open-terminal"], false),
    );
    let files = [
        (
            format!("{BRIDGE}/intellij.air.integrationTests.bridge.iml"),
            "<module />".to_owned(),
        ),
        (
            format!("{BRIDGE}/resources/intellij.air.integrationTests.bridge.xml"),
            r#"<idea-plugin><extensions defaultExtensionNs="com.intellij">
  <applicationService serviceImplementation="com.intellij.air.integration.bridge.Service"/>
</extensions></idea-plugin>"#
                .to_owned(),
        ),
        (VOCABULARY.to_owned(), VOCABULARY_TEXT.to_owned()),
        (
            format!("{BRIDGE}/genui/resources/intellij.air.integrationTests.bridge.genui.xml"),
            "<idea-plugin/>".to_owned(),
        ),
        (
            format!("{BRIDGE}/genui/tests/Probe.kt"),
            "package genui\n\nclass Probe\n".to_owned(),
        ),
        (
            format!("{BRIDGE}/tests/Protocol.kt"),
            r#"package com.intellij.air.integration.bridge

sealed interface AirUiTestRequest<out R> {
  fun run(bridges: Bridges): R
}

@SerialName("reset")
data class Reset(val all: Boolean) : AirUiTestRequest<Unit> {
  override fun run(bridges: Bridges) = Resets.reset()
}
"#
            .to_owned(),
        ),
        (
            SHARED.to_owned(),
            r"package com.intellij.air.integration.bridge

object Resets {
  fun reset() {
    Shared.clean()
  }
}

object Shared {
  fun clean() = Unit
}
"
            .to_owned(),
        ),
        (
            format!("{BRIDGE}/tests/Service.kt"),
            "package com.intellij.air.integration.bridge\n\nclass Service {\n  fun ping() = Unit\n}\n".to_owned(),
        ),
        (DRIVER.to_owned(), DRIVER_TEXT.to_owned()),
        (
            format!("{BRIDGE}/testGen/Requests.generated.kt"),
            r#"package com.intellij.air.integration.bridge

@SerialName("action/open-preset")
data class AirFlowActionOpenPreset(val timeoutMs: Long) : AirUiTestRequest<Unit> {
  override fun run(bridges: Bridges) = Driver().openPreset()
}

@SerialName("action/open-terminal")
data class AirFlowActionOpenTerminal(val timeoutMs: Long) : AirUiTestRequest<Unit> {
  override fun run(bridges: Bridges) = Driver().openTerminal()
}

@SerialName("action/authored-step")
data class AirFlowActionAuthoredStep(val suite: String) : AirUiTestRequest<Unit> {
  override fun run(bridges: Bridges) = Bodies.run(suite)
}
"#
            .to_owned(),
        ),
        (BODIES.to_owned(), bodies(&[("manage-launch-preset", "PresetBody")])),
        (
            format!("{BRIDGE}/tests/authored/PresetBody.kt"),
            "package com.intellij.air.integration.bridge\n\nobject PresetBody {\n  fun part() = Driver().openPreset()\n}\n".to_owned(),
        ),
    ];
    for (path, text) in files {
        tree.insert(path, text);
    }
    let fake = FakeRuntime::with_tree(&tree);
    fake.set_upstream_base(BASE);
    for (path, text) in &tree {
        if path.starts_with(BRIDGE) {
            fake.put_revision(BASE, path, text);
        }
    }
    fake
}

/// The dispatcher of the authored parts, one branch for each suite and its body object.
fn bodies(branches: &[(&str, &str)]) -> String {
    let mut text = "package com.intellij.air.integration.bridge\n\nobject Bodies {\n  fun run(suite: String) = when (suite) {\n".to_owned();
    for (suite, body) in branches {
        text.push_str(&format!(
            "    \"{suite}/scenario/part\" -> {{\n      with({body}) {{ part() }}\n    }}\n"
        ));
    }
    text.push_str("    else -> error(suite)\n  }\n}\n");
    text
}

fn affected_of(fake: &FakeRuntime, path: &str) -> Affected {
    affected_suites(fake, area(), &[path.to_owned()], Baseline::Upstream).unwrap_or_else(|failure| panic!("the join refused: {failure}"))
}

fn vias(affected: &Affected) -> Vec<Vec<String>> {
    affected.suites.iter().map(|suite| suite.via.clone()).collect()
}

fn text_of(fake: &FakeRuntime, path: &str) -> String {
    fake.state().files[&format!("/repo/{path}")].clone()
}

/// A changed driver function reaches the suites whose requests reach it, and names itself as the reason.
#[test]
fn a_changed_driver_function_reaches_only_the_suites_whose_requests_reach_it() {
    let fake = bridge_tree();
    fake.put(DRIVER, &DRIVER_TEXT.replace(r#"click("terminal")"#, r#"click("terminal-tab")"#));
    let affected = affected_of(&fake, DRIVER);
    assert_eq!(classes_of(&affected), TERMINAL);
    assert_eq!(vias(&affected), [["declaration:Driver.openTerminal"]]);
    // The authored part of the preset suite opens the preset too.
    fake.put(DRIVER, &DRIVER_TEXT.replace(r#"click("preset")"#, r#"click("preset-row")"#));
    assert_eq!(classes_of(&affected_of(&fake, DRIVER)), format!("{PRESET},{QUICK_START}"));
}

/// A declaration that the roots of every suite reach, such as the reset, keeps the lane-wide answer.
#[test]
fn a_declaration_that_every_suite_reaches_is_lane_wide() {
    let fake = bridge_tree();
    fake.put(
        SHARED,
        &text_of(&fake, SHARED).replace("fun clean() = Unit", "fun clean() = println()"),
    );
    let affected = affected_of(&fake, SHARED);
    assert_eq!(classes_of(&affected), format!("{PRESET},{QUICK_START},{TERMINAL}"));
    assert_eq!(vias(&affected)[0], [format!("lane-wide:{MODULE}")]);
}

/// A new function that no suite calls yet reaches no suite, and the path says why.
#[test]
fn a_new_function_that_no_suite_calls_reaches_no_suite() {
    let fake = bridge_tree();
    fake.put(
        DRIVER,
        &DRIVER_TEXT.replace("  private fun click", "  fun openNothing() = Unit\n\n  private fun click"),
    );
    let affected = affected_of(&fake, DRIVER);
    assert_eq!(affected.suites, []);
    assert_eq!(
        affected.unmapped,
        [UnmappedPath {
            module: Some(MODULE.to_owned()),
            ..UnmappedPath::new(DRIVER, reason::DECLARATION_NOT_REACHED)
        }]
    );
}

/// Every request calls a member `run`, and only a created type answers it: a type that no reached code names keeps its
/// members out of the reach.
#[test]
fn a_member_is_reached_only_with_its_type() {
    let fake = bridge_tree();
    fake.put(
        DRIVER,
        &DRIVER_TEXT.replace("fun run(bridges: Bridges) = Unit", "fun run(bridges: Bridges) = println()"),
    );
    let affected = affected_of(&fake, DRIVER);
    assert_eq!(affected.suites, []);
    assert_eq!(affected.unmapped[0].reason, reason::DECLARATION_NOT_REACHED);
}

/// A change of a comment changes no declaration.
#[test]
fn a_changed_comment_reaches_no_suite() {
    let fake = bridge_tree();
    fake.put(
        DRIVER,
        &DRIVER_TEXT.replace("  fun openTerminal", "  /** Opens the terminal. */\n  fun openTerminal"),
    );
    let affected = affected_of(&fake, DRIVER);
    assert_eq!(affected.suites, []);
    assert_eq!(affected.unmapped[0].reason, reason::DECLARATION_NOT_REACHED);
}

/// A file equal to its baseline, a file that the baseline does not hold, and a checkout without an upstream each count
/// as changed as a whole. The entry names the file.
#[test]
fn a_file_without_a_baseline_to_compare_counts_as_changed_as_a_whole() {
    let every = format!("{PRESET},{QUICK_START},{TERMINAL}");
    let unchanged = bridge_tree();
    let affected = affected_of(&unchanged, DRIVER);
    assert_eq!(classes_of(&affected), every);
    assert_eq!(vias(&affected)[0], ["declaration:Driver.kt"]);

    let added = bridge_tree();
    if let Some(files) = added.state().revisions.get_mut(BASE) {
        files.remove(DRIVER);
    }
    assert_eq!(vias(&affected_of(&added, DRIVER))[0], ["declaration:Driver.kt"]);

    let without_upstream = bridge_tree();
    without_upstream.state().upstream_base = None;
    without_upstream.put(DRIVER, &DRIVER_TEXT.replace(r#"click("terminal")"#, r#"click("terminal-tab")"#));
    assert_eq!(classes_of(&affected_of(&without_upstream, DRIVER)), every);
}

/// A named revision is the baseline instead of the upstream.
#[test]
fn a_named_revision_is_the_baseline() {
    let fake = bridge_tree();
    fake.put_revision(
        "older",
        DRIVER,
        &DRIVER_TEXT.replace(r#"click("preset")"#, r#"click("old-preset")"#),
    );
    let affected = affected_suites(&fake, area(), &[DRIVER.to_owned()], Baseline::Revision("older")).expect("the join answers");
    assert_eq!(classes_of(&affected), format!("{PRESET},{QUICK_START}"));
}

/// A suite whose document names a request that no request type declares reaches every changed declaration. A host-side
/// action needs no request.
#[test]
fn a_suite_with_an_unknown_request_reaches_every_changed_declaration() {
    let fake = bridge_tree();
    fake.put(
        &document_path("manage-launch-preset-quick-start"),
        &document("manage-launch-preset-quick-start", &["open-preset", "open-missing"], false),
    );
    fake.put(DRIVER, &DRIVER_TEXT.replace(r#"click("terminal")"#, r#"click("terminal-tab")"#));
    let affected = affected_of(&fake, DRIVER);
    assert_eq!(classes_of(&affected), format!("{QUICK_START},{TERMINAL}"));
}

/// The branch of a suite in the dispatcher is a root of that suite alone. A change of a body reaches only its suite, and
/// a new branch of another suite changes only that suite.
#[test]
fn an_authored_body_reaches_only_its_suite() {
    let fake = bridge_tree();
    let body = format!("{BRIDGE}/tests/authored/PresetBody.kt");
    fake.put(
        &body,
        &text_of(&fake, &body).replace("openPreset()", "openPreset().also { println() }"),
    );
    let affected = affected_of(&fake, &body);
    assert_eq!(classes_of(&affected), PRESET);
    assert_eq!(vias(&affected), [["declaration:PresetBody.part"]]);

    let fake = bridge_tree();
    fake.put(
        &document_path("new-session-terminal"),
        &document("new-session-terminal", &["open-terminal", "authored-step"], true),
    );
    fake.put(
        BODIES,
        &bodies(&[("manage-launch-preset", "PresetBody"), ("new-session-terminal", "TerminalBody")]),
    );
    let affected = affected_of(&fake, BODIES);
    assert_eq!(classes_of(&affected), TERMINAL);
    assert_eq!(vias(&affected), [["declaration:Bodies.run"]]);
}

/// A suite with authored parts that reaches the dispatcher without a branch of its own runs a body that the reach
/// cannot find, so it reaches every changed declaration.
#[test]
fn an_authored_suite_without_a_branch_reaches_every_changed_declaration() {
    let fake = bridge_tree();
    let other_suite_only = bodies(&[("new-session-terminal", "PresetBody")]);
    fake.put(BODIES, &other_suite_only);
    fake.put_revision(BASE, BODIES, &other_suite_only);
    fake.put(DRIVER, &DRIVER_TEXT.replace(r#"click("terminal")"#, r#"click("terminal-tab")"#));
    let affected = affected_of(&fake, DRIVER);
    assert_eq!(classes_of(&affected), format!("{PRESET},{TERMINAL}"));
}

/// A changed entry of the vocabulary reaches the suites whose vocabulary holds it. A change outside the entries is
/// lane-wide.
#[test]
fn a_changed_vocabulary_entry_reaches_the_suites_that_hold_it() {
    let fake = bridge_tree();
    fake.put(VOCABULARY, &VOCABULARY_TEXT.replace(r#"["part"]"#, r#"["part","second"]"#));
    let affected = affected_of(&fake, VOCABULARY);
    assert_eq!(classes_of(&affected), PRESET);
    assert_eq!(vias(&affected), [["vocabulary:authored/manage-launch-preset"]]);

    fake.put(
        VOCABULARY,
        &VOCABULARY_TEXT.replace(r#""open-terminal":{}"#, r#""open-terminal":{"side":"ide"}"#),
    );
    assert_eq!(classes_of(&affected_of(&fake, VOCABULARY)), TERMINAL);

    fake.put(
        VOCABULARY,
        &VOCABULARY_TEXT.replace(r#""runnerAnswers":[]"#, r#""runnerAnswers":["x"]"#),
    );
    assert_eq!(vias(&affected_of(&fake, VOCABULARY))[0], [format!("lane-wide:{MODULE}")]);
}

/// A file of the module that is no Kotlin source keeps the lane-wide answer, and so does a module whose sources the
/// relation cannot read.
#[test]
fn a_file_outside_the_kotlin_sources_and_an_unreadable_source_stay_lane_wide() {
    let fake = bridge_tree();
    let resource = format!("{BRIDGE}/resources/skills/skill.md");
    fake.put(&resource, "# A skill\n");
    assert_eq!(vias(&affected_of(&fake, &resource))[0], [format!("lane-wide:{MODULE}")]);

    let fake = bridge_tree();
    fake.state().files.retain(|path, _| !path.contains("/genui/"));
    fake.put(DRIVER, &DRIVER_TEXT.replace(r#"click("terminal")"#, r#"click("terminal-tab")"#));
    assert_eq!(vias(&affected_of(&fake, DRIVER))[0], [format!("lane-wide:{MODULE}")]);
}

/// The report holds the committed sources to the relation: every suite resolves, and a declaration names the suites
/// that reach it.
#[test]
fn the_report_names_the_unresolved_suites_and_the_suites_of_a_declaration() {
    let fake = bridge_tree();
    let report = DeclarationReport::read(&fake, area(), MODULE).expect("the report reads the sources");
    assert_eq!(report.unresolved_suites(), Vec::<String>::new());
    assert_eq!(
        report.suites_reaching(DRIVER, "Driver.openPreset"),
        Ok(Some(vec![
            "manage-launch-preset-quick-start".to_owned(),
            "manage-launch-preset".to_owned()
        ]))
    );
    assert_eq!(report.suites_reaching(SHARED, "Shared.clean"), Ok(None));
    assert_eq!(
        report.suites_reaching(DRIVER, "Driver.absent"),
        Err(format!("{DRIVER} declares no Driver.absent"))
    );
}

/// A change of tokens outside every declaration, such as the package line, changes the whole file. An added import
/// changes each declaration that names the imported name.
#[test]
fn a_package_line_changes_the_whole_file_and_an_import_changes_its_users() {
    let every = format!("{PRESET},{QUICK_START},{TERMINAL}");
    let fake = bridge_tree();
    fake.put(
        DRIVER,
        &DRIVER_TEXT.replace("package com.intellij.air.integration.bridge", "package com.intellij.air.other"),
    );
    let affected = affected_of(&fake, DRIVER);
    assert_eq!(classes_of(&affected), every);
    assert_eq!(vias(&affected)[0], ["declaration:Driver.kt"]);

    let fake = bridge_tree();
    fake.put(
        DRIVER,
        &DRIVER_TEXT.replace(
            "package com.intellij.air.integration.bridge\n",
            "package com.intellij.air.integration.bridge\n\nimport other.click\n",
        ),
    );
    let affected = affected_of(&fake, DRIVER);
    assert_eq!(classes_of(&affected), every);
    assert_eq!(vias(&affected)[0], ["declaration:Driver.openPreset"]);
}

/// A harness file that the checkout no longer holds reaches the whole lane set, because nothing tells which suites
/// used it.
#[test]
fn a_deleted_bridge_file_is_lane_wide() {
    let fake = bridge_tree();
    fake.state().files.remove(&format!("/repo/{DRIVER}"));
    let affected = affected_of(&fake, DRIVER);
    assert_eq!(classes_of(&affected), format!("{PRESET},{QUICK_START},{TERMINAL}"));
    assert_eq!(vias(&affected)[0], [format!("lane-wide:{MODULE}")]);
}

/// The platform calls an override of a platform member, so it also calls an override of that override in a subtype that
/// the code creates.
#[test]
fn an_override_of_a_platform_override_is_reached_with_its_type() {
    let fake = bridge_tree();
    let actions = format!("{BRIDGE}/tests/Actions.kt");
    let text = "package com.intellij.air.integration.bridge\n\nopen class BaseAction : AnAction() {\n  override fun actionPerformed(event: Event) = Unit\n}\n\nclass LeafAction : BaseAction() {\n  override fun actionPerformed(event: Event) = Unit\n}\n";
    fake.put(&actions, text);
    fake.put_revision(BASE, &actions, text);
    let driver = DRIVER_TEXT.replace(r#"click("terminal")"#, "register(LeafAction())");
    fake.put(DRIVER, &driver);
    fake.put_revision(BASE, DRIVER, &driver);
    fake.put(
        &actions,
        &text.replace(
            "class LeafAction : BaseAction() {\n  override fun actionPerformed(event: Event) = Unit",
            "class LeafAction : BaseAction() {\n  override fun actionPerformed(event: Event) = println()",
        ),
    );
    assert_eq!(classes_of(&affected_of(&fake, &actions)), TERMINAL);
}

/// A sealed type is decoded into one of its subtypes, which no code names, so a reached sealed type reaches its
/// subtypes.
#[test]
fn a_reached_sealed_type_reaches_its_subtypes() {
    let fake = bridge_tree();
    let steps = format!("{BRIDGE}/tests/Steps.kt");
    let text = "package com.intellij.air.integration.bridge\n\nsealed interface Step {\n  fun go()\n}\n\ndata class Click(val id: String) : Step {\n  override fun go() = Unit\n}\n";
    fake.put(&steps, text);
    fake.put_revision(BASE, &steps, text);
    let driver = DRIVER_TEXT.replace(r#"click("preset")"#, "decode<Step>(json).go()");
    fake.put(DRIVER, &driver);
    fake.put_revision(BASE, DRIVER, &driver);
    fake.put(&steps, &text.replace("override fun go() = Unit", "override fun go() = println()"));
    assert_eq!(classes_of(&affected_of(&fake, &steps)), format!("{PRESET},{QUICK_START}"));
}

/// A pair of a map keyed by a suite is created with the map, for every user. A changed pair of constructors and
/// literals reaches its suite alone, and a changed pair that runs a function of the sources changes the map.
#[test]
fn a_changed_pair_that_runs_a_bridge_function_changes_its_map() {
    let parts = format!("{BRIDGE}/testGen/Parts.generated.kt");
    let map = |value: &str| {
        format!(
            "package com.intellij.air.integration.bridge\n\nobject Parts {{\n  val contracts = mapOf(\n    \"manage-launch-preset/scenario/part\" to {value},\n  )\n}}\n"
        )
    };
    let with_parts = |value: &str| {
        let fake = bridge_tree();
        fake.put_revision(BASE, &parts, &map("Contract(takes = emptyMap())"));
        fake.put(&parts, &map(value));
        let driver = DRIVER_TEXT.replace(r#"click("terminal")"#, "Parts.contracts.size");
        fake.put(DRIVER, &driver);
        fake.put_revision(BASE, DRIVER, &driver);
        fake.put(
            &format!("{BRIDGE}/tests/Describe.kt"),
            "package com.intellij.air.integration.bridge\n\nfun describe(value: String) = value\n",
        );
        affected_of(&fake, &parts)
    };
    let constructors = with_parts("Contract(takes = mapOf(\"a\" to 1))");
    assert_eq!(classes_of(&constructors), PRESET);
    assert_eq!(vias(&constructors), [["declaration:Parts.contracts"]]);
    // The terminal suite reads the map, so it creates every pair of it.
    let running = with_parts("Contract(takes = describe(\"a\"))");
    assert_eq!(classes_of(&running), format!("{PRESET},{TERMINAL}"));
}
