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

/// The deadline of a scan of every file of a slot: `git add --all` into an index copy.
pub(crate) const SCAN: Duration = Duration::from_secs(30 * 60);

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

/// Why the agent cannot use a path as a repository.
#[derive(Debug, PartialEq, Eq)]
pub(crate) enum NotRepo {
    /// The path is no directory, or git says that it is no repository. The text says which.
    Missing(String),
    /// The check itself failed, such as a git that cannot start on a host short of processes or descriptors.
    Unchecked(String),
}

/// Checks that `repo` is a git repository. A checkout has `.git`, and a bare repository has `HEAD` and `objects`. A
/// stat starts no child, so a loaded host answers too. Any other layout asks `git rev-parse --git-dir`.
pub(crate) fn check_repo(repo: &Path) -> Result<(), NotRepo> {
    if !repo.is_dir() {
        return Err(NotRepo::Missing("it is no directory".to_owned()));
    }
    if repo.join(".git").exists() || (repo.join("HEAD").is_file() && repo.join("objects").is_dir()) {
        return Ok(());
    }
    match child::run(git(repo).args(["rev-parse", "--git-dir"]), QUICK, &never) {
        Ok(output) if output.status.success() => Ok(()),
        Ok(output) => Err(NotRepo::Missing(format!(
            "`git rev-parse --git-dir` says: {} ({})",
            output.diagnostic(),
            output.status
        ))),
        Err(error) => Err(NotRepo::Unchecked(error.to_string())),
    }
}

/// Whether `text` is a full commit id: 40 or 64 lower-case hex digits.
pub(crate) fn is_commit_id(text: &str) -> bool {
    matches!(text.len(), 40 | 64) && text.bytes().all(|byte| matches!(byte, b'0'..=b'9' | b'a'..=b'f'))
}

/// Whether `reference` is a ref that a pack may make: a run ref, or a base ref `refs/yonder/base/<name>`. A name of a
/// base ref has the rules of a run id.
pub(crate) fn is_pack_ref(reference: &str) -> bool {
    is_run_ref(reference)
        || reference
            .strip_prefix(yonder_link::proto::BASE_REFS)
            .is_some_and(yonder_link::proto::is_run_id)
}

/// Whether `reference` is the ref of a run: `refs/yonder/<run id>`. The run deletes it on every way out.
pub(crate) fn is_run_ref(reference: &str) -> bool {
    reference.strip_prefix(REF_PREFIX).is_some_and(yonder_link::proto::is_run_id)
}

/// The commits of `commits` that `repo` has as commit objects, in their order. An entry can also be a ref.
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

