//! The summary of a session: the runs per arm, the statistics of each metric, and the A/B delta. `summary.json` and
//! the `--json` envelope carry it.

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};

use crate::arm::Arm;
use crate::profile::{EdtProfile, FrameSamples};
use crate::record::{RunKind, RunRecord};
use crate::session::{GitInfo, SessionInfo};

/// The valid runs that each arm needs for a delta.
pub(crate) const MIN_RUNS_FOR_DELTA: usize = 3;

/// The EDT frames of the profile summary.
const PROFILE_TOP: usize = 10;

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Summary {
    /// `welcome` or `open-project`.
    pub(crate) command: String,
    pub(crate) session: String,
    pub(crate) target: String,
    pub(crate) git: GitInfo,
    pub(crate) cold: bool,
    pub(crate) hold_ms: u64,
    pub(crate) profile: bool,
    pub(crate) project: Option<String>,
    /// By [`Arm::key`].
    pub(crate) arms: BTreeMap<String, ArmSummary>,
    /// The non-modal median minus the modal median, per metric that both arms have.
    pub(crate) delta: BTreeMap<String, f64>,
    /// Why the summary has no delta.
    pub(crate) delta_note: Option<String>,
    pub(crate) warnings: Vec<String>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ArmSummary {
    pub(crate) arm: Arm,
    /// The measured runs, valid or not.
    pub(crate) runs: Vec<RunRecord>,
    pub(crate) valid_runs: usize,
    /// The statistics of the valid runs, per metric.
    pub(crate) summary: BTreeMap<String, Stat>,
    /// The EDT samples of the valid runs with a profile.
    pub(crate) edt_samples: Option<u64>,
    /// The frames with the most EDT self samples, over the valid runs.
    pub(crate) edt_frames: Vec<FrameSamples>,
}

#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
pub(crate) struct Stat {
    pub(crate) median: f64,
    pub(crate) min: f64,
    pub(crate) max: f64,
    pub(crate) count: usize,
}

impl Stat {
    /// The statistics of a non-empty list.
    pub(crate) fn of(values: &[f64]) -> Option<Self> {
        let mut sorted = values.to_vec();
        sorted.sort_by(f64::total_cmp);
        let count = sorted.len();
        let (&min, &max) = (sorted.first()?, sorted.last()?);
        let middle = count / 2;
        let median = if count % 2 == 1 {
            sorted[middle]
        } else {
            f64::midpoint(sorted[middle - 1], sorted[middle])
        };
        Some(Self { median, min, max, count })
    }
}

impl Summary {
    /// Builds the summary from the session inputs and the runs. Prime runs are left out.
    pub(crate) fn build(info: &SessionInfo, session: &str, records: &[RunRecord]) -> Self {
        let mut arms = BTreeMap::new();
        for arm in &info.arms {
            let runs: Vec<RunRecord> = records
                .iter()
                .filter(|record| record.arm == *arm && record.kind == RunKind::Measured)
                .cloned()
                .collect();
            arms.insert(arm.key().to_owned(), arm_summary(*arm, runs));
        }
        let (delta, delta_note) = delta(&arms);
        Self {
            command: info.command.clone(),
            session: session.to_owned(),
            target: info.target.clone(),
            git: info.git.clone(),
            cold: info.cold,
            hold_ms: info.hold_ms,
            profile: info.profile,
            project: info.project.clone(),
            arms,
            delta,
            delta_note,
            warnings: info.warnings.clone(),
        }
    }

    /// The arms that have no valid run.
    pub(crate) fn arms_without_valid_run(&self) -> Vec<Arm> {
        self.arms.values().filter(|arm| arm.valid_runs == 0).map(|arm| arm.arm).collect()
    }
}

fn arm_summary(arm: Arm, runs: Vec<RunRecord>) -> ArmSummary {
    let valid: Vec<&RunRecord> = runs.iter().filter(|run| run.valid).collect();
    let mut values: BTreeMap<String, Vec<f64>> = BTreeMap::new();
    for run in &valid {
        for (metric, value) in &run.metrics {
            values.entry(metric.clone()).or_default().push(*value);
        }
    }
    let summary = values
        .into_iter()
        .filter_map(|(metric, values)| Stat::of(&values).map(|stat| (metric, stat)))
        .collect();
    let mut profile: Option<EdtProfile> = None;
    for run_profile in valid.iter().filter_map(|run| run.profile.as_ref()) {
        profile.get_or_insert_with(EdtProfile::default).merge(run_profile);
    }
    ArmSummary {
        arm,
        valid_runs: valid.len(),
        summary,
        edt_samples: profile.as_ref().map(|profile| profile.samples),
        edt_frames: profile.map(|profile| profile.top(PROFILE_TOP)).unwrap_or_default(),
        runs,
    }
}

/// The delta of the medians, non-modal minus modal, or the reason that there is none.
fn delta(arms: &BTreeMap<String, ArmSummary>) -> (BTreeMap<String, f64>, Option<String>) {
    let (Some(modal), Some(non_modal)) = (arms.get(Arm::Modal.key()), arms.get(Arm::NonModal.key())) else {
        return (BTreeMap::new(), None);
    };
    if modal.valid_runs < MIN_RUNS_FOR_DELTA || non_modal.valid_runs < MIN_RUNS_FOR_DELTA {
        let note = format!(
            "no delta: modal has {} valid runs and non-modal has {}; a delta needs {MIN_RUNS_FOR_DELTA} per arm",
            modal.valid_runs, non_modal.valid_runs
        );
        return (BTreeMap::new(), Some(note));
    }
    let delta = non_modal
        .summary
        .iter()
        .filter_map(|(metric, stat)| {
            let base = modal.summary.get(metric)?;
            Some((metric.clone(), ((stat.median - base.median) * 10.0).round() / 10.0))
        })
        .collect();
    (delta, None)
}

#[cfg(test)]
mod tests;
