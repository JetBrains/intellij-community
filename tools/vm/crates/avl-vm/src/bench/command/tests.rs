//! The bench verbs as a caller sees them: one invocation of the controller over in-memory writers, with no Bazel and
//! no IDE. `replay` of the fixture session is the whole path from the argv to the envelope.

use std::ffi::OsString;
use std::path::{Path, PathBuf};

use avl_base::{Environment, Exit, Reporter};
use avl_host_sys::Interrupts;
use pretty_assertions::assert_eq;
use serde_json::Value;
use tempfile::TempDir;

use crate::bench::gc::DEFAULT_KEEP;
use crate::bench::options::{
    DEFAULT_HOLD, DEFAULT_LIMIT, DEFAULT_MIN_MS, DEFAULT_OPEN_PROJECT_RUNS, DEFAULT_TARGET, DEFAULT_TOP, DEFAULT_WELCOME_RUNS,
};
use crate::bench::testing::fixture_session;
use crate::cli::PROGRAM;
use crate::controller::run;
use crate::terminal::TerminalFacts;

struct Answer {
    exit: Exit,
    stdout: String,
    stderr: String,
}

/// Runs the controller with every path of its environment under `root`.
async fn invoke(root: &TempDir, argv: &[&str]) -> Answer {
    invoke_with(root, argv, &[]).await
}

/// Runs the controller as [`invoke`] does, with the variables `extra` added to its environment.
async fn invoke_with(root: &TempDir, argv: &[&str], extra: &[(&str, &str)]) -> Answer {
    avl_affected::bridge::install_fixture();
    let path = |relative: &str| root.path().join(relative).to_string_lossy().into_owned();
    let pairs: Vec<(String, String)> = [
        ("HOME", path("")),
        ("BUILD_WORKSPACE_DIRECTORY", path("")),
        ("AIR_VM_RUNTIME_ROOT", path("runtime")),
        ("AIR_VM_IMAGE_ROOT", path("image")),
        ("AIR_VM_HOST_REPO", path("")),
        ("AIR_VM_BAZEL_USER_ROOT", path("bazel-root")),
    ]
    .into_iter()
    .chain(extra.iter().map(|(name, value)| (*name, (*value).to_owned())))
    .map(|(name, value)| (name.to_owned(), value))
    .collect();
    let (reporter, stdout, stderr) = Reporter::in_memory("vm");
    let exit = run(
        argv.iter().map(OsString::from).collect(),
        Environment::from_pairs(pairs),
        reporter,
        TerminalFacts::default(),
        Interrupts::detached(),
    )
    .await;
    Answer {
        exit,
        stdout: stdout.text(),
        stderr: stderr.text(),
    }
}

fn envelope(text: &str) -> Value {
    serde_json::from_str(text.trim_end()).unwrap_or_else(|error| panic!("not one JSON object: {error} ({text:?})"))
}

fn text(session: &Path) -> String {
    session.display().to_string()
}

/// A copy of the fixture session under `<runtime root>/bench/runs`, with the name `name`, so the locator finds it.
fn session_in_runs(root: &TempDir, name: &str) -> PathBuf {
    let session = root.path().join("runtime/bench/runs").join(name);
    avl_testkit::traces::copy_tree(&crate::bench::testing::testdata_dir().join("session"), &session);
    session
}

/// The help that `--help --text` after `bench` and `words` prints.
async fn help_of(root: &TempDir, words: &[&str]) -> String {
    let argv: Vec<&str> = std::iter::once("bench")
        .chain(words.iter().copied())
        .chain(["--help", "--text"])
        .collect();
    let answer = invoke(root, &argv).await;
    assert_eq!(answer.exit, Exit::OK, "{argv:?}: {}", answer.stderr);
    answer.stdout
}

fn assert_contains(text: &str, expected: &[&str]) {
    for part in expected {
        assert!(text.contains(part), "no {part:?} in:\n{text}");
    }
}

