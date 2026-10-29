//! The fixture of the tests: one recorded run stream, and the path of each golden of `testdata/`.

use std::path::PathBuf;

use avl_wire::daemon::{RunEvent, decode_run_event};

/// The absolute path of `testdata/<name>`, because `expect_file!` reads a relative path from the source file.
pub(crate) fn testdata(name: &str) -> PathBuf {
    avl_testkit::testdata_path!(name)
}

/// The records of `testdata/run-stream.ndjson`: one recorded iteration of the lane daemon with a green scenario that
/// has a note and two metrics, a failed instruction with a typed failure, a skipped scenario, a prologue failure and
/// the summary.
pub(crate) fn run_stream() -> Vec<RunEvent> {
    avl_testkit::decode_lines(&testdata("run-stream.ndjson"), decode_run_event)
}
