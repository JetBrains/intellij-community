//! The driver against the shared fixture's two-worker pool: a fake `tart` reporting an empty pool, so a release
//! finishes without a guest, and a fake Bazel whose builds the suite counts or refuses.

use std::sync::atomic::{AtomicUsize, Ordering};
use std::time::Duration;

use avl_base::{Exit, GuestOs};
use avl_host_sys::Signal;
use avl_wire::progress::{Kind, PhaseChange, PhaseState};
use pretty_assertions::assert_eq;

use super::*;
use crate::daemon::build::DaemonTarget;
use crate::daemon::fixture::Fixture;
use crate::daemon::testing::Recorded;
use crate::worker::run_marker::read_run_marker;
use avl_base::RefusalExt;

/// The run id every case but the journal one passes.
const RUN_ID: &str = "suite-user-test-run";

fn two(borrowed: Option<&Path>) -> Workers<'_> {
    Workers {
        borrowed,
        holder: None,
        count: 2,
        exact: false,
    }
}

async fn bounded<T>(future: impl Future<Output = T>) -> T {
    tokio::time::timeout(Duration::from_secs(120), future)
        .await
        .expect("the leased run settles")
}

/// [`Host::leased_run`] of [`RUN_ID`] on two workers of the lanes, with the receipt `borrowed` of the caller, bounded.
async fn two_leased<P, T>(
    fixture: &Fixture,
    borrowed: Option<&Path>,
    divide: impl FnOnce(&[HeldWorker]) -> Result<P, Refusal>,
    body: impl AsyncFn(&P, usize, Lease, &Arc<PreparedBuild>) -> T,
) -> Result<Leased<P, T>, Refusal> {
    bounded(
        fixture
            .host
            .leased_run(&Ctx::background(), RUN_ID, "test", two(borrowed), DaemonTarget::Lanes, divide, body),
    )
    .await
}

/// A [`two_leased`] that must refuse before any body runs: the refusal. No worker is leased after it.
async fn refused_before_any_body(fixture: &Fixture, divide: impl FnOnce(&[HeldWorker]) -> Result<(), Refusal>) -> Refusal {
    let ran = AtomicUsize::new(0);
    let refusal = refused(
        two_leased(
            fixture,
            None,
            divide,
            async |(): &(), _: usize, _: Lease, _: &Arc<PreparedBuild>| {
                ran.fetch_add(1, Ordering::SeqCst);
            },
        )
        .await,
    );
    for worker in fixture.workers() {
        assert!(
            !fixture.leased(&worker),
            "{worker} is still leased after the refusal {}",
            refusal.code
        );
    }
    assert_eq!(ran.load(Ordering::SeqCst), 0, "no body ran");
    refusal
}

fn refused<T>(result: Result<T, Refusal>) -> Refusal {
    match result {
        Ok(_) => panic!("the leased run was expected to refuse"),
        Err(refusal) => refusal,
    }
}

fn phases(recorded: &Recorded) -> Vec<String> {
    recorded
        .data_of::<PhaseChange>(Kind::Phase)
        .iter()
        .map(|change| format!("{}:{}", change.phase, change.state))
        .collect()
}

fn held_workers<P, T>(leased: &Leased<P, T>) -> Vec<String> {
    leased.held.iter().map(|item| item.lease.worker.clone()).collect()
}

// A cold or broken analysis inside held leases is N workers nobody else can use for minutes, so the build runs first.
#[tokio::test]
async fn the_build_comes_before_the_lease() {
    let fixture = Fixture::pool().await;
    let recorded = Recorded::start(fixture.host.reporter());
    fixture
        .bazel
        .fail_builds(Refusal::new("host_build_failed", Exit::SOFTWARE, "the lane does not compile"));
    let refusal = refused_before_any_body(&fixture, |_| Ok(())).await;
    assert_eq!(refusal.code, "host_build_failed");
    assert!(
        !phases(&recorded).iter().any(|phase| phase.starts_with("lease:")),
        "{:?}",
        phases(&recorded)
    );

    // A healthy build: exactly one for the whole set, and then the lease phase.
    fixture.bazel.pass_builds();
    let builds = fixture.bazel.build_calls();
    let recorded = Recorded::start(fixture.host.reporter());
    let leased = two_leased(
        &fixture,
        None,
        |_| Ok(()),
        async |(): &(), _: usize, _: Lease, _: &Arc<PreparedBuild>| {},
    )
    .await
    .unwrap_or_else(|refusal| panic!("the run leases two workers: {refusal:?}"));
    assert_eq!(fixture.bazel.build_calls() - builds, 1);
    // The digest sweep of the build is its own phase, and it too comes before the lease. Its progress names the
    // time of each of its parts.
    assert_eq!(
        phases(&recorded),
        [
            "prepare:started",
            "prepare:progress",
            "prepare:finished",
            "lease:started",
            "lease:finished",
            "release:started",
            "release:finished"
        ]
    );
    let progress = recorded
        .data_of::<PhaseChange>(Kind::Phase)
        .into_iter()
        .find(|change| change.phase == Phase::Prepare && change.state == PhaseState::Progress)
        .and_then(|change| change.detail)
        .unwrap_or_default();
    for part in ["descriptor ", "cache open ", "digests ", "cache save ", "runfiles tree "] {
        assert!(progress.contains(part), "the prepare phase does not time {part:?}: {progress:?}");
    }
    assert_eq!(leased.settlement.disposition, LeaseDisposition::Released);
}

