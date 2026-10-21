//! The run stream of one iteration as test cases: one case for each scenario, each failed container and each skipped
//! container, in the order of the stream.
//!
//! The JUnit XML and the run report of the controller read the stream through this one reading, so the two never
//! disagree about what a scenario did. The TeamCity messages follow the stream record by record and use
//! [`failure_of_test`] and [`failure_of_container`] for the text of a failure.

use std::collections::HashSet;

use avl_wire::daemon::{ContainerFailed, ExecutionIdentity, RunEvent, RunEventKind, TestFinished};
use avl_wire::report::ScenarioReport;

#[cfg(test)]
mod tests;

/// The `testFinished` status of a passed test, as both daemons spell it.
pub const STATUS_SUCCESSFUL: &str = "SUCCESSFUL";
/// The `testFinished` status of a failed test.
pub const STATUS_FAILED: &str = "FAILED";
/// The `testFinished` status of a test that the run gave up on, such as an assumption that did not hold. It is a skip.
pub const STATUS_ABORTED: &str = "ABORTED";

/// The message of a case that started and never finished: the stream ended while it ran.
pub const UNFINISHED_MESSAGE: &str = "the scenario never finished: the run stream ended while it ran";

/// One case of the stream.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct StreamCase {
    /// The `className` of the record, or its display name when the record names no class.
    pub class_name: String,
    /// The display name of the record: the scenario, or the suite of a prologue failure.
    pub name: String,
    /// The `timestamp` of the first record of the case.
    pub started_at: String,
    /// Zero for a case that has no `testFinished`.
    pub duration_ms: i64,
    pub outcome: CaseOutcome,
    /// The notes and the measurements of the scenario.
    pub report: ScenarioReport,
}

/// How a case ended.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum CaseOutcome {
    Passed,
    Failed(CaseFailure),
    /// The reason of the skip.
    Skipped(String),
    /// The case started, and the stream ended before its `testFinished`. A killed run leaves this shape.
    Unfinished,
}

/// What a test failure or a container failure says.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct CaseFailure {
    pub kind: FailureOrigin,
    /// The kind of the failure, such as the class name of an exception in the IDE. The JVM daemon sends none.
    pub r#type: Option<String>,
    /// The first account of the failure, which can span lines.
    pub message: String,
    /// The cause chain or the stack. Empty when the record holds nothing after the message.
    pub detail: String,
}

/// Which record failed.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum FailureOrigin {
    Test,
    /// A container: a prologue failure of a suite document, or a JUnit class that failed outside its tests.
    Container,
}

impl CaseFailure {
    /// The text of the failure as one block: the message, then the detail on the lines after it.
    pub fn text(&self) -> String {
        if self.detail.is_empty() {
            return self.message.clone();
        }
        if self.message.is_empty() || self.detail.starts_with(&self.message) {
            return self.detail.clone();
        }
        format!("{}\n{}", self.message, self.detail)
    }

    /// The first line of the message: the line a build server shows beside the name of the test.
    pub fn headline(&self) -> &str {
        first_line(&self.message)
    }
}

/// The failure of a failed `testFinished`.
pub fn failure_of_test(finished: &TestFinished) -> CaseFailure {
    failure(FailureOrigin::Test, &finished.execution, &finished.failure)
}

/// The failure of a `containerFailed`.
pub fn failure_of_container(failed: &ContainerFailed) -> CaseFailure {
    failure(FailureOrigin::Container, &failed.execution, &failed.failure)
}

/// The typed failure of a record. A record with no message gets a message that names the case.
fn failure(kind: FailureOrigin, execution: &ExecutionIdentity, typed: &avl_wire::daemon::TypedFailure) -> CaseFailure {
    CaseFailure {
        kind,
        r#type: typed.kind.clone().filter(|kind| !kind.is_empty()),
        message: typed
            .message
            .clone()
            .filter(|message| !message.is_empty())
            .unwrap_or_else(|| format!("{} failed", execution.display_name)),
        detail: typed.detail.clone().unwrap_or_default(),
    }
}

/// The name of `class_name` without its package. The plan of the lane daemon names a class by the `testClassName` of
/// its document, and the records name it by its FQN, so a class is matched by this name.
pub fn simple_class_name(class_name: &str) -> &str {
    class_name.rsplit('.').next().unwrap_or(class_name)
}

/// The first line of `text`, or the whole of it when it has no line end.
fn first_line(text: &str) -> &str {
    text.lines().next().unwrap_or_default()
}

/// The class of a record: its `className`, or its display name when it names none.
pub fn class_of(execution: &ExecutionIdentity) -> &str {
    execution.class_name.as_deref().unwrap_or(&execution.display_name)
}

