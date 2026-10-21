use std::fs;
use std::path::Path;

use avl_wire::daemon::{
    ContainerFailed, ExecutionIdentity, IdeExited, IdeLaunched, RunEvent, RunEventKind, TestFinished, TestStarted, TypedFailure,
};
use avl_wire::report::ScenarioReport;
use pretty_assertions::assert_eq;
use tempfile::TempDir;

use super::*;

fn execution(scenario: &str) -> ExecutionIdentity {
    ExecutionIdentity {
        id: format!("[engine:lane-daemon]/[suite:suite]/[scenario:{scenario}]"),
        display_name: scenario.to_owned(),
        class_name: Some("com.intellij.air.AirRenameSessionGeneratedFlowUiTest".to_owned()),
        method_name: None,
    }
}

fn launched(pid: i64, name: &str, root: &Path) -> RunEventKind {
    RunEventKind::IdeLaunched(IdeLaunched {
        pid,
        launch_name: name.to_owned(),
        launch_key: "air-ui-fa2b".to_owned(),
        log_dir: root.join("ide/air-ui-fa2b/log").join(name).display().to_string(),
    })
}

fn exited(pid: i64) -> RunEventKind {
    RunEventKind::IdeExited(IdeExited {
        pid,
        exit_code: 0,
        signal: None,
    })
}

fn started(scenario: &str) -> RunEventKind {
    RunEventKind::TestStarted(TestStarted {
        timestamp: "t".to_owned(),
        execution: execution(scenario),
    })
}

fn finished(scenario: &str, status: &str) -> RunEventKind {
    RunEventKind::TestFinished(TestFinished {
        timestamp: "t".to_owned(),
        execution: execution(scenario),
        status: status.to_owned(),
        duration_ms: 1_000,
        failure: TypedFailure::default(),
        report: ScenarioReport::default(),
    })
}

fn events(kinds: Vec<RunEventKind>) -> Vec<RunEvent> {
    kinds.into_iter().map(RunEvent::synthesized).collect()
}

fn names(launches: &[Launch]) -> Vec<&str> {
    launches.iter().map(|launch| launch.name.as_str()).collect()
}

// A launch is affected when it ran during a scenario that did not pass: the launch that died in the scenario and the
// relaunch after it. A green scenario affects no launch, and an adopted IDE that names its launch again stays one launch.
#[test]
fn a_failed_scenario_affects_each_launch_that_ran_during_it() {
    let root = Path::new("/run");
    let run = events(vec![
        launched(100, "start-a", root),
        started("passes"),
        finished("passes", "SUCCESSFUL"),
        launched(100, "start-a", root),
        started("fails"),
        exited(100),
        launched(101, "restart-1-b", root),
        finished("fails", "FAILED"),
        exited(101),
        launched(102, "recycle-1-c", root),
        started("passes again"),
        finished("passes again", "SUCCESSFUL"),
    ]);

    let (all, affected) = launches(&run, false);

    assert_eq!(names(&all), ["start-a", "restart-1-b", "recycle-1-c"]);
    assert_eq!(names(&affected), ["start-a", "restart-1-b"]);
    assert_eq!(
        affected[1].log_dir,
        Path::new("/run/ide/air-ui-fa2b/log/restart-1-b"),
        "the log directory is the one of the record"
    );
    let (_, green) = launches(&run[..4], false);
    assert!(green.is_empty(), "{green:?}");

    let gone = TempDir::new().unwrap();
    let evidence = Evidence::new(&gone.path().join("state"), &gone.path().join("ide"), &gone.path().join("outputs"));
    let kept = keep(&evidence, &run, Moment::RunEnd, Limits::LANE);
    assert_eq!(kept.files, 0);
    assert_eq!(
        kept.cut,
        [
            "ide/air-ui-fa2b/restart-1-b: the log directory /run/ide/air-ui-fa2b/log/restart-1-b is gone",
            "ide/air-ui-fa2b/start-a: the log directory /run/ide/air-ui-fa2b/log/start-a is gone",
        ]
    );
}

