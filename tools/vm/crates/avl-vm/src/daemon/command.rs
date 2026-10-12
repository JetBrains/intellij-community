//! The `run` and `daemon` commands.

use std::path::Path;
use std::sync::Arc;
use std::time::Duration;

use crate::worker::lease::{receipt_for_path, required_receipt, with_lease_operation};
use crate::worker::worker::Lease;
use avl_base::clock::millis;
use avl_base::format::{seconds, words};
use avl_base::phase::{Timeline, Timing};
use avl_base::plain::release_command;
use avl_base::{Exit, Outcome, Refusal, Scope, Selection, history};
use avl_host_sys::Ctx;
use avl_host_sys::guest::{SupervisorOptions, ensure_host_paths};
use avl_wire::daemon::Status as DaemonStatus;
use avl_wire::progress::{Event, LeaseDisposition, PlannedIteration, RunPlanned, Verdict, VerdictLease};
use avl_wire::report::{RunReport, Status};
use serde::{Deserialize, Serialize};
use serde_json::json;

use crate::daemon::build::{BuildScope, PreparedBuild};
use crate::daemon::host::Host;
use crate::daemon::iterate::RunAttempt;
use crate::daemon::leased::Workers;
use crate::daemon::run::{RunArgs, RunCommandArgs, RunPlan};
use crate::daemon::state::HostState;
use crate::daemon::verdict::{iteration_verdict, lanes_verdict};
use crate::lane::secrets::{RunSecrets, clear_run_secrets};
use avl_base::RefusalExt;

/// The timeout of the supervisor verb that reads the tail of the daemon log for `daemon log`: a read of one file in
/// the guest.
const DAEMON_LOG_TIMEOUT: Duration = Duration::from_secs(10);

#[cfg(test)]
#[cfg(unix)]
mod tests;

// --- the payloads ------------------------------------------------------------------------------------------------

/// `run`'s data payload for one iteration.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct RunData {
    pub worker: String,
    pub selection: String,
    pub iteration_id: String,
    pub tests_started: u32,
    pub tests_failed: u32,
    pub tests_skipped: u32,
    pub containers_skipped: u32,
    pub container_failures: u32,
    pub has_failures: bool,
    pub ide_action: String,
    pub timing: String,
    pub report: RunReport,
    pub report_path: String,
    /// One selector command per failed class, in the order the report first named them; absent for a green run.
    /// See [`crate::daemon::verdict::reproduce_commands`] for why the report is not the place for it.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub reproduce: Vec<String>,
    /// The lease a `run` without a receipt took for itself, and what became of it. Absent for a run under the
    /// caller's receipt, whose lease is the caller's to report.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub lease: Option<RunLease>,
}

/// The payload of a `run` that ran more than one lane: one verdict per lane, in the declared lane order, on one
/// worker.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct RunLanesData {
    pub worker: String,
    pub lanes: Vec<LaneRun>,
    /// The lanes after an iteration that produced no report, which the run did not reach.
    pub not_run: Vec<NotRunLane>,
    /// Whether any lane did not pass.
    pub has_failures: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub lease: Option<RunLease>,
}

/// One lane of a several-lane `run`: the verdict that a `run --lane` of it alone would give.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct LaneRun {
    pub lane: String,
    /// 0 for a passed lane, 6 for a red one, and the refusal's exit otherwise.
    pub exit_code: u8,
    /// The refusal code, absent for a passed lane.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub code: Option<String>,
    /// The verdict's text for a passed lane and the refusal's message otherwise.
    pub message: String,
    /// The single-lane payload, or `null` when the iteration produced no report.
    pub run: Option<RunData>,
}

/// One lane of the plan that the run did not reach, and why.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub(crate) struct NotRunLane {
    pub lane: String,
    /// The lane's generated suite classes, in the answer's order: what `run <Class>` takes.
    pub suites: Vec<String>,
    pub reason: String,
}

/// The lease a self-leased `run` placed: who held it, on which worker, and what became of it.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct RunLease {
    pub holder: String,
    pub worker: String,
    pub disposition: LeaseDisposition,
    /// The receipt, present only while the worker is still held, because it is what frees it.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub lease_file: Option<String>,
}

