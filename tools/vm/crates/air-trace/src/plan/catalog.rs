//! The committed suite documents, read as the scenarios they declare.
//!
//! The reading is `bt`'s, [bt_core::catalog], the one [avl_affected::affected_suites] joins through: this holds the
//! generated suites and answers the planner's questions one level below the suite, because a trace is per
//! scenario, and a flow id, a scenario name or a step of a program is only answerable from the profiles. Routes,
//! variants and the rest of the flow model stay `docs/scripts/flowCatalog.ts`'s, which wrote these files.

use bt_core::OsRuntime;
use bt_core::catalog::{SuiteDocument, SuiteProfile, read_generated_suites};

use crate::{Exit, Refusal, from_bt};

/// Every generated suite, in file-name order, the order `bt` reads them in too. An authored suite is not here: it
/// has no profile, so a name that reaches one goes on to the selector, which plans it as a class.
#[derive(Debug)]
pub(crate) struct Catalog {
    pub suites: Vec<SuiteDocument>,
}

/// Reads every committed suite document under the checkout, and refuses a document whose lane no VM worker runs.
///
/// A document that cannot be read is an error rather than a skipped suite, for the reason `bt` refuses one: a
/// planner that silently drops a suite answers "no scenario covers this" for a scenario that exists.
pub(crate) fn read_catalog(runtime: &OsRuntime) -> Result<Catalog, Refusal> {
    let catalog = avl_affected::air_area().catalog().map_err(from_bt)?;
    let suites = read_generated_suites(runtime, &catalog).map_err(from_bt)?;
    for suite in &suites {
        checked_lane(suite).map_err(|mut refused| {
            refused.message = format!("suite {}: {}", suite.suite, refused.message);
            refused
        })?;
    }
    Ok(Catalog { suites })
}

/// A suite's lane in the words `vm.cmd --lane` takes, or why a suite document declares none of them.
pub(crate) fn checked_lane(suite: &SuiteDocument) -> Result<&'static str, Refusal> {
    suite.lane_name(avl_affected::air_area().lanes()).ok_or_else(|| {
        Refusal::new(
            "suite_lane_unknown",
            Exit::Broken,
            format!(
                "declares the lane {}, which is none of {}",
                suite.lane,
                avl_affected::ui_lane_names().join(", ")
            ),
        )
    })
}

/// A suite's lane in the words `vm.cmd --lane` takes. Every suite the planner holds has one: [read_catalog] and
/// the dropped-document reader refuse a document without it.
pub(crate) fn lane_of(suite: &SuiteDocument) -> &'static str {
    suite.lane_name(avl_affected::air_area().lanes()).unwrap_or_default()
}

impl Catalog {
    /// The suite whose generated class has this simple name.
    pub(crate) fn by_class(&self, simple_name: &str) -> Option<&SuiteDocument> {
        self.suites.iter().find(|candidate| candidate.test_class_name == simple_name)
    }

    /// One suite by its id.
    pub(crate) fn suite_by_id(&self, id: &str) -> Option<&SuiteDocument> {
        self.suites.iter().find(|candidate| candidate.suite == id)
    }

    /// Whether a scenario tells or walks the flow, or a suite declares it as an implementation flow.
    pub(crate) fn knows_flow(&self, flow: &str) -> bool {
        self.suites.iter().any(|candidate| {
            candidate.implementation_flows.iter().any(|known| known == flow)
                || candidate.profiles.iter().any(|scenario| scenario.scenario_flows().walks(flow))
        })
    }

    /// Every scenario that tells or walks the flow, and every scenario of a suite whose story flows implement it:
    /// the same two relations `bt` applies to a `@flow` tag, one level down.
    pub(crate) fn scenarios_of_flow(&self, flow: &str) -> Vec<(&SuiteDocument, &SuiteProfile)> {
        let mut matches = Vec::new();
        for candidate in &self.suites {
            let implements = candidate.implementation_flows.iter().any(|known| known == flow);
            for scenario in &candidate.profiles {
                if implements || scenario.scenario_flows().walks(flow) {
                    matches.push((candidate, scenario));
                }
            }
        }
        matches
    }

    /// Every scenario a bare name reaches: a suite id, a scenario name, a generated class, or a step, operation,
    /// action or assertion id of a program. A name can be more than one of these at once -
    /// `mark-session-folder-done` is a suite and its one scenario - so every relation is asked and the answer is
    /// their union.
    pub(crate) fn scenarios_named(&self, name: &str) -> Vec<(&SuiteDocument, &SuiteProfile)> {
        let mut matches = Vec::new();
        for candidate in &self.suites {
            let whole_suite = candidate.suite == name || candidate.test_class_name == name;
            for scenario in &candidate.profiles {
                if whole_suite
                    || scenario.name == name
                    || scenario.steps.iter().any(|step| step.step == name)
                    || scenario.program_ids().iter().any(|id| id == name)
                {
                    matches.push((candidate, scenario));
                }
            }
        }
        matches
    }
}
