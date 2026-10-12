//! Portable file publication: a file appears whole or not at all.
//!
//! There are two ways to publish, and which one a caller wants is decided by what the file *is*. Current state (a
//! lease receipt, a worker's state) is published with [`write_atomically`], which replaces. Evidence about
//! something that already happened (a run report, a pulled artifact) is published with [`write_exclusively`] or
//! [`publish`], which refuse to replace: two of them collide on one path only when two runs claimed the same
//! identity, and replacing would silently lose one run's account.
//!
//! Both write a temporary beside the destination (a rename or a link is only atomic within one filesystem, and
//! the system temporary directory is routinely another one), `fsync` it, and then move it into place. The
//! `fsync` is not ceremony: a receipt read back after a host crash must not be a zero-length file that parses as
//! "no lease", and a report must not parse as a run with no failures. A temporary never survives a call: after a
//! refusal it would be litter beside real evidence.
//!
//! The move is a std call on the path, `fs::rename` or `fs::hard_link`, and never a `tempfile` persist: std gives a
//! long Windows path the `\\?\` prefix, and `tempfile` does not, so its persist fails past `MAX_PATH`.
//!
//! The guest-path gate (`pull` destinations, private directories) lives in `avl-host-sys`, for Unix and Windows.

use std::fs;
use std::io::{self, Write};
use std::path::{Path, PathBuf};

use tempfile::NamedTempFile;

use crate::refusal::{Exit, OrRefuse, Refusal};

#[cfg(test)]
mod tests;

/// Why a no-clobber publish failed. The code and the message of the refusal belong to the caller: `avl-report`
/// refuses a second report as `report_destination_exists`, `pull` a second artifact as
/// `pull_destination_exists`, and both are contracts an agent branches on.
#[derive(Debug, thiserror::Error)]
pub enum PublishError {
    /// The destination was already there; it is untouched.
    #[error("publish destination exists: {}", .0.display())]
    DestinationExists(PathBuf),
    /// Anything else, with the step that failed.
    #[error("{context}: {source}")]
    Io {
        context: String,
        #[source]
        source: io::Error,
    },
}

impl PublishError {
    fn io(context: impl Into<String>) -> impl FnOnce(io::Error) -> Self {
        let context = context.into();
        move |source| Self::Io { context, source }
    }
}

/// Publishes a file's whole content or none of it, replacing what was there, at `mode` (ignored off unix).
///
/// The mode is applied to the written file rather than asked for at `open`, because the open mode is masked by
/// `umask` and a 0600 receipt under a permissive umask is a 0644 one.
pub fn write_atomically(path: &Path, content: &[u8], mode: u32) -> Result<(), Refusal> {
    let temporary = write_temporary(path, content, mode)?;
    move_into_place(temporary, path).or_refuse("state_write_failed", Exit::FAILURE, || format!("cannot publish {}", path.display()))
}

/// Makes a finished temporary the destination, replacing what was there. The temporary is removed when the move
/// fails.
pub fn move_into_place(temporary: NamedTempFile, destination: &Path) -> io::Result<()> {
    let mut temporary = temporary.into_temp_path();
    fs::rename(&temporary, destination)?;
    temporary.disable_cleanup(true);
    Ok(())
}

/// Writes only when the content differs, and answers whether it wrote.
///
/// The answer is the point, not the saved write: `remount` and the parity layout are skipped when their inputs
/// did not move, which was worth 16.1 s of a 95.7 s warm restart. The mode is still applied when the content
/// matches, so a receipt whose permissions drifted is repaired without being rewritten.
pub fn write_atomically_if_changed(path: &Path, content: &[u8], mode: u32) -> Result<bool, Refusal> {
    match fs::read(path) {
        Ok(existing) if existing == content => {
            set_mode(path, mode).or_refuse("state_write_failed", Exit::FAILURE, || {
                format!("cannot set the mode of {}", path.display())
            })?;
            return Ok(false);
        }
        Ok(_) => {}
        Err(error) if error.kind() == io::ErrorKind::NotFound => {}
        Err(error) => {
            return Err(Refusal::new(
                "state_read_failed",
                Exit::FAILURE,
                format!("cannot read {}: {error}", path.display()),
            ));
        }
    }
    write_atomically(path, content, mode)?;
    Ok(true)
}

fn write_temporary(path: &Path, content: &[u8], mode: u32) -> Result<NamedTempFile, Refusal> {
    let mut temporary = private_temporary(parent_of(path), &file_name(path)).or_refuse("state_write_failed", Exit::FAILURE, || {
        format!("cannot create a temporary beside {}", path.display())
    })?;
    let written = temporary
        .write_all(content)
        .and_then(|()| temporary.as_file().sync_all())
        .and_then(|()| set_mode(temporary.path(), mode));
    written.or_refuse("state_write_failed", Exit::FAILURE, || {
        format!("cannot write {}", temporary.path().display())
    })?;
    Ok(temporary)
}

