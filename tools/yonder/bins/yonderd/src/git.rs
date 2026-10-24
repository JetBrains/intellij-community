//! The git calls of the agent. Each call has a deadline and answers a message for a person when it fails.

use std::path::Path;
use std::process::Command;
use std::time::Duration;

use crate::child::{self, Output, Running, Stop, never};

/// The deadline of a call that reads or writes a few objects or refs.
pub(crate) const QUICK: Duration = Duration::from_secs(60);

/// The deadline of a checkout in a slot. A checkout of the monorepo writes many files.
pub(crate) const CHECKOUT: Duration = Duration::from_secs(30 * 60);

/// The deadline of `index-pack` for one pack.
pub(crate) const INDEX_PACK: Duration = Duration::from_secs(30 * 60);

/// The namespace of the refs that a pack makes.
pub(crate) const REF_PREFIX: &str = "refs/yonder/";

fn git(dir: &Path) -> Command {
    let mut command = Command::new("git");
    command.arg("-C").arg(dir).env("GIT_TERMINAL_PROMPT", "0");
    command
}

fn checked(output: Result<Output, child::ChildError>) -> Result<Output, String> {
    let output = output.map_err(|error| error.to_string())?;
    if output.status.success() {
        Ok(output)
    } else {
        Err(format!("{} ({})", output.diagnostic(), output.status))
    }
}

/// Whether `repo` is a git repository.
pub(crate) fn is_repo(repo: &Path) -> bool {
    repo.is_dir() && child::run(git(repo).args(["rev-parse", "--git-dir"]), QUICK, &never).is_ok_and(|output| output.status.success())
}

/// Whether `text` is a full commit id: 40 or 64 lower-case hex digits.
pub(crate) fn is_commit_id(text: &str) -> bool {
    matches!(text.len(), 40 | 64) && text.bytes().all(|byte| matches!(byte, b'0'..=b'9' | b'a'..=b'f'))
}

/// Whether `reference` is a ref that a pack may make: `refs/yonder/<run id>`.
pub(crate) fn is_pack_ref(reference: &str) -> bool {
    reference.strip_prefix(REF_PREFIX).is_some_and(yonder_link::proto::is_run_id)
}

/// The commits of `commits` that `repo` has as commit objects, in their order.
pub(crate) fn have(repo: &Path, commits: &[String]) -> Result<Vec<String>, String> {
    let input: String = commits.iter().map(|commit| format!("{commit}^{{commit}}\n")).collect();
    let output = checked(child::run_with_input(
        git(repo).args(["cat-file", "--batch-check"]),
        input.as_bytes(),
        QUICK,
    ))?;
    let text = String::from_utf8_lossy(&output.stdout);
    let lines: Vec<&str> = text.lines().collect();
    if lines.len() != commits.len() {
        return Err(format!("git cat-file answered {} lines for {} commits", lines.len(), commits.len()));
    }
    Ok(commits
        .iter()
        .zip(lines)
        .filter(|(_, line)| line.split(' ').nth(1) == Some("commit"))
        .map(|(commit, _)| commit.clone())
        .collect())
}

/// Starts `git index-pack --stdin --fix-thin`. The caller writes the pack to its stdin, then waits.
pub(crate) fn index_pack(repo: &Path) -> Result<Running, String> {
    child::spawn(git(repo).args(["index-pack", "--stdin", "--fix-thin"]), true, INDEX_PACK).map_err(|error| error.to_string())
}

/// Checks the result of an `index_pack` child.
pub(crate) fn finish_index_pack(running: Running) -> Result<(), String> {
    checked(running.wait(&never)).map(drop)
}

/// The refs under [`REF_PREFIX`] in `repo`.
pub(crate) fn pack_refs(repo: &Path) -> Result<Vec<String>, String> {
    let output = checked(child::run(
        git(repo).args(["for-each-ref", "--format=%(refname)", REF_PREFIX]),
        QUICK,
        &never,
    ))?;
    Ok(output.stdout_text().lines().map(str::to_owned).collect())
}

/// The commit of HEAD in `repo`. `stop` kills the call early.
pub(crate) fn head(repo: &Path, stop: Stop<'_>) -> Result<String, String> {
    checked(child::run(git(repo).args(["rev-parse", "HEAD"]), QUICK, stop)).map(|output| output.stdout_text())
}

/// Points `reference` at `commit`.
pub(crate) fn update_ref(repo: &Path, reference: &str, commit: &str) -> Result<(), String> {
    checked(child::run(git(repo).args(["update-ref", reference, commit]), QUICK, &never)).map(drop)
}

/// Deletes `reference`.
pub(crate) fn delete_ref(repo: &Path, reference: &str) -> Result<(), String> {
    checked(child::run(git(repo).args(["update-ref", "-d", reference]), QUICK, &never)).map(drop)
}

/// Checks out `commit` detached in the worktree `dir`, and discards the local changes there. `stop` kills the call
/// early.
pub(crate) fn checkout(dir: &Path, commit: &str, stop: Stop<'_>) -> Result<(), String> {
    checked(child::run(
        git(dir).args(["checkout", "-q", "-f", "--detach", commit]),
        CHECKOUT,
        stop,
    ))
    .map(drop)
}

#[cfg(test)]
mod tests;
