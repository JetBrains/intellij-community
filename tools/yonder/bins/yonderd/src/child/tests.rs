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

/// A grandchild that keeps the stdout open, as a helper of bd can, costs the drain time, and the output that came
/// before stays.
#[test]
fn a_grandchild_that_holds_the_pipe_keeps_the_output_that_came() {
    let started = Instant::now();
    // The grandchild closes each descriptor above 2 first. On macOS a descriptor of another test thread can reach a
    // child before its close-on-exec flag is set, and a grandchild that lives for seconds would hold it open.
    let script = "echo '{\"lease_id\":\"L1\"}'; \
                  (i=3; while [ $i -lt 256 ]; do eval \"exec $i>&-\"; i=$((i + 1)); done; sleep 8) & exit 0";
    let output = run(&mut sh(script), Duration::from_secs(60), &never).expect("the output");
    assert_eq!(output.stdout_text(), r#"{"lease_id":"L1"}"#);
    assert!(started.elapsed() < Duration::from_secs(20), "{:?}", started.elapsed());
}

#[test]
fn a_missing_program_is_a_spawn_error() {
    let error = run(&mut Command::new("/nonexistent/yonder-test"), Duration::from_secs(1), &never).expect_err("refused");
    assert!(matches!(error, ChildError::Spawn { .. }), "{error:?}");
}

#[test]
fn a_stop_kills_the_children_of_the_child_too() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let pid_file = dir.path().join("grandchild.pid");
    let script = format!("sleep 30 & echo $! >'{}'; wait", pid_file.display());
    let stop = AtomicBool::new(false);
    let stopper = || {
        if pid_file.exists() {
            stop.store(true, Ordering::SeqCst);
        }
        stop.load(Ordering::SeqCst)
    };
    let started = Instant::now();
    let error = run(&mut sh(&script), Duration::from_secs(60), &stopper).expect_err("stopped");
    assert!(matches!(error, ChildError::Stopped { .. }), "{error:?}");
    let grandchild: libc::pid_t = std::fs::read_to_string(&pid_file).expect("the pid").trim().parse().expect("a pid");
    // The system reaps the killed grandchild a moment later, as its parent has ended.
    // SAFETY: kill with signal 0 only checks that the process exists.
    while unsafe { libc::kill(grandchild, 0) } == 0 {
        assert!(started.elapsed() < Duration::from_secs(10), "the grandchild {grandchild} lives on");
        thread::sleep(Duration::from_millis(20));
    }
}
