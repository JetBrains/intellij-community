//! The `flake` command: one trial chain per worker, under the leased-run driver, and the reply.
//!
//! The driver holds each worker's lifecycle lock for the whole length of that worker's chain, so nothing can take the
//! worker mid-experiment - which is exactly why the chain calls the lock-free iteration and never a command wrapper.
//! A second acquisition of the same lock by the same live process is *refused, not reclaimed*, so a wrapper called
//! from inside a chain would fail `lease_busy` on the second trial. The suite pins that on this file's own text and
//! on the driver's.

use std::path::Path;
use std::sync::Arc;
use std::time::Duration;

use crate::worker::lease::{HeldWorker, ReleaseResult};
use crate::worker::worker::Lease;
use avl_base::format::seconds;
use avl_base::{Clock, Exit, Outcome, Refusal, Scope, SystemClock};
use avl_host_sys::guest::Guest;
use avl_host_sys::{Ctx, SpawnOptions};
use avl_report::aggregate::persist_flake_summary;
use avl_wire::progress::Event;
use avl_wire::report::{FlakeSummary, ResetPolicy};
use serde::{Deserialize, Serialize};
use serde_json::json;

use crate::daemon::flake::plan::{MAX_CONSECUTIVE_TRIAL_FAILURES, ResetIo, apply_reset, trial_was_usable};
use crate::daemon::flake::{
    FLAKE_ORDER_PROBE_NOTE, FlakeArgs, TrialAssignment, TrialOutcome, plan_flake_trials, render_flake_text, reset_steps, summarize_trials,
};
use crate::daemon::leased::Workers;
use crate::daemon::shard::command::LeaseData;
use crate::daemon::{Host, HostState, ParsedRun, PreparedBuild, RunAttempt, guest_free_bytes};
use crate::lane::secrets::RunSecrets;
use avl_base::RefusalExt;

/// The timeout of the guest removal between two trials. It removes the outputs of one trial, which hold its traces
/// and its IDE logs.
const TRIAL_REMOVE_TIMEOUT: Duration = Duration::from_mins(10);

#[cfg(test)]
#[cfg(unix)]
mod tests;

// --- the payload -------------------------------------------------------------------------------------------------

/// A worker whose chain never ran, or stopped for a reason that is not one trial's failure.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub(crate) struct ChainFailure {
    pub worker: String,
    pub code: String,
    pub message: String,
}

/// `flake`'s data payload.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct FlakeData {
    pub selection: String,
    /// `lane` or `selector`; see [`FLAKE_ORDER_PROBE_NOTE`].
    pub selection_shape: String,
    pub reset: String,
    pub reset_policy: ResetPolicy,
    pub requested_trials: u16,
    pub requested_workers: u8,
    pub workers: Vec<String>,
    pub trial_plan: Vec<TrialAssignment>,
    pub chain_failures: Vec<ChainFailure>,
    /// `chain_ordered` on one worker, `interleaved_workers` on several.
    pub positional_evidence: String,
    pub order_probe_note: String,
    pub summary: FlakeSummary,
    pub flake_run_id: String,
    pub aggregate_path: String,
    pub release_failures: Vec<String>,
}

/// `flake_interrupted`'s details: the signal, every worker the run chained on, and the leases it took, all of them
/// still held. A borrowed receipt is the caller's, so it is not among them.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct FlakeInterruptedData {
    pub interrupted: String,
    pub workers: Vec<String>,
    pub leases: Vec<LeaseData>,
}

// --- the command -------------------------------------------------------------------------------------------------

/// `flake`: K trials of one selection, spread over the workers it could lease, folded into a per-class rate.
///
/// `lease_file` is optional, and what this command acquired is all it releases. An agent driving this already holds a
/// lease, and asking it to release one so the harness can take it back is a race with whoever else is waiting for
/// the pool. So a receipt, when given, supplies the first worker, and the remaining `--workers N - 1` are acquired
/// under this run's id - and released again once every chain settled, while the caller's receipt is left as it was
/// found. The leases, the lock each chain holds and the release are the leased-run driver's, as they are `run`'s
/// and `shard`'s; the chain inside the lock still calls no command wrapper.
pub(crate) async fn command_flake(ctx: &Ctx, host: &Arc<Host>, args: FlakeArgs, lease_file: Option<&Path>) -> Result<Outcome, Refusal> {
    let live = |lease: &Lease, built: &Arc<PreparedBuild>| LiveReset {
        host: Arc::clone(host),
        lease: lease.clone(),
        built: Arc::clone(built),
    };
    flake(ctx, host, &args, lease_file, &SystemClock, &live).await
}

