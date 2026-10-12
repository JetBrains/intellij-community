//! The capture ladder's second rung: the IDE paints its showing windows and the bridge answers the picture as a PNG.
//!
//! It is the rung for a macOS host and for the Tart macOS and Parallels guests, where there is no X server to read.
//! It sees less than X11 does, no native popup and no cursor, and each picture costs the IDE's event thread a paint,
//! so the recorder only asks for one at a snapshot and never at the video's frame rate.

use std::time::Instant;

use anyhow::Context;
use avl_trace::protocol::CaptureSource;

use super::{Frame, Source};
use crate::Clock;
use crate::bridge::Client;

#[cfg(test)]
mod tests;

/// Paints through one bridge.
pub(crate) struct IdePaint {
    client: Client,
    clock: Clock,
}

impl IdePaint {
    /// A source that paints through `client`, stamping each frame with `clock`.
    pub(crate) fn new(client: Client, clock: Clock) -> Self {
        Self { client, clock }
    }
}

impl Source for IdePaint {
    fn kind(&self) -> CaptureSource {
        CaptureSource::IdePaint
    }

    /// Asks for a paint and decodes it. The frame is stamped when the request is sent: the paint happens on the
    /// event thread at some point after that, and the request's start is the one moment the recorder knows.
    fn grab(&self, deadline: Instant, frame: &mut Frame) -> anyhow::Result<()> {
        let at = (self.clock)();
        let document = self.client.paint(deadline)?;
        frame
            .set_png(&document, at)
            .context("the paint route answered a PNG that does not decode")
    }
}
