//! Materializes, on the guest's own disk, what a run may not read through the read-only share: the daemon's
//! runtime, a flat jar list plus an extracted JBR, about 852 MiB of it.
//!
//! The runtime changes one part at a time, so the stager hardlinks what a retained generation already holds and
//! copies only what moved ([`donor`] holds the rule that keeps such a link off stale bytes). Both generations are
//! on this filesystem, which is what makes the link legal; the share never is, which is why a source can only ever
//! be copied.
//!
//! Every check refuses with the refusal of its verb, `guest_<verb>_failed` with exit 70
//! ([`AgentRefusalExt::for_verb`]), so a helper that two verbs share takes
//! the verb. The message is written for a human on the host, so it must stay this process's own text: never append a
//! subprocess's output to one.
//!
//! The manifests and replies are declared in `avl_wire::stage`; this module is only the half that acts on them.

pub(crate) mod donor;
pub(crate) mod gc;
pub(crate) mod launch_prep;
pub(crate) mod runtime;

#[cfg(test)]
mod tests;

use std::fs::{self, File, OpenOptions};
use std::io::{self, Read, Write};
use std::os::unix::fs::OpenOptionsExt;
use std::path::{Path, PathBuf};

use avl_wire::stage::{GENERATIONS_DIR, is_sha256_hex};
use nix::errno::Errno;
use nix::sys::signal::kill;
use nix::unistd::Pid;
use serde::Serialize;
use sha2::{Digest, Sha256};

use crate::reply::{AgentRefusal, AgentRefusalExt, refuse};
use avl_wire::verb::AgentVerb;

/// The guest's own `tar`, by absolute path: these verbs run under an exec channel that decides the PATH, and the
/// archive each one unpacks is a host-built artifact.
pub(crate) const TAR_BINARY: &str = "/usr/bin/tar";

pub(crate) fn require_runtime_root(verb: AgentVerb, value: &str) -> Result<PathBuf, AgentRefusal> {
    let path = Path::new(value);
    if value.is_empty() || !path.is_absolute() || path == Path::new("/") {
        refuse!(verb, "runtime root must be a non-root absolute path");
    }
    Ok(path.to_path_buf())
}

pub(crate) fn generations_dir(runtime_root: &Path) -> PathBuf {
    runtime_root.join(GENERATIONS_DIR)
}

pub(crate) fn generation_root(verb: AgentVerb, runtime_root: &Path, digest: &str) -> Result<PathBuf, AgentRefusal> {
    if !is_sha256_hex(digest) {
        refuse!(verb, "invalid runtime digest: {digest}");
    }
    Ok(generations_dir(runtime_root).join(digest))
}

pub(crate) fn is_file(path: &Path) -> bool {
    fs::metadata(path).is_ok_and(|info| info.is_file())
}

pub(crate) fn is_directory(path: &Path) -> bool {
    fs::metadata(path).is_ok_and(|info| info.is_dir())
}

/// A sink that hashes what passes through it on the way to a file, so recording a digest costs no second read.
struct Hashing<W> {
    inner: W,
    hasher: Sha256,
}

impl<W: Write> Write for Hashing<W> {
    fn write(&mut self, buffer: &[u8]) -> io::Result<usize> {
        let written = self.inner.write(buffer)?;
        self.hasher.update(&buffer[..written]);
        Ok(written)
    }

    fn flush(&mut self) -> io::Result<()> {
        self.inner.flush()
    }
}

/// Copies `source` to `destination` (private, truncated) and answers the sha256 of the bytes written.
///
/// The digest is taken as the bytes travel to the file, which makes the record a statement about the *staged*
/// file rather than the share: the next generation hardlinks against these hashes.
pub(crate) fn copy_file_hashed(source: &Path, destination: &Path) -> io::Result<String> {
    let mut input = File::open(source)?;
    let output = OpenOptions::new()
        .write(true)
        .create(true)
        .truncate(true)
        .mode(0o600)
        .open(destination)?;
    let mut sink = Hashing {
        inner: output,
        hasher: Sha256::new(),
    };
    io::copy(&mut input, &mut sink)?;
    sink.flush()?;
    Ok(hex::encode(sink.hasher.finalize()))
}

