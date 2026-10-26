//! The result of a `lane-run` run, `run-result.json`: what a TeamCity step sends as the failure event of the build.
//!
//! `lane-run` writes it after every run that is not `--plan-only`, green or not. It goes to
//! `$TEST_UNDECLARED_OUTPUTS_DIR/lane/run-result.json` under a Bazel test, else to `<root>/run-result.json` ([`path`]).
//! The TeamCity step reads `testlogs/**/test.outputs/lane/run-result.json`, adds the fields of the build and posts it.
//! So the field names are a contract with that step:
//!
//! - `lane` and `green`.
//! - `failedTests`: each case that fails the JUnit XML, as `test`, `<className> <name>`, and `failure`, the text of
//!   the failure that the XML writes.
//! - `summaries`: the text of each `summary.txt` of the authored artifacts of the iteration, by `<scenario>/<part>`.
//! - `performance`: each IJ Perf file and part file of the run, as `file` and `artifactPath`.
//! - `refusal`: only for a run that `lane-run` refused before its end wrote a result, such as a host probe that found
//!   no window manager. It holds the `code` and the `message` of the refusal, so the event says why no test ran.
//!
//! A text holds at most [`TEXT_CAP`] bytes, cut at a character boundary.
//!
//! [`RunEnd`] writes the IJ Perf files of the run and then its result. The end of the run calls it, and so does the hard
//! stop of the monitor, which ends a run whose thread did not return. So a run that its budget or a hang ended still
//! names its failed and unfinished tests, and it keeps the metrics measured before the end.

use std::collections::BTreeMap;
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use avl_junit::failed_cases;
use avl_junit::performance::Plan;
use avl_wire::daemon::{RunEvent, RunEventKind};
use avl_wire::program::SuiteProgram;
use serde::Serialize;

use super::evidence;
use super::performance::{self, Published, SUMMARY_FILE, write_replacing};
use super::program::ITERATION_ARTIFACTS_DIR;

#[cfg(test)]
mod tests;

/// The file of the run result.
pub(crate) const FILE: &str = "run-result.json";
/// The most bytes of a failure or a summary.
pub(crate) const TEXT_CAP: usize = 4000;

/// The result of one run.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct RunResult {
    pub lane: String,
    pub green: bool,
    pub failed_tests: Vec<FailedTest>,
    pub summaries: BTreeMap<String, String>,
    pub performance: Vec<Published>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub refusal: Option<RunRefusal>,
}

/// The refusal that ended a run before its end wrote a result.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
pub(crate) struct RunRefusal {
    pub code: String,
    /// The message of the refusal, capped.
    pub message: String,
}

/// One case that fails the JUnit XML.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
pub(crate) struct FailedTest {
    /// `<className> <name>`.
    pub test: String,
    /// The message and the detail of the failure, trimmed and capped.
    pub failure: String,
}

/// The result of the run of `lane` that streamed `events`. `artifacts` is the directory of the authored artifacts of
/// the iteration, and `performance` lists the IJ Perf files and the part files that the run wrote.
pub(crate) fn run_result(lane: &str, green: bool, events: &[RunEvent], artifacts: Option<&Path>, performance: Vec<Published>) -> RunResult {
    RunResult {
        lane: lane.to_owned(),
        green,
        failed_tests: failed_cases(events)
            .into_iter()
            .map(|case| FailedTest {
                test: format!("{} {}", case.class_name, case.name),
                failure: capped(case.failure.text().trim()),
            })
            .collect(),
        summaries: artifacts.map(summaries).unwrap_or_default(),
        performance,
        refusal: None,
    }
}

/// The result of a run of `lane` that `lane-run` refused with `code` and `message` before the end of the run.
pub(crate) fn refused(lane: &str, code: &str, message: &str) -> RunResult {
    RunResult {
        lane: lane.to_owned(),
        green: false,
        failed_tests: Vec::new(),
        summaries: BTreeMap::new(),
        performance: Vec::new(),
        refusal: Some(RunRefusal {
            code: code.to_owned(),
            message: capped(message.trim()),
        }),
    }
}

/// The path of the run result: `<outputs>/lane/run-result.json` for the outputs directory of a Bazel test, else
/// `<root>/run-result.json`.
pub(crate) fn path(bazel_outputs: Option<&Path>, root: &Path) -> PathBuf {
    bazel_outputs.map_or_else(|| root.join(FILE), |outputs| outputs.join(evidence::DIR).join(FILE))
}

