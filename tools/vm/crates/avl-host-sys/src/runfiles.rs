//! The runfiles of a runtime descriptor on the host, and where a worker opens them.
//!
//! Bazel writes the MANIFEST of the descriptor on every host, and the host reads a runfile at its MANIFEST target. The
//! lane build builds no tree of links (`--build_runfile_links` is off), so a `<descriptor>.runfiles` tree that is on
//! disk is one of an earlier build and can be stale. It is read only when no MANIFEST is there.
//!
//! The guest agent builds its own tree from the MANIFEST on every host, with the `runfiles-tree` verb
//! ([`Guest::ensure_runfiles_tree`](crate::guest::Guest::ensure_runfiles_tree)), under
//! [`guest_runfiles_destination`], at `<destination>/<digest>`. No share holds the checkout, so a runfile whose host
//! file lies in the checkout is a staged runfile: the host sends its bytes, and the tree holds a copy
//! ([`HostRunfiles::guest_tree`]). The digest is a function of the request, so the host names the root before the
//! guest has built it.

use std::fs::File;
use std::io::Read as _;
use std::path::{Path, PathBuf};

use avl_base::config::HostOs;
use avl_base::{Config, Exit, Refusal};
use avl_wire::path_map::{PathMap, is_absolute_host_path};
use avl_wire::runfiles::{ManifestEntry, RunfilesManifest, RunfilesTreeRequest, SCHEMA_VERSION, StagedRunfile, tree_digest};
use avl_wire::runtime::runfiles_root;
use sha2::{Digest as _, Sha256};

use crate::guest::guest_join;
use crate::paths::{GuestPaths, lies_below};

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
    /// The runfiles of `descriptor`: its MANIFEST, read now, on every host. A tree beside it is ignored, because it can
    /// be one of an earlier build.
    ///
    /// With no MANIFEST, the answer is the tree, so the stamp refuses the first input it cannot find by name.
    pub fn of(descriptor: &Path) -> Result<Self, Refusal> {
        Self::of_on(descriptor, HostOs::CURRENT)
    }

    /// [`HostRunfiles::of`] as a controller on `host` reads it. On Windows every absolute target is resolved through
    /// its junctions, because Bazel's external repositories there are junctions into the repository cache, which the
    /// guest's mount cannot read; the file under the cache is one it can.
    pub fn of_on(descriptor: &Path, host: HostOs) -> Result<Self, Refusal> {
        let Some(path) = manifest_paths(descriptor).into_iter().find(|path| path.is_file()) else {
            return Ok(Self::Tree(runfiles_root(descriptor)));
        };
        let mut manifest = read_manifest(&path)?;
        if host == HostOs::Windows {
            resolve_junctions(&mut manifest);
        }
        // The bytes the guest gets and both sides hash: the rendered entries, so a resolved target is in them.
        let bytes = manifest.render().into_bytes();
        Ok(Self::Manifest { path, bytes, manifest })
    }

    /// Whether the runfiles of `descriptor` are there: a tree, or a MANIFEST in place of one.
    pub fn present(descriptor: &Path) -> bool {
        holds_a_tree(&runfiles_root(descriptor)) || manifest_paths(descriptor).iter().any(|path| path.is_file())
    }

    /// The host file of the runfile at `logical_path`, or `None` when the MANIFEST has no line for it, names an empty
    /// file, or follows a symlink runfile out of the MANIFEST. A path under a tree is answered whether or not a file
    /// is there; the caller checks the file.
    pub fn host_path(&self, logical_path: &str) -> Option<PathBuf> {
        match self {
            Self::Tree(root) => Some(root.join(logical_path)),
            Self::Manifest { manifest, .. } => manifest
                .host_target(logical_path)
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

    /// The MANIFEST of these runfiles: the one this value read, or the one Bazel wrote beside its tree.
    fn manifest(&self) -> Result<RunfilesManifest, Refusal> {
        let root = match self {
            Self::Manifest { manifest, .. } => return Ok(manifest.clone()),
            Self::Tree(root) => root,
        };
        let mut beside = root.as_os_str().to_owned();
        beside.push("_manifest");
        let candidates = [root.join("MANIFEST"), PathBuf::from(beside)];
        let Some(path) = candidates.iter().find(|path| path.is_file()) else {
            return Err(Refusal::new(
                "runfiles_manifest_missing",
                Exit::SOFTWARE,
                format!(
                    "the runfiles tree {} has no MANIFEST, and the guest builds its tree from one",
                    root.display()
                ),
            ));
        };
        read_manifest(path)
    }

    /// The tree the guest builds from these runfiles: the request of the `runfiles-tree` verb, its root, and the host
    /// file of each staged runfile.
    ///
    /// A MANIFEST line whose target lies in the checkout, after its links are resolved, becomes a staged runfile: the
    /// line leaves the MANIFEST, and the request names the digest and the length of the file. A directory there becomes
    /// one staged runfile per file below it. Every other line stays as it is. Blocking: it resolves each target and
    /// reads each staged file.
    pub fn guest_tree(&self, inputs: &GuestTreeInputs) -> Result<GuestRunfilesTree, Refusal> {
        let manifest = self.manifest()?;
        let mut kept = Vec::with_capacity(manifest.entries.len());
        let mut staged = Vec::new();
        let mut staged_files = Vec::new();
        for entry in manifest.entries {
            let in_checkout = is_absolute_host_path(&entry.target)
                .then(|| fscopy::resolve_links(Path::new(&entry.target)).unwrap_or_else(|_| PathBuf::from(&entry.target)))
                .filter(|resolved| resolved == &inputs.host_repo || lies_below(&inputs.host_repo, resolved));
            let Some(resolved) = in_checkout else {
                kept.push(entry);
                continue;
            };
            for (path, file) in staged_files_of(&entry, &resolved)? {
                let (sha256, size) = file_identity(&file)?;
                staged.push(StagedRunfile { path, sha256, size });
                staged_files.push(file);
            }
        }
        let request = RunfilesTreeRequest {
            schema_version: SCHEMA_VERSION,
            manifest_text: RunfilesManifest { entries: kept }.render(),
            path_map: inputs.path_map.clone(),
            destination: inputs.destination.clone(),
            staged,
            with_bytes: false,
            copy_package_stores: inputs.host == HostOs::Windows,
        };
        Ok(GuestRunfilesTree {
            root: guest_join(&inputs.destination, &tree_digest(&request)),
            request,
            staged_files,
        })
    }
}

/// What [`HostRunfiles::guest_tree`] needs of the settings, apart from them, so that a blocking task can own it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct GuestTreeInputs {
    /// The checkout, as a real path. A target whose real path lies below it is a staged runfile.
    pub host_repo: PathBuf,
    pub path_map: PathMap,
    pub destination: String,
    pub host: HostOs,
}

