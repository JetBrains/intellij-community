use std::io::Write;
use std::sync::{Arc, Condvar, Mutex};
use std::time::{Duration, Instant};

use avl_testkit::http::{FakeHttp, Response};
use avl_wire::daemon::ExecutionIdentity;
use pretty_assertions::assert_eq;
use tempfile::TempDir;

use super::*;

const POLICY: Policy = Policy {
    active_execution: Duration::from_mins(30),
    progress_gap: Duration::from_mins(5),
    run_end: None,
};

fn identity(name: &str) -> ExecutionIdentity {
    ExecutionIdentity {
        id: format!("[scenario:{name}]"),
        display_name: name.to_owned(),
        class_name: Some("com.example.AirSuiteGeneratedFlowUiTest".to_owned()),
        method_name: Some("generatedFlowScenarios".to_owned()),
    }
}

// Between scenarios the progress gap applies, and during one the active execution budget. The emergency deadline caps
// the run at a gap before each scenario and after the last, the budget of each, and the kill.
#[test]
fn the_deadlines_follow_the_phase_and_the_emergency_caps_the_run() {
    let start = Instant::now();
    let mut deadlines = Deadlines::new(POLICY, start);
    assert_eq!(deadlines.expiry(start + Duration::from_mins(4)), None);
    assert_eq!(
        deadlines.expiry(start + Duration::from_mins(5)).map(|expiry| expiry.reason),
        Some(ExpiryReason::ProgressGap)
    );
    deadlines.plan_started(2, start);
    deadlines.execution_started(identity("first"), start);
    assert_eq!(deadlines.expiry(start + Duration::from_mins(29)), None);
    let active = deadlines.expiry(start + Duration::from_mins(30)).unwrap();
    assert_eq!(active.reason, ExpiryReason::ActiveExecution);
    assert_eq!(active.active, Some(identity("first")));
    deadlines.execution_finished(start + Duration::from_mins(20));
    assert_eq!(deadlines.expiry(start + Duration::from_mins(24)), None);
    // 4 gaps of 5 min, 2 executions of 30 min, and the kill grace of 1 min.
    let emergency = start + Duration::from_mins(81);
    deadlines.execution_started(identity("second"), start + Duration::from_mins(80));
    assert_eq!(deadlines.expiry(start + Duration::from_secs(81 * 60 - 1)), None);
    assert_eq!(
        deadlines.expiry(emergency).map(|expiry| expiry.reason),
        Some(ExpiryReason::Emergency)
    );
    deadlines.completed();
    assert_eq!(deadlines.expiry(emergency + Duration::from_hours(1)), None);
    assert_eq!(deadlines.intrusion("a dialog".to_owned(), emergency), None);
}

// The budget of the run caps every deadline: the gap, the budget of a scenario and the emergency. At its end the run
// expires as run_budget_timeout, also inside a scenario whose own budget is far from spent.
#[test]
fn the_run_budget_caps_every_deadline_and_ends_the_run_as_run_budget_timeout() {
    let start = Instant::now();
    let end = start + Duration::from_mins(10);
    let mut deadlines = Deadlines::new(
        Policy {
            run_end: Some(end),
            ..POLICY
        },
        start,
    );
    deadlines.plan_started(20, start);
    assert_eq!(deadlines.emergency, Some(end), "the emergency deadline passed the budget");
    deadlines.execution_started(identity("first"), start + Duration::from_mins(8));
    assert_eq!(
        deadlines.phase_deadline,
        Some(end),
        "the budget of the scenario passed the budget of the run"
    );
    assert_eq!(deadlines.expiry(start + Duration::from_secs(599)), None);
    let expiry = deadlines.expiry(end).unwrap();
    assert_eq!((expiry.reason, expiry.deadline), (ExpiryReason::RunBudget, end));
    assert_eq!(expiry.reason.wire(), "run_budget_timeout");
    assert_eq!(expiry.active, Some(identity("first")));
    deadlines.execution_finished(start + Duration::from_mins(9));
    assert_eq!(deadlines.phase_deadline, Some(end), "the gap passed the budget of the run");
}

