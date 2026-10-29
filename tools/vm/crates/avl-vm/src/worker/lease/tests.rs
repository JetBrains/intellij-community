//! The lease policy's suite. Hermetic: no VM, no hypervisor call beyond the fake's version, no network.
//!
//! The subject is a race, so most of this suite is about what two callers can do to one worker at the same time, and
//! the concurrency tests use real threads and tasks against a real filesystem: the mutual exclusion here is a
//! no-clobber move and a `flock`, and neither of them can be modelled.

#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};

use avl_base::{Backend, Exit, GuestOs, Outcome, Refusal};
#[cfg(unix)]
use avl_host_sys::Captured;
use avl_host_sys::Ctx;
use avl_host_testkit::FakeProbe;
#[cfg(unix)]
use avl_host_testkit::{agent::active_reply, has};
use pretty_assertions::assert_eq;
use serde_json::Value;

use super::receipt::receipt_files;
use super::*;
use crate::worker::testing::Fixture;
#[cfg(unix)]
use crate::worker::testing::Notes;
#[cfg(unix)]
use crate::worker::worker::{IdleStopRequest, StopState, read_idle_stop_record};
#[cfg(unix)]
use avl_host_sys::interrupt::{Interrupts, Signal};

pub(super) fn ctx() -> Ctx {
    Ctx::background()
}

/// A Tart pool of the named workers, or of the default slots when `workers` is empty. `TART_BIN` points at the fake,
/// seeded with its version: `lease release` runs the version gate before it touches a worker.
///
/// A Windows host has the Docker backend only. There a Docker pool of the two Tart slot names stands in, and the
/// tests that need a Tart worker or a file mode are Unix only.
pub(super) fn pool(workers: &str) -> Fixture {
    #[cfg(unix)]
    let builder = Fixture::builder(GuestOs::Macos);
    #[cfg(windows)]
    let (builder, workers) = (
        Fixture::docker_builder(),
        if workers.is_empty() { "air-macos-1,air-macos-2" } else { workers },
    );
    if workers.is_empty() {
        builder.build()
    } else {
        builder.env("AIR_VM_WORKERS", workers).build()
    }
}

pub(super) fn request(holder: &str, count: u8, exact: bool) -> AcquireRequest {
    AcquireRequest::new(holder, count, exact).expect("a valid request")
}

pub(super) async fn acquire(fixture: &Fixture, holder: &str) -> Result<Outcome, Refusal> {
    command_lease_acquire(&ctx(), &fixture.manager, &request(holder, 1, false)).await
}

fn text(data: &Value, key: &str) -> String {
    data[key].as_str().unwrap_or_else(|| panic!("{key} in {data}")).to_owned()
}

fn lease_file(outcome: &Outcome) -> PathBuf {
    PathBuf::from(text(&outcome.data, "leaseFile"))
}

pub(super) async fn release(fixture: &Fixture, receipt: &Path, probe: &dyn ParkedDaemonProbe) -> Result<Outcome, Refusal> {
    command_lease(&ctx(), &fixture.manager, &LeaseCommand::Release, Some(receipt), probe).await
}

fn code(result: Result<impl std::fmt::Debug, Refusal>) -> String {
    result.expect_err("a refusal").code.into_owned()
}

// --- the receipt gate ------------------------------------------------------------------------------------------

/// A receipt is a capability, so every way of presenting one that proves nothing is refused. The mode and the owner
/// are checked on the open descriptor rather than on the path, so nothing can be swapped in between.
#[cfg(unix)]
#[test]
fn a_receipt_is_refused_unless_it_is_private_well_formed_and_ours() {
    let fixture = pool("");
    let settings = &fixture.settings;
    let worker = fixture.worker(0);
    let lease = fixture.new_lease(worker, "the-token", "holder");
    assert!(try_atomic_lease(settings, worker, &lease).unwrap());

    let good = write_lease_receipt(settings, &lease).unwrap();
    let (path, active) = receipt_for_path(settings, &good).unwrap();
    assert_eq!((path, active), (good.clone(), lease.clone()));
    // The receipt is the lease's own document with the marker last.
    let written = std::fs::read_to_string(&good).unwrap();
    assert!(
        written.ends_with(&format!("\"acquiredAt\":\"{}\",\"kind\":\"{RECEIPT_KIND}\"}}\n", lease.acquired_at)),
        "{written}"
    );

    let refusal = required_receipt(None).unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("lease_file_required", Exit::USAGE));
    let refusal = receipt_for_path(settings, &fixture.root().join("nothing-here.json")).unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("invalid_lease_file", Exit::NO_PERM));

    // A world-readable receipt is refused rather than accepted with a warning: a receipt anybody may read is a
    // worker anybody may take over.
    std::fs::set_permissions(&good, std::fs::Permissions::from_mode(0o644)).unwrap();
    assert_eq!(code(receipt_for_path(settings, &good)), "invalid_lease_file_mode");
    std::fs::set_permissions(&good, std::fs::Permissions::from_mode(0o600)).unwrap();

    // A symlink where the receipt should be is refused rather than followed.
    let link = fixture.root().join("pointer.json");
    std::os::unix::fs::symlink(&good, &link).unwrap();
    assert_eq!(code(receipt_for_path(settings, &link)), "invalid_lease_file");

    let candidate = fixture.root().join("candidate.json");
    for (name, content) in [
        (
            "not a receipt at all",
            format!(
                r#"{{"schemaVersion":1,"backend":"tart","guestOs":"linux","kind":"something-else","worker":"{worker}","token":"the-token","holder":"h","acquiredAt":"a"}}"#
            ),
        ),
        (
            "a schema this build does not speak",
            format!(
                r#"{{"schemaVersion":99,"backend":"tart","guestOs":"linux","kind":"{RECEIPT_KIND}","worker":"{worker}","token":"the-token","holder":"h","acquiredAt":"a"}}"#
            ),
        ),
        (
            "no token",
            format!(
                r#"{{"schemaVersion":1,"backend":"tart","guestOs":"linux","kind":"{RECEIPT_KIND}","worker":"{worker}","holder":"h","acquiredAt":"a"}}"#
            ),
        ),
        ("not JSON", "{".to_owned()),
    ] {
        std::fs::write(&candidate, content).unwrap();
        std::fs::set_permissions(&candidate, std::fs::Permissions::from_mode(0o600)).unwrap();
        assert_eq!(code(receipt_for_path(settings, &candidate)), "invalid_lease_file", "{name}");
    }

    // A receipt selecting another backend is its own refusal, because the caller's repair is to invoke the controller
    // for that pool rather than to find a different receipt.
    let other = LeaseReceipt {
        lease: Lease {
            backend: Backend::Parallels,
            ..lease
        },
        kind: RECEIPT_KIND.to_owned(),
    };
    let parallels = fixture.root().join("parallels.json");
    std::fs::write(&parallels, serde_json::to_vec(&other).unwrap()).unwrap();
    std::fs::set_permissions(&parallels, std::fs::Permissions::from_mode(0o600)).unwrap();
    assert_eq!(code(receipt_for_path(settings, &parallels)), "lease_backend_mismatch");

    // And a receipt whose lease has since been released owns nothing, which is what makes a stale handle refuse
    // instead of authorizing.
    std::fs::remove_file(settings.lease_path(worker)).unwrap();
    assert_eq!(code(receipt_for_path(settings, &good)), "invalid_lease_file");
}

