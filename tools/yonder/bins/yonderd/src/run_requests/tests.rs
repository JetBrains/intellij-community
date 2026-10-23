use pretty_assertions::assert_eq;

use super::*;

#[test]
fn the_stage_lists_its_regular_files_in_name_order() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let stage = dir.path().join("stage");
    fs::create_dir_all(stage.join("out/bt-runs/b")).expect("a directory");
    fs::write(stage.join("out/bt-runs/b/report.xml"), "<x/>").expect("a file");
    fs::write(stage.join("out/bt-runs/a.sh"), "#!/bin/sh\n").expect("a file");
    fs::set_permissions(stage.join("out/bt-runs/a.sh"), fs::Permissions::from_mode(0o755)).expect("executable");
    std::os::unix::fs::symlink("a.sh", stage.join("out/bt-runs/link")).expect("a link");
    let mut files = Vec::new();
    staged_files(&stage, "", &FileKind::Staged, &mut files).expect("listed");
    let listed: Vec<(&str, bool)> = files.iter().map(|file| (file.relative.as_str(), file.executable)).collect();
    assert_eq!(listed, vec![("out/bt-runs/a.sh", true), ("out/bt-runs/b/report.xml", false)]);
    let (_, size) = open_regular(&stage.join("out/bt-runs/b/report.xml")).expect("a regular file");
    assert_eq!(size, 4);
    assert!(open_regular(&stage.join("out/bt-runs/link")).is_err(), "a link is not followed");

    let mut none = Vec::new();
    staged_files(&dir.path().join("missing"), "", &FileKind::Staged, &mut none).expect("a missing stage has no files");
    assert_eq!(none, Vec::new());
}

#[test]
fn a_fetch_sends_the_stage_then_each_output_then_the_changes() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let run = RunDir::new(dir.path().join("run"));
    fs::create_dir_all(run.stage_path().join("out")).expect("the stage");
    fs::write(run.stage_path().join("out/new.txt"), "n").expect("a staged file");
    fs::create_dir_all(run.outputs_path().join("tests/results/a")).expect("an output directory");
    fs::write(run.outputs_path().join("tests/results/a/trace.zip"), "t").expect("an output file");
    fs::create_dir_all(run.outputs_path().join("target/ci")).expect("the parent of an output file");
    fs::write(run.outputs_path().join("target/ci/junit.xml"), "<x/>").expect("an output file");
    run.write_output_roots(&["tests/results".to_owned(), "target/ci/junit.xml".to_owned()])
        .expect("the outputs");
    fs::write(run.changes_path(), "diff --git a/x b/x\n").expect("the changes");
    let files = run_files(&run).expect("the files");
    let listed: Vec<(&str, &FileKind)> = files.iter().map(|file| (file.relative.as_str(), &file.kind)).collect();
    let results = FileKind::Output {
        root: "tests/results".to_owned(),
    };
    let junit = FileKind::Output {
        root: "target/ci/junit.xml".to_owned(),
    };
    assert_eq!(
        listed,
        vec![
            ("out/new.txt", &FileKind::Staged),
            ("tests/results/a/trace.zip", &results),
            ("target/ci/junit.xml", &junit),
            ("changes.patch", &FileKind::Changes),
        ]
    );

    let bare = RunDir::new(dir.path().join("bare"));
    fs::create_dir_all(bare.path()).expect("a run without a stage");
    assert_eq!(run_files(&bare).expect("no files"), Vec::new());
}
