//! The verb `lane-run`: one iteration of a lane from a runfiles tree, with no control channel.
//!
//! `lane-daemon` serves the channel in the run slot of a worker, and the controller pushes the lane inputs to it.
//! `lane-run` is the second front door of the same library, for a host with the runfiles of the lane rule: a Bazel test,
//! `bt --lane` and a TeamCity agent. It reads the runtime descriptor, stores the lane inputs and the context plugins of
//! the runfiles in the jar store by digest, and runs the iteration of [`super::iteration`] as a `/run` would. It writes
//! the run stream on standard output and into the run directory, and the JUnit XML of the stream into the run
//! directory and to the XML file of a Bazel test. After the summary, the stream gets one `timing` record for each step of
//! its end. It writes the IJ Perf files of the run, as [`super::performance`]
//! states, and the result of the run, as [`super::run_result`] states. It quits the IDE, and exits 0 when the run is
//! green. Under a Bazel test, a run that is not green keeps its evidence in the outputs directory of the test, as
//! [`super::evidence`] states. On a TeamCity build that names a build log file, the run shows in the build log while it
//! goes on, as [`super::build_log`] states. [`VIDEO_VARIABLE`] can keep the video of a scenario that did not pass only,
//! as [`video_retention`] states. A TeamCity build on Linux lets the recorder read the screen, and the run then starts an
//! X display of its own, as [`super::own_display`] states. Each failed scenario of the lane IDE runs once more at the
//! end of the suites of the lane IDE, as [`super::rerun`] states.
//!
//! With `--plan-only` it stops before the IDE: it stages the inputs and selects the documents as a run does, and it
//! writes the [`RunPlan`] on standard output. So a host proves a lane rule and its runfiles without a lane IDE.

use std::collections::BTreeMap;
use std::fs::{self, File};
use std::io::{self, Write};
use std::net::{IpAddr, Ipv4Addr};
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use avl_junit::cases::simple_class_name;
use avl_junit::performance::{Plan, rfc_1123};
use avl_junit::teamcity;
use avl_trace::protocol::VideoRetention;
use avl_wire::daemon::{
    IdePlugin, LaneInputs, RUN_SECRETS_VARIABLE, RunEvent, RunEventKind, RunRequest, SessionLog, TimedStep, decode_run_event,
};
use avl_wire::runtime::{self, RuntimeFile};
use avl_wire::session_log;
use serde::Serialize;

use super::build_log::BuildLog;
use super::channel::{self, BOOT_CODE, log};
use super::evidence::{self, Evidence, Limits, Moment};
use super::inputs::{self, LaunchInputs, guest_platform};
use super::iteration::{self, RunnerSplit};
use super::monitor::{self, BUDGET_PROBLEM, LiveStop, MONITOR_THREAD, Monitor, MonitorPaths};
use super::own_display;
use super::package::sha256_hex;
use super::performance;
use super::rerun;
use super::run_result::{self, RunEnd};
use super::watchdog::{ExpiryReason, KILL_GRACE};
use crate::cli::{LaneDaemonArgs, LaneRunArgs, ValidateGuestArgs};
use crate::ide::launch::CODE_DISPLAY_MISSING;
use crate::linux::DISPLAY_VARIABLE;
use crate::linux::validate::GuestValidator;
use crate::reply::{AgentRefusal, AgentRefusalExt};
use crate::step::SystemRunner;

#[cfg(test)]
mod tests;

