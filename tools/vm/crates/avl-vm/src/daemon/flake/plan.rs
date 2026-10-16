//! The pure half of `flake`: which trial runs where, the reset between trials as a list of steps, and what a
//! measured trial hands the fold.

use avl_base::format::one_decimal;
use avl_base::{Config, Refusal};
use avl_host_sys::Ctx;
use avl_report::aggregate::{Attempt, TrialAttempt, flake_summary};
use avl_wire::report::{AggregateError, FlakeSummary, ResetPolicy, Status};
use serde::{Deserialize, Serialize};

use crate::daemon::RunAttempt;

#[cfg(test)]
#[cfg(unix)]
mod tests;

/// What a run's order suspects can mean, published beside them.
///
/// The order suspects are the one instrument that tells the two live hypotheses apart: `first_trial_only` says the
/// failure belongs to a cold state the first trial alone met; `after_first_trial` says something the first trial
/// *left behind* is what later trials fail on - the shape "a warm carrier poisons its successor" would take, and the
/// shape a per-class pass ratio cannot see at all. Two properties of a run decide what they mean, so both are
/// published beside them:
///
/// - `selectionShape`: a `--lane` chain repeats a whole lane, a selector chain repeats one class standalone. The
///   decisive experiment - class A then class B, chained warm - is `flake --lane ui --reset none --filter
///   include-classname=.*(A|B).*`, a two-class lane.
/// - `positionalEvidence`: trial ordinals are chain positions only on one worker. Across N workers each chain has its
///   own first trial while the order signature knows only the globally lowest ordinal, so a positional signal is
///   diluted rather than wrong; `trialPlan` lets a reader recover what the ordinals flattened.
pub(crate) const FLAKE_ORDER_PROBE_NOTE: &str = "orderSuspects is interpretable as chain position only at --workers 1; \
                                          see trialPlan for per-chain position";

/// The guest test home the `daemon` reset discards, relative to `Config::vm_data`.
///
/// `$AIR_VM_DATA/ide/` holds the lane IDE contexts, `<launchKey>/` each, shared across runs on a warm worker. That
/// sharing has a cost: a class run standalone and then run again in a lane fails an install-state assertion, because
/// the managed agent is already installed. That is a carrier the IDE relaunch does not touch. The launch key is the
/// daemon's to compute, so the reset discards every context. It is [`crate::lane::ide::guest_ide_root`].
pub(crate) const GUEST_SHARED_TEST_HOME: &str = "ide";

/// How many consecutive unusable trials on one worker end its chain.
///
/// Not one, because one dead trial is what the exclusion list is for, and not unbounded either: a worker whose daemon
/// will not come back produces K identical exclusions, which costs the whole run's wall clock to learn something the
/// second trial already said. The trials never attempted are simply absent - fabricating them as attempts would put a
/// number into `attemptedTrials`, the denominator of the honesty guard.
pub(crate) const MAX_CONSECUTIVE_TRIAL_FAILURES: usize = 2;

// --- the trial plan -----------------------------------------------------------------------------------------------

/// Which trial runs where, and where in its worker's chain it sits. See [`FLAKE_ORDER_PROBE_NOTE`].
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct TrialAssignment {
    pub ordinal: u32,
    pub worker: String,
    pub chain_position: u32,
}

/// Contiguous blocks of ordinals per worker, not round-robin; the earlier workers take the remainder.
///
/// The order signature reads the globally lowest executed ordinal as "the first trial", so interleaving the workers
/// would make ordinal order disagree with chain order on every worker but one - and a positional signal computed over
/// that is not diluted, it is wrong. Blocks keep ordinal order equal to chain order within each worker, which is the
/// most that can be true of a flat ordinal across N chains.
pub(crate) fn plan_flake_trials(workers: &[String], trials: usize) -> Vec<TrialAssignment> {
    let mut plan = Vec::with_capacity(trials);
    if workers.is_empty() {
        return plan;
    }
    for (index, worker) in workers.iter().enumerate() {
        let share = trials / workers.len() + usize::from(index < trials % workers.len());
        for position in 1..=share {
            plan.push(TrialAssignment {
                ordinal: u32::try_from(plan.len() + 1).unwrap_or(u32::MAX),
                worker: worker.clone(),
                chain_position: u32::try_from(position).unwrap_or(u32::MAX),
            });
        }
    }
    plan
}

