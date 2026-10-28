//! `bt --host <alias>`: one run of bt on another machine, from the committed HEAD of this checkout, through `bd-host`
//! of Branch Deck. The contract of `bd-host` is `spec/cli/host.spec.md` of Branch Deck.
//!
//! bt starts `bd-host run <alias> --head --fetch out/bt-runs --report-fd 3 -- ./community/tools/bt.cmd <args>`, with
//! the arguments of bt less the flags of this module. `bd-host` refuses a checkout with local changes, because only the
//! commit reaches the host. It sends the git objects that the host lacks, and the agent there runs the command in a
//! host slot that it lends to this run only. The stderr of the host and the lines of `bd-host` stream to the stderr
//! here, the `bd-host: phases:` line at the end included. The stdout of `bd-host` is the digest or the `--json` payload
//! of the host, and bt reads it. `bd-host` writes one JSON report of the run to the descriptor 3: the exit code of the
//! command, or a refusal code. The exit code of the host is the exit code here. The new run directories of
//! `out/bt-runs` come back to `out/bt-runs` here.
//!
//! `bd-host` stays in the process group of bt, so the Ctrl+C of the terminal reaches it, and bt only waits for it. The
//! first Ctrl+C cancels the run on the host, and `bd-host` follows the output to the end. A second Ctrl+C stops the
//! follow at once. Then bt exits with its infrastructure code, and under `--json` with the payload of an
//! infrastructure outcome.

use std::collections::BTreeSet;
use std::ffi::OsString;
use std::path::{Path, PathBuf};

use bt_core::WithDetails;
use bt_core::evidence::RUNS_DIR;
use bt_core::result::RunStatus;
use bt_core::runtime::{Runtime, repo_file};
use bt_core::{Refusal, exit};

use crate::check::{Check, CheckCommand, record_host_run};
use crate::execute::{JsonPayload, Outcome};

pub(crate) mod bd_host;

/// The main repository on the host for `--prepare` when `--host-repo` and `BT_HOST_REPO` name none. A run without
/// them takes the repository of the host file, which `--prepare` writes.
pub(crate) const DEFAULT_HOST_REPO: &str = "~/projects/idea-1";

/// The command of the host run, in the root of the slot.
const COMMAND: &str = "./community/tools/bt.cmd";

/// The flags of this module with a value, which the host run does not get.
const HOST_FLAGS: [&str; 2] = ["--host", "--host-repo"];

/// The switches of this module, which the host run does not get.
const HOST_SWITCHES: [&str; 2] = ["--prepare", "--yes"];

/// The exit code of `bd-host` when the host did not run the command, so a caller can run it locally.
pub(crate) const NOT_RUN: i32 = 75;

/// The refusal codes of `bd-host` that bt acts on, and the codes of bt for a `bd-host` that gave no answer. The table
/// of `bd-host` is "Refusal codes" of `spec/cli/host.spec.md` of Branch Deck.
pub(crate) mod code {
    /// A second Ctrl+C stopped the follow of the run.
    pub(crate) const INTERRUPTED: &str = "interrupted";
    /// `--head` found tracked or untracked changes.
    pub(crate) const LOCAL_CHANGES: &str = "local_changes";
    /// A code of bt: no `bd-host` on the PATH and none in `~/.local/bin`.
    pub(crate) const NOT_INSTALLED: &str = "not_installed";
    /// A code of bt: `bd-host` ended without a report, or with a report that bt cannot read.
    pub(crate) const NO_REPORT: &str = "no_report";
    /// A code of bt: `bd-host setup` exited with another code than 0.
    pub(crate) const SETUP_FAILED: &str = "setup_failed";
}

/// Where a run goes, from the local command line.
pub(crate) struct Host<'a> {
    /// The alias of the host file, which is the ssh alias of `--prepare`.
    pub(crate) alias: &'a str,
    /// `--host-repo`: the main repository on the host.
    pub(crate) repo: Option<&'a str>,
}

/// A refusal of `bd-host`: the code and the message of its report, and its exit code.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct HostRefusal {
    pub(crate) code: String,
    /// The exit code of `bd-host`: [`NOT_RUN`] when the host did not run the command, 255 after the start, 130 for
    /// an interrupt.
    pub(crate) exit: i32,
    pub(crate) message: String,
}

impl HostRefusal {
    pub(crate) fn new(code: &str, exit: i32, message: impl Into<String>) -> Self {
        Self {
            code: code.to_owned(),
            exit,
            message: message.into(),
        }
    }
}

