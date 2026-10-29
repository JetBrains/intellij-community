use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};

use avl_base::phase::Timeline;
use avl_base::sync::lock;
use avl_host_sys::{Captured, Signal};
use avl_host_testkit::agent::{agent_reply, argv_value, run_state};
use avl_host_testkit::{Call, answer_exit, answer_text, failed, handler, has, refusal, said};
use pretty_assertions::assert_eq;
use serde_json::json;

use super::*;
use crate::daemon::fixture::Fixture;
use crate::daemon::testing::{DAEMON_PID, daemon_run_state, df_with_free_kib};

/// The index of the first of `calls` whose line contains `fragment`.
fn position_of(calls: &[Call], fragment: &str) -> usize {
    calls
        .iter()
        .position(|call| call.line().contains(fragment))
        .unwrap_or_else(|| panic!("no call contains {fragment:?}"))
}

/// Checks that a start kept the lane IDE of the product of `prep` and refreshed no share.
fn assert_kept_the_ide_of_the_product(fixture: &Fixture, prep: &PreparedBuild, ides: &IdeGcResult) {
    fixture.assert_one_ide_gc(&format!("--keep-product {}", prep.product_digest));
    assert_eq!((ides.kept.len(), ides.stopped.len()), (1, 0));
    assert_eq!(remounts(fixture), 0);
}

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
    fixture.install_happy_guest();
    fixture.daemon.script().launch_digest = prep.launch_digest.clone();
    (fixture, prep)
}

/// Puts a fixture that [`ready`] scripted back to that state for the next case of its test: the calls are forgotten,
/// the worker has no host records, and the guest answers what a start that reaches its boot poll needs again.
fn ready_again(fixture: &Fixture, prep: &PreparedBuild) {
    fixture.forget_calls();
    fixture.forget_worker_records();
    fixture.install_happy_guest();
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
    assert_eq!(cancels(&fixture), Vec::<String>::new());
    assert_eq!(fixture.channel().calls_containing("vm-guest-agent log"), Vec::<String>::new());
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
    assert_eq!(cancels(&fixture), Vec::<String>::new());
}

// The full start: agent installed, retired runtime generations removed, free space checked, state directory made,
// state file of the previous daemon removed, supervisor started, state file read, health proven, state recorded - with
// the phase table naming each step.
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
        (&state.launch_digest, &state.last_product_digest, &state.last_mount_digest),
        (&prep.launch_digest, &prep.product_digest, &prep.mount_digest)
    );
    assert_eq!(
        HostState::read(&fixture.settings, &fixture.worker).as_ref(),
        Some(&state),
        "the state is recorded for the next iteration"
    );
    // The start records the agent it installed, which a lane daemon runs from.
    let source = fixture.settings.vm_agent_source.as_ref().expect("the pool names the agent source");
    assert_eq!(
        state.agent_digest,
        avl_host_sys::guest::agent_digest(&std::fs::read(source).unwrap())
    );

    let channel = fixture.channel();
    // The supervisor start runs the lane-daemon verb of the installed agent under /usr/bin/env with the run environment.
    // The run starts in the state directory. The verb gets the home in the state directory and the IDE root, and its
    // boot makes them. No guest call of the controller prepares the launch.
    let starts = channel.calls_containing("vm-guest-agent start");
    assert_eq!(starts.len(), 1, "{starts:?}");
    for fragment in [
        " --cwd /vm/data/daemon -- ",
        " lane-daemon --state-dir /vm/data/daemon ",
        " LANG=C.UTF-8 ",
        " --home /vm/data/daemon/home",
        " --ide-root /vm/data/ide ",
    ] {
        assert!(starts[0].contains(fragment), "{fragment} missing from {}", starts[0]);
    }
    // The lane daemon is no Bazel test, so the start sets no variable of one.
    for bazel in [
        "TEST_TMPDIR=",
        "TEST_UNDECLARED_OUTPUTS_DIR=",
        "XML_OUTPUT_FILE=",
        "RUNFILES_DIR=",
        "SELF_LOCATION=",
    ] {
        assert!(!starts[0].contains(bazel), "{bazel} in {}", starts[0]);
    }
    assert_eq!(channel.calls_containing("vm-guest-agent launch-prep"), Vec::<String>::new());
    // The start stages no runtime generation and collects none. The agent has no verb for either, and no guest call
    // names one.
    for retired in ["vm-guest-agent stage", "vm-guest-agent gc "] {
        assert!(channel.calls_containing(retired).is_empty(), "{retired}");
    }
    // The start checks the free space of the guest once. Then it makes the state directory, which is the working
    // directory of the run, and the directory of the agent, before the supervisor starts the run.
    let calls = channel.calls();
    let at = |fragment: &str| position_of(&calls, fragment);
    assert_eq!(channel.calls_containing("/bin/df").len(), 1);
    let directories = at("/bin/mkdir -p /vm/data/daemon /vm/data/state");
    assert!(at("/bin/df") < directories && directories < at("vm-guest-agent start"));
    // Then it removes the state file that the previous daemon left, once, so the boot poll reads only the file of this
    // run.
    let state_file_removals = channel.calls_containing("/bin/rm -f ");
    assert_eq!(state_file_removals.len(), 1, "{state_file_removals:?}");
    let state_file_removal = at("/bin/rm -f /vm/data/daemon/daemon.json");
    assert!(directories < state_file_removal && state_file_removal < at("vm-guest-agent start"));
    // Before the free-space check the start removes the retired roots once, so the check counts their space.
    let retired = "/bin/rm -rf /vm/data/daemon-runtime /vm/data/out /vm/data/build-download";
    let removals = channel.calls_containing("/bin/rm -rf");
    assert_eq!(removals.len(), 1, "{removals:?}");
    assert!(removals[0].ends_with(retired), "{}", removals[0]);
    assert!(at(retired) < at("/bin/df"));
    // No argument names the checkout: the guest has none.
    assert!(
        !starts[0].contains(&fixture.root().display().to_string()),
        "the start names the checkout {}: {}",
        fixture.root().display(),
        starts[0]
    );

    // A start with no record of the daemon keeps no lane IDE.
    fixture.assert_one_ide_gc("--stop-all");

    // A green start cancels nothing.
    assert_eq!(cancels(&fixture), Vec::<String>::new());

    // The phase table names the fixed sequence, in order.
    let names: Vec<String> = timeline.take().into_iter().map(|timing| timing.name).collect();
    assert_eq!(names, PHASES);
}

