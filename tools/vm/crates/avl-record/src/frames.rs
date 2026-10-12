//! The raw video's frame loop.

use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;
use std::time::{Duration, Instant};

use crossbeam_channel::{Sender, bounded, select, tick};

use crate::capture::{Detector, Frame, Source};
use crate::scenario::ERROR_SOURCE_VIDEO;
use crate::video::FrameSink;

#[cfg(test)]
mod tests;

/// Bounds how many ticks one late tick makes up for. A loop that fell further behind than this was stopped rather
/// than slow (a suspended guest, a debugger), and a video that pads that gap with one still frame only misleads.
const MAX_PADDED_TICKS: u128 = 20;

/// Hears the loop's first grab failure and its first write failure, by the failing part's name.
pub(crate) type Report = Box<dyn Fn(&str, &anyhow::Error) + Send>;

/// Feeds a raw video one frame per tick while a scenario is open.
///
/// Every tick writes a frame, the previous one again when a grab fails, so the video's frame count follows the wall
/// clock and its playback is close to real time. A tick the loop missed because the encoder or the grab was slow is
/// made up for with the previous picture too. Either way a repeated frame keeps the capture time of the picture it
/// repeats, which is what makes the index honest: the viewer seeks to the last frame read at or before a moment, and
/// a repeat is that frame.
pub(crate) struct FrameLoop {
    stop: Sender<()>,
    stopping: Arc<AtomicBool>,
    thread: thread::JoinHandle<Counts>,
}

/// How many frames the loop wrote, and how many of them were a changed screen.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) struct Counts {
    pub(crate) written: usize,
    pub(crate) changed: usize,
}

impl FrameLoop {
    /// Starts the loop with `first`, which it writes before its first tick. After a write failure the loop ends,
    /// since the encoder is gone.
    ///
    /// The first frame is written here rather than by the caller, because the caller is the command loop inside a
    /// scenario ack: an encoder that never takes its input would block that write for good, and with it the ack, the
    /// end of the input and the recorder's exit. On the loop's thread the write is bounded like every later one, by
    /// the encode of one frame.
    pub(crate) fn start(source: Arc<dyn Source>, sink: Box<dyn FrameSink>, first: Frame, interval: Duration, report: Report) -> Self {
        let (stop, stopped) = bounded(1);
        let stopping = Arc::new(AtomicBool::new(false));
        let thread = {
            let stopping = stopping.clone();
            thread::spawn(move || {
                let mut run = Run {
                    sink,
                    stopping,
                    report,
                    counts: Counts::default(),
                };
                run.feed(&*source, first, interval, &stopped);
                run.counts
            })
        };
        Self { stop, stopping, thread }
    }

    /// Asks the loop to stop. It does not wait: a write in progress ends after the encode of its one frame, the
    /// encoder's stop refuses every later frame, and [FrameLoop::wait] follows that.
    pub(crate) fn halt(&self) {
        if !self.stopping.swap(true, Ordering::SeqCst) {
            let _ = self.stop.try_send(());
        }
    }

    /// Waits for the loop to end.
    pub(crate) fn wait(self) -> Counts {
        self.thread.join().unwrap_or_default()
    }
}

struct Run {
    sink: Box<dyn FrameSink>,
    stopping: Arc<AtomicBool>,
    report: Report,
    counts: Counts,
}

impl Run {
    fn feed(&mut self, source: &dyn Source, first: Frame, interval: Duration, stopped: &crossbeam_channel::Receiver<()>) {
        let (mut current, mut next) = (first, Frame::default());
        let mut detector = Detector::new();
        detector.update(&current);
        let mut grab_failed = false;
        if !self.write(&current) {
            return;
        }
        let ticker = tick(interval);
        let mut previous_tick = Instant::now();
        loop {
            select! {
                recv(stopped) -> _ => return,
                recv(ticker) -> _ => {}
            }
            // Measured from the clock rather than read off the tick: a ticker hands a slow receiver one tick and
            // drops the rest, so the tick it delivers does not say how many were dropped.
            let now = Instant::now();
            let late = (now - previous_tick + interval / 2).as_nanos() / interval.as_nanos().max(1);
            previous_tick = now;
            for _ in 1..late.min(MAX_PADDED_TICKS + 1) {
                if !self.write(&current) {
                    return;
                }
            }
            match source.grab(now + interval * 10, &mut next) {
                Ok(()) => {
                    if detector.update(&next).is_some() {
                        self.counts.changed += 1;
                    }
                    std::mem::swap(&mut current, &mut next);
                }
                Err(error) => {
                    if !grab_failed && !self.stopping.load(Ordering::SeqCst) {
                        grab_failed = true;
                        (self.report)(source.kind().as_str(), &error);
                    }
                }
            }
            if !self.write(&current) {
                return;
            }
        }
    }

    fn write(&mut self, frame: &Frame) -> bool {
        match self.sink.write_frame(frame) {
            Ok(()) => {
                self.counts.written += 1;
                true
            }
            Err(error) => {
                if !self.stopping.load(Ordering::SeqCst) {
                    (self.report)(ERROR_SOURCE_VIDEO, &error);
                }
                false
            }
        }
    }
}
