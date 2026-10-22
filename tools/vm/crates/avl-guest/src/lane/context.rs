//! The context files of a lane IDE: what the lane daemon writes into a prepared context before the launch, from the
//! lane document and the lane identity.
//!
//! The files, in write order:
//!
//! | file | content |
//! | --- | --- |
//! | `config/options/*.xml` | the option files of [`OPTION_FILE_TEMPLATES`] |
//! | `config/air-ui-test/token` | the token of the UI-test bridge |
//! | `config/air-ui-test/lane-services.json` | the lane-service token and the base URL of each lane service |
//! | `config/air-ui-test/control.json` | the control server in the IDE and its credentials, when the IDE owns an agent |
//! | `home/.air-lane/acp-scenario-agent.jar` | the copy of the fake agent jar |
//! | `home/.jetbrains/acp.json` | one entry for each local agent, when the lane has one |
//! | `bin/<runtime>` | one launcher for each declared CLI runtime |
//! | `home/.codex/config.toml` | the Codex mock model, when the lane serves it |
//! | `project/<name>/.git`, `project/<name>-origin.git` | the Git remote of the project, when the lane has a Cloud agent |
//!
//! A bare IDE gets the option files and the token of the bridge only, see [`write_bare`].
//!
//! The directory `config/air-ui-test` and its three files are private to the account. The runtime in the IDE owns
//! [`NEXT_START_CONTROL_FILE`], so this writer never writes or deletes it.

use std::collections::{BTreeMap, BTreeSet};
use std::fs::{self, OpenOptions};
use std::io::{self, Write};
use std::os::unix::fs::{DirBuilderExt, OpenOptionsExt, PermissionsExt};
use std::path::{Path, PathBuf};
use std::process::Command;

use avl_wire::ide::{ADVANCED_SETTINGS_FILE, IdePrepared, NO_README_SETTINGS};
use avl_wire::program::lane::{Delivery, FAKE_AGENT_JAR_TOKEN, JAVA_TOKEN, LIVE_LANE, LaneAgent, LaneDocument, Requirement};
use serde::Serialize;

use super::identity::{BRIDGE_TOKEN_PURPOSE, LANE_SERVICES_PURPOSE, LaneIdentity, terminal_login_key};
use super::live::LiveRun;
use super::package::sha256_hex;
use super::services::codex;
use crate::reply::{AgentRefusal, AgentRefusalExt};

pub(crate) mod editors;

#[cfg(test)]
mod tests;

/// The refusal code of a lane document or an input that this writer cannot launch as it states.
pub(crate) const INPUT_CODE: &str = "lane_context_input";

/// The refusal code of a context file that cannot be written.
pub(crate) const WRITE_CODE: &str = "lane_context_write";

/// The version of `control.json` and of `lane-services.json`.
pub(crate) const SCHEMA_VERSION: u32 = 1;

/// The directory below the config directory that holds the files of the UI-test bridge.
pub(crate) const BRIDGE_DIR: &str = "air-ui-test";

/// The token file of the UI-test bridge, `AIR_UI_TEST_TOKEN_FILE` of the bridge.
pub(crate) const TOKEN_FILE: &str = "token";

/// The control server in the IDE and its credentials.
pub(crate) const CONTROL_FILE: &str = "control.json";

/// The lane services of the lane.
pub(crate) const LANE_SERVICES_FILE: &str = "lane-services.json";

/// The held load of a restart, which the runtime in the IDE writes before a quit and consumes at the next start.
#[cfg_attr(
    not(test),
    expect(
        dead_code,
        reason = "the writer never writes or deletes this file, and only a test names it to prove that"
    )
)]
pub(crate) const NEXT_START_CONTROL_FILE: &str = "control-next-start.json";

/// The copy of the fake agent jar below the home of the context.
pub(crate) const AGENT_JAR: &str = ".air-lane/acp-scenario-agent.jar";

/// The ACP agent configuration below the home of the context.
pub(crate) const ACP_JSON: &str = ".jetbrains/acp.json";

