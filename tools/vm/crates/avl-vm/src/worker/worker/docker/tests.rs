//! The Docker lifecycle's suite, over the fake `docker`. Hermetic: no engine, no network, no Bazel. It runs on every
//! host, because a Windows host drives the Docker backend too.

use avl_host_testkit::answer_guest;
use avl_testkit::tartfake::Answer;
use pretty_assertions::assert_eq;

use super::*;
use crate::worker::testing::{Fixture, find_step};
#[cfg(unix)]
use crate::worker::worker::{IdleStopRecord, IdleStopRequest, IdleStopVerdict, read_idle_stop_record, remove_idle_stop_record};
use crate::worker::worker::{PoolCommand, PoolTarget};

fn ctx() -> Ctx {
    Ctx::background()
}

fn docker(fixture: &Fixture) -> &Docker {
    fixture.manager.machine().docker().expect("a Docker pool")
}

/// The whole Docker start over the fake: the image is built, the container is created and recorded, started, and
/// the guest runs the Linux boot: the agent install, `validate-guest`, the parity layout and the readiness. The image
/// carries the packages and the Node, so no retired verb runs. The parity step remounts nothing: the shares are bind
/// mounts.
#[tokio::test]
async fn a_docker_start_builds_creates_starts_and_validates_without_provisioning() {
    let fixture = Fixture::docker();
    let worker = fixture.worker(0);
    assert_eq!(
        fixture.manager.start_without_lifecycle_lock(&ctx(), worker).await.unwrap(),
        StartState::Started
    );
    let calls = fixture.fake.calls();
    let position = |prefix: &str| {
        calls
            .iter()
            .position(|call| call.starts_with(prefix))
            .unwrap_or_else(|| panic!("no {prefix:?} call in {calls:#?}"))
    };
    assert!(position("version ") < position("pull "));
    assert!(position("pull ") < position("build "));
    assert!(position("build ") < position("create "));
    assert!(position("create ") < position(&format!("start {worker}")));
    assert!(docker(&fixture).create_record_is_current(worker).unwrap());

    let argvs = fixture.guest.lines();
    for retired in [" provision-guest ", " stage-node ", " check-node ", "remount-shares.sh"] {
        assert!(!argvs.iter().any(|argv| argv.contains(retired)), "{retired}: {argvs:#?}");
    }
    let mut at = 0;
    for step in ["/bin/mv -f ", " validate-guest ", "/bin/sh "] {
        at = find_step(&argvs, step, at);
    }

    // A second start over the running container creates nothing and starts nothing.
    let before = fixture.fake.calls().len();
    assert_eq!(
        fixture.manager.start_without_lifecycle_lock(&ctx(), worker).await.unwrap(),
        StartState::AlreadyRunning
    );
    let after = &fixture.fake.calls()[before..];
    assert!(
        !after.iter().any(|call| call.starts_with("create ")
            || call.starts_with("start ")
            || call.starts_with("pull ")
            || call.starts_with("build ")),
        "{after:#?}"
    );
}

/// Writes a create record of other arguments over the worker's own: the record a second checkout finds.
fn declare_stale_container(fixture: &Fixture, worker: &str) {
    std::fs::write(
        fixture.settings.docker_create_record_path(worker),
        format!(r#"{{"schemaVersion":1,"worker":"{worker}","argv":["docker","create","old"],"containerId":"fake-container-1"}}"#),
    )
    .unwrap();
}

/// An unleased running container whose record declares other shares or another image is stopped, removed and made
/// again by `pool start`: the Docker twin of restarting a `tart run` for a second checkout. The removal has no
/// `--force`, so the engine is the last check that the container is down.
#[tokio::test]
async fn a_pool_start_recreates_an_unleased_container_of_another_declaration() {
    let fixture = Fixture::docker();
    let worker = fixture.worker(0);
    fixture.manager.start(&ctx(), worker).await.unwrap();
    declare_stale_container(&fixture, worker);
    let before = fixture.fake.calls().len();
    // The container ran, so the start is `started` again only after it was made again.
    assert_eq!(fixture.manager.start(&ctx(), worker).await.unwrap(), StartState::Started);
    let after: Vec<String> = fixture.fake.calls()[before..].to_vec();
    let position = |prefix: &str| {
        after
            .iter()
            .position(|call| call.starts_with(prefix))
            .unwrap_or_else(|| panic!("no {prefix:?} call in {after:#?}"))
    };
    assert!(position("stop ") < position(&format!("rm {worker}")));
    assert!(position(&format!("rm {worker}")) < position("create "));
    assert!(
        !after
            .iter()
            .any(|call| call.starts_with("volume ") || call.starts_with("rm --force")),
        "{after:#?}"
    );
    assert!(docker(&fixture).container_is_current(&ctx(), worker).await.unwrap());
}

/// `pool start` of a second checkout must not take a running container away from the session that leased it. It is
/// refused `worker_leased`, and the container is neither stopped nor removed. Only the lease holder's own readiness
/// gate may make it again.
#[tokio::test]
async fn a_pool_start_refuses_to_recreate_a_leased_running_container() {
    let fixture = Fixture::docker();
    let worker = fixture.worker(0);
    fixture.manager.start(&ctx(), worker).await.unwrap();
    fixture.write_lease(worker, "another-session");
    declare_stale_container(&fixture, worker);
    let before = fixture.fake.calls().len();
    let refusal = fixture.manager.start(&ctx(), worker).await.unwrap_err();
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("worker_leased", Exit::TEMP_FAIL),
        "{refusal:?}"
    );
    let after: Vec<String> = fixture.fake.calls()[before..].to_vec();
    assert!(
        !after
            .iter()
            .any(|call| call.starts_with("stop ") || call.starts_with("rm ") || call.starts_with("create ")),
        "{after:#?}"
    );
    assert_eq!(docker(&fixture).state(&ctx(), worker).await.unwrap(), ContainerState::Running);
}

