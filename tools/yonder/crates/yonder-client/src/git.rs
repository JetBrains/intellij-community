//! The local git calls of a run: the clean check, the commit of the working tree, the commits that the host may have,
//! the base, and the thin pack.

use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::Duration;

use crate::process::{Call, Limit, Processes, Ran, Stop};
use crate::{Refusal, code, refused};

/// The deadline of a short git call. `git status` of a large checkout reads every file, so it gets minutes.
const GIT_DEADLINE: Duration = Duration::from_secs(300);

/// The quiet limit of `git pack-objects --progress`. It writes a progress line at least every second while it works, so
/// a minute without one is a stuck child. A large pack takes as long as it needs.
pub(crate) const PACK_QUIET: Duration = Duration::from_secs(60);

/// The largest first-parent distance from HEAD of the first `Have` of a run. git walks 17 commits for it, and a host
/// that ran a recent commit of the checkout has one of them.
pub(crate) const NEAR: usize = 16;

/// The largest first-parent distance from HEAD that the client asks the host about, when the host has none of the near
/// candidates. The distances are the powers of two up to it, so a host that is some thousand commits behind still has
/// one of them.
pub(crate) const FARTHEST: usize = 65_536;

/// How many changed paths a refusal shows.
const SHOWN_CHANGES: usize = 5;

/// The author and the committer of a commit of the working tree.
const SNAPSHOT_NAME: &str = "yonder";
const SNAPSHOT_EMAIL: &str = "yonder@yonder.invalid";

/// The count of the index copies of this process, for their names.
static INDEXES: AtomicU64 = AtomicU64::new(0);

/// The commit that a run ships.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Snapshot {
    pub(crate) commit: String,
    pub(crate) head: String,
    /// The lines of `git status --porcelain` of the changes beyond HEAD. Empty when the commit is HEAD.
    pub(crate) changed: Vec<String>,
}

/// Refuses a checkout with local changes: tracked or untracked files that differ from HEAD. `git status` of a large
/// checkout reads every file, so a run asks the host meanwhile.
pub(crate) fn check_clean(processes: &dyn Processes, checkout: &Path, stop: Stop<'_>) -> Result<(), Refusal> {
    let status = git(processes, checkout, &["status", "--porcelain"], stop);
    if !status.ok() {
        return Err(git_refusal("git status", &status));
    }
    let text = status.stdout_text();
    let changed: Vec<&str> = text.lines().filter(|line| !line.trim().is_empty()).collect();
    if !changed.is_empty() {
        let more = match changed.len() {
            0..=SHOWN_CHANGES => String::new(),
            count => format!("\n  (+{} more)", count - SHOWN_CHANGES),
        };
        return Err(refused(
            code::LOCAL_CHANGES,
            format!(
                "a remote run takes the committed HEAD only, and this checkout has local changes. Commit or stash them first:\n  {}{more}",
                changed[..changed.len().min(SHOWN_CHANGES)].join("\n  ")
            ),
        ));
    }
    Ok(())
}

/// The commit of HEAD.
pub(crate) fn head_commit(processes: &dyn Processes, checkout: &Path, stop: Stop<'_>) -> Result<String, Refusal> {
    #[expect(clippy::literal_string_with_formatting_args, reason = "`HEAD^{commit}` is the peel syntax of git")]
    let head = git(processes, checkout, &["rev-parse", "--verify", "HEAD^{commit}"], stop);
    let commit = head.stdout_text().trim().to_owned();
    if !head.ok() || commit.is_empty() {
        return Err(git_refusal("git rev-parse HEAD", &head));
    }
    Ok(commit)
}

