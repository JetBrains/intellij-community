//! The split of a lane across the runners of a TeamCity job.
//!
//! Every runner of a job selects the same suites from the same documents, and computes the same split from them. So
//! the runners of one job together run each selected suite once. The duration split reads the season file of the JVM
//! duration bucketing, `<season>.csv` of `tests/classes-duration/seasons`, which the rule of the lane test names as
//! `--season-dir`. A run without that file, or whose file names no selected suite, takes the position split.

use std::collections::BTreeMap;
use std::path::Path;

use avl_wire::daemon::LaneClass;

use super::program::Suite;

#[cfg(test)]
mod tests;

/// The share of one runner when a TeamCity job splits a lane across its runners, and the durations of the duration
/// split. The whole lane is runner 0 of 1.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct RunnerSplit {
    pub index: u32,
    pub count: u32,
    /// The durations of the season of the job, or `None` for the position split.
    pub durations: Option<SeasonDurations>,
}

/// The durations of the suites of one season of the JVM duration bucketing, in milliseconds, by the simple name of the
/// test class.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct SeasonDurations {
    pub season: String,
    pub by_class: BTreeMap<String, u64>,
}

/// The suites of the selection that one runner takes, and the note that names the split, or `None` for the whole lane.
#[derive(Debug)]
pub(crate) struct Share {
    pub suites: Vec<Suite>,
    pub undocumented: Vec<LaneClass>,
    pub note: Option<String>,
}

/// The greedy split of the selected suites by their durations.
#[derive(Debug, PartialEq, Eq)]
pub(crate) struct DurationPlan {
    /// The index of the runner of each suite, by its test class.
    pub runner_of: BTreeMap<String, u32>,
    /// The expected milliseconds of each runner, by its index.
    pub totals: Vec<u64>,
    /// The count of the suites that the season does not name.
    pub defaulted: usize,
    /// The duration of a suite that the season does not name: the median of the selected suites that it names.
    pub median: u64,
}

impl RunnerSplit {
    /// The whole lane: the split of the lane daemon and of a run that names no runner.
    pub(crate) const WHOLE: Self = Self {
        index: 0,
        count: 1,
        durations: None,
    };

    /// The suites and the undocumented suites of the lane `lane` that this runner takes, in their order. The duration
    /// split applies when the season names at least one selected suite, and the position split applies otherwise.
    pub(crate) fn share(&self, lane: &str, suites: Vec<Suite>, undocumented: Vec<LaneClass>) -> Share {
        if self.count <= 1 {
            return Share {
                suites,
                undocumented,
                note: None,
            };
        }
        let selected = suites.len() + undocumented.len();
        let runner = format!("lane {lane}: the runner with index {} of {} runners", self.index, self.count);
        if let Some(durations) = &self.durations {
            let classes: Vec<&str> = suites
                .iter()
                .map(|suite| suite.program.test_class_name.as_str())
                .chain(undocumented.iter().map(|class| class.test_class_name.as_str()))
                .collect();
            if let Some(plan) = durations.plan(&classes, self.count) {
                let takes = |class: &str| plan.runner_of.get(class) == Some(&self.index);
                let suites: Vec<Suite> = suites.into_iter().filter(|suite| takes(&suite.program.test_class_name)).collect();
                let undocumented: Vec<LaneClass> = undocumented.into_iter().filter(|class| takes(&class.test_class_name)).collect();
                let note = plan.describe(&runner, self.index, suites.len() + undocumented.len(), selected, &durations.season);
                return Share {
                    suites,
                    undocumented,
                    note: Some(note),
                };
            }
            let suites = self.by_position(suites);
            let undocumented = self.by_position(undocumented);
            let note = format!(
                "{runner} takes {} of the {selected} selected suite(s) by the position split, because the season {} names no \
                 selected suite: each one whose position in the lane order is {} modulo {}",
                suites.len() + undocumented.len(),
                durations.season,
                self.index,
                self.count,
            );
            return Share {
                suites,
                undocumented,
                note: Some(note),
            };
        }
        let suites = self.by_position(suites);
        let undocumented = self.by_position(undocumented);
        let note = format!(
            "{runner} takes {} of the {selected} selected suite(s) by the position split: each one whose position in the \
             lane order is {} modulo {}",
            suites.len() + undocumented.len(),
            self.index,
            self.count,
        );
        Share {
            suites,
            undocumented,
            note: Some(note),
        }
    }

    /// The items at the positions that this runner takes: each one whose position is the index modulo the count.
    fn by_position<T>(&self, items: Vec<T>) -> Vec<T> {
        items
            .into_iter()
            .enumerate()
            .filter_map(|(position, item)| {
                u32::try_from(position)
                    .is_ok_and(|position| position % self.count == self.index)
                    .then_some(item)
            })
            .collect()
    }
}

