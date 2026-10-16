use std::sync::Arc;
use std::time::{Duration, Instant};

use pretty_assertions::assert_eq;

use super::*;
use crate::proc::{Captured, ProbeOutput, ProcessTable, probe_unanswered};
use crate::testing::runner;

fn own_pid() -> i32 {
    i32::try_from(std::process::id()).unwrap()
}

/// A table of two pids: 7 is no process, and no probe of 8 gives an answer.
struct Table;

impl ProcessTable for Table {
    fn holds(&self, pid: i32) -> bool {
        matches!(pid, 7 | 8)
    }

    fn field(&self, pid: i32, _field: PsField) -> Result<Option<String>, Refusal> {
        if pid == 8 {
            let argv = ["/bin/ps", "-p", "8"].map(str::to_owned);
            let killed = Captured {
                exit_code: 137,
                ..Captured::default()
            };
            return Err(probe_unanswered(&argv, &killed, ProbeOutput::Quoted));
        }
        Ok(None)
    }
}

// This process has an identity of three fields, and it is alive while the process table answers all three.
#[tokio::test]
async fn a_live_process_is_alive_while_all_three_fields_agree() {
    let ctx = Ctx::background();
    let runner = runner();
    let own = runner
        .identify(&ctx, own_pid())
        .await
        .unwrap()
        .expect("this process is in the process table");
    assert_eq!(own.pid, own_pid());
    assert!(!own.process_start.is_empty() && !own.process_command.is_empty(), "{own:?}");
    // `lstart` pads the day of the month, so the recorded form is collapsed, or an identity never matches itself.
    assert!(!own.process_start.contains("  "), "{own:?}");
    assert!(runner.identity_alive(&ctx, &own).await.unwrap());
    // Each field alone disqualifies the identity, because the host gives the pid of an ended process to a new one.
    for other in [
        ProcessIdentity {
            process_start: "Sat Jan 1 00:00:00 2000".to_owned(),
            ..own.clone()
        },
        ProcessIdentity {
            process_command: "/usr/bin/another-program".to_owned(),
            ..own.clone()
        },
    ] {
        assert!(!runner.identity_alive(&ctx, &other).await.unwrap(), "{other:?}");
    }
}

// A pid without a process has no identity, and an identity recorded for it is dead.
#[tokio::test]
async fn a_pid_without_a_process_has_no_identity_and_is_dead() {
    let ctx = Ctx::background();
    let runner = runner().with_process_table(Arc::new(Table));
    for pid in [0, 7, 0x7FFF_FFF0] {
        assert_eq!(runner.identify(&ctx, pid).await, Ok(None), "pid {pid}");
        let recorded = ProcessIdentity {
            pid,
            process_start: "Sun Aug 23 00:00:00 2026".to_owned(),
            process_command: "vm run".to_owned(),
        };
        assert!(!runner.identity_alive(&ctx, &recorded).await.unwrap(), "pid {pid}");
    }
}

// The process table answers for a zombie, with a stable start time and command, so its identity matches it for ever.
// The identity of a zombie must still read as dead.
#[tokio::test]
async fn a_zombie_is_dead_although_the_process_table_answers_for_it() {
    let ctx = Ctx::background();
    let runner = runner();
    let child = std::process::Command::new("/bin/sh").args(["-c", "exit 0"]).spawn().unwrap();
    let pid = i32::try_from(child.id()).unwrap();
    // Never reaped: the zombie leaves the process table with this test process.
    drop(child);
    let deadline = Instant::now() + Duration::from_secs(10);
    while !runner
        .probe_process(&ctx, pid, PsField::State)
        .await
        .unwrap()
        .is_some_and(|state| state.starts_with('Z'))
    {
        assert!(Instant::now() < deadline, "the child never became a zombie");
        tokio::time::sleep(Duration::from_millis(10)).await;
    }
    let zombie = runner
        .identify(&ctx, pid)
        .await
        .unwrap()
        .expect("the process table answers for a zombie");
    assert!(!runner.identity_alive(&ctx, &zombie).await.unwrap(), "{zombie:?}");
}

// A probe without an answer says nothing about the process, so it is a refusal and never a dead process. A dead
// process is the answer that lets a caller remove the record of a live one.
#[tokio::test]
async fn a_probe_without_an_answer_stays_a_refusal() {
    let ctx = Ctx::background();
    let runner = runner().with_process_table(Arc::new(Table));
    assert_eq!(runner.identify(&ctx, 8).await.unwrap_err().code, "probe_unanswered");
    let recorded = ProcessIdentity {
        pid: 8,
        process_start: "Sun Aug 23 00:00:00 2026".to_owned(),
        process_command: "vm run".to_owned(),
    };
    assert_eq!(runner.identity_alive(&ctx, &recorded).await.unwrap_err().code, "probe_unanswered");
}

// The three keys of the document, in their order: the pid receipt, the lock record and the run marker carry them.
#[test]
fn an_identity_is_three_keys() {
    let identity = ProcessIdentity {
        pid: 42,
        process_start: "Sun Aug 23 00:00:00 2026".to_owned(),
        process_command: "vm run".to_owned(),
    };
    let written = serde_json::to_string(&identity).unwrap();
    assert_eq!(
        written,
        r#"{"pid":42,"processStart":"Sun Aug 23 00:00:00 2026","processCommand":"vm run"}"#
    );
    assert_eq!(serde_json::from_str::<ProcessIdentity>(&written).unwrap(), identity);
}
