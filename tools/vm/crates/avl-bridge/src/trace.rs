//! The readers of the trace routes of [avl_trace::bridge]: the facts, the Swing tree, a painted frame and the IDE's
//! own spans.
//!
//! A snapshot reads them while the lane waits for its ack, so a route that hangs would stall the scenario that the
//! recorder only watches. The recorder therefore gives each read a deadline well inside the lane's own.

use std::time::Instant;

use avl_trace::bridge::{
    FACTS_ROUTE, FLUSHED_SPANS_ROUTE, Facts, IdeSpans, PAINT_CONTENT_TYPE, PAINT_ROUTE, SPANS_ROUTE, TREE_ROUTE, Tree, decode_facts,
    decode_ide_spans, decode_tree,
};

use crate::client::protocol;
use crate::{Client, Error, JSON};

#[cfg(test)]
mod tests;

impl Client {
    /// Reads the facts route.
    pub fn facts(&self, deadline: Instant) -> Result<Facts, Error> {
        let body = self.get(FACTS_ROUTE, JSON, deadline)?;
        decode_facts(&body).map_err(|error| undecoded(FACTS_ROUTE, &error))
    }

    /// Reads the tree route and answers both the document, which is what a snapshot keeps verbatim, and its decoded
    /// form, which is where the drained inputs are read from.
    pub fn tree(&self, deadline: Instant) -> Result<(Vec<u8>, Tree), Error> {
        let body = self.get(TREE_ROUTE, JSON, deadline)?;
        let tree = decode_tree(&body).map_err(|error| undecoded(TREE_ROUTE, &error))?;
        Ok((body, tree))
    }

    /// Reads the spans the IDE ended since the last read of either spans route. `flushed` makes the IDE export its
    /// queued spans first, which can take seconds, so a snapshot asks without it.
    pub fn spans(&self, flushed: bool, deadline: Instant) -> Result<IdeSpans, Error> {
        let route = if flushed { FLUSHED_SPANS_ROUTE } else { SPANS_ROUTE };
        let body = self.get(route, JSON, deadline)?;
        decode_ide_spans(&body).map_err(|error| undecoded(route, &error))
    }

    /// Reads the paint route's PNG.
    pub fn paint(&self, deadline: Instant) -> Result<Vec<u8>, Error> {
        self.get(PAINT_ROUTE, PAINT_CONTENT_TYPE, deadline)
    }
}

fn undecoded(route: &str, error: &avl_trace::Error) -> Error {
    Error::of_call("GET", route, protocol(error.to_string()))
}
