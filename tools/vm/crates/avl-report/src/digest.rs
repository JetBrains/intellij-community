//! The content identities that decide whether a warm iteration reuses a staged generation.
//!
//! # Why the identities are the point of this module
//!
//! A staged runtime generation holds the daemon's stable classpath tier and the extracted JBR. On the Linux
//! guest that stable tier alone was 852 MiB, and `stage-runtime` cost 22.0 s of a cold `daemon start`. Whether an
//! iteration pays that or reuses what the guest already holds is decided by five digests composed out of the
//! primitives here. Both ways of getting one wrong are expensive and neither announces itself: a digest that
//! changes when nothing changed restages gigabytes every iteration, and a digest that stays the same when
//! something *did* change runs the next test against the previous build's bytes and reports the result as if it
//! were fresh. So every input that can change a staged tree is folded in explicitly, and nothing here derives an
//! identity from anything a build tool merely happened to write.
//!
//! The Bazel runtime descriptor these identities are taken over is [`avl_wire::runtime`].

use std::collections::BTreeMap;
use std::fs::{self, File, Metadata};
use std::io::{self, Read};
use std::path::{Path, PathBuf};
use std::time::UNIX_EPOCH;

use avl_base::{Exit, OrRefuse, Refusal};
use serde::{Deserialize, Serialize};
use sha2::{Digest as _, Sha256};

#[cfg(test)]
mod tests;

// --- the identities -------------------------------------------------------------------------------------------

/// The content identity of a string: the hex SHA-256 of its UTF-8 bytes.
pub fn sha256_text(value: &str) -> String {
    hex::encode(Sha256::digest(value.as_bytes()))
}

/// The content identity of one file, streamed: the inputs are the daemon's jars and the data it mounts, and the
/// first sweep reads all of it.
pub fn sha256_file(path: &Path) -> io::Result<String> {
    // A read loop rather than `io::copy`: digest 0.11 dropped its `io::Write` implementation.
    let mut file = File::open(path)?;
    let mut hash = Sha256::new();
    let mut buffer = vec![0; 64 * 1024];
    loop {
        match file.read(&mut buffer) {
            Ok(0) => break,
            Ok(read) => hash.update(&buffer[..read]),
            Err(error) if error.kind() == io::ErrorKind::Interrupted => {}
            Err(error) => return Err(error),
        }
    }
    Ok(hex::encode(hash.finalize()))
}

/// One named thing and the digest of its bytes.
///
/// `path` is not always a path: the compositions mix in synthetic names like `@static-launch` and
/// `@guest-policy`, so that a policy change is indistinguishable, to the reuse decision, from a file change. Both
/// mean the staged tree cannot be reused.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
pub struct PathDigest {
    pub path: String,
    pub sha256: String,
}

impl PathDigest {
    pub fn new(path: impl Into<String>, sha256: impl Into<String>) -> Self {
        Self {
            path: path.into(),
            sha256: sha256.into(),
        }
    }
}

/// The ordered, path-sensitive identity of a set of named digests.
///
/// Both halves are load-bearing. Order, because a classpath is ordered and two jars that both define a class
/// resolve differently when they swap places. The path, because the guest's parity layout makes host and guest
/// paths identical: an identity over contents alone would call two different layouts of the same bytes one
/// generation. The NUL separators make the concatenation unambiguous: without them `{a, bc}` and `{ab, c}` hash
/// identically.
pub fn path_sensitive_digest(entries: &[PathDigest]) -> String {
    let mut hash = Sha256::new();
    for entry in entries {
        hash.update(entry.path.as_bytes());
        hash.update([0]);
        hash.update(entry.sha256.as_bytes());
        hash.update([0]);
    }
    hex::encode(hash.finalize())
}

/// How the guest is allowed to obtain and unpack its runtime.
///
/// The field order is contract: [`product_identity`] folds this in as the JSON text of the object, so reordering
/// the fields changes `productDigest` for every worker, which restages 4.22 GB once per worker and says nothing
/// about why.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct JbrPolicy {
    pub platform: String,
    pub java_home_suffix: String,
    pub preloaded_only: bool,
}