/// The commit of the working tree of the checkout: HEAD when the checkout has no local changes, else a commit on HEAD
/// whose tree is the one that `git add --all` would stage: the tracked and untracked files that git does not ignore.
///
/// The tree comes from a copy of the index in the temporary directory (`GIT_INDEX_FILE`), so the index, the files and
/// the refs of the checkout stay as they are. `git add --all` takes only the paths that `git status` named, so a large
/// checkout with a few changes costs one status and not a second scan of every file. Only the objects of the changed
/// files are new, as loose objects that no ref names; `git gc` prunes them. The author and the committer are fixed, and
/// their date is the one of the committer of HEAD, so the same tree on the same HEAD is the same commit, and a second
/// run of it sends no pack.
pub(crate) fn working_tree_commit(processes: &dyn Processes, checkout: &Path, stop: Stop<'_>) -> Result<Snapshot, Refusal> {
    let status = git(processes, checkout, &["status", "--porcelain", "-z"], stop);
    if !status.ok() {
        return Err(git_refusal("git status", &status));
    }
    let (changed, paths) = status_entries(&status.stdout);
    let head = head_commit(processes, checkout, stop)?;
    if changed.is_empty() {
        return Ok(Snapshot {
            commit: head.clone(),
            head,
            changed,
        });
    }
    let index = IndexCopy::new();
    let own = git(
        processes,
        checkout,
        &["rev-parse", "--path-format=absolute", "--git-path", "index"],
        stop,
    );
    if !own.ok() {
        return Err(git_refusal("git rev-parse --git-path index", &own));
    }
    let own = PathBuf::from(own.stdout_text().trim());
    let index_text = index.0.to_string_lossy().into_owned();
    let with_index = |args: &[&str], input: Vec<u8>| {
        processes.run(
            &Call::new("git", args, GIT_DEADLINE)
                .in_dir(checkout.to_path_buf())
                .with_env("GIT_INDEX_FILE", &index_text)
                .with_input(input),
            stop,
        )
    };
    match fs::copy(&own, &index.0) {
        Ok(_) => {}
        // A repository without a commit staged yet has no index.
        Err(error) if error.kind() == io::ErrorKind::NotFound => {
            let read = with_index(&["read-tree", &head], Vec::new());
            if !read.ok() {
                return Err(git_refusal("git read-tree", &read));
            }
        }
        Err(error) => {
            return Err(refused(
                code::GIT_FAILED,
                format!("cannot copy the index {} to {}: {error}", own.display(), index.0.display()),
            ));
        }
    }
    let mut pathspec = Vec::new();
    for path in &paths {
        pathspec.extend_from_slice(path.as_bytes());
        pathspec.push(0);
    }
    let added = with_index(
        &[
            "--literal-pathspecs",
            "add",
            "--all",
            "--pathspec-from-file=-",
            "--pathspec-file-nul",
        ],
        pathspec,
    );
    if !added.ok() {
        return Err(git_refusal("git add --all", &added));
    }
    let tree = with_index(&["write-tree"], Vec::new());
    let tree_id = tree.stdout_text().trim().to_owned();
    if !tree.ok() || tree_id.is_empty() {
        return Err(git_refusal("git write-tree", &tree));
    }
    let head_tree = git(processes, checkout, &["rev-parse", "--verify", &format!("{head}^{{tree}}")], stop);
    if head_tree.ok() && head_tree.stdout_text().trim() == tree_id {
        // Only a change that git does not keep, such as a mode under core.fileMode=false.
        return Ok(Snapshot {
            commit: head.clone(),
            head,
            changed: Vec::new(),
        });
    }
    let date = committer_date(processes, checkout, &head, stop)?;
    let message = format!("yonder: the working tree on {}", head.get(..12).unwrap_or(&head));
    let committed = processes.run(
        &Call::new(
            "git",
            &["commit-tree", "--no-gpg-sign", "-p", &head, "-m", &message, &tree_id],
            GIT_DEADLINE,
        )
        .in_dir(checkout.to_path_buf())
        .with_env("GIT_AUTHOR_NAME", SNAPSHOT_NAME)
        .with_env("GIT_AUTHOR_EMAIL", SNAPSHOT_EMAIL)
        .with_env("GIT_AUTHOR_DATE", &date)
        .with_env("GIT_COMMITTER_NAME", SNAPSHOT_NAME)
        .with_env("GIT_COMMITTER_EMAIL", SNAPSHOT_EMAIL)
        .with_env("GIT_COMMITTER_DATE", &date),
        stop,
    );
    let commit = committed.stdout_text().trim().to_owned();
    if !committed.ok() || commit.is_empty() {
        return Err(git_refusal("git commit-tree", &committed));
    }
    Ok(Snapshot { commit, head, changed })
}

