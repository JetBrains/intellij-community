//! The program of one generated suite: the suite document that `plugins/air/docs/scripts/flowCatalog.ts` writes under
//! the flow profile directory, read as a lane runner reads it.
//!
//! The generator owns the schema. This side declares the fields that a runner needs and ignores the others: the
//! reach of the suite, the expectations, the fixture names. What it holds the document to is the vocabulary: every id
//! that an instruction names has an entry of its kind, a `host` id is a word of [`HostAction`] or [`HostAssertion`],
//! an `ide` id is not, and every argument fits the schema of its id. A value that an id takes has an answer earlier in
//! its scenario, or the runner answers it before the first instruction. A part of an authored program states the values
//! it takes and answers on its instruction, and the same rule holds for them.

use std::collections::{BTreeMap, BTreeSet};

use serde::Deserialize;
use serde_json::Value;
use serde_json::value::RawValue;

mod documents;
pub mod lane;

pub use documents::{LANE_DOCUMENT_DIRECTORY, SUITE_DOCUMENT_DIRECTORY, read_lane_documents, read_suite_documents};

#[cfg(test)]
mod tests;

vocabulary! {
    /// Where an id runs.
    pub enum Side {
        /// In the lane IDE, through the bridge.
        Ide = "ide",
        /// On the host of the lane, against a lane service or the IDE process.
        Host = "host",
    }
}

vocabulary! {
    /// The actions that a lane runner performs on the host. A runner matches over them, so an id with no word here
    /// cannot be a host action.
    pub enum HostAction {
        /// Approves the JetBrains Account login of the value `signInUrl` in the JetBrains Cloud emulator.
        AccountApproveSignIn = "account-approve-sign-in",
        /// Pauses the next package download of the fake registry.
        ConsentHoldDownload = "consent-hold-download",
        /// Makes the fake registry fail the next package download.
        RegistryFailNextDownload = "registry-fail-next-download",
        /// Answers the count of package requests that the fake registry answered so far, as `consentRegistryBaseline`.
        RegistryMarkRequests = "registry-mark-requests",
        /// Lets the paused package download of the fake registry finish.
        ReleaseDownload = "release-download",
        /// Quits the IDE the way a user does and starts it again on the same context. Between the quit and the launch it
        /// performs the file operations of the value `whileDown`, each on a path relative to the IDE context, and it
        /// answers the text of each read in `whileDownReads`.
        RestartIde = "restart-ide",
    }
}

vocabulary! {
    /// The assertions that a lane runner checks on the host.
    pub enum HostAssertion {
        /// The fake registry fails the next package download.
        ConsentDownloadFaultArmed = "consent-download-fault-armed",
        /// The fake registry answered the stated count of package requests since `consentRegistryBaseline`.
        RegistryRequestCount = "registry-request-count",
    }
}

vocabulary! {
    /// The type of one argument.
    pub enum ParamType {
        Int = "int",
        Boolean = "boolean",
        /// A string of [`ParamSpec::values`].
        Enum = "enum",
    }
}

/// One suite document, as a lane runner reads it.
#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SuiteProgram {
    pub suite: String,
    pub lane: String,
    pub test_class_name: String,
    /// Whether the scenarios are authored programs of parts and host ids, not story scenarios of setups and steps.
    #[serde(default)]
    pub authored: bool,
    /// The Allure labels of the generated class, which a runner passes in the `scenario` command of the recorder.
    pub labels: Vec<SuiteLabel>,
    pub vocabulary: Vocabulary,
    /// What the state reset of each scenario sends. Absent in a document that predates it.
    #[serde(default, deserialize_with = "crate::json::non_null")]
    pub reset: Option<SuiteReset>,
    /// The fixtures of the scenarios, by the fixture name that a scenario states.
    #[serde(default)]
    pub fixtures: BTreeMap<String, SuiteFixture>,
    pub profiles: Vec<ScenarioProgram>,
}

/// What a runner reads of one fixture. The generator owns the other fields.
#[derive(Clone, Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SuiteFixture {
    /// The registry agent of the fixture starts installed: the suite installs it before its first scenario.
    #[serde(default)]
    pub managed_agent_starts_installed: bool,
    /// The keys of the agents that the suite installs before its first scenario.
    #[serde(default)]
    pub preinstalled: Vec<String>,
    /// A restart of a scenario of this fixture keeps the Junie offer of the IDE start open.
    #[serde(default)]
    pub keeps_junie_startup_offer: bool,
    /// The bridge requests of the scenario preparation, rendered by the generator. Absent in a document that predates
    /// them.
    #[serde(default, deserialize_with = "crate::json::non_null")]
    pub bridge: Option<FixtureBridge>,
}

