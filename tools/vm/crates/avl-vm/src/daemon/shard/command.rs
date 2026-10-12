//! The `shard` command: plan the split, lease the workers, run them at once, and merge what they said.
//!
//! The sequence is the leased-run driver's, as it is `run`'s and `flake`'s: the host build with no worker held, one
//! acquisition of N workers, one body per worker under its lifecycle lock, and the release. The iteration is
//! [`Host::run_one_iteration`] and the verdict is [`merge_shard_verdict`]; this module contributes the split, the
//! body, the fold into the aggregate and where the aggregate goes.

use std::sync::Arc;

use crate::worker::lease::{HeldWorker, ReleaseResult};
use crate::worker::worker::Lease;
use avl_base::format::{seconds, whole_seconds};
use avl_base::{Exit, Outcome, Refusal, Reporter, Scope};
use avl_host_sys::Ctx;
use avl_report::aggregate::{Attempt, ShardAttempt, merge_shard_verdict, persist_shard_verdict};
use avl_wire::progress::{Event, LeaseDisposition, ShardSplit, Verdict};
use avl_wire::report::{AggregateError, RunReport, ShardVerdict};
use serde::{Deserialize, Serialize};
use serde_json::json;

use crate::daemon::leased::Workers;
use crate::daemon::shard::{
    ShardArgs, ShardAssignment, ShardBaseline, ShardKind, ShardPlan, plan_shards, read_shard_baseline, shard_selection,
};
use crate::daemon::verdict::{Reach, bundles_of, checkout_of, conclude, counts_of, failures_of, reproduce_commands, skips_of, traces_of};
use crate::daemon::{Host, ParsedRun, PreparedBuild};
use crate::lane::secrets::RunSecrets;
use avl_base::RefusalExt;

#[cfg(test)]
#[cfg(unix)]
mod tests;

// --- the payload -------------------------------------------------------------------------------------------------

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct BaselineData {
    pub measured_classes: usize,
    pub admitted_reports: usize,
    pub scanned_reports: usize,
    pub unpatternable_classes: Vec<String>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PlanShardData {
    pub shard_index: u32,
    pub kind: ShardKind,
    pub worker: String,
    pub classes: Vec<String>,
    pub predicted_ms: f64,
    pub junit5_filters: Vec<String>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PlanData {
    pub total_ms: f64,
    pub slowest_class_ms: f64,
    pub floor_ms: f64,
    pub predicted_makespan_ms: f64,
    pub shards: Vec<PlanShardData>,
}

/// One worker this run leased, and what became of that lease.
///
/// `released` per lease and not only the disposition above it: a four-shard run whose third release refused leaves
/// three free workers and one held one, and "which" is the only part of that a caller can act on.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct LeaseData {
    pub worker: String,
    pub lease_file: String,
    pub released: bool,
}

/// `shard`'s data payload. `interrupted` and `leaseDisposition` are `null` rather than absent when unset.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ShardData {
    pub lane: String,
    pub shard_run_id: String,
    pub requested_shards: u8,
    pub shard_count: usize,
    pub holder: String,
    pub interrupted: Option<String>,
    pub baseline: BaselineData,
    pub plan: PlanData,
    pub leases: Vec<LeaseData>,
    pub lease_disposition: Option<LeaseDisposition>,
    pub release_failures: Vec<String>,
    pub aggregate_path: String,
    pub aggregate: ShardVerdict,
}

// --- the command -------------------------------------------------------------------------------------------------

/// `shard`: plan the split, lease the workers, run them at once, and merge what they said.
///
/// A journaled run, whose id is also the holder of its leases unless `--holder` names one.
pub(crate) async fn command_shard(ctx: &Ctx, host: &Host, args: ShardArgs) -> Result<Outcome, Refusal> {
    host.journaled(ctx, "shard", args.argv(), async |ctx: &Ctx, run_id: &str| {
        shard(ctx, host, &args, run_id).await
    })
    .await
}

