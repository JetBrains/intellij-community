//! What a sharded lane must never do: run a class twice, run no class at all, or lose a class between the shards.
//! The balancer's numbers are the lane's real measurements rather than round fixtures - a split that looks balanced
//! on 24 equal classes is exactly the split this module refuses to produce.

#![expect(
    clippy::float_cmp,
    reason = "the balancer sums and compares whole milliseconds, exact in f64, so an epsilon would hide a wrong split"
)]

use std::cmp::Ordering;

use pretty_assertions::assert_eq;

use super::*;

// --- the lane, as it was actually measured ------------------------------------------------------------------------
//
// `air-linux-1`, 2026-08-21, 23 of 23 green: 199.0 s of tests in 207 s of wall clock. Five classes carry 72% of it,
// the median class is 3.0 s, and the filler is a spread whose median is exactly 3.0 s and whose sum closes the lane
// to 199.0 s. Round numbers would make every N look good; this distribution is the reason a count-based split is
// refused.

const HEAVY_CLASSES: [(&str, f64); 5] = [
    ("com.intellij.air.flow.AirCliAgentManagementUiTest", 41_300.0),
    ("com.intellij.air.flow.AirFlowRecycleSmokeUiTest", 33_100.0),
    ("com.intellij.air.flow.AirNewSessionShellTerminalUiTest", 28_900.0),
    ("com.intellij.air.flow.AirDownloadConsentAgentsPageUiTest", 20_300.0),
    ("com.intellij.air.flow.AirResumeSessionAfterRestartUiTest", 16_800.0),
];

/// 19 classes, median 3.0 s, sum 58.6 s - the rest of a 24-class lane across its two packages.
const FILLER_MS: [f64; 19] = [
    1_200.0, 1_500.0, 1_800.0, 2_100.0, 2_300.0, 2_500.0, 2_700.0, 2_900.0, 3_000.0, 3_000.0, 3_000.0, 3_100.0, 3_300.0, 3_500.0, 3_700.0,
    4_000.0, 4_400.0, 5_000.0, 5_600.0,
];

const LANE_TOTAL_MS: f64 = 199_000.0;

pub(crate) fn measured_lane() -> Vec<ClassDuration> {
    let heavy = HEAVY_CLASSES.iter().map(|(name, duration_ms)| ClassDuration {
        class_name: (*name).to_owned(),
        duration_ms: *duration_ms,
    });
    // Two packages, because the lane has two and `include-package` therefore cannot express a split.
    let filler = FILLER_MS.iter().enumerate().map(|(index, duration_ms)| ClassDuration {
        class_name: format!(
            "{}{index:02}UiTest",
            if index % 2 == 0 {
                "com.intellij.air.flow.AirFlowScenario"
            } else {
                "com.intellij.air.gui.AirGuiScenario"
            }
        ),
        duration_ms: *duration_ms,
    });
    heavy.chain(filler).collect()
}

fn plan(durations: &[ClassDuration], shard_count: usize) -> ShardPlan {
    plan_shards(durations, shard_count, "vm").unwrap_or_else(|refusal| panic!("planning {shard_count} shards: {refusal:?}"))
}

fn refused(durations: &[ClassDuration], shard_count: usize) -> Refusal {
    match plan_shards(durations, shard_count, "vm") {
        Ok(plan) => panic!("{shard_count} shards were expected to refuse, planned {plan:?}"),
        Err(refusal) => refusal,
    }
}

// --- filter evaluation --------------------------------------------------------------------------------------------

/// The literal a `^\Q…\E$` pattern stands for.
///
/// The evaluator compares literals rather than compiling Java regexes, which is the point: it asserts that every
/// pattern emitted *is* an anchored literal. A pattern that escaped anything less than completely would fail here
/// rather than quietly matching a second class.
fn literal_of(pattern: &str) -> &str {
    pattern
        .strip_prefix(r"^\Q")
        .and_then(|rest| rest.strip_suffix(r"\E$"))
        .unwrap_or_else(|| panic!("not an anchored literal class pattern: {pattern}"))
}

