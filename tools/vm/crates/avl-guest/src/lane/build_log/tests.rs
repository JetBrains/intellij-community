use std::collections::HashMap;
use std::fs;
use std::io;
use std::sync::{Arc, Mutex};

use avl_trace::bundle::{
    BundleStatus, Capture, MANIFEST_FILE, MANIFEST_SCHEMA, Manifest, SNAP_DIR, SPANS_FILE, StillEncoding, Stills, Video, bundle_dir,
    encode_manifest,
};
use avl_trace::protocol::{CaptureSource, Launcher};
use avl_wire::daemon::{ExecutionIdentity, PlanStarted, Summary, TestFinished, TestStarted, TypedFailure};
use avl_wire::report::ScenarioReport;
use expect_test::expect_file;
use pretty_assertions::assert_eq;
use tempfile::TempDir;

use super::*;

/// The bytes that a channel wrote, shared with the test.
#[derive(Clone, Default)]
pub(crate) struct Lines(pub Arc<Mutex<Vec<u8>>>);

impl Write for Lines {
    fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
        self.0.lock().unwrap().extend_from_slice(bytes);
        Ok(bytes.len())
    }

    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

impl Lines {
    fn text(&self) -> String {
        String::from_utf8(self.0.lock().unwrap().clone()).unwrap()
    }
}

const CLASS: &str = "AirComposerUiTest";
const RUN: &str = "iter-1";

fn execution(scenario: &str) -> ExecutionIdentity {
    ExecutionIdentity {
        id: format!("[engine:lane-daemon]/[suite:composer]/[scenario:{scenario}]"),
        display_name: scenario.to_owned(),
        class_name: Some(format!("com.intellij.air.{CLASS}")),
        method_name: None,
    }
}

/// One record of the run stream, as its line.
pub(crate) fn line(kind: &RunEventKind) -> Vec<u8> {
    let mut line = serde_json::to_vec(kind).unwrap();
    line.push(b'\n');
    line
}

pub(crate) fn plan(total: u32) -> RunEventKind {
    RunEventKind::PlanStarted(PlanStarted {
        timestamp: "t".to_owned(),
        discovered_executions: total,
        class_names: vec![CLASS.to_owned()],
        executions: Vec::new(),
    })
}

pub(crate) fn started(scenario: &str) -> RunEventKind {
    RunEventKind::TestStarted(TestStarted {
        timestamp: "t".to_owned(),
        execution: execution(scenario),
    })
}

pub(crate) fn finished(scenario: &str, status: CaseStatus, duration_ms: i64) -> RunEventKind {
    RunEventKind::TestFinished(TestFinished {
        timestamp: "t".to_owned(),
        execution: execution(scenario),
        status: status.wire().to_owned(),
        duration_ms,
        failure: TypedFailure::default(),
        report: ScenarioReport::default(),
    })
}

/// The summary of a run of `started` scenarios, of which `failed` failed.
pub(crate) fn summary(started: u32, failed: u32) -> RunEventKind {
    RunEventKind::Summary(Summary {
        iteration_id: RUN.to_owned(),
        daemon_boot_stamp: "boot".to_owned(),
        started_at: "t".to_owned(),
        tests_started: started,
        tests_failed: failed,
        tests_skipped: 0,
        containers_skipped: 0,
        container_failures: 0,
        has_failures: failed > 0,
        ide_running: false,
    })
}

