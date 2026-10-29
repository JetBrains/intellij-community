//! The owner of the lane IDE: one context and one IDE run per config key, its lane services, and the decision of each
//! iteration to reuse the IDE, to relaunch it on its context, or to launch it on a fresh context.
//!
//! The run supervisor owns the IDE process, so the IDE outlives the lane daemon. A daemon that boots again finds the
//! IDE of a key by the launch record of its context. It keeps that IDE when the record names the same product and the
//! run is live. The decision of an iteration:
//!
//! | the IDE of the key | the decision |
//! | --- | --- |
//! | none, or another product | `fresh`: every IDE run under the IDE root stops, the context is made again |
//! | live, and its bridge answers, with the wanted context plugins | `reuse` |
//! | ended, or its bridge does not answer | `relaunch` on the same context |
//! | live, and only the bridge plugin directory changed | `reload` of the bridge plugin in place |
//! | live, and another context plugin directory changed | `relaunch` on the same context |
//!
//! A reload that fails is a `relaunch`, with the failure as its reason ([`super::reload`]). Each decision is a
//! `decision` record of the run stream.
//!
//! A suite of the launch profile `bare` gets an IDE of its own: the owner quits the IDE of the lane and keeps its record,
//! launches a bare IDE on a fresh context of the launch key of the suite, and holds the record of the lane IDE again
//! when the suite ends. The next run then relaunches the lane IDE on its context.
//!
//! A relaunch keeps the config, system and project directories of the context, which a restart scenario asserts
//! across. A recycle removes the editor tabs that the start would reopen, see [`Relaunch::restores_editors`]. A quit
//! asks the IDE through its bridge and waits for the run to end. It kills the process after a grace, with a thread dump
//! first, and no launch lands beside a process that survives both.

use std::collections::BTreeMap;
use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use avl_bridge::lifecycle::Reader;
use avl_bridge::{Client, url};
use avl_wire::daemon::{IdeExited, IdeLaunched as IdeLaunchedRecord, IdeReady, Output, RunEventKind};
use avl_wire::ide::{self as wire_ide, IdeContext, IdeLaunch, IdePrepared, LaunchRecord, ProjectSource};
use avl_wire::program::lane::{Delivery, LaneDocument, LaunchHost};
use avl_wire::progress::{Decision, Subject};
use avl_wire::supervisor::{Phase, RunState};

use super::context::editors::clear_restored_editors;
use super::context::{LaneContext, ServicePorts};
use super::identity::{BRIDGE_PORT, BRIDGE_TOKEN_PURPOSE, LANE_SERVICES_PURPOSE, LaneIdentity};
use super::inputs::LaunchInputs;
use super::package::Packages;
use super::readiness::{self, Bridge, Ready};
use super::reload::{self, PluginChange};
use super::services::Service;
use super::services::cloud::{self, Cloud, Organization};
use super::services::codex::{self, Codex};
use super::services::feedback::{self, Feedback};
use super::services::registry::{self, Registry, RegistryAgent};
use crate::cli::{CancelArgs, IdeGcArgs, RootArgs, RunArgs};
use crate::clock::now_stamp;
use crate::reply::{AgentRefusal, AgentRefusalExt};
use crate::supervisor::{self, Launcher, LiveSystem};

mod restart;

pub(crate) use restart::{KillTarget, Relaunch, RelaunchPart, RelaunchParts};
use restart::{NoParts, timed};
#[cfg(test)]
mod tests;

/// The refusal code of an IDE that the owner cannot launch, quit or keep.
pub(crate) const CODE: &str = "lane_ide";

/// The plugin directories that every lane IDE context must hold: the bridge and its resident part, which no lane
/// distribution carries. The lane drives the IDE through them.
pub(crate) const REQUIRED_CONTEXT_PLUGINS: [&str; 2] =
    ["air-integrationTests-bridge", "air-integrationTests-bridge-resident"];

/// The manifest of the context plugins in the plugin directory of a context: one `<sha256> <destination>` line per
/// file, in destination order. The resident plugin reads it for the digest of the loaded bridge. A leading dot, so the
/// platform reads no plugin from it.
pub(crate) const MANIFEST_FILE: &str = ".air-context-plugins";

/// The platform property of the internal mode, which every lane IDE runs in, and which a bare IDE leaves out: it
/// measures the start-up that a user pays.
const INTERNAL_MODE_PROPERTY: &str = "idea.is.internal";

/// The launch name of the first run of a context.
const FIRST_LAUNCH: &str = "start";

/// How long a quit that the IDE scheduled gets to end the process, and how long a kill gets after it.
const QUIT_EXIT_WAIT: Duration = Duration::from_mins(1);
const KILL_EXIT_WAIT: Duration = Duration::from_secs(30);

/// How often a wait for the end of a run asks the supervisor.
const EXIT_POLL: Duration = Duration::from_millis(100);

/// How long a reused IDE gets to answer one lifecycle poll before the owner relaunches it.
const REACHABLE_WAIT: Duration = Duration::from_secs(5);

/// The property of the platform that names `idea.properties` of the distribution. The IDE flags file leaves it out.
const IDEA_PROPERTIES_FILE: &str = "idea.properties.file";

/// The property of a dev distribution that names the checkout. The guest has none, so it names the empty home of the
/// launch inputs.
const DEV_PROJECT_ROOT: &str = "idea.dev.project.root";

/// The properties of the bridge: its port, its launch, and the project it drives, `AIR_UI_TEST_*_PROPERTY`.
const BRIDGE_PORT_PROPERTY: &str = "air.ui.test.http.port";
const BRIDGE_LAUNCH_PROPERTY: &str = "air.ui.test.launch.id";
const DRIVEN_PROJECT_PROPERTY: &str = "air.ui.test.driven.project";

/// The property of the fake ACP registry, `air.acp.registry.url`.
const REGISTRY_URL_PROPERTY: &str = "air.acp.registry.url";

