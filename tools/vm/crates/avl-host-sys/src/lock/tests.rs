use std::sync::Arc;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::time::Instant;

use pretty_assertions::assert_eq;
use tokio_util::sync::CancellationToken;

use super::*;
use crate::testing::runner;

fn manager() -> LockManager {
    LockManager::new(runner())
}

/// The owner record a lock file carries, or the default when it carries none.
fn record(path: &Path) -> OwnerRecord {
    let content = std::fs::read(path).expect("the lock file is readable");
    if content.is_empty() {
        return OwnerRecord::default();
    }
    serde_json::from_slice(&content).expect("the owner record is JSON")
}

fn own_pid() -> u32 {
    std::process::id()
}

#[tokio::test]
async fn a_lock_is_taken_once_and_released() {
    let ctx = Ctx::background();
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join("lease-operation.lock");
    let locks = manager();
    let held = locks.acquire(&ctx, &path, "pool start", "busy").await.unwrap();
    // The lock names its owner, which is what lets an operator see which operation is in the way.
    let owned = record(&path);
    assert_eq!(owned.pid, own_pid());
    assert_eq!(owned.operation, "pool start");
    assert_eq!(owned.schema_version, SCHEMA_VERSION);
    assert!(!owned.process_start.is_empty(), "{owned:?}");
    assert!(owned.acquired_at.parse::<Timestamp>().is_ok(), "{owned:?}");

    drop(held);
    // The lock file **survives** its release, and that is the rule: a `flock` binds to the inode, so unlinking the
    // file lets one contender hold the old inode while another creates a fresh one at the same path. What a
    // release clears is the record.
    assert!(path.exists(), "the lock file was unlinked by its release");
    assert_eq!(record(&path), OwnerRecord::default());
    // And it can be taken again afterwards.
    locks.acquire(&ctx, &path, "pool stop", "busy").await.unwrap();
}

// A live holder is not displaced, and the refusal carries the caller's own message because that is what tells an
// operator which operation is in the way.
#[tokio::test]
async fn a_live_holder_is_refused() {
    let ctx = Ctx::background();
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join("op.lock");
    let busy = "worker air-linux-1 is handling another lifecycle operation";
    let locks = manager();
    let _held = locks.acquire(&ctx, &path, "run", busy).await.unwrap();
    let refusal = locks.acquire(&ctx, &path, "gc", busy).await.unwrap_err();
    assert_eq!(refusal.code, "lease_busy");
    assert_eq!(refusal.exit, Exit::TEMP_FAIL);
    assert_eq!(refusal.message, busy);
    // The refused acquisition left the holder's record alone. It is the same process refusing itself - the closest
    // a test gets to a peer - so this also pins that a second descriptor on one file excludes inside one process.
    let owned = record(&path);
    assert_eq!((owned.operation.as_str(), owned.pid), ("run", own_pid()));
}

