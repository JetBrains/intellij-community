use crate::worker::docker::{CreateRecord, Docker};
use crate::worker::hypervisor::Machine;
use avl_base::format::words;
use avl_base::{Backend, GuestOs, Outcome, Refusal, SCHEMA_VERSION};
use avl_host_sys::guest::{InitReceipt, PARITY_MARKER, init_receipt_path, write_init_receipt};
use avl_host_sys::{Captured, Channel as _, Ctx, SpawnOptions};
use avl_host_testkit::agent::active_reply;
use avl_host_testkit::{answer_guest, outcome_of, refusal, said};
use avl_testkit::tartfake::Answer;
use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::command_status;
use crate::lane::observe::testing::{Fixture, PARALLELS_INFO_JSON, parallels_fixture};
use avl_base::RefusalExt;

// --- fixtures --------------------------------------------------------------------------------------------------

/// A strict `tart list` document from the rows a test declares.
fn tart_list(rows: &[(&str, f64, bool, &str)]) -> String {
    let rows: Vec<Value> = rows
        .iter()
        .map(|(name, disk, running, state)| json!({ "Name": name, "Disk": disk, "Running": running, "State": state }))
        .collect();
    Value::Array(rows).to_string()
}

async fn status(fixture: &Fixture) -> Outcome {
    outcome_of(command_status(&Ctx::background(), &fixture.manager).await)
}

fn workers(outcome: &Outcome) -> &[Value] {
    outcome.data["workers"].as_array().expect("the report lists workers")
}

fn parallels_row(outcome: &Outcome) -> &Value {
    match workers(outcome) {
        [row] => row,
        rows => panic!("the Parallels pool reported {} workers", rows.len()),
    }
}

/// The argv fragments of the questions only a macOS worker can be asked: its TCC databases and the query over them,
/// its Aqua session, and the SSH host key a Tart macOS clone regenerates.
const MACOS_ONLY_PROBES: [&str; 4] = ["TCC.db", "/usr/bin/sqlite3", "/usr/bin/who", "ssh-host-key-fingerprint"];

/// Answers a Linux guest's probes and fails every macOS-only one.
///
/// The guard is asserted by this never being consulted rather than by the shape of the row: an unguarded probe errors
/// out of the channel *and* leaves its argv in the record, so a test names the question that was asked instead of
/// reporting a field that happened to end up false.
fn answer_linux_guest(
    rules: Vec<(&'static str, Captured)>,
) -> impl Fn(&[String], &SpawnOptions) -> Result<Captured, Refusal> + Send + Sync + 'static {
    let answer = answer_guest(rules);
    move |argv, options| {
        let joined = argv.join(" ");
        if MACOS_ONLY_PROBES.iter().any(|probe| joined.contains(probe)) {
            return Err(Refusal::internal(format!(
                "a Linux guest was asked a macOS-only question: {joined}"
            )));
        }
        answer(argv, options)
    }
}

/// Every worker of the pool is a healthy running macOS worker with no run in flight and a logged-in console.
fn answer_running_macos_pool(fixture: &Fixture) {
    for channel in fixture.channels() {
        channel.answer(answer_guest(vec![
            ("/usr/bin/who", said("admin    console  Aug 23 10:00\n")),
            (" active ", active_reply(None)),
        ]));
    }
}

/// Every worker of the pool is a healthy running Linux worker with no run in flight.
fn answer_running_linux_pool(fixture: &Fixture) {
    for channel in fixture.channels() {
        channel.answer(answer_linux_guest(vec![(" active ", active_reply(None))]));
    }
}

/// Leaves behind the receipt of a worker this pool last provisioned from another working copy of the repository -
/// the state a pool shared between two checkouts is in until the next run re-provisions it. Restamped from a real
/// receipt rather than hand-built, so the schema stays the writer's.
fn provisioned_for_another_checkout(fixture: &Fixture, worker: &str, host_repo: &str) {
    write_init_receipt(&fixture.settings, worker).expect("the init receipt is written");
    let path = init_receipt_path(&fixture.settings, worker);
    let mut receipt: InitReceipt =
        serde_json::from_slice(&std::fs::read(&path).expect("the receipt is readable")).expect("the receipt decodes");
    receipt.host_repo = host_repo.to_owned();
    let mut encoded = serde_json::to_vec(&receipt).expect("the receipt encodes");
    encoded.push(b'\n');
    std::fs::write(&path, encoded).expect("the receipt is restamped");
}

/// [`PARALLELS_INFO_JSON`] with the two read-only shares the controller declares already in place, which is what
/// `sharesConfigured` - and so the parity probe behind it - waits for.
fn parallels_info_with_shares(fixture: &Fixture) -> String {
    let settings = &fixture.settings;
    let path = |path: &std::path::Path| path.to_string_lossy().into_owned();
    let mut shared = serde_json::Map::new();
    shared.insert("enabled".to_owned(), json!(true));
    shared.insert(
        settings.repo_share_name.clone(),
        json!({ "enabled": true, "path": path(settings.host_repo().expect("resolved")), "mode": "ro" }),
    );
    shared.insert(
        settings.bazel_share_name.clone(),
        json!({ "enabled": true, "path": path(settings.host_bazel_user_root().expect("resolved")), "mode": "ro" }),
    );
    json!([{
        "ID": "{11111111-2222}",
        "Name": "macOS",
        "Type": "APPLE_VZ_VM",
        "State": "running",
        "OS": "macosx",
        "Host Shared Folders": shared,
    }])
    .to_string()
}