/// Whether `revision`, such as a commit id or a ref, names a commit in `repo`.
pub(crate) fn has_commit(repo: &Path, revision: &str) -> Result<bool, String> {
    have(repo, &[revision.to_owned()]).map(|found| !found.is_empty())
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

/// Points `reference` at `commit`.
pub(crate) fn update_ref(repo: &Path, reference: &str, commit: &str) -> Result<(), String> {
    checked(child::run(git(repo).args(["update-ref", reference, commit]), QUICK, &never)).map(drop)
}

/// Deletes `reference`.
pub(crate) fn delete_ref(repo: &Path, reference: &str) -> Result<(), String> {
    checked(child::run(git(repo).args(["update-ref", "-d", reference]), QUICK, &never)).map(drop)
}

/// The deadline of `git maintenance run --auto`, which can repack a large repository.
pub(crate) const MAINTENANCE: Duration = Duration::from_secs(30 * 60);

/// Runs the maintenance tasks of `repo` that its configuration asks for, such as a repack when the packs of the
/// runs piled up. git runs them after a fetch, but not after an `index-pack`. The tasks run in the foreground, so the
/// deadline holds for them, and no detached git outlives the call.
pub(crate) fn maintenance(repo: &Path) -> Result<(), String> {
    let mut command = git(repo);
    command.args([
        "-c",
        "maintenance.autoDetach=false",
        "-c",
        "gc.autoDetach=false",
        "maintenance",
        "run",
        "--auto",
    ]);
    checked(child::run(&mut command, MAINTENANCE, &never)).map(drop)
}

/// The object ids of `paths` in the tree of `commit`, in the order of `paths`: `None` for a path that the tree lacks.
/// A directory answers its tree id, which covers its whole content.
pub(crate) fn object_ids(dir: &Path, commit: &str, paths: &[String]) -> Result<Vec<Option<String>>, String> {
    if paths.is_empty() {
        return Ok(Vec::new());
    }
    let output = checked(child::run(
        git(dir)
            .args(["--literal-pathspecs", "ls-tree", "-z", "--full-tree", commit, "--"])
            .args(paths),
        QUICK,
        &never,
    ))?;
    let mut found = std::collections::BTreeMap::new();
    for entry in output.stdout.split(|&byte| byte == 0).filter(|entry| !entry.is_empty()) {
        let entry = String::from_utf8_lossy(entry);
        let Some((head, path)) = entry.split_once('\t') else {
            return Err(format!("git ls-tree answered {entry:?}"));
        };
        let id = head.rsplit(' ').next().unwrap_or_default().to_owned();
        found.insert(path.to_owned(), id);
    }
    Ok(paths.iter().map(|path| found.get(path.trim_end_matches('/')).cloned()).collect())
}

/// The bytes of the blob `id` in the repository of `dir`.
pub(crate) fn blob(dir: &Path, id: &str) -> Result<Vec<u8>, String> {
    checked(child::run(git(dir).args(["cat-file", "blob", id]), QUICK, &never)).map(|output| output.stdout)
}

/// The tree of the files of the worktree `dir` as `git add --all` sees them: tracked and untracked files that git does
/// not ignore. The scan goes into `index`, a copy of the index of the worktree, so the index of the worktree stays as it
/// is. The copy is removed on every way out. The scan runs without fsmonitor, which would start a daemon in the slot.
pub(crate) fn worktree_tree(dir: &Path, index: &Path, stop: Stop<'_>) -> Result<String, String> {
    let own = checked(child::run(
        git(dir).args(["rev-parse", "--path-format=absolute", "--git-path", "index"]),
        QUICK,
        stop,
    ))?
    .stdout_text();
    let _removed = RemoveOnDrop(index.to_path_buf());
    std::fs::copy(&own, index).map_err(|error| format!("cannot copy the index {own} to {}: {error}", index.display()))?;
    let scan = |args: &[&str]| {
        let mut command = git(dir);
        command.args(["-c", "core.fsmonitor=false"]).args(args).env("GIT_INDEX_FILE", index);
        checked(child::run(&mut command, SCAN, stop))
    };
    scan(&["add", "--all"])?;
    Ok(scan(&["write-tree"])?.stdout_text())
}

/// The patch from the tree `before` to the tree `after`, for `git apply`: binary, with full ids, without renames.
pub(crate) fn tree_patch(dir: &Path, before: &str, after: &str) -> Result<Vec<u8>, String> {
    checked(child::run(
        git(dir).args(["diff-tree", "-r", "-p", "--binary", "--full-index", "--no-renames", before, after]),
        SCAN,
        &never,
    ))
    .map(|output| output.stdout)
}

/// A temporary file that goes with its guard, together with the lock file that git makes beside it.
struct RemoveOnDrop(std::path::PathBuf);

impl Drop for RemoveOnDrop {
    fn drop(&mut self) {
        let _ = std::fs::remove_file(&self.0);
        let mut lock = self.0.clone().into_os_string();
        lock.push(".lock");
        let _ = std::fs::remove_file(lock);
    }
}

/// How [`checkout`] brought a worktree to its commit.
#[derive(Debug, PartialEq, Eq)]
pub(crate) enum Checkout {
    /// The detached HEAD was the commit already: no file changed.
    Kept,
    /// `reset --keep` moved the detached HEAD: it wrote only the files that differ between the two commits.
    Switched,
    /// `checkout -f --detach` wrote the commit and discarded the local changes, for the reason in the text.
    Forced(String),
}

/// Checks out `commit` detached in the worktree `dir`, which must have no local changes: the give-back of the last run
/// discarded them (Branch Deck, `spec/cli/lease.spec.md`, "Host slots"). A detached HEAD moves with `reset --keep`, a two-way merge that reads only the files of the diff. A
/// HEAD on a branch, which `reset` would move, or a refused merge, such as a local change in the diff, falls back to
/// `checkout -f --detach`, which stats every file of the worktree and discards its local changes. `stop` kills the call
/// early.
pub(crate) fn checkout(dir: &Path, commit: &str, stop: Stop<'_>) -> Result<Checkout, String> {
    let head = checked(child::run(
        git(dir).args(["rev-parse", "HEAD", "--symbolic-full-name", "HEAD"]),
        QUICK,
        stop,
    ));
    let why = match head.as_ref().map(Output::stdout_text) {
        Ok(text) => match text.lines().collect::<Vec<_>>()[..] {
            [id, "HEAD"] if id == commit => return Ok(Checkout::Kept),
            [_, "HEAD"] => match checked(child::run(git(dir).args(["reset", "-q", "--keep", commit]), CHECKOUT, stop)) {
                Ok(_) => return Ok(Checkout::Switched),
                Err(error) => format!("`git reset --keep` refused: {error}"),
            },
            [_, branch] => format!("the HEAD is on the branch {branch}"),
            _ => format!("`git rev-parse` answered {text:?}"),
        },
        Err(error) => format!("the HEAD is unknown: {error}"),
    };
    if stop() {
        return Err(why);
    }
    checked(child::run(
        git(dir).args(["checkout", "-q", "-f", "--detach", commit]),
        CHECKOUT,
        stop,
    ))
    .map(|_| Checkout::Forced(why))
}

#[cfg(test)]
mod tests;
