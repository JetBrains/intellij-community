use avl_testkit::environment_lookup;
use avl_wire::daemon::{ExecutionIdentity, PlanStarted, RunEvent, RunEventKind, TestFinished, TestStarted, TypedFailure};
use pretty_assertions::assert_eq;

use super::*;
use crate::testing::{run_stream, testdata};

#[test]
fn the_recorded_stream_renders_its_golden() {
    let events = run_stream();
    expect_test::expect_file![testdata("run-stream.xml")].assert_eq(&render([events.as_slice()]));
}

#[test]
fn the_rendered_document_reads_back_with_the_counts_of_the_stream() {
    let events = run_stream();
    let parsed = bt_junit::parse_report(&render([events.as_slice()]));
    assert_eq!(
        parsed.integrity.status,
        bt_junit::IntegrityStatus::Complete,
        "{:?}",
        parsed.integrity.message
    );
    let counts: Vec<(String, u32, u32, u32)> = parsed
        .suites
        .iter()
        .map(|suite| (suite.name.clone(), suite.tests, suite.failures, suite.skipped))
        .collect();
    assert_eq!(
        counts,
        [
            ("AirComposerGeneratedFlowUiTest".to_owned(), 2, 1, 0),
            ("AirSessionTabsGeneratedFlowUiTest".to_owned(), 1, 0, 1),
            ("AirRegistryGeneratedFlowUiTest".to_owned(), 1, 1, 0),
        ]
    );
    let failure = parsed.suites[0].cases[1].failure.as_ref().expect("the failed scenario");
    assert_eq!(
        failure.message.as_deref(),
        Some("instruction 4 assert-chip-visible: the chip <a.txt> is not visible")
    );
    assert!(failure.detail.contains("the prompt holds ]]> & nothing else"), "{}", failure.detail);
}

