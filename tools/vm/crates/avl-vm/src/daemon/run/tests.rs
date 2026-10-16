use std::fs;
use std::path::Path;

use avl_host_testkit::refusal;
use avl_wire::daemon::WatchdogState;
use clap::Parser;
use pretty_assertions::assert_eq;
use serde_json::{Value, json};
use tokio_util::sync::CancellationToken;

use super::*;
use crate::daemon::fixture::Fixture;
use crate::daemon::testing::{ndjson_line, run_started_record, summary_record};

// --- the decisions of a run stream ----------------------------------------------------------------------------

// A 409 answers by its reason: a stale daemon names both generations, quiesced shares are not busy, and any other
// reason is busy.
#[test]
fn a_conflict_answers_by_its_reason() {
    let conflict = |reason: &str, launch_digest: Option<&str>| wire::Conflict {
        reason: reason.to_owned(),
        launch_digest: launch_digest.map(str::to_owned),
    };
    let stale = conflict_refusal(&conflict(conflict::STALE_DAEMON, Some("launch-0")), "launch-1");
    assert_eq!((stale.code.as_ref(), stale.exit), ("daemon_stale", Exit::TEMP_FAIL));
    assert!(
        stale.message.contains("(it serves launch-0, this run wants launch-1)"),
        "{}",
        stale.message
    );
    let quiesced = conflict_refusal(&conflict(conflict::MOUNT_QUIESCED, None), "launch-1");
    assert_eq!(quiesced.code, "daemon_mount_quiesced");
    let busy = conflict_refusal(&conflict("busy", None), "launch-1");
    assert_eq!(
        (busy.code.as_ref(), busy.message.as_str()),
        ("daemon_busy", "the daemon rejected the run: busy")
    );
}

// The stream's records fold into the execution: the iteration from `runStarted`, and every record as evidence; a
// failed stream names its iteration in the synthetic record when it has one.
#[test]
fn the_stream_folds_into_the_execution() {
    let mut execution = RunExecution::default();
    for line in [run_started_record("it-1"), summary_record("it-1", 1, 1, 0, 0, 0, false)] {
        let decoded = wire::NdjsonDecoder::default().push(format!("{}\n", ndjson_line(&line)).as_bytes());
        for event in decoded.events {
            execution.absorb(event);
        }
    }
    assert_eq!(execution.iteration_id.as_deref(), Some("it-1"));
    assert_eq!(execution.events.len(), 2);

    let failure = daemon_died("run-1", "connection reset");
    assert_eq!((failure.code.as_ref(), failure.exit), ("daemon_died", Exit::SOFTWARE));
    assert_eq!(
        protocol_failed_record(&failure, Some("it-1")),
        json!({"event": "protocolFailed", "code": "daemon_died", "message": failure.message, "iterationId": "it-1"})
    );
    assert_eq!(protocol_failed_record(&failure, None).get("iterationId"), None);
}

/// `vm run`, as far as its own options go.
#[derive(Parser, Debug)]
struct Probe {
    #[command(flatten)]
    run: RunArgs,
}

fn parsed(argv: &[&str]) -> RunArgs {
    Probe::try_parse_from(std::iter::once("run").chain(argv.iter().copied()))
        .unwrap_or_else(|error| panic!("{argv:?} did not parse: {error}"))
        .run
}

fn rejected(argv: &[&str]) -> String {
    match Probe::try_parse_from(std::iter::once("run").chain(argv.iter().copied())) {
        Ok(probe) => panic!("{argv:?} parsed as {:?}", probe.run),
        Err(error) => error.to_string(),
    }
}

fn state_record(phase: &str, next_deadline_in_ms: Option<i64>) -> RunEvent {
    RunEvent::synthesized(RunEventKind::WatchdogState(WatchdogState {
        timestamp: "t".to_owned(),
        phase: phase.to_owned(),
        active_execution_timeout_ms: 1,
        progress_gap_timeout_ms: 1,
        phase_deadline: None,
        emergency_deadline: None,
        next_deadline_in_ms,
        active_execution: None,
    }))
}

// The transport deadline is derived only from watchdog lifecycle records: an expiry re-arms at +90 s, a completed
// phase at +30 s, and a state record at its own next deadline plus the margin - guarded to a present, non-negative
// number, because a timer must never be armed from a record that named none.
#[test]
fn transport_deadline_arithmetic() {
    let expired = RunEvent::synthesized(RunEventKind::WatchdogExpired(WatchdogExpired {
        reason: "active_execution_timeout".to_owned(),
        deadline: "t".to_owned(),
        expired_at: "t".to_owned(),
        detail: None,
        active_execution: None,
        evidence: Vec::new(),
    }));
    let seconds = Duration::from_secs;
    assert_eq!(transport_deadline_after(&expired), Some(seconds(90)));
    assert_eq!(transport_deadline_after(&state_record("completed", None)), Some(seconds(30)));
    assert_eq!(
        transport_deadline_after(&state_record("active_execution", Some(5_000))),
        Some(seconds(95))
    );
    // A zero deadline is a deadline: it expires now.
    assert_eq!(
        transport_deadline_after(&state_record("active_execution", Some(0))),
        Some(seconds(90))
    );
    assert_eq!(transport_deadline_after(&state_record("active_execution", None)), None);
    assert_eq!(transport_deadline_after(&state_record("active_execution", Some(-1))), None);
    assert_eq!(transport_deadline_after(&RunEvent::output("text")), None);
}

