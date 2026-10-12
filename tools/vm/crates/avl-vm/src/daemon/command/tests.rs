use std::path::PathBuf;

use avl_host_sys::Signal;
use avl_host_testkit::agent::{agent_envelope, agent_reply, argv_value, run_state};
use avl_host_testkit::git::FAKE_HEAD;
use avl_host_testkit::{answer_text, handler, refusal};
use avl_wire::progress::{Checkout, Kind, TraceRef, VerdictTraces};
use avl_wire::report::{
    Failure as ReportFailure, FailureKind, FailureSource, Retrieval, RetrievedIntegrity, RetrievedXml, TraceArchive, TraceBundle, Tree,
};
use clap::Parser;
use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;
use crate::daemon::fixture::Fixture;
use crate::daemon::run::tests::{suite_catalog, three_lane_catalog};
use crate::daemon::testing::{
    EMPTY_COMPLETE_XML, PASSING_XML, PLENTIFUL_DF, Recorded, failing_run, ndjson_line, passing_run_lines, run_started_record,
    summary_record,
};
use crate::daemon::verdict::{checkout_of, failures_of, reproduce_commands, traces_of};

/// `vm daemon`, as far as its own grammar goes.
#[derive(Parser, Debug)]
struct DaemonProbe {
    #[command(subcommand)]
    verb: DaemonVerb,
}

#[derive(Parser, Debug)]
struct RunProbe {
    #[command(flatten)]
    run: RunCommandArgs,
}

/// `run`'s whole command line, `--test-env` included, as `command_run` takes it.
fn run_command_args(argv: &[&str]) -> RunCommandArgs {
    RunProbe::try_parse_from(std::iter::once("run").chain(argv.iter().copied()))
        .unwrap()
        .run
}

// The grammar rejects an extra argument and an unknown verb before anything runs, and names the verbs: `daemon warm
// --backend docker` is a plausible mistake, and it must cost a message rather than a lane build.
#[test]
fn the_daemon_grammar_refuses_arguments_and_unknown_verbs() {
    let parse = |argv: &[&str]| DaemonProbe::try_parse_from(std::iter::once("daemon").chain(argv.iter().copied()));
    let extra = parse(&["warm", "--backend"]).unwrap_err().to_string();
    assert!(extra.contains("unexpected argument '--backend'"), "{extra}");
    let bogus = parse(&["bogus", "extra"]).unwrap_err().to_string();
    assert!(bogus.contains("unrecognized subcommand 'bogus'"), "{bogus}");
    for (word, verb) in [
        ("start", DaemonVerb::Start),
        ("restart", DaemonVerb::Restart),
        ("status", DaemonVerb::Status),
        ("stop", DaemonVerb::Stop),
        ("log", DaemonVerb::Log),
        ("warm", DaemonVerb::Warm),
    ] {
        assert_eq!(parse(&[word]).unwrap().verb, verb);
        assert_eq!(verb.as_str(), word);
    }
}

// --- daemon warm -------------------------------------------------------------------------------------------------

// The verb's reason for existing: warming touches no worker, so it is answered before the receipt is resolved. The
// second half is the contrast that makes that a decision rather than an accident - every other verb refuses
// without one.
#[tokio::test]
async fn daemon_warm_needs_no_lease_file() {
    let fixture = Fixture::new().await;
    let ctx = Ctx::background();
    let outcome = fixture.host.command_daemon(&ctx, DaemonVerb::Warm, None).await.unwrap();
    assert!(outcome.text.starts_with("warmed docker "), "{:?}", outcome.text);
    for verb in [
        DaemonVerb::Start,
        DaemonVerb::Restart,
        DaemonVerb::Status,
        DaemonVerb::Stop,
        DaemonVerb::Log,
    ] {
        let failure = refusal(fixture.host.command_daemon(&ctx, verb, None).await);
        assert_eq!(failure.code, "lease_file_required", "daemon {}", verb.as_str());
    }
}

// A warm is the host build and the stamp over its outputs, and nothing else: no guest call, one build, into the
// scope every worker of the pool reads rather than one worker's own.
#[tokio::test]
async fn daemon_warm_reports_the_build_identities_and_touches_no_guest() {
    let fixture = Fixture::new().await;
    let ctx = Ctx::background();
    let outcome = fixture.host.command_daemon(&ctx, DaemonVerb::Warm, None).await.unwrap();
    // An unrouted verb answers exit 0 rather than failing, so a warm that reached the guest would pass silently if
    // the channel were not asked what it was called with.
    assert!(fixture.channel().calls().is_empty(), "warming reached the guest");
    assert_eq!(fixture.bazel.build_calls(), 1, "a warm is one host build");
    assert_eq!(outcome.data["worker"], json!(null), "the payload names no worker, as null");
    assert_eq!(outcome.data["config"], "docker");
    assert_eq!(outcome.data["guestOs"], "linux");
    // The reference stamp, through the same call a `daemon start` makes.
    let built = fixture
        .host
        .prepare_build(&ctx, &BuildScope::pool(&fixture.settings).unwrap())
        .await
        .unwrap();
    for (key, digest) in [
        ("runtimeDigest", &built.runtime_digest),
        ("launchDigest", &built.launch_digest),
        ("productDigest", &built.product_digest),
        ("mountDigest", &built.mount_digest),
    ] {
        assert_eq!(outcome.data[key], json!(digest), "{key}");
    }
    assert!(
        outcome
            .text
            .starts_with(&format!("warmed docker {} in ", &built.runtime_digest[..12]))
            && outcome.text.ends_with('s')
            && !outcome.text.contains('\n'),
        "one line naming the configuration, the digest and the cost: {:?}",
        outcome.text
    );
    // The shared scope, not the worker's: the digest cache this warmed is what the next worker's start reads.
    assert!(fixture.settings.runtime_root.join("shared-build/host-build.log").is_file());
    assert!(!fixture.settings.worker_dir(&fixture.worker).join("host-build.log").exists());
}

// `daemon status` reports a healthy daemon's own account, and "no healthy daemon" otherwise.
#[tokio::test]
async fn command_daemon_status_reports_the_daemons_own_account() {
    let fixture = Fixture::new().await;
    let receipt = fixture.lease_receipt();
    let ctx = Ctx::background();
    let outcome = fixture.host.command_daemon(&ctx, DaemonVerb::Status, Some(&receipt)).await.unwrap();
    assert_eq!(outcome.text, "no healthy daemon");
    assert_eq!(outcome.data["reachable"], json!(false));
    let prep = fixture.prepared().await;
    let state = fixture.seed_healthy_daemon(&prep);
    fixture.daemon.script().iteration_count = 4;
    let outcome = fixture.host.command_daemon(&ctx, DaemonVerb::Status, Some(&receipt)).await.unwrap();
    assert_eq!(outcome.text, format!("daemon={}\nide=stopped\niterations=4", state.run_id));
    assert_eq!(outcome.data["reachable"], json!(true));
    assert_eq!(outcome.data["daemon"]["runId"], json!(state.run_id));
    assert_eq!(outcome.data["status"]["iterationCount"], json!(4));
}

// `daemon stop` retires the state and says which run it stopped.
#[tokio::test]
async fn command_daemon_stop_retires_the_state() {
    let fixture = Fixture::new().await;
    let receipt = fixture.lease_receipt();
    let prep = fixture.prepared().await;
    let state = fixture.seed_healthy_daemon(&prep);
    fixture.install_slot_double(None);
    let outcome = fixture
        .host
        .command_daemon(&Ctx::background(), DaemonVerb::Stop, Some(&receipt))
        .await
        .unwrap();
    assert_eq!(outcome.text, "stopped");
    assert_eq!(outcome.data["stopped"], json!(state.run_id));
    assert_eq!(HostState::read(&fixture.settings, &fixture.worker), None);
    assert!(fixture.daemon.saw_request("POST /shutdown"));
    // A run whose controller died before its own removal left its secret files; the stop removes the directory.
    let removal = format!("/bin/rm -rf -- {}", fixture.settings.vm_run_secrets);
    assert!(fixture.channel().saw_call_containing(&removal), "{:#?}", fixture.channel().lines());
}

