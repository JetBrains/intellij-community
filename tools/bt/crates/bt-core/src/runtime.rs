//! The runtime seam. Every side effect of this crate goes through it, which is what lets the tests drive the whole
//! wrapper (resolution, a bazel run, result collection, the digest) without a filesystem and without bazel.
//!
//! [`Runtime::platform`] in particular stays injectable rather than read from the host: [`crate::bazel_command`]
//! and the directory-selector rules branch on it, and the Windows dialect is tested from macOS.

use std::ffi::OsString;
use std::io;
use std::path::{Path, PathBuf};
use std::thread;
use std::time::Duration;

/// The platform whose conventions a path or a command line follows.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Platform {
    Darwin,
    Linux,
    Windows,
}

impl Platform {
    /// The host this binary was compiled for. Anything that is neither Windows nor macOS follows the Linux rules,
    /// which are the POSIX ones.
    pub const fn current() -> Self {
        if cfg!(windows) {
            Self::Windows
        } else if cfg!(target_os = "macos") {
            Self::Darwin
        } else {
            Self::Linux
        }
    }
}

/// One name from a directory listing, and whether it is a directory. Deliberately not `std::fs::DirEntry`: this
/// seam must be satisfiable by a map in a test.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DirEntry {
    pub name: String,
    pub is_dir: bool,
}

/// A finished child process.
///
/// `output` is stdout and stderr merged, tail-truncated by the implementation. It is captured rather than streamed
/// because bazel's own chatter is what this wrapper exists to suppress; it is read only when something went wrong.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct SpawnResult {
    pub exit_code: i32,
    pub output: String,
}

/// The content of a checkout as git names it: the commit of HEAD, and the tree of the files with the local changes.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct CheckoutTree {
    pub head: String,
    /// `HEAD^{tree}` for a clean checkout. With local changes, the tree that `git add -A` and `git write-tree` would
    /// give, untracked files included and ignored files left out.
    pub tree: String,
    /// The paths with local changes, repo-relative with `/`, as `git status` names them. Empty for a clean checkout.
    pub changed: Vec<String>,
}

/// The exit code of a child that [`Runtime::spawn_watched`] stopped, as the `timeout` command answers it.
pub const STOPPED_IDLE: i32 = 124;

/// Called with the elapsed milliseconds every 30 s while a child runs, so a long bazel run does not look hung.
pub type Heartbeat<'a> = &'a (dyn Fn(u64) + Sync);

/// The injection point. `Sync`, because the tree scan and the suite reads call it from worker threads.
pub trait Runtime: Sync {
    /// The checkout root. Every path this crate builds is relative to it, never to the process working directory:
    /// under the runfiles tree of a `bazel run` launcher the two differ, and a target pattern is repo-relative
    /// regardless.
    fn repo_root(&self) -> &Path;

    fn platform(&self) -> Platform;

    fn read_text_file(&self, path: &Path) -> io::Result<String>;

    /// Replaces a file in one step, so a concurrent reader sees the old text or the new, never a part. Creates the
    /// directory of the file when it is missing.
    fn write_text_file(&self, path: &Path, text: &str) -> io::Result<()>;

    /// Streams a file that may not exist and may end mid-line.
    ///
    /// No error: bazel never wrote the BEP file when it died during startup or option parsing, and a truncated
    /// final line is expected when it was killed mid-write. Both are diagnosable outcomes the BEP reader handles,
    /// so an absent file yields no lines.
    fn read_lines(&self, path: &Path) -> Box<dyn Iterator<Item = String> + '_>;

    fn read_dir(&self, path: &Path) -> io::Result<Vec<DirEntry>>;

    /// Every file under a directory that the checkout tracks, and every untracked file under it that the checkout
    /// does not ignore, as absolute paths in name order. A tracked file that is gone from the disk is not listed.
    /// An error is a directory the checkout cannot list.
    ///
    /// Separate from [`Runtime::read_dir`] because only the version control knows what a checkout ignores: a walk
    /// would answer every build output and every `node_modules` file under a module as a changed path.
    fn list_files(&self, dir: &Path) -> io::Result<Vec<PathBuf>>;

    /// The text of a repo-relative file at a revision of the checkout, or `None` when the revision holds no such file.
    /// An error is a revision that the checkout does not hold, or a checkout that the version control cannot read.
    fn revision_text(&self, revision: &str, path: &str) -> io::Result<Option<String>>;

    /// The commit where `HEAD` meets its upstream: the upstream of the current branch, or the default branch of
    /// `origin` for a detached `HEAD`. The commits after it are the changes that are not pushed yet. An error is a
    /// checkout without an upstream.
    fn upstream_base(&self) -> io::Result<String>;

    /// Answers for a directory as well as a file: every test source root this crate probes is a directory.
    fn exists(&self, path: &Path) -> bool;

    /// The content of the checkout now, or `None` when the checkout root is not a linked work tree of git. Only a
    /// linked work tree, such as a lease of Branch Deck, is landed by `bd lease pick`, so only it needs the tree.
    ///
    /// It changes neither the index nor the work tree of the checkout. It writes the blobs and the trees of the local
    /// changes into the object database, so a later reader can compare two trees.
    fn checkout_tree(&self) -> io::Result<Option<CheckoutTree>>;

    /// The tree of a commit of the checkout.
    fn commit_tree(&self, commit: &str) -> io::Result<String>;

