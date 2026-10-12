use std::collections::BTreeMap;
use std::thread;

use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;
use crate::refusal::RefusalExt;

fn recorder(mode: Mode) -> (Reporter, Buffer, Buffer) {
    let (reporter, stdout, stderr) = Reporter::in_memory("vm.ts");
    reporter.set_mode(mode);
    (reporter, stdout, stderr)
}

fn decode(line: &str) -> Value {
    serde_json::from_str(line).unwrap_or_else(|error| panic!("cannot decode {line:?}: {error}"))
}

fn lines(buffer: &Buffer) -> Vec<String> {
    buffer.text().lines().map(str::to_owned).collect()
}

fn only_line(buffer: &Buffer) -> Value {
    let lines = lines(buffer);
    assert_eq!(lines.len(), 1, "expected one line, got {lines:?}");
    decode(&lines[0])
}

// A successful command writes exactly one object, on stdout, and nothing on stderr. "Exactly one" is the whole
// contract: a caller reads one object and stops.
#[test]
fn a_success_writes_one_object_on_stdout() {
    let (reporter, stdout, stderr) = recorder(Mode::Json);
    let exit = main("status", &reporter, || Outcome::data(json!({"worker": "air-linux-1"})));
    assert_eq!(exit, Exit::OK);
    assert!(stderr.is_empty(), "a success wrote to stderr: {stderr:?}");
    assert_eq!(
        only_line(&stdout),
        json!({"schemaVersion": 1, "ok": true, "command": "status", "data": {"worker": "air-linux-1"}})
    );
}

