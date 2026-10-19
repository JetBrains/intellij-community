//! The file helpers that several verbs share: the type checks of a path, the copy of a directory, and the staging
//! tree that a verb renames into place.

use std::fs;
use std::io;
use std::path::{Path, PathBuf};

#[cfg(test)]
mod tests;

/// Whether `path` is a file. A symbolic link counts as its target.
pub(crate) fn is_file(path: &Path) -> bool {
    fs::metadata(path).is_ok_and(|info| info.is_file())
}

/// Whether `path` is a directory. A symbolic link counts as its target.
pub(crate) fn is_directory(path: &Path) -> bool {
    fs::metadata(path).is_ok_and(|info| info.is_dir())
}

/// Copies the directory `source` to `destination`, which must not exist: a file by its content and its permission
/// bits, a directory by recursion, and a symbolic link as the link it is. A link at `source` itself is followed.
pub(crate) fn copy_tree(source: &Path, destination: &Path) -> io::Result<()> {
    fs::create_dir(destination)?;
    for entry in fs::read_dir(source)? {
        let entry = entry?;
        let from = entry.path();
        let to = destination.join(entry.file_name());
        let metadata = fs::symlink_metadata(&from)?;
        if metadata.is_dir() {
            copy_tree(&from, &to)?;
        } else if metadata.is_symlink() {
            std::os::unix::fs::symlink(fs::read_link(&from)?, &to)?;
        } else {
            fs::copy(&from, &to)?;
        }
    }
    Ok(())
}

/// Removes a tree that may not exist. The staging tree uses it on its own path, where a failed removal shows up in the
/// error of the next step.
fn remove_tree(path: &Path) {
    let _ = fs::remove_dir_all(path);
}

/// The prefix of the name of a staging tree. The whole name is `.stage-<name>-<pid>`.
const STAGING_PREFIX: &str = ".stage-";

/// The staging tree of one verb, `<parent>/.stage-<name>-<pid>`. The drop removes it, unless [`Self::publish`] renamed
/// it into place.
///
/// Every refusal between the first write and the rename returns early. So the removal belongs to the drop, and not to
/// each return.
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