/// `daemon start`'s payload.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct DaemonStartData {
    pub worker: String,
    pub run_id: String,
    pub port: u16,
    pub runtime_digest: String,
    pub product_digest: String,
    pub phases: Vec<Timing>,
}

/// `daemon status`'s payload.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
pub(crate) struct DaemonStatusData {
    pub worker: String,
    pub daemon: Option<DaemonName>,
    /// What the status probe decided; `status` is the decoded body itself.
    pub reachable: bool,
    pub status: Option<DaemonStatus>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct DaemonName {
    pub run_id: String,
    /// The worker and the daemon's guest port, as `worker:port`.
    pub endpoint: String,
}

/// `daemon warm`'s payload: no worker, the configuration that was warmed, and the four identities the build
/// stamped.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct DaemonWarmData {
    /// Always `null`, and written rather than omitted. Every other payload of these commands names a worker, so a
    /// reader comparing two envelopes has to be able to see that this command took none - an absent key reads as a
    /// field that went missing.
    pub worker: Option<String>,
    /// The `--backend` spelling of the pool that was warmed; `guest_os` is the axis that actually decides the Bazel
    /// configuration. Both, because the caller retypes the first and reasons about the second.
    pub config: String,
    pub guest_os: String,
    pub runtime_digest: String,
    /// What a start compares its own build against, and warming reaches no guest to change it.
    pub launch_digest: String,
    pub product_digest: String,
    pub mount_digest: String,
    /// Bazel's own cost, and this controller's hashing of what Bazel produced, kept apart for the reason
    /// [`Host::prepare_build`] separates them.
    pub build_ms: u64,
    pub stamp_ms: u64,
}

/// The `daemon` verbs.
#[derive(clap::Subcommand, Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum DaemonVerb {
    /// Stages the runtime and boots the daemon, replacing any running one.
    Start,
    /// The same as start.
    Restart,
    /// The daemon's own account of itself.
    Status,
    /// Retires the daemon.
    Stop,
    /// The tail of the daemon's log.
    Log,
    /// Builds and stamps the host half of the lane, for no worker and without a lease.
    Warm,
}

impl DaemonVerb {
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::Start => "start",
            Self::Restart => "restart",
            Self::Status => "status",
            Self::Stop => "stop",
            Self::Log => "log",
            Self::Warm => "warm",
        }
    }
}

// --- run ---------------------------------------------------------------------------------------------------------

impl Host {
    /// One `run`: parse, build, run each iteration of the plan, and turn the reports into a verdict whose exit a
    /// caller can branch on.
    ///
    /// Under the caller's receipt when `lease_file` names one, which keeps a warm-worker inner loop on its worker.
    /// Without one it takes a lease of its own for the run; see [`Host::run_self_leased`].
    ///
    /// Every run has an id and a journal from its first record; see [`Host::journaled`]. The id is also the holder
    /// of a self-taken lease, so `lease show` and the journal name one run by one name. The journal receives every
    /// record the run publishes, from `runStarted` to `runFinished`, whatever the output form.
    ///
    /// The `--test-env` sources, stdin and host files, are read first, so a missing one costs no build and no lease.
    /// The journal's argv leaves them out: a run is named by what it selects, and the history of earlier runs
    /// matches on that.
    pub(crate) async fn command_run(&self, ctx: &Ctx, args: &RunCommandArgs, lease_file: Option<&Path>) -> Result<Outcome, Refusal> {
        let RunCommandArgs { run: args, test_env } = args;
        let argv = args.argv();
        self.journaled(ctx, "run", argv.clone(), async |ctx: &Ctx, run_id: &str| {
            let secrets = RunSecrets::read(test_env, self.stdin.as_ref())?;
            match lease_file {
                None => self.run_self_leased(ctx, args, &argv, run_id, &secrets).await,
                Some(lease_file) => self.run_under_receipt(ctx, args, &argv, lease_file, &secrets).await,
            }
        })
        .await
    }

