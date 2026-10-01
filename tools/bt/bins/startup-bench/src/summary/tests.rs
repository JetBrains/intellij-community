use std::collections::BTreeMap;

use pretty_assertions::assert_eq;

use super::{MIN_RUNS_FOR_DELTA, Stat, Summary};
use crate::arm::Arm;
use crate::record::{LaunchFacts, RunKind, RunRecord};
use crate::session::{GitInfo, SessionInfo};

fn info(arms: Vec<Arm>) -> SessionInfo {
    SessionInfo {
        command: "welcome".to_owned(),
        target: "//build:idea".to_owned(),
        git: GitInfo::default(),
        arms,
        runs: 3,
        cold: false,
        hold_ms: 0,
        profile: false,
        project: None,
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
        top_plugins: Vec::new(),
        top_modules: Vec::new(),
        edt_samples: None,
        edt_frames: Vec::new(),
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
    assert!(summary.arms_without_valid_run().is_empty());
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
