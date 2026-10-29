#[cfg(target_os = "macos")]
use std::fs::{self, OpenOptions};
#[cfg(target_os = "macos")]
use std::io::Write;
#[cfg(target_os = "macos")]
use std::sync::mpsc;
use std::time::Instant;

use super::*;

/// The longest time from a change to the end of a wait.
#[cfg(target_os = "macos")]
const LATENCY: Duration = Duration::from_millis(50);

/// Waits with `watch` while a thread runs `change` 100 ms later. Answers the time from the change to the end of the
/// wait.
#[cfg(target_os = "macos")]
fn latency(watch: &mut Watch, change: impl FnOnce() + Send + 'static) -> Duration {
    let (sender, changed) = mpsc::channel();
    let changer = thread::spawn(move || {
        thread::sleep(Duration::from_millis(100));
        let at = Instant::now();
        change();
        let _ = sender.send(at);
    });
    watch.wait(Duration::from_secs(5));
    let woke = Instant::now();
    changer.join().expect("the change");
    let at = changed.recv().expect("the time of the change");
    woke.saturating_duration_since(at)
}

#[test]
#[cfg(target_os = "macos")]
fn a_write_to_the_log_and_a_new_file_in_the_run_directory_end_a_wait_at_once() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let log = dir.path().join("events.log");
    fs::write(&log, "").expect("the log");
    let mut watch = Watch::new(&[&log, dir.path()]);
    assert!(watch.is_pushed());

    let waited = latency(&mut watch, move || {
        let mut file = OpenOptions::new().append(true).open(&log).expect("opened");
        file.write_all(b"line\n").expect("appended");
    });
    assert!(waited < LATENCY, "{waited:?}");

    let exit = dir.path().join("exit.json");
    let waited = latency(&mut watch, move || {
        let temp = exit.with_extension("tmp");
        fs::write(&temp, "{}").expect("written");
        fs::rename(&temp, &exit).expect("renamed");
    });
    assert!(waited < LATENCY, "{waited:?}");
}

#[test]
#[cfg(target_os = "macos")]
fn a_change_before_the_wait_ends_it_at_once() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let log = dir.path().join("events.log");
    fs::write(&log, "").expect("the log");
    let mut watch = Watch::new(&[&log]);
    fs::write(&log, "line\n").expect("written");
    let started = Instant::now();
    watch.wait(Duration::from_secs(5));
    assert!(started.elapsed() < LATENCY, "{:?}", started.elapsed());
}

#[test]
fn a_watch_of_a_missing_file_polls() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let mut watch = Watch::new(&[&dir.path().join("missing")]);
    assert!(!watch.is_pushed());
    let started = Instant::now();
    watch.wait(Duration::from_secs(5));
    let waited = started.elapsed();
    assert!(waited >= POLL && waited < Duration::from_secs(2), "{waited:?}");
}