impl GuestTreeInputs {
    /// The inputs of this invocation, refused before the host paths are resolved. The checkout is resolved through its
    /// links, as each target is.
    pub fn of(settings: &Config) -> Result<Self, Refusal> {
        let repo = settings.host_repo()?;
        Ok(Self {
            host_repo: fscopy::resolve_links(repo).unwrap_or_else(|_| repo.to_owned()),
            path_map: GuestPaths::of(settings)?.map().clone(),
            destination: guest_runfiles_destination(settings),
            host: HostOs::CURRENT,
        })
    }
}

/// The runfiles tree that one worker builds: the root the guest opens, the request that builds it, and the host file of
/// each staged runfile of the request, in its order.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct GuestRunfilesTree {
    pub root: String,
    pub request: RunfilesTreeRequest,
    pub staged_files: Vec<PathBuf>,
}

/// The staged runfiles of one MANIFEST line in the checkout: the line itself for a file, and each file below a
/// directory, by its path under the runfile, in sorted order.
fn staged_files_of(entry: &ManifestEntry, resolved: &Path) -> Result<Vec<(String, PathBuf)>, Refusal> {
    let unreadable = |path: &Path, error: std::io::Error| {
        Refusal::new(
            "runfiles_staged_unreadable",
            Exit::SOFTWARE,
            format!("cannot read the runfile {} at {}: {error}", entry.path, path.display()),
        )
    };
    let metadata = std::fs::metadata(resolved).map_err(|error| unreadable(resolved, error))?;
    if !metadata.is_dir() {
        return Ok(vec![(entry.path.clone(), resolved.to_owned())]);
    }
    let mut files = Vec::new();
    let mut pending = vec![(entry.path.clone(), resolved.to_owned())];
    while let Some((logical, directory)) = pending.pop() {
        let listing = std::fs::read_dir(&directory).map_err(|error| unreadable(&directory, error))?;
        for item in listing {
            let item = item.map_err(|error| unreadable(&directory, error))?;
            let path = item.path();
            let child = format!("{logical}/{}", item.file_name().to_string_lossy());
            if std::fs::metadata(&path).map_err(|error| unreadable(&path, error))?.is_dir() {
                pending.push((child, path));
            } else {
                files.push((child, path));
            }
        }
    }
    files.sort();
    Ok(files)
}

