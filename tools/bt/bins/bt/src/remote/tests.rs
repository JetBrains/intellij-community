use std::ffi::OsString;

use pretty_assertions::assert_eq;

use super::fake::{FakeLink, RecordedRun, host_refused};
use super::*;
use crate::execute::execute_with;
use bt_core::fake::{FakeRuntime, REPO_ROOT, area_files, refusal};

const COMMIT: &str = "0123456789abcdef0123456789abcdef01234567";

/// A green payload as the host writes it with `--json`.
const PAYLOAD: &str = r#"{"status":"PASS","exitCode":0,"durationMs":5,"cachedTargets":0,"ranTargets":1,"targets":[],"failures":[],"flaky":[],"errors":[],"degraded":false}"#;

/// The tree of [`COMMIT`].
const TREE: &str = "fedcba9876543210fedcba9876543210fedcba98";

/// Where the check records of the fake checkout go.
const RECORDS: &str = "/repo/.git/bd-checks.jsonl";

/// A checkout with `bt.json`, whose git knows the tree of [`COMMIT`].
fn checkout() -> FakeRuntime {
    let fake = FakeRuntime::new([]);
    for (path, text) in area_files() {
        fake.put(&path, &text);
    }
    fake.state().commit_trees.insert(COMMIT.to_owned(), TREE.to_owned());
    fake
}

fn records(fake: &FakeRuntime) -> Option<String> {
    fake.state().files.get(RECORDS).cloned()
}

/// A link whose run ends with `code`, `stdout` and the fetched files.
fn link_ending(code: i32, stdout: &str, fetched: &[&str]) -> FakeLink {
    let link = FakeLink::default();
    link.answer_run(Ok(HostOutcome {
        commit: COMMIT.to_owned(),
        code,
        stdout: stdout.to_owned(),
        fetched: fetched.iter().map(|path| (*path).to_owned()).collect(),
    }));
    link
}

fn outcome(argv: &[&str], fake: &FakeRuntime, link: &FakeLink) -> Outcome {
    execute_with(argv, fake, link).unwrap_or_else(|failure| panic!("{argv:?} refused: {failure:?}"))
}

#[test]
fn the_flags_of_the_host_stay_here_and_the_rest_goes_to_the_host() {
    let argv: Vec<OsString> = ["--host", "studio", "FooTest", "--host-repo=~/r", "--json", "--", "--host", "x"]
        .into_iter()
        .map(OsString::from)
        .collect();
    assert_eq!(host_args(&argv), ["FooTest", "--json", "--", "--host", "x"]);

    let after: Vec<OsString> = ["--lane", "fast", "--host=studio", "--host-repo", "~/r"]
        .into_iter()
        .map(OsString::from)
        .collect();
    assert_eq!(host_args(&after), ["--lane", "fast"]);
}

/// Each code of bt keeps its meaning; an interrupt, a refusal of bd-host and an unknown code are infrastructure.
#[test]
fn the_exit_code_of_the_host_maps_one_to_one() {
    for code in [0, 2, 3, 4, 5, 6] {
        assert_eq!(i32::from(local_exit(code)), code);
    }
    for code in [1, 7, 75, 130, 255, -1] {
        assert_eq!(local_exit(code), exit::INFRA, "{code}");
    }
}

#[test]
fn a_run_goes_to_bd_host_with_the_wrapper_and_answers_its_payload() {
    let fake = checkout();
    let link = link_ending(0, &format!("{PAYLOAD}\n"), &[]);
    let answer = outcome(&["AgentLaunchTokensTest", "--host", "studio", "--json"], &fake, &link);
    assert_eq!(answer.exit_code, exit::GREEN);
    assert_eq!(answer.json.map(|payload| payload.ran_targets), Some(1));
    assert_eq!(answer.text, "");
    assert_eq!(
        link.runs(),
        [RecordedRun {
            alias: "studio".to_owned(),
            checkout: REPO_ROOT.into(),
            repo: None,
            command: ["./community/tools/bt.cmd", "AgentLaunchTokensTest", "--json"]
                .map(str::to_owned)
                .to_vec(),
            fetch: vec!["out/bt-runs".to_owned()],
        }]
    );
    assert!(
        fake.spawned().is_empty(),
        "bt starts no child of its runtime for a run: {:#?}",
        fake.spawned()
    );
    assert_eq!(fake.state().stderr, ["> studio: bt AgentLaunchTokensTest --json"]);
}