fn filter_literals<'a>(assignment: &'a ShardAssignment, option: &str) -> Vec<&'a str> {
    let prefix = format!("{option}=");
    assignment
        .junit5_filters
        .iter()
        .filter_map(|filter| filter.strip_prefix(&prefix))
        .map(literal_of)
        .collect()
}

/// Whether one shard would run one class, the way `ClassNameFilter` decides it: the includes admit a class matching
/// *any* of them, the excludes reject one matching any of theirs, and a shard with no include admits everything the
/// excludes leave.
fn shard_runs(assignment: &ShardAssignment, class_name: &str) -> bool {
    let includes = filter_literals(assignment, "include-classname");
    if !includes.is_empty() && !includes.contains(&class_name) {
        return false;
    }
    !filter_literals(assignment, "exclude-classname").contains(&class_name)
}

/// JavaScript's `<` on a string: UTF-16 code-unit order - the order this controller had to *not* pick.
fn utf16_compare(left: &str, right: &str) -> Ordering {
    left.encode_utf16().cmp(right.encode_utf16())
}

// --- the balancer -------------------------------------------------------------------------------------------------

// A reordering moves a class between an include list and the remainder, which is *which tests run on which worker*.
// The candidate orders agree for every ASCII class name, so nothing but this test would catch a different one.
#[test]
fn the_class_list_is_sorted_by_utf8_byte_order_chosen_rather_than_inherited() {
    assert_eq!(
        "a".cmp("B"),
        Ordering::Greater,
        "byte order puts uppercase first; a collation would put `a` before `B`"
    );
    // U+E000 encodes as EE 80 80 and U+10000 as F0 90 80 80, so byte order puts U+E000 first. UTF-16 code-unit
    // order puts the surrogate pair (D800 DC00) first instead: opposite answers from the same pair of strings.
    let (private_use, astral) = ("\u{E000}", "\u{10000}");
    assert_eq!(private_use.cmp(astral), Ordering::Less);
    assert_eq!(
        utf16_compare(private_use, astral),
        Ordering::Greater,
        "the pin is worthless unless UTF-16 order genuinely disagrees on this pair"
    );
    assert!("Air" < "AirFlow", "a prefix sorts first");

    // The exact order of a representative list, pinned so a comparator swap is a diff rather than a surprise.
    let mut ordered = vec!["b", "A", "a$", "a.b", "aB", "Ab"];
    ordered.sort_unstable();
    assert_eq!(ordered, ["A", "Ab", "a$", "a.b", "aB", "b"]);

    // And the order the planner publishes is that one.
    for assignment in plan(&measured_lane(), 3).shards {
        let mut sorted = assignment.classes.clone();
        sorted.sort();
        assert_eq!(
            sorted, assignment.classes,
            "shard {} publishes an unsorted class list",
            assignment.shard_index
        );
    }
}

#[test]
fn lpt_lands_within_115_percent_of_the_floor_on_the_lanes_real_durations() {
    let lane = measured_lane();
    assert_eq!(lane.len(), 24, "the measured lane is 24 classes");
    for shard_count in 2..=5 {
        let plan = plan(&lane, shard_count);
        assert_eq!(plan.total_ms, LANE_TOTAL_MS);
        assert_eq!(plan.slowest_class_ms, 41_300.0);
        let floor = (LANE_TOTAL_MS / shard_count as f64).max(41_300.0);
        assert_eq!(plan.floor_ms, floor, "the floor at {shard_count} shards");
        // The bound is on the *achievable* makespan, so a planner reporting a smaller number than any schedule can
        // reach fails here rather than looking better than optimal.
        assert!(
            plan.makespan_ms >= plan.floor_ms && plan.makespan_ms <= plan.floor_ms * 1.15,
            "at {shard_count} shards the makespan {} is outside [floor, 1.15 x floor]",
            plan.makespan_ms
        );
        // Every class is in exactly one bin, and the bins add back up to the lane.
        let summed: f64 = plan.shards.iter().map(|shard| shard.predicted_ms).sum();
        let placed: usize = plan.shards.iter().map(|shard| shard.classes.len()).sum();
        assert_eq!(summed, LANE_TOTAL_MS);
        assert_eq!(placed, lane.len());
    }
}

