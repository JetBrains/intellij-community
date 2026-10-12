use std::io::{Read, Write};
use std::os::unix::net::UnixStream;
use std::thread;
use std::time::{Duration, Instant};

use x11rb::protocol::xproto::{BackingStore, Depth, EventMask, Format, Visualtype};
use x11rb::x11_utils::Serialize;

use super::*;

fn xvfb_screen() -> Screen {
    Screen {
        root: 0x100,
        default_colormap: 0x20,
        white_pixel: 0x00ff_ffff,
        black_pixel: 0,
        current_input_masks: EventMask::NO_EVENT,
        width_in_pixels: 64,
        height_in_pixels: 48,
        width_in_millimeters: 17,
        height_in_millimeters: 13,
        min_installed_maps: 1,
        max_installed_maps: 1,
        root_visual: 33,
        backing_stores: BackingStore::NOT_USEFUL,
        save_unders: false,
        root_depth: 24,
        allowed_depths: vec![Depth {
            depth: 24,
            visuals: vec![Visualtype {
                visual_id: 33,
                class: VisualClass::TRUE_COLOR,
                bits_per_rgb_value: 8,
                colormap_entries: 256,
                red_mask: 0x00ff_0000,
                green_mask: 0xff00,
                blue_mask: 0xff,
            }],
        }],
    }
}

fn xvfb_setup() -> Setup {
    Setup {
        status: 1,
        protocol_major_version: 11,
        protocol_minor_version: 0,
        length: 0,
        release_number: 0,
        resource_id_base: 0x0020_0000,
        resource_id_mask: 0x001f_ffff,
        motion_buffer_size: 0,
        maximum_request_length: 65535,
        image_byte_order: ImageOrder::LSB_FIRST,
        bitmap_format_bit_order: ImageOrder::LSB_FIRST,
        bitmap_format_scanline_unit: 32,
        bitmap_format_scanline_pad: 32,
        min_keycode: 8,
        max_keycode: 255,
        vendor: b"silent".to_vec(),
        pixmap_formats: vec![
            Format {
                depth: 1,
                bits_per_pixel: 1,
                scanline_pad: 32,
            },
            Format {
                depth: 24,
                bits_per_pixel: 32,
                scanline_pad: 32,
            },
        ],
        roots: vec![xvfb_screen()],
    }
}

#[test]
fn xvfbs_layout_is_read_and_every_other_is_refused() {
    let setup = xvfb_setup();
    check_pixel_format(&setup, &setup.roots[0]).expect("Xvfb's own layout is read");
    type Mutation = fn(&mut Setup);
    let refused: [(&str, Mutation); 5] = [
        ("most significant byte first", |setup| {
            setup.image_byte_order = ImageOrder::MSB_FIRST;
        }),
        ("16 bits per pixel", |setup| {
            setup.pixmap_formats[1].bits_per_pixel = 16;
        }),
        ("RGB masks in the other order", |setup| {
            let visual = &mut setup.roots[0].allowed_depths[0].visuals[0];
            (visual.red_mask, visual.blue_mask) = (0xff, 0x00ff_0000);
        }),
        ("a palette visual", |setup| {
            setup.roots[0].allowed_depths[0].visuals[0].class = VisualClass::PSEUDO_COLOR;
        }),
        ("a root visual the screen does not list", |setup| {
            setup.roots[0].root_visual = 99;
        }),
    ];
    for (label, mutate) in refused {
        let mut setup = xvfb_setup();
        mutate(&mut setup);
        assert!(
            check_pixel_format(&setup, &setup.roots[0]).is_err(),
            "a display with {label} was accepted"
        );
    }
}

#[test]
fn a_missing_display_is_an_error_rather_than_a_hang() {
    let start = Instant::now();
    let error = X11Source::open(":7357").err().expect("a display nobody serves opened");
    assert!(format!("{error:#}").contains(":7357"), "{error:#}");
    assert!(start.elapsed() < OPEN_TIMEOUT + Duration::from_secs(1));
}

