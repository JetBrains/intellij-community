//! What the dashboard shows, and how each progress event changes it.
//!
//! [`State::apply`] is pure: an event, its worker and the time in, the scrollback lines that are complete now
//! out. The live region and the card are rendered from the same state by [`crate::console::view`].

use std::collections::{BTreeMap, BTreeSet};
use std::time::{Duration, SystemTime};

use avl_base::Scope;
use avl_base::format::{first_line, format_elapsed};
use avl_base::plain::checkout_line;
use avl_trace_tools::viewer::{run_url, viewer_url};
use avl_wire::daemon::{RunEvent, RunEventKind};
use avl_wire::progress::{
    BuildSummary, Checkout, Decision, Event, Expected, Phase, PhaseChange, PhaseState, RunFinished, RunPlanned, Subject, TraceReady,
    Verdict,
};

use crate::console::paint::{Hue, Paint};

#[cfg(test)]
mod tests;

/// The order a run goes through its phases. Before the first phase starts, the dashboard shows each of them as
/// pending, so a person sees the whole plan.
const CANONICAL_PHASES: [Phase; 6] = [
    Phase::HostBuild,
    Phase::Lease,
    Phase::DaemonStart,
    Phase::Iteration,
    Phase::Traces,
    Phase::Release,
];

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum RowState {
    Pending,
    Active,
    Done,
    Failed,
    Skipped,
}

/// One phase of one worker, as the checklist shows it. A phase that starts again is a new row.
#[derive(Clone, Debug)]
pub(crate) struct PhaseRow {
    pub(crate) phase: Phase,
    pub(crate) worker: String,
    pub(crate) state: RowState,
    /// The phase's started detail, then its last progress line.
    pub(crate) detail: String,
    /// What the phase's typical time is kept under; see [`Expected::phase`].
    pub(crate) key: Option<String>,
    /// The decision that explains this row, such as a daemon restart and its reason.
    pub(crate) why: String,
    /// What the phase did, such as the actions a host build ran. It explains a slow phase.
    pub(crate) summary: String,
    pub(crate) started: SystemTime,
    pub(crate) elapsed: Duration,
    pub(crate) failure: String,
}

impl PhaseRow {
    fn pending(phase: Phase, worker: &str) -> Self {
        Self {
            phase,
            worker: worker.to_owned(),
            state: RowState::Pending,
            detail: String::new(),
            key: None,
            why: String::new(),
            summary: String::new(),
            started: SystemTime::UNIX_EPOCH,
            elapsed: Duration::ZERO,
            failure: String::new(),
        }
    }
}

/// The test a worker runs now.
pub(crate) struct RunningTest {
    pub(crate) name: String,
    pub(crate) started: SystemTime,
}

/// The class wall times of one worker's iteration, as its records arrive. It measures a class the way
/// `avl_base::history` does, so a class's time in this run and its typical time compare.
pub(crate) struct WorkerClasses {
    boundary: SystemTime,
    pub(crate) current: String,
    pub(crate) started: SystemTime,
}

/// One item of the scrollback, in the order of publication.
///
/// A scrollback line cannot change once it is printed, and a scenario's trace arrives after its test finished. So
/// a finished test waits in the queue, and its line gets the trace link when the trace arrives. It stops waiting
/// when the trace arrives, when the next test of its worker finishes, when its worker's traces phase ends, when
/// its iteration's report is persisted, or when the run finishes. Each of these is an event, so no timer exists.
/// The lines behind a waiting test wait too, so the scrollback keeps the order of the facts.
struct Entry {
    lines: Vec<String>,
    /// Set for a finished test whose trace can still arrive; worker, class and scenario name it.
    waiting: bool,
    worker: String,
    class: String,
    scenario: String,
}

impl Entry {
    const fn lines(lines: Vec<String>) -> Self {
        Self {
            lines,
            waiting: false,
            worker: String::new(),
            class: String::new(),
            scenario: String::new(),
        }
    }
}

/// Everything the dashboard shows. It is changed only by [`State::apply`], under the reporter's lock.
pub(crate) struct State {
    pub(crate) paint: Paint,
    pub(crate) rerun_prefix: String,
    pub(crate) viewer_port: u16,
    pub(crate) viewer_note: String,

    pub(crate) run_id: String,
    command: String,
    args: Vec<String>,
    pub(crate) started_at: Option<SystemTime>,
    pub(crate) checkout: Option<Checkout>,
    pub(crate) expected: Option<Expected>,

