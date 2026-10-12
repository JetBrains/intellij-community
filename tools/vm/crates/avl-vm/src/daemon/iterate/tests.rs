use std::io::Write as _;
use std::path::Path;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use avl_host_testkit::agent::{agent_reply, answer_pull};
use avl_host_testkit::git::FAKE_HEAD;
use avl_host_testkit::{answer_exit, answer_text, handler};
use avl_testkit::tartfake::Answer;
use avl_trace::bundle::{ROOT_DIR_NAME, SPANS_FILE, VIDEO_FILE};
use avl_trace_tools::discover::{SourceKind, bundle_id};
use avl_trace_tools::pack::Report;
use avl_wire::daemon::{RunEventKind, WatchdogExpired};
use avl_wire::progress::Kind;
use avl_wire::report::{Status, decode_run_report};
use avl_wire::verb::AgentVerb;
use pretty_assertions::assert_eq;
use regex::Regex;
use serde_json::json;

use super::*;
use crate::daemon::fixture::Fixture;
use crate::lane::secrets::{RunSecretFile, ScriptedStdin, SecretSource};
use avl_base::RefusalExt;

/// The pack verb, as the fake channel matches it in an argv.
const TRACE_PACK_READY_VERB: &str = AgentVerb::TracePackReady.as_str();
use crate::daemon::testing::{PASSING_XML, PLENTIFUL_DF, Recorded, passing_run_lines};
use crate::daemon::traces::TRACE_PACK_TIMEOUT;

// --- the decisions ---------------------------------------------------------------------------------------------

const BUILD: DaemonDigests<'static> = DaemonDigests {
    launch: "launch-1",
    mount: "mount-1",
    runtime: "runtime-1",
};

fn healthy(launch: &'static str, mount: &'static str, runtime: &'static str, ide_running: bool) -> HealthyDaemon<'static> {
    HealthyDaemon {
        recorded: DaemonDigests { launch, mount, runtime },
        ide_running,
    }
}

// A healthy daemon of this launch serves the iteration, refreshed or relaunched when an axis asks for it; any other
// daemon is started, and the decision names which axis moved.
#[test]
fn the_daemon_decision_names_the_axis_that_moved() {
    let start = |verb, reason| DaemonAction::Start { verb, reason };
    for (seen, fresh_ide, expected) in [
        (None, false, start("start", "no healthy daemon")),
        (Some(healthy("launch-1", "mount-1", "runtime-1", true)), false, DaemonAction::Reuse),
        (Some(healthy("launch-1", "mount-0", "runtime-1", true)), true, DaemonAction::Remount),
        (
            Some(healthy("launch-1", "mount-1", "runtime-1", true)),
            true,
            DaemonAction::Relaunch { stop_ide: true },
        ),
        (
            Some(healthy("launch-1", "mount-1", "runtime-1", false)),
            true,
            DaemonAction::Relaunch { stop_ide: false },
        ),
        (
            Some(healthy("launch-0", "mount-1", "runtime-0", true)),
            false,
            start("restart", "the stable runtime or JBR changed"),
        ),
        (
            Some(healthy("launch-0", "mount-1", "runtime-1", true)),
            true,
            start("restart", "the controller boot settings changed"),
        ),
    ] {
        assert_eq!(
            decide_daemon_action(seen, BUILD, fresh_ide),
            expected,
            "{seen:?} fresh_ide={fresh_ide}"
        );
    }
}

#[test]
fn the_push_notes_count_the_changed_jars() {
    assert_eq!(reuse_reason(0), "no daemon input and no test jar changed");
    assert_eq!(reuse_reason(2), "the daemon inputs did not change");
    assert_eq!(jar_push_reason(0), None);
    assert_eq!(jar_push_reason(1).as_deref(), Some("1 changed test jar"));
    assert_eq!(jar_push_reason(3).as_deref(), Some("3 changed test jars"));
}

// A stream that never named its iteration answers its own protocol failure, or `daemon_run_failed`, with the timing;
// the report's diagnostic joins the protocol failure and the daemon's error.
#[test]
fn a_stream_without_its_iteration_id_is_refused_with_the_timing() {
    let silent = RunExecution::default();
    let refusal = unaddressed_iteration(&silent, "build 1s");
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("daemon_run_failed", Exit::SOFTWARE));
    assert_eq!(
        refusal.message,
        "the daemon stream ended before identifying its iteration\nbuild 1s"
    );
    assert_eq!(protocol_diagnostic(&silent), None);

    let broken = RunExecution {
        protocol_failure: Some(Refusal::new("daemon_protocol", Exit::DATA_ERR, "a bad record")),
        ..RunExecution::default()
    };
    let refusal = unaddressed_iteration(&broken, "build 1s");
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("daemon_protocol", Exit::DATA_ERR));
    assert_eq!(protocol_diagnostic(&broken).as_deref(), Some("a bad record"));
}

const POLICY: WatchdogPolicy = WatchdogPolicy {
    active_execution: Duration::from_mins(30),
    progress_gap: Duration::from_secs(300),
};

fn lane_ui() -> RunSelection {
    RunSelection {
        selectors: Vec::new(),
        junit5_filters: Vec::new(),
        description: "lane ui".to_owned(),
    }
}

