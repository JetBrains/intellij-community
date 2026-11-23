//! The `.git` file of a linked Git worktree, which names the main checkout of the worktree.

use std::path::{Path, PathBuf};

use crate::runtime::normalize_path;

/// The main checkout of the linked worktree `root`, from the `gitdir:` line of `dot_git`, the text of its `.git` file.
///
/// The line names `<main>/.git/worktrees/<name>`, absolute or relative to `root`. Any other text answers `None`: a main
/// checkout has a `.git` directory, and a submodule names `.git/modules/<name>`.
pub fn linked_worktree_main_root(root: &Path, dot_git: &str) -> Option<PathBuf> {
    let git_dir = dot_git.lines().find_map(|line| line.strip_prefix("gitdir:"))?.trim();
    if git_dir.is_empty() {
        return None;
    }
    let joined = root.join(git_dir);
    let worktrees = normalize_path(&std::path::absolute(&joined).unwrap_or(joined)).parent()?.to_owned();
    let common = worktrees.parent()?;
    (worktrees.file_name()? == "worktrees" && common.file_name()? == ".git").then(|| common.parent().map(Path::to_owned))?
}

#[cfg(test)]
mod tests;
