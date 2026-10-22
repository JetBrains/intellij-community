//! The fixture of the tests of the TeamCity messages: the recorded run stream of `avl-junit`, and the path of each
//! golden of `testdata/`.

use std::path::PathBuf;

use avl_wire::daemon::{RunEvent, decode_run_event};

/// The absolute path of `testdata/<name>`, because `expect_file!` reads a relative path from the source file.
pub(crate) fn testdata(name: &str) -> PathBuf {
    let path = avl_testkit::crate_path!(&format!("testdata/{name}"));
    std::path::absolute(&path).unwrap_or_else(|error| panic!("{}: {error}", path.display()))
}

/// The records of the recorded iteration that the JUnit XML of `avl-junit` has its golden of.
pub(crate) fn run_stream() -> Vec<RunEvent> {
    let path = avl_testkit::repo_path("tools/vm/crates/avl-junit/testdata/run-stream.ndjson");
    let text = std::fs::read_to_string(&path).unwrap_or_else(|error| panic!("{}: {error}", path.display()));
    text.lines()
        .map(|line| decode_run_event(line.as_bytes()).unwrap_or_else(|error| panic!("the fixture line {line} reads: {error}")))
        .collect()
}
