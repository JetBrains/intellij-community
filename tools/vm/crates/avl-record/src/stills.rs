//! A snapshot's picture: lossless WebP, encoded off the lane's path.
//!
//! # The encoder
//!
//! Lossless, because a still is evidence: a check failed on what the screen showed, and a lossy picture of
//! antialiased text is not what the screen showed. WebP, because every browser the viewer runs in decodes it natively
//! and it is smaller than PNG on UI content. Pure Rust (`image-webp`), because the recorder cross-builds to a static
//! musl guest binary.
//!
//! # Off the lane's path
//!
//! A snapshot is taken while the lane waits for its ack, but only the grab has to happen then: the picture is the
//! moment. The encoding does not, so [Writer] takes the grabbed frame and encodes it on a thread of its own, and the
//! ack goes back as soon as the frame is queued. The manifest is written only once the writer has drained, so a
//! finished bundle never names a picture that is not on disk.
//!
//! # One file per screen
//!
//! Consecutive stills of an unchanged screen are common, an assertion's check right after its boundary for one. Such
//! a still gets no file of its own: [Writer::submit] answers the previous still's file, and the snapshot record names
//! that. The decision is made before the ack, on the caller's thread, because the record names the file at once. It
//! costs one hash pass over the frame, the pass the frame loop makes at every tick.
//!
//! Only the previous still is compared, not every earlier one. So the files follow the screen's changes in order, and
//! the last file by name is always the last snapshot's picture, which a bundle's thumbnail relies on.

use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Instant;

use anyhow::{Context, bail};
use avl_trace::bundle::bundle_file;
use crossbeam_channel::{Receiver, SendTimeoutError, Sender, TrySendError, bounded};

use crate::bundle::write_file_atomically;
use crate::capture::{Detector, Frame};
use crate::lock;

#[cfg(test)]
mod tests;

/// The frame as a lossless WebP file.
pub(crate) fn encode(frame: &Frame) -> anyhow::Result<Vec<u8>> {
    let width = u32::try_from(frame.width).context("the frame is too wide for WebP")?;
    let height = u32::try_from(frame.height).context("the frame is too tall for WebP")?;
    let mut document = Vec::new();
    image_webp::WebPEncoder::new(&mut document).encode(&frame.to_rgb(), width, height, image_webp::ColorType::Rgb8)?;
    Ok(document)
}

/// How many grabbed frames may wait for the encoder. Four 1080p frames are 33 MB; a scenario that snaps faster than
/// the encoder keeps up with waits at [Writer::submit] instead of growing without bound.
pub(crate) const QUEUE_DEPTH: usize = 4;

/// Hears a still that could not be written, by its name, on the encoder's thread.
pub(crate) type Failed = Box<dyn Fn(&str, &anyhow::Error) + Send>;

struct Job {
    name: String,
    frame: Frame,
}

/// What the submitting thread and the encoder share.
struct State {
    detector: Detector,
    /// The file of the previous still, which a still of the same screen names instead of a file of its own. `None`
    /// before the first still, and after the previous still could not be queued or written.
    last: Option<String>,
    files: usize,
    repeats: usize,
}

impl State {
    /// Stops the next still of the same screen from naming a file that will not be on disk. A snapshot that already
    /// named it has no picture, and the failure the writer reports names the file.
    fn forget(&mut self, name: &str) {
        if self.last.as_deref() == Some(name) {
            self.last = None;
        }
    }
}

/// Writes the stills of one bundle, in the order they were submitted.
pub(crate) struct Writer {
    jobs: Sender<Job>,
    encoder: Option<thread::JoinHandle<()>>,
    /// The queue's far end when no encoder drains it, which only a test wants.
    #[cfg(test)]
    _parked: Option<Receiver<Job>>,
    state: Arc<Mutex<State>>,
}

impl Writer {
    /// Starts a writer for the bundle in `dir`. `failed` hears every still that could not be written.
    pub(crate) fn new(dir: &Path, failed: Failed) -> Self {
        let (jobs, queued) = bounded(QUEUE_DEPTH);
        let state = Arc::new(Mutex::new(State {
            detector: Detector::new(),
            last: None,
            files: 0,
            repeats: 0,
        }));
        let encoder = {
            let dir = dir.to_owned();
            let state = state.clone();
            thread::spawn(move || encode_queue(&dir, &queued, &state, &failed))
        };
        Self {
            jobs,
            encoder: Some(encoder),
            #[cfg(test)]
            _parked: None,
            state,
        }
    }

    /// A writer whose queue nothing drains, so it holds what was submitted.
    #[cfg(test)]
    fn parked() -> Self {
        let (jobs, queued) = bounded(QUEUE_DEPTH);
        Self {
            jobs,
            encoder: None,
            _parked: Some(queued),
            state: Arc::new(Mutex::new(State {
                detector: Detector::new(),
                last: None,
                files: 0,
                repeats: 0,
            })),
        }
    }

    /// Answers the file that holds the frame's picture, a bundle path with `/`.
    ///
    /// When the screen changed since the previous still, the frame is queued to be written at `name`, and `name` is
    /// the answer. When it did not, nothing is queued, and the answer is the previous still's file.
    ///
    /// Queueing waits for room, and gives up at `deadline`. A free slot is taken before the deadline is looked at: a
    /// grab and a tree read that used up the snapshot's budget leave it passed, and a picture the queue had room for
    /// is still kept.
    pub(crate) fn submit(&self, deadline: Instant, name: String, frame: Frame) -> anyhow::Result<String> {
        {
            let mut state = lock(&self.state);
            let changed = state.detector.update(&frame).is_some();
            if !changed && let Some(previous) = state.last.clone() {
                state.repeats += 1;
                return Ok(previous);
            }
            state.last = Some(name.clone());
        }
        let job = Job { name: name.clone(), frame };
        let job = match self.jobs.try_send(job) {
            Ok(()) => return Ok(name),
            Err(TrySendError::Full(job)) => job,
            Err(TrySendError::Disconnected(_)) => {
                lock(&self.state).forget(&name);
                bail!("the still encoder is gone");
            }
        };
        match self.jobs.send_timeout(job, deadline.saturating_duration_since(Instant::now())) {
            Ok(()) => Ok(name),
            Err(error) => {
                lock(&self.state).forget(&name);
                match error {
                    SendTimeoutError::Timeout(_) => {
                        bail!("the still encoder is {QUEUE_DEPTH} pictures behind and the snapshot's budget is spent")
                    }
                    SendTimeoutError::Disconnected(_) => bail!("the still encoder is gone"),
                }
            }
        }
    }

    /// Waits for every queued still to be written, and answers how many files were written and how many stills
    /// named the previous still's file instead.
    pub(crate) fn close(self) -> (usize, usize) {
        let Self { jobs, encoder, state, .. } = self;
        drop(jobs);
        if let Some(encoder) = encoder {
            let _ = encoder.join();
        }
        let state = lock(&state);
        (state.files, state.repeats)
    }
}

fn encode_queue(dir: &Path, queued: &Receiver<Job>, state: &Mutex<State>, failed: &Failed) {
    for job in queued {
        match write(dir, &job) {
            Ok(()) => lock(state).files += 1,
            Err(error) => {
                lock(state).forget(&job.name);
                failed(&job.name, &error);
            }
        }
    }
}

fn write(dir: &Path, job: &Job) -> anyhow::Result<()> {
    let content = encode(&job.frame).context("the WebP encoder")?;
    let path: PathBuf = bundle_file(dir, &job.name);
    write_file_atomically(&path, &content)?;
    Ok(())
}
