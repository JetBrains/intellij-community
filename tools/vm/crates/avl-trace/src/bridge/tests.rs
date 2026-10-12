use pretty_assertions::assert_eq;

use super::*;

#[test]
fn the_routes_are_beside_the_request_endpoint_and_not_under_it() {
    let routes = [FACTS_ROUTE, TREE_ROUTE, PAINT_ROUTE, SPANS_ROUTE, FLUSHED_SPANS_ROUTE];
    assert_eq!(
        routes,
        [
            "/api/air/ui-test-trace/facts",
            "/api/air/ui-test-trace/tree",
            "/api/air/ui-test-trace/paint",
            "/api/air/ui-test-trace/spans",
            // The Kotlin side matches the whole segment after the prefix, so this is one route and not a query.
            "/api/air/ui-test-trace/spans/flushed",
        ]
    );
    for route in routes {
        assert!(
            route.strip_prefix(ROUTE_PREFIX).is_some_and(|rest| rest.starts_with('/')),
            "{route} is not under {ROUTE_PREFIX}"
        );
    }
    // The platform's `HttpRequestHandler.checkPrefix` accepts a prefix only when `/` or `?` follows it, so the
    // request endpoint's `api/air/ui-test` does not claim these, and neither handler has to order itself before
    // the other.
    let next = ROUTE_PREFIX.strip_prefix("/api/air/ui-test").and_then(|rest| rest.chars().next());
    assert!(
        next.is_some_and(|next| next != '/' && next != '?'),
        "the trace prefix {ROUTE_PREFIX} would be claimed by the request endpoint"
    );
    assert_eq!(TOKEN_HEADER, "X-Air-Ui-Test-Token");
}

