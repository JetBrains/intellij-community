use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::thread;

use pretty_assertions::assert_eq;

use super::*;

/// Reads one request from a real connection whose client sends `bytes`.
fn read_from(bytes: &'static [u8]) -> Result<Request, RequestError> {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let address = listener.local_addr().unwrap();
    let client = thread::spawn(move || {
        let mut stream = TcpStream::connect(address).unwrap();
        stream.write_all(bytes).unwrap();
        stream.shutdown(std::net::Shutdown::Write).unwrap();
        let mut rest = Vec::new();
        let _ = stream.read_to_end(&mut rest);
    });
    let (stream, _) = listener.accept().unwrap();
    let request = read_request(&stream, MAX_BODY_BYTES);
    drop(stream);
    client.join().unwrap();
    request
}

#[test]
fn a_request_with_a_length_has_its_method_path_query_headers_and_body() {
    let request = read_from(b"POST /oauth2/token?x=1&y=a%20b HTTP/1.1\r\nHost: h\r\ncontent-length: 5\r\n\r\nhello").unwrap();
    assert_eq!(request.method, "POST");
    assert_eq!(request.path, "/oauth2/token");
    assert_eq!(request.target(), "/oauth2/token?x=1&y=a%20b");
    assert_eq!(request.query_param("y").as_deref(), Some("a b"));
    assert_eq!(request.header("Content-Length"), Some("5"));
    assert_eq!(request.body, b"hello");
}

#[test]
fn a_chunked_request_body_is_decoded_with_its_extensions_and_trailer() {
    let request = read_from(
        b"POST /feedback HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n5;ext=1\r\nhello\r\n6\r\n world\r\n0\r\nX-Trailer: 1\r\n\r\n",
    )
    .unwrap();
    assert_eq!(request.body, b"hello world");
}

#[test]
fn an_expect_continue_gets_the_interim_answer_before_the_body() {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let address = listener.local_addr().unwrap();
    let client = thread::spawn(move || {
        let mut stream = TcpStream::connect(address).unwrap();
        stream
            .write_all(b"POST /feedback HTTP/1.1\r\nExpect: 100-continue\r\nContent-Length: 2\r\n\r\n")
            .unwrap();
        let mut interim = [0; 25];
        stream.read_exact(&mut interim).unwrap();
        stream.write_all(b"ok").unwrap();
        interim
    });
    let (stream, _) = listener.accept().unwrap();
    let request = read_request(&stream, MAX_BODY_BYTES).unwrap();
    assert_eq!(&client.join().unwrap(), b"HTTP/1.1 100 Continue\r\n\r\n");
    assert_eq!(request.body, b"ok");
}

#[test]
fn a_malformed_request_line_and_a_large_body_are_refused_and_a_closed_one_ends() {
    assert!(matches!(read_from(b"GARBAGE\r\n\r\n"), Err(RequestError::Malformed(_))));
    assert!(matches!(
        read_from(b"GET relative HTTP/1.1\r\n\r\n"),
        Err(RequestError::Malformed(_))
    ));
    assert!(matches!(
        read_from(b"POST / HTTP/1.1\r\nContent-Length: 99999999999\r\n\r\n"),
        Err(RequestError::TooLarge)
    ));
    assert!(matches!(read_from(b"GET / HTTP/1.1\r\nHost"), Err(RequestError::Closed)));
    assert!(matches!(
        read_from(b"POST / HTTP/1.1\r\nContent-Length: 9\r\n\r\nshort"),
        Err(RequestError::Closed)
    ));
}

fn written(response: Response, head_only: bool) -> String {
    let mut bytes = Vec::new();
    assert!(
        write_response(&mut bytes, response, head_only).unwrap().is_none(),
        "an answer without an upgrade"
    );
    String::from_utf8(bytes).unwrap()
}

#[test]
fn an_answer_has_its_status_line_headers_and_length_and_closes_the_connection() {
    let response = Response::empty(302).header("Location", "http://127.0.0.1:1/cb?code=c");
    assert_eq!(
        written(response, false),
        "HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:1/cb?code=c\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
    );
    let json = || Response::with_body(200, "application/json", "[]");
    assert_eq!(
        written(json(), false),
        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n[]"
    );
    assert_eq!(
        written(json(), true),
        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n"
    );
}

#[test]
fn a_truncated_answer_announces_the_whole_length_and_a_chunked_one_ends_with_the_last_chunk() {
    let response = Response::truncated(200, "application/zip", 8, b"half".to_vec());
    assert_eq!(response.sent_bytes(), 4);
    assert_eq!(
        written(response, false),
        "HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nContent-Length: 8\r\nConnection: close\r\n\r\nhalf"
    );
    let response = Response::chunked(200, "text/event-stream", b"event: e\n\n".to_vec());
    assert_eq!(
        written(response, false),
        "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\na\r\nevent: e\n\n\r\n0\r\n\r\n"
    );
}

#[test]
fn a_streamed_answer_sends_one_chunk_per_write_and_skips_an_empty_one() {
    let response = Response::streamed(
        200,
        "application/x-ndjson",
        Box::new(|chunks| {
            chunks.write_all(b"{\"a\":1}\n")?;
            chunks.write_all(b"")?;
            chunks.write_all(b"{\"b\":2}\n")
        }),
    );
    assert_eq!(response.sent_bytes(), 0);
    assert_eq!(
        written(response, false),
        "HTTP/1.1 200 OK\r\nContent-Type: application/x-ndjson\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n\
         8\r\n{\"a\":1}\n\r\n8\r\n{\"b\":2}\n\r\n0\r\n\r\n"
    );
    let unrun = Response::streamed(200, "text/plain", Box::new(|_| panic!("a HEAD answer runs no writer")));
    assert_eq!(
        written(unrun, true),
        "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n"
    );
}

#[test]
fn a_body_past_the_bound_of_the_server_is_too_large() {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let address = listener.local_addr().unwrap();
    let client = thread::spawn(move || {
        let mut stream = TcpStream::connect(address).unwrap();
        stream.write_all(b"PUT /jars/x HTTP/1.1\r\nContent-Length: 5\r\n\r\nhello").unwrap();
    });
    let (stream, _) = listener.accept().unwrap();
    assert!(matches!(read_request(&stream, 4), Err(RequestError::TooLarge)));
    client.join().unwrap();
}

#[test]
fn a_form_component_decodes_and_encodes_as_the_java_url_codec_does() {
    assert_eq!(
        form("a=1&b=x+y&c=%41%zz&d&=e"),
        [
            ("a".to_owned(), "1".to_owned()),
            ("b".to_owned(), "x y".to_owned()),
            ("c".to_owned(), "A%zz".to_owned()),
            ("d".to_owned(), String::new()),
            (String::new(), "e".to_owned()),
        ]
    );
    assert_eq!(form_decode("%e2%9c%93%"), "\u{2713}%");
    assert_eq!(form_encode("a b/c~é.-*_"), "a+b%2Fc%7E%C3%A9.-*_");
}
