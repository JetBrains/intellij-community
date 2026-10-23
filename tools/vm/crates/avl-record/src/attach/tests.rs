use std::fs;
use std::path::Path;

use avl_trace::bundle::{PARTIAL_SUFFIX, attach_path, bundle_file};
use avl_trace::otlp::{LogRecord, attr, event, lookup_str, span_id};
use avl_trace::protocol::{Command, VideoCodec, encode_command};
use pretty_assertions::assert_eq;

use super::*;
use crate::Options;
use crate::capture::Source;
use crate::ladder::{VideoPlan, Videographer};
use crate::stills::{QUEUE_DEPTH, Writer};
use crate::tests::harness::*;
use crate::video::{FrameSink, Recording, Stopped};

const SCENARIO: &str = r#"{"op":"scenario","name":"example","testClass":"AirExampleUiTest","lane":"UI"}"#;
const SPAN: &str = r#"{"op":"span","id":1,"kind":"journey","key":"journey:open","title":"Open the chat"}"#;
const END: &str = r#"{"op":"end","id":1,"status":"passed"}"#;
const DONE: &str = r#"{"op":"done","status":"passed"}"#;

fn attach_line(name: &str, mime: &str, file: &Path, span: u32) -> String {
    let command = Command::Attach(AttachCommand {
        name: name.to_owned(),
        mime: mime.to_owned(),
        file: file.to_string_lossy().into_owned(),
        span,
    });
    String::from_utf8(encode_command(&command).unwrap()).unwrap()
}

fn events(dir: &Path, name: &str) -> Vec<LogRecord> {
    records(dir).into_iter().filter(|record| record.event_name == name).collect()
}

/// Each `air.attachment` record as its span, title, media type and file.
fn attachments(dir: &Path) -> Vec<(String, String, String, String)> {
    events(dir, event::ATTACHMENT)
        .iter()
        .map(|record| {
            let text = |key| lookup_str(&record.attributes, key).unwrap_or_default().to_owned();
            (
                record.span_id.clone(),
                text(attr::ATTACHMENT_NAME),
                text(attr::ATTACHMENT_MIME),
                text(attr::ATTACHMENT_FILE),
            )
        })
        .collect()
}

/// Each `air.trace.error` record as `<source>: <message>`.
fn trace_errors(dir: &Path) -> Vec<String> {
    events(dir, event::TRACE_ERROR)
        .iter()
        .map(|record| {
            format!(
                "{}: {}",
                lookup_str(&record.attributes, attr::TRACE_ERROR_SOURCE).unwrap_or_default(),
                lookup_str(&record.attributes, attr::TRACE_ERROR_MESSAGE).unwrap_or_default()
            )
        })
        .collect()
}

fn want(rows: &[(u32, &str, &str, &str)]) -> Vec<(String, String, String, String)> {
    rows.iter()
        .map(|(span, name, mime, file)| (span_id(*span), (*name).to_owned(), (*mime).to_owned(), (*file).to_owned()))
        .collect()
}

/// A PNG of 4 x 3 pixels, each of them the RGB `pixel`.
fn png_of(pixel: [u8; 3]) -> Vec<u8> {
    let mut document = Vec::new();
    let mut encoder = png::Encoder::new(&mut document, 4, 3);
    encoder.set_color(png::ColorType::Rgb);
    let mut writer = encoder.write_header().unwrap();
    writer.write_image_data(&pixel.repeat(4 * 3)).unwrap();
    writer.finish().unwrap();
    document
}

/// The width and the height of a WebP file, after a check that it is a lossy one without alpha.
fn lossy_webp_size(document: &[u8]) -> (u32, u32) {
    assert_eq!(&document[8..16], b"WEBPVP8 ", "the picture is not lossy WebP");
    let decoder = image_webp::WebPDecoder::new(io::Cursor::new(document)).unwrap();
    assert!(!decoder.has_alpha(), "a picture of an attachment has an alpha channel");
    decoder.dimensions()
}

/// The names of the files in the `attach/` directory of the bundle, sorted.
fn attach_files(bundle: &Path) -> Vec<String> {
    let mut names: Vec<String> = fs::read_dir(bundle.join(ATTACH_DIR))
        .unwrap()
        .map(|entry| entry.unwrap().file_name().to_string_lossy().into_owned())
        .collect();
    names.sort();
    names
}

/// A raw video that the recorder starts and stops, and that takes no frame. A scenario with it has lossy stills.
struct SilentVideo;

impl Videographer for SilentVideo {
    fn plan(&self, _: Option<&dyn Source>, _: bool) -> VideoPlan {
        VideoPlan {
            codec: VideoCodec::H264,
            reason: String::new(),
            raw: false,
        }
    }

    fn start(&self, _: &Path, _: &VideoPlan, _: Option<&Frame>) -> anyhow::Result<Box<dyn Recording>> {
        Ok(Box::new(Self))
    }
}

