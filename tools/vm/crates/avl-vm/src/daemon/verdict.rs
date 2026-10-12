//! The verdict of a run: counts, failures, skips, the traces that did not pass, the checkout, and the reproduce list.
//!
//! [`conclude`] is the one place that decides what a report concluded: the published [`Verdict`]'s summary, the
//! refusal's code and exit, and its one-line message all come from it. [`iteration_verdict`] asks it about one
//! iteration, and `shard` about its merged run, whose verdict it builds from the helpers below.

use std::borrow::Cow;
use std::collections::HashSet;
use std::path::Path;

use avl_base::format::first_line;
use avl_base::plain::release_command;
use avl_base::{Exit, Refusal};
use avl_trace_tools::discover::Status as BundleStatus;
use avl_wire::progress::{Checkout, Failure, LaneVerdict, LeaseDisposition, Skip, TraceRef, Verdict, VerdictCounts, VerdictTraces};
use avl_wire::report::{ExecutionCounts, Failure as ReportFailure, RunReport, SkippedContainer, Status, TraceBundle};

use crate::daemon::iterate::RunAttempt;
use crate::daemon::run::RunSelection;

/// The verdict of one iteration that left a report, and the refusal the command answers with, which is `None` for
/// a passed iteration.
pub(crate) fn iteration_verdict(
    selection: &RunSelection,
    document: &RunReport,
    report_path: &Path,
    attempt: &RunAttempt,
) -> (Verdict, Option<Refusal>) {
    let mut verdict = Verdict {
        status: document.status,
        code: None,
        summary: String::new(),
        counts: counts_of(&document.execution),
        failures: failures_of(&document.failures, &bundles_of([document])),
        rerun: reproduce_commands(&document.failures),
        skipped: skips_of(&document.skipped_containers),
        unreported: document.unreported_classes.clone(),
        lanes: Vec::new(),
        shards: None,
        lease: None,
        checkout: checkout_of(document),
        traces: traces_of([document]),
        timing: Some(attempt.timing.clone()).filter(|timing| !timing.is_empty()),
        report: Some(report_path.to_string_lossy().into_owned()),
    };
    let execution = attempt.execution.as_ref();
    let protocol_failure = execution.and_then(|execution| execution.protocol_failure.as_ref());
    let watchdog_expired = execution.is_some_and(|execution| execution.watchdog_expired.is_some());
    let stream_failed = execution.is_some_and(|execution| execution.failed.is_some());
    // The stream's own account decides here even when the report's status does not say so, because a stream that
    // broke is no authoritative result whatever the XML says.
    if protocol_failure.is_some() || stream_failed || watchdog_expired {
        verdict.status = Status::InfrastructureError;
    }
    let infrastructure = match protocol_failure {
        Some(failure) => (failure.code.clone(), failure.exit),
        None if watchdog_expired => ("daemon_watchdog_expired".into(), Exit::SOFTWARE),
        None => ("daemon_run_failed".into(), Exit::SOFTWARE),
    };
    let (summary, refused) = conclude(
        verdict.status,
        infrastructure,
        document.verdict_diagnostic.as_deref(),
        &document.execution,
        document.failures.first(),
        &selection.description,
        Reach::Iteration(&document.iteration_id),
    );
    verdict.summary = summary;
    verdict.code = refused.as_ref().map(|refused| refused.code.to_string());
    (verdict, refused)
}

/// What a conclusion was reached over, which is all its wording differs by.
#[derive(Clone, Copy, Debug)]
pub(crate) enum Reach<'a> {
    /// One daemon iteration, by its id.
    Iteration(&'a str),
    /// The merge of this many shards.
    Shards(usize),
}

