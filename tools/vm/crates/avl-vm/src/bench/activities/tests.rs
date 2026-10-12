use avl_base::Reporter;
use pretty_assertions::assert_eq;

use super::report;
use crate::bench::command::replay;
use crate::bench::options::{ActivitiesArgs, DEFAULT_TOP, RunSelection, Window};
use crate::bench::testing::{assert_golden, fixture_session};

fn args(run: Option<&str>, from: Option<f64>, to: Option<f64>, top: u32) -> ActivitiesArgs {
    ActivitiesArgs {
        selection: RunSelection {
            session: "session".to_owned(),
            run: run.map(str::to_owned),
        },
        window: Window { from, to },
        top,
    }
}

/// A replayed copy of the fixture session, which has its `summary.json`.
fn replayed() -> (tempfile::TempDir, std::path::PathBuf) {
    let (dir, session) = fixture_session();
    let (reporter, _, _) = Reporter::in_memory("vm");
    replay(&session, &reporter).expect("a summary");
    (dir, session)
}

/// The text of the default report of the fixture equals `testdata/bench/activities.txt`.
#[test]
fn the_default_activities_match_the_golden() {
    let (_dir, session) = replayed();
    let report = report(&session, &args(None, None, None, DEFAULT_TOP)).expect("a report");
    assert_eq!(report.run, "non-modal-run-01");
    assert_eq!(report.median_by, Some("welcomeBecameVisible"));
    let text = format!("{}\n", report.text()).replace(&session.display().to_string(), "<session>");
    assert_golden("activities.txt", &text);
}

#[test]
fn the_activities_are_by_duration_and_the_plugins_by_the_sum_without_the_helper_twins() {
    let (_dir, session) = replayed();
    let report = report(&session, &args(None, None, None, DEFAULT_TOP)).expect("a report");
    let durations: Vec<String> = report.activities.iter().map(|line| line.span.duration_ms.to_string()).collect();
    assert_eq!(durations, vec!["4500", "1265", "30", "12", "4.2"]);
    assert!(report.activities.iter().all(|line| !line.span.helper));
    assert_eq!((report.total_activities, report.in_window, report.waiters), (5, 5, 1));
    assert!(report.activities[0].waits, "the first activity is over 4 s");
    let plugins: Vec<(Option<&str>, usize, String, &str)> = report
        .plugins
        .iter()
        .map(|line| {
            (
                line.plugin.as_deref(),
                line.count,
                line.sum_ms.to_string(),
                line.longest_class.as_str(),
            )
        })
        .collect();
    assert_eq!(
        plugins,
        vec![
            (
                Some("tanvd.grazi"),
                1,
                "4500".to_owned(),
                "com.intellij.grazie.spellcheck.engine.GrazieSpellCheckerEngine$SpellerLoadActivity"
            ),
            (
                Some("org.jetbrains.kotlin"),
                1,
                "1265".to_owned(),
                "org.jetbrains.kotlin.idea.base.plugin.KotlinBundledRefresher"
            ),
            (Some("com.example"), 2, "42".to_owned(), "com.example.EarlyActivity"),
            (
                Some("com.intellij"),
                1,
                "4.2".to_owned(),
                "com.intellij.psi.impl.file.impl.PsiVfsInitProjectActivity"
            ),
        ]
    );
}

#[test]
fn the_window_and_the_top_narrow_the_tables_and_the_footer_counts_the_rest() {
    let (_dir, session) = replayed();
    let narrow = report(&session, &args(None, Some(1714.0), Some(3225.0), 2)).expect("a report");
    assert_eq!((narrow.total_activities, narrow.in_window, narrow.waiters), (5, 4, 1));
    assert_eq!((narrow.activities.len(), narrow.plugins.len(), narrow.total_plugins), (2, 2, 4));
    let text = narrow.text();
    assert!(text.contains("\nby class, 2 of 4 by duration:\n"), "{text}");
    assert!(text.contains("\nby plugin, 2 of 4 by the sum of the durations:\n"), "{text}");
    assert!(
        text.ends_with("\n5 activities, 4 start inside 1714..3225 ms, 1 wait over 4 s, 0 cut by the quit"),
        "{text}"
    );

    let empty = report(&session, &args(None, Some(0.0), Some(1.0), 2)).expect("a report");
    assert!(empty.activities.is_empty() && empty.plugins.is_empty());
    assert!(
        empty
            .text()
            .ends_with("\nno activity starts in the window\n5 activities, 0 start inside 0..1 ms, 0 wait over 4 s, 0 cut by the quit"),
        "{}",
        empty.text()
    );
}

