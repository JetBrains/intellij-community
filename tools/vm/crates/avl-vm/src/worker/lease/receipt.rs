//! The lease receipt: `<runtime>/receipts/lease-*.json`, a lease plus the marker that says the file is one of ours.

use std::path::{Path, PathBuf};

use avl_base::{Backend, Config, Exit, GuestOs, Refusal, Selection};
use avl_host_sys::private::PRIVATE_FILE_RULE;
use serde::{Deserialize, Serialize};

use crate::worker::secure::{Requirements, SecureReadError, read_private_file};
use crate::worker::worker::{Lease, read_lease};

/// What marks a file in the receipt directory as one of ours.
///
/// Checked before anything else about a receipt, and it is why the sweep in [`remove_lease_receipts`] can walk a
/// directory the user also owns: a file that does not carry this is not a receipt and is left alone. The name
/// predates the Linux pool and is a contract: agents hold receipts written with it.
pub(crate) const RECEIPT_KIND: &str = "macos-vm-ui-test-lease";

/// A lease plus the marker, flattened into one object with `kind` last.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub(crate) struct LeaseReceipt {
    #[serde(flatten)]
    pub lease: Lease,
    pub kind: String,
}

/// The receipt that `--lease-file` names, or `lease_file_required` for a command that needs one and got none. The
/// command line refuses an empty `--lease-file`, so a named receipt is a path.
pub(crate) fn required_receipt(lease_file: Option<&Path>) -> Result<&Path, Refusal> {
    lease_file.ok_or_else(|| Refusal::new("lease_file_required", Exit::USAGE, "--lease-file is required"))
}

/// Validates a lease receipt and answers its absolute path and the live lease it owns.
///
/// The gate every receipt-bearing command runs first, and every refusal below is a way of presenting a handle that
/// proves nothing:
///
/// - opened without following a symlink, and checked on the open descriptor;
/// - private and owned by the invoking user, because a receipt anyone could write or read is not evidence of
///   anything. Private is mode exactly 0600 on Unix, and an access list that grants nobody else on Windows;
/// - the backend it selects must be the one the controller selected, or one invocation would be acting on another
///   pool's worker with a valid-looking handle;
/// - and finally the token must still match the *live* lease, which is what makes a receipt for a lease that has
///   since been released refuse instead of authorizing.
pub(crate) fn receipt_for_path(settings: &Config, input: &Path) -> Result<(PathBuf, Lease), Refusal> {
    let path =
        std::path::absolute(input).map_err(|error| invalid(format!("lease receipt does not exist: {}: {error}", input.display())))?;
    let receipt = read_secure_receipt(&path)?;
    if !receipt.lease.is_complete() || receipt.kind != RECEIPT_KIND {
        return Err(invalid("lease receipt has an invalid schema".to_owned()));
    }
    let selected = receipt.lease.backend;
    if selected != settings.backend {
        return Err(Refusal::new(
            "lease_backend_mismatch",
            Exit::NO_PERM,
            format!("lease receipt selects {selected}, but the controller selected {}", settings.backend),
        ));
    }
    settings.require_pool_worker(&receipt.lease.worker)?;
    read_lease(&settings.lease_path(&receipt.lease.worker))?
        .filter(|active| active.same_lease(&receipt.lease))
        .map(|active| (path, active))
        .ok_or_else(|| invalid("lease receipt does not own an active worker".to_owned()))
}

fn invalid(message: String) -> Refusal {
    Refusal::new("invalid_lease_file", Exit::NO_PERM, message)
}

/// Decodes a receipt only if it is a private, current-user, regular file that was opened without following a
/// link.
///
/// Most failures answer the same code, deliberately: the caller's only correct response to any of them is to stop
/// presenting this file, and a message distinguishing them would be a probe for whoever is holding the wrong file.
/// The mode and the owner keep their own codes, because each is a repair the rightful holder can make.
fn read_secure_receipt(path: &Path) -> Result<LeaseReceipt, Refusal> {
    let content = read_private_file(
        path,
        Requirements {
            mode: 0o600,
            owned_by_caller: true,
        },
    )
    .map_err(|error| match error {
        SecureReadError::Missing | SecureReadError::Symlink => invalid(format!("lease receipt does not exist: {}", path.display())),
        SecureReadError::Unreadable(_) => invalid(format!("lease receipt cannot be read: {}", path.display())),
        SecureReadError::NotRegular => invalid("lease receipt must be a regular file".to_owned()),
        SecureReadError::WrongMode => Refusal::new(
            "invalid_lease_file_mode",
            Exit::NO_PERM,
            format!("lease receipt {PRIVATE_FILE_RULE}"),
        ),
        SecureReadError::WrongOwner => Refusal::new(
            "invalid_lease_file_owner",
            Exit::NO_PERM,
            "lease receipt must be owned by the current user",
        ),
    })?;
    serde_json::from_slice(&content).map_err(|error| invalid(format!("lease receipt is not valid JSON: {error}")))
}

