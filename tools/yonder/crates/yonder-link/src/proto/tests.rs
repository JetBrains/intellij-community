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
        ..RunSpec::default()
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

#[test]
fn the_optional_fields_of_a_start_stay_out_of_the_json_and_an_old_spec_parses() {
    let old = r#"{"request":"start","run":"1-2","repo":"~/r","holder":"h","commit":"c","reference":"refs/yonder/1-2","command":["true"],"fetch":[],"idle_hours":8}"#;
    let Request::Start(spec) = serde_json::from_str::<Request>(old).expect("a start of an older client") else {
        panic!("not a start");
    };
    assert_eq!((spec.nice, spec.outputs, spec.changes), (None, false, false));
    assert_eq!(serde_json::to_string(&Request::Start(spec.clone())).expect("json"), old);

    let new = RunSpec {
        nice: Some(10),
        outputs: true,
        changes: true,
        ..spec
    };
    let json = serde_json::to_string(&Request::Start(new.clone())).expect("json");
    assert!(
        json.ends_with(r#""idle_hours":8,"nice":10,"outputs":true,"changes":true}"#),
        "{json}"
    );
    assert_eq!(serde_json::from_str::<Request>(&json).expect("parsed"), Request::Start(new));
}

#[test]
fn a_hello_without_features_and_a_file_without_a_kind_parse_as_an_older_agent_sends_them() {
    let hello: Reply =
        serde_json::from_str(r#"{"reply":"hello","schema_version":2,"agent_version":"0.1.0","os":"macos","arch":"aarch64"}"#)
            .expect("a hello of an older agent");
    assert!(matches!(&hello, Reply::Hello { features, .. } if features.is_empty()), "{hello:?}");

    let file: Reply = serde_json::from_str(r#"{"reply":"file","path":"out/a","size":1,"executable":false}"#).expect("a file");
    assert!(
        matches!(
            &file,
            Reply::File {
                kind: FileKind::Staged,
                ..
            }
        ),
        "{file:?}"
    );
    assert_eq!(
        serde_json::to_string(&file).expect("json"),
        r#"{"reply":"file","path":"out/a","size":1,"executable":false}"#
    );

    let output = Reply::File {
        path: "tests/results/a.txt".to_owned(),
        size: 1,
        executable: false,
        kind: FileKind::Output {
            root: "tests/results".to_owned(),
        },
    };
    let json = serde_json::to_string(&output).expect("json");
    assert!(json.ends_with(r#""kind":{"output":{"root":"tests/results"}}}"#), "{json}");
    assert_eq!(serde_json::from_str::<Reply>(&json).expect("parsed"), output);
    let changes = r#"{"reply":"file","path":"changes.patch","size":3,"executable":false,"kind":"changes"}"#;
    assert!(matches!(
        serde_json::from_str::<Reply>(changes).expect("parsed"),
        Reply::File {
            kind: FileKind::Changes,
            ..
        }
    ));
}

#[test]
fn a_hello_of_an_older_agent_has_no_binary_and_a_new_hello_names_it() {
    let old = r#"{"reply":"hello","schema_version":2,"agent_version":"0.1.0","os":"macos","arch":"aarch64","features":["config"]}"#;
    let hello: Reply = serde_json::from_str(old).expect("a hello of an agent before the upgrade");
    assert!(
        matches!(&hello, Reply::Hello { binary_sha256: None, features, .. } if *features == ["config"]),
        "{hello:?}"
    );
    assert_eq!(serde_json::to_string(&hello).expect("json"), old);

    let new = Reply::hello("0.1.0", Some("ab".repeat(32)));
    let json = serde_json::to_string(&new).expect("json");
    assert!(json.ends_with(&format!(r#""binary_sha256":"{}"}}"#, "ab".repeat(32))), "{json}");
    assert_eq!(serde_json::from_str::<Reply>(&json).expect("parsed"), new);
    let Reply::Hello { features, .. } = new else {
        panic!("not a hello");
    };
    assert_eq!(
        features.contains(&feature::UPGRADE.to_owned()),
        cfg!(target_os = "macos"),
        "{features:?}"
    );
}

#[test]
fn an_upgrade_and_its_reply_have_a_tagged_json_shape() {
    let request = Request::Upgrade {
        size: 9,
        sha256: "cd".repeat(32),
    };
    let json = serde_json::to_string(&request).expect("json");
    assert_eq!(json, format!(r#"{{"request":"upgrade","size":9,"sha256":"{}"}}"#, "cd".repeat(32)));
    assert_eq!(serde_json::from_str::<Request>(&json).expect("parsed"), request);

    let reply = Reply::Upgraded {
        previous_sha256: "ab".repeat(32),
        sha256: "cd".repeat(32),
        version: "0.1.0".to_owned(),
    };
    let json = serde_json::to_string(&reply).expect("json");
    assert!(json.starts_with(r#"{"reply":"upgraded","previous_sha256":"#), "{json}");
    assert_eq!(serde_json::from_str::<Reply>(&json).expect("parsed"), reply);
}
