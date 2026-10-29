//! The monitor of `lane-run`: a thread beside the run thread, which the run thread cannot hold.
//!
//! A run thread can wait on something that no deadline of the watchdog ends. A TeamCity job then cancels the step at
//! its limit, and the run loses its log and its JUnit XML. The monitor runs on a thread of its own. It reads the watch
//! of the run, the run stream file and `/proc`, and it takes no lock that the run thread holds for longer than a field
//! update.
//!
//! Every [`HEARTBEAT`] the monitor writes the hang report on the standard error: the scenario and the open steps of the
//! watch, the budget left, and each thread of this process with its state, its wait channel and its system call. A
//! system call on a file descriptor names the target of the descriptor too.
//!
//! The hard stop comes at the end of the budget of the run and the kill grace of the watchdog after it. The watchdog
//! ended the run at the end of the budget, so a run that is still there then waits on something other than the IDE.
//! The monitor then ends the run itself. It writes the JUnit XML of the records so far, with the open scenario
//! unfinished and each planned scenario that never ran skipped. It cancels each IDE run that the stream launched and
//! did not end, and it kills each recorder. Under a Bazel test it then keeps the evidence of the run, as
//! [`super::evidence`] states. It writes the IJ Perf files and the result of the run from the records so far, as the end
//! of the run does ([`super::run_result::RunEnd`]), with the run not green. It writes the hang report and one line, and the process exits 1. Each
//! write on the standard error waits a bounded time, so a standard error that blocks cannot hold the hard stop.

use std::collections::HashSet;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::mpsc;
use std::sync::{Condvar, Mutex, PoisonError};
use std::time::{Duration, Instant};

use avl_junit::performance::rfc_1123;
use avl_junit::teamcity;
use avl_wire::daemon::{RunEvent, RunEventKind, Skipped, decode_run_event};
use avl_wire::ide::ide_run_id;
use nix::sys::signal::{Signal, kill};
use nix::unistd::Pid;

use super::channel::Daemon;
use super::evidence::{self, Evidence, Kept, Limits, Moment};
use super::run_result::{Ended, RunEnd};
use super::watchdog::RunSnapshot;
use crate::cli::{CancelArgs, RootArgs, RunArgs};
use crate::clock::now_stamp;
use crate::supervisor::{self, LiveSystem};

#[cfg(test)]
mod tests;

/// How often the monitor writes the hang report.
pub(crate) const HEARTBEAT: Duration = Duration::from_mins(3);
/// The identity of the TeamCity build problem of a run that its budget or the hard stop ended.
pub(crate) const BUDGET_PROBLEM: &str = "lane-run-budget";
/// The name of the thread of the monitor, which its own line of a hang report shows.
pub(crate) const MONITOR_THREAD: &str = "lane-monitor";
/// How long one write of the monitor on the standard error may take before the monitor goes on without it.
const SAY_WAIT: Duration = Duration::from_secs(2);
/// The file of the hang report in the outputs directory of a Bazel test, which Bazel keeps after the test.
pub(crate) const REPORT_FILE: &str = "lane-hang-report.txt";

/// The files that the monitor writes, and the directories that it reads.
pub(crate) struct MonitorPaths {
    /// The run stream that the run thread writes, record by record.
    pub stream: PathBuf,
    /// Each file of the JUnit XML of the run.
    pub xml: Vec<PathBuf>,
    /// The parent of every context of the lane IDE.
    pub ide_root: PathBuf,
    /// Where the hard stop writes the hang report, when a Bazel test names an outputs directory.
    pub report: Option<PathBuf>,
    /// The evidence that the hard stop keeps, when a Bazel test names an outputs directory.
    pub evidence: Option<Evidence>,
}

/// What the hard stop does to the processes of the run. The live one cancels the IDE runs through the supervisor and
/// kills the recorders. A test answers with a fake.
pub(crate) trait StopEffects {
    /// Cancels the supervised run `run_id` of the IDE context `context`, with a thread dump first.
    fn cancel_ide(&self, context: &Path, run_id: &str) -> Result<(), String>;
    /// The pids of the recorders that run now.
    fn recorders(&self) -> Vec<u32>;
    /// Kills the recorder `pid`.
    fn kill_recorder(&self, pid: u32);
}