/// The Codex configuration below the home of the context.
pub(crate) const CODEX_CONFIG: &str = ".codex/config.toml";

/// The identity of the JetBrains Cloud emulator that the lane document states.
pub(crate) const CLOUD_IDENTITY: &str = "jba-jcp-emulator-1";

/// The branch of the repository that the project of a Cloud lane becomes.
pub(crate) const CLOUD_BRANCH: &str = "main";

/// The author of the one commit of that repository, and no signature.
const CLOUD_GIT_CONFIG: [&str; 6] = [
    "-c",
    "user.name=Air Lane",
    "-c",
    "user.email=air-lane@example.com",
    "-c",
    "commit.gpgsign=false",
];

/// `PathManager.OPTIONS_DIRECTORY`.
const OPTIONS_DIR: &str = "options";

/// The default directory of a new project, a child of the context.
const NEW_PROJECTS_DIR: &str = "new-projects";

/// The option file that the writer writes only when it is missing.
const GENERAL_SETTINGS_FILE: &str = "ide.general.xml";

/// The placeholder of the default directory of a new project, an XML attribute value.
const DEFAULT_PROJECT_DIRECTORY: &str = "{{defaultProjectDirectory}}";

/// The placeholder of the trusted project paths: one `<entry>` line each, every line ended by a line feed.
const TRUSTED_PATH_ENTRIES: &str = "{{trustedPathEntries}}";

/// Each option file of a new context by its name, in write order, as its template. A test pins their digest, which the
/// config key reads. The last one keeps the project from opening its README, so every IDE that the daemon launches
/// starts with an empty editor area.
pub(crate) const OPTION_FILE_TEMPLATES: [(&str, &str); 6] = [
    (GENERAL_SETTINGS_FILE, include_str!("context/options/ide.general.xml")),
    ("ide.general.local.xml", include_str!("context/options/ide.general.local.xml")),
    ("Minimap.xml", include_str!("context/options/Minimap.xml")),
    ("certificates.xml", include_str!("context/options/certificates.xml")),
    ("trusted-paths.xml", include_str!("context/options/trusted-paths.xml")),
    (ADVANCED_SETTINGS_FILE, NO_README_SETTINGS),
];

/// The hex SHA-256 of the templates: each one as `<name>\n<template>\n`, in write order. The config key reads it as
/// `optionFiles`.
pub(crate) fn option_files_digest() -> String {
    let text: String = OPTION_FILE_TEMPLATES
        .iter()
        .map(|(name, template)| format!("{name}\n{template}\n"))
        .collect();
    sha256_hex(text.as_bytes())
}

/// The identity of the Codex mock model that the lane document states, `SERVICE_IDENTITIES.codexModel` of
/// `flowFixtures.ts`.
pub(crate) fn codex_identity() -> String {
    format!("mock-responses port={} reply={}", codex::PORT, codex::REPLY)
}

/// One declared CLI runtime: the launcher name, the command that runs it, the identity of its declared inputs, and the
/// version that its manifest states.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct CliRuntime {
    pub name: String,
    /// The interpreter, when the runtime has one, then the entry point. Each one is an absolute guest path.
    pub command: Vec<String>,
    pub identity: String,
    /// The `version` of the manifest, which the live lane pins the release track of the Central CLI to.
    pub version: Option<String>,
}

/// The ports of the lane services of one lane. A service that the lane does not run has none.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct ServicePorts {
    /// The fake ACP registry, for a lane with a registry agent.
    pub registry: Option<u16>,
    /// The JetBrains Cloud emulator, for every lane.
    pub cloud: u16,
    /// The feedback capture, for a lane with the requirement `FEEDBACK_CAPTURE`.
    pub feedback: Option<u16>,
    /// The Codex mock model, for a lane that states it.
    pub codex: Option<u16>,
}