/// [`command_flake`] with its two seams: the trial clock, and the reset's effects on one leased worker.
pub(crate) async fn flake<I: ResetIo>(
    ctx: &Ctx,
    host: &Arc<Host>,
    args: &FlakeArgs,
    lease_file: Option<&Path>,
    clock: &dyn Clock,
    reset_io: &dyn Fn(&Lease, &Arc<PreparedBuild>) -> I,
) -> Result<Outcome, Refusal> {
    host.journaled(ctx, "flake", args.argv(), async |ctx: &Ctx, run_id: &str| {
        measure(ctx, host, args, lease_file, clock, reset_io, run_id).await
    })
    .await
}

async fn measure<I: ResetIo>(
    ctx: &Ctx,
    host: &Host,
    args: &FlakeArgs,
    lease_file: Option<&Path>,
    clock: &dyn Clock,
    reset_io: &dyn Fn(&Lease, &Arc<PreparedBuild>) -> I,
    run_id: &str,
) -> Result<Outcome, Refusal> {
    let parsed = host.parse_run(ctx, &args.run).await?;
    let workers = args.worker_count();
    let policy = args.reset.policy();
    let fresh_ide = parsed.fresh_ide || policy == ResetPolicy::FreshIde;
    // A count without `exact`: a narrower run beats a run that waits for a busy pool, and the trial plan spreads
    // over whatever came back. One build for the whole run, and the pool-wide scope because a flake run is a fan-out
    // by design: every trial has to measure the same product, and a rebuild between trials would make the K reports
    // a sample over two builds.
    let request = Workers {
        borrowed: lease_file,
        holder: None,
        count: workers,
        exact: false,
    };
    // One chain per worker, and no cancellation between them: a chain that could not take its worker's lifecycle
    // lock must not discard the trials the other chains already measured.
    let mut leased = host
        .leased_run(
            ctx,
            run_id,
            "flake",
            request,
            |held: &[HeldWorker]| Ok(plan_flake_trials(&names_of(held), usize::from(args.trials))),
            async |plan: &Vec<TrialAssignment>, _: usize, current: Lease, built: &Arc<PreparedBuild>| {
                let assignments: Vec<TrialAssignment> = plan
                    .iter()
                    .filter(|assignment| assignment.worker == current.worker)
                    .cloned()
                    .collect();
                let io = reset_io(&current, built);
                let trials = Trials {
                    host,
                    built,
                    parsed: &parsed,
                    policy,
                    fresh_ide,
                    clock,
                };
                run_trial_chain(ctx, &trials, &current, &assignments, &io).await
            },
        )
        .await?;
    let names = names_of(&leased.held);
    let reporter = host.reporter();
    let program = reporter.program();

    // Every chain was dropped with the fan-out, so each has lost its whole trial list: a summary of what is left
    // would be a sample truncated by position, which is the bias the order probe exists to see.
    if let Some(signal) = leased.interrupted {
        return Err(flake_interrupted(signal, names, leased.acquired(), &leased.release_hint(&program)));
    }

    let (outcomes, chain_failures) = settle_chains(&leased.held, std::mem::take(&mut leased.settled));
    let summary = summarize_trials(&outcomes, policy);
    // Filed beside the shard verdicts, and before the branches below: a summary an agent can only read out of an
    // envelope is a measurement that does not survive the process, and the unreportable case is exactly the one
    // somebody comes back to.
    let aggregate_id = format!("flake-{}-{}", reporter.now().as_millisecond(), &avl_base::new_id()[..8]);
    let aggregate_path = persist_flake_summary(host.settings(), &aggregate_id, &summary)?
        .to_string_lossy()
        .into_owned();
    reporter.note(format!("aggregate {aggregate_path}"), None);
    // Not fatal: the measurement is already made, and losing it to a release failure would be the more expensive of
    // the two problems. But not silent either - a worker this run took and could not give back is a slot the pool
    // has lost.
    let release_failures = release_failures(&leased.settlement.releases);
    let data = FlakeData {
        selection: parsed.selection.description.clone(),
        selection_shape: args.selection_shape().to_owned(),
        reset: args.reset.as_str().to_owned(),
        reset_policy: policy,
        requested_trials: args.trials,
        requested_workers: workers,
        workers: names,
        trial_plan: leased.plan,
        chain_failures,
        positional_evidence: positional_evidence(leased.held.len()).to_owned(),
        order_probe_note: FLAKE_ORDER_PROBE_NOTE.to_owned(),
        summary,
        flake_run_id: aggregate_id,
        aggregate_path,
        release_failures,
    };
    let text = render_flake_text(&data.selection, &data.reset, &data.workers, &data.summary, &data.aggregate_path);
    flake_verdict(&data, &text)?;
    Outcome::new(&data, text)
}

