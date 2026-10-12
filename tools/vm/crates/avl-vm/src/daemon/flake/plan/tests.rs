//! Every case here is a way the flake harness could have published a number nobody may quote, or reset the wrong
//! thing between trials.
//!
//! The trial outcomes are hand-built attempts rather than daemon transcripts: what the fold reacts to is the *shape*
//! of a finished attempt, and the combinations that matter (one dead trial beside a green one, two excluded of
//! three) cannot be produced by one run on one worker. The transcript-driven fixture is for the chain, where the
//! question is which effects reach the guest and in what order.

#![expect(
    clippy::float_cmp,
    reason = "the rates are small exact fractions (0.0, 0.5), so an epsilon would hide a wrong formula"
)]

use std::path::PathBuf;
use std::sync::Mutex;

use avl_base::Exit;
use avl_wire::report::RunReport;
use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;
use crate::daemon::fixture::Fixture;
use crate::daemon::flake::{FlakeArgs, Reset};
use crate::daemon::iterate::IdeAction;

fn stamp(minute: usize) -> String {
    format!("2026-08-22T12:{minute:02}:00Z")
}

/// A finished report of the shape the report builder produces, assembled from a class list.
fn report_of(passed: &[&str], failed: &[&str]) -> RunReport {
    let names: Vec<&str> = passed.iter().chain(failed).copied().collect();
    let suites: Vec<serde_json::Value> = names
        .iter()
        .enumerate()
        .map(|(index, class_name)| {
            let failures = u32::from(failed.contains(class_name));
            json!({
                "name": class_name, "timestamp": stamp(index + 1), "durationMs": 1_000.0,
                "documentIndex": index, "tests": 1, "failures": failures, "errors": 0, "skipped": 0,
                "cases": [{"className": class_name, "name": "test",
                    "status": if failures == 1 { "failed" } else { "passed" }, "durationMs": 1_000.0}],
            })
        })
        .collect();
    let failures: Vec<serde_json::Value> = failed
        .iter()
        .enumerate()
        .map(|(index, class_name)| {
            json!({
                "source": "junit_xml", "suite": class_name, "suiteTimestamp": stamp(passed.len() + index + 1),
                "className": class_name, "testName": "test", "kind": "failure",
                "message": format!("{class_name} failed"), "relevantFrames": [],
                "messageTruncated": false, "detailTruncated": false,
            })
        })
        .collect();
    let status = if !failed.is_empty() {
        "failed"
    } else if names.is_empty() {
        "no_tests"
    } else {
        "passed"
    };
    let joined = if names.is_empty() { "empty".to_owned() } else { names.join("-") };
    serde_json::from_value(json!({
        "reportSchemaVersion": avl_wire::report::RUN_REPORT_SCHEMA_VERSION,
        "iterationId": format!("iter-{joined}"),
        "daemonRunId": "run-ui-daemon-1",
        "daemonBootStamp": "boot",
        "selection": "lane ui",
        "status": status,
        "startedAt": stamp(0),
        "completedAt": stamp(names.len() + 1),
        "durationMs": 60_000.0,
        "ordering": avl_wire::report::ORDERING,
        "execution": {"testsStarted": names.len(), "testsFailed": failed.len(), "testsSkipped": 0,
            "containersSkipped": 0, "containerFailures": 0},
        "xml": {"tests": names.len(), "failures": failed.len(), "errors": 0, "skipped": 0},
        "source": {"guestPath": "/worker/daemon/test.xml", "retrieval": "daemon_http", "integrity": "complete"},
        "suites": suites,
        "failures": failures,
        "watchdog": {},
    }))
    .expect("the fixture report decodes")
}

fn attempt_of(worker: &str, document: Option<RunReport>, failure: Option<Refusal>) -> RunAttempt {
    RunAttempt {
        worker: worker.to_owned(),
        report: document.map(|document| {
            let path = PathBuf::from(format!("/reports/{}.json", document.iteration_id));
            (document, path)
        }),
        error: failure,
        timing: "build 1.0s".to_owned(),
        ide_action: IdeAction::Relaunch,
        execution: None,
    }
}

