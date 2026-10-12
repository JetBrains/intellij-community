use avl_base::GuestOs;
use avl_base::format::words;
use avl_wire::supervisor::{ActiveReply, Envelope, EnvelopeError, Outcome, Phase};
use pretty_assertions::assert_eq;
use serde::Serialize;

use super::*;
use crate::guest::testing::{FakeChannel, FakeProbe, Host, failed};

fn envelope(command: &str, data: impl Serialize) -> String {
    serde_json::to_string(&Envelope::success(command, data)).unwrap()
}

fn refused(command: &str, code: &str, message: &str) -> String {
    serde_json::to_string(&Envelope::<()>::failure(
        command,
        EnvelopeError {
            code: code.to_owned(),
            message: message.to_owned(),
            details: serde_json::Value::Null,
        },
    ))
    .unwrap()
}

fn live_run(run_id: &str) -> RunState {
    RunState::new(run_id, Phase::Running)
}

fn slot(active: Option<RunState>) -> String {
    envelope("active", ActiveReply { active })
}

// --- the argv --------------------------------------------------------------------------------------------------

// "aqua" means "where the IDE can open a window", and the two guests have nothing in common about it: macOS has a
// console user's launchd session reachable only through `launchctl asuser`, Linux has an X server the guest already
// runs.
// A macOS guest runs on Tart only, and a Windows host drives only Docker.
#[cfg(unix)]
#[tokio::test]
async fn aqua_argv_differs_per_guest() {
    let aqua = SupervisorOptions {
        aqua: true,
        timeout: Duration::from_mins(1),
    };
    let macos = Host::new(GuestOs::Macos);
    let channel = FakeChannel::spoke("air-macos-1", slot(None));
    let settings = &macos.settings;
    macos
        .guest(&channel)
        .supervisor_active_reply(&settings.vm_runs_root, aqua)
        .await
        .unwrap();
    assert_eq!(
        channel.lines()[0],
        format!(
            "/bin/launchctl asuser {} /usr/bin/sudo -H -u {} /usr/bin/env IJ_PRIVATE_PACKAGES_AUTHORIZER_SKIP=true {} \
             active --root {}",
            settings.vm_uid, settings.vm_user, settings.vm_agent, settings.vm_runs_root
        )
    );

    let linux = Host::new(GuestOs::Linux);
    let channel = FakeChannel::spoke("air-docker-1", slot(None));
    let settings = &linux.settings;
    linux
        .guest(&channel)
        .supervisor_active_reply(&settings.vm_runs_root, aqua)
        .await
        .unwrap();
    // `setsid --wait` is enough to detach it from this exec: any process with DISPLAY set reaches the guest's X
    // server.
    assert_eq!(
        channel.lines()[0],
        format!(
            "/usr/bin/sudo -H -u {} /usr/bin/setsid --wait /usr/bin/env DISPLAY={} /usr/bin/env \
             IJ_PRIVATE_PACKAGES_AUTHORIZER_SKIP=true {} active --root {}",
            settings.vm_user, settings.guest_display, settings.vm_agent, settings.vm_runs_root
        )
    );
}

#[tokio::test]
async fn non_aqua_invocation_runs_as_the_worker_user() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::spoke("air-docker-1", slot(None));
    host.guest(&channel).active_run().await.unwrap();
    let settings = &host.settings;
    assert_eq!(
        channel.lines()[0],
        format!(
            "/usr/bin/sudo -H -u {} /usr/bin/env IJ_PRIVATE_PACKAGES_AUTHORIZER_SKIP=true {} active --root {}",
            settings.vm_user, settings.vm_agent, settings.vm_runs_root
        )
    );
    // A timeout is named for the supervisor.
    host.guest(&channel)
        .supervisor_active_reply(
            &settings.vm_runs_root,
            SupervisorOptions {
                aqua: false,
                timeout: Duration::from_secs(9),
            },
        )
        .await
        .unwrap();
    assert_eq!(channel.calls()[1].options.timeout_code, Some("supervisor_timeout"));
}

// --- the envelope ----------------------------------------------------------------------------------------------

