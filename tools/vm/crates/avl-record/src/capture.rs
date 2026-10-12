//! Where a recorder's pixels come from: one frame type every source fills, the trait a source implements, and the
//! change detector the recorder runs over consecutive frames.
//!
//! The sources are the rungs of the ladder [avl_trace::protocol::CaptureSource] names: [x11] reads the X server,
//! [ide] asks the bridge to paint. The recorder picks one and records which, and why a better one was not
//! available.

use std::ops::Range;
use std::time::{Instant, SystemTime, UNIX_EPOCH};

use anyhow::{Context, bail};
use avl_trace::protocol::CaptureSource;

pub(crate) mod ide;
#[cfg(target_os = "linux")]
pub(crate) mod x11;

#[cfg(test)]
mod tests;

/// The size of one BGRX pixel.
pub(crate) const BYTES_PER_PIXEL: usize = 4;

/// One screen picture in BGRX: blue, green, red and a byte nothing reads, per pixel, rows top to bottom, packed.
///
/// BGRX is not an arbitrary choice. It is what an X server with a 24-bit TrueColor visual at 32 bits per pixel sends
/// in least-significant-byte-first order, so an X11 frame lands in it without a per-pixel pass. It is also BGRA with an
/// alpha nothing reads, which the H.264 encoder's input conversion reads directly. Only the still encoder needs
/// another order, and [Frame::to_rgb] converts for it in one tight loop.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Frame {
    pub(crate) width: usize,
    pub(crate) height: usize,
    pub(crate) pix: Vec<u8>,
    /// When the pixels were read.
    pub(crate) at: SystemTime,
}

impl Default for Frame {
    fn default() -> Self {
        Self {
            width: 0,
            height: 0,
            pix: Vec::new(),
            at: UNIX_EPOCH,
        }
    }
}

impl Frame {
    /// Makes the frame hold a picture of this size, reusing its buffer. The pixels are left as they were, so a
    /// caller fills every row it resizes to.
    pub(crate) fn resize(&mut self, width: usize, height: usize) {
        self.width = width;
        self.height = height;
        self.pix.resize(width * height * BYTES_PER_PIXEL, 0);
    }

    /// The bytes of one row.
    #[cfg(test)]
    pub(crate) fn row(&self, y: usize) -> &[u8] {
        let length = self.width * BYTES_PER_PIXEL;
        &self.pix[y * length..(y + 1) * length]
    }

    #[cfg(test)]
    pub(crate) fn row_mut(&mut self, y: usize) -> &mut [u8] {
        let length = self.width * BYTES_PER_PIXEL;
        &mut self.pix[y * length..(y + 1) * length]
    }

    /// The pixels as tightly packed RGB, the order the still encoder takes. BGRX has no alpha, and a still is
    /// opaque, so none is invented for it.
    pub(crate) fn to_rgb(&self) -> Vec<u8> {
        let mut rgb = Vec::with_capacity(self.width * self.height * 3);
        for pixel in self.pix.as_chunks::<BYTES_PER_PIXEL>().0 {
            rgb.extend_from_slice(&[pixel[2], pixel[1], pixel[0]]);
        }
        rgb
    }

    /// Fills the frame from a PNG, such as the bridge's painted screen, stamped `at`.
    ///
    /// The decoder expands every layout to 8-bit gray, gray and alpha, RGB or RGBA, and each of those is converted
    /// with one pass. An alpha is dropped: a painted screen is opaque anyway, and BGRX has nowhere to keep it.
    pub(crate) fn set_png(&mut self, document: &[u8], at: SystemTime) -> anyhow::Result<()> {
        let mut decoder = png::Decoder::new(std::io::Cursor::new(document));
        decoder.set_transformations(png::Transformations::normalize_to_color8());
        let mut reader = decoder.read_info()?;
        let size = reader.output_buffer_size().context("the PNG is too large to decode")?;
        let mut buffer = vec![0; size];
        let info = reader.next_frame(&mut buffer)?;
        let channels = match info.color_type {
            png::ColorType::Grayscale => 1,
            png::ColorType::GrayscaleAlpha => 2,
            png::ColorType::Rgb => 3,
            png::ColorType::Rgba => 4,
            png::ColorType::Indexed => bail!("the PNG's palette was not expanded"),
        };
        let (width, height) = (info.width as usize, info.height as usize);
        self.resize(width, height);
        self.at = at;
        for y in 0..height {
            let source = &buffer[y * info.line_size..y * info.line_size + width * channels];
            let target = &mut self.pix[y * width * BYTES_PER_PIXEL..(y + 1) * width * BYTES_PER_PIXEL];
            for (pixel, bgrx) in source.chunks_exact(channels).zip(target.as_chunks_mut::<BYTES_PER_PIXEL>().0) {
                let (red, green, blue) = if channels < 3 {
                    (pixel[0], pixel[0], pixel[0])
                } else {
                    (pixel[0], pixel[1], pixel[2])
                };
                bgrx.copy_from_slice(&[blue, green, red, 0]);
            }
        }
        Ok(())
    }
}

