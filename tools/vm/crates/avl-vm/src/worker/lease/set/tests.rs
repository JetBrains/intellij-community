//! The typed seam. The command's two reply shapes are pinned in the lease suite, because those are a wire contract;
//! what is pinned here is that nothing inside this process has to read that wire to hold or free a worker.

use avl_base::GuestOs;
use avl_host_sys::lock::LockGuard;
use pretty_assertions::assert_eq;

use super::*;
use crate::worker::hypervisor::SlotState;
#[cfg(unix)]
use crate::worker::lease::receipt::receipt_files;
use crate::worker::lease::receipt_for_path;
use crate::worker::testing::Fixture;
#[cfg(unix)]
use avl_host_testkit::FakeProbe;

use crate::worker::lease::tests::{ctx, pool, request};

#[tokio::test]
async fn acquired_workers_carry_the_handle_that_frees_them() {
    let fixture = pool("air-macos-1,air-macos-2");
    let held = acquire_workers(&ctx(), &fixture.manager, &request("shardable", 2, false))
        .await
        .unwrap();
    assert_eq!(held.len(), 2);
    for (index, item) in held.iter().enumerate() {
        // Every value the caller needs is here: the lease it holds, and the receipt that is its only handle. A receipt
        // that does not validate is a worker nobody can use and nobody can free.
        let (path, validated) = receipt_for_path(&fixture.settings, &item.receipt).unwrap();
        assert_eq!((path, &validated), (item.receipt.clone(), &item.lease));
        assert!(!item.recovered);
        // Each shard is its own holder, which is what lets each of them recover independently.
        assert_eq!(item.lease.holder, format!("shardable#{}", index + 1));
        // The acquisition time is a millisecond UTC stamp.
        assert!(
            jiff::fmt::strtime::parse("%Y-%m-%dT%H:%M:%S%.3fZ", &item.lease.acquired_at).is_ok()
                && item.lease.acquired_at.len() == "2026-08-23T00:00:00.000Z".len(),
            "{}",
            item.lease.acquired_at
        );
    }
    // The pool lock is held for the whole survey, so a second caller finds the pool full rather than taking a worker
    // between the survey and the placements.
    let refusal = acquire_workers(&ctx(), &fixture.manager, &request("second", 1, false))
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "pool_exhausted");
}

/// A count is a ceiling, and the seam says so with values rather than with two counts in a document: what came back is
/// the length of what came back.
#[tokio::test]
async fn acquire_workers_answers_fewer_rather_than_waiting() {
    let fixture = pool("air-macos-1");
    let held = acquire_workers(&ctx(), &fixture.manager, &request("shardable", 3, false))
        .await
        .unwrap();
    assert_eq!(held.len(), 1);

    // `exact` is the measurement run's escape hatch, and it unwinds what it placed rather than leaving a partly-owned
    // set behind.
    let fixture = pool("air-macos-1");
    let refusal = acquire_workers(&ctx(), &fixture.manager, &request("measurement", 2, true))
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "pool_exhausted");
    assert_eq!(read_lease(&fixture.settings.lease_path(fixture.worker(0))).unwrap(), None);
}

/// One refusal must not hide the others. A release is best effort by the time it runs, so the loop finishes the set and
/// answers per worker, and the caller decides what to say about a worker that stayed held.
// Liveness on Tart is the pid receipt, and a Windows host has no Tart worker.
#[cfg(unix)]
#[tokio::test]
async fn release_workers_finishes_the_set_and_names_what_stayed_held() {
    let fixture = pool("air-macos-1,air-macos-2");
    let held = acquire_workers(&ctx(), &fixture.manager, &request("shardable", 2, false))
        .await
        .unwrap();
    // The first worker reads as running - liveness on Tart is the pid receipt - so its release goes through the guest,
    // and this fixture's guest answers nothing the release gate accepts.
    let stuck = held[0].lease.worker.clone();
    fixture.run_as_fake_process(&stuck).await;

    let results = release_workers(&ctx(), &fixture.manager, &held, &FakeProbe::default()).await;
    assert_eq!(results.len(), 2);
    assert_eq!(results[0].worker, stuck);
    assert!(!results[0].released && results[0].failure.is_some(), "{results:?}");
    // The answer carries the handle that still frees it.
    assert_eq!(results[0].receipt, held[0].receipt);
    assert!(results[1].released && results[1].failure.is_none(), "{results:?}");
    assert_eq!(disposition(&results), LeaseDisposition::ReleaseFailed);
    assert_eq!(disposition(&results[1..]), LeaseDisposition::Released);
    assert_eq!(serde_json::to_value(LeaseDisposition::ReleaseFailed).unwrap(), "release_failed");

    // What the answers say is what is on disk: one worker held with its receipt, one worker free with none.
    assert!(read_lease(&fixture.settings.lease_path(&stuck)).unwrap().is_some());
    assert_eq!(read_lease(&fixture.settings.lease_path(&results[1].worker)).unwrap(), None);
    assert_eq!(receipt_files(&fixture.settings), vec![held[0].receipt.clone()]);
}