/// Every declared input that can change the share-backed IDE product.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ProductIdentityInput {
    pub fingerprint: PathDigest,
    pub config: PathDigest,
    pub jbr_manifest: PathDigest,
    pub jbr_policy: JbrPolicy,
}

/// The content identity of the staged IDE product, `productDigest`.
///
/// The JBR policy is folded in under a synthetic name because it is not a file and still changes the product: the
/// same archive unpacked under a different Java-home suffix is a different distribution, and a guest that may
/// only use a preloaded runtime stages a different tree from one that may download.
pub fn product_identity(input: &ProductIdentityInput) -> String {
    // A struct of strings and a bool always serializes; the error text stands in only so that a failure could
    // never collide with a real serialization.
    let policy = serde_json::to_string(&input.jbr_policy).unwrap_or_else(|error| format!("unencodable:{error}"));
    path_sensitive_digest(&[
        input.fingerprint.clone(),
        input.config.clone(),
        input.jbr_manifest.clone(),
        PathDigest::new("@guest-policy", sha256_text(&policy)),
    ])
}

// --- the digest cache -----------------------------------------------------------------------------------------

/// One file's remembered identity, keyed by what a stat can see.
///
/// The mtime is whole nanoseconds since the epoch, an exact integer: a float key only survives a JSON round trip
/// when the parser is exact, and every entry it misreads is a file re-hashed on every warm iteration. A cache that
/// does not decode costs one sweep instead of being half believed.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct CacheEntry {
    size: u64,
    mtime_ns: u64,
    ino: u64,
    sha256: String,
}

impl CacheEntry {
    const fn matches(&self, key: &StatKey) -> bool {
        self.size == key.size && self.mtime_ns == key.mtime_ns && self.ino == key.ino
    }
}

/// What a stat can tell about a file's identity. The inode is 0 where the platform offers none: size and mtime
/// alone are a weaker key, but they are the two that catch a rebuild, and losing the inode degrades the key
/// rather than the answer.
struct StatKey {
    size: u64,
    mtime_ns: u64,
    ino: u64,
}

impl StatKey {
    fn of(metadata: &Metadata) -> Self {
        // An mtime before the epoch or past 2554 has no u64 nanosecond spelling; 0 still keys on size and inode.
        let mtime_ns = metadata
            .modified()
            .ok()
            .and_then(|modified| modified.duration_since(UNIX_EPOCH).ok())
            .and_then(|since| u64::try_from(since.as_nanos()).ok())
            .unwrap_or(0);
        Self {
            size: metadata.len(),
            mtime_ns,
            ino: inode(metadata),
        }
    }
}

#[cfg(unix)]
fn inode(metadata: &Metadata) -> u64 {
    use std::os::unix::fs::MetadataExt;
    metadata.ino()
}

#[cfg(not(unix))]
const fn inode(_metadata: &Metadata) -> u64 {
    0
}

/// The permission bits a directory digest records: the mode where there is one, and the read-only flag, the only
/// permission Windows reports, elsewhere.
#[cfg(unix)]
fn permission_bits(metadata: &Metadata) -> u32 {
    use std::os::unix::fs::PermissionsExt;
    metadata.permissions().mode() & 0o777
}

#[cfg(not(unix))]
fn permission_bits(metadata: &Metadata) -> u32 {
    if metadata.permissions().readonly() { 0o444 } else { 0o644 }
}

/// Content digests keyed by `(size, mtimeNs, inode)`, so that only files Bazel actually rewrote are re-hashed.
///
/// The first stamp over a full ultimate runfiles tree reads gigabytes once; every later run is a stat scan. That
/// is the difference between a 16 s stamp and a sub-second one on every warm iteration.
///
/// Single-owner (`&mut self`): the daemon build opens its own cache and walks its inputs one at a time.
#[derive(Debug)]
pub struct FileDigestCache {
    path: PathBuf,
    entries: BTreeMap<String, CacheEntry>,
    dirty: bool,
}

