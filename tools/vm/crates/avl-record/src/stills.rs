//! A snapshot's picture, encoded off the lane's path.
//!
//! # The encoding
//!
//! A scenario without a video gets lossless pictures, because a still is then its only picture of the screen. A lossy
//! picture of antialiased text is not what the screen showed. A scenario with a video gets lossy pictures at quality
//! [LOSSY_QUALITY], because its video also shows each moment. The scenario picks the [StillEncoding] after it starts
//! the video, and the manifest names it. A lossy encode that fails or panics leaves that one still lossless.
//!
//! A lossless still is WebP from `image-webp`, in pure Rust. A lossy still is AVIF from libaom, 4:4:4 at speed 6. It is
//! less than half the size of the lossy WebP that it replaces, at a better quality. Its lossless fallback
//! is AVIF too, so the file keeps the name and the format that its snapshot record already gave it. Every browser that
//! the viewer runs in decodes both formats natively. Bazel builds libaom into the static musl guest binary.
//!
//! # Off the lane's path
//!
//! A snapshot is taken while the lane waits for its ack, but only the grab has to happen then: the picture is the
//! moment. The encoding does not, so [Writer] takes the grabbed frame and encodes it on threads of its own, and the
//! ack goes back as soon as the frame is queued. The manifest is written only once the writer has drained, so a
//! finished bundle never names a picture that is not on disk.
//!
//! A lossy picture of a 1920x1200 screen costs about 0.36 s of CPU on an M1 Ultra core, and a lossless WebP one about
//! 10 ms. So a burst of changed screens outruns one thread. The writer runs [worker_count] encoders over one queue: half the cores, at most
//! [MAX_WORKERS]. The other half stays with the frame loop of the video and with the IDE. A name is fixed when the
//! still is submitted, so the order in which the workers finish does not matter.
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
//!
//! # Attachments
//!
//! A scenario with a video runs a second writer for the PNG attachments of the lane, which become lossy
//! `attach/NNNN.avif` pictures. The rules are the same: off the lane's path, the lossless fallback, and the previous
//! picture's file for the same picture. The writer is a second one, so a still and an attachment picture never name
//! the file of the other.

use std::any::Any;
use std::fmt;
use std::iter;
use std::panic::{self, AssertUnwindSafe};
use std::path::Path;
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Instant;

use anyhow::{Context, bail};
use avl_trace::bundle::{StillEncoding, bundle_file};
use crossbeam_channel::{Receiver, SendTimeoutError, Sender, TrySendError, bounded};

#[cfg(unix)]
use crate::avif;
use crate::bundle::write_file_atomically;
use crate::capture::{Detector, Frame};
use crate::lock;

#[cfg(test)]
mod tests;

/// The quality of a lossy picture, on the scale of 0 to 100 that `avifenc -q` takes. At 50 the step pictures of a CI
/// build take 25% of the size of their PNG and 41% of the lossy WebP that they replace. Their SSIMULACRA2 is better in
/// the mean, in the worst tenth and in the worst picture.
#[cfg(unix)]
pub(crate) const LOSSY_QUALITY: u8 = 50;

/// What one encode of a still writes.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub(crate) enum Picture {
    /// Every still of a scenario without a video.
    LosslessWebp,
    /// A still of a scenario with a video.
    LossyAvif,
    /// A still of a scenario with a video whose lossy encode failed.
    LosslessAvif,
}

impl Picture {
    /// The picture of a still in the encoding, and the picture that replaces it when its encode fails.
    const fn of(encoding: StillEncoding) -> (Self, Option<Self>) {
        match encoding {
            StillEncoding::Lossless => (Self::LosslessWebp, None),
            StillEncoding::Lossy => (Self::LossyAvif, Some(Self::LosslessAvif)),
        }
    }
}

impl fmt::Display for Picture {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(match self {
            Self::LosslessWebp => "lossless WebP",
            Self::LossyAvif => "lossy AVIF",
            Self::LosslessAvif => "lossless AVIF",
        })
    }
}

