use std::sync::{Arc, Mutex};

use avl_testkit::http::{FakeHttp, Request, Response};
use expect_test::expect;
use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;

const TOKEN: &str = "bridge-token";
const LAUNCH: &str = "air-ui-0123456789abcdef";
const DIGEST: &str = "fac4a8f1fdc799f94ddda2f702923d6609be71e8ead7d3304828712fff78e951";

/// One lifecycle answer of an IDE of [`LAUNCH`] in `phase`, which serves the vocabulary `vocabulary`.
fn snapshot(phase: &str, vocabulary: Option<&str>) -> String {
    let mut snapshot = json!({
        "seq": 1,
        "state": {
            "schemaVersion": 2,
            "phase": phase,
            "launch": LAUNCH,
            "pid": 4321,
            "projects": ["plain-project"],
            "indexing": false,
            "backgroundTasks": 0,
            "bridgePlugin": null,
            "vocabularyDigest": vocabulary,
        }
    });
    if phase == BIND_FAILED {
        snapshot["state"]["phase"] = json!("BUSY");
        snapshot["state"]["controlBindFailure"] = json!("Address already in use: 127.0.0.1:30309");
    }
    snapshot.to_string()
}

/// The phase of [`snapshot`] that stands for a busy IDE whose control server did not bind.
const BIND_FAILED: &str = "BIND_FAILED";

/// The answer `{value}` of the request endpoint.
fn value(value: &Value) -> Response {
    Response::json(200, json!({ "value": value }).to_string())
}

/// The class of a bridge request, without the package.
fn class(request: &Request) -> String {
    let body: Value = serde_json::from_slice(&request.body).unwrap_or(Value::Null);
    let name = body["type"].as_str().unwrap_or_default();
    name.strip_prefix("com.intellij.air.integration.bridge.").unwrap_or(name).to_owned()
}

/// A fake IDE that is ready, serves `vocabulary`, and drives gestures with `robot`. It answers each ping with its nonce
/// and logs the class of each request.
fn fake_ide(vocabulary: Option<&'static str>, robot: &'static str) -> (FakeHttp, Arc<Mutex<Vec<String>>>) {
    fake_ide_in("READY", vocabulary, robot)
}