/// What a report, or the merge of a sharded run's reports, concluded: the verdict's one-line summary, and the
/// refusal the command answers with, which is `None` for a pass. Every human form renders the published verdict, so
/// the refusal's message is that line.
///
/// The caller decides the status and the code and exit of an infrastructure verdict, because they come from
/// different evidence: a run's from its own stream (a protocol failure, the watchdog), a shard run's from the merge
/// ladder. The other codes are the same for both, so an agent branches on one code whether it ran one worker or
/// several. That includes a merge whose every shard was skipped or found nothing: its aggregate carries its own
/// `shard_*` code, and the verdict still answers with the run's.
pub(crate) fn conclude(
    status: Status,
    infrastructure: (Cow<'static, str>, Exit),
    diagnostic: Option<&str>,
    execution: &ExecutionCounts,
    first_failure: Option<&ReportFailure>,
    selection: &str,
    reach: Reach<'_>,
) -> (String, Option<Refusal>) {
    let (code, exit, summary): (Cow<'static, str>, Exit, String) = match status {
        Status::InfrastructureError => {
            let summary = diagnostic.map_or_else(
                || match reach {
                    Reach::Iteration(_) => "the daemon iteration did not produce an authoritative result".to_owned(),
                    Reach::Shards(_) => "the sharded run produced no authoritative result".to_owned(),
                },
                str::to_owned,
            );
            (infrastructure.0, infrastructure.1, summary)
        }
        Status::Failed => {
            let first = first_failure.map_or_else(String::new, |failure| {
                format!(
                    "; first: {} — {}",
                    failure_class(failure).unwrap_or("unknown"),
                    first_line(&failure.message)
                )
            });
            let over = match reach {
                Reach::Iteration(id) => format!("(iteration {id})"),
                Reach::Shards(count) => format!("across {count} shard(s)"),
            };
            let summary = format!(
                "{} test(s) and {} container(s) failed of {} started {over}{first}",
                execution.tests_failed, execution.container_failures, execution.tests_started
            );
            ("tests_failed".into(), Exit::TESTS_FAILED, summary)
        }
        // Every class was ruled out by a condition, which is still no coverage. The reasons are known, and the
        // verdict names each class with its reason. A guest without an agent CLI is the ordinary way here.
        Status::AllSkipped => {
            let summary = match reach {
                Reach::Iteration(id) => {
                    format!("{selection} ran nothing: every class was skipped (iteration {id})")
                }
                Reach::Shards(count) => {
                    format!("{selection} ran nothing across {count} shard(s): every class was skipped")
                }
            };
            ("all_tests_skipped".into(), Exit::TESTS_FAILED, summary)
        }
        // A selection that matches nothing is the classic false green of this repository: the run is "successful"
        // because nothing ran. The daemon cannot judge intent, so the controller does, because a `run` always names
        // something it expects to execute.
        Status::NoTests => {
            let summary = match reach {
                Reach::Iteration(id) => format!("{selection} matched no tests in the hot tier (iteration {id}); check the selector's FQN"),
                Reach::Shards(count) => format!(
                    "{selection} matched no tests in any of {count} shard(s); the split covers the whole lane by \
                     construction, so this is the lane's own selection"
                ),
            };
            ("no_tests_discovered".into(), Exit::TESTS_FAILED, summary)
        }
        Status::Passed => {
            let summary = match reach {
                Reach::Iteration(_) => format!("{} test(s) passed", execution.tests_started),
                Reach::Shards(count) => format!("{} test(s) passed across {count} shard(s)", execution.tests_started),
            };
            return (summary, None);
        }
    };
    let refusal = Refusal::new(code, exit, summary.clone());
    (summary, Some(refusal))
}

/// The verdict of a run of two or more lanes, from the verdict of each lane: the worst lane's status and code, its
/// summary under the lane's name, and the counts, failures and rerun commands of all lanes.
pub(crate) fn lanes_verdict(lanes: &[String], verdicts: &[Verdict]) -> Verdict {
    let mut merged = Verdict {
        status: Status::Passed,
        code: None,
        summary: String::new(),
        counts: VerdictCounts::default(),
        failures: Vec::new(),
        rerun: Vec::new(),
        skipped: Vec::new(),
        unreported: Vec::new(),
        lanes: Vec::new(),
        shards: None,
        lease: None,
        checkout: None,
        traces: None,
        timing: None,
        report: None,
    };
    let mut worst: Option<usize> = None;
    for (index, (lane, verdict)) in lanes.iter().zip(verdicts).enumerate() {
        merged.lanes.push(LaneVerdict {
            lane: lane.clone(),
            status: verdict.status,
            code: verdict.code.clone(),
            summary: verdict.summary.clone(),
            counts: verdict.counts,
            report: verdict.report.clone(),
        });
        merged.counts = merged.counts + verdict.counts;
        merged.failures.extend(verdict.failures.iter().cloned());
        for command in &verdict.rerun {
            if !merged.rerun.contains(command) {
                merged.rerun.push(command.clone());
            }
        }
        merged.skipped.extend(verdict.skipped.iter().cloned());
        merged.unreported.extend(verdict.unreported.iter().cloned());
        merged.traces = merge_traces(merged.traces.take(), verdict.traces.as_ref());
        if merged.checkout.is_none() {
            merged.checkout.clone_from(&verdict.checkout);
        }
        if worst.is_none_or(|worst| status_rank(verdict.status) > status_rank(verdicts[worst].status)) {
            worst = Some(index);
        }
    }
    if let Some(worst) = worst.filter(|worst| verdicts[*worst].status != Status::Passed) {
        merged.status = verdicts[worst].status;
        merged.code.clone_from(&verdicts[worst].code);
        merged.summary = format!("lane {}: {}", lanes[worst], verdicts[worst].summary);
        return merged;
    }
    merged.summary = format!("{} test(s) passed in {} lanes", merged.counts.started, verdicts.len());
    merged
}

/// Orders the verdict statuses: passed, then red, then no verdict at all.
const fn status_rank(status: Status) -> u8 {
    match status {
        Status::Passed => 0,
        Status::InfrastructureError => 2,
        Status::Failed | Status::NoTests | Status::AllSkipped => 1,
    }
}

/// The daemon's counts of a report, as a verdict names them.
pub(crate) const fn counts_of(execution: &ExecutionCounts) -> VerdictCounts {
    VerdictCounts {
        started: execution.tests_started,
        failed: execution.tests_failed,
        skipped: execution.tests_skipped,
        containers_skipped: execution.containers_skipped,
        container_failures: execution.container_failures,
    }
}

/// The failures of a report as a verdict names them, each with the trace of its scenario when one of the bundles
/// recorded it. `shard` names the failures of its aggregate with it too.
pub(crate) fn failures_of(failures: &[ReportFailure], bundles: &[TraceBundle]) -> Vec<Failure> {
    failures
        .iter()
        .map(|failure| Failure {
            class: failure_class(failure).unwrap_or_default().to_owned(),
            name: Some(failure.test_name.clone()).filter(|name| !name.is_empty()),
            message: Some(first_line(&failure.message).to_owned()).filter(|line| !line.is_empty()),
            trace: trace_of(failure, bundles),
        })
        .collect()
}

/// The class a failure names, or its suite.
fn failure_class(failure: &ReportFailure) -> Option<&str> {
    failure.class_name.as_deref().or(failure.suite.as_deref())
}

/// The trace of a failure's scenario. A bundle names its class by the simple name and the report by the FQN, so
/// the simple names are compared. A failure from the stream names its test by the scenario; one from the XML names
/// it as the JUnit reporter does, which can add an index or parentheses, so the test name must contain the
/// scenario.
fn trace_of(failure: &ReportFailure, bundles: &[TraceBundle]) -> Option<TraceRef> {
    let class = simple_name(failure.class_name.as_deref()?);
    bundles
        .iter()
        .find(|bundle| {
            simple_name(&bundle.test_class) == class && !bundle.scenario.is_empty() && failure.test_name.contains(&bundle.scenario)
        })
        .map(trace_ref)
}

fn trace_ref(bundle: &TraceBundle) -> TraceRef {
    TraceRef {
        bundle_id: bundle.id.clone(),
        test_class: bundle.test_class.clone(),
        scenario: bundle.scenario.clone(),
        status: bundle.status.clone(),
        has_video: bundle.has_video,
    }
}

fn simple_name(class_name: &str) -> &str {
    class_name.rsplit('.').next().unwrap_or(class_name)
}

/// What re-runs each failed class on its own: `run <FQN>`, once per class.
///
/// A red lane leaves a caller holding class names and having to reassemble a command out of them, which is the step
/// that turns a two-minute narrowing into a re-run of the whole lane. So the verdict carries it.
///
/// Deliberately not a field of the versioned report: the report is declared once for the guest and the host both,
/// and a controller command spelling is host knowledge the guest has no business holding. The program and the
/// receipt are left off for the same reason - they belong to the caller, so what is offered is the argv after
/// them, exactly as the help of `run` spells it.
///
/// A container failure that named no class is skipped rather than guessed at: `run` takes a class or an FQN, and a
/// suite display name is neither.
pub(crate) fn reproduce_commands(failures: &[ReportFailure]) -> Vec<String> {
    let mut seen = HashSet::new();
    failures
        .iter()
        .filter_map(|failure| failure.class_name.as_deref())
        .filter(|class| !class.is_empty() && seen.insert(*class))
        .map(|class| format!("run {class}"))
        .collect()
}

/// Each class a JUnit condition ruled out, by its class name or else its display name, with the reason.
pub(crate) fn skips_of(containers: &[SkippedContainer]) -> Vec<Skip> {
    containers
        .iter()
        .map(|container| Skip {
            name: container.class_name.clone().unwrap_or_else(|| container.display_name.clone()),
            reason: container.reason.clone(),
        })
        .collect()
}

/// Every trace bundle of the reports.
pub(crate) fn bundles_of<'a>(documents: impl IntoIterator<Item = &'a RunReport>) -> Vec<TraceBundle> {
    documents
        .into_iter()
        .flat_map(|document| &document.trace_archives)
        .flat_map(|archive| archive.bundles.iter().cloned())
        .collect()
}