/// One file of a context plugin: its destination below the plugin directory, its digest, and the file of its bytes.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct ContextPlugin {
    pub destination: String,
    pub sha256: String,
    pub source: PathBuf,
}

impl ContextPlugin {
    /// The plugin directory of the file: the first component of its destination.
    fn directory(&self) -> &str {
        self.destination.split('/').next().unwrap_or_default()
    }
}

/// The manifest text of `files`, one `<sha256> <destination>` line each in destination order. Empty for no file.
pub(crate) fn manifest(files: &[ContextPlugin]) -> String {
    let mut lines: Vec<String> = files.iter().map(|file| format!("{} {}\n", file.sha256, file.destination)).collect();
    lines.sort_by(|left, right| left.split_once(' ').map(|(_, d)| d).cmp(&right.split_once(' ').map(|(_, d)| d)));
    lines.concat()
}

/// The manifest that `plugins_dir` holds, or empty when it holds none.
pub(crate) fn installed_manifest(plugins_dir: &Path) -> String {
    fs::read_to_string(plugins_dir.join(MANIFEST_FILE)).unwrap_or_default()
}

/// Installs `files` into `plugins_dir`, when its manifest does not name them already.
///
/// The plugins must hold each plugin directory of [`REQUIRED_CONTEXT_PLUGINS`], and the distribution must hold none of
/// their directories, or the platform loads a plugin twice. Each plugin directory is written into a staging directory
/// beside it, and then replaces the old one. A directory that the previous manifest named and `files` does not is
/// removed. The manifest is written last, so an install that fails midway reads as not installed.
pub(crate) fn install_plugins(plugins_dir: &Path, files: &[ContextPlugin], distribution_plugins: &[String]) -> Result<(), AgentRefusal> {
    let directories: std::collections::BTreeSet<&str> = files.iter().map(ContextPlugin::directory).collect();
    let missing: Vec<&str> = REQUIRED_CONTEXT_PLUGINS
        .into_iter()
        .filter(|required| !directories.contains(required))
        .collect();
    if !missing.is_empty() {
        return Err(refused(format!(
            "the context plugins hold no plugin directory {missing:?}; they hold {directories:?}"
        )));
    }
    let doubled: Vec<&str> = directories
        .iter()
        .copied()
        .filter(|directory| distribution_plugins.iter().any(|plugin| plugin == directory))
        .collect();
    if !doubled.is_empty() {
        return Err(refused(format!(
            "the distribution carries plugins/{doubled:?}, and the context plugins carry them too, so the platform would load them twice"
        )));
    }
    let wanted = manifest(files);
    if installed_manifest(plugins_dir) == wanted {
        return Ok(());
    }
    let failure = |what: &str, path: &Path, error: io::Error| failed(format!("cannot {what} {}: {error}", path.display()));
    fs::create_dir_all(plugins_dir).map_err(|error| failure("create", plugins_dir, error))?;
    let manifest_file = plugins_dir.join(MANIFEST_FILE);
    let previous: Vec<String> = installed_manifest(plugins_dir)
        .lines()
        .filter_map(|line| {
            line.split_once(' ')
                .map(|(_, destination)| destination.split('/').next().unwrap_or_default().to_owned())
        })
        .collect();
    remove_if_present(&manifest_file).map_err(|error| failure("delete", &manifest_file, error))?;
    for directory in previous.iter().filter(|directory| !directories.contains(directory.as_str())) {
        let stale = plugins_dir.join(directory);
        remove_tree(&stale).map_err(|error| failure("delete", &stale, error))?;
    }
    for directory in &directories {
        let staging = plugins_dir.join(format!(".{directory}.staging"));
        remove_tree(&staging).map_err(|error| failure("delete", &staging, error))?;
        for file in files.iter().filter(|file| file.directory() == *directory) {
            let target = file
                .destination
                .split('/')
                .skip(1)
                .fold(staging.clone(), |path, component| path.join(component));
            if let Some(parent) = target.parent() {
                fs::create_dir_all(parent).map_err(|error| failure("create", parent, error))?;
            }
            fs::copy(&file.source, &target).map_err(|error| failure("copy the plugin file to", &target, error))?;
        }
        let installed = plugins_dir.join(directory);
        remove_tree(&installed).map_err(|error| failure("delete", &installed, error))?;
        fs::rename(&staging, &installed).map_err(|error| failure("move the staged plugin to", &installed, error))?;
    }
    fs::write(&manifest_file, wanted).map_err(|error| failure("write", &manifest_file, error))
}

/// The lane services of one config key. They bind once for the key and live until the daemon shuts down, or until a
/// run asks for another key, whose ports differ.
pub(crate) struct LaneServices {
    key: String,
    registry: Option<Service<Registry>>,
    cloud: Service<Cloud>,
    feedback: Option<Service<Feedback>>,
    // Held for its listener only: no launch property names it, because `.codex/config.toml` names its fixed port.
    _codex: Option<Service<Codex>>,
}

impl LaneServices {
    /// Starts the services of `context` on `ports`.
    fn start(context: &LaneContext<'_>, ports: ServicePorts) -> Result<Self, AgentRefusal> {
        let identity = context.identity;
        let document = context.document;
        let token = identity.secret(LANE_SERVICES_PURPOSE);
        let registry = match ports.registry {
            Some(port) => {
                let packages = Packages::assemble(context.fake_agent_jar, context.java)?;
                Some(registry::start(port, &token, packages, registry_agents(context))?)
            }
            None => None,
        };
        let organization = if document.agents.iter().any(|agent| agent.delivery == Delivery::Cloud) {
            Organization::CloudWorkspace
        } else {
            Organization::None
        };
        Ok(Self {
            key: identity.key().to_owned(),
            registry,
            cloud: cloud::start(ports.cloud, &token, organization)?,
            feedback: ports.feedback.map(|port| feedback::start(port, &token)).transpose()?,
            _codex: ports.codex.map(|port| codex::start(port, &token)).transpose()?,
        })
    }