/// Writes the bundle of `scenario` into `dir`, as the recorder leaves it: its spans, two stills, the video of a bundle
/// that did not pass, and the manifest last.
fn bundle(dir: &Path, scenario: &str, status: BundleStatus, started_at: &str) {
    fs::create_dir_all(dir.join(SNAP_DIR)).unwrap();
    fs::write(dir.join(SPANS_FILE), b"").unwrap();
    fs::write(dir.join(SNAP_DIR).join("0001.webp"), b"first still").unwrap();
    fs::write(dir.join(SNAP_DIR).join("0002.webp"), b"last still").unwrap();
    // A scenario with a video takes lossy stills, as the recorder picks them.
    let (video, encoding) = if status == BundleStatus::Passed {
        let reason = "the lane did not let the recorder read the screen".to_owned();
        (Video::None { reason }, StillEncoding::Lossless)
    } else {
        fs::write(dir.join(VIDEO_FILE), b"the video").unwrap();
        (Video::H264, StillEncoding::Lossy)
    };
    let manifest = Manifest {
        schema: MANIFEST_SCHEMA.to_owned(),
        run_id: RUN.to_owned(),
        test_class: CLASS.to_owned(),
        scenario: scenario.to_owned(),
        flow: None,
        lane: "UI".to_owned(),
        launcher: Launcher::Bazel,
        status,
        running_span: None,
        started_at: started_at.to_owned(),
        duration_ms: 1000,
        capture: Capture {
            source: CaptureSource::X11,
            reason: None,
        },
        video,
        stills: Stills { encoding },
    };
    fs::write(dir.join(MANIFEST_FILE), encode_manifest(&manifest).unwrap()).unwrap();
}

/// A channel into `lines` for the Bazel outputs of the target `ui_lane_test` below `root`, and its trace root.
fn channel(root: &Path, lines: &Lines) -> (BuildLog, PathBuf) {
    let outputs = root.join("testlogs/plugins/air/tests/integration/ui/ui_lane_test/test.outputs");
    let channel = BuildLog::to(
        Box::new(lines.clone()),
        root.join("live/live.log"),
        "ui",
        Some(&outputs),
        root.join("live"),
    );
    (channel, outputs.join(ROOT_DIR_NAME))
}

// A run of a passed and a failed scenario says the start and the end of each. It publishes the video and the last still
// of the failed one at the artifact paths of teamcity-publish. An odd name is escaped, and a record that arrives in two
// writes still counts.
#[test]
fn a_run_with_a_passed_and_a_failed_scenario_writes_the_golden_lines() {
    let root = TempDir::new().unwrap();
    let lines = Lines::default();
    let (mut channel, traces) = channel(root.path(), &lines);
    let odd = "it's [odd] | named\nnext";

    channel.observe(&line(&plan(2)));
    channel.observe(&line(&started("passes")));
    bundle(
        &bundle_dir(&traces, RUN, CLASS, "passes"),
        "passes",
        BundleStatus::Passed,
        "2026-10-21T10:00:00.000Z",
    );
    channel.observe(&line(&finished("passes", CaseStatus::Successful, 12_345)));
    let start = line(&started(odd));
    let (head, tail) = start.split_at(10);
    channel.observe(head);
    channel.observe(tail);
    bundle(
        &bundle_dir(&traces, RUN, CLASS, odd),
        odd,
        BundleStatus::Failed,
        "2026-10-21T10:00:13.000Z",
    );
    channel.observe(&line(&finished(odd, CaseStatus::Failed, 3_210)));

    let text = lines.text().replace(&root.path().display().to_string(), "$ROOT");
    expect_file![avl_testkit::crate_path!("testdata/build-log/run.txt")].assert_eq(&text);
    assert!(text.contains("progressMessage 'ui 2/2: it|'s |[odd|] || named|nnext'"), "{text}");
    let staged = root
        .path()
        .join("live/air-traces-failed/ui_lane_test/iter-1/AirComposerUiTest/it_s__odd____named_next");
    assert_eq!(fs::read(staged.join("failure.webp")).unwrap(), b"last still");
    assert_eq!(fs::read(staged.join(VIDEO_FILE)).unwrap(), b"the video");
}