    /// Says what the run will execute, once its selection resolved, and how long that work took in the earlier
    /// runs whose journals are on this machine.
    fn publish_plan(&self, plan: &RunPlan, argv: &[String]) {
        self.reporter.publish(
            Event::RunPlanned(RunPlanned {
                iterations: plan
                    .iterations
                    .iter()
                    .map(|iteration| PlannedIteration {
                        lane: iteration.lane.clone(),
                        selection: iteration.selection.description.clone(),
                        classes: iteration.classes.clone(),
                    })
                    .collect(),
            }),
            None,
        );
        self.reporter
            .publish(Event::Expected(history::read(&self.settings.runtime_root, "run", argv)), None);
    }

    fn publish_verdict(&self, verdict: Option<Verdict>) {
        if let Some(verdict) = verdict {
            self.reporter.publish(Event::Verdict(Box::new(verdict)), None);
        }
    }

    async fn run_under_receipt(
        &self,
        ctx: &Ctx,
        args: &RunArgs,
        argv: &[String],
        lease_file: &Path,
        secrets: &RunSecrets,
    ) -> Result<Outcome, Refusal> {
        let (_, receipt) = receipt_for_path(&self.settings, lease_file)?;
        with_lease_operation(ctx, &self.manager, &receipt, "run", async |current: Lease| {
            let plan = self.plan_run(ctx, args).await?;
            self.publish_plan(&plan, argv);
            ensure_host_paths(ctx, &self.runner, &self.settings).await?;
            let scope = BuildScope::worker(&self.settings, &current.worker)?;
            let built = self.prepare_build(ctx, &scope).await?;
            let attempts = self.run_plan(ctx, &current, &built, &plan, secrets).await;
            let (outcome, verdict) = plan_verdict(&self.reporter.program(), &plan, &attempts, None);
            self.publish_verdict(verdict);
            outcome
        })
        .await
    }

    /// Runs the iterations of the plan one after the other, on one worker, against one build.
    ///
    /// One build serves every lane, because the daemon's classpath is the union of the lanes. `--fresh-ide`
    /// relaunches the IDE for the first iteration only. An iteration that produced no report stops the run: the
    /// cause is the worker or the daemon, and the next lane would pay for it again. [`plan_verdict`] names each lane
    /// that was not reached.
    async fn run_plan(&self, ctx: &Ctx, current: &Lease, built: &PreparedBuild, plan: &RunPlan, secrets: &RunSecrets) -> Vec<RunAttempt> {
        let mut attempts = Vec::with_capacity(plan.iterations.len());
        let count = plan.iterations.len();
        for (index, iteration) in plan.iterations.iter().enumerate() {
            if count > 1 {
                self.reporter.note(
                    format!("lane {} ({} of {count})", iteration.lane.as_deref().unwrap_or_default(), index + 1),
                    Some(&Scope::worker(&current.worker)),
                );
            }
            let attempt = self
                .run_one_iteration(
                    ctx,
                    current,
                    built,
                    &iteration.selection,
                    plan.policy,
                    plan.fresh_ide && index == 0,
                    secrets,
                )
                .await;
            let failed = attempt.error.is_some();
            attempts.push(attempt);
            if failed {
                break;
            }
        }
        attempts
    }

