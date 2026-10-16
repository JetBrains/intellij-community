//! The control-port channel against a listener of this test that answers canned bytes: no container, no script.

use std::path::Path;
use std::time::Duration;

use avl_base::config::WORKSPACE_DIR;
use avl_base::format::words;
use avl_base::{Backend, Environment, GuestOs, Selection};
use pretty_assertions::assert_eq;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpListener;

use super::*;

const WORKER: &str = "container-linux-1";
const BEARER: &str = "0123456789abcdef0123456789abcdef";
/// The default of `AIR_VM_DAEMON_PORT`, the one guest port `start` publishes.
const DAEMON_PORT: u16 = 27_100;

/// A listener on a free loopback port, and the settings of a container-linux pool whose port and bearer files name it.
async fn listener_and_channel(root: &Path) -> (TcpListener, ControlPortChannel) {
    let listener = TcpListener::bind("127.0.0.1:0").await.expect("a loopback listener");
    let port = listener.local_addr().expect("a bound address").port();
    (listener, channel(root, port))
}

fn channel(root: &Path, port: u16) -> ControlPortChannel {
    channel_with(root, port, &[])
}

/// A channel whose settings publish the daemon port at `host_port`.
fn channel_publishing(root: &Path, host_port: u16) -> ControlPortChannel {
    channel_with(root, 1, &[("AIR_VM_DAEMON_HOST_PORT", &host_port.to_string())])
}

fn channel_with(root: &Path, port: u16, extra: &[(&str, &str)]) -> ControlPortChannel {
    // The files sit where the checkout's skill writes them, because the settings derive the root from the checkout.
    let work = root.join("repo").join("out").join("testing-ui");
    std::fs::create_dir_all(&work).expect("the testing-ui root");
    std::fs::write(work.join("container.ctl_port"), port.to_string()).expect("the port file");
    std::fs::write(work.join("container.ctl_bearer"), format!("{BEARER}\n")).expect("the bearer file");
    let runtime = root.join("runtime").to_string_lossy().into_owned();
    let mut pairs = vec![("HOME", "/Users/air"), ("AIR_VM_RUNTIME_ROOT", runtime.as_str())];
    pairs.extend(extra.iter().copied());
    let environment = Environment::from_pairs(pairs);
    let settings = Config::load(
        avl_base::HostFacts::without_memory(),
        Selection {
            backend: Backend::ContainerLinux,
            guest_os: GuestOs::Linux,
        },
        &environment,
        &root.join("repo").join(WORKSPACE_DIR),
    )
    .unwrap_or_else(|refusal| panic!("the environment was refused: {refusal:?}"));
    ControlPortChannel::new(Arc::new(settings), WORKER)
}

/// What one request said: its head as text, and its body.
struct Received {
    head: String,
    body: Vec<u8>,
}

/// Reads one HTTP/1.1 request whole: the head up to the empty line, then as many body bytes as `Content-Length` says.
async fn read_request(stream: &mut TcpStream) -> Received {
    let mut head = Vec::new();
    while !head.ends_with(b"\r\n\r\n") {
        head.push(stream.read_u8().await.expect("a byte of the request head"));
    }
    let head = String::from_utf8(head).expect("an ASCII head");
    let length = head
        .lines()
        .find_map(|line| {
            let (name, value) = line.split_once(':')?;
            name.eq_ignore_ascii_case("content-length")
                .then(|| value.trim().parse::<usize>().ok())
        })
        .flatten()
        .unwrap_or(0);
    let mut body = vec![0; length];
    stream.read_exact(&mut body).await.expect("the request body");
    Received { head, body }
}

/// Answers the one request the listener gets with `response`, and hands back what the request said.
fn answer_once(listener: TcpListener, response: String) -> tokio::task::JoinHandle<Received> {
    tokio::spawn(async move {
        let (mut stream, _) = listener.accept().await.expect("a connection");
        let received = read_request(&mut stream).await;
        stream.write_all(response.as_bytes()).await.expect("the answer is written");
        stream.shutdown().await.expect("the answer is flushed");
        received
    })
}

