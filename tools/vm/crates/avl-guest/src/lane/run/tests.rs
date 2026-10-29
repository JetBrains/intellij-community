use std::collections::BTreeMap;
use std::path::PathBuf;
use std::sync::Arc;
use std::sync::atomic::{AtomicUsize, Ordering};

use std::ffi::OsString;

use pretty_assertions::assert_eq;

use super::*;
use crate::cli::{Verb, parse};
use crate::lane::build_log::tests::{finished, line, plan, started, summary as summary_of};
use crate::lane::stream::CaseStatus;

/// The arguments of `lane-run --lane ui --descriptor /r/d.json` and `extra`.
fn args(extra: &[&str]) -> LaneRunArgs {
    let argv: Vec<OsString> = ["vm-guest-agent", "lane-run", "--lane", "ui", "--descriptor", "/r/d.json"]
        .into_iter()
        .chain(extra.iter().copied())
        .map(OsString::from)
        .collect();
    match parse(&argv).unwrap().verb {
        Verb::LaneRun(args) => args,
        other => panic!("lane-run parsed as {other:?}"),
    }
}

fn environment(pairs: &[(&str, &str)]) -> impl Fn(&str) -> Option<String> {
    let pairs: BTreeMap<String, String> = pairs
        .iter()
        .map(|(name, value)| ((*name).to_owned(), (*value).to_owned()))
        .collect();
    move |name| pairs.get(name).cloned()
}

// The run directory is --runtime-root, then $AIR_LANE_RUNTIME_ROOT, then $TEST_TMPDIR/air-lane. A run with none of them
// is refused, and so is a relative one.
#[test]
fn the_run_directory_comes_from_the_flag_the_variable_or_the_test_directory() {
    let none = environment(&[]);
    assert_eq!(
        runtime_root(
            &args(&["--runtime-root", "/flag"]),
            &environment(&[("AIR_LANE_RUNTIME_ROOT", "/variable")])
        )
        .unwrap(),
        PathBuf::from("/flag")
    );
    assert_eq!(
        runtime_root(
            &args(&[]),
            &environment(&[("AIR_LANE_RUNTIME_ROOT", "/variable"), ("TEST_TMPDIR", "/tmp/t")])
        )
        .unwrap(),
        PathBuf::from("/variable")
    );
    assert_eq!(
        runtime_root(&args(&[]), &environment(&[("TEST_TMPDIR", "/tmp/t")])).unwrap(),
        PathBuf::from("/tmp/t/air-lane")
    );
    assert_eq!(runtime_root(&args(&[]), &none).unwrap_err().code, "lane_run_root_missing");
    assert_eq!(
        runtime_root(&args(&[]), &environment(&[("AIR_LANE_RUNTIME_ROOT", "relative")]))
            .unwrap_err()
            .code,
        "lane_run_root_missing"
    );
}

// A Bazel --test_filter adds its selectors after the flags, and $JB_TEST_JUNIT5_FILTERS adds its filters after the flags.
#[test]
fn the_selectors_and_the_filters_come_from_the_flags_and_the_environment() {
    let parsed = args(&[
        "--selector",
        "AirRenameSessionGeneratedFlowUiTest",
        "--junit5-filter",
        "exclude-classname=.*Slow",
    ]);
    let variable = environment(&[
        ("TESTBRIDGE_TEST_ONLY", "a.AirComposerGeneratedFlowUiTest#one, AirGenUiHostUiTest"),
        ("JB_TEST_JUNIT5_FILTERS", "include-package=com.intellij.air;"),
    ]);
    assert_eq!(
        selectors(&parsed, &variable),
        [
            "AirRenameSessionGeneratedFlowUiTest",
            "a.AirComposerGeneratedFlowUiTest#one",
            "AirGenUiHostUiTest"
        ]
    );
    assert_eq!(
        junit5_filters(&parsed, &variable),
        ["exclude-classname=.*Slow", "include-package=com.intellij.air"]
    );
    assert_eq!(selectors(&args(&[]), &environment(&[])), Vec::<String>::new());
}

