//! The committed suite documents of an area's suite catalog, read once for every reader: `bt`'s join, the Air
//! UI-lane controller and its trace planner.
//!
//! A generated document is one suite of the flow catalog, under the flow profile directory; an authored suite is
//! an entry of the authored suites file. Only the fields a reader here uses are declared, and an unknown field is
//! ignored on purpose: `docs/scripts/flowCatalog.ts` owns the schema, and the scenario runner reads far more of it.
//!
//! The join stops at the suite, because what it needs is which classes to run. The planner needs one level
//! further down, because a trace is per scenario: a scenario name, a step or an id out of a program is answerable
//! only from the profiles. So a profile is read with its name, its setups and its steps' operations too.

use std::collections::BTreeSet;

use serde::Deserialize;

use crate::areas::Area;
use crate::lanes::{Catalog, Lanes};
use crate::refusal::{Refusal, fail_infra};
use crate::runtime::{Runtime, par_map, repo_file};
use crate::suites::ScenarioFlows;

/// One suite: a committed suite document, or an entry of the authored suites file.
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
    /// The scenarios, in declared order. An authored suite has none.
    pub profiles: Vec<SuiteProfile>,
    /// The repo-relative path of the document, how a named path finds its own document. Empty for an authored
    /// suite and for a document read from bytes.
    #[serde(skip)]
    pub path: String,
    #[serde(skip)]
    pub authored: bool,
}

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
    /// The lane table's name for the suite's lane (`ui-real` for `UI_REAL`), or `None` for a lane it does not
    /// declare.
    pub fn lane_name<'l>(&self, lanes: &'l Lanes) -> Option<&'l str> {
        lanes.by_catalog_lane(&self.lane).map(|lane| lane.name.as_str())
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

/// Every committed suite document of the area's catalog in name order, and then every authored suite.
///
/// Both kinds are one set: a suite id and a test class name each name one suite, whichever kind it is. A clash is
/// refused, because the answer would name one suite for a path that reaches the other.
pub fn read_suite_documents(runtime: &dyn Runtime, area: &Area) -> Result<Vec<SuiteDocument>, Refusal> {
    let catalog = area.catalog()?;
    let mut documents = read_generated_suites(runtime, &catalog)?;
    let authored = read_authored_suites(runtime, &catalog)?;
    let mut suites: BTreeSet<String> = documents.iter().map(|document| document.suite.clone()).collect();
    let mut classes: BTreeSet<String> = documents.iter().map(|document| document.test_class_name.clone()).collect();
    for document in &authored {
        if !suites.insert(document.suite.clone()) || !classes.insert(document.test_class_name.clone()) {
            return Err(fail_infra(format!(
                "the authored suite {} ({}) has the id or the test class of another suite; rename it where \
                 {} is written from",
                document.suite, document.test_class_name, catalog.authored_suites_file
            )));
        }
    }
    documents.extend(authored);
    Ok(documents)
}

/// The authored suites file.
///
/// A checkout without the file answers no authored suite, as every checkout did before the file existed. The
/// fast-lane test owns that the committed file exists and matches. A file that is there and cannot be read or
/// parsed is refused.
fn read_authored_suites(runtime: &dyn Runtime, catalog: &Catalog<'_>) -> Result<Vec<SuiteDocument>, Refusal> {
    #[derive(Deserialize)]
    struct Held {
        #[serde(default)]
        suites: Vec<SuiteDocument>,
    }

    let relative = catalog.authored_suites_file;
    let file = repo_file(runtime, relative);
    if !runtime.exists(&file) {
        return Ok(Vec::new());
    }
    let text = runtime
        .read_text_file(&file)
        .map_err(|error| fail_infra(format!("the authored suites are unreadable at {relative}: {error}")))?;
    let held: Held = serde_json::from_str(&text).map_err(|error| {
        fail_infra(format!(
            "{relative} is malformed: {error}; the test that owns the file prints the text to commit"
        ))
    })?;
    let mut suites = held.suites;
    for document in &mut suites {
        if document.suite.is_empty() || document.lane.is_empty() || document.test_class_name.is_empty() {
            return Err(fail_infra(format!(
                "{relative} holds a suite with no suite, lane or test class; the test that owns the file prints \
                 the text to commit"
            )));
        }
        document.authored = true;
    }
    Ok(suites)
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
