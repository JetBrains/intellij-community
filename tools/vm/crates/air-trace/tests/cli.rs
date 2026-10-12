//! The process-level tests of `air-trace`, the binary behind `trace.cmd`.
//!
//! The tests take the path of the binary from `CARGO_BIN_EXE_air-trace`. Cargo sets it when it compiles the test,
//! and the integration test that the rust-tools core declares sets it at run time.

#![expect(clippy::tests_outside_test_module, reason = "a Cargo integration test has no #[cfg(test)] module")]

use std::ffi::OsString;
use std::path::PathBuf;
use std::process::{Command, Stdio};

fn air_trace() -> PathBuf {
    let path = std::env::var_os("CARGO_BIN_EXE_air-trace")
        .or_else(|| option_env!("CARGO_BIN_EXE_air-trace").map(OsString::from))
        .expect("cargo and the rust-tools core set CARGO_BIN_EXE_air-trace");
    // Bazel names the binary relative to the start directory of the test.
    std::path::absolute(path).unwrap()
}

/// Runs `air-trace` with `args` and no standard input, and answers the exit code, the standard output and the
/// standard error.
fn answer(args: &[&str]) -> (Option<i32>, String, String) {
    let output = Command::new(air_trace()).args(args).stdin(Stdio::null()).output().unwrap();
    let text = |bytes: Vec<u8>| String::from_utf8(bytes).unwrap();
    (output.status.code(), text(output.stdout), text(output.stderr))
}

/// No subcommand is a usage error on stderr with exit 2, and `--help` is the usage on stdout with exit 0. No
/// command line exits with 78, which `trace.cmd` keeps for its own failures.
#[test]
fn the_usage_is_exit_2_on_stderr_and_help_is_exit_0_on_stdout() {
    let (exit, stdout, stderr) = answer(&[]);
    assert_eq!((exit, stdout.as_str()), (Some(2), ""), "{stderr}");
    assert!(stderr.contains("Usage: air-trace"), "{stderr}");

    let (exit, stdout, stderr) = answer(&["--help"]);
    assert_eq!((exit, stderr.as_str()), (Some(0), ""));
    for subcommand in ["pack", "serve", "plan"] {
        assert!(stdout.contains(subcommand), "{subcommand}: {stdout}");
    }
}

/// A refusal is one `air-trace <command>: <message>` line on stderr, and the exit of the refusal.
#[test]
fn a_refusal_is_one_line_and_its_exit() {
    let scratch = std::env::temp_dir().join(format!("air-trace-cli-{}", std::process::id()));
    let missing = scratch.join("missing");
    let missing = missing.to_str().unwrap();

    let (exit, stdout, stderr) = answer(&["pack", missing, &format!("{missing}.zip")]);
    assert_eq!((exit, stdout.as_str()), (Some(1), ""), "{stderr}");
    assert!(stderr.starts_with("air-trace pack: "), "{stderr}");
    assert_eq!(stderr.lines().count(), 1, "{stderr}");

    let (exit, stdout, stderr) = answer(&["plan", "--repo", missing, "flow-x"]);
    assert_eq!((exit, stdout.as_str()), (Some(1), ""), "{stderr}");
    assert!(stderr.starts_with("air-trace plan: the checkout "), "{stderr}");

    let (exit, stdout, stderr) = answer(&["serve", "--no-default-roots"]);
    assert_eq!(
        (exit, stdout.as_str(), stderr.as_str()),
        (Some(2), "", "air-trace serve: no roots to look in; pass --root DIR\n")
    );
}