/// Everything one warm iteration needs: a healthy daemon, a passing stream, and the JUnit XML its report reads.
async fn iteration_fixture(iteration_id: &str) -> (Fixture, PreparedBuild, HostState) {
    iteration_fixture_over(Fixture::new().await, iteration_id).await
}

/// [`iteration_fixture`] over a given fixture. On a Docker pool the container is then current and running.
async fn iteration_fixture_over(fixture: Fixture, iteration_id: &str) -> (Fixture, PreparedBuild, HostState) {
    fixture.on("df", answer_text(PLENTIFUL_DF));
    let prep = fixture.prepared().await;
    let state = fixture.seed_healthy_daemon(&prep);
    fixture.seed_current_container().await;
    fixture.script_run(passing_run_lines(iteration_id), PASSING_XML);
    (fixture, prep, state)
}

/// One iteration inside the run `suite-run-1`, which is where its traces go, over a fresh build.
async fn run_warm_iteration(fixture: &Fixture, fresh_ide: bool) -> RunAttempt {
    run_iteration_with(fixture, &Ctx::background(), fresh_ide, &RunSecrets::default()).await
}

/// [`run_warm_iteration`] under `ctx`, with `secrets` as `run --test-env` hands them over.
async fn run_iteration_with(fixture: &Fixture, ctx: &Ctx, fresh_ide: bool, secrets: &RunSecrets) -> RunAttempt {
    let built = fixture.prepared().await;
    fixture
        .host
        .run_one_iteration(
            &ctx.clone().with_run_id("suite-run-1"),
            &fixture.lease(),
            &built,
            &lane_ui(),
            POLICY,
            fresh_ide,
            secrets,
        )
        .await
}

fn report_of(attempt: &RunAttempt) -> &RunReport {
    match (&attempt.report, &attempt.error) {
        (Some((report, _)), _) => report,
        (None, error) => panic!("the iteration left no report: {error:?}"),
    }
}

fn error_of(attempt: &RunAttempt) -> &Refusal {
    attempt
        .error
        .as_ref()
        .unwrap_or_else(|| panic!("the iteration was expected to fail: {:?}", attempt.report))
}

// The warm path: a healthy daemon whose digests match runs the iteration with no restart, no remount and no relaunch,
// and leaves a persisted report and the exact timing line behind.
#[tokio::test]
async fn a_warm_iteration_reuses_the_daemon() {
    let (fixture, prep, _) = iteration_fixture("it-10").await;
    let attempt = run_warm_iteration(&fixture, false).await;
    let report = report_of(&attempt);
    assert_eq!(attempt.ide_action, IdeAction::Reuse);
    let timing = Regex::new(r"^build \d+\.\ds {2}stamp \d+\.\ds {2}push \d+ jar\(s\) \d+\.\ds {2}ide [a-z ]+ {2}tests \d+\.\ds$").unwrap();
    assert!(
        timing.is_match(&attempt.timing),
        "the timing line's spelling is contract, was {:?}",
        attempt.timing
    );
    assert!(
        attempt.timing.contains("  ide reuse  ") && attempt.timing.contains("push 0 jar(s)"),
        "{:?}",
        attempt.timing
    );
    assert_eq!(report.status, Status::Passed);
    let (_, path) = attempt.report.as_ref().unwrap();
    assert!(path.is_file(), "the report is persisted at {}", path.display());
    assert!(!fixture.daemon.saw_request("POST /mount/quiesce"));
    assert!(!fixture.daemon.saw_request("POST /ide/stop"));
    assert!(
        fixture.channel().calls_containing("vm-guest-agent start").is_empty(),
        "the warm path never starts a daemon"
    );
    // The state file carries the just-run build's product and mount digests forward.
    let recorded = HostState::read(&fixture.settings, &fixture.worker).unwrap();
    assert_eq!(recorded.last_product_digest, prep.product_digest);
    assert_eq!(recorded.last_mount_digest, prep.mount_digest);
}

/// The remount script *executions* the scripted guest saw.
fn remounts(fixture: &Fixture) -> usize {
    fixture.channel().calls_containing("/bin/sh /vm/data/state/remount-shares.sh").len()
}

// On Tart a moved mount digest quiesces the daemon around the remount and resumes it, without a restart.
#[tokio::test]
async fn a_mount_digest_mismatch_quiesces_remounts_and_resumes() {
    let (fixture, prep, mut state) = iteration_fixture_over(Fixture::tart_macos().await, "it-11").await;
    state.last_mount_digest = "something-else".to_owned();
    state.write(&fixture.settings, &fixture.worker).unwrap();
    let attempt = run_warm_iteration(&fixture, false).await;
    report_of(&attempt);
    assert_eq!(attempt.ide_action, IdeAction::Remount);
    assert!(attempt.timing.contains("  ide remount  "), "{:?}", attempt.timing);
    assert!(fixture.daemon.saw_request("POST /mount/quiesce"));
    assert!(fixture.daemon.saw_request("POST /mount/resume"));
    assert!(
        !fixture
            .channel()
            .calls_containing("/usr/bin/tee /vm/data/state/remount-shares.sh")
            .is_empty(),
        "the remount script is written"
    );
    assert_eq!(remounts(&fixture), 1, "the shares are actually remounted");
    assert!(
        fixture.channel().calls_containing("vm-guest-agent start").is_empty(),
        "a share-backed change never restarts the daemon"
    );
    // The persisted state carries the run's mount digest forward, so the next iteration skips the quiesce.
    let recorded = HostState::read(&fixture.settings, &fixture.worker).unwrap();
    assert_eq!(recorded.last_mount_digest, prep.mount_digest);
}

