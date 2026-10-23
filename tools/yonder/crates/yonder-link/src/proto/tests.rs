use pretty_assertions::assert_eq;

use super::*;

/// The JSON shape is the contract between two builds, so the tests pin it.
#[test]
fn a_request_and_a_reply_have_a_tagged_json_shape() {
    let request = Request::Follow {
        run: "1-2".to_owned(),
        offset: 7,
    };
    let json = serde_json::to_string(&request).expect("json");
    assert_eq!(json, r#"{"request":"follow","run":"1-2","offset":7}"#);
    assert_eq!(serde_json::from_str::<Request>(&json).expect("parsed"), request);
    assert_eq!(serde_json::to_string(&Request::Runs).expect("json"), r#"{"request":"runs"}"#);

    let reply = Reply::Output {
        stream: Stream::Stderr,
        text: "a line".to_owned(),
        end: 9,
    };
    assert_eq!(
        serde_json::to_string(&reply).expect("json"),
        r#"{"reply":"output","stream":"stderr","text":"a line","end":9}"#
    );
}

#[test]
fn a_start_carries_the_run_spec_inline() {
    let spec = RunSpec {
        run: "1-2".to_owned(),
        repo: "~/projects/idea-1".to_owned(),
        holder: "yonder-0123".to_owned(),
        commit: "abc".to_owned(),
        reference: "refs/yonder/1-2".to_owned(),
        command: vec!["./community/tools/bt.cmd".to_owned(), "--lane".to_owned(), "fast".to_owned()],
        fetch: vec!["out/bt-runs".to_owned()],
        idle_hours: 8,
    };
    let json = serde_json::to_string(&Request::Start(spec.clone())).expect("json");
    assert!(json.starts_with(r#"{"request":"start","run":"1-2","#), "{json}");
    assert_eq!(serde_json::from_str::<Request>(&json).expect("parsed"), Request::Start(spec));
}

#[test]
fn a_run_id_names_one_directory() {
    for good in ["1792789527302-61884", "a.b_c-d"] {
        assert!(is_run_id(good), "{good}");
    }
    // git refuses `a..b`, `x.lock` and `x.` as the last part of a ref.
    for bad in ["", ".", "..", "a/b", "a b", "a..b", "x.lock", "x.", &"x".repeat(129)] {
        assert!(!is_run_id(bad), "{bad:?}");
    }
}

#[test]
fn the_phases_of_a_run_and_a_run_info_without_them_parse() {
    let phases = Reply::Phases {
        phases: vec![Phase {
            name: "lease".to_owned(),
            ms: 1200,
        }],
    };
    let json = serde_json::to_string(&phases).expect("json");
    assert_eq!(json, r#"{"reply":"phases","phases":[{"name":"lease","ms":1200}]}"#);
    assert_eq!(serde_json::from_str::<Reply>(&json).expect("parsed"), phases);
    let info: RunInfo = serde_json::from_str(r#"{"run":"1-2","holder":"h","commit":"c","command":["true"],"started_ms":1,"exit":null}"#)
        .expect("an info of schema 1");
    assert!(info.phases.is_empty());
    assert!(info.fetch.is_empty(), "an agent before the fetch directories sends none");
    assert!(BASE_REFS.starts_with("refs/yonder/") && BASE_REFS.ends_with('/'));
}
