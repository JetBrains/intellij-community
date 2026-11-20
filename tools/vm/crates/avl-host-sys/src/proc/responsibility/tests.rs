use std::fs;
use std::path::Path;
use std::time::Duration;

use avl_base::format::words;
use pretty_assertions::assert_eq;

use super::*;
use crate::ctx::Ctx;
use crate::interrupt::Interrupts;
use crate::proc::{Runner, SpawnOptions};

/// What the system answers for one child and its child.
struct Answer {
    child: i32,
    grandchild: i32,
    /// The responsible pid of the child and of the grandchild.
    responsible: (Option<i32>, Option<i32>),
}

/// Starts `sh` under `responsibility` with a script that writes its pid and the pid of a `sleep` child, and answers
/// the responsible pid of both. The context then ends the shell.
async fn responsible_of_child(responsibility: Responsibility) -> Answer {
    let directory = tempfile::tempdir().expect("a temporary directory");
    let pid_file = directory.path().join("pid");
    let script = format!(
        "/bin/sleep 30 & echo \"$$ $!\" > '{}.tmp'; mv '{0}.tmp' '{0}'; wait",
        pid_file.display()
    );
    let argv = words(["/bin/sh", "-c", &script]);
    let runner = Runner::new([("PATH".to_owned(), "/usr/bin:/bin".to_owned())], Interrupts::detached());
    let ctx = Ctx::background();
    let child = Ctx::new(ctx.token().child_token());
    let log = directory.path().join("log");
    let options = SpawnOptions {
        responsibility,
        ..SpawnOptions::within(Duration::from_mins(1))
    };
    let run = runner.checked_to_file(&child, &argv, &log, &options);
    tokio::pin!(run);
    let (shell, sleep) = tokio::select! {
        result = &mut run => panic!("the shell ended early: {result:?}"),
        pids = read_pids(&pid_file) => pids,
    };
    let responsible = (responsible_pid(shell), responsible_pid(sleep));
    child.token().cancel();
    let _ = run.await;
    Answer {
        child: shell,
        grandchild: sleep,
        responsible,
    }
}

async fn read_pids(path: &Path) -> (i32, i32) {
    for _ in 0..200 {
        if let Ok(text) = fs::read_to_string(path)
            && let Some((shell, sleep)) = text.trim().split_once(' ')
            && let (Ok(shell), Ok(sleep)) = (shell.parse(), sleep.parse())
        {
            return (shell, sleep);
        }
        tokio::time::sleep(Duration::from_millis(25)).await;
    }
    panic!("the shell never wrote {}", path.display());
}

fn own_pid() -> i32 {
    i32::try_from(std::process::id()).expect("a pid")
}

/// A child that owns its responsibility is the responsible process of itself and of its own child. A child by default
/// names the responsible process of this process. A process that the system has no responsible process for answers
/// its own pid, and so does each child that inherits from it.
#[tokio::test]
async fn a_child_with_its_own_responsibility_is_its_own_responsible_process() {
    let answer = responsible_of_child(Responsibility::Own).await;
    assert_eq!(
        answer.responsible,
        (Some(answer.child), Some(answer.child)),
        "the child {} and its child {}",
        answer.child,
        answer.grandchild
    );
    let ours = responsible_pid(own_pid()).expect("this process has a responsible process");
    let answer = responsible_of_child(Responsibility::Inherited).await;
    let expected = if ours == own_pid() {
        (Some(answer.child), Some(answer.grandchild))
    } else {
        (Some(ours), Some(ours))
    };
    assert_eq!(
        answer.responsible, expected,
        "the child {} and its child {} inherit from this process, whose responsible process is {ours}",
        answer.child, answer.grandchild
    );
}

/// The disclaimed exec keeps what the command set: the argv, the environment, the directory and the exit code.
#[tokio::test]
async fn a_child_with_its_own_responsibility_keeps_its_argv_environment_directory_and_exit_code() {
    let directory = tempfile::tempdir().expect("a temporary directory");
    let runner = Runner::new(
        [
            ("PATH".to_owned(), "/usr/bin:/bin".to_owned()),
            ("MARK".to_owned(), "a b".to_owned()),
        ],
        Interrupts::detached(),
    );
    let options = SpawnOptions {
        dir: Some(directory.path().to_path_buf()),
        responsibility: Responsibility::Own,
        ..SpawnOptions::within(Duration::from_mins(1))
    };
    let argv = words([
        "/bin/sh",
        "-c",
        "printf '%s|%s|%s' \"$MARK\" \"$1\" \"$(pwd -P)\"; exit 3",
        "sh",
        "one",
    ]);
    let captured = runner.capture(&Ctx::background(), &argv, &options).await.expect("a capture");
    let real = fscopy::resolve_links(directory.path()).expect("the real path");
    assert_eq!((captured.exit_code, captured.stdout), (3, format!("a b|one|{}", real.display())));
}

/// A relative program cannot start with its own responsibility, because the exec takes no PATH search.
#[tokio::test]
async fn a_relative_program_with_its_own_responsibility_fails_to_start() {
    let runner = Runner::new([("PATH".to_owned(), "/usr/bin:/bin".to_owned())], Interrupts::detached());
    let options = SpawnOptions {
        responsibility: Responsibility::Own,
        ..SpawnOptions::within(Duration::from_mins(1))
    };
    let failure = runner
        .capture(&Ctx::background(), &words(["sh", "-c", "true"]), &options)
        .await
        .expect_err("a spawn failure");
    assert!(failure.to_string().contains("could not start sh"), "{failure}");
}

#[test]
fn a_pid_without_a_process_has_no_responsible_process() {
    assert_eq!(responsible_pid(0), None);
    assert_eq!(responsible_pid(-1), None);
}
