//! One iteration as a value: the daemon decision, the push, the run, the JUnit retrieval and the report.

use std::path::PathBuf;
use std::time::Instant;

use crate::worker::worker::Lease;
use avl_base::clock::stamp;
use avl_base::format::{clip, seconds};
use avl_base::fs::create_private_dir;
use avl_base::{Exit, OrRefuse, Refusal, Scope};
use avl_host_sys::Ctx;
use avl_host_sys::guest::guest_join;
use avl_report::report::{self as run_report, Input};
use avl_wire::daemon::{self as wire, RunEvent};
use avl_wire::progress::{Decision, Event, IterationReported, Phase, Subject};
use avl_wire::report::{
    EvidenceArtifact, MAX_JUNIT_XML_BYTES, Retrieval, RetrievedIntegrity, RetrievedXml, RunReport, safe_evidence_paths,
};
use jiff::Timestamp;
use serde::Serialize;
use serde_json::json;
use tempfile::TempPath;
use tokio_util::sync::CancellationToken;

use crate::daemon::build::PreparedBuild;
use crate::daemon::host::{Host, WatchdogPolicy};
use crate::daemon::run::{RunExecution, RunSelection, push_hot_jars};
use crate::daemon::state::{HostState, guest_state_dir};
use crate::daemon::traces::TraceSync;
use crate::lane::secrets::{RunSecrets, remove_run_secrets, scan_artifacts, stage_run_secrets, withhold};
use avl_base::RefusalExt;
use avl_base::journal;

#[cfg(test)]
#[cfg(unix)]
mod tests;

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
    /// The shares were refreshed around a quiesced daemon: a remount on Tart and Parallels, a settle on Docker. The
    /// label is `remount` on every backend.
    #[serde(rename = "remount")]
    Remount,
    #[serde(rename = "relaunch")]
    Relaunch,
}

impl IdeAction {
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::Reuse => "reuse",
            Self::DaemonRestart => "daemon restart",
            Self::Remount => "remount",
            Self::Relaunch => "relaunch",
        }
    }
}

/// The digests that decide whether a daemon serves an iteration: of the daemon a record names, or of the build that
/// the iteration needs.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct DaemonDigests<'a> {
    pub launch: &'a str,
    pub mount: &'a str,
    pub runtime: &'a str,
}

/// The daemon that a worker's record names, when it answers as healthy.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct HealthyDaemon<'a> {
    pub recorded: DaemonDigests<'a>,
    pub ide_running: bool,
}

/// What an iteration does with the daemon before it runs: the answer of [`decide_daemon_action`].
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum DaemonAction {
    /// The healthy daemon serves the iteration as it is.
    Reuse,
    /// The healthy daemon serves it after a refresh of the shares.
    Remount,
    /// `--fresh-ide`: the run launches the IDE again, after a stop of the running one when there is one.
    Relaunch { stop_ide: bool },
    /// A daemon is started: `verb` is `restart` or `start`, and `reason` names the axis that moved.
    Start { verb: &'static str, reason: &'static str },
}

/// The daemon decision of one iteration: values in, a decision out, no I/O.
///
/// A healthy daemon of this launch serves the iteration: after a refresh of the shares when the mount digest moved,
/// after an IDE stop for `--fresh-ide`, or as it is. Any other daemon is started, and the decision names which axis
/// moved, because "restart the daemon" alone cannot say whether the next start will restage gigabytes or only
/// re-exec a JVM.
pub(crate) fn decide_daemon_action(healthy: Option<HealthyDaemon<'_>>, build: DaemonDigests<'_>, fresh_ide: bool) -> DaemonAction {
    let Some(HealthyDaemon { recorded, ide_running }) = healthy else {
        return DaemonAction::Start {
            verb: "start",
            reason: "no healthy daemon",
        };
    };
    if recorded.launch == build.launch {
        return if recorded.mount != build.mount {
            DaemonAction::Remount
        } else if fresh_ide {
            DaemonAction::Relaunch { stop_ide: ide_running }
        } else {
            DaemonAction::Reuse
        };
    }
    let reason = if recorded.runtime == build.runtime {
        "the controller boot settings changed"
    } else {
        "the stable runtime or JBR changed"
    };
    DaemonAction::Start { verb: "restart", reason }
}

/// The note of an iteration that reuses the IDE, which says whether a test jar changed.
pub(crate) const fn reuse_reason(pushed: usize) -> &'static str {
    if pushed == 0 {
        "no daemon input and no test jar changed"
    } else {
        "the daemon inputs did not change"
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
            runtime: &self.runtime_digest,
        }
    }
}

