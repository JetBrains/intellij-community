//! The process-level tests of `air-trace-record`, the binary that the lane JVM starts with no argument.
//!
//! The tests take the path of the binary from `CARGO_BIN_EXE_air-trace-record`. Cargo sets it when it compiles the
//! test, and the integration test that the rust-tools core declares sets it at run time.

#![expect(clippy::tests_outside_test_module, reason = "a Cargo integration test has no #[cfg(test)] module")]

use std::ffi::OsString;
use std::io::Write;
use std::path::PathBuf;
use std::process::{Command, Stdio};

fn recorder() -> PathBuf {
    let path = std::env::var_os("CARGO_BIN_EXE_air-trace-record")
        .or_else(|| option_env!("CARGO_BIN_EXE_air-trace-record").map(OsString::from))
        .expect("cargo and the rust-tools core set CARGO_BIN_EXE_air-trace-record");
    // Bazel names the binary relative to the start directory of the test.
    std::path::absolute(path).unwrap()
}

/// Starts the recorder with `args`, writes `input` to its standard input, closes it, and answers the exit code, the
/// standard output and the standard error.
fn record(args: &[&str], input: &[u8]) -> (Option<i32>, String, String) {
    let mut child = Command::new(recorder())
        .args(args)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .unwrap();
    let mut stdin = child.stdin.take().unwrap();
    // A refused command line exits before it reads, so the write can meet a closed pipe.
    let _ = stdin.write_all(input);
    drop(stdin);
    let output = child.wait_with_output().unwrap();
    let text = |bytes: Vec<u8>| String::from_utf8(bytes).unwrap();
    (output.status.code(), text(output.stdout), text(output.stderr))
}

/// An older lane passed `record`, and no refusal exits with 78, which `trace.cmd` keeps for its own failures.
#[test]
fn an_argument_is_refused_with_one_line_and_exit_2() {
    for argument in ["record", "--help"] {
        assert_eq!(
            record(&[argument], b""),
            (
                Some(2),
                String::new(),
                format!("ERROR: air-trace-record takes no argument, but got {argument:?}\n")
            )
        );
    }
}

#[test]
fn an_empty_input_ends_the_session_with_exit_0() {
    assert_eq!(record(&[], b""), (Some(0), String::new(), String::new()));
}

/// The acks leave on standard output, one JSON object per line, and the diagnostics on standard error. A line before
/// the hello is refused, and the first line is answered as a hello, because that is the one ack the lane waits for
/// then.
#[test]
fn a_done_before_the_hello_is_answered_as_a_refused_hello() {
    assert_eq!(
        record(&[], b"{\"op\":\"done\",\"status\":\"passed\"}\n"),
        (
            Some(0),
            "{\"ok\":false,\"line\":1,\"capture\":\"none\",\"video\":\"none\",\"reason\":\"a done before the hello\"}\n".to_owned(),
            "air-trace-record: line 1 refused: a done before the hello\n".to_owned()
        )
    );
}
