//! The fan-out against the shared daemon fixture: one fake daemon per worker, a fake `tart` reporting an empty pool,
//! and a guest that answers `df` and silence.

use std::path::Path;
use std::time::Duration;

use avl_base::GuestOs;
use avl_host_sys::Signal;
use avl_wire::progress::{Kind, Phase, PhaseChange, PhaseState};
use avl_wire::report::{EntryStatus, Status};
use pretty_assertions::assert_eq;
use tokio_util::sync::CancellationToken;

use super::*;
use crate::daemon::fixture::{Fixture, baseline_report, passing_run, write_report};
use crate::daemon::testing::{FakeDaemon, Recorded};

// --- the decisions -------------------------------------------------------------------------------------------

#[test]
fn the_decisions_of_a_shard_run_need_no_worker() {
    let plan = ShardPlan {
        shard_count: 2,
        shards: Vec::new(),
        total_ms: 0.0,
        slowest_class_ms: 0.0,
        floor_ms: 0.0,
        makespan_ms: 0.0,
    };
    assert!(shard_attempts(&plan, &[], Vec::new(), None).is_empty());
    assert!(plan_data(&plan, &[]).shards.is_empty());
    let refusal = shard_interrupted("SIGTERM", &[], &plan, "/agg/shard-1.json", "`vm.cmd lease release`");
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("shard_interrupted", Exit::SOFTWARE));
    assert_eq!(
        refusal.message,
        "SIGTERM abandoned the run after 0 of 2 shard(s) reported; the aggregate is at /agg/shard-1.json and the \
         leases are still held (release each with `vm.cmd lease release`)"
    );
    let releases = [ReleaseResult {
        worker: "air-2".to_owned(),
        receipt: std::path::PathBuf::from("/r/lease-2.json"),
        released: false,
        failure: Some("lease_changed".to_owned()),
    }];
    assert_eq!(release_failures(&releases), ["air-2: lease_changed"]);
}

/// `vm shard`, as far as its own options go: the controller's refusal of a bad option is `avl-vm`'s suite.
#[derive(clap::Parser, Debug)]
struct Probe {
    #[command(flatten)]
    args: ShardArgs,
}

fn shard_args(argv: &[&str]) -> ShardArgs {
    <Probe as clap::Parser>::try_parse_from(std::iter::once("shard").chain(argv.iter().copied()))
        .unwrap_or_else(|error| panic!("{argv:?} did not parse: {error}"))
        .args
}

async fn run(fixture: &Fixture, argv: &[&str]) -> Result<Outcome, Refusal> {
    let args = shard_args(argv);
    // Bounded, so a fan-out that serialized its shards against a blocking double fails rather than hangs.
    tokio::time::timeout(Duration::from_secs(120), command_shard(&Ctx::background(), &fixture.host, args))
        .await
        .expect("the shard run settles")
}

fn refused(result: Result<Outcome, Refusal>) -> Refusal {
    match result {
        Ok(outcome) => panic!("the command was expected to refuse and answered {outcome:?}"),
        Err(refusal) => refusal,
    }
}

fn data_of(outcome: &Outcome) -> ShardData {
    serde_json::from_value(outcome.data.clone()).expect("the outcome is shard data")
}

fn details_of(refusal: &Refusal) -> ShardData {
    serde_json::from_value(refusal.details().expect("the refusal carries details")).expect("the details are shard data")
}

/// What the pool last measured, which is the only thing a split may be balanced on.
fn seed_baseline(fixture: &Fixture, classes: &[(&str, f64)]) {
    write_report(
        &fixture.settings,
        &fixture.worker,
        "run-baseline-1",
        "iter-1",
        &baseline_report(classes),
    );
}

const TWO_CLASSES: [(&str, f64); 2] = [("com.example.Shard1Test", 2_000.0), ("com.example.Shard2Test", 1_000.0)];

/// Every worker of the pool with a warm daemon of its own, each reporting one class of its own so a two-shard run
/// cannot look like an overlap merely because both doubles named the same suite.
async fn warm_pool(fixture: &Fixture) -> Vec<Arc<FakeDaemon>> {
    let prep = fixture.pool_build().await;
    let mut daemons = Vec::new();
    for (index, worker) in fixture.workers().iter().enumerate() {
        let daemon = fixture.warm_daemon(worker, &prep).await;
        let (lines, xml) = passing_run(&format!("iter-{worker}"), &format!("com.example.Shard{}Test", index + 1));
        {
            let mut script = daemon.script();
            script.run_lines = lines;
            script.results_xml = xml.into_bytes();
        }
        daemons.push(daemon);
    }
    daemons
}