fn response(status: &str, headers: &[(&str, &str)], body: &str) -> String {
    let mut text = format!("HTTP/1.1 {status}\r\nContent-Length: {}\r\nConnection: close\r\n", body.len());
    for (name, value) in headers {
        text.push_str(&format!("{name}: {value}\r\n"));
    }
    text.push_str("\r\n");
    text.push_str(body);
    text
}

fn header_line<'a>(head: &'a str, name: &str) -> Option<&'a str> {
    head.lines().find_map(|line| {
        line.split_once(':')
            .filter(|(key, _)| key.eq_ignore_ascii_case(name))
            .map(|(_, value)| value.trim())
    })
}

// --- exec -----------------------------------------------------------------------------------------------------

/// The frames of a program that ran, as the guest server writes them: both outputs under their headers, a newline
/// after the bytes, and the exit code last.
fn frames(stdout: &str, stderr: &str, exit_code: i32) -> String {
    let mut body = String::new();
    for (stream, bytes) in [("stdout", stdout), ("stderr", stderr)] {
        body.push_str(&format!("stream={stream};bytes={}\n{bytes}\n", bytes.len()));
    }
    body.push_str(&format!("exit-code={exit_code}\n"));
    body
}

/// The argv travels as `arg` query values, encoded so a slash, a space and a `+` arrive as themselves; the stdin
/// option is the body; the two outputs and the exit code come back out of the frames of a 200.
#[tokio::test]
async fn exec_posts_the_argv_and_the_stdin_and_reads_the_frames_of_the_answer() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let (listener, channel) = listener_and_channel(root.path()).await;
    let served = answer_once(
        listener,
        response(
            "200 OK",
            &[("Content-Type", "text/plain; charset=utf-8")],
            &frames("hello\n", "warned\n", 0),
        ),
    );
    let options = SpawnOptions {
        stdin: Some(b"fed in".to_vec()),
        ..SpawnOptions::within(Duration::from_secs(5))
    };
    let captured = channel
        .exec(&Ctx::background(), &words(["/bin/echo", "hello world", "a+b"]), &options)
        .await
        .expect("the program ran");
    assert_eq!(
        captured,
        Captured {
            exit_code: 0,
            stdout: "hello\n".to_owned(),
            stderr: "warned\n".to_owned(),
            stdout_truncated: false,
            stderr_truncated: false,
        }
    );
    let received = served.await.expect("the listener answered");
    let request_line = received.head.lines().next().unwrap_or_default();
    assert_eq!(
        request_line,
        "POST /v1/execute?arg=%2Fbin%2Fecho&arg=hello%20world&arg=a%2Bb HTTP/1.1"
    );
    assert_eq!(
        header_line(&received.head, "authorization"),
        Some(format!("Bearer {BEARER}").as_str())
    );
    assert_eq!(received.body, b"fed in");
}

/// A program that exited with another code answers 502 with the same frames: the code is the program's own answer,
/// not a refusal. The bytes arrive whole, with the newline after them consumed.
#[tokio::test]
async fn a_502_is_the_answer_of_a_program_that_failed() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let (listener, channel) = listener_and_channel(root.path()).await;
    let served = answer_once(listener, response("502 Bad Gateway", &[], &frames("no newline", "", 3)));
    let captured = channel
        .exec(
            &Ctx::background(),
            &words(["/usr/bin/false"]),
            &SpawnOptions::within(Duration::from_secs(5)),
        )
        .await
        .expect("the program ran");
    assert_eq!(
        (captured.exit_code, captured.stdout.as_str(), captured.stderr.as_str()),
        (3, "no newline", "")
    );
    served.await.expect("the listener answered");
}

