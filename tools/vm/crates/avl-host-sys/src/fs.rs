//! The host half of writing to the host's disk safely: private directories, the gate every path a guest named has
//! to pass before the controller writes to it, and the real path of a host directory. Publication itself (atomic
//! replace, no-clobber publish) is portable and lives in [`avl_base::fs`]. What "private" means on each platform
//! is [`crate::private`]'s.
//!
//! Every function here is a refusal that was learned rather than designed. The controller's state is a set of
//! mode-0600 receipts checked fail-closed, and one way that state has gone wrong is a path from inside the guest
//! that resolved somewhere the controller did not intend. That is what [`resolve_pull_destination`] exists for.

use std::fs;
use std::io;
use std::path::{Component, Path, PathBuf};

use avl_base::fs::create_private_dir;
use avl_base::{Exit, OrRefuse, Refusal};
use sha2::{Digest, Sha256};

use crate::private::{create_private_directory, directory_protection, restrict_directory};

#[cfg(test)]
mod tests;

/// Answers where a `pull` may write, and refuses everything else.
///
/// The input is a relative path a caller chose, and what it must not be able to reach is anything outside this
/// worker's own artifact directory. The refusals, in the order they are applied:
///
/// - empty, absolute, or containing a NUL - none of which is a relative path under a directory;
/// - resolving to the directory itself or above it, after `..` has been collapsed;
/// - a parent component that is not a private current-user directory, checked at every level rather than only at
///   the root, because one symlinked component is enough;
/// - a parent whose realpath is not itself, which is the check that catches a symlink planted between the walk and
///   the write;
/// - a destination that already exists, refused rather than overwritten, because the caller asked to publish an
///   artifact and not to replace one.
pub fn resolve_pull_destination(runtime_root: &Path, worker_key: &str, input: &str) -> Result<PathBuf, Refusal> {
    let unsafe_path = |message: &str| Refusal::new("pull_destination_unsafe", Exit::USAGE, message);
    if input.is_empty() || input.contains('\0') || Path::new(input).is_absolute() {
        return Err(unsafe_path(
            "pull destination must be a relative path beneath the worker artifact directory",
        ));
    }
    let root = real_path(runtime_root).or_refuse("pull_destination_unsafe", Exit::NO_PERM, || {
        format!("cannot resolve the runtime root {}", runtime_root.display())
    })?;
    ensure_private_directory(&root)?;
    let artifacts = root.join("artifacts");
    let worker_artifacts = artifacts.join(worker_key);
    ensure_private_directory(&artifacts)?;
    ensure_private_directory(&worker_artifacts)?;

    // `..` collapsed lexically, as a join would: `a/../b` is `b`, and anything that climbs out is refused. The
    // directory itself (`.`, `a/..`) is refused here too, rather than a few lines below as an existing
    // destination, which would tell the caller to pick another name for a request that was never allowed.
    let beneath = unsafe_path("pull destination must remain beneath the worker artifact directory");
    let mut components: Vec<&std::ffi::OsStr> = Vec::new();
    for component in Path::new(input).components() {
        match component {
            Component::Normal(name) => components.push(name),
            Component::CurDir => {}
            Component::ParentDir => {
                if components.pop().is_none() {
                    return Err(beneath);
                }
            }
            Component::RootDir | Component::Prefix(_) => return Err(beneath),
        }
    }
    let Some((file_name, parents)) = components.split_last() else {
        return Err(beneath);
    };

    let mut parent = worker_artifacts;
    for component in parents {
        parent.push(component);
        ensure_private_directory(&parent)?;
    }
    // After the walk, not before: a symlink planted while the directories were being created would otherwise have
    // been followed by the write.
    if real_path(&parent).ok().as_deref() != Some(parent.as_path()) {
        return Err(Refusal::new(
            "pull_destination_unsafe",
            Exit::NO_PERM,
            format!("artifact parent resolved outside its canonical path: {}", parent.display()),
        ));
    }
    let destination = parent.join(file_name);
    match fs::symlink_metadata(&destination) {
        Ok(_) => Err(Refusal::new(
            "pull_destination_exists",
            Exit::CANT_CREATE,
            format!("refusing to overwrite an existing artifact: {}", destination.display()),
        )),
        Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(destination),
        Err(error) => Err(Refusal::new(
            "pull_destination_unsafe",
            Exit::NO_PERM,
            format!("cannot inspect {}: {error}", destination.display()),
        )),
    }
}

