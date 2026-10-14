use std::net::SocketAddr;
use std::thread;
use std::time::{Duration, Instant};

use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;
use crate::lane::services::testing::{self, TOKEN, fixture_packages, golden};

fn agents() -> Vec<RegistryAgent> {
    vec![
        RegistryAgent {
            registry_id: "codex-acp".to_owned(),
            display_name: "Codex".to_owned(),
            environment: vec![
                ("ACP_FAKE_AGENT_CONTROL_URL".to_owned(), "ws://127.0.0.1:30042/acp".to_owned()),
                ("ACP_FAKE_AGENT_CONTROL_KEY".to_owned(), "codex".to_owned()),
            ],
        },
        RegistryAgent {
            registry_id: "air-scenario-gemini".to_owned(),
            display_name: "Air \"Controlled\" gemini".to_owned(),
            environment: vec![(STARTUP_TOKEN_ENV.to_owned(), "overridden".to_owned())],
        },
    ]
}

fn start_registry() -> Service<Registry> {
    start(0, TOKEN, fixture_packages(), agents()).unwrap()
}

#[test]
fn the_registry_document_is_the_golden() {
    let text = registry_json("http://127.0.0.1:26123/", &fixture_packages(), &agents(), None);
    serde_json::from_str::<Value>(&text).expect("the document is JSON");
    expect_test::expect_file![golden("registry.json")].assert_eq(&text);
}

#[test]
fn the_registry_and_the_packages_answer_with_the_archives_of_the_document() {
    let service = start_registry();
    let address = service.address();
    let answer = testing::get(address, "/registry.json");
    assert_eq!((answer.status, answer.header("Content-Type")), (200, Some("application/json")));
    assert_eq!(
        answer.text(),
        registry_json(&format!("http://{address}/"), &fixture_packages(), &agents(), None)
    );
    let document = answer.json();
    let linux = &document["agents"][0]["distribution"]["binary"]["linux-x86_64"];
    let archive = linux["archive"].as_str().unwrap();
    let path = archive.strip_prefix(&format!("http://{address}")).unwrap();
    let package = testing::get(address, path);
    assert_eq!((package.status, package.header("Content-Type")), (200, Some("application/zip")));
    assert_eq!(package.body(), fixture_packages().archive("linux-x86_64").unwrap());
    assert_eq!(linux["sha256"], package::sha256_hex(&package.body()));
    assert_eq!(testing::get(address, "/packages/acp-scenario-agent-solaris-sparc.zip").status, 404);
    assert_eq!(testing::get(address, "/elsewhere").status, 404);
}

