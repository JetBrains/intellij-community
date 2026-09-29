//! A refusal: the one error of this crate, and the exit codes `bt` leaves with.
//!
//! The type is this crate's own rather than the Air UI-lane controller's, because this crate builds in a workspace
//! that knows nothing of the controller. The controller converts a refusal into its own type at the boundary, and
//! [`Refusal::details_json_text`] is what lets it carry the details across: the two workspaces link different
//! builds of `serde_json`, so a [`serde_json::Value`] of one is no value of the other.

use std::borrow::Cow;
use std::fmt;

use serde::Serialize;

/// The exit-code vocabulary, and it is `bt`'s own.
///
/// `bt` has six *outcomes*, numbered 0-6, and they are the numbers an agent's shell script already branches on
/// ("exit 4 means nothing ran"). [`USAGE`] coincides with the usage status of the Air UI-lane controller: a bad
/// invocation of `bt` and of the controller is one failure.
pub mod exit {
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
}

/// An expected failure: a stable code, a readable message, the exit code, and optional structured evidence.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Refusal {
    /// `usage`, `bt_infra`, `no_affected_suite`, …: the half a caller automates against, with [`Refusal::exit`].
    pub code: Cow<'static, str>,
    pub message: String,
    /// One of [`exit`].
    pub exit: u8,
    pub details: Option<serde_json::Value>,
}

impl Refusal {
    pub fn new(code: impl Into<Cow<'static, str>>, exit: u8, message: impl Into<String>) -> Self {
        Self {
            code: code.into(),
            message: message.into(),
            exit,
            details: None,
        }
    }

    /// The same refusal carrying structured evidence. A value that does not serialize is recorded as the reason
    /// it did not, rather than dropped: the evidence is what a caller reads next.
    #[must_use]
    pub fn with_details(mut self, details: impl Serialize) -> Self {
        self.details = Some(serde_json::to_value(details).unwrap_or_else(|error| {
            serde_json::Value::String(format!("the details did not serialize: {error}"))
        }));
        self
    }

    /// The details as JSON text, for a caller that links another build of `serde_json`.
    pub fn details_json_text(&self) -> Option<String> {
        self.details.as_ref().map(serde_json::Value::to_string)
    }
}

impl fmt::Display for Refusal {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}

impl std::error::Error for Refusal {}

/// The refusal for a bad invocation.
pub fn fail_usage(message: impl Into<String>) -> Refusal {
    Refusal::new("usage", exit::USAGE, message)
}

/// The refusal for a repository that is not shaped the way resolution requires: two unfiltered `jps_test` rules in
/// one BUILD.bazel, an unreadable suite catalog, a file that vanished mid-resolution. Distinct from [`fail_usage`]
/// because the caller did nothing wrong and retrying the same command will not help.
pub fn fail_infra(message: impl Into<String>) -> Refusal {
    Refusal::new("bt_infra", exit::INFRA, message)
}
