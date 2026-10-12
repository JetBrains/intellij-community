use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::time::{Duration, Instant};

use avl_base::format::words;
use avl_base::phase::Timeline;
use pretty_assertions::assert_eq;
use tokio_util::sync::CancellationToken;

use super::*;
use crate::interrupt::Signal;
use crate::testing::runner;

fn sh(script: &str) -> Vec<String> {
    words(["/bin/sh", "-c", script])
}

async fn capture(runner: &Runner, argv: &[String]) -> Result<Captured, ProcError> {
    runner
        .capture(&Ctx::background(), argv, &SpawnOptions::within(Duration::from_mins(1)))
        .await
}

/// Whether a process exists, zombies aside: `kill(pid, 0)` answers for a zombie too, so `ps` decides.
async fn alive(pid: i32) -> bool {
    runner()
        .probe_process(&Ctx::background(), pid, PsField::State)
        .await
        .expect("ps answers")
        .is_some_and(|state| !state.starts_with('Z'))
}

/// Waits up to five seconds for a process to be gone.
async fn gone(pid: i32) -> bool {
    for _ in 0..100 {
        if !alive(pid).await {
            return true;
        }
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
    false
}

/// Waits up to five seconds for a child to write its grandchild's pid.
async fn read_pid(path: &Path) -> i32 {
    for _ in 0..100 {
        if let Ok(text) = fs::read_to_string(path)
            && let Ok(pid) = text.trim().parse()
        {
            return pid;
        }
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
    panic!("the child never wrote {}", path.display());
}

#[tokio::test]
async fn capture_answers_what_the_command_said() {
    let captured = capture(&runner(), &sh("printf out; printf err >&2; exit 3"))
        .await
        .expect("a non-zero exit is an answer, not an error");
    assert_eq!(captured.exit_code, 3);
    assert_eq!(captured.stdout, "out");
    assert_eq!(captured.stderr, "err");
}

#[tokio::test]
async fn an_empty_argv_is_refused() {
    let refusal = capture(&runner(), &[]).await.map_err(Refusal::from).unwrap_err();
    assert_eq!(refusal.code, "internal_error");
}

// An empty executable is a host-side Tart command built before the gate resolved the binary. It is a coded refusal,
// not an internal error, and it withholds the argv, which on a guest line can carry the UI-test bridge token.
#[tokio::test]
async fn an_argv_with_no_executable_is_hypervisor_unresolved_and_withholds_the_argv() {
    let refusal = capture(&runner(), &words(["", "exec", "w", "secret-token"]))
        .await
        .map_err(Refusal::from)
        .unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("hypervisor_unresolved", Exit::SOFTWARE));
    assert!(
        refusal.message.contains("`exec`") && !refusal.message.contains("secret-token"),
        "{}",
        refusal.message
    );
}

#[tokio::test]
async fn a_missing_binary_is_unavailable_rather_than_failed() {
    let refusal = capture(&runner(), &words(["/nonexistent/tart"]))
        .await
        .map_err(Refusal::from)
        .unwrap_err();
    assert_eq!(refusal.code, "spawn_failed");
    assert_eq!(refusal.exit, Exit::UNAVAILABLE);
}

/// A caller branches on the case of a failure, never on its code. Each case carries the refusal with the code that
/// the command answers.
#[tokio::test]
async fn each_failure_of_a_command_is_its_own_case() {
    let ctx = Ctx::background();
    let options = SpawnOptions::within(Duration::from_mins(1));
    let exited = runner().checked(&ctx, &sh("exit 4"), &options).await.unwrap_err();
    assert!(
        matches!(&exited, ProcError::Exited { exit_code: 4, refusal } if refusal.code == "subprocess_failed"),
        "{exited:?}"
    );
    let missing = runner().capture(&ctx, &words(["/nonexistent/tool"]), &options).await.unwrap_err();
    assert!(
        matches!(&missing, ProcError::SpawnFailed(refusal) if refusal.code == "spawn_failed"),
        "{missing:?}"
    );
    let directory = tempfile::tempdir().unwrap();
    let taken = directory.path().join("taken");
    fs::write(&taken, b"").unwrap();
    let unusable = runner().checked_to_file(&ctx, &sh("true"), &taken, &options).await.unwrap_err();
    assert!(
        matches!(&unusable, ProcError::DestinationUnusable(refusal) if refusal.code == "capture_destination_exists"),
        "{unusable:?}"
    );
    let quiet = runner()
        .checked_to_file(&ctx, &sh("exit 3"), &directory.path().join("out"), &options)
        .await
        .unwrap_err();
    assert!(matches!(quiet, ProcError::Exited { exit_code: 3, .. }), "{quiet:?}");
    assert_eq!(Refusal::from(quiet).code, "subprocess_failed");
}

// A host command fails with what it said, because withholding it turned every backend failure into "prlctl exited
// with 1" and nothing else. The details are part of the agent envelope contract.
#[tokio::test]
async fn checked_reports_what_the_command_said() {
    let ctx = Ctx::background();
    let options = SpawnOptions::within(Duration::from_mins(1));
    let command = sh("echo 'VM air-macos-1 is not running' >&2; exit 4");
    let refusal = runner().checked(&ctx, &command, &options).await.map_err(Refusal::from).unwrap_err();
    assert_eq!(refusal.code, "subprocess_failed");
    assert_eq!(refusal.exit, Exit::from_status(4, Exit::FAILURE));
    assert!(refusal.message.contains("VM air-macos-1 is not running"), "{}", refusal.message);
    assert_eq!(
        refusal.details(),
        Some(json!({
            "exitCode": 4,
            "command": command,
            "output": "VM air-macos-1 is not running",
        }))
    );

    // Stdout is used when stderr said nothing, because some of these tools report on the wrong stream.
    let refusal = runner()
        .checked(&ctx, &sh("echo said-on-stdout; exit 1"), &options)
        .await
        .map_err(Refusal::from)
        .unwrap_err();
    assert!(refusal.message.contains("said-on-stdout"), "{}", refusal.message);

    // A command that printed nothing says so, rather than trailing a bare colon.
    let refusal = runner()
        .checked(&ctx, &sh("exit 1"), &options)
        .await
        .map_err(Refusal::from)
        .unwrap_err();
    assert!(refusal.message.contains("it printed nothing"), "{}", refusal.message);

    // The quoted output is its newest bytes, bounded.
    let refusal = runner()
        .checked(&ctx, &sh("printf 'x%.0s' $(seq 1 3000) >&2; printf END >&2; exit 1"), &options)
        .await
        .map_err(Refusal::from)
        .unwrap_err();
    let output = refusal.details().as_ref().unwrap()["output"].as_str().unwrap().to_owned();
    assert!(output.starts_with('…') && output.ends_with("END"), "{output}");
    assert_eq!(output.len(), '…'.len_utf8() + FAILURE_OUTPUT_TAIL_BYTES);
}

// A guest command's output stays out of the refusal, because a guest process can echo the UI-test bridge token and
// the refusal ends up in an envelope an agent reads and may log.
#[tokio::test]
async fn checked_quietly_withholds_guest_output() {
    let secret = "AIR-BRIDGE-TOKEN-8f3a";
    let refusal = runner()
        .checked_quietly(
            &Ctx::background(),
            &sh(&format!("echo {secret}; echo {secret} >&2; exit 1")),
            &SpawnOptions::within(Duration::from_mins(1)),
        )
        .await
        .map_err(Refusal::from)
        .unwrap_err();
    assert_eq!(refusal.code, "subprocess_failed");
    let rendered = format!("{} {:?}", refusal.message, refusal.details());
    assert!(!rendered.contains(secret), "the refusal leaked guest output: {rendered}");
    assert!(refusal.message.contains("withheld"), "{}", refusal.message);
    assert_eq!(refusal.details(), Some(json!({ "exitCode": 1 })));
}

// A spawn in a session of its own answers at once, before its child has done anything, and the child's output lands
// in the files it was given. It is a host child like any other, so the bridge credentials do not reach it either.
#[test]
fn a_session_spawn_does_not_wait_and_is_scrubbed_too() {
    let runner = Runner::new(
        [("TART_VM_TOKEN", "a-live-token"), ("AIR_VM_DATA", "/kept")].map(|(name, value)| (name.to_owned(), value.to_owned())),
        Interrupts::detached(),
    );
    let directory = tempfile::tempdir().expect("a directory");
    let log_path = directory.path().join("detached.log");
    let log = File::create(&log_path).expect("the log");
    let started = Instant::now();
    let mut child = runner
        .spawn_session(
            &["/bin/sh", "-c", "sleep 1; /usr/bin/env; echo done"],
            Some(directory.path()),
            log.try_clone().expect("the log is shared"),
            log,
        )
        .expect("the child starts");
    let elapsed = started.elapsed();
    assert!(elapsed < Duration::from_millis(900), "the spawn waited {elapsed:?} for its child");
    let deadline = Instant::now() + Duration::from_secs(10);
    let mut said = String::new();
    while Instant::now() < deadline && !said.contains("done") {
        std::thread::sleep(Duration::from_millis(50));
        said = fs::read_to_string(&log_path).unwrap_or_default();
    }
    assert!(said.contains("AIR_VM_DATA=/kept"), "{said:?}");
    assert!(!said.contains("a-live-token"), "a bridge credential was inherited:\n{said}");
    child.wait().expect("the child is reaped");
}

// The two variables that tell a guest process which worker it is and how to authenticate must not reach a host
// child, not even through an override.
#[tokio::test]
async fn the_bridge_credentials_are_never_inherited() {
    let scrubbed = Runner::new(
        [
            ("PATH", std::env::var("PATH").unwrap_or_default().as_str()),
            ("TART_VM_TOKEN", "a-live-token"),
            ("TART_VM_WORKER", "air-linux-1"),
            ("AIR_VM_DATA", "/kept"),
        ]
        .map(|(name, value)| (name.to_owned(), value.to_owned())),
        Interrupts::detached(),
    );
    let env = capture(&scrubbed, &words(["/usr/bin/env"])).await.unwrap().stdout;
    assert!(
        !env.contains("TART_VM_TOKEN") && !env.contains("TART_VM_WORKER"),
        "a bridge credential was inherited:\n{env}"
    );
    assert!(env.contains("AIR_VM_DATA=/kept"), "an ordinary variable was dropped:\n{env}");

    let restored = scrubbed.with_overrides(&[("TART_VM_TOKEN", "sneaked-back")]);
    let env = capture(&restored, &words(["/usr/bin/env"])).await.unwrap().stdout;
    assert!(!env.contains("sneaked-back"), "an override reintroduced a credential:\n{env}");
}

#[tokio::test]
async fn overrides_reach_the_child() {
    let base = runner().with_overrides(&[("AIR_TEST_MARKER", "first")]);
    let overridden = base.with_overrides(&[("AIR_TEST_MARKER", "present")]);
    let env = capture(&overridden, &words(["/usr/bin/env"])).await.unwrap().stdout;
    assert!(env.contains("AIR_TEST_MARKER=present"), "{env}");
    assert!(!env.contains("AIR_TEST_MARKER=first"), "{env}");
}

#[tokio::test]
async fn stdin_is_delivered() {
    let captured = runner()
        .capture(
            &Ctx::background(),
            &words(["/bin/cat"]),
            &SpawnOptions {
                stdin: Some(b"a manifest".to_vec()),
                ..SpawnOptions::within(Duration::from_mins(1))
            },
        )
        .await
        .unwrap();
    assert_eq!(captured.stdout, "a manifest");
}

// The timeout kills the whole process group, so a `tart exec` whose ssh child holds the guest command does not
// leave the guest running with the run slot taken.
#[tokio::test]
async fn a_timeout_kills_the_group_and_names_its_code() {
    let directory = tempfile::tempdir().unwrap();
    let pid_file = directory.path().join("grandchild.pid");
    let started = Instant::now();
    let error = runner()
        .capture(
            &Ctx::background(),
            &sh(&format!("sleep 30 & echo $! > {}; wait", pid_file.display())),
            &SpawnOptions::timeout(Duration::from_millis(300), "guest_exec_timeout"),
        )
        .await
        .unwrap_err();
    // The case is typed, so a caller branches on it and never on the code.
    assert!(
        matches!(error, ProcError::TimedOut { timeout, .. } if timeout == Duration::from_millis(300)),
        "{error:?}"
    );
    let refusal = Refusal::from(error);
    assert_eq!(refusal.code, "guest_exec_timeout");
    assert_eq!(refusal.exit, Exit::TEMP_FAIL);
    assert_eq!(refusal.details(), Some(json!({ "timeoutMs": 300 })));
    assert!(started.elapsed() < Duration::from_secs(10), "the group was not killed");
    let grandchild = read_pid(&pid_file).await;
    assert!(gone(grandchild).await, "the grandchild {grandchild} survived the timeout");

    // Without a code of its own the timeout is the generic one.
    let refusal = runner()
        .capture(
            &Ctx::background(),
            &sh("sleep 30"),
            &SpawnOptions::within(Duration::from_millis(100)),
        )
        .await
        .map_err(Refusal::from)
        .unwrap_err();
    assert_eq!(refusal.code, "subprocess_timeout");
}

// A cancelled operation is the cooperative abort the controller was missing: `shard` synthesised entries on SIGINT
// because an iteration had no way to be told to stop.
#[tokio::test]
async fn a_cancelled_operation_stops_the_child() {
    let directory = tempfile::tempdir().unwrap();
    let pid_file = directory.path().join("grandchild.pid");
    let token = CancellationToken::new();
    let ctx = Ctx::new(token.clone());
    let cancel = tokio::spawn(async move {
        tokio::time::sleep(Duration::from_millis(200)).await;
        token.cancel();
    });
    let started = Instant::now();
    let captured = runner()
        .capture(
            &ctx,
            &sh(&format!("sleep 30 & echo $! > {}; wait", pid_file.display())),
            &SpawnOptions::within(Duration::from_mins(1)),
        )
        .await
        .expect("a cancelled child is still answered");
    cancel.await.unwrap();
    assert!(started.elapsed() < Duration::from_secs(10));
    // SIGTERM, as a shell reports it.
    assert_eq!(captured.exit_code, 128 + libc::SIGTERM);
    let grandchild = read_pid(&pid_file).await;
    assert!(gone(grandchild).await, "the grandchild {grandchild} survived");
}

// A spawn whose future is dropped - an abandoned operation - takes its whole group with it.
#[tokio::test]
async fn dropping_a_spawn_signals_its_group() {
    let directory = tempfile::tempdir().unwrap();
    let pid_file = directory.path().join("grandchild.pid");
    let runner = runner();
    let command = sh(&format!("sleep 30 & echo $! > {}; wait", pid_file.display()));
    let abandoned = tokio::time::timeout(
        Duration::from_millis(500),
        runner.capture(&Ctx::background(), &command, &SpawnOptions::within(Duration::from_mins(1))),
    )
    .await;
    assert!(abandoned.is_err(), "the spawn finished on its own: {abandoned:?}");
    let grandchild = read_pid(&pid_file).await;
    assert!(gone(grandchild).await, "the grandchild {grandchild} outlived its spawn");
}

/// A shell that leaves a background `sleep` holding its stdout and exits at once: the leader is reaped long before
/// the output ends.
fn leader_exits_first(pid_file: &Path) -> Vec<String> {
    sh(&format!("sleep 30 & echo $! > {}; echo hi", pid_file.display()))
}

// The timeout, the cancellation and the drop guard stay armed until the pipes are drained, not only until the leader
// is reaped; otherwise a descendant holding stdout turns a one-second timeout into a thirty-second clean exit.
#[tokio::test]
async fn a_timeout_still_fires_after_the_leader_exited() {
    let directory = tempfile::tempdir().unwrap();
    let pid_file = directory.path().join("grandchild.pid");
    let started = Instant::now();
    let refusal = runner()
        .capture(
            &Ctx::background(),
            &leader_exits_first(&pid_file),
            &SpawnOptions::within(Duration::from_millis(500)),
        )
        .await
        .map_err(Refusal::from)
        .unwrap_err();
    assert_eq!(refusal.code, "subprocess_timeout");
    assert!(started.elapsed() < Duration::from_secs(10));
    let grandchild = read_pid(&pid_file).await;
    assert!(gone(grandchild).await, "the grandchild {grandchild} survived the timeout");
}

#[tokio::test]
async fn a_cancellation_still_reaches_the_group_after_the_leader_exited() {
    let directory = tempfile::tempdir().unwrap();
    let pid_file = directory.path().join("grandchild.pid");
    let token = CancellationToken::new();
    let ctx = Ctx::new(token.clone());
    let cancel = tokio::spawn(async move {
        tokio::time::sleep(Duration::from_millis(300)).await;
        token.cancel();
    });
    let started = Instant::now();
    let captured = runner()
        .capture(&ctx, &leader_exits_first(&pid_file), &SpawnOptions::within(Duration::from_mins(1)))
        .await
        .expect("a cancelled child is still answered");
    cancel.await.unwrap();
    assert!(started.elapsed() < Duration::from_secs(10));
    // The leader's own exit, and what it said before it.
    assert_eq!((captured.exit_code, captured.stdout.as_str()), (0, "hi\n"));
    let grandchild = read_pid(&pid_file).await;
    assert!(gone(grandchild).await, "the grandchild {grandchild} survived");
}

#[tokio::test]
async fn dropping_a_spawn_signals_its_group_after_the_leader_exited() {
    let directory = tempfile::tempdir().unwrap();
    let pid_file = directory.path().join("grandchild.pid");
    let runner = runner();
    let command = leader_exits_first(&pid_file);
    let abandoned = tokio::time::timeout(
        Duration::from_millis(500),
        runner.capture(&Ctx::background(), &command, &SpawnOptions::within(Duration::from_mins(1))),
    )
    .await;
    assert!(abandoned.is_err(), "the spawn finished on its own: {abandoned:?}");
    let grandchild = read_pid(&pid_file).await;
    assert!(gone(grandchild).await, "the grandchild {grandchild} outlived its spawn");
}

// The interrupt service reaches a child in its own group, which the terminal's Ctrl-C does not.
#[tokio::test]
async fn an_interrupt_reaches_every_registered_group() {
    let interrupts = Interrupts::detached();
    let runner = Runner::new(runner().environment(), interrupts.clone());
    // A foreground child: a non-interactive shell starts a background job with SIGINT ignored.
    let running = tokio::spawn(async move {
        runner
            .capture(
                &Ctx::background(),
                &sh("exec sleep 30"),
                &SpawnOptions::within(Duration::from_mins(1)),
            )
            .await
    });
    tokio::time::sleep(Duration::from_millis(300)).await;
    assert!(interrupts.deliver(Signal::Interrupt));
    let captured = tokio::time::timeout(Duration::from_secs(10), running)
        .await
        .expect("the interrupt did not reach the child")
        .unwrap()
        .unwrap();
    assert_eq!(captured.exit_code, 128 + libc::SIGINT);
}

// After the first signal a child is signalled as it registers, unless it is a teardown that survives the interrupt:
// the removal of a run's secret files still runs then.
#[tokio::test]
async fn a_teardown_child_survives_an_interrupt_that_already_arrived() {
    let interrupts = Interrupts::detached();
    let runner = Runner::new(runner().environment(), interrupts.clone());
    assert!(interrupts.deliver(Signal::Interrupt));
    let options = SpawnOptions::within(Duration::from_secs(10));
    let teardown = runner
        .capture(
            &Ctx::background(),
            &sh("sleep 0.3; printf removed"),
            &options.clone().surviving_interrupt(),
        )
        .await
        .unwrap();
    assert_eq!((teardown.exit_code, teardown.stdout.as_str()), (0, "removed"));
    let ordinary = runner.capture(&Ctx::background(), &sh("exec sleep 30"), &options).await.unwrap();
    assert_eq!(ordinary.exit_code, 128 + libc::SIGINT);
}

// Output past the limit is refused rather than silently truncated, because a truncated JSON reply parses as
// something and that something is wrong.
#[tokio::test]
async fn output_past_the_limit_is_refused() {
    let small = runner().with_capture_limit(64);
    let command = sh("printf 'x%.0s' $(seq 1 500)");
    let refusal = capture(&small, &command).await.map_err(Refusal::from).unwrap_err();
    assert_eq!(refusal.code, "subprocess_output_limit");
    assert_eq!(refusal.exit, Exit::SOFTWARE);
    assert_eq!(
        refusal.details(),
        Some(json!({ "stdoutTruncated": true, "stderrTruncated": false }))
    );

    // Unless the caller wants a tail rather than a document, in which case it is kept and flagged.
    let captured = small
        .capture(
            &Ctx::background(),
            &command,
            &SpawnOptions {
                allow_truncated: true,
                ..SpawnOptions::within(Duration::from_mins(1))
            },
        )
        .await
        .unwrap();
    assert!(captured.stdout_truncated);
    assert_eq!(captured.stdout.len(), 64);
}

// The child must not block on a full pipe once the limit is reached, or the controller reports a timeout for a
// command that had already finished its work.
#[tokio::test]
async fn the_reader_keeps_draining_past_the_limit() {
    let small = runner().with_capture_limit(16);
    let drained = tokio::time::timeout(
        Duration::from_secs(20),
        small.capture(
            &Ctx::background(),
            // Far more than a pipe buffer, so a reader that stopped consuming would deadlock here.
            &sh("printf 'y%.0s' $(seq 1 200000)"),
            &SpawnOptions {
                allow_truncated: true,
                ..SpawnOptions::within(Duration::from_mins(1))
            },
        ),
    )
    .await
    .expect("the capture deadlocked once the limit was reached")
    .unwrap();
    assert_eq!(drained.exit_code, 0);
    assert_eq!(drained.stdout.len(), 16);
}

#[tokio::test]
async fn checked_to_file_writes_the_whole_output_and_cleans_up_on_failure() {
    let ctx = Ctx::background();
    let root = tempfile::tempdir().unwrap();
    let destination = root.path().join("build.log");
    let said = runner()
        .checked_to_file(
            &ctx,
            &sh("echo built; echo noise >&2"),
            &destination,
            &SpawnOptions::within(Duration::from_mins(1)),
        )
        .await
        .unwrap();
    assert_eq!(fs::read_to_string(&destination).unwrap(), "built\n");
    // Stderr stays out of the file and comes back to the caller.
    assert_eq!(said, "noise\n");
    let mode = fs::symlink_metadata(&destination).unwrap().permissions().mode();
    assert_eq!(mode & 0o777, 0o600);

    // An existing destination is refused rather than appended to or replaced, so two callers cannot both think they
    // own the log.
    let refusal = runner()
        .checked_to_file(&ctx, &sh("echo again"), &destination, &SpawnOptions::within(Duration::from_mins(1)))
        .await
        .map_err(Refusal::from)
        .unwrap_err();
    assert_eq!(refusal.code, "capture_destination_exists");
    assert_eq!(refusal.exit, Exit::CANT_CREATE);
    assert_eq!(fs::read_to_string(&destination).unwrap(), "built\n");

    // A failure removes the partial file, so a caller cannot mistake it for a complete capture.
    let failed = root.path().join("failed.log");
    let refusal = runner()
        .checked_to_file(
            &ctx,
            &sh("echo partial; exit 2"),
            &failed,
            &SpawnOptions::within(Duration::from_mins(1)),
        )
        .await
        .map_err(Refusal::from)
        .unwrap_err();
    assert_eq!(refusal.code, "subprocess_failed");
    assert_eq!(refusal.details(), Some(json!({ "exitCode": 2 })));
    assert!(!failed.exists(), "a partial capture was left behind");

    // A log is the opposite: kept on failure, with the diagnostics merged in, because the failure is when someone
    // reads it.
    let log = root.path().join("kept.log");
    let options = SpawnOptions {
        merge_stderr_into_file: true,
        keep_destination_on_failure: true,
        ..SpawnOptions::within(Duration::from_mins(1))
    };
    let refusal = runner()
        .checked_to_file(&ctx, &sh("echo out; echo diagnostic >&2; exit 3"), &log, &options)
        .await
        .map_err(Refusal::from)
        .unwrap_err();
    assert_eq!(refusal.code, "subprocess_failed");
    assert_eq!(fs::read_to_string(&log).unwrap(), "out\ndiagnostic\n");
}

#[tokio::test]
async fn the_ps_probe_answers_nothing_for_absent_processes() {
    let ctx = Ctx::background();
    let runner = runner();
    for pid in [0, -1, 0x7FFF_FFF0] {
        assert_eq!(runner.probe_process(&ctx, pid, PsField::StartTime).await, Ok(None), "pid {pid}");
    }
    // And this process's own facts are answered, which is what a held lock records.
    let own = i32::try_from(std::process::id()).unwrap();
    let start = runner.probe_process(&ctx, own, PsField::StartTime).await.unwrap();
    let start = start.expect("this process has a start time");
    assert!(!start.contains("  "), "the start time is not collapsed: {start:?}");
    assert!(runner.probe_process(&ctx, own, PsField::Command).await.unwrap().is_some());
}

// A suite gives its runner a process table, and then no probe of a pid the table holds reads a host process.
#[tokio::test]
async fn a_process_table_answers_the_probe_in_place_of_the_host() {
    /// Holds two pids: 7 is a live process, and 8 is no process.
    struct OneProcess;
    impl ProcessTable for OneProcess {
        fn holds(&self, pid: i32) -> bool {
            matches!(pid, 7 | 8)
        }

        fn field(&self, pid: i32, field: PsField) -> Option<String> {
            assert!(self.holds(pid), "the table was asked about pid {pid}, which it does not hold");
            (pid == 7).then(|| format!("{field:?}"))
        }
    }
    let ctx = Ctx::background();
    let runner = runner().with_process_table(Arc::new(OneProcess));
    assert_eq!(
        runner.probe_process(&ctx, 7, PsField::Command).await,
        Ok(Some("Command".to_owned()))
    );
    assert_eq!(runner.probe_process(&ctx, 8, PsField::Command).await, Ok(None), "the host answered");
    let own = i32::try_from(std::process::id()).unwrap();
    assert!(
        runner.probe_process(&ctx, own, PsField::StartTime).await.unwrap().is_some(),
        "the host did not answer for a pid the table does not hold"
    );
    let overridden = runner.with_overrides(&[("NAME", "value")]);
    assert_eq!(
        overridden.probe_process(&ctx, 7, PsField::State).await,
        Ok(Some("State".to_owned())),
        "an override dropped the table"
    );
}

// A stop path decides from this probe whether to delete a VM's only identity record, and it runs exactly when the
// operator pressed Ctrl-C. An interrupted service signals every group registered after the signal, and a cancelled
// context asks every wait to stop, so a probe going through either would answer a live process as dead.
#[tokio::test]
async fn the_ps_probe_still_answers_after_an_interrupt() {
    let interrupts = Interrupts::detached();
    let runner = Runner::new(runner().environment(), interrupts.clone());
    assert!(interrupts.deliver(Signal::Interrupt));
    let ctx = interrupts.context();
    assert!(ctx.is_cancelled());
    let own = i32::try_from(std::process::id()).unwrap();
    assert!(
        runner.probe_process(&ctx, own, PsField::State).await.unwrap().is_some(),
        "a live process read as dead after the interrupt"
    );
    // The interrupt still reaches an ordinary spawn: only the probe is exempt.
    let captured = runner
        .capture(&ctx, &sh("exec sleep 30"), &SpawnOptions::within(Duration::from_mins(1)))
        .await
        .unwrap();
    assert_ne!(captured.exit_code, 0);
}

// A spawn records itself into the phase its context carries, whether it succeeded or not.
#[tokio::test]
async fn a_spawn_is_recorded_into_its_phase() {
    let timeline = Timeline::collecting();
    let ctx = Ctx::background().with_timeline(timeline.clone());
    let (inner, phase) = ctx.begin("host-work");
    let _ = capture_in(&inner, &words(["/bin/sh", "-c", "exit 0"])).await;
    let _ = capture_in(&inner, &words(["/nonexistent/tool"])).await;
    drop(phase);
    assert_eq!(timeline.take()[0].host_calls, 2);
}

async fn capture_in(ctx: &Ctx, argv: &[String]) -> Result<Captured, ProcError> {
    runner().capture(ctx, argv, &SpawnOptions::within(Duration::from_mins(1))).await
}

// --- the piped child ---------------------------------------------------------------------------------------------

fn piped(argv: &[String]) -> GuestStream {
    runner().spawn_piped(&Ctx::background(), argv).expect("the child starts").into()
}

// The stream is a socket to the HTTP client: every byte written reaches the child, every byte the child writes
// reaches the reader, and closing stdin ends the stream. The payload is larger than a pipe buffer, so a stream that
// could not read and write at once would deadlock here.
#[tokio::test]
async fn spawn_piped_streams_both_ways() {
    use tokio::io::AsyncReadExt as _;
    let stream = piped(&words(["/bin/cat"]));
    let (mut reader, mut writer) = tokio::io::split(stream);
    let payload: Vec<u8> = (0..=u8::MAX).cycle().take(200 * 1024).collect();
    let sent = payload.clone();
    let writing = tokio::spawn(async move {
        writer.write_all(&sent).await.expect("the payload is written");
        writer.shutdown().await.expect("stdin is closed");
    });
    let mut received = Vec::new();
    tokio::time::timeout(Duration::from_secs(20), reader.read_to_end(&mut received))
        .await
        .expect("the stream deadlocked")
        .expect("the stream ends with an EOF");
    writing.await.unwrap();
    assert_eq!(received.len(), payload.len());
    assert!(received == payload, "the bytes changed on the way");
}

// A child that ends before the first byte is a connection that never opened, so the HTTP client must see a refused
// connection with the reason, and not an empty response.
#[tokio::test]
async fn spawn_piped_reports_an_exit_before_the_first_byte() {
    use tokio::io::AsyncReadExt as _;
    // The guest agent refuses a closed port with exit 70 and one failure envelope on stderr. The refusal quotes the
    // message of the envelope, and not the JSON.
    let envelope = r#"{"schemaVersion":1,"ok":false,"command":"relay","error":{"code":"guest_relay_failed","message":"cannot connect to 127.0.0.1:7654: Connection refused (os error 61)","details":null}}"#;
    let mut stream = piped(&sh(&format!("echo '{envelope}' >&2; exit 70")));
    let mut buffer = [0; 16];
    let error = stream.read(&mut buffer).await.unwrap_err();
    assert_eq!(error.kind(), std::io::ErrorKind::ConnectionRefused);
    assert!(
        error
            .to_string()
            .ends_with("exited with 70: cannot connect to 127.0.0.1:7654: Connection refused (os error 61)"),
        "{error}"
    );

    // Other stderr is quoted as it is: the last non-empty line.
    let mut stream = piped(&sh("echo ignored >&2; echo nope >&2; echo >&2; exit 70"));
    let error = stream.read(&mut buffer).await.unwrap_err();
    assert_eq!(error.kind(), std::io::ErrorKind::ConnectionRefused);
    let message = error.to_string();
    assert!(
        message.starts_with("sh -c ") && message.ends_with("exited with 70: nope"),
        "{message}"
    );
    // Every later read answers the same refusal, and so does a write.
    let again = stream.read(&mut buffer).await.unwrap_err();
    assert_eq!(
        (again.kind(), again.to_string()),
        (std::io::ErrorKind::ConnectionRefused, message.clone())
    );
    let error = stream.write_all(b"GET / HTTP/1.1\r\n").await.unwrap_err();
    assert_eq!((error.kind(), error.to_string()), (std::io::ErrorKind::ConnectionRefused, message));

    // A client that writes a request body larger than the pipe before it reads also receives the reason, and not
    // a broken pipe.
    let mut stream = piped(&sh("echo nope >&2; exit 70"));
    let body = vec![0; 1024 * 1024];
    let error = tokio::time::timeout(Duration::from_secs(20), stream.write_all(&body))
        .await
        .expect("the write did not end")
        .unwrap_err();
    assert_eq!(error.kind(), std::io::ErrorKind::ConnectionRefused, "{error}");
    assert!(error.to_string().ends_with("exited with 70: nope"), "{error}");

    // 64 is the usage envelope of an installed agent older than this controller, which the message names.
    let mut stream = piped(&sh("echo 'usage: vm-guest-agent <verb>' >&2; exit 64"));
    let error = stream.read(&mut buffer).await.unwrap_err();
    assert_eq!(
        (error.kind(), error.to_string()),
        (
            std::io::ErrorKind::ConnectionRefused,
            "the installed guest agent has no relay verb (exit 64)".to_owned()
        )
    );

    // A child that printed nothing says so.
    let mut stream = piped(&sh("exit 3"));
    let error = stream.read(&mut buffer).await.unwrap_err();
    assert!(error.to_string().ends_with("exited with 3; it printed nothing"), "{error}");
}

// A loaded fast lane once lost the line of a refusal: the exit arrived first, and the drain of stderr outlasted the
// short grace. With the group empty no process holds stderr, so the supervisor reads it to its end.
#[tokio::test]
async fn an_exited_child_keeps_its_stderr_line_when_the_tail_is_late() {
    let late = async {
        tokio::time::sleep(Duration::from_millis(500)).await;
        "nope".to_owned()
    };
    assert_eq!(stream::said_after_exit(late, false).await, "nope");

    // A member of the group can hold stderr for as long as it runs, so the wait for it still ends.
    assert_eq!(stream::said_after_exit(std::future::pending(), true).await, "");
}

// After the first byte, the end of the child is the end of the response, whatever it exited with. A clean exit
// with no byte is a connection the far side closed, and so an EOF too.
#[tokio::test]
async fn spawn_piped_ends_with_an_eof_after_the_first_byte() {
    use tokio::io::AsyncReadExt as _;
    let mut received = Vec::new();
    piped(&sh("printf partial; echo failed >&2; exit 69"))
        .read_to_end(&mut received)
        .await
        .expect("an exit after the first byte is an EOF");
    assert_eq!(received, b"partial");

    received.clear();
    piped(&sh("exit 0"))
        .read_to_end(&mut received)
        .await
        .expect("a clean exit is an EOF");
    assert!(received.is_empty());
}

// A dropped HTTP connection must strand no `tart exec` and no guest relay, so the drop takes the whole group.
#[tokio::test]
async fn spawn_piped_kills_the_child_on_drop() {
    let directory = tempfile::tempdir().unwrap();
    let leader_file = directory.path().join("leader.pid");
    let grandchild_file = directory.path().join("grandchild.pid");
    let stream = piped(&sh(&format!(
        "echo $$ > {}; sleep 30 & echo $! > {}; wait",
        leader_file.display(),
        grandchild_file.display()
    )));
    let leader = read_pid(&leader_file).await;
    let grandchild = read_pid(&grandchild_file).await;
    drop(stream);
    assert!(gone(leader).await, "the child {leader} outlived its stream");
    assert!(gone(grandchild).await, "the grandchild {grandchild} outlived its stream");
}

// A cancelled operation asks the piped child to stop, as it asks a captured one, and the reader sees the end.
#[tokio::test]
async fn a_cancelled_operation_ends_the_piped_child() {
    use tokio::io::AsyncReadExt as _;
    let token = CancellationToken::new();
    let ctx = Ctx::new(token.clone());
    let mut stream: GuestStream = runner().spawn_piped(&ctx, &sh("exec sleep 30")).expect("the child starts").into();
    token.cancel();
    let mut buffer = [0; 16];
    let error = tokio::time::timeout(Duration::from_secs(10), stream.read(&mut buffer))
        .await
        .expect("the cancellation did not reach the child")
        .unwrap_err();
    assert!(
        error.to_string().contains(&format!("exited with {}", 128 + libc::SIGTERM)),
        "{error}"
    );
}

// A piped child records itself into the phase its context carries, like any other spawn.
#[tokio::test]
async fn a_piped_child_is_recorded_into_its_phase() {
    use tokio::io::AsyncReadExt as _;
    let timeline = Timeline::collecting();
    let ctx = Ctx::background().with_timeline(timeline.clone());
    let (inner, phase) = ctx.begin("relay");
    let mut stream: GuestStream = runner().spawn_piped(&inner, &sh("printf done")).unwrap().into();
    let mut received = Vec::new();
    stream.read_to_end(&mut received).await.unwrap();
    drop(stream);
    // The supervisor records once the stream is dropped, on its own task.
    tokio::time::sleep(Duration::from_millis(300)).await;
    drop(phase);
    assert_eq!(timeline.take()[0].host_calls, 1);
}