/// The entries of `git status --porcelain -z` as lines for a person (`XY path`), and the path of each one. The old path
/// of a staged rename or copy is not one of them: the index has the rename already, and a file at the old path is an
/// entry of its own. An untracked directory is one path.
pub(crate) fn status_entries(stdout: &[u8]) -> (Vec<String>, Vec<String>) {
    let mut lines = Vec::new();
    let mut paths = Vec::new();
    let mut fields = stdout.split(|&byte| byte == 0).filter(|field| !field.is_empty());
    while let Some(field) = fields.next() {
        let field = String::from_utf8_lossy(field);
        let (Some(code), Some(path)) = (field.get(..2), field.get(3..)) else {
            continue;
        };
        if code.contains(['R', 'C']) {
            fields.next();
        }
        lines.push(field.to_string());
        paths.push(path.to_owned());
    }
    (lines, paths)
}

/// The date of the committer of `commit`, raw: `<epoch seconds> <zone>`.
fn committer_date(processes: &dyn Processes, checkout: &Path, commit: &str, stop: Stop<'_>) -> Result<String, Refusal> {
    let object = git(processes, checkout, &["cat-file", "commit", commit], stop);
    if !object.ok() {
        return Err(git_refusal("git cat-file commit", &object));
    }
    let text = object.stdout_text();
    let date = text
        .lines()
        .take_while(|line| !line.is_empty())
        .find_map(|line| line.strip_prefix("committer "))
        .and_then(|line| {
            let mut words = line.rsplitn(3, ' ');
            let zone = words.next()?;
            let seconds = words.next()?;
            Some(format!("{seconds} {zone}"))
        });
    date.ok_or_else(|| refused(code::GIT_FAILED, format!("the commit {commit} has no committer line")))
}

/// The name of the local repository: the directory of its main checkout, such as `branch-deck` for a linked worktree
/// of `~/projects/branch-deck`. `None` when git cannot tell.
pub(crate) fn repo_name(processes: &dyn Processes, checkout: &Path, stop: Stop<'_>) -> Option<String> {
    let common = git(
        processes,
        checkout,
        &["rev-parse", "--path-format=absolute", "--git-common-dir"],
        stop,
    );
    if !common.ok() {
        return None;
    }
    let common = PathBuf::from(common.stdout_text().trim());
    let name = if common.file_name().is_some_and(|name| name == ".git") {
        common.parent()?.file_name()?
    } else {
        common.file_name()?
    };
    let name = name.to_str()?;
    let name = name.strip_suffix(".git").unwrap_or(name);
    (!name.is_empty()).then(|| name.to_owned())
}

/// A copy of the index in the temporary directory, removed with its guard, together with the lock file that git makes
/// beside it.
struct IndexCopy(PathBuf);

impl IndexCopy {
    fn new() -> Self {
        let name = format!("yonder-{}-{}.index", std::process::id(), INDEXES.fetch_add(1, Ordering::Relaxed));
        Self(std::env::temp_dir().join(name))
    }
}

impl Drop for IndexCopy {
    fn drop(&mut self) {
        let _ = fs::remove_file(&self.0);
        let mut lock = self.0.clone().into_os_string();
        lock.push(".lock");
        let _ = fs::remove_file(lock);
    }
}

