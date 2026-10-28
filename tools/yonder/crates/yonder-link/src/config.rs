//! `.yonder.toml` in the root of the shipped commit: what a repository asks of the host for each run. The agent and the
//! client parse it with this one parser: the agent for the prepare steps, the environment and the outputs that it
//! stages, the client for the outputs that it takes back. Each end reads the file from the commit of the run, so a file
//! beside the commit never counts. A commit without the file has no prepare steps, no environment of its own and no
//! outputs. `API.md` of the agent gives the format with an example.
//!
//! - `outputs`: the paths that come back after the run, for a client that asks for them.
//! - `[env]`: the environment of the prepare steps and the command, on top of the login environment of the user.
//! - `[[prepare]]`: the steps that prepare the slot before the command, each one only when its inputs changed.
//!
//! An unknown key or a value of the wrong shape is a [`ConfigError`], so a typo never drops a step.

use std::collections::BTreeMap;
use std::fmt;
use std::path::{Component, Path};

use serde::Deserialize;

/// The name of the file in the root of the commit.
pub const FILE: &str = ".yonder.toml";

/// The content of `.yonder.toml`.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct RepoConfig {
    /// Paths relative to the slot root: files or directories.
    pub outputs: Vec<String>,
    /// The variables in the order of their names.
    pub env: Vec<(String, EnvValue)>,
    pub prepare: Vec<Step>,
}

/// One prepare step.
#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Step {
    /// The name in the log. The command line when the file names none.
    #[serde(default)]
    pub name: String,
    /// The program and its arguments, run in the slot root.
    pub run: Vec<String>,
    /// Tracked paths whose content keys the step: files or directories at the commit of the run.
    #[serde(default)]
    pub inputs: Vec<String>,
    /// Paths that the step makes. A missing one runs the step again.
    #[serde(default)]
    pub creates: Vec<String>,
}

/// The value of one variable of `[env]`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum EnvValue {
    /// `NAME = "text"`: the text as it is.
    Text(String),
    /// `NAME = { prepend = [...], append = [...] }`: entries joined with `:` before and after the inherited value. A
    /// leading `~/` of an entry is the home directory, and a relative entry is below the slot root.
    List { prepend: Vec<String>, append: Vec<String> },
    /// `NAME = { port = 3100 }`: a free TCP port, the base plus the number of the slot or the next free one.
    Port(u16),
    /// `NAME = { unset = true }`: the variable is removed.
    Unset,
}

/// Why the text of `.yonder.toml` does not parse. The message starts with the name of the file.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ConfigError {
    message: String,
}

impl ConfigError {
    fn new(message: impl fmt::Display) -> Self {
        Self {
            message: format!("{FILE}: {message}"),
        }
    }
}

impl fmt::Display for ConfigError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.message)
    }
}

impl std::error::Error for ConfigError {}

/// The table form of an `[env]` value, before the check of its shape.
#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct EnvTable {
    prepend: Option<Vec<String>>,
    append: Option<Vec<String>>,
    port: Option<u16>,
    unset: Option<bool>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct RawConfig {
    #[serde(default)]
    outputs: Vec<String>,
    #[serde(default)]
    env: BTreeMap<String, toml::Value>,
    #[serde(default)]
    prepare: Vec<Step>,
}

/// Parses and checks the text of `.yonder.toml`.
pub fn parse(text: &str) -> Result<RepoConfig, ConfigError> {
    let raw: RawConfig = toml::from_str(text).map_err(|error| ConfigError::new(error.message()))?;
    for output in &raw.outputs {
        check_path("outputs", output).map_err(ConfigError::new)?;
    }
    let mut env = Vec::with_capacity(raw.env.len());
    for (name, value) in raw.env {
        if name.is_empty() || name.contains(['=', '\0']) {
            return Err(ConfigError::new(format!("{name:?} cannot name a variable")));
        }
        let value = env_value(&name, value).map_err(ConfigError::new)?;
        env.push((name, value));
    }
    let mut prepare = raw.prepare;
    for step in &mut prepare {
        if step.run.is_empty() || step.run[0].is_empty() {
            return Err(ConfigError::new("a prepare step has no command in `run`"));
        }
        if step.name.is_empty() {
            step.name = step.run.join(" ");
        }
        for path in step.inputs.iter().chain(&step.creates) {
            check_path(&format!("the prepare step {:?}", step.name), path).map_err(ConfigError::new)?;
        }
    }
    Ok(RepoConfig {
        outputs: raw.outputs,
        env,
        prepare,
    })
}

/// Whether `path` is a relative path that stays inside its base: no root, no `..`, no `.`.
pub fn is_inner_path(path: &str) -> bool {
    !path.is_empty()
        && Path::new(path)
            .components()
            .all(|component| matches!(component, Component::Normal(_)))
}

fn check_path(what: &str, path: &str) -> Result<(), String> {
    if is_inner_path(path) {
        Ok(())
    } else {
        Err(format!("{what} names {path:?}, which is not a path inside the slot"))
    }
}

fn env_value(name: &str, value: toml::Value) -> Result<EnvValue, String> {
    let table = match value {
        toml::Value::String(text) => return Ok(EnvValue::Text(text)),
        toml::Value::Table(table) => table,
        other => return Err(format!("env.{name} is a {}, not a string or a table", other.type_str())),
    };
    let table: EnvTable = toml::Value::Table(table)
        .try_into()
        .map_err(|error: toml::de::Error| format!("env.{name}: {}", error.message()))?;
    match table {
        EnvTable {
            prepend,
            append,
            port: None,
            unset: None,
        } if prepend.is_some() || append.is_some() => Ok(EnvValue::List {
            prepend: prepend.unwrap_or_default(),
            append: append.unwrap_or_default(),
        }),
        EnvTable {
            prepend: None,
            append: None,
            port: Some(port),
            unset: None,
        } => Ok(EnvValue::Port(port)),
        EnvTable {
            prepend: None,
            append: None,
            port: None,
            unset: Some(true),
        } => Ok(EnvValue::Unset),
        _ => Err(format!(
            "env.{name} takes one of: `prepend` and `append`, `port`, or `unset = true`"
        )),
    }
}

#[cfg(test)]
mod tests;
