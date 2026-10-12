//! The duration-balanced split: longest-processing-time packing into N bins, and the JUnit filters that express
//! it.
//!
//! **A shard is a JUnit filter, and the class list never has to be correct.** Shards 1..N-1 carry an
//! `include-classname` filter naming their classes; shard N - the one with the lightest predicted load - carries
//! `exclude-classname` of the union of all the others. That remainder-by-exclusion is what makes the union of the
//! shards provably the unsharded lane *for any class list, current or stale*: a suite generated after the last
//! measurement, a class renamed this morning, a class whose name could not be turned into a safe pattern - all of
//! them land in the remainder and run. Disjointness is structural rather than checked.
//!
//! **The class list is sorted by byte order**, which is `str`'s own `Ord`. Here a reordering does not reshuffle
//! output: LPT walks the sorted list, so moving one class changes which bin it lands in, which changes whether it
//! appears in an `include-classname` list or falls through to the remainder - *which tests run on which worker*.

use std::cmp::Ordering;

use crate::lane::class_name_pattern;
use avl_base::{Exit, Refusal};
use serde::{Deserialize, Serialize};

use crate::daemon::RunSelection;
use avl_base::RefusalExt;

#[cfg(test)]
mod tests;

/// The largest shard count this controller plans for, and why it is five rather than "the pool size".
///
/// A split's makespan can never fall below `max(slowest class, total / N)`. The lane measured 199 s of tests with a
/// 41.3 s slowest class, so `total / N` crosses under that single class between N=4 (49.8 s) and N=5 (39.8 s): at
/// N=5 the floor is already the slowest class alone, and every further worker buys nothing but another IDE to boot
/// and another competitor for the host's Bazel analysis cache. Refusing past it is the honest answer - a sixth shard
/// would report a smaller *predicted* makespan than the run can achieve.
pub(crate) const MAX_USEFUL_SHARDS: u8 = 5;

/// One class and what it last cost, which is the only input the balancer has.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct ClassDuration {
    pub class_name: String,
    pub duration_ms: f64,
}

/// How a shard names the tests it is responsible for.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub(crate) enum ShardKind {
    Include,
    Remainder,
}

/// One shard of a planned split.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct ShardAssignment {
    /// 1-based.
    pub shard_index: u32,
    pub kind: ShardKind,
    /// What this shard was balanced around.
    ///
    /// For an `include` shard that is exactly what it will run. For the `remainder` it is what the balancer
    /// *expects* it to run - the shard actually runs everything the others exclude, so its real load is this plus
    /// whatever the baseline had never seen.
    pub classes: Vec<String>,
    /// The sum of `classes`, so the remainder's figure is a lower bound and an include shard's is not.
    pub predicted_ms: f64,
    /// `option=value` filters, in the form the daemon groups and applies.
    pub junit5_filters: Vec<String>,
}

/// One split of a measured lane.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct ShardPlan {
    pub shard_count: usize,
    pub shards: Vec<ShardAssignment>,
    /// Every measured class summed: what the lane costs run serially.
    pub total_ms: f64,
    pub slowest_class_ms: f64,
    /// `max(slowest_class_ms, total_ms / shard_count)` - no split of this class list can beat it.
    pub floor_ms: f64,
    /// The heaviest shard's predicted load, which is what a sharded run is predicted to take.
    pub makespan_ms: f64,
}

#[derive(Default)]
struct Bin {
    classes: Vec<String>,
    load_ms: f64,
}

impl Bin {
    /// An empty bin sorts under the name nobody has.
    fn first_class(&self) -> &str {
        self.classes.first().map_or("", String::as_str)
    }
}

/// Longest-processing-time-first greedy: the classes in descending duration, each into the lightest bin.
///
/// LPT is 4/3-competitive in the worst case and, on a distribution with one class carrying a fifth of the lane,
/// effectively optimal - the makespan is pinned by that class either way. The important property is not the ratio
/// but that the schedule is deterministic: same durations, same bins, every time, which is what lets a test assert
/// the makespan against the real measurements.
fn pack_bins(durations: &[ClassDuration], shard_count: usize) -> Vec<Bin> {
    let mut bins: Vec<Bin> = (0..shard_count).map(|_| Bin::default()).collect();
    let mut ordered: Vec<&ClassDuration> = durations.iter().collect();
    ordered.sort_by(|left, right| {
        right
            .duration_ms
            .total_cmp(&left.duration_ms)
            .then_with(|| left.class_name.cmp(&right.class_name))
    });
    for entry in ordered {
        // The first of the lightest, so a tie always lands in the lower-numbered bin.
        let mut lightest = 0;
        for (index, bin) in bins.iter().enumerate().skip(1) {
            if bin.load_ms < bins[lightest].load_ms {
                lightest = index;
            }
        }
        bins[lightest].classes.push(entry.class_name.clone());
        bins[lightest].load_ms += entry.duration_ms;
    }
    bins
}