/// The launch digest of a run with no channel. No controller prepared it, so no other digest can be stale.
const LAUNCH_DIGEST: &str = "lane-run";
/// The port of the absent channel. A derived port of a lane service is never 0.
const NO_CHANNEL_PORT: u16 = 0;
/// The file of the run stream in the run directory.
pub(crate) const STREAM_FILE: &str = "run.ndjson";
/// The file of the JUnit XML in the run directory.
pub(crate) const XML_FILE: &str = "test.xml";
/// The variable that names the run directory, before `$TEST_TMPDIR/air-lane`.
pub(crate) const RUNTIME_ROOT_VARIABLE: &str = "AIR_LANE_RUNTIME_ROOT";
/// The variable of a Bazel `--test_filter`.
const TEST_FILTER_VARIABLE: &str = "TESTBRIDGE_TEST_ONLY";
/// The variable of the JUnit5 filters, `;`-separated, as the lane table spells them.
const JUNIT5_FILTERS_VARIABLE: &str = "JB_TEST_JUNIT5_FILTERS";
/// The variable of the shard count of a Bazel test.
const TOTAL_SHARDS_VARIABLE: &str = "TEST_TOTAL_SHARDS";
/// The variable of the timeout of a Bazel test, in seconds. Bazel ends the test at it.
const TEST_TIMEOUT_VARIABLE: &str = "TEST_TIMEOUT";
/// The variable of the outputs directory of a Bazel test, which Bazel keeps after the test.
const OUTPUTS_VARIABLE: &str = "TEST_UNDECLARED_OUTPUTS_DIR";
/// What the budget of a run leaves for its end: the kill grace of the watchdog, the hard stop with its JUnit XML, the
/// cancel of the IDE with a thread dump and the copy of the evidence, and the kill delay of Bazel.
pub(crate) const TEARDOWN_RESERVE: Duration = Duration::from_mins(3);
/// The refusal code of a budget that a run cannot keep.
pub(crate) const BUDGET_CODE: &str = "lane_run_budget";
/// The refusal code of a run that Bazel splits into shards.
pub(crate) const SHARDED_CODE: &str = "lane_run_sharded";
/// The refusal code of a recorded session that the variable names and that cannot be packed.
pub(crate) const SESSION_LOG_CODE: &str = "lane_session_log";
/// The refusal code of a `--jvm_flag` that lane-run does not take.
pub(crate) const JVM_FLAG_CODE: &str = "lane_run_jvm_flag";
/// The JVM property of the index of a runner of a TeamCity UI job, from 0.
const RUNNER_INDEX_PROPERTY: &str = "idea.test.runner.index";
/// The JVM property of the count of the runners of a TeamCity UI job.
const RUNNERS_COUNT_PROPERTY: &str = "idea.test.runners.count";
/// The JVM property that asks the recorder for the Allure results, with the value `on`: [`inputs::ALLURE_FLAG`].
const ALLURE_PROPERTY: &str = "air.flow.trace.allure";
/// The variable that names which scenarios keep their video, a word of [`VideoRetention`]. A TeamCity build passes it
/// with `--test_env`.
pub(crate) const VIDEO_VARIABLE: &str = "AIR_LANE_VIDEO";
/// The refusal code of a value of [`VIDEO_VARIABLE`] that names no retention.
pub(crate) const VIDEO_CODE: &str = "lane_run_video";
/// Why lane-run ignores a bucketing property of the JUnit runner.
const BUCKETING_IGNORED: &str = "lane-run splits the lane by the order of its suites, not by duration buckets";
/// The JVM properties that lane-run ignores, each with the reason that its note gives.
const IGNORED_PROPERTIES: [(&str, &str); 3] = [
    ("java.awt.headless", "the lane IDE runs headed on the display of the run"),
    ("idea.bucketing.season", BUCKETING_IGNORED),
    ("idea.tests.duration.bucketing.enabled", BUCKETING_IGNORED),
];

/// What the `--jvm_flag` arguments of a run ask for: the share of this runner of the lane, one note for each flag that
/// lane-run ignores, and whether the recorder writes the Allure results.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct JvmFlags {
    pub split: RunnerSplit,
    pub notes: Vec<String>,
    pub allure: bool,
}

/// Reads the `--jvm_flag` arguments that the UI job of TeamCity passes to the JUnit runner. The runner index and count
/// give the split. `-Dair.flow.trace.allure=on` asks for the Allure results, and another value of that property is
/// refused. Each ignored property gives a note. A flag that is no `-D` property, another property, a property with two
/// values, and an index and a count that do not make a split are refused. The refusal of another flag names it and
/// never its value.
pub(crate) fn jvm_flags(flags: &[String]) -> Result<JvmFlags, AgentRefusal> {
    let refused = |message: String| AgentRefusal::refused(JVM_FLAG_CODE, message);
    let mut properties: BTreeMap<&str, &str> = BTreeMap::new();
    let mut notes = Vec::new();
    let mut allure = false;
    for flag in flags {
        let Some(property) = flag.strip_prefix("-D").filter(|property| !property.is_empty()) else {
            let name = flag.split('=').next().unwrap_or_default();
            return Err(refused(format!(
                "lane-run takes only a -D<name>=<value> property as --jvm_flag, and the flag {name} is none"
            )));
        };
        let (name, value) = property.split_once('=').unwrap_or((property, ""));
        if let Some((_, reason)) = IGNORED_PROPERTIES.iter().find(|(ignored, _)| *ignored == name) {
            notes.push(format!("lane-run ignores the JVM flag {flag}: {reason}"));
            continue;
        }
        if name == ALLURE_PROPERTY {
            if value != "on" {
                return Err(refused(format!("the JVM property {ALLURE_PROPERTY} takes only the value on")));
            }
            allure = true;
            continue;
        }
        if name != RUNNER_INDEX_PROPERTY && name != RUNNERS_COUNT_PROPERTY {
            return Err(refused(format!(
                "lane-run does not take the JVM property {name}: it takes {RUNNER_INDEX_PROPERTY}, \
                 {RUNNERS_COUNT_PROPERTY} and {ALLURE_PROPERTY}, and it ignores {}",
                IGNORED_PROPERTIES.map(|(ignored, _)| ignored).join(", ")
            )));
        }
        if properties.insert(name, value).is_some_and(|earlier| earlier != value) {
            return Err(refused(format!("the JVM property {name} has two values")));
        }
    }
    let number = |name: &str| -> Result<Option<u32>, AgentRefusal> {
        properties
            .get(name)
            .map(|value| {
                value
                    .parse::<u32>()
                    .map_err(|error| refused(format!("the JVM property {name} is no whole number: {value} ({error})")))
            })
            .transpose()
    };
    let split = match (number(RUNNER_INDEX_PROPERTY)?, number(RUNNERS_COUNT_PROPERTY)?) {
        (None, None) => RunnerSplit::WHOLE,
        (Some(index), Some(count)) if index < count => RunnerSplit { index, count },
        (Some(index), Some(count)) => {
            return Err(refused(format!(
                "the runner index {index} of {RUNNER_INDEX_PROPERTY} is not below the runner count {count} of \
                 {RUNNERS_COUNT_PROPERTY}"
            )));
        }
        (Some(_), None) | (None, Some(_)) => {
            return Err(refused(format!(
                "a split of the lane needs both {RUNNER_INDEX_PROPERTY} and {RUNNERS_COUNT_PROPERTY}, and the run names \
                 one of them"
            )));
        }
    };
    Ok(JvmFlags { split, notes, allure })
}

