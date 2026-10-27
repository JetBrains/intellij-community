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

/// A detached slot moves with a one-way merge from the index, which keeps a local change outside the diff and the ignored files. The
/// same commit changes nothing.
#[test]
fn a_detached_slot_moves_to_the_commit_and_writes_only_the_diff() {
    let (_dir, repo, first) = repo();
    let run = |args: &[&str]| testing::git(&repo, args);
    std::fs::write(repo.join("b.txt"), "b").expect("a file");
    run(&["add", "b.txt"]);
    run(&["commit", "-q", "-m", "second"]);
    let second = run(&["rev-parse", "HEAD"]);
    run(&["checkout", "-q", "--detach", &first]);
    std::fs::write(repo.join("a.txt"), "outside the diff").expect("a change");

    assert_eq!(checkout(&repo, &second, &never), Ok(Checkout::Switched));
    assert_eq!(run(&["rev-parse", "HEAD"]), second);
    assert_eq!(std::fs::read_to_string(repo.join("b.txt")).expect("the new file"), "b");
    assert_eq!(
        std::fs::read_to_string(repo.join("a.txt")).expect("the kept file"),
        "outside the diff",
        "the checkout reads no file outside the diff"
    );

    assert_eq!(checkout(&repo, &second, &never), Ok(Checkout::Kept));
    assert_eq!(checkout(&repo, &first, &never), Ok(Checkout::Switched));
    assert!(!repo.join("b.txt").exists(), "the file of the diff goes");

    run(&["add", "a.txt"]);
    assert_eq!(checkout(&repo, &second, &never), Ok(Checkout::Switched));
    assert_eq!(
        std::fs::read_to_string(repo.join("a.txt")).expect("the reset file"),
        "a",
        "a staged change outside the diff ends at the commit"
    );
    assert_eq!(run(&["status", "--porcelain"]), "");
}

/// A local change in the diff, and a HEAD on a branch, get `checkout -f --detach`: it discards the change, and the
/// branch stays where it was.
#[test]
fn a_change_in_the_diff_or_a_branch_forces_the_checkout() {
    let (_dir, repo, first) = repo();
    let run = |args: &[&str]| testing::git(&repo, args);
    std::fs::write(repo.join("a.txt"), "second").expect("a change");
    run(&["commit", "-q", "-am", "second"]);
    let second = run(&["rev-parse", "HEAD"]);
    let branch = run(&["symbolic-ref", "HEAD"]);

    let Ok(Checkout::Forced(why)) = checkout(&repo, &first, &never) else {
        panic!("a HEAD on a branch must force the checkout");
    };
    assert_eq!(why, format!("the HEAD is on the branch {branch}"));
    assert_eq!(run(&["rev-parse", &branch]), second, "the branch stays");
    assert_eq!(run(&["rev-parse", "HEAD"]), first);

    std::fs::write(repo.join("a.txt"), "local").expect("a change in the diff");
    let Ok(Checkout::Forced(why)) = checkout(&repo, &second, &never) else {
        panic!("a change in the diff must force the checkout");
    };
    assert!(why.starts_with("`git reset --merge` refused: "), "{why}");
    assert_eq!(run(&["rev-parse", "HEAD"]), second);
    assert_eq!(std::fs::read_to_string(repo.join("a.txt")).expect("the file"), "second");
}

