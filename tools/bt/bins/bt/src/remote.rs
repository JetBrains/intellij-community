//! `bt --host <alias>`: one run of bt on another machine, from the committed HEAD of this checkout, over yonder.
//!
//! `yonder-client` does the run. It refuses a checkout with local changes, because only the commit reaches the host.
//! It sends the git objects that the host lacks, and the agent there runs `./community/tools/bt.cmd` with the same
//! arguments, less the flags of this module, in the warm Branch Deck lease of this checkout. The stderr of the host
//! streams here as it comes. The stdout is the digest or the `--json` payload, and the exit code of the host is the
//! exit code here. The new run directories of `out/bt-runs` come back to `out/bt-runs` here. Ctrl+C cancels the run on
//! the host. A second Ctrl+C stops the follow at once with exit 6, and under `--json` with the payload of an
//! infrastructure outcome. A run outlives a lost connection, and the client follows it again. `--release` returns the lease.

use std::collections::BTreeSet;
use std::ffi::OsString;
use std::path::PathBuf;

use bt_core::evidence::RUNS_DIR;
use bt_core::result::RunStatus;
use bt_core::runtime::{Runtime, repo_file};
use bt_core::{Refusal, exit};
use yonder_client::{AgentInfo, Client, Config, Leased, OsProcesses, PrepareRequest, Prepared, RunOutcome, RunRequest, interrupt};

use crate::execute::{JsonPayload, Outcome};

/// The main repository on the host for `--prepare` when `--host-repo` and `BT_HOST_REPO` name none. A run without
/// them takes the repository of the host file, which `--prepare` writes.
pub(crate) const DEFAULT_HOST_REPO: &str = "~/projects/idea-1";

/// How long a lease of another checkout may stay unused on the host before a run returns it, when
/// `--host-idle-hours` and `BT_HOST_IDLE_HOURS` name no count.
pub(crate) const DEFAULT_IDLE_HOURS: u32 = 8;

/// The command of the host run, in the root of the slot.
const COMMAND: &str = "./community/tools/bt.cmd";

/// The prefix of the lease holder of a checkout.
const HOLDER_PREFIX: &str = "bt-remote";

/// The flags of this module with a value, which the host run does not get.
const HOST_FLAGS: [&str; 3] = ["--host", "--host-repo", "--host-idle-hours"];

/// The switches of this module, which the host run does not get.
const HOST_SWITCHES: [&str; 4] = ["--release", "--prepare", "--yes", "--take-lease"];

/// Where a run goes, from the local command line.
pub(crate) struct Host<'a> {
    /// The alias of the host file, which is the ssh alias of `--prepare`.
    pub(crate) alias: &'a str,
    /// `--host-repo`: the main repository on the host.
    pub(crate) repo: Option<&'a str>,
    pub(crate) idle_hours: u32,
}

/// The calls to the agent of a host: `yonder-client` in production, a script in the tests.
pub(crate) trait HostLink {
    fn run(&self, alias: &str, request: &RunRequest<'_>) -> Result<RunOutcome, yonder_client::Refusal>;

    fn lease(&self, alias: &str, repo: Option<&str>, holder: &str) -> Result<Leased, yonder_client::Refusal>;

    fn release(&self, alias: &str, repo: Option<&str>, holder: &str) -> Result<String, yonder_client::Refusal>;

    fn hello(&self, alias: &str) -> Result<AgentInfo, yonder_client::Refusal>;

    fn prepare(&self, request: &PrepareRequest<'_>) -> Result<Prepared, yonder_client::Refusal>;
}

/// The production link: the client directory `~/.config/yonder`, the child processes of the system, the stderr of
/// bt, and the Ctrl+C of the terminal.
pub(crate) struct Yonder<'r> {
    runtime: &'r dyn Runtime,
}

impl<'r> Yonder<'r> {
    pub(crate) const fn new(runtime: &'r dyn Runtime) -> Self {
        Self { runtime }
    }

