use pretty_assertions::assert_eq;

use super::*;
use crate::execute::execute;
use bt_core::fake::{FakeRuntime, area_files, refusal};
use bt_core::runtime::{CheckoutTree, SpawnResult};

const RECORDS: &str = "/repo/.git/bd-checks.jsonl";
const BEP_PATH: &str = "/tmp/air-bazel-build-bep.json";

/// A checkout whose tree is the same before and after a run.
fn checkout() -> FakeRuntime {
    let fake = FakeRuntime::new([]);
    for (path, text) in area_files() {
        fake.put(&path, &text);
    }
    let tree = CheckoutTree {
        head: "head-a".to_owned(),
        tree: "tree-a".to_owned(),
        changed: Vec::new(),
    };
    fake.state().checkout_trees = [tree.clone(), tree].into();
    fake
}

fn build(argv: &[&str], fake: &FakeRuntime, bazel_exit: i32, output: &str) -> Outcome {
    fake.state().spawn_result = SpawnResult {
        exit_code: bazel_exit,
        output: output.to_owned(),
    };
    let argv: Vec<&str> = std::iter::once("build").chain(argv.iter().copied()).collect();
    execute(&argv, fake).unwrap_or_else(|failure| panic!("{argv:?} refused: {failure:?}"))
}

#[test]
fn a_green_build_says_so_in_one_line_and_records_the_tree() {
    let fake = checkout();
    let outcome = build(&["//plugins/air/..."], &fake, 0, "");
    assert_eq!(outcome.exit_code, exit::GREEN);
    assert_eq!(outcome.text, "BUILD OK  //plugins/air/...  5.0s");
    let spawned = fake.spawned();
    assert_eq!(spawned.len(), 1);
    assert_eq!(
        spawned[0][1..],
        [
            "/repo/bazel.cmd",
            "build",
            "--noshow_progress",
            "--curses=no",
            "--color=no",
            "--show_result=0",
            "-k",
            &format!("--build_event_json_file={BEP_PATH}"),
            "//plugins/air/...",
        ]
    );
    let records = fake.state().files.get(RECORDS).cloned().expect("a record");
    assert!(
        records.starts_with(
            r#"{"schema":1,"tree":"tree-a","head":"head-a","tool":"bt","command":"build","patterns":["//plugins/air/..."],"exit":0,"#
        ),
        "{records}"
    );
    assert_eq!(fake.state().removed, [BEP_PATH]);
}

#[test]
fn a_failed_build_names_the_compile_errors_and_records_nothing() {
    let fake = checkout();
    let output = "plugins/air/x/Foo.kt:17:16: error: Unresolved reference 'foo'.\nERROR: Build did NOT complete successfully";
    let outcome = build(&["//plugins/air/...", "--json"], &fake, 1, output);
    assert_eq!(outcome.exit_code, exit::BUILD_FAILED);
    assert_eq!(
        outcome.text,
        "BUILD FAILED  //plugins/air/...  5.0s\nplugins/air/x/Foo.kt:17:16: error: Unresolved reference 'foo'."
    );
    assert_eq!(outcome.json.map(|payload| payload.status), Some(RunStatus::BuildFailed));
    assert!(
        fake.state().stderr.join("\n").contains("Build did NOT complete"),
        "the tail of bazel is missing"
    );
    assert!(!fake.state().files.contains_key(RECORDS));
    assert_eq!(fake.state().checkout_trees.len(), 1, "the tree after a failed build was read");
}

/// Every exit code of bazel but 0 and 1 is bazel failing, which says nothing about the build.
#[test]
fn a_bazel_that_fails_is_infrastructure() {
    for code in [2, 8, 33, 37, 127] {
        let fake = checkout();
        let outcome = build(&["//plugins/air/..."], &fake, code, "FATAL: bazel crashed");
        assert_eq!(outcome.exit_code, exit::INFRA, "{code}");
        assert!(
            outcome.text.starts_with("INFRA  the bazel build did not finish"),
            "{}",
            outcome.text
        );
    }
}

#[test]
fn a_dry_run_prints_the_argv_and_spawns_nothing() {
    let fake = checkout();
    let outcome = build(&["//plugins/air/...", "--dry-run", "--", "--config=x"], &fake, 0, "");
    assert!(
        outcome.text.starts_with("argv    /bin/sh /repo/bazel.cmd build "),
        "{}",
        outcome.text
    );
    assert!(outcome.text.ends_with(" //plugins/air/... --config=x"), "{}", outcome.text);
    assert!(fake.spawned().is_empty());
    assert_eq!(fake.state().checkout_trees.len(), 2, "a dry run read the tree");
}

#[test]
fn the_command_line_of_a_build_is_checked_before_any_work() {
    let fake = checkout();
    let failure = refusal(execute(["build", "plugins/air"], &fake));
    assert_eq!(failure.exit, exit::USAGE);
    assert!(
        failure.message.contains("a target pattern starts with // or @"),
        "{}",
        failure.message
    );

    let help = execute(["build", "--help"], &fake).expect("the help");
    assert_eq!(help.exit_code, exit::GREEN);
    assert!(help.text.starts_with("Agent-friendly wrapper over `bazel build`."), "{}", help.text);

    let bare = execute(["build"], &fake).expect("the help");
    assert_eq!(bare.exit_code, exit::USAGE);
    assert!(fake.spawned().is_empty());
}
