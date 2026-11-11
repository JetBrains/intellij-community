// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

//! The runner of `accio_unused_test` (`defs.bzl`): a Bazel test that fails on a declaration that nothing uses.
//!
//! The test has one case. It runs `accio unused` over the scope of the target and fails on each candidate that accio
//! lists, as `<path>:<line> <kind> <name>`. A declaration that stays without a use carries the mark of its language
//! that `accio unused --help` names, such as `@Suppress("unused")` or `#[expect(dead_code, reason = "…")]`, and accio
//! then leaves it out.
//!
//! The test always reads the checkout that holds its anchor file, a file of the package of the target: the runner
//! resolves the link of the anchor in the runfiles to the checkout. It runs the `community/tools/accio.cmd` of that
//! checkout, which runs the pinned accio or a newer one on the PATH. The test inherits the index home of the user, so a
//! checkout with a ready index answers in seconds. A checkout without an index fails the first query, and the runner
//! then builds the index and asks again.

use crate::{Case, env_var, finish, resolve, root_of};
use runfiles::Runfiles;
use serde::Deserialize;
use std::error::Error;
use std::fmt::Write as _;
use std::io;
use std::path::Path;
use std::process::{Command, Output, Stdio};

/// The runfiles path of `community/tools/accio.cmd`. The rule sets it only for an `accio_unused_test`, so it also
/// picks the runner of the test.
pub(crate) const ACCIO_ENV_VAR: &str = "ACCIO_UNUSED_TEST_ACCIO";

/// The runfiles path of the anchor file, and its path inside its repository.
const ANCHOR_ENV_VAR: &str = "ACCIO_UNUSED_TEST_ANCHOR";
const ANCHOR_PATH_ENV_VAR: &str = "ACCIO_UNUSED_TEST_ANCHOR_PATH";

/// The scope entries of the target as a JSON array, relative to the directory of the anchor.
const SCOPES_ENV_VAR: &str = "ACCIO_UNUSED_TEST_SCOPES";

/// The class name and the case name of the report.
const CLASS_ENV_VAR: &str = "ACCIO_UNUSED_TEST_CLASS";
const CASE_ENV_VAR: &str = "ACCIO_UNUSED_TEST_CASE";

/// One sentence that a failure prints, such as the guide to removing dead code.
const GUIDE_ENV_VAR: &str = "ACCIO_UNUSED_TEST_GUIDE";

/// A limit above any scope of the repository, so the answer is complete.
const LIMIT: &str = "100000";

/// The answer of `accio unused --format json`.
#[derive(Debug, Deserialize)]
struct Answer {
    #[serde(default)]
    hits: Vec<Hit>,
    complete: Option<bool>,
    available: Option<bool>,
    note: Option<String>,
}

#[derive(Debug, Deserialize)]
struct Hit {
    file: String,
    line: u32,
    kind: String,
    name: String,
    parent: Option<String>,
}

/// Runs the case, prints its failure, writes the report, and answers whether it passed.
pub(crate) fn run() -> Result<bool, Box<dyn Error>> {
    let runfiles = Runfiles::create()?;
    let accio = fscopy::resolve_links(&resolve(&runfiles, &env_var(ACCIO_ENV_VAR)?)?)?;
    let anchor = fscopy::resolve_links(&resolve(&runfiles, &env_var(ANCHOR_ENV_VAR)?)?)?;
    let root = root_of(&anchor, &env_var(ANCHOR_PATH_ENV_VAR)?)?;
    let base = anchor.parent().unwrap_or(&root);
    let entries: Vec<String> = serde_json::from_str(&env_var(SCOPES_ENV_VAR)?)?;
    let scopes = scope_args(base, &entries);
    let guide = std::env::var(GUIDE_ENV_VAR).unwrap_or_default();
    let case = Case {
        name: env_var(CASE_ENV_VAR)?,
        failure: unused_failure(&accio, &root, &scopes, &guide),
    };
    Ok(finish(&env_var(CLASS_ENV_VAR)?, &[case])?)
}

