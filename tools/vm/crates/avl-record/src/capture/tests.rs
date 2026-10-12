use std::time::{Duration, UNIX_EPOCH};

use super::*;

fn filled(width: usize, height: usize, blue: u8) -> Frame {
    let mut frame = Frame::default();
    frame.resize(width, height);
    for pixel in frame.pix.as_chunks_mut::<BYTES_PER_PIXEL>().0 {
        pixel.copy_from_slice(&[blue, 0x20, 0x30, 0]);
    }
    frame
}

#[test]
fn rgb_swaps_blue_and_red_and_drops_the_padding() {
    let rgb = filled(3, 2, 0x10).to_rgb();
    assert_eq!(rgb.len(), 3 * 2 * 3);
    for pixel in rgb.as_chunks::<3>().0 {
        assert_eq!(*pixel, [0x30, 0x20, 0x10]);
    }
}

/// A PNG of `width` x `height` in the given layout, every pixel the same.
fn png_of(color: png::ColorType, depth: png::BitDepth, pixel: &[u8], palette: Option<&[u8]>) -> Vec<u8> {
    let (width, height) = (4, 3);
    let mut document = Vec::new();
    let mut encoder = png::Encoder::new(&mut document, width, height);
    encoder.set_color(color);
    encoder.set_depth(depth);
    if let Some(palette) = palette {
        encoder.set_palette(palette.to_vec());
    }
    let mut writer = encoder.write_header().unwrap();
    writer.write_image_data(&pixel.repeat((width * height) as usize)).unwrap();
    writer.finish().unwrap();
    document
}

/// Every layout a PNG can take decodes to the same BGRX, the alpha dropped and 16-bit channels cut to their high byte.
#[test]
fn every_png_layout_decodes_to_the_same_bgrx() {
    use png::{BitDepth, ColorType};
    let colored = [0x33, 0x22, 0x11, 0];
    let layouts: [(&str, Vec<u8>, [u8; 4]); 6] = [
        ("rgb", png_of(ColorType::Rgb, BitDepth::Eight, &[0x11, 0x22, 0x33], None), colored),
        (
            "rgba",
            png_of(ColorType::Rgba, BitDepth::Eight, &[0x11, 0x22, 0x33, 0xff], None),
            colored,
        ),
        (
            "rgb16",
            png_of(ColorType::Rgb, BitDepth::Sixteen, &[0x11, 0x99, 0x22, 0x99, 0x33, 0x99], None),
            colored,
        ),
        (
            "palette",
            png_of(ColorType::Indexed, BitDepth::Eight, &[1], Some(&[0, 0, 0, 0x11, 0x22, 0x33])),
            colored,
        ),
        (
            "gray",
            png_of(ColorType::Grayscale, BitDepth::Eight, &[0x22], None),
            [0x22, 0x22, 0x22, 0],
        ),
        (
            "gray-alpha",
            png_of(ColorType::GrayscaleAlpha, BitDepth::Eight, &[0x22, 0x80], None),
            [0x22, 0x22, 0x22, 0],
        ),
    ];
    let at = UNIX_EPOCH + Duration::from_millis(1_790_158_500_120);
    for (name, document, bgrx) in layouts {
        let mut frame = Frame::default();
        frame.set_png(&document, at).unwrap_or_else(|error| panic!("{name}: {error:#}"));
        assert_eq!((frame.width, frame.height, frame.at), (4, 3, at), "{name}");
        for pixel in frame.pix.as_chunks::<BYTES_PER_PIXEL>().0 {
            assert_eq!(*pixel, bgrx, "{name}");
        }
    }
}

#[test]
fn the_detector_names_the_changed_bands_only() {
    let mut detector = Detector::new();
    let mut frame = filled(64, 100, 0x10);
    assert_eq!(detector.update(&frame), Some(0..100), "the first frame changed everywhere");
    assert_eq!(detector.update(&frame.clone()), None, "an identical frame changed");
    frame.row_mut(40)[8] ^= 0xff;
    assert_eq!(detector.update(&frame), Some(32..48), "one pixel on row 40 is its band");
    frame.row_mut(99)[0] ^= 0xff;
    assert_eq!(detector.update(&frame), Some(96..100), "the short last band");
    assert_eq!(
        detector.update(&filled(64, 50, 0x10)),
        Some(0..50),
        "a resized frame changed everywhere"
    );
}
