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
    staged_files(&stage, "", &mut files).expect("listed");
    let listed: Vec<(&str, bool)> = files.iter().map(|file| (file.relative.as_str(), file.executable)).collect();
    assert_eq!(listed, vec![("out/bt-runs/a.sh", true), ("out/bt-runs/b/report.xml", false)]);
    let (_, size) = open_regular(&stage.join("out/bt-runs/b/report.xml")).expect("a regular file");
    assert_eq!(size, 4);
    assert!(open_regular(&stage.join("out/bt-runs/link")).is_err(), "a link is not followed");

    let mut none = Vec::new();
    staged_files(&dir.path().join("missing"), "", &mut none).expect("a missing stage has no files");
    assert_eq!(none, Vec::new());
}
