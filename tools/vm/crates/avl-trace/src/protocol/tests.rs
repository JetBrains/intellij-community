use std::collections::{BTreeMap, BTreeSet};

use avl_testkit::traces::{self, TRANSCRIPT, TRUNCATED_TRANSCRIPT, lines};
use pretty_assertions::assert_eq;
use proptest::prelude::*;
use serde::Deserialize;

use super::*;
use crate::testdata::decode_transcript;

/// The golden transcript is the contract the sidecar is compared with byte for byte, so it has to be in the
/// canonical form, or the comparison would be with a spelling no encoder produces.
#[test]
fn every_transcript_line_decodes_and_is_canonical() {
    for name in [TRANSCRIPT, TRUNCATED_TRANSCRIPT] {
        for (index, line) in lines(&traces::path(name)).iter().enumerate() {
            let command = decode_command(line).unwrap_or_else(|error| panic!("{name} line {}: {error}", index + 1));
            let encoded = encode_command(&command).unwrap_or_else(|error| panic!("{name} line {}: {error}", index + 1));
            assert_eq!(
                String::from_utf8_lossy(&encoded),
                String::from_utf8_lossy(line),
                "{name} line {} is not canonical",
                index + 1
            );
        }
    }
}

/// Replays a transcript through the rules a recorder enforces, and fails on the first line that breaks one. It is
/// a model of the contract, not the recorder: its only job is to prove that the golden transcript is a session a
/// correct lane could have sent.
#[derive(Default)]
struct Session {
    open: BTreeMap<u32, SpanCommand>,
    stack: Vec<u32>,
    next_id: u32,
    done: bool,
    scenario: Option<ScenarioCommand>,
}

impl Session {
    fn replay(commands: &[Command]) -> Self {
        let mut state = Self {
            next_id: 1,
            ..Self::default()
        };
        for (index, command) in commands.iter().enumerate() {
            let line = index + 1;
            if index == 0 {
                assert_eq!(command.op(), Op::Hello, "a session opens with hello");
                continue;
            }
            assert_ne!(command.op(), Op::Hello, "line {line} is a second hello");
            assert!(!state.done, "line {line} follows done");
            assert!(
                state.scenario.is_some() || command.op() == Op::Scenario,
                "line {line} is {} before any scenario",
                command.op()
            );
            state.apply(line, command);
        }
        state
    }

    fn require_open(&self, line: usize, id: u32) {
        assert!(
            id == 0 || self.open.contains_key(&id),
            "line {line} names the span {id}, which is not open"
        );
    }

    fn apply(&mut self, line: usize, command: &Command) {
        match command {
            Command::Scenario(scenario) => self.scenario = Some(scenario.clone()),
            Command::Span(span) => {
                assert_eq!(span.id, self.next_id, "line {line} opens the span {} out of order", span.id);
                self.next_id += 1;
                self.require_open(line, span.parent);
                let innermost = self.stack.last().copied().unwrap_or(0);
                assert_eq!(
                    span.parent, innermost,
                    "line {line} opens the span {} under a span that is not innermost",
                    span.id
                );
                if span.kind == SpanKind::Assertion {
                    assert!(span.expectation.is_some(), "line {line} opens an assertion without its expectation");
                }
                self.open.insert(span.id, span.clone());
                self.stack.push(span.id);
            }
            Command::End(end) => {
                assert_eq!(
                    self.stack.last(),
                    Some(&end.id),
                    "line {line} ends a span that is not the innermost open one"
                );
                self.stack.pop();
                self.open.remove(&end.id);
            }
            Command::Snap(snap) => self.require_open(line, snap.span),
            Command::Call(call) => self.require_open(line, call.span),
            Command::Done(_) => {
                assert!(
                    self.stack.is_empty(),
                    "line {line} is done with the spans {:?} still open",
                    self.stack
                );
                self.done = true;
            }
            Command::Hello(_) | Command::Restart | Command::DriverSteps(_) => {}
        }
    }
}

#[test]
fn the_transcript_is_one_well_formed_scenario() {
    let commands = decode_transcript(TRANSCRIPT);
    let state = Session::replay(&commands);
    assert!(state.done, "the transcript never sends done");
    assert_eq!(commands[1].op(), Op::Scenario);
    assert_eq!(commands.last().map(Command::op), Some(Op::Done));

    // Every kind a generated profile uses appears, so the viewer is built against all of them.
    let mut used = BTreeMap::<SpanKind, usize>::new();
    for command in &commands {
        if let Command::Span(span) = command {
            *used.entry(span.kind).or_default() += 1;
        }
    }
    for kind in [
        SpanKind::Reset,
        SpanKind::Setup,
        SpanKind::Step,
        SpanKind::Operation,
        SpanKind::Action,
        SpanKind::Assertion,
    ] {
        assert!(used.contains_key(&kind), "the transcript opens no {kind} span");
    }
    assert_eq!(used[&SpanKind::Reset], 2, "a scenario sits between two resets");
}