fn summary(has_failures: bool) -> String {
    format!(
        r#"{{"event":"summary","iterationId":"i","daemonBootStamp":"b","startedAt":"s","testsStarted":1,"testsFailed":0,"testsSkipped":0,"containersSkipped":0,"containerFailures":0,"hasFailures":{has_failures},"ideRunning":false}}"#
    )
}

// A run is green only with a summary of no failure and no failed run. A stream that was cut off has no summary, and a
// record that does not decode is no green record.
#[test]
fn only_a_summary_without_failures_is_green() {
    let note = r#"{"event":"output","text":"lane ui: ide launch"}"#;
    assert!(is_green(format!("{note}\n{}\n", summary(false)).as_bytes()));
    assert!(!is_green(format!("{note}\n{}\n", summary(true)).as_bytes()));
    assert!(!is_green(format!("{note}\n").as_bytes()));
    assert!(!is_green(format!("{note}\nnot a record\n{}\n", summary(false)).as_bytes()));
}

/// The steps of an end, which each say that they ran. The end answers `green`, the XML write fails on `refuse_xml`, and
/// the stream failed an attempt on `failed_an_attempt`.
struct FakeEnding {
    ran: Vec<&'static str>,
    green: bool,
    refuse_xml: bool,
    failed_an_attempt: bool,
}

impl Ending for FakeEnding {
    fn write_xml(&mut self) -> Result<(), AgentRefusal> {
        self.ran.push("xml");
        if self.refuse_xml {
            return Err(AgentRefusal::failure(BOOT_CODE, "cannot write the JUnit XML"));
        }
        Ok(())
    }

    fn quit(&mut self) -> Result<(), AgentRefusal> {
        self.ran.push("quit");
        Ok(())
    }

    fn end(&mut self, quit: &Result<(), AgentRefusal>) -> Result<bool, AgentRefusal> {
        self.ran.push("end");
        Ok(self.green && quit.is_ok())
    }

    fn failed_an_attempt(&self) -> bool {
        self.failed_an_attempt
    }

    fn keep_evidence(&mut self) {
        self.ran.push("evidence");
    }
}

/// The steps that [`finish`] runs over a [`FakeEnding`]: its outcome, the steps that ran, and the `timing` records.
fn finished_steps(green: bool, refuse_xml: bool, failed_an_attempt: bool) -> (Result<bool, String>, Vec<&'static str>, Vec<String>) {
    let mut stream = format!("{}\n", summary(false)).into_bytes();
    let mut ending = FakeEnding {
        ran: Vec::new(),
        green,
        refuse_xml,
        failed_an_attempt,
    };
    let outcome = finish(&mut stream, &mut ending);
    let text = String::from_utf8(stream.clone()).unwrap();
    let timed: Vec<String> = text
        .lines()
        .skip(1)
        .map(|line| match decode_run_event(line.as_bytes()).unwrap().kind {
            RunEventKind::Timing(timing) if timing.subject.is_none() => timing.step,
            other => panic!("{other:?}"),
        })
        .collect();
    assert!(is_green(&stream), "{text}");
    (outcome.map_err(|refusal| refusal.code.into_owned()), ending.ran, timed)
}

// A run that its rerun made green keeps the evidence of the failed attempt, as a run that is not green does, and it
// stays green.
#[test]
fn a_run_that_its_rerun_made_green_keeps_the_evidence_of_its_failed_attempt() {
    let (outcome, ran, timed) = finished_steps(true, false, true);
    assert_eq!(outcome, Ok(true));
    assert_eq!(ran, ["xml", "quit", "end", "evidence"]);
    assert_eq!(timed.last().map(String::as_str), Some("lane-run-evidence"));
}

