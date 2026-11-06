//! The shard plan of a multi-target run: the number of shards of each test target, and the recorded seconds of each
//! test class.
//!
//! `bazel test` has one shard count for a whole invocation, and a lane holds targets of 5 s and of 500 s. So a planned
//! run has two groups of targets, and each group has its own `bazel test` invocation with a constant forced count:
//!
//! - A heavy target is a target whose recorded seconds want more than [`PLANNED_SHARDS`] shards. The first invocation
//!   names the heavy targets of the run ([`ShardPlan::heavy_targets`]) and forces [`HEAVY_SHARDS`].
//! - The second invocation runs the patterns of the run less the heavy targets, and forces [`PLANNED_SHARDS`].
//!
//! Each invocation passes the plan file to each test in [`SHARD_PLAN_ENV`]. The JUnit runner (`ShardFilter` of
//! `community/platform/testFramework/bootstrap`) reads the line of its own target. A shard at or above the planned
//! count exits before the test framework starts, and the other shards split the classes of the target by their
//! recorded seconds, each class whole. Only one shard then pays the setup of a class, and a small target starts one
//! test JVM instead of two.
//!
//! A forced count is part of the key of every test action. Both counts are constants, so the key of a target changes
//! only when the target moves to the other group. A target that a pattern covers in a way that bt cannot see stays in
//! the second invocation, and the runner gives it at most [`PLANNED_SHARDS`] shards there.
//!
//! After the run, [`ShardPlan::record`] takes the class times of each target whose shards all ran and passed. A target
//! that failed keeps its old lines, so a rerun of its failed shard splits the classes as its cached shards did. The
//! exception is a target with a shard timeout whose times of this run make it heavy:
//! [`ShardPlan::record_lower_bound`] raises its record. A run with a test filter records nothing, because it times
//! only some classes of a target.
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

/// The Bazel profile of the invocation of the heavy targets of a planned run, relative to the checkout root.
pub const HEAVY_PROFILE_FILE: &str = "out/bt/profile-heavy.json.gz";

/// The shard count that a planned run forces on every target that is not heavy.
///
/// Two, not four: Bazel starts every forced shard, and a shard that the plan leaves idle is still a JVM start. Measured
/// on a `--lane fast` run at load 70-100 (177 JVM targets), an idle shard took 1.0 s of CPU and 6.4 s of a test slot,
/// and a real shard of a tiny target 5.3 s of CPU. With the recorded plan, `forced=4` starts 493 idle shards, which eat
/// about two thirds of the JVM saving. `forced=2` starts 150.
pub const PLANNED_SHARDS: u32 = 2;

/// The shard count that a planned run forces on its heavy targets, and so the most shards the plan gives one target.
///
/// Eight, not four: a `--lane fast` plan has six heavy targets of 183 s to 530 s, which want three to six shards. At
/// `forced=8` they start about 30 idle shards, about 190 s of test slots, or 10 s on 20 cores.
pub const HEAVY_SHARDS: u32 = 8;

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

    /// Raises the class times of a target with a shard timeout to the times of this run, when the raise makes it a heavy
    /// target. Answers whether it did.
    ///
    /// The times of such a target are lower bounds, because the classes after the timeout did not run. A target that
    /// times out never passes, so without the raise it would keep its old record and time out in each run. A raise
    /// that makes it heavy moves it to the other invocation, so each of its shards reruns under another key and reads
    /// the new plan. A raise that keeps its group would split the classes of a rerun shard otherwise than its cached
    /// shards did, so the target keeps its old lines then.
    pub fn record_lower_bound(&mut self, timing: &TargetTiming) -> bool {
        if self.shards_of(&timing.label) > PLANNED_SHARDS {
            return false;
        }
        let mut raised = self.targets.get(&timing.label).cloned().unwrap_or_default();
        for (class, seconds) in &timing.class_seconds {
            let recorded = raised.entry(class.clone()).or_default();
            *recorded = recorded.max(*seconds);
        }
        if shards_for(raised.values().sum()) <= PLANNED_SHARDS {
            return false;
        }
        self.targets.insert(timing.label.clone(), raised);
        true
    }

    /// The shard count of the target with this label, from its recorded seconds.
    pub fn shards_of(&self, label: &str) -> u32 {
        self.targets
            .get(label)
            .map_or(DEFAULT_SHARDS, |classes| shards_for(classes.values().sum()))
    }

    /// The heavy targets of the plan that the target patterns of a run cover, in the order of their labels.
    ///
    /// A pattern covers a target as Bazel resolves it: `//pkg/...`, `//pkg:all`, `//pkg:*`, `//pkg:name` and `//pkg`,
    /// each with an optional repository. A later negative pattern (`-//pkg/...`) removes what it covers. bt reads no
    /// tag, so a covered target can still be one that the tag filters of the run leave out. The first invocation then
    /// runs no test of it.
    pub fn heavy_targets(&self, patterns: &[String]) -> Vec<String> {
        self.targets
            .keys()
            .filter(|label| self.shards_of(label) > PLANNED_SHARDS && covered(patterns, label))
            .cloned()
            .collect()
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
    #[expect(clippy::cast_possible_truncation, clippy::cast_sign_loss, reason = "clamped into 1..=HEAVY_SHARDS")]
    let shards = wanted.clamp(1.0, f64::from(HEAVY_SHARDS)) as u32;
    shards
}

/// Whether the target patterns, in their order, cover the label.
fn covered(patterns: &[String], label: &str) -> bool {
    let Some(target) = split_label(label) else {
        return false;
    };
    let mut covered = false;
    for pattern in patterns {
        let (negative, pattern) = match pattern.strip_prefix('-') {
            Some(rest) => (true, rest),
            None => (false, pattern.as_str()),
        };
        if pattern_covers(pattern, &target) {
            covered = !negative;
        }
    }
    covered
}

/// A label as its repository, package and name. `@@community+`, `@@community` and `@community` are one repository.
#[derive(Debug, PartialEq)]
struct Label<'a> {
    repository: &'a str,
    package: &'a str,
    name: &'a str,
}

fn split_label(label: &str) -> Option<Label<'_>> {
    let (repository, rest) = label.split_once("//")?;
    let repository = repository.trim_start_matches('@').trim_end_matches('+');
    let (package, name) = match rest.split_once(':') {
        Some((package, name)) => (package, name),
        None => (rest, rest.rsplit('/').next().unwrap_or(rest)),
    };
    Some(Label { repository, package, name })
}

fn pattern_covers(pattern: &str, target: &Label<'_>) -> bool {
    let Some(pattern) = split_label(pattern) else {
        return false;
    };
    if pattern.repository != target.repository {
        return false;
    }
    let any_name = matches!(pattern.name, "all" | "*" | "all-targets");
    if let Some(root) = pattern.package.strip_suffix("...") {
        // `//pkg/...` reads as the package `pkg/...` with the name `...`, and `//pkg/...:all` with the name `all`.
        if !(any_name || pattern.name == "...") {
            return false;
        }
        let root = root.trim_end_matches('/');
        return root.is_empty() || target.package == root || target.package.strip_prefix(root).is_some_and(|rest| rest.starts_with('/'));
    }
    pattern.package == target.package && (any_name || pattern.name == target.name)
}

#[cfg(test)]
mod tests;
