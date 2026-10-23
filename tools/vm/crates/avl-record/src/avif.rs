//! An AVIF picture of a frame: AV1 from libaom in the container of `avif-serialize`.
//!
//! A lossy picture is 8-bit YCbCr 4:4:4 with the BT.601 matrix in full range, as `avifenc -y 444` writes it. 4:4:4,
//! because the chroma of antialiased UI text is a pixel wide. A lossless picture keeps the RGB samples with the
//! identity matrix, so it decodes to the exact pixels. Both are one AV1 key frame with the reduced still picture
//! header.
//!
//! The encoder settings are those of `avifenc` for a still at one thread: the all-intra usage, a fixed quantizer, and
//! speed [SPEED]. The tuning is `iq`, the default of `avifenc` for a still. The decision record
//! `2026-10-23-a-lossy-still-is-avif-from-libaom.md` has the measurement.

use std::ffi::{CStr, c_int, c_uint};
use std::mem::MaybeUninit;
use std::ptr;
use std::slice;

use anyhow::{Context, bail, ensure};
use avif_serialize::Aviffy;
use avif_serialize::constants::{ColorPrimaries, MatrixCoefficients, TransferCharacteristics};
use avl_aom_sys as aom;

#[cfg(test)]
mod tests;

/// The libaom speed, from 0 (the slowest) to 9. In libaom 3.15, speed 6 takes 4 times the CPU of speed 8 on a step
/// picture, for a quarter fewer bytes at about the same quality.
pub(crate) const SPEED: u8 = 6;

/// What one encode writes.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub(crate) enum Loss {
    /// Lossy at a quality from 0 to 100 on the scale of `avifenc -q`.
    Lossy { quality: u8 },
    /// Lossless: the picture decodes to the exact pixels.
    Lossless,
}

impl Loss {
    /// The AV1 quantizer, from 0 (the best) to 63, as libavif maps its quality.
    fn quantizer(self) -> c_uint {
        match self {
            Self::Lossy { quality } => ((100 - c_uint::from(quality.min(100))) * 63 + 50) / 100,
            Self::Lossless => 0,
        }
    }
}

/// A tightly packed RGB picture of `width` by `height` pixels as an AVIF file.
pub(crate) fn encode(rgb: &[u8], width: u32, height: u32, loss: Loss) -> anyhow::Result<Vec<u8>> {
    Ok(encode_measured(rgb, width, height, loss, false)?.0)
}

/// What libaom measured of one picture: the squared error of its samples against its reconstruction, the picture that
/// a decoder shows, and the PSNR of that error in dB.
#[derive(Clone, Copy, PartialEq, Debug)]
pub(crate) struct Distortion {
    pub(crate) squared_error: u64,
    pub(crate) psnr: f64,
}

/// [encode], and with `measure` the [Distortion] of the YUV planes too. libaom computes it from the reconstruction, so
/// a test checks the encode without an AV1 decoder.
pub(crate) fn encode_measured(
    rgb: &[u8],
    width: u32,
    height: u32,
    loss: Loss,
    measure: bool,
) -> anyhow::Result<(Vec<u8>, Option<Distortion>)> {
    let pixels = usize::try_from(u64::from(width) * u64::from(height)).context("the frame is too large for AVIF")?;
    ensure!(pixels > 0, "the frame has no pixels");
    ensure!(
        rgb.len() == pixels * 3,
        "the frame has {} bytes of RGB for {width}x{height} pixels",
        rgb.len()
    );
    let matrix = match loss {
        Loss::Lossy { .. } => MatrixCoefficients::Bt601,
        Loss::Lossless => MatrixCoefficients::Rgb,
    };
    let mut planes = planes(rgb, pixels, loss);
    let (av1, distortion) = encode_av1(&mut planes, width, height, loss, matrix, measure)?;
    let file = Aviffy::new()
        .set_seq_profile(1)
        .set_chroma_subsampling((false, false))
        .set_color_primaries(ColorPrimaries::Bt709)
        .set_transfer_characteristics(TransferCharacteristics::Srgb)
        .set_matrix_coefficients(matrix)
        .set_full_color_range(true)
        .to_vec(&av1, None, width, height, 8);
    Ok((file, distortion))
}

/// The three planes of the picture, one after the other, which AV1 names Y, U and V: Y, Cb and Cr of BT.601 in full range
/// for a lossy picture, and G, B and R for a lossless one, the plane order of the identity matrix.
fn planes(rgb: &[u8], pixels: usize, loss: Loss) -> Vec<u8> {
    let mut planes = vec![0; pixels * 3];
    let (y_plane, rest) = planes.split_at_mut(pixels);
    let (u_plane, v_plane) = rest.split_at_mut(pixels);
    let samples = y_plane.iter_mut().zip(u_plane.iter_mut()).zip(v_plane.iter_mut());
    for (((y, u), v), pixel) in samples.zip(rgb.as_chunks::<3>().0) {
        let [r, g, b] = *pixel;
        (*y, *u, *v) = match loss {
            Loss::Lossy { .. } => ycbcr(r, g, b),
            Loss::Lossless => (g, b, r),
        };
    }
    planes
}

