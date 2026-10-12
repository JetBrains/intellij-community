#[cfg(unix)]
use std::path::Path;
#[cfg(unix)]
use std::time::Duration;
use std::time::SystemTime;

use pretty_assertions::assert_eq;

use super::{GcReport, collect};
#[cfg(unix)]
use super::{Kept, KeptBecause, RECENT};

#[test]
fn a_runtime_root_without_generations_removes_nothing() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    assert_eq!(collect(dir.path(), 2, SystemTime::now()).expect("a report"), GcReport::default());
}

/// A generation with a read-only file of `bytes` bytes, whose directory has the mtime `age` before `now`.
#[cfg(unix)]
fn generation(root: &Path, digest: &str, bytes: usize, now: SystemTime, age: Duration) {
    let path = root.join("bench/generations").join(digest);
    std::fs::create_dir_all(path.join("idea_dist.dist")).expect("a generation");
    std::fs::write(path.join("generation.json"), "{}").expect("a record");
    let jar = path.join("idea_dist.dist/a.jar");
    std::fs::write(&jar, vec![0; bytes]).expect("a jar");
    let mut permissions = std::fs::metadata(&jar).expect("the jar").permissions();
    permissions.set_readonly(true);
    std::fs::set_permissions(&jar, permissions).expect("a read-only jar");
    std::fs::File::open(&path)
        .and_then(|directory| directory.set_modified(now - age))
        .expect("an mtime");
}

/// A session under `bench/runs` whose summary names `digest` and changed `age` before `now`.
#[cfg(unix)]
fn session(root: &Path, name: &str, digest: &str, now: SystemTime, age: Duration) {
    let summary = root.join("bench/runs").join(name).join("summary.json");
    std::fs::create_dir_all(summary.parent().expect("a session")).expect("a session");
    std::fs::write(&summary, format!(r#"{{"distDigest":"{digest}","command":"welcome"}}"#)).expect("a summary");
    std::fs::File::options()
        .write(true)
        .open(&summary)
        .and_then(|file| file.set_modified(now - age))
        .expect("an mtime");
}

#[cfg(unix)]
#[test]
fn keeps_the_newest_and_the_recently_named_and_removes_the_rest() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let root = dir.path();
    let now = SystemTime::now();
    let hour = Duration::from_secs(3600);
    generation(root, "aaa", 10, now, hour);
    generation(root, "bbb", 20, now, hour * 2);
    generation(root, "ccc", 30, now, hour * 3);
    generation(root, "ddd", 40, now, hour * 4);
    generation(root, "eee", 50, now, hour * 5);
    session(root, "recent", "ddd", now, hour);
    session(root, "old", "eee", now, RECENT + hour);
    std::fs::create_dir_all(root.join("bench/generations/.stage-fff-123/x")).expect("an unfinished stage");

    let report = collect(root, 2, now).expect("a report");
    assert_eq!(
        report.kept,
        vec![
            Kept {
                digest: "aaa".to_owned(),
                because: KeptBecause::Newest
            },
            Kept {
                digest: "bbb".to_owned(),
                because: KeptBecause::Newest
            },
            Kept {
                digest: "ddd".to_owned(),
                because: KeptBecause::RecentSession
            },
        ]
    );
    let removed: Vec<(&str, u64)> = report
        .removed
        .iter()
        .map(|removed| (removed.name.as_str(), removed.bytes))
        .collect();
    // Each size is the jar and the two bytes of `generation.json`.
    assert_eq!(removed, vec![(".stage-fff-123", 0), ("ccc", 32), ("eee", 52)]);
    let left: Vec<String> = {
        let mut left: Vec<String> = std::fs::read_dir(root.join("bench/generations"))
            .expect("the generations")
            .map(|entry| entry.expect("an entry").file_name().to_string_lossy().into_owned())
            .collect();
        left.sort();
        left
    };
    assert_eq!(left, vec!["aaa", "bbb", "ddd"]);
    assert!(report.text().starts_with("removed .stage-fff-123 (0 MiB): "), "{}", report.text());
    assert!(report.text().ends_with("3 removed, 0 MiB freed, 3 kept"), "{}", report.text());

    let again = collect(root, 0, now).expect("a report");
    let removed: Vec<&str> = again.removed.iter().map(|removed| removed.name.as_str()).collect();
    assert_eq!(removed, vec!["aaa", "bbb"], "--keep 0 still keeps what a recent session names");
}
