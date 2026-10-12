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
    assert!(second.text.ends_with("recovered=true"), "{}", second.text);
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

/// The Docker twin: a container that does not run is released without a start and without a guest call, for the
/// Tart reason. A container that crashed must not keep its slot held.
#[tokio::test]
async fn releasing_a_stopped_docker_worker_does_not_start_it() {
    let fixture = Fixture::docker();
    let worker = "air-docker-1";
    let held = acquire(&fixture, "agent").await.unwrap();
    fixture.fake.answer(avl_testkit::tartfake::Answer::ContainerState, "exited/1\n");
    let released = release(&fixture, &lease_file(&held), &FakeProbe::default()).await.unwrap();
    assert_eq!(
        released.data,
        serde_json::json!({ "worker": worker, "released": true, "workerWasStopped": true })
    );
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
    fixture.fake.answer(avl_testkit::tartfake::Answer::ContainerState, "exited/1\n");
    let released = release(&fixture, &lease_file(&held), &FakeProbe::default()).await.unwrap();
    assert_eq!(
        released.data,
        serde_json::json!({ "worker": worker, "released": true, "workerWasStopped": true })
    );
    assert_ne!(docker.program(), "");
    assert!(fixture.fake.saw_call_containing("inspect --type container --format"));
    assert_eq!(read_lease(&fixture.settings.lease_path(worker)).unwrap(), None);
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
    assert_eq!(outcome.data["staleRunProcess"], Value::from(stale.identity.pid));
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

/// The contract a warm lane depends on: the daemon holds the supervisor's run slot for its whole life, so a release
/// that consulted the slot alone could never free a worker with a warm daemon on it. A slot held by this controller's
/// own idle daemon is not an execution, and the worker goes back.
#[cfg(unix)]
#[tokio::test]
async fn a_release_succeeds_while_a_warm_daemon_holds_the_slot() {
    let fixture = pool("air-macos-1");
    let held = acquire(&fixture, "agent").await.unwrap();
    fixture.run_as_fake_process("air-macos-1").await;
    live_guest(&fixture, "run-ui-daemon-1");

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
}

/// What the slot still exists to catch. The lifecycle lock excludes every iteration this host can start, so the residue
/// is a run no lock covers - a controller that crashed and left one behind - and the probe answers nothing for it.
#[cfg(unix)]
#[tokio::test]
async fn a_release_is_refused_while_an_iteration_is_in_flight() {
    let fixture = pool("air-macos-1");
    let held = acquire(&fixture, "agent").await.unwrap();
    fixture.run_as_fake_process("air-macos-1").await;
    live_guest(&fixture, "run-ui-daemon-1");

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

    let refusal = with_lease_operation(&ctx(), &fixture.manager, &lease, "suite", async |_| -> Result<(), Refusal> {
        panic!("the operation must not run against a lease that changed")
    })
    .await
    .unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("lease_changed", Exit::NO_PERM));

    let ran = with_lease_operation(&ctx(), &fixture.manager, &successor, "suite", async |current| Ok(current))
        .await
        .unwrap();
    assert_eq!(ran, successor);
}