/// BT.601 in full range, in 16-bit fixed point with rounding. Each row of weights sums to 65536 or to 0, so every
/// result is in 0..=255.
const fn ycbcr(r: u8, g: u8, b: u8) -> (u8, u8, u8) {
    const HALF: i32 = 1 << 15;
    const OFFSET: i32 = 128 << 16;
    let (r, g, b) = (r as i32, g as i32, b as i32);
    let y = (19595 * r + 38470 * g + 7471 * b + HALF) >> 16;
    let cb = (-11059 * r - 21709 * g + 32768 * b + OFFSET + HALF) >> 16;
    let cr = (32768 * r - 27439 * g - 5329 * b + OFFSET + HALF) >> 16;
    #[expect(
        clippy::cast_possible_truncation,
        clippy::cast_sign_loss,
        reason = "the weights keep every value in 0..=255"
    )]
    (y as u8, cb as u8, cr as u8)
}

/// One encoder instance, destroyed on drop.
struct Encoder(aom::aom_codec_ctx_t);

impl Encoder {
    fn start(cfg: &aom::aom_codec_enc_cfg, interface: *const aom::aom_codec_iface, measure: bool) -> anyhow::Result<Self> {
        let mut context = MaybeUninit::<aom::aom_codec_ctx_t>::zeroed();
        let version = c_int::try_from(aom::AOM_ENCODER_ABI_VERSION).context("the ABI version of libaom")?;
        let flags = if measure {
            aom::aom_codec_flags_t::from(aom::AOM_CODEC_USE_PSNR)
        } else {
            0
        };
        // SAFETY: the context is writable memory of the right type, the interface is libaom's own, and the version is
        // that of the bindings, which libaom refuses when its own differs.
        let result = unsafe { aom::aom_codec_enc_init_ver(context.as_mut_ptr(), interface, cfg, flags, version) };
        ensure!(result == aom::AOM_CODEC_OK, "libaom cannot start an encoder: error {result}");
        // SAFETY: a context that the init filled is initialized, and a zeroed one is a valid `aom_codec_ctx_t` too.
        Ok(Self(unsafe { context.assume_init() }))
    }

    /// The error of the last call, with libaom's detail when it has one.
    fn failure(&self, what: &str) -> anyhow::Error {
        // SAFETY: the context is initialized.
        let error = unsafe { aom::aom_codec_error(&raw const self.0) };
        // SAFETY: libaom answers a static NUL-terminated string for every context.
        let error = unsafe { CStr::from_ptr(error) };
        // SAFETY: the context is initialized. The detail is a string of the context, or null.
        let detail = unsafe { aom::aom_codec_error_detail(&raw const self.0) };
        if detail.is_null() {
            return anyhow::anyhow!("libaom cannot {what}: {}", error.to_string_lossy());
        }
        // SAFETY: a detail that is not null is a NUL-terminated string that lives until the next call.
        let detail = unsafe { CStr::from_ptr(detail) };
        anyhow::anyhow!("libaom cannot {what}: {}: {}", error.to_string_lossy(), detail.to_string_lossy())
    }

    fn control(&mut self, id: aom::aome_enc_control_id, value: c_int) -> anyhow::Result<()> {
        let id = c_int::try_from(id).context("a libaom control id")?;
        // SAFETY: the context is initialized, and each control of this module takes one `int`.
        let result = unsafe { aom::aom_codec_control(&raw mut self.0, id, value) };
        if result != aom::AOM_CODEC_OK {
            return Err(self.failure(&format!("set the control {id} to {value}")));
        }
        Ok(())
    }

    /// Encodes `image`, or flushes the encoder when it is `None`, appends the frames to `av1`, and answers the distortion
    /// when the encoder measures it.
    fn encode(&mut self, image: Option<&aom::aom_image_t>, av1: &mut Vec<u8>) -> anyhow::Result<Option<Distortion>> {
        let image = image.map_or(ptr::null(), ptr::from_ref);
        // SAFETY: the context is initialized, and the image is null or a wrapped image whose planes outlive the call.
        let result = unsafe { aom::aom_codec_encode(&raw mut self.0, image, 0, 1, 0) };
        if result != aom::AOM_CODEC_OK {
            return Err(self.failure(if image.is_null() {
                "flush the encoder"
            } else {
                "encode the picture"
            }));
        }
        let mut iterator = ptr::null();
        let mut distortion = None;
        loop {
            // SAFETY: the context is initialized, and the iterator is the one that libaom advances.
            let packet = unsafe { aom::aom_codec_get_cx_data(&raw mut self.0, &raw mut iterator) };
            // SAFETY: a packet that is not null is valid until the next call into the context.
            let Some(packet) = (unsafe { packet.as_ref() }) else {
                return Ok(distortion);
            };
            if packet.kind == aom::AOM_CODEC_CX_FRAME_PKT {
                // SAFETY: a frame packet holds the `frame` member of the union.
                let frame = unsafe { packet.data.frame };
                // SAFETY: the frame's buffer holds `sz` bytes until the next call into the context.
                av1.extend_from_slice(unsafe { slice::from_raw_parts(frame.buf.cast::<u8>(), frame.sz) });
            } else if packet.kind == aom::AOM_CODEC_PSNR_PKT {
                // SAFETY: a PSNR packet holds the `psnr` member of the union.
                let measured = unsafe { packet.data.psnr };
                distortion = Some(Distortion {
                    squared_error: measured.sse[0],
                    psnr: measured.psnr[0],
                });
            }
        }
    }
}

