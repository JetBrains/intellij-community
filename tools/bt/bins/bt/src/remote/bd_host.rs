//! The production [`HostLink`]: the `bd-host` binary of Branch Deck as a child process, with the contract of
//! `spec/cli/host.spec.md` of Branch Deck.
//!
//! bt looks for `bd-host` on the PATH, else at `~/.local/bin/bd-host`, where `bd install` links it. The child gets the
//! stdin and the stderr of bt, and bt reads its stdout. A run writes its report to the descriptor 3, which a shell
//! opens on a temporary file, because std cannot give a child a descriptor of a chosen number. bt keeps a handler of
//! Ctrl+C during a run, so the Ctrl+C of the terminal reaches only `bd-host` with its effect, and bt waits for the end.

use std::ffi::{OsStr, OsString};
use std::io::Read as _;
use std::path::{Path, PathBuf};
use std::process::{Command, ExitStatus, Stdio};
use std::sync::OnceLock;

use bt_core::runtime::Runtime;
use serde::Deserialize;

use super::{HostHello, HostLink, HostOutcome, HostRefusal, HostRun, HostSetup, NOT_RUN, code};

/// The file name of the binary.
const BINARY: &str = "bd-host";

/// The descriptor of the report of a run, as `--report-fd` names it.
const REPORT_FD: &str = "3";

/// The schema of the report and of the hello that bt reads.
const SCHEMA: u32 = 1;

/// The shell line that opens the report file, `$0`, at the descriptor 3 and replaces itself with `bd-host`, `$@`.
const WITH_REPORT_FD: &str = r#"exec "$@" 3>"$0""#;

/// What bt says when it finds no `bd-host`.
const INSTALL_HINT: &str = "run `bd install` of Branch Deck, which links ~/.local/bin/bd-host";

/// The link to one `bd-host` binary.
pub(crate) struct BdHost<'r> {
    runtime: &'r dyn Runtime,
    /// `None` when neither the PATH nor `~/.local/bin` has `bd-host`.
    binary: Option<PathBuf>,
}

impl<'r> BdHost<'r> {
    /// The link to the `bd-host` of the PATH, else of `~/.local/bin`.
    pub(crate) fn new(runtime: &'r dyn Runtime) -> Self {
        let path = std::env::var_os("PATH");
        let binary = find_binary(path.as_deref(), runtime.home_dir().as_deref(), Path::is_file);
        Self::with_binary(runtime, binary)
    }

    /// The link to the binary `binary`, or to none.
    pub(crate) const fn with_binary(runtime: &'r dyn Runtime, binary: Option<PathBuf>) -> Self {
        Self { runtime, binary }
    }

    fn binary(&self) -> Result<&Path, HostRefusal> {
        if cfg!(windows) {
            return Err(HostRefusal::new(
                code::NOT_INSTALLED,
                NOT_RUN,
                "bt --host runs on macOS and Linux only, because bd-host writes its report to a Unix descriptor",
            ));
        }
        self.binary.as_deref().ok_or_else(|| {
            HostRefusal::new(
                code::NOT_INSTALLED,
                NOT_RUN,
                format!("no {BINARY} on the PATH and none at ~/.local/bin/{BINARY}; {INSTALL_HINT}"),
            )
        })
    }

    /// Keeps bt alive on a Ctrl+C, so it waits for `bd-host`, which gets the Ctrl+C with the process group. Once per
    /// process: the handler only takes the signal.
    fn wait_through_ctrl_c(&self, alias: &str) {
        static INSTALLED: OnceLock<Result<(), String>> = OnceLock::new();
        let installed = INSTALLED.get_or_init(|| ctrlc::set_handler(|| {}).map_err(|error| error.to_string()));
        if let Err(said) = installed {
            self.runtime.write_error(&format!(
                "bt --host: cannot handle Ctrl+C ({said}); Ctrl+C ends bt, and the run goes on on {alias}"
            ));
        }
    }
}

