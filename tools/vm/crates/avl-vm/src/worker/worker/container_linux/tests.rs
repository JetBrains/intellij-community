//! The container-linux lifecycle's suite, over the fake `container.cmd` and the fake control port. Hermetic: no container,
//! no network beyond the loopback, no Bazel. Every guest command of a boot goes through the production control-port
//! channel, and the pool's fake control port answers it from the fixture's guests.

use avl_host_sys::paths::GuestPaths;
use avl_host_sys::share::shares;
use avl_testkit::tartfake::Answer;
use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;
use crate::worker::testing::{
    Fixture, assert_init_refuses_a_golden, ctx, find_step, recycle_with_a_stale_receipt, start_again, start_first,
};
use crate::worker::worker::PoolCommand;

/// The verb of every call of the fake script, in order.
fn verbs(fixture: &Fixture) -> Vec<String> {
    fixture.fake.argvs().into_iter().filter_map(|argv| argv.first().cloned()).collect()
}

/// The `start` calls of the fake script, in order, each whole.
fn start_calls(fixture: &Fixture) -> Vec<Vec<String>> {
    fixture
        .fake
        .argvs()
        .into_iter()
        .filter(|argv| argv.first().is_some_and(|verb| verb == "start"))
        .collect()
}

/// Whether each of the three files the script writes at a start and removes at a stop is under the testing-ui root.
fn control_port_files(fixture: &Fixture) -> [bool; 3] {
    ["container.ctl_port", "container.ctl_bearer", "container.json"].map(|name| fixture.settings.container_linux_root.join(name).is_file())
}

/// The `start` argv the fixture expects: the worker directory, the Bazel share read-only at its guest root, and the
/// published daemon port.
fn expected_start(fixture: &Fixture, worker: &str) -> Vec<String> {
    let settings = &fixture.settings;
    let paths = GuestPaths::of(settings).expect("the guest paths render");
    let mut argv = vec!["start".to_owned(), settings.worker_dir(worker).to_string_lossy().into_owned()];
    for (share, guest) in shares(settings).expect("the shares render").iter().zip([paths.bazel_user_root()]) {
        argv.push("--ro".to_owned());
        argv.push(format!("{}:{guest}", share.path.display()));
    }
    argv.push("--publish".to_owned());
    argv.push(format!("{}:{}", settings.daemon_host_port, settings.daemon.port));
    argv
}

/// The whole start over the fakes: the script's `start` with the mounts and the environment of this backend, then
/// the Linux boot through the control port, **without** `provision-guest`, in the order the Docker boot runs it. A
/// second start asks the script again, because the script is what keeps a running container.
#[tokio::test]
async fn a_container_linux_start_runs_the_script_then_the_linux_boot_through_the_control_port() {
    let fixture = Fixture::container_linux();
    let worker = start_first(&fixture).await;
    assert_eq!(start_calls(&fixture), [expected_start(&fixture, worker)]);
    assert_eq!(
        fixture.fake.container_linux_start_environment(),
        [("APP_EXEC".to_owned(), "/bin/true".to_owned())]
    );
    assert_eq!(control_port_files(&fixture), [true, true, true]);

    let requests = fixture.control_port().requests();
    assert!(
        !requests.is_empty() && requests.iter().all(|request| request.starts_with("POST /v1/execute?")),
        "{requests:?}"
    );
    let argvs = fixture.guest.lines();
    assert!(!argvs.iter().any(|argv| argv.contains(" provision-guest ")), "{argvs:#?}");
    find_step(&argvs, " validate-guest ", 0);

    start_again(&fixture, worker).await;
    assert_eq!(start_calls(&fixture).len(), 2, "{:?}", fixture.fake.calls());
}

/// Leases the first worker of `fixture` and passes its readiness gate once. Answers the worker and the lease.
async fn ready_under_a_lease(fixture: &Fixture) -> (&str, Lease) {
    let worker = fixture.worker(0);
    let lease = fixture.write_lease(worker, "token-1");
    fixture.manager.require_ready(&ctx(), &lease).await.unwrap();
    (worker, lease)
}

/// The readiness gate is lazy, as on Docker: a stopped container is started under the caller's own lease, and a
/// running one whose guest answers is left as it is.
#[tokio::test]
async fn the_container_linux_readiness_gate_starts_a_stopped_worker_and_keeps_a_running_one() {
    let fixture = Fixture::container_linux();
    let (worker, lease) = ready_under_a_lease(&fixture).await;
    assert_eq!(start_calls(&fixture), [expected_start(&fixture, worker)]);

    fixture.manager.require_ready(&ctx(), &lease).await.unwrap();
    assert_eq!(start_calls(&fixture).len(), 1, "{:?}", fixture.fake.calls());
    assert!(fixture.fake.saw_call_containing("list"));
}