// After the summary of its iteration, lane-run runs its own steps in order and writes the `timing` record of each after
// it: the XML, the quit, the end, and the evidence of a run that is not green. Each record has no subject, and a stream
// with them is still green. A JUnit XML that cannot be written is the refusal of the run, after every step ran.
#[test]
fn the_steps_after_the_iteration_are_timing_records_after_the_summary() {
    let steps = |green: bool, refuse_xml: bool| finished_steps(green, refuse_xml, false);
    assert_eq!(
        steps(true, false),
        (
            Ok(true),
            vec!["xml", "quit", "end"],
            vec!["lane-run-xml".to_owned(), "lane-run-quit".to_owned(), "lane-run-end".to_owned()]
        )
    );
    let red = ["lane-run-xml", "lane-run-quit", "lane-run-end", "lane-run-evidence"]
        .map(str::to_owned)
        .to_vec();
    assert_eq!(
        steps(false, false),
        (Ok(false), vec!["xml", "quit", "end", "evidence"], red.clone())
    );
    assert_eq!(
        steps(false, true),
        (Err(BOOT_CODE.to_owned()), vec!["xml", "quit", "end", "evidence"], red)
    );
}

// The JUnit XML goes into the run directory, and to the file of a Bazel test: $JB_XML_OUTPUT_FILE before
// $XML_OUTPUT_FILE.
#[test]
fn the_junit_xml_goes_to_the_run_directory_and_to_the_file_of_the_bazel_test() {
    let root = tempfile::TempDir::new().unwrap();
    let bazel = root.path().join("bazel/test.xml");
    let named = root.path().join("named/report.xml");
    let xml = "<testsuites/>\n";
    write_xml(root.path(), xml, &environment(&[("XML_OUTPUT_FILE", bazel.to_str().unwrap())])).unwrap();
    assert_eq!(fs::read_to_string(root.path().join("test.xml")).unwrap(), xml);
    assert_eq!(fs::read_to_string(&bazel).unwrap(), xml);
    write_xml(
        root.path(),
        xml,
        &environment(&[
            ("XML_OUTPUT_FILE", bazel.to_str().unwrap()),
            ("JB_XML_OUTPUT_FILE", named.to_str().unwrap()),
        ]),
    )
    .unwrap();
    assert_eq!(fs::read_to_string(&named).unwrap(), xml);
}

// A lane IDE takes the fixed bridge port, so a lane test runs as one shard. A run of more shards is refused before it
// prepares anything, and one shard is no split.
#[test]
fn a_run_of_more_than_one_shard_is_refused() {
    let refused = run(&args(&[]), &environment(&[("TEST_TOTAL_SHARDS", "6"), ("TEST_SHARD_INDEX", "2")])).unwrap_err();
    assert_eq!(refused.code, SHARDED_CODE);
    assert!(refused.message.contains("6 shards"), "{}", refused.message);
    refuse_shards(&environment(&[("TEST_TOTAL_SHARDS", "1")])).unwrap();
    refuse_shards(&environment(&[])).unwrap();
}

// A run under a Bazel test that lane-run refuses before its end writes a run result that names the refusal, so the
// TeamCity step sends why no scenario ran. A result that is there already stays, and a plan writes none.
#[test]
fn a_refused_run_under_a_bazel_test_writes_its_refusal_as_the_run_result() {
    let outputs = tempfile::tempdir().unwrap();
    let directory = outputs.path().to_str().unwrap().to_owned();
    let variables = [("TEST_TOTAL_SHARDS", "6"), ("TEST_UNDECLARED_OUTPUTS_DIR", directory.as_str())];
    let refused = run(&args(&[]), &environment(&variables)).unwrap_err();
    let file = run_result::path(Some(outputs.path()), outputs.path());
    let document: serde_json::Value = serde_json::from_str(&fs::read_to_string(&file).unwrap()).unwrap();
    assert_eq!(document["lane"], "ui");
    assert_eq!(document["green"], false);
    assert_eq!(document["refusal"]["code"], SHARDED_CODE);
    assert_eq!(document["refusal"]["message"], refused.message.as_str());

    fs::write(&file, "{}").unwrap();
    run(&args(&[]), &environment(&variables)).unwrap_err();
    assert_eq!(fs::read_to_string(&file).unwrap(), "{}", "the result of the end of a run stays");

    fs::remove_file(&file).unwrap();
    run(&args(&["--plan-only"]), &environment(&variables)).unwrap_err();
    assert!(!file.exists(), "a plan writes no run result");
}

