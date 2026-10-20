//! One iteration as a value: the daemon decision, the push, the run, the evidence and the report.

use std::path::PathBuf;
use std::time::Instant;

use crate::worker::worker::Lease;
use avl_base::clock::stamp;
use avl_base::format::{clip, seconds};
use avl_base::fs::create_private_dir;
use avl_base::{Exit, Refusal, Scope};
use avl_host_sys::Ctx;
use avl_report::report::{self as run_report, Input};
use avl_wire::daemon::{self as wire, RunEvent, RunEventKind};
use avl_wire::progress::{Decision, Event, IterationReported, Phase, Subject};
use avl_wire::report::{EvidenceArtifact, RunReport, safe_evidence_paths};
use jiff::Timestamp;
use serde::Serialize;
use serde_json::json;
use tokio_util::sync::CancellationToken;

use crate::daemon::build::{PreparedBuild, lane_inputs_note, plugin_directories_note};
use crate::daemon::host::{Host, WatchdogPolicy};
use crate::daemon::run::{RunExecution, RunSelection, push_jars};
use crate::daemon::session_logs;
use crate::daemon::state::HostState;
use crate::daemon::traces::TraceSync;
use crate::lane::ide::{IdeRetention, gc_guest_ides, gc_note, running_guest_ides};
use crate::lane::secrets::{RunSecrets, remove_run_secrets, scan_artifacts, stage_run_secrets, withhold};
use avl_base::RefusalExt;
use avl_base::journal;

#[cfg(test)]
#[cfg(unix)]
mod tests;

/// The refusal of a remount while a lane IDE of the worker still runs, or while its runs cannot be read.
pub(crate) const REMOUNT_IDE_RUNNING: &str = "daemon_remount_ide_running";

/// Bounds the diagnostic a report carries from the stream: a run that failed with a megabyte of stack trace still
/// has a one-screen explanation.
const MAX_PROTOCOL_DIAGNOSTIC_BYTES: usize = 32 * 1024;

/// What an iteration did to the IDE before it ran, as the timing line and the run data name it.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize)]
pub(crate) enum IdeAction {
    /// Nothing moved: the running IDE served the iteration.
    #[default]
    #[serde(rename = "reuse")]
    Reuse,
    #[serde(rename = "daemon restart")]
    DaemonRestart,
    /// The daemon restarted, and the IDE of this product outlived it: the new daemon attaches to it.
    #[serde(rename = "keep")]
    Keep,
    /// The shares were refreshed while no lane IDE ran: a remount on Tart and Parallels, a settle on Docker. The label is
    /// `remount` on every backend.
    #[serde(rename = "remount")]
    Remount,
    #[serde(rename = "relaunch")]
    Relaunch,
    /// The lane daemon reloaded the bridge plugin in the running IDE.
    #[serde(rename = "reload")]
    Reload,
    /// The lane daemon launched the IDE on a fresh context.
    #[serde(rename = "fresh")]
    Fresh,
}

impl IdeAction {
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::Reuse => "reuse",
            Self::DaemonRestart => "daemon restart",
            Self::Keep => "keep",
            Self::Remount => "remount",
            Self::Relaunch => "relaunch",
            Self::Reload => "reload",
            Self::Fresh => "fresh",
        }
    }

    /// The action that the last `decision` record of the lane daemon about the IDE names, or `None` without one. The
    /// lane daemon decides the IDE of each iteration by the context plugin directories, so its word is the action.
    pub(crate) fn of_lane_daemon(events: &[RunEvent]) -> Option<Self> {
        events.iter().rev().find_map(|event| match &event.kind {
            RunEventKind::Decision(decision) if decision.subject == Subject::Ide => match decision.action.as_str() {
                "reuse" => Some(Self::Reuse),
                "reload" => Some(Self::Reload),
                "relaunch" => Some(Self::Relaunch),
                "fresh" => Some(Self::Fresh),
                _ => None,
            },
            _ => None,
        })
    }
}

/// The digests that decide whether a daemon serves an iteration: of the daemon a record names, or of the build that
/// the iteration needs.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct DaemonDigests<'a> {
    pub launch: &'a str,
    pub mount: &'a str,
    /// The context plugin identity. Empty in a record that predates it, which reads as unknown.
    pub plugins: &'a str,
}

/// The daemon that a worker's record names, when it answers as healthy.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct HealthyDaemon<'a> {
    pub recorded: DaemonDigests<'a>,
    pub ide_running: bool,
    /// The guest agent of a lane daemon, or `None` when the run does not know it: the record of the daemon names no agent.
    pub agent: Option<AgentDigests<'a>>,
}

/// The guest agent of a healthy lane daemon: the digest that its record names from its start, the digest of the agent
/// that the guest holds now, and the digest of the agent that the build of this checkout made. The installed agent
/// differs after an install of another build under the daemon, such as the release of a lease from another checkout.
/// The built agent differs after a change of the guest agent code in this checkout. `None` is a digest that the run does
/// not know.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct AgentDigests<'a> {
    pub started: &'a str,
    pub installed: Option<&'a str>,
    pub built: Option<&'a str>,
}