/// One run on the host, as bt asks for it.
pub(crate) struct HostRun<'a> {
    /// The local checkout. `bd-host` runs in it and finds the commit there.
    pub(crate) checkout: &'a Path,
    /// `--repo` of `bd-host run`: the main repository on the host.
    pub(crate) repo: Option<&'a str>,
    pub(crate) command: &'a [String],
    /// The directories whose new entries come back, relative to the slot root.
    pub(crate) fetch: &'a [String],
}

/// A run that the host ran to its end.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct HostOutcome {
    /// The commit that the host ran, or empty when the report names none.
    pub(crate) commit: String,
    /// The exit code of the command.
    pub(crate) code: i32,
    /// The stdout of the command.
    pub(crate) stdout: String,
    /// The files that came back, relative to the checkout root with `/`.
    pub(crate) fetched: Vec<String>,
}

/// The answer of `bd-host hello <alias> --json`.
#[derive(Clone, Debug, PartialEq, Eq, serde::Deserialize)]
pub(crate) struct HostHello {
    /// The address that answered, such as `192.168.1.20:7742`.
    pub(crate) address: String,
    pub(crate) agent: HostAgent,
    /// Whether `bd-host` here and the agent run the same build.
    pub(crate) same_build: bool,
}

/// The agent of a hello.
#[derive(Clone, Debug, PartialEq, Eq, serde::Deserialize)]
pub(crate) struct HostAgent {
    /// The version of Branch Deck.
    pub(crate) version: String,
    pub(crate) os: String,
    pub(crate) arch: String,
}

/// What `bd-host setup <alias>` gets from bt.
pub(crate) struct HostSetup<'a> {
    pub(crate) alias: &'a str,
    /// The main repository on the host, which the host file names.
    pub(crate) repo: &'a str,
    /// The name of this client in the allow list of the agent.
    pub(crate) name: &'a str,
}