/// The two bridge requests of the preparation of a scenario of one fixture, as the generator renders them: each one a
/// whole request body, `type` first. A runner sends each one as it is.
#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct FixtureBridge {
    /// `SetScenarioFixture` with the facts of the fixture.
    pub set_scenario_fixture: Box<RawValue>,
    /// `SeedSessionPresets` with the preset of the fixture first, then its additional presets.
    pub seed_session_presets: Box<RawValue>,
}

/// What the state reset of every scenario of a suite sends, as the generator renders it.
#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct SuiteReset {
    /// Every registry flag that a scenario of the suite sets, sorted.
    pub flag_keys: Vec<String>,
    /// The reset forgets the answer to the Junie promo, so the next IDE start offers it.
    pub offer_junie_promo: bool,
    /// The lane value of each registry flag that has one.
    pub lane_flags: BTreeMap<String, String>,
}

/// One Allure label of the generated class of a suite.
#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct SuiteLabel {
    pub name: String,
    pub value: String,
}

/// The ids that a suite runs, by kind.
#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Vocabulary {
    /// The values that the runner answers before the first instruction of a scenario.
    pub runner_answers: Vec<String>,
    pub actions: BTreeMap<String, IdSpec>,
    pub assertions: BTreeMap<String, IdSpec>,
}

/// What a runner needs to know about one id before it sends it.
#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct IdSpec {
    pub side: Side,
    /// How long one use may take: the deadline of the lane, plus one relaunch for each restart.
    pub timeout_ms: u64,
    /// How many times one use restarts the IDE.
    pub restarts: u32,
    /// The values one use answers. A runner keeps each one under its name, as an opaque blob.
    pub answers: Vec<String>,
    /// The values one use answers only on some arguments. An absent answer keeps the old value under its name.
    #[serde(default)]
    pub may_answer: Vec<String>,
    /// The values one use needs. A runner passes the latest answer of each name.
    pub takes: Vec<String>,
    /// The values one use reads only on some arguments, or only when they exist.
    pub may_take: Vec<String>,
    pub params: BTreeMap<String, ParamSpec>,
}

/// The schema of one argument.
#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ParamSpec {
    #[serde(rename = "type")]
    pub kind: ParamType,
    #[serde(default)]
    pub values: Vec<String>,
    pub optional: bool,
}

/// One scenario: its setups, then its flow steps, then its authored program.
#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ScenarioProgram {
    pub name: String,
    /// The key of an authored scenario, unique in its suite. A story scenario has none.
    #[serde(default, deserialize_with = "crate::json::non_null")]
    pub key: Option<String>,
    /// The JUnit factory method of an authored scenario. A story scenario takes `testMethod` of the suite labels.
    #[serde(default, deserialize_with = "crate::json::non_null")]
    pub method: Option<String>,
    pub fixture: String,
    /// The declaring flow of a story scenario.
    #[serde(default, deserialize_with = "crate::json::non_null")]
    pub flow: Option<String>,
    /// The registry flags that the preparation sets, by key.
    #[serde(default)]
    pub flags: BTreeMap<String, String>,
    /// The registry flags that an authored scenario sets itself, so the reset puts them back.
    #[serde(default)]
    pub flag_keys: Vec<String>,
    /// The declared CLI runtimes that an authored scenario drives.
    #[serde(default)]
    pub required_executables: Vec<String>,
    pub setups: Vec<OperationProgram>,
    pub steps: Vec<StepProgram>,
    /// The program of an authored scenario: one instruction for each part or host id. Empty for a story scenario, which
    /// has setups and steps instead.
    #[serde(default)]
    pub program: Vec<Instruction>,
}

/// One flow step of a scenario.
#[derive(Clone, Debug, Deserialize)]
pub struct StepProgram {
    pub flow: String,
    pub step: String,
    pub operations: Vec<OperationProgram>,
}

/// One operation: its ordered instructions.
#[derive(Clone, Debug, Deserialize)]
pub struct OperationProgram {
    pub operation: String,
    pub instructions: Vec<Instruction>,
}

