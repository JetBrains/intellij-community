use std::collections::BTreeMap;

use avl_wire::program::lane::PerformanceSeries;
use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;

const CLASS: &str = "com.intellij.air.integration.acp.AirLongSessionPerformanceUiTest";
const FIRST: &str = "a session of size M opens";

fn series() -> Vec<PerformanceSeries> {
    (1..=5)
        .map(|pass| PerformanceSeries {
            test_class_name: CLASS.to_owned(),
            scenario: name(pass),
            project: "air/long-session/M".to_owned(),
            method: format!("{CLASS}#longSession"),
            artifact_path: "air-long-session/M".to_owned(),
        })
        .collect()
}

fn name(pass: u32) -> String {
    if pass == 1 {
        FIRST.to_owned()
    } else {
        format!("{FIRST}, pass {pass} of 5")
    }
}

/// open.M.gestureToTabMs of the nights before 2026-11-19 22:19: about 550 ms, so the band ends at 30 % more.
fn early_stop() -> EarlyStop {
    let baseline: Baseline = serde_json::from_value(json!({
        "minPasses": 2,
        "series": {"air/long-session/M": {"open.M.gestureToTabMs": {
            "values": [560, 532, 548, 571, 540, 555, 566, 538, 549, 561], "relative": 25, "absolute": 30, "noise": 5
        }}}
    }))
    .unwrap();
    EarlyStop::new(baseline)
}

fn record(stop: &EarlyStop, pass: u32, value: i64) {
    let metrics: BTreeMap<String, serde_json::Number> = BTreeMap::from([("open.M.gestureToTabMs".to_owned(), value.into())]);
    stop.record(&series(), "AirLongSessionPerformanceUiTest", &name(pass), &metrics);
}

fn skips(stop: &EarlyStop, pass: u32) -> bool {
    stop.skip_reason(&series(), "AirLongSessionPerformanceUiTest", &name(pass))
        .is_some()
}

#[test]
fn a_quiet_night_stops_after_the_passes_that_always_run() {
    let stop = early_stop();
    assert!(!skips(&stop, 1));
    record(&stop, 1, 551);
    assert!(!skips(&stop, 2), "the second pass always runs");
    record(&stop, 2, 563);
    let reason = stop.skip_reason(&series(), "AirLongSessionPerformanceUiTest", &name(3)).unwrap();
    assert_eq!(
        reason,
        "the 2 passes so far of air/long-session/M are in the band of the nights before, so pass 3 is not needed: \
         open.M.gestureToTabMs 557 <= 690"
    );
}

#[test]
fn a_slow_night_runs_every_pass() {
    let stop = early_stop();
    record(&stop, 1, 11098);
    record(&stop, 2, 11160);
    for pass in 3..=5 {
        assert!(!skips(&stop, pass), "pass {pass}");
        record(&stop, pass, 11100);
    }
}

#[test]
fn one_slow_pass_of_two_takes_the_next_pass() {
    let stop = early_stop();
    record(&stop, 1, 551);
    record(&stop, 2, 1500);
    assert!(!skips(&stop, 3), "the median of 551 and 1500 is over the band");
}

#[test]
fn a_failed_pass_leaves_too_few_passes_to_stop() {
    let stop = early_stop();
    record(&stop, 1, 551);
    assert!(!skips(&stop, 3), "pass 2 failed, so it has no record");
}

#[test]
fn a_metric_without_enough_nights_runs_every_pass() {
    let baseline: Baseline = serde_json::from_value(json!({
        "minPasses": 2,
        "series": {"air/long-session/M": {"open.M.gestureToTabMs": {"values": [560, 532], "relative": 25, "absolute": 30, "noise": 5}}}
    }))
    .unwrap();
    let stop = EarlyStop::new(baseline);
    record(&stop, 1, 551);
    record(&stop, 2, 563);
    assert!(!skips(&stop, 3));
}

#[test]
fn a_series_without_a_baseline_runs_every_pass() {
    let stop = EarlyStop::new(Baseline {
        min_passes: 2,
        series: BTreeMap::new(),
    });
    record(&stop, 1, 551);
    record(&stop, 2, 563);
    assert!(!skips(&stop, 3));
}

#[test]
fn a_baseline_that_cannot_be_read_is_a_note_and_runs_every_pass() {
    let mut notes = Vec::new();
    assert!(EarlyStop::from_variable(Some("/no/such/baseline.json".to_owned()), &mut notes).is_none());
    assert_eq!(notes.len(), 1);
    assert!(notes[0].starts_with("every pass runs, because the baseline /no/such/baseline.json cannot be read"));
    assert!(EarlyStop::from_variable(None, &mut notes).is_none());
    assert_eq!(notes.len(), 1, "no variable is no note");
}

#[test]
fn a_metric_with_a_limit_compares_the_passes_to_the_limit() {
    let baseline: Baseline = serde_json::from_value(json!({
        "minPasses": 2,
        "series": {"air/long-session/M": {"open.M.gestureToTabMs": {"limit": 300}}}
    }))
    .unwrap();
    let quiet = EarlyStop::new(baseline.clone());
    record(&quiet, 1, 120);
    record(&quiet, 2, 140);
    assert!(skips(&quiet, 3));
    let slow = EarlyStop::new(baseline);
    record(&slow, 1, 320);
    record(&slow, 2, 340);
    assert!(!skips(&slow, 3));
}