/// `lane-services.json`: the lane-service token and the base URL of each lane service, `null` for a service that the
/// lane does not run.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct LaneServicesFile {
    schema_version: u32,
    token: String,
    registry: Option<String>,
    cloud: String,
    feedback: Option<String>,
    codex: Option<String>,
}

/// `control.json`: the control server in the IDE, the `java` and the jar of a terminal launcher, and the credentials.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ControlFile {
    schema_version: u32,
    port: u16,
    agent_java: String,
    agent_jar: String,
    credentials: Vec<Credential>,
}

/// One credential of the control server. A terminal sign-in has its own key and its own token.
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct Credential {
    agent_key: String,
    token: String,
    terminal_login: bool,
    /// The agent lists sessions, [`LaneAgent::lists_sessions`]. A terminal sign-in lists no session.
    lists_sessions: bool,
}

/// The inputs of the context files of one lane IDE.
#[derive(Clone, Copy, Debug)]
pub(crate) struct LaneContext<'a> {
    pub document: &'a LaneDocument,
    pub identity: &'a LaneIdentity,
    /// The `java` that runs the fake agent: `bin/java` of the JBR that the lane IDE runs on, an absolute guest path.
    pub java: &'a Path,
    /// The fake agent jar that the lane pushed.
    pub fake_agent_jar: &'a Path,
    /// The declared CLI runtimes. Their names are the `cliRuntimes` of the document.
    pub runtimes: &'a [CliRuntime],
    /// The live run of the iteration, for the live lane alone.
    pub live: Option<&'a LiveRun>,
}