impl Drop for Encoder {
    fn drop(&mut self) {
        // SAFETY: the context was initialized by `start` and is destroyed exactly once.
        unsafe { aom::aom_codec_destroy(&raw mut self.0) };
    }
}

/// The AV1 temporal unit of the planes, the sequence header and the one frame, and with `measure` its distortion.
fn encode_av1(
    planes: &mut [u8],
    width: u32,
    height: u32,
    loss: Loss,
    matrix: MatrixCoefficients,
    measure: bool,
) -> anyhow::Result<(Vec<u8>, Option<Distortion>)> {
    // SAFETY: the call takes no argument and answers libaom's static encoder interface.
    let interface = unsafe { aom::aom_codec_av1_cx() };
    let mut cfg = MaybeUninit::<aom::aom_codec_enc_cfg>::zeroed();
    // SAFETY: the configuration is writable memory of the right type, which the call fills.
    let result = unsafe { aom::aom_codec_enc_config_default(interface, cfg.as_mut_ptr(), aom::AOM_USAGE_ALL_INTRA) };
    ensure!(result == aom::AOM_CODEC_OK, "libaom has no all-intra configuration: error {result}");
    // SAFETY: the call above filled the configuration.
    let mut cfg = unsafe { cfg.assume_init() };
    let quantizer = loss.quantizer();
    cfg.g_w = width;
    cfg.g_h = height;
    // The high profile, which 4:4:4 needs.
    cfg.g_profile = 1;
    cfg.g_bit_depth = aom::AOM_BITS_8;
    cfg.g_input_bit_depth = 8;
    // One thread: the still writer runs one encoder on each of its threads.
    cfg.g_threads = 1;
    // One frame, which gives the reduced still picture header.
    cfg.g_limit = 1;
    cfg.g_lag_in_frames = 0;
    cfg.rc_end_usage = aom::AOM_Q;
    cfg.rc_min_quantizer = quantizer;
    cfg.rc_max_quantizer = quantizer;

    let mut encoder = Encoder::start(&cfg, interface, measure)?;
    encoder.control(aom::AOME_SET_CPUUSED, c_int::from(SPEED))?;
    match loss {
        Loss::Lossy { .. } => {
            encoder.control(aom::AOME_SET_CQ_LEVEL, c_int::try_from(quantizer)?)?;
            encoder.control(aom::AOME_SET_TUNING, c_int::try_from(aom::AOM_TUNE_IQ)?)?;
        }
        Loss::Lossless => encoder.control(aom::AV1E_SET_LOSSLESS, 1)?,
    }
    encoder.control(aom::AV1E_SET_COLOR_RANGE, c_int::try_from(aom::AOM_CR_FULL_RANGE)?)?;
    encoder.control(aom::AV1E_SET_COLOR_PRIMARIES, c_int::try_from(aom::AOM_CICP_CP_BT_709)?)?;
    encoder.control(aom::AV1E_SET_TRANSFER_CHARACTERISTICS, c_int::try_from(aom::AOM_CICP_TC_SRGB)?)?;
    encoder.control(aom::AV1E_SET_MATRIX_COEFFICIENTS, matrix as c_int)?;

    let mut image = MaybeUninit::<aom::aom_image_t>::zeroed();
    // SAFETY: the image is writable memory of the right type. `planes` holds three planes of `width * height` bytes, the
    // layout of 4:4:4 with the alignment 1, and it outlives every use of the image below.
    let wrapped = unsafe { aom::aom_img_wrap(image.as_mut_ptr(), aom::AOM_IMG_FMT_I444, width, height, 1, planes.as_mut_ptr()) };
    if wrapped.is_null() {
        bail!("libaom cannot wrap the planes of a {width}x{height} picture");
    }
    // SAFETY: the wrap above filled the image.
    let mut image = unsafe { image.assume_init() };
    image.range = aom::AOM_CR_FULL_RANGE;
    image.cp = aom::AOM_CICP_CP_BT_709;
    image.tc = aom::AOM_CICP_TC_SRGB;
    image.mc = matrix as aom::aom_matrix_coefficients_t;

    let mut av1 = Vec::new();
    let encoded = encoder.encode(Some(&image), &mut av1)?;
    let flushed = encoder.encode(None, &mut av1)?;
    let distortion = encoded.or(flushed);
    ensure!(!av1.is_empty(), "libaom wrote no frame for the picture");
    ensure!(!measure || distortion.is_some(), "libaom measured no distortion of the picture");
    Ok((av1, distortion))
}