fn outcome_of(ordinal: u32, worker: &str, chain_position: u32, attempt: RunAttempt, free: Option<(i64, i64)>) -> TrialOutcome {
    TrialOutcome {
        assignment: TrialAssignment {
            ordinal,
            worker: worker.to_owned(),
            chain_position,
        },
        attempt,
        duration_ms: 200_000.0,
        guest_free_bytes_before: free.map(|(before, _)| before),
        guest_free_bytes_after: free.map(|(_, after)| after),
    }
}

const GIB: i64 = 1024 * 1024 * 1024;

fn refusal(code: &'static str, message: &str) -> Refusal {
    Refusal::new(code, Exit::SOFTWARE, message)
}

// --- the fold's inputs --------------------------------------------------------------------------------------------

// Two workers, the same two classes, and one class that failed on exactly one of them: the minimum shape in which a
// flake exists at all. The tally has to name it flaky and the other stable, and it has to keep the per-trial disk
// context, because a trial that ran against a nearly full disk is otherwise an outlier nobody can explain.
#[test]
fn two_trials_on_two_workers_fold_into_one_tally_with_the_free_disk_context_of_each() {
    let summary = summarize_trials(
        &[
            outcome_of(
                1,
                "air-linux-1",
                1,
                attempt_of("air-linux-1", Some(report_of(&["a.OneTest", "a.TwoTest"], &[])), None),
                Some((40 * GIB, 38 * GIB)),
            ),
            outcome_of(
                2,
                "air-linux-2",
                1,
                attempt_of("air-linux-2", Some(report_of(&["a.OneTest"], &["a.TwoTest"])), None),
                Some((6 * GIB, 5 * GIB)),
            ),
        ],
        ResetPolicy::FreshIde,
    );
    assert_eq!((summary.attempted_trials, summary.authoritative_trials), (2, 2));
    assert_eq!(summary.stable_set, ["a.OneTest"]);
    assert_eq!(summary.flake_set, ["a.TwoTest"]);
    assert_eq!(summary.measured_classes, 2);
    assert_eq!(summary.lane_flake_rate, 0.5);
    assert!(summary.reportable, "{:?}", summary.not_reportable_reason);
    // One record per trial, carrying the worker it ran on, the policy it ran under and the disk either side.
    let workers: Vec<&str> = summary.trials.iter().map(|trial| trial.entry.worker.as_str()).collect();
    assert_eq!(workers, ["air-linux-1", "air-linux-2"]);
    assert!(summary.trials.iter().all(|trial| trial.reset_policy == ResetPolicy::FreshIde));
    let second = &summary.trials[1];
    assert_eq!(second.duration_ms, 200_000.0);
    assert_eq!(second.guest_free_bytes_before, Some(6 * GIB));
    // The one class that failed here failed on the *second* ordinal only, which is the positional claim the order
    // suspects exist to make.
    assert_eq!(summary.order_suspects, ["a.TwoTest"]);
}

// Excluded trials are not missing at random: a daemon that dies during the relaunch-heavy classes removes exactly the
// classes most likely to flake, so the survivors read *low* - the damaging direction. The controller's own error has
// to reach the exclusion list by code, which is what makes the guard bite.
#[test]
fn an_infrastructure_heavy_run_publishes_no_rate_at_all() {
    let summary = summarize_trials(
        &[
            outcome_of(
                1,
                "air-linux-1",
                1,
                attempt_of("air-linux-1", Some(report_of(&["a.OneTest"], &[])), None),
                None,
            ),
            outcome_of(
                2,
                "air-linux-1",
                2,
                attempt_of(
                    "air-linux-1",
                    None,
                    Some(refusal("flake_reset_failed", "the daemon did not come back")),
                ),
                None,
            ),
            outcome_of(
                3,
                "air-linux-1",
                3,
                attempt_of(
                    "air-linux-1",
                    None,
                    Some(refusal("daemon_watchdog_expired", "no progress for 600 s")),
                ),
                None,
            ),
        ],
        ResetPolicy::FreshWorker,
    );
    assert_eq!(summary.authoritative_trials, 1);
    let codes: Vec<&str> = summary
        .infrastructure_trials
        .iter()
        .map(|exclusion| exclusion.code.as_str())
        .collect();
    assert_eq!(codes, ["flake_reset_failed", "daemon_watchdog_expired"]);
    assert!(!summary.reportable);
    let reason = summary.not_reportable_reason.as_deref().unwrap_or_default();
    assert!(reason.contains("2 of 3 trials were excluded"), "{reason}");
}