    /// The launch properties that point the IDE at these services.
    fn launch_properties(&self) -> Vec<(&'static str, String)> {
        let mut properties = Vec::new();
        if let Some(registry) = &self.registry {
            properties.push((REGISTRY_URL_PROPERTY, format!("{}/registry.json", registry.url())));
        }
        if let Some(feedback) = &self.feedback {
            properties.extend(feedback.handler().launch_properties());
        }
        properties.extend(self.cloud.handler().launch_properties());
        properties
    }
}

/// The registry agents of the lane in publish order, each with its environment and, for a controlled agent, the
/// control environment of its owner.
fn registry_agents(context: &LaneContext<'_>) -> Vec<RegistryAgent> {
    let document = context.document;
    document
        .registry_agents
        .iter()
        .filter_map(|key| document.agents.iter().find(|agent| agent.key == *key))
        .filter_map(|agent| {
            let mut environment: Vec<(String, String)> = agent.env.clone().unwrap_or_default().into_iter().collect();
            if agent.controlled {
                environment.extend(
                    context
                        .identity
                        .control_environment(&agent.key)
                        .map(|(name, value)| (name.to_owned(), value)),
                );
            }
            Some(RegistryAgent {
                registry_id: agent.registry_id.clone()?,
                display_name: agent.display_name.clone(),
                environment,
            })
        })
        .collect()
}

/// Where the owner writes what it did: the `ideLaunched` and `ideExited` records, and each line a person reads.
pub(crate) struct Records<'a>(pub &'a mut dyn FnMut(RunEventKind));

impl Records<'_> {
    pub(crate) fn emit(&mut self, record: RunEventKind) {
        (self.0)(record);
    }

    pub(crate) fn note(&mut self, text: String) {
        (self.0)(RunEventKind::Output(Output { text }));
    }

    /// Writes `decision` as a `decision` record about the IDE, and its line into the run log of the daemon.
    pub(crate) fn decide(&mut self, decision: &IdeDecision) {
        let record = Decision {
            subject: Subject::Ide,
            action: decision.action.as_str().to_owned(),
            reason: Some(decision.reason.clone()),
        };
        super::channel::log(&record.line());
        (self.0)(RunEventKind::Decision(record));
    }
}

/// What one iteration asks of the IDE: the lane, its vocabulary, its identity, and the context plugins.
pub(crate) struct LaneLaunch<'a> {
    pub document: &'a LaneDocument,
    /// The digest of the flow vocabulary of the run, which the bridge of the IDE must serve.
    pub vocabulary_digest: &'a str,
    pub identity: &'a LaneIdentity,
    pub product_stamp: &'a str,
    pub fake_agent_jar: &'a Path,
    pub plugins: &'a [ContextPlugin],
    /// The live run of the iteration, for the live lane alone. The context writer seeds the lane home with it.
    pub live: Option<&'a super::live::LiveRun>,
}

/// The IDE that the owner holds.
#[derive(Clone, Debug)]
struct HeldIde {
    key: String,
    /// The token of the bridge of the IDE, which a quit sends.
    token: String,
    paths: IdePrepared,
    launch_name: String,
    pid: Option<i32>,
    relaunches: u32,
    /// Whether the end of this run was reported, so each run reports its end once.
    exit_reported: bool,
    /// Whether this is the IDE of a suite of the launch profile `bare`.
    bare: bool,
}

impl HeldIde {
    fn run_id(&self) -> String {
        wire_ide::ide_run_id(&self.launch_name).unwrap_or_default()
    }

    fn context(&self) -> &Path {
        Path::new(&self.paths.context_dir)
    }
}

/// What the owner does to reach a ready IDE.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum IdeAction {
    Reuse,
    /// The bridge plugin reloads in the running IDE, and the IDE keeps its process.
    Reload,
    Relaunch,
    Fresh,
}

impl IdeAction {
    /// The word of the action, as the run log spells it.
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::Reuse => "reuse",
            Self::Reload => "reload",
            Self::Relaunch => "relaunch",
            Self::Fresh => "fresh",
        }
    }
}

/// What the owner did to reach a ready IDE, and why.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct IdeDecision {
    pub action: IdeAction,
    pub reason: String,
}

/// The owner of the lane IDE and of its lane services.
pub(crate) struct IdeOwner {
    inputs: LaunchInputs,
    services: Option<LaneServices>,
    held: Option<HeldIde>,
    /// The record of the lane IDE that a bare launch quit, until the bare suite ends.
    parked: Option<HeldIde>,
    /// The held run as the target of a kill of the watchdog, see [`KillTarget`].
    target: KillTarget,
}

impl IdeOwner {
    pub(crate) fn new(inputs: LaunchInputs) -> Self {
        Self {
            inputs,
            services: None,
            held: None,
            parked: None,
            target: KillTarget::default(),
        }
    }

    /// The declared CLI runtimes that `names` name, in that order.
    pub(crate) fn runtimes_named(&self, names: &[String]) -> Result<Vec<super::context::CliRuntime>, AgentRefusal> {
        self.inputs.runtimes_named(names)
    }

    /// Every declared CLI runtime of the runtime descriptor.
    pub(crate) fn runtimes(&self) -> &[super::context::CliRuntime] {
        &self.inputs.runtimes
    }

    /// The paths of the context of the held IDE, or `None` when the owner holds none.
    pub(crate) fn held_paths(&self) -> Option<IdePrepared> {
        self.held.as_ref().map(|held| held.paths.clone())
    }

    /// Whether the held IDE run is live now.
    pub(crate) fn is_running(&self) -> bool {
        self.held.as_ref().is_some_and(|held| live_state(held).is_some())
    }

