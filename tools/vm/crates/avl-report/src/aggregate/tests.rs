#![expect(
    clippy::float_cmp,
    reason = "the rates and interval bounds compared are exact by construction, so an epsilon would hide a wrong formula"
)]

use avl_wire::report::{
    Case, Failure, FailureKind, FailureSource, RUN_REPORT_SCHEMA_VERSION, Retrieval, SkippedContainer, Source, Suite, Watchdog,
    decode_flake_summary, decode_shard_verdict,
};
use pretty_assertions::assert_eq;

use super::*;

// --- fixtures -------------------------------------------------------------------------------------------------

/// A healthy single-run report, which every fixture below spoils in exactly one way.
pub(super) fn document(status: Status) -> RunReport {
    RunReport {
        report_schema_version: RUN_REPORT_SCHEMA_VERSION,
        iteration_id: "iteration-1".to_owned(),
        daemon_run_id: "run-ui-daemon-0001".to_owned(),
        daemon_boot_stamp: "2026-08-22T09:59:00Z".to_owned(),
        selection: "com.intellij.air.*".to_owned(),
        status,
        started_at: "2026-08-22T10:00:00Z".to_owned(),
        completed_at: "2026-08-22T10:01:00Z".to_owned(),
        duration_ms: 60_000.0,
        ordering: ORDERING.to_owned(),
        execution: ExecutionCounts::default(),
        xml: XmlCounts::default(),
        source: Source {
            guest_path: "/Users/air/out/junit.xml".to_owned(),
            retrieval: Retrieval::DaemonHttp,
            integrity: Integrity::Complete,
            diagnostic: None,
        },
        suites: Vec::new(),
        failures: Vec::new(),
        skipped_containers: Vec::new(),
        active_tests: Vec::new(),
        unreported_classes: Vec::new(),
        watchdog: Watchdog::default(),
        evidence: Vec::new(),
        trace_archives: Vec::new(),
        traces_error: None,
        tree: None,
        tree_error: None,
        protocol_diagnostic: None,
        verdict_diagnostic: None,
    }
}

fn passed(class_name: &str) -> (&str, CaseStatus) {
    (class_name, CaseStatus::Passed)
}

fn failed(class_name: &str) -> (&str, CaseStatus) {
    (class_name, CaseStatus::Failed)
}

/// Adds one document's worth of cases, and one failure entry per failed case - which is what a complete XML
/// produces.
fn with_suite(mut report: RunReport, name: &str, timestamp: Option<&str>, cases: &[(&str, CaseStatus)]) -> RunReport {
    let failures = cases.iter().filter(|(_, status)| *status == CaseStatus::Failed).count();
    let failures = u32::try_from(failures).unwrap();
    for (class_name, _) in cases.iter().filter(|(_, status)| *status == CaseStatus::Failed) {
        report.failures.push(Failure {
            source: FailureSource::JunitXml,
            suite: Some(name.to_owned()),
            suite_timestamp: timestamp.map(str::to_owned),
            class_name: Some((*class_name).to_owned()),
            test_name: "t".to_owned(),
            kind: FailureKind::Failure,
            r#type: None,
            message: "boom".to_owned(),
            detail: None,
            relevant_frames: Vec::new(),
            message_truncated: false,
            detail_truncated: false,
        });
    }
    let tests = u32::try_from(cases.len()).unwrap();
    report.suites.push(Suite {
        name: name.to_owned(),
        timestamp: timestamp.map(str::to_owned),
        duration_ms: 0.0,
        document_index: report.suites.len(),
        tests,
        failures,
        errors: 0,
        skipped: 0,
        cases: cases
            .iter()
            .map(|(class_name, status)| Case {
                class_name: (*class_name).to_owned(),
                name: "t".to_owned(),
                status: *status,
                duration_ms: 0.0,
            })
            .collect(),
    });
    report.xml.tests += tests;
    report.xml.failures += failures;
    report.execution.tests_started += tests;
    report.execution.tests_failed += failures;
    report
}

fn with_ruled_out(mut report: RunReport, class_name: &str, reason: &str) -> RunReport {
    report.skipped_containers.push(SkippedContainer {
        class_name: Some(class_name.to_owned()),
        display_name: class_name.to_owned(),
        reason: reason.to_owned(),
    });
    report.execution.containers_skipped += 1;
    report
}

fn with_diagnostic(mut report: RunReport, diagnostic: &str) -> RunReport {
    report.verdict_diagnostic = Some(diagnostic.to_owned());
    report
}

fn shard(index: u32, worker: &str, report: RunReport) -> ShardAttempt {
    ShardAttempt {
        attempt: Attempt {
            worker: worker.to_owned(),
            report: Some(report),
            report_path: Some("/reports/x.json".to_owned()),
            ..Attempt::default()
        },
        shard_index: index,
    }
}

