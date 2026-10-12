use std::collections::BTreeSet;

use pretty_assertions::assert_eq;
use proptest::prelude::*;
use serde_json::{Map, Value};

use super::*;

fn fields(state: &RunState) -> Map<String, Value> {
    match serde_json::to_value(state).unwrap() {
        Value::Object(fields) => fields,
        other => panic!("a run state encoded as {other}"),
    }
}

// The spellings, as literals, so this file is a second reader of the contract rather than a restatement of
// the declarations. A rename that changed both would have to change this too.
#[test]
fn phase_order_is_part_of_the_contract() {
    let phases: Vec<&str> = Phase::ALL.iter().map(|phase| phase.as_str()).collect();
    // The host compares this list order-sensitively.
    assert_eq!(phases, ["starting", "running", "orphaned", "finished"]);
}

#[test]
fn outcome_order_is_part_of_the_contract() {
    let outcomes: Vec<&str> = Outcome::ALL.iter().map(Outcome::as_str).collect();
    assert_eq!(
        outcomes,
        [
            "succeeded",
            "failed",
            "canceled",
            "canceled_orphan",
            "rejected",
            "failed_to_start",
            "supervisor_lost",
            "orphaned"
        ]
    );
}

// The defect this module exists to make impossible.
#[test]
fn cancelled_with_two_ls_is_not_an_outcome() {
    assert!(!"cancelled".parse::<Outcome>().unwrap().is_declared());
    assert!(!Outcome::Unknown("cancelled".to_owned()).is_declared());
    assert!(Outcome::Canceled.is_declared());
    assert_eq!(Outcome::Canceled.as_str(), "canceled");
}

