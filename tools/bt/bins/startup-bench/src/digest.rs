//! The text digest of a summary, about 40 lines. It is advisory and can change; `summary.json` is the stable half.

use std::collections::BTreeMap;
use std::fmt::Write as _;

use crate::arm::Arm;
use crate::record::{
    CLASS_COUNT, CLASS_EDT_TIME, CLASS_TIME, CREATE_CONTENT_PREFIX, FRAME_BECAME_INTERACTIVE, FRAME_BECAME_VISIBLE, OPEN_EDITOR_PAINT,
    OPEN_FRAME, OPEN_HIGHLIGHTED, PLATFORM_SPANS, PLUGIN_CLASSES, TOTAL_DURATION, WELCOME_BECAME_VISIBLE, WELCOME_SPANS,
};
use crate::session::SUMMARY_FILE;
use crate::summary::{ArmSummary, Summary};

/// The width of the metric name column.
const NAME_WIDTH: usize = 60;
/// The width of one number column.
const NUMBER_WIDTH: usize = 7;
/// The frames of the profile block.
const PROFILE_FRAMES: usize = 10;
/// The longest frame name in the profile block.
const FRAME_WIDTH: usize = 100;

/// The metrics of the first block, in order.
const EVENT_METRICS: [&str; 8] = [
    WELCOME_BECAME_VISIBLE,
    FRAME_BECAME_VISIBLE,
    FRAME_BECAME_INTERACTIVE,
    TOTAL_DURATION,
    CLASS_COUNT,
    CLASS_TIME,
    CLASS_EDT_TIME,
    PLUGIN_CLASSES,
];

/// The metrics of the second project, in order.
const OPEN_METRICS: [&str; 3] = [OPEN_FRAME, OPEN_EDITOR_PAINT, OPEN_HIGHLIGHTED];

