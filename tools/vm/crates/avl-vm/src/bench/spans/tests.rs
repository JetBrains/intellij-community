use avl_base::Reporter;
use pretty_assertions::assert_eq;

use super::report;
use crate::bench::command::replay;
use crate::bench::options::{RunSelection, TraceArgs, Window};
use crate::bench::testing::{assert_golden, fixture_session};

fn args(run: Option<&str>, from: Option<f64>, to: Option<f64>, min: f64, all: bool) -> TraceArgs {
    TraceArgs {
        selection: RunSelection {
            session: "session".to_owned(),
            run: run.map(str::to_owned),
        },
        window: Window { from, to },
        min,
        all,
    }
}

/// A replayed copy of the fixture session, which has its `summary.json`.
fn replayed() -> (tempfile::TempDir, std::path::PathBuf) {
    let (dir, session) = fixture_session();
    let (reporter, _, _) = Reporter::in_memory("vm");
    replay(&session, &reporter).expect("a summary");
    (dir, session)
}

/// The text of the default report of the fixture equals `testdata/bench/trace.txt`.
#[test]
fn the_default_trace_matches_the_golden() {
    let (_dir, session) = replayed();
    let report = report(&session, &args(None, None, None, 10.0, false)).expect("a report");
    assert_eq!(report.run, "non-modal-run-01");
    assert_eq!(report.median_by, Some("welcomeBecameVisible"));
    let text = format!("{}\n", report.text()).replace(&session.display().to_string(), "<session>");
    assert_golden("trace.txt", &text);
}

#[test]
fn the_helper_twins_stay_with_all_and_the_window_and_the_minimum_narrow_the_spans() {
    let (_dir, session) = replayed();
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

#[test]
fn a_named_run_has_no_median_and_a_missing_anchor_widens_the_window() {
    let (_dir, session) = replayed();
    let modal = report(&session, &args(Some("modal-run-01"), None, None, 0.0, true)).expect("a report");
    assert_eq!(modal.median_by, None);
    assert_eq!(modal.window.from_ms.to_string(), "0", "the modal arm has no frameBecameVisible");
    assert_eq!(modal.spans.len(), modal.total_spans, "the window is the whole run");
    assert!(
        modal
            .text()
            .starts_with("vm bench trace session modal-run-01\nanchors (ms from the process start): welcomeBecameVisible ")
    );
}

#[test]
fn a_bad_run_an_empty_window_and_a_session_without_a_summary_are_refused() {
    let (_dir, session) = fixture_session();
    let refusal = report(&session, &args(None, None, None, 10.0, false)).expect_err("no summary");
    assert_eq!(refusal.code, "no_summary");

    let (_dir, session) = replayed();
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
    let (_dir, session) = replayed();
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
