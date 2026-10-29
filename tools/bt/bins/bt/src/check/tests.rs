use pretty_assertions::assert_eq;

use super::*;
use crate::execute::{Outcome, execute};
use bt_core::fake::{AttemptSpec, FakeRuntime, area_files, attempt, bep_lines, case, failing, suite, suite_xml, test_result_event};
use bt_core::runtime::SpawnResult;

const CORE_LABEL: &str = "//plugins/air/shared/core:ai-agent-core-tests_test";
const BEP_PATH: &str = "/tmp/air-bazel-test-bep.json";
const XML_PATH: &str = "/exec/testlogs/p/t/test.xml";
const RECORDS: &str = "/repo/.git/bd-checks.jsonl";

fn tree(name: &str) -> CheckoutTree {
    CheckoutTree {
        head: format!("head-{name}"),
        tree: format!("tree-{name}"),
        changed: Vec::new(),
    }
}

/// The fixture tree, whose `checkout_tree` calls answer `trees` in order.
fn checkout(trees: &[CheckoutTree]) -> FakeRuntime {
    let fake = bt_core::fake::fake_air_tree();
    for (path, text) in area_files() {
        fake.put(&path, &text);
    }
    fake.state().checkout_trees = trees.iter().cloned().collect();
    fake
}

/// A run of the core target whose one test passes, or fails with `exit` 3.
fn run_core(fake: &FakeRuntime, passes: bool) -> Outcome {
    let (status, test, exit) = if passes {
        ("PASSED", case("a"), 0)
    } else {
        ("FAILED", failing("a", "E", "boom", "\tat com.intellij.air.X.y(X.kt:1)"), 3)
    };
    fake.put_absolute(XML_PATH, &suite_xml(&suite("AgentThreadIdentityTest", vec![test])));
    fake.put_absolute(
        BEP_PATH,
        &bep_lines(&[test_result_event(&AttemptSpec {
            status,
            xml: XML_PATH,
            ..attempt(CORE_LABEL)
        })]),
    );
    fake.state().spawn_result = SpawnResult {
        exit_code: exit,
        output: String::new(),
    };
    execute(["AgentThreadIdentityTest"], fake).unwrap_or_else(|failure| panic!("refused: {failure:?}"))
}

fn records(fake: &FakeRuntime) -> Option<String> {
    fake.state().files.get(RECORDS).cloned()
}

fn stderr(fake: &FakeRuntime) -> String {
    fake.state().stderr.join("\n")
}

/// The line is the contract that `bd lease pick` reads, so the test pins it whole: the keys and their order.
#[test]
fn a_green_test_run_records_the_tree_that_it_saw() {
    let fake = checkout(&[tree("a"), tree("a")]);
    assert_eq!(run_core(&fake, true).exit_code, exit::GREEN);
    assert_eq!(
        records(&fake).as_deref(),
        Some(
            r#"{"schema":1,"tree":"tree-a","head":"head-a","tool":"bt","command":"test","patterns":["//plugins/air/shared/core:ai-agent-core-tests_test","--filter","com.intellij.air.shared.core.AgentThreadIdentityTest"],"exit":0,"at":6000}
"#
        )
    );
    assert!(!stderr(&fake).contains("check record"), "{}", stderr(&fake));
}

/// A record of a tree with local changes counts only for a commit of the same files, so the stderr names them.
#[test]
fn a_record_of_local_changes_names_them() {
    let changed: Vec<String> = (0..12).map(|index| format!("src/File{index}.kt")).collect();
    let dirty = CheckoutTree { changed, ..tree("a") };
    let fake = checkout(&[dirty.clone(), dirty]);
    run_core(&fake, true);
    assert!(records(&fake).is_some());
    assert!(
        stderr(&fake).contains(
            "bt: the check record names the tree with the local changes of src/File0.kt, src/File1.kt, src/File2.kt, \
             src/File3.kt, src/File4.kt, src/File5.kt, src/File6.kt, src/File7.kt, src/File8.kt, src/File9.kt, 2 more. \
             Commit them"
        ),
        "{}",
        stderr(&fake)
    );
}

/// The green build saw some version of the files, and no one can tell which: no record, and a line that says why.
#[test]
fn an_edit_during_the_run_leaves_no_record() {
    let fake = checkout(&[tree("a"), tree("b")]);
    assert_eq!(run_core(&fake, true).exit_code, exit::GREEN);
    assert_eq!(records(&fake), None);
    assert!(
        stderr(&fake)
            .contains("bt: the files changed during the run (tree tree-a before it, tree-b after it), so the run leaves no check record"),
        "{}",
        stderr(&fake)
    );
}

/// A red run reads the tree once, before the run, and records nothing.
#[test]
fn a_red_run_leaves_no_record() {
    let fake = checkout(&[tree("a"), tree("a")]);
    assert_eq!(run_core(&fake, false).exit_code, exit::TEST_FAILED);
    assert_eq!(records(&fake), None);
    assert_eq!(fake.state().checkout_trees.len(), 1, "the tree after a red run was read");
}

/// Outside git there is no tree to record, and nothing to say about it.
#[test]
fn a_checkout_outside_git_records_nothing_and_says_nothing() {
    let fake = checkout(&[]);
    assert_eq!(run_core(&fake, true).exit_code, exit::GREEN);
    assert_eq!(records(&fake), None);
    assert!(!stderr(&fake).contains("check record"), "{}", stderr(&fake));
}

/// A dry run spawns nothing, so it has no tree to read.
#[test]
fn a_dry_run_reads_no_tree() {
    let fake = checkout(&[tree("a")]);
    execute(["AgentThreadIdentityTest", "--dry-run"], &fake).expect("a dry run");
    assert_eq!(fake.state().checkout_trees.len(), 1);
}

/// The file keeps the newest records, and the new one goes last.
#[test]
fn the_file_keeps_the_newest_records() {
    let fake = checkout(&[tree("a"), tree("a")]);
    let old: Vec<String> = (0..KEPT_RECORDS).map(|index| format!(r#"{{"old":{index}}}"#)).collect();
    fake.put_absolute(RECORDS, &format!("{}\n", old.join("\n")));
    run_core(&fake, true);
    let text = records(&fake).expect("the records");
    let lines: Vec<&str> = text.lines().collect();
    assert_eq!(lines.len(), KEPT_RECORDS);
    assert_eq!(lines[0], r#"{"old":1}"#);
    assert!(
        lines[KEPT_RECORDS - 1].contains(r#""tree":"tree-a""#),
        "{}",
        lines[KEPT_RECORDS - 1]
    );
}
