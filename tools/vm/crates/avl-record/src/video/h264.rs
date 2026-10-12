//! The raw video's encoder: the frames the recorder grabs itself, in H.264 by Cisco's openh264, in the fragmented MP4
//! of [super::mp4].
//!
//! Every frame the frame loop writes becomes one sample, the previous picture again when the screen did not change.
//! The encoder codes such a repeat as skipped macroblocks, so it costs almost nothing. The recorder stamps every
//! frame it writes, so `video.index.json` is exact: frame i is the picture read at `framesMs[i]`.
//!
//! The encoder runs inside the frame loop's write, on the loop's thread. There is no process and no pipe, so no write
//! waits for a reader that stopped, and the stop waits for at most the one frame that is being encoded.

use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

use anyhow::{Context, bail};
use avl_trace::bundle::{VIDEO_FILE, VideoIndex};
use openh264::OpenH264API;
use openh264::encoder::{
    Complexity, Encoder, EncoderConfig, FrameRate, FrameType, IntraFramePeriod, RateControlMode, SpsPpsStrategy, UsageType,
};
use openh264::formats::{BgraSliceU8, YUVBuffer};

use super::mp4::FragmentedMp4;
use super::{FRAME_RATE, FrameSink, Recording, Stopped, write_index};
use crate::capture::{BYTES_PER_PIXEL, Frame};
use crate::{lock, unix_ms};

#[cfg(test)]
mod tests;

/// The encoder's speed budget, which x264's `veryfast` preset was: the IDE under test competes for the same cores.
const COMPLEXITY: Complexity = Complexity::Low;

/// The largest frame the encoder takes, 3840x2160. openh264 supports the levels up to 5.2 only, and it refuses a larger
/// frame at its first encode. In either orientation, the longer side may not exceed [MAX_WIDTH] and the shorter side
/// may not exceed [MAX_HEIGHT], so a portrait 2160x3840 is taken too.
const MAX_WIDTH: usize = 3840;
const MAX_HEIGHT: usize = 2160;

/// The type of the NAL unit that holds a sequence parameter set.
const NAL_SPS: u8 = 7;

/// The type of the NAL unit that holds a picture parameter set.
const NAL_PPS: u8 = 8;

/// The encoder's settings.
///
/// The rate control is off, so the encoder codes every picture at a fixed QP 26 in place of x264's CRF 26. That is
/// openh264's default layer quantizer, which the crate does not expose. CRF is not a QP: x264 varies the quantizer
/// per frame for it. Adaptive quantization is off, so the quantizer is flat over the frame, and the text of a UI stays
/// legible at it.
///
/// A keyframe comes every [FRAME_RATE] frames, one a second, which x264's `-g 10` was: a seek in the viewer never
/// decodes more than a second of frames, and a killed recorder loses at most its last second.
///
/// openh264 makes no B-frames, which x264 needed `-bf 0` for. A frame's decode time is its presentation time, so the
/// first frame plays at 0 and frame i at i/[FRAME_RATE], which is what the index says.
///
/// The screen content mode keeps its scene change detection on, and a frame that changes most of the screen is a
/// keyframe too, which starts a fragment of its own. That mode does not support background detection, so it is
/// off.
///
/// Frame skipping is off: frame i of the index must be sample i of the video, and a frame the encoder dropped would
/// move every later one. One thread, since the IDE under test competes for the cores. The parameter set ids
/// stay constant, so every keyframe repeats the SPS and PPS that the file's header carries.
const fn config() -> EncoderConfig {
    EncoderConfig::new()
        .usage_type(UsageType::ScreenContentRealTime)
        .rate_control_mode(RateControlMode::Off)
        .max_frame_rate(FrameRate::from_hz(FRAME_RATE as f32))
        .intra_frame_period(IntraFramePeriod::from_num_frames(FRAME_RATE))
        .adaptive_quantization(false)
        .background_detection(false)
        .skip_frames(false)
        .complexity(COMPLEXITY)
        .num_threads(1)
        .sps_pps_strategy(SpsPpsStrategy::ConstantId)
}

