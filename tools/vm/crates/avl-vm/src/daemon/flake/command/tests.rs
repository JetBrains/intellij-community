//! The trial chains against the shared daemon fixture, and the source guard that keeps a chain lock-free.

#![expect(
    clippy::float_cmp,
    reason = "a fake clock makes every duration and rate exact, so an epsilon would hide a wrong formula"
)]

use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::Duration;

use avl_base::{FakeClock, GuestOs};
use avl_host_sys::Signal;
use avl_wire::progress::{Kind, Phase, PhaseChange, PhaseState};
use pretty_assertions::assert_eq;

use super::*;
use crate::daemon::fixture::{Fixture, passing_run};
use crate::daemon::shard::read_shard_baseline;
use crate::daemon::testing::{FakeDaemon, PASSING_XML, Recorded, passing_run_lines};

// --- the decisions -------------------------------------------------------------------------------------------

#[test]
fn the_decisions_of_a_measurement_need_no_worker() {
    assert_eq!(positional_evidence(1), "chain_ordered");
    assert_eq!(positional_evidence(3), "interleaved_workers");

    let releases = [
        ReleaseResult {
            worker: "air-1".to_owned(),
            receipt: PathBuf::from("/r/lease-1.json"),
            released: true,
            failure: None,
        },
        ReleaseResult {
            worker: "air-2".to_owned(),
            receipt: PathBuf::from("/r/lease-2.json"),
            released: false,
            failure: Some("lease_changed".to_owned()),
        },
    ];
    assert_eq!(release_failures(&releases), ["air-2 (/r/lease-2.json): lease_changed"]);

    let refusal = flake_interrupted("SIGINT", vec!["air-1".to_owned(), "air-2".to_owned()], &[], "unused");
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("flake_interrupted", Exit::SOFTWARE));
    assert_eq!(
        refusal.message,
        "SIGINT abandoned the run on air-1, air-2; it took no lease of its own"
    );
    let (outcomes, failures) = settle_chains(&[], Vec::new());
    assert!(outcomes.is_empty() && failures.is_empty());
}

/// `vm flake`, as far as its own options go.
#[derive(clap::Parser, Debug)]
struct Probe {
    #[command(flatten)]
    args: FlakeArgs,
}

fn flake_args(argv: &[&str]) -> FlakeArgs {
    <Probe as clap::Parser>::try_parse_from(std::iter::once("flake").chain(argv.iter().copied()))
        .unwrap_or_else(|error| panic!("{argv:?} did not parse: {error}"))
        .args
}

async fn bounded<T>(future: impl Future<Output = T>) -> T {
    tokio::time::timeout(Duration::from_secs(120), future)
        .await
        .expect("the flake run settles")
}

/// `flake` as the controller runs it: the wall clock and the live reset.
async fn run(fixture: &Fixture, argv: &[&str], lease_file: Option<&Path>) -> Result<Outcome, Refusal> {
    bounded(command_flake(&Ctx::background(), &fixture.host, flake_args(argv), lease_file)).await
}

/// `flake` over the suite's clock and reset effects.
async fn run_with(
    fixture: &Fixture,
    argv: &[&str],
    lease_file: &Path,
    clock: &FakeClock,
    reset: &impl ResetIo,
) -> Result<Outcome, Refusal> {
    let factory = |_: &Lease, _: &Arc<PreparedBuild>| reset;
    bounded(flake(
        &Ctx::background(),
        &fixture.host,
        &flake_args(argv),
        Some(lease_file),
        clock,
        &factory,
    ))
    .await
}

fn refused(result: Result<Outcome, Refusal>) -> Refusal {
    match result {
        Ok(outcome) => panic!("the command was expected to refuse and answered {outcome:?}"),
        Err(refusal) => refusal,
    }
}

fn data_of(outcome: &Outcome) -> FlakeData {
    serde_json::from_value(outcome.data.clone()).expect("the outcome is flake data")
}

fn details_of(refusal: &Refusal) -> FlakeData {
    serde_json::from_value(refusal.details().expect("the refusal carries details")).expect("the details are flake data")
}

fn runs(daemon: &FakeDaemon) -> usize {
    daemon
        .script()
        .requests
        .iter()
        .filter(|request| request.as_str() == "POST /run")
        .count()
}

