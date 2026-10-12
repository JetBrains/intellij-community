use avl_base::{Backend, Exit, GuestOs};
use avl_host_sys::Ctx;
use avl_host_testkit::{outcome_of, refusal};
use pretty_assertions::assert_eq;
use serde_json::json;

use super::{VNC_TAIL_LINES, command_vnc};
use crate::lane::observe::testing::Fixture;

/// A runtime log whose interesting lines sit at controlled distances from the end, CRLF throughout - which is how
/// the endpoint pattern earns the line split it is applied over.
fn write_tart_log(fixture: &Fixture, worker: &str, lines: &[String]) {
    std::fs::write(fixture.settings.tart_log_path(worker), lines.join("\r\n") + "\r\n").expect("the runtime log is written");
}

#[tokio::test]
async fn vnc_reads_the_first_endpoint_in_the_last_forty_log_lines() {
    let fixture = Fixture::tart_macos();
    fixture.mark_ready("air-macos-1").await;
    let receipt = fixture.lease_receipt("air-macos-1");
    // An endpoint outside the 40-line window must not win: it is a previous boot's.
    let mut lines = vec!["boot: VNC server: vnc://stale.example:5900".to_owned()];
    lines.extend((0..50).map(|index| format!("guest: progress {index}")));
    lines.push("boot: VNC server: VNC://127.0.0.1:5901".to_owned());
    lines.push("boot: also rfb://127.0.0.1:5902".to_owned());
    lines.extend((0..5).map(|_| "guest: trailing".to_owned()));
    write_tart_log(&fixture, "air-macos-1", &lines);

    let outcome = outcome_of(command_vnc(&Ctx::background(), &fixture.manager, Some(&receipt)).await);
    // First match wins, matched case-insensitively, and the CRLF line ending does not travel inside the endpoint.
    assert_eq!(outcome.data["endpoint"], json!("VNC://127.0.0.1:5901"));
    let tail = outcome.data["tail"].as_str().expect("a tail");
    assert_eq!(tail.split('\n').count(), VNC_TAIL_LINES);
    assert!(!tail.contains('\r'), "the tail carries a CR from the CRLF log");
    assert!(!tail.contains("stale.example"), "the tail reaches past the forty-line window");
    assert!(outcome.text.contains("\nEndpoint: VNC://127.0.0.1:5901\n"), "{}", outcome.text);
}

#[tokio::test]
async fn vnc_refuses_a_stopped_worker() {
    let fixture = Fixture::tart_macos();
    // A lease but no live run-process identity: the worker is stopped, and vnc has nothing to point at.
    let receipt = fixture.lease_receipt("air-macos-1");
    let refusal = refusal(command_vnc(&Ctx::background(), &fixture.manager, Some(&receipt)).await);
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("worker_stopped", Exit::FAILURE));
}

#[tokio::test]
async fn vnc_is_refused_on_parallels() {
    let fixture = Fixture::new(Backend::Parallels, GuestOs::Macos);
    let refusal = refusal(command_vnc(&Ctx::background(), &fixture.manager, Some(std::path::Path::new("unused"))).await);
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("unsupported_backend_operation", Exit::USAGE)
    );
}