/// The calls of bt to `bd-host`: the child process in production, a script in the tests.
pub(crate) trait HostLink {
    fn run(&self, alias: &str, request: &HostRun<'_>) -> Result<HostOutcome, HostRefusal>;

    fn hello(&self, alias: &str) -> Result<HostHello, HostRefusal>;

    /// Prepares the host over ssh, and answers the stdout of `bd-host setup`.
    fn setup(&self, request: &HostSetup<'_>) -> Result<String, HostRefusal>;
}

/// One run on the host, given as the whole local command line. A green run records the tree of the commit that the
/// host ran, when the report names that commit.
pub(crate) fn run_on_host(
    host: &Host<'_>,
    check: CheckCommand,
    json: bool,
    argv: &[OsString],
    runtime: &dyn Runtime,
    link: &dyn HostLink,
) -> Result<Outcome, Refusal> {
    let forwarded = host_args(argv);
    runtime.write_error(&format!("> {}: bt {}", host.alias, forwarded.join(" ")));
    let command: Vec<String> = std::iter::once(COMMAND.to_owned()).chain(forwarded).collect();
    // The record names the command apart, so the patterns of a build go without the verb.
    let patterns = match check {
        CheckCommand::Build => command.get(2..),
        CheckCommand::Test => command.get(1..),
    }
    .unwrap_or_default();
    let fetch = [RUNS_DIR.to_owned()];
    let request = HostRun {
        checkout: runtime.repo_root(),
        repo: host.repo,
        command: &command,
        fetch: &fetch,
    };
    let ran = match link.run(host.alias, &request) {
        Ok(ran) => ran,
        Err(refusal) if json && refusal.code == code::INTERRUPTED => {
            let refused = host_refusal(host.alias, &refusal);
            return Ok(Outcome {
                exit_code: refused.exit,
                json: Some(interrupted_payload(&refused.message)),
                text: refused.message,
            });
        }
        Err(refusal) => return Err(host_refusal(host.alias, &refusal)),
    };
    let answered = answer(runtime, host.alias, json, &ran)?;
    if answered.exit_code == exit::GREEN && !ran.commit.is_empty() {
        let check = Check {
            command: check,
            patterns,
            host: Some(host.alias),
        };
        record_host_run(runtime, &ran.commit, &check);
    }
    Ok(answered)
}

/// The `--json` payload of a run that a second Ctrl+C stopped: an infrastructure outcome that says nothing about the
/// tests, with the reason in `errors`. A caller that reads stdout still gets one payload.
fn interrupted_payload(message: &str) -> JsonPayload {
    JsonPayload {
        status: RunStatus::Infra,
        exit_code: exit::INFRA,
        duration_ms: 0,
        cached_targets: 0,
        ran_targets: 0,
        targets: Vec::new(),
        failures: Vec::new(),
        flaky: Vec::new(),
        errors: vec![message.to_owned()],
        degraded: false,
    }
}

/// The command line of the host: the local one less the flags of this module. Everything after `--` is bazel's.
pub(crate) fn host_args(argv: &[OsString]) -> Vec<String> {
    let mut forwarded = Vec::new();
    let mut arguments = argv.iter().map(|argument| argument.to_string_lossy().into_owned());
    while let Some(argument) = arguments.next() {
        if argument == "--" {
            forwarded.push(argument);
            forwarded.extend(arguments.by_ref());
            break;
        }
        if HOST_FLAGS.contains(&argument.as_str()) {
            arguments.next();
            continue;
        }
        if HOST_SWITCHES.contains(&argument.as_str()) {
            continue;
        }
        let is_host_flag = HOST_FLAGS
            .iter()
            .any(|flag| argument.strip_prefix(flag).is_some_and(|rest| rest.starts_with('=')));
        if !is_host_flag {
            forwarded.push(argument);
        }
    }
    forwarded
}

/// A refusal of `bd-host` as a refusal of bt. The code keeps its meaning under the prefix `host_`, and the details
/// give the exit code of `bd-host`, so a caller sees whether the command ran. Local changes are a usage failure, as a
/// selector that resolves to nothing is. Every other refusal says nothing about the tests, so it is infrastructure.
pub(crate) fn host_refusal(alias: &str, refusal: &HostRefusal) -> Refusal {
    let exit = if refusal.code == code::LOCAL_CHANGES {
        exit::USAGE
    } else {
        exit::INFRA
    };
    Refusal::new(
        format!("host_{}", refusal.code),
        exit,
        format!("bt --host {alias}: {}", refusal.message),
    )
    .with_details(serde_json::json!({ "bd_host_exit": refusal.exit }))
}

/// The local copies of the run directories that came back: one per name under `out/bt-runs`.
fn copied_runs(runtime: &dyn Runtime, fetched: &[String]) -> Vec<PathBuf> {
    let names: BTreeSet<&str> = fetched
        .iter()
        .filter_map(|path| path.strip_prefix(RUNS_DIR)?.strip_prefix('/')?.split('/').next())
        .filter(|name| !name.is_empty())
        .collect();
    names
        .into_iter()
        .map(|name| repo_file(runtime, &format!("{RUNS_DIR}/{name}")))
        .collect()
}

/// The outcome of the run on the host: its digest or payload, its exit code, and the local copies of its evidence.
fn answer(runtime: &dyn Runtime, host: &str, json: bool, ran: &HostOutcome) -> Result<Outcome, Refusal> {
    let exit_code = local_exit(ran.code);
    let notes: Vec<String> = copied_runs(runtime, &ran.fetched)
        .iter()
        .map(|path| format!("copied {}  (the kept logs of the run on {host})", path.display()))
        .collect();
    let stdout = ran.stdout.trim_end();
    let silent = |missing: &str| {
        Refusal::new(
            "host_run_failed",
            exit_code,
            format!(
                "the run on {host} exited with {} and wrote {missing}; its stderr is above",
                ran.code
            ),
        )
    };
    if json {
        let payload: JsonPayload = serde_json::from_str(stdout).map_err(|error| silent(&format!("no JSON payload ({error})")))?;
        return Ok(Outcome {
            exit_code,
            text: notes.join("\n"),
            json: Some(payload),
        });
    }
    if stdout.is_empty() {
        return Err(silent("no digest"));
    }
    let mut text = stdout.to_owned();
    for note in &notes {
        text.push('\n');
        text.push_str(note);
    }
    Ok(Outcome {
        exit_code,
        text,
        json: None,
    })
}

/// The exit code of the host as an exit code of bt: each code of bt maps to itself, and any other code, such as the
/// 130 of an interrupt, is [`exit::INFRA`].
pub(crate) fn local_exit(code: i32) -> u8 {
    match u8::try_from(code) {
        Ok(code @ (exit::GREEN | exit::USAGE | exit::TEST_FAILED | exit::NO_TESTS | exit::BUILD_FAILED | exit::INFRA)) => code,
        _ => exit::INFRA,
    }
}

#[cfg(test)]
pub(crate) mod fake;
#[cfg(test)]
mod tests;