// --- the state ladder ------------------------------------------------------------------------------------------

/// "suspended" is not "stopped": the worker is holding a multi-gigabyte saved guest state and the next start resumes
/// rather than boots, so the ladder keeps the two apart - and neither is probed, because tart's list metadata
/// already answered.
#[tokio::test]
async fn the_state_ladder_keeps_suspended_distinct_from_stopped() {
    let fixture = Fixture::tart_macos();
    fixture.fake.answer(
        Answer::ListJson,
        tart_list(&[("air-macos-1", 80.0, false, "suspended"), ("air-macos-2", 80.0, false, "stopped")]),
    );
    let outcome = status(&fixture).await;
    let states: Vec<&Value> = workers(&outcome).iter().map(|row| &row["state"]).collect();
    assert_eq!(states, [&json!("suspended"), &json!("stopped")]);
}

/// The trap this ladder was built around: `tart exec` reports a stopped VM on stderr but exits zero, so a guest
/// probe against a stopped worker would read as a live one. The list metadata is authoritative, and a worker the
/// list calls not-running is never probed at all - even when its channel would happily answer.
#[tokio::test]
async fn tarts_list_metadata_not_the_exec_exit_code_decides_running() {
    let fixture = Fixture::tart_macos();
    fixture.fake.answer(
        Answer::ListJson,
        tart_list(&[("air-macos-1", 80.0, false, "stopped"), ("air-macos-2", 80.0, true, "running")]),
    );
    // The stopped worker's channel answers success, the way a real `tart exec` against a stopped VM exits 0.
    fixture
        .channel("air-macos-2")
        .answer(answer_guest(vec![(" active ", active_reply(None))]));
    let outcome = status(&fixture).await;
    let rows = workers(&outcome);
    assert_eq!((&rows[0]["state"], &rows[0]["guestAgent"]), (&json!("stopped"), &json!(false)));
    assert_eq!(
        fixture.channel("air-macos-1").lines(),
        Vec::<String>::new(),
        "a worker the list calls stopped was probed anyway"
    );
    assert_eq!((&rows[1]["state"], &rows[1]["guestAgent"]), (&json!("running"), &json!(true)));
}

#[tokio::test]
async fn a_running_vm_whose_guest_never_answers_is_unresponsive() {
    let fixture = Fixture::tart_macos();
    fixture.fake.answer(
        Answer::ListJson,
        tart_list(&[("air-macos-1", 80.0, true, "running"), ("air-macos-2", 80.0, false, "stopped")]),
    );
    fixture.channel("air-macos-1").answer(|_, _| {
        Ok(Captured {
            exit_code: 1,
            ..Captured::default()
        })
    });
    let outcome = status(&fixture).await;
    assert_eq!(workers(&outcome)[0]["state"], json!("unresponsive"));
}

// --- the tri-state facts ---------------------------------------------------------------------------------------

/// `null` is "the question was not asked", and it must never collapse into `false`. A worker whose guest never
/// answered had its console and its TCC asked of nobody. The provenance and the SSH host key are host facts, so they
/// are `false` and not `null`. Every key is present on every row, which is what an agent reading the envelope
/// relies on.
#[tokio::test]
async fn tri_state_facts_are_null_never_false() {
    let fixture = Fixture::tart_macos();
    fixture
        .fake
        .answer(Answer::ListJson, tart_list(&[("air-macos-1", 80.0, false, "suspended")]));
    let outcome = status(&fixture).await;
    let expected_gb = fixture.settings.vm_root_disk_gb;
    let host_repo = fixture.root().to_string_lossy().into_owned();
    assert_eq!(
        workers(&outcome),
        [
            json!({
                "worker": "air-macos-1",
                "exists": true,
                "provenanceReady": false,
                "provenanceError": "worker_provenance_missing",
                "state": "suspended",
                "pid": null,
                "guestAgent": false,
                "consoleLogin": null,
                "workerStorageReady": false,
                "rootDiskGb": 80.0,
                "rootDiskExpectedGb": expected_gb,
                "rootDiskReady": f64::from(expected_gb) <= 80.0,
                "parityReady": null,
                "parityError": null,
                "hostRepo": host_repo,
                "tccClean": null,
                "tccError": null,
                "sshHostKeyFingerprint": null,
                "activeRun": null,
                "runError": null,
                "lease": null,
                "sshHostKeyReady": false,
                "sshHostKeyUnique": null,
            }),
            // A missing VM is still reported, with the one error that fact implies, and the guest facts still null.
            json!({
                "worker": "air-macos-2",
                "exists": false,
                "provenanceReady": false,
                "provenanceError": "worker_vm_missing",
                "state": "stopped",
                "pid": null,
                "guestAgent": false,
                "consoleLogin": null,
                "workerStorageReady": false,
                "rootDiskGb": null,
                "rootDiskExpectedGb": expected_gb,
                "rootDiskReady": false,
                "parityReady": null,
                "parityError": null,
                "hostRepo": host_repo,
                "tccClean": null,
                "tccError": null,
                "sshHostKeyFingerprint": null,
                "activeRun": null,
                "runError": null,
                "lease": null,
                "sshHostKeyReady": false,
                "sshHostKeyUnique": null,
            }),
        ]
    );
    assert_eq!(outcome.data["backend"], json!("tart"));
    assert_eq!(outcome.data["hostPathsError"], json!(null));
}

