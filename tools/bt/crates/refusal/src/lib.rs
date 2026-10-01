//! A refusal: the expected failure of a person- or agent-facing CLI.
//!
//! A refusal has a stable code, a message, the exit code and optional details. A caller automates against the code
//! and the exit code. Each tool defines its own exit codes, and `bt_core::exit` holds the codes of `bt`.
//!
//! The details are JSON text, so this crate has no dependency. A caller that links another build of `serde_json`
//! parses the text with its own build. The Air UI-lane controller converts a refusal of `bt_core` into its own type
//! in that way.

use std::borrow::Cow;
use std::fmt;

/// An expected failure: a stable code, a readable message, the exit code, and optional details as JSON text.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Refusal {
    /// The half that a caller automates against, with [`Refusal::exit`]: `usage`, `bt_infra`, `no_affected_suite`.
    pub code: Cow<'static, str>,
    /// The text for a person.
    pub message: String,
    /// The exit code of the process. The tool defines the codes.
    pub exit: u8,
    details: Option<String>,
}

impl Refusal {
    /// A refusal without details.
    pub fn new(code: impl Into<Cow<'static, str>>, exit: u8, message: impl Into<String>) -> Self {
        Self {
            code: code.into(),
            message: message.into(),
            exit,
            details: None,
        }
    }

    /// The same refusal with details. The text must be one JSON value. This crate does not parse it.
    #[must_use]
    pub fn with_details_json_text(mut self, json_text: impl Into<String>) -> Self {
        self.details = Some(json_text.into());
        self
    }

    /// The details as JSON text, or `None` for a refusal without details.
    pub fn details_json_text(&self) -> Option<&str> {
        self.details.as_deref()
    }
}

impl fmt::Display for Refusal {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}

impl std::error::Error for Refusal {}

#[cfg(test)]
mod tests;