impl AgentDigests<'_> {
    fn moved(&self, digest: Option<&str>) -> bool {
        digest.is_some_and(|digest| digest != self.started)
    }
}

/// The reason of the restart of a lane daemon whose guest agent was reinstalled under it.
pub(crate) const AGENT_REINSTALLED: &str = "the guest agent was reinstalled under the lane daemon";

/// The reason of the restart of a lane daemon whose start agent is not the guest agent that the build made.
pub(crate) const AGENT_BUILT: &str = "the guest agent of the build changed";

/// The reason of the restart of a daemon of another launch digest. A static flag of the runtime descriptor, a boot
/// setting of the controller or the daemon environment changed.
pub(crate) const LAUNCH_CHANGED: &str = "the launch of the daemon changed";

/// What an iteration does with the daemon before it runs: the answer of [`decide_daemon_action`].
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum DaemonAction {
    /// The healthy daemon serves the iteration as it is.
    Reuse,
    /// The healthy daemon serves it after a refresh of the shares.
    Remount,
    /// `--fresh-ide`, or a healthy daemon whose IDE is not running: the run launches the IDE again, after a stop of the
    /// running one when there is one.
    Relaunch { stop_ide: bool },
    /// A daemon is started: `verb` is `restart` or `start`, and `reason` names the axis that moved. `keep_ide` is true
    /// for a restart: the healthy daemon had an IDE that the new one can attach to.
    Start {
        verb: &'static str,
        reason: &'static str,
        keep_ide: bool,
    },
}

/// The daemon decision of one iteration: values in, a decision out, no I/O.
///
/// A healthy daemon of this launch serves the iteration: after a refresh of the shares when the mount digest moved,
/// after an IDE stop for `--fresh-ide`, after a launch of the IDE when the daemon holds none, or as it is. The lane
/// daemon compares the digest of each context plugin directory itself, and it reloads the bridge plugin when only that
/// directory changed. A lane daemon whose guest agent was reinstalled under it is restarted instead, so the code of the daemon is the code of the install.
/// Any other daemon is started, and the decision names which axis moved, because "restart the daemon" alone cannot
/// say which input of the daemon changed. A restart keeps the IDE of the build's product. A start without a healthy
/// daemon keeps no IDE, because no daemon can show that the IDE still answers.
///
/// A lane daemon whose start agent is not the agent of the build is restarted, and that restart keeps no IDE. The
/// context writer is part of the guest agent, so the new daemon makes a fresh context and writes its files.
pub(crate) fn decide_daemon_action(healthy: Option<HealthyDaemon<'_>>, build: DaemonDigests<'_>, fresh_ide: bool) -> DaemonAction {
    let Some(HealthyDaemon {
        recorded,
        ide_running,
        agent,
    }) = healthy
    else {
        return DaemonAction::Start {
            verb: "start",
            reason: "no healthy daemon",
            keep_ide: false,
        };
    };
    let built_moved = agent.is_some_and(|agent| agent.moved(agent.built));
    if recorded.launch == build.launch {
        return if built_moved {
            DaemonAction::Start {
                verb: "restart",
                reason: AGENT_BUILT,
                keep_ide: false,
            }
        } else if agent.is_some_and(|agent| agent.moved(agent.installed)) {
            DaemonAction::Start {
                verb: "restart",
                reason: AGENT_REINSTALLED,
                keep_ide: true,
            }
        } else if recorded.mount != build.mount {
            DaemonAction::Remount
        } else if fresh_ide {
            DaemonAction::Relaunch { stop_ide: ide_running }
        } else if !ide_running {
            DaemonAction::Relaunch { stop_ide: false }
        } else {
            DaemonAction::Reuse
        };
    }
    DaemonAction::Start {
        verb: "restart",
        reason: LAUNCH_CHANGED,
        keep_ide: !built_moved,
    }
}

/// The note of the jar push, or `None` when no test jar changed.
pub(crate) fn jar_push_reason(pushed: usize) -> Option<String> {
    match pushed {
        0 => None,
        1 => Some("1 changed test jar".to_owned()),
        _ => Some(format!("{pushed} changed test jars")),
    }
}

/// The timing line of an iteration. Two spaces between fields, and this exact spelling: an agent compares timing
/// lines across runs.
pub(crate) fn timing_line(prep: &PreparedBuild, pushed: usize, push_ms: f64, ide_action: IdeAction, run_ms: f64) -> String {
    format!(
        "build {}  stamp {}  push {pushed} jar(s) {}  ide {}  tests {}",
        seconds(prep.build.as_secs_f64() * 1000.0),
        seconds(prep.stamp.as_secs_f64() * 1000.0),
        seconds(push_ms),
        ide_action.as_str(),
        seconds(run_ms)
    )
}