/// Packs each recorded session that [`session_log::VARIABLE`] names into the jar store, as the controller pushes them
/// to a guest. The zip leaves the run directory once the store holds it.
pub(crate) fn session_logs(
    daemon: &channel::Daemon,
    root: &Path,
    variable: &dyn Fn(&str) -> Option<String>,
) -> Result<Vec<SessionLog>, AgentRefusal> {
    let Some(value) = variable(session_log::VARIABLE) else {
        return Ok(Vec::new());
    };
    let logs = session_log::named_logs(&value).map_err(|message| AgentRefusal::refused(SESSION_LOG_CODE, message))?;
    let directory = root.join("session-logs");
    fs::create_dir_all(&directory)
        .map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot create {}: {error}", directory.display())))?;
    logs.iter()
        .map(|log| {
            let zip = directory.join(format!("{}.zip", log.label));
            session_log::pack(log, &zip).map_err(|message| AgentRefusal::refused(SESSION_LOG_CODE, message))?;
            let sha256 = daemon.store_file(&zip);
            let _ = fs::remove_file(&zip);
            Ok(SessionLog {
                label: log.label.clone(),
                sha256: sha256?,
            })
        })
        .collect()
}

/// Runs one iteration of `args`, and answers the exit status: 0 for a green run, 1 for any other. `variable` reads the
/// environment.
pub(crate) fn run(args: &LaneRunArgs, variable: &dyn Fn(&str) -> Option<String>) -> Result<u8, AgentRefusal> {
    run_lane(args, variable).inspect_err(|refusal| record_refusal(args, variable, refusal))
}

/// Writes the run result of a run under a Bazel test that `lane-run` refused before the end of the run wrote one, such
/// as a host probe that found no window manager. Without it, the TeamCity step reads no run result and sends an event
/// with no failed test and no summary, which does not say why no scenario ran. A result that the end of the run wrote
/// stays.
fn record_refusal(args: &LaneRunArgs, variable: &dyn Fn(&str) -> Option<String>, refusal: &AgentRefusal) {
    let Some(outputs) = bazel_outputs(variable).filter(|_| !args.plan_only) else {
        return;
    };
    let file = run_result::path(Some(&outputs), &outputs);
    if file.exists() {
        return;
    }
    if let Err(error) = run_result::write(&file, &run_result::refused(&args.lane, &refusal.code, &refusal.message)) {
        eprintln!(
            "lane-run: cannot write the run result {} of the refusal {}: {error}",
            file.display(),
            refusal.code
        );
    }
}

