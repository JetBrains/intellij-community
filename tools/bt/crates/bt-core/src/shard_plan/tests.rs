use pretty_assertions::assert_eq;

use super::*;

fn timing(label: &str, classes: &[(&str, f64)]) -> TargetTiming {
    TargetTiming {
        label: label.to_owned(),
        class_seconds: classes.iter().map(|(class, seconds)| ((*class).to_owned(), *seconds)).collect(),
    }
}

#[test]
fn a_target_gets_a_shard_per_90_recorded_seconds_up_to_the_heavy_count() {
    let mut plan = ShardPlan::default();
    plan.record(&timing("//a:small", &[("A", 5.0)]));
    plan.record(&timing("//a:full", &[("A", 45.0), ("B", 45.0)]));
    plan.record(&timing("//a:medium", &[("A", 60.0), ("B", 60.0)]));
    plan.record(&timing("//a:heavy", &[("A", 530.0)]));
    plan.record(&timing("//a:heaviest", &[("A", 2000.0)]));
    plan.record(&timing("//a:empty", &[]));

    assert_eq!(plan.shards_of("//a:small"), 1);
    assert_eq!(plan.shards_of("//a:full"), 1);
    assert_eq!(plan.shards_of("//a:medium"), PLANNED_SHARDS);
    assert_eq!(plan.shards_of("//a:heavy"), 6);
    assert_eq!(plan.shards_of("//a:heaviest"), HEAVY_SHARDS);
    assert_eq!(plan.shards_of("//a:empty"), 1);
    assert_eq!(plan.shards_of("//a:unknown"), DEFAULT_SHARDS);
}

/// A heavy target is one that wants more than the planned count, and the run names only the ones its patterns cover.
#[test]
fn the_heavy_targets_are_the_covered_targets_that_want_more_than_the_planned_count() {
    let mut plan = ShardPlan::default();
    plan.record(&timing("//p/a:light_test", &[("A", 180.0)]));
    plan.record(&timing("//p/a:heavy_test", &[("A", 181.0)]));
    plan.record(&timing("//p/b/c:heavy_test", &[("A", 400.0)]));
    plan.record(&timing("//q:heavy_test", &[("A", 400.0)]));
    plan.record(&timing("@@community+//tools/bt:heavy_test", &[("A", 400.0)]));
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
fn the_rendered_plan_has_the_lines_the_runner_reads() {
    let mut plan = ShardPlan::default();
    plan.record(&timing("//a:t", &[("com.B", 100.0), ("com.A", 1.5)]));

    assert_eq!(
        plan.render(),
        "# The shard plan of bt. bt rewrites this file after each multi-target run.\n\
         default\t2\n\
         target\t//a:t\t2\n\
         class\t//a:t\tcom.A\t1.500\n\
         class\t//a:t\tcom.B\t100.000\n"
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
    plan.record(&timing("//a:t", &[("com.A", 530.0)]));
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
        "160 s stay light"
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
    assert_eq!(plan.shards_of("//a:t"), 7);

    // a heavy target keeps its record: a raise would change the split of its cached shards
    let heavy = plan.clone();
    assert!(!plan.record_lower_bound(&timing("//a:t", &[("com.A", 700.0)])));
    assert_eq!(plan, heavy);

    // a target without a record becomes heavy as well
    assert!(plan.record_lower_bound(&timing("//b:t", &[("com.E", 300.0)])));
    assert_eq!(plan.shards_of("//b:t"), 4);
}
