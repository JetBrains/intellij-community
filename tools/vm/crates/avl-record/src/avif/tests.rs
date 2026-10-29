use super::*;
use crate::testing::ui_frame;

/// The payload of the first box of this type in an ISO BMFF file, after its size and its type. The boxes of an AVIF
/// still nest only a few levels deep, so a search for the type finds each one that the tests read.
fn find_box<'a>(file: &'a [u8], kind: &[u8; 4]) -> &'a [u8] {
    let at = file
        .windows(4)
        .position(|window| window == kind)
        .unwrap_or_else(|| panic!("no {:?} box", String::from_utf8_lossy(kind)));
    let size = u32::from_be_bytes(file[at - 4..at].try_into().unwrap()) as usize;
    &file[at + 4..at - 4 + size]
}

/// The width and the height of the `ispe` box.
fn spatial_extent(file: &[u8]) -> (u32, u32) {
    let ispe = find_box(file, b"ispe");
    (
        u32::from_be_bytes(ispe[4..8].try_into().unwrap()),
        u32::from_be_bytes(ispe[8..12].try_into().unwrap()),
    )
}

/// The AV1 profile and the chroma subsampling flags of the `av1C` box, and the matrix and the range of the `colr` box
/// when the file has one.
fn color(file: &[u8]) -> (u8, u8, Option<(u16, bool)>) {
    let av1c = find_box(file, b"av1C");
    assert_eq!(av1c[0], 0x81, "the marker and the version of av1C");
    let colr = file.windows(4).any(|window| window == b"colr").then(|| {
        let colr = find_box(file, b"colr");
        assert_eq!(&colr[0..4], b"nclx");
        (u16::from_be_bytes(colr[8..10].try_into().unwrap()), colr[10] & 0x80 != 0)
    });
    (av1c[1] >> 5, av1c[2] & 0x7f, colr)
}

fn rgb_of(width: usize, height: usize, accent: u8) -> Vec<u8> {
    ui_frame(width, height, accent).to_rgb()
}

/// A lossy picture is an AVIF still of the frame's size in 8-bit 4:4:4, close to the frame. The sizes are odd, and one
/// is below an AV1 block, because the encoder pads each side and the container names the size. The sawtooth of
/// `ui_frame` costs the lossy picture about 31 dB.
#[test]
fn a_lossy_picture_is_a_close_444_avif_still_of_the_frame() {
    for (width, height) in [(97, 61), (1, 1), (15, 17), (640, 400)] {
        let rgb = rgb_of(width, height, 0x80);
        let (width, height) = (u32::try_from(width).unwrap(), u32::try_from(height).unwrap());
        let (file, distortion) = encode_measured(&rgb, width, height, Loss::Lossy { quality: 50 }, true).unwrap();
        assert_eq!(&file[4..12], b"ftypavif", "the brand of a {width}x{height} picture");
        assert_eq!(spatial_extent(&file), (width, height));
        // The high profile, no subsampling and 8 bits. The `colr` box is left out: BT.601 in full range is what the AV1
        // sequence header says, and what Safari assumes without the box.
        assert_eq!(color(&file), (1, 0, None), "the color of a {width}x{height} picture");
        let psnr = distortion.unwrap().psnr;
        assert!(psnr > 28.0, "a {width}x{height} lossy picture is {psnr:.1} dB from its frame");
    }
}

/// A lossless picture keeps the RGB samples with the identity matrix, so its reconstruction is the frame itself.
#[test]
fn a_lossless_picture_reconstructs_the_exact_frame() {
    let rgb = rgb_of(97, 61, 0x80);
    let (file, distortion) = encode_measured(&rgb, 97, 61, Loss::Lossless, true).unwrap();
    assert_eq!(spatial_extent(&file), (97, 61));
    assert_eq!(
        color(&file),
        (1, 0, Some((0, true))),
        "4:4:4 with the identity matrix in full range"
    );
    assert_eq!(
        distortion.unwrap().squared_error,
        0,
        "the lossless reconstruction differs from the frame"
    );
}

/// The lossy quality decides the size: a better quality costs more bytes on the same frame.
#[test]
fn a_better_quality_takes_more_bytes() {
    let rgb = rgb_of(320, 200, 0x40);
    let sizes = [30, 50, 70].map(|quality| encode(&rgb, 320, 200, Loss::Lossy { quality }).unwrap().len());
    assert!(
        sizes.is_sorted() && sizes[0] < sizes[2],
        "the sizes at the qualities 30, 50 and 70: {sizes:?}"
    );
}

/// A frame of no pixels, or one whose RGB has the wrong length, is refused before libaom sees it.
#[test]
fn a_frame_without_its_pixels_is_refused() {
    let refused = |rgb: &[u8], width, height| format!("{:#}", encode(rgb, width, height, Loss::Lossless).unwrap_err());
    assert_eq!(refused(&[], 0, 4), "the frame has no pixels");
    assert_eq!(refused(&[0; 5], 1, 2), "the frame has 5 bytes of RGB for 1x2 pixels");
}

#[test]
fn the_quality_maps_to_the_quantizer_of_libavif() {
    let quantizers = [0, 50, 100, 200].map(|quality| Loss::Lossy { quality }.quantizer());
    assert_eq!(quantizers, [63, 32, 0, 0]);
    assert_eq!(Loss::Lossless.quantizer(), 0);
}
