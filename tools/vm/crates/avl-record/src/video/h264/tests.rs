use std::time::{Duration, UNIX_EPOCH};

use avl_trace::bundle::{VIDEO_INDEX_FILE, decode_video_index};
use openh264::decoder::Decoder;
use openh264::formats::YUVSource;

use super::*;
use crate::video::mp4::tests::{Mp4, parse};

/// A `width` x `height` frame of one grey `level`, stamped `ms`.
fn grey(width: usize, height: usize, level: u8, ms: u64) -> Frame {
    let mut frame = Frame::default();
    frame.resize(width, height);
    frame.pix.fill(level);
    frame.at = UNIX_EPOCH + Duration::from_millis(ms);
    frame
}

/// The ramp's frame size: eight macroblocks.
const RAMP_WIDTH: usize = 64;
const RAMP_HEIGHT: usize = 32;

/// The grey of the ramp's still background.
const BACKGROUND: u8 = 128;

/// The side of the ramp's changing corner: one macroblock.
const CORNER: usize = 16;

/// Frame `index` of the ramp: a still background and a top left corner that lightens a step a frame, as a UI changes
/// one part of the screen. A frame that changes most of the screen is a scene change, which the encoder makes a
/// keyframe of.
fn ramp_frame(index: usize) -> Frame {
    let mut frame = grey(RAMP_WIDTH, RAMP_HEIGHT, BACKGROUND, ramp_ms(index));
    for row in 0..CORNER {
        frame.row_mut(row)[..CORNER * BYTES_PER_PIXEL].fill(ramp(index));
    }
    frame
}

/// The mean luma of ramp frame `index`.
fn ramp_luma(index: usize) -> f64 {
    let corner = (CORNER * CORNER) as f64 / (RAMP_WIDTH * RAMP_HEIGHT) as f64;
    corner * luma(ramp(index)) + (1.0 - corner) * luma(BACKGROUND)
}

/// The grey level of frame `index` of the ramp: a step a frame, dark to light.
fn ramp(index: usize) -> u8 {
    u8::try_from(20 + 8 * index).unwrap()
}

/// The luma the encoder's input conversion makes of a grey `level`: limited range, 16 for black.
fn luma(level: u8) -> f64 {
    16.0 + 0.859_375 * f64::from(level)
}

/// A decoded picture's size and the mean of its luma.
#[derive(Debug)]
struct Picture {
    width: usize,
    height: usize,
    mean_luma: f64,
}

impl Picture {
    fn of(yuv: &impl YUVSource) -> Self {
        let (width, height) = yuv.dimensions();
        let stride = yuv.strides().0;
        let sum: u64 = (0..height)
            .flat_map(|row| &yuv.y()[row * stride..row * stride + width])
            .map(|&luma| u64::from(luma))
            .sum();
        Self {
            width,
            height,
            mean_luma: sum as f64 / (width * height) as f64,
        }
    }
}

/// Appends `nal` after an Annex B start code.
fn annex_b(stream: &mut Vec<u8>, nal: &[u8]) {
    stream.extend_from_slice(&[0, 0, 0, 1]);
    stream.extend_from_slice(nal);
}

/// The type of the NAL unit that holds a slice of an IDR picture, a keyframe.
const NAL_IDR: u8 = 5;

/// The NAL units of one sample, each without the four bytes of its length.
fn nal_units(sample: &[u8]) -> Vec<&[u8]> {
    let mut units = Vec::new();
    let mut rest = sample;
    while !rest.is_empty() {
        let (length, tail) = rest.split_at(4);
        let (nal, tail) = tail.split_at(u32::from_be_bytes(length.try_into().unwrap()) as usize);
        units.push(nal);
        rest = tail;
    }
    units
}

/// Whether a sample holds a slice of an IDR picture.
fn holds_idr(sample: &[u8]) -> bool {
    nal_units(sample)
        .iter()
        .any(|nal| nal.first().is_some_and(|header| header & 0x1F == NAL_IDR))
}

/// Decodes the video as a player does: the parameter sets of the sample entry, then each sample's NAL units, one
/// access unit a sample.
fn decode(mp4: &Mp4) -> Vec<Picture> {
    let mut decoder = Decoder::new().unwrap();
    let mut pictures = Vec::new();
    let mut packet = Vec::new();
    annex_b(&mut packet, &mp4.sps);
    annex_b(&mut packet, &mp4.pps);
    for sample in mp4.samples() {
        for nal in nal_units(sample) {
            annex_b(&mut packet, nal);
        }
        if let Some(picture) = decoder.decode(&packet).unwrap() {
            pictures.push(Picture::of(&picture));
        }
        packet.clear();
    }
    for picture in decoder.flush_remaining().unwrap() {
        pictures.push(Picture::of(&picture));
    }
    pictures
}

