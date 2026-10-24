use std::path::PathBuf;

use pretty_assertions::assert_eq;

use super::*;

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

#[test]
fn the_pack_wait_ends_at_once_for_a_present_commit_at_its_deadline_or_at_a_stop() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let repo = dir.path().join("repo");
    fs::create_dir(&repo).expect("the repository directory");
    git_in(&repo, &["init", "-q"]);
    git_in(&repo, &["commit", "-q", "--allow-empty", "-m", "first"]);
    let present = git_in(&repo, &["rev-parse", "HEAD"]);
    let mut said = Vec::new();

    // A commit that the host has already needs no pack.
    let started = Instant::now();
    let waited = wait_for_pack(&repo, &spec_of(&present), Duration::from_secs(30), &|| false, &mut |line| {
        said.push(line.to_owned());
    });
    assert!(waited.expect("no refusal"));
    assert!(started.elapsed() < Duration::from_secs(5), "{:?}", started.elapsed());

    let missing = "1".repeat(40);
    let refusal = wait_for_pack(&repo, &spec_of(&missing), Duration::from_millis(300), &|| false, &mut |line| {
        said.push(line.to_owned());
    })
    .expect_err("a refusal");
    assert_eq!(refusal.code, PACK_MISSING);
    assert!(refusal.message.contains("refs/yonder/1-1"), "{}", refusal.message);

    let waited = wait_for_pack(&repo, &spec_of(&missing), Duration::from_secs(30), &|| true, &mut |line| {
        said.push(line.to_owned());
    });
    assert!(!waited.expect("no refusal"));
    assert_eq!(said, Vec::<String>::new(), "a short wait says nothing");
}

#[test]
fn the_pack_wait_of_a_missing_commit_ends_with_its_reference_and_says_so_after_a_second() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let repo = dir.path().join("repo");
    fs::create_dir(&repo).expect("the repository directory");
    git_in(&repo, &["init", "-q"]);
    git_in(&repo, &["commit", "-q", "--allow-empty", "-m", "first"]);
    let first = git_in(&repo, &["rev-parse", "HEAD"]);
    let tree = git_in(&repo, &["rev-parse", "HEAD:"]);
    let later = git_in(&repo, &["commit-tree", &tree, "-p", &first, "-m", "later"]);
    // The commit object goes away for a while, as if its pack still travelled.
    let object = repo.join(".git/objects").join(&later[..2]).join(&later[2..]);
    let hidden = dir.path().join("hidden");
    fs::rename(&object, &hidden).expect("the object hidden");
    let mut said = Vec::new();

    // The commit alone does not end the wait: the pack points the reference at it a moment later.
    let (back, ready) = (object.clone(), hidden.clone());
    let restore = thread::spawn(move || {
        thread::sleep(Duration::from_millis(200));
        fs::rename(&ready, &back).expect("the object back");
    });
    let refusal = wait_for_pack(&repo, &spec_of(&later), Duration::from_millis(600), &|| false, &mut |line| {
        said.push(line.to_owned());
    })
    .expect_err("a refusal");
    restore.join().expect("the restore");
    assert_eq!(refusal.code, PACK_MISSING);

    fs::rename(&object, &hidden).expect("the object hidden again");
    let pack = {
        let repo = repo.clone();
        let later = later.clone();
        thread::spawn(move || {
            thread::sleep(Duration::from_millis(1500));
            fs::rename(&hidden, &object).expect("the object back");
            git_in(&repo, &["update-ref", "refs/yonder/1-1", &later]);
        })
    };
    let started = Instant::now();
    let waited = wait_for_pack(&repo, &spec_of(&later), Duration::from_secs(30), &|| false, &mut |line| {
        said.push(line.to_owned());
    });
    pack.join().expect("the pack");
    assert!(waited.expect("no refusal"));
    assert!(started.elapsed() < Duration::from_secs(10), "{:?}", started.elapsed());
    assert_eq!(said, vec![format!("waiting for the pack of {later}")]);
}
