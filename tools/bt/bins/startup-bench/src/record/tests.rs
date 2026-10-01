use pretty_assertions::assert_eq;

use super::{LaunchFacts, RunId, RunKind, WELCOME_BECAME_VISIBLE, collect};
use crate::arm::Arm;
use crate::testkit::fixture_session;

fn id(arm: Arm) -> RunId {
    RunId {
        arm,
        kind: RunKind::Measured,
        index: 1,
    }
}

#[test]
fn a_non_modal_run_passes_the_gate_and_has_the_welcome_spans() {
    let (_dir, session) = fixture_session();
    let record = collect(&session.join("non-modal-run-01"), &id(Arm::NonModal), LaunchFacts::default());
    assert_eq!(record.reason, None);
    assert!(record.valid);
    for metric in [
        WELCOME_BECAME_VISIBLE,
        "frameBecameInteractive",
        "totalDuration",
        "classLoading.count",
        "pluginClasses",
        "ProjectManager.openAsync",
        "welcome screen painted",
        "welcome left toolbar first fill",
        "welcome right tab body: createContent trial.started.banner",
    ] {
        assert!(record.metrics.contains_key(metric), "{metric}: {:?}", record.metrics.keys());
    }
    assert!(!record.metrics.keys().any(|metric| metric.ends_with(": scheduled")));
    assert!(record.edt_samples.is_some_and(|samples| samples > 0));
    assert_eq!(record.top_plugins.first().map(|count| count.name.as_str()), Some("com.intellij"));
}

#[test]
fn a_modal_run_without_the_report_is_valid_with_a_note() {
    let (_dir, session) = fixture_session();
    let record = collect(&session.join("modal-run-01"), &id(Arm::Modal), LaunchFacts::default());
    assert!(record.valid, "{:?}", record.reason);
    assert_eq!(record.notes, vec!["no startup-stats.json".to_owned()]);
}

#[test]
fn the_gate_names_what_is_missing() {
    let (_dir, session) = fixture_session();
    let non_modal = session.join("non-modal-run-01");
    let record = collect(&non_modal, &id(Arm::Modal), LaunchFacts::default());
    assert_eq!(
        record.reason.as_deref(),
        Some("welcome.screen.became.visible has is_modal=false, and the modal arm expects is_modal=true")
    );
    assert!(record.metrics.is_empty(), "a failed run reports no number");

    std::fs::write(non_modal.join("log/idea.log"), "nothing\n").expect("a log");
    let record = collect(&non_modal, &id(Arm::NonModal), LaunchFacts::default());
    assert_eq!(
        record.reason.as_deref(),
        Some("log/idea.log has no line \"Opened the welcome screen project\"")
    );

    let failure = LaunchFacts {
        failure: Some("no welcome.screen.became.visible within 180 s".to_owned()),
        ..LaunchFacts::default()
    };
    let record = collect(&session.join("modal-run-01"), &id(Arm::Modal), failure);
    assert_eq!(record.reason.as_deref(), Some("no welcome.screen.became.visible within 180 s"));

    std::fs::remove_file(session.join("modal-run-01/fus.jsonl")).expect("a removal");
    let record = collect(&session.join("modal-run-01"), &id(Arm::Modal), LaunchFacts::default());
    assert_eq!(record.reason.as_deref(), Some("no fus.jsonl"));
}

#[test]
fn a_truncated_trace_is_a_note_and_an_unknown_report_is_a_failure() {
    let (_dir, session) = fixture_session();
    let run = session.join("non-modal-run-01");
    let trace = std::fs::read_to_string(run.join("opentelemetry.json")).expect("a trace");
    std::fs::write(run.join("opentelemetry.json"), &trace[..trace.len() / 2]).expect("a trace");
    let record = collect(&run, &id(Arm::NonModal), LaunchFacts::default());
    assert!(record.valid, "{:?}", record.reason);
    assert!(
        record
            .notes
            .contains(&"opentelemetry.json is truncated; the reader closed it".to_owned())
    );

    let report = std::fs::read_to_string(run.join("startup-stats.json")).expect("a report");
    std::fs::write(run.join("startup-stats.json"), report.replace("\"38\"", "\"40\"")).expect("a report");
    let record = collect(&run, &id(Arm::NonModal), LaunchFacts::default());
    assert_eq!(
        record.reason.as_deref(),
        Some("startup-stats.json: version 40 is not supported, only 38")
    );
}

#[test]
fn a_run_directory_name_round_trips() {
    for name in ["modal-prime", "non-modal-run-07", "open-project-run-12"] {
        assert_eq!(RunId::parse(name).map(|id| id.dir_name()).as_deref(), Some(name));
    }
    for name in ["template", "modal-run-00", "sideways-run-01", "modal-run-x"] {
        assert_eq!(RunId::parse(name), None, "{name}");
    }
}
