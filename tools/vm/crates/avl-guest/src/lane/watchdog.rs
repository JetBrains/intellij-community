//! The watchdog of one run: the deadlines of the run, the intruder probe, the dumps of a slow step, and the heartbeat.
//!
//! The watchdog thread ticks each [`TICK`] while the run thread waits for the IDE:
//! - **Deadlines.** Between scenarios the progress gap of the run request applies, during a scenario its active
//!   execution timeout. An emergency deadline caps the whole run from the count of discovered scenarios. On expiry the
//!   watchdog marks the run expired, kills the IDE run with a thread dump, and writes `watchdogExpired` with the files
//!   of its evidence. The run thread sees the mark at its next instruction and starts no further scenario.
//! - **Intruder probe.** `ModalIntruder` with a [`PROBE_TIMEOUT`] asks the IDE for a modal window that no scenario
//!   opened. The first intrusion of a run gets one `DismissIntruder` with a [`DISMISS_TIMEOUT`]; the next one that a
//!   tick sees expires the run as `ide_intrusion`. A relaunch holds the probe off, because no IDE answers then.
//! - **Slow steps.** A step of the run, such as a reset, a relaunch or an instruction, that is open past
//!   [`SLOW_STEP_BUDGET`] gets the dumps of the IDE once: the answer of [`DUMP_ROUTE`] into
//!   `<iterationDir>/slow-steps/NN-<slug>-ide-dump.txt`, at most [`MAX_DUMPS`] per run, and a `slowStep` record. The
//!   resident plugin serves that route, so a step that hangs in a reload of the bridge plugin or of Air gets its dump.
//! - **Heartbeat.** A `watchdogState` record each [`HEARTBEAT`], so the controller sees a live run during a long scenario.
//! - **Quiet.** A suite of a bare IDE measures the start-up of its IDE, and a request of the watchdog would load classes
//!   into the count. While [`Watch::quiet`] runs, the watchdog sends the IDE no probe and no dump. The deadlines and the
//!   heartbeat go on.
//!
//! Every deadline runs on the monotonic clock. Only the instants of the wire read the wall clock, anchored once.

use std::fs;
use std::path::{Path, PathBuf};
use std::sync::{Condvar, Mutex, MutexGuard, PoisonError};
use std::time::{Duration, Instant, SystemTime};

use avl_wire::daemon::{ExecutionIdentity, RunEventKind, SlowStep, WatchdogExpired, WatchdogState};
use avl_wire::report::MAX_EVIDENCE_FILES;
use jiff::Timestamp;

use super::readiness::Bridge;
use super::stream::Stream;
use crate::clock::{now_stamp, stamp};
use crate::supervisor::thread_dump;

#[cfg(test)]
mod tests;

/// How often the watchdog thread wakes.
pub(crate) const TICK: Duration = Duration::from_secs(1);
/// How often the watchdog writes its state while nothing else changes.
pub(crate) const HEARTBEAT: Duration = Duration::from_mins(1);
/// The name of the watchdog thread, which a hang report shows.
pub(crate) const WATCHDOG_THREAD: &str = "lane-watchdog";
/// How long the run thread may sit in one step before the IDE dumps its threads.
pub(crate) const SLOW_STEP_BUDGET: Duration = Duration::from_secs(30);
/// How many steps of one run get dumps.
pub(crate) const MAX_DUMPS: u32 = 4;
/// The deadline of one intruder probe, `AIR_UI_TEST_PROBE_TIMEOUT_MS`.
pub(crate) const PROBE_TIMEOUT: Duration = Duration::from_secs(2);
/// The deadline of the one dismissal of a run, `AIR_UI_TEST_DISMISS_INTRUDER_TIMEOUT_MS`.
pub(crate) const DISMISS_TIMEOUT: Duration = Duration::from_secs(15);
/// The deadline of the dumps of the IDE, `AIR_UI_TEST_DIAGNOSTIC_DUMP_TIMEOUT_MS`.
pub(crate) const DUMP_TIMEOUT: Duration = Duration::from_secs(10);
/// The dump route of the resident plugin, `AIR_UI_TEST_RESIDENT_DUMP_PREFIX` with its leading slash. The resident plugin
/// never unloads, so the route answers while a reload unloads the bridge plugin or Air.
pub(crate) const DUMP_ROUTE: &str = "/api/air/ui-test-resident/dump";
/// The media type of the answer of [`DUMP_ROUTE`].
const DUMP_MEDIA_TYPE: &str = "text/plain";
/// How long a modal window may be unclaimed before it counts as an intruder.
pub(crate) const INTRUDER_GRACE_MS: u64 = 3_000;
/// What the emergency deadline adds for the kill.
pub(crate) const KILL_GRACE: Duration = Duration::from_mins(1);

