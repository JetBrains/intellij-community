use std::process::Stdio;
use std::time::Duration;

use pretty_assertions::assert_eq;
use tokio::process::Command;

use super::*;

/// A child that waits for about 30 seconds and starts a child of its own, so the group has two members.
fn sleeper() -> Command {
    let mut command = if cfg!(windows) {
        let mut command = Command::new("cmd.exe");
        command.args(["/d", "/c", "ping -n 30 127.0.0.1 >NUL"]);
        command
    } else {
        let mut command = Command::new("/bin/sh");
        command.args(["-c", "sleep 30; exit 0"]);
        command
    };
    command
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .kill_on_drop(true);
    ProcessGroup::prepare(&mut command);
    command
}

/// Waits up to five seconds for the group to have no member.
async fn emptied(group: &ProcessGroup) -> bool {
    for _ in 0..100 {
        if group.is_empty() {
            return true;
        }
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
    false
}

// A kill ends every member of the group, and the group is then empty. The child reports the status a shell reports
// for SIGKILL, on Windows too.
#[tokio::test]
async fn a_kill_ends_the_whole_group() {
    let mut child = sleeper().spawn().expect("the sleeper starts");
    let group = ProcessGroup::attach(&child)
        .expect("the group takes the child")
        .expect("the child runs");
    assert!(!group.is_empty(), "a group with a running child is empty");

    group.kill();
    let status = tokio::time::timeout(Duration::from_secs(10), child.wait())
        .await
        .expect("the kill did not end the child")
        .unwrap();
    assert_eq!(exit_code(status), 128 + 9);
    assert!(emptied(&group).await, "a member of the group survived the kill");
}

// A child that has ended and was reaped has no group left to reach.
#[tokio::test]
async fn a_reaped_child_has_no_group() {
    let mut command = if cfg!(windows) {
        let mut command = Command::new("cmd.exe");
        command.args(["/d", "/c", "exit 3"]);
        command
    } else {
        let mut command = Command::new("/bin/sh");
        command.args(["-c", "exit 3"]);
        command
    };
    ProcessGroup::prepare(&mut command);
    let mut child = command.spawn().expect("the child starts");
    let status = child.wait().await.unwrap();
    assert_eq!(exit_code(status), 3);
    assert!(ProcessGroup::attach(&child).expect("a reaped child is not an error").is_none());
}
