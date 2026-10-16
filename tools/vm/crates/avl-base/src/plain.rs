//! The plain human renderer: how a person reads the progress stream in a log, or in a terminal without the
//! dashboard.
//!
//! Every line is `<program>: [<worker>] <text>`, the prefix every prose line of this controller has. With
//! [`Terminal::redraw`] the renderer also keeps one status footer under the lines and redraws it in place: the
//! phase in flight, its elapsed time, the pass and fail counts, and the test that runs. A redraw writes control
//! bytes, so it is only for a terminal. Without it, a long phase says "still …" at most every
//! [`PLAIN_PROGRESS_EVERY`], which is the heartbeat a log file needs.
//!
//! The verdict of a run is the answer, so the renderer writes it to stdout when the command ends, where the text
//! answer of every command goes.

use std::sync::mpsc::{self, RecvTimeoutError, Sender};
use std::sync::{Arc, Mutex, Weak};
use std::thread::JoinHandle;
use std::time::Duration;

use avl_trace_tools::viewer::DEFAULT_SERVE_PORT;
use avl_trace_tools::viewer::{run_url, viewer_url};
use avl_wire::daemon::{RunEvent, RunEventKind};
use avl_wire::progress::{Checkout, Event, Failure, Phase, PhaseChange, PhaseState, TraceReady, Verdict, VerdictLease, VerdictTraces};
use jiff::Timestamp;

use crate::clock::{self, Clock};
use crate::format::{first_line, format_elapsed, format_elapsed_ms};
use crate::report::{Renderer, Scope, SharedWriter, Terminal};
use crate::sync::lock;

#[cfg(test)]
mod tests;

/// How often a phase's progress prints as a line, without a footer to hold it.
pub const PLAIN_PROGRESS_EVERY: Duration = Duration::from_secs(15);

/// How often the footer's elapsed time is redrawn.
const FOOTER_TICK: Duration = Duration::from_secs(1);

/// A phase that started and has not ended.
struct ActivePhase {
    phase: Phase,
    worker: Option<String>,
    detail: String,
    started: Timestamp,
    /// When a progress line of this phase was last printed, in plain mode.
    printed: Timestamp,
}

/// What the renderer and its footer thread share.
struct State {
    out: SharedWriter,
    /// Where the verdict goes when the command ends: stdout.
    answer: SharedWriter,
    program: String,
    terminal: Terminal,
    clock: Arc<dyn Clock>,
    /// Every phase in flight, in start order; the footer shows the last.
    phases: Vec<ActivePhase>,
    /// The tests of this command, over every worker.
    passed: u32,
    failed: u32,
    skipped: u32,
    /// The test that runs, or empty.
    current: String,
    /// The footer text on the terminal now, or empty when none is drawn.
    footer: String,
    /// Cleared by `finish`, so a tick that was already waiting for the lock draws nothing after it.
    ticking: bool,
    /// Where the trace viewer answers, or 0; with it, a run and a trace are named by their link.
    viewer_port: u16,
    viewer_note: String,
    verdict: Option<Box<Verdict>>,
}

/// The plain [`Renderer`]. The reporter makes one for human mode.
pub struct Screen {
    state: Arc<Mutex<State>>,
    ticker: Option<Ticker>,
}

/// The thread that redraws the footer every [`FOOTER_TICK`] while a phase is in flight. Dropping it stops and
/// joins the thread.
struct Ticker {
    stop: Option<Sender<()>>,
    thread: Option<JoinHandle<()>>,
}

impl Ticker {
    fn start(state: Weak<Mutex<State>>) -> Self {
        let (stop, stopped) = mpsc::channel::<()>();
        let thread = std::thread::Builder::new()
            .name("avl-footer".to_owned())
            .spawn(move || {
                while stopped.recv_timeout(FOOTER_TICK) == Err(RecvTimeoutError::Timeout) {
                    let Some(state) = state.upgrade() else {
                        return;
                    };
                    let mut state = lock(&state);
                    if state.ticking {
                        state.redraw_footer();
                    }
                }
            })
            .ok();
        Self { stop: Some(stop), thread }
    }
}