impl SeasonDurations {
    /// Reads `<season>.csv` of `directory`, and answers the note of the run that says what it read. A file that cannot
    /// be read, or that holds no duration, is a note and no durations, so the run takes the position split.
    pub(crate) fn read(directory: &Path, season: &str) -> (Option<Self>, String) {
        let file = directory.join(format!("{season}.csv"));
        let text = match std::fs::read_to_string(&file) {
            Ok(text) => text,
            Err(error) => {
                return (
                    None,
                    format!(
                        "lane-run splits the lane by position, because it cannot read the durations of the season {season} \
                         from {}: {error}",
                        file.display()
                    ),
                );
            }
        };
        let (durations, skipped) = Self::parse(season, &text);
        if durations.by_class.is_empty() {
            return (
                None,
                format!(
                    "lane-run splits the lane by position, because {} holds no duration of the season {season}",
                    file.display()
                ),
            );
        }
        let mut note = format!(
            "lane-run splits the lane by the durations of {} suite(s) of the season {season} from {}",
            durations.by_class.len(),
            file.display()
        );
        if skipped > 0 {
            note.push_str(&format!(", and it skips {skipped} row(s) that are no <test class>,<milliseconds>"));
        }
        (Some(durations), note)
    }

    /// Parses the rows `<test class>,<milliseconds>` of a season file, the format of the JVM duration bucketing, and
    /// answers the count of the rows in another form, which it skips. A test class is keyed by its simple name, as a
    /// suite document names it. Two rows of one simple name keep the larger duration.
    pub(crate) fn parse(season: &str, text: &str) -> (Self, usize) {
        let mut by_class = BTreeMap::new();
        let mut skipped = 0;
        for line in text.lines().map(str::trim).filter(|line| !line.is_empty()) {
            let parsed = line.split_once(',').and_then(|(class, milliseconds)| {
                let class = class.trim().rsplit('.').next().filter(|class| !class.is_empty())?;
                Some((class, milliseconds.trim().parse::<u64>().ok()?))
            });
            match parsed {
                Some((class, milliseconds)) => {
                    let duration = by_class.entry(class.to_owned()).or_insert(0);
                    *duration = (*duration).max(milliseconds);
                }
                None => skipped += 1,
            }
        }
        (
            Self {
                season: season.to_owned(),
                by_class,
            },
            skipped,
        )
    }

    /// The greedy split of the suites `classes` across `count` runners: the longest suite first, to the runner with the
    /// least expected total. Equal durations go in the order of the class name, and equal totals to the lower index. A
    /// suite that the season does not name takes the median of the selected suites that it names. `None` when the
    /// season names no selected suite.
    pub(crate) fn plan(&self, classes: &[&str], count: u32) -> Option<DurationPlan> {
        let mut known: Vec<u64> = classes.iter().filter_map(|class| self.by_class.get(*class).copied()).collect();
        if known.is_empty() || count == 0 {
            return None;
        }
        known.sort_unstable();
        let median = known[(known.len() - 1) / 2];
        let mut suites: Vec<(u64, &str)> = classes
            .iter()
            .map(|class| (self.by_class.get(*class).copied().unwrap_or(median), *class))
            .collect();
        suites.sort_by(|left, right| right.0.cmp(&left.0).then_with(|| left.1.cmp(right.1)));
        let mut totals = vec![0_u64; count as usize];
        let mut runner_of = BTreeMap::new();
        for (duration, class) in suites {
            let (runner, total) = totals
                .iter_mut()
                .enumerate()
                .min_by_key(|(runner, total)| (**total, *runner))
                .expect("a split has at least one runner");
            *total += duration;
            runner_of.insert(class.to_owned(), u32::try_from(runner).expect("the runner count is a u32"));
        }
        Some(DurationPlan {
            runner_of,
            totals,
            defaulted: classes.len() - known.len(),
            median,
        })
    }
}

impl DurationPlan {
    /// The note of the runner `index`, which takes `taken` of the `selected` suites by the durations of `season`.
    fn describe(&self, runner: &str, index: u32, taken: usize, selected: usize, season: &str) -> String {
        let total: u64 = self.totals.iter().sum();
        let mut note = format!(
            "{runner} takes {taken} of the {selected} selected suite(s) by the duration split of the season {season}: an \
             expected {} of {}, and the runners expect {}",
            minutes(self.totals.get(index as usize).copied().unwrap_or_default()),
            minutes(total),
            self.totals.iter().map(|total| minutes(*total)).collect::<Vec<_>>().join(", "),
        );
        if self.defaulted > 0 {
            note.push_str(&format!(
                "; {} suite(s) that the season does not name take the median {}",
                self.defaulted,
                minutes(self.median)
            ));
        }
        note
    }
}

/// Milliseconds as minutes with one decimal, such as `12.5 min`.
fn minutes(milliseconds: u64) -> String {
    format!("{:.1} min", milliseconds as f64 / 60_000.0)
}