const RAMP_FRAMES: usize = 25;

/// The capture times of the ramp: a frame every 100 ms, but the third repeats the second's time, as a frame the loop
/// writes again does, and the fifth is stamped before the fourth, as a clock stepped back does.
fn ramp_ms(index: usize) -> u64 {
    match index {
        2 => 1100,
        4 => 1250,
        _ => 1000 + 100 * u64::try_from(index).unwrap(),
    }
}

#[test]
fn a_raw_recording_decodes_to_every_frame_it_was_written() {
    let dir = tempfile::tempdir().unwrap();
    let mut recording = H264Recording::start(dir.path(), RAMP_WIDTH, RAMP_HEIGHT).unwrap();
    assert!(recording.raw());
    let mut sink = recording.take_sink().expect("a raw recording has a sink");
    assert!(recording.take_sink().is_none(), "the sink is taken once");
    for index in 0..RAMP_FRAMES {
        sink.write_frame(&ramp_frame(index)).unwrap();
    }
    let stopped = recording.stop().unwrap();
    assert_eq!(
        stopped,
        Stopped {
            frames: RAMP_FRAMES,
            first_frame_ms: 1000
        }
    );

    let index = decode_video_index(&std::fs::read(dir.path().join(VIDEO_INDEX_FILE)).unwrap()).unwrap();
    assert_eq!(index.frame_rate, FRAME_RATE);
    assert_eq!(index.frames_ms.len(), RAMP_FRAMES);
    assert_eq!(index.frames_ms[..6], [1000, 1100, 1100, 1300, 1300, 1500]);
    assert!(index.frames_ms.is_sorted(), "the index is not sorted: {:?}", index.frames_ms);
    assert!(!dir.path().join(format!("{VIDEO_INDEX_FILE}.partial")).exists());

    let mp4 = parse(&std::fs::read(dir.path().join(VIDEO_FILE)).unwrap());
    assert_eq!((mp4.width, mp4.height), (64, 32));
    assert_eq!(mp4.samples().count(), RAMP_FRAMES, "one sample a frame");
    assert_eq!(mp4.keyframes(), [0, 10, 20], "a keyframe every {FRAME_RATE} frames");
    for (fragment, first) in mp4.fragments.iter().zip(mp4.keyframes()) {
        assert_eq!(fragment.decode_time, u64::try_from(first).unwrap() * u64::from(1000 / FRAME_RATE));
        // The sync flags follow the bitstream: a fragment's first sample holds an IDR slice, and no other one does.
        let idr: Vec<bool> = fragment.samples.iter().map(|sample| holds_idr(sample)).collect();
        let mut want = vec![false; idr.len()];
        want[0] = true;
        assert_eq!(idr, want, "the IDR samples of the fragment at {first}");
    }

    let pictures = decode(&mp4);
    assert_eq!(pictures.len(), RAMP_FRAMES, "the decoded pictures");
    for (index, picture) in pictures.iter().enumerate() {
        assert_eq!((picture.width, picture.height), (64, 32));
        let want = ramp_luma(index);
        assert!(
            (picture.mean_luma - want).abs() < 1.0,
            "frame {index} decodes to a mean luma of {}, not {want}",
            picture.mean_luma
        );
    }
    assert!(
        pictures.windows(2).all(|pair| pair[0].mean_luma < pair[1].mean_luma),
        "the decoded ramp is not monotonic: {pictures:?}"
    );
}

/// A frame of fine grey detail, as text is: a keyframe of it carries real picture data, unlike one of flat grey.
fn textured(width: usize, height: usize) -> Frame {
    let mut frame = grey(width, height, 0, 1000);
    for row in 0..height {
        for (column, pixel) in frame.row_mut(row).chunks_exact_mut(BYTES_PER_PIXEL).enumerate() {
            let level = u8::try_from((column * 37 + row * 91 + column * row * 13) % 256).unwrap();
            pixel.fill(level);
        }
    }
    frame
}