    /// `run` without a receipt: one lease, taken for this run and given back after it.
    ///
    /// The order is the skill's standard sequence, so one command line replaces the script that spelled it. The
    /// selection is parsed first, so a refusal costs nothing. The rest is [`Host::leased_run`] with one worker under
    /// the holder `<user>-run-<uuid>`: the host build with no worker held, then the lease, then every iteration of
    /// the plan on that one worker, then the release.
    ///
    /// Every exit after the acquisition releases the lease, a failed iteration included, and the verdict says what
    /// became of it. The one exception is an interrupt, the driver's policy for every command: the iteration may
    /// still be in flight, so the lease is kept and the refusal names its receipt. The run's secret files are
    /// removed all the same, since neither the dropped iteration nor a release will.
    async fn run_self_leased(
        &self,
        ctx: &Ctx,
        args: &RunArgs,
        argv: &[String],
        run_id: &str,
        secrets: &RunSecrets,
    ) -> Result<Outcome, Refusal> {
        let plan = self.plan_run(ctx, args).await?;
        self.publish_plan(&plan, argv);
        let workers = Workers {
            borrowed: None,
            holder: None,
            count: 1,
            exact: false,
        };
        let leased = self
            .leased_run(
                ctx,
                run_id,
                "run",
                workers,
                |_| Ok(()),
                async |(): &(), _, current: Lease, built: &Arc<PreparedBuild>| self.run_plan(ctx, &current, built, &plan, secrets).await,
            )
            .await?;
        let Some(taken) = leased.acquired().first() else {
            return Err(Refusal::internal("the acquisition answered no worker"));
        };
        let worker = taken.lease.worker.clone();
        let receipt = taken.receipt.to_string_lossy().into_owned();
        let disposition = leased.settlement.disposition;
        let record = RunLease {
            holder: leased.holder.clone(),
            worker: worker.clone(),
            disposition,
            lease_file: (disposition != LeaseDisposition::Released).then(|| receipt.clone()),
        };
        let program = self.reporter.program();
        if let Some(signal) = leased.interrupted {
            // The interrupt dropped the iteration before its own removal, and the kept lease means no release
            // removes the directory either; so the secret files go here.
            self.remove_run_secrets_from(ctx, &worker, secrets).await;
            return Err(Refusal::new(
                "run_interrupted",
                Exit::SOFTWARE,
                format!(
                    "{signal} abandoned the run on {worker}; the lease is still held (release it with {})",
                    release_command(&program, &receipt)
                ),
            )
            .with_details(json!({ "lease": record })));
        }
        let attempts = leased
            .settled
            .into_iter()
            .next()
            .flatten()
            .ok_or_else(|| Refusal::internal("the run's iterations never settled"))??;
        let (outcome, verdict) = plan_verdict(&program, &plan, &attempts, Some(&record));
        self.publish_verdict(verdict);
        outcome
    }
}

/// One iteration's run data, verdict and refusal, or the attempt's own refusal when it left no report.
fn lane_parts(
    selection: &crate::daemon::run::RunSelection,
    attempt: &RunAttempt,
    held: Option<&RunLease>,
) -> Result<(RunData, Verdict, Option<Refusal>), Refusal> {
    if let Some(error) = &attempt.error {
        return Err(error.clone());
    }
    let Some((document, report_path)) = &attempt.report else {
        return Err(Refusal::internal("an iteration answered neither a report nor a refusal"));
    };
    let (mut verdict, refused) = iteration_verdict(selection, document, report_path, attempt);
    verdict.lease = verdict_lease(held);
    let execution = &document.execution;
    let data = RunData {
        worker: attempt.worker.clone(),
        selection: selection.description.clone(),
        iteration_id: document.iteration_id.clone(),
        tests_started: execution.tests_started,
        tests_failed: execution.tests_failed,
        tests_skipped: execution.tests_skipped,
        containers_skipped: execution.containers_skipped,
        container_failures: execution.container_failures,
        has_failures: document.status == Status::Failed,
        ide_action: attempt.ide_action.as_str().to_owned(),
        timing: attempt.timing.clone(),
        report: document.clone(),
        report_path: report_path.to_string_lossy().into_owned(),
        reproduce: if verdict.status == Status::Failed {
            verdict.rerun.clone()
        } else {
            Vec::new()
        },
        lease: held.cloned(),
    };
    Ok((data, verdict, refused))
}

/// What became of a self-taken lease, as the verdict names it, or `None` under the caller's receipt.
fn verdict_lease(held: Option<&RunLease>) -> Option<VerdictLease> {
    held.map(|held| VerdictLease {
        workers: vec![held.worker.clone()],
        disposition: held.disposition,
        receipts: held.lease_file.iter().cloned().collect(),
    })
}

