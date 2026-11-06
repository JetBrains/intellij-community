//! The run stream of `/run`: one NDJSON record per chunk, from `runStarted` to `summary`. The stream is the one account
//! of a run: the controller reads the run report, the JUnit XML and the TeamCity messages from it.
//!
//! A scenario is one case: `testStarted`, then `testFinished` with the status `SUCCESSFUL`, `FAILED` or `ABORTED` and
//! the typed failure. A scenario that the recycle budget never let run is `testSkipped` with its reason, and no
//! `testStarted`. A suite whose prologue failed is one `containerFailed`. The case of a scenario names the
//! `testClass` label of its suite as its class and the JUnit factory method as its method, as the JVM daemon named a
//! dynamic test. Its id is `[engine:lane-daemon]/[suite:<suite>]/[scenario:<name>]`. A rerun of a scenario is a second
//! case of the same id, and its pass takes the failure of the first attempt out of the counts.
//!
//! The stream is shared: the run thread writes the records of the scenarios, and the watchdog thread writes its
//! heartbeat and the slow-step records while the run thread waits for the IDE.
//!
//! The stream also keeps each record but `output`, so the iteration renders its JUnit XML from the same account, also
//! after the reader of the stream went away.

use std::collections::HashSet;
use std::io::Write;
use std::sync::{Mutex, MutexGuard, PoisonError};
use std::time::{Duration, Instant};

use avl_wire::daemon::{
    ContainerFailed, ExecutionIdentity, Output, RunEvent, RunEventKind, Skipped, TestFinished, TestStarted, TimedStep, Timing, TraceFailed,
    TypedFailure,
};
use avl_wire::report::ScenarioReport;

use super::channel::log;
use crate::clock::now_stamp;

#[cfg(test)]
mod tests;

/// The engine segment of the id of every case.
const ENGINE: &str = "[engine:lane-daemon]";

/// The status of a case, as JUnit names it on the run stream.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum CaseStatus {
    Successful,
    Failed,
    /// The scenario started and reached no verdict: the watchdog ended it, or the body aborted it.
    Aborted,
}

impl CaseStatus {
    pub(crate) const fn wire(self) -> &'static str {
        match self {
            Self::Successful => "SUCCESSFUL",
            Self::Failed => "FAILED",
            Self::Aborted => "ABORTED",
        }
    }
}

/// The identity of the scenario `name` of one suite.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct CaseIdentity {
    pub suite: String,
    /// The `testClass` label of the suite, which the case names as its class.
    pub class_name: String,
    pub method_name: Option<String>,
    pub name: String,
}

impl CaseIdentity {
    pub(crate) fn execution(&self) -> ExecutionIdentity {
        ExecutionIdentity {
            id: format!("{ENGINE}/[suite:{}]/[scenario:{}]", self.suite, self.name),
            display_name: self.name.clone(),
            class_name: Some(self.class_name.clone()),
            method_name: self.method_name.clone(),
        }
    }
}

/// The counts of the summary.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) struct Counts {
    /// The attempts that started: a rerun counts again.
    pub started: u32,
    /// The scenarios whose last attempt failed.
    pub failed: u32,
    pub skipped: u32,
    pub container_failures: u32,
}

struct Inner<'a> {
    out: &'a mut (dyn Write + Send),
    broken: bool,
    counts: Counts,
    /// The ids of the scenarios that [`Counts::failed`] counts.
    failed: HashSet<String>,
    /// Each record but `output`, in stream order: what the JUnit XML reads.
    kept: Vec<RunEvent>,
}

/// The writer of the run stream. A write that fails ends the stream, and the iteration goes on, so the state of the
/// daemon stays whole.
pub(crate) struct Stream<'a> {
    inner: Mutex<Inner<'a>>,
}

impl<'a> Stream<'a> {
    pub(crate) fn new(out: &'a mut (dyn Write + Send)) -> Self {
        Self {
            inner: Mutex::new(Inner {
                out,
                broken: false,
                counts: Counts::default(),
                failed: HashSet::new(),
                kept: Vec::new(),
            }),
        }
    }

    fn lock(&self) -> MutexGuard<'_, Inner<'a>> {
        self.inner.lock().unwrap_or_else(PoisonError::into_inner)
    }