fn run_lane(args: &LaneRunArgs, variable: &dyn Fn(&str) -> Option<String>) -> Result<u8, AgentRefusal> {
    let started = Instant::now();
    let flags = jvm_flags(&args.jvm_flags)?;
    let rerun = rerun::from_environment(variable)?;
    refuse_shards(variable)?;
    let keep_video = video_retention(variable)?;
    // A plan launches no IDE, so it spends no budget.
    let budget = if args.plan_only { None } else { run_budget(args, variable)? };
    let root = runtime_root(args, variable)?;
    let runfiles_root = args
        .runfiles_root
        .clone()
        .unwrap_or_else(|| runtime::runfiles_root(&args.descriptor));
    let daemon_args = LaneDaemonArgs {
        state_dir: root.join("state"),
        port: NO_CHANNEL_PORT,
        bind: IpAddr::V4(Ipv4Addr::LOCALHOST),
        launch_digest: LAUNCH_DIGEST.to_owned(),
        descriptor: args.descriptor.clone(),
        runfiles_root: runfiles_root.clone(),
        ide_root: root.join("ide"),
        home: root.join("home"),
    };
    let content = fs::read(&args.descriptor).map_err(|error| {
        AgentRefusal::failure(
            BOOT_CODE,
            format!("cannot read the runtime descriptor {}: {error}", args.descriptor.display()),
        )
    })?;
    let descriptor = runtime::parse_runtime_descriptor(&content, &args.descriptor, guest_platform())
        .map_err(|error| AgentRefusal::refused(error.code, error.message))?;
    let mut launch_inputs = inputs::from_descriptor(&descriptor, &daemon_args)?;
    launch_inputs.trace.bazel_outputs = bazel_outputs(variable);
    let owns_screen = build_owns_screen(variable, cfg!(target_os = "linux"));
    launch_inputs.trace.screen |= owns_screen;
    launch_inputs.trace.allure |= flags.allure;
    launch_inputs.trace.keep_video = keep_video;
    let jcmd = launch_inputs.jcmd();
    let mut notes = flags.notes;
    notes.extend(rerun.injected.iter().map(rerun::Injection::describe));
    // Held to the end of the run: its drop stops the display.
    let run_display = if owns_screen && !args.plan_only {
        let (display, note) = own_display::take(&root, &variable(DISPLAY_VARIABLE).unwrap_or_default(), variable("PATH").as_deref())?;
        if display.is_none() {
            println!("{}", own_display::warning_message(&note));
        }
        notes.push(note);
        display
    } else {
        None
    };
    let owned = run_display.as_ref().map(|display| display.display().to_owned());
    let on_own_display = |name: &str| match &owned {
        Some(display) if name == DISPLAY_VARIABLE => Some(display.clone()),
        _ => variable(name),
    };
    let variable: &dyn Fn(&str) -> Option<String> = &on_own_display;
    if host_probe_applies(args, variable, cfg!(target_os = "linux")) {
        notes.extend(probe_host(&launch_inputs, variable)?);
    }
    let daemon = channel::boot(&daemon_args, launch_inputs, variable)?;
    let runfile = |file: &RuntimeFile| runtime::runtime_file_path(&runfiles_root, file);
    let lane_inputs = LaneInputs {
        fake_agent_jar: daemon.store_file(&runfile(&descriptor.ide.fake_agent_jar))?,
        lane_documents: daemon.store_file(&runfile(&descriptor.ide.lane_documents))?,
        suite_documents: daemon.store_file(&runfile(&descriptor.ide.suite_documents))?,
    };
    let ide_plugins = descriptor
        .ide
        .plugins
        .iter()
        .map(|plugin| {
            Ok(IdePlugin {
                destination: plugin.destination.clone(),
                sha256: daemon.store_file(&runfile(&plugin.file))?,
            })
        })
        .collect::<Result<Vec<_>, AgentRefusal>>()?;
    let fingerprint = runfile(&descriptor.dev_dist.fingerprint);
    let product_stamp = sha256_hex(
        &fs::read(&fingerprint)
            .map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot read {}: {error}", fingerprint.display())))?,
    );
    let request = RunRequest {
        selectors: selectors(args, variable),
        junit5_filters: junit5_filters(args, variable),
        product_stamp,
        launch_digest: LAUNCH_DIGEST.to_owned(),
        active_execution_timeout_sec: args.active_execution_timeout_sec,
        progress_gap_timeout_sec: args.progress_gap_timeout_sec,
        // What the staging left of the budget, so the watchdog ends the run at the end of the budget of the process.
        run_budget_sec: budget.map(|budget| budget.saturating_sub(started.elapsed()).as_secs()),
        ide_plugins,
        lane: Some(args.lane.clone()),
        lane_inputs: Some(lane_inputs),
        lane_classes: Vec::new(),
        session_logs: session_logs(&daemon, &root, variable)?,
    };
    if args.plan_only {
        let plan = plan(&daemon, &request, flags.split)?;
        let mut out = io::stdout().lock();
        serde_json::to_writer_pretty(&mut out, &plan)
            .map_err(io::Error::from)
            .and_then(|()| writeln!(out))
            .map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot write the plan: {error}")))?;
        return Ok(0);
    }
    let performance_plan = performance::plan_or_note(
        &runfile(&descriptor.ide.lane_documents),
        &runfile(&descriptor.dev_dist.home),
        &args.lane,
        &mut notes,
    );
    let end = run_end(&daemon, &request, flags.split, performance_plan, &root, variable);
    let evidence = bazel_outputs(variable).map(|outputs| Evidence::new(&daemon_args.state_dir, &daemon_args.ide_root, &outputs));
    let stream_path = root.join(STREAM_FILE);
    let stream = File::create(&stream_path)
        .map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot create {}: {error}", stream_path.display())))?;
    let monitor = Monitor::new(
        &daemon,
        MonitorPaths {
            stream: stream_path.clone(),
            xml: xml_files(&root, variable),
            ide_root: daemon_args.ide_root,
            report: bazel_outputs(variable).map(|directory| directory.join(monitor::REPORT_FILE)),
            evidence: evidence.clone(),
        },
        budget,
        budget.map(|budget| started + budget + KILL_GRACE),
        teamcity::is_team_city(variable),
        Some(&end),
    );
    let mut tee = Tee {
        file: stream,
        out: io::stdout(),
        build_log: BuildLog::open(variable, &args.lane, bazel_outputs(variable).as_deref()),
    };
    let green = std::thread::scope(|scope| {
        // Named, so a hang report tells the monitor from the run thread.
        if let Err(error) = std::thread::Builder::new()
            .name(MONITOR_THREAD.to_owned())
            .spawn_scoped(scope, || monitor.watch(&LiveStop { jcmd }))
        {
            log(&format!(
                "cannot start the monitor, so the run has no hang report and no hard stop: {error}"
            ));
        }
        let xml = iteration::run_split(&daemon, &request, flags.split, &notes, Some(&rerun), &mut tee);
        monitor.run_returned();
        let mut ending = LaneRunEnding {
            daemon: &daemon,
            monitor: &monitor,
            root: &root,
            xml,
            stream_path: &stream_path,
            budget,
            end: &end,
            evidence: evidence.as_ref(),
            variable,
            events: Vec::new(),
        };
        finish(&mut tee, &mut ending)
    })?;
    Ok(u8::from(!green))
}

/// The steps of `lane-run` after its iteration, which [`finish`] runs in their order.
pub(crate) trait Ending {
    /// Writes the JUnit XML of the iteration.
    fn write_xml(&mut self) -> Result<(), AgentRefusal>;