/// A 200 whose body is not three frames is no answer: the refusal names where the body broke.
#[tokio::test]
async fn a_body_of_another_shape_is_a_bad_answer() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let (listener, channel) = listener_and_channel(root.path()).await;
    let served = answer_once(listener, response("200 OK", &[], "hello\n"));
    let refusal = channel
        .exec(
            &Ctx::background(),
            &words(["/bin/echo"]),
            &SpawnOptions::within(Duration::from_secs(5)),
        )
        .await
        .unwrap_err();
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("container_linux_bad_answer", Exit::SOFTWARE)
    );
    assert!(
        refusal.message.contains("`stream=stdout;bytes=<n>` expected"),
        "{}",
        refusal.message
    );
    served.await.expect("the listener answered");
}

/// The parser reads the frames exactly: a count is bytes, a newline ends every frame, and a body that breaks is
/// named where it breaks.
#[test]
fn the_frames_are_read_exactly() {
    let answer = captured(b"stream=stdout;bytes=3\na\nb\nstream=stderr;bytes=0\n\nexit-code=143\n").expect("three frames");
    assert_eq!(
        (answer.exit_code, answer.stdout.as_str(), answer.stderr.as_str()),
        (143, "a\nb", "")
    );
    assert_eq!(
        captured(b"stream=stdout;bytes=99\nshort\n").unwrap_err(),
        "99 bytes of stdout announced, 6 left"
    );
    assert_eq!(
        captured(b"stream=stdout;bytes=5\nshortstream=stderr;bytes=0\n\n").unwrap_err(),
        "no newline after the bytes of stdout"
    );
    assert_eq!(
        captured(b"stream=stdout;bytes=0\n\nstream=stderr;bytes=0\n\n").unwrap_err(),
        "a header line without its newline: \"\""
    );
    assert_eq!(
        captured(b"stream=stdout;bytes=0\n\nstream=stderr;bytes=0\n\nexit-code=x\n").unwrap_err(),
        "`exit-code=<code>` expected, got \"exit-code=x\""
    );
    assert_eq!(
        captured(b"stream=stdout;bytes=0\n\nstream=stderr;bytes=0\n\nexit-code=0\nextra").unwrap_err(),
        "5 bytes after the exit code"
    );
}

/// A program the guest does not have is a refusal a caller can branch on, and the message carries the server's own
/// text, which names the program.
#[tokio::test]
async fn a_missing_guest_program_is_guest_program_missing() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let (listener, channel) = listener_and_channel(root.path()).await;
    let served = answer_once(
        listener,
        response(
            "404 Not Found",
            &[("Content-Type", "text/plain; charset=utf-8")],
            "exec \"/nope\": executable file not found in $PATH\n",
        ),
    );
    let refusal = channel
        .exec(&Ctx::background(), &words(["/nope"]), &SpawnOptions::within(Duration::from_secs(5)))
        .await
        .unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("guest_program_missing", Exit::UNAVAILABLE));
    assert!(refusal.message.contains("executable file not found"), "{}", refusal.message);
    served.await.expect("the listener answered");
}

/// A bearer the server does not know is told apart from a server that does not answer.
#[tokio::test]
async fn a_rejected_bearer_is_its_own_refusal() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let (listener, channel) = listener_and_channel(root.path()).await;
    let served = answer_once(listener, response("401 Unauthorized", &[], "unauthorized\n"));
    let refusal = channel
        .exec(
            &Ctx::background(),
            &words(["/usr/bin/true"]),
            &SpawnOptions::within(Duration::from_secs(5)),
        )
        .await
        .unwrap_err();
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("container_linux_bearer_rejected", Exit::NO_PERM)
    );
    served.await.expect("the listener answered");
}

/// A control port where nothing listens is `container_linux_unreachable` at 69, which a caller retries against.
#[tokio::test]
async fn a_control_port_that_does_not_answer_is_unreachable() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let (listener, channel) = listener_and_channel(root.path()).await;
    drop(listener);
    let refusal = channel
        .exec(
            &Ctx::background(),
            &words(["/usr/bin/true"]),
            &SpawnOptions::within(Duration::from_secs(5)),
        )
        .await
        .unwrap_err();
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("container_linux_unreachable", Exit::UNAVAILABLE)
    );
}