/// `pool start all` starts the slots which it can and names the slot that it refused. The first slot runs a stale
/// container under the lease of another holder, so it is refused `worker_leased`. The start goes on with the next
/// slot.
///
/// The fake holds one container for every name, so the second slot finds the container of the first slot running.
/// The second slot has no create record, so its start makes the container again under its own name.
#[tokio::test]
async fn a_pool_start_of_all_starts_the_other_slots_and_names_the_refused_one() {
    let fixture = Fixture::docker_builder().env("AIR_VM_WORKERS", "air-docker-1,air-docker-2").build();
    let [leased, free] = [0, 1].map(|index| fixture.worker(index).to_owned());
    fixture.manager.start(&ctx(), &leased).await.unwrap();
    fixture.write_lease(&leased, "another-session");
    declare_stale_container(&fixture, &leased);
    let before = fixture.fake.calls().len();

    let refusal = fixture
        .manager
        .pool(&ctx(), PoolCommand::Start(PoolTarget::All), None)
        .await
        .unwrap_err();
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("start_incomplete", Exit::TEMP_FAIL),
        "{refusal:?}"
    );
    assert_eq!(refusal.message, format!("started {free}; not started: {leased} (worker_leased)"));
    let details = refusal.details().expect("the per-worker outcome");
    assert_eq!(
        (&details["backend"], &details["action"], &details["target"]),
        (&serde_json::json!("docker"), &serde_json::json!("start"), &serde_json::json!("all"))
    );
    assert_eq!(details["workers"], serde_json::json!({ free.as_str(): "started" }));
    let refused = details["refused"].as_object().unwrap();
    assert_eq!(refused.len(), 1, "{details}");
    assert_eq!(refused[&leased]["code"], "worker_leased");
    let after: Vec<String> = fixture.fake.calls()[before..].to_vec();
    assert!(after.contains(&format!("start {free}")), "{after:#?}");
    assert!(
        !after
            .iter()
            .any(|call| call.ends_with(leased.as_str())
                && (call.starts_with("stop ") || call.starts_with("rm ") || call.starts_with("start "))),
        "{after:#?}"
    );
    assert!(fixture.settings.lease_path(&leased).exists());
}

/// A container that does not run is made again for any caller, leased or not: nothing runs in it, and its volume
/// stays.
#[tokio::test]
async fn a_pool_start_recreates_a_stopped_stale_container_even_when_leased() {
    let fixture = Fixture::docker();
    let worker = fixture.worker(0);
    fixture.manager.start(&ctx(), worker).await.unwrap();
    fixture.manager.stop(&ctx(), worker).await.unwrap();
    fixture.write_lease(worker, "another-session");
    declare_stale_container(&fixture, worker);
    let before = fixture.fake.calls().len();
    assert_eq!(fixture.manager.start(&ctx(), worker).await.unwrap(), StartState::Started);
    let after: Vec<String> = fixture.fake.calls()[before..].to_vec();
    assert!(after.iter().any(|call| call == &format!("rm {worker}")), "{after:#?}");
    assert!(!after.iter().any(|call| call.starts_with("stop ")), "{after:#?}");
}

/// A container of the right record but another id was not made by this controller's create, and is made again.
#[tokio::test]
async fn a_pool_start_recreates_a_container_of_another_id() {
    let fixture = Fixture::docker();
    let worker = fixture.worker(0);
    fixture.manager.start(&ctx(), worker).await.unwrap();
    fixture.fake.answer(Answer::ContainerId, "hand-made\n");
    assert_eq!(fixture.manager.start(&ctx(), worker).await.unwrap(), StartState::Started);
    assert!(docker(&fixture).container_is_current(&ctx(), worker).await.unwrap());
    assert_eq!(
        docker(&fixture).read_create_record(worker).unwrap().container_id,
        "fake-container-2"
    );
}