/// The directory of the slow-step dumps in the directory of an iteration.
pub(crate) const SLOW_STEPS_DIR: &str = "slow-steps";
/// The directory of the screenshots in the log directory of the IDE. With `ide.performance.screenshot=heartbeat` the
/// IDE writes a set into `<NNN>_heartbeat` there each minute, with one PNG for each window.
pub(crate) const SCREENSHOTS_DIR: &str = "screenshots";
const MAX_SLUG_CHARS: usize = 40;

/// What the watchdog asks of the IDE. Every answer may be "nothing": a probe never fails a run by itself.
pub(crate) trait Probes: Sync {
    /// A description of a modal window that no scenario opened, or `None`.
    fn intrusion(&self) -> Option<String>;
    /// Cancels what [`Probes::intrusion`] reported, and answers what it did, or `None` when it did nothing.
    fn dismiss_intrusion(&self) -> Option<String>;
    /// The thread dump and the coroutine dump of the IDE, as one text.
    fn diagnostic_dump(&self) -> Result<String, String>;
    /// Kills the IDE run with a thread dump first.
    fn kill(&self) -> Result<(), String>;
    /// The log directory of the IDE run that [`Probes::kill`] ends, or `None` while no IDE run is held. The IDE writes
    /// its heartbeat screenshots there, and the kill writes its thread dump there.
    fn log_dir(&self) -> Option<PathBuf>;
}

/// Why the watchdog gave up on a run, the `reason` of `watchdogExpired`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum ExpiryReason {
    ActiveExecution,
    ProgressGap,
    Emergency,
    IdeIntrusion,
    /// The budget of the whole run ended, see [`Policy::run_end`].
    RunBudget,
}

impl ExpiryReason {
    pub(crate) const fn wire(self) -> &'static str {
        match self {
            Self::ActiveExecution => "active_execution_timeout",
            Self::ProgressGap => "progress_gap_timeout",
            Self::Emergency => "emergency_timeout",
            Self::IdeIntrusion => "ide_intrusion",
            Self::RunBudget => "run_budget_timeout",
        }
    }
}

/// The phase of the run, as `watchdogState` names it.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Phase {
    ProgressGap,
    ActiveExecution,
    Completed,
}

impl Phase {
    const fn wire(self) -> &'static str {
        match self {
            Self::ProgressGap => "progress_gap",
            Self::ActiveExecution => "active_execution",
            Self::Completed => "completed",
        }
    }
}

/// The budgets of the run request.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Policy {
    pub active_execution: Duration,
    pub progress_gap: Duration,
    /// The end of the budget of the whole run, from `runBudgetSec` of the request. No deadline passes it, and the
    /// watchdog ends the run at it. `None` sets no budget.
    pub run_end: Option<Instant>,
}

/// One expiry of the deadlines.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Expiry {
    pub reason: ExpiryReason,
    pub deadline: Instant,
    pub at: Instant,
    pub detail: Option<String>,
    pub active: Option<ExecutionIdentity>,
}

