use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant, SystemTime};

use anyhow::bail;
use avl_trace::protocol::CaptureSource;

use super::*;
use crate::lock;

/// Answers a frame stamped with the real clock, and fails every grab while `failing` is set.
#[derive(Default)]
pub(crate) struct CountingScreen {
    pub(crate) failing: AtomicBool,
}

impl Source for CountingScreen {
    fn kind(&self) -> CaptureSource {
        CaptureSource::X11
    }

    fn grab(&self, _deadline: Instant, frame: &mut Frame) -> anyhow::Result<()> {
        if self.failing.load(Ordering::SeqCst) {
            bail!("the X server is busy");
        }
        frame.resize(8, 4);
        frame.at = SystemTime::now();
        Ok(())
    }
}

/// Takes as long as it is told to for the one frame it is told to, and keeps every frame's time.
#[derive(Clone, Default)]
struct SlowSink {
    frames: Arc<Mutex<Vec<SystemTime>>>,
    slow_at: usize,
    slowness: Duration,
}

impl SlowSink {
    fn times(&self) -> Vec<SystemTime> {
        lock(&self.frames).clone()
    }
}

impl FrameSink for SlowSink {
    fn write_frame(&mut self, frame: &Frame) -> anyhow::Result<()> {
        let slow = {
            let mut frames = lock(&self.frames);
            frames.push(frame.at);
            frames.len() == self.slow_at
        };
        if slow {
            thread::sleep(self.slowness);
        }
        Ok(())
    }
}

fn first_frame(at: SystemTime) -> Frame {
    let mut first = Frame::default();
    first.resize(8, 4);
    first.at = at;
    first
}

fn collecting() -> (Report, Arc<Mutex<Vec<String>>>) {
    let reports = Arc::new(Mutex::new(Vec::new()));
    let seen = reports.clone();
    let report: Report = Box::new(move |source, error| lock(&seen).push(format!("{source}: {error:#}")));
    (report, reports)
}

fn wait_for(sink: &SlowSink, frames: usize, interval: Duration) {
    let deadline = Instant::now() + Duration::from_secs(10);
    while sink.times().len() < frames && Instant::now() < deadline {
        thread::sleep(interval);
    }
}

/// An encoder that stalls for many ticks must not make the video play faster than the scenario ran: the missed
/// ticks are written as the picture that was on screen, with that picture's capture time.
#[test]
fn missed_ticks_are_padded_with_the_previous_picture() {
    let interval = Duration::from_millis(20);
    let sink = SlowSink {
        slow_at: 3,
        slowness: interval * 7,
        ..SlowSink::default()
    };
    let (report, reports) = collecting();
    let frames = FrameLoop::start(
        Arc::new(CountingScreen::default()),
        Box::new(sink.clone()),
        first_frame(SystemTime::now()),
        interval,
        report,
    );
    wait_for(&sink, 12, interval);
    frames.halt();
    frames.wait();
    let times = sink.times();
    assert!(times.is_sorted(), "the frames are out of order: {times:?}");
    // The ticks the stall swallowed come back as repeats of one picture, with that picture's capture time: the
    // third frame's when the stall followed its grab, the next grab's when a loaded machine made the stalled write
    // itself a repeat.
    let longest = times.chunk_by(|a, b| a == b).map(<[SystemTime]>::len).max().unwrap_or(0);
    assert!(
        longest > 3,
        "a stall of 7 ticks was made up with {} repeats: {times:?}",
        longest.saturating_sub(1)
    );
    assert!(lock(&reports).is_empty(), "a healthy loop reported {:?}", lock(&reports));
}

/// A failing grab writes the last good picture and says so once, not ten times a second.
#[test]
fn a_failing_grab_repeats_the_last_picture_and_reports_once() {
    let interval = Duration::from_millis(10);
    let screen = CountingScreen::default();
    screen.failing.store(true, Ordering::SeqCst);
    let sink = SlowSink::default();
    let (report, reports) = collecting();
    let at = SystemTime::UNIX_EPOCH + Duration::from_secs(1);
    let frames = FrameLoop::start(Arc::new(screen), Box::new(sink.clone()), first_frame(at), interval, report);
    wait_for(&sink, 5, interval);
    frames.halt();
    let counts = frames.wait();
    assert!(sink.times().iter().all(|time| *time == at), "a failed grab wrote a new frame");
    assert!(counts.written >= 5, "{counts:?}");
    assert_eq!(*lock(&reports), ["x11: the X server is busy"]);
}