#[tokio::test]
async fn invoke_supervisor_checks_the_echoed_command() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::spoke("air-docker-1", envelope("status", ActiveReply { active: None }));
    let refusal = host
        .guest(&channel)
        .invoke_supervisor(
            Command::Active,
            &words(["--root", &host.settings.vm_runs_root]),
            SupervisorOptions {
                aqua: false,
                timeout: Duration::from_mins(1),
            },
        )
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "supervisor_protocol");
    assert!(refusal.message.contains(r#"answered for "status""#), "{}", refusal.message);
}

#[tokio::test]
async fn invoke_supervisor_refuses_another_schema() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::spoke(
        "air-docker-1",
        r#"{"schemaVersion":2,"ok":true,"command":"active","data":{"active":null}}"#,
    );
    let refusal = host.guest(&channel).active_run().await.unwrap_err();
    assert_eq!(refusal.code, "supervisor_schema_unsupported");
    // The actionable half is not obvious, so the prose says which copy is unlikely to be the stale one.
    assert!(
        refusal.message.contains("installed from the built `vm-guest-agent` binary"),
        "{}",
        refusal.message
    );
    assert_eq!(refusal.exit, Exit::SOFTWARE);
}

// An explicit `null` is read as the field's default, and a bare `null` as an envelope that said nothing: either way
// the agent is judged by what it did say, not refused as invalid JSON.
#[tokio::test]
async fn invoke_supervisor_reads_nulls_as_what_was_left_out() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::answering("air-docker-1", |_| {
        Ok(failed(
            70,
            r#"{"schemaVersion":null,"ok":null,"command":null,"error":{"code":"supervisor_busy","message":null}}"#,
        ))
    });
    let refusal = host.guest(&channel).active_run().await.unwrap_err();
    assert_eq!(
        (refusal.code.as_ref(), refusal.message.as_str()),
        ("supervisor_busy", "guest run supervisor exited 70")
    );
    let channel = FakeChannel::answering("air-docker-1", |_| Ok(failed(70, "null")));
    let refusal = host.guest(&channel).active_run().await.unwrap_err();
    assert_eq!(refusal.code, "supervisor_failed");
}

#[tokio::test]
async fn invoke_supervisor_refuses_something_that_is_not_json() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::answering("air-docker-1", |_| Ok(failed(2, "sudo: a password is required\n")));
    let refusal = host.guest(&channel).active_run().await.unwrap_err();
    assert_eq!(refusal.code, "supervisor_protocol");
    // The guest's own exit code survives, so a caller branching on it is not told the failure was this controller's.
    assert_eq!(refusal.exit, Exit::from_status(2, Exit::FAILURE));
    // And a reply that is not JSON on a command that exited 0 still leaves nonzero.
    let silent = FakeChannel::spoke("air-docker-1", "");
    assert_eq!(host.guest(&silent).active_run().await.unwrap_err().exit, Exit::SOFTWARE);
}

