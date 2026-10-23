//! The run marker's suite, over the Tart fixture. Hermetic: the process table of the fixture declares each live
//! process of another controller, and the host answers for this test process.

use std::os::unix::fs::PermissionsExt;

use avl_base::Exit;
use pretty_assertions::assert_eq;
use serde_json::Value;

use super::*;
use crate::worker::testing::{Fixture, Notes, ctx};

/// A Tart pool and the lease of its first worker.
fn leased() -> (Fixture, Lease) {
    let fixture = Fixture::macos();
    let lease = fixture.write_lease(fixture.worker(0), "the-token");
    (fixture, lease)
}

// A marker is written at mode 0600, reads back as it was written, and holds the digest of the token, not the token.
#[tokio::test]
async fn a_marker_reads_back_as_it_was_written() {
    let (fixture, lease) = leased();
    let worker = lease.worker.as_str();
    let written = fixture
        .manager
        .write_run_marker(&ctx(), &lease, "suite-user-run-1", "run")
        .await
        .unwrap();
    let path = fixture.settings.run_marker_path(worker);
    assert_eq!(std::fs::metadata(&path).unwrap().permissions().mode() & 0o7777, 0o600);
    assert_eq!(read_run_marker(&fixture.settings, worker).unwrap(), Some(written.clone()));
    // The document's keys, in their order.
    let text = std::fs::read_to_string(&path).unwrap();
    assert!(
        text.starts_with(&format!(
            "{{\"schemaVersion\":1,\"worker\":\"{worker}\",\"runId\":\"suite-user-run-1\",\"operation\":\"run\",\
             \"leaseDigest\":\"{}\",\"process\":{{\"pid\":{},\"processStart\":",
            written.lease_digest, written.process.pid
        )),
        "{text}"
    );
    assert!(text.ends_with("\"}\n") && text.contains("\"startedAt\":"), "{text}");
    assert!(!text.contains("the-token"), "the marker holds a clear copy of the token: {text}");
    assert_eq!(written.lease_digest, sha256_text("the-token"));
    assert!(written.started_at.parse::<jiff::Timestamp>().is_ok(), "{written:?}");
    // The marker names the identity of this process, and the lock records name the same one.
    assert_eq!(fixture.manager.locks().identity(&ctx()).await.unwrap(), Some(&written.process));
    assert!(written.names_lease(&lease));
    assert!(!written.names_lease(&Lease {
        token: "another-token".to_owned(),
        ..lease.clone()
    }));
    // A removal leaves no marker, and a removal of no marker is no error.
    remove_run_marker(&fixture.settings, worker).unwrap();
    assert_eq!(read_run_marker(&fixture.settings, worker).unwrap(), None);
    remove_run_marker(&fixture.settings, worker).unwrap();
}

// A marker of this process is ours without a liveness probe. A marker of another live process is live, and a marker of
// a process that is gone is stale. The whole identity decides, so a dead process with the pid of this one is stale.
#[tokio::test]
async fn a_marker_is_ours_live_or_stale() {
    let (fixture, lease) = leased();
    let worker = lease.worker.as_str();
    let judge = async |manager: &Manager| manager.judge_run_marker(&ctx(), worker).await.unwrap();
    assert_eq!(judge(&fixture.manager).await, RunMarkerState::None);

    let ours = fixture
        .manager
        .write_run_marker(&ctx(), &lease, "suite-user-run-1", "run")
        .await
        .unwrap();
    assert_eq!(judge(&fixture.manager).await, RunMarkerState::Ours(ours.clone()));
    // Another controller over the same pool sees that marker as a live run.
    let another = fixture.another_controller();
    assert_eq!(judge(&another).await, RunMarkerState::Live(ours.clone()));

    let live = fixture.write_run_marker(&lease, "suite-user-run-2", fixture.another_process().await);
    assert_eq!(judge(&fixture.manager).await, RunMarkerState::Live(live));

    let gone = fixture.write_run_marker(&lease, "suite-user-run-3", Fixture::gone_process());
    assert_eq!(judge(&fixture.manager).await, RunMarkerState::Stale(gone));

    let reused = fixture.write_run_marker(
        &lease,
        "suite-user-run-4",
        ProcessIdentity {
            process_start: "Sat Jan 1 00:00:00 2000".to_owned(),
            ..ours.process
        },
    );
    assert_eq!(judge(&fixture.manager).await, RunMarkerState::Stale(reused));
}

