use std::io::{BufRead, BufReader, Write};
use std::net::TcpListener;
use std::sync::{Mutex, mpsc};
use std::thread;
use std::time::{Duration, Instant};

use avl_trace::bridge::{FACTS_ROUTE, FLUSHED_SPANS_ROUTE, PAINT_ROUTE, SPANS_ROUTE, TOKEN_HEADER, TREE_ROUTE, WITHIN_HEADER};

use super::*;
use crate::testing::{FakeHttp, Response};

fn soon() -> Instant {
    Instant::now() + Duration::from_secs(5)
}

#[test]
fn the_client_sends_the_token_and_reads_each_route() {
    let server = FakeHttp::start(|request| {
        if request.header(TOKEN_HEADER) != Some("secret") {
            return Response::error(403, "no token");
        }
        match request.path.as_str() {
            FACTS_ROUTE => Response::ok(
                "application/json; charset=utf-8",
                r#"{"pid":42,"logPath":"/tmp/idea.log","logSize":7,"display":":88","screen":{"x":0,"y":0,"width":1280,"height":800},"os":"linux"}"#,
            ),
            TREE_ROUTE => Response::ok(
                "application/json",
                r#"{"capturedAtMs":1,"windows":[],"inputs":[{"atMs":2,"gesture":"click","target":{"bounds":{"x":0,"y":0,"width":1,"height":1},"component":"JButton"}}]}"#,
            ),
            PAINT_ROUTE => Response::ok("text/plain", "not a png"),
            SPANS_ROUTE => Response::ok("application/json", r#"{"spans":[],"dropped":0}"#),
            FLUSHED_SPANS_ROUTE => Response::ok(
                "application/json",
                concat!(
                    r#"{"spans":[{"scope":"air.sessionLog","name":"air.session.log.read","traceId":"0af7651916cd43dd8448eb211c80319c","#,
                    r#""spanId":"b7ad6b7169203331","startEpochNanos":1790000000000000000,"endEpochNanos":1790000000012000000,"#,
                    r#""attributes":{"air.session.id":"thread-1","air.bytes":18234}}],"dropped":2}"#
                ),
            ),
            _ => Response::error(404, "not found"),
        }
    });
    let client = Client::new(server.url(), "secret").unwrap();
    let facts = client.facts(soon()).unwrap();
    assert_eq!((facts.display.as_deref(), facts.log_size), (Some(":88"), 7));
    let (document, tree) = client.tree(soon()).unwrap();
    assert_eq!(tree.inputs.len(), 1);
    assert!(document.starts_with(br#"{"capturedAtMs":1"#));
    let paint = client.paint(soon()).unwrap_err();
    assert!(paint.to_string().contains("content type"), "{paint:#}");
    let exported = client.spans(false, soon()).unwrap();
    assert!(exported.spans.is_empty(), "{exported:?}");
    let flushed = client.spans(true, soon()).unwrap();
    assert_eq!(
        (
            flushed.spans.len(),
            flushed.dropped,
            flushed.spans.first().map(|span| span.name.as_str())
        ),
        (1, 2, Some("air.session.log.read"))
    );
    let refused = Client::new(server.url(), "wrong").unwrap().facts(soon()).unwrap_err();
    assert!(refused.to_string().contains("HTTP 403"), "{refused:#}");
}

/// The IDE skips an event-thread read the recorder no longer waits for, so each call says how long it still does:
/// what is left of its deadline, never more.
#[test]
fn a_call_tells_the_ide_how_long_it_still_waits() {
    let (within, told) = mpsc::channel();
    let within = Mutex::new(within);
    let server = FakeHttp::start(move |request| {
        let _ = crate::lock(&within).send(request.header(WITHIN_HEADER).map(str::to_owned));
        Response::ok("application/json", r#"{"capturedAtMs":1,"windows":[]}"#)
    });
    let client = Client::new(server.url(), "t").unwrap();
    client.tree(Instant::now() + Duration::from_millis(1500)).unwrap();
    let ms: u64 = told.recv().unwrap().expect("the header is sent").parse().unwrap();
    assert!(ms > 0 && ms <= 1500, "a call with 1500 ms left said it waits {ms} ms");
}

#[test]
fn a_call_that_outlives_its_deadline_fails() {
    let (release, released) = mpsc::channel::<()>();
    let released = Mutex::new(released);
    let server = FakeHttp::start(move |_| {
        let _ = crate::lock(&released).recv_timeout(Duration::from_secs(10));
        Response::ok("application/json", "{}")
    });
    let start = Instant::now();
    let answer = Client::new(server.url(), "t")
        .unwrap()
        .tree(Instant::now() + Duration::from_millis(50));
    assert!(answer.is_err(), "a hung tree route answered");
    assert!(
        start.elapsed() < Duration::from_secs(2),
        "a 50 ms deadline took {:?}",
        start.elapsed()
    );
    drop(release);
}

/// The request is the one the IDE's handler reads: the host it was sent to, the media type it accepts, and a
/// connection that the IDE closes after its answer.
#[test]
fn a_request_names_its_host_and_closes_its_connection() {
    let (heads, told) = mpsc::channel();
    let heads = Mutex::new(heads);
    let server = FakeHttp::start(move |request| {
        let head = ["Host", "Accept", "Connection"].map(|name| request.header(name).map(str::to_owned));
        let _ = crate::lock(&heads).send((request.path.clone(), head));
        Response::ok("application/json", r#"{"spans":[],"dropped":0}"#)
    });
    Client::new(server.url(), "t").unwrap().spans(false, soon()).unwrap();
    let host = server.url().trim_start_matches("http://").to_owned();
    assert_eq!(
        told.recv().unwrap(),
        (
            SPANS_ROUTE.to_owned(),
            [Some(host), Some("application/json".to_owned()), Some("close".to_owned())]
        )
    );
}

/// A server that reads the head of one request and answers it with `answer`, byte for byte, then closes.
fn answering(answer: &'static [u8]) -> String {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let url = format!("http://{}", listener.local_addr().unwrap());
    thread::spawn(move || {
        let (mut stream, _) = listener.accept().unwrap();
        let mut reader = BufReader::new(stream.try_clone().unwrap());
        let mut line = String::new();
        while reader.read_line(&mut line).unwrap() > 0 && line != "\r\n" {
            line.clear();
        }
        stream.write_all(answer).unwrap();
    });
    url
}

#[test]
fn an_answer_without_a_length_ends_with_its_connection() {
    let url = answering(b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{\"spans\":[],\"dropped\":3}");
    assert_eq!(Client::new(&url, "t").unwrap().spans(false, soon()).unwrap().dropped, 3);
}

#[test]
fn a_broken_answer_names_what_broke() {
    for (answer, says) in [
        (
            &b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 50\r\n\r\n{}"[..],
            "the answer ended after 2 of its 50 bytes",
        ),
        (
            b"HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n2\r\n{}\r\n0\r\n\r\n",
            "the bridge answered Transfer-Encoding chunked, which the recorder does not read",
        ),
        (
            b"SSH-2.0-OpenSSH_9.9\r\n",
            r#"the bridge answered the malformed status line "SSH-2.0-OpenSSH_9.9""#,
        ),
        (
            b"HTTP/1.1 200 OK\r\nContent-Type",
            "the answer's head ended early or passed 65536 bytes",
        ),
    ] {
        let error = Client::new(&answering(answer), "t").unwrap().tree(soon()).unwrap_err();
        assert_eq!(format!("{error:#}"), format!("GET {TREE_ROUTE}: {says}"));
    }
}

#[test]
fn a_base_that_is_not_a_loopback_url_is_refused() {
    for base in ["https://127.0.0.1:1", "http://localhost:1", "127.0.0.1:1"] {
        let error = Client::new(base, "t").err().map(|error| error.to_string());
        assert_eq!(error, Some(format!("the bridge URL {base:?} is not http://<ip>:<port>")), "{base}");
    }
    let error = Client::new("http://127.0.0.1:1", "a\r\nb").err().map(|error| error.to_string());
    assert_eq!(
        error.as_deref(),
        Some("the bridge token holds a control character, which no request header can carry")
    );
}
