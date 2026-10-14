//! One iteration of the lane daemon: the run stream of `/run` from `runStarted` to `summary`.
//!
//! The iteration reads the lane document and the suite documents from the jars that the push stored, selects the
//! documents of the lane, makes the IDE of the lane ready, and then names the plan. The program runner of the suite
//! documents is the next slice, so each selected document ends as a container failure of the type `not_implemented`,
//! which names the suite. The IDE part is whole, so a run proves the decision, the launch and the readiness of the IDE.

use std::collections::BTreeMap;
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::time::Duration;

use avl_wire::daemon::{
    ContainerFailed, ExecutionIdentity, Output, PlanStarted, RunEventKind, RunFailed, RunRequest, RunStarted, Summary, TypedFailure,
    WatchdogState,
};
use avl_wire::program::lane::{LaneDocument, parse_lane_document};
use avl_wire::program::{SuiteProgram, parse_suite_program, read_lane_documents, read_suite_documents};

use super::channel::Daemon;
use super::context::option_files_digest;
use super::ide::{ContextPlugin, LaneLaunch, Records};
use super::identity::{KeyInputs, LaneIdentity};
use super::package::sha256_hex;
use super::readiness::READY_TIMEOUT;
use crate::clock::now_stamp;

#[cfg(test)]
mod tests;

/// The type of the failure of a selected document until the program runner lands.
pub(crate) const NOT_IMPLEMENTED: &str = "not_implemented";

/// The budget that the watchdog record names for the IDE part: a fresh launch, then readiness.
const IDE_BUDGET: Duration = Duration::from_mins(5);

/// The writer of the run stream: one record per line, each line one chunk. A write that fails ends the stream, and the
/// iteration goes on, so the state of the daemon stays whole.
pub(crate) struct Stream<'a> {
    out: &'a mut dyn Write,
    broken: bool,
}

impl<'a> Stream<'a> {
    pub(crate) fn new(out: &'a mut dyn Write) -> Self {
        Self { out, broken: false }
    }

    /// Writes one record. The run log of the daemon gets an `output` record too.
    pub(crate) fn emit(&mut self, record: &RunEventKind) {
        if let RunEventKind::Output(output) = record {
            eprintln!("{}", output.text);
        }
        if self.broken {
            return;
        }
        let Ok(mut line) = serde_json::to_vec(record) else {
            return;
        };
        line.push(b'\n');
        if self.out.write_all(&line).is_err() {
            self.broken = true;
        }
    }

    /// Writes one `output` record of `text`.
    pub(crate) fn note(&mut self, text: impl Into<String>) {
        self.emit(&RunEventKind::Output(Output { text: text.into() }));
    }
}

/// The suite documents that an iteration runs, and the lane document of their lane.
#[derive(Debug)]
pub(crate) struct Selection {
    pub lane_bytes: Vec<u8>,
    pub lane: LaneDocument,
    pub suites: Vec<SuiteProgram>,
}

/// What a selection is read from: the lane, the jars of the documents, and the selectors.
pub(crate) struct SelectionInputs<'a> {
    pub lane: &'a str,
    pub lane_documents: &'a Path,
    pub suite_documents: &'a Path,
    pub selectors: &'a [String],
}

/// Reads the lane document `<lane>.json` and the suite documents of its lane. A selector names the `testClassName` of a
/// document, with or without a package and a `#scenario` part. No selector selects every document of the lane.
///
/// The JUnit5 filters of a request are not read here. The program runner applies them.
pub(crate) fn select(inputs: &SelectionInputs<'_>) -> Result<Selection, String> {
    let lanes = read_lane_documents(inputs.lane_documents)?;
    let name = format!("{}.json", inputs.lane);
    let lane_bytes = lanes.get(&name).cloned().ok_or_else(|| {
        format!(
            "the lane documents hold no {name}; they hold {:?}",
            lanes.keys().collect::<Vec<_>>()
        )
    })?;
    let lane = parse_lane_document(&lane_bytes).map_err(|problem| format!("the lane document {name} {problem}"))?;
    let mut suites = Vec::new();
    for (file, content) in read_suite_documents(inputs.suite_documents)? {
        let suite = parse_suite_program(&content).map_err(|problem| format!("the suite document {file} {problem}"))?;
        if suite.lane == lane.lane {
            suites.push(suite);
        }
    }
    let wanted: Vec<&str> = inputs
        .selectors
        .iter()
        .map(|selector| {
            let class = selector.split('#').next().unwrap_or_default();
            class.rsplit('.').next().unwrap_or(class)
        })
        .collect();
    if !wanted.is_empty() {
        suites.retain(|suite| wanted.contains(&suite.test_class_name.as_str()));
        if suites.is_empty() {
            return Err(format!("no suite document of the lane {} has the test class {wanted:?}", lane.lane));
        }
    }
    suites.sort_by(|left, right| left.test_class_name.cmp(&right.test_class_name));
    Ok(Selection { lane_bytes, lane, suites })
}

/// The counts that the summary reports.
#[derive(Debug, Default)]
struct Counts {
    container_failures: u32,
    failed: bool,
}

