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
