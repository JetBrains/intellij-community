//! The fake control port against raw HTTP/1.1 of this test: no channel, no container.

use std::sync::Arc;

use avl_host_sys::Captured;
use pretty_assertions::assert_eq;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpStream;

use super::*;
use crate::answer_guest;

const WORKER: &str = "container-linux-1";

fn guests() -> Arc<FakeGuests> {
    FakeGuests::of(&[WORKER.to_owned()])
}

/// Sends one request and reads the whole answer, which the server closes.
async fn request(port: u16, text: &str) -> String {
    let mut stream = TcpStream::connect(("127.0.0.1", port)).await.expect("the control port accepts");
    stream.write_all(text.as_bytes()).await.expect("the request is written");
    let mut answer = String::new();
    stream.read_to_string(&mut answer).await.expect("the answer is read");
    answer
}

fn status_line(answer: &str) -> &str {
    answer.lines().next().unwrap_or_default()
}

fn body(answer: &str) -> &str {
    answer.split_once("\r\n\r\n").map(|(_, body)| body).unwrap_or_default()
}

/// The execute route hands the decoded argv and the body to the worker's fake channel, and answers what the channel
/// said as frames, with a 502 for a code other than 0. The channel records the call.
#[tokio::test]
async fn exec_runs_the_argv_through_the_fake_guest_and_answers_the_frames() {
    let guests = guests();
    guests.answer(answer_guest(vec![(
        "/bin/echo",
        Captured {
            exit_code: 3,
            stdout: "hello\n".to_owned(),
            stderr: "warned\n".to_owned(),
            ..Captured::default()
        },
    )]));
    let control_port = FakeControlPort::start(WORKER, Arc::clone(&guests));
    let answer = request(
        control_port.port(),
        &format!(
            "POST /v1/execute?arg=%2Fbin%2Fecho&arg=hello%20world&arg=a%2Bb HTTP/1.1\r\nHost: 127.0.0.1\r\n\
             Authorization: Bearer {CONTAINER_LINUX_BEARER}\r\nContent-Length: 6\r\n\r\nfed in"
        ),
    )
    .await;
    assert_eq!(status_line(&answer), "HTTP/1.1 502 Bad Gateway", "{answer}");
    assert_eq!(
        body(&answer),
        "stream=stdout;bytes=6\nhello\n\nstream=stderr;bytes=7\nwarned\n\nexit-code=3\n"
    );
    let calls = guests.channel(WORKER).calls();
    assert_eq!(calls.len(), 1, "{calls:?}");
    assert_eq!(calls[0].argv, ["/bin/echo", "hello world", "a+b"]);
    assert_eq!(calls[0].options.stdin.as_deref(), Some(b"fed in".as_slice()));
    assert_eq!(
        control_port.requests(),
        ["POST /v1/execute?arg=%2Fbin%2Fecho&arg=hello%20world&arg=a%2Bb"]
    );

    // A program whose answer is silence is a 200 with two empty frames and exit 0.
    let answer = request(
        control_port.port(),
        &format!("POST /v1/execute?arg=%2Fusr%2Fbin%2Ftrue HTTP/1.1\r\nAuthorization: Bearer {CONTAINER_LINUX_BEARER}\r\n\r\n"),
    )
    .await;
    assert_eq!(status_line(&answer), "HTTP/1.1 200 OK", "{answer}");
    assert_eq!(body(&answer), "stream=stdout;bytes=0\n\nstream=stderr;bytes=0\n\nexit-code=0\n");
}

/// A request without the bearer the fake `container.cmd` wrote reaches no guest.
#[tokio::test]
async fn a_wrong_bearer_is_refused_with_401() {
    let guests = guests();
    let control_port = FakeControlPort::start(WORKER, Arc::clone(&guests));
    let answer = request(
        control_port.port(),
        "POST /v1/execute?arg=%2Fusr%2Fbin%2Ftrue HTTP/1.1\r\nAuthorization: Bearer nope\r\n\r\n",
    )
    .await;
    assert_eq!(status_line(&answer), "HTTP/1.1 401 Unauthorized", "{answer}");
    assert!(guests.channel(WORKER).calls().is_empty());
    let answer = request(
        control_port.port(),
        &format!("GET /v1/other HTTP/1.1\r\nAuthorization: Bearer {CONTAINER_LINUX_BEARER}\r\n\r\n"),
    )
    .await;
    assert_eq!(status_line(&answer), "HTTP/1.1 404 Not Found", "{answer}");
}

#[test]
fn the_query_decodes_as_a_form() {
    assert_eq!(
        argv_of("/v1/execute?arg=%2Fusr%2Fbin%2Fenv&arg=A%3D1&arg=a+b&arg=%C3%A9"),
        ["/usr/bin/env", "A=1", "a b", "é"]
    );
    assert!(argv_of("/v1/execute").is_empty());
}
