//! The golden replay and the process loop.
//!
//! The golden bundle is not a hand-written example of the format but what the recorder writes for the golden
//! transcript. The viewer's tests read it, so this is the test that keeps the viewer honest about the recorder, and
//! the transcript is the one the sidecar's contract test holds the lane to: the three ends meet here.
//!
//! This recorder wrote the goldens, so every file is compared byte for byte: a change of key order, float or escape
//! formatting fails the replay. A deliberate format change regenerates the text files under cargo with
//! `UPDATE_EXPECT=1`, the variable of `expect-test`. A still is binary, and `expect-test` holds text only, so a still
//! has no update mode: a still that differs fails the replay, which keeps the replay's file and names it.

use std::collections::BTreeMap;
use std::fs;
use std::io::{self, Write};
use std::path::Path;
use std::thread;
use std::time::{Duration, Instant};

use avl_testkit::traces::{self, EXAMPLE_BUNDLE, TRANSCRIPT, TRUNCATED_EXAMPLE_BUNDLE, TRUNCATED_TRANSCRIPT};
use avl_trace::bundle::{
    ALLURE_RESULT_SUFFIX, BundleStatus, IDEA_LOG_FILE, SNAP_DIR, allure_result_file, allure_results_dir, bundle_file, decode_allure_result,
    snap_image_path,
};
use avl_trace::otlp::{attr, event, lookup_str, span_id};
use avl_trace::protocol::{CaptureSource, Status, VideoCodec, decode_command, decode_hello_ack};
use expect_test::expect_file;
use pretty_assertions::assert_eq;

use crate::testing::SharedBuffer;
use crate::{serve, system_clock};

pub(crate) mod harness;

use harness::*;

/// Holds the produced files to a golden directory. A text file goes through `expect_file!`, so `UPDATE_EXPECT=1`
/// rewrites it. A still is compared byte for byte. The names are compared after the files, so an update that adds a
/// text file passes on its first run, and a file that the replay no longer writes fails until it is removed.
fn assert_golden_files(produced: &BTreeMap<String, Vec<u8>>, golden: &Path, what: &str) {
    for (name, content) in produced {
        let path = bundle_file(golden, name);
        if is_still(name) {
            let expected = fs::read(&path).unwrap_or_else(|error| panic!("{what} has no still {name}: {error}"));
            assert_same_bytes(name, content, &expected);
        } else {
            let text = std::str::from_utf8(content).unwrap_or_else(|error| panic!("the replay's {name} is not text: {error}"));
            expect_file![absolute(&path)].assert_eq(text);
        }
    }
    let names = |files: &BTreeMap<String, Vec<u8>>| files.keys().cloned().collect::<Vec<_>>();
    assert_eq!(names(produced), names(&files_under(golden)), "the replay's files against {what}");
}

/// Whether a bundle path names a still, the one binary file of a golden bundle.
fn is_still(name: &str) -> bool {
    name.starts_with(&format!("{SNAP_DIR}/")) && name.ends_with(".webp")
}

/// Holds a produced file to its golden one, byte for byte: a still, which `expect-test` cannot hold, or a snapshot of
/// the truncated replay, which the full golden holds. A file that differs is kept, under Bazel's undeclared outputs or
/// else the system temporary directory, and the failure names that file: a deliberate encoder change copies it over
/// the golden one.
fn assert_same_bytes(name: &str, produced: &[u8], golden: &[u8]) {
    if produced == golden {
        return;
    }
    let directory = std::env::var_os("TEST_UNDECLARED_OUTPUTS_DIR").map_or_else(std::env::temp_dir, std::path::PathBuf::from);
    let kept = directory.join(format!("replay-{}", name.replace('/', "-")));
    fs::write(&kept, produced).unwrap_or_else(|error| panic!("keep the replay's {name} as {}: {error}", kept.display()));
    panic!(
        "{name} differs from the golden one ({} bytes, {} expected); the replay's file is {}",
        produced.len(),
        golden.len(),
        kept.display()
    );
}