    pub(crate) rows: Vec<PhaseRow>,
    pub(crate) workers: BTreeSet<String>,

    pub(crate) total: u32,
    pub(crate) passed: u32,
    pub(crate) failed: u32,
    pub(crate) skipped: u32,
    /// Every class the iterations discovered, in discovery order.
    pub(crate) classes: Vec<String>,
    pub(crate) class_done: BTreeMap<String, Duration>,
    pub(crate) running: BTreeMap<String, RunningTest>,
    pub(crate) per_worker: BTreeMap<String, WorkerClasses>,

    /// The scrollback that has not been printed yet: see [`Entry`].
    queue: Vec<Entry>,
    /// The simple name of the class whose header the scrollback printed last, or "" when another line followed
    /// it. With one worker, the tests of a class print under one header.
    group: String,

    pub(crate) decisions: Vec<Decision>,

    pub(crate) verdict: Option<Box<Verdict>>,
    pub(crate) finished: Option<RunFinished>,
    pub(crate) finished_at: Option<SystemTime>,
}

impl State {
    pub(crate) fn new(rerun_prefix: &str, paint: Paint) -> Self {
        Self {
            paint,
            rerun_prefix: rerun_prefix.to_owned(),
            viewer_port: 0,
            viewer_note: String::new(),
            run_id: String::new(),
            command: String::new(),
            args: Vec::new(),
            started_at: None,
            checkout: None,
            expected: None,
            rows: CANONICAL_PHASES.iter().map(|phase| PhaseRow::pending(*phase, "")).collect(),
            workers: BTreeSet::new(),
            total: 0,
            passed: 0,
            failed: 0,
            skipped: 0,
            classes: Vec::new(),
            class_done: BTreeMap::new(),
            running: BTreeMap::new(),
            per_worker: BTreeMap::new(),
            queue: Vec::new(),
            group: String::new(),
            decisions: Vec::new(),
            verdict: None,
            finished: None,
            finished_at: None,
        }
    }

    /// Changes the state for one event, and answers the lines that are complete now and go into the scrollback
    /// above the dashboard.
    pub(crate) fn apply(&mut self, event: &Event, scope: Option<&Scope>, now: SystemTime) -> Vec<String> {
        let worker = scope.map_or("", |scope| scope.worker.as_str());
        if scope.is_some() {
            self.workers.insert(worker.to_owned());
        }
        match event {
            Event::Daemon(event) => self.apply_daemon(event, worker, now),
            Event::TraceReady(ready) => self.apply_trace(ready, worker),
            event => {
                let lines = self.apply_other(event, worker, now);
                if !lines.is_empty() {
                    self.group.clear();
                    self.queue.push(Entry::lines(lines));
                }
            }
        }
        self.flush()
    }

    fn apply_other(&mut self, event: &Event, worker: &str, now: SystemTime) -> Vec<String> {
        let paint = self.paint;
        let prefix = self.worker_prefix(worker);
        match event {
            Event::RunStarted(started) => {
                self.run_id.clone_from(&started.run_id);
                self.command.clone_from(&started.command);
                self.args.clone_from(&started.args);
                self.started_at = Some(now);
                return self.header_lines();
            }
            Event::RunPlanned(planned) => return self.plan_lines(planned),
            Event::Checkout(checkout) => {
                self.checkout = Some(checkout.clone());
                return vec![self.checkout_line(checkout)];
            }
            Event::Expected(expected) => {
                self.expected = Some(expected.clone());
                if let Some(run) = &expected.run {
                    return vec![paint.muted(&format!(
                        "  usually {} for this command, the median of {} runs",
                        format_elapsed(ms(run.ms)),
                        run.samples
                    ))];
                }
            }
            Event::Note(message) => return vec![paint.muted(&format!("  {prefix}{message}"))],
            Event::Structured { line, .. } if !line.is_empty() => {
                return vec![paint.muted(&format!("  {prefix}{line}"))];
            }
            Event::Decision(decision) => {
                self.decisions.push(decision.clone());
                self.attach_decision(decision, worker);
                return vec![self.decision_line(decision, &prefix)];
            }
            Event::BuildSummary(summary) => {
                if let Some(row) = self.last_row(Phase::HostBuild, worker) {
                    row.summary = build_summary_text(summary);
                }
            }
            Event::Phase(change) => {
                if change.phase == Phase::Traces && matches!(change.state, PhaseState::Finished | PhaseState::Failed) {
                    self.release(worker);
                }
                return self.apply_phase(change, worker, &prefix, now);
            }
            Event::IterationReported(reported) => {
                // The report is persisted after the last pull of the iteration, so no trace of it can still arrive.
                self.release(worker);
                return vec![paint.muted(&format!("  {prefix}report {}", reported.report_path))];
            }
            Event::Verdict(verdict) => self.verdict = Some(verdict.clone()),
            Event::RunFinished(finished) => {
                self.release("");
                self.finished = Some(finished.clone());
                self.finished_at = Some(now);
            }
            Event::Structured { .. } | Event::Daemon(_) | Event::TraceReady(_) => {}
        }
        Vec::new()
    }