// --- the decisions of a measurement: values in, an answer out, no I/O ------------------------------------------

/// The refusal of a run that an interrupt abandoned. Every chain was dropped with the fan-out, so each has lost its
/// whole trial list: a summary of what is left would be a sample truncated by position, which is the bias the order
/// probe exists to see. `acquired` are the leases the run took itself, which stay held.
pub(crate) fn flake_interrupted(signal: &str, workers: Vec<String>, acquired: &[HeldWorker], release_hint: &str) -> Refusal {
    let leases: Vec<LeaseData> = acquired
        .iter()
        .map(|item| LeaseData {
            worker: item.lease.worker.clone(),
            lease_file: item.receipt.to_string_lossy().into_owned(),
            released: false,
        })
        .collect();
    let kept = if leases.is_empty() {
        "it took no lease of its own".to_owned()
    } else {
        format!("the leases it took are still held (release each with {release_hint})")
    };
    Refusal::new(
        "flake_interrupted",
        Exit::SOFTWARE,
        format!("{signal} abandoned the run on {}; {kept}", workers.join(", ")),
    )
    .with_details(&FlakeInterruptedData {
        interrupted: signal.to_owned(),
        workers,
        leases,
    })
}

/// The trials of the settled chains, and the chains that could not run. Only an interrupt leaves a chain unsettled,
/// and the driver answers that before it gets here.
pub(crate) fn settle_chains(
    held: &[HeldWorker],
    settled: Vec<Option<Result<Vec<TrialOutcome>, Refusal>>>,
) -> (Vec<TrialOutcome>, Vec<ChainFailure>) {
    let mut outcomes: Vec<TrialOutcome> = Vec::new();
    let mut chain_failures: Vec<ChainFailure> = Vec::new();
    for (item, chain) in held.iter().zip(settled) {
        match chain {
            Some(Ok(chain)) => outcomes.extend(chain),
            Some(Err(refusal)) => chain_failures.push(ChainFailure {
                worker: item.lease.worker.clone(),
                code: refusal.code.to_string(),
                message: refusal.message,
            }),
            None => {}
        }
    }
    (outcomes, chain_failures)
}

/// Each worker the run took and could not give back, with the receipt that still frees it. Not fatal: the
/// measurement is already made, and losing it to a release failure would be the more expensive of the two problems.
/// But not silent either: such a worker is a slot the pool has lost.
pub(crate) fn release_failures(releases: &[ReleaseResult]) -> Vec<String> {
    releases
        .iter()
        .filter(|result| !result.released)
        .map(|result| {
            format!(
                "{} ({}): {}",
                result.worker,
                result.receipt.display(),
                result.failure.as_deref().unwrap_or_default()
            )
        })
        .collect()
}

/// What the order of the trials can show: one chain on one worker is ordered, and several workers interleave.
pub(crate) const fn positional_evidence(workers: usize) -> &'static str {
    if workers == 1 { "chain_ordered" } else { "interleaved_workers" }
}