// --- the macOS-only gates --------------------------------------------------------------------------------------

// --- the parity refusal ----------------------------------------------------------------------------------------

/// `guest_init_stale` is the normal state of a pool shared between two checkouts, and the next run re-provisions
/// the worker for the one that asked. Keeping only `parityReady` made a self-repairing worker indistinguishable from
/// a layout that needs looking at.
#[tokio::test]
async fn a_parity_refusal_keeps_its_code() {
    let fixture = Fixture::tart_macos();
    fixture.fake.answer(
        Answer::ListJson,
        tart_list(&[("air-macos-1", 80.0, true, "running"), ("air-macos-2", 80.0, true, "running")]),
    );
    provisioned_for_another_checkout(&fixture, "air-macos-1", "/another/checkout");
    write_init_receipt(&fixture.settings, "air-macos-2").expect("the init receipt is written");
    answer_running_macos_pool(&fixture);
    let outcome = status(&fixture).await;
    let rows = workers(&outcome);
    assert_eq!(
        (&rows[0]["parityReady"], &rows[0]["parityError"]),
        (&json!(false), &json!("guest_init_stale"))
    );
    assert_eq!((&rows[1]["parityReady"], &rows[1]["parityError"]), (&json!(true), &Value::Null));
    assert!(
        outcome.text.contains("parity=not-ready(guest_init_stale)"),
        "the one-line rendering drops the reason:\n{}",
        outcome.text
    );
    assert!(
        outcome.text.contains("parity=ready"),
        "the provisioned worker does not render as ready:\n{}",
        outcome.text
    );
}

// --- the host paths a parity verdict is measured against --------------------------------------------------------

/// The parity verdict is a comparison against the two host paths, so `status` has to resolve them itself. It once did
/// not, and on real hardware every worker of both pools reported `parity=not-ready(host_paths_unresolved)` with
/// `hostRepo` empty - the controller's own omission, rendered as the guest's answer. This starts from the fixture
/// whose paths are not declared for it.
#[tokio::test]
async fn status_resolves_the_host_paths_its_parity_verdict_needs() {
    let fixture = Fixture::before_host_paths(Backend::Tart, GuestOs::Macos, true);
    fixture.fake.answer(
        Answer::ListJson,
        tart_list(&[("air-macos-1", 80.0, true, "running"), ("air-macos-2", 80.0, true, "running")]),
    );
    answer_running_macos_pool(&fixture);
    let outcome = status(&fixture).await;
    assert_eq!(outcome.data["hostPathsError"], Value::Null);
    let host_repo = outcome.data["hostRepo"].as_str().expect("a host repo");
    assert!(!host_repo.is_empty());
    assert!(!outcome.data["hostBazelUserRoot"].as_str().expect("a Bazel user root").is_empty());
    for row in workers(&outcome) {
        assert_eq!(row["hostRepo"], json!(host_repo), "{row}");
        // The guest's own refusal, which is the whole point: nothing provisioned these workers, so the receipt is
        // missing - a fact about the guest, and precisely what `host_paths_unresolved` used to hide.
        assert_eq!(row["parityError"], json!("guest_init_required"), "{row}");
    }

    // And it published what it resolved rather than resolving into something of its own: writing a receipt is
    // refused before the paths exist, so these writes are only possible because `status` resolved this very config.
    for worker in &fixture.settings.workers {
        write_init_receipt(&fixture.settings, worker).expect("the init receipt is written");
    }
    let outcome = status(&fixture).await;
    for row in workers(&outcome) {
        assert_eq!((&row["parityReady"], &row["parityError"]), (&json!(true), &Value::Null), "{row}");
    }
    assert!(
        !outcome.text.contains("host_paths"),
        "a resolved pool states a host-path verdict nobody needs:\n{}",
        outcome.text
    );
}

