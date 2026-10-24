use pretty_assertions::assert_eq;

use super::*;

#[test]
fn a_lease_get_line_gives_the_path_and_the_lease_id() {
    let text = r#"{"schema":2,"repository":"/r","pool":"/p","path":"/p/15/idea-1","lease_id":"208e","holder":"h","slot":"idea-114","reused":true}"#;
    assert_eq!(
        parse_get(text),
        Some(Leased {
            path: PathBuf::from("/p/15/idea-1"),
            lease_id: "208e".to_owned(),
        })
    );
    assert_eq!(parse_get(r#"{"schema":2}"#), None);
    assert_eq!(parse_get(r#"{"path":"","lease_id":"x"}"#), None);
    assert_eq!(parse_get("not json"), None);
}

#[test]
fn a_lease_status_names_the_live_leases() {
    let text = r#"{"schema":2,"slots":[{"slot":"a","path":"/p/1","state":"ready","lease":{"lease_id":"one","holder":"h"}},{"slot":"b","path":"/p/2","state":"free"}]}"#;
    assert!(status_has(text, "one"));
    assert!(!status_has(text, "two"));
    assert!(!status_has("garbage", "one"));
}