fn runs(daemon: &FakeDaemon) -> usize {
    daemon
        .script()
        .requests
        .iter()
        .filter(|request| request.as_str() == "POST /run")
        .count()
}

/// Holds every double's `/run` open on one token, and answers a task that waits until all of them are in flight.
fn hold_every_run(daemons: &[Arc<FakeDaemon>]) -> (CancellationToken, tokio::task::JoinHandle<()>) {
    let gate = CancellationToken::new();
    for daemon in daemons {
        daemon.script().block = Some(gate.clone());
    }
    let watched: Vec<Arc<FakeDaemon>> = daemons.to_vec();
    let all_in_flight = tokio::spawn(async move {
        while !watched.iter().all(|daemon| daemon.saw_request("POST /run")) {
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    });
    (gate, all_in_flight)
}

// --- the command's own grammar ------------------------------------------------------------------------------------

#[test]
fn shard_owns_three_options_and_hands_every_other_one_to_the_run_grammar() {
    let parsed = shard_args(&[
        "--shards",
        "3",
        "--holder",
        "ci-42",
        "--exact",
        "--lane",
        "ui",
        "--filter",
        "exclude-tag=slow",
        "--timeout",
        "600",
        "--fresh-ide",
    ]);
    assert_eq!((parsed.shards, parsed.holder.as_deref(), parsed.exact), (3, Some("ci-42"), true));
    // `run`'s grammar is the one owner of what a lane selects, and a second parser here would be a second answer.
    assert_eq!(
        parsed.run.argv(),
        ["--lane", "ui", "--filter", "exclude-tag=slow", "--timeout", "600", "--fresh-ide"]
    );
    let joined = shard_args(&["--shards=2", "--lane=ui"]);
    assert_eq!((joined.shards, joined.run.lane.as_deref()), (2, Some("ui")));
}

// A shard is a class-name filter over a lane; a single selector already names one class and has nothing to divide.
#[tokio::test]
async fn a_single_selector_has_nothing_to_split() {
    let fixture = Fixture::pool().await;
    let refusal = refused(run(&fixture, &["--shards", "2", "AirFlowUiTest"]).await);
    assert_eq!(refusal.exit, Exit::USAGE);
    assert!(refusal.message.contains("requires --lane"), "{}", refusal.message);
}

// --- before anything runs -----------------------------------------------------------------------------------------

// `shard_baseline_unavailable` is the most likely refusal of this command, and it must not cost an acquisition to
// discover: a run that leases two workers only to say it cannot plan is a pool that drains one bad invocation at a
// time.
#[tokio::test]
async fn an_unplannable_split_costs_no_acquisition() {
    let fixture = Fixture::pool().await;
    let refusal = refused(run(&fixture, &["--shards", "2", "--lane", "ui"]).await);
    assert_eq!(refusal.code, "shard_baseline_unavailable");
    for worker in fixture.workers() {
        assert!(!fixture.leased(&worker), "{worker} was leased by a refusal that never needed it");
    }
    assert_eq!(fixture.bazel.build_calls(), 0, "nothing is built before the split is plannable");
}

// One sharded run is one guest because it is one build, and two Bazel configurations do not merely compete for the
// analysis cache - they evict it. The check lives in the acquisition, which is what makes it bite on the case that
// can actually happen: a lease of the other guest left under a shared runtime root and recovered by holder.
#[tokio::test]
async fn a_lease_from_another_guest_is_refused_before_anything_runs() {
    let fixture = Fixture::pool().await;
    seed_baseline(&fixture, &TWO_CLASSES);
    let workers = fixture.workers();
    fixture.leased_by(&workers[0], "suite#1", GuestOs::Linux);
    let builds_before = fixture.bazel.build_calls();

    let refusal = refused(run(&fixture, &["--shards", "2", "--holder", "suite", "--lane", "ui"]).await);
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("lease_guest_os_mismatch", Exit::NO_PERM));
    assert!(refusal.message.contains("one run cannot span guests"), "{}", refusal.message);
    // The build precedes the lease, as it does for `run`, so the refusal follows one build - usually a warm one - and
    // the second worker the acquisition had already taken is given back rather than left owned by nobody.
    assert_eq!(fixture.bazel.build_calls(), builds_before + 1, "the build precedes the lease");
    assert!(!fixture.leased(&workers[1]), "the lease placed before the refusal is rolled back");
}

