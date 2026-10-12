//! The process-level tests of `vm`, the controller behind `vm.cmd`.
//!
//! The tests take the path of the binary from `CARGO_BIN_EXE_vm`. Cargo sets it when it compiles the test, and the
//! integration test that the rust-tools core declares sets it at run time. A pipe gets JSON, so each answer is one
//! envelope.

#![expect(clippy::tests_outside_test_module, reason = "a Cargo integration test has no #[cfg(test)] module")]

use std::ffi::OsString;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

use serde_json::Value;

fn vm() -> PathBuf {
    let path = std::env::var_os("CARGO_BIN_EXE_vm")
        .or_else(|| option_env!("CARGO_BIN_EXE_vm").map(OsString::from))
        .expect("cargo and the rust-tools core set CARGO_BIN_EXE_vm");
    // Bazel names the binary relative to the start directory of the test.
    std::path::absolute(path).unwrap()
}

/// A checkout with the fixture `bt.json` and its lane table, the files that `vm` reads before it parses.
fn checkout() -> tempfile::TempDir {
    let root = tempfile::tempdir().unwrap();
    for (path, text) in bt_core::fake::area_files() {
        let file = root.path().join(path);
        std::fs::create_dir_all(file.parent().unwrap()).unwrap();
        std::fs::write(file, text).unwrap();
    }
    root
}

/// Runs `vm` in the checkout `root` with `args` and no standard input, and answers the exit code, the standard output
/// and the standard error.
fn answer_in(root: &Path, args: &[&str]) -> (Option<i32>, String, String) {
    let output = Command::new(vm())
        .args(args)
        .env("BUILD_WORKSPACE_DIRECTORY", root)
        .stdin(Stdio::null())
        .output()
        .unwrap();
    let text = |bytes: Vec<u8>| String::from_utf8(bytes).unwrap();
    (output.status.code(), text(output.stdout), text(output.stderr))
}

/// Runs `vm` as [`answer_in`] does, in a checkout with the fixture areas.
fn answer(args: &[&str]) -> (Option<i32>, String, String) {
    answer_in(checkout().path(), args)
}

fn envelope(text: &str) -> Value {
    serde_json::from_str(text).unwrap_or_else(|error| panic!("not one JSON envelope ({error}): {text}"))
}

/// No command is the `usage` refusal with exit 2, one envelope on stderr and nothing on stdout.
#[test]
fn no_command_is_the_usage_refusal_with_exit_2() {
    let (exit, stdout, stderr) = answer(&[]);
    assert_eq!((exit, stdout.as_str()), (Some(2), ""), "{stderr}");
    let refused = envelope(&stderr);
    assert_eq!(
        (&refused["ok"], &refused["error"]["code"]),
        (&Value::Bool(false), &Value::from("usage")),
        "{stderr}"
    );
}

/// An unknown command is `unknown_command` with exit 2, and the envelope names it.
#[test]
fn an_unknown_command_is_refused_by_name() {
    let (exit, stdout, stderr) = answer(&["frobnicate"]);
    assert_eq!((exit, stdout.as_str()), (Some(2), ""), "{stderr}");
    let refused = envelope(&stderr);
    assert_eq!(
        (&refused["command"], &refused["error"]["code"]),
        (&Value::from("frobnicate"), &Value::from("unknown_command")),
        "{stderr}"
    );
}

/// `--help` is an answer: exit 0, and the usage in the success envelope on stdout.
#[test]
fn help_is_the_usage_in_the_success_envelope() {
    let (exit, stdout, stderr) = answer(&["--help"]);
    assert_eq!((exit, stderr.as_str()), (Some(0), ""));
    let answered = envelope(&stdout);
    assert_eq!(
        (&answered["ok"], &answered["command"]),
        (&Value::Bool(true), &Value::from("help")),
        "{stdout}"
    );
    assert!(
        answered["data"]["usage"].as_str().is_some_and(|usage| usage.contains("Usage")),
        "{stdout}"
    );
}

/// A checkout whose `bt.json` names no Air area is refused before the parse, so even `--help` is
/// `air_area_missing` with exit 65.
#[test]
fn a_checkout_without_the_air_area_is_refused() {
    let root = tempfile::tempdir().unwrap();
    let (exit, stdout, stderr) = answer_in(root.path(), &["--help"]);
    assert_eq!((exit, stdout.as_str()), (Some(65), ""), "{stderr}");
    assert_eq!(envelope(&stderr)["error"]["code"], Value::from("air_area_missing"), "{stderr}");
}
