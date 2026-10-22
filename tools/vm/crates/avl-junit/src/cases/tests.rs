use avl_wire::daemon::{
    ContainerFailed, ExecutionIdentity, PlanStarted, RunEvent, RunEventKind, Skipped, TestFinished, TestStarted, TypedFailure,
};
use pretty_assertions::assert_eq;

use super::*;
use crate::testing::run_stream;

fn identity(id: &str, name: &str, class: Option<&str>) -> ExecutionIdentity {
    ExecutionIdentity {
        id: id.to_owned(),
        display_name: name.to_owned(),
        class_name: class.map(str::to_owned),
        method_name: None,
    }
}

fn started(id: &str, name: &str) -> RunEvent {
    RunEvent::synthesized(RunEventKind::TestStarted(TestStarted {
        timestamp: format!("start-{id}"),
        execution: identity(id, name, Some("Suite")),
    }))
}

fn finished(id: &str, name: &str, status: &str, typed: TypedFailure) -> RunEvent {
    RunEvent::synthesized(RunEventKind::TestFinished(TestFinished {
        timestamp: format!("end-{id}"),
        execution: identity(id, name, Some("Suite")),
        status: status.to_owned(),
        duration_ms: 7,
        failure: typed,
        report: ScenarioReport::default(),
    }))
}

fn skipped(id: &str, name: &str, reason: &str) -> RunEvent {
    RunEvent::synthesized(RunEventKind::TestSkipped(Skipped {
        timestamp: format!("skip-{id}"),
        execution: identity(id, name, Some("Suite")),
        reason: reason.to_owned(),
    }))
}

fn plan(classes: &[&str]) -> RunEvent {
    RunEvent::synthesized(RunEventKind::PlanStarted(PlanStarted {
        timestamp: "plan".to_owned(),
        discovered_executions: 1,
        class_names: classes.iter().map(|class| (*class).to_owned()).collect(),
        executions: Vec::new(),
    }))
}