/// bd-host writes the steps of the run to the stderr itself, so bt adds no line of them. Under `--json` the stdout
/// stays the one payload of the host.
#[test]
fn the_payload_stays_alone_on_the_stdout() {
    let fake = checkout();
    let link = link_ending(0, &format!("{PAYLOAD}\n"), &[]);
    let answer = execute_with(["AgentLaunchTokensTest", "--host", "studio", "--json"], &fake, &link);
    assert_eq!(fake.state().stderr, ["> studio: bt AgentLaunchTokensTest --json"]);
    let (mut stdout, mut stderr) = (Vec::new(), Vec::new());
    assert_eq!(crate::cli::report(answer, &mut stdout, &mut stderr), exit::GREEN);
    let stdout = String::from_utf8(stdout).expect("UTF-8");
    assert_eq!(stdout.lines().count(), 1, "{stdout}");
    assert_eq!(
        serde_json::from_str::<serde_json::Value>(&stdout).expect("one JSON payload"),
        serde_json::from_str::<serde_json::Value>(PAYLOAD).expect("the payload of the host")
    );
    assert!(stderr.is_empty(), "{}", String::from_utf8_lossy(&stderr));
}

#[test]
fn the_repository_of_the_command_line_reaches_bd_host() {
    let fake = checkout();
    let link = link_ending(0, "PASS\n", &[]);
    outcome(&["--host", "studio", "--host-repo", "/w/idea", "FooTest"], &fake, &link);
    let run = &link.runs()[0];
    assert_eq!(run.repo.as_deref(), Some("/w/idea"));
    assert_eq!(run.command, ["./community/tools/bt.cmd", "FooTest"]);
}

/// A failed run answers the code of the host, and its kept logs that came back are named once per run directory.
#[test]
fn a_failed_run_answers_the_host_code_and_names_the_copied_logs() {
    let digest = "FAIL  1 of 3 tests failed\nkept   out/bt-runs/0000000002000  (the logs of each failed shard)\n";
    let fake = checkout();
    let link = link_ending(
        3,
        digest,
        &[
            "out/bt-runs/0000000002000/shard-1/idea.log",
            "out/bt-runs/0000000002000/shard-2/idea.log",
        ],
    );
    let answer = outcome(&["--host", "studio", "FooTest"], &fake, &link);
    assert_eq!(answer.exit_code, exit::TEST_FAILED);
    assert!(answer.text.starts_with("FAIL  1 of 3"), "{}", answer.text);
    let local = format!("{REPO_ROOT}/out/bt-runs/0000000002000");
    assert!(
        answer.text.ends_with(&format!(
            "kept   out/bt-runs/0000000002000  (the logs of each failed shard)\ncopied {local}  (the kept logs of the run on studio)"
        )),
        "{}",
        answer.text
    );
}

/// A refusal on the host writes no digest: the host printed it on the stderr, and the code stays the host's.
#[test]
fn a_refusal_on_the_host_keeps_its_code() {
    let fake = checkout();
    let link = link_ending(2, "", &[]);
    let failure = refusal(execute_with(["--host", "studio", "--json", "NoSuchTest"], &fake, &link));
    assert_eq!((failure.code.as_ref(), failure.exit), ("host_run_failed", exit::USAGE));
}