/// The pure deadline model of one run: a progress gap between scenarios, an active execution budget during one, and an
/// emergency cap from the count of discovered scenarios.
#[derive(Clone, Debug)]
pub(crate) struct Deadlines {
    policy: Policy,
    started: Instant,
    phase: Phase,
    phase_deadline: Option<Instant>,
    emergency: Option<Instant>,
    active: Option<ExecutionIdentity>,
    /// When the scenario in [`Self::active`] started.
    active_since: Option<Instant>,
}

impl Deadlines {
    pub(crate) fn new(policy: Policy, now: Instant) -> Self {
        Self {
            policy,
            started: now,
            phase: Phase::ProgressGap,
            phase_deadline: Some(cap(now + policy.progress_gap, policy.run_end)),
            emergency: None,
            active: None,
            active_since: None,
        }
    }

    /// The plan names `count` scenarios: the emergency deadline holds a gap before each and after the last, the active
    /// budget of each, and the kill.
    pub(crate) fn plan_started(&mut self, count: u32, now: Instant) {
        let gaps = self.policy.progress_gap.saturating_mul(count.saturating_add(2));
        let active = self.policy.active_execution.saturating_mul(count);
        self.emergency = Some(cap(self.started + gaps + active + KILL_GRACE, self.policy.run_end));
        self.enter_gap(now);
    }

    pub(crate) fn execution_started(&mut self, identity: ExecutionIdentity, now: Instant) {
        if self.phase == Phase::Completed {
            return;
        }
        self.phase = Phase::ActiveExecution;
        self.phase_deadline = Some(cap(now + self.policy.active_execution, self.policy.run_end));
        self.active = Some(identity);
        self.active_since = Some(now);
    }

    pub(crate) fn execution_finished(&mut self, now: Instant) {
        self.enter_gap(now);
    }

    pub(crate) fn completed(&mut self) {
        self.phase = Phase::Completed;
        self.phase_deadline = None;
        self.emergency = None;
        self.active = None;
        self.active_since = None;
    }

    fn enter_gap(&mut self, now: Instant) {
        if self.phase == Phase::Completed {
            return;
        }
        self.phase = Phase::ProgressGap;
        self.phase_deadline = Some(cap(now + self.policy.progress_gap, self.policy.run_end));
        self.active = None;
        self.active_since = None;
    }

    /// The expiry at `now`, or `None` while every deadline holds.
    pub(crate) fn expiry(&self, now: Instant) -> Option<Expiry> {
        if self.phase == Phase::Completed {
            return None;
        }
        let expired = |reason, deadline| Expiry {
            reason,
            deadline,
            at: now,
            detail: None,
            active: self.active.clone(),
        };
        if let Some(end) = self.policy.run_end.filter(|end| now >= *end) {
            return Some(expired(ExpiryReason::RunBudget, end));
        }
        if let Some(emergency) = self.emergency.filter(|emergency| now >= *emergency) {
            return Some(expired(ExpiryReason::Emergency, emergency));
        }
        let deadline = self.phase_deadline.filter(|deadline| now >= *deadline)?;
        let reason = if self.phase == Phase::ActiveExecution {
            ExpiryReason::ActiveExecution
        } else {
            ExpiryReason::ProgressGap
        };
        Some(expired(reason, deadline))
    }

    /// The expiry that the intrusion `detail` causes now, or `None` once the run completed.
    pub(crate) fn intrusion(&self, detail: String, now: Instant) -> Option<Expiry> {
        (self.phase != Phase::Completed).then(|| Expiry {
            reason: ExpiryReason::IdeIntrusion,
            deadline: now,
            at: now,
            detail: Some(detail),
            active: self.active.clone(),
        })
    }

    fn next_deadline(&self) -> Option<Instant> {
        [self.phase_deadline, self.emergency].into_iter().flatten().min()
    }
}

/// `deadline`, or the end of the run budget when that comes first.
fn cap(deadline: Instant, run_end: Option<Instant>) -> Instant {
    run_end.map_or(deadline, |end| deadline.min(end))
}

