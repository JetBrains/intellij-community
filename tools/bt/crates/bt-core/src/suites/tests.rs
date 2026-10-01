use pretty_assertions::assert_eq;

use super::*;
use crate::exit;
use crate::fake::{FakeRuntime, area, areas, flow_catalog_tree, lanes, reaching, refusal};
use crate::lanes::{RunPlan, build_bazel_args, default_shards};
use crate::scan::ResolutionInputs;
use crate::selector::resolve_selector;

fn catalog() -> FakeRuntime {
    FakeRuntime::with_tree(&flow_catalog_tree())
}

fn selector(raw: &str) -> Selector {
    Selector::classify(raw).unwrap_or_else(|failure| panic!("{raw} refused: {failure}"))
}

/// The bazel argv a settled suite run becomes, the way the run builds it: the lane owns the flags and the shards.
fn argv_of(resolution: &Resolution) -> Vec<String> {
    let lane = resolution.lane.as_deref().and_then(|lane| lanes().get(lane));
    build_bazel_args(&RunPlan {
        resolution: resolution.clone(),
        extra: lane.map(|lane| lane.extra.clone()).unwrap_or_default(),
        shards: default_shards(resolution, lane),
        bep_path: "/tmp/bep.json".to_owned(),
        ..RunPlan::default()
    })
}

fn suites_of(resolution: &Resolution) -> Vec<String> {
    resolution
        .suites
        .iter()
        .map(|suite| format!("{} ({}, lane {})", suite.class, suite.suite, suite.lane))
        .collect()
}

/// A flow selector is its lane narrowed to the classes of the flow's suites: the lane's target and flags, one
/// shard because the lane launches one IDE, and one class filter per suite in `JB_TEST_JUNIT5_FILTERS`.
#[test]
fn a_flow_selector_runs_its_lane_narrowed_to_its_suites_classes() {
    let fake = catalog();
    let resolution = resolve_selector(
        &fake,
        &selector("flow-new-session-terminal"),
        &ResolutionInputs::new(&fake, areas()),
    )
    .expect("resolves");
    assert_eq!(resolution.labels, ["//plugins/air/tests/integration/ui-real:ui-real_test"]);
    assert_eq!(
        suites_of(&resolution),
        ["AirNewSessionTerminalGeneratedFlowUiTest (new-session-terminal, lane ui-real)"]
    );
    assert_eq!(
        resolution.junit5_filters,
        [r"include-classname=(^|.*\.)AirNewSessionTerminalGeneratedFlowUiTest$"]
    );
    let argv = argv_of(&resolution);
    for expected in [
        "--test_sharding_strategy=forced=1",
        r"--test_env=JB_TEST_JUNIT5_FILTERS=include-classname=(^|.*\.)AirNewSessionTerminalGeneratedFlowUiTest$",
    ] {
        assert!(
            argv.iter().any(|argument| argument == expected),
            "{expected} is missing from {argv:?}"
        );
    }
    // A class filter selects the suites, so no --test_filter may narrow the run a second time.
    assert!(!argv.iter().any(|argument| argument.starts_with("--test_filter")), "{argv:?}");
    assert!(fake.spawned().is_empty());
}

/// Two lanes are one refusal, the same one `run --changed` gives, and `--lane` beside the selector settles it. The
/// refusal names every lane and chooses none, because a GUI-chat suite may drive the physical pointer.
#[test]
fn a_flow_of_two_lanes_is_refused_until_lane_settles_it() {
    let flow = selector("flow-manage-launch-preset");
    let failure = refusal(resolve_suite_run(&catalog(), area(), &flow, None));
    assert_eq!((failure.code.as_ref(), failure.exit), ("affected_lanes_ambiguous", exit::USAGE));
    assert!(
        failure
            .message
            .contains("the scenarios of flow-manage-launch-preset reach 2 suite(s): ui 1, gui-chat 1"),
        "{}",
        failure.message
    );
    assert!(
        failure.message.contains("pass --lane with one of ui, gui-chat"),
        "{}",
        failure.message
    );

    let settled = resolve_suite_run(&catalog(), area(), &flow, Some("ui")).expect("--lane settles it");
    assert_eq!(settled.labels, ["//plugins/air/tests/integration/ui/..."]);
    assert!(argv_of(&settled).contains(&"--test_tag_filters=air-integration-ui-flow".to_owned()));
    assert_eq!(
        settled.junit5_filters,
        [r"include-classname=(^|.*\.)AirManageLaunchPresetQuickStartGeneratedFlowUiTest$"]
    );

    let unreached = refusal(resolve_suite_run(&catalog(), area(), &flow, Some("ui-real")));
    assert_eq!(unreached.code, "no_affected_suite");
    assert!(
        unreached
            .message
            .contains("no ui-real suite covers the scenarios of flow-manage-launch-preset"),
        "{}",
        unreached.message
    );
}