/// A paused container reports `.State.Running=true` and answers no `docker exec`. The start and the stop refuse it
/// at once with its state word, instead of a boot timeout or a false `AlreadyStopped`.
#[tokio::test]
async fn a_paused_container_is_refused_at_once_by_start_and_stop() {
    let fixture = Fixture::docker();
    let worker = fixture.worker(0);
    fixture.manager.start(&ctx(), worker).await.unwrap();
    fixture.fake.answer(Answer::ContainerState, "paused/0\n");
    let before = fixture.fake.calls().len();
    let refusal = fixture.manager.start(&ctx(), worker).await.unwrap_err();
    assert_eq!(refusal.code, "container_unusable", "{refusal:?}");
    assert!(refusal.message.contains("is paused"), "{}", refusal.message);
    let refusal = fixture.manager.stop(&ctx(), worker).await.unwrap_err();
    assert_eq!(refusal.code, "container_unusable", "{refusal:?}");
    let after: Vec<String> = fixture.fake.calls()[before..].to_vec();
    assert!(
        !after
            .iter()
            .any(|call| call.starts_with("start ") || call.starts_with("stop ") || call.starts_with("rm ")),
        "{after:#?}"
    );
}

/// A container that a start leaves paused while the boot polls is refused at once, not after the boot budget.
#[tokio::test]
async fn a_docker_boot_that_finds_a_paused_container_says_so() {
    let fixture = Fixture::docker();
    let worker = fixture.worker(0);
    fixture
        .guest
        .answer(answer_guest(vec![("/usr/bin/true", avl_host_testkit::failed(1, "paused"))]));
    fixture.fake.answer(Answer::StartedState, "paused/0\n");
    let refusal = fixture.manager.start_without_lifecycle_lock(&ctx(), worker).await.unwrap_err();
    assert_eq!(refusal.code, "container_unusable", "{refusal:?}");
}

/// Two starts share one image pull or build: the second waits for the image lock, and a start that cannot get it
/// in time is refused `docker_image_busy` and neither pulls nor builds.
#[tokio::test]
async fn the_image_build_holds_the_pool_wide_image_lock() {
    let fixture = Fixture::docker();
    fixture.manager.prepare_runtime_dirs().unwrap();
    let held = fixture
        .manager
        .locks()
        .acquire(&ctx(), &fixture.settings.docker_image_lock_path(), "another-start", "busy")
        .await
        .unwrap();
    let refusal = fixture.manager.ensure_docker_image(&ctx(), docker(&fixture)).await.unwrap_err();
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("docker_image_busy", Exit::TEMP_FAIL),
        "{refusal:?}"
    );
    assert!(
        !fixture
            .fake
            .calls()
            .iter()
            .any(|call| call.starts_with("build ") || call.starts_with("pull "))
    );
    assert!(
        refusal
            .message
            .contains(&fixture.settings.docker_pull_log_path().display().to_string()),
        "{}",
        refusal.message
    );
    drop(held);
    let tag = fixture.manager.ensure_docker_image(&ctx(), docker(&fixture)).await.unwrap();
    assert_eq!(tag, docker(&fixture).image_tag());
}

/// An entrypoint that gave up is refused at once, with what it printed, rather than after the whole boot budget.
#[tokio::test]
async fn a_docker_start_whose_container_exited_says_so() {
    let fixture = Fixture::docker();
    let worker = fixture.worker(0);
    fixture
        .guest
        .answer(answer_guest(vec![("/usr/bin/true", avl_host_testkit::failed(1, "not running"))]));
    // `start` exits 0, and the container it started has already exited, as it does when `air-display` gives up.
    fixture.fake.answer(Answer::StartedState, "exited/1\n");
    let refusal = fixture.manager.start_without_lifecycle_lock(&ctx(), worker).await.unwrap_err();
    assert_eq!(refusal.code, "container_exited", "{refusal:?}");
    assert!(refusal.message.contains("exited with 1"), "{}", refusal.message);
}

#[tokio::test]
async fn a_docker_stop_stops_a_running_container_once() {
    let fixture = Fixture::docker();
    let worker = fixture.worker(0);
    assert_eq!(fixture.manager.stop(&ctx(), worker).await.unwrap(), StopState::AlreadyStopped);
    fixture.manager.start_without_lifecycle_lock(&ctx(), worker).await.unwrap();
    assert_eq!(fixture.manager.stop(&ctx(), worker).await.unwrap(), StopState::Stopped);
    assert!(fixture.fake.saw_call_containing(&format!("stop --time 10 {worker}")));
    assert_eq!(fixture.manager.stop(&ctx(), worker).await.unwrap(), StopState::AlreadyStopped);
}

/// `pool stop` answers the state of each worker that it stopped, and nothing about the engine: the engine of the host
/// environment is the operator's, and no command of this controller stops it.
#[tokio::test]
async fn a_pool_stop_names_its_workers_and_no_engine() {
    let fixture = Fixture::docker();
    let worker = fixture.worker(0).to_owned();
    fixture.manager.start_without_lifecycle_lock(&ctx(), &worker).await.unwrap();
    let outcome = fixture
        .manager
        .pool(&ctx(), PoolCommand::Stop(PoolTarget::All), None)
        .await
        .unwrap();
    assert_eq!(outcome.data["workers"][&worker], "stopped");
    assert!(outcome.data.get("engine").is_none(), "{}", outcome.data);
    assert_eq!(outcome.text, format!("{worker}=stopped"));
}

