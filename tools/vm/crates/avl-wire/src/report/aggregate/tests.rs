use pretty_assertions::assert_eq;
use serde::Serialize;
use serde_json::{Map, Value, json};

use super::*;
use crate::report::tests::{refusal_code, valid_report};

fn valid_shard_verdict() -> ShardVerdict {
    ShardVerdict {
        aggregate_schema_version: AGGREGATE_SCHEMA_VERSION,
        kind: AggregateKind::Shard,
        status: Status::Passed,
        code: None,
        diagnostic: None,
        shard_count: 1,
        entries: vec![ShardEntry {
            entry: Entry::new("air-linux-1", EntryStatus::Passed),
            shard_index: 1,
        }],
        started_at: None,
        completed_at: None,
        wall_duration_ms: 249_364.0,
        total_duration_ms: 249_364.0,
        execution: ExecutionCounts {
            tests_started: 23,
            containers_skipped: 1,
            ..ExecutionCounts::default()
        },
        xml: XmlCounts {
            tests: 23,
            ..XmlCounts::default()
        },
        ordering: crate::report::ORDERING.to_owned(),
        suites: Vec::new(),
        failures: Vec::new(),
        skipped_containers: Vec::new(),
        coverage: Vec::new(),
        executed_classes: Vec::new(),
        overlaps: Vec::new(),
    }
}

fn valid_flake_summary() -> FlakeSummary {
    FlakeSummary {
        aggregate_schema_version: AGGREGATE_SCHEMA_VERSION,
        kind: AggregateKind::Flake,
        attempted_trials: 12,
        authoritative_trials: 12,
        trials: vec![FlakeTrial {
            entry: Entry::new("air-linux-1", EntryStatus::Passed),
            ordinal: 1,
            reset_policy: ResetPolicy::Warm,
            duration_ms: 249_364.0,
            guest_free_bytes_before: None,
            guest_free_bytes_after: None,
        }],
        infrastructure_trials: Vec::new(),
        classes: vec![FlakeClass {
            class_name: "com.intellij.air.A".to_owned(),
            bucket: FlakeBucket::Stable,
            executed_trials: 12,
            failed_trials: 0,
            skipped_trials: 0,
            executed_ordinals: Vec::new(),
            failed_ordinals: Vec::new(),
            skipped_ordinals: Vec::new(),
            rate: None,
            interval: None,
            skip_reasons: Vec::new(),
            order_signature: None,
        }],
        flake_set: Vec::new(),
        broken_set: Vec::new(),
        stable_set: Vec::new(),
        not_measured: Vec::new(),
        unstable_prerequisite: Vec::new(),
        insufficient_evidence: Vec::new(),
        order_suspects: Vec::new(),
        lane_flake_rate: 0.0,
        measured_classes: 1,
        lane_flake_interval: WilsonInterval {
            low: 0.0,
            high: 0.26,
            confidence: CONFIDENCE_95,
        },
        reportable: true,
        not_reportable_reason: None,
    }
}

fn encode<T: Serialize>(value: &T) -> Vec<u8> {
    serde_json::to_vec(value).unwrap()
}

/// Reshapes an otherwise valid aggregate. JSON-shaped rather than typed, because half of these cases put a value
/// on the wire that the type cannot hold - which is the point.
fn reshaped<T: Serialize>(valid: &T, mutate: impl FnOnce(&mut Map<String, Value>)) -> Vec<u8> {
    let Value::Object(mut fields) = serde_json::to_value(valid).unwrap() else {
        unreachable!()
    };
    mutate(&mut fields);
    encode(&fields)
}