    /// Takes the complete entries from the head of the queue, and answers their lines.
    fn flush(&mut self) -> Vec<String> {
        let complete = self.queue.iter().position(|entry| entry.waiting).unwrap_or(self.queue.len());
        self.queue.drain(..complete).flat_map(|entry| entry.lines).collect()
    }

    /// Ends the wait of every test of `worker`, or of every worker for "".
    fn release(&mut self, worker: &str) {
        for entry in &mut self.queue {
            if worker.is_empty() || entry.worker == worker {
                entry.waiting = false;
            }
        }
    }

    /// Ends every wait and answers every line the queue still holds, for a dashboard that finishes.
    pub(crate) fn drain(&mut self) -> Vec<String> {
        self.release("");
        self.flush()
    }

    /// The lines of the queue, which the live region shows so that no finished test is out of sight.
    pub(crate) fn waiting_lines(&self) -> impl Iterator<Item = &String> {
        self.queue.iter().flat_map(|entry| &entry.lines)
    }

    fn header_lines(&self) -> Vec<String> {
        let paint = self.paint;
        let title = format!("{} {}", paint.banner(Hue::Magenta, " AIR UI lane "), paint.bold(&self.run_id));
        let mut lines = vec![String::new(), title];
        if !self.command.is_empty() {
            let command = format!("{} {} {}", self.rerun_prefix, self.command, self.args.join(" "));
            lines.push(format!("  {}", paint.bold(command.trim())));
        }
        if self.viewer_port != 0 {
            let url = run_url(self.viewer_port, &self.run_id);
            let mut line = format!("  viewer {}", paint.link(&url, &url));
            if !self.viewer_note.is_empty() {
                line.push_str(&paint.muted(&format!(" ({})", self.viewer_note)));
            }
            lines.push(line);
        }
        lines
    }

    fn plan_lines(&self, planned: &RunPlanned) -> Vec<String> {
        planned
            .iterations
            .iter()
            .map(|iteration| {
                let mut text = format!("  plan {}", iteration.selection);
                if !iteration.classes.is_empty() {
                    text.push_str(&format!(" · {} classes", iteration.classes.len()));
                }
                self.paint.muted(&text)
            })
            .collect()
    }

    fn checkout_line(&self, checkout: &Checkout) -> String {
        let paint = self.paint;
        let line = checkout_line(checkout);
        if checkout.error.is_some() {
            return format!("  {}", paint.warn(&format!("⚠ {line}")));
        }
        if checkout.uncommitted > 0 {
            return format!(
                "  {}{}",
                paint.warn(&format!("⚠ {line}")),
                paint.muted(" (a red test can be another session's edit)")
            );
        }
        format!("  {}", paint.muted(&line))
    }

    /// A decision with a mark for its kind: `♻` for a reuse, `↑` for a push, and `⟳` for a start, a restart, a
    /// remount or a relaunch, which cost time.
    fn decision_line(&self, decision: &Decision, prefix: &str) -> String {
        let paint = self.paint;
        let mark = match decision.action.as_str() {
            "reuse" => paint.pass("♻"),
            "push" => paint.active("↑"),
            _ => paint.warn("⟳"),
        };
        let text =
            paint.bold(&format!("{} {}", decision.subject, decision.action)) + &paint.muted(&reason_suffix(decision.reason.as_deref()));
        format!("  {mark} {prefix}{text}")
    }

