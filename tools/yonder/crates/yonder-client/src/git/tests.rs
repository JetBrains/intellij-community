use pretty_assertions::assert_eq;

use super::*;
use crate::process::never;
use crate::testkit::{FakeProcesses, failed, ok, real_git};

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

/// What a run must not change in a checkout: the status, the index, the refs, HEAD and the reflog.
fn checkout_state(dir: &Path) -> (String, Vec<u8>, String, String, String) {
    (
        real_git(dir, &["status", "--porcelain=v2", "--branch", "--untracked-files=all"]),
        fs::read(dir.join(".git/index")).expect("the index"),
        real_git(dir, &["for-each-ref"]),
        real_git(dir, &["rev-parse", "HEAD"]),
        real_git(dir, &["reflog", "--format=%H %gs"]),
    )
}

#[test]
fn the_commit_of_the_working_tree_holds_every_change_and_leaves_the_checkout_as_it_was() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let repo = dir.path();
    real_git(repo, &["init", "-q"]);
    fs::write(repo.join(".gitignore"), "ignored/\n").expect("the ignore file");
    fs::write(repo.join("a.txt"), "a\n").expect("a file");
    fs::write(repo.join("b.txt"), "b\n").expect("a file");
    fs::write(repo.join("gone.txt"), "gone\n").expect("a file");
    fs::write(repo.join("moved.txt"), "moved\n").expect("a file");
    real_git(repo, &["add", "--all"]);
    real_git(repo, &["commit", "-q", "-m", "first"]);
    let head = real_git(repo, &["rev-parse", "HEAD"]);

    let clean = working_tree_commit(&crate::OsProcesses, repo, &never).expect("a clean checkout");
    assert_eq!((clean.commit.as_str(), clean.changed.len()), (head.as_str(), 0));

    // A tracked change, a staged change that the working tree changed again, a deleted file, a staged rename, an
    // untracked file and an ignored file.
    fs::write(repo.join("a.txt"), "a changed\n").expect("a change");
    fs::write(repo.join("b.txt"), "b staged\n").expect("a staged change");
    real_git(repo, &["add", "b.txt"]);
    fs::write(repo.join("b.txt"), "b in the working tree\n").expect("a later change");
    fs::remove_file(repo.join("gone.txt")).expect("a deleted file");
    real_git(repo, &["mv", "moved.txt", "renamed.txt"]);
    fs::create_dir_all(repo.join("new/dir")).expect("a directory");
    fs::write(repo.join("new/dir/c.txt"), "c\n").expect("an untracked file");
    fs::create_dir_all(repo.join("ignored")).expect("an ignored directory");
    fs::write(repo.join("ignored/x.txt"), "x\n").expect("an ignored file");
    let before = checkout_state(repo);

    let snapshot = working_tree_commit(&crate::OsProcesses, repo, &never).expect("a snapshot");
    assert_ne!(snapshot.commit, head);
    assert_eq!(snapshot.head, head);
    assert_eq!(snapshot.changed.len(), 5, "{:?}", snapshot.changed);
    assert_eq!(checkout_state(repo), before, "the checkout is as it was");
    assert_eq!(real_git(repo, &["rev-parse", &format!("{}^", snapshot.commit)]), head);
    assert_eq!(
        real_git(repo, &["ls-tree", "-r", "--name-only", &snapshot.commit]),
        ".gitignore\na.txt\nb.txt\nnew/dir/c.txt\nrenamed.txt"
    );
    assert_eq!(
        real_git(repo, &["show", &format!("{}:b.txt", snapshot.commit)]),
        "b in the working tree"
    );
    assert_eq!(real_git(repo, &["show", &format!("{}:a.txt", snapshot.commit)]), "a changed");
    assert_eq!(
        real_git(
            repo,
            &["log", "-1", "--format=%an <%ae> %ad|%cn %cd", "--date=raw", &snapshot.commit]
        ),
        format!(
            "yonder <yonder@yonder.invalid> {}|yonder {}",
            real_git(repo, &["log", "-1", "--format=%cd", "--date=raw", &head]),
            real_git(repo, &["log", "-1", "--format=%cd", "--date=raw", &head])
        )
    );

    // The same tree on the same HEAD is the same commit, so a second run sends nothing.
    let again = working_tree_commit(&crate::OsProcesses, repo, &never).expect("a second snapshot");
    assert_eq!(again.commit, snapshot.commit);
    let left: Vec<_> = fs::read_dir(std::env::temp_dir())
        .expect("the temporary directory")
        .filter_map(Result::ok)
        .filter(|entry| {
            entry
                .file_name()
                .to_string_lossy()
                .starts_with(&format!("yonder-{}-", std::process::id()))
        })
        .filter(|entry| entry.file_name().to_string_lossy().contains(".index"))
        .collect();
    assert_eq!(left.len(), 0, "no index copy stays");
}