/// A host-side refusal is not a verdict about any worker. It is one fact, stated once, and the parity question it
/// makes unaskable stays unasked: `n/a`, the same tri-state the macOS-only probes use, rather than `not-ready` on
/// every row for a check that never ran.
#[tokio::test]
async fn a_host_path_refusal_is_not_every_workers_parity_verdict() {
    let fixture = Fixture::before_host_paths(Backend::Tart, GuestOs::Macos, false);
    fixture.fake.answer(
        Answer::ListJson,
        tart_list(&[("air-macos-1", 80.0, true, "running"), ("air-macos-2", 80.0, true, "running")]),
    );
    answer_running_macos_pool(&fixture);
    let outcome = status(&fixture).await;
    assert_eq!(outcome.data["hostPathsError"], json!("host_repo_required"));
    assert_eq!(outcome.data["hostRepo"], json!(""));
    for row in workers(&outcome) {
        assert_eq!(
            (&row["parityReady"], &row["parityError"], &row["hostRepo"]),
            (&Value::Null, &Value::Null, &Value::Null),
            "{row}"
        );
        // Everything the host paths have nothing to do with is still reported: a report that gives up on the pool
        // because of one host fact is the readout this set out to remove.
        assert_eq!(
            (&row["state"], &row["guestAgent"], &row["workerStorageReady"]),
            (&json!("running"), &json!(true), &json!(true)),
            "{row}"
        );
    }
    // The guest was never asked, not merely disbelieved.
    for channel in fixture.channels() {
        assert!(
            !channel.saw_call_containing(PARITY_MARKER),
            "{} was asked for a parity verdict anyway: {:?}",
            channel.worker(),
            channel.lines()
        );
    }
    assert!(
        outcome.text.starts_with("pool: host_paths=not-ready(host_repo_required)\n"),
        "the text rendering states no reason for the n/a parity below it:\n{}",
        outcome.text
    );
    assert!(
        outcome.text.contains("parity=n/a") && !outcome.text.contains("host_paths_unresolved"),
        "a host-side refusal still renders as a worker's parity verdict:\n{}",
        outcome.text
    );
}

// --- the second pass -------------------------------------------------------------------------------------------

/// `sshHostKeyUnique` is a per-worker field only decidable globally: a fingerprint is unique against every other
/// worker's answer, so it is computed in a second pass over the finished fan-out rather than during it. A macOS pool,
/// because that is the only pool with a fingerprint to compare.
#[tokio::test]
async fn ssh_host_key_uniqueness_is_decided_over_the_whole_pool() {
    let fixture = Fixture::tart_macos();
    fixture.fake.answer(
        Answer::ListJson,
        tart_list(&[("air-macos-1", 200.0, true, "running"), ("air-macos-2", 200.0, true, "running")]),
    );
    let answer = |fingerprint: &str| {
        answer_guest(vec![
            ("/bin/cat", said(&format!("SHA256:{fingerprint}\n"))),
            (" active ", active_reply(None)),
        ])
    };
    fixture.channel("air-macos-1").answer(answer("collided+fingerprint"));
    fixture.channel("air-macos-2").answer(answer("collided+fingerprint"));
    let outcome = status(&fixture).await;
    for row in workers(&outcome) {
        assert_eq!(
            (&row["sshHostKeyReady"], &row["sshHostKeyUnique"]),
            (&json!(true), &json!(false)),
            "a collided fingerprint was not reported: {row}"
        );
    }
    assert!(
        outcome.text.contains("ssh_host_key=collision"),
        "the text rendering does not say collision:\n{}",
        outcome.text
    );

    fixture.channel("air-macos-2").answer(answer("distinct+fingerprint"));
    let outcome = status(&fixture).await;
    for row in workers(&outcome) {
        assert_eq!(row["sshHostKeyUnique"], json!(true), "{row}");
    }
    assert!(outcome.text.contains("ssh_host_key=unique"), "{}", outcome.text);
}

// --- the parallels row -----------------------------------------------------------------------------------------

/// The Parallels path needs none of the guards the Tart path was missing - Parallels is refused with anything but a
/// macOS guest - but it discarded the parity refusal in the same way, and a worker provisioned from another checkout
/// of this repository is exactly as stale there.
#[tokio::test]
async fn the_parallels_row_keeps_its_parity_refusal() {
    let (fixture, _) = parallels_fixture();
    fixture.fake.answer(Answer::ListJson, parallels_info_with_shares(&fixture));
    fixture.channel("macOS").answer(answer_guest(vec![
        ("/usr/bin/who", said("test  console  Aug 23 10:00\n")),
        (" active ", active_reply(None)),
    ]));
    let outcome = status(&fixture).await;
    let row = parallels_row(&outcome);
    assert_eq!(
        (&row["sharesConfigured"], &row["initialized"]),
        (&json!(true), &json!(true)),
        "the fixture is not the worker this test needs: {row}"
    );
    assert_eq!((&row["parityReady"], &row["parityError"]), (&json!(true), &Value::Null), "{row}");
    assert_eq!(outcome.data["backend"], json!("parallels"));

    provisioned_for_another_checkout(&fixture, "macOS", "/another/checkout");
    let outcome = status(&fixture).await;
    let row = parallels_row(&outcome);
    assert_eq!(
        (&row["parityReady"], &row["parityError"]),
        (&json!(false), &json!("guest_init_stale")),
        "{row}"
    );
    assert!(
        outcome.text.contains("parity=not-ready(guest_init_stale)"),
        "the one-line rendering drops the reason:\n{}",
        outcome.text
    );
}

