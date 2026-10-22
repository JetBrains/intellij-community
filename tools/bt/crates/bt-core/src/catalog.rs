//! The committed suite documents of an area's suite catalog, read once for every reader: `bt`'s join, the Air
//! UI-lane controller and its trace planner.
//!
//! A document is one suite of the flow catalog, under the flow profile directory. Only the fields a reader here uses
//! are declared, and an unknown field is ignored on purpose: `docs/scripts/flowCatalog.ts` owns the schema, and the scenario runner reads far more of it.
//!
//! The join stops at the suite, because what it needs is which classes to run. The planner needs one level
//! further down, because a trace is per scenario: a scenario name, a step or an id out of a program is answerable
//! only from the profiles. So a profile is read with its name, its setups and its steps' operations too.

use std::collections::BTreeSet;

use refusal::Refusal;
use serde::Deserialize;

use crate::areas::Area;
use crate::exit::fail_infra;
use crate::lanes::{Catalog, Lanes};
use crate::runtime::{Runtime, par_map, repo_file};
use crate::suites::ScenarioFlows;

/// One suite: a committed suite document.
#[derive(Clone, Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct SuiteDocument {
    pub suite: String,
    /// The flow catalog's name for the lane, `UI_REAL`; [SuiteDocument::lane_name] is the lane table's.
    pub lane: String,
    pub test_class_name: String,
    /// The JPS module name of every `module:` endpoint of every step this suite's routes walk.
    pub modules: Vec<String>,
    /// What the story flows of those routes declare they implement. The generator joins the two flows, so this is
    /// the only place that relation enters the controller.
    pub implementation_flows: Vec<String>,
    /// The scenarios, in declared order.
    pub profiles: Vec<SuiteProfile>,
    /// The labels of the suite for the JUnit report. `testClass` names the class with its package.
    pub labels: Vec<SuiteLabel>,
    /// The repo-relative path of the document, how a named path finds its own document. Empty for a document read from
    /// bytes.
    #[serde(skip)]
    pub path: String,
}

/// One label of a suite document: a name and a value.
#[derive(Clone, Debug, Default, Deserialize)]
#[serde(default)]
pub struct SuiteLabel {
    pub name: String,
    pub value: String,
}

/// The label that names the class of a suite with its package.
const TEST_CLASS_LABEL: &str = "testClass";

/// One scenario of a suite document.
#[derive(Clone, Debug, Default, Deserialize)]
#[serde(default)]
pub struct SuiteProfile {
    pub name: String,
    /// The flow the scenario tells.
    pub flow: String,
    pub setups: Vec<ProgramPart>,
    pub steps: Vec<SuiteStep>,
}

/// One step of a scenario: the flow it belongs to, its id, and its program.
#[derive(Clone, Debug, Default, Deserialize)]
#[serde(default)]
pub struct SuiteStep {
    pub flow: String,
    pub step: String,
    pub operations: Vec<ProgramPart>,
}

/// One setup or step operation: its id and its instructions.
#[derive(Clone, Debug, Default, Deserialize)]
#[serde(default)]
pub struct ProgramPart {
    pub operation: String,
    pub instructions: Vec<Instruction>,
}

/// One instruction of an operation: an action or an assertion, by id.
#[derive(Clone, Debug, Default, Deserialize)]
#[serde(default)]
pub struct Instruction {
    pub action: String,
    pub assertion: String,
}

impl SuiteDocument {
    /// The lane table's name for the suite's lane (`ui-real` for `UI_REAL`, `ui-perf` for `UI_PERF`), or `None` for a
    /// lane it does not declare.
    pub fn lane_name<'l>(&self, lanes: &'l Lanes) -> Option<&'l str> {
        lanes.by_suite_lane(&self.lane).map(|lane| lane.name.as_str())
    }

    /// The fully qualified name of the suite's class: the value of its `testClass` label, or the simple name when the
    /// document has no such label.
    pub fn test_class(&self) -> &str {
        self.labels
            .iter()
            .find(|label| label.name == TEST_CLASS_LABEL)
            .map_or(self.test_class_name.as_str(), |label| label.value.as_str())
    }

    /// Whether the suite belongs to an explicit-only lane, whose suites no changed path, flow or suite id reaches
    /// (ADR 0200).
    pub fn is_explicit_only(&self, lanes: &Lanes) -> bool {
        lanes.by_suite_lane(&self.lane).is_some_and(|lane| lane.document_lane.is_some())
    }
}

