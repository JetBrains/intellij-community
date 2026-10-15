//! The fixture of the tests: one recorded run stream, and the path of each golden of `testdata/`.

use std::path::PathBuf;

use avl_wire::daemon::{RunEvent, decode_run_event};

/// The absolute path of `testdata/<name>`, because `expect_file!` reads a relative path from the source file.
pub(crate) fn testdata(name: &str) -> PathBuf {
    let path = avl_testkit::crate_path!(&format!("testdata/{name}"));
    std::path::absolute(&path).unwrap_or_else(|error| panic!("{}: {error}", path.display()))
}

/// The records of `testdata/run-stream.ndjson`: one recorded iteration of the lane daemon with a green scenario that
/// has a note and two metrics, a failed instruction with a typed failure, a skipped scenario, a prologue failure and
/// the summary.
pub(crate) fn run_stream() -> Vec<RunEvent> {
    let path = testdata("run-stream.ndjson");
    let text = std::fs::read_to_string(&path).unwrap_or_else(|error| panic!("{}: {error}", path.display()));
    text.lines()
        .map(|line| decode_run_event(line.as_bytes()).unwrap_or_else(|error| panic!("the fixture line {line} reads: {error}")))
        .collect()
}
