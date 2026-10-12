//! `bench compare`: the medians of several sessions per arm, with the delta of each session to the first one, and the
//! noise of the noise arm, which tells whether the sessions compare. It reads only `summary.json` of each session.
//!
//! The noise arm of a session is the first arm, in the order of [`Arm::ALL`], that A and the session have, each with
//! [`MIN_RUNS_FOR_DELTA`] valid runs with its main metric.
//!
//! A later session of the distribution of the first one is an A/A run: its move is the noise of this host.

use std::collections::BTreeMap;
use std::fmt::Write as _;
use std::path::PathBuf;

use avl_base::{Exit, Refusal};
use serde::{Serialize, Serializer};

use super::arm::Arm;
use super::digest::{metric_groups, number, present_rows, short, signed, table_line, write_group};
use super::record::{CLASS_REQUESTS, PLUGIN_CLASSES};
use super::session;
use super::summary::{ArmSummary, MIN_RUNS_FOR_DELTA, Stat, Summary, spread};
use super::trace::tenth;

/// The factor that turns a median absolute deviation into the standard deviation of a normal distribution.
pub(crate) const MAD_TO_SIGMA: f64 = 1.4826;

/// The sessions do not compare when the noise arm moved by more than this many times the noise of A.
pub(crate) const NOISE_LIMIT: f64 = 2.0;

/// A session whose own noise is above this share of the median of its noise arm is too noisy to compare.
pub(crate) const NOISY_SHARE: f64 = 0.2;

/// The answer of `bench compare`.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Comparison {
    /// In the order of the arguments: A, B, C...
    pub(crate) sessions: Vec<SessionColumn>,
    /// By [`Arm::key`], then by metric.
    #[serde(serialize_with = "arms_by_key")]
    pub(crate) arms: Vec<ArmTable>,
    /// One check per session after A: does the noise arm of the session agree with A within the noise?
    pub(crate) noise_arm: Vec<NoiseCheck>,
    /// Why a delta is left out.
    pub(crate) notes: Vec<String>,
}

/// One session of the comparison.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SessionColumn {
    /// A, B, C...
    pub(crate) label: String,
    /// The session directory.
    pub(crate) session: String,
    pub(crate) commit: String,
    pub(crate) dirty: bool,
    pub(crate) dist_digest: String,
    /// The valid measured runs, by [`Arm::key`].
    pub(crate) valid_runs: BTreeMap<String, usize>,
    /// The measured runs, valid or not, by [`Arm::key`].
    pub(crate) measured_runs: BTreeMap<String, usize>,
    /// The median of the 1-minute load average at the start of the measured runs. A session without a load has none.
    pub(crate) load: Option<f64>,
}

/// The rows of one arm, in the groups of the digest.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct ArmTable {
    pub(crate) arm: Arm,
    /// A title and its rows, with the metric of each row.
    pub(crate) groups: Vec<(&'static str, Vec<(String, MetricRow)>)>,
}

/// One metric of one arm.
#[derive(Clone, Debug, PartialEq, Serialize)]
pub(crate) struct MetricRow {
    /// One per session. A session without the arm or the metric has none.
    pub(crate) medians: Vec<Option<f64>>,
    /// One per session after A: its median minus the median of A. See [`delta`].
    pub(crate) deltas: Vec<Option<f64>>,
}

/// The noise arm of one session against the same arm of A.
#[derive(Clone, Debug, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct NoiseCheck {
    /// The session against A.
    pub(crate) label: String,
    /// The noise arm of the check. A pair without a noise arm has none.
    pub(crate) arm: Option<Arm>,
    /// Neither session is too noisy, see [`NOISY_SHARE`], and the noise arm moved by at most [`NOISE_LIMIT`] times
    /// the noise of A.
    pub(crate) comparable: bool,
    /// The median of the session minus the median of A, of the main metric of the noise arm.
    pub(crate) moved_ms: Option<f64>,
    /// The noise of A: [`MAD_TO_SIGMA`] times the median absolute deviation of the main metric of its noise arm.
    pub(crate) noise_ms: Option<f64>,
    /// The noise of the session, by the same rule.
    pub(crate) own_noise_ms: Option<f64>,
    /// The session has the distribution of A, so the check is an A/A run. See [`same_distribution`].
    pub(crate) same_dist: bool,
    /// The line of the text.
    pub(crate) note: String,
}