/// The sha256 and the length of one staged file, as the guest checks them.
fn file_identity(path: &Path) -> Result<(String, u64), Refusal> {
    let unreadable = |error: std::io::Error| {
        Refusal::new(
            "runfiles_staged_unreadable",
            Exit::SOFTWARE,
            format!("cannot read the staged runfile {}: {error}", path.display()),
        )
    };
    let mut file = File::open(path).map_err(unreadable)?;
    let mut hasher = Sha256::new();
    let mut buffer = vec![0; 64 * 1024];
    let mut size = 0_u64;
    loop {
        let read = file.read(&mut buffer).map_err(unreadable)?;
        if read == 0 {
            return Ok((hex::encode(hasher.finalize()), size));
        }
        hasher.update(&buffer[..read]);
        size += u64::try_from(read).unwrap_or(u64::MAX);
    }
}

/// Reads and parses one MANIFEST, refused by name when it cannot be read.
fn read_manifest(path: &Path) -> Result<RunfilesManifest, Refusal> {
    let unreadable = |cause: String| {
        Refusal::new(
            "runfiles_manifest_unreadable",
            Exit::SOFTWARE,
            format!("cannot read the runfiles MANIFEST {}: {cause}", path.display()),
        )
    };
    let bytes = std::fs::read(path).map_err(|error| unreadable(error.to_string()))?;
    let text = std::str::from_utf8(&bytes).map_err(|error| unreadable(error.to_string()))?;
    RunfilesManifest::parse(text).map_err(|error| unreadable(error.to_string()))
}

/// Where Bazel writes the MANIFEST of an executable, in the order they are read: `<descriptor>.runfiles_manifest`
/// beside it, and `MANIFEST` inside `<descriptor>.runfiles`. The descriptor rule is executable, so Bazel writes
/// both on a host that builds the tree. Which ones a Windows host writes is not pinned here, so both are read.
pub fn manifest_paths(descriptor: &Path) -> [PathBuf; 2] {
    let mut beside = descriptor.as_os_str().to_owned();
    beside.push(".runfiles_manifest");
    [PathBuf::from(beside), runfiles_root(descriptor).join("MANIFEST")]
}

/// Replaces each absolute target with the path its junctions and links lead to, as [`fscopy::resolve_links`] reports
/// it, with forward slashes. A target that cannot be resolved stays, so the stamp names it.
fn resolve_junctions(manifest: &mut RunfilesManifest) {
    for entry in &mut manifest.entries {
        if !is_absolute_host_path(&entry.target) {
            continue;
        }
        if let Ok(resolved) = fscopy::resolve_links(Path::new(&entry.target)) {
            entry.target = resolved.to_string_lossy().replace('\\', "/");
        }
    }
}

/// Whether `root` holds a runfiles tree: an entry other than the two files Bazel writes even where it builds no
/// tree, `MANIFEST` and `_repo_mapping`, with something in it. A Windows host gets an empty `_main` directory
/// beside the MANIFEST, and an empty directory is no tree.
fn holds_a_tree(root: &Path) -> bool {
    std::fs::read_dir(root).is_ok_and(|entries| {
        entries.filter_map(Result::ok).any(|entry| {
            let name = entry.file_name();
            if name == "MANIFEST" || name == "_repo_mapping" {
                return false;
            }
            let path = entry.path();
            !path.is_dir() || std::fs::read_dir(&path).is_ok_and(|mut inner| inner.next().is_some())
        })
    })
}

/// The guest directory that holds the runfiles trees the guest agent builds: `<vmData>/runfiles`.
pub fn guest_runfiles_destination(settings: &Config) -> String {
    guest_join(&settings.vm_data, "runfiles")
}
