//! `bench activities`: the post-startup activities of one run, by class and by plugin. It reads only the files of the
//! session, so it answers on any host.

use std::collections::BTreeMap;
use std::fmt::Write as _;
use std::path::Path;

use avl_base::Refusal;
use serde::Serialize;

use super::digest::number;
use super::options::ActivitiesArgs;
use super::session::{SelectedRun, TRACE_FILE};
use super::spans::{self, SpanLine};
use super::timeline::{Anchor, TimedSpan, Timeline, WAITER_MS, WindowMs};
use super::trace::tenth;

/// The answer of `bench activities`.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ActivityReport {
    pub(crate) session: String,
    /// The run directory name.
    pub(crate) run: String,
    /// The metric whose median picked the run. A run that `--run` names has none.
    pub(crate) median_by: Option<&'static str>,
    pub(crate) anchors: Vec<Anchor>,
    pub(crate) window: WindowMs,
    /// The rows of each table at most.
    pub(crate) top: usize,
    /// The activities of the run, in the window or not.
    pub(crate) total_activities: usize,
    /// The activities that start in the window.
    pub(crate) in_window: usize,
    /// The activities of the window over [`WAITER_MS`].
    pub(crate) waiters: usize,
    /// The activities of the window that the quit cut. See [`TimedSpan::is_cut_by`].
    pub(crate) cut_by_quit: usize,
    /// The plugins of the activities of the window.
    pub(crate) total_plugins: usize,
    /// The trace ended inside the span array, and the reader closed it.
    pub(crate) truncated: bool,
    /// The longest activities of the window, by duration, at most `top`.
    pub(crate) activities: Vec<SpanLine>,
    /// The plugins of the activities of the window, by the sum of the durations, at most `top`.
    pub(crate) plugins: Vec<PluginLine>,
}

/// The activities of one plugin in the window.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct PluginLine {
    /// The `plugin` tag. An activity without the tag has none.
    pub(crate) plugin: Option<String>,
    pub(crate) count: usize,
    /// The sum of the durations, to a tenth of a millisecond.
    pub(crate) sum_ms: f64,
    /// The class of the longest activity of the plugin. See [`class_of`].
    pub(crate) longest_class: String,
    pub(crate) longest_ms: f64,
}

/// Reads the run that `args` selects and groups the activities of its window.
pub(crate) fn report(session: &Path, args: &ActivitiesArgs) -> Result<ActivityReport, Refusal> {
    let (SelectedRun { id, median_by, .. }, timeline) = Timeline::select(session, args.selection.run.as_deref())?;
    let window = WindowMs::of(&args.window, timeline.whole(), &id)?;
    let top = usize::try_from(args.top).unwrap_or(usize::MAX);
    let mut activities: Vec<&TimedSpan> = timeline
        .window(window.from_ms, window.to_ms)
        .filter(|span| span.is_activity())
        .collect();
    let plugins = by_plugin(&activities);
    let in_window = activities.len();
    let waiters = activities.iter().filter(|span| span.is_waiter()).count();
    let quit_ms = timeline.quit_ms();
    let cut_by_quit = activities.iter().filter(|span| span.is_cut_by(quit_ms)).count();
    activities.sort_by(|left, right| {
        right
            .duration_ms
            .total_cmp(&left.duration_ms)
            .then_with(|| left.start_ms.total_cmp(&right.start_ms))
    });
    Ok(ActivityReport {
        session: session.display().to_string(),
        run: id.dir_name(),
        median_by,
        anchors: timeline.anchors.clone(),
        window,
        top,
        total_activities: timeline.spans.iter().filter(|span| span.is_activity()).count(),
        in_window,
        waiters,
        cut_by_quit,
        total_plugins: plugins.len(),
        truncated: timeline.truncated,
        activities: activities.into_iter().take(top).map(|span| SpanLine::of(span, quit_ms)).collect(),
        plugins: plugins.into_iter().take(top).collect(),
    })
}

/// The name of an activity: its `class` tag, else its span name.
fn class_of(span: &TimedSpan) -> &str {
    span.class.as_deref().unwrap_or(&span.name)
}