    fn with_client<T>(&self, call: impl FnOnce(&Client<'_>) -> Result<T, yonder_client::Refusal>) -> Result<T, yonder_client::Refusal> {
        let home = self.runtime.home_dir().ok_or_else(|| {
            yonder_client::Refusal::new(
                yonder_client::code::CONFIG,
                yonder_client::Exit::Refused,
                "no home directory for ~/.config/yonder",
            )
        })?;
        let say = |line: &str| self.runtime.write_error(line);
        let presses = interrupt::presses;
        let client = Client::new(Config::in_home(&home), &OsProcesses, &say, &presses);
        call(&client)
    }
}

impl HostLink for Yonder<'_> {
    fn run(&self, alias: &str, request: &RunRequest<'_>) -> Result<RunOutcome, yonder_client::Refusal> {
        if let Err(said) = interrupt::install() {
            self.runtime
                .write_error(&format!("bt --host: {said}; Ctrl+C ends bt, and the run goes on on {alias}"));
        }
        self.with_client(|client| client.run(alias, request))
    }

    fn lease(&self, alias: &str, repo: Option<&str>, holder: &str) -> Result<Leased, yonder_client::Refusal> {
        self.with_client(|client| client.lease(alias, repo, holder))
    }

    fn release(&self, alias: &str, repo: Option<&str>, holder: &str) -> Result<String, yonder_client::Refusal> {
        self.with_client(|client| client.release(alias, repo, holder))
    }

    fn hello(&self, alias: &str) -> Result<AgentInfo, yonder_client::Refusal> {
        self.with_client(|client| client.hello(alias))
    }

    fn prepare(&self, request: &PrepareRequest<'_>) -> Result<Prepared, yonder_client::Refusal> {
        self.with_client(|client| client.prepare(request))
    }
}

/// One run on the host, given as the whole local command line.
pub(crate) fn run_on_host(
    host: &Host<'_>,
    json: bool,
    argv: &[OsString],
    runtime: &dyn Runtime,
    link: &dyn HostLink,
) -> Result<Outcome, Refusal> {
    let forwarded = host_args(argv);
    runtime.write_error(&format!("> {}: bt {}", host.alias, forwarded.join(" ")));
    let command: Vec<String> = std::iter::once(COMMAND.to_owned()).chain(forwarded).collect();
    let holder = holder(runtime);
    let fetch = [RUNS_DIR.to_owned()];
    let request = RunRequest {
        checkout: runtime.repo_root(),
        repo: host.repo,
        holder: &holder,
        command: &command,
        fetch: &fetch,
        idle_hours: host.idle_hours,
    };
    let ran = match link.run(host.alias, &request) {
        Ok(ran) => ran,
        Err(refusal) if json && refusal.code == yonder_client::code::INTERRUPTED => {
            let refused = host_refusal(host.alias, &refusal);
            return Ok(Outcome {
                exit_code: refused.exit,
                json: Some(interrupted_payload(&refused.message)),
                text: refused.message,
            });
        }
        Err(refusal) => return Err(host_refusal(host.alias, &refusal)),
    };
    answer(runtime, host.alias, json, &ran)
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

/// Returns the lease that the runs of this checkout keep on the host.
pub(crate) fn release_on_host(host: &Host<'_>, runtime: &dyn Runtime, link: &dyn HostLink) -> Result<Outcome, Refusal> {
    let said = link
        .release(host.alias, host.repo, &holder(runtime))
        .map_err(|refusal| host_refusal(host.alias, &refusal))?;
    Ok(Outcome {
        exit_code: exit::GREEN,
        text: said,
        json: None,
    })
}

/// The lease holder of this checkout on a host: `bt-remote-<hash of the checkout path>`, one per checkout, so two
/// checkouts never share a slot there.
pub(crate) fn holder(runtime: &dyn Runtime) -> String {
    yonder_client::holder(HOLDER_PREFIX, runtime.repo_root())
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

/// A refusal of yonder as a refusal of bt. The code keeps its meaning under the prefix `host_`. Local changes are a
/// usage failure, as a selector that resolves to nothing is. Every other refusal says nothing about the tests, so it
/// is infrastructure.
pub(crate) fn host_refusal(alias: &str, refusal: &yonder_client::Refusal) -> Refusal {
    let exit = if refusal.code == yonder_client::code::LOCAL_CHANGES {
        exit::USAGE
    } else {
        exit::INFRA
    };
    Refusal::new(
        format!("host_{}", refusal.code),
        exit,
        format!("bt --host {alias}: {}", refusal.message),
    )
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
fn answer(runtime: &dyn Runtime, host: &str, json: bool, ran: &RunOutcome) -> Result<Outcome, Refusal> {
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
