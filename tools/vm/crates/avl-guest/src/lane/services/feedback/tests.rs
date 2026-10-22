use std::thread;
use std::time::{Duration, Instant};

use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;
use crate::lane::services::testing::{self, TOKEN};

#[test]
fn a_submission_is_queued_and_the_browse_link_answers_200() {
    let service = start(0, TOKEN).unwrap();
    let address = service.address();
    let answer = testing::call(
        address,
        "POST",
        "/feedback",
        &[("Content-Type", "application/json")],
        br#"{"summary":"one"}"#,
    );
    assert_eq!((answer.status, answer.raw_body.len()), (200, 0));
    let chunked = b"POST /feedback HTTP/1.1\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n3\r\ntwo\r\n0\r\n\r\n";
    assert_eq!(testing::exchange(address, chunked).status, 200);
    assert_eq!(testing::get(address, "/browse").status, 200);
    assert_eq!(testing::get(address, "/feedback").status, 405);
    assert_eq!(testing::get(address, "/other").status, 404);

    let queue = testing::control(address, "GET", "/_lane/feedback/submissions", "").json();
    assert_eq!(queue, json!({ "submissions": [r#"{"summary":"one"}"#, "two"] }));
    let removed = testing::control(address, "DELETE", "/_lane/feedback/submissions", "").json();
    assert_eq!(removed, json!({ "submissions": [r#"{"summary":"one"}"#, "two"] }));
    assert_eq!(
        testing::control(address, "GET", "/_lane/feedback/submissions", "").json(),
        json!({ "submissions": [] })
    );
    let base = service.url();
    assert_eq!(
        service.handler().launch_properties(),
        [
            ("air.feedback.test.bundle.url", format!("{base}/browse#air-problem-bundle")),
            ("air.feedback.test.logs.url", format!("{base}/browse#air-problem-logs")),
            ("platform.feedback.test.submission.url", format!("{base}/feedback")),
        ]
    );
}

#[test]
fn next_takes_the_oldest_submission_and_waits_for_one_until_its_deadline() {
    let service = start(0, TOKEN).unwrap();
    let address = service.address();
    testing::call(address, "POST", "/feedback", &[], b"first");
    let next = testing::control(address, "POST", "/_lane/feedback/next", r#"{"waitMs":0}"#).json();
    assert_eq!(next, json!({ "submission": "first" }));

    let started = Instant::now();
    let none = testing::control(address, "POST", "/_lane/feedback/next", r#"{"waitMs":200}"#).json();
    assert_eq!(none, json!({ "submission": null }));
    assert!(started.elapsed() >= Duration::from_millis(200));

    let waiting = thread::spawn(move || testing::control(address, "POST", "/_lane/feedback/next", r#"{"waitMs":60000}"#).json());
    thread::sleep(Duration::from_millis(200));
    testing::call(address, "POST", "/feedback", &[], b"late");
    assert_eq!(waiting.join().unwrap(), json!({ "submission": "late" }));

    let answer = testing::control(address, "POST", "/_lane/feedback/next", r#"{"waitMs":300001}"#);
    assert_eq!(answer.status, 400);
}

#[test]
fn a_stopped_service_ends_a_waiting_next() {
    let service = start(0, TOKEN).unwrap();
    let address = service.address();
    let waiting = thread::spawn(move || testing::control(address, "POST", "/_lane/feedback/next", r#"{"waitMs":120000}"#).json());
    thread::sleep(Duration::from_millis(200));
    let started = Instant::now();
    drop(service);
    assert_eq!(waiting.join().unwrap(), json!({ "submission": null }));
    assert!(started.elapsed() < Duration::from_secs(60));
}