// --- a busy worker ---------------------------------------------------------------------------------------------

/// Holds the lifecycle lock of a worker, as a start does.
async fn busy(fixture: &Fixture, worker: &str) -> LockGuard {
    fixture.manager.prepare_runtime_dirs().unwrap();
    fixture
        .manager
        .locks()
        .acquire(&ctx(), &fixture.manager.lifecycle_lock_path(worker), "pool-start", "busy")
        .await
        .unwrap()
}

/// Places a lease of `holder` on a worker with its receipt, as an earlier acquisition does.
fn leased(fixture: &Fixture, worker: &str, holder: &str) -> Lease {
    fixture.manager.prepare_runtime_dirs().unwrap();
    let lease = fixture.new_lease(worker, &format!("token-of-{holder}"), holder);
    assert!(try_atomic_lease(&fixture.settings, worker, &lease).unwrap());
    write_lease_receipt(&fixture.settings, &lease).unwrap();
    lease
}

/// A pool of two Tart workers whose first worker the holder `agent` leased.
fn pool_leased_by_agent() -> (Fixture, Lease) {
    let fixture = pool("air-macos-1,air-macos-2");
    let lease = leased(&fixture, fixture.worker(0), "agent");
    (fixture, lease)
}

/// Acquires one worker of `fixture` for the holder `agent`.
async fn acquire_for_agent(fixture: &Fixture) -> Result<Vec<HeldWorker>, Refusal> {
    acquire_workers(&ctx(), &fixture.manager, &request("agent", 1, false)).await
}

/// One busy worker must not refuse the whole pool. The busy worker is not available, and the acquisition leases a
/// free one. `worker_busy` is the answer only when the busy worker was in the way and no lease could be placed.
#[tokio::test]
async fn an_acquisition_skips_a_busy_worker_and_leases_a_free_one() {
    let fixture = pool("air-macos-1,air-macos-2");
    leased(&fixture, fixture.worker(0), "peer");
    let start = busy(&fixture, fixture.worker(0)).await;
    let held = acquire_workers(&ctx(), &fixture.manager, &request("next", 1, false)).await.unwrap();
    assert_eq!((held[0].lease.worker.as_str(), held[0].recovered), (fixture.worker(1), false));

    let refusal = acquire_workers(&ctx(), &fixture.manager, &request("third", 1, false))
        .await
        .unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("worker_busy", Exit::TEMP_FAIL));
    assert!(refusal.message.contains(fixture.worker(0)), "{refusal}");
    // With no busy worker in the way, the full pool is exhausted.
    drop(start);
    let refusal = acquire_workers(&ctx(), &fixture.manager, &request("third", 1, false))
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "pool_exhausted");
}

/// A busy worker without a lease, such as one that `pool start` brings up, is skipped too.
#[tokio::test]
async fn an_acquisition_skips_a_busy_free_worker() {
    let fixture = pool("air-macos-1,air-macos-2");
    let _start = busy(&fixture, fixture.worker(0)).await;
    let held = acquire_workers(&ctx(), &fixture.manager, &request("next", 1, false)).await.unwrap();
    assert_eq!(held[0].lease.worker, fixture.worker(1));
}