#[test]
fn a_valid_shard_verdict_round_trips() {
    let decoded = decode_shard_verdict(&encode(&valid_shard_verdict())).expect("a valid shard verdict was refused");
    assert_eq!(decoded, valid_shard_verdict());
    // The entry's own fields and the shard index are flat on the wire, the way the interface extends rather than
    // nests.
    let document = String::from_utf8(encode(&valid_shard_verdict())).unwrap();
    assert!(
        document.contains(r#""entries":[{"worker":"air-linux-1","status":"passed","shardIndex":1}]"#),
        "{document}"
    );
}

#[test]
fn a_valid_flake_summary_round_trips() {
    let decoded = decode_flake_summary(&encode(&valid_flake_summary())).expect("a valid flake summary was refused");
    assert_eq!(decoded, valid_flake_summary());
    let document = String::from_utf8(encode(&valid_flake_summary())).unwrap();
    assert!(
        document.contains(r#""trials":[{"worker":"air-linux-1","status":"passed","ordinal":1,"resetPolicy":"warm","#),
        "{document}"
    );
}

// The two aggregates share a version and a decoder front half, so a document of the wrong kind satisfies almost
// everything; refusing on the kind keeps a flake summary from being read as a verdict about a lane.
#[test]
fn an_aggregate_of_the_wrong_kind_is_refused() {
    let flake_as_shard = reshaped(&valid_shard_verdict(), |fields| {
        fields.insert("kind".to_owned(), json!("flake"));
    });
    assert_eq!(refusal_code(decode_shard_verdict(&flake_as_shard)), "aggregate_schema_wrong_kind");
    let shard_as_flake = reshaped(&valid_flake_summary(), |fields| {
        fields.insert("kind".to_owned(), json!("shard"));
    });
    assert_eq!(refusal_code(decode_flake_summary(&shard_as_flake)), "aggregate_schema_wrong_kind");
}

#[test]
fn an_aggregate_at_another_version_is_refused() {
    let document = reshaped(&valid_shard_verdict(), |fields| {
        fields.insert("aggregateSchemaVersion".to_owned(), json!(AGGREGATE_SCHEMA_VERSION + 1));
    });
    assert_eq!(refusal_code(decode_shard_verdict(&document)), "aggregate_schema_unsupported");
    assert_eq!(refusal_code(decode_flake_summary(b"{}")), "aggregate_schema_version_missing");
}

// A guard status with no code leaves the reader a red result and nothing to act on. `no_tests` and
// `all_skipped` are guards too: a lane that measured nothing has to say why.
#[test]
fn a_guard_status_must_name_its_diagnostic() {
    for status in [Status::NoTests, Status::AllSkipped, Status::InfrastructureError] {
        let document = reshaped(&valid_shard_verdict(), |fields| {
            fields.insert("status".to_owned(), json!(status));
        });
        assert_eq!(
            refusal_code(decode_shard_verdict(&document)),
            "aggregate_schema_unnamed_verdict",
            "{status}"
        );
    }
    // With a code it is accepted, so the refusal is about the absence and not about the status.
    let named = reshaped(&valid_shard_verdict(), |fields| {
        fields.insert("status".to_owned(), json!("infrastructure_error"));
        fields.insert("code".to_owned(), json!("shard_infrastructure_error"));
    });
    let verdict = decode_shard_verdict(&named).expect("a named infrastructure verdict was refused");
    assert_eq!(verdict.code, Some(ShardDiagnosticCode::InfrastructureError));
}

// `failed` is self-describing - its `failures` array is the diagnosis - so an ordinary red lane decodes with no
// code, and a code is still permitted.
#[test]
fn a_failed_verdict_needs_no_diagnostic_code() {
    let failed = reshaped(&valid_shard_verdict(), |fields| {
        fields.insert("status".to_owned(), json!("failed"));
    });
    let verdict = decode_shard_verdict(&failed).expect("an ordinary red lane was refused");
    assert_eq!(verdict.code, None);
    let named = reshaped(&valid_shard_verdict(), |fields| {
        fields.insert("status".to_owned(), json!("failed"));
        fields.insert("code".to_owned(), json!("shard_overlap"));
    });
    decode_shard_verdict(&named).expect("a named failed verdict was refused");
}

#[test]
fn unknown_aggregate_vocabulary_is_refused() {
    type Mutation = Box<dyn FnOnce(&mut Map<String, Value>)>;
    let shard_cases: [(&str, Mutation, &str); 4] = [
        (
            "a status",
            Box::new(|fields| {
                fields.insert("status".to_owned(), json!("green"));
            }),
            "aggregate_schema_unknown_status",
        ),
        (
            "an ordering",
            Box::new(|fields| {
                fields.insert("ordering".to_owned(), json!("shard_index"));
            }),
            "aggregate_schema_unknown_ordering",
        ),
        (
            "a diagnostic code",
            Box::new(|fields| {
                fields.insert("status".to_owned(), json!("failed"));
                fields.insert("code".to_owned(), json!("shard_went_wrong"));
            }),
            "aggregate_schema_unknown_code",
        ),
        (
            "an entry status",
            Box::new(|fields| fields["entries"][0]["status"] = json!("probably_fine")),
            "aggregate_schema_unknown_entry_status",
        ),
    ];
    for (what, mutate, code) in shard_cases {
        assert_eq!(
            refusal_code(decode_shard_verdict(&reshaped(&valid_shard_verdict(), mutate))),
            code,
            "shard/{what}"
        );
    }

    let flake_cases: [(&str, Mutation, &str); 4] = [
        (
            "a bucket",
            Box::new(|fields| fields["classes"][0]["bucket"] = json!("sometimesFine")),
            "aggregate_schema_unknown_bucket",
        ),
        (
            "an order signature",
            Box::new(|fields| fields["classes"][0]["orderSignature"] = json!("second_trial_only")),
            "aggregate_schema_unknown_order_signature",
        ),
        (
            "a reset policy",
            Box::new(|fields| fields["trials"][0]["resetPolicy"] = json!("reboot")),
            "aggregate_schema_unknown_reset",
        ),
        (
            "a confidence",
            Box::new(|fields| fields["laneFlakeInterval"]["confidence"] = json!(0.9)),
            "aggregate_schema_unknown_confidence",
        ),
    ];
    for (what, mutate, code) in flake_cases {
        assert_eq!(
            refusal_code(decode_flake_summary(&reshaped(&valid_flake_summary(), mutate))),
            code,
            "flake/{what}"
        );
    }
}

// `f > n` is arithmetic that cannot have happened, and every rate and interval derived from the pair is
// meaningless once it has.
#[test]
fn a_class_cannot_fail_more_trials_than_it_ran() {
    let document = reshaped(&valid_flake_summary(), |fields| {
        fields["classes"][0]["executedTrials"] = json!(3);
        fields["classes"][0]["failedTrials"] = json!(4);
    });
    assert_eq!(refusal_code(decode_flake_summary(&document)), "aggregate_schema_impossible_counts");
}

// A missing boolean would read as "not quotable" for a run that was fine, or with the opposite default as
// quotable for one that was not; neither guess is safe.
#[test]
fn the_honesty_guard_is_required() {
    let document = reshaped(&valid_flake_summary(), |fields| {
        fields.remove("reportable");
    });
    assert_eq!(refusal_code(decode_flake_summary(&document)), "aggregate_schema_field_missing");
}

// An embedded report gets the same refusals as a standalone one, or a document this controller refuses on its
// own becomes readable by being wrapped.
#[test]
fn an_embedded_report_is_validated_too() {
    let embedding = |report: Value| {
        reshaped(&valid_shard_verdict(), |fields| {
            fields.insert("status".to_owned(), json!("failed"));
            fields.insert("code".to_owned(), json!("shard_infrastructure_error"));
            fields["entries"][0]["report"] = report;
        })
    };
    let mut broken = serde_json::to_value(valid_report()).unwrap();
    broken["status"] = json!("green");
    assert_eq!(
        refusal_code(decode_shard_verdict(&embedding(broken.clone()))),
        "report_schema_unknown_status"
    );

    // One at another version is refused by version rather than by word, so the message says which.
    let mut stale = serde_json::to_value(valid_report()).unwrap();
    stale["reportSchemaVersion"] = json!(RUN_REPORT_SCHEMA_VERSION - 1);
    assert_eq!(
        refusal_code(decode_shard_verdict(&embedding(stale.clone()))),
        "report_schema_unsupported"
    );
    broken["reportSchemaVersion"] = json!(RUN_REPORT_SCHEMA_VERSION - 1);
    assert_eq!(refusal_code(decode_shard_verdict(&embedding(broken))), "report_schema_unsupported");

    let mut unordered = serde_json::to_value(valid_report()).unwrap();
    unordered["ordering"] = json!("document_order");
    assert_eq!(
        refusal_code(decode_shard_verdict(&embedding(unordered))),
        "report_schema_unknown_ordering"
    );

    let mut trial = valid_flake_summary();
    let mut report = valid_report();
    report.report_schema_version = RUN_REPORT_SCHEMA_VERSION - 1;
    trial.trials[0].entry.report = Some(Box::new(report));
    assert_eq!(refusal_code(decode_flake_summary(&encode(&trial))), "report_schema_unsupported");

    let embedded = decode_shard_verdict(&embedding(serde_json::to_value(valid_report()).unwrap())).unwrap();
    assert_eq!(embedded.entries[0].entry.report.as_deref(), Some(&valid_report()));
}

// `no_report` is the one entry status a run report cannot carry about itself.
#[test]
fn no_report_is_an_entry_status_and_not_a_report_status() {
    assert_eq!("no_report".parse::<EntryStatus>(), Ok(EntryStatus::NoReport));
    assert!(
        "no_report".parse::<Status>().is_err(),
        "no_report leaked into the run report's own status vocabulary"
    );
    assert_eq!(EntryStatus::ALL.len(), Status::ALL.len() + 1);
    for status in Status::ALL {
        assert_eq!(EntryStatus::from(*status).as_str(), status.as_str());
        assert_eq!(EntryStatus::from(*status).report_status(), Some(*status));
    }
    assert_eq!(EntryStatus::NoReport.report_status(), None);
}

#[test]
fn declared_aggregate_arrays_are_never_null() {
    let mut verdict = valid_shard_verdict();
    verdict.entries.clear();
    let verdict = String::from_utf8(encode(&verdict)).unwrap();
    for field in [
        "entries",
        "suites",
        "failures",
        "skippedContainers",
        "coverage",
        "executedClasses",
        "overlaps",
    ] {
        assert!(
            verdict.contains(&format!(r#""{field}":[]"#)),
            "a shard verdict writes {field} as something other than []"
        );
    }
    let mut summary = valid_flake_summary();
    summary.trials.clear();
    summary.classes.clear();
    let summary = String::from_utf8(encode(&summary)).unwrap();
    for field in [
        "trials",
        "infrastructureTrials",
        "classes",
        "flakeSet",
        "brokenSet",
        "stableSet",
        "notMeasured",
        "unstablePrerequisite",
        "insufficientEvidence",
        "orderSuspects",
    ] {
        assert!(
            summary.contains(&format!(r#""{field}":[]"#)),
            "a flake summary writes {field} as something other than []"
        );
    }
}