    /// Puts a decision on the row it explains. A reused IDE means that no daemon starts, so a pending daemon start
    /// is skipped, and it says why.
    fn attach_decision(&mut self, decision: &Decision, worker: &str) {
        match decision.subject {
            Subject::Daemon => {
                if let Some(row) = self.next_row(Phase::DaemonStart, worker) {
                    row.why = decision.action.clone() + &reason_suffix(decision.reason.as_deref());
                }
            }
            Subject::Ide if decision.action == "reuse" => {
                if let Some(row) = self.next_row(Phase::DaemonStart, worker)
                    && row.state == RowState::Pending
                {
                    row.state = RowState::Skipped;
                    row.why = "the warm daemon is reused".to_owned();
                }
            }
            _ => {}
        }
    }

    /// The row of the phase that runs next for the worker: its pending or active row.
    fn next_row(&mut self, phase: Phase, worker: &str) -> Option<&mut PhaseRow> {
        self.rows.iter_mut().find(|row| {
            row.phase == phase
                && (row.worker == worker || row.worker.is_empty())
                && matches!(row.state, RowState::Pending | RowState::Active)
        })
    }

    /// The last row of the phase that started for the worker.
    fn last_row(&mut self, phase: Phase, worker: &str) -> Option<&mut PhaseRow> {
        self.rows
            .iter_mut()
            .rev()
            .find(|row| row.phase == phase && row.worker == worker && !matches!(row.state, RowState::Pending | RowState::Skipped))
    }

    fn active_row(&mut self, phase: Phase, worker: &str) -> Option<&mut PhaseRow> {
        self.rows
            .iter_mut()
            .rev()
            .find(|row| row.phase == phase && row.worker == worker && row.state == RowState::Active)
    }

    fn apply_phase(&mut self, change: &PhaseChange, worker: &str, prefix: &str, now: SystemTime) -> Vec<String> {
        let paint = self.paint;
        let detail = change.detail.as_deref().unwrap_or("");
        match change.state {
            PhaseState::Started => {
                let index = self.claim_row(change.phase, worker);
                let row = &mut self.rows[index];
                row.state = RowState::Active;
                row.started = now;
                row.detail = detail.to_owned();
                row.key = (change.phase == Phase::Iteration).then(|| change.detail.clone()).flatten();
                row.elapsed = Duration::ZERO;
                row.failure.clear();
                row.summary.clear();
                // Every pending row before a row that started is skipped: a run under a caller's lease takes no
                // lease, and a warm daemon does not start.
                for row in &mut self.rows[..index] {
                    if row.state == RowState::Pending {
                        row.state = RowState::Skipped;
                    }
                }
                Vec::new()
            }
            PhaseState::Progress => {
                if let Some(row) = self.active_row(change.phase, worker)
                    && !detail.is_empty()
                {
                    row.detail = detail.to_owned();
                }
                Vec::new()
            }
            PhaseState::Finished | PhaseState::Failed => {
                let Some(row) = self.active_row(change.phase, worker) else {
                    return Vec::new();
                };
                row.elapsed = ms(change.elapsed_ms);
                let label = paint.bold(phase_label(change.phase));
                let summary = if row.summary.is_empty() {
                    String::new()
                } else {
                    paint.muted(&format!(" — {}", row.summary))
                };
                if change.state == PhaseState::Failed {
                    row.state = RowState::Failed;
                    row.failure = first_line(change.error.as_deref().unwrap_or("")).to_owned();
                    let line = format!(
                        "  {} {prefix}{label} {}{}{summary}",
                        paint.fail("✗"),
                        paint.fail(&format!("failed after {}", format_elapsed(row.elapsed))),
                        paint.muted(&format!(" — {}", row.failure)),
                    );
                    return vec![line];
                }
                row.state = RowState::Done;
                let elapsed = format_elapsed(row.elapsed);
                let row = row.clone();
                let usual = self.usual_suffix(&row);
                vec![format!("  {} {prefix}{label} {elapsed}{usual}{summary}", paint.pass("✓"))]
            }
        }
    }

    /// The typical time of a row's phase, when earlier runs measured it.
    pub(crate) fn typical(&self, row: &PhaseRow) -> Option<Duration> {
        let typical = self.expected.as_ref()?.phase(row.phase, row.key.as_deref())?;
        Some(ms(typical.ms))
    }

    /// The typical wall time of a class, when earlier runs measured it.
    pub(crate) fn class_typical(&self, class_name: &str) -> Option<Duration> {
        let typical = self.expected.as_ref()?.classes.get(class_name)?;
        Some(ms(typical.ms))
    }