// A failed supervisor call answers a structured refusal on stderr; reporting "exited with 1" instead would throw away
// the code the agent chose.
#[tokio::test]
async fn supervisor_failure_carries_the_agents_own_code() {
    let host = Host::new(GuestOs::Linux);
    let answer = refused("active", "run_state_unreadable", "active.json is truncated");
    let channel = FakeChannel::answering("air-docker-1", move |_| Ok(failed(65, &answer)));
    let refusal = host.guest(&channel).active_run().await.unwrap_err();
    assert_eq!(refusal.code, "run_state_unreadable");
    assert!(refusal.message.contains("active.json is truncated"), "{}", refusal.message);
    assert_eq!(refusal.exit, Exit::DATA_ERR);
    // An envelope that states nothing gets the controller's own sentence.
    let bare = FakeChannel::answering("air-docker-1", |_| {
        Ok(failed(1, r#"{"schemaVersion":1,"ok":false,"command":"active"}"#))
    });
    let refusal = host.guest(&bare).active_run().await.unwrap_err();
    assert_eq!(
        (refusal.code.as_ref(), refusal.message.as_str()),
        ("supervisor_failed", "guest run supervisor exited 1")
    );
}

// --- the agent's own verbs -------------------------------------------------------------------------------------

// The verb's own document on stdout, and the same argv `as_user` built: what was wrong with these verbs was the
// reporting, not the wire.
#[tokio::test]
async fn invoke_agent_keeps_the_argv_and_answers_the_verbs_own_document() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::spoke("air-docker-1", r#"{"complete":true}"#);
    let stdout = host
        .guest(&channel)
        .invoke_agent(
            AgentAccount::Worker,
            AgentVerb::StageCheck,
            &words(["/vm/runtime", "abc123"]),
            &SpawnOptions::within(Duration::from_mins(1)),
        )
        .await
        .unwrap();
    assert_eq!(stdout, r#"{"complete":true}"#);
    assert_eq!(
        channel.lines()[0],
        format!(
            "/usr/bin/sudo -H -u {} {} stage-check /vm/runtime abc123",
            host.settings.vm_user, host.settings.vm_agent
        )
    );
}

// The account is the caller's, and the two prefixes differ in exactly one thing. A `-u` in front of the Linux boot
// verb would run it as the worker.
#[tokio::test]
async fn invoke_agent_runs_a_verb_under_the_account_its_caller_named() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::spoke("air-docker-1", r#"{"ok":true}"#);
    host.guest(&channel)
        .invoke_agent(
            AgentAccount::Root,
            AgentVerb::ValidateGuest,
            &words([":88", "/data"]),
            &SpawnOptions::within(Duration::from_mins(1)),
        )
        .await
        .unwrap();
    assert_eq!(
        channel.lines()[0],
        format!("/usr/bin/sudo -H {} validate-guest :88 /data", host.settings.vm_agent)
    );
}

// A structured refusal on stderr is reported as the code the agent chose, with its message and its exit status.
#[tokio::test]
async fn invoke_agent_reports_the_agents_own_refusal() {
    let host = Host::new(GuestOs::Linux);
    let answer = refused("stage", "stage_manifest_unreadable", "manifest is truncated");
    let channel = FakeChannel::answering("air-docker-1", move |_| Ok(failed(70, &answer)));
    let refusal = host
        .guest(&channel)
        .invoke_agent(
            AgentAccount::Worker,
            AgentVerb::Stage,
            &words(["/vm/runtime", "/vm/data/state/runtime-stage.json"]),
            &SpawnOptions::within(Duration::from_mins(1)),
        )
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "stage_manifest_unreadable");
    assert!(refusal.message.contains("manifest is truncated"), "{}", refusal.message);
    assert_eq!(refusal.exit, Exit::SOFTWARE);
}

// The defect this exists for, measured on air-linux-2 on 2026-08-24: an agent older than its controller answers a
// verb it does not have with its usage text and `EX_USAGE`, and routed through `raw` that read `sudo in air-linux-1
// exited with 64`.
#[tokio::test]
async fn invoke_agent_reports_a_usage_refusal_with_its_first_line() {
    let host = Host::new(GuestOs::Linux);
    let usage = "usage: vm-guest-agent stage <runtime-root> <manifest> | stage-check <runtime-root> <digest>\n";
    let channel = FakeChannel::answering("air-docker-1", move |_| Ok(failed(64, usage)));
    let refusal = host
        .guest(&channel)
        .invoke_agent(
            AgentAccount::Worker,
            AgentVerb::LaunchPrep,
            &words(["/vm/runtime"]),
            &SpawnOptions::within(Duration::from_mins(1)),
        )
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "guest_agent_failed");
    for fragment in [
        "launch-prep",
        "air-docker-1",
        "exited with 64",
        "usage: vm-guest-agent stage",
        "older than this controller",
    ] {
        assert!(refusal.message.contains(fragment), "no {fragment:?}: {}", refusal.message);
    }
    assert!(!refusal.message.contains("sudo"), "{}", refusal.message);
    assert_eq!(refusal.exit, Exit::from_status(64, Exit::FAILURE));
    assert_eq!(refusal.details(), Some(json!({ "exitCode": 64 })));
}

// The Rust agent's usage refusal is an envelope with code `usage` at exit 64, which is how the host tells it from an
// old agent's bare usage block: the code travels, and so does the diagnosis, because a verb the agent does not have
// is still what an agent older than its controller looks like.
#[tokio::test]
async fn a_usage_envelope_from_the_rust_agent_keeps_its_code() {
    let host = Host::new(GuestOs::Linux);
    let answer = refused("launch-prep", "usage", "usage: vm-guest-agent launch-prep <runtime-root>");
    let channel = FakeChannel::answering("air-docker-1", move |_| Ok(failed(64, &answer)));
    let refusal = host
        .guest(&channel)
        .invoke_agent(
            AgentAccount::Worker,
            AgentVerb::LaunchPrep,
            &[],
            &SpawnOptions::within(Duration::from_mins(1)),
        )
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "usage");
    assert_eq!(
        refusal.message,
        "the guest agent's launch-prep in air-docker-1 refused: usage: vm-guest-agent launch-prep <runtime-root>. \
         the installed guest agent does not have this verb or these arguments, which is what an agent older than \
         this controller looks like"
    );
    assert_eq!(refusal.exit, Exit::from_status(64, Exit::FAILURE));
}