impl LaneContext<'_> {
    /// Refuses a document and inputs that this writer cannot launch as they state, and answers the ports of the lane
    /// services of the lane.
    pub(crate) fn check(&self) -> Result<ServicePorts, AgentRefusal> {
        let document = self.document;
        let live = document.lane == LIVE_LANE;
        if live && self.live.is_none() {
            return Err(refused(
                "the lane UI_LIVE launches only in a live run, which the lane daemon of a Linux guest starts with its Central login",
            ));
        }
        if !live && self.live.is_some() {
            return Err(AgentRefusal::internal(format!(
                "a live run reached the context of the lane {}",
                document.lane
            )));
        }
        let declared: BTreeSet<&str> = document.cli_runtimes.iter().map(String::as_str).collect();
        let given: BTreeSet<&str> = self.runtimes.iter().map(|runtime| runtime.name.as_str()).collect();
        if declared != given || given.len() != self.runtimes.len() {
            return Err(refused(format!(
                "the lane document of {} declares the CLI runtimes {declared:?}, and the lane has the runtimes {:?}",
                document.lane,
                self.runtimes.iter().map(|runtime| &runtime.name).collect::<Vec<_>>()
            )));
        }
        if let Some(runtime) = self.runtimes.iter().find(|runtime| runtime.command.is_empty()) {
            return Err(refused(format!("the CLI runtime '{}' has no command", runtime.name)));
        }
        // A live lane runs the real Codex on the Central proxy, so it gets no Codex mock model.
        let mut served = BTreeMap::from([("jetBrainsCloud".to_owned(), CLOUD_IDENTITY.to_owned())]);
        if !live {
            served.insert("codexModel".to_owned(), codex_identity());
        }
        if document.services != served {
            return Err(refused(format!(
                "the lane document of {} states the services {:?}, and the lane daemon serves {served:?}",
                document.lane, document.services
            )));
        }
        let mut names = BTreeSet::new();
        for name in document.agents.iter().filter_map(|agent| agent.config_name.as_deref()) {
            if !names.insert(name) {
                return Err(refused(format!("two local agents of the lane state the acp.json entry '{name}'")));
            }
        }
        if !self.java.is_absolute() {
            return Err(refused(format!(
                "the java of the fake agent is not absolute: {}",
                self.java.display()
            )));
        }
        let requires_feedback = document
            .requirements
            .iter()
            .any(|requirement| requirement.name == Requirement::FeedbackCapture);
        Ok(ServicePorts {
            registry: (!document.registry_agents.is_empty()).then(|| self.identity.registry_port()),
            cloud: self.identity.cloud_port(),
            feedback: requires_feedback.then(|| self.identity.feedback_port()),
            codex: (!live).then_some(codex::PORT),
        })
    }

    /// Writes the context files into the prepared context `paths`, and answers the ports of the lane services.
    pub(crate) fn write(&self, paths: &IdePrepared) -> Result<ServicePorts, AgentRefusal> {
        let ports = self.check()?;
        let (home, project) = (Path::new(&paths.home_dir), Path::new(&paths.project_dir));
        if self.document.agents.iter().any(|agent| agent.delivery == Delivery::Cloud) {
            prepare_cloud_remote(&real_path(project)?)?;
        }
        let bridge = write_options_and_token(paths, &self.identity.secret(BRIDGE_TOKEN_PURPOSE))?;
        write_private(&bridge.join(LANE_SERVICES_FILE), &json_document(&self.lane_services(ports))?)?;
        let control = bridge.join(CONTROL_FILE);
        match self.control_file(home) {
            Some(file) => write_private(&control, &json_document(&file)?)?,
            None => remove_file_if_present(&control)?,
        }
        let jar = home.join(AGENT_JAR);
        create_dirs(jar.parent().unwrap_or(home))?;
        fs::copy(self.fake_agent_jar, &jar).map_err(|error| {
            write_failure(format!(
                "cannot copy the fake agent jar {} to {}: {error}",
                self.fake_agent_jar.display(),
                jar.display()
            ))
        })?;
        if let Some(acp_json) = self.acp_json(&jar) {
            write_file(&home.join(ACP_JSON), acp_json.as_bytes())?;
        }
        for runtime in self.runtimes {
            write_launcher(&Path::new(&paths.bin_dir).join(&runtime.name), &runtime.command, &BTreeMap::new())?;
        }
        if let Some(port) = ports.codex {
            let real_project = real_path(project)?;
            write_file(&home.join(CODEX_CONFIG), codex_config(&real_project, port).as_bytes())?;
        }
        if let Some(live) = self.live {
            live.seed(paths)?;
        }
        Ok(ports)
    }

    fn lane_services(&self, ports: ServicePorts) -> LaneServicesFile {
        let url = |port: u16| format!("http://127.0.0.1:{port}");
        LaneServicesFile {
            schema_version: SCHEMA_VERSION,
            token: self.identity.secret(LANE_SERVICES_PURPOSE),
            registry: ports.registry.map(url),
            cloud: url(ports.cloud),
            feedback: ports.feedback.map(url),
            codex: ports.codex.map(url),
        }
    }

    /// The control file of the agents that the IDE owns, each with its credential and the one of its terminal sign-in.
    /// `None` when the IDE owns no agent.
    fn control_file(&self, home: &Path) -> Option<ControlFile> {
        let credentials: Vec<Credential> = self
            .document
            .agents
            .iter()
            .filter(|agent| agent.controlled)
            .flat_map(|agent| {
                [(agent.key.clone(), false), (terminal_login_key(&agent.key), true)].map(|(key, terminal_login)| Credential {
                    token: self.identity.secret(&key),
                    agent_key: key,
                    terminal_login,
                    lists_sessions: agent.lists_sessions && !terminal_login,
                })
            })
            .collect();
        (!credentials.is_empty()).then(|| ControlFile {
            schema_version: SCHEMA_VERSION,
            port: self.identity.control_port(),
            agent_java: self.java.display().to_string(),
            agent_jar: home.join(AGENT_JAR).display().to_string(),
            credentials,
        })
    }

    /// `acp.json` with one `agent_servers` entry for each local agent, in document order, or `None` for a lane without
    /// one. The tokens of an entry resolve to the `java` of the distribution's JBR and to the jar copy. A controlled
    /// agent also gets the control environment of its owner.
    fn acp_json(&self, jar: &Path) -> Option<String> {
        let entries: Vec<String> = self
            .document
            .agents
            .iter()
            .filter(|agent| agent.delivery == Delivery::Local)
            .filter_map(|agent| Some((agent, agent.config_name.as_deref()?, agent.acp_json.as_ref()?)))
            .map(|(agent, name, entry)| {
                let resolve = |text: &str| {
                    text.replace(JAVA_TOKEN, &self.java.display().to_string())
                        .replace(FAKE_AGENT_JAR_TOKEN, &jar.display().to_string())
                };
                let args: Vec<String> = entry.args.iter().map(|arg| json_string(&resolve(arg))).collect();
                let env: Vec<String> = entry
                    .env
                    .iter()
                    .map(|(name, value)| (name.as_str(), value.clone()))
                    .chain(self.control_environment(agent))
                    .map(|(name, value)| format!("{}:{}", json_string(name), json_string(&value)))
                    .collect();
                format!(
                    "{}:{{\"command\":{},\"args\":[{}],\"env\":{{{}}}}}",
                    json_string(name),
                    json_string(&resolve(&entry.command)),
                    args.join(","),
                    env.join(",")
                )
            })
            .collect();
        (!entries.is_empty()).then(|| format!("{{\"agent_servers\":{{{}}}}}", entries.join(",")))
    }

    fn control_environment(&self, agent: &LaneAgent) -> Vec<(&'static str, String)> {
        if agent.controlled {
            self.identity.control_environment(&agent.key).into()
        } else {
            Vec::new()
        }
    }
}