// A failed build is the likeliest failure after the plan, and it must cost no acquisition: holding N workers through
// a cold or broken analysis is N workers nobody else can use.
#[tokio::test]
async fn a_refused_build_costs_no_acquisition() {
    let fixture = Fixture::pool().await;
    seed_baseline(&fixture, &TWO_CLASSES);
    fixture
        .bazel
        .fail_builds(Refusal::new("host_build_failed", Exit::SOFTWARE, "the lane does not compile"));

    let refusal = refused(run(&fixture, &["--shards", "2", "--lane", "ui"]).await);
    assert_eq!(refusal.code, "host_build_failed");
    for worker in fixture.workers() {
        assert!(!fixture.leased(&worker), "{worker} was leased by a run whose build failed");
    }
}

// One id per invocation: the journal's, the trace directory's and, unless `--holder` names one, the holder's. The
// aggregate keeps an id of its own, because it lives in the no-clobber aggregates tree agents read by that shape.
#[tokio::test]
async fn a_sharded_run_keeps_a_journal_whose_id_is_its_holder() {
    let fixture = Fixture::pool().await;
    seed_baseline(&fixture, &TWO_CLASSES);
    warm_pool(&fixture).await;

    let outcome = run(&fixture, &["--shards", "2", "--lane", "ui"])
        .await
        .unwrap_or_else(|refusal| panic!("both shards passed: {refusal:?}"));
    let data = data_of(&outcome);
    assert!(data.holder.starts_with("suite-user-shard-"), "{}", data.holder);
    let aggregate_shaped = data
        .shard_run_id
        .strip_prefix("shard-")
        .and_then(|rest| rest.split_once('-'))
        .is_some_and(|(stamp, tail)| {
            stamp.bytes().all(|byte| byte.is_ascii_digit()) && tail.len() == 8 && tail.bytes().all(|byte| byte.is_ascii_hexdigit())
        });
    assert!(aggregate_shaped, "{}", data.shard_run_id);
    let journal = std::fs::read_to_string(fixture.settings.runtime_root.join("runs").join(&data.holder).join("events.ndjson"))
        .unwrap_or_else(|error| panic!("no journal for {}: {error}", data.holder));
    let records: Vec<serde_json::Value> = journal
        .lines()
        .map(|line| serde_json::from_str(line).unwrap_or_else(|_| panic!("a torn record {line:?}")))
        .collect();
    let first = records.first().expect("the journal has records");
    assert_eq!(
        (first["event"].as_str(), first["data"]["command"].as_str()),
        (Some("runStarted"), Some("shard"))
    );
    assert_eq!(records.last().and_then(|record| record["event"].as_str()), Some("runFinished"));
    let phases: Vec<String> = records
        .iter()
        .filter(|record| record["event"] == "phase")
        .map(|record| {
            format!(
                "{}:{}",
                record["data"]["phase"].as_str().unwrap_or_default(),
                record["data"]["state"].as_str().unwrap_or_default()
            )
        })
        .collect();
    for want in ["lease:started", "lease:finished", "release:finished"] {
        assert!(phases.iter().any(|phase| phase == want), "the journal lacks {want}: {phases:?}");
    }
}

// --- the fan-out --------------------------------------------------------------------------------------------------

