//! The link against a fake `bd-host`: a shell script that writes its arguments to a file and answers as the script
//! of each test says. The link runs on Unix only.
#![cfg(unix)]

use std::collections::BTreeSet;
use std::os::unix::fs::PermissionsExt as _;

use pretty_assertions::assert_eq;

use super::*;
use crate::execute::execute_with;
use crate::remote::{HostAgent, HostRun, code};
use bt_core::exit;
use bt_core::fake::{FakeRuntime, area_files, refusal};
use bt_core::runtime::Platform;

/// The golden JSON of the report of a run: the block `json golden=run-report` of "The report of a run" in
/// `spec/cli/host.spec.md` of Branch Deck.
const GOLDEN_REPORT: &str = r#"{
  "schema": 1,
  "alias": "studio",
  "run": "1793181600000-4242",
  "commit": "8d4b3790b5c0e7f1a2d3c4b5a6978877665544aa",
  "head": "8d4b3790b5c0e7f1a2d3c4b5a6978877665544aa",
  "working_tree": false,
  "repository": "~/projects/idea-1",
  "exit": 0,
  "command_exit": 0,
  "refusal": null,
  "phases": [
    { "name": "pack", "ms": 3100 },
    { "name": "upload", "ms": 2000 },
    { "name": "slot", "ms": 120 },
    { "name": "pack_wait", "ms": 0 },
    { "name": "checkout", "ms": 4200 },
    { "name": "prepare", "ms": 0 },
    { "name": "command", "ms": 600000 },
    { "name": "stage", "ms": 800 },
    { "name": "fetch", "ms": 900 },
    { "name": "total", "ms": 612000 }
  ],
  "fetched": ["out/bt-runs/20261028-100000-0001/summary.json"],
  "applied_files": null
}"#;

/// The golden JSON of a hello: the block `json golden=hello` of "hello" in `spec/cli/host.spec.md` of Branch Deck.
const GOLDEN_HELLO: &str = r#"{
  "schema": 1,
  "alias": "studio",
  "address": "192.168.1.20:7742",
  "agent": {
    "version": "0.37.0",
    "build": "5e1f0c3a9b7d4e2f8a6c1b3d5e7f9a0b2c4d6e8f1a3b5c7d9e0f2a4b6c8d0e1f",
    "schema": 1,
    "os": "macos",
    "arch": "aarch64",
    "features": ["upgrade"]
  },
  "client": {
    "version": "0.37.0",
    "build": "5e1f0c3a9b7d4e2f8a6c1b3d5e7f9a0b2c4d6e8f1a3b5c7d9e0f2a4b6c8d0e1f",
    "schema": 1
  },
  "same_build": true
}"#;

const COMMIT: &str = "0123456789abcdef0123456789abcdef01234567";

/// A fake `bd-host` in its own directory, which is also the checkout of the run.
struct FakeBdHost {
    dir: tempfile::TempDir,
    binary: PathBuf,
}

impl FakeBdHost {
    /// A script that writes each argument as one line to `argv` beside it, then runs `body`.
    fn new(body: &str) -> Self {
        let dir = tempfile::tempdir().expect("a temporary directory");
        let binary = dir.path().join(BINARY);
        let argv = dir.path().join("argv");
        let script = format!("#!/bin/sh\nprintf '%s\\n' \"$@\" > '{}'\n{body}\n", argv.display());
        std::fs::write(&binary, script).expect("the script");
        std::fs::set_permissions(&binary, std::fs::Permissions::from_mode(0o755)).expect("an executable script");
        Self { dir, binary }
    }

    /// A runtime whose checkout is the directory of the script, with the areas of bt.
    fn runtime(&self) -> FakeRuntime {
        let fake = FakeRuntime::on(Platform::Darwin, self.root(), []);
        for (path, text) in area_files() {
            fake.put(&path, &text);
        }
        fake
    }

    fn root(&self) -> &str {
        self.dir.path().to_str().expect("a UTF-8 temporary directory")
    }

    /// The refusal of a run of the script, in the checkout of the script, without a command.
    fn refused_run(&self) -> HostRefusal {
        let runtime = self.runtime();
        let link = BdHost::with_binary(&runtime, Some(self.binary.clone()));
        link.run("studio", &run_request(self.dir.path(), &[], &[])).expect_err("a refusal")
    }

    /// The arguments of the last call.
    fn argv(&self) -> Vec<String> {
        let text = std::fs::read_to_string(self.dir.path().join("argv")).expect("the arguments of bd-host");
        text.lines().map(str::to_owned).collect()
    }
}

fn run_request<'a>(checkout: &'a Path, command: &'a [String], fetch: &'a [String]) -> HostRun<'a> {
    HostRun {
        checkout,
        repo: Some("/w/idea"),
        command,
        fetch,
    }
}

/// The body of a script that writes `report` to the descriptor 3, `stdout` to the stdout, a line of its own to the
/// stderr, and exits with `exit`.
fn answering(report: &str, stdout: &str, exit: i32) -> String {
    format!("echo 'bd-host: run 1-2 of {COMMIT} on studio' >&2\nprintf '%s' '{stdout}'\nprintf '%s\\n' '{report}' >&3\nexit {exit}")
}