impl SuiteProfile {
    /// The flow the scenario tells and the flow of each of its steps.
    pub fn scenario_flows(&self) -> ScenarioFlows {
        ScenarioFlows {
            flow: self.flow.clone(),
            step_flows: self.steps.iter().map(|step| step.flow.clone()).collect(),
        }
    }

    /// Every operation, action and assertion id of the scenario's program, setups included, sorted and without
    /// the empty id: the keys a trace's spans carry, so an id copied out of the viewer answers the scenarios that
    /// run it.
    pub fn program_ids(&self) -> Vec<String> {
        let mut ids = BTreeSet::new();
        let parts = self.setups.iter().chain(self.steps.iter().flat_map(|step| &step.operations));
        for part in parts {
            ids.insert(part.operation.as_str());
            for instruction in &part.instructions {
                ids.insert(instruction.action.as_str());
                ids.insert(instruction.assertion.as_str());
            }
        }
        ids.remove("");
        ids.into_iter().map(str::to_owned).collect()
    }
}

/// Reads one suite document from its bytes, committed or dropped, and answers why it is not one otherwise.
///
/// A document is one only with its suite, its lane and its test class: those three are what every reader joins by.
pub fn parse_suite_document(content: &[u8]) -> Result<SuiteDocument, String> {
    let document: SuiteDocument = serde_json::from_slice(content).map_err(|error| format!("is malformed: {error}"))?;
    if document.suite.is_empty() || document.lane.is_empty() || document.test_class_name.is_empty() {
        return Err("names no suite, lane or test class".to_owned());
    }
    Ok(document)
}

/// Every committed suite document of the area's catalog in name order.
pub fn read_suite_documents(runtime: &dyn Runtime, area: &Area) -> Result<Vec<SuiteDocument>, Refusal> {
    read_generated_suites(runtime, &area.catalog()?)
}

/// Every committed suite document under the flow profile directory, in name order.
///
/// A test runtime loads each document by its exact resource name, so a listing is the only consumer that treats
/// the directory as a set: a document whose name it cannot read is one nothing else misses. A document that cannot
/// be read is refused rather than skipped: a reader that silently drops a suite answers "nothing covers this" for a
/// suite that exists.
pub fn read_generated_suites(runtime: &dyn Runtime, catalog: &Catalog<'_>) -> Result<Vec<SuiteDocument>, Refusal> {
    let relative = catalog.flow_profile_dir;
    let directory = repo_file(runtime, relative);
    let entries = runtime
        .read_dir(&directory)
        .map_err(|error| fail_infra(format!("the generated suite documents are unreadable at {relative}: {error}")))?;
    let mut names: Vec<String> = entries
        .into_iter()
        .filter(|entry| !entry.is_dir && entry.name.ends_with(".json"))
        .map(|entry| entry.name)
        .collect();
    if names.is_empty() {
        return Err(fail_infra(format!(
            "no generated suite document under {relative}; regenerate the flow catalog"
        )));
    }
    names.sort();
    par_map(&names, |name| {
        let text = runtime
            .read_text_file(&directory.join(name))
            .map_err(|error| fail_infra(format!("suite document {name} is unreadable: {error}")))?;
        let mut document = parse_suite_document(text.as_bytes())
            .map_err(|reason| fail_infra(format!("suite document {name} {reason}; regenerate the flow catalog")))?;
        document.path = format!("{relative}/{name}");
        Ok(document)
    })
    .into_iter()
    .collect()
}

#[cfg(test)]
mod tests;
