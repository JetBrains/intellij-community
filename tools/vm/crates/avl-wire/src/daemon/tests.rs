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
        ("POST", "/ide/stop", Some(Route::IdeStop)),
        ("POST", "/shutdown", Some(Route::Shutdown)),
        ("PUT", &format!("/jars/{digest}"), Some(Route::UploadJar)),
        ("GET", "/results/iter-1/test.xml", Some(Route::Results)),
        // Protocol 6 has no quiesce of the shares.
        ("POST", "/mount/quiesce", None),
        ("POST", "/mount/resume", None),
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
        r#"{"event":"testFinished","timestamp":"t","displayName":"d","id":"i","status":"FAILED","durationMs":1,"message":null}"#,
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

// The lane daemon sends the eight fields of protocol 6. A JVM daemon that a guest still holds from an older controller
// also sends the two fields that protocol 6 dropped. The reader ignores them, so the controller reads the protocol of
// that daemon and retires it.
#[test]
fn a_status_of_either_runner_decodes() {
    let lane_daemon = br#"{"protocolVersion":6,"daemonBootStamp":"b","controllerLaunchDigest":"d","productStamp":"p","ideRunning":true,"busy":false,"iterationCount":7,"pid":42}"#;
    let status = decode_status(lane_daemon).unwrap();
    assert_eq!(serde_json::to_vec(&status).unwrap(), lane_daemon);
    let jvm = decode_status(
        br#"{"protocolVersion":5,"daemonBootStamp":"b","controllerLaunchDigest":"d","mountQuiesced":false,"productStamp":"p","ideRunning":true,"busy":false,"iterationCount":7,"metaspaceUsedBytes":9007199254740993,"pid":42}"#,
    )
    .unwrap();
    assert_eq!((jvm.protocol_version, jvm.iteration_count, jvm.pid), (5, 7, 42));
    assert_eq!(decode_missing_jars(br#"{"missing":["a"]}"#).unwrap().missing, ["a"]);
}

// A field that a request does not use is absent from it. A run of the lane daemon names the lane and the digests of the
// lane inputs.
#[test]
fn the_run_request_names_the_lane_inputs_for_the_lane_daemon_only() {
    let mut request = RunRequest {
        selectors: Vec::new(),
        junit5_filters: vec!["include-tag=air-integration-ui".to_owned()],
        product_stamp: "p".to_owned(),
        launch_digest: "l".to_owned(),
        active_execution_timeout_sec: 1800,
        progress_gap_timeout_sec: 300,
        run_budget_sec: None,
        ide_plugins: vec![IdePlugin {
            destination: "air-integrationTests-bridge/lib/a.jar".to_owned(),
            sha256: "s".to_owned(),
        }],
        lane: None,
        lane_inputs: None,
        lane_classes: Vec::new(),
        session_logs: Vec::new(),
    };
    assert_eq!(
        serde_json::to_string(&request).unwrap(),
        r#"{"selectors":[],"junit5Filters":["include-tag=air-integration-ui"],"productStamp":"p","launchDigest":"l","activeExecutionTimeoutSec":1800,"progressGapTimeoutSec":300,"idePlugins":[{"destination":"air-integrationTests-bridge/lib/a.jar","sha256":"s"}]}"#
    );
    request.lane = Some("ui".to_owned());
    request.lane_inputs = Some(LaneInputs {
        fake_agent_jar: "f".to_owned(),
        lane_documents: "d".to_owned(),
        suite_documents: "d".to_owned(),
    });
    let encoded = serde_json::to_string(&request).unwrap();
    assert!(
        encoded.ends_with(r#""lane":"ui","laneInputs":{"fakeAgentJar":"f","laneDocuments":"d","suiteDocuments":"d"}}"#),
        "{encoded}"
    );
    assert_eq!(serde_json::from_str::<RunRequest>(&encoded).unwrap(), request);
    request.lane_classes = vec![
        LaneClass {
            suite: "composer".to_owned(),
            test_class_name: "AirComposerGeneratedFlowUiTest".to_owned(),
        },
        LaneClass {
            suite: "genui-host".to_owned(),
            test_class_name: "AirGenUiHostUiTest".to_owned(),
        },
    ];
    let encoded = serde_json::to_string(&request).unwrap();
    assert!(
        encoded.ends_with(
            r#""laneClasses":[{"suite":"composer","testClassName":"AirComposerGeneratedFlowUiTest"},{"suite":"genui-host","testClassName":"AirGenUiHostUiTest"}]}"#
        ),
        "{encoded}"
    );
    assert_eq!(serde_json::from_str::<RunRequest>(&encoded).unwrap(), request);
    request.session_logs = vec![SessionLog {
        label: "thread-7".to_owned(),
        sha256: "z".to_owned(),
    }];
    let encoded = serde_json::to_string(&request).unwrap();
    assert!(
        encoded.ends_with(r#""sessionLogs":[{"label":"thread-7","sha256":"z"}]}"#),
        "{encoded}"
    );
    assert_eq!(serde_json::from_str::<RunRequest>(&encoded).unwrap(), request);
}

// The lane daemon types a failure: its kind, its message and its detail, and the record keeps its bytes when it is
// published again. The `error` text of an older journal is no field, so that record reads with no failure.
#[test]
fn a_failure_record_carries_the_typed_failure_and_no_error_text() {
    let typed = r#"{"event":"containerFailed","timestamp":"t","id":"[suite:s]","displayName":"S","className":"S","type":"not_implemented","message":"m","detail":"d"}"#;
    let RunEventKind::ContainerFailed(failed) = decode(typed).kind else {
        panic!("not a container failure");
    };
    assert_eq!(
        failed.failure,
        TypedFailure {
            kind: Some("not_implemented".to_owned()),
            message: Some("m".to_owned()),
            detail: Some("d".to_owned()),
        }
    );
    let re_encoded = serde_json::to_value(RunEventKind::ContainerFailed(failed)).unwrap();
    assert_eq!(re_encoded, serde_json::from_str::<Value>(typed).unwrap());
    let older = r#"{"event":"testFinished","timestamp":"t","id":"i","displayName":"n","status":"FAILED","durationMs":3,"error":"boom"}"#;
    let RunEventKind::TestFinished(finished) = decode(older).kind else {
        panic!("not a finished test");
    };
    assert_eq!(finished.failure, TypedFailure::default());
    assert_eq!(
        protocol_code(r#"{"event":"containerFailed","timestamp":"t","id":"i","displayName":"n","type":null}"#),
        CODE_FIELD_MISSING
    );
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

// The daemon sends each error line of a run as an explicit record. The record keeps its bytes, which a non-JSON
// line has none of, and a record without its text is refused.
#[test]
fn an_explicit_output_record_keeps_its_text_and_its_bytes() {
    let line = r#"{"event":"output","text":"Recycling the IDE because the reset failed: \u00e9"}"#;
    let decoded = NdjsonDecoder::default().push(format!("{line}\n").as_bytes());
    assert_eq!(decoded.failure, None);
    let [event] = decoded.events.as_slice() else {
        panic!("read {:?}", decoded.events)
    };
    let RunEventKind::Output(output) = &event.kind else {
        panic!("read {:?}", event.kind)
    };
    assert_eq!(output.text, "Recycling the IDE because the reset failed: é");
    assert_eq!(event.raw.as_ref().map(|raw| raw.get()), Some(line));
    for refused in [
        r#"{"event":"output"}"#,
        r#"{"event":"output","text":null}"#,
        r#"{"event":"output","text":7}"#,
    ] {
        assert_eq!(protocol_code(refused), CODE_FIELD_MISSING, "{refused}");
    }
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

// A literal, so a bump of the protocol is a deliberate change of the lane daemon and of the controller together.
#[test]
fn the_lane_daemon_speaks_protocol_6() {
    assert_eq!(PROTOCOL_VERSION, 6);
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

// The two records of the IDE life decode, keep their bytes, and refuse a field of the wrong kind or a null signal.
#[test]
fn the_ide_life_records_decode_and_refuse_a_wrong_field() {
    let launched = r#"{"event":"ideLaunched","pid":4242,"launchName":"launch-1","launchKey":"k","logDir":"/data/ide/k/log/launch-1"}"#;
    let event = decode(launched);
    assert_eq!(event.event(), Event::IdeLaunched);
    assert_eq!(event.to_raw().get(), launched);
    let RunEventKind::IdeLaunched(record) = event.kind else {
        panic!("read {:?}", event.kind);
    };
    assert_eq!((record.pid, record.log_dir.as_str()), (4242, "/data/ide/k/log/launch-1"));

    let exited = |tail: &str| format!(r#"{{"event":"ideExited","pid":4242{tail}}}"#);
    let RunEventKind::IdeExited(record) = decode(&exited(r#","exitCode":143,"signal":"SIGTERM""#)).kind else {
        panic!("not an ideExited record");
    };
    assert_eq!((record.exit_code, record.signal.as_deref()), (143, Some("SIGTERM")));
    let RunEventKind::IdeExited(record) = decode(&exited(r#","exitCode":0"#)).kind else {
        panic!("not an ideExited record");
    };
    assert_eq!(record.signal, None);
    for line in [
        exited(r#","exitCode":0,"signal":null"#),
        exited(""),
        r#"{"event":"ideLaunched","pid":"4242","launchName":"l","launchKey":"k","logDir":"/d"}"#.to_owned(),
        r#"{"event":"ideLaunched","timestamp":null,"pid":4242,"launchName":"l","launchKey":"k","logDir":"/d"}"#.to_owned(),
    ] {
        assert_eq!(protocol_code(&line), CODE_FIELD_MISSING, "{line}");
    }
}

// A daemon stamps the IDE life records with the time it saw each, and a record of an older daemon without a time still
// reads. The ready record names the launch and the wait for READY.
#[test]
fn the_ide_life_records_carry_their_time_and_the_ready_record_its_wait() {
    let launched = r#"{"event":"ideLaunched","timestamp":"2026-10-21T16:34:02.120Z","pid":4242,"launchName":"restart-1-mvic2833","launchKey":"k","logDir":"/d"}"#;
    let event = decode(launched);
    assert_eq!(event.to_raw().get(), launched);
    let RunEventKind::IdeLaunched(record) = &event.kind else {
        panic!("read {:?}", event.kind);
    };
    assert_eq!(record.timestamp.as_deref(), Some("2026-10-21T16:34:02.120Z"));
    assert_eq!(
        serde_json::to_string(&event.kind).unwrap(),
        launched,
        "the reading writes the same bytes"
    );
    let RunEventKind::IdeExited(record) = decode(r#"{"event":"ideExited","timestamp":"t","pid":4242,"exitCode":0}"#).kind else {
        panic!("not an ideExited record");
    };
    assert_eq!(record.timestamp.as_deref(), Some("t"));

    let ready = r#"{"event":"ideReady","timestamp":"2026-10-21T16:34:09.069Z","pid":4243,"launchName":"restart-1-mvic2833","launchKey":"k","readyAfterMs":6949}"#;
    let event = decode(ready);
    assert_eq!(event.event(), Event::IdeReady);
    assert_eq!(serde_json::to_string(&event.kind).unwrap(), ready);
    let RunEventKind::IdeReady(record) = event.kind else {
        panic!("not an ideReady record");
    };
    assert_eq!((record.pid, record.ready_after_ms), (4243, 6949));
    assert_eq!(
        protocol_code(r#"{"event":"ideReady","timestamp":"t","pid":1,"launchName":"l","launchKey":"k"}"#),
        CODE_FIELD_MISSING,
        "a ready record without its wait"
    );
}

// A timing record names its step in the vocabulary of the steps, its time, its duration and, for a step of one suite or
// scenario, its subject.
#[test]
fn a_timing_record_names_its_step_its_duration_and_its_subject() {
    let line = r#"{"event":"timing","timestamp":"2026-10-21T16:40:00.000Z","step":"bare-launch","durationMs":21567,"subject":"startup-class-loading"}"#;
    let event = decode(line);
    assert_eq!(event.event(), Event::Timing);
    assert_eq!(serde_json::to_string(&event.kind).unwrap(), line);
    let RunEventKind::Timing(record) = event.kind else {
        panic!("not a timing record");
    };
    assert_eq!(record.step.parse::<TimedStep>().ok(), Some(TimedStep::BareLaunch));
    assert_eq!(
        (record.duration_ms, record.subject.as_deref()),
        (21567, Some("startup-class-loading"))
    );
    let RunEventKind::Timing(record) = decode(r#"{"event":"timing","timestamp":"t","step":"lane-run-quit","durationMs":1700}"#).kind else {
        panic!("not a timing record");
    };
    assert_eq!(record.subject, None, "a step of the whole run");
    assert_eq!(
        protocol_code(r#"{"event":"timing","timestamp":"t","step":"bare-launch","durationMs":"1"}"#),
        CODE_FIELD_MISSING
    );
}

// A decision of the lane daemon about its IDE decodes as the decision of the controller, keeps its bytes, and
// serializes back to its own shape.
#[test]
fn a_decision_record_of_the_lane_daemon_decodes_and_keeps_its_bytes() {
    let line = r#"{"event":"decision","subject":"ide","action":"reload","reason":"only the bridge plugin changed"}"#;
    let event = decode(line);
    assert_eq!(event.event(), Event::Decision);
    assert_eq!(event.to_raw().get(), line);
    let RunEventKind::Decision(decision) = &event.kind else {
        panic!("read {:?}", event.kind);
    };
    assert_eq!(decision.line(), "ide reload: only the bridge plugin changed");
    let synthesized = RunEvent::synthesized(event.kind.clone());
    assert_eq!(synthesized.to_raw().get(), line);
    assert_eq!(
        protocol_code(r#"{"event":"decision","subject":"plugin","action":"reload"}"#),
        CODE_FIELD_MISSING
    );
}

// The lane daemon says when its recorder records nothing. The record keeps its bytes, serializes back to its own
// shape, and needs its reason.
#[test]
fn a_trace_failure_of_the_lane_daemon_decodes_and_keeps_its_bytes() {
    let line = r#"{"event":"traceFailed","timestamp":"2026-10-16T08:00:00Z","reason":"the recorder refused the scenario 'a'"}"#;
    let event = decode(line);
    assert_eq!(event.event(), Event::TraceFailed);
    assert_eq!(event.to_raw().get(), line);
    let RunEventKind::TraceFailed(failed) = &event.kind else {
        panic!("read {:?}", event.kind);
    };
    assert_eq!(failed.reason, "the recorder refused the scenario 'a'");
    assert_eq!(RunEvent::synthesized(event.kind.clone()).to_raw().get(), line);
    assert_eq!(
        protocol_code(r#"{"event":"traceFailed","timestamp":"2026-10-16T08:00:00Z"}"#),
        CODE_FIELD_MISSING
    );
}

// The digest of a plugin directory reads each destination and content digest of its files in destination order, so
// the order of the files does not move it, and a file of another directory does not either.
#[test]
fn the_digest_of_a_plugin_directory_is_over_its_own_files_in_destination_order() {
    // The shell gives the same values: `printf '%s\0' bridge/lib/a.jar 1 bridge/lib/b.jar 2 | shasum -a 256`.
    let digests = plugin_directory_digests([("bridge/lib/b.jar", "2"), ("resident/lib/r.jar", "3"), ("bridge/lib/a.jar", "1")]);
    assert_eq!(
        digests,
        BTreeMap::from([
            (
                "bridge".to_owned(),
                "98304173ed18c6e9fc65d9987c2b28cef126c89959d35914fa7862204d2796b2".to_owned()
            ),
            (
                "resident".to_owned(),
                "2495ce34ed1483690fb99314c174c7db5d6e52fb0a305c8629ea9a4529f6ebbd".to_owned()
            ),
        ])
    );
    assert_eq!(
        plugin_directory_digests([("bridge/lib/a.jar", "1"), ("bridge/lib/b.jar", "2")])["bridge"],
        digests["bridge"]
    );
    assert!(plugin_directory_digests([]).is_empty());
}
