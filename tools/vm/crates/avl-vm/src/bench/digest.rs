//! The text digest of a summary, about 40 lines. It is advisory and can change; `summary.json` is the stable half.

use std::collections::{BTreeMap, BTreeSet};
use std::fmt::Write as _;
use std::time::SystemTime;

use jiff::Timestamp;
use jiff::tz::TimeZone;

use super::arm::Arm;
use super::profile::FrameSamples;
use super::record::{
    CLASS_ANCHORS, CLASS_EDT_TIME, CLASS_REQUESTS, CLASS_TIME, CLASSES_HIDDEN, CLASSES_JAR, CLASSES_JDK, CLASSES_NAMED, CLASSES_PLATFORM,
    CLASSES_PLUGINS, CREATE_CONTENT_PREFIX, EDITOR_HIGHLIGHTED, EMPTY_STATE_BUILT, EMPTY_STATE_SPANS, EMPTY_STATE_STEP_PREFIX,
    FRAME_BECAME_INTERACTIVE, FRAME_BECAME_VISIBLE, OPEN_EDITOR_PAINT, OPEN_FRAME, OPEN_HIGHLIGHTED, PLATFORM_SPANS, PLUGIN_CLASSES,
    TOTAL_DURATION, WELCOME_BECAME_VISIBLE, WELCOME_SPANS, named_at,
};
use super::session::SUMMARY_FILE;
use super::summary::{ArmSummary, Summary};

/// The width of the metric name column.
pub(crate) const NAME_WIDTH: usize = 60;
/// The width of one number column.
pub(crate) const NUMBER_WIDTH: usize = 7;
/// The frames of the EDT block of the profile.
const PROFILE_FRAMES: usize = 10;
/// The frames of the trigger block of the profile.
const TRIGGER_FRAMES: usize = 5;
/// The longest frame name in the profile block.
const FRAME_WIDTH: usize = 100;

/// The metrics of the first block, in order.
const EVENT_METRICS: [&str; 7] = [
    WELCOME_BECAME_VISIBLE,
    FRAME_BECAME_VISIBLE,
    FRAME_BECAME_INTERACTIVE,
    TOTAL_DURATION,
    CLASS_REQUESTS,
    CLASS_TIME,
    CLASS_EDT_TIME,
];

/// The metrics of the class block, in order. The rows of [`named_at`] follow them.
const CLASS_METRICS: [&str; 7] = [
    CLASSES_NAMED,
    CLASSES_HIDDEN,
    CLASSES_JDK,
    CLASSES_JAR,
    CLASSES_PLATFORM,
    CLASSES_PLUGINS,
    PLUGIN_CLASSES,
];

/// The metrics of the second project, in order.
const OPEN_METRICS: [&str; 3] = [OPEN_FRAME, OPEN_EDITOR_PAINT, OPEN_HIGHLIGHTED];

