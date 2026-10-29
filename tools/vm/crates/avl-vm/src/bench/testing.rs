//! The helpers that the suites of `bench` share: the fixture session of `testdata/bench` and its goldens.

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

use avl_base::Reporter;

use super::arm::Arm;
use super::files;
use super::profile::ProfileEvent;
use super::record::{self, LaunchFacts, RunId, RunKind, RunRecord};
use super::session::{self, GenerationRef, GitInfo, SUMMARY_FILE, SessionInfo};
use super::summary::Summary;

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

/// A copy of the fixture session after its replay, so it has its `summary.json`.
pub(crate) fn replayed_session() -> (tempfile::TempDir, PathBuf) {
    let (dir, session) = fixture_session();
    replay(&session);
    (dir, session)
}

/// Replays `session` into its `summary.json`, and answers the summary.
pub(crate) fn replay(session: &Path) -> Summary {
    let (reporter, _, _) = Reporter::in_memory("vm");
    super::command::replay(session, &reporter).expect("a summary")
}

/// The inputs of a welcome session with `arms` and `runs` runs of each arm, without a generation, a project and a load
/// limit.
pub(crate) fn session_info(arms: Vec<Arm>, runs: u32) -> SessionInfo {
    SessionInfo {
        command: "welcome".to_owned(),
        target: "//build:idea_dist".to_owned(),
        git: GitInfo::default(),
        generation: GenerationRef::default(),
        measure_model: None,
        arms,
        runs,
        cold: false,
        hold_ms: 0,
        profile: false,
        profile_event: ProfileEvent::Cpu,
        project: None,
        project_file: None,
        suppressed_plugin: None,
        additional_modules: Vec::new(),
        max_load: None,
        warnings: Vec::new(),
    }
}

/// The record of the run `index` of `arm`, without a profile. A run with a `value` is valid and has it as its metric
/// `metric`. A run without a value is invalid, because it saw no event.
pub(crate) fn run_record(arm: Arm, kind: RunKind, index: u32, metric: &str, value: Option<f64>) -> RunRecord {
    RunRecord {
        valid: value.is_some(),
        reason: value.is_none().then(|| "no event".to_owned()),
        metrics: value.map(|value| BTreeMap::from([(metric.to_owned(), value)])).unwrap_or_default(),
        ..RunRecord::new(&RunId { arm, kind, index }, LaunchFacts::default())
    }
}

/// Collects the run directories `runs` of `session` into their `result.json`, and writes a `summary.json` whose arms
/// are `arms`. The session keeps its other inputs. A run directory name gives the arm of the run.
pub(crate) fn summarize(session: &Path, arms: Vec<Arm>, runs: &[&str]) -> Summary {
    let mut info = session::read_info(session).expect("the session");
    info.arms = arms;
    let records: Vec<_> = runs
        .iter()
        .map(|run| {
            let id = RunId::parse(run).unwrap_or_else(|| panic!("{run} is no run directory name"));
            let record = record::collect(&session.join(run), &id, LaunchFacts::default());
            files::write_json(&session.join(run).join("result.json"), &record).expect("result.json");
            record
        })
        .collect();
    let summary = Summary::build(&info, &session.display().to_string(), &records);
    files::write_json(&session.join(SUMMARY_FILE), &summary).expect("summary.json");
    summary
}

/// Writes `summary.json` of a session whose only arm is the project arm, over `project-run-01` of the fixture. Its
/// plugin log has the new shape, with the thread, the time and the activation stack of a class.
pub(crate) fn project_session(session: &Path) {
    summarize(session, vec![Arm::PROJECT], &["project-run-01"]);
}

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
/// see [`append_spans`]. Answers the run directory.
pub(crate) fn empty_editor_run(session: &Path, spans: &[(&str, i64, i64)]) -> PathBuf {
    let run = session.join("empty-editor-run-01");
    avl_testkit::traces::copy_tree(&session.join("project-run-01"), &run);
    append_spans(&run, spans);
    run
}

/// Appends `spans` to the trace of the run directory `run`, each a name, a start in milliseconds from the origin of the
/// trace, and a duration in milliseconds. The origin is the process start, see [`super::trace::Trace::origin_us`].
pub(crate) fn append_spans(run: &Path, spans: &[(&str, i64, i64)]) {
    let path = run.join("opentelemetry.json");
    let text = std::fs::read_to_string(&path).expect("a trace");
    let origin_us = super::trace::parse(&text)
        .expect("a trace")
        .origin_us()
        .expect("a trace with a span");
    let mut trace: serde_json::Value = serde_json::from_str(&text).expect("a Jaeger trace");
    let list = trace["data"][0]["spans"].as_array_mut().expect("a span list");
    for (name, start_ms, duration_ms) in spans {
        let id = format!("ee{:014x}", list.len());
        list.push(serde_json::json!({
            "traceID": "070d9092ee000000ee00000000000000",
            "spanID": id,
            "operationName": name,
            "processID": "p1",
            "startTime": origin_us + start_ms * 1000,
            "duration": duration_ms * 1000,
        }));
    }
    std::fs::write(&path, trace.to_string()).expect("a trace");
}

/// Holds `text` to the golden file `name` of `testdata/bench`. `UPDATE_EXPECT=1` rewrites it.
pub(crate) fn assert_golden(name: &str, text: &str) {
    let path = testdata_dir().join(name);
    // `expect_file!` reads a relative path from the workspace root.
    let path = std::path::absolute(&path).unwrap_or_else(|error| panic!("{}: {error}", path.display()));
    expect_test::expect_file![path].assert_eq(text);
}