#[test]
fn the_recorded_stream_reads_as_one_case_per_scenario_and_prologue() {
    let read: Vec<(String, String, i64, &'static str)> = cases(&run_stream())
        .into_iter()
        .map(|case| {
            let outcome = match case.outcome {
                CaseOutcome::Passed => "passed",
                CaseOutcome::Failed(_) => "failed",
                CaseOutcome::Skipped(_) => "skipped",
                CaseOutcome::Unfinished => "unfinished",
            };
            (case.class_name, case.name, case.duration_ms, outcome)
        })
        .collect();
    let expected = [
        ("AirComposerGeneratedFlowUiTest", "send-prompt", 12_345, "passed"),
        ("AirComposerGeneratedFlowUiTest", "attach-file", 30_500, "failed"),
        ("AirSessionTabsGeneratedFlowUiTest", "open-tab", 0, "skipped"),
        ("AirRegistryGeneratedFlowUiTest", "registry", 0, "failed"),
    ]
    .map(|(class, name, millis, outcome)| (class.to_owned(), name.to_owned(), millis, outcome));
    assert_eq!(read, expected);
}

#[test]
fn a_typed_failure_keeps_its_type_message_and_detail() {
    let events = run_stream();
    let failed = cases(&events).into_iter().nth(1).expect("the failed scenario");
    let CaseOutcome::Failed(failure) = failed.outcome else {
        panic!("the scenario failed: {:?}", failed.outcome);
    };
    assert_eq!(failure.kind, FailureOrigin::Test);
    assert_eq!(
        failure.r#type.as_deref(),
        Some("com.intellij.air.integration.AirFlowAssertionError")
    );
    assert_eq!(
        failure.headline(),
        "instruction 4 assert-chip-visible: the chip <a.txt> is not visible"
    );
    assert!(
        failure.text().starts_with("instruction 4 assert-chip-visible"),
        "{}",
        failure.text()
    );
    assert!(failure.text().ends_with("(AirChipAssertions.kt:41)"), "{}", failure.text());
}

#[test]
fn the_report_of_a_scenario_rides_on_its_case() {
    let first = cases(&run_stream()).into_iter().next().expect("the green scenario");
    assert_eq!(first.report.notes, ["the reply arrived in one turn & named the file 'a.txt'"]);
    assert_eq!(
        first.report.metrics.keys().map(String::as_str).collect::<Vec<_>>(),
        ["replyMs", "turns"]
    );
}

#[test]
fn an_aborted_test_is_a_skip_and_a_test_still_running_at_the_end_is_unfinished() {
    let events = [
        started("a", "aborted"),
        finished(
            "a",
            "aborted",
            STATUS_ABORTED,
            TypedFailure {
                message: Some("Assumption failed: no codex".to_owned()),
                ..TypedFailure::default()
            },
        ),
        started("b", "hangs"),
    ];
    let outcomes: Vec<CaseOutcome> = cases(&events).into_iter().map(|case| case.outcome).collect();
    assert_eq!(
        outcomes,
        [
            CaseOutcome::Skipped("Assumption failed: no codex".to_owned()),
            CaseOutcome::Unfinished
        ]
    );
}

#[test]
fn a_skip_of_a_started_test_closes_its_case_in_place() {
    let events = [
        started("a", "first"),
        started("b", "second"),
        skipped("a", "first", "the isolation is spent"),
    ];
    let read: Vec<(String, CaseOutcome)> = cases(&events).into_iter().map(|case| (case.name, case.outcome)).collect();
    assert_eq!(
        read,
        [
            ("first".to_owned(), CaseOutcome::Skipped("the isolation is spent".to_owned())),
            ("second".to_owned(), CaseOutcome::Unfinished),
        ]
    );
}

#[test]
fn a_container_failure_with_no_class_is_named_after_its_display_name() {
    let events = [RunEvent::synthesized(RunEventKind::ContainerFailed(ContainerFailed {
        timestamp: "t".to_owned(),
        execution: identity("[engine:junit-jupiter]", "JUnit Jupiter", None),
        failure: TypedFailure::default(),
    }))];
    let [case] = cases(&events).try_into().expect("one case");
    assert_eq!((case.class_name.as_str(), case.name.as_str()), ("JUnit Jupiter", "JUnit Jupiter"));
    let CaseOutcome::Failed(failure) = case.outcome else {
        panic!("the container failed");
    };
    assert_eq!(
        (failure.kind, failure.message.as_str()),
        (FailureOrigin::Container, "JUnit Jupiter failed")
    );
}

#[test]
fn a_class_of_the_plan_that_no_record_reached_is_unreported() {
    let events = [plan(&["Suite", "Dropped"]), started("a", "first")];
    assert_eq!(unreported_classes(&events), ["Dropped"]);
    assert_eq!(unreported_classes(&run_stream()), Vec::<String>::new());
}

/// The plan of the lane daemon names a class by the `testClassName` of its document, and its records by the FQN. The
/// two name one class, so the class is reached; a class of the plan that no record names stays unreported.
#[test]
fn a_plan_of_simple_names_is_reached_by_records_of_fqns() {
    let events = [
        plan(&["AgentProjectWorkflowsUiTest", "AgentWelcomeScreenSurfacesUiTest"]),
        RunEvent::synthesized(RunEventKind::TestStarted(TestStarted {
            timestamp: "t".to_owned(),
            execution: identity(
                "a",
                "open",
                Some("com.intellij.air.integration.flow.workflows.AgentProjectWorkflowsUiTest"),
            ),
        })),
    ];
    assert_eq!(unreported_classes(&events), ["AgentWelcomeScreenSurfacesUiTest"]);
    assert_eq!(
        simple_class_name("com.intellij.air.integration.flow.workflows.AgentProjectWorkflowsUiTest"),
        "AgentProjectWorkflowsUiTest"
    );
}