/// One instruction: an action or an assertion, with its arguments.
#[derive(Clone, Debug, Deserialize)]
pub struct Instruction {
    #[serde(default)]
    pub action: Option<String>,
    #[serde(default)]
    pub assertion: Option<String>,
    /// The sentence that an assertion proves.
    #[serde(default, deserialize_with = "crate::json::non_null")]
    pub expectation: Option<String>,
    #[serde(default)]
    pub args: BTreeMap<String, Value>,
    /// The values that one use takes and answers, for an id whose values change per use, such as `authored-step`.
    #[serde(default)]
    pub uses: Option<InstructionUses>,
}

/// The values that one use of an id takes and answers, stated on the instruction.
#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct InstructionUses {
    pub takes: Vec<String>,
    pub answers: Vec<String>,
}

/// The kind of an id. An action and an assertion may share an id, so a kind and an id together name one entry.
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub enum IdKind {
    Action,
    Assertion,
}

impl IdKind {
    /// The kind as a document and a bridge request spell it.
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Action => "action",
            Self::Assertion => "assertion",
        }
    }
}

/// Reads one suite document from its bytes.
pub fn parse_suite_program(content: &[u8]) -> Result<SuiteProgram, String> {
    serde_json::from_slice(content).map_err(|error| format!("is not a suite program: {error}"))
}

/// The scenarios of one suite document, each verbatim as the document holds it: the `program` of the recorder's
/// `scenario` command.
pub fn raw_profiles(content: &[u8]) -> Result<Vec<Box<RawValue>>, String> {
    #[derive(Deserialize)]
    struct Profiles {
        profiles: Vec<Box<RawValue>>,
    }
    serde_json::from_slice::<Profiles>(content)
        .map(|document| document.profiles)
        .map_err(|error| format!("is not a suite program: {error}"))
}

impl Instruction {
    /// The kind and the id of the instruction, or `None` when it is not exactly one action or one assertion.
    pub fn id(&self) -> Option<(IdKind, &str)> {
        match (&self.action, &self.assertion) {
            (Some(id), None) => Some((IdKind::Action, id)),
            (None, Some(id)) => Some((IdKind::Assertion, id)),
            _ => None,
        }
    }

    /// The label that names the instruction in a trace and in a failure: the id, and its arguments in key order, as
    /// `instructionKey` of the generator spells it. A string argument is written without its quotes.
    pub fn label(&self) -> String {
        let id = self.id().map_or("", |(_, id)| id);
        if self.args.is_empty() {
            return id.to_owned();
        }
        let args: Vec<String> = self
            .args
            .iter()
            .map(|(key, value)| match value {
                Value::String(text) => format!("{key}={text}"),
                other => format!("{key}={other}"),
            })
            .collect();
        format!("{id}({})", args.join(","))
    }
}

impl SuiteProgram {
    /// Every way in which this program does not fit its vocabulary, one line each, in program order. Empty for a
    /// program a runner can run.
    pub fn refusals(&self) -> Vec<String> {
        let mut refusals = Vec::new();
        let classes: Vec<&SuiteLabel> = self.labels.iter().filter(|label| label.name == "testClass").collect();
        if !matches!(classes.as_slice(), [class] if class.value.ends_with(&format!(".{}", self.test_class_name))) {
            refusals.push(format!(
                "the labels name the test class {classes:?}, not one class '{}'",
                self.test_class_name
            ));
        }
        let mut used = BTreeSet::new();
        for scenario in &self.profiles {
            let mut answered: BTreeSet<&str> = self.vocabulary.runner_answers.iter().map(String::as_str).collect();
            let operations = scenario
                .setups
                .iter()
                .chain(scenario.steps.iter().flat_map(|step| &step.operations))
                .map(|operation| {
                    (
                        format!("scenario '{}' operation '{}'", scenario.name, operation.operation),
                        &operation.instructions,
                    )
                });
            let program = std::iter::once((format!("scenario '{}' program", scenario.name), &scenario.program));
            for (place, instructions) in operations.chain(program) {
                for instruction in instructions {
                    refusals.extend(self.check_instruction(instruction, &place, &mut used).err());
                    let Some(spec) = self.spec_of(instruction) else { continue };
                    let uses = instruction.uses.as_ref();
                    let takes = spec.takes.iter().chain(uses.into_iter().flat_map(|uses| &uses.takes));
                    for name in takes.filter(|name| !answered.contains(name.as_str())) {
                        refusals.push(format!("{place} takes the value '{name}', which no earlier instruction answers"));
                    }
                    let answers = uses.into_iter().flat_map(|uses| &uses.answers);
                    answered.extend(spec.answers.iter().chain(&spec.may_answer).chain(answers).map(String::as_str));
                }
            }
        }
        for (kind, entries) in [
            (IdKind::Action, &self.vocabulary.actions),
            (IdKind::Assertion, &self.vocabulary.assertions),
        ] {
            for (id, spec) in entries {
                if !used.contains(&(kind, id.clone())) {
                    refusals.push(format!(
                        "the vocabulary states the {} '{id}', which no instruction runs",
                        kind.as_str()
                    ));
                }
                refusals.extend(side_refusal(kind, id, spec.side));
                if spec.timeout_ms == 0 {
                    refusals.push(format!("the {} '{id}' has no deadline", kind.as_str()));
                }
                if spec.restarts > 0 && spec.side == Side::Ide {
                    refusals.push(format!(
                        "the {} '{id}' restarts the IDE, so it cannot run in the IDE alone",
                        kind.as_str()
                    ));
                }
            }
        }
        refusals
    }