// A watch says what the run thread does: the scenario that runs, its open steps, outermost first, and the budget left.
#[test]
fn a_snapshot_names_the_scenario_the_open_steps_and_the_budget_left() {
    let watch = Watch::new(Policy {
        run_end: Some(Instant::now() + Duration::from_mins(10)),
        ..POLICY
    });
    assert_eq!(watch.snapshot().scenario, None);
    let mut out = Vec::new();
    let stream = Stream::new(&mut out);
    watch.execution_started(&stream, identity("restore-loaded"));
    let snapshot = watch.step("relaunch for ide", || watch.step("readiness", || watch.snapshot()));
    assert_eq!(
        snapshot.scenario.map(|(name, _)| name).as_deref(),
        Some("AirSuiteGeneratedFlowUiTest.restore-loaded")
    );
    assert_eq!(
        snapshot.steps.map(|(path, _)| path).as_deref(),
        Some("relaunch for ide > readiness")
    );
    let left = snapshot.budget_left.unwrap();
    assert!(left > Duration::from_mins(9) && left <= Duration::from_mins(10), "{left:?}");
    assert_eq!(watch.snapshot().steps, None, "a step that returned is still open");
}

/// Probes that answer an intruder on every probe and count what the watchdog asked.
#[derive(Default)]
struct Intruded {
    dismissals: Mutex<u32>,
    kills: Mutex<u32>,
}

impl Probes for Intruded {
    fn intrusion(&self) -> Option<String> {
        Some("an unexpected modal dialog no scenario opened is showing: 'EAP expired'".to_owned())
    }

    fn dismiss_intrusion(&self) -> Option<String> {
        *self.dismissals.lock().unwrap() += 1;
        Some("cancelled 'EAP expired'".to_owned())
    }

    fn diagnostic_dump(&self) -> Result<String, String> {
        Ok("\"main\" RUNNABLE".to_owned())
    }

    fn kill(&self) -> Result<(), String> {
        *self.kills.lock().unwrap() += 1;
        Ok(())
    }
}

// The first intrusion of a run gets one dismissal. The next tick that still sees it expires the run as an intrusion,
// names the dismissal, and kills the IDE, so the run thread unwinds.
#[test]
fn an_intruder_that_survives_its_dismissal_expires_the_run_and_kills_the_ide() {
    let directory = TempDir::new().unwrap();
    let probes = Intruded::default();
    let watch = Watch::new(POLICY).with_kill_grace(Duration::from_millis(20));
    let mut out = Vec::new();
    let stream = Stream::new(&mut out);
    watch.execution_started(&stream, identity("first"));
    super::watch(&watch, &stream, &probes, directory.path());
    assert_eq!(*probes.dismissals.lock().unwrap(), 1);
    assert_eq!(*probes.kills.lock().unwrap(), 1);
    let expired = watch.expired().unwrap();
    assert!(expired.starts_with("the watchdog ended the run: ide_intrusion: "), "{expired}");
    drop(stream);
    let records = String::from_utf8(out).unwrap();
    let expiry = records.lines().find(|line| line.contains("\"watchdogExpired\"")).unwrap();
    assert!(
        expiry.contains("The run tried to clear it first: cancelled 'EAP expired'"),
        "{expiry}"
    );
    // No run thread completes the watch here, so the watchdog says that the run thread did not unwind.
    assert!(
        records.contains("the run thread did not unwind within 20 ms after the watchdog killed the IDE"),
        "{records}"
    );
}

/// Probes that count what the watchdog asked: the intruder probes and the dumps. No intruder shows.
#[derive(Default)]
struct Counting {
    probes: Mutex<u32>,
    dumps: Mutex<u32>,
}

impl Probes for Counting {
    fn intrusion(&self) -> Option<String> {
        *self.probes.lock().unwrap() += 1;
        None
    }

    fn dismiss_intrusion(&self) -> Option<String> {
        None
    }

    fn diagnostic_dump(&self) -> Result<String, String> {
        *self.dumps.lock().unwrap() += 1;
        Ok("\"main\" RUNNABLE".to_owned())
    }

    fn kill(&self) -> Result<(), String> {
        Ok(())
    }
}