/// Runs one iteration of `request` and writes its records to `out`. A write that fails ends the stream, not the run.
pub(crate) fn run(daemon: &Daemon, request: &RunRequest, out: &mut dyn Write) {
    let mut stream = Stream::new(out);
    let iteration_id = daemon.next_iteration_id();
    let started_at = now_stamp();
    let iteration_dir = daemon.iteration_dir(&iteration_id);
    if let Err(error) = fs::create_dir_all(&iteration_dir) {
        stream.note(format!("cannot create {}: {error}", iteration_dir.display()));
    }
    stream.emit(&RunEventKind::RunStarted(RunStarted {
        iteration_id: iteration_id.clone(),
        daemon_boot_stamp: daemon.boot_stamp().to_owned(),
        started_at: started_at.clone(),
    }));
    let counts = match execute(daemon, request, &mut stream) {
        Ok(counts) => counts,
        Err(error) => {
            stream.emit(&RunEventKind::RunFailed(RunFailed { error }));
            Counts {
                container_failures: 0,
                failed: true,
            }
        }
    };
    stream.emit(&watchdog("completed", request, None));
    stream.emit(&RunEventKind::Summary(Summary {
        iteration_id,
        daemon_boot_stamp: daemon.boot_stamp().to_owned(),
        started_at,
        tests_started: 0,
        tests_failed: 0,
        tests_skipped: 0,
        containers_skipped: 0,
        container_failures: counts.container_failures,
        has_failures: counts.failed || counts.container_failures > 0,
        ide_running: daemon.ide_running(),
    }));
}

/// The part of an iteration that can fail as a whole: the documents, the IDE and the plan.
fn execute(daemon: &Daemon, request: &RunRequest, stream: &mut Stream<'_>) -> Result<Counts, String> {
    let (Some(lane), Some(lane_inputs)) = (&request.lane, &request.lane_inputs) else {
        return Err("the run request names no lane and no lane inputs, which the lane daemon needs".to_owned());
    };
    let lane_documents = daemon.stored(&lane_inputs.lane_documents)?;
    let suite_documents = daemon.stored(&lane_inputs.suite_documents)?;
    let fake_agent_jar = daemon.stored(&lane_inputs.fake_agent_jar)?;
    let selection = select(&SelectionInputs {
        lane,
        lane_documents: &lane_documents,
        suite_documents: &suite_documents,
        selectors: &request.selectors,
    })?;
    let plugins: Vec<ContextPlugin> = request
        .ide_plugins
        .iter()
        .map(|plugin| {
            Ok(ContextPlugin {
                destination: plugin.destination.clone(),
                sha256: plugin.sha256.clone(),
                source: daemon.stored(&plugin.sha256)?,
            })
        })
        .collect::<Result<_, String>>()?;
    stream.emit(&watchdog("ide", request, Some(IDE_BUDGET + READY_TIMEOUT)));
    let decision = daemon.with_ide(|owner| {
        let runtimes: BTreeMap<String, String> = owner
            .runtimes_named(&selection.lane.cli_runtimes)?
            .into_iter()
            .map(|runtime| (runtime.name, runtime.identity))
            .collect();
        let key = KeyInputs {
            lane: &selection.lane.lane,
            lane_document: &sha256_hex(&selection.lane_bytes),
            product_stamp: Some(&request.product_stamp),
            runtimes: &runtimes,
            option_files: &option_files_digest(),
        }
        .config_key();
        let identity = LaneIdentity::new(key, daemon.port());
        let launch = LaneLaunch {
            document: &selection.lane,
            identity: &identity,
            product_stamp: &request.product_stamp,
            fake_agent_jar: &fake_agent_jar,
            plugins: &plugins,
        };
        owner.ensure(&launch, &mut Records(&mut |record| stream.emit(&record)))
    });
    let decision = decision.map_err(|refusal| format!("{}: {}", refusal.code, refusal.message))?;
    stream.note(format!("lane {lane}: ide {}", decision.action.as_str()));
    let discovered: usize = selection.suites.iter().map(|suite| suite.profiles.len()).sum();
    stream.emit(&RunEventKind::PlanStarted(PlanStarted {
        timestamp: now_stamp(),
        discovered_executions: u32::try_from(discovered).unwrap_or(u32::MAX),
        class_names: selection.suites.iter().map(|suite| suite.test_class_name.clone()).collect(),
    }));
    for suite in &selection.suites {
        stream.emit(&not_implemented(suite));
    }
    Ok(Counts {
        container_failures: u32::try_from(selection.suites.len()).unwrap_or(u32::MAX),
        failed: false,
    })
}

/// The container failure of a selected document until the program runner lands.
pub(crate) fn not_implemented(suite: &SuiteProgram) -> RunEventKind {
    RunEventKind::ContainerFailed(ContainerFailed {
        timestamp: now_stamp(),
        execution: ExecutionIdentity {
            id: format!("[engine:lane-daemon]/[suite:{}]", suite.suite),
            display_name: suite.test_class_name.clone(),
            class_name: Some(suite.test_class_name.clone()),
            method_name: None,
        },
        error: None,
        failure: TypedFailure {
            kind: Some(NOT_IMPLEMENTED.to_owned()),
            message: Some(format!(
                "the lane daemon runs no suite document yet, so the suite '{}' did not run",
                suite.suite
            )),
            detail: None,
        },
    })
}

/// The watchdog record of the phase `phase`, which tells the controller how long the next step may take.
fn watchdog(phase: &str, request: &RunRequest, next: Option<Duration>) -> RunEventKind {
    let millis = |seconds: u64| i64::try_from(seconds.saturating_mul(1000)).unwrap_or(i64::MAX);
    RunEventKind::WatchdogState(WatchdogState {
        timestamp: now_stamp(),
        phase: phase.to_owned(),
        active_execution_timeout_ms: millis(request.active_execution_timeout_sec),
        progress_gap_timeout_ms: millis(request.progress_gap_timeout_sec),
        phase_deadline: None,
        emergency_deadline: None,
        next_deadline_in_ms: next.map(|next| i64::try_from(next.as_millis()).unwrap_or(i64::MAX)),
        active_execution: None,
    })
}

/// Where the push stored the file of `sha256`.
pub(crate) fn stored_path(jars: &Path, sha256: &str) -> PathBuf {
    jars.join(sha256)
}
