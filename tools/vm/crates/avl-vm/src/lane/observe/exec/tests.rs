use std::path::Path;

use avl_base::format::words;
use avl_base::{Outcome, Refusal};
use avl_host_sys::Ctx;
use avl_host_testkit::agent::active_reply;
use avl_host_testkit::{answer_guest, outcome_of, refusal, said};
use pretty_assertions::assert_eq;
use serde_json::json;

use super::command_exec;
use crate::lane::observe::testing::Fixture;
use crate::terminal::Output;
use avl_base::RefusalExt;

/// `exec` of `argv` with the receipt of the suite, answered as JSON.
async fn exec(fixture: &Fixture, receipt: &Path, argv: &[&str]) -> Result<Outcome, Refusal> {
    command_exec(&Ctx::background(), &fixture.manager, words(argv), Some(receipt), Output::Json).await
}

#[tokio::test]
async fn exec_is_refused_while_a_run_is_active() {
    let (fixture, receipt) = Fixture::leased_ready_docker().await;
    fixture
        .channel("air-docker-1")
        .answer(answer_guest(vec![(" active ", active_reply(Some("run-1")))]));
    let refusal = refusal(exec(&fixture, &receipt, &["--", "/bin/echo", "hi"]).await);
    assert_eq!(refusal.code, "run_active", "{}", refusal.message);
    // The operation is named, so the refusal says what was being attempted rather than only what blocked it.
    assert!(
        refusal.message.contains("execute an arbitrary guest command"),
        "{}",
        refusal.message
    );
}

/// `exec` acts on the worker, so a run of another live process refuses it as `lease_busy` and names the run. No
/// command reaches the guest.
#[tokio::test]
async fn exec_is_refused_while_a_run_of_another_process_is_in_flight() {
    let (fixture, receipt) = Fixture::leased_ready_docker().await;
    let run = fixture.run_of_another_process("air-docker-1").await;
    let refusal = refusal(exec(&fixture, &receipt, &["--", "/bin/echo", "hi"]).await);
    assert_eq!(refusal.code, "lease_busy", "{}", refusal.message);
    assert!(refusal.message.contains(&format!("has run {run} in flight")), "{}", refusal.message);
    assert_eq!(fixture.channel("air-docker-1").lines(), Vec::<String>::new());
}

#[tokio::test]
async fn exec_runs_the_command_as_the_worker_user() {
    let (fixture, receipt) = Fixture::leased_ready_docker().await;
    let channel = fixture.channel("air-docker-1");
    channel.answer(answer_guest(vec![(" active ", active_reply(None)), ("/bin/echo", said("hi\n"))]));
    let outcome = outcome_of(exec(&fixture, &receipt, &["--", "/bin/echo", "hi"]).await);
    assert_eq!(
        outcome.data,
        json!({
            "worker": "air-docker-1",
            "exitCode": 0,
            "stdout": "hi\n",
            "stderr": "",
            "stdoutTruncated": false,
            "stderrTruncated": false,
        })
    );
    assert!(
        channel.saw_call_containing("/usr/bin/sudo -H -u admin /bin/echo hi"),
        "the command did not run under the worker user's sudo prefix: {:?}",
        channel.lines()
    );
}

/// The guest's failure is the command's own exit, and what the guest printed stays out of the refusal: a guest
/// process can echo the bridge token, and a refusal ends up in an envelope an agent logs.
#[tokio::test]
async fn a_failed_guest_command_keeps_its_exit_and_withholds_its_output() {
    let (fixture, receipt) = Fixture::leased_ready_docker().await;
    fixture.channel("air-docker-1").answer(answer_guest(vec![
        (" active ", active_reply(None)),
        (
            "/bin/false",
            avl_host_sys::Captured {
                exit_code: 3,
                stderr: "token=secret\n".to_owned(),
                ..avl_host_sys::Captured::default()
            },
        ),
    ]));
    let refusal = refusal(exec(&fixture, &receipt, &["/bin/false"]).await);
    assert_eq!((refusal.code.as_ref(), refusal.exit.code()), ("guest_command_failed", 3));
    assert!(!refusal.message.contains("secret"), "{}", refusal.message);
    assert_eq!(refusal.details(), Some(json!({ "exitCode": 3 })));
}