/// The effects of a hard stop on this host: a cancel through the supervisor with a thread dump by `jcmd`, and a KILL to
/// each recorder of [`super::trace::running_recorders`].
pub(crate) struct LiveStop {
    pub jcmd: Option<PathBuf>,
}

impl StopEffects for LiveStop {
    fn cancel_ide(&self, context: &Path, run_id: &str) -> Result<(), String> {
        let args = CancelArgs {
            run: RunArgs {
                root: RootArgs {
                    root: context.to_path_buf(),
                },
                run_id: run_id.to_owned(),
            },
            grace_ms: 0,
            thread_dump: self.jcmd.clone(),
        };
        supervisor::cancel(&LiveSystem, &args)
            .map(|_| ())
            .map_err(|refusal| refusal.message)
    }

    fn recorders(&self) -> Vec<u32> {
        super::trace::running_recorders()
    }

    fn kill_recorder(&self, pid: u32) {
        if let Ok(pid) = i32::try_from(pid) {
            let _ = kill(Pid::from_raw(pid), Signal::SIGKILL);
        }
    }
}

/// The monitor of one `lane-run`.
pub(crate) struct Monitor<'a> {
    daemon: &'a Daemon,
    paths: MonitorPaths,
    /// The budget of the run after the teardown reserve, for the text of the hard stop.
    budget: Option<Duration>,
    /// When the monitor ends the run itself, or `None` for a run with no budget.
    hard_stop: Option<Instant>,
    /// Whether the run is a build step of TeamCity, so the hard stop also writes a build problem.
    team_city: bool,
    /// What the hard stop writes in place of the end of the run: the IJ Perf files and the result of the run.
    run_end: Option<&'a RunEnd>,
    state: Mutex<MonitorState>,
    changed: Condvar,
}

#[derive(Default)]
struct MonitorState {
    /// The run thread returned, so the JUnit XML of the run is its own to write.
    run_returned: bool,
    /// The run and its teardown ended, so the monitor returns.
    ended: bool,
}

/// What the hard stop did, for its line and for a test.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct HardStop {
    /// The count of cases in the JUnit XML that the hard stop wrote, or `None` when the run thread writes the XML.
    pub cases: Option<usize>,
    /// The IDE runs that the hard stop canceled, as `<context>/<run id>`.
    pub canceled: Vec<String>,
    /// The recorders that the hard stop killed.
    pub killed_recorders: Vec<u32>,
    /// The one line of the hard stop.
    pub line: String,
    /// The TeamCity build problem of the hard stop, on a TeamCity agent.
    pub build_problem: Option<String>,
    /// The evidence that the hard stop kept, when a Bazel test names an outputs directory.
    pub kept: Option<Kept>,
    /// The IJ Perf files and the result of the run that the hard stop wrote, when the run names what its end writes.
    pub ended: Option<Ended>,
}

impl<'a> Monitor<'a> {
    pub(crate) fn new(
        daemon: &'a Daemon,
        paths: MonitorPaths,
        budget: Option<Duration>,
        hard_stop: Option<Instant>,
        team_city: bool,
        run_end: Option<&'a RunEnd>,
    ) -> Self {
        Self {
            daemon,
            paths,
            budget,
            hard_stop,
            team_city,
            run_end,
            state: Mutex::new(MonitorState::default()),
            changed: Condvar::new(),
        }
    }

    /// Notes that the run thread returned: from now on the run thread writes the JUnit XML, and a hard stop writes none.
    pub(crate) fn run_returned(&self) {
        self.state.lock().unwrap_or_else(PoisonError::into_inner).run_returned = true;
    }

    /// Ends the monitor: its thread returns at once.
    pub(crate) fn end(&self) {
        self.state.lock().unwrap_or_else(PoisonError::into_inner).ended = true;
        self.changed.notify_all();
    }