#[test]
fn a_cut_off_run_fails_its_unfinished_scenario_and_the_suite_it_never_reached() {
    let events = [
        RunEvent::synthesized(RunEventKind::PlanStarted(PlanStarted {
            timestamp: "t".to_owned(),
            discovered_executions: 2,
            class_names: vec!["Reached".to_owned(), "Dropped".to_owned()],
            executions: Vec::new(),
        })),
        RunEvent::synthesized(RunEventKind::TestStarted(TestStarted {
            timestamp: "t".to_owned(),
            execution: ExecutionIdentity {
                id: "a".to_owned(),
                display_name: "hangs".to_owned(),
                class_name: Some("Reached".to_owned()),
                method_name: None,
            },
        })),
    ];
    let parsed = bt_junit::parse_report(&render([events.as_slice()]));
    let failed: Vec<(String, String, Option<String>)> = parsed
        .suites
        .iter()
        .flat_map(|suite| &suite.cases)
        .map(|case| {
            let kind = case.failure.as_ref().and_then(|failure| failure.r#type.clone());
            (case.class_name.clone(), case.name.clone(), kind)
        })
        .collect();
    assert_eq!(
        failed,
        [
            ("Reached".to_owned(), "hangs".to_owned(), Some(TYPE_UNFINISHED.to_owned())),
            ("Dropped".to_owned(), "Dropped".to_owned(), Some(TYPE_UNREPORTED.to_owned())),
        ]
    );
    let messages: Vec<String> = failed_cases(&events).into_iter().map(|case| case.failure.message).collect();
    assert_eq!(
        messages,
        [
            UNFINISHED_MESSAGE,
            "the run never reached the suite Dropped: the run stream ended before its first record"
        ]
    );
}

#[test]
fn the_failed_cases_are_those_that_fail_the_xml_with_its_failure() {
    let failed = failed_cases(&run_stream());
    let named: Vec<(&str, &str, Option<&str>)> = failed
        .iter()
        .map(|case| (case.class_name.as_str(), case.name.as_str(), case.failure.r#type.as_deref()))
        .collect();
    assert_eq!(
        named,
        [
            (
                "AirComposerGeneratedFlowUiTest",
                "attach-file",
                Some("com.intellij.air.integration.AirFlowAssertionError")
            ),
            ("AirRegistryGeneratedFlowUiTest", "registry", Some("prologue_failed")),
        ]
    );
    assert_eq!(
        failed[0].failure.headline(),
        "instruction 4 assert-chip-visible: the chip <a.txt> is not visible"
    );
}

/// The records of one attempt of the scenario `name` of `AirCodeNotesGeneratedFlowUiTest` with `status`.
fn attempt(name: &str, status: &str) -> [RunEvent; 2] {
    let execution = ExecutionIdentity {
        id: format!("[engine:lane-daemon]/[suite:code-notes]/[scenario:{name}]"),
        display_name: name.to_owned(),
        class_name: Some("AirCodeNotesGeneratedFlowUiTest".to_owned()),
        method_name: None,
    };
    let failure = (status != cases::STATUS_SUCCESSFUL).then(|| TypedFailure {
        kind: Some("lane_bridge".to_owned()),
        message: Some(format!("{name} timed out")),
        detail: None,
    });
    [
        RunEventKind::TestStarted(TestStarted {
            timestamp: "2026-10-29T12:00:00Z".to_owned(),
            execution: execution.clone(),
        }),
        RunEventKind::TestFinished(TestFinished {
            timestamp: "2026-10-29T12:00:02Z".to_owned(),
            execution,
            status: status.to_owned(),
            duration_ms: 2_000,
            failure: failure.unwrap_or_default(),
            report: avl_wire::report::ScenarioReport::default(),
        }),
    ]
    .map(|kind| RunEvent { raw: None, kind })
}

// A rerun of `lane-run` is a second case of the same class and name. A failed attempt that a later pass follows is no
// failed case of the run result, and the XML keeps both attempts in the order of the stream: TeamCity reads a test
// that failed and then passed.
#[test]
fn a_failure_that_a_later_pass_follows_is_no_failed_case_and_the_xml_keeps_both_attempts() {
    let events: Vec<RunEvent> = [
        attempt("flaky-note", cases::STATUS_FAILED),
        attempt("broken-note", cases::STATUS_FAILED),
        attempt("flaky-note", cases::STATUS_SUCCESSFUL),
        attempt("broken-note", cases::STATUS_FAILED),
    ]
    .into_iter()
    .flatten()
    .collect();
    let failed = failed_cases(&events);
    let names: Vec<&str> = failed.iter().map(|case| case.name.as_str()).collect();
    assert_eq!(names, ["broken-note", "broken-note"]);
    let parsed = bt_junit::parse_report(&render([events.as_slice()]));
    let attempts: Vec<(&str, bool)> = parsed.suites[0]
        .cases
        .iter()
        .map(|case| (case.name.as_str(), case.outcome == bt_junit::Outcome::Failed))
        .collect();
    assert_eq!(
        attempts,
        [
            ("flaky-note", true),
            ("broken-note", true),
            ("flaky-note", false),
            ("broken-note", true)
        ]
    );
}

#[test]
fn two_iterations_of_one_class_share_its_suite() {
    let events = run_stream();
    let parsed = bt_junit::parse_report(&render([events.as_slice(), events.as_slice()]));
    assert_eq!(parsed.suites.len(), 3);
    assert_eq!(parsed.suites[0].tests, 4);
}

#[test]
fn the_named_file_wins_over_the_bazel_file_and_a_blank_name_is_none() {
    let both = environment_lookup(&[(XML_OUTPUT_FILE, "/bazel/test.xml"), (JB_XML_OUTPUT_FILE, "/jb/test.xml")]);
    assert_eq!(destination(both).as_deref(), Some("/jb/test.xml"));
    let blank = environment_lookup(&[(XML_OUTPUT_FILE, "/bazel/test.xml"), (JB_XML_OUTPUT_FILE, " ")]);
    assert_eq!(destination(blank).as_deref(), Some("/bazel/test.xml"));
    assert_eq!(destination(|_| None), None);
}

#[test]
fn text_that_xml_forbids_is_dropped_and_a_cdata_end_is_split() {
    assert_eq!(escape("a\u{1b}[31m<b>\"&\u{ffff}"), "a[31m&lt;b&gt;&quot;&amp;");
    assert_eq!(escape("one\ntwo"), "one&#10;two");
    assert_eq!(cdata("x]]>y\u{7}"), "x]]]]><![CDATA[>y");
}

#[test]
fn seconds_have_three_decimals_and_a_negative_duration_is_zero() {
    assert_eq!(seconds(12_345), "12.345");
    assert_eq!(seconds(5), "0.005");
    assert_eq!(seconds(-3), "0.000");
}
