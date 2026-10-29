//! The shard plan of a multi-target run: the number of shards of each test target, and the recorded seconds of each
//! test class.
//!
//! `bazel test` has one shard count for a whole invocation, and a lane holds targets of 5 s and of 500 s. So a planned
//! run forces [`PLANNED_SHARDS`] on every target and passes the plan file to each test in [`SHARD_PLAN_ENV`]. The JUnit
//! runner (`ShardFilter` of `community/platform/testFramework/bootstrap`) reads the line of its own target. A shard at or
//! above the planned count exits before the test framework starts, and the other shards split the classes of the
//! target by their recorded seconds, each class whole. Only one shard then pays the setup of a class, and a small target
//! starts one test JVM instead of two.
//!
//! After the run, [`ShardPlan::record`] takes the class times of each target whose shards all ran and passed. A target
//! that failed keeps its old lines, so a rerun of its failed shard splits the classes as its cached shards did. A run
//! with a test filter records nothing, because it times only some classes of a target.
//!
//! The file is not a Bazel input: the plan decides how the tests of a target split, never which tests run, and an input
//! would rerun every test after each update. bt passes the path relative to the checkout. So the key of a test action
//! is the same in every checkout, and the checkouts share their cached test results.
//!
//! The shards of one target can still read different versions of the plan. A cached shard can come from another
//! checkout or an earlier run, and a concurrent bt can rewrite the file during a run. So each shard writes the digest
//! of the plan it read into its test.xml. A target whose shards report different digests makes the run INFRA.

use std::collections::BTreeMap;
use std::fmt::Write as _;

use crate::result::TargetTiming;

/// The variable that names the plan file to the JUnit runner.
pub const SHARD_PLAN_ENV: &str = "JB_TEST_SHARD_PLAN";

/// The plan file, relative to the checkout root.
pub const SHARD_PLAN_FILE: &str = "out/bt/shard-plan.tsv";

/// The Bazel profile of the last multi-target run, relative to the checkout root. It shows the compile and the tests
/// apart: `bazel analyze-profile out/bt/profile.json.gz`.
pub const PROFILE_FILE: &str = "out/bt/profile.json.gz";

/// The shard count a planned run forces on every target, and so the most shards the plan gives one target.
///
/// A constant rather than the largest count of the plan: a forced count is part of the key of every test action, so a
/// count that followed the plan would rerun the cached tests whenever the heaviest target crossed a step.
///
/// Two, not four: Bazel starts every forced shard, and a shard that the plan leaves idle is still a JVM start. Measured
/// on a `--lane fast` run at load 70-100 (177 JVM targets), an idle shard took 1.0 s of CPU and 6.4 s of a test slot,
/// and a real shard of a tiny target 5.3 s of CPU. With the recorded plan, `forced=4` starts 493 idle shards, which eat
/// about two thirds of the JVM saving. `forced=2` starts 150. A heavy target that wants more than two shards needs a
/// `shard_count` of its own or a second invocation.
pub const PLANNED_SHARDS: u32 = 2;

/// The shard count of a target the plan has no record of.
const DEFAULT_SHARDS: u32 = 2;

/// The recorded test seconds that one shard takes.
///
/// Simulated with the runner's partition on the test.xml files of a `--lane fast` run (148 JVM targets): against
/// `forced=2` by test, a shard per 90 s takes 18 % less test CPU with up to four shards (8.0 ks against 9.7 ks). A shard
/// per 60 s cuts no more off the slowest target and takes 3 % more CPU.
const SECONDS_PER_SHARD: f64 = 90.0;

/// The recorded seconds of each class, by target label.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct ShardPlan {
    targets: BTreeMap<String, BTreeMap<String, f64>>,
}

impl ShardPlan {
    /// Reads a plan file. A line this version does not know is skipped, so an old plan is read rather than refused:
    /// the plan steers the speed of a run and never its result.
    pub fn parse(text: &str) -> Self {
        let mut plan = Self::default();
        for line in text.lines() {
            let fields: Vec<&str> = line.split('\t').collect();
            if let ["class", label, class, seconds] = fields[..]
                && let Ok(seconds) = seconds.parse::<f64>()
            {
                plan.targets.entry(label.to_owned()).or_default().insert(class.to_owned(), seconds);
            }
        }
        plan
    }

    /// Replaces the class times of the target of a finished run.
    pub fn record(&mut self, timing: &TargetTiming) {
        self.targets.insert(timing.label.clone(), timing.class_seconds.clone());
    }

    /// The shard count of the target with this label, from its recorded seconds.
    pub fn shards_of(&self, label: &str) -> u32 {
        self.targets
            .get(label)
            .map_or(DEFAULT_SHARDS, |classes| shards_for(classes.values().sum()))
    }

    /// The text of the plan file, in the format that `ShardFilter.SHARD_PLAN_ENV` describes.
    pub fn render(&self) -> String {
        let mut text = String::from("# The shard plan of bt. bt rewrites this file after each multi-target run.\n");
        let _ = writeln!(text, "default\t{DEFAULT_SHARDS}");
        for (label, classes) in &self.targets {
            let _ = writeln!(text, "target\t{label}\t{}", self.shards_of(label));
            for (class, seconds) in classes {
                let _ = writeln!(text, "class\t{label}\t{class}\t{seconds:.3}");
            }
        }
        text
    }
}

fn shards_for(seconds: f64) -> u32 {
    let wanted = (seconds / SECONDS_PER_SHARD).ceil();
    #[expect(
        clippy::cast_possible_truncation,
        clippy::cast_sign_loss,
        reason = "clamped into 1..=PLANNED_SHARDS"
    )]
    let shards = wanted.clamp(1.0, f64::from(PLANNED_SHARDS)) as u32;
    shards
}

#[cfg(test)]
mod tests;
