use std::ffi::OsString;

use pretty_assertions::assert_eq;

use super::fake::{FakeLink, RecordedRun, yonder_refusal};
use super::*;
use crate::execute::execute_with;
use bt_core::fake::{FakeRuntime, REPO_ROOT, area_files, refusal};

const COMMIT: &str = "0123456789abcdef0123456789abcdef01234567";

/// A green payload as the host writes it with `--json`.
const PAYLOAD: &str = r#"{"status":"PASS","exitCode":0,"durationMs":5,"cachedTargets":0,"ranTargets":1,"targets":[],"failures":[],"flaky":[],"errors":[],"degraded":false}"#;

/// A checkout with `bt.json`.
fn checkout() -> FakeRuntime {
    let fake = FakeRuntime::new([]);
    for (path, text) in area_files() {
        fake.put(&path, &text);
    }
    fake
}

/// A link whose run ends with `code`, `stdout` and the fetched files.
fn link_ending(code: i32, stdout: &str, fetched: &[&str]) -> FakeLink {
    let link = FakeLink::default();
    link.answer_run(Ok(RunOutcome {
        run: "1000-1".to_owned(),
        commit: COMMIT.to_owned(),
        code,
        stdout: stdout.to_owned(),
        fetched: fetched.iter().map(|path| (*path).to_owned()).collect(),
        phases: Vec::new(),
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

    let after: Vec<OsString> = ["--lane", "fast", "--host=studio", "--host-idle-hours", "2"]
        .into_iter()
        .map(OsString::from)
        .collect();
    assert_eq!(host_args(&after), ["--lane", "fast"]);
}

/// Each code of bt keeps its meaning; an interrupt, a refusal of yonder and an unknown code are infrastructure.
#[test]
fn the_exit_code_of_the_host_maps_one_to_one() {
    for code in [0, 2, 3, 4, 5, 6] {
        assert_eq!(i32::from(local_exit(code)), code);
    }
    for code in [1, 7, 130, 255, -1] {
        assert_eq!(local_exit(code), exit::INFRA, "{code}");
    }
}

#[test]
fn a_run_goes_to_yonder_with_the_wrapper_and_answers_its_payload() {
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
            holder: holder(&fake),
            command: ["./community/tools/bt.cmd", "AgentLaunchTokensTest", "--json"]
                .map(str::to_owned)
                .to_vec(),
            fetch: vec!["out/bt-runs".to_owned()],
            idle_hours: 8,
        }]
    );
    assert!(fake.spawned().is_empty(), "bt starts no child for a run: {:#?}", fake.spawned());
    assert_eq!(fake.state().stderr, ["> studio: bt AgentLaunchTokensTest --json"]);
}