/// The verdict of a measurement: a chain that could not run refuses, and so does a summary that is not reportable.
///
/// A flaky lane is a successful measurement; an unreportable one is not. `reportable` is false exactly when the
/// surviving trials are not a sample anyone may quote, and answering that with exit 0 and a `laneFlakeRate` field is
/// how such a number gets quoted anyway.
pub(crate) fn flake_verdict(data: &FlakeData, text: &str) -> Result<(), Refusal> {
    if !data.chain_failures.is_empty() {
        let named: Vec<String> = data
            .chain_failures
            .iter()
            .map(|failure| format!("{}: {} {}", failure.worker, failure.code, failure.message))
            .collect();
        return Err(Refusal::new(
            "flake_chain_failed",
            Exit::SOFTWARE,
            format!(
                "{} of {} worker chain(s) could not run: {}",
                data.chain_failures.len(),
                data.workers.len(),
                named.join("; ")
            ),
        )
        .with_details(data));
    }
    if !data.summary.reportable {
        let reason = data
            .summary
            .not_reportable_reason
            .as_deref()
            .unwrap_or("this run did not produce a flake rate");
        return Err(Refusal::new("flake_not_reportable", Exit::SOFTWARE, format!("{reason}\n{text}")).with_details(data));
    }
    Ok(())
}

/// The workers of a held set, in its order.
fn names_of(held: &[HeldWorker]) -> Vec<String> {
    held.iter().map(|item| item.lease.worker.clone()).collect()
}

// --- one worker's chain -------------------------------------------------------------------------------------------

/// What every chain of one run shares.
struct Trials<'a> {
    host: &'a Host,
    built: &'a PreparedBuild,
    parsed: &'a ParsedRun,
    policy: ResetPolicy,
    fresh_ide: bool,
    clock: &'a dyn Clock,
}

/// Every trial assigned to one worker, under that worker's lifecycle lock, in order.
///
/// The lock-free iteration and nothing that takes a lock: see this module's header, and the source guard in its
/// suite. A failed iteration is a *value* here rather than an error, which is the whole reason K trials can be
/// reported at all - the first failure would otherwise unwind the chain and take the trials before it with it.
async fn run_trial_chain(
    ctx: &Ctx,
    trials: &Trials<'_>,
    lease: &Lease,
    assignments: &[TrialAssignment],
    io: &impl ResetIo,
) -> Vec<TrialOutcome> {
    let host = trials.host;
    let worker = &lease.worker;
    let scope = Scope::worker(worker);
    let steps = reset_steps(host.settings(), trials.policy);
    let mut outcomes = Vec::with_capacity(assignments.len());
    let mut consecutive_failures = 0;
    for assignment in assignments {
        // The reset runs before the trial and outside its clock: a `daemon` reset is ~188 s, and folding that into
        // the trial's own duration would make every trial after a reset look slow for a reason that has nothing to do
        // with the tests. It runs before *every* trial, the first included, so that trial 1 is the same treatment as
        // trial 12 - an asymmetric first trial is precisely what the order signature would read as a positional
        // signal. A trial whose reset failed therefore records a duration near zero, which is the honest figure: that
        // trial never ran.
        let free_before = guest_free_bytes_or_nothing(ctx, host, worker).await;
        let reset = apply_reset(ctx, &steps, io).await;
        let started = trials.clock.now().as_millisecond();
        let attempt = match reset {
            // A reset that cannot be applied is still one trial's worth of evidence, and it belongs in the exclusion
            // list - named by the reset's own refusal - rather than aborting the run and discarding the trials already
            // measured.
            Err(refusal) => RunAttempt {
                worker: worker.clone(),
                error: Some(refusal),
                ..RunAttempt::default()
            },
            Ok(()) => {
                host.run_one_iteration(
                    ctx,
                    lease,
                    trials.built,
                    &trials.parsed.selection,
                    trials.parsed.policy,
                    trials.fresh_ide,
                    &RunSecrets::default(),
                )
                .await
            }
        };
        let duration_ms = (trials.clock.now().as_millisecond() - started) as f64;
        let free_after = guest_free_bytes_or_nothing(ctx, host, worker).await;
        let status = attempt
            .report
            .as_ref()
            .map_or("no_report", |(document, _)| document.status.as_str());
        let error_code = attempt.error.as_ref().map(|refusal| refusal.code.to_string());
        let reported = match (&attempt.report, &error_code) {
            (None, Some(code)) => code.as_str(),
            _ => status,
        };
        host.reporter().publish(
            Event::Structured {
                data: json!({
                    "event": "flakeTrial",
                    "ordinal": assignment.ordinal,
                    "chainPosition": assignment.chain_position,
                    "status": status,
                    "errorCode": error_code,
                    "durationMs": duration_ms,
                    "ideAction": attempt.ide_action.as_str(),
                    "timing": attempt.timing,
                }),
                line: format!(
                    "trial {} (position {}): {reported} in {}",
                    assignment.ordinal,
                    assignment.chain_position,
                    seconds(duration_ms)
                ),
            },
            Some(&scope),
        );
        consecutive_failures = if trial_was_usable(&attempt) { 0 } else { consecutive_failures + 1 };
        outcomes.push(TrialOutcome {
            assignment: assignment.clone(),
            attempt,
            duration_ms,
            guest_free_bytes_before: free_before,
            guest_free_bytes_after: free_after,
        });
        if consecutive_failures >= MAX_CONSECUTIVE_TRIAL_FAILURES {
            host.reporter().note(
                format!(
                    "abandoning this worker's chain after {consecutive_failures} consecutive unusable trials; {} \
                     trial(s) were not attempted",
                    assignments.len() - outcomes.len()
                ),
                Some(&scope),
            );
            break;
        }
    }
    outcomes
}