// The fold can only be as honest as what is handed to it: an attempt whose error was dropped would arrive as a
// missing report with no reason.
#[test]
fn a_trials_controller_side_error_and_report_path_survive_the_mapping_into_the_aggregate() {
    let mapped = trial_attempt(
        &outcome_of(
            7,
            "air-linux-2",
            3,
            attempt_of("air-linux-2", None, Some(refusal("guest_disk_full", "5 GiB"))),
            None,
        ),
        ResetPolicy::Warm,
    );
    assert_eq!(mapped.attempt.worker, "air-linux-2");
    assert_eq!(mapped.trial_ordinal, 7);
    assert_eq!(mapped.reset_policy, ResetPolicy::Warm);
    assert_eq!(mapped.attempt.report, None);
    assert_eq!(
        mapped.attempt.error,
        Some(AggregateError {
            code: "guest_disk_full".to_owned(),
            message: "5 GiB".to_owned(),
        })
    );
    assert_eq!(
        mapped.duration_ms,
        Some(200_000.0),
        "a trial that never reported still has a duration"
    );
    assert_eq!(mapped.attempt.report_path, None);

    let with_report = trial_attempt(
        &outcome_of(
            8,
            "air-linux-2",
            4,
            attempt_of("air-linux-2", Some(report_of(&["a.OneTest"], &[])), None),
            None,
        ),
        ResetPolicy::FreshIde,
    );
    assert_eq!(with_report.attempt.error, None);
    assert!(
        with_report
            .attempt
            .report_path
            .as_deref()
            .is_some_and(|path| path.contains("/reports/")),
        "{:?}",
        with_report.attempt.report_path
    );
    assert_eq!(
        with_report.attempt.iteration_id.as_deref(),
        Some("iter-a.OneTest"),
        "the identity is the report's own"
    );
}

// --- the reset ----------------------------------------------------------------------------------------------------

/// Records what a reset asked for, in order, and refuses the step named by `fail_on`.
#[derive(Default)]
struct RecordingReset {
    observed: Mutex<Vec<String>>,
    fail_on: Option<&'static str>,
}

impl RecordingReset {
    fn record(&self, name: String) -> impl Future<Output = Result<(), Refusal>> {
        let refused = self.fail_on.is_some_and(|fail_on| name == fail_on);
        self.observed.lock().unwrap().push(name);
        std::future::ready(if refused {
            Err(refusal("daemon_ide_stop_failed", "/ide/stop returned 500"))
        } else {
            Ok(())
        })
    }

    fn observed(&self) -> Vec<String> {
        self.observed.lock().unwrap().clone()
    }
}

impl ResetIo for RecordingReset {
    fn ide_stop(&self, _ctx: &Ctx) -> impl Future<Output = Result<(), Refusal>> {
        self.record("ide_stop".to_owned())
    }

    fn daemon_stop(&self, _ctx: &Ctx) -> impl Future<Output = Result<(), Refusal>> {
        self.record("daemon_stop".to_owned())
    }

    fn guest_remove(&self, _ctx: &Ctx, argv: &[String]) -> impl Future<Output = Result<(), Refusal>> {
        self.record(format!("guest {}", argv.join(" ")))
    }

    fn daemon_start(&self, _ctx: &Ctx) -> impl Future<Output = Result<(), Refusal>> {
        self.record("daemon_start".to_owned())
    }
}

