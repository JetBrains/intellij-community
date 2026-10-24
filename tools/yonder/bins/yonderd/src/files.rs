//! The files and the directories of the agent: each directory has the mode 0700 and each file the mode 0600. The modes
//! are set after each create, so the umask does not change them.

use std::fs::{self, File, OpenOptions, Permissions};
use std::io::{self, Write};
use std::os::fd::AsRawFd;
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};

/// The mode of each directory of the agent.
pub(crate) const DIR_MODE: u32 = 0o700;

/// The mode of each file of the agent.
pub(crate) const FILE_MODE: u32 = 0o600;

/// Makes `dir` and its parents, and gives `dir` the mode 0700.
pub(crate) fn private_dir(dir: &Path) -> io::Result<()> {
    fs::create_dir_all(dir)?;
    fs::set_permissions(dir, Permissions::from_mode(DIR_MODE))
}

/// The count of the temporary files of this process. `serve` writes from many threads, so the pid alone does not
/// make a name unique.
static TEMPORARIES: AtomicU64 = AtomicU64::new(0);

/// Writes `path` through a temporary file beside it, so a reader never sees a part. The file has the mode 0600. Two
/// threads that write the same path at once each write a temporary file of their own, and the last rename wins.
pub(crate) fn write_private(path: &Path, bytes: &[u8]) -> io::Result<()> {
    let temp = PathBuf::from(format!(
        "{}.tmp-{}-{}",
        path.display(),
        std::process::id(),
        TEMPORARIES.fetch_add(1, Ordering::Relaxed)
    ));
    let written = OpenOptions::new()
        .write(true)
        .create(true)
        .truncate(true)
        .open(&temp)
        .and_then(|mut file| {
            file.set_permissions(Permissions::from_mode(FILE_MODE))?;
            file.write_all(bytes)
        })
        .and_then(|()| fs::rename(&temp, path));
    written.inspect_err(|_| {
        let _ = fs::remove_file(&temp);
    })
}

/// Opens `path` for appends, and makes it with the mode 0600 when it is missing.
pub(crate) fn append_private(path: &Path) -> io::Result<File> {
    let file = OpenOptions::new().create(true).append(true).open(path)?;
    file.set_permissions(Permissions::from_mode(FILE_MODE))?;
    Ok(file)
}

/// Opens `path` for writes without a truncate, and makes it with the mode 0600 when it is missing.
pub(crate) fn open_private(path: &Path) -> io::Result<File> {
    let file = OpenOptions::new().create(true).truncate(false).write(true).open(path)?;
    file.set_permissions(Permissions::from_mode(FILE_MODE))?;
    Ok(file)
}

/// Takes an exclusive flock of `file` when no other open file holds it. Answers whether it took the lock.
pub(crate) fn try_flock(file: &File) -> io::Result<bool> {
    try_flock_as(file, libc::LOCK_EX)
}

/// Takes a shared flock of `file` when no other open file holds an exclusive one. Answers whether it took the lock.
/// Two shared takers never refuse each other, so a probe of a lock cannot make another probe fail.
pub(crate) fn try_flock_shared(file: &File) -> io::Result<bool> {
    try_flock_as(file, libc::LOCK_SH)
}

fn try_flock_as(file: &File, kind: libc::c_int) -> io::Result<bool> {
    // SAFETY: the descriptor belongs to `file`, which lives for the call.
    if unsafe { libc::flock(file.as_raw_fd(), kind | libc::LOCK_NB) } == 0 {
        return Ok(true);
    }
    let error = io::Error::last_os_error();
    if error.kind() == io::ErrorKind::WouldBlock {
        Ok(false)
    } else {
        Err(error)
    }
}

#[cfg(test)]
mod tests;