impl Drop for Ticker {
    fn drop(&mut self) {
        // Dropping the sender wakes the thread with `Disconnected`.
        self.stop.take();
        if let Some(thread) = self.thread.take() {
            let _ = thread.join();
        }
    }
}

impl Screen {
    pub fn new(answer: SharedWriter, out: SharedWriter, program: String, terminal: Terminal, clock: Arc<dyn Clock>) -> Self {
        Self {
            state: Arc::new(Mutex::new(State {
                out,
                answer,
                program,
                terminal,
                clock,
                phases: Vec::new(),
                passed: 0,
                failed: 0,
                skipped: 0,
                current: String::new(),
                footer: String::new(),
                ticking: false,
                viewer_port: 0,
                viewer_note: String::new(),
                verdict: None,
            })),
            ticker: None,
        }
    }
}

impl Renderer for Screen {
    fn render(&mut self, event: &Event, scope: Option<&Scope>) {
        let mut state = lock(&self.state);
        state.render(event, scope);
        if state.terminal.redraw && !state.phases.is_empty() && self.ticker.is_none() {
            state.ticking = true;
            self.ticker = Some(Ticker::start(Arc::downgrade(&self.state)));
        }
    }

    fn finish(&mut self) -> Option<Box<Verdict>> {
        let verdict = {
            let mut state = lock(&self.state);
            state.clear_footer();
            state.ticking = false;
            state.phases.clear();
            let verdict = state.verdict.take();
            if let Some(verdict) = &verdict {
                let port = match state.viewer_port {
                    0 => DEFAULT_SERVE_PORT,
                    port => port,
                };
                let text = verdict_text(verdict, &state.program, port) + "\n";
                state.answer.emit(text.as_bytes());
            }
            verdict
        };
        // Outside the lock: the thread may be waiting for it.
        self.ticker = None;
        verdict
    }

    fn set_viewer(&mut self, port: u16, note: &str) {
        let mut state = lock(&self.state);
        state.viewer_port = port;
        state.viewer_note = note.to_owned();
    }
}

impl State {
    fn now(&self) -> Timestamp {
        self.clock.now()
    }

    fn render(&mut self, event: &Event, scope: Option<&Scope>) {
        match event {
            Event::Note(message) => self.line(scope, message),
            Event::Structured { line, .. } if !line.is_empty() => self.line(scope, line),
            Event::Phase(change) => self.render_phase(change, scope),
            Event::Daemon(event) => self.render_daemon(event, scope),
            Event::RunStarted(started) => {
                let mut text = format!("run {}", started.run_id);
                if self.viewer_port != 0 {
                    text.push_str(": ");
                    text.push_str(&run_url(self.viewer_port, &started.run_id));
                    if !self.viewer_note.is_empty() {
                        text.push_str(&format!(" ({})", self.viewer_note));
                    }
                }
                self.line(scope, &text);
            }
            Event::TraceReady(ready) => {
                let text = self.trace_line(ready);
                self.line(scope, &text);
            }
            Event::Decision(decision) => self.line(scope, &decision.line()),
            Event::Checkout(checkout) => self.line(scope, &checkout_line(checkout)),
            Event::Expected(expected) => {
                if let Some(run) = &expected.run {
                    let text = format!(
                        "this run usually takes {} (the median of {} runs)",
                        format_elapsed_ms(run.ms),
                        run.samples
                    );
                    self.line(scope, &text);
                }
            }
            Event::BuildSummary(summary) => {
                self.line(scope, &format!("host build: {}", summary.line()));
            }
            Event::Verdict(verdict) => self.verdict = Some(verdict.clone()),
            Event::Structured { .. } | Event::RunPlanned(_) | Event::IterationReported(_) | Event::RunFinished(_) => {}
        }
    }

