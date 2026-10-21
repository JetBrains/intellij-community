//! What the run reports already on disk say a split can be balanced against.
//!
//! **Balance by measured duration, or refuse.** The lane's median class is 3.0 s and its slowest is 41.3 s, with
//! five classes carrying 72% of a 199 s lane (measured 2026-08-21 on `air-linux-1`, 23 of 23 green, 207 s wall). A
//! count-based split of that distribution is a plausible-looking wrong answer, so there is no such fallback: with no
//! history the plan refuses with `shard_baseline_unavailable`. Durations come from the run reports already on disk -
//! nothing new is recorded, and nothing has to be.

use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::path::Path;

use crate::lane::is_patternable_class_name;
use avl_base::Config;
use avl_report::report::instant_millis;
use avl_wire::report::{RUN_REPORT_SCHEMA_VERSION, RunReport, Status};
use serde_json::Value;

use crate::daemon::shard::ClassDuration;

#[cfg(test)]
#[cfg(unix)]
mod tests;

/// What the reports on disk say a split can be balanced against.
#[derive(Clone, Debug, Default, PartialEq)]
pub(crate) struct ShardBaseline {
    /// Sorted by class name, so a plan is a function of the reports and nothing else.
    pub durations: Vec<ClassDuration>,
    /// How many reports were admitted as evidence, and how many were on disk at all.
    pub admitted_reports: usize,
    pub scanned_reports: usize,
    /// Class names a report carried that could not be turned into a safe filter pattern. They run in the
    /// remainder.
    pub unpatternable: Vec<String>,
}

/// What one report says each of its classes cost: the sum of the durations of its cases.
pub(crate) fn class_durations_of(document: &RunReport) -> BTreeMap<String, f64> {
    let mut per_class: BTreeMap<String, f64> = BTreeMap::new();
    for case in &document.cases {
        *per_class.entry(case.class_name.clone()).or_default() += case.duration_ms;
    }
    per_class
}

/// Whether a report may be used as a duration measurement.
///
/// The same false-green guards the rest of the controller already owns, read here as "did this run measure anything
/// trustworthy": a cut-off or contradicted stream arrives as an `infrastructure_error`, and a `no_tests` or
/// `all_skipped` run measured nothing at all. The schema check is there because
/// a report of an older layout would be read field by field as zeros, and a class believed to cost 0 ms is worse
/// than a class with no measurement - the latter is at least visible as a missing baseline.
const fn is_duration_evidence(document: &RunReport) -> bool {
    document.report_schema_version == RUN_REPORT_SCHEMA_VERSION && matches!(document.status, Status::Passed | Status::Failed)
}

/// What one report on disk measured: the instant it completed, and the duration of each class. A stamp nobody can read
/// is "do not know", which sorts such a report last.
///
/// A report of schema 2, from before the run stream was the one source of a report, still measures: its suites are
/// read as they were, so a pool keeps its history across the change of schema.
fn read_measurement(path: &Path) -> Option<(i64, BTreeMap<String, f64>)> {
    let content = fs::read(path).ok()?;
    let loose: Value = serde_json::from_slice(&content).ok()?;
    if loose.get("reportSchemaVersion").and_then(Value::as_u64) == Some(SCHEMA_2) {
        return schema_2_measurement(&loose);
    }
    let document = read_report(loose)?;
    if !is_duration_evidence(&document) {
        return None;
    }
    Some((instant_millis(&document.completed_at).unwrap_or(0), class_durations_of(&document)))
}

/// The run report schema whose suites [`schema_2_measurement`] reads.
const SCHEMA_2: u64 = 2;

/// The measurement of a report of schema 2: a passed or failed run whose XML was complete, each class costing what the
/// suite that ran it cost. The fixture time of a suite of one class is that class's, because a `@BeforeAll` IDE
/// relaunch lives in the suite and in no case.
fn schema_2_measurement(loose: &Value) -> Option<(i64, BTreeMap<String, f64>)> {
    let text = |value: &Value, key: &str| value.get(key).and_then(Value::as_str).map(str::to_owned);
    let measured = matches!(text(loose, "status").as_deref(), Some("passed" | "failed"))
        && loose.pointer("/source/integrity").and_then(Value::as_str) == Some("complete");
    if !measured {
        return None;
    }
    let completed_ms = instant_millis(&text(loose, "completedAt")?).unwrap_or(0);
    let mut per_class: BTreeMap<String, f64> = BTreeMap::new();
    for suite in loose.get("suites")?.as_array()? {
        let mut in_suite: BTreeMap<String, f64> = BTreeMap::new();
        for case in suite.get("cases").and_then(Value::as_array).into_iter().flatten() {
            let class_name = text(case, "className")?;
            *in_suite.entry(class_name).or_default() += case.get("durationMs").and_then(Value::as_f64).unwrap_or(0.0);
        }
        if in_suite.len() == 1
            && let Some(only) = in_suite.values_mut().next()
        {
            *only = only.max(suite.get("durationMs").and_then(Value::as_f64).unwrap_or(0.0));
        }
        for (class_name, duration_ms) in in_suite {
            *per_class.entry(class_name).or_default() += duration_ms;
        }
    }
    Some((completed_ms, per_class))
}