async fn shard(ctx: &Ctx, host: &Host, args: &ShardArgs, run_id: &str) -> Result<Outcome, Refusal> {
    // A shard is a class-name filter over a lane, so there has to be a lane. A single selector already names one
    // class and has nothing to divide; splitting it would mean splitting its methods, which is a different subject
    // with a different balancing question.
    let reporter = host.reporter();
    let program = reporter.program();
    if args.run.lane.is_none() {
        return Err(Refusal::usage(format!(
            "{program} shard requires --lane; a single selector has nothing to split"
        )));
    }
    let parsed = host.parse_run(ctx, &args.run).await?;
    let baseline = read_shard_baseline(host.settings());
    // Planned before a single worker is leased or anything is built: `shard_baseline_unavailable` is the most
    // likely refusal of this command, and it must not cost an acquisition to discover.
    plan_shards(&baseline.durations, usize::from(args.shards), &program)?;

    // One acquisition for the whole set. One `Config` means one guest and so one Bazel configuration, which is what
    // a single shared build depends on; the acquisition refuses a mixed set for that reason.
    let workers = Workers {
        borrowed: None,
        holder: args.holder.as_deref(),
        count: args.shards,
        exact: args.exact,
    };
    let mut leased = host
        .leased_run(
            ctx,
            run_id,
            "shard-run",
            workers,
            |held: &[HeldWorker]| divide(reporter, args, &baseline, held.len(), &program),
            async |plan: &ShardPlan, index: usize, current: Lease, built: &Arc<PreparedBuild>| {
                run_shard(ctx, host, &parsed, plan, index, &current, built).await
            },
        )
        .await?;
    let plan = &leased.plan;
    let signal = leased.interrupted;
    let attempts = shard_attempts(plan, &leased.held, std::mem::take(&mut leased.settled), signal);

    // After the release, so a persist that fails has already given the workers back; and on an interrupt too, with
    // the abandoned shards named rather than missing.
    let aggregate_id = format!("shard-{}-{}", reporter.now().as_millisecond(), &avl_base::new_id()[..8]);
    let verdict = merge_shard_verdict(&attempts);
    let aggregate_path = persist_shard_verdict(host.settings(), &aggregate_id, &verdict)?
        .to_string_lossy()
        .into_owned();
    reporter.note(format!("aggregate {aggregate_path}"), None);

    let held = &leased.held;
    let data = ShardData {
        lane: parsed.selection.description.clone(),
        shard_run_id: aggregate_id,
        requested_shards: args.shards,
        shard_count: plan.shard_count,
        holder: leased.holder.clone(),
        interrupted: signal.map(str::to_owned),
        baseline: BaselineData {
            measured_classes: baseline.durations.len(),
            admitted_reports: baseline.admitted_reports,
            scanned_reports: baseline.scanned_reports,
            unpatternable_classes: baseline.unpatternable.clone(),
        },
        plan: plan_data(plan, held),
        leases: held
            .iter()
            .map(|item| LeaseData {
                worker: item.lease.worker.clone(),
                lease_file: item.receipt.to_string_lossy().into_owned(),
                released: leased.released(&item.lease.worker),
            })
            .collect(),
        lease_disposition: Some(leased.settlement.disposition),
        release_failures: release_failures(&leased.settlement.releases),
        aggregate_path: aggregate_path.clone(),
        aggregate: verdict,
    };
    // Nothing below branches on the release. The exit answers what the *tests* did, because a lane that passed and
    // could not give a worker back is a green lane with a housekeeping problem, and turning it red would make an
    // agent re-run a suite that already answered.

    if let Some(signal) = signal {
        // The abandoned iterations were dropped with the fan-out, and their children are the interrupt service's to
        // stop. An abandoned run is no verdict on the tests, so it publishes none.
        return Err(shard_interrupted(signal, &attempts, plan, &aggregate_path, &leased.release_hint(&program)).with_details(&data));
    }
    let (mut published, refused) = shard_verdict(&parsed.selection.description, plan, &data.aggregate, &attempts);
    if let Some(shards) = &mut published.shards {
        shards.labels = shard_labels(plan, held);
    }
    published.lease = Some(leased.verdict_lease());
    published.report = Some(aggregate_path);
    reporter.publish(Event::Verdict(Box::new(published)), None);
    match refused {
        Some(refused) => Err(refused.with_details(&data)),
        None => Outcome::data(&data),
    }
}

// --- the decisions of a shard run: values in, an answer out, no I/O --------------------------------------------

/// One entry per planned shard, in the plan's order: the attempt of a settled body, an error entry for a body that
/// could not run, and an abandoned entry for a body that `signal` dropped.
///
/// A body that could not run is everything outside the iteration itself: a worker busy with another lifecycle
/// operation, a lease that changed under us. It is still an entry, because a shard that never ran is exactly what
/// must not be averaged away: the merge reads a missing report as `shard_missing_report`.
pub(crate) fn shard_attempts(
    plan: &ShardPlan,
    held: &[HeldWorker],
    settled: Vec<Option<Result<ShardAttempt, Refusal>>>,
    signal: Option<&str>,
) -> Vec<ShardAttempt> {
    plan.shards
        .iter()
        .zip(held)
        .zip(settled)
        .map(|((assignment, item), settled)| match settled {
            Some(Ok(attempt)) => attempt,
            Some(Err(refusal)) => ShardAttempt {
                shard_index: assignment.shard_index,
                attempt: Attempt {
                    worker: item.lease.worker.clone(),
                    error: Some(aggregate_error(&refusal)),
                    ..Attempt::default()
                },
            },
            None => abandoned_attempt(assignment, &item.lease.worker, signal.unwrap_or("an interrupt")),
        })
        .collect()
}