    fn render_phase(&mut self, change: &PhaseChange, scope: Option<&Scope>) {
        let label = change.phase.label();
        let elapsed = Duration::from_millis(change.elapsed_ms);
        let worker = scope.map(|scope| scope.worker.clone());
        let detail = change.detail.as_deref().unwrap_or("");
        match change.state {
            PhaseState::Started => {
                let now = self.now();
                self.phases.push(ActivePhase {
                    phase: change.phase,
                    worker,
                    detail: detail.to_owned(),
                    started: now,
                    printed: now,
                });
                let text = if detail.is_empty() {
                    label.to_owned()
                } else {
                    format!("{label}: {detail}")
                };
                self.line(scope, &text);
            }
            PhaseState::Progress => {
                let now = self.now();
                let redraw = self.terminal.redraw;
                let Some(active) = self.find(change.phase, worker.as_deref()) else {
                    return;
                };
                active.detail = detail.to_owned();
                if redraw {
                    self.redraw_footer();
                    return;
                }
                if clock::elapsed(active.printed, now) >= PLAIN_PROGRESS_EVERY {
                    active.printed = now;
                    let text = format!("still {label} ({}): {detail}", format_elapsed(elapsed));
                    self.line(scope, &text);
                }
            }
            PhaseState::Finished | PhaseState::Failed => {
                self.drop_phase(change.phase, worker.as_deref());
                let text = if change.state == PhaseState::Failed {
                    format!(
                        "{label}: failed after {}: {}",
                        format_elapsed(elapsed),
                        first_line(change.error.as_deref().unwrap_or(""))
                    )
                } else {
                    format!("{label}: done in {}", format_elapsed(elapsed))
                };
                self.line(scope, &text);
            }
        }
    }

    fn render_daemon(&mut self, event: &RunEvent, scope: Option<&Scope>) {
        match &event.kind {
            RunEventKind::RunStarted(started) => {
                let text = format!("daemon iteration {} started", started.iteration_id);
                self.line(scope, &text);
            }
            RunEventKind::TestStarted(started) => {
                self.current = started.execution.describe();
                // The footer names the test that runs, so a line for it only repeats the footer.
                if self.terminal.redraw {
                    self.redraw_footer();
                    return;
                }
                let text = format!("▶ {}", self.current);
                self.line(scope, &text);
            }
            RunEventKind::TestFinished(finished) => {
                self.current.clear();
                let mark = if finished.status == "SUCCESSFUL" {
                    self.passed += 1;
                    self.paint("✓", "32")
                } else {
                    self.failed += 1;
                    self.paint("✗", "31")
                };
                let suffix = match finished.error.as_deref() {
                    Some(error) if !error.is_empty() => format!(" — {}", first_line(error)),
                    _ => String::new(),
                };
                let text = format!("{mark} {}{suffix}", finished.execution.describe());
                self.line(scope, &text);
            }
            RunEventKind::TestSkipped(skipped) => {
                self.skipped += 1;
                let named = named(skipped.execution.class_name.as_deref(), &skipped.execution.display_name);
                self.line(scope, &format!("↷ {named} ({})", skipped.reason));
            }
            RunEventKind::ContainerFailed(failed) => {
                self.failed += 1;
                let named = named(failed.execution.class_name.as_deref(), &failed.execution.display_name);
                let text = format!(
                    "{} {named} (container)\n{}",
                    self.paint("✗", "31"),
                    failed.error.as_deref().unwrap_or("")
                );
                self.line(scope, &text);
            }
            RunEventKind::WatchdogState(watchdog) => {
                // Once a minute during a long test. The footer already shows the test and its time.
                let Some(active) = watchdog.active_execution.as_ref() else {
                    return;
                };
                if self.terminal.redraw || watchdog.phase != "active_execution" {
                    return;
                }
                let deadline = watchdog.phase_deadline.as_deref().unwrap_or("undefined");
                let text = format!("watchdog ▶ {} until {deadline}", active.describe());
                self.line(scope, &text);
            }
            RunEventKind::WatchdogExpired(expired) => {
                let mut text = format!("watchdog expired: {}", expired.reason);
                if let Some(detail) = expired.detail.as_deref().filter(|detail| !detail.is_empty()) {
                    text.push_str(&format!(" — {detail}"));
                }
                if let Some(active) = &expired.active_execution {
                    text.push_str(&format!(" while {}", active.describe()));
                }
                self.line(scope, &text);
            }
            RunEventKind::SlowStep(slow) => {
                let dumps: Vec<&str> = slow.dumps().map(String::as_str).collect();
                let text = format!(
                    "slow step: {} took {} of a {} budget; dumps: {}",
                    slow.path,
                    format_elapsed_ms(u64::try_from(slow.elapsed_ms).unwrap_or(0)),
                    format_elapsed_ms(u64::try_from(slow.budget_ms).unwrap_or(0)),
                    dumps.join(", ")
                );
                self.line(scope, &text);
            }
            RunEventKind::PlanStarted(_)
            | RunEventKind::ContainerSkipped(_)
            | RunEventKind::RunFailed(_)
            | RunEventKind::Summary(_)
            | RunEventKind::Output(_)
            | RunEventKind::IdeLaunched(_)
            | RunEventKind::IdeExited(_) => {}
        }
    }

