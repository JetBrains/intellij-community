use std::collections::BTreeSet;

use pretty_assertions::assert_eq;

use super::*;
use crate::fake::timing;

fn plan() -> ShardPlan {
    let mut plan = ShardPlan::default();
    plan.record(&timing("//air:tests", &[("com.air.SlowTest", 35.4), ("com.air.AtLimitTest", 30.0)]));
    plan
}

fn budget() -> ClassTimeBudget {
    ClassTimeBudget {
        seconds: 30,
        baseline: "plugins/air/tests/integration/class-time-budget.tsv".to_owned(),
    }
}

fn target_labels() -> BTreeSet<String> {
    BTreeSet::from(["//air:tests".to_owned()])
}

#[test]
fn a_class_over_the_limit_without_a_reason_is_a_violation() {
    let baseline = ClassTimeBudgetBaseline::parse("").expect("an empty baseline is valid");
    let violations = budget().violations(&plan(), &target_labels(), &baseline);

    assert_eq!(violations.len(), 1);
    assert_eq!(violations[0].target_label, "//air:tests");
    assert_eq!(violations[0].class_name, "com.air.SlowTest");
    assert!((violations[0].seconds - 35.4).abs() < 0.000_1);
    assert_eq!(violations[0].summary(), "com.air.SlowTest in //air:tests: 35.4s > 30s");
}

#[test]
fn a_class_over_the_limit_with_a_reason_is_allowed() {
    let baseline = ClassTimeBudgetBaseline::parse("com.air.SlowTest\tUses a costly project fixture\n").expect("baseline parses");

    assert_eq!(
        budget().violations(&plan(), &target_labels(), &baseline),
        Vec::<ClassTimeBudgetViolation>::new()
    );
}

#[test]
fn a_class_at_the_limit_is_allowed() {
    let plan = ShardPlan::default();
    let mut plan = plan;
    plan.record(&timing("//air:tests", &[("com.air.AtLimitTest", 30.0)]));
    let baseline = ClassTimeBudgetBaseline::default();

    assert_eq!(
        budget().violations(&plan, &target_labels(), &baseline),
        Vec::<ClassTimeBudgetViolation>::new()
    );
}

#[test]
fn an_unselected_target_does_not_violate_the_budget() {
    let baseline = ClassTimeBudgetBaseline::default();
    let target_labels = BTreeSet::from(["//air:other".to_owned()]);

    assert_eq!(
        budget().violations(&plan(), &target_labels, &baseline),
        Vec::<ClassTimeBudgetViolation>::new()
    );
}

#[test]
fn a_non_jvm_suite_identifier_can_have_a_baseline_reason() {
    let suite = "@@community+//tools/vm/crates/avl-vm:avl-vm-test in @@community+//tools/vm/crates/avl-vm:avl-vm-test";
    let baseline = ClassTimeBudgetBaseline::parse(&format!("{suite}\tThe Rust runner reports one suite for this target.\n"))
        .expect("the suite identifier parses");
    let mut plan = ShardPlan::default();
    plan.record(&timing("@@community+//tools/vm/crates/avl-vm:avl-vm-test", &[(suite, 41.8)]));
    let target_labels = BTreeSet::from(["@@community+//tools/vm/crates/avl-vm:avl-vm-test".to_owned()]);

    assert_eq!(
        budget().violations(&plan, &target_labels, &baseline),
        Vec::<ClassTimeBudgetViolation>::new()
    );
}

#[test]
fn a_baseline_requires_unique_class_names_and_reasons() {
    assert_eq!(
        ClassTimeBudgetBaseline::parse("com.air.SlowTest\n").unwrap_err(),
        "line 1 has no tab between class name and reason"
    );
    assert_eq!(
        ClassTimeBudgetBaseline::parse("com.air.SlowTest\t   \n").unwrap_err(),
        "line 1 has no single-line reason"
    );
    assert_eq!(
        ClassTimeBudgetBaseline::parse("com.air.SlowTest\tfirst reason\ncom.air.SlowTest\tsecond reason\n").unwrap_err(),
        "line 2 repeats class com.air.SlowTest"
    );
}

#[test]
fn a_budget_needs_a_positive_limit_and_a_repo_relative_path() {
    let mut budget = budget();
    budget.seconds = 0;
    assert_eq!(budget.validate().unwrap_err(), "classTimeBudget.seconds must be greater than zero");

    for path in [
        "",
        "/tmp/budget.tsv",
        "../budget.tsv",
        "plugins/../budget.tsv",
        "plugins\\budget.tsv",
    ] {
        let budget = ClassTimeBudget {
            seconds: 30,
            baseline: path.to_owned(),
        };
        assert!(budget.validate().is_err(), "accepted {path:?}");
    }
}