/// The frame as a picture file.
pub(crate) fn encode(frame: &Frame, picture: Picture) -> anyhow::Result<Vec<u8>> {
    let width = u32::try_from(frame.width).context("the frame is too wide for a picture")?;
    let height = u32::try_from(frame.height).context("the frame is too tall for a picture")?;
    let rgb = frame.to_rgb();
    match picture {
        Picture::LosslessWebp => {
            let mut document = Vec::new();
            image_webp::WebPEncoder::new(&mut document).encode(&rgb, width, height, image_webp::ColorType::Rgb8)?;
            Ok(document)
        }
        #[cfg(unix)]
        Picture::LossyAvif => avif::encode(&rgb, width, height, avif::Loss::Lossy { quality: LOSSY_QUALITY }),
        #[cfg(unix)]
        Picture::LosslessAvif => avif::encode(&rgb, width, height, avif::Loss::Lossless),
        #[cfg(not(unix))]
        Picture::LossyAvif | Picture::LosslessAvif => bail!("this build of the recorder has no AVIF encoder"),
    }
}

/// Encodes a frame as a picture file: [encode], or a stand-in that fails or waits in a test.
type EncodeFn = dyn Fn(&Frame, Picture) -> anyhow::Result<Vec<u8>> + Send + Sync;

/// The encoder that every worker of a writer shares.
type Encoder = Arc<EncodeFn>;

/// How many grabbed frames may wait for the encoders. Four 1080p frames are 33 MB. Each worker also holds a frame while
/// it encodes it, with the RGB copy of the frame, its planes and the buffers of libaom: about 50 MB at 1920x1200. A
/// scenario that snaps faster than the encoders keep up with waits at [Writer::submit] instead of growing without
/// bound. An encoder of 1.2 s per picture, libaom 3.11 at speed 6, overflowed the queue of a lane on a loaded host.
pub(crate) const QUEUE_DEPTH: usize = 4;

/// The most encoders one writer runs.
pub(crate) const MAX_WORKERS: usize = 4;

/// How many encoders a writer runs: half the cores of this machine, at least 1 and at most [MAX_WORKERS].
pub(crate) fn worker_count() -> usize {
    thread::available_parallelism().map_or(1, |cores| (cores.get() / 2).clamp(1, MAX_WORKERS))
}

/// Hears a problem of a still, by its name, on the thread of an encoder: a still that could not be written, or a lossy
/// still that was written lossless.
pub(crate) type Failed = Box<dyn Fn(&str, &anyhow::Error) + Send + Sync>;

struct Job {
    name: String,
    frame: Frame,
}

/// What the submitting thread and the encoders share.
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

/// What [Writer::close] answers.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub(crate) struct Closed {
    /// How many files the encoders wrote.
    pub(crate) files: usize,
    /// How many stills named the previous still's file instead of a file of their own.
    pub(crate) repeats: usize,
    /// How many encoders ended with a panic. The stills that they held are not on disk.
    pub(crate) panicked: usize,
}

/// Writes the stills of one bundle. The names follow the order of submission, and the files are written in any order.
pub(crate) struct Writer {
    jobs: Sender<Job>,
    encoders: Vec<thread::JoinHandle<()>>,
    /// The queue's far end when no encoder drains it, which only a test wants.
    #[cfg(test)]
    _parked: Option<Receiver<Job>>,
    state: Arc<Mutex<State>>,
}

impl Writer {
    /// Starts a writer that encodes the stills of the bundle in `dir` as `encoding` says. `failed` hears every still
    /// that could not be written, and every lossy still that was written lossless.
    pub(crate) fn new(dir: &Path, encoding: StillEncoding, failed: Failed) -> Self {
        Self::with_encoder(dir, encoding, Arc::new(encode), worker_count(), failed)
    }

    /// [Writer::new] with another encoder, which a test makes fail or wait, and `workers` encoders.
    fn with_encoder(dir: &Path, encoding: StillEncoding, encode: Encoder, workers: usize, failed: Failed) -> Self {
        let (jobs, queued) = bounded(QUEUE_DEPTH);
        let state = Arc::new(Mutex::new(State {
            detector: Detector::new(),
            last: None,
            files: 0,
            repeats: 0,
        }));
        let failed = Arc::new(failed);
        let encoders = iter::repeat_n(encode, workers.max(1))
            .map(|encode| {
                let (dir, queued, state, failed) = (dir.to_owned(), queued.clone(), state.clone(), failed.clone());
                thread::spawn(move || encode_queue(&dir, encoding, &*encode, &queued, &state, &failed))
            })
            .collect();
        Self {
            jobs,
            encoders,
            #[cfg(test)]
            _parked: None,
            state,
        }
    }

