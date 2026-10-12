use pretty_assertions::assert_eq;

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

#[test]
fn an_absent_file_is_none() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    assert_eq!(read_optional(&dir.path().join("absent")).expect("no error"), None);
}
