use std::collections::BTreeMap;
use std::time::{Duration, SystemTime};

use avl_base::Scope;
use avl_wire::daemon::decode_run_event;
use avl_wire::progress::{
    BuildSummary, Checkout, Decision, Event, Expected, Failure, IterationReported, LaneVerdict, LeaseDisposition, Phase, PhaseChange,
    PhaseState, PhaseTypical, RunFinished, RunStarted, Subject, TraceReady, TraceRef, Typical, Verdict, VerdictCounts, VerdictLease,
};
use avl_wire::report::Status;
use expect_test::expect;
use pretty_assertions::assert_eq;

use super::State;
use crate::console::paint::{ColorChoice, Paint};

const COLOR: Paint = Paint::fallback(true, true);

fn strip(text: &str) -> String {
    strip_ansi_escapes::strip_str(text)
}

/// Rows with a left margin, so a snapshot keeps their indentation.
fn margined(text: &str) -> String {
    text.lines().map(|line| format!("|{line}\n")).collect()
}

/// Drives a state the way the reporter does, with a clock the test moves.
struct Run {
    state: State,
    now: SystemTime,
    scrollback: Vec<String>,
}

impl Run {
    fn new() -> Self {
        Self {
            state: State::new("./community/tools/vm.cmd", COLOR),
            // 2026-09-24T12:00:00Z
            now: SystemTime::UNIX_EPOCH + Duration::from_hours(497_292),
            scrollback: Vec::new(),
        }
    }

    fn publish(&mut self, event: &Event, worker: &str) {
        let scope = (!worker.is_empty()).then(|| Scope::worker(worker));
        for line in self.state.apply(event, scope.as_ref(), self.now) {
            self.scrollback.push(strip(&line));
        }
    }

    /// Publishes one record of the daemon's run stream, decoded the way the controller decodes it.
    fn daemon(&mut self, raw: &str, worker: &str) {
        let event = decode_run_event(raw.as_bytes()).unwrap_or_else(|error| panic!("the fixture {raw} does not decode: {error}"));
        self.publish(&Event::Daemon(event), worker);
    }

    fn advance(&mut self, seconds: u64) {
        self.now += Duration::from_secs(seconds);
    }

    fn live(&self, width: usize) -> String {
        strip(&self.state.live_view(self.now, width).join("\n"))
    }

    fn card(&self, width: usize) -> String {
        strip(&self.state.card(width).join("\n"))
    }

    fn has_line(&self, fragment: &str) -> bool {
        self.scrollback.iter().any(|line| line.contains(fragment))
    }

    fn scrollback(&self) -> String {
        self.scrollback.join("\n")
    }

    fn finish_test(&mut self, scenario: &str, worker: &str) {
        self.daemon(
            &format!(
                r#"{{"event":"testFinished","timestamp":"t","id":"{scenario}","className":"com.example.AirDraftFlowUiTest","displayName":"{scenario}","status":"SUCCESSFUL","durationMs":3000}}"#
            ),
            worker,
        );
    }
}

fn phase(state: PhaseState, which: Phase, detail: &str, elapsed_seconds: u64) -> Event {
    Event::Phase(PhaseChange {
        phase: which,
        state,
        detail: (!detail.is_empty()).then(|| detail.to_owned()),
        elapsed_ms: elapsed_seconds * 1000,
        error: None,
    })
}

fn run_started(args: &[&str]) -> Event {
    Event::RunStarted(RunStarted {
        run_id: "me-run-1".to_owned(),
        command: "run".to_owned(),
        args: args.iter().map(|arg| (*arg).to_owned()).collect(),
        checkout: None,
    })
}

fn typical(seconds: u64) -> Typical {
    Typical {
        ms: seconds * 1000,
        samples: 3,
    }
}

/// What earlier runs of the lane measured.
fn expected_lane() -> Event {
    Event::Expected(Expected {
        run: Some(typical(300)),
        phases: vec![
            PhaseTypical {
                phase: Phase::HostBuild,
                key: None,
                typical: typical(40),
            },
            PhaseTypical {
                phase: Phase::Iteration,
                key: Some("flow flow-new-session".to_owned()),
                typical: typical(120),
            },
        ],
        classes: BTreeMap::from([
            ("com.example.AirNewSessionFlowUiTest".to_owned(), typical(30)),
            ("com.example.AirDraftFlowUiTest".to_owned(), typical(20)),
        ]),
    })
}