/// Creates a private directory and refuses one that is not private and current-user owned.
///
/// Four separate checks, and each one is a way the same attack lands: a symlink pointing elsewhere, a file where a
/// directory was expected, another user's directory, and a directory of the right owner that others can open. The
/// check never follows a link, or it would follow the symlink it is meant to detect.
pub fn ensure_private_directory(path: &Path) -> Result<(), Refusal> {
    let refuse = |message: String| Refusal::new("pull_destination_unsafe", Exit::NO_PERM, message);
    match create_private_directory(path) {
        Ok(()) => {}
        Err(error) if error.kind() == io::ErrorKind::AlreadyExists => {}
        Err(error) => {
            return Err(refuse(format!("cannot create the artifact directory {}: {error}", path.display())));
        }
    }
    let protection =
        directory_protection(path).map_err(|error| refuse(format!("cannot inspect the artifact directory {}: {error}", path.display())))?;
    let private = protection.is_some_and(|protection| protection.owned_by_caller() && protection.is_private());
    if !private {
        return Err(refuse(format!(
            "artifact directory must be a private, current-user-owned directory: {}",
            path.display()
        )));
    }
    Ok(())
}

/// Creates the controller's own state directories at 0700: `workers`, `receipts` and each worker directory, and
/// makes the root 0700 too. On Windows the root gets the private access list, and the directories below it
/// inherit it.
///
/// The mode is set as well as asked for at creation, and on the root as well as the children: a creation mode is
/// masked by `umask`, so a run under a permissive umask would leave a world-readable directory holding mode-0600
/// receipts - not an immediate leak, but the difference between one wrong permission and two. On Windows the root
/// is what makes a receipt private: the receipt writer sets no access list of its own, so a receipt inherits the
/// list of the directory it is written in.
pub fn prepare_runtime_dirs(runtime_root: &Path, worker_dirs: &[PathBuf]) -> Result<(), Refusal> {
    let fixed = [runtime_root.join("workers"), runtime_root.join("receipts")];
    for directory in fixed.iter().chain(worker_dirs) {
        create_private_dir(directory)?;
    }
    restrict_directory(runtime_root).or_refuse("state_write_failed", Exit::FAILURE, || {
        format!("cannot set the mode of {}", runtime_root.display())
    })
}

/// The real path of `path`, in the form `git rev-parse --show-toplevel` and Bazel print it. The error names the path.
///
/// [`fscopy::resolve_links`] follows every link and junction, and on Windows it removes the verbatim prefix that the
/// system answers, so `\\?\C:\repo` is `C:\repo` and `\\?\UNC\host\share` is `\\host\share`. Neither tool prints
/// `\`, so on Windows the separators then become `/`, which gives `C:/repo` and `//host/share`. Then a path from here
/// compares equal to a path from them.
///
/// The viewer's paths are the other form: discovery in `avl-trace` names a bundle by the text of
/// [`fscopy::resolve_links`] of its zip, so a path that becomes a bundle id goes through that function and not this one.
pub fn real_path(path: &Path) -> io::Result<PathBuf> {
    let resolved = fscopy::resolve_links(path)?;
    #[cfg(windows)]
    let resolved = match resolved.to_str() {
        Some(text) => PathBuf::from(portable_form(text)),
        None => resolved,
    };
    Ok(resolved)
}

/// The Windows path `git` and Bazel print for a verbatim path. A verbatim path of another kind, a volume GUID
/// for example, has no such form and is kept as it is.
#[cfg(any(windows, test))]
fn portable_form(path: &str) -> String {
    if let Some(share) = path.strip_prefix(r"\\?\UNC\") {
        return format!("//{}", share.replace('\\', "/"));
    }
    match path.strip_prefix(r"\\?\") {
        Some(drive) if drive.len() >= 3 && drive.as_bytes()[0].is_ascii_alphabetic() && drive[1..].starts_with(":\\") => {
            drive.replace('\\', "/")
        }
        Some(_) => path.to_owned(),
        None => path.replace('\\', "/"),
    }
}

/// Compares two secrets without leaking which byte differed, or how long the real one is.
///
/// Hashed first, then compared without an early exit. The hash is what makes the comparison constant-time over
/// inputs of different lengths as well: a comparison of the raw strings returns early on a length mismatch, which
/// would leak the length of the real token to a caller that can time it. The token this guards is the UI-test
/// bridge's, which a guest process can present.
pub fn secret_matches(left: &str, right: &str) -> bool {
    let left = Sha256::digest(left.as_bytes());
    let right = Sha256::digest(right.as_bytes());
    left.iter()
        .zip(right.iter())
        .fold(0u8, |difference, (left, right)| difference | (left ^ right))
        == 0
}
