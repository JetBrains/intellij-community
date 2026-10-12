//! The read-only share set a worker reads through. It sits below both backends and below the guest setup: each
//! backend renders it its own way, and the guest mounts what was declared.

use std::path::PathBuf;

use avl_base::{Config, Refusal};

#[cfg(test)]
mod tests;

/// The only mode this controller ever declares. A worker has no business writing to the host checkout: the whole
/// point of the parity layout is that the guest reads outputs the host built.
pub const SHARE_MODE: &str = "ro";

/// One read-only host directory a worker reads through.
///
/// Each field is part of a grammar somewhere: the name is what names the directory under the guest's mount point
/// (both backends), the path is the host side, and the mode is `prlctl --mode`'s value and Tart's `--dir` options
/// field at once. They happen to spell "read-only" the same way, which is why one type serves both.
///
/// This is the **only** declaration of a share. Something composes a share and a backend *renders* it - `--dir=`
/// fragments on a `tart run` argv, or `prlctl set --shf-host-add` against a live VM - so it lives in the lower
/// layer, and guest provisioning depends on it rather than the reverse.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct SharedFolder {
    pub name: String,
    pub path: PathBuf,
    /// [`SHARE_MODE`] for every share this controller declares; a field because it is compared against what a VM
    /// currently has, which may have drifted to `rw`.
    pub mode: &'static str,
}

/// The two shares a worker gets, the repository's and the Bazel output root's, in the order both backends declare
/// them.
///
/// Refused before the host paths are resolved rather than answering empty paths, because an empty path is the one
/// failure that is invisible on the host: `tart run` accepts `--dir=name::ro`, the VM boots, and every guest probe
/// afterwards blames the guest for a share that was never pointed anywhere.
pub fn shares(settings: &Config) -> Result<[SharedFolder; 2], Refusal> {
    let repo = settings.host_repo()?;
    let bazel = settings.host_bazel_user_root()?;
    Ok([
        SharedFolder {
            name: settings.repo_share_name.clone(),
            path: repo.to_owned(),
            mode: SHARE_MODE,
        },
        SharedFolder {
            name: settings.bazel_share_name.clone(),
            path: bazel.to_owned(),
            mode: SHARE_MODE,
        },
    ])
}
