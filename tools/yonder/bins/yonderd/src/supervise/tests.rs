use std::path::PathBuf;

use pretty_assertions::assert_eq;

use super::*;
use crate::run::Runs;

fn lines(input: &[u8]) -> Vec<String> {
    let mut lines = Vec::new();
    read_lines(input, &mut |line| lines.push(line.to_owned()));
    lines
}

#[test]
fn lines_lose_their_ends_and_a_last_line_without_an_end_comes_too() {
    assert_eq!(lines(b"one\ntwo\r\n\nthree"), vec!["one", "two", "", "three"]);
    assert_eq!(lines(b""), Vec::<String>::new());
}

#[test]
fn a_long_line_comes_in_pieces() {
    let mut input = vec![b'x'; MAX_EVENT_TEXT * 2 + 5];
    input.push(b'\n');
    input.extend_from_slice(b"next\n");
    let pieces = lines(&input);
    assert_eq!(
        pieces.iter().map(String::len).collect::<Vec<_>>(),
        vec![MAX_EVENT_TEXT, MAX_EVENT_TEXT, 5, 4]
    );
    let exact = [vec![b'y'; MAX_EVENT_TEXT], b"\n".to_vec()].concat();
    assert_eq!(lines(&exact).len(), 1, "a line of exactly the limit is one event");
}

#[test]
fn an_exit_code_of_a_signal_is_128_plus_its_number() {
    let status = Command::new("/bin/sh").args(["-c", "kill -TERM $$"]).status().expect("ran");
    assert_eq!(exit_code(status), 128 + libc::SIGTERM);
    let status = Command::new("/bin/sh").args(["-c", "exit 4"]).status().expect("ran");
    assert_eq!(exit_code(status), 4);
}

#[test]
fn only_the_new_entries_of_a_fetch_directory_go_to_the_stage() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let slot = dir.path().join("slot");
    let runs = slot.join("out/bt-runs");
    fs::create_dir_all(runs.join("old")).expect("an old run");
    let fetch = vec!["out/bt-runs".to_owned(), "out/missing".to_owned()];
    let before: Vec<_> = fetch.iter().map(|dir| entries(&slot.join(dir))).collect();
    fs::create_dir_all(runs.join("new/sub")).expect("a new run");
    fs::write(runs.join("new/sub/report.xml"), "<x/>").expect("a report");
    fs::write(runs.join("loose.txt"), "t").expect("a file");
    let stage = dir.path().join("stage");
    assert_eq!(stage_new_entries(&slot, &fetch, &before, &stage), Vec::<String>::new());
    assert_eq!(
        fs::read_to_string(stage.join("out/bt-runs/new/sub/report.xml")).expect("staged"),
        "<x/>"
    );
    assert!(stage.join("out/bt-runs/loose.txt").is_file());
    assert!(!stage.join("out/bt-runs/old").exists());
    assert!(runs.join("old").is_dir());
    assert!(!runs.join("new").exists());
    assert!(!stage.join("out/missing").exists());
}

#[test]
fn a_tree_copy_keeps_files_dirs_and_links() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let source = dir.path().join("source");
    fs::create_dir_all(source.join("a")).expect("a directory");
    fs::write(source.join("a/f"), "f").expect("a file");
    std::os::unix::fs::symlink("a/f", source.join("link")).expect("a link");
    let target: PathBuf = dir.path().join("target");
    copy_tree(&source, &target).expect("copied");
    assert_eq!(fs::read_to_string(target.join("a/f")).expect("the file"), "f");
    assert_eq!(fs::read_link(target.join("link")).expect("the link"), PathBuf::from("a/f"));
}

/// Runs git in `repo` with a test identity and answers its stdout.
fn git_in(repo: &Path, args: &[&str]) -> String {
    let output = Command::new("git")
        .arg("-C")
        .arg(repo)
        .args(args)
        .env("GIT_CONFIG_NOSYSTEM", "1")
        .env("GIT_AUTHOR_NAME", "t")
        .env("GIT_AUTHOR_EMAIL", "t@t")
        .env("GIT_COMMITTER_NAME", "t")
        .env("GIT_COMMITTER_EMAIL", "t@t")
        .output()
        .expect("git");
    assert!(output.status.success(), "{}", String::from_utf8_lossy(&output.stderr));
    String::from_utf8_lossy(&output.stdout).trim().to_owned()
}

fn spec_of(commit: &str) -> RunSpec {
    RunSpec {
        run: "1-1".to_owned(),
        repo: "~/repo".to_owned(),
        holder: "h".to_owned(),
        commit: commit.to_owned(),
        reference: "refs/yonder/1-1".to_owned(),
        command: vec!["true".to_owned()],
        fetch: vec![],
        idle_hours: 8,
    }
}