// What a staging verb refuses *with* reaches the refusal, because it arrives in the envelope both halves declare -
// and the refusal still names the verb and the worker.
#[tokio::test]
async fn a_staging_envelope_carries_its_message_and_keeps_the_attribution() {
    let host = Host::new(GuestOs::Linux);
    let spoken = "stable classpath source is not a file: /vm/runtime/generations/abc/lib/0001-app.jar";
    let answer = refused("stage", "guest_stage_failed", spoken);
    let channel = FakeChannel::answering("air-docker-1", move |_| Ok(failed(70, &answer)));
    let refusal = host
        .guest(&channel)
        .invoke_agent(
            AgentAccount::Worker,
            AgentVerb::Stage,
            &words(["/vm/runtime"]),
            &SpawnOptions::within(Duration::from_mins(1)),
        )
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "guest_stage_failed");
    for fragment in [spoken, "stage", "air-docker-1"] {
        assert!(refusal.message.contains(fragment), "no {fragment:?}: {}", refusal.message);
    }
    assert_eq!(refusal.exit, Exit::SOFTWARE);
    // An envelope that names a code and no message still says where it happened.
    let silent = r#"{"schemaVersion":1,"ok":false,"command":"gc","error":{"code":"guest_gc_failed"}}"#;
    let quiet = FakeChannel::answering("air-docker-2", move |_| Ok(failed(70, silent)));
    let refusal = host
        .guest(&quiet)
        .invoke_agent(
            AgentAccount::Worker,
            AgentVerb::Gc,
            &words(["/vm/runtime"]),
            &SpawnOptions::within(Duration::from_mins(1)),
        )
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "guest_gc_failed");
    assert_eq!(refusal.message, "the guest agent's gc in air-docker-2 refused: it stated no reason");
}

// The agent's own message is bounded like everything else a refusal carries, and on a character boundary.
#[tokio::test]
async fn an_envelope_message_is_bounded_on_a_rune_boundary() {
    let host = Host::new(GuestOs::Linux);
    // `é` is two bytes and the prefix is odd-length, so the bound falls inside a character.
    let spoken = format!("cannot read x{}", "é".repeat(GUEST_AGENT_MESSAGE_BYTES));
    let answer = refused("stage", "guest_stage_failed", &spoken);
    let channel = FakeChannel::answering("air-docker-1", move |_| Ok(failed(70, &answer)));
    let refusal = host
        .guest(&channel)
        .invoke_agent(
            AgentAccount::Worker,
            AgentVerb::Stage,
            &words(["/vm/runtime"]),
            &SpawnOptions::within(Duration::from_mins(1)),
        )
        .await
        .unwrap_err();
    let message = &refusal.message;
    assert!(message.len() <= GUEST_AGENT_MESSAGE_BYTES + 128, "{} bytes", message.len());
    assert!(!message.contains('\u{FFFD}'));
    // Bounded, not withheld: what the check said is still the first thing a reader sees.
    assert!(message.contains("cannot read x"), "{message}");
}