/// Writes the caller's handle to a lease and answers its path.
///
/// Published whole through [`avl_base::fs::write_exclusively`]: a receipt whose write or sync failed is a corrupt
/// handle that would be answered as this acquisition's proof of ownership, so the file under its final
/// `lease-*.json` name (the shape the sweeps filter on) is complete from the moment it exists. The temporary it is
/// written as is dot-named, so a sweep never sees a partial receipt. Two acquisitions of two workers write here
/// concurrently; the UUID makes a collision improbable and the no-clobber link refuses it. Mode 0600 after the
/// write, because a creation mode is masked by `umask`, and a caller under an odd one would otherwise acquire a
/// lease and immediately be unable to use it. On Windows the receipt has no mode, and inherits the private access
/// list of the receipt directory, which `prepare_runtime_dirs` sets.
pub(crate) fn write_lease_receipt(settings: &Config, lease: &Lease) -> Result<PathBuf, Refusal> {
    let failed = |message: String| Refusal::new("state_write_failed", Exit::FAILURE, message);
    let receipt = LeaseReceipt {
        lease: lease.clone(),
        kind: RECEIPT_KIND.to_owned(),
    };
    let mut encoded =
        serde_json::to_vec(&receipt).map_err(|error| failed(format!("cannot describe the receipt for {}: {error}", lease.worker)))?;
    encoded.push(b'\n');
    let path = receipt_directory(settings).join(format!("lease-{}.json", avl_base::new_id()));
    avl_base::fs::write_exclusively(&path, &encoded, 0o600)
        .map_err(|error| failed(format!("cannot write the lease receipt for {}: {error}", lease.worker)))?;
    Ok(path)
}

pub(crate) fn receipt_directory(settings: &Config) -> PathBuf {
    settings.runtime_root.join("receipts")
}

/// The caller's existing receipt for this lease, or `None`.
///
/// This is what makes an acquisition idempotent: a caller that asked twice - a retried command, a lane whose wrapper
/// re-ran - finds the lease it already holds and is handed back the *same* receipt rather than a second one. Only a
/// receipt that passes the whole of [`receipt_for_path`] counts, because a receipt that would be refused as a handle
/// cannot recover anything either.
pub(crate) fn reusable_lease_receipt(settings: &Config, lease: &Lease) -> Option<PathBuf> {
    receipt_files(settings)
        .into_iter()
        .find(|path| receipt_for_path(settings, path).is_ok_and(|(_, validated)| validated.same_lease(lease)))
}

/// Deletes every receipt for one lease, so a released worker leaves no usable handle.
///
/// Token-checked rather than worker-checked, and read directly rather than through [`receipt_for_path`]: the lease
/// this is called for has usually just been unlinked, so a validating read would refuse every one of its own
/// receipts and leave them all behind. Anything unreadable or unrelated in the directory is left alone.
pub(crate) fn remove_lease_receipts(settings: &Config, lease: &Lease) {
    for path in receipt_files(settings) {
        let Some(receipt) = std::fs::read(&path)
            .ok()
            .and_then(|content| serde_json::from_slice::<LeaseReceipt>(&content).ok())
        else {
            continue;
        };
        if receipt.kind == RECEIPT_KIND && !receipt.lease.token.is_empty() && receipt.lease.same_lease(lease) {
            let _ = std::fs::remove_file(&path);
        }
    }
}

/// The receipt directory's files that could be receipts: regular, and named `lease-*.json`.
pub(crate) fn receipt_files(settings: &Config) -> Vec<PathBuf> {
    let Ok(entries) = std::fs::read_dir(receipt_directory(settings)) else {
        return Vec::new();
    };
    let mut paths: Vec<PathBuf> = entries
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_ok_and(|kind| kind.is_file()))
        .filter(|entry| {
            let name = entry.file_name();
            let name = name.to_string_lossy();
            name.starts_with("lease-") && name.ends_with(".json")
        })
        .map(|entry| entry.path())
        .collect();
    paths.sort();
    paths
}

/// Which pool a lease receipt selects, or `None` when the file is not one.
///
/// This runs *before* a config exists - it is what lets `--lease-file` decide the backend and the guest OS without
/// the caller repeating `--backend` - so it validates nothing beyond the marker and the two axes, and a receipt
/// missing either axis, or naming one this controller does not know, selects nothing. Everything else about the
/// receipt is checked by [`receipt_for_path`] once there is a config to check it against.
pub(crate) fn receipt_backend(path: &Path) -> Option<Selection> {
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct Axes {
        kind: String,
        backend: Backend,
        guest_os: GuestOs,
    }
    if path.as_os_str().is_empty() {
        return None;
    }
    let content = std::fs::read(std::path::absolute(path).ok()?).ok()?;
    let axes: Axes = serde_json::from_slice(&content).ok()?;
    if axes.kind != RECEIPT_KIND {
        return None;
    }
    Some(Selection {
        backend: axes.backend,
        guest_os: axes.guest_os,
    })
}
