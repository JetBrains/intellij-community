use avl_base::format::words;
use avl_host_sys::Ctx;
use avl_host_testkit::agent::active_reply;
use avl_host_testkit::{answer_guest, outcome_of, refusal, said};
use pretty_assertions::assert_eq;
use serde_json::json;

use super::command_exec;
use crate::lane::observe::testing::Fixture;
use crate::terminal::Output;
use avl_base::RefusalExt;

#[tokio::test]
async fn exec_is_refused_while_a_run_is_active() {
    let fixture = Fixture::docker();
    fixture.mark_ready("air-docker-1").await;
    let receipt = fixture.lease_receipt("air-docker-1");
    fixture
        .channel("air-docker-1")
        .answer(answer_guest(vec![(" active ", active_reply(Some("run-1")))]));
    let refusal = refusal(
        command_exec(
            &Ctx::background(),
            &fixture.manager,
            words(["--", "/bin/echo", "hi"]),
            Some(&receipt),
            Output::Json,
        )
        .await,
    );
    assert_eq!(refusal.code, "run_active", "{}", refusal.message);
    // The operation is named, so the refusal says what was being attempted rather than only what blocked it.
    assert!(
        refusal.message.contains("execute an arbitrary guest command"),
        "{}",
        refusal.message
    );
}

#[tokio::test]
async fn exec_runs_the_command_as_the_worker_user() {
    let fixture = Fixture::docker();
    fixture.mark_ready("air-docker-1").await;
    let receipt = fixture.lease_receipt("air-docker-1");
    let channel = fixture.channel("air-docker-1");
    channel.answer(answer_guest(vec![(" active ", active_reply(None)), ("/bin/echo", said("hi\n"))]));
    let outcome = outcome_of(
        command_exec(
            &Ctx::background(),
            &fixture.manager,
            words(["--", "/bin/echo", "hi"]),
            Some(&receipt),
            Output::Json,
        )
        .await,
    );
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
    let fixture = Fixture::docker();
    fixture.mark_ready("air-docker-1").await;
    let receipt = fixture.lease_receipt("air-docker-1");
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
    let refusal = refusal(
        command_exec(
            &Ctx::background(),
            &fixture.manager,
            words(["/bin/false"]),
            Some(&receipt),
            Output::Json,
        )
        .await,
    );
    assert_eq!((refusal.code.as_ref(), refusal.exit.code()), ("guest_command_failed", 3));
    assert!(!refusal.message.contains("secret"), "{}", refusal.message);
    assert_eq!(refusal.details(), Some(json!({ "exitCode": 3 })));
}