// The quoting is an exception to the withholding contract and stays a narrow one: what travels is a *usage* line,
// and nothing else on that stream travels at all. A first line says nothing about who wrote it, so the prefix is
// what is tested.
#[tokio::test]
async fn invoke_agent_quotes_a_usage_line_and_withholds_every_other_stderr() {
    let host = Host::new(GuestOs::Linux);
    let cases = [
        (
            "a verb's usage refusal",
            "usage: vm-guest-agent gc <root> [keep-digest...]\nTART_VM_TOKEN=secret\n",
            Some("usage: vm-guest-agent gc"),
        ),
        ("dispatch's usage block", "Usage:\n  vm-guest-agent gc ROOT\n", Some("Usage:")),
        // A verb's own message quoting what a guest subprocess printed.
        ("a verb's own failure message", "JBR extraction failed: tar: broken pipe\n", None),
        ("a bare refusal", "gc refused\nTART_VM_TOKEN=secret\n", None),
        // A usage line that is not the first one is not a usage refusal.
        ("usage behind another line", "gc refused\nusage: vm-guest-agent gc <root>\n", None),
        ("nothing at all", "", None),
    ];
    for (name, stderr, quoted) in cases {
        let channel = FakeChannel::answering("air-docker-1", move |_| {
            Ok(Captured {
                exit_code: 1,
                stdout: "TART_VM_TOKEN=secret".to_owned(),
                stderr: stderr.to_owned(),
                ..Captured::default()
            })
        });
        let refusal = host
            .guest(&channel)
            .invoke_agent(
                AgentAccount::Worker,
                AgentVerb::Gc,
                &words(["/vm/runtime"]),
                &SpawnOptions::within(Duration::from_mins(1)),
            )
            .await
            .unwrap_err();
        assert_eq!(refusal.code, "guest_agent_failed", "{name}");
        let bare = "the guest agent's gc in air-docker-1 exited with 1";
        match quoted {
            // The bare `exited with N`, with nothing of the stream appended. Equality rather than a substring sweep:
            // this is the assertion that fails if the exception widens again.
            None => assert_eq!(refusal.message, bare, "{name}"),
            Some(quoted) => {
                assert!(refusal.message.starts_with(bare), "{name}: {}", refusal.message);
                assert!(refusal.message.contains(quoted), "{name}: {}", refusal.message);
                assert!(!refusal.message.contains("secret"), "{name}: {}", refusal.message);
            }
        }
    }
}

// A verb sudo refused is not the agent failing, and it says so by name - a code of its own, because a verb sudo
// refused never ran and `guest_agent_failed` is a claim about the agent.
#[tokio::test]
async fn invoke_agent_names_a_sudo_refusal_rather_than_blaming_the_agent() {
    let host = Host::new(GuestOs::Linux);
    let cases = [
        ("a password is required", "sudo: a password is required\n", "guest_sudo_refused"),
        (
            "the account may not run this",
            "sudo: admin is not in the sudoers file. This incident will be reported.\n",
            "guest_sudo_refused",
        ),
        // The prefix is what is tested, not the position.
        (
            "a sudo line behind another line",
            "gc refused\nsudo: a password is required\n",
            "guest_agent_failed",
        ),
        (
            "a line that merely mentions sudo",
            "gc could not sudo to the worker account\n",
            "guest_agent_failed",
        ),
    ];
    for (name, stderr, code) in cases {
        let channel = FakeChannel::answering("air-docker-1", move |_| Ok(failed(1, stderr)));
        let refusal = host
            .guest(&channel)
            .invoke_agent(
                AgentAccount::Root,
                AgentVerb::ValidateGuest,
                &words([":88", "/vm/data"]),
                &SpawnOptions::within(Duration::from_mins(1)),
            )
            .await
            .unwrap_err();
        assert_eq!(refusal.code, code, "{name}: {}", refusal.message);
        if code != "guest_sudo_refused" {
            continue;
        }
        // The verb, the worker, sudo's own sentence, and the fact that no repository code installs the policy - the
        // last of which stops a reader looking for the provisioning step that "forgot" it.
        for fragment in ["air-docker-1", "validate-guest", stderr.trim(), "base image"] {
            assert!(refusal.message.contains(fragment), "{name}: no {fragment:?}: {}", refusal.message);
        }
    }
}