/// Writes `result` to `path` as pretty JSON with a final line end.
pub(crate) fn write(path: &Path, result: &RunResult) -> io::Result<()> {
    let mut text = serde_json::to_string_pretty(result)?;
    text.push('\n');
    write_replacing(path, text.as_bytes())
}

/// What the end of a run writes beside its JUnit XML: the IJ Perf files of its plan and the result of the run.
#[derive(Clone, Debug)]
pub(crate) struct RunEnd {
    pub lane: String,
    /// The plan of the IJ Perf files, or `None` for a run that writes none.
    pub plan: Option<Plan>,
    /// The suite documents of the selection, which map a case to the artifact directories of its parts.
    pub suites: Vec<SuiteProgram>,
    /// The directory of the IJ Perf files, [`performance::directory`].
    pub performance_dir: PathBuf,
    /// The file of the run result, [`path`].
    pub result_file: PathBuf,
    /// Whether the run is a TeamCity build, so the IJ Perf files get a `publishArtifacts` line each.
    pub team_city: bool,
}

/// What [`RunEnd::write`] did: the result, the lines for standard output, and the lines for the log.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct Ended {
    pub result: RunResult,
    /// The `publishArtifacts` line of each written file, on a TeamCity build.
    pub messages: Vec<String>,
    /// What was written where, and each problem.
    pub lines: Vec<String>,
}

impl RunEnd {
    /// Writes the IJ Perf files and the result of the run that streamed `events`, at `generated`, an RFC 1123 time.
    /// `iteration_dir` names the directory of an iteration by its id. It writes nothing on standard output or the log,
    /// so the hard stop decides how long it waits for them.
    pub(crate) fn write(&self, events: &[RunEvent], green: bool, iteration_dir: &dyn Fn(&str) -> PathBuf, generated: &str) -> Ended {
        let artifacts = iteration_id(events).map(|id| iteration_dir(&id).join(ITERATION_ARTIFACTS_DIR));
        let mut lines = Vec::new();
        let mut messages = Vec::new();
        let published = if let Some(plan) = &self.plan {
            let scenarios = artifacts
                .as_deref()
                .map(|artifacts| performance::scenario_parts(&self.suites, artifacts))
                .unwrap_or_default();
            let (written, problems) = performance::write(plan, events, &scenarios, &self.performance_dir, generated);
            lines.extend(problems);
            if !written.is_empty() {
                if self.team_city {
                    messages = performance::publish_lines(&written);
                } else {
                    lines.push(format!(
                        "{} IJ Perf file(s) and part file(s) in {}",
                        written.len(),
                        self.performance_dir.display()
                    ));
                }
            }
            written
        } else {
            Vec::new()
        };
        let result = run_result(&self.lane, green, events, artifacts.as_deref(), published);
        lines.push(match write(&self.result_file, &result) {
            Ok(()) => format!("the result of the run: {}", self.result_file.display()),
            Err(error) => format!("cannot write the result of the run {}: {error}", self.result_file.display()),
        });
        Ended { result, messages, lines }
    }
}

/// The id of the iteration of `events`, from its `runStarted`.
pub(crate) fn iteration_id(events: &[RunEvent]) -> Option<String> {
    events.iter().find_map(|event| match &event.kind {
        RunEventKind::RunStarted(started) => Some(started.iteration_id.clone()),
        _ => None,
    })
}

/// The text of each `<suite>/<scenario>/<part>/summary.txt` below `artifacts`, by `<scenario>/<part>`, capped.
fn summaries(artifacts: &Path) -> BTreeMap<String, String> {
    let mut found = BTreeMap::new();
    for suite in directories(artifacts) {
        for scenario in directories(&suite) {
            for part in directories(&scenario) {
                let (Some(scenario_name), Some(part_name)) = (scenario.file_name(), part.file_name()) else {
                    continue;
                };
                if let Ok(text) = fs::read_to_string(part.join(SUMMARY_FILE)) {
                    found.insert(
                        format!("{}/{}", scenario_name.to_string_lossy(), part_name.to_string_lossy()),
                        capped(&text),
                    );
                }
            }
        }
    }
    found
}

/// The directories in `directory`, sorted by name.
fn directories(directory: &Path) -> Vec<PathBuf> {
    let mut found: Vec<PathBuf> = fs::read_dir(directory)
        .into_iter()
        .flatten()
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .filter(|path| path.is_dir())
        .collect();
    found.sort();
    found
}

/// `text` cut to at most [`TEXT_CAP`] bytes, at a character boundary.
fn capped(text: &str) -> String {
    let mut end = text.len().min(TEXT_CAP);
    while !text.is_char_boundary(end) {
        end -= 1;
    }
    text[..end].to_owned()
}
