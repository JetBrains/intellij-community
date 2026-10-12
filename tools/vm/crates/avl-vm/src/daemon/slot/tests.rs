use pretty_assertions::assert_eq;

use super::*;
use crate::daemon::fixture::Fixture;

/// Records a daemon the HTTP double answers healthy for, under the given run id.
fn record_daemon(fixture: &Fixture, run_id: &str) {
    fixture
        .daemon
        .host_state(run_id, "launch-slot")
        .write(&fixture.settings, &fixture.worker)
        .unwrap();
}

// The probe answers "parked" for exactly one state of the world, and everything else - including every way of
// failing to find out - reads as not parked. That direction is what makes a release refuse rather than free a
// worker out from under an iteration on the strength of a question that failed.
#[tokio::test]
async fn only_an_answering_idle_daemon_reads_as_parked() {
    type Seed = fn(&Fixture);
    let cases: [(&str, Seed, Option<&str>); 5] = [
        ("no daemon recorded on the worker", |_| {}, None),
        (
            "a recorded daemon that does not answer",
            |fixture| {
                record_daemon(fixture, "run-daemon");
                fixture.daemon.script().status_code = Some(500);
            },
            None,
        ),
        (
            "a recorded daemon with an iteration in flight",
            |fixture| {
                record_daemon(fixture, "run-daemon");
                fixture.daemon.script().busy = true;
            },
            None,
        ),
        (
            "a recorded daemon speaking another protocol",
            |fixture| {
                record_daemon(fixture, "run-daemon");
                fixture.daemon.script().protocol_version = 99;
            },
            None,
        ),
        (
            "a recorded daemon, answering, idle",
            |fixture| record_daemon(fixture, "run-daemon"),
            Some("run-daemon"),
        ),
    ];
    for (name, seed, want) in cases {
        let fixture = Fixture::new().await;
        seed(&fixture);
        let probe = parked_daemon_probe(Arc::clone(&fixture.settings), fixture.host.daemon().clone());
        let parked = probe.parked_daemon_run(&Ctx::background(), &fixture.worker).await.unwrap();
        assert_eq!(parked.as_deref(), want, "{name}");
    }
}

// The record is read for the refusal and not for the decision, so it answers whatever is on disk however the daemon
// behind it behaves - that is how a refusal can say "this is the daemon you recorded, retire it" rather than
// "something is running".
#[tokio::test]
async fn the_recorded_run_is_answered_without_asking_the_daemon() {
    let fixture = Fixture::new().await;
    let probe = parked_daemon_probe(Arc::clone(&fixture.settings), fixture.host.daemon().clone());
    assert_eq!(probe.recorded_daemon_run(&fixture.worker), None);
    record_daemon(&fixture, "run-daemon");
    fixture.daemon.script().status_code = Some(500);
    assert_eq!(probe.recorded_daemon_run(&fixture.worker).as_deref(), Some("run-daemon"));
    assert_eq!(fixture.daemon.request_count(), 0, "reading the record asked the daemon");
}