// --- the reset ----------------------------------------------------------------------------------------------------

/// One effect a reset can have, as a value rather than as a block of code.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum ResetStep {
    IdeStop,
    DaemonStop,
    /// One fixed argument vector, so the deletion cannot be widened by a shell that re-splits it.
    GuestRemove(Vec<String>),
    DaemonStart,
}

impl ResetStep {
    /// The step's name in a test's expectation. The tests that read it are Unix only.
    #[cfg(all(test, unix))]
    pub(crate) const fn name(&self) -> &'static str {
        match self {
            Self::IdeStop => "ide_stop",
            Self::DaemonStop => "daemon_stop",
            Self::GuestRemove(_) => "guest_remove",
            Self::DaemonStart => "daemon_start",
        }
    }
}

/// One reset, as an ordered list.
///
/// The order carries an obligation that is not obvious from either end of it: the warm IDE's config and system
/// directories live *under* the tree the `daemon` reset deletes, so an `rm -rf` issued while that IDE runs deletes the
/// live IDE's own state. The stop is therefore not an optimization, and a list is what lets a test assert the order
/// directly. The daemon stop also stops every lane IDE that the guest agent runs, because an IDE outlives its daemon
/// and an unhealthy daemon cannot stop it.
///
/// `warm` inherits everything, and `fresh_ide` is the iteration's own `--fresh-ide` path - the same `/ide/stop`,
/// issued where the iteration already decides between a relaunch, a remount and a daemon restart. Re-issuing it here
/// would stop an IDE the iteration was about to replace anyway.
pub(crate) fn reset_steps(settings: &Config, policy: ResetPolicy) -> Vec<ResetStep> {
    if policy != ResetPolicy::FreshWorker {
        return Vec::new();
    }
    vec![
        ResetStep::IdeStop,
        ResetStep::DaemonStop,
        ResetStep::GuestRemove(vec![
            "/bin/rm".to_owned(),
            "-rf".to_owned(),
            format!("{}/{GUEST_SHARED_TEST_HOME}", settings.vm_data),
        ]),
        ResetStep::DaemonStart,
    ]
}

/// The four effects a reset can have, named so a test can drive them without a hypervisor.
pub(crate) trait ResetIo {
    async fn ide_stop(&self, ctx: &Ctx) -> Result<(), Refusal>;
    async fn daemon_stop(&self, ctx: &Ctx) -> Result<(), Refusal>;
    async fn guest_remove(&self, ctx: &Ctx, argv: &[String]) -> Result<(), Refusal>;
    async fn daemon_start(&self, ctx: &Ctx) -> Result<(), Refusal>;
}

impl<T: ResetIo> ResetIo for &T {
    async fn ide_stop(&self, ctx: &Ctx) -> Result<(), Refusal> {
        (**self).ide_stop(ctx).await
    }

    async fn daemon_stop(&self, ctx: &Ctx) -> Result<(), Refusal> {
        (**self).daemon_stop(ctx).await
    }

    async fn guest_remove(&self, ctx: &Ctx, argv: &[String]) -> Result<(), Refusal> {
        (**self).guest_remove(ctx, argv).await
    }

    async fn daemon_start(&self, ctx: &Ctx) -> Result<(), Refusal> {
        (**self).daemon_start(ctx).await
    }
}

/// Runs the steps in the order they were given, and stops at the first refusal: the `rm -rf` must not run against a
/// live IDE because the stop that was to precede it failed.
pub(crate) async fn apply_reset(ctx: &Ctx, steps: &[ResetStep], io: &impl ResetIo) -> Result<(), Refusal> {
    for step in steps {
        match step {
            ResetStep::IdeStop => io.ide_stop(ctx).await?,
            ResetStep::DaemonStop => io.daemon_stop(ctx).await?,
            ResetStep::GuestRemove(argv) => io.guest_remove(ctx, argv).await?,
            ResetStep::DaemonStart => io.daemon_start(ctx).await?,
        }
    }
    Ok(())
}

// --- one trial ----------------------------------------------------------------------------------------------------

