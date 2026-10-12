use pretty_assertions::assert_eq;
use proptest::prelude::*;

use super::*;

fn decode(line: &str) -> RunEvent {
    decode_run_event(line.as_bytes()).unwrap_or_else(|error| panic!("{line} was refused: {error}"))
}

fn protocol_code(line: &str) -> &'static str {
    match decode_run_event(line.as_bytes()) {
        Err(DecodeError::Protocol(failure)) => failure.code,
        other => panic!("{line} was not refused as a protocol break: {other:?}"),
    }
}

// A controller that holds no record recognises a daemon run by its prefix alone, and nothing else carries it.
#[test]
fn a_daemon_run_is_recognised_by_its_prefix() {
    assert!(is_daemon_run(&run_id("u")));
    assert!(!is_daemon_run("run-flow-1"));
    assert!(!is_daemon_run("run-ui-"));
}

#[test]
fn routes_are_matched_the_way_the_daemon_routes_them() {
    let digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    for (method, path, want) in [
        ("GET", "/status", Some(Route::Status)),
        ("POST", "/jars", Some(Route::MissingJars)),
        ("POST", "/run", Some(Route::Run)),
        ("POST", "/mount/quiesce", Some(Route::MountQuiesce)),
        ("POST", "/mount/resume", Some(Route::MountResume)),
        ("POST", "/ide/stop", Some(Route::IdeStop)),
        ("POST", "/shutdown", Some(Route::Shutdown)),
        ("PUT", &format!("/jars/{digest}"), Some(Route::UploadJar)),
        ("GET", "/results/iter-1/test.xml", Some(Route::Results)),
        // A route matched by path alone would answer the wrong handler for the wrong verb.
        ("GET", "/run", None),
        ("POST", "/status", None),
        // Not a sha256, so not a jar upload: a segment that is merely 64 characters is not a digest.
        ("PUT", "/jars/nope", None),
        (
            "PUT",
            "/jars/0123456789ABCDEF0123456789abcdef0123456789abcdef0123456789abcdef",
            None,
        ),
        // Traversal in the iteration segment must not reach the results handler.
        ("GET", "/results/a/b/test.xml", None),
        ("GET", "/results//test.xml", None),
    ] {
        assert_eq!(match_route(method, path), want, "{method} {path}");
    }
    // The builders answer paths the matcher routes back to them.
    for endpoint in [upload_jar(digest), iteration_result("iter-1")] {
        assert_eq!(match_route(endpoint.method.as_str(), &endpoint.path), Some(endpoint.route));
    }
}

#[test]
fn only_the_run_route_declares_no_budget() {
    for endpoint in &FIXED_ENDPOINTS {
        if endpoint.route == Route::Run {
            assert_eq!(endpoint.timeout, None, "/run's deadline is the daemon's streamed watchdog");
        } else {
            assert!(
                endpoint.timeout.is_some_and(|timeout| !timeout.is_zero()),
                "{} declares no budget",
                endpoint.path
            );
        }
    }
}

// The run id names the same run on both channels, so a daemon run id is a supervisor run id.
#[test]
fn a_daemon_run_id_is_a_supervisor_run_id() {
    assert!(
        crate::supervisor::is_run_id(&run_id("0d8e1f")),
        "{} is not a supervisor run id",
        run_id("0d8e1f")
    );
}

#[test]
fn an_unknown_record_is_refused() {
    for line in [
        r#"{"event":"testAborted","timestamp":"t","displayName":"d","id":"i"}"#,
        r#"{"timestamp":"t"}"#,
        r#"{"event":42}"#,
    ] {
        assert_eq!(protocol_code(line), CODE_UNKNOWN_EVENT, "{line}");
    }
}

#[test]
fn an_unknown_field_is_kept_and_the_bytes_survive() {
    let line = r#"{"event":"testStarted","timestamp":"t","displayName":"d","id":"i","aFieldFromTheFuture":{"x":1}}"#;
    let event = decode(line);
    assert_eq!(
        event.raw.as_ref().map(|raw| raw.get()),
        Some(line),
        "the record was rebuilt rather than kept"
    );
    assert_eq!(event.to_raw().get(), line);
}