/// An unchanged screen is the same frame written again, once a tick, which the encoder codes as skipped
/// macroblocks: every repeat costs a small part of the keyframe and decodes to the same picture.
#[test]
fn a_repeated_frame_costs_little_and_decodes_to_the_same_picture() {
    const REPEATS: usize = 6;
    let dir = tempfile::tempdir().unwrap();
    let mut recording = H264Recording::start(dir.path(), RAMP_WIDTH, RAMP_HEIGHT).unwrap();
    let mut sink = recording.take_sink().unwrap();
    let mut frame = textured(RAMP_WIDTH, RAMP_HEIGHT);
    for index in 0..=REPEATS {
        frame.at = UNIX_EPOCH + Duration::from_millis(ramp_ms(index));
        sink.write_frame(&frame).unwrap();
    }
    assert_eq!(recording.stop().unwrap().frames, REPEATS + 1);

    let mp4 = parse(&std::fs::read(dir.path().join(VIDEO_FILE)).unwrap());
    let sizes: Vec<usize> = mp4.samples().map(<[u8]>::len).collect();
    assert_eq!(sizes.len(), REPEATS + 1, "one sample a frame");
    assert_eq!(mp4.keyframes(), [0]);
    assert!(
        sizes[1..].iter().all(|&size| size * 4 < sizes[0]),
        "a repeat is not far smaller than the keyframe: {sizes:?}"
    );
    let pictures = decode(&mp4);
    assert_eq!(pictures.len(), REPEATS + 1, "the decoded pictures");
    assert!(
        pictures
            .iter()
            .all(|picture| (picture.mean_luma - pictures[0].mean_luma).abs() < 1e-9),
        "a repeat decodes to another picture: {pictures:?}"
    );
}

/// openh264 refuses a frame larger than its level 5.2 at the first encode, so the start refuses the screen before.
#[test]
fn a_screen_larger_than_the_encoder_takes_is_refused_at_the_start() {
    let dir = tempfile::tempdir().unwrap();
    let Err(error) = H264Recording::start(dir.path(), 3842, 100) else {
        panic!("a 3842 pixel wide screen was taken");
    };
    assert!(format!("{error:#}").contains("3840x2160"), "{error:#}");

    // A portrait 4K screen is within the encoder's limit, which the encoder checks at the first frame.
    let mut portrait = H264Recording::start(dir.path(), 2160, 3840).unwrap();
    let mut sink = portrait.take_sink().unwrap();
    sink.write_frame(&grey(2160, 3840, BACKGROUND, 1000)).unwrap();
    assert_eq!(portrait.stop().unwrap().frames, 1);
    let mp4 = parse(&std::fs::read(dir.path().join(VIDEO_FILE)).unwrap());
    assert_eq!((mp4.width, mp4.height), (2160, 3840));
    assert_eq!(decode(&mp4).len(), 1, "the decoded pictures");
}

/// The encoder takes an even size only, so an odd screen loses its last column and row.
#[test]
fn an_odd_screen_is_recorded_one_column_and_row_smaller() {
    let dir = tempfile::tempdir().unwrap();
    let mut recording = H264Recording::start(dir.path(), 33, 17).unwrap();
    let mut sink = recording.take_sink().unwrap();
    for index in 0..3 {
        sink.write_frame(&grey(33, 17, ramp(index), ramp_ms(index))).unwrap();
    }
    assert!(
        sink.write_frame(&grey(32, 16, 0, 2000)).is_err(),
        "a frame of another size was taken"
    );
    assert_eq!(recording.stop().unwrap().frames, 3);
    let mp4 = parse(&std::fs::read(dir.path().join(VIDEO_FILE)).unwrap());
    assert_eq!((mp4.width, mp4.height), (32, 16));
    let pictures = decode(&mp4);
    assert_eq!(pictures.len(), 3);
    assert!(pictures.iter().all(|picture| (picture.width, picture.height) == (32, 16)));
}

#[test]
fn a_stopped_recording_takes_no_frame() {
    let dir = tempfile::tempdir().unwrap();
    let mut recording = H264Recording::start(dir.path(), 32, 16).unwrap();
    let mut sink = recording.take_sink().unwrap();
    sink.write_frame(&grey(32, 16, 100, 1000)).unwrap();
    recording.stop().unwrap();
    assert!(
        sink.write_frame(&grey(32, 16, 100, 1100)).is_err(),
        "a stopped recording took another frame"
    );
}

#[test]
fn a_recording_without_a_frame_says_so_and_leaves_no_index() {
    let dir = tempfile::tempdir().unwrap();
    let mut recording = H264Recording::start(dir.path(), 32, 16).unwrap();
    let error = recording.stop().unwrap_err();
    assert!(format!("{error:#}").contains("no frame"), "{error:#}");
    assert!(!dir.path().join(VIDEO_INDEX_FILE).exists());
    assert!(!dir.path().join(VIDEO_FILE).exists());
}