    /// One scenario's trace as it arrives: its mark, its name, whether it has a video, and where it is.
    fn trace_line(&self, ready: &TraceReady) -> String {
        let mark = if ready.status == "passed" {
            self.paint("✓", "32")
        } else {
            self.paint("✗", "31")
        };
        let video = if ready.has_video { ", video" } else { "" };
        let place = if self.viewer_port != 0 {
            viewer_url(self.viewer_port, &ready.bundle_id, &ready.scenario)
        } else {
            ready.zip.clone()
        };
        format!(
            "trace {mark} {} · {} ({}{video}): {place}",
            ready.test_class, ready.scenario, ready.status
        )
    }

    /// Prints one line above the footer.
    fn line(&mut self, scope: Option<&Scope>, text: &str) {
        self.clear_footer();
        let line = format!("{}: {}{text}\n", self.program, Scope::prefix(scope));
        self.out.emit(line.as_bytes());
        self.draw_footer();
    }

    /// Draws the footer again, for a new elapsed time or a new detail.
    fn redraw_footer(&mut self) {
        if !self.terminal.redraw || self.footer_text() == self.footer {
            return;
        }
        self.clear_footer();
        self.draw_footer();
    }

    fn draw_footer(&mut self) {
        if !self.terminal.redraw {
            return;
        }
        self.footer = self.footer_text();
        if !self.footer.is_empty() {
            self.out.emit(self.footer.as_bytes());
        }
    }

    fn clear_footer(&mut self) {
        if self.footer.is_empty() {
            return;
        }
        // Carriage return, then erase the line: the footer never ends in a newline.
        self.out.emit(b"\r\x1b[2K");
        self.footer.clear();
    }

    /// `<phase> <elapsed> · <detail> · ✓ n ✗ m ↷ k · ▶ <test>`, cut to the terminal's width.
    fn footer_text(&self) -> String {
        let Some(active) = self.phases.last() else {
            return String::new();
        };
        let mut head = format!(
            "{} {}",
            active.phase.label(),
            format_elapsed(clock::elapsed(active.started, self.now()))
        );
        if let Some(worker) = &active.worker {
            head = format!("[{worker}] {head}");
        }
        let mut parts = vec![head];
        if !active.detail.is_empty() {
            parts.push(active.detail.clone());
        }
        if self.passed + self.failed + self.skipped > 0 {
            parts.push(format!("✓ {} ✗ {} ↷ {}", self.passed, self.failed, self.skipped));
        }
        if !self.current.is_empty() {
            parts.push(format!("▶ {}", self.current));
        }
        let text = parts.join(" · ");
        // One column short of the width: a footer that fills the last column wraps on some terminals, and a
        // wrapped footer cannot be erased by one carriage return.
        let width = usize::from(self.terminal.width);
        if width > 1 && text.chars().count() > width - 1 {
            let mut cut: String = text.chars().take(width - 2).collect();
            cut.push('…');
            return cut;
        }
        text
    }

