use pretty_assertions::assert_eq;

use super::parse;

const COMPLETE: &str = r#"{"data":[{"traceID":"t","processes":{"p1":{"serviceName":"IDE","tags":[]}},"spans":[{"traceID":"t","spanID":"1","operationName":"bootstrap","processID":"p1","startTime":1000,"duration":500,"tags":[{"key":"k","type":"string","value":"v"}]},{"traceID":"t","spanID":"2","operationName":"x","processID":"p1","startTime":3000,"duration":7}]}]}"#;

#[test]
fn reads_a_complete_trace() {
    let trace = parse(COMPLETE).expect("a trace");
    assert!(!trace.truncated);
    assert_eq!(trace.spans.len(), 2);
    assert_eq!(trace.origin_us(), Some(1000));
    assert_eq!(trace.first_after("x", 2000).map(|span| span.duration), Some(7));
    assert_eq!(trace.first_after("x", 3001), None);
}

#[test]
fn repairs_a_trace_that_ends_inside_a_span() {
    let cut = COMPLETE.find(r#""operationName":"x""#).expect("the second span");
    let truncated = &COMPLETE[..cut + 30];
    let trace = parse(truncated).expect("a repaired trace");
    assert!(trace.truncated);
    assert_eq!(trace.spans.len(), 1);
    assert_eq!(trace.spans[0].operation_name, "bootstrap");
}

#[test]
fn refuses_another_document() {
    let error = parse(r#"{"spans":[]}"#).expect_err("a refusal");
    assert!(
        format!("{error:#}").starts_with("not a Jaeger trace: missing field `data`"),
        "{error:#}"
    );
}

#[test]
fn a_span_keeps_its_tags_and_knows_its_helper_twin() {
    let text = r#"{"data":[{"spans":[
        {"operationName":"bootstrap","startTime":1000000,"duration":5},
        {"operationName":"run activity","startTime":1250050,"duration":9,"tags":[
            {"key":"class","type":"string","value":"a.B"},{"key":"plugin","type":"string","value":"p"},
            {"key":"synchronous","type":"boolean","value":true}]},
        {"operationName":"run activity: scheduled","startTime":1250000,"duration":1}]}]}"#;
    let trace = parse(text).expect("a trace");
    let activity = &trace.spans[1];
    assert_eq!(activity.tag("class"), Some("a.B"));
    assert_eq!(activity.tag("plugin"), Some("p"));
    assert_eq!(activity.tag("synchronous"), None, "a tag that is not a string has no string value");
    assert_eq!(activity.tag("absent"), None);
    assert_eq!(trace.spans[0].tags, []);
    assert!(!activity.is_helper());
    assert!(trace.spans[2].is_helper());
    let origin = trace.origin_us().expect("an origin");
    assert_eq!(activity.offset_ms(origin).to_string(), "250.1");
    assert_eq!(trace.spans[0].offset_ms(origin).to_string(), "0");
}
