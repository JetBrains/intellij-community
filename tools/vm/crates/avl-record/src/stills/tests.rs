use std::fs;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use super::*;
use crate::testing::ui_frame;

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

/// The RIFF container of a simple WebP file and its one chunk, `VP8L` for a lossless picture and `VP8 ` for a lossy
/// one: the header a browser's decoder reads first.
fn require_simple_webp(encoded: &[u8], chunk: &[u8; 4]) {
    assert_eq!(&encoded[0..4], b"RIFF");
    assert_eq!(&encoded[8..12], b"WEBP");
    assert_eq!(
        &encoded[12..16],
        chunk,
        "a simple WebP with the {:?} chunk",
        String::from_utf8_lossy(chunk)
    );
    let size = u32::from_le_bytes(encoded[4..8].try_into().unwrap());
    assert_eq!(size as usize, encoded.len() - 8, "the RIFF size");
}

#[test]
fn a_lossless_still_decodes_to_exactly_the_frames_pixels() {
    let frame = ui_frame(97, 61, 0x80);
    let encoded = encode(&frame, Picture::LosslessWebp).unwrap();
    require_simple_webp(&encoded, b"VP8L");
    assert_eq!(decode_rgb(&encoded), (97, 61, frame.to_rgb()));
}

/// A lossy still is an AVIF file. The tests of `avif` check the picture inside it.
#[cfg(unix)]
#[test]
fn a_lossy_still_is_an_avif_file() {
    let encoded = encode(&ui_frame(97, 61, 0x80), Picture::LossyAvif).unwrap();
    assert_eq!(&encoded[4..12], b"ftypavif");
}

