use std::fs::File;
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime};

use avl_base::Environment;
use jiff::tz::{TimeZone, offset};
use pretty_assertions::assert_eq;

use super::{BINARY_VARIABLE, SOURCES_DIR, Source, WORKSPACE_DIR, age, newest};

/// 2026-10-01 16:02:00 UTC, which is 18:02 in the zone of [`zone`].
const BUILT: u64 = 1_790_870_520;

fn zone() -> TimeZone {
    TimeZone::fixed(offset(2))
}

fn at(seconds: u64) -> SystemTime {
    SystemTime::UNIX_EPOCH + Duration::from_secs(seconds)
}

/// Writes a file at `path` with the mtime `modified`.
fn file(path: &Path, modified: SystemTime) {
    std::fs::create_dir_all(path.parent().expect("a parent")).expect("a directory");
    let file = File::create(path).expect("a file");
    file.set_modified(modified).expect("an mtime");
}

/// A checkout under `root` with two sources of the controller, the newer one at `newest`, and a binary built at
/// [`BUILT`].
fn checkout(root: &Path, newest: SystemTime) -> (PathBuf, PathBuf) {
    let repo = root.join("repo");
    let crates = repo.join(WORKSPACE_DIR).join(SOURCES_DIR);
    file(&crates.join("avl-vm/src/bench/command.rs"), at(BUILT - 600));
    file(&crates.join("avl-vm/src/bench/launch.rs"), newest);
    let binary = root.join("target/debug/vm");
    file(&binary, at(BUILT));
    (repo, binary)
}

fn environment(binary: &Path) -> Environment {
    Environment::from_pairs([(BINARY_VARIABLE, binary.display().to_string())])
}

#[test]
fn a_binary_newer_than_its_sources_is_a_note_without_a_warning() {
    let root = tempfile::tempdir().expect("a temporary root");
    let (repo, binary) = checkout(root.path(), at(BUILT - 60));
    let age = age(&environment(&binary), &repo).expect("an age");
    assert_eq!(age.binary, binary);
    assert_eq!(
        age.newest_source,
        Some(Source {
            path: PathBuf::from("crates/avl-vm/src/bench/launch.rs"),
            modified: at(BUILT - 60),
        })
    );
    assert_eq!(
        age.note_in(&zone()),
        format!(
            "AIR_VM_BIN {} built 2026-10-01 18:02, newest source 2026-10-01 18:01 crates/avl-vm/src/bench/launch.rs",
            binary.display()
        )
    );
    assert_eq!(age.warning(), None);
}

#[test]
fn a_source_newer_than_the_binary_is_a_warning() {
    let root = tempfile::tempdir().expect("a temporary root");
    let (repo, binary) = checkout(root.path(), at(BUILT + 98 * 60));
    let age = age(&environment(&binary), &repo).expect("an age");
    assert!(
        age.note_in(&zone())
            .ends_with(" built 2026-10-01 18:02, newest source 2026-10-01 19:40 crates/avl-vm/src/bench/launch.rs"),
        "{}",
        age.note_in(&zone())
    );
    assert_eq!(
        age.warning().as_deref(),
        Some("the controller binary is older than its sources; rebuild it or unset AIR_VM_BIN")
    );
}

#[test]
fn the_test_sources_are_no_sources_of_the_binary() {
    let root = tempfile::tempdir().expect("a temporary root");
    let (repo, binary) = checkout(root.path(), at(BUILT - 60));
    let crates = repo.join(WORKSPACE_DIR).join(SOURCES_DIR);
    for test in [
        "avl-vm/testdata/bench/ls.txt",
        "avl-vm/src/bench/ls/tests.rs",
        "avl-vm/src/bench/tests.rs",
        "avl-vm/tests/cli.rs",
    ] {
        file(&crates.join(test), at(BUILT + 600));
    }
    let age = age(&environment(&binary), &repo).expect("an age");
    assert_eq!(
        age.newest_source.as_ref().map(|source| source.path.as_path()),
        Some(Path::new("crates/avl-vm/src/bench/launch.rs"))
    );
    assert_eq!(age.warning(), None, "a golden update alone is no newer source");
}

#[test]
fn of_two_files_with_one_mtime_the_first_path_is_the_newest() {
    let root = tempfile::tempdir().expect("a temporary root");
    for name in ["b.rs", "a/z.rs", "c.rs"] {
        file(&root.path().join(name), at(BUILT));
    }
    assert_eq!(newest(root.path()), Some((root.path().join("a/z.rs"), at(BUILT))));
}

#[test]
fn no_variable_no_binary_and_no_sources() {
    let root = tempfile::tempdir().expect("a temporary root");
    let (repo, binary) = checkout(root.path(), at(BUILT));
    assert_eq!(
        age(&Environment::from_pairs([("HOME", "/h")]), &repo),
        None,
        "the variable is unset"
    );
    assert_eq!(
        age(&Environment::from_pairs([(BINARY_VARIABLE, "")]), &repo),
        None,
        "the variable is empty"
    );
    let absent = root.path().join("absent");
    assert_eq!(age(&environment(&absent), &repo), None, "the binary has no mtime");

    let age = age(&environment(&binary), &root.path().join("elsewhere")).expect("an age");
    assert_eq!(age.newest_source, None);
    assert!(
        age.note_in(&zone())
            .ends_with(" built 2026-10-01 18:02, no source under community/tools/vm/crates"),
        "{}",
        age.note_in(&zone())
    );
    assert_eq!(age.warning(), None);
}