    /// A writer whose one encoder ends with a panic at its first still: the encoder fails, and the report of the failure
    /// panics.
    #[cfg(test)]
    pub(crate) fn panicking(dir: &Path) -> Self {
        Self::with_encoder(
            dir,
            StillEncoding::Lossless,
            Arc::new(|_: &Frame, _: Picture| bail!("the encoder broke")),
            1,
            Box::new(|name, _| panic!("the report of {name} broke")),
        )
    }

    /// A writer whose queue nothing drains, so it holds what was submitted.
    #[cfg(test)]
    pub(crate) fn parked() -> Self {
        let (jobs, queued) = bounded(QUEUE_DEPTH);
        Self {
            jobs,
            encoders: Vec::new(),
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
                bail!("the still encoders are gone");
            }
        };
        match self.jobs.send_timeout(job, deadline.saturating_duration_since(Instant::now())) {
            Ok(()) => Ok(name),
            Err(error) => {
                lock(&self.state).forget(&name);
                match error {
                    SendTimeoutError::Timeout(_) => {
                        bail!("the still encoders are {QUEUE_DEPTH} pictures behind and the budget of the command is spent")
                    }
                    SendTimeoutError::Disconnected(_) => bail!("the still encoders are gone"),
                }
            }
        }
    }

    /// Waits for every queued still to be written and for every encoder to end.
    pub(crate) fn close(self) -> Closed {
        let Self { jobs, encoders, state, .. } = self;
        drop(jobs);
        let panicked = encoders.into_iter().filter_map(|encoder| encoder.join().err()).count();
        let state = lock(&state);
        Closed {
            files: state.files,
            repeats: state.repeats,
            panicked,
        }
    }
}

/// Encodes the stills of the queue until it is empty and closed. Each encoder of a writer runs one.
fn encode_queue(dir: &Path, encoding: StillEncoding, encode: &EncodeFn, queued: &Receiver<Job>, state: &Mutex<State>, failed: &Failed) {
    for job in queued {
        match write(dir, encoding, encode, &job, failed) {
            Ok(()) => lock(state).files += 1,
            Err(error) => {
                lock(state).forget(&job.name);
                failed(&job.name, &error);
            }
        }
    }
}

/// Encodes one still and writes its file. A lossy encode that fails or panics costs the still its loss, not its file:
/// the still is written lossless in the same format, and then `failed` hears the lossy failure. When the lossless still
/// cannot be written either, the error names both causes.
fn write(dir: &Path, encoding: StillEncoding, encode: &EncodeFn, job: &Job, failed: &Failed) -> anyhow::Result<()> {
    let (picture, fallback) = Picture::of(encoding);
    let first = match guarded(encode, &job.frame, picture) {
        Ok(content) => return store(dir, job, &content),
        Err(error) => error,
    };
    let Some(fallback) = fallback else {
        return Err(first);
    };
    match guarded(encode, &job.frame, fallback).and_then(|content| store(dir, job, &content)) {
        Ok(()) => {
            failed(&job.name, &first.context(format!("the still is {fallback} instead")));
            Ok(())
        }
        Err(second) => bail!("{first:#}; {second:#}"),
    }
}

fn store(dir: &Path, job: &Job, content: &[u8]) -> anyhow::Result<()> {
    write_file_atomically(&bundle_file(dir, &job.name), content)?;
    Ok(())
}

/// Runs the encoder and turns its panic into an error. A bug of an encoder then costs one still, and not the encoder's
/// thread with every still queued behind it.
fn guarded(encode: &EncodeFn, frame: &Frame, picture: Picture) -> anyhow::Result<Vec<u8>> {
    match panic::catch_unwind(AssertUnwindSafe(|| encode(frame, picture))) {
        Ok(encoded) => encoded.with_context(|| format!("the {picture} encoder")),
        Err(panic) => bail!("the {picture} encoder panicked: {}", panic_message(panic.as_ref())),
    }
}

/// The text of a panic, which `panic!` carries as a `&str` or a `String`.
fn panic_message(panic: &(dyn Any + Send)) -> &str {
    panic
        .downcast_ref::<&str>()
        .copied()
        .or_else(|| panic.downcast_ref::<String>().map(String::as_str))
        .unwrap_or("no message")
}
