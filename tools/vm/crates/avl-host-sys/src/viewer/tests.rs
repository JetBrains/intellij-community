use std::io::{BufRead, BufReader};
use std::net::TcpListener;

use pretty_assertions::assert_eq;

use super::*;

/// A one-connection HTTP server on a free port that answers the request head it reads with `answer`, and the
/// request line it read.
fn serve_once(answer: &'static str) -> (u16, std::thread::JoinHandle<String>) {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let port = listener.local_addr().unwrap().port();
    let server = std::thread::spawn(move || {
        let (stream, _) = listener.accept().unwrap();
        let mut reader = BufReader::new(stream);
        let mut request_line = String::new();
        reader.read_line(&mut request_line).unwrap();
        let mut line = String::new();
        while reader.read_line(&mut line).unwrap() > 0 && line != "\r\n" {
            line.clear();
        }
        reader.get_mut().write_all(answer.as_bytes()).unwrap();
        request_line
    });
    (port, server)
}

#[test]
fn the_probe_knows_the_viewer_by_its_header() {
    let (viewer, request) = serve_once("HTTP/1.1 200 OK\r\nx-air-trace: serve\r\nContent-Length: 0\r\n\r\n");
    assert!(probe_viewer(viewer, PROBE_TIMEOUT), "the probe did not know the viewer");
    assert_eq!(request.join().unwrap(), format!("HEAD {RUNS_ROUTE} HTTP/1.1\r\n"));

    let (other, _) = serve_once("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
    assert!(!probe_viewer(other, PROBE_TIMEOUT), "the probe took another program for the viewer");
    let (redirect, _) = serve_once("HTTP/1.1 302 Found\r\nLocation: /air/runs\r\nX-Air-Trace: serve\r\n\r\n");
    assert!(!probe_viewer(redirect, PROBE_TIMEOUT), "the probe took a redirect for the viewer");
    let (another_server, _) = serve_once("HTTP/1.1 200 OK\r\nX-Air-Trace: plan\r\nContent-Length: 0\r\n\r\n");
    assert!(
        !probe_viewer(another_server, PROBE_TIMEOUT),
        "the probe took another identity for the viewer"
    );
    let (not_http, _) = serve_once("SSH-2.0-OpenSSH_9.9\r\n");
    assert!(
        !probe_viewer(not_http, PROBE_TIMEOUT),
        "the probe took a program that speaks no HTTP for the viewer"
    );

    // A program that accepts and never answers is not the viewer either, and costs the probe only its timeout.
    let silent = TcpListener::bind("127.0.0.1:0").unwrap();
    let started = Instant::now();
    assert!(!probe_viewer(silent.local_addr().unwrap().port(), Duration::from_millis(200)));
    assert!(started.elapsed() < Duration::from_secs(5));
}

/// A port where nothing listens holds no viewer.
#[tokio::test]
async fn the_probe_finds_no_viewer_on_a_closed_port() {
    let (closed, _held) = avl_testkit::closed_port().await;
    assert!(!probe_viewer(closed, PROBE_TIMEOUT), "the probe found a viewer on a closed port");
}

/// The controller starts the wrapper in the foreground form, and the start appends what `--detach` would pass.
#[test]
fn the_controllers_start_is_the_wrapper_with_the_detached_arguments() {
    let checkout = Path::new("/repo");
    // Through the shell, because the wrapper is a polyglot with no shebang.
    let shell: &[&str] = if cfg!(windows) { &["cmd.exe", "/c"] } else { &["/bin/bash"] };
    let want: Vec<OsString> = shell
        .iter()
        .map(OsString::from)
        .chain([
            checkout.join("community").join("tools").join("trace.cmd").into_os_string(),
            "serve".into(),
        ])
        .collect();
    assert_eq!(serve_command(checkout), want);
    assert_eq!(viewer_dir(Path::new("/runtime")), Path::new("/runtime").join("viewer"));
    let pid_file = viewer_dir(Path::new("/runtime")).join(SERVE_PID_FILE);
    let args: Vec<String> = DetachRequest::for_port(7357)
        .args(&pid_file)
        .iter()
        .map(|arg| arg.to_string_lossy().into_owned())
        .collect();
    assert_eq!(
        args,
        ["--port", "7357", "--idle-exit", "30m", "--pid-file", &pid_file.to_string_lossy(),]
    );
}