// --- atomicity -------------------------------------------------------------------------------------------------

/// The failure this whole module exists to prevent: two holders of one worker. Many callers race for one free slot and
/// exactly one may win, because the placement is a no-clobber move onto a name that must not exist and not a check
/// followed by a write.
#[test]
fn only_one_of_many_concurrent_placements_wins_one_worker() {
    let fixture = pool("");
    let worker = fixture.worker(0);
    let outcomes: Vec<Result<bool, Refusal>> = std::thread::scope(|scope| {
        #[expect(
            clippy::needless_collect,
            reason = "every contender is spawned before the first join, or the race runs one lease at a time"
        )]
        let contenders: Vec<_> = (0..16)
            .map(|index| {
                let lease = fixture.new_lease(worker, &format!("token-{index}"), "holder");
                let settings = &fixture.settings;
                scope.spawn(move || try_atomic_lease(settings, worker, &lease))
            })
            .collect();
        contenders.into_iter().map(|contender| contender.join().unwrap()).collect()
    });
    let winners = outcomes
        .into_iter()
        .map(|outcome| outcome.expect("a contender loses rather than failing"))
        .filter(|won| *won)
        .count();
    assert_eq!(winners, 1);
    // The losers' staging files must not survive: the worker directory is also where the lease itself lives.
    for entry in std::fs::read_dir(fixture.settings.worker_dir(worker)).unwrap() {
        let name = entry.unwrap().file_name();
        assert!(!name.to_string_lossy().ends_with(".tmp"), "{name:?}");
    }
    assert!(read_lease(&fixture.settings.lease_path(worker)).unwrap().is_some());
}

/// Two acquisitions running at once take two *different* workers. The pool-wide lock is what makes that true: without
/// it both would survey the same free pool and place a lease on its first slot.
#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn two_concurrent_acquisitions_take_different_workers() {
    let fixture = pool("air-macos-1,air-macos-2");
    let (first, second) = tokio::join!(acquire(&fixture, "agent-a"), acquire(&fixture, "agent-b"));
    let (first, second) = (first.unwrap(), second.unwrap());
    assert_ne!(text(&first.data, "worker"), text(&second.data, "worker"));
    for outcome in [first, second] {
        assert_eq!(outcome.data["recovered"], false);
    }
}

// --- counts and rollback ---------------------------------------------------------------------------------------

/// The count is a ceiling: a shardable lane spreads its trials over whatever the pool can give it, and a run that
/// silently waited for a busy worker would be worse than a narrower one. Both counts are always reported, and the
/// sharded reply is its own shape.
#[tokio::test]
async fn count_is_a_ceiling_and_exact_refuses_short_without_leaving_leases() {
    let fixture = pool("air-macos-1,air-macos-2");
    let outcome = command_lease_acquire(&ctx(), &fixture.manager, &request("shardable", 4, false))
        .await
        .unwrap();
    assert_eq!(
        (&outcome.data["requested"], &outcome.data["acquired"]),
        (&Value::from(4), &Value::from(2))
    );
    assert!(outcome.data.get("worker").is_none(), "{}", outcome.data);
    for item in outcome.data["leases"].as_array().unwrap() {
        assert!(text(item, "holder").starts_with("shardable#"), "{item}");
    }
    assert!(outcome.text.starts_with("requested=4\nacquired=2\n"), "{}", outcome.text);

    // `--exact` refuses a short set - and unwinds every lease it placed on the way, because a worker owned by nobody is
    // worse than an acquisition that failed.
    let fixture = pool("air-macos-1,air-macos-2");
    let refusal = command_lease_acquire(&ctx(), &fixture.manager, &request("measurement", 3, true))
        .await
        .unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("pool_exhausted", Exit::TEMP_FAIL));
    for worker in &fixture.settings.workers {
        assert_eq!(read_lease(&fixture.settings.lease_path(worker)).unwrap(), None);
    }
    assert_eq!(receipt_files(&fixture.settings), Vec::<PathBuf>::new());
}

/// A full pool is `pool_exhausted` rather than a wait, because the caller is better placed to decide whether to retry
/// than this command is to hold a lock while it does.
#[tokio::test]
async fn a_full_pool_is_exhausted_rather_than_waited_on() {
    let fixture = pool("air-macos-1");
    acquire(&fixture, "first").await.unwrap();
    assert_eq!(code(acquire(&fixture, "second").await), "pool_exhausted");
}

// --- recovery --------------------------------------------------------------------------------------------------

