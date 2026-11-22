use std::path::{Path, PathBuf};

use pretty_assertions::assert_eq;

use super::*;

fn absolute(path: &str) -> PathBuf {
    std::path::absolute(path).unwrap()
}

#[test]
fn an_absolute_gitdir_names_the_main_checkout() {
    let main = linked_worktree_main_root(Path::new("/pool/lane"), "gitdir: /main/.git/worktrees/lane\n");
    assert_eq!(main, Some(absolute("/main")));
}

#[test]
fn a_relative_gitdir_resolves_against_the_worktree_root() {
    let main = linked_worktree_main_root(Path::new("/pool/lane"), "gitdir: ../../main/.git/worktrees/lane\n");
    assert_eq!(main, Some(absolute("/main")));
}

#[test]
fn a_gitdir_outside_the_worktrees_of_a_main_checkout_names_none() {
    let root = Path::new("/main/module");
    assert_eq!(linked_worktree_main_root(root, "gitdir: /main/.git/modules/module\n"), None);
    assert_eq!(linked_worktree_main_root(root, "gitdir: \n"), None);
    assert_eq!(linked_worktree_main_root(root, "ref: refs/heads/master\n"), None);
}