/// The absolute form of a golden path, because `expect_file!` reads a relative path from the workspace root.
fn absolute(path: &Path) -> std::path::PathBuf {
    std::path::absolute(path).unwrap_or_else(|error| panic!("{}: {error}", path.display()))
}

#[test]
fn the_golden_transcript_replays_into_the_golden_bundle() {
    let h = Harness::new();
    h.replay(&traces::file_lines(TRANSCRIPT));
    let produced = files_under(&h.bundle());
    manifest_of(&h.bundle());
    records(&h.bundle());
    spans(&h.bundle());
    assert_golden_files(&produced, &traces::path(EXAMPLE_BUNDLE), EXAMPLE_BUNDLE);
    expect_file![absolute(&record_testdata(GOLDEN_ACKS))].assert_eq(&h.acks.text());
}

/// The golden transcript's Allure result is `example.allure-result.json` of the recorder's testdata, and every file
/// it names is in the results directory, a picture with the bytes of the bundle's still.
#[test]
fn the_golden_transcript_writes_the_golden_allure_result() {
    let h = Harness::new();
    h.replay(&traces::file_lines(TRANSCRIPT));
    let manifest = manifest_of(&h.bundle());
    let results = allure_results_dir(&h.root, &manifest.run_id);
    let names: Vec<String> = fs::read_dir(&results)
        .unwrap()
        .map(|entry| entry.unwrap().file_name().to_string_lossy().into_owned())
        .filter(|name| name.ends_with(ALLURE_RESULT_SUFFIX))
        .collect();
    let [name] = names.as_slice() else {
        panic!("the golden scenario writes one result: {names:?}");
    };
    let document = fs::read(results.join(name)).unwrap();
    expect_file![absolute(&record_testdata(GOLDEN_ALLURE_RESULT))].assert_eq(std::str::from_utf8(&document).unwrap());
    let result = decode_allure_result(&document).unwrap();
    assert_eq!(name, &allure_result_file(&result.uuid));
    for source in result.sources() {
        assert!(results.join(source).is_file(), "the result names {source}, which is not there");
    }
    let still = |ordinal| fs::read(bundle_file(&h.bundle(), &snap_image_path(ordinal))).unwrap();
    // The reset before the setup takes no snapshot, so the setup's Before is the first picture.
    let setup = &result.steps[1];
    assert_eq!(
        (result.steps[0].attachments.len(), setup.attachments[0].name.as_str()),
        (0, "Before")
    );
    assert_eq!(fs::read(results.join(&setup.attachments[0].source)).unwrap(), still(1));
}

/// Every acked line of the golden transcript is answered, positively and in order, with the line it answers.
#[test]
fn every_acked_line_is_answered_with_its_line_number() {
    let h = Harness::new();
    let lines = traces::file_lines(TRANSCRIPT);
    h.replay(&lines);
    let want: Vec<u64> = lines
        .iter()
        .enumerate()
        .filter(|(_, line)| decode_command(line).unwrap().op().is_acked())
        .map(|(index, _)| index as u64 + 1)
        .collect();
    let acks = h.ack_lines();
    let answered: Vec<(bool, u64)> = acks.iter().map(|ack| (ack["ok"] == true, ack["line"].as_u64().unwrap())).collect();
    assert_eq!(answered, want.iter().map(|line| (true, *line)).collect::<Vec<_>>());
    let text = h.acks.text();
    let hello = decode_hello_ack(text.lines().next().unwrap().as_bytes()).unwrap();
    assert_eq!(
        (hello.capture, hello.video, hello.reason.as_deref()),
        (CaptureSource::X11, VideoCodec::None, Some(GOLDEN_VIDEO_REASON))
    );
}

