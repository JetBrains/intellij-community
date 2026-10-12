//! The timeline of one run: its spans in milliseconds from the process start, the anchors of `result.json` that tell
//! where the frame, the welcome screen and the highlighted editor show, and the start of the quit.

use std::path::Path;

use anyhow::Context;
use avl_base::{Refusal, RefusalExt};
use serde::Serialize;

use super::digest::number;
use super::files;
use super::options::Window;
use super::record::{
    EDITOR_HIGHLIGHTED, FRAME_BECAME_INTERACTIVE, FRAME_BECAME_VISIBLE, RunId, RunRecord, WELCOME_BECAME_VISIBLE, WELCOME_SCREEN_PAINTED,
};
use super::session::{self, RESULT_FILE, SelectedRun, TRACE_FILE};
use super::trace::{self, micros_to_ms, tenth};

/// The resolution of the timeline: [`micros_to_ms`] rounds to a tenth of a millisecond.
pub(crate) const RESOLUTION_MS: f64 = 0.1;

/// A span over 4 s waits for another task, and it does not compute.
pub(crate) const WAITER_MS: f64 = 4000.0;

/// The span of one post-startup activity of a project. Its `class` and `plugin` tags name the activity.
pub(crate) const RUN_ACTIVITY: &str = "run activity";

/// The prefix of the span of one init activity of a project, such as `run init activity PsiVfsInitProjectActivity`.
pub(crate) const RUN_INIT_ACTIVITY_PREFIX: &str = "run init activity ";

/// The metrics that are anchors, in the order of the start-up. Each one is in milliseconds from the process start. A
/// metric that the run does not have is left out, as in the modal arm, which has only the welcome event.
const ANCHORS: [&str; 5] = [
    FRAME_BECAME_VISIBLE,
    FRAME_BECAME_INTERACTIVE,
    WELCOME_BECAME_VISIBLE,
    WELCOME_SCREEN_PAINTED,
    EDITOR_HIGHLIGHTED,
];

/// The span of the quit of the IDE. Its start is the last anchor of a run.
pub(crate) const QUIT_SPAN: &str = "application.exit";

/// One anchor in milliseconds from the process start: a metric of the run, or the start of [`QUIT_SPAN`].
#[derive(Clone, Debug, PartialEq, Serialize)]
pub(crate) struct Anchor {
    pub(crate) name: &'static str,
    pub(crate) ms: f64,
}

/// One span of the timeline.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct TimedSpan {
    /// Milliseconds from the process start, to a tenth.
    pub(crate) start_ms: f64,
    pub(crate) duration_ms: f64,
    pub(crate) name: String,
    /// The `class` tag, which a `run activity` and a `run init activity` span have.
    pub(crate) class: Option<String>,
    /// The `plugin` tag.
    pub(crate) plugin: Option<String>,
    /// The span is a coroutine helper twin. See [`trace::Span::is_helper`].
    pub(crate) helper: bool,
}

impl TimedSpan {
    /// Tells whether the span is over [`WAITER_MS`].
    pub(crate) fn is_waiter(&self) -> bool {
        self.duration_ms > WAITER_MS
    }

    /// The end of the span in milliseconds from the process start.
    pub(crate) fn end_ms(&self) -> f64 {
        self.start_ms + self.duration_ms
    }

    /// Tells whether the span runs one activity: [`RUN_ACTIVITY`] or a span with [`RUN_INIT_ACTIVITY_PREFIX`]. A helper
    /// twin is no activity.
    pub(crate) fn is_activity(&self) -> bool {
        !self.helper && (self.name == RUN_ACTIVITY || self.name.starts_with(RUN_INIT_ACTIVITY_PREFIX))
    }

    /// Tells whether the quit at `quit_ms` cut the span: it starts before the quit and ends at the start of the quit
    /// or later, within [`RESOLUTION_MS`]. Such a span never finished, so its duration is not its work.
    pub(crate) fn is_cut_by(&self, quit_ms: Option<f64>) -> bool {
        quit_ms.is_some_and(|quit| self.start_ms < quit && self.end_ms() >= quit - RESOLUTION_MS)
    }
}

/// The window that a report applies, after the defaults, in milliseconds from the process start.
#[derive(Clone, Copy, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct WindowMs {
    pub(crate) from_ms: f64,
    pub(crate) to_ms: f64,
}

impl WindowMs {
    /// The window of `--from` and `--to` over the run `run`, with `default` for a bound that the options do not give.
    /// A window whose start is not before its end is a usage refusal.
    pub(crate) fn of(window: &Window, default: Self, run: &RunId) -> Result<Self, Refusal> {
        let resolved = Self {
            from_ms: window.from.unwrap_or(default.from_ms),
            to_ms: window.to.unwrap_or(default.to_ms),
        };
        if resolved.from_ms >= resolved.to_ms {
            return Err(Refusal::usage(format!(
                "the window {}..{} ms of {} is empty: its start is not before its end",
                number(resolved.from_ms),
                number(resolved.to_ms),
                run.dir_name()
            )));
        }
        Ok(resolved)
    }
}

