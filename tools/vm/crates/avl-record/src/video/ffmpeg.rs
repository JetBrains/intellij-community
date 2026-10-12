//! The macOS screen recording: an ffmpeg that captures a Mac's main screen itself, through `avfoundation`.
//!
//! On a macOS host there is no X server, and the IDE paints only at snapshots, so the machine's ffmpeg records the
//! screen. The recorder never sees those frames, so the index is reconstructed from ffmpeg's progress reports and is
//! exact only to about one report interval.
//!
//! ffmpeg writes H.264 in the fragmented MP4 that the raw video's own muxer writes too: a keyframe every second, and
//! a fragment per keyframe, so an ffmpeg killed mid-scenario still leaves a video up to its last second.

use std::collections::VecDeque;
use std::ffi::OsStr;
use std::fs::Metadata;
use std::io::{BufRead, BufReader, Read, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, ChildStdin, Command, ExitStatus, Stdio};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

use anyhow::{Context, anyhow};
use avl_trace::bundle::{VIDEO_FILE, VideoIndex};
use crossbeam_channel::Receiver;

use super::{FRAME_INTERVAL, FRAME_RATE, FrameSink, Recording, Stopped, write_index};
use crate::{Clock, lock, unix_ms};

#[cfg(test)]
mod tests;

/// How long ffmpeg gets to finish once it was told to, before it is killed. Finishing is flushing the last fragment,
/// which takes well under a second; a process past this is stuck, and the fragments it already wrote are the video.
const STOP_GRACE: Duration = Duration::from_secs(5);

/// How long the stop waits for ffmpeg's pipes to end once ffmpeg itself has. They end with it, unless a grandchild
/// inherited one.
const PIPE_GRACE: Duration = Duration::from_secs(1);

/// How often the stop looks whether ffmpeg has exited.
const EXIT_POLL: Duration = Duration::from_millis(10);

/// Where ffmpeg is looked for after PATH. A lane started by Bazel runs with a PATH of the system directories only,
/// which misses Homebrew's.
const FALLBACK_DIRS: [&str; 3] = ["/usr/bin", "/usr/local/bin", "/opt/homebrew/bin"];

/// The ffmpeg to run: the one on PATH, else the first of the usual install locations.
pub(crate) fn find() -> anyhow::Result<PathBuf> {
    find_in(std::env::var_os("PATH").as_deref(), &FALLBACK_DIRS)
}

/// The first ffmpeg in `path`, in the form of `PATH`, then in `fallback`. An empty entry of `path` is skipped
/// rather than read as the working directory.
fn find_in(path: Option<&OsStr>, fallback: &[&str]) -> anyhow::Result<PathBuf> {
    path.iter()
        .flat_map(std::env::split_paths)
        .filter(|dir| !dir.as_os_str().is_empty())
        .chain(fallback.iter().map(PathBuf::from))
        .map(|dir| dir.join(PROGRAM))
        .find(|candidate| is_program(candidate))
        .ok_or_else(|| anyhow!("ffmpeg is not on PATH, nor in {}", fallback.join(", ")))
}

/// The file name of ffmpeg. Only a Mac runs it, but every host builds the search.
const PROGRAM: &str = if cfg!(windows) { "ffmpeg.exe" } else { "ffmpeg" };

/// Whether `path` is a file this process may run.
fn is_program(path: &Path) -> bool {
    std::fs::metadata(path).is_ok_and(|metadata| metadata.is_file() && is_runnable(&metadata))
}

#[cfg(unix)]
fn is_runnable(metadata: &Metadata) -> bool {
    std::os::unix::fs::PermissionsExt::mode(&metadata.permissions()) & 0o111 != 0
}

#[cfg(not(unix))]
const fn is_runnable(_metadata: &Metadata) -> bool {
    true
}

/// Waits at most `grace` for `child` to exit, and answers its status, `None` when it still runs or its status
/// cannot be read.
fn wait_within(child: &mut Child, grace: Duration) -> Option<ExitStatus> {
    let deadline = Instant::now() + grace;
    loop {
        match child.try_wait() {
            Ok(Some(status)) => return Some(status),
            Ok(None) if Instant::now() < deadline => thread::sleep(EXIT_POLL),
            Ok(None) | Err(_) => return None,
        }
    }
}