#[tokio::test]
async fn two_shards_run_at_once_on_one_build_and_their_verdict_is_merged() {
    let fixture = Fixture::pool().await;
    let workers = fixture.workers();
    assert_eq!(workers.len(), 2, "the fixture pool is two workers");
    seed_baseline(&fixture, &TWO_CLASSES);
    let daemons = warm_pool(&fixture).await;
    let builds_before = fixture.bazel.build_calls();
    // Every `/run` is held until both are in flight: a fan-out that ran its shards one after the other would never
    // get there.
    let (gate, all_in_flight) = hold_every_run(&daemons);
    let released_together = tokio::spawn(async move {
        all_in_flight.await.expect("the watch ends");
        gate.cancel();
    });
    let recorded = Recorded::start(fixture.host.reporter());

    let outcome = run(&fixture, &["--shards", "2", "--holder", "suite", "--lane", "ui"])
        .await
        .unwrap_or_else(|refusal| panic!("both shards passed, so the command succeeds: {refusal:?}"));
    released_together.await.unwrap();
    let data = data_of(&outcome);
    assert_eq!((data.shard_count, data.aggregate.status), (2, Status::Passed));
    // Both daemons were asked to run: one shard's work is not the other's, which is the whole point of the fan-out.
    for daemon in &daemons {
        assert_eq!(runs(daemon), 1);
    }
    // One build for every shard, with the pool's shared digest cache: the Bazel outputs are identical for every
    // worker of one guest.
    assert_eq!(fixture.bazel.build_calls(), builds_before + 1, "the command builds once");
    // The heaviest bin is the include shard and the lightest the remainder, laid onto the leases in order.
    let laid: Vec<(ShardKind, &str)> = data.plan.shards.iter().map(|shard| (shard.kind, shard.worker.as_str())).collect();
    assert_eq!(
        laid,
        [
            (ShardKind::Include, workers[0].as_str()),
            (ShardKind::Remainder, workers[1].as_str())
        ]
    );
    // Every lease this command placed is given back, and each row says so of itself.
    assert_eq!(data.lease_disposition, Some(LeaseDisposition::Released));
    assert!(data.release_failures.is_empty());
    assert!(data.leases.iter().all(|lease| lease.released), "{:?}", data.leases);
    for worker in &workers {
        assert!(!fixture.leased(worker), "{worker} is still leased after the run finished");
    }
    assert!(Path::new(&data.aggregate_path).is_file(), "the aggregate is a file");
    // The verdict names the split and the result, and it is the answer: the outcome has no text of its own.
    let verdict = recorded.verdict().expect("the command publishes a verdict");
    assert_eq!(verdict.status, Status::Passed);
    let split = verdict.shards.expect("the verdict names the split");
    assert_eq!((split.count, split.labels.len()), (2, 2));
    assert!(split.labels[1].ends_with("+rest"), "{:?}", split.labels);
    assert_eq!(verdict.report.as_deref(), Some(data.aggregate_path.as_str()));
    assert!(verdict.lease.expect("the verdict names the leases").released());
    assert_eq!(outcome.text, "");
    // The progress records name each shard as it starts and as it finishes.
    let records: Vec<serde_json::Value> = recorded.data_of(Kind::Run);
    for event in ["shardStarted", "shardFinished"] {
        assert_eq!(records.iter().filter(|record| record["event"] == event).count(), 2, "{event}");
    }
}

// `--count` is a ceiling by design, and remainder-by-exclusion is what makes a narrower split correct rather than
// merely acceptable: fewer shards is the same lane, differently divided.
#[tokio::test]
async fn a_narrower_held_set_is_re_planned_rather_than_run_short() {
    let fixture = Fixture::pool().await;
    seed_baseline(
        &fixture,
        &[
            ("com.example.Shard1Test", 3_000.0),
            ("com.example.Shard2Test", 2_000.0),
            ("com.example.Shard3Test", 1_000.0),
        ],
    );
    warm_pool(&fixture).await;

    // No holder is named, so the run id is the holder.
    let outcome = run(&fixture, &["--shards", "3", "--lane", "ui"])
        .await
        .unwrap_or_else(|refusal| panic!("a two-worker pool still runs the whole lane: {refusal:?}"));
    let data = data_of(&outcome);
    assert_eq!((data.requested_shards, data.shard_count), (3, 2));
    // The run id: the user, the command and a uuid.
    assert!(
        data.holder.starts_with("suite-user-shard-") && data.holder.len() == "suite-user-shard-".len() + 36,
        "an unnamed holder is the run id: {}",
        data.holder
    );
    // The remainder still excludes exactly the include shard's classes, so the union is the lane whatever the pool
    // could give: the third class the plan no longer names runs on the remainder.
    let [include, remainder] = data.plan.shards.as_slice() else {
        panic!("two shards, was {:?}", data.plan.shards);
    };
    assert_eq!(include.junit5_filters.len(), 1);
    assert!(include.junit5_filters[0].starts_with("include-classname="));
    assert_eq!(remainder.junit5_filters.len(), 1);
    assert!(remainder.junit5_filters[0].contains("Shard1Test"), "{:?}", remainder.junit5_filters);
    let said = fixture.stdout.text() + &fixture.stderr.text();
    assert!(said.contains("re-balanced for 2"), "the narrowing is announced: {said}");
}