/// A shard whose daemon was killed before it wrote anything: no report, only the controller's refusal.
pub(super) fn dead_shard(index: u32, worker: &str, message: &str) -> ShardAttempt {
    ShardAttempt {
        attempt: Attempt {
            worker: worker.to_owned(),
            error: Some(AggregateError {
                code: "daemon_died".to_owned(),
                message: message.to_owned(),
            }),
            ..Attempt::default()
        },
        shard_index: index,
    }
}

fn trial(ordinal: u32, report: RunReport) -> TrialAttempt {
    TrialAttempt {
        attempt: Attempt {
            worker: "air-linux-1".to_owned(),
            report: Some(report),
            ..Attempt::default()
        },
        trial_ordinal: ordinal,
        reset_policy: ResetPolicy::Warm,
        duration_ms: Some(60_000.0),
        guest_free_bytes_before: None,
        guest_free_bytes_after: None,
    }
}

fn dead_trial(ordinal: u32, code: &str) -> TrialAttempt {
    TrialAttempt {
        attempt: Attempt {
            worker: "air-linux-1".to_owned(),
            error: Some(AggregateError {
                code: code.to_owned(),
                message: "the worker went away".to_owned(),
            }),
            ..Attempt::default()
        },
        trial_ordinal: ordinal,
        reset_policy: ResetPolicy::Warm,
        duration_ms: None,
        guest_free_bytes_before: None,
        guest_free_bytes_after: None,
    }
}

/// The verdict as a reader gets it back, proving the wire accepts what this module produced.
fn read_back(verdict: &ShardVerdict) -> ShardVerdict {
    let encoded = serde_json::to_vec(verdict).unwrap();
    decode_shard_verdict(&encoded).unwrap_or_else(|error| {
        panic!(
            "this module produced a verdict its own wire refuses: {error}\n{}",
            String::from_utf8_lossy(&encoded)
        )
    })
}

fn read_back_flake(summary: &FlakeSummary) -> FlakeSummary {
    let encoded = serde_json::to_vec(summary).unwrap();
    decode_flake_summary(&encoded).unwrap_or_else(|error| {
        panic!(
            "this module produced a summary its own wire refuses: {error}\n{}",
            String::from_utf8_lossy(&encoded)
        )
    })
}

fn class_of<'a>(summary: &'a FlakeSummary, class_name: &str) -> &'a FlakeClass {
    summary
        .classes
        .iter()
        .find(|class| class.class_name == class_name)
        .unwrap_or_else(|| panic!("{class_name} is not in the summary: {:?}", summary.classes))
}

fn text(value: Option<&str>) -> &str {
    value.unwrap_or("<absent>")
}

// --- the interval ---------------------------------------------------------------------------------------------

/// 1/3 and 3/9 are the same point estimate and very different evidence.
#[test]
fn the_wilson_interval_narrows_with_evidence() {
    let third = wilson_interval(1, 3);
    let ninth = wilson_interval(3, 9);
    assert!(
        third.low < ninth.low && third.high > ninth.high,
        "3/9 did not narrow 1/3: {third:?} versus {ninth:?}"
    );
    for interval in [third, ninth] {
        assert!(interval.low <= 1.0 / 3.0 && interval.high >= 1.0 / 3.0, "{interval:?}");
        assert_eq!(interval.confidence, CONFIDENCE_95);
    }
    // Exact by construction, not by rounding.
    assert_eq!(wilson_interval(0, 5).low, 0.0);
    assert_eq!(wilson_interval(5, 5).high, 1.0);
    let half = wilson_interval(1, 2);
    assert!((half.low + half.high - 1.0).abs() < 1e-12, "{half:?}");
    assert!(
        (0.9054..0.9055).contains(&half.high),
        "the 95% bound at 1/2 is {}, want 0.90547 - check WILSON_Z_95",
        half.high
    );
    // Nothing known, said honestly.
    let none = wilson_interval(0, 0);
    assert_eq!((none.low, none.high), (0.0, 1.0));
}

// --- the shard merge ------------------------------------------------------------------------------------------

#[test]
fn a_merge_is_only_as_green_as_its_worst_shard() {
    let verdict = merge_shard_verdict(&[
        shard(
            1,
            "air-linux-1",
            with_suite(
                document(Status::Passed),
                "A",
                Some("2026-08-22T10:00:00Z"),
                &[passed("com.intellij.air.A")],
            ),
        ),
        shard(
            2,
            "air-linux-2",
            with_diagnostic(
                with_suite(
                    document(Status::InfrastructureError),
                    "B",
                    Some("2026-08-22T10:00:01Z"),
                    &[passed("com.intellij.air.B")],
                ),
                "JUnit result is truncated",
            ),
        ),
    ]);
    assert_eq!(
        (verdict.status, verdict.code),
        (Status::InfrastructureError, Some(ShardDiagnosticCode::InfrastructureError))
    );
    // The verdict is re-used, not re-derived: the shard's own diagnostic is what the merge quotes.
    assert!(
        text(verdict.diagnostic.as_deref()).contains("shard 2 (air-linux-2): JUnit result is truncated"),
        "{}",
        text(verdict.diagnostic.as_deref())
    );
    // The passing shard's report is not inlined and the broken one's is.
    let decoded = read_back(&verdict);
    assert!(decoded.entries[0].entry.report.is_none());
    assert!(decoded.entries[1].entry.report.is_some());
    assert_eq!(decoded.entries[1].entry.iteration_id.as_deref(), Some("iteration-1"));
}