/// A build without libaom refuses an AVIF picture, so the writer reports the still instead of writing a wrong format.
#[cfg(not(unix))]
#[test]
fn a_build_without_libaom_refuses_an_avif_picture() {
    let refused = encode(&ui_frame(8, 8, 0x80), Picture::LossyAvif).unwrap_err();
    assert_eq!(format!("{refused:#}"), "this build of the recorder has no AVIF encoder");
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

/// The writer compares the grabbed frames, not the encoded files, so a still of an unchanged screen names the previous
/// file in both encodings.
#[test]
fn a_still_of_an_unchanged_screen_names_the_previous_file() {
    a_still_of_an_unchanged_screen_names_the_previous_file_in(StillEncoding::Lossless);
    #[cfg(unix)]
    a_still_of_an_unchanged_screen_names_the_previous_file_in(StillEncoding::Lossy);
}

fn a_still_of_an_unchanged_screen_names_the_previous_file_in(encoding: StillEncoding) {
    let dir = tempfile::tempdir().unwrap();
    let (failed, failures) = collecting();
    let writer = Writer::new(dir.path(), encoding, failed);
    let first = ui_frame(64, 48, 0x10);
    let frames = [first.clone(), first.clone(), ui_frame(64, 48, 0x90), first];
    let named: Vec<String> = frames
        .into_iter()
        .zip(["a", "b", "c", "d"])
        .map(|(frame, name)| {
            let deadline = Instant::now() + Duration::from_secs(10);
            writer.submit(deadline, format!("{name}.{}", encoding.extension()), frame).unwrap()
        })
        .collect();
    let closed = writer.close();
    assert!(lock(&failures).is_empty(), "{encoding}: {:?}", lock(&failures));
    let file = |name: &str| format!("{name}.{}", encoding.extension());
    assert_eq!(named, ["a", "a", "c", "d"].map(file), "{encoding}");
    assert_eq!((closed.files, closed.repeats), (3, 1), "{encoding}");
    let read = |name: &str| fs::read(dir.path().join(file(name))).unwrap();
    assert_eq!(read("a"), read("d"), "{encoding}: one screen, two files");
    assert_ne!(read("a"), read("c"), "{encoding}: two screens, one file");
    let mut names: Vec<String> = fs::read_dir(dir.path())
        .unwrap()
        .map(|entry| entry.unwrap().file_name().into_string().unwrap())
        .collect();
    names.sort();
    assert_eq!(names, ["a", "c", "d"].map(file));
}

/// A still that could not be written is not on disk, so the next still of its screen gets a file of its own rather
/// than naming the missing one.
#[test]
fn a_still_that_could_not_be_written_is_not_named_again() {
    let dir = tempfile::tempdir().unwrap();
    let (reported, failed) = bounded(1);
    let writer = Writer::new(
        dir.path(),
        StillEncoding::Lossless,
        Box::new(move |name, _| {
            let _ = reported.send(name.to_owned());
        }),
    );
    let frame = ui_frame(32, 16, 0x40);
    let deadline = Instant::now() + Duration::from_secs(10);
    writer.submit(deadline, "missing/a.webp".to_owned(), frame.clone()).unwrap();
    assert_eq!(failed.recv_timeout(Duration::from_secs(10)).unwrap(), "missing/a.webp");
    let named = writer.submit(deadline, "b.webp".to_owned(), frame);
    let closed = writer.close();
    assert_eq!(named.unwrap(), "b.webp");
    assert!(dir.path().join("b.webp").is_file());
    assert_eq!((closed.files, closed.repeats), (1, 0));
}

/// A stand-in for a lossy encoder with a bug: it panics on a lossy picture and encodes a lossless one.
#[cfg(unix)]
fn lossy_panics(frame: &Frame, picture: Picture) -> anyhow::Result<Vec<u8>> {
    assert!(picture != Picture::LossyAvif, "the lossy encoder broke");
    encode(frame, picture)
}

/// A stand-in for an encoder that panics on every picture.
fn every_picture_panics(_: &Frame, picture: Picture) -> anyhow::Result<Vec<u8>> {
    panic!("the {picture} encoder broke")
}

/// A panic of the lossy encoder costs a still its loss, not its file. The still is a lossless AVIF picture under its own
/// name, the failure names the file, and the stills queued behind it are still written.
#[cfg(unix)]
#[test]
fn a_still_whose_lossy_encoder_panics_is_written_lossless() {
    let dir = tempfile::tempdir().unwrap();
    let (failed, failures) = collecting();
    let writer = Writer::with_encoder(dir.path(), StillEncoding::Lossy, Arc::new(lossy_panics), 2, failed);
    let deadline = Instant::now() + Duration::from_secs(10);
    for (name, accent) in [("a", 0x10), ("b", 0x90)] {
        let named = writer.submit(deadline, format!("{name}.avif"), ui_frame(32, 16, accent)).unwrap();
        assert_eq!(named, format!("{name}.avif"));
    }
    assert_eq!(
        writer.close(),
        Closed {
            files: 2,
            repeats: 0,
            panicked: 0
        }
    );
    let mut reported = lock(&failures).clone();
    reported.sort();
    assert_eq!(
        reported,
        ["a.avif", "b.avif"]
            .map(|name| format!("{name}: the still is lossless AVIF instead: the lossy AVIF encoder panicked: the lossy encoder broke"))
    );
    for name in ["a.avif", "b.avif"] {
        assert_eq!(&fs::read(dir.path().join(name)).unwrap()[4..12], b"ftypavif", "{name}");
    }
}

/// A still that no encoder could write is not on disk. One report names both causes, and the next still of its screen
/// gets a file of its own rather than naming the missing one. The encoder's thread survives the panics.
#[test]
fn a_still_that_every_encoder_panics_on_is_reported_and_not_named_again() {
    let dir = tempfile::tempdir().unwrap();
    let (reported, failures) = bounded(4);
    let writer = Writer::with_encoder(
        dir.path(),
        StillEncoding::Lossy,
        Arc::new(every_picture_panics),
        2,
        Box::new(move |name, error| {
            let _ = reported.send(format!("{name}: {error:#}"));
        }),
    );
    let frame = ui_frame(32, 16, 0x40);
    let deadline = Instant::now() + Duration::from_secs(10);
    writer.submit(deadline, "a.avif".to_owned(), frame.clone()).unwrap();
    assert_eq!(
        failures.recv_timeout(Duration::from_secs(10)).unwrap(),
        "a.avif: the lossy AVIF encoder panicked: the lossy AVIF encoder broke; \
         the lossless AVIF encoder panicked: the lossless AVIF encoder broke"
    );
    let named = writer.submit(deadline, "b.avif".to_owned(), frame);
    assert_eq!(named.unwrap(), "b.avif", "a still of the screen of a missing file");
    assert_eq!(
        writer.close(),
        Closed {
            files: 0,
            repeats: 0,
            panicked: 0
        }
    );
    assert_eq!(
        failures.try_iter().collect::<Vec<_>>(),
        ["b.avif: the lossy AVIF encoder panicked: the lossy AVIF encoder broke; \
          the lossless AVIF encoder panicked: the lossless AVIF encoder broke"],
        "the encoder's thread did not take the still after the panics"
    );
    assert_eq!(
        fs::read_dir(dir.path()).unwrap().count(),
        0,
        "a file of a still that no encoder wrote"
    );
}

/// A lossless still has no fallback: a failure of its encoder is the failure of the still.
#[test]
fn a_lossless_still_whose_encoder_panics_is_reported() {
    let dir = tempfile::tempdir().unwrap();
    let (reported, failures) = bounded(1);
    let writer = Writer::with_encoder(
        dir.path(),
        StillEncoding::Lossless,
        Arc::new(every_picture_panics),
        1,
        Box::new(move |name, error| {
            let _ = reported.send(format!("{name}: {error:#}"));
        }),
    );
    let deadline = Instant::now() + Duration::from_secs(10);
    writer.submit(deadline, "a.webp".to_owned(), ui_frame(32, 16, 0x40)).unwrap();
    assert_eq!(
        failures.recv_timeout(Duration::from_secs(10)).unwrap(),
        "a.webp: the lossless WebP encoder panicked: the lossless WebP encoder broke"
    );
    assert_eq!(writer.close().files, 0);
}

/// The encoders of a writer hold stills at once, never more than there are encoders, and `close` waits for every
/// still that they hold. The encoder waits for one token per still, so the test decides when each still ends.
#[test]
fn the_encoders_run_at_once_and_close_waits_for_every_still() {
    const WORKERS: usize = 3;
    assert!(
        (1..=MAX_WORKERS).contains(&worker_count()),
        "the encoders of this machine: {}",
        worker_count()
    );
    let dir = tempfile::tempdir().unwrap();
    let (open, tokens) = bounded::<()>(WORKERS + 1);
    let (busy, most) = (Arc::new(AtomicUsize::new(0)), Arc::new(AtomicUsize::new(0)));
    let encoder: Encoder = {
        let (busy, most) = (busy.clone(), most.clone());
        Arc::new(move |frame: &Frame, picture: Picture| {
            let holding = busy.fetch_add(1, Ordering::SeqCst) + 1;
            most.fetch_max(holding, Ordering::SeqCst);
            let token = tokens.recv_timeout(Duration::from_secs(10));
            busy.fetch_sub(1, Ordering::SeqCst);
            token.context("no token within 10 s")?;
            encode(frame, picture)
        })
    };
    let (failed, failures) = collecting();
    let writer = Writer::with_encoder(dir.path(), StillEncoding::Lossless, encoder, WORKERS, failed);
    let deadline = Instant::now() + Duration::from_secs(10);
    for slot in 0..=WORKERS {
        let name = format!("{slot}.webp");
        assert_eq!(
            writer
                .submit(deadline, name.clone(), ui_frame(8, 16, u8::try_from(slot).unwrap()))
                .unwrap(),
            name
        );
    }
    while busy.load(Ordering::SeqCst) < WORKERS && Instant::now() < deadline {
        thread::sleep(Duration::from_millis(5));
    }
    assert_eq!(busy.load(Ordering::SeqCst), WORKERS, "the stills that the encoders hold at once");

    let (closed, answer) = bounded(1);
    let closing = thread::spawn(move || {
        let _ = closed.send(writer.close());
    });
    // The encoders end their stills, and one of them takes the last still, which waits for the last token.
    for _ in 0..WORKERS {
        open.send(()).unwrap();
    }
    assert!(
        answer.recv_timeout(Duration::from_millis(200)).is_err(),
        "close returned while an encoder held a still"
    );
    open.send(()).unwrap();
    let closed = answer.recv_timeout(Duration::from_secs(10)).expect("close waits for ever");
    closing.join().unwrap();
    assert_eq!(
        closed,
        Closed {
            files: WORKERS + 1,
            repeats: 0,
            panicked: 0
        }
    );
    assert_eq!(most.load(Ordering::SeqCst), WORKERS, "the most stills held at once");
    assert!(lock(&failures).is_empty(), "{:?}", lock(&failures));
    assert_eq!(fs::read_dir(dir.path()).unwrap().count(), WORKERS + 1);
}

/// A panic outside the guarded encode ends its encoder, here a panic of the failure report. `close` still returns, and
/// it counts the encoder that ended with a panic, which the scenario records.
#[test]
fn close_counts_an_encoder_that_ended_with_a_panic() {
    let dir = tempfile::tempdir().unwrap();
    let writer = Writer::panicking(dir.path());
    let deadline = Instant::now() + Duration::from_secs(10);
    writer.submit(deadline, "a.webp".to_owned(), ui_frame(32, 16, 0x40)).unwrap();
    assert_eq!(
        writer.close(),
        Closed {
            files: 0,
            repeats: 0,
            panicked: 1
        }
    );
}