/// A refusal of bd-host keeps its code under `host_`, and its details give the exit code of bd-host. Local changes are
/// a usage failure; the rest is infrastructure.
#[test]
fn a_refusal_of_bd_host_maps_by_its_meaning() {
    let fake = checkout();
    let link = FakeLink::default();
    link.answer_run(Err(host_refused(code::LOCAL_CHANGES, "this checkout has local changes")));
    let failure = refusal(execute_with(["--host", "studio", "--lane", "fast"], &fake, &link));
    assert_eq!((failure.code.as_ref(), failure.exit), ("host_local_changes", exit::USAGE));
    assert_eq!(failure.message, "bt --host studio: this checkout has local changes");
    assert_eq!(failure.details_json_text(), Some(r#"{"bd_host_exit":75}"#));

    for code in ["unreachable", "untrusted", "lease_failed", code::NOT_INSTALLED] {
        let link = FakeLink::default();
        link.answer_run(Err(host_refused(code, "said")));
        let failure = refusal(execute_with(["--host", "studio", "--lane", "fast"], &fake, &link));
        assert_eq!(
            (failure.code.as_ref(), failure.exit),
            (format!("host_{code}").as_str(), exit::INFRA)
        );
    }

    let link = FakeLink::default();
    link.answer_run(Err(HostRefusal::new("fetch_failed", 255, "2 files did not come")));
    let failure = refusal(execute_with(["--host", "studio", "--lane", "fast"], &fake, &link));
    assert_eq!((failure.code.as_ref(), failure.exit), ("host_fetch_failed", exit::INFRA));
    assert_eq!(failure.details_json_text(), Some(r#"{"bd_host_exit":255}"#));
}

#[test]
fn host_without_a_selector_is_a_usage_failure() {
    let fake = checkout();
    let link = FakeLink::default();
    let failure = refusal(execute_with(["--host", "studio"], &fake, &link));
    assert_eq!(failure.exit, exit::USAGE);
    assert_eq!(link.calls(), Vec::<String>::new());
}

/// A second Ctrl+C stops the follow. bt leaves by its own infrastructure code, and under `--json` it still writes one
/// payload, which says that the run said nothing about the tests.
#[test]
fn a_second_ctrl_c_is_infrastructure_with_a_json_payload() {
    let fake = checkout();
    let link = FakeLink::default();
    link.answer_run(Err(HostRefusal::new(
        code::INTERRUPTED,
        130,
        "a second Ctrl+C stopped the follow of the run 1-2",
    )));
    let answer = outcome(&["--host", "studio", "--json", "FooTest"], &fake, &link);
    assert_eq!(answer.exit_code, exit::INFRA);
    let payload = answer.json.expect("a payload");
    assert_eq!((payload.status, payload.exit_code), (RunStatus::Infra, exit::INFRA));
    assert_eq!(
        payload.errors,
        ["bt --host studio: a second Ctrl+C stopped the follow of the run 1-2"]
    );

    let failure = refusal(execute_with(["--host", "studio", "FooTest"], &fake, &link));
    assert_eq!((failure.code.as_ref(), failure.exit), ("host_interrupted", exit::INFRA));
}

/// A green run on the host records the tree of the commit that the host ran, with the arguments of bt there.
#[test]
fn a_green_run_on_a_host_records_the_tree_of_the_commit_it_ran() {
    let fake = checkout();
    let link = link_ending(0, "PASS  12 tests\n", &[]);
    outcome(&["--host", "studio", "--lane", "fast"], &fake, &link);
    assert_eq!(
        records(&fake).as_deref(),
        Some(
            format!(
                r#"{{"schema":1,"tree":"{TREE}","head":"{COMMIT}","tool":"bt","command":"test","patterns":["--lane","fast"],"exit":0,"at":1000,"host":"studio"}}
"#
            )
            .as_str()
        )
    );

    let fake = checkout();
    let link = link_ending(0, "BUILD OK  //plugins/air/...  3.0s\n", &[]);
    outcome(&["build", "//plugins/air/...", "--host", "studio"], &fake, &link);
    assert_eq!(link.runs()[0].command, ["./community/tools/bt.cmd", "build", "//plugins/air/..."]);
    let text = records(&fake).expect("a record of the build");
    assert!(text.contains(r#""command":"build","patterns":["//plugins/air/..."]"#), "{text}");
}

/// A red run records nothing, and neither does a run whose report names no commit.
#[test]
fn a_red_run_on_a_host_or_one_without_a_commit_records_nothing() {
    let fake = checkout();
    outcome(
        &["--host", "studio", "FooTest"],
        &fake,
        &link_ending(3, "FAIL  1 of 3 tests failed\n", &[]),
    );
    assert_eq!(records(&fake), None);

    let fake = checkout();
    let link = FakeLink::default();
    link.answer_run(Ok(HostOutcome {
        commit: String::new(),
        code: 0,
        stdout: "PASS\n".to_owned(),
        fetched: Vec::new(),
    }));
    outcome(&["--host", "studio", "FooTest"], &fake, &link);
    assert_eq!(records(&fake), None);
}

#[test]
fn a_host_behind_names_its_own_code() {
    let fake = checkout();
    let link = FakeLink::default();
    link.answer_run(Err(host_refused("behind", "studio has none of the commits near HEAD")));
    let failure = refusal(execute_with(["--host", "studio", "FooTest"], &fake, &link));
    assert_eq!((failure.code.as_ref(), failure.exit), ("host_behind", exit::INFRA));
}