impl Recording for SilentVideo {
    fn raw(&self) -> bool {
        false
    }

    fn take_sink(&mut self) -> Option<Box<dyn FrameSink>> {
        None
    }

    fn stop(&mut self) -> anyhow::Result<Stopped> {
        Ok(Stopped {
            frames: 0,
            first_frame_ms: 0,
        })
    }
}

/// The options of the harness with a video, so the stills are lossy.
fn options_with_video(h: &Harness) -> Options {
    let mut options = h.options();
    options.video = Box::new(SilentVideo);
    options
}

/// The lane's file leaves the run's directory and becomes the bundle's, numbered in the order it arrived, and its
/// record sits on the span the lane named. In a scenario without a video, a PNG keeps its bytes and its media type.
#[test]
fn an_attachment_moves_into_the_bundle_and_is_recorded_on_its_span() {
    let h = Harness::new();
    let lane_dir = h.root.join("run-1").join("attach");
    fs::create_dir_all(&lane_dir).unwrap();
    let (table, picture) = (lane_dir.join("1.TXT"), lane_dir.join("2"));
    fs::write(&table, "class count\n").unwrap();
    fs::write(&picture, [0x89, b'P', b'N', b'G']).unwrap();
    let mut session = h.session(h.options());
    let (first, second) = (
        attach_line("Start-up classes", "text/plain", &table, 1),
        attach_line("The chat", "image/png", &picture, 0),
    );
    h.feed(&mut session, &[&h.hello(false), SCENARIO, SPAN, &first, END, &second, DONE]);

    let bundle = h.example_bundle();
    assert_eq!(
        fs::read_to_string(bundle_file(&bundle, &attach_path(1, "txt"))).unwrap(),
        "class count\n"
    );
    assert_eq!(
        fs::read(bundle_file(&bundle, &attach_path(2, "bin"))).unwrap(),
        [0x89, b'P', b'N', b'G']
    );
    assert!(!table.exists() && !picture.exists(), "the lane's files stay in the run's directory");
    assert_eq!(
        attachments(&bundle),
        want(&[
            (1, "Start-up classes", "text/plain", "attach/0001.txt"),
            (0, "The chat", "image/png", "attach/0002.bin"),
        ])
    );
    assert_eq!(trace_errors(&bundle), Vec::<String>::new());
}

/// In a scenario with a video, a PNG becomes a lossy WebP picture of the same size, and its record names `image/webp`.
/// The lane file is removed, and the bundle keeps no PNG.
#[test]
fn a_png_attachment_becomes_a_lossy_webp_picture_in_a_scenario_with_a_video() {
    let h = Harness::new();
    let lane_dir = h.root.join("run-1").join("attach");
    fs::create_dir_all(&lane_dir).unwrap();
    let picture = lane_dir.join("1.png");
    let paint = fs::read(record_testdata("ide/editor.png")).unwrap();
    fs::write(&picture, &paint).unwrap();
    let mut session = h.session(options_with_video(&h));
    let line = attach_line("after open", "image/png", &picture, 1);
    h.feed(&mut session, &[&h.hello(false), SCENARIO, SPAN, &line, END, DONE]);

    let bundle = h.example_bundle();
    let mut frame = Frame::default();
    frame.set_png(&paint, SystemTime::UNIX_EPOCH).unwrap();
    let size = lossy_webp_size(&fs::read(bundle.join(ATTACH_DIR).join("0001.webp")).unwrap());
    assert_eq!(size, (u32::try_from(frame.width).unwrap(), u32::try_from(frame.height).unwrap()));
    assert_eq!(attach_files(&bundle), ["0001.webp"]);
    assert!(!picture.exists(), "the lane's file stays in the run's directory");
    assert_eq!(attachments(&bundle), want(&[(1, "after open", "image/webp", "attach/0001.webp")]));
    assert_eq!(trace_errors(&bundle), Vec::<String>::new());
}