#[tokio::test]
async fn a_container_linux_stop_stops_a_running_container_once() {
    let fixture = Fixture::container_linux();
    let worker = fixture.worker(0);
    assert_eq!(fixture.manager.stop(&ctx(), worker).await.unwrap(), StopState::AlreadyStopped);
    assert!(!verbs(&fixture).iter().any(|verb| verb == "stop"), "{:?}", fixture.fake.calls());

    fixture.manager.start_without_lifecycle_lock(&ctx(), worker).await.unwrap();
    assert_eq!(fixture.manager.stop(&ctx(), worker).await.unwrap(), StopState::Stopped);
    assert_eq!(verbs(&fixture).iter().filter(|verb| *verb == "stop").count(), 1);
    assert_eq!(control_port_files(&fixture), [false, false, false]);

    assert_eq!(fixture.manager.stop(&ctx(), worker).await.unwrap(), StopState::AlreadyStopped);
    assert_eq!(verbs(&fixture).iter().filter(|verb| *verb == "stop").count(), 1);
}

/// A `start` the script refuses is `container_linux_start_failed`, and the refusal names the log that kept what the
/// script printed, because a failed image build is read there. Nothing reaches the guest.
#[tokio::test]
async fn a_start_whose_script_fails_names_the_start_log() {
    let fixture = Fixture::container_linux();
    let worker = fixture.worker(0);
    fixture.fake.answer(Answer::ContainerLinuxFailedVerb, "start");
    let refusal = fixture.manager.start_without_lifecycle_lock(&ctx(), worker).await.unwrap_err();
    assert_eq!(refusal.code, "container_linux_start_failed", "{refusal:?}");
    let log = fixture.settings.worker_dir(worker).join("container-linux-start.log");
    assert!(refusal.message.contains(&log.display().to_string()), "{}", refusal.message);
    let content = std::fs::read_to_string(&log).expect("the start log is kept");
    assert!(content.contains("fake container.cmd: start failed"), "{content:?}");
    assert_eq!(control_port_files(&fixture), [false, false, false]);
    assert!(fixture.guest.lines().is_empty(), "{:#?}", fixture.guest.lines());
}

/// A lease release of a stopped container is refused the start a release gate would need, as on Docker.
#[tokio::test]
async fn a_stopped_container_linux_worker_is_not_release_ready() {
    let fixture = Fixture::container_linux();
    let refusal = fixture.manager.require_release_ready(&ctx(), fixture.worker(0)).await.unwrap_err();
    assert_eq!(refusal.code, "worker_stopped");
}

/// `pool init` starts the one slot, because the container is all the pool holds, and takes no golden VM.
#[tokio::test]
async fn pool_init_on_container_linux_starts_the_one_slot_and_takes_no_golden() {
    let fixture = Fixture::container_linux();
    assert_init_refuses_a_golden(&fixture).await;
    assert_eq!(start_calls(&fixture), Vec::<Vec<String>>::new());

    let outcome = fixture.run_pool(PoolCommand::Init { golden: None }).await.unwrap();
    assert_eq!(outcome.data["started"], json!(["container-linux-1"]));
    assert_eq!(start_calls(&fixture).len(), 1);
    let outcome = fixture.run_pool(PoolCommand::Init { golden: None }).await.unwrap();
    assert_eq!(outcome.data["started"], json!([]));
}