/// Renders the digest.
pub(crate) fn render(summary: &Summary) -> String {
    let arms: Vec<&ArmSummary> = summary.arms.values().collect();
    let mut text = String::new();
    let commit = short(&summary.git.commit);
    let dirty = if summary.git.dirty { " (dirty)" } else { "" };
    let start = if summary.cold { "cold" } else { "warm" };
    let max_load = summary.max_load.map(|load| format!(", max load {load}")).unwrap_or_default();
    let modules = match summary.additional_modules.len() {
        0 => String::new(),
        count => format!(", with {count} additional modules"),
    };
    let _ = writeln!(
        text,
        "vm bench {}: {} at {commit}{dirty}, {start}, hold {} ms{max_load}{modules}",
        summary.command, summary.target, summary.hold_ms
    );
    let digest = short(&summary.dist_digest);
    let _ = writeln!(text, "dist {digest} staged at {}", summary.generation);
    if let Some(project) = &summary.project {
        match &summary.project_file {
            Some(file) => {
                let _ = writeln!(text, "project: {project}, prime file {file}");
            }
            None => {
                let _ = writeln!(text, "project: {project}");
            }
        }
    }
    header(&mut text, summary, &arms);

    let mut missing = Vec::new();
    let measured = arms.iter().any(|arm| arm.valid_runs > 0);
    for (title, rows) in metric_groups(&arms) {
        let (present, absent) = present_rows(rows, &arms);
        if measured {
            missing.extend(absent);
        }
        write_group(&mut text, title, &present, |metric| cells(summary, &arms, metric));
    }
    if !missing.is_empty() {
        let _ = writeln!(text, "not measured: {}", missing.join(", "));
    }
    if let Some(note) = &summary.delta_note {
        let _ = writeln!(text, "{note}");
    }
    failures(&mut text, &arms);
    for arm in &arms {
        profile(&mut text, arm);
    }
    load(&mut text, &arms);
    for warning in &summary.warnings {
        let _ = writeln!(text, "warning: {warning}");
    }
    let _ = write!(text, "session: {}  summary: {}/{SUMMARY_FILE}", summary.session, summary.session);
    text
}

fn header(text: &mut String, summary: &Summary, arms: &[&ArmSummary]) {
    let mut first = format!("{:<NAME_WIDTH$}", "");
    let mut second = Vec::new();
    for arm in arms {
        let title = format!("{} {}/{} valid", arm.arm.label(), arm.valid_runs, arm.runs.len());
        let _ = write!(first, " {title:>width$}", width = NUMBER_WIDTH * 3 + 2);
        second.extend(["median", "min", "max"].map(str::to_owned));
    }
    if !summary.delta.is_empty() {
        let _ = write!(first, " {:>NUMBER_WIDTH$}", "delta");
        second.push("B-A".to_owned());
    }
    let _ = writeln!(text, "{}", first.trim_end());
    let _ = writeln!(text, "{}", table_line("metric (ms or count)", &second));
}

/// The metric groups of a table over `arms`, in order: a title and its rows. The editor needs the project arm, the
/// welcome spans need an arm with the non-modal welcome screen, and the second project needs the open-project arm.
/// The project arm finds its editor spans in the platform spans. The class group holds a row of [`named_at`] only for
/// an anchor that an arm has.
pub(crate) fn metric_groups(arms: &[&ArmSummary]) -> Vec<(&'static str, Vec<String>)> {
    let mut classes = CLASS_METRICS.map(str::to_owned).to_vec();
    classes.extend(
        CLASS_ANCHORS
            .iter()
            .map(|anchor| named_at(anchor))
            .filter(|metric| arms.iter().any(|arm| arm.summary.contains_key(metric))),
    );
    let mut groups = vec![
        ("events and report", EVENT_METRICS.map(str::to_owned).to_vec()),
        ("classes", classes),
    ];
    if arms.iter().any(|arm| arm.arm == Arm::Project) {
        groups.push(("editor", vec![EDITOR_HIGHLIGHTED.to_owned()]));
    }
    if arms.iter().any(|arm| arm.arm == Arm::EmptyEditor) {
        groups.push(("empty-state composer", empty_state_rows(arms)));
    }
    groups.push(("platform spans", PLATFORM_SPANS.map(str::to_owned).to_vec()));
    if arms.iter().any(|arm| matches!(arm.arm, Arm::NonModal | Arm::OpenProject)) {
        groups.push(("welcome spans", welcome_rows(arms)));
    }
    if arms.iter().any(|arm| arm.arm == Arm::OpenProject) {
        groups.push(("second project", OPEN_METRICS.map(str::to_owned).to_vec()));
    }
    groups
}

/// Splits `rows` into the rows that an arm of `arms` has and the rows that no arm has, in their order.
pub(crate) fn present_rows(rows: Vec<String>, arms: &[&ArmSummary]) -> (Vec<String>, Vec<String>) {
    rows.into_iter()
        .partition(|metric| arms.iter().any(|arm| arm.summary.contains_key(metric)))
}