#[test]
fn a_declared_field_of_the_wrong_kind_is_refused() {
    for line in [
        r#"{"event":"testFinished","timestamp":"t","displayName":"d","id":"i","status":"PASSED","durationMs":"12"}"#,
        r#"{"event":"testStarted","timestamp":"t","displayName":"d","id":1}"#,
        r#"{"event":"summary","iterationId":"i","daemonBootStamp":"b","startedAt":"s","testsStarted":0,"testsFailed":0,"testsSkipped":0,"containersSkipped":0,"containerFailures":0,"hasFailures":"no","ideRunning":true}"#,
        // An optional field that is present must still be of its kind, and an explicit null is present.
        r#"{"event":"testStarted","timestamp":"t","displayName":"d","id":"i","className":null}"#,
        r#"{"event":"testFinished","timestamp":"t","displayName":"d","id":"i","status":"FAILED","durationMs":1,"error":null}"#,
    ] {
        assert_eq!(protocol_code(line), CODE_FIELD_MISSING, "{line}");
    }
}

// The counter trap, on the record that decides a verdict: `nextDeadlineInMs` absent means no next deadline,
// zero means it expires now.
#[test]
fn an_absent_deadline_is_not_zero() {
    let base = r#"{"event":"watchdogState","timestamp":"t","phase":"p","activeExecutionTimeoutMs":1,"progressGapTimeoutMs":2"#;
    let deadline = |line: String| match decode(&line).kind {
        RunEventKind::WatchdogState(state) => state.next_deadline_in_ms,
        other => panic!("read {other:?}"),
    };
    assert_eq!(deadline(format!("{base}}}")), None);
    assert_eq!(deadline(format!(r#"{base},"nextDeadlineInMs":0}}"#)), Some(0));
}

// The same trap one level up: a stream that ended early has no summary.
#[test]
fn a_stream_without_a_summary_has_no_summary() {
    let started = decode(r#"{"event":"runStarted","iterationId":"i","daemonBootStamp":"b","startedAt":"s"}"#);
    assert_eq!(find_summary(std::slice::from_ref(&started)), None);
    let summary = decode(
        r#"{"event":"summary","iterationId":"i","daemonBootStamp":"b","startedAt":"s","testsStarted":3,"testsFailed":1,"testsSkipped":0,"containersSkipped":0,"containerFailures":0,"hasFailures":true,"ideRunning":true}"#,
    );
    let events = [started, summary];
    let found = find_summary(&events).expect("the summary was not found");
    assert_eq!((found.tests_started, found.tests_failed, found.has_failures), (3, 1, true));
}

#[test]
fn a_watchdog_expiry_keeps_its_detail_and_evidence() {
    let event = decode(
        r#"{"event":"watchdogExpired","reason":"progress-gap","deadline":"d","expiredAt":"e","detail":"no record for 300s","evidence":["/tmp/a.png","/tmp/b.txt"],"activeExecution":{"id":"i","displayName":"n","className":"C","methodName":"m"}}"#,
    );
    let RunEventKind::WatchdogExpired(expired) = event.kind else {
        panic!("read {:?}", event.kind)
    };
    assert_eq!(expired.detail.as_deref(), Some("no record for 300s"));
    assert_eq!(expired.evidence, ["/tmp/a.png", "/tmp/b.txt"]);
    let active = expired.active_execution.expect("the active execution was dropped");
    assert_eq!(active.class_name.as_deref(), Some("C"));
    assert_eq!(active.describe(), "C#m[n]");
}

#[test]
fn an_execution_identity_must_be_one() {
    for identity in [
        r#"{"id":"i"}"#,
        r#"{"displayName":"n"}"#,
        r#"{"id":1,"displayName":"n"}"#,
        r#"{"id":"i","displayName":"n","className":7}"#,
        r#"{"id":"i","displayName":"n","methodName":null}"#,
        r#""i""#,
        "[]",
    ] {
        let line = format!(
            r#"{{"event":"watchdogState","timestamp":"t","phase":"p","activeExecutionTimeoutMs":1,"progressGapTimeoutMs":2,"activeExecution":{identity}}}"#
        );
        assert_eq!(
            protocol_code(&line),
            CODE_FIELD_MISSING,
            "{identity} was accepted as an execution identity"
        );
    }
}

#[test]
fn the_state_file_is_refused_rather_than_treated_as_unwritten() {
    let missing_stamp = decode_state_file(br#"{"protocolVersion":2,"port":1,"token":"t","pid":9}"#);
    assert_eq!(missing_stamp.unwrap_err().code, CODE_STATE_FILE_UNREADABLE);
    let state = decode_state_file(br#"{"protocolVersion":2,"port":1,"token":"t","pid":9,"daemonBootStamp":"b"}"#).unwrap();
    assert_eq!((state.token.as_str(), state.port), ("t", 1));
    let failure = decode_state_file(br#"{"protocolVersion":2}"#).unwrap_err();
    assert_eq!(failure.code, CODE_STATE_FILE_UNREADABLE);
    assert!(failure.message.contains("the daemon's state file"), "{}", failure.message);
    assert!(failure.message.contains("`daemon log`"), "{}", failure.message);
}

#[test]
fn an_unknown_conflict_reason_is_data() {
    let conflict = decode_conflict(br#"{"reason":"a-refusal-this-controller-predates"}"#).expect("an unknown conflict reason was refused");
    assert_eq!(
        conflict,
        Conflict {
            reason: "a-refusal-this-controller-predates".to_owned(),
            launch_digest: None
        }
    );
    assert!(decode_conflict(b"{}").is_err(), "a 409 with no reason was accepted");
    decode_conflict(br#"{"reason":"stale-daemon","launchDigest":null}"#).unwrap_err();
    let stale = decode_conflict(br#"{"reason":"stale-daemon","launchDigest":"d"}"#).unwrap();
    assert_eq!(
        (stale.reason.as_str(), stale.launch_digest.as_deref()),
        (conflict::STALE_DAEMON, Some("d"))
    );
}

// The metaspace figure passes a billion routinely, so it must not be carried by anything a float would round.
#[test]
fn status_counters_keep_every_digit() {
    let status = decode_status(
        br#"{"protocolVersion":2,"daemonBootStamp":"b","controllerLaunchDigest":"d","mountQuiesced":false,"productStamp":"p","ideRunning":true,"busy":false,"iterationCount":7,"metaspaceUsedBytes":9007199254740993,"pid":42}"#,
    )
    .unwrap();
    assert_eq!(status.metaspace_used_bytes, 9_007_199_254_740_993);
    assert_eq!(decode_missing_jars(br#"{"missing":["a"]}"#).unwrap().missing, ["a"]);
}

#[test]
fn the_stream_keeps_non_json_lines_as_output() {
    let decoded = NdjsonDecoder::default().push(b"Picked up JAVA_TOOL_OPTIONS\n[1,2]\n\"quoted\"\n{\"broken\n");
    assert_eq!(decoded.failure, None, "a non-record line was reported as a protocol break");
    let texts: Vec<_> = decoded
        .events
        .iter()
        .map(|event| match &event.kind {
            RunEventKind::Output(output) if event.raw.is_none() => output.text.as_str(),
            other => panic!("a non-record line became {other:?}"),
        })
        .collect();
    assert_eq!(texts, ["Picked up JAVA_TOOL_OPTIONS", "[1,2]", "\"quoted\"", "{\"broken"]);
}

#[test]
fn the_stream_reassembles_a_record_split_across_chunks() {
    let mut decoder = NdjsonDecoder::default();
    let first = decoder.push(br#"{"event":"testStarted","timestamp":"t","#);
    assert!(
        first.events.is_empty() && first.failure.is_none(),
        "a partial line produced {first:?}"
    );
    // The split falls inside the two bytes of `é`.
    let second = decoder.push(b"\"displayName\":\"d\xc3");
    assert!(
        second.events.is_empty() && second.failure.is_none(),
        "a partial line produced {second:?}"
    );
    let third = decoder.push(b"\xa9\",\"id\":\"i\"}\n");
    assert_eq!(third.failure, None);
    let [event] = third.events.as_slice() else {
        panic!("read {:?}", third.events)
    };
    let RunEventKind::TestStarted(started) = &event.kind else {
        panic!("read {:?}", event.kind)
    };
    assert_eq!(
        (started.execution.id.as_str(), started.execution.display_name.as_str()),
        ("i", "dé")
    );
}

// A renamed record is reported, not filed under stdout: the second would leave a run looking merely quiet.
// The records before the break are still the run's.
#[test]
fn the_stream_reports_a_renamed_record() {
    let decoded = NdjsonDecoder::default().push(
        concat!(
            r#"{"event":"runStarted","iterationId":"i","daemonBootStamp":"b","startedAt":"s"}"#,
            "\n",
            r#"{"event":"testBegun","timestamp":"t"}"#,
            "\n"
        )
        .as_bytes(),
    );
    assert_eq!(decoded.failure.expect("a renamed record was accepted").code, CODE_UNKNOWN_EVENT);
    assert_eq!(decoded.events.iter().map(RunEvent::event).collect::<Vec<_>>(), [Event::RunStarted]);
}

// A count says how much coverage a killed run lost, a name says what; a plan with no count is refused rather
// than read as zero.
#[test]
fn the_plan_record_carries_the_discovered_classes() {
    let event = decode(r#"{"event":"planStarted","timestamp":"t","discoveredExecutions":2,"classNames":["air.AlphaTest","air.BetaTest"]}"#);
    let RunEventKind::PlanStarted(plan) = event.kind else {
        panic!("read {:?}", event.kind)
    };
    assert_eq!(plan.discovered_executions, 2);
    assert_eq!(plan.class_names, ["air.AlphaTest", "air.BetaTest"]);
    assert_eq!(
        protocol_code(r#"{"event":"planStarted","timestamp":"t","classNames":[]}"#),
        CODE_FIELD_MISSING
    );
}

#[test]
fn protocol_version_is_the_one_kotlin_declares() {
    // A literal, so bumping the constant without the Kotlin side is a deliberate two-file change.
    assert_eq!(PROTOCOL_VERSION, 3, "`AIR_UI_DAEMON_PROTOCOL_VERSION` must move with it");
}

// A dynamic node carries its factory's method and its own display name, so a progress line names both; a plain
// test method, whose display name only restates the method, is named once.
#[test]
fn a_progress_line_names_the_dynamic_node_beside_its_factory() {
    let class = Some("com.example.GeneratedFlowTest");
    let factory = Some("generatedFlowScenarios");
    for (what, class, display, method, want) in [
        (
            "a dynamic node",
            class,
            "add-empty-context-status",
            factory,
            "com.example.GeneratedFlowTest#generatedFlowScenarios[add-empty-context-status]",
        ),
        (
            "a plain method",
            class,
            "generatedFlowScenarios()",
            factory,
            "com.example.GeneratedFlowTest#generatedFlowScenarios",
        ),
        (
            "a method named plainly",
            class,
            "generatedFlowScenarios",
            factory,
            "com.example.GeneratedFlowTest#generatedFlowScenarios",
        ),
        (
            "a class container",
            class,
            "GeneratedFlowTest",
            None,
            "com.example.GeneratedFlowTest",
        ),
        ("no class at all", None, "engine", None, "engine"),
    ] {
        assert_eq!(describe_execution(class, display, method), want, "{what}");
    }
}

// A synthesized record serializes to the shape the daemon would have written, so a fake daemon and a
// republished record agree.
#[test]
fn a_synthesized_record_serializes_to_the_daemons_shape() {
    let event = RunEvent::synthesized(RunEventKind::TestSkipped(Skipped {
        timestamp: "t".to_owned(),
        execution: ExecutionIdentity {
            id: "i".to_owned(),
            display_name: "d".to_owned(),
            class_name: Some("C".to_owned()),
            method_name: None,
        },
        reason: "no CLI".to_owned(),
    }));
    let raw = event.to_raw();
    assert_eq!(
        raw.get(),
        r#"{"event":"testSkipped","timestamp":"t","id":"i","displayName":"d","className":"C","reason":"no CLI"}"#
    );
    assert_eq!(decode(raw.get()).kind, event.kind);
    assert_eq!(RunEvent::output("hi").to_raw().get(), r#"{"event":"output","text":"hi"}"#);
}

proptest! {
    // However a stream is cut into chunks, the decoder answers the same records.
    #[test]
    fn chunking_never_changes_the_records(cuts in proptest::collection::vec(0usize..400, 0..8)) {
        let stream = concat!(
            r#"{"event":"runStarted","iterationId":"i","daemonBootStamp":"b","startedAt":"s"}"#, "\n",
            "Picked up JAVA_TOOL_OPTIONS: -Dfile.encoding=UTF-8 \u{00e9}\u{1F600}\n",
            r#"{"event":"testStarted","timestamp":"t","displayName":"é","id":"i"}"#, "\r\n",
            "\n",
            r#"{"event":"output","text":"from the daemon"}"#, "\n",
        ).as_bytes();
        let whole = NdjsonDecoder::default().push(stream);
        let mut decoder = NdjsonDecoder::default();
        let mut pieces = Vec::new();
        let mut cuts: Vec<usize> = cuts.into_iter().map(|cut| cut % (stream.len() + 1)).collect();
        cuts.sort_unstable();
        let mut start = 0;
        for cut in cuts.into_iter().chain([stream.len()]) {
            let decoded = decoder.push(&stream[start..cut]);
            prop_assert!(decoded.failure.is_none());
            pieces.extend(decoded.events);
            start = cut;
        }
        prop_assert!(whole.failure.is_none());
        prop_assert_eq!(whole.events.len(), 4);
        prop_assert_eq!(pieces, whole.events);
    }
}
