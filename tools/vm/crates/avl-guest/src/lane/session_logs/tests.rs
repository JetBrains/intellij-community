use std::fs;
use std::path::{Path, PathBuf};

use avl_wire::daemon::SessionLog;
use avl_wire::session_log::{NamedLog, pack};
use pretty_assertions::assert_eq;
use serde_json::json;
use tempfile::TempDir;

use super::*;

/// A store of one packed log, `thread`, under the digest `d1`.
fn store_with_one_log(root: &Path) -> PathBuf {
    let thread = root.join("logs/thread");
    fs::create_dir_all(thread.join("outputs")).unwrap();
    fs::write(thread.join("events.jsonl"), "{}\n").unwrap();
    fs::write(thread.join("outputs/o.txt"), "o").unwrap();
    let store = root.join("store");
    fs::create_dir_all(&store).unwrap();
    let log = NamedLog {
        label: "thread".to_owned(),
        events: thread.join("events.jsonl"),
    };
    pack(&log, &store.join("d1")).unwrap();
    store
}

fn stored_in(store: &Path) -> impl Fn(&str) -> Result<PathBuf, String> + '_ {
    move |sha256: &str| {
        let path = store.join(sha256);
        if path.is_file() {
            Ok(path)
        } else {
            Err(format!("no file {sha256}"))
        }
    }
}

// The logs of the request go into the context under their labels, a copy of an earlier run goes first, and the zip
// leaves the store. The copies leave the context when the run ends.
#[test]
fn the_logs_go_into_the_context_and_leave_it_when_the_run_ends() {
    let root = TempDir::new().unwrap();
    let store = store_with_one_log(root.path());
    let context = root.path().join("context");
    let leftover = context.join("air-real-session-logs/old/events.jsonl");
    fs::create_dir_all(leftover.parent().unwrap()).unwrap();
    fs::write(&leftover, "old").unwrap();
    let logs = [SessionLog {
        label: "thread".to_owned(),
        sha256: "d1".to_owned(),
    }];
    let staged = stage(Some(&context), &logs, &stored_in(&store)).unwrap();
    let events = context.join("air-real-session-logs/thread/events.jsonl");
    assert_eq!(
        staged.logs,
        [StagedLog {
            label: "thread".to_owned(),
            events: events.clone(),
        }]
    );
    assert_eq!(
        fs::read_to_string(context.join("air-real-session-logs/thread/outputs/o.txt")).unwrap(),
        "o"
    );
    assert!(!leftover.exists(), "the copy of an earlier run went first");
    assert!(!store.join("d1").exists(), "the zip left the store");
    assert_eq!(
        runner_value(&staged.logs),
        Some(json!([{"label": "thread", "eventsPath": events.display().to_string()}]))
    );
    drop(staged);
    assert!(!context.join("air-real-session-logs").exists());
}

// A run with no log, or with no context, stages nothing and answers no runner value. A label that is no plain name and
// a digest that the store does not hold are refused.
#[test]
fn no_log_no_context_or_a_bad_label_stages_nothing() {
    let root = TempDir::new().unwrap();
    let store = store_with_one_log(root.path());
    let context = root.path().join("context");
    let one = |label: &str, sha256: &str| {
        vec![SessionLog {
            label: label.to_owned(),
            sha256: sha256.to_owned(),
        }]
    };
    assert_eq!(stage(Some(&context), &[], &stored_in(&store)).unwrap().logs, []);
    assert_eq!(stage(None, &one("thread", "d1"), &stored_in(&store)).unwrap().logs, []);
    assert!(store.join("d1").exists(), "a run with no context leaves the store alone");
    assert_eq!(runner_value(&[]), None);
    assert_eq!(
        stage(Some(&context), &one("../escape", "d1"), &stored_in(&store)).unwrap_err(),
        "the recorded session label '../escape' is no plain name"
    );
    assert_eq!(
        stage(Some(&context), &one("thread", "d2"), &stored_in(&store)).unwrap_err(),
        "no file d2"
    );
}