// The division is the one fallible step after the acquisition, so its refusal is where a leak would hide.
#[tokio::test]
async fn a_refused_division_gives_back_every_worker_it_acquired() {
    let fixture = Fixture::pool().await;
    let recorded = Recorded::start(fixture.host.reporter());
    let refusal = refused_before_any_body(&fixture, |held: &[HeldWorker]| {
        assert_eq!(held.len(), 2, "the division sees every held worker");
        Err(Refusal::usage("this split cannot be planned"))
    })
    .await;
    assert_eq!(
        (refusal.exit, refusal.message.as_str()),
        (Exit::USAGE, "this split cannot be planned")
    );
    assert!(
        phases(&recorded).contains(&"release:finished".to_owned()),
        "{:?}",
        phases(&recorded)
    );
}

// A body answers a value, so a worker whose lifecycle lock refuses is one entry, and its sibling's answer stands.
#[tokio::test]
async fn a_lock_refusal_is_one_settled_entry_and_its_siblings_still_run() {
    let fixture = Fixture::pool().await;
    let leased = two_leased(
        &fixture,
        None,
        |held: &[HeldWorker]| {
            // The second worker's lease changes under the run: another holder's token is on disk now.
            fixture.leased_by(&held[1].lease.worker, "somebody-else", GuestOs::Macos);
            Ok(())
        },
        async |(): &(), index: usize, current: Lease, _: &Arc<PreparedBuild>| (index, current.worker),
    )
    .await
    .unwrap_or_else(|refusal| panic!("a lock refusal is an entry, not a refusal: {refusal:?}"));
    let workers = held_workers(&leased);
    let [first, second] = leased.settled.as_slice() else {
        panic!("two entries, was {}", leased.settled.len());
    };
    assert_eq!(first.as_ref().map(Result::as_ref), Some(Ok(&(0, workers[0].clone()))));
    let refusal = second
        .as_ref()
        .and_then(|result| result.as_ref().err())
        .expect("the changed lease refuses its lock");
    assert_eq!(refusal.code, "lease_changed");
    // Both releases were attempted, and each is in the settlement.
    let released: Vec<&str> = leased.settlement.releases.iter().map(|result| result.worker.as_str()).collect();
    assert_eq!(released, [workers[0].as_str(), workers[1].as_str()]);
}

// An iteration still in flight is an active run, which a release refuses to free, so an interrupt keeps the leases.
#[tokio::test]
async fn an_interrupt_keeps_every_acquired_lease_and_names_each_receipt() {
    let fixture = Fixture::pool().await;
    let recorded = Recorded::start(fixture.host.reporter());
    let interrupts = fixture.interrupts.clone();
    let leased = two_leased(
        &fixture,
        None,
        |_| Ok(()),
        async |(): &(), index: usize, _: Lease, _: &Arc<PreparedBuild>| {
            if index == 0 {
                interrupts.deliver(Signal::Interrupt);
            }
            std::future::pending::<()>().await;
        },
    )
    .await
    .unwrap_or_else(|refusal| panic!("an interrupt is a settled run: {refusal:?}"));
    assert_eq!(leased.interrupted, Some("SIGINT"));
    assert!(leased.settled.iter().all(Option::is_none), "every body was dropped");
    assert_eq!(leased.settlement.disposition, LeaseDisposition::Kept);
    let receipts: Vec<String> = leased.held.iter().map(|item| item.receipt.to_string_lossy().into_owned()).collect();
    assert_eq!(leased.settlement.still_held, receipts);
    for receipt in &receipts {
        assert!(
            leased.release_hint("vm").contains(receipt.as_str()),
            "{}",
            leased.release_hint("vm")
        );
    }
    for worker in held_workers(&leased) {
        assert!(fixture.leased(&worker), "{worker} must still be leased");
        assert!(!leased.released(&worker));
    }
    // The body that took the interrupt was dropped after its start, so its run marker stays and goes stale with this
    // process.
    let marker = read_run_marker(&fixture.settings, &held_workers(&leased)[0]).unwrap();
    assert_eq!(marker.map(|marker| marker.run_id).as_deref(), Some(RUN_ID));
    assert!(
        !phases(&recorded).iter().any(|phase| phase.starts_with("release:")),
        "{:?}",
        phases(&recorded)
    );
}