/// The tree of the scan is the one of `git add --all`: a change, a deletion, an untracked directory, a name with a
/// glob character and a space go in, and an ignored file stays out. The index of the worktree stays as it is.
#[test]
fn the_worktree_tree_is_the_tree_of_add_all() {
    let (dir, repo, _) = repo();
    let run = |args: &[&str]| testing::git(&repo, args);
    std::fs::write(repo.join(".gitignore"), "*.log\n").expect("the ignore file");
    std::fs::write(repo.join("gone.txt"), "gone").expect("a file");
    run(&["add", ".gitignore", "gone.txt"]);
    run(&["commit", "-q", "-m", "second"]);
    let index_before = std::fs::read(repo.join(".git/index")).expect("the index");
    let clean = worktree_tree(&repo, &dir.path().join("clean.index"), &never).expect("the clean tree");
    assert_eq!(clean, run(&["rev-parse", "HEAD^{tree}"]), "a clean worktree has the tree of HEAD");

    std::fs::write(repo.join("a.txt"), "changed").expect("a change");
    std::fs::remove_file(repo.join("gone.txt")).expect("a deletion");
    std::fs::create_dir_all(repo.join("new/sub")).expect("a directory");
    std::fs::write(repo.join("new/sub/b.txt"), "b").expect("an untracked file");
    std::fs::write(repo.join("star*.txt"), "star").expect("a glob name");
    std::fs::write(repo.join("a b.txt"), "space").expect("a name with a space");
    std::fs::write(repo.join("out.log"), "ignored").expect("an ignored file");
    let scanned = worktree_tree(&repo, &dir.path().join("scan.index"), &never).expect("the tree");

    assert_eq!(
        std::fs::read(repo.join(".git/index")).expect("the index"),
        index_before,
        "the index stays"
    );
    assert!(
        !dir.path().join("scan.index").exists() && !dir.path().join("scan.paths").exists(),
        "the copies go"
    );
    assert_eq!(scanned, add_all_tree(&repo, &dir.path().join("all.index")));
    let names = run(&["ls-tree", "-r", "--name-only", &scanned]);
    assert_eq!(names, ".gitignore\na b.txt\na.txt\nnew/sub/b.txt\nstar*.txt");
}

/// The tree of a plain `git add --all` of the worktree `repo` into a copy `copy` of its index.
fn add_all_tree(repo: &Path, copy: &Path) -> String {
    std::fs::copy(repo.join(".git/index"), copy).expect("a copy");
    let all = |args: &[&str]| {
        let output = Command::new("git")
            .arg("-C")
            .arg(repo)
            .args(args)
            .env("GIT_INDEX_FILE", copy)
            .output()
            .expect("git");
        assert!(output.status.success(), "{}", String::from_utf8_lossy(&output.stderr));
        String::from_utf8_lossy(&output.stdout).trim().to_owned()
    };
    all(&["add", "--all"]);
    let tree = all(&["write-tree"]);
    let _ = std::fs::remove_file(copy);
    tree
}

/// Up to `PATHSPEC_ADD_MAX` entries of `git status` go to the add by their paths, one more gets a plain add.
#[test]
fn the_status_paths_end_at_the_threshold() {
    let entries = |count: usize, prefix: &str| -> Vec<u8> { (0..count).flat_map(|n| format!("{prefix}f{n}\0").into_bytes()).collect() };
    assert_eq!(
        status_paths(&entries(PATHSPEC_ADD_MAX, "?? ")),
        Some(entries(PATHSPEC_ADD_MAX, "")),
        "the paths at the threshold, each with its NUL"
    );
    assert_eq!(status_paths(&entries(PATHSPEC_ADD_MAX + 1, " M ")), None);
    assert_eq!(status_paths(b""), Some(Vec::new()));
}

/// A scan with more changes than `PATHSPEC_ADD_MAX` and one with a file that became a directory have the tree of
/// `git add --all`.
#[test]
fn a_scan_of_many_changes_and_of_a_file_that_became_a_directory_is_the_tree_of_add_all() {
    let (dir, repo, _) = repo();
    let run = |args: &[&str]| testing::git(&repo, args);
    for n in 0..PATHSPEC_ADD_MAX {
        std::fs::write(repo.join(format!("t{n}.txt")), "t").expect("a tracked file");
    }
    run(&["add", "--all"]);
    run(&["commit", "-q", "-m", "many"]);
    for n in 0..PATHSPEC_ADD_MAX {
        std::fs::write(repo.join(format!("t{n}.txt")), "changed").expect("a change");
    }
    std::fs::write(repo.join("u.txt"), "u").expect("one more untracked file");
    let many = worktree_tree(&repo, &dir.path().join("many.index"), &never).expect("the tree");
    assert_eq!(many, add_all_tree(&repo, &dir.path().join("all.index")));
    run(&["add", "--all"]);
    run(&["commit", "-q", "-m", "changed"]);

    std::fs::remove_file(repo.join("a.txt")).expect("the file goes");
    std::fs::create_dir(repo.join("a.txt")).expect("a directory of the same name");
    std::fs::write(repo.join("a.txt/inner.txt"), "inner").expect("a file in it");
    let swapped = worktree_tree(&repo, &dir.path().join("swap.index"), &never).expect("the tree");
    assert_eq!(swapped, add_all_tree(&repo, &dir.path().join("all.index")));
    assert!(run(&["ls-tree", "-r", "--name-only", &swapped]).contains("a.txt/inner.txt"));
}