/// The content identity of one file, streamed because the files are jars.
pub(crate) fn sha256_file(path: &Path) -> io::Result<String> {
    let mut file = File::open(path)?;
    let mut hasher = Sha256::new();
    let mut buffer = vec![0; 64 * 1024];
    loop {
        let read = file.read(&mut buffer)?;
        if read == 0 {
            return Ok(hex::encode(hasher.finalize()));
        }
        hasher.update(&buffer[..read]);
    }
}

pub(crate) fn write_private(path: &Path, content: &[u8]) -> io::Result<()> {
    let mut file = OpenOptions::new().write(true).create(true).truncate(true).mode(0o600).open(path)?;
    file.write_all(content)
}

pub(crate) fn write_json_file(path: &Path, value: &impl Serialize) -> io::Result<()> {
    let mut encoded = serde_json::to_vec(value)?;
    encoded.push(b'\n');
    write_private(path, &encoded)
}

/// Creates a directory tree, readable by everyone (the generations are read by the daemon's own account).
pub(crate) fn create_dirs(verb: AgentVerb, path: &Path) -> Result<(), AgentRefusal> {
    fs::create_dir_all(path).map_err(|error| AgentRefusal::for_verb(verb, format!("cannot create {}: {error}", path.display())))
}

/// Removes a tree that may not exist. Used on paths this verb owns (its own staging root, the generation it is
/// about to replace), where a failed removal shows up in the next step's error.
pub(crate) fn remove_tree(path: &Path) {
    let _ = fs::remove_dir_all(path);
}

/// The prefix of a staging tree's name. The whole name is `.stage-<name>-<pid>`.
const STAGING_PREFIX: &str = ".stage-";

/// One verb's staging tree, `<parent>/.stage-<name>-<pid>`. The drop removes it, unless [`Self::publish`] renamed it
/// into place.
///
/// Every refusal between the first write and the rename returns early, and a runtime tree holds up to 852 MiB, so
/// the removal belongs to the drop and not to each return.
pub(crate) struct StagingTree {
    path: PathBuf,
    published: bool,
}

impl StagingTree {
    /// Names the tree and removes what an earlier process with the same pid left at that path. Creates nothing.
    pub(crate) fn new(parent: &Path, name: &str) -> Self {
        let path = parent.join(format!("{STAGING_PREFIX}{name}-{}", std::process::id()));
        remove_tree(&path);
        Self { path, published: false }
    }

    pub(crate) fn path(&self) -> &Path {
        &self.path
    }

    /// Renames the tree onto `destination`. After a successful rename the drop leaves the destination alone.
    pub(crate) fn publish(mut self, destination: &Path) -> io::Result<()> {
        fs::rename(&self.path, destination)?;
        self.published = true;
        Ok(())
    }
}

impl Drop for StagingTree {
    fn drop(&mut self) {
        if !self.published {
            remove_tree(&self.path);
        }
    }
}

/// Removes each staging tree under `parent` whose process no longer runs, and answers the removed names in order.
///
/// A killed stage never reaches the drop of its [`StagingTree`], so its tree stays until this runs. The pid in the
/// name is the only owner record. A pid is reused, so a tree whose pid names a live process again stays: this
/// check can keep a dead tree, and it never removes the tree of a live stage.
pub(crate) fn remove_abandoned_staging_trees(parent: &Path) -> Vec<String> {
    let Ok(entries) = fs::read_dir(parent) else {
        return Vec::new();
    };
    let mut abandoned: Vec<String> = entries
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_ok_and(|kind| kind.is_dir()))
        .filter_map(|entry| entry.file_name().into_string().ok())
        .filter(|name| staging_tree_pid(name).is_some_and(|pid| !process_runs(pid)))
        .collect();
    abandoned.sort();
    for name in &abandoned {
        remove_tree(&parent.join(name));
    }
    abandoned
}

/// The pid a staging tree's name records, or `None` for a name of another shape.
fn staging_tree_pid(name: &str) -> Option<i32> {
    let (_, pid) = name.strip_prefix(STAGING_PREFIX)?.rsplit_once('-')?;
    pid.parse().ok().filter(|pid| *pid > 0)
}

/// Whether a process with `pid` exists. Signal 0 delivers nothing, and EPERM counts as alive: a process of another
/// account exists too.
fn process_runs(pid: i32) -> bool {
    match kill(Pid::from_raw(pid), None) {
        Ok(()) => true,
        Err(error) => error == Errno::EPERM,
    }
}
