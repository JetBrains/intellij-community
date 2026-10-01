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

/// One span. The times are in microseconds. The reader ignores the tags and the references.
#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Span {
    pub(crate) operation_name: String,
    /// Microseconds since the epoch.
    pub(crate) start_time: i64,
    pub(crate) duration: i64,
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