/// The refusal of a stream that ended before it named its iteration, so no result can be addressed: the protocol
/// failure when there is one, with the timing line.
pub(crate) fn unaddressed_iteration(execution: &RunExecution, timing: &str) -> Refusal {
    let (code, exit, message) = match &execution.protocol_failure {
        Some(failure) => (failure.code.clone(), failure.exit, failure.message.clone()),
        None => (
            "daemon_run_failed".into(),
            Exit::SOFTWARE,
            "the daemon stream ended before identifying its iteration".to_owned(),
        ),
    };
    Refusal::new(code, exit, format!("{message}\n{timing}")).with_details(json!({
        "report": null,
        "reportUnavailable": "runStarted was not received, so no iteration result can be addressed safely",
    }))
}

/// The diagnostic a report carries from the stream: the protocol failure and the daemon's own error, bounded, or
/// `None` when the stream named neither.
pub(crate) fn protocol_diagnostic(execution: &RunExecution) -> Option<String> {
    let mut diagnostic: Vec<&str> = Vec::new();
    if let Some(failure) = &execution.protocol_failure
        && !failure.message.is_empty()
    {
        diagnostic.push(&failure.message);
    }
    if let Some(failed) = &execution.failed
        && !failed.error.is_empty()
    {
        diagnostic.push(&failed.error);
    }
    let diagnostic = diagnostic.join("\n");
    (!diagnostic.is_empty()).then(|| clip(&diagnostic, MAX_PROTOCOL_DIAGNOSTIC_BYTES).to_owned())
}

impl HostState {
    /// The digests of the daemon this record names.
    pub(crate) fn daemon_digests(&self) -> DaemonDigests<'_> {
        DaemonDigests {
            launch: &self.launch_digest,
            mount: &self.last_mount_digest,
            plugins: &self.last_plugins_digest,
        }
    }
}

impl PreparedBuild {
    /// The digests of the daemon this build needs.
    pub(crate) fn daemon_digests(&self) -> DaemonDigests<'_> {
        DaemonDigests {
            launch: &self.launch_digest,
            mount: &self.mount_digest,
            plugins: &self.plugins_digest,
        }
    }
}

/// What one iteration runs, and on which worker: the inputs [`Host::run_one_iteration`] hands down unchanged.
#[derive(Clone, Copy)]
struct IterationInputs<'a> {
    lease: &'a Lease,
    prep: &'a PreparedBuild,
    selection: &'a RunSelection,
    policy: WatchdogPolicy,
    fresh_ide: bool,
}

/// One iteration on one worker, as a value.
#[derive(Clone, Debug, Default)]
pub(crate) struct RunAttempt {
    pub worker: String,
    /// The persisted report and where it is, or `None` when the iteration never named an iteration id that could
    /// be addressed.
    pub report: Option<(RunReport, PathBuf)>,
    /// Why this attempt produced no report, as a value. Raising it is the caller's decision.
    pub error: Option<Refusal>,
    /// `build … stamp … push … ide … tests …`, or empty when the attempt failed before the run was timed.
    pub timing: String,
    pub ide_action: IdeAction,
    /// What the daemon's stream said; present even for a failed attempt whose events are the only evidence.
    pub execution: Option<RunExecution>,
}

impl Host {
    /// One iteration on one worker, as a value.
    ///
    /// Lock-free and non-throwing, deliberately, because both callers that need several of these need it that way.
    /// The worker's run marker is the caller's: `lease` is the one [`crate::worker::lease::with_run_marker`] handed
    /// it. A second run marker of one process on one worker is refused. So an iteration that wrote its own marker
    /// could only ever run one deep. And an attempt that failed is an answer: N attempts across N workers cannot be
    /// reported at all if the first failure unwinds the caller. `run` is the degenerate case - one attempt, whose
    /// error it answers.
    ///
    /// `secrets` are `run`'s `--test-env` files, and empty for `shard` and `flake`.
    #[expect(clippy::too_many_arguments, reason = "one iteration's inputs, each from a different owner")]
    pub(crate) async fn run_one_iteration(
        &self,
        ctx: &Ctx,
        lease: &Lease,
        built: &PreparedBuild,
        selection: &RunSelection,
        policy: WatchdogPolicy,
        fresh_ide: bool,
        secrets: &RunSecrets,
    ) -> RunAttempt {
        let mut attempt = RunAttempt {
            worker: lease.worker.clone(),
            ..RunAttempt::default()
        };
        let iteration = IterationInputs {
            lease,
            prep: built,
            selection,
            policy,
            fresh_ide,
        };
        if let Err(refusal) = self.iterate(ctx, &iteration, secrets, &mut attempt).await {
            attempt.error = Some(refusal);
            attempt.report = None;
        }
        attempt
    }

