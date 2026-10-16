use std::fs;
use std::path::Path;

use pretty_assertions::assert_eq;

use super::*;

#[test]
fn the_dump_of_an_ide_run_goes_into_the_log_of_its_launch() {
    let root = Path::new("/data/ide/k");
    assert_eq!(directory(root, "run-ide-launch-3"), root.join("log/launch-3"));
    assert_eq!(directory(root, "run-ui-daemon-1"), root.join("run-ui-daemon-1"));
}

#[test]
fn a_dump_is_the_output_of_jcmd_and_a_failure_keeps_the_file() {
    let directory = tempfile::tempdir().unwrap();
    let jcmd = avl_testkit::fake_executable(directory.path(), "jcmd", "echo \"jcmd $*\"\necho warning >&2\n").unwrap();
    let at: Timestamp = "2026-10-08T10:00:00Z".parse().unwrap();
    let logs = directory.path().join("log/launch-1");
    let dump = capture(&jcmd, 4242, &logs, at).unwrap();
    assert_eq!(dump, logs.join(format!("threadDump-before-kill-{}.txt", at.as_millisecond())));
    assert_eq!(fs::read_to_string(&dump).unwrap(), "jcmd 4242 Thread.print\nwarning\n");

    let failing = avl_testkit::fake_executable(directory.path(), "failing-jcmd", "echo 'no such process'\nexit 1\n").unwrap();
    let later = at.checked_add(jiff::SignedDuration::from_millis(1)).unwrap();
    let error = capture(&failing, 4242, &logs, later).unwrap_err();
    assert!(error.to_string().contains("exited with"), "{error}");
    let kept = logs.join(format!("threadDump-before-kill-{}.txt", later.as_millisecond()));
    assert_eq!(fs::read_to_string(kept).unwrap(), "no such process\n");
}
