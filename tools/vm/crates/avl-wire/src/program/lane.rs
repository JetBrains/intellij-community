//! The lane document of one lane: every launch input of the lane IDE, `lanes/<lane>.json` beside the suite documents.
//! `plugins/air/docs/scripts/flowLanes.ts` writes it.
//!
//! The config key of a lane hashes the bytes of this document, so a runner reads the launch inputs here and nowhere
//! else. The digest of the flow vocabulary is not a launch input: [`crate::program::read_vocabulary_digest`] reads it
//! from the resource root. This side reads every field and refuses an unknown one: a field that a runner does not know is a launch input
//! that it would not apply. A local agent has an `acp.json` entry with the tokens [`JAVA_TOKEN`] and
//! [`FAKE_AGENT_JAR_TOKEN`], which the runner resolves. A controlled agent also gets the address and the credential of
//! the control channel, which derive from the config key and so are not in the document.

use std::collections::{BTreeMap, BTreeSet};

use serde::{Deserialize, Serialize};

#[cfg(test)]
mod tests;

/// The version of the lane document that this reader reads, `LANE_DOCUMENT_SCHEMA_VERSION` of the generator.
pub const LANE_DOCUMENT_SCHEMA_VERSION: u32 = 2;

/// The token for the `java` that runs the fake agent, in an `acp.json` entry.
pub const JAVA_TOKEN: &str = "${java}";

/// The token for the fake agent jar in the home of a context, in an `acp.json` entry.
pub const FAKE_AGENT_JAR_TOKEN: &str = "${fakeAgentJar}";

/// The lane of the live agents, the one lane that states a [`LiveContext`].
pub const LIVE_LANE: &str = "UI_LIVE";

vocabulary! {
    /// When a launch property applies. The IDE runs on the machine that launches it.
    pub enum LaunchCondition {
        Linux = "linux",
        /// A run off TeamCity: `TEAMCITY_VERSION` is unset.
        NotTeamCity = "notTeamCity",
        NotWindows = "notWindows",
    }
}

vocabulary! {
    /// How Air is told that a fake agent exists.
    pub enum Delivery {
        /// One `acp.json` entry.
        Local = "local",
        /// A package of the fake registry.
        Registry = "registry",
        /// The Cloud agent of the product, reached through the emulated Agent Spawner.
        Cloud = "cloud",
    }
}

vocabulary! {
    /// Which process serves the control channel of a controlled agent, `FlowControlOwner` of `flowFixtures.ts`. The
    /// lane IDE serves every controlled agent.
    pub enum ControlOwner {
        /// The control server in the lane IDE, which the launch file `control.json` binds.
        Ide = "IDE",
    }
}

vocabulary! {
    /// A lane fixture that no agent implies, `FlowLaneRequirement` of `flowFixtures.ts`.
    pub enum Requirement {
        AcpTransportCapture = "ACP_TRANSPORT_CAPTURE",
        FeedbackCapture = "FEEDBACK_CAPTURE",
        Workflows = "WORKFLOWS",
    }
}

/// The lane document.
#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct LaneDocument {
    pub schema_version: u32,
    /// The name of the lane, such as `UI` or `GUI_CHAT`.
    pub lane: String,
    /// The `-D` properties beyond the requirements and the services, in launch order.
    pub launch_properties: Vec<LaunchProperty>,
    /// The requirements of the lane, sorted by name.
    pub requirements: Vec<LaneRequirement>,
    /// The fake agents of the lane, in launch order: the order of `acp.json` and of the fake registry.
    pub agents: Vec<LaneAgent>,
    /// The keys of the registry agents, in publish order.
    pub registry_agents: Vec<String>,
    pub disabled_plugin_ids: Vec<String>,
    /// The names of the declared CLI runtimes.
    pub cli_runtimes: Vec<String>,
    /// The identity line of each lane service, by the name of the service.
    pub services: BTreeMap<String, String>,
    /// The live context of [`LIVE_LANE`]. Every other lane leaves the field out.
    #[serde(default)]
    pub live: Option<LiveContext>,
    /// The IJ Perf series of the scenarios of the lane that report metrics. A lane with none leaves the field out.
    #[serde(default)]
    pub performance_series: Vec<PerformanceSeries>,
}

