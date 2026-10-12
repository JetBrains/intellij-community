//! Reading a private receipt without following a symlink, the one read the golden seal, a worker's provenance and a
//! lease receipt share, and the one write of a private host record.
//!
//! Three defences, and each one closes a different substitution. What each one is on a platform is
//! [`avl_host_sys::private`]'s:
//!
//! - no link followed at open, so a symlink planted where the receipt should be is refused rather than followed to
//!   whatever it points at. Unix: `O_NOFOLLOW`. Windows: the reparse point is opened itself and refused.
//! - the owner and the protection read from the *open handle* rather than from the path, so nothing can be swapped
//!   between the check and the read.
//! - the whole protection: on Unix the full permission word, setuid, setgid and sticky included, so a mode-4600
//!   receipt is not a mode-0600 one. On Windows an access list that grants nobody else.
//!
//! What each failure is called is the caller's: the seal and the provenance distinguish four refusals, a lease
//! receipt says one thing about most of them on purpose.

use std::io::{self, Read};
use std::path::Path;

use avl_host_sys::private::{NoFollowError, open_no_follow, protection};

/// Why a private file could not be read.
#[derive(Debug)]
pub(crate) enum SecureReadError {
    Missing,
    Symlink,
    #[cfg_attr(
        windows,
        expect(dead_code, reason = "only the Tart receipts quote the cause, and Tart is not built on Windows")
    )]
    Unreadable(io::Error),
    NotRegular,
    /// The protection is not the one required: on Unix the permission word, masked to `0o7777`.
    WrongMode,
    WrongOwner,
}

/// What a private read checks beyond the file being a regular one.
#[derive(Clone, Copy, Debug)]
pub(crate) struct Requirements {
    /// The exact permission word, `0o600` for every receipt this controller keeps. On Windows every mode without a
    /// group or other bit asks for the private access list.
    pub(crate) mode: u32,
    /// Whether the file must also belong to the invoking user.
    pub(crate) owned_by_caller: bool,
}

/// Opens `path` without following a symlink and answers its bytes, or why it is not a file this controller trusts.
pub(crate) fn read_private_file(path: &Path, requirements: Requirements) -> Result<Vec<u8>, SecureReadError> {
    let mut file = open_no_follow(path).map_err(|error| match error {
        NoFollowError::Missing => SecureReadError::Missing,
        NoFollowError::Link => SecureReadError::Symlink,
        NoFollowError::Failed(error) => SecureReadError::Unreadable(error),
    })?;
    let metadata = file.metadata().map_err(SecureReadError::Unreadable)?;
    if !metadata.file_type().is_file() {
        return Err(SecureReadError::NotRegular);
    }
    let protection = protection(&file, &metadata).map_err(SecureReadError::Unreadable)?;
    if !protection.is_exactly(requirements.mode) {
        return Err(SecureReadError::WrongMode);
    }
    if requirements.owned_by_caller && !protection.owned_by_caller() {
        return Err(SecureReadError::WrongOwner);
    }
    // From the handle, never from the path again: re-opening by name would hand back the substitution that the
    // no-follow open and the check above just ruled out.
    let mut content = Vec::new();
    #[expect(
        clippy::verbose_file_reads,
        reason = "`fs::read` re-opens by path, which is the substitution ruled out above"
    )]
    file.read_to_end(&mut content).map_err(SecureReadError::Unreadable)?;
    Ok(content)
}

/// Publishes one compact JSON document and a newline at mode 0600, atomically: the pid receipt, the suspended
/// state, the provenance receipt and the Docker create record all go through it.
pub(crate) fn write_json_line(path: &Path, value: &impl serde::Serialize, what: &str) -> Result<(), avl_base::Refusal> {
    use avl_base::{Exit, OrRefuse};
    let mut encoded = serde_json::to_vec(value).or_refuse("state_write_failed", Exit::FAILURE, || format!("cannot encode {what}"))?;
    encoded.push(b'\n');
    avl_base::fs::write_atomically(path, &encoded, 0o600)
}