/// One group of a metric table: the title line, then one [`table_line`] per row with the cells that `cells` gives.
/// A group without a row prints nothing.
pub(crate) fn write_group(text: &mut String, title: &str, rows: &[String], cells: impl Fn(&str) -> Vec<String>) {
    if rows.is_empty() {
        return;
    }
    let _ = writeln!(text, "{title}:");
    for metric in rows {
        let name = format!("  {}", clip(metric, NAME_WIDTH - 2));
        let _ = writeln!(text, "{}", table_line(&name, &cells(metric)));
    }
}

/// One line of a metric table: the name in [`NAME_WIDTH`] columns, then each cell right-aligned in [`NUMBER_WIDTH`]
/// columns after a space. The line has no trailing space.
pub(crate) fn table_line(name: &str, cells: &[String]) -> String {
    let mut line = format!("{name:<NAME_WIDTH$}");
    for cell in cells {
        let _ = write!(line, " {cell:>NUMBER_WIDTH$}");
    }
    line.trim_end().to_owned()
}

/// The rows of the Air empty-state composer: its end, the spans with a fixed name, then the prologue steps that an
/// arm has, in name order.
fn empty_state_rows(arms: &[&ArmSummary]) -> Vec<String> {
    let steps: BTreeSet<String> = arms
        .iter()
        .flat_map(|arm| arm.summary.keys())
        .filter(|metric| metric.starts_with(EMPTY_STATE_STEP_PREFIX))
        .cloned()
        .collect();
    std::iter::once(EMPTY_STATE_BUILT.to_owned())
        .chain(EMPTY_STATE_SPANS.map(str::to_owned))
        .chain(steps)
        .collect()
}

/// The welcome span rows: the fixed names, with the per-feature rows after the feature ids.
fn welcome_rows(arms: &[&ArmSummary]) -> Vec<String> {
    let features: Vec<String> = arms
        .iter()
        .flat_map(|arm| arm.summary.keys())
        .filter(|metric| metric.starts_with(CREATE_CONTENT_PREFIX))
        .cloned()
        .collect::<BTreeSet<String>>()
        .into_iter()
        .collect();
    let mut rows = Vec::new();
    for name in WELCOME_SPANS {
        rows.push(name.to_owned());
        if name == "welcome right tab body: feature ids" {
            rows.extend(features.iter().cloned());
        }
    }
    rows
}

/// The cells of one digest row: the median, the minimum and the maximum per arm, then the delta.
fn cells(summary: &Summary, arms: &[&ArmSummary], metric: &str) -> Vec<String> {
    let mut cells = Vec::new();
    for arm in arms {
        match arm.summary.get(metric) {
            Some(stat) => cells.extend([number(stat.median), number(stat.min), number(stat.max)]),
            None => cells.extend(["-".to_owned(), String::new(), String::new()]),
        }
    }
    if let Some(delta) = summary.delta.get(metric) {
        cells.push(signed(*delta));
    }
    cells
}

/// The failed runs and the notes of the valid runs.
fn failures(text: &mut String, arms: &[&ArmSummary]) {
    for arm in arms {
        let mut notes: BTreeMap<&str, usize> = BTreeMap::new();
        for run in &arm.runs {
            let terminated = if run.launch.terminated { " (terminated)" } else { "" };
            if !run.valid {
                let reason = run.reason.as_deref().unwrap_or("no reason");
                let _ = writeln!(text, "{} run {} failed{terminated}: {reason}", arm.arm.label(), run.index);
            } else if run.launch.terminated {
                let _ = writeln!(text, "{} run {} quit by a signal", arm.arm.label(), run.index);
            }
            for note in &run.notes {
                *notes.entry(note.as_str()).or_default() += 1;
            }
        }
        for (note, count) in notes {
            let _ = writeln!(text, "{} note: {note} ({count} of {} runs)", arm.arm.label(), arm.runs.len());
        }
    }
}