/// A dead shard outranks every other diagnosis: nothing ran, and the reason it died is the controller's own.
#[test]
fn a_dead_shard_outranks_every_other_diagnosis() {
    let verdict = merge_shard_verdict(&[
        dead_shard(1, "air-linux-1", "the daemon exited during the run"),
        shard(
            2,
            "air-linux-2",
            with_diagnostic(document(Status::InfrastructureError), "truncated"),
        ),
    ]);
    assert_eq!(verdict.code, Some(ShardDiagnosticCode::MissingReport));
    assert!(
        text(verdict.diagnostic.as_deref()).contains("shard 1 (air-linux-1): the daemon exited during the run"),
        "{}",
        text(verdict.diagnostic.as_deref())
    );
    assert_eq!(read_back(&verdict).entries[0].entry.status, EntryStatus::NoReport);
}

/// Rule 3 is unreachable through the report builder; this builds the impossible report by hand so that its
/// becoming reachable is visible.
#[test]
fn an_incomplete_source_is_caught_even_if_the_single_run_guard_ever_misses_it() {
    let mut impossible = with_suite(document(Status::Passed), "A", None, &[passed("com.intellij.air.A")]);
    impossible.source.integrity = Integrity::Truncated;
    let verdict = merge_shard_verdict(&[shard(1, "air-linux-1", impossible)]);
    assert_eq!(verdict.code, Some(ShardDiagnosticCode::IncompleteSource));
    assert!(
        text(verdict.diagnostic.as_deref()).contains("shard 1 (air-linux-1): truncated"),
        "{}",
        text(verdict.diagnostic.as_deref())
    );
}

/// Every shard finding nothing is a selection that matched nothing; *one* is lost coverage wearing a green hat.
#[test]
fn discovering_nothing_is_two_different_verdicts() {
    let all = merge_shard_verdict(&[
        shard(1, "air-linux-1", document(Status::NoTests)),
        shard(2, "air-linux-2", document(Status::NoTests)),
    ]);
    assert_eq!(all.status, Status::NoTests);
    // The wire refuses an unnamed non-green verdict, and it is right to.
    assert_eq!(read_back(&all).code, Some(ShardDiagnosticCode::DiscoveredNothing));

    let partial = merge_shard_verdict(&[
        shard(1, "air-linux-1", document(Status::NoTests)),
        shard(
            2,
            "air-linux-2",
            with_suite(document(Status::Passed), "B", None, &[passed("com.intellij.air.B")]),
        ),
    ]);
    assert_eq!(
        (partial.status, partial.code),
        (Status::InfrastructureError, Some(ShardDiagnosticCode::DiscoveredNothing))
    );
    assert!(
        text(partial.diagnostic.as_deref()).contains("while 1 other shard(s) ran some"),
        "{}",
        text(partial.diagnostic.as_deref())
    );
}

/// All ruled out is a lane-wide prerequisite verdict, and the reasons are the answer.
#[test]
fn every_class_ruled_out_is_a_prerequisite_verdict() {
    let verdict = merge_shard_verdict(&[
        shard(
            1,
            "air-linux-1",
            with_ruled_out(document(Status::AllSkipped), "com.intellij.air.A", "no claude on air-linux-1"),
        ),
        shard(
            2,
            "air-linux-2",
            with_ruled_out(document(Status::AllSkipped), "com.intellij.air.A", "no claude on air-linux-2"),
        ),
    ]);
    assert_eq!(verdict.status, Status::AllSkipped);
    let decoded = read_back(&verdict);
    assert_eq!(decoded.skipped_containers.len(), 2);
    assert_eq!(decoded.coverage[0].skipped_classes, ["com.intellij.air.A"]);
    // A ruled-out class is not coverage: one missing guest CLI rules the same class out on every worker.
    assert!(decoded.overlaps.is_empty(), "{:?}", decoded.overlaps);
}