/// The attempts of a plan as the one outcome `run` answers, with `held` as its `lease` key, and the verdict the run
/// publishes.
///
/// One iteration answers exactly what a single-lane `run` always answered: the outcome carries no text, and a
/// refusal's message is the verdict's one line, because every human form renders the published verdict and the JSON
/// caller reads the data. A self-leased run settles its lease before this is called, so the verdict says what
/// became of it; nothing here branches on the release, because a green run that could not give its worker back is a
/// green run with a housekeeping problem.
///
/// Several iterations answer [`RunLanesData`]: one [`LaneRun`] per lane, and a [`NotRunLane`] for each lane after an
/// iteration that produced no report. The exit is the worst of the lanes: a passed lane is 0, a red lane is 6, and
/// every other refusal is worse than red because it is no verdict at all. Between two lanes of the same rank, the
/// first in lane order speaks.
///
/// The verdict is `None` when an iteration left no report. That refusal is no verdict on the tests, so the run
/// publishes none, and the refusal's message keeps each lane's line and every line of the failure.
pub(crate) fn plan_verdict(
    program: &str,
    plan: &RunPlan,
    attempts: &[RunAttempt],
    held: Option<&RunLease>,
) -> (Result<Outcome, Refusal>, Option<Verdict>) {
    fn outcome_of(data: &impl Serialize) -> Result<Outcome, Refusal> {
        Outcome::data(data)
    }
    if let ([iteration], [attempt]) = (plan.iterations.as_slice(), attempts) {
        return match lane_parts(&iteration.selection, attempt, held) {
            Err(refusal) => (Err(refusal), None),
            Ok((data, verdict, None)) => (outcome_of(&data), Some(verdict)),
            Ok((data, verdict, Some(refused))) => (Err(refused.with_details(&data)), Some(verdict)),
        };
    }

    let mut not_run = Vec::new();
    if let Some(last) = attempts.last()
        && attempts.len() < plan.iterations.len()
    {
        let failed_lane = plan.iterations[attempts.len() - 1].lane.as_deref().unwrap_or_default();
        let code = last.error.as_ref().map_or("", |error| error.code.as_ref());
        for iteration in &plan.iterations[attempts.len()..] {
            not_run.push(NotRunLane {
                lane: iteration.lane.clone().unwrap_or_default(),
                suites: iteration.classes.clone(),
                reason: format!("not reached: the {failed_lane} iteration failed with {code}"),
            });
        }
    }
    let mut data = RunLanesData {
        worker: attempts.first().map(|attempt| attempt.worker.clone()).unwrap_or_default(),
        lanes: Vec::new(),
        not_run,
        has_failures: false,
        lease: held.cloned(),
    };
    let mut lanes = Vec::new();
    let mut verdicts = Vec::new();
    let mut blocks = Vec::new();
    let mut worst: Option<Refusal> = None;
    for (iteration, attempt) in plan.iterations.iter().zip(attempts) {
        let lane = iteration.lane.clone().unwrap_or_default();
        let mut entry = LaneRun {
            lane: lane.clone(),
            exit_code: 0,
            code: None,
            message: String::new(),
            run: None,
        };
        let refused = match lane_parts(&iteration.selection, attempt, None) {
            Err(refusal) => Some(refusal),
            Ok((run, verdict, refused)) => {
                entry.message.clone_from(&verdict.summary);
                entry.run = Some(run);
                lanes.push(lane.clone());
                verdicts.push(verdict);
                refused
            }
        };
        if let Some(refused) = refused {
            entry.exit_code = refused.exit.code();
            entry.code = Some(refused.code.to_string());
            entry.message.clone_from(&refused.message);
            if worst.as_ref().is_none_or(|worst| exit_rank(refused.exit) > exit_rank(worst.exit)) {
                worst = Some(refused);
            }
            data.has_failures = true;
        }
        blocks.push(format!("lane {lane}: {}", entry.message));
        data.lanes.push(entry);
    }
    if verdicts.len() < attempts.len() {
        let worst = worst.unwrap_or_else(|| Refusal::internal("a lane left no report"));
        let text = format!(
            "{}{}{}",
            blocks.join("\n\n"),
            render_not_run(&data.not_run),
            render_lease(program, held)
        );
        return (Err(Refusal::new(worst.code, worst.exit, text).with_details(&data)), None);
    }
    let mut verdict = lanes_verdict(&lanes, &verdicts);
    verdict.lease = verdict_lease(held);
    match worst {
        Some(worst) => (
            Err(Refusal::new(worst.code, worst.exit, verdict.summary.clone()).with_details(&data)),
            Some(verdict),
        ),
        None => (outcome_of(&data), Some(verdict)),
    }
}

