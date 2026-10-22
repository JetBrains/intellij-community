//! The documents of the lane IDE: the context document of a preparation, the launch document of a launch, their
//! answers, the launch record that a launch writes into the context, and the answer of `ide-gc`.
//!
//! The lane daemon of the guest agent prepares and launches its IDE in its own process. One context is
//! `<ideRoot>/<launchKey>`. It holds the data directories of the IDE, the project, the home and the `bin` directory of
//! the IDE environment, the argument file, the launch record, and the run slot of the run supervisor. One IDE run is
//! the supervisor run [`ide_run_id`] of one launch name, so a relaunch on the same context is a new run in the same
//! slot.
//!
//! [`validate_ide_context`] and [`validate_ide_launch`] are the checks of the two documents. The guest agent calls
//! them at the start of each preparation and each launch.
//!
//! No document here holds a secret. The bridge token is a file that the caller writes into the config directory, and
//! the IDE environment is the layout of the context, not data. So the documents derive their `Debug` form.

use std::collections::BTreeMap;
use std::fmt;

use serde::{Deserialize, Serialize};

use crate::supervisor::RunState;

#[cfg(test)]
mod tests;

/// The version of the context and launch documents, of their answers and of the launch record. The agent refuses a
/// document or a record of another version.
pub const SCHEMA_VERSION: u32 = 2;

/// The prefix of the supervisor run id of an IDE launch.
pub const IDE_RUN_PREFIX: &str = "run-ide-";

/// The directories of a context, each one a child of the context directory.
pub const CONFIG_DIR: &str = "config";
pub const SYSTEM_DIR: &str = "system";
pub const PLUGINS_DIR: &str = "plugins";
/// The parent of one log directory per launch name.
pub const LOG_DIR: &str = "log";
/// The default unpack root of the project archive.
pub const PROJECT_DIR: &str = "project";
/// The `HOME` of the IDE process. The caller seeds it between the preparation and the launch.
pub const HOME_DIR: &str = "home";
/// The first directory of the `PATH` of the IDE process. The caller puts its launchers into it.
pub const BIN_DIR: &str = "bin";

/// The argument file of the IDE, a child of the context directory. The IDE starts as `java @<this file>`.
pub const ARG_FILE: &str = "ide-jvm.args";
/// The launch record, a child of the context directory.
pub const LAUNCH_RECORD_FILE: &str = "launch.json";
/// The file of the plugin ids that the IDE does not load, a child of the config directory.
pub const DISABLED_PLUGINS_FILE: &str = "disabled_plugins.txt";
/// The advanced settings of an IDE, below the `options` directory of its config directory.
pub const ADVANCED_SETTINGS_FILE: &str = "advancedSettings.xml";

/// The advanced settings of an IDE whose project opens no editor at the start. On the first open of a project, the IDE
/// opens its `README.md` (`ide.open.readme.md.on.startup`), and the next start restores that editor. The start-up bench
/// and every IDE that the lane daemon launches write these bytes as [`ADVANCED_SETTINGS_FILE`], so the editor area of
/// their project starts empty. No lane tests the opening of a README.
pub const NO_README_SETTINGS: &str = r#"<application>
  <component name="AdvancedSettings">
    <option name="settings">
      <map>
        <entry key="ide.open.readme.md.on.startup" value="false" />
      </map>
    </option>
  </component>
</application>
"#;

/// The system properties that the agent owns, because each one names a path of the context. A launch document or a
/// flags file that states one of them is refused.
pub const OWNED_PROPERTIES: [&str; 6] = [
    "idea.config.path",
    "idea.system.path",
    "idea.plugins.path",
    "idea.log.path",
    "snapshots.path",
    "idea.diagnostic.opentelemetry.file",
];

/// The JVM options that the agent owns, by their prefix, for the same reason as [`OWNED_PROPERTIES`].
pub const OWNED_JVM_OPTIONS: [&str; 3] = ["-XX:ErrorFile=", "-XX:HeapDumpPath=", "-Xlog:gc"];

