//! The leases that holders kept before the host slots (`slots`), and their return.
//!
//! Until 2026-10-27 a run took the warm lease of its holder, one local checkout, kept it in `<leases>/<holder>.lease`
//! and held it for hours, so the slot count had no bound. Now a run borrows a host slot for its run only, and the keeper
//! of `serve` returns each such lease once. A holder whose lock an old supervisor still holds (one that runs across the
//! upgrade) is skipped until a later tick, and a file without `repo=` names no repository to return it in. The return
//! passes `--confirm-preserved`, as the release of a holder did: the commit in the slot came from the local checkout of
//! the holder, which keeps it.

use std::fs::{self, File};
use std::io;
use std::path::{Path, PathBuf};

use crate::bd;
use crate::child::Stop;
use crate::files::{open_private, try_flock};

/// The content of a lease file: `lease_id=`, `slot=`, `repo=`; the `used=` and `prepared.<key>=` lines are no longer
/// read.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct LeaseFile {
    pub(crate) lease_id: String,
    pub(crate) slot: PathBuf,
    /// The main repository of the lease. `None` in a file of `remote.sh`.
    pub(crate) repo: Option<PathBuf>,
}

impl LeaseFile {
    pub(crate) fn parse(text: &str) -> Option<Self> {
        let field = |key: &str| {
            text.lines()
                .find_map(|line| line.strip_prefix(key).and_then(|rest| rest.strip_prefix('=')))
                .filter(|value| !value.is_empty())
        };
        Some(Self {
            lease_id: field("lease_id")?.to_owned(),
            slot: PathBuf::from(field("slot")?),
            repo: field("repo").map(PathBuf::from),
        })
    }
}

/// The flock of a holder, held while its lease goes back. Dropping it unlocks.
struct HolderLock {
    _file: File,
}

/// Takes the lock of `holder` in `dir` when no other process holds it.
fn try_lock(dir: &Path, holder: &str) -> io::Result<Option<HolderLock>> {
    let file = open_private(&dir.join(format!("{holder}.lock")))?;
    Ok(try_flock(&file)?.then_some(HolderLock { _file: file }))
}

/// The holders of `dir` with their lease files, by name.
fn files(dir: &Path) -> Vec<(String, Option<LeaseFile>)> {
    let Ok(entries) = fs::read_dir(dir) else {
        return Vec::new();
    };
    let mut files: Vec<(String, Option<LeaseFile>)> = entries
        .filter_map(Result::ok)
        .filter_map(|entry| {
            let holder = entry.file_name().to_str()?.strip_suffix(".lease")?.to_owned();
            let file = fs::read_to_string(entry.path()).ok().and_then(|text| LeaseFile::parse(&text));
            Some((holder, file))
        })
        .collect();
    files.sort_by(|a, b| a.0.cmp(&b.0));
    files
}

/// Whether a process holds the lock of a holder in `dir`: an old supervisor that has its lease.
fn held_lock(dir: &Path) -> bool {
    let Ok(entries) = fs::read_dir(dir) else {
        return false;
    };
    entries.filter_map(Result::ok).any(|entry| {
        entry
            .file_name()
            .to_str()
            .and_then(|name| name.strip_suffix(".lock"))
            .is_some_and(|holder| matches!(try_lock(dir, holder), Ok(None)))
    })
}

/// The repositories that the lease files of `dir` name.
pub(crate) fn repositories(dir: &Path) -> Vec<PathBuf> {
    let mut repos: Vec<PathBuf> = files(dir).into_iter().filter_map(|(_, file)| file?.repo).collect();
    repos.sort();
    repos.dedup();
    repos
}

/// Returns each lease of `repo` that a holder kept in `dir`, and deletes its file. A holder whose lock another process
/// holds keeps its file until a later call, and so does a failed return, whose line goes to `say`. A lease that bd no
/// longer knows only loses its file. Answers whether no lease of `repo` is left.
///
/// An old supervisor that runs across the upgrade writes the lease file of its holder only at its end, so a held lock
/// of any holder counts as a lease that is left too: its repository is unknown until the file comes.
pub(crate) fn retire(dir: &Path, repo: &Path, stop: Stop<'_>, say: &mut dyn FnMut(&str)) -> bool {
    let mut done = !held_lock(dir);
    for (holder, file) in files(dir) {
        let path = dir.join(format!("{holder}.lease"));
        let Some(file) = file else {
            let _ = fs::remove_file(&path);
            continue;
        };
        if file.repo.as_deref() != Some(repo) {
            continue;
        }
        let Ok(Some(_lock)) = try_lock(dir, &holder) else {
            done = false;
            continue;
        };
        if stop() {
            return false;
        }
        if !bd::lease_alive(repo, &file.lease_id, stop) {
            let _ = fs::remove_file(&path);
            continue;
        }
        match bd::lease_return(&file.slot, &file.lease_id, true, stop) {
            Ok(()) => {
                let _ = fs::remove_file(&path);
                say(&format!(
                    "returned the lease {} of {holder} ({}): runs borrow the host slots now",
                    file.lease_id,
                    file.slot.display()
                ));
            }
            Err(error) => {
                done = false;
                say(&format!(
                    "cannot return the lease {} of {holder} ({}): {error}",
                    file.lease_id,
                    file.slot.display()
                ));
            }
        }
    }
    done
}

/// The current epoch second.
pub(crate) fn now_seconds() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_or(0, |since| since.as_secs())
}

#[cfg(test)]
mod tests;