    /// Compares a finished row with its typical time: ` (usual 45s)`, marked when it took much longer.
    fn usual_suffix(&self, row: &PhaseRow) -> String {
        let Some(typical) = self.typical(row) else {
            return String::new();
        };
        let text = format!(" (usual {})", format_elapsed(typical));
        if slower(row.elapsed, typical) {
            return self.paint.warn(&format!("{text} ▲"));
        }
        self.paint.muted(&text)
    }

    /// The row a phase that starts takes: the first pending row of that phase, or a new row after the last row of
    /// that phase.
    fn claim_row(&mut self, phase: Phase, worker: &str) -> usize {
        let mut last = None;
        for (index, row) in self.rows.iter_mut().enumerate() {
            if row.phase != phase {
                continue;
            }
            if row.state == RowState::Pending && (row.worker.is_empty() || row.worker == worker) {
                row.worker = worker.to_owned();
                return index;
            }
            last = Some(index);
        }
        let index = last.map_or(self.rows.len(), |last| last + 1);
        self.rows.insert(index, PhaseRow::pending(phase, worker));
        index
    }

    fn apply_daemon(&mut self, event: &RunEvent, worker: &str, now: SystemTime) {
        let paint = self.paint;
        match &event.kind {
            RunEventKind::RunStarted(_) => {
                self.per_worker.insert(
                    worker.to_owned(),
                    WorkerClasses {
                        boundary: now,
                        current: String::new(),
                        started: now,
                    },
                );
            }
            RunEventKind::PlanStarted(plan) => {
                self.total += plan.discovered_executions;
                for name in &plan.class_names {
                    if !self.classes.contains(name) {
                        self.classes.push(name.clone());
                    }
                }
            }
            RunEventKind::TestStarted(started) => {
                let test = &started.execution;
                let class_name = test.class_name.as_deref().unwrap_or("");
                self.enter_class(worker, class_name, now);
                let name = test_name(class_name, &test.display_name, test.method_name.as_deref());
                self.running.insert(worker.to_owned(), RunningTest { name, started: now });
            }
            RunEventKind::TestFinished(finished) => {
                let test = &finished.execution;
                let class_name = test.class_name.as_deref().unwrap_or("");
                self.running.remove(worker);
                // The test before this one on the worker has had the whole of this test's run for its trace to
                // arrive.
                self.release(worker);
                let took = paint.muted(&format_elapsed(ms(finished.duration_ms)));
                let indent = self.indent(worker);
                let name = self.scenario_name(class_name, &test.display_name, test.method_name.as_deref());
                let mut lines = Vec::new();
                if finished.status == "SUCCESSFUL" {
                    self.passed += 1;
                    lines.push(format!("{indent}{} {name} {took}", paint.pass("✓")));
                } else {
                    self.failed += 1;
                    lines.push(format!("{indent}{} {took}", paint.fail(&format!("✗ {name}"))));
                    let message = first_line(finished.error.as_deref().unwrap_or(""));
                    if !message.is_empty() {
                        lines.push(format!("{indent}    {}", paint.fail(message)));
                    }
                }
                let entry = Entry {
                    lines,
                    waiting: true,
                    worker: worker.to_owned(),
                    class: short_class(class_name).to_owned(),
                    scenario: test.display_name.clone(),
                };
                self.push_test(class_name, entry);
            }
            RunEventKind::TestSkipped(skipped) => {
                let test = &skipped.execution;
                let class_name = test.class_name.as_deref().unwrap_or("");
                self.skipped += 1;
                let name = self.scenario_name(class_name, &test.display_name, test.method_name.as_deref());
                let line = self.indent(worker) + &paint.muted(&format!("↷ {name}{}", reason_suffix(Some(&skipped.reason))));
                self.push_test(class_name, Entry::lines(vec![line]));
            }
            RunEventKind::ContainerFailed(failed) => {
                self.failed += 1;
                let name = container_name(&failed.execution);
                let prefix = self.worker_prefix(worker);
                let mut lines = vec![format!("  {}", paint.fail(&format!("✗ {prefix}{name} (container)")))];
                let message = first_line(failed.error.as_deref().unwrap_or(""));
                if !message.is_empty() {
                    lines.push(format!("      {}", paint.fail(message)));
                }
                self.push_line_entry(lines);
            }
            RunEventKind::ContainerSkipped(skipped) => {
                let name = container_name(&skipped.execution);
                let prefix = self.worker_prefix(worker);
                let line = format!(
                    "  {}",
                    paint.muted(&format!("↷ {prefix}{name} skipped{}", reason_suffix(Some(&skipped.reason))))
                );
                self.push_line_entry(vec![line]);
            }
            RunEventKind::WatchdogExpired(expired) => {
                let mut line = format!("watchdog expired: {}", expired.reason);
                if let Some(detail) = expired.detail.as_deref().filter(|detail| !detail.is_empty()) {
                    line.push_str(&format!(" — {detail}"));
                }
                if let Some(active) = &expired.active_execution {
                    line.push_str(&format!(" while {}", active.describe()));
                }
                let prefix = self.worker_prefix(worker);
                let line = format!("  {}", paint.fail(&format!("⏱ {prefix}{line}")));
                self.push_line_entry(vec![line]);
            }
            RunEventKind::RunFailed(failed) => {
                let prefix = self.worker_prefix(worker);
                let line = format!(
                    "  {}",
                    paint.fail(&format!(
                        "✗ {prefix}the daemon run failed{}",
                        reason_suffix(Some(first_line(&failed.error)))
                    ))
                );
                self.push_line_entry(vec![line]);
            }
            RunEventKind::Summary(_) => {
                self.leave_class(worker, now);
                self.running.remove(worker);
                self.per_worker.remove(worker);
            }
            RunEventKind::WatchdogState(_) | RunEventKind::Output(_) => {}
        }
    }

