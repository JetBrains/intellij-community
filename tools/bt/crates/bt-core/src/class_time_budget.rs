//! A lane's class-time limit and its reviewed exceptions.

use std::collections::{BTreeMap, BTreeSet};
use std::path::Path;

use refusal::Refusal;
use serde::Deserialize;

use crate::exit::fail_infra;
use crate::runtime::{Runtime, repo_file};
use crate::shard_plan::ShardPlan;

/// A class-time limit configured on a lane.
#[derive(Clone, Debug, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct ClassTimeBudget {
    /// The longest recorded class time that does not need a baseline entry.
    pub seconds: u32,
    /// The repo-relative TSV file of reviewed classes over the limit.
    pub baseline: String,
}

/// One class in a run that exceeds its lane's limit and has no baseline reason.
#[derive(Clone, Debug, PartialEq)]
pub struct ClassTimeBudgetViolation {
    pub target_label: String,
    pub class_name: String,
    pub seconds: f64,
    pub limit_seconds: u32,
}

impl ClassTimeBudgetViolation {
    /// A short form for the digest and the JSON payload.
    pub fn summary(&self) -> String {
        format!(
            "{} in {}: {:.1}s > {}s",
            self.class_name, self.target_label, self.seconds, self.limit_seconds
        )
    }
}

/// Class or suite identifiers and the reasons that keep them above the time limit.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct ClassTimeBudgetBaseline {
    reasons: BTreeMap<String, String>,
}

impl ClassTimeBudget {
    /// Checks the threshold and repo-relative path before a lane table is used.
    pub fn validate(&self) -> Result<(), String> {
        if self.seconds == 0 {
            return Err("classTimeBudget.seconds must be greater than zero".to_owned());
        }
        let path = Path::new(&self.baseline);
        if self.baseline.is_empty()
            || self.baseline.contains('\\')
            || path.is_absolute()
            || self.baseline.split('/').any(|part| matches!(part, "" | "." | ".."))
        {
            return Err(format!(
                "classTimeBudget.baseline must be a repo-relative slash path without dot segments: {}",
                self.baseline
            ));
        }
        Ok(())
    }

    /// Reads the versioned exceptions for this lane.
    pub fn load_baseline(&self, runtime: &dyn Runtime) -> Result<ClassTimeBudgetBaseline, Refusal> {
        let path = repo_file(runtime, &self.baseline);
        let text = runtime
            .read_text_file(&path)
            .map_err(|error| fail_infra(format!("cannot read class-time budget baseline {}: {error}", path.display())))?;
        ClassTimeBudgetBaseline::parse(&text)
            .map_err(|reason| fail_infra(format!("class-time budget baseline {} is malformed: {reason}", path.display())))
    }

    /// Finds recorded classes above the limit that have no reviewed reason.
    pub fn violations(
        &self,
        plan: &ShardPlan,
        target_labels: &BTreeSet<String>,
        baseline: &ClassTimeBudgetBaseline,
    ) -> Vec<ClassTimeBudgetViolation> {
        let limit = f64::from(self.seconds);
        let mut violations: Vec<ClassTimeBudgetViolation> = plan
            .class_times()
            .filter(|(label, class, seconds)| {
                target_labels.contains(*label) && seconds.is_finite() && *seconds > limit && !baseline.reasons.contains_key(*class)
            })
            .map(|(label, class, seconds)| ClassTimeBudgetViolation {
                target_label: label.to_owned(),
                class_name: class.to_owned(),
                seconds,
                limit_seconds: self.seconds,
            })
            .collect();
        violations.sort_by(|left, right| {
            right
                .seconds
                .total_cmp(&left.seconds)
                .then_with(|| left.class_name.cmp(&right.class_name))
                .then_with(|| left.target_label.cmp(&right.target_label))
        });
        violations
    }
}

impl ClassTimeBudgetBaseline {
    /// Parses `class or suite identifier<TAB>reason` rows. Blank lines and comments are ignored.
    pub fn parse(text: &str) -> Result<Self, String> {
        let mut reasons = BTreeMap::new();
        for (index, line) in text.lines().enumerate() {
            let line = line.trim_end_matches('\r');
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            let row = index + 1;
            let Some((class, reason)) = line.split_once('\t') else {
                return Err(format!("line {row} has no tab between class name and reason"));
            };
            if class.is_empty() || class.trim() != class {
                return Err(format!("line {row} has an invalid class name"));
            }
            if reason.trim().is_empty() || reason.contains('\t') {
                return Err(format!("line {row} has no single-line reason"));
            }
            if reasons.insert(class.to_owned(), reason.to_owned()).is_some() {
                return Err(format!("line {row} repeats class {class}"));
            }
        }
        Ok(Self { reasons })
    }
}

#[cfg(test)]
mod tests;
