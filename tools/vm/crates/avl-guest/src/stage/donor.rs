//! Linking one generation from the last.
//!
//! A hardlink is what keeps a staged generation cheap, and a *stale* hardlink is the one defect this module exists
//! to make impossible: a run whose classpath points at the previous build's bytes passes, and reports itself as
//! having tested the new ones. So a donor file is chosen by the sha256 of its own bytes, and never by its name,
//! its index or its path; a staged jar keeps all three of those while its contents change under it.
//!
//! The link is legal because both generations sit under `<runtimeRoot>/generations` on the guest's own disk.
//! Nothing writes to a staged file after its generation is renamed into place, so two generations sharing an inode
//! stay two readers of one file.

use std::collections::{BTreeMap, HashMap, HashSet};
use std::fs;
use std::path::{Path, PathBuf};
use std::time::SystemTime;

use avl_wire::stage::is_sha256_hex;

use super::generations_dir;

/// One retained generation directory and when it was last modified.
pub(crate) struct Candidate {
    pub name: String,
    modified: SystemTime,
}

/// The retained generations under `runtime_root`, newest first, leaving out `exclude`.
///
/// By the directory's own mtime (`lstat`), because the host's keep list may name only one digest and "newest"
/// is what decides the second.
pub(crate) fn generation_candidates(runtime_root: &Path, exclude: &str) -> Vec<Candidate> {
    let generations = generations_dir(runtime_root);
    let Ok(entries) = fs::read_dir(&generations) else {
        return Vec::new();
    };
    let mut candidates: Vec<Candidate> = entries
        .filter_map(Result::ok)
        .filter_map(|entry| {
            let name = entry.file_name().into_string().ok()?;
            if !is_sha256_hex(&name) || name == exclude {
                return None;
            }
            let modified = fs::symlink_metadata(entry.path()).ok()?.modified().ok()?;
            Some(Candidate { name, modified })
        })
        .collect();
    candidates.sort_by_key(|candidate| std::cmp::Reverse(candidate.modified));
    candidates
}

/// The newest other retained generation that `read` accepts as a donor.
///
/// A neighbour is a donor only when its own marker is readable and holds the content record that makes a hardlink
/// safe; the newest is preferred because a build changes incrementally. `digest` is the generation being staged,
/// and excluding it is not tidiness: an interrupted earlier attempt at the same digest can be sitting there
/// half-written, and it is the one neighbour whose name promises exactly the bytes this stage is about to produce.
pub(crate) fn newest_donor<D>(runtime_root: &Path, digest: &str, read: impl Fn(&Path) -> Option<D>) -> Option<D> {
    let generations = generations_dir(runtime_root);
    generation_candidates(runtime_root, digest)
        .into_iter()
        .find_map(|candidate| read(&generations.join(&candidate.name)))
}

/// Inverts one generation's `staged name -> sha256` record into `sha256 -> full path`.
///
/// The inversion is the safety property: the key a donor file is looked up by is the digest of its bytes, so the
/// only file that can ever be linked is one whose contents the lookup already matched. A same-named file in a
/// neighbouring generation is not reachable through this map at all.
pub(crate) fn staged_files_by_content(root: &Path, record: &BTreeMap<String, String>) -> HashMap<String, PathBuf> {
    record.iter().map(|(name, sha)| (sha.clone(), root.join(name))).collect()
}

/// A retained runtime generation the stager may link an unchanged jar from.
pub(crate) struct RuntimeDonor {
    pub by_content: HashMap<String, PathBuf>,
    /// The lengths of the files this donor holds, so that a source of an unseen length is never hashed.
    pub sizes: HashSet<u64>,
}

impl RuntimeDonor {
    pub(crate) fn from_record(lib: &Path, record: &BTreeMap<String, String>) -> Self {
        let by_content = staged_files_by_content(lib, record);
        let sizes = by_content
            .values()
            .filter_map(|path| fs::metadata(path).ok())
            .map(|info| info.len())
            .collect();
        Self { by_content, sizes }
    }
}