/// One rung of the capture ladder. The recorder's frame loop and a snapshot both grab, from their own threads.
/// Closing is `Drop`.
pub(crate) trait Source: Send + Sync {
    /// The source as the manifest and every snapshot record name it.
    fn kind(&self) -> CaptureSource;

    /// Reads the screen as it is now into `frame`, reusing its buffer, and gives up at `deadline`.
    fn grab(&self, deadline: Instant, frame: &mut Frame) -> anyhow::Result<()>;

    /// Why this source no longer grabs, `None` while it does. A source that breaks is opened anew rather than
    /// tried again.
    fn broken(&self) -> Option<String> {
        None
    }
}

// --- change detection ------------------------------------------------------------------------------------------

/// The height of the bands the detector hashes. Small enough that a blinking caret changes one band rather than the
/// screen, large enough that a 1080-row frame is 68 hashes to keep.
pub(crate) const DETECTOR_BAND_ROWS: usize = 16;

/// Tells which rows of a frame changed since the frame it was shown before.
///
/// It keeps one keyed 64-bit hash per horizontal band rather than the previous frame's pixels, so it costs a pass
/// over the frame and 8 bytes a band, not a second frame buffer. A hash collision would call a changed band
/// unchanged; with a 64-bit keyed hash that is far below every other way a frame can be lost. XXH3 rather than std's
/// SipHash, because the frame loop hashes every band of a 1080p frame ten times a second.
pub(crate) struct Detector {
    seed: u64,
    width: usize,
    height: usize,
    bands: Vec<u64>,
}

impl Detector {
    /// A detector that has seen no frame yet, keyed at random.
    pub(crate) fn new() -> Self {
        use std::hash::BuildHasher;
        Self {
            seed: std::collections::hash_map::RandomState::new().hash_one(0_u8),
            width: 0,
            height: 0,
            bands: Vec::new(),
        }
    }

    /// Hashes the frame and answers the rows whose bands differ from the previous frame's, `None` when nothing
    /// changed. The first frame, and a frame of another size, changed everywhere.
    pub(crate) fn update(&mut self, frame: &Frame) -> Option<Range<usize>> {
        let count = frame.height.div_ceil(DETECTOR_BAND_ROWS);
        let resized = frame.width != self.width || frame.height != self.height;
        if resized {
            self.width = frame.width;
            self.height = frame.height;
            self.bands = vec![0; count];
        }
        let band_bytes = DETECTOR_BAND_ROWS * frame.width * BYTES_PER_PIXEL;
        let mut changed: Option<Range<usize>> = None;
        for (band, (pixels, kept)) in frame.pix.chunks(band_bytes.max(1)).zip(self.bands.iter_mut()).enumerate() {
            let sum = xxhash_rust::xxh3::xxh3_64_with_seed(pixels, self.seed);
            if resized || sum != *kept {
                let first = band * DETECTOR_BAND_ROWS;
                let last = (first + DETECTOR_BAND_ROWS).min(frame.height);
                changed = Some(match changed {
                    Some(rows) => rows.start.min(first)..rows.end.max(last),
                    None => first..last,
                });
            }
            *kept = sum;
        }
        changed
    }
}