// Without a record, `daemon stop` asks the supervisor what holds the slot: it retires a daemon run, says so when
// there is nothing to stop, and refuses any other run.
#[tokio::test]
async fn a_daemon_stop_without_a_record_retires_the_daemon_run_in_the_slot() {
    let stop = async |fixture: &Fixture| {
        let receipt = fixture.lease_receipt();
        fixture
            .host
            .command_daemon(&Ctx::background(), DaemonVerb::Stop, Some(&receipt))
            .await
    };
    let cancels = |fixture: &Fixture| fixture.channel().calls_containing("vm-guest-agent cancel");

    let fixture = Fixture::new().await;
    let slot = fixture.install_slot_double(Some("run-ui-daemon-orphan"));
    let outcome = stop(&fixture).await.unwrap();
    assert_eq!(outcome.text, "stopped");
    assert_eq!(outcome.data["stopped"], json!("run-ui-daemon-orphan"));
    let sent = cancels(&fixture);
    assert!(sent.len() == 1 && sent[0].contains("--run run-ui-daemon-orphan"), "{sent:?}");
    assert_eq!(*slot.lock().unwrap(), None);

    let fixture = Fixture::new().await;
    fixture.install_slot_double(None);
    let outcome = stop(&fixture).await.unwrap();
    assert_eq!(outcome.text, "no daemon to stop");
    assert_eq!(outcome.data["stopped"], Value::Null);

    let fixture = Fixture::new().await;
    fixture.install_slot_double(Some("run-flow-1"));
    let failure = refusal(stop(&fixture).await);
    assert_eq!((failure.code.as_ref(), failure.exit), ("run_active", Exit::FAILURE));
    assert!(failure.message.contains("cannot stop the daemon"), "{}", failure.message);
    assert!(cancels(&fixture).is_empty());

    // A supervisor that does not finish the run is a refusal, never a "stopped".
    let fixture = Fixture::new().await;
    fixture.install_slot_double(Some("run-ui-daemon-orphan"));
    fixture.on(
        "cancel",
        handler(|argv, _| Ok(agent_reply("cancel", &run_state(argv_value(argv, "--run"), "running")))),
    );
    let failure = refusal(stop(&fixture).await);
    assert_eq!((failure.code.as_ref(), failure.exit), ("daemon_retire_failed", Exit::SOFTWARE));
    assert!(
        failure.message.contains("run-ui-daemon-orphan") && failure.message.contains("`daemon stop`"),
        "{}",
        failure.message
    );
}

// A recorded run the supervisor will not finish refuses `daemon stop` and keeps the record: dropping it and then
// failing in the slot check is how a live daemon run became one no record names.
#[tokio::test]
async fn a_daemon_stop_that_cannot_retire_the_recorded_run_keeps_the_record() {
    let fixture = Fixture::new().await;
    let receipt = fixture.lease_receipt();
    let prep = fixture.prepared().await;
    let state = fixture.seed_healthy_daemon(&prep);
    fixture.install_slot_double(Some("run-ui-daemon-seeded"));
    fixture.on(
        "cancel",
        handler(|argv, _| Ok(agent_reply("cancel", &run_state(argv_value(argv, "--run"), "running")))),
    );
    let failure = refusal(
        fixture
            .host
            .command_daemon(&Ctx::background(), DaemonVerb::Stop, Some(&receipt))
            .await,
    );
    assert_eq!((failure.code.as_ref(), failure.exit), ("daemon_retire_failed", Exit::SOFTWARE));
    assert!(failure.message.contains("`daemon stop`"), "{}", failure.message);
    assert_eq!(failure.details(), Some(json!({ "runId": state.run_id })));
    assert_eq!(HostState::read(&fixture.settings, &fixture.worker), Some(state));
    assert_eq!(
        fixture.channel().calls_containing("vm-guest-agent cancel").len(),
        1,
        "the unrecorded-run retire did not run a second cancel"
    );
}

/// The fake's recorded `tart exec` calls into the first worker, joined by spaces.
fn guest_execs(fixture: &Fixture) -> Vec<String> {
    fixture
        .tart
        .calls()
        .into_iter()
        .filter(|call| call.starts_with(&format!("exec {} ", fixture.worker)))
        .collect()
}

/// The fake the Tart backend resolved, as the gate would have: through Bazel, to its real path.
fn assert_resolved_the_fake(fixture: &Fixture) {
    let fake = avl_host_sys::fs::real_path(fixture.tart.executable()).unwrap();
    assert_eq!(fixture.manager.tart_executable(), Some(fake.as_path()));
}

// `daemon stop` never passes the Tart gate, and live it spawned `["", "exec", ...]`: an argv with no executable. Over
// the production guest channel with no `TART_BIN`, its slot check reaches the guest through the pinned Tart.
#[tokio::test]
async fn a_daemon_stop_reaches_the_guest_through_the_pinned_tart_without_a_record() {
    let fixture = Fixture::over_hypervisor().await;
    let receipt = fixture.lease_receipt();
    fixture
        .tart
        .answer_exec_verb("active", agent_envelope("active", &json!({"active": null})));
    let outcome = fixture
        .host
        .command_daemon(&Ctx::background(), DaemonVerb::Stop, Some(&receipt))
        .await
        .unwrap_or_else(|refusal| panic!("{refusal:?}"));
    assert_eq!(outcome.text, "no daemon to stop");
    assert_eq!(outcome.data["stopped"], Value::Null);
    let execs = guest_execs(&fixture);
    assert!(execs.iter().any(|call| call.contains("vm-guest-agent active --root")), "{execs:?}");
    assert_resolved_the_fake(&fixture);
}

// The recorded case of the same trap: the cancel went out with no executable, its failure was swallowed, the record
// was deleted, and the verb exited 1. Through the pinned Tart the cancel reaches the guest and the verb stops it.
#[tokio::test]
async fn a_daemon_stop_retires_a_recorded_daemon_through_the_pinned_tart() {
    let fixture = Fixture::over_hypervisor().await;
    let receipt = fixture.lease_receipt();
    let prep = fixture.prepared().await;
    let state = fixture.seed_healthy_daemon(&prep);
    fixture
        .tart
        .answer_exec_verb("cancel", agent_envelope("cancel", &run_state(&state.run_id, "finished")));
    fixture
        .tart
        .answer_exec_verb("active", agent_envelope("active", &json!({"active": null})));
    let outcome = fixture
        .host
        .command_daemon(&Ctx::background(), DaemonVerb::Stop, Some(&receipt))
        .await
        .unwrap_or_else(|refusal| panic!("{refusal:?}"));
    assert_eq!(outcome.text, "stopped");
    assert_eq!(outcome.data["stopped"], json!(state.run_id));
    assert_eq!(HostState::read(&fixture.settings, &fixture.worker), None);
    // The stop swallows a failed `/shutdown`, so the double's requests are the only trace of one.
    assert!(
        fixture.daemon.saw_request("POST /shutdown"),
        "the daemon double saw {:?}",
        fixture.daemon.script().requests
    );
    let execs = guest_execs(&fixture);
    let cancel = execs
        .iter()
        .position(|call| call.contains("vm-guest-agent cancel") && call.contains(&format!("--run {}", state.run_id)))
        .unwrap_or_else(|| panic!("no cancel: {execs:?}"));
    let active = execs
        .iter()
        .position(|call| call.contains("vm-guest-agent active"))
        .unwrap_or_else(|| panic!("no slot check: {execs:?}"));
    assert!(cancel < active, "{execs:?}");
    assert_resolved_the_fake(&fixture);
}

