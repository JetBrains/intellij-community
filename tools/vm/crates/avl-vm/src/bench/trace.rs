//! The reader of the Jaeger trace that `idea.diagnostic.opentelemetry.file` names.
//!
//! The IDE writes the file as a stream and closes the JSON at the exit. A file of a running IDE, or of an IDE that
//! did not exit cleanly, thus ends inside the span array. The reader repairs such a file and marks it truncated.

use anyhow::{Context, bail};
use serde::Deserialize;

#[derive(Deserialize)]
struct Jaeger {
    data: Vec<JaegerTrace>,
}

#[derive(Deserialize)]
struct JaegerTrace {
    spans: Vec<Span>,
}

/// One span. The times are in microseconds. The reader ignores the references.
#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Span {
    pub(crate) operation_name: String,
    /// Microseconds since the epoch.
    pub(crate) start_time: i64,
    pub(crate) duration: i64,
    /// The attributes of the span, such as `class` and `plugin` of a `run activity` span.
    #[serde(default)]
    pub(crate) tags: Vec<Tag>,
}

/// One attribute of a span. The IDE writes most values as strings, and the `type` field names the original type.
#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
pub(crate) struct Tag {
    pub(crate) key: String,
    pub(crate) value: serde_json::Value,
}

/// The suffixes of the coroutine helper spans, which are no measurement.
const HELPER_SUFFIXES: [&str; 2] = [": scheduled", ": completing"];

impl Span {
    /// The string value of the tag `key`.
    pub(crate) fn tag(&self, key: &str) -> Option<&str> {
        self.tags.iter().find(|tag| tag.key == key).and_then(|tag| tag.value.as_str())
    }

    /// The start of the span in milliseconds from `origin_us`, to a tenth. The origin is [`Trace::origin_us`], which a
    /// caller reads once for all spans.
    pub(crate) fn offset_ms(&self, origin_us: i64) -> f64 {
        micros_to_ms(self.start_time - origin_us)
    }

    /// Tells whether the span is a coroutine helper twin of a span: `<name>: scheduled` or `<name>: completing`.
    pub(crate) fn is_helper(&self) -> bool {
        HELPER_SUFFIXES.iter().any(|suffix| self.operation_name.ends_with(suffix))
    }
}

/// The spans of one file, in file order.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct Trace {
    pub(crate) spans: Vec<Span>,
    /// The file ended inside the span array, and the reader closed it.
    pub(crate) truncated: bool,
}

/// The most cuts that the repair tries before it gives up.
const MAX_REPAIR_CUTS: usize = 4096;

/// What closes a file that ends after a complete span object.
const CLOSING: &str = "]}]}";

impl Trace {
    /// The microseconds since the epoch of the process start: the start of `bootstrap`, else of the first span.
    pub(crate) fn origin_us(&self) -> Option<i64> {
        self.first("bootstrap")
            .map(|span| span.start_time)
            .or_else(|| self.spans.iter().map(|span| span.start_time).min())
    }

    /// The span with this name that starts first.
    pub(crate) fn first(&self, name: &str) -> Option<&Span> {
        self.spans
            .iter()
            .filter(|span| span.operation_name == name)
            .min_by_key(|span| span.start_time)
    }

    /// The span with this name that starts first at or after `from_us`.
    pub(crate) fn first_after(&self, name: &str, from_us: i64) -> Option<&Span> {
        self.spans
            .iter()
            .filter(|span| span.operation_name == name && span.start_time >= from_us)
            .min_by_key(|span| span.start_time)
    }
}

/// Microseconds as milliseconds, to a tenth.
pub(crate) fn micros_to_ms(micros: i64) -> f64 {
    tenth(as_f64(micros) / 1000.0)
}

/// Milliseconds to a tenth, the resolution of a span.
pub(crate) fn tenth(ms: f64) -> f64 {
    (ms * 10.0).round() / 10.0
}

/// A count or a duration as `f64`. The values stay far below 2^53.
pub(crate) const fn as_f64(value: i64) -> f64 {
    value as f64
}

/// Parses a trace. A file that ends inside the span array is cut at a `}` and closed.
pub(crate) fn parse(text: &str) -> anyhow::Result<Trace> {
    let complete_error = match parse_complete(text) {
        Ok(spans) => return Ok(Trace { spans, truncated: false }),
        Err(error) => error,
    };
    if !text.trim_start().starts_with("{\"data\":[") {
        return Err(complete_error).context("not a Jaeger trace");
    }
    let mut end = text.len();
    for _ in 0..MAX_REPAIR_CUTS {
        let Some(cut) = text[..end].rfind('}') else {
            break;
        };
        let repaired = format!("{}{CLOSING}", &text[..=cut]);
        if let Ok(spans) = parse_complete(&repaired) {
            return Ok(Trace { spans, truncated: true });
        }
        end = cut;
    }
    bail!("a truncated Jaeger trace that no cut repairs: {complete_error}")
}

fn parse_complete(text: &str) -> serde_json::Result<Vec<Span>> {
    let jaeger: Jaeger = serde_json::from_str(text)?;
    Ok(jaeger.data.into_iter().flat_map(|entry| entry.spans).collect())
}

#[cfg(test)]
mod tests;
