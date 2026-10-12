//! The process-level tests of `vm-guest-agent`, the binary that the controller runs inside a worker over an exec
//! channel.
//!
//! The tests take the path of the binary from `CARGO_BIN_EXE_vm-guest-agent`. Cargo sets it when it compiles the
//! test, and the integration test that the rust-tools core declares sets it at run time.

#![expect(clippy::tests_outside_test_module, reason = "a Cargo integration test has no #[cfg(test)] module")]

use std::ffi::OsString;
use std::path::PathBuf;
use std::process::{Command, Stdio};

fn agent() -> PathBuf {
    let path = std::env::var_os("CARGO_BIN_EXE_vm-guest-agent")
        .or_else(|| option_env!("CARGO_BIN_EXE_vm-guest-agent").map(OsString::from))
        .expect("cargo and the rust-tools core set CARGO_BIN_EXE_vm-guest-agent");
    // Bazel names the binary relative to the start directory of the test.
    std::path::absolute(path).unwrap()
}

/// Runs the agent with `args` and no standard input, and answers the exit code, the standard output and the
/// standard error.
fn answer(args: &[&str]) -> (Option<i32>, String, String) {
    let output = Command::new(agent()).args(args).stdin(Stdio::null()).output().unwrap();
    let text = |bytes: Vec<u8>| String::from_utf8(bytes).unwrap();
    (output.status.code(), text(output.stdout), text(output.stderr))
}

/// A verb the agent does not have is the `usage` envelope on stderr with exit 64, which the host reads as "this
/// agent is older than its controller". Nothing goes to stdout, so the host never reads it as a reply.
#[test]
fn an_unknown_verb_is_the_usage_envelope_with_exit_64() {
    let (exit, stdout, stderr) = answer(&["no-such-verb"]);
    assert_eq!((exit, stdout.as_str()), (Some(64), ""), "{stderr}");
    let envelope: serde_json::Value = serde_json::from_str(&stderr).expect("one JSON envelope on stderr");
    assert_eq!(
        (&envelope["ok"], &envelope["command"], &envelope["error"]["code"]),
        (
            &serde_json::json!(false),
            &serde_json::json!("no-such-verb"),
            &serde_json::json!("usage")
        ),
        "{stderr}"
    );
}

/// `--help` is the usage on stdout with exit 0, and names the agent.
#[test]
fn help_is_the_usage_on_stdout_with_exit_0() {
    let (exit, stdout, stderr) = answer(&["--help"]);
    assert_eq!((exit, stderr.as_str()), (Some(0), ""));
    assert!(stdout.contains("vm-guest-agent"), "{stdout}");
}

/// `contract` answers one JSON document on stdout with exit 0: the wire of this binary and the digest of its bytes.
#[test]
fn contract_answers_one_document_on_stdout() {
    let (exit, stdout, stderr) = answer(&["contract"]);
    assert_eq!((exit, stderr.as_str()), (Some(0), ""));
    assert_eq!(stdout.lines().count(), 1, "{stdout}");
    let contract: serde_json::Value = serde_json::from_str(&stdout).expect("one JSON document");
    assert!(contract.is_object(), "{stdout}");
}