/// One session that [`report`] reads: its directory and its summary.
pub(crate) struct Compared {
    pub(crate) dir: PathBuf,
    pub(crate) summary: Summary,
}

impl Compared {
    /// Reads `summary.json` of a session. See [`session::read_summary`].
    pub(crate) fn read(dir: PathBuf) -> Result<Self, Refusal> {
        let summary = session::read_summary(&dir)?;
        Ok(Self { dir, summary })
    }
}

/// Compares the sessions, each one against the first one. With `expect_different_dist`, two sessions of one
/// distribution are the refusal `same_dist`.
pub(crate) fn report(sessions: &[Compared], expect_different_dist: bool) -> Result<Comparison, Refusal> {
    let columns: Vec<SessionColumn> = sessions
        .iter()
        .enumerate()
        .map(|(index, compared)| column(index, compared))
        .collect();
    if expect_different_dist {
        same_dist(&columns)?;
    }
    let mut arms: Vec<Arm> = sessions
        .iter()
        .flat_map(|compared| compared.summary.arms.values().map(|arm| arm.arm))
        .collect();
    arms.sort();
    arms.dedup();
    let mut notes = Vec::new();
    let tables = arms.into_iter().map(|arm| table(arm, sessions, &columns, &mut notes)).collect();
    let noise_arm = sessions
        .iter()
        .zip(&columns)
        .skip(1)
        .map(|(compared, column)| {
            let same_dist = same_distribution(&columns[0], column);
            noise_check(&sessions[0].summary, &compared.summary, &columns[0].label, &column.label, same_dist)
        })
        .collect();
    Ok(Comparison {
        sessions: columns,
        arms: tables,
        noise_arm,
        notes,
    })
}

/// The letter of the session at `index`: A, B, C...
fn label(index: usize) -> String {
    u8::try_from(index)
        .ok()
        .and_then(|index| b'A'.checked_add(index))
        .map_or_else(|| format!("#{index}"), |letter| char::from(letter).to_string())
}

fn column(index: usize, compared: &Compared) -> SessionColumn {
    let summary = &compared.summary;
    let loads: Vec<f64> = summary
        .arms
        .values()
        .flat_map(|arm| &arm.runs)
        .filter_map(|run| run.launch.load.as_ref().map(|load| load.one))
        .collect();
    SessionColumn {
        label: label(index),
        session: compared.dir.display().to_string(),
        commit: summary.git.commit.clone(),
        dirty: summary.git.dirty,
        dist_digest: summary.dist_digest.clone(),
        valid_runs: summary.arms.iter().map(|(key, arm)| (key.clone(), arm.valid_runs)).collect(),
        measured_runs: summary.arms.iter().map(|(key, arm)| (key.clone(), arm.runs.len())).collect(),
        load: Stat::of(&loads).map(|stat| tenth(stat.median)),
    }
}

/// Tells whether two sessions ran one distribution: their distribution digests are equal.
fn same_distribution(first: &SessionColumn, second: &SessionColumn) -> bool {
    first.dist_digest == second.dist_digest
}

/// Refuses two sessions with one distribution digest, and names each pair.
fn same_dist(columns: &[SessionColumn]) -> Result<(), Refusal> {
    let mut pairs = Vec::new();
    for (index, first) in columns.iter().enumerate() {
        for second in &columns[index + 1..] {
            if same_distribution(first, second) {
                pairs.push(format!(
                    "{} {} and {} {} share the distribution {}",
                    first.label,
                    session::display_name(&first.session),
                    second.label,
                    session::display_name(&second.session),
                    short(&first.dist_digest)
                ));
            }
        }
    }
    if pairs.is_empty() {
        return Ok(());
    }
    Err(Refusal::new(
        "same_dist",
        Exit::USAGE,
        format!(
            "{}; --expect-different-dist expects another distribution in each session",
            pairs.join("; ")
        ),
    ))
}

