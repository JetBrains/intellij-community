use std::path::Path;

use bt_core::{Refusal, exit};
use pretty_assertions::assert_eq;

use super::{report, run};
use crate::testkit::fixture_session;

struct Answer {
    exit_code: u8,
    stdout: String,
    stderr: String,
}

fn invoke(repo_root: Option<&Path>, working_dir: &Path, argv: &[&str]) -> Answer {
    let mut stdout = Vec::new();
    let mut stderr = Vec::new();
    let exit_code = run(argv, repo_root, working_dir, &mut stdout, &mut stderr);
    Answer {
        exit_code,
        stdout: String::from_utf8_lossy(&stdout).into_owned(),
        stderr: String::from_utf8_lossy(&stderr).into_owned(),
    }
}

#[test]
fn a_launch_without_the_checkout_root_refuses() {
    let answer = invoke(None, Path::new("/"), &["welcome"]);
    assert_eq!(answer.exit_code, exit::USAGE);
    assert_eq!(answer.stdout, "");
    assert_eq!(
        answer.stderr,
        "BUILD_WORKSPACE_DIRECTORY is not set; run ./community/tools/startup-bench.cmd, which exports it\n"
    );
}

#[test]
fn a_json_refusal_is_one_envelope_on_stdout() {
    let mut stdout = Vec::new();
    let mut stderr = Vec::new();
    let refusal = Refusal::new("license_missing", exit::USAGE, "no license");
    assert_eq!(report(Err(refusal), true, &mut stdout, &mut stderr), exit::USAGE);
    let envelope: serde_json::Value = serde_json::from_slice(&stdout).expect("one JSON value");
    assert_eq!(
        envelope,
        serde_json::json!({"ok": false, "error": {"code": "license_missing", "message": "no license", "details": null}})
    );
    assert_eq!(String::from_utf8_lossy(&stderr), "no license\n");
}

#[test]
fn replay_prints_the_digest_or_the_envelope() {
    let (dir, session) = fixture_session();
    let session = session.display().to_string();
    let answer = invoke(None, dir.path(), &["replay", &session]);
    assert_eq!(answer.exit_code, exit::GREEN, "{}", answer.stderr);
    assert!(
        answer.stdout.starts_with("startup-bench welcome: //build:idea at 3f337093836d,"),
        "{}",
        answer.stdout
    );
    assert!(answer.stdout.trim_end().ends_with("/summary.json"), "{}", answer.stdout);

    let answer = invoke(None, dir.path(), &["replay", &session, "--json"]);
    assert_eq!(answer.exit_code, exit::GREEN, "{}", answer.stderr);
    let envelope: serde_json::Value = serde_json::from_str(&answer.stdout).expect("one JSON value");
    assert_eq!(envelope["ok"], true);
    assert_eq!(envelope["data"]["arms"]["nonModal"]["validRuns"], 1);
    assert!(envelope["data"]["arms"]["modal"]["summary"]["welcomeBecameVisible"]["median"].is_number());
    assert!(answer.stderr.contains("startup-bench welcome:"), "the digest goes to stderr");
    let summary: serde_json::Value =
        serde_json::from_str(&std::fs::read_to_string(Path::new(&session).join("summary.json")).expect("summary.json")).expect("JSON");
    assert_eq!(summary, envelope["data"]);
}

#[test]
fn a_session_without_a_valid_run_exits_with_usage() {
    let (dir, session) = fixture_session();
    std::fs::remove_file(session.join("modal-run-01/fus.jsonl")).expect("a removal");
    let answer = invoke(None, dir.path(), &["replay", &session.display().to_string()]);
    assert_eq!(answer.exit_code, exit::USAGE);
    assert!(answer.stdout.contains("modal run 1 failed: no fus.jsonl"), "{}", answer.stdout);
    assert!(
        answer
            .stderr
            .ends_with("no run passed the gate in the arm modal; see the reasons above\n"),
        "{}",
        answer.stderr
    );
}

#[test]
fn replay_of_another_directory_refuses() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let answer = invoke(None, dir.path(), &["replay", "."]);
    assert_eq!(answer.exit_code, exit::USAGE);
    assert!(
        answer.stderr.ends_with("is not a session directory: it has no session.json\n"),
        "{}",
        answer.stderr
    );
}