// Exit 1 is not EX_USAGE, so the "older than this controller" diagnosis is not asserted onto it.
#[tokio::test]
async fn invoke_agent_does_not_blame_the_agents_age_for_a_plain_failure() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::answering("air-docker-1", |_| Ok(failed(1, "gc refused\n")));
    let refusal = host
        .guest(&channel)
        .invoke_agent(
            AgentAccount::Worker,
            AgentVerb::Gc,
            &words(["/vm/runtime"]),
            &SpawnOptions::within(Duration::from_mins(1)),
        )
        .await
        .unwrap_err();
    assert!(!refusal.message.contains("older than this controller"), "{}", refusal.message);
}

// --- the active slot -------------------------------------------------------------------------------------------

// This wire's null convention is inverted: an explicit `{"active": null}` is the good case, so a *missing* key is the
// protocol failure.
#[tokio::test]
async fn active_run_accepts_an_explicit_null() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::spoke(
        "air-docker-1",
        r#"{"schemaVersion":1,"ok":true,"command":"active","data":{"active":null}}"#,
    );
    assert_eq!(host.guest(&channel).active_run().await.unwrap(), None);
}

#[tokio::test]
async fn active_run_refuses_a_missing_active_field() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::spoke(
        "air-docker-1",
        r#"{"schemaVersion":1,"ok":true,"command":"active","data":{"runId":"run-1"}}"#,
    );
    assert_eq!(host.guest(&channel).active_run().await.unwrap_err().code, "supervisor_protocol");
}

// There is no safe default for a phase: the daemon start decides a daemon died by matching `finished`, so a phase
// neither half knows would read as "still booting" and burn the whole boot budget.
#[tokio::test]
async fn active_run_refuses_an_undeclared_phase() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::spoke(
        "air-docker-1",
        r#"{"schemaVersion":1,"ok":true,"command":"active","data":{"active":{"schemaVersion":1,"runId":"run-1","phase":"booting"}}}"#,
    );
    assert_eq!(host.guest(&channel).active_run().await.unwrap_err().code, "supervisor_protocol");
}

// An undeclared *outcome* is kept rather than refused: nothing branches on it, it is only quoted into a message, and
// refusing it would stop a run from reporting a failure this half predates.
#[tokio::test]
async fn active_run_keeps_an_undeclared_outcome() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::spoke(
        "air-docker-1",
        r#"{"schemaVersion":1,"ok":true,"command":"active","data":{"active":{"schemaVersion":1,"runId":"run-1","phase":"finished","outcome":"evaporated"}}}"#,
    );
    let active = host.guest(&channel).active_run().await.unwrap().unwrap();
    assert_eq!(active.outcome, Some(Outcome::Unknown("evaporated".to_owned())));
}

// The check that catches an agent answering about the previous run in the slot.
#[tokio::test]
async fn supervisor_run_state_reply_compares_the_identity() {
    let host = Host::new(GuestOs::Linux);
    let other = FakeChannel::spoke("air-docker-1", envelope("status", live_run("run-other")));
    let refusal = host
        .guest(&other)
        .supervisor_run_state_reply(
            Command::Status,
            &words(["--root", &host.settings.vm_runs_root, "--run", "run-asked"]),
            Some("run-asked"),
            SupervisorOptions {
                aqua: false,
                timeout: Duration::from_mins(1),
            },
        )
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "supervisor_protocol");
    let asked = FakeChannel::spoke("air-docker-1", envelope("status", live_run("run-asked")));
    let state = host
        .guest(&asked)
        .supervisor_run_state_reply(
            Command::Status,
            &words(["--run", "run-asked"]),
            Some("run-asked"),
            SupervisorOptions {
                aqua: false,
                timeout: Duration::from_mins(1),
            },
        )
        .await
        .unwrap();
    assert_eq!(state.phase, Phase::Running);
}

