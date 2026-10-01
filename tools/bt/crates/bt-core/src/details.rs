//! The details of a refusal from a value that `serde` serializes.

use refusal::Refusal;
use serde::Serialize;

/// Gives a [`Refusal`] its details from a `serde` value. The `refusal` crate holds the details as JSON text, and it
/// has no `serde`.
pub trait WithDetails {
    /// The same refusal with the JSON text of `details`. A value that does not serialize gives the reason as a JSON
    /// string, so the details are not dropped: the evidence is what a caller reads next.
    #[must_use]
    fn with_details(self, details: impl Serialize) -> Self;
}

impl WithDetails for Refusal {
    fn with_details(self, details: impl Serialize) -> Self {
        let value = serde_json::to_value(details)
            .unwrap_or_else(|error| serde_json::Value::String(format!("the details did not serialize: {error}")));
        self.with_details_json_text(value.to_string())
    }
}