/// The first worker warm and leased by the suite: what an agent holding a receipt hands `flake`.
async fn leased_warm_worker(fixture: &Fixture) -> PathBuf {
    let prep = fixture.pool_build().await;
    fixture.warm_daemon(&fixture.worker, &prep).await;
    fixture.leased_by(&fixture.worker, "suite", GuestOs::Macos)
}

/// One green trial per entry of `iterations`, on the first worker's double, in order.
fn passing_trials(fixture: &Fixture, iterations: &[&str]) {
    let streams = iterations.iter().map(|id| passing_run_lines(id)).collect();
    let documents = vec![PASSING_XML; iterations.len()];
    fixture.queue_runs(streams, &documents);
}

/// A daemon that walks away with no transcript at all, which is one unusable trial.
const DEAD: &str = "";

fn trials(fixture: &Fixture, script: &[&str]) {
    let streams = script
        .iter()
        .map(|id| if id.is_empty() { Vec::new() } else { passing_run_lines(id) })
        .collect();
    let documents: Vec<&str> = script.iter().filter(|id| !id.is_empty()).map(|_| PASSING_XML).collect();
    fixture.queue_runs(streams, &documents);
}

// --- a measurement ------------------------------------------------------------------------------------------------

#[tokio::test]
async fn two_green_trials_on_one_worker_publish_a_quotable_rate() {
    let fixture = Fixture::pool().await;
    let receipt = leased_warm_worker(&fixture).await;
    passing_trials(&fixture, &["iter-1", "iter-2"]);
    let builds_before = fixture.bazel.build_calls();

    let outcome = run(&fixture, &["--lane", "ui", "--trials", "2"], Some(&receipt))
        .await
        .unwrap_or_else(|refusal| panic!("two green trials are a measurement: {refusal:?}"));
    let data = data_of(&outcome);
    assert_eq!((data.summary.attempted_trials, data.summary.authoritative_trials), (2, 2));
    assert!(data.summary.reportable, "{:?}", data.summary.not_reportable_reason);
    assert_eq!(data.summary.lane_flake_rate, 0.0, "a stable class publishes a zero rate");
    assert_eq!(runs(&fixture.daemon), 2, "one iteration per trial");
    // One build for the whole run: a rebuild between trials would make the K reports a sample over two builds.
    assert_eq!(fixture.bazel.build_calls(), builds_before + 1);
    // The caller's own receipt is never released: an agent driving this already holds a lease, and taking it back is
    // a race with whoever else is waiting for the pool.
    assert!(fixture.leased(&fixture.worker), "the caller's lease survives its own flake run");
    assert!(receipt.is_file(), "...and so does its receipt");
    // Chain order is ordinal order on one worker, and the reply says so rather than leaving it to be assumed.
    assert_eq!(data.positional_evidence, "chain_ordered");
    assert_eq!(data.order_probe_note, FLAKE_ORDER_PROBE_NOTE);
    assert_eq!(data.trial_plan.len(), 2);
    assert_eq!(data.trial_plan[1].chain_position, 2);
    assert!(data.chain_failures.is_empty(), "{:?}", data.chain_failures);
    assert!(data.release_failures.is_empty(), "{:?}", data.release_failures);
}

// A flaky lane is a successful measurement; an unreportable one is not. Answering "not a sample anyone may quote"
// with exit 0 and a `laneFlakeRate` field is how such a number gets quoted anyway.
#[tokio::test]
async fn an_unreportable_run_leaves_at_seventy_rather_than_zero() {
    let fixture = Fixture::pool().await;
    let receipt = leased_warm_worker(&fixture).await;
    trials(&fixture, &[DEAD]);

    let refusal = refused(run(&fixture, &["--lane", "ui", "--trials", "1"], Some(&receipt)).await);
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("flake_not_reportable", Exit::SOFTWARE));
    let data = details_of(&refusal);
    assert!(!data.summary.reportable);
    assert!(data.summary.not_reportable_reason.is_some());
    // The refusal carries the whole text, so the reader has the per-class evidence the headline was withheld from.
    assert!(refusal.message.contains("reportable=false"), "{}", refusal.message);
}

