//! A command's expected failure, and the exit status it leaves with.
//!
//! The code is the stable half and the message is the readable one; the details carry whatever structured
//! evidence the refusal has. A refusal is the controller's one error: every command answers
//! `Result<Outcome, Refusal>`, so what reaches the envelope is the refusal a frame raised, never a wrapper to search.
//!
//! The type is the shared one of the `refusal` crate, with [`Exit`] as its exit vocabulary. [`RefusalExt`] adds the
//! controller's constructors and the details as a `serde` value, because the shared crate keeps them as JSON text.

use std::borrow::Cow;
use std::fmt;

use serde::Serialize;

#[cfg(test)]
mod tests;

/// The exit-code vocabulary.
///
/// These are `sysexits.h` numbers used with the meanings the controller needs, and they are a contract: a caller
/// branches on them, so the same failure must always leave by the same number.
///
/// A newtype over the number rather than a closed enum, because a guest agent's refusal keeps the guest's own
/// status ([`Exit::from_status`]): a number chosen inside a VM reaches an operator's shell through here. The
/// agent's statuses (`avl_wire::supervisor::AgentExit`) are all named below, and that is the property that
/// matters: a bad invocation is 2 here and 64 there, because `vm` and `vm-guest-agent` are two programs.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct Exit(u8);

impl Exit {
    pub const OK: Self = Self(0);
    /// The unclassified failure, and the number `internal_error` leaves by.
    pub const FAILURE: Self = Self(1);
    /// A bad argument and a bad environment variable alike: the invocation was wrong before any work started.
    pub const USAGE: Self = Self(2);
    /// A host build that failed, so no test ran. `bt` gives a build failure the same number.
    pub const BUILD_FAILED: Self = Self(5);
    /// A red lane: the tests ran and some failed. The one status that says something about the product rather
    /// than the controller.
    pub const TESTS_FAILED: Self = Self(6);
    /// A document that arrived and could not be read: a malformed receipt, an unknown schema version.
    pub const DATA_ERR: Self = Self(65);
    /// An input that is absent or unsafe to use: a lease file that is not there, a name a shell would
    /// reinterpret.
    pub const NO_INPUT: Self = Self(66);
    /// A tool or service the controller needs and cannot reach: `tart` not installed, a spawn that failed.
    pub const UNAVAILABLE: Self = Self(69);
    /// The far end speaking something this controller does not understand, or exceeding a declared limit. A
    /// guest verb that ran and refused lands here too: both are facts about the far end.
    pub const SOFTWARE: Self = Self(70);
    /// A destination that already exists. `pull` refuses to overwrite rather than choosing for the caller.
    pub const CANT_CREATE: Self = Self(73);
    /// Busy or timed out: retrying later is reasonable.
    pub const TEMP_FAIL: Self = Self(75);
    /// An operation the caller is not entitled to.
    pub const NO_PERM: Self = Self(77);

    /// The status as a number.
    pub const fn code(self) -> u8 {
        self.0
    }

    /// A status another program chose, kept as its own when it is a failure status, or `fallback` when it is 0
    /// or does not fit an exit status. A guest agent's refusal reaches the operator with the guest's number.
    pub fn from_status(status: i32, fallback: Self) -> Self {
        match u8::try_from(status) {
            Ok(0) | Err(_) => fallback,
            Ok(code) => Self(code),
        }
    }

    pub const fn is_ok(self) -> bool {
        self.0 == 0
    }
}

impl fmt::Display for Exit {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        self.0.fmt(f)
    }
}

impl From<Exit> for std::process::ExitCode {
    fn from(exit: Exit) -> Self {
        Self::from(exit.0)
    }
}

impl From<Exit> for i32 {
    fn from(exit: Exit) -> Self {
        Self::from(exit.0)
    }
}

/// A refusal with a code a caller can branch on, and the exit status of the controller.
///
/// The envelope writes the details as `null` rather than omitting them when there are none, so a caller reading
/// `error.details` never has to distinguish absent from empty.
pub type Refusal = ::refusal::Refusal<Exit>;

/// The controller's half of [`Refusal`]: its constructors, and the details as a `serde` value.
pub trait RefusalExt: Sized {
    /// The refusal for a bad invocation.
    fn usage(message: impl Into<String>) -> Self;

    /// The refusal for a malformed `AIR_VM_*` value.
    fn invalid_environment(message: impl Into<String>) -> Self;

    /// The refusal for a failure that is the controller's own bug, such as output that does not encode.
    fn internal(message: impl Into<String>) -> Self;

    /// The same refusal carrying structured evidence. A value that does not serialize is recorded as the reason
    /// it did not, rather than dropped: the evidence is what a caller reads next.
    #[must_use]
    fn with_details(self, details: impl Serialize) -> Self;

    /// The details as a value, or `None` for a refusal without details. Text that is no JSON value gives the
    /// reason as a JSON string, so the details are not dropped.
    fn details(&self) -> Option<serde_json::Value>;
}

impl RefusalExt for Refusal {
    fn usage(message: impl Into<String>) -> Self {
        Self::new("usage", Exit::USAGE, message)
    }

    fn invalid_environment(message: impl Into<String>) -> Self {
        Self::new("invalid_environment", Exit::USAGE, message)
    }

    fn internal(message: impl Into<String>) -> Self {
        Self::new("internal_error", Exit::FAILURE, message)
    }

    fn with_details(self, details: impl Serialize) -> Self {
        // Through a value, so the keys of an object are sorted, as the envelope has always written them.
        let value = serde_json::to_value(details)
            .unwrap_or_else(|error| serde_json::Value::String(format!("the details did not serialize: {error}")));
        self.with_details_json_text(value.to_string())
    }

    fn details(&self) -> Option<serde_json::Value> {
        self.details_json_text().map(|text| {
            serde_json::from_str(text).unwrap_or_else(|error| serde_json::Value::String(format!("the details did not parse: {error}")))
        })
    }
}

/// A runtime-descriptor refusal with the descriptor's own code. Every one is [`Exit::SOFTWARE`]: the far end of the
/// Bazel contract is unreadable, and a caller retries none of them.
pub fn descriptor_refusal(error: avl_wire::runtime::DescriptorError) -> Refusal {
    Refusal::new(error.code, Exit::SOFTWARE, error.message)
}

/// Turns any error into a refusal whose message is the caller's sentence followed by the cause:
/// `fs::create_dir_all(&dir).or_refuse("state_write_failed", Exit::FAILURE, || format!("cannot create {}", dir.display()))?`
/// refuses with `cannot create /x: Permission denied (os error 13)`.
pub trait OrRefuse<T> {
    fn or_refuse(self, code: impl Into<Cow<'static, str>>, exit: Exit, message: impl FnOnce() -> String) -> Result<T, Refusal>;
}

impl<T, E: fmt::Display> OrRefuse<T> for Result<T, E> {
    fn or_refuse(self, code: impl Into<Cow<'static, str>>, exit: Exit, message: impl FnOnce() -> String) -> Result<T, Refusal> {
        self.map_err(|error| Refusal::new(code, exit, format!("{}: {error:#}", message())))
    }
}