/// Writes the option files into `options`, and creates `new_projects`, an absolute path. `ide.general.xml` is written only when it is
/// missing. The trusted paths are the absolute project path and its real path.
/// Writes the context files of a bare IDE into the prepared context `paths`: the option files and `bridge_token` as the
/// token of the bridge. A bare IDE gets no file of the lane services, of the control server or of an agent, so its
/// home holds nothing of the machine or of the lane.
pub(crate) fn write_bare(paths: &IdePrepared, bridge_token: &str) -> Result<(), AgentRefusal> {
    write_options_and_token(paths, bridge_token).map(|_| ())
}

/// Writes the option files and the token of the bridge, which every lane IDE gets, and answers the private directory
/// of the bridge files.
fn write_options_and_token(paths: &IdePrepared, bridge_token: &str) -> Result<PathBuf, AgentRefusal> {
    let config = Path::new(&paths.config_dir);
    write_option_files(
        &config.join(OPTIONS_DIR),
        Path::new(&paths.project_dir),
        &Path::new(&paths.context_dir).join(NEW_PROJECTS_DIR),
    )?;
    let bridge = config.join(BRIDGE_DIR);
    create_private_dir(&bridge)?;
    write_private(&bridge.join(TOKEN_FILE), bridge_token.as_bytes())?;
    Ok(bridge)
}

fn write_option_files(options: &Path, project: &Path, new_projects: &Path) -> Result<(), AgentRefusal> {
    create_dirs(options)?;
    create_dirs(new_projects)?;
    let absolute = std::path::absolute(project).map_err(|error| write_failure(format!("cannot resolve {}: {error}", project.display())))?;
    let mut trusted = vec![absolute];
    let real = real_path(project)?;
    if !trusted.contains(&real) {
        trusted.push(real);
    }
    let entries: String = trusted
        .iter()
        .map(|path| {
            format!(
                "        <entry key=\"{}\" value=\"true\" />\n",
                xml_attribute(&path.display().to_string())
            )
        })
        .collect();
    let default_project = xml_attribute(&new_projects.display().to_string());
    for (name, template) in OPTION_FILE_TEMPLATES {
        let file = options.join(name);
        if name == GENERAL_SETTINGS_FILE {
            if !file.exists() {
                write_file(&file, template.as_bytes())?;
            }
            continue;
        }
        let text = template
            .replace(DEFAULT_PROJECT_DIRECTORY, &default_project)
            .replace(TRUSTED_PATH_ENTRIES, &entries);
        write_file(&file, text.as_bytes())?;
    }
    Ok(())
}

