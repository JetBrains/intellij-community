//! Where a session's pixels and its video come from: the capture ladder and the videographer, as traits the replay
//! replaces, and the system's own of each.

use std::path::{Path, PathBuf};
use std::sync::Arc;

use avl_trace::protocol::{CaptureSource, VideoCodec};

use crate::bridge::Client;
use crate::capture::ide::IdePaint;
use crate::capture::{Frame, Source};
use crate::video::{self, Recording};
use crate::{Clock, Diagnostics, unix_ms};

#[cfg(test)]
mod tests;

/// What the ladder knows about the screen the IDE draws on, and whether it may read it.
#[derive(Clone, Debug, Default)]
pub(crate) struct ScreenAccess {
    /// The IDE's `DISPLAY` from the facts route, empty when unknown.
    pub(crate) display: String,
    /// The IDE's operating system from the facts route, empty when unknown.
    pub(crate) os: String,
    /// The hello's `screen`: the lane lets the recorder read the screen itself.
    pub(crate) allowed: bool,
}

/// Picks where a session's pixels come from.
pub(crate) trait CaptureLadder: Send {
    /// The best source available now, `None` when there is none, and why each better rung was not available.
    fn climb(&mut self, screen: &ScreenAccess, client: Option<&Client>) -> (Option<Arc<dyn Source>>, String);
}

/// What a scenario's video will be.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct VideoPlan {
    pub(crate) codec: VideoCodec,
    /// Why there is no video, when there is none.
    pub(crate) reason: String,
    /// A video fed with the recorder's own frames, as opposed to one ffmpeg captures by itself.
    pub(crate) raw: bool,
}

impl VideoPlan {
    fn none(reason: impl Into<String>) -> Self {
        Self {
            codec: VideoCodec::None,
            reason: reason.into(),
            raw: false,
        }
    }

    const fn h264(raw: bool) -> Self {
        Self {
            codec: VideoCodec::H264,
            reason: String::new(),
            raw,
        }
    }
}

/// Decides and starts the videos.
pub(crate) trait Videographer: Send {
    /// Decides the video of a scenario captured from `source`. `screen` is the hello's, which a video recorded off
    /// the screen itself needs.
    fn plan(&self, source: Option<&dyn Source>, screen: bool) -> VideoPlan;

    /// Starts a video into `dir`. `first` is the frame a raw video starts with, `None` otherwise.
    fn start(&self, dir: &Path, plan: &VideoPlan, first: Option<&Frame>) -> anyhow::Result<Box<dyn Recording>>;
}

/// How a person running a lane on their own machine lets the recorder read its screen.
pub(crate) const SCREEN_OPT_IN: &str = "-Dair.flow.trace.screen=on";

/// How long a display that failed to open is not tried again, in milliseconds.
const FAILED_DISPLAY_RETRY_MS: i64 = 60_000;

/// Opens an X display, such as `:88`.
type OpenX11 = Box<dyn Fn(&str) -> anyhow::Result<Arc<dyn Source>> + Send>;

/// The ladder [CaptureSource] declares: X11, then the IDE's paint, then nothing.
///
/// It remembers the display it last failed to open. Off X11 the ladder is climbed at every scenario, and a display
/// that accepts a connection and then never answers costs the whole open timeout inside the scenario ack; one such
/// timeout a minute is enough to learn that. Not for good, though: the climb after a stall is one open, at one
/// scenario's start, and a server still stalled then would otherwise leave every later scenario of the iteration
/// without its video.
pub(crate) struct SystemLadder {
    clock: Clock,
    /// The recorder's own operating system, which stands in for the IDE's before the facts name it.
    os: String,
    open_x11: OpenX11,
    failed: Option<FailedDisplay>,
}

struct FailedDisplay {
    display: String,
    at_ms: i64,
    failure: String,
}

impl SystemLadder {
    pub(crate) fn new(diagnostics: Diagnostics, clock: Clock, os: String) -> Self {
        Self {
            clock,
            os,
            open_x11: Box::new(move |display| open_x11(display, &diagnostics)),
            failed: None,
        }
    }

    /// Opens the X server the IDE draws on, or answers why there is none to read.
    ///
    /// Only on Linux, whatever `DISPLAY` says. AWT draws to X there and nowhere else: a Mac with XQuartz has a
    /// `DISPLAY` in every GUI process's environment, and reading it would record XQuartz's empty root window while
    /// the IDE draws through Cocoa.
    fn open_x11(&mut self, screen: &ScreenAccess) -> Result<Arc<dyn Source>, String> {
        let os = if screen.os.is_empty() { &self.os } else { &screen.os };
        let display = if screen.display.is_empty() {
            std::env::var("DISPLAY").unwrap_or_default()
        } else {
            screen.display.clone()
        };
        if !screen.allowed {
            return Err(format!("the lane did not let the recorder read the screen ({SCREEN_OPT_IN} does)"));
        }
        if os != "linux" {
            return Err(format!("the IDE draws through the window system of {os}, not through X"));
        }
        if display.is_empty() {
            return Err("neither the IDE nor the recorder has a DISPLAY".to_owned());
        }
        if let Some(failed) = &self.failed
            && failed.display == display
            && self.recently_failed(failed.at_ms)
        {
            return Err(failed.failure.clone());
        }
        match (self.open_x11)(&display) {
            Ok(source) => {
                self.failed = None;
                Ok(source)
            }
            Err(error) => {
                let failure = format!("{error:#}");
                self.failed = Some(FailedDisplay {
                    display,
                    at_ms: unix_ms((self.clock)()),
                    failure: failure.clone(),
                });
                Err(failure)
            }
        }
    }