    /// The thread of the monitor: a hang report every [`HEARTBEAT`], and the hard stop, which ends the process with 1.
    pub(crate) fn watch(&self, effects: &dyn StopEffects) {
        let mut next_report = Instant::now() + HEARTBEAT;
        let mut state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
        loop {
            if state.ended {
                return;
            }
            let now = Instant::now();
            if self.hard_stop.is_some_and(|stop| now >= stop) {
                drop(state);
                let report = self.report(Path::new(PROC_SELF));
                let stop = self.hard_stop(effects);
                if let Some(file) = &self.paths.report {
                    let _ = fs::write(file, format!("{}\n{}\n", stop.line, report.join("\n")));
                }
                if let Some(problem) = &stop.build_problem {
                    say_on(Output::Stdout, problem.clone());
                }
                if let Some(ended) = &stop.ended {
                    if !ended.messages.is_empty() {
                        say_on(Output::Stdout, ended.messages.join("\n"));
                    }
                    say(ended.lines.join("\n"));
                }
                say(format!("{}\n{}", report.join("\n"), stop.line));
                std::process::exit(1);
            }
            if now >= next_report {
                next_report = now + HEARTBEAT;
                drop(state);
                say(self.report(Path::new(PROC_SELF)).join("\n"));
                state = self.state.lock().unwrap_or_else(PoisonError::into_inner);
                continue;
            }
            let wake = self.hard_stop.map_or(next_report, |stop| stop.min(next_report));
            state = self
                .changed
                .wait_timeout(state, wake.saturating_duration_since(now))
                .unwrap_or_else(PoisonError::into_inner)
                .0;
        }
    }

    /// The hang report: one line of the run as the watch knows it, and one line for each thread of this process as
    /// `process` names it.
    pub(crate) fn report(&self, process: &Path) -> Vec<String> {
        let snapshot = self.daemon.watch().map(|watch| watch.snapshot()).unwrap_or_default();
        let mut lines = vec![format!("lane-run: hang report: {}", describe_run(&snapshot))];
        let own = (process == Path::new(PROC_SELF)).then(own_thread).flatten();
        lines.extend(thread_lines(process, own));
        lines
    }

    /// Ends the run: the JUnit XML of the records so far, the cancel of each IDE run that the stream launched and did not
    /// end, and the kill of each recorder. It answers what it did and the line that says so.
    pub(crate) fn hard_stop(&self, effects: &dyn StopEffects) -> HardStop {
        let stream = fs::read(&self.paths.stream).unwrap_or_default();
        let events = decode_stream(&stream);
        let budget = self
            .budget
            .map_or_else(|| "no".to_owned(), |budget| format!("{} s", budget.as_secs()));
        let reason = format!("the lane budget of {budget} ended the run before this scenario ran");
        let run_returned = self.state.lock().unwrap_or_else(PoisonError::into_inner).run_returned;
        let cases = (!run_returned).then(|| {
            let (xml, cases) = partial_xml(events.clone(), &reason);
            for file in &self.paths.xml {
                if let Some(parent) = file.parent() {
                    let _ = fs::create_dir_all(parent);
                }
                let _ = fs::write(file, &xml);
            }
            cases
        });
        let mut canceled = Vec::new();
        for (launch_key, launch_name) in unended_ide_runs(&events) {
            let Some(run_id) = ide_run_id(&launch_name) else { continue };
            let context = self.paths.ide_root.join(&launch_key);
            if effects.cancel_ide(&context, &run_id).is_ok() {
                canceled.push(format!("{}/{run_id}", context.display()));
            }
        }
        let killed_recorders = effects.recorders();
        for pid in &killed_recorders {
            effects.kill_recorder(*pid);
        }
        // After the cancels, so the thread dumps of the killed IDE runs are in their log directories.
        let kept = self
            .paths
            .evidence
            .as_ref()
            .map(|evidence| (evidence, evidence::keep(evidence, &events, Moment::HardStop, Limits::LANE)));
        // The end of the run never came, so the hard stop writes what it writes. The run is not green.
        let ended = self
            .run_end
            .map(|end| end.write(&events, false, &|id| self.daemon.iteration_dir(id), &rfc_1123(&jiff::Zoned::now())));
        let snapshot = self.daemon.watch().map(|watch| watch.snapshot()).unwrap_or_default();
        let written = cases.map_or_else(
            || "the run thread writes the JUnit XML".to_owned(),
            |cases| format!("the JUnit XML holds {cases} case(s)"),
        );
        let evidence = kept
            .as_ref()
            .map(|(evidence, kept)| format!("; {}", kept.describe(&evidence.destination)))
            .unwrap_or_default();
        let line = format!(
            "lane-run: the hard stop ended the run: the lane budget of {budget} and the kill grace of the watchdog \
             passed, and the run thread did not return. {}. {written}; {} IDE run(s) canceled, {} recorder(s) \
             killed{evidence}; exit 1",
            describe_run(&snapshot),
            canceled.len(),
            killed_recorders.len()
        );
        let build_problem = self.team_city.then(|| teamcity::build_problem(&line, BUDGET_PROBLEM));
        HardStop {
            cases,
            canceled,
            killed_recorders,
            line,
            build_problem,
            kept: kept.map(|(_, kept)| kept),
            ended,
        }
    }
}

