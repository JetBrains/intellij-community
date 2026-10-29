use pretty_assertions::assert_eq;

use super::report;
use crate::bench::arm::Arm;
use crate::bench::options::{RunSelection, TraceArgs, Window};
use crate::bench::testing::{assert_golden, fixture_session, replayed_session};

fn args(run: Option<&str>, from: Option<f64>, to: Option<f64>, min: f64, all: bool) -> TraceArgs {
    TraceArgs {
        selection: RunSelection {
            session: "session".to_owned(),
            run: run.map(str::to_owned),
            arm: None,
        },
        window: Window { from, to },
        min,
        all,
        overlap: false,
    }
}

/// The arguments of the median run of `arm`, with every span of its window.
fn of_arm(arm: Arm) -> TraceArgs {
    let mut args = args(None, None, None, 0.0, true);
    args.selection.arm = Some(arm);
    args
}

/// The text of the default report of the fixture equals `testdata/bench/trace.txt`.
#[test]
fn the_default_trace_matches_the_golden() {
    let (_dir, session) = replayed_session();
    let report = report(&session, &args(None, None, None, 10.0, false)).expect("a report");
    assert_eq!(report.run, "non-modal-run-01");
    assert_eq!(report.median_by, Some("welcomeBecameVisible"));
    let text = format!("{}\n", report.text()).replace(&session.display().to_string(), "<session>");
    assert_golden("trace.txt", &text);
}

#[test]
fn the_helper_twins_stay_with_all_and_the_window_and_the_minimum_narrow_the_spans() {
    let (_dir, session) = replayed_session();
    let default = report(&session, &args(None, None, None, 10.0, false)).expect("a report");
    assert!(default.spans.iter().all(|line| !line.span.helper && line.span.duration_ms >= 10.0));
    let all = report(&session, &args(None, None, None, 10.0, true)).expect("a report");
    let helpers: Vec<&str> = all
        .spans
        .iter()
        .filter(|line| line.span.helper)
        .map(|line| line.span.name.as_str())
        .collect();
    assert_eq!(
        helpers,
        vec!["run init activity WelcomeScreenInitActivity: scheduled", "run activity: scheduled"]
    );

    let narrow = report(&session, &args(None, Some(1500.0), Some(1810.0), 0.0, true)).expect("a report");
    let names: Vec<&str> = narrow.spans.iter().map(|line| line.span.name.as_str()).collect();
    assert_eq!(
        names,
        vec![
            "run activity",
            "ProjectManager.openAsync",
            "project frame creating",
            "run init activity WelcomeScreenInitActivity: scheduled",
            "run init activity WelcomeScreenInitActivity"
        ]
    );
    assert_eq!(narrow.spans[0].span.class.as_deref(), Some("com.example.EarlyActivity"));
}

/// `--arm` picks the median run of an arm. A run without frameBecameVisible, such as a modal run or a project run, has
/// the window from the process start to the main anchor of its arm. A named run has no median.
#[test]
fn a_run_without_the_frame_has_the_window_from_the_process_start_to_the_main_anchor_of_its_arm() {
    let (_dir, session) = replayed_session();
    let modal = report(&session, &of_arm(Arm::MODAL)).expect("a report");
    assert_eq!(
        (modal.run.as_str(), modal.median_by),
        ("modal-run-01", Some("welcomeBecameVisible"))
    );
    assert_eq!((modal.window.from_ms, modal.window.to_ms), (0.0, 2336.0));
    assert!(
        modal.text().starts_with(
            "vm bench trace session modal-run-01 (the median run by welcomeBecameVisible)\nanchors (ms from the process start): \
             welcomeBecameVisible 2336  application.exit 7749\nwindow 0..2336 ms, spans that start in it, >= 0 ms with the helper \
             twins, 12 of 16 spans\n"
        ),
        "{}",
        modal.text()
    );
    let named = report(&session, &args(Some("modal-run-01"), None, None, 0.0, true)).expect("a report");
    assert_eq!(named.median_by, None);
    assert_eq!(named.window, modal.window);
    let project = report(&session, &args(Some("project-run-01"), None, None, 0.0, true)).expect("a report");
    assert_eq!(
        (project.window.from_ms, project.window.to_ms),
        (0.0, 2950.0),
        "the highlighted editor"
    );
}

/// A missing end widens the window to the end of the run.
#[test]
fn a_missing_end_widens_the_window_to_the_end_of_the_run() {
    let (_dir, session) = replayed_session();
    let path = session.join("non-modal-run-01/result.json");
    let mut result: serde_json::Value = serde_json::from_str(&std::fs::read_to_string(&path).expect("a result")).expect("a result");
    result["metrics"]
        .as_object_mut()
        .expect("the metrics")
        .remove("welcome screen painted");
    std::fs::write(&path, result.to_string()).expect("a result");
    let report = report(&session, &args(None, None, None, 0.0, true)).expect("a report");
    assert_eq!((report.window.from_ms, report.window.to_ms), (1714.0, 7943.9));
}

