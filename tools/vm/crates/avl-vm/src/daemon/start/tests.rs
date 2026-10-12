use std::sync::Arc;
use std::sync::atomic::{AtomicUsize, Ordering};

use avl_base::phase::Timeline;
use avl_host_sys::{Captured, Signal};
use avl_host_testkit::agent::{agent_reply, argv_value, run_state};
use avl_host_testkit::{answer_exit, answer_text, handler, refusal};
use avl_wire::stage::decode_launch_prep;
use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;
use crate::daemon::fixture::Fixture;

/// The remount script *executions* the scripted guest saw: writing and chmodding the script name it too, and they
/// are not remounts.
fn remounts(fixture: &Fixture) -> usize {
    fixture.channel().calls_containing("/bin/sh /vm/data/state/remount-shares.sh").len()
}

// The remount is skipped only when the previous launch digest and the previous mount digest both match; a missing
// record, like either mismatch, remounts - nothing can prove a mount is current, only a record of having made it
// can.
#[tokio::test]
async fn remount_is_skipped_only_when_both_digests_match() {
    let fixture = Fixture::tart_macos().await;
    let prep = fixture.prepared().await;
    let recorded = |launch: &str, mount: &str| {
        let mut state = fixture.daemon.host_state("run-1", launch);
        state.last_mount_digest = mount.to_owned();
        state
    };
    let cases = [
        ("both digests match", Some(recorded(&prep.launch_digest, &prep.mount_digest)), 0),
        ("the launch digest moved", Some(recorded("elsewhere", &prep.mount_digest)), 1),
        ("the mount digest moved", Some(recorded(&prep.launch_digest, "elsewhere")), 1),
        ("no prior state", None, 1),
    ];
    let ctx = Ctx::background();
    for (name, previous, expected) in cases {
        let before = remounts(&fixture);
        fixture
            .host
            .refresh_shares_if_host_bytes_moved(&ctx, &fixture.worker, previous.as_ref(), &prep)
            .await
            .unwrap();
        assert_eq!(remounts(&fixture) - before, expected, "{name}");
    }
}

/// A fixture scripted to the point where a start reaches its boot poll.
async fn start_ready() -> (Fixture, PreparedBuild) {
    start_ready_with(&[]).await
}

/// [`start_ready`] under more environment variables.
async fn start_ready_with(extra: &[(&str, &str)]) -> (Fixture, PreparedBuild) {
    ready(Fixture::with_environment(extra).await).await
}

/// Scripts `fixture` to the point where a start reaches its boot poll.
async fn ready(fixture: Fixture) -> (Fixture, PreparedBuild) {
    let prep = fixture.prepared().await;
    fixture.install_happy_guest(&prep);
    fixture.daemon.script().launch_digest = prep.launch_digest.clone();
    (fixture, prep)
}

/// Puts a fixture that [`ready`] scripted back to that state for the next case of its test: the calls are forgotten,
/// the worker has no host records, and the guest answers what a start that reaches its boot poll needs again.
fn ready_again(fixture: &Fixture, prep: &PreparedBuild) {
    fixture.forget_calls();
    fixture.forget_worker_records();
    fixture.install_happy_guest(prep);
    fixture.daemon.script().launch_digest = prep.launch_digest.clone();
}

/// Every supervisor cancel the first worker's guest was sent.
fn cancels(fixture: &Fixture) -> Vec<String> {
    fixture.channel().calls_containing("vm-guest-agent cancel")
}

/// The refusal's details, which every refusal of a start past its supervisor start carries.
fn details_of(failure: &Refusal) -> Value {
    failure.details().expect("the refusal carries details")
}

async fn start(fixture: &Fixture, prep: &PreparedBuild) -> Result<HostState, Refusal> {
    fixture.host.start_daemon(&Ctx::background(), &fixture.worker, prep).await
}

