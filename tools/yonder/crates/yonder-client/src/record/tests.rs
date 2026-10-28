use std::time::SystemTime;

use pretty_assertions::assert_eq;

use super::*;
use crate::bd::Blob;

#[test]
fn a_record_goes_into_the_patch_directory_and_comes_back() {
    let dir = tempfile::tempdir().expect("a directory");
    let files = RunFiles::new(&dir.path().join(".git/yonder")).expect("the directory");
    assert!(files.dir().is_dir(), "{}", files.dir().display());
    assert_eq!(files.read("1-1").expect("no record"), None);
    let record = RunRecord {
        commit: "c".to_owned(),
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
    let base = tempfile::tempdir().expect("a directory");
    let dir = base.path().join("yonder");
    RunFiles::new(&dir).expect("the directory");
    let week_ago = SystemTime::now() - KEEP - Duration::from_secs(60);
    for name in ["1-1.json", "1-1.patch", "note.txt", "2-2.json"] {
        let file = fs::File::create(dir.join(name)).expect("a file");
        if name != "2-2.json" {
            file.set_modified(week_ago).expect("an old file");
        }
    }
    RunFiles::new(&dir).expect("the directory");
    let mut left: Vec<String> = fs::read_dir(&dir)
        .expect("the directory")
        .map(|entry| entry.expect("an entry").file_name().to_string_lossy().into_owned())
        .collect();
    left.sort();
    assert_eq!(left, ["2-2.json", "note.txt"]);
}

fn snapshot_with(blob: Option<&str>) -> Snapshot {
    Snapshot {
        commit: "c0ffee".to_owned(),
        blob: blob.map(|text| Blob { text: text.to_owned() }),
        ..Snapshot::default()
    }
}

/// The client reads the outputs from `.yonder.toml` of the shipped commit with the parser of the agent: a commit without
/// the file has none, and a file that does not parse refuses.
#[test]
fn the_outputs_come_from_the_config_of_the_commit() {
    assert_eq!(
        outputs_of(&snapshot_with(Some("outputs = [\"results\", \"target/junit.xml\"]\n"))).expect("the outputs"),
        ["results", "target/junit.xml"]
    );
    assert_eq!(outputs_of(&snapshot_with(None)).expect("no file"), Vec::<String>::new());
    let refusal = outputs_of(&snapshot_with(Some("outputs = [\"../up\"]\n"))).expect_err("a bad file");
    assert_eq!(refusal.code, code::BAD_CONFIG);
    assert!(
        refusal.message.starts_with(".yonder.toml: outputs names \"../up\"") && refusal.message.ends_with("in the commit c0ffee"),
        "{}",
        refusal.message
    );
}