/// The top EDT frames of an arm, then its top class-loading triggers.
fn profile(text: &mut String, arm: &ArmSummary) {
    let Some(samples) = arm.edt_samples else {
        return;
    };
    let _ = writeln!(text, "{} EDT, top Java frames by samples ({samples} EDT samples):", arm.arm.label());
    frames(text, &arm.edt_frames, PROFILE_FRAMES, samples);
    let Some(define_samples) = arm.define_samples else {
        return;
    };
    let share = arm
        .define_share
        .map_or_else(|| "-".to_owned(), |share| format!("{:.1}", share * 100.0));
    let _ = writeln!(
        text,
        "{} class-loading triggers, top Java frames by samples ({define_samples} define samples, {share} % of all):",
        arm.arm.label()
    );
    frames(text, &arm.triggers, TRIGGER_FRAMES, define_samples);
}

/// One line per frame of a profile block, at most `limit`, with its share of `samples`.
fn frames(text: &mut String, frames: &[FrameSamples], limit: usize, samples: u64) {
    for frame in frames.iter().take(limit) {
        let share = if samples == 0 {
            0.0
        } else {
            frame.samples as f64 * 100.0 / samples as f64
        };
        let _ = writeln!(text, "  {share:5.1}% {:>6}  {}", frame.samples, clip(&frame.frame, FRAME_WIDTH));
    }
}

/// The 1-minute load average at the start of each measured run, per arm, and a warning for the runs that started
/// with more runnable threads than CPUs.
fn load(text: &mut String, arms: &[&ArmSummary]) {
    let mut saturated = 0;
    let mut cpus = 0;
    for arm in arms {
        let loads: Vec<String> = arm
            .runs
            .iter()
            .filter_map(|run| run.launch.load.as_ref())
            .map(|load| {
                cpus = load.cpus;
                saturated += usize::from(load.saturated());
                format!("{:.1}", load.one)
            })
            .collect();
        if !loads.is_empty() {
            let _ = writeln!(
                text,
                "{} host load at run start (1-minute average, {cpus} CPUs): {}",
                arm.arm.label(),
                loads.join(" ")
            );
        }
    }
    if saturated > 0 {
        let _ = writeln!(
            text,
            "warning: {saturated} runs started with a load above the CPU count; the host was busy, so the numbers are noisy"
        );
    }
}

/// A value: one decimal below 10, else a whole number.
pub(crate) fn number(value: f64) -> String {
    if value.abs() < 10.0 && value.fract() != 0.0 {
        format!("{value:.1}")
    } else {
        format!("{value:.0}")
    }
}

/// A value with its sign, at the precision of [`number`].
pub(crate) fn signed(value: f64) -> String {
    if value.abs() < 10.0 && value.fract() != 0.0 {
        format!("{value:+.1}")
    } else {
        format!("{value:+.0}")
    }
}

/// The first 12 characters of a commit or a digest, as the texts print them.
pub(crate) fn short(text: &str) -> String {
    text.chars().take(12).collect()
}

/// A time in `zone` to the minute, such as `2026-10-01 18:02`.
pub(crate) fn minute(time: SystemTime, zone: &TimeZone) -> String {
    Timestamp::try_from(time).map_or_else(
        |_| "an unknown time".to_owned(),
        |timestamp| timestamp.to_zoned(zone.clone()).strftime("%Y-%m-%d %H:%M").to_string(),
    )
}

/// Clips a text to `width` characters with an ellipsis.
pub(crate) fn clip(text: &str, width: usize) -> String {
    if text.chars().count() <= width {
        return text.to_owned();
    }
    let mut clipped: String = text.chars().take(width.saturating_sub(1)).collect();
    clipped.push('…');
    clipped
}

#[cfg(test)]
mod tests;