/// The `config.toml` of Codex: the mock model on `port`, and the trust of the project and of the first directory above
/// it that holds a `.git` entry, so Codex asks nothing.
fn codex_config(project: &Path, port: u16) -> String {
    let mut trusted = vec![project.to_path_buf()];
    if let Some(root) = project
        .ancestors()
        .find(|directory| directory.join(".git").symlink_metadata().is_ok())
        && root != project
    {
        trusted.push(root.to_path_buf());
    }
    let projects: String = trusted
        .iter()
        .map(|directory| {
            format!(
                "{} = {{ trust_level = \"trusted\" }}\n",
                toml_string(&directory.display().to_string())
            )
        })
        .collect();
    let base_url = toml_string(&format!("http://127.0.0.1:{port}/v1"));
    format!(
        "model = {model}\nmodel_provider = \"mock_provider\"\napproval_policy = \"never\"\nsandbox_mode = \"read-only\"\n\
         suppress_unstable_features_warning = true\ncli_auth_credentials_store = \"file\"\n\n[features]\nremote_plugin = false\n\n\
         [projects]\n{projects}\n[model_providers.mock_provider]\nname = \"Mock provider for test\"\nbase_url = {base_url}\n\
         wire_api = \"responses\"\nrequest_max_retries = 0\nstream_max_retries = 0\n",
        model = toml_string(codex::MODEL),
    )
}

/// Makes `project` a Git repository with one commit on [CLOUD_BRANCH], and gives it the `origin` `<name>-origin.git`
/// beside it: a bare repository that holds the commit. A Cloud launch reads the URL and the remote branch from Git, and
/// `git ls-remote` proves the branch. The emulated spawner never clones the URL, so the bare repository is the whole
/// remote. A project that is a repository already stays.
fn prepare_cloud_remote(project: &Path) -> Result<(), AgentRefusal> {
    if project.join(".git").symlink_metadata().is_ok() {
        return Ok(());
    }
    let name = project.file_name().unwrap_or_default().to_string_lossy();
    let origin = project.with_file_name(format!("{name}-origin.git"));
    let branch = format!("--initial-branch={CLOUD_BRANCH}");
    git(project, &["init", &branch])?;
    git(project, &["add", "--all"])?;
    git(project, &["commit", "--message", "Initial commit"])?;
    create_dirs(&origin)?;
    git(&origin, &["init", "--bare", &branch])?;
    git(project, &["remote", "add", "origin", &origin.display().to_string()])?;
    git(project, &["push", "--set-upstream", "origin", CLOUD_BRANCH])
}

/// Runs `git` with `arguments` in `directory`, with [CLOUD_GIT_CONFIG]. It reads no global and no system configuration,
/// so a hook or a template of the machine cannot change the repository.
fn git(directory: &Path, arguments: &[&str]) -> Result<(), AgentRefusal> {
    let output = Command::new("git")
        .args(CLOUD_GIT_CONFIG)
        .args(arguments)
        .current_dir(directory)
        .env("GIT_CONFIG_NOSYSTEM", "1")
        .env("GIT_CONFIG_GLOBAL", "/dev/null")
        .output()
        .map_err(|error| write_failure(format!("cannot run git in {}: {error}", directory.display())))?;
    if output.status.success() {
        return Ok(());
    }
    Err(write_failure(format!(
        "`git {}` failed in {} with {}: {}",
        arguments.join(" "),
        directory.display(),
        output.status,
        String::from_utf8_lossy(&output.stderr).trim()
    )))
}