/// Orders the exits of lane verdicts: passed, then red, then every refusal that is no verdict.
const fn exit_rank(exit: Exit) -> u8 {
    match exit {
        Exit::OK => 0,
        Exit::TESTS_FAILED => 1,
        _ => 2,
    }
}

/// Each lane the run did not reach, one line each, or nothing at all. Only a refusal with no verdict has such a
/// lane, because a lane is left out only after an iteration that left no report.
fn render_not_run(not_run: &[NotRunLane]) -> String {
    not_run
        .iter()
        .map(|left| format!("\nnot run: {} ({} suite(s)): {}", left.lane, left.suites.len(), left.reason))
        .collect()
}

/// The self-leased run's lease as a line of a refusal with no verdict, or nothing under the caller's receipt. A
/// worker that stayed held is named with the receipt that frees it.
fn render_lease(program: &str, held: Option<&RunLease>) -> String {
    let Some(held) = held else {
        return String::new();
    };
    let mut line = format!("\nlease {} {}", held.worker, held.disposition);
    if let Some(receipt) = &held.lease_file {
        line.push_str(&format!(" (release it with {})", release_command(program, receipt)));
    }
    line
}

// --- daemon ------------------------------------------------------------------------------------------------------

/// The phase breakdown as a table, for the `--text` reader.
///
/// Four columns because four different fixes: `guest` is round-trips a persistent channel or batching would remove,
/// `host` is local work, `poll` is probes whose `slept` column is the quantization a blocking wait would remove, and
/// `other` is elapsed the phase did not account for - which is how an uninstrumented spawn or a genuinely slow
/// in-process step shows up instead of hiding inside a total. `slowest` belongs to the first: it names the dearest
/// single guest call of the phase, which tells a reader whether the `guest` number is one expensive round-trip or an
/// even spread over many. Its skeleton is not the argv - the timeline withholds anything that carries a value,
/// since a guest argv can hold the UI-test bridge token.
pub(crate) fn render_phase_table(phases: &[Timing]) -> String {
    let format = |milliseconds: u64| seconds(milliseconds as f64);
    let mut total = 0;
    let mut lines = Vec::with_capacity(phases.len() + 1);
    for entry in phases {
        total += entry.elapsed_ms;
        let other = entry.elapsed_ms.saturating_sub(entry.guest_ms + entry.host_ms);
        let mut calls = Vec::new();
        if entry.guest_calls > 0 {
            calls.push(format!("guest {}x {}", entry.guest_calls, format(entry.guest_ms)));
        }
        if let (Some(slowest_ms), Some(slowest)) = (entry.slowest_guest_call_ms, &entry.slowest_guest_call) {
            calls.push(format!("slowest {slowest} {}", format(slowest_ms)));
        }
        if entry.host_calls > 0 {
            calls.push(format!("host {}x {}", entry.host_calls, format(entry.host_ms)));
        }
        if let Some(iterations) = entry.poll_iterations {
            calls.push(format!("poll {iterations}x"));
        }
        if let Some(last) = entry.last_unsuccessful_probe_ms {
            calls.push(format!("slept {}", format(entry.elapsed_ms.saturating_sub(last))));
        }
        if other > 0 {
            calls.push(format!("other {}", format(other)));
        }
        lines.push(format!(
            "  {:<18} {:>7}  {}",
            entry.name,
            format(entry.elapsed_ms),
            calls.join("  ")
        ));
    }
    lines.insert(0, format!("phases ({} total)", format(total)));
    lines.join("\n")
}

