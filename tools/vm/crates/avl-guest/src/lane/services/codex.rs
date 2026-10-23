//! The model that a real Codex CLI in the lane IDE talks to: the Responses API of a mock provider on [PORT].
//!
//! Every turn gets the same completed reply, [REPLY], as an event stream. The service logs the request of each turn,
//! so a scenario reads the model and the reasoning effort that Codex asked for. A title request of Codex gets a title
//! and is not a turn.

use std::sync::{Mutex, MutexGuard};

use serde::Serialize;
use serde_json::{Value, json};

use super::control;
use super::http::{Request, Response};
use super::{Handler, Service};
use crate::reply::AgentRefusal;

#[cfg(test)]
mod tests;

/// The name of the service in its control route and its threads.
pub(crate) const NAME: &str = "codex";

/// The fixed loopback port of the model, which the lane's `.codex/config.toml` names.
pub(crate) const PORT: u16 = 31_500;

/// What the model answers to any prompt. The bridge spells it `AIR_LANE_CODEX_REPLY`.
pub(crate) const REPLY: &str = "Air UI lane Codex reply";

/// The model id that `/v1/models` lists.
pub(crate) const MODEL: &str = "mock-model";

const TITLE: &str = r#"{"title":"Mock session"}"#;

/// One turn as the control route lists it: the `model` and the `reasoning.effort` of its request. `parsed` is false
/// for a request that is no JSON object.
#[derive(Debug, Serialize)]
pub(crate) struct Turn {
    pub sequence: usize,
    pub model: Option<String>,
    pub effort: Option<String>,
    pub parsed: bool,
}

/// The handler of the Codex service.
pub(crate) struct Codex {
    token: String,
    requests: Mutex<Vec<String>>,
}

/// Starts the model on `127.0.0.1:<port>`. A lane passes [PORT].
pub(crate) fn start(port: u16, token: &str) -> Result<Service<Codex>, AgentRefusal> {
    Service::start(NAME, port, |_| Codex {
        token: token.to_owned(),
        requests: Mutex::new(Vec::new()),
    })
}

impl Handler for Codex {
    fn control_route(&self) -> Option<(&'static str, &str)> {
        Some((NAME, &self.token))
    }

    fn control(&self, request: &Request, route: &str) -> Response {
        match (route, request.method.as_str()) {
            ("mark", "GET") => control::answer(&json!({ "mark": self.lock().len() })),
            ("turns", "GET") => match control::since(request) {
                Ok(since) => {
                    let requests = self.lock();
                    let turns: Vec<Turn> = control::after(&requests, since)
                        .iter()
                        .enumerate()
                        .map(|(index, body)| turn(since + index + 1, body))
                        .collect();
                    control::answer(&json!({ "mark": requests.len(), "turns": turns }))
                }
                Err(refusal) => refusal,
            },
            ("mark" | "turns", _) => control::method_not_allowed(request, "GET"),
            _ => control::unknown_route(request),
        }
    }

    fn handle(&self, request: &Request) -> Response {
        if request.path.starts_with("/v1/models") {
            if request.method != "GET" {
                return method_not_allowed();
            }
            let models = json!({ "object": "list", "data": [{ "id": MODEL, "object": "model" }] });
            return Response::with_body(200, "application/json", models.to_string());
        }
        if request.path.starts_with("/v1/responses") {
            if request.method != "POST" {
                return method_not_allowed();
            }
            let body = request.body_text();
            let message = if is_title_request(&body) {
                TITLE
            } else {
                self.lock().push(body);
                REPLY
            };
            return Response::chunked(200, "text/event-stream", completed_reply(message).into_bytes()).header("Cache-Control", "no-cache");
        }
        Response::with_body(404, "text/plain", Vec::new())
    }
}

impl Codex {
    fn lock(&self) -> MutexGuard<'_, Vec<String>> {
        self.requests.lock().unwrap_or_else(std::sync::PoisonError::into_inner)
    }
}

fn method_not_allowed() -> Response {
    Response::with_body(405, "text/plain; charset=utf-8", "Method Not Allowed")
}

fn turn(sequence: usize, body: &str) -> Turn {
    let request = serde_json::from_str::<Value>(body).ok().filter(Value::is_object);
    let text = |value: Option<&Value>| value.and_then(Value::as_str).map(str::to_owned);
    Turn {
        sequence,
        model: text(request.as_ref().and_then(|request| request.get("model"))),
        effort: text(request.as_ref().and_then(|request| request.pointer("/reasoning/effort"))),
        parsed: request.is_some(),
    }
}

/// Whether `body` is the title request of Codex: its `text.format.schema.required` is exactly `["title"]`.
fn is_title_request(body: &str) -> bool {
    serde_json::from_str::<Value>(body)
        .ok()
        .and_then(|request| request.pointer("/text/format/schema/required").cloned())
        .is_some_and(|required| required == json!(["title"]))
}

/// The event stream of a completed turn whose one assistant message is `message`: `response.created`,
/// `response.output_item.done` and `response.completed`.
pub(crate) fn completed_reply(message: &str) -> String {
    let message = Value::from(message);
    let events = [
        (
            "response.created",
            r#"{"type":"response.created","response":{"id":"resp-1"}}"#.to_owned(),
        ),
        (
            "response.output_item.done",
            format!(
                r#"{{"type":"response.output_item.done","item":{{"type":"message","role":"assistant","id":"msg-1","content":[{{"type":"output_text","text":{message}}}]}}}}"#
            ),
        ),
        (
            "response.completed",
            concat!(
                r#"{"type":"response.completed","response":{"id":"resp-1","usage":{"input_tokens":0,"input_tokens_details":null,"#,
                r#""output_tokens":0,"output_tokens_details":null,"total_tokens":0}}}"#
            )
            .to_owned(),
        ),
    ];
    events
        .iter()
        .map(|(event, data)| format!("event: {event}\ndata: {data}\n\n"))
        .collect()
}