// `daemon log` is the other verb that never passes the gate; it reads the supervisor's tail through the pinned Tart.
#[tokio::test]
async fn daemon_log_reaches_the_guest_through_the_pinned_tart() {
    let fixture = Fixture::over_hypervisor().await;
    let receipt = fixture.lease_receipt();
    let prep = fixture.prepared().await;
    let state = fixture.seed_healthy_daemon(&prep);
    fixture.tart.answer_exec_verb(
        "log",
        agent_envelope(
            "log",
            &json!({"runId": state.run_id, "logPath": "/vm/log", "content": "line\n", "truncated": false}),
        ),
    );
    let outcome = fixture
        .host
        .command_daemon(&Ctx::background(), DaemonVerb::Log, Some(&receipt))
        .await
        .unwrap_or_else(|refusal| panic!("{refusal:?}"));
    assert_eq!(outcome.text, "line");
    assert!(
        guest_execs(&fixture).iter().any(|call| call.contains("vm-guest-agent log")),
        "{:?}",
        fixture.tart.calls()
    );
    assert_resolved_the_fake(&fixture);
}

// An interrupted `daemon stop` keeps the record and does not say "stopped": the interrupt fails both stops before
// either happened, and without the record the next `run` cannot recognise the daemon run still in the slot.
#[tokio::test]
async fn an_interrupted_daemon_stop_keeps_the_state() {
    let fixture = Fixture::new().await;
    let receipt = fixture.lease_receipt();
    let prep = fixture.prepared().await;
    let state = fixture.seed_healthy_daemon(&prep);
    // The Ctrl-C lands while the supervisor cancel is in flight, which it then fails, as a signalled exec does.
    let interrupts = fixture.interrupts.clone();
    fixture.on(
        "cancel",
        handler(move |_, _| {
            interrupts.deliver(Signal::Interrupt);
            Ok(avl_host_sys::Captured {
                exit_code: 130,
                ..avl_host_sys::Captured::default()
            })
        }),
    );
    let failure = refusal(
        fixture
            .host
            .command_daemon(&fixture.interrupts.context(), DaemonVerb::Stop, Some(&receipt))
            .await,
    );
    assert_eq!((failure.code.as_ref(), failure.exit), ("daemon_stop_interrupted", Exit::SOFTWARE));
    assert!(failure.message.contains("SIGINT"), "{}", failure.message);
    assert_eq!(HostState::read(&fixture.settings, &fixture.worker), Some(state));
}

// `daemon log` refuses without a daemon, and otherwise trims exactly one trailing newline from the supervisor's tail.
#[tokio::test]
async fn command_daemon_log_trims_exactly_one_trailing_newline() {
    let fixture = Fixture::new().await;
    let receipt = fixture.lease_receipt();
    let ctx = Ctx::background();
    let failure = refusal(fixture.host.command_daemon(&ctx, DaemonVerb::Log, Some(&receipt)).await);
    assert_eq!((failure.code.as_ref(), failure.exit), ("daemon_missing", Exit::USAGE));
    let prep = fixture.prepared().await;
    fixture.seed_healthy_daemon(&prep);
    fixture.on(
        "log",
        handler(|argv, _| {
            Ok(agent_reply(
                "log",
                &json!({"runId": argv_value(argv, "--run"), "logPath": "/vm/log",
                    "content": "line1\nline2\n\n", "truncated": false}),
            ))
        }),
    );
    let outcome = fixture.host.command_daemon(&ctx, DaemonVerb::Log, Some(&receipt)).await.unwrap();
    assert_eq!(outcome.text, "line1\nline2\n");
    assert_eq!(fixture.channel().calls_containing("--tail 200").len(), 1);
}

// `daemon start` runs the whole boot under one phase table and reports the endpoint plus the table.
#[tokio::test]
async fn command_daemon_start_reports_the_endpoint_and_phases() {
    let fixture = Fixture::new().await;
    let receipt = fixture.lease_receipt();
    let prep = fixture.prepared().await;
    fixture.install_happy_guest(&prep);
    fixture.daemon.script().launch_digest = prep.launch_digest.clone();
    fixture.mark_ready().await;
    let outcome = fixture
        .host
        .command_daemon(&Ctx::background(), DaemonVerb::Start, Some(&receipt))
        .await
        .unwrap();
    assert!(
        outcome.text.starts_with("daemon=run-ui-daemon-")
            && outcome
                .text
                .contains(&format!("\nendpoint={}:{}\n", fixture.worker, fixture.daemon.port()))
            && outcome.text.contains("phases ("),
        "{}",
        outcome.text
    );
    for name in ["host-paths", "worker-ready", "prepare-build", "boot-poll"] {
        assert!(outcome.text.contains(name), "the table names {name}:\n{}", outcome.text);
    }
    assert_eq!(
        (&outcome.data["worker"], &outcome.data["port"]),
        (&json!(fixture.worker), &json!(fixture.daemon.port()))
    );
    assert_eq!(outcome.data["runtimeDigest"], json!(prep.runtime_digest));
    assert_eq!(outcome.data["productDigest"], json!(prep.product_digest));
    assert!(!outcome.data["phases"].as_array().unwrap().is_empty());
    assert!(
        fixture.stderr.text().contains(r#""event":"daemonPhases""#),
        "the structured mirror of the table is published as progress"
    );
}

// The table: the total, one row per phase with its name and elapsed aligned, and each column only when the phase has
// something to say in it.
#[test]
fn the_phase_table_aligns_its_rows_and_rounds_to_a_tenth() {
    let table = render_phase_table(&[
        Timing {
            name: "boot-poll".to_owned(),
            elapsed_ms: 30_250,
            guest_calls: 20,
            guest_ms: 1_234,
            poll_iterations: Some(10),
            last_unsuccessful_probe_ms: Some(27_250),
            ..Timing::default()
        },
        Timing {
            name: "x".to_owned(),
            elapsed_ms: 940,
            host_calls: 1,
            host_ms: 940,
            ..Timing::default()
        },
    ]);
    assert_eq!(
        table,
        [
            "phases (31.2s total)",
            "  boot-poll            30.2s  guest 20x 1.2s  poll 10x  slept 3.0s  other 29.0s",
            "  x                     0.9s  host 1x 0.9s",
        ]
        .join("\n")
    );
}

// The slowest guest call is rendered beside the guest column it breaks down, and only when the phase made one.
#[test]
fn the_phase_table_names_the_slowest_guest_call_beside_the_guest_column() {
    let skeleton = "tart exec … /vm/agent stage";
    let table = render_phase_table(&[
        Timing {
            name: "prepare-launch".to_owned(),
            elapsed_ms: 11_000,
            guest_calls: 2,
            guest_ms: 10_900,
            slowest_guest_call_ms: Some(10_600),
            slowest_guest_call: Some(skeleton.to_owned()),
            ..Timing::default()
        },
        Timing {
            name: "host-paths".to_owned(),
            elapsed_ms: 100,
            host_calls: 1,
            host_ms: 100,
            ..Timing::default()
        },
    ]);
    assert!(table.contains(&format!("guest 2x 10.9s  slowest {skeleton} 10.6s")), "{table}");
    assert!(
        table
            .lines()
            .filter(|line| line.contains("host-paths"))
            .all(|line| !line.contains("slowest")),
        "{table}"
    );
}

// --- run's outcomes ----------------------------------------------------------------------------------------------

/// A warm daemon whose stream and XML the test chooses, and the caller's receipt.
async fn run_outcome_fixture(lines: Vec<String>, xml: &str) -> (Fixture, PathBuf) {
    let fixture = Fixture::with_environment(&[("USER", "suite-user")]).await;
    fixture.on("df", answer_text(PLENTIFUL_DF));
    let receipt = fixture.lease_receipt();
    let prep = fixture.prepared().await;
    fixture.seed_healthy_daemon(&prep);
    fixture.script_run(lines, xml);
    (fixture, receipt)
}

/// One `run` of `argv` under `receipt` (or self-leased without one): its answer, and the verdict it published.
async fn run_for_verdict(fixture: &Fixture, argv: &[&str], receipt: Option<&Path>) -> (Result<Outcome, Refusal>, Option<Verdict>) {
    let recorded = Recorded::start(&fixture.host.reporter);
    let result = fixture.host.command_run(&Ctx::background(), &run_command_args(argv), receipt).await;
    (result, recorded.verdict())
}

fn run_data(value: &Value) -> RunData {
    serde_json::from_value(value.clone()).unwrap_or_else(|error| panic!("not run data ({error}): {value}"))
}

fn lanes_data(value: &Value) -> RunLanesData {
    serde_json::from_value(value.clone()).unwrap_or_else(|error| panic!("not lanes data ({error}): {value}"))
}

fn details(failure: &Refusal) -> Value {
    failure.details().expect("the refusal carries details")
}

// A `--test-env` file that cannot be read refuses the run before the plan, the build or any guest command.
#[tokio::test]
async fn command_run_refuses_an_unreadable_secret_before_any_work() {
    let (fixture, receipt) = run_outcome_fixture(passing_run_lines("it-25"), PASSING_XML).await;
    fixture.forget_calls();
    let missing = format!(
        "AIR_LIVE_CENTRAL_LOGIN=@{}",
        fixture.settings.runtime_root.join("nowhere").display()
    );
    let (result, verdict) = run_for_verdict(&fixture, &["--lane", "ui", "--test-env", &missing], Some(&receipt)).await;
    let refusal = result.unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("run_secret_unreadable", Exit::NO_INPUT));
    assert!(verdict.is_none());
    assert_eq!(fixture.channel().lines(), Vec::<String>::new());
    assert!(!fixture.daemon.saw_request("POST /run"));
}