// Every body runs under the run marker of its worker, and the lifecycle lock is free while it runs. Each marker names
// the run id of the journal, and the end of each body removes its marker.
#[tokio::test]
async fn every_body_runs_under_its_run_marker_with_the_lock_free() {
    let fixture = Fixture::pool().await;
    let host = &fixture.host;
    let (run_id, seen) = bounded(
        host.journaled(&Ctx::background(), "test", Vec::new(), async |ctx: &Ctx, run_id: &str| {
            let leased = host
                .leased_run(
                    ctx,
                    run_id,
                    "test",
                    two(None),
                    DaemonTarget::Lanes,
                    |_| Ok(()),
                    async |(): &(), _: usize, current: Lease, _: &Arc<PreparedBuild>| {
                        let lock = fixture.manager.lifecycle_lock_path(&current.worker);
                        let free = fixture
                            .manager
                            .locks()
                            .try_acquire(&Ctx::background(), &lock, "suite")
                            .await
                            .unwrap()
                            .is_some();
                        let marker = read_run_marker(&fixture.settings, &current.worker).unwrap();
                        (free, marker.map(|marker| (marker.run_id, marker.operation)))
                    },
                )
                .await?;
            let seen: Vec<_> = leased
                .settled
                .into_iter()
                .map(|settled| settled.expect("every body settled").expect("every run marker was written"))
                .collect();
            Ok((run_id.to_owned(), seen))
        }),
    )
    .await
    .unwrap_or_else(|refusal| panic!("the run leases two workers: {refusal:?}"));
    assert_eq!(seen.len(), 2);
    for (free, marker) in seen {
        assert!(free, "a body ran under the lifecycle lock");
        assert_eq!(marker, Some((run_id.clone(), "test".to_owned())));
    }
    for worker in fixture.workers() {
        assert_eq!(
            read_run_marker(&fixture.settings, &worker).unwrap(),
            None,
            "{worker} keeps a marker"
        );
    }
}

// `biased`: a signal that arrived before the fan-out is seen before any body is polled.
#[tokio::test]
async fn a_signal_before_the_fan_out_starts_no_body() {
    let fixture = Fixture::pool().await;
    let ran = AtomicUsize::new(0);
    let interrupts = fixture.interrupts.clone();
    let leased = two_leased(
        &fixture,
        None,
        |_| {
            interrupts.deliver(Signal::Terminate);
            Ok(())
        },
        async |(): &(), _: usize, _: Lease, _: &Arc<PreparedBuild>| {
            ran.fetch_add(1, Ordering::SeqCst);
        },
    )
    .await
    .unwrap_or_else(|refusal| panic!("an interrupt is a settled run: {refusal:?}"));
    assert_eq!(leased.interrupted, Some("SIGTERM"));
    assert_eq!(ran.load(Ordering::SeqCst), 0, "no body ran");
    assert_eq!(leased.settlement.disposition, LeaseDisposition::Kept);
    for worker in held_workers(&leased) {
        assert!(fixture.leased(&worker), "{worker} must still be leased");
    }
}