impl HostLink for BdHost<'_> {
    fn run(&self, alias: &str, request: &HostRun<'_>) -> Result<HostOutcome, HostRefusal> {
        let binary = self.binary()?;
        let report = tempfile::Builder::new()
            .prefix("bt-host-report-")
            .tempfile()
            .map_err(|error| HostRefusal::new(code::NO_REPORT, NOT_RUN, format!("cannot make the file of the report: {error}")))?;
        let mut command = Command::new("/bin/sh");
        command
            .arg("-c")
            .arg(WITH_REPORT_FD)
            .arg(report.path())
            .arg(binary)
            .args(run_args(alias, request))
            .current_dir(request.checkout);
        self.wait_through_ctrl_c(alias);
        let (exit, stdout) = run_to_end(command, binary)?;
        let text = std::fs::read_to_string(report.path()).unwrap_or_default();
        outcome_of(exit, stdout, &text)
    }

    fn hello(&self, alias: &str) -> Result<HostHello, HostRefusal> {
        let binary = self.binary()?;
        let mut command = Command::new(binary);
        command.args(["hello", alias, "--json"]).current_dir(self.runtime.repo_root());
        let (exit, stdout) = run_to_end(command, binary)?;
        hello_of(exit, &stdout)
    }

    fn setup(&self, request: &HostSetup<'_>) -> Result<String, HostRefusal> {
        let binary = self.binary()?;
        let mut command = Command::new(binary);
        command
            .args(["setup", request.alias, "--repo", request.repo, "--name", request.name])
            .current_dir(self.runtime.repo_root());
        let (exit, stdout) = run_to_end(command, binary)?;
        if exit == 0 {
            return Ok(stdout.trim().to_owned());
        }
        Err(HostRefusal::new(
            code::SETUP_FAILED,
            exit,
            format!("`{BINARY} setup {}` exited with {exit}; its stderr is above", request.alias),
        ))
    }
}

/// The arguments of `bd-host run`, after the binary. The defaults of `run` are the ones of a person, so bt names its
/// options in full ("The report of a run" in `spec/cli/host.spec.md` of Branch Deck): niceness 0 keeps the CPU for the
/// tests, and `--no-outputs` keeps the local copies of the outputs of `[host]`.
fn run_args(alias: &str, request: &HostRun<'_>) -> Vec<OsString> {
    let mut args: Vec<OsString> = vec![
        "run".into(),
        alias.into(),
        "--head".into(),
        "--nice".into(),
        "0".into(),
        "--no-outputs".into(),
    ];
    for dir in request.fetch {
        args.push("--fetch".into());
        args.push(dir.into());
    }
    if let Some(repo) = request.repo {
        args.push("--repo".into());
        args.push(repo.into());
    }
    args.push("--report-fd".into());
    args.push(REPORT_FD.into());
    args.push("--".into());
    args.extend(request.command.iter().map(OsString::from));
    args
}

/// The first `bd-host` of the absolute directories of `path`, else the one of `~/.local/bin`.
fn find_binary(path: Option<&OsStr>, home: Option<&Path>, is_file: impl Fn(&Path) -> bool) -> Option<PathBuf> {
    let on_path = path
        .into_iter()
        .flat_map(std::env::split_paths)
        .filter(|dir| dir.is_absolute())
        .map(|dir| dir.join(BINARY));
    let installed = home.map(|home| home.join(".local/bin").join(BINARY));
    on_path.chain(installed).find(|candidate| is_file(candidate))
}

/// Runs a child to its end with the stdin and the stderr of bt, and answers its exit code and its stdout.
fn run_to_end(mut command: Command, binary: &Path) -> Result<(i32, String), HostRefusal> {
    command.stdin(Stdio::inherit()).stdout(Stdio::piped()).stderr(Stdio::inherit());
    let mut child = command.spawn().map_err(|error| {
        HostRefusal::new(
            code::NOT_INSTALLED,
            NOT_RUN,
            format!("cannot start {}: {error}; {INSTALL_HINT}", binary.display()),
        )
    })?;
    let mut bytes = Vec::new();
    if let Some(mut stdout) = child.stdout.take() {
        // What arrived before a broken pipe is what the child said; its exit code and its report tell the rest.
        let _ = stdout.read_to_end(&mut bytes);
    }
    let status = child
        .wait()
        .map_err(|error| HostRefusal::new(code::NO_REPORT, NOT_RUN, format!("cannot wait for {}: {error}", binary.display())))?;
    Ok((exit_code(status), String::from_utf8_lossy(&bytes).into_owned()))
}