#[tokio::test]
async fn a_daemon_that_dies_mid_stream_cannot_make_the_run_green() {
    let fixture = Fixture::pool().await;
    let workers = fixture.workers();
    seed_baseline(&fixture, &TWO_CLASSES);
    let daemons = warm_pool(&fixture).await;
    {
        // Half a stream: the record that names the iteration, and then nothing. That is the evidence state a
        // daemon whose watchdog exited leaves behind.
        let mut dying = daemons[1].script();
        dying.run_lines.truncate(1);
        dying.close_mid_stream = true;
        dying.results_status = Some(404);
    }

    let refusal = refused(run(&fixture, &["--shards", "2", "--holder", "suite", "--lane", "ui"]).await);
    // A dead shard can never be averaged away, and this is the merge saying so rather than this command re-deriving
    // it.
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("shard_infrastructure_error", Exit::SOFTWARE)
    );
    let data = details_of(&refusal);
    assert_eq!(data.aggregate.status, Status::InfrastructureError);
    // One shard's death did not unwind its sibling: both daemons were asked to run, and the healthy shard's report is
    // on disk and green, which is what keeps a post-mortem possible after a partial failure.
    assert_eq!((runs(&daemons[0]), runs(&daemons[1])), (1, 1));
    let healthy = data
        .aggregate
        .entries
        .iter()
        .find(|entry| entry.entry.worker == workers[0])
        .expect("the healthy shard has an entry");
    assert_eq!(healthy.entry.status, EntryStatus::Passed);
    let report = healthy.entry.report_path.as_deref().expect("the healthy shard reported");
    assert!(Path::new(report).is_file(), "the healthy shard's report is on disk");
    // Every lease this command placed is given back even though the run failed: a shard set that keeps its workers
    // on failure is a pool that drains one bad run at a time.
    assert_eq!(data.lease_disposition, Some(LeaseDisposition::Released));
    for worker in &workers {
        assert!(!fixture.leased(worker), "{worker} is still leased after a failed run");
    }
    // ...and the aggregate is beside the reports rather than inside them, so the next split is not balanced
    // against a merged verdict.
    assert!(
        Path::new(&data.aggregate_path).starts_with(fixture.settings.runtime_root.join("aggregates")),
        "{}",
        data.aggregate_path
    );
    let baseline = read_shard_baseline(&fixture.settings);
    assert!(!baseline.durations.is_empty(), "the scan still reads the run reports");
    assert!(
        baseline.durations.iter().all(|entry| !entry.class_name.contains("shard")),
        "a merged verdict is never read back as a duration measurement: {:?}",
        baseline.durations
    );
}