/// `pool gc` stops the unleased container, and keeps a stopped slot and a leased slot without touching them.
#[tokio::test]
async fn pool_gc_on_container_linux_stops_an_unleased_container_and_keeps_a_leased_one() {
    let fixture = Fixture::container_linux();
    let worker = fixture.worker(0).to_owned();
    let outcome = fixture.run_pool(PoolCommand::Gc).await.unwrap();
    assert_eq!(outcome.data["kept"], json!({ worker.as_str(): "stopped" }));
    assert!(!verbs(&fixture).iter().any(|verb| verb == "stop"), "{:?}", fixture.fake.calls());

    fixture.manager.start(&ctx(), &worker).await.unwrap();
    fixture.fake.forget_calls();
    let outcome = fixture.run_pool(PoolCommand::Gc).await.unwrap();
    assert_eq!(outcome.data["removed"], json!([worker.as_str()]));
    assert_eq!(verbs(&fixture).iter().filter(|verb| *verb == "stop").count(), 1);
    assert_eq!(control_port_files(&fixture), [false, false, false]);

    fixture.manager.start(&ctx(), &worker).await.unwrap();
    fixture.write_lease(&worker, "token");
    fixture.fake.forget_calls();
    let outcome = fixture.run_pool(PoolCommand::Gc).await.unwrap();
    assert_eq!(outcome.data["kept"], json!({ worker.as_str(): "leased" }));
    assert!(!verbs(&fixture).iter().any(|verb| verb == "stop"), "{:?}", fixture.fake.calls());
    assert_eq!(control_port_files(&fixture), [true, true, true]);
}

/// `pool recycle` stops the container, empties the host directory of the slot, and starts the worker again.
#[tokio::test]
async fn pool_recycle_on_container_linux_stops_the_container_clears_the_slot_and_starts_again() {
    let fixture = Fixture::container_linux();
    let (worker, receipt, _) = recycle_with_a_stale_receipt(&fixture).await;
    let verbs = verbs(&fixture);
    let position = |verb: &str| {
        verbs
            .iter()
            .position(|called| called == verb)
            .unwrap_or_else(|| panic!("no {verb:?} call in {verbs:?}"))
    };
    assert!(position("stop") < position("start"), "{verbs:?}");
    assert!(!receipt.exists(), "a receipt of the recycled slot survived");
    assert!(fixture.manager.lifecycle_lock_path(&worker).exists());
    assert_eq!(control_port_files(&fixture), [true, true, true]);
}

/// A container the script makes has a fresh disk, so a start of a stopped worker forgets what the host recorded
/// about the previous guest: the receipts go, the lease and the pulled reports stay,
/// and the start is recorded.
#[tokio::test]
async fn a_start_of_a_stopped_worker_forgets_the_previous_guest_and_records_the_start() {
    let fixture = Fixture::container_linux();
    let worker = fixture.worker(0);
    let directory = fixture.settings.worker_dir(worker);
    let kept = directory.join("reports/run-1/test.xml");
    std::fs::create_dir_all(kept.parent().expect("a parent")).expect("the directory");
    std::fs::write(&kept, "kept").expect("the kept file");
    // The boot writes the agent receipt again, so the daemon record stands for what the previous guest left.
    std::fs::write(directory.join("daemon.json"), "{}").expect("a record of the previous guest's daemon");
    let lease = fixture.write_lease(worker, "token-1");

    fixture.manager.require_ready(&ctx(), &lease).await.unwrap();
    assert!(!directory.join("daemon.json").exists(), "the previous guest's daemon record stays");
    assert!(kept.exists(), "the pulled report is gone");
    assert!(fixture.settings.lease_path(worker).exists(), "the lease is gone");
    let record: Vec<String> =
        serde_json::from_slice(&std::fs::read(directory.join("container-linux-start.json")).expect("the start record"))
            .expect("a JSON list");
    let expected = expected_start(&fixture, worker);
    assert!(record.ends_with(&expected), "{record:?} does not end with {expected:?}");
}

/// A running container whose start record names another argv is another container: the gate stops it and starts
/// again, so other mounts or another published port take effect.
#[tokio::test]
async fn the_readiness_gate_makes_a_running_container_again_when_its_start_record_is_stale() {
    let fixture = Fixture::container_linux();
    let (worker, lease) = ready_under_a_lease(&fixture).await;
    assert_eq!(start_calls(&fixture).len(), 1);
    assert!(!verbs(&fixture).iter().any(|verb| verb == "stop"), "{:?}", fixture.fake.calls());

    std::fs::write(
        fixture.settings.worker_dir(worker).join("container-linux-start.json"),
        r#"["start", "/elsewhere"]"#,
    )
    .expect("a record of another start");
    fixture.manager.require_ready(&ctx(), &lease).await.unwrap();
    let verbs = verbs(&fixture);
    let stop = verbs
        .iter()
        .position(|verb| verb == "stop")
        .expect("the stale container was stopped");
    assert_eq!(verbs.iter().filter(|verb| *verb == "start").count(), 2, "{verbs:?}");
    assert!(
        verbs.iter().skip(stop).any(|verb| verb == "start"),
        "no start after the stop: {verbs:?}"
    );
}