/// An acquisition is idempotent through its own receipt: a retried command finds the lease it already holds and is
/// handed the *same* handle rather than a second one.
#[tokio::test]
async fn an_acquisition_is_idempotent_through_its_original_receipt() {
    let fixture = pool("air-macos-1,air-macos-2");
    let first = acquire(&fixture, "agent").await.unwrap();
    let second = acquire(&fixture, "agent").await.unwrap();
    assert_eq!(text(&first.data, "worker"), text(&second.data, "worker"));
    assert_eq!(lease_file(&first), lease_file(&second));
    assert_eq!(first.data["recovered"], false);
    assert_eq!(second.data["recovered"], true);
    assert!(second.text.contains("\nrecovered=true\n"), "{}", second.text);
    // The reply names why the slot was chosen: the first one by its state, the second one by the recovery.
    assert_eq!(first.data["slotReason"], "absent");
    assert_eq!(second.data["slotReason"], "recovered");
    assert!(second.text.ends_with("slot_reason=recovered"), "{}", second.text);
    assert_eq!(receipt_files(&fixture.settings).len(), 1);
}

/// Without the original receipt there is nothing proving the caller is the holder, so recovery refuses rather than
/// handing a live lease to whoever asks for it under the right name.
#[tokio::test]
async fn recovery_without_the_original_receipt_refuses() {
    let fixture = pool("air-macos-1");
    let outcome = acquire(&fixture, "agent").await.unwrap();
    std::fs::remove_file(lease_file(&outcome)).unwrap();
    assert_eq!(code(acquire(&fixture, "agent").await), "lease_recovery_receipt_missing");
}

/// A recovered lease of the other guest refuses the whole acquisition. Handing both guests to one sharded run would
/// make each shard configure Bazel differently, and the two configurations evict each other's analysis cache.
#[tokio::test]
async fn an_acquisition_spanning_guest_oses_is_refused() {
    let fixture = pool("air-macos-1");
    let worker = fixture.worker(0);
    let lease = Lease {
        guest_os: GuestOs::Linux,
        ..fixture.new_lease(worker, "token", "agent")
    };
    assert!(try_atomic_lease(&fixture.settings, worker, &lease).unwrap());
    write_lease_receipt(&fixture.settings, &lease).unwrap();
    assert_eq!(code(acquire(&fixture, "agent").await), "lease_guest_os_mismatch");
}

// --- the request -----------------------------------------------------------------------------------------------

/// Every request that exists passed these rules; the argv spellings (`--holder=agent`, a missing value) are the
/// command line's to parse.
#[test]
fn acquire_refuses_an_invocation_it_cannot_honour() {
    let refused = |holder: &str, count: u8| AcquireRequest::new(holder, count, false).unwrap_err().code.into_owned();
    assert_eq!(refused("", 1), "usage");
    assert_eq!(refused("agent", 0), "invalid_count");
    assert_eq!(refused("agent", 100), "invalid_count");
    // `#` separates a holder from its shard index, so a holder carrying one could collide with another caller's shard -
    // and that collision surfaces as a missing receipt, which is a confusing way to learn about a naming clash.
    assert_eq!(refused("agent#1", 1), "invalid_holder");
    assert_eq!(refused("tab\there", 1), "invalid_holder");
    assert_eq!(refused("naïve", 1), "invalid_holder");
    let longest = "h".repeat(MAX_HOLDER_LENGTH);
    assert_eq!(refused(&format!("{longest}h"), 1), "invalid_holder");
    AcquireRequest::new(longest.clone(), 1, false).unwrap();
    // A shard suffix is part of the bound.
    assert_eq!(refused(&longest, 2), "invalid_holder");
    AcquireRequest::new(&longest[3..], 99, true).unwrap();

    for count in ["0", "100", "1.0", "+1", " 1", "", "01"] {
        let refusal = parse_count(count).unwrap_err();
        assert_eq!((refusal.code.as_ref(), refusal.exit), ("invalid_count", Exit::USAGE), "{count:?}");
    }
    assert_eq!(parse_count("1").unwrap(), 1);
    assert_eq!(parse_count("99").unwrap(), 99);
}

// --- show ------------------------------------------------------------------------------------------------------

/// `lease show` tells everyone which workers are busy and tells only the holder who is holding one. The occupancy is
/// not private; the holder's identity is.
#[tokio::test]
async fn show_names_the_holder_only_of_the_callers_own_lease() {
    let fixture = pool("air-macos-1,air-macos-2");
    let held = acquire(&fixture, "the-holder").await.unwrap();
    let worker = text(&held.data, "worker");

    let anonymous = command_lease(&ctx(), &fixture.manager, &LeaseCommand::Show, None, &FakeProbe::default())
        .await
        .unwrap();
    assert!(!anonymous.text.contains("the-holder"), "{}", anonymous.text);
    assert!(anonymous.text.contains(&format!("{worker}: leased")), "{}", anonymous.text);
    assert!(!anonymous.data.to_string().contains("the-holder"), "{}", anonymous.data);
    let authorized = command_lease(
        &ctx(),
        &fixture.manager,
        &LeaseCommand::Show,
        Some(&lease_file(&held)),
        &FakeProbe::default(),
    )
    .await
    .unwrap();
    assert!(authorized.text.contains("holder=the-holder"), "{}", authorized.text);
    let row = authorized.data["workers"]
        .as_array()
        .unwrap()
        .iter()
        .find(|row| row["worker"] == worker.as_str())
        .unwrap();
    assert_eq!(row["holder"], "the-holder");
    assert_eq!(authorized.data["backend"], serde_json::to_value(fixture.settings.backend).unwrap());
}

// --- release ---------------------------------------------------------------------------------------------------