/// A watchdog's `exitProcess` leaves the recorder reading EOF inside an assertion. The bundle is still finished:
/// every span that was open is written as aborted, and the manifest names the assertion.
#[test]
fn a_truncated_transcript_closes_the_bundle_naming_the_running_span() {
    let h = Harness::new();
    h.replay(&traces::file_lines(TRUNCATED_TRANSCRIPT));
    let bundle = h.bundle();
    let manifest = manifest_of(&bundle);
    assert_eq!(manifest.status, BundleStatus::Truncated);
    assert_eq!(manifest.running_span, Some(span_id(10)));
    let by_id = spans(&bundle);
    for id in [10, 7, 6, 0] {
        let status = by_id
            .get(&span_id(id))
            .and_then(|span| lookup_str(&span.attributes, attr::SPAN_STATUS));
        assert_eq!(status, Some(Status::Aborted.as_str()), "the open span {id}");
    }
    let ended = lookup_str(&by_id[&span_id(9)].attributes, attr::SPAN_STATUS);
    assert_eq!(ended, Some(Status::Passed.as_str()), "the span that ended before the truncation");
    let all = records(&bundle);
    let started = all.iter().any(|record| {
        record.event_name == event::SPAN_STARTED
            && Some(&record.span_id) == manifest.running_span.as_ref()
            && lookup_str(&record.attributes, attr::SPAN_KEY) == Some("assertion:rename-session-dialog-shown")
    });
    assert!(started, "no air.span.started record names the running span's assertion");
    // A tree for each of the 8 snapshots, and a picture for each change of screen among them.
    let snapshots: Vec<_> = all.iter().filter(|record| record.event_name == event::SNAPSHOT).collect();
    let mut named: Vec<&str> = snapshots
        .iter()
        .flat_map(|record| [attr::SNAPSHOT_IMAGE, attr::SNAPSHOT_TREE].map(|key| lookup_str(&record.attributes, key).unwrap_or("")))
        .collect();
    named.sort_unstable();
    named.dedup();
    let files = fs::read_dir(bundle.join(SNAP_DIR)).unwrap().count();
    assert_eq!(snapshots.len(), 8);
    assert_eq!(files, named.len(), "the snapshot files are the ones the records name");
    assert!(named.len() < 2 * 8, "an unchanged screen got a picture of its own");
    assert!(bundle.join(IDEA_LOG_FILE).is_file(), "the truncated bundle has no log slice");
    compare_truncated_golden(&files_under(&bundle));
}

/// Holds a truncated bundle's text files to `example-truncated.airtrace`, which the viewer's tests read to see how a
/// cut-off scenario reads. Its snapshots are the full run's first ones, since the two replays are the same up to the
/// cut; the golden keeps them once, in `example.airtrace`.
fn compare_truncated_golden(produced: &BTreeMap<String, Vec<u8>>) {
    let (snaps, texts): (BTreeMap<_, _>, BTreeMap<_, _>) = produced
        .iter()
        .map(|(name, content)| (name.clone(), content.clone()))
        .partition(|(name, _)| name.starts_with(&format!("{SNAP_DIR}/")));
    assert_golden_files(&texts, &traces::path(TRUNCATED_EXAMPLE_BUNDLE), TRUNCATED_EXAMPLE_BUNDLE);
    let full = files_under(&traces::path(EXAMPLE_BUNDLE));
    for (name, content) in &snaps {
        let golden = full.get(name).unwrap_or_else(|| panic!("the full golden has no {name}"));
        assert_same_bytes(name, content, golden);
    }
}

