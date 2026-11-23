//! The early stop of the passes of a performance scenario: a pass after the first [`Baseline::min_passes`] passes runs
//! only when the passes so far are out of the band of the nights before.
//!
//! The nightly TeamCity build writes the baseline file and names it in [`BASELINE_VARIABLE`]. The file holds, for the
//! project of each series, the values of its metrics in the latest nights, and the band settings of each metric. The
//! band is the one of the step analysis of the IJ Perf degradation detector: around the median of the nights, the
//! largest of a relative change, an absolute change and a number of robust standard deviations. When the median of the
//! passes so far is in the band for every metric of the baseline, the next passes of the scenario are skipped, so a
//! quiet night costs [`Baseline::min_passes`] passes. When one metric is over the band, every pass runs, so the night
//! that changed gets the median of all its passes. Without the variable, or without a baseline of a series, every pass
//! runs. A file that cannot be read is a note of the run, never a verdict.

use std::cell::RefCell;
use std::collections::BTreeMap;
use std::fs;
use std::path::Path;

use avl_junit::cases::simple_class_name;
use avl_junit::performance::pass_number;
use avl_wire::program::lane::PerformanceSeries;
use serde::Deserialize;

#[cfg(test)]
mod tests;

/// The variable that names the baseline file.
pub(crate) const BASELINE_VARIABLE: &str = "AIR_PERF_BASELINE";

/// The number of the nights that a band needs, so that its median and its spread mean something.
const MINIMUM_NIGHTS: usize = 5;

/// The factor from the median absolute deviation to the standard deviation of a normal distribution.
const MAD_TO_SIGMA: f64 = 1.4826;

/// The baseline file.
#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Baseline {
    /// The number of the passes that always run.
    pub min_passes: u32,
    /// The metrics of the nights before, by the project of a series and the name of a metric.
    pub series: BTreeMap<String, BTreeMap<String, MetricBaseline>>,
}

/// The nights of one metric and its band settings, or the fixed limit of a metric that IJ Perf compares to a limit.
#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct MetricBaseline {
    /// The fixed limit. With it, the band ends at the limit, and the nights do not count.
    #[serde(default)]
    pub limit: Option<f64>,
    /// The value of each night before, the median of its passes.
    #[serde(default)]
    pub values: Vec<f64>,
    /// The smallest change that counts, in percent of the median of the nights.
    #[serde(default)]
    pub relative: f64,
    /// The smallest change that counts, in the unit of the metric.
    #[serde(default)]
    pub absolute: f64,
    /// The number of robust standard deviations of the nights that a change must pass.
    #[serde(default)]
    pub noise: f64,
}

impl MetricBaseline {
    /// The upper end of the band: the limit, or `None` with fewer than [`MINIMUM_NIGHTS`] nights.
    pub(crate) fn upper(&self) -> Option<f64> {
        if self.limit.is_some() {
            return self.limit;
        }
        if self.values.len() < MINIMUM_NIGHTS {
            return None;
        }
        let center = median(self.values.clone())?;
        let spread = median(self.values.iter().map(|value| (value - center).abs()).collect())? * MAD_TO_SIGMA;
        let margin = (self.relative / 100.0 * center.abs()).max(self.absolute).max(self.noise * spread);
        Some(center + margin)
    }
}

/// The early stop of one run: the baseline, and the metrics of the passes that passed, by the artifact path of their
/// series.
pub(crate) struct EarlyStop {
    baseline: Baseline,
    passes: RefCell<BTreeMap<String, Vec<BTreeMap<String, f64>>>>,
}

impl EarlyStop {
    pub(crate) const fn new(baseline: Baseline) -> Self {
        Self {
            baseline,
            passes: RefCell::new(BTreeMap::new()),
        }
    }

    /// The early stop of the file that `variable` names, or `None` without it. A file that cannot be read gives a line
    /// of `notes`.
    pub(crate) fn from_variable(variable: Option<String>, notes: &mut Vec<String>) -> Option<Self> {
        let path = variable.filter(|path| !path.is_empty())?;
        match read(Path::new(&path)) {
            Ok(baseline) => Some(Self::new(baseline)),
            Err(reason) => {
                notes.push(format!("every pass runs, because the baseline {path} cannot be read: {reason}"));
                None
            }
        }
    }

    /// Keeps the metrics of a pass that passed: the case `name` of the test class `class`.
    pub(crate) fn record(&self, series: &[PerformanceSeries], class: &str, name: &str, metrics: &BTreeMap<String, serde_json::Number>) {
        let Some(series) = series_of(series, class, name) else {
            return;
        };
        let values = metrics
            .iter()
            .filter_map(|(name, value)| Some((name.clone(), value.as_f64()?)))
            .collect();
        self.passes
            .borrow_mut()
            .entry(series.artifact_path.clone())
            .or_default()
            .push(values);
    }

    /// Why the case `name` of the test class `class` does not run, or `None` when it runs.
    pub(crate) fn skip_reason(&self, series: &[PerformanceSeries], class: &str, name: &str) -> Option<String> {
        let pass = pass_number(name);
        if pass <= self.baseline.min_passes {
            return None;
        }
        let series = series_of(series, class, name)?;
        let metrics = self.baseline.series.get(&series.project).filter(|metrics| !metrics.is_empty())?;
        let passes = self.passes.borrow();
        let done = passes.get(&series.artifact_path)?;
        if done.len() < self.baseline.min_passes as usize {
            return None;
        }
        let mut checked = Vec::new();
        for (metric, baseline) in metrics {
            let upper = baseline.upper()?;
            let value = median(done.iter().filter_map(|pass| pass.get(metric).copied()).collect())?;
            if value > upper {
                return None;
            }
            checked.push(format!("{metric} {value:.0} <= {upper:.0}"));
        }
        Some(format!(
            "the {} passes so far of {} are in the band of the nights before, so pass {pass} is not needed: {}",
            done.len(),
            series.project,
            checked.join(", ")
        ))
    }
}

fn read(path: &Path) -> Result<Baseline, String> {
    let text = fs::read_to_string(path).map_err(|error| error.to_string())?;
    serde_json::from_str(&text).map_err(|error| error.to_string())
}

/// The series of the case `name` of the test class `class`. A class matches by its simple name.
fn series_of<'s>(series: &'s [PerformanceSeries], class: &str, name: &str) -> Option<&'s PerformanceSeries> {
    let class = simple_class_name(class);
    series
        .iter()
        .find(|series| series.scenario == name && simple_class_name(&series.test_class_name) == class)
}

fn median(mut values: Vec<f64>) -> Option<f64> {
    values.sort_by(f64::total_cmp);
    let middle = values.len() / 2;
    if values.len().is_multiple_of(2) {
        Some(f64::midpoint(*values.get(middle.checked_sub(1)?)?, *values.get(middle)?))
    } else {
        values.get(middle).copied()
    }
}