// The previous daemon leaves its state file: a `/shutdown`, a cancel and a stop of the worker all keep it. The new
// daemon removes it only late in its boot, so the first read of the boot poll can come first. The start removes the
// file before the supervisor start, so the boot poll never records the token of the previous daemon. Here the old file
// names the pid of the new run, as after a restart of a container, so only the removal keeps the token apart.
#[tokio::test]
async fn the_start_removes_the_state_file_of_the_previous_daemon_before_the_supervisor_start() {
    let (fixture, prep) = start_ready().await;
    let mut previous: Value = serde_json::from_str(&fixture.daemon.state_file_json()).unwrap();
    previous["token"] = json!("token-of-the-previous-daemon");
    let guest_file = Arc::new(Mutex::new(Some(previous.to_string())));
    let removed = Arc::clone(&guest_file);
    fixture.on(
        "rm",
        handler(move |argv, _| {
            if has(argv, "/vm/data/daemon/daemon.json") {
                *lock(&removed) = None;
            }
            Ok(Captured::default())
        }),
    );
    // The first read finds what the guest holds, because the daemon is still in its boot. Each later read finds the
    // file that the boot of this run published.
    let held = Arc::clone(&guest_file);
    let published = fixture.daemon.state_file_json();
    let reads = Arc::new(AtomicUsize::new(0));
    let counted = Arc::clone(&reads);
    fixture.on(
        "cat",
        handler(move |_, _| {
            let file = if counted.fetch_add(1, Ordering::SeqCst) == 0 {
                lock(&held).clone()
            } else {
                Some(published.clone())
            };
            Ok(file.map_or_else(|| failed(1, "No such file or directory"), |document| said(&document)))
        }),
    );

    let state = start(&fixture, &prep).await.unwrap();
    assert_eq!(
        state.token, fixture.daemon.token,
        "the start records the token of the daemon of this run"
    );
    assert_eq!(reads.load(Ordering::SeqCst), 2, "the first read finds no file");
    let calls = fixture.channel().calls();
    let at = |fragment: &str| position_of(&calls, fragment);
    assert!(at("/bin/rm -f /vm/data/daemon/daemon.json") < at("vm-guest-agent start"));
}

