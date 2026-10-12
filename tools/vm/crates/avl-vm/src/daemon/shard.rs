//! `shard`: one lane split by measured class duration over up to [`MAX_USEFUL_SHARDS`] leased workers, run at once
//! on one build, and merged into one verdict.
//!
//! - [`plan`]: the balancer and the JUnit filters that express a split;
//! - [`baseline`]: what the reports on disk say each class costs;
//! - [`command`]: the split over the held workers, one shard per worker, and the merge.

pub(crate) mod baseline;
pub(crate) mod command;
pub(crate) mod plan;

use crate::daemon::RunArgs;

pub(crate) use baseline::{ShardBaseline, read_shard_baseline};
pub(crate) use command::command_shard;
pub(crate) use plan::{ClassDuration, MAX_USEFUL_SHARDS, ShardAssignment, ShardKind, ShardPlan, plan_shards, shard_selection};

/// `shard --lane LANE --shards N [--holder ID] [--exact]` and every `run` option.
///
/// Only the three options that exist because there are several workers are this command's; the rest is `run`'s
/// grammar, flattened, so a lane selects here exactly what it selects for `run`.
#[derive(clap::Args, Clone, Debug, PartialEq, Eq)]
pub(crate) struct ShardArgs {
    /// How many workers to split the lane over.
    #[arg(
        long,
        value_name = "N",
        value_parser = clap::value_parser!(u8).range(1..=i64::from(MAX_USEFUL_SHARDS))
    )]
    pub shards: u8,
    /// The lease holder; the run id when absent. Naming the holder of a kept set recovers it.
    #[arg(long, value_name = "ID", allow_hyphen_values = true)]
    pub holder: Option<String>,
    /// Requires all N workers rather than at most N.
    #[arg(long)]
    pub exact: bool,
    #[command(flatten)]
    pub run: RunArgs,
}

impl ShardArgs {
    /// The arguments as one command line, the way the run journal names a run.
    pub(crate) fn argv(&self) -> Vec<String> {
        let mut argv = vec!["--shards".to_owned(), self.shards.to_string()];
        if let Some(holder) = &self.holder {
            argv.extend(["--holder".to_owned(), holder.clone()]);
        }
        if self.exact {
            argv.push("--exact".to_owned());
        }
        argv.extend(self.run.argv());
        argv
    }
}
