//! The rerun of the failed scenarios of a `lane-run`, and the failures that a run injects to prove it.
//!
//! A TeamCity lane job runs a whole lane in one Bazel test. One failed scenario fails the test, and the retry policy of
//! the build then starts all scenarios again. So `lane-run` runs each failed scenario of the lane IDE once more, after
//! the last suite of the lane IDE and before the suites of a bare IDE. The program runner recycles the IDE once before
//! the first rerun, so each rerun gets a new IDE process on the same context, and then the reset of its scenario. The
//! run stream holds both attempts under one id. A later pass of a scenario clears its failure in the counts of the
//! stream, so the run is green, and the JUnit XML keeps the failed attempt for TeamCity.
//!
//! [`refusal`] is the one policy of when no rerun starts: past [`MAX_FAILED`] failures, after the watchdog ended the
//! run, on an exhausted isolation, in the live lane, and when the budget left cannot hold the recycle. The lane daemon
//! of a worker has no [`Rerun`], so a run of the controller shows each flake.
//!
//! [`INJECT_VARIABLE`] fails a named scenario on purpose, so a run proves the rerun with a real lane IDE.

use std::time::Duration;

use crate::reply::{AgentRefusal, AgentRefusalExt};

#[cfg(test)]
mod tests;

/// The most failures of a run that still get a rerun: the failed scenarios and the failed suite prologues. More
/// failures are a break of the product, not a flake. Of 600 bucket builds of the UI lane in October 2026, 186 of the
/// 188 builds with failed tests had at most 3 of them.
pub(crate) const MAX_FAILED: u32 = 3;
/// The variable that injects failures into the named scenarios, a list of [`Injection`] separated by `;`.
pub(crate) const INJECT_VARIABLE: &str = "AIR_LANE_INJECT_FAILURE";
/// The refusal code of a value of [`INJECT_VARIABLE`] that names no injection.
pub(crate) const INJECT_CODE: &str = "lane_run_inject_failure";

/// What a `lane-run` adds to the program runner: the rerun of the failed scenarios, and the injected failures.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct Rerun {
    pub injected: Vec<Injection>,
}

/// The failure of one scenario that [`INJECT_VARIABLE`] asks for: `first:<scenario>` or `every:<scenario>`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Injection {
    pub attempts: Attempts,
    /// The name or the key of the scenario.
    pub scenario: String,
}

/// Which attempts of a scenario an [`Injection`] fails.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Attempts {
    /// The first attempt only, so the rerun passes.
    First,
    /// Every attempt, so the rerun fails too.
    Every,
}

impl Attempts {
    const fn as_str(self) -> &'static str {
        match self {
            Self::First => "first",
            Self::Every => "every",
        }
    }
}

impl Injection {
    /// Whether this injection fails the attempt of `names`, the name and the key of a scenario. `rerun` is true for the
    /// second attempt.
    pub(crate) fn fails(&self, names: &[&str], rerun: bool) -> bool {
        names.contains(&self.scenario.as_str()) && (self.attempts == Attempts::Every || !rerun)
    }

    /// The note that names this injection on the run stream.
    pub(crate) fn describe(&self) -> String {
        format!(
            "${INJECT_VARIABLE} fails {} attempt of the scenario '{}'",
            match self.attempts {
                Attempts::First => "the first",
                Attempts::Every => "each",
            },
            self.scenario
        )
    }
}

/// The rerun of a `lane-run`, with the injections of [`INJECT_VARIABLE`]. An unset or blank variable injects nothing. A
/// part that is no `first:<scenario>` or `every:<scenario>` is refused by name.
pub(crate) fn from_environment(variable: &dyn Fn(&str) -> Option<String>) -> Result<Rerun, AgentRefusal> {
    let value = variable(INJECT_VARIABLE).unwrap_or_default();
    let injected = value
        .split(';')
        .map(str::trim)
        .filter(|part| !part.is_empty())
        .map(|part| {
            let parsed = part.split_once(':').and_then(|(attempts, scenario)| {
                let attempts = [Attempts::First, Attempts::Every]
                    .into_iter()
                    .find(|candidate| candidate.as_str() == attempts.trim())?;
                let scenario = scenario.trim();
                (!scenario.is_empty()).then(|| Injection {
                    attempts,
                    scenario: scenario.to_owned(),
                })
            });
            parsed.ok_or_else(|| {
                AgentRefusal::refused(
                    INJECT_CODE,
                    format!("${INJECT_VARIABLE} names {part:?}, and it takes only first:<scenario> or every:<scenario>, separated by ;"),
                )
            })
        })
        .collect::<Result<_, _>>()?;
    Ok(Rerun { injected })
}

/// The state of a run at the end of the suites of its lane IDE, which [`refusal`] reads.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Moment {
    /// The failed scenarios and the failed suite prologues of the run so far.
    pub failed: u32,
    /// Whether the watchdog ended the run.
    pub expired: bool,
    /// Whether the isolation of the run is exhausted, so no later scenario can run honestly.
    pub exhausted: bool,
    /// Whether the lane is the live lane, whose real agents spend the turns of real accounts.
    pub live: bool,
    /// What is left of the budget of the run, or `None` for a run with no budget.
    pub budget_left: Option<Duration>,
    /// What the recycle before the rerun can take.
    pub recycle_need: Duration,
}

/// Why no failed scenario reruns at `moment`, or `None` when the rerun starts.
pub(crate) fn refusal(moment: Moment) -> Option<String> {
    if moment.expired {
        return Some("the watchdog ended the run".to_owned());
    }
    if moment.exhausted {
        return Some("the isolation of the run is exhausted, so no later scenario runs".to_owned());
    }
    if moment.live {
        return Some("a rerun in the live lane spends the turns of its real accounts again".to_owned());
    }
    if moment.failed > MAX_FAILED {
        return Some(format!(
            "{} scenarios and suite prologues failed, more than {MAX_FAILED}, which is a break and not a flake",
            moment.failed
        ));
    }
    match moment.budget_left {
        Some(left) if left < moment.recycle_need => Some(format!(
            "the lane budget left {} s, and the recycle before the rerun needs up to {} s",
            left.as_secs(),
            moment.recycle_need.as_secs()
        )),
        _ => None,
    }
}