/// The commits that the host may have: HEAD and its first-parent ancestors at the distances 1, 2, 4 … `farthest`, then
/// the merge bases of HEAD with `master` and `origin/master`. A ref that this checkout lacks adds nothing.
pub(crate) fn candidates(processes: &dyn Processes, checkout: &Path, commit: &str, farthest: usize, stop: Stop<'_>) -> Vec<String> {
    let mut found: Vec<String> = Vec::new();
    let mut add = |line: &str| {
        if !line.is_empty() && !found.iter().any(|known| known == line) {
            found.push(line.to_owned());
        }
    };
    let ancestors = git(
        processes,
        checkout,
        &["rev-list", "--first-parent", &format!("--max-count={}", farthest + 1), commit],
        stop,
    );
    if ancestors.ok() {
        let text = ancestors.stdout_text();
        let lines: Vec<&str> = text.lines().map(str::trim).collect();
        for distance in std::iter::once(0).chain((0..=farthest.ilog2()).map(|power| 1 << power)) {
            if let Some(line) = lines.get(distance) {
                add(line);
            }
        }
    }
    for base in ["master", "origin/master"] {
        let merged = git(processes, checkout, &["merge-base", commit, base], stop);
        if merged.ok() {
            add(merged.stdout_text().trim());
        }
    }
    found
}

/// Adds a commit to the candidates when it is a new one.
pub(crate) fn add_candidate(candidates: &mut Vec<String>, commit: Option<&str>) {
    if let Some(commit) = commit
        && !commit.is_empty()
        && !candidates.iter().any(|known| known == commit)
    {
        candidates.push(commit.to_owned());
    }
}

/// The commit of the base of the later packs: `origin/master`, or `master` when the checkout has no `origin/master`.
/// `None` when the checkout has neither.
pub(crate) fn base_commit(processes: &dyn Processes, checkout: &Path, stop: Stop<'_>) -> Option<String> {
    ["origin/master", "master"].into_iter().find_map(|base| {
        let ran = git(
            processes,
            checkout,
            &["rev-parse", "--verify", "--quiet", &format!("{base}^{{commit}}")],
            stop,
        );
        let commit = ran.stdout_text().trim().to_owned();
        (ran.ok() && !commit.is_empty()).then_some(commit)
    })
}

/// The count of the commits of `commit` that are not in `present`, or `None` when git cannot count them.
pub(crate) fn commit_count(processes: &dyn Processes, checkout: &Path, commit: &str, present: &[String], stop: Stop<'_>) -> Option<u64> {
    let mut args = vec!["rev-list".to_owned(), "--count".to_owned(), commit.to_owned()];
    args.extend(present.iter().map(|known| format!("^{known}")));
    let counted = git(processes, checkout, &args, stop);
    counted.stdout_text().trim().parse().ok().filter(|_| counted.ok())
}

/// The call of the thin pack of `commit` against the commits that the host has. Its stdout goes into a file, and
/// its progress keeps it alive.
pub(crate) fn pack_call(checkout: &Path, commit: &str, present: &[String]) -> Call {
    let mut input = format!("{commit}\n");
    for known in present {
        input.push('^');
        input.push_str(known);
        input.push('\n');
    }
    Call::new("git", &["pack-objects", "--revs", "--thin", "--stdout", "--progress"], GIT_DEADLINE)
        .with_limit(Limit::Quiet(PACK_QUIET))
        .in_dir(checkout.to_path_buf())
        .with_input(input.into_bytes())
}

pub(crate) fn git_refusal(what: &str, ran: &Ran) -> Refusal {
    refused(code::GIT_FAILED, format!("{what} failed: {}", ran.said()))
}

fn git<S: AsRef<str>>(processes: &dyn Processes, checkout: &Path, args: &[S], stop: Stop<'_>) -> Ran {
    processes.run(&Call::new("git", args, GIT_DEADLINE).in_dir(checkout.to_path_buf()), stop)
}

#[cfg(test)]
mod tests;
