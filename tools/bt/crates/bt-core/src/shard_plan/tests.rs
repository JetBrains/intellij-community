use pretty_assertions::assert_eq;

use super::*;
use crate::fake::timing;

/// A timing of one class per value, with the classes `com.C0`, `com.C1` and so on.
fn classes(label: &str, seconds: &[f64]) -> TargetTiming {
    TargetTiming {
        label: label.to_owned(),
        class_seconds: seconds
            .iter()
            .enumerate()
            .map(|(index, seconds)| (format!("com.C{index}"), *seconds))
            .collect(),
        fixed_seconds: None,
    }
}

#[test]
fn a_target_gets_a_shard_per_90_recorded_seconds_up_to_the_heavy_count() {
    let mut plan = ShardPlan::default();
    plan.record(&timing("//a:small", &[("A", 5.0)]));
    plan.record(&timing("//a:full", &[("A", 45.0), ("B", 45.0)]));
    plan.record(&timing("//a:medium", &[("A", 60.0), ("B", 60.0)]));
    plan.record(&classes("//a:heavy", &[90.0, 90.0, 90.0, 90.0, 90.0, 80.0]));
    plan.record(&classes("//a:heaviest", &[100.0; 20]));
    plan.record(&timing("//a:empty", &[]));

    assert_eq!(plan.shards_of("//a:small"), SINGLE_SHARDS);
    assert_eq!(plan.shards_of("//a:full"), SINGLE_SHARDS);
    assert_eq!(plan.shards_of("//a:medium"), PLANNED_SHARDS);
    assert_eq!(plan.shards_of("//a:heavy"), 6);
    assert_eq!(plan.shards_of("//a:heaviest"), HEAVY_SHARDS);
    assert_eq!(plan.shards_of("//a:empty"), 1);
    assert_eq!(plan.shards_of("//a:unknown"), DEFAULT_SHARDS);
}

/// The runner never splits a top-level class. So a target gets at most one shard per its heaviest class, and a target of
/// one class gets one shard, whatever its seconds.
#[test]
fn a_target_gets_no_more_shards_than_its_heaviest_class_allows() {
    let mut plan = ShardPlan::default();
    plan.record(&timing("//a:one_class", &[("com.A", 2000.0)]));
    plan.record(&timing(
        "//a:one_heavy_class",
        &[("com.A", 300.0), ("com.B", 10.0), ("com.C", 10.0), ("com.D", 10.0)],
    ));
    plan.record(&timing("//a:even", &[("com.A", 200.0), ("com.B", 100.0), ("com.C", 100.0)]));
    plan.record(&timing(
        "//a:light_classes",
        &[("com.A", 100.0), ("com.B", 100.0), ("com.C", 100.0)],
    ));

    assert_eq!(plan.shards_of("//a:one_class"), SINGLE_SHARDS);
    assert_eq!(plan.shards_of("//a:one_heavy_class"), 2);
    assert_eq!(plan.shards_of("//a:even"), 2);
    assert_eq!(plan.shards_of("//a:light_classes"), 3);
}

/// A plan line names a nested class apart from its top-level class. The runner weighs the top-level class, so the count
/// does too: the record of a top-level class holds its nested classes, and a top-level class without a record weighs as
/// much as its nested classes.
#[test]
fn a_nested_class_counts_in_its_top_level_class() {
    let mut plan = ShardPlan::default();
    plan.record(&timing(
        "//a:outer_without_record",
        &[("com.A$X", 200.0), ("com.A$Y", 200.0), ("com.B", 10.0)],
    ));
    plan.record(&timing(
        "//a:outer_with_record",
        &[
            ("com.A", 300.0),
            ("com.A$Inner", 290.0),
            ("com.A$Inner$Deep", 280.0),
            ("com.B", 100.0),
        ],
    ));
    plan.record(&timing(
        "//a:nested_of_three_classes",
        &[("com.A$X", 100.0), ("com.B$X", 100.0), ("com.C$X", 100.0)],
    ));

    // 410 s over the 400 s of com.A
    assert_eq!(plan.shards_of("//a:outer_without_record"), 2);
    // 400 s over the 300 s of com.A, without the seconds of its nested classes
    assert_eq!(plan.shards_of("//a:outer_with_record"), 2);
    // three top-level classes of 100 s each
    assert_eq!(plan.shards_of("//a:nested_of_three_classes"), 3);
}