// On Docker a moved mount digest still quiesces and resumes the daemon and still reports `ide remount`, but the guest
// sees no remount script: the shares are bind mounts, and the refresh is a settle. The scripted guest is where every
// guest command lands, and the fake `docker` log shows the backend was Docker and ran no guest command of its own.
#[tokio::test]
async fn a_mount_digest_mismatch_on_docker_settles_without_a_remount_script() {
    let (fixture, prep, mut state) = iteration_fixture_over(Fixture::docker().await, "it-11d").await;
    state.last_mount_digest = "something-else".to_owned();
    state.write(&fixture.settings, &fixture.worker).unwrap();
    let attempt = run_warm_iteration(&fixture, false).await;
    report_of(&attempt);
    assert_eq!(attempt.ide_action, IdeAction::Remount);
    assert!(attempt.timing.contains("  ide remount  "), "{:?}", attempt.timing);
    assert!(fixture.daemon.saw_request("POST /mount/quiesce"));
    assert!(fixture.daemon.saw_request("POST /mount/resume"));
    let scripted = fixture.channel().calls_containing("remount-shares.sh");
    assert!(scripted.is_empty(), "{scripted:#?}");
    let said = fixture.stdout.text() + &fixture.stderr.text();
    assert!(said.contains("bind mounts serve fresh bytes"), "{said}");
    let calls = fixture.tart.calls();
    assert!(calls.iter().any(|call| call.starts_with("version ")), "{calls:#?}");
    assert!(!calls.iter().any(|call| call.contains("remount-shares.sh")), "{calls:#?}");
    assert!(
        fixture.channel().calls_containing("vm-guest-agent start").is_empty(),
        "a share-backed change never restarts the daemon"
    );
    let recorded = HostState::read(&fixture.settings, &fixture.worker).unwrap();
    assert_eq!(recorded.last_mount_digest, prep.mount_digest);
}

// A warm iteration on Docker asks whether the container is current, because a new image tag (a new guest package),
// other shares or another display need a new container. A stale create record makes the iteration recreate the
// container under its own lease and start the daemon again; the record of the old daemon is not probed.
#[tokio::test]
async fn a_warm_iteration_on_docker_recreates_a_container_of_another_declaration() {
    let (fixture, prep, _state) = iteration_fixture_over(Fixture::docker().await, "it-12d").await;
    // The new container needs a daemon start: the guest and the daemon double answer one.
    fixture.install_happy_guest(&prep);
    fixture.daemon.script().launch_digest = prep.launch_digest.clone();
    let record_path = fixture.settings.docker_create_record_path(&fixture.worker);
    let mut record: serde_json::Value = serde_json::from_slice(&std::fs::read(&record_path).unwrap()).unwrap();
    let argv = record["argv"].as_array_mut().unwrap();
    *argv.last_mut().unwrap() = json!("air-ui-worker:000000000000");
    std::fs::write(&record_path, record.to_string()).unwrap();
    let before = fixture.tart.calls().len();

    let attempt = run_warm_iteration(&fixture, false).await;
    report_of(&attempt);
    assert_eq!(attempt.ide_action, IdeAction::DaemonRestart);
    let worker = fixture.worker.as_str();
    let after = fixture.tart.calls()[before..].to_vec();
    let position = |prefix: &str| {
        after
            .iter()
            .position(|call| call.starts_with(prefix))
            .unwrap_or_else(|| panic!("no {prefix:?} call in {after:#?}"))
    };
    assert!(position(&format!("rm {worker}")) < position("create "));
    assert!(position("create ") < position(&format!("start {worker}")));
    assert!(
        !fixture.channel().calls_containing("vm-guest-agent start").is_empty(),
        "the daemon starts again on the new container"
    );
    let said = fixture.stdout.text() + &fixture.stderr.text();
    assert!(said.contains("the container's declaration changed"), "{said}");
}

// A current container is kept by a warm iteration: nothing is removed, created or started, and the daemon is reused.
#[tokio::test]
async fn a_warm_iteration_on_docker_keeps_a_current_container() {
    let (fixture, _prep, _state) = iteration_fixture_over(Fixture::docker().await, "it-13d").await;
    let before = fixture.tart.calls().len();
    let attempt = run_warm_iteration(&fixture, false).await;
    report_of(&attempt);
    assert_eq!(attempt.ide_action, IdeAction::Reuse);
    let worker = fixture.worker.as_str();
    let after = fixture.tart.calls()[before..].to_vec();
    assert!(
        !after
            .iter()
            .any(|call| call.starts_with("rm ") || call.starts_with("create ") || call.starts_with(&format!("start {worker}"))),
        "{after:#?}"
    );
    assert!(
        fixture.channel().calls_containing("vm-guest-agent start").is_empty(),
        "a current container keeps its warm daemon"
    );
}

