use std::time::{Duration, Instant};

use avl_testkit::http::{FakeHttp, Response};
use avl_trace::bridge::{FACTS_ROUTE, FLUSHED_SPANS_ROUTE, PAINT_ROUTE, SPANS_ROUTE, TOKEN_HEADER, TREE_ROUTE};

use crate::{Client, ErrorKind};

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
    assert_eq!(
        refused.to_string(),
        format!("GET {FACTS_ROUTE}: the bridge answered HTTP 403: no token")
    );
}

/// A document that the route's decoder refuses names the route and what the decoder said.
#[test]
fn a_document_the_route_does_not_declare_is_refused() {
    let server = FakeHttp::start(|_| Response::ok("application/json", r#"{"spans":[],"dropped":0,"renamed":1}"#));
    let error = Client::new(server.url(), "t").unwrap().spans(false, soon()).unwrap_err();
    assert!(matches!(error.kind(), ErrorKind::Protocol(_)), "{error:?}");
    let text = error.to_string();
    assert!(
        text.starts_with(&format!("GET {SPANS_ROUTE}: ")) && text.contains("renamed"),
        "{text}"
    );
}