#[tokio::test]
async fn the_shared_guest_test_home_is_discarded_under_exactly_the_daemon_policy_and_never_under_a_live_ide() {
    let fixture = Fixture::pool().await;
    let settings = &fixture.settings;
    let ctx = Ctx::background();
    let home = format!("{}/{GUEST_SHARED_TEST_HOME}", settings.vm_data);

    // `warm` inherits everything by definition, and `fresh_ide` is the iteration's own `/ide/stop` path - neither may
    // delete a tree, because a reset that quietly discards the shared test home is a different experiment from the
    // one the caller asked for.
    for policy in [ResetPolicy::Warm, ResetPolicy::FreshIde] {
        assert_eq!(reset_steps(settings, policy), [], "{policy} deletes nothing");
    }
    let steps = reset_steps(settings, ResetPolicy::FreshWorker);
    let names: Vec<&str> = steps.iter().map(ResetStep::name).collect();
    assert_eq!(
        names,
        ["ide_stop", "daemon_stop", "guest_remove", "daemon_start"],
        "the order is the obligation"
    );
    let ResetStep::GuestRemove(argv) = &steps[2] else {
        panic!("the third step removes the tree, was {:?}", steps[2]);
    };
    // One fixed argv, anchored under the guest's own data root, that no shell can re-split.
    assert_eq!(argv, &["/bin/rm", "-rf", home.as_str()]);
    assert!(home.ends_with("/out/ide-tests/tests/IU-LOCAL/air"), "{home}");
    for value in argv {
        assert!(!value.contains(['*', '?', ' ', '\t']), "{value:?}");
    }

    // The warm IDE's config and system directories live *under* that tree, so the ordering is asserted against the
    // effects a reset actually issued.
    let issued = RecordingReset::default();
    apply_reset(&ctx, &steps, &issued).await.unwrap();
    assert_eq!(
        issued.observed(),
        [
            "ide_stop".to_owned(),
            "daemon_stop".to_owned(),
            format!("guest /bin/rm -rf {home}"),
            "daemon_start".to_owned()
        ]
    );
    // A `fresh_ide` reset reaches neither the daemon nor the guest at all.
    let quiet = RecordingReset::default();
    apply_reset(&ctx, &reset_steps(settings, ResetPolicy::FreshIde), &quiet)
        .await
        .unwrap();
    assert_eq!(quiet.observed(), Vec::<String>::new());
    // A step that refuses stops the list where it is: the `rm -rf` must not run against a live IDE because the stop
    // that was to precede it failed.
    let refusing = RecordingReset {
        fail_on: Some("ide_stop"),
        ..RecordingReset::default()
    };
    assert!(apply_reset(&ctx, &steps, &refusing).await.is_err());
    assert_eq!(refusing.observed(), ["ide_stop"]);
}

// --- the plan -----------------------------------------------------------------------------------------------------

// Ordinal order has to equal chain order within a worker, because the order signature reads the lowest executed
// ordinal as "the first trial". Round-robin would make those two disagree on every worker but the first.
#[test]
fn trials_are_planned_as_contiguous_blocks_per_worker() {
    let assignment = |ordinal, worker: &str, chain_position| TrialAssignment {
        ordinal,
        worker: worker.to_owned(),
        chain_position,
    };
    assert_eq!(
        plan_flake_trials(&["w1".to_owned()], 3),
        [assignment(1, "w1", 1), assignment(2, "w1", 2), assignment(3, "w1", 3)]
    );
    // The remainder goes to the earlier workers, and each block is contiguous.
    assert_eq!(
        plan_flake_trials(&["w1".to_owned(), "w2".to_owned()], 5),
        [
            assignment(1, "w1", 1),
            assignment(2, "w1", 2),
            assignment(3, "w1", 3),
            assignment(4, "w2", 1),
            assignment(5, "w2", 2)
        ]
    );
}

// --- the command line ---------------------------------------------------------------------------------------------

/// `vm flake`, as far as its own options go: the controller's refusal of a bad option is `avl-vm`'s suite.
#[derive(clap::Parser, Debug)]
struct Probe {
    #[command(flatten)]
    args: FlakeArgs,
}

fn flake_args(argv: &[&str]) -> FlakeArgs {
    <Probe as clap::Parser>::try_parse_from(std::iter::once("flake").chain(argv.iter().copied()))
        .unwrap_or_else(|error| panic!("{argv:?} did not parse: {error}"))
        .args
}