/// An X server that completes the handshake, answers the MIT-SHM query with "absent", and then never answers
/// anything else: the X server of a guest that stopped answering. With `setup` false it never even answers the
/// handshake.
fn serve_silently(mut server: UnixStream, setup: bool) {
    let mut head = [0; 12];
    if server.read_exact(&mut head).is_err() {
        return;
    }
    let pad = |n: u16| (usize::from(n) + 3) & !3;
    let skip = pad(u16::from_le_bytes([head[6], head[7]])) + pad(u16::from_le_bytes([head[8], head[9]]));
    if server.read_exact(&mut vec![0; skip]).is_err() {
        return;
    }
    if !setup {
        let _ = std::io::copy(&mut server, &mut std::io::sink());
        return;
    }
    let mut reply = xvfb_setup().serialize();
    let length = u16::try_from((reply.len() - 8) / 4).unwrap();
    reply[6..8].copy_from_slice(&length.to_le_bytes());
    if server.write_all(&reply).is_err() {
        return;
    }
    for sequence in 1_u16.. {
        let mut request = [0; 4];
        if server.read_exact(&mut request).is_err() {
            return;
        }
        let length = usize::from(u16::from_le_bytes([request[2], request[3]])) * 4;
        if server.read_exact(&mut vec![0; length.saturating_sub(4)]).is_err() {
            return;
        }
        // QueryExtension, which the MIT-SHM check asks: absent, so the grabs take the plain path.
        if request[0] == 98 {
            let mut answer = [0; 32];
            answer[0] = 1;
            answer[2..4].copy_from_slice(&sequence.to_le_bytes());
            if server.write_all(&answer).is_err() {
                return;
            }
        }
    }
}

fn open_silent(setup: bool) -> anyhow::Result<X11Source> {
    let (client, server) = UnixStream::pair().unwrap();
    thread::spawn(move || serve_silently(server, setup));
    let shutter = Shutter::Unix(client.try_clone().unwrap());
    let (stream, _) = DefaultStream::from_unix_stream(client).unwrap();
    open_stream(stream, shutter, Vec::new(), Vec::new(), 0, "silent")
}

/// A grab from a server that stopped answering returns at its deadline, and leaves nothing waiting: not the grab,
/// not a grab queued behind it, not the drop. The recorder's frame loop and every snapshot share the source, and a
/// lane waits on the snapshots.
#[test]
fn a_grab_from_a_server_that_stopped_answering_ends_at_its_deadline() {
    let source = std::sync::Arc::new(open_silent(true).unwrap());
    assert_eq!(source.size(), (64, 48));
    assert!(source.plain_reason().is_some(), "the silent server has no MIT-SHM");

    let start = Instant::now();
    let stuck = {
        let source = source.clone();
        thread::spawn(move || source.grab(Instant::now() + Duration::from_millis(400), &mut Frame::default()))
    };
    thread::sleep(Duration::from_millis(50));
    let queued = source.grab(Instant::now() + Duration::from_millis(100), &mut Frame::default());
    assert!(
        queued.is_err() && start.elapsed() < Duration::from_millis(300),
        "a grab queued behind a stuck one answered {queued:?} after {:?}",
        start.elapsed()
    );
    let stuck = stuck.join().unwrap();
    assert!(start.elapsed() < Duration::from_secs(3), "the stuck grab outlived its deadline");
    assert!(stuck.is_err() && source.broken().is_some(), "the stuck grab answered {stuck:?}");
    assert!(
        source.grab(Instant::now() + Duration::from_secs(5), &mut Frame::default()).is_err(),
        "a broken source grabbed again"
    );
    let dropped = Instant::now();
    drop(std::sync::Arc::into_inner(source).expect("the grabs are done"));
    assert!(dropped.elapsed() < Duration::from_secs(2), "dropping the broken source hung");
}

/// A working source whose server stops answering later still closes: dropping it waits on no round trip.
#[test]
fn dropping_a_source_whose_server_stopped_answering_returns() {
    let source = open_silent(true).unwrap();
    let start = Instant::now();
    drop(source);
    assert!(start.elapsed() < Duration::from_secs(1), "closing took {:?}", start.elapsed());
}

#[test]
fn a_server_that_never_answers_the_handshake_is_given_up_on() {
    let start = Instant::now();
    assert!(open_silent(false).is_err(), "a server that never answered the handshake opened");
    assert!(
        start.elapsed() < OPEN_TIMEOUT + Duration::from_secs(1),
        "giving up took {:?}",
        start.elapsed()
    );
}

/// A real X server, when the machine running the test has one. The Linux worker's guest does; a Bazel sandbox does
/// not, so the test says so and passes.
#[test]
fn a_real_display_yields_frames_of_its_roots_size() {
    let Some(display) = std::env::var("DISPLAY").ok().filter(|display| !display.is_empty()) else {
        eprintln!("skipped: no DISPLAY, and this test reads a real X server");
        return;
    };
    let source = match X11Source::open(&display) {
        Ok(source) => source,
        Err(error) => {
            eprintln!("skipped: DISPLAY={display} is set and cannot be read: {error:#}");
            return;
        }
    };
    eprintln!("MIT-SHM: {:?} (none means in use)", source.plain_reason());
    let mut frame = Frame::default();
    for _ in 0..2 {
        source.grab(Instant::now() + Duration::from_secs(10), &mut frame).unwrap();
    }
    let (width, height) = source.size();
    assert_eq!((frame.width, frame.height), (usize::from(width), usize::from(height)));
    assert_eq!(frame.pix.len(), frame.width * frame.height * BYTES_PER_PIXEL);
}
