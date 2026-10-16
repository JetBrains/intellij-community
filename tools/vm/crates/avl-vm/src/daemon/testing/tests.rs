use std::time::Duration;

use avl_base::format::words;
use pretty_assertions::assert_eq;
use serde_json::json;

use avl_host_sys::{Ctx, SpawnOptions};
use avl_host_testkit::{answer_exit, answer_text};

use super::DaemonFixture;
use crate::daemon::command::DaemonVerb;
use crate::daemon::fixture::Fixture;

// Over the testing-ui container, the daemon's control channel is a TCP connection to the published host port: a
// `daemon status` reaches the double through it and leaves no trace on the control port, whose exec route carries the
// fixture's guest answers.
#[tokio::test]
async fn the_container_linux_fixture_reaches_the_daemon_through_the_published_port() {
    let fixture = DaemonFixture::over_container_linux(&[]).await;
    let receipt = fixture.lease_receipt();
    let prep = fixture.prepared().await;
    let state = fixture.seed_healthy_daemon(&prep);
    let outcome = fixture
        .host
        .command_daemon(&Ctx::background(), DaemonVerb::Status, Some(&receipt))
        .await
        .unwrap_or_else(|refusal| panic!("{refusal:?}"));
    assert_eq!(outcome.data["reachable"], json!(true), "{outcome:?}");
    assert_eq!(outcome.data["daemon"]["runId"], json!(state.run_id));
    assert!(
        fixture.daemon.saw_request("GET /status"),
        "the daemon double saw {:?}",
        fixture.daemon.script().requests
    );
    let requests = fixture.control_port().requests();
    assert!(
        requests.iter().all(|request| request.starts_with("POST /v1/execute?")),
        "the control port carried only exec: {requests:?}"
    );

    // The exec route carries the scripted guest's answers, as the scripted channel does on Tart.
    fixture.on("df", answer_text("the disk"));
    let channel = fixture.manager.channel(&fixture.worker);
    let df = channel
        .exec(
            &Ctx::background(),
            &words(["/bin/df", "-k"]),
            &SpawnOptions::within(Duration::from_mins(1)),
        )
        .await
        .unwrap();
    assert_eq!(df.stdout, "the disk");
    assert_eq!(fixture.channel().calls_containing("/bin/df"), ["/bin/df -k"]);
    assert!(
        fixture
            .control_port()
            .requests()
            .iter()
            .any(|request| request.contains("arg=%2Fbin%2Fdf")),
        "{:?}",
        fixture.control_port().requests()
    );
}

// The shared fixture routes the agent by verb and everything else by basename, per worker, through the manager.
#[tokio::test]
async fn the_scripted_guest_routes_by_verb_and_records_per_worker() {
    let fixture = Fixture::new().await;
    fixture.on("gc", answer_text("collected"));
    fixture.on("df", answer_exit(3));
    let channel = fixture.manager.channel(&fixture.worker);
    let ctx = Ctx::background();
    let options = SpawnOptions::within(Duration::from_mins(1));
    let agent = fixture.settings.vm_agent.clone();
    let gc = channel
        .exec(&ctx, &words(["/usr/bin/sudo", "-H", "-u", "admin", &agent, "gc"]), &options)
        .await
        .unwrap();
    assert_eq!(gc.stdout, "collected");
    let df = channel.exec(&ctx, &words(["/bin/df", "-k"]), &options).await.unwrap();
    assert_eq!(df.exit_code, 3);
    assert_eq!(fixture.channel().calls().len(), 2);
    assert_eq!(fixture.channel().calls_containing("/bin/df"), ["/bin/df -k"]);
    let lease = fixture.lease_receipt();
    assert!(lease.is_file());
}