// The boot poll's three failures stay three: a supervisor that finished, a state file that is not JSON, and JSON that
// is not an object each name themselves without spending the boot budget.
#[tokio::test]
async fn boot_poll_separates_its_three_failures() {
    // The daemon exited during boot.
    let (fixture, prep) = start_ready().await;
    fixture.on(
        "status",
        handler(|argv, _| {
            let mut state = run_state(argv_value(argv, "--run"), "finished");
            state["outcome"] = json!("failed");
            state["exitCode"] = json!(7);
            state["nativeExitCode"] = json!(7);
            state["signal"] = json!(null);
            Ok(agent_reply("status", &state))
        }),
    );
    // No record names the run, so `daemon log` could not read it later: the refusal carries the end of its log.
    fixture.on(
        "log",
        handler(|argv, _| {
            Ok(agent_reply(
                "log",
                &json!({"runId": argv_value(argv, "--run"), "logPath": "/vm/log",
                    "content": "boom\n", "truncated": false}),
            ))
        }),
    );
    let failure = refusal(start(&fixture, &prep).await);
    assert_eq!((failure.code.as_ref(), failure.exit), ("daemon_start_failed", Exit::SOFTWARE));
    assert!(
        failure.message.contains("exited during boot (failed, exit 7)") && failure.message.contains("logTail"),
        "the refusal names the outcome and where its log is, said {:?}",
        failure.message
    );
    let details = details_of(&failure);
    assert!(
        details["runId"].as_str().is_some_and(|run| run.starts_with("run-ui-daemon-")),
        "{details}"
    );
    assert_eq!(details["logTail"], json!("boom\n"));

    // A state file that is not JSON. The log is unrouted again, as in a fresh fixture.
    ready_again(&fixture, &prep);
    fixture.on("log", answer_exit(0));
    fixture.on("cat", answer_text("not json{"));
    let failure = refusal(start(&fixture, &prep).await);
    assert_eq!(failure.code, "daemon_state_file_unreadable");
    assert!(failure.message.contains("is not JSON: not json{"), "{}", failure.message);
    assert_retired_without_a_record(&fixture, &failure);

    // JSON that is not an object.
    ready_again(&fixture, &prep);
    fixture.on("cat", answer_text("42"));
    let failure = refusal(start(&fixture, &prep).await);
    assert_eq!(failure.code, "daemon_state_file_unreadable");
    assert!(failure.message.contains("is 42 rather than an object"), "{}", failure.message);
    assert_retired_without_a_record(&fixture, &failure);
}

/// A start that failed before it could write a record cancelled the run it launched, once, and recorded nothing.
fn assert_retired_without_a_record(fixture: &Fixture, failure: &Refusal) {
    let details = details_of(failure);
    let run = details["runId"].as_str().expect("the refusal names its run");
    let sent = cancels(fixture);
    assert!(
        sent.len() == 1 && sent[0].contains(&format!("--run {run}")),
        "one cancel of {run}: {sent:?}"
    );
    assert_eq!(details["retired"], json!(true), "{details}");
    assert!(failure.message.contains("was retired"), "{}", failure.message);
    assert_eq!(HostState::read(&fixture.settings, &fixture.worker), None);
}

