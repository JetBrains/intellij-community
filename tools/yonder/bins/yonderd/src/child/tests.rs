use std::sync::atomic::{AtomicBool, Ordering};

use pretty_assertions::assert_eq;

use super::*;

fn sh(script: &str) -> Command {
    let mut command = Command::new("/bin/sh");
    command.arg("-c").arg(script);
    command
}

#[test]
fn a_child_answers_its_output_and_status() {
    let output = run(&mut sh("echo out; echo err >&2; exit 3"), Duration::from_secs(10), &never).expect("the output");
    assert_eq!(output.status.code(), Some(3));
    assert_eq!(output.stdout_text(), "out");
    assert_eq!(output.diagnostic(), "err");
}

#[test]
fn a_child_reads_its_input() {
    let output = run_with_input(&mut sh("tr a-z A-Z"), b"yonder\n", Duration::from_secs(10)).expect("the output");
    assert_eq!(output.stdout_text(), "YONDER");
}

#[test]
fn a_child_past_its_deadline_is_killed() {
    let started = Instant::now();
    let error = run(&mut sh("sleep 30"), Duration::from_millis(200), &never).expect_err("timed out");
    assert!(matches!(error, ChildError::TimedOut { .. }), "{error:?}");
    assert!(started.elapsed() < Duration::from_secs(10), "{:?}", started.elapsed());
    assert_eq!(error.to_string(), "`/bin/sh -c sleep 30` did not end in 0 s and was killed");
}

#[test]
fn a_stopped_child_is_killed() {
    let stop = AtomicBool::new(false);
    let started = Instant::now();
    let stopper = || {
        if started.elapsed() > Duration::from_millis(200) {
            stop.store(true, Ordering::SeqCst);
        }
        stop.load(Ordering::SeqCst)
    };
    let error = run(&mut sh("sleep 30"), Duration::from_secs(60), &stopper).expect_err("stopped");
    assert!(matches!(error, ChildError::Stopped { .. }), "{error:?}");
    assert!(started.elapsed() < Duration::from_secs(10), "{:?}", started.elapsed());
}

#[test]
fn the_watchdog_ends_a_write_to_a_child_that_reads_nothing() {
    let mut running = spawn(&mut sh("exec sleep 30"), true, Duration::from_millis(300)).expect("started");
    let mut stdin = running.take_stdin().expect("the stdin");
    let started = Instant::now();
    // The pipe buffer fills, and the write blocks until the watchdog kills the child.
    let chunk = vec![0u8; 1 << 20];
    let written = (0..64).try_for_each(|_| stdin.write_all(&chunk));
    assert!(written.is_err(), "the write ended without an error");
    assert!(started.elapsed() < Duration::from_secs(10), "{:?}", started.elapsed());
    drop(stdin);
    let error = running.wait(&never).expect_err("timed out");
    assert!(matches!(error, ChildError::TimedOut { .. }), "{error:?}");
}

#[test]
fn a_missing_program_is_a_spawn_error() {
    let error = run(&mut Command::new("/nonexistent/yonder-test"), Duration::from_secs(1), &never).expect_err("refused");
    assert!(matches!(error, ChildError::Spawn { .. }), "{error:?}");
}