/// The free bytes on the guest volume, or nothing.
///
/// Recorded either side of every trial, not to prevent the failure - the free-space ladder does that - but to make a
/// trial that ran against a nearly full disk *visible in the data*: an outlier with no explanation is what gets
/// averaged into a rate. Never fatal: a `df` this cannot read is not evidence about the disk.
async fn guest_free_bytes_or_nothing(ctx: &Ctx, host: &Host, worker: &str) -> Option<i64> {
    let channel = host.manager().channel(worker);
    let guest = Guest {
        ctx,
        settings: host.settings(),
        channel: channel.as_ref(),
        reporter: host.reporter(),
    };
    guest_free_bytes(&guest)
        .await
        .ok()
        .flatten()
        .and_then(|bytes| i64::try_from(bytes).ok())
}

// --- the reset, against a real worker -----------------------------------------------------------------------------

/// The reset's four effects, aimed at one leased worker.
struct LiveReset {
    host: Arc<Host>,
    lease: Lease,
    built: Arc<PreparedBuild>,
}

impl ResetIo for LiveReset {
    async fn ide_stop(&self, ctx: &Ctx) -> Result<(), Refusal> {
        let host = &self.host;
        let Some(state) = HostState::read(host.settings(), &self.lease.worker) else {
            return Ok(());
        };
        // No healthy daemon means no IDE this controller can address, and no IDE means nothing is holding the tree
        // open. The daemon stop still runs after this: it is what retires a daemon that stopped answering.
        if !host.daemon().status(ctx, &state).await?.is_some_and(|status| status.ide_running) {
            return Ok(());
        }
        host.reporter().note(
            "stopping the warm IDE before discarding the shared test home",
            Some(&Scope::worker(&self.lease.worker)),
        );
        let (code, _) = host.daemon().http(ctx, &state, &avl_wire::daemon::IDE_STOP, None).await?;
        if !code.is_success() {
            return Err(Refusal::new(
                "daemon_ide_stop_failed",
                Exit::SOFTWARE,
                format!("/ide/stop returned {}", code.as_u16()),
            ));
        }
        Ok(())
    }

    async fn daemon_stop(&self, ctx: &Ctx) -> Result<(), Refusal> {
        let state = HostState::read(self.host.settings(), &self.lease.worker);
        self.host.stop_daemon(ctx, &self.lease.worker, state.as_ref()).await
    }

    async fn guest_remove(&self, ctx: &Ctx, argv: &[String]) -> Result<(), Refusal> {
        let channel = self.host.manager().channel(&self.lease.worker);
        Guest {
            ctx,
            settings: self.host.settings(),
            channel: channel.as_ref(),
            reporter: self.host.reporter(),
        }
        .as_user(argv, &SpawnOptions::within(TRIAL_REMOVE_TIMEOUT))
        .await
        .map(|_| ())
    }

    async fn daemon_start(&self, ctx: &Ctx) -> Result<(), Refusal> {
        self.host.manager().require_ready(ctx, &self.lease).await?;
        self.host.start_daemon(ctx, &self.lease.worker, &self.built).await.map(|_| ())
    }
}
