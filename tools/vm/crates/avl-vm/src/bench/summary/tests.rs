use std::collections::BTreeMap;

use pretty_assertions::assert_eq;

use super::{MIN_RUNS_FOR_DELTA, Stat, Summary};
use crate::bench::arm::Arm;
use crate::bench::record::{LaunchFacts, RunKind, RunRecord};
use crate::bench::session::{GenerationRef, GitInfo, SessionInfo};

fn info(arms: Vec<Arm>) -> SessionInfo {
    SessionInfo {
        command: "welcome".to_owned(),
        target: "//build:idea".to_owned(),
        git: GitInfo::default(),
        generation: GenerationRef::default(),
        arms,
        runs: 3,
        cold: false,
        hold_ms: 0,
        profile: false,
        project: None,
        project_file: None,
        additional_modules: Vec::new(),
        max_load: None,
        warnings: Vec::new(),
    }
}

fn run(arm: Arm, kind: RunKind, index: u32, value: Option<f64>) -> RunRecord {
    RunRecord {
        arm,
        kind,
        index,
        dir: String::new(),
        launch: LaunchFacts::default(),
        valid: value.is_some(),
        reason: value.is_none().then(|| "no event".to_owned()),
        notes: Vec::new(),
        metrics: value.map(|value| BTreeMap::from([("m".to_owned(), value)])).unwrap_or_default(),
        classes_by_plugin: BTreeMap::new(),
        classes_by_module: BTreeMap::new(),
        classes_by_plugin_at: BTreeMap::new(),
        edt_samples: None,
        edt_frames: Vec::new(),
        triggers: Vec::new(),
        define_share: None,
        profile: None,
    }
}

#[test]
fn the_median_of_an_even_count_is_the_mean_of_the_middle() {
    assert_eq!(
        Stat::of(&[4.0, 1.0, 3.0, 2.0]),
        Some(Stat {
            median: 2.5,
            min: 1.0,
            max: 4.0,
            count: 4
        })
    );
    assert_eq!(Stat::of(&[5.0]).map(|stat| stat.median), Some(5.0));
    assert_eq!(Stat::of(&[]), None);
}

#[test]
fn the_delta_is_non_modal_minus_modal() {
    let mut records = vec![run(Arm::Modal, RunKind::Prime, 0, Some(9999.0))];
    for index in 1..=3 {
        records.push(run(Arm::Modal, RunKind::Measured, index, Some(1000.0 + f64::from(index))));
        records.push(run(Arm::NonModal, RunKind::Measured, index, Some(1500.0 + f64::from(index))));
    }
    records.push(run(Arm::NonModal, RunKind::Measured, 4, None));
    let summary = Summary::build(&info(vec![Arm::Modal, Arm::NonModal]), "s", &records);
    assert_eq!(summary.delta, BTreeMap::from([("m".to_owned(), 500.0)]));
    assert_eq!(summary.delta_note, None);
    let modal = &summary.arms["modal"];
    assert_eq!(modal.runs.len(), 3, "the prime run counts for nothing");
    assert_eq!(
        modal.summary["m"],
        Stat {
            median: 1002.0,
            min: 1001.0,
            max: 1003.0,
            count: 3
        }
    );
    assert_eq!(summary.arms["nonModal"].valid_runs, 3);
    assert_eq!(summary.arms_without_valid_run(), []);
}

#[test]
fn too_few_valid_runs_give_no_delta_and_say_why() {
    let records = vec![
        run(Arm::Modal, RunKind::Measured, 1, Some(1.0)),
        run(Arm::NonModal, RunKind::Measured, 1, None),
    ];
    let summary = Summary::build(&info(vec![Arm::Modal, Arm::NonModal]), "s", &records);
    assert!(summary.delta.is_empty());
    assert_eq!(
        summary.delta_note.as_deref(),
        Some(format!("no delta: modal has 1 valid runs and non-modal has 0; a delta needs {MIN_RUNS_FOR_DELTA} per arm").as_str())
    );
    assert_eq!(summary.arms_without_valid_run(), vec![Arm::NonModal]);
}

/// A measured run whose main metric is `value`, or a failed run.
fn visible(arm: Arm, index: u32, value: Option<f64>) -> RunRecord {
    let mut record = run(arm, RunKind::Measured, index, None);
    if let Some(value) = value {
        record.valid = true;
        record.reason = None;
        record.metrics = BTreeMap::from([(arm.main_metric().to_owned(), value)]);
    }
    record
}

#[test]
fn the_median_run_is_a_valid_run_and_the_lower_middle_of_an_even_count() {
    let odd = vec![
        visible(Arm::NonModal, 1, Some(1549.0)),
        visible(Arm::NonModal, 2, Some(1785.0)),
        visible(Arm::NonModal, 3, None),
        visible(Arm::NonModal, 4, Some(1524.0)),
    ];
    let summary = Summary::build(&info(vec![Arm::NonModal]), "s", &odd);
    let median = summary.arms["nonModal"].median_run().map(|run| run.index);
    assert_eq!(median, Some(1), "a failed run does not count");

    let mut even = odd;
    even.push(visible(Arm::NonModal, 5, Some(1600.0)));
    let summary = Summary::build(&info(vec![Arm::NonModal]), "s", &even);
    assert_eq!(summary.arms["nonModal"].median_run().map(|run| run.index), Some(1));

    let summary = Summary::build(&info(vec![Arm::Modal]), "s", &[visible(Arm::Modal, 1, None)]);
    assert_eq!(summary.arms["modal"].median_run(), None);
}

#[test]
fn the_spread_is_the_median_absolute_deviation() {
    assert_eq!(super::spread(&[1.0, 2.0, 3.0, 4.0, 100.0]), Some(1.0));
    assert_eq!(super::spread(&[5.0]), Some(0.0));
    assert_eq!(super::spread(&[]), None);
}

#[test]
fn the_values_of_a_metric_are_those_of_the_valid_runs_in_run_order() {
    let records = vec![
        run(Arm::Modal, RunKind::Measured, 1, Some(30.0)),
        run(Arm::Modal, RunKind::Measured, 2, None),
        run(Arm::Modal, RunKind::Measured, 3, Some(10.0)),
    ];
    let summary = Summary::build(&info(vec![Arm::Modal]), "s", &records);
    let modal = &summary.arms["modal"];
    assert_eq!(modal.values("m"), vec![30.0, 10.0]);
    assert_eq!(modal.values("other"), Vec::<f64>::new());
}
