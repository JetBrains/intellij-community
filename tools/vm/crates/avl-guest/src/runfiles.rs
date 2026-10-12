//! `runfiles-tree`: the runfiles tree of a host MANIFEST, built on the guest's own disk.
//!
//! A Windows host writes the runfiles MANIFEST of a test and no runfiles tree, and a Unix tree would not help: its
//! links carry absolute host targets such as `C:\…`, which a Linux guest cannot follow. So this verb reads the
//! MANIFEST through a share and makes one entry per line under the worker data directory:
//!
//! - an absolute host target becomes a symbolic link to its guest path, through the request's
//!   [`avl_wire::path_map::PathMap`]. A target that no prefix of the table holds is the refusal
//!   [`UNMAPPED_TARGET_CODE`], because a link to a path the guest cannot see is a runfile that is silently missing;
//! - a relative target stays a symbolic link to the same text;
//! - an empty target is an empty file, as in a tree that Bazel builds.
//!
//! The tree is built beside its final name and published by one rename, so a reader sees a whole tree or none. Its
//! name is [`avl_wire::runfiles::tree_digest`] of the MANIFEST bytes and the path table, which the controller also
//! computes to know the root before it asks. A tree of that digest that is already there is reused as it is.
//!
//! The verb owns the layout of its destination. After each build or reuse it keeps the tree it answers and the newest
//! other tree, and it removes every other entry.
//!
//! The request and the reply are declared in `avl_wire::runfiles`.

use std::fs::{self, File};
use std::io::Read;
use std::path::{Path, PathBuf};

use avl_wire::path_map::is_absolute_host_path;
use avl_wire::runfiles::{RunfilesManifest, RunfilesTreeRequest, RunfilesTreeResult, SCHEMA_VERSION, tree_digest};
use avl_wire::verb::AgentVerb;

use crate::reply::AgentRefusalExt;
use crate::reply::{AgentRefusal, quoted};

#[cfg(test)]
mod tests;

/// The refusal of a MANIFEST line whose absolute target is outside every prefix of the path table.
pub(crate) const UNMAPPED_TARGET_CODE: &str = "runfiles_target_unmapped";

/// Reads the request on `stdin` and builds its tree.
pub(crate) fn build_from(stdin: &mut dyn Read) -> Result<RunfilesTreeResult, AgentRefusal> {
    let mut raw = Vec::new();
    stdin
        .read_to_end(&mut raw)
        .map_err(|error| AgentRefusal::for_verb(AgentVerb::RunfilesTree, format!("cannot read the runfiles tree request: {error}")))?;
    let request: RunfilesTreeRequest = serde_json::from_slice(&raw)
        .map_err(|error| AgentRefusal::for_verb(AgentVerb::RunfilesTree, format!("invalid runfiles tree request: {error}")))?;
    build(&request)
}

/// Builds the tree of `request`, or reuses the tree of the same digest.
pub(crate) fn build(request: &RunfilesTreeRequest) -> Result<RunfilesTreeResult, AgentRefusal> {
    if request.schema_version != SCHEMA_VERSION {
        return Err(AgentRefusal::for_verb(
            AgentVerb::RunfilesTree,
            format!(
                "runfiles tree request of schema {} is not schema {SCHEMA_VERSION}",
                request.schema_version
            ),
        ));
    }
    let manifest_path = absolute(&request.manifest, "MANIFEST")?;
    let destination = absolute(&request.destination, "destination")?;
    if destination == Path::new("/") {
        return Err(AgentRefusal::for_verb(
            AgentVerb::RunfilesTree,
            "the runfiles destination must not be /",
        ));
    }
    if let Some(prefix) = request.path_map.invalid_prefix() {
        return Err(AgentRefusal::for_verb(
            AgentVerb::RunfilesTree,
            format!(
                "the path table holds a prefix that is not absolute on both sides: {} -> {}",
                quoted(&prefix.host),
                quoted(&prefix.guest)
            ),
        ));
    }
    let bytes = fs::read(&manifest_path).map_err(|error| {
        AgentRefusal::for_verb(
            AgentVerb::RunfilesTree,
            format!("cannot read the MANIFEST {}: {error}", manifest_path.display()),
        )
    })?;
    let text = String::from_utf8(bytes).map_err(|error| {
        AgentRefusal::for_verb(
            AgentVerb::RunfilesTree,
            format!("the MANIFEST {} is not UTF-8: {}", manifest_path.display(), error.utf8_error()),
        )
    })?;
    let manifest = RunfilesManifest::parse(&text).map_err(|error| AgentRefusal::for_verb(AgentVerb::RunfilesTree, error.to_string()))?;
    let digest = tree_digest(text.as_bytes(), &request.path_map);
    let entries = u32::try_from(manifest.entries.len())
        .map_err(|error| AgentRefusal::for_verb(AgentVerb::RunfilesTree, format!("the MANIFEST holds too many runfiles: {error}")))?;
    let root = destination.join(&digest);
    let reused = if root.is_dir() {
        touch(&root);
        true
    } else {
        publish(&destination, &digest, &manifest, request)?
    };
    Ok(RunfilesTreeResult {
        root: root.to_string_lossy().into_owned(),
        digest: digest.clone(),
        entries,
        reused,
        removed: retain(&destination, &digest),
    })
}