/// The `--jvm_flag` arguments of the UI job of TeamCity build 1097187017, for the runner with index `index`.
fn teamcity_flags(index: u32) -> Vec<String> {
    [
        "-Djava.awt.headless=false".to_owned(),
        format!("-Didea.test.runner.index={index}"),
        "-Didea.test.runners.count=2".to_owned(),
        "-Didea.bucketing.season=AirPluginGuiChatTests".to_owned(),
        "-Didea.tests.duration.bucketing.enabled=true".to_owned(),
    ]
    .to_vec()
}

// The UI job of TeamCity passes the flags of the JUnit runner with --test_arg=--jvm_flag=<flag>. lane-run takes the
// joined form and the two-token form, repeated, in their order.
#[test]
fn lane_run_takes_the_jvm_flags_of_the_teamcity_ui_job_in_both_forms() {
    let joined: Vec<String> = teamcity_flags(0).iter().map(|flag| format!("--jvm_flag={flag}")).collect();
    let joined: Vec<&str> = joined.iter().map(String::as_str).collect();
    assert_eq!(args(&joined).jvm_flags, teamcity_flags(0));

    let split = args(&[
        "--jvm_flag",
        "-Didea.test.runner.index=1",
        "--jvm_flag",
        "-Didea.test.runners.count=2",
    ]);
    assert_eq!(split.jvm_flags, ["-Didea.test.runner.index=1", "-Didea.test.runners.count=2"]);
    assert_eq!(args(&[]).jvm_flags, Vec::<String>::new());
}

// The runner index and count of the job split the lane, and each ignored flag gives one note in the order of the
// flags. A run without flags is the whole lane, with no note.
#[test]
fn the_runner_flags_split_the_lane_and_each_ignored_flag_gives_one_note() {
    let flags = jvm_flags(&teamcity_flags(1)).unwrap();

    assert_eq!(flags.split, RunnerSplit { index: 1, count: 2 });
    assert_eq!(
        flags.notes,
        [
            "lane-run ignores the JVM flag -Djava.awt.headless=false: the lane IDE runs headed on the display of the run",
            "lane-run ignores the JVM flag -Didea.bucketing.season=AirPluginGuiChatTests: lane-run splits the lane by the \
             order of its suites, not by duration buckets",
            "lane-run ignores the JVM flag -Didea.tests.duration.bucketing.enabled=true: lane-run splits the lane by the \
             order of its suites, not by duration buckets",
        ]
    );
    assert_eq!(jvm_flags(&teamcity_flags(0)).unwrap().split, RunnerSplit { index: 0, count: 2 });
    let one = ["-Didea.test.runner.index=0".to_owned(), "-Didea.test.runners.count=1".to_owned()];
    assert_eq!(jvm_flags(&one).unwrap().split, RunnerSplit::WHOLE);
    assert_eq!(
        jvm_flags(&[]).unwrap(),
        JvmFlags {
            split: RunnerSplit::WHOLE,
            notes: Vec::new(),
            allure: false,
        }
    );
}

// The lane flag of the Allure results is a JVM flag of lane-run too, so a person asks for them for one Bazel run. Only
// the value `on` asks, and a run without the flag writes no Allure result.
#[test]
fn the_allure_flag_asks_for_the_allure_results_of_one_run() {
    let mut flags = teamcity_flags(1);
    assert!(!jvm_flags(&flags).unwrap().allure);
    flags.push(inputs::ALLURE_FLAG.to_owned());
    let asked = jvm_flags(&flags).unwrap();
    assert_eq!(
        (asked.allure, asked.split, asked.notes.len()),
        (true, RunnerSplit { index: 1, count: 2 }, 3)
    );
    let refusal = jvm_flags(&["-Dair.flow.trace.allure=true".to_owned()]).unwrap_err();
    assert_eq!(refusal.code, JVM_FLAG_CODE);
    assert_eq!(refusal.message, "the JVM property air.flow.trace.allure takes only the value on");
}