// A watchdog expiry, the failure of a class and the hard stop affect each launch that runs then. A failure when no
// launch runs affects the newest launch, because an IDE that died before the failure ran it.
#[test]
fn an_expiry_a_failed_class_and_the_hard_stop_affect_the_running_launch_or_else_the_newest() {
    let root = Path::new("/run");
    let class_failed = RunEventKind::ContainerFailed(ContainerFailed {
        timestamp: "t".to_owned(),
        execution: execution("bare"),
        failure: TypedFailure::default(),
    });
    let (_, affected) = launches(
        &events(vec![
            launched(100, "start-a", root),
            exited(100),
            launched(101, "bare-b", root),
            exited(101),
            class_failed,
        ]),
        false,
    );
    assert_eq!(names(&affected), ["bare-b"]);

    let open = events(vec![
        launched(100, "start-a", root),
        exited(100),
        launched(101, "restart-1-b", root),
        started("hangs"),
    ]);
    assert!(launches(&open, false).1.is_empty());
    assert_eq!(names(&launches(&open, true).1), ["restart-1-b"]);
}

/// Writes `bytes` to `root/relative`.
fn write(root: &Path, relative: &str, bytes: &[u8]) {
    let file = root.join(relative);
    fs::create_dir_all(file.parent().unwrap()).unwrap();
    fs::write(file, bytes).unwrap();
}

/// The files below `directory`, relative to it, as slash text.
fn listed(directory: &Path) -> Vec<String> {
    files_below(directory).iter().map(|file| file.display().to_string()).collect()
}

/// A run directory with the IDE logs and the process output of a green launch and of a failed one, a slow step and an
/// authored artifact.
fn run_directory() -> (TempDir, Vec<RunEvent>) {
    let root = TempDir::new().unwrap();
    let logs = root.path().join("ide/air-ui-fa2b/log");
    write(&logs, "start-a/idea.log", b"green launch");
    write(&logs, "restart-1-b/idea.log", b"the newest part of the log");
    write(&logs, "restart-1-b/idea.1.log", b"an older part of the log");
    write(
        &logs,
        "restart-1-b/threadDump-before-kill-1792519706000.txt",
        b"\"AWT-EventQueue-0\"",
    );
    write(&logs, "restart-1-b/screenshots/001_heartbeat/IDE frame.png", b"png 1");
    write(&logs, "restart-1-b/screenshots/002_heartbeat/IDE frame.png", b"png 2");
    write(&logs, "restart-1-b/jcef/browser.log", b"jcef");
    let runs = root.path().join("ide/air-ui-fa2b");
    write(&runs, "run-ide-start-a/run.log", b"green output");
    write(&runs, "run-ide-restart-1-b/run.log", b"Error: could not open display");
    write(&runs, "run-ide-restart-1-b/supervisor.log", b"child exited 1");
    write(&runs, "run-ide-restart-1-b/state.json", b"{}");
    let iteration = root.path().join("state/iterations/iter-929c1541-1");
    write(
        &iteration,
        "slow-steps/01-before-reset-of-fails-daemon-threads.txt",
        b"daemon threads",
    );
    write(&iteration, "slow-steps/01-before-reset-of-fails-ide-dump.txt", b"ide dump");
    write(&iteration, "authored-artifacts/suite/fails/open/air-performance.json", b"{}");
    write(&iteration, "test.xml", b"<testsuites/>");
    let run = events(vec![
        launched(100, "start-a", root.path()),
        started("passes"),
        finished("passes", "SUCCESSFUL"),
        exited(100),
        launched(101, "restart-1-b", root.path()),
        started("fails"),
        finished("fails", "FAILED"),
    ]);
    (root, run)
}

/// The evidence of the run directory `root`.
fn evidence_of(root: &Path) -> Evidence {
    Evidence::new(&root.join("state"), &root.join("ide"), &root.join("outputs"))
}