/// The recovery keeps its meaning. A busy worker that holds the lease of the holder refuses `worker_busy`, and the
/// acquisition places no second lease for that holder. When the operation ends, the holder recovers its worker.
#[tokio::test]
async fn a_busy_worker_of_the_holder_refuses_rather_than_leasing_a_second_one() {
    let (fixture, lease) = pool_leased_by_agent();
    let start = busy(&fixture, fixture.worker(0)).await;
    let refusal = acquire_for_agent(&fixture).await.unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("worker_busy", Exit::TEMP_FAIL));
    assert_eq!(read_lease(&fixture.settings.lease_path(fixture.worker(1))).unwrap(), None);

    // A sharded acquisition of the same holder name is another holder, so it is not stopped.
    let held = acquire_workers(&ctx(), &fixture.manager, &request("agent", 2, false))
        .await
        .unwrap();
    assert_eq!((held.len(), held[0].lease.worker.as_str()), (1, fixture.worker(1)));
    release_by_hand(&fixture, &held[0].lease);

    drop(start);
    let recovered = acquire_for_agent(&fixture).await.unwrap();
    assert_eq!((&recovered[0].lease, recovered[0].slot_reason), (&lease, SlotReason::Recovered));
}

/// A busy worker whose lease cannot be read may hold the lease of the holder, so it refuses as one that does.
#[tokio::test]
async fn a_busy_worker_with_an_unreadable_lease_refuses() {
    let fixture = pool("air-macos-1,air-macos-2");
    let _start = busy(&fixture, fixture.worker(0)).await;
    std::fs::write(fixture.settings.lease_path(fixture.worker(0)), "{").unwrap();
    let refusal = acquire_for_agent(&fixture).await.unwrap_err();
    assert_eq!(refusal.code, "worker_busy");
    assert_eq!(read_lease(&fixture.settings.lease_path(fixture.worker(1))).unwrap(), None);
}

/// A sharded acquisition spreads over the workers that are not busy. `--exact` short of its count because of a busy
/// worker refuses `worker_busy` and leaves no lease behind.
#[tokio::test]
async fn a_sharded_acquisition_skips_a_busy_worker_and_exact_unwinds() {
    let fixture = pool("air-macos-1,air-macos-2,air-macos-3");
    let _start = busy(&fixture, fixture.worker(1)).await;
    let held = acquire_workers(&ctx(), &fixture.manager, &request("shardable", 3, false))
        .await
        .unwrap();
    let workers: Vec<&str> = held.iter().map(|item| item.lease.worker.as_str()).collect();
    assert_eq!(workers, [fixture.worker(0), fixture.worker(2)]);
    for item in &held {
        release_by_hand(&fixture, &item.lease);
    }

    let refusal = acquire_workers(&ctx(), &fixture.manager, &request("measurement", 3, true))
        .await
        .unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("worker_busy", Exit::TEMP_FAIL));
    assert!(refusal.message.contains("--exact asked for 3 workers and only 2"), "{refusal}");
    for worker in &fixture.settings.workers {
        assert_eq!(read_lease(&fixture.settings.lease_path(worker)).unwrap(), None);
    }
}

/// A slot that becomes busy between the survey and the placement is skipped as a taken one is.
#[tokio::test]
async fn a_placement_on_a_busy_worker_answers_busy() {
    let fixture = pool("air-macos-1");
    let worker = fixture.worker(0);
    let _start = busy(&fixture, worker).await;
    let lease = fixture.new_lease(worker, "token", "late");
    assert!(matches!(
        place_lease(&ctx(), &fixture.manager, worker, &lease).await.unwrap(),
        Placement::Busy
    ));
    assert_eq!(read_lease(&fixture.settings.lease_path(worker)).unwrap(), None);
}

/// Unlinks a lease and its receipts without the release gate, which asks a guest that this fixture does not have.
fn release_by_hand(fixture: &Fixture, lease: &Lease) {
    std::fs::remove_file(fixture.settings.lease_path(&lease.worker)).unwrap();
    remove_lease_receipts(&fixture.settings, lease);
}