    /// The iteration, with the run secrets around the run: written to the guest before `/run`, removed after it on
    /// every exit, and then looked for in what the run fetched back ([`crate::lane::secrets`]).
    ///
    /// A hit is the refusal of the attempt whatever else it concluded, because a report that holds a secret must not
    /// be published, green or red.
    async fn iterate(
        &self,
        ctx: &Ctx,
        iteration: &IterationInputs<'_>,
        secrets: &RunSecrets,
        attempt: &mut RunAttempt,
    ) -> Result<(), Refusal> {
        let ran = self.run_iteration(ctx, iteration, secrets, attempt).await;
        if secrets.is_empty() {
            return ran;
        }
        let worker = iteration.lease.worker.as_str();
        self.remove_run_secrets_from(ctx, worker, secrets).await;
        let hits = scan_artifacts(secrets, &self.fetched_text_artifacts(ctx, attempt));
        if !hits.is_empty() {
            let withheld = withhold(&hits, &self.settings.worker_dir(worker).join("withheld"));
            return Err(match &ran {
                Ok(()) => withheld,
                Err(failed) => {
                    let message = format!("{}\nthe iteration had also failed: {}", withheld.message, failed.code);
                    Refusal::new(withheld.code.clone(), withheld.exit, message).with_details(withheld.details())
                }
            });
        }
        ran
    }

    /// Removes this run's secret files from `worker`, best effort and bounded: a failure is a note, because the run's
    /// answer is already decided, and `daemon stop` and a lease release remove the whole directory. Nothing for a
    /// run without secrets.
    ///
    /// The teardown of every run that staged secrets: after its iteration, and after an interrupt dropped the
    /// iteration in flight ([`Host::leased_run`] keeps the lease then, so no release follows).
    pub(crate) async fn remove_run_secrets_from(&self, ctx: &Ctx, worker: &str, secrets: &RunSecrets) {
        if secrets.is_empty() {
            return;
        }
        let channel = self.channel(worker);
        if let Err(refusal) = remove_run_secrets(&self.guest(ctx, channel.as_ref()), secrets).await {
            self.reporter.note(
                format!(
                    "the run secrets could not be removed from {worker} ({}); `daemon stop` and a lease release remove them",
                    refusal.code
                ),
                Some(&Scope::worker(worker)),
            );
        }
    }

    /// The host files this attempt fetched that hold text: the persisted report, its `.txt` evidence, and the run's
    /// journal. Screenshots and trace archives are binary and are not read.
    fn fetched_text_artifacts(&self, ctx: &Ctx, attempt: &RunAttempt) -> Vec<PathBuf> {
        let mut paths = Vec::new();
        if let Some((report, path)) = &attempt.report {
            paths.push(path.clone());
            paths.extend(
                report
                    .evidence
                    .iter()
                    .filter_map(|artifact| artifact.artifact_path.as_deref())
                    .filter(|path| path.to_ascii_lowercase().ends_with(".txt"))
                    .map(PathBuf::from),
            );
        }
        if let Some(run_id) = ctx.run_id()
            && let Ok(directory) = journal::run_dir(&self.settings.runtime_root, run_id)
        {
            paths.push(directory.join(journal::FILE_NAME));
        }
        paths
    }