/// What the run thread does now, as the watch knows it. The monitor of `lane-run` reads it without any lock that the
/// run thread holds for longer than a field update.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct RunSnapshot {
    /// The scenario that runs, as `<class>.<scenario>`, and for how long.
    pub scenario: Option<(String, Duration)>,
    /// The open steps of the run thread, outermost first and joined by ` > `, and how long the innermost one is open.
    pub steps: Option<(String, Duration)>,
    /// What is left of the budget of the run, when the run has one.
    pub budget_left: Option<Duration>,
}

/// A step of the run thread that the slow-step dumps watch.
#[derive(Clone, Debug)]
struct OpenStep {
    title: String,
    started: Instant,
    dumped: bool,
}

struct State {
    deadlines: Deadlines,
    steps: Vec<OpenStep>,
    relaunching: u32,
    /// How many callers hold the IDE free of every request of the watchdog, see [`Watch::quiet`].
    quiet: u32,
    expired: Option<String>,
    /// The IDE dumps that the watchdog wrote and the run thread did not attach yet: the step and the file.
    dumps: Vec<(String, PathBuf)>,
    /// The IDE dumps that the watchdog wrote since the phase began, oldest first: of the scenario that runs, or of the
    /// gap between two scenarios. An expiry names them as its evidence.
    phase_dumps: Vec<PathBuf>,
}

/// What the run thread and the watchdog thread share.
pub(crate) struct Watch {
    state: Mutex<State>,
    changed: Condvar,
    anchor: (Instant, Timestamp),
    /// How long a step may be open before the IDE dumps its threads, [`SLOW_STEP_BUDGET`] outside a test.
    slow_step: Duration,
    /// How long the watchdog waits for the run thread to complete after a kill, [`KILL_GRACE`] outside a test.
    kill_grace: Duration,
}

impl Watch {
    pub(crate) fn new(policy: Policy) -> Self {
        let now = Instant::now();
        Self {
            state: Mutex::new(State {
                deadlines: Deadlines::new(policy, now),
                steps: Vec::new(),
                relaunching: 0,
                quiet: 0,
                expired: None,
                dumps: Vec::new(),
                phase_dumps: Vec::new(),
            }),
            changed: Condvar::new(),
            anchor: (now, Timestamp::now()),
            slow_step: SLOW_STEP_BUDGET,
            kill_grace: KILL_GRACE,
        }
    }

    /// The same watch with a kill grace of `grace`, so a test sees the end of the wait after a kill at once.
    #[cfg(test)]
    pub(crate) const fn with_kill_grace(mut self, grace: Duration) -> Self {
        self.kill_grace = grace;
        self
    }

    /// Waits at most `within` for the run thread to complete the watch, and answers whether it did.
    fn await_completion(&self, within: Duration) -> bool {
        let deadline = Instant::now() + within;
        let mut state = self.lock();
        while state.deadlines.phase != Phase::Completed {
            let left = deadline.saturating_duration_since(Instant::now());
            if left.is_zero() {
                return false;
            }
            state = self.changed.wait_timeout(state, left).unwrap_or_else(PoisonError::into_inner).0;
        }
        true
    }

    /// The same watch with a slow-step budget of `budget`, so a test sees the dumps of a step in a second.
    #[cfg(test)]
    pub(crate) const fn with_slow_step(mut self, budget: Duration) -> Self {
        self.slow_step = budget;
        self
    }

