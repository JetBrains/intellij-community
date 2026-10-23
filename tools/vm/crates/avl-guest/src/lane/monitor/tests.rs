use std::cell::RefCell;
use std::fs;
use std::os::unix::fs::symlink;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use avl_junit::performance::Plan;
use avl_wire::daemon::{ExecutionIdentity, IdeExited, IdeLaunched, PlanStarted, RunStarted, TestFinished, TestStarted, TypedFailure};
use avl_wire::program::lane::PerformanceSeries;
use avl_wire::report::ScenarioReport;
use pretty_assertions::assert_eq;
use tempfile::TempDir;

use super::*;
use crate::lane::evidence::Evidence;
use crate::lane::testing::launch_inputs;
use crate::lane::watchdog::{Policy, Watch};
use crate::supervisor::thread_dump;

/// The number of the system call `name` on this architecture.
fn number_of(name: &str) -> i64 {
    let (number, _, _) = SYSCALLS
        .iter()
        .find(|(_, known, _)| *known == name)
        .unwrap_or_else(|| panic!("no system call {name} on this architecture"));
    *number
}

/// Writes one thread `tid` of a fake `/proc/self` under `process`.
fn thread(process: &Path, tid: u32, comm: &str, stat: &str, wchan: &str, syscall: &str) {
    let task = process.join("task").join(tid.to_string());
    fs::create_dir_all(&task).unwrap();
    fs::write(task.join("comm"), format!("{comm}\n")).unwrap();
    fs::write(task.join("stat"), format!("{stat}\n")).unwrap();
    fs::write(task.join("wchan"), wchan).unwrap();
    fs::write(task.join("syscall"), format!("{syscall}\n")).unwrap();
}

// The report names each thread by its id and its name, with its state, its wait channel and its system call. A call on
// a file descriptor names what the descriptor is, and wait4 names its pid. A name with a parenthesis keeps the state.
#[test]
fn each_thread_is_named_with_its_state_its_wait_and_the_target_of_its_descriptor() {
    let root = TempDir::new().unwrap();
    let process = root.path();
    fs::create_dir_all(process.join("fd")).unwrap();
    symlink("pipe:[4026]", process.join("fd/5")).unwrap();
    thread(
        process,
        7,
        "vm-guest-agent",
        "7 (vm-guest-agent) S 1 7 7",
        "futex_wait_queue",
        &format!("{} 0x7f00 0x80 0x0 0x0 0x0 0x0 0x7ffd 0x7f01", number_of("futex")),
    );
    thread(
        process,
        8,
        "lane (trace)",
        "8 (lane (trace)) D 1 7 7",
        "pipe_write",
        &format!("{} 0x5 0x7f02 0x40 0x0 0x0 0x0 0x7ffd 0x7f01", number_of("write")),
    );
    thread(
        process,
        9,
        "lane-watchdog",
        "9 (lane-watchdog) S 1 7 7",
        "do_wait",
        &format!("{} 0x1092 0x0 0x0 0x0 0x0 0x0 0x7ffd 0x7f01", number_of("wait4")),
    );
    thread(process, 10, "lane-monitor", "10 (lane-monitor) R 1 7 7", "0", "running");
    thread(process, 11, "idle", "11 (idle) S 1 7 7", "", "-1 0x7ffd 0x7f01");
    assert_eq!(
        thread_lines(process, Some(12)),
        [
            "lane-run: thread 7 'vm-guest-agent': state S, wchan futex_wait_queue, syscall futex",
            "lane-run: thread 8 'lane (trace)': state D, wchan pipe_write, syscall write on fd 5 -> pipe:[4026]",
            "lane-run: thread 9 'lane-watchdog': state S, wchan do_wait, syscall wait4 of pid 4242",
            "lane-run: thread 10 'lane-monitor': state R, wchan 0, syscall running",
            "lane-run: thread 11 'idle': state S, wchan ?, syscall none",
        ]
    );
    // The thread that writes the report reads its own `syscall` file. It is named as the monitor alone.
    assert_eq!(
        thread_lines(process, Some(10))[3],
        format!("lane-run: thread 10 'lane-monitor': {OWN_THREAD}")
    );
    assert_eq!(
        thread_lines(&root.path().join("absent"), None),
        [format!(
            "lane-run: no thread report: {} has no task directory",
            root.path().join("absent").display()
        )]
    );
}