/// A heavy target is one that wants more than the planned count, and the run names only the ones its patterns cover.
#[test]
fn the_heavy_targets_are_the_covered_targets_that_want_more_than_the_planned_count() {
    let mut plan = ShardPlan::default();
    plan.record(&classes("//p/a:light_test", &[90.0, 90.0]));
    plan.record(&classes("//p/a:heavy_test", &[61.0, 60.0, 60.0]));
    plan.record(&classes("//p/b/c:heavy_test", &[100.0; 4]));
    plan.record(&classes("//q:heavy_test", &[100.0; 4]));
    plan.record(&classes("@@community+//tools/bt:heavy_test", &[100.0; 4]));
    // one class of 400 s runs whole on one shard
    plan.record(&classes("//p/a:one_class_test", &[400.0]));
    let heavy = |patterns: &[&str]| plan.heavy_targets(&patterns.iter().map(|pattern| (*pattern).to_owned()).collect::<Vec<_>>());

    assert_eq!(heavy(&["//p/..."]), ["//p/a:heavy_test", "//p/b/c:heavy_test"]);
    assert_eq!(heavy(&["//..."]), ["//p/a:heavy_test", "//p/b/c:heavy_test", "//q:heavy_test"]);
    assert_eq!(heavy(&["//p/b/...:all"]), ["//p/b/c:heavy_test"]);
    assert_eq!(heavy(&["//p/a:all"]), ["//p/a:heavy_test"]);
    assert_eq!(heavy(&["//p/a:*"]), ["//p/a:heavy_test"]);
    assert_eq!(heavy(&["//p/a:heavy_test"]), ["//p/a:heavy_test"]);
    assert_eq!(heavy(&["//p/a:light_test"]), Vec::<String>::new());
    assert_eq!(heavy(&["//q"]), Vec::<String>::new());
    assert_eq!(heavy(&["//p/a/..."]), ["//p/a:heavy_test"]);
    // a sibling package with the same prefix is not under the pattern
    assert_eq!(heavy(&["//p/b/c/..."]), ["//p/b/c:heavy_test"]);
    assert_eq!(heavy(&["//p/b/..."]), ["//p/b/c:heavy_test"]);
    assert_eq!(heavy(&["//p/b/c:heavy"]), Vec::<String>::new());
    // the apparent and the canonical name of a repository are one repository
    assert_eq!(heavy(&["@community//tools/bt/..."]), ["@@community+//tools/bt:heavy_test"]);
    assert_eq!(heavy(&["//tools/bt/..."]), Vec::<String>::new());
    // a later negative pattern removes what it covers, and a later positive one adds it again
    assert_eq!(heavy(&["//p/...", "-//p/b/..."]), ["//p/a:heavy_test"]);
    assert_eq!(
        heavy(&["//p/...", "-//p/b/...", "//p/b/c:heavy_test"]),
        ["//p/a:heavy_test", "//p/b/c:heavy_test"]
    );
    assert_eq!(heavy(&["p/..."]), Vec::<String>::new());
}

#[test]
fn the_single_targets_are_the_covered_targets_that_want_one_shard() {
    let mut plan = ShardPlan::default();
    plan.record(&timing("//p/a:single_test", &[("A", 90.0)]));
    plan.record(&classes("//p/a:medium_test", &[90.0, 90.0]));
    plan.record(&classes("//p/b:heavy_test", &[61.0, 60.0, 60.0]));
    // one class of 400 s runs whole on one shard
    plan.record(&classes("//p/b:one_class_test", &[400.0]));
    plan.record(&timing("@@community+//tools/bt:single_test", &[("A", 5.0)]));
    let single = |patterns: &[&str]| plan.single_targets(&patterns.iter().map(|pattern| (*pattern).to_owned()).collect::<Vec<_>>());

    assert_eq!(single(&["//p/..."]), ["//p/a:single_test", "//p/b:one_class_test"]);
    assert_eq!(single(&["//p/a:all"]), ["//p/a:single_test"]);
    assert_eq!(single(&["@community//tools/bt/..."]), ["@@community+//tools/bt:single_test"]);
    assert_eq!(single(&["//p/...", "-//p/a:single_test"]), ["//p/b:one_class_test"]);
    assert_eq!(
        single(&["//p/a/...", "-//p/a:single_test", "//p/a:single_test"]),
        ["//p/a:single_test"]
    );
    assert_eq!(single(&["//p/a:unknown_test"]), Vec::<String>::new());
}