    /// Makes the IDE of `launch` ready: decides, launches when the decision asks for it, and waits for readiness.
    /// `records` gets the `ideLaunched` and `ideExited` records, and each line of what the owner did.
    pub(crate) fn ensure(&mut self, launch: &LaneLaunch<'_>, records: &mut Records<'_>) -> Result<IdeDecision, AgentRefusal> {
        let key = launch.identity.key().to_owned();
        let runtimes = self.inputs.runtimes_named(&launch.document.cli_runtimes)?;
        let java = self.inputs.java();
        let context = LaneContext {
            document: launch.document,
            identity: launch.identity,
            java: &java,
            fake_agent_jar: launch.fake_agent_jar,
            runtimes: &runtimes,
            live: launch.live,
        };
        let ports = context.check()?;
        if let Some(other) = self.held.as_ref().map(|held| held.key.clone()).filter(|held| *held != key) {
            records.note(format!("the run asks for '{key}', and the IDE runs '{other}'; quitting it"));
            self.quit(records)?;
        }
        if self.services.as_ref().is_none_or(|services| services.key != key) {
            // The Codex model has one fixed port, so the services of the previous key close first.
            self.services = None;
            self.services = Some(LaneServices::start(&context, ports)?);
            let port = |port: Option<u16>| port.map_or_else(|| "none".to_owned(), |port| port.to_string());
            records.note(format!(
                "the lane services of '{key}' listen: registry {}, cloud {}, feedback {}, codex {}",
                port(ports.registry),
                ports.cloud,
                port(ports.feedback),
                port(ports.codex)
            ));
        }
        if self.held.is_none()
            && let Some(adopted) = self.adopt(&key, launch.product_stamp)
        {
            records.note(format!(
                "the IDE of '{key}' outlived the previous daemon as pid {}; adopting it",
                adopted.pid.map_or_else(|| "unknown".to_owned(), |pid| pid.to_string())
            ));
            emit_launched(&adopted, records);
            self.held = Some(adopted);
            self.publish_target();
        }
        let decision = self.decide(launch, &key);
        records.decide(&decision);
        let bridge = Bridge::new(BRIDGE_PORT, &launch.identity.secret(BRIDGE_TOKEN_PURPOSE))?;
        let decision = match decision.action {
            IdeAction::Reuse => decision,
            IdeAction::Relaunch => {
                self.relaunch(launch, Relaunch::Recycle, records)?;
                decision
            }
            IdeAction::Fresh => {
                self.launch_fresh(launch, &context, records)?;
                decision
            }
            IdeAction::Reload => match self.reload(launch, &bridge, records) {
                Ok(()) => return Ok(decision),
                Err(reason) => {
                    let fallback = IdeDecision {
                        action: IdeAction::Relaunch,
                        reason,
                    };
                    records.decide(&fallback);
                    self.relaunch(launch, Relaunch::Recycle, records)?;
                    fallback
                }
            },
        };
        records.note(format!(
            "waiting up to {} s for the IDE of '{key}' to get ready",
            readiness::READY_TIMEOUT.as_secs()
        ));
        let ready = readiness::await_lifecycle_ready(&bridge, &key, launch.vocabulary_digest, &mut self.run_end(), &mut |text| {
            records.note(text);
        })?;
        self.emit_ready(ready, records);
        readiness::ping(&bridge)?;
        Ok(decision)
    }

    /// The `ideReady` record of the held IDE, which got `ready`.
    pub(super) fn emit_ready(&self, ready: Ready, records: &mut Records<'_>) {
        let Some(held) = &self.held else { return };
        records.emit(RunEventKind::IdeReady(IdeReady {
            timestamp: now_stamp(),
            pid: ready.pid,
            launch_name: held.launch_name.clone(),
            launch_key: held.key.clone(),
            ready_after_ms: i64::try_from(ready.after.as_millis()).unwrap_or(i64::MAX),
        }));
    }

    /// Reloads the bridge plugin of the held IDE from the context plugins of `launch`, and waits until the new copy is
    /// ready. The error is the reason of the relaunch that follows, and the staged copy is gone then.
    fn reload(&self, launch: &LaneLaunch<'_>, bridge: &Bridge, records: &mut Records<'_>) -> Result<(), String> {
        let Some(held) = &self.held else {
            return Err("a reload was decided without an IDE".to_owned());
        };
        let started = Instant::now();
        let key = launch.identity.key();
        let reloaded = Client::new(&url(BRIDGE_PORT), &launch.identity.secret(BRIDGE_TOKEN_PURPOSE))
            .map_err(|error| error.to_string())
            .and_then(|client| reload::reload(&client, key, held.context(), launch.plugins, &mut |text| records.note(text)))
            .and_then(|()| {
                let not_ready = |refusal: AgentRefusal| format!("the reloaded bridge plugin did not get ready: {}", refusal.message);
                let ready = readiness::await_lifecycle_ready(bridge, key, launch.vocabulary_digest, &mut self.run_end(), &mut |text| {
                    records.note(text);
                })
                .map_err(not_ready)?;
                self.emit_ready(ready, records);
                readiness::ping(bridge).map_err(not_ready)
            });
        match &reloaded {
            Ok(()) => records.note(format!(
                "reload: the bridge plugin of '{key}' reloaded in {} ms, and the IDE keeps pid {}",
                started.elapsed().as_millis(),
                held.pid.map_or_else(|| "unknown".to_owned(), |pid| pid.to_string())
            )),
            Err(reason) => {
                records.note(format!(
                    "reload: the bridge plugin of '{key}' did not reload after {} ms: {reason}",
                    started.elapsed().as_millis()
                ));
                if let Err(error) = reload::clear_staging(held.context()) {
                    records.note(format!("reload: cannot remove the staged copy: {error}"));
                }
            }
        }
        reloaded
    }

