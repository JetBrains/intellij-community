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
use avl_wire::report::{Integrity, RUN_REPORT_SCHEMA_VERSION, RunReport, Status};
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

/// What one report says each of its classes cost.
///
/// Fixture time is attributed to the class that pays it rather than to nobody: four of the lane's five heaviest
/// classes relaunch the IDE in `@BeforeAll` at 10-12 s each, and that time lives in the suite's own duration rather
/// than in any test case's. It is folded in only for a suite whose cases are all one class - the lane's shape -
/// because for a mixed suite there is no non-arbitrary way to split it.
pub(crate) fn class_durations_of(document: &RunReport) -> BTreeMap<String, f64> {
    let mut per_class: BTreeMap<String, f64> = BTreeMap::new();
    for suite in &document.suites {
        let mut in_suite: BTreeMap<&str, f64> = BTreeMap::new();
        for case in &suite.cases {
            *in_suite.entry(&case.class_name).or_default() += case.duration_ms;
        }
        if in_suite.len() == 1
            && let Some(only) = in_suite.values_mut().next()
        {
            *only = only.max(suite.duration_ms);
        }
        for (class_name, duration_ms) in in_suite {
            *per_class.entry(class_name.to_owned()).or_default() += duration_ms;
        }
    }
    per_class
}

/// Whether a report may be used as a duration measurement.
///
/// The same false-green guards the rest of the controller already owns, read here as "did this run measure anything
/// trustworthy": a truncated, stale or malformed XML arrives as an `infrastructure_error` with an integrity that is
/// not `complete`, and a `no_tests` or `all_skipped` run measured nothing at all. The schema check is there because
/// a report of an older layout would be read field by field as zeros, and a class believed to cost 0 ms is worse
/// than a class with no measurement - the latter is at least visible as a missing baseline.
fn is_duration_evidence(document: &RunReport) -> bool {
    document.report_schema_version == RUN_REPORT_SCHEMA_VERSION
        && matches!(document.status, Status::Passed | Status::Failed)
        && document.source.integrity == Integrity::Complete
}

/// One report, read permissively: a half-written or hand-edited report is not evidence, and a baseline scan is not
/// the place to fail a run over one - the classes it would have contributed fall into the remainder instead.
///
/// The three fields checked before the typed decode are the ones a report cannot be trusted without, and which the
/// typed decode would otherwise default.
fn read_report(path: &Path) -> Option<RunReport> {
    let content = fs::read(path).ok()?;
    let loose: Value = serde_json::from_slice(&content).ok()?;
    let present = |key: &str| loose.get(key).is_some_and(|value| !value.is_null());
    if !loose.get("suites").is_some_and(Value::is_array) || !present("completedAt") || !present("source") {
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
                let Some(document) = read_report(&entry.path()) else {
                    continue;
                };
                if !is_duration_evidence(&document) {
                    continue;
                }
                // A stamp nobody can read is "do not know", which sorts such a report last.
                let completed_ms = instant_millis(&document.completed_at).unwrap_or(0);
                admitted.push((completed_ms, class_durations_of(&document)));
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