/// A stopped Tart worker is released without being started. Requiring a start here is how a lease outlived its holder
/// on a worker that had crashed, leaving a slot that could be neither used nor freed.
#[cfg(unix)]
#[tokio::test]
async fn releasing_a_stopped_tart_worker_does_not_start_it() {
    let fixture = pool("air-macos-1");
    let held = acquire(&fixture, "agent").await.unwrap();
    let released = release(&fixture, &lease_file(&held), &FakeProbe::default()).await.unwrap();
    assert_eq!(
        released.data,
        serde_json::json!({ "worker": "air-macos-1", "released": true, "workerWasStopped": true })
    );
    assert_eq!(read_lease(&fixture.settings.lease_path("air-macos-1")).unwrap(), None);
    assert_eq!(receipt_files(&fixture.settings), Vec::<PathBuf>::new());
    assert!(!fixture.fake.saw_call_containing("run"));
    // The receipt no longer owns anything, so a second release refuses rather than repeating itself.
    assert_eq!(
        code(release(&fixture, &lease_file(&held), &FakeProbe::default()).await),
        "invalid_lease_file"
    );
}

/// Releases the lease of `held` while the container of air-docker-1 has exited, and asserts that the release names
/// the stopped worker.
async fn release_stopped_container(fixture: &Fixture, held: &Outcome) {
    fixture.fake.answer(avl_testkit::tartfake::Answer::ContainerState, "exited/1\n");
    let released = release(fixture, &lease_file(held), &FakeProbe::default()).await.unwrap();
    assert_eq!(
        released.data,
        serde_json::json!({ "worker": "air-docker-1", "released": true, "workerWasStopped": true })
    );
}

/// The Docker twin: a container that does not run is released without a start and without a guest call, for the
/// Tart reason. A container that crashed must not keep its slot held.
#[tokio::test]
async fn releasing_a_stopped_docker_worker_does_not_start_it() {
    let fixture = Fixture::docker();
    let worker = "air-docker-1";
    let held = acquire(&fixture, "agent").await.unwrap();
    release_stopped_container(&fixture, &held).await;
    assert_eq!(read_lease(&fixture.settings.lease_path(worker)).unwrap(), None);
    assert_eq!(receipt_files(&fixture.settings), Vec::<PathBuf>::new());
    let calls = fixture.fake.calls();
    assert!(
        !calls.iter().any(|call| call.starts_with("start ") || call.starts_with("exec ")),
        "{calls:?}"
    );
    assert_eq!(fixture.guest.lines(), Vec::<String>::new());
}

/// A standalone `lease release` is the first command of its process, and the acquisition resolved no CLI. The release
/// reaches the engine itself, so its `docker inspect` spawns the pinned CLI instead of refusing `hypervisor_unresolved`.
#[cfg(unix)]
#[tokio::test]
async fn a_standalone_docker_release_resolves_the_pinned_cli() {
    let fixture = Fixture::docker_builder().pinned_docker().build();
    let worker = "air-docker-1";
    let held = acquire(&fixture, "agent").await.unwrap();
    let docker = fixture.manager.machine().docker().unwrap();
    assert_eq!(docker.program(), "", "the acquisition resolves no CLI");
    release_stopped_container(&fixture, &held).await;
    assert_ne!(docker.program(), "");
    assert!(fixture.fake.saw_call_containing("inspect --type container --format"));
    assert_eq!(read_lease(&fixture.settings.lease_path(worker)).unwrap(), None);
}

/// A container pool whose one worker runs and is leased, with `AIR_VM_IDLE_STOP` at `seconds`.
#[cfg(unix)]
async fn leased_container_worker(seconds: &str) -> (Fixture, Outcome) {
    let fixture = Fixture::docker_builder()
        .container_engine()
        .env("AIR_VM_IDLE_STOP", seconds)
        .build();
    let worker = fixture.worker(0).to_owned();
    fixture.manager.start_without_lifecycle_lock(&ctx(), &worker).await.unwrap();
    let held = acquire(&fixture, "agent").await.unwrap();
    // The run slot is empty: no daemon and no iteration.
    fixture.guest.answer(|argv, _| {
        Ok(if has(argv, "active") {
            active_reply(None)
        } else {
            Captured::default()
        })
    });
    fixture.fake.forget_calls();
    (fixture, held)
}

/// On the Apple `container` engine a release writes the idle stop record and asks for its detached process, with the
/// nonce of the record. The reply names the deadline. The warm daemon stays, so nothing stops the container yet.
#[cfg(unix)]
#[tokio::test]
async fn a_container_release_schedules_the_idle_stop() {
    let (fixture, held) = leased_container_worker("60").await;
    let worker = fixture.worker(0);
    let released = release(&fixture, &lease_file(&held), &FakeProbe::default()).await.unwrap();
    let record = read_idle_stop_record(&fixture.settings, worker).expect("the idle stop record");
    assert_eq!(released.data["idleStopAt"], Value::from(record.deadline.clone()), "{released:?}");
    assert_eq!(released.text, format!("released={worker}\nidle_stop_at={}", record.deadline));
    let requests = avl_base::sync::lock(&fixture.idle_stops).clone();
    assert_eq!(
        requests,
        [IdleStopRequest {
            worker: worker.to_owned(),
            nonce: record.nonce,
        }]
    );
    assert!(
        !fixture.fake.calls().iter().any(|call| call.starts_with("stop ")),
        "{:#?}",
        fixture.fake.calls()
    );
}

/// `AIR_VM_IDLE_STOP=off` writes no record and starts no process, and `0` stops the worker inside the release.
#[cfg(unix)]
#[tokio::test]
async fn a_container_release_without_a_grace_writes_no_record() {
    let (fixture, held) = leased_container_worker("off").await;
    let worker = fixture.worker(0);
    let released = release(&fixture, &lease_file(&held), &FakeProbe::default()).await.unwrap();
    assert_eq!(released.data, serde_json::json!({ "worker": worker, "released": true }));
    assert_eq!(read_idle_stop_record(&fixture.settings, worker), None);
    assert!(avl_base::sync::lock(&fixture.idle_stops).is_empty());

    let (fixture, held) = leased_container_worker("0").await;
    let worker = fixture.worker(0);
    let released = release(&fixture, &lease_file(&held), &FakeProbe::default()).await.unwrap();
    assert!(released.data["idleStopAt"].is_string(), "{released:?}");
    assert_eq!(read_idle_stop_record(&fixture.settings, worker), None);
    assert!(avl_base::sync::lock(&fixture.idle_stops).is_empty());
    let calls = fixture.fake.calls();
    assert!(calls.contains(&format!("stop -t 10 {worker}")), "{calls:#?}");
}