#[test]
fn five_shards_is_the_last_count_that_buys_anything_and_the_sixth_is_refused() {
    let lane = measured_lane();
    assert_eq!(MAX_USEFUL_SHARDS, 5);
    // At N=5 the floor is already one class on its own: total/N (39.8 s) has fallen under the 41.3 s class, so a
    // sixth worker cannot lower the makespan and would only predict that it had.
    assert_eq!(plan(&lane, 4).floor_ms, LANE_TOTAL_MS / 4.0);
    assert_eq!(plan(&lane, 5).floor_ms, 41_300.0);
    let sixth = refused(&lane, usize::from(MAX_USEFUL_SHARDS) + 1);
    assert_eq!(sixth.exit, Exit::USAGE);
    assert!(sixth.message.contains("exceeds 5"), "{}", sixth.message);
    assert!(sixth.message.contains("makespan the run cannot achieve"), "{}", sixth.message);
}

#[test]
fn the_remainder_is_the_lightest_shard_and_the_only_one_that_excludes() {
    let lane = measured_lane();
    for shard_count in 2..=5 {
        let plan = plan(&lane, shard_count);
        let remainders: Vec<&ShardAssignment> = plan.shards.iter().filter(|shard| shard.kind == ShardKind::Remainder).collect();
        let [remainder] = remainders.as_slice() else {
            panic!("exactly one remainder, got {remainders:?}");
        };
        assert_eq!(remainder.shard_index as usize, shard_count, "the remainder is the last shard");
        // Lightest by prediction: nothing else may carry less than the shard that also absorbs the unknown.
        for shard in &plan.shards {
            assert!(
                shard.predicted_ms >= remainder.predicted_ms,
                "shard {} is lighter than the remainder",
                shard.shard_index
            );
        }
        let mut included: Vec<&str> = Vec::new();
        for shard in plan.shards.iter().filter(|shard| shard.kind == ShardKind::Include) {
            included.extend(shard.classes.iter().map(String::as_str));
            assert!(filter_literals(shard, "exclude-classname").is_empty());
            assert_eq!(
                filter_literals(shard, "include-classname"),
                shard.classes,
                "shard {}'s includes are exactly its classes",
                shard.shard_index
            );
        }
        included.sort_unstable();
        let mut excludes = filter_literals(remainder, "exclude-classname");
        excludes.sort_unstable();
        // Exactly the other shards' classes, and no more: an exclude naming its own class would drop it.
        assert_eq!(excludes, included, "at {shard_count} shards");
        assert!(filter_literals(remainder, "include-classname").is_empty());
    }
}

#[test]
fn the_shards_are_disjoint_and_total_including_for_a_class_the_balancer_never_saw() {
    let lane = measured_lane();
    // The class list the *daemon* will discover, which is not the list the plan was built from: a flow suite
    // generated after the last measurement, and a class renamed since. Both must run, on exactly one shard.
    let unseen = [
        "com.intellij.air.flow.AirBrandNewGeneratedFlowUiTest",
        "com.intellij.air.gui.AirRenamedSinceTheBaselineUiTest",
    ];
    let discovered: Vec<&str> = lane.iter().map(|entry| entry.class_name.as_str()).chain(unseen).collect();
    for shard_count in 2..=5 {
        let plan = plan(&lane, shard_count);
        for class_name in &discovered {
            // Exactly one shard per class: two would be `shard_overlap` in the merge, zero would be coverage that
            // vanished with nothing to report it. The property has to hold for a *stale* class list, which is what
            // makes remainder-by-exclusion load-bearing rather than stylistic.
            let owners: Vec<u32> = plan
                .shards
                .iter()
                .filter(|shard| shard_runs(shard, class_name))
                .map(|shard| shard.shard_index)
                .collect();
            assert_eq!(owners.len(), 1, "{class_name} is owned by {owners:?}");
        }
        // ...and the two the balancer never saw land on the remainder, which is the shard that absorbs them.
        let remainder = plan.shards.last().expect("a plan has shards");
        for class_name in unseen {
            assert!(shard_runs(remainder, class_name), "{class_name}");
        }
    }

    // One shard is the unsharded lane by construction: no filter at all, so nothing can be lost by it.
    let single = plan(&lane, 1);
    assert_eq!(single.shards.len(), 1);
    assert!(single.shards[0].junit5_filters.is_empty());
    for class_name in &discovered {
        assert!(shard_runs(&single.shards[0], class_name));
    }
}