/// The rows of `arm`: the groups of the digest, with the rows that a session has.
fn table(arm: Arm, sessions: &[Compared], columns: &[SessionColumn], notes: &mut Vec<String>) -> ArmTable {
    let summaries: Vec<Option<&ArmSummary>> = sessions.iter().map(|compared| compared.summary.arms.get(arm.key())).collect();
    let base = summaries[0];
    for (summary, column) in summaries.iter().zip(columns).skip(1) {
        if let Some(note) = delta_note(arm, (base, &columns[0].label), (*summary, &column.label)) {
            notes.push(note);
        }
    }
    let present: Vec<&ArmSummary> = summaries.iter().flatten().copied().collect();
    let groups = metric_groups(&present)
        .into_iter()
        .map(|(title, rows)| {
            let rows = present_rows(rows, &present)
                .0
                .into_iter()
                .map(|metric| {
                    let medians: Vec<Option<f64>> = summaries
                        .iter()
                        .map(|summary| summary.and_then(|summary| summary.summary.get(&metric)).map(|stat| stat.median))
                        .collect();
                    let deltas = summaries
                        .iter()
                        .zip(&medians)
                        .skip(1)
                        .map(|(summary, median)| delta((base, medians[0]), (*summary, *median)))
                        .collect();
                    (metric, MetricRow { medians, deltas })
                })
                .collect();
            (title, rows)
        })
        .collect();
    ArmTable { arm, groups }
}

/// The median of a session minus the median of A, to a tenth. Both arms need [`MIN_RUNS_FOR_DELTA`] valid runs.
fn delta(base: (Option<&ArmSummary>, Option<f64>), other: (Option<&ArmSummary>, Option<f64>)) -> Option<f64> {
    let enough = |arm: Option<&ArmSummary>| arm.is_some_and(|arm| arm.valid_runs >= MIN_RUNS_FOR_DELTA);
    if !enough(base.0) || !enough(other.0) {
        return None;
    }
    Some(tenth(other.1? - base.1?))
}

/// Why an arm has no delta of a session to A, or `None` when it has one.
fn delta_note(arm: Arm, base: (Option<&ArmSummary>, &str), other: (Option<&ArmSummary>, &str)) -> Option<String> {
    let (base_label, other_label) = (base.1, other.1);
    let pair = format!("no delta {other_label}-{base_label} in the {} arm", arm.label());
    match (base.0, other.0) {
        (None, _) => Some(format!("{pair}: {base_label} has no {} arm", arm.label())),
        (_, None) => Some(format!("{pair}: {other_label} has no {} arm", arm.label())),
        (Some(base), Some(other)) if base.valid_runs < MIN_RUNS_FOR_DELTA || other.valid_runs < MIN_RUNS_FOR_DELTA => Some(format!(
            "{pair}: {base_label} has {} valid runs and {other_label} has {}; a delta needs {MIN_RUNS_FOR_DELTA} per session",
            base.valid_runs, other.valid_runs
        )),
        _ => None,
    }
}