/// A lease release of a stopped container is refused the start a release gate would need, as on Tart.
#[tokio::test]
async fn a_stopped_docker_worker_is_not_release_ready() {
    let fixture = Fixture::docker();
    let refusal = fixture.manager.require_release_ready(&ctx(), fixture.worker(0)).await.unwrap_err();
    assert_eq!(refusal.code, "worker_stopped");
}

/// The share refresh settles on Docker: the bind mounts serve fresh bytes, so a Docker refresh sends the guest
/// nothing. The Tart twin, which remounts, is in the worker suite.
#[tokio::test]
async fn the_share_refresh_settles_on_docker() {
    let fixture = Fixture::docker();
    fixture.manager.refresh_shares(&ctx(), fixture.worker(0)).await.unwrap();
    assert!(fixture.guest.lines().is_empty(), "{:#?}", fixture.guest.lines());
}

#[tokio::test]
async fn pool_init_on_docker_builds_the_image_and_creates_every_slot() {
    let fixture = Fixture::docker();
    let refusal = fixture
        .manager
        .pool(
            &ctx(),
            PoolCommand::Init {
                golden: Some("air-macos-golden".to_owned()),
            },
            None,
        )
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "usage");

    let outcome = fixture
        .manager
        .pool(&ctx(), PoolCommand::Init { golden: None }, None)
        .await
        .unwrap();
    assert_eq!(outcome.data["created"], serde_json::json!(["air-docker-1"]));
    assert_eq!(outcome.data["image"], docker(&fixture).image_tag());
    // Nothing is started: the first lease operation starts a slot.
    assert!(
        !fixture.fake.calls().iter().any(|call| call.starts_with("start ")),
        "{:#?}",
        fixture.fake.calls()
    );
    // A second init finds every container current.
    let outcome = fixture
        .manager
        .pool(&ctx(), PoolCommand::Init { golden: None }, None)
        .await
        .unwrap();
    assert_eq!(outcome.data["created"], serde_json::json!([]));
}

/// `pool gc` removes a stopped container and keeps its volume, so the next start is warm; a leased slot is kept
/// without a look at its container.
///
/// The fake holds one container state, which every slot of this pool shares, so the pool has one slot per case.
#[tokio::test]
async fn pool_gc_on_docker_removes_stopped_containers_and_keeps_volumes() {
    let fixture = Fixture::docker();
    for (state, kept) in [
        ("exited/0\n", None),
        ("created/0\n", None),
        ("dead/0\n", None),
        ("running/0\n", Some("running")),
        ("paused/0\n", Some("paused")),
        ("restarting/0\n", Some("restarting")),
    ] {
        fixture.fake.forget_calls();
        fixture.fake.answer(Answer::ContainerState, state);
        let outcome = fixture.manager.pool(&ctx(), PoolCommand::Gc, None).await.unwrap();
        match kept {
            None => {
                assert_eq!(outcome.data["removed"], serde_json::json!(["air-docker-1"]), "{state}");
                assert!(fixture.fake.saw_call_containing("rm air-docker-1"), "{state}");
            }
            Some(word) => {
                assert_eq!(outcome.data["removed"], serde_json::json!([]), "{state}");
                assert_eq!(outcome.data["kept"], serde_json::json!({ "air-docker-1": word }), "{state}");
                assert!(!fixture.fake.saw_call_containing("rm "), "{state}");
            }
        }
        assert!(!fixture.fake.saw_call_containing("rm --force"), "{state}");
        assert!(!fixture.fake.saw_call_containing("volume rm"), "{state}");
    }

    fixture.fake.forget_calls();
    fixture.fake.answer(Answer::ContainerState, "exited/0\n");
    fixture.write_lease("air-docker-1", "token");
    let outcome = fixture.manager.pool(&ctx(), PoolCommand::Gc, None).await.unwrap();
    assert_eq!(outcome.data["kept"], serde_json::json!({ "air-docker-1": "leased" }));
    assert!(!fixture.fake.saw_call_containing("rm "));
}

/// gc reads the state under the slot's lifecycle lock. A `pool start` that holds the lock makes gc keep that slot as
/// `busy`, and a container that runs once gc has the lock is kept. It is never killed.
#[tokio::test]
async fn pool_gc_on_docker_reads_the_state_under_the_lifecycle_lock() {
    let fixture = Fixture::docker();
    let worker = fixture.worker(0).to_owned();
    fixture.manager.start(&ctx(), &worker).await.unwrap();
    let before = fixture.fake.calls().len();
    let outcome = fixture.manager.pool(&ctx(), PoolCommand::Gc, None).await.unwrap();
    assert_eq!(outcome.data["kept"], serde_json::json!({ worker.as_str(): "running" }));
    let after: Vec<String> = fixture.fake.calls()[before..].to_vec();
    assert!(
        after
            .iter()
            .any(|call| call.starts_with("inspect --type container --format {{.State.Status}}")),
        "{after:#?}"
    );
    assert!(!after.iter().any(|call| call.starts_with("rm ")), "{after:#?}");
    assert!(
        !after.iter().any(|call| call.starts_with("container ls")),
        "gc must not decide from a listing made before the lock: {after:#?}"
    );

    let _held = fixture
        .manager
        .locks()
        .acquire(&ctx(), &fixture.manager.lifecycle_lock_path(&worker), "pool-start", "busy")
        .await
        .unwrap();
    let outcome = fixture.manager.pool(&ctx(), PoolCommand::Gc, None).await.unwrap();
    assert_eq!(outcome.data["kept"], serde_json::json!({ worker.as_str(): "busy" }));
}

