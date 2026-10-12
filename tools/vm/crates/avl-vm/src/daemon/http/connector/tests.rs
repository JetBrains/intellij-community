use std::sync::Arc;
use std::time::Duration;

use avl_base::Refusal;
use avl_host_sys::{Channel, Ctx};
use avl_host_testkit::{FakeGuests, serve_on_connect};
use avl_wire::daemon as wire;
use axum::body::Body;
use http_body_util::BodyExt;
use hyper::StatusCode;
use hyper::body::Bytes;
use hyper_util::rt::TokioIo;
use hyper_util::service::TowerToHyperService;
use pretty_assertions::assert_eq;
use tokio::io::DuplexStream;
use tokio::sync::mpsc;

use crate::daemon::http::{DaemonClient, Transport};
use crate::daemon::state::HostState;

const PORT: u16 = 7654;

/// A client over `guests`, the way a host builds one over its manager.
fn client(guests: &Arc<FakeGuests>) -> DaemonClient {
    let guests = Arc::clone(guests);
    DaemonClient::new(Arc::new(move |worker: &str| guests.channel(worker) as Arc<dyn Channel>))
}

/// A daemon record that aims at one worker's guest port.
fn state(worker: &str, port: u16) -> HostState {
    HostState {
        run_id: "run-ui-daemon-1".to_owned(),
        port,
        token: "token".to_owned(),
        worker: worker.to_owned(),
        daemon_boot_stamp: "boot-1".to_owned(),
        runtime_digest: String::new(),
        launch_digest: String::new(),
        last_product_digest: String::new(),
        last_mount_digest: String::new(),
    }
}

/// The server end of each relay: HTTP/1.1 over the stream, served by `router` on a task of its own.
fn served_by(router: axum::Router) -> impl Fn(DuplexStream) + Send + Sync + 'static {
    move |server_end| {
        let service = TowerToHyperService::new(router.clone());
        tokio::spawn(async move {
            let _ = hyper::server::conn::http1::Builder::new()
                .serve_connection(TokioIo::new(server_end), service)
                .await;
        });
    }
}

/// A router that answers every request with `body`.
fn answering(body: &'static str) -> axum::Router {
    axum::Router::new().fallback(move || async move { body })
}

// The URI authority names the worker, so each request travels through that worker's channel and no other.
#[tokio::test]
async fn the_authority_selects_the_workers_channel() {
    let guests = FakeGuests::new();
    guests.channel("worker-a").on_connect(serve_on_connect(served_by(answering("a"))));
    guests.channel("worker-b").on_connect(serve_on_connect(served_by(answering("b"))));
    let daemon = client(&guests);
    let ctx = Ctx::background();
    for (worker, answer) in [("worker-a", "a"), ("worker-b", "b")] {
        let (status, body) = daemon.http(&ctx, &state(worker, PORT), &wire::STATUS, None).await.unwrap();
        assert_eq!((status, body.as_ref()), (StatusCode::OK, answer.as_bytes()));
        assert_eq!(guests.channel(worker).lines(), [format!("relay {PORT}")], "{worker}");
    }
}

// One worker's requests share one relay: the client pools the connection by worker and port.
#[tokio::test]
async fn two_requests_to_one_worker_reuse_one_relay() {
    let guests = FakeGuests::new();
    guests.on_connect(serve_on_connect(served_by(answering("ok"))));
    let daemon = client(&guests);
    let ctx = Ctx::background();
    let target = state("worker-a", PORT);
    for _ in 0..2 {
        let (status, _) = daemon.http(&ctx, &target, &wire::STATUS, None).await.unwrap();
        assert_eq!(status, StatusCode::OK);
        // The connection goes back to the pool on its own task once the body ends.
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
    assert_eq!(guests.channel("worker-a").lines(), [format!("relay {PORT}")]);
}

// A relay that exits before its first byte, and a channel that cannot start one, are both a relay that did not open,
// named by port and worker with the cause under it.
#[tokio::test]
async fn a_refused_relay_is_a_connect_failure_that_names_the_relay() {
    let guests = FakeGuests::new();
    let daemon = client(&guests);
    let ctx = Ctx::background();
    let refused = daemon.exchange(&ctx, &state("worker-a", PORT), &wire::STATUS, None).await;
    let Err(Transport::Connect(detail)) = refused else {
        panic!("a connect failure was expected: {refused:?}");
    };
    assert_eq!(
        detail,
        format!(
            "the relay to 127.0.0.1:{PORT} inside worker-a did not open: nothing listens on 127.0.0.1:{PORT} \
             inside worker-a"
        )
    );

    guests.channel("worker-b").on_connect(|_| {
        Err(Refusal::new(
            "guest_agent_outdated",
            avl_base::Exit::UNAVAILABLE,
            "the installed guest agent has no relay verb (exit 64)",
        ))
    });
    let refused = daemon.exchange(&ctx, &state("worker-b", PORT), &wire::STATUS, None).await;
    let Err(Transport::Connect(detail)) = refused else {
        panic!("a connect failure was expected: {refused:?}");
    };
    assert!(
        detail.starts_with(&format!("the relay to 127.0.0.1:{PORT} inside worker-b did not open: "))
            && detail.contains("the installed guest agent has no relay verb (exit 64)"),
        "{detail}"
    );
}

// A streamed response is read frame by frame: each chunk arrives before the daemon writes the next one.
#[tokio::test]
async fn a_streamed_body_arrives_chunk_by_chunk() {
    let (chunks, receiver) = mpsc::unbounded_channel::<Bytes>();
    let receiver = Arc::new(tokio::sync::Mutex::new(Some(receiver)));
    let router = axum::Router::new().fallback(move || {
        let receiver = Arc::clone(&receiver);
        async move {
            let receiver = receiver.lock().await.take().expect("one streamed request");
            let stream = futures::stream::unfold(receiver, async |mut receiver| {
                let chunk = receiver.recv().await?;
                Some((Ok::<_, std::io::Error>(chunk), receiver))
            });
            Body::from_stream(stream)
        }
    });
    let guests = FakeGuests::new();
    guests.on_connect(serve_on_connect(served_by(router)));
    let daemon = client(&guests);
    let response = daemon.send(&state("worker-a", PORT), &wire::RUN, Bytes::new()).await.unwrap();
    assert_eq!(response.status(), StatusCode::OK);
    let mut body = response.into_body();
    for line in ["one\n", "two\n", "three\n"] {
        chunks.send(Bytes::from_static(line.as_bytes())).unwrap();
        let frame = tokio::time::timeout(Duration::from_secs(5), body.frame())
            .await
            .expect("the chunk arrives before the stream ends")
            .expect("a frame")
            .expect("a readable frame");
        assert_eq!(frame.into_data().ok(), Some(Bytes::from_static(line.as_bytes())));
    }
    drop(chunks);
    let end = body.frame().await;
    assert!(end.is_none(), "{end:?}");
}
