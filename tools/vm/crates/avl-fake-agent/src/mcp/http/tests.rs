use std::io::Cursor;

use pretty_assertions::assert_eq;

use super::*;

fn answer(bytes: &'static [u8]) -> Answer {
    read_answer(Cursor::new(bytes), false).unwrap()
}

#[test]
fn a_body_with_a_length_ends_at_the_length() {
    let answer = answer(b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nmcp-session-id: s-1\r\nContent-Length: 2\r\n\r\n{}tail");
    assert_eq!((answer.status, answer.header("Mcp-Session-Id")), (200, Some("s-1")));
    assert_eq!(answer.text().unwrap(), "{}");
}

#[test]
fn a_chunked_body_drops_its_extensions_and_its_trailer() {
    let answer =
        answer(b"HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5;ext=1\r\nhello\r\n6\r\n world\r\n0\r\nX-Trailer: 1\r\n\r\nafter");
    assert_eq!(answer.text().unwrap(), "hello world");
}

#[test]
fn a_body_without_a_length_ends_with_the_connection_and_a_204_has_none() {
    assert_eq!(answer(b"HTTP/1.1 202 Accepted\r\n\r\nrest").text().unwrap(), "rest");
    assert_eq!(answer(b"HTTP/1.1 204 No Content\r\n\r\nrest").text().unwrap(), "");
}

#[test]
fn a_broken_answer_is_refused() {
    let refusal = |bytes: &'static [u8]| match read_answer(Cursor::new(bytes), false) {
        Ok(answer) => answer.text().unwrap_err().to_string(),
        Err(error) => error.to_string(),
    };
    assert_eq!(refusal(b"garbage\r\n\r\n"), "the answer starts with \"garbage\"");
    assert_eq!(refusal(b"HTTP/1.1 200 OK\r\n"), "the answer head ended early");
    assert_eq!(
        refusal(b"HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nzz\r\n"),
        "the chunk size \"zz\" is no hex number"
    );
    assert_eq!(
        refusal(b"HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n9\r\nshort"),
        "the answer ended inside a chunk"
    );
}

/// An event ends with an empty line or with the stream, its data lines join with line feeds, and a comment or an event
/// without data is skipped. A carriage return before a line feed is part of the line end.
#[test]
fn the_events_of_a_stream_are_their_data() {
    let mut events = Events::new(Cursor::new(
        &b": comment\r\nevent: message\r\ndata: {\"a\":1}\r\n\r\nevent: ping\n\ndata:one\ndata: two\n\ndata: last"[..],
    ));
    let mut all = Vec::new();
    while let Some(data) = events.next_data().unwrap() {
        all.push(data);
    }
    assert_eq!(all, ["{\"a\":1}", "one\ntwo", "last"]);
}

#[test]
fn a_url_that_is_no_http_url_is_refused() {
    let Err(error) = send("POST", "https://127.0.0.1:1/mcp", &[], b"", Duration::from_secs(1)) else {
        panic!("an https URL is sent");
    };
    assert_eq!(error.to_string(), "the MCP URL https://127.0.0.1:1/mcp is no http URL");
}
