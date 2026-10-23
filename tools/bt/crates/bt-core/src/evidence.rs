//! The evidence of a failed run.
//!
//! Bazel writes the logs of a test target to out/bazel-testlogs, and the next run of that target overwrites them. A
//! rerun of a failed target, which is the usual next step, therefore deletes the only record of the failure: its
//! test.log, its test.xml, and the test.outputs directory with the IDE log and the thread dumps of a timeout. So each
//! run copies the logs of its failed attempts to a run directory of its own under [`RUNS_DIR`], and the digest names
//! that directory. The last [`KEPT_RUNS`] run directories stay.

use std::path::{Path, PathBuf};

use crate::bep::{BepAttempt, BepSummary};
use crate::runtime::{Runtime, repo_file};

/// The repo-relative directory that holds one directory per run that kept evidence.
pub const RUNS_DIR: &str = "out/bt-runs";

/// How many run directories stay. A run directory holds the IDE logs of each failed shard, so a lane run with a few
/// failures takes tens of MB; ten runs cover a fix loop of reruns.
pub const KEPT_RUNS: usize = 10;

/// The directory of the undeclared outputs of a test, beside its test.log.
const OUTPUTS_DIR: &str = "test.outputs";

/// Copies the test.log, the test.xml and the test.outputs directory of each failed attempt of `bep` to a new run
/// directory, then removes the oldest run directories beyond [`KEPT_RUNS`].
///
/// Answers the repo-relative path of the new run directory, or `None` when no attempt failed or nothing could be
/// copied. A copy that fails is skipped: the evidence is a convenience, and the run already has its verdict.
pub fn keep_failed_evidence(runtime: &dyn Runtime, bep: &BepSummary) -> Option<String> {
    let failed: Vec<&BepAttempt> = bep.attempts.iter().filter(|attempt| attempt.status != "PASSED").collect();
    if failed.is_empty() {
        return None;
    }
    let run = new_run_dir(runtime);
    let run_dir = repo_file(runtime, &run);
    let mut copied = 0;
    for attempt in failed {
        let target_dir = run_dir.join(attempt_dir(attempt));
        for path in [&attempt.log_path, &attempt.xml_path].into_iter().flatten() {
            let source = Path::new(path);
            if let Some(name) = source.file_name()
                && runtime.copy_file(source, &target_dir.join(name)).is_ok()
            {
                copied += 1;
            }
        }
        if let Some(outputs) = attempt
            .log_path
            .as_deref()
            .and_then(|log| Path::new(log).parent())
            .map(|dir| dir.join(OUTPUTS_DIR))
            && runtime.exists(&outputs)
        {
            copied += copy_tree(runtime, &outputs, &target_dir.join(OUTPUTS_DIR));
        }
    }
    if copied == 0 {
        // Nothing to point at: an empty run directory would only push a useful one out of the retention.
        let _ = runtime.remove_dir_all(&run_dir);
        return None;
    }
    remove_old_runs(runtime);
    Some(run)
}

/// A repo-relative run directory that no earlier run uses. The name starts with the wall clock in milliseconds,
/// zero-padded, so the names sort in run order, which is what the retention removes by.
fn new_run_dir(runtime: &dyn Runtime) -> String {
    let stamp = format!("{RUNS_DIR}/{:013}", runtime.now_ms());
    let mut candidate = stamp.clone();
    let mut suffix = 2;
    while runtime.exists(&repo_file(runtime, &candidate)) {
        candidate = format!("{stamp}-{suffix}");
        suffix += 1;
    }
    candidate
}

/// The directory of an attempt inside a run directory: its path under bazel's testlogs, such as
/// `plugins/a/b/target_test/shard_2_of_2`, or a name built from its label and shard when bazel reported no such path.
fn attempt_dir(attempt: &BepAttempt) -> PathBuf {
    let reported = attempt.log_path.as_deref().or(attempt.xml_path.as_deref());
    if let Some(reported) = reported {
        let normalized = reported.replace('\\', "/");
        if let Some((_, relative)) = normalized.split_once("/testlogs/")
            && let Some((dir, _)) = relative.rsplit_once('/')
        {
            return dir.split('/').collect();
        }
    }
    let label = attempt.label.trim_start_matches('@').trim_start_matches('/').replace(':', "/");
    let mut dir: PathBuf = label.split('/').filter(|part| !part.is_empty()).collect();
    dir.push(format!("run_{}_shard_{}_attempt_{}", attempt.run, attempt.shard, attempt.attempt));
    dir
}

/// Copies every file under `from` to the same place under `to`, and answers how many it copied.
fn copy_tree(runtime: &dyn Runtime, from: &Path, to: &Path) -> usize {
    let Ok(entries) = runtime.read_dir(from) else {
        return 0;
    };
    let mut copied = 0;
    for entry in entries {
        let (source, target) = (from.join(&entry.name), to.join(&entry.name));
        if entry.is_dir {
            copied += copy_tree(runtime, &source, &target);
        } else if runtime.copy_file(&source, &target).is_ok() {
            copied += 1;
        }
    }
    copied
}

/// Removes the oldest run directories, so [`KEPT_RUNS`] stay. Only a directory whose name starts with a digit is a
/// run directory: anything else under [`RUNS_DIR`] was put there by someone else and stays.
fn remove_old_runs(runtime: &dyn Runtime) {
    let runs_dir = repo_file(runtime, RUNS_DIR);
    let Ok(entries) = runtime.read_dir(&runs_dir) else {
        return;
    };
    let mut runs: Vec<String> = entries
        .into_iter()
        .filter(|entry| entry.is_dir && entry.name.starts_with(|first: char| first.is_ascii_digit()))
        .map(|entry| entry.name)
        .collect();
    runs.sort();
    let excess = runs.len().saturating_sub(KEPT_RUNS);
    for name in &runs[..excess] {
        let _ = runtime.remove_dir_all(&runs_dir.join(name));
    }
}

#[cfg(test)]
mod tests;
