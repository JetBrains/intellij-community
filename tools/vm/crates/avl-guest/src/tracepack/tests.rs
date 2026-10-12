//! The trace verb: the argv the controller must get right, and that it packs with `air-trace pack`'s own code
//! rather than a copy of it.

use std::fs;
use std::path::Path;

use pretty_assertions::assert_eq;
use serde_json::Value;

use crate::testing::run_agent;

fn write_bundle(bundle: &Path, files: &[(&str, &str)]) {
    for (name, content) in files {
        let path = bundle.join(name);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, content).unwrap();
    }
}

/// A missing tree is a refusal under the verb's own code, which the controller records as the iteration's trace
/// error; it never changes the run's verdict.
#[test]
fn trace_pack_ready_refuses_a_missing_tree() {
    let root = tempfile::tempdir().unwrap();
    let missing = root.path().join("air-traces");
    let answered = run_agent(
        &[
            Path::new("trace-pack-ready"),
            &missing,
            &root.path().join("t.zip"),
            &root.path().join("ledger"),
        ],
        b"",
    );
    assert_eq!(answered.exit, 70);
    assert_eq!(answered.code(), "guest_trace_pack_ready_failed");
    let message = answered.failure()["error"]["message"].as_str().unwrap().to_owned();
    assert!(message.contains(&*missing.to_string_lossy()), "{message}");
}

/// Each finished bundle is packed once. A bundle without a manifest waits for `--all`, and a call with nothing new
/// writes no zip.
#[test]
fn trace_pack_ready_packs_each_finished_bundle_once() {
    let source = tempfile::tempdir().unwrap();
    let scenarios = source.path().join("run-1/AirNewSessionTest");
    write_bundle(&scenarios.join("finished"), &[("spans.jsonl", "{}\n"), ("bundle.json", "{}\n")]);
    write_bundle(&scenarios.join("running"), &[("spans.jsonl", "{}\n")]);
    let out = tempfile::tempdir().unwrap();
    let ledger = out.path().join("ledger");
    let call = |name: &str, all: bool| -> Value {
        let destination = out.path().join(name);
        let mut args = vec![
            "trace-pack-ready".to_owned(),
            source.path().to_string_lossy().into_owned(),
            destination.to_string_lossy().into_owned(),
            ledger.to_string_lossy().into_owned(),
        ];
        if all {
            args.push("--all".to_owned());
        }
        let answered = run_agent(&args, b"");
        assert_eq!(answered.exit, 0, "{}", answered.stderr);
        answered.document()["data"].clone()
    };

    let first = call("001.zip", false);
    assert_eq!(first["packed"], serde_json::json!(["run-1/AirNewSessionTest/finished"]));

    let second = call("002.zip", false);
    assert_eq!(second.get("packed"), None, "a call with nothing new packed {second}");
    assert_eq!(second["destination"], "", "nothing new is an empty destination on the wire");
    assert!(!out.path().join("002.zip").exists(), "a call with nothing new wrote a zip");

    let third = call("003.zip", true);
    assert_eq!(third["packed"], serde_json::json!(["run-1/AirNewSessionTest/running"]));
    assert_eq!(third["entries"], 1, "the zip holds files outside the bundle it packed: {third}");

    assert_eq!(
        fs::read_to_string(&ledger).unwrap(),
        "run-1/AirNewSessionTest/finished\nrun-1/AirNewSessionTest/running\n"
    );
}

#[test]
fn trace_pack_ready_refuses_a_miswired_argv() {
    let cases: [&[&str]; 3] = [&["/a", "/b"], &["/a", "/b", "ledger"], &["/a", "/b", "/c", "--everything"]];
    for argv in cases {
        let args: Vec<&str> = std::iter::once("trace-pack-ready").chain(argv.iter().copied()).collect();
        let answered = run_agent(&args, b"");
        assert_eq!((answered.exit, answered.code()), (64, "usage".to_owned()), "{argv:?}");
    }
}
