use std::collections::{BTreeMap, BTreeSet};
use std::fs;

use avl_testkit::traces::{self, TRANSCRIPT, example_bundle, lines};
use pretty_assertions::assert_eq;
use serde_json::Value;

use super::*;
use crate::bundle::{MANIFEST_SCHEMA, Manifest};
use crate::protocol::{Command, SpanCommand};
use crate::testdata::{decode_transcript, example_manifest};

/// The two examples the OTLP specification ships, `examples/trace.json` and `examples/logs.json` of
/// github.com/open-telemetry/opentelemetry-proto, copied verbatim. They are the reference for the encoding the
/// structs claim: if a hand-written field name or scalar encoding drifted from OTLP's, one of these would stop
/// decoding strictly or stop re-encoding to the same document.
#[test]
fn the_specification_examples_decode_strictly_and_round_trip() {
    let trace_document = fs::read(traces::path("otlp-spec/trace.json")).unwrap();
    let traces = decode_traces_line(&trace_document).unwrap();
    let span = &traces.resource_spans[0].scope_spans[0].spans[0];
    // Uppercase ids decode; only validation refuses them.
    assert_eq!(
        (span.trace_id.as_str(), span.span_id.as_str()),
        ("5B8EFFF798038103D269B633813FC60C", "EEE19B7EC3C1B174")
    );
    assert_eq!(span.start_time_unix_nano, UnixNano(1_544_712_660_000_000_000));
    assert_eq!(span.end_time_unix_nano, UnixNano(1_544_712_661_000_000_000));
    assert_eq!(span.kind, SpanKindCode::SERVER);
    assert!(traces.validate().is_err(), "uppercase ids are this crate's refusal");
    require_same_document(&trace_document, &traces);

    let logs_document = fs::read(traces::path("otlp-spec/logs.json")).unwrap();
    let logs = decode_logs_line(&logs_document).unwrap();
    let record = &logs.resource_logs[0].scope_logs[0].log_records[0];
    assert_eq!(
        (record.time_unix_nano, record.severity_number),
        (UnixNano(1_544_712_660_300_000_000), SeverityNumber(10))
    );
    assert_eq!(lookup_int(&record.attributes, "int.attribute"), Some(10));
    assert_eq!(
        lookup(&record.attributes, "double.attribute"),
        Some(&AnyValue::DoubleValue(637.704))
    );
    assert!(matches!(lookup(&record.attributes, "array.attribute"), Some(AnyValue::ArrayValue(array)) if array.values.len() == 2));
    assert!(matches!(lookup(&record.attributes, "map.attribute"), Some(AnyValue::KvlistValue(list)) if list.values.len() == 1));
    require_same_document(&logs_document, &logs);
}

/// Re-encodes a decoded value and compares it with the original as JSON values, so that a field this crate drops
/// or respells shows up as a difference.
fn require_same_document<T: Serialize>(original: &[u8], decoded: &T) {
    let want: Value = serde_json::from_slice(original).unwrap();
    let got: Value = serde_json::from_slice(&crate::encode(decoded).unwrap()).unwrap();
    assert_eq!(got, want);
}