/// One running raw video. The frame loop's sink and the stop share its state.
pub(crate) struct H264Recording {
    dir: PathBuf,
    state: Arc<Mutex<State>>,
    /// The frame loop's input, until the loop takes it.
    sink: Option<H264Sink>,
}

impl H264Recording {
    /// Starts a raw recording of `width` x `height` frames into `dir`. The file is created with the first frame,
    /// whose keyframe carries the parameter sets the file's header needs.
    ///
    /// The encoder takes an even size only, so a screen of an odd width or height loses its last column or row, as
    /// ffmpeg's `crop=trunc(iw/2)*2:trunc(ih/2)*2` did. A screen larger than the encoder takes is refused here, before
    /// the scenario's first frame, rather than at that frame's encode.
    pub(crate) fn start(dir: &Path, width: usize, height: usize) -> anyhow::Result<Self> {
        let (even_width, even_height) = (width & !1, height & !1);
        if even_width == 0 || even_height == 0 {
            bail!("a {width}x{height} screen is too small for a video");
        }
        if even_width.max(even_height) > MAX_WIDTH || even_width.min(even_height) > MAX_HEIGHT {
            bail!("a {width}x{height} screen exceeds the encoder's {MAX_WIDTH}x{MAX_HEIGHT}");
        }
        let encoder = Encoder::with_api_config(OpenH264API::from_source(), config()).context("cannot start the H.264 encoder")?;
        let state = Arc::new(Mutex::new(State {
            encoder,
            yuv: YUVBuffer::new(even_width, even_height),
            width,
            height,
            even: Vec::new(),
            sample: Vec::new(),
            path: dir.join(VIDEO_FILE),
            mp4: None,
            sps: Vec::new(),
            pps: Vec::new(),
            frames_ms: Vec::new(),
            stopped: false,
        }));
        Ok(Self {
            dir: dir.to_owned(),
            sink: Some(H264Sink(state.clone())),
            state,
        })
    }
}

impl Recording for H264Recording {
    fn raw(&self) -> bool {
        true
    }

    fn take_sink(&mut self) -> Option<Box<dyn FrameSink>> {
        self.sink.take().map(|sink| Box::new(sink) as Box<dyn FrameSink>)
    }

