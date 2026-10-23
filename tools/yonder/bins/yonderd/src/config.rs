//! `.yonder.toml` in the root of the shipped commit: what a repository asks of the host for each run. The supervisor
//! reads it from the commit of the run, so a file that a slot has beside the commit never counts. A commit without the
//! file has no prepare steps, no environment of its own and no outputs. `API.md` of this crate gives the format with an
//! example.
//!
//! - `outputs`: the paths that come back after the run, for a client that asks for them.
//! - `[env]`: the environment of the prepare steps and the command, on top of the login environment of the user.
//! - `[[prepare]]`: the steps that prepare the slot before the command, each one only when its inputs changed.
//!
//! An unknown key or a value of the wrong shape refuses the run with `bad_config`, so a typo never drops a step.

use std::collections::BTreeMap;
use std::path::Path;

use serde::Deserialize;

use crate::git;
use crate::refused::{BAD_CONFIG, Refusal, refused};
use crate::serve::is_inner_path;

/// The name of the file in the root of the slot.
pub(crate) const FILE: &str = ".yonder.toml";

/// The content of `.yonder.toml`.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(crate) struct RepoConfig {
    /// Paths relative to the slot root: files or directories.
    pub(crate) outputs: Vec<String>,
    /// The variables in the order of their names.
    pub(crate) env: Vec<(String, EnvValue)>,
    pub(crate) prepare: Vec<Step>,
}

/// One prepare step.
#[derive(Clone, Debug, PartialEq, Eq, Deserialize)]
#[serde(deny_unknown_fields)]
pub(crate) struct Step {
    /// The name in the log. The command line when the file names none.
    #[serde(default)]
    pub(crate) name: String,
    /// The program and its arguments, run in the slot root.
    pub(crate) run: Vec<String>,
    /// Tracked paths whose content keys the step: files or directories at the commit of the run.
    #[serde(default)]
    pub(crate) inputs: Vec<String>,
    /// Paths that the step makes. A missing one runs the step again.
    #[serde(default)]
    pub(crate) creates: Vec<String>,
}

/// The value of one variable of `[env]`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum EnvValue {
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

/// The configuration of `commit` in the repository of `dir`, or the default one when the commit has no `.yonder.toml`.
pub(crate) fn read(dir: &Path, commit: &str) -> Result<RepoConfig, Refusal> {
    let cannot = |error: String| refused(BAD_CONFIG, format!("cannot read {FILE} of {commit}: {error}"));
    let ids = git::object_ids(dir, commit, &[FILE.to_owned()]).map_err(cannot)?;
    let Some(Some(blob)) = ids.first() else {
        return Ok(RepoConfig::default());
    };
    let bytes = git::blob(dir, blob).map_err(cannot)?;
    let text = String::from_utf8(bytes).map_err(|error| cannot(format!("it is not UTF-8: {error}")))?;
    parse(&text)
}

/// Parses and checks the text of `.yonder.toml`.
pub(crate) fn parse(text: &str) -> Result<RepoConfig, Refusal> {
    let bad = |message: String| refused(BAD_CONFIG, format!("{FILE}: {message}"));
    let raw: RawConfig = toml::from_str(text).map_err(|error| bad(error.message().to_owned()))?;
    for output in &raw.outputs {
        check_path("outputs", output).map_err(bad)?;
    }
    let mut env = Vec::with_capacity(raw.env.len());
    for (name, value) in raw.env {
        if name.is_empty() || name.contains(['=', '\0']) {
            return Err(bad(format!("{name:?} cannot name a variable")));
        }
        let value = env_value(&name, value).map_err(bad)?;
        env.push((name, value));
    }
    let mut prepare = raw.prepare;
    for step in &mut prepare {
        if step.run.is_empty() || step.run[0].is_empty() {
            return Err(bad("a prepare step has no command in `run`".to_owned()));
        }
        if step.name.is_empty() {
            step.name = step.run.join(" ");
        }
        for path in step.inputs.iter().chain(&step.creates) {
            check_path(&format!("the prepare step {:?}", step.name), path).map_err(bad)?;
        }
    }
    Ok(RepoConfig {
        outputs: raw.outputs,
        env,
        prepare,
    })
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