/// The JetBrains Central account that the lane daemon of a Linux guest gives the agent CLIs of the lane IDE.
///
/// The run hands the guest the Central login as the run input [`LiveContext::login_input`]. The guest agent imports it
/// with the declared Central runtime, pins the proxy to [`LiveContext::central_port`] and wires each agent of
/// [`LiveContext::central_agents`]. No field holds a credential.
#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct LiveContext {
    /// The name of the run input that holds the Central login: a file of the run secrets directory of the guest.
    pub login_input: String,
    /// The host command whose output a caller pipes into the run as the login input.
    pub login_source: String,
    /// The declared CLI runtime of the Central CLI. Its launcher in the context is the Central guard, never the CLI.
    pub central_runtime: String,
    /// The port that the configuration of the Central proxy pins.
    pub central_port: u16,
    /// The agents that `central add` wires, in its own spelling and in this order.
    pub central_agents: Vec<String>,
    /// The declared CLI runtimes of the live agents, each with the environment that its launcher exports.
    pub runtimes: Vec<LiveRuntime>,
    /// The unpack root of the project: an absolute guest path outside the context.
    pub project_unpack_root: String,
}

/// One declared CLI runtime of a live agent.
#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct LiveRuntime {
    pub name: String,
    /// The variables that the launcher exports, because the lane IDE inherits no variable.
    pub environment: BTreeMap<String, String>,
}

/// One `-D` property.
#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct LaunchProperty {
    pub key: String,
    pub value: String,
    /// When the property applies. Absent means always.
    #[serde(rename = "when", default, deserialize_with = "crate::json::non_null")]
    pub condition: Option<LaunchCondition>,
}

/// One requirement and the launch properties that it is. A requirement that starts a server has none.
#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct LaneRequirement {
    pub name: Requirement,
    pub properties: BTreeMap<String, String>,
}

/// One fake agent.
#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct LaneAgent {
    pub key: String,
    pub delivery: Delivery,
    /// The `acp.json` entry name of a local agent.
    #[serde(default, deserialize_with = "crate::json::non_null")]
    pub config_name: Option<String>,
    /// The production identity of a registry agent.
    #[serde(default, deserialize_with = "crate::json::non_null")]
    pub registry_id: Option<String>,
    pub display_name: String,
    /// The agent proxies every frame to the control channel of the lane.
    pub controlled: bool,
    /// The process that serves the control channel of a controlled agent.
    #[serde(default, deserialize_with = "crate::json::non_null")]
    pub control_owner: Option<ControlOwner>,
    /// A controlled agent lists the sessions that a body gives it, `listsSessions` of `flowFixtures.ts`. The launch file
    /// of the control server carries it, so the first `initialize` answer of the agent advertises the list.
    #[serde(default)]
    pub lists_sessions: bool,
    /// What a deterministic agent answers.
    #[serde(default, deserialize_with = "crate::json::non_null")]
    pub deterministic: Option<DeterministicAnswer>,
    #[serde(default, deserialize_with = "crate::json::non_null")]
    pub required_executable: Option<String>,
    /// The `acp.json` entry of a local agent.
    #[serde(default, deserialize_with = "crate::json::non_null")]
    pub acp_json: Option<AcpJsonEntry>,
    /// The environment that the fake registry publishes for a registry agent.
    #[serde(default, deserialize_with = "crate::json::non_null")]
    pub env: Option<BTreeMap<String, String>>,
}

/// What a deterministic agent answers: prompt N gets `<response_prefix> N`.
#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct DeterministicAnswer {
    pub response_prefix: String,
    pub response_delay_ms: u64,
    pub asks_permission: bool,
    pub loads_sessions: bool,
}

/// The `acp.json` entry of a local agent.
#[derive(Clone, Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct AcpJsonEntry {
    pub command: String,
    pub args: Vec<String>,
    pub env: BTreeMap<String, String>,
}