/// The output half of the argv: H.264 in fragmented MP4.
///
/// `veryfast` and CRF 26 are the budget of a machine that also runs the IDE under test, and a UI is flat enough that
/// CRF 26 keeps text legible. `-g 10` is a keyframe every second, so a seek in the viewer never decodes more than a
/// second of frames.
///
/// `-bf 0` is what makes the first frame play at 0. `veryfast` would use B-frames, whose reordering starts the decode
/// timestamps two frames before the presentation ones; a fragmented MP4 with `empty_moov` gets no edit list to hide
/// that, so ffmpeg shifts the timestamps up until the first decode time is 0 and the first frame plays 200 ms in. The
/// frame index says frame i plays at i/[FRAME_RATE], and every seek the viewer makes through it would then show the
/// screen two frames early. Without B-frames the two timestamps are one, and a UI video, mostly skips, loses almost
/// nothing by it.
fn encoding(output: &str) -> Vec<String> {
    let rate = FRAME_RATE.to_string();
    [
        "-c:v",
        "libx264",
        "-preset",
        "veryfast",
        "-crf",
        "26",
        "-g",
        &rate,
        "-bf",
        "0",
        "-pix_fmt",
        "yuv420p",
        "-movflags",
        "+frag_keyframe+empty_moov+default_base_moof",
        output,
    ]
    .map(str::to_owned)
    .into()
}

/// ffmpeg's argv, without the program, for an `avfoundation` recording of the main screen.
///
/// The device is named rather than numbered, since its index moves with every camera attached. The OS pointer and its
/// clicks are drawn. The lanes post synthetic input events, which move neither, so these pixels do not show a lane's
/// clicks. A Retina screen is scaled down to at most 1920 pixels across, the size an X11 recording has, so a host run
/// does not cost four times the encoder's time. `-progress pipe:1` is how the frame count reaches the index.
pub(crate) fn screen_args(output: &str) -> Vec<String> {
    let mut args: Vec<String> = [
        "-hide_banner",
        "-loglevel",
        "error",
        "-nostats",
        "-progress",
        "pipe:1",
        "-y",
        "-f",
        "avfoundation",
        "-capture_cursor",
        "1",
        "-capture_mouse_clicks",
        "1",
        "-framerate",
        "30",
        "-i",
        "Capture screen 0:none",
        "-vf",
        "scale='min(1920,iw)':-2",
        "-r",
        &FRAME_RATE.to_string(),
    ]
    .map(str::to_owned)
    .into();
    args.extend(encoding(output));
    args
}

/// Starts an `avfoundation` recording of the main screen into `dir`, whose progress reports `clock` dates.
pub(crate) fn start_screen(ffmpeg: &Path, dir: &Path, clock: Clock) -> anyhow::Result<FfmpegRecording> {
    let mut argv = vec![ffmpeg.as_os_str().to_string_lossy().into_owned()];
    argv.extend(screen_args(VIDEO_FILE));
    FfmpegRecording::start(&argv, dir, clock)
}

/// Keeps the last bytes ffmpeg wrote to its standard error, which is the one place it says why it failed.
#[derive(Default)]
struct Tail(VecDeque<u8>);

const TAIL_BYTES: usize = 2048;

impl Tail {
    fn push(&mut self, chunk: &[u8]) {
        self.0.extend(chunk);
        let excess = self.0.len().saturating_sub(TAIL_BYTES);
        self.0.drain(..excess);
    }

    fn text(&self) -> String {
        let bytes: Vec<u8> = self.0.iter().copied().collect();
        String::from_utf8_lossy(&bytes).split_whitespace().collect::<Vec<_>>().join(" ")
    }
}

/// Adds what ffmpeg said last to an error, since an exit status alone does not say why it failed.
fn with_tail(error: anyhow::Error, tail: &Mutex<Tail>) -> anyhow::Error {
    let said = lock(tail).text();
    if said.is_empty() {
        error
    } else {
        anyhow!("{error:#}; ffmpeg said: {said}")
    }
}

/// The index, from ffmpeg's progress reports.
#[derive(Default)]
struct Progress {
    frames: usize,
    first_frame_ms: i64,
}

/// The progress, and a channel that disconnects once its reader reached the end of ffmpeg's output.
struct ProgressReader {
    progress: Arc<Mutex<Progress>>,
    done: Receiver<()>,
}

/// One running ffmpeg that records the screen.
pub(crate) struct FfmpegRecording {
    dir: PathBuf,
    child: Child,
    /// ffmpeg's input, which takes its `q` at the stop.
    keys: Option<ChildStdin>,
    stopped: bool,
    tail: Arc<Mutex<Tail>>,
    /// Disconnects once standard error reached its end.
    tail_done: Receiver<()>,
    progress: ProgressReader,
    grace: Duration,
}

impl FfmpegRecording {
    /// Starts `argv` in `dir`. `clock` dates the progress reports.
    ///
    /// ffmpeg gets a process group of its own, so a Ctrl-C to the lane's terminal does not kill it before the
    /// recorder tells it to finish and the last fragment is flushed; the recorder still kills it after the grace.
    pub(crate) fn start(argv: &[String], dir: &Path, clock: Clock) -> anyhow::Result<Self> {
        let (program, args) = argv.split_first().context("an empty ffmpeg argv")?;
        let mut command = Command::new(program);
        command
            .args(args)
            .current_dir(dir)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());
        #[cfg(unix)]
        std::os::unix::process::CommandExt::process_group(&mut command, 0);
        let mut child = command.spawn().with_context(|| format!("cannot start {program}"))?;
        let keys = child.stdin.take().context("ffmpeg has no input pipe")?;
        let stdout = child.stdout.take().context("ffmpeg has no output pipe")?;