// A green run with a secret answers its verdict, and its journal, which records every published line, holds no value.
#[tokio::test]
async fn command_run_with_a_secret_journals_no_value() {
    let (fixture, receipt) = run_outcome_fixture(passing_run_lines("it-26"), PASSING_XML).await;
    let secret = "central-login-export-0123456789abcdef";
    let file = fixture.settings.runtime_root.join("login");
    std::fs::write(&file, secret).unwrap();
    let option = format!("AIR_LIVE_CENTRAL_LOGIN=@{}", file.display());
    let (result, verdict) = run_for_verdict(&fixture, &["--lane", "ui", "--test-env", &option], Some(&receipt)).await;
    let outcome = result.unwrap();
    assert_eq!(verdict.expect("a verdict is published").status, Status::Passed);
    assert!(!outcome.data.to_string().contains(secret));
    let runs = fixture.settings.runtime_root.join(avl_trace_tools::discover::VM_RUNS_DIR);
    let journals: Vec<PathBuf> = std::fs::read_dir(&runs)
        .unwrap()
        .map(|entry| entry.unwrap().path().join(avl_base::journal::FILE_NAME))
        .collect();
    assert_eq!(journals.len(), 1, "{journals:?}");
    let journal = std::fs::read_to_string(&journals[0]).unwrap();
    assert!(journal.contains("run secrets: AIR_LIVE_CENTRAL_LOGIN"), "{journal}");
    assert!(!journal.contains(secret), "{journal}");
    assert!(
        !journal.contains(&file.display().to_string()),
        "the journal names no secret file: {journal}"
    );
    assert!(fixture.channel().saw_call_containing("/bin/rm -f --"));
}

// `--test-env NAME=@-` reads the pipe once and hands the guest exactly its bytes, on the write's stdin; no argv, no
// journal line and no answer carries them, and no host file is named.
#[tokio::test]
async fn command_run_hands_the_guest_the_bytes_of_its_stdin() {
    let (fixture, receipt) = run_outcome_fixture(passing_run_lines("it-27"), PASSING_XML).await;
    let export = "central-login-export-from-a-pipe-0123456789\n";
    fixture.stdin.feed(export.as_bytes());
    let (result, verdict) = run_for_verdict(
        &fixture,
        &["--lane", "ui", "--test-env", "AIR_LIVE_CENTRAL_LOGIN=@-"],
        Some(&receipt),
    )
    .await;
    let outcome = result.unwrap();
    assert_eq!(verdict.expect("a verdict is published").status, Status::Passed);
    assert_eq!(fixture.stdin.reads(), 1);
    let directory = &fixture.settings.vm_run_secrets;
    let written = fixture
        .channel()
        .options_for_call_containing(&format!("exec cat > \"$1\" sh {directory}/AIR_LIVE_CENTRAL_LOGIN"))
        .expect("the secret is written");
    assert_eq!(written.stdin.as_deref(), Some(export.as_bytes()));
    let secret = export.trim();
    for line in fixture.channel().lines() {
        assert!(!line.contains(secret), "{line}");
    }
    assert!(!outcome.data.to_string().contains(secret));
    assert!(
        fixture
            .channel()
            .saw_call_containing(&format!("/bin/rm -f -- {directory}/AIR_LIVE_CENTRAL_LOGIN"))
    );
}

// The command line's own mistakes about stdin cost nothing: no read, no guest command, no daemon request.
#[tokio::test]
async fn command_run_refuses_stdin_misuse_before_any_work() {
    let (fixture, receipt) = run_outcome_fixture(passing_run_lines("it-28"), PASSING_XML).await;
    fixture.forget_calls();
    fixture.stdin.feed(b"central-login-export-from-a-pipe-0123456789");
    let twice = ["--lane", "ui", "--test-env", "FIRST=@-", "--test-env", "SECOND=@-"];
    let (result, verdict) = run_for_verdict(&fixture, &twice, Some(&receipt)).await;
    let refusal = result.unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("usage", Exit::USAGE));
    assert!(refusal.message.contains("@- once"), "{}", refusal.message);
    assert!(verdict.is_none());
    fixture.stdin.set_terminal(true);
    let (result, _) = run_for_verdict(&fixture, &["--lane", "ui", "--test-env", "LOGIN=@-"], Some(&receipt)).await;
    let refusal = result.unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("usage", Exit::USAGE));
    assert!(
        refusal.message.contains("| vm.cmd run ... --test-env LOGIN=@-"),
        "{}",
        refusal.message
    );
    assert_eq!(fixture.stdin.reads(), 0);
    assert_eq!(fixture.channel().lines(), Vec::<String>::new());
    assert!(!fixture.daemon.saw_request("POST /run"));
}

#[tokio::test]
async fn command_run_reports_a_passed_iteration() {
    let (fixture, receipt) = run_outcome_fixture(passing_run_lines("it-20"), PASSING_XML).await;
    let (result, verdict) = run_for_verdict(&fixture, &["--lane", "ui"], Some(&receipt)).await;
    let outcome = result.unwrap();
    // The verdict is the answer every human form renders, so the outcome carries no text of its own.
    assert_eq!(outcome.text, "");
    let verdict = verdict.expect("a verdict is published");
    assert_eq!(verdict.status, Status::Passed);
    assert_eq!(verdict.summary, "1 test(s) passed");
    assert_eq!(verdict.counts.started, 1);
    assert!(verdict.timing.as_deref().is_some_and(|timing| timing.starts_with("build ")));
    let data = run_data(&outcome.data);
    assert_eq!(data.iteration_id, "it-20");
    assert_eq!(data.tests_started, 1);
    assert!(!data.has_failures);
    assert_eq!(data.ide_action, "reuse");
    assert_eq!(data.selection, "lane ui");
    assert_eq!(data.lease, None, "a run under the caller's receipt reports no lease of its own");
    assert_eq!(verdict.report.as_deref(), Some(data.report_path.as_str()));
    // A lane that recorded nothing has no traces at all.
    assert_eq!(verdict.traces, None);
}

/// A report with nothing in it, for the verdict helpers to be handed.
fn blank_report() -> RunReport {
    avl_report::report::build(avl_report::report::Input {
        iteration_id: "it".to_owned(),
        daemon_run_id: "run".to_owned(),
        daemon_boot_stamp: "boot".to_owned(),
        selection: "lane ui".to_owned(),
        started_at: "2026-08-23T12:00:00.000Z".to_owned(),
        completed_at: "2026-08-23T12:00:01.000Z".to_owned(),
        summary: None,
        events: Vec::new(),
        source: RetrievedXml {
            guest_path: "/vm/test.xml".to_owned(),
            retrieval: Retrieval::Unavailable,
            integrity: RetrievedIntegrity::Missing,
            xml: None,
            diagnostic: None,
        },
        evidence: Vec::new(),
        protocol_diagnostic: None,
        hot_jar_drift: None,
        traces: Vec::new(),
        traces_error: None,
        tree: None,
        tree_error: None,
    })
    .unwrap()
}

