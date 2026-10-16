use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use avl_testkit::http::{FakeHttp, Response};
use avl_wire::daemon::ExecutionIdentity;
use pretty_assertions::assert_eq;
use tempfile::TempDir;

use super::*;

const POLICY: Policy = Policy {
    active_execution: Duration::from_mins(30),
    progress_gap: Duration::from_mins(5),
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
    let watch = Watch::new(POLICY);
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
