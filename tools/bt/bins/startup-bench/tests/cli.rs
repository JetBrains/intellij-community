//! The process tests of `startup-bench`: the help, a bad option, and `replay` over the fixture session.
//!
//! The tests read the path of the binary from `CARGO_BIN_EXE_startup-bench` at run time. Where the variable is absent,
//! each test skips with a message.

#![expect(clippy::tests_outside_test_module, reason = "a Cargo integration test has no #[cfg(test)] module")]

use std::path::{Path, PathBuf};
use std::process::{Command, Output};

fn binary() -> Option<PathBuf> {
    if let Some(path) = std::env::var_os("CARGO_BIN_EXE_startup-bench") {
        // Bazel names the binary relative to the start directory, and a test starts it in another directory.
        Some(std::path::absolute(path).expect("an absolute path"))
    } else {
        eprintln!("CARGO_BIN_EXE_startup-bench is not set; run `cargo test` to include the binary");
        None
    }
}

fn testdata_dir() -> PathBuf {
    std::env::var_os("BT_TESTDATA_DIR").map_or_else(
        || PathBuf::from(std::env::var_os("CARGO_MANIFEST_DIR").expect("cargo test sets CARGO_MANIFEST_DIR")).join("testdata"),
        PathBuf::from,
    )
}

fn copy_dir(from: &Path, to: &Path) {
    std::fs::create_dir_all(to).expect("a directory");
    for entry in std::fs::read_dir(from).expect("a listing") {
        let entry = entry.expect("an entry");
        let target = to.join(entry.file_name());
        if entry.path().is_dir() {
            copy_dir(&entry.path(), &target);
        } else {
            // New files: the runfiles of Bazel are read-only, and `fs::copy` keeps the mode.
            std::fs::write(&target, std::fs::read(entry.path()).expect("a fixture file")).expect("a copy");
        }
    }
}

fn run(binary: &Path, args: &[&str], dir: &Path) -> Output {
    Command::new(binary)
        .args(args)
        .current_dir(dir)
        .env_remove("BUILD_WORKSPACE_DIRECTORY")
        .output()
        .expect("the binary runs")
}

fn text(bytes: &[u8]) -> String {
    String::from_utf8_lossy(bytes).into_owned()
}

#[test]
fn help_names_the_commands() {
    let Some(binary) = binary() else { return };
    let dir = tempfile::tempdir().expect("a temporary directory");
    let output = run(&binary, &["--help"], dir.path());
    assert_eq!(output.status.code(), Some(0));
    let help = text(&output.stdout);
    for command in ["welcome", "replay", "open-project"] {
        assert!(help.contains(command), "{help}");
    }
    let output = run(&binary, &["welcome", "--help"], dir.path());
    assert!(text(&output.stdout).contains("--arm <ARM>"), "{}", text(&output.stdout));
}

#[test]
fn a_bad_option_refuses_with_exit_2() {
    let Some(binary) = binary() else { return };
    let dir = tempfile::tempdir().expect("a temporary directory");
    let output = run(&binary, &["welcome", "--arm", "sideways"], dir.path());
    assert_eq!(output.status.code(), Some(2));
    assert_eq!(text(&output.stdout), "");
    assert!(
        text(&output.stderr).contains("invalid value 'sideways' for '--arm <ARM>'"),
        "{}",
        text(&output.stderr)
    );
    let output = run(&binary, &["welcome"], dir.path());
    assert_eq!(output.status.code(), Some(2), "a launch without BUILD_WORKSPACE_DIRECTORY refuses");
}

#[test]
fn replay_of_the_fixture_session_prints_the_digest() {
    let Some(binary) = binary() else { return };
    let dir = tempfile::tempdir().expect("a temporary directory");
    copy_dir(&testdata_dir().join("session"), &dir.path().join("session"));
    let output = run(&binary, &["replay", "session"], dir.path());
    assert_eq!(output.status.code(), Some(0), "{}", text(&output.stderr));
    let digest = text(&output.stdout);
    assert!(digest.contains("welcome left toolbar first fill"), "{digest}");
    assert!(digest.trim_end().ends_with("session/summary.json"), "{digest}");
    assert!(dir.path().join("session/summary.json").exists());
}