    /// Whether a failure at `at_ms` is younger than the retry interval. A failure the clock has since stepped back
    /// past counts as old, since how long ago it was is then unknown.
    fn recently_failed(&self, at_ms: i64) -> bool {
        let elapsed = unix_ms((self.clock)()) - at_ms;
        (0..FAILED_DISPLAY_RETRY_MS).contains(&elapsed)
    }
}

impl CaptureLadder for SystemLadder {
    fn climb(&mut self, screen: &ScreenAccess, client: Option<&Client>) -> (Option<Arc<dyn Source>>, String) {
        let reason = match self.open_x11(screen) {
            Ok(source) => return (Some(source), String::new()),
            Err(reason) => format!("no X11: {reason}"),
        };
        match client {
            Some(client) => (Some(Arc::new(IdePaint::new(client.clone(), self.clock.clone()))), reason),
            None => (None, format!("{reason}; no IDE paint: the lane named no bridge")),
        }
    }
}

#[cfg(target_os = "linux")]
fn open_x11(display: &str, diagnostics: &Diagnostics) -> anyhow::Result<Arc<dyn Source>> {
    let source = crate::capture::x11::X11Source::open(display)?;
    if let Some(plain) = source.plain_reason() {
        say!(diagnostics, "X11 frames come through the plain GetImage: {plain}");
    }
    Ok(Arc::new(source))
}

#[cfg(not(target_os = "linux"))]
fn open_x11(display: &str, _diagnostics: &Diagnostics) -> anyhow::Result<Arc<dyn Source>> {
    anyhow::bail!(
        "cannot read the X display {display}: this recorder is built for {}, where it has no X11 client",
        std::env::consts::OS
    )
}

/// Records with the recorder's own encoder off X11, and with the machine's ffmpeg off a Mac's screen.
pub(crate) struct SystemVideographer {
    /// The ffmpeg, or why there is none to record a screen with.
    ffmpeg: Result<PathBuf, String>,
    os: String,
    clock: Clock,
}

impl SystemVideographer {
    pub(crate) fn new(os: String, clock: Clock) -> Self {
        Self {
            ffmpeg: video::ffmpeg::find().map_err(|error| error.to_string()),
            os,
            clock,
        }
    }
}

impl Videographer for SystemVideographer {
    fn plan(&self, source: Option<&dyn Source>, screen: bool) -> VideoPlan {
        let Some(source) = source else {
            return VideoPlan::none("nothing is captured");
        };
        match source.kind() {
            CaptureSource::X11 => VideoPlan::h264(true),
            CaptureSource::IdePaint if self.os == "darwin" && !screen => VideoPlan::none(format!(
                "ffmpeg would record this Mac's whole screen, which the lane did not allow ({SCREEN_OPT_IN} does)"
            )),
            CaptureSource::IdePaint if self.os == "darwin" => match &self.ffmpeg {
                Ok(_) => VideoPlan::h264(false),
                Err(missing) => VideoPlan::none(missing.clone()),
            },
            _ => VideoPlan::none(format!(
                "the IDE paints only at snapshots, and {} has no screen recording of its own",
                self.os
            )),
        }
    }

    fn start(&self, dir: &Path, plan: &VideoPlan, first: Option<&Frame>) -> anyhow::Result<Box<dyn Recording>> {
        if !plan.raw {
            let ffmpeg = self.ffmpeg.as_ref().map_err(|missing| anyhow::anyhow!("{missing}"))?;
            return Ok(Box::new(video::ffmpeg::start_screen(ffmpeg, dir, self.clock.clone())?));
        }
        let Some(first) = first else {
            anyhow::bail!("a raw video started without its first frame");
        };
        start_raw(dir, first)
    }
}

#[cfg(unix)]
fn start_raw(dir: &Path, first: &Frame) -> anyhow::Result<Box<dyn Recording>> {
    Ok(Box::new(video::h264::H264Recording::start(dir, first.width, first.height)?))
}

#[cfg(not(unix))]
fn start_raw(_dir: &Path, _first: &Frame) -> anyhow::Result<Box<dyn Recording>> {
    anyhow::bail!("this recorder is built for {}, where it records no raw video", std::env::consts::OS)
}