#[test]
fn the_manifest_environment_puts_the_package_variables_first_and_an_agent_value_replaces_one_in_place() {
    let text = registry_json("http://127.0.0.1:1/", &fixture_packages(), &agents(), None);
    let document: Value = serde_json::from_str(&text).unwrap();
    let env = |agent: usize| document["agents"][agent]["distribution"]["binary"]["darwin-aarch64"]["env"].to_string();
    assert_eq!(
        env(0),
        r#"{"ACP_FAKE_AGENT_CONTROL_KEY":"codex","ACP_FAKE_AGENT_CONTROL_URL":"ws://127.0.0.1:30042/acp","ACP_FAKE_AGENT_PACKAGE_TOKEN":"air-scenario-package-v1","ACP_FAKE_AGENT_STARTUP_TOKEN":"registry-installed"}"#
    );
    assert!(
        text.contains(r#""env":{"ACP_FAKE_AGENT_PACKAGE_TOKEN":"air-scenario-package-v1","ACP_FAKE_AGENT_STARTUP_TOKEN":"overridden"}"#)
    );
}

fn arm(address: SocketAddr, body: &Value) -> Value {
    let answer = testing::control(address, "POST", "/_lane/registry/fault", &body.to_string());
    assert_eq!(answer.status, 200, "{}", answer.text());
    answer.json()
}

#[test]
fn each_fault_shapes_the_answer_that_the_ide_reads() {
    let service = start_registry();
    let address = service.address();
    let package = "/packages/acp-scenario-agent-linux-aarch64.zip";
    let archive = fixture_packages().archive("linux-aarch64").unwrap().to_vec();

    arm(address, &json!({ "fault": "registry-http-error" }));
    assert_eq!(testing::get(address, "/registry.json").status, 503);
    arm(address, &json!({ "fault": "registry-http-error", "status": 500 }));
    assert_eq!(testing::get(address, "/registry.json").status, 500);
    assert_eq!(testing::get(address, package).body(), archive);

    arm(address, &json!({ "fault": "package-http-error" }));
    assert_eq!(testing::get(address, package).status, 404);
    arm(address, &json!({ "fault": "package-http-error", "status": 503 }));
    assert_eq!(testing::get(address, package).status, 503);
    assert_eq!(testing::get(address, "/registry.json").status, 200);

    arm(address, &json!({ "fault": "checksum-mismatch" }));
    let document = testing::get(address, "/registry.json").json();
    assert_eq!(
        document["agents"][1]["distribution"]["binary"]["windows-aarch64"]["sha256"],
        "0".repeat(64)
    );
    assert_eq!(testing::get(address, package).body(), archive);

    arm(address, &json!({ "fault": "invalid-archive" }));
    let invalid = testing::get(address, package).body();
    assert_eq!(invalid, b"not-a-zip-archive");
    let document = testing::get(address, "/registry.json").json();
    assert_eq!(
        document["agents"][0]["distribution"]["binary"]["linux-aarch64"]["sha256"],
        package::sha256_hex(&invalid)
    );

    arm(address, &json!({ "fault": "truncated-response" }));
    let truncated = testing::get(address, package);
    assert_eq!(truncated.header("Content-Length"), Some(archive.len().to_string().as_str()));
    assert_eq!(truncated.raw_body, archive[..archive.len() / 2]);

    arm(address, &json!({ "fault": "malformed-registry" }));
    assert_eq!(testing::get(address, "/registry.json").text(), "{this is not valid registry json");
    assert_eq!(testing::get(address, package).body(), archive);
}

#[test]
fn a_held_package_answers_on_the_clear_and_enters_the_log_only_then() {
    let service = start_registry();
    let address = service.address();
    let armed = arm(address, &json!({ "fault": "slow-package-response", "holdMs": 120_000 }));
    assert_eq!(
        armed,
        json!({ "fault": "slow-package-response", "registryStatus": 503, "packageStatus": 404, "holdMs": 120_000 })
    );
    let mark = testing::control(address, "GET", "/_lane/registry/mark", "").json()["mark"]
        .as_u64()
        .unwrap();

    // The index answers at once under this fault.
    assert_eq!(testing::get(address, "/registry.json").status, 200);
    let started = Instant::now();
    let held = thread::spawn(move || testing::get(address, "/packages/acp-scenario-agent-darwin-x86_64.zip"));
    thread::sleep(Duration::from_millis(300));
    let events = testing::control(
        address,
        "GET",
        &format!("/_lane/registry/events?since={mark}&prefix=/packages/"),
        "",
    )
    .json();
    assert_eq!(events["events"], json!([]), "a held request is not logged before its answer");

    let cleared = testing::control(address, "DELETE", "/_lane/registry/fault", "").json();
    assert_eq!(
        cleared,
        json!({ "fault": "none", "registryStatus": 503, "packageStatus": 404, "holdMs": 30_000 })
    );
    let answer = held.join().unwrap();
    assert_eq!(answer.status, 200);
    assert!(started.elapsed() < Duration::from_secs(60), "the clear released the hold");

    let events = testing::control(
        address,
        "GET",
        &format!("/_lane/registry/events?since={mark}&prefix=/packages/"),
        "",
    )
    .json();
    let events = events["events"].as_array().unwrap();
    assert_eq!(events.len(), 1);
    assert_eq!(events[0]["path"], "/packages/acp-scenario-agent-darwin-x86_64.zip");
    assert_eq!(events[0]["fault"], "slow-package-response");
    assert_eq!(events[0]["sequence"], mark + 2);
}

#[test]
fn a_held_index_answers_after_its_hold_ends_unreleased() {
    let service = start_registry();
    let address = service.address();
    arm(address, &json!({ "fault": "slow-response", "holdMs": 200 }));
    let started = Instant::now();
    assert_eq!(testing::get(address, "/registry.json").status, 200);
    assert!(started.elapsed() >= Duration::from_millis(200));
}

#[test]
fn a_stopped_registry_releases_a_held_answer() {
    let service = start_registry();
    let address = service.address();
    arm(address, &json!({ "fault": "slow-response", "holdMs": 120_000 }));
    let held = thread::spawn(move || testing::get(address, "/registry.json"));
    thread::sleep(Duration::from_millis(200));
    let started = Instant::now();
    drop(service);
    assert_eq!(held.join().unwrap().status, 200);
    assert!(started.elapsed() < Duration::from_secs(60));
}

#[test]
fn the_log_lists_every_answer_after_the_mark_and_no_control_request() {
    let service = start_registry();
    let address = service.address();
    testing::get(address, "/registry.json");
    let mark = testing::control(address, "GET", "/_lane/registry/mark", "").json()["mark"]
        .as_u64()
        .unwrap();
    assert_eq!(mark, 1);
    testing::get(address, "/packages/acp-scenario-agent-linux-x86_64.zip");
    testing::get(address, "/nothing");
    let all = testing::control(address, "GET", "/_lane/registry/events", "").json();
    assert_eq!(all["mark"], 3);
    let since = testing::control(address, "GET", "/_lane/registry/events?since=1", "").json();
    let events: Vec<(Value, Value, Value, Value)> = since["events"]
        .as_array()
        .unwrap()
        .iter()
        .map(|event| {
            (
                event["sequence"].clone(),
                event["path"].clone(),
                event["status"].clone(),
                event["truncated"].clone(),
            )
        })
        .collect();
    assert_eq!(
        events,
        [
            (
                json!(2),
                json!("/packages/acp-scenario-agent-linux-x86_64.zip"),
                json!(200),
                json!(false)
            ),
            (json!(3), json!("/nothing"), json!(404), json!(false)),
        ]
    );
    let event = &since["events"][0];
    assert_eq!(event["method"], "GET");
    assert_eq!(event["fault"], "none");
    assert_eq!(event["responseBytes"], fixture_packages().archive("linux-x86_64").unwrap().len());
    assert!(event["receivedAtMs"].as_u64().unwrap() > 1_700_000_000_000);
}

#[test]
fn an_unknown_fault_or_an_option_that_the_fault_does_not_take_is_refused() {
    let service = start_registry();
    let address = service.address();
    let answer = testing::control(address, "POST", "/_lane/registry/fault", r#"{"fault":"flaky"}"#);
    assert_eq!(
        (answer.status, answer.json()["error"]["code"].clone()),
        (400, "unknown_fault".into())
    );
    assert_eq!(
        answer.json()["error"]["message"],
        "the fault \"flaky\" is not one of none, registry-http-error, package-http-error, checksum-mismatch, invalid-archive, truncated-response, slow-response, slow-package-response, malformed-registry"
    );
    for body in [
        r#"{"fault":"checksum-mismatch","status":500}"#,
        r#"{"fault":"package-http-error","holdMs":10}"#,
        r#"{"fault":"package-http-error","status":42}"#,
        r#"{"fault":"slow-response","holdMs":0}"#,
    ] {
        let answer = testing::control(address, "POST", "/_lane/registry/fault", body);
        assert_eq!(
            (answer.status, answer.json()["error"]["code"].clone()),
            (400, "bad_request".into()),
            "{body}"
        );
    }
    assert_eq!(
        testing::control(address, "GET", "/_lane/registry/fault", "").json()["fault"],
        "none"
    );
}
