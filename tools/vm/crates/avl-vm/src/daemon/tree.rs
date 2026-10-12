//! The host checkout a build read: its HEAD and its uncommitted paths under the lane's source roots.

use std::time::Duration;

use avl_base::{Config, Refusal};
use avl_host_sys::{Ctx, Runner, SpawnOptions};
use avl_wire::progress::Checkout;
use avl_wire::report::{MAX_TREE_PATHS, Tree};

#[cfg(test)]
#[cfg(unix)]
mod tests;

/// The checkout subtrees whose uncommitted paths a run report names.
pub(crate) const TREE_ROOTS: [&str; 2] = ["plugins/air", "community/platform"];

/// Bounds each of the two host `git` calls. A `git status` of the two roots takes some seconds on a large checkout.
const TREE_PROBE_TIMEOUT: Duration = Duration::from_secs(60);

/// The HEAD commit of the host checkout and its uncommitted paths under [`TREE_ROOTS`].
///
/// It never fails the run. Every failure becomes the report's `treeError`, because the tree only explains a run.
/// `--no-optional-locks` keeps `git status` from writing the shared index, which other sessions use.
pub(crate) async fn read_checkout_tree(ctx: &Ctx, runner: &Runner, settings: &Config) -> Result<Tree, Refusal> {
    let repo = settings.host_repo()?.to_string_lossy().into_owned();
    let options = SpawnOptions::timeout(TREE_PROBE_TIMEOUT, "host_git_timeout");
    let git = settings.git.as_str();
    let head_argv: Vec<String> = [git, "-C", &repo, "rev-parse", "--verify", "HEAD"].map(str::to_owned).into();
    let head = runner.checked(ctx, &head_argv, &options).await?;
    let mut status_argv: Vec<String> = [
        git,
        "--no-optional-locks",
        "-C",
        &repo,
        "status",
        "--porcelain=v1",
        "-z",
        "--no-renames",
        "--untracked-files=all",
        "--",
    ]
    .map(str::to_owned)
    .into();
    status_argv.extend(TREE_ROOTS.map(str::to_owned));
    let status = runner.checked(ctx, &status_argv, &options).await?;
    Ok(bounded_tree(head.stdout.trim(), porcelain_paths(&status.stdout)))
}

/// The paths of `git status --porcelain=v1 -z --no-renames`: one `XY <path>` record per path, each terminated by
/// NUL. Without renames, no record carries a second path.
fn porcelain_paths(output: &str) -> Vec<String> {
    output
        .split('\0')
        .filter_map(|record| record.get(3..).filter(|path| !path.is_empty()))
        .map(str::to_owned)
        .collect()
}

/// Keeps the first [`MAX_TREE_PATHS`] paths and counts all of them.
fn bounded_tree(head: &str, mut paths: Vec<String>) -> Tree {
    let count = paths.len();
    paths.truncate(MAX_TREE_PATHS);
    Tree {
        head: head.to_owned(),
        uncommitted: paths,
        uncommitted_count: count,
    }
}

/// The checkout that the build read, as the progress stream says it.
pub(crate) fn checkout_event(tree: &Result<Tree, Refusal>) -> Checkout {
    match tree {
        Ok(tree) => Checkout {
            head: Some(tree.head.clone()),
            uncommitted: u32::try_from(tree.uncommitted_count).unwrap_or(u32::MAX),
            error: None,
        },
        Err(refusal) => Checkout {
            head: None,
            uncommitted: 0,
            error: Some(refusal.message.clone()),
        },
    }
}