// Not zero, because one dead trial is what the exclusion list is for, and not unbounded either: a worker whose daemon
// will not come back produces K identical exclusions. The trials never attempted are simply absent.
#[tokio::test]
async fn a_chain_breaks_at_two_consecutive_unusable_trials_and_the_rest_are_absent() {
    let fixture = Fixture::pool().await;
    let receipt = leased_warm_worker(&fixture).await;
    trials(&fixture, &["iter-1", DEAD, DEAD, DEAD, DEAD]);

    let refusal = refused(run(&fixture, &["--lane", "ui", "--trials", "5"], Some(&receipt)).await);
    // Two of three attempted are excluded, past the honesty guard, so the run is unreportable rather than a rate over
    // one surviving trial.
    assert_eq!(refusal.code, "flake_not_reportable");
    let data = details_of(&refusal);
    assert_eq!(data.summary.attempted_trials, 3, "one good trial and two dead ones of five");
    assert_eq!(
        runs(&fixture.daemon),
        3,
        "the chain stopped asking after the second consecutive failure"
    );
    assert_eq!(data.summary.infrastructure_trials.len(), 2);
    // The plan still names all five, so a reader can see what was not attempted.
    assert_eq!(data.trial_plan.len(), 5);
    let said = fixture.stdout.text() + &fixture.stderr.text();
    assert!(said.contains("2 trial(s) were not attempted"), "giving up is announced: {said}");
}

// --- the leases ---------------------------------------------------------------------------------------------------

// The one place a mixed set can arise: an acquisition names the invoked guest for every lease it places, but a receipt
// handed in on the command line says nothing about which guest the controller was invoked for.
#[tokio::test]
async fn a_receipt_from_another_guest_cannot_be_chained_with_this_runs_own_workers() {
    let fixture = Fixture::pool().await;
    let prep = fixture.pool_build().await;
    fixture.warm_daemon(&fixture.worker, &prep).await;
    let receipt = fixture.leased_by(&fixture.worker, "suite", GuestOs::Linux);
    let builds_before = fixture.bazel.build_calls();

    let refusal = refused(run(&fixture, &["--lane", "ui", "--trials", "2", "--workers", "2"], Some(&receipt)).await);
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("lease_guest_os_mismatch", Exit::NO_PERM));
    assert_eq!(runs(&fixture.daemon), 0, "the refusal costs no trial");
    // ...and nothing else: the receipt is checked before the build and before a worker is acquired.
    assert_eq!(fixture.bazel.build_calls(), builds_before, "the refusal costs no build");
    for worker in fixture.workers().iter().filter(|worker| **worker != fixture.worker) {
        assert!(!fixture.leased(worker), "{worker} was acquired for nothing");
    }
}

// The measurement is already made when the release runs, so a refusal must not cost the rate - but it must not be
// invisible either: a worker this run took and could not give back is a slot the pool has lost.
//
// The refusal is a real one: a worker with a live run process is released through the guest, and this fixture's guest
// answers with silence, which the release gate does not accept.
#[tokio::test]
async fn a_release_this_run_cannot_complete_is_named_rather_than_swallowed() {
    let fixture = Fixture::pool().await;
    let workers = fixture.workers();
    let borrowed = &workers[1];
    let prep = fixture.pool_build().await;
    for worker in &workers {
        let daemon = fixture.warm_daemon(worker, &prep).await;
        let (lines, xml) = passing_run(&format!("iter-{worker}"), "com.example.OneTest");
        let mut script = daemon.script();
        script.run_lines = lines;
        script.results_xml = xml.into_bytes();
    }
    let receipt = fixture.leased_by(&workers[0], "suite", GuestOs::Macos);
    fixture.read_as_running(borrowed).await;
    let recorded = Recorded::start(fixture.host.reporter());

    let outcome = run(&fixture, &["--lane", "ui", "--trials", "2", "--workers", "2"], Some(&receipt))
        .await
        .unwrap_or_else(|refusal| panic!("a release failure must not cost the measurement: {refusal:?}"));
    let data = data_of(&outcome);
    assert_eq!(data.workers, workers, "the receipt's worker, then the one this run borrowed");
    assert_eq!(data.release_failures.len(), 1, "{:?}", data.release_failures);
    assert!(data.release_failures[0].contains(borrowed.as_str()), "{:?}", data.release_failures);
    // ...with the handle that frees it, and the lease still in place for that handle to act on.
    assert!(fixture.leased(borrowed), "a refused release leaves the lease in place");
    // The release phase fails where a person reads it, naming the command that frees the worker; the measurement
    // and the exit are unchanged.
    let release = recorded
        .data_of::<PhaseChange>(Kind::Phase)
        .into_iter()
        .rfind(|change| change.phase == Phase::Release)
        .expect("a release phase");
    assert_eq!(release.state, PhaseState::Failed);
    let error = release.error.unwrap_or_default();
    assert!(
        error.contains("lease_still_held") || error.contains("the lease is still held"),
        "{error}"
    );
    let held = data.release_failures[0]
        .split_once(" (")
        .and_then(|(_, rest)| rest.split_once("): "))
        .map(|(receipt, _)| receipt.to_owned())
        .expect("a release failure reads `worker (receipt): message`");
    assert!(error.contains(&held), "{error}");
    // Only what this run acquired is released: the caller's receipt and lease were never this run's to give back.
    assert!(receipt.is_file(), "the caller's receipt survives its own flake run");
    assert!(fixture.leased(&workers[0]), "the caller's lease survives its own flake run");
}