// `--fresh-ide` stops a running IDE and reports the relaunch.
#[tokio::test]
async fn fresh_ide_stops_a_running_ide() {
    let (fixture, _, _) = iteration_fixture("it-12").await;
    fixture.daemon.script().ide_running = true;
    let attempt = run_warm_iteration(&fixture, true).await;
    report_of(&attempt);
    assert_eq!(attempt.ide_action, IdeAction::Relaunch);
    assert!(fixture.daemon.saw_request("POST /ide/stop"));
}

// No healthy daemon starts one: the worker is brought to readiness first, the daemon is started, and the action says
// what happened.
#[tokio::test]
async fn no_healthy_daemon_restarts_the_daemon() {
    let fixture = Fixture::new().await;
    let prep = fixture.prepared().await;
    fixture.install_happy_guest(&prep);
    fixture.daemon.script().launch_digest = prep.launch_digest.clone();
    fixture.script_run(passing_run_lines("it-13"), PASSING_XML);
    // Readiness needs a live run process and a provisioned layout; both are the suite's to arrange.
    fixture.mark_ready().await;
    let recorded = Recorded::start(&fixture.host.reporter);
    let attempt = run_warm_iteration(&fixture, false).await;
    report_of(&attempt);
    assert_eq!(attempt.ide_action, IdeAction::DaemonRestart);
    assert_eq!(
        fixture.channel().calls_containing("vm-guest-agent start").len(),
        1,
        "the daemon is actually started"
    );
    assert!(attempt.timing.contains("ide daemon restart"), "{:?}", attempt.timing);
    let decisions: Vec<serde_json::Value> = recorded.data_of(Kind::Decision);
    assert!(
        decisions.contains(&json!({"subject": "daemon", "action": "start", "reason": "no healthy daemon"})),
        "the decision names why the daemon started: {decisions:?}"
    );
}

// A stream that ends before `runStarted` is a refusal carrying the report-unavailable detail: no iteration id means
// no result can be addressed safely.
#[tokio::test]
async fn a_stream_without_an_iteration_id_refuses_with_report_unavailable() {
    let (fixture, _, _) = iteration_fixture("unused").await;
    fixture.daemon.script().run_lines = vec!["nothing but stdout noise".to_owned()];
    let attempt = run_warm_iteration(&fixture, false).await;
    let error = error_of(&attempt);
    assert_eq!(error.code, "daemon_run_failed");
    assert!(
        error.message.contains("ended before identifying its iteration"),
        "{:?}",
        error.message
    );
    assert!(error.message.contains("\nbuild "), "the refusal still ends with the timing line");
    let details = error.details().unwrap();
    assert!(details["report"].is_null());
    assert!(
        details["reportUnavailable"]
            .as_str()
            .unwrap()
            .contains("runStarted was not received")
    );
    assert!(
        attempt.execution.is_some(),
        "the events read are the attempt's evidence even when it failed"
    );
    assert!(attempt.report.is_none());
}

// A failure anywhere inside becomes the attempt's error, as a value: the attempt is an answer, not an unwound stack.
#[tokio::test]
async fn an_unclassified_failure_becomes_internal_error() {
    let (fixture, _, _) = iteration_fixture("it-14").await;
    fixture.on("df", handler(|_, _| Err(Refusal::internal("deadline exceeded"))));
    let attempt = run_warm_iteration(&fixture, false).await;
    let error = error_of(&attempt);
    assert_eq!((error.code.as_ref(), error.exit), ("internal_error", Exit::FAILURE));
    assert!(attempt.report.is_none());
}

// The JUnit XML falls back to the guest pull when the daemon's endpoint cannot answer, and the HTTP half of the story
// is carried into the fallback's diagnostic.
#[tokio::test]
async fn junit_retrieval_falls_back_to_the_guest_pull() {
    let (fixture, _, state) = iteration_fixture("it-15").await;
    fixture.daemon.script().results_status = Some(500);
    answer_pull(&fixture.tart, PASSING_XML.as_bytes());
    let retrieved = fixture
        .host
        .retrieve_iteration_junit_xml(&Ctx::background(), &fixture.worker, &state, "it-15")
        .await
        .unwrap();
    assert_eq!(
        (retrieved.retrieval, retrieved.integrity),
        (Retrieval::GuestPull, RetrievedIntegrity::Available)
    );
    assert_eq!(retrieved.xml.as_deref(), Some(PASSING_XML));
    assert!(
        retrieved
            .diagnostic
            .as_deref()
            .is_some_and(|diagnostic| diagnostic.contains("/results returned HTTP 500")),
        "{:?}",
        retrieved.diagnostic
    );
    assert_eq!(retrieved.guest_path, "/vm/data/daemon/iterations/it-15/test.xml");
}

