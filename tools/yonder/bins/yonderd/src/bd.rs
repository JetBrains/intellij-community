//! The calls of Branch Deck (`bd`): `bd lease host`, which lends the host slots per run (`slots`) and does each git
//! call of the agent on the host repositories and their slots (`host`). Each call runs in `/` with stdin `/dev/null`,
//! unless it takes a pack, as `remote.sh` of BT runs them: bd must not take the directory of the agent for a repository,
//! and it must not wait for an input.
//!
//! An answer is one JSON object on stdout. A refusal is the error object of bd, `{"error": {"code", "message"}}`, with a
//! nonzero exit. The progress of bd goes to stderr, one line at a time.

use std::fmt;
use std::path::Path;
use std::process::Command;
use std::time::Duration;

use serde::Deserialize;

use crate::child::{self, Output, Stop};

/// A failed call of bd.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) struct Failed {
    /// The code of the error object of bd, such as `no_lease` or `git_error`. `None` when bd did not start, did not
    /// end in time, or answered no error object.
    pub(crate) code: Option<String>,
    pub(crate) message: String,
}

impl fmt::Display for Failed {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}

/// `bd lease host <args> -C <repo>`, to run in `/`.
pub(crate) fn host_command(repo: &Path, args: &[&str]) -> Command {
    let mut command = Command::new("bd");
    command.current_dir("/").args(["lease", "host"]).args(args).arg("-C").arg(repo);
    command
}

/// `bd lease host <args> -C <repo>` within `deadline`; its stdout when it succeeded. `stop` kills it early.
pub(crate) fn host(repo: &Path, args: &[&str], deadline: Duration, stop: Stop<'_>) -> Result<String, String> {
    host_call(repo, args, deadline, stop, &mut |_| {}).map_err(|failed| failed.message)
}

/// `bd lease host <args> -C <repo>` within `deadline`. Each line of its stderr goes to `line` while bd runs. Answers its
/// stdout when it succeeded. `stop` kills it early.
pub(crate) fn host_call(
    repo: &Path,
    args: &[&str],
    deadline: Duration,
    stop: Stop<'_>,
    line: &mut dyn FnMut(&str),
) -> Result<String, Failed> {
    let output = child::run_with_lines(&mut host_command(repo, args), deadline, stop, line).map_err(|error| Failed {
        code: None,
        message: error.to_string(),
    })?;
    answer(repo, args.first().copied().unwrap_or_default(), &output)
}

/// The stdout of a call of `bd lease host <verb>` that ended, or its failure with the code of its error object.
pub(crate) fn answer(repo: &Path, verb: &str, output: &Output) -> Result<String, Failed> {
    if output.status.success() {
        return Ok(output.stdout_text());
    }
    let error = error_of(&output.stdout_text());
    let said = error.as_ref().map_or_else(|| output.diagnostic(), |error| error.message.clone());
    Err(Failed {
        code: error.map(|error| error.code),
        message: format!("bd lease host {verb} failed in {}: {said} ({})", repo.display(), output.status),
    })
}

#[derive(Deserialize)]
struct ErrorObject {
    code: String,
    message: String,
}

/// The error object of the last line of `stdout`.
fn error_of(stdout: &str) -> Option<ErrorObject> {
    #[derive(Deserialize)]
    struct Answer {
        error: ErrorObject,
    }
    serde_json::from_str::<Answer>(stdout.lines().last()?)
        .ok()
        .map(|answer| answer.error)
}

/// Parses the last line of the answer of `bd lease host <verb>` as `T`.
pub(crate) fn parse<T: serde::de::DeserializeOwned>(verb: &str, text: &str) -> Result<T, String> {
    let last = text.lines().last().unwrap_or_default();
    serde_json::from_str(last).map_err(|error| format!("bd lease host {verb} answered {last:?}: {error}"))
}

#[cfg(test)]
mod tests;
