//! The runtime seam. Every side effect of this crate goes through it, which is what lets the tests drive the whole
//! wrapper (resolution, a bazel run, result collection, the digest) without a filesystem and without bazel.
//!
//! [`Runtime::platform`] in particular stays injectable rather than read from the host: [`crate::bazel_command`]
//! and the directory-selector rules branch on it, and the Windows dialect is tested from macOS.

use std::ffi::OsString;
use std::io;
use std::path::{Path, PathBuf};
use std::thread;

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

    /// Answers for a directory as well as a file: every test source root this crate probes is a directory.
    fn exists(&self, path: &Path) -> bool;

    /// Runs a child to completion and never fails: a command that could not start is a [`SpawnResult`] with exit
    /// code 127, a run outcome the classifier can reason about rather than an error to re-classify.
    fn spawn(&self, command: &[OsString], heartbeat: Option<Heartbeat<'_>>) -> SpawnResult;

    /// A fresh path for a temporary file. The caller owns it and removes it with [`Runtime::remove`], from a guard
    /// that runs even when the run blows up: a leaked BEP file is tens of MB for a lane run.
    fn temp_file(&self, prefix: &str) -> PathBuf;

    fn remove(&self, path: &Path) -> io::Result<()>;

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