/// A launcher that exports `environment` and runs `command` with the arguments of the launcher, executable by the owner.
/// The lane IDE inherits no variable, so a variable that a CLI needs is an export of its launcher.
pub(super) fn write_launcher(file: &Path, command: &[String], environment: &BTreeMap<String, String>) -> Result<(), AgentRefusal> {
    let quoted: Vec<String> = command.iter().map(|argument| shell_quote(argument)).collect();
    let exports: String = environment
        .iter()
        .map(|(name, value)| format!("export {name}={}\n", shell_quote(value)))
        .collect();
    write_file(file, format!("#!/bin/sh\n{exports}exec {} \"$@\"\n", quoted.join(" ")).as_bytes())?;
    let mode = fs::metadata(file)
        .map_err(|error| write_failure(format!("cannot read {}: {error}", file.display())))?
        .permissions()
        .mode();
    fs::set_permissions(file, fs::Permissions::from_mode(mode | 0o100))
        .map_err(|error| write_failure(format!("cannot make {} executable: {error}", file.display())))
}

/// `text` as one word of a POSIX shell: in single quotes, with each single quote of it closed, quoted and reopened.
pub(super) fn shell_quote(text: &str) -> String {
    format!("'{}'", text.replace('\'', "'\"'\"'"))
}

/// The real path of `path`, as the IDE resolves it.
#[expect(
    clippy::disallowed_methods,
    reason = "the guest agent runs on Linux and macOS only, and the trusted paths name the real path that the IDE resolves"
)]
pub(super) fn real_path(path: &Path) -> Result<PathBuf, AgentRefusal> {
    fs::canonicalize(path).map_err(|error| write_failure(format!("cannot resolve {}: {error}", path.display())))
}

fn json_document(value: &impl Serialize) -> Result<Vec<u8>, AgentRefusal> {
    let mut bytes = serde_json::to_vec_pretty(value).map_err(|error| write_failure(format!("cannot write a context document: {error}")))?;
    bytes.push(b'\n');
    Ok(bytes)
}

fn json_string(text: &str) -> String {
    serde_json::Value::from(text).to_string()
}

fn toml_string(text: &str) -> String {
    format!("\"{}\"", text.replace('\\', "\\\\").replace('"', "\\\""))
}

fn xml_attribute(text: &str) -> String {
    text.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
}

pub(super) fn create_dirs(directory: &Path) -> Result<(), AgentRefusal> {
    fs::create_dir_all(directory).map_err(|error| write_failure(format!("cannot create {}: {error}", directory.display())))
}

/// Creates `directory` readable only by this account, and takes that access from an existing one.
fn create_private_dir(directory: &Path) -> Result<(), AgentRefusal> {
    fs::DirBuilder::new()
        .recursive(true)
        .mode(0o700)
        .create(directory)
        .and_then(|()| fs::set_permissions(directory, fs::Permissions::from_mode(0o700)))
        .map_err(|error| write_failure(format!("cannot create {}: {error}", directory.display())))
}

fn write_file(file: &Path, bytes: &[u8]) -> Result<(), AgentRefusal> {
    if let Some(parent) = file.parent() {
        create_dirs(parent)?;
    }
    fs::write(file, bytes).map_err(|error| write_failure(format!("cannot write {}: {error}", file.display())))
}

/// Replaces `file` with a new file that only this account can read, so no earlier mode survives.
pub(super) fn write_private(file: &Path, bytes: &[u8]) -> Result<(), AgentRefusal> {
    remove_file_if_present(file)?;
    OpenOptions::new()
        .write(true)
        .create_new(true)
        .mode(0o600)
        .open(file)
        .and_then(|mut opened| opened.write_all(bytes))
        .map_err(|error| write_failure(format!("cannot write {}: {error}", file.display())))
}

fn remove_file_if_present(file: &Path) -> Result<(), AgentRefusal> {
    match fs::remove_file(file) {
        Err(error) if error.kind() != io::ErrorKind::NotFound => Err(write_failure(format!("cannot delete {}: {error}", file.display()))),
        _ => Ok(()),
    }
}

fn refused(message: impl Into<String>) -> AgentRefusal {
    AgentRefusal::refused(INPUT_CODE, message)
}

fn write_failure(message: String) -> AgentRefusal {
    AgentRefusal::failure(WRITE_CODE, message)
}