/// One busy slot does not stop the gc of the pool. A lane run holds the lifecycle lock of its worker for the whole
/// run, so the gc keeps that slot as `busy` and removes the stopped container of the other slot.
#[tokio::test]
async fn pool_gc_on_docker_keeps_a_busy_slot_and_goes_on() {
    let fixture = Fixture::docker_builder().env("AIR_VM_WORKERS", "air-docker-1,air-docker-2").build();
    fixture.manager.prepare_runtime_dirs().unwrap();
    let _run = fixture
        .manager
        .locks()
        .acquire(&ctx(), &fixture.manager.lifecycle_lock_path("air-docker-1"), "run", "busy")
        .await
        .unwrap();
    fixture.fake.answer(Answer::ContainerState, "exited/0\n");
    let outcome = fixture.manager.pool(&ctx(), PoolCommand::Gc, None).await.unwrap();
    assert_eq!(outcome.data["removed"], serde_json::json!(["air-docker-2"]));
    assert_eq!(outcome.data["kept"], serde_json::json!({ "air-docker-1": "busy" }));
    assert!(!fixture.fake.saw_call_containing("rm air-docker-1"));
}

/// `pool recycle` is the one command that drops a Docker worker's volume, and it starts the worker again.
#[tokio::test]
async fn pool_recycle_on_docker_drops_the_volume_and_starts_again() {
    let fixture = Fixture::docker();
    let worker = fixture.worker(0).to_owned();
    fixture.manager.start_without_lifecycle_lock(&ctx(), &worker).await.unwrap();
    let receipt = fixture.settings.worker_dir(&worker).join("stale-receipt.json");
    std::fs::write(&receipt, "{}").unwrap();
    let before = fixture.fake.calls().len();
    fixture
        .manager
        .pool(&ctx(), PoolCommand::Recycle(PoolTarget::Worker(worker.clone())), None)
        .await
        .unwrap();
    let after: Vec<String> = fixture.fake.calls()[before..].to_vec();
    let position = |prefix: &str| {
        after
            .iter()
            .position(|call| call.starts_with(prefix))
            .unwrap_or_else(|| panic!("no {prefix:?} call in {after:#?}"))
    };
    // No stop: a recycle removes the container in any state, with `--force`, once the lease guard passed.
    assert!(!after.iter().any(|call| call.starts_with("stop ")), "{after:#?}");
    assert!(position("rm --force ") < position(&format!("volume rm --force air-{worker}-data")));
    assert!(position("volume rm ") < position("create "));
    assert!(position("create ") < position(&format!("start {worker}")));
    assert!(!receipt.exists(), "a receipt of the dropped volume survived");
    assert!(fixture.manager.lifecycle_lock_path(&worker).exists());
}

/// The holder of a lease recycles its own worker under its receipt, also when the guest refuses the gc of its lane
/// IDEs: the recycle discards that guest. The recycle passes the lease guard and gives the lease back, so neither the
/// lease nor its receipt survives.
#[cfg(unix)]
#[tokio::test]
async fn a_holder_recycles_its_leased_worker_under_its_receipt_and_gives_the_lease_back() {
    let fixture = Fixture::docker();
    let worker = fixture.worker(0).to_owned();
    fixture.manager.prepare_runtime_dirs().unwrap();
    fixture.manager.start_without_lifecycle_lock(&ctx(), &worker).await.unwrap();
    let lease = fixture.write_lease(&worker, "holder-token");
    let receipt = crate::worker::lease::write_lease_receipt(&fixture.settings, &lease).unwrap();
    fixture.guest.channel(&worker).answer(answer_guest(vec![(
        "ide-gc",
        avl_host_testkit::failed(1, "the process group 4242 did not exit"),
    )]));
    let notes = crate::worker::testing::Notes::start(fixture.manager.reporter());

    let outcome = fixture
        .manager
        .pool(&ctx(), PoolCommand::Recycle(PoolTarget::Worker(worker.clone())), Some(&receipt))
        .await
        .unwrap();

    assert_eq!(outcome.data["recycled"], serde_json::json!([worker]));
    assert_eq!(outcome.data["leaseReleased"], serde_json::json!(worker));
    assert!(fixture.fake.saw_call_containing(&format!("volume rm --force air-{worker}-data")));
    assert_eq!(read_lease(&fixture.settings.lease_path(&worker)).unwrap(), None);
    assert!(!receipt.exists(), "the receipt of the given-back lease survived");
    let notes = notes.all();
    assert!(
        notes.iter().any(|note| note.contains("were not stopped before the recycle")),
        "{notes:#?}"
    );
    assert!(notes.iter().any(|note| note.contains("gave back the lease")), "{notes:#?}");
}