// Another flag is refused by name, so the next flag of a job is loud. The refusal of an unknown property names it and
// never its value. A split that is not one is refused too.
#[test]
fn an_unknown_jvm_flag_or_a_split_that_is_not_one_is_refused_by_name() {
    let refusal = |flags: &[&str]| {
        let flags: Vec<String> = flags.iter().map(|flag| (*flag).to_owned()).collect();
        let refusal = jvm_flags(&flags).unwrap_err();
        assert_eq!(refusal.code, JVM_FLAG_CODE, "{flags:?}");
        refusal.message
    };

    let unknown = refusal(&["-Didea.some.token=s3cr3t-value"]);
    assert!(
        unknown.contains("idea.some.token") && !unknown.contains("s3cr3t-value"),
        "{unknown}"
    );
    assert!(
        unknown.contains("idea.test.runner.index, idea.test.runners.count and air.flow.trace.allure"),
        "{unknown}"
    );
    assert!(refusal(&["-Xmx2g"]).contains("the flag -Xmx2g is none"));
    // The screen flag is no JVM flag. A direct lane reads the screen only on a TeamCity build, never on a developer host.
    assert!(refusal(&["-Dair.flow.trace.screen=on"]).contains("air.flow.trace.screen"));
    assert!(refusal(&["-D"]).contains("takes only a -D<name>=<value> property"));
    assert!(refusal(&["-Didea.test.runner.index=0"]).contains("needs both"));
    assert!(refusal(&["-Didea.test.runners.count=2"]).contains("needs both"));
    assert!(refusal(&["-Didea.test.runner.index=2", "-Didea.test.runners.count=2"]).contains("is not below the runner count 2"));
    assert!(refusal(&["-Didea.test.runner.index=0", "-Didea.test.runners.count=0"]).contains("is not below"));
    assert!(refusal(&["-Didea.test.runner.index=one", "-Didea.test.runners.count=2"]).contains("no whole number"));
    assert!(
        refusal(&[
            "-Didea.test.runner.index=0",
            "-Didea.test.runner.index=1",
            "-Didea.test.runners.count=2"
        ])
        .contains("has two values")
    );
}

// lane-run proves the host only where nothing proved it yet: on Linux, for a run that launches an IDE, and on a host
// that no controller prepared. A worker of the controller names the run secrets directory. validate-guest proved the
// display of that worker at its boot, and its lane daemon proves the shared objects of the distribution at its own
// boot, so a run there pays nothing.
#[test]
fn only_a_linux_run_on_a_host_that_no_controller_prepared_probes_the_host() {
    let host = environment(&[]);
    let worker = environment(&[("AIR_VM_RUN_SECRETS", "/dev/shm/air-run-secrets")]);

    assert!(host_probe_applies(&args(&[]), &host, true));
    assert!(host_probe_applies(&args(&[]), &environment(&[("AIR_VM_RUN_SECRETS", " ")]), true));
    assert!(!host_probe_applies(&args(&[]), &worker, true));
    assert!(!host_probe_applies(&args(&["--plan-only"]), &host, true));
    assert!(!host_probe_applies(&args(&[]), &host, false));
}

// No person works on the X display of a TeamCity agent on Linux, so the recorder may read it. Each missing condition
// keeps the screen closed, and a developer host is no TeamCity build.
#[test]
fn only_a_teamcity_build_on_linux_with_a_display_lets_the_recorder_read_the_screen() {
    let build = [("TEAMCITY_VERSION", "2026.1"), ("DISPLAY", ":0.0")];

    assert!(build_owns_screen(&environment(&build), true));
    assert!(!build_owns_screen(&environment(&build), false));
    assert!(!build_owns_screen(&environment(&[("DISPLAY", ":0.0")]), true));
    assert!(!build_owns_screen(
        &environment(&[("TEAMCITY_VERSION", " "), ("DISPLAY", ":0.0")]),
        true
    ));
    assert!(!build_owns_screen(&environment(&[("TEAMCITY_VERSION", "2026.1")]), true));
    assert!(!build_owns_screen(
        &environment(&[("TEAMCITY_VERSION", "2026.1"), ("DISPLAY", " ")]),
        true
    ));
    let developer = environment(&[("DISPLAY", ":1"), ("TEST_UNDECLARED_OUTPUTS_DIR", "/testlogs/outputs")]);
    assert!(!build_owns_screen(&developer, true));
}