/// Runs one step of `step` past the slow-step budget under the watchdog thread, and answers what the IDE was asked. A
/// `quiet` hold starts before the watchdog thread and ends after it, so the first tick cannot come before the hold.
fn watched_step(quiet: bool) -> (u32, u32) {
    let directory = TempDir::new().unwrap();
    let probes = Counting::default();
    let watch = Watch::new(POLICY).with_slow_step(Duration::from_millis(100));
    let mut out = Vec::new();
    let stream = Stream::new(&mut out);
    let run = || {
        std::thread::scope(|scope| {
            scope.spawn(|| super::watch(&watch, &stream, &probes, directory.path()));
            watch.step("measure", || std::thread::sleep(TICK + Duration::from_millis(500)));
            watch.completed(&stream);
        });
    };
    if quiet {
        watch.quiet(run);
    } else {
        run();
    }
    (*probes.probes.lock().unwrap(), *probes.dumps.lock().unwrap())
}

// While a quiet hold runs, the watchdog sends the IDE no probe and no dump, so a bare IDE measures a start-up that no
// request of the watchdog changed. The same step without the hold gets both.
#[test]
fn a_quiet_hold_sends_the_ide_no_probe_and_no_dump() {
    assert_eq!(watched_step(true), (0, 0));
    let (probes, dumps) = watched_step(false);
    assert!(probes > 0, "the watchdog probed no intruder");
    assert_eq!(dumps, 1);
}

// A file name part keeps lowercase letters and digits, joins the rest with one `-`, and keeps 40 characters.
#[test]
fn a_slug_is_lowercase_words_joined_by_dashes() {
    assert_eq!(
        slug("before reset of Rename: closed project"),
        "before-reset-of-rename-closed-project"
    );
    assert_eq!(slug("!!!"), "step");
    assert_eq!(slug(&"a".repeat(60)).len(), 40);
}

// The dump of a slow step comes from the route of the resident plugin, with the token of the launch. The request endpoint
// of the bridge answers 503 while a reload unloads the bridge plugin, and the dump still arrives.
#[test]
fn the_ide_dump_comes_from_the_resident_route_while_the_bridge_unloads() {
    const DUMP: &str = "\"main\" RUNNABLE\n\nCoroutine dump:\n";
    let asked = Arc::new(Mutex::new(Vec::new()));
    let seen = Arc::clone(&asked);
    let server = FakeHttp::start(move |request| {
        seen.lock().unwrap().push(format!("{} {}", request.method, request.path));
        if request.header("X-Air-Ui-Test-Token") != Some("lane-token") {
            return Response::error(401, "unsigned Air UI-test dump request");
        }
        match (request.method.as_str(), request.path.as_str()) {
            ("GET", DUMP_ROUTE) => Response::ok("text/plain; charset=utf-8", DUMP),
            _ => Response::json(
                503,
                r#"{"failure":{"type":"java.lang.IllegalStateException","message":"the Air UI test bridge unloads"}}"#,
            ),
        }
    });

    let dump = ide_dump(&Bridge::at(server.url(), "lane-token").unwrap()).unwrap();
    assert_eq!(dump, DUMP);
    assert_eq!(*asked.lock().unwrap(), [format!("GET {DUMP_ROUTE}")]);

    let refused = ide_dump(&Bridge::at(server.url(), "another-launch").unwrap()).unwrap_err();
    assert_eq!(
        refused,
        format!("GET {DUMP_ROUTE}: the bridge answered HTTP 401: unsigned Air UI-test dump request")
    );
}

/// The budget of a scenario that the first tick after one second finds spent.
const SPENT: Policy = Policy {
    active_execution: Duration::from_millis(10),
    progress_gap: Duration::from_mins(5),
    run_end: None,
};

/// A writer that blocks each write while it is shut, as a stream does whose reader stopped reading.
struct Gate {
    shut: Arc<(Mutex<bool>, Condvar)>,
    written: Arc<Mutex<Vec<u8>>>,
}

impl Write for Gate {
    fn write(&mut self, bytes: &[u8]) -> std::io::Result<usize> {
        let (lock, opened) = &*self.shut;
        let mut shut = lock.lock().unwrap();
        while *shut {
            shut = opened.wait(shut).unwrap();
        }
        self.written.lock().unwrap().extend_from_slice(bytes);
        Ok(bytes.len())
    }