/// One report, read permissively: a half-written or hand-edited report is not evidence, and a baseline scan is not
/// the place to fail a run over one - the classes it would have contributed fall into the remainder instead.
///
/// The three fields checked before the typed decode are the ones a report cannot be trusted without, and which the
/// typed decode would otherwise default.
fn read_report(loose: Value) -> Option<RunReport> {
    let present = |key: &str| loose.get(key).is_some_and(|value| !value.is_null());
    if !loose.get("cases").is_some_and(Value::is_array) || !present("completedAt") || !present("execution") {
        return None;
    }
    serde_json::from_value(loose).ok()
}

/// The entries of a directory in file-name order, or none when it cannot be read.
fn sorted_entries(directory: &Path) -> Vec<fs::DirEntry> {
    let Ok(entries) = fs::read_dir(directory) else {
        return Vec::new();
    };
    let mut entries: Vec<fs::DirEntry> = entries.filter_map(Result::ok).collect();
    entries.sort_by_key(fs::DirEntry::file_name);
    entries
}

/// Every class duration this pool has on disk, the most recent measurement per class.
///
/// Scoped to `settings.workers` rather than to every directory under `workers/`, because a duration is a property
/// of a *guest*: the Linux and macOS pools have distinct slot names, and the 41.3 s a macOS worker measured is not
/// evidence about a Linux one. One `Config` means one guest, so this scoping is also what makes the plan's inputs and
/// the run's workers the same population.
///
/// The scan order is fully determined - workers in pool order, then file-name order - and the sort by completion
/// is stable, so two reports completed in the same millisecond are broken the same way on every machine: which
/// report speaks for a class decides a plan.
pub(crate) fn read_shard_baseline(settings: &Config) -> ShardBaseline {
    let mut admitted: Vec<(i64, BTreeMap<String, f64>)> = Vec::new();
    let mut scanned_reports = 0;
    for worker in &settings.workers {
        let reports_root = settings.worker_dir(worker).join("reports");
        for run_directory in sorted_entries(&reports_root) {
            if !run_directory.file_type().is_ok_and(|kind| kind.is_dir()) {
                continue;
            }
            for entry in sorted_entries(&run_directory.path()) {
                // `*-evidence` subdirectories and the `.report-*.tmp` staging files of an in-flight persist both
                // live here; only a published report is a `*.json` file.
                let is_report =
                    entry.file_type().is_ok_and(|kind| kind.is_file()) && entry.file_name().to_string_lossy().ends_with(".json");
                if !is_report {
                    continue;
                }
                scanned_reports += 1;
                if let Some(measurement) = read_measurement(&entry.path()) {
                    admitted.push(measurement);
                }
            }
        }
    }
    // Most recent first, so the first report that mentions a class is the one that speaks for it.
    admitted.sort_by_key(|(completed_ms, _)| std::cmp::Reverse(*completed_ms));
    let mut latest: BTreeMap<String, f64> = BTreeMap::new();
    let mut unpatternable: BTreeSet<String> = BTreeSet::new();
    for (_, durations) in &admitted {
        for (class_name, duration_ms) in durations {
            if !is_patternable_class_name(class_name) {
                unpatternable.insert(class_name.clone());
                continue;
            }
            latest.entry(class_name.clone()).or_insert(*duration_ms);
        }
    }
    ShardBaseline {
        durations: latest
            .into_iter()
            .map(|(class_name, duration_ms)| ClassDuration { class_name, duration_ms })
            .collect(),
        admitted_reports: admitted.len(),
        scanned_reports,
        unpatternable: unpatternable.into_iter().collect(),
    }
}