/// A fixture with a healthy daemon seeded, and the build it was seeded for.
async fn executing() -> (Fixture, PreparedBuild, HostState) {
    let fixture = Fixture::new().await;
    let prep = fixture.prepared().await;
    let state = fixture.seed_healthy_daemon(&prep);
    (fixture, prep, state)
}

const POLICY: WatchdogPolicy = WatchdogPolicy {
    active_execution: Duration::from_mins(30),
    progress_gap: Duration::from_secs(300),
};

async fn execute(fixture: &Fixture, prep: &PreparedBuild, state: &HostState, selection: &RunSelection) -> Result<RunExecution, Refusal> {
    fixture
        .host
        .execute_run(&Ctx::background(), &fixture.worker, state, prep, selection, POLICY, &mut |_| {})
        .await
}

// The `/run` body is the one the Kotlin daemon decodes: every key present, and an empty selection an empty list rather
// than `null`, because the far end's fields are non-nullable.
#[tokio::test]
async fn the_run_request_body_carries_every_key_and_empty_lists() {
    let (fixture, prep, state) = executing().await;
    fixture.daemon.script().run_lines = vec![
        ndjson_line(&run_started_record("it-1")),
        ndjson_line(&summary_record("it-1", 0, 0, 0, 0, 0, false)),
    ];
    let selection = RunSelection {
        selectors: vec!["com.example.MyTest#method".to_owned()],
        junit5_filters: Vec::new(),
        description: "selector".to_owned(),
    };
    let execution = execute(&fixture, &prep, &state, &selection).await.unwrap();
    assert!(execution.protocol_failure.is_none());
    let body: Value = serde_json::from_slice(&fixture.daemon.script().run_body).unwrap();
    assert_eq!(
        body,
        json!({
            "selectors": ["com.example.MyTest#method"],
            "junit5Filters": [],
            "productStamp": prep.product_digest,
            "launchDigest": prep.launch_digest,
            "hotJars": [prep.hot_jars[0].sha256],
            "activeExecutionTimeoutSec": 1_800,
            "progressGapTimeoutSec": 300,
            "idePlugins": [{"destination": "bridge/lib/bridge.jar", "sha256": prep.plugin_files[0].host.sha256}],
        })
    );
    assert_eq!(execution.iteration_id.as_deref(), Some("it-1"));
    assert!(execution.summary.is_some(), "the stream's facts are captured");
}

// A 409 is triaged into the three refusals a caller answers differently, all at 75: a stale daemon names both
// generations and the recovery, a quiesced mount names its own, and everything else is busy.
#[tokio::test]
async fn a_409_is_triaged_into_stale_quiesced_and_busy_at_75() {
    let (fixture, prep, state) = executing().await;
    for (name, conflict, code, fragment) in [
        (
            "a stale daemon",
            json!({"reason": "stale-daemon", "launchDigest": "other"}),
            "daemon_stale",
            "recover with: daemon restart",
        ),
        (
            "a stale daemon naming no generation",
            json!({"reason": "stale-daemon"}),
            "daemon_stale",
            "an unnamed generation",
        ),
        (
            "a quiesced mount",
            json!({"reason": "mount-quiesced"}),
            "daemon_mount_quiesced",
            "recover with: daemon restart",
        ),
        (
            "anything else",
            json!({"reason": "busy"}),
            "daemon_busy",
            "the daemon rejected the run: busy",
        ),
    ] {
        fixture.daemon.script().conflict = Some(conflict);
        let failure = refusal(execute(&fixture, &prep, &state, &RunSelection::default()).await);
        assert_eq!((failure.code.as_ref(), failure.exit), (code, Exit::TEMP_FAIL), "{name}");
        assert!(failure.message.contains(fragment), "{name}: said {:?}", failure.message);
    }
}