/// The traces of the reports together, or `None` when they recorded none and no pull failed.
pub(crate) fn traces_of<'a>(documents: impl IntoIterator<Item = &'a RunReport>) -> Option<VerdictTraces> {
    documents
        .into_iter()
        .fold(None, |merged, document| merge_traces(merged, iteration_traces(document).as_ref()))
}

/// The traces of one iteration, or `None` when it recorded none and no pull failed.
fn iteration_traces(document: &RunReport) -> Option<VerdictTraces> {
    let bundles = bundles_of([document]);
    if bundles.is_empty() && document.traces_error.is_none() {
        return None;
    }
    Some(VerdictTraces {
        scenarios: u32::try_from(bundles.len()).unwrap_or(u32::MAX),
        not_passed: bundles
            .iter()
            .filter(|bundle| bundle.status != BundleStatus::Passed.as_str())
            .map(trace_ref)
            .collect(),
        with_video: u32::try_from(bundles.iter().filter(|bundle| bundle.has_video).count()).unwrap_or(u32::MAX),
        directory: document.trace_archives.first().map(|archive| parent_of(&archive.path)),
        error: document.traces_error.clone(),
    })
}

/// The traces of two iterations together. The directory of two iterations is the run's traces directory, the
/// parent of each iteration's directory.
fn merge_traces(merged: Option<VerdictTraces>, lane: Option<&VerdictTraces>) -> Option<VerdictTraces> {
    let Some(lane) = lane else {
        return merged;
    };
    let Some(mut merged) = merged else {
        return Some(lane.clone());
    };
    merged.scenarios += lane.scenarios;
    merged.not_passed.extend(lane.not_passed.iter().cloned());
    merged.with_video += lane.with_video;
    if let Some(directory) = &lane.directory
        && merged.directory.as_ref() != Some(directory)
    {
        merged.directory = Some(parent_of(directory));
    }
    if merged.error.is_none() {
        merged.error.clone_from(&lane.error);
    }
    Some(merged)
}