    fn lock(&self) -> MutexGuard<'_, State> {
        self.state.lock().unwrap_or_else(PoisonError::into_inner)
    }

    /// Changes the deadlines and writes the new state. A new phase starts with no dump of its own.
    fn transition(&self, stream: &Stream<'_>, change: impl FnOnce(&mut Deadlines, Instant)) {
        let record = {
            let mut state = self.lock();
            change(&mut state.deadlines, Instant::now());
            state.phase_dumps.clear();
            self.changed.notify_all();
            self.record(&state.deadlines)
        };
        stream.emit(&record);
    }

    pub(crate) fn plan_started(&self, stream: &Stream<'_>, count: u32) {
        self.transition(stream, |deadlines, now| deadlines.plan_started(count, now));
    }

    pub(crate) fn execution_started(&self, stream: &Stream<'_>, identity: ExecutionIdentity) {
        self.transition(stream, |deadlines, now| deadlines.execution_started(identity, now));
    }

    pub(crate) fn execution_finished(&self, stream: &Stream<'_>) {
        self.transition(stream, Deadlines::execution_finished);
    }

    /// Ends the watch: the watchdog thread returns at its next wake.
    pub(crate) fn completed(&self, stream: &Stream<'_>) {
        self.transition(stream, |deadlines, _| deadlines.completed());
    }

    /// What is left of the budget of the run now, or `None` for a run with no budget.
    pub(crate) fn budget_left(&self) -> Option<Duration> {
        self.lock()
            .deadlines
            .policy
            .run_end
            .map(|end| end.saturating_duration_since(Instant::now()))
    }

    /// What the run thread does now.
    pub(crate) fn snapshot(&self) -> RunSnapshot {
        let now = Instant::now();
        let state = self.lock();
        let deadlines = &state.deadlines;
        let scenario = deadlines.active.as_ref().zip(deadlines.active_since).map(|(active, since)| {
            let class = active
                .class_name
                .as_deref()
                .map(avl_junit::cases::simple_class_name)
                .unwrap_or_default();
            (format!("{class}.{}", active.display_name), now.saturating_duration_since(since))
        });
        let steps = state.steps.last().map(|innermost| {
            let path = state.steps.iter().map(|step| step.title.as_str()).collect::<Vec<_>>().join(" > ");
            (path, now.saturating_duration_since(innermost.started))
        });
        RunSnapshot {
            scenario,
            steps,
            budget_left: deadlines.policy.run_end.map(|end| end.saturating_duration_since(now)),
        }
    }

    /// Why the watchdog ended the run, once it did.
    pub(crate) fn expired(&self) -> Option<String> {
        self.lock().expired.clone()
    }

    /// Runs `work` as the step `title`, whose dumps the watchdog writes when it is open past its budget.
    pub(crate) fn step<T>(&self, title: &str, work: impl FnOnce() -> T) -> T {
        self.lock().steps.push(OpenStep {
            title: title.to_owned(),
            started: Instant::now(),
            dumped: false,
        });
        let result = work();
        self.lock().steps.pop();
        result
    }

    /// The IDE dumps of slow steps that the watchdog wrote since the last call, each with the title of its step.
    pub(crate) fn take_dumps(&self) -> Vec<(String, PathBuf)> {
        std::mem::take(&mut self.lock().dumps)
    }

    /// Runs `work` while no IDE answers: the probe is held off.
    pub(crate) fn relaunching<T>(&self, work: impl FnOnce() -> T) -> T {
        self.lock().relaunching += 1;
        let result = work();
        self.lock().relaunching -= 1;
        result
    }

    /// Runs `work` while the IDE must get no request of the watchdog: neither the probe nor the dump of a slow step.
    pub(crate) fn quiet<T>(&self, work: impl FnOnce() -> T) -> T {
        self.lock().quiet += 1;
        let result = work();
        self.lock().quiet -= 1;
        result
    }

    /// The `watchdogState` record of `deadlines`.
    fn record(&self, deadlines: &Deadlines) -> RunEventKind {
        let millis = |duration: Duration| i64::try_from(duration.as_millis()).unwrap_or(i64::MAX);
        let now = Instant::now();
        RunEventKind::WatchdogState(WatchdogState {
            timestamp: now_stamp(),
            phase: deadlines.phase.wire().to_owned(),
            active_execution_timeout_ms: millis(deadlines.policy.active_execution),
            progress_gap_timeout_ms: millis(deadlines.policy.progress_gap),
            phase_deadline: deadlines.phase_deadline.map(|deadline| self.wire_instant(deadline)),
            emergency_deadline: deadlines.emergency.map(|deadline| self.wire_instant(deadline)),
            next_deadline_in_ms: deadlines
                .next_deadline()
                .map(|deadline| millis(deadline.saturating_duration_since(now))),
            active_execution: deadlines.active.clone(),
        })
    }

    /// `at` on the wall clock that the watch read once at its start.
    fn wire_instant(&self, at: Instant) -> String {
        let (instant, wall) = self.anchor;
        let shifted = if at >= instant {
            jiff::SignedDuration::try_from(at - instant)
                .ok()
                .and_then(|ahead| wall.checked_add(ahead).ok())
        } else {
            jiff::SignedDuration::try_from(instant - at)
                .ok()
                .and_then(|behind| wall.checked_sub(behind).ok())
        };
        stamp(shifted.unwrap_or(wall))
    }
}