/// A receipt authorizes the recycle of its own worker only. A recycle of another worker or of `all` under it is refused
/// before anything is deleted, and the lease stays.
#[tokio::test]
async fn a_receipt_does_not_authorize_the_recycle_of_another_target() {
    let fixture = Fixture::docker();
    let worker = fixture.worker(0).to_owned();
    fixture.manager.prepare_runtime_dirs().unwrap();
    let lease = fixture.write_lease(&worker, "holder-token");
    let receipt = crate::worker::lease::write_lease_receipt(&fixture.settings, &lease).unwrap();
    for target in [PoolTarget::Worker("air-docker-2".to_owned()), PoolTarget::All] {
        let refusal = fixture
            .manager
            .pool(&ctx(), PoolCommand::Recycle(target), Some(&receipt))
            .await
            .unwrap_err();
        assert_eq!(refusal.code, "usage", "{refusal:?}");
        assert!(refusal.message.contains(&format!("pool recycle {worker}")), "{refusal}");
    }
    assert!(!fixture.fake.saw_call_containing("rm "));
    assert_eq!(read_lease(&fixture.settings.lease_path(&worker)).unwrap(), Some(lease));
    assert!(receipt.exists());
}

/// The readiness gate is lazy on Docker as on Tart: a slot with no container is created and started. A running
/// container of another declaration is made again under the caller's own lease, which authorizes its stop.
#[tokio::test]
async fn the_docker_readiness_gate_starts_a_free_slot_and_recreates_a_stale_one() {
    let fixture = Fixture::docker();
    let worker = fixture.worker(0);
    let lease = fixture.write_lease(worker, "token-1");
    fixture.manager.require_ready(&ctx(), &lease).await.unwrap();
    assert!(fixture.fake.saw_call_containing(&format!("start {worker}")));

    // Current and answering: nothing is made or started again.
    let before = fixture.fake.calls().len();
    fixture.manager.require_ready(&ctx(), &lease).await.unwrap();
    assert!(
        !fixture.fake.calls()[before..]
            .iter()
            .any(|call| call.starts_with("create ") || call.starts_with("start ")),
        "{:#?}",
        &fixture.fake.calls()[before..]
    );

    declare_stale_container(&fixture, worker);
    let before = fixture.fake.calls().len();
    fixture.manager.require_ready(&ctx(), &lease).await.unwrap();
    let after: Vec<String> = fixture.fake.calls()[before..].to_vec();
    for verb in ["stop ", &format!("rm {worker}"), "create ", "start "] {
        assert!(after.iter().any(|call| call.starts_with(verb)), "no {verb:?} in {after:#?}");
    }
    assert!(docker(&fixture).container_is_current(&ctx(), worker).await.unwrap());

    // A running, answering container that another create made is not trusted either.
    fixture.fake.answer(Answer::ContainerId, "hand-made\n");
    fixture.manager.require_ready(&ctx(), &lease).await.unwrap();
    assert_eq!(
        docker(&fixture).read_create_record(worker).unwrap().container_id,
        "fake-container-3"
    );
}

/// A running container that does not answer and is stale is made again under the caller's own lease. The gate must
/// not refuse its own holder `worker_leased`. The guest never answers in this fixture, so the gate ends in a boot
/// timeout of one second, after the container was made again.
#[tokio::test]
async fn the_docker_readiness_gate_recreates_a_hung_stale_container_under_its_own_lease() {
    let fixture = Fixture::docker_builder().env("AIR_VM_BOOT_TIMEOUT", "1").build();
    let worker = fixture.worker(0);
    fixture.manager.start(&ctx(), worker).await.unwrap();
    let lease = fixture.write_lease(worker, "token-1");
    declare_stale_container(&fixture, worker);
    fixture
        .guest
        .answer(answer_guest(vec![("/usr/bin/true", avl_host_testkit::failed(1, "not answering"))]));
    let refusal = fixture.manager.require_ready(&ctx(), &lease).await.unwrap_err();
    assert_eq!(refusal.code, "boot_timeout", "{refusal:?}");
    assert_eq!(
        docker(&fixture).read_create_record(worker).unwrap().container_id,
        "fake-container-2"
    );
}

// --- the idle stop on the Apple `container` engine ---------------------------------------------------------------

/// A container pool with an idle stop of `seconds`, whose one worker runs.
#[cfg(unix)]
async fn idle_container_pool(seconds: &str) -> Fixture {
    let fixture = Fixture::docker_builder()
        .container_engine()
        .env("AIR_VM_IDLE_STOP", seconds)
        .build();
    let worker = fixture.worker(0);
    fixture.manager.start_without_lifecycle_lock(&ctx(), worker).await.unwrap();
    fixture.fake.forget_calls();
    fixture
}

/// The nonce of the one idle stop that the fixture was asked to start.
#[cfg(unix)]
fn scheduled_nonce(fixture: &Fixture) -> String {
    let requests = avl_base::sync::lock(&fixture.idle_stops).clone();
    assert_eq!(requests.len(), 1, "{requests:?}");
    requests[0].nonce.clone()
}

#[cfg(unix)]
fn stop_calls(fixture: &Fixture) -> Vec<String> {
    fixture.fake.calls().into_iter().filter(|call| call.starts_with("stop ")).collect()
}