/// `/proc` of this process.
const PROC_SELF: &str = "/proc/self";

/// The run as the watch knows it: the scenario, the open steps, and the budget left.
fn describe_run(snapshot: &RunSnapshot) -> String {
    let scenario = snapshot.scenario.as_ref().map_or_else(
        || "no scenario runs".to_owned(),
        |(name, since)| format!("{name} runs for {} s", since.as_secs()),
    );
    let steps = snapshot.steps.as_ref().map_or_else(
        || "no step is open".to_owned(),
        |(path, since)| format!("step '{path}' is open for {} s", since.as_secs()),
    );
    let budget = snapshot.budget_left.map_or_else(
        || "the run has no budget".to_owned(),
        |left| format!("{} s of the budget left", left.as_secs()),
    );
    format!("{scenario}; {steps}; {budget}")
}

/// Writes `text` on the standard error, and waits at most [`SAY_WAIT`] for the write.
fn say(text: String) {
    say_on(Output::Stderr, text);
}

/// A standard stream of the process.
#[derive(Clone, Copy)]
enum Output {
    Stdout,
    Stderr,
}

/// Writes `text` on `output`, and waits at most [`SAY_WAIT`] for the write. The write runs on a thread of its own, so
/// a stream that blocks cannot hold the monitor before the hard stop ends the process.
fn say_on(output: Output, text: String) {
    let (written, done) = mpsc::channel();
    let _ = std::thread::Builder::new().name("lane-monitor-say".to_owned()).spawn(move || {
        match output {
            Output::Stdout => println!("{text}"),
            Output::Stderr => eprintln!("{text}"),
        }
        let _ = written.send(());
    });
    let _ = done.recv_timeout(SAY_WAIT);
}

/// The records of `stream`, a run stream file. A line that does not decode is left out: the file can end inside the
/// record that the run thread writes.
pub(crate) fn decode_stream(stream: &[u8]) -> Vec<RunEvent> {
    stream
        .split(|byte| *byte == b'\n')
        .filter(|line| !line.is_empty())
        .filter_map(|line| decode_run_event(line).ok())
        .collect()
}