/// The watchdog thread of a run: it returns when the watch completes, or after it expired the run and the run thread
/// completed or the kill grace ended.
pub(crate) fn watch(watch: &Watch, stream: &Stream<'_>, probes: &dyn Probes, iteration_dir: &Path) {
    let mut next_heartbeat = Instant::now() + HEARTBEAT;
    let mut dismissal: Option<Option<String>> = None;
    let mut dumps = 0;
    loop {
        let (expiry, probing, overdue) = {
            let state = watch.lock();
            if state.deadlines.phase == Phase::Completed {
                return;
            }
            let now = Instant::now();
            let quiet = state.quiet > 0;
            let overdue = state
                .steps
                .iter()
                .position(|step| !quiet && !step.dumped && now.duration_since(step.started) >= watch.slow_step);
            (state.deadlines.expiry(now), state.relaunching == 0 && !quiet, overdue)
        };
        if let Some(expiry) = expiry {
            expire(watch, stream, probes, &expiry);
            return;
        }
        if probing && let Some(detail) = probes.intrusion() {
            match &dismissal {
                None => {
                    // Once per run, before any kill: the bridge answers from under the nested event pump of the
                    // modal, so the channel that read the dialog can cancel it. The next tick decides the run.
                    dismissal = Some(probes.dismiss_intrusion());
                }
                Some(dismissed) => {
                    let verdict = match dismissed {
                        Some(dismissed) => format!("{detail}. The run tried to clear it first: {dismissed}"),
                        None => detail,
                    };
                    let expiry = watch.lock().deadlines.intrusion(verdict, Instant::now());
                    if let Some(expiry) = expiry {
                        expire(watch, stream, probes, &expiry);
                        return;
                    }
                }
            }
        }
        if let Some(index) = overdue {
            let step = {
                let mut state = watch.lock();
                state.steps.get_mut(index).map(|step| {
                    step.dumped = true;
                    step.clone()
                })
            };
            if let Some(step) = step {
                dumps += 1;
                if dumps <= MAX_DUMPS {
                    let path = watch
                        .lock()
                        .steps
                        .iter()
                        .map(|step| step.title.clone())
                        .collect::<Vec<_>>()
                        .join(" > ");
                    let record = slow_step(probes, iteration_dir, dumps, &step, &path, watch.slow_step);
                    if let RunEventKind::SlowStep(SlowStep { ide_dump: Some(file), .. }) = &record {
                        let mut state = watch.lock();
                        state.dumps.push((step.title.clone(), PathBuf::from(file)));
                        state.phase_dumps.push(PathBuf::from(file));
                    }
                    stream.emit(&record);
                } else if dumps == MAX_DUMPS + 1 {
                    stream.note(format!(
                        "slow step '{}': this run already wrote {MAX_DUMPS} slow-step dumps, so it writes no more",
                        step.title
                    ));
                }
            }
        }
        if Instant::now() >= next_heartbeat {
            next_heartbeat = Instant::now() + HEARTBEAT;
            let record = {
                let state = watch.lock();
                watch.record(&state.deadlines)
            };
            stream.emit(&record);
        }
        let state = watch.lock();
        if state.deadlines.phase == Phase::Completed {
            return;
        }
        let _ = watch.changed.wait_timeout(state, TICK).unwrap_or_else(PoisonError::into_inner);
    }
}