/// An unleased running worker whose record carries the nonce is stopped with `stop -t 10`, and the record goes.
#[cfg(unix)]
#[tokio::test]
async fn an_idle_stop_stops_an_unleased_running_worker() {
    let fixture = idle_container_pool("60").await;
    let worker = fixture.worker(0);
    let deadline = fixture
        .manager
        .schedule_idle_stop(&ctx(), worker)
        .await
        .unwrap()
        .expect("a deadline");
    let record = read_idle_stop_record(&fixture.settings, worker).expect("the record");
    assert_eq!(record.deadline, deadline);
    let nonce = scheduled_nonce(&fixture);
    assert_eq!(record.nonce, nonce);
    let released: jiff::Timestamp = record.released_at.parse().unwrap();
    let due: jiff::Timestamp = record.deadline.parse().unwrap();
    assert_eq!(due.duration_since(released), jiff::SignedDuration::from_secs(60));

    let verdict = fixture.manager.idle_stop_worker(&ctx(), worker, &nonce).await.unwrap();
    assert_eq!(verdict, IdleStopVerdict::Stopped);
    assert_eq!(stop_calls(&fixture), [format!("stop -t 10 {worker}")]);
    assert_eq!(read_idle_stop_record(&fixture.settings, worker), None);
}

/// A leased worker keeps running, and so does a worker whose record is gone or carries another nonce: a later
/// operation took the slot.
#[cfg(unix)]
#[tokio::test]
async fn an_idle_stop_keeps_a_worker_that_somebody_took() {
    let fixture = idle_container_pool("60").await;
    let worker = fixture.worker(0);
    fixture.manager.schedule_idle_stop(&ctx(), worker).await.unwrap();
    let nonce = scheduled_nonce(&fixture);
    assert_eq!(
        fixture.manager.idle_stop_worker(&ctx(), worker, "another-nonce").await.unwrap(),
        IdleStopVerdict::Superseded
    );
    let lease = fixture.write_lease(worker, "token-1");
    assert_eq!(
        fixture.manager.idle_stop_worker(&ctx(), worker, &nonce).await.unwrap(),
        IdleStopVerdict::Leased
    );
    std::fs::remove_file(fixture.settings.lease_path(&lease.worker)).unwrap();
    remove_idle_stop_record(&fixture.settings, worker).unwrap();
    assert_eq!(
        fixture.manager.idle_stop_worker(&ctx(), worker, &nonce).await.unwrap(),
        IdleStopVerdict::Superseded
    );
    assert_eq!(stop_calls(&fixture), Vec::<String>::new());
}

/// A server that is down runs no container, so the idle stop starts nothing, stops nothing and removes the record.
#[cfg(unix)]
#[tokio::test]
async fn an_idle_stop_on_a_stopped_engine_starts_nothing() {
    let fixture = Fixture::docker_builder().container_engine().env("AIR_VM_IDLE_STOP", "60").build();
    let worker = fixture.worker(0);
    fixture.manager.schedule_idle_stop(&ctx(), worker).await.unwrap();
    let nonce = scheduled_nonce(&fixture);
    assert_eq!(
        fixture.manager.idle_stop_worker(&ctx(), worker, &nonce).await.unwrap(),
        IdleStopVerdict::AlreadyStopped
    );
    let calls = fixture.fake.calls();
    assert!(
        !calls
            .iter()
            .any(|call| call.starts_with("system start") || call.starts_with("stop ")),
        "{calls:#?}"
    );
    assert_eq!(read_idle_stop_record(&fixture.settings, worker), None);
}

/// The detached process waits for the deadline and then stops the worker. A process of another nonce exits at once.
/// A start removes the record, so the process of the last release stops nothing.
#[cfg(unix)]
#[tokio::test]
async fn the_idle_stop_process_waits_for_the_deadline() {
    let fixture = idle_container_pool("1").await;
    let worker = fixture.worker(0);
    fixture.manager.schedule_idle_stop(&ctx(), worker).await.unwrap();
    let nonce = scheduled_nonce(&fixture);
    let superseded = fixture.manager.pool_idle_stop(&ctx(), worker, "another-nonce").await.unwrap();
    assert_eq!(superseded.text, format!("{worker}: idle_stop=superseded"));
    let started = std::time::Instant::now();
    let stopped = fixture.manager.pool_idle_stop(&ctx(), worker, &nonce).await.unwrap();
    assert_eq!(stopped.data["idleStop"], "stopped", "{stopped:?}");
    assert!(
        started.elapsed() >= std::time::Duration::from_millis(500),
        "{:?}",
        started.elapsed()
    );
    assert_eq!(stop_calls(&fixture), [format!("stop -t 10 {worker}")]);

    fixture.manager.schedule_idle_stop(&ctx(), worker).await.unwrap();
    let second = avl_base::sync::lock(&fixture.idle_stops)[1].nonce.clone();
    fixture.manager.start_without_lifecycle_lock(&ctx(), worker).await.unwrap();
    assert_eq!(read_idle_stop_record(&fixture.settings, worker), None);
    let after_start = fixture.manager.pool_idle_stop(&ctx(), worker, &second).await.unwrap();
    assert_eq!(after_start.data["idleStop"], "superseded");
}