    /// The vocabulary entry of one instruction, if it states one.
    pub fn spec_of(&self, instruction: &Instruction) -> Option<&IdSpec> {
        match (&instruction.action, &instruction.assertion) {
            (Some(id), None) => self.vocabulary.actions.get(id),
            (None, Some(id)) => self.vocabulary.assertions.get(id),
            _ => None,
        }
    }

    /// Why one instruction does not fit its vocabulary. A stated id goes into `used` whether its arguments fit or not.
    fn check_instruction(&self, instruction: &Instruction, place: &str, used: &mut BTreeSet<(IdKind, String)>) -> Result<(), String> {
        let (kind, id, entries) = match (&instruction.action, &instruction.assertion) {
            (Some(id), None) => (IdKind::Action, id, &self.vocabulary.actions),
            (None, Some(id)) => (IdKind::Assertion, id, &self.vocabulary.assertions),
            _ => {
                return Err(format!(
                    "{place} has an instruction that is not exactly one action or one assertion"
                ));
            }
        };
        let spec = entries
            .get(id)
            .ok_or_else(|| format!("{place} runs the {} '{id}', which the vocabulary does not state", kind.as_str()))?;
        used.insert((kind, id.clone()));
        for (name, value) in &instruction.args {
            let param = spec
                .params
                .get(name)
                .ok_or_else(|| format!("{place}: the {} '{id}' has no parameter '{name}'", kind.as_str()))?;
            if !param.accepts(value) {
                return Err(format!(
                    "{place}: the {} '{id}' parameter '{name}' does not accept {value}",
                    kind.as_str()
                ));
            }
        }
        if let Some((name, _)) = spec
            .params
            .iter()
            .find(|(name, param)| !param.optional && !instruction.args.contains_key(*name))
        {
            return Err(format!("{place}: the {} '{id}' needs the parameter '{name}'", kind.as_str()));
        }
        Ok(())
    }
}

impl ParamSpec {
    /// Whether `value` is one of this parameter's values.
    pub fn accepts(&self, value: &Value) -> bool {
        match self.kind {
            ParamType::Int => value.as_i64().is_some(),
            ParamType::Boolean => value.is_boolean(),
            ParamType::Enum => value
                .as_str()
                .is_some_and(|text| self.values.iter().any(|candidate| candidate == text)),
        }
    }
}

/// Why `side` is not the side of the id, or nothing.
fn side_refusal(kind: IdKind, id: &str, side: Side) -> Option<String> {
    let host = match kind {
        IdKind::Action => id.parse::<HostAction>().is_ok(),
        IdKind::Assertion => id.parse::<HostAssertion>().is_ok(),
    };
    let kind = kind.as_str();
    match side {
        Side::Host if !host => Some(format!(
            "the {kind} '{id}' is a host id, and the host vocabulary of a runner has no word for it"
        )),
        Side::Ide if host => Some(format!(
            "the {kind} '{id}' is stated to run in the IDE, but a runner knows it as a host id"
        )),
        Side::Host | Side::Ide => None,
    }
}