fn bundle(id: &str, scenario: &str, status: &str, has_video: bool) -> TraceBundle {
    TraceBundle {
        id: id.to_owned(),
        entry: format!("run/AirTest/{scenario}/"),
        test_class: "AirTest".to_owned(),
        scenario: scenario.to_owned(),
        flow: None,
        status: status.to_owned(),
        has_video,
    }
}

fn archive(path: &str, bundles: Vec<TraceBundle>) -> TraceArchive {
    TraceArchive {
        path: path.to_owned(),
        bytes: 9,
        bundles,
    }
}

// The verdict counts the traces, names the ones that did not pass, and says why a pull failed, so a run whose traces
// did not arrive does not read like one that recorded nothing. Two iterations are in the run's traces directory.
#[test]
fn the_verdict_names_the_traces_that_did_not_pass() {
    let mut pulled = blank_report();
    pulled.trace_archives = vec![
        archive("/host/runs/r/traces/it/001.zip", vec![bundle("a", "opens", "passed", true)]),
        archive("/host/runs/r/traces/it/002.zip", vec![bundle("b", "renames", "failed", true)]),
    ];
    assert_eq!(
        traces_of([&pulled]),
        Some(VerdictTraces {
            scenarios: 2,
            not_passed: vec![TraceRef {
                bundle_id: "b".to_owned(),
                test_class: "AirTest".to_owned(),
                scenario: "renames".to_owned(),
                status: "failed".to_owned(),
                has_video: true,
            }],
            with_video: 2,
            directory: Some("/host/runs/r/traces/it".to_owned()),
            error: None,
        })
    );
    let reason = "the guest agent's trace-pack-ready in air-docker-1 exited with 70";
    pulled.traces_error = Some(reason.to_owned());
    let failing = traces_of([&pulled]).unwrap();
    assert_eq!((failing.error.as_deref(), failing.scenarios), (Some(reason), 2));
    assert_eq!(traces_of([&blank_report()]), None);
    let mut other = blank_report();
    other.trace_archives = vec![archive(
        "/host/runs/r/traces/it2/001.zip",
        vec![bundle("c", "closes", "passed", false)],
    )];
    let merged = traces_of([&pulled, &other]).unwrap();
    assert_eq!(merged.scenarios, 3);
    assert_eq!(merged.directory.as_deref(), Some("/host/runs/r/traces"));
}

fn report_failure(class_name: Option<&str>, suite: Option<&str>, test_name: &str, message: &str) -> ReportFailure {
    ReportFailure {
        source: FailureSource::JunitXml,
        suite: suite.map(str::to_owned),
        suite_timestamp: None,
        class_name: class_name.map(str::to_owned),
        test_name: test_name.to_owned(),
        kind: FailureKind::Failure,
        r#type: None,
        message: message.to_owned(),
        detail: None,
        relevant_frames: Vec::new(),
        message_truncated: false,
        detail_truncated: false,
    }
}

// A failure carries the trace of its scenario. The bundle names the class by the simple name and the report by the
// FQN, and an XML failure names the test as the JUnit reporter does.
#[test]
fn a_failure_carries_the_trace_of_its_scenario() {
    let class = Some("com.example.AirTest");
    let bundles = [bundle("a", "opens", "passed", false), bundle("b", "renames", "failed", false)];
    let failures = failures_of(
        &[
            report_failure(class, None, "renames", "boom\nstack"),
            report_failure(class, None, "[2] opens", ""),
            report_failure(class, None, "closes", ""),
        ],
        &bundles,
    );
    assert_eq!(failures[0].trace.as_ref().map(|trace| trace.bundle_id.as_str()), Some("b"));
    assert_eq!(failures[0].message.as_deref(), Some("boom"));
    assert_eq!(failures[0].class, "com.example.AirTest");
    assert_eq!(
        failures[1].trace.as_ref().map(|trace| trace.bundle_id.as_str()),
        Some("a"),
        "an XML name with an index finds its trace"
    );
    assert_eq!(failures[2].trace, None);
}

// The verdict says whether the tree the build read was clean.
#[tokio::test]
async fn the_verdict_states_the_tree() {
    let with = |tree: Option<Tree>, error: Option<&str>| {
        let mut document = blank_report();
        document.tree = tree;
        document.tree_error = error.map(str::to_owned);
        checkout_of(&document)
    };
    let tree = |uncommitted: Vec<String>, count: usize| Tree {
        head: FAKE_HEAD.to_owned(),
        uncommitted,
        uncommitted_count: count,
    };
    assert_eq!(
        with(Some(tree(Vec::new(), 0)), None),
        Some(Checkout {
            head: Some(FAKE_HEAD.to_owned()),
            uncommitted: 0,
            error: None
        })
    );
    assert_eq!(
        with(Some(tree(vec!["a".to_owned()], 73)), None),
        Some(Checkout {
            head: Some(FAKE_HEAD.to_owned()),
            uncommitted: 73,
            error: None
        })
    );
    let reason = "fatal: not a git repository";
    assert_eq!(
        with(None, Some(reason)),
        Some(Checkout {
            head: None,
            uncommitted: 0,
            error: Some(reason.to_owned())
        })
    );
    assert_eq!(with(None, None), None);

    let (fixture, receipt) = run_outcome_fixture(passing_run_lines("it-27"), PASSING_XML).await;
    fixture.git().status(&[" M plugins/air/a.kt", "?? plugins/air/b.kt"]);
    let (result, verdict) = run_for_verdict(&fixture, &["--lane", "ui"], Some(&receipt)).await;
    result.unwrap();
    assert_eq!(
        verdict.and_then(|verdict| verdict.checkout).map(|checkout| checkout.uncommitted),
        Some(2)
    );
}

#[tokio::test]
async fn command_run_reports_failed_tests_at_six() {
    let (lines, xml) = failing_run("it-21");
    let (fixture, receipt) = run_outcome_fixture(lines, &xml).await;
    let (result, verdict) = run_for_verdict(&fixture, &["--lane", "ui"], Some(&receipt)).await;
    let failure = refusal(result);
    assert_eq!((failure.code.as_ref(), failure.exit), ("tests_failed", Exit::TESTS_FAILED));
    // The message is the verdict's one line: it counts and names the first failure's first line.
    let want = "1 test(s) and 0 container(s) failed of 1 started (iteration it-21); first: com.example.MyTest — boom";
    assert_eq!(failure.message, want);
    let verdict = verdict.expect("a verdict is published");
    assert_eq!(verdict.summary, want);
    assert_eq!(verdict.code.as_deref(), Some("tests_failed"));
    // A red run hands back the command that re-runs just what failed, in the verdict and in the data both.
    assert_eq!(verdict.rerun, ["run com.example.MyTest"]);
    assert_eq!(verdict.failures[0].class, "com.example.MyTest");
    let data = run_data(&details(&failure));
    assert!(data.has_failures);
    assert_eq!(data.reproduce, ["run com.example.MyTest"]);
}

// One command per failed class, however many of its cases failed, and none for a failure that named no class - `run`
// takes a class or an FQN, and a suite display name is neither.
#[test]
fn the_reproduce_list_is_one_per_failed_class_and_skips_an_unnamed_one() {
    let commands = reproduce_commands(&[
        report_failure(Some("a.OneTest"), None, "first", ""),
        report_failure(Some("a.OneTest"), None, "second", ""),
        report_failure(None, Some("Some Suite"), "a container with no class", ""),
        report_failure(Some("b.TwoTest"), None, "third", ""),
    ]);
    assert_eq!(commands, ["run a.OneTest", "run b.TwoTest"]);
    assert!(reproduce_commands(&[]).is_empty());
}