// An unreadable or incomplete marker refuses, and the guard keeps it: a marker that cannot be read must never read as
// no run.
#[tokio::test]
async fn an_unreadable_marker_refuses_and_stays() {
    let (fixture, lease) = leased();
    let worker = lease.worker.as_str();
    let path = fixture.settings.run_marker_path(worker);
    let complete = serde_json::to_value(fixture.write_run_marker(&lease, "suite-user-run-1", fixture.another_process().await)).unwrap();
    let edited = |edit: &dyn Fn(&mut Value)| {
        let mut copy = complete.clone();
        edit(&mut copy);
        copy.to_string()
    };
    for (name, content) in [
        ("not JSON", "{".to_owned()),
        ("an empty file", String::new()),
        (
            "a schema this build does not speak",
            edited(&|marker| marker["schemaVersion"] = 99.into()),
        ),
        (
            "the marker of another worker",
            edited(&|marker| marker["worker"] = "air-macos-2".into()),
        ),
        ("no run id", edited(&|marker| marker["runId"] = "".into())),
        ("no lease digest", edited(&|marker| marker["leaseDigest"] = "".into())),
        ("no pid", edited(&|marker| marker["process"]["pid"] = 0.into())),
        ("no start time", edited(&|marker| marker["process"]["processStart"] = "".into())),
        (
            "no start of the run",
            edited(&|marker| {
                marker.as_object_mut().unwrap().remove("startedAt");
            }),
        ),
    ] {
        std::fs::write(&path, content).unwrap();
        let refusal = read_run_marker(&fixture.settings, worker).unwrap_err();
        assert_eq!(
            (refusal.code.as_ref(), refusal.exit),
            ("corrupt_run_marker", Exit::FAILURE),
            "{name}"
        );
        assert!(refusal.message.contains(&path.display().to_string()), "{name}: {refusal}");
        let refusal = fixture.manager.require_no_run(&ctx(), worker, "release").await.unwrap_err();
        assert_eq!(refusal.code, "corrupt_run_marker", "{name}");
        assert!(path.exists(), "{name}: the guard removed a marker it could not read");
    }
}

// The guard refuses a live run of another process and names the run. It removes a stale marker with a note, and a
// marker of this process passes.
#[tokio::test]
async fn require_no_run_refuses_a_live_run_and_removes_a_stale_marker() {
    let (fixture, lease) = leased();
    let worker = lease.worker.as_str();
    let path = fixture.settings.run_marker_path(worker);
    let notes = Notes::start(fixture.manager.reporter());

    let live = fixture.write_run_marker(&lease, "suite-user-run-1", fixture.another_process().await);
    let refusal = fixture.manager.require_no_run(&ctx(), worker, "release").await.unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("lease_busy", Exit::TEMP_FAIL));
    assert_eq!(
        refusal.message,
        format!(
            "worker {worker} has run suite-user-run-1 in flight (pid {} since 2026-10-13T12:00:00.000Z); wait for it to \
             finish",
            live.process.pid
        )
    );
    assert_eq!(
        refusal.details().unwrap(),
        json!({
            "worker": worker,
            "operation": "release",
            "runId": "suite-user-run-1",
            "pid": live.process.pid,
            "startedAt": "2026-10-13T12:00:00.000Z",
        })
    );
    assert!(path.exists(), "the guard removed a live marker");
    assert_eq!(notes.all(), Vec::<String>::new());

    fixture.write_run_marker(&lease, "suite-user-run-2", Fixture::gone_process());
    fixture.manager.require_no_run(&ctx(), worker, "release").await.unwrap();
    assert!(!path.exists(), "the stale marker stayed");
    assert_eq!(
        notes.all(),
        [format!(
            "removed the run marker of suite-user-run-2 on {worker}: its process {} is gone",
            Fixture::gone_process().pid
        )]
    );

    fixture
        .manager
        .write_run_marker(&ctx(), &lease, "suite-user-run-3", "run")
        .await
        .unwrap();
    fixture.manager.require_no_run(&ctx(), worker, "stop").await.unwrap();
    assert!(path.exists(), "the guard removed the marker of this process");
}

// A probe without an answer says nothing about the run, so the guard refuses and keeps the marker.
#[tokio::test]
async fn a_probe_without_an_answer_refuses_the_guard() {
    let (fixture, lease) = leased();
    let worker = lease.worker.as_str();
    let silent = ProcessIdentity {
        pid: fixture.start_unanswered_process(),
        ..Fixture::gone_process()
    };
    fixture.write_run_marker(&lease, "suite-user-run-1", silent);
    let refusal = fixture.manager.require_no_run(&ctx(), worker, "release").await.unwrap_err();
    assert_eq!(refusal.code, "probe_unanswered", "{refusal:?}");
    assert!(fixture.settings.run_marker_path(worker).exists());
}