fn report(exit: i32, command_exit: Option<i32>, refusal: Option<(&str, &str)>) -> String {
    serde_json::json!({
        "schema": 1,
        "alias": "studio",
        "commit": COMMIT,
        "exit": exit,
        "command_exit": command_exit,
        "refusal": refusal.map(|(code, message)| serde_json::json!({ "code": code, "message": message })),
        "phases": [{ "name": "total", "ms": 10 }],
        "fetched": ["out/bt-runs/1/shard-1/idea.log"],
    })
    .to_string()
}

#[test]
fn a_run_passes_the_contract_arguments_and_answers_the_stdout_and_the_report() {
    let fake = FakeBdHost::new(&answering(&report(3, Some(3), None), "FAIL  1 of 3 tests failed\n", 3));
    let runtime = fake.runtime();
    let link = BdHost::with_binary(&runtime, Some(fake.binary.clone()));
    let command = ["./community/tools/bt.cmd".to_owned(), "FooTest".to_owned()];
    let fetch = ["out/bt-runs".to_owned()];
    let ran = link
        .run("studio", &run_request(fake.dir.path(), &command, &fetch))
        .expect("a run to its end");
    assert_eq!(
        ran,
        HostOutcome {
            commit: COMMIT.to_owned(),
            code: 3,
            stdout: "FAIL  1 of 3 tests failed\n".to_owned(),
            fetched: vec!["out/bt-runs/1/shard-1/idea.log".to_owned()],
        },
        "the stdout holds only the stdout of the command; the line of bd-host went to the stderr"
    );
    assert_eq!(
        fake.argv(),
        [
            "run",
            "studio",
            "--head",
            "--nice",
            "0",
            "--no-outputs",
            "--fetch",
            "out/bt-runs",
            "--repo",
            "/w/idea",
            "--report-fd",
            "3",
            "--",
            "./community/tools/bt.cmd",
            "FooTest"
        ]
    );
}

/// The exit code of the command is the exit code of bt, and the run directory that came back is named.
#[test]
fn the_exit_code_of_the_command_passes_through_bt() {
    let fake = FakeBdHost::new(&answering(&report(3, Some(3), None), "FAIL  1 of 3 tests failed\n", 3));
    let runtime = fake.runtime();
    let link = BdHost::with_binary(&runtime, Some(fake.binary.clone()));
    let answer = execute_with(["--host", "studio", "FooTest"], &runtime, &link).expect("an answer of the host");
    assert_eq!(answer.exit_code, exit::TEST_FAILED);
    assert_eq!(
        answer.text,
        format!(
            "FAIL  1 of 3 tests failed\ncopied {}/out/bt-runs/1  (the kept logs of the run on studio)",
            fake.root()
        )
    );
    assert_eq!(
        fake.argv(),
        [
            "run",
            "studio",
            "--head",
            "--nice",
            "0",
            "--no-outputs",
            "--fetch",
            "out/bt-runs",
            "--report-fd",
            "3",
            "--",
            "./community/tools/bt.cmd",
            "FooTest"
        ],
        "without --host-repo, bd-host takes the repository of the host file"
    );
}

#[test]
fn a_refusal_of_the_report_is_a_host_refusal_with_the_exit_of_bd_host() {
    let fake = FakeBdHost::new(&answering(
        &report(75, None, Some(("local_changes", "this checkout has local changes"))),
        "",
        75,
    ));
    assert_eq!(
        fake.refused_run(),
        HostRefusal::new(code::LOCAL_CHANGES, 75, "this checkout has local changes")
    );
}

