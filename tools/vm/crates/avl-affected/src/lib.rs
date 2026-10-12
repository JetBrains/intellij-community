//! The Air side of BT's suite join: which suites a changed path reaches, through the flow tags, the specs, the
//! lane harness and the JPS modules. BT's own crates, in `community/tools/bt`, know no Air path, so the join that
//! reads Air's specs and `.iml` files lives here, and so does [`bridge`], where the controller meets BT. [`lanes`]
//! holds the UI lanes a VM worker runs, which the controller and the trace planner both read.

pub mod affected;
pub mod bridge;
pub mod lanes;
mod specs;

/// A `&'static Regex` compiled once, on first use.
///
/// This crate's own and not `bt_core::regex!`: under Bazel BT's crates link another build of `regex`, so a pattern
/// that macro compiles is no `regex::Regex` of this crate. Cargo unifies the two builds and would not show it.
macro_rules! regex {
    ($pattern:literal) => {{
        static PATTERN: std::sync::LazyLock<regex::Regex> =
            // An invariant: the pattern is a literal, and every one is exercised by a test.
            std::sync::LazyLock::new(|| regex::Regex::new($pattern).expect("a literal pattern compiles"));
        &*PATTERN
    }};
}
pub(crate) use regex;

pub use affected::{VIA_LANE_WIDE, affected_suites, reason};
pub use bridge::{air_area, air_areas, refusal};
pub use lanes::{UiLane, explicit_only_lane_names, ui_lane, ui_lane_names, ui_lane_of_label, ui_lanes};
