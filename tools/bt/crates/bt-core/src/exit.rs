//! The exit codes of `bt`, and the refusals that name one.
//!
//! `bt` has six *outcomes*, numbered 0-6, and they are the numbers an agent's shell script already branches on
//! ("exit 4 means nothing ran"). [`USAGE`] coincides with the usage status of the Air UI-lane controller: a bad
//! invocation of `bt` and of the controller is one failure. The [`Refusal`] type is the shared one of the `refusal`
//! crate, and each tool keeps its own exit codes.

use refusal::Refusal;

/// Every executed test passed. Note "executed": zero tests is [`NO_TESTS`].
pub const GREEN: u8 = 0;
/// The invocation is wrong (an unreadable selector, a name that resolves to nothing, two selectors) before
/// bazel is spawned.
pub const USAGE: u8 = 2;
/// The ordinary red run: tests ran and some failed.
pub const TEST_FAILED: u8 = 3;
/// Zero executed tests. Bazel cannot express it: a filter that matches nothing exits the runner with 42, which
/// bazel reports as an ordinary test failure, and a lane whose tag filter matches no target exits 4 having run
/// nothing at all. Both used to read as green or as red, and neither is either.
pub const NO_TESTS: u8 = 4;
/// A compile or analysis failure, so no test could run.
pub const BUILD_FAILED: u8 = 5;
/// Bazel itself failing: not installed, killed, a BEP file that never appeared. The run said nothing about
/// the tests.
pub const INFRA: u8 = 6;

/// The refusal for a bad invocation.
pub fn fail_usage(message: impl Into<String>) -> Refusal {
    Refusal::new("usage", USAGE, message)
}

/// The refusal for a repository that is not shaped the way resolution requires: two unfiltered `jps_test` rules in
/// one BUILD.bazel, an unreadable suite catalog, a file that vanished mid-resolution. Distinct from [`fail_usage`]
/// because the caller did nothing wrong and retrying the same command will not help.
pub fn fail_infra(message: impl Into<String>) -> Refusal {
    Refusal::new("bt_infra", INFRA, message)
}
