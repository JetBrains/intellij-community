//! `bench trace`: the spans of one run that start in a window, or that run in it, with the anchors of the run. It
//! reads only the files of the session, so it answers on any host.

use std::fmt::Write as _;
use std::path::Path;

use avl_base::Refusal;
use serde::Serialize;

use super::arm::Arm;
use super::digest::{NAME_WIDTH, clip, number};
use super::options::TraceArgs;
use super::record::{FRAME_BECAME_VISIBLE, WELCOME_SCREEN_PAINTED};
use super::session::{self, SelectedRun, TRACE_FILE};
use super::timeline::{Anchor, TimedSpan, Timeline, WindowMs};

/// The answer of `bench trace`.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SpanReport {
    pub(crate) session: String,
    /// The run directory name.
    pub(crate) run: String,
    /// The metric whose median picked the run. A run that `--run` names has none.
    pub(crate) median_by: Option<&'static str>,
    pub(crate) origin_us: i64,
    pub(crate) anchors: Vec<Anchor>,
    pub(crate) window: WindowMs,
    /// The report keeps every span that runs in the window, not only the spans that start in it.
    pub(crate) overlap: bool,
    pub(crate) min_ms: f64,
    /// The report keeps the coroutine helper twins.
    pub(crate) all: bool,
    /// The spans of the run, in the window or not.
    pub(crate) total_spans: usize,
    /// The trace ended inside the span array, and the reader closed it.
    pub(crate) truncated: bool,
    pub(crate) spans: Vec<SpanLine>,
}

/// One span of the report.
#[derive(Clone, Debug, PartialEq, Serialize)]
pub(crate) struct SpanLine {
    #[serde(flatten)]
    pub(crate) span: TimedSpan,
    /// The span is over [`super::timeline::WAITER_MS`].
    pub(crate) waits: bool,
    /// The quit cut the span. See [`TimedSpan::is_cut_by`].
    pub(crate) cut: bool,
}

impl SpanLine {
    /// The line of `span`, with its marks against the quit at `quit_ms`.
    pub(crate) fn of(span: &TimedSpan, quit_ms: Option<f64>) -> Self {
        Self {
            span: span.clone(),
            waits: span.is_waiter(),
            cut: span.is_cut_by(quit_ms),
        }
    }

    /// The marks of the text, `waits` and `cut by the quit`, each one after two spaces.
    pub(crate) fn marks(&self) -> String {
        let mut marks = String::new();
        if self.waits {
            marks.push_str("  waits");
        }
        if self.cut {
            marks.push_str("  cut by the quit");
        }
        marks
    }
}

/// Reads the run that `args` selects and keeps the spans of its window: the spans that start in it, or with
/// `--overlap` the spans that run in it.
pub(crate) fn report(session: &Path, args: &TraceArgs) -> Result<SpanReport, Refusal> {
    let selection = &args.selection;
    let (SelectedRun { id, median_by, .. }, timeline) = Timeline::select(session, selection.run.as_deref(), selection.arm.as_ref())?;
    let window = WindowMs::of(&args.window, default_window(&timeline, &id.arm), &id)?;
    let spans = timeline
        .spans
        .iter()
        .filter(|span| if args.overlap { window.overlaps(span) } else { window.starts(span) })
        .filter(|span| (args.all || !span.helper) && span.duration_ms >= args.min)
        .map(|span| SpanLine::of(span, timeline.quit_ms()))
        .collect();
    Ok(SpanReport {
        session: session.display().to_string(),
        run: id.dir_name(),
        median_by,
        origin_us: timeline.origin_us,
        anchors: timeline.anchors.clone(),
        window,
        overlap: args.overlap,
        min_ms: args.min,
        all: args.all,
        total_spans: timeline.spans.len(),
        truncated: timeline.truncated,
        spans,
    })
}

/// The window of a run without `--from` and `--to`: from `frameBecameVisible` to the welcome screen paint. A run
/// without the frame, such as a modal run, starts at the process start and ends at the main anchor of its arm, see
/// [`Arm::main_anchor`]. A missing end widens the window to the end of the run.
pub(crate) fn default_window(timeline: &Timeline, arm: &Arm) -> WindowMs {
    let (from_ms, to_ms) = match timeline.anchor(FRAME_BECAME_VISIBLE) {
        Some(frame) => (frame, timeline.anchor(WELCOME_SCREEN_PAINTED)),
        None => (0.0, timeline.anchor(arm.main_anchor())),
    };
    WindowMs {
        from_ms,
        to_ms: to_ms.unwrap_or_else(|| timeline.end_ms()),
    }
}

impl SpanReport {
    /// The text: the run, the anchors, the window, and one line per span.
    pub(crate) fn text(&self) -> String {
        let mut text = String::new();
        heading(&mut text, "trace", &self.session, &self.run, self.median_by, &self.anchors);
        let helpers = if self.all { " with the helper twins" } else { "" };
        let (kept, none) = if self.overlap {
            ("spans that run in it", "no span runs in the window")
        } else {
            ("spans that start in it", "no span starts in the window")
        };
        let _ = writeln!(
            text,
            "window {}..{} ms, {kept}, >= {} ms{helpers}, {} of {} spans",
            number(self.window.from_ms),
            number(self.window.to_ms),
            number(self.min_ms),
            self.spans.len(),
            self.total_spans
        );
        if self.truncated {
            let _ = writeln!(text, "note: {TRACE_FILE} is truncated; the reader closed it");
        }
        if self.spans.is_empty() {
            let _ = write!(text, "{none}");
            return text;
        }
        let _ = write!(
            text,
            "  {:>7} {:>7} {:>6}  {:<NAME_WIDTH$}  class / plugin",
            "start", "end", "dur", "span"
        );
        for line in &self.spans {
            let span = &line.span;
            let mut row = format!(
                "  {:>7} {:>7} {:>6}  {:<NAME_WIDTH$}",
                number(span.start_ms),
                number(span.end_ms),
                number(span.duration_ms),
                clip(&span.name, NAME_WIDTH)
            );
            for tag in [&span.class, &span.plugin].into_iter().flatten() {
                let _ = write!(row, "  {tag}");
            }
            row.push_str(&line.marks());
            let _ = write!(text, "\n{}", row.trim_end());
        }
        text
    }
}

/// The first two lines of a report over one run: the verb, the session, the run and why the run, then the anchors.
pub(crate) fn heading(text: &mut String, verb: &str, session: &str, run: &str, median_by: Option<&str>, anchors: &[Anchor]) {
    let picked = median_by.map_or_else(String::new, |metric| format!(" (the median run by {metric})"));
    let _ = writeln!(text, "vm bench {verb} {} {run}{picked}", session::display_name(session));
    if anchors.is_empty() {
        let _ = writeln!(text, "anchors: none in result.json and no quit in the trace");
    } else {
        let anchors: Vec<String> = anchors
            .iter()
            .map(|anchor| format!("{} {}", anchor.name, number(anchor.ms)))
            .collect();
        let _ = writeln!(text, "anchors (ms from the process start): {}", anchors.join("  "));
    }
}

#[cfg(test)]
mod tests;
