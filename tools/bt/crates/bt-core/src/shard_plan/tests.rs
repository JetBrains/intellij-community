use pretty_assertions::assert_eq;

use super::*;

fn timing(label: &str, classes: &[(&str, f64)]) -> TargetTiming {
    TargetTiming {
        label: label.to_owned(),
        class_seconds: classes.iter().map(|(class, seconds)| ((*class).to_owned(), *seconds)).collect(),
    }
}

#[test]
fn a_target_gets_a_shard_per_90_recorded_seconds_up_to_the_planned_count() {
    let mut plan = ShardPlan::default();
    plan.record(&timing("//a:small", &[("A", 5.0)]));
    plan.record(&timing("//a:full", &[("A", 45.0), ("B", 45.0)]));
    plan.record(&timing("//a:medium", &[("A", 60.0), ("B", 60.0)]));
    plan.record(&timing("//a:heavy", &[("A", 600.0)]));
    plan.record(&timing("//a:empty", &[]));

    assert_eq!(plan.shards_of("//a:small"), 1);
    assert_eq!(plan.shards_of("//a:full"), 1);
    assert_eq!(plan.shards_of("//a:medium"), 2);
    assert_eq!(plan.shards_of("//a:heavy"), PLANNED_SHARDS);
    assert_eq!(plan.shards_of("//a:empty"), 1);
    assert_eq!(plan.shards_of("//a:unknown"), DEFAULT_SHARDS);
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