/// A plan entry is a hint: the groups name only the covered targets that the current tree has.
#[test]
fn the_groups_are_the_covered_targets_that_the_tree_has() {
    let mut plan = ShardPlan::default();
    plan.record(&classes("//p/a:heavy_test", &[100.0; 4]));
    plan.record(&classes("//p/gone:heavy_test", &[100.0; 4]));
    plan.record(&timing("//p/a:single_test", &[("A", 5.0)]));
    plan.record(&timing("@@community+//tools/bt:single_test", &[("A", 5.0)]));
    plan.record(&timing("@@community+//tools/bt:gone_test", &[("A", 5.0)]));
    plan.record(&classes("//q:heavy_test", &[100.0; 4]));
    let patterns = vec!["//p/...".to_owned(), "@community//tools/bt/...".to_owned()];
    let tree = TreeTargets::parse(
        [
            "//p/a:heavy_test",
            "//p/a:single_test",
            "//p/a:medium_test",
            "@community//tools/bt:single_test",
            "//q:heavy_test",
            "",
        ]
        .map(str::to_owned),
    );

    let answer = |tree: &TreeTargets| plan.groups(&patterns, || Ok::<_, ()>(Some(tree.clone())));
    assert_eq!(
        answer(&tree),
        Ok(Groups {
            heavy: vec!["//p/a:heavy_test".to_owned()],
            single: vec!["//p/a:single_test".to_owned(), "@@community+//tools/bt:single_test".to_owned()],
            gone: vec!["//p/gone:heavy_test".to_owned(), "@@community+//tools/bt:gone_test".to_owned()],
        })
    );
    // a tree without the targets of the plan gives no group, and each covered target is gone
    let none = answer(&TreeTargets::default()).expect("an answer");
    assert!(none.heavy.is_empty() && none.single.is_empty(), "{none:?}");
    assert_eq!(none.gone.len(), 5, "{none:?}");
    // a query without an answer gives no group, and no target is gone
    assert_eq!(plan.groups(&patterns, || Ok::<_, ()>(None)), Ok(Groups::default()));
    // an error of the query is the answer
    assert_eq!(plan.groups(&patterns, || Err("interrupted")), Err("interrupted"));
}

/// libtest runs the tests of a Rust target in parallel threads, and each shard pays the shared setup again. So each Rust
/// test target of the tree is a single-shard target, with a record or without, under the label of the query.
#[test]
fn a_rust_test_target_is_a_single_shard_target_whatever_its_record() {
    let mut plan = ShardPlan::default();
    plan.record(&classes("//p/a:heavy_rust_test", &[100.0; 4]));
    plan.record(&classes("//p/a:medium_rust_test", &[60.0, 60.0]));
    plan.record(&timing("@@community+//tools/bt:single_rust_test", &[("A", 5.0)]));
    plan.record(&classes("//p/a:medium_test", &[60.0, 60.0]));
    let tree = TreeTargets::parse(
        [
            "_rust_test_junit_test rule //p/a:heavy_rust_test",
            "_rust_test_junit_test rule //p/a:medium_rust_test",
            "rust_test rule //p/b:new_rust_test",
            "rust_test rule @community//tools/bt:single_rust_test",
            "java_test rule //p/a:medium_test",
        ]
        .map(str::to_owned),
    );
    let patterns = vec!["//p/...".to_owned(), "@community//tools/bt/...".to_owned()];
    assert_eq!(
        plan.groups(&patterns, || Ok::<_, ()>(Some(tree.clone()))),
        Ok(Groups {
            heavy: Vec::new(),
            single: vec![
                "//p/a:heavy_rust_test".to_owned(),
                "//p/a:medium_rust_test".to_owned(),
                "//p/b:new_rust_test".to_owned(),
                "@community//tools/bt:single_rust_test".to_owned(),
            ],
            gone: Vec::new(),
        })
    );
}