// The help of `bench` names the verbs and states the exit codes once. The help of a verb states its grammar and its
// defaults, which clap reads from the declared defaults. A word of the prose is asserted alone, because clap wraps
// the prose at any space.
#[tokio::test]
async fn the_help_of_bench_names_the_verbs_and_the_exit_codes_and_each_verb_its_options() {
    let root = tempfile::tempdir().expect("a temporary root");
    let bench = help_of(&root, &[]).await;
    assert_contains(
        &bench,
        &[
            "welcome",
            "replay",
            "open-project",
            "project",
            "gc",
            "trace",
            "activities",
            "classes",
            "compare",
            "ls",
        ],
    );
    assert_contains(
        &bench,
        &[
            "bench_busy",
            "AIR_VM_BIN",
            "Exit codes:",
            "6   an arm has no run that passed the gate",
            "75  the host is busy, or a session runs",
        ],
    );
    let welcome = help_of(&root, &["welcome"]).await;
    let open_project = help_of(&root, &["open-project"]).await;
    let project = help_of(&root, &["project"]).await;
    for (verb, text) in [("welcome", &welcome), ("open-project", &open_project), ("project", &project)] {
        let usage = format!("Usage: {PROGRAM} bench {verb} [OPTIONS]");
        let hold = format!("[default: {DEFAULT_HOLD}]");
        assert_contains(
            text,
            &[
                &usage,
                &format!("[default: {DEFAULT_TARGET}]"),
                &hold,
                "--max-load <LOAD>",
                "host_busy",
            ],
        );
    }
    assert_contains(&welcome, &["--arm <ARM>", &format!("[default: {DEFAULT_WELCOME_RUNS}]")]);
    assert_contains(&open_project, &["<PROJECT>", &format!("[default: {DEFAULT_OPEN_PROJECT_RUNS}]")]);
    assert_contains(
        &project,
        &[
            "<PROJECT>",
            "`markdown`",
            "restores",
            &format!("[default: {DEFAULT_OPEN_PROJECT_RUNS}]"),
        ],
    );
    let trace = help_of(&root, &["trace"]).await;
    assert_contains(
        &trace,
        &[
            "<SESSION>",
            "--run <DIR>",
            "--from <MS>",
            "--to <MS>",
            "--min <MS>",
            "--all",
            "`waits`",
        ],
    );
    assert_contains(&trace, &[&format!("[default: {DEFAULT_MIN_MS}]"), "`latest`"]);
    let activities = help_of(&root, &["activities"]).await;
    assert_contains(&activities, &["--run <DIR>", "--from <MS>", "--to <MS>", "--top <N>"]);
    assert_contains(&activities, &[&format!("[default: {DEFAULT_TOP}]")]);
    let classes = help_of(&root, &["classes"]).await;
    assert_contains(
        &classes,
        &[
            "<SESSION>",
            "--arm <ARM>",
            "--top <N>",
            "--plugin <ID>",
            "--at <ANCHOR>",
            "non-modal",
            "\"editor highlighting completed\"",
            "quit",
            "class-load.log",
            "JVM",
            &format!("[default: {DEFAULT_TOP}]"),
        ],
    );
    let compare = help_of(&root, &["compare"]).await;
    assert_contains(&compare, &["<SESSION> <SESSION>...", "--expect-different-dist", "same_dist", "A/A"]);
    let ls = help_of(&root, &["ls"]).await;
    assert_contains(&ls, &["--limit <N>", &format!("[default: {DEFAULT_LIMIT}]")]);
    let gc = help_of(&root, &["gc"]).await;
    assert_contains(&gc, &["--keep <N>", &format!("[default: {DEFAULT_KEEP}]")]);
}