    /// Quits the lane IDE and stops the lane services.
    fn quit(&mut self) -> Result<(), AgentRefusal>;

    /// Reads the run stream back, writes the IJ Perf files and the result of the run, and answers whether the run is
    /// green. `quit` is the outcome of [`Ending::quit`].
    fn end(&mut self, quit: &Result<(), AgentRefusal>) -> Result<bool, AgentRefusal>;

    /// Whether a scenario of the run stream that [`Ending::end`] read failed an attempt, also one that its rerun passed.
    fn failed_an_attempt(&self) -> bool;

    /// Keeps the evidence of a run that is not green, or that failed an attempt.
    fn keep_evidence(&mut self);
}

/// Runs the steps of `ending` in order, and writes the `timing` record of each into `out` after it: `lane-run-xml`,
/// `lane-run-quit`, `lane-run-end` and, for a run that is not green or that failed an attempt, `lane-run-evidence`. It
/// answers whether the run is green, and a JUnit XML that it could not write is its refusal.
pub(crate) fn finish(out: &mut impl Write, ending: &mut dyn Ending) -> Result<bool, AgentRefusal> {
    let written = timed(out, TimedStep::LaneRunXml, || ending.write_xml());
    let quit = timed(out, TimedStep::LaneRunQuit, || ending.quit());
    let green = timed(out, TimedStep::LaneRunEnd, || ending.end(&quit))?;
    if !green || ending.failed_an_attempt() {
        timed(out, TimedStep::LaneRunEvidence, || ending.keep_evidence());
    }
    written?;
    Ok(green)
}

/// Runs `work` as the step `step`, and writes its `timing` record into `out`, where the run stream goes. A write that
/// fails is a line of the log.
fn timed<T>(out: &mut impl Write, step: TimedStep, work: impl FnOnce() -> T) -> T {
    let started = Instant::now();
    let outcome = work();
    let record = super::stream::timing(step, started, None);
    let written = serde_json::to_vec(&record)
        .map_err(io::Error::from)
        .and_then(|mut line| {
            line.push(b'\n');
            out.write_all(&line)
        })
        .and_then(|()| out.flush());
    if let Err(error) = written {
        log(&format!("cannot write the {} record of the run stream: {error}", step.as_str()));
    }
    outcome
}

/// The end of a real run: the daemon, the monitor, the run directory and the files of the run.
struct LaneRunEnding<'a> {
    daemon: &'a channel::Daemon,
    monitor: &'a Monitor<'a>,
    root: &'a Path,
    xml: String,
    stream_path: &'a Path,
    budget: Option<Duration>,
    end: &'a RunEnd,
    evidence: Option<&'a Evidence>,
    variable: &'a dyn Fn(&str) -> Option<String>,
    /// The records of the run stream, which [`Ending::end`] reads back.
    events: Vec<RunEvent>,
}

impl Ending for LaneRunEnding<'_> {
    fn write_xml(&mut self) -> Result<(), AgentRefusal> {
        write_xml(self.root, &self.xml, self.variable)
    }

    fn quit(&mut self) -> Result<(), AgentRefusal> {
        let finished = self.daemon.finish();
        self.monitor.end();
        if let Err(refusal) = &finished {
            log(&format!("the IDE did not quit: {}: {}", refusal.code, refusal.message));
        }
        finished
    }

    fn end(&mut self, quit: &Result<(), AgentRefusal>) -> Result<bool, AgentRefusal> {
        let stream = fs::read(self.stream_path)
            .map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot read {}: {error}", self.stream_path.display())))?;
        self.events = monitor::decode_stream(&stream);
        if let Some(problem) = budget_problem(&self.events, self.budget, self.variable) {
            println!("{problem}");
        }
        let green = is_green(&stream) && quit.is_ok();
        let ended = self.end.write(
            &self.events,
            green,
            &|id| self.daemon.iteration_dir(id),
            &rfc_1123(&jiff::Zoned::now()),
        );
        if !ended.messages.is_empty() {
            let mut out = io::stdout().lock();
            if let Err(error) = writeln!(out, "{}", ended.messages.join("\n")).and_then(|()| out.flush()) {
                log(&format!("cannot write the publishArtifacts lines of the IJ Perf files: {error}"));
            }
        }
        for line in &ended.lines {
            log(line);
        }
        Ok(green)
    }

    fn failed_an_attempt(&self) -> bool {
        self.events
            .iter()
            .any(|event| matches!(&event.kind, RunEventKind::TestFinished(finished) if finished.status == avl_junit::cases::STATUS_FAILED))
    }

    fn keep_evidence(&mut self) {
        if let Some(evidence) = self.evidence {
            let kept = evidence::keep(evidence, &self.events, Moment::RunEnd, Limits::LANE);
            log(&kept.describe(&evidence.destination));
        }
    }
}