// Both retrieval paths enforce the size limit; an oversized document is never read into a report.
#[tokio::test]
async fn oversized_junit_xml_is_refused_on_both_paths() {
    let (fixture, _, state) = iteration_fixture("it-16").await;
    let oversized = vec![b'x'; MAX_JUNIT_XML_BYTES + 1];
    fixture.daemon.script().results_xml = oversized.clone();
    let ctx = Ctx::background();
    let retrieved = fixture
        .host
        .retrieve_iteration_junit_xml(&ctx, &fixture.worker, &state, "it-16")
        .await
        .unwrap();
    assert_eq!(
        (retrieved.retrieval, retrieved.integrity),
        (Retrieval::DaemonHttp, RetrievedIntegrity::Oversized)
    );

    fixture.daemon.script().results_status = Some(404);
    answer_pull(&fixture.tart, &oversized);
    let retrieved = fixture
        .host
        .retrieve_iteration_junit_xml(&ctx, &fixture.worker, &state, "it-16b")
        .await
        .unwrap();
    assert_eq!(
        (retrieved.retrieval, retrieved.integrity),
        (Retrieval::GuestPull, RetrievedIntegrity::Oversized)
    );
    assert!(retrieved.xml.is_none());
}

// A JUnit XML nobody can produce is an honest unavailable/missing with both halves of the story joined.
#[tokio::test]
async fn unretrievable_junit_xml_carries_both_diagnostics() {
    let (fixture, _, state) = iteration_fixture("it-17").await;
    fixture.daemon.script().results_status = Some(500);
    fixture.tart.answer(Answer::ReadFileFails, "1");
    let retrieved = fixture
        .host
        .retrieve_iteration_junit_xml(&Ctx::background(), &fixture.worker, &state, "it-17")
        .await
        .unwrap();
    assert_eq!(
        (retrieved.retrieval, retrieved.integrity),
        (Retrieval::Unavailable, RetrievedIntegrity::Missing)
    );
    let diagnostic = retrieved.diagnostic.unwrap_or_default();
    assert!(
        diagnostic.contains("/results returned HTTP 500") && diagnostic.contains("guest pull failed"),
        "{diagnostic:?}"
    );
}

// Evidence file names join the last two guest path segments with `_` and sanitize the rest, so
// `001_heartbeat/dialog0.png` and `005_heartbeat/dialog0.png` stay distinct; a file that cannot be fetched is recorded
// with its reason, never dropped.
#[tokio::test]
async fn evidence_names_join_the_last_two_segments_and_failures_are_recorded() {
    let (fixture, _, state) = iteration_fixture("it-18").await;
    let events = vec![RunEvent::synthesized(RunEventKind::WatchdogExpired(WatchdogExpired {
        reason: "active_execution_timeout".to_owned(),
        deadline: "t".to_owned(),
        expired_at: "t".to_owned(),
        detail: None,
        active_execution: None,
        evidence: vec![
            "/vm/out/001_heartbeat/dialog0.png".to_owned(),
            "/vm/out/002 heartbeat/dial og.png".to_owned(),
        ],
    }))];
    assert_eq!(evidence_name("/vm/out/001_heartbeat/dialog0.png"), "001_heartbeat_dialog0.png");
    assert_eq!(evidence_name("/vm/out/002 heartbeat/dial og.png"), "002-heartbeat_dial-og.png");
    let ctx = Ctx::background();
    answer_pull(&fixture.tart, b"png-bytes");
    let artifacts = fixture
        .host
        .pull_run_evidence(&ctx, &fixture.worker, &state.run_id, "it-18", &events)
        .await
        .unwrap();
    let names: Vec<_> = artifacts
        .iter()
        .map(|artifact| {
            let path = artifact.artifact_path.as_deref().expect("a fetched artifact");
            Path::new(path).file_name().unwrap().to_string_lossy().into_owned()
        })
        .collect();
    assert_eq!(names, ["001_heartbeat_dialog0.png", "002-heartbeat_dial-og.png"]);

    fixture.tart.answer(Answer::ReadFileFails, "1");
    let failed = fixture
        .host
        .pull_run_evidence(&ctx, &fixture.worker, &state.run_id, "it-18b", &events)
        .await
        .unwrap();
    assert_eq!(failed.len(), 2, "a failed fetch is recorded, never dropped");
    assert!(failed[0].error.is_some() && failed[0].artifact_path.is_none(), "{failed:?}");
}

// --- the scenario traces -----------------------------------------------------------------------------------------

/// A zip that holds one bundle, found by its spans, the way the guest agent packs one.
fn bundle_zip(entry: &str) -> Vec<u8> {
    let mut writer = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
    for name in [SPANS_FILE, VIDEO_FILE] {
        writer
            .start_file(format!("{entry}{name}"), zip::write::SimpleFileOptions::default())
            .unwrap();
        writer.write_all(b"{}\n").unwrap();
    }
    writer.finish().unwrap().into_inner()
}

/// A warm iteration whose lane recorded traces, armed by [`arm_traces`].
async fn traces_fixture(iteration_id: &str) -> (Fixture, Arc<Mutex<Vec<Vec<String>>>>) {
    let (fixture, _, _) = iteration_fixture(iteration_id).await;
    let packed = arm_traces(&fixture, iteration_id);
    (fixture, packed)
}

