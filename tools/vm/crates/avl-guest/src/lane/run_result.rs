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
//!
//! A text holds at most [`TEXT_CAP`] bytes, cut at a character boundary.

use std::collections::BTreeMap;
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

use avl_junit::failed_cases;
use avl_wire::daemon::RunEvent;
use serde::Serialize;

use super::evidence;
use super::performance::{Published, SUMMARY_FILE, write_replacing};

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
