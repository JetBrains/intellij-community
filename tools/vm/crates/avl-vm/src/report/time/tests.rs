use std::fs::{self, File};
use std::io::Write as _;
use std::path::Path;

use avl_testkit::traces;
use avl_trace::otlp::{
    InstrumentationScope, KeyValue, Resource, ResourceSpans, SERVICE_NAME, ScopeSpans, Span, TracesData, UnixNano, attr, span_id,
};
use avl_wire::progress::{Event, IterationReported, Record, RunStarted, TraceReady};
use avl_wire::report::{Case, CaseStatus, RunReport, Status, TraceArchive, TraceBundle};
use expect_test::expect;
use pretty_assertions::assert_eq;
use tempfile::TempDir;
use zip::ZipWriter;
use zip::write::SimpleFileOptions;

use super::*;

const SECOND: f64 = 1_000.0;

/// One run of the fixture, written the way the controller writes it: a journal, the report of each iteration, and a
/// trace zip that the report and the journal name.
struct FixtureRun<'a> {
    root: &'a Path,
    run_id: &'a str,
    iteration_id: &'a str,
    duration_ms: f64,
    cases: &'a [(&'a str, &'a str, f64)],
    ide_launches: usize,
    bundles: Vec<(String, Vec<TracesData>)>,
}