/// Both status paths report a host-path refusal through one helper, so this is the Parallels half of
/// [`a_host_path_refusal_is_not_every_workers_parity_verdict`].
#[tokio::test]
async fn the_parallels_row_states_a_host_path_refusal_once() {
    let fixture = Fixture::before_host_paths(Backend::Parallels, GuestOs::Macos, false);
    fixture.fake.answer(Answer::ListJson, PARALLELS_INFO_JSON);
    fixture.fake.answer(Answer::Status, "VM macOS exist running\n");
    fixture.channel("macOS").answer(answer_guest(vec![
        ("/usr/bin/who", said("test  console  Aug 23 10:00\n")),
        (" active ", active_reply(None)),
    ]));
    let outcome = status(&fixture).await;
    let row = parallels_row(&outcome);
    assert_eq!(
        (&row["hostPathsError"], &row["hostRepo"]),
        (&json!("host_repo_required"), &Value::Null),
        "{row}"
    );
    // The shares cannot be checked against paths that do not exist, and the worker is still reported: the row says
    // what it knows and names the one reason for what it does not.
    assert_eq!(row["sharesConfigured"], json!(false), "{row}");
    assert_eq!(
        (&row["guestAgent"], &row["state"], &row["consoleLogin"]),
        (&json!(true), &json!("running"), &json!(true)),
        "{row}"
    );
    // Unasked rather than answered: both of its gates are down, `sharesConfigured` and the receipt this fixture
    // cannot write without the paths, which is the state a first-use Parallels worker is really in.
    assert_eq!((&row["parityReady"], &row["parityError"]), (&Value::Null, &Value::Null), "{row}");
    assert!(
        outcome.text.ends_with(" host_paths=not-ready(host_repo_required)"),
        "the one line states no reason for what it could not check:\n{}",
        outcome.text
    );
}

// --- the strict listing ----------------------------------------------------------------------------------------

/// A listing this controller half-understands must refuse rather than report a worker in a state nobody stated:
/// `Running` decides whether the guest is probed at all.
#[tokio::test]
async fn an_invalid_tart_list_is_refused_rather_than_half_read() {
    let fixture = Fixture::tart_macos();
    for (name, listing) in [
        ("not an array", "true"),
        ("null", "null"),
        (
            "entry missing Disk",
            r#"[{"Name":"air-macos-1","Running":false,"State":"stopped"}]"#,
        ),
        (
            "Disk as a string",
            r#"[{"Name":"air-macos-1","Disk":"80","Running":false,"State":"stopped"}]"#,
        ),
        (
            "Running as a string",
            r#"[{"Name":"air-macos-1","Disk":80,"Running":"yes","State":"stopped"}]"#,
        ),
    ] {
        fixture.fake.answer(Answer::ListJson, listing);
        let refusal = refusal(command_status(&Ctx::background(), &fixture.manager).await);
        assert_eq!(refusal.code, "invalid_tart_list", "{name}: {}", refusal.message);
    }
}

// --- the disk arithmetic ---------------------------------------------------------------------------------------

/// `>=` on purpose: `tart set --disk-size` only grows, so a worker created when the default was 400 GB keeps that
/// disk and is perfectly usable against today's smaller expectation.
#[tokio::test]
async fn an_oversized_root_disk_still_counts_as_ready() {
    let fixture = Fixture::tart_macos();
    fixture.fake.answer(
        Answer::ListJson,
        tart_list(&[("air-macos-1", 400.0, false, "stopped"), ("air-macos-2", 40.0, false, "stopped")]),
    );
    let outcome = status(&fixture).await;
    let rows = workers(&outcome);
    assert_eq!(rows[0]["rootDiskReady"], json!(true), "{}", rows[0]);
    assert_eq!(rows[1]["rootDiskReady"], json!(false), "{}", rows[1]);
    assert!(outcome.text.contains("root_disk=400/"), "{}", outcome.text);
}

/// The lease is reported as held and since when, never by whom: who holds a worker is not something an
/// unauthenticated `status` is told.
#[tokio::test]
async fn a_leased_worker_is_reported_without_its_holder() {
    let fixture = Fixture::tart_macos();
    fixture.lease_receipt("air-macos-1");
    let outcome = status(&fixture).await;
    assert_eq!(
        workers(&outcome)[0]["lease"],
        json!({ "state": "leased", "acquiredAt": "2026-08-23T00:00:00.000Z" })
    );
    assert!(outcome.text.starts_with("air-macos-1: stopped lease=leased "), "{}", outcome.text);
}

// --- the docker row --------------------------------------------------------------------------------------------