// The interrupt policy is the driver's, for every command: the chains in flight are dropped, the leases this run took
// are kept, because a release proves no run is active and an abandoned trial is one, and the refusal names each
// receipt. No summary is written: every dropped chain lost its whole trial list, so what is left is a sample
// truncated by position.
#[tokio::test]
async fn an_interrupt_keeps_the_leases_this_run_took_and_writes_no_summary() {
    let fixture = Fixture::pool().await;
    let workers = fixture.workers();
    let acquired = &workers[1];
    let prep = fixture.pool_build().await;
    let gate = tokio_util::sync::CancellationToken::new();
    let mut daemons = Vec::new();
    for worker in &workers {
        let daemon = fixture.warm_daemon(worker, &prep).await;
        let (lines, xml) = passing_run(&format!("iter-{worker}"), "com.example.OneTest");
        {
            let mut script = daemon.script();
            script.run_lines = lines;
            script.results_xml = xml.into_bytes();
            script.block = Some(gate.clone());
        }
        daemons.push(daemon);
    }
    let receipt = fixture.leased_by(&workers[0], "suite", GuestOs::Macos);
    let recorded = Recorded::start(fixture.host.reporter());
    // The signal arrives once the acquired worker's trial is in flight, which it will not leave by itself.
    let interrupter = {
        let daemon = Arc::clone(&daemons[1]);
        let interrupts = fixture.interrupts.clone();
        tokio::spawn(async move {
            while !daemon.saw_request("POST /run") {
                tokio::time::sleep(Duration::from_millis(10)).await;
            }
            interrupts.deliver(Signal::Interrupt);
        })
    };

    let result = run(&fixture, &["--lane", "ui", "--trials", "2", "--workers", "2"], Some(&receipt)).await;
    interrupter.await.unwrap();
    gate.cancel();
    let refusal = refused(result);
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("flake_interrupted", Exit::SOFTWARE));
    assert!(refusal.message.starts_with("SIGINT abandoned the run"), "{}", refusal.message);
    let details: FlakeInterruptedData =
        serde_json::from_value(refusal.details().expect("the refusal carries details")).expect("the details are the interrupt's");
    assert_eq!(details.interrupted, "SIGINT");
    assert_eq!(details.workers, workers);
    // Only the lease this run took is named, and kept for the caller to free.
    let [lease] = details.leases.as_slice() else {
        panic!("one acquired lease, was {:?}", details.leases);
    };
    assert_eq!(lease.worker, *acquired);
    assert!(!lease.released);
    assert!(Path::new(&lease.lease_file).is_file(), "{lease:?}");
    assert!(
        refusal
            .message
            .contains(&format!("--lease-file {} lease release", lease.lease_file)),
        "{}",
        refusal.message
    );
    assert!(fixture.leased(acquired), "the acquired lease is kept");
    // The caller's receipt was never this run's to give back.
    assert!(receipt.is_file(), "the caller's receipt survives");
    assert!(fixture.leased(&workers[0]), "the caller's lease survives");
    // No summary: a truncated sample is not one.
    let aggregates = fixture.settings.runtime_root.join("aggregates");
    let summaries: Vec<PathBuf> = std::fs::read_dir(&aggregates)
        .map(|entries| {
            entries
                .flatten()
                .map(|entry| entry.path().join("flake.json"))
                .filter(|path| path.is_file())
                .collect()
        })
        .unwrap_or_default();
    assert_eq!(summaries, Vec::<PathBuf>::new());
    assert!(recorded.verdict().is_none(), "flake never publishes a verdict");
}

