//! Private files and directories: what only the invoking user may open, and the check that refuses anything else.
//!
//! One seam for every private write and every private read of the controller: the lock files, the captured
//! output, the pulled artifacts, the state directories, and the receipts the workers read back. A caller asks for
//! "private" and never names a mode or an access list, so the two platform forms stay in this module.
//!
//! - Unix: a file is created at mode 0600 and a directory at mode 0700. A read opens with `O_NOFOLLOW`, and the
//!   check compares the owner uid and the permission word of the open descriptor.
//! - Windows: a file or a directory is created with a protected access list. The list grants full access to the
//!   invoking user, to SYSTEM and to the Administrators group, and to nobody else. A directory's entries are
//!   inheritable, so a file created in it later gets the same list. A read opens the reparse point itself and
//!   refuses it, and the check compares the owner SID and the access list of the open handle.
//!
//! SYSTEM and Administrators are in the Windows list on purpose. They have the part root has on Unix: they can take
//! ownership of any file, so a list without them protects nothing more. Without them, a backup, an antivirus scan
//! or an elevated repair would fail on the controller's state. The check accepts the same set, so a file under the
//! user profile, whose inherited list is exactly that set, is private too.

use std::fs::{File, Metadata};
use std::io;
use std::path::Path;

#[cfg(unix)]
mod unix;
#[cfg(unix)]
use unix as imp;
#[cfg(windows)]
mod windows;
#[cfg(windows)]
use windows as imp;

#[cfg(test)]
mod tests;

/// How [`open_private_file`] opens a file. A file it creates is private; an existing file keeps its protection.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PrivateOpen {
    /// Write only, and a new file: an existing one, a link included, is refused with `AlreadyExists`.
    CreateNew,
    /// Read and write, created when missing, never truncated.
    ReadWrite,
    /// Write only, created when missing, emptied when present.
    Truncate,
    /// Append only, created when missing.
    Append,
}

/// The rule a private receipt breaks when [`Protection::is_exactly`] refuses it, for a refusal message.
#[cfg(unix)]
pub const PRIVATE_FILE_RULE: &str = "mode must be exactly 0600";
/// The rule a private receipt breaks when [`Protection::is_exactly`] refuses it, for a refusal message.
#[cfg(windows)]
pub const PRIVATE_FILE_RULE: &str = "access list must grant only the current user, SYSTEM and Administrators";

/// Opens a file as `how` says, and creates it private when it is missing.
///
/// A creation is private whatever the umask or the parent directory's inherited access list says: on Unix the
/// mode is asked for at the `open`, and on Windows the access list is part of the creation.
pub fn open_private_file(path: &Path, how: PrivateOpen) -> io::Result<File> {
    imp::open_private_file(path, how)
}

/// Creates one directory, private. The parent must exist, and an existing directory is `AlreadyExists`.
pub fn create_private_directory(path: &Path) -> io::Result<()> {
    imp::create_private_directory(path)
}

/// Makes an existing file private: mode 0600 on Unix, the private access list on Windows.
pub fn restrict_file(path: &Path) -> io::Result<()> {
    imp::restrict_file(path)
}

/// Makes an existing directory private: mode 0700 on Unix, the private access list on Windows. On Windows the new
/// list also reaches every entry below the directory that inherits.
pub fn restrict_directory(path: &Path) -> io::Result<()> {
    imp::restrict_directory(path)
}

/// Why [`open_no_follow`] did not answer a file.
#[derive(Debug)]
pub enum NoFollowError {
    Missing,
    /// The path is a symlink, or on Windows any reparse point: a junction, a symlink, or a placeholder.
    Link,
    Failed(io::Error),
}

/// Opens `path` for reading without following a link in its last component.
///
/// Unix: `O_NOFOLLOW`, and `ELOOP` is how the kernel says the path was a symlink. Windows: the reparse point is
/// opened itself, and a handle whose attributes carry `FILE_ATTRIBUTE_REPARSE_POINT` is refused.
pub fn open_no_follow(path: &Path) -> Result<File, NoFollowError> {
    imp::open_no_follow(path)
}

/// Who owns an open file or directory, and who else can open it.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Protection {
    owned_by_caller: bool,
    access: imp::Access,
}

impl Protection {
    /// Unix: the owner uid is the caller's. Windows: the owner SID is the caller's user, or the token's default
    /// owner, which is the Administrators group for an elevated process.
    pub const fn owned_by_caller(&self) -> bool {
        self.owned_by_caller
    }

    /// Unix: no group or other bit is set. Windows: the access list grants nobody beyond the caller, SYSTEM and
    /// Administrators.
    pub const fn is_private(&self) -> bool {
        self.access.is_private()
    }

    /// Unix: the permission word, setuid, setgid and sticky included, is exactly `mode`. A mode-4600 file is not a
    /// mode-0600 one. Windows: `mode` has no group or other bit, and the file is private. An access list has no
    /// mode word, so every private `mode` is the same list.
    pub const fn is_exactly(&self, mode: u32) -> bool {
        self.access.is_exactly(mode)
    }
}

/// The protection of an open file. The metadata is the one of the same handle.
///
/// From the open handle, never from the path again: a lookup by name would see what a substitution put there.
pub fn protection(file: &File, metadata: &Metadata) -> io::Result<Protection> {
    imp::protection(file, metadata)
}

/// The protection of the directory at `path`, or `None` when the path is not a directory itself: a link to a
/// directory, or a file. A link is never followed, so a check cannot pass for the target of a planted link.
pub fn directory_protection(path: &Path) -> io::Result<Option<Protection>> {
    imp::directory_protection(path)
}