// AIR_LANE_VIDEO=failed keeps the video of a scenario that did not pass only. Unset or empty, every scenario keeps its
// video, so a TeamCity build keeps every video until its config sets the variable. Another value is refused by name.
#[test]
fn air_lane_video_keeps_every_video_unless_it_says_failed() {
    let retention = |value: Option<&str>| {
        let pairs: Vec<(&str, &str)> = value.map(|value| (VIDEO_VARIABLE, value)).into_iter().collect();
        video_retention(&environment(&pairs))
    };
    assert_eq!(retention(None).unwrap(), VideoRetention::Always);
    assert_eq!(retention(Some(" ")).unwrap(), VideoRetention::Always);
    assert_eq!(retention(Some("always")).unwrap(), VideoRetention::Always);
    assert_eq!(retention(Some("failed")).unwrap(), VideoRetention::Failed);
    assert_eq!(retention(Some(" failed\n")).unwrap(), VideoRetention::Failed);

    let refusal = retention(Some("passed")).unwrap_err();
    assert_eq!(refusal.code, VIDEO_CODE);
    assert_eq!(
        refusal.message,
        r#"$AIR_LANE_VIDEO is "passed", and it takes only always or failed"#
    );
}

// The lane IDE opens its windows on an X display, so a host without DISPLAY is refused by name before the probe.
#[test]
fn a_host_without_a_display_is_refused_by_name() {
    assert_eq!(host_display(&environment(&[("DISPLAY", ":0")])).unwrap(), ":0");

    let refusal = host_display(&environment(&[("DISPLAY", "")])).unwrap_err();

    assert_eq!(refusal.code, CODE_DISPLAY_MISSING);
    assert!(refusal.message.contains("DISPLAY is unset"), "{}", refusal.message);
    assert_eq!(host_display(&environment(&[])).unwrap_err().code, CODE_DISPLAY_MISSING);
}

// The budget of a run is the smaller of --run-budget-sec and $TEST_TIMEOUT, less the teardown reserve. A run that names
// neither has no budget. A $TEST_TIMEOUT that is no whole number, and a budget that the reserve leaves nothing of, are
// refused.
#[test]
fn the_run_budget_is_the_smaller_of_the_flag_and_test_timeout_less_the_reserve() {
    let none = environment(&[]);
    let reserve = TEARDOWN_RESERVE.as_secs();
    let budget = |flags: &[&str], variables: &[(&str, &str)]| run_budget(&args(flags), &environment(variables));
    assert_eq!(budget(&[], &[]).unwrap(), None);
    assert_eq!(
        budget(&["--run-budget-sec", "2700"], &[]).unwrap(),
        Some(Duration::from_secs(2700 - reserve))
    );
    assert_eq!(
        budget(&[], &[("TEST_TIMEOUT", "14400")]).unwrap(),
        Some(Duration::from_secs(14_400 - reserve))
    );
    assert_eq!(
        budget(&["--run-budget-sec", "2700"], &[("TEST_TIMEOUT", "14400")]).unwrap(),
        Some(Duration::from_secs(2700 - reserve))
    );
    assert_eq!(
        budget(&["--run-budget-sec", "2700"], &[("TEST_TIMEOUT", "900")]).unwrap(),
        Some(Duration::from_secs(900 - reserve))
    );
    assert_eq!(budget(&[], &[("TEST_TIMEOUT", "an hour")]).unwrap_err().code, BUDGET_CODE);
    let short = run_budget(&args(&["--run-budget-sec", "180"]), &none).unwrap_err();
    assert_eq!(short.code, BUDGET_CODE);
    assert_eq!(
        short.message,
        "the run budget of 180 s leaves nothing after the teardown reserve of 180 s"
    );
}

fn expired(reason: &str, active: Option<(&str, &str)>) -> RunEvent {
    RunEvent {
        raw: None,
        kind: RunEventKind::WatchdogExpired(avl_wire::daemon::WatchdogExpired {
            reason: reason.to_owned(),
            deadline: "2026-10-19T05:42:20.794Z".to_owned(),
            expired_at: "2026-10-19T05:42:20.795Z".to_owned(),
            detail: None,
            active_execution: active.map(|(class, scenario)| avl_wire::daemon::ExecutionIdentity {
                id: format!("[engine:lane-daemon]/[suite:s]/[scenario:{scenario}]"),
                display_name: scenario.to_owned(),
                class_name: Some(class.to_owned()),
                method_name: Some("sessionToolWindowComposerScenarios".to_owned()),
            }),
            evidence: Vec::new(),
        }),
    }
}