// A state file that is not written yet keeps the loop going, and the protocol gate runs immediately after the boot
// poll - before this controller has made a single HTTP request.
#[tokio::test]
async fn an_unwritten_state_file_loops_and_the_protocol_gate_runs_before_any_http() {
    let (fixture, prep) = start_ready().await;
    // The version this controller speaks, spelled from the constant: a bump would otherwise make the replacement a
    // no-op, and the test would pass a state file it never made stale.
    let stale = fixture.daemon.state_file_json().replacen(
        &format!(r#""protocolVersion":{}"#, wire::PROTOCOL_VERSION),
        r#""protocolVersion":99"#,
        1,
    );
    assert!(stale.contains(r#""protocolVersion":99"#));
    let reads = Arc::new(AtomicUsize::new(0));
    let counted = Arc::clone(&reads);
    fixture.on(
        "cat",
        handler(move |_, _| {
            if counted.fetch_add(1, Ordering::SeqCst) == 0 {
                // not written yet
                return Ok(Captured {
                    exit_code: 1,
                    ..Captured::default()
                });
            }
            Ok(Captured {
                stdout: stale.clone(),
                ..Captured::default()
            })
        }),
    );
    let requests_before = fixture.daemon.request_count();
    let failure = refusal(start(&fixture, &prep).await);
    assert_eq!(
        (failure.code.as_ref(), failure.exit),
        ("daemon_protocol_unsupported", Exit::SOFTWARE)
    );
    assert!(
        failure.message.contains("speaks run protocol 99") && failure.message.contains("daemon stop"),
        "the refusal names both versions and the retirement, said {:?}",
        failure.message
    );
    assert_eq!(reads.load(Ordering::SeqCst), 2, "the unwritten file keeps the loop going once");
    assert_eq!(
        fixture.daemon.request_count(),
        requests_before,
        "the protocol gate fires before the first HTTP request, and the retire sends no /shutdown"
    );
    assert_retired_without_a_record(&fixture, &failure);
}

// A boot that never publishes a state file times out with its own code once the deadline passes.
#[tokio::test]
async fn a_boot_that_never_publishes_times_out() {
    let (fixture, prep) = start_ready().await;
    fixture.on("cat", answer_exit(1));
    let failure = refusal(start(&fixture, &prep).await);
    assert_eq!(
        (failure.code.as_ref(), failure.exit),
        ("daemon_boot_timeout", Exit::SOFTWARE),
        "{}",
        failure.message
    );
    // 1200 s of budget at 3 s a turn, and one probe more at the end of the budget: the fake clock paid it without the
    // suite waiting.
    assert_eq!(fixture.channel().calls_containing("/bin/cat").len(), 401);
    assert_retired_without_a_record(&fixture, &failure);
}

/// Asserts a failed health poll's refusal: `daemon_unreachable` at 69, its reason, the run it retired, and the record
/// it kept so `daemon log` can read the boot.
fn assert_unreachable_and_recorded(fixture: &Fixture, failure: &Refusal, reason: &str) {
    assert_eq!(
        (failure.code.as_ref(), failure.exit),
        ("daemon_unreachable", Exit::UNAVAILABLE),
        "{}",
        failure.message
    );
    let details = details_of(failure);
    assert_eq!(details["reason"], json!(reason), "{details}");
    let run = details["runId"].as_str().expect("the refusal names its run");
    let recorded = HostState::read(&fixture.settings, &fixture.worker).expect("a start that read the state file keeps its record");
    assert_eq!(recorded.run_id, run);
    assert_eq!(details["retired"], json!(true), "{details}");
    assert!(failure.message.contains("was retired"), "{}", failure.message);
    assert!(
        details.get("logTail").is_none(),
        "a record names the run, so `daemon log` reads it: {details}"
    );
}

// A daemon that publishes its state file and answers its status endpoint with a 500 is `daemon_unreachable` at 69,
// and the refusal says what it answered rather than blaming the relay. The start cancels the run it launched and
// keeps the record, so `daemon log` still reads the boot.
#[tokio::test]
async fn a_daemon_that_answers_500_is_retired_and_its_record_kept() {
    let (fixture, prep) = start_ready().await;
    let slot = fixture.install_slot_double(None);
    fixture.daemon.script().status_code = Some(500);
    let failure = refusal(start(&fixture, &prep).await);
    assert_unreachable_and_recorded(&fixture, &failure, "not_a_status");
    assert!(
        failure.message.contains("HTTP 500") && !failure.message.contains("no relay"),
        "{}",
        failure.message
    );
    // The default budget, 180 s at 2 s a probe and one probe more at its end, paid by the fake clock.
    assert_eq!(details_of(&failure)["probes"], json!(91));
    let run = details_of(&failure)["runId"].as_str().unwrap().to_owned();
    let sent = cancels(&fixture);
    assert!(sent.len() == 1 && sent[0].contains(&format!("--run {run}")), "{sent:?}");
    assert_eq!(*slot.lock().unwrap(), None, "the retired run left the slot");
}

// Only a relay that never opened blames the path to the daemon, and the poll spends its whole budget first.
#[tokio::test]
async fn a_health_poll_whose_relay_never_opens_names_the_relay() {
    let (fixture, prep) = start_ready_with(&[("AIR_VM_DAEMON_HEALTH_TIMEOUT", "10")]).await;
    publish_on_port_one(&fixture);
    let failure = refusal(start(&fixture, &prep).await);
    assert_unreachable_and_recorded(&fixture, &failure, "no_connection");
    // 10 s at 2 s a probe, and one probe more at the end of the budget.
    assert_eq!(details_of(&failure)["probes"], json!(6));
    assert!(
        details_of(&failure)["lastProbe"]
            .as_str()
            .is_some_and(|probe| probe.contains("could not connect") && probe.contains("nothing listens on 127.0.0.1:1")),
        "{:?}",
        failure.details()
    );
    assert!(
        failure
            .message
            .contains(&format!("no relay to 127.0.0.1:1 inside {} opened in 10 s", fixture.worker))
            && failure.message.contains("read `daemon log`"),
        "{}",
        failure.message
    );
}

/// The daemon address the state file names, moved to port 1, where nothing listens: every `/status` probe is
/// `NoConnection`.
fn publish_on_port_one(fixture: &Fixture) {
    let published = fixture
        .daemon
        .state_file_json()
        .replacen(&format!(r#""port":{}"#, fixture.daemon.port()), r#""port":1"#, 1);
    assert!(published.contains(r#""port":1,"#) || published.contains(r#""port":1}"#));
    fixture.on("cat", answer_text(published));
}

// A status from another boot or another launch names both values, and is not blamed on the relay.
#[tokio::test]
async fn a_status_from_another_daemon_is_named_not_blamed_on_the_relay() {
    let (fixture, prep) = start_ready().await;
    let expected = fixture.daemon.script().boot_stamp.clone();
    fixture.daemon.script().boot_stamp = "elsewhere".to_owned();
    let failure = refusal(start(&fixture, &prep).await);
    assert_unreachable_and_recorded(&fixture, &failure, "other_daemon");
    assert!(
        failure.message.contains("boot stamp elsewhere") && failure.message.contains(&expected) && !failure.message.contains("no relay"),
        "{}",
        failure.message
    );

    // The double answers its own boot stamp again, and the guest's state file names it.
    fixture.daemon.script().boot_stamp = expected;
    ready_again(&fixture, &prep);
    fixture.daemon.script().launch_digest = "elsewhere".to_owned();
    let failure = refusal(start(&fixture, &prep).await);
    assert_unreachable_and_recorded(&fixture, &failure, "other_daemon");
    assert!(
        failure.message.contains("launch digest elsewhere")
            && failure.message.contains(&prep.launch_digest)
            && !failure.message.contains("no relay"),
        "{}",
        failure.message
    );
}

// A cancel that fails keeps the record, and the refusal names the verb that finishes the retirement.
#[tokio::test]
async fn a_retire_that_fails_keeps_the_record_and_names_daemon_stop() {
    let (fixture, prep) = start_ready().await;
    fixture.daemon.script().status_code = Some(500);
    fixture.on("cancel", answer_exit(1));
    let failure = refusal(start(&fixture, &prep).await);
    assert_eq!(failure.code, "daemon_unreachable");
    assert_eq!(details_of(&failure)["retired"], json!(false));
    assert!(failure.message.contains("`daemon stop` retires it"), "{}", failure.message);
    assert!(HostState::read(&fixture.settings, &fixture.worker).is_some());
}

// The health budget is `AIR_VM_DAEMON_HEALTH_TIMEOUT`. A malformed one refuses at `Config::load`, before any command.
#[tokio::test]
async fn the_health_budget_comes_from_the_environment() {
    let (fixture, prep) = start_ready_with(&[("AIR_VM_DAEMON_HEALTH_TIMEOUT", "10")]).await;
    fixture.daemon.script().status_code = Some(500);
    let failure = refusal(start(&fixture, &prep).await);
    let details = details_of(&failure);
    assert_eq!(
        (&details["probes"], &details["budgetSeconds"]),
        (&json!(6), &json!(10)),
        "{details}"
    );
    let probes = fixture
        .daemon
        .script()
        .requests
        .iter()
        .filter(|request| request.as_str() == "GET /status")
        .count();
    assert_eq!(probes, 6);
}

// A start finds a daemon run no record names, retires it, and takes the slot; any other holder still refuses.
#[tokio::test]
async fn a_start_retires_a_daemon_run_no_record_names() {
    let (fixture, prep) = start_ready().await;
    fixture.install_slot_double(Some("run-ui-daemon-orphan"));
    let timeline = Timeline::collecting();
    let ctx = Ctx::background().with_timeline(timeline.clone());
    let state = fixture.host.start_daemon(&ctx, &fixture.worker, &prep).await.unwrap();
    let calls: Vec<String> = fixture.channel().calls().into_iter().map(|call| call.argv.join(" ")).collect();
    let cancel = calls
        .iter()
        .position(|call| call.contains("vm-guest-agent cancel") && call.contains("--run run-ui-daemon-orphan"))
        .expect("the orphan is cancelled");
    let launch = calls
        .iter()
        .position(|call| call.contains("vm-guest-agent start"))
        .expect("the daemon is launched");
    assert!(cancel < launch, "the orphan is retired before the launch");
    assert_eq!(cancels(&fixture).len(), 1);
    assert_ne!(state.run_id, "run-ui-daemon-orphan");
    assert_eq!(
        HostState::read(&fixture.settings, &fixture.worker).map(|recorded| recorded.run_id),
        Some(state.run_id)
    );
    let names: Vec<String> = timeline.take().into_iter().map(|timing| timing.name).collect();
    assert_eq!(names, PHASES);

    ready_again(&fixture, &prep);
    fixture.install_slot_double(Some("run-flow-1"));
    let failure = refusal(start(&fixture, &prep).await);
    assert_eq!(failure.code, "run_active");
    assert!(failure.message.contains("cannot start the daemon"), "{}", failure.message);
    assert!(cancels(&fixture).is_empty(), "another run is never cancelled");
}

// An interrupt after the supervisor start is `daemon_start_interrupted`, whichever poll it landed in, and skips the
// cancel the interrupt would fail too.
#[tokio::test]
async fn an_interrupted_start_is_daemon_start_interrupted() {
    // In the boot poll: no state file yet, so no record either.
    let (fixture, prep) = start_ready().await;
    let interrupts = fixture.interrupts.clone();
    fixture.on(
        "cat",
        handler(move |_, _| {
            interrupts.deliver(Signal::Interrupt);
            Ok(Captured {
                exit_code: 1,
                ..Captured::default()
            })
        }),
    );
    let failure = refusal(
        fixture
            .host
            .start_daemon(&fixture.interrupts.context(), &fixture.worker, &prep)
            .await,
    );
    assert_eq!((failure.code.as_ref(), failure.exit), ("daemon_start_interrupted", Exit::SOFTWARE));
    assert!(
        failure.message.contains("SIGINT") && failure.message.contains("daemon stop"),
        "{}",
        failure.message
    );
    assert!(
        details_of(&failure)["runId"]
            .as_str()
            .is_some_and(|run| run.starts_with("run-ui-daemon-"))
    );
    assert!(cancels(&fixture).is_empty());
    assert!(fixture.channel().calls_containing("vm-guest-agent log").is_empty());
    assert_eq!(HostState::read(&fixture.settings, &fixture.worker), None);

    // In the health poll: the record is written, and it stays for `daemon stop`.
    let (fixture, prep) = start_ready().await;
    fixture.daemon.script().status_code = Some(500);
    let interrupter = {
        let daemon = Arc::clone(&fixture.daemon);
        let interrupts = fixture.interrupts.clone();
        tokio::spawn(async move {
            while !daemon.saw_request("GET /status") {
                tokio::task::yield_now().await;
            }
            interrupts.deliver(Signal::Interrupt);
        })
    };
    let failure = refusal(
        fixture
            .host
            .start_daemon(&fixture.interrupts.context(), &fixture.worker, &prep)
            .await,
    );
    interrupter.await.unwrap();
    assert_eq!(failure.code, "daemon_start_interrupted", "{}", failure.message);
    assert!(HostState::read(&fixture.settings, &fixture.worker).is_some());
    assert!(cancels(&fixture).is_empty());
}

/// The launch-prep document the start sent, decoded by the guest's own decoder.
fn launch_prep_request(fixture: &Fixture) -> LaunchPrep {
    let options = fixture
        .channel()
        .options_for_call_containing("vm-guest-agent launch-prep")
        .expect("a launch-prep call was made");
    decode_launch_prep(options.stdin.as_deref().unwrap_or_default()).expect("the controller sent a request its own guest accepts")
}

// The full start: agent installed, runtime staged, the launch prepared, supervisor started, state file read, health
// proven, state recorded, old generations collected - with the phase table naming each step.
#[tokio::test]
async fn a_full_start_boots_polls_and_records_its_state() {
    let (fixture, prep) = start_ready().await;
    let timeline = Timeline::collecting();
    let ctx = Ctx::background().with_timeline(timeline.clone());
    let state = fixture.host.start_daemon(&ctx, &fixture.worker, &prep).await.unwrap();
    assert_eq!(
        (state.worker.as_str(), state.port, state.token.as_str()),
        (fixture.worker.as_str(), fixture.daemon.port(), fixture.daemon.token.as_str()),
        "the state aims at the daemon the guest named"
    );
    assert_eq!(
        (
            &state.launch_digest,
            &state.runtime_digest,
            &state.last_product_digest,
            &state.last_mount_digest
        ),
        (&prep.launch_digest, &prep.runtime_digest, &prep.product_digest, &prep.mount_digest)
    );
    assert_eq!(
        HostState::read(&fixture.settings, &fixture.worker).as_ref(),
        Some(&state),
        "the state is recorded for the next iteration"
    );

    let channel = fixture.channel();
    // One guest call prepares the launch, and the round-trips it replaced are gone rather than moved.
    assert_eq!(channel.calls_containing("vm-guest-agent launch-prep").len(), 1);
    for retired in ["/usr/bin/tee /vm/data/daemon/daemon-jvm.args", "/bin/rm -f"] {
        assert!(channel.calls_containing(retired).is_empty(), "{retired}");
    }

    // The launch identity is the controller's, whichever half writes the file: every `-D` property is in the prefix
    // it sent, and the main class is named outright.
    let request = launch_prep_request(&fixture);
    let prefix = request.arg_file.prefix.join("\n");
    for fragment in [
        format!("-Dair.ui.daemon.controller.launch.digest={}", prep.launch_digest),
        "-Dair.ui.daemon.port=27100".to_owned(),
        format!("-Didea.home.path={}", fixture.root().display()),
    ] {
        assert!(prefix.contains(&fragment), "{fragment} missing from\n{prefix}");
    }
    assert_eq!(request.arg_file.main_class, "com.example.Main");
    assert_eq!(request.arg_file.destination, "/vm/data/daemon/daemon-jvm.args");
    assert_eq!(request.remove_files, ["/vm/data/daemon/daemon.json"]);
    assert_eq!(request.directories.len(), 2);

    // The supervisor start runs the staged JVM under /usr/bin/env with the run environment.
    let starts = channel.calls_containing("vm-guest-agent start");
    assert_eq!(starts.len(), 1, "{starts:?}");
    assert!(
        starts[0].contains("@/vm/data/daemon/daemon-jvm.args") && starts[0].contains("SELF_LOCATION="),
        "{}",
        starts[0]
    );

    // Old generations are collected keeping the current digest; the previous state had none.
    let collections = channel.calls_containing("vm-guest-agent gc");
    assert_eq!(collections.len(), 1);
    assert!(
        collections[0].ends_with(&format!("gc /vm/data/daemon-runtime {}", prep.runtime_digest)),
        "{}",
        collections[0]
    );

    // A green start cancels nothing.
    assert!(cancels(&fixture).is_empty());

    // The phase table names the fixed sequence, in order.
    let names: Vec<String> = timeline.take().into_iter().map(|timing| timing.name).collect();
    assert_eq!(names, PHASES);
}

/// The phase table of a green start, in order.
const PHASES: [&str; 11] = [
    "install-agent",
    "stop-daemon",
    "reject-active-run",
    "remount",
    "parity-probe",
    "stage-runtime",
    "prepare-launch",
    "supervisor-start",
    "boot-poll",
    "health-poll",
    "gc-runtimes",
];

// The regression tripwire. What the collapse bought is that the ~1000 staged classpath paths stop crossing the exec
// channel, and nothing about the request's shape would fail if a later change put them back - the start would
// simply be 10.6 s slower again. So the bytes are searched for one.
#[tokio::test]
async fn the_launch_prep_request_carries_no_classpath() {
    let (fixture, prep) = start_ready().await;
    start(&fixture, &prep).await.unwrap();
    let options = fixture
        .channel()
        .options_for_call_containing("vm-guest-agent launch-prep")
        .expect("a launch-prep call was made");
    let sent = String::from_utf8(options.stdin.unwrap()).unwrap();
    let generation = format!("/vm/data/daemon-runtime/generations/{}", prep.runtime_digest);
    for staged in [format!("{generation}/classpath/000.jar"), "-cp".to_owned()] {
        assert!(
            !sent.contains(&staged),
            "the request names {staged:?}; the guest joins its own classpath:\n{sent}"
        );
    }
    // The count does cross, and it is what the guest holds its own list against.
    let request = launch_prep_request(&fixture);
    assert_eq!(request.stable_count, 2);
    assert_eq!(request.runtime_digest, prep.runtime_digest);
}

// The guest composes the @-file; it does not decide it. A reply whose digest is not the one the controller computed
// from the staged classpath is a refused start.
#[tokio::test]
async fn a_guest_answer_whose_argfile_hash_differs_is_refused() {
    let answer = |arg_file: &str, entries: u32| LaunchPrepResult {
        arg_file: arg_file.to_owned(),
        sha256: "e".repeat(64),
        bytes: 4096,
        entries,
    };
    let (fixture, prep) = start_ready().await;
    for (name, reply) in [
        ("another digest", answer("/vm/data/daemon/daemon-jvm.args", 2)),
        ("another destination", answer("/vm/tmp/somewhere-else.args", 2)),
        ("another entry count", answer("/vm/data/daemon/daemon-jvm.args", 1)),
        (
            "no answer at all",
            LaunchPrepResult {
                arg_file: String::new(),
                sha256: String::new(),
                bytes: 0,
                entries: 0,
            },
        ),
    ] {
        ready_again(&fixture, &prep);
        fixture.on("launch-prep", answer_text(serde_json::to_string(&reply).unwrap()));
        let failure = refusal(start(&fixture, &prep).await);
        assert_eq!(
            (failure.code.as_ref(), failure.exit),
            ("daemon_launch_prep_failed", Exit::SOFTWARE),
            "{name}"
        );
        assert!(failure.message.contains("this controller expected"), "{name}: {}", failure.message);
        assert!(
            fixture.channel().calls_containing("vm-guest-agent start").is_empty(),
            "{name}: no daemon may be started from an @-file this controller did not verify"
        );
    }
    // Output that is not a reply at all names itself rather than being read as a mismatch.
    ready_again(&fixture, &prep);
    fixture.on("launch-prep", answer_text("Killed: 9"));
    let failure = refusal(start(&fixture, &prep).await);
    assert_eq!(failure.code, "daemon_launch_prep_failed");
    assert!(failure.message.contains("invalid JSON: Killed: 9"), "{}", failure.message);
}

// A parity layout that does not show the descriptor refuses before anything is staged, at 65.
#[tokio::test]
async fn a_stale_parity_layout_refuses_before_staging() {
    let (fixture, prep) = start_ready().await;
    fixture.on("test", answer_exit(1));
    let failure = refusal(start(&fixture, &prep).await);
    assert_eq!((failure.code.as_ref(), failure.exit), ("guest_runtime_unreachable", Exit::DATA_ERR));
    assert!(
        fixture.channel().calls_containing("vm-guest-agent stage").is_empty(),
        "nothing may be staged over a stale layout"
    );
}

// With no state a stop is a no-op: no HTTP and no guest command.
#[tokio::test]
async fn stop_daemon_without_a_record_touches_nothing() {
    let fixture = Fixture::new().await;
    fixture.host.stop_daemon(&Ctx::background(), &fixture.worker, None).await.unwrap();
    assert_eq!(fixture.daemon.request_count(), 0);
    assert!(fixture.channel().calls().is_empty());
}

// With a record a stop shuts down over HTTP, cancels through the supervisor, and removes the record once the
// supervisor answered the run finished. The shutdown's failure is swallowed: an unreachable daemon is cancelled.
#[tokio::test]
async fn stop_daemon_removes_the_record_once_the_supervisor_finished_the_run() {
    let fixture = Fixture::new().await;
    let ctx = Ctx::background();
    let prep = fixture.prepared().await;
    fixture.install_slot_double(Some("run-ui-daemon-seeded"));
    let state = fixture.seed_healthy_daemon(&prep);
    fixture.daemon.script().status_code = Some(500);
    fixture.host.stop_daemon(&ctx, &fixture.worker, Some(&state)).await.unwrap();
    assert_eq!(HostState::read(&fixture.settings, &fixture.worker), None);
    assert!(fixture.daemon.saw_request("POST /shutdown"), "the polite shutdown comes first");
    assert_eq!(
        fixture.channel().calls_containing("vm-guest-agent cancel").len(),
        1,
        "the supervisor cancel is attempted"
    );

    // A daemon that does not answer at all is the same.
    let mut unreachable = fixture.seed_healthy_daemon(&prep);
    unreachable.port = 1;
    fixture.host.stop_daemon(&ctx, &fixture.worker, Some(&unreachable)).await.unwrap();
    assert_eq!(HostState::read(&fixture.settings, &fixture.worker), None);
}

// A cancel that fails while the recorded run still holds the slot refuses and keeps the record: removing it would
// leave a live daemon run no record names.
#[tokio::test]
async fn a_stop_whose_cancel_fails_keeps_the_record_while_the_run_holds_the_slot() {
    let fixture = Fixture::new().await;
    let prep = fixture.prepared().await;
    fixture.install_slot_double(Some("run-ui-daemon-seeded"));
    fixture.on("cancel", answer_exit(1));
    let state = fixture.seed_healthy_daemon(&prep);
    let failure = refusal(fixture.host.stop_daemon(&Ctx::background(), &fixture.worker, Some(&state)).await);
    assert_eq!((failure.code.as_ref(), failure.exit), ("daemon_retire_failed", Exit::SOFTWARE));
    assert_eq!(failure.details(), Some(json!({ "runId": state.run_id })));
    assert_eq!(HostState::read(&fixture.settings, &fixture.worker), Some(state));
}

// A cancel that fails for a run that no longer holds the slot forgets the record: a recycled guest answers
// `invalid_state` to a cancel for a run it never had, and that record would otherwise block `daemon stop` forever.
#[tokio::test]
async fn a_stop_whose_cancel_fails_forgets_a_run_that_no_longer_holds_the_slot() {
    let fixture = Fixture::new().await;
    let prep = fixture.prepared().await;
    for holder in [None, Some("run-flow-1")] {
        fixture.stderr.clear();
        fixture.install_slot_double(holder);
        fixture.on(
            "cancel",
            handler(|_, _| {
                Ok(Captured {
                    exit_code: 1,
                    stdout: json!({"schemaVersion": 1, "ok": false, "command": "cancel",
                        "error": {"code": "invalid_state", "message": "no state for the run"}})
                    .to_string(),
                    ..Captured::default()
                })
            }),
        );
        let state = fixture.seed_healthy_daemon(&prep);
        fixture
            .host
            .stop_daemon(&Ctx::background(), &fixture.worker, Some(&state))
            .await
            .unwrap_or_else(|refusal| panic!("{holder:?}: {refusal:?}"));
        assert_eq!(HostState::read(&fixture.settings, &fixture.worker), None, "{holder:?}");
        assert!(
            fixture.stderr.text().contains("daemon run run-ui-daemon-seeded no longer holds"),
            "{holder:?}: {}",
            fixture.stderr.text()
        );
    }
}

// A cancel that fails while the slot cannot be read keeps the record: an unreadable slot may still hold the run.
#[tokio::test]
async fn a_stop_that_cannot_read_the_slot_keeps_the_record() {
    let fixture = Fixture::new().await;
    let prep = fixture.prepared().await;
    // `active` answers exit 0 with no output, which the supervisor decoder refuses.
    fixture.on("active", answer_exit(0));
    fixture.on("cancel", answer_exit(1));
    let state = fixture.seed_healthy_daemon(&prep);
    let failure = refusal(fixture.host.stop_daemon(&Ctx::background(), &fixture.worker, Some(&state)).await);
    assert_eq!(failure.code, "daemon_retire_failed");
    assert_eq!(
        fixture.channel().calls_containing("vm-guest-agent active").len(),
        1,
        "the slot was asked"
    );
    assert_eq!(HostState::read(&fixture.settings, &fixture.worker), Some(state));
}

// A start over a record whose run will not finish refuses from its stop-daemon phase, keeps the record, and
// launches nothing: before, the record was dropped and the start failed later with the run orphaned.
#[tokio::test]
async fn a_start_over_a_record_whose_run_will_not_finish_refuses_and_launches_nothing() {
    let (fixture, prep) = start_ready().await;
    fixture.install_slot_double(Some("run-ui-daemon-seeded"));
    fixture.on(
        "cancel",
        handler(|argv, _| Ok(agent_reply("cancel", &run_state(argv_value(argv, "--run"), "running")))),
    );
    let state = fixture.seed_healthy_daemon(&prep);
    let failure = refusal(start(&fixture, &prep).await);
    assert_eq!((failure.code.as_ref(), failure.exit), ("daemon_retire_failed", Exit::SOFTWARE));
    assert_eq!(HostState::read(&fixture.settings, &fixture.worker), Some(state));
    assert!(
        fixture.channel().calls_containing("vm-guest-agent start").is_empty(),
        "nothing may be launched over a live recorded run"
    );
}

/// A host that keeps no tree has the guest build it: inside the parity probe phase, before the probe and before the
/// stage, with the MANIFEST by its guest path, and at the root the launch already names.
#[tokio::test]
async fn a_host_without_a_tree_has_the_guest_build_it_before_the_probe() {
    let fixture = Fixture::new().await;
    crate::daemon::testing::replace_tree_with_manifest(&fixture.bazel.descriptor_path);
    let (fixture, prep) = ready(fixture).await;
    let root = prep.guest_runfiles_root.clone();
    fixture.on(
        "runfiles-tree",
        handler(move |_, _| {
            Ok(agent_reply(
                "runfiles-tree",
                &json!({"root": root, "digest": "d", "entries": 9, "reused": false}),
            ))
        }),
    );
    start(&fixture, &prep).await.unwrap();

    let calls = fixture.channel().calls();
    let at = |fragment: &str| {
        calls
            .iter()
            .position(|call| call.line().contains(fragment))
            .unwrap_or_else(|| panic!("no call contains {fragment:?}"))
    };
    let built = at(" runfiles-tree");
    assert!(built < at(&format!("/bin/test -f {}", self_location(&prep))));
    assert!(built < at(" stage "));
    let request: avl_wire::runfiles::RunfilesTreeRequest = serde_json::from_slice(calls[built].options.stdin.as_deref().unwrap()).unwrap();
    let paths = GuestPaths::of(&fixture.settings).unwrap();
    assert_eq!(
        request.manifest,
        paths
            .to_guest(&avl_host_sys::runfiles::manifest_paths(&fixture.bazel.descriptor_path)[0])
            .unwrap()
    );
    assert_eq!(request.path_map, *paths.map());
    // What the guest runs from is the built tree.
    assert!(
        self_location(&prep).starts_with(&format!("{}/", prep.guest_runfiles_root)),
        "{}",
        self_location(&prep)
    );
}

// The label-derived guest probe path replaces `//` and the first `:` once, and the pinned path is what the parity
// probe actually asks for.
#[tokio::test]
async fn the_parity_probe_asks_for_the_label_derived_descriptor() {
    let (fixture, prep) = start_ready().await;
    start(&fixture, &prep).await.unwrap();
    let expected = format!(
        "{}/_main/plugins/air/tests/integration/ui/ui_daemon.runtime.json",
        prep.guest_runfiles_root
    );
    assert_eq!(self_location(&prep), expected);
    let probes = fixture.channel().calls_containing(&format!("/bin/test -f {expected}"));
    assert_eq!(probes.len(), 1, "{:?}", fixture.channel().calls_containing("/bin/test -f"));
}