// A renamed field must be a refusal rather than an empty string: a log reply whose `content` silently became "" reads
// as a run that printed nothing.
#[tokio::test]
async fn supervisor_log_reply_refuses_a_missing_field() {
    let host = Host::new(GuestOs::Linux);
    let args = words(["--run", "run-1"]);
    let partial = FakeChannel::spoke(
        "air-docker-1",
        envelope("log", json!({ "runId": "run-1", "logPath": "/tmp/run.log", "content": "hello" })),
    );
    let refusal = host
        .guest(&partial)
        .supervisor_log_reply(
            &args,
            SupervisorOptions {
                aqua: false,
                timeout: Duration::from_mins(1),
            },
        )
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "supervisor_protocol");
    assert!(refusal.message.contains("truncated"), "{}", refusal.message);
    let complete = FakeChannel::spoke(
        "air-docker-1",
        envelope(
            "log",
            LogReply {
                run_id: "run-1".to_owned(),
                log_path: "/tmp/run.log".to_owned(),
                content: "hello".to_owned(),
                truncated: true,
            },
        ),
    );
    let reply = host
        .guest(&complete)
        .supervisor_log_reply(
            &args,
            SupervisorOptions {
                aqua: false,
                timeout: Duration::from_mins(1),
            },
        )
        .await
        .unwrap();
    assert!(reply.truncated);
    assert_eq!(reply.content, "hello");
}

#[tokio::test]
async fn reject_active_run_names_the_run_holding_the_slot() {
    let host = Host::new(GuestOs::Linux);
    let busy = FakeChannel::spoke("air-docker-1", slot(Some(live_run("run-abc"))));
    let refusal = host.guest(&busy).reject_active_run("restart the daemon").await.unwrap_err();
    assert_eq!(refusal.code, "run_active");
    assert!(
        refusal.message.contains("run-abc") && refusal.message.contains("restart the daemon"),
        "{}",
        refusal.message
    );
    let free = FakeChannel::spoke("air-docker-1", slot(None));
    host.guest(&free).reject_active_run("restart the daemon").await.unwrap();
}

// --- a parked daemon in the slot -------------------------------------------------------------------------------

fn daemon_probe(parked: Option<&str>, recorded: Option<&str>) -> FakeProbe {
    FakeProbe {
        parked: parked.map(str::to_owned),
        recorded: recorded.map(str::to_owned),
        refusal: None,
    }
}

// The contract the warm daemon depends on: the daemon holds the run slot for its whole life, so a check on the slot
// alone can never free a worker that has one. A holder this controller can show to be its own idle daemon is not an
// execution.
#[tokio::test]
async fn a_parked_daemon_is_not_an_executing_run() {
    let host = Host::new(GuestOs::Linux);
    let daemon = daemon_probe(Some("run-daemon"), Some("run-daemon"));
    let proves_nothing = FakeProbe::default();
    type Case<'a> = (&'a str, Option<RunState>, &'a dyn ParkedDaemonProbe, RunSlot);
    let cases: [Case<'_>; 4] = [
        ("an empty slot", None, &daemon, RunSlot::Free),
        (
            "this controller's parked daemon",
            Some(live_run("run-daemon")),
            &daemon,
            RunSlot::ParkedDaemon(live_run("run-daemon")),
        ),
        (
            "a run that is not the parked daemon",
            Some(live_run("run-other")),
            &daemon,
            RunSlot::Executing(live_run("run-other")),
        ),
        // A probe that names no parked daemon gives no grounds to call a holder idle.
        (
            "a probe that proves nothing",
            Some(live_run("run-daemon")),
            &proves_nothing,
            RunSlot::Executing(live_run("run-daemon")),
        ),
    ];
    for (name, held, probe, want) in cases {
        let channel = FakeChannel::spoke("air-docker-1", slot(held));
        let guest = host.guest(&channel);
        assert_eq!(guest.judge_run_slot(probe).await.unwrap(), want, "{name}");
        let released = guest.reject_executing_run(probe, "release the lease").await;
        match want {
            RunSlot::Executing(_) => assert_eq!(released.unwrap_err().code, "run_active", "{name}"),
            _ => released.unwrap_or_else(|refusal| panic!("{name} refused a release: {refusal:?}")),
        }
    }
}

