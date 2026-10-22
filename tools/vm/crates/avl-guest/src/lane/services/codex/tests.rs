use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;
use crate::lane::services::testing::{self, TOKEN, golden};

#[test]
fn the_event_stream_of_the_lane_reply_is_the_golden() {
    // The Kotlin test of `MockResponsesServer` holds its stream to the same file.
    expect_test::expect_file![golden("codex-completed-reply.sse")].assert_eq(&completed_reply(REPLY));
}

#[test]
fn a_turn_answers_the_reply_as_a_chunked_event_stream_and_is_logged() {
    let service = start(0, TOKEN).unwrap();
    let address = service.address();
    let request = r#"{"model":"gpt-5.5","reasoning":{"effort":"high"},"input":[]}"#;
    let answer = testing::call(
        address,
        "POST",
        "/v1/responses",
        &[("Content-Type", "application/json")],
        request.as_bytes(),
    );
    assert_eq!(answer.status, 200);
    assert_eq!(answer.header("Content-Type"), Some("text/event-stream"));
    assert_eq!(answer.header("Cache-Control"), Some("no-cache"));
    assert_eq!(answer.header("Transfer-Encoding"), Some("chunked"));
    assert_eq!(answer.text(), completed_reply(REPLY));

    let turns = testing::control(address, "GET", "/_lane/codex/turns", "").json();
    assert_eq!(
        turns,
        json!({ "mark": 1, "turns": [{ "sequence": 1, "model": "gpt-5.5", "effort": "high", "parsed": true }] })
    );
}

#[test]
fn a_title_request_gets_a_title_and_is_no_turn() {
    let service = start(0, TOKEN).unwrap();
    let address = service.address();
    let title = r#"{"model":"m","text":{"format":{"type":"json_schema","schema":{"type":"object","required":["title"]}}}}"#;
    let answer = testing::call(address, "POST", "/v1/responses", &[], title.as_bytes());
    assert_eq!(answer.text(), completed_reply(r#"{"title":"Mock session"}"#));
    assert!(answer.text().contains(r#""text":"{\"title\":\"Mock session\"}""#));
    let not_title = r#"{"text":{"format":{"schema":{"required":["title","other"]}}}}"#;
    testing::call(address, "POST", "/v1/responses", &[], not_title.as_bytes());
    assert_eq!(
        testing::control(address, "GET", "/_lane/codex/mark", "").json(),
        json!({ "mark": 1 })
    );
}

#[test]
fn the_turns_after_a_mark_name_their_model_and_effort_or_say_that_they_are_unparsed() {
    let service = start(0, TOKEN).unwrap();
    let address = service.address();
    testing::call(address, "POST", "/v1/responses", &[], br#"{"model":"before"}"#);
    let mark = testing::control(address, "GET", "/_lane/codex/mark", "").json()["mark"].clone();
    assert_eq!(mark, 1);
    testing::call(address, "POST", "/v1/responses", &[], br#"{"model":"gpt-5.5-codex"}"#);
    testing::call(address, "POST", "/v1/responses", &[], b"not json");
    let turns = testing::control(address, "GET", &format!("/_lane/codex/turns?since={mark}"), "").json();
    assert_eq!(
        turns,
        json!({ "mark": 3, "turns": [
            { "sequence": 2, "model": "gpt-5.5-codex", "effort": null, "parsed": true },
            { "sequence": 3, "model": null, "effort": null, "parsed": false },
        ] })
    );
    let answer = testing::control(address, "GET", "/_lane/codex/turns?since=x", "");
    assert_eq!((answer.status, answer.json()["error"]["code"].clone()), (400, "bad_request".into()));
}

#[test]
fn the_models_route_lists_the_mock_model_and_a_wrong_method_is_405() {
    let service = start(0, TOKEN).unwrap();
    let address = service.address();
    let models = testing::get(address, "/v1/models");
    assert_eq!(
        models.json(),
        json!({ "object": "list", "data": [{ "id": "mock-model", "object": "model" }] })
    );
    let answer = testing::call(address, "POST", "/v1/models", &[], b"");
    assert_eq!((answer.status, answer.text()), (405, "Method Not Allowed".to_owned()));
    assert_eq!(testing::get(address, "/v1/responses").status, 405);
    assert_eq!(testing::get(address, "/v2").status, 404);
}

#[test]
fn the_lane_port_is_clear_of_the_bridge_port_the_emulator_range_and_the_ephemeral_ports() {
    assert_ne!(PORT, 31_347);
    assert!(!(32_000..32_500).contains(&PORT));
    const { assert!(PORT < 32_768) };
}