    /// Refuses every later frame, writes the pending fragment, then the index. A frame being encoded finishes first,
    /// since it holds the state.
    fn stop(&mut self) -> anyhow::Result<Stopped> {
        self.sink = None;
        let (mp4, frames_ms) = {
            let mut state = lock(&self.state);
            state.stopped = true;
            (state.mp4.take(), std::mem::take(&mut state.frames_ms))
        };
        let (Some(mp4), Some(&first_frame_ms)) = (mp4, frames_ms.first()) else {
            bail!("the encoder recorded no frame");
        };
        mp4.finish().with_context(|| format!("cannot finish {VIDEO_FILE}"))?;
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

/// A raw recording's input, which the frame loop owns.
struct H264Sink(Arc<Mutex<State>>);

impl FrameSink for H264Sink {
    /// Encodes one frame into the file and lists it in the index.
    fn write_frame(&mut self, frame: &Frame) -> anyhow::Result<()> {
        lock(&self.0).write(frame)
    }
}

struct State {
    encoder: Encoder,
    /// The picture the encoder reads, which each frame is converted into.
    yuv: YUVBuffer,
    /// The size every frame must have.
    width: usize,
    height: usize,
    /// A frame cut to the even size of [State::yuv], for a screen of an odd width or height.
    even: Vec<u8>,
    /// The sample being assembled from the encoder's output.
    sample: Vec<u8>,
    path: PathBuf,
    /// The file, from the first frame on.
    mp4: Option<FragmentedMp4>,
    /// The parameter sets of the file's header, which every later keyframe must repeat.
    sps: Vec<u8>,
    pps: Vec<u8>,
    frames_ms: Vec<i64>,
    stopped: bool,
}

impl State {
    fn write(&mut self, frame: &Frame) -> anyhow::Result<()> {
        if self.stopped {
            bail!("the video is stopped");
        }
        if frame.width != self.width || frame.height != self.height {
            bail!(
                "a {}x{} frame for a {}x{} video",
                frame.width,
                frame.height,
                self.width,
                self.height
            );
        }
        debug_assert_eq!(frame.pix.len(), frame.width * frame.height * BYTES_PER_PIXEL);
        let (width, height) = (self.width & !1, self.height & !1);
        let pix = if (width, height) == (self.width, self.height) {
            &frame.pix
        } else {
            self.even.clear();
            let rows = frame.pix.chunks_exact(self.width * BYTES_PER_PIXEL);
            for row in rows.take(height) {
                self.even.extend_from_slice(&row[..width * BYTES_PER_PIXEL]);
            }
            &self.even
        };
        // BGRX is BGRA with an alpha nothing reads, and the conversion to YUV reads no alpha.
        self.yuv.read_bgra8(BgraSliceU8::new(pix, (width, height)));
        let stream = self.encoder.encode(&self.yuv).context("the H.264 encoder refused a frame")?;
        let frame_type = stream.frame_type();

        self.sample.clear();
        let (mut sps, mut pps) = (None, None);
        for layer in (0..stream.num_layers()).filter_map(|index| stream.layer(index)) {
            for nal in (0..layer.nal_count()).filter_map(|index| layer.nal_unit(index)) {
                let nal = without_start_code(nal);
                match nal.first().map(|header| header & 0x1F) {
                    None => {}
                    // A layer lends its units only while it lives, and the parameter sets come once a second.
                    Some(NAL_SPS) => sps = Some(nal.to_vec()),
                    Some(NAL_PPS) => pps = Some(nal.to_vec()),
                    // A slice of the picture, or a unit that goes with it, such as an SEI.
                    Some(_) => {
                        let length = u32::try_from(nal.len()).context("a NAL unit of 4 GiB")?;
                        self.sample.extend_from_slice(&length.to_be_bytes());
                        self.sample.extend_from_slice(nal);
                    }
                }
            }
        }
        if self.sample.is_empty() {
            bail!("the H.264 encoder made no picture of a frame ({frame_type:?})");
        }

        let mp4 = if let Some(mp4) = &mut self.mp4 {
            // The file's header holds the one pair of parameter sets that every frame decodes with.
            let changed = |new: Option<Vec<u8>>, old: &[u8]| new.is_some_and(|new| new != old);
            if changed(sps, &self.sps) || changed(pps, &self.pps) {
                bail!("the H.264 encoder changed its parameter sets mid-video");
            }
            mp4
        } else {
            let (Some(sps), Some(pps)) = (sps, pps) else {
                bail!("the H.264 encoder's first frame has no SPS and PPS");
            };
            let mp4 = FragmentedMp4::start(&self.path, width, height, &sps, &pps).with_context(|| format!("cannot start {VIDEO_FILE}"))?;
            self.sps = sps;
            self.pps = pps;
            self.mp4.insert(mp4)
        };
        mp4.push(frame_type == FrameType::IDR, &self.sample)
            .with_context(|| format!("writing a frame to {VIDEO_FILE}"))?;
        // A frame's capture time is the wall clock, which the Linux guest's clock steps can move back. The index must
        // stay sorted for the viewer's seek, and the contract refuses one that is not, so a frame stamped before the
        // previous one is listed at the previous one's time.
        let at = unix_ms(frame.at).max(self.frames_ms.last().copied().unwrap_or(i64::MIN));
        self.frames_ms.push(at);
        Ok(())
    }
}

/// A NAL unit without the Annex B start code the encoder writes before it.
fn without_start_code(nal: &[u8]) -> &[u8] {
    nal.strip_prefix(&[0, 0, 0, 1])
        .or_else(|| nal.strip_prefix(&[0, 0, 1]))
        .unwrap_or(nal)
}