#[test]
fn the_entries_of_a_status_name_each_path_but_the_old_path_of_a_rename() {
    let (lines, paths) = status_entries(b" M a.txt\0R  new name.txt\0old name.txt\0?? dir/\0 D gone.txt\0");
    assert_eq!(lines, [" M a.txt", "R  new name.txt", "?? dir/", " D gone.txt"]);
    assert_eq!(paths, ["a.txt", "new name.txt", "dir/", "gone.txt"]);
    assert_eq!(status_entries(b""), (Vec::new(), Vec::new()));
}

#[test]
fn the_snapshot_sets_the_index_copy_for_add_and_write_tree_only() {
    let status = " M a.txt\0?? b*.txt\0";
    let processes = FakeProcesses::new(&[
        ("status --porcelain", ok(status)),
        ("rev-parse --verify HEAD", ok(&format!("{COMMIT}\n"))),
        ("--git-path index", ok("/missing/index\n")),
        ("write-tree", ok("t1\n")),
        ("^{tree}", ok("t0\n")),
        (
            "cat-file commit",
            ok("tree t0\nauthor a <a@a> 1 +0000\ncommitter c <c@c> 1700000000 +0200\n\nm\n"),
        ),
        ("commit-tree", ok("s1\n")),
    ]);
    let snapshot = working_tree_commit(&processes, Path::new("/c"), &never).expect("a snapshot");
    assert_eq!(snapshot.commit, "s1");
    let calls = processes.calls();
    let index_of = |text: &str| {
        calls
            .iter()
            .find(|call| call.text().contains(text))
            .unwrap_or_else(|| panic!("no call {text}"))
            .env
            .iter()
            .find(|(name, _)| name == "GIT_INDEX_FILE")
            .map(|(_, value)| value.clone())
    };
    let copy = index_of("add --all").expect("add has the index copy");
    let add = calls.iter().find(|call| call.text().contains("add --all")).expect("add");
    assert_eq!(
        add.text(),
        "git --literal-pathspecs add --all --pathspec-from-file=- --pathspec-file-nul"
    );
    assert_eq!(add.input, b"a.txt\0b*.txt\0", "only the paths of the status, each one literal");
    assert!(copy.contains(&format!("yonder-{}-", std::process::id())), "{copy}");
    assert_eq!(index_of("read-tree"), Some(copy.clone()), "a missing index starts from HEAD");
    assert_eq!(index_of("write-tree"), Some(copy));
    assert_eq!(index_of("status --porcelain"), None);
    let commit = calls.iter().find(|call| call.text().contains("commit-tree")).expect("commit-tree");
    assert!(
        commit
            .env
            .contains(&("GIT_COMMITTER_DATE".to_owned(), "1700000000 +0200".to_owned())),
        "{:?}",
        commit.env
    );
    assert_eq!(
        commit.text(),
        format!("git commit-tree --no-gpg-sign -p {COMMIT} -m yonder: the working tree on 0123456789ab t1")
    );
}

#[test]
fn the_name_of_a_repository_is_the_directory_of_its_main_checkout() {
    let named = |common: &str| repo_name(&FakeProcesses::new(&[("--git-common-dir", ok(common))]), Path::new("/c"), &never);
    assert_eq!(named("/u/projects/branch-deck/.git\n").as_deref(), Some("branch-deck"));
    assert_eq!(named("/srv/git/tool.git\n").as_deref(), Some("tool"));
    assert_eq!(
        repo_name(
            &FakeProcesses::new(&[("--git-common-dir", failed(128, "fatal"))]),
            Path::new("/c"),
            &never
        ),
        None
    );
}