/// A shard split is only a split if the pieces are disjoint.
#[test]
fn two_shards_running_one_class_is_refused() {
    let verdict = merge_shard_verdict(&[
        shard(
            2,
            "air-linux-2",
            with_suite(
                document(Status::Passed),
                "A",
                None,
                &[passed("com.intellij.air.A"), passed("com.intellij.air.Shared")],
            ),
        ),
        shard(
            1,
            "air-linux-1",
            with_suite(document(Status::Passed), "A", None, &[passed("com.intellij.air.Shared")]),
        ),
    ]);
    assert_eq!(
        (verdict.status, verdict.code),
        (Status::InfrastructureError, Some(ShardDiagnosticCode::Overlap))
    );
    // Shard order, not input order: the same set of reports must always produce the same verdict.
    assert_eq!(
        verdict.overlaps,
        [ShardOverlapEntry {
            class_name: "com.intellij.air.Shared".to_owned(),
            shard_indexes: vec![1, 2],
        }]
    );
    assert!(
        text(verdict.diagnostic.as_deref()).contains("com.intellij.air.Shared in shards 1/2"),
        "{}",
        text(verdict.diagnostic.as_deref())
    );
}

/// A balancer that dropped every class leaves shards that reported and executed nothing.
#[test]
fn shards_that_executed_nothing_are_refused() {
    let verdict = merge_shard_verdict(&[
        shard(1, "air-linux-1", document(Status::Passed)),
        shard(2, "air-linux-2", document(Status::Passed)),
    ]);
    assert_eq!(
        (verdict.status, verdict.code),
        (Status::InfrastructureError, Some(ShardDiagnosticCode::ExecutedNothing))
    );
}

#[test]
fn an_empty_shard_set_is_a_planner_bug() {
    let verdict = merge_shard_verdict(&[]);
    assert_eq!(
        (verdict.status, verdict.code),
        (Status::InfrastructureError, Some(ShardDiagnosticCode::SetEmpty))
    );
    assert_eq!((verdict.shard_count, verdict.wall_duration_ms), (0, 0.0));
    read_back(&verdict);
}

/// An ordinary red lane carries no diagnostic code, and round-trips: its `failures` are the diagnosis.
#[test]
fn a_plain_red_lane_needs_no_diagnostic_code() {
    let verdict = merge_shard_verdict(&[
        shard(
            1,
            "air-linux-1",
            with_suite(document(Status::Failed), "A", None, &[failed("com.intellij.air.A")]),
        ),
        shard(
            2,
            "air-linux-2",
            with_suite(document(Status::Passed), "B", None, &[passed("com.intellij.air.B")]),
        ),
    ]);
    assert_eq!((verdict.status, verdict.code), (Status::Failed, None));
    let decoded = read_back(&verdict);
    assert_eq!((decoded.status, decoded.code), (Status::Failed, None));
    assert!(!decoded.failures.is_empty());
}

/// Suites are merged by instant and by global position, never by timestamp text.
#[test]
fn merged_suites_are_ordered_by_instant_across_shards() {
    let first = with_suite(
        with_suite(
            document(Status::Passed),
            "late",
            Some("2026-08-22T12:00:30+02:00"),
            &[passed("com.intellij.air.Late")],
        ),
        "untimed-a",
        None,
        &[passed("com.intellij.air.UntimedA")],
    );
    let second = with_suite(
        with_suite(
            document(Status::Passed),
            "early",
            Some("2026-08-22T10:00:00Z"),
            &[passed("com.intellij.air.Early")],
        ),
        "untimed-b",
        None,
        &[passed("com.intellij.air.UntimedB")],
    );
    let verdict = merge_shard_verdict(&[shard(1, "air-linux-1", first), shard(2, "air-linux-2", second)]);
    let names: Vec<&str> = verdict.suites.iter().map(|suite| suite.name.as_str()).collect();
    // `late` is 10:00:30Z once the offset is read as an offset; the untimed suites keep their position, last.
    assert_eq!(names, ["early", "late", "untimed-a", "untimed-b"]);
    // Each suite keeps its own document index, the only pointer back into its XML.
    let untimed = verdict.suites.iter().find(|suite| suite.name == "untimed-a").unwrap();
    assert_eq!(untimed.document_index, 1);
}

/// The wall clock is what the lane cost and the sum is the machine time it took.
#[test]
fn the_merge_reports_both_wall_and_machine_time() {
    let first = with_suite(document(Status::Passed), "A", None, &[passed("com.intellij.air.A")]);
    let mut second = with_suite(document(Status::Passed), "B", None, &[passed("com.intellij.air.B")]);
    second.started_at = "2026-08-22T10:00:30Z".to_owned();
    second.completed_at = "2026-08-22T10:02:00Z".to_owned();
    second.duration_ms = 90_000.0;
    let verdict = merge_shard_verdict(&[shard(1, "air-linux-1", first), shard(2, "air-linux-2", second)]);
    assert_eq!(
        (verdict.started_at.as_deref(), verdict.completed_at.as_deref()),
        (Some("2026-08-22T10:00:00Z"), Some("2026-08-22T10:02:00Z"))
    );
    assert_eq!((verdict.wall_duration_ms, verdict.total_duration_ms), (120_000.0, 150_000.0));
    assert_eq!((verdict.execution.tests_started, verdict.xml.tests), (2, 2));
    assert_eq!(verdict.executed_classes, ["com.intellij.air.A", "com.intellij.air.B"]);
    assert_eq!((verdict.status, verdict.code), (Status::Passed, None));
    read_back(&verdict);
}