        let tail = Arc::new(Mutex::new(Tail::default()));
        let (tail_guard, tail_done) = crossbeam_channel::bounded::<()>(0);
        if let Some(mut stderr) = child.stderr.take() {
            let tail = tail.clone();
            thread::spawn(move || {
                let _guard = tail_guard;
                let mut chunk = [0; 1024];
                while let Ok(read @ 1..) = stderr.read(&mut chunk) {
                    lock(&tail).push(&chunk[..read]);
                }
            });
        }
        let progress = Arc::new(Mutex::new(Progress::default()));
        let (guard, done) = crossbeam_channel::bounded::<()>(0);
        {
            let progress = progress.clone();
            thread::spawn(move || {
                let _guard = guard;
                read_progress(stdout, &progress, &clock);
            });
        }
        Ok(Self {
            dir: dir.to_owned(),
            child,
            keys: Some(keys),
            stopped: false,
            tail,
            tail_done,
            progress: ProgressReader { progress, done },
            grace: STOP_GRACE,
        })
    }
}

/// How long after the first frame the frame at `index` was captured, in milliseconds.
fn frame_offset_ms(index: usize) -> i64 {
    i64::try_from(index as u128 * FRAME_INTERVAL.as_millis()).unwrap_or(i64::MAX)
}

/// Follows `-progress` reports. Each report ends with a `progress=` line after its `frame=` line, and the first
/// report that counts frames dates the first one: it was captured that many frame intervals before the report
/// arrived.
fn read_progress(stdout: impl Read, progress: &Mutex<Progress>, clock: &Clock) {
    for line in BufReader::new(stdout).lines() {
        let Ok(line) = line else { return };
        let Some(value) = line.strip_prefix("frame=") else {
            continue;
        };
        let Ok(frames @ 1..) = value.trim().parse::<usize>() else {
            continue;
        };
        let mut progress = lock(progress);
        if progress.first_frame_ms == 0 {
            progress.first_frame_ms = unix_ms(clock()) - frame_offset_ms(frames - 1);
        }
        progress.frames = frames;
    }
}

impl Recording for FfmpegRecording {
    fn raw(&self) -> bool {
        false
    }

    fn take_sink(&mut self) -> Option<Box<dyn FrameSink>> {
        None
    }

    /// Tells ffmpeg to finish, gives it the grace to do so and kills it after that, then writes the index.
    fn stop(&mut self) -> anyhow::Result<Stopped> {
        self.stopped = true;
        if let Some(mut keys) = self.keys.take() {
            // `q` is ffmpeg's own "finish and exit" key; closing the input right after is the fallback for a build
            // that reads no keys.
            let _ = keys.write_all(b"q");
        }
        let (status, killed) = if let Some(status) = wait_within(&mut self.child, self.grace) {
            (Some(status), false)
        } else {
            let _ = self.child.kill();
            (self.child.wait().ok(), true)
        };
        // A grandchild that inherited a pipe is not worth waiting for past the pipe grace.
        let _ = self.tail_done.recv_timeout(PIPE_GRACE);
        // The reader ends with the pipe, after ffmpeg's final report, which carries the frame count.
        let _ = self.progress.done.recv_timeout(PIPE_GRACE);
        let frames_ms: Vec<i64> = {
            let progress = lock(&self.progress.progress);
            (0..progress.frames)
                .map(|index| progress.first_frame_ms + frame_offset_ms(index))
                .collect()
        };

        let Some(&first_frame_ms) = frames_ms.first() else {
            return Err(with_tail(anyhow!("ffmpeg recorded no frame"), &self.tail));
        };
        let written = std::fs::metadata(self.dir.join(VIDEO_FILE)).map(|metadata| metadata.len());
        if !matches!(written, Ok(1..)) {
            let exit = match (killed, status) {
                (true, _) => format!("killed after {:?}", self.grace),
                (false, Some(status)) if status.success() => "exited 0".to_owned(),
                (false, Some(status)) => status.to_string(),
                (false, None) => "its exit could not be read".to_owned(),
            };
            return Err(with_tail(anyhow!("ffmpeg left no {VIDEO_FILE} ({exit})"), &self.tail));
        }
        let frames = frames_ms.len();
        write_index(
            &self.dir,
            &VideoIndex {
                frame_rate: FRAME_RATE,
                frames_ms,
            },
        )?;
        Ok(Stopped { frames, first_frame_ms })
    }
}

impl Drop for FfmpegRecording {
    /// A recording dropped without its stop, which only an abandoned scenario does, leaves no ffmpeg behind.
    fn drop(&mut self) {
        if !self.stopped {
            let _ = self.child.kill();
            let _ = self.child.wait();
        }
    }
}