// A failure writes its one object to stderr, so a caller that only reads stdout gets nothing it might mistake
// for an answer.
#[test]
fn a_failure_writes_one_object_on_stderr() {
    let (reporter, stdout, stderr) = recorder(Mode::Json);
    let exit = main("run", &reporter, || {
        Err(Refusal::new(
            "daemon_died",
            Exit::SOFTWARE,
            "the daemon exited before the run started",
        ))
    });
    assert_eq!(exit, Exit::SOFTWARE);
    assert!(stdout.is_empty(), "a failure wrote to stdout: {stdout:?}");
    // `details` is written even when there is none, as null.
    assert_eq!(
        only_line(&stderr),
        json!({
            "schemaVersion": 1,
            "ok": false,
            "command": "run",
            "error": {"code": "daemon_died", "message": "the daemon exited before the run started", "details": null},
        })
    );
    let text = stderr.text();
    assert!(text.contains(r#""details":null"#), "{text}");
}

// No command was named is a different fact from a command named the empty string.
#[test]
fn a_failure_before_a_command_reports_null() {
    let (reporter, _, stderr) = recorder(Mode::Json);
    main("", &reporter, || Err(Refusal::usage("--backend must be tart, parallels or linux")));
    let envelope = only_line(&stderr);
    assert_eq!(envelope.get("command"), Some(&Value::Null));
}

#[test]
fn details_are_carried_when_the_refusal_has_them() {
    let (reporter, _, stderr) = recorder(Mode::Json);
    main("lease", &reporter, || {
        Err(Refusal::new("lease_busy", Exit::TEMP_FAIL, "another holder has the lock").with_details(json!({"holder": "shard-3d9936c3"})))
    });
    assert_eq!(only_line(&stderr)["error"]["details"], json!({"holder": "shard-3d9936c3"}));
}

// Human mode prints prose and no JSON at all, and adds the newline only when the text lacks one.
#[test]
fn text_mode_prints_prose() {
    let (reporter, stdout, _) = recorder(Mode::Human(Terminal::default()));
    main("status", &reporter, || Outcome::new(json!({"ignored": true}), "air-linux-1 free"));
    assert_eq!(stdout.text(), "air-linux-1 free\n");

    let (reporter, stdout, _) = recorder(Mode::Human(Terminal::default()));
    main("status", &reporter, || Outcome::new((), "already ends\n"));
    assert_eq!(stdout.text(), "already ends\n");

    // A command with nothing to say in text mode prints nothing, rather than an empty line.
    let (reporter, stdout, _) = recorder(Mode::Human(Terminal::default()));
    main("status", &reporter, || Outcome::data(1));
    assert!(stdout.is_empty(), "printed {stdout:?}");
}

#[test]
fn progress_is_silent_unless_asked_for() {
    let (reporter, _, stderr) = recorder(Mode::Json);
    reporter.note("staging the runtime", None);
    reporter.publish(
        Event::Structured {
            data: json!({"event": "testStarted"}),
            line: String::new(),
        },
        None,
    );
    assert!(stderr.is_empty(), "progress without --stream: {stderr:?}");
}

#[test]
fn progress_is_ndjson_on_stderr() {
    let (reporter, stdout, stderr) = recorder(Mode::Stream);
    reporter.note("staging the runtime", Some(&Scope::worker("air-linux-2")));
    reporter.publish(
        Event::Structured {
            data: json!({"event": "testStarted"}),
            line: String::new(),
        },
        None,
    );
    assert!(stdout.is_empty());
    let lines = lines(&stderr);
    assert_eq!(lines.len(), 2, "{lines:?}");
    let first = decode(&lines[0]);
    assert_eq!(first["event"], "progress");
    assert_eq!(first["message"], "staging the runtime");
    assert_eq!(first["worker"], "air-linux-2");
    let second = decode(&lines[1]);
    assert_eq!(second["event"], "runProgress");
    // An unscoped record carries no worker field at all.
    assert_eq!(second.get("worker"), None, "{second}");
}

// A structured record is one fact with two forms: human mode prints its line and no record; JSON mode writes its
// record and no line.
#[test]
fn a_structured_record_is_a_line_in_text_and_a_record_in_json() {
    let event = Event::Structured {
        data: json!({"event": "flakeTrial"}),
        line: "trial 1: passed".to_owned(),
    };
    let (reporter, _, stderr) = recorder(Mode::Stream);
    reporter.publish(event.clone(), None);
    let written = stderr.text();
    assert!(
        !written.contains("trial 1: passed") && written.contains(r#""flakeTrial""#),
        "{written}"
    );

    let (reporter, _, stderr) = recorder(Mode::Human(Terminal::default()));
    reporter.publish(event, Some(&Scope::worker("air-linux-1")));
    assert_eq!(stderr.text(), "vm.ts: [air-linux-1] trial 1: passed\n");

    let (reporter, _, stderr) = recorder(Mode::Human(Terminal::default()));
    reporter.publish(
        Event::Structured {
            data: json!({"event": "daemonPhases"}),
            line: String::new(),
        },
        None,
    );
    assert!(stderr.is_empty(), "a record with no line printed {stderr:?}");
}

// The reason the reporter holds a mutex: several workers stream at once, and a torn NDJSON line is unparseable.
#[test]
fn concurrent_progress_does_not_tear_lines() {
    const WORKERS: usize = 8;
    const RECORDS_EACH: usize = 40;
    let (reporter, _, stderr) = recorder(Mode::Stream);
    thread::scope(|scope| {
        for worker in 0..WORKERS {
            let reporter = reporter.clone();
            scope.spawn(move || {
                let named = Scope::worker(format!("air-linux-{worker}"));
                for _ in 0..RECORDS_EACH {
                    reporter.note("x".repeat(200), Some(&named));
                }
            });
        }
    });
    let lines = lines(&stderr);
    assert_eq!(lines.len(), WORKERS * RECORDS_EACH);
    for (index, line) in lines.iter().enumerate() {
        serde_json::from_str::<Value>(line).unwrap_or_else(|error| panic!("record {index} is torn: {error} ({line:?})"));
    }
}

// Data that is not JSON is the command's own error, reported rather than swallowed, and nothing reaches stdout:
// the caller is waiting for exactly one object.
#[test]
fn unencodable_data_is_reported_rather_than_swallowed() {
    let (reporter, stdout, stderr) = recorder(Mode::Json);
    let exit = main("status", &reporter, || {
        // A map with non-string keys has no JSON form.
        Outcome::data(BTreeMap::from([((1, 2), "pair")]))
    });
    assert_eq!(exit, Exit::FAILURE);
    assert!(stdout.is_empty(), "a broken envelope reached stdout: {stdout:?}");
    let error = &only_line(&stderr)["error"];
    assert_eq!(error["code"], "internal_error");
    let message = error["message"].as_str().unwrap_or_default();
    assert!(message.contains("cannot encode this command's own output"), "{message}");
}

// The number is controller-wide and the wire owns it; two constants that must agree is the defect the wire layer
// exists to remove.
#[test]
fn the_schema_version_is_the_wires_own() {
    assert_eq!(SCHEMA_VERSION, 1);
    assert_eq!(SCHEMA_VERSION, avl_wire::supervisor::SCHEMA_VERSION);
}

// A phase dropped without an end is a failed phase, never a green one: a `?` that returned early must not read
// as success.
#[test]
fn a_phase_dropped_without_an_end_is_reported_failed() {
    let (reporter, _, stderr) = recorder(Mode::Stream);
    {
        let _phase = reporter.start_phase(Phase::Lease, "", None);
    }
    let lines = lines(&stderr);
    assert_eq!(lines.len(), 2, "{lines:?}");
    let ended = decode(&lines[1]);
    assert_eq!(ended["data"]["state"], "failed");
}
