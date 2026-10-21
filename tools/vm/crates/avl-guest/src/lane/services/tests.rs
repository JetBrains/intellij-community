//! The recorded exchanges of the control route of the four services, `testdata/lane-services/control-exchanges.json`.
//!
//! The document is a script of requests to the four services, each with the status of its answer, and the answer text
//! of each control request. The Kotlin client of the IDE reads these answer bytes in `AirLaneServicesClientTest`. So
//! both sides of the route protocol read one set of bytes.

use std::net::SocketAddr;

use serde::Serialize;

use super::registry::RegistryAgent;
use super::testing::{self, TOKEN, fixture_packages, golden};
use super::{cloud, codex, feedback, registry};

/// The time of every event in the document, because the time of a request is not a fact of the protocol.
const RECEIVED_AT_MS: &str = "\"receivedAtMs\":1760000000000";

/// Who sends a request: the IDE, a control client with the token, or a control client without it.
#[derive(Clone, Copy, Serialize)]
#[serde(rename_all = "camelCase")]
enum Side {
    Ide,
    Control,
    Anonymous,
}

/// One request of the script and what the service answered.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Step {
    service: &'static str,
    side: Side,
    method: &'static str,
    target: &'static str,
    body: &'static str,
    status: u16,
    /// The answer text of a control request. The answer to a request of the IDE is not part of the control route.
    #[serde(skip_serializing_if = "Option::is_none")]
    answer: Option<String>,
}

#[derive(Serialize)]
struct Document {
    steps: Vec<Step>,
}

