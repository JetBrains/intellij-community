use avl_report::aggregate::{Attempt, ShardAttempt, merge_shard_verdict, persist_shard_verdict};
use avl_wire::report::AggregateError;
use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;
use crate::daemon::fixture::{Fixture, baseline_report, typed, write_report};

fn duration(class_name: &str, duration_ms: f64) -> ClassDuration {
    ClassDuration {
        class_name: class_name.to_owned(),
        duration_ms,
    }
}

#[test]
fn a_class_costs_what_the_suite_that_ran_it_cost_fixture_time_included() {
    let durations = class_durations_of(&typed(&baseline_report(&[("a.Heavy", 41_300.0), ("a.Light", 3_000.0)])));
    // 41.3 s, not the 20.65 s the one test case reported: the other 20 s is the IDE relaunch in `@BeforeAll`, and
    // attributing it to nobody is how the balancer would under-weight exactly the classes that matter.
    assert_eq!(
        durations,
        BTreeMap::from([("a.Heavy".to_owned(), 41_300.0), ("a.Light".to_owned(), 3_000.0)])
    );

    // A suite that mixes classes has no non-arbitrary way to split its fixture time, so it is not invented: each
    // class gets its own cases and nothing more.
    let mut mixed = baseline_report(&[]);
    mixed["suites"] = json!([{
        "name": "a.Mixed", "timestamp": "2026-08-21T12:00:00Z", "durationMs": 30_000.0, "documentIndex": 0,
        "tests": 2, "failures": 0, "errors": 0, "skipped": 0,
        "cases": [
            {"className": "a.One", "name": "x", "status": "passed", "durationMs": 4_000.0},
            {"className": "a.Two", "name": "y", "status": "passed", "durationMs": 5_000.0},
        ],
    }]);
    assert_eq!(
        class_durations_of(&typed(&mixed)),
        BTreeMap::from([("a.One".to_owned(), 4_000.0), ("a.Two".to_owned(), 5_000.0)])
    );
}

fn at(mut document: Value, completed_at: &str) -> Value {
    document["completedAt"] = json!(completed_at);
    document
}

#[tokio::test]
async fn the_baseline_is_the_most_recent_measurement_per_class_and_only_from_a_run_that_measured() {
    let fixture = Fixture::pool().await;
    let settings = &fixture.settings;
    let first = &fixture.worker;

    // Older, and the only report that mentions `a.Retired`.
    let older = at(
        baseline_report(&[("a.Slow", 40_000.0), ("a.Retired", 9_000.0)]),
        "2026-08-20T12:00:00Z",
    );
    write_report(settings, first, "run-1", "iter-1", &older);
    // Newer: what `a.Slow` costs now.
    let newer = at(baseline_report(&[("a.Slow", 12_000.0)]), "2026-08-21T12:00:00Z");
    write_report(settings, first, "run-2", "iter-1", &newer);
    // Newest, and not evidence: a truncated XML arrives as an infrastructure error, and a run that measured nothing
    // trustworthy must not be the thing a split is balanced on.
    let mut truncated = at(baseline_report(&[("a.Slow", 999_000.0)]), "2026-08-22T12:00:00Z");
    truncated["status"] = json!("infrastructure_error");
    truncated["source"]["integrity"] = json!("truncated");
    write_report(settings, first, "run-3", "iter-1", &truncated);
    // Neither is a run that discovered nothing, however complete its (empty) XML.
    let mut empty = at(baseline_report(&[]), "2026-08-22T13:00:00Z");
    empty["status"] = json!("no_tests");
    write_report(settings, first, "run-4", "iter-1", &empty);
    // Nor a report of an older layout, whose fields this reader would take as zeros.
    let mut stale = at(baseline_report(&[("a.Stale", 7_000.0)]), "2026-08-22T14:00:00Z");
    stale["reportSchemaVersion"] = json!(RUN_REPORT_SCHEMA_VERSION - 1);
    write_report(settings, first, "run-5", "iter-1", &stale);
    // An evidence directory and a half-written report both live beside the reports, and neither is one.
    let run_2 = settings.worker_dir(first).join("reports").join("run-2");
    fs::create_dir_all(run_2.join("iter-1-evidence")).unwrap();
    fs::write(run_2.join(".report-1-x.tmp"), "{ truncated").unwrap();

    let baseline = read_shard_baseline(settings);
    assert_eq!(
        baseline.durations,
        [duration("a.Retired", 9_000.0), duration("a.Slow", 12_000.0)],
        "the newest measurement per class, sorted by name"
    );
    assert_eq!(
        (baseline.admitted_reports, baseline.scanned_reports),
        (2, 5),
        "two of five reports are evidence"
    );

    // A worker of another pool is not evidence about this one: durations are a property of a guest, and the two
    // guests have distinct slot names for exactly that reason.
    write_report(
        settings,
        "air-macos-9",
        "run-9",
        "iter-1",
        &baseline_report(&[("a.OtherPoolOnly", 5_000.0)]),
    );
    assert!(
        read_shard_baseline(settings)
            .durations
            .iter()
            .all(|entry| entry.class_name != "a.OtherPoolOnly"),
        "a worker outside this pool is not evidence about it"
    );
}

#[tokio::test]
async fn an_unpatternable_class_is_named_rather_than_planned() {
    let fixture = Fixture::pool().await;
    let document = baseline_report(&[("a.Good", 1_000.0), (r"a.B\Ec", 2_000.0)]);
    write_report(&fixture.settings, &fixture.worker, "run-1", "iter-1", &document);
    let baseline = read_shard_baseline(&fixture.settings);
    // A name that cannot be quoted is left out of the plan, and named, so the reader knows it ran in the remainder.
    assert_eq!(baseline.durations, [duration("a.Good", 1_000.0)]);
    assert_eq!(baseline.unpatternable, [r"a.B\Ec"]);
}

// What the baseline owes the aggregate's home is one property: the scan must not read a merged verdict back as a
// measurement. The document itself - its bytes, its mode, its refusal to be overwritten - belongs to `avl-report`.
#[tokio::test]
async fn the_baseline_scan_is_blind_to_a_merged_verdict() {
    let fixture = Fixture::pool().await;
    let settings = &fixture.settings;
    let verdict = merge_shard_verdict(&[ShardAttempt {
        shard_index: 1,
        attempt: Attempt {
            worker: fixture.worker.clone(),
            error: Some(AggregateError {
                code: "daemon_died".to_owned(),
                message: "the daemon walked away".to_owned(),
            }),
            ..Attempt::default()
        },
    }]);
    let path = persist_shard_verdict(settings, "shard-1-abcd", &verdict).unwrap();
    assert_eq!(
        path.parent().and_then(Path::parent),
        Some(settings.runtime_root.join("aggregates").as_path()),
        "the aggregate lives under <runtimeRoot>/aggregates"
    );
    assert!(
        !path.components().any(|part| part.as_os_str() == "reports"),
        "the aggregate is deliberately outside every worker's reports tree: {}",
        path.display()
    );
    assert!(read_shard_baseline(settings).durations.is_empty());
}