/// The counts are the guest's, so any u32 can arrive: two such shards must saturate, neither panic nor wrap to a
/// small, wrong count.
#[test]
fn guest_counts_saturate_instead_of_overflowing() {
    let huge = |class_name: &str| {
        let mut report = with_suite(document(Status::Passed), "A", None, &[passed(class_name)]);
        report.execution.tests_started = u32::MAX;
        report.execution.tests_skipped = u32::MAX;
        report.xml.tests = u32::MAX;
        report.xml.skipped = u32::MAX;
        report
    };
    let verdict = merge_shard_verdict(&[
        shard(1, "air-linux-1", huge("com.intellij.air.A")),
        shard(2, "air-linux-2", huge("com.intellij.air.B")),
    ]);
    assert_eq!(
        (
            verdict.execution.tests_started,
            verdict.execution.tests_skipped,
            verdict.xml.tests,
            verdict.xml.skipped
        ),
        (u32::MAX, u32::MAX, u32::MAX, u32::MAX)
    );
}

/// Two spellings of one instant: the first shard's is kept at either end.
#[test]
fn the_first_spelling_of_an_equal_instant_is_kept() {
    let mut first = with_suite(document(Status::Passed), "A", None, &[passed("com.intellij.air.A")]);
    first.started_at = "2026-08-22T10:00:00Z".to_owned();
    first.completed_at = "2026-08-22T10:02:00Z".to_owned();
    let mut second = with_suite(document(Status::Passed), "B", None, &[passed("com.intellij.air.B")]);
    second.started_at = "2026-08-22T12:00:00+02:00".to_owned();
    second.completed_at = "2026-08-22T12:02:00+02:00".to_owned();
    let verdict = merge_shard_verdict(&[shard(1, "air-linux-1", first), shard(2, "air-linux-2", second)]);
    assert_eq!(
        (verdict.started_at.as_deref(), verdict.completed_at.as_deref()),
        (Some("2026-08-22T10:00:00Z"), Some("2026-08-22T10:02:00Z"))
    );
}

/// Twelve labels is not more information than eight and a count.
#[test]
fn a_diagnostic_is_bounded() {
    let attempts: Vec<ShardAttempt> = (1..=12).map(|index| dead_shard(index, "air-linux-1", "gone")).collect();
    let verdict = merge_shard_verdict(&attempts);
    let diagnostic = text(verdict.diagnostic.as_deref());
    assert!(diagnostic.contains("and 4 more"), "{diagnostic}");
    assert!(!diagnostic.contains("shard 9"), "{diagnostic}");
}

// --- the flake summary ----------------------------------------------------------------------------------------

/// "Flaky" is only meaningful once the classes that are not flaky have been separated from the classes nobody
/// measured. All six buckets are reachable from one run of trials.
#[test]
fn the_six_buckets_are_kept_apart() {
    let mut trials = Vec::new();
    for ordinal in 1..=3 {
        let flaky = if ordinal == 2 {
            failed("com.intellij.air.Flaky")
        } else {
            passed("com.intellij.air.Flaky")
        };
        let mut report = with_suite(
            document(Status::Failed),
            "suite",
            Some("2026-08-22T10:00:00Z"),
            &[flaky, failed("com.intellij.air.Broken"), passed("com.intellij.air.Stable")],
        );
        report = if ordinal == 3 {
            with_suite(report, "prerequisite", None, &[passed("com.intellij.air.Prerequisite")])
        } else {
            with_ruled_out(report, "com.intellij.air.Prerequisite", "no claude on air-linux-1")
        };
        report = with_ruled_out(report, "com.intellij.air.NeverRan", "no codex on air-linux-1");
        trials.push(trial(ordinal, report));
    }
    // One extra trial that only this class ever ran, once, and failed.
    let once = with_suite(
        with_suite(document(Status::Failed), "suite", None, &[failed("com.intellij.air.Once")]),
        "rest",
        None,
        &[passed("com.intellij.air.Stable")],
    );
    trials.push(trial(4, once));

    let summary = flake_summary(&trials);
    read_back_flake(&summary);
    for (class_name, bucket) in [
        ("com.intellij.air.Flaky", FlakeBucket::Flaky),
        ("com.intellij.air.Broken", FlakeBucket::Broken),
        ("com.intellij.air.Stable", FlakeBucket::Stable),
        ("com.intellij.air.Prerequisite", FlakeBucket::UnstablePrerequisite),
        ("com.intellij.air.NeverRan", FlakeBucket::NotMeasured),
        ("com.intellij.air.Once", FlakeBucket::InsufficientEvidence),
    ] {
        assert_eq!(class_of(&summary, class_name).bucket, bucket, "{class_name}");
    }
    // Only `flaky` publishes a rate: a broken class's would be 1.0 and read as maximal flakiness.
    let flaky = class_of(&summary, "com.intellij.air.Flaky");
    assert_eq!(flaky.rate, Some(1.0 / 3.0));
    assert!(flaky.interval.is_some());
    for class_name in [
        "com.intellij.air.Broken",
        "com.intellij.air.Stable",
        "com.intellij.air.Prerequisite",
        "com.intellij.air.NeverRan",
        "com.intellij.air.Once",
    ] {
        let class = class_of(&summary, class_name);
        assert!(class.rate.is_none() && class.interval.is_none(), "{class:?}");
    }
    // The lane's rate is over `stable` and `flaky` and nothing else.
    assert_eq!((summary.measured_classes, summary.lane_flake_rate), (2, 0.5));
    // The reasons the guest gave are kept, in the order they were observed.
    assert_eq!(
        class_of(&summary, "com.intellij.air.Prerequisite").skip_reasons,
        ["no claude on air-linux-1"]
    );
}