    /// The decision for the held IDE, after a possible adoption. It reads the run, the bridge and the plugin manifest.
    fn decide(&self, launch: &LaneLaunch<'_>, key: &str) -> IdeDecision {
        let decision = |action: IdeAction, reason: String| IdeDecision { action, reason };
        let Some(held) = &self.held else {
            return decision(IdeAction::Fresh, format!("no IDE of '{key}' runs for this product"));
        };
        if live_state(held).is_none() {
            return decision(IdeAction::Relaunch, format!("the IDE of '{key}' exited"));
        }
        if !bridge_answers(launch.identity, key) {
            return decision(
                IdeAction::Relaunch,
                format!("the IDE of '{key}' is alive, and its bridge does not answer"),
            );
        }
        match reload::plugin_change(&installed_manifest(Path::new(&held.paths.plugins_dir)), launch.plugins) {
            PluginChange::Same => {}
            PluginChange::Bridge { installed, wanted } => {
                return decision(
                    IdeAction::Reload,
                    format!(
                        "only the bridge plugin changed, from {} to {}",
                        reload::short(&installed),
                        reload::short(&wanted)
                    ),
                );
            }
            PluginChange::Others(directories) => {
                return decision(
                    IdeAction::Relaunch,
                    format!("the context plugin directories {} changed", directories.join(", ")),
                );
            }
        }
        decision(
            IdeAction::Reuse,
            format!(
                "the IDE of '{key}' runs as pid {}",
                held.pid.map_or_else(|| "unknown".to_owned(), |pid| pid.to_string())
            ),
        )
    }

    /// The IDE of `key` that outlived the previous daemon: its launch record names `key` and `product_stamp`, and its
    /// run is live.
    fn adopt(&self, key: &str, product_stamp: &str) -> Option<HeldIde> {
        let context = self.inputs.ide_root.join(key);
        let record: LaunchRecord = wire_ide::decode_launch_record(&fs::read(context.join(wire_ide::LAUNCH_RECORD_FILE)).ok()?).ok()?;
        if record.launch_key != key || product_stamp.is_empty() || record.product_digest != product_stamp {
            return None;
        }
        let held = HeldIde {
            key: key.to_owned(),
            token: LaneIdentity::new(key, 0).secret(BRIDGE_TOKEN_PURPOSE),
            paths: self.layout(&context),
            launch_name: record.launch_name,
            pid: None,
            relaunches: 0,
            exit_reported: false,
            bare: false,
        };
        let state = live_state(&held)?;
        Some(HeldIde { pid: state.pid, ..held })
    }

    /// The paths of the context `context`, by the layout of a preparation.
    fn layout(&self, context: &Path) -> IdePrepared {
        let child = |name: &str| context.join(name).display().to_string();
        let unpack = context.join(wire_ide::PROJECT_DIR);
        let project = if self.inputs.project_root.is_empty() {
            unpack
        } else {
            self.inputs.project_root.split('/').fold(unpack, |path, part| path.join(part))
        };
        IdePrepared {
            context_dir: context.display().to_string(),
            config_dir: child(wire_ide::CONFIG_DIR),
            system_dir: child(wire_ide::SYSTEM_DIR),
            plugins_dir: child(wire_ide::PLUGINS_DIR),
            project_dir: project.display().to_string(),
            home_dir: child(wire_ide::HOME_DIR),
            bin_dir: child(wire_ide::BIN_DIR),
        }
    }

    /// Stops every IDE run under the IDE root, makes the context of the key again, writes its files and plugins, and
    /// launches the IDE as `start-<millis>`.
    fn launch_fresh(&mut self, launch: &LaneLaunch<'_>, context: &LaneContext<'_>, records: &mut Records<'_>) -> Result<(), AgentRefusal> {
        if self.held.is_some() {
            self.quit(records)?;
        }
        self.stop_every_run(records)?;
        records.note(format!(
            "preparing a fresh context of '{}' under {}",
            launch.identity.key(),
            self.inputs.ide_root.display()
        ));
        let paths = crate::ide::prepare::prepare(
            &LiveSystem,
            &fresh_context(&self.inputs, launch.identity.key(), launch.document, false),
        )?;
        context.write(&paths)?;
        let held = HeldIde {
            key: launch.identity.key().to_owned(),
            token: launch.identity.secret(BRIDGE_TOKEN_PURPOSE),
            paths,
            launch_name: launch_name(FIRST_LAUNCH),
            pid: None,
            relaunches: 0,
            exit_reported: false,
            bare: false,
        };
        self.start_run(held, launch, records)
    }

    /// Stops every IDE run under the IDE root before a launch on a fresh context, so no IDE of an earlier daemon holds
    /// the bridge port. The contexts stay.
    fn stop_every_run(&self, records: &mut Records<'_>) -> Result<(), AgentRefusal> {
        let stopped = crate::ide::gc::collect(
            &LiveSystem,
            &IdeGcArgs {
                root: self.inputs.ide_root.clone(),
                stop_all: true,
                keep_product: None,
                keep_logs: 5,
                grace_ms: 10_000,
            },
        )?;
        for run in &stopped.stopped {
            records.note(format!(
                "cancelled the IDE run {} of '{}' before a new context",
                run.run_id, run.launch_key
            ));
        }
        Ok(())
    }

    /// Quits the held IDE and launches it again on its context. Between the two, [`before_relaunch`] removes the restored
    /// editor tabs of a recycle.
    fn relaunch(&mut self, launch: &LaneLaunch<'_>, cause: Relaunch, records: &mut Records<'_>) -> Result<(), AgentRefusal> {
        let Some(previous) = self.held.clone() else {
            return Err(AgentRefusal::internal("a relaunch was decided without an IDE"));
        };
        self.quit(records)?;
        before_relaunch(cause, Path::new(&previous.paths.config_dir), records)?;
        self.launch_again(previous, launch, cause, records)
    }

    /// Launches the IDE of the run `previous`, which ended, again on its context as `<cause>-<n>-<millis>`. The count `n`
    /// counts every relaunch of the context.
    fn launch_again(
        &mut self,
        previous: HeldIde,
        launch: &LaneLaunch<'_>,
        cause: Relaunch,
        records: &mut Records<'_>,
    ) -> Result<(), AgentRefusal> {
        let relaunches = previous.relaunches + 1;
        let held = HeldIde {
            launch_name: launch_name(&format!("{}-{relaunches}", cause.as_str())),
            pid: None,
            relaunches,
            exit_reported: false,
            ..previous
        };
        self.start_run(held, launch, records)
    }

