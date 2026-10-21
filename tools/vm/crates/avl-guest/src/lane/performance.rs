//! The IJ Perf files of a `lane-run` run, which the nightly TeamCity build of the performance lane collects.
//!
//! Before the run, `lane-run` makes the [`Plan`] of `avl_junit::performance` from the lane documents and the
//! `build.txt` of the distribution in its runfiles. A plan that cannot be made is a note of the run, because a metric
//! never decides a verdict. After the run, green or not, it writes one `metrics.performance.json` for each case that
//! reported a metric and has a series, at `<directory>/<artifactPath>/`. The directory is
//! `$TEST_UNDECLARED_OUTPUTS_DIR/performance` under a Bazel test, else `<root>/performance` ([`directory`]).
//!
//! Beside each file go the result files of the authored parts of the case ([`PART_FILES`]), each only when a part wrote
//! it. The runner moves the artifacts of a part to `<iterationDir>/authored-artifacts/<suite>/<scenario>/<part>/`, as the
//! `suite`, `scenario` and `part` arguments of its instruction name them. A case is the scenario of the suite document
//! whose test class has the simple name of the case class, and whose profile has the name of the case. The parts are
//! copied in the order of the program. So when two parts wrote the same file, the later part wins, and a log line names
//! the file.
//!
//! On TeamCity, `lane-run` writes one `publishArtifacts` line on standard output for each written file, into the
//! artifact path of its series. Off TeamCity, it writes one log line with the directory. A write that fails is a log
//! line, never a verdict.

use std::fs;
use std::io::{self, Write};
use std::path::{Path, PathBuf};

use avl_junit::cases::{cases, simple_class_name};
use avl_junit::performance::{self, BUILD_TXT, Plan};
use avl_junit::teamcity;
use avl_wire::daemon::RunEvent;
use avl_wire::program::{SuiteProgram, read_lane_documents};
use serde::Serialize;
use serde_json::Value;

use super::channel::log;

#[cfg(test)]
mod tests;

/// The directory of the IJ Perf files, below the outputs directory of a Bazel test or below the run directory.
pub(crate) const DIR: &str = "performance";
/// The result files of an authored part that go beside the IJ Perf file of its case, in the order of their copy.
pub(crate) const PART_FILES: [&str; 5] = [
    "summary.txt",
    "air-performance.json",
    "watchdog.ndjson",
    "trace.ndjson",
    "report.json",
];
/// The start of the note of a plan that cannot be made, as the controller writes it.
const NO_FILE_NOTE: &str = "no IJ Perf file for this iteration";

/// One written file and the artifact path of its series, where TeamCity publishes it.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Published {
    /// The absolute path of the file.
    pub file: String,
    pub artifact_path: String,
}

/// The plan of `lane` from the lane documents at `lane_documents`, a directory or a jar, and the `build.txt` of the
/// distribution at `dist_home`. When it cannot be made, `notes` gets the reason and the answer is `None`.
pub(crate) fn plan_or_note(lane_documents: &Path, dist_home: &Path, lane: &str, notes: &mut Vec<String>) -> Option<Plan> {
    let build_txt = dist_home.join(BUILD_TXT);
    let made = read_lane_documents(lane_documents)
        .and_then(|documents| performance::plan(&documents, lane, performance::ide_build(&build_txt, fs::read_to_string(&build_txt))));
    match made {
        Ok(plan) => plan,
        Err(reason) => {
            notes.push(format!("{NO_FILE_NOTE}: {reason}"));
            None
        }
    }
}

/// The directory of the IJ Perf files: `<outputs>/performance` for the outputs directory of a Bazel test, else
/// `<root>/performance`.
pub(crate) fn directory(bazel_outputs: Option<&Path>, root: &Path) -> PathBuf {
    bazel_outputs.unwrap_or(root).join(DIR)
}

/// The artifact directories of the parts of one scenario, in the order of its program.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct ScenarioParts {
    /// The simple name of the test class of the suite.
    pub class_name: String,
    /// The name of the scenario, as a case names it.
    pub scenario: String,
    pub parts: Vec<PathBuf>,
}

/// The part directories of each authored scenario of `suites`, below `artifacts`, the authored artifacts of the
/// iteration. A part is an instruction with the string arguments `suite`, `scenario` and `part`.
pub(crate) fn scenario_parts<'a>(suites: impl IntoIterator<Item = &'a SuiteProgram>, artifacts: &Path) -> Vec<ScenarioParts> {
    let mut found = Vec::new();
    for suite in suites.into_iter().filter(|suite| suite.authored) {
        for profile in &suite.profiles {
            let mut parts: Vec<PathBuf> = Vec::new();
            for instruction in &profile.program {
                let arg = |name: &str| instruction.args.get(name).and_then(Value::as_str);
                if let (Some(suite), Some(scenario), Some(part)) = (arg("suite"), arg("scenario"), arg("part")) {
                    let directory = artifacts.join(suite).join(scenario).join(part);
                    if !parts.contains(&directory) {
                        parts.push(directory);
                    }
                }
            }
            found.push(ScenarioParts {
                class_name: simple_class_name(&suite.test_class_name).to_owned(),
                scenario: profile.name.clone(),
                parts,
            });
        }
    }
    found
}

