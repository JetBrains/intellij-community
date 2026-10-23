use std::sync::{Arc, Mutex};

use avl_wire::daemon::decode_run_event;
use avl_wire::progress::{BuildSummary, LeaseDisposition, Record, RunStarted, TraceRef, VerdictCounts};
use avl_wire::report::Status;
use pretty_assertions::assert_eq;

use super::*;
use crate::clock::FakeClock;
use crate::refusal::{Exit, Refusal};
use crate::report::{Buffer, Mode, Outcome, Reporter, main};

/// A reporter in human mode, with the terminal the test chooses and a clock it moves: `(reporter, stderr, clock)`.
fn screen_recorder(terminal: Terminal) -> (Reporter, Buffer, Arc<FakeClock>) {
    let clock = Arc::new(FakeClock::at("2026-09-24T12:00:00Z"));
    let (reporter, _, stderr) = Reporter::in_memory("vm");
    let reporter = reporter.with_clock(clock.clone());
    reporter.set_mode(Mode::Human(terminal));
    (reporter, stderr, clock)
}

fn daemon_event(raw: &str) -> Event {
    Event::Daemon(decode_run_event(raw.as_bytes()).expect("a valid daemon record"))
}

fn last_line(text: &str) -> &str {
    text.rsplit('\n').next().unwrap_or("")
}

// Plain lines, for a log file: a phase says when it starts and ends, and a test says when it starts and how it
// ended. Nothing is redrawn, so no control byte reaches the file.
#[test]
fn plain_lines_name_each_phase_and_each_test() {
    let (reporter, stderr, clock) = screen_recorder(Terminal::default());
    let scope = Scope::worker("air-linux-1");
    let phase = reporter.start_phase(Phase::Iteration, "flow-new-session", Some(&scope));
    reporter.publish(
        daemon_event(
            r#"{"event":"testStarted","timestamp":"t","id":"1","displayName":"start-in-terminal","className":"AirNewSessionTest","methodName":"scenarios"}"#,
        ),
        Some(&scope),
    );
    reporter.publish(
        daemon_event(
            r#"{"event":"testFinished","timestamp":"t","id":"1","displayName":"start-in-terminal","className":"AirNewSessionTest","methodName":"scenarios","status":"FAILED","durationMs":5,"message":"no chip","detail":"at line 3"}"#,
        ),
        Some(&scope),
    );
    clock.advance(Duration::from_secs(65));
    phase.succeed();

    assert_eq!(
        stderr.text(),
        [
            "vm: [air-linux-1] running the tests: flow-new-session",
            "vm: [air-linux-1] ▶ AirNewSessionTest#scenarios[start-in-terminal]",
            "vm: [air-linux-1] ✗ AirNewSessionTest#scenarios[start-in-terminal] — no chip",
            "vm: [air-linux-1] running the tests: done in 1m05s",
            "",
        ]
        .join("\n")
    );
}

// Without a footer, a long phase is a heartbeat at most every 15 s, so a log shows that the build still moves
// without a line for every Bazel progress update.
#[test]
fn plain_progress_is_a_heartbeat_not_a_flood() {
    let (reporter, stderr, clock) = screen_recorder(Terminal::default());
    let phase = reporter.start_phase(Phase::HostBuild, "//a:b", None);
    for step in 1..=10 {
        clock.advance(Duration::from_secs(2));
        phase.progress(&format!("[{step} / 10] Compiling"));
    }
    phase.succeed();
    let text = stderr.text();
    // 20 s of progress, one line per 15 s.
    let stills = text
        .lines()
        .filter(|line| line.starts_with("vm: still building on the host"))
        .count();
    assert_eq!(stills, 1, "{text}");
    assert!(
        text.contains("still building on the host (16s): [8 / 10] Compiling"),
        "the heartbeat does not name the elapsed time and the detail:\n{text}"
    );
}

// A failed phase names its error's first line and its elapsed time.
#[test]
fn a_failed_phase_says_why_and_when() {
    let (reporter, stderr, clock) = screen_recorder(Terminal::default());
    let phase = reporter.start_phase(Phase::Lease, "", None);
    clock.advance(Duration::from_secs(3));
    phase.fail("no free worker\nall 2 are leased");
    assert!(
        stderr.text().ends_with("vm: leasing a worker: failed after 3s: no free worker\n"),
        "{:?}",
        stderr.text()
    );
}