/// What the end of the run of `request` writes: the IJ Perf files of `plan` and the result of the run. A plan gets the
/// suite documents of the selection, which map a case to its parts. A selection that cannot be made is a log line, and
/// the IJ Perf files then get no part files.
pub(crate) fn run_end(
    daemon: &channel::Daemon,
    request: &RunRequest,
    split: RunnerSplit,
    plan: Option<Plan>,
    root: &Path,
    variable: &dyn Fn(&str) -> Option<String>,
) -> RunEnd {
    let suites = match (&plan, &request.lane, &request.lane_inputs) {
        (Some(_), Some(lane), Some(lane_inputs)) => match iteration::selection_of(daemon, lane, lane_inputs, request, split) {
            Ok(selection) => selection.suites.into_iter().map(|suite| suite.program).collect(),
            Err(reason) => {
                log(&format!(
                    "the IJ Perf files get no part files, because the suites cannot be read: {reason}"
                ));
                Vec::new()
            }
        },
        _ => Vec::new(),
    };
    let outputs = bazel_outputs(variable);
    RunEnd {
        lane: request.lane.clone().unwrap_or_default(),
        plan,
        suites,
        performance_dir: performance::directory(outputs.as_deref(), root),
        result_file: run_result::path(outputs.as_deref(), root),
        team_city: teamcity::is_team_city(variable),
    }
}

/// The TeamCity build problem of a run that its budget ended, on a TeamCity agent: the case that ran and the budget. A
/// muted case is no failure of the build, so the case that the budget cut off cannot leave the build green.
pub(crate) fn budget_problem(events: &[RunEvent], budget: Option<Duration>, variable: &dyn Fn(&str) -> Option<String>) -> Option<String> {
    if !teamcity::is_team_city(variable) {
        return None;
    }
    let expired = events.iter().find_map(|event| match &event.kind {
        RunEventKind::WatchdogExpired(expired) if expired.reason == ExpiryReason::RunBudget.wire() => Some(expired),
        _ => None,
    })?;
    let budget = budget.map_or_else(
        || "its budget".to_owned(),
        |budget| format!("the lane budget of {} s", budget.as_secs()),
    );
    let case = expired.active_execution.as_ref().map_or_else(
        || "between two scenarios".to_owned(),
        |execution| {
            let class = execution.class_name.as_deref().map(simple_class_name).unwrap_or_default();
            format!("in {class}.{}", execution.display_name)
        },
    );
    Some(teamcity::build_problem(
        &format!("lane-run: {budget} ended the run {case}; the scenarios after it did not run"),
        BUDGET_PROBLEM,
    ))
}

/// Whether lane-run proves the host before the lane IDE starts: on Linux, for a run that launches an IDE, on a host that
/// no controller prepared, such as a TeamCity agent or a developer host. The controller names the run secrets
/// directory for its workers alone. `validate-guest` proved the display of such a worker at its boot, and the lane
/// daemon of the worker proves the shared objects of its distribution at its own boot. So a run there pays nothing.
pub(crate) fn host_probe_applies(args: &LaneRunArgs, variable: &dyn Fn(&str) -> Option<String>, linux: bool) -> bool {
    linux && !args.plan_only && variable(RUN_SECRETS_VARIABLE).is_none_or(|value| value.trim().is_empty())
}

/// Whether the recorder may read the screen of a run whose descriptor does not let it: a TeamCity build on Linux with
/// an X display. A TeamCity agent runs one build at a time, and no person works on its display, so it shows nothing of a
/// person. A host of a developer is no TeamCity build, so lane-run never reads the screen of a person. Such a run starts
/// a display of its own, as [`own_display`] states.
pub(crate) fn build_owns_screen(variable: &dyn Fn(&str) -> Option<String>, linux: bool) -> bool {
    linux && teamcity::is_team_city(variable) && variable("DISPLAY").is_some_and(|display| !display.trim().is_empty())
}

/// Which scenarios keep their video, as [`VIDEO_VARIABLE`] names it. `failed` keeps the video of a scenario that did not
/// pass only. A TeamCity build sets it on a branch other than `master` and in a personal build, so a passed scenario
/// keeps its video only in a build of `master`, the baseline. Unset or empty, every scenario keeps its video. Another
/// value is refused by name.
pub(crate) fn video_retention(variable: &dyn Fn(&str) -> Option<String>) -> Result<VideoRetention, AgentRefusal> {
    let Some(value) = variable(VIDEO_VARIABLE).filter(|value| !value.trim().is_empty()) else {
        return Ok(VideoRetention::Always);
    };
    VideoRetention::from_word(value.trim()).ok_or_else(|| {
        let words: Vec<&str> = VideoRetention::ALL.iter().map(|retention| retention.as_str()).collect();
        AgentRefusal::refused(
            VIDEO_CODE,
            format!("${VIDEO_VARIABLE} is {value:?}, and it takes only {}", words.join(" or ")),
        )
    })
}

/// The X display of the host, `DISPLAY`, refused by name when it is unset, because the lane IDE opens its windows on it.
pub(crate) fn host_display(variable: &dyn Fn(&str) -> Option<String>) -> Result<String, AgentRefusal> {
    variable("DISPLAY").filter(|display| !display.trim().is_empty()).ok_or_else(|| {
        AgentRefusal::refused(
            CODE_DISPLAY_MISSING,
            "this host cannot run the lane IDE: DISPLAY is unset, and the lane IDE opens its windows on an X display \
             with a window manager; start Xvfb and a window manager such as fluxbox, and export DISPLAY",
        )
    })
}

