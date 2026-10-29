//! The TeamCity artifact names of the trace bundles of a Bazel test. `air-trace teamcity-publish` publishes them after
//! the test, and the build log channel of `lane-run` publishes the files of a failed scenario while the test runs. Both
//! name the files with this module, so the second publish replaces the files of the first.
//!
//! The lane is the directory of the `test.xml` of the test: the Bazel target's name, such as `ui_lane_test`. A shard or
//! run directory below it keeps the target's name in front, such as `ui_lane_test/shard_1_of_2`. A bundle is
//! `<lane>/<runId>/<TestClass>/<stem>`, where `<runId>/<TestClass>` is its class directory and the stem is its
//! scenario. A later bundle of the same path in one publish takes the stem `<scenario>.<n>`.

use std::collections::BTreeSet;
use std::path::Path;

#[cfg(test)]
mod tests;

/// The artifact directory of the loose files of the failed scenarios, which the Tests tab of TeamCity shows inline.
pub const FAILED_DIR: &str = "air-traces-failed";
/// The name of the failure picture of a failed scenario in [FAILED_DIR], before its extension.
pub const FAILURE_STEM: &str = "failure";

/// The lane of the `test.xml` directory `dir`, relative to its testlogs or absolute: the Bazel target's name, and a
/// shard or run directory below it with the target's name in front.
pub fn testlogs_lane(dir: &Path) -> String {
    let segments: Vec<String> = dir
        .components()
        .map(|component| component.as_os_str().to_string_lossy().into_owned())
        .collect();
    match segments.as_slice() {
        [] => "tests".to_owned(),
        [.., target, last] if last.starts_with("shard_") || last.starts_with("run_") => format!("{target}/{last}"),
        [.., last] => last.clone(),
    }
}

/// The artifact directory of the loose files of the failed bundle `<lane>/<class_dir>/<stem>`.
pub fn failed_target(lane: &str, class_dir: &str, stem: &str) -> String {
    format!("{FAILED_DIR}/{lane}/{class_dir}/{stem}")
}

/// The file name of the failure picture whose bundle file is `still`: [FAILURE_STEM] with the extension of `still`, or
/// `webp` when it has none.
pub fn failure_picture(still: &str) -> String {
    let extension = still.rsplit_once('.').map_or("webp", |(_, extension)| extension);
    format!("{FAILURE_STEM}.{extension}")
}

/// The stems that the bundles of one publish took, by their paths.
///
/// `air-trace teamcity-publish` takes the stems in the order of the run, the class and the raw scenario name of the
/// bundles. `lane-run` takes them while the run goes on, in the order that the scenarios end. Both agree unless two raw
/// names sanitize to one path and run in another order than they sort. Then the two publishes swap the stems of those
/// bundles, and the artifacts of the build keep both copies.
#[derive(Debug, Default)]
pub struct Stems(BTreeSet<String>);

impl Stems {
    /// The stem of the next bundle `<lane>/<class_dir>/<scenario>`: the scenario, or `<scenario>.<n>` with the lowest
    /// free `n` from 2 when an earlier bundle took that path.
    pub fn take(&mut self, lane: &str, class_dir: &str, scenario: &str) -> String {
        let mut stem = scenario.to_owned();
        let mut attempt = 1;
        while !self.0.insert(format!("{lane}/{class_dir}/{stem}")) {
            attempt += 1;
            stem = format!("{scenario}.{attempt}");
        }
        stem
    }
}
