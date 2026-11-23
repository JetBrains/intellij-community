use pretty_assertions::assert_eq;

#[cfg(unix)]
use super::copy_dir_resolved;
use super::{copy_dir, read_optional, write_text};

#[test]
fn copies_a_tree_with_its_links() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let from = dir.path().join("from");
    write_text(&from.join("a/b.txt"), "b").expect("a file");
    #[cfg(unix)]
    std::os::unix::fs::symlink("a/b.txt", from.join("link")).expect("a link");
    let to = dir.path().join("to");
    copy_dir(&from, &to).expect("a copy");
    assert_eq!(std::fs::read_to_string(to.join("a/b.txt")).expect("the copy"), "b");
    #[cfg(unix)]
    assert_eq!(
        std::fs::read_link(to.join("link")).expect("the link"),
        std::path::Path::new("a/b.txt")
    );
}

/// A resolved copy holds the file and the directory that a link names, and no link.
#[cfg(unix)]
#[test]
fn a_resolved_copy_holds_what_the_links_name() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let packed = dir.path().join("packed");
    write_text(&packed.join("lib/a.jar"), "a").expect("a file");
    write_text(&packed.join("plugin.xml"), "p").expect("a file");
    let from = dir.path().join("from");
    std::fs::create_dir_all(from.join("plugin")).expect("a directory");
    std::os::unix::fs::symlink(packed.join("lib"), from.join("plugin/lib")).expect("a link to a tree");
    std::os::unix::fs::symlink(packed.join("plugin.xml"), from.join("plugin/plugin.xml")).expect("a link to a file");
    let to = dir.path().join("to");
    copy_dir_resolved(&from, &to).expect("a copy");
    for (path, text) in [("plugin/lib/a.jar", "a"), ("plugin/plugin.xml", "p")] {
        assert_eq!(std::fs::read_to_string(to.join(path)).expect("the copy"), text);
        assert!(!to.join(path).is_symlink(), "{path}");
    }
    assert!(!to.join("plugin/lib").is_symlink());
}

#[test]
fn an_absent_file_is_none() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    assert_eq!(read_optional(&dir.path().join("absent")).expect("no error"), None);
}
