use std::fs::File;
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime};

use avl_base::Reporter;
use jiff::tz::{TimeZone, offset};
use pretty_assertions::assert_eq;
use serde_json::json;

use super::report;
use crate::bench::command::replay;
use crate::bench::files;
use crate::bench::session::{SESSION_FILE, SUMMARY_FILE, SessionInfo, locate, read_info};
use crate::bench::testing::{assert_golden, testdata_dir};

/// 2026-10-01 16:02:00 UTC, which is 18:02 in the zone of [`zone`]: the start of the newest session.
const NEWEST: u64 = 1_790_870_520;

fn zone() -> TimeZone {
    TimeZone::fixed(offset(2))
}

/// A copy of the fixture session at `<runs>/<name>`, replayed when `replayed`, whose `session.json` `edit` changes and
/// whose mtime is `age_minutes` before [`NEWEST`].
fn session(runs: &Path, name: &str, age_minutes: u64, replayed: bool, edit: impl FnOnce(&mut SessionInfo)) -> PathBuf {
    let dir = runs.join(name);
    avl_testkit::traces::copy_tree(&testdata_dir().join("session"), &dir);
    if replayed {
        let (reporter, _, _) = Reporter::in_memory("vm");
        replay(&dir, &reporter).expect("a summary");
    }
    let mut info = read_info(&dir).expect("session.json");
    edit(&mut info);
    files::write_json(&dir.join(SESSION_FILE), &info).expect("session.json");
    let file = File::options().write(true).open(dir.join(SESSION_FILE)).expect("session.json");
    file.set_modified(SystemTime::UNIX_EPOCH + Duration::from_secs(NEWEST - age_minutes * 60))
        .expect("an mtime");
    dir
}

/// Four sessions, newest first: another commit with changes, the fixture, one without a summary, and one with a
/// summary of another shape.
fn runs(root: &Path) -> PathBuf {
    let runs = root.join("runs");
    session(&runs, "develar-bench-6291e91e-935b", 0, true, |info| {
        "b0b0b0b0b0b01111111111111111111111111111".clone_into(&mut info.git.commit);
        info.git.dirty = true;
        "0b0b0b0b0b0b4c3e8d6f1a0b9c8e7d6f5a4b3c2d1e0f9a8b7c6d5e4f3a2b1c0d".clone_into(&mut info.generation.dist_digest);
    });
    session(&runs, "develar-bench-aac81325-1c2d", 11, true, |_| {});
    session(&runs, "develar-bench-f8d8cadd-0a1b", 22, false, |info| {
        info.command = "open-project".to_owned();
    });
    let broken = session(&runs, "develar-bench-cf380279-7e7e", 33, true, |_| {});
    std::fs::write(broken.join(SUMMARY_FILE), "{}").expect("a summary of another shape");
    runs
}

/// The text of `bench ls` over [`runs`] equals `testdata/bench/ls.txt`.
#[test]
fn the_listing_matches_the_golden() {
    let root = tempfile::tempdir().expect("a temporary root");
    let runs = runs(root.path());
    let text = report(&runs, 10).text(&zone()).replace(&runs.display().to_string(), "<runs>");
    assert_golden("ls.txt", &format!("{text}\n"));
}

#[test]
fn the_listing_is_newest_first_with_the_names_that_the_locator_accepts() {
    let root = tempfile::tempdir().expect("a temporary root");
    let runs = runs(root.path());
    let listing = report(&runs, 10);
    assert_eq!(listing.total, 4);
    let names: Vec<&str> = listing.sessions.iter().map(|listed| listed.name.as_str()).collect();
    assert_eq!(
        names,
        [
            "develar-bench-6291e91e-935b",
            "develar-bench-aac81325-1c2d",
            "develar-bench-f8d8cadd-0a1b",
            "develar-bench-cf380279-7e7e"
        ]
    );
    for listed in &listing.sessions {
        assert_eq!(
            locate(&listed.name, Path::new("/"), &runs).expect("a session"),
            PathBuf::from(&listed.path)
        );
    }
    assert_eq!(
        locate("latest", Path::new("/"), &runs).expect("the latest"),
        PathBuf::from(&listing.sessions[0].path),
        "the first line is the latest"
    );

    let limited = report(&runs, 2);
    assert_eq!((limited.total, limited.sessions.len()), (4, 2));
    assert_eq!(limited.sessions[..], listing.sessions[..2]);
}

#[test]
fn the_json_holds_the_inputs_and_the_median_per_arm() {
    let root = tempfile::tempdir().expect("a temporary root");
    let runs = runs(root.path());
    let data = serde_json::to_value(report(&runs, 10)).expect("JSON");
    let first = &data["sessions"][0];
    assert_eq!(first["name"], "develar-bench-6291e91e-935b");
    assert_eq!(first["modified"], "2026-10-01T16:02:00Z");
    assert_eq!(
        (&first["command"], &first["commit"], &first["dirty"]),
        (&json!("welcome"), &json!("b0b0b0b0b0b01111111111111111111111111111"), &json!(true))
    );
    assert_eq!(
        first["arms"]["nonModal"],
        json!({"validRuns": 1, "measuredRuns": 1, "metric": "welcomeBecameVisible", "median": 2718.0})
    );
    assert!(first.get("modifiedAt").is_none(), "{first}");
    let unreplayed = &data["sessions"][2];
    assert_eq!((&unreplayed["arms"], &unreplayed["notes"]), (&json!(null), &json!([])));
    let broken = &data["sessions"][3];
    assert_eq!(broken["arms"], json!(null));
    assert!(
        broken["notes"][0].as_str().is_some_and(|note| note.contains("is not a summary")),
        "{broken}"
    );
}

#[test]
fn a_host_without_a_session_has_an_empty_listing() {
    let root = tempfile::tempdir().expect("a temporary root");
    let runs = root.path().join("runs");
    let listing = report(&runs, 10);
    assert_eq!((listing.total, listing.sessions.len()), (0, 0));
    assert_eq!(listing.text(&zone()), format!("vm bench ls: no session under {}", runs.display()));
}
