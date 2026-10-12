//! The UI lanes a VM worker may run: the integration lanes of the Air lane table, each with its test label and its
//! JUnit tag. The controller and the trace planner both read them here.

use std::sync::LazyLock;

use avl_base::Refusal;

use crate::bridge::air_area;
use avl_base::RefusalExt;

/// One IDE-launching lane: the only kind of thing a VM worker may run.
///
/// Everything else in the repository is host-runnable, and an arbitrary Bazel invocation has no meaning in a guest
/// with no checkout and no Bazel.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct UiLane {
    /// The lane's name, as `bt` declares it.
    pub name: &'static str,
    /// The IDE target the lane selects. Every one is in the daemon's classpath, so the label identifies the lane's
    /// *content* rather than a target to launch.
    pub label: &'static str,
    /// The JUnit tag the lane's suites carry, which is how the *daemon* selects a lane.
    ///
    /// A one-shot Bazel run has no need of this: one module per lane means the lane's Bazel tag already selects
    /// it, and `bt`'s lane table carries no JUnit filter at all. The daemon is the exception it was built to be - a
    /// single warm JVM holding the union of every lane's classpath - so an iteration can only name its lane by
    /// filter.
    pub junit_tag: &'static str,
    /// Whether the lane runs only when a caller names it: a lane with no `catalogLane`.
    ///
    /// No flow, suite or changed path reaches such a lane, because the suite join maps lanes through the catalog.
    /// `shard` and `flake` refuse it, because each repeats a selection and every scenario of `ui-live` spends a
    /// billed turn on a real account (ADR 0200).
    pub explicit_only: bool,
}

/// The UI lanes are `bt`'s integration lanes, in `bt`'s declared order, each with its test label and its JUnit
/// tag from the same lane table: nothing about a lane is spelled here. An integration lane missing either fact is
/// not runnable, and a test in `bt` pins that there is none. An integration lane without a catalog lane is
/// explicit-only.
static UI_LANES: LazyLock<Vec<UiLane>> = LazyLock::new(|| {
    air_area()
        .lanes()
        .iter()
        .filter(|lane| lane.integration_tag.is_some())
        .filter_map(|lane| {
            Some(UiLane {
                name: lane.name.as_str(),
                label: lane.test_label.as_deref()?,
                junit_tag: lane.junit_tag.as_deref()?,
                explicit_only: lane.catalog_lane.is_none(),
            })
        })
        .collect()
});

/// Every UI lane in declared order.
///
/// The order is read by a human - the "a VM worker runs ui, ui-real, gui-chat" refusal - and by `run`, which runs
/// one iteration per reached lane in this order.
pub fn ui_lanes() -> &'static [UiLane] {
    &UI_LANES
}

/// Every UI lane name in declared order.
pub fn ui_lane_names() -> Vec<&'static str> {
    ui_lanes().iter().map(|lane| lane.name).collect()
}

/// Every explicit-only UI lane name in declared order; see [`UiLane::explicit_only`].
pub fn explicit_only_lane_names() -> Vec<&'static str> {
    ui_lanes().iter().filter(|lane| lane.explicit_only).map(|lane| lane.name).collect()
}

/// The UI lane of that name, or a usage refusal naming the lanes a VM worker runs.
pub fn ui_lane(name: &str) -> Result<&'static UiLane, Refusal> {
    ui_lanes().iter().find(|lane| lane.name == name).ok_or_else(|| {
        Refusal::usage(format!(
            "--lane {name} is not an IDE UI lane; a VM worker runs {}",
            ui_lane_names().join(", ")
        ))
    })
}

/// The UI lane whose test target is exactly this label, or `None` for a label no UI lane runs: how the controller
/// and the trace planner tell which lane a resolved class belongs to.
pub fn ui_lane_of_label(label: &str) -> Option<&'static UiLane> {
    ui_lanes().iter().find(|lane| lane.label == label)
}

#[cfg(test)]
mod tests;