// The owner record is diagnostics and decides nothing, so no content in a lock file can keep the lock from being
// taken once nobody holds it. The live-pid case is where this and the old reclaim protocol disagree: that protocol
// found a pid that exists with a matching start time and refused, permanently.
#[tokio::test]
async fn the_owner_record_decides_nothing() {
    let ctx = Ctx::background();
    let own_start = runner()
        .probe_process(&ctx, i32::try_from(own_pid()).unwrap(), PsField::StartTime)
        .await
        .unwrap()
        .unwrap_or_default();
    let live = serde_json::to_vec(&OwnerRecord {
        schema_version: SCHEMA_VERSION,
        pid: own_pid(),
        process_start: own_start,
        operation: "run".to_owned(),
        acquired_at: String::new(),
    })
    .unwrap();
    for (what, content) in [
        ("an empty file", Vec::new()),
        ("bytes that are not json", b"{not json".to_vec()),
        ("a record naming no pid", br#"{"schemaVersion":1}"#.to_vec()),
        ("a record naming this live pid", live),
    ] {
        let directory = tempfile::tempdir().unwrap();
        let path = directory.path().join("op.lock");
        std::fs::write(&path, content).unwrap();
        if let Err(refusal) = manager().acquire(&ctx, &path, "pool start", "busy").await {
            panic!("a lock carrying {what} was refused: {refusal:?}");
        }
    }
}

// Residue from a crashed holder never strands a lock: the kernel dropped the `flock` when the holder's descriptor
// closed, and the record it left behind is read by nobody.
#[tokio::test]
async fn residue_from_a_crashed_holder_never_strands_a_lock() {
    let ctx = Ctx::background();
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join("op.lock");
    let crashed = OwnerRecord {
        schema_version: SCHEMA_VERSION,
        pid: 0x7FFF_FFF0,
        process_start: "Thu Jan  1 00:00:00 1970".to_owned(),
        operation: "run".to_owned(),
        acquired_at: "1970-01-01T00:00:00Z".to_owned(),
    };
    let mut line = serde_json::to_vec(&crashed).unwrap();
    line.push(b'\n');
    // A holder that took the lock, wrote its record and went away without releasing anything: only its descriptor
    // closed, as a crash closes it.
    {
        let holder = File::create(&path).unwrap();
        try_lock(&holder).unwrap();
        write_record(&holder, &line).unwrap();
    }
    // Queued, briefly: a child another test thread is forking may hold a copy of the dead holder's descriptor until
    // it execs, which a crashed process's descriptors never outlive.
    let _held = manager()
        .acquire_queued(&ctx, &path, "pool start", Duration::from_secs(5), "lease_timeout", "busy")
        .await
        .expect("residue stranded the lock");
    // And taking it overwrites the dead invocation's account with this one's.
    let owned = record(&path);
    assert_eq!((owned.pid, owned.operation.as_str()), (own_pid(), "pool start"));
}

// The whole point of the lock: many contenders, and exactly one inside at a time.
#[tokio::test(flavor = "multi_thread", worker_threads = 4)]
async fn only_one_holder_is_inside_the_critical_section() {
    let directory = tempfile::tempdir().unwrap();
    let path = Arc::new(directory.path().join("op.lock"));
    let locks = Arc::new(manager());
    let inside = Arc::new(AtomicUsize::new(0));
    let peak = Arc::new(AtomicUsize::new(0));
    let succeeded = Arc::new(AtomicUsize::new(0));
    let mut contenders = tokio::task::JoinSet::new();
    for _ in 0..12 {
        let (path, locks) = (path.clone(), locks.clone());
        let (inside, peak, succeeded) = (inside.clone(), peak.clone(), succeeded.clone());
        contenders.spawn(async move {
            let ctx = Ctx::background();
            let Ok(_held) = locks
                .acquire_queued(&ctx, &path, "run", Duration::from_secs(20), "lease_timeout", "busy")
                .await
            else {
                return;
            };
            let now = inside.fetch_add(1, Ordering::SeqCst) + 1;
            peak.fetch_max(now, Ordering::SeqCst);
            succeeded.fetch_add(1, Ordering::SeqCst);
            tokio::time::sleep(Duration::from_millis(5)).await;
            inside.fetch_sub(1, Ordering::SeqCst);
        });
    }
    while contenders.join_next().await.is_some() {}
    assert_eq!(peak.load(Ordering::SeqCst), 1, "several holders were inside at once");
    // A queued acquisition does not give up inside its budget.
    assert_eq!(succeeded.load(Ordering::SeqCst), 12);
    // Every release gave the lock up, so it is free rather than gone.
    locks.acquire(&Ctx::background(), &path, "after", "busy").await.unwrap();
}

// A queued acquisition gives up at its deadline with the caller's own code, so "somebody is holding this" and "I
// waited and gave up" are distinguishable.
#[tokio::test]
async fn a_queued_acquisition_times_out_with_its_own_code() {
    let ctx = Ctx::background();
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join("op.lock");
    let locks = manager();
    let _held = locks.acquire(&ctx, &path, "run", "busy").await.unwrap();
    let started = Instant::now();
    let refusal = locks
        .acquire_queued(
            &ctx,
            &path,
            "acquire",
            Duration::from_millis(300),
            "lease_acquire_timeout",
            "no worker became free in time",
        )
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "lease_acquire_timeout");
    assert_eq!(refusal.exit, Exit::TEMP_FAIL);
    assert_eq!(refusal.message, "no worker became free in time");
    assert!(started.elapsed() >= Duration::from_millis(250), "gave up before its budget");
}

// A cancelled operation stops the wait rather than burning the whole budget, which is why the wait is a poll rather
// than a blocking `flock`.
#[tokio::test]
async fn a_cancelled_operation_ends_the_wait() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join("op.lock");
    let locks = manager();
    let _held = locks.acquire(&Ctx::background(), &path, "run", "busy").await.unwrap();
    let token = CancellationToken::new();
    let ctx = Ctx::new(token.clone());
    let cancel = tokio::spawn(async move {
        tokio::time::sleep(Duration::from_millis(100)).await;
        token.cancel();
    });
    let started = Instant::now();
    let refusal = locks
        .acquire_queued(&ctx, &path, "acquire", Duration::from_secs(30), "lease_acquire_timeout", "busy")
        .await
        .unwrap_err();
    cancel.await.unwrap();
    assert_eq!(refusal.code, "lease_acquire_timeout");
    assert!(started.elapsed() < Duration::from_secs(5));
}

// Several locks are taken in the caller's order and released in reverse, and a failure part-way leaves nothing
// held - otherwise a refused multi-worker command would strand every worker it had reached.
#[tokio::test]
async fn acquire_all_releases_the_partial_set_on_failure() {
    let ctx = Ctx::background();
    let directory = tempfile::tempdir().unwrap();
    let [first, second, third] = ["one.lock", "two.lock", "three.lock"].map(|name| directory.path().join(name));
    let paths = [first.clone(), second.clone(), third.clone()];

    let blocker = manager();
    let blocked = blocker.acquire(&ctx, &second, "run", "busy").await.unwrap();

    let refusal = manager().acquire_all(&ctx, &paths, "shard", "busy").await.unwrap_err();
    assert_eq!(refusal.code, "lease_busy");
    // The one it did take is free again, and the blocker still holds its own. Freedom is what is asserted rather
    // than absence, because a lock file is never unlinked.
    assert_eq!(record(&first), OwnerRecord::default());
    let owned = record(&second);
    assert_eq!((owned.pid, owned.operation.as_str()), (own_pid(), "run"));
    // The one past the failure was never reached, so it was never even created.
    assert!(!third.exists(), "a lock past the failure was taken");
    drop(blocked);

    // And the whole set succeeds once nothing is in the way.
    let taken = manager().acquire_all(&ctx, &paths, "shard", "busy").await.unwrap();
    for path in &paths {
        assert_eq!(record(path).operation, "shard", "{} is not held", path.display());
    }
    drop(taken);
    for path in &paths {
        assert_eq!(record(path), OwnerRecord::default(), "{} is still held", path.display());
    }
}
