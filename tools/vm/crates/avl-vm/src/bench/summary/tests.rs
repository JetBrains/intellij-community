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
        measure_model: None,
        arms,
        runs: 3,
        cold: false,
        hold_ms: 0,
        profile: false,
        profile_event: crate::bench::profile::ProfileEvent::Cpu,
        project: None,
        project_file: None,
        suppressed_plugin: None,
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
        class_maps: crate::bench::record::ClassMaps::default(),
        profile_scope: None,
        excluded_samples: None,
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
    let mut records = vec![run(Arm::MODAL, RunKind::Prime, 0, Some(9999.0))];
    for index in 1..=3 {
        records.push(run(Arm::MODAL, RunKind::Measured, index, Some(1000.0 + f64::from(index))));
        records.push(run(Arm::NON_MODAL, RunKind::Measured, index, Some(1500.0 + f64::from(index))));
    }
    records.push(run(Arm::NON_MODAL, RunKind::Measured, 4, None));
    let summary = Summary::build(&info(vec![Arm::MODAL, Arm::NON_MODAL]), "s", &records);
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
        run(Arm::MODAL, RunKind::Measured, 1, Some(1.0)),
        run(Arm::NON_MODAL, RunKind::Measured, 1, None),
    ];
    let summary = Summary::build(&info(vec![Arm::MODAL, Arm::NON_MODAL]), "s", &records);
    assert!(summary.delta.is_empty());
    assert_eq!(
        summary.delta_note.as_deref(),
        Some(format!("no delta: modal has 1 valid runs and non-modal has 0; a delta needs {MIN_RUNS_FOR_DELTA} per arm").as_str())
    );
    assert_eq!(summary.arms_without_valid_run(), vec![Arm::NON_MODAL]);
}

/// A measured run whose main metric is `value`, or a failed run.
fn visible(arm: &Arm, index: u32, value: Option<f64>) -> RunRecord {
    let mut record = run(arm.clone(), RunKind::Measured, index, None);
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
        visible(&Arm::NON_MODAL, 1, Some(1549.0)),
        visible(&Arm::NON_MODAL, 2, Some(1785.0)),
        visible(&Arm::NON_MODAL, 3, None),
        visible(&Arm::NON_MODAL, 4, Some(1524.0)),
    ];
    let summary = Summary::build(&info(vec![Arm::NON_MODAL]), "s", &odd);
    let median = summary.arms["nonModal"].median_run().map(|run| run.index);
    assert_eq!(median, Some(1), "a failed run does not count");

    let mut even = odd;
    even.push(visible(&Arm::NON_MODAL, 5, Some(1600.0)));
    let summary = Summary::build(&info(vec![Arm::NON_MODAL]), "s", &even);
    assert_eq!(summary.arms["nonModal"].median_run().map(|run| run.index), Some(1));

    let summary = Summary::build(&info(vec![Arm::MODAL]), "s", &[visible(&Arm::MODAL, 1, None)]);
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
        run(Arm::MODAL, RunKind::Measured, 1, Some(30.0)),
        run(Arm::MODAL, RunKind::Measured, 2, None),
        run(Arm::MODAL, RunKind::Measured, 3, Some(10.0)),
    ];
    let summary = Summary::build(&info(vec![Arm::MODAL]), "s", &records);
    let modal = &summary.arms["modal"];
    assert_eq!(modal.values("m"), vec![30.0, 10.0]);
    assert_eq!(modal.values("other"), Vec::<f64>::new());
}

/// A twin of `--suppress-plugin` is one more arm: `replay` reads its run directories, the summary keys it, the digest
/// shows its column after the arm of its kind, `compare` gives it a table, and `ls` lists it after its arm.
#[test]
fn a_twin_arm_is_an_arm_of_the_summary_the_digest_compare_and_ls() {
    use avl_base::Reporter;

    use crate::bench::arm::in_arm_order;
    use crate::bench::command::replay;
    use crate::bench::compare::{self, Compared};
    use crate::bench::digest;
    use crate::bench::files;
    use crate::bench::session::{SESSION_FILE, read_info};
    use crate::bench::testing::fixture_session;

    let air = "com.intellij.air";
    let twin = Arm::PROJECT.without(air);
    let (_dir, session) = fixture_session();
    avl_testkit::traces::copy_tree(
        &session.join("project-run-01"),
        &session.join("project-without-com.intellij.air-run-01"),
    );
    let mut info = read_info(&session).expect("session.json");
    info.command = "project".to_owned();
    info.arms = vec![Arm::PROJECT, twin.clone()];
    info.suppressed_plugin = Some(air.to_owned());
    files::write_json(&session.join(SESSION_FILE), &info).expect("session.json");
    let session_json = std::fs::read_to_string(session.join(SESSION_FILE)).expect("session.json");
    assert!(session_json.contains(r#""suppressedPlugin": "com.intellij.air""#), "{session_json}");
    assert!(session_json.contains(r#""project-without-com.intellij.air""#), "{session_json}");

    let (reporter, _, _) = Reporter::in_memory("vm");
    let summary = replay(&session, &reporter).expect("a summary");
    assert_eq!(
        summary.arms.keys().map(String::as_str).collect::<Vec<_>>(),
        ["project", "project-without-com.intellij.air"]
    );
    assert_eq!(summary.arms["project-without-com.intellij.air"].arm, twin);
    assert_eq!(summary.arms["project-without-com.intellij.air"].valid_runs, 1);
    let text = digest::render(&summary);
    let header = text.lines().find(|line| line.contains("valid")).expect("the header");
    assert!(
        header.find("project 1/1 valid") < header.find("project-without-com.intellij.air 1/1 valid"),
        "{header}"
    );
    assert_eq!(
        in_arm_order(&summary.arms).into_iter().map(|(arm, _)| arm).collect::<Vec<_>>(),
        [Arm::PROJECT, twin.clone()]
    );

    let sessions = [
        Compared::read(session.clone()).expect("a summary"),
        Compared::read(session).expect("a summary"),
    ];
    let comparison = compare::report(&sessions, false).expect("a comparison");
    assert_eq!(
        comparison.arms.iter().map(|table| table.arm.clone()).collect::<Vec<_>>(),
        [Arm::PROJECT, twin]
    );
    assert!(
        comparison
            .text()
            .contains("project 1/1, project-without-com.intellij.air 1/1 valid"),
        "{}",
        comparison.text()
    );
}