/// An acquisition removes the record of the last release, so the waiting process finds no record and the new holder
/// keeps a running worker.
#[cfg(unix)]
#[tokio::test]
async fn an_acquisition_removes_the_idle_stop_record() {
    let (fixture, held) = leased_container_worker("60").await;
    let worker = fixture.worker(0);
    release(&fixture, &lease_file(&held), &FakeProbe::default()).await.unwrap();
    assert!(read_idle_stop_record(&fixture.settings, worker).is_some());
    acquire(&fixture, "agent-2").await.unwrap();
    assert_eq!(read_idle_stop_record(&fixture.settings, worker), None);
}

/// A CLI that no `DOCKER_BIN` names and no Bazel resolves is still `docker_missing`, which a caller retries against.
/// The release refuses before it spawns anything, and the lease stays.
#[cfg(unix)]
#[tokio::test]
async fn a_docker_release_without_a_cli_is_docker_missing() {
    let fixture = Fixture::docker_builder().unresolvable_docker().build();
    let worker = "air-docker-1";
    let held = acquire(&fixture, "agent").await.unwrap();
    let refusal = release(&fixture, &lease_file(&held), &FakeProbe::default()).await.unwrap_err();
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("docker_missing", Exit::UNAVAILABLE),
        "{refusal:?}"
    );
    assert!(fixture.fake.calls().is_empty(), "{:?}", fixture.fake.calls());
    assert!(read_lease(&fixture.settings.lease_path(worker)).unwrap().is_some());
    assert!(lease_file(&held).exists());
}

/// A release that cannot be completed leaves the lease in place. The worker stays held rather than becoming free while
/// a run may still be using it, which is the whole reason release is a gate and not a delete.
#[cfg(unix)]
#[tokio::test]
async fn a_refused_release_leaves_the_lease_in_place() {
    let fixture = pool("air-macos-1");
    let held = acquire(&fixture, "agent").await.unwrap();
    // A running worker, so the release takes the guest path - and a guest that does not answer, so the gate refuses
    // before anything is unlinked.
    fixture.run_as_fake_process("air-macos-1").await;
    fixture.guest.fail_everything();
    assert_eq!(
        code(release(&fixture, &lease_file(&held), &FakeProbe::default()).await),
        "guest_agent_unavailable"
    );
    assert!(read_lease(&fixture.settings.lease_path("air-macos-1")).unwrap().is_some());
    assert!(lease_file(&held).exists());
}

/// A live `tart run` whose VM Tart does not list holds no guest, so its lease is released without the guest check
/// that could only refuse `guest_agent_unavailable`. The data names the stale process for `pool recycle`.
#[cfg(unix)]
#[tokio::test]
async fn a_release_of_a_worker_whose_vm_is_missing_skips_the_guest() {
    let fixture = pool("air-macos-1");
    let held = acquire(&fixture, "agent").await.unwrap();
    // The quiet listing stays unseeded: Tart lists no `air-macos-1`.
    let stale = fixture.run_stale_tart_process("air-macos-1").await;
    fixture.guest.fail_everything();
    let outcome = release(&fixture, &lease_file(&held), &FakeProbe::default()).await.unwrap();
    assert_eq!(outcome.text, "released=air-macos-1");
    assert_eq!(outcome.data["workerVmMissing"], Value::Bool(true));
    assert_eq!(outcome.data["staleRunProcess"], Value::from(stale.identity.process.pid));
    assert!(read_lease(&fixture.settings.lease_path("air-macos-1")).unwrap().is_none());
    assert!(!lease_file(&held).exists());
    assert!(fixture.guest.lines().is_empty(), "{:?}", fixture.guest.lines());
    // The release ends nothing: the stale process is `pool recycle`'s.
    assert!(
        fixture
            .manager
            .tart()
            .unwrap()
            .process_alive(&ctx(), Some(&stale.identity))
            .await
            .unwrap()
    );
}

/// A VM Tart does list keeps the guest path: the shortcut is for a missing VM only.
#[cfg(unix)]
#[tokio::test]
async fn a_release_of_a_listed_worker_still_asks_the_guest() {
    let fixture = pool("air-macos-1");
    let held = acquire(&fixture, "agent").await.unwrap();
    fixture.fake.answer(avl_testkit::tartfake::Answer::ListQuiet, "air-macos-1\n");
    let _stale = fixture.run_stale_tart_process("air-macos-1").await;
    fixture.guest.fail_everything();
    assert_eq!(
        code(release(&fixture, &lease_file(&held), &FakeProbe::default()).await),
        "guest_agent_unavailable"
    );
    assert!(lease_file(&held).exists());
}

/// Every guest command exits 0, the console is logged in, and the supervisor's `active` verb answers `run_id` in the
/// slot.
#[cfg(unix)]
fn live_guest(fixture: &Fixture, run_id: &'static str) {
    fixture.guest.answer(move |argv, _| {
        Ok(if has(argv, "active") {
            active_reply(Some(run_id))
        } else if has(argv, "/usr/bin/who") {
            avl_host_testkit::said(crate::worker::testing::CONSOLE_LOGIN)
        } else {
            Captured::default()
        })
    });
}

/// A Tart pool of one running worker that the holder `agent` leased, and whose guest supervisor names the run
/// `run-ui-daemon-1` in its slot. Answers the fixture and the outcome of the acquisition.
#[cfg(unix)]
async fn held_with_a_warm_daemon() -> (Fixture, Outcome) {
    let fixture = pool("air-macos-1");
    let held = acquire(&fixture, "agent").await.unwrap();
    fixture.run_as_fake_process("air-macos-1").await;
    live_guest(&fixture, "run-ui-daemon-1");
    (fixture, held)
}

