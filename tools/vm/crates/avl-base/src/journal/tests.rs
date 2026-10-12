use std::fs;

use avl_wire::progress::Phase;
use pretty_assertions::assert_eq;
use serde_json::Value;

use super::*;
use crate::report::{Mode, Scope, Terminal};

fn journal_lines(path: &Path) -> Vec<Value> {
    let content = fs::read_to_string(path).expect("the journal is readable");
    content
        .lines()
        .map(|line| serde_json::from_str(line).unwrap_or_else(|error| panic!("a torn record {line:?}: {error}")))
        .collect()
}

// The journal holds every record the command published while it was attached, in order, whatever the output
// form: the viewer reads a run that printed nothing to anybody.
#[test]
fn the_journal_keeps_every_record_in_order() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let (reporter, _, _) = Reporter::in_memory("vm");
    let attached = attach(root.path(), &reporter, "user-run-1");
    reporter.note("first", None);
    reporter
        .start_phase(Phase::HostBuild, "//a:b", Some(&Scope::worker("air-linux-1")))
        .succeed();
    drop(attached);
    reporter.note("after the run", None);

    let path = root.path().join("runs").join("user-run-1").join(FILE_NAME);
    let kinds: Vec<Value> = journal_lines(&path).into_iter().map(|record| record["event"].clone()).collect();
    assert_eq!(kinds, ["progress", "phase", "phase"]);
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let mode = fs::metadata(&path).expect("the journal exists").permissions().mode();
        assert_eq!(mode & 0o777, 0o600, "the journal is not private");
        let directory = fs::metadata(path.parent().expect("a run directory"))
            .expect("the run directory exists")
            .permissions()
            .mode();
        assert_eq!(directory & 0o777, 0o700, "the run directory is not private");
    }
}

// A run's last record carries the exit status, the refusal's code and the first line of its message; a green run
// carries the exit status alone.
#[test]
fn run_finished_carries_the_exit_status_and_the_refusals_first_line() {
    let refused = Refusal::new("tests_failed", Exit::TESTS_FAILED, "1 test failed\nfirst: a.OneTest");
    assert_eq!(
        serde_json::to_value(run_finished(Some(&refused))).unwrap(),
        serde_json::json!({"exitCode": 6, "code": "tests_failed", "message": "1 test failed"})
    );
    assert_eq!(
        serde_json::to_value(run_finished(None)).unwrap(),
        serde_json::json!({"exitCode": 0})
    );
}

// A run id names one run. A second journal for the same id is refused, and the run goes on without one.
#[test]
fn a_second_journal_for_one_run_is_refused_and_the_run_goes_on() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let first = Journal::create(root.path(), "user-run-1").expect("the first journal");
    let refusal = Journal::create(root.path(), "user-run-1")
        .err()
        .expect("a second journal for one run is refused");
    assert_eq!(refusal.code, "state_write_failed");

    let (reporter, _, stderr) = Reporter::in_memory("vm");
    reporter.set_mode(Mode::Human(Terminal::default()));
    drop(attach(root.path(), &reporter, "user-run-1"));
    assert!(stderr.text().contains("no run journal"), "{:?}", stderr.text());
    first.close().expect("the first journal closes");
}

// The run id becomes a path component, so a name a shell would reinterpret is refused.
#[test]
fn a_run_id_is_a_path_component() {
    let root = Path::new("/state");
    run_dir(root, "../escape").unwrap_err();
    assert_eq!(run_dir(root, "user-run-1"), Ok(root.join("runs").join("user-run-1")));
    traces_dir(root, "user-run-1", "it/1").unwrap_err();
    assert_eq!(
        traces_dir(root, "user-run-1", "it-1"),
        Ok(root.join("runs").join("user-run-1").join("traces").join("it-1"))
    );
}