/// A suite selector is that one suite, on its own lane, whatever else its flow reaches.
#[test]
fn a_suite_selector_runs_its_one_suite() {
    let resolution = resolve_suite_run(&catalog(), area(), &selector("manage-launch-preset"), None).expect("resolves");
    assert_eq!(resolution.labels, ["//plugins/air/tests/integration/gui-chat/..."]);
    assert_eq!(
        suites_of(&resolution),
        ["AirManageLaunchPresetGeneratedFlowUiTest (manage-launch-preset, lane gui-chat)"]
    );
    assert!(argv_of(&resolution).contains(&"--test_tag_filters=air-integration-gui-chat".to_owned()));
    assert!(resolution.multi_target);
}

fn classes_of(affected: &Affected) -> String {
    affected
        .suites
        .iter()
        .map(|suite| format!("{}/{}", suite.lane, suite.class))
        .collect::<Vec<_>>()
        .join(",")
}

/// A `@flow` tag and a flow selector answer through one relation, so an implementation flow reaches the suites of
/// the story flows that implement it.
#[test]
fn an_implementation_flow_selects_the_suites_of_its_story_flows() {
    let mut tree = flow_catalog_tree();
    reaching(
        &mut tree,
        "new-session-terminal",
        &["intellij.air.terminal"],
        &["flow-new-session-launch"],
    );
    let affected = named_suites(
        &FakeRuntime::with_tree(&tree),
        area(),
        &Selector::new(SelectorKind::Flow, "flow-new-session-launch"),
    )
    .expect("named");
    assert_eq!(classes_of(&affected), "ui-real/AirNewSessionTerminalGeneratedFlowUiTest");
    assert_eq!(affected.suites[0].via, ["flow:flow-new-session-launch"]);
}

/// An id nothing knows names the nearest ids. A flow with committed story text and no suite is a different answer,
/// `no_affected_suite` with its own reason, because the next action is an authored suite, not a spelling fix.
#[test]
fn an_unknown_id_names_the_nearest_and_an_uncovered_flow_says_no_suite_tests_it() {
    let mut tree = flow_catalog_tree();
    let text_dir = lanes().catalog().expect("a catalog").flow_text_dir;
    tree.insert(format!("{text_dir}/flow-java-to-kotlin.txt"), "Java to Kotlin\n".to_owned());
    tree.insert(format!("{text_dir}/index.txt"), "flows\n".to_owned());
    let flow_hint = format!("(no similar id; a flow id is a story flow under {text_dir}");
    let suite_hint = format!(
        "(no similar id; a suite id is the name of a document under {}",
        lanes().catalog().expect("a catalog").flow_profile_dir
    );
    for (raw, fragment) in [
        ("flow-manage-launch-prest", "did you mean  flow-manage-launch-preset"),
        ("flow-java-to-kotln", "did you mean  flow-java-to-kotlin"),
        ("manage-launch-prest", "did you mean  manage-launch-preset"),
        ("flow-nothing-like-it", flow_hint.as_str()),
        ("nothing-like-it", suite_hint.as_str()),
    ] {
        let fake = FakeRuntime::with_tree(&tree);
        let failure = refusal(resolve_suite_run(&fake, area(), &selector(raw), None));
        assert_eq!((failure.code.as_ref(), failure.exit), ("usage", exit::USAGE), "{raw}");
        assert!(failure.message.contains(fragment), "{raw}: {}", failure.message);
    }

    let failure = refusal(resolve_suite_run(
        &FakeRuntime::with_tree(&tree),
        area(),
        &selector("flow-java-to-kotlin"),
        None,
    ));
    assert_eq!((failure.code.as_ref(), failure.exit), ("no_affected_suite", exit::USAGE));
    assert!(
        failure
            .message
            .contains(&format!("flow-java-to-kotlin: {REASON_NO_SUITE_TESTS_FLOW}")),
        "{}",
        failure.message
    );
    assert_eq!(
        failure.details,
        Some(json!({"flow": "flow-java-to-kotlin", "reason": REASON_NO_SUITE_TESTS_FLOW}))
    );
}