#[tokio::test]
async fn command_run_reports_all_skipped_with_the_reasons() {
    let lines = vec![
        ndjson_line(&run_started_record("it-22")),
        ndjson_line(
            &json!({"event": "containerSkipped", "timestamp": "t", "displayName": "MyTest", "id": "c1",
            "className": "com.example.MyTest", "reason": "codex is not installed on air-docker-1 (docker linux guest)"}),
        ),
        ndjson_line(&summary_record("it-22", 0, 0, 0, 1, 0, false)),
    ];
    let (fixture, receipt) = run_outcome_fixture(lines, EMPTY_COMPLETE_XML).await;
    let (result, verdict) = run_for_verdict(&fixture, &["--lane", "ui"], Some(&receipt)).await;
    let failure = refusal(result);
    assert_eq!((failure.code.as_ref(), failure.exit), ("all_tests_skipped", Exit::TESTS_FAILED));
    assert_eq!(failure.message, "lane ui ran nothing: every class was skipped (iteration it-22)");
    let verdict = verdict.expect("a verdict is published");
    assert_eq!(verdict.status, Status::AllSkipped);
    assert_eq!(verdict.skipped.len(), 1);
    assert_eq!(verdict.skipped[0].name, "com.example.MyTest");
    assert!(verdict.skipped[0].reason.starts_with("codex is not installed"));
}

#[tokio::test]
async fn command_run_reports_no_tests_discovered_at_six() {
    let lines = vec![
        ndjson_line(&run_started_record("it-23")),
        ndjson_line(&summary_record("it-23", 0, 0, 0, 0, 0, false)),
    ];
    let (fixture, receipt) = run_outcome_fixture(lines, EMPTY_COMPLETE_XML).await;
    let (result, verdict) = run_for_verdict(&fixture, &["--lane", "ui"], Some(&receipt)).await;
    let failure = refusal(result);
    assert_eq!((failure.code.as_ref(), failure.exit), ("no_tests_discovered", Exit::TESTS_FAILED));
    assert!(
        failure.message.contains("matched no tests in the hot tier (iteration it-23)"),
        "{:?}",
        failure.message
    );
    let verdict = verdict.expect("a verdict is published");
    assert_eq!(verdict.code.as_deref(), Some("no_tests_discovered"));
    assert_eq!(verdict.summary, failure.message);
}

// A run that lost its IDE names every suite it never reached, in the verdict every human form renders: the verdict
// diagnostic names the first few and counts the rest, which is right for one line and wrong for the reader's next
// move - re-running exactly the suites that were dropped.
#[tokio::test]
async fn command_run_names_the_suites_it_never_reached() {
    let lines = vec![
        ndjson_line(&run_started_record("it-25")),
        ndjson_line(&json!({"event": "planStarted", "timestamp": "t", "discoveredExecutions": 3,
            "classNames": ["a.AlphaTest", "a.BetaTest", "a.GammaTest"]})),
        ndjson_line(&json!({"event": "testStarted", "timestamp": "t", "displayName": "one", "id": "1",
            "className": "a.AlphaTest"})),
        ndjson_line(&json!({"event": "watchdogExpired", "reason": "ide_intrusion",
            "deadline": "2026-08-27T12:30:00.000Z", "expiredAt": "2026-08-27T12:30:01.000Z"})),
    ];
    let (fixture, receipt) = run_outcome_fixture(lines, EMPTY_COMPLETE_XML).await;
    let (result, verdict) = run_for_verdict(&fixture, &["--lane", "ui"], Some(&receipt)).await;
    let failure = refusal(result);
    assert!(
        failure.message.contains("2 class(es) were selected and never reported"),
        "{:?}",
        failure.message
    );
    // The suite the run did reach is not in the list: it started, so the watchdog's own line names it.
    let verdict = verdict.expect("a verdict is published");
    assert_eq!(verdict.status, Status::InfrastructureError);
    assert_eq!(verdict.unreported, ["a.BetaTest", "a.GammaTest"]);
}

#[tokio::test]
async fn command_run_reports_a_watchdog_expiry_as_infrastructure() {
    let lines = vec![
        ndjson_line(&run_started_record("it-24")),
        ndjson_line(&json!({"event": "watchdogExpired", "reason": "active_execution_timeout",
            "deadline": "2026-08-23T12:30:00.000Z", "expiredAt": "2026-08-23T12:30:01.000Z"})),
        ndjson_line(&summary_record("it-24", 1, 0, 0, 0, 0, false)),
    ];
    let (fixture, receipt) = run_outcome_fixture(lines, EMPTY_COMPLETE_XML).await;
    let (result, verdict) = run_for_verdict(&fixture, &["--lane", "ui"], Some(&receipt)).await;
    let failure = refusal(result);
    assert_eq!(
        verdict.and_then(|verdict| verdict.code).as_deref(),
        Some("daemon_watchdog_expired"),
        "a run with a report publishes its verdict"
    );
    assert_eq!((failure.code.as_ref(), failure.exit), ("daemon_watchdog_expired", Exit::SOFTWARE));
    assert!(
        failure.message.contains("daemon watchdog expired: active_execution_timeout"),
        "{:?}",
        failure.message
    );
    assert_eq!(run_data(&details(&failure)).report.status, Status::InfrastructureError);
}

// --- a run that takes its own lease ------------------------------------------------------------------------------

/// A warm daemon on the pool's first worker and every worker unleased, which is what a `run` with no receipt finds.
async fn self_leased_fixture(lines: Vec<String>, xml: &str) -> Fixture {
    let fixture = Fixture::with_environment(&[("USER", "suite-user")]).await;
    fixture.on("df", answer_text(PLENTIFUL_DF));
    let prep = fixture.prepared().await;
    fixture.seed_healthy_daemon(&prep);
    fixture.script_run(lines, xml);
    fixture
}

/// The regression of 2026-09-27: a run with no receipt whose daemon start never passes its health poll gives its
/// lease back, because the start retired the run it launched. Without that retire the slot keeps the run, the
/// release refuses, and the lease stays held with no verb that frees it.
#[tokio::test]
async fn a_self_leased_run_whose_daemon_never_answers_gives_its_lease_back() {
    let fixture = Fixture::with_environment(&[("USER", "suite-user")]).await;
    fixture.on("df", answer_text(PLENTIFUL_DF));
    let prep = fixture.prepared().await;
    fixture.install_happy_guest(&prep);
    fixture.install_slot_double(None);
    // No daemon is recorded, so the iteration starts one, and a start asks the readiness gate first.
    fixture.mark_ready().await;
    {
        let mut script = fixture.daemon.script();
        script.status_code = Some(500);
        script.launch_digest = prep.launch_digest.clone();
    }
    let recorded = Recorded::start(&fixture.host.reporter);
    let (result, _) = run_for_verdict(&fixture, &["--lane", "ui"], None).await;
    let failure = refusal(result);
    assert_eq!(
        (failure.code.as_ref(), failure.exit),
        ("daemon_unreachable", Exit::UNAVAILABLE),
        "{}",
        failure.message
    );
    // A start's refusal is the run's answer, so the release says what became of the lease in its own phase: a
    // release that failed would finish that phase with `release_failed`.
    let release: Vec<Value> = recorded
        .data_of::<Value>(Kind::Phase)
        .into_iter()
        .filter(|phase| phase["phase"] == json!("release"))
        .collect();
    assert!(
        release.last().is_some_and(|phase| phase["state"] == json!("finished")),
        "{release:?}"
    );
    require_unleased(&fixture);
    assert_eq!(fixture.channel().calls_containing("vm-guest-agent cancel").len(), 1);
}

fn require_unleased(fixture: &Fixture) {
    for worker in &fixture.settings.workers {
        assert!(!fixture.settings.lease_path(worker).exists(), "{worker} is still leased");
    }
}