    /// The iteration itself: the gates, the daemon, the push, the secrets when there are any, the run, the
    /// retrieval and the report.
    async fn run_iteration(
        &self,
        ctx: &Ctx,
        iteration: &IterationInputs<'_>,
        secrets: &RunSecrets,
        attempt: &mut RunAttempt,
    ) -> Result<(), Refusal> {
        let IterationInputs {
            lease,
            prep,
            selection,
            policy,
            fresh_ide,
        } = *iteration;
        let worker = lease.worker.as_str();
        let scope = Scope::worker(worker);
        let recreated = self.gate_iteration(ctx, lease, &scope).await?;
        let (mut state, ide_action) = self.prepare_daemon(ctx, lease, prep, fresh_ide, recreated, &scope).await?;
        attempt.ide_action = ide_action;

        // A worker that never needed a restart would otherwise run unbounded until a lane died of `ENOSPC`. The
        // `free-space` phase of a start is the only other free-space check, and a warm iteration starts no daemon.
        // One `df` against a 200 s lane is free.
        let channel = self.channel(worker);
        self.require_guest_free_space(&self.guest(ctx, channel.as_ref())).await?;

        // The lane daemon decides about its IDE itself, and its `decision` record reaches the journal as one.
        let packed = session_logs::pack(&self.environment, &self.settings.runtime_root)?;
        let push_started = Instant::now();
        let pushed = push_jars(ctx, &self.daemon, &state, prep, &packed.files).await?;
        let push_ms = push_started.elapsed().as_secs_f64() * 1000.0;
        let session_logs = packed.into_logs();
        if !session_logs.is_empty() {
            let labels: Vec<&str> = session_logs.iter().map(|log| log.label.as_str()).collect();
            self.reporter
                .note(format!("recorded sessions: {}", labels.join(", ")), Some(&scope));
        }
        if let Some(reason) = jar_push_reason(pushed) {
            self.decide(Subject::Jars, "push", &reason, &scope);
        }
        self.reporter.note(lane_inputs_note(&prep.lane_inputs), Some(&scope));
        self.reporter.note(plugin_directories_note(&prep.plugin_directories), Some(&scope));
        if let Some(lane) = &selection.lane {
            self.publish_performance_plan(prep, lane, &scope);
        }
        if !secrets.is_empty() {
            // The names and never the files: a path a caller made with `mktemp` says nothing a reader needs.
            self.reporter
                .note(format!("run secrets: {}", secrets.names().join(", ")), Some(&scope));
            let (step, _phase) = ctx.begin("run-secrets");
            stage_run_secrets(&self.guest(&step, channel.as_ref()), secrets).await?;
        }

        let run_started = Instant::now();
        let controller_started_at = stamp(Timestamp::now());
        let iteration = self.reporter.start_phase(Phase::Iteration, &selection.description, Some(&scope));
        let traces = TraceSync::new(worker, ctx.run_id());
        let stop = CancellationToken::new();
        // The trace pulls run beside the stream and the retrieval, and stop when both are done - on every path, the
        // early ones included, because the guard fires on drop.
        let (collected, pulls) = tokio::join!(
            async {
                let _halt = stop.clone().drop_guard();
                let execution = self
                    .execute_run(ctx, worker, &state, prep, selection, policy, &session_logs, &mut |event| {
                        traces.observe(event);
                    })
                    .await;
                match &execution {
                    Err(refusal) => iteration.fail(refusal),
                    Ok(RunExecution {
                        protocol_failure: Some(failure),
                        ..
                    }) => iteration.fail(failure),
                    Ok(_) => iteration.succeed(),
                }
                let execution = execution?;
                let run_ms = run_started.elapsed().as_secs_f64() * 1000.0;
                if ide_action == IdeAction::Reuse
                    && let Some(decided) = IdeAction::of_lane_daemon(&execution.events)
                {
                    attempt.ide_action = decided;
                }
                attempt.timing = timing_line(prep, pushed, push_ms, attempt.ide_action, run_ms);
                // Kept on the attempt from here on: the events are its evidence whatever fails next.
                let execution = attempt.execution.insert(execution);
                state.last_product_digest = prep.product_digest.clone();
                state.last_mount_digest = prep.mount_digest.clone();
                state.last_plugins_digest = prep.plugins_digest.clone();
                state.write(&self.settings, worker)?;
                let Some(iteration_id) = execution.iteration_id.clone() else {
                    return Err(unaddressed_iteration(execution, &attempt.timing));
                };
                let evidence = self
                    .pull_run_evidence(ctx, worker, &state.run_id, &iteration_id, &execution.events)
                    .await?;
                // Stamped before the traces are packed and pulled, which can take minutes: the report's duration,
                // and a shard's wall time built from it, is the run's, and the evidence that explains a run must not
                // lengthen it.
                let completed_at = stamp(Timestamp::now());
                Ok::<_, Refusal>((iteration_id, evidence, completed_at))
            },
            traces.run(self, ctx, &stop),
        );
        let (iteration_id, evidence, completed_at) = collected?;
        let Some(execution) = attempt.execution.as_ref() else {
            return Err(Refusal::internal("an iteration was reported without its stream"));
        };
        let (archives, traces_error) = traces.finish(self, ctx, pulls).await;
        let drift = prep.pushed_file_drift();
        if let Some(drift) = &drift {
            self.reporter.note(drift.clone(), Some(&scope));
        }
        let document = run_report::build(Input {
            iteration_id,
            daemon_run_id: state.run_id.clone(),
            daemon_boot_stamp: state.daemon_boot_stamp.clone(),
            selection: selection.description.clone(),
            started_at: execution
                .iteration_started_at
                .clone()
                .filter(|started| !started.is_empty())
                .unwrap_or(controller_started_at),
            completed_at,
            summary: execution.summary.clone(),
            events: execution.events.clone(),
            evidence,
            protocol_diagnostic: protocol_diagnostic(execution),
            notes: drift.into_iter().collect(),
            traces: archives,
            traces_error,
            tree: prep.tree.clone().ok(),
            tree_error: prep.tree.as_ref().err().map(|refusal| refusal.message.clone()),
        });
        attempt.report = Some(self.publish_report(worker, &scope, selection, document)?);
        Ok(())
    }

