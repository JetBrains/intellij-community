//! The process-level tests of `air-local-app`, against a fake IDE that serves the local peer handshake v2 on a socket.
//!
//! The tests take the path of the binary from `CARGO_BIN_EXE_air-local-app`. Cargo sets it when it compiles the test,
//! and the integration test that the rust-tools core declares sets it at run time.

#![expect(clippy::tests_outside_test_module, reason = "a Cargo integration test has no #[cfg(test)] module")]

use std::ffi::OsString;
use std::io::{BufRead, BufReader, Write};
use std::os::unix::net::UnixListener;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::thread;

fn local_app() -> PathBuf {
    let path = std::env::var_os("CARGO_BIN_EXE_air-local-app")
        .or_else(|| option_env!("CARGO_BIN_EXE_air-local-app").map(OsString::from))
        .expect("cargo and the rust-tools core set CARGO_BIN_EXE_air-local-app");
    // Bazel names the binary relative to the start directory of the test.
    std::path::absolute(path).unwrap()
}

/// A directory for the socket. The path of a Unix-domain socket is limited to about 100 bytes, and the temporary
/// directory of a Bazel test is longer, so the directory is in `/tmp`.
fn socket_directory() -> tempfile::TempDir {
    tempfile::Builder::new().prefix("air-local-app-").tempdir_in("/tmp").unwrap()
}

/// Starts the local app, checks its ready line, writes `order` as its one input line, and answers the exit code, the
/// lines of its standard output after the ready line, and its standard error.
fn send(order: &str) -> (Option<i32>, Vec<String>, String) {
    let mut child = Command::new(local_app())
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .unwrap();
    let mut stdout = BufReader::new(child.stdout.take().unwrap());
    let mut ready = String::new();
    stdout.read_line(&mut ready).unwrap();
    assert_eq!(ready, format!("{{\"pid\":{}}}\n", child.id()));
    let mut stdin = child.stdin.take().unwrap();
    stdin.write_all(format!("{order}\n").as_bytes()).unwrap();
    let lines = stdout.lines().map(Result::unwrap).collect();
    let output = child.wait_with_output().unwrap();
    (output.status.code(), lines, String::from_utf8(output.stderr).unwrap())
}

/// Serves one connection on `socket` as the IDE does: it reads the opener, answers `greeting`, and reads the envelope.
/// Then it writes `replies` and closes the connection. Answers the opener and the envelope that the local app wrote.
fn serve_once(socket: &Path, greeting: &'static str, replies: &'static [&'static str]) -> thread::JoinHandle<(String, String)> {
    let listener = UnixListener::bind(socket).unwrap();
    thread::spawn(move || {
        let (stream, _) = listener.accept().unwrap();
        let mut reader = BufReader::new(&stream);
        let mut writer = &stream;
        let mut opener = String::new();
        reader.read_line(&mut opener).unwrap();
        writer.write_all(format!("{greeting}\n").as_bytes()).unwrap();
        let mut envelope = String::new();
        reader.read_line(&mut envelope).unwrap();
        for reply in replies {
            writer.write_all(format!("{reply}\n").as_bytes()).unwrap();
        }
        (opener, envelope)
    })
}

#[test]
fn the_request_goes_in_the_envelope_with_the_nonce_and_each_reply_line_reaches_the_standard_output() {
    let directory = socket_directory();
    let socket = directory.path().join("launch.sock");
    let ide = serve_once(
        &socket,
        r#"{"proto":"local-peer","v":2,"nonce":"n0nce"}"#,
        &[r#"{"status":"session","n":1}"#, r#"{"status":"session","n":2}"#],
    );
    let order = format!(
        r#"{{"socket":"{}","request":{{"method":"targets","project":"/p","v":1}}}}"#,
        socket.display()
    );
    let (code, lines, errors) = send(&order);
    let (opener, envelope) = ide.join().unwrap();
    assert_eq!(opener, "{\"proto\":\"local-peer\",\"v\":2}\n");
    assert_eq!(
        envelope,
        "{\"nonce\":\"n0nce\",\"request\":{\"method\":\"targets\",\"project\":\"/p\",\"v\":1}}\n"
    );
    assert_eq!(
        (code, lines, errors),
        (
            Some(0),
            vec![
                r#"{"status":"session","n":1}"#.to_owned(),
                r#"{"status":"session","n":2}"#.to_owned()
            ],
            String::new()
        )
    );
}

#[test]
fn a_greeting_without_a_nonce_is_refused_with_exit_1() {
    let directory = socket_directory();
    let socket = directory.path().join("launch.sock");
    let ide = serve_once(&socket, r#"{"status":"refused","code":"BAD_OPENER"}"#, &[]);
    let order = format!(r#"{{"socket":"{}","request":{{"method":"targets"}}}}"#, socket.display());
    let (code, lines, errors) = send(&order);
    ide.join().unwrap();
    assert_eq!(
        (code, lines, errors),
        (
            Some(1),
            vec![],
            "ERROR: the IDE answered no greeting: {\"status\":\"refused\",\"code\":\"BAD_OPENER\"}\n".to_owned()
        )
    );
}

#[test]
fn an_input_without_a_request_object_is_refused_with_exit_1() {
    let (code, lines, errors) = send(r#"{"socket":"/tmp/none.sock","request":"targets"}"#);
    assert_eq!(
        (code, lines, errors),
        (
            Some(1),
            vec![],
            "ERROR: the standard input names no request object: {\"socket\":\"/tmp/none.sock\",\"request\":\"targets\"}\n".to_owned()
        )
    );
}

#[test]
fn an_argument_is_refused_with_exit_2() {
    let output = Command::new(local_app()).arg("/tmp/request.json").output().unwrap();
    assert_eq!(
        (
            output.status.code(),
            String::from_utf8(output.stdout).unwrap(),
            String::from_utf8(output.stderr).unwrap()
        ),
        (
            Some(2),
            String::new(),
            "ERROR: air-local-app takes no argument, but got /tmp/request.json\n".to_owned()
        )
    );
}