fn execution(class: &str, scenario: &str) -> ExecutionIdentity {
    ExecutionIdentity {
        id: format!("[engine:lane-daemon]/[suite:suite]/[scenario:{scenario}]"),
        display_name: scenario.to_owned(),
        class_name: Some(class.to_owned()),
        method_name: None,
    }
}

/// The records of a run that planned three scenarios of one class, passed the first, launched an IDE twice, and stopped
/// in the second scenario.
fn cut_off_run() -> Vec<RunEventKind> {
    let class = "com.intellij.air.AcpDynamicFakeAgentRestoreUiTest";
    vec![
        RunEventKind::PlanStarted(PlanStarted {
            timestamp: "t0".to_owned(),
            discovered_executions: 3,
            class_names: vec!["AcpDynamicFakeAgentRestoreUiTest".to_owned()],
            executions: vec![
                execution(class, "restore-loaded"),
                execution(class, "restore-fresh-session"),
                execution(class, "restore-failed"),
            ],
        }),
        RunEventKind::IdeLaunched(IdeLaunched {
            timestamp: None,
            pid: 100,
            launch_name: "start-mve37si6".to_owned(),
            launch_key: "air-gui_chat-fa2b".to_owned(),
            log_dir: "/ide/log".to_owned(),
        }),
        RunEventKind::TestStarted(TestStarted {
            timestamp: "t1".to_owned(),
            execution: execution(class, "restore-loaded"),
        }),
        RunEventKind::IdeExited(IdeExited {
            timestamp: None,
            pid: 100,
            exit_code: 0,
            signal: None,
        }),
        RunEventKind::IdeLaunched(IdeLaunched {
            timestamp: None,
            pid: 101,
            launch_name: "restart-1-mve37si7".to_owned(),
            launch_key: "air-gui_chat-fa2b".to_owned(),
            log_dir: "/ide/log".to_owned(),
        }),
        RunEventKind::TestFinished(TestFinished {
            timestamp: "t2".to_owned(),
            execution: execution(class, "restore-loaded"),
            status: "SUCCESSFUL".to_owned(),
            duration_ms: 36_000,
            failure: TypedFailure::default(),
            report: ScenarioReport::default(),
        }),
        RunEventKind::TestStarted(TestStarted {
            timestamp: "t3".to_owned(),
            execution: execution(class, "restore-fresh-session"),
        }),
    ]
}

/// The run stream file of `records`, one JSON record per line, and a last line that the run thread did not finish.
fn stream_file(directory: &Path, records: &[RunEventKind]) -> PathBuf {
    let mut text: String = records
        .iter()
        .map(|record| format!("{}\n", serde_json::to_string(record).unwrap()))
        .collect();
    text.push_str("{\"event\":\"output\",\"te");
    let file = directory.join("run.ndjson");
    fs::write(&file, text).unwrap();
    file
}

