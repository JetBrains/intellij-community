use std::io::{BufRead, BufReader, Write};
use std::net::TcpListener;
use std::sync::{Mutex, PoisonError, mpsc};
use std::thread;
use std::time::{Duration, Instant};

use avl_testkit::http::{FakeHttp, Response};
use avl_trace::bridge::{SPANS_ROUTE, TOKEN_HEADER, TREE_ROUTE, WITHIN_HEADER};
use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;

const REQUEST_ROUTE: &str = "/api/air/ui-test";

fn soon() -> Instant {
    Instant::now() + Duration::from_secs(5)
}

/// The IDE skips an event-thread read the caller no longer waits for, so each call says how long it still does:
/// what is left of its deadline, never more.
#[test]
fn a_call_tells_the_ide_how_long_it_still_waits() {
    let (within, told) = mpsc::channel();
    let within = Mutex::new(within);
    let server = FakeHttp::start(move |request| {
        let sender = within.lock().unwrap_or_else(PoisonError::into_inner);
        let _ = sender.send(request.header(WITHIN_HEADER).map(str::to_owned));
        Response::ok(JSON, "{}")
    });
    let client = Client::new(server.url(), "t").unwrap();
    client.get(TREE_ROUTE, JSON, Instant::now() + Duration::from_millis(1500)).unwrap();
    let ms: u64 = told.recv().unwrap().expect("the header is sent").parse().unwrap();
    assert!(ms > 0 && ms <= 1500, "a call with 1500 ms left said it waits {ms} ms");
}

#[test]
fn a_call_that_outlives_its_deadline_fails_and_may_be_retried() {
    let (release, released) = mpsc::channel::<()>();
    let released = Mutex::new(released);
    let server = FakeHttp::start(move |_| {
        let _ = released
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .recv_timeout(Duration::from_secs(10));
        Response::ok(JSON, "{}")
    });
    let start = Instant::now();
    let error = Client::new(server.url(), "t")
        .unwrap()
        .get(TREE_ROUTE, JSON, Instant::now() + Duration::from_millis(50))
        .unwrap_err();
    assert!(
        start.elapsed() < Duration::from_secs(2),
        "a 50 ms deadline took {:?}",
        start.elapsed()
    );
    assert!(matches!(error.kind(), ErrorKind::TimedOut), "{error:?}");
    assert!(error.is_transient());
    assert_eq!(
        error.to_string(),
        format!("GET {TREE_ROUTE}: the deadline passed before the answer arrived")
    );
    drop(release);
}

#[test]
fn a_refused_connect_may_be_retried() {
    // A free port below the ephemeral floor, which no parallel bind of port 0 takes, so the connect is refused.
    let port = avl_testkit::free_port_below_ephemeral_floor();
    let error = Client::new(&url(port), "t").unwrap().get(TREE_ROUTE, JSON, soon()).unwrap_err();
    assert!(
        matches!(error.kind(), ErrorKind::Connect { source, .. } if source.kind() == io::ErrorKind::ConnectionRefused),
        "{error:?}"
    );
    assert!(error.is_transient());
    assert!(
        error
            .to_string()
            .starts_with(&format!("GET {TREE_ROUTE}: cannot connect to 127.0.0.1:{port}: ")),
        "{error}"
    );
}

