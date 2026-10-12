//! The runfiles of a runtime descriptor on the host, and where a worker opens them.
//!
//! On a macOS or a Linux host Bazel builds `<descriptor>.runfiles`, a tree of links into the checkout and the output
//! root. The host reads a runfile under that tree, and the guest opens the same tree through a share at its
//! [`GuestPaths`] path.
//!
//! On a Windows host Bazel writes the MANIFEST of the descriptor and no tree. The host reads a runfile at its
//! MANIFEST target. The guest agent builds the tree itself with the `runfiles-tree` verb
//! ([`Guest::ensure_runfiles_tree`](crate::guest::Guest::ensure_runfiles_tree)), under
//! [`guest_runfiles_destination`], at `<destination>/<digest>`. The digest is a function of the MANIFEST bytes and
//! the path table, so the host names that root before the guest has built it.
//!
//! The choice is made on what is on disk, not on the host OS: a host that has a tree uses it, and a host that has
//! only a MANIFEST gets the guest tree.

use std::path::{Path, PathBuf};

use avl_base::{Config, Exit, Refusal};
use avl_wire::runfiles::{RunfilesManifest, tree_digest};
use avl_wire::runtime::runfiles_root;

use crate::guest::guest_join;
use crate::paths::GuestPaths;

#[cfg(test)]
mod tests;

/// Where the runfiles of one descriptor are on the host.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum HostRunfiles {
    /// The tree Bazel built, `<descriptor>.runfiles`.
    Tree(PathBuf),
    /// The MANIFEST Bazel wrote in place of a tree ([`manifest_paths`]), with its bytes and its lines.
    Manifest {
        path: PathBuf,
        bytes: Vec<u8>,
        manifest: RunfilesManifest,
    },
}

impl HostRunfiles {
    /// The runfiles of `descriptor`: the tree when Bazel built one, else its MANIFEST, read now.
    ///
    /// With neither, the answer is the tree, so the stamp refuses the first input it cannot find by name.
    pub fn of(descriptor: &Path) -> Result<Self, Refusal> {
        let root = runfiles_root(descriptor);
        if holds_a_tree(&root) {
            return Ok(Self::Tree(root));
        }
        let Some(path) = manifest_paths(descriptor).into_iter().find(|path| path.is_file()) else {
            return Ok(Self::Tree(root));
        };
        let unreadable = |cause: String| {
            Refusal::new(
                "runfiles_manifest_unreadable",
                Exit::SOFTWARE,
                format!("cannot read the runfiles MANIFEST {}: {cause}", path.display()),
            )
        };
        let bytes = std::fs::read(&path).map_err(|error| unreadable(error.to_string()))?;
        let text = std::str::from_utf8(&bytes).map_err(|error| unreadable(error.to_string()))?;
        let manifest = RunfilesManifest::parse(text).map_err(|error| unreadable(error.to_string()))?;
        Ok(Self::Manifest { path, bytes, manifest })
    }

    /// Whether the runfiles of `descriptor` are there: a tree, or a MANIFEST in place of one.
    pub fn present(descriptor: &Path) -> bool {
        holds_a_tree(&runfiles_root(descriptor)) || manifest_paths(descriptor).iter().any(|path| path.is_file())
    }

    /// The host file of the runfile at `logical_path`, or `None` when the MANIFEST has no line for it or names an
    /// empty file. A path under a tree is answered whether or not a file is there; the caller checks the file.
    pub fn host_path(&self, logical_path: &str) -> Option<PathBuf> {
        match self {
            Self::Tree(root) => Some(root.join(logical_path)),
            Self::Manifest { manifest, .. } => manifest
                .resolve(logical_path)
                .filter(|target| !target.is_empty())
                .map(PathBuf::from),
        }
    }

    /// The tree or the MANIFEST, for a message.
    pub fn location(&self) -> &Path {
        match self {
            Self::Tree(root) => root,
            Self::Manifest { path, .. } => path,
        }
    }

    /// Where the worker opens the runfiles root: the tree through its share, or the tree the guest agent builds
    /// from the MANIFEST under [`guest_runfiles_destination`].
    pub fn guest_root(&self, settings: &Config) -> Result<String, Refusal> {
        let paths = GuestPaths::of(settings)?;
        match self {
            Self::Tree(root) => paths.to_guest(root),
            Self::Manifest { bytes, .. } => Ok(guest_join(&guest_runfiles_destination(settings), &tree_digest(bytes, paths.map()))),
        }
    }
}

/// Where Bazel writes the MANIFEST of an executable, in the order they are read: `<descriptor>.runfiles_manifest`
/// beside it, and `MANIFEST` inside `<descriptor>.runfiles`. The descriptor rule is executable, so Bazel writes
/// both on a host that builds the tree. Which ones a Windows host writes is not pinned here, so both are read.
pub fn manifest_paths(descriptor: &Path) -> [PathBuf; 2] {
    let mut beside = descriptor.as_os_str().to_owned();
    beside.push(".runfiles_manifest");
    [PathBuf::from(beside), runfiles_root(descriptor).join("MANIFEST")]
}

/// Whether `root` holds a runfiles tree: an entry other than the two files Bazel writes even where it builds no
/// tree, `MANIFEST` and `_repo_mapping`.
fn holds_a_tree(root: &Path) -> bool {
    std::fs::read_dir(root).is_ok_and(|entries| {
        entries.filter_map(Result::ok).any(|entry| {
            let name = entry.file_name();
            name != "MANIFEST" && name != "_repo_mapping"
        })
    })
}

/// The guest directory that holds the runfiles trees the guest agent builds: `<vmData>/runfiles`.
pub fn guest_runfiles_destination(settings: &Config) -> String {
    guest_join(&settings.vm_data, "runfiles")
}