/// The plugins of `activities`, by the sum of the durations, then by the plugin. `activities` are by start, so the
/// longest activity of a plugin is the first one of the longest duration.
fn by_plugin(activities: &[&TimedSpan]) -> Vec<PluginLine> {
    let mut plugins: BTreeMap<Option<&str>, PluginLine> = BTreeMap::new();
    for span in activities {
        let line = plugins.entry(span.plugin.as_deref()).or_insert_with(|| PluginLine {
            plugin: span.plugin.clone(),
            count: 0,
            sum_ms: 0.0,
            longest_class: class_of(span).to_owned(),
            longest_ms: span.duration_ms,
        });
        line.count += 1;
        line.sum_ms += span.duration_ms;
        if span.duration_ms > line.longest_ms {
            line.longest_class = class_of(span).to_owned();
            line.longest_ms = span.duration_ms;
        }
    }
    let mut plugins: Vec<PluginLine> = plugins
        .into_values()
        .map(|line| PluginLine {
            sum_ms: tenth(line.sum_ms),
            ..line
        })
        .collect();
    plugins.sort_by(|left, right| right.sum_ms.total_cmp(&left.sum_ms).then_with(|| left.plugin.cmp(&right.plugin)));
    plugins
}

impl ActivityReport {
    /// The text: the run, the anchors, the table by class, the table by plugin, and the counts.
    pub(crate) fn text(&self) -> String {
        let mut text = String::new();
        spans::heading(&mut text, "activities", &self.session, &self.run, self.median_by, &self.anchors);
        if self.truncated {
            let _ = writeln!(text, "note: {TRACE_FILE} is truncated; the reader closed it");
        }
        if self.activities.is_empty() {
            let _ = writeln!(text, "no activity starts in the window");
        } else {
            self.classes(&mut text);
            self.plugin_table(&mut text);
        }
        let _ = write!(
            text,
            "{} activities, {} start inside {}..{} ms, {} wait over {} s, {} cut by the quit",
            self.total_activities,
            self.in_window,
            number(self.window.from_ms),
            number(self.window.to_ms),
            self.waiters,
            number(WAITER_MS / 1000.0),
            self.cut_by_quit
        );
        match self.cut_by_quit {
            0 => {}
            1 => text.push_str("\n1 activity was still running at the quit; raise --hold to see its full duration."),
            count => {
                let _ = write!(
                    text,
                    "\n{count} activities were still running at the quit; raise --hold to see their full duration."
                );
            }
        }
        text
    }

    fn classes(&self, text: &mut String) {
        let class_width = column_width("class", self.activities.iter().map(|line| class_of(&line.span)));
        let plugin_width = column_width("plugin", self.activities.iter().map(|line| plugin_of(line.span.plugin.as_deref())));
        let _ = writeln!(text, "by class, {} of {} by duration:", self.activities.len(), self.in_window);
        let _ = writeln!(text, "  {:>7} {:>6}  {:<class_width$}  plugin", "start", "dur", "class");
        for line in &self.activities {
            let span = &line.span;
            let row = format!(
                "  {:>7} {:>6}  {:<class_width$}  {:<plugin_width$}{}",
                number(span.start_ms),
                number(span.duration_ms),
                class_of(span),
                plugin_of(span.plugin.as_deref()),
                line.marks()
            );
            let _ = writeln!(text, "{}", row.trim_end());
        }
    }

    fn plugin_table(&self, text: &mut String) {
        let plugin_width = column_width("plugin", self.plugins.iter().map(|line| plugin_of(line.plugin.as_deref())));
        let _ = writeln!(
            text,
            "by plugin, {} of {} by the sum of the durations:",
            self.plugins.len(),
            self.total_plugins
        );
        let _ = writeln!(
            text,
            "  {:>7} {:>6}  {:<plugin_width$}  {:>7}  the longest class",
            "count", "sum", "plugin", "longest"
        );
        for line in &self.plugins {
            let row = format!(
                "  {:>7} {:>6}  {:<plugin_width$}  {:>7}  {}",
                line.count,
                number(line.sum_ms),
                plugin_of(line.plugin.as_deref()),
                number(line.longest_ms),
                line.longest_class
            );
            let _ = writeln!(text, "{}", row.trim_end());
        }
    }
}

/// The text of a `plugin` tag: the tag, else `-`.
fn plugin_of(plugin: Option<&str>) -> &str {
    plugin.unwrap_or("-")
}

/// The width of a text column: its longest cell or its title.
fn column_width<'a>(title: &str, cells: impl Iterator<Item = &'a str>) -> usize {
    cells.map(|cell| cell.chars().count()).fold(title.len(), usize::max)
}

#[cfg(test)]
mod tests;