#[test]
fn with_nothing_measured_the_split_is_refused_rather_than_guessed_by_class_count() {
    let unmeasured = refused(&[], 2);
    assert_eq!(
        (unmeasured.code.as_ref(), unmeasured.exit),
        ("shard_baseline_unavailable", Exit::UNAVAILABLE)
    );
    assert!(
        unmeasured.message.contains("run --lane"),
        "the refusal says how to earn a baseline: {}",
        unmeasured.message
    );
    // Fewer classes than shards is a different refusal: an empty shard discovers no tests, which the merge reads as
    // lost coverage rather than as the planner bug it would be.
    let too_few = refused(&measured_lane()[..2], 3);
    assert_eq!((too_few.code.as_ref(), too_few.exit), ("shard_count_exceeds_classes", Exit::USAGE));
    assert!(too_few.message.contains("cannot fill 3 shards"), "{}", too_few.message);
    // One shard needs no measurement at all, because it is the lane.
    assert_eq!(plan(&[], 1).shards[0].kind, ShardKind::Remainder);
    // And a count below one is refused before anything is read.
    let zero = refused(&measured_lane(), 0);
    assert_eq!((zero.code.as_ref(), zero.exit), ("usage", Exit::USAGE));
}

// Same duration for every class, so the pack produces four identical loads and only the tie-breaks decide the
// order. Without them the shard indexes - and therefore which worker runs which class - would depend on sort
// incidentals.
#[test]
fn two_equally_loaded_bins_are_ordered_by_their_first_class_name() {
    let durations: Vec<ClassDuration> = ["z.OneTest", "a.TwoTest", "m.ThreeTest", "b.FourTest"]
        .into_iter()
        .map(|class_name| ClassDuration {
            class_name: class_name.to_owned(),
            duration_ms: 1_000.0,
        })
        .collect();
    let first = plan(&durations, 4);
    // The duration tie-break inside a bin is the class name, so reversing the input changes nothing.
    let reversed: Vec<ClassDuration> = durations.iter().rev().cloned().collect();
    assert_eq!(first, plan(&reversed, 4));
    let names: Vec<&str> = first.shards.iter().map(|shard| shard.classes[0].as_str()).collect();
    assert_eq!(names, ["a.TwoTest", "b.FourTest", "m.ThreeTest", "z.OneTest"]);
}

// --- one shard's selection ----------------------------------------------------------------------------------------

#[test]
fn a_shards_selection_carries_the_lanes_own_filters_and_names_itself() {
    let base = RunSelection {
        selectors: Vec::new(),
        junit5_filters: vec!["include-tag=air-flow-ui".to_owned()],
        description: "lane ui".to_owned(),
    };
    let include = shard_selection(
        &base,
        &ShardAssignment {
            shard_index: 1,
            kind: ShardKind::Include,
            classes: vec!["a.OneTest".to_owned(), "a.TwoTest".to_owned()],
            predicted_ms: 0.0,
            junit5_filters: vec!["include-classname=x".to_owned(), "include-classname=y".to_owned()],
        },
        2,
    );
    // The lane's filters come first and the shard's after.
    assert_eq!(
        include.junit5_filters,
        ["include-tag=air-flow-ui", "include-classname=x", "include-classname=y"]
    );
    assert_eq!(include.description, "lane ui — shard 1/2 (2 class(es))");
    let remainder = shard_selection(
        &base,
        &ShardAssignment {
            shard_index: 2,
            kind: ShardKind::Remainder,
            classes: vec!["a.ThreeTest".to_owned()],
            predicted_ms: 0.0,
            junit5_filters: vec!["exclude-classname=x".to_owned(), "exclude-classname=y".to_owned()],
        },
        2,
    );
    // The remainder names what it excludes rather than what it expects, because what it *runs* is everything the
    // others do not - including classes the balancer never saw.
    assert_eq!(remainder.description, "lane ui — shard 2/2 (remainder, excluding 2 class(es))");
}