impl Host {
    /// Drives the daemon lifecycle deliberately: start/restart, status, stop, log, warm.
    ///
    /// `warm` is answered before the receipt is resolved, and that placement is the verb's whole point: warming
    /// touches no worker, so requiring a lease would make the cheapest useful command in this controller cost a
    /// worker some other shard could be running tests on.
    pub(crate) async fn command_daemon(&self, ctx: &Ctx, verb: DaemonVerb, lease_file: Option<&Path>) -> Result<Outcome, Refusal> {
        if verb == DaemonVerb::Warm {
            return self.daemon_warm(ctx).await;
        }
        let (_, receipt) = receipt_for_path(&self.settings, required_receipt(lease_file)?)?;
        match verb {
            DaemonVerb::Start | DaemonVerb::Restart => {
                with_lease_operation(ctx, &self.manager, &receipt, "run", async |current: Lease| {
                    self.daemon_start(ctx, verb, &current).await
                })
                .await
            }
            DaemonVerb::Status => {
                with_lease_operation(ctx, &self.manager, &receipt, "status", async |current: Lease| {
                    self.daemon_status_outcome(ctx, &current.worker).await
                })
                .await
            }
            DaemonVerb::Stop => {
                with_lease_operation(ctx, &self.manager, &receipt, "cancel", async |current: Lease| {
                    let state = HostState::read(&self.settings, &current.worker);
                    self.stop_daemon(ctx, &current.worker, state.as_ref()).await?;
                    // `stop_daemon` refuses and keeps the record while the recorded run still holds the slot, so
                    // this runs only once no record names a daemon: a daemon run in the slot is retired here, and
                    // any other holder refuses `run_active`. No gate first: the backend resolves its executable
                    // when it builds a guest line, for this verb and `daemon log` alike.
                    let retired = self.retire_unrecorded_daemon(ctx, &current.worker, "stop the daemon").await?;
                    // A run that died before its own removal leaves its secret files; no daemon is left to read them.
                    let channel = self.channel(&current.worker);
                    clear_run_secrets(&self.guest(ctx, channel.as_ref())).await?;
                    let stopped = retired.or(state.map(|state| state.run_id));
                    Ok(Outcome {
                        text: if stopped.is_some() { "stopped" } else { "no daemon to stop" }.to_owned(),
                        data: json!({
                            "worker": current.worker,
                            "stopped": stopped,
                        }),
                    })
                })
                .await
            }
            DaemonVerb::Log => {
                with_lease_operation(ctx, &self.manager, &receipt, "log", async |current: Lease| {
                    self.daemon_log(ctx, &current.worker).await
                })
                .await
            }
            DaemonVerb::Warm => unreachable!("warm is answered before the receipt is read"),
        }
    }

    /// Builds and stamps the host half of the lane, for no worker.
    ///
    /// Every step is host-side: [`Host::prepare_build`] runs the lane build, resolves the runtime descriptor and
    /// hashes the files it names, and none of that needs a guest, a lease or a running VM. What it leaves behind is
    /// what a `daemon start` would otherwise pay for on the critical path - Bazel's analysis of the lane graph, the
    /// build outputs themselves, and the stat-keyed digest cache under [`BuildScope::pool`], which every worker of
    /// this pool reads.
    ///
    /// A real build and deliberately not `--nobuild`: `--nobuild` warms the analysis and leaves the outputs
    /// unbuilt, so the next start still pays the actions - and ADR 0105 records what a canary built on `--nobuild`
    /// cost when it launched an hour of VM work against a tree whose assembler had failed.
    ///
    /// One guest configuration per warm. The host Bazel argv adds this guest's `.bazelrc` config, whose build
    /// options are part of Bazel's analysis key, so a Linux warm does not warm macOS - and, worse, warming the other
    /// guest inside one output base *evicts* what this one warmed.
    async fn daemon_warm(&self, ctx: &Ctx) -> Result<Outcome, Refusal> {
        ensure_host_paths(ctx, &self.runner, &self.settings).await?;
        // The pool scope, not a worker's: this build is identical for every worker of the guest, and warming into
        // one worker's directory would leave the other workers' digest caches cold for no reason.
        let scope = BuildScope::pool(&self.settings)?;
        let built = self.prepare_build(ctx, &scope).await?;
        let label = Selection {
            backend: self.settings.backend,
            guest_os: self.settings.guest_os,
        }
        .label();
        let data = DaemonWarmData {
            worker: None,
            config: label.to_owned(),
            guest_os: self.settings.guest_os.as_str().to_owned(),
            runtime_digest: built.runtime_digest.clone(),
            launch_digest: built.launch_digest.clone(),
            product_digest: built.product_digest.clone(),
            mount_digest: built.mount_digest.clone(),
            build_ms: millis(built.build),
            stamp_ms: millis(built.stamp),
        };
        // The configuration is named in the prose form too, because the trap this command exists to remove is paid
        // for again by warming the wrong guest, and a bare digest does not say which one was warmed.
        let short = built.runtime_digest.get(..12).unwrap_or(&built.runtime_digest);
        Outcome::new(
            &data,
            format!("warmed {label} {short} in {}", seconds(millis(built.build + built.stamp) as f64)),
        )
    }