/// A container pool of two workers with an idle stop of 60 s. The fake holds one container for every name, so both
/// workers run.
#[cfg(unix)]
async fn idle_container_pair() -> Fixture {
    let fixture = Fixture::docker_builder()
        .container_engine()
        .env("AIR_VM_IDLE_STOP", "60")
        .env("AIR_VM_WORKERS", "air-docker-1,air-docker-2")
        .build();
    fixture.manager.prepare_runtime_dirs().unwrap();
    fixture
        .manager
        .start_without_lifecycle_lock(&ctx(), fixture.worker(0))
        .await
        .unwrap();
    fixture.fake.forget_calls();
    fixture
}

/// Writes a record of `worker` whose deadline passed `overdue` ago, as a release leaves it when its process died.
#[cfg(unix)]
fn write_overdue_idle_stop(fixture: &Fixture, worker: &str, overdue: jiff::SignedDuration) -> IdleStopRecord {
    let deadline = jiff::Timestamp::now().checked_sub(overdue).unwrap();
    let record = IdleStopRecord {
        schema_version: avl_base::SCHEMA_VERSION,
        worker: worker.to_owned(),
        released_at: avl_base::clock::stamp(deadline.checked_sub(jiff::SignedDuration::from_mins(1)).unwrap()),
        deadline: avl_base::clock::stamp(deadline),
        nonce: "the-nonce-of-a-dead-process".to_owned(),
    };
    avl_base::fs::write_atomically(
        &fixture.settings.idle_stop_record_path(worker),
        &serde_json::to_vec(&record).unwrap(),
        0o600,
    )
    .unwrap();
    record
}

/// A release stops another unleased worker whose record passed its deadline more than one poll ago, because no
/// process waits for that record. The release still schedules the idle stop of its own worker.
#[cfg(unix)]
#[tokio::test]
async fn a_release_stops_another_worker_whose_idle_stop_has_no_process() {
    let fixture = idle_container_pair().await;
    let [stranded, released] = [fixture.worker(0), fixture.worker(1)];
    let overdue = write_overdue_idle_stop(&fixture, stranded, jiff::SignedDuration::from_mins(10));
    let notes = crate::worker::testing::Notes::start(fixture.manager.reporter());

    let deadline = fixture
        .manager
        .schedule_idle_stop(&ctx(), released)
        .await
        .unwrap()
        .expect("a deadline");

    assert_eq!(stop_calls(&fixture), [format!("stop -t 10 {stranded}")]);
    assert_eq!(read_idle_stop_record(&fixture.settings, stranded), None);
    let own = read_idle_stop_record(&fixture.settings, released).expect("the record of the released worker");
    assert_eq!(own.deadline, deadline);
    assert_eq!(
        avl_base::sync::lock(&fixture.idle_stops).clone(),
        [IdleStopRequest {
            worker: released.to_owned(),
            nonce: own.nonce,
        }]
    );
    let notes = notes.all();
    let expected = format!(
        "stopped {stranded}, whose idle stop passed its deadline at {} without its detached process",
        overdue.deadline
    );
    assert!(notes.contains(&expected), "{notes:#?}");
}

/// A release keeps another worker whose record passed its deadline by less than one poll, because its process can
/// still decide. It also keeps a worker with a lease, a worker whose lifecycle lock another operation holds, and a
/// worker without a record.
#[cfg(unix)]
#[tokio::test]
async fn a_release_keeps_another_worker_whose_idle_stop_can_still_decide() {
    let fixture = idle_container_pair().await;
    let [other, released] = [fixture.worker(0), fixture.worker(1)];

    let late = write_overdue_idle_stop(&fixture, other, jiff::SignedDuration::from_secs(5));
    fixture.manager.schedule_idle_stop(&ctx(), released).await.unwrap();
    assert_eq!(read_idle_stop_record(&fixture.settings, other), Some(late));

    let leased = write_overdue_idle_stop(&fixture, other, jiff::SignedDuration::from_mins(10));
    let lease = fixture.write_lease(other, "token-1");
    fixture.manager.schedule_idle_stop(&ctx(), released).await.unwrap();
    assert_eq!(read_idle_stop_record(&fixture.settings, other), Some(leased.clone()));
    std::fs::remove_file(fixture.settings.lease_path(&lease.worker)).unwrap();

    let busy = fixture
        .manager
        .locks()
        .acquire(&ctx(), &fixture.manager.lifecycle_lock_path(other), "run", "busy")
        .await
        .unwrap();
    fixture.manager.schedule_idle_stop(&ctx(), released).await.unwrap();
    assert_eq!(read_idle_stop_record(&fixture.settings, other), Some(leased));
    drop(busy);

    remove_idle_stop_record(&fixture.settings, other).unwrap();
    fixture.manager.schedule_idle_stop(&ctx(), released).await.unwrap();
    assert_eq!(stop_calls(&fixture), Vec::<String>::new());
    assert_eq!(avl_base::sync::lock(&fixture.idle_stops).len(), 4);
}