/// The exit code of a child, and 128 plus the signal number for a child that a signal ended.
fn exit_code(status: ExitStatus) -> i32 {
    #[cfg(unix)]
    {
        use std::os::unix::process::ExitStatusExt as _;
        if let Some(signal) = status.signal() {
            return 128 + signal;
        }
    }
    status.code().unwrap_or(-1)
}

/// The report of a run on the descriptor 3: "The report of a run" of `spec/cli/host.spec.md` of Branch Deck. bt
/// reads only the fields that it acts on.
#[derive(Deserialize)]
struct RunReport {
    schema: u32,
    /// The exit code of `bd-host`.
    exit: i32,
    /// The exit code of the command, `None` when it did not run or its end is unknown.
    command_exit: Option<i32>,
    commit: Option<String>,
    refusal: Option<ReportRefusal>,
    #[serde(default)]
    fetched: Vec<String>,
}

/// The refusal of a report or of a hello.
#[derive(Deserialize)]
struct ReportRefusal {
    code: String,
    message: String,
}

/// The outcome of `bd-host run` from its exit code, its stdout and the text of its report.
fn outcome_of(exit: i32, stdout: String, report: &str) -> Result<HostOutcome, HostRefusal> {
    let lost = |why: String| {
        HostRefusal::new(
            code::NO_REPORT,
            exit,
            format!("{BINARY} exited with {exit} and {why}; its stderr is above"),
        )
    };
    if report.trim().is_empty() {
        return Err(lost(format!("wrote no report on the descriptor {REPORT_FD}")));
    }
    let report: RunReport = serde_json::from_str(report).map_err(|error| lost(format!("wrote a report that bt cannot read ({error})")))?;
    if report.schema != SCHEMA {
        return Err(lost(format!("wrote a report of schema {}, and bt reads {SCHEMA}", report.schema)));
    }
    if let Some(refusal) = report.refusal {
        return Err(HostRefusal {
            code: refusal.code,
            exit: report.exit,
            message: refusal.message,
        });
    }
    Ok(HostOutcome {
        commit: report.commit.unwrap_or_default(),
        code: report.command_exit.unwrap_or(report.exit),
        stdout,
        fetched: report.fetched,
    })
}

/// The answer of `bd-host hello --json` from its exit code and its stdout: "hello" of `spec/cli/host.spec.md` of
/// Branch Deck.
fn hello_of(exit: i32, stdout: &str) -> Result<HostHello, HostRefusal> {
    let lost = |why: String| HostRefusal::new(code::NO_REPORT, exit, format!("`{BINARY} hello` exited with {exit} and {why}"));
    let value: serde_json::Value = serde_json::from_str(stdout.trim()).map_err(|error| lost(format!("printed no JSON ({error})")))?;
    let schema = value.get("schema").and_then(serde_json::Value::as_u64);
    if schema != Some(u64::from(SCHEMA)) {
        return Err(lost(format!("printed a hello of schema {schema:?}, and bt reads {SCHEMA}")));
    }
    if let Some(refusal) = value.get("refusal").filter(|refusal| !refusal.is_null()) {
        let refusal =
            ReportRefusal::deserialize(refusal).map_err(|error| lost(format!("printed a refusal that bt cannot read ({error})")))?;
        return Err(HostRefusal {
            code: refusal.code,
            exit,
            message: refusal.message,
        });
    }
    HostHello::deserialize(&value).map_err(|error| lost(format!("printed a hello that bt cannot read ({error})")))
}

#[cfg(test)]
mod tests;