/// `no_tests` is not authoritative: folding it in as a zero-failure trial is how a broken selector becomes
/// evidence of stability.
#[test]
fn a_trial_that_discovered_nothing_does_not_vote() {
    let summary = flake_summary(&[
        trial(1, with_suite(document(Status::Passed), "A", None, &[passed("com.intellij.air.A")])),
        trial(2, document(Status::NoTests)),
    ]);
    assert_eq!((summary.authoritative_trials, summary.infrastructure_trials.len()), (1, 1));
    assert_eq!(summary.infrastructure_trials[0].code, "trial_discovered_nothing");
    // One of two excluded is past the fraction: the headline is withheld, the class is still published.
    assert!(!summary.reportable);
    assert!(
        text(summary.not_reportable_reason.as_deref()).contains("50% > 20%"),
        "{}",
        text(summary.not_reportable_reason.as_deref())
    );
    assert_eq!(class_of(&summary, "com.intellij.air.A").executed_trials, 1);
}

/// The four ways a trial is thrown out, each with the code that threw it out.
#[test]
fn every_excluded_trial_names_why() {
    let mut truncated = document(Status::Passed);
    truncated.source.integrity = Integrity::Truncated;
    let summary = flake_summary(&[
        dead_trial(1, "lease_unavailable"),
        trial(
            2,
            with_diagnostic(document(Status::InfrastructureError), "daemon watchdog expired: progress gap"),
        ),
        trial(3, document(Status::NoTests)),
        trial(4, truncated),
    ]);
    let codes: Vec<&str> = summary
        .infrastructure_trials
        .iter()
        .map(|exclusion| exclusion.code.as_str())
        .collect();
    assert_eq!(
        codes,
        [
            "lease_unavailable",
            "trial_infrastructure_error",
            "trial_discovered_nothing",
            "trial_incomplete_source"
        ]
    );
    assert_eq!(summary.infrastructure_trials[1].message, "daemon watchdog expired: progress gap");
    // Nothing was measured, and the summary says so rather than publishing zero as a clean lane.
    assert!(!summary.reportable);
    assert_eq!((summary.lane_flake_rate, summary.measured_classes), (0.0, 0));
    read_back_flake(&summary);
}

/// Past the fraction the survivors are not a random sample: the rate reads *low*, the damaging direction.
#[test]
fn the_exclusion_fraction_withholds_the_headline_and_keeps_the_evidence() {
    let mut trials: Vec<TrialAttempt> = (1..=5).map(|ordinal| dead_trial(ordinal, "worker_lost")).collect();
    for ordinal in 6..=8 {
        let report = if ordinal == 7 {
            with_suite(document(Status::Failed), "A", None, &[failed("com.intellij.air.A")])
        } else {
            with_suite(document(Status::Passed), "A", None, &[passed("com.intellij.air.A")])
        };
        trials.push(trial(ordinal, report));
    }
    let summary = flake_summary(&trials);
    assert!(!summary.reportable);
    let reason = text(summary.not_reportable_reason.as_deref());
    // 62.5% is an exact tie, which this controller's percent rendering rounds to even.
    assert!(
        reason.contains("5 of 8 trials were excluded") && reason.contains("(62% > 20%)"),
        "{reason}"
    );
    // The per-class evidence is still evidence: what is withheld is the headline.
    let class = class_of(&summary, "com.intellij.air.A");
    assert_eq!(
        (class.bucket, class.executed_trials, class.failed_trials),
        (FlakeBucket::Flaky, 3, 1)
    );
    // Exactly at the fraction is still reportable (`>`, not `>=`), and judging exactly half is too (`<`).
    assert_eq!(reportability(10, 2, 1, 1), None);
    assert_eq!(reportability(10, 0, 2, 4), None);
    assert_eq!(reportability(0, 0, 0, 0).as_deref(), Some("no trials were attempted"));
}