/// Arms `fixture` for one iteration whose lane recorded traces: the daemon streams the iteration, the guest reports
/// the iteration's trace directory, the agent's pack verb packs one bundle on its first call and nothing after, and
/// the pull answers that bundle's zip. A test arms it again for each of its cases. Answers the argv of every pack.
fn arm_traces(fixture: &Fixture, iteration_id: &str) -> Arc<Mutex<Vec<Vec<String>>>> {
    fixture.script_run(passing_run_lines(iteration_id), PASSING_XML);
    fixture.on("test", answer_exit(0));
    let packed: Arc<Mutex<Vec<Vec<String>>>> = Arc::default();
    fixture.on(TRACE_PACK_READY_VERB, {
        let packed = Arc::clone(&packed);
        handler(move |argv, _| {
            let mut packed = packed.lock().unwrap();
            packed.push(argv.to_vec());
            // The whole pack report the agent writes, so a field the controller newly requires fails here.
            let report = if packed.len() == 1 {
                let all = argv.last().is_some_and(|last| last == "--all");
                let destination = &argv[argv.len() - if all { 4 } else { 3 }];
                Report {
                    destination: Some(destination.into()),
                    packed: vec!["run-1/AirTest/opens".to_owned()],
                    ..Report::default()
                }
            } else {
                Report::default()
            };
            let data = serde_json::to_value(report).unwrap();
            Ok(agent_reply(TRACE_PACK_READY_VERB, &data))
        })
    });
    fixture.tart.forget(Answer::ReadFileFails);
    answer_pull(&fixture.tart, &bundle_zip("run-1/AirTest/opens/"));
    packed
}

// An iteration's finished traces are packed where the daemon put them, pulled into the run's directory as they
// arrive, named on the report with their bundles, and published as `traceReady` with the viewer's id.
#[tokio::test]
async fn an_iteration_pulls_its_traces_into_the_run() {
    let (fixture, packed) = traces_fixture("it-30").await;
    let recorded = Recorded::start(&fixture.host.reporter);
    let attempt = run_warm_iteration(&fixture, false).await;
    let report = report_of(&attempt);
    let guest_directory = format!("/vm/data/daemon/iterations/it-30/{ROOT_DIR_NAME}");
    let packed = packed.lock().unwrap().clone();
    assert!(
        packed[0].join(" ").contains(&format!(
            "{TRACE_PACK_READY_VERB} {guest_directory} {guest_directory}-001.zip {guest_directory}.ledger"
        )),
        "the pack verb was asked {packed:?}"
    );
    assert_eq!(
        packed.last().unwrap().last().map(String::as_str),
        Some("--all"),
        "the last pull takes the unfinished bundles"
    );
    assert_eq!(
        fixture
            .channel()
            .options_for_call_containing(TRACE_PACK_READY_VERB)
            .map(|options| options.timeout),
        Some(TRACE_PACK_TIMEOUT),
        "the pack is bounded by its own timeout"
    );
    let want = fscopy::resolve_links(&fixture.settings.runtime_root.join("runs/suite-run-1/traces/it-30"))
        .unwrap()
        .join("001.zip")
        .to_string_lossy()
        .into_owned();
    assert_eq!(report.trace_archives.len(), 1, "{:?}", report.trace_archives);
    assert_eq!(report.trace_archives[0].path, want);
    let bundles = &report.trace_archives[0].bundles;
    assert_eq!(bundles.len(), 1, "{bundles:?}");
    assert_eq!(bundles[0].id, bundle_id(SourceKind::Zip, &want, "run-1/AirTest/opens/"));
    assert_eq!(
        (bundles[0].test_class.as_str(), bundles[0].scenario.as_str(), bundles[0].has_video),
        ("AirTest", "opens", true)
    );
    assert_eq!(report.traces_error, None);
    let ready: Vec<avl_wire::progress::TraceReady> = recorded.data_of(Kind::TraceReady);
    assert_eq!(ready.len(), 1, "one traceReady per bundle: {ready:?}");
    assert_eq!(
        (ready[0].bundle_id.as_str(), ready[0].zip.as_str(), ready[0].iteration_id.as_str()),
        (bundles[0].id.as_str(), want.as_str(), "it-30")
    );
    assert!(
        fixture.tart.saw_call_containing(&format!("{guest_directory}-001.zip")),
        "the zip the agent wrote is the file that was pulled"
    );
    let (_, path) = attempt.report.as_ref().unwrap();
    let persisted = decode_run_report(&std::fs::read(path).unwrap()).unwrap();
    assert_eq!(persisted.trace_archives[0].path, want);
    assert_eq!(report.status, Status::Passed, "the run's verdict is its own");
}

// Every way the traces can fail to arrive is the report's `tracesError`, and none of them is a failed iteration or a
// different verdict: a trace explains a run, and an explanation that did not arrive does not change what the run did.
#[tokio::test]
async fn a_traces_failure_is_recorded_and_never_the_verdict() {
    type Arrange = fn(&Fixture);
    let cases: [(&str, Arrange, &str); 4] = [
        (
            "the probe cannot reach the guest",
            |fixture| {
                fixture.on("test", handler(|_, _| Err(Refusal::internal("deadline exceeded"))));
            },
            "cannot ask whether",
        ),
        (
            "the probe answers neither yes nor no",
            |fixture| fixture.on("test", answer_exit(2)),
            "exited with 2",
        ),
        (
            "the agent refuses to pack",
            |fixture| {
                fixture.on(
                    TRACE_PACK_READY_VERB,
                    handler(|_, _| {
                        Ok(avl_host_sys::Captured {
                            exit_code: 70,
                            stderr: "no space left on device".to_owned(),
                            ..avl_host_sys::Captured::default()
                        })
                    }),
                );
            },
            TRACE_PACK_READY_VERB,
        ),
        (
            "the pull fails",
            |fixture| fixture.tart.answer(Answer::ReadFileFails, "1"),
            "-001.zip",
        ),
    ];
    let (fixture, _, _) = iteration_fixture("it-40").await;
    for (index, (what, arrange, says)) in cases.into_iter().enumerate() {
        arm_traces(&fixture, &format!("it-4{index}"));
        arrange(&fixture);
        let attempt = run_warm_iteration(&fixture, false).await;
        let report = report_of(&attempt);
        assert!(
            report.traces_error.as_deref().is_some_and(|error| error.contains(says)),
            "{what}: the report says {:?} / {:?}",
            report.trace_archives,
            report.traces_error
        );
        assert_eq!(report.status, Status::Passed, "{what}");
        assert_eq!(report.verdict_diagnostic, None, "{what}");
    }
}

