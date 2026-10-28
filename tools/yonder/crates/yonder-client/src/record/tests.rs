use std::time::SystemTime;

use pretty_assertions::assert_eq;

use super::*;
use crate::OsProcesses;
use crate::process::never;
use crate::testkit::real_git;

/// A checkout with one commit of `files`.
fn checkout_with(files: &[(&str, &str)]) -> (tempfile::TempDir, PathBuf, String) {
    let dir = tempfile::tempdir().expect("a directory");
    let root = dir.path().join("checkout");
    fs::create_dir_all(&root).expect("the checkout");
    real_git(&root, &["init", "-q"]);
    fs::write(root.join("a.txt"), "a\n").expect("a file");
    for (path, text) in files {
        fs::write(root.join(path), text).expect("a file");
    }
    real_git(&root, &["add", "--all"]);
    real_git(&root, &["commit", "-q", "-m", "first"]);
    let commit = real_git(&root, &["rev-parse", "HEAD"]);
    (dir, root, commit)
}

#[test]
fn a_record_goes_into_the_git_directory_and_comes_back() {
    let (_dir, root, commit) = checkout_with(&[]);
    let files = RunFiles::of(&OsProcesses, &root, &never).expect("the directory");
    assert!(files.dir().ends_with(".git/yonder"), "{}", files.dir().display());
    assert_eq!(files.read("1-1").expect("no record"), None);
    let record = RunRecord {
        commit,
        fetch: vec!["out/bt-runs".to_owned()],
        outputs: vec!["results".to_owned()],
        changes: true,
    };
    files.write("1-1", &record).expect("the record");
    assert_eq!(files.read("1-1").expect("the record"), Some(record));
    assert_eq!(files.patch("1-1"), files.dir().join("1-1.patch"));

    fs::write(files.dir().join("2-2.json"), "{\"commit\": \"c\", \"host\": \"h\"}").expect("a bad record");
    let refusal = files.read("2-2").expect_err("an unknown field");
    assert_eq!(refusal.code, code::USAGE);
    assert!(refusal.message.contains("is not a run record"), "{}", refusal.message);
}

#[test]
fn the_records_and_the_patches_of_a_week_ago_go() {
    let (_dir, root, _) = checkout_with(&[]);
    let dir = RunFiles::of(&OsProcesses, &root, &never)
        .expect("the directory")
        .dir()
        .to_path_buf();
    let week_ago = SystemTime::now() - KEEP - Duration::from_secs(60);
    for name in ["1-1.json", "1-1.patch", "note.txt", "2-2.json"] {
        let file = fs::File::create(dir.join(name)).expect("a file");
        if name != "2-2.json" {
            file.set_modified(week_ago).expect("an old file");
        }
    }
    RunFiles::of(&OsProcesses, &root, &never).expect("the directory");
    let mut left: Vec<String> = fs::read_dir(&dir)
        .expect("the directory")
        .map(|entry| entry.expect("an entry").file_name().to_string_lossy().into_owned())
        .collect();
    left.sort();
    assert_eq!(left, ["2-2.json", "note.txt"]);
}

/// The client reads the outputs from the shipped commit with the parser of the agent: a file beside the commit never
/// counts, a commit without the file has none, and a file that does not parse refuses.
#[test]
fn the_outputs_come_from_the_config_of_the_commit() {
    let (_dir, root, commit) = checkout_with(&[(".yonder.toml", "outputs = [\"results\", \"target/junit.xml\"]\n")]);
    fs::write(root.join(".yonder.toml"), "outputs = [\"src\"]\n").expect("a local change");
    assert_eq!(
        outputs_of(&OsProcesses, &root, &commit, &never).expect("the outputs"),
        ["results", "target/junit.xml"]
    );

    let (_dir, root, commit) = checkout_with(&[]);
    fs::write(root.join(".yonder.toml"), "outputs = [\"src\"]\n").expect("an untracked file");
    assert_eq!(
        outputs_of(&OsProcesses, &root, &commit, &never).expect("no file"),
        Vec::<String>::new()
    );

    let (_dir, root, commit) = checkout_with(&[(".yonder.toml", "outputs = [\"../up\"]\n")]);
    let refusal = outputs_of(&OsProcesses, &root, &commit, &never).expect_err("a bad file");
    assert_eq!(refusal.code, code::BAD_CONFIG);
    assert!(
        refusal.message.starts_with(".yonder.toml: outputs names \"../up\"") && refusal.message.contains(&commit),
        "{}",
        refusal.message
    );

    let refusal = outputs_of(&OsProcesses, &root, "0123456789abcdef0123456789abcdef01234567", &never).expect_err("no commit");
    assert_eq!(refusal.code, code::GIT_FAILED);
}