    /// Installs the plugins of `launch` into the context of `held` and starts its run.
    fn start_run(&mut self, mut held: HeldIde, launch: &LaneLaunch<'_>, records: &mut Records<'_>) -> Result<(), AgentRefusal> {
        supervisor::reap_ended();
        let distribution: Vec<String> = self.inputs.distribution_plugins.iter().cloned().collect();
        install_plugins(Path::new(&held.paths.plugins_dir), launch.plugins, &distribution)?;
        let properties = self.launch_properties(launch, &held)?;
        let launched = crate::ide::launch::launch(
            &LiveSystem,
            &Launcher::current(),
            &crate::ide::launch::Guest::current(),
            &IdeLaunch {
                schema_version: wire_ide::SCHEMA_VERSION,
                ide_root: self.inputs.ide_root.display().to_string(),
                launch_key: held.key.clone(),
                dist_home: self.inputs.dist_home.display().to_string(),
                ide_config: self.inputs.ide_config.display().to_string(),
                java_home: self.inputs.java_home.display().to_string(),
                flags_file: self.inputs.flags_file.display().to_string(),
                properties,
                project_dir: held.paths.project_dir.clone(),
                launch_name: held.launch_name.clone(),
                product_digest: launch.product_stamp.to_owned(),
            },
        )?;
        held.pid = launched.run.pid;
        records.emit(RunEventKind::IdeLaunched(IdeLaunchedRecord {
            timestamp: Some(now_stamp()),
            pid: i64::from(held.pid.unwrap_or_default()),
            launch_name: held.launch_name.clone(),
            launch_key: held.key.clone(),
            log_dir: launched.log_dir,
        }));
        self.held = Some(held);
        self.publish_target();
        Ok(())
    }

    /// The `-D` properties of the launch of `held`. A later property wins, in this order: the paths of the launch, the
    /// launch properties of the lane document, the properties of the lane services, and the bridge. A bare IDE gets
    /// the launch properties of the lane document without the internal mode, and no property of a lane service.
    fn launch_properties(&self, launch: &LaneLaunch<'_>, held: &HeldIde) -> Result<BTreeMap<String, String>, AgentRefusal> {
        let services = if held.bare {
            None
        } else {
            Some(
                self.services
                    .as_ref()
                    .ok_or_else(|| AgentRefusal::internal("a launch without the lane services"))?,
            )
        };
        let paths = &held.paths;
        let project = real_path(Path::new(&paths.project_dir))?;
        let host = LaunchHost {
            linux: cfg!(target_os = "linux"),
            windows: false,
            team_city: std::env::var_os("TEAMCITY_VERSION").is_some(),
        };
        let mut properties = BTreeMap::new();
        let mut put = |key: &str, value: String| {
            properties.insert(key.to_owned(), value);
        };
        put(
            IDEA_PROPERTIES_FILE,
            self.inputs.dist_home.join("bin").join("idea.properties").display().to_string(),
        );
        put(DEV_PROJECT_ROOT, self.inputs.home.display().to_string());
        put("user.home", paths.home_dir.clone());
        put("java.util.prefs.userRoot", paths.home_dir.clone());
        put(DRIVEN_PROJECT_PROPERTY, project.display().to_string());
        for (key, value) in document_properties(launch.document, host, held.bare) {
            put(key, value.to_owned());
        }
        for (key, value) in services.into_iter().flat_map(LaneServices::launch_properties) {
            put(key, value);
        }
        put(BRIDGE_PORT_PROPERTY, BRIDGE_PORT.to_string());
        put(BRIDGE_LAUNCH_PROPERTY, held.key.clone());
        Ok(properties)
    }

    /// Launches a bare IDE for a suite of the launch profile `bare`, and waits until it is ready.
    ///
    /// The owner quits the IDE of the lane and keeps its record until [`IdeOwner::end_bare`]. It stops every other IDE
    /// run under the IDE root, and prepares a fresh context of `key` with the option files and the token of the bridge
    /// alone, installs the context
    /// plugins of `launch`, and launches the IDE with the launch properties of the lane document without the internal
    /// mode. The context disables no plugin, as [`fresh_context`] says. The readiness is the lifecycle and the
    /// vocabulary only, so the IDE gets no request before the first instruction of its suite.
    pub(crate) fn launch_bare(&mut self, launch: &LaneLaunch<'_>, key: &str, records: &mut Records<'_>) -> Result<(), AgentRefusal> {
        if key == launch.identity.key() {
            return Err(refused(format!(
                "the bare launch key '{key}' is the key of the lane, and a bare launch makes its context again"
            )));
        }
        if let Some(lane) = self.held.clone().filter(|held| !held.bare) {
            records.note(format!(
                "a suite of a bare IDE quits the IDE of '{}', and the next run launches it again on its context",
                lane.key
            ));
            self.quit(records)?;
            self.parked = Some(HeldIde {
                exit_reported: true,
                ..lane
            });
        } else {
            self.quit(records)?;
        }
        self.stop_every_run(records)?;
        records.note(format!(
            "preparing a fresh context of '{key}' under {}",
            self.inputs.ide_root.display()
        ));
        let paths = crate::ide::prepare::prepare(&LiveSystem, &fresh_context(&self.inputs, key, launch.document, true))?;
        // The bare IDE serves the token of the lane, so the bridge of the run reaches both IDEs.
        let token = launch.identity.secret(BRIDGE_TOKEN_PURPOSE);
        super::context::write_bare(&paths, &token)?;
        let held = HeldIde {
            key: key.to_owned(),
            token: token.clone(),
            paths,
            launch_name: launch_name(FIRST_LAUNCH),
            pid: None,
            relaunches: 0,
            exit_reported: false,
            bare: true,
        };
        self.start_run(held, launch, records)?;
        records.note(format!(
            "waiting up to {} s for the bare IDE of '{key}' to get ready",
            readiness::READY_TIMEOUT.as_secs()
        ));
        let bridge = Bridge::new(BRIDGE_PORT, &token)?;
        let ready = readiness::await_lifecycle_ready(&bridge, key, launch.vocabulary_digest, &mut self.run_end(), &mut |text| {
            records.note(text);
        })?;
        self.emit_ready(ready, records);
        Ok(())
    }