/// `AirTraceRecorderProcess` starts `air-trace-record` with no argument, and the recorder takes none. The argv an
/// older lane passed, `record`, is refused as a usage error rather than taken for a session, and no refusal exits
/// with 78, which `trace.cmd` keeps for its own failures. `tests/cli.rs` holds the binary to the same.
#[test]
fn an_argument_is_refused_before_a_session_starts() {
    for argument in ["record", "--scenario", "--help"] {
        let (stdout, stderr) = (SharedBuffer::default(), SharedBuffer::default());
        let exit = crate::run([argument.into()], io::empty(), stdout.clone(), stderr.clone());
        assert_eq!(
            (exit, stdout.text(), stderr.text()),
            (
                2,
                String::new(),
                format!("ERROR: air-trace-record takes no argument, but got {argument:?}\n")
            )
        );
    }
}

/// `serve` is the process's loop: lines from a reader until EOF. An EOF in the middle of a scenario is the same
/// truncation as above, reached through the real input path.
#[test]
fn run_finishes_the_bundle_when_its_input_ends() {
    let h = Harness::new();
    let mut options = h.options();
    options.clock = system_clock();
    let mut lines = traces::file_lines(TRUNCATED_TRANSCRIPT);
    lines[0] = h.rooted(&lines[0]);
    // The last line has no newline, as a lane killed mid-write leaves it, and it is still read: it is the
    // assertion's boundary snapshot, which is acked.
    let input = lines.join(&b'\n');
    let acks = SharedBuffer::default();
    let (_stop, stopped) = crossbeam_channel::bounded(1);
    serve(options, io::Cursor::new(input), acks.clone(), stopped);
    let manifest = manifest_of(&h.bundle());
    assert_eq!(
        (manifest.status, manifest.running_span),
        (BundleStatus::Truncated, Some(span_id(10)))
    );
    let last = format!(r#""line":{}"#, lines.len());
    assert!(
        acks.text().contains(&last),
        "the unterminated last line was not answered: {}",
        acks.text()
    );
}

#[test]
fn a_cancelled_run_finishes_the_bundle_too() {
    let h = Harness::new();
    let mut options = h.options();
    options.clock = system_clock();
    // The hello and the scenario only, so that the scenario's ack proves every line was read before the stop.
    let mut lines = traces::file_lines(TRANSCRIPT)[..2].to_vec();
    lines[0] = h.rooted(&lines[0]);
    let (reader, mut writer) = io::pipe().unwrap();
    writer.write_all(&lines.join(&b'\n')).unwrap();
    writer.write_all(b"\n").unwrap();
    let acks = SharedBuffer::default();
    let (stop, stopped) = crossbeam_channel::bounded(1);
    let running = {
        let acks = acks.clone();
        thread::spawn(move || serve(options, reader, acks, stopped))
    };
    let deadline = Instant::now() + Duration::from_secs(10);
    while !acks.text().contains(r#""line":2"#) && Instant::now() < deadline {
        thread::sleep(Duration::from_millis(5));
    }
    stop.send(()).unwrap();
    running.join().unwrap();
    drop(writer);
    let manifest = manifest_of(&h.bundle());
    assert_eq!(
        (manifest.status, manifest.running_span),
        (BundleStatus::Truncated, Some(span_id(0)))
    );
}

/// A lane that died leaves a closed pipe behind its acks. The writes fail, and the recorder still reads its input to
/// the end and finishes the bundle.
#[test]
fn acks_to_a_closed_pipe_do_not_stop_the_session() {
    struct ClosedPipe;
    impl Write for ClosedPipe {
        fn write(&mut self, _: &[u8]) -> io::Result<usize> {
            Err(io::ErrorKind::BrokenPipe.into())
        }
        fn flush(&mut self) -> io::Result<()> {
            Err(io::ErrorKind::BrokenPipe.into())
        }
    }
    let h = Harness::new();
    let mut lines = traces::file_lines(TRANSCRIPT);
    lines[0] = h.rooted(&lines[0]);
    let (_stop, stopped) = crossbeam_channel::bounded(1);
    serve(h.options(), io::Cursor::new(lines.join(&b'\n')), ClosedPipe, stopped);
    assert_eq!(manifest_of(&h.bundle()).status, BundleStatus::Passed);
}
