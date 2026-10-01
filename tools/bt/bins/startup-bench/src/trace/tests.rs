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