/// Whether `text` can be a launch key: one path component of ASCII letters, digits, `.`, `_` and `-`, which does
/// not start with `.`.
pub fn is_launch_key(text: &str) -> bool {
    !text.is_empty()
        && !text.starts_with('.')
        && text.len() <= 128
        && text
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_' | b'-'))
}

/// Whether `text` can be a launch name: ASCII letters, digits and `-`, so that [`ide_run_id`] is a supervisor run id.
pub fn is_launch_name(text: &str) -> bool {
    !text.is_empty() && text.len() <= 128 && text.bytes().all(|byte| byte.is_ascii_alphanumeric() || byte == b'-')
}

/// The supervisor run id of the launch `launch_name`, or `None` when the name is not a launch name.
pub fn ide_run_id(launch_name: &str) -> Option<String> {
    is_launch_name(launch_name).then(|| format!("{IDE_RUN_PREFIX}{launch_name}"))
}

/// The launch name of the IDE run `run_id`, or `None` when the run is not the run of an IDE launch.
pub fn launch_name_of(run_id: &str) -> Option<&str> {
    run_id.strip_prefix(IDE_RUN_PREFIX).filter(|name| is_launch_name(name))
}

/// The context directory of `launch_key` under `ide_root`, as a guest path.
pub fn context_dir(ide_root: &str, launch_key: &str) -> String {
    format!("{}/{launch_key}", ide_root.trim_end_matches('/'))
}

// --- the context document ----------------------------------------------------------------------------------

/// What a preparation lays out: one context.
///
/// The caller prepares a context once, before it seeds the home, the `bin` directory and the config files. A relaunch
/// on the same context prepares it with `fresh` false.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdeContext {
    pub schema_version: u32,
    /// The parent of every context, an absolute guest path.
    pub ide_root: String,
    /// The context under `ide_root`. See [`is_launch_key`].
    pub launch_key: String,
    /// Deletes what a preparation and a launch write before it prepares again: the data directories, the project, the
    /// home, the `bin` directory, the argument file and the launch record. The log directories and the run
    /// directories of earlier launches stay, and `ide-gc` trims them. The caller sets it on the first call for a new
    /// context only.
    pub fresh: bool,
    pub project: ProjectSource,
    #[serde(default)]
    pub disabled_plugin_ids: Vec<String>,
}

/// The project that the IDE opens: a zip archive, and the directory inside it that is the project.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProjectSource {
    /// The zip archive, an absolute guest path.
    pub archive: String,
    /// The project directory inside the archive, a relative slash path. Empty is the archive root.
    #[serde(default)]
    pub archive_root: String,
    /// The unpack root instead of `<context>/project`, an absolute guest path. A fresh preparation deletes it first.
    #[serde(default, deserialize_with = "crate::json::non_null", skip_serializing_if = "Option::is_none")]
    pub relocate_to: Option<String>,
}

/// What a preparation answers: the paths of the context.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdePrepared {
    pub context_dir: String,
    pub config_dir: String,
    pub system_dir: String,
    pub plugins_dir: String,
    /// The directory that the IDE opens: the unpack root joined with `archiveRoot`.
    pub project_dir: String,
    /// [`HOME_DIR`] of the context.
    pub home_dir: String,
    /// [`BIN_DIR`] of the context.
    pub bin_dir: String,
}

// --- the launch document -----------------------------------------------------------------------------------

/// What a launch starts: one launch of the IDE on a prepared context.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdeLaunch {
    pub schema_version: u32,
    /// The parent of every context, an absolute guest path.
    pub ide_root: String,
    /// The context under `ide_root`. See [`is_launch_key`].
    pub launch_key: String,
    /// The home of the dev distribution, an absolute guest path. The IDE runs in it.
    pub dist_home: String,
    /// The `DevIdeConfig` file of the distribution, which states the main class. An absolute guest path.
    pub ide_config: String,
    /// The JVM that the IDE runs on, an absolute guest path. The launch record keeps it, so a later kill finds
    /// `<javaHome>/bin/jcmd`.
    pub java_home: String,
    /// The JVM flags that Bazel writes, one per line. An absolute guest path.
    pub flags_file: String,
    /// The `-D` properties of the launch. Sorted by key on the wire, so the argument file is the same for the same
    /// document.
    #[serde(default)]
    pub properties: BTreeMap<String, String>,
    /// The directory that the IDE opens, as [`IdePrepared`] answered it. An absolute guest path.
    pub project_dir: String,
    /// The name of this launch. See [`is_launch_name`].
    pub launch_name: String,
    /// The identity of the product that the IDE runs. `ide-gc --keep-product` keeps a live IDE only when its launch
    /// record names the same digest. Empty means no identity, which no `--keep-product` matches.
    #[serde(default)]
    pub product_digest: String,
}