/// `f > n` is arithmetic that cannot have happened. A container that died before writing its XML is how it used to
/// happen: the class was failed and not executed.
#[test]
fn a_failure_whose_class_never_reached_the_xml_is_still_executed() {
    let mut report = document(Status::Failed);
    report.failures.push(Failure {
        source: FailureSource::Progress,
        suite: None,
        suite_timestamp: None,
        class_name: Some("com.intellij.air.NoXml".to_owned()),
        test_name: "Before all".to_owned(),
        kind: FailureKind::Container,
        r#type: None,
        message: "timeout".to_owned(),
        detail: None,
        relevant_frames: Vec::new(),
        message_truncated: false,
        detail_truncated: false,
    });
    let summary = flake_summary(&[trial(1, report.clone()), trial(2, report)]);
    let class = class_of(&summary, "com.intellij.air.NoXml");
    assert_eq!((class.executed_trials, class.failed_trials), (2, 2));
    assert_eq!(class.bucket, FlakeBucket::Broken);
    // The wire refuses `f > n` outright, so this is also what keeps the document readable.
    read_back_flake(&summary);
}

/// A class that failed every trial it ran is broken, not flaky: a permanently red lane is red, not unstable.
#[test]
fn a_broken_class_is_not_a_flaky_one() {
    let trials: Vec<TrialAttempt> = (1..=4)
        .map(|ordinal| {
            trial(
                ordinal,
                with_suite(
                    document(Status::Failed),
                    "A",
                    None,
                    &[failed("com.intellij.air.Broken"), passed("com.intellij.air.Stable")],
                ),
            )
        })
        .collect();
    let summary = flake_summary(&trials);
    assert_eq!(class_of(&summary, "com.intellij.air.Broken").bucket, FlakeBucket::Broken);
    assert!(summary.flake_set.is_empty());
    assert_eq!(summary.lane_flake_rate, 0.0);
    // One stable class was measured, so the summary is quotable: a red lane is not an unmeasured one.
    assert!(summary.reportable, "{}", text(summary.not_reportable_reason.as_deref()));
    assert_eq!(summary.measured_classes, 1);
    assert_eq!(summary.broken_set, ["com.intellij.air.Broken"]);
}

/// Failing only on the first trial, or only after it, is a claim about position in the sequence.
#[test]
fn the_order_signature_names_a_positional_failure() {
    let build = |failing: &[u32]| {
        let trials: Vec<TrialAttempt> = (1..=3)
            .map(|ordinal| {
                let (case, status) = if failing.contains(&ordinal) {
                    (failed("com.intellij.air.Ordered"), Status::Failed)
                } else {
                    (passed("com.intellij.air.Ordered"), Status::Passed)
                };
                trial(ordinal, with_suite(document(status), "A", None, &[case]))
            })
            .collect();
        flake_summary(&trials)
    };
    let signature = |summary: &FlakeSummary| class_of(summary, "com.intellij.air.Ordered").order_signature;
    assert_eq!(signature(&build(&[1])), Some(OrderSignature::FirstTrialOnly));
    let after = build(&[2, 3]);
    assert_eq!(signature(&after), Some(OrderSignature::AfterFirstTrial));
    assert_eq!(after.order_suspects, ["com.intellij.air.Ordered"]);
    // Failing in every trial carries no positional signal, and neither does failing on both sides of the boundary.
    assert_eq!(signature(&build(&[1, 2, 3])), None);
    assert_eq!(signature(&build(&[1, 2])), None);
}

/// The trials are the matrix's rows *and* the aggregate's entries: one record. A trial that never reported still
/// has a wall time.
#[test]
fn a_trial_is_one_record_and_keeps_its_own_wall_time() {
    let mut reported = trial(2, with_suite(document(Status::Passed), "A", None, &[passed("com.intellij.air.A")]));
    reported.duration_ms = None;
    let mut dead = dead_trial(1, "worker_lost");
    dead.guest_free_bytes_before = Some(1_000);
    let summary = flake_summary(&[reported, dead]);
    let ordinals: Vec<u32> = summary.trials.iter().map(|trial| trial.ordinal).collect();
    assert_eq!(ordinals, [1, 2]);
    // No measured duration falls back to the report's own, and a trial with neither is zero rather than absent.
    assert_eq!((summary.trials[0].duration_ms, summary.trials[1].duration_ms), (0.0, 60_000.0));
    assert_eq!(summary.trials[0].entry.status, EntryStatus::NoReport);
    assert!(summary.trials[0].entry.error.is_some());
    assert_eq!(
        (summary.trials[0].guest_free_bytes_before, summary.trials[0].guest_free_bytes_after),
        (Some(1_000), None)
    );
    read_back_flake(&summary);
}