    /// The check of a readiness wait on the held IDE run: how the run ended, once it did. A run that the owner does not
    /// hold, or whose state cannot be read, has not ended, so a slow read of the supervisor ends no wait.
    pub(super) fn run_end(&self) -> impl FnMut() -> Option<String> + use<> {
        let run = self.held.as_ref().map(|held| (held.context().to_path_buf(), held.run_id()));
        move || {
            let (context, run_id) = run.as_ref()?;
            let state = supervisor::status(&LiveSystem, context, run_id).ok()?;
            (state.phase == Phase::Finished).then(|| {
                let exit = state.exit_code.map_or_else(String::new, |code| format!(" with {code}"));
                format!("its run {run_id} ended{exit}")
            })
        }
    }

    /// Quits the bare IDE, and holds the record of the IDE of the lane again, so the next run relaunches it on its
    /// context.
    pub(crate) fn end_bare(&mut self, records: &mut Records<'_>) -> Result<(), AgentRefusal> {
        if self.held.as_ref().is_some_and(|held| held.bare) {
            self.quit(records)?;
        }
        if self.held.is_none() {
            self.held = self.parked.take();
        }
        Ok(())
    }

    /// Quits the held IDE and forgets it: the bridge asks the IDE to quit, and the owner waits for the run to end. A run
    /// that does not end gets a kill with a thread dump. A process that survives both is a refusal, and the owner keeps
    /// it, so no launch lands beside it.
    ///
    /// `/ide/stop` asks for it, so the next run makes a fresh context.
    pub(crate) fn quit(&mut self, records: &mut Records<'_>) -> Result<(), AgentRefusal> {
        self.quit_in_parts(records, &mut NoParts)
    }

    /// [`IdeOwner::quit`], and `parts` gets the begin and the end of the quit request, the wait for the exit, and a kill.
    fn quit_in_parts(&mut self, records: &mut Records<'_>, parts: &mut dyn RelaunchParts) -> Result<(), AgentRefusal> {
        let Some(mut held) = self.held.take() else {
            self.publish_target();
            return Ok(());
        };
        let result = self.end_run(&mut held, records, parts);
        if result.is_err() {
            self.held = Some(held);
        }
        self.publish_target();
        result
    }

    /// Ends the run of `held`. A refused quit request and a run that does not end after it fail no part, because the
    /// kill comes next. A run that survives the kill fails the kill.
    fn end_run(&self, held: &mut HeldIde, records: &mut Records<'_>, parts: &mut dyn RelaunchParts) -> Result<(), AgentRefusal> {
        if live_state(held).is_some() {
            let pid = held.pid.map_or_else(|| "unknown".to_owned(), |pid| pid.to_string());
            records.note(format!("quitting the IDE pid {pid} of '{}'", held.key));
            parts.begin(RelaunchPart::QuitRequest);
            let asked = Bridge::new(BRIDGE_PORT, &held.token).and_then(|bridge| readiness::quit(&bridge));
            parts.end(RelaunchPart::QuitRequest, None);
            if let Err(refusal) = asked {
                records.note(format!("the IDE pid {pid} did not quit through its bridge: {}", refusal.message));
            }
            parts.begin(RelaunchPart::QuitExit);
            let ended = await_end(held, QUIT_EXIT_WAIT);
            parts.end(RelaunchPart::QuitExit, None);
            if ended.is_none() {
                records.note(format!(
                    "the IDE pid {pid} is still alive {}s after the quit; killing it",
                    QUIT_EXIT_WAIT.as_secs()
                ));
                timed(parts, RelaunchPart::QuitKill, || {
                    supervisor::cancel(
                        &LiveSystem,
                        &CancelArgs {
                            run: RunArgs {
                                root: RootArgs {
                                    root: held.context().to_path_buf(),
                                },
                                run_id: held.run_id(),
                            },
                            grace_ms: 0,
                            thread_dump: self.inputs.jcmd(),
                        },
                    )?;
                    match await_end(held, KILL_EXIT_WAIT) {
                        Some(_) => Ok(()),
                        None => Err(refused(format!(
                            "the IDE pid {pid} of '{}' survived a quit and a kill; a launch beside it would contend for \
                             the fixed bridge port",
                            held.key
                        ))),
                    }
                })?;
            }
        }
        if !held.exit_reported {
            held.exit_reported = true;
            if let Ok(state) = supervisor::status(&LiveSystem, held.context(), &held.run_id()) {
                records.emit(exited(held, &state));
            }
        }
        // The supervisor of the run ended with it, and this process is its parent, so it reaps it now.
        supervisor::reap_ended();
        Ok(())
    }

    /// Stops the lane services. The IDE keeps running: it outlives the daemon.
    pub(crate) fn close_services(&mut self) {
        self.services = None;
    }
}

/// The request of a fresh context of `key`. The IDE of the lane disables the plugins of the lane `document`. A bare IDE
/// disables none: it is the IDE of a user, so it keeps the plugin set of the product, AI Assistant included.
pub(crate) fn fresh_context(inputs: &LaunchInputs, key: &str, document: &LaneDocument, bare: bool) -> IdeContext {
    IdeContext {
        schema_version: wire_ide::SCHEMA_VERSION,
        ide_root: inputs.ide_root.display().to_string(),
        launch_key: key.to_owned(),
        fresh: true,
        // The project of a live lane unpacks outside the context, below no agent instruction or configuration.
        project: ProjectSource {
            archive: inputs.project_archive.display().to_string(),
            archive_root: inputs.project_root.clone(),
            relocate_to: if bare {
                None
            } else {
                document.live.as_ref().map(|live| live.project_unpack_root.clone())
            },
        },
        disabled_plugin_ids: if bare { Vec::new() } else { document.disabled_plugin_ids.clone() },
    }
}