/// The `--scope` arguments of the scope entries. A path entry is absolute under `base`; an entry that starts with `!`
/// or holds a `*` is a pattern, which accio reads as it is.
pub(crate) fn scope_args(base: &Path, entries: &[String]) -> Vec<String> {
    entries
        .iter()
        .flat_map(|entry| {
            let scope = if entry.starts_with('!') || entry.contains('*') {
                entry.to_owned()
            } else {
                crate::config::normalize(&base.join(entry)).display().to_string()
            };
            ["--scope".to_owned(), scope]
        })
        .collect()
}

/// The failure of the case: accio failed, or it lists a declaration that nothing uses.
fn unused_failure(accio: &Path, root: &Path, scopes: &[String], guide: &str) -> Option<String> {
    match ask(accio, root, scopes) {
        Ok(json) => failure_of(&json, guide),
        Err(error) => Some(error.to_string()),
    }
}

/// Runs `accio unused` in `root`. A failed first run, such as one without an index, builds the index and runs again.
fn ask(accio: &Path, root: &Path, scopes: &[String]) -> Result<String, Box<dyn Error>> {
    let mut unused = vec!["unused".to_owned()];
    unused.extend_from_slice(scopes);
    unused.extend(["--limit", LIMIT, "--format", "json"].map(str::to_owned));
    let first = accio_command(accio, root, &unused).output()?;
    if first.status.success() {
        return Ok(String::from_utf8(first.stdout)?);
    }
    let index = accio_command(accio, root, &["index".to_owned()]).output()?;
    if !index.status.success() {
        return Err(failed("accio index", &index).into());
    }
    let second = accio_command(accio, root, &unused).output()?;
    if !second.status.success() {
        return Err(failed("accio unused", &second).into());
    }
    Ok(String::from_utf8(second.stdout)?)
}

/// The command that runs the polyglot wrapper `accio` with `args` in `root`: through bash, as the wrapper has no
/// shebang, and through `cmd` on Windows.
fn accio_command(accio: &Path, root: &Path, args: &[String]) -> Command {
    let mut command = if cfg!(windows) {
        let mut command = Command::new("cmd");
        command.arg("/c").arg(accio);
        command
    } else {
        let mut command = Command::new("bash");
        command.arg(accio);
        command
    };
    command.args(args).current_dir(root).stdin(Stdio::null());
    command
}

fn failed(what: &str, output: &Output) -> io::Error {
    io::Error::other(format!(
        "{what} failed with {}:\n{}{}",
        output.status,
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    ))
}

/// The failure of an answer of `accio unused --format json`: an answer that is not available or not complete, or each
/// listed declaration as `<path>:<line> <kind> <parent>.<name>`.
pub(crate) fn failure_of(json: &str, guide: &str) -> Option<String> {
    let answer: Answer = match serde_json::from_str(json) {
        Ok(answer) => answer,
        Err(error) => return Some(format!("accio unused printed no answer ({error}):\n{json}")),
    };
    let note = answer.note.as_deref().unwrap_or_default();
    if answer.available == Some(false) {
        return Some(format!("accio unused has no answer: {note}"));
    }
    if answer.complete == Some(false) {
        return Some(format!("the answer of accio unused is not complete: {note}"));
    }
    if answer.hits.is_empty() {
        return None;
    }
    let mut message = format!(
        "accio unused lists {} declarations that nothing uses. Delete each one, or keep it with the mark of its \
         language that `accio unused --help` names.",
        answer.hits.len()
    );
    if !guide.is_empty() {
        message.push(' ');
        message.push_str(guide);
    }
    for hit in &answer.hits {
        let _ = write!(message, "\n{}:{} {} ", hit.file, hit.line, hit.kind);
        if let Some(parent) = &hit.parent {
            let _ = write!(message, "{parent}.");
        }
        message.push_str(&hit.name);
    }
    Some(message)
}

#[cfg(test)]
#[path = "unused_test.rs"]
mod test;