/// Two PNG attachments of the same picture, one after the other, are one file: both records name the file of the
/// first. Another picture after them gets a file of its own. This holds for a WebP picture and for a kept PNG.
#[test]
fn equal_consecutive_png_attachments_name_one_file() {
    for (video, extension, mime) in [(true, "webp", "image/webp"), (false, "png", "image/png")] {
        let h = Harness::new();
        let lane_dir = h.root.join("run-1").join("attach");
        fs::create_dir_all(&lane_dir).unwrap();
        let files = ["1.png", "2.png", "3.png"].map(|name| lane_dir.join(name));
        fs::write(&files[0], png_of([0x10, 0x20, 0x30])).unwrap();
        fs::write(&files[1], png_of([0x10, 0x20, 0x30])).unwrap();
        fs::write(&files[2], png_of([0x40, 0x50, 0x60])).unwrap();
        let mut session = h.session(if video { options_with_video(&h) } else { h.options() });
        let lines = [
            attach_line("after open", "image/png", &files[0], 1),
            attach_line("after type", "image/png", &files[1], 1),
            attach_line("after send", "image/png", &files[2], 1),
        ];
        h.feed(
            &mut session,
            &[&h.hello(false), SCENARIO, SPAN, &lines[0], &lines[1], &lines[2], END, DONE],
        );

        let bundle = h.example_bundle();
        let (first, third) = (format!("attach/0001.{extension}"), format!("attach/0003.{extension}"));
        assert_eq!(
            attachments(&bundle),
            want(&[
                (1, "after open", mime, &first),
                (1, "after type", mime, &first),
                (1, "after send", mime, &third),
            ]),
            "a video: {video}"
        );
        assert_eq!(attach_files(&bundle), [format!("0001.{extension}"), format!("0003.{extension}")]);
        assert!(files.iter().all(|file| !file.exists()), "a lane file stays in the run's directory");
        assert_eq!(trace_errors(&bundle), Vec::<String>::new());
    }
}

/// In a scenario with a video, a PNG that does not decode moves into the bundle as it is, with its media type. One
/// `air.trace.error` says why it is no WebP picture.
#[test]
fn a_png_that_does_not_decode_is_kept_as_it_is_and_recorded() {
    let h = Harness::new();
    let lane_dir = h.root.join("run-1").join("attach");
    fs::create_dir_all(&lane_dir).unwrap();
    let broken = lane_dir.join("1.png");
    fs::write(&broken, [0x89, b'P', b'N', b'G']).unwrap();
    let mut session = h.session(options_with_video(&h));
    let line = attach_line("The chat", "image/png", &broken, 1);
    h.feed(&mut session, &[&h.hello(false), SCENARIO, SPAN, &line, END, DONE]);

    let bundle = h.example_bundle();
    assert_eq!(
        fs::read(bundle_file(&bundle, &attach_path(1, "png"))).unwrap(),
        [0x89, b'P', b'N', b'G']
    );
    assert!(!broken.exists(), "the lane's file stays in the run's directory");
    assert_eq!(attachments(&bundle), want(&[(1, "The chat", "image/png", "attach/0001.png")]));
    let errors = events(&bundle, event::TRACE_ERROR);
    assert_eq!(errors.len(), 1, "{:?}", trace_errors(&bundle));
    assert_eq!(errors[0].span_id, span_id(1));
    assert!(
        trace_errors(&bundle)[0]
            .starts_with("attach: attach/0001.png: the attachment stays a PNG and is no WebP picture: the PNG does not decode: "),
        "{:?}",
        trace_errors(&bundle)
    );
}

/// A PNG that the writer cannot queue before the budget of the command is spent moves into the bundle as it is. One
/// `air.trace.error` says why it is no WebP picture.
#[test]
fn a_png_that_the_full_queue_cannot_take_is_kept_as_it_is_and_recorded() {
    let h = Harness::new();
    let lane_dir = h.root.join("run-1").join("attach");
    fs::create_dir_all(&lane_dir).unwrap();
    let files: Vec<_> = (1..=QUEUE_DEPTH + 1)
        .map(|ordinal| lane_dir.join(format!("{ordinal}.png")))
        .collect();
    let documents: Vec<Vec<u8>> = (0_u8..).zip(&files).map(|(shade, _)| png_of([shade, shade, shade])).collect();
    for (file, document) in files.iter().zip(&documents) {
        fs::write(file, document).unwrap();
    }
    let mut options = options_with_video(&h);
    options.snap_budget = Duration::from_millis(1);
    let mut session = h.session(options);
    h.feed(&mut session, &[&h.hello(false), SCENARIO, SPAN]);
    session.scenario.as_mut().expect("the scenario is open").attachment_pictures = Some(Writer::parked());
    let lines: Vec<String> = files.iter().map(|file| attach_line("a step", "image/png", file, 1)).collect();
    h.feed(&mut session, &lines.iter().map(String::as_str).collect::<Vec<_>>());
    h.feed(&mut session, &[END, DONE]);

    let bundle = h.example_bundle();
    let last = QUEUE_DEPTH + 1;
    let files: Vec<String> = attachments(&bundle)
        .into_iter()
        .map(|(_, _, mime, file)| format!("{mime} {file}"))
        .collect();
    let mut want_files: Vec<String> = (1..last).map(|ordinal| format!("image/webp attach/{ordinal:04}.webp")).collect();
    want_files.push(format!("image/png attach/{last:04}.png"));
    assert_eq!(files, want_files);
    assert_eq!(
        fs::read(bundle.join(ATTACH_DIR).join(format!("{last:04}.png"))).unwrap(),
        documents[QUEUE_DEPTH]
    );
    assert_eq!(
        trace_errors(&bundle),
        [format!(
            "attach: attach/{last:04}.png: the attachment stays a PNG and is no WebP picture: the WebP encoders are \
             {QUEUE_DEPTH} pictures behind and the budget of the command is spent"
        )]
    );
}

