use avl_wire::daemon::{PROTOCOL_VERSION, Runner};
use pretty_assertions::assert_eq;

use super::*;
use crate::daemon::fixture::Fixture;

/// Records a daemon the HTTP double answers healthy for, under the given run id.
fn record_daemon(fixture: &Fixture, run_id: &str) {
    record_daemon_of(fixture, run_id, Runner::Jvm);
}

/// [`record_daemon`] for a daemon that `runner` started.
fn record_daemon_of(fixture: &Fixture, run_id: &str, runner: Runner) {
    let mut state = fixture.daemon.host_state(run_id, "launch-slot");
    state.runner = Some(runner);
    state.write(&fixture.settings, &fixture.worker).unwrap();
}

// The probe answers "parked" for exactly one state of the world, and everything else - including every way of
// failing to find out - reads as not parked. That direction is what makes a release refuse rather than free a
// worker out from under an iteration on the strength of a question that failed.
#[tokio::test]
async fn only_an_answering_idle_daemon_reads_as_parked() {
    type Seed = fn(&Fixture);
    let cases: [(&str, Seed, Option<&str>); 7] = [
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
        // The probe asks in the protocol of the runner that started the daemon, so a lane daemon is parked for a
        // controller of `--runner jvm` too.
        (
            "a recorded lane daemon, answering, idle",
            |fixture| {
                record_daemon_of(fixture, "run-lane-daemon", Runner::Daemon);
                fixture.daemon.script().protocol_version = PROTOCOL_VERSION;
            },
            Some("run-lane-daemon"),
        ),
        (
            "a daemon that does not speak the protocol of its record",
            |fixture| {
                record_daemon_of(fixture, "run-daemon", Runner::Jvm);
                fixture.daemon.script().protocol_version = PROTOCOL_VERSION;
            },
            None,
        ),
    ];
    for (name, seed, want) in cases {
        let fixture = Fixture::new().await;
        seed(&fixture);
        assert_eq!(
            fixture.host.daemon().runner(),
            Runner::Jvm,
            "the controller of the cases runs the JVM runner"
        );
        let probe = parked_daemon_probe(Arc::clone(&fixture.settings), fixture.host.daemon().clone());
        let parked = probe.parked_daemon_run(&Ctx::background(), &fixture.worker).await.unwrap();
        assert_eq!(parked.as_deref(), want, "{name}");
    }
}

// A record of an older controller names no runner. The probe asks it in the protocol of the caller's own `--runner`, as
// it did before records named one. So the release of a lane daemon run passes, and a command of `--runner jvm` misreads
// that daemon until the next run writes the runner into its record.
#[tokio::test]
async fn a_record_without_a_runner_is_asked_in_the_protocol_of_the_caller() {
    for (caller, want) in [(Runner::Daemon, Some("run-old")), (Runner::Jvm, None)] {
        let fixture = Fixture::new().await;
        let state = fixture.daemon.host_state("run-old", "launch-slot");
        assert_eq!(state.runner, None);
        state.write(&fixture.settings, &fixture.worker).unwrap();
        fixture.daemon.script().protocol_version = PROTOCOL_VERSION;
        let probe = parked_daemon_probe(Arc::clone(&fixture.settings), fixture.host.daemon().for_runner(caller));
        let parked = probe.parked_daemon_run(&Ctx::background(), &fixture.worker).await.unwrap();
        assert_eq!(parked.as_deref(), want, "a caller of {caller}");
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