    fn flush(&mut self) -> std::io::Result<()> {
        Ok(())
    }
}

/// Probes whose kill counts itself and opens the gate of the stream.
struct OpensTheGate {
    shut: Arc<(Mutex<bool>, Condvar)>,
    kills: Mutex<u32>,
}

impl Probes for OpensTheGate {
    fn intrusion(&self) -> Option<String> {
        None
    }

    fn dismiss_intrusion(&self) -> Option<String> {
        None
    }

    fn diagnostic_dump(&self) -> Result<String, String> {
        Ok(String::new())
    }

    fn kill(&self) -> Result<(), String> {
        *self.kills.lock().unwrap() += 1;
        let (lock, opened) = &*self.shut;
        *lock.lock().unwrap() = false;
        opened.notify_all();
        Ok(())
    }
}

// The kill comes before the watchdogExpired record. A stream that blocks then holds only the record, and the IDE ends
// anyway, so the run thread can unwind.
#[test]
fn the_kill_comes_before_the_expiry_record_so_a_blocked_stream_does_not_hold_it() {
    let directory = TempDir::new().unwrap();
    let shut = Arc::new((Mutex::new(false), Condvar::new()));
    let written = Arc::new(Mutex::new(Vec::new()));
    let mut out = Gate {
        shut: Arc::clone(&shut),
        written: Arc::clone(&written),
    };
    let stream = Stream::new(&mut out);
    let watch = Watch::new(SPENT).with_kill_grace(Duration::from_millis(10));
    watch.execution_started(&stream, identity("first"));
    *shut.0.lock().unwrap() = true;
    let probes = OpensTheGate {
        shut: Arc::clone(&shut),
        kills: Mutex::new(0),
    };
    std::thread::scope(|scope| {
        scope.spawn(|| super::watch(&watch, &stream, &probes, directory.path()));
        let deadline = Instant::now() + Duration::from_secs(20);
        while *probes.kills.lock().unwrap() == 0 && Instant::now() < deadline {
            std::thread::sleep(Duration::from_millis(10));
        }
        let killed = *probes.kills.lock().unwrap() == 1;
        // Opens the gate in any case, so a watchdog that waits on the stream returns and the scope ends.
        *shut.0.lock().unwrap() = false;
        shut.1.notify_all();
        assert!(killed, "the kill waited for the stream");
    });
    let records = String::from_utf8(written.lock().unwrap().clone()).unwrap();
    assert!(records.contains("\"watchdogExpired\""), "{records}");
}

/// Probes whose kill completes the watch, as a run thread does that unwinds at once after the kill.
struct CompletesOnKill<'a, 's> {
    watch: &'a Watch,
    stream: &'a Stream<'s>,
}

impl Probes for CompletesOnKill<'_, '_> {
    fn intrusion(&self) -> Option<String> {
        None
    }

    fn dismiss_intrusion(&self) -> Option<String> {
        None
    }

    fn diagnostic_dump(&self) -> Result<String, String> {
        Ok(String::new())
    }

    fn kill(&self) -> Result<(), String> {
        self.watch.completed(self.stream);
        Ok(())
    }
}

// After a kill the watchdog waits for the run thread to complete, and a run thread that does complete ends the wait at
// once and gets no note.
#[test]
fn a_run_thread_that_completes_after_the_kill_ends_the_wait_without_a_note() {
    let directory = TempDir::new().unwrap();
    let watch = Watch::new(SPENT).with_kill_grace(Duration::from_mins(1));
    let mut out = Vec::new();
    let stream = Stream::new(&mut out);
    watch.execution_started(&stream, identity("first"));
    let started = Instant::now();
    super::watch(
        &watch,
        &stream,
        &CompletesOnKill {
            watch: &watch,
            stream: &stream,
        },
        directory.path(),
    );
    assert!(
        started.elapsed() < Duration::from_secs(30),
        "the watchdog waited out its kill grace"
    );
    drop(stream);
    let records = String::from_utf8(out).unwrap();
    assert!(records.contains("\"watchdogExpired\""), "{records}");
    assert!(!records.contains("did not unwind"), "{records}");
}