/// Builds the tree of `digest` beside its final name and publishes it by one rename. Answers whether a build of the
/// same digest published first, so this one was not needed.
fn publish(destination: &Path, digest: &str, manifest: &RunfilesManifest, request: &RunfilesTreeRequest) -> Result<bool, AgentRefusal> {
    let root = destination.join(digest);
    fs::create_dir_all(destination)
        .map_err(|error| AgentRefusal::for_verb(AgentVerb::RunfilesTree, format!("cannot create {}: {error}", destination.display())))?;
    let staging = destination.join(format!(".{digest}.{}.partial", std::process::id()));
    let _ = fs::remove_dir_all(&staging);
    let built = fs::create_dir(&staging)
        .map_err(|error| AgentRefusal::for_verb(AgentVerb::RunfilesTree, format!("cannot create {}: {error}", staging.display())))
        .and_then(|()| populate(&staging, manifest, request));
    if let Err(refusal) = built {
        let _ = fs::remove_dir_all(&staging);
        return Err(refusal);
    }
    match fs::rename(&staging, &root) {
        Ok(()) => Ok(false),
        // Another build of the same digest published first: its tree is the same tree.
        Err(_) if root.is_dir() => {
            let _ = fs::remove_dir_all(&staging);
            Ok(true)
        }
        Err(error) => {
            let _ = fs::remove_dir_all(&staging);
            Err(AgentRefusal::for_verb(
                AgentVerb::RunfilesTree,
                format!("cannot publish {}: {error}", root.display()),
            ))
        }
    }
}

/// Gives a reused tree the current time, so the retention of the next build sees it as the tree used last.
fn touch(root: &Path) {
    if let Ok(directory) = File::open(root) {
        let _ = directory.set_modified(std::time::SystemTime::now());
    }
}

/// Keeps the tree of `current` and the newest other tree, and removes every other entry of `destination`: older
/// trees, the staging directory of a build that died, and anything else. Answers the paths it removed.
///
/// The newest other tree stays so that a daemon of the previous build could still read its runfiles. The caller
/// runs the verb when no process reads a tree, so the rule is only a margin. A removal that fails is not a refusal:
/// the tree the reply names is whole, and the next build tries the removal again.
fn retain(destination: &Path, current: &str) -> Vec<String> {
    let Ok(listing) = fs::read_dir(destination) else {
        return Vec::new();
    };
    let mut trees = Vec::new();
    let mut others = Vec::new();
    for entry in listing.flatten() {
        let name = entry.file_name().to_string_lossy().into_owned();
        if name == current {
            continue;
        }
        let Ok(metadata) = entry.path().symlink_metadata() else {
            continue;
        };
        if metadata.is_dir() && avl_wire::stage::is_sha256_hex(&name) {
            let modified = metadata.modified().unwrap_or(std::time::UNIX_EPOCH);
            trees.push((modified, entry.path()));
        } else {
            others.push((metadata.is_dir(), entry.path()));
        }
    }
    // The newest first. Of two trees of the same time, the one with the lower name stays.
    trees.sort_by(|(left_time, left), (right_time, right)| right_time.cmp(left_time).then_with(|| left.cmp(right)));
    let mut removed = Vec::new();
    for (is_directory, path) in trees.into_iter().skip(1).map(|(_, path)| (true, path)).chain(others) {
        let result = if is_directory {
            fs::remove_dir_all(&path)
        } else {
            fs::remove_file(&path)
        };
        if result.is_ok() {
            removed.push(path.to_string_lossy().into_owned());
        }
    }
    removed.sort();
    removed
}

/// Writes every runfile of `manifest` under `staging`.
fn populate(staging: &Path, manifest: &RunfilesManifest, request: &RunfilesTreeRequest) -> Result<(), AgentRefusal> {
    for entry in &manifest.entries {
        let entry_path = runfile_path(staging, &entry.path)?;
        if let Some(parent) = entry_path.parent() {
            fs::create_dir_all(parent).map_err(|error| {
                AgentRefusal::for_verb(
                    AgentVerb::RunfilesTree,
                    format!("cannot create the directory of the runfile {}: {error}", quoted(&entry.path)),
                )
            })?;
        }
        let written = if entry.target.is_empty() {
            File::create_new(&entry_path).map(drop)
        } else if is_absolute_host_path(&entry.target) {
            let Some(guest) = request.path_map.map(&entry.target) else {
                return Err(AgentRefusal::refused(
                    UNMAPPED_TARGET_CODE,
                    format!(
                        "the runfile {} points at {}, which no prefix of the path table holds",
                        quoted(&entry.path),
                        quoted(&entry.target)
                    ),
                ));
            };
            std::os::unix::fs::symlink(guest, &entry_path)
        } else {
            std::os::unix::fs::symlink(&entry.target, &entry_path)
        };
        written.map_err(|error| {
            AgentRefusal::for_verb(
                AgentVerb::RunfilesTree,
                format!("cannot write the runfile {}: {error}", quoted(&entry.path)),
            )
        })?;
    }
    Ok(())
}

/// Where the runfile at `logical` goes under `root`. A path that is absolute, or that holds an empty, `.` or `..`
/// component, would land outside the tree or on another entry, so it is refused.
fn runfile_path(root: &Path, logical: &str) -> Result<PathBuf, AgentRefusal> {
    let components: Vec<&str> = logical.split('/').collect();
    if components.iter().any(|component| matches!(*component, "" | "." | "..")) {
        return Err(AgentRefusal::for_verb(
            AgentVerb::RunfilesTree,
            format!("the runfile path {} is not a relative path inside the tree", quoted(logical)),
        ));
    }
    Ok(components.iter().fold(root.to_path_buf(), |path, component| path.join(component)))
}

fn absolute(value: &str, what: &str) -> Result<PathBuf, AgentRefusal> {
    let path = Path::new(value);
    if !path.is_absolute() {
        return Err(AgentRefusal::for_verb(
            AgentVerb::RunfilesTree,
            format!("the runfiles {what} must be an absolute guest path, not {}", quoted(value)),
        ));
    }
    Ok(path.to_path_buf())
}