// The boot poll accepts only the state file that names the pid of its run. A file of another pid is not the file of
// this run, so the poll goes on. A run that is still starting names no pid, so the poll waits for one. A budget that
// ends on a file of another pid refuses `daemon_state_file_foreign` and names both pids.
#[tokio::test]
async fn the_boot_poll_reads_only_the_state_file_of_the_process_of_its_run() {
    let (fixture, prep) = start_ready().await;
    let mut other: Value = serde_json::from_str(&fixture.daemon.state_file_json()).unwrap();
    other["token"] = json!("token-of-another-process");
    other["pid"] = json!(99);
    let other = other.to_string();
    let own = fixture.daemon.state_file_json();
    let reads = Arc::new(AtomicUsize::new(0));
    let counted = Arc::clone(&reads);
    let foreign = other.clone();
    fixture.on(
        "cat",
        handler(move |_, _| {
            let early = counted.fetch_add(1, Ordering::SeqCst) < 2;
            Ok(said(if early { &foreign } else { &own }))
        }),
    );
    let state = start(&fixture, &prep).await.unwrap();
    assert_eq!(state.token, fixture.daemon.token);
    assert_eq!(reads.load(Ordering::SeqCst), 3, "two files of another pid keep the poll going");

    // The first status names a run that is still starting.
    ready_again(&fixture, &prep);
    let probes = Arc::new(AtomicUsize::new(0));
    let counted = Arc::clone(&probes);
    fixture.on(
        "status",
        handler(move |argv, _| {
            let first = counted.fetch_add(1, Ordering::SeqCst) == 0;
            let phase = if first { "starting" } else { "running" };
            Ok(agent_reply("status", &daemon_run_state(argv_value(argv, "--run"), phase)))
        }),
    );
    let state = start(&fixture, &prep).await.unwrap();
    assert_eq!(state.token, fixture.daemon.token);
    assert_eq!(
        fixture.channel().calls_containing("/bin/cat").len(),
        2,
        "a run with no pid keeps the poll going"
    );

    // A file of another pid until the end of the budget.
    ready_again(&fixture, &prep);
    fixture.on("cat", answer_text(other));
    let failure = refusal(start(&fixture, &prep).await);
    assert_eq!(
        (failure.code.as_ref(), failure.exit),
        ("daemon_state_file_foreign", Exit::SOFTWARE),
        "{}",
        failure.message
    );
    assert!(
        failure.message.contains("names pid 99") && failure.message.contains(&format!("is pid {DAEMON_PID}")),
        "{}",
        failure.message
    );
    let details = details_of(&failure);
    assert_eq!(
        (&details["filePid"], &details["runPid"]),
        (&json!(99), &json!(DAEMON_PID)),
        "{details}"
    );
    assert_retired_without_a_record(&fixture, &failure);
}

/// The phase table of a green start, in order.
const PHASES: [&str; 10] = [
    "install-agent",
    "stop-daemon",
    "ide-gc",
    "reject-active-run",
    "remount",
    "parity-probe",
    "free-space",
    "supervisor-start",
    "boot-poll",
    "health-poll",
];

/// An `ide-gc` double that keeps one IDE when it is asked to keep a product, and stops one otherwise.
fn answer_ide_gc(fixture: &Fixture) {
    fixture.on(
        "ide-gc",
        handler(|argv, _| {
            Ok(said(if has(argv, "--keep-product") {
                r#"{"stopped":[],"kept":[{"launchKey":"launch-key-1","runId":"run-ide-launch-key-1"}],"removed":[]}"#
            } else {
                r#"{"stopped":[{"launchKey":"launch-key-1","runId":"run-ide-launch-key-1"}],"kept":[],"removed":[]}"#
            }))
        }),
    );
}

// A restart keeps the lane IDEs of its product only when it refreshes no VirtioFS share: a remount unmounts the share
// that the IDE runs from. The answer says what the gc did, which is what the timing line reads.
#[tokio::test]
async fn a_start_keeps_the_ides_of_its_product_only_when_it_does_not_remount() {
    let (fixture, prep) = ready(Fixture::tart_macos().await).await;
    answer_ide_gc(&fixture);
    let keep = IdeRetention::KeepProduct(&prep.product_digest);
    let ctx = Ctx::background();

    // No record: the start remounts, so it stops every IDE.
    let (_, ides) = fixture.host.start_daemon_keeping(&ctx, &fixture.worker, &prep, keep).await.unwrap();
    fixture.assert_one_ide_gc("--stop-all");
    assert_eq!((ides.kept.len(), ides.stopped.len()), (0, 1));
    assert_eq!(remounts(&fixture), 1);

    // A record of this launch and this mount: the start refreshes nothing, so the gc keeps the product.
    ready_again(&fixture, &prep);
    answer_ide_gc(&fixture);
    let mut previous = fixture.daemon.host_state("run-ui-daemon-previous", &prep.launch_digest);
    previous.last_mount_digest = prep.mount_digest.clone();
    previous.write(&fixture.settings, &fixture.worker).unwrap();
    let (_, ides) = fixture.host.start_daemon_keeping(&ctx, &fixture.worker, &prep, keep).await.unwrap();
    assert_kept_the_ide_of_the_product(&fixture, &prep, &ides);
}