    /// Adds an entry that is not a test's, and ends the class header's group.
    fn push_line_entry(&mut self, lines: Vec<String>) {
        self.group.clear();
        self.queue.push(Entry::lines(lines));
    }

    /// Whether the scrollback groups the tests under their class: with one worker only, because the classes of
    /// two workers interleave.
    pub(crate) fn grouped(&self) -> bool {
        self.workers.len() < 2
    }

    /// Adds a test's entry, after the header of its class when the class changed.
    fn push_test(&mut self, class_name: &str, entry: Entry) {
        let short = short_class(class_name);
        if self.grouped() && !short.is_empty() && short != self.group {
            self.group = short.to_owned();
            let header = format!("  {}", self.paint.bold(short));
            self.queue.push(Entry::lines(vec![header]));
        }
        self.queue.push(entry);
    }

    /// The start of a test's line: under its class header when grouped, and with the worker otherwise.
    fn indent(&self, worker: &str) -> String {
        if self.grouped() {
            return "    ".to_owned();
        }
        format!("  {}", self.worker_prefix(worker))
    }

    /// How a test line names its test: the scenario alone under its class header, and `Class › scenario` when the
    /// lines are flat.
    fn scenario_name(&self, class_name: &str, display_name: &str, method_name: Option<&str>) -> String {
        if self.grouped() && !short_class(class_name).is_empty() {
            return test_name("", display_name, method_name);
        }
        test_name(class_name, display_name, method_name)
    }

    /// Notes that a worker runs a test of `class_name`. When the class changes, the class before it is done, and
    /// its wall time ends where the new class's first test starts.
    fn enter_class(&mut self, worker: &str, class_name: &str, now: SystemTime) {
        let classes = self.per_worker.entry(worker.to_owned()).or_insert_with(|| WorkerClasses {
            boundary: now,
            current: String::new(),
            started: now,
        });
        if classes.current == class_name {
            return;
        }
        self.leave_class(worker, now);
        if let Some(classes) = self.per_worker.get_mut(worker) {
            classes.current = class_name.to_owned();
            classes.started = classes.boundary;
        }
    }

    fn leave_class(&mut self, worker: &str, now: SystemTime) {
        let Some(classes) = self.per_worker.get_mut(worker) else {
            return;
        };
        if classes.current.is_empty() {
            return;
        }
        let current = std::mem::take(&mut classes.current);
        *self.class_done.entry(current).or_default() += since(classes.started, now);
        classes.boundary = now;
    }

    /// Adds a trace to the line of its test while the test waits, and prints a line of its own that names the
    /// test otherwise.
    fn apply_trace(&mut self, ready: &TraceReady, worker: &str) {
        let class = short_class(&ready.test_class);
        let link = self.trace_link(ready);
        if let Some(entry) = self
            .queue
            .iter_mut()
            .find(|entry| entry.waiting && entry.worker == worker && entry.class == class && entry.scenario == ready.scenario)
        {
            if let Some(first) = entry.lines.first_mut() {
                first.push_str("  ");
                first.push_str(&link);
            }
            entry.waiting = false;
            return;
        }
        let paint = self.paint;
        let mark = if ready.status == "passed" {
            paint.pass("✓")
        } else {
            paint.fail("✗")
        };
        let line = format!(
            "{}  {mark} {link}{}",
            self.indent(worker),
            paint.muted(&format!(" · {class} › {}", ready.scenario))
        );
        self.queue.push(Entry::lines(vec![line]));
    }