// A daemon that does not answer is not provably parked, so it refuses - and the refusal says which of the two things
// it found, because the old one named a finished daemon run and called it active.
#[tokio::test]
async fn an_unreachable_daemon_is_judged_executing() {
    let host = Host::new(GuestOs::Linux);
    let channel = FakeChannel::spoke("air-docker-1", slot(Some(live_run("run-daemon"))));
    let guest = host.guest(&channel);

    // The record names the run in the slot, and the daemon behind it says nothing: a daemon to retire, not work to
    // wait for.
    let silent = daemon_probe(None, Some("run-daemon"));
    let refusal = guest.reject_executing_run(&silent, "release the lease").await.unwrap_err();
    assert_eq!(refusal.code, "run_active");
    assert!(
        refusal.message.contains("run-daemon") && refusal.message.contains("daemon stop"),
        "{}",
        refusal.message
    );

    // Nothing recorded, so the run in the slot is somebody's iteration and the refusal says so.
    let foreign = FakeProbe::default();
    let refusal = guest.reject_executing_run(&foreign, "release the lease").await.unwrap_err();
    assert!(
        refusal.message.contains("in flight") && !refusal.message.contains("daemon stop"),
        "{}",
        refusal.message
    );

    // A daemon run that no record names is a failed start's or a dead controller's: still refused, and the refusal
    // names the command that retires it.
    let orphan_channel = FakeChannel::spoke("air-docker-1", slot(Some(live_run("run-ui-daemon-1"))));
    let refusal = host
        .guest(&orphan_channel)
        .reject_executing_run(&foreign, "release the lease")
        .await
        .unwrap_err();
    assert_eq!(refusal.code, "run_active");
    assert!(
        refusal.message.contains("run-ui-daemon-1")
            && refusal.message.contains("no daemon record")
            && refusal.message.contains("daemon stop"),
        "{}",
        refusal.message
    );

    // A probe that fails is not an answer of "idle" either.
    let failing = FakeProbe {
        refusal: Some(Refusal::new("daemon_protocol", Exit::SOFTWARE, "the daemon speaks version 9")),
        ..FakeProbe::default()
    };
    assert_eq!(
        guest.reject_executing_run(&failing, "release the lease").await.unwrap_err().code,
        "daemon_protocol"
    );
}

// Every status the guest agent can leave with is one this controller names. A refusal's status stays the guest's own,
// so whatever a guest picks is what `vm` leaves with and what a caller branches on. The 71 and 72 are the numbers the
// retired `validate-guest.sh` spent on two of its check classes, and this is the check they fail; why a check class
// must not be a status is ADR 0108.
#[test]
fn every_status_the_guest_agent_leaves_with_is_one_the_controller_names() {
    let named: Vec<(i32, &str)> = vec![
        (Exit::FAILURE.into(), "FAILURE"),
        (Exit::USAGE.into(), "USAGE"),
        (Exit::DATA_ERR.into(), "DATA_ERR"),
        (Exit::NO_INPUT.into(), "NO_INPUT"),
        (Exit::UNAVAILABLE.into(), "UNAVAILABLE"),
        (Exit::SOFTWARE.into(), "SOFTWARE"),
        (Exit::CANT_CREATE.into(), "CANT_CREATE"),
        (Exit::TEMP_FAIL.into(), "TEMP_FAIL"),
        (Exit::NO_PERM.into(), "NO_PERM"),
        // The one status the controller does not answer with and does read: the agent's own `EX_USAGE`, whose number
        // carries the "older than its controller" diagnosis.
        (GUEST_AGENT_USAGE_EXIT, "GUEST_AGENT_USAGE_EXIT"),
    ];
    let name_of = |status: i32| named.iter().find(|(code, _)| *code == status).map(|(_, name)| *name);
    for status in AgentExit::ALL {
        assert!(
            name_of(status.code()).is_some(),
            "a guest verb can exit {} and no name in this controller says what that means; either name it or have \
             the verb refuse with {} and say which check failed in its message",
            status.code(),
            AgentExit::Refused.code()
        );
    }
    // And the two the Linux guest script invented are still unnamed, which is what makes the assertion above load
    // bearing rather than a tautology over a set that happens to fit.
    for invented in [71, 72] {
        assert_eq!(name_of(invented), None, "{invented} is named");
    }
}