/// The snapshot rules the plan states: a boundary as every instruction starts, a check as every assertion holds,
/// and a closing boundary as every operation ends.
#[test]
fn the_transcript_snaps_at_every_boundary_and_check() {
    let commands = decode_transcript(TRANSCRIPT);
    let mut spans = BTreeMap::new();
    let snap_of = |command: Option<&Command>| match command {
        Some(Command::Snap(snap)) => Some((snap.span, snap.phase)),
        _ => None,
    };
    for (index, command) in commands.iter().enumerate() {
        match command {
            Command::Span(span) => {
                spans.insert(span.id, span.clone());
                if matches!(span.kind, SpanKind::Action | SpanKind::Assertion) {
                    assert_eq!(
                        snap_of(commands.get(index + 1)),
                        Some((span.id, SnapshotPhase::Boundary)),
                        "the {} {} is not followed by its boundary snapshot",
                        span.kind,
                        span.key
                    );
                }
            }
            Command::End(end) => {
                let opened = &spans[&end.id];
                let previous = snap_of(commands.get(index - 1));
                match opened.kind {
                    SpanKind::Assertion => assert_eq!(
                        previous,
                        Some((end.id, SnapshotPhase::Check)),
                        "the assertion {} ends without its check snapshot",
                        opened.key
                    ),
                    SpanKind::Operation => assert_eq!(
                        previous,
                        Some((end.id, SnapshotPhase::Boundary)),
                        "the operation {} ends without its closing boundary",
                        opened.key
                    ),
                    _ => {}
                }
            }
            _ => {}
        }
    }
}

/// The program is the profile verbatim, which is what lets the viewer grey out the instructions a failure never
/// reached: every instruction of the program has a span in a complete run.
#[test]
fn the_transcript_program_is_the_profile_it_ran() {
    #[derive(Deserialize)]
    struct Program {
        name: String,
        fixture: String,
        flow: String,
        setups: Vec<Setup>,
        steps: Vec<Step>,
    }
    #[derive(Deserialize)]
    struct Setup {
        operation: String,
    }
    #[derive(Deserialize)]
    struct Step {
        step: String,
        operations: Vec<Operation>,
    }
    #[derive(Deserialize)]
    struct Operation {
        instructions: Vec<Instruction>,
    }
    #[derive(Deserialize)]
    struct Instruction {
        action: Option<String>,
        assertion: Option<String>,
    }

    let commands = decode_transcript(TRANSCRIPT);
    let Command::Scenario(scenario) = &commands[1] else {
        panic!("the second line is not the scenario");
    };
    let program: Program =
        serde_json::from_str(scenario.program.as_ref().expect("the scenario has a program").get()).expect("the program is a profile");
    assert_eq!(program.name, scenario.name);
    assert_eq!(Some(&program.fixture), scenario.fixture.as_ref());
    assert_eq!(Some(&program.flow), scenario.flow.as_ref());
    let keys: BTreeSet<&str> = commands
        .iter()
        .filter_map(|command| match command {
            Command::Span(span) => Some(span.key.as_str()),
            _ => None,
        })
        .collect();
    for setup in &program.setups {
        assert!(
            keys.contains(span_key(SpanKind::Setup, &setup.operation).as_str()),
            "the setup {} has no span",
            setup.operation
        );
    }
    for step in &program.steps {
        assert!(
            keys.contains(span_key(SpanKind::Step, &step.step).as_str()),
            "the step {} has no span",
            step.step
        );
        for instruction in step.operations.iter().flat_map(|operation| &operation.instructions) {
            let key = match (&instruction.assertion, &instruction.action) {
                (Some(assertion), _) => span_key(SpanKind::Assertion, assertion),
                (None, Some(action)) => span_key(SpanKind::Action, action),
                (None, None) => panic!("an instruction is neither an action nor an assertion"),
            };
            assert!(keys.contains(key.as_str()), "the instruction {key} has no span");
        }
    }
}

/// The truncated transcript is the shape a watchdog's `exitProcess` leaves: the full session's first lines, then
/// EOF with an assertion running.
#[test]
fn the_truncated_transcript_stops_inside_an_assertion() {
    let full = lines(&traces::path(TRANSCRIPT));
    let truncated = lines(&traces::path(TRUNCATED_TRANSCRIPT));
    assert!(truncated.len() < full.len());
    assert_eq!(
        truncated[..],
        full[..truncated.len()],
        "the truncated transcript is not a prefix of the full one"
    );
    let state = Session::replay(&decode_transcript(TRUNCATED_TRANSCRIPT));
    assert!(!state.done, "the truncated transcript sends done");
    let running = state
        .stack
        .last()
        .map(|id| &state.open[id])
        .expect("the truncated transcript leaves a span open");
    assert_eq!(running.kind, SpanKind::Assertion);
}