/// Proves the host before the lane IDE starts, with the distribution of `inputs` as the root of the shared objects,
/// and answers the notes of the probe.
fn probe_host(inputs: &LaunchInputs, variable: &dyn Fn(&str) -> Option<String>) -> Result<Vec<String>, AgentRefusal> {
    let probe = ValidateGuestArgs {
        display: host_display(variable)?,
    };
    GuestValidator::new(&probe, SystemRunner).probe_host(&inputs.dist_home)
}

/// The refusal code of a plan whose documents do not select.
pub(crate) const PLAN_CODE: &str = "lane_run_plan";

/// What a run would run, as `--plan-only` writes it: the lane, its IDE, its inputs, and its suites in run order.
#[derive(Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct RunPlan {
    pub lane: String,
    /// The config key of the IDE of the lane, or `None` when every suite launches a bare IDE of its own.
    pub lane_key: Option<String>,
    pub product_stamp: String,
    /// The digests of the lane inputs in the jar store.
    pub lane_inputs: LaneInputs,
    pub context_plugin_files: usize,
    /// The names of the declared CLI runtimes of the descriptor, sorted. The descriptor of the live lane adds `central`,
    /// `claude` and `junie`.
    pub cli_runtimes: Vec<String>,
    /// The suites of the shared IDE, then the suites of a bare IDE, as the program runner runs them.
    pub suites: Vec<PlannedSuite>,
}

/// One suite of a [`RunPlan`].
#[derive(Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PlannedSuite {
    pub test_class_name: String,
    pub suite: String,
    pub scenarios: Vec<String>,
    /// The launch key of a suite of a bare IDE.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub bare_launch_key: Option<String>,
}

/// The plan of `request` for the share `split`: the selection of a run, and the key of the IDE that the run would
/// launch.
pub(crate) fn plan(daemon: &channel::Daemon, request: &RunRequest, split: RunnerSplit) -> Result<RunPlan, AgentRefusal> {
    let refused = |message: String| AgentRefusal::refused(PLAN_CODE, message);
    let (Some(lane), Some(lane_inputs)) = (&request.lane, &request.lane_inputs) else {
        return Err(refused("the run request names no lane and no lane inputs".to_owned()));
    };
    let selection = iteration::selection_of(daemon, lane, lane_inputs, request, split).map_err(refused)?;
    let shared = selection.suites.iter().any(|suite| suite.program.launch.is_none());
    let lane_key = if shared {
        Some(iteration::lane_key(daemon, &selection, &request.product_stamp).map_err(refused)?)
    } else {
        None
    };
    let mut cli_runtimes: Vec<String> = daemon.with_ide(|owner| owner.runtimes().iter().map(|runtime| runtime.name.clone()).collect());
    cli_runtimes.sort();
    let (bare, shared): (Vec<_>, Vec<_>) = selection.suites.iter().partition(|suite| suite.program.launch.is_some());
    let suites = shared
        .into_iter()
        .chain(bare)
        .map(|suite| PlannedSuite {
            test_class_name: suite.program.test_class_name.clone(),
            suite: suite.program.suite.clone(),
            scenarios: suite.program.profiles.iter().map(|profile| profile.name.clone()).collect(),
            bare_launch_key: suite.program.launch.as_ref().map(|launch| launch.key.clone()),
        })
        .collect();
    Ok(RunPlan {
        lane: lane.clone(),
        lane_key,
        product_stamp: request.product_stamp.clone(),
        lane_inputs: lane_inputs.clone(),
        context_plugin_files: request.ide_plugins.len(),
        cli_runtimes,
        suites,
    })
}

/// Writes `xml` into the run directory `root`, and to the file that `$JB_XML_OUTPUT_FILE` or `$XML_OUTPUT_FILE` names.
/// Bazel and `bt` count the cases of that file, so a write that fails fails the run.
/// The files of the JUnit XML of a run: the one in the run directory `root`, and the XML file of a Bazel test.
pub(crate) fn xml_files(root: &Path, variable: &dyn Fn(&str) -> Option<String>) -> Vec<PathBuf> {
    let mut files = vec![root.join(XML_FILE)];
    files.extend(avl_junit::destination(variable).map(PathBuf::from));
    files
}

/// The budget of the run: the smaller of `--run-budget-sec` and `$TEST_TIMEOUT`, less [`TEARDOWN_RESERVE`], or `None`
/// when neither names one. A `$TEST_TIMEOUT` that is no whole number of seconds is refused, and so is a budget that the
/// reserve leaves nothing of.
pub(crate) fn run_budget(args: &LaneRunArgs, variable: &dyn Fn(&str) -> Option<String>) -> Result<Option<Duration>, AgentRefusal> {
    let refused = |message: String| AgentRefusal::refused(BUDGET_CODE, message);
    let timeout = variable(TEST_TIMEOUT_VARIABLE)
        .filter(|value| !value.trim().is_empty())
        .map(|value| {
            value
                .trim()
                .parse::<u64>()
                .map_err(|error| refused(format!("${TEST_TIMEOUT_VARIABLE} is no whole number of seconds: {value} ({error})")))
        })
        .transpose()?;
    let Some(seconds) = [args.run_budget_sec, timeout].into_iter().flatten().min() else {
        return Ok(None);
    };
    match Duration::from_secs(seconds).checked_sub(TEARDOWN_RESERVE) {
        Some(budget) if !budget.is_zero() => Ok(Some(budget)),
        _ => Err(refused(format!(
            "the run budget of {seconds} s leaves nothing after the teardown reserve of {} s",
            TEARDOWN_RESERVE.as_secs()
        ))),
    }
}

