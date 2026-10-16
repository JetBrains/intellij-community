use std::process::Command;
use std::time::{Duration, Instant};

use pretty_assertions::assert_eq;

use super::*;

#[test]
fn only_the_helpers_of_the_context_are_selected() {
    let table = [
        (10, "/jbr/lib/cef_server --user-data-dir=/data/ide/k/system/jcef_cache".to_owned()),
        (11, "/jbr/lib/cef_server --user-data-dir=/data/ide/k2/system/jcef_cache".to_owned()),
        (12, "/jbr/bin/java -Didea.system.path=/data/ide/k/system".to_owned()),
        (13, "/jbr/lib/cef_server --user-data-dir=/data/ide/k/system/jcef_cache".to_owned()),
        (0, "/jbr/lib/cef_server /data/ide/k/system".to_owned()),
    ];
    assert_eq!(jcef_helpers(&table, "/data/ide/k", 13), [10]);
    assert_eq!(jcef_helpers(&table, "/data/ide/k/", 0), [10, 13]);
}

#[test]
fn a_ps_line_is_a_pid_and_a_command_line() {
    let text = "    1 /sbin/launchd\n  420 /Applications/My App.app/cef_server --x=/a b\nnot a line\n";
    assert_eq!(
        parse_ps(text),
        [
            (1, "/sbin/launchd".to_owned()),
            (420, "/Applications/My App.app/cef_server --x=/a b".to_owned())
        ]
    );
}

// A live helper of the context is killed; the helper of another context lives on.
#[test]
fn a_live_helper_of_the_context_is_killed() {
    let directory = tempfile::tempdir().unwrap();
    let helper = avl_testkit::fake_executable(directory.path(), "cef_server", "while :; do /bin/sleep 1; done\n").unwrap();
    let context = directory.path().join("ide/k");
    let other = directory.path().join("ide/k2");
    let spawn = |root: &Path| {
        Command::new("/bin/sh")
            .arg(&helper)
            .arg(format!("--user-data-dir={}/system/jcef_cache", root.display()))
            .spawn()
            .unwrap()
    };
    let mut mine = spawn(&context);
    let mut theirs = spawn(&other);
    // The table shows a process only once it has exec'd the shell with these arguments.
    let until = Instant::now() + Duration::from_secs(10);
    let own = i32::try_from(std::process::id()).unwrap();
    while jcef_helpers(&process_table(), &context.to_string_lossy(), own).is_empty() && Instant::now() < until {
        thread::sleep(Duration::from_millis(20));
    }
    let reaped = reap_jcef_helpers(&context);
    assert_eq!(reaped, [i32::try_from(mine.id()).unwrap()]);
    let status = mine.wait().unwrap();
    assert!(!status.success(), "the helper ended by itself: {status}");
    assert_eq!(theirs.try_wait().unwrap(), None, "the helper of another context was killed");
    theirs.kill().unwrap();
    theirs.wait().unwrap();
}