// A later attempt of a failed scenario takes the next stem, as teamcity-publish names it.
#[test]
fn a_second_failed_attempt_publishes_under_the_next_stem() {
    let root = TempDir::new().unwrap();
    let lines = Lines::default();
    let (mut channel, traces) = channel(root.path(), &lines);
    let first = bundle_dir(&traces, RUN, CLASS, "fails");
    let second = first.with_file_name("fails.2");
    for (dir, started_at) in [(first, "2026-10-21T10:00:00.000Z"), (second, "2026-10-21T10:01:00.000Z")] {
        channel.observe(&line(&started("fails")));
        bundle(&dir, "fails", BundleStatus::Failed, started_at);
        channel.observe(&line(&finished("fails", CaseStatus::Failed, 1000)));
    }

    let targets: Vec<String> = lines
        .text()
        .lines()
        .filter(|line| line.starts_with("##teamcity[publishArtifacts") && line.contains(VIDEO_FILE))
        .map(|line| line.rsplit_once(" => ").unwrap().1.trim_end_matches("']").to_owned())
        .collect();
    assert_eq!(
        targets,
        [
            "air-traces-failed/ui_lane_test/iter-1/AirComposerUiTest/fails",
            "air-traces-failed/ui_lane_test/iter-1/AirComposerUiTest/fails.2"
        ]
    );
}

// A rerun of a scenario names itself in its progress messages, and it takes no position of the plan.
#[test]
fn a_rerun_names_itself_in_the_progress_messages() {
    let root = TempDir::new().unwrap();
    let lines = Lines::default();
    let (mut channel, _) = channel(root.path(), &lines);
    channel.observe(&line(&plan(2)));
    for (scenario, status) in [
        ("flaky", CaseStatus::Failed),
        ("passes", CaseStatus::Successful),
        ("flaky", CaseStatus::Successful),
    ] {
        channel.observe(&line(&started(scenario)));
        channel.observe(&line(&finished(scenario, status, 2_000)));
    }
    let text = lines.text();
    let progress: Vec<&str> = text
        .lines()
        .map(|line| line.trim_start_matches("##teamcity[progressMessage '").trim_end_matches("']"))
        .collect();
    assert_eq!(
        progress,
        [
            "ui 1/2: flaky",
            "ui 1/2: flaky failed (2.0 s)",
            "ui 2/2: passes",
            "ui 2/2: passes passed (2.0 s)",
            "ui rerun: flaky",
            "ui rerun: flaky passed (2.0 s)",
        ]
    );
}

/// The environment of `pairs`.
fn environment(pairs: &[(&str, String)]) -> impl Fn(&str) -> Option<String> {
    let map: HashMap<String, String> = pairs.iter().map(|(key, value)| ((*key).to_owned(), value.clone())).collect();
    move |name| map.get(name).cloned()
}

// The channel is on only on a TeamCity build that names the file. A file that cannot be opened keeps it off. Without the
// outputs of a Bazel test it reports the progress and publishes nothing.
#[test]
fn the_channel_is_on_only_on_teamcity_with_the_variable() {
    let root = TempDir::new().unwrap();
    let file = root.path().join("live.log");
    let named = file.display().to_string();
    let teamcity = ("TEAMCITY_VERSION", "2026.1".to_owned());

    assert!(BuildLog::open(&environment(&[(VARIABLE, named.clone())]), "ui", None).is_none());
    assert!(BuildLog::open(&environment(std::slice::from_ref(&teamcity)), "ui", None).is_none());
    assert!(BuildLog::open(&environment(&[teamcity.clone(), (VARIABLE, " ".to_owned())]), "ui", None).is_none());
    let directory = root.path().display().to_string();
    assert!(BuildLog::open(&environment(&[teamcity.clone(), (VARIABLE, directory)]), "ui", None).is_none());

    let mut channel = BuildLog::open(&environment(&[teamcity, (VARIABLE, named)]), "ui", None).unwrap();
    channel.observe(&line(&plan(1)));
    channel.observe(&line(&started("fails")));
    channel.observe(&line(&finished("fails", CaseStatus::Failed, 500)));
    assert_eq!(
        fs::read_to_string(&file).unwrap(),
        "##teamcity[progressMessage 'ui 1/1: fails']\n##teamcity[progressMessage 'ui 1/1: fails failed (0.5 s)']\n"
    );
}