impl FixtureRun<'_> {
    fn write(self) {
        let run_dir = self.root.join(VM_RUNS_DIR).join(self.run_id);
        fs::create_dir_all(&run_dir).unwrap();
        let zip_path = run_dir.join("traces.zip");
        let mut writer = ZipWriter::new(File::create(&zip_path).unwrap());
        for (entry, lines) in &self.bundles {
            writer
                .start_file(format!("{entry}spans.jsonl"), SimpleFileOptions::default())
                .unwrap();
            for line in lines {
                writer.write_all(&serde_json::to_vec(line).unwrap()).unwrap();
                writer.write_all(b"\n").unwrap();
            }
        }
        writer.finish().unwrap();

        let report_path = run_dir.join(format!("{}.json", self.iteration_id));
        let report = report(self.iteration_id, self.duration_ms, self.cases, &zip_path, &self.bundles);
        fs::write(&report_path, serde_json::to_vec_pretty(&report).unwrap()).unwrap();

        let at = |second: i64| jiff::Timestamp::from_second(1_791_460_800 + second).unwrap();
        let mut lines = vec![record(
            &Event::RunStarted(RunStarted {
                run_id: self.run_id.to_owned(),
                command: "run".to_owned(),
                args: vec!["AirSessionReadStateGeneratedFlowUiTest".to_owned()],
                checkout: None,
                process_started_at: None,
            }),
            at(0),
        )];
        let daemon = |raw: &str| {
            format!(r#"{{"schemaVersion":1,"event":"runProgress","at":"2026-10-09T12:00:01.000Z","worker":"air-docker-1","data":{raw}}}"#)
        };
        lines.push(daemon(&format!(
            r#"{{"event":"runStarted","iterationId":"{}","daemonBootStamp":"b","startedAt":"t"}}"#,
            self.iteration_id
        )));
        for launch in 0..self.ide_launches {
            lines.push(daemon(&format!(
                r#"{{"event":"ideLaunched","pid":{launch},"launchName":"recycle-{launch}","launchKey":"k","logDir":"/log"}}"#
            )));
            if launch > 0 {
                lines.push(daemon(&format!(r#"{{"event":"ideExited","pid":{},"exitCode":0}}"#, launch - 1)));
            }
        }
        for (entry, _) in &self.bundles {
            lines.push(record(
                &Event::TraceReady(TraceReady {
                    iteration_id: self.iteration_id.to_owned(),
                    bundle_id: "id".to_owned(),
                    test_class: "C".to_owned(),
                    scenario: "s".to_owned(),
                    flow: None,
                    status: "passed".to_owned(),
                    has_video: false,
                    zip: zip_path.to_string_lossy().into_owned(),
                    entry: entry.clone(),
                }),
                at(2),
            ));
        }
        lines.push(record(
            &Event::IterationReported(IterationReported {
                iteration_id: self.iteration_id.to_owned(),
                daemon_run_id: "run-ui-daemon-1".to_owned(),
                selection: "class".to_owned(),
                status: Status::Passed,
                report_path: report_path.to_string_lossy().into_owned(),
                tests_failed: 0,
                tests_started: 3,
            }),
            at(3),
        ));
        // A run in flight ends with a line the controller is still writing.
        lines.push(r#"{"schemaVersion":1,"event":"ru"#.to_owned());
        fs::write(run_dir.join(FILE_NAME), lines.join("\n")).unwrap();
    }
}

fn record(event: &Event, at: jiff::Timestamp) -> String {
    serde_json::to_string(&Record::new(event, at, None)).unwrap()
}

fn report(
    iteration_id: &str,
    duration_ms: f64,
    cases: &[(&str, &str, f64)],
    zip: &Path,
    bundles: &[(String, Vec<TracesData>)],
) -> RunReport {
    RunReport {
        iteration_id: iteration_id.to_owned(),
        daemon_run_id: "run-ui-daemon-1".to_owned(),
        daemon_boot_stamp: "b".to_owned(),
        selection: "class".to_owned(),
        status: Status::Passed,
        started_at: "2026-10-09T12:00:00Z".to_owned(),
        completed_at: "2026-10-09T12:25:31Z".to_owned(),
        duration_ms,
        cases: cases
            .iter()
            .map(|(class_name, name, duration_ms)| Case {
                class_name: (*class_name).to_owned(),
                name: (*name).to_owned(),
                status: CaseStatus::Passed,
                duration_ms: *duration_ms,
                started_at: "2026-10-09T12:00:00Z".to_owned(),
                recorded: None,
                reason: None,
            })
            .collect(),
        trace_archives: vec![TraceArchive {
            path: zip.to_string_lossy().into_owned(),
            bytes: 1,
            bundles: bundles
                .iter()
                .map(|(entry, _)| TraceBundle {
                    id: "id".to_owned(),
                    entry: entry.clone(),
                    test_class: "C".to_owned(),
                    scenario: "s".to_owned(),
                    flow: None,
                    status: "passed".to_owned(),
                    has_video: false,
                })
                .collect(),
        }],
        ..RunReport::default()
    }
}

/// One lane span from `start` to `end` seconds, under the lane span `parent`, or the root when `parent` is 0.
fn lane(id: u32, parent: u32, kind: &str, key: &str, title: &str, start: u64, end: u64) -> Span {
    let mut attributes = Vec::new();
    if parent != 0 {
        attributes.push(KeyValue::string(attr::SPAN_KIND, kind));
        attributes.push(KeyValue::string(attr::SPAN_KEY, key));
    }
    Span {
        trace_id: "0".repeat(32),
        span_id: span_id(id),
        parent_span_id: if parent == 0 { String::new() } else { span_id(parent) },
        name: title.to_owned(),
        start_time_unix_nano: UnixNano(start * 1_000_000_000),
        end_time_unix_nano: UnixNano(end * 1_000_000_000),
        attributes,
        ..Span::default()
    }
}

fn traces(spans: Vec<Span>) -> TracesData {
    TracesData {
        resource_spans: vec![ResourceSpans {
            resource: Resource {
                attributes: vec![KeyValue::string(attr::SERVICE_NAME, SERVICE_NAME)],
                ..Resource::default()
            },
            scope_spans: vec![ScopeSpans {
                scope: InstrumentationScope {
                    name: "air-trace".to_owned(),
                    ..InstrumentationScope::default()
                },
                spans,
                schema_url: String::new(),
            }],
            schema_url: String::new(),
        }],
    }
}

/// A scenario with one restart of 125 s, whose quit waits 119 s for the process, and an IDE span that overlaps it. The
/// children come first, as the recorder writes a span when it ends.
fn restarting_bundle() -> Vec<TracesData> {
    let mut ide = lane(9, 3, "ide", "", "project opening", 100, 400);
    ide.attributes = vec![KeyValue::string(attr::SPAN_KIND, "ide")];
    vec![traces(vec![
        lane(2, 1, "reset", "reset:before", "[reset] before keep-session-read-state", 1, 5),
        lane(4, 5, "relaunch", "relaunch:quit/exit", "[relaunch] process exit", 100, 219),
        lane(5, 3, "relaunch", "relaunch:quit", "[relaunch] quit", 100, 220),
        lane(6, 3, "relaunch", "relaunch:ready", "[relaunch] ready", 220, 225),
        lane(3, 1, "restart", "restart:ide", "[restart] IDE", 100, 225),
        ide,
        lane(1, 0, "", "", "keep-session-read-state", 0, 300),
    ])]
}

/// The golden bundle of the recorder, as one more scenario of the run.
fn golden_bundle() -> Vec<TracesData> {
    traces::lines(&traces::example_bundle().join(avl_trace::bundle::SPANS_FILE))
        .iter()
        .map(|line| decode_traces_line(line).unwrap())
        .collect()
}

fn fixture() -> TempDir {
    let root = TempDir::new().unwrap();
    FixtureRun {
        root: root.path(),
        run_id: "run-slow",
        iteration_id: "iter-slow-1",
        duration_ms: 1_531.0 * SECOND,
        cases: &[
            (
                "com.intellij.air.AirSessionReadStateGeneratedFlowUiTest",
                "keep-session-read-state",
                412.3 * SECOND,
            ),
            (
                "com.intellij.air.AirSessionReadStateGeneratedFlowUiTest",
                "mark-unread",
                300.0 * SECOND,
            ),
            (
                "com.intellij.air.AirRenameSessionGeneratedFlowUiTest",
                "rename-closed-project-session",
                389.0 * SECOND,
            ),
        ],
        ide_launches: 4,
        bundles: vec![
            (
                "iter-slow-1/AirSessionReadStateGeneratedFlowUiTest/keep-session-read-state/".to_owned(),
                restarting_bundle(),
            ),
            (
                "iter-slow-1/AirRenameSessionGeneratedFlowUiTest/rename-closed-project-session/".to_owned(),
                golden_bundle(),
            ),
        ],
    }
    .write();
    FixtureRun {
        root: root.path(),
        run_id: "run-fast",
        iteration_id: "iter-fast-1",
        duration_ms: 760.0 * SECOND,
        cases: &[
            (
                "com.intellij.air.AirSessionReadStateGeneratedFlowUiTest",
                "keep-session-read-state",
                200.0 * SECOND,
            ),
            (
                "com.intellij.air.AirSessionReadStateGeneratedFlowUiTest",
                "mark-unread",
                100.0 * SECOND,
            ),
            (
                "com.intellij.air.AirRenameSessionGeneratedFlowUiTest",
                "rename-closed-project-session",
                389.0 * SECOND,
            ),
            ("com.intellij.air.AirQuickGeneratedFlowUiTest", "quick", 1.0 * SECOND),
        ],
        ide_launches: 1,
        bundles: Vec::new(),
    }
    .write();
    root
}

fn time(root: &Path, run: &str, baseline: Option<&str>) -> TimeReport {
    let outcome = command_time(
        root,
        &TimeArgs {
            run: run.to_owned(),
            baseline: baseline.map(str::to_owned),
        },
    )
    .unwrap_or_else(|refusal| panic!("{}: {}", refusal.code, refusal.message));
    let run = measure(&RunFiles::read(root, run).unwrap());
    let baseline = baseline.map(|name| compare(&run, &measure(&RunFiles::read(root, name).unwrap())));
    let answer = TimeReport { run, baseline };
    assert_eq!(
        outcome.data,
        serde_json::to_value(&answer).unwrap(),
        "the envelope's data is the answer"
    );
    assert_eq!(outcome.text, answer.text(), "the prose is the answer's");
    answer
}

/// A millisecond count in seconds with one decimal, so a test compares floats by their text.
fn secs(milliseconds: f64) -> String {
    format!("{:.1}", milliseconds / SECOND)
}

#[test]
fn a_run_is_its_cases_the_time_around_them_its_launches_and_its_relaunches() {
    let root = fixture();

    let answer = time(root.path(), "run-slow", None);
    let run = &answer.run;

    assert_eq!(run.iterations, ["iter-slow-1"]);
    assert_eq!(secs(run.total_ms), "1531.0");
    assert_eq!(secs(run.inside_cases_ms), "1101.3");
    assert_eq!(secs(run.outside_cases_ms), "429.7");
    assert_eq!((run.ide_launches, run.ide_exits), (4, 3));
    assert_eq!(run.bundles, 2, "the zip the report and the journal both name is read once");
    assert_eq!((run.relaunches.count, secs(run.relaunches.total_ms)), (1, "125.0".to_owned()));
    assert_eq!(run.relaunches.by_key.keys().collect::<Vec<_>>(), ["restart:ide"]);
    assert_eq!(
        run.relaunches.parts.keys().collect::<Vec<_>>(),
        ["relaunch:quit", "relaunch:quit/exit", "relaunch:ready"]
    );
    assert_eq!(secs(run.relaunches.parts["relaunch:quit/exit"].total_ms), "119.0");
    assert_eq!(run.top_cases[0].name, "keep-session-read-state");
    let quit_exit = run.top_spans.iter().find(|span| span.key == "relaunch:quit/exit").unwrap();
    assert_eq!(
        (secs(quit_exit.self_ms), secs(quit_exit.total_ms)),
        ("119.0".to_owned(), "119.0".to_owned())
    );
    let quit = run.top_spans.iter().find(|span| span.key == "relaunch:quit").unwrap();
    assert_eq!(secs(quit.self_ms), "1.0", "the self time of a span leaves out its lane children");
    let root_span = run
        .top_spans
        .iter()
        .find(|span| span.key == "scenario" && span.scenario == "keep-session-read-state")
        .unwrap();
    assert_eq!(root_span.title, ROOT_TITLE);
    assert_eq!(
        secs(root_span.self_ms),
        "171.0",
        "and the IDE spans under it overlap, so they are left out"
    );
    assert!(run.top_spans.windows(2).all(|pair| pair[0].self_ms >= pair[1].self_ms));
    assert!(run.notes.is_empty(), "{:?}", run.notes);
    expect![[r"
        run run-slow: 1 iteration, 1531.0s
          inside the test cases     1101.3s  72%
          outside the test cases     429.7s  28%
          IDE launches                    4  3 exits
          relaunches                 125.0s  1 restart span in 2 bundles
            restart:ide              125.0s  1
        the parts of the relaunches
          relaunch:quit                  120.0s  1 span, 120.0s each
          relaunch:quit/exit             119.0s  1 span, 119.0s each
          relaunch:ready                   5.0s  1 span, 5.0s each
        the longest test cases
             412.3s  AirSessionReadStateGeneratedFlowUiTest  keep-session-read-state
             389.0s  AirRenameSessionGeneratedFlowUiTest  rename-closed-project-session
             300.0s  AirSessionReadStateGeneratedFlowUiTest  mark-unread
        the lane spans with the most self time
             171.0s  [scenario] outside every lane span  keep-session-read-state  (300.0s in all)
             119.0s  [relaunch] process exit  keep-session-read-state  (119.0s in all)
               5.0s  [relaunch] ready  keep-session-read-state  (5.0s in all)
               4.0s  [reset] before keep-session-read-state  keep-session-read-state  (4.0s in all)
               2.2s  [reset] after rename-closed-project-session  rename-closed-project-session  (2.2s in all)
               2.2s  [reset] before rename-closed-project-session  rename-closed-project-session  (2.2s in all)
               1.9s  [action] prepare-closed-project-session  rename-closed-project-session  (1.9s in all)
               1.2s  [action] rename-session-enter-name  rename-closed-project-session  (1.2s in all)
               1.0s  [relaunch] quit  keep-session-read-state  (120.0s in all)
               0.9s  [assertion] rename-session-offered: Rename is enabled in the popup of the closed project session row.  rename-closed-project-session  (0.9s in all)"]].assert_eq(&answer.text());
}

#[test]
fn a_baseline_adds_the_ratio_of_each_class_and_the_median_over_the_classes_above_five_seconds() {
    let root = fixture();

    let answer = time(root.path(), "run-slow", Some("iter-fast-1"));
    let baseline = answer.baseline.as_ref().unwrap();

    assert_eq!(baseline.run_id, "run-fast", "an iteration id names the run that reported it");
    let ratios: Vec<(&str, String)> = baseline
        .classes
        .iter()
        .map(|class| (short_class(&class.class_name), format!("{:.4}", class.ratio.unwrap())))
        .collect();
    assert_eq!(
        ratios,
        [
            ("AirSessionReadStateGeneratedFlowUiTest", "2.3743".to_owned()),
            ("AirRenameSessionGeneratedFlowUiTest", "1.0000".to_owned()),
        ]
    );
    assert_eq!(baseline.median_classes, 2);
    assert_eq!(
        baseline.median_ratio.map(|median| format!("{median:.4}")).as_deref(),
        Some("1.6872")
    );
    assert_eq!(baseline.only_in_baseline, ["com.intellij.air.AirQuickGeneratedFlowUiTest"]);
    // The text of the run is the one without a baseline, which the snapshot of the slow run pins. The baseline part
    // follows it.
    let text = answer.text();
    let without_baseline = format!("{}\n", time(root.path(), "run-slow", None).text());
    let baseline_part = text
        .strip_prefix(&without_baseline)
        .unwrap_or_else(|| panic!("the text does not start with the text of the run without a baseline:\n{text}"));
    expect![[r"
        against run-fast: 760.0s
            2.37     712.3s /    300.0s  AirSessionReadStateGeneratedFlowUiTest
            1.00     389.0s /    389.0s  AirRenameSessionGeneratedFlowUiTest
          median ratio 1.69 over 2 classes above 5 s in the baseline
          only in the baseline: com.intellij.air.AirQuickGeneratedFlowUiTest"]]
    .assert_eq(baseline_part);
}

#[test]
fn an_iteration_id_measures_that_iteration_and_an_unknown_name_is_refused() {
    let root = fixture();

    assert_eq!(time(root.path(), "iter-slow-1", None).run.run_id, "run-slow");
    let Err(refusal) = RunFiles::read(root.path(), "iter-none") else {
        panic!("an unknown name was read");
    };
    assert_eq!(refusal.code, "report_run_unknown");
    assert_eq!(refusal.exit, Exit::NO_INPUT);
    let Err(refusal) = RunFiles::read(root.path(), "../run-slow") else {
        panic!("a path was read as a name");
    };
    assert_eq!(refusal.code, "unsafe_name");
}

#[test]
fn a_file_that_cannot_be_read_is_a_note_and_the_rest_is_measured() {
    let root = fixture();
    fs::remove_file(root.path().join(VM_RUNS_DIR).join("run-slow").join("traces.zip")).unwrap();

    let run = time(root.path(), "run-slow", None).run;

    assert_eq!(secs(run.total_ms), "1531.0");
    assert_eq!(run.relaunches.count, 0);
    assert_eq!(run.notes.len(), 2, "{:?}", run.notes);
    assert!(
        run.notes.iter().all(|note| note.starts_with("cannot read iter-slow-1/")),
        "{:?}",
        run.notes
    );
}

#[test]
fn the_median_of_an_even_count_is_the_mean_of_the_middle_two() {
    assert_eq!(median(&[]), None);
    assert_eq!(median(&[3.0]), Some(3.0));
    assert_eq!(median(&[1.0, 2.0, 4.0, 8.0]), Some(3.0));
}