/// Opens a private temporary in `directory`, named `.<name>-XXXXXX.tmp` so a temporary that outlived a crashed
/// process says which answer it was going to be. Created exclusively at 0600 (on unix), so two concurrent
/// iterations on one worker can never write into one another's bytes. Dropped, it is removed.
pub fn private_temporary(directory: &Path, name: &str) -> io::Result<NamedTempFile> {
    tempfile::Builder::new()
        .prefix(&format!(".{name}-"))
        .suffix(".tmp")
        .tempfile_in(directory)
}

/// Makes a finished temporary the destination, or refuses when the destination exists.
///
/// The no-clobber step is the filesystem's: a hard link fails when its name exists, `link` on Unix and
/// `CreateHardLinkW` on Windows. So the refusal cannot be raced by a caller's own pre-check. The temporary is synced
/// first, and its name is removed on every path: after a refusal it would be litter.
///
/// The move works on the directory entry, not on the handle, so a symlink planted where the temporary was would
/// itself be published. The entry is therefore checked first to still be the regular file this handle holds (the
/// same inode on unix; not a symlink on Windows, which has no stable inode to compare), and a mismatch is refused
/// as an `Io` error rather than followed - it is not a caller asking to replace an answer.
pub fn publish(temporary: NamedTempFile, destination: &Path) -> Result<(), PublishError> {
    ensure_entry_is_the_held_file(&temporary).map_err(PublishError::io(format!(
        "the finished temporary {} is not the file this publish wrote",
        temporary.path().display()
    )))?;
    temporary
        .as_file()
        .sync_all()
        .map_err(PublishError::io(format!("cannot fsync {}", temporary.path().display())))?;
    // The link gives the file its second name, and the drop of `temporary` removes the first one.
    let temporary = temporary.into_temp_path();
    fs::hard_link(&temporary, destination).map_err(|error| {
        if error.kind() == io::ErrorKind::AlreadyExists {
            PublishError::DestinationExists(destination.to_owned())
        } else {
            PublishError::Io {
                context: format!("cannot move {} into place as {}", temporary.display(), destination.display()),
                source: error,
            }
        }
    })
}

/// Publishes content already in memory where it cannot overwrite an earlier answer, at `mode` (ignored off unix).
pub fn write_exclusively(path: &Path, content: &[u8], mode: u32) -> Result<(), PublishError> {
    let mut temporary = private_temporary(parent_of(path), &file_name(path))
        .map_err(PublishError::io(format!("cannot create a temporary beside {}", path.display())))?;
    temporary
        .write_all(content)
        .and_then(|()| set_mode(temporary.path(), mode))
        .map_err(PublishError::io(format!("cannot write {}", temporary.path().display())))?;
    publish(temporary, path)
}

/// Creates a directory, and every missing parent, at 0700 (on unix), and makes the leaf 0700 whatever the umask
/// said. It holds mode-0600 documents or evidence, and a world-readable directory around them is the difference
/// between one wrong permission and two. A missing parent is created at 0700 masked by the umask, so another local
/// user cannot list the run ids under it either. An existing directory is kept, and its leaf mode is repaired.
///
/// A creator, not a gate: `avl-host-sys`'s `ensure_private_directory` is the check that refuses a directory
/// someone else made.
pub fn create_private_dir(directory: &Path) -> Result<(), Refusal> {
    let mut builder = fs::DirBuilder::new();
    builder.recursive(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::DirBuilderExt;
        builder.mode(0o700);
    }
    builder.create(directory).or_refuse("state_write_failed", Exit::FAILURE, || {
        format!("cannot create {}", directory.display())
    })?;
    set_mode(directory, 0o700).or_refuse("state_write_failed", Exit::FAILURE, || {
        format!("cannot set the mode of {}", directory.display())
    })
}

fn ensure_entry_is_the_held_file(temporary: &NamedTempFile) -> io::Result<()> {
    let entry = fs::symlink_metadata(temporary.path())?;
    if !entry.file_type().is_file() {
        return Err(io::Error::other("the entry is not a regular file"));
    }
    #[cfg(unix)]
    {
        use std::os::unix::fs::MetadataExt;
        let held = temporary.as_file().metadata()?;
        if (held.dev(), held.ino()) != (entry.dev(), entry.ino()) {
            return Err(io::Error::other("the entry was replaced"));
        }
    }
    Ok(())
}

fn parent_of(path: &Path) -> &Path {
    match path.parent() {
        Some(parent) if !parent.as_os_str().is_empty() => parent,
        _ => Path::new("."),
    }
}

fn file_name(path: &Path) -> String {
    path.file_name()
        .map_or_else(|| "file".to_owned(), |name| name.to_string_lossy().into_owned())
}

#[cfg(unix)]
fn set_mode(path: &Path, mode: u32) -> io::Result<()> {
    use std::os::unix::fs::PermissionsExt;
    fs::set_permissions(path, fs::Permissions::from_mode(mode))
}

/// Windows has no mode bits to set: permissions there are an ACL, which a mode cannot express.
#[cfg(not(unix))]
#[expect(clippy::unnecessary_wraps, reason = "the signature of the Unix set_mode")]
const fn set_mode(_path: &Path, _mode: u32) -> io::Result<()> {
    Ok(())
}
