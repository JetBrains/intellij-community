//! The dashboard as text: the live region while the run goes on, and the verdict card when it ended. Pure
//! functions of the state, the time and the width, so the tests read them without a terminal.

use std::time::{Duration, SystemTime};

use avl_base::Exit;
use avl_base::format::format_elapsed;
use avl_base::plain::lease_line;
use avl_wire::progress::Verdict;
use avl_wire::report::Status;
use console::{measure_text_width, truncate_str};

use crate::console::paint::Hue;
use crate::console::state::{
    PhaseRow, RowState, RunningTest, State, ms, phase_label, reason_suffix, run_link, short_class, since, slower, test_name,
};

/// The braille dots most terminal tools use, to animate the phase in flight.
const SPINNER_FRAMES: [&str; 10] = ["⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"];

/// How often the dashboard redraws, for the spinner and the elapsed times.
pub(crate) const FRAME_EVERY: Duration = Duration::from_millis(125);

/// The width below which the checklist drops its typical times.
const NARROW_WIDTH: usize = 80;

impl State {
    /// The dashboard under the scrollback while the command runs: the finished tests that wait for their traces,
    /// the phase checklist, the test progress, and the tests that run now. One string for each row.
    pub(crate) fn live_view(&self, now: SystemTime, width: usize) -> Vec<String> {
        let mut lines: Vec<String> = Vec::new();
        lines.extend(self.waiting_lines().cloned());
        lines.push(String::new());
        for row in &self.rows {
            lines.push(self.row_line(row, now, width));
        }
        if let Some(bar) = self.tests_line(now, width) {
            lines.push(String::new());
            lines.push(bar);
        }
        for (worker, test) in &self.running {
            lines.push(self.running_line(worker, test, now));
        }
        if let Some(started_at) = self.started_at {
            let mut total = format!("  {}{}", self.paint.muted("elapsed "), format_elapsed(since(started_at, now)));
            if let Some(run) = self.expected.as_ref().and_then(|expected| expected.run) {
                total.push_str(&self.paint.muted(&format!(" · usually {}", format_elapsed(ms(run.ms)))));
            }
            lines.push(total);
        }
        let limit = width.saturating_sub(1).max(20);
        lines.into_iter().map(|line| truncate_str(&line, limit, "…").into_owned()).collect()
    }

    fn row_line(&self, row: &PhaseRow, now: SystemTime, width: usize) -> String {
        let paint = self.paint;
        let mut label = phase_label(row.phase).to_owned();
        if !row.worker.is_empty() && self.workers.len() > 1 {
            label.push_str(&format!(" [{}]", row.worker));
        }
        let label = format!("{label:<14}");
        let (mark, label, elapsed, mut detail) = match row.state {
            RowState::Pending if !row.why.is_empty() => {
                return format!("  {}          {}", paint.muted(&format!("○ {label}")), paint.muted(&row.why));
            }
            RowState::Pending => {
                return format!("  {}", paint.muted(&format!("○ {}", label.trim_end())));
            }
            RowState::Skipped => {
                let mut text = format!("– {label} skipped");
                if !row.why.is_empty() {
                    text.push_str(&format!(" · {}", row.why));
                }
                return format!("  {}", paint.muted(&text));
            }
            RowState::Active => {
                let tick = since(SystemTime::UNIX_EPOCH, now).as_millis() / FRAME_EVERY.as_millis();
                let frame = SPINNER_FRAMES[(tick % SPINNER_FRAMES.len() as u128) as usize];
                (
                    paint.active(frame),
                    paint.active(&label),
                    format_elapsed(since(row.started, now)),
                    row.detail.as_str(),
                )
            }
            RowState::Done => (paint.pass("✓"), label, format_elapsed(row.elapsed), row.detail.as_str()),
            RowState::Failed => (
                paint.fail("✗"),
                paint.fail(&label),
                format_elapsed(row.elapsed),
                row.failure.as_str(),
            ),
        };
        let mut line = format!("  {mark} {label} {elapsed:>7}");
        if width >= NARROW_WIDTH {
            let usual = self
                .typical(row)
                .map(|typical| format!("usual {}", format_elapsed(typical)))
                .unwrap_or_default();
            line.push_str(&format!("  {}", paint.muted(&format!("{usual:<12}"))));
        }
        if !row.why.is_empty() {
            detail = &row.why;
        }
        if !detail.is_empty() {
            line.push_str(&format!(" {}", paint.muted(detail)));
        }
        line
    }