// An iteration whose lane recorded nothing - tracing off, or a lane from before the recorder - has nothing to report
// either way, and asks the agent for nothing.
#[tokio::test]
async fn an_iteration_without_traces_reports_neither() {
    let (fixture, _, _) = iteration_fixture("it-31").await;
    let attempt = run_warm_iteration(&fixture, false).await;
    let report = report_of(&attempt);
    assert!(report.trace_archives.is_empty());
    assert_eq!(report.traces_error, None);
    assert!(
        fixture.channel().calls_containing(TRACE_PACK_READY_VERB).is_empty(),
        "nothing is packed when nothing was recorded"
    );
}

// A warm iteration passes the hypervisor's gate before its first guest command, as every worker operation must: the
// gate resolves the pinned Tart on demand, so an iteration that skipped it ran `tart exec` with no executable. A Tart
// the gate cannot use is the gate's own refusal, and no guest was asked anything.
#[tokio::test]
async fn a_warm_iteration_passes_the_hypervisor_gate_first() {
    let (fixture, _, _) = iteration_fixture_over(Fixture::tart_macos().await, "it-32").await;
    fixture.tart.answer(Answer::VersionExit, "1");
    let attempt = run_warm_iteration(&fixture, false).await;
    let error = error_of(&attempt);
    assert_eq!(error.code, "tart_missing", "{:?}", error.message);
    assert!(fixture.channel().calls().is_empty(), "a guest call ran before the gate");
}

// The build reads the tree, and the persisted report names it.
#[tokio::test]
async fn the_run_report_names_the_tree_the_build_read() {
    let (fixture, _, _) = iteration_fixture("it-50").await;
    fixture.git().status(&[" M plugins/air/a.kt"]);
    let attempt = run_warm_iteration(&fixture, false).await;
    report_of(&attempt);
    let (_, path) = attempt.report.as_ref().unwrap();
    let persisted = decode_run_report(&std::fs::read(path).unwrap()).unwrap();
    let tree = persisted.tree.expect("the report names the tree");
    assert_eq!(tree.head, FAKE_HEAD);
    assert_eq!(tree.uncommitted_count, 1);
    assert_eq!(tree.uncommitted, ["plugins/air/a.kt"]);
    assert_eq!(persisted.tree_error, None);
}

// A `git` that fails is the report's `treeError`. It never fails the iteration and never moves the verdict.
#[tokio::test]
async fn a_tree_failure_is_recorded_and_never_the_verdict() {
    let (fixture, _, _) = iteration_fixture("it-51").await;
    fixture.git().fail();
    let attempt = run_warm_iteration(&fixture, false).await;
    let report = report_of(&attempt);
    assert_eq!(report.tree, None);
    assert!(
        report
            .tree_error
            .as_deref()
            .is_some_and(|error| error.contains("not a git repository")),
        "{:?}",
        report.tree_error
    );
    assert_eq!(report.status, Status::Passed);
    assert_eq!(report.verdict_diagnostic, None);
}

// --- the run secrets ---------------------------------------------------------------------------------------------

const SECRET: &str = "central-login-export-0123456789abcdef";

/// One `--test-env AIR_LIVE_CENTRAL_LOGIN=@<file>`, read the way `run` reads it.
fn login_secret(directory: &Path) -> RunSecrets {
    let path = directory.join("login");
    std::fs::write(&path, format!("{SECRET}\n")).unwrap();
    let file = RunSecretFile {
        name: "AIR_LIVE_CENTRAL_LOGIN".to_owned(),
        source: SecretSource::File(path),
    };
    RunSecrets::read(&[file], &ScriptedStdin::default()).unwrap()
}

/// The guest line that writes the secret file, and the one that removes it, as positions in the channel's calls.
fn secret_write_and_removal(fixture: &Fixture) -> (usize, usize) {
    let directory = &fixture.settings.vm_run_secrets;
    let lines = fixture.channel().lines();
    let position = |fragment: &str| {
        lines
            .iter()
            .position(|line| line.contains(fragment))
            .unwrap_or_else(|| panic!("no {fragment:?} in {lines:#?}"))
    };
    let made = position(&format!(
        "umask 077 && /bin/mkdir -p \"$1\" && /bin/chmod 700 \"$1\" sh {directory}"
    ));
    let written = position(&format!("umask 077 && exec cat > \"$1\" sh {directory}/AIR_LIVE_CENTRAL_LOGIN"));
    let removed = position(&format!("/bin/rm -f -- {directory}/AIR_LIVE_CENTRAL_LOGIN"));
    assert!(made < written && written < removed, "{lines:#?}");
    // The removal outlives an interrupt: the service would signal a child it spawned after the first signal.
    assert!(fixture.channel().calls()[removed].options.survives_interrupt(), "{lines:#?}");
    (written, removed)
}