#[test]
fn a_run_without_a_report_is_refused_with_the_exit_of_bd_host() {
    let refused = FakeBdHost::new("echo 'bd-host: lost' >&2\nexit 1").refused_run();
    assert_eq!((refused.code.as_str(), refused.exit), (code::NO_REPORT, 1));
    assert_eq!(
        refused.message,
        "bd-host exited with 1 and wrote no report on the descriptor 3; its stderr is above"
    );
    assert_eq!(
        outcome_of(0, String::new(), r#"{"schema":2,"exit":0}"#)
            .expect_err("another schema")
            .message,
        "bd-host exited with 0 and wrote a report of schema 2, and bt reads 1; its stderr is above"
    );
}

/// Without `bd-host`, the host did not run the command (75), and the message names the install.
#[test]
fn a_missing_bd_host_is_not_run_with_the_install_hint() {
    let fake = FakeBdHost::new("exit 0");
    let runtime = fake.runtime();
    let link = BdHost::with_binary(&runtime, None);
    let refused = link.run("studio", &run_request(fake.dir.path(), &[], &[])).expect_err("a refusal");
    assert_eq!((refused.code.as_str(), refused.exit), (code::NOT_INSTALLED, NOT_RUN));
    assert!(refused.message.contains("`bd install`"), "{}", refused.message);
    assert_eq!(link.hello("studio").expect_err("a refusal").code, code::NOT_INSTALLED);

    let failure = refusal(execute_with(["--host", "studio", "FooTest"], &runtime, &link));
    assert_eq!((failure.code.as_ref(), failure.exit), ("host_not_installed", exit::INFRA));
    assert_eq!(failure.details_json_text(), Some(r#"{"bd_host_exit":75}"#));
}

/// The Ctrl+C of the terminal reaches bt and `bd-host` together. bt lives on, waits for the end, and answers the
/// refusal of the report. The script sends SIGINT to bt, its parent, and to itself, as the terminal does.
#[test]
fn a_ctrl_c_reaches_bd_host_and_bt_waits_for_its_report() {
    let interrupted = report(
        130,
        None,
        Some(("interrupted", "a second Ctrl+C stopped the follow of the run 1-2")),
    );
    let fake = FakeBdHost::new(&format!(
        "report='{interrupted}'\ntrap 'printf \"%s\\n\" \"$report\" >&3; exit 130' INT\nkill -INT \"$PPID\"\nkill -INT $$\nsleep 5\nexit 0"
    ));
    assert_eq!(
        fake.refused_run(),
        HostRefusal::new(code::INTERRUPTED, 130, "a second Ctrl+C stopped the follow of the run 1-2")
    );
}

#[test]
fn the_golden_report_of_the_spec_parses() {
    let ran = outcome_of(0, "PASS\n".to_owned(), GOLDEN_REPORT).expect("a run to its end");
    assert_eq!(
        ran,
        HostOutcome {
            commit: "8d4b3790b5c0e7f1a2d3c4b5a6978877665544aa".to_owned(),
            code: 0,
            stdout: "PASS\n".to_owned(),
            fetched: vec!["out/bt-runs/20261028-100000-0001/summary.json".to_owned()],
        }
    );
}

#[test]
fn the_golden_hello_of_the_spec_parses_and_a_refused_hello_keeps_its_code() {
    assert_eq!(
        hello_of(0, GOLDEN_HELLO).expect("a hello"),
        HostHello {
            address: "192.168.1.20:7742".to_owned(),
            agent: HostAgent {
                version: "0.37.0".to_owned(),
                os: "macos".to_owned(),
                arch: "aarch64".to_owned(),
            },
            same_build: true,
        }
    );
    let refused = r#"{"schema": 1, "alias": "studio", "refusal": {"code": "unreachable", "message": "no address answers"}}"#;
    assert_eq!(
        hello_of(255, refused).expect_err("a refusal"),
        HostRefusal::new("unreachable", 255, "no address answers")
    );
}

#[test]
fn hello_and_setup_pass_their_arguments() {
    let fake = FakeBdHost::new(&format!("printf '%s\\n' '{}'", GOLDEN_HELLO.replace('\n', " ")));
    let runtime = fake.runtime();
    let link = BdHost::with_binary(&runtime, Some(fake.binary.clone()));
    assert_eq!(link.hello("studio").expect("a hello").address, "192.168.1.20:7742");
    assert_eq!(fake.argv(), ["hello", "studio", "--json"]);

    let fake = FakeBdHost::new("echo 'copied bd-host to studio'");
    let runtime = fake.runtime();
    let link = BdHost::with_binary(&runtime, Some(fake.binary.clone()));
    let setup = HostSetup {
        alias: "studio",
        repo: "~/projects/idea-1",
        name: "bt on dev-mac",
    };
    assert_eq!(link.setup(&setup).expect("a setup"), "copied bd-host to studio");
    assert_eq!(
        fake.argv(),
        ["setup", "studio", "--repo", "~/projects/idea-1", "--name", "bt on dev-mac"]
    );

    let fake = FakeBdHost::new("exit 255");
    let runtime = fake.runtime();
    let link = BdHost::with_binary(&runtime, Some(fake.binary));
    assert_eq!(
        link.setup(&setup).expect_err("a refusal"),
        HostRefusal::new(
            code::SETUP_FAILED,
            255,
            "`bd-host setup studio` exited with 255; its stderr is above"
        )
    );
}

#[test]
fn bd_host_comes_from_the_path_else_from_the_local_bin() {
    let files: BTreeSet<PathBuf> = ["/opt/bd/bd-host", "/home/me/.local/bin/bd-host", "/rel/bd-host"]
        .into_iter()
        .map(PathBuf::from)
        .collect();
    let is_file = |path: &Path| files.contains(path);
    let home = Some(Path::new("/home/me"));
    assert_eq!(
        find_binary(Some(OsStr::new("relative:/usr/bin:/opt/bd")), home, is_file),
        Some(PathBuf::from("/opt/bd/bd-host"))
    );
    assert_eq!(
        find_binary(Some(OsStr::new("/usr/bin")), home, is_file),
        Some(PathBuf::from("/home/me/.local/bin/bd-host"))
    );
    assert_eq!(find_binary(None, None, is_file), None);
}