/// A refused token is final: the port belongs to another launch, and a retry gets the same answer. The request
/// endpoint says so in a failure body, and the error keeps it.
#[test]
fn a_refused_token_is_final_and_keeps_what_the_bridge_said() {
    let server = FakeHttp::start(|request| {
        if request.header(TOKEN_HEADER) == Some("secret") {
            return Response::json(200, r#"{"value":1}"#);
        }
        Response::json(
            401,
            r#"{"failure":{"type":"java.lang.SecurityException","message":"unsigned Air UI test request","stack":[]}}"#,
        )
    });
    let error = Client::new(server.url(), "wrong")
        .unwrap()
        .post_json(REQUEST_ROUTE, "{}", soon())
        .unwrap_err();
    assert!(!error.is_transient());
    let ErrorKind::Unauthorized(rejection) = error.kind() else {
        panic!("not a refused token: {error:?}");
    };
    assert_eq!(
        rejection.failure.as_ref().map(|failure| failure.type_name.as_str()),
        Some("java.lang.SecurityException")
    );
    assert_eq!(
        error.to_string(),
        format!("POST {REQUEST_ROUTE}: the bridge answered HTTP 401: java.lang.SecurityException: unsigned Air UI test request")
    );
}

/// The bridge that unloads answers 503 and asks the caller to ask the next copy.
#[test]
fn an_unloading_bridge_may_be_retried_and_another_status_is_final() {
    let server = FakeHttp::start(|request| match request.path.as_str() {
        REQUEST_ROUTE => Response::json(
            503,
            r#"{"failure":{"type":"java.lang.IllegalStateException","message":"the Air UI test bridge unloads; ask the next copy of the bridge","stack":[]}}"#,
        ),
        _ => Response::error(404, "not found"),
    });
    let client = Client::new(server.url(), "t").unwrap();
    let unloading = client.post_json(REQUEST_ROUTE, "{}", soon()).unwrap_err();
    assert!(unloading.is_transient(), "{unloading:?}");
    let missing = client.get(TREE_ROUTE, JSON, soon()).unwrap_err();
    assert!(!missing.is_transient(), "{missing:?}");
    assert_eq!(
        missing.to_string(),
        format!("GET {TREE_ROUTE}: the bridge answered HTTP 404: not found")
    );
}

/// A POST sends the request verbatim as JSON and answers the `value` of the answer.
#[test]
fn a_post_sends_its_json_and_answers_the_value() {
    let (requests, told) = mpsc::channel();
    let requests = Mutex::new(requests);
    let server = FakeHttp::start(move |request| {
        let sender = requests.lock().unwrap_or_else(PoisonError::into_inner);
        let _ = sender.send((
            request.method.clone(),
            request.path.clone(),
            request.header("Content-Type").map(str::to_owned),
            String::from_utf8_lossy(&request.body).into_owned(),
        ));
        Response::json(200, r#"{"value":{"presetIds":["a","b"]}}"#)
    });
    let client = Client::new(server.url(), "t").unwrap();
    let request = r#"{"type":"seed-session-presets","timeoutMs":30000}"#;
    let value = client.post_json(REQUEST_ROUTE, request, soon()).unwrap();
    assert_eq!(value, json!({"presetIds": ["a", "b"]}));
    assert_eq!(
        told.recv().unwrap(),
        (
            "POST".to_owned(),
            REQUEST_ROUTE.to_owned(),
            Some(JSON.to_owned()),
            request.to_owned()
        )
    );
}

/// An operation without a value answers `{}`, as the bridge's encoder leaves a null default out.
#[test]
fn a_post_without_a_value_answers_null() {
    let server = FakeHttp::start(|_| Response::json(200, "{}"));
    let value = Client::new(server.url(), "t")
        .unwrap()
        .post_json(REQUEST_ROUTE, "{}", soon())
        .unwrap();
    assert_eq!(value, Value::Null);
}

/// A request that failed in the IDE is a final error with the whole failure, cause chain and stack.
#[test]
fn a_failure_in_the_ide_is_final_and_keeps_its_chain() {
    let server = FakeHttp::start(|_| {
        Response::json(
            200,
            concat!(
                r#"{"failure":{"type":"java.lang.IllegalStateException","message":"no session row","#,
                r#""stack":["a.B.c(B.kt:1)"],"cause":{"type":"java.util.concurrent.TimeoutException","message":null,"stack":[]}}}"#
            ),
        )
    });
    let error = Client::new(server.url(), "t")
        .unwrap()
        .post_json(REQUEST_ROUTE, "{}", soon())
        .unwrap_err();
    assert!(!error.is_transient());
    assert!(matches!(error.kind(), ErrorKind::Failed(_)), "{error:?}");
    assert_eq!(
        error.to_string(),
        format!(
            "POST {REQUEST_ROUTE}: the request failed in the IDE: java.lang.IllegalStateException: no session row\n\tat a.B.c(B.kt:1)\nCaused by: java.util.concurrent.TimeoutException"
        )
    );
}

/// The request is the one the IDE's handler reads: the host it was sent to, the media type it accepts, and a
/// connection that the IDE closes after its answer.
#[test]
fn a_request_names_its_host_and_closes_its_connection() {
    let (heads, told) = mpsc::channel();
    let heads = Mutex::new(heads);
    let server = FakeHttp::start(move |request| {
        let head = ["Host", "Accept", "Connection"].map(|name| request.header(name).map(str::to_owned));
        let sender = heads.lock().unwrap_or_else(PoisonError::into_inner);
        let _ = sender.send((request.method.clone(), request.path.clone(), head));
        Response::ok(JSON, r#"{"spans":[],"dropped":0}"#)
    });
    Client::new(server.url(), "t").unwrap().get(SPANS_ROUTE, JSON, soon()).unwrap();
    let host = server.url().trim_start_matches("http://").to_owned();
    assert_eq!(
        told.recv().unwrap(),
        (
            "GET".to_owned(),
            SPANS_ROUTE.to_owned(),
            [Some(host), Some(JSON.to_owned()), Some("close".to_owned())]
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
    let body = Client::new(&url, "t").unwrap().get(SPANS_ROUTE, JSON, soon()).unwrap();
    assert_eq!(body, br#"{"spans":[],"dropped":3}"#);
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
            "the bridge answered Transfer-Encoding chunked, which the client does not read",
        ),
        (
            b"SSH-2.0-OpenSSH_9.9\r\n",
            r#"the bridge answered the malformed status line "SSH-2.0-OpenSSH_9.9""#,
        ),
        (
            b"HTTP/1.1 200 OK\r\nContent-Type",
            "the answer's head ended early or passed 65536 bytes",
        ),
        (
            b"HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\n{}",
            r#"the bridge answered the content type "text/plain", not application/json"#,
        ),
        (
            b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 0\r\n\r\n",
            "the bridge answered an empty body",
        ),
    ] {
        let error = Client::new(&answering(answer), "t")
            .unwrap()
            .get(TREE_ROUTE, JSON, soon())
            .unwrap_err();
        assert!(matches!(error.kind(), ErrorKind::Protocol(_)), "{error:?}");
        assert!(!error.is_transient(), "{error:?}");
        assert_eq!(error.to_string(), format!("GET {TREE_ROUTE}: {says}"));
    }
}

#[test]
fn a_post_answer_that_is_not_the_envelope_is_refused() {
    for (answer, says) in [
        ("[1]", "the bridge answered [1], not an object {value, failure}"),
        (
            r#"{"failure":{"message":"no type"}}"#,
            r#"the failure {"message":"no type"} has no text `type`"#,
        ),
    ] {
        let server = FakeHttp::start(move |_| Response::json(200, answer));
        let error = Client::new(server.url(), "t")
            .unwrap()
            .post_json(REQUEST_ROUTE, "{}", soon())
            .unwrap_err();
        assert_eq!(error.to_string(), format!("POST {REQUEST_ROUTE}: {says}"));
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

// A route that states a refusal in its own document answers it with a status that the caller names, and the caller
// reads the document. A status that the caller does not name stays a rejection, and a refused token stays final.
#[test]
fn a_documented_post_answers_the_named_statuses_with_their_document() {
    let server = FakeHttp::start(|request| match request.path.as_str() {
        "/ok" => Response::json(200, r#"{"done":true}"#),
        "/refused" => Response::json(409, r#"{"done":false}"#),
        "/token" => Response::json(401, r#"{"failure":{"type":"java.lang.SecurityException","message":"bad token"}}"#),
        _ => Response::error(500, "the driver failed"),
    });
    let client = Client::new(server.url(), "t").unwrap();
    let post = |route: &str| client.post_document(route, "{}", &[409], soon());
    assert_eq!(post("/ok").unwrap(), (200, br#"{"done":true}"#.to_vec()));
    assert_eq!(post("/refused").unwrap(), (409, br#"{"done":false}"#.to_vec()));
    let failed = post("/failed").unwrap_err();
    assert!(
        matches!(failed.kind(), ErrorKind::Rejected { status: 500, rejection } if rejection.excerpt == "the driver failed"),
        "{failed:?}"
    );
    assert!(matches!(post("/token").unwrap_err().kind(), ErrorKind::Unauthorized(_)));
    let not_named = client.post_document("/refused", "{}", &[], soon()).unwrap_err();
    assert!(matches!(not_named.kind(), ErrorKind::Rejected { status: 409, .. }), "{not_named:?}");
}