// The defect this closes: the envelope wrote `released` whether or not anything went back, so a worker the pool had
// lost was reported as free and rediscovered later as an unexplained `pool_exhausted`.
//
// The refusal is a real one. A worker with a live run process is released through the guest rather than through the
// stopped-worker shortcut, and this fixture's guest answers with silence, which is not an answer the release gate
// accepts - so that worker stays held while its sibling goes back.
#[tokio::test]
async fn a_refused_release_is_not_reported_as_released() {
    let fixture = Fixture::pool().await;
    let workers = fixture.workers();
    seed_baseline(&fixture, &TWO_CLASSES);
    warm_pool(&fixture).await;
    let stuck = &workers[1];
    fixture.read_as_running(stuck).await;
    let recorded = Recorded::start(fixture.host.reporter());

    // Both shards passed, so the run is green: a lane that answered and could not give a worker back is a green lane
    // with a housekeeping problem, and turning it red would make an agent re-run a suite that already answered.
    let outcome = run(&fixture, &["--shards", "2", "--holder", "suite", "--lane", "ui"])
        .await
        .unwrap_or_else(|refusal| panic!("a release failure must not turn a green lane red: {refusal:?}"));
    // The release phase fails where a person reads it, and names the command that frees the worker.
    let release = recorded
        .data_of::<PhaseChange>(Kind::Phase)
        .into_iter()
        .rfind(|change| change.phase == Phase::Release)
        .expect("a release phase");
    assert_eq!(release.state, PhaseState::Failed);
    assert!(
        release.error.as_deref().is_some_and(|error| error.contains("lease release")),
        "{release:?}"
    );
    let data = data_of(&outcome);
    assert_eq!(data.lease_disposition, Some(LeaseDisposition::ReleaseFailed));
    assert_eq!(data.release_failures.len(), 1, "{:?}", data.release_failures);
    assert!(data.release_failures[0].contains(stuck.as_str()), "{:?}", data.release_failures);
    for lease in &data.leases {
        assert_eq!(lease.released, lease.worker != *stuck, "{lease:?}");
    }
    // The worker that refused is still leased, and the verdict names its receipt: the disposition says a lease was
    // kept and only the receipt says how to free it.
    assert!(fixture.leased(stuck), "a refused release leaves the lease in place");
    assert!(
        !fixture.leased(&workers[0]),
        "one refusal must not stop the other worker from going back"
    );
    let held = data
        .leases
        .iter()
        .find(|lease| lease.worker == *stuck)
        .expect("the stuck worker has a row");
    let lease = recorded
        .verdict()
        .and_then(|verdict| verdict.lease)
        .expect("the verdict names the leases");
    assert_eq!(lease.disposition, LeaseDisposition::ReleaseFailed);
    assert_eq!(lease.receipts, std::slice::from_ref(&held.lease_file));
}

// The aggregate has to be written even when the run is abandoned. A half-finished sharded run whose evidence was
// discarded - reports on disk and nothing that says they were ever one run - is the failure this command is arranged
// against, and the default signal disposition is exactly that.
#[tokio::test]
async fn an_interrupt_keeps_the_leases_and_still_writes_the_aggregate() {
    let fixture = Fixture::pool().await;
    let workers = fixture.workers();
    seed_baseline(&fixture, &TWO_CLASSES);
    let daemons = warm_pool(&fixture).await;
    // The signal arrives once both iterations are in flight, which neither will leave by itself.
    let (gate, all_in_flight) = hold_every_run(&daemons);
    let interrupter = {
        let interrupts = fixture.interrupts.clone();
        tokio::spawn(async move {
            all_in_flight.await.expect("the watch ends");
            interrupts.deliver(Signal::Interrupt);
        })
    };

    let result = run(&fixture, &["--shards", "2", "--holder", "suite", "--lane", "ui"]).await;
    interrupter.await.unwrap();
    gate.cancel();
    let refusal = refused(result);
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("shard_interrupted", Exit::SOFTWARE));
    assert!(
        refusal.message.starts_with("SIGINT abandoned the run after 0 of 2"),
        "{}",
        refusal.message
    );
    let data = details_of(&refusal);
    assert_eq!(data.interrupted.as_deref(), Some("SIGINT"));
    // An interrupted run keeps its leases: a release takes the worker's lifecycle lock and proves no run is active,
    // and neither is true of a shard whose iteration is still in flight.
    assert_eq!(data.lease_disposition, Some(LeaseDisposition::Kept));
    for worker in &workers {
        assert!(fixture.leased(worker), "{worker} must still be leased so the caller can free it");
    }
    // The receipts are in the reply and in the message, which is how the caller frees them - and no row claims a
    // release.
    assert_eq!(data.leases.len(), 2);
    for lease in &data.leases {
        assert!(!lease.released, "{lease:?}");
        assert!(Path::new(&lease.lease_file).is_file(), "{lease:?}");
        assert!(
            refusal
                .message
                .contains(&format!("--lease-file {} lease release", lease.lease_file)),
            "{}",
            refusal.message
        );
    }
    assert!(!refusal.message.contains("FILE"), "{}", refusal.message);
    // And the aggregate is on disk, with the abandoned shards named rather than missing.
    assert!(
        Path::new(&data.aggregate_path).is_file(),
        "the aggregate is written before the refusal"
    );
    assert_eq!(data.aggregate.status, Status::InfrastructureError);
    for entry in &data.aggregate.entries {
        assert_eq!(
            entry.entry.error.as_ref().map(|error| error.code.as_str()),
            Some("shard_interrupted"),
            "{entry:?}"
        );
    }
}