// The copy takes the log directory and the process output of each affected launch, and the slow steps and the authored
// artifacts of each iteration. It leaves out the logs of a launch that no failure affected, the other files of its run,
// and the other files of an iteration.
#[test]
fn the_copy_keeps_the_logs_of_the_affected_launches_and_the_files_of_the_iterations() {
    let (root, run) = run_directory();
    let evidence = evidence_of(root.path());

    let kept = keep(&evidence, &run, Moment::RunEnd, Limits::LANE);

    assert_eq!(evidence.destination, root.path().join("outputs/lane"));
    assert_eq!(
        listed(&evidence.destination),
        [
            "evidence.txt",
            "ide/air-ui-fa2b/restart-1-b/idea.1.log",
            "ide/air-ui-fa2b/restart-1-b/idea.log",
            "ide/air-ui-fa2b/restart-1-b/jcef/browser.log",
            "ide/air-ui-fa2b/restart-1-b/run/run.log",
            "ide/air-ui-fa2b/restart-1-b/run/supervisor.log",
            "ide/air-ui-fa2b/restart-1-b/screenshots/001_heartbeat/IDE frame.png",
            "ide/air-ui-fa2b/restart-1-b/screenshots/002_heartbeat/IDE frame.png",
            "ide/air-ui-fa2b/restart-1-b/threadDump-before-kill-1792519706000.txt",
            "iterations/iter-929c1541-1/authored-artifacts/suite/fails/open/air-performance.json",
            "iterations/iter-929c1541-1/slow-steps/01-before-reset-of-fails-daemon-threads.txt",
            "iterations/iter-929c1541-1/slow-steps/01-before-reset-of-fails-ide-dump.txt",
        ]
    );
    assert_eq!(
        fs::read(evidence.destination.join("ide/air-ui-fa2b/restart-1-b/idea.log")).unwrap(),
        b"the newest part of the log"
    );
    assert_eq!(
        fs::read(evidence.destination.join("ide/air-ui-fa2b/restart-1-b/run/run.log")).unwrap(),
        b"Error: could not open display"
    );
    assert_eq!((kept.files, kept.cut.len(), kept.manifest_error.as_deref()), (11, 0, None));
    let manifest = fs::read_to_string(evidence.destination.join(MANIFEST_FILE)).unwrap();
    assert_eq!(
        manifest,
        format!(
            "lane-run kept this evidence at the end of a run that is not green: 11 file(s), {} bytes. The caps are 33554432 \
             bytes per file and 268435456 bytes in all.\nlaunch air-ui-fa2b/restart-1-b: {}\n",
            kept.bytes,
            root.path().join("ide/air-ui-fa2b/log/restart-1-b").display()
        )
    );
    assert_eq!(
        kept.describe(&evidence.destination),
        format!(
            "kept 11 evidence file(s) of {} bytes in {}, and cut or left out 0, which evidence.txt names",
            kept.bytes,
            evidence.destination.display()
        )
    );
}