/// Every case of the stream, in the order of its first record.
///
/// A `testStarted` opens a case, and its `testFinished` or `testSkipped` closes it. A `testSkipped` with no start, a
/// `containerFailed` and a `containerSkipped` are a case of their own. A case that is still open at the end of the
/// stream is [`CaseOutcome::Unfinished`]. A start of an id that is still open keeps the position of the first start.
pub fn cases(events: &[RunEvent]) -> Vec<StreamCase> {
    let mut cases: Vec<StreamCase> = Vec::new();
    // The index of the open case of each id.
    let mut open: Vec<(String, usize)> = Vec::new();
    for event in events {
        match &event.kind {
            RunEventKind::TestStarted(started) => {
                if open.iter().any(|(known, _)| *known == started.execution.id) {
                    continue;
                }
                open.push((started.execution.id.clone(), cases.len()));
                cases.push(case(&started.execution, &started.timestamp, CaseOutcome::Unfinished));
            }
            RunEventKind::TestFinished(finished) => {
                let outcome = match finished.status.as_str() {
                    STATUS_FAILED => CaseOutcome::Failed(failure_of_test(finished)),
                    STATUS_ABORTED => CaseOutcome::Skipped(
                        finished
                            .failure
                            .message
                            .clone()
                            .unwrap_or_else(|| "the run gave up on the test".to_owned()),
                    ),
                    _ => CaseOutcome::Passed,
                };
                let index = close(&mut open, &finished.execution.id).unwrap_or_else(|| {
                    cases.push(case(&finished.execution, &finished.timestamp, CaseOutcome::Unfinished));
                    cases.len() - 1
                });
                let closed = &mut cases[index];
                closed.outcome = outcome;
                closed.duration_ms = finished.duration_ms.max(0);
                closed.report = finished.report.clone();
            }
            RunEventKind::TestSkipped(skipped) => {
                let outcome = CaseOutcome::Skipped(skipped.reason.clone());
                match close(&mut open, &skipped.execution.id) {
                    Some(index) => cases[index].outcome = outcome,
                    None => cases.push(case(&skipped.execution, &skipped.timestamp, outcome)),
                }
            }
            RunEventKind::ContainerSkipped(skipped) => {
                cases.push(case(
                    &skipped.execution,
                    &skipped.timestamp,
                    CaseOutcome::Skipped(skipped.reason.clone()),
                ));
            }
            RunEventKind::ContainerFailed(failed) => {
                cases.push(case(
                    &failed.execution,
                    &failed.timestamp,
                    CaseOutcome::Failed(failure_of_container(failed)),
                ));
            }
            _ => {}
        }
    }
    cases
}

/// Removes the open case of `id`, and answers its index.
fn close(open: &mut Vec<(String, usize)>, id: &str) -> Option<usize> {
    let position = open.iter().position(|(known, _)| known == id)?;
    Some(open.remove(position).1)
}

fn case(execution: &ExecutionIdentity, timestamp: &str, outcome: CaseOutcome) -> StreamCase {
    StreamCase {
        class_name: class_of(execution).to_owned(),
        name: execution.display_name.clone(),
        started_at: timestamp.to_owned(),
        duration_ms: 0,
        outcome,
        report: ScenarioReport::default(),
    }
}

/// Every class that the last `planStarted` named and that no later record reached, in the order of the plan. A class
/// is matched by its [`simple_class_name`], so a plan of simple names and records of FQNs agree.
///
/// The one account of a suite that never ran: a watchdog that kills the IDE ends the stream, and the suites queued
/// behind the failure write no record of their own. A class that started and did not finish is reached, and its
/// unfinished case names it.
pub fn unreported_classes(events: &[RunEvent]) -> Vec<String> {
    let Some(plan) = events.iter().rev().find_map(|event| match &event.kind {
        RunEventKind::PlanStarted(plan) => Some(plan),
        _ => None,
    }) else {
        return Vec::new();
    };
    let reached: HashSet<&str> = events
        .iter()
        .filter_map(|event| match &event.kind {
            RunEventKind::TestStarted(started) => started.execution.class_name.as_deref(),
            RunEventKind::TestFinished(finished) => finished.execution.class_name.as_deref(),
            RunEventKind::TestSkipped(skipped) | RunEventKind::ContainerSkipped(skipped) => skipped.execution.class_name.as_deref(),
            RunEventKind::ContainerFailed(failed) => failed.execution.class_name.as_deref(),
            _ => None,
        })
        .map(simple_class_name)
        .collect();
    plan.class_names
        .iter()
        .filter(|class_name| !reached.contains(simple_class_name(class_name)))
        .cloned()
        .collect()
}
