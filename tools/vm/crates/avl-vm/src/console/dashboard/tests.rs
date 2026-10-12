use std::sync::Arc;

use avl_base::FakeClock;
use avl_base::report::{Buffer, Renderer};
use avl_wire::progress::{Event, RunFinished, RunStarted, Verdict, VerdictCounts};
use avl_wire::report::Status;
use pretty_assertions::assert_eq;

use super::{Canvas, Dashboard, Options};
use crate::console::paint::ColorChoice;

fn passed() -> Verdict {
    Verdict {
        status: Status::Passed,
        code: None,
        summary: "0 test(s) passed".to_owned(),
        counts: VerdictCounts::default(),
        failures: Vec::new(),
        rerun: Vec::new(),
        skipped: Vec::new(),
        unreported: Vec::new(),
        lanes: Vec::new(),
        shards: None,
        lease: None,
        checkout: None,
        traces: None,
        timing: None,
        report: None,
    }
}

/// Through the real drawing thread, the scrollback lines keep their order, the card is the last thing drawn, and
/// the cursor is never hidden.
#[test]
fn the_dashboard_keeps_the_order_and_ends_with_the_card() {
    let out = Buffer::new();
    let mut dashboard = Dashboard::start(
        Box::new(out.clone()),
        Options {
            rerun_prefix: "vm".to_owned(),
            width: 100,
            color: ColorChoice::Always,
            colors: None,
            clock: Some(Arc::new(FakeClock::at("2026-09-24T12:00:00Z"))),
            follow_stderr_size: false,
            ..Options::default()
        },
    );
    dashboard.render(
        &Event::RunStarted(RunStarted {
            run_id: "me-run-1".to_owned(),
            command: "run".to_owned(),
            args: Vec::new(),
            checkout: None,
        }),
        None,
    );
    let notes: Vec<String> = ('a'..='t').map(|letter| format!("note {letter}")).collect();
    for note in &notes {
        dashboard.render(&Event::Note(note.clone()), None);
    }
    dashboard.render(&Event::Verdict(Box::new(passed())), None);
    dashboard.render(
        &Event::RunFinished(RunFinished {
            exit_code: 0,
            code: None,
            message: None,
        }),
        None,
    );
    let verdict = dashboard.finish();
    assert_eq!(
        verdict.map(|verdict| verdict.summary),
        Some("0 test(s) passed".to_owned()),
        "the dashboard does not answer the verdict it rendered"
    );

    let raw = out.text();
    let text = strip_ansi_escapes::strip_str(&raw);
    let mut last = 0;
    for note in &notes {
        let at = text.find(note.as_str()).unwrap_or_else(|| panic!("{note} is missing from\n{text}"));
        assert!(at >= last, "{note} is out of order in\n{text}");
        last = at;
    }
    let card = text.rfind("PASSED").expect("the card is drawn");
    assert!(card > last, "the card is not after the notes:\n{text}");
    assert!(!text[card..].contains("note "), "a line follows the card:\n{text}");
    assert!(!raw.contains("\x1b[?25l"), "the cursor was hidden: {raw:?}");

    // A render after the finish goes nowhere and does not block.
    let written = out.bytes().len();
    dashboard.render(&Event::Note("late".to_owned()), None);
    dashboard.set_viewer(7357, "");
    drop(dashboard);
    assert_eq!(out.bytes().len(), written, "the dashboard wrote after its finish");
}

/// Each frame erases exactly the rows the last frame drew, whatever its height, and prints the scrollback above
/// the new frame. The last frame stays.
#[test]
fn a_frame_replaces_the_live_region_of_the_last() {
    let out = Buffer::new();
    let mut canvas = Canvas {
        out: Box::new(out.clone()),
        rows: 0,
    };
    let lines = |texts: &[&str]| texts.iter().map(|text| (*text).to_owned()).collect::<Vec<_>>();

    canvas.paint(&lines(&["first"]), &lines(&["a", "b", "c"]), false);
    assert_eq!(out.text(), "first\na\nb\nc\n");
    out.clear();

    canvas.paint(&[], &lines(&["a", "b", "c", "d", "e"]), false);
    assert_eq!(out.text(), "\r\x1b[3A\x1b[Ja\nb\nc\nd\ne\n");
    out.clear();

    canvas.paint(&lines(&["second"]), &lines(&["", "card"]), true);
    assert_eq!(out.text(), "\r\x1b[5A\x1b[Jsecond\n\ncard\n");
    out.clear();

    // The card is never erased.
    canvas.paint(&[], &[], false);
    assert_eq!(out.text(), "");
}

/// A dashboard dropped without a finish, as a panic that unwinds through a command drops it, still prints the
/// lines it held and leaves no live region to be drawn over.
#[test]
fn a_dropped_dashboard_leaves_its_lines() {
    let out = Buffer::new();
    let mut dashboard = Dashboard::start(
        Box::new(out.clone()),
        Options {
            width: 80,
            color: ColorChoice::Never,
            ..Options::default()
        },
    );
    dashboard.render(&Event::Note("before the panic".to_owned()), None);
    drop(dashboard);
    assert!(out.text().contains("before the panic"), "{:?}", out.text());
}
