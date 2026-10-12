//! `bench gc`: the removal of old generations.
//!
//! A generation is kept when it is one of the `--keep` newest by the mtime of its directory, or when a `summary.json`
//! under `bench/runs` that changed in the last 24 hours names it. Every other generation is removed, and so is the
//! temporary directory of a stage that did not finish. A session under a `--session` directory outside `bench/runs`
//! protects nothing. The caller holds the session lock, so no session stages or runs during the removal.

use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime};

use avl_base::{Exit, Refusal};
use serde::Serialize;

use super::session::{BENCH_DIR, SUMMARY_FILE, runs_root};
use super::stage::{self, RECORD_FILE};

/// The generations that `bench gc` keeps by default.
pub(crate) const DEFAULT_KEEP: u32 = 2;

/// How long a session protects the generation that it names.
pub(crate) const RECENT: Duration = Duration::from_hours(24);

/// Why a generation stays.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) enum KeptBecause {
    /// One of the `--keep` newest.
    Newest,
    /// A session of the last 24 hours names it.
    RecentSession,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Kept {
    pub(crate) digest: String,
    pub(crate) because: KeptBecause,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Removed {
    /// The digest, or the name of an unfinished stage.
    pub(crate) name: String,
    pub(crate) path: String,
    pub(crate) bytes: u64,
}

/// What one `bench gc` did.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct GcReport {
    pub(crate) removed: Vec<Removed>,
    pub(crate) kept: Vec<Kept>,
}

impl GcReport {
    /// One line per removed generation with its size, then the count of the kept ones.
    pub(crate) fn text(&self) -> String {
        let mut lines: Vec<String> = self
            .removed
            .iter()
            .map(|removed| format!("removed {} ({} MiB): {}", removed.name, removed.bytes / (1024 * 1024), removed.path))
            .collect();
        let freed: u64 = self.removed.iter().map(|removed| removed.bytes).sum();
        lines.push(format!(
            "{} removed, {} MiB freed, {} kept",
            self.removed.len(),
            freed / (1024 * 1024),
            self.kept.len()
        ));
        lines.join("\n")
    }
}

fn failed(message: impl Into<String>) -> Refusal {
    Refusal::new("bench_gc_failed", Exit::FAILURE, message)
}

/// Removes the generations under `runtime_root` that neither `keep` nor a recent session protects. `now` is the
/// time the 24 hours count back from.
pub(crate) fn collect(runtime_root: &Path, keep: u32, now: SystemTime) -> Result<GcReport, Refusal> {
    let bench = runtime_root.join(BENCH_DIR);
    let generations_dir = bench.join("generations");
    let mut report = GcReport::default();
    let entries = match fs::read_dir(&generations_dir) {
        Ok(entries) => entries,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(report),
        Err(error) => return Err(failed(format!("cannot list {}: {error}", generations_dir.display()))),
    };
    let mut generations: Vec<(String, PathBuf, SystemTime)> = Vec::new();
    for entry in entries {
        let entry = entry.map_err(|error| failed(format!("cannot list {}: {error}", generations_dir.display())))?;
        let name = entry.file_name().to_string_lossy().into_owned();
        let path = entry.path();
        if name.starts_with(".stage-") {
            report.removed.push(remove(&name, &path)?);
        } else if path.join(RECORD_FILE).is_file() {
            let modified = fs::metadata(&path)
                .and_then(|metadata| metadata.modified())
                .map_err(|error| failed(format!("cannot stat {}: {error}", path.display())))?;
            generations.push((name, path, modified));
        }
    }
    generations.sort_by(|left, right| right.2.cmp(&left.2).then_with(|| left.0.cmp(&right.0)));
    let protected = recent_digests(&runs_root(runtime_root), now);
    for (index, (name, path, _)) in generations.into_iter().enumerate() {
        if index < usize::try_from(keep).unwrap_or(usize::MAX) {
            report.kept.push(Kept {
                digest: name,
                because: KeptBecause::Newest,
            });
        } else if protected.contains(&name) {
            report.kept.push(Kept {
                digest: name,
                because: KeptBecause::RecentSession,
            });
        } else {
            report.removed.push(remove(&name, &path)?);
        }
    }
    Ok(report)
}

/// The digests that a `summary.json` of the last [`RECENT`] names, one directory below `runs`. A summary that cannot be
/// read protects nothing.
fn recent_digests(runs: &Path, now: SystemTime) -> Vec<String> {
    #[derive(serde::Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct Named {
        dist_digest: String,
    }
    let Ok(sessions) = fs::read_dir(runs) else {
        return Vec::new();
    };
    sessions
        .filter_map(Result::ok)
        .map(|session| session.path().join(SUMMARY_FILE))
        .filter(|summary| {
            fs::metadata(summary)
                .and_then(|metadata| metadata.modified())
                .is_ok_and(|modified| now.duration_since(modified).unwrap_or(Duration::ZERO) <= RECENT)
        })
        .filter_map(|summary| fs::read_to_string(summary).ok())
        .filter_map(|text| serde_json::from_str::<Named>(&text).ok())
        .map(|named| named.dist_digest)
        .collect()
}

fn remove(name: &str, path: &Path) -> Result<Removed, Refusal> {
    let bytes = tree_bytes(path);
    stage::make_writable_and_remove(path).map_err(|error| failed(format!("cannot remove {}: {error}", path.display())))?;
    Ok(Removed {
        name: name.to_owned(),
        path: path.display().to_string(),
        bytes,
    })
}

/// The bytes of the files of a tree. A link counts nothing, and an entry that cannot be read counts nothing.
fn tree_bytes(path: &Path) -> u64 {
    let Ok(metadata) = fs::symlink_metadata(path) else {
        return 0;
    };
    if metadata.is_dir() {
        fs::read_dir(path).map_or(0, |entries| {
            entries.filter_map(Result::ok).map(|entry| tree_bytes(&entry.path())).sum()
        })
    } else if metadata.is_file() {
        metadata.len()
    } else {
        0
    }
}

#[cfg(test)]
mod tests;