    /// The one operation whose cost is worth a breakdown rather than a total: a warm restart is the largest fixed
    /// cost on a warm worker and is reported as one word everywhere else. Collection starts here rather than in
    /// [`Host::start_daemon`] so that the host build and the digest stamp - which a restart pays too - are inside
    /// the same table.
    async fn daemon_start(&self, ctx: &Ctx, verb: DaemonVerb, current: &Lease) -> Result<Outcome, Refusal> {
        let timeline = Timeline::collecting();
        let timed = ctx.clone().with_timeline(timeline.clone());
        {
            let (step, _phase) = timed.begin("host-paths");
            ensure_host_paths(&step, &self.runner, &self.settings).await?;
        }
        {
            let (step, _phase) = timed.begin("worker-ready");
            self.manager.require_ready(&step, current).await?;
        }
        let prep = {
            let (step, _phase) = timed.begin("prepare-build");
            let scope = BuildScope::worker(&self.settings, &current.worker)?;
            self.prepare_build(&step, &scope).await?
        };
        let state = self.start_daemon(&timed, &current.worker, &prep).await?;
        let phases = timeline.take();
        // No line for a person: the text answer prints the same table.
        self.reporter.publish(
            Event::Structured {
                data: json!({
                    "event": "daemonPhases",
                    "action": verb.as_str(),
                    "phases": serde_json::to_value(&phases).map_err(|error| {
                        Refusal::internal(format!("cannot encode this command's own output: {error}"))
                    })?,
                }),
                line: String::new(),
            },
            Some(&Scope::worker(&current.worker)),
        );
        let text = format!(
            "daemon={}\nendpoint={}:{}\n{}",
            state.run_id,
            state.worker,
            state.port,
            render_phase_table(&phases)
        );
        let data = DaemonStartData {
            worker: current.worker.clone(),
            run_id: state.run_id,
            port: state.port,
            runtime_digest: prep.runtime_digest,
            product_digest: prep.product_digest,
            phases,
        };
        Outcome::new(&data, text)
    }

    async fn daemon_status_outcome(&self, ctx: &Ctx, worker: &str) -> Result<Outcome, Refusal> {
        let state = HostState::read(&self.settings, worker);
        let status = match &state {
            Some(state) => self.daemon.status(ctx, state).await?,
            None => None,
        };
        let text = match (&state, &status) {
            (Some(state), Some(status)) => format!(
                "daemon={}\nide={}\niterations={}",
                state.run_id,
                if status.ide_running { "running" } else { "stopped" },
                status.iteration_count
            ),
            _ => "no healthy daemon".to_owned(),
        };
        let data = DaemonStatusData {
            worker: worker.to_owned(),
            daemon: state.map(|state| DaemonName {
                endpoint: format!("{}:{}", state.worker, state.port),
                run_id: state.run_id,
            }),
            reachable: status.is_some(),
            status,
        };
        Outcome::new(&data, text)
    }

    async fn daemon_log(&self, ctx: &Ctx, worker: &str) -> Result<Outcome, Refusal> {
        let Some(state) = HostState::read(&self.settings, worker) else {
            return Err(Refusal::new(
                "daemon_missing",
                Exit::USAGE,
                "no daemon state; run `daemon start` first",
            ));
        };
        let channel = self.channel(worker);
        let reply = self
            .guest(ctx, channel.as_ref())
            .supervisor_log_reply(
                &words(["--root", &self.settings.vm_runs_root, "--run", &state.run_id, "--tail", "200"]),
                SupervisorOptions {
                    aqua: false,
                    timeout: DAEMON_LOG_TIMEOUT,
                },
            )
            .await?;
        // Exactly one trailing newline trimmed, never a second.
        let text = reply.content.strip_suffix('\n').unwrap_or(&reply.content).to_owned();
        Ok(Outcome {
            data: json!({"worker": worker, "runId": state.run_id, "content": reply.content}),
            text,
        })
    }
}