/// What a launch answers: the log directory and the argument file of the launch, and the state of its run.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdeLaunched {
    /// `log/<launchName>` of the context.
    pub log_dir: String,
    pub arg_file: String,
    /// The run `run-ide-<launchName>`, as `status` answers it.
    pub run: RunState,
}

/// The file `launch.json` of a context: which launch the argument file is for.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LaunchRecord {
    pub schema_version: u32,
    pub launch_key: String,
    pub launch_name: String,
    pub product_digest: String,
    pub java_home: String,
    pub arg_file: String,
    pub prepared_at: String,
}

// --- the collection -----------------------------------------------------------------------------------------

/// One IDE run under an IDE root.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdeRun {
    pub launch_key: String,
    pub run_id: String,
}

/// What `ide-gc` answers. Each list is always an array. An agent of an older build sends no `killed` and no
/// `abandoned`, and a reader then reads empty lists.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IdeGcResult {
    /// The live IDE runs that it canceled.
    pub stopped: Vec<IdeRun>,
    /// The live IDE runs whose cancel did not end in time, so that the agent killed their process groups.
    #[serde(default)]
    pub killed: Vec<IdeRun>,
    /// The IDE runs whose processes outlived a kill. Their slots are free, and a recycle of the worker discards them.
    #[serde(default)]
    pub abandoned: Vec<IdeRun>,
    /// The live IDE runs that it kept.
    pub kept: Vec<IdeRun>,
    /// The log directories and the finished run directories that it removed, as guest paths.
    pub removed: Vec<String>,
}

/// The file in the slot root of a context that records an abandoned run, [`AbandonedRun`].
pub const ABANDONED_FILE: &str = "abandoned.json";

/// A run whose processes outlived the kill of its process group. The agent freed its slot and wrote this record, so
/// `ide-gc` and the `status` of the worker name it until its processes are gone or a recycle discards the guest.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AbandonedRun {
    pub run_id: String,
    /// The process group that outlived the kill.
    pub pgid: i32,
    /// Each process of the group when the agent gave up, as `<pid> <command>`.
    pub members: Vec<String>,
    pub abandoned_at: String,
}

// --- the checks ---------------------------------------------------------------------------------------------

/// Why a context or launch document is refused. The message names a field or a key, and never a value.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct IdeDocumentError {
    pub message: String,
}

impl fmt::Display for IdeDocumentError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}

impl std::error::Error for IdeDocumentError {}

fn refuse(message: impl Into<String>) -> IdeDocumentError {
    IdeDocumentError { message: message.into() }
}

/// Refuses a context document that the agent cannot act on. A preparation calls it before it changes a file.
pub fn validate_ide_context(document: &IdeContext) -> Result<(), IdeDocumentError> {
    validate_slot(
        "the IDE context document",
        document.schema_version,
        &document.ide_root,
        &document.launch_key,
    )?;
    validate_project(&document.project)?;
    for id in &document.disabled_plugin_ids {
        if id.trim().is_empty() || id.contains(['\n', '\r']) {
            return Err(refuse(format!("{id:?} is not a plugin id")));
        }
    }
    Ok(())
}

