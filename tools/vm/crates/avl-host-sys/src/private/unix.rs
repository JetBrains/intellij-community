//! The Unix form of the private seam: mode 0600 and 0700, `O_NOFOLLOW`, and the uid and the mode word.

use std::fs::{self, DirBuilder, File, Metadata, OpenOptions, Permissions};
use std::io;
use std::os::unix::fs::{DirBuilderExt, MetadataExt, OpenOptionsExt, PermissionsExt};
use std::path::Path;

use super::{NoFollowError, PrivateOpen, Protection};

/// The permission word of a file or a directory, masked to `0o7777`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) struct Access {
    mode: u32,
}

impl Access {
    #[expect(
        clippy::verbose_bit_mask,
        reason = "`mode & 0o077 == 0` reads as no group or other bits; trailing_zeros() >= 6 does not"
    )]
    pub(super) const fn is_private(self) -> bool {
        self.mode & 0o077 == 0
    }

    pub(super) const fn is_exactly(self, mode: u32) -> bool {
        self.mode == mode
    }
}

pub(super) fn open_private_file(path: &Path, how: PrivateOpen) -> io::Result<File> {
    let mut options = OpenOptions::new();
    match how {
        PrivateOpen::CreateNew => options.write(true).create_new(true),
        PrivateOpen::ReadWrite => options.read(true).write(true).create(true).truncate(false),
        PrivateOpen::Truncate => options.write(true).create(true).truncate(true),
        PrivateOpen::Append => options.create(true).append(true),
    };
    options.mode(0o600).open(path)
}

pub(super) fn create_private_directory(path: &Path) -> io::Result<()> {
    DirBuilder::new().mode(0o700).create(path)
}

pub(super) fn restrict_file(path: &Path) -> io::Result<()> {
    fs::set_permissions(path, Permissions::from_mode(0o600))
}

pub(super) fn restrict_directory(path: &Path) -> io::Result<()> {
    fs::set_permissions(path, Permissions::from_mode(0o700))
}

pub(super) fn open_no_follow(path: &Path) -> Result<File, NoFollowError> {
    OpenOptions::new()
        .read(true)
        .custom_flags(libc::O_NOFOLLOW)
        .open(path)
        .map_err(|error| match error.kind() {
            io::ErrorKind::NotFound => NoFollowError::Missing,
            _ if error.raw_os_error() == Some(libc::ELOOP) => NoFollowError::Link,
            _ => NoFollowError::Failed(error),
        })
}

#[expect(
    clippy::unnecessary_wraps,
    reason = "the signature of the Windows protection, which reads the access list"
)]
pub(super) fn protection(_file: &File, metadata: &Metadata) -> io::Result<Protection> {
    Ok(of_metadata(metadata))
}

/// `lstat` rather than `stat`, or a symlink to a private directory would pass as that directory.
pub(super) fn directory_protection(path: &Path) -> io::Result<Option<Protection>> {
    let metadata = fs::symlink_metadata(path)?;
    Ok(metadata.file_type().is_dir().then(|| of_metadata(&metadata)))
}

fn of_metadata(metadata: &Metadata) -> Protection {
    Protection {
        owned_by_caller: metadata.uid() == nix::unistd::getuid().as_raw(),
        access: Access {
            mode: metadata.mode() & 0o7777,
        },
    }
}