/// The fixture quits after its last activity. A copy whose quit starts at 3300 ms cuts the two activities that run
/// at that time, and the cut does not depend on the duration.
#[test]
fn an_activity_that_runs_into_the_quit_is_cut_by_the_quit() {
    let (_dir, session) = replayed();
    let trace = session.join("non-modal-run-01/opentelemetry.json");
    let mut document: serde_json::Value = serde_json::from_str(&std::fs::read_to_string(&trace).expect("a trace")).expect("JSON");
    let spans = document["data"][0]["spans"].as_array_mut().expect("the spans");
    let quit = spans
        .iter_mut()
        .find(|span| span["operationName"] == "application.exit")
        .expect("the quit");
    quit["startTime"] = (1_790_860_689_816_000_i64 + 3_300_000).into();
    std::fs::write(&trace, document.to_string()).expect("a trace");

    let report = report(&session, &args(None, None, None, DEFAULT_TOP)).expect("a report");
    let marks: Vec<(&str, bool, bool)> = report
        .activities
        .iter()
        .map(|line| (line.span.class.as_deref().unwrap_or_default(), line.waits, line.cut))
        .collect();
    assert_eq!(
        marks[..2],
        [
            (
                "com.intellij.grazie.spellcheck.engine.GrazieSpellCheckerEngine$SpellerLoadActivity",
                true,
                true
            ),
            ("org.jetbrains.kotlin.idea.base.plugin.KotlinBundledRefresher", false, true),
        ]
    );
    assert!(marks[2..].iter().all(|(_, waits, cut)| !waits && !cut), "{marks:?}");
    assert_eq!(report.cut_by_quit, 2);
    let text = report.text();
    assert!(text.contains("  tanvd.grazi           waits  cut by the quit\n"), "{text}");
    assert!(text.contains("  org.jetbrains.kotlin  cut by the quit\n"), "{text}");
    assert!(
        text.ends_with(
            ", 1 wait over 4 s, 2 cut by the quit\n2 activities were still running at the quit; raise --hold to see their full duration."
        ),
        "{text}"
    );
}

/// The footer asks for a longer hold only when the quit cut an activity.
#[test]
fn a_cut_activity_asks_for_a_longer_hold() {
    let (_dir, session) = replayed();
    let mut report = report(&session, &args(None, None, None, DEFAULT_TOP)).expect("a report");
    assert_eq!(report.cut_by_quit, 0);
    assert!(!report.text().contains("--hold"), "{}", report.text());
    report.cut_by_quit = 1;
    assert!(
        report
            .text()
            .ends_with(", 1 cut by the quit\n1 activity was still running at the quit; raise --hold to see its full duration."),
        "{}",
        report.text()
    );
}

#[test]
fn a_modal_run_has_its_own_activities_and_an_empty_window_is_refused() {
    let (_dir, session) = replayed();
    let modal = report(&session, &args(Some("modal-run-01"), None, None, DEFAULT_TOP)).expect("a report");
    assert_eq!(modal.median_by, None);
    assert!(
        modal.text().starts_with("vm bench activities session modal-run-01\n"),
        "{}",
        modal.text()
    );
    let refusal = report(&session, &args(None, Some(5000.0), Some(10.0), DEFAULT_TOP)).expect_err("an empty window");
    assert_eq!(refusal.code, "usage");
    assert_eq!(
        refusal.message,
        "the window 5000..10 ms of non-modal-run-01 is empty: its start is not before its end"
    );
    let (_dir, session) = fixture_session();
    let refusal = report(&session, &args(None, None, None, DEFAULT_TOP)).expect_err("no summary");
    assert_eq!(refusal.code, "no_summary");
}