    /// The path of a file in the git directory of the checkout, as `git rev-parse --git-path` gives it. A linked work
    /// tree gives its own directory under `.git/worktrees/`.
    fn git_path(&self, name: &str) -> io::Result<PathBuf>;

    /// Runs a child to completion and never fails: a command that could not start is a [`SpawnResult`] with exit
    /// code 127, a run outcome the classifier can reason about rather than an error to re-classify.
    fn spawn(&self, command: &[OsString], heartbeat: Option<Heartbeat<'_>>) -> SpawnResult;

    /// Runs a child to completion, passes each line of its stderr to [`Runtime::write_error`] as it comes, and answers
    /// its whole stdout as the [`SpawnResult`] output. Never fails, as [`Runtime::spawn`].
    ///
    /// The stdin of the child is a pipe that the runtime keeps open and never writes, until the child exits. So the
    /// child sees the end of its stdin only when this process is gone. `bt --host` relies on it: ssh passes the end
    /// to the host, and the host stops the run.
    fn spawn_streaming(&self, command: &[OsString]) -> SpawnResult;

    /// Runs a child to completion with `input` on its stdin, and answers its stdout and stderr merged. Never fails, as
    /// [`Runtime::spawn`]. The input stays off the command line, so a secret in it shows in no process list.
    fn spawn_with_input(&self, command: &[OsString], input: &str) -> SpawnResult;

    /// Runs a child as [`Runtime::spawn`] does, and stops it with its process group when it writes nothing for
    /// `idle`. A stopped child answers [`STOPPED_IDLE`]. Never fails.
    ///
    /// For a child that can stall without an end, such as an ssh or a git push that waits for a dead link or a key
    /// agent: its options bound the link, and this bounds the rest.
    fn spawn_watched(&self, command: &[OsString], idle: Duration) -> SpawnResult;

    /// A fresh path for a temporary file. The caller owns it and removes it with [`Runtime::remove`], from a guard
    /// that runs even when the run blows up: a leaked BEP file is tens of MB for a lane run.
    fn temp_file(&self, prefix: &str) -> PathBuf;

    fn remove(&self, path: &Path) -> io::Result<()>;

    /// Copies one file, and creates the directories of `to` first.
    fn copy_file(&self, from: &Path, to: &Path) -> io::Result<()>;

    /// Creates a directory and its parents. A directory that is there already is no error.
    fn create_dir_all(&self, path: &Path) -> io::Result<()>;

    /// Removes a directory with everything in it. A directory that is not there is no error.
    fn remove_dir_all(&self, path: &Path) -> io::Result<()>;

    /// The home directory of the user, when the platform names one.
    fn home_dir(&self) -> Option<PathBuf>;

    /// Asks the person at the terminal a yes or no question, on the stderr. Answers `None` when the stdin is not a
    /// terminal, so a script or an agent never answers it, and `Some(true)` only for a yes.
    fn confirm(&self, question: &str) -> Option<bool>;

    /// Wall-clock milliseconds. Only differences are used, so a fake can start anywhere.
    fn now_ms(&self) -> u64;

    /// The digest's destination. [`Runtime::write_error`] takes progress and diagnostics; the split is the whole
    /// reason `--json` stdout stays machine-clean.
    fn write(&self, text: &str);

    fn write_error(&self, text: &str);
}

/// The fan-out bound of the tree scan and the document reads.
///
/// The Air tree holds ~1200 unpruned directories and ~180 .iml files; walking them one at a time is latency-bound,
/// while an unbounded fan-out would want more descriptors at once than the default macOS `ulimit -n` of 256.
const IO_CONCURRENCY: usize = 32;

/// Maps `items` with bounded parallelism, answering results in input order.
///
/// Order is restored by index rather than by completion, and that is load-bearing: the scan's listings are zipped
/// back against the frontier that produced them, and a resolution that reads several files must not depend on
/// which read finished first.
pub fn par_map<T: Sync, R: Send>(items: &[T], transform: impl Fn(&T) -> R + Sync) -> Vec<R> {
    let workers = IO_CONCURRENCY.min(items.len());
    if workers <= 1 {
        return items.iter().map(transform).collect();
    }
    let cursor = std::sync::atomic::AtomicUsize::new(0);
    let mut results: Vec<(usize, R)> = thread::scope(|scope| {
        let handles: Vec<_> = (0..workers)
            .map(|_| {
                scope.spawn(|| {
                    let mut done = Vec::new();
                    loop {
                        let index = cursor.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
                        let Some(item) = items.get(index) else {
                            return done;
                        };
                        done.push((index, transform(item)));
                    }
                })
            })
            .collect();
        handles
            .into_iter()
            .flat_map(|handle| handle.join().unwrap_or_else(|panic| std::panic::resume_unwind(panic)))
            .collect()
    });
    results.sort_unstable_by_key(|(index, _)| *index);
    results.into_iter().map(|(_, result)| result).collect()
}

/// The file of a repo-relative path. `/` is a separator on every platform, Windows included.
pub fn repo_file(runtime: &dyn Runtime, relative: &str) -> PathBuf {
    runtime.repo_root().join(relative)
}

#[cfg(test)]
mod tests {
    use super::par_map;

    #[test]
    fn par_map_answers_in_input_order() {
        let items: Vec<usize> = (0..500).collect();
        let doubled = par_map(&items, |item| item * 2);
        assert_eq!(doubled, items.iter().map(|item| item * 2).collect::<Vec<_>>());
        assert!(par_map(&[] as &[usize], |item| *item).is_empty());
    }
}
