use pretty_assertions::assert_eq;

use super::{Anchor, Timeline, WAITER_MS};
use crate::bench::testing::fixture_session;

#[test]
fn the_timeline_has_the_anchors_and_the_spans_by_start_with_their_tags() {
    let (_dir, session) = fixture_session();
    let timeline = Timeline::read(&session.join("non-modal-run-01")).expect("a timeline");
    assert_eq!(timeline.origin_us, 1_790_860_689_816_000);
    assert!(!timeline.truncated);
    assert_eq!(
        timeline.anchors,
        vec![
            Anchor {
                name: "frameBecameVisible",
                ms: 1714.0
            },
            Anchor {
                name: "frameBecameInteractive",
                ms: 2528.0
            },
            Anchor {
                name: "welcomeBecameVisible",
                ms: 2718.0
            },
            Anchor {
                name: "welcome screen painted",
                ms: 3224.7
            },
            Anchor {
                name: "application.exit",
                ms: 7553.7
            },
        ]
    );
    assert_eq!(timeline.quit_ms(), Some(7553.7));
    assert!(timeline.spans.windows(2).all(|pair| pair[0].start_ms <= pair[1].start_ms));
    let refresher = timeline
        .spans
        .iter()
        .find(|span| span.class.as_deref() == Some("org.jetbrains.kotlin.idea.base.plugin.KotlinBundledRefresher"))
        .expect("the refresher");
    assert_eq!(refresher.name, "run activity");
    assert_eq!(refresher.plugin.as_deref(), Some("org.jetbrains.kotlin"));
    assert_eq!(refresher.start_ms.to_string(), "2100");
    assert_eq!(refresher.duration_ms.to_string(), "1265");
    assert!(!refresher.helper && !refresher.is_waiter());
    let waiters: Vec<&str> = timeline
        .spans
        .iter()
        .filter(|span| span.is_waiter())
        .filter_map(|span| span.class.as_deref())
        .collect();
    assert_eq!(
        waiters,
        vec!["com.intellij.grazie.spellcheck.engine.GrazieSpellCheckerEngine$SpellerLoadActivity"]
    );
    assert!(
        timeline
            .spans
            .iter()
            .any(|span| span.helper && span.name == "run activity: scheduled")
    );
    assert!(timeline.spans.iter().all(|span| !span.is_waiter() || span.duration_ms > WAITER_MS));
}

#[test]
fn a_window_keeps_the_spans_that_start_in_it() {
    let (_dir, session) = fixture_session();
    let timeline = Timeline::read(&session.join("non-modal-run-01")).expect("a timeline");
    let names: Vec<&str> = timeline.window(1500.0, 1615.5).map(|span| span.name.as_str()).collect();
    assert_eq!(
        names,
        vec!["run activity", "ProjectManager.openAsync", "project frame creating"],
        "the end of the window is open"
    );
    assert_eq!(timeline.window(0.0, 0.0).count(), 0);
    assert_eq!(
        timeline.window(timeline.start_ms(), timeline.end_ms()).count(),
        timeline.spans.len()
    );
    assert_eq!(timeline.start_ms().to_string(), "0");
    assert!(timeline.end_ms() > 7000.0, "application.exit ends last: {}", timeline.end_ms());
}

#[test]
fn a_modal_run_has_only_the_welcome_and_the_quit_anchors_and_a_run_without_a_trace_is_an_error() {
    let (_dir, session) = fixture_session();
    let timeline = Timeline::read(&session.join("modal-run-01")).expect("a timeline");
    let names: Vec<&str> = timeline.anchors.iter().map(|anchor| anchor.name).collect();
    assert_eq!(names, vec!["welcomeBecameVisible", "application.exit"]);
    let last = timeline.spans.last().expect("a span");
    assert_eq!(
        (last.name.as_str(), last.duration_ms.to_string()),
        ("rdct.station.discovery: completing", "0".to_owned())
    );
    assert_eq!(
        timeline.window(timeline.start_ms(), timeline.end_ms()).count(),
        timeline.spans.len(),
        "the end of the run is past a span of zero length"
    );
    std::fs::remove_file(session.join("modal-run-01/opentelemetry.json")).expect("a removal");
    let error = Timeline::read(&session.join("modal-run-01")).expect_err("no trace");
    assert!(format!("{error:#}").starts_with("cannot read "), "{error:#}");
}

#[test]
fn a_truncated_trace_gives_a_timeline_that_says_so() {
    let (_dir, session) = fixture_session();
    let run = session.join("non-modal-run-01");
    let text = std::fs::read_to_string(run.join("opentelemetry.json")).expect("a trace");
    let cut = text.find(r#""operationName":"toolwindow creating""#).expect("a span");
    std::fs::write(run.join("opentelemetry.json"), &text[..cut]).expect("a truncated trace");
    let timeline = Timeline::read(&run).expect("a timeline");
    assert!(timeline.truncated);
    assert!(!timeline.spans.iter().any(|span| span.name == "toolwindow creating"));
    assert_eq!(timeline.origin_us, 1_790_860_689_816_000);
}

#[test]
fn an_activity_is_a_run_activity_or_a_run_init_activity_span_without_its_twins() {
    let (_dir, session) = fixture_session();
    let timeline = Timeline::read(&session.join("non-modal-run-01")).expect("a timeline");
    let activities: Vec<&str> = timeline
        .spans
        .iter()
        .filter(|span| span.is_activity())
        .map(|span| span.name.as_str())
        .collect();
    assert_eq!(
        activities,
        vec![
            "run activity",
            "run init activity WelcomeScreenInitActivity",
            "run init activity PsiVfsInitProjectActivity",
            "run activity",
            "run activity"
        ]
    );
    let whole = timeline.whole();
    assert_eq!((whole.from_ms, whole.to_ms), (timeline.start_ms(), timeline.end_ms()));
}

#[test]
fn the_quit_cuts_a_span_that_starts_before_it_and_ends_at_its_start_or_later() {
    let span = |start_ms: f64, duration_ms: f64| super::TimedSpan {
        start_ms,
        duration_ms,
        name: "run activity".to_owned(),
        class: None,
        plugin: None,
        helper: false,
    };
    let quit = Some(5113.4);
    assert!(span(1631.0, 3489.5).is_cut_by(quit), "it ends after the start of the quit");
    assert!(span(1631.0, 3482.4).is_cut_by(quit), "it ends at the start of the quit");
    assert!(!span(1631.0, 3482.0).is_cut_by(quit), "it ends before the quit");
    assert!(!span(5113.4, 287.9).is_cut_by(quit), "the quit does not cut itself");
    assert!(!span(1631.0, 3489.5).is_cut_by(None), "a run without a quit cuts nothing");
}

#[test]
fn a_project_run_has_the_highlighted_editor_as_an_anchor() {
    let (_dir, session) = fixture_session();
    let timeline = Timeline::read(&session.join("project-run-01")).expect("a timeline");
    assert_eq!(
        timeline.anchors,
        vec![
            Anchor {
                name: "editor highlighting completed",
                ms: 2950.0
            },
            Anchor {
                name: "application.exit",
                ms: 7553.7
            },
        ]
    );
}