/// Every declared array is an array, so a reader never decides for itself whether `null` meant "none".
#[test]
fn an_empty_summary_still_writes_every_array() {
    let summary = flake_summary(&[]);
    let encoded = serde_json::to_string(&summary).unwrap();
    for field in [
        "trials",
        "infrastructureTrials",
        "classes",
        "flakeSet",
        "brokenSet",
        "stableSet",
        "notMeasured",
        "unstablePrerequisite",
        "insufficientEvidence",
        "orderSuspects",
    ] {
        assert!(
            encoded.contains(&format!(r#""{field}":[]"#)),
            "{field} is not an empty array: {encoded}"
        );
    }
    assert!(!summary.reportable);
    assert_eq!(text(summary.not_reportable_reason.as_deref()), "no trials were attempted");
    read_back_flake(&summary);
}

/// The honesty guard withholds a run that judged fewer than half of the classes it observed, however many trials
/// counted as authoritative. This pins the fix for `flake --lane ui --trials 12 --reset none` on `air-linux-1` on
/// 2026-08-23, which came back `reportable: true` at a 100% lane rate over one measured class.
#[test]
fn the_honesty_guard_withholds_a_run_that_judged_almost_no_classes() {
    let mut trials = vec![trial(
        1,
        with_suite(
            document(Status::Failed),
            "lane",
            None,
            &[failed("com.intellij.air.Broken"), passed("com.intellij.air.Stable")],
        ),
    )];
    // Trials 2-12 ran nothing at all, and say so honestly, naming the classes they ruled out.
    for ordinal in 2..=12 {
        let lost = "the Air flow lane lost the IDE its suites share";
        let empty = with_ruled_out(
            with_ruled_out(document(Status::AllSkipped), "com.intellij.air.NeverRan", lost),
            "com.intellij.air.AlsoNeverRan",
            lost,
        );
        trials.push(trial(ordinal, empty));
    }
    let summary = flake_summary(&trials);
    assert_eq!(
        (summary.authoritative_trials, summary.infrastructure_trials.len()),
        (12, 0),
        "the fixture no longer reproduces the shape"
    );
    assert!(
        !summary.reportable,
        "a run that judged 1 of {} observed classes is reportable again; the measured-class floor is gone",
        summary.classes.len()
    );
    assert!(
        text(summary.not_reportable_reason.as_deref()).contains("observed classes were measured"),
        "{}",
        text(summary.not_reportable_reason.as_deref())
    );
    // The floor withholds the headline, not the evidence.
    assert_eq!(summary.measured_classes, 1);
    assert!(!summary.not_measured.is_empty());
}

/// The floor is a floor, not a nonzero check: the healthy 2026-08-23 baseline measured 22 of 23 classes.
#[test]
fn a_lane_that_judged_most_of_its_classes_stays_reportable() {
    let trials: Vec<TrialAttempt> = (1..=3)
        .map(|ordinal| {
            let run = with_suite(
                document(Status::Passed),
                "lane",
                None,
                &[
                    passed("com.intellij.air.A"),
                    passed("com.intellij.air.B"),
                    passed("com.intellij.air.C"),
                ],
            );
            trial(
                ordinal,
                with_ruled_out(run, "com.intellij.air.NeedsCodex", "codex is not installed on this guest"),
            )
        })
        .collect();
    let summary = flake_summary(&trials);
    assert!(summary.reportable, "{}", text(summary.not_reportable_reason.as_deref()));
    assert_eq!(summary.measured_classes, 3);
    assert_eq!(summary.unstable_prerequisite.len() + summary.not_measured.len(), 1);
}

/// A run that started nothing and failed only as containers judged nobody. From the same 2026-08-23 run: a class
/// genuinely failed once in trial 1 and "failed" in trials 2-12 only because there was no IDE left to run it on.
#[test]
fn a_container_failure_on_a_dead_daemon_is_not_an_execution() {
    let mut trials = vec![trial(
        1,
        with_suite(document(Status::Failed), "lane", None, &[failed("com.intellij.air.Broken")]),
    )];
    for ordinal in 2..=4 {
        let mut lost = with_suite(document(Status::Failed), "lane", None, &[failed("com.intellij.air.Broken")]);
        lost.execution.tests_started = 0;
        lost.execution.container_failures = 1;
        trials.push(trial(ordinal, lost));
    }
    let summary = flake_summary(&trials);
    let class = class_of(&summary, "com.intellij.air.Broken");
    assert_eq!(
        (class.executed_trials, class.failed_trials),
        (1, 1),
        "the lost-IDE trials voted anyway"
    );
    assert_eq!(class.bucket, FlakeBucket::InsufficientEvidence);
    read_back_flake(&summary);
}