impl FileDigestCache {
    /// Reads a cache file, treating an unreadable one as empty.
    ///
    /// Never an error: the cache is a performance record, and a truncated or half-written one must cost a sweep
    /// rather than a run. Every digest it holds is checked against a fresh stat before it is believed, so a wrong
    /// file here can only produce a slow answer, never a wrong one.
    pub fn open(path: &Path) -> Self {
        let entries = fs::read(path)
            .ok()
            .and_then(|content| serde_json::from_slice(&content).ok())
            .unwrap_or_default();
        Self {
            path: path.to_owned(),
            entries,
            dirty: false,
        }
    }

    /// One file's or directory's content identity, hashing a file only if its stat key moved.
    ///
    /// The failure is a plain error rather than a refusal, because the callers are the ones that know what a
    /// missing declared input means and which rule declared it.
    pub fn digest(&mut self, path: &Path) -> io::Result<String> {
        let metadata = fs::metadata(path)?;
        if metadata.is_dir() {
            return self.directory_digest(path);
        }
        self.file_digest(path, &metadata)
    }

    fn file_digest(&mut self, path: &Path, metadata: &Metadata) -> io::Result<String> {
        let key = StatKey::of(metadata);
        // A path that is not UTF-8 cannot be a key of the JSON file; it is hashed every time instead.
        let Some(name) = path.to_str() else {
            return sha256_file(path);
        };
        if let Some(cached) = self.entries.get(name)
            && cached.matches(&key)
        {
            return Ok(cached.sha256.clone());
        }
        let sha256 = sha256_file(path)?;
        self.entries.insert(
            name.to_owned(),
            CacheEntry {
                size: key.size,
                mtime_ns: key.mtime_ns,
                ino: key.ino,
                sha256: sha256.clone(),
            },
        );
        self.dirty = true;
        Ok(sha256)
    }

    /// A directory's identity: every entry's relative path with its kind, permission bits and content, in name
    /// order.
    ///
    /// The walk follows the root link, so a directory reached through an alias is the same input; a nested link is
    /// recorded by its target rather than followed, so a link that moves changes the identity. The walk is sorted
    /// by name, or the digest would change every run and restage the tree every iteration.
    fn directory_digest(&mut self, path: &Path) -> io::Result<String> {
        let mut entries = Vec::new();
        for entry in walkdir::WalkDir::new(path).follow_root_links(true).sort_by_file_name() {
            let entry = entry?;
            let relative = entry.path().strip_prefix(path).map_err(io::Error::other)?;
            let relative = if relative.as_os_str().is_empty() {
                ".".to_owned()
            } else {
                relative
                    .components()
                    .map(|component| component.as_os_str().to_string_lossy())
                    .collect::<Vec<_>>()
                    .join("/")
            };
            // walkdir reports a root link as a link, so the root takes the metadata of the directory it names.
            let metadata = if entry.depth() == 0 {
                fs::metadata(path)?
            } else {
                entry.metadata()?
            };
            let file_type = metadata.file_type();
            let sha256 = if file_type.is_dir() {
                sha256_text(&format!("directory:{:o}", permission_bits(&metadata)))
            } else if file_type.is_symlink() {
                let target = fs::read_link(entry.path())?;
                sha256_text(&format!("symlink:{}", target.to_string_lossy()))
            } else if file_type.is_file() {
                let content = self.file_digest(entry.path(), &metadata)?;
                sha256_text(&format!("file:{:o}:{content}", permission_bits(&metadata)))
            } else {
                return Err(io::Error::other(format!("unsupported runtime input: {}", entry.path().display())));
            };
            entries.push(PathDigest::new(relative, sha256));
        }
        Ok(path_sensitive_digest(&entries))
    }

    /// Publishes the cache atomically at 0600, and does nothing when nothing was hashed.
    ///
    /// Atomic because a half-written cache is not merely a slow next run: it is a document the next process
    /// silently discards, which turns one interrupted sweep into a re-hash of gigabytes on every run until
    /// something rewrites the file completely.
    pub fn save(&mut self) -> Result<(), Refusal> {
        if !self.dirty {
            return Ok(());
        }
        let encoded = serde_json::to_vec(&self.entries).or_refuse("state_write_failed", Exit::FAILURE, || {
            format!("cannot encode {}", self.path.display())
        })?;
        avl_base::fs::write_atomically(&self.path, &encoded, 0o600)?;
        self.dirty = false;
        Ok(())
    }
}
