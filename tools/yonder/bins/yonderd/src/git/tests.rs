use std::path::PathBuf;

use pretty_assertions::assert_eq;

use super::*;
use crate::testing;

/// A repository with one commit. Answers the directory and the commit.
fn repo() -> (tempfile::TempDir, PathBuf, String) {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let repo = dir.path().join("repo");
    std::fs::create_dir(&repo).expect("the repository directory");
    let run = |args: &[&str]| testing::git(&repo, args);
    run(&["init", "-q"]);
    std::fs::write(repo.join("a.txt"), "a").expect("a file");
    run(&["add", "a.txt"]);
    run(&["commit", "-q", "-m", "first"]);
    let commit = run(&["rev-parse", "HEAD"]);
    (dir, repo, commit)
}

#[test]
fn a_commit_id_is_40_or_64_lower_case_hex_digits() {
    assert!(is_commit_id(&"a".repeat(40)));
    assert!(is_commit_id(&"0".repeat(64)));
    assert!(!is_commit_id(&"A".repeat(40)));
    assert!(!is_commit_id("abc"));
    assert!(!is_commit_id(&format!("{}\n", "a".repeat(39))));
}

#[test]
fn a_pack_ref_is_a_run_ref_or_a_base_ref() {
    assert!(is_pack_ref("refs/yonder/1-2"));
    assert!(is_pack_ref("refs/yonder/base/master"));
    assert!(!is_pack_ref("refs/heads/master"));
    assert!(!is_pack_ref("refs/yonder/../heads/master"));
    assert!(!is_pack_ref("refs/yonder/"));
    assert!(!is_pack_ref("refs/yonder/base/"));
    assert!(!is_pack_ref("refs/yonder/base/a/b"));
    assert!(!is_pack_ref("refs/yonder/base/.hidden"));

    assert!(is_run_ref("refs/yonder/1-2"));
    assert!(!is_run_ref("refs/yonder/base/master"), "a base ref is no run ref");
}

#[test]
fn have_answers_the_commits_that_the_repository_has() {
    let (_dir, repo, commit) = repo();
    assert_eq!(check_repo(&repo), Ok(()));
    let missing = "1".repeat(40);
    assert_eq!(have(&repo, &[missing, commit.clone()]).expect("the answer"), vec![commit.clone()]);
    assert!(has_commit(&repo, &commit).expect("the answer"));
    assert!(!has_commit(&repo, "refs/yonder/1-2").expect("the answer"));
    update_ref(&repo, "refs/yonder/1-2", &commit).expect("made");
    assert!(has_commit(&repo, "refs/yonder/1-2").expect("the answer"));
}

#[test]
fn a_ref_is_made_and_deleted() {
    let (_dir, repo, commit) = repo();
    update_ref(&repo, "refs/yonder/1-2", &commit).expect("made");
    delete_ref(&repo, "refs/yonder/1-2").expect("deleted");
    let error = update_ref(&repo, "refs/yonder/1-2", &"1".repeat(40)).expect_err("refused");
    assert!(error.contains("exit status"), "{error}");
}

/// A path that is no repository says why, with the words of git.
#[test]
fn a_directory_without_git_is_no_repository_and_says_why() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let Err(NotRepo::Missing(why)) = check_repo(dir.path()) else {
        panic!("{:?}", check_repo(dir.path()));
    };
    assert!(
        why.starts_with("`git rev-parse --git-dir` says: fatal: not a git repository"),
        "{why}"
    );
    assert_eq!(
        check_repo(&dir.path().join("missing")),
        Err(NotRepo::Missing("it is no directory".to_owned()))
    );
}

/// A checkout and a bare repository pass by their files, without a git call.
#[test]
fn a_checkout_and_a_bare_repository_pass_by_their_files() {
    let (dir, repo, _) = repo();
    assert_eq!(check_repo(&repo), Ok(()));
    let bare = dir.path().join("bare.git");
    testing::git(
        dir.path(),
        &["clone", "-q", "--bare", &repo.to_string_lossy(), &bare.to_string_lossy()],
    );
    assert_eq!(check_repo(&bare), Ok(()));
}