#[test]
fn the_sixty_four_bit_scalars_are_decimal_strings_only() {
    #[derive(serde::Serialize)]
    struct Scalars {
        time: UnixNano,
        value: I64String,
    }
    let encoded = crate::encode(&Scalars {
        time: UnixNano::from_ms(1_790_158_500_120),
        value: I64String(-42),
    })
    .unwrap();
    assert_eq!(
        String::from_utf8(encoded).unwrap(),
        r#"{"time":"1790158500120000000","value":"-42"}"#
    );
    for refused in [
        r"1790158500120000000",
        r#""1.5""#,
        r#""-1""#,
        r#""""#,
        r#""12a""#,
        r#""+1""#,
        "null",
        r#""18446744073709551616""#,
    ] {
        assert!(
            serde_json::from_str::<UnixNano>(refused).is_err(),
            "the nanosecond time {refused} was accepted"
        );
    }
    assert_eq!(
        serde_json::from_str::<I64String>(r#""-9223372036854775808""#).unwrap(),
        I64String(i64::MIN)
    );
    for refused in ["10", r#""-""#, r#""9223372036854775808""#] {
        assert!(
            serde_json::from_str::<I64String>(refused).is_err(),
            "the intValue {refused} was accepted"
        );
    }
    assert_eq!(UnixNano::from_ms(1_790_158_500_120).ms(), 1_790_158_500_120);
}

#[test]
fn an_any_value_sets_exactly_one_field() {
    for refused in ["{}", r#"{"stringValue":"a","boolValue":true}"#, r#"{"textValue":"a"}"#] {
        assert!(
            serde_json::from_str::<AnyValue>(refused).is_err(),
            "the value {refused} was accepted"
        );
    }
    assert_eq!(
        serde_json::from_str::<AnyValue>(r#"{"boolValue":true}"#).unwrap(),
        AnyValue::BoolValue(true)
    );
}

#[test]
fn ids() {
    assert_eq!(
        (span_id(0).as_str(), span_id(17).as_str()),
        ("0000000000000001", "0000000000000012")
    );
    let first = trace_id("iter-1", "AirExampleUiTest", "example");
    assert_eq!(first, trace_id("iter-1", "AirExampleUiTest", "example"));
    assert!(is_valid_trace_id(&first), "the trace id {first} is not valid");
    assert_ne!(first, trace_id("iter-1", "AirExampleUiTest", "example2"));
    assert_ne!(first, trace_id("iter-1", "AirExampleUiTes", "texample"));
    for refused in [
        "",
        "00000000000000000000000000000000",
        "5B8EFFF798038103D269B633813FC60C",
        "5b8efff79803810",
    ] {
        assert!(!is_valid_trace_id(refused), "the trace id {refused:?} was accepted");
    }
    for refused in ["0000000000000000", "EEE19B7EC3C1B174", "eee19b7ec3c1b17g"] {
        assert!(!is_valid_span_id(refused), "the span id {refused:?} was accepted");
    }
    assert_eq!(
        [Status::Passed, Status::Failed, Status::Aborted].map(status_code_of),
        [StatusCode::OK, StatusCode::ERROR, StatusCode::UNSET]
    );
}

/// Walks a line as generic JSON and checks what the typed decoder cannot see once it has parsed: that the ids were
/// written as lowercase hex and every 64-bit scalar as a decimal string.
fn require_wire_scalars(where_: &str, value: &Value) {
    let hex = |text: &str, length: usize| text.len() == length && text.bytes().all(|byte| matches!(byte, b'0'..=b'9' | b'a'..=b'f'));
    let decimal = |text: &str| {
        let digits = text.strip_prefix('-').unwrap_or(text);
        !digits.is_empty() && digits.bytes().all(|byte| byte.is_ascii_digit())
    };
    match value {
        Value::Object(fields) => {
            for (key, field) in fields {
                let text = field.as_str();
                match key.as_str() {
                    "traceId" => assert!(text.is_some_and(|text| hex(text, 32)), "{where_}: traceId is {field}"),
                    "spanId" | "parentSpanId" => assert!(text.is_some_and(|text| hex(text, 16)), "{where_}: {key} is {field}"),
                    key if key.ends_with("UnixNano") || key == "intValue" => {
                        assert!(text.is_some_and(decimal), "{where_}: {key} is {field}, not a decimal string");
                    }
                    _ => require_wire_scalars(where_, field),
                }
            }
        }
        Value::Array(elements) => elements.iter().for_each(|element| require_wire_scalars(where_, element)),
        _ => {}
    }
}

struct ExampleTelemetry {
    manifest: Manifest,
    /// The lane's spans, and `ide_spans` the spans the IDE ended, which only the IDE resource holds.
    spans: Vec<Span>,
    ide_spans: Vec<Span>,
    resources: Vec<Resource>,
    records: Vec<LogRecord>,
}

fn read_example_telemetry() -> ExampleTelemetry {
    let mut telemetry = ExampleTelemetry {
        manifest: example_manifest(),
        spans: Vec::new(),
        ide_spans: Vec::new(),
        resources: Vec::new(),
        records: Vec::new(),
    };
    for (index, line) in lines(&example_bundle().join(SPANS_FILE)).iter().enumerate() {
        let where_ = format!("{SPANS_FILE} line {}", index + 1);
        let data = decode_traces_line(line).unwrap_or_else(|error| panic!("{where_}: {error}"));
        data.validate().unwrap_or_else(|error| panic!("{where_}: {error}"));
        require_wire_scalars(&where_, &serde_json::from_slice(line).unwrap());
        for resource in data.resource_spans {
            let ide = lookup_str(&resource.resource.attributes, attr::SERVICE_NAME) == Some(IDE_SERVICE_NAME);
            telemetry.resources.push(resource.resource);
            for scope in resource.scope_spans {
                if ide {
                    assert_ne!(scope.scope.name, SCOPE_NAME, "{where_} writes an IDE span under the lane's scope");
                    telemetry.ide_spans.extend(scope.spans);
                } else {
                    assert_eq!(
                        (scope.scope.name.as_str(), scope.scope.version.as_str()),
                        (SCOPE_NAME, MANIFEST_SCHEMA),
                        "{where_}"
                    );
                    telemetry.spans.extend(scope.spans);
                }
            }
        }
    }
    for (index, line) in lines(&example_bundle().join(LOGS_FILE)).iter().enumerate() {
        let where_ = format!("{LOGS_FILE} line {}", index + 1);
        let data = decode_logs_line(line).unwrap_or_else(|error| panic!("{where_}: {error}"));
        data.validate().unwrap_or_else(|error| panic!("{where_}: {error}"));
        require_wire_scalars(&where_, &serde_json::from_slice(line).unwrap());
        for resource in data.resource_logs {
            telemetry.resources.push(resource.resource);
            telemetry
                .records
                .extend(resource.scope_logs.into_iter().flat_map(|scope| scope.log_records));
        }
    }
    telemetry
}

#[test]
fn every_example_line_is_valid_otlp() {
    let telemetry = read_example_telemetry();
    assert!(!telemetry.spans.is_empty() && !telemetry.records.is_empty());
    let manifest = &telemetry.manifest;
    for resource in &telemetry.resources {
        let attributes = &resource.attributes;
        let service = lookup_str(attributes, attr::SERVICE_NAME);
        assert!(
            service == Some(SERVICE_NAME) || service == Some(IDE_SERVICE_NAME),
            "a resource names the service {service:?}"
        );
        assert_eq!(lookup_str(attributes, attr::RUN_ID), Some(manifest.run_id.as_str()));
        assert_eq!(lookup_str(attributes, attr::LAUNCHER), Some(manifest.launcher.as_str()));
        assert_eq!(lookup_str(attributes, attr::LANE), Some(manifest.lane.as_str()));
    }
}

/// The spans are the transcript's, one for one, under the ids [span_id] derives, and every log record belongs to
/// one of them. That is the join the viewer makes, so the example has to satisfy it exactly.
#[test]
fn the_example_spans_are_the_transcripts_spans() {
    let telemetry = read_example_telemetry();
    let commands = decode_transcript(TRANSCRIPT);
    let Command::Scenario(scenario) = &commands[1] else {
        panic!("the second line is not the scenario");
    };
    let manifest = &telemetry.manifest;
    let trace = trace_id(&manifest.run_id, &manifest.test_class, &manifest.scenario);

    let mut by_id = BTreeMap::new();
    for span in &telemetry.spans {
        assert_eq!(span.trace_id, trace, "the span {:?} is in another trace", span.name);
        assert!(
            by_id.insert(span.span_id.clone(), span).is_none(),
            "the span id {} is written twice",
            span.span_id
        );
    }
    let root = by_id[&span_id(0)];
    assert_eq!((root.parent_span_id.as_str(), root.name.as_str()), ("", scenario.name.as_str()));
    let program = scenario.program.as_ref().map(|program| program.get());
    assert_eq!(
        lookup_str(&root.attributes, attr::PROGRAM),
        program,
        "the root span carries the program verbatim"
    );
    assert_eq!(lookup_str(&root.attributes, attr::SCENARIO_NAME), Some(scenario.name.as_str()));
    assert_eq!(lookup_str(&root.attributes, attr::TEST_CLASS), Some(scenario.test_class.as_str()));
    assert_eq!(lookup_str(&root.attributes, attr::SUITE), scenario.suite.as_deref());
    assert_eq!(lookup_str(&root.attributes, attr::FLOW_ID), scenario.flow.as_deref());
    assert_eq!(lookup_str(&root.attributes, attr::FIXTURE), scenario.fixture.as_deref());
    assert_eq!(lookup_str(&root.attributes, attr::FLAGS), Some("{}"));

    let opened: Vec<&SpanCommand> = commands
        .iter()
        .filter_map(|command| match command {
            Command::Span(span) => Some(span),
            _ => None,
        })
        .collect();
    for span in &opened {
        let written = by_id
            .get(&span_id(span.id))
            .unwrap_or_else(|| panic!("the span {} ({}) was never written", span.id, span.key));
        assert_eq!(written.parent_span_id, span_id(span.parent), "the span {}", span.id);
        assert_eq!(written.name, span.title);
        assert_eq!(lookup_str(&written.attributes, attr::SPAN_KIND), Some(span.kind.as_str()));
        assert_eq!(lookup_str(&written.attributes, attr::SPAN_KEY), Some(span.key.as_str()));
        assert_eq!(lookup_str(&written.attributes, attr::EXPECTATION), span.expectation.as_deref());
        assert_eq!(lookup_str(&written.attributes, attr::SPAN_STATUS), Some(Status::Passed.as_str()));
        let parent = by_id[&written.parent_span_id];
        assert!(
            written.start_time_unix_nano >= parent.start_time_unix_nano && written.end_time_unix_nano <= parent.end_time_unix_nano,
            "the span {:?} runs outside its parent {:?}",
            written.name,
            parent.name
        );
    }
    assert_eq!(by_id.len(), opened.len() + 1, "one span per opened span and the root");

    let mut started = BTreeSet::new();
    let mut events = BTreeMap::<&str, usize>::new();
    for record in &telemetry.records {
        assert_eq!(record.trace_id, trace, "a {} record is in another trace", record.event_name);
        let span = by_id.get(&record.span_id).unwrap_or_else(|| {
            panic!(
                "a {} record names the span {}, which the bundle does not hold",
                record.event_name, record.span_id
            )
        });
        assert!(
            record.time_unix_nano >= span.start_time_unix_nano && record.time_unix_nano <= span.end_time_unix_nano,
            "a {} record lies outside its span {:?}",
            record.event_name,
            span.name
        );
        *events.entry(&record.event_name).or_default() += 1;
        if record.event_name == event::SPAN_STARTED {
            started.insert(record.span_id.as_str());
        }
    }
    for (id, span) in &by_id {
        assert!(
            started.contains(id.as_str()),
            "the span {:?} has no {} record",
            span.name,
            event::SPAN_STARTED
        );
    }
    for name in [
        event::SPAN_STARTED,
        event::SNAPSHOT,
        event::INPUT,
        event::BRIDGE_CALL,
        event::VIDEO,
        event::DRIVER_STEP,
    ] {
        assert!(
            events.contains_key(name),
            "the example holds no {name} record, and the viewer is built against every kind"
        );
    }
}

/// The IDE's spans are in the bundle's one trace, each under a lane span that contains its start or under another
/// IDE span, and none takes a lane span's id. The viewer nests them by that parent, so the example needs them.
#[test]
fn the_example_ide_spans_hang_under_the_lane_span_they_started_in() {
    let telemetry = read_example_telemetry();
    assert!(
        !telemetry.ide_spans.is_empty(),
        "the example holds no IDE span, and the viewer is built against them"
    );
    let manifest = &telemetry.manifest;
    let trace = trace_id(&manifest.run_id, &manifest.test_class, &manifest.scenario);
    let lane: BTreeMap<&str, &Span> = telemetry.spans.iter().map(|span| (span.span_id.as_str(), span)).collect();
    let ide: BTreeSet<&str> = telemetry.ide_spans.iter().map(|span| span.span_id.as_str()).collect();
    for span in &telemetry.ide_spans {
        assert!(
            !lane.contains_key(span.span_id.as_str()),
            "the IDE span {:?} takes the lane span id {}",
            span.name,
            span.span_id
        );
        assert_eq!(span.trace_id, trace, "the IDE span {:?} is in another trace", span.name);
        assert!(
            lookup_str(&span.attributes, attr::IDE_TRACE_ID).is_some_and(is_valid_trace_id),
            "the IDE span {:?} names no IDE trace",
            span.name
        );
        assert_eq!(
            lookup_str(&span.attributes, attr::SPAN_KIND),
            Some(IDE_SPAN_KIND),
            "the IDE span {:?}",
            span.name
        );
        assert!(
            lookup_str(&span.attributes, attr::SPAN_STATUS).is_some(),
            "the IDE span {:?} has no status",
            span.name
        );
        if ide.contains(span.parent_span_id.as_str()) {
            continue;
        }
        let parent = lane.get(span.parent_span_id.as_str()).unwrap_or_else(|| {
            panic!(
                "the IDE span {:?} names the parent {}, which the bundle does not hold",
                span.name, span.parent_span_id
            )
        });
        assert!(
            span.start_time_unix_nano >= parent.start_time_unix_nano && span.start_time_unix_nano <= parent.end_time_unix_nano,
            "the IDE span {:?} starts outside its lane parent {:?}",
            span.name,
            parent.name
        );
    }
}