/// A Rust test target can have no record, so a plan without a covered heavy or single-shard target still asks for the
/// tree.
#[test]
fn the_groups_ask_for_the_tree_also_without_a_covered_target_of_the_plan() {
    let plan = ShardPlan::default();
    let tree = TreeTargets::parse(["rust_test rule //p:rust_test".to_owned()]);
    assert_eq!(
        plan.groups(&["//p/...".to_owned()], || Ok::<_, ()>(Some(tree))),
        Ok(Groups {
            single: vec!["//p:rust_test".to_owned()],
            ..Groups::default()
        })
    );
}

#[test]
fn the_tree_query_names_the_test_targets_of_the_patterns_in_their_order() {
    let query = |patterns: &[&str]| TreeTargets::query(&patterns.iter().map(|pattern| (*pattern).to_owned()).collect::<Vec<_>>());
    assert_eq!(
        query(&["//p/...", "@@community+//tools/bt/...", "-//p/b/...", "//p/b/c:t"]),
        r#"tests(set() + (tests("//p/...") except attr(tags, "\bmanual\b", tests("//p/..."))) + (tests("@@community+//tools/bt/...") except attr(tags, "\bmanual\b", tests("@@community+//tools/bt/..."))) - "//p/b/..." + "//p/b/c:t")"#
    );
    // a negative pattern first removes from nothing, as in `bazel test`
    assert_eq!(query(&["-//p/..."]), r#"tests(set() - "//p/...")"#);
    assert_eq!(query(&[]), "tests(set())");
}

/// `bazel test` leaves out a `manual` target of a wildcard pattern, and runs a `manual` target that a pattern names by its
/// label. So only a wildcard pattern drops the `manual` targets from the answer.
#[test]
fn the_tree_query_keeps_a_manual_target_that_a_pattern_names_by_its_label() {
    let query = |pattern: &str| TreeTargets::query(&[pattern.to_owned()]);
    for explicit in [
        "//build/tests/smoke:all-products-packaging_test",
        "//p",
        "@community//p:t",
        "//p:all_tests",
    ] {
        assert_eq!(query(explicit), format!(r#"tests(set() + "{explicit}")"#), "{explicit}");
    }
    for wildcard in [
        "//...",
        "//p/...",
        "//p/...:all",
        "//p:all",
        "//p:*",
        "//p:all-targets",
        "@@community+//p/...",
    ] {
        assert_eq!(
            query(wildcard),
            format!(r#"tests(set() + (tests("{wildcard}") except attr(tags, "\bmanual\b", tests("{wildcard}"))))"#),
            "{wildcard}"
        );
    }
}

/// A `manual` target that the run names by its label is in the answer of the query. So a record of it is no gone target,
/// and the target takes the count of its group.
#[test]
fn a_manual_target_that_the_run_names_is_not_gone() {
    let mut plan = ShardPlan::default();
    plan.record(&timing("//build/tests/smoke:all-products-packaging_test", &[("A", 400.0)]));
    let patterns = vec!["//build/tests/smoke:all-products-packaging_test".to_owned()];
    assert!(!TreeTargets::query(&patterns).contains("manual"));
    let tree = TreeTargets::parse(["java_test rule //build/tests/smoke:all-products-packaging_test".to_owned()]);
    assert_eq!(
        plan.groups(&patterns, || Ok::<_, ()>(Some(tree))),
        Ok(Groups {
            single: vec!["//build/tests/smoke:all-products-packaging_test".to_owned()],
            ..Groups::default()
        })
    );
}

#[test]
fn the_tree_has_a_target_under_either_name_of_its_repository() {
    let tree = TreeTargets::parse(["@community//tools/bt:t".to_owned(), "//p:t".to_owned(), "no label".to_owned()]);
    assert!(tree.has("@@community+//tools/bt:t"));
    assert!(tree.has("@community//tools/bt:t"));
    assert!(tree.has("//p:t"));
    assert!(!tree.has("//p:other"));
    assert!(!tree.has("//tools/bt:t"));
    assert!(!tree.has("no label"));
}

#[test]
fn the_tree_reads_the_rule_kind_of_each_target() {
    let tree = TreeTargets::parse(
        [
            "_rust_test_junit_test rule //p:junit_rust",
            "rust_test rule @community//tools/bt:plain_rust",
            "rust_clippy_test rule //p:clippy",
            "java_test rule //p:java",
            "//p:unknown",
        ]
        .map(str::to_owned),
    );
    for label in [
        "//p:junit_rust",
        "@@community+//tools/bt:plain_rust",
        "//p:clippy",
        "//p:java",
        "//p:unknown",
    ] {
        assert!(tree.has(label), "{label}");
    }
    assert!(tree.is_rust_test("//p:junit_rust"));
    assert!(tree.is_rust_test("@@community+//tools/bt:plain_rust"));
    assert!(!tree.is_rust_test("//p:clippy"));
    assert!(!tree.is_rust_test("//p:java"));
    assert!(!tree.is_rust_test("//p:unknown"));
}

#[test]
fn the_rendered_plan_has_the_lines_the_runner_reads() {
    let mut plan = ShardPlan::default();
    plan.record(&timing("//a:t", &[("com.B", 100.0), ("com.A", 1.5)]));
    plan.mark_seen(["//a:t"], 20_418);

    assert_eq!(
        plan.render(),
        "# The shard plan of bt. bt rewrites this file after each planned run.\n\
         default\t2\n\
         target\t//a:t\t2\n\
         class\t//a:t\tcom.A\t1.500\n\
         class\t//a:t\tcom.B\t100.000\n\
         seen\t//a:t\t20418\n"
    );
}

#[test]
fn a_plan_reads_back_what_it_wrote_and_skips_the_lines_it_does_not_know() {
    let mut plan = ShardPlan::default();
    plan.record(&timing("//a:t", &[("com.A", 1.5), ("com.B", 2.25)]));
    plan.record(&timing("//b:t", &[("com.C", 3.0)]));
    assert_eq!(ShardPlan::parse(&plan.render()), plan);

    let text = "# a comment\nfuture\t//a:t\tx\nclass\t//a:t\tcom.A\tmany\nclass\t//a:t\tcom.B\nclass\t//a:t\tcom.C\t4\n";
    assert_eq!(ShardPlan::parse(text), {
        let mut expected = ShardPlan::default();
        expected.record(&timing("//a:t", &[("com.C", 4.0)]));
        expected
    });
}

/// The shared plan keeps its own record of a target, and takes each target that only the other plan names.
#[test]
fn a_plan_adds_only_the_targets_it_has_no_record_of() {
    let mut shared = ShardPlan::default();
    shared.record(&timing("//a:both", &[("A", 150.0), ("B", 150.0)]));
    let mut copy = ShardPlan::default();
    copy.record(&timing("//a:both", &[("A", 5.0)]));
    copy.record(&timing("//a:old", &[("B", 7.0)]));

    assert!(shared.add_missing_targets(&copy));
    assert_eq!(shared.shards_of("//a:both"), 2);
    assert_eq!(
        shared.class_times().collect::<Vec<_>>(),
        [("//a:both", "A", 150.0), ("//a:both", "B", 150.0), ("//a:old", "B", 7.0)]
    );
    assert!(!shared.add_missing_targets(&copy));
}

#[test]
fn a_record_replaces_the_classes_of_its_own_target_only() {
    let mut plan = ShardPlan::default();
    plan.record(&timing("//a:t", &[("com.Gone", 500.0)]));
    plan.record(&timing("//b:t", &[("com.Kept", 1.0)]));
    plan.record(&timing("//a:t", &[("com.New", 1.0)]));

    assert_eq!(plan, {
        let mut expected = ShardPlan::default();
        expected.record(&timing("//b:t", &[("com.Kept", 1.0)]));
        expected.record(&timing("//a:t", &[("com.New", 1.0)]));
        expected
    });
    assert_eq!(plan.shards_of("//a:t"), 1);
}

#[test]
fn a_heavy_target_has_its_wanted_count_in_the_rendered_plan() {
    let mut plan = ShardPlan::default();
    plan.record(&classes("//a:t", &[90.0, 90.0, 90.0, 90.0, 90.0, 80.0]));
    assert!(plan.render().contains("target\t//a:t\t6\n"), "{}", plan.render());
}

/// A target that times out never passes, so its old record would keep it light in each run. Its times of the run raise
/// the record only when the raise makes it heavy, because only then does each of its shards rerun under another key.
#[test]
fn a_shard_timeout_raises_the_record_only_when_the_target_becomes_heavy() {
    let mut plan = ShardPlan::default();
    plan.record(&timing("//a:t", &[("com.A", 100.0), ("com.B", 50.0), ("com.C", 10.0)]));
    assert_eq!(plan.shards_of("//a:t"), PLANNED_SHARDS);

    let before = plan.clone();
    assert!(
        !plan.record_lower_bound(&timing("//a:t", &[("com.A", 110.0), ("com.B", 5.0)])),
        "170 s over a class of 110 s stay light"
    );
    assert_eq!(plan, before);

    assert!(plan.record_lower_bound(&timing("//a:t", &[("com.A", 267.0), ("com.D", 263.0)])));
    assert_eq!(plan, {
        let mut expected = ShardPlan::default();
        expected.record(&timing(
            "//a:t",
            &[("com.A", 267.0), ("com.B", 50.0), ("com.C", 10.0), ("com.D", 263.0)],
        ));
        expected
    });
    assert_eq!(plan.shards_of("//a:t"), 3);

    // a heavy target keeps its record: a raise would change the split of its cached shards
    let heavy = plan.clone();
    assert!(!plan.record_lower_bound(&timing("//a:t", &[("com.A", 700.0)])));
    assert_eq!(plan, heavy);

    // a target without a record becomes heavy as well
    assert!(plan.record_lower_bound(&timing("//b:t", &[("com.E", 100.0), ("com.F", 100.0), ("com.G", 100.0)])));
    assert_eq!(plan.shards_of("//b:t"), 3);

    // one class runs whole on one shard, so a long class alone never makes a target heavy
    assert!(!plan.record_lower_bound(&timing("//c:t", &[("com.Slow", 600.0)])));
    assert_eq!(plan.shards_of("//c:t"), DEFAULT_SHARDS);
}

/// The fixed seconds of a target travel in a line of their own, which an older bt skips.
#[test]
fn the_fixed_seconds_of_a_target_read_back_and_a_record_without_them_keeps_the_old_value() {
    let mut plan = ShardPlan::default();
    plan.record(&TargetTiming {
        fixed_seconds: Some(8.5),
        ..timing("//a:t", &[("com.A", 1.0)])
    });
    assert!(
        plan.render().contains("class\t//a:t\tcom.A\t1.000\nfixed\t//a:t\t8.500\n"),
        "{}",
        plan.render()
    );
    assert_eq!(ShardPlan::parse(&plan.render()), plan);
    assert_eq!(plan.fixed_of("//a:t"), Some(8.5));

    plan.record(&timing("//a:t", &[("com.A", 2.0)]));
    assert_eq!(plan.fixed_of("//a:t"), Some(8.5));
    assert_eq!(ShardPlan::parse("fixed\t//a:t\tmany\nfixed\t//a:t\n").fixed_of("//a:t"), None);
}

/// The day of the last run of a target travels in a line of its own, which the runner and an older bt skip.
#[test]
fn the_day_of_a_target_reads_back_and_a_bad_day_is_skipped() {
    let mut plan = ShardPlan::default();
    plan.record(&timing("//a:t", &[("com.A", 1.0)]));
    plan.record(&timing("//b:t", &[("com.B", 1.0)]));
    plan.mark_seen(["//a:t", "//c:no_record"], 7);
    let text = plan.render();
    assert!(text.contains("class\t//a:t\tcom.A\t1.000\nseen\t//a:t\t7\n"), "{text}");
    // a label without a record gets no day, and a target that no run named has none yet
    assert!(!text.contains("//c:no_record") && !text.contains("seen\t//b:t"), "{text}");
    assert_eq!(ShardPlan::parse(&text), plan);

    let bad = ShardPlan::parse("class\t//a:t\tcom.A\t1.000\nseen\t//a:t\tyesterday\nseen\t//a:t\n");
    assert_eq!(bad, ShardPlan::parse("class\t//a:t\tcom.A\t1.000\n"));
}

/// An older bt writes no day. Its targets count as seen on the day of the next update, so none of them leaves at once.
#[test]
fn a_target_without_a_day_gets_the_day_of_the_update() {
    let mut plan = ShardPlan::parse("class\t//a:t\tcom.A\t1.000\nclass\t//b:t\tcom.B\t1.000\nseen\t//b:t\t90\n");
    plan.forget_older_than(100, PLAN_TARGET_TTL_DAYS);
    let text = plan.render();
    assert!(text.contains("seen\t//a:t\t100\n") && text.contains("seen\t//b:t\t90\n"), "{text}");
}

/// A target that no run named for more than the TTL leaves the plan with its fixed seconds and its day. A target of
/// exactly that age stays.
#[test]
fn a_target_older_than_the_ttl_leaves_the_plan_with_its_fixed_seconds() {
    let mut plan = ShardPlan::default();
    for label in ["//a:old", "//a:edge", "//a:new"] {
        plan.record(&TargetTiming {
            fixed_seconds: Some(3.0),
            ..timing(label, &[("com.A", 1.0)])
        });
    }
    plan.mark_seen(["//a:old"], 85);
    plan.mark_seen(["//a:edge"], 86);
    plan.mark_seen(["//a:new"], 100);
    plan.forget_older_than(100, PLAN_TARGET_TTL_DAYS);

    assert_eq!(
        plan.class_times().map(|(label, _, _)| label).collect::<Vec<_>>(),
        ["//a:edge", "//a:new"]
    );
    assert_eq!(plan.fixed_of("//a:old"), None);
    assert_eq!(plan.fixed_of("//a:edge"), Some(3.0));
    assert!(!plan.render().contains("//a:old"), "{}", plan.render());
    // a later record of the target starts without the old day
    plan.record(&timing("//a:old", &[("com.A", 1.0)]));
    assert!(!plan.render().contains("seen\t//a:old"), "{}", plan.render());
}

/// For a target that both plans name, the merge takes the later day. A target without a day counts as seen today, so
/// it keeps no day. A target that only the other plan names comes with its day, so an old copy cannot keep a target
/// that the shared plan forgot.
#[test]
fn a_merge_takes_the_later_day_of_a_target() {
    let mut shared = ShardPlan::parse(
        "class\t//a:both\tA\t1.000\nseen\t//a:both\t50\nclass\t//a:later\tA\t1.000\nseen\t//a:later\t60\nclass\t//a:no_day\tA\t1.000\n",
    );
    let copy = ShardPlan::parse(
        "class\t//a:both\tA\t1.000\nseen\t//a:both\t70\nclass\t//a:later\tA\t1.000\nseen\t//a:later\t40\n\
         class\t//a:no_day\tA\t1.000\nseen\t//a:no_day\t10\nclass\t//a:forgotten\tA\t1.000\nfixed\t//a:forgotten\t2.000\nseen\t//a:forgotten\t3\n",
    );

    assert!(shared.add_missing_targets(&copy));
    let text = shared.render();
    for line in [
        "seen\t//a:both\t70\n",
        "seen\t//a:later\t60\n",
        "fixed\t//a:forgotten\t2.000\nseen\t//a:forgotten\t3\n",
    ] {
        assert!(text.contains(line), "no {line:?} in:\n{text}");
    }
    assert!(!text.contains("seen\t//a:no_day"), "{text}");
    assert!(!shared.add_missing_targets(&copy), "a second merge changes nothing");

    shared.forget_older_than(70, PLAN_TARGET_TTL_DAYS);
    assert_eq!(
        shared.class_times().map(|(label, _, _)| label).collect::<Vec<_>>(),
        ["//a:both", "//a:later", "//a:no_day"]
    );
}

/// A copy from before the `seen` line names a target without a day. The shared plan takes it with no claim to today, so
/// it stays only when the run names it. An undated target of the shared plan still counts as seen today.
#[test]
fn an_undated_target_of_the_copy_stays_only_when_the_run_names_it() {
    let mut shared = ShardPlan::parse("class\t//a:shared_undated\tA\t1.000\n");
    let copy = ShardPlan::parse("class\t//a:gone\tA\t1.000\nclass\t//a:live\tA\t1.000\n");

    assert!(shared.add_missing_targets(&copy));
    assert!(shared.render().contains("seen\t//a:gone\t0\n"), "{}", shared.render());
    shared.mark_seen(["//a:live"], 100);
    shared.forget_older_than(100, PLAN_TARGET_TTL_DAYS);
    assert_eq!(
        shared.class_times().map(|(label, _, _)| label).collect::<Vec<_>>(),
        ["//a:live", "//a:shared_undated"]
    );
    assert!(shared.render().contains("seen\t//a:shared_undated\t100\n"), "{}", shared.render());
}
