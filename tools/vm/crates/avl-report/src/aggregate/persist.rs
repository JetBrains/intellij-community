//! Where one run's aggregates live, and the publish that refuses to overwrite one.

use std::path::PathBuf;

use avl_base::fs::{PublishError, create_private_dir};
use avl_base::{Config, Exit, OrRefuse, Refusal, validate_name};
use avl_wire::report::{AggregateKind, FlakeSummary, ShardVerdict};
use serde::Serialize;

#[cfg(test)]
mod tests;

/// Where one run's aggregate lives: `<runtimeRoot>/aggregates/<runId>/<kind>.json`, with the directory created at
/// 0700 so the answer has somewhere to land.
///
/// Deliberately *outside* `workers/*/reports/`: a report directory is keyed on one worker and one daemon run, and
/// an aggregate speaks for N workers. Worse, the shard balancer scans that tree for class durations, so a merged
/// document written into it would come back as another measurement of the same classes and balance the next split
/// against a number no worker ever produced.
///
/// The run id becomes a path component, so it is validated like every other name that does.
pub fn aggregate_path(settings: &Config, run_id: &str, kind: AggregateKind) -> Result<PathBuf, Refusal> {
    validate_name(run_id, "run id")?;
    let directory = settings.runtime_root.join("aggregates").join(run_id);
    create_private_dir(&directory)?;
    Ok(directory.join(format!("{}.json", kind.as_str())))
}

/// Writes the merged verdict of one sharded run and answers where it went.
pub fn persist_shard_verdict(settings: &Config, run_id: &str, verdict: &ShardVerdict) -> Result<PathBuf, Refusal> {
    persist(settings, run_id, AggregateKind::Shard, verdict)
}

/// Writes the folded rate of one flake run and answers where it went.
///
/// Beside the shard verdicts rather than in a tree of its own: both are one run's merged answer over several
/// workers, both are read by an agent after the process is gone, and one home means one place to look.
pub fn persist_flake_summary(settings: &Config, run_id: &str, summary: &FlakeSummary) -> Result<PathBuf, Refusal> {
    persist(settings, run_id, AggregateKind::Flake, summary)
}

/// Publishes one aggregate where it cannot overwrite an earlier one.
///
/// A run id names one run, so a second document under the same path is a *collision* and not an update:
/// overwriting is how one of two verdicts disappears without anybody being told, and the refusal is the
/// filesystem's no-clobber move rather than a check above it. The publish is synced, because the envelope naming
/// this path is printed before the process leaves, and a zero-length file parses as a run with no failures.
///
/// Two-space indentation and one trailing newline: a verdict is compared against a previous run's by `diff`.
fn persist(settings: &Config, run_id: &str, kind: AggregateKind, document: &impl Serialize) -> Result<PathBuf, Refusal> {
    let destination = aggregate_path(settings, run_id, kind)?;
    let mut content = serde_json::to_vec_pretty(document).or_refuse("state_write_failed", Exit::FAILURE, || {
        format!("cannot encode {}", destination.display())
    })?;
    content.push(b'\n');
    match avl_base::fs::write_exclusively(&destination, &content, 0o600) {
        Ok(()) => Ok(destination),
        // One code for "this aggregate did not get written", with the cause in the sentence: a collision means a
        // bug upstream of here, which is worth its own sentence but not its own wire contract.
        Err(PublishError::DestinationExists(_)) => Err(Refusal::new(
            "state_write_failed",
            Exit::FAILURE,
            format!(
                "{} already holds an aggregate: a run id names one run, and overwriting is how one of two \
                 answers disappears",
                destination.display()
            ),
        )),
        Err(error) => Err(Refusal::new(
            "state_write_failed",
            Exit::FAILURE,
            format!("cannot write {}: {error}", destination.display()),
        )),
    }
}