// On Docker the refresh of the shares is a settle of the bind mounts and unmounts nothing. So a start without a record
// still keeps the lane IDEs of its product.
#[tokio::test]
async fn a_start_on_docker_keeps_the_ides_of_its_product_without_a_record() {
    let (fixture, prep) = start_ready().await;
    answer_ide_gc(&fixture);
    let keep = IdeRetention::KeepProduct(&prep.product_digest);
    let (_, ides) = fixture
        .host
        .start_daemon_keeping(&Ctx::background(), &fixture.worker, &prep, keep)
        .await
        .unwrap();
    assert_kept_the_ide_of_the_product(&fixture, &prep, &ides);
}

// An IDE that the gc could not stop holds the shares and the slot root of its context, so the start refuses before it
// remounts or starts anything.
#[tokio::test]
async fn a_failed_ide_gc_refuses_the_start() {
    let (fixture, prep) = start_ready().await;
    fixture.on("ide-gc", answer_exit(1));
    let failure = refusal(start(&fixture, &prep).await);
    assert_eq!(failure.code, "guest_agent_failed");
    assert_eq!(remounts(&fixture), 0);
    assert_eq!(fixture.channel().calls_containing("vm-guest-agent start"), Vec::<String>::new());
}

// A parity layout that does not show the descriptor refuses before the supervisor start, at 65.
#[tokio::test]
async fn a_stale_parity_layout_refuses_before_the_supervisor_start() {
    let (fixture, prep) = start_ready().await;
    fixture.on("test", answer_exit(1));
    let failure = refusal(start(&fixture, &prep).await);
    assert_eq!((failure.code.as_ref(), failure.exit), ("guest_runtime_unreachable", Exit::DATA_ERR));
    assert!(
        fixture.channel().calls_containing("vm-guest-agent start").is_empty(),
        "nothing may start over a stale layout"
    );
}

