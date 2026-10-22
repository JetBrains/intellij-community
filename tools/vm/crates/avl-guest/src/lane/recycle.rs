//! Scenario isolation: the reset on both sides of a scenario, and the recycle of the lane IDE when a reset does not
//! hold.
//!
//! A reset that fails is no scenario failure to report and move past: what it could not undo is still there for the
//! next scenario. The remedy is a new IDE on the same context, so the runner recycles one and resets again. The budget
//! is [`MAX_RECYCLES`] per run, because an IDE that needs a recycle again and again is a run that stopped meaning
//! anything. Past the budget, each scenario left is skipped with the sentence of [`never_ran`].
//!
//! The remedy can fail in two ways, and they end differently:
//! - **unavailable**: the recycle itself failed, or its IDE never got ready. Nothing restored the state, so the run is
//!   over, and every scenario left is skipped.
//! - **insufficient**: the recycle worked and the reset still failed. This scenario fails, and the budget decides
//!   whether the next one runs.
//!
//! A run with a time budget also refuses a recycle that cannot end inside what is left of it. Every scenario left is
//! then skipped, so the run ends with an account of each one and not at the hard stop.

use std::fmt;
use std::time::Duration;

#[cfg(test)]
mod tests;

/// How many recycles one run may spend on resets that did not hold.
pub(crate) const MAX_RECYCLES: usize = 3;

/// The side of a scenario that a reset runs on. Only the wording differs.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum ResetPhase {
    Before,
    After,
}

impl ResetPhase {
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::Before => "before",
            Self::After => "after",
        }
    }
}

/// A reset that did not hold, with what the remedy added to it: the failed recycle, or the reset after it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct ResetFailure {
    pub message: String,
    pub suppressed: Vec<String>,
}

impl fmt::Display for ResetFailure {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.message)?;
        for suppressed in &self.suppressed {
            write!(formatter, "\nSuppressed: {suppressed}")?;
        }
        Ok(())
    }
}

/// What is left of the time budget of a run when a reset fails, and what one recycle can take.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct RecycleBudget {
    pub left: Duration,
    pub need: Duration,
}

/// The isolation of the scenarios of one run, and its recycle budget.
#[derive(Debug, Default)]
pub(crate) struct Isolation {
    /// Every reset failure the run had to answer, in order.
    incidents: Vec<String>,
    /// The incidents that a completed recycle answered: the count that the budget bounds.
    recycles: Vec<String>,
    /// Why the remedy was unavailable, once it was.
    remedy_failure: Option<String>,
    /// Why the time budget of the run refused a recycle, once it did.
    budget_refusal: Option<String>,
    exhausted: bool,
}

impl Isolation {
    /// Whether no later scenario can run honestly: the budget is spent, or no usable IDE is to be had.
    pub(crate) const fn exhausted(&self) -> bool {
        self.exhausted
    }

    /// Runs `reset`. A reset that fails recycles the IDE through `recycle` and runs `reset` again. With `budget`, a
    /// recycle that needs more than is left of the time budget is refused, and the isolation is exhausted.
    pub(crate) fn ensure_clean(
        &mut self,
        phase: ResetPhase,
        scenario: &str,
        budget: Option<RecycleBudget>,
        reset: &mut dyn FnMut() -> Result<(), String>,
        recycle: &mut dyn FnMut() -> Result<(), String>,
    ) -> Result<(), ResetFailure> {
        let Err(message) = reset() else { return Ok(()) };
        let description = format!("{} '{scenario}': {message}", phase.as_str());
        self.incidents.push(description.clone());
        let mut failure = ResetFailure {
            message,
            suppressed: Vec::new(),
        };
        if self.recycles.len() >= MAX_RECYCLES {
            self.exhausted = true;
            return Err(failure);
        }
        if let Some(budget) = budget.filter(|budget| budget.left < budget.need) {
            let refusal = format!(
                "the lane budget left {} s, and a recycle needs up to {} s",
                budget.left.as_secs(),
                budget.need.as_secs()
            );
            self.budget_refusal = Some(refusal.clone());
            self.exhausted = true;
            failure.suppressed.push(refusal);
            return Err(failure);
        }
        if let Err(unavailable) = recycle() {
            self.remedy_failure = Some(unavailable.clone());
            self.exhausted = true;
            failure.suppressed.push(unavailable);
            return Err(failure);
        }
        self.recycles.push(description);
        // A quit flushes the workspace, so the recycled IDE restores what the scenario left. The reset again is what
        // makes the recycled IDE a clean one.
        let Err(still_dirty) = reset() else { return Ok(()) };
        self.incidents
            .push(format!("{} '{scenario}' after recycle: {still_dirty}", phase.as_str()));
        failure.suppressed.push(still_dirty);
        Err(failure)
    }

    /// Why nothing further is worth running. The two causes have different fixes, so the sentence tells them apart.
    pub(crate) fn exhaustion_reason(&self) -> String {
        let history = self.incidents.join("; ");
        if let Some(refusal) = &self.budget_refusal {
            return format!("{refusal}, so no recycle answered these resets: {history}");
        }
        match &self.remedy_failure {
            None => format!(
                "the shared IDE was recycled {} time(s) and isolation still failed: {history}",
                self.recycles.len()
            ),
            Some(remedy) => format!(
                "the shared IDE could not be recycled into a usable one ('{remedy}'), so nothing restored the state these \
                 resets failed on: {history}"
            ),
        }
    }

    /// The one line that names the recycles of a run that needed some, or `None`.
    pub(crate) fn report(&self) -> Option<String> {
        (!self.recycles.is_empty()).then(|| format!("The IDE was recycled {} time(s): {}", self.recycles.len(), self.recycles.join("; ")))
    }
}

/// The reason of a scenario that the run skipped once the isolation was exhausted.
pub(crate) fn never_ran(scenario: &str, reason: &str) -> String {
    format!("scenario '{scenario}' never ran: {reason}")
}