/// The steps of the run go to the stderr in one line. Under `--json` the stdout stays the one payload of the host.
#[test]
fn the_steps_of_a_run_go_to_the_stderr_and_the_payload_stays_alone_on_the_stdout() {
    let fake = checkout();
    let link = FakeLink::default();
    let step = |name: &str, ms: u64| yonder_client::Phase { name: name.to_owned(), ms };
    link.answer_run(Ok(RunOutcome {
        run: "1000-1".to_owned(),
        commit: COMMIT.to_owned(),
        code: 0,
        stdout: format!("{PAYLOAD}\n"),
        fetched: Vec::new(),
        phases: vec![
            step("pack", 3100),
            step("lease", 542_000),
            step("command", 25_100),
            step("total", 613_000),
        ],
    }));
    let answer = execute_with(["AgentLaunchTokensTest", "--host", "studio", "--json"], &fake, &link);
    assert_eq!(
        fake.state().stderr,
        [
            "> studio: bt AgentLaunchTokensTest --json",
            "yonder: phases: pack 3.1s, lease 9m02s, command 25.1s, total 10m13s"
        ]
    );
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
fn the_repository_and_the_idle_hours_of_the_command_line_reach_yonder() {
    let fake = checkout();
    let link = link_ending(0, "PASS\n", &[]);
    outcome(
        &["--host", "studio", "--host-repo", "/w/idea", "--host-idle-hours", "2", "FooTest"],
        &fake,
        &link,
    );
    let run = &link.runs()[0];
    assert_eq!((run.repo.as_deref(), run.idle_hours), (Some("/w/idea"), 2));
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

/// A refusal of yonder keeps its code under `host_`. Local changes are a usage failure; the rest is infrastructure.
#[test]
fn a_refusal_of_yonder_maps_by_its_meaning() {
    let fake = checkout();
    let link = FakeLink::default();
    link.answer_run(Err(yonder_refusal(
        yonder_client::code::LOCAL_CHANGES,
        "this checkout has local changes",
    )));
    let failure = refusal(execute_with(["--host", "studio", "--lane", "fast"], &fake, &link));
    assert_eq!((failure.code.as_ref(), failure.exit), ("host_local_changes", exit::USAGE));
    assert_eq!(failure.message, "bt --host studio: this checkout has local changes");

    for code in [yonder_client::code::UNREACHABLE, yonder_client::code::UNTRUSTED, "lease_failed"] {
        let link = FakeLink::default();
        link.answer_run(Err(yonder_refusal(code, "said")));
        let failure = refusal(execute_with(["--host", "studio", "--lane", "fast"], &fake, &link));
        assert_eq!(
            (failure.code.as_ref(), failure.exit),
            (format!("host_{code}").as_str(), exit::INFRA)
        );
    }
}

#[test]
fn host_without_a_selector_is_a_usage_failure() {
    let fake = checkout();
    let link = FakeLink::default();
    let failure = refusal(execute_with(["--host", "studio"], &fake, &link));
    assert_eq!(failure.exit, exit::USAGE);
    assert!(link.calls().is_empty());
}

/// A release needs no clean checkout: it only returns the lease of this checkout on the host.
#[test]
fn a_release_returns_the_lease_of_this_checkout_and_runs_nothing_else() {
    let fake = checkout();
    let link = FakeLink::default();
    link.answer_release(Ok("released the lease L1 of /pool/1/idea-1".to_owned()));
    let answer = outcome(&["--host", "studio", "--release"], &fake, &link);
    assert_eq!(answer.exit_code, exit::GREEN);
    assert_eq!(answer.text, "released the lease L1 of /pool/1/idea-1");
    assert_eq!(link.calls(), [format!("release studio None {}", holder(&fake))]);
    assert!(fake.spawned().is_empty());

    let failure = refusal(execute_with(["--release"], &checkout(), &FakeLink::default()));
    assert_eq!(failure.exit, exit::USAGE);
}

/// One holder per checkout, named as the leases of the runs over ssh were.
#[test]
fn each_checkout_has_its_own_stable_holder() {
    let first = FakeRuntime::on(bt_core::runtime::Platform::Darwin, "/pool/1/idea-1", []);
    let second = FakeRuntime::on(bt_core::runtime::Platform::Darwin, "/pool/2/idea-1", []);
    assert_eq!(holder(&first), holder(&first));
    assert_ne!(holder(&first), holder(&second));
    assert!(holder(&first).starts_with("bt-remote-"), "{}", holder(&first));
    assert_eq!(holder(&first).len(), "bt-remote-".len() + 12);
}

/// A second Ctrl+C stops the follow. bt leaves by its own infrastructure code, and under `--json` it still writes one
/// payload, which says that the run said nothing about the tests.
#[test]
fn a_second_ctrl_c_is_infrastructure_with_a_json_payload() {
    let fake = checkout();
    let link = FakeLink::default();
    link.answer_run(Err(yonder_refusal(
        yonder_client::code::INTERRUPTED,
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

#[test]
fn a_host_behind_names_its_own_code() {
    let fake = checkout();
    let link = FakeLink::default();
    link.answer_run(Err(yonder_refusal(
        yonder_client::code::BEHIND,
        "studio has none of the commits near HEAD",
    )));
    let failure = refusal(execute_with(["--host", "studio", "FooTest"], &fake, &link));
    assert_eq!((failure.code.as_ref(), failure.exit), ("host_behind", exit::INFRA));
}