    /// The gates a warm iteration would otherwise skip, because it skips `require_ready` while a healthy daemon
    /// answers. Answers whether the worker was made again.
    ///
    /// The version gate first. The empty executable it once also guarded against is closed in the backend: a guest
    /// line resolves its own head. Then the declaration check: a Docker worker whose image tag, shares or display
    /// changed is made again here, under the caller's lease. The daemon run died with the old container, so its
    /// record is forgotten: the start neither probes nor retires it, and the daemon starts as on a worker with no
    /// healthy daemon.
    async fn gate_iteration(&self, ctx: &Ctx, lease: &Lease, scope: &Scope) -> Result<bool, Refusal> {
        self.manager.machine().require_available(ctx, &lease.worker).await?;
        if self.manager.guest_declaration_current(ctx, lease).await? {
            return Ok(false);
        }
        self.decide(Subject::Worker, "recreate", "the container's declaration changed", scope);
        self.manager.require_ready(ctx, lease).await?;
        HostState::remove(&self.settings, &lease.worker)?;
        Ok(true)
    }

    /// Brings the daemon to what this iteration needs ([`decide_daemon_action`]) and answers its state and what the
    /// iteration did to the IDE. Without a healthy daemon the worker has to be brought up before anything can be
    /// launched on it; a worker made again by the gate is already up.
    async fn prepare_daemon(
        &self,
        ctx: &Ctx,
        lease: &Lease,
        prep: &PreparedBuild,
        fresh_ide: bool,
        recreated: bool,
        scope: &Scope,
    ) -> Result<(HostState, IdeAction), Refusal> {
        let worker = lease.worker.as_str();
        let recorded = HostState::read(&self.settings, worker);
        let status = match &recorded {
            Some(state) => self.daemon.status(ctx, state).await?,
            None => None,
        };
        let (installed_agent, built_agent) = match (&recorded, &status) {
            (Some(state), Some(_)) => (
                self.installed_agent(ctx, state, scope).await,
                self.built_agent(ctx, state, scope).await,
            ),
            _ => (None, None),
        };
        let healthy = recorded.as_ref().zip(status.as_ref()).map(|(state, status)| HealthyDaemon {
            recorded: state.daemon_digests(),
            ide_running: status.ide_running,
            agent: (!state.agent_digest.is_empty()).then(|| AgentDigests {
                started: &state.agent_digest,
                installed: installed_agent.as_deref(),
                built: built_agent.as_deref(),
            }),
        });
        if status.is_none() && !recreated {
            self.manager.require_ready(ctx, lease).await?;
        }
        let action = decide_daemon_action(healthy, prep.daemon_digests(), fresh_ide);
        let state = match (action, recorded) {
            (DaemonAction::Start { verb, reason, keep_ide }, _) => {
                self.decide(Subject::Daemon, verb, reason, scope);
                if status.is_some() {
                    self.manager.require_ready(ctx, lease).await?;
                }
                let retention = if keep_ide {
                    IdeRetention::KeepProduct(&prep.product_digest)
                } else {
                    IdeRetention::StopAll
                };
                let (state, ides) = self.start_daemon_keeping(ctx, worker, prep, retention).await?;
                if ides.kept.is_empty() {
                    return Ok((state, IdeAction::DaemonRestart));
                }
                self.decide(Subject::Ide, "keep", "the product did not change", scope);
                return Ok((state, IdeAction::Keep));
            }
            (_, None) => return Err(Refusal::internal("a healthy daemon was decided without its record")),
            (_, Some(state)) => state,
        };
        let ide_action = match action {
            DaemonAction::Remount => {
                self.remount_around(ctx, worker, scope, &state, prep).await?;
                IdeAction::Remount
            }
            DaemonAction::Relaunch { stop_ide } => {
                if stop_ide {
                    self.decide(Subject::Ide, "relaunch", "--fresh-ide", scope);
                    let (stopped, _) = self.daemon.http(ctx, &state, &wire::IDE_STOP, None).await?;
                    if !stopped.is_success() {
                        return Err(Refusal::new(
                            "daemon_ide_stop_failed",
                            Exit::SOFTWARE,
                            format!("/ide/stop returned {}", stopped.as_u16()),
                        ));
                    }
                } else {
                    self.decide(Subject::Ide, "relaunch", "the daemon holds no running IDE", scope);
                }
                IdeAction::Relaunch
            }
            DaemonAction::Reuse | DaemonAction::Start { .. } => IdeAction::Reuse,
        };
        Ok((state, ide_action))
    }

    /// The digest of the guest agent that the guest of the healthy daemon `state` holds, when the record names the agent
    /// of its start. Otherwise the run does not ask the guest. One `contract` exec of the
    /// agent path answers, about 0.1 s. A digest that moved is a note of the run stream with both digests, and so is a
    /// guest that does not say: then the daemon stays.
    pub(crate) async fn installed_agent(&self, ctx: &Ctx, state: &HostState, scope: &Scope) -> Option<String> {
        if state.agent_digest.is_empty() {
            return None;
        }
        let channel = self.channel(&state.worker);
        let stays = |why: String| format!("the lane daemon stays, because {why}");
        match self.guest(ctx, channel.as_ref()).installed_agent_digest().await {
            Ok(Some(installed)) => {
                if installed != state.agent_digest {
                    self.reporter.note(
                        format!(
                            "the lane daemon runs the guest agent {} of its start, and the guest holds {} now",
                            short(&state.agent_digest),
                            short(&installed)
                        ),
                        Some(scope),
                    );
                }
                Some(installed)
            }
            Ok(None) => {
                self.reporter
                    .note(stays("the guest agent names no digest of its own".to_owned()), Some(scope));
                None
            }
            Err(refusal) => {
                self.reporter.note(
                    stays(format!("the guest agent did not say which build it is: {}", refusal.message)),
                    Some(scope),
                );
                None
            }
        }
    }