/// The thread dump and the coroutine dump of the IDE behind `bridge`, as one text: the answer of [`DUMP_ROUTE`] within
/// [`DUMP_TIMEOUT`]. A byte that is not UTF-8 becomes U+FFFD, because a dump is evidence for a person.
pub(crate) fn ide_dump(bridge: &Bridge) -> Result<String, String> {
    bridge
        .get(DUMP_ROUTE, DUMP_MEDIA_TYPE, DUMP_TIMEOUT)
        .map(|body| String::from_utf8_lossy(&body).into_owned())
        .map_err(|error| error.to_string())
}

/// Marks the run expired, kills the IDE so the run thread unwinds, writes `watchdogExpired` with the files of
/// [`evidence`], and waits the kill grace for the run thread to complete.
///
/// The kill comes before the record and its log line, so an expiry ends the IDE even when the stream or the standard
/// error blocks. A run thread that does not complete within the grace waits on something other than the IDE, and the
/// watchdog says so.
fn expire(watch: &Watch, stream: &Stream<'_>, probes: &dyn Probes, expiry: &Expiry) {
    let reason = format!(
        "the watchdog ended the run: {}{}",
        expiry.reason.wire(),
        expiry.detail.as_deref().map(|detail| format!(": {detail}")).unwrap_or_default()
    );
    let dumps = {
        let mut state = watch.lock();
        state.expired = Some(reason);
        state.phase_dumps.clone()
    };
    // The log directory of the run that the kill ends, before a relaunch can publish another run.
    let log_dir = probes.log_dir();
    let killed_since = Timestamp::now();
    let killed = probes.kill();
    stream.emit(&RunEventKind::WatchdogExpired(WatchdogExpired {
        reason: expiry.reason.wire().to_owned(),
        deadline: watch.wire_instant(expiry.deadline),
        expired_at: watch.wire_instant(expiry.at),
        detail: expiry.detail.clone(),
        active_execution: expiry.active.clone(),
        evidence: evidence(log_dir.as_deref(), killed_since, &dumps),
    }));
    if let Err(error) = killed {
        stream.note(format!("the watchdog could not kill the IDE: {error}"));
    }
    if !watch.await_completion(watch.kill_grace) {
        stream.note(format!(
            "the run thread did not unwind within {} ms after the watchdog killed the IDE: it waits on something \
             other than the IDE",
            watch.kill_grace.as_millis()
        ));
    }
}

/// The files that an expiry names as its evidence, [`MAX_EVIDENCE_FILES`] at most: the thread dump that the kill wrote
/// in `log_dir` at `killed_since` or later, the newest heartbeat screenshots of the IDE in `log_dir`, and the IDE
/// `dumps` of the slow steps of the phase, newest first.
///
/// The lane daemon sets the log directory of the IDE itself, so it finds these files without a request to an IDE that
/// can hang. The dumps come last, because their `slowStep` records bring them to the controller in any case.
fn evidence(log_dir: Option<&Path>, killed_since: Timestamp, dumps: &[PathBuf]) -> Vec<String> {
    let kill_dump = log_dir.and_then(|directory| thread_dump::newest_since(directory, killed_since));
    let screenshots = log_dir.map_or_else(Vec::new, newest_screenshots);
    kill_dump
        .into_iter()
        .chain(screenshots)
        .chain(dumps.iter().rev().cloned())
        .take(MAX_EVIDENCE_FILES)
        .map(|file| file.display().to_string())
        .collect()
}