/// [`fake_ide`] whose lifecycle answers `phase`.
fn fake_ide_in(phase: &'static str, vocabulary: Option<&'static str>, robot: &'static str) -> (FakeHttp, Arc<Mutex<Vec<String>>>) {
    let log = Arc::new(Mutex::new(Vec::new()));
    let seen = Arc::clone(&log);
    let server = FakeHttp::start(move |request| {
        if request.header("X-Air-Ui-Test-Token") != Some(TOKEN) {
            return Response::json(401, r#"{"failure":{"type":"java.lang.SecurityException","message":"bad token"}}"#);
        }
        if request.path.starts_with("/api/air/ui-test-events") {
            return Response::ok("application/json", snapshot(phase, vocabulary));
        }
        let name = class(request);
        seen.lock().unwrap().push(name.clone());
        let body: Value = serde_json::from_slice(&request.body).unwrap();
        match name.as_str() {
            "StepSequence" => {
                let nonce = body["steps"][1]["nonce"].clone();
                value(&json!([
                    { "value": robot, "durationMs": 1, "ran": true },
                    { "value": nonce, "durationMs": 1, "ran": true },
                ]))
            }
            "Quit" => value(&json!(4321)),
            other => Response::json(
                500,
                json!({ "failure": { "type": "java.lang.IllegalArgumentException", "message": other } }).to_string(),
            ),
        }
    });
    (server, log)
}

fn ready(server: &FakeHttp) -> (Result<(), AgentRefusal>, Vec<String>) {
    let bridge = Bridge::at(server.url(), TOKEN).unwrap();
    let mut notes = Vec::new();
    // The whole gate: the lifecycle, then the robot and ping chain.
    let result = await_lifecycle_ready(&bridge, LAUNCH, DIGEST, &mut || None, &mut |note| notes.push(note)).and_then(|_| ping(&bridge));
    (result, notes)
}

// A ready IDE of the launch passes the gate after one chain of the robot and the ping.
#[test]
fn a_ready_ide_passes_the_gate_after_one_chain_of_the_robot_and_the_ping() {
    let (server, log) = fake_ide(Some(DIGEST), INPUT_EVENTS_ROBOT);
    let (result, notes) = ready(&server);
    result.unwrap();
    assert_eq!(*log.lock().unwrap(), ["StepSequence"]);
    assert!(notes.iter().any(|note| note.contains("is ready as pid 4321")), "{notes:?}");
}

// A bare IDE is ready once its lifecycle says so and its vocabulary fits. The wait sends no request of the bridge, so
// the first request that the IDE gets is the first instruction of its suite.
#[test]
fn the_readiness_of_a_bare_ide_sends_no_request_of_the_bridge() {
    let (server, log) = fake_ide(Some(DIGEST), INPUT_EVENTS_ROBOT);
    let bridge = Bridge::at(server.url(), TOKEN).unwrap();
    let mut notes = Vec::new();
    await_lifecycle_ready(&bridge, LAUNCH, DIGEST, &mut || None, &mut |note| notes.push(note)).unwrap();
    assert_eq!(*log.lock().unwrap(), Vec::<String>::new());
    assert!(notes.iter().any(|note| note.contains("is ready as pid 4321")), "{notes:?}");
    let (server, _) = fake_ide(Some("0000"), INPUT_EVENTS_ROBOT);
    let bridge = Bridge::at(server.url(), TOKEN).unwrap();
    let refusal = await_lifecycle_ready(&bridge, LAUNCH, DIGEST, &mut || None, &mut |_| {}).unwrap_err();
    assert_eq!(refusal.code, VOCABULARY_SKEW_CODE);
}

// An IDE whose run ended cannot get ready, so the wait ends at the first check after the end, not after the 600 s of
// the gate. Here no bridge answers, as after a kill, and the run ends after the first poll.
#[test]
fn a_wait_on_an_ide_whose_run_ended_is_refused_at_once() {
    let bridge = Bridge::at(
        &format!("http://127.0.0.1:{}", avl_testkit::free_port_below_ephemeral_floor()),
        TOKEN,
    )
    .unwrap();
    let mut checks = 0;
    let mut ended = || {
        checks += 1;
        (checks > 1).then(|| "its run run-ide-start-1 ended with 143".to_owned())
    };
    let started = Instant::now();
    let refusal = await_lifecycle_ready(&bridge, LAUNCH, DIGEST, &mut ended, &mut |_| {}).unwrap_err();
    assert!(started.elapsed() < Duration::from_secs(10), "the wait took {:?}", started.elapsed());
    assert_eq!(refusal.code, NOT_READY_CODE);
    assert_eq!(
        refusal.message,
        format!("the IDE of '{LAUNCH}' did not get ready: the process of the IDE ended: its run run-ide-start-1 ended with 143")
    );
}

// The bridge serves the vocabulary of one build and the documents name another: the gate names both digests. A
// bridge that names no digest passes, and the daemon notes that once.
#[test]
fn a_vocabulary_of_another_build_is_a_skew_and_a_missing_one_is_noted_once() {
    let (server, _) = fake_ide(Some("0000"), INPUT_EVENTS_ROBOT);
    let refusal = ready(&server).0.unwrap_err();
    assert_eq!(refusal.code, VOCABULARY_SKEW_CODE);
    assert!(
        refusal.message.contains("0000") && refusal.message.contains(DIGEST),
        "{}",
        refusal.message
    );
    let noted = AtomicBool::new(false);
    let mut notes = Vec::new();
    for _ in 0..2 {
        check_vocabulary("air-ui-1", None, DIGEST, &noted, &mut |text| notes.push(text)).unwrap();
    }
    assert_eq!(notes.len(), 1, "{notes:?}");
    assert!(notes[0].contains("names no vocabulary digest"), "{notes:?}");
    let (server, _) = fake_ide(None, INPUT_EVENTS_ROBOT);
    ready(&server).0.unwrap();
}

// An IDE that drives gestures with the real pointer is refused, because a lane gesture must not depend on the screen.
#[test]
fn another_robot_is_refused() {
    let (server, _) = fake_ide(Some(DIGEST), "java.awt.Robot");
    let refusal = ready(&server).0.unwrap_err();
    assert_eq!(refusal.code, NOT_READY_CODE);
    assert!(refusal.message.contains("'java.awt.Robot'"), "{}", refusal.message);
}

// The chain is one request of two steps, and each request carries the class name of its operation.
#[test]
fn the_chain_is_one_step_sequence_of_the_robot_and_the_ping() {
    expect![[r#"{"steps":[{"type":"com.intellij.air.integration.bridge.InputRobot"},{"nonce":"n-1","type":"com.intellij.air.integration.bridge.Ping"}],"type":"com.intellij.air.integration.bridge.StepSequence"}"#]]
        .assert_eq(&robot_and_ping("n-1").to_string());
    let failed = json!([
        { "failure": { "type": "java.lang.IllegalStateException", "message": "no robot" }, "ran": true },
        { "ran": false },
    ]);
    let refusal = check_robot_and_ping(&failed, "n-1").unwrap_err();
    assert_eq!(
        refusal.message,
        "step 1 of the robot and ping chain failed: java.lang.IllegalStateException: no robot"
    );
    let wrong = json!([{ "value": INPUT_EVENTS_ROBOT, "ran": true }, { "value": "other", "ran": true }]);
    assert!(
        check_robot_and_ping(&wrong, "n-1")
            .unwrap_err()
            .message
            .contains("'other', expected 'n-1'")
    );
}

// A quit is one request, and the IDE answers when it scheduled the exit.
#[test]
fn a_quit_is_one_request() {
    let (server, log) = fake_ide(Some(DIGEST), INPUT_EVENTS_ROBOT);
    quit(&Bridge::at(server.url(), TOKEN).unwrap()).unwrap();
    assert_eq!(*log.lock().unwrap(), ["Quit"]);
}

// An IDE whose control server did not bind never gets ready. The gate ends at the first state that names the failure,
// with its text, and sends no bridge request.
#[test]
fn a_failed_control_bind_ends_the_gate_at_once_with_its_text() {
    let (server, log) = fake_ide_in(BIND_FAILED, Some(DIGEST), INPUT_EVENTS_ROBOT);
    let started = Instant::now();
    let refusal = ready(&server).0.unwrap_err();
    assert!(
        started.elapsed() < Duration::from_secs(10),
        "the gate waited {:?}",
        started.elapsed()
    );
    assert_eq!(refusal.code, CONTROL_BIND_CODE);
    assert!(
        refusal.message.contains("did not bind: Address already in use: 127.0.0.1:30309"),
        "{}",
        refusal.message
    );
    assert!(log.lock().unwrap().is_empty());
}