    /// The digest of the guest agent that the build of this checkout made, when the record of the healthy daemon `state`
    /// names the agent of its start. Otherwise the run reads nothing. A digest that
    /// moved is a note of the run stream with both digests, and so is an agent that cannot be read: then the daemon stays.
    pub(crate) async fn built_agent(&self, ctx: &Ctx, state: &HostState, scope: &Scope) -> Option<String> {
        if state.agent_digest.is_empty() {
            return None;
        }
        let channel = self.channel(&state.worker);
        match self.guest(ctx, channel.as_ref()).built_agent_digest(self.bazel.as_ref()).await {
            Ok(built) => {
                if built != state.agent_digest {
                    self.reporter.note(
                        format!(
                            "the lane daemon runs the guest agent {} of its start, and the build made {}",
                            short(&state.agent_digest),
                            short(&built)
                        ),
                        Some(scope),
                    );
                }
                Some(built)
            }
            Err(refusal) => {
                self.reporter.note(
                    format!(
                        "the lane daemon stays, because the guest agent of the build cannot be read: {}",
                        refusal.message
                    ),
                    Some(scope),
                );
                None
            }
        }
    }

    /// Publishes the IJ Perf plan of an iteration of `lane`, which the results read. A plan that cannot be made is a note,
    /// because a metric never decides a verdict.
    fn publish_performance_plan(&self, prep: &PreparedBuild, lane: &str, scope: &Scope) {
        match crate::daemon::performance::plan(prep, lane) {
            Ok(Some(plan)) => self.reporter.publish(
                Event::Structured {
                    data: plan.record(),
                    line: String::new(),
                },
                Some(scope),
            ),
            Ok(None) => {}
            Err(reason) => self
                .reporter
                .note(format!("no IJ Perf file for this iteration: {reason}"), Some(scope)),
        }
    }

    /// Persists the report of an iteration and announces it; answers the report and its path.
    fn publish_report(
        &self,
        worker: &str,
        scope: &Scope,
        selection: &RunSelection,
        document: RunReport,
    ) -> Result<(RunReport, PathBuf), Refusal> {
        let path = run_report::persist(&self.settings, worker, &document)?;
        self.reporter.publish(
            Event::IterationReported(IterationReported {
                iteration_id: document.iteration_id.clone(),
                daemon_run_id: document.daemon_run_id.clone(),
                selection: selection.description.clone(),
                status: document.status,
                report_path: path.to_string_lossy().into_owned(),
                tests_failed: document.execution.tests_failed,
                tests_started: document.execution.tests_started,
            }),
            Some(scope),
        );
        Ok((document, path))
    }

    /// Stops every lane IDE around a refresh of the shares: a share-backed change never restarts the daemon.
    ///
    /// The refresh is [`Manager::refresh_shares`]: the VirtioFS remount sweep on Tart and Parallels, a settle for
    /// the bind mounts on Docker. The decision and the timing line say `remount` on every backend, because both are
    /// a contract with the docs and the suites; the note of the refresh says what it did.
    ///
    /// `/ide/stop` makes the daemon quit and forget its IDE, and `ide-gc --stop-all` cancels every other IDE run of the
    /// worker, such as one that outlived an earlier daemon. The guest agent owns each IDE process. So the refresh starts
    /// only when the supervisor reports every IDE run finished: an IDE that runs from a share makes its unmount busy.
    /// The controller holds the lease, so no run starts in between, and the next run launches the IDE again. The lane
    /// daemon serves `/ide/stop`, so the remount needs no route of its own.
    ///
    /// [`Manager::refresh_shares`]: crate::worker::worker::Manager::refresh_shares
    async fn remount_around(&self, ctx: &Ctx, worker: &str, scope: &Scope, state: &HostState, prep: &PreparedBuild) -> Result<(), Refusal> {
        let reason = if state.last_product_digest == prep.product_digest {
            "the share-backed runtime data changed"
        } else {
            "the product inputs changed"
        };
        self.decide(Subject::Shares, "remount", reason, scope);
        let (stopped, _) = self.daemon.http(ctx, state, &wire::IDE_STOP, None).await?;
        if !stopped.is_success() {
            return Err(Refusal::new(
                "daemon_ide_stop_failed",
                Exit::SOFTWARE,
                format!("/ide/stop returned {} before the remount", stopped.as_u16()),
            ));
        }
        {
            let channel = self.channel(worker);
            let ides = gc_guest_ides(&self.guest(ctx, channel.as_ref()), IdeRetention::StopAll).await?;
            if let Some(note) = gc_note(&ides) {
                self.reporter.note(format!("{note} on {worker} before the remount"), Some(scope));
            }
        }
        self.require_no_running_ide(ctx, worker).await?;
        self.manager.refresh_shares(ctx, worker).await?;
        if self.daemon.status(ctx, state).await?.is_none() {
            return Err(Refusal::new(
                "daemon_unhealthy_after_remount",
                Exit::SOFTWARE,
                "daemon did not resume after remount",
            ));
        }
        Ok(())
    }