#[test]
fn a_node_writes_enabled_only_as_false_and_focused_only_as_true() {
    let mut button = Node {
        class: "JButton".to_owned(),
        text: Some("OK".to_owned()),
        bounds: [1, 2, 3, 4],
        ..Node::default()
    };
    button.set_enabled(false);
    button.set_focused(true);
    assert_eq!(
        serde_json::to_string(&button).unwrap(),
        r#"{"c":"JButton","t":"OK","b":[1,2,3,4],"e":false,"f":true}"#
    );
    let plain = Node {
        class: "JPanel".to_owned(),
        bounds: [0, 0, 10, 10],
        ..Node::default()
    };
    assert_eq!(serde_json::to_string(&plain).unwrap(), r#"{"c":"JPanel","b":[0,0,10,10]}"#);
    assert!(plain.is_enabled() && !plain.is_focused());
    assert!(!button.is_enabled() && button.is_focused());

    let window = |root: &str| {
        format!(r#"{{"capturedAtMs":1,"windows":[{{"class":"W","bounds":{{"x":0,"y":0,"width":1,"height":1}},"root":{root}}}]}}"#)
    };
    let refused = [
        window(r#"{"c":"A","b":[0,0,1,1],"e":true}"#),
        window(r#"{"c":"A","b":[0,0,1,1],"f":false}"#),
        window(r#"{"b":[0,0,1,1]}"#),
        window(r#"{"c":"","b":[0,0,1,1]}"#),
        window(r#"{"c":"A","b":[0,0,-1,1]}"#),
        window(r#"{"c":"A","b":[0,0,1,1],"visible":true}"#),
        window(r#"{"c":"A","b":[0,0,1,1],"k":[{"c":"B","b":[0,0,1,1],"e":true}]}"#),
        r#"{"capturedAtMs":1,"windows":[{"bounds":{"x":0,"y":0,"width":1,"height":1},"root":{"c":"A","b":[0,0,1,1]}}]}"#.to_owned(),
        r#"{"capturedAtMs":1}"#.to_owned(),
        r#"{"capturedAtMs":0,"windows":[]}"#.to_owned(),
        r#"{"capturedAtMs":1,"windows":[],"inputs":[{"atMs":1,"gesture":"","target":{"bounds":{"x":0,"y":0,"width":1,"height":1},"component":"A"}}]}"#.to_owned(),
    ];
    for document in refused {
        assert!(decode_tree(document.as_bytes()).is_err(), "the tree {document} was accepted");
    }
    let tree = decode_tree(window(r#"{"c":"A","b":[0,0,1,1],"e":false,"f":true}"#).as_bytes()).unwrap();
    assert!(!tree.windows[0].root.is_enabled() && tree.windows[0].root.is_focused());
    assert!(
        decode_tree(br#"{"capturedAtMs":1,"windows":[]}"#).is_ok(),
        "an IDE with no showing window is refused"
    );
}

/// The viewer draws a point on the video at the time of its pointer event, so a point without its time, or a time
/// without its point, is refused rather than drawn at the wrong moment.
#[test]
fn a_tree_input_carries_its_point_and_the_points_time_together() {
    let target = r#""target":{"bounds":{"x":0,"y":0,"width":1,"height":1},"component":"A"}"#;
    for input in [
        format!(r#"{{"atMs":1,"gesture":"click","point":{{"x":1,"y":1}},{target}}}"#),
        format!(r#"{{"atMs":1,"gesture":"press","pointAtMs":2,{target}}}"#),
        format!(r#"{{"atMs":1,"gesture":"press","point":{{"x":1,"y":1}},"pointAtMs":-2,{target}}}"#),
    ] {
        let document = format!(r#"{{"capturedAtMs":1,"windows":[],"inputs":[{input}]}}"#);
        assert!(decode_tree(document.as_bytes()).is_err(), "the tree {document} was accepted");
    }
    let pressed = decode_tree(
        concat!(
            r#"{"capturedAtMs":3,"windows":[],"inputs":["#,
            r#"{"atMs":1,"gesture":"pressEscape","target":{"bounds":{"x":0,"y":0,"width":1,"height":1},"component":"A"}},"#,
            r#"{"atMs":2,"gesture":"press","point":{"x":5,"y":6},"pointAtMs":2,"target":{"bounds":{"x":0,"y":0,"width":9,"height":9},"component":"B"}}]}"#
        )
        .as_bytes(),
    )
    .unwrap();
    assert_eq!(pressed.inputs.len(), 2);
    assert_eq!(
        (
            pressed.inputs[1].gesture.as_str(),
            pressed.inputs[1].point,
            pressed.inputs[1].point_at_ms
        ),
        (GESTURE_PRESS, Some(Point { x: 5, y: 6 }), 2)
    );
}

#[test]
fn facts_decode_and_refuse() {
    let facts = decode_facts(
        concat!(
            r#"{"pid":4242,"logPath":"/home/admin/WorkerData/system/log/idea.log","logSize":1048576,"#,
            r#""display":":88","screen":{"x":0,"y":0,"width":1280,"height":800},"os":"linux"}"#
        )
        .as_bytes(),
    )
    .unwrap();
    assert_eq!((facts.pid, facts.display.as_deref(), facts.screen.width), (4242, Some(":88"), 1280));
    for document in [
        r#"{"logPath":"/l","logSize":0,"screen":{"x":0,"y":0,"width":1,"height":1},"os":"linux"}"#,
        r#"{"pid":1,"logPath":"","logSize":0,"screen":{"x":0,"y":0,"width":1,"height":1},"os":"linux"}"#,
        r#"{"pid":1,"logPath":"/l","logSize":0,"screen":{"x":0,"y":0,"width":1,"height":1}}"#,
        r#"{"pid":1,"logPath":"/l","logSize":-1,"screen":{"x":0,"y":0,"width":1,"height":1},"os":"linux"}"#,
        r#"{"pid":1,"logPath":"/l","logSize":0,"screen":{"x":0,"y":0,"width":1,"height":1},"os":"linux","user":"admin"}"#,
    ] {
        assert!(decode_facts(document.as_bytes()).is_err(), "the facts {document} were accepted");
    }
}

#[test]
fn ide_spans_decode_and_refuse() {
    let spans = decode_ide_spans(
        concat!(
            r#"{"spans":[{"scope":"air.sessionLog","name":"air.session.log.read","traceId":"0af7651916cd43dd8448eb211c80319c","#,
            r#""spanId":"b7ad6b7169203331","parentSpanId":"00f067aa0ba902b7","startEpochNanos":1790000000000000000,"#,
            r#""endEpochNanos":1790000000012000000,"attributes":{"air.session.id":"thread-1","air.bytes":18234,"air.cached":true,"air.ratio":0.5},"#,
            r#""failed":true}],"dropped":3}"#
        )
        .as_bytes(),
    )
    .unwrap();
    assert_eq!((spans.spans.len(), spans.dropped), (1, 3));
    let span = &spans.spans[0];
    assert!(span.failed);
    assert_eq!(span.parent_span_id.as_deref(), Some("00f067aa0ba902b7"));
    // Sorted by key, so a bundle is the same every time, and each value keeps its JSON type.
    assert_eq!(
        span.otlp_attributes(),
        [
            KeyValue::int("air.bytes", 18234),
            KeyValue::bool("air.cached", true),
            KeyValue {
                key: "air.ratio".to_owned(),
                value: AnyValue::DoubleValue(0.5)
            },
            KeyValue::string("air.session.id", "thread-1"),
        ]
    );
    // A fraction or an exponent makes a double, and so does an integer past i64.
    for (json, value) in [
        ("1.0", IdeAttribute::Double(1.0)),
        ("1e3", IdeAttribute::Double(1000.0)),
        ("-7", IdeAttribute::Int(-7)),
        ("9223372036854775808", IdeAttribute::Double(2f64.powi(63))),
    ] {
        assert_eq!(serde_json::from_str::<IdeAttribute>(json).unwrap(), value, "{json}");
    }

    let valid = r#""scope":"s","name":"n","traceId":"0af7651916cd43dd8448eb211c80319c","spanId":"b7ad6b7169203331","startEpochNanos":2,"endEpochNanos":3"#;
    let refused = [
        r#"{"dropped":0}"#.to_owned(),
        r#"{"spans":[],"dropped":-1}"#.to_owned(),
        r#"{"spans":[],"dropped":0,"sequence":1}"#.to_owned(),
        format!(r#"{{"spans":[{{{valid},"sequence":1}}],"dropped":0}}"#),
        r#"{"spans":[{"scope":"s","name":"","traceId":"0af7651916cd43dd8448eb211c80319c","spanId":"b7ad6b7169203331","startEpochNanos":2,"endEpochNanos":3}],"dropped":0}"#.to_owned(),
        r#"{"spans":[{"scope":"s","name":"n","traceId":"0AF7651916CD43DD8448EB211C80319C","spanId":"b7ad6b7169203331","startEpochNanos":2,"endEpochNanos":3}],"dropped":0}"#.to_owned(),
        format!(r#"{{"spans":[{{{valid},"parentSpanId":"0000000000000000"}}],"dropped":0}}"#),
        r#"{"spans":[{"scope":"s","name":"n","traceId":"0af7651916cd43dd8448eb211c80319c","spanId":"b7ad6b7169203331","startEpochNanos":3,"endEpochNanos":2}],"dropped":0}"#.to_owned(),
        format!(r#"{{"spans":[{{{valid},"attributes":{{"a":null}}}}],"dropped":0}}"#),
        format!(r#"{{"spans":[{{{valid},"attributes":{{"a":[1]}}}}],"dropped":0}}"#),
        format!(r#"{{"spans":[{{{valid},"attributes":{{"a":{{"b":1}}}}}}],"dropped":0}}"#),
    ];
    for document in refused {
        assert!(decode_ide_spans(document.as_bytes()).is_err(), "{document} was accepted");
    }
    // A default value is left out, as the Kotlin side leaves it out.
    let minimal = format!(r#"{{"spans":[{{{valid}}}],"dropped":0}}"#);
    let decoded = decode_ide_spans(minimal.as_bytes()).unwrap();
    assert_eq!(crate::encode(&decoded).unwrap(), minimal.as_bytes());
}
