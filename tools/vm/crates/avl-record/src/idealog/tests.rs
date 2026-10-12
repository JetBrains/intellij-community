use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::Path;

use avl_trace::bridge::{Facts, Rect};

use super::*;

fn facts(log: &Path, size: u64) -> Facts {
    Facts {
        pid: 1,
        log_path: log.to_string_lossy().into_owned(),
        log_size: size,
        display: None,
        screen: Rect::default(),
        os: "linux".to_owned(),
    }
}

fn append(path: &Path, content: &str) {
    let mut file = OpenOptions::new().create(true).append(true).open(path).unwrap();
    file.write_all(content.as_bytes()).unwrap();
}

#[test]
fn the_slice_is_what_the_scenario_wrote() {
    let dir = tempfile::tempdir().unwrap();
    let log = dir.path().join("idea.log");
    fs::write(&log, "before\n").unwrap();
    let mut slicer = LogSlicer::default();
    slicer.begin(&facts(&log, 7)).unwrap();
    append(&log, "during 1\nduring 2\n");
    let output = dir.path().join("slice.log");
    slicer.cut(&output, Some(&facts(&log, 25))).unwrap();
    assert_eq!(fs::read_to_string(output).unwrap(), "during 1\nduring 2\n");
}

/// A restart that rotates the log moves the first file away and starts a second: the slice keeps the first file's
/// tail, shutdown included, and appends the new file's lines.
#[cfg(unix)]
#[test]
fn a_rotated_log_is_sliced_across_both_files() {
    let dir = tempfile::tempdir().unwrap();
    let log = dir.path().join("idea.log");
    fs::write(&log, "before\n").unwrap();
    let mut slicer = LogSlicer::default();
    slicer.begin(&facts(&log, 7)).unwrap();
    append(&log, "old ide\n");
    fs::rename(&log, dir.path().join("idea.1.log")).unwrap();
    fs::write(&log, "new ide\n").unwrap();
    let output = dir.path().join("slice.log");
    slicer.cut(&output, Some(&facts(&log, 8))).unwrap();
    assert_eq!(fs::read_to_string(output).unwrap(), "old ide\nnew ide\n");
}

/// A log truncated in place is shorter than the start offset, so all of it was written during the scenario.
#[test]
fn a_log_truncated_in_place_is_sliced_from_its_start() {
    let dir = tempfile::tempdir().unwrap();
    let log = dir.path().join("idea.log");
    fs::write(&log, "x".repeat(100)).unwrap();
    let mut slicer = LogSlicer::default();
    slicer.begin(&facts(&log, 100)).unwrap();
    fs::write(&log, "fresh\n").unwrap();
    let output = dir.path().join("slice.log");
    slicer.cut(&output, None).unwrap();
    assert_eq!(fs::read_to_string(output).unwrap(), "fresh\n");
}