/// Refuses a launch document that the agent cannot act on. One such document sets a property of [`OWNED_PROPERTIES`],
/// which names a data directory of the context. A launch calls it before it changes a file.
pub fn validate_ide_launch(document: &IdeLaunch) -> Result<(), IdeDocumentError> {
    validate_slot(
        "the IDE launch document",
        document.schema_version,
        &document.ide_root,
        &document.launch_key,
    )?;
    if !is_launch_name(&document.launch_name) {
        return Err(refuse(format!("launchName {:?} is not a launch name", document.launch_name)));
    }
    for (field, value) in [
        ("distHome", &document.dist_home),
        ("ideConfig", &document.ide_config),
        ("javaHome", &document.java_home),
        ("flagsFile", &document.flags_file),
        ("projectDir", &document.project_dir),
    ] {
        if !is_absolute_guest_path(value) {
            return Err(refuse(format!("{field} must be an absolute guest path")));
        }
    }
    for (key, value) in &document.properties {
        if key.is_empty() || key.contains(['=', '\n', '\r']) {
            return Err(refuse(format!("the property key {key:?} is not a property key")));
        }
        if value.contains(['\n', '\r']) {
            return Err(refuse(format!(
                "the value of the property {key} holds a line break, which an argument file cannot hold"
            )));
        }
        if OWNED_PROPERTIES.contains(&key.as_str()) {
            return Err(refuse(format!("the property {key} names a data directory, which the agent owns")));
        }
    }
    if document.product_digest.contains(['\n', '\r']) {
        return Err(refuse("productDigest holds a line break"));
    }
    Ok(())
}

/// Refuses another schema version, an IDE root that the agent cannot delete below, and a launch key that is not one.
fn validate_slot(what: &str, schema_version: u32, ide_root: &str, launch_key: &str) -> Result<(), IdeDocumentError> {
    if schema_version != SCHEMA_VERSION {
        return Err(refuse(format!(
            "{what} has schema version {schema_version}, and this agent reads {SCHEMA_VERSION}"
        )));
    }
    if !is_deletable_root(ide_root) {
        return Err(refuse("ideRoot must be an absolute guest path below the root, without `.` or `..`"));
    }
    if !is_launch_key(launch_key) {
        return Err(refuse(format!("launchKey {launch_key:?} is not a launch key")));
    }
    Ok(())
}

fn validate_project(project: &ProjectSource) -> Result<(), IdeDocumentError> {
    if !is_absolute_guest_path(&project.archive) {
        return Err(refuse("project.archive must be an absolute guest path"));
    }
    if !is_relative_slash_path(&project.archive_root) {
        return Err(refuse("project.archiveRoot must be a relative slash path without `.` or `..`"));
    }
    if let Some(relocate_to) = &project.relocate_to
        && !is_deletable_root(relocate_to)
    {
        return Err(refuse(
            "project.relocateTo must be an absolute guest path below the root, without `.` or `..`",
        ));
    }
    Ok(())
}

/// Reads the launch record of a context.
pub fn decode_launch_record(raw: &[u8]) -> Result<LaunchRecord, IdeDocumentError> {
    let record: LaunchRecord =
        serde_json::from_slice(raw).map_err(|error| refuse(format!("the launch record is not readable ({error})")))?;
    if record.schema_version != SCHEMA_VERSION {
        return Err(refuse(format!(
            "the launch record has schema version {}, and this agent reads {SCHEMA_VERSION}",
            record.schema_version
        )));
    }
    Ok(record)
}

/// Whether `path` is an absolute POSIX path.
fn is_absolute_guest_path(path: &str) -> bool {
    path.starts_with('/') && !path.contains('\0')
}

/// Whether `path` is an absolute POSIX path with at least two components and no `.` or `..` component: a path that
/// the agent can delete without the risk of a parent of it.
fn is_deletable_root(path: &str) -> bool {
    if !is_absolute_guest_path(path) {
        return false;
    }
    let components: Vec<&str> = path.split('/').filter(|component| !component.is_empty()).collect();
    components.len() >= 2 && components.iter().all(|component| !matches!(*component, "." | ".."))
}

/// Whether `path` is empty or a relative slash path without an empty, `.` or `..` component.
fn is_relative_slash_path(path: &str) -> bool {
    path.is_empty()
        || (!path.starts_with('/')
            && !path.contains('\0')
            && path
                .trim_end_matches('/')
                .split('/')
                .all(|component| !matches!(component, "" | "." | "..")))
}
