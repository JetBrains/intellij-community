//! The control route `/_lane/<service>/<route>` that every lane service has, and its token rule.
//!
//! A control request carries the lane-service token in [TOKEN_HEADER], the convention of the bridge's own token
//! header. Every answer is JSON. A refusal is `{"error": {"code", "message"}}` with a 4xx status. The IDE's own
//! requests never reach this route, and a control request never enters a record of the service.

use serde::Serialize;
use serde::de::DeserializeOwned;
use serde_json::json;

use super::http::{Request, Response};

#[cfg(test)]
mod tests;

/// Carries the lane-service token, `lane_secret(key, "air-lane-services")` of the lane identity. `lane-services.json`
/// gives it to the IDE.
pub(crate) const TOKEN_HEADER: &str = "X-Air-Lane-Service-Token";

/// The path prefix of the control route.
pub(crate) const PREFIX: &str = "/_lane/";

/// The media type of every control answer.
pub(crate) const JSON: &str = "application/json";

/// The control route of `service` that `request` asks for, without the prefix, when the path is a control path.
/// `None` is a request of the IDE. An `Err` is the refusal of a request without the token or for another service.
pub(crate) fn route<'r>(request: &'r Request, service: &str, token: &str) -> Option<Result<&'r str, Response>> {
    let rest = request.path.strip_prefix(PREFIX)?;
    if request.header(TOKEN_HEADER) != Some(token) {
        return Some(Err(refuse(
            401,
            "token_refused",
            &format!("a control request needs the lane-service token in {TOKEN_HEADER}"),
        )));
    }
    Some(
        rest.strip_prefix(service)
            .and_then(|rest| rest.strip_prefix('/'))
            .ok_or_else(|| unknown_route(request)),
    )
}

/// The 200 answer with `value` as JSON.
pub(crate) fn answer(value: &impl Serialize) -> Response {
    match serde_json::to_vec(value) {
        Ok(body) => Response::with_body(200, JSON, body),
        Err(error) => refuse(500, "answer_failed", &format!("the answer cannot be written: {error}")),
    }
}

/// The refusal `{"error": {"code", "message"}}` with `status`.
pub(crate) fn refuse(status: u16, code: &str, message: &str) -> Response {
    let body = json!({ "error": { "code": code, "message": message } }).to_string();
    Response::with_body(status, JSON, body)
}

/// The 404 refusal of a control route that the service does not have.
pub(crate) fn unknown_route(request: &Request) -> Response {
    refuse(
        404,
        "unknown_route",
        &format!("the lane service has no control route {} {}", request.method, request.path),
    )
}

/// The 405 refusal of a known control route with another method.
pub(crate) fn method_not_allowed(request: &Request, allowed: &str) -> Response {
    refuse(
        405,
        "method_not_allowed",
        &format!("the control route {} takes {allowed}, not {}", request.path, request.method),
    )
}

/// The 400 refusal of a request that the route cannot read.
pub(crate) fn bad_request(message: &str) -> Response {
    refuse(400, "bad_request", message)
}

/// The JSON body of `request` as `T`. An empty body reads as `{}`.
pub(crate) fn body<T: DeserializeOwned>(request: &Request) -> Result<T, Response> {
    let body: &[u8] = if request.body.is_empty() { b"{}" } else { &request.body };
    serde_json::from_slice(body).map_err(|error| bad_request(&format!("the body of {} is not valid: {error}", request.path)))
}

/// The query parameter `since` of `request`: a mark that a `mark` route answered. A request without it reads from the
/// start.
pub(crate) fn since(request: &Request) -> Result<usize, Response> {
    match request.query_param("since") {
        None => Ok(0),
        Some(value) => value
            .parse::<usize>()
            .map_err(|_invalid| bad_request(&format!("the mark {value:?} is not a whole number"))),
    }
}

/// The entries of `entries` after the mark `since`. The mark of an entry is its position counted from 1, and the mark
/// of a service is the number of its entries.
pub(crate) fn after<T>(entries: &[T], since: usize) -> &[T] {
    entries.get(since..).unwrap_or_default()
}