/// The series of the IJ Perf dashboard that one scenario reports its metrics to.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct PerformanceSeries {
    /// The `testClassName` of the suite document of the scenario.
    pub test_class_name: String,
    pub scenario: String,
    /// The project of the series on the dashboard, such as `air/many-agents`.
    pub project: String,
    /// The method of the series on the dashboard, such as `<test class FQN>#manyAgents`.
    pub method: String,
    /// The directory of the TeamCity artifacts that holds the file of the series: a relative slash path, such as
    /// `air-long-session/SMALL`.
    pub artifact_path: String,
}

/// The facts of a machine that the conditions of the launch properties read.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct LaunchHost {
    pub linux: bool,
    pub windows: bool,
    pub team_city: bool,
}

impl LaunchCondition {
    /// Whether the condition holds on `host`.
    pub const fn holds(self, host: LaunchHost) -> bool {
        match self {
            Self::Linux => host.linux,
            Self::NotTeamCity => !host.team_city,
            Self::NotWindows => !host.windows,
        }
    }
}

/// Reads one lane document from its bytes, and refuses another schema version and a document that does not fit itself.
pub fn parse_lane_document(content: &[u8]) -> Result<LaneDocument, String> {
    let document: LaneDocument = serde_json::from_slice(content).map_err(|error| format!("is not a lane document: {error}"))?;
    if document.schema_version != LANE_DOCUMENT_SCHEMA_VERSION {
        return Err(format!(
            "has the schema version {}, and this reader reads {LANE_DOCUMENT_SCHEMA_VERSION}",
            document.schema_version
        ));
    }
    let refusals = document.refusals();
    if refusals.is_empty() {
        Ok(document)
    } else {
        Err(refusals.join("; "))
    }
}

impl LaneDocument {
    /// The launch properties that apply on `host`, in document order, then the properties of the requirements.
    pub fn launch_properties_on(&self, host: LaunchHost) -> Vec<(&str, &str)> {
        let mut properties = self.stated_launch_properties_on(host);
        properties.extend(
            self.requirements
                .iter()
                .flat_map(|requirement| requirement.properties.iter().map(|(key, value)| (key.as_str(), value.as_str()))),
        );
        properties
    }

    /// The launch properties that the document states and that apply on `host`, in document order, without the
    /// properties of the requirements: the defaults of every IDE of the lane, which a bare IDE gets too.
    pub fn stated_launch_properties_on(&self, host: LaunchHost) -> Vec<(&str, &str)> {
        self.launch_properties
            .iter()
            .filter(|property| property.condition.is_none_or(|condition| condition.holds(host)))
            .map(|property| (property.key.as_str(), property.value.as_str()))
            .collect()
    }

    /// Every way in which this document does not fit itself, one line each. Empty for a document a runner can launch.
    pub fn refusals(&self) -> Vec<String> {
        let mut refusals = Vec::new();
        let mut keys = BTreeSet::new();
        for agent in &self.agents {
            if !keys.insert(agent.key.as_str()) {
                refusals.push(format!("the agent '{}' is stated twice", agent.key));
            }
            refusals.extend(agent.refusals());
        }
        let registry: Vec<&str> = self
            .agents
            .iter()
            .filter(|agent| agent.delivery == Delivery::Registry)
            .map(|agent| agent.key.as_str())
            .collect();
        if self.registry_agents.iter().map(String::as_str).ne(registry.iter().copied()) {
            refusals.push(format!(
                "the registry agents {:?} are not the registry agents {registry:?} of the agents",
                self.registry_agents
            ));
        }
        if self.agents.iter().filter(|agent| agent.delivery == Delivery::Cloud).count() > 1 {
            refusals.push("the lane states more than one Cloud agent".to_owned());
        }
        let mut properties = BTreeSet::new();
        for (key, _) in self.launch_properties_on(LaunchHost {
            linux: true,
            windows: false,
            team_city: false,
        }) {
            if !properties.insert(key) {
                refusals.push(format!("the launch property '{key}' is stated twice"));
            }
        }
        let mut scenarios = BTreeSet::new();
        let mut artifact_paths = BTreeSet::new();
        for series in &self.performance_series {
            let scenario = format!("{}#{}", series.test_class_name, series.scenario);
            if !scenarios.insert(scenario.clone()) {
                refusals.push(format!("the scenario {scenario} has two performance series"));
            }
            if !artifact_paths.insert(series.artifact_path.as_str()) {
                refusals.push(format!(
                    "the artifact path '{}' is the path of two performance series",
                    series.artifact_path
                ));
            }
            if !is_relative_slash_path(&series.artifact_path) {
                refusals.push(format!(
                    "the performance series of {scenario} has the artifact path '{}', which is not a relative slash path",
                    series.artifact_path
                ));
            }
            if series.project.is_empty() || series.method.is_empty() {
                refusals.push(format!("the performance series of {scenario} names no project or no method"));
            }
        }
        match &self.live {
            Some(live) if self.lane == LIVE_LANE => refusals.extend(live.refusals(&self.cli_runtimes)),
            Some(_) => refusals.push(format!("the lane {} states a live context, which only {LIVE_LANE} has", self.lane)),
            None if self.lane == LIVE_LANE => refusals.push(format!("the lane {LIVE_LANE} states no live context")),
            None => {}
        }
        refusals
    }
}