// --- the summary's home -------------------------------------------------------------------------------------------

// A summary an agent can only read out of an envelope is a measurement that does not survive the process. It goes into
// the tree a sharded run's verdict does, because both are one run's merged answer over several workers.
#[tokio::test]
async fn a_flake_run_persists_its_summary_beside_the_shard_aggregates() {
    let fixture = Fixture::pool().await;
    let receipt = leased_warm_worker(&fixture).await;
    passing_trials(&fixture, &["iter-1", "iter-2"]);

    let outcome = run(&fixture, &["--lane", "ui", "--trials", "2"], Some(&receipt))
        .await
        .unwrap_or_else(|refusal| panic!("two green trials are a measurement: {refusal:?}"));
    let data = data_of(&outcome);
    let shaped = data
        .flake_run_id
        .strip_prefix("flake-")
        .and_then(|rest| rest.split_once('-'))
        .is_some_and(|(stamp, tail)| {
            stamp.bytes().all(|byte| byte.is_ascii_digit()) && tail.len() == 8 && tail.bytes().all(|byte| byte.is_ascii_hexdigit())
        });
    assert!(shaped, "a millisecond stamp and an eight-character tail: {}", data.flake_run_id);
    let expected = fixture
        .settings
        .runtime_root
        .join("aggregates")
        .join(&data.flake_run_id)
        .join("flake.json");
    assert_eq!(Path::new(&data.aggregate_path), expected);
    let content = std::fs::read_to_string(&data.aggregate_path).expect("the summary is a file");
    assert!(content.contains(r#""kind": "flake""#), "{content}");
    // The `--text` reader gets the path too, on its last line, which is how somebody comes back to the run.
    assert!(
        outcome.text.ends_with(&format!("aggregate {}", data.aggregate_path)),
        "{}",
        outcome.text
    );
}

// Why the tree is outside `workers/*/reports/`: the shard planner scans that tree for class durations, and a merged
// answer written into it would be read back as another measurement.
#[tokio::test]
async fn the_duration_baseline_does_not_read_a_flake_summary() {
    let fixture = Fixture::pool().await;
    let receipt = leased_warm_worker(&fixture).await;
    passing_trials(&fixture, &["iter-1", "iter-2"]);

    let outcome = run(&fixture, &["--lane", "ui", "--trials", "2"], Some(&receipt))
        .await
        .unwrap_or_else(|refusal| panic!("two green trials are a measurement: {refusal:?}"));
    assert!(Path::new(&data_of(&outcome).aggregate_path).is_file());
    // The trials measured `com.example.MyTest`, so the scan finds that class; what it must never find is anything
    // the summary contributed.
    let baseline = read_shard_baseline(&fixture.settings);
    assert!(
        baseline.durations.iter().all(|entry| !entry.class_name.contains("flake")),
        "{:?}",
        baseline.durations
    );
    assert_eq!(baseline.scanned_reports, 2, "the scan reads the two trial reports and nothing else");
}

// --- the reset, in a chain ----------------------------------------------------------------------------------------

/// Records the reset's effects and charges the clock for each, which is how a suite asks whether the reset lands
/// inside the trial's own duration.
struct ClockCharging<'a> {
    clock: &'a FakeClock,
    observed: Mutex<Vec<&'static str>>,
}

impl ClockCharging<'_> {
    fn record(&self, name: &'static str) -> impl Future<Output = Result<(), Refusal>> {
        self.observed.lock().unwrap().push(name);
        self.clock.advance(Duration::from_secs(47));
        std::future::ready(Ok(()))
    }
}