    fn find(&mut self, phase: Phase, worker: Option<&str>) -> Option<&mut ActivePhase> {
        self.phases
            .iter_mut()
            .rev()
            .find(|active| active.phase == phase && active.worker.as_deref() == worker)
    }

    fn drop_phase(&mut self, phase: Phase, worker: Option<&str>) {
        if let Some(index) = self
            .phases
            .iter()
            .rposition(|active| active.phase == phase && active.worker.as_deref() == worker)
        {
            self.phases.remove(index);
        }
    }

    /// Colours a mark when the terminal allows it.
    fn paint(&self, mark: &str, color: &str) -> String {
        if self.terminal.color {
            format!("\x1b[{color}m{mark}\x1b[0m")
        } else {
            mark.to_owned()
        }
    }
}

fn named(class_name: Option<&str>, display_name: &str) -> String {
    class_name.unwrap_or(display_name).to_owned()
}

/// The checkout that the build read, as one line: its HEAD and whether it is clean. A dirty tree is said first,
/// because it is what a person must know before they believe a red lane.
pub fn checkout_line(checkout: &Checkout) -> String {
    if let Some(error) = &checkout.error {
        return format!("checkout: unknown: {}", first_line(error));
    }
    let head = short_head(checkout.head.as_deref().unwrap_or(""));
    match checkout.uncommitted {
        0 => format!("checkout {head}: clean"),
        1 => format!("checkout {head}: 1 uncommitted file is under test"),
        count => format!("checkout {head}: {count} uncommitted files are under test"),
    }
}

fn short_head(head: &str) -> &str {
    head.get(..12).unwrap_or(head)
}

// --- the verdict ------------------------------------------------------------------------------------------

/// How many failures the block names one by one, and how many traces; the rest are counted. The rerun commands
/// are bounded too: a lane whose shared fixture broke fails every class it has, and 24 command lines under a
/// verdict bury the verdict.
const MAX_VERDICT_FAILURES: usize = 5;
const MAX_TRACE_ROWS: usize = 10;
const MAX_RERUN_COMMANDS: usize = 4;

/// The verdict as the plain answer. The summary comes first, and then what explains it: the lanes, the skipped
/// and the unreported classes, and the failures. Then where the report is, the timing, the shard split, the
/// rerun commands, the lease, the tree, and last the traces that did not pass, each with its link in the trace
/// viewer at `port`.
pub fn verdict_text(verdict: &Verdict, program: &str, port: u16) -> String {
    let mut lines = vec![verdict.summary.clone()];
    for lane in &verdict.lanes {
        lines.push(format!("lane {}: {}", lane.lane, lane.summary));
        if let Some(report) = &lane.report {
            lines.push(format!("  report {report}"));
        }
    }
    for skip in &verdict.skipped {
        lines.push(format!("  {}: {}", skip.name, skip.reason));
    }
    // The whole list, unbounded: a lane that loses its IDE on an early suite drops twenty-odd classes, and the
    // reader's next move is to re-run exactly those.
    if !verdict.unreported.is_empty() {
        lines.push(format!("selected and never run ({}):", verdict.unreported.len()));
        lines.extend(verdict.unreported.iter().map(|class| format!("  {class}")));
    }
    for (index, failure) in verdict.failures.iter().enumerate() {
        if index == MAX_VERDICT_FAILURES {
            lines.push(format!("(+{} more failures in the report)", verdict.failures.len() - index));
            break;
        }
        let mut line = format!("✗ {}", failure_name(failure));
        if let Some(message) = failure.message.as_deref().filter(|message| !message.is_empty()) {
            line.push_str(&format!(" — {message}"));
        }
        lines.push(line);
        if let Some(trace) = &failure.trace {
            lines.push(format!("  trace {}", viewer_url(port, &trace.bundle_id, &trace.scenario)));
        }
    }
    if let Some(report) = &verdict.report {
        lines.push(format!("report {report}"));
    }
    if let Some(timing) = &verdict.timing {
        lines.push(timing.clone());
    }
    if let Some(shards) = &verdict.shards {
        lines.push(format!(
            "shards {} ({}), wall {} of {} machine time",
            shards.count,
            shards.labels.join(" "),
            format_elapsed_ms(shards.wall_ms),
            format_elapsed_ms(shards.machine_ms)
        ));
    }
    if !verdict.rerun.is_empty() {
        let shown = &verdict.rerun[..verdict.rerun.len().min(MAX_RERUN_COMMANDS)];
        let mut line = format!("reproduce: {}", shown.join(" | "));
        if verdict.rerun.len() > MAX_RERUN_COMMANDS {
            line.push_str(&format!(" (+{} more in the report)", verdict.rerun.len() - MAX_RERUN_COMMANDS));
        }
        lines.push(line);
    }
    if let Some(held) = &verdict.lease {
        lines.push(lease_line(held, program));
    }
    if let Some(checkout) = &verdict.checkout {
        lines.push(tree_line(checkout));
    }
    if let Some(traces) = &verdict.traces {
        lines.extend(traces_lines(traces, port));
    }
    lines.join("\n")
}