/// The contract a warm lane depends on: the daemon holds the supervisor's run slot for its whole life, so a release
/// that consulted the slot alone could never free a worker with a warm daemon on it. A slot held by this controller's
/// own idle daemon is not an execution, and the worker goes back.
#[cfg(unix)]
#[tokio::test]
async fn a_release_succeeds_while_a_warm_daemon_holds_the_slot() {
    let (fixture, held) = held_with_a_warm_daemon().await;
    let probe = FakeProbe::new(Some("run-ui-daemon-1"), Some("run-ui-daemon-1"));
    let released = release(&fixture, &lease_file(&held), &probe).await.unwrap();
    assert_eq!(released.text, "released=air-macos-1");
    assert_eq!(released.data["released"], true);
    assert!(released.data.get("workerWasStopped").is_none());
    assert_eq!(read_lease(&fixture.settings.lease_path("air-macos-1")).unwrap(), None);
    assert_eq!(receipt_files(&fixture.settings), Vec::<PathBuf>::new());
    // The next holder gets no secret file of this one's runs.
    let removal = format!("/bin/rm -rf -- {}", fixture.settings.vm_run_secrets);
    assert!(
        fixture.guest.lines().iter().any(|line| line.contains(&removal)),
        "{:#?}",
        fixture.guest.lines()
    );
    // The warm daemon stays, and its lane IDEs are stopped.
    let gc = format!("vm-guest-agent ide-gc --root {}/ide --stop-all", fixture.settings.vm_data);
    assert!(
        fixture.guest.lines().iter().any(|line| line.contains(&gc)),
        "{:#?}",
        fixture.guest.lines()
    );
}

/// What the slot still exists to catch. The run marker excludes every run of a live process. The residue is a run of
/// a controller that crashed and left it behind, and the probe answers nothing for it.
#[cfg(unix)]
#[tokio::test]
async fn a_release_is_refused_while_an_iteration_is_in_flight() {
    let (fixture, held) = held_with_a_warm_daemon().await;
    let probe = FakeProbe::new(None, Some("run-ui-daemon-1"));
    let refusal = release(&fixture, &lease_file(&held), &probe).await.unwrap_err();
    assert_eq!(refusal.code, "run_active", "{refusal:?}");
    assert!(refusal.message.contains("run-ui-daemon-1"), "{refusal}");
    assert!(read_lease(&fixture.settings.lease_path("air-macos-1")).unwrap().is_some());
    assert_eq!(receipt_files(&fixture.settings).len(), 1);
}

// --- receipt housekeeping --------------------------------------------------------------------------------------

/// The sweep is token-checked, so it removes the handles to one lease and nothing else - including nothing belonging
/// to another lease, and nothing the user happens to keep in that directory.
#[test]
fn removing_receipts_takes_only_the_ones_for_that_lease() {
    let fixture = pool("air-macos-1,air-macos-2");
    let mine = fixture.new_lease(fixture.worker(0), "my-token", "me");
    let yours = fixture.new_lease(fixture.worker(1), "your-token", "you");
    let same_worker = fixture.new_lease(fixture.worker(0), "an-older-token", "me");
    let mine_path = write_lease_receipt(&fixture.settings, &mine).unwrap();
    let yours_path = write_lease_receipt(&fixture.settings, &yours).unwrap();
    let older_path = write_lease_receipt(&fixture.settings, &same_worker).unwrap();
    let foreign = receipt::receipt_directory(&fixture.settings).join("lease-notes.json");
    std::fs::write(&foreign, "not a receipt").unwrap();

    remove_lease_receipts(&fixture.settings, &mine);
    assert!(!mine_path.exists());
    assert!(yours_path.exists() && older_path.exists() && foreign.exists());
}

/// A receipt is written at mode 0600 whatever the umask is: a creation mode is masked, so without the explicit mode a
/// caller under an odd umask would acquire a lease and immediately be unable to use it.
///
/// The umask is process-wide and this suite's tests run on parallel threads, so the test re-runs itself alone in a
/// child process and changes the umask only there.
#[test]
#[cfg(unix)]
fn a_receipt_is_private_whatever_the_umask_is() {
    const CHILD: &str = "AVL_WORKER_UMASK_CHILD";
    if std::env::var_os(CHILD).is_none() {
        let child = std::process::Command::new(std::env::current_exe().unwrap())
            .args([
                "worker::lease::tests::a_receipt_is_private_whatever_the_umask_is",
                "--exact",
                "--test-threads=1",
            ])
            .env(CHILD, "1")
            .output()
            .unwrap();
        let said = String::from_utf8_lossy(&child.stdout);
        assert!(child.status.success(), "the isolated run failed: {said}");
        // A filter that matched nothing would pass too; the child must have run this very test.
        assert!(said.contains("1 passed"), "{said}");
        return;
    }
    let fixture = pool("");
    let lease = fixture.new_lease(fixture.worker(0), "token", "holder");
    for mask in [0o000, 0o077, 0o277] {
        let previous = nix::sys::stat::umask(nix::sys::stat::Mode::from_bits_truncate(mask));
        let written = write_lease_receipt(&fixture.settings, &lease);
        nix::sys::stat::umask(previous);
        let mode = std::fs::metadata(written.unwrap()).unwrap().permissions().mode();
        assert_eq!(mode & 0o7777, 0o600, "umask {mask:03o}");
    }
}

// --- the backend a receipt selects -----------------------------------------------------------------------------

