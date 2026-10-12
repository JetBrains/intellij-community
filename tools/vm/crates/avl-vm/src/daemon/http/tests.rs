use std::sync::Mutex;

use pretty_assertions::assert_eq;

use super::*;
use crate::daemon::fixture::Fixture;

// The daemon's account is used only when it is the daemon this controller launched, unquiesced; every other answer
// is "start a fresh one" - except a protocol this controller cannot read, which refuses by name.
#[tokio::test]
async fn daemon_status_answers_only_the_daemon_this_controller_launched() {
    let fixture = Fixture::new().await;
    let ctx = Ctx::background();
    let state = fixture.daemon.host_state("run-1", "launch-1");
    let daemon = fixture.host.daemon();
    let status = daemon.status(&ctx, &state).await.unwrap().expect("a healthy daemon");
    assert_eq!(status.controller_launch_digest, "launch-1");
    assert!(fixture.daemon.saw_request("GET /status"));

    let mut moved = state.clone();
    moved.launch_digest = "launch-2".to_owned();
    assert_eq!(daemon.status(&ctx, &moved).await.unwrap(), None, "another launch");
    let mut rebooted = state.clone();
    rebooted.daemon_boot_stamp = "boot-2".to_owned();
    assert_eq!(daemon.status(&ctx, &rebooted).await.unwrap(), None, "another boot");
    let mut forged = state.clone();
    forged.token = "wrong".to_owned();
    assert_eq!(daemon.status(&ctx, &forged).await.unwrap(), None, "a 403");

    fixture.daemon.script().mount_quiesced = true;
    assert_eq!(daemon.status(&ctx, &state).await.unwrap(), None, "quiesced");
    fixture.daemon.script().mount_quiesced = false;

    fixture.daemon.script().protocol_version = 99;
    let failure = daemon.status(&ctx, &state).await.expect_err("a stale protocol");
    assert_eq!(
        (failure.code.as_ref(), failure.exit),
        ("daemon_protocol_unsupported", Exit::SOFTWARE)
    );
    assert!(failure.message.contains("speaks run protocol 99"), "{}", failure.message);

    let mut unreachable = state.clone();
    unreachable.port = 1;
    assert_eq!(daemon.status(&ctx, &unreachable).await.unwrap(), None, "nothing listening");
}