fn parent_of(path: &str) -> String {
    Path::new(path)
        .parent()
        .map_or_else(String::new, |parent| parent.to_string_lossy().into_owned())
}

/// The checkout that the build read, as the report states it, or `None` for a report from before the tree fact.
pub(crate) fn checkout_of(document: &RunReport) -> Option<Checkout> {
    match (&document.tree, &document.tree_error) {
        (Some(tree), _) => Some(Checkout {
            head: Some(tree.head.clone()),
            uncommitted: u32::try_from(tree.uncommitted_count).unwrap_or(u32::MAX),
            error: None,
        }),
        (None, Some(error)) => Some(Checkout {
            head: None,
            uncommitted: 0,
            error: Some(error.clone()),
        }),
        (None, None) => None,
    }
}

/// Why a release phase's workers are not all free again, or `Ok` when they are. It names each receipt and the
/// command that frees it, so the release phase fails where a person reads it. The leased-run driver finishes its
/// release phase with it, for `run`, `shard` and `flake`. The code is `lease_still_held` at [`Exit::TEMP_FAIL`], because the named release is the remedy.
pub(crate) fn release_error(program: &str, disposition: LeaseDisposition, receipts: &[String]) -> Result<(), Refusal> {
    if disposition == LeaseDisposition::Released {
        return Ok(());
    }
    let commands: Vec<String> = receipts.iter().map(|receipt| release_command(program, receipt)).collect();
    Err(Refusal::new(
        "lease_still_held",
        Exit::TEMP_FAIL,
        format!("the lease is still held ({disposition}); release it with {}", commands.join(", ")),
    ))
}