// On a TeamCity agent, a run that its budget ended is a build problem that names the case and the budget, because a
// muted case cannot fail the build. Another expiry, and a run off TeamCity, are no build problem.
#[test]
fn a_run_that_its_budget_ended_is_a_teamcity_build_problem_with_the_case_and_the_budget() {
    let team_city = environment(&[("TEAMCITY_VERSION", "2026.1")]);
    let budget = Some(Duration::from_mins(42));
    let case = Some((
        "com.intellij.air.integration.flow.AgentSessionToolWindowComposerUiTest",
        "the 'second' frame",
    ));
    assert_eq!(
        budget_problem(&[expired("run_budget_timeout", case)], budget, &team_city).as_deref(),
        Some(
            "##teamcity[buildProblem description='lane-run: the lane budget of 2520 s ended the run in \
             AgentSessionToolWindowComposerUiTest.the |'second|' frame; the scenarios after it did not run' \
             identity='lane-run-budget']"
        )
    );
    assert_eq!(
        budget_problem(&[expired("run_budget_timeout", None)], budget, &team_city).as_deref(),
        Some(
            "##teamcity[buildProblem description='lane-run: the lane budget of 2520 s ended the run between two \
             scenarios; the scenarios after it did not run' identity='lane-run-budget']"
        )
    );
    assert_eq!(budget_problem(&[expired("progress_gap_timeout", case)], budget, &team_city), None);
    assert_eq!(
        budget_problem(&[expired("run_budget_timeout", case)], budget, &environment(&[])),
        None
    );
}

// The outputs directory of a Bazel test is its `TEST_UNDECLARED_OUTPUTS_DIR`. lane-run roots the trace bundles and the
// hang report there. An empty or absent value names no directory.
#[test]
fn the_outputs_directory_of_a_bazel_test_is_its_variable() {
    let outputs = |value: Option<&str>| bazel_outputs(&|name| value.filter(|_| name == "TEST_UNDECLARED_OUTPUTS_DIR").map(str::to_owned));
    assert_eq!(outputs(Some("/testlogs/outputs")), Some(PathBuf::from("/testlogs/outputs")));
    assert_eq!(outputs(Some("")), None);
    assert_eq!(outputs(None), None);
}

/// A build log file that refuses each write, and counts the writes.
struct Refusing(Arc<AtomicUsize>);

impl Write for Refusing {
    fn write(&mut self, _: &[u8]) -> io::Result<usize> {
        self.0.fetch_add(1, Ordering::SeqCst);
        Err(io::Error::other("no space left on the device"))
    }

    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

// A failed write to the build log channel turns the channel off after one line of the log. The run file still gets each
// record, so a green run stays green.
#[test]
fn a_failed_write_to_the_build_log_keeps_the_run_file_whole_and_green() {
    let writes = Arc::new(AtomicUsize::new(0));
    let channel = BuildLog::to(
        Box::new(Refusing(writes.clone())),
        PathBuf::from("/agent/temp/air-lane-live/live.log"),
        "ui",
        None,
        PathBuf::new(),
    );
    let mut tee = Tee {
        file: Vec::new(),
        out: io::sink(),
        build_log: Some(channel),
    };
    let records = [
        line(&plan(2)),
        line(&started("passes")),
        line(&finished("passes", CaseStatus::Successful, 1000)),
        line(&started("again")),
        line(&finished("again", CaseStatus::Successful, 1000)),
        line(&summary_of(2, 0)),
    ];

    for record in &records {
        tee.write_all(record).unwrap();
    }

    assert_eq!(tee.file, records.concat());
    assert!(is_green(&tee.file), "the run file gives the verdict of the run");
    assert_eq!(writes.load(Ordering::SeqCst), 1, "the channel is off after its first failed write");
}
