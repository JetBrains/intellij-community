use pretty_assertions::assert_eq;
use serde_json::{Map, Value, json};

use super::*;
use crate::daemon::{Skipped, WatchdogExpired};

/// A report that satisfies every requirement, the starting point of the refusal cases. Built as a value and
/// serialized rather than written as a literal, so a field renamed on the struct cannot leave this fixture
/// silently describing the old shape.
pub(super) fn valid_report() -> RunReport {
    RunReport {
        report_schema_version: RUN_REPORT_SCHEMA_VERSION,
        iteration_id: "iteration-1".to_owned(),
        daemon_run_id: "run-ui-daemon-015546b8-bd1e-4dec-8b20-f9627a770f68".to_owned(),
        daemon_boot_stamp: "boot-1".to_owned(),
        selection: "lane ui".to_owned(),
        status: Status::Passed,
        started_at: "2026-08-23T08:08:56.000200831Z".to_owned(),
        completed_at: "2026-08-23T08:13:05.364Z".to_owned(),
        duration_ms: 249_364.0,
        ordering: ORDERING.to_owned(),
        execution: ExecutionCounts {
            tests_started: 23,
            containers_skipped: 1,
            ..ExecutionCounts::default()
        },
        xml: XmlCounts {
            tests: 23,
            ..XmlCounts::default()
        },
        source: Source {
            guest_path: "/Users/air/data/out/test.xml".to_owned(),
            retrieval: Retrieval::DaemonHttp,
            integrity: Integrity::Complete,
            diagnostic: None,
        },
        suites: Vec::new(),
        failures: Vec::new(),
        skipped_containers: Vec::new(),
        active_tests: Vec::new(),
        unreported_classes: Vec::new(),
        watchdog: Watchdog::default(),
        evidence: Vec::new(),
        trace_archives: Vec::new(),
        traces_error: None,
        tree: None,
        tree_error: None,
        protocol_diagnostic: None,
        verdict_diagnostic: None,
    }
}

fn encode<T: Serialize>(value: &T) -> Vec<u8> {
    serde_json::to_vec(value).unwrap()
}

/// Reshapes an otherwise valid report and answers the bytes, so a refusal case says what it changed.
fn reshaped(mutate: impl FnOnce(&mut Map<String, Value>)) -> Vec<u8> {
    let Value::Object(mut fields) = serde_json::to_value(valid_report()).unwrap() else {
        unreachable!()
    };
    mutate(&mut fields);
    encode(&fields)
}

pub(super) fn refusal_code<T: fmt::Debug>(decoded: Result<T, SchemaError>) -> &'static str {
    match decoded {
        Ok(value) => panic!("expected a refusal, got {value:?}"),
        Err(error) => error.code,
    }
}

#[test]
fn a_valid_report_round_trips() {
    let decoded = decode_run_report(&encode(&valid_report())).expect("a valid report was refused");
    assert_eq!(decoded, valid_report());
}