    /// Refuses [`REMOUNT_IDE_RUNNING`] while a lane IDE run of the worker is not finished, and when the supervisor cannot
    /// say. It fails closed, because a refresh under a live IDE unmounts the share that the IDE runs from.
    async fn require_no_running_ide(&self, ctx: &Ctx, worker: &str) -> Result<(), Refusal> {
        let channel = self.channel(worker);
        let running = running_guest_ides(&self.guest(ctx, channel.as_ref())).await.map_err(|refusal| {
            Refusal::new(
                REMOUNT_IDE_RUNNING,
                Exit::SOFTWARE,
                format!(
                    "the lane IDEs of {worker} were stopped, but their runs cannot be read ({}: {})",
                    refusal.code, refusal.message
                ),
            )
        })?;
        if running.is_empty() {
            return Ok(());
        }
        let runs: Vec<String> = running
            .iter()
            .map(|(root, state)| format!("{} in {root} ({})", state.run_id, state.phase))
            .collect();
        Err(Refusal::new(
            REMOUNT_IDE_RUNNING,
            Exit::SOFTWARE,
            format!(
                "the lane IDEs of {worker} were stopped, but one still runs: {}; the share refresh would unmount the \
                 share it runs from",
                runs.join(", ")
            ),
        )
        .with_details(json!({ "runs": running.iter().map(|(_, state)| state.run_id.clone()).collect::<Vec<_>>() })))
    }

    /// Publishes a choice this iteration made about the daemon, the shares or the IDE, and why.
    fn decide(&self, subject: Subject, action: &str, reason: &str, scope: &Scope) {
        self.reporter.publish(
            Event::Decision(Decision {
                subject,
                action: action.to_owned(),
                reason: Some(reason.to_owned()),
            }),
            Some(scope),
        );
    }

    /// Fetches whatever the daemon named as evidence for an expiry, next to the report it belongs to.
    ///
    /// The screenshots that explained the EAP-expiry hang had been on the guest the whole time, and the run verdict
    /// never mentioned them; the point of pulling them here is that nobody has to know they exist. A file that
    /// cannot be fetched is recorded with its reason rather than dropped - a missing artifact is itself worth
    /// seeing, and evidence is never allowed to turn into the failure it is describing.
    pub(crate) async fn pull_run_evidence(
        &self,
        ctx: &Ctx,
        worker: &str,
        daemon_run_id: &str,
        iteration_id: &str,
        events: &[RunEvent],
    ) -> Result<Vec<EvidenceArtifact>, Refusal> {
        let guest_paths = safe_evidence_paths(events);
        if guest_paths.is_empty() {
            return Ok(Vec::new());
        }
        let directory = run_report::evidence_directory(&self.settings, worker, daemon_run_id, iteration_id)?;
        create_private_dir(&directory)?;
        let mut artifacts = Vec::with_capacity(guest_paths.len());
        for guest_path in guest_paths {
            let destination = directory.join(evidence_name(&guest_path));
            let pulled = crate::lane::pull_guest_file(ctx, &self.manager, worker, &guest_path, &destination).await;
            artifacts.push(match pulled {
                Ok(_) => EvidenceArtifact {
                    guest_path,
                    artifact_path: Some(destination.to_string_lossy().into_owned()),
                    error: None,
                },
                Err(refusal) => EvidenceArtifact {
                    guest_path,
                    artifact_path: None,
                    error: Some(refusal.message),
                },
            });
        }
        Ok(artifacts)
    }
}

/// The host file name of one evidence file: its last two guest path segments joined by `_`, because the capture
/// directory is what distinguishes `001_heartbeat/dialog0.png` from `005_heartbeat/dialog0.png`, with every
/// character outside `[A-Za-z0-9._-]` replaced.
pub(crate) fn evidence_name(guest_path: &str) -> String {
    let segments: Vec<&str> = guest_path.split('/').collect();
    let tail = &segments[segments.len().saturating_sub(2)..];
    tail.join("_")
        .chars()
        .map(|character| {
            if character.is_ascii_alphanumeric() || matches!(character, '.' | '_' | '-') {
                character
            } else {
                '-'
            }
        })
        .collect()
}

/// The first twelve characters of a digest, the way notes name a guest agent.
fn short(digest: &str) -> &str {
    digest.get(..12).unwrap_or(digest)
}