/// The newest screenshot set of the IDE in its log directory `log_dir`: the pictures of the subdirectory of
/// [`SCREENSHOTS_DIR`] that changed last and holds a picture, sorted by name.
fn newest_screenshots(log_dir: &Path) -> Vec<PathBuf> {
    let entries = |directory: &Path| fs::read_dir(directory).into_iter().flatten().filter_map(Result::ok);
    let mut sets: Vec<(SystemTime, PathBuf)> = entries(&log_dir.join(SCREENSHOTS_DIR))
        .filter_map(|entry| {
            let metadata = entry.metadata().ok().filter(fs::Metadata::is_dir)?;
            Some((metadata.modified().ok()?, entry.path()))
        })
        .collect();
    sets.sort();
    sets.into_iter()
        .rev()
        .map(|(_, set)| {
            let mut pictures: Vec<PathBuf> = entries(&set)
                .filter(|entry| entry.metadata().is_ok_and(|metadata| metadata.is_file()))
                .map(|entry| entry.path())
                .filter(|file| is_picture(file))
                .collect();
            pictures.sort();
            pictures
        })
        .find(|pictures| !pictures.is_empty())
        .unwrap_or_default()
}

/// Whether `file` is a PNG or a JPEG by its extension.
fn is_picture(file: &Path) -> bool {
    file.extension()
        .and_then(|extension| extension.to_str())
        .is_some_and(|extension| ["png", "jpg", "jpeg"].iter().any(|picture| extension.eq_ignore_ascii_case(picture)))
}

/// Writes the dumps of the slow step `step`, the `ordinal`th of the run, and answers its `slowStep` record.
fn slow_step(probes: &dyn Probes, iteration_dir: &Path, ordinal: u32, step: &OpenStep, path: &str, budget: Duration) -> RunEventKind {
    let elapsed = step.started.elapsed();
    let directory = iteration_dir.join(SLOW_STEPS_DIR);
    let stem = format!("{ordinal:02}-{}", slug(&step.title));
    let header = format!(
        "Slow step: {path}\nOpen for {} ms of a {} ms budget, at {}\n\n",
        elapsed.as_millis(),
        budget.as_millis(),
        now_stamp()
    );
    let write = |name: String, text: &str| -> Result<PathBuf, String> {
        let file = directory.join(name);
        fs::create_dir_all(&directory)
            .and_then(|()| fs::write(&file, format!("{header}{text}")))
            .map(|()| file.clone())
            .map_err(|error| format!("cannot write {}: {error}", file.display()))
    };
    let own = write(
        format!("{stem}-daemon-threads.txt"),
        "The lane daemon runs no JVM. Its run thread waits in the step above for an answer of the IDE.\n",
    );
    let (ide_dump, ide_dump_error) = match probes
        .diagnostic_dump()
        .and_then(|text| write(format!("{stem}-ide-dump.txt"), &text))
    {
        Ok(file) => (Some(file.display().to_string()), None),
        Err(error) => (None, Some(error)),
    };
    RunEventKind::SlowStep(SlowStep {
        timestamp: now_stamp(),
        step: step.title.clone(),
        path: path.to_owned(),
        budget_ms: i64::try_from(budget.as_millis()).unwrap_or(i64::MAX),
        elapsed_ms: i64::try_from(elapsed.as_millis()).unwrap_or(i64::MAX),
        thread_dump: own.map_or_else(|error| error, |file| file.display().to_string()),
        ide_dump,
        ide_dump_error,
    })
}

/// The title of a step as a file name part: lowercase letters and digits, and `-` for every other run of characters.
pub(crate) fn slug(title: &str) -> String {
    let mut slug = String::new();
    for character in title.chars().flat_map(char::to_lowercase) {
        if character.is_ascii_lowercase() || character.is_ascii_digit() {
            slug.push(character);
        } else if !slug.ends_with('-') {
            slug.push('-');
        }
    }
    let slug: String = slug.trim_matches('-').chars().take(MAX_SLUG_CHARS).collect();
    let slug = slug.trim_end_matches('-');
    if slug.is_empty() { "step".to_owned() } else { slug.to_owned() }
}