#[test]
fn the_request_names_its_own_defaults_and_hands_everything_else_to_the_run_grammar() {
    let defaults = flake_args(&["--lane", "ui", "--trials", "12"]);
    // fresh-ide, not none: a warm IDE inherited across trials is an uncharacterised carrier. One worker, because N
    // workers is a different execution context and the one-worker baseline has to exist first.
    assert_eq!(
        (defaults.trials, defaults.reset, defaults.reset.policy(), defaults.worker_count()),
        (12, Reset::FreshIde, ResetPolicy::FreshIde, 1)
    );
    assert_eq!(defaults.selection_shape(), "lane");
    assert_eq!(defaults.run.argv(), ["--lane", "ui"], "selection is `run`'s grammar");

    let filtered = flake_args(&[
        "--lane",
        "ui",
        "--trials",
        "4",
        "--workers",
        "2",
        "--reset",
        "daemon",
        "--filter",
        "include-classname=.*(A|B).*",
        "--timeout",
        "900",
    ]);
    assert_eq!(
        (filtered.trials, filtered.worker_count(), filtered.reset.policy()),
        (4, 2, ResetPolicy::FreshWorker)
    );
    assert_eq!(
        filtered.run.argv(),
        ["--lane", "ui", "--filter", "include-classname=.*(A|B).*", "--timeout", "900"]
    );

    // A standalone selector chain is the other shape of the experiment, and the request says which one it is.
    let selector = flake_args(&["a.OneTest", "--trials", "2", "--reset", "none"]);
    assert_eq!(selector.selection_shape(), "selector");
    assert_eq!(selector.reset.policy(), ResetPolicy::Warm);
    assert_eq!(selector.run.argv(), ["a.OneTest"]);

    // More workers than trials would leave a chain holding a worker with nothing to run on it.
    assert_eq!(flake_args(&["--lane", "ui", "--trials", "2", "--workers", "8"]).worker_count(), 2);
}

// --- the text -----------------------------------------------------------------------------------------------------

#[test]
fn the_text_renders_every_bucket_and_ends_with_the_aggregate() {
    let summary = summarize_trials(
        &[
            outcome_of(
                1,
                "air-linux-1",
                1,
                attempt_of("air-linux-1", Some(report_of(&["a.OneTest", "a.TwoTest"], &[])), None),
                None,
            ),
            outcome_of(
                2,
                "air-linux-1",
                2,
                attempt_of("air-linux-1", Some(report_of(&["a.OneTest"], &["a.TwoTest"])), None),
                None,
            ),
        ],
        ResetPolicy::FreshIde,
    );
    let text = render_flake_text(
        "lane ui",
        "fresh-ide",
        &["air-linux-1".to_owned()],
        &summary,
        "/runtime/aggregates/flake-1-abcd/flake.json",
    );
    let lines: Vec<&str> = text.lines().collect();
    assert_eq!(lines[0], "lane ui, 2 trial(s) on air-linux-1, reset fresh-ide");
    assert_eq!(
        lines[1], "2 authoritative, 0 excluded",
        "the exclusions are published beside the rate"
    );
    assert!(lines[2].contains("lane flake rate 50.0%"), "{}", lines[2]);
    assert!(text.contains("\nflaky: a.TwoTest\n"), "{text}");
    // An empty list renders as a dash rather than as nothing, so a reader can tell "none" from "not printed".
    for label in ["broken", "notMeasured", "unstablePrerequisite", "insufficientEvidence"] {
        assert!(text.contains(&format!("\n{label}: -\n")), "{label}: {text}");
    }
    assert!(text.contains("\nreportable=true\n"), "{text}");
    // The summary's path is the last line: it is how a reader comes back to this run once the process is gone.
    assert!(text.ends_with("\naggregate /runtime/aggregates/flake-1-abcd/flake.json"), "{text}");

    // ...and an unreportable one carries its reason on the same line.
    let unreportable = summarize_trials(&[], ResetPolicy::Warm);
    let text = render_flake_text("lane ui", "none", &[], &unreportable, "/aggregates/flake.json");
    assert!(text.contains("\nreportable=false: no trials were attempted\n"), "{text}");
}