/// A run holds the lifecycle lock only at its ends, so the recovery reads the run marker. A live run of another process
/// on the worker of the holder refuses `worker_busy`, names the run, and places no second lease for the holder.
#[cfg(unix)]
#[tokio::test]
async fn a_recovery_refuses_while_a_run_of_the_holder_is_in_flight() {
    let (fixture, lease) = pool_leased_by_agent();
    let marker = fixture.write_run_marker(&lease, "suite-user-run-1", fixture.another_process().await);
    let refusal = acquire_for_agent(&fixture).await.unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("worker_busy", Exit::TEMP_FAIL));
    assert!(
        refusal.message.contains(&format!(
            "its run suite-user-run-1 is in flight (pid {} since 2026-10-13T12:00:00.000Z)",
            marker.process.pid
        )),
        "{refusal}"
    );
    assert_eq!(read_lease(&fixture.settings.lease_path(fixture.worker(1))).unwrap(), None);
    assert!(fixture.settings.run_marker_path(fixture.worker(0)).exists());
}

/// A stale marker does not stop the recovery: the acquisition removes it and answers the lease of the holder.
#[cfg(unix)]
#[tokio::test]
async fn a_recovery_removes_a_stale_marker_and_recovers() {
    let (fixture, lease) = pool_leased_by_agent();
    fixture.write_run_marker(&lease, "suite-user-run-1", Fixture::gone_process());
    let recovered = acquire_for_agent(&fixture).await.unwrap();
    assert_eq!((&recovered[0].lease, recovered[0].slot_reason), (&lease, SlotReason::Recovered));
    assert!(!fixture.settings.run_marker_path(fixture.worker(0)).exists());
}
/// receipt its invoker supplied with the workers it leased itself.
#[test]
fn require_one_guest_os_refuses_a_set_a_caller_assembled() {
    let fixture = pool("air-macos-1,air-macos-2");
    let given = Lease {
        guest_os: GuestOs::Linux,
        ..fixture.new_lease(fixture.worker(0), "token", "the-invoker")
    };
    let ours = fixture.new_lease(fixture.worker(1), "token", "vm-flake");
    let held = |lease: &Lease, receipt: &str| HeldWorker {
        lease: lease.clone(),
        receipt: PathBuf::from(receipt),
        recovered: false,
        slot_reason: SlotReason::Borrowed,
    };

    let refusal = require_one_guest_os(&fixture.settings, &[held(&given, "given.json"), held(&ours, "ours.json")]).unwrap_err();
    assert_eq!(refusal.code, "lease_guest_os_mismatch");
    assert!(
        refusal.message.contains(fixture.worker(0)) && refusal.message.contains("one run cannot span guests"),
        "{refusal}"
    );
    // A set of one guest is what every other caller has, and it passes.
    require_one_guest_os(&fixture.settings, &[held(&ours, "ours.json")]).unwrap();
}

// --- the slot preference --------------------------------------------------------------------------------------

fn slot(worker: &str, reason: SlotReason) -> SlotFacts {
    SlotFacts {
        worker: worker.to_owned(),
        reason,
    }
}

fn workers<'a>(ranked: &[&'a SlotFacts]) -> Vec<&'a str> {
    ranked.iter().map(|slot| slot.worker.as_str()).collect()
}

/// A free slot is classed by its state and its daemon record, and a slot of no running state keeps class 2.
#[test]
fn a_slot_is_classed_by_its_state_and_its_daemon() {
    assert_eq!(SlotReason::of(SlotState::Running, true), SlotReason::WarmDaemon);
    assert_eq!(SlotReason::of(SlotState::Running, false), SlotReason::Running);
    assert_eq!(SlotReason::of(SlotState::Unknown, true), SlotReason::Unranked);
    assert_eq!(SlotReason::of(SlotState::Stopped, true), SlotReason::Stopped);
    assert_eq!(SlotReason::of(SlotState::Absent, false), SlotReason::Absent);
}

/// The four classes come in order: the warm daemon, a running slot, a stopped slot, an absent slot.
#[test]
fn the_ranking_puts_the_four_classes_in_order() {
    let facts = [
        slot("air-docker-1", SlotReason::Absent),
        slot("air-docker-2", SlotReason::Stopped),
        slot("air-docker-3", SlotReason::Running),
        slot("air-docker-4", SlotReason::WarmDaemon),
    ];
    assert_eq!(
        workers(&rank_slots(&facts)),
        ["air-docker-4", "air-docker-3", "air-docker-2", "air-docker-1"]
    );
}

