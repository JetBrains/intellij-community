//! The typed seam. The command's two reply shapes are pinned in the lease suite, because those are a wire contract;
//! what is pinned here is that nothing inside this process has to read that wire to hold or free a worker.

use avl_base::GuestOs;
use pretty_assertions::assert_eq;

use super::*;
#[cfg(unix)]
use crate::worker::lease::receipt::receipt_files;
use crate::worker::lease::receipt_for_path;
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

/// The check is public because a caller can assemble a set this module did not place: the flake harness mixes a
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