/// A file outside the trace root, or a span the scenario never opened, is a lane mistake: a negative ack and a
/// protocol record, and the file stays where it is. A file that is not there is evidence loss and is no refusal.
#[test]
fn an_attachment_outside_the_root_or_its_scenario_is_refused_and_a_lost_one_is_recorded() {
    let h = Harness::new();
    let outside = tempfile::tempdir().unwrap();
    let foreign = outside.path().join("secret.txt");
    fs::write(&foreign, "not the lane's").unwrap();
    let escaping = h.root.join("run-1").join("..").join("..").join("escape.txt");
    let inside = h.root.join("run-1").join("attach").join("1.txt");
    fs::create_dir_all(inside.parent().unwrap()).unwrap();
    fs::write(&inside, "kept").unwrap();
    let missing = h.root.join("run-1").join("attach").join("9.txt");
    let mut session = h.session(h.options());
    let lines = [
        h.hello(false),
        SCENARIO.to_owned(),
        attach_line("foreign", "text/plain", &foreign, 0),
        attach_line("escaping", "text/plain", &escaping, 0),
        attach_line("unopened span", "text/plain", &inside, 7),
        attach_line("missing", "text/plain", &missing, 0),
        DONE.to_owned(),
    ];
    h.feed(&mut session, &lines.iter().map(String::as_str).collect::<Vec<_>>());

    let answered: Vec<(bool, u64)> = h
        .ack_lines()
        .iter()
        .map(|ack| (ack["ok"] == true, ack["line"].as_u64().unwrap()))
        .collect();
    assert_eq!(answered, [(true, 1), (true, 2), (false, 3), (false, 4), (false, 5), (true, 7)]);
    assert_eq!(fs::read_to_string(&foreign).unwrap(), "not the lane's");
    assert_eq!(fs::read_to_string(&inside).unwrap(), "kept");
    let bundle = h.example_bundle();
    assert_eq!(events(&bundle, event::ATTACHMENT), Vec::<LogRecord>::new());
    assert!(!bundle.join(ATTACH_DIR).join("0001.txt").exists());
    let errors: Vec<String> = events(&bundle, event::TRACE_ERROR)
        .iter()
        .map(|record| {
            format!(
                "{}: {}",
                lookup_str(&record.attributes, attr::TRACE_ERROR_SOURCE).unwrap_or_default(),
                lookup_str(&record.attributes, attr::TRACE_ERROR_MESSAGE).unwrap_or_default()
            )
        })
        .collect();
    assert_eq!(errors.len(), 4, "{errors:#?}");
    assert!(
        errors[0].starts_with("protocol: line 3: ") && errors[0].contains("not under the trace root"),
        "{errors:#?}"
    );
    assert!(
        errors[1].starts_with("protocol: line 4: ") && errors[1].contains("not under the trace root"),
        "{errors:#?}"
    );
    assert!(
        errors[2].starts_with("protocol: line 5: ") && errors[2].contains("never opened"),
        "{errors:#?}"
    );
    assert!(errors[3].starts_with("attach: attach/0001.txt: cannot move "), "{errors:#?}");
}

#[test]
fn the_bundle_keeps_a_short_alphanumeric_extension_only() {
    for (file, want) in [
        ("/r/a.webp", "webp"),
        ("/r/a.PNG", "png"),
        ("/r/a.tar.gz", "gz"),
        ("/r/a", "bin"),
        ("/r/a.", "bin"),
        ("/r/a.j-son", "bin"),
        ("/r/a.abcdefghijklmnopq", "bin"),
    ] {
        assert_eq!(extension_of(Path::new(file)), want, "{file}");
    }
}

/// A move whose rename fails takes the copy path. When the copy cannot take the final name either, the lane file
/// stays and no partial file is left.
#[test]
fn a_move_that_cannot_place_the_file_keeps_the_lane_file() {
    let dir = tempfile::tempdir().unwrap();
    let from = dir.path().join("lane.txt");
    fs::write(&from, "evidence").unwrap();
    // A rename onto an existing directory fails on every platform, which is how this test reaches the copy.
    let to = dir.path().join("bundle").join("0001.txt");
    fs::create_dir_all(&to).unwrap();
    assert!(move_file(&from, &to).is_err(), "a move onto a directory succeeded");
    assert_eq!(fs::read_to_string(&from).unwrap(), "evidence", "a failed move lost the lane file");
    let mut partial = to.as_os_str().to_owned();
    partial.push(PARTIAL_SUFFIX);
    assert!(!Path::new(&partial).exists(), "a failed copy left its partial file");
}