/// Turns a set of measured durations into N filter sets.
///
/// Pure, and the whole reason the split is testable without a VM: the fan-out contributes nothing to the decision
/// except the number of workers it managed to lease. `program` is the command a refusal tells the caller to run.
pub(crate) fn plan_shards(durations: &[ClassDuration], shard_count: usize, program: &str) -> Result<ShardPlan, Refusal> {
    if shard_count < 1 {
        return Err(Refusal::usage("--shards expects a positive integer"));
    }
    if shard_count > usize::from(MAX_USEFUL_SHARDS) {
        return Err(Refusal::usage(format!(
            "--shards {shard_count} exceeds {MAX_USEFUL_SHARDS}: a split's makespan cannot fall below the slowest \
             single class, and past five shards the lane's total divided by the shard count is already under it, \
             so a sixth worker predicts a makespan the run cannot achieve"
        )));
    }
    let total_ms: f64 = durations.iter().map(|entry| entry.duration_ms).sum();
    let slowest_class_ms = durations.iter().map(|entry| entry.duration_ms).fold(0.0, f64::max);
    if shard_count == 1 {
        // One shard is the unsharded lane, so it needs no measurement and gets no filter: the same code path
        // produces the baseline run that a first `--shards 2` will balance against.
        return Ok(ShardPlan {
            shard_count,
            shards: vec![ShardAssignment {
                shard_index: 1,
                kind: ShardKind::Remainder,
                classes: Vec::new(),
                predicted_ms: total_ms,
                junit5_filters: Vec::new(),
            }],
            total_ms,
            slowest_class_ms,
            floor_ms: total_ms,
            makespan_ms: total_ms,
        });
    }
    if durations.is_empty() {
        return Err(Refusal::new(
            "shard_baseline_unavailable",
            Exit::UNAVAILABLE,
            format!(
                "no run report on disk measures a class of this pool, and a shard split balanced by class *count* \
                 would be a plausible-looking wrong answer for this lane (median class 3.0 s, slowest 41.3 s); run \
                 `{program} run --lane <lane>` once — about 207 s — and shard the next one"
            ),
        ));
    }
    if durations.len() < shard_count {
        return Err(Refusal::new(
            "shard_count_exceeds_classes",
            Exit::USAGE,
            format!(
                "{} measured class(es) cannot fill {shard_count} shards; an empty shard discovers no tests, which \
                 the merge reports as lost coverage rather than as the planner bug it would be",
                durations.len()
            ),
        ));
    }
    // Heaviest bin first, so the lightest is last and becomes the remainder. The name tie-break keeps two equally
    // loaded bins in a fixed order rather than in whatever order the pack produced them.
    let mut bins = pack_bins(durations, shard_count);
    bins.sort_by(|left, right| match right.load_ms.total_cmp(&left.load_ms) {
        Ordering::Equal => left.first_class().cmp(right.first_class()),
        unequal => unequal,
    });
    let last = bins.len() - 1;
    let mut shards: Vec<ShardAssignment> = bins
        .into_iter()
        .enumerate()
        .map(|(index, bin)| {
            let mut classes = bin.classes;
            classes.sort();
            ShardAssignment {
                shard_index: u32::try_from(index + 1).unwrap_or(u32::MAX),
                kind: if index == last { ShardKind::Remainder } else { ShardKind::Include },
                classes,
                predicted_ms: bin.load_ms,
                junit5_filters: Vec::new(),
            }
        })
        .collect();
    let mut excluded: Vec<String> = shards
        .iter()
        .filter(|shard| shard.kind == ShardKind::Include)
        .flat_map(|shard| shard.classes.iter().cloned())
        .collect();
    excluded.sort();
    for shard in &mut shards {
        shard.junit5_filters = match shard.kind {
            ShardKind::Include => shard
                .classes
                .iter()
                .map(|class| format!("include-classname={}", class_name_pattern(class)))
                .collect(),
            ShardKind::Remainder => excluded
                .iter()
                .map(|class| format!("exclude-classname={}", class_name_pattern(class)))
                .collect(),
        };
    }
    let makespan_ms = shards.iter().map(|shard| shard.predicted_ms).fold(0.0, f64::max);
    Ok(ShardPlan {
        shard_count,
        shards,
        total_ms,
        slowest_class_ms,
        floor_ms: slowest_class_ms.max(total_ms / shard_count as f64),
        makespan_ms,
    })
}

/// The lane's own filters plus this shard's, and a description that says which shard produced a report.
pub(crate) fn shard_selection(base: &RunSelection, assignment: &ShardAssignment, shard_count: usize) -> RunSelection {
    let detail = match assignment.kind {
        ShardKind::Include => format!("{} class(es)", assignment.classes.len()),
        ShardKind::Remainder => format!("remainder, excluding {} class(es)", assignment.junit5_filters.len()),
    };
    RunSelection {
        selectors: base.selectors.clone(),
        junit5_filters: base.junit5_filters.iter().chain(&assignment.junit5_filters).cloned().collect(),
        description: format!("{} — shard {}/{shard_count} ({detail})", base.description, assignment.shard_index),
    }
}
