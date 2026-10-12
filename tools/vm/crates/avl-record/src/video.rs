//! A scenario's video, `video.mp4`, and the frame index beside it.
//!
//! Two sources, one per capture rung that can have a video:
//!
//! - Raw ([h264]): the recorder grabs the X11 frames itself and encodes each one, one per tick, the previous one
//!   again when the screen did not change. The recorder stamps every frame it encodes, so `video.index.json` is
//!   exact: frame i is the picture read at `framesMs[i]`.
//! - Screen ([ffmpeg]): on a macOS host, where there is no X server, the machine's ffmpeg captures the screen itself
//!   through `avfoundation`. The recorder never sees those frames, so the index is reconstructed from ffmpeg's
//!   progress reports and is exact only to about one report interval.
//!
//! Both write H.264 in fragmented MP4, the raw video through [mp4], with a keyframe at least every second: the codec
//! with hardware decode in both Chrome and Safari on every Mac, and a container whose every fragment plays on its own,
//! so a recording killed mid-scenario still leaves a video up to its last second.
//!
//! The raw input has one owner: the frame loop holds the [FrameSink], and nothing else writes to the encoder.

use std::path::Path;
use std::time::Duration;

use avl_trace::bundle::{VIDEO_INDEX_FILE, VideoIndex};

use crate::bundle::write_file_atomically;
use crate::capture::Frame;

pub(crate) mod ffmpeg;
// The encoder is built on Unix only, and the muxer has no other writer.
#[cfg(unix)]
pub(crate) mod h264;
#[cfg(unix)]
pub(crate) mod mp4;

/// The rate frames are captured and encoded at, and the index's frame rate.
pub(crate) const FRAME_RATE: u32 = 10;

/// The time between two frames at [FRAME_RATE].
pub(crate) const FRAME_INTERVAL: Duration = Duration::from_millis(1000 / FRAME_RATE as u64);

/// Where the frame loop writes frames: a raw recording's input.
pub(crate) trait FrameSink: Send {
    fn write_frame(&mut self, frame: &Frame) -> anyhow::Result<()>;
}

/// How a recording that has a usable video ended.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Stopped {
    /// How many frames the index lists.
    pub(crate) frames: usize,
    /// The first frame's capture time.
    pub(crate) first_frame_ms: i64,
}

/// One scenario's running video.
pub(crate) trait Recording: Send {
    /// Whether the recording takes its frames from a [FrameSink].
    fn raw(&self) -> bool;

    /// The sink of a raw recording, once; `None` for a screen recording.
    fn take_sink(&mut self) -> Option<Box<dyn FrameSink>>;

    /// Ends the recording and writes its index. The error says why the bundle has no usable video; it is `Ok`
    /// exactly when `video.mp4` and its index are both written.
    fn stop(&mut self) -> anyhow::Result<Stopped>;
}

/// Writes `video.index.json` into `dir`, under a temporary name renamed into place like every other file of a
/// bundle: a pack running beside a recorder still finishing a truncated bundle, or a viewer tailing a running one,
/// would otherwise read half an index, which the contract refuses, and lose the seekable video.
pub(crate) fn write_index(dir: &Path, index: &VideoIndex) -> anyhow::Result<()> {
    let mut document = avl_trace::encode(index)?;
    document.push(b'\n');
    write_file_atomically(&dir.join(VIDEO_INDEX_FILE), &document)?;
    Ok(())
}