// The footer: one line under the scrollback, erased before each new line and drawn again after it. It names the
// phase, its time, the counts and the test that runs, and it is gone before the command's answer.
#[test]
fn the_footer_stays_under_the_lines_and_leaves_before_the_answer() {
    let (reporter, stderr, clock) = screen_recorder(Terminal {
        redraw: true,
        color: false,
        width: 200,
    });
    let scope = Scope::worker("air-linux-1");
    let phase = reporter.start_phase(Phase::Iteration, "", Some(&scope));
    clock.advance(Duration::from_secs(5));
    reporter.publish(
        daemon_event(r#"{"event":"testStarted","timestamp":"t","id":"1","displayName":"a","className":"T","methodName":"m"}"#),
        Some(&scope),
    );
    let written = stderr.text();
    assert!(
        !written.contains("▶ T#m[a]\n"),
        "a started test printed a line under a footer that already names it: {written:?}"
    );
    assert!(
        written.ends_with("[air-linux-1] running the tests 5s · ▶ T#m[a]"),
        "the footer reads {:?}",
        last_line(&written)
    );
    reporter.publish(
        daemon_event(
            r#"{"event":"testFinished","timestamp":"t","id":"1","displayName":"a","className":"T","methodName":"m","status":"SUCCESSFUL","durationMs":1}"#,
        ),
        Some(&scope),
    );
    let written = stderr.text();
    assert!(
        written.contains("\r\x1b[2Kvm: [air-linux-1] ✓ T#m[a]\n"),
        "the footer was not erased before the line: {written:?}"
    );
    assert!(
        written.ends_with("running the tests 5s · ✓ 1 ✗ 0 ↷ 0"),
        "the footer after a pass reads {:?}",
        last_line(&written)
    );

    main("run", &reporter, || Outcome::new((), "1 test(s) passed"));
    assert!(
        stderr.text().ends_with("\r\x1b[2K"),
        "the footer was left on the terminal: {:?}",
        stderr.text()
    );
    phase.succeed();
}

// A footer wider than the terminal wraps, and a wrapped footer cannot be erased by one carriage return.
#[test]
fn the_footer_is_cut_to_the_terminal_width() {
    let (reporter, stderr, _) = screen_recorder(Terminal {
        redraw: true,
        color: false,
        width: 30,
    });
    let phase = reporter.start_phase(Phase::HostBuild, &"//very/long:target ".repeat(10), None);
    let written = stderr.text();
    let footer = last_line(&written);
    assert_eq!(footer.chars().count(), 29, "{footer:?}");
    assert!(footer.ends_with('…'), "{footer:?}");
    phase.succeed();
}

// Every sink receives every record, in every mode, and before any renderer writes: the journal must not depend
// on whether anybody watches.
#[test]
fn a_sink_receives_every_record_in_every_mode() {
    for mode in [Mode::Json, Mode::Stream, Mode::Human(Terminal::default())] {
        let (reporter, _, _) = Reporter::in_memory("vm");
        reporter.set_mode(mode);
        let got: Arc<Mutex<Vec<Record>>> = Arc::default();
        let sink = got.clone();
        let guard = reporter.add_sink(move |record: &Record| lock(&sink).push(record.clone()));
        reporter.note("a", Some(&Scope::worker("air-linux-1")));
        reporter.start_phase(Phase::Traces, "", None).succeed();
        drop(guard);
        reporter.note("after the sink left", None);
        let got = lock(&got);
        let kinds: Vec<_> = got.iter().map(|record| record.event).collect();
        assert_eq!(
            kinds,
            [
                avl_wire::progress::Kind::Note,
                avl_wire::progress::Kind::Phase,
                avl_wire::progress::Kind::Phase
            ],
            "mode {mode:?}"
        );
        assert_eq!(got[0].worker.as_deref(), Some("air-linux-1"), "mode {mode:?}");
    }
}

fn trace_ref() -> TraceRef {
    TraceRef {
        bundle_id: "b1".to_owned(),
        test_class: "OneTest".to_owned(),
        scenario: "renames".to_owned(),
        status: "failed".to_owned(),
        has_video: false,
    }
}

/// A red run with every part the plain answer prints.
fn red_verdict() -> Verdict {
    Verdict {
        code: Some("tests_failed".to_owned()),
        counts: VerdictCounts {
            started: 2,
            failed: 1,
            ..VerdictCounts::default()
        },
        failures: vec![Failure {
            class: "a.OneTest".to_owned(),
            name: Some("renames".to_owned()),
            message: Some("boom".to_owned()),
            trace: Some(trace_ref()),
        }],
        rerun: vec!["run a.OneTest".to_owned()],
        lease: Some(VerdictLease {
            workers: vec!["air-linux-1".to_owned()],
            disposition: LeaseDisposition::ReleaseFailed,
            receipts: vec!["/r/1.json".to_owned()],
        }),
        checkout: Some(Checkout {
            head: Some("abc".to_owned()),
            uncommitted: 2,
            error: None,
        }),
        traces: Some(VerdictTraces {
            scenarios: 2,
            not_passed: vec![trace_ref()],
            with_video: 1,
            directory: Some("/runs/r/traces/it-1".to_owned()),
            error: None,
        }),
        timing: Some("build 1.0s tests 9.0s".to_owned()),
        report: Some("/reports/it-1.json".to_owned()),
        ..Verdict::new(
            Status::Failed,
            "1 test(s) and 0 container(s) failed of 2 started (iteration it-1); first: a.OneTest — boom",
        )
    }
}

// The verdict is the answer of a run: the plain renderer writes it to stdout when the command ends, and the
// refusal with the verdict's code prints no second line. The block names only the traces that did not pass,
// because every trace already arrived as a line.
#[test]
fn the_verdict_is_the_plain_answer_and_the_refusal_adds_no_line() {
    let (reporter, stdout, stderr) = Reporter::in_memory("vm");
    reporter.set_mode(Mode::Human(Terminal::default()));
    reporter.publish(Event::Verdict(Box::new(red_verdict())), None);
    let exit = main("run", &reporter, || {
        Err(Refusal::new("tests_failed", Exit::TESTS_FAILED, red_verdict().summary))
    });
    assert_eq!(exit, Exit::TESTS_FAILED);
    assert_eq!(
        stdout.text(),
        [
            "1 test(s) and 0 container(s) failed of 2 started (iteration it-1); first: a.OneTest — boom",
            "✗ a.OneTest › renames — boom",
            "  trace http://127.0.0.1:7357/air/runs/trace?src=b1&scenario=renames",
            "report /reports/it-1.json",
            "build 1.0s tests 9.0s",
            "reproduce: run a.OneTest",
            "lease air-linux-1 release_failed (release it with `vm --lease-file /r/1.json lease release`)",
            "tree: dirty (2 files)",
            "traces: 2 scenario(s), 1 not passed, 1 with video, in /runs/r/traces/it-1",
            "  ✗ OneTest · renames  http://127.0.0.1:7357/air/runs/trace?src=b1&scenario=renames",
            "",
        ]
        .join("\n")
    );
    assert!(stderr.is_empty(), "the refusal of the verdict printed a second line: {stderr:?}");
}

// A refusal with another code than the verdict's is news the verdict does not hold, so it still prints. A green
// verdict replaces the text answer.
#[test]
fn a_refusal_after_the_verdict_still_prints_and_a_green_verdict_replaces_the_text() {
    let (reporter, stdout, stderr) = Reporter::in_memory("vm");
    reporter.set_mode(Mode::Human(Terminal::default()));
    reporter.publish(Event::Verdict(Box::new(red_verdict())), None);
    main("run", &reporter, || {
        Err(Refusal::new(
            "lock_release_failed",
            Exit::FAILURE,
            "cannot release the operation lock",
        ))
    });
    assert_eq!(stderr.text(), "vm: cannot release the operation lock\n");

    stdout.clear();
    reporter.set_mode(Mode::Human(Terminal::default()));
    let green = Verdict::new(Status::Passed, "2 test(s) passed");
    reporter.publish(Event::Verdict(Box::new(green)), None);
    main("run", &reporter, || Outcome::new((), "the old text answer"));
    assert_eq!(stdout.text(), "2 test(s) passed\n");
}

// A host build names what Bazel did, so a slow build says whether it rebuilt much.
// A slow step is one line that names the open phases, the time, and the dump files to open.
#[test]
fn a_slow_step_is_a_line_with_its_dumps() {
    let (reporter, stderr, _) = screen_recorder(Terminal::default());
    reporter.publish(
        daemon_event(
            r#"{"event":"slowStep","timestamp":"t","step":"restart","path":"restart > quit > process exit","budgetMs":30000,"elapsedMs":125000,"threadDump":"/it/slow-steps/01-restart-daemon-threads.txt","ideDumpError":"refused"}"#,
        ),
        Some(&Scope::worker("air-docker-1")),
    );
    assert_eq!(
        stderr.text(),
        "vm: [air-docker-1] slow step: restart > quit > process exit took 2m05s of a 30s budget; dumps: /it/slow-steps/01-restart-daemon-threads.txt\n"
    );
}

#[test]
fn the_build_summary_is_a_line() {
    let (reporter, stderr, _) = screen_recorder(Terminal::default());
    reporter.publish(
        Event::BuildSummary(BuildSummary {
            processes: 1025,
            ran: 970,
            cached: 6684,
            critical_path_ms: 187_670,
        }),
        None,
    );
    assert_eq!(
        stderr.text(),
        "vm: host build: 970 actions ran, 6684 cached, critical path 187.7s\n"
    );
}

fn trace_ready(scenario: &str, status: &str, has_video: bool) -> TraceReady {
    TraceReady {
        iteration_id: "it-1".to_owned(),
        bundle_id: "ab12".to_owned(),
        test_class: "AirTest".to_owned(),
        scenario: scenario.to_owned(),
        flow: None,
        status: status.to_owned(),
        has_video,
        zip: "/r/001.zip".to_owned(),
        entry: "001".to_owned(),
    }
}

// With a viewer on this machine, the run's first line is its live page, and each trace is its link, so a person
// clicks rather than unzips. Without one, a trace is named by its zip.
#[test]
fn a_run_and_its_traces_are_named_by_their_viewer_links() {
    let (reporter, stderr, _) = screen_recorder(Terminal::default());
    reporter.set_viewer(7357, "the viewer is starting");
    reporter.publish(
        Event::RunStarted(RunStarted {
            run_id: "me-run-1".to_owned(),
            command: "run".to_owned(),
            args: Vec::new(),
            checkout: None,
            process_started_at: None,
        }),
        None,
    );
    reporter.publish(Event::TraceReady(trace_ready("renames session", "failed", true)), None);
    assert_eq!(
        stderr.text(),
        "vm: run me-run-1: http://127.0.0.1:7357/air/runs/run?id=me-run-1 (the viewer is starting)\n\
         vm: trace ✗ AirTest · renames session (failed, video): \
         http://127.0.0.1:7357/air/runs/trace?src=ab12&scenario=renames+session\n"
    );

    let (plain, plain_stderr, _) = screen_recorder(Terminal::default());
    plain.publish(Event::TraceReady(trace_ready("s", "passed", false)), None);
    assert!(
        plain_stderr.text().ends_with("(passed): /r/001.zip\n"),
        "without a viewer the trace reads {:?}",
        plain_stderr.text()
    );
}

#[test]
fn a_checkout_line_says_whether_the_tree_is_clean() {
    let checkout = |head: &str, uncommitted| Checkout {
        head: Some(head.to_owned()),
        uncommitted,
        error: None,
    };
    assert_eq!(checkout_line(&checkout("0123456789abcdef", 0)), "checkout 0123456789ab: clean");
    assert_eq!(checkout_line(&checkout("abc", 1)), "checkout abc: 1 uncommitted file is under test");
    assert_eq!(
        checkout_line(&checkout("abc", 3)),
        "checkout abc: 3 uncommitted files are under test"
    );
    let unknown = Checkout {
        head: None,
        uncommitted: 0,
        error: Some("not a repository\nmore".to_owned()),
    };
    assert_eq!(checkout_line(&unknown), "checkout: unknown: not a repository");
}

// A failed test line names the headline of its typed failure: the first line of the message, else the type. A failed
// container shows the headline and the detail on the lines below it.
#[test]
fn a_failed_test_line_names_the_headline_of_its_typed_failure() {
    let (reporter, stderr, _clock) = screen_recorder(Terminal::default());
    let scope = Scope::worker("air-linux-1");
    for record in [
        r#"{"event":"testFinished","timestamp":"t","id":"1","displayName":"a","className":"T","methodName":"m","status":"FAILED","durationMs":5,"type":"java.lang.AssertionError","message":"no chip\nmore","detail":"at line 3"}"#,
        r#"{"event":"testFinished","timestamp":"t","id":"2","displayName":"b","className":"T","methodName":"m","status":"FAILED","durationMs":5,"type":"java.lang.IllegalStateException"}"#,
        r#"{"event":"containerFailed","timestamp":"t","id":"[suite:s]","displayName":"S","className":"S","message":"the prologue failed","detail":"at prologue"}"#,
    ] {
        reporter.publish(daemon_event(record), Some(&scope));
    }
    assert_eq!(
        stderr.text(),
        [
            "vm: [air-linux-1] ✗ T#m[a] — no chip",
            "vm: [air-linux-1] ✗ T#m[b] — java.lang.IllegalStateException",
            "vm: [air-linux-1] ✗ S (container)",
            "the prologue failed",
            "at prologue",
            "",
        ]
        .join("\n")
    );
}