/// The verbs that change a container, a volume or an image. `status` is a report, so the fake must record none.
const DOCKER_MUTATING_VERBS: [&str; 9] = ["create", "start", "stop", "rm", "build", "volume", "pull", "push", "tag"];

/// Every argv the fake engine received, checked against [`DOCKER_MUTATING_VERBS`] by its verb.
fn assert_docker_status_wrote_nothing(fixture: &Fixture) {
    for argv in fixture.fake.argvs() {
        assert!(
            argv.first().is_none_or(|verb| !DOCKER_MUTATING_VERBS.contains(&verb.as_str())),
            "status sent a mutating command: {argv:?}"
        );
    }
}

fn docker_row(outcome: &Outcome) -> &Value {
    match workers(outcome) {
        [row] => row,
        rows => panic!("the Docker pool reported {} workers", rows.len()),
    }
}

fn docker(fixture: &Fixture) -> &Docker {
    match fixture.manager.machine() {
        Machine::Docker(docker) => docker,
        _ => panic!("the fixture is a Docker pool"),
    }
}

/// The id the fake engine reports for its one container.
const CONTAINER_ID: &str = "fake-container-1";

/// Writes the create record a `docker create` of `argv` would leave behind for the container `container_id`.
fn record_create(fixture: &Fixture, worker: &str, argv: Vec<String>, container_id: &str) {
    let path = fixture.settings.docker_create_record_path(worker);
    std::fs::create_dir_all(path.parent().expect("a worker directory")).expect("the worker directory is created");
    let record = CreateRecord {
        schema_version: SCHEMA_VERSION,
        worker: worker.to_owned(),
        argv,
        container_id: container_id.to_owned(),
    };
    let mut encoded = serde_json::to_vec(&record).expect("the record encodes");
    encoded.push(b'\n');
    std::fs::write(path, encoded).expect("the create record is written");
}

fn current_argv(fixture: &Fixture) -> Vec<String> {
    docker(fixture).create_argv("air-docker-1").expect("the create argv")
}

/// A running container made with the argv a create would use now, from an image the engine has, provisioned for
/// this checkout.
fn running_docker_worker(fixture: &Fixture) {
    fixture.fake.answer(Answer::ContainerState, "running/0\n");
    fixture.fake.answer(Answer::ContainerId, format!("{CONTAINER_ID}\n"));
    fixture.fake.answer(Answer::ImagePresent, "");
    record_create(fixture, "air-docker-1", current_argv(fixture), CONTAINER_ID);
    write_init_receipt(&fixture.settings, "air-docker-1").expect("the init receipt is written");
    answer_running_linux_pool(fixture);
}

/// TCC admission, the console session and the SSH host-key fingerprint are macOS-only. Each of the three once
/// answered `false` for a healthy Linux worker, and a real session then took both Linux workers for unusable. So a
/// running Linux worker is asked none of these questions.
#[tokio::test]
async fn a_running_linux_worker_is_asked_no_macos_question() {
    let fixture = Fixture::docker();
    running_docker_worker(&fixture);
    let outcome = status(&fixture).await;
    let row = docker_row(&outcome);
    assert_eq!((&row["state"], &row["guestAgent"]), (&json!("running"), &json!(true)), "{row}");
    for field in [
        "tccClean",
        "tccError",
        "consoleLogin",
        "sshHostKeyFingerprint",
        "sshHostKeyReady",
        "sshHostKeyUnique",
    ] {
        // Absent and not null: indexing an absent key also answers null, so the test asks for the key itself.
        assert!(row.get(field).is_none(), "{} carries {field}: {row}", row["worker"]);
    }
    // The assertion that matters: not the shape of the row, but that the guest was never asked.
    for channel in fixture.channels() {
        for probe in MACOS_ONLY_PROBES {
            assert!(
                !channel.saw_call_containing(probe),
                "{} was asked {probe:?}: {:?}",
                channel.worker(),
                channel.lines()
            );
        }
    }
    assert!(
        !outcome.text.contains("console=no") && !outcome.text.contains("ssh_host_key=not-ready"),
        "a healthy Linux worker renders as failing a macOS check:\n{}",
        outcome.text
    );
}

/// A slot with no container is `absent`, not an error, and every key of the row is present. The Tart facts are not
/// null here but absent: a container has no provenance, console, TCC, SSH host key or root disk to ask about.
#[tokio::test]
async fn an_absent_docker_worker_reports_every_key() {
    let fixture = Fixture::new(Backend::Docker, GuestOs::Linux);
    let outcome = status(&fixture).await;
    let host_repo = fixture.root().to_string_lossy().into_owned();
    assert_eq!(
        outcome.data,
        json!({
            "backend": "docker",
            "engine": "host",
            "engineStatus": null,
            "workers": [{
                "worker": "air-docker-1",
                "exists": false,
                "state": "absent",
                "exitCode": null,
                "image": docker(&fixture).image_tag(),
                "imageSource": "missing",
                "declarationCurrent": null,
                "declarationError": null,
                "guestAgent": false,
                "workerStorageReady": false,
                "parityReady": null,
                "parityError": null,
                "lease": null,
            }],
            "hostRepo": host_repo,
            "hostBazelUserRoot": host_repo,
            "hostPathsError": null,
        })
    );
    assert_eq!(
        outcome.text,
        "pool: engine=host\n\
         air-docker-1: absent lease=free image=missing declaration=n/a parity=n/a"
    );
    assert!(
        !fixture.fake.saw_call_containing("list"),
        "a Docker status asked for a Tart listing: {:?}",
        fixture.fake.calls()
    );
    assert!(fixture.channel("air-docker-1").lines().is_empty());
    assert_docker_status_wrote_nothing(&fixture);
}