/// The timeline of one run.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Timeline {
    /// The microseconds since the epoch of the process start. See [`trace::Trace::origin_us`].
    pub(crate) origin_us: i64,
    pub(crate) anchors: Vec<Anchor>,
    /// By start, then by name.
    pub(crate) spans: Vec<TimedSpan>,
    /// The trace ended inside the span array, and the reader closed it.
    pub(crate) truncated: bool,
}

impl Timeline {
    /// Reads the timeline of the run that `run` names, else of the median run. See [`session::select_run`]. A session
    /// without `summary.json` is the refusal `no_summary`, and a run without its files is a usage refusal.
    pub(crate) fn select(session: &Path, run: Option<&str>) -> Result<(SelectedRun, Self), Refusal> {
        let summary = session::read_summary(session)?;
        let selected = session::select_run(session, &summary, run)?;
        let timeline = Self::read(&selected.dir).map_err(|error| Refusal::usage(format!("{error:#}")))?;
        Ok((selected, timeline))
    }

    /// Reads `opentelemetry.json` and `result.json` of a run directory.
    pub(crate) fn read(run_dir: &Path) -> anyhow::Result<Self> {
        let trace_path = run_dir.join(TRACE_FILE);
        let trace = trace::parse(&files::read_text(&trace_path)?).with_context(|| format!("{} is not a trace", trace_path.display()))?;
        let result_path = run_dir.join(RESULT_FILE);
        let record: RunRecord = serde_json::from_str(&files::read_text(&result_path)?)
            .with_context(|| format!("{} is not a run result", result_path.display()))?;
        let origin_us = trace.origin_us().with_context(|| format!("{} has no span", trace_path.display()))?;
        let mut spans: Vec<(i64, TimedSpan)> = trace
            .spans
            .iter()
            .map(|span| {
                let timed = TimedSpan {
                    start_ms: span.offset_ms(origin_us),
                    duration_ms: micros_to_ms(span.duration),
                    name: span.operation_name.clone(),
                    class: span.tag("class").map(str::to_owned),
                    plugin: span.tag("plugin").map(str::to_owned),
                    helper: span.is_helper(),
                };
                (span.start_time, timed)
            })
            .collect();
        spans.sort_by(|left, right| left.0.cmp(&right.0).then_with(|| left.1.name.cmp(&right.1.name)));
        let quit = spans.iter().find(|(_, span)| span.name == QUIT_SPAN).map(|(_, span)| Anchor {
            name: QUIT_SPAN,
            ms: span.start_ms,
        });
        let anchors = ANCHORS
            .iter()
            .filter_map(|name| record.metrics.get(*name).map(|ms| Anchor { name, ms: *ms }))
            .chain(quit)
            .collect();
        Ok(Self {
            origin_us,
            anchors,
            spans: spans.into_iter().map(|(_, span)| span).collect(),
            truncated: trace.truncated,
        })
    }

    /// The milliseconds of the anchor `name`.
    pub(crate) fn anchor(&self, name: &str) -> Option<f64> {
        self.anchors.iter().find(|anchor| anchor.name == name).map(|anchor| anchor.ms)
    }

    /// The start of the quit: the anchor [`QUIT_SPAN`]. A run whose trace ends before the quit has none.
    pub(crate) fn quit_ms(&self) -> Option<f64> {
        self.anchor(QUIT_SPAN)
    }

    /// The start of the run: the process start, or the start of a span before it.
    pub(crate) fn start_ms(&self) -> f64 {
        self.spans.first().map_or(0.0, |span| span.start_ms.min(0.0))
    }

    /// The end of the run: the end of the span that ends last. It is at least [`RESOLUTION_MS`] after the last start,
    /// so the window from [`Timeline::start_ms`] to it holds every span, also one of zero length at the end. It is a
    /// whole number of [`RESOLUTION_MS`], like each start and duration.
    pub(crate) fn end_ms(&self) -> f64 {
        let end = self
            .spans
            .iter()
            .map(|span| span.end_ms().max(span.start_ms + RESOLUTION_MS))
            .fold(0.0, f64::max);
        tenth(end)
    }

    /// The window from [`Timeline::start_ms`] to [`Timeline::end_ms`], which holds every span.
    pub(crate) fn whole(&self) -> WindowMs {
        WindowMs {
            from_ms: self.start_ms(),
            to_ms: self.end_ms(),
        }
    }

    /// The spans that start in `[from_ms, to_ms)`.
    pub(crate) fn window(&self, from_ms: f64, to_ms: f64) -> impl Iterator<Item = &TimedSpan> {
        self.spans
            .iter()
            .filter(move |span| span.start_ms >= from_ms && span.start_ms < to_ms)
    }
}

#[cfg(test)]
mod tests;