/// Writes the IJ Perf files of the run that streamed `events` below `directory`, at `generated`, an RFC 1123 time, and
/// copies the part files of each case from `scenarios` beside its file. Answers the written files and one line for
/// each problem.
pub(crate) fn write(
    plan: &Plan,
    events: &[RunEvent],
    scenarios: &[ScenarioParts],
    directory: &Path,
    generated: &str,
) -> (Vec<Published>, Vec<String>) {
    let mut published = Vec::new();
    let mut problems = Vec::new();
    let mut publish = |path: &Path, artifact_path: &str| {
        published.push(Published {
            file: path.to_string_lossy().into_owned(),
            artifact_path: artifact_path.to_owned(),
        });
    };
    let metrics: Vec<_> = cases(events).into_iter().filter(|case| !case.report.metrics.is_empty()).collect();
    for file in performance::files(plan, events, generated) {
        let path = file.path_below(directory);
        if let Err(error) = write_replacing(&path, file.text.as_bytes()) {
            problems.push(format!("cannot write {}: {error}", path.display()));
            continue;
        }
        publish(&path, &file.artifact_path);
        let Some(case) = plan
            .series
            .iter()
            .find(|series| series.artifact_path == file.artifact_path)
            .and_then(|series| {
                metrics.iter().find(|case| {
                    case.name == series.scenario && simple_class_name(&case.class_name) == simple_class_name(&series.test_class_name)
                })
            })
        else {
            continue;
        };
        let class = simple_class_name(&case.class_name);
        let Some(scenario) = scenarios
            .iter()
            .find(|scenario| scenario.class_name == class && scenario.scenario == case.name)
        else {
            continue;
        };
        let target = path.parent().unwrap_or(directory);
        let mut copied: Vec<(&str, &Path)> = Vec::new();
        for part in &scenario.parts {
            for name in PART_FILES {
                let source = part.join(name);
                if !source.is_file() {
                    continue;
                }
                let destination = target.join(name);
                match copy_replacing(&source, &destination) {
                    Ok(()) => {
                        if let Some((_, earlier)) = copied.iter().find(|(copied, _)| *copied == name) {
                            problems.push(format!(
                                "{name} of the part {} replaces the one of the part {} in {}",
                                part.display(),
                                earlier.display(),
                                target.display()
                            ));
                        } else {
                            publish(&destination, &file.artifact_path);
                        }
                        copied.retain(|(copied, _)| *copied != name);
                        copied.push((name, part));
                    }
                    Err(error) => problems.push(format!("cannot copy {} to {}: {error}", source.display(), destination.display())),
                }
            }
        }
    }
    (published, problems)
}

/// Announces the written files of `directory`. On TeamCity, it writes one `publishArtifacts` line for each file on
/// `out`. Off TeamCity, it writes one log line.
pub(crate) fn announce(published: &[Published], directory: &Path, team_city: bool, out: &mut dyn Write) {
    if published.is_empty() {
        return;
    }
    if !team_city {
        log(&format!(
            "{} IJ Perf file(s) and part file(s) in {}",
            published.len(),
            directory.display()
        ));
        return;
    }
    let mut lines = String::new();
    for file in published {
        lines.push_str(&teamcity::publish_artifacts(&file.file, &file.artifact_path));
        lines.push('\n');
    }
    if let Err(error) = out.write_all(lines.as_bytes()).and_then(|()| out.flush()) {
        log(&format!("cannot write the publishArtifacts lines of the IJ Perf files: {error}"));
    }
}

/// Writes `bytes` to `path` through a temporary beside it and a rename, so a reader never sees half a file. Creates
/// the parent directories.
pub(crate) fn write_replacing(path: &Path, bytes: &[u8]) -> io::Result<()> {
    let parent = path.parent().unwrap_or(Path::new("."));
    fs::create_dir_all(parent)?;
    let name = path.file_name().map(|name| name.to_string_lossy().into_owned()).unwrap_or_default();
    let temporary = parent.join(format!(".{name}.{}.tmp", std::process::id()));
    let written = fs::write(&temporary, bytes).and_then(|()| fs::rename(&temporary, path));
    if written.is_err() {
        let _ = fs::remove_file(&temporary);
    }
    written
}

fn copy_replacing(source: &Path, destination: &Path) -> io::Result<()> {
    write_replacing(destination, &fs::read(source)?)
}
