use std::fs;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use super::*;
use crate::capture::BYTES_PER_PIXEL;

/// Something with the texture of a UI: flat panels, hard edges and a few odd pixels, which is what the encoder's
/// palette and predictor paths see on a real screen. The accent shows only below row 12.
pub(crate) fn ui_frame(width: usize, height: usize, accent: u8) -> Frame {
    let mut frame = Frame::default();
    frame.resize(width, height);
    for (index, pixel) in frame.pix.as_chunks_mut::<BYTES_PER_PIXEL>().0.iter_mut().enumerate() {
        let (x, y) = (index % width, index / width);
        let bgr = match (y, x) {
            (0..12, _) => [0x30, 0x2d, 0x2b],
            (_, x) if x < width / 3 => [0x22, 0x1f, 0x1e],
            _ => [u8::try_from(x * 7 % 256).unwrap(), u8::try_from(y * 3 % 256).unwrap(), accent],
        };
        pixel[..3].copy_from_slice(&bgr);
    }
    frame
}

/// Decodes a WebP to tightly packed RGB, whatever alpha its header claims.
fn decode_rgb(document: &[u8]) -> (u32, u32, Vec<u8>) {
    let mut decoder = image_webp::WebPDecoder::new(std::io::Cursor::new(document)).unwrap();
    let (width, height) = decoder.dimensions();
    let mut pixels = vec![0; decoder.output_buffer_size().unwrap()];
    decoder.read_image(&mut pixels).unwrap();
    if decoder.has_alpha() {
        pixels = pixels
            .as_chunks::<4>()
            .0
            .iter()
            .flat_map(|pixel| [pixel[0], pixel[1], pixel[2]])
            .collect();
    }
    (width, height, pixels)
}

#[test]
fn a_still_decodes_to_exactly_the_frames_pixels() {
    let frame = ui_frame(97, 61, 0x80);
    let encoded = encode(&frame).unwrap();
    assert_eq!(&encoded[0..4], b"RIFF");
    assert_eq!(&encoded[8..16], b"WEBPVP8L", "a simple lossless WebP");
    let size = u32::from_le_bytes(encoded[4..8].try_into().unwrap());
    assert_eq!(size as usize, encoded.len() - 8, "the RIFF size");
    assert_eq!(decode_rgb(&encoded), (97, 61, frame.to_rgb()));
}

/// A snapshot whose grab and tree read used up its budget arrives with its deadline passed. A picture already
/// grabbed is still queued while there is room, and only a full queue refuses it.
#[test]
fn a_spent_budget_drops_a_still_only_when_the_queue_is_full() {
    let spent = Instant::now().checked_sub(Duration::from_millis(1)).unwrap();
    // Nothing drains the queue, so it holds what was submitted.
    let writer = Writer::parked();
    // A screen per slot, since a still of the previous screen names that still's file and takes no slot.
    for slot in 0..QUEUE_DEPTH {
        let name = format!("{slot}.webp");
        let named = writer
            .submit(spent, name.clone(), ui_frame(8, 16, u8::try_from(slot).unwrap()))
            .unwrap();
        assert_eq!(named, name, "the still for slot {slot} of a free queue");
    }
    let refused = ui_frame(8, 16, 0xf0);
    assert!(
        writer.submit(spent, "refused.webp".to_owned(), refused.clone()).is_err(),
        "a full queue took a still past a spent budget"
    );
    // The refused still is not on disk, so the next still of its screen must not name it.
    let again = writer.submit(spent, "again.webp".to_owned(), refused);
    assert!(again.is_err(), "a still of the refused screen was answered {again:?}");
}

fn collecting() -> (Failed, Arc<Mutex<Vec<String>>>) {
    let failures = Arc::new(Mutex::new(Vec::new()));
    let seen = failures.clone();
    let failed: Failed = Box::new(move |name, error| {
        lock(&seen).push(format!("{name}: {error:#}"));
    });
    (failed, failures)
}

#[test]
fn a_still_of_an_unchanged_screen_names_the_previous_file() {
    let dir = tempfile::tempdir().unwrap();
    let (failed, failures) = collecting();
    let writer = Writer::new(dir.path(), failed);
    let first = ui_frame(64, 48, 0x10);
    let frames = [first.clone(), first.clone(), ui_frame(64, 48, 0x90), first];
    let named: Vec<String> = frames
        .into_iter()
        .zip(["a", "b", "c", "d"])
        .map(|(frame, name)| {
            let deadline = Instant::now() + Duration::from_secs(10);
            writer.submit(deadline, format!("{name}.webp"), frame).unwrap()
        })
        .collect();
    let (files, repeats) = writer.close();
    assert!(lock(&failures).is_empty(), "{:?}", lock(&failures));
    assert_eq!(named, ["a.webp", "a.webp", "c.webp", "d.webp"]);
    assert_eq!((files, repeats), (3, 1));
    let read = |name: &str| fs::read(dir.path().join(name)).unwrap();
    assert_eq!(read("a.webp"), read("d.webp"));
    assert_ne!(read("a.webp"), read("c.webp"));
    let mut names: Vec<String> = fs::read_dir(dir.path())
        .unwrap()
        .map(|entry| entry.unwrap().file_name().into_string().unwrap())
        .collect();
    names.sort();
    assert_eq!(names, ["a.webp", "c.webp", "d.webp"]);
}

/// A still that could not be written is not on disk, so the next still of its screen gets a file of its own rather
/// than naming the missing one.
#[test]
fn a_still_that_could_not_be_written_is_not_named_again() {
    let dir = tempfile::tempdir().unwrap();
    let (reported, failed) = bounded(1);
    let writer = Writer::new(
        dir.path(),
        Box::new(move |name, _| {
            let _ = reported.send(name.to_owned());
        }),
    );
    let frame = ui_frame(32, 16, 0x40);
    let deadline = Instant::now() + Duration::from_secs(10);
    writer.submit(deadline, "missing/a.webp".to_owned(), frame.clone()).unwrap();
    assert_eq!(failed.recv_timeout(Duration::from_secs(10)).unwrap(), "missing/a.webp");
    let named = writer.submit(deadline, "b.webp".to_owned(), frame);
    let (files, repeats) = writer.close();
    assert_eq!(named.unwrap(), "b.webp");
    assert!(dir.path().join("b.webp").is_file());
    assert_eq!((files, repeats), (1, 0));
}
