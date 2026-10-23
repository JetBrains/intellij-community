//! The fixture of the tests of the TeamCity messages: the recorded run stream of `avl-junit`, and the path of each
//! golden of `testdata/`.

use std::path::{Path, PathBuf};

use avl_base::{Config, Environment, HostFacts, Selection};
use avl_wire::daemon::{RunEvent, decode_run_event};

/// Settings whose runtime root and home are under `root`. A publish reads nothing else.
pub(crate) fn settings(root: &Path) -> Config {
    let environment = Environment::from_pairs([
        ("HOME", root.join("home").to_str().unwrap()),
        ("TART_BIN", "tart"),
        ("AIR_VM_RUNTIME_ROOT", root.join("runtime").to_str().unwrap()),
    ]);
    Config::load(
        HostFacts::without_memory(),
        Selection::DEFAULT,
        &environment,
        Path::new("/repo/scripts"),
    )
    .unwrap_or_else(|refusal| panic!("the environment was refused: {refusal:?}"))
}

/// The absolute path of `testdata/<name>`, because `expect_file!` reads a relative path from the source file.
pub(crate) fn testdata(name: &str) -> PathBuf {
    avl_testkit::testdata_path!(name)
}

/// The records of the recorded iteration that the JUnit XML of `avl-junit` has its golden of.
pub(crate) fn run_stream() -> Vec<RunEvent> {
    avl_testkit::decode_lines(
        &avl_testkit::repo_path("tools/vm/crates/avl-junit/testdata/run-stream.ndjson"),
        decode_run_event,
    )
}
