use pretty_assertions::assert_eq;

use super::*;
use crate::process::never;
use crate::testkit::{FakeProcesses, failed, ok};

const COMMIT: &str = "0123456789abcdef0123456789abcdef01234567";

#[test]
fn a_clean_checkout_passes_and_answers_its_head() {
    let processes = FakeProcesses::new(&[("status --porcelain", ok("")), ("rev-parse", ok(&format!("{COMMIT}\n")))]);
    check_clean(&processes, Path::new("/c"), &never).expect("clean");
    assert_eq!(head_commit(&processes, Path::new("/c"), &never).expect("the head"), COMMIT);
    let calls = processes.calls();
    assert_eq!(calls[0].dir.as_deref(), Some(Path::new("/c")));
    assert_eq!(
        processes.texts(),
        ["git status --porcelain", "git rev-parse --verify HEAD^{commit}"]
    );
}

#[test]
fn local_changes_are_refused_with_the_first_paths() {
    let status: String = (0..7).map(|index| format!("?? file{index}.txt\n")).collect();
    let processes = FakeProcesses::new(&[("status --porcelain", ok(&status))]);
    let refusal = check_clean(&processes, Path::new("/c"), &never).expect_err("local changes");
    assert_eq!(refusal.code, code::LOCAL_CHANGES);
    assert!(refusal.message.contains("?? file4.txt\n  (+2 more)"), "{}", refusal.message);
    assert!(!refusal.message.contains("file5"), "{}", refusal.message);
}

#[test]
fn a_failed_git_names_its_stderr() {
    let processes = FakeProcesses::new(&[("status", failed(128, "fatal: not a git repository\n"))]);
    let refusal = check_clean(&processes, Path::new("/c"), &never).expect_err("no repository");
    assert_eq!(refusal.code, code::GIT_FAILED);
    assert_eq!(refusal.message, "git status failed: exit 128: fatal: not a git repository");
}

/// The candidates sample the first-parent line at the distances 0, 1, 2, 4 … 65536, so a host far behind still has one.
#[test]
fn the_candidates_sample_the_ancestors_in_powers_of_two_then_the_merge_bases() {
    let line: String = (0..=70_000).map(|distance| format!("a{distance}\n")).collect();
    let processes = FakeProcesses::new(&[
        ("rev-list", ok(&line)),
        ("merge-base 0123456789abcdef0123456789abcdef01234567 master", ok("a4\n")),
        ("origin/master", failed(128, "fatal: Not a valid object name origin/master")),
    ]);
    let expected: Vec<String> = std::iter::once(0)
        .chain((0..=16).map(|power| 1 << power))
        .map(|distance: usize| format!("a{distance}"))
        .collect();
    assert_eq!(candidates(&processes, Path::new("/c"), COMMIT, FARTHEST, &never), expected);
    assert_eq!(expected.last().map(String::as_str), Some("a65536"));
    assert_eq!(
        processes.texts()[0],
        format!("git rev-list --first-parent --max-count=65537 {COMMIT}")
    );
}

#[test]
fn a_short_history_gives_what_it_has() {
    let processes = FakeProcesses::new(&[("rev-list", ok("c3\nc2\nc1\n")), ("merge-base", ok("c1\n"))]);
    assert_eq!(
        candidates(&processes, Path::new("/c"), COMMIT, FARTHEST, &never),
        ["c3", "c2", "c1"]
    );
}

/// The base is `origin/master`, or `master` in a checkout without `origin/master`, or none.
#[test]
fn the_base_is_origin_master_then_master() {
    let both = FakeProcesses::new(&[("rev-parse", ok("b1\n"))]);
    assert_eq!(base_commit(&both, Path::new("/c"), &never).as_deref(), Some("b1"));
    assert_eq!(both.texts(), ["git rev-parse --verify --quiet origin/master^{commit}"]);

    let local = FakeProcesses::new(&[("origin/master", failed(1, "")), ("master", ok("b2\n"))]);
    assert_eq!(base_commit(&local, Path::new("/c"), &never).as_deref(), Some("b2"));

    let neither = FakeProcesses::new(&[("rev-parse", failed(1, ""))]);
    assert_eq!(base_commit(&neither, Path::new("/c"), &never), None);
}

#[test]
fn a_candidate_comes_once() {
    let mut found = vec!["c1".to_owned()];
    add_candidate(&mut found, Some("c1"));
    add_candidate(&mut found, None);
    add_candidate(&mut found, Some(""));
    add_candidate(&mut found, Some("b1"));
    assert_eq!(found, ["c1", "b1"]);
}

#[test]
fn the_pack_excludes_each_present_commit_and_writes_progress() {
    let call = pack_call(Path::new("/c"), "c3", &["c1".to_owned(), "m0".to_owned()]);
    assert_eq!(call.text(), "git pack-objects --revs --thin --stdout --progress");
    assert_eq!(call.limit, Limit::Quiet(PACK_QUIET));
    assert_eq!(String::from_utf8_lossy(&call.input), "c3\n^c1\n^m0\n");
}

#[test]
fn the_count_of_a_pack_excludes_the_present_commits() {
    let processes = FakeProcesses::new(&[("rev-list --count", ok("12\n"))]);
    assert_eq!(
        commit_count(&processes, Path::new("/c"), "c3", &["c1".to_owned()], &never),
        Some(12)
    );
    assert_eq!(processes.texts(), ["git rev-list --count c3 ^c1"]);
}