// A log above the cap per file keeps its tail, and a picture above it is left out. Once the total cap is spent, the
// files of less worth are left out: the kill dump goes first, then idea.log, the output of the IDE process and the
// rotated idea.1.log, and the newest screenshot before an older one. A small file of less worth still fits in what is
// left. The manifest names each file that was cut or left out.
#[test]
fn the_caps_keep_the_tail_of_a_large_log_and_leave_out_the_files_of_least_worth() {
    let (root, run) = run_directory();
    let logs = root.path().join("ide/air-ui-fa2b/log/restart-1-b");
    write(&logs, "idea.log", b"0123456789 the end of the run");
    write(&logs, "screenshots/002_heartbeat/IDE frame.png", b"a picture that is too large");
    let evidence = evidence_of(root.path());
    let limits = Limits { file: 25, total: 84 };

    let kept = keep(&evidence, &run, Moment::HardStop, limits);

    let copy = evidence.destination.join("ide/air-ui-fa2b/restart-1-b");
    assert_eq!(fs::read_to_string(copy.join("idea.log")).unwrap(), "456789 the end of the run");
    assert_eq!(fs::read_to_string(copy.join("run/run.log")).unwrap(), "r: could not open display");
    let slow = "iterations/iter-929c1541-1/slow-steps/01-before-reset-of-fails";
    let launch = "ide/air-ui-fa2b/restart-1-b";
    let left_out = |file: &str, size: u64| format!("{file}: left out, {size} bytes do not fit in the cap of 84 bytes in all");
    assert_eq!(
        kept.cut,
        [
            format!("{launch}/idea.log: kept the last 25 of 29 bytes, because the cap per file is 25 bytes"),
            format!("{launch}/run/run.log: kept the last 25 of 29 bytes, because the cap per file is 25 bytes"),
            left_out(&format!("{launch}/idea.1.log"), 24),
            left_out(&format!("{slow}-daemon-threads.txt"), 14),
            left_out(&format!("{slow}-ide-dump.txt"), 8),
            left_out(&format!("{launch}/jcef/browser.log"), 4),
            format!("{launch}/screenshots/002_heartbeat/IDE frame.png: left out, 27 bytes are above the cap of 25 bytes per file"),
            left_out(&format!("{launch}/screenshots/001_heartbeat/IDE frame.png"), 5),
        ]
    );
    assert!(copy.join("threadDump-before-kill-1792519706000.txt").is_file());
    assert!(copy.join("run/supervisor.log").is_file());
    assert_eq!((kept.files, kept.bytes), (5, 18 + 25 + 25 + 14 + 2));
    let manifest = fs::read_to_string(evidence.destination.join(MANIFEST_FILE)).unwrap();
    assert!(
        manifest.starts_with("lane-run kept this evidence at the hard stop of the monitor: 5 file(s), 84 bytes."),
        "{manifest}"
    );
    assert!(manifest.ends_with(&format!("cut {}\n", kept.cut.last().unwrap())), "{manifest}");
}

// A manifest that cannot be written is named in the line of the copy, because the line would name a file that is not
// there. Each file that cannot be copied is a cut line too.
#[test]
fn a_manifest_that_cannot_be_written_is_named_in_the_line() {
    let (root, run) = run_directory();
    fs::write(root.path().join("outputs"), b"a file where the outputs directory belongs").unwrap();
    let evidence = evidence_of(root.path());

    let kept = keep(&evidence, &run, Moment::RunEnd, Limits::LANE);

    assert_eq!(kept.files, 0);
    assert!(kept.cut.iter().all(|cut| cut.contains(": cannot copy ")), "{:?}", kept.cut);
    let error = kept.manifest_error.clone().expect("the manifest cannot be written");
    assert!(
        error.starts_with(&evidence.destination.join(MANIFEST_FILE).display().to_string()),
        "{error}"
    );
    let line = kept.describe(&evidence.destination);
    assert!(line.ends_with(&format!("and evidence.txt cannot be written: {error}")), "{line}");
}

// An IDE that dies before its logger starts leaves no log directory. The copy still keeps the output of its process, and
// it names the log directory that is gone.
#[test]
fn the_process_output_is_kept_when_the_log_directory_is_gone() {
    let root = TempDir::new().unwrap();
    write(
        &root.path().join("ide/air-ui-fa2b"),
        "run-ide-start-a/run.log",
        b"Error: could not open display",
    );
    let run = events(vec![
        launched(100, "start-a", root.path()),
        started("fails"),
        exited(100),
        finished("fails", "FAILED"),
    ]);
    let evidence = evidence_of(root.path());

    let kept = keep(&evidence, &run, Moment::RunEnd, Limits::LANE);

    assert_eq!(
        kept.cut,
        [format!(
            "ide/air-ui-fa2b/start-a: the log directory {} is gone",
            root.path().join("ide/air-ui-fa2b/log/start-a").display()
        )]
    );
    assert_eq!(kept.files, 1);
    assert_eq!(
        fs::read(evidence.destination.join("ide/air-ui-fa2b/start-a/run/run.log")).unwrap(),
        b"Error: could not open display"
    );
}
