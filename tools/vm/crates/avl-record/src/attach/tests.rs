use std::fs;
use std::path::Path;

use avl_trace::bundle::{ATTACH_DIR, PARTIAL_SUFFIX, attach_path, bundle_file};
use avl_trace::otlp::{LogRecord, attr, event, lookup_str, span_id};
use avl_trace::protocol::{Command, encode_command};
use pretty_assertions::assert_eq;

use super::*;
use crate::tests::harness::*;

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

/// The lane's file leaves the run's directory and becomes the bundle's, numbered in the order it arrived, and its
/// record sits on the span the lane named.
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
    let recorded: Vec<(String, String, String, String)> = events(&bundle, event::ATTACHMENT)
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
        .collect();
    let want = [
        (span_id(1), "Start-up classes", "text/plain", "attach/0001.txt"),
        (span_id(0), "The chat", "image/png", "attach/0002.bin"),
    ]
    .map(|(span, name, mime, file)| (span, name.to_owned(), mime.to_owned(), file.to_owned()));
    assert_eq!(recorded, want);
    assert!(
        events(&bundle, event::TRACE_ERROR).is_empty(),
        "{:?}",
        events(&bundle, event::TRACE_ERROR)
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