// A protocol break mid-stream is answered, never raised: the events read before the break are the only evidence the
// run left, and a synthetic `protocolFailed` record marks the spot in the progress stream.
#[tokio::test]
async fn a_protocol_break_is_returned_as_a_value_not_an_error() {
    let (fixture, prep, state) = executing().await;
    fixture.daemon.script().run_lines = vec![
        ndjson_line(&run_started_record("it-2")),
        r#"{"event":"somethingNobodyDeclared"}"#.to_owned(),
    ];
    let execution = execute(&fixture, &prep, &state, &RunSelection::default())
        .await
        .expect("a protocol failure is a value");
    let failure = execution.protocol_failure.as_ref().unwrap();
    assert_eq!(failure.code, "daemon_protocol_unknown_event");
    assert_eq!(execution.iteration_id.as_deref(), Some("it-2"));
    assert_eq!(execution.events.len(), 1, "the events before the break are kept");
    let progress = fixture.stderr.text();
    assert!(
        progress.contains(r#""event":"protocolFailed""#)
            && progress.contains(r#""iterationId":"it-2""#)
            && progress.contains("daemon_protocol_unknown_event"),
        "a synthetic protocolFailed record marks the break, the stream was\n{progress}"
    );
}

// A connection that dies mid-stream is explained as the daemon dying, with the tail-reading next step - and still as
// a value.
#[tokio::test]
async fn a_mid_stream_death_is_explained_as_daemon_died() {
    let (fixture, prep, state) = executing().await;
    {
        let mut script = fixture.daemon.script();
        script.run_lines = vec![ndjson_line(&run_started_record("it-3"))];
        script.close_mid_stream = true;
    }
    let execution = execute(&fixture, &prep, &state, &RunSelection::default())
        .await
        .expect("a death mid-stream is a value");
    let failure = execution.protocol_failure.as_ref().unwrap();
    assert_eq!((failure.code.as_ref(), failure.exit), ("daemon_died", Exit::SOFTWARE));
    assert!(
        failure.message.contains(&state.run_id)
            && failure.message.contains("daemon log")
            && failure.message.contains("starts a new daemon by itself"),
        "the death names the run and the recovery, said {:?}",
        failure.message
    );
    assert_eq!(execution.iteration_id.as_deref(), Some("it-3"));
}

// Output lines that are not records are kept as output events and republished.
#[tokio::test]
async fn non_record_lines_are_kept_as_output() {
    let (fixture, prep, state) = executing().await;
    fixture.daemon.script().run_lines = vec![
        "WARNING: something the JVM printed".to_owned(),
        ndjson_line(&run_started_record("it-4")),
        ndjson_line(&summary_record("it-4", 0, 0, 0, 0, 0, false)),
    ];
    let execution = execute(&fixture, &prep, &state, &RunSelection::default()).await.unwrap();
    assert!(execution.protocol_failure.is_none(), "stdout noise is no failure");
    assert_eq!(execution.events.len(), 3);
    assert_eq!(
        execution.events[0].event(),
        avl_wire::daemon::Event::Output,
        "the non-record line is kept first"
    );
    assert!(
        fixture.stderr.text().contains(r#""text":"WARNING: something the JVM printed""#),
        "the output record is republished"
    );
}

// The push offers the hot tier and the context plugin files by digest, and uploads only what the guest does not hold.
#[tokio::test]
async fn push_jars_uploads_only_missing_digests() {
    let (fixture, prep, state) = executing().await;
    let ctx = Ctx::background();
    assert_eq!(push_jars(&ctx, fixture.host.daemon(), &state, &prep).await.unwrap(), 0);
    let sha = prep.hot_jars[0].sha256.clone();
    let plugin = prep.plugin_files[0].host.sha256.clone();
    fixture.daemon.script().missing = vec![sha.clone()];
    assert_eq!(push_jars(&ctx, fixture.host.daemon(), &state, &prep).await.unwrap(), 1);
    {
        let script = fixture.daemon.script();
        assert_eq!(script.uploads.len(), 1);
        assert!(script.uploads[0].contains(&sha), "the upload names the digest");
        let body: Value = serde_json::from_slice(&script.jars_body).unwrap();
        assert_eq!(body, json!({ "jars": [sha, plugin] }));
    }
    fixture.daemon.script().missing = vec![plugin.clone()];
    assert_eq!(push_jars(&ctx, fixture.host.daemon(), &state, &prep).await.unwrap(), 1);
    let script = fixture.daemon.script();
    assert!(
        script.uploads[1].contains(&plugin),
        "a changed plugin file travels by the same push"
    );
}

// A plugin file whose bytes equal a hot jar is offered and uploaded once.
#[tokio::test]
async fn a_digest_that_two_files_share_is_offered_once() {
    let (fixture, mut prep, state) = executing().await;
    prep.plugin_files[0].host = prep.hot_jars[0].clone();
    let sha = prep.hot_jars[0].sha256.clone();
    fixture.daemon.script().missing = vec![sha.clone()];
    assert_eq!(
        push_jars(&Ctx::background(), fixture.host.daemon(), &state, &prep).await.unwrap(),
        1
    );
    let body: Value = serde_json::from_slice(&fixture.daemon.script().jars_body).unwrap();
    assert_eq!(body, json!({ "jars": [sha] }));
}

/// A build whose hot tier is `count` jars of `size` bytes each, every one missing on the guest, written under
/// `directory`.
fn missing_hot_jars(fixture: &Fixture, prep: &mut PreparedBuild, directory: &Path, count: usize, size: usize) {
    prep.hot_jars.clear();
    for index in 0..count {
        let content = vec![u8::try_from(index).expect("a small index"); size];
        let path = directory.join(format!("hot-{index}.jar"));
        fs::write(&path, &content).unwrap();
        let sha256 = avl_report::digest::sha256_file(&path).unwrap();
        prep.hot_jars.push(PathDigest::new(path.to_string_lossy(), sha256));
    }
    fixture.daemon.script().missing = prep.hot_jars.iter().map(|jar| jar.sha256.clone()).collect();
}

/// Waits until the double holds `count` uploads at once, or fails the test after 30 s.
async fn wait_for_uploads_in_flight(fixture: &Fixture, count: usize) {
    let deadline = Instant::now() + Duration::from_secs(30);
    while fixture.daemon.script().uploads_in_flight < count {
        assert!(Instant::now() < deadline, "{count} uploads never ran at once");
        tokio::time::sleep(Duration::from_millis(5)).await;
    }
}

/// How many relays the push opened into the guest: one for each new connection of the pool.
fn relays(fixture: &Fixture) -> usize {
    fixture.channel().calls_containing("relay ").len()
}

const MIB: usize = 1 << 20;

// One connection for each 8 MiB of jars begun, from one to four, so a push of at most 8 MiB opens no relay more.
#[test]
fn the_upload_concurrency_follows_the_bytes() {
    let step = BYTES_PER_UPLOAD_CONNECTION;
    for (bytes, expected) in [
        (0, 1),
        (1, 1),
        (step, 1),
        (step + 1, 2),
        (2 * step, 2),
        (2 * step + 1, 3),
        (3 * step + 1, MAX_PARALLEL_UPLOADS),
        (u64::MAX, MAX_PARALLEL_UPLOADS),
    ] {
        assert_eq!(upload_concurrency(bytes), expected, "{bytes} bytes");
    }
}

// A push of at most 8 MiB uploads over the one connection the `/jars` query opened: one relay in all.
#[tokio::test]
async fn a_small_push_opens_no_relay_more() {
    let (fixture, mut prep, state) = executing().await;
    let directory = tempfile::tempdir().unwrap();
    missing_hot_jars(&fixture, &mut prep, directory.path(), 4, 64 * 1024);
    let pushed = push_jars(&Ctx::background(), fixture.host.daemon(), &state, &prep).await;
    assert_eq!(pushed.unwrap(), 4);
    assert_eq!(relays(&fixture), 1, "{:?}", fixture.channel().lines());
    assert_eq!(fixture.daemon.script().uploads.len(), 4);
}

// 15 MiB of jars travel two at a time over two relays, and every missing jar arrives. The answer is a count, so the
// order in which the uploads end changes nothing in it.
#[tokio::test]
async fn the_hot_jars_upload_with_a_bounded_concurrency() {
    let (fixture, mut prep, state) = executing().await;
    let directory = tempfile::tempdir().unwrap();
    missing_hot_jars(&fixture, &mut prep, directory.path(), 3, 5 * MIB);
    let hold = CancellationToken::new();
    fixture.daemon.script().upload_hold = Some(hold.clone());
    let ctx = Ctx::background();
    let push = push_jars(&ctx, fixture.host.daemon(), &state, &prep);
    let release = async {
        wait_for_uploads_in_flight(&fixture, 2).await;
        hold.cancel();
    };
    let (pushed, ()) = tokio::join!(push, release);
    assert_eq!(pushed.unwrap(), 3);
    assert_eq!(fixture.daemon.script().max_uploads_in_flight, 2);
    assert_eq!(relays(&fixture), 2, "{:?}", fixture.channel().lines());
    let mut uploaded: Vec<String> = fixture.daemon.script().uploads.clone();
    uploaded.sort();
    let mut expected: Vec<String> = prep
        .hot_jars
        .iter()
        .map(|jar| wire::upload_jar(&jar.sha256).path.into_owned())
        .collect();
    expected.sort();
    assert_eq!(uploaded, expected);
}

// The first upload that fails ends the push with its own refusal, and the uploads still in flight end with it: the
// double holds them open, so a push that waited for them would never return.
#[tokio::test]
async fn a_failed_upload_ends_the_others_and_keeps_its_refusal() {
    let (fixture, mut prep, state) = executing().await;
    let directory = tempfile::tempdir().unwrap();
    missing_hot_jars(&fixture, &mut prep, directory.path(), 2, 8 * MIB);
    let refused = prep.hot_jars[1].clone();
    {
        let mut script = fixture.daemon.script();
        script.upload_hold = Some(CancellationToken::new());
        script.upload_refused = Some((refused.sha256.clone(), 500));
    }
    let ctx = Ctx::background();
    let push = push_jars(&ctx, fixture.host.daemon(), &state, &prep);
    let refusal = tokio::time::timeout(Duration::from_secs(60), push)
        .await
        .expect("the push waited for the uploads that were still in flight")
        .expect_err("a refused upload was accepted");
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("daemon_push_failed", Exit::SOFTWARE));
    assert_eq!(refusal.message, format!("uploading {} returned 500", refused.path));
}

// --- selection parsing -------------------------------------------------------------------------------------------

// The usage ladder. What is a matter of spelling - a flag without its value, an unknown option, a filter the daemon
// cannot apply, a timeout that is not a positive whole number - is refused by the grammar itself; what needs the
// arguments together is refused by the parse, as a usage refusal at 2.
#[tokio::test]
async fn parse_run_refuses_the_usage_ladder() {
    for (argv, fragment) in [
        (&["--lane"][..], "--lane <LANE>"),
        (&["--frobnicate"], "unexpected argument '--frobnicate'"),
        (&["--filter"], "--filter <OPTION=VALUE>"),
        (&["--filter", "include-tag"], "expects <option>=<value>"),
        (&["--filter", "=x"], "expects <option>=<value>"),
        (&["--lane", "ui", "--filter", "include-engine=x"], "not one the daemon applies"),
        (
            &["--lane", "ui", "--timeout", "8.0"],
            "--timeout expects positive per-execution seconds",
        ),
        (
            &["--lane", "ui", "--progress-timeout", "0"],
            "--progress-timeout expects positive seconds",
        ),
        (&["--changed"], "--changed <PATH>"),
    ] {
        let message = rejected(argv);
        assert!(message.contains(fragment), "{argv:?}: {message}");
    }

    let fixture = Fixture::new().await;
    for (argv, fragment) in [
        (&[][..], "usage:"),
        (&["--lane", "ui", "SomeTest"], "usage:"),
        (&["OneTest", "TwoTest"], "pass exactly one test selector"),
        (&["--lane", "warp"], "not an IDE UI lane"),
        (&["--changed", ""], "--changed requires a path"),
    ] {
        let failure = refusal(fixture.host.parse_run(&Ctx::background(), &parsed(argv)).await);
        assert_eq!(
            (failure.code.as_ref(), failure.exit),
            ("usage", Exit::USAGE),
            "{argv:?}: {:?}",
            failure.message
        );
        assert!(failure.message.contains(fragment), "{argv:?}: {:?}", failure.message);
    }
}

const FLOW_PROFILE_DIR: &str = "plugins/air/tests/integration/flow-profiles/resources/flow-profiles";
const FLOW_TEXT_DIR: &str = "plugins/air/docs/flows";

pub(crate) fn write(root: &Path, relative: &str, content: &str) {
    let path = root.join(relative);
    fs::create_dir_all(path.parent().unwrap()).unwrap();
    fs::write(path, content).unwrap();
}

fn suite_document(suite: &str, lane: &str, class_name: &str, flow: &str, steps: &str) -> String {
    format!(
        r#"{{"suite": "{suite}", "lane": "{lane}", "testClassName": "{class_name}", "modules": [], "implementationFlows": [], "profiles": [{{"name": "{suite}-one", "flow": "{flow}", "steps": [{steps}]}}]}}"#
    )
}

/// One `ui` suite of `flow-preset`, and one source file per name that the flow tag reaches. The tag is what these
/// cases select by, so the suite reaches nothing else.
fn write_preset_suite(fixture: &Fixture, sources: &[&str]) {
    write(
        fixture.root(),
        &format!("{FLOW_PROFILE_DIR}/preset.json"),
        &suite_document("preset", "UI", "AirPresetGeneratedFlowUiTest", "flow-preset", ""),
    );
    for name in sources {
        write(
            fixture.root(),
            &format!("plugins/air/frontend/src/{name}"),
            "package air\n\n/**\n * @flow flow-preset\n */\nclass Preset\n",
        );
    }
}

/// The suite documents and story-flow text a flow or a suite selector reads: two UI suites and one GUI-chat suite of
/// one flow, one suite of a second flow, and one story flow with no suite at all.
pub(crate) fn suite_catalog(root: &Path) {
    let document = |suite: &str, lane: &str, class_name: &str, flow: &str| {
        write(
            root,
            &format!("{FLOW_PROFILE_DIR}/{suite}.json"),
            &suite_document(suite, lane, class_name, flow, &format!(r#"{{"flow": "{flow}"}}"#)),
        );
    };
    document("rename-session", "UI", "AirRenameSessionGeneratedFlowUiTest", "flow-rename-session");
    document(
        "new-session-native",
        "UI",
        "AirNewSessionNativeGeneratedFlowUiTest",
        "flow-new-session",
    );
    document(
        "new-session-shell",
        "UI",
        "AirNewSessionShellGeneratedFlowUiTest",
        "flow-new-session",
    );
    document(
        "new-session-chat",
        "GUI_CHAT",
        "AirNewSessionChatGeneratedFlowUiTest",
        "flow-new-session",
    );
    for flow in ["flow-rename-session", "flow-new-session", "flow-java-to-kotlin"] {
        write(root, &format!("{FLOW_TEXT_DIR}/{flow}.txt"), &format!("{flow}\n"));
    }
}

/// Adds `flow-three-lanes` to [`suite_catalog`]: one suite in each of the three lanes.
pub(crate) fn three_lane_catalog(root: &Path) {
    suite_catalog(root);
    for (suite, lane, class_name) in [
        ("three-lanes-ui", "UI", "AirThreeLanesUiGeneratedFlowUiTest"),
        ("three-lanes-real", "UI_REAL", "AirThreeLanesRealGeneratedFlowUiTest"),
        ("three-lanes-chat", "GUI_CHAT", "AirThreeLanesChatGeneratedFlowUiTest"),
    ] {
        write(
            root,
            &format!("{FLOW_PROFILE_DIR}/{suite}.json"),
            &suite_document(suite, lane, class_name, "flow-three-lanes", ""),
        );
    }
}

// `--changed` is the lane's tag plus one class filter per suite the change reaches, resolved from the checkout the
// fixture owns. What is pinned here is that a parsed `--changed` reaches the daemon as an ordinary selection and says
// what it selected; the refusals belong to the `lane` module, which owns the join.
#[tokio::test]
async fn parse_run_turns_a_changed_path_into_its_suites_class_filters() {
    let fixture = Fixture::new().await;
    write_preset_suite(&fixture, &["Preset.kt"]);
    let parsed_run = fixture
        .host
        .parse_run(&Ctx::background(), &parsed(&["--changed", "plugins/air/frontend/src/Preset.kt"]))
        .await
        .unwrap();
    assert_eq!(
        parsed_run.selection.junit5_filters,
        [
            "include-tag=air-flow-ui",
            r"include-classname=(^|.*\.)AirPresetGeneratedFlowUiTest$"
        ]
    );
    // The description is what a false-green verdict prints, so it names the classes it selected.
    assert_eq!(
        parsed_run.selection.description,
        "1 changed path(s) -> lane ui: AirPresetGeneratedFlowUiTest"
    );
}

// With `--changed`, every bare argument is a changed path. One `--changed` before three paths and one `--changed`
// per path are the same run.
#[tokio::test]
async fn parse_run_reads_every_bare_argument_as_a_changed_path() {
    let fixture = Fixture::new().await;
    write_preset_suite(&fixture, &["A.kt", "B.kt", "C.kt"]);
    let [a, b, c] = [
        "plugins/air/frontend/src/A.kt",
        "plugins/air/frontend/src/B.kt",
        "plugins/air/frontend/src/C.kt",
    ];
    for argv in [
        vec!["--changed", a, b, c],
        vec!["--changed", a, "--changed", b, "--changed", c],
        vec!["--lane", "ui", "--changed", a, b, "--fresh-ide", c],
    ] {
        let parsed_run = fixture
            .host
            .parse_run(&Ctx::background(), &parsed(&argv))
            .await
            .unwrap_or_else(|refusal| panic!("{argv:?}: {refusal:?}"));
        assert!(
            parsed_run
                .selection
                .description
                .starts_with("3 changed path(s) -> lane ui: AirPresetGeneratedFlowUiTest"),
            "{argv:?}: {}",
            parsed_run.selection.description
        );
    }
}

// A lane is its JUnit filter plus whatever `--filter` added, and the description says what narrowed it.
#[tokio::test]
async fn parse_run_composes_a_lanes_filters() {
    let fixture = Fixture::new().await;
    let parsed_run = fixture
        .host
        .parse_run(
            &Ctx::background(),
            &parsed(&["--lane", "ui", "--filter", "exclude-tag=slow", "--timeout", "600", "--fresh-ide"]),
        )
        .await
        .unwrap();
    assert!(
        parsed_run.selection.selectors.is_empty(),
        "a lane selects by filter, never by selector"
    );
    assert_eq!(parsed_run.selection.junit5_filters, ["include-tag=air-flow-ui", "exclude-tag=slow"]);
    assert_eq!(parsed_run.selection.description, "lane ui (filters: exclude-tag=slow)");
    // The requested timeout wins and the gap falls back to the environment's.
    assert_eq!(
        parsed_run.policy,
        WatchdogPolicy {
            active_execution: Duration::from_secs(600),
            progress_gap: Duration::from_secs(300),
        }
    );
    assert!(parsed_run.fresh_ide);
}

// An explicit-only lane is one `run` of its tag, and `shard` and `flake`, which read one selection to repeat it,
// refuse it by name: `--lane` and its own target alike. A repeat would multiply its billed turns.
#[tokio::test]
async fn shard_and_flake_refuse_an_explicit_only_lane_that_run_takes() {
    let fixture = Fixture::new().await;
    let plan = fixture
        .host
        .plan_run(&Ctx::background(), &parsed(&["--lane", "ui-live"]))
        .await
        .unwrap();
    assert_eq!(plan.iterations.len(), 1);
    assert_eq!(plan.iterations[0].selection.junit5_filters, ["include-tag=air-flow-ui-live"]);
    for argv in [
        &["--lane", "ui-live"][..],
        &["//plugins/air/tests/integration/ui-live:ui-live_test"][..],
    ] {
        let failure = refusal(fixture.host.parse_run(&Ctx::background(), &parsed(argv)).await);
        assert_eq!(
            (failure.code.as_ref(), failure.exit),
            ("explicit_only_lane", Exit::USAGE),
            "{argv:?}: {:?}",
            failure.message
        );
        assert!(failure.message.contains("run --lane ui-live"), "{argv:?}: {:?}", failure.message);
    }
    // A repeatable lane still parses, so the refusal is the lane's and not the command's.
    fixture
        .host
        .parse_run(&Ctx::background(), &parsed(&["--lane", "ui-real"]))
        .await
        .unwrap();
}

// The watchdog budgets come from the settings when no flag asks otherwise.
#[tokio::test]
async fn watchdog_budgets_come_from_the_environment() {
    let fixture = Fixture::with_environment(&[("AIR_VM_DAEMON_EXECUTION_TIMEOUT", "900"), ("AIR_VM_DAEMON_PROGRESS_TIMEOUT", "60")]).await;
    let parsed_run = fixture
        .host
        .parse_run(&Ctx::background(), &parsed(&["--lane", "ui"]))
        .await
        .unwrap();
    assert_eq!(
        parsed_run.policy,
        WatchdogPolicy {
            active_execution: Duration::from_mins(15),
            progress_gap: Duration::from_secs(60),
        }
    );
}

// A `--filter` is typed at the parse, and the request spells it back as it was written.
#[test]
fn a_filter_is_typed_and_spelled_back_as_written() {
    let filter = parse_filter("exclude-tag=slow").unwrap();
    assert_eq!(
        filter,
        Junit5Filter {
            kind: Junit5FilterKind::ExcludeTag,
            value: "slow".to_owned(),
        }
    );
    assert_eq!(filter.to_string(), "exclude-tag=slow");
    assert_eq!(parse_filter("include-classname=a=b").unwrap().value, "a=b");
    assert!(
        parse_filter("include-method=x")
            .unwrap_err()
            .contains("accepted: include-tag, exclude-tag")
    );
}

// The six JUnit5 filter options are the daemon's own list, verbatim: `AirUiDaemonIteration.buildFilters`.
#[test]
fn junit5_filter_options_match_the_daemons_six() {
    assert_eq!(
        Junit5FilterKind::ALL.map(Junit5FilterKind::as_str),
        [
            "include-tag",
            "exclude-tag",
            "include-classname",
            "exclude-classname",
            "include-package",
            "exclude-package",
        ]
    );
}

// A flow id and a suite id reach the daemon as the selection `--changed` makes: the lane's tag and one class filter
// per suite. The description names what was asked and what it selected, because a false-green verdict prints it.
#[tokio::test]
async fn parse_run_turns_a_flow_or_a_suite_into_its_suites_class_filters() {
    let fixture = Fixture::new().await;
    suite_catalog(fixture.root());
    let class = |name: &str| format!(r"include-classname=(^|.*\.){name}$");
    for (name, argv, filters, description) in [
        (
            "a flow",
            vec!["flow-rename-session"],
            vec!["include-tag=air-flow-ui".to_owned(), class("AirRenameSessionGeneratedFlowUiTest")],
            "flow-rename-session -> lane ui: AirRenameSessionGeneratedFlowUiTest",
        ),
        (
            "a suite",
            vec!["new-session-shell"],
            vec!["include-tag=air-flow-ui".to_owned(), class("AirNewSessionShellGeneratedFlowUiTest")],
            "new-session-shell -> lane ui: AirNewSessionShellGeneratedFlowUiTest",
        ),
        (
            "a two-lane flow settled by --lane",
            vec!["flow-new-session", "--lane", "ui"],
            vec![
                "include-tag=air-flow-ui".to_owned(),
                class("AirNewSessionNativeGeneratedFlowUiTest"),
                class("AirNewSessionShellGeneratedFlowUiTest"),
            ],
            "flow-new-session -> lane ui: AirNewSessionNativeGeneratedFlowUiTest, \
             AirNewSessionShellGeneratedFlowUiTest",
        ),
        (
            "a flow narrowed by --filter",
            vec!["flow-rename-session", "--filter", "exclude-tag=slow"],
            vec![
                "include-tag=air-flow-ui".to_owned(),
                class("AirRenameSessionGeneratedFlowUiTest"),
                "exclude-tag=slow".to_owned(),
            ],
            "flow-rename-session -> lane ui: AirRenameSessionGeneratedFlowUiTest (filters: exclude-tag=slow)",
        ),
    ] {
        let parsed_run = fixture
            .host
            .parse_run(&Ctx::background(), &parsed(&argv))
            .await
            .unwrap_or_else(|refusal| panic!("{name}: {refusal:?}"));
        assert_eq!(parsed_run.selection.junit5_filters, filters, "{name}");
        assert_eq!(parsed_run.selection.description, description, "{name}");
        assert!(parsed_run.selection.selectors.is_empty(), "{name}");
    }
}

// Each refusal is decided from the checkout, so it costs no build and no worker, and none of them is an empty
// selection.
#[tokio::test]
async fn parse_run_refuses_a_flow_it_cannot_run() {
    let fixture = Fixture::new().await;
    suite_catalog(fixture.root());
    for (name, argv, code, fragment) in [
        (
            "a two-lane flow",
            vec!["flow-new-session"],
            "affected_lanes_ambiguous",
            "the scenarios of flow-new-session reach 3 suite(s): ui 2, gui-chat 1; one iteration runs one lane, so \
             pass --lane with one of ui, gui-chat",
        ),
        (
            "a lane the flow does not reach",
            vec!["flow-new-session", "--lane", "ui-real"],
            "no_affected_suite",
            "no ui-real suite covers the scenarios of flow-new-session; they reach gui-chat, ui",
        ),
        (
            "a story flow no suite covers",
            vec!["flow-java-to-kotlin"],
            "no_affected_suite",
            "flow-java-to-kotlin: no e2e suite tests this flow",
        ),
        (
            "a flow id with a typo",
            vec!["flow-rename-sesion"],
            "usage",
            "did you mean  flow-rename-session",
        ),
        (
            "a suite id with a typo",
            vec!["rename-sesion"],
            "usage",
            "did you mean  rename-session",
        ),
        (
            "a flow with a method",
            vec!["flow-rename-session#one"],
            "usage",
            "cannot carry a #method",
        ),
        ("a class beside --lane", vec!["SomeTest", "--lane", "gui-chat"], "usage", "usage:"),
    ] {
        let failure = refusal(fixture.host.parse_run(&Ctx::background(), &parsed(&argv)).await);
        assert_eq!(
            (failure.code.as_ref(), failure.exit),
            (code, Exit::USAGE),
            "{name}: {:?}",
            failure.message
        );
        assert!(failure.message.contains(fragment), "{name}: {:?}", failure.message);
    }
    assert_eq!(fixture.bazel.build_calls(), 0, "a refused selection built");
}

// Without `--lane`, the plan holds one iteration per reached lane, in the declared lane order. A GUI-chat lane runs
// on the default Linux pool too, because the VM keeps its input off the host.
#[tokio::test]
async fn a_run_plan_holds_every_reached_lane() {
    let fixture = Fixture::new().await;
    three_lane_catalog(fixture.root());
    let ctx = Ctx::background();
    let plan = fixture.host.plan_run(&ctx, &parsed(&["flow-three-lanes"])).await.unwrap();
    let lanes: Vec<_> = plan
        .iterations
        .iter()
        .map(|iteration| iteration.lane.as_deref().unwrap_or_default())
        .collect();
    assert_eq!(lanes, ["ui", "ui-real", "gui-chat"]);
    let chat = &plan.iterations[2];
    assert_eq!(
        chat.selection.junit5_filters,
        [
            "include-tag=air-gui-chat",
            r"include-classname=(^|.*\.)AirThreeLanesChatGeneratedFlowUiTest$"
        ]
    );
    assert_eq!(chat.classes, ["AirThreeLanesChatGeneratedFlowUiTest"]);

    // A suite of the GUI-chat lane alone is one iteration of that lane, and `--lane` keeps its meaning.
    let alone = fixture.host.plan_run(&ctx, &parsed(&["new-session-chat"])).await.unwrap();
    assert_eq!(alone.iterations.len(), 1);
    assert_eq!(alone.iterations[0].lane.as_deref(), Some("gui-chat"));
    let named = fixture
        .host
        .plan_run(&ctx, &parsed(&["flow-three-lanes", "--lane", "ui-real"]))
        .await
        .unwrap();
    assert_eq!(named.iterations.len(), 1);
    assert_eq!(named.iterations[0].lane.as_deref(), Some("ui-real"));

    // `shard` and `flake` still read one selection, so the same answer is still refused for them.
    let failure = refusal(fixture.host.parse_run(&ctx, &parsed(&["flow-three-lanes"])).await);
    assert_eq!(failure.code, "affected_lanes_ambiguous");
}

// The journal and the history of earlier runs name a run by its command line, so the same options spell the same
// line whichever order they were typed in.
#[test]
fn the_same_options_spell_the_same_command_line() {
    let typed = parsed(&["--fresh-ide", "--filter", "exclude-tag=slow", "--lane", "ui"]);
    let reordered = parsed(&["--lane", "ui", "--filter", "exclude-tag=slow", "--fresh-ide"]);
    assert_eq!(typed.argv(), reordered.argv());
    assert_eq!(parsed(&typed.argv().iter().map(String::as_str).collect::<Vec<_>>()), typed);
}

// --- selectors ------------------------------------------------------------------------------------------------

#[test]
fn a_selector_becomes_either_a_selection_or_a_class_name_filter() {
    let cases: [(&str, &[&str], &[&str]); 3] = [
        ("com.example.AirSmokeTest", &["com.example.AirSmokeTest"], &[]),
        (
            "com.example.AirSmokeTest#opensAProject",
            &["com.example.AirSmokeTest#opensAProject"],
            &[],
        ),
        // No package to select by, so it travels as a regex over a full hot-tier scan.
        ("AirSmokeTest", &[], &[r"include-classname=(^|.*\.)AirSmokeTest$"]),
    ];
    for (filter, want_selectors, want_filters) in cases {
        let (selectors, junit5_filters) = selector_to_run_request(filter);
        assert_eq!(selectors, want_selectors, "{filter}");
        assert_eq!(junit5_filters, want_filters, "{filter}");
        // Never null on the wire: the far end is Kotlin with non-nullable fields.
        let encoded = serde_json::to_value(RunRequestBody {
            selectors: &selectors,
            junit5_filters: &junit5_filters,
            product_stamp: "",
            launch_digest: "",
            hot_jars: Vec::new(),
            active_execution_timeout_sec: 0,
            progress_gap_timeout_sec: 0,
            ide_plugins: Vec::new(),
        })
        .unwrap();
        assert!(encoded["selectors"].is_array(), "{filter}: {encoded}");
        assert!(encoded["idePlugins"].is_array(), "{filter}: {encoded}");
        assert!(encoded["junit5Filters"].is_array(), "{filter}: {encoded}");
    }
}
