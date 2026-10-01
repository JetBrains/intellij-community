//! One invocation: the parse, the command, and the answer on the right descriptor.
//!
//! Without `--json`, stdout carries the digest. With `--json`, stdout carries only the envelope, and the digest goes
//! to stderr. The envelope is `{"ok": true, "data": <summary>}`, or `{"ok": false, "error": {code, message, details}}`
//! with `data` when the session finished but an arm has no valid run. Progress lines always go to stderr.

use std::ffi::OsString;
use std::io::Write;
use std::path::Path;

use bt_core::{Refusal, exit};
use serde::Serialize;

use crate::bench::{self, Host};
use crate::digest;
use crate::launch::Progress;
use crate::options::{Command, Invocation, parse_args};
use crate::summary::Summary;

/// How a refusal names the command to run: the wrapper, by its path in the checkout.
pub(crate) const PROGRAM: &str = "./community/tools/startup-bench.cmd";

/// The JSON envelope.
#[derive(Serialize)]
struct Envelope<'a> {
    ok: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    error: Option<EnvelopeError<'a>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    data: Option<&'a Summary>,
}

#[derive(Serialize)]
struct EnvelopeError<'a> {
    code: &'a str,
    message: &'a str,
    details: Option<&'a serde_json::Value>,
}

/// Runs one invocation and returns the exit code.
pub(crate) fn run<I, T>(argv: I, repo_root: Option<&Path>, working_dir: &Path, stdout: &mut dyn Write, stderr: &mut dyn Write) -> u8
where
    I: IntoIterator<Item = T>,
    T: Into<OsString> + Clone,
{
    let command = match parse_args(argv) {
        Ok(Invocation::Run(command)) => command,
        Ok(Invocation::Help { text, exit }) => {
            write_line(stdout, text.trim_end());
            return exit;
        }
        Err(refusal) => {
            write_line(stderr, &refusal.message);
            return refusal.exit;
        }
    };
    let json = command.json();
    let answer = execute(&command, repo_root, working_dir, stderr);
    report(answer, json, stdout, stderr)
}

fn execute(command: &Command, repo_root: Option<&Path>, working_dir: &Path, stderr: &mut dyn Write) -> Result<Summary, Refusal> {
    let mut progress = Progress::new(stderr);
    match command {
        Command::Replay(args) => {
            let host = Host {
                repo_root: repo_root.unwrap_or(working_dir),
                working_dir,
            };
            bench::replay(&args.session, &host, &mut progress)
        }
        Command::Welcome(args) => bench::welcome(args, &host(repo_root, working_dir)?, &mut progress),
        Command::OpenProject(args) => bench::open_project(args, &host(repo_root, working_dir)?, &mut progress),
    }
}

/// The host of a command that starts the IDE. It needs the checkout root.
fn host<'a>(repo_root: Option<&'a Path>, working_dir: &'a Path) -> Result<Host<'a>, Refusal> {
    let Some(repo_root) = repo_root.filter(|root| !root.as_os_str().is_empty()) else {
        return Err(Refusal::new(
            "repo_root_unresolved",
            exit::USAGE,
            format!("BUILD_WORKSPACE_DIRECTORY is not set; run {PROGRAM}, which exports it"),
        ));
    };
    Ok(Host { repo_root, working_dir })
}

/// Writes the answer and returns the exit code.
pub(crate) fn report(answer: Result<Summary, Refusal>, json: bool, stdout: &mut dyn Write, stderr: &mut dyn Write) -> u8 {
    let summary = match answer {
        Ok(summary) => summary,
        Err(refusal) => {
            if json {
                let envelope = Envelope {
                    ok: false,
                    error: Some(EnvelopeError {
                        code: &refusal.code,
                        message: &refusal.message,
                        details: refusal.details.as_ref(),
                    }),
                    data: None,
                };
                write_line(stdout, &encode(&envelope));
            }
            write_line(stderr, &refusal.message);
            return refusal.exit;
        }
    };
    let text = digest::render(&summary);
    let failed: Vec<&str> = summary.arms_without_valid_run().iter().map(|arm| arm.label()).collect();
    let refusal = (!failed.is_empty()).then(|| {
        Refusal::new(
            "no_valid_run",
            exit::USAGE,
            format!("no run passed the gate in the arm {}; see the reasons above", failed.join(", ")),
        )
    });
    if json {
        let envelope = Envelope {
            ok: refusal.is_none(),
            error: refusal.as_ref().map(|refusal| EnvelopeError {
                code: &refusal.code,
                message: &refusal.message,
                details: None,
            }),
            data: Some(&summary),
        };
        write_line(stdout, &encode(&envelope));
        write_line(stderr, &text);
    } else {
        write_line(stdout, &text);
    }
    match refusal {
        Some(refusal) => {
            write_line(stderr, &refusal.message);
            refusal.exit
        }
        None => exit::GREEN,
    }
}

/// An envelope that does not encode is a bug of this tool, and the text says so where the caller reads.
fn encode(envelope: &Envelope<'_>) -> String {
    serde_json::to_string(envelope).unwrap_or_else(|error| format!("unencodable: {error}"))
}

/// A failed write to stdout leaves nowhere to report it, so the error is dropped.
fn write_line(out: &mut dyn Write, text: &str) {
    let _ = writeln!(out, "{text}");
    let _ = out.flush();
}

#[cfg(test)]
mod tests;