/// Renders the digest.
pub(crate) fn render(summary: &Summary) -> String {
    let arms: Vec<&ArmSummary> = summary.arms.values().collect();
    let mut text = String::new();
    let commit: String = summary.git.commit.chars().take(12).collect();
    let dirty = if summary.git.dirty { " (dirty)" } else { "" };
    let start = if summary.cold { "cold" } else { "warm" };
    let _ = writeln!(
        text,
        "startup-bench {}: {} at {commit}{dirty}, {start}, hold {} ms",
        summary.command, summary.target, summary.hold_ms
    );
    if let Some(project) = &summary.project {
        let _ = writeln!(text, "project: {project}");
    }
    header(&mut text, summary, &arms);

    let mut missing = Vec::new();
    block(
        &mut text,
        "events and report",
        &EVENT_METRICS.map(str::to_owned),
        summary,
        &arms,
        &mut missing,
    );
    block(
        &mut text,
        "platform spans",
        &PLATFORM_SPANS.map(str::to_owned),
        summary,
        &arms,
        &mut missing,
    );
    if arms.iter().any(|arm| arm.arm != Arm::Modal) {
        block(&mut text, "welcome spans", &welcome_rows(&arms), summary, &arms, &mut missing);
    }
    if arms.iter().any(|arm| arm.arm == Arm::OpenProject) {
        block(
            &mut text,
            "second project",
            &OPEN_METRICS.map(str::to_owned),
            summary,
            &arms,
            &mut missing,
        );
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
    for warning in &summary.warnings {
        let _ = writeln!(text, "warning: {warning}");
    }
    let _ = write!(text, "session: {}  summary: {}/{SUMMARY_FILE}", summary.session, summary.session);
    text
}

fn header(text: &mut String, summary: &Summary, arms: &[&ArmSummary]) {
    let mut first = format!("{:<NAME_WIDTH$}", "");
    let mut second = format!("{:<NAME_WIDTH$}", "metric (ms or count)");
    for arm in arms {
        let title = format!("{} {}/{} valid", arm.arm.label(), arm.valid_runs, arm.runs.len());
        let _ = write!(first, " {title:>width$}", width = NUMBER_WIDTH * 3 + 2);
        let _ = write!(
            second,
            " {:>NUMBER_WIDTH$} {:>NUMBER_WIDTH$} {:>NUMBER_WIDTH$}",
            "median", "min", "max"
        );
    }
    if !summary.delta.is_empty() {
        let _ = write!(first, " {:>NUMBER_WIDTH$}", "delta");
        let _ = write!(second, " {:>NUMBER_WIDTH$}", "B-A");
    }
    let _ = writeln!(text, "{}", first.trim_end());
    let _ = writeln!(text, "{}", second.trim_end());
}

/// The welcome span rows: the fixed names, with the per-feature rows after the feature ids.
fn welcome_rows(arms: &[&ArmSummary]) -> Vec<String> {
    let features: Vec<String> = arms
        .iter()
        .flat_map(|arm| arm.summary.keys())
        .filter(|metric| metric.starts_with(CREATE_CONTENT_PREFIX))
        .cloned()
        .collect::<std::collections::BTreeSet<String>>()
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

/// One block of rows. A row that no arm has goes to `missing`.
fn block(text: &mut String, title: &str, rows: &[String], summary: &Summary, arms: &[&ArmSummary], missing: &mut Vec<String>) {
    let present: Vec<&String> = rows
        .iter()
        .filter(|metric| arms.iter().any(|arm| arm.summary.contains_key(*metric)))
        .collect();
    missing.extend(
        rows.iter()
            .filter(|metric| !present.contains(metric) && arms.iter().any(|arm| arm.valid_runs > 0))
            .cloned(),
    );
    if present.is_empty() {
        return;
    }
    let _ = writeln!(text, "{title}:");
    for metric in present {
        let mut line = format!("  {:<width$}", clip(metric, NAME_WIDTH - 2), width = NAME_WIDTH - 2);
        for arm in arms {
            match arm.summary.get(metric) {
                Some(stat) => {
                    let _ = write!(
                        line,
                        " {:>NUMBER_WIDTH$} {:>NUMBER_WIDTH$} {:>NUMBER_WIDTH$}",
                        number(stat.median),
                        number(stat.min),
                        number(stat.max)
                    );
                }
                None => {
                    let _ = write!(line, " {:>NUMBER_WIDTH$} {:>NUMBER_WIDTH$} {:>NUMBER_WIDTH$}", "-", "", "");
                }
            }
        }
        if let Some(delta) = summary.delta.get(metric) {
            let _ = write!(line, " {:>NUMBER_WIDTH$}", signed(*delta));
        }
        let _ = writeln!(text, "{}", line.trim_end());
    }
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

/// The top EDT frames of an arm.
fn profile(text: &mut String, arm: &ArmSummary) {
    let Some(samples) = arm.edt_samples else {
        return;
    };
    let _ = writeln!(text, "{} EDT, top Java frames by samples ({samples} EDT samples):", arm.arm.label());
    for frame in arm.edt_frames.iter().take(PROFILE_FRAMES) {
        let share = if samples == 0 {
            0.0
        } else {
            frame.samples as f64 * 100.0 / samples as f64
        };
        let _ = writeln!(text, "  {share:5.1}% {:>6}  {}", frame.samples, clip(&frame.frame, FRAME_WIDTH));
    }
}

/// A value: one decimal below 10, else a whole number.
fn number(value: f64) -> String {
    if value.abs() < 10.0 && value.fract() != 0.0 {
        format!("{value:.1}")
    } else {
        format!("{value:.0}")
    }
}

fn signed(value: f64) -> String {
    if value.abs() < 10.0 && value.fract() != 0.0 {
        format!("{value:+.1}")
    } else {
        format!("{value:+.0}")
    }
}

/// Clips a text to `width` characters with an ellipsis.
fn clip(text: &str, width: usize) -> String {
    if text.chars().count() <= width {
        return text.to_owned();
    }
    let mut clipped: String = text.chars().take(width.saturating_sub(1)).collect();
    clipped.push('…');
    clipped
}

#[cfg(test)]
mod tests;