    /// Writes one record. The run log of the daemon gets each `output` record and each failure too.
    pub(crate) fn emit(&self, record: &RunEventKind) {
        match record {
            RunEventKind::Output(output) => log(&output.text),
            RunEventKind::RunFailed(failed) => log(&format!("the iteration failed: {}", failed.error)),
            RunEventKind::IdeLaunched(launched) => log(&format!(
                "the IDE of '{}' runs as pid {}, launch {}, log {}",
                launched.launch_key, launched.pid, launched.launch_name, launched.log_dir
            )),
            RunEventKind::IdeExited(exited) => log(&format!("the IDE pid {} exited with {}", exited.pid, exited.exit_code)),
            RunEventKind::TestFinished(finished) if finished.status != CaseStatus::Successful.wire() => log(&format!(
                "{} {}: {}",
                finished.execution.describe(),
                finished.status,
                finished.failure.message.as_deref().unwrap_or_default()
            )),
            RunEventKind::WatchdogExpired(expired) => log(&format!(
                "the watchdog expired: {}{}",
                expired.reason,
                expired.detail.as_deref().map(|detail| format!(": {detail}")).unwrap_or_default()
            )),
            _ => {}
        }
        let mut inner = self.lock();
        if !matches!(record, RunEventKind::Output(_)) {
            inner.kept.push(RunEvent {
                raw: None,
                kind: record.clone(),
            });
        }
        if inner.broken {
            return;
        }
        let Ok(mut line) = serde_json::to_vec(record) else {
            return;
        };
        line.push(b'\n');
        if inner.out.write_all(&line).is_err() {
            inner.broken = true;
        }
    }

    /// Writes one `output` record of `text`.
    pub(crate) fn note(&self, text: impl Into<String>) {
        self.emit(&RunEventKind::Output(Output { text: text.into() }));
    }

    /// The `traceFailed` record: the recorder records nothing from here on, or missed a scenario, for `reason`.
    pub(crate) fn trace_failed(&self, reason: String) {
        self.emit(&RunEventKind::TraceFailed(TraceFailed {
            timestamp: now_stamp(),
            reason,
        }));
    }

    /// The `timing` record of `step`, which began at `started` and ends now. `subject` is its suite or its scenario.
    pub(crate) fn timing(&self, step: TimedStep, started: Instant, subject: Option<&str>) {
        self.emit(&timing(step, started, subject));
    }

    /// The `testStarted` record of a scenario.
    pub(crate) fn started(&self, case: &CaseIdentity) {
        self.lock().counts.started += 1;
        self.emit(&RunEventKind::TestStarted(TestStarted {
            timestamp: now_stamp(),
            execution: case.execution(),
        }));
    }

    /// The `testFinished` record of a scenario that started, and its case. A pass of a scenario that failed before in
    /// the stream takes that failure out of the counts, and a second failure of a scenario counts once.
    pub(crate) fn finished(
        &self,
        case: &CaseIdentity,
        status: CaseStatus,
        duration: Duration,
        failure: Option<TypedFailure>,
        report: ScenarioReport,
    ) {
        let execution = case.execution();
        {
            let mut inner = self.lock();
            match status {
                CaseStatus::Successful => {
                    if inner.failed.remove(&execution.id) {
                        inner.counts.failed -= 1;
                    }
                }
                CaseStatus::Failed => {
                    if inner.failed.insert(execution.id.clone()) {
                        inner.counts.failed += 1;
                    }
                }
                CaseStatus::Aborted => inner.counts.skipped += 1,
            }
        }
        self.emit(&RunEventKind::TestFinished(TestFinished {
            timestamp: now_stamp(),
            execution,
            status: status.wire().to_owned(),
            duration_ms: i64::try_from(duration.as_millis()).unwrap_or(i64::MAX),
            failure: failure.unwrap_or_default(),
            report,
        }));
    }

    /// The `testSkipped` record of a scenario that never started, and its case.
    pub(crate) fn skipped(&self, case: &CaseIdentity, reason: String) {
        {
            let mut inner = self.lock();
            inner.counts.skipped += 1;
        }
        self.emit(&RunEventKind::TestSkipped(Skipped {
            timestamp: now_stamp(),
            execution: case.execution(),
            reason,
        }));
    }

    /// The `containerFailed` record of a suite whose prologue failed, and its case, named after the suite.
    pub(crate) fn container_failed(&self, suite: &str, suite_class: &str, class_name: &str, failure: TypedFailure) {
        {
            let mut inner = self.lock();
            inner.counts.container_failures += 1;
        }
        self.emit(&RunEventKind::ContainerFailed(ContainerFailed {
            timestamp: now_stamp(),
            execution: ExecutionIdentity {
                id: format!("{ENGINE}/[suite:{suite}]"),
                display_name: suite_class.to_owned(),
                class_name: Some(class_name.to_owned()),
                method_name: None,
            },
            failure,
        }));
    }

    /// The counts so far.
    pub(crate) fn counts(&self) -> Counts {
        self.lock().counts
    }

    /// The JUnit XML of the records so far.
    pub(crate) fn junit_xml(&self) -> String {
        avl_junit::render([self.lock().kept.as_slice()])
    }
}

/// The `timing` record of `step`, which began at `started` and ends now.
pub(crate) fn timing(step: TimedStep, started: Instant, subject: Option<&str>) -> RunEventKind {
    RunEventKind::Timing(Timing {
        timestamp: now_stamp(),
        step: step.as_str().to_owned(),
        duration_ms: i64::try_from(started.elapsed().as_millis()).unwrap_or(i64::MAX),
        subject: subject.map(str::to_owned),
    })
}
