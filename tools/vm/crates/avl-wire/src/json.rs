//! The serde helpers every document of this crate shares: the null conventions, and the shape check of
//! the agent-facing documents.
//!
//! serde reads a missing key and an explicit `null` into an `Option` the same way. These wires do not: an
//! absent optional is absent, and a `null` where a value belongs is a rename that landed halfway. So every
//! optional field of a daemon body, a report or an aggregate carries
//! `#[serde(default, deserialize_with = "crate::json::non_null")]`. The one reader that takes a `null` for the
//! default instead is the agent's reply as the controller receives it, [`crate::supervisor::ReceivedEnvelope`].

use serde::{Deserialize, Deserializer};
use serde_json::{Map, Value};

/// Reads a present value, refusing an explicit `null`. With `#[serde(default)]` an absent key stays `None`.
pub(crate) fn non_null<'de, D, T>(deserializer: D) -> Result<Option<T>, D::Error>
where
    D: Deserializer<'de>,
    T: Deserialize<'de>,
{
    T::deserialize(deserializer).map(Some)
}

/// Reads a value, taking an explicit `null` for the default. With `#[serde(default)]` an absent key is the default too.
pub(crate) fn null_as_default<'de, D, T>(deserializer: D) -> Result<T, D::Error>
where
    D: Deserializer<'de>,
    T: Default + Deserialize<'de>,
{
    Option::<T>::deserialize(deserializer).map(Option::unwrap_or_default)
}

/// Reads a tri-state field: absent (`None`, with `#[serde(default)]`), `null` (`Some(None)`), or a value.
///
/// serde writes `Some(None)` as `null`, so `skip_serializing_if = "Option::is_none"` is all the writing side
/// needs.
#[expect(
    clippy::option_option,
    reason = "the three states absent, null and a value are the wire's, and serde's default fills the outer None"
)]
pub(crate) fn double_option<'de, D, T>(deserializer: D) -> Result<Option<Option<T>>, D::Error>
where
    D: Deserializer<'de>,
    T: Deserialize<'de>,
{
    Option::<T>::deserialize(deserializer).map(Some)
}

/// The JSON kind a top-level field of a document must have.
#[derive(Clone, Copy, Debug)]
pub(crate) enum Kind {
    String,
    Number,
    Boolean,
    Object,
}

impl Kind {
    const fn name(self) -> &'static str {
        match self {
            Self::String => "string",
            Self::Number => "number",
            Self::Boolean => "boolean",
            Self::Object => "object",
        }
    }

    /// An explicit `null` is of no kind.
    fn admits(self, value: &Value) -> bool {
        match self {
            Self::String => value.is_string(),
            Self::Number => value.is_number(),
            Self::Boolean => value.is_boolean(),
            Self::Object => value.is_object(),
        }
    }
}

/// What a document's top level must carry before it is worth decoding: the fields a reader depends on, and the
/// kinds of the optional ones when they are present. Unlisted fields are permitted and ignored.
pub(crate) struct Shape {
    pub required: &'static [(&'static str, Kind)],
    pub optional: &'static [(&'static str, Kind)],
}

impl Shape {
    /// Why `fields` fail this shape, or `None`. The first failing field in table order is named, so a
    /// refusal is the same text every time.
    pub(crate) fn check(&self, fields: &Map<String, Value>, label: &str) -> Option<String> {
        for &(field, kind) in self.required {
            let value = fields.get(field);
            if !value.is_some_and(|value| kind.admits(value)) {
                return Some(format!("{label} has no {} {field} (got {})", kind.name(), shown(value)));
            }
        }
        for &(field, kind) in self.optional {
            if let Some(value) = fields.get(field)
                && !kind.admits(value)
            {
                let article = if matches!(kind, Kind::Object) { "an" } else { "a" };
                return Some(format!("{label} has a {field} that is not {article} {} (got {value})", kind.name()));
            }
        }
        None
    }
}

fn shown(value: Option<&Value>) -> String {
    value.map_or_else(|| "undefined".to_owned(), Value::to_string)
}

#[cfg(test)]
mod tests {
    use super::*;
    use proptest::prelude::*;
    use serde::Serialize;

    #[derive(Debug, Deserialize, Serialize, PartialEq)]
    struct Probe {
        #[serde(default, deserialize_with = "non_null", skip_serializing_if = "Option::is_none")]
        text: Option<String>,
        #[serde(default, deserialize_with = "double_option", skip_serializing_if = "Option::is_none")]
        #[expect(clippy::option_option, reason = "the probe of double_option holds its tri-state")]
        signal: Option<Option<String>>,
    }

    #[test]
    fn an_explicit_null_on_an_optional_is_refused() {
        serde_json::from_str::<Probe>(r#"{"text":null}"#).unwrap_err();
        assert_eq!(serde_json::from_str::<Probe>("{}").unwrap(), Probe { text: None, signal: None });
    }

    #[test]
    fn a_tri_state_keeps_absent_null_and_value_apart() {
        let absent: Probe = serde_json::from_str("{}").unwrap();
        let null: Probe = serde_json::from_str(r#"{"signal":null}"#).unwrap();
        let value: Probe = serde_json::from_str(r#"{"signal":"TERM"}"#).unwrap();
        assert_eq!(absent.signal, None);
        assert_eq!(null.signal, Some(None));
        assert_eq!(value.signal, Some(Some("TERM".to_owned())));
        assert_eq!(serde_json::to_string(&absent).unwrap(), "{}");
        assert_eq!(serde_json::to_string(&null).unwrap(), r#"{"signal":null}"#);
    }

    proptest! {
        // Any present string survives, any absence stays absent, and a null is never read as either.
        #[test]
        fn non_null_reads_every_present_string_and_no_null(text in any::<Option<String>>()) {
            let document = serde_json::to_string(&Probe { text: text.clone(), signal: None }).unwrap();
            prop_assert_eq!(serde_json::from_str::<Probe>(&document).unwrap().text, text);
            let null = serde_json::from_str::<Probe>(r#"{"text":null}"#);
            prop_assert!(null.is_err());
        }
    }

    #[test]
    fn a_shape_names_the_first_failing_field() {
        let shape = Shape {
            required: &[("alpha", Kind::String), ("beta", Kind::Number)],
            optional: &[("gamma", Kind::Object)],
        };
        let fields = |text: &str| serde_json::from_str::<Map<String, Value>>(text).unwrap();
        assert_eq!(shape.check(&fields(r#"{"alpha":"a","beta":1}"#), "a body"), None);
        assert_eq!(
            shape.check(&fields("{}"), "a body").as_deref(),
            Some("a body has no string alpha (got undefined)")
        );
        assert_eq!(
            shape.check(&fields(r#"{"alpha":"a","beta":null}"#), "a body").as_deref(),
            Some("a body has no number beta (got null)")
        );
        assert_eq!(
            shape.check(&fields(r#"{"alpha":"a","beta":1,"gamma":7}"#), "a body").as_deref(),
            Some("a body has a gamma that is not an object (got 7)")
        );
    }
}