const SCRIPT: &[(&str, Side, &str, &str, &str)] = &[
    ("registry", Side::Control, "GET", "/_lane/registry/mark", ""),
    ("registry", Side::Ide, "GET", "/nothing.txt", ""),
    (
        "registry",
        Side::Control,
        "POST",
        "/_lane/registry/fault",
        r#"{"fault":"package-http-error"}"#,
    ),
    ("registry", Side::Ide, "GET", "/packages/acp-scenario-agent-linux-x86_64.zip", ""),
    ("registry", Side::Control, "GET", "/_lane/registry/mark", ""),
    ("registry", Side::Control, "GET", "/_lane/registry/events?since=0&prefix=", ""),
    (
        "registry",
        Side::Control,
        "GET",
        "/_lane/registry/events?since=1&prefix=%2Fpackages%2F",
        "",
    ),
    ("registry", Side::Control, "GET", "/_lane/registry/fault", ""),
    (
        "registry",
        Side::Control,
        "POST",
        "/_lane/registry/fault",
        r#"{"fault":"slow-package-response","holdMs":5}"#,
    ),
    (
        "registry",
        Side::Control,
        "POST",
        "/_lane/registry/fault",
        r#"{"fault":"registry-http-error","status":500}"#,
    ),
    (
        "registry",
        Side::Control,
        "POST",
        "/_lane/registry/fault",
        r#"{"fault":"no-such-fault"}"#,
    ),
    (
        "registry",
        Side::Control,
        "POST",
        "/_lane/registry/fault",
        r#"{"fault":"none","status":500}"#,
    ),
    ("registry", Side::Control, "DELETE", "/_lane/registry/fault", ""),
    ("registry", Side::Control, "GET", "/_lane/registry/events?since=x", ""),
    ("registry", Side::Control, "PUT", "/_lane/registry/mark", ""),
    ("registry", Side::Control, "GET", "/_lane/registry/unknown", ""),
    ("registry", Side::Anonymous, "GET", "/_lane/registry/mark", ""),
    ("cloud", Side::Ide, "GET", "/jcp/user-management/api/ai-access-options", ""),
    ("cloud", Side::Ide, "GET", "/nothing", ""),
    ("cloud", Side::Control, "GET", "/_lane/cloud/record", ""),
    ("cloud", Side::Control, "DELETE", "/_lane/cloud/record", ""),
    ("cloud", Side::Control, "GET", "/_lane/cloud/record", ""),
    ("cloud", Side::Control, "POST", "/_lane/cloud/record", ""),
    ("feedback", Side::Control, "GET", "/_lane/feedback/submissions", ""),
    ("feedback", Side::Ide, "POST", "/feedback", "first submission"),
    ("feedback", Side::Control, "GET", "/_lane/feedback/submissions", ""),
    ("feedback", Side::Control, "POST", "/_lane/feedback/next", r#"{"waitMs":0}"#),
    ("feedback", Side::Control, "POST", "/_lane/feedback/next", r#"{"waitMs":1}"#),
    ("feedback", Side::Ide, "POST", "/feedback", "second submission"),
    ("feedback", Side::Control, "DELETE", "/_lane/feedback/submissions", ""),
    ("feedback", Side::Control, "POST", "/_lane/feedback/next", r#"{"waitMs":300001}"#),
    ("codex", Side::Control, "GET", "/_lane/codex/mark", ""),
    (
        "codex",
        Side::Ide,
        "POST",
        "/v1/responses",
        r#"{"model":"gpt-5.4","reasoning":{"effort":"high"},"input":[]}"#,
    ),
    ("codex", Side::Ide, "POST", "/v1/responses", "[]"),
    (
        "codex",
        Side::Ide,
        "POST",
        "/v1/responses",
        r#"{"text":{"format":{"schema":{"required":["title"]}}}}"#,
    ),
    ("codex", Side::Control, "GET", "/_lane/codex/mark", ""),
    ("codex", Side::Control, "GET", "/_lane/codex/turns?since=0", ""),
    ("codex", Side::Control, "GET", "/_lane/codex/turns?since=1", ""),
    ("codex", Side::Control, "GET", "/_lane/codex/turns?since=x", ""),
];

#[test]
fn the_control_exchanges_are_the_golden() {
    let agent = RegistryAgent {
        registry_id: "air-scenario-agent".to_owned(),
        display_name: "Air scenario agent".to_owned(),
        environment: Vec::new(),
    };
    let registry = registry::start(0, TOKEN, fixture_packages(), vec![agent]).unwrap();
    let cloud = cloud::start(0, TOKEN, cloud::Organization::None).unwrap();
    let feedback = feedback::start(0, TOKEN).unwrap();
    let codex = codex::start(0, TOKEN).unwrap();
    let address = |service: &str| -> SocketAddr {
        match service {
            "registry" => registry.address(),
            "cloud" => cloud.address(),
            "feedback" => feedback.address(),
            "codex" => codex.address(),
            other => panic!("no service {other}"),
        }
    };
    let steps = SCRIPT
        .iter()
        .map(|&(service, side, method, target, body)| {
            let address = address(service);
            let answer = match side {
                Side::Control => testing::control(address, method, target, body),
                Side::Ide | Side::Anonymous => testing::call(address, method, target, &[], body.as_bytes()),
            };
            Step {
                service,
                side,
                method,
                target,
                body,
                status: answer.status,
                answer: match side {
                    Side::Ide => None,
                    Side::Control | Side::Anonymous => Some(normalized(&answer.text())),
                },
            }
        })
        .collect();
    let mut document = serde_json::to_string_pretty(&Document { steps }).unwrap();
    document.push('\n');
    expect_test::expect_file![golden("control-exchanges.json")].assert_eq(&document);
}

/// `text` with [RECEIVED_AT_MS] for the time of each event.
fn normalized(text: &str) -> String {
    const KEY: &str = "\"receivedAtMs\":";
    let mut normalized = String::with_capacity(text.len());
    let mut rest = text;
    while let Some(start) = rest.find(KEY) {
        normalized.push_str(&rest[..start]);
        normalized.push_str(RECEIVED_AT_MS);
        rest = rest[start + KEY.len()..].trim_start_matches(|digit: char| digit.is_ascii_digit());
    }
    normalized.push_str(rest);
    normalized
}
