//! `bt`'s entry point suite. Hermetic: no bazel, no checkout, no network. It covers exactly what this module decides
//! for itself: which descriptor each half of an answer goes to, how a refusal is rendered, and the refusal for a
//! missing checkout root.

use std::path::Path;

use bt_core::Refusal;
use pretty_assertions::assert_eq;

use super::{PROGRAM, report, run};
use crate::execute::{JsonFailure, JsonPayload, Outcome};
use bt_core::exit;
use bt_core::result::RunStatus;

/// One invocation's two streams and its exit code, which is `bt`'s whole observable surface.
#[derive(Debug)]
struct Answer {
    exit_code: u8,
    stdout: String,
    stderr: String,
}

/// Runs `bt` over explicit writers. The checkout root is a temporary directory rather than the real one: an empty
/// tree resolves no selector, so every case here refuses before anything could reach bazel.
fn invoke(repo_root: Option<&Path>, argv: &[&str]) -> Answer {
    let mut stdout = Vec::new();
    let mut stderr = Vec::new();
    let exit_code = run(argv, repo_root, &mut stdout, &mut stderr);
    Answer {
        exit_code,
        stdout: String::from_utf8_lossy(&stdout).into_owned(),
        stderr: String::from_utf8_lossy(&stderr).into_owned(),
    }
}

/// Renders an answer `bt` could have given, without running one.
fn deliver(answer: Result<Outcome, Refusal>) -> Answer {
    let mut stdout = Vec::new();
    let mut stderr = Vec::new();
    let exit_code = report(answer, &mut stdout, &mut stderr);
    Answer {
        exit_code,
        stdout: String::from_utf8_lossy(&stdout).into_owned(),
        stderr: String::from_utf8_lossy(&stderr).into_owned(),
    }
}

fn payload() -> JsonPayload {
    JsonPayload {
        status: RunStatus::Fail,
        exit_code: exit::TEST_FAILED,
        duration_ms: 5000,
        cached_targets: 0,
        ran_targets: 0,
        targets: Vec::new(),
        failures: vec![JsonFailure {
            class_name: "AgentThreadIdentityTest".to_owned(),
            name: "resolvesWorktreeCwd".to_owned(),
            failure_type: None,
            message: Some(r#"expected: <"/repo/wt"> but was: <"/repo"> & nothing else"#.to_owned()),
            frames: Vec::new(),
            frames_omitted: 0,
        }],
        flaky: Vec::new(),
        errors: Vec::new(),
        degraded: false,
    }
}

fn usage_refusal(message: &str) -> Refusal {
    Refusal::new("usage", exit::USAGE, message)
}

// --- the two descriptors -----------------------------------------------------------------------------------------

/// The `--json` split is the contract a caller automates against: one JSON object on stdout whether the run passed
/// or failed, and the digest out of the way on stderr.
#[test]
fn the_json_payload_goes_to_stdout_and_the_digest_to_stderr() {
    let answer = deliver(Ok(Outcome {
        exit_code: exit::TEST_FAILED,
        text: "1 failed".to_owned(),
        json: Some(payload()),
    }));
    assert_eq!(answer.exit_code, exit::TEST_FAILED);
    assert!(
        answer.stdout.starts_with(r#"{"status":"FAIL""#),
        "{}",
        answer.stdout
    );
    assert_eq!(answer.stdout.matches('\n').count(), 1, "{}", answer.stdout);
    let decoded: serde_json::Value =
        serde_json::from_str(&answer.stdout).expect("stdout is one JSON object");
    assert_eq!(decoded["failures"][0]["name"], "resolvesWorktreeCwd");
    assert_eq!(answer.stderr, "1 failed\n");
}

/// An empty digest writes nothing at all, rather than a blank line a caller reading stderr would have to ignore.
#[test]
fn an_empty_digest_leaves_stderr_untouched() {
    let answer = deliver(Ok(Outcome {
        exit_code: exit::GREEN,
        text: String::new(),
        json: Some(payload()),
    }));
    assert_eq!(answer.stderr, "");
    assert!(!answer.stdout.is_empty(), "the payload is still written");
}

/// Without `--json` the digest *is* the answer, so it is on stdout, which is also why nothing else may ever be
/// written there.
#[test]
fn without_json_the_digest_is_the_answer_on_stdout() {
    let answer = deliver(Ok(Outcome {
        exit_code: exit::GREEN,
        text: "12 tests, 0 failed".to_owned(),
        json: None,
    }));
    assert_eq!(answer.exit_code, exit::GREEN);
    assert_eq!(answer.stdout, "12 tests, 0 failed\n");
    assert_eq!(answer.stderr, "");
}

// --- refusals ----------------------------------------------------------------------------------------------------

/// A refusal is a bare message line and `bt`'s own exit code: no envelope, and stdout untouched. An agent's script
/// reads the message, and a caller reading stdout for a payload must get nothing rather than a second object.
#[test]
fn a_refusal_is_a_message_line_and_not_the_controller_envelope() {
    let answer = deliver(Err(usage_refusal("Unknown option: --nonsense")));
    assert_eq!(answer.exit_code, exit::USAGE);
    assert_eq!(answer.stdout, "");
    assert_eq!(answer.stderr, "Unknown option: --nonsense\n");
    assert!(
        !answer.stderr.contains("schemaVersion"),
        "{}",
        answer.stderr
    );
    assert!(!answer.stderr.contains(r#""ok""#), "{}", answer.stderr);
}

// --- the checkout root -------------------------------------------------------------------------------------------

/// Started without the variable the wrapper exports, `bt` refuses and names the wrapper. It does not search upwards:
/// every path it builds is relative to the checkout root, so a guess answers "no such test" for a tree that is there.
#[test]
fn a_missing_checkout_root_is_refused_and_names_the_wrapper() {
    for root in [None, Some(Path::new(""))] {
        let answer = invoke(root, &["AgentThreadCliTest"]);
        assert_eq!(answer.exit_code, exit::USAGE, "{answer:?}");
        assert_eq!(answer.stdout, "");
        assert!(
            answer.stderr.contains("BUILD_WORKSPACE_DIRECTORY"),
            "{}",
            answer.stderr
        );
        assert!(answer.stderr.contains(PROGRAM), "{}", answer.stderr);
    }
}

/// `--help` is answered before anything reads the filesystem, and it is a success, so it belongs on stdout like any
/// other digest. A bare `bt` prints the same text at exit 2.
#[test]
fn help_is_written_to_stdout() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let helped = invoke(Some(dir.path()), &["--help"]);
    assert_eq!(helped.exit_code, exit::GREEN);
    assert!(helped.stdout.contains("Usage:"), "{}", helped.stdout);
    assert_eq!(helped.stderr, "");

    let bare = invoke(Some(dir.path()), &[]);
    assert_eq!(bare.exit_code, exit::USAGE);
    assert!(bare.stdout.contains("Usage:"), "{}", bare.stdout);
}

/// A selector that resolves to nothing refuses without spawning anything, which is what makes an empty checkout root
/// a usable fixture here: an unresolvable name never reaches bazel.
#[test]
fn an_unresolvable_selector_refuses_without_reaching_bazel() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let answer = invoke(Some(dir.path()), &["AgentThreadCliTest"]);
    assert_eq!(answer.exit_code, exit::USAGE, "{answer:?}");
    assert_eq!(answer.stdout, "");
    assert!(
        answer.stderr.contains("AgentThreadCliTest"),
        "{}",
        answer.stderr
    );
}