/// The plan as the run data names it, with the worker of each shard.
pub(crate) fn plan_data(plan: &ShardPlan, held: &[HeldWorker]) -> PlanData {
    PlanData {
        total_ms: plan.total_ms,
        slowest_class_ms: plan.slowest_class_ms,
        floor_ms: plan.floor_ms,
        predicted_makespan_ms: plan.makespan_ms,
        shards: plan
            .shards
            .iter()
            .zip(held)
            .map(|(assignment, item)| PlanShardData {
                shard_index: assignment.shard_index,
                kind: assignment.kind,
                worker: item.lease.worker.clone(),
                classes: assignment.classes.clone(),
                predicted_ms: assignment.predicted_ms,
                junit5_filters: assignment.junit5_filters.clone(),
            })
            .collect(),
    }
}

/// Each worker the run could not give back, with the reason.
pub(crate) fn release_failures(releases: &[ReleaseResult]) -> Vec<String> {
    releases
        .iter()
        .filter(|result| !result.released)
        .map(|result| format!("{}: {}", result.worker, result.failure.as_deref().unwrap_or_default()))
        .collect()
}

/// The refusal of a run that `signal` abandoned: how many shards reported, where the aggregate is, and how to free
/// the leases, which stay held.
pub(crate) fn shard_interrupted(
    signal: &str,
    attempts: &[ShardAttempt],
    plan: &ShardPlan,
    aggregate_path: &str,
    release_hint: &str,
) -> Refusal {
    let reported = attempts.iter().filter(|attempt| attempt.attempt.report.is_some()).count();
    Refusal::new(
        "shard_interrupted",
        Exit::SOFTWARE,
        format!(
            "{signal} abandoned the run after {reported} of {} shard(s) reported; the aggregate is at \
             {aggregate_path} and the leases are still held (release each with {release_hint})",
            plan.shard_count
        ),
    )
}

/// The split over the workers the acquisition answered.
///
/// `--count` is a ceiling by design, and remainder-by-exclusion is what makes a narrower split correct rather than
/// merely acceptable: fewer shards is the same lane, differently divided. `--exact` is the measurement run's escape
/// hatch and is enforced by the acquisition itself.
fn divide(reporter: &Reporter, args: &ShardArgs, baseline: &ShardBaseline, held: usize, program: &str) -> Result<ShardPlan, Refusal> {
    let plan = plan_shards(&baseline.durations, held, program)?;
    if held != usize::from(args.shards) {
        reporter.note(
            format!("asked for {} workers and leased {held}; re-balanced for {held}", args.shards),
            None,
        );
    }
    reporter.note(
        format!(
            "{} measured class(es) from {} report(s); serial {}, predicted makespan {}, floor {}",
            baseline.durations.len(),
            baseline.admitted_reports,
            seconds(plan.total_ms),
            seconds(plan.makespan_ms),
            seconds(plan.floor_ms)
        ),
        None,
    );
    Ok(plan)
}

/// One shard, as a value: the body the driver runs under the worker's lifecycle lock.
///
/// Non-failing on purpose, and the reason the fan-out can let every shard settle without losing an answer: a shard
/// whose daemon died is an `infrastructure_error` entry in the aggregate rather than a failure that unwinds its
/// siblings. Every report the iteration produced is on disk before this returns, so the merge can only ever add to
/// the evidence.
async fn run_shard(
    ctx: &Ctx,
    host: &Host,
    parsed: &ParsedRun,
    plan: &ShardPlan,
    index: usize,
    lease: &Lease,
    built: &PreparedBuild,
) -> ShardAttempt {
    let assignment = &plan.shards[index];
    let selection = shard_selection(&parsed.selection, assignment, plan.shard_count);
    let scope = Scope::worker(&lease.worker);
    host.reporter().publish(
        Event::Structured {
            data: json!({
                "event": "shardStarted",
                "shardIndex": assignment.shard_index,
                "kind": assignment.kind,
                "classes": assignment.classes,
                "predictedMs": assignment.predicted_ms,
            }),
            line: format!("shard {}: {}", assignment.shard_index, selection.description),
        },
        Some(&scope),
    );
    let attempt = host
        .run_one_iteration(
            ctx,
            lease,
            built,
            &selection,
            parsed.policy,
            parsed.fresh_ide,
            &RunSecrets::default(),
        )
        .await;
    let status = attempt
        .report
        .as_ref()
        .map_or("no_report", |(document, _)| document.status.as_str());
    host.reporter().publish(
        Event::Structured {
            data: json!({
                "event": "shardFinished",
                "shardIndex": assignment.shard_index,
                "status": status,
                "timing": attempt.timing,
            }),
            line: format!("shard {}: {status}", assignment.shard_index),
        },
        Some(&scope),
    );
    let (report, report_path) = match attempt.report {
        Some((document, path)) => (Some(document), Some(path.to_string_lossy().into_owned())),
        None => (None, None),
    };
    ShardAttempt {
        shard_index: assignment.shard_index,
        attempt: Attempt {
            worker: lease.worker.clone(),
            iteration_id: report.as_ref().map(|document| document.iteration_id.clone()),
            daemon_run_id: report.as_ref().map(|document| document.daemon_run_id.clone()),
            report_path,
            report,
            error: attempt.error.as_ref().map(aggregate_error),
        },
    }
}