impl ResetIo for ClockCharging<'_> {
    fn ide_stop(&self, _ctx: &Ctx) -> impl Future<Output = Result<(), Refusal>> {
        self.record("ide_stop")
    }

    fn daemon_stop(&self, _ctx: &Ctx) -> impl Future<Output = Result<(), Refusal>> {
        self.record("daemon_stop")
    }

    fn guest_remove(&self, _ctx: &Ctx, _argv: &[String]) -> impl Future<Output = Result<(), Refusal>> {
        self.record("guest_remove")
    }

    fn daemon_start(&self, _ctx: &Ctx) -> impl Future<Output = Result<(), Refusal>> {
        self.record("daemon_start")
    }
}

// It runs before *every* trial, the first included, so that trial 1 is the same treatment as trial 12 - an asymmetric
// first trial is precisely what the order signature would read as a positional signal.
#[tokio::test]
async fn the_reset_runs_before_every_trial_including_the_first_and_outside_its_clock() {
    let fixture = Fixture::pool().await;
    let receipt = leased_warm_worker(&fixture).await;
    passing_trials(&fixture, &["iter-1", "iter-2"]);
    let clock = FakeClock::at("2026-08-23T12:00:00Z");
    let reset = ClockCharging {
        clock: &clock,
        observed: Mutex::default(),
    };

    let outcome = run_with(
        &fixture,
        &["--lane", "ui", "--trials", "2", "--reset", "daemon"],
        &receipt,
        &clock,
        &reset,
    )
    .await
    .unwrap_or_else(|refusal| panic!("two green trials under a daemon reset are a measurement: {refusal:?}"));
    // Four steps per trial, twice, in the one order that does not delete a live IDE's own state.
    let cycle = ["ide_stop", "daemon_stop", "guest_remove", "daemon_start"];
    assert_eq!(*reset.observed.lock().unwrap(), [cycle, cycle].concat());
    let data = data_of(&outcome);
    assert_eq!(data.summary.trials.len(), 2);
    // A `daemon` reset is ~188 s; folding that into the trial's own duration would make every trial after a reset look
    // slow for a reason that has nothing to do with the tests.
    for trial in &data.summary.trials {
        assert_eq!(trial.duration_ms, 0.0, "trial {} ran inside the reset's clock", trial.ordinal);
        assert_eq!(
            trial.reset_policy,
            ResetPolicy::FreshWorker,
            "`daemon` is recorded as `fresh_worker`"
        );
    }
}

/// Refuses its first step, which is the one way a trial can fail without an iteration.
struct Refusing;

impl ResetIo for Refusing {
    fn ide_stop(&self, _ctx: &Ctx) -> impl Future<Output = Result<(), Refusal>> {
        std::future::ready(Err(Refusal::new(
            "daemon_ide_stop_failed",
            Exit::SOFTWARE,
            "/ide/stop returned 500",
        )))
    }

    fn daemon_stop(&self, _ctx: &Ctx) -> impl Future<Output = Result<(), Refusal>> {
        std::future::ready(Ok(()))
    }

    fn guest_remove(&self, _ctx: &Ctx, _argv: &[String]) -> impl Future<Output = Result<(), Refusal>> {
        std::future::ready(Ok(()))
    }

    fn daemon_start(&self, _ctx: &Ctx) -> impl Future<Output = Result<(), Refusal>> {
        std::future::ready(Ok(()))
    }
}

// A reset that cannot be applied is still one trial's worth of evidence, and it belongs in the exclusion list rather
// than aborting the run and discarding the trials already measured.
#[tokio::test]
async fn a_reset_that_cannot_be_applied_becomes_an_excluded_trial_rather_than_an_aborted_run() {
    let fixture = Fixture::pool().await;
    let receipt = leased_warm_worker(&fixture).await;
    let clock = FakeClock::at("2026-08-23T12:00:00Z");

    let refusal = refused(
        run_with(
            &fixture,
            &["--lane", "ui", "--trials", "3", "--reset", "daemon"],
            &receipt,
            &clock,
            &Refusing,
        )
        .await,
    );
    assert_eq!(
        refusal.code, "flake_not_reportable",
        "nothing was measured, so nothing may be published"
    );
    let data = details_of(&refusal);
    // Two consecutive unusable trials end the chain, so the third is absent rather than fabricated.
    assert_eq!(data.summary.attempted_trials, 2);
    for exclusion in &data.summary.infrastructure_trials {
        assert_eq!(
            exclusion.code, "daemon_ide_stop_failed",
            "the reset's own refusal names the exclusion"
        );
    }
    assert_eq!(runs(&fixture.daemon), 0, "a trial whose reset failed never ran");
}