/// The JUnit XML of `events` and its count of cases. Each scenario that the last `planStarted` names and that no record
/// reached becomes a `testSkipped` with `reason`. The renderer makes a scenario that started and never finished an
/// unfinished case.
pub(crate) fn partial_xml(mut events: Vec<RunEvent>, reason: &str) -> (String, usize) {
    let planned = events
        .iter()
        .rev()
        .find_map(|event| match &event.kind {
            RunEventKind::PlanStarted(plan) => Some(plan.executions.clone()),
            _ => None,
        })
        .unwrap_or_default();
    let reached: HashSet<String> = events
        .iter()
        .filter_map(|event| match &event.kind {
            RunEventKind::TestStarted(started) => Some(started.execution.id.clone()),
            RunEventKind::TestFinished(finished) => Some(finished.execution.id.clone()),
            RunEventKind::TestSkipped(skipped) => Some(skipped.execution.id.clone()),
            _ => None,
        })
        .collect();
    for execution in planned.into_iter().filter(|execution| !reached.contains(&execution.id)) {
        events.push(RunEvent::synthesized(RunEventKind::TestSkipped(Skipped {
            timestamp: now_stamp(),
            execution,
            reason: reason.to_owned(),
        })));
    }
    let cases = avl_junit::cases::cases(&events).len() + avl_junit::cases::unreported_classes(&events).len();
    (avl_junit::render([events.as_slice()]), cases)
}

/// The launch key and the launch name of each IDE run that an `ideLaunched` record names and no `ideExited` record of
/// its pid ended, in launch order.
pub(crate) fn unended_ide_runs(events: &[RunEvent]) -> Vec<(String, String)> {
    let mut running: Vec<(i64, String, String)> = Vec::new();
    for event in events {
        match &event.kind {
            RunEventKind::IdeLaunched(launched) => {
                running.push((launched.pid, launched.launch_key.clone(), launched.launch_name.clone()));
            }
            RunEventKind::IdeExited(exited) => running.retain(|(pid, _, _)| *pid != exited.pid),
            _ => {}
        }
    }
    running.into_iter().map(|(_, key, name)| (key, name)).collect()
}

/// What the report says of the thread `own` that writes it. That thread reads its own `syscall` file, so its system call
/// is a read on a descriptor that is closed when the report names it.
pub(crate) const OWN_THREAD: &str = "the monitor, which writes this report";

/// The id of the thread that calls this, from `/proc/thread-self`, or `None` on a host without it.
pub(crate) fn own_thread() -> Option<u64> {
    fs::read_link("/proc/thread-self").ok()?.file_name()?.to_str()?.parse().ok()
}

/// One line for each thread of the process that `process` names, `/proc/self` outside a test: its id, its name, its
/// state, its wait channel and its system call. A system call on a file descriptor names the target of the descriptor,
/// and `wait4` names the pid it waits for. The thread `own` is named as the monitor alone. A host without `/proc` gets
/// one line that says so.
pub(crate) fn thread_lines(process: &Path, own: Option<u64>) -> Vec<String> {
    let Ok(tasks) = fs::read_dir(process.join("task")) else {
        return vec![format!("lane-run: no thread report: {} has no task directory", process.display())];
    };
    let mut threads: Vec<(u64, PathBuf)> = tasks
        .filter_map(Result::ok)
        .filter_map(|task| Some((task.file_name().to_str()?.parse().ok()?, task.path())))
        .collect();
    threads.sort_by_key(|(tid, _)| *tid);
    threads
        .into_iter()
        .map(|(tid, task)| {
            let read = |name: &str| fs::read_to_string(task.join(name)).map(|text| text.trim().to_owned());
            let name = read("comm").unwrap_or_default();
            if own == Some(tid) {
                return format!("lane-run: thread {tid} '{name}': {OWN_THREAD}");
            }
            let state = read("stat").ok().and_then(|stat| task_state(&stat)).unwrap_or('?');
            let wchan = read("wchan")
                .ok()
                .filter(|wchan| !wchan.is_empty())
                .unwrap_or_else(|| "?".to_owned());
            let call = read("syscall").map_or_else(|_| "unreadable".to_owned(), |line| describe_syscall(&line, process));
            format!("lane-run: thread {tid} '{name}': state {state}, wchan {wchan}, syscall {call}")
        })
        .collect()
}

/// The state letter of a `stat` line, the field after the name in parentheses. The name can hold a `)`, so the state is
/// read after the last one.
fn task_state(stat: &str) -> Option<char> {
    stat.rsplit_once(')')?.1.trim_start().chars().next()
}

