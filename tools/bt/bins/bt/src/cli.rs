//! `bt`'s process boundary: where the repository is, and which descriptor each half of an answer goes to.
//!
//! # `bt` writes no envelope, deliberately
//!
//! The Air UI-lane controller wraps a refusal in `{"schemaVersion":1,"ok":false,…}` and a success in the matching
//! `ok:true` object. `bt` does not, and this is a divergence rather than an omission: its output contract is older
//! and narrower, and it is what an agent's shell script already reads. `bt <selector> --json` writes exactly one
//! payload to stdout and prose to stderr; a refusal writes one bare message line to stderr and leaves by one of
//! `bt`'s own six exit codes. An envelope would put a second JSON object where a caller expects one payload.
//!
//! # stdout carries exactly one thing
//!
//! With `--json` the payload is on stdout and the digest on stderr. Without it the digest is the answer and goes to
//! stdout. Nothing else is ever written there: bazel's own output, the progress lines and an INFRA tail all reach
//! stderr through the runtime's sinks.

use std::ffi::OsString;
use std::io::Write;
use std::path::Path;

use bt_core::Refusal;

use crate::execute::{Outcome, execute};
use bt_core::exit;
use bt_core::os_runtime::{OsRuntime, Spawner};

/// How a refusal names the command to run instead: the wrapper, by its repository-relative path. Not the program's
/// own path, which under Bazel is inside an output tree, and not `bt`, which is not a command anyone can run.
pub(crate) const PROGRAM: &str = "./community/tools/bt.cmd";

/// The whole invocation; answers the status the process should leave with.
///
/// `repo_root` is passed in rather than read here because it is one of the things a running process has that a test
/// cannot be given, and `main.rs` is where those are read. An absent or empty one is a refusal, not a search: every
/// selector, `.iml` and `BUILD.bazel` resolves relative to the checkout root, and guessing it from the working
/// directory would answer "no such test" for a whole tree that is there.
pub(crate) fn run<I, T>(
    argv: I,
    repo_root: Option<&Path>,
    stdout: &mut dyn Write,
    stderr: &mut dyn Write,
) -> u8
where
    I: IntoIterator<Item = T>,
    T: Into<OsString> + Clone,
{
    report(execute_in(argv, repo_root), stdout, stderr)
}

/// Builds the production runtime and runs one invocation.
///
/// The spawn runs children in the checkout: bazel resolves a target pattern against the workspace it was started in,
/// and this binary's own working directory under a `bazel run` launcher is somewhere in an output tree. The two sinks
/// stay the process's own stdout and stderr; the lane controller is the consumer that routes them elsewhere.
fn execute_in<I, T>(argv: I, repo_root: Option<&Path>) -> Result<Outcome, Refusal>
where
    I: IntoIterator<Item = T>,
    T: Into<OsString> + Clone,
{
    let Some(repo_root) = repo_root.filter(|root| !root.as_os_str().is_empty()) else {
        // `bt`'s usage code rather than its infrastructure one: nothing is broken, this binary was started without
        // the one fact the wrapper always exports. Reporting it as infrastructure would say bazel failed.
        return Err(Refusal::new(
            "repo_root_unresolved",
            exit::USAGE,
            format!("BUILD_WORKSPACE_DIRECTORY is not set; run {PROGRAM}, which exports it"),
        ));
    };
    let runtime = OsRuntime::builder(repo_root)
        .spawn(Spawner::ChildIn(repo_root.to_path_buf()))
        .build();
    execute(argv, &runtime)
}

/// Writes one invocation's answer and answers the exit status.
///
/// A refusal is its message line on stderr and leaves by the refusal's own exit status.
pub(crate) fn report(
    answer: Result<Outcome, Refusal>,
    stdout: &mut dyn Write,
    stderr: &mut dyn Write,
) -> u8 {
    let outcome = match answer {
        Ok(outcome) => outcome,
        Err(refusal) => {
            write_line(stderr, &refusal.message);
            return refusal.exit;
        }
    };
    match &outcome.json {
        Some(payload) => {
            // An unencodable payload is a bug in this tool rather than a caller's problem, and the text says so where
            // a caller will see it: on stdout, in place of the payload it was waiting for.
            let encoded = serde_json::to_string(payload)
                .unwrap_or_else(|error| format!("unencodable: {error}"));
            write_line(stdout, &encoded);
            // An empty digest writes nothing, rather than a blank line a caller reading stderr would have to ignore.
            if !outcome.text.is_empty() {
                write_line(stderr, &outcome.text);
            }
        }
        None => write_line(stdout, &outcome.text),
    }
    outcome.exit_code
}

/// A failed write to stdout leaves nowhere to report it, so it is dropped rather than crashed over.
fn write_line(out: &mut dyn Write, text: &str) {
    let _ = writeln!(out, "{text}");
    let _ = out.flush();
}

#[cfg(test)]
mod tests;
