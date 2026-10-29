use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::thread;
use std::time::{Duration, Instant};

use super::*;

/// A fake `caffeinate` that appends its arguments and its pid to `log`, then sleeps until it is killed.
fn fake(dir: &Path, log: &Path) -> OsString {
    let script = dir.join("caffeinate");
    fs::write(&script, format!("#!/bin/sh\necho \"$$ $*\" >>'{}'\nexec sleep 60\n", log.display())).expect("the fake");
    fs::set_permissions(&script, fs::Permissions::from_mode(0o755)).expect("the mode");
    script.into_os_string()
}

/// The lines of `log` once it has `count` of them.
fn lines(log: &Path, count: usize) -> Vec<String> {
    let deadline = Instant::now() + Duration::from_secs(10);
    loop {
        let text = fs::read_to_string(log).unwrap_or_default();
        let lines: Vec<String> = text.lines().map(str::to_owned).collect();
        if lines.len() >= count || Instant::now() > deadline {
            return lines;
        }
        thread::sleep(Duration::from_millis(10));
    }
}

fn alive(pid: &str) -> bool {
    let pid: libc::pid_t = pid.parse().expect("a pid");
    // SAFETY: kill(2) with the signal 0 sends nothing and touches no memory.
    unsafe { libc::kill(pid, 0) == 0 }
}

/// The first holder starts one program that watches this process, a second holder shares it, and the program ends with
/// the last holder.
#[test]
fn the_holders_of_a_connection_share_one_program_that_ends_with_the_last() {
    let dir = tempfile::tempdir().expect("a directory");
    let log = dir.path().join("log");
    let awake = Awake::new(Some(fake(dir.path(), &log)));
    let first = awake.hold();
    let second = awake.hold();
    let started = lines(&log, 1);
    assert_eq!(started.len(), 1, "{started:?}");
    let (pid, args) = started[0].split_once(' ').expect("a pid and the arguments");
    assert_eq!(args, format!("-i -s -w {}", std::process::id()));
    drop(first);
    assert!(alive(pid), "a holder is left");
    drop(second);
    assert!(!alive(pid), "the last holder stopped the program");
    let again = awake.hold();
    assert_eq!(lines(&log, 2).len(), 2, "a new holder starts it again");
    drop(again);
}

/// Without a program nothing starts, and a holder still counts.
#[test]
fn without_a_program_a_holder_starts_nothing() {
    let awake = Awake::new(None);
    let holding = awake.hold();
    assert!(awake.held.lock().expect("the lock").child.is_none());
    drop(holding);
    assert_eq!(awake.held.lock().expect("the lock").count, 0);
}