/// The noise arm of `other` against the same arm of `base`, by the main metric of the arm. See [`noise_arm`]. A session
/// that is too noisy, see [`NOISY_SHARE`], does not compare. Else the move is set against the noise of `base` alone.
///
/// With `same_dist`, the check is an A/A run, and each note starts with that. A move within the noise is the noise of
/// this host. A larger move tells that the noise estimate of `base` is too low.
fn noise_check(base: &Summary, other: &Summary, base_label: &str, other_label: &str, same_dist: bool) -> NoiseCheck {
    let a_a = format!("{other_label} is an A/A run of {base_label}");
    let prefixed = |note: String| if same_dist { format!("{a_a}; {note}") } else { note };
    let Some((arm, base_values, other_values)) = noise_arm(base, other) else {
        return NoiseCheck {
            label: other_label.to_owned(),
            arm: None,
            comparable: false,
            moved_ms: None,
            noise_ms: None,
            own_noise_ms: None,
            same_dist,
            note: prefixed(format!(
                "the comparison of {base_label} and {other_label} is not meaningful: no arm has {MIN_RUNS_FOR_DELTA} runs with \
                 its main metric in both sessions, so the noise is unknown ({base_label}: {}; {other_label}: {})",
                arm_runs(base),
                arm_runs(other)
            )),
        };
    };
    let name = arm.label();
    let median = |values: &[f64]| Stat::of(values).map_or(0.0, |stat| stat.median);
    let noise_of = |values: &[f64]| MAD_TO_SIGMA * spread(values).unwrap_or(0.0);
    let (base_median, other_median) = (median(&base_values), median(&other_values));
    let (noise, own_noise) = (noise_of(&base_values), noise_of(&other_values));
    let moved = other_median - base_median;
    let too_noisy = |label: &str, noise: f64, median: f64| {
        (noise > NOISY_SHARE * median).then(|| {
            format!(
                "{label} is too noisy to compare: noise {} ms on a {name} median of {} ms",
                number(tenth(noise)),
                number(median)
            )
        })
    };
    let noisy = too_noisy(base_label, noise, base_median).or_else(|| too_noisy(other_label, own_noise, other_median));
    let comparable = noisy.is_none() && moved.abs() <= NOISE_LIMIT * noise;
    let note = if let Some(note) = noisy {
        prefixed(note)
    } else if same_dist && comparable {
        format!(
            "{a_a}: the {name} arm moved by {} ms, which is the noise of this host (noise of {base_label} {} ms)",
            signed(tenth(moved)),
            number(tenth(noise))
        )
    } else if same_dist {
        format!(
            "{a_a}: the {name} arm moved by {} ms, above the noise of {base_label} ({} ms); the noise estimate of {base_label} is too low",
            signed(tenth(moved)),
            number(tenth(noise))
        )
    } else if comparable {
        format!(
            "the {name} arm agrees within the noise: {base_label} {} ms, {other_label} {} ms (noise {} ms)",
            number(base_median),
            number(other_median),
            number(tenth(noise))
        )
    } else {
        format!(
            "the sessions do not compare: the {name} arm moved by {} ms between {base_label} and {other_label} (noise {} ms)",
            signed(tenth(moved)),
            number(tenth(noise))
        )
    };
    NoiseCheck {
        label: other_label.to_owned(),
        arm: Some(arm),
        comparable,
        moved_ms: Some(tenth(moved)),
        noise_ms: Some(tenth(noise)),
        own_noise_ms: Some(tenth(own_noise)),
        same_dist,
        note,
    }
}

/// The noise arm of two sessions, with the values of its main metric in `base` and in `other`: the first arm of
/// [`Arm::ALL`] that both sessions have, each with [`MIN_RUNS_FOR_DELTA`] valid runs with the main metric.
fn noise_arm(base: &Summary, other: &Summary) -> Option<(Arm, Vec<f64>, Vec<f64>)> {
    Arm::ALL.into_iter().find_map(|arm| {
        let values = |summary: &Summary| summary.arms.get(arm.key()).map(|summary| summary.values(arm.main_metric()));
        let (base_values, other_values) = (values(base)?, values(other)?);
        (base_values.len() >= MIN_RUNS_FOR_DELTA && other_values.len() >= MIN_RUNS_FOR_DELTA).then_some((arm, base_values, other_values))
    })
}

/// The arms of a session, in the order of [`Arm::ALL`], each with its valid runs with the main metric, such as
/// `modal 5, non-modal 1`. A session without an arm gives `no arm`.
fn arm_runs(summary: &Summary) -> String {
    let arms: Vec<String> = Arm::ALL
        .into_iter()
        .filter_map(|arm| {
            let runs = summary.arms.get(arm.key())?.values(arm.main_metric()).len();
            Some(format!("{} {runs}", arm.label()))
        })
        .collect();
    if arms.is_empty() { "no arm".to_owned() } else { arms.join(", ") }
}