impl PreparedBuild {
    /// The digests of the daemon this build needs.
    pub(crate) fn daemon_digests(&self) -> DaemonDigests<'_> {
        DaemonDigests {
            launch: &self.launch_digest,
            mount: &self.mount_digest,
            runtime: &self.runtime_digest,
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
    /// The worker's lifecycle lock is the caller's: `lease` is the one [`crate::worker::lease::with_lease_operation`]
    /// handed it, and a second acquisition of the same lock by the same live process is refused rather than
    /// reclaimed, so an iteration that took its own lock could only ever run one deep. And an attempt that failed is
    /// an answer: N attempts across N workers cannot be reported at all if the first failure unwinds the caller.
    /// `run` is the degenerate case - one attempt, whose error it answers.
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

        // A worker that never needed a restart would otherwise run unbounded until a lane died of `ENOSPC`
        // mid-staging: the free-space check is reached through the stager, which only a start calls. One `df`
        // against a 200 s lane is free, and this is also where per-iteration retention lands - before the run,
        // never after it, because the evidence pull below reads out of exactly these trees and the current launch is
        // the newest thing in them.
        let channel = self.channel(worker);
        self.require_guest_free_space(&self.guest(ctx, channel.as_ref()), Some(&scope))
            .await?;

        let push_started = Instant::now();
        let pushed = push_hot_jars(ctx, &self.daemon, &state, prep).await?;
        let push_ms = push_started.elapsed().as_secs_f64() * 1000.0;
        if ide_action == IdeAction::Reuse {
            self.decide(Subject::Ide, "reuse", reuse_reason(pushed), &scope);
        }
        if let Some(reason) = jar_push_reason(pushed) {
            self.decide(Subject::Jars, "push", &reason, &scope);
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
                    .execute_run(ctx, worker, &state, prep, selection, policy, &mut |event| {
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
                attempt.timing = timing_line(prep, pushed, push_ms, ide_action, run_ms);
                // Kept on the attempt from here on: the events are its evidence whatever fails next.
                let execution = attempt.execution.insert(execution);
                state.last_product_digest = prep.product_digest.clone();
                state.last_mount_digest = prep.mount_digest.clone();
                state.write(&self.settings, worker)?;
                let Some(iteration_id) = execution.iteration_id.clone() else {
                    return Err(unaddressed_iteration(execution, &attempt.timing));
                };
                let source = self.retrieve_iteration_junit_xml(ctx, worker, &state, &iteration_id).await?;
                let evidence = self
                    .pull_run_evidence(ctx, worker, &state.run_id, &iteration_id, &execution.events)
                    .await?;
                // Stamped before the traces are packed and pulled, which can take minutes: the report's duration,
                // and a shard's wall time built from it, is the run's, and the evidence that explains a run must not
                // lengthen it.
                let completed_at = stamp(Timestamp::now());
                Ok::<_, Refusal>((iteration_id, source, evidence, completed_at))
            },
            traces.run(self, ctx, &stop),
        );
        let (iteration_id, source, evidence, completed_at) = collected?;
        let Some(execution) = attempt.execution.as_ref() else {
            return Err(Refusal::internal("an iteration was reported without its stream"));
        };
        let (archives, traces_error) = traces.finish(self, ctx, pulls).await;
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
            source,
            evidence,
            protocol_diagnostic: protocol_diagnostic(execution),
            hot_jar_drift: prep.hot_jar_drift(),
            traces: archives,
            traces_error,
            tree: prep.tree.clone().ok(),
            tree_error: prep.tree.as_ref().err().map(|refusal| refusal.message.clone()),
        })?;
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
        self.manager.machine().require_available_for_lease(ctx, lease).await?;
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
        let healthy = recorded.as_ref().zip(status.as_ref()).map(|(state, status)| HealthyDaemon {
            recorded: state.daemon_digests(),
            ide_running: status.ide_running,
        });
        if status.is_none() && !recreated {
            self.manager.require_ready(ctx, lease).await?;
        }
        let action = decide_daemon_action(healthy, prep.daemon_digests(), fresh_ide);
        let state = match (action, recorded) {
            (DaemonAction::Start { verb, reason }, _) => {
                self.decide(Subject::Daemon, verb, reason, scope);
                if status.is_some() {
                    self.manager.require_ready(ctx, lease).await?;
                }
                return Ok((self.start_daemon(ctx, worker, prep).await?, IdeAction::DaemonRestart));
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
                }
                IdeAction::Relaunch
            }
            DaemonAction::Reuse | DaemonAction::Start { .. } => IdeAction::Reuse,
        };
        Ok((state, ide_action))
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

    /// Quiesces the daemon around a refresh of the shares, and resumes it: a share-backed change never restarts
    /// the daemon.
    ///
    /// The refresh is [`Manager::refresh_shares`]: the VirtioFS remount sweep on Tart and Parallels, a settle for
    /// the bind mounts on Docker. The decision and the timing line say `remount` on every backend, because both are
    /// a contract with the docs and the suites; the note of the refresh says what it did.
    ///
    /// [`Manager::refresh_shares`]: crate::worker::worker::Manager::refresh_shares
    async fn remount_around(&self, ctx: &Ctx, worker: &str, scope: &Scope, state: &HostState, prep: &PreparedBuild) -> Result<(), Refusal> {
        let reason = if state.last_product_digest == prep.product_digest {
            "the share-backed runtime data changed"
        } else {
            "the product inputs changed"
        };
        self.decide(Subject::Shares, "remount", reason, scope);
        let (quiesced, _) = self.daemon.http(ctx, state, &wire::MOUNT_QUIESCE, None).await?;
        if !quiesced.is_success() {
            return Err(Refusal::new(
                "daemon_mount_quiesce_failed",
                Exit::SOFTWARE,
                format!("/mount/quiesce returned {}", quiesced.as_u16()),
            ));
        }
        self.manager.refresh_shares(ctx, worker).await?;
        let (resumed, _) = self.daemon.http(ctx, state, &wire::MOUNT_RESUME, None).await?;
        if !resumed.is_success() {
            return Err(Refusal::new(
                "daemon_mount_resume_failed",
                Exit::SOFTWARE,
                format!("/mount/resume returned {}", resumed.as_u16()),
            ));
        }
        if self.daemon.status(ctx, state).await?.is_none() {
            return Err(Refusal::new(
                "daemon_unhealthy_after_remount",
                Exit::SOFTWARE,
                "daemon did not resume after remount",
            ));
        }
        Ok(())
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

    /// One iteration's JUnit XML: the daemon's own `/results` route first, the guest-file pull as the fallback, and
    /// an honest account of both when neither worked.
    ///
    /// The HTTP diagnostic is carried into the fallback's answer rather than dropped, because a report that says
    /// only "guest pull failed" hides the first half of the story. [`MAX_JUNIT_XML_BYTES`] is enforced on both
    /// paths - an oversized document is a producer bug, and parsing it would spend the report's memory budget
    /// proving so.
    pub(crate) async fn retrieve_iteration_junit_xml(
        &self,
        ctx: &Ctx,
        worker: &str,
        state: &HostState,
        iteration_id: &str,
    ) -> Result<RetrievedXml, Refusal> {
        let guest_path = guest_join(
            &guest_join(&guest_join(&guest_state_dir(&self.settings), "iterations"), iteration_id),
            "test.xml",
        );
        let retrieved = |retrieval, integrity, xml: Option<String>, diagnostic: Option<String>| RetrievedXml {
            guest_path: guest_path.clone(),
            retrieval,
            integrity,
            xml,
            diagnostic,
        };
        let oversized = |bytes: u64| format!("JUnit XML is {bytes} bytes; limit is {MAX_JUNIT_XML_BYTES}");
        let http_diagnostic = match self.daemon.http(ctx, state, &wire::iteration_result(iteration_id), None).await {
            Err(refusal) => format!("daemon result endpoint unavailable: {}", refusal.message),
            Ok((status, content)) if status.is_success() => {
                if content.len() > MAX_JUNIT_XML_BYTES {
                    return Ok(retrieved(
                        Retrieval::DaemonHttp,
                        RetrievedIntegrity::Oversized,
                        None,
                        Some(oversized(content.len() as u64)),
                    ));
                }
                return Ok(retrieved(
                    Retrieval::DaemonHttp,
                    RetrievedIntegrity::Available,
                    Some(String::from_utf8_lossy(&content).into_owned()),
                    None,
                ));
            }
            Ok((status, _)) => format!("/results returned HTTP {}", status.as_u16()),
        };

        let incoming = self.settings.worker_dir(worker).join("reports").join(".incoming");
        create_private_dir(&incoming)?;
        // Removed when it goes out of scope, pulled or not.
        let temporary = TempPath::try_from_path(incoming.join(format!("{}-{iteration_id}-{}.xml", state.run_id, avl_base::new_id())))
            .or_refuse("state_write_failed", Exit::FAILURE, || {
                format!("cannot name a temporary under {}", incoming.display())
            })?;
        let pulled = match crate::lane::pull_guest_file(ctx, &self.manager, worker, &guest_path, &temporary).await {
            Ok(pulled) => pulled,
            Err(refusal) => {
                return Ok(retrieved(
                    Retrieval::Unavailable,
                    RetrievedIntegrity::Missing,
                    None,
                    Some(format!("{http_diagnostic}; guest pull failed: {}", refusal.message)),
                ));
            }
        };
        if pulled > MAX_JUNIT_XML_BYTES as u64 {
            return Ok(retrieved(
                Retrieval::GuestPull,
                RetrievedIntegrity::Oversized,
                None,
                Some(oversized(pulled)),
            ));
        }
        let xml = std::fs::read(&temporary).or_refuse("state_read_failed", Exit::FAILURE, || {
            format!("cannot read the pulled JUnit XML {}", temporary.display())
        })?;
        Ok(retrieved(
            Retrieval::GuestPull,
            RetrievedIntegrity::Available,
            Some(String::from_utf8_lossy(&xml).into_owned()),
            Some(http_diagnostic),
        ))
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