/// The listing shows every lane a flow reaches, before `--lane` narrows it.
#[test]
fn list_names_every_suite_of_a_flow() {
    let affected = named_suites(&catalog(), area(), &selector("flow-manage-launch-preset")).expect("named");
    let text = affected_text(&affected);
    for expected in [
        "gui-chat AirManageLaunchPresetGeneratedFlowUiTest (manage-launch-preset) via flow:flow-manage-launch-preset",
        "ui AirManageLaunchPresetQuickStartGeneratedFlowUiTest (manage-launch-preset-quick-start)",
    ] {
        assert!(text.contains(expected), "no {expected:?} in:\n{text}");
    }
}

/// A scenario walks a flow it does not tell when one of its steps belongs to that flow. The planner and the flow
/// selector both ask this rule.
#[test]
fn a_scenario_walks_the_flows_of_its_steps() {
    let scenario = ScenarioFlows {
        flow: "flow-add-to-agent-context".to_owned(),
        step_flows: vec![
            "flow-add-to-agent-context".to_owned(),
            "flow-send-message-with-context".to_owned(),
            String::new(),
        ],
    };
    assert!(scenario.walks("flow-add-to-agent-context") && scenario.walks("flow-send-message-with-context"));
    assert!(!scenario.walks("flow-rename-session") && !scenario.walks(""));
    assert_eq!(scenario.flows(), ["flow-add-to-agent-context", "flow-send-message-with-context"]);
}

/// A generated class name is embedded in the pattern unquoted, so a name that is not a plain identifier is refused
/// rather than allowed to widen or narrow the filter.
#[test]
fn a_suite_class_filter_accepts_only_a_plain_identifier() {
    assert_eq!(
        suite_class_filters(&["AirOneGeneratedFlowUiTest".to_owned()], "ui"),
        Ok(vec![r"include-classname=(^|.*\.)AirOneGeneratedFlowUiTest$".to_owned()])
    );
    for corrupt in ["a.B", "B$Nested", r"B\E", ""] {
        let failure = refusal(suite_class_filters(&[corrupt.to_owned()], "ui"));
        assert_eq!(failure.code, "affected_class_unpatternable", "{corrupt:?}");
    }
}

/// Past the first few unmapped paths the refusal counts the rest, and its details carry every one.
#[test]
fn the_no_suite_refusal_names_the_first_paths_and_counts_the_rest() {
    let affected = Affected {
        unmapped: (0..UNMAPPED_LINES_PER_REASON + 3)
            .map(|index| UnmappedPath {
                path: format!("plugins/air/notes/N{index}.kt"),
                reason: "module_not_walked".to_owned(),
                ..UnmappedPath::default()
            })
            .collect(),
        ..Affected::default()
    };
    let failure = refusal(choose_lane(area(), &affected, None, CHANGED_PATHS_SUBJECT));
    assert!(failure.message.contains("and 3 more"), "{}", failure.message);
    assert!(!failure.message.contains("N12.kt"), "{}", failure.message);
    assert!(failure.message.contains("[@test]"), "{}", failure.message);
    let details = failure.details.expect("the details carry the answer");
    assert_eq!(details["affected"]["unmapped"].as_array().map(Vec::len), Some(13));
}

/// A settled answer is its one lane, and the counts follow the declared lane order rather than the sorted one.
#[test]
fn one_lane_is_chosen_and_the_counts_follow_the_declared_order() {
    let affected = named_suites(&catalog(), area(), &selector("flow-manage-launch-preset")).expect("named");
    assert_eq!(affected.lanes, ["gui-chat", "ui"]);
    let counts = lane_counts(area(), &affected);
    assert_eq!(lane_counts_text(&counts), "ui 1, gui-chat 1");
    assert_eq!(counted_lane_names(&counts), ["ui", "gui-chat"]);
    let single = named_suites(&catalog(), area(), &selector("new-session-terminal")).expect("named");
    assert_eq!(choose_lane(area(), &single, None, "x"), Ok("ui-real".to_owned()));
    assert_eq!(choose_lane(area(), &affected, Some("gui-chat"), "x"), Ok("gui-chat".to_owned()));
}

/// An answer reads back as the answer it was, so a caller that parses the JSON text sees every field.
#[test]
fn an_answer_reads_back_from_its_json_text() {
    let affected = named_suites(&catalog(), area(), &selector("flow-manage-launch-preset")).expect("named");
    let read: Affected = serde_json::from_str(&affected.to_json_text()).expect("reads back");
    assert_eq!(read, affected);
}