#[tokio::test]
async fn a_running_docker_worker_with_a_current_declaration_is_ready() {
    let fixture = Fixture::new(Backend::Docker, GuestOs::Linux);
    running_docker_worker(&fixture);
    let outcome = status(&fixture).await;
    let row = docker_row(&outcome);
    assert_eq!(
        (&row["exists"], &row["state"], &row["exitCode"], &row["imageSource"]),
        (&json!(true), &json!("running"), &Value::Null, &json!("local")),
        "{row}"
    );
    assert_eq!(
        (&row["declarationCurrent"], &row["declarationError"]),
        (&json!(true), &Value::Null),
        "{row}"
    );
    assert_eq!(
        (
            &row["guestAgent"],
            &row["workerStorageReady"],
            &row["parityReady"],
            &row["parityError"]
        ),
        (&json!(true), &json!(true), &json!(true), &Value::Null),
        "{row}"
    );
    assert_eq!(
        outcome.text,
        "pool: engine=host\n\
         air-docker-1: running lease=free image=local declaration=current parity=ready"
    );
    // A Linux guest in a container is asked no macOS question either.
    for probe in MACOS_ONLY_PROBES {
        assert!(
            !fixture.channel("air-docker-1").saw_call_containing(probe),
            "the container was asked {probe:?}"
        );
    }
    assert_docker_status_wrote_nothing(&fixture);
}