    /// The test progress: a bar over the discovered tests, the counts, and the time left when the classes have
    /// typical times.
    fn tests_line(&self, now: SystemTime, width: usize) -> Option<String> {
        let paint = self.paint;
        let done = self.passed + self.failed + self.skipped;
        if self.total == 0 && done == 0 {
            return None;
        }
        let counts = format!(
            "{}  {}  {}",
            paint.pass(&format!("✓ {}", self.passed)),
            paint.fail(&format!("✗ {}", self.failed)),
            paint.muted(&format!("↷ {}", self.skipped))
        );
        let mut line = format!("  {} ", paint.bold("tests"));
        if self.total > 0 {
            let bar_width = width.saturating_sub(60).clamp(10, 40);
            let stops = if self.failed > 0 {
                [Hue::Red, Hue::Yellow]
            } else {
                [Hue::Green, Hue::Blue]
            };
            line.push_str(&self.bar(f64::from(done) / f64::from(self.total), bar_width, stops));
            line.push_str(&format!(" {done}/{}", self.total));
        } else {
            line.push_str(&done.to_string());
        }
        line.push_str("   ");
        line.push_str(&counts);
        if let Some(left) = self.time_left(now) {
            line.push_str("   ");
            line.push_str(&paint.muted(&format!("~{} left", format_elapsed(left))));
        }
        Some(line)
    }