impl LiveContext {
    /// Every way in which this live context does not fit a lane that declares the CLI runtimes `cli_runtimes`.
    fn refusals(&self, cli_runtimes: &[String]) -> Vec<String> {
        let mut refusals = Vec::new();
        if self.login_input.is_empty() || self.login_input.contains('/') {
            refusals.push(format!("the live login input '{}' is not a file name", self.login_input));
        }
        if self.login_source.is_empty() || self.central_agents.is_empty() {
            refusals.push("the live context names no login source or no Central agent".to_owned());
        }
        let mut names = BTreeSet::new();
        for name in std::iter::once(&self.central_runtime).chain(self.runtimes.iter().map(|runtime| &runtime.name)) {
            if !names.insert(name.as_str()) {
                refusals.push(format!("the live context names the runtime '{name}' twice"));
            }
            if cli_runtimes.contains(name) {
                refusals.push(format!(
                    "the live runtime '{name}' is a declared CLI runtime too, so the lane would get two launchers of it"
                ));
            }
        }
        if !self.project_unpack_root.starts_with('/') || !is_relative_slash_path(&self.project_unpack_root[1..]) {
            refusals.push(format!(
                "the live project unpack root '{}' is not an absolute slash path",
                self.project_unpack_root
            ));
        }
        refusals
    }
}

/// Whether `path` is a relative path of named segments split by `/`: no root, no `.` or `..`, and no backslash.
fn is_relative_slash_path(path: &str) -> bool {
    !path.contains('\\')
        && path
            .split('/')
            .all(|segment| !segment.is_empty() && segment != "." && segment != "..")
}

impl LaneAgent {
    fn refusals(&self) -> Vec<String> {
        let mut refusals = Vec::new();
        let key = &self.key;
        if self.controlled == self.deterministic.is_some() {
            refusals.push(format!("the agent '{key}' must state an answer exactly when it is not controlled"));
        }
        if self.controlled != self.control_owner.is_some() {
            refusals.push(format!(
                "the agent '{key}' must state a control owner exactly when it is controlled"
            ));
        }
        if self.lists_sessions && !self.controlled {
            refusals.push(format!(
                "the agent '{key}' lists sessions, and only a controlled agent lists sessions"
            ));
        }
        let local = self.delivery == Delivery::Local;
        let registry = self.delivery == Delivery::Registry;
        if local != self.config_name.is_some() || local != self.acp_json.is_some() {
            refusals.push(format!(
                "the agent '{key}' must state an acp.json entry and its name exactly when it is local"
            ));
        }
        if registry != self.registry_id.is_some() || registry != self.env.is_some() {
            refusals.push(format!(
                "the agent '{key}' must state a registry id and its environment exactly when the registry delivers it"
            ));
        }
        if self.delivery == Delivery::Cloud && !self.controlled {
            refusals.push(format!("the Cloud agent '{key}' is controlled"));
        }
        if let Some(entry) = &self.acp_json
            && (entry.command != JAVA_TOKEN || entry.args != ["-jar", FAKE_AGENT_JAR_TOKEN])
        {
            refusals.push(format!(
                "the acp.json entry of '{key}' runs {} {:?}, not {JAVA_TOKEN} -jar {FAKE_AGENT_JAR_TOKEN}",
                entry.command, entry.args
            ));
        }
        refusals
    }
}