// --- the source guard ---------------------------------------------------------------------------------------------

/// This module's own source, compiled in, so the guard can never scan a file that is not the one that runs.
const SOURCE: &str = include_str!("../command.rs");

/// Every non-test source of the `flake` module, by the name its `mod` declaration gives it, and the leased-run
/// driver that holds the chain's lock. The chain's reset and trial folding live in `plan`, and a call to a wrapper
/// there or in the driver would collide with the chain's lock just the same.
const MODULE_SOURCES: [(&str, &str); 4] = [
    ("flake", include_str!("../../flake.rs")),
    ("command", SOURCE),
    ("plan", include_str!("../plan.rs")),
    ("leased", include_str!("../../leased.rs")),
];

// A source guard rather than a behavioural one: this is an invariant of the chain, not something any single trial
// demonstrates. A second acquisition of one worker's lifecycle lock by the same live process is refused rather than
// reclaimed, so a command wrapper called inside the chain would fail `lease_busy` on the second trial, after the first
// had already paid for a build, a daemon start and a lane.
#[test]
fn the_trial_chain_cannot_reach_lease_busy_because_it_calls_no_command_wrapper() {
    let start = SOURCE
        .find("async fn run_trial_chain(")
        .expect("the chain must be findable by name for this guard to mean anything");
    let length = SOURCE[start..]
        .find("\n}\n")
        .expect("the chain's body must be delimited for this guard to mean anything");
    let chain = &SOURCE[start..start + length];
    // The scan must prove it reached the chain, and it cannot do that by finding nothing.
    assert!(
        chain.contains("run_one_iteration("),
        "the chain runs iterations directly, and this guard only means something if it found one"
    );
    let wrappers = command_wrapper_calls(chain);
    assert_eq!(wrappers, Vec::<&str>::new(), "the chain must call no command wrapper");
    // ...and the module never reaches the two whose lock would collide, so no future edit can call one without also
    // changing this guard's answer.
    for (name, source) in MODULE_SOURCES {
        for forbidden in ["command_run(", "command_daemon("] {
            assert!(
                !source.contains(forbidden),
                "{name}: {forbidden} takes the worker's lifecycle lock, which a chain already holds"
            );
        }
    }
    // A submodule added to the module is a source the list above does not scan until it names it.
    for (name, source) in MODULE_SOURCES {
        for line in source.lines() {
            let Some(declared) = line
                .trim_start()
                .trim_start_matches("pub ")
                .strip_prefix("mod ")
                .and_then(|rest| rest.strip_suffix(';'))
            else {
                continue;
            };
            assert!(
                declared == "tests" || MODULE_SOURCES.iter().any(|(known, _)| *known == declared),
                "{name} declares `mod {declared}`, which the guard does not scan"
            );
        }
    }
}

/// Every `command_<name>(` in `text` that starts a word: what `\bcommand_\w+\(` matches.
fn command_wrapper_calls(text: &str) -> Vec<&str> {
    let word = |byte: u8| byte.is_ascii_alphanumeric() || byte == b'_';
    text.match_indices("command_")
        .filter(|(start, _)| *start == 0 || !word(text.as_bytes()[start - 1]))
        .filter_map(|(start, _)| {
            let name_end = start + "command_".len() + text[start + "command_".len()..].bytes().take_while(|byte| word(*byte)).count();
            (name_end > start + "command_".len() && text[name_end..].starts_with('(')).then(|| &text[start..=name_end])
        })
        .collect()
}

#[test]
fn the_guard_scan_finds_a_command_wrapper_call() {
    assert_eq!(
        command_wrapper_calls("host.command_run(ctx); recommand_x(); command_(); command_daemon (x)"),
        ["command_run("]
    );
}
