use std::fs;
use std::os::unix::fs::{PermissionsExt, symlink};
use std::path::Path;

use pretty_assertions::assert_eq;

use super::*;

/// The type checks follow a link. An absent path is neither a file nor a directory.
#[test]
fn the_type_checks_follow_a_link_and_refuse_an_absent_path() {
    let root = tempfile::tempdir().unwrap();
    let file = root.path().join("file");
    fs::write(&file, "bytes").unwrap();
    let file_link = root.path().join("file-link");
    symlink(&file, &file_link).unwrap();
    let directory_link = root.path().join("directory-link");
    symlink(root.path(), &directory_link).unwrap();

    assert!(is_file(&file) && is_file(&file_link));
    assert!(!is_directory(&file) && !is_directory(&file_link));
    assert!(is_directory(root.path()) && is_directory(&directory_link));
    assert!(!is_file(root.path()) && !is_file(&directory_link));
    let absent = root.path().join("absent");
    assert!(!is_file(&absent) && !is_directory(&absent));
}

/// The copy keeps the bytes and the permission bits of each file, and a link in the tree stays a link. A link at the
/// source is followed. A later change of the source does not reach the copy, and a destination that exists is refused.
#[test]
fn a_copied_tree_keeps_its_bytes_its_modes_and_its_links() {
    let root = tempfile::tempdir().unwrap();
    let source = root.path().join("source");
    fs::create_dir_all(source.join("bin")).unwrap();
    fs::write(source.join("bin/tool"), "#!/bin/sh\n").unwrap();
    fs::set_permissions(source.join("bin/tool"), fs::Permissions::from_mode(0o755)).unwrap();
    fs::create_dir_all(source.join("lib")).unwrap();
    fs::write(source.join("lib/data.txt"), "first bytes").unwrap();
    fs::set_permissions(source.join("lib/data.txt"), fs::Permissions::from_mode(0o644)).unwrap();
    fs::create_dir_all(source.join("links")).unwrap();
    symlink("../lib/data.txt", source.join("links/data.txt")).unwrap();

    let copy = root.path().join("copy");
    copy_tree(&source, &copy).unwrap();

    let tool = fs::symlink_metadata(copy.join("bin/tool")).unwrap();
    assert!(tool.is_file(), "the copied tool is not a regular file");
    assert_eq!(tool.permissions().mode() & 0o777, 0o755, "the copied tool lost its executable bits");
    let data = fs::symlink_metadata(copy.join("lib/data.txt")).unwrap();
    assert_eq!(data.permissions().mode() & 0o777, 0o644);
    assert_eq!(fs::read_link(copy.join("links/data.txt")).unwrap(), Path::new("../lib/data.txt"));
    assert_eq!(fs::read_to_string(copy.join("links/data.txt")).unwrap(), "first bytes");

    let alias = root.path().join("alias");
    symlink(&source, &alias).unwrap();
    let through_link = root.path().join("through-link");
    copy_tree(&alias, &through_link).unwrap();
    assert!(
        fs::symlink_metadata(&through_link).unwrap().is_dir(),
        "the copy of a linked source is a link"
    );
    assert_eq!(fs::read_to_string(through_link.join("lib/data.txt")).unwrap(), "first bytes");

    fs::write(source.join("lib/data.txt"), "rebuilt bytes").unwrap();
    assert_eq!(
        fs::read_to_string(copy.join("lib/data.txt")).unwrap(),
        "first bytes",
        "the copy reads the source"
    );
    assert!(copy_tree(&source, &copy).is_err(), "a copy onto an existing tree was accepted");
}

/// A staging tree that its verb drops before the rename is gone, and a published tree stays at its destination. A
/// new staging tree removes what an earlier process with the same pid left at its path.
#[test]
fn a_staging_tree_is_removed_unless_it_was_published() {
    let root = tempfile::tempdir().unwrap();
    let left = root.path().join(format!(".stage-project-{}", std::process::id()));
    fs::create_dir_all(left.join("src")).unwrap();
    fs::write(left.join("src/Main.java"), "left by an earlier process").unwrap();

    let dropped = StagingTree::new(root.path(), "project");
    assert_eq!(dropped.path(), left);
    assert!(!left.exists(), "the tree of an earlier process with this pid survived");
    fs::create_dir_all(dropped.path().join("src")).unwrap();
    drop(dropped);
    assert!(!left.exists(), "a dropped staging tree survived");

    let published = StagingTree::new(root.path(), "project");
    fs::create_dir_all(published.path().join("src")).unwrap();
    fs::write(published.path().join("src/Main.java"), "class Main {}").unwrap();
    let destination = root.path().join("project");
    published.publish(&destination).unwrap();
    assert_eq!(fs::read_to_string(destination.join("src/Main.java")).unwrap(), "class Main {}");
    assert!(!left.exists(), "the published tree is still at its staging path");
}