// The arrays this document declares are declared unconditionally: a reader that saw `null` would have to decide
// for itself whether that meant "none" or "the producer forgot".
#[test]
fn declared_arrays_are_never_null() {
    let document = String::from_utf8(encode(&valid_report())).unwrap();
    for field in ["suites", "failures", "skippedContainers", "activeTests", "unreportedClasses"] {
        assert!(
            document.contains(&format!(r#""{field}":[]"#)),
            "{field} is not an empty array: {document}"
        );
    }
    assert!(!document.contains("null"), "a minimal report contains a null: {document}");
}

// An absent optional stays absent rather than being written as its zero value.
#[test]
fn absent_optionals_are_not_written() {
    let document = String::from_utf8(encode(&valid_report())).unwrap();
    for field in [
        "verdictDiagnostic",
        "protocolDiagnostic",
        "evidence",
        "diagnostic",
        "traces",
        "tracesError",
        "tree",
    ] {
        assert!(
            !document.contains(&format!(r#""{field}""#)),
            "an absent {field} was written: {document}"
        );
    }
}

#[test]
fn the_schema_version_is_checked_before_the_fields() {
    // A document at a version this module does not read, whose fields are otherwise fine, is refused for its
    // version: "it decoded" is not the same as "it means what I think".
    let newer = reshaped(|fields| {
        fields.insert("reportSchemaVersion".to_owned(), json!(RUN_REPORT_SCHEMA_VERSION + 1));
    });
    assert_eq!(refusal_code(decode_run_report(&newer)), "report_schema_unsupported");
    // A version-less document is refused before anything else is inspected, so the message names the version.
    assert_eq!(refusal_code(decode_run_report(b"{}")), "report_schema_version_missing");
    assert_eq!(
        refusal_code(decode_run_report(br#"{"reportSchemaVersion":"2"}"#)),
        "report_schema_version_missing"
    );
    assert_eq!(refusal_code(decode_run_report(b"[]")), "report_schema_unreadable");
}

#[test]
fn a_required_field_missing_is_refused() {
    for field in [
        "iterationId",
        "daemonRunId",
        "status",
        "ordering",
        "execution",
        "xml",
        "source",
        "watchdog",
    ] {
        let document = reshaped(|fields| {
            fields.remove(field);
        });
        assert_eq!(refusal_code(decode_run_report(&document)), "report_schema_field_missing", "{field}");
    }
}

// An explicit `null` is not an absent field.
#[test]
fn an_explicit_null_is_refused() {
    let document = reshaped(|fields| {
        fields.insert("status".to_owned(), Value::Null);
    });
    assert_eq!(refusal_code(decode_run_report(&document)), "report_schema_field_missing");
    let optional = reshaped(|fields| {
        fields.insert("verdictDiagnostic".to_owned(), Value::Null);
    });
    assert_eq!(refusal_code(decode_run_report(&optional)), "report_schema_field_missing");
}

// A number where a diagnostic belongs is a rename that landed halfway, not a caller being generous.
#[test]
fn a_present_optional_of_the_wrong_kind_is_refused() {
    let document = reshaped(|fields| {
        fields.insert("verdictDiagnostic".to_owned(), json!(7));
    });
    assert_eq!(refusal_code(decode_run_report(&document)), "report_schema_field_missing");
}

// The archives round-trip with their bundles beside a traces error, and both are optional. A document of the
// first pull design, with one `traces` object, still decodes and names no archive.
#[test]
fn the_trace_archives_round_trip_beside_an_error() {
    let mut pulled = valid_report();
    pulled.trace_archives = vec![TraceArchive {
        path: "/host/runs/r/traces/it-1/001.zip".to_owned(),
        bytes: 4096,
        bundles: vec![TraceBundle {
            id: "ab12".to_owned(),
            entry: "run/AirTest/start/".to_owned(),
            test_class: "AirTest".to_owned(),
            scenario: "start".to_owned(),
            flow: None,
            status: "failed".to_owned(),
            has_video: true,
        }],
    }];
    pulled.traces_error = Some("trace-pack-ready in air-linux-1 exited with 70".to_owned());
    let decoded = decode_run_report(&encode(&pulled)).expect("a report with trace archives was refused");
    assert_eq!(decoded, pulled);
    // The verdict is the one the rest of the document gives: the archives are evidence and decide nothing.
    assert_eq!((decoded.status, decoded.verdict_diagnostic), (Status::Passed, None));

    let older = reshaped(|fields| {
        fields.insert(
            "traces".to_owned(),
            json!({"path": "/host/reports/r/traces/it-1.zip", "bytes": 4096}),
        );
    });
    let decoded = decode_run_report(&older).expect("a report of the first pull design did not decode");
    assert!(decoded.trace_archives.is_empty());
}

#[test]
fn an_inconsistent_trace_archive_is_refused() {
    let archive = |path: &str, bytes: i64, bundles: Vec<TraceBundle>| {
        let mut report = valid_report();
        report.trace_archives = vec![TraceArchive {
            path: path.to_owned(),
            bytes,
            bundles,
        }];
        encode(&report)
    };
    let nameless = TraceBundle {
        id: String::new(),
        entry: "a/".to_owned(),
        test_class: String::new(),
        scenario: String::new(),
        flow: None,
        status: String::new(),
        has_video: false,
    };
    for document in [
        archive("", 1, Vec::new()),
        archive("/host/traces.zip", -1, Vec::new()),
        archive("/host/traces.zip", 1, vec![nameless]),
    ] {
        assert_eq!(refusal_code(decode_run_report(&document)), "report_schema_traces_inconsistent");
    }
    let document = reshaped(|fields| {
        fields.insert("tracesError".to_owned(), json!({}));
    });
    assert_eq!(refusal_code(decode_run_report(&document)), "report_schema_field_missing");
}

// A clean tree writes an empty list and a zero count.
#[test]
fn the_tree_pair_round_trips_either_way() {
    let mut clean = valid_report();
    clean.tree = Some(Tree {
        head: "1e46d8efe7b9e".to_owned(),
        uncommitted: Vec::new(),
        uncommitted_count: 0,
    });
    let document = String::from_utf8(encode(&clean)).unwrap();
    assert!(
        document.contains(r#""tree":{"head":"1e46d8efe7b9e","uncommitted":[],"uncommittedCount":0}"#),
        "{document}"
    );
    assert_eq!(decode_run_report(document.as_bytes()).unwrap(), clean);

    let mut unread = valid_report();
    unread.tree_error = Some("git status exited with 128".to_owned());
    let decoded = decode_run_report(&encode(&unread)).unwrap();
    assert_eq!(decoded, unread);
    assert_eq!((decoded.status, decoded.verdict_diagnostic), (Status::Passed, None));
}

#[test]
fn an_inconsistent_tree_pair_is_refused() {
    let with_tree = |tree: Tree, error: Option<&str>| {
        let mut report = valid_report();
        report.tree = Some(tree);
        report.tree_error = error.map(str::to_owned);
        encode(&report)
    };
    let tree = |head: &str, paths: usize, count: usize| Tree {
        head: head.to_owned(),
        uncommitted: vec!["plugins/air/file.kt".to_owned(); paths],
        uncommitted_count: count,
    };
    for (what, document) in [
        ("both halves", with_tree(tree("abc", 0, 0), Some("not read"))),
        ("no head", with_tree(tree("", 0, 0), None)),
        (
            "too many paths",
            with_tree(tree("abc", MAX_TREE_PATHS + 1, MAX_TREE_PATHS + 1), None),
        ),
        ("a count below the paths", with_tree(tree("abc", 2, 1), None)),
    ] {
        assert_eq!(
            refusal_code(decode_run_report(&document)),
            "report_schema_tree_inconsistent",
            "{what}"
        );
    }
    for (field, value) in [("tree", json!("abc")), ("treeError", json!({}))] {
        let document = reshaped(|fields| {
            fields.insert(field.to_owned(), value);
        });
        assert_eq!(refusal_code(decode_run_report(&document)), "report_schema_field_missing", "{field}");
    }
}

#[test]
fn unknown_vocabulary_is_refused() {
    let suite = |case_status: &str| {
        json!([{
            "name": "s", "durationMs": 1, "documentIndex": 0, "tests": 1, "failures": 0, "errors": 0, "skipped": 0,
            "cases": [{"className": "C", "name": "t", "status": case_status, "durationMs": 1}],
        }])
    };
    let failure = |source: &str, kind: &str| {
        json!([{
            "source": source, "testName": "t", "kind": kind, "message": "m",
            "relevantFrames": [], "messageTruncated": false, "detailTruncated": false,
        }])
    };
    type Mutation = Box<dyn FnOnce(&mut Map<String, Value>)>;
    let cases: [(&str, Mutation, &str); 7] = [
        (
            "a status",
            Box::new(|fields| {
                fields.insert("status".to_owned(), json!("green"));
            }),
            "report_schema_unknown_status",
        ),
        (
            "an ordering",
            Box::new(|fields| {
                fields.insert("ordering".to_owned(), json!("document_order"));
            }),
            "report_schema_unknown_ordering",
        ),
        (
            "a retrieval",
            Box::new(|fields| fields["source"]["retrieval"] = json!("scp")),
            "report_schema_unknown_retrieval",
        ),
        (
            "an integrity",
            Box::new(|fields| fields["source"]["integrity"] = json!("probably_fine")),
            "report_schema_unknown_integrity",
        ),
        (
            "a case status",
            Box::new(move |fields| {
                fields.insert("suites".to_owned(), suite("green"));
            }),
            "report_schema_unknown_case_status",
        ),
        (
            "a failure kind",
            Box::new(move |fields| {
                fields.insert("failures".to_owned(), failure("junit_xml", "exploded"));
            }),
            "report_schema_unknown_failure_kind",
        ),
        (
            "a failure source",
            Box::new(move |fields| {
                fields.insert("failures".to_owned(), failure("hearsay", "failure"));
            }),
            "report_schema_unknown_failure_source",
        ),
    ];
    for (what, mutate, code) in cases {
        assert_eq!(refusal_code(decode_run_report(&reshaped(mutate))), code, "{what}");
    }
    // The same shapes with declared words decode, so each refusal is about the word.
    let declared = reshaped(|fields| {
        fields.insert("suites".to_owned(), suite("skipped"));
        fields.insert("failures".to_owned(), failure("progress", "container"));
    });
    let decoded = decode_run_report(&declared).expect("declared words were refused");
    assert_eq!(
        (decoded.suites[0].cases[0].status, decoded.failures[0].kind),
        (CaseStatus::Skipped, FailureKind::Container)
    );
}

// The first four integrities are the JUnit reader's own words; `avl-report` holds the reader to them.
#[test]
fn the_integrities_are_spelled_once() {
    let spelled: Vec<&str> = Integrity::ALL.iter().map(|integrity| integrity.as_str()).collect();
    assert_eq!(spelled, ["complete", "empty", "truncated", "malformed", "missing", "oversized"]);
}

// --- the evidence guard -----------------------------------------------------------------------------------

fn expiry(evidence: &[&str]) -> RunEvent {
    RunEvent::synthesized(RunEventKind::WatchdogExpired(WatchdogExpired {
        reason: "progress-gap".to_owned(),
        deadline: "d".to_owned(),
        expired_at: "e".to_owned(),
        detail: None,
        active_execution: None,
        evidence: evidence.iter().map(|path| (*path).to_owned()).collect(),
    }))
}

#[test]
fn safe_evidence_paths_keeps_only_what_is_safe_to_fetch() {
    let skipped = RunEvent::synthesized(RunEventKind::ContainerSkipped(Skipped {
        timestamp: "t".to_owned(),
        execution: ExecutionIdentity {
            id: "i".to_owned(),
            display_name: "d".to_owned(),
            class_name: None,
            method_name: None,
        },
        reason: "r".to_owned(),
    }));
    for (what, events, want) in [
        ("a plain capture", vec![expiry(&["/data/out/shot.png"])], vec!["/data/out/shot.png"]),
        ("a relative path", vec![expiry(&["out/shot.png"])], vec![]),
        ("a traversal", vec![expiry(&["/data/../../etc/passwd.txt"])], vec![]),
        ("a non-capture extension", vec![expiry(&["/data/out/heap.hprof"])], vec![]),
        ("a non-ASCII path", vec![expiry(&["/data/oüt/shot.png"])], vec![]),
        ("a duplicate", vec![expiry(&["/a.png", "/a.png"])], vec!["/a.png"]),
        (
            "case-insensitive extensions",
            vec![expiry(&["/a.PNG", "/b.JPeG", "/c.TXT"])],
            vec!["/a.PNG", "/b.JPeG", "/c.TXT"],
        ),
        ("no expiry at all", vec![skipped], vec![]),
    ] {
        assert_eq!(safe_evidence_paths(&events), want, "{what}");
    }
}

#[test]
fn safe_evidence_paths_caps_the_count() {
    let paths = safe_evidence_paths(&[expiry(&["/a.png", "/b.png", "/c.png", "/d.png", "/e.png", "/f.png"])]);
    assert_eq!(paths, ["/a.png", "/b.png", "/c.png", "/d.png"]);
    assert_eq!(paths.len(), MAX_EVIDENCE_FILES);
}

// A run may publish several expiries, and the one that ended it describes the end.
#[test]
fn safe_evidence_paths_reads_the_last_expiry() {
    assert_eq!(
        safe_evidence_paths(&[expiry(&["/first.png"]), expiry(&["/second.png"])]),
        ["/second.png"]
    );
}

// A path is capped at 512 characters after the slash: these strings are fetched, and a path long enough to
// matter is a path nobody wrote deliberately.
#[test]
fn safe_evidence_paths_bounds_the_length() {
    let at_the_bound = format!("/{}.png", "a".repeat(508));
    let past_it = format!("/{}.png", "a".repeat(509));
    assert_eq!(
        safe_evidence_paths(&[expiry(&[&at_the_bound])]).len(),
        1,
        "a path at the bound was refused"
    );
    assert!(
        safe_evidence_paths(&[expiry(&[&past_it])]).is_empty(),
        "a path past the bound was accepted"
    );
}
