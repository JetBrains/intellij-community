//! The helpers that the suites of `bench` share: the fixture session of `testdata/bench` and its goldens.

use std::path::{Path, PathBuf};

/// The `testdata/bench` directory of this crate, under cargo and under Bazel.
pub(crate) fn testdata_dir() -> PathBuf {
    avl_testkit::crate_path!("testdata/bench")
}

/// A copy of the fixture session `testdata/bench/session` in a temporary directory, because `replay` writes into it.
pub(crate) fn fixture_session() -> (tempfile::TempDir, PathBuf) {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let session = dir.path().join("session");
    avl_testkit::traces::copy_tree(&testdata_dir().join("session"), &session);
    (dir, session)
}

/// The microseconds since the epoch of the process start of the fixture `project-run-01`: the start of `bootstrap`.
const FIXTURE_ORIGIN_US: i64 = 1_790_860_689_816_000;

/// The spans of the Air empty-state composer that [`empty_editor_run`] adds: the name, the start in milliseconds from
/// the process start, and the duration in milliseconds. The composer ends at 2900 ms.
pub(crate) const EMPTY_STATE_FIXTURE_SPANS: [(&str, i64, i64); 8] = [
    ("air.emptyState.createComponent", 2000, 900),
    ("air.emptyState.prologue", 2000, 150),
    ("air.emptyState.prologue.promptDocument", 2010, 136),
    ("air.emptyState.prologue.aiSource", 2005, 5),
    ("air.emptyState.buildOnEdt", 2170, 730),
    ("air.emptyState.buildOnEdt: scheduled", 2160, 10),
    ("air.emptyState.createContent", 2175, 651),
    ("air.emptyState.initializeContent", 2830, 64),
];

/// A run of the empty-editor arm in `session`: a copy of the fixture `project-run-01` whose trace also holds `spans`,
/// each a name, a start and a duration in milliseconds. Answers the run directory.
pub(crate) fn empty_editor_run(session: &Path, spans: &[(&str, i64, i64)]) -> PathBuf {
    let run = session.join("empty-editor-run-01");
    avl_testkit::traces::copy_tree(&session.join("project-run-01"), &run);
    let path = run.join("opentelemetry.json");
    let text = std::fs::read_to_string(&path).expect("a trace");
    let mut trace: serde_json::Value = serde_json::from_str(&text).expect("a Jaeger trace");
    let list = trace["data"][0]["spans"].as_array_mut().expect("a span list");
    for (index, (name, start_ms, duration_ms)) in spans.iter().enumerate() {
        list.push(serde_json::json!({
            "traceID": "070d9092ee000000ee00000000000000",
            "spanID": format!("ee{index:014x}"),
            "operationName": name,
            "processID": "p1",
            "startTime": FIXTURE_ORIGIN_US + start_ms * 1000,
            "duration": duration_ms * 1000,
        }));
    }
    std::fs::write(&path, trace.to_string()).expect("a trace");
    run
}

/// Holds `text` to the golden file `name` of `testdata/bench`. `UPDATE_EXPECT=1` rewrites it.
pub(crate) fn assert_golden(name: &str, text: &str) {
    let path = testdata_dir().join(name);
    // `expect_file!` reads a relative path from the workspace root.
    let path = std::path::absolute(&path).unwrap_or_else(|error| panic!("{}: {error}", path.display()));
    expect_test::expect_file![path].assert_eq(text);
}