/// A `syscall` line in words: `running`, `none` for a thread that waits outside a system call, or the name of the call
/// with the target of its file descriptor.
fn describe_syscall(line: &str, process: &Path) -> String {
    let mut fields = line.split_whitespace();
    let Some(number) = fields.next() else {
        return "unknown".to_owned();
    };
    let Ok(number) = number.parse::<i64>() else {
        return number.to_owned();
    };
    if number < 0 {
        return "none".to_owned();
    }
    let argument = fields
        .next()
        .and_then(|argument| u64::from_str_radix(argument.trim_start_matches("0x"), 16).ok());
    let Some((name, kind)) = syscall(number) else {
        return format!("{number}");
    };
    match (kind, argument) {
        (Argument::Descriptor, Some(fd)) => {
            let target = fs::read_link(process.join("fd").join(fd.to_string()))
                .map_or_else(|_| "an unreadable descriptor".to_owned(), |target| target.display().to_string());
            format!("{name} on fd {fd} -> {target}")
        }
        (Argument::Pid, Some(pid)) => format!("{name} of pid {}", pid.cast_signed()),
        _ => name.to_owned(),
    }
}

/// What the first argument of a system call names.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Argument {
    Descriptor,
    Pid,
    Other,
}

/// The name of system call `number` of this architecture and what its first argument names, for the calls that a
/// thread waits in.
fn syscall(number: i64) -> Option<(&'static str, Argument)> {
    SYSCALLS
        .iter()
        .find(|(known, _, _)| *known == number)
        .map(|(_, name, argument)| (*name, *argument))
}

#[cfg(target_arch = "x86_64")]
const SYSCALLS: &[(i64, &str, Argument)] = &[
    (0, "read", Argument::Descriptor),
    (1, "write", Argument::Descriptor),
    (7, "poll", Argument::Other),
    (17, "pread64", Argument::Descriptor),
    (19, "readv", Argument::Descriptor),
    (20, "writev", Argument::Descriptor),
    (23, "select", Argument::Other),
    (35, "nanosleep", Argument::Other),
    (42, "connect", Argument::Descriptor),
    (43, "accept", Argument::Descriptor),
    (44, "sendto", Argument::Descriptor),
    (45, "recvfrom", Argument::Descriptor),
    (46, "sendmsg", Argument::Descriptor),
    (47, "recvmsg", Argument::Descriptor),
    (61, "wait4", Argument::Pid),
    (202, "futex", Argument::Other),
    (230, "clock_nanosleep", Argument::Other),
    (232, "epoll_wait", Argument::Descriptor),
    (247, "waitid", Argument::Other),
    (270, "pselect6", Argument::Other),
    (271, "ppoll", Argument::Other),
    (281, "epoll_pwait", Argument::Descriptor),
    (288, "accept4", Argument::Descriptor),
];

#[cfg(target_arch = "aarch64")]
const SYSCALLS: &[(i64, &str, Argument)] = &[
    (22, "epoll_pwait", Argument::Descriptor),
    (63, "read", Argument::Descriptor),
    (64, "write", Argument::Descriptor),
    (65, "readv", Argument::Descriptor),
    (66, "writev", Argument::Descriptor),
    (67, "pread64", Argument::Descriptor),
    (72, "pselect6", Argument::Other),
    (73, "ppoll", Argument::Other),
    (95, "waitid", Argument::Other),
    (98, "futex", Argument::Other),
    (101, "nanosleep", Argument::Other),
    (115, "clock_nanosleep", Argument::Other),
    (202, "accept", Argument::Descriptor),
    (203, "connect", Argument::Descriptor),
    (206, "sendto", Argument::Descriptor),
    (207, "recvfrom", Argument::Descriptor),
    (211, "sendmsg", Argument::Descriptor),
    (212, "recvmsg", Argument::Descriptor),
    (242, "accept4", Argument::Descriptor),
    (260, "wait4", Argument::Pid),
];

#[cfg(not(any(target_arch = "x86_64", target_arch = "aarch64")))]
const SYSCALLS: &[(i64, &str, Argument)] = &[];
