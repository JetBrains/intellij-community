//! Where the lane IDE sends feedback instead of to production, and the queue that a scenario reads it back from.
//!
//! `POST /feedback` queues the body as one submission. `/browse` exists only to be a URL that the product can put in a
//! bundle link, so it answers 200 and nothing else.

use std::collections::VecDeque;
use std::sync::{Condvar, Mutex, MutexGuard};
use std::time::{Duration, Instant};

use serde::Deserialize;
use serde_json::json;

use super::control;
use super::http::{Request, Response};
use super::{Handler, Service};
use crate::reply::AgentRefusal;

#[cfg(test)]
mod tests;

/// The name of the service in its control route and its threads.
pub(crate) const NAME: &str = "feedback";

/// Bounds the wait of one `next` request.
const MAX_WAIT_MS: u64 = 300_000;

struct State {
    submissions: VecDeque<String>,
    stopped: bool,
}

/// The handler of the feedback service.
pub(crate) struct Feedback {
    base_url: String,
    token: String,
    state: Mutex<State>,
    submitted: Condvar,
}

/// Starts the feedback capture on `127.0.0.1:<port>`.
pub(crate) fn start(port: u16, token: &str) -> Result<Service<Feedback>, AgentRefusal> {
    Service::start(NAME, port, |address| Feedback {
        base_url: format!("http://{address}"),
        token: token.to_owned(),
        state: Mutex::new(State {
            submissions: VecDeque::new(),
            stopped: false,
        }),
        submitted: Condvar::new(),
    })
}

impl Handler for Feedback {
    fn control_route(&self) -> Option<(&'static str, &str)> {
        Some((NAME, &self.token))
    }

    fn control(&self, request: &Request, route: &str) -> Response {
        match (route, request.method.as_str()) {
            ("submissions", "GET") => control::answer(&json!({ "submissions": &self.lock().submissions })),
            ("submissions", "DELETE") => control::answer(&json!({ "submissions": self.clear() })),
            ("next", "POST") => match control::body::<NextRequest>(request) {
                Ok(NextRequest { wait_ms }) if wait_ms <= MAX_WAIT_MS => {
                    control::answer(&json!({ "submission": self.next(Duration::from_millis(wait_ms)) }))
                }
                Ok(NextRequest { wait_ms }) => control::bad_request(&format!("the waitMs {wait_ms} is more than {MAX_WAIT_MS}")),
                Err(refusal) => refusal,
            },
            ("submissions", _) => control::method_not_allowed(request, "GET or DELETE"),
            ("next", _) => control::method_not_allowed(request, "POST"),
            _ => control::unknown_route(request),
        }
    }

    fn handle(&self, request: &Request) -> Response {
        if request.path.starts_with("/feedback") {
            if request.method != "POST" {
                return Response::with_body(
                    405,
                    "text/plain",
                    format!("a feedback submission is a POST, not a {}", request.method),
                );
            }
            self.lock().submissions.push_back(request.body_text());
            self.submitted.notify_all();
            return Response::empty(200);
        }
        if request.path.starts_with("/browse") {
            return Response::empty(200);
        }
        Response::with_body(404, "text/plain", Vec::new())
    }

    fn stop(&self) {
        self.lock().stopped = true;
        self.submitted.notify_all();
    }
}

impl Feedback {
    fn lock(&self) -> MutexGuard<'_, State> {
        self.state.lock().unwrap_or_else(std::sync::PoisonError::into_inner)
    }

    /// The three properties that keep the feedback of the IDE on this machine.
    pub(crate) fn launch_properties(&self) -> Vec<(&'static str, String)> {
        vec![
            (
                "air.feedback.test.bundle.url",
                format!("{}/browse#air-problem-bundle", self.base_url),
            ),
            ("air.feedback.test.logs.url", format!("{}/browse#air-problem-logs", self.base_url)),
            ("platform.feedback.test.submission.url", format!("{}/feedback", self.base_url)),
        ]
    }

    /// Empties the queue, and answers the submissions that it held.
    pub(crate) fn clear(&self) -> Vec<String> {
        self.lock().submissions.drain(..).collect()
    }

    /// Takes the oldest submission, and waits for one at most `wait`.
    fn next(&self, wait: Duration) -> Option<String> {
        let deadline = Instant::now() + wait;
        let mut state = self.lock();
        loop {
            if let Some(submission) = state.submissions.pop_front() {
                return Some(submission);
            }
            let left = deadline.saturating_duration_since(Instant::now());
            if left.is_zero() || state.stopped {
                return None;
            }
            state = self
                .submitted
                .wait_timeout(state, left)
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .0;
        }
    }
}

/// The body of `POST /_lane/feedback/next`.
#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct NextRequest {
    wait_ms: u64,
}