/// A repository with one commit, and a later commit whose object is hidden, as if its pack still travelled. Answers
/// the directory, the repository, the hidden commit, its object path and its hiding place.
fn repo_with_a_travelling_commit() -> (tempfile::TempDir, PathBuf, String, PathBuf, PathBuf) {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let repo = dir.path().join("repo");
    fs::create_dir(&repo).expect("the repository directory");
    git_in(&repo, &["init", "-q"]);
    git_in(
        &repo,
        &["-c", "maintenance.auto=false", "commit", "-q", "--allow-empty", "-m", "first"],
    );
    let first = git_in(&repo, &["rev-parse", "HEAD"]);
    let tree = git_in(&repo, &["rev-parse", "HEAD:"]);
    let later = git_in(&repo, &["commit-tree", &tree, "-p", &first, "-m", "later"]);
    let object = repo.join(".git/objects").join(&later[..2]).join(&later[2..]);
    let hidden = dir.path().join("hidden");
    fs::rename(&object, &hidden).expect("the object hidden");
    (dir, repo, later, object, hidden)
}

fn limits(deadline_ms: u64, stall_ms: u64) -> PackLimits {
    PackLimits {
        deadline: Duration::from_millis(deadline_ms),
        stall: Duration::from_millis(stall_ms),
    }
}

#[test]
fn the_pack_wait_ends_at_once_for_a_present_commit_at_its_deadline_at_a_stall_or_at_a_stop() {
    let (dir, repo, missing, _, _) = repo_with_a_travelling_commit();
    let present = git_in(&repo, &["rev-parse", "HEAD"]);
    let (run, _lock) = Runs::new(dir.path().join("runs")).create(&spec_of(&missing)).expect("a run");
    let mut said = Vec::new();
    let mut wait = |commit: &str, limits: PackLimits, stop: Stop<'_>| {
        wait_for_pack(&run, &repo, &spec_of(commit), limits, stop, &mut |line| said.push(line.to_owned()))
    };

    // A commit that the host has already needs no pack.
    let started = Instant::now();
    assert!(wait(&present, limits(30_000, 30_000), &|| false).expect("no refusal"));
    assert!(started.elapsed() < Duration::from_secs(5), "{:?}", started.elapsed());

    for (limits, failure) in [
        (limits(300, 30_000), "did not come in 0.3 s"),
        (limits(30_000, 300), "sent no chunk for 0.3 s, so its client is gone"),
    ] {
        let refusal = wait(&missing, limits, &|| false).expect_err(failure);
        assert_eq!(refusal.code, PACK_MISSING, "{failure}");
        assert!(
            refusal.message.starts_with(&format!("the pack of refs/yonder/1-1 {failure}")),
            "{failure}: {}",
            refusal.message
        );
    }

    assert!(!wait(&missing, limits(30_000, 30_000), &|| true).expect("no refusal"));
    assert_eq!(said, Vec::<String>::new(), "a short wait says nothing");
}

/// The chunks of a pack keep the wait alive past its stall time, and the mark of the landed pack ends it. A commit that
/// landed without its mark ends it at the stall too.
#[test]
fn the_chunks_of_a_pack_keep_the_wait_alive_and_its_mark_ends_it() {
    let (dir, repo, later, object, hidden) = repo_with_a_travelling_commit();
    let (run, _lock) = Runs::new(dir.path().join("runs")).create(&spec_of(&later)).expect("a run");
    let mut said = Vec::new();
    let started = Instant::now();
    let waited = thread::scope(|scope| {
        scope.spawn(|| {
            while started.elapsed() < Duration::from_millis(1500) {
                run.note_pack_progress().expect("a chunk noted");
                thread::sleep(Duration::from_millis(100));
            }
            run.mark_packed().expect("the pack landed");
        });
        wait_for_pack(&run, &repo, &spec_of(&later), limits(30_000, 500), &|| false, &mut |line| {
            said.push(line.to_owned());
        })
    });
    assert!(waited.expect("no refusal"));
    assert!(started.elapsed() >= Duration::from_millis(1500), "{:?}", started.elapsed());
    assert_eq!(said, vec![format!("waiting for the pack of {later}")]);

    let (other, _lock) = Runs::new(dir.path().join("runs"))
        .create(&RunSpec {
            run: "1-2".to_owned(),
            reference: "refs/yonder/1-2".to_owned(),
            ..spec_of(&later)
        })
        .expect("another run");
    let restore = thread::spawn(move || {
        thread::sleep(Duration::from_millis(100));
        fs::rename(&hidden, &object).expect("the object back");
    });
    let waited = wait_for_pack(&other, &repo, &spec_of(&later), limits(30_000, 400), &|| false, &mut |_| {});
    restore.join().expect("the restore");
    assert!(waited.expect("the commit came without a mark"));
}