// A run with no receipt leases a worker under `<user>-run-<uuid>`, runs the iteration on it, and gives it back. The
// verdict says so in the data and in the published verdict, because the lease is the one thing the caller did not
// hold.
#[tokio::test]
async fn a_run_without_a_receipt_leases_a_worker_and_gives_it_back() {
    let fixture = self_leased_fixture(passing_run_lines("it-40"), PASSING_XML).await;
    let (result, verdict) = run_for_verdict(&fixture, &["--lane", "ui"], None).await;
    let data = run_data(&result.unwrap().data);
    assert_eq!(data.iteration_id, "it-40");
    let lease = data.lease.expect("the data carries the lease");
    assert!(
        lease.holder.starts_with("suite-user-run-") && lease.holder.len() > "suite-user-run-".len(),
        "{}",
        lease.holder
    );
    assert_eq!(lease.worker, fixture.worker);
    assert_eq!(lease.disposition, LeaseDisposition::Released);
    assert_eq!(lease.lease_file, None);
    let taken = verdict.and_then(|verdict| verdict.lease).expect("the verdict names the lease");
    assert_eq!(taken.workers, std::slice::from_ref(&fixture.worker));
    assert!(taken.released());
    assert!(taken.receipts.is_empty());
    require_unleased(&fixture);
}

// A run keeps a journal under `runs/<runId>`, named by the holder of its lease, from `runStarted` to `runFinished`,
// with the phases and the daemon's records in between, in order.
#[tokio::test]
async fn a_run_keeps_a_journal_named_by_its_holder() {
    let fixture = self_leased_fixture(passing_run_lines("it-42"), PASSING_XML).await;
    let (result, _) = run_for_verdict(&fixture, &["--lane", "ui"], None).await;
    let holder = run_data(&result.unwrap().data).lease.unwrap().holder;
    let journal = std::fs::read_to_string(fixture.settings.runtime_root.join("runs").join(&holder).join("events.ndjson"))
        .unwrap_or_else(|error| panic!("no journal for {holder}: {error}"));
    let kinds: Vec<String> = journal
        .lines()
        .map(|line| {
            let record: Value = serde_json::from_str(line).unwrap_or_else(|_| panic!("a torn record {line:?}"));
            let kind = record["event"].as_str().unwrap().to_owned();
            if kind == "phase" {
                format!(
                    "{kind}:{}:{}",
                    record["data"]["phase"].as_str().unwrap(),
                    record["data"]["state"].as_str().unwrap()
                )
            } else {
                kind
            }
        })
        .collect();
    let joined = kinds.join(" ");
    for want in [
        "runStarted runPlanned",
        "phase:lease:started",
        "phase:iteration:finished",
        "iterationReported",
        "phase:release:finished",
        "runFinished",
    ] {
        assert!(joined.contains(want), "the journal lacks {want:?}: {joined}");
    }
    assert!(joined.starts_with("runStarted") && joined.ends_with("runFinished"), "{joined}");
}

// A red run still gives the worker back: the lease is released before the verdict, and the verdict carries what
// became of it.
#[tokio::test]
async fn a_self_leased_run_releases_the_worker_when_the_tests_fail() {
    let (lines, xml) = failing_run("it-41");
    let fixture = self_leased_fixture(lines, &xml).await;
    let (result, _) = run_for_verdict(&fixture, &["--lane", "ui"], None).await;
    let failure = refusal(result);
    assert_eq!((failure.code.as_ref(), failure.exit), ("tests_failed", Exit::TESTS_FAILED));
    assert_eq!(
        run_data(&details(&failure)).lease.map(|lease| lease.disposition),
        Some(LeaseDisposition::Released)
    );
    require_unleased(&fixture);
}

// An iteration that ends in a refusal of its own, here a busy daemon, releases the worker too.
#[tokio::test]
async fn a_self_leased_run_releases_the_worker_when_the_iteration_refuses() {
    let fixture = self_leased_fixture(Vec::new(), EMPTY_COMPLETE_XML).await;
    fixture.daemon.script().conflict = Some(json!({"reason": "busy"}));
    let (result, _) = run_for_verdict(&fixture, &["--lane", "ui"], None).await;
    assert_eq!(refusal(result).code, "daemon_busy");
    require_unleased(&fixture);
}

// A selection the controller refuses is refused before a worker is leased, so it costs the pool nothing.
#[tokio::test]
async fn a_self_leased_run_refuses_its_selection_before_it_leases() {
    let fixture = self_leased_fixture(Vec::new(), EMPTY_COMPLETE_XML).await;
    suite_catalog(fixture.root());
    let (result, _) = run_for_verdict(&fixture, &["flow-java-to-kotlin"], None).await;
    assert_eq!(refusal(result).code, "no_affected_suite");
    require_unleased(&fixture);
    assert!(!fixture.daemon.saw_request("/run"), "a refused selection reached the daemon");
}

// An interrupt while the iteration is in flight keeps the lease, as `shard` does: a release proves no run is active,
// which is not true here. The refusal names the receipt that frees the worker.
#[tokio::test]
async fn an_interrupted_self_leased_run_keeps_its_lease_and_names_the_receipt() {
    let fixture = self_leased_fixture(passing_run_lines("it-42"), PASSING_XML).await;
    let block = tokio_util::sync::CancellationToken::new();
    fixture.daemon.script().block = Some(block.clone());
    // The interrupt arrives once the iteration is in flight: the daemon holds the `/run` open until then.
    let interrupter = {
        let daemon = Arc::clone(&fixture.daemon);
        let interrupts = fixture.interrupts.clone();
        tokio::spawn(async move {
            while !daemon.saw_request("POST /run") {
                tokio::time::sleep(Duration::from_millis(10)).await;
            }
            interrupts.deliver(Signal::Interrupt);
        })
    };
    let (result, _) = run_for_verdict(&fixture, &["--lane", "ui"], None).await;
    interrupter.await.unwrap();
    block.cancel();
    let failure = refusal(result);
    assert_eq!((failure.code.as_ref(), failure.exit), ("run_interrupted", Exit::SOFTWARE));
    let held: RunLease = serde_json::from_value(details(&failure)["lease"].clone()).unwrap();
    assert_eq!(held.disposition, LeaseDisposition::Kept);
    let receipt = held.lease_file.expect("the kept lease names its receipt");
    assert!(
        failure.message.contains(&format!("--lease-file {receipt} lease release")),
        "{:?}",
        failure.message
    );
    assert!(failure.message.starts_with("SIGINT abandoned the run"), "{:?}", failure.message);
    assert!(
        fixture.settings.lease_path(&held.worker).exists(),
        "the worker is still leased so the caller can free it"
    );
}

// The interrupt drops the iteration in flight before its own removal, and the kept lease means no release follows;
// the secret file is removed all the same, before the refusal is answered.
#[tokio::test]
async fn an_interrupted_self_leased_run_still_removes_its_secret() {
    let fixture = self_leased_fixture(passing_run_lines("it-43"), PASSING_XML).await;
    let block = tokio_util::sync::CancellationToken::new();
    fixture.daemon.script().block = Some(block.clone());
    let interrupter = {
        let daemon = Arc::clone(&fixture.daemon);
        let interrupts = fixture.interrupts.clone();
        tokio::spawn(async move {
            while !daemon.saw_request("POST /run") {
                tokio::time::sleep(Duration::from_millis(10)).await;
            }
            interrupts.deliver(Signal::Interrupt);
        })
    };
    fixture.stdin.feed(b"central-login-export-from-a-pipe-0123456789");
    let (result, _) = run_for_verdict(&fixture, &["--lane", "ui", "--test-env", "AIR_LIVE_CENTRAL_LOGIN=@-"], None).await;
    interrupter.await.unwrap();
    block.cancel();
    let failure = refusal(result);
    assert_eq!(failure.code, "run_interrupted");
    let held: RunLease = serde_json::from_value(details(&failure)["lease"].clone()).unwrap();
    assert_eq!(held.disposition, LeaseDisposition::Kept);
    let directory = &fixture.settings.vm_run_secrets;
    let lines = fixture.channel().lines();
    let written = lines
        .iter()
        .position(|line| line.contains(&format!("exec cat > \"$1\" sh {directory}/AIR_LIVE_CENTRAL_LOGIN")))
        .unwrap_or_else(|| panic!("the secret was never written: {lines:#?}"));
    let removed = lines
        .iter()
        .position(|line| line.contains(&format!("/bin/rm -f -- {directory}/AIR_LIVE_CENTRAL_LOGIN")))
        .unwrap_or_else(|| panic!("the interrupt left the secret in the guest: {lines:#?}"));
    assert!(written < removed, "{lines:#?}");
    let removal = fixture
        .channel()
        .options_for_call_containing("/bin/rm -f --")
        .expect("the removal ran");
    assert!(
        removal.survives_interrupt(),
        "the interrupt service would signal the removal at once"
    );
    // No release ran: the lease is kept, so the whole directory is not removed.
    assert!(!fixture.channel().saw_call_containing("/bin/rm -rf --"), "{lines:#?}");
}