#[test]
fn decode_command_refuses_what_the_contract_does_not_name() {
    let refused = [
        ("not an object", r"[1]"),
        ("no op", r#"{"root":"/r","runId":"r","launcher":"daemon"}"#),
        ("an op that is no string", r#"{"op":1}"#),
        ("an unknown op", r#"{"op":"pause"}"#),
        (
            "an unknown field",
            r#"{"op":"hello","root":"/r","runId":"r","launcher":"daemon","display":":88"}"#,
        ),
        (
            "an unknown nested field",
            r#"{"op":"hello","root":"/r","runId":"r","launcher":"daemon","bridge":{"port":1,"token":"t","host":"h"}}"#,
        ),
        (
            "a field of the wrong kind",
            r#"{"op":"span","id":"1","kind":"step","key":"step:a","title":"[flow] a"}"#,
        ),
        (
            "an unknown kind",
            r#"{"op":"span","id":1,"kind":"widget","key":"widget:a","title":"a"}"#,
        ),
        (
            "an unknown launcher",
            r#"{"op":"hello","root":"/r","runId":"r","launcher":"gradle"}"#,
        ),
        ("an unknown phase", r#"{"op":"snap","span":0,"phase":"idle"}"#),
        ("an unknown status", r#"{"op":"done","status":"green"}"#),
        (
            "an unknown step status",
            r#"{"op":"driverSteps","steps":[{"name":"a","start":1,"stop":2,"status":"ok"}]}"#,
        ),
        (
            "a parent opened later",
            r#"{"op":"span","id":2,"parent":2,"kind":"step","key":"step:a","title":"[flow] a"}"#,
        ),
        (
            "an expectation on an action",
            r#"{"op":"span","id":1,"kind":"action","key":"action:a","title":"a","expectation":"x"}"#,
        ),
        ("a failure without a why", r#"{"op":"end","id":3,"status":"failed"}"#),
        (
            "a pass with an error",
            r#"{"op":"end","id":3,"status":"passed","error":{"type":"java.lang.AssertionError"}}"#,
        ),
        ("an end of the root", r#"{"op":"end","id":0,"status":"passed"}"#),
        (
            "a call without a start",
            r#"{"op":"call","span":0,"request":"Ping","startMs":0,"durationMs":1,"ok":true}"#,
        ),
        ("no driver step", r#"{"op":"driverSteps","steps":[]}"#),
        (
            "a program that is no JSON object",
            r#"{"op":"scenario","name":"a","testClass":"C","lane":"UI","program":"x"}"#,
        ),
        (
            "a program that is null",
            r#"{"op":"scenario","name":"a","testClass":"C","lane":"UI","program":null}"#,
        ),
        (
            "a scenario's tokenless bridge",
            r#"{"op":"scenario","name":"a","testClass":"C","lane":"UI","bridge":{"port":1,"token":""}}"#,
        ),
        (
            "a screen that is no boolean",
            r#"{"op":"hello","root":"/r","runId":"r","launcher":"daemon","screen":"on"}"#,
        ),
        ("a negative span", r#"{"op":"snap","span":-1,"phase":"check"}"#),
        ("a field on restart", r#"{"op":"restart","ide":"now"}"#),
        (
            "trailing data",
            r#"{"op":"done","status":"passed"} {"op":"done","status":"passed"}"#,
        ),
    ];
    for (label, line) in refused {
        if let Ok(command) = decode_command(line.as_bytes()) {
            panic!("{label} was accepted as {command:?}");
        }
    }
}

#[test]
fn encode_command_puts_the_op_first_and_refuses_an_invalid_command() {
    assert_eq!(encode_command(&Command::Restart).unwrap(), br#"{"op":"restart"}"#);
    let snap = Command::Snap(SnapCommand {
        span: 3,
        phase: SnapshotPhase::Failure,
    });
    assert_eq!(encode_command(&snap).unwrap(), br#"{"op":"snap","span":3,"phase":"failure"}"#);
    let invalid = Command::Span(SpanCommand {
        id: 1,
        parent: 0,
        kind: SpanKind::Step,
        key: String::new(),
        title: "t".to_owned(),
        expectation: None,
    });
    assert!(encode_command(&invalid).is_err(), "an invalid span was encoded");

    // An expectation or a JVM frame is full of `<`, `>` and `&`, and the sidecar writes them raw.
    let failed = Command::End(EndCommand {
        id: 4,
        status: Status::Failed,
        error: Some(Failure {
            r#type: "java.lang.AssertionError".to_owned(),
            message: Some("expected <a> & <b>".to_owned()),
            stack: vec!["com.intellij.air.Flow.<init>(Flow.kt:1)".to_owned()],
        }),
    });
    assert_eq!(
        String::from_utf8(encode_command(&failed).unwrap()).unwrap(),
        r#"{"op":"end","id":4,"status":"failed","error":{"type":"java.lang.AssertionError","message":"expected <a> & <b>","stack":["com.intellij.air.Flow.<init>(Flow.kt:1)"]}}"#
    );
}

#[test]
fn only_the_four_waiting_ops_are_acked() {
    let acked: Vec<&str> = Op::ALL.iter().filter(|op| op.is_acked()).map(|op| op.as_str()).collect();
    assert_eq!(acked, ["hello", "scenario", "snap", "done"]);
}

#[test]
fn hello_acks_decode_and_refuse() {
    let hello = decode_hello_ack(br#"{"ok":true,"line":1,"capture":"ide-paint","video":"none","reason":"no DISPLAY"}"#).unwrap();
    assert_eq!(
        (hello.capture, hello.video, hello.line),
        (CaptureSource::IdePaint, VideoCodec::None, 1)
    );
    for line in [
        r#"{"ok":true,"line":1,"capture":"vnc","video":"none"}"#,
        r#"{"ok":true,"line":1,"capture":"x11","video":"av1"}"#,
        r#"{"ok":true,"line":0,"capture":"x11","video":"h264"}"#,
        r#"{"ok":true,"line":1,"capture":"x11","video":"h264","frames":3}"#,
    ] {
        assert!(decode_hello_ack(line.as_bytes()).is_err(), "the hello ack {line} was accepted");
    }
}

#[test]
fn every_vocabulary_declares_its_own_words_only() {
    fn refused<T: DeserializeOwned>(word: &str) -> bool {
        serde_json::from_str::<T>(&format!("{word:?}")).is_err()
    }
    assert!(refused::<Op>("pause"));
    assert!(refused::<SpanKind>("scenario"));
    assert!(refused::<SnapshotPhase>("idle"));
    assert!(refused::<Status>("skipped"));
    assert!(refused::<Launcher>("gradle"));
    assert!(refused::<CaptureSource>("vnc"));
    assert!(refused::<VideoCodec>("av1"));
    assert!(refused::<DriverStepStatus>("ok"));
    assert!(refused::<crate::bundle::BundleStatus>("running"));
    for op in Op::ALL {
        assert_eq!(Op::from_word(op.as_str()), Some(*op));
        assert_eq!(serde_json::to_string(op).unwrap(), format!("{:?}", op.as_str()));
    }
    // An Allure step without a status is accepted: an unfinished step has none.
    let unfinished = decode_command(br#"{"op":"driverSteps","steps":[{"name":"a","start":1,"stop":0}]}"#).unwrap();
    let Command::DriverSteps(driver) = unfinished else {
        panic!("not driver steps")
    };
    assert_eq!(driver.steps[0].status, None);
}

fn command_line() -> impl Strategy<Value = String> {
    let word = "[a-z<>& ]{1,8}";
    prop_oneof![
        (1u32..50, 0u32..50, word, word).prop_map(|(id, parent, key, title)| format!(
            r#"{{"op":"span","id":{id},"parent":{},"kind":"step","key":"{key}","title":"{title}"}}"#,
            parent % id
        )),
        (0u32..50, prop::sample::select(SnapshotPhase::ALL))
            .prop_map(|(span, phase)| format!(r#"{{"op":"snap","span":{span},"phase":"{phase}"}}"#)),
        (0u32..9, word, 1i64..i64::MAX, 0i64..10_000).prop_map(|(span, request, start, duration)| format!(
            r#"{{"op":"call","span":{span},"request":"{request}","startMs":{start},"durationMs":{duration},"ok":true}}"#
        )),
        (1u32..9, word).prop_map(|(id, message)| format!(
            r#"{{"op":"end","id":{id},"status":"failed","error":{{"type":"E","message":"{message}"}}}}"#
        )),
    ]
}

proptest! {
    #[test]
    fn decoding_never_panics(line in prop::collection::vec(any::<u8>(), 0..200)) {
        let _ = decode_command(&line);
    }

    #[test]
    fn a_canonical_line_survives_a_round_trip(line in command_line()) {
        let line = line.replace(r#","parent":0"#, "");
        let command = decode_command(line.as_bytes()).unwrap();
        prop_assert_eq!(String::from_utf8(encode_command(&command).unwrap()).unwrap(), line);
    }
}