/// `--arm` refuses an arm that the session lacks, and a named run of another arm.
#[test]
fn an_arm_that_the_session_lacks_or_a_run_of_another_arm_is_refused() {
    let (_dir, session) = replayed_session();
    let refusal = report(&session, &of_arm(Arm::PROJECT)).expect_err("no project arm");
    assert!(
        refusal.message.ends_with("has no project arm; its arms are modal, non-modal"),
        "{}",
        refusal.message
    );
    let mut both = args(Some("non-modal-run-01"), None, None, 10.0, false);
    both.selection.arm = Some(Arm::MODAL);
    let refusal = report(&session, &both).expect_err("a run of another arm");
    assert_eq!(
        refusal.message,
        "non-modal-run-01 is a run of the non-modal arm, and --arm names the modal arm; drop one of them"
    );
}

/// `--overlap` also keeps the spans that start before the window and run into it, and every span that starts in it.
/// The end of a span comes from its microseconds.
#[test]
fn the_overlap_keeps_the_spans_that_run_into_the_window() {
    let (_dir, session) = replayed_session();
    let names = |overlap: bool| {
        let args = TraceArgs {
            overlap,
            ..args(None, Some(2200.0), Some(2300.0), 0.0, false)
        };
        let report = report(&session, &args).expect("a report");
        let names: Vec<String> = report.spans.iter().map(|line| line.span.name.clone()).collect();
        (names, report.text())
    };
    let (starts, _) = names(false);
    assert_eq!(starts, ["toolwindow creating"]);
    let (runs, text) = names(true);
    assert_eq!(
        runs,
        [
            "welcome screen painted",
            "welcome screen project opening",
            "ProjectManager.openAsync",
            "restoreEditors",
            "run activity",
            "run activity",
            "toolwindow creating",
        ]
    );
    assert!(
        text.contains("\nwindow 2200..2300 ms, spans that run in it, >= 0 ms, 7 of 36 spans\n"),
        "{text}"
    );
    let (_dir, session) = replayed_session();
    let report = report(&session, &args(None, None, None, 10.0, false)).expect("a report");
    let toolwindow = report
        .spans
        .iter()
        .find(|line| line.span.name == "toolwindow creating")
        .expect("the span");
    assert_eq!(
        (toolwindow.span.start_ms, toolwindow.span.duration_ms, toolwindow.span.end_ms),
        (2233.0, 61.6, 2294.7),
        "the end rounds once"
    );
}

#[test]
fn a_bad_run_an_empty_window_and_a_session_without_a_summary_are_refused() {
    let (_dir, session) = fixture_session();
    let refusal = report(&session, &args(None, None, None, 10.0, false)).expect_err("no summary");
    assert_eq!(refusal.code, "no_summary");

    let (_dir, session) = replayed_session();
    let refusal = report(&session, &args(Some("non-modal-run-09"), None, None, 10.0, false)).expect_err("no such run");
    assert_eq!(refusal.code, "usage");
    assert!(
        refusal
            .message
            .ends_with("has no run non-modal-run-09; its runs are modal-run-01, non-modal-run-01, project-run-01"),
        "{}",
        refusal.message
    );
    let refusal = report(&session, &args(Some("sandbox"), None, None, 10.0, false)).expect_err("not a run");
    assert_eq!(
        refusal.message,
        "sandbox is not a run directory name: use <arm>-run-NN or <arm>-prime"
    );
    let refusal = report(&session, &args(None, Some(4000.0), None, 10.0, false)).expect_err("an empty window");
    assert_eq!(
        refusal.message,
        "the window 4000..3225 ms of non-modal-run-01 is empty: its start is not before its end"
    );
}

#[test]
fn a_truncated_trace_is_a_note_of_the_text() {
    let (_dir, session) = replayed_session();
    let trace = session.join("non-modal-run-01/opentelemetry.json");
    let text = std::fs::read_to_string(&trace).expect("a trace");
    let cut = text.find(r#""operationName":"toolwindow creating""#).expect("a span");
    std::fs::write(&trace, &text[..cut]).expect("a truncated trace");
    let report = report(&session, &args(None, None, None, 10.0, false)).expect("a report");
    assert!(report.truncated);
    let text = report.text();
    let lines: Vec<&str> = text.lines().take(4).collect();
    assert_eq!(lines[3], "note: opentelemetry.json is truncated; the reader closed it", "{text}");
}