// A guest whose disk is full after the removal of the retired roots refuses the start in the `free-space` phase. The
// refusal comes before the supervisor start, so no daemon writes into a full disk. A removal of the retired roots that
// fails is a note, and the free-space check still decides.
#[tokio::test]
async fn a_full_guest_disk_refuses_the_start_before_the_supervisor_start() {
    let (fixture, prep) = start_ready().await;
    // 1 GiB free: below the floor of 5 GiB.
    fixture.on("df", answer_text(df_with_free_kib("1048576")));
    fixture.on("rm", answer_exit(1));
    let timeline = Timeline::collecting();
    let ctx = Ctx::background().with_timeline(timeline.clone());
    let failure = refusal(fixture.host.start_daemon(&ctx, &fixture.worker, &prep).await);
    assert_eq!((failure.code.as_ref(), failure.exit), ("guest_disk_full", Exit::UNAVAILABLE));
    assert!(
        fixture
            .stderr
            .text()
            .contains("keeps the retired roots /vm/data/daemon-runtime, /vm/data/out, /vm/data/build-download"),
        "{}",
        fixture.stderr.text()
    );
    let channel = fixture.channel();
    for retired in ["vm-guest-agent start", "/bin/mkdir -p /vm/data/daemon"] {
        assert!(channel.calls_containing(retired).is_empty(), "{retired}");
    }
    let names: Vec<String> = timeline.take().into_iter().map(|timing| timing.name).collect();
    assert_eq!(names.last().map(String::as_str), Some("free-space"), "{names:?}");
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

/// The guest builds its own tree inside the parity probe phase, before the probe and before the supervisor start, at
/// the root the launch already names. The first request carries no bytes; a guest without the tree refuses it, and the
/// second request carries the bytes of every staged runfile after the request line.
#[tokio::test]
async fn the_guest_builds_its_tree_before_the_probe_and_gets_the_bytes_when_it_has_none() {
    let (fixture, prep) = start_ready().await;
    let root = prep.guest_runfiles_root.clone();
    fixture.on(
        "runfiles-tree",
        handler(move |_, options| {
            let stdin = options.stdin.as_deref().unwrap_or_default();
            let (line, _) = avl_wire::runfiles::split_request_stdin(stdin);
            let request: avl_wire::runfiles::RunfilesTreeRequest = serde_json::from_slice(line).unwrap();
            if !request.with_bytes {
                return Ok(Captured {
                    exit_code: 70,
                    stderr: json!({"schemaVersion": 1, "ok": false, "command": "runfiles-tree",
                        "error": {"code": avl_wire::runfiles::STAGED_BYTES_MISSING_CODE, "message": "no tree"}})
                    .to_string(),
                    ..Captured::default()
                });
            }
            Ok(agent_reply(
                "runfiles-tree",
                &json!({"root": root, "digest": "d", "entries": 11, "reused": false}),
            ))
        }),
    );
    start(&fixture, &prep).await.unwrap();

    let calls = fixture.channel().calls();
    let trees: Vec<usize> = (0..calls.len())
        .filter(|index| calls[*index].line().contains(" runfiles-tree"))
        .collect();
    assert_eq!(trees.len(), 2, "{:?}", fixture.channel().lines());
    let at = |fragment: &str| position_of(&calls, fragment);
    assert!(trees[1] < at(&format!("/bin/test -f {}", self_location(&prep))));
    assert!(trees[1] < at("vm-guest-agent start"));
    let stdin = |index: usize| calls[index].options.stdin.clone().unwrap();
    let first = stdin(trees[0]);
    let (line, bytes) = avl_wire::runfiles::split_request_stdin(&first);
    let request: avl_wire::runfiles::RunfilesTreeRequest = serde_json::from_slice(line).unwrap();
    assert_eq!((request.with_bytes, bytes.len()), (false, 0));
    assert_eq!(
        request.path_map,
        *avl_host_sys::paths::GuestPaths::of(&fixture.settings).unwrap().map()
    );
    let second = stdin(trees[1]);
    let (_, bytes) = avl_wire::runfiles::split_request_stdin(&second);
    let expected: Vec<u8> = prep
        .guest_tree
        .staged_files
        .iter()
        .flat_map(|file| std::fs::read(file).unwrap())
        .collect();
    assert_eq!(bytes, expected.as_slice());
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
    // The fixture is a Linux guest, so the descriptor is the one of the Linux-guest daemon target.
    assert_eq!(prep.label, avl_wire::daemon::LINUX_GUEST_LABEL);
    let expected = format!(
        "{}/_main/plugins/air/tests/integration/ui/ui_daemon_linux_guest.runtime.json",
        prep.guest_runfiles_root
    );
    assert_eq!(self_location(&prep), expected);
    let probes = fixture.channel().calls_containing(&format!("/bin/test -f {expected}"));
    assert_eq!(probes.len(), 1, "{:?}", fixture.channel().calls_containing("/bin/test -f"));
}

/// On container-linux the daemon binds every interface of the container, because the published port forwards to
/// the container's address and not to its loopback. The argument of the lane-daemon verb says so, and the other
/// backends keep the loopback default.
#[cfg(unix)]
#[tokio::test]
async fn a_container_linux_launch_binds_every_interface_of_the_container() {
    let started = |fixture: &Fixture| fixture.channel().calls_containing("vm-guest-agent start").concat();
    let (fixture, prep) = ready(Fixture::over_container_linux().await).await;
    start(&fixture, &prep).await.unwrap();
    let call = started(&fixture);
    assert!(call.contains(" lane-daemon ") && call.ends_with(" --bind 0.0.0.0"), "{call}");

    let (tart, prep) = start_ready().await;
    start(&tart, &prep).await.unwrap();
    let call = started(&tart);
    assert!(call.contains(" lane-daemon ") && !call.contains("--bind"), "{call}");
}

// The run slot holds the `lane-daemon` verb of the installed guest agent. It gets the state directory, the port, the
// launch digest, the runtime descriptor, the runfiles root, the IDE root and the home in the state directory.
#[tokio::test]
async fn the_daemon_runner_starts_the_lane_daemon_verb_of_the_installed_agent() {
    let (fixture, prep) = start_ready().await;
    let settings = &fixture.settings;
    assert_eq!(
        daemon_command(settings, &prep, "/vm/data/daemon", "/runfiles/_main/ui_daemon.runtime.json"),
        [
            settings.vm_agent.as_str(),
            "lane-daemon",
            "--state-dir",
            "/vm/data/daemon",
            "--port",
            "27100",
            "--launch-digest",
            &prep.launch_digest,
            "--descriptor",
            "/runfiles/_main/ui_daemon.runtime.json",
            "--runfiles-root",
            &prep.guest_runfiles_root,
            "--ide-root",
            "/vm/data/ide",
            "--home",
            "/vm/data/daemon/home",
        ]
    );
}