/// Within one class the pool order stays, so a pool of one class, or of no running state, keeps today's order.
#[test]
fn the_ranking_keeps_the_pool_order_within_a_class() {
    let facts = [
        slot("air-docker-1", SlotReason::Unranked),
        slot("air-docker-2", SlotReason::Running),
        slot("air-docker-3", SlotReason::Unranked),
        slot("air-docker-4", SlotReason::Stopped),
        slot("air-docker-5", SlotReason::Stopped),
    ];
    assert_eq!(
        workers(&rank_slots(&facts)),
        ["air-docker-1", "air-docker-2", "air-docker-3", "air-docker-4", "air-docker-5"]
    );
}

/// Each pending shard takes the next slot of the same ranking, so a shard of two over a mixed pool takes the two
/// warmest slots.
#[test]
fn a_shard_of_two_takes_the_two_warmest_slots() {
    let facts = [
        slot("air-docker-1", SlotReason::Absent),
        slot("air-docker-2", SlotReason::Running),
        slot("air-docker-3", SlotReason::Stopped),
        slot("air-docker-4", SlotReason::WarmDaemon),
    ];
    let ranked = rank_slots(&facts);
    assert_eq!(workers(&ranked[..2]), ["air-docker-4", "air-docker-2"]);
}

/// A lease lands on the slot the backend knows, before a slot with no machine, and the reply names the reason. The
/// fake Tart knows the second slot only, and runs none.
#[cfg(unix)]
#[tokio::test]
async fn a_lease_prefers_a_stopped_slot_to_an_absent_one() {
    let fixture = pool("air-macos-1,air-macos-2");
    fixture.fake.answer(avl_testkit::tartfake::Answer::ListQuiet, "air-macos-2\n");
    let held = acquire_workers(&ctx(), &fixture.manager, &request("warm", 1, false)).await.unwrap();
    assert_eq!(
        (held[0].lease.worker.as_str(), held[0].slot_reason),
        ("air-macos-2", SlotReason::Stopped)
    );
    // A second lease takes the absent slot that is left.
    let second = acquire_workers(&ctx(), &fixture.manager, &request("cold", 1, false)).await.unwrap();
    assert_eq!(second[0].lease.worker, "air-macos-1");
}

/// The holder-recovery path comes first: a holder that already has a lease gets it back, whatever the ranking says.
#[cfg(unix)]
#[tokio::test]
async fn the_recovery_path_comes_before_the_ranking() {
    let fixture = pool("air-macos-1,air-macos-2");
    let first = acquire_workers(&ctx(), &fixture.manager, &request("again", 1, false))
        .await
        .unwrap();
    assert_eq!(first[0].lease.worker, "air-macos-1");
    fixture.fake.answer(avl_testkit::tartfake::Answer::ListQuiet, "air-macos-2\n");
    let recovered = acquire_workers(&ctx(), &fixture.manager, &request("again", 1, false))
        .await
        .unwrap();
    assert_eq!(
        (recovered[0].lease.worker.as_str(), recovered[0].slot_reason),
        ("air-macos-1", SlotReason::Recovered)
    );
}

/// The fake Tart pool knows no slot, so every slot is of one class, and a shard over it keeps the pool order.
#[cfg(unix)]
#[tokio::test]
async fn a_pool_of_one_class_keeps_its_order() {
    let fixture = pool("air-macos-1,air-macos-2,air-macos-3");
    let held = acquire_workers(&ctx(), &fixture.manager, &request("order", 3, false))
        .await
        .unwrap();
    let placed: Vec<(&str, SlotReason)> = held.iter().map(|item| (item.lease.worker.as_str(), item.slot_reason)).collect();
    assert_eq!(
        placed,
        [
            ("air-macos-1", SlotReason::Absent),
            ("air-macos-2", SlotReason::Absent),
            ("air-macos-3", SlotReason::Absent),
        ]
    );
}