/// The launch properties of `document` on `host`. The IDE of a lane gets the stated properties and those of the
/// requirements. A bare IDE gets the stated properties without the internal mode, as the IDE of a user starts.
pub(crate) fn document_properties(document: &LaneDocument, host: LaunchHost, bare: bool) -> Vec<(&str, &str)> {
    if !bare {
        return document.launch_properties_on(host);
    }
    document
        .stated_launch_properties_on(host)
        .into_iter()
        .filter(|(key, _)| *key != INTERNAL_MODE_PROPERTY)
        .collect()
}

/// What a relaunch of `cause` does to the context with the config directory `config` while no IDE runs on it. A cause
/// that restores no editor removes the editor tabs that the start would reopen, and `records` gets one line for each
/// changed file.
fn before_relaunch(cause: Relaunch, config: &Path, records: &mut Records<'_>) -> Result<(), AgentRefusal> {
    if cause.restores_editors() {
        return Ok(());
    }
    let cleared = clear_restored_editors(config).map_err(|error| {
        failed(format!(
            "cannot remove the restored editor tabs below {}: {error}",
            config.display()
        ))
    })?;
    for file in cleared {
        records.note(file.note());
    }
    Ok(())
}

/// The `ideLaunched` record of an adopted IDE, which keeps the pid that an earlier record named. An earlier daemon
/// launched it, so the record has no time.
fn emit_launched(held: &HeldIde, records: &mut Records<'_>) {
    records.emit(RunEventKind::IdeLaunched(IdeLaunchedRecord {
        timestamp: None,
        pid: i64::from(held.pid.unwrap_or_default()),
        launch_name: held.launch_name.clone(),
        launch_key: held.key.clone(),
        log_dir: Path::new(&held.paths.context_dir)
            .join(wire_ide::LOG_DIR)
            .join(&held.launch_name)
            .display()
            .to_string(),
    }));
}

/// The `ideExited` record of the ended run `state`.
fn exited(held: &HeldIde, state: &RunState) -> RunEventKind {
    RunEventKind::IdeExited(IdeExited {
        timestamp: Some(now_stamp()),
        pid: i64::from(state.pid.or(held.pid).unwrap_or_default()),
        exit_code: state.exit_code.unwrap_or(-1),
        signal: state.signal.clone().flatten(),
    })
}

/// The state of the run of `held` while it is live, or `None` once it ended or cannot be read.
fn live_state(held: &HeldIde) -> Option<RunState> {
    let state = supervisor::status(&LiveSystem, held.context(), &held.run_id()).ok()?;
    (state.phase != Phase::Finished).then_some(state)
}

/// Waits up to `within` for the run of `held` to end, and answers its ended state, or `None` at the deadline.
fn await_end(held: &HeldIde, within: Duration) -> Option<RunState> {
    let deadline = Instant::now() + within;
    loop {
        match supervisor::status(&LiveSystem, held.context(), &held.run_id()) {
            Ok(state) if state.phase == Phase::Finished => return Some(state),
            Err(_) => return None,
            Ok(_) => {}
        }
        if Instant::now() >= deadline {
            return None;
        }
        std::thread::sleep(EXIT_POLL);
    }
}

/// Whether the bridge of the IDE of `key` answers one lifecycle poll within [`REACHABLE_WAIT`].
fn bridge_answers(identity: &LaneIdentity, key: &str) -> bool {
    let Ok(client) = Client::new(&url(BRIDGE_PORT), &identity.secret(BRIDGE_TOKEN_PURPOSE)) else {
        return false;
    };
    let reader = Reader::new(client, Some(key));
    let deadline = Instant::now() + REACHABLE_WAIT;
    while Instant::now() < deadline {
        match reader.poll(None, Duration::ZERO) {
            Ok(snapshot) => return snapshot.state.launch.as_deref().is_none_or(|launch| launch == key),
            Err(error) if error.is_transient() => std::thread::sleep(Duration::from_millis(250)),
            Err(_) => return false,
        }
    }
    false
}

/// `<base>-<milliseconds since the epoch in base 36>`, a launch name that no earlier run of a context has: the run
/// directory of a launch outlives its run, and the supervisor refuses a run id that exists.
fn launch_name(base: &str) -> String {
    let millis = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_or(0, |elapsed| u64::try_from(elapsed.as_millis()).unwrap_or(u64::MAX));
    format!("{base}-{}", base36(millis))
}

fn base36(mut value: u64) -> String {
    const DIGITS: &[u8; 36] = b"0123456789abcdefghijklmnopqrstuvwxyz";
    let mut digits = Vec::new();
    loop {
        digits.push(DIGITS[usize::try_from(value % 36).unwrap_or_default()]);
        value /= 36;
        if value == 0 {
            break;
        }
    }
    digits.reverse();
    String::from_utf8(digits).unwrap_or_default()
}

/// The real path of `path`, as the IDE resolves its driven project.
#[expect(
    clippy::disallowed_methods,
    reason = "the guest agent runs on Linux and macOS only, and the bridge compares the real path of the project"
)]
fn real_path(path: &Path) -> Result<PathBuf, AgentRefusal> {
    fs::canonicalize(path).map_err(|error| failed(format!("cannot resolve {}: {error}", path.display())))
}

/// Removes the directory `path` and everything in it. A path that does not exist is no error.
pub(crate) fn remove_tree(path: &Path) -> io::Result<()> {
    match fs::remove_dir_all(path) {
        Err(error) if error.kind() != io::ErrorKind::NotFound => Err(error),
        _ => Ok(()),
    }
}

fn remove_if_present(path: &Path) -> io::Result<()> {
    match fs::remove_file(path) {
        Err(error) if error.kind() != io::ErrorKind::NotFound => Err(error),
        _ => Ok(()),
    }
}

fn refused(message: impl Into<String>) -> AgentRefusal {
    AgentRefusal::refused(CODE, message)
}

fn failed(message: impl Into<String>) -> AgentRefusal {
    AgentRefusal::failure(CODE, message)
}