/// Serializes the tables as a map by [`Arm::key`], each one a map by metric.
fn arms_by_key<S: Serializer>(tables: &[ArmTable], serializer: S) -> Result<S::Ok, S::Error> {
    serializer.collect_map(tables.iter().map(|table| {
        let rows: BTreeMap<&str, &MetricRow> = table
            .groups
            .iter()
            .flat_map(|(_, rows)| rows)
            .map(|(metric, row)| (metric.as_str(), row))
            .collect();
        (table.arm.key(), rows)
    }))
}

impl Comparison {
    /// The text: one line per session, the noise arm checks, one table per arm, and the notes.
    pub(crate) fn text(&self) -> String {
        let mut text = String::new();
        let base = self.sessions.first().map_or("A", |column| column.label.as_str());
        let _ = writeln!(text, "vm bench compare: {} sessions, each against {base}", self.sessions.len());
        let names: Vec<String> = self.sessions.iter().map(|column| session::display_name(&column.session)).collect();
        let name_width = names.iter().map(|name| name.chars().count()).max().unwrap_or(0);
        for (column, name) in self.sessions.iter().zip(&names) {
            let dirty = if column.dirty { "(dirty)" } else { "" };
            let runs: Vec<String> = Arm::ALL
                .into_iter()
                .filter_map(|arm| {
                    let valid = column.valid_runs.get(arm.key())?;
                    let measured = column.measured_runs.get(arm.key()).copied().unwrap_or(0);
                    Some(format!("{} {valid}/{measured}", arm.label()))
                })
                .collect();
            let load = column.load.map_or_else(|| "-".to_owned(), |load| format!("{load:.1}"));
            let line = format!(
                "  {} {name:<name_width$}  {:<12} {dirty:<7}  dist {}  {} valid  load {load}",
                column.label,
                short(&column.commit),
                short(&column.dist_digest),
                runs.join(", ")
            );
            let _ = writeln!(text, "{line}");
        }
        for check in &self.noise_arm {
            let _ = writeln!(text, "{}", check.note);
        }
        let deltas: Vec<String> = self
            .sessions
            .iter()
            .skip(1)
            .map(|column| format!("{}-{base}", column.label))
            .collect();
        let mut titles: Vec<String> = self.sessions.iter().map(|column| column.label.clone()).collect();
        titles.extend(deltas);
        for table in &self.arms {
            let _ = writeln!(
                text,
                "{}",
                table_line(&format!("{} arm (median ms, or a count)", table.arm.label()), &titles)
            );
            for (title, rows) in &table.groups {
                let metrics: Vec<String> = rows.iter().map(|(metric, _)| metric.clone()).collect();
                write_group(&mut text, title, &metrics, |metric| {
                    rows.iter()
                        .find(|(name, _)| name == metric)
                        .map(|(_, row)| cells(metric, row))
                        .unwrap_or_default()
                });
            }
        }
        for note in &self.notes {
            let _ = writeln!(text, "{note}");
        }
        text.truncate(text.trim_end().len());
        text
    }
}

/// The cells of one row: the median per session, then each delta. A count, see [`is_count`], prints as a whole number.
fn cells(metric: &str, row: &MetricRow) -> Vec<String> {
    let count = is_count(metric);
    // `+ 0.0` turns a rounded `-0.0` into `0.0`, so a delta of a count never prints `-0`.
    let value = |value: f64| if count { value.round() + 0.0 } else { value };
    let medians = row
        .medians
        .iter()
        .map(|median| median.map_or_else(|| "-".to_owned(), |median| number(value(median))));
    let deltas = row
        .deltas
        .iter()
        .map(|delta| delta.map_or_else(|| "-".to_owned(), |delta| signed(value(delta))));
    medians.chain(deltas).collect()
}

/// Tells whether a metric is a count, not a time: a key that starts with `classes.`, [`CLASS_REQUESTS`] or
/// [`PLUGIN_CLASSES`]. The other `classLoading.` keys are milliseconds.
fn is_count(metric: &str) -> bool {
    metric.starts_with("classes.") || metric == CLASS_REQUESTS || metric == PLUGIN_CLASSES
}

#[cfg(test)]
mod tests;