// A caller's receipt is chained on first and is the caller's to give back.
#[tokio::test]
async fn a_borrowed_receipt_runs_first_and_is_never_released() {
    let fixture = Fixture::pool().await;
    let workers = fixture.workers();
    let receipt = fixture.leased_by(&workers[0], "suite", GuestOs::Macos);
    let leased = two_leased(
        &fixture,
        Some(&receipt),
        |_| Ok(()),
        async |(): &(), index: usize, current: Lease, _: &Arc<PreparedBuild>| (index, current.worker),
    )
    .await
    .unwrap_or_else(|refusal| panic!("the run chains on the receipt: {refusal:?}"));
    assert_eq!(held_workers(&leased), workers);
    assert_eq!(leased.borrowed, 1);
    let acquired: Vec<&str> = leased.acquired().iter().map(|item| item.lease.worker.as_str()).collect();
    assert_eq!(acquired, [workers[1].as_str()]);
    let released: Vec<&str> = leased.settlement.releases.iter().map(|result| result.worker.as_str()).collect();
    assert_eq!(released, [workers[1].as_str()], "only the acquired worker");
    assert!(!fixture.leased(&workers[1]));
    assert!(receipt.is_file(), "the caller's receipt survives");
    assert!(fixture.leased(&workers[0]), "the caller's lease survives");
    assert_eq!(
        leased.verdict_lease().workers,
        [workers[1].clone()],
        "the verdict names only what this run leased"
    );
}

#[tokio::test]
async fn a_borrowed_receipt_of_another_guest_costs_no_build_and_no_lease() {
    let fixture = Fixture::pool().await;
    let workers = fixture.workers();
    let receipt = fixture.leased_by(&workers[0], "suite", GuestOs::Linux);
    let refusal = refused(
        two_leased(
            &fixture,
            Some(&receipt),
            |_| Ok(()),
            async |(): &(), _: usize, _: Lease, _: &Arc<PreparedBuild>| {},
        )
        .await,
    );
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("lease_guest_os_mismatch", Exit::NO_PERM));
    assert_eq!(fixture.bazel.build_calls(), 0, "the refusal costs no build");
    assert!(!fixture.leased(&workers[1]), "the refusal costs no lease");
}

// One id per invocation: the journal's and, unless the caller names one, the holder's. A named holder never becomes
// the journal's id, because a repeated id loses its journal.
#[tokio::test]
async fn the_run_id_is_the_holder_unless_one_is_named() {
    let fixture = Fixture::pool().await;
    let host = &fixture.host;
    for named in [None, Some("suite")] {
        let (run_id, holders) = bounded(
            host.journaled(&Ctx::background(), "test", Vec::new(), async |ctx: &Ctx, run_id: &str| {
                let workers = Workers {
                    holder: named,
                    ..two(None)
                };
                let leased = host
                    .leased_run(
                        ctx,
                        run_id,
                        "test",
                        workers,
                        DaemonTarget::Lanes,
                        |_| Ok(()),
                        async |(): &(), _: usize, current: Lease, _: &Arc<PreparedBuild>| current.holder,
                    )
                    .await?;
                let holders: Vec<String> = leased
                    .settled
                    .into_iter()
                    .map(|settled| settled.expect("every body settled").expect("every lock was taken"))
                    .collect();
                Ok((run_id.to_owned(), holders))
            }),
        )
        .await
        .unwrap_or_else(|refusal| panic!("the run leases two workers: {refusal:?}"));
        assert!(run_id.starts_with("suite-user-test-"), "{run_id}");
        let holder = named.unwrap_or(&run_id);
        assert_eq!(holders, [format!("{holder}#1"), format!("{holder}#2")]);
        assert!(
            fixture
                .settings
                .runtime_root
                .join("runs")
                .join(&run_id)
                .join("events.ndjson")
                .is_file(),
            "the journal is named by the run id"
        );
    }
}

/// `runStarted` carries the start of the process, so a journal shows how long the `vm.cmd` wrapper built before the
/// controller ran. The Unix answer is local time, and the record is UTC.
#[test]
fn a_process_start_reads_as_rfc3339_in_utc() {
    use super::rfc3339_of_process_start;
    let local = |day| {
        jiff::civil::date(2026, 10, day)
            .at(15, 41, 37, 0)
            .to_zoned(jiff::tz::TimeZone::system())
            .unwrap()
            .timestamp()
            .to_string()
    };
    assert_eq!(rfc3339_of_process_start("Sat Oct 10 15:41:37 2026"), Some(local(10)));
    // `ps` pads a day before the tenth, and the probe collapses the padding.
    assert_eq!(rfc3339_of_process_start("Fri Oct 9 15:41:37 2026"), Some(local(9)));
    assert_eq!(
        rfc3339_of_process_start("2026-10-10T13:41:37.25Z").as_deref(),
        Some("2026-10-10T13:41:37.25Z")
    );
    assert_eq!(rfc3339_of_process_start("yesterday"), None);
}