/// A failure as a person reads it: `a.OneTest › works`, or the class alone.
fn failure_name(failure: &Failure) -> String {
    match failure.name.as_deref().filter(|name| !name.is_empty()) {
        None => failure.class.clone(),
        Some(name) if failure.class.is_empty() => name.to_owned(),
        Some(name) => format!("{} › {name}", failure.class),
    }
}

/// The command that frees one held worker, backticked as a line quotes it: `` `vm --lease-file R lease release` ``.
/// Every hint names the lease the same way, so a person can copy any of them; `receipt` is `FILE` where a line
/// speaks for several receipts.
pub fn release_command(program: &str, receipt: &str) -> String {
    format!("`{program} --lease-file {receipt} lease release`")
}

/// What became of the leases a command took for itself, as one line. A worker that stayed held is named with the
/// receipt that frees it: the disposition says that it happened, and only the receipt says what to do about it.
/// The dashboard prints the same line.
pub fn lease_line(held: &VerdictLease, program: &str) -> String {
    let mut line = format!("lease {} {}", held.workers.join(" "), held.disposition);
    if !held.receipts.is_empty() {
        let commands: Vec<String> = held.receipts.iter().map(|receipt| release_command(program, receipt)).collect();
        line.push_str(&format!(" (release it with {})", commands.join(", ")));
    }
    line
}

/// The checkout that the build read. A red lane on a dirty tree can come from the edits of another session in
/// the same working copy, so the verdict says whether the tree was clean.
fn tree_line(checkout: &Checkout) -> String {
    match (&checkout.error, checkout.uncommitted) {
        (Some(error), _) => format!("tree: unknown: {error}"),
        (None, 0) => "tree: clean".to_owned(),
        (None, count) => format!("tree: dirty ({count} files)"),
    }
}

/// The scenario traces: a count, then each trace that did not pass with its link, and then why a pull failed, so
/// a run whose traces did not arrive does not read like a run that recorded none.
fn traces_lines(traces: &VerdictTraces, port: u16) -> Vec<String> {
    let mut lines = Vec::new();
    if traces.scenarios > 0 {
        lines.push(format!(
            "traces: {} scenario(s), {} not passed, {} with video, in {}",
            traces.scenarios,
            traces.not_passed.len(),
            traces.with_video,
            traces.directory.as_deref().unwrap_or("")
        ));
        for (index, reference) in traces.not_passed.iter().enumerate() {
            if index == MAX_TRACE_ROWS {
                lines.push(format!("  (+{} more)", traces.not_passed.len() - MAX_TRACE_ROWS));
                break;
            }
            lines.push(format!(
                "  ✗ {} · {}  {}",
                reference.test_class,
                reference.scenario,
                viewer_url(port, &reference.bundle_id, &reference.scenario)
            ));
        }
    }
    if let Some(error) = &traces.error {
        lines.push(format!("traces: not pulled: {error}"));
    }
    lines
}