// The JUnit XML of a cut-off run: the scenario that passed, the open one unfinished, and each planned scenario that never
// ran skipped with the reason. A torn last line is left out.
#[test]
fn a_cut_off_run_gets_its_open_case_unfinished_and_its_planned_cases_skipped() {
    let root = TempDir::new().unwrap();
    let file = stream_file(root.path(), &cut_off_run());
    let events = decode_stream(&fs::read(file).unwrap());
    assert_eq!(events.len(), 7);
    let (xml, cases) = partial_xml(events, "the lane budget of 2520 s ended the run before this scenario ran");
    assert_eq!(cases, 3);
    assert!(
        xml.contains(
            r#"<testsuite name="com.intellij.air.AcpDynamicFakeAgentRestoreUiTest" tests="3" failures="1" errors="0" skipped="1""#
        ),
        "{xml}"
    );
    assert!(
        xml.contains(r#"name="restore-fresh-session""#) && xml.contains(r#"type="unfinished""#),
        "{xml}"
    );
    assert!(
        xml.contains(r#"<skipped message="the lane budget of 2520 s ended the run before this scenario ran"/>"#),
        "{xml}"
    );
    assert!(!xml.contains("unreported"), "{xml}");
}

// The IDE runs that a hard stop cancels: each one that an ideLaunched record names and no ideExited record of its pid
// ended.
#[test]
fn an_ide_run_is_unended_until_an_exit_of_its_pid() {
    let events: Vec<RunEvent> = cut_off_run().into_iter().map(RunEvent::synthesized).collect();
    assert_eq!(
        unended_ide_runs(&events),
        [("air-gui_chat-fa2b".to_owned(), "restart-1-mve37si7".to_owned())]
    );
}

/// The thread dump that the fake cancel writes into the log directory of the IDE run, when that directory is there.
const KILL_DUMP: &str = "the threads of the hung IDE";

/// The effects of a hard stop as a test sees them: each cancel, and each kill of a recorder. A cancel writes the thread
/// dump of the IDE run, as the live cancel does.
#[derive(Default)]
struct Recorded {
    canceled: RefCell<Vec<(PathBuf, String)>>,
    killed: RefCell<Vec<u32>>,
}

impl StopEffects for Recorded {
    fn cancel_ide(&self, context: &Path, run_id: &str) -> Result<(), String> {
        self.canceled.borrow_mut().push((context.to_path_buf(), run_id.to_owned()));
        let directory = thread_dump::directory(context, run_id);
        if directory.is_dir() {
            fs::write(directory.join(format!("{}1.txt", thread_dump::FILE_PREFIX)), KILL_DUMP).unwrap();
        }
        Ok(())
    }

    fn recorders(&self) -> Vec<u32> {
        vec![77]
    }

    fn kill_recorder(&self, pid: u32) {
        self.killed.borrow_mut().push(pid);
    }
}

fn daemon(root: &Path) -> Daemon {
    Daemon::new(root.join("state"), "lane-run".to_owned(), 0, launch_inputs(root))
}

// The hard stop writes the JUnit XML of the records so far to each file of the run, cancels the IDE run that the
// stream launched and did not end, kills each recorder, and says so in one line with what the run thread did. Once the
// run thread returned, the XML is its own, and the hard stop writes none.
#[test]
fn the_hard_stop_writes_the_partial_xml_cancels_the_live_ide_run_and_kills_the_recorder() {
    let root = TempDir::new().unwrap();
    let daemon = daemon(root.path());
    let watch = Arc::new(Watch::new(Policy {
        active_execution: Duration::from_mins(30),
        progress_gap: Duration::from_mins(5),
        run_end: Some(Instant::now()),
    }));
    let mut out = Vec::new();
    let stream = super::super::stream::Stream::new(&mut out);
    watch.execution_started(
        &stream,
        execution("com.intellij.air.AcpDynamicFakeAgentRestoreUiTest", "restore-fresh-session"),
    );
    daemon.publish_watch(Some(Arc::clone(&watch)));
    let xml = root.path().join("bazel/test.xml");
    let monitor = Monitor::new(
        &daemon,
        MonitorPaths {
            stream: stream_file(root.path(), &cut_off_run()),
            xml: vec![root.path().join("test.xml"), xml.clone()],
            ide_root: root.path().join("ide"),
            report: None,
            evidence: None,
        },
        Some(Duration::from_secs(2520)),
        None,
        true,
        None,
    );
    let effects = Recorded::default();
    let stop = watch.step("relaunch for ide", || monitor.hard_stop(&effects));
    assert_eq!(stop.cases, Some(3));
    assert!(fs::read_to_string(&xml).unwrap().contains(r#"type="unfinished""#));
    assert_eq!(fs::read(&xml).unwrap(), fs::read(root.path().join("test.xml")).unwrap());
    let context = root.path().join("ide/air-gui_chat-fa2b");
    assert_eq!(
        effects.canceled.borrow().as_slice(),
        [(context.clone(), "run-ide-restart-1-mve37si7".to_owned())]
    );
    assert_eq!(stop.canceled, [format!("{}/run-ide-restart-1-mve37si7", context.display())]);
    assert_eq!((stop.killed_recorders, effects.killed.borrow().clone()), (vec![77], vec![77]));
    assert!(
        stop.line.starts_with(
            "lane-run: the hard stop ended the run: the lane budget of 2520 s and the kill grace of the watchdog \
             passed, and the run thread did not return. AcpDynamicFakeAgentRestoreUiTest.restore-fresh-session runs for "
        ) && stop.line.contains("; step 'relaunch for ide' is open for ")
            && stop
                .line
                .ends_with("; 0 s of the budget left. the JUnit XML holds 3 case(s); 1 IDE run(s) canceled, 1 recorder(s) killed; exit 1"),
        "{}",
        stop.line
    );
    // On a TeamCity agent the hard stop is a build problem too, so a muted case cannot leave the build green.
    assert_eq!(
        stop.build_problem.as_deref(),
        Some(teamcity::build_problem(&stop.line, BUDGET_PROBLEM).as_str())
    );
    assert!(
        stop.build_problem.as_deref().is_some_and(|problem| problem
            .starts_with("##teamcity[buildProblem description='lane-run: the hard stop ended the run")
            && problem.ends_with(" identity='lane-run-budget']")),
        "{:?}",
        stop.build_problem
    );

    fs::remove_file(&xml).unwrap();
    monitor.run_returned();
    let stop = monitor.hard_stop(&Recorded::default());
    assert_eq!(stop.cases, None);
    assert!(!xml.exists(), "the hard stop wrote the XML of a run thread that returned");
    assert!(stop.line.contains("the run thread writes the JUnit XML"), "{}", stop.line);
}

// The hard stop writes the IJ Perf files and the result of the run from the records so far, as the end of the run does.
// The run is not green, the open scenario is a failed test, and the metrics of the scenario that passed keep their file.
#[test]
fn the_hard_stop_writes_the_ij_perf_files_and_the_result_of_the_run() {
    let root = TempDir::new().unwrap();
    let daemon = daemon(root.path());
    let class = "com.intellij.air.AcpDynamicFakeAgentRestoreUiTest";
    let mut records = vec![RunEventKind::RunStarted(RunStarted {
        iteration_id: "it-1".to_owned(),
        daemon_boot_stamp: "boot".to_owned(),
        started_at: "t0".to_owned(),
    })];
    records.extend(cut_off_run().into_iter().map(|record| match record {
        RunEventKind::TestFinished(finished) => RunEventKind::TestFinished(TestFinished {
            report: ScenarioReport {
                metrics: [("loadMs".to_owned(), serde_json::Number::from(420))].into(),
                ..ScenarioReport::default()
            },
            ..finished
        }),
        other => other,
    }));
    let summary = daemon
        .iteration_dir("it-1")
        .join("authored-artifacts/restore/restore-loaded/measure/summary.txt");
    fs::create_dir_all(summary.parent().unwrap()).unwrap();
    fs::write(&summary, "load 420 ms\n").unwrap();
    let outputs = root.path().join("outputs");
    let end = RunEnd {
        lane: "ui-perf".to_owned(),
        plan: Some(Plan {
            ide_build: "271.SNAPSHOT".to_owned(),
            series: vec![PerformanceSeries {
                test_class_name: "AcpDynamicFakeAgentRestoreUiTest".to_owned(),
                scenario: "restore-loaded".to_owned(),
                project: "air/restore".to_owned(),
                method: format!("{class}#restore"),
                artifact_path: "air-restore".to_owned(),
            }],
        }),
        suites: Vec::new(),
        performance_dir: outputs.join("performance"),
        result_file: outputs.join("lane/run-result.json"),
        team_city: true,
    };
    let monitor = Monitor::new(
        &daemon,
        MonitorPaths {
            stream: stream_file(root.path(), &records),
            xml: vec![root.path().join("test.xml")],
            ide_root: root.path().join("ide"),
            report: None,
            evidence: None,
        },
        Some(Duration::from_secs(2520)),
        None,
        true,
        Some(&end),
    );
    let ended = monitor
        .hard_stop(&Recorded::default())
        .ended
        .expect("the hard stop writes the end of the run");
    let file = outputs.join("performance/air-restore/metrics.performance.json");
    let document: serde_json::Value = serde_json::from_str(&fs::read_to_string(&file).unwrap()).unwrap();
    assert_eq!(document["metrics"], serde_json::json!([{"n": "loadMs", "d": 420, "v": 420}]));
    assert_eq!(
        ended.messages,
        [format!("##teamcity[publishArtifacts '{} => air-restore']", file.display())]
    );
    let result: serde_json::Value = serde_json::from_str(&fs::read_to_string(&end.result_file).unwrap()).unwrap();
    assert_eq!(result["green"], false);
    assert_eq!(
        result["failedTests"],
        serde_json::json!([{"test": format!("{class} restore-fresh-session"), "failure": avl_junit::cases::UNFINISHED_MESSAGE}])
    );
    assert_eq!(result["summaries"], serde_json::json!({"restore-loaded/measure": "load 420 ms\n"}));
    assert_eq!(result["performance"][0]["artifactPath"], "air-restore");
    assert_eq!(
        ended.lines.last().map(String::as_str),
        Some(format!("the result of the run: {}", end.result_file.display()).as_str())
    );
}

// Under a Bazel test the hard stop keeps the evidence of the run after its cancels, so the thread dump that the cancel
// of the hung IDE run wrote is among the files. Its line says what it kept.
#[test]
fn the_hard_stop_keeps_the_evidence_of_the_ide_run_that_it_canceled() {
    let root = TempDir::new().unwrap();
    let daemon = daemon(root.path());
    let log_dir = root.path().join("ide/air-gui_chat-fa2b/log/restart-1-mve37si7");
    fs::create_dir_all(&log_dir).unwrap();
    fs::write(log_dir.join("idea.log"), "the log of the hung IDE").unwrap();
    let records: Vec<RunEventKind> = cut_off_run()
        .into_iter()
        .map(|record| match record {
            RunEventKind::IdeLaunched(launched) => RunEventKind::IdeLaunched(IdeLaunched {
                log_dir: root
                    .path()
                    .join("ide")
                    .join(&launched.launch_key)
                    .join("log")
                    .join(&launched.launch_name)
                    .display()
                    .to_string(),
                ..launched
            }),
            other => other,
        })
        .collect();
    let evidence = Evidence::new(&root.path().join("state"), &root.path().join("ide"), &root.path().join("outputs"));
    let monitor = Monitor::new(
        &daemon,
        MonitorPaths {
            stream: stream_file(root.path(), &records),
            xml: Vec::new(),
            ide_root: root.path().join("ide"),
            report: None,
            evidence: Some(evidence.clone()),
        },
        Some(Duration::from_secs(2520)),
        None,
        false,
        None,
    );

    let stop = monitor.hard_stop(&Recorded::default());

    let kept = stop.kept.expect("the hard stop kept the evidence");
    assert_eq!((kept.files, kept.cut.len()), (2, 0), "{:?}", kept.cut);
    let copy = evidence.destination.join("ide/air-gui_chat-fa2b/restart-1-mve37si7");
    assert_eq!(fs::read_to_string(copy.join("idea.log")).unwrap(), "the log of the hung IDE");
    assert_eq!(fs::read_to_string(copy.join("threadDump-before-kill-1.txt")).unwrap(), KILL_DUMP);
    assert!(
        stop.line.ends_with(&format!(
            "1 recorder(s) killed; kept 2 evidence file(s) of 50 bytes in {}, and cut or left out 0, which evidence.txt \
             names; exit 1",
            evidence.destination.display()
        )),
        "{}",
        stop.line
    );
}

// The hang report of a heartbeat starts with the run as the watch knows it, and a run between two runs says so.
#[test]
fn the_hang_report_starts_with_the_run_as_the_watch_knows_it() {
    let root = TempDir::new().unwrap();
    let daemon = daemon(root.path());
    let monitor = Monitor::new(
        &daemon,
        MonitorPaths {
            stream: root.path().join("run.ndjson"),
            xml: Vec::new(),
            ide_root: root.path().join("ide"),
            report: None,
            evidence: None,
        },
        None,
        None,
        false,
        None,
    );
    assert!(
        monitor.hard_stop(&Recorded::default()).build_problem.is_none(),
        "a run off TeamCity writes no build problem"
    );
    let absent = root.path().join("absent");
    assert_eq!(
        monitor.report(&absent)[0],
        "lane-run: hang report: no scenario runs; no step is open; the run has no budget"
    );
}

// On Linux the report reads this process. The thread that writes it reads its own system call file, so it is named as
// the monitor, and not by a read on a descriptor that is closed by then.
#[cfg(target_os = "linux")]
#[test]
fn the_report_of_this_process_names_the_thread_that_writes_it_as_the_monitor() {
    let own = own_thread().expect("/proc/thread-self names this thread");
    let lines = thread_lines(Path::new(PROC_SELF), Some(own));
    let line = format!("lane-run: thread {own} '");
    assert!(
        lines
            .iter()
            .any(|written| written.starts_with(&line) && written.ends_with(OWN_THREAD)),
        "{lines:#?}"
    );
}
