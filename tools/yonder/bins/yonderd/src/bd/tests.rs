use super::*;

#[test]
fn a_lease_status_names_the_live_leases() {
    let text = r#"{"schema":2,"slots":[{"slot":"a","path":"/p/1","state":"ready","lease":{"lease_id":"one","holder":"h"}},{"slot":"b","path":"/p/2","state":"free"}]}"#;
    assert!(status_has(text, "one"));
    assert!(!status_has(text, "two"));
    assert!(!status_has("garbage", "one"));
}