// The secret reaches the guest on stdin only, before the run, and is removed after it; no argv carries it.
#[tokio::test]
async fn a_run_secret_travels_on_stdin_and_is_removed_after_the_run() {
    let (fixture, _, _) = iteration_fixture("it-60").await;
    let directory = tempfile::tempdir().unwrap();
    let attempt = run_iteration_with(&fixture, &Ctx::background(), false, &login_secret(directory.path())).await;
    assert_eq!(report_of(&attempt).status, Status::Passed);
    assert!(fixture.daemon.saw_request("POST /run"));
    let (written, _) = secret_write_and_removal(&fixture);
    let calls = fixture.channel().calls();
    assert_eq!(calls[written].options.stdin.as_deref(), Some(format!("{SECRET}\n").as_bytes()));
    for call in &calls {
        assert!(!call.line().contains(SECRET), "{}", call.line());
    }
    let said = fixture.stdout.text() + &fixture.stderr.text();
    assert!(said.contains("run secrets: AIR_LIVE_CENTRAL_LOGIN"), "{said}");
    assert!(!said.contains(SECRET), "{said}");
    assert!(
        !said.contains(&directory.path().display().to_string()),
        "the phase line names no file: {said}"
    );
}

// A run that fails still removes the secret: here the stream ends before it names its iteration.
#[tokio::test]
async fn a_failed_run_still_removes_its_secret() {
    let (fixture, _, _) = iteration_fixture("it-61").await;
    fixture.script_run(Vec::new(), PASSING_XML);
    let directory = tempfile::tempdir().unwrap();
    let attempt = run_iteration_with(&fixture, &Ctx::background(), false, &login_secret(directory.path())).await;
    assert_eq!(error_of(&attempt).code, "daemon_run_failed");
    secret_write_and_removal(&fixture);
}

// An interrupt after the write still removes the secret, under a context the interrupt does not cancel.
#[tokio::test]
async fn an_interrupted_run_still_removes_its_secret() {
    let (fixture, _, _) = iteration_fixture("it-62").await;
    let token = CancellationToken::new();
    let cancel = token.clone();
    fixture.on(
        "sh",
        handler(move |argv, _| {
            if argv.iter().any(|word| word.contains("exec cat")) {
                cancel.cancel();
            }
            Ok(avl_host_testkit::said(""))
        }),
    );
    let directory = tempfile::tempdir().unwrap();
    let attempt = run_iteration_with(&fixture, &Ctx::new(token), false, &login_secret(directory.path())).await;
    assert_eq!(error_of(&attempt).code, "run_interrupted");
    secret_write_and_removal(&fixture);
}

// A secret that reaches a fetched artifact refuses the run, green or not, and the artifact is moved aside.
#[tokio::test]
async fn a_secret_in_the_report_refuses_the_run_and_withholds_the_report() {
    let (fixture, _, _) = iteration_fixture("it-63").await;
    let leaked: Vec<String> = passing_run_lines("it-63")
        .into_iter()
        .map(|line| line.replace("\"works\"", &format!("\"{SECRET}\"")))
        .collect();
    fixture.script_run(leaked, &PASSING_XML.replace("name=\"works\"", &format!("name=\"{SECRET}\"")));
    let directory = tempfile::tempdir().unwrap();
    let attempt = run_iteration_with(&fixture, &Ctx::background(), false, &login_secret(directory.path())).await;
    let error = error_of(&attempt);
    assert_eq!((error.code.as_ref(), error.exit), ("secret_in_artifact", Exit::FAILURE));
    assert!(attempt.report.is_none(), "a report that holds a secret is not answered");
    assert!(error.message.contains("AIR_LIVE_CENTRAL_LOGIN"), "{}", error.message);
    let rendered = format!("{} {:?}", error.message, error.details());
    assert!(!rendered.contains(SECRET), "{rendered}");
    let withheld = fixture.settings.worker_dir(&fixture.worker).join("withheld");
    let moved: Vec<_> = std::fs::read_dir(&withheld).unwrap().map(|entry| entry.unwrap().path()).collect();
    assert_eq!(moved.len(), 1, "{moved:?}");
    assert!(moved[0].to_string_lossy().ends_with("-it-63.json"), "{}", moved[0].display());
    secret_write_and_removal(&fixture);
}

// Without a secret nothing is written, removed or scanned: `shard` and `flake` pass none.
#[tokio::test]
async fn no_secret_touches_no_secret_directory() {
    let (fixture, _, _) = iteration_fixture("it-64").await;
    let attempt = run_warm_iteration(&fixture, false).await;
    report_of(&attempt);
    let directory = &fixture.settings.vm_run_secrets;
    assert_eq!(fixture.channel().calls_containing(directory), Vec::<String>::new());
}
