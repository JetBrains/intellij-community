use pretty_assertions::assert_eq;

use super::parse;

const REPORT: &str = r#"{"version":"38","items":[{"n":"bootstrap","s":0,"d":166,"t":"d 1"}],"traceEvents":[{"name":"e","ts":5,"ph":"i"}],"classLoading":{"time":10,"count":20},"plugins":[{"id":"a","classCount":3,"classLoadingEdtTime":4,"modules":[{"name":"m","classCount":1,"classLoadingEdtTime":0}]},{"id":"b","classCount":1,"classLoadingEdtTime":2}],"totalDuration":3930}"#;

#[test]
fn reads_version_38() {
    let stats = parse(REPORT).expect("a report");
    assert_eq!(stats.item("bootstrap").map(|item| item.duration), Some(166));
    assert_eq!(stats.edt_class_loading_ms(), 6);
    assert_eq!(stats.total_duration, 3930);
    assert_eq!(stats.trace_events[0].ts, 5);
}

#[test]
fn refuses_another_version_and_another_shape_by_name() {
    let error = parse(&REPORT.replace("\"38\"", "\"39\"")).expect_err("a refusal");
    assert_eq!(format!("{error:#}"), "version 39 is not supported, only 38");
    let error = parse(r#"{"items":[]}"#).expect_err("a refusal");
    assert_eq!(format!("{error:#}"), "no `version` field");
    let error = parse(r#"{"version":"38","items":[]}"#).expect_err("a refusal");
    assert!(
        format!("{error:#}").starts_with("not the shape of version 38: missing field"),
        "{error:#}"
    );
}