// Under a Bazel test on TeamCity, Bazel sets XML_OUTPUT_FILE, and the build passes TEAMCITY_VERSION with --test_env.
// That is a TeamCity build step for the channel, so it writes its lines there.
#[test]
fn the_channel_is_on_in_a_bazel_test_of_a_teamcity_build() {
    let root = TempDir::new().unwrap();
    let file = root.path().join("live.log");
    let pairs = [
        ("TEAMCITY_VERSION", "2026.1".to_owned()),
        ("XML_OUTPUT_FILE", root.path().join("test.xml").display().to_string()),
        (VARIABLE, file.display().to_string()),
    ];
    let variables = environment(&pairs);

    let mut channel = BuildLog::open(&variables, "ui", None).expect("a Bazel test of a TeamCity build opens the channel");
    channel.observe(&line(&plan(1)));
    channel.observe(&line(&started("passes")));
    channel.observe(&line(&finished("passes", CaseStatus::Successful, 1500)));

    assert_eq!(
        fs::read_to_string(&file).unwrap(),
        "##teamcity[progressMessage 'ui 1/1: passes']\n##teamcity[progressMessage 'ui 1/1: passes passed (1.5 s)']\n"
    );
}

// The recorder can finish the bundle of the last scenario after its testFinished. The summary of the run scans once
// more, so that bundle still gets its files published, and staged beside the file of the channel.
#[test]
fn the_summary_publishes_a_bundle_that_the_recorder_finished_late() {
    let root = TempDir::new().unwrap();
    let lines = Lines::default();
    let (mut channel, traces) = channel(root.path(), &lines);

    channel.observe(&line(&plan(1)));
    channel.observe(&line(&started("fails")));
    channel.observe(&line(&finished("fails", CaseStatus::Failed, 1000)));
    assert!(!lines.text().contains("publishArtifacts"), "{}", lines.text());
    bundle(
        &bundle_dir(&traces, RUN, CLASS, "fails"),
        "fails",
        BundleStatus::Failed,
        "2026-10-21T10:00:00.000Z",
    );
    channel.observe(&line(&summary(1, 1)));

    let staged = root
        .path()
        .join("live/air-traces-failed/ui_lane_test/iter-1/AirComposerUiTest/fails");
    let text = lines.text();
    let published: Vec<&str> = text.lines().filter(|line| line.contains("publishArtifacts")).collect();
    assert_eq!(published.len(), 2, "{published:?}");
    assert!(
        published[0].contains(&staged.join(VIDEO_FILE).display().to_string()),
        "{published:?}"
    );
    assert_eq!(fs::read(staged.join(VIDEO_FILE)).unwrap(), b"the video");
}

/// A build log file that takes `prefix` bytes of its first write and refuses the rest of it, then takes each write.
struct Short {
    prefix: usize,
    written: Arc<Mutex<Vec<u8>>>,
}

impl Write for Short {
    fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
        let mut written = self.written.lock().unwrap();
        let taken = if written.is_empty() {
            self.prefix.min(bytes.len())
        } else {
            bytes.len()
        };
        written.extend_from_slice(&bytes[..taken]);
        Ok(taken)
    }

    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

// A write that takes a part of a line ends that line before the channel goes off, so the next writer of the file starts
// a line of its own.
#[test]
fn a_short_write_ends_its_line_and_turns_the_channel_off() {
    let written = Arc::new(Mutex::new(Vec::new()));
    let file = Short {
        prefix: 12,
        written: written.clone(),
    };
    let mut channel = BuildLog::to(Box::new(file), PathBuf::from("/live/live.log"), "ui", None, PathBuf::new());

    channel.observe(&line(&plan(2)));
    channel.observe(&line(&started("first")));
    channel.observe(&line(&started("second")));

    assert_eq!(String::from_utf8(written.lock().unwrap().clone()).unwrap(), "##teamcity[p\n");
}
