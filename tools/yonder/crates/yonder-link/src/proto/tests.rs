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
    for bad in ["", ".", "..", "a/b", "a b", &"x".repeat(129)] {
        assert!(!is_run_id(bad), "{bad:?}");
    }
}