// The asymmetry: an unknown phase is refused, an unknown outcome is kept.
#[test]
fn unknown_phase_is_refused_and_unknown_outcome_is_kept() {
    assert!(
        decode_run_state(br#"{"schemaVersion":1,"runId":"run-a","phase":"paused"}"#, "run-a").is_err(),
        "a phase this half cannot act on has no safe default"
    );
    let state = decode_run_state(
        br#"{"schemaVersion":1,"runId":"run-a","phase":"finished","outcome":"eaten_by_a_bear"}"#,
        "run-a",
    )
    .expect("an undeclared outcome must be kept and reported");
    let outcome = state.outcome.expect("the undeclared outcome was not carried through");
    assert_eq!(outcome, Outcome::Unknown("eaten_by_a_bear".to_owned()));
    assert!(!outcome.is_declared(), "keeping an outcome is not the same as knowing it");
}

#[test]
fn writing_an_undeclared_outcome_is_refused() {
    let invented = Outcome::Unknown("eaten_by_a_bear".to_owned());
    assert_eq!(
        validate_outcome_to_write(&invented).unwrap_err().to_string(),
        "eaten_by_a_bear is not one of the declared run outcomes"
    );
    for outcome in &Outcome::ALL {
        assert_eq!(validate_outcome_to_write(outcome), Ok(()), "{outcome} was refused");
    }
}

// Adding a field is how this wire grows compatibly, and an active run is republished verbatim.
#[test]
fn unknown_fields_cross_untouched() {
    decode_run_state(
        br#"{"schemaVersion":1,"runId":"run-a","phase":"running","somethingNew":{"a":1}}"#,
        "run-a",
    )
    .expect("an unknown field was refused");
}

#[test]
fn schema_and_identity_are_refused() {
    let other_schema = decode_run_state(br#"{"schemaVersion":2,"runId":"run-a","phase":"running"}"#, "run-a");
    assert!(
        matches!(other_schema, Err(RunStateError::UnsupportedSchema { .. })),
        "{other_schema:?}"
    );
    let other_run = decode_run_state(br#"{"schemaVersion":1,"runId":"run-b","phase":"running"}"#, "run-a");
    assert!(matches!(other_run, Err(RunStateError::InvalidIdentity { .. })), "{other_run:?}");
}

// A live run must *omit* outcome and exitCode; a terminal one must carry signal and nativeExitCode as explicit
// nulls. The host asserts on both directions.
#[test]
fn live_run_omits_outcome_and_terminal_run_nulls_signal() {
    let live = fields(&RunState::new("run-a", Phase::Running));
    for absent in ["outcome", "exitCode", "signal", "nativeExitCode"] {
        assert!(!live.contains_key(absent), "a live run sent {absent}");
    }

    let mut finished = RunState::new("run-a", Phase::Finished);
    finished.outcome = Some(Outcome::Canceled);
    finished.exit_code = Some(143);
    finished.native_exit_code = Some(None);
    finished.signal = Some(None);
    let finished = fields(&finished);
    for nulled in ["signal", "nativeExitCode"] {
        assert_eq!(finished.get(nulled), Some(&Value::Null), "{nulled} is not an explicit null");
    }
}

// The agent writes the terminal nulls itself, and a finished state reads back with them.
#[test]
fn a_finished_state_read_back_keeps_its_explicit_nulls() {
    let written = br#"{"schemaVersion":1,"runId":"run-a","snapshotId":null,"phase":"finished","outcome":"succeeded","exitCode":0,"nativeExitCode":0,"signal":null}"#;
    let state = decode_run_state(written, "run-a").unwrap();
    assert_eq!(state.native_exit_code, Some(Some(0)));
    assert_eq!(state.signal, Some(None));
    assert_eq!(serde_json::to_vec(&state).unwrap(), written.to_vec());
}

// A free slot is `{"active": null}` and not an absent key; a missing key is the protocol failure.
#[test]
fn free_slot_is_an_explicit_null() {
    assert_eq!(serde_json::to_string(&ActiveReply { active: None }).unwrap(), r#"{"active":null}"#);
    assert_eq!(
        serde_json::from_str::<ActiveReply>(r#"{"active":null}"#).unwrap(),
        ActiveReply { active: None }
    );
    assert!(
        serde_json::from_str::<ActiveReply>("{}").is_err(),
        "a reply with no active key was read as a free slot"
    );
}

#[test]
fn run_id_pattern_refuses_what_the_host_refuses() {
    for accepted in ["run-a", "run-ui-daemon-0d8e", "run-A-1"] {
        assert!(is_run_id(accepted), "{accepted:?} was refused");
    }
    for refused in ["", "run-", "ui-run", "run-a/b", "run-a.b", "run-a b", " run-a", "run-a\n", "run-é"] {
        assert!(!is_run_id(refused), "{refused:?} was accepted");
    }
    for accepted in ["snapshot-a", "snapshot-1.2_3-x"] {
        assert!(is_snapshot_id(accepted), "{accepted:?} was refused");
    }
    for refused in ["snapshot-", "snapshot-a/b", "snapshot-a b", "run-a"] {
        assert!(!is_snapshot_id(refused), "{refused:?} was accepted");
    }
}

proptest! {
    /// The checks are the published patterns: a host that compiles [RUN_ID_PATTERN_TEXT] or
    /// [SNAPSHOT_ID_PATTERN_TEXT] accepts exactly the ids the agent accepts.
    #[test]
    fn the_id_checks_are_the_published_patterns(id in "(run-|snapshot-|run|x)?[a-zA-Z0-9._/ é\n-]{0,6}") {
        let run = regex::Regex::new(RUN_ID_PATTERN_TEXT).unwrap();
        let snapshot = regex::Regex::new(SNAPSHOT_ID_PATTERN_TEXT).unwrap();
        prop_assert_eq!(is_run_id(&id), run.is_match(&id), "{:?}", id);
        prop_assert_eq!(is_snapshot_id(&id), snapshot.is_match(&id), "{:?}", id);
    }
}

// The contract's digest is text, so no caller can widen it to a float: a digest that rounds compares equal to
// bytes it does not describe, which on this wire means "the installed agent is already right".
#[test]
fn self_digest_stays_text() {
    let digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    let encoded = serde_json::to_string(&Contract::current(digest)).unwrap();
    assert!(encoded.contains(&format!(r#""selfDigest":"{digest}""#)), "{encoded}");
    assert!(encoded.contains(r#""runIdPattern":"^run-[a-zA-Z0-9-]+$""#), "{encoded}");
}

// The statuses, as literals, asserted as a whole set: the host reads a 64 as an agent older than its controller
// only while every in-agent failure answers 70.
#[test]
fn the_agents_exit_statuses_are_a_closed_set() {
    let codes: Vec<i32> = AgentExit::ALL.iter().map(|status| status.code()).collect();
    assert_eq!(codes, [1, 64, 70, 73]);
    assert_eq!(
        codes.iter().collect::<BTreeSet<_>>().len(),
        codes.len(),
        "a status is declared twice"
    );
    for status in AgentExit::ALL {
        assert_eq!(AgentExit::from_code(status.code()), Some(status));
    }
    assert_eq!(AgentExit::from_code(0), None);
    assert_eq!(AgentExit::from_code(2), None);
}

// The envelope the agent writes decodes, both ways it can arrive.
#[test]
fn envelopes_read_what_the_agent_writes() {
    let success: Envelope<ActiveReply> =
        serde_json::from_str(r#"{"schemaVersion":1,"ok":true,"command":"active","data":{"active":null}}"#).unwrap();
    assert_eq!(success.command, "active");
    assert_eq!(success.into_result().unwrap(), ActiveReply { active: None });

    let failure: Envelope<Value> = serde_json::from_str(
        r#"{"schemaVersion":1,"ok":false,"command":"start","error":{"code":"usage","message":"no such verb","details":null}}"#,
    )
    .unwrap();
    let error = failure.into_result().unwrap_err();
    assert_eq!((error.code.as_str(), error.message.as_str()), ("usage", "no such verb"));

    let written = serde_json::to_string(&Envelope::success(
        "log",
        LogReply {
            run_id: "run-a".to_owned(),
            log_path: "/l".to_owned(),
            content: "x".to_owned(),
            truncated: false,
        },
    ))
    .unwrap();
    assert_eq!(
        written,
        r#"{"schemaVersion":1,"ok":true,"command":"log","data":{"runId":"run-a","logPath":"/l","content":"x","truncated":false}}"#
    );
}

// The controller reads the agent's envelope through its own lenient reader. Both are declared here, and a field
// renamed in one of them fails this rather than a worker.
#[test]
fn the_controller_reads_every_envelope_the_agent_writes() {
    let written = serde_json::to_string(&Envelope::success(AgentVerb::Active.as_str(), ActiveReply { active: None })).unwrap();
    let received = ReceivedEnvelope::read(&written).unwrap();
    assert_eq!(
        (received.schema_version, received.ok, received.command.as_str()),
        (i64::from(SCHEMA_VERSION), true, "active")
    );
    assert_eq!(received.data.unwrap().get(), r#"{"active":null}"#);
    assert!(received.error.is_none());

    let written = serde_json::to_string(&Envelope::<()>::failure(
        AgentVerb::Stage.as_str(),
        EnvelopeError {
            code: USAGE_CODE.to_owned(),
            message: "no such verb".to_owned(),
            details: Value::Null,
        },
    ))
    .unwrap();
    let received = ReceivedEnvelope::read(&written).unwrap();
    assert_eq!((received.ok, received.command.as_str()), (false, "stage"));
    let error = received.error.unwrap();
    assert_eq!((error.code.as_str(), error.message.as_str()), (USAGE_CODE, "no such verb"));
    assert!(received.data.is_none());
}

// What the agent leaves out, or writes as `null`, reads as the default; a bare `null` is an envelope that said
// nothing.
#[test]
fn the_received_envelope_reads_nulls_as_what_was_left_out() {
    let received = ReceivedEnvelope::read(r#"{"schemaVersion":null,"ok":null,"command":null,"error":{"code":null}}"#).unwrap();
    assert_eq!((received.schema_version, received.ok, received.command.as_str()), (0, false, ""));
    assert_eq!(received.error.unwrap().code, "");
    let nothing = ReceivedEnvelope::read("null").unwrap();
    assert!(nothing.data.is_none() && nothing.error.is_none());
    ReceivedEnvelope::read("{").unwrap_err();
}

#[test]
fn every_verb_parses_back() {
    for command in Command::ALL {
        assert_eq!(command.as_str().parse::<Command>(), Ok(*command));
    }
    "contract".parse::<Command>().unwrap_err();
}
