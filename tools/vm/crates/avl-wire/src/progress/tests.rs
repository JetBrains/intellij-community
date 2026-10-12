use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;
use crate::daemon::decode_run_event;

fn at() -> jiff::Timestamp {
    "2026-09-24T12:00:00.005Z".parse().unwrap()
}

fn encode(event: &Event, worker: Option<&str>) -> Value {
    serde_json::to_value(Record::new(event, at(), worker)).unwrap()
}

// A note keeps the shape `--stream` always wrote: event `progress` and a top-level message.
#[test]
fn a_note_is_the_progress_record_it_always_was() {
    assert_eq!(
        encode(&Event::Note("staging".to_owned()), Some("air-linux-1")),
        json!({"schemaVersion": 1, "event": "progress", "at": "2026-09-24T12:00:00.005Z", "worker": "air-linux-1", "message": "staging"})
    );
    let line = serde_json::to_string(&Record::new(&Event::Note("x".to_owned()), at(), None)).unwrap();
    assert_eq!(
        line,
        r#"{"schemaVersion":1,"event":"progress","at":"2026-09-24T12:00:00.005Z","message":"x"}"#
    );
}

// A forwarded daemon record is the daemon's own bytes, so a reader of `--stream` sees what the daemon wrote.
#[test]
fn a_daemon_record_is_the_daemons_bytes() {
    let raw = r#"{"event":"testFinished","timestamp":"t","displayName":"a","id":"i","status":"SUCCESSFUL","durationMs":3,"extra":1}"#;
    let line = serde_json::to_string(&Record::new(&Event::Daemon(decode_run_event(raw.as_bytes()).unwrap()), at(), None)).unwrap();
    assert_eq!(
        line,
        format!(r#"{{"schemaVersion":1,"event":"runProgress","at":"2026-09-24T12:00:00.005Z","data":{raw}}}"#)
    );

    let output = encode(&Event::Daemon(RunEvent::output("hi")), None);
    assert_eq!(output["data"], json!({"event": "output", "text": "hi"}));
}

#[test]
fn a_phase_record_carries_its_state() {
    let fields = encode(
        &Event::Phase(PhaseChange {
            phase: Phase::HostBuild,
            state: PhaseState::Failed,
            detail: None,
            elapsed_ms: 1200,
            error: Some("exit 1".to_owned()),
        }),
        None,
    );
    assert_eq!(fields["event"], "phase");
    assert_eq!(
        fields["data"],
        json!({"phase": "host-build", "state": "failed", "elapsedMs": 1200, "error": "exit 1"})
    );
    assert!(fields.get("message").is_none());
}

// Every phase has a label a person reads, so a new phase cannot print its bare id by accident.
#[test]
fn every_phase_has_a_label() {
    for phase in Phase::ALL {
        assert_ne!(phase.label(), phase.as_str(), "{phase} has no label");
    }
}

// A decision is typed for a reader that branches, and keeps the one line a person reads as its message.
#[test]
fn a_decision_carries_its_line_as_the_message() {
    let decision = Decision {
        subject: Subject::Daemon,
        action: "restart".to_owned(),
        reason: Some("the stable runtime or JBR changed".to_owned()),
    };
    let fields = encode(&Event::Decision(decision), Some("air-linux-1"));
    assert_eq!(fields["event"], "decision");
    assert_eq!(fields["message"], "daemon restart: the stable runtime or JBR changed");
    assert_eq!(
        fields["data"],
        json!({"subject": "daemon", "action": "restart", "reason": "the stable runtime or JBR changed"})
    );
    assert_eq!(
        Decision {
            subject: Subject::Ide,
            action: "reuse".to_owned(),
            reason: None
        }
        .line(),
        "ide reuse"
    );
}

// A verdict carries its summary as the message, so the journal and the viewer read the same one line.
#[test]
fn a_verdict_carries_its_summary_as_the_message() {
    let verdict = Verdict {
        status: Status::Failed,
        code: Some("tests_failed".to_owned()),
        summary: "1 of 3 tests failed".to_owned(),
        counts: VerdictCounts {
            started: 3,
            failed: 1,
            ..VerdictCounts::default()
        },
        failures: Vec::new(),
        rerun: vec!["run a.B".to_owned()],
        skipped: Vec::new(),
        unreported: Vec::new(),
        lanes: Vec::new(),
        shards: None,
        lease: None,
        checkout: None,
        traces: None,
        timing: None,
        report: None,
    };
    assert!(!verdict.passed());
    assert_eq!(verdict.counts.passed(), 2);
    let fields = encode(&Event::Verdict(Box::new(verdict)), None);
    assert_eq!(fields["event"], "verdict");
    assert_eq!(fields["message"], "1 of 3 tests failed");
    assert_eq!(
        fields["data"],
        json!({
            "status": "failed", "code": "tests_failed", "summary": "1 of 3 tests failed",
            "counts": {"started": 3, "failed": 1, "skipped": 0}, "rerun": ["run a.B"],
        })
    );

    let sum = VerdictCounts {
        started: 3,
        failed: 1,
        ..VerdictCounts::default()
    } + VerdictCounts {
        started: 4,
        skipped: 2,
        container_failures: 1,
        ..VerdictCounts::default()
    };
    assert_eq!(
        sum,
        VerdictCounts {
            started: 7,
            failed: 1,
            skipped: 2,
            containers_skipped: 0,
            container_failures: 1
        }
    );
    assert_eq!(
        VerdictCounts {
            started: 1,
            failed: 2,
            ..VerdictCounts::default()
        }
        .passed(),
        0
    );
}

#[test]
fn a_build_summary_carries_its_line_as_the_message() {
    let summary = BuildSummary {
        processes: 1025,
        ran: 970,
        cached: 6684,
        critical_path_ms: 187_670,
    };
    let fields = encode(&Event::BuildSummary(summary), None);
    assert_eq!(fields["event"], "buildSummary");
    assert_eq!(fields["message"], "970 actions ran, 6684 cached, critical path 187.7s");
    assert_eq!(fields["data"]["ran"], 970);
    assert_eq!(
        BuildSummary {
            ran: 0,
            cached: 3,
            ..summary
        }
        .line()
        .as_str(),
        "0 actions ran, 3 cached, critical path 187.7s"
    );
    assert_eq!(
        BuildSummary {
            critical_path_ms: 0,
            ..summary
        }
        .line(),
        "970 actions ran, 6684 cached"
    );
}

#[test]
fn expected_finds_a_phase_by_its_key() {
    let expected = Expected {
        run: None,
        phases: vec![
            PhaseTypical {
                phase: Phase::HostBuild,
                key: None,
                typical: Typical { ms: 45_000, samples: 3 },
            },
            PhaseTypical {
                phase: Phase::Iteration,
                key: Some("lane ui".to_owned()),
                typical: Typical { ms: 200_000, samples: 2 },
            },
        ],
        classes: BTreeMap::new(),
    };
    assert_eq!(
        expected.phase(Phase::Iteration, Some("lane ui")),
        Some(&Typical { ms: 200_000, samples: 2 })
    );
    assert_eq!(expected.phase(Phase::Iteration, Some("other")), None);
    assert_eq!(expected.phase(Phase::HostBuild, None).map(|typical| typical.ms), Some(45_000));
    assert_eq!(Expected::default().phase(Phase::HostBuild, None), None);
    let fields = encode(&Event::Expected(expected), None);
    assert_eq!(fields["event"], "expected");
    assert_eq!(
        fields["data"]["phases"][1],
        json!({"phase": "iteration", "key": "lane ui", "ms": 200_000, "samples": 2})
    );
}

// Journals are read back: a written record decodes and round-trips byte for byte.
#[test]
fn a_journal_record_reads_back() {
    let written = r#"{"schemaVersion":1,"event":"runStarted","at":"2026-09-24T12:00:00.005Z","data":{"runId":"run-1","command":"run","args":["lane","ui"]}}"#;
    let record: Record = serde_json::from_str(written).unwrap();
    assert_eq!(
        (record.event, record.worker.as_deref(), record.message.as_str()),
        (Kind::RunStarted, None, "")
    );
    let started: RunStarted = serde_json::from_str(record.data.as_ref().unwrap().get()).unwrap();
    assert_eq!(started.args, ["lane", "ui"]);
    assert_eq!(serde_json::to_string(&record).unwrap(), written);
}