    /// A progress bar of `width` cells, filled to `fraction`: the first half of the bar in one colour and the
    /// second in the other, so a bar that is nearly done reads differently from one that started.
    fn bar(&self, fraction: f64, width: usize, stops: [Hue; 2]) -> String {
        #[expect(
            clippy::cast_possible_truncation,
            clippy::cast_sign_loss,
            reason = "the clamped fraction of the width, rounded, is in 0..=width"
        )]
        let filled = ((fraction.clamp(0.0, 1.0) * width as f64) + 0.5) as usize;
        let filled = filled.min(width);
        let mut bar = String::new();
        let half = width.div_ceil(2);
        let first = filled.min(half);
        bar.push_str(&self.paint.hue(stops[0], &"━".repeat(first)));
        bar.push_str(&self.paint.hue(stops[1], &"━".repeat(filled - first)));
        bar.push_str(&self.paint.muted(&"─".repeat(width - filled)));
        bar
    }

    /// How long the tests still take: the typical time of every discovered class not done yet, less the time the
    /// current class already ran. Known only when every such class has a typical time.
    fn time_left(&self, now: SystemTime) -> Option<Duration> {
        let expected = self.expected.as_ref()?;
        if self.classes.is_empty() {
            return None;
        }
        let mut left = Duration::ZERO;
        for class_name in &self.classes {
            if self.class_done.contains_key(class_name) {
                continue;
            }
            let typical = ms(expected.classes.get(class_name)?.ms);
            let current = self
                .per_worker
                .values()
                .find(|classes| classes.current == *class_name)
                .map_or(Duration::ZERO, |classes| since(classes.started, now));
            left += typical.saturating_sub(current);
        }
        Some(left)
    }

    fn running_line(&self, worker: &str, test: &RunningTest, now: SystemTime) -> String {
        let paint = self.paint;
        let prefix = if self.workers.len() > 1 {
            format!("[{worker}] ")
        } else {
            String::new()
        };
        let mut line = format!(
            "  {} {prefix}{}  {}",
            paint.active("▶"),
            paint.bold(&test.name),
            format_elapsed(since(test.started, now))
        );
        if let Some(classes) = self.per_worker.get(worker)
            && !classes.current.is_empty()
        {
            let class_elapsed = since(classes.started, now);
            let mut text = format!("class {}", format_elapsed(class_elapsed));
            if let Some(typical) = self.class_typical(&classes.current) {
                text.push_str(&format!(" of ~{}", format_elapsed(typical)));
                if slower(class_elapsed, typical) {
                    return format!("{line}  {}", paint.warn(&format!("{text} ▲ slower than usual")));
                }
            }
            line.push_str(&format!("  {}", paint.muted(&text)));
        }
        line
    }

    /// The verdict the dashboard leaves on the terminal when the run finished, one string for each row: a banner
    /// with the counts, the reason of a verdict that is not a list of failures, each lane, the phase times against
    /// their typical times, each failed phase, what was slower than usual and why, why the controller did what it
    /// did, what failed with its trace, how to rerun it, and what still needs a person: a held lease, a dirty
    /// checkout, traces that did not arrive. The facts of the tests come from the [`Verdict`] when the run
    /// published one, which is the report's account; the stream's counts stand in for a run that has none.
    ///
    /// Empty for a command that did not finish a run.
    pub(crate) fn card(&self, width: usize) -> Vec<String> {
        let Some(finished) = &self.finished else {
            return Vec::new();
        };
        let paint = self.paint;
        let verdict = self.verdict.as_deref();
        let (banner, border) = self.banner();
        let (passed, failed, skipped) = match verdict {
            Some(verdict) => (
                verdict.counts.passed(),
                verdict.counts.failed + verdict.counts.container_failures,
                verdict.counts.skipped + verdict.counts.containers_skipped,
            ),
            None => (self.passed, self.failed, self.skipped),
        };
        let took = match (self.started_at, self.finished_at) {
            (Some(started), Some(finished)) => since(started, finished),
            _ => Duration::ZERO,
        };
        let mut head = format!(
            "{}  {} · {} · {} · {}",
            paint.banner(border, banner),
            paint.pass(&format!("{passed} passed")),
            paint.fail(&format!("{failed} failed")),
            paint.muted(&format!("{skipped} skipped")),
            paint.bold(&format_elapsed(took)),
        );
        if let Some(run) = self.expected.as_ref().and_then(|expected| expected.run) {
            head.push_str(&paint.muted(&format!(" (usual {})", format_elapsed(ms(run.ms)))));
        }
        let mut lines = vec![head];
        match verdict {
            Some(verdict) if !verdict.passed() && verdict.code.as_deref() != Some("tests_failed") => {
                lines.push(paint.warn(&verdict.summary));
            }
            None if finished.exit_code != 0 => {
                if let Some(message) = finished.message.as_deref().filter(|m| !m.is_empty()) {
                    let code = finished.code.as_deref().unwrap_or("");
                    lines.push(paint.warn(&format!("{code}: {message}")));
                }
            }
            _ => {}
        }
        if let Some(verdict) = verdict {
            lines.extend(self.lane_lines(verdict));
            lines.extend(self.skip_lines(verdict));
            if !verdict.unreported.is_empty() {
                lines.push(paint.warn(&format!(
                    "{} selected classes never ran; the report names them",
                    verdict.unreported.len()
                )));
            }
        }
        let phases = self.phase_summary();
        if !phases.is_empty() {
            lines.push(phases);
        }
        for row in &self.rows {
            if row.state == RowState::Failed {
                lines.push(paint.fail(&format!("✗ {}: ", phase_label(row.phase))) + &row.failure);
            }
        }
        let slow = self.slow_summary();
        if !slow.is_empty() {
            lines.push(paint.warn("slower than usual ") + &slow);
        }
        let why = self.why_summary();
        if !why.is_empty() {
            lines.push(paint.muted("why ") + &why);
        }
        if let Some(verdict) = verdict {
            lines.extend(self.failure_lines(verdict));
            lines.extend(self.rerun_lines(verdict));
            if let Some(held) = verdict.lease.as_ref().filter(|lease| !lease.released()) {
                lines.push(paint.warn(&format!("⚠ {}", lease_line(held, &self.rerun_prefix))));
            }
            if let Some(error) = verdict.traces.as_ref().and_then(|traces| traces.error.as_deref()) {
                lines.push(paint.warn(&format!("⚠ traces not pulled: {error}")));
            }
        }
        let checkout = verdict.and_then(|verdict| verdict.checkout.as_ref()).or(self.checkout.as_ref());
        if let Some(checkout) = checkout.filter(|checkout| checkout.uncommitted > 0) {
            lines.push(paint.warn(&format!(
                "⚠ {} uncommitted files were under test; check `git status` before you believe a red test",
                checkout.uncommitted
            )));
        }
        if let Some(link) = run_link(self) {
            lines.push(paint.muted("run ") + &link);
        }
        let inner = width.saturating_sub(6).max(40);
        let lines: Vec<String> = lines.iter().map(|line| truncate_str(line, inner, "…").into_owned()).collect();
        self.boxed(&lines, border)
    }

    /// The card's banner and border: PASSED, FAILED for a red verdict, and STOPPED for a run that reached no
    /// verdict on the tests. Without a verdict, the exit code decides.
    fn banner(&self) -> (&'static str, Hue) {
        let exit_code = self.finished.as_ref().map_or(0, |finished| finished.exit_code);
        match self.verdict.as_ref().map(|verdict| verdict.status) {
            Some(Status::Passed) => (" PASSED ", Hue::Green),
            None if exit_code == 0 => (" PASSED ", Hue::Green),
            Some(Status::InfrastructureError) => (" STOPPED ", Hue::Yellow),
            None if exit_code != i32::from(Exit::TESTS_FAILED.code()) => (" STOPPED ", Hue::Yellow),
            _ => (" FAILED ", Hue::Red),
        }
    }

    /// The lines inside a rounded border in the banner's colour, one column of padding on each side.
    fn boxed(&self, lines: &[String], border: Hue) -> Vec<String> {
        let paint = self.paint;
        let inner = lines.iter().map(|line| measure_text_width(line)).max().unwrap_or(0);
        let rule = "─".repeat(inner + 2);
        let mut boxed = Vec::with_capacity(lines.len() + 2);
        boxed.push(paint.hue(border, &format!("╭{rule}╮")));
        let side = paint.hue(border, "│");
        for line in lines {
            let pad = " ".repeat(inner - measure_text_width(line));
            boxed.push(format!("{side} {line}{pad} {side}"));
        }
        boxed.push(paint.hue(border, &format!("╰{rule}╯")));
        boxed
    }

    /// One line for each lane of a run of two or more lanes.
    fn lane_lines(&self, verdict: &Verdict) -> Vec<String> {
        let paint = self.paint;
        verdict
            .lanes
            .iter()
            .map(|lane| {
                if lane.status == Status::Passed {
                    paint.pass(&format!("✓ lane {}", lane.lane)) + &paint.muted(&format!(" {} passed", lane.counts.passed()))
                } else {
                    paint.fail(&format!("✗ lane {}", lane.lane)) + &paint.muted(&format!(" {}", lane.summary))
                }
            })
            .collect()
    }

    /// Each class a condition ruled out, with its reason, for a run whose every class was skipped.
    fn skip_lines(&self, verdict: &Verdict) -> Vec<String> {
        if verdict.status != Status::AllSkipped {
            return Vec::new();
        }
        let paint = self.paint;
        let mut lines: Vec<String> = verdict
            .skipped
            .iter()
            .take(3)
            .map(|skip| paint.muted(&format!("↷ {}{}", short_class(&skip.name), reason_suffix(Some(&skip.reason)))))
            .collect();
        if verdict.skipped.len() > 3 {
            lines.push(paint.muted(&format!("… and {} more", verdict.skipped.len() - 3)));
        }
        lines
    }

    /// Each failed test with its first message line and its trace, at most five.
    fn failure_lines(&self, verdict: &Verdict) -> Vec<String> {
        let paint = self.paint;
        let mut lines: Vec<String> = verdict
            .failures
            .iter()
            .take(5)
            .map(|failed| {
                let name = test_name(&failed.class, failed.name.as_deref().unwrap_or(""), None);
                let mut line = paint.fail(&format!("✗ {name}")) + &paint.muted(&reason_suffix(failed.message.as_deref()));
                if let Some(trace) = &failed.trace
                    && self.viewer_port != 0
                {
                    let url = avl_trace_tools::viewer::viewer_url(self.viewer_port, &trace.bundle_id, &trace.scenario);
                    line.push_str("  ");
                    line.push_str(&paint.link("trace", &url));
                }
                line
            })
            .collect();
        if verdict.failures.len() > 5 {
            lines.push(paint.fail(&format!("… and {} more", verdict.failures.len() - 5)));
        }
        lines
    }

    /// The command that reruns each failed class, at most four.
    fn rerun_lines(&self, verdict: &Verdict) -> Vec<String> {
        let paint = self.paint;
        let mut lines: Vec<String> = verdict
            .rerun
            .iter()
            .take(4)
            .map(|command| {
                let command = format!("{} {command}", self.rerun_prefix);
                paint.muted("rerun ") + &paint.bold(command.trim())
            })
            .collect();
        if verdict.rerun.len() > 4 {
            lines.push(paint.muted(&format!("… and {} more in the report", verdict.rerun.len() - 4)));
        }
        lines
    }

    /// Every phase that ran, with its time: `host build 1m12s ▲ · lease 3s · tests 3m31s`.
    fn phase_summary(&self) -> String {
        let paint = self.paint;
        let parts: Vec<String> = self
            .rows
            .iter()
            .filter(|row| matches!(row.state, RowState::Done | RowState::Failed))
            .map(|row| {
                let part = format!("{} {}", phase_label(row.phase), format_elapsed(row.elapsed));
                if self.typical(row).is_some_and(|typical| slower(row.elapsed, typical)) {
                    paint.warn(&format!("{part} ▲"))
                } else if row.state == RowState::Failed {
                    paint.fail(&format!("{part} ✗"))
                } else {
                    part
                }
            })
            .collect();
        parts.join(&paint.muted(" · "))
    }

    fn why_summary(&self) -> String {
        let mut parts: Vec<String> = Vec::new();
        for decision in &self.decisions {
            let mut part = format!("{} {}", decision.subject, decision.action);
            if let Some(reason) = decision.reason.as_deref().filter(|reason| !reason.is_empty()) {
                part.push_str(&format!(" ({reason})"));
            }
            if !parts.contains(&part) {
                parts.push(part);
            }
        }
        parts.join(" · ")
    }

    /// What took much longer than its typical time: each such phase first, with what explains it, such as the
    /// actions a host build ran or the decision on its row, and then the classes, the slowest excess first.
    fn slow_summary(&self) -> String {
        let mut parts = Vec::new();
        for row in &self.rows {
            if !matches!(row.state, RowState::Done | RowState::Failed) {
                continue;
            }
            let Some(typical) = self.typical(row).filter(|typical| slower(row.elapsed, *typical)) else {
                continue;
            };
            let mut part = format!(
                "{} {} (usual {})",
                phase_label(row.phase),
                format_elapsed(row.elapsed),
                format_elapsed(typical)
            );
            if !row.summary.is_empty() {
                part.push_str(&format!(": {}", row.summary));
            } else if !row.why.is_empty() {
                part.push_str(&format!(": {}", row.why));
            }
            parts.push(part);
        }
        let mut slow: Vec<(&str, Duration, Duration)> = self
            .class_done
            .iter()
            .filter_map(|(class_name, elapsed)| {
                let typical = self.class_typical(class_name)?;
                slower(*elapsed, typical).then(|| (short_class(class_name), *elapsed, typical))
            })
            .collect();
        slow.sort_by_key(|(_, elapsed, typical)| std::cmp::Reverse(elapsed.saturating_sub(*typical)));
        for (name, elapsed, typical) in slow.into_iter().take(3) {
            parts.push(format!("{name} {} (usual {})", format_elapsed(elapsed), format_elapsed(typical)));
        }
        parts.join(" · ")
    }
}