/// The engine cannot tell a pulled image from a built one, so `status` reads the pool's image record: `pulled` for
/// this tag, and `local` for a record about another tag or a built image.
#[tokio::test]
async fn a_pulled_image_is_reported_from_the_image_record() {
    let fixture = Fixture::new(Backend::Docker, GuestOs::Linux);
    running_docker_worker(&fixture);
    let path = fixture.settings.docker_image_record_path();
    let record = |tag: &str, source: &str| {
        std::fs::write(
            &path,
            format!(r#"{{"schemaVersion":{SCHEMA_VERSION},"tag":"{tag}","source":"{source}"}}"#),
        )
        .expect("the image record is written");
    };
    record(&docker(&fixture).image_tag(), "pulled");
    let outcome = status(&fixture).await;
    assert_eq!(docker_row(&outcome)["imageSource"], json!("pulled"), "{outcome:?}");
    assert!(outcome.text.contains(" image=pulled "), "{}", outcome.text);

    record("air-ui-worker:000000000000", "pulled");
    let outcome = status(&fixture).await;
    assert_eq!(docker_row(&outcome)["imageSource"], json!("local"), "{outcome:?}");
    record(&docker(&fixture).image_tag(), "built");
    let outcome = status(&fixture).await;
    assert_eq!(docker_row(&outcome)["imageSource"], json!("local"), "{outcome:?}");
    assert_docker_status_wrote_nothing(&fixture);
}

/// An exited container keeps the exit code of its entrypoint, because 0 is a `docker stop` and 1 is an `air-display`
/// that gave up. A container that does not run is never asked anything.
#[tokio::test]
async fn an_exited_docker_worker_keeps_its_exit_code() {
    let fixture = Fixture::new(Backend::Docker, GuestOs::Linux);
    running_docker_worker(&fixture);
    fixture.fake.answer(Answer::ContainerState, "exited/1\n");
    let outcome = status(&fixture).await;
    let row = docker_row(&outcome);
    assert_eq!(
        (&row["state"], &row["exitCode"], &row["guestAgent"], &row["parityReady"]),
        (&json!("exited"), &json!(1), &json!(false), &Value::Null),
        "{row}"
    );
    // The declaration is a host fact, so a stopped container still answers it.
    assert_eq!(row["declarationCurrent"], json!(true), "{row}");
    assert_eq!(
        outcome.text,
        "pool: engine=host\n\
         air-docker-1: exited(1) lease=free image=local declaration=current parity=n/a"
    );
    assert!(
        fixture.channel("air-docker-1").lines().is_empty(),
        "an exited container was probed: {:?}",
        fixture.channel("air-docker-1").lines()
    );
    assert_docker_status_wrote_nothing(&fixture);
}

/// A container made with other shares or another image is reported stale, which is what the next lease repairs by
/// creating it again. So is a container of the same name that the record is not about, and a container with no
/// record at all: nothing says what either was made with.
#[tokio::test]
async fn a_stale_docker_declaration_is_reported_stale() {
    let fixture = Fixture::new(Backend::Docker, GuestOs::Linux);
    running_docker_worker(&fixture);
    record_create(
        &fixture,
        "air-docker-1",
        words(["docker", "create", "--name", "air-docker-1", "air/old:0"]),
        CONTAINER_ID,
    );
    let outcome = status(&fixture).await;
    let row = docker_row(&outcome);
    assert_eq!(
        (&row["declarationCurrent"], &row["declarationError"]),
        (&json!(false), &Value::Null),
        "{row}"
    );
    assert!(outcome.text.contains(" declaration=stale "), "{}", outcome.text);

    // The current argv, recorded for another container: one made by hand under the same name.
    record_create(&fixture, "air-docker-1", current_argv(&fixture), "another-container");
    let outcome = status(&fixture).await;
    assert_eq!(docker_row(&outcome)["declarationCurrent"], json!(false), "{}", docker_row(&outcome));

    std::fs::remove_file(fixture.settings.docker_create_record_path("air-docker-1")).expect("the record is removed");
    let outcome = status(&fixture).await;
    assert_eq!(docker_row(&outcome)["declarationCurrent"], json!(false), "{}", docker_row(&outcome));
    assert_docker_status_wrote_nothing(&fixture);
}

/// The Docker half of [`a_host_path_refusal_is_not_every_workers_parity_verdict`]: the refusal is stated once for the
/// pool, and the two questions that need the host paths stay unasked on the row.
#[tokio::test]
async fn a_docker_status_states_a_host_path_refusal_once() {
    let fixture = Fixture::before_host_paths(Backend::Docker, GuestOs::Linux, false);
    fixture.fake.answer(Answer::ContainerState, "running/0\n");
    answer_running_linux_pool(&fixture);
    let outcome = status(&fixture).await;
    assert_eq!(outcome.data["hostPathsError"], json!("host_repo_required"));
    let row = docker_row(&outcome);
    assert_eq!(
        (
            &row["declarationCurrent"],
            &row["declarationError"],
            &row["parityReady"],
            &row["parityError"]
        ),
        (&Value::Null, &Value::Null, &Value::Null, &Value::Null),
        "{row}"
    );
    assert_eq!(
        (&row["guestAgent"], &row["workerStorageReady"]),
        (&json!(true), &json!(true)),
        "{row}"
    );
    assert_eq!(
        outcome.text,
        "pool: engine=host host_paths=not-ready(host_repo_required)\n\
         air-docker-1: running lease=free image=missing declaration=n/a parity=n/a"
    );
    assert_docker_status_wrote_nothing(&fixture);
}

// --- the Lima engine -------------------------------------------------------------------------------------------

/// An engine never created is `Absent`, and `status` resolves no `limactl`, starts nothing and asks no `docker`
/// command: every row is `unknown`. This is what a Mac reports before its first Docker start.
#[cfg(unix)]
#[tokio::test]
async fn a_docker_status_on_an_absent_lima_engine_starts_nothing() {
    let fixture = Fixture::docker_lima();
    let outcome = status(&fixture).await;
    assert_eq!(
        (&outcome.data["engine"], &outcome.data["engineStatus"]),
        (&json!("lima"), &json!("Absent"))
    );
    let row = docker_row(&outcome);
    assert_eq!(
        (&row["state"], &row["exists"], &row["imageSource"], &row["lease"]),
        (&json!("unknown"), &json!(false), &json!("unknown"), &Value::Null),
        "{row}"
    );
    assert_eq!(
        outcome.text,
        "pool: engine=lima engine_status=Absent\n\
         air-docker-1: unknown lease=free image=unknown declaration=n/a parity=n/a"
    );
    assert!(fixture.fake.calls().is_empty(), "{:?}", fixture.fake.calls());
    assert!(!fixture.settings.lima_engine_record_path().exists());
}

/// A running engine is listed, never started or made again, and then asked the questions of an external engine.
#[cfg(unix)]
#[tokio::test]
async fn a_docker_status_on_a_running_lima_engine_asks_it() {
    let fixture = Fixture::docker_lima();
    let socket = fixture.settings.lima_socket_path();
    std::fs::create_dir_all(socket.parent().unwrap()).unwrap();
    fixture.fake.answer(Answer::LimaState, "Running\n");
    let outcome = status(&fixture).await;
    assert_eq!(outcome.data["engineStatus"], json!("Running"));
    assert_eq!(docker_row(&outcome)["state"], json!("absent"));
    assert!(
        outcome.text.starts_with("pool: engine=lima engine_status=Running\n"),
        "{}",
        outcome.text
    );
    let calls = fixture.fake.calls();
    assert!(
        !calls.iter().any(|call| call.starts_with("start ") || call.starts_with("delete ")),
        "{calls:#?}"
    );
    assert!(calls.iter().any(|call| call.starts_with("list --format")), "{calls:#?}");
    assert_docker_status_wrote_nothing(&fixture);
}