fn decision(subject: Subject, action: &str, reason: Option<&str>) -> Event {
    Event::Decision(Decision {
        subject,
        action: action.to_owned(),
        reason: reason.map(str::to_owned),
    })
}

fn note(message: &str) -> Event {
    Event::Note(message.to_owned())
}

fn iteration_reported(report_path: &str) -> Event {
    Event::IterationReported(IterationReported {
        iteration_id: "i".to_owned(),
        daemon_run_id: "d".to_owned(),
        selection: "flow flow-draft".to_owned(),
        status: Status::Passed,
        report_path: report_path.to_owned(),
        tests_failed: 0,
        tests_started: 1,
    })
}

/// The trace of one scenario of AirDraftFlowUiTest, as the controller publishes it.
fn trace_ready(scenario: &str, bundle: &str) -> Event {
    Event::TraceReady(TraceReady {
        iteration_id: "i".to_owned(),
        bundle_id: bundle.to_owned(),
        test_class: "AirDraftFlowUiTest".to_owned(),
        scenario: scenario.to_owned(),
        flow: None,
        status: "passed".to_owned(),
        has_video: true,
        zip: "/r/001.zip".to_owned(),
        entry: "001".to_owned(),
    })
}

fn verdict(status: Status, code: Option<&str>, summary: &str) -> Verdict {
    Verdict {
        status,
        code: code.map(str::to_owned),
        summary: summary.to_owned(),
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

fn run_finished(exit_code: i32, code: Option<&str>) -> Event {
    Event::RunFinished(RunFinished {
        exit_code,
        code: code.map(str::to_owned),
        message: None,
    })
}

/// The whole plan is visible before it runs, and each phase says its time against its usual time when it ends.
#[test]
fn the_checklist_shows_the_plan_and_the_usual_times() {
    let mut run = Run::new();
    run.publish(&run_started(&["flow-new-session"]), "");
    run.publish(&expected_lane(), "");
    let live = run.live(120);
    for fragment in ["○ host build", "○ lease", "○ daemon start", "○ tests", "○ traces", "○ release"] {
        assert!(live.contains(fragment), "the plan has no {fragment:?}:\n{live}");
    }

    run.publish(&phase(PhaseState::Started, Phase::HostBuild, "//a:b", 0), "");
    run.advance(12);
    run.publish(&phase(PhaseState::Progress, Phase::HostBuild, "[12 / 90] Compiling Kotlin", 0), "");
    let live = run.live(120);
    expect![[r"
        |
        |  ⠦ host build         12s  usual 40s    [12 / 90] Compiling Kotlin
        |  ○ lease
        |  ○ daemon start
        |  ○ tests
        |  ○ traces
        |  ○ release
        |  elapsed 12s · usually 5m00s
    "]]
    .assert_eq(&margined(&live));

    run.advance(60);
    run.publish(&phase(PhaseState::Finished, Phase::HostBuild, "", 72), "");
    assert!(
        run.has_line("✓ host build 1m12s (usual 40s) ▲"),
        "a slow build is not marked:\n{}",
        run.scrollback()
    );
    // A narrow terminal drops the usual times, and no line is as wide as the terminal.
    for line in run.state.live_view(run.now, 60) {
        let width = console::measure_text_width(&line);
        assert!(width < 60, "a line is {width} columns wide on a 60-column terminal: {line:?}");
    }
    assert!(!run.live(60).contains("usual 40s"), "{}", run.live(60));
}

/// A decision says why, and a reused IDE skips the daemon start with its reason.
#[test]
fn a_decision_explains_its_row() {
    let mut run = Run::new();
    run.publish(
        &decision(Subject::Daemon, "restart", Some("the stable runtime or JBR changed")),
        "air-linux-1",
    );
    assert!(
        run.has_line("⟳ daemon restart — the stable runtime or JBR changed"),
        "the scrollback is {:?}",
        run.scrollback
    );
    let live = run.live(120);
    assert!(
        live.contains("restart — the stable runtime or JBR changed"),
        "the daemon row does not say why:\n{live}"
    );

    let mut reused = Run::new();
    reused.publish(
        &decision(Subject::Ide, "reuse", Some("the daemon inputs did not change")),
        "air-linux-1",
    );
    let live = reused.live(120);
    assert!(
        live.contains("– daemon start   skipped · the warm daemon is reused"),
        "a reused IDE does not skip the daemon start:\n{live}"
    );
}

/// The progress bar counts the discovered tests, the running test names its class time, and the time left is the
/// typical time of every class not done yet.
#[test]
fn the_tests_line_counts_and_estimates() {
    let mut run = Run::new();
    run.publish(&expected_lane(), "");
    run.publish(&phase(PhaseState::Started, Phase::Iteration, "flow flow-new-session", 0), "w");
    run.daemon(
        r#"{"event":"runStarted","iterationId":"i","daemonBootStamp":"b","startedAt":"s"}"#,
        "w",
    );
    run.daemon(
        r#"{"event":"planStarted","timestamp":"t","discoveredExecutions":4,"classNames":["com.example.AirNewSessionFlowUiTest","com.example.AirDraftFlowUiTest"]}"#,
        "w",
    );
    run.advance(2);
    run.daemon(
        r#"{"event":"testStarted","timestamp":"t","id":"1","className":"com.example.AirNewSessionFlowUiTest","displayName":"opens the chooser"}"#,
        "w",
    );
    run.advance(8);
    let live = run.live(120);
    // 20 s of the first class and 20 s of the second are left.
    expect![[r"
        |
        |  – host build     skipped
        |  – lease          skipped
        |  – daemon start   skipped
        |  ⠋ tests              10s  usual 2m00s  flow flow-new-session
        |  ○ traces
        |  ○ release
        |
        |  tests ──────────────────────────────────────── 0/4   ✓ 0  ✗ 0  ↷ 0   ~40s left
        |  ▶ AirNewSessionFlowUiTest › opens the chooser  8s  class 10s of ~30s
    "]]
    .assert_eq(&margined(&live));

    run.daemon(
        r#"{"event":"testFinished","timestamp":"t","id":"1","className":"com.example.AirNewSessionFlowUiTest","displayName":"opens the chooser","status":"SUCCESSFUL","durationMs":8100}"#,
        "w",
    );
    run.daemon(
        r#"{"event":"testFinished","timestamp":"t","id":"2","className":"com.example.AirNewSessionFlowUiTest","displayName":"restores the draft","status":"FAILED","durationMs":31000,"error":"expected \"hi\" but was \"\"\n\tat line 3"}"#,
        "w",
    );
    // The first test stopped waiting for its trace when the second finished. The second still waits, and the live
    // region shows it.
    assert!(
        run.has_line("  AirNewSessionFlowUiTest") && run.has_line("    ✓ opens the chooser 8s") && !run.has_line("restores the draft"),
        "the scrollback is\n{}",
        run.scrollback()
    );
    let live = run.live(120);
    assert!(
        live.contains("2/4") && live.contains("✓ 1  ✗ 1  ↷ 0") && live.contains("✗ restores the draft 31s"),
        "the counts and the waiting test read\n{live}"
    );
    run.publish(&iteration_reported("/r/it.json"), "w");
    assert!(
        run.has_line("    ✗ restores the draft 31s") && run.has_line(r#"expected "hi" but was """#),
        "the report does not release the waiting test:\n{}",
        run.scrollback()
    );
}

/// The card is the verdict: a banner, the phase times, why, what failed with its trace, and the rerun command.
#[test]
fn the_card_is_the_verdict() {
    let mut run = Run::new();
    run.state.viewer_port = 6173;
    run.publish(&run_started(&["flow-new-session"]), "");
    run.publish(&expected_lane(), "");
    let checkout = Checkout {
        head: Some("74d40e8bead8e0000".to_owned()),
        uncommitted: 15,
        error: None,
    };
    run.publish(&Event::Checkout(checkout.clone()), "");
    run.publish(&phase(PhaseState::Started, Phase::HostBuild, "//a:b", 0), "");
    run.publish(&phase(PhaseState::Finished, Phase::HostBuild, "", 30), "");
    run.publish(&decision(Subject::Ide, "reuse", None), "w");
    run.publish(&phase(PhaseState::Started, Phase::Iteration, "flow flow-new-session", 0), "w");
    run.daemon(
        r#"{"event":"runStarted","iterationId":"i","daemonBootStamp":"b","startedAt":"s"}"#,
        "w",
    );
    run.daemon(
        r#"{"event":"testStarted","timestamp":"t","id":"1","className":"com.example.AirNewSessionFlowUiTest","displayName":"restores the draft"}"#,
        "w",
    );
    run.advance(90);
    run.daemon(
        r#"{"event":"testFinished","timestamp":"t","id":"1","className":"com.example.AirNewSessionFlowUiTest","displayName":"restores the draft","status":"FAILED","durationMs":90000,"error":"no chip"}"#,
        "w",
    );
    run.daemon(
        r#"{"event":"summary","iterationId":"i","daemonBootStamp":"b","startedAt":"s","testsStarted":1,"testsFailed":1,"testsSkipped":0,"containersSkipped":0,"containerFailures":0,"hasFailures":true,"ideRunning":true}"#,
        "w",
    );
    run.publish(&phase(PhaseState::Finished, Phase::Iteration, "", 90), "w");
    run.publish(
        &Event::TraceReady(TraceReady {
            iteration_id: "i".to_owned(),
            bundle_id: "b1".to_owned(),
            test_class: "AirNewSessionFlowUiTest".to_owned(),
            scenario: "restores the draft".to_owned(),
            flow: None,
            status: "failed".to_owned(),
            has_video: false,
            zip: "/tmp/t.zip".to_owned(),
            entry: "001".to_owned(),
        }),
        "w",
    );
    let mut failed = verdict(Status::Failed, Some("tests_failed"), "1 test(s) failed");
    failed.counts = VerdictCounts {
        started: 1,
        failed: 1,
        ..VerdictCounts::default()
    };
    failed.failures = vec![Failure {
        class: "com.example.AirNewSessionFlowUiTest".to_owned(),
        name: Some("restores the draft".to_owned()),
        message: Some("no chip".to_owned()),
        trace: Some(TraceRef {
            bundle_id: "b1".to_owned(),
            test_class: "AirNewSessionFlowUiTest".to_owned(),
            scenario: "restores the draft".to_owned(),
            status: "failed".to_owned(),
            has_video: false,
        }),
    }];
    failed.rerun = vec!["run com.example.AirNewSessionFlowUiTest".to_owned()];
    failed.checkout = Some(checkout);
    failed.lease = Some(VerdictLease {
        workers: vec!["w".to_owned()],
        disposition: LeaseDisposition::ReleaseFailed,
        receipts: vec!["/r/w.json".to_owned()],
    });
    run.publish(&Event::Verdict(Box::new(failed)), "");
    run.publish(&run_finished(6, Some("tests_failed")), "");

    let card = run.card(120);
    expect![[r"
        ╭────────────────────────────────────────────────────────────────────────────────────────────────────────────╮
        │  FAILED   0 passed · 1 failed · 0 skipped · 1m30s (usual 5m00s)                                            │
        │ host build 30s · tests 1m30s                                                                               │
        │ slower than usual AirNewSessionFlowUiTest 1m30s (usual 30s)                                                │
        │ why ide reuse                                                                                              │
        │ ✗ AirNewSessionFlowUiTest › restores the draft — no chip  trace                                            │
        │ rerun ./community/tools/vm.cmd run com.example.AirNewSessionFlowUiTest                                     │
        │ ⚠ lease w release_failed (release it with `./community/tools/vm.cmd --lease-file /r/w.json lease release`) │
        │ ⚠ 15 uncommitted files were under test; check `git status` before you believe a red test                   │
        │ run http://127.0.0.1:6173/air/runs/run?id=me-run-1                                                         │
        ╰────────────────────────────────────────────────────────────────────────────────────────────────────────────╯"]]
    .assert_eq(&card);
    // The trace is a label, and its URL is in the OSC 8 link only.
    let raw = run.state.card(120).join("\n");
    assert!(
        raw.contains("\x1b]8;;http://127.0.0.1:6173/air/runs/trace?src=b1&scenario=restores+the+draft\x1b\\")
            && !card.contains("trace?src=b1"),
        "the trace of the failure is not an OSC 8 link:\n{raw:?}"
    );
    assert!(
        run.has_line("⚠ checkout 74d40e8bead8: 15 uncommitted files are under test"),
        "the dirty checkout is not said:\n{}",
        run.scrollback()
    );
    assert!(
        Run::new().state.card(120).is_empty(),
        "a command that did not finish a run has a card"
    );
}

/// A finished test waits for its trace, and its line gets the trace as an OSC 8 label. The tests line of the
/// iteration waits behind it, so it prints after the last trace and not between a test and its trace.
#[test]
fn a_test_waits_for_its_trace_and_the_tests_line_follows_it() {
    let mut run = Run::new();
    run.state.viewer_port = 7357;
    run.publish(&phase(PhaseState::Started, Phase::Iteration, "flow flow-draft", 0), "w");
    run.finish_test("keeps the draft", "w");
    run.publish(&phase(PhaseState::Finished, Phase::Iteration, "", 12), "w");
    assert!(
        !run.has_line("keeps the draft") && !run.has_line("✓ tests"),
        "a test that waits for its trace printed, or the line behind it did:\n{}",
        run.scrollback()
    );
    let live = run.live(120);
    assert!(
        live.contains("✓ keeps the draft 3s"),
        "the live region does not show the waiting test:\n{live}"
    );
    assert!(
        run.has_line("  AirDraftFlowUiTest"),
        "the class header is not in the scrollback:\n{}",
        run.scrollback()
    );
    let raw = run
        .state
        .apply(&trace_ready("keeps the draft", "b7"), Some(&Scope::worker("w")), run.now);
    let lines: Vec<String> = raw.iter().map(|line| strip(line)).collect();
    assert_eq!(lines, ["    ✓ keeps the draft 3s  trace · video", "  ✓ tests 12s"]);
    assert!(
        raw[0].contains("\x1b]8;;http://127.0.0.1:7357/air/runs/trace?src=b7&scenario=keeps+the+draft\x1b\\"),
        "the trace label carries no OSC 8 link: {:?}",
        raw[0]
    );
}

/// A test stops waiting when the next test of its worker finishes. Its trace then prints on a line of its own, and
/// that line names the test. The line waits behind the test that still waits, so the order stays.
#[test]
fn a_late_trace_names_its_test() {
    let mut run = Run::new();
    run.state.viewer_port = 7357;
    run.finish_test("first", "w");
    run.finish_test("second", "w");
    assert!(
        run.has_line("    ✓ first 3s") && !run.has_line("second"),
        "the scrollback is\n{}",
        run.scrollback()
    );
    run.publish(&trace_ready("first", "b1"), "w");
    run.publish(&trace_ready("second", "b2"), "w");
    assert_eq!(
        run.scrollback,
        [
            "  AirDraftFlowUiTest",
            "    ✓ first 3s",
            "    ✓ second 3s  trace · video",
            "      ✓ trace · video · AirDraftFlowUiTest › first",
        ]
    );
}

/// Two workers interleave their classes, so their lines stay flat and name the worker and the class.
#[test]
fn two_workers_print_flat_lines() {
    let mut run = Run::new();
    run.publish(&note("leased"), "air-linux-1");
    run.publish(&note("leased"), "air-linux-2");
    run.finish_test("first", "air-linux-1");
    run.publish(&iteration_reported("/r/1.json"), "air-linux-1");
    assert!(
        run.has_line("  [air-linux-1] ✓ AirDraftFlowUiTest › first 3s") && !run.has_line("  AirDraftFlowUiTest"),
        "the lines of two workers are not flat:\n{}",
        run.scrollback()
    );

    // With one worker, the class is a header and the test line names the scenario alone.
    let mut alone = Run::new();
    alone.finish_test("first", "air-linux-1");
    alone.publish(&iteration_reported("/r/1.json"), "air-linux-1");
    assert_eq!(alone.scrollback[..2], ["  AirDraftFlowUiTest", "    ✓ first 3s"]);
}

/// A build that took much longer than usual says what it did, on its line and on the card.
#[test]
fn a_slow_build_names_what_it_ran() {
    let mut run = Run::new();
    run.publish(&run_started(&[]), "");
    run.publish(&expected_lane(), "");
    run.publish(&phase(PhaseState::Started, Phase::HostBuild, "//a:b", 0), "");
    run.publish(
        &Event::BuildSummary(BuildSummary {
            processes: 1025,
            ran: 970,
            cached: 6684,
            critical_path_ms: 187_670,
        }),
        "",
    );
    run.advance(240);
    run.publish(&phase(PhaseState::Finished, Phase::HostBuild, "", 240), "");
    assert!(
        run.has_line("✓ host build 4m00s (usual 40s) ▲ — 970 actions ran, 6684 cached · critical path 3m07s"),
        "the build line does not say what it ran:\n{}",
        run.scrollback()
    );
    run.publish(&Event::Verdict(Box::new(verdict(Status::Passed, None, "0 test(s) passed"))), "");
    run.publish(&run_finished(0, None), "");
    let card = run.card(160);
    assert!(
        card.contains("slower than usual host build 4m00s (usual 40s): 970 actions ran, 6684 cached · critical path 3m07s"),
        "the card does not explain the slow build:\n{card}"
    );
    assert!(card.contains(" PASSED "), "{card}");
}

/// A verdict that is not a list of failures states its reason on the card, and a run of two lanes names each lane.
/// A failed phase names its reason.
#[test]
fn the_card_states_the_reason_the_lanes_and_a_failed_phase() {
    let mut run = Run::new();
    run.publish(&run_started(&[]), "");
    run.publish(&phase(PhaseState::Started, Phase::Release, "air-linux-1", 0), "");
    run.publish(
        &Event::Phase(PhaseChange {
            phase: Phase::Release,
            state: PhaseState::Failed,
            detail: None,
            elapsed_ms: 0,
            error: Some("the lease is still held (release_failed)".to_owned()),
        }),
        "",
    );
    let mut no_tests = verdict(
        Status::NoTests,
        Some("no_tests_discovered"),
        "lane ui-real: lane ui-real matched no tests in the hot tier (iteration it-2)",
    );
    no_tests.counts.started = 9;
    no_tests.lanes = vec![
        LaneVerdict {
            lane: "ui".to_owned(),
            status: Status::Passed,
            code: None,
            summary: "9 test(s) passed".to_owned(),
            counts: VerdictCounts {
                started: 9,
                ..VerdictCounts::default()
            },
            report: None,
        },
        LaneVerdict {
            lane: "ui-real".to_owned(),
            status: Status::NoTests,
            code: Some("no_tests_discovered".to_owned()),
            summary: "lane ui-real matched no tests in the hot tier (iteration it-2)".to_owned(),
            counts: VerdictCounts::default(),
            report: None,
        },
    ];
    run.publish(&Event::Verdict(Box::new(no_tests)), "");
    run.publish(&run_finished(6, Some("no_tests_discovered")), "");
    let card = run.card(160);
    for fragment in [
        "FAILED",
        "9 passed · 0 failed",
        "lane ui-real: lane ui-real matched no tests in the hot tier (iteration it-2)",
        "✓ lane ui 9 passed",
        "✗ lane ui-real lane ui-real matched no tests",
        "✗ release: the lease is still held (release_failed)",
    ] {
        assert!(card.contains(fragment), "the card has no {fragment:?}:\n{card}");
    }
}

/// A run that stopped before its tests reached a verdict says so and why, and not that tests failed.
#[test]
fn a_run_without_a_verdict_is_stopped_with_its_reason() {
    let mut run = Run::new();
    run.publish(&run_started(&[]), "");
    run.publish(
        &Event::RunFinished(RunFinished {
            exit_code: 69,
            code: Some("worker_unavailable".to_owned()),
            message: Some("no worker answered".to_owned()),
        }),
        "",
    );
    let card = run.card(120);
    assert!(
        card.contains(" STOPPED ") && card.contains("worker_unavailable: no worker answered"),
        "{card}"
    );
}

/// Without colour the text is the same, and no SGR code is written; a link is not a colour, so it stays. A dumb
/// terminal gets neither.
#[test]
fn no_color_and_a_dumb_terminal_are_honoured() {
    assert_eq!(
        ColorChoice::Auto.resolve(Some("1"), Some("xterm-256color"), None),
        Paint::fallback(false, true)
    );
    assert_eq!(ColorChoice::Auto.resolve(None, Some("dumb"), None), Paint::fallback(false, false));
    assert_eq!(ColorChoice::Auto.resolve(Some(""), Some("xterm"), None), COLOR);
    assert!(!ColorChoice::Auto.resolve(None, None, None).color);

    let plain = Paint::fallback(false, true);
    let mut run = Run::new();
    run.state = State::new("vm", plain);
    run.state.viewer_port = 7357;
    run.publish(&run_started(&[]), "");
    run.finish_test("first", "w");
    run.publish(&trace_ready("first", "b1"), "w");
    run.publish(&run_finished(0, None), "");
    let written = [run.state.live_view(run.now, 120), run.state.card(120)].concat().join("\n");
    assert!(!written.contains("\x1b["), "an SGR code without colour: {written:?}");
    assert!(written.contains("\x1b]8;;"), "the run link is gone: {written:?}");

    let dumb = Paint::fallback(false, false);
    assert_eq!(dumb.link("trace", "http://x"), "trace");
}