// A route answers with its own status and body, and a daemon nothing listens for is a refusal at 69 naming the
// route, the worker and the port.
#[tokio::test]
async fn daemon_http_answers_the_status_and_refuses_a_dead_port() {
    let fixture = Fixture::new().await;
    let ctx = Ctx::background();
    let state = fixture.daemon.host_state("run-1", "launch-1");
    let daemon = fixture.host.daemon();
    fixture.daemon.script().results_xml = b"<testsuites/>".to_vec();
    let (status, body) = daemon.http(&ctx, &state, &wire::iteration_result("it-1"), None).await.unwrap();
    assert_eq!((status, body.as_ref()), (StatusCode::OK, b"<testsuites/>".as_ref()));
    fixture.daemon.script().results_status = Some(404);
    let (status, _) = daemon.http(&ctx, &state, &wire::iteration_result("it-1"), None).await.unwrap();
    assert_eq!(status, StatusCode::NOT_FOUND);

    let (status, _) = daemon
        .http(&ctx, &state, &wire::MISSING_JARS, Some(Bytes::from_static(br#"{"jars":[]}"#)))
        .await
        .unwrap();
    assert!(status.is_success());
    assert_eq!(fixture.daemon.script().jars_body, br#"{"jars":[]}"#);

    let mut dead = state;
    dead.port = 1;
    let failure = daemon
        .http(&ctx, &dead, &wire::SHUTDOWN, None)
        .await
        .expect_err("nothing listens on port 1");
    assert_eq!((failure.code.as_ref(), failure.exit), ("daemon_http_failed", Exit::UNAVAILABLE));
    let worker = &fixture.worker;
    assert!(
        failure
            .message
            .contains(&format!("POST /shutdown on the daemon at {worker}:1 failed"))
            && failure.message.contains(&format!(
                "the relay to 127.0.0.1:1 inside {worker} did not open: nothing listens on 127.0.0.1:1"
            )),
        "the refusal names the route and the cause under hyper's own words: {}",
        failure.message
    );
}

// A probe names what it saw, because a health poll that failed has to say which repair it needs: only a relay that
// never opened is the path's.
#[tokio::test]
async fn probe_status_names_what_it_saw() {
    let fixture = Fixture::new().await;
    let ctx = Ctx::background();
    let state = fixture.daemon.host_state("run-1", "launch-1");
    let daemon = fixture.host.daemon();
    let probe = async |state: &HostState| daemon.probe_status(&ctx, state).await.unwrap();

    assert!(matches!(probe(&state).await, StatusProbe::Healthy(_)));

    let mut forged = state.clone();
    forged.token = "wrong".to_owned();
    let seen = probe(&forged).await;
    assert!(matches!(&seen, StatusProbe::NotAStatus(detail) if detail == "HTTP 403"), "{seen:?}");
    fixture.daemon.script().status_code = Some(500);
    let seen = probe(&state).await;
    assert!(matches!(&seen, StatusProbe::NotAStatus(detail) if detail == "HTTP 500"), "{seen:?}");
    fixture.daemon.script().status_code = None;

    let mut rebooted = state.clone();
    rebooted.daemon_boot_stamp = "boot-2".to_owned();
    let seen = probe(&rebooted).await;
    assert_eq!(seen.reason(), "other_daemon");
    assert!(seen.detail().contains("boot stamp"), "{}", seen.detail());
    let mut moved = state.clone();
    moved.launch_digest = "launch-2".to_owned();
    let seen = probe(&moved).await;
    assert_eq!(seen.reason(), "other_daemon");
    assert!(seen.detail().contains("launch digest"), "{}", seen.detail());

    fixture.daemon.script().mount_quiesced = true;
    assert!(matches!(probe(&state).await, StatusProbe::Quiesced));
    fixture.daemon.script().mount_quiesced = false;

    let mut unreachable = state.clone();
    unreachable.port = 1;
    let seen = probe(&unreachable).await;
    assert_eq!(seen.reason(), "no_connection");
    assert!(seen.detail().contains("could not connect"), "{}", seen.detail());

    let cancelled = Ctx::background();
    cancelled.token().cancel();
    assert!(matches!(
        daemon.probe_status(&cancelled, &state).await.unwrap(),
        StatusProbe::Interrupted
    ));

    fixture.daemon.script().protocol_version = 99;
    assert_eq!(
        daemon.probe_status(&ctx, &state).await.unwrap_err().code,
        "daemon_protocol_unsupported"
    );
}

// A relay that opens and never answers is no reply rather than no connection: the classifier puts a timeout after
// the connect on the daemon's side of the line.
#[tokio::test]
async fn a_relay_that_never_answers_is_no_reply() {
    let fixture = Fixture::new().await;
    let held = Arc::new(Mutex::new(Vec::new()));
    let holder = Arc::clone(&held);
    fixture.ports.listen(7, move |server_end| holder.lock().unwrap().push(server_end));
    let mut silent = fixture.daemon.host_state("run-1", "launch-1");
    silent.port = 7;
    let endpoint = Endpoint {
        timeout: Some(Duration::from_millis(200)),
        ..wire::STATUS
    };
    let answered = fixture.host.daemon().exchange(&Ctx::background(), &silent, &endpoint, None).await;
    assert!(matches!(answered, Err(Transport::NoReply)), "{answered:?}");
    assert_eq!(held.lock().unwrap().len(), 1, "one relay opened");
}

// An interrupted operation stops waiting on the daemon.
#[tokio::test]
async fn an_interrupted_request_refuses() {
    let fixture = Fixture::new().await;
    let block = tokio_util::sync::CancellationToken::new();
    fixture.daemon.script().block = Some(block.clone());
    let ctx = Ctx::background();
    let state = fixture.daemon.host_state("run-1", "launch-1");
    let stopper = ctx.token().clone();
    let run = wire::RUN;
    let request = fixture.host.daemon().http(&ctx, &state, &run, None);
    let (answered, ()) = tokio::join!(request, async move {
        tokio::task::yield_now().await;
        stopper.cancel();
    });
    let failure = answered.expect_err("an interrupted request");
    assert!(failure.message.contains("was interrupted"), "{}", failure.message);
    block.cancel();
}