    /// A trace as a short label: `trace · video`. The label is an OSC 8 link to the scenario's page in the trace
    /// viewer. Without a viewer, the label is followed by the zip.
    fn trace_link(&self, ready: &TraceReady) -> String {
        let paint = self.paint;
        let video = if ready.has_video { paint.muted(" · video") } else { String::new() };
        if self.viewer_port == 0 {
            return paint.muted("trace") + &video + &paint.muted(&format!(" {}", ready.zip));
        }
        let url = viewer_url(self.viewer_port, &ready.bundle_id, &ready.scenario);
        paint.link("trace", &url) + &video
    }

    /// `[air-docker-1] ` once a second worker was seen, and nothing for a run of one worker.
    pub(crate) fn worker_prefix(&self, worker: &str) -> String {
        if worker.is_empty() || self.workers.len() < 2 {
            return String::new();
        }
        format!("[{worker}] ")
    }
}

/// A container's name on its line: its simple class name, or its display name without a class.
fn container_name(execution: &avl_wire::daemon::ExecutionIdentity) -> String {
    let short = short_class(execution.class_name.as_deref().unwrap_or(""));
    if short.is_empty() {
        return execution.display_name.clone();
    }
    short.to_owned()
}

/// What a host build did, for the phase's line: `970 actions ran, 6684 cached · critical path 3m08s`.
fn build_summary_text(summary: &BuildSummary) -> String {
    let mut text = format!("{} actions ran, {} cached", summary.ran, summary.cached);
    if summary.critical_path_ms > 0 {
        text.push_str(&format!(" · critical path {}", format_elapsed(ms(summary.critical_path_ms))));
    }
    text
}

pub(crate) fn reason_suffix(reason: Option<&str>) -> String {
    match reason {
        Some(reason) if !reason.is_empty() => format!(" — {reason}"),
        _ => String::new(),
    }
}

/// A test the way a person reads it: `AirNewSessionFlowUiTest › opens the chooser`.
pub(crate) fn test_name(class_name: &str, display_name: &str, method_name: Option<&str>) -> String {
    let short = short_class(class_name);
    let name = match (display_name, method_name) {
        ("", Some(method)) => method,
        (display, _) => display,
    };
    let name = name.strip_suffix("()").unwrap_or(name);
    match (short, name) {
        ("", name) => name.to_owned(),
        (short, "") => short.to_owned(),
        (short, name) => format!("{short} › {name}"),
    }
}

pub(crate) fn short_class(class_name: &str) -> &str {
    class_name.rsplit('.').next().unwrap_or(class_name)
}

pub(crate) const fn phase_label(phase: Phase) -> &'static str {
    match phase {
        Phase::HostBuild => "host build",
        Phase::Lease => "lease",
        Phase::DaemonStart => "daemon start",
        Phase::Iteration => "tests",
        Phase::Traces => "traces",
        Phase::Release => "release",
    }
}

/// Whether `elapsed` is well over its typical time: half as long again, and at least 5 s more.
pub(crate) fn slower(elapsed: Duration, typical: Duration) -> bool {
    !typical.is_zero() && elapsed > typical * 3 / 2 && elapsed.saturating_sub(typical) >= Duration::from_secs(5)
}

pub(crate) fn ms(milliseconds: impl TryInto<u64>) -> Duration {
    Duration::from_millis(milliseconds.try_into().unwrap_or(0))
}

/// The time from `earlier` to `later`, or zero when `later` is not later.
pub(crate) fn since(earlier: SystemTime, later: SystemTime) -> Duration {
    later.duration_since(earlier).unwrap_or_default()
}

/// The run's page in the trace viewer, for the card.
pub(crate) fn run_link(state: &State) -> Option<String> {
    if state.viewer_port == 0 || state.run_id.is_empty() {
        return None;
    }
    let url = run_url(state.viewer_port, &state.run_id);
    Some(state.paint.link(&url, &url))
}
