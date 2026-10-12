//! The core of `bt`, the agent-facing wrapper over `bazel test`: selector resolution, the areas and their lanes,
//! the suite catalog, Bazel test runs, and BEP and test.xml reading.
//!
//! A raw test run costs an agent hundreds of lines of bazel progress and summary noise, and `--test_output=errors`
//! dumps whole test logs (one failing test's test.xml measured 146 KB, almost all of it `<system-out>` platform-log
//! noise). So a caller passes a bare test class name, this crate resolves the label and the FQN itself, the `bt`
//! binary runs bazel with the UI silenced, reads results structurally out of the Build Event Protocol plus test.xml,
//! and answers a digest.
//!
//! # Areas
//!
//! This crate knows no path of any product. `bt.json` at the repository root names each area: a directory that
//! resolution scans, and the lane table of that directory ([`Areas`]). A lane table can also name a suite catalog,
//! which a flow or a suite selector reads.
//!
//! # A library with more than one consumer
//!
//! The `bt` binary is one consumer. The Air UI-lane controller and its trace planner resolve a lane or a selector
//! through the same [`Lanes`], [`Selector`], [`ResolutionInputs`] and [`resolve_selector`], so the lane table and
//! selector resolution are spelled once. For the same reason the production [`Runtime`] is injected by each
//! consumer: `bt` runs bazel, and the controller's selector resolution must never start a subprocess from inside a
//! held lease.

pub mod areas;
pub mod bep;
pub mod catalog;
pub mod details;
pub mod exit;
pub mod fake;
pub mod lanes;
pub mod modules;
pub mod os_runtime;
pub mod paths;
pub mod result;
pub mod runtime;
pub mod scan;
pub mod selector;
pub mod suites;

/// A `&'static Regex` compiled once, on first use.
#[macro_export]
macro_rules! regex {
    ($pattern:literal) => {{
        static PATTERN: std::sync::LazyLock<$crate::__regex::Regex> =
            // An invariant: the pattern is a literal, and every one is exercised by a test.
            std::sync::LazyLock::new(|| $crate::__regex::Regex::new($pattern).expect("a literal pattern compiles"));
        &*PATTERN
    }};
}

// The crate `regex!` expands to, so that a crate using the macro need not depend on `regex` itself.
#[doc(hidden)]
pub use regex as __regex;

pub use areas::{AREAS_FILE, Area, Areas};
pub use details::WithDetails;
pub use exit::{fail_infra, fail_usage};
pub use lanes::{LaneSpec, Lanes, bazel_command};
pub use modules::resolve_module;
pub use os_runtime::{OsRuntime, Spawner};
pub use refusal::Refusal;
pub use runtime::{Platform, Runtime};
pub use scan::ResolutionInputs;
pub use selector::{Selector, SelectorKind, resolve_selector};
pub use suites::{
    Affected, AffectedSuite, CHANGED_PATHS_SUBJECT, LaneCount, REASON_NO_SUITE_TESTS_FLOW, ScenarioFlows, UnmappedPath, VIA_MODULE,
    affected_text, choose_lane, classes_of_lane, counted_lane_names, describe_named, lane_counts, lane_counts_text, named_suites,
    reached_by_module_only, suite_class_filters,
};

#[cfg(test)]
mod tests;