/// One trial as this command measured it, before the fold is asked what it means.
#[derive(Clone, Debug)]
pub(crate) struct TrialOutcome {
    pub assignment: TrialAssignment,
    pub attempt: RunAttempt,
    /// Measured by the controller, so a trial that never produced a report still has one.
    pub duration_ms: f64,
    /// `None` for "do not know", which never blocks anything.
    pub guest_free_bytes_before: Option<i64>,
    pub guest_free_bytes_after: Option<i64>,
}

/// The controller's own view of a trial, mapped onto what the fold reads. The attempt's refusal survives as a value:
/// dropped, it would arrive as a missing report with no reason, and the exclusion list is the one place a reader
/// looks to find out why a rate is missing trials.
pub(crate) fn trial_attempt(outcome: &TrialOutcome, reset_policy: ResetPolicy) -> TrialAttempt {
    let attempt = &outcome.attempt;
    let report = attempt.report.as_ref().map(|(document, _)| document);
    TrialAttempt {
        attempt: Attempt {
            worker: attempt.worker.clone(),
            iteration_id: report.map(|document| document.iteration_id.clone()),
            daemon_run_id: report.map(|document| document.daemon_run_id.clone()),
            report_path: attempt.report.as_ref().map(|(_, path)| path.to_string_lossy().into_owned()),
            report: report.cloned(),
            error: attempt.error.as_ref().map(|refusal| AggregateError {
                code: refusal.code.to_string(),
                message: refusal.message.clone(),
            }),
        },
        trial_ordinal: outcome.assignment.ordinal,
        reset_policy,
        duration_ms: Some(outcome.duration_ms),
        guest_free_bytes_before: outcome.guest_free_bytes_before,
        guest_free_bytes_after: outcome.guest_free_bytes_after,
    }
}

/// Hands the measured trials to the arithmetic that owns them.
pub(crate) fn summarize_trials(outcomes: &[TrialOutcome], reset_policy: ResetPolicy) -> FlakeSummary {
    let attempts: Vec<TrialAttempt> = outcomes.iter().map(|outcome| trial_attempt(outcome, reset_policy)).collect();
    flake_summary(&attempts)
}

/// Whether the rate can use a trial, which is the same question the chain's give-up counter asks.
pub(crate) fn trial_was_usable(attempt: &RunAttempt) -> bool {
    attempt
        .report
        .as_ref()
        .is_some_and(|(document, _)| document.status != Status::InfrastructureError)
}

// --- what a run says ----------------------------------------------------------------------------------------------

/// The `--text` reader's whole answer.
pub(crate) fn render_flake_text(selection: &str, reset: &str, workers: &[String], summary: &FlakeSummary, aggregate_path: &str) -> String {
    let percent = |value: f64| format!("{}%", one_decimal(value * 100.0));
    let named = |label: &str, items: &[String]| {
        if items.is_empty() {
            format!("{label}: -")
        } else {
            format!("{label}: {}", items.join(", "))
        }
    };
    let mut reportable = format!("reportable={}", summary.reportable);
    if let Some(reason) = &summary.not_reportable_reason {
        reportable.push_str(&format!(": {reason}"));
    }
    [
        format!(
            "{selection}, {} trial(s) on {}, reset {reset}",
            summary.attempted_trials,
            workers.join(", ")
        ),
        format!(
            "{} authoritative, {} excluded",
            summary.authoritative_trials,
            summary.infrastructure_trials.len()
        ),
        format!(
            "measured {} class(es): {} stable, {} flaky (lane flake rate {}, 95% CI {}-{})",
            summary.measured_classes,
            summary.stable_set.len(),
            summary.flake_set.len(),
            percent(summary.lane_flake_rate),
            percent(summary.lane_flake_interval.low),
            percent(summary.lane_flake_interval.high)
        ),
        named("flaky", &summary.flake_set),
        named("broken", &summary.broken_set),
        named("notMeasured", &summary.not_measured),
        named("unstablePrerequisite", &summary.unstable_prerequisite),
        named("insufficientEvidence", &summary.insufficient_evidence),
        named("orderSuspects", &summary.order_suspects),
        reportable,
        // Last, and always: this line is how a reader comes back to the run once the process is gone.
        format!("aggregate {aggregate_path}"),
    ]
    .join("\n")
}
