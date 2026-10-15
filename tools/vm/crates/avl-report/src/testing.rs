//! The fixture that the readers of the run stream share: one recorded stream, and the golden of each form.

use std::path::PathBuf;

use avl_wire::daemon::{RunEvent, decode_run_event};
use pretty_assertions::assert_eq;

/// One recorded iteration of the lane daemon: a green scenario with a note and two metrics, a failed instruction with
/// a typed failure, a skipped scenario, a prologue failure, and the summary.
pub(crate) const RUN_STREAM: &str = include_str!("../testdata/run-stream.ndjson");

/// The JUnit XML of [`RUN_STREAM`].
pub(crate) const RUN_STREAM_JUNIT: &str = include_str!("../testdata/run-stream.xml");

/// The TeamCity messages of [`RUN_STREAM`] on the worker `air-linux-1`, with the trace of the failed scenario.
pub(crate) const RUN_STREAM_TEAMCITY: &str = include_str!("../testdata/run-stream.teamcity.txt");

/// The records of [`RUN_STREAM`].
pub(crate) fn run_stream() -> Vec<RunEvent> {
    RUN_STREAM
        .lines()
        .map(|line| decode_run_event(line.as_bytes()).unwrap_or_else(|error| panic!("the fixture line {line} reads: {error}")))
        .collect()
}

/// Asserts that `actual` is the golden `expected` of `testdata/<name>`. With `UPDATE_EXPECT` set, under cargo, it
/// writes `actual` over the golden instead.
pub(crate) fn assert_golden(name: &str, expected: &str, actual: &str) {
    if std::env::var_os("UPDATE_EXPECT").is_some() {
        let directory = std::env::var_os("CARGO_MANIFEST_DIR").expect("the update path runs under cargo");
        let path = PathBuf::from(directory).join("testdata").join(name);
        std::fs::write(&path, actual).unwrap_or_else(|error| panic!("cannot write {}: {error}", path.display()));
        return;
    }
    assert_eq!(
        expected, actual,
        "the golden testdata/{name}; run the test with UPDATE_EXPECT=1 to write it"
    );
}