/// This runs before a config exists - it is what lets `--lease-file` decide the pool without the caller repeating
/// `--backend` - so it reads the marker and the two axes and validates nothing else. A receipt missing an axis, or
/// naming one this build does not know, selects nothing.
#[test]
fn a_receipt_selects_the_pool_it_names() {
    let directory = tempfile::tempdir().unwrap();
    let write = |name: &str, content: &str| {
        let path = directory.path().join(name);
        std::fs::write(&path, content).unwrap();
        path
    };
    assert_eq!(receipt_backend(Path::new("")), None);
    assert_eq!(receipt_backend(&directory.path().join("absent.json")), None);
    assert_eq!(receipt_backend(&write("other.json", r#"{"kind":"something-else"}"#)), None);
    assert_eq!(receipt_backend(&write("junk.json", "{")), None);

    let no_axes = write("no-axes.json", &format!(r#"{{"kind":"{RECEIPT_KIND}","worker":"w"}}"#));
    assert_eq!(receipt_backend(&no_axes), None);
    let current = write(
        "current.json",
        &format!(r#"{{"kind":"{RECEIPT_KIND}","backend":"parallels","guestOs":"linux","worker":"w"}}"#),
    );
    assert_eq!(
        receipt_backend(&current),
        Some(avl_base::Selection {
            backend: Backend::Parallels,
            guest_os: GuestOs::Linux
        })
    );
    let unknown = write(
        "unknown.json",
        &format!(r#"{{"kind":"{RECEIPT_KIND}","backend":"vmware","guestOs":"linux"}}"#),
    );
    assert_eq!(receipt_backend(&unknown), None);
}

// --- the lease-operation window --------------------------------------------------------------------------------

/// A receipt is validated before the worker's lock is taken, so the lease can change in between - and an operation
/// that ran anyway would act on another holder's worker with a handle that looked valid when it was checked.
#[tokio::test]
async fn a_lease_that_changed_under_the_operation_refuses() {
    let fixture = pool("air-macos-1");
    let worker = fixture.worker(0);
    let lease = fixture.new_lease(worker, "the-original", "holder");
    assert!(try_atomic_lease(&fixture.settings, worker, &lease).unwrap());
    // Released and taken by somebody else between the receipt's validation and the operation.
    std::fs::remove_file(fixture.settings.lease_path(worker)).unwrap();
    let successor = fixture.new_lease(worker, "somebody-elses", "another-holder");
    assert!(try_atomic_lease(&fixture.settings, worker, &successor).unwrap());

    let refusal = with_lease(
        &ctx(),
        &fixture.manager,
        &lease,
        "suite",
        LeaseUse::LockedAction,
        async |_| -> Result<(), Refusal> { panic!("the operation must not run against a lease that changed") },
    )
    .await
    .unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("lease_changed", Exit::NO_PERM));

    let ran = with_lease(
        &ctx(),
        &fixture.manager,
        &successor,
        "suite",
        LeaseUse::LockedAction,
        async |current| Ok(current),
    )
    .await
    .unwrap();
    assert_eq!(ran, successor);
}

// --- the run marker ----------------------------------------------------------------------------------------------

/// A Tart pool of one worker, the lease of an acquisition and its receipt.
#[cfg(unix)]
async fn leased_pool() -> (Fixture, Lease, PathBuf) {
    let fixture = pool("air-macos-1");
    let held = acquire(&fixture, "agent").await.unwrap();
    let receipt = lease_file(&held);
    let (_, lease) = receipt_for_path(&fixture.settings, &receipt).unwrap();
    (fixture, lease, receipt)
}

/// A run holds the lifecycle lock only at its two ends. In its body the lock is free and the marker names the run, and
/// the end removes the marker.
#[cfg(unix)]
#[tokio::test]
async fn a_run_holds_the_lock_only_at_its_ends() {
    let (fixture, lease, _) = leased_pool().await;
    let worker = lease.worker.clone();
    let lock = fixture.manager.lifecycle_lock_path(&worker);
    let answer = with_run_marker(&ctx(), &fixture.manager, &lease, "suite-user-run-1", "run", async |current| {
        let taken = fixture.manager.locks().try_acquire(&ctx(), &lock, "suite").await?;
        assert!(taken.is_some(), "the run holds the lifecycle lock in its body");
        drop(taken);
        let marker = read_run_marker(&fixture.settings, &current.worker)?.expect("the run marks its worker");
        assert_eq!((marker.run_id.as_str(), marker.operation.as_str()), ("suite-user-run-1", "run"));
        assert!(marker.names_lease(&current));
        Ok(current.worker)
    })
    .await
    .unwrap();
    assert_eq!(answer, worker);
    assert_eq!(
        read_run_marker(&fixture.settings, &worker).unwrap(),
        None,
        "the end of the run removes its marker"
    );
}

/// A release while a run of another live process is in flight refuses `lease_busy` and names the run. The lease and the
/// receipt stay, and the release passes once the run ended.
#[cfg(unix)]
#[tokio::test]
async fn a_release_during_a_run_of_another_process_refuses() {
    let (fixture, lease, receipt) = leased_pool().await;
    let another = fixture.another_controller();
    let refusal = with_run_marker(&ctx(), &another, &lease, "suite-user-run-1", "run", async |_| {
        Ok(release(&fixture, &receipt, &FakeProbe::default()).await.unwrap_err())
    })
    .await
    .unwrap();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("lease_busy", Exit::TEMP_FAIL));
    assert!(refusal.message.contains("has run suite-user-run-1 in flight"), "{refusal}");
    assert!(read_lease(&fixture.settings.lease_path(&lease.worker)).unwrap().is_some());
    assert!(receipt.exists());
    release(&fixture, &receipt, &FakeProbe::default()).await.unwrap();
}

/// `lease show` with the receipt answers during a run and names the run. Without the receipt, and after the run, it
/// names no run.
#[cfg(unix)]
#[tokio::test]
async fn lease_show_names_the_run_in_flight_on_the_callers_lease() {
    let (fixture, lease, receipt) = leased_pool().await;
    let show = async |lease_file: Option<&Path>| {
        command_lease(&ctx(), &fixture.manager, &LeaseCommand::Show, lease_file, &FakeProbe::default())
            .await
            .unwrap()
    };
    let another = fixture.another_controller();
    let (authorized, anonymous) = with_run_marker(&ctx(), &another, &lease, "suite-user-run-1", "run", async |_| {
        Ok((show(Some(&receipt)).await, show(None).await))
    })
    .await
    .unwrap();
    assert!(authorized.text.ends_with(" run=suite-user-run-1"), "{}", authorized.text);
    assert_eq!(authorized.data["workers"][0]["run"], "suite-user-run-1");
    assert!(!anonymous.text.contains("run="), "{}", anonymous.text);
    assert!(anonymous.data["workers"][0].get("run").is_none(), "{}", anonymous.data);
    let after = show(Some(&receipt)).await;
    assert!(!after.text.contains("run="), "{}", after.text);
    assert!(after.data["workers"][0].get("run").is_none(), "{}", after.data);
}

/// A release removes the marker of a controller that died, with a note. The guest check of the release still decides
/// about an iteration that the dead controller left: it refuses while the iteration is in flight.
#[cfg(unix)]
#[tokio::test]
async fn a_release_removes_the_marker_of_a_dead_run_and_still_asks_the_guest() {
    let (fixture, lease, receipt) = leased_pool().await;
    fixture.run_as_fake_process(&lease.worker).await;
    live_guest(&fixture, "run-ui-daemon-1");
    let notes = Notes::start(fixture.manager.reporter());
    fixture.write_run_marker(&lease, "suite-user-run-1", Fixture::gone_process());

    let refusal = release(&fixture, &receipt, &FakeProbe::new(None, Some("run-ui-daemon-1")))
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "run_active", "{refusal:?}");
    assert!(!fixture.settings.run_marker_path(&lease.worker).exists(), "the stale marker stayed");
    assert!(
        notes
            .all()
            .contains(&"removed the run marker of suite-user-run-1 on air-macos-1: its process 2147483632 is gone".to_owned()),
        "{:?}",
        notes.all()
    );
    // Once the daemon's iteration ends, the release passes.
    let probe = FakeProbe::new(Some("run-ui-daemon-1"), Some("run-ui-daemon-1"));
    release(&fixture, &receipt, &probe).await.unwrap();
}

/// The end of a run never changes its answer. A peer that holds the lock for the whole wait leaves the marker on the
/// worker, and a note says that the marker stays.
#[cfg(unix)]
#[tokio::test]
async fn the_end_of_a_run_keeps_its_answer_when_a_peer_holds_the_lock() {
    let (fixture, lease, _) = leased_pool().await;
    let notes = Notes::start(fixture.manager.reporter());
    let lock = fixture.manager.lifecycle_lock_path(&lease.worker);
    let peer = std::sync::Mutex::new(None);
    let answer = with_run_marker(&ctx(), &fixture.manager, &lease, "suite-user-run-1", "run", async |_| {
        let held = fixture.manager.locks().acquire(&ctx(), &lock, "pool-start", "busy").await?;
        *peer.lock().unwrap() = Some(held);
        Ok("the answer")
    })
    .await;
    assert_eq!(answer.unwrap(), "the answer");
    assert!(
        read_run_marker(&fixture.settings, &lease.worker).unwrap().is_some(),
        "the marker was removed under a lock that a peer held"
    );
    assert_eq!(
        notes.all(),
        [
            "the run marker of suite-user-run-1 stays on air-macos-1: another lifecycle operation held the lock for the \
          whole wait; it goes stale with this process"
        ]
    );
    drop(peer);
}

/// A run of this process inside a run of this process is an internal error. The nested run would remove the marker of
/// the outer run at its end. The outer run keeps its marker until its own end.
#[cfg(unix)]
#[tokio::test]
async fn a_nested_run_marker_is_an_internal_error() {
    let (fixture, lease, _) = leased_pool().await;
    let (nested, outer) = with_run_marker(&ctx(), &fixture.manager, &lease, "suite-user-run-1", "flake", async |current| {
        let nested = with_run_marker(&ctx(), &fixture.manager, &current, "suite-user-run-2", "run", async |_| Ok(())).await;
        let outer = read_run_marker(&fixture.settings, &current.worker)?.map(|marker| marker.run_id);
        Ok((nested, outer))
    })
    .await
    .unwrap();
    let refusal = nested.unwrap_err();
    assert_eq!(refusal.code, "internal_error");
    assert_eq!(refusal.message, "a run of this process is already in flight on air-macos-1");
    assert_eq!(outer.as_deref(), Some("suite-user-run-1"));
    assert_eq!(read_run_marker(&fixture.settings, &lease.worker).unwrap(), None);
}

/// An interrupt keeps the marker, as it keeps the lease: the iteration of the run can still be in flight. A note says
/// that the marker stays.
#[cfg(unix)]
#[tokio::test]
async fn an_interrupted_run_keeps_its_marker() {
    let (fixture, lease, _) = leased_pool().await;
    let notes = Notes::start(fixture.manager.reporter());
    let interrupts = Interrupts::detached();
    let ctx = interrupts.context();
    with_run_marker(&ctx, &fixture.manager, &lease, "suite-user-run-1", "run", async |_| {
        assert!(interrupts.deliver(Signal::Interrupt));
        Ok(())
    })
    .await
    .unwrap();
    assert!(read_run_marker(&fixture.settings, &lease.worker).unwrap().is_some());
    assert_eq!(
        notes.all(),
        ["the run marker of suite-user-run-1 stays on air-macos-1: the run was interrupted; it goes stale with this process"]
    );
}

/// A run restarts its own worker under its own marker, so an authorized stop from inside the run passes the run guard.
/// The same stop from another controller refuses `lease_busy`.
#[cfg(unix)]
#[tokio::test]
async fn an_authorized_stop_inside_a_run_passes_the_run_guard() {
    let (fixture, lease, _) = leased_pool().await;
    let another = fixture.another_controller();
    let (inside, outside) = with_run_marker(&ctx(), &fixture.manager, &lease, "suite-user-run-1", "run", async |current| {
        let inside = fixture
            .manager
            .stop_without_lifecycle_lock(&ctx(), &current.worker, Some(&current))
            .await;
        let outside = another.stop_without_lifecycle_lock(&ctx(), &current.worker, Some(&current)).await;
        Ok((inside, outside))
    })
    .await
    .unwrap();
    assert_eq!(inside.unwrap(), StopState::AlreadyStopped);
    assert_eq!(outside.unwrap_err().code, "lease_busy");
}