pub(crate) fn write_xml(root: &Path, xml: &str, variable: &dyn Fn(&str) -> Option<String>) -> Result<(), AgentRefusal> {
    for file in xml_files(root, variable) {
        if let Some(parent) = file.parent() {
            fs::create_dir_all(parent)
                .map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot create {}: {error}", parent.display())))?;
        }
        fs::write(&file, xml)
            .map_err(|error| AgentRefusal::failure(BOOT_CODE, format!("cannot write the JUnit XML {}: {error}", file.display())))?;
    }
    Ok(())
}

/// Refuses a run that Bazel splits into more than one shard. A lane IDE takes the fixed bridge port, so the shards of one
/// lane cannot run beside each other, and one iteration runs the whole selection.
pub(crate) fn refuse_shards(variable: &dyn Fn(&str) -> Option<String>) -> Result<(), AgentRefusal> {
    match variable(TOTAL_SHARDS_VARIABLE).and_then(|total| total.trim().parse::<u32>().ok()) {
        Some(total) if total > 1 => Err(AgentRefusal::refused(
            SHARDED_CODE,
            format!(
                "Bazel splits the lane test into {total} shards, and lane-run runs one iteration of the lane on the \
                 fixed bridge port; run the lane test as one shard"
            ),
        )),
        _ => Ok(()),
    }
}

/// The outputs directory of the Bazel test, `$TEST_UNDECLARED_OUTPUTS_DIR`, which Bazel keeps after the test. The trace
/// bundles and the hang report go there. An empty value names no directory.
pub(crate) fn bazel_outputs(variable: &dyn Fn(&str) -> Option<String>) -> Option<PathBuf> {
    variable(OUTPUTS_VARIABLE)
        .filter(|directory| !directory.is_empty())
        .map(PathBuf::from)
}

/// The run directory: `--runtime-root`, then `$AIR_LANE_RUNTIME_ROOT`, then `$TEST_TMPDIR/air-lane`.
pub(crate) fn runtime_root(args: &LaneRunArgs, variable: &dyn Fn(&str) -> Option<String>) -> Result<PathBuf, AgentRefusal> {
    if let Some(root) = &args.runtime_root {
        return Ok(root.clone());
    }
    let named = |name: &str| variable(name).filter(|value| !value.is_empty()).map(PathBuf::from);
    named(RUNTIME_ROOT_VARIABLE)
        .or_else(|| named("TEST_TMPDIR").map(|temporary| temporary.join("air-lane")))
        .filter(|root| root.is_absolute())
        .ok_or_else(|| {
            AgentRefusal::refused(
                "lane_run_root_missing",
                format!("lane-run needs an absolute --runtime-root, ${RUNTIME_ROOT_VARIABLE} or $TEST_TMPDIR"),
            )
        })
}

/// The selectors of `--selector`, and then those of a Bazel `--test_filter`.
pub(crate) fn selectors(args: &LaneRunArgs, variable: &dyn Fn(&str) -> Option<String>) -> Vec<String> {
    let filter = variable(TEST_FILTER_VARIABLE).unwrap_or_default();
    args.selectors
        .iter()
        .cloned()
        .chain(
            filter
                .split(',')
                .map(str::trim)
                .filter(|selector| !selector.is_empty())
                .map(str::to_owned),
        )
        .collect()
}

/// The filters of `--junit5-filter`, and then those of `$JB_TEST_JUNIT5_FILTERS`.
pub(crate) fn junit5_filters(args: &LaneRunArgs, variable: &dyn Fn(&str) -> Option<String>) -> Vec<String> {
    let listed = variable(JUNIT5_FILTERS_VARIABLE).unwrap_or_default();
    args.junit5_filters
        .iter()
        .cloned()
        .chain(
            listed
                .split(';')
                .map(str::trim)
                .filter(|filter| !filter.is_empty())
                .map(str::to_owned),
        )
        .collect()
}

/// Whether the run stream ends green: a summary with no failure, and no failed run. A stream without a summary is a run
/// that was cut off, and it is not green.
pub(crate) fn is_green(stream: &[u8]) -> bool {
    let mut summary = None;
    for line in stream.split(|byte| *byte == b'\n').filter(|line| !line.is_empty()) {
        match decode_run_event(line).map(|event| event.kind) {
            Ok(RunEventKind::Summary(found)) => summary = Some(found),
            Ok(RunEventKind::RunFailed(_)) | Err(_) => return false,
            Ok(_) => {}
        }
    }
    summary.is_some_and(|summary| !summary.has_failures)
}

/// Writes each record to the run file and to standard output, and shows it in the build log channel of TeamCity. A
/// failed write to standard output or to the channel is not a failed run.
struct Tee<F, O> {
    file: F,
    out: O,
    build_log: Option<BuildLog>,
}

impl<F: Write, O: Write> Write for Tee<F, O> {
    fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
        self.file.write_all(bytes)?;
        let _ = self.out.write_all(bytes);
        if let Some(build_log) = &mut self.build_log {
            build_log.observe(bytes);
        }
        Ok(bytes.len())
    }

    fn flush(&mut self) -> io::Result<()> {
        let _ = self.out.flush();
        self.file.flush()
    }
}