/// The timeout of the options fires with their code, as it does for a spawn, so a probe reads a silent guest as
/// `guest_agent_timeout`.
#[tokio::test]
async fn a_silent_server_is_the_timeout_of_the_options() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let (listener, channel) = listener_and_channel(root.path()).await;
    let silent = tokio::spawn(async move {
        let (stream, _) = listener.accept().await.expect("a connection");
        tokio::time::sleep(Duration::from_secs(30)).await;
        drop(stream);
    });
    let refusal = channel
        .exec(
            &Ctx::background(),
            &words(["/usr/bin/true"]),
            &SpawnOptions::timeout(Duration::from_millis(200), "guest_agent_timeout"),
        )
        .await
        .unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("guest_agent_timeout", Exit::TEMP_FAIL));
    silent.abort();
}

// --- connect --------------------------------------------------------------------------------------------------

/// The percent encoding keeps the unreserved characters and encodes every other byte, the space and the `+` among
/// them, because the server reads the query as a form.
#[test]
fn the_query_encoding_keeps_only_the_unreserved_characters() {
    assert_eq!(percent_encode("abc-XYZ_0.9~"), "abc-XYZ_0.9~");
    assert_eq!(percent_encode("a b+c/d&e=f%g"), "a%20b%2Bc%2Fd%26e%3Df%25g");
    assert_eq!(percent_encode("é"), "%C3%A9");
    assert_eq!(
        exec_uri(6090, &words(["/usr/bin/env", "A=1"])),
        "http://127.0.0.1:6090/v1/execute?arg=%2Fusr%2Fbin%2Fenv&arg=A%3D1"
    );
}

// --- connect ------------------------------------------------------------------------------------------------

/// A connect to the daemon's guest port is a TCP connection to the host port `start` published it at: the bytes go
/// both ways.
#[tokio::test]
async fn a_connect_to_the_daemon_port_reaches_the_published_host_port() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let published = TcpListener::bind("127.0.0.1:0").await.expect("a loopback listener");
    let host_port = published.local_addr().expect("a bound address").port();
    let channel = channel_publishing(root.path(), host_port);
    let echo = tokio::spawn(async move {
        let (mut stream, _) = published.accept().await.expect("one connection");
        let mut bytes = [0_u8; 4];
        stream.read_exact(&mut bytes).await.expect("four bytes");
        stream.write_all(&bytes).await.expect("the echo");
    });
    let mut stream = channel
        .connect(&Ctx::background(), DAEMON_PORT)
        .await
        .expect("the published port answers");
    stream.write_all(b"ping").await.expect("a write into the stream");
    let mut back = [0_u8; 4];
    stream.read_exact(&mut back).await.expect("the echo back");
    assert_eq!(&back, b"ping");
    echo.await.expect("the echo ends");
}

/// A published port where nothing listens is a refused stream, as the relay of the other backends reports it.
#[tokio::test]
async fn a_published_port_where_nothing_listens_is_a_refused_stream() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let free = TcpListener::bind("127.0.0.1:0").await.expect("a loopback listener");
    let host_port = free.local_addr().expect("a bound address").port();
    drop(free);
    let channel = channel_publishing(root.path(), host_port);
    let mut stream = channel
        .connect(&Ctx::background(), DAEMON_PORT)
        .await
        .expect("a refused stream, not a refusal");
    let error = stream.read(&mut [0_u8; 1]).await.expect_err("the first read fails");
    assert_eq!(error.kind(), std::io::ErrorKind::ConnectionRefused);
    assert!(error.to_string().contains(&format!("127.0.0.1:{host_port}")), "{error}");
}

/// Only the daemon port is published, so any other guest port is refused by name.
#[tokio::test]
async fn a_guest_port_that_is_not_published_is_refused_by_name() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let channel = channel_publishing(root.path(), 1);
    let refusal = channel.connect(&Ctx::background(), 8080).await.expect_err("a refusal");
    assert_eq!(refusal.code, "container_linux_port_not_published");
}
