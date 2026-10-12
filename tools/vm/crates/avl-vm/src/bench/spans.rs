//! `bench trace`: the spans of one run that start in a window, with the anchors of the run. It reads only the files
//! of the session, so it answers on any host.

use std::fmt::Write as _;
use std::path::Path;

use avl_base::Refusal;
use serde::Serialize;

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

/// Reads the run that `args` selects and keeps the spans of its window.
pub(crate) fn report(session: &Path, args: &TraceArgs) -> Result<SpanReport, Refusal> {
    let (SelectedRun { id, median_by, .. }, timeline) = Timeline::select(session, args.selection.run.as_deref())?;
    let default = WindowMs {
        from_ms: timeline.anchor(FRAME_BECAME_VISIBLE).unwrap_or_else(|| timeline.start_ms()),
        to_ms: timeline.anchor(WELCOME_SCREEN_PAINTED).unwrap_or_else(|| timeline.end_ms()),
    };
    let window = WindowMs::of(&args.window, default, &id)?;
    let spans = timeline
        .window(window.from_ms, window.to_ms)
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
        min_ms: args.min,
        all: args.all,
        total_spans: timeline.spans.len(),
        truncated: timeline.truncated,
        spans,
    })
}

impl SpanReport {
    /// The text: the run, the anchors, the window, and one line per span.
    pub(crate) fn text(&self) -> String {
        let mut text = String::new();
        heading(&mut text, "trace", &self.session, &self.run, self.median_by, &self.anchors);
        let helpers = if self.all { " with the helper twins" } else { "" };
        let _ = writeln!(
            text,
            "window {}..{} ms, spans >= {} ms{helpers}, {} of {} spans",
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
            let _ = write!(text, "no span starts in the window");
            return text;
        }
        let _ = write!(text, "  {:>7} {:>6}  {:<NAME_WIDTH$}  class / plugin", "start", "dur", "span");
        for line in &self.spans {
            let span = &line.span;
            let mut row = format!(
                "  {:>7} {:>6}  {:<NAME_WIDTH$}",
                number(span.start_ms),
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