fn aggregate_error(refusal: &Refusal) -> AggregateError {
    AggregateError {
        code: refusal.code.to_string(),
        message: refusal.message.clone(),
    }
}

/// What a shard that was still running when the run was abandoned contributes.
fn abandoned_attempt(assignment: &ShardAssignment, worker: &str, signal: &str) -> ShardAttempt {
    ShardAttempt {
        shard_index: assignment.shard_index,
        attempt: Attempt {
            worker: worker.to_owned(),
            error: Some(AggregateError {
                code: "shard_interrupted".to_owned(),
                message: format!("{signal} arrived while this shard was still running; its iteration was abandoned"),
            }),
            ..Attempt::default()
        },
    }
}

/// The verdict of a merged shard run, and the refusal the command answers with, which is `None` for a passed run.
/// Every human form renders the published verdict, so the refusal's message is its one line.
fn shard_verdict(selection: &str, plan: &ShardPlan, merged: &ShardVerdict, attempts: &[ShardAttempt]) -> (Verdict, Option<Refusal>) {
    let reports: Vec<&RunReport> = attempts.iter().filter_map(|attempt| attempt.attempt.report.as_ref()).collect();
    let count = plan.shard_count;
    let execution = &merged.execution;
    let infrastructure = (
        merged.code.map_or("shard_infrastructure_error", |code| code.as_str()).into(),
        Exit::SOFTWARE,
    );
    let (summary, refused) = conclude(
        merged.status,
        infrastructure,
        merged.diagnostic.as_deref(),
        execution,
        merged.failures.first(),
        selection,
        Reach::Shards(count),
    );
    let verdict = Verdict {
        status: merged.status,
        code: refused.as_ref().map(|refusal| refusal.code.to_string()),
        summary,
        counts: counts_of(execution),
        failures: failures_of(&merged.failures, &bundles_of(reports.iter().copied())),
        rerun: reproduce_commands(&merged.failures),
        skipped: skips_of(&merged.skipped_containers),
        unreported: reports
            .iter()
            .flat_map(|report| report.unreported_classes.iter().cloned())
            .collect(),
        lanes: Vec::new(),
        shards: Some(ShardSplit {
            count: u32::try_from(count).unwrap_or(u32::MAX),
            labels: Vec::new(),
            wall_ms: milliseconds(merged.wall_duration_ms),
            machine_ms: milliseconds(merged.total_duration_ms),
        }),
        lease: None,
        checkout: reports.iter().find_map(|report| checkout_of(report)),
        traces: traces_of(reports.iter().copied()),
        timing: None,
        report: None,
    };
    (verdict, refused)
}

#[expect(
    clippy::cast_possible_truncation,
    clippy::cast_sign_loss,
    reason = "a merged duration is a non-negative sum of measured milliseconds; the cast saturates rather than wraps"
)]
const fn milliseconds(value: f64) -> u64 {
    value.max(0.0) as u64
}

/// One label for each shard: its index, its worker and its predicted time, such as `1:air-docker-1:84s`, and `+rest`
/// for the shard that runs the remainder.
fn shard_labels(plan: &ShardPlan, held: &[HeldWorker]) -> Vec<String> {
    plan.shards
        .iter()
        .zip(held)
        .map(|(assignment, item)| {
            let suffix = match assignment.kind {
                ShardKind::Remainder => "+rest",
                ShardKind::Include => "",
            };
            format!(
                "{}:{}:{}s{suffix}",
                assignment.shard_index,
                item.lease.worker,
                whole_seconds(assignment.predicted_ms)
            )
        })
        .collect()
}