// --- a run of every reached lane ---------------------------------------------------------------------------------

// Every reached lane runs on one worker, one after the other, and answers one envelope with a verdict per lane.
#[tokio::test]
async fn a_run_of_several_lanes_answers_one_envelope_with_a_verdict_per_lane() {
    let (fixture, receipt) = run_outcome_fixture(Vec::new(), PASSING_XML).await;
    three_lane_catalog(fixture.root());
    fixture.queue_runs(
        vec![passing_run_lines("it-60"), passing_run_lines("it-61"), passing_run_lines("it-62")],
        &[PASSING_XML, PASSING_XML, PASSING_XML],
    );
    let (result, published) = run_for_verdict(&fixture, &["flow-three-lanes"], Some(&receipt)).await;
    let data = lanes_data(&result.unwrap().data);
    assert_eq!(data.worker, fixture.worker);
    assert!(!data.has_failures);
    assert!(data.not_run.is_empty());
    let lanes: Vec<_> = data
        .lanes
        .iter()
        .map(|lane| {
            (
                lane.lane.as_str(),
                lane.exit_code,
                lane.code.clone(),
                lane.run.as_ref().map(|run| run.iteration_id.as_str()),
            )
        })
        .collect();
    assert_eq!(
        lanes,
        [
            ("ui", 0, None, Some("it-60")),
            ("ui-real", 0, None, Some("it-61")),
            ("gui-chat", 0, None, Some("it-62")),
        ]
    );
    assert_eq!(data.lanes[2].message, "1 test(s) passed");
    // One verdict for the run, with a lane entry for each lane, and the lane's message is its one line.
    let published = published.expect("a verdict is published");
    assert_eq!(published.summary, "3 test(s) passed in 3 lanes");
    assert_eq!(published.lanes.len(), 3);
    assert_eq!(published.lanes[1].lane, "ui-real");
    assert_eq!(published.lanes[1].summary, "1 test(s) passed");
    assert_eq!(
        published.lanes[1].report.as_deref(),
        data.lanes[1].run.as_ref().map(|run| run.report_path.as_str())
    );
    assert_eq!(published.counts.started, 3);
    let last: Value = serde_json::from_slice(&fixture.daemon.script().run_body).unwrap();
    assert!(
        last["junit5Filters"].to_string().contains("include-tag=air-gui-chat"),
        "the last iteration asked for {last}"
    );
}

// The exit is the worst of the lanes, so a red middle lane is still exit 6, and the green lanes are still in the
// details.
#[tokio::test]
async fn a_red_lane_makes_the_whole_run_red() {
    let (fixture, receipt) = run_outcome_fixture(Vec::new(), PASSING_XML).await;
    three_lane_catalog(fixture.root());
    let (red, red_xml) = failing_run("it-64");
    fixture.queue_runs(
        vec![passing_run_lines("it-63"), red, passing_run_lines("it-65")],
        &[PASSING_XML, &red_xml, PASSING_XML],
    );
    let (result, published) = run_for_verdict(&fixture, &["flow-three-lanes"], Some(&receipt)).await;
    let failure = refusal(result);
    assert_eq!((failure.code.as_ref(), failure.exit), ("tests_failed", Exit::TESTS_FAILED));
    let published = published.expect("a verdict is published");
    assert_eq!(published.status, Status::Failed);
    assert_eq!(published.code.as_deref(), Some("tests_failed"));
    assert_eq!(published.summary, failure.message);
    assert_eq!(published.lanes[0].status, Status::Passed);
    let data = lanes_data(&details(&failure));
    assert!(data.has_failures);
    let exits: Vec<_> = data.lanes.iter().map(|lane| lane.exit_code).collect();
    assert_eq!(exits, [0, 6, 0]);
    assert_eq!(data.lanes[1].code.as_deref(), Some("tests_failed"));
    assert_eq!(
        data.lanes[1].run.as_ref().map(|run| run.reproduce.clone()),
        Some(vec!["run com.example.MyTest".to_owned()])
    );
    assert!(
        failure.message.contains("lane ui-real: 1 test(s) and 0 container(s) failed"),
        "{:?}",
        failure.message
    );
}

// An iteration that produced no report stops the run, and the refusal names each lane it did not reach.
#[tokio::test]
async fn an_iteration_with_no_report_stops_the_run() {
    let (fixture, receipt) = run_outcome_fixture(Vec::new(), PASSING_XML).await;
    three_lane_catalog(fixture.root());
    fixture.daemon.script().conflict = Some(json!({"reason": "busy"}));
    let (result, published) = run_for_verdict(&fixture, &["flow-three-lanes"], Some(&receipt)).await;
    // An iteration with no report is no verdict on the tests, so the refusal keeps every line.
    assert_eq!(published, None);
    let failure = refusal(result);
    assert_eq!((failure.code.as_ref(), failure.exit), ("daemon_busy", Exit::TEMP_FAIL));
    let data = lanes_data(&details(&failure));
    assert_eq!(data.lanes.len(), 1);
    assert_eq!(data.lanes[0].run, None);
    let not_run: Vec<_> = data
        .not_run
        .iter()
        .map(|left| (left.lane.as_str(), left.reason.as_str(), left.suites.len()))
        .collect();
    let reason = "not reached: the ui iteration failed with daemon_busy";
    assert_eq!(not_run, [("ui-real", reason, 1), ("gui-chat", reason, 1)]);
    assert!(
        failure.message.contains(&format!("\nnot run: gui-chat (1 suite(s)): {reason}")),
        "{:?}",
        failure.message
    );
    let requests = fixture.daemon.script().requests.join("\n");
    assert_eq!(requests.matches("/run").count(), 1, "the run went on after the refusal: {requests}");
}

// A self-leased run of several lanes holds one lease for every iteration, and gives it back once.
#[tokio::test]
async fn a_self_leased_run_of_several_lanes_holds_one_lease() {
    let fixture = self_leased_fixture(Vec::new(), PASSING_XML).await;
    three_lane_catalog(fixture.root());
    fixture.queue_runs(
        vec![passing_run_lines("it-66"), passing_run_lines("it-67"), passing_run_lines("it-68")],
        &[PASSING_XML, PASSING_XML, PASSING_XML],
    );
    let (result, published) = run_for_verdict(&fixture, &["flow-three-lanes"], None).await;
    let data = lanes_data(&result.unwrap().data);
    assert_eq!(data.lanes.len(), 3);
    let lease = data.lease.expect("the payload names the lease");
    assert_eq!(lease.disposition, LeaseDisposition::Released);
    for lane in &data.lanes {
        assert_eq!(
            lane.run.as_ref().map(|run| run.worker.as_str()),
            Some(lease.worker.as_str()),
            "lane {}",
            lane.lane
        );
    }
    let taken = published.and_then(|verdict| verdict.lease).expect("the verdict names the lease");
    assert!(taken.released());
    assert_eq!(taken.workers, std::slice::from_ref(&lease.worker));
    require_unleased(&fixture);
}

// The records a run publishes carry the kind a reader filters on.
#[tokio::test]
async fn a_run_publishes_its_plan_before_its_iterations() {
    let (fixture, receipt) = run_outcome_fixture(passing_run_lines("it-70"), PASSING_XML).await;
    let recorded = Recorded::start(&fixture.host.reporter);
    fixture
        .host
        .command_run(&Ctx::background(), &run_command_args(&["--lane", "ui"]), Some(&receipt))
        .await
        .unwrap();
    let planned: Vec<RunPlanned> = recorded.data_of(Kind::RunPlanned);
    assert_eq!(planned.len(), 1);
    assert_eq!(planned[0].iterations[0].selection, "lane ui");
    assert_eq!(planned[0].iterations[0].lane, None);
}
