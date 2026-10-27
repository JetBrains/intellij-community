//! The container-linux row's suite, over the fake `container.cmd` and the fake control port.

use avl_base::Outcome;
use avl_host_sys::Ctx;
use avl_host_sys::guest::write_init_receipt;
use avl_host_testkit::outcome_of;
use avl_testkit::tartfake::CONTAINER_LINUX_NOVNC_URL;
use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use crate::lane::observe::status::command_status;
use crate::lane::observe::testing::Fixture;

const WORKER: &str = "container-linux-1";

async fn status(fixture: &Fixture) -> Outcome {
    outcome_of(command_status(&Ctx::background(), &fixture.manager).await)
}

fn row(outcome: &Outcome) -> &Value {
    match outcome.data["workers"].as_array().expect("the report lists workers").as_slice() {
        [row] => row,
        rows => panic!("the container-linux pool reported {} workers", rows.len()),
    }
}

/// `status` is a report: the fake script sees `list` and never `start` or `stop`.
fn assert_status_wrote_nothing(fixture: &Fixture) {
    let verbs: Vec<String> = fixture.fake.argvs().into_iter().filter_map(|argv| argv.first().cloned()).collect();
    assert!(
        verbs.iter().any(|verb| verb == "list"),
        "status asked the script nothing: {verbs:?}"
    );
    assert!(verbs.iter().all(|verb| verb == "list"), "status sent a mutating command: {verbs:?}");
}

/// A stopped container reports every key, asks the guest nothing, and names no noVNC page. A lease is reported
/// without its holder.
#[tokio::test]
async fn a_stopped_container_linux_worker_reports_every_key() {
    let fixture = Fixture::container_linux();
    let outcome = status(&fixture).await;
    let host_repo = fixture.root().to_string_lossy().into_owned();
    assert_eq!(
        outcome.data,
        json!({
            "backend": "container-linux",
            "workers": [{
                "abandonedIdeRuns": null,
                "worker": WORKER,
                "state": "stopped",
                "guestAgent": false,
                "workerStorageReady": false,
                "parityReady": null,
                "parityError": null,
                "novnc": null,
                "lease": null,
            }],
            "hostRepo": host_repo,
            "hostBazelUserRoot": host_repo,
            "hostPathsError": null,
        })
    );
    assert_eq!(outcome.text, "container-linux-1: stopped lease=free parity=n/a novnc=none");
    assert_eq!(fixture.channel(WORKER).lines(), Vec::<String>::new());
    assert_eq!(fixture.control_port().requests(), Vec::<String>::new());

    fixture.lease_receipt(WORKER);
    let outcome = status(&fixture).await;
    assert_eq!(row(&outcome)["lease"]["state"], json!("leased"), "{outcome:?}");
    assert_eq!(row(&outcome)["lease"].get("holder"), None, "{outcome:?}");
    assert_eq!(outcome.text, "container-linux-1: stopped lease=leased parity=n/a novnc=none");
    assert_status_wrote_nothing(&fixture);
}

/// A running container is probed through its control port: the guest agent, the storage and the parity. The row
/// carries the noVNC page the guest wrote.
#[tokio::test]
async fn a_running_container_linux_worker_is_probed_through_the_control_port() {
    let fixture = Fixture::container_linux();
    fixture.start_container_linux_container();
    write_init_receipt(&fixture.settings, WORKER).expect("the init receipt is written");
    let outcome = status(&fixture).await;
    let row = row(&outcome);
    assert_eq!(
        (
            &row["state"],
            &row["guestAgent"],
            &row["workerStorageReady"],
            &row["parityReady"],
            &row["parityError"],
            &row["novnc"],
        ),
        (
            &json!("running"),
            &json!(true),
            &json!(true),
            &json!(true),
            &Value::Null,
            &json!(CONTAINER_LINUX_NOVNC_URL),
        ),
        "{row}"
    );
    assert_eq!(
        outcome.text,
        format!("container-linux-1: running lease=free parity=ready novnc={CONTAINER_LINUX_NOVNC_URL}")
    );
    let requests = fixture.control_port().requests();
    assert!(
        !requests.is_empty() && requests.iter().all(|request| request.starts_with("POST /v1/execute?")),
        "{requests:?}"
    );
    let channel = fixture.channel(WORKER);
    assert!(channel.saw_call_containing("/usr/bin/true"), "{:?}", channel.lines());
    assert!(channel.saw_call_containing("/bin/test -d"), "{:?}", channel.lines());
    assert_status_wrote_nothing(&fixture);
}