#[tokio::test]
async fn replay_answers_the_summary_in_the_envelope_and_the_digest_in_prose() {
    let root = tempfile::tempdir().expect("a temporary root");
    let (_dir, session) = fixture_session();
    let answer = invoke(&root, &["bench", "replay", &text(&session), "--json"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    assert_eq!(answer.stderr, "");
    let envelope = envelope(&answer.stdout);
    assert_eq!(envelope["ok"], true);
    assert_eq!(envelope["command"], "bench");
    let data = &envelope["data"];
    assert_eq!(data["arms"]["nonModal"]["validRuns"], 1);
    assert!(data["arms"]["modal"]["summary"]["welcomeBecameVisible"]["median"].is_number());
    assert_eq!(data["distDigest"].as_str().map(str::len), Some(64));
    let summary: Value = serde_json::from_str(&std::fs::read_to_string(session.join("summary.json")).expect("summary.json")).expect("JSON");
    assert_eq!(&summary, data);

    let answer = invoke(&root, &["bench", "replay", &text(&session), "--text"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    assert!(
        answer.stdout.starts_with("vm bench welcome: //build:idea_dist at 3f337093836d,"),
        "{}",
        answer.stdout
    );
    assert!(answer.stdout.trim_end().ends_with("/summary.json"), "{}", answer.stdout);
}

#[tokio::test]
async fn a_session_without_a_valid_run_leaves_with_tests_failed_and_the_summary_in_the_details() {
    let root = tempfile::tempdir().expect("a temporary root");
    let (_dir, session) = fixture_session();
    std::fs::remove_file(session.join("modal-run-01/fus.jsonl")).expect("a removal");
    let answer = invoke(&root, &["bench", "replay", &text(&session), "--json"]).await;
    assert_eq!(answer.exit, Exit::TESTS_FAILED);
    assert_eq!(answer.stdout, "");
    let envelope = envelope(&answer.stderr);
    assert_eq!(envelope["error"]["code"], "no_valid_run");
    assert_eq!(
        envelope["error"]["message"],
        "no run passed the gate in the arm modal; see the reasons above"
    );
    assert_eq!(envelope["error"]["details"]["arms"]["modal"]["validRuns"], 0);

    let answer = invoke(&root, &["bench", "replay", &text(&session), "--text"]).await;
    assert_eq!(answer.exit, Exit::TESTS_FAILED);
    assert!(answer.stderr.contains("vm: modal run 1 failed: no fus.jsonl"), "{}", answer.stderr);
    assert!(
        answer
            .stderr
            .ends_with("vm: no run passed the gate in the arm modal; see the reasons above\n"),
        "{}",
        answer.stderr
    );
}

#[tokio::test]
async fn replay_refuses_another_directory_and_a_run_of_another_distribution() {
    let root = tempfile::tempdir().expect("a temporary root");
    let answer = invoke(&root, &["bench", "replay", &text(root.path()), "--json"]).await;
    assert_eq!(answer.exit, Exit::USAGE);
    assert_eq!(envelope(&answer.stderr)["error"]["code"], "not_a_session");

    let (_dir, session) = fixture_session();
    let result = session.join("non-modal-run-01/result.json");
    let changed = std::fs::read_to_string(&result)
        .expect("a result")
        .replace("5f1c0d9e2b7a", "0000000000aa");
    std::fs::write(&result, changed).expect("a result");
    let answer = invoke(&root, &["bench", "replay", &text(&session), "--json"]).await;
    assert_eq!(answer.exit, Exit::USAGE);
    let envelope = envelope(&answer.stderr);
    assert_eq!(envelope["error"]["code"], "bench_dist_digest_mismatch");
}

#[cfg(not(target_os = "macos"))]
#[tokio::test]
async fn a_session_runs_on_a_macos_host_only() {
    let root = tempfile::tempdir().expect("a temporary root");
    let answer = invoke(&root, &["bench", "welcome", "--json"]).await;
    assert_eq!(answer.exit, Exit::USAGE);
    assert_eq!(envelope(&answer.stderr)["error"]["code"], "unsupported_os");
}

/// A root that is a Git checkout with a license, and a session directory under it that is not empty. A session there
/// passes the license check, and a session that passes the load check still refuses before the lock and the build.
#[cfg(target_os = "macos")]
fn checkout_with_a_license() -> (TempDir, PathBuf) {
    let root = tempfile::tempdir().expect("a temporary root");
    let git = std::process::Command::new("git")
        .args(["init", "--quiet"])
        .arg(root.path())
        .status()
        .expect("git");
    assert!(git.success(), "git init: {git}");
    let license = root.path().join(crate::bench::session::LICENSE_FILE);
    std::fs::create_dir_all(license.parent().expect("a parent")).expect("the license directory");
    std::fs::write(&license, "key").expect("a license");
    let session = root.path().join("session");
    std::fs::create_dir_all(&session).expect("a session directory");
    std::fs::write(session.join("kept"), "").expect("a file");
    (root, session)
}

/// A session on a host above `--max-load` is the refusal `host_busy` before the lock and the build. Any live macOS
/// host has a 1-minute load above 0.001.
#[cfg(target_os = "macos")]
#[tokio::test]
async fn a_session_above_its_max_load_is_refused_before_the_lock_and_the_build() {
    let (root, session) = checkout_with_a_license();
    let path = std::env::var("PATH").unwrap_or_default();
    let argv = ["bench", "welcome", "--max-load", "0.001", "--session", &text(&session), "--json"];
    let answer = invoke_with(&root, &argv, &[("PATH", &path)]).await;
    assert_eq!(answer.exit, Exit::TEMP_FAIL, "{}", answer.stderr);
    let error = &envelope(&answer.stderr)["error"];
    assert_eq!(error["code"], "host_busy");
    let message = error["message"].as_str().unwrap_or_default();
    assert!(message.starts_with("the 1-minute load is "), "{message}");
    assert!(
        message.ends_with(" CPUs, above --max-load 0.001; wait for the host to settle"),
        "{message}"
    );
    assert!(!root.path().join("runtime/bench").exists(), "the refusal comes before the lock");
    assert!(!session.join("build.log").exists(), "the refusal comes before the build");
}

/// A session with `AIR_VM_BIN` notes the age of the binary before its checks. A verb that starts no IDE does not.
#[cfg(target_os = "macos")]
#[tokio::test]
async fn a_session_notes_the_age_of_the_air_vm_bin_binary_and_a_replay_does_not() {
    let (root, session) = checkout_with_a_license();
    let binary = root.path().join("vm");
    std::fs::write(&binary, "").expect("a binary");
    let path = std::env::var("PATH").unwrap_or_default();
    let extra = [("PATH", path.as_str()), ("AIR_VM_BIN", &text(&binary))];
    // A high limit passes a busy host too, so the default limit does not refuse first.
    let argv = ["bench", "welcome", "--max-load", "1000", "--session", &text(&session), "--text"];
    let answer = invoke_with(&root, &argv, &extra).await;
    assert_eq!(answer.exit, Exit::USAGE, "{}", answer.stderr);
    let note = format!("vm: AIR_VM_BIN {} built ", binary.display());
    assert!(answer.stderr.contains(&note), "{}", answer.stderr);
    assert!(
        answer.stderr.contains("no source under community/tools/vm/crates"),
        "{}",
        answer.stderr
    );
    assert!(
        answer.stderr.contains("is not empty"),
        "the session directory refuses: {}",
        answer.stderr
    );

    let (_dir, replayed) = fixture_session();
    let answer = invoke_with(&root, &["bench", "replay", &text(&replayed), "--text"], &extra).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    assert!(!answer.stderr.contains("AIR_VM_BIN"), "{}", answer.stderr);
}

#[tokio::test]
async fn a_target_that_is_not_a_distribution_is_a_usage_refusal() {
    let root = tempfile::tempdir().expect("a temporary root");
    let answer = invoke(&root, &["bench", "welcome", "--target", "//build:idea", "--json"]).await;
    assert_eq!(answer.exit, Exit::USAGE);
    let code = envelope(&answer.stderr)["error"]["code"].clone();
    let expected = if cfg!(target_os = "macos") { "usage" } else { "unsupported_os" };
    assert_eq!(code, expected);
}

#[tokio::test]
async fn gc_answers_its_report_and_refuses_while_a_session_holds_the_lock() {
    let root = tempfile::tempdir().expect("a temporary root");
    let answer = invoke(&root, &["bench", "gc", "--json"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    let answered = envelope(&answer.stdout);
    assert_eq!(answered["data"]["removed"], serde_json::json!([]));

    let runner = avl_host_sys::Runner::new(std::env::vars(), Interrupts::detached());
    let locks = avl_host_sys::lock::LockManager::new(runner);
    let bench = root.path().join("runtime/bench");
    std::fs::create_dir_all(&bench).expect("the bench directory");
    let _held = locks
        .acquire(&avl_host_sys::Ctx::background(), &bench.join("session.lock"), "a session", "busy")
        .await
        .expect("the session lock");
    let answer = invoke(&root, &["bench", "gc", "--keep", "0", "--json"]).await;
    assert_eq!(answer.exit, Exit::TEMP_FAIL);
    assert_eq!(envelope(&answer.stderr)["error"]["code"], "bench_busy");
}

#[tokio::test]
async fn replay_locates_a_session_by_a_part_of_its_name_and_as_the_latest() {
    let root = tempfile::tempdir().expect("a temporary root");
    let session = session_in_runs(&root, "develar-bench-2104c3b2-bf21");
    let answer = invoke(&root, &["bench", "replay", "latest", "--json"]).await;
    assert_eq!(answer.exit, Exit::USAGE, "a session without a summary is not the latest");
    assert_eq!(envelope(&answer.stderr)["error"]["code"], "not_a_session");

    let answer = invoke(&root, &["bench", "replay", "2104c3b2", "--json"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    assert_eq!(envelope(&answer.stdout)["data"]["session"], text(&session));
    let answer = invoke(&root, &["bench", "replay", "latest", "--json"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    assert_eq!(envelope(&answer.stdout)["data"]["session"], text(&session));
}

#[tokio::test]
async fn trace_answers_the_spans_of_the_median_run_in_prose_and_in_the_envelope() {
    let root = tempfile::tempdir().expect("a temporary root");
    let session = session_in_runs(&root, "develar-bench-2104c3b2-bf21");
    let answer = invoke(&root, &["bench", "trace", "2104c3b2", "--json"]).await;
    assert_eq!(answer.exit, Exit::USAGE, "a session before its replay has no summary");
    assert_eq!(envelope(&answer.stderr)["error"]["code"], "no_summary");
    let answer = invoke(&root, &["bench", "replay", "2104c3b2", "--json"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);

    let answer = invoke(&root, &["bench", "trace", "latest", "--json"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    let data = &envelope(&answer.stdout)["data"];
    assert_eq!(data["session"], text(&session));
    assert_eq!(data["run"], "non-modal-run-01");
    assert_eq!(data["medianBy"], "welcomeBecameVisible");
    assert_eq!(data["window"], serde_json::json!({"fromMs": 1714.0, "toMs": 3224.7}));
    assert_eq!(data["minMs"], 10.0);
    assert_eq!(data["anchors"][0], serde_json::json!({"name": "frameBecameVisible", "ms": 1714.0}));
    let waiter = data["spans"]
        .as_array()
        .expect("the spans")
        .iter()
        .find(|span| span["waits"] == true)
        .expect("a waiter");
    assert_eq!(waiter["name"], "run activity");
    assert_eq!(waiter["plugin"], "tanvd.grazi");
    assert_eq!(waiter["durationMs"], 4500.0);
    assert_eq!(waiter["helper"], false);

    let answer = invoke(&root, &["bench", "trace", "2104c3b2", "--run", "modal-run-01", "--text"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    assert!(
        answer
            .stdout
            .starts_with("vm bench trace develar-bench-2104c3b2-bf21 modal-run-01\n"),
        "{}",
        answer.stdout
    );
}

#[tokio::test]
async fn activities_answer_the_tables_of_the_median_run_in_prose_and_in_the_envelope() {
    let root = tempfile::tempdir().expect("a temporary root");
    let session = session_in_runs(&root, "develar-bench-2104c3b2-bf21");
    let answer = invoke(&root, &["bench", "activities", "2104c3b2", "--json"]).await;
    assert_eq!(answer.exit, Exit::USAGE, "a session before its replay has no summary");
    assert_eq!(envelope(&answer.stderr)["error"]["code"], "no_summary");
    let answer = invoke(&root, &["bench", "replay", "2104c3b2", "--json"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);

    let answer = invoke(&root, &["bench", "activities", "latest", "--top", "2", "--json"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    let data = &envelope(&answer.stdout)["data"];
    assert_eq!(data["session"], text(&session));
    assert_eq!(data["run"], "non-modal-run-01");
    assert_eq!(data["medianBy"], "welcomeBecameVisible");
    assert_eq!(data["window"]["fromMs"], 0.0);
    assert_eq!((data["totalActivities"].clone(), data["waiters"].clone()), (5.into(), 1.into()));
    let first = &data["activities"][0];
    assert_eq!(first["plugin"], "tanvd.grazi");
    assert_eq!(first["waits"], true);
    assert_eq!(data["activities"].as_array().map(Vec::len), Some(2));
    assert_eq!(data["plugins"][2], Value::Null, "--top bounds the plugins too: {}", data["plugins"]);
    assert_eq!(
        data["plugins"][1],
        serde_json::json!({
            "plugin": "org.jetbrains.kotlin",
            "count": 1,
            "sumMs": 1265.0,
            "longestClass": "org.jetbrains.kotlin.idea.base.plugin.KotlinBundledRefresher",
            "longestMs": 1265.0
        })
    );

    let answer = invoke(&root, &["bench", "activities", "2104c3b2", "--run", "non-modal-run-01", "--text"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    assert!(
        answer
            .stdout
            .starts_with("vm bench activities develar-bench-2104c3b2-bf21 non-modal-run-01\n"),
        "{}",
        answer.stdout
    );
}

#[tokio::test]
async fn classes_answer_the_tables_of_one_arm_in_prose_and_in_the_envelope() {
    let root = tempfile::tempdir().expect("a temporary root");
    let session = session_in_runs(&root, "develar-bench-2104c3b2-bf21");
    let answer = invoke(&root, &["bench", "classes", "2104c3b2", "--json"]).await;
    assert_eq!(answer.exit, Exit::USAGE, "a session before its replay has no summary");
    assert_eq!(envelope(&answer.stderr)["error"]["code"], "no_summary");
    let answer = invoke(&root, &["bench", "replay", "2104c3b2", "--json"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);

    let argv = [
        "bench",
        "classes",
        "latest",
        "--at",
        "frameBecameVisible",
        "--plugin",
        "com.intellij",
        "--plugin",
        "intellij.webp",
        "--json",
    ];
    let answer = invoke(&root, &argv).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    let data = &envelope(&answer.stdout)["data"];
    assert_eq!(data["session"], text(&session));
    assert_eq!(
        (data["arm"].clone(), data["at"].clone(), data["runs"].clone()),
        ("nonModal".into(), "frameBecameVisible".into(), 1.into())
    );
    assert_eq!(data["totals"]["classes.named@frameBecameVisible"]["median"], 26.0);
    assert_eq!(
        data["byPlugin"],
        serde_json::json!([{"name": "com.intellij", "classes": 4.0}, {"name": "intellij.webp", "classes": 0.0}])
    );
    assert_eq!(data["missing"], serde_json::json!(["intellij.webp"]));

    let answer = invoke(&root, &["bench", "classes", "2104c3b2", "--at", "sometime", "--text"]).await;
    assert_eq!(answer.exit, Exit::USAGE);
    assert!(answer.stderr.contains("'sometime'"), "{}", answer.stderr);
    let answer = invoke(&root, &["bench", "classes", "2104c3b2", "--arm", "modal", "--text"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    assert!(
        answer
            .stdout
            .starts_with("vm bench classes develar-bench-2104c3b2-bf21 modal arm, 1 valid run\n"),
        "{}",
        answer.stdout
    );
}

#[tokio::test]
async fn compare_answers_the_medians_of_the_sessions_and_refuses_one_distribution_on_request() {
    let root = tempfile::tempdir().expect("a temporary root");
    let first = session_in_runs(&root, "develar-bench-aaaa1111");
    let second = session_in_runs(&root, "develar-bench-bbbb2222");
    let answer = invoke(&root, &["bench", "compare", "aaaa1111", "bbbb2222", "--json"]).await;
    assert_eq!(answer.exit, Exit::USAGE, "a session before its replay has no summary");
    assert_eq!(envelope(&answer.stderr)["error"]["code"], "no_summary");
    for name in ["aaaa1111", "bbbb2222"] {
        let answer = invoke(&root, &["bench", "replay", name, "--json"]).await;
        assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    }

    let answer = invoke(&root, &["bench", "compare", "aaaa1111", "bbbb2222", "--json"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    let data = &envelope(&answer.stdout)["data"];
    assert_eq!(data["sessions"][0]["session"], text(&first));
    assert_eq!(data["sessions"][1]["session"], text(&second));
    assert_eq!(data["sessions"][1]["label"], "B");
    assert_eq!(data["sessions"][1]["validRuns"], serde_json::json!({"modal": 1, "nonModal": 1}));
    assert_eq!(
        data["arms"]["nonModal"]["welcomeBecameVisible"],
        serde_json::json!({"medians": [2718.0, 2718.0], "deltas": [null]})
    );
    assert_eq!(data["noiseArm"][0]["comparable"], false);
    assert_eq!(data["noiseArm"][0]["sameDist"], true, "two copies of the fixture are an A/A run");
    assert_eq!(data["notes"].as_array().map(Vec::len), Some(2), "{}", data["notes"]);

    let answer = invoke(&root, &["bench", "compare", "aaaa1111", "bbbb2222", "--text"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    assert!(
        answer
            .stdout
            .starts_with("vm bench compare: 2 sessions, each against A\n  A develar-bench-aaaa1111  3f337093836d "),
        "{}",
        answer.stdout
    );

    let answer = invoke(
        &root,
        &["bench", "compare", "aaaa1111", "bbbb2222", "--expect-different-dist", "--json"],
    )
    .await;
    assert_eq!(answer.exit, Exit::USAGE);
    let error = &envelope(&answer.stderr)["error"];
    assert_eq!(error["code"], "same_dist");
    assert!(
        error["message"]
            .as_str()
            .is_some_and(|message| message.starts_with("A develar-bench-aaaa1111 and B develar-bench-bbbb2222 share")),
        "{error}"
    );
}

#[tokio::test]
async fn ls_lists_the_sessions_of_the_runs_root_with_names_that_replay_accepts() {
    let root = tempfile::tempdir().expect("a temporary root");
    let answer = invoke(&root, &["bench", "ls", "--text"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    assert!(answer.stdout.starts_with("vm bench ls: no session under "), "{}", answer.stdout);

    let first = session_in_runs(&root, "develar-bench-aaaa1111");
    session_in_runs(&root, "develar-bench-bbbb2222");
    let answer = invoke(&root, &["bench", "replay", "aaaa1111", "--json"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);

    let answer = invoke(&root, &["bench", "ls", "--json"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    let data = &envelope(&answer.stdout)["data"];
    assert_eq!(data["total"], 2);
    let listed: Vec<&Value> = data["sessions"].as_array().expect("the sessions").iter().collect();
    let replayed = listed
        .iter()
        .find(|listed| listed["name"] == "develar-bench-aaaa1111")
        .expect("the replayed session");
    assert_eq!(replayed["path"], text(&first));
    assert_eq!(replayed["arms"]["modal"]["validRuns"], 1);
    let other = listed
        .iter()
        .find(|listed| listed["name"] == "develar-bench-bbbb2222")
        .expect("the other session");
    assert_eq!(other["arms"], Value::Null, "a session before its replay has no summary");
    for listed in &listed {
        let name = listed["name"].as_str().expect("a name");
        let answer = invoke(&root, &["bench", "replay", name, "--json"]).await;
        assert_eq!(answer.exit, Exit::OK, "{name}: {}", answer.stderr);
    }

    let answer = invoke(&root, &["bench", "ls", "--limit", "1", "--text"]).await;
    assert_eq!(answer.exit, Exit::OK, "{}", answer.stderr);
    assert!(
        answer.stdout.starts_with("vm bench ls: 1 of 2 sessions under "),
        "{}",
        answer.stdout
    );
}

#[test]
fn project_takes_markdown_or_a_directory_with_a_file_to_open() {
    use crate::bench::command::project_source;
    use crate::bench::session::ProjectSource;
    let dir = tempfile::tempdir().expect("a temporary directory");
    assert_eq!(project_source("markdown", dir.path()), Ok(ProjectSource::Markdown));
    std::fs::create_dir_all(dir.path().join("docs")).expect("a project");
    std::fs::write(dir.path().join("docs/README.md"), "# docs\n").expect("a file");
    assert_eq!(project_source("docs", dir.path()), Ok(ProjectSource::Dir(dir.path().join("docs"))));
    let refused = |arg: &str| project_source(arg, dir.path()).expect_err("a refusal");
    let absent = refused("absent");
    assert_eq!((absent.code.as_ref(), absent.exit), ("usage", Exit::USAGE));
    assert!(
        absent.message.ends_with("absent is not a directory, and it is not `markdown`"),
        "{}",
        absent.message
    );
    std::fs::create_dir_all(dir.path().join("empty/sub")).expect("an empty project");
    let empty = refused("empty");
    assert_eq!(empty.code, "usage");
    assert!(
        empty
            .message
            .ends_with("empty has no file to open: it needs a file that is not hidden at its top level"),
        "{}",
        empty.message
    );
}
