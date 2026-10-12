//! The mount the controller takes over from the guest's own boot automount, and the sweep that invalidates it.
//!
//! The sweep is for a VirtioFS device, on Tart and Parallels. A Docker worker's shares are bind mounts with no
//! device to sweep, so nothing here runs on Docker ([`ShareMount::Bind`](super::ShareMount::Bind)).

use std::time::Duration;

use avl_base::config::VirtiofsMount;
use avl_base::format::words;
use avl_base::{Config, Exit, Refusal, posix_shell_quote};

use super::{GUEST_COMMAND_TIMEOUT, Guest, guest_join};
use crate::proc::SpawnOptions;

/// The timeout of the remount script. It unmounts and mounts the two VirtioFS shares, which takes seconds, so two
/// minutes covers a guest under load.
const REMOUNT_TIMEOUT: Duration = Duration::from_mins(2);

#[cfg(test)]
mod tests;

/// Where one share appears inside the guest.
///
/// The share's *name* is all this needs of it: what a backend did to publish it is
/// [`SharedFolder`](crate::share::SharedFolder)'s business, and nothing here renders one. What is owned here is
/// what the guest then does with the device those declarations produce.
pub fn share_mount_path(settings: &Config, share_name: &str) -> String {
    guest_join(settings.guest.share_mount, share_name)
}

/// The guest script that drops every view of the shared-folder device and mounts it where the controller wants it.
///
/// On Tart and Parallels every share reaches the macOS guest through one VirtioFS device under Apple's automount tag.
/// The guest mounts that tag at boot, at a path with spaces, and the controller replaces that mount with its own for
/// two reasons. The boot mount is stale by design: the guest keeps a dead VirtioFS node for any file the host
/// rewrites after the mount, so `stat` still answers from cache while `open` fails with ENOENT, which is precisely
/// what a host rebuild does to a Bazel output. Remounting is the only invalidation the guest offers. A space-free
/// mount point also keeps every guest path a single shell word, which is all `prlctl exec` can carry.
///
/// Every binary in it is absolute. This runs under `sudo -H`, whose `secure_path` on a macOS guest contains no
/// `/sbin`: a bare `mount` was "command not found", so the sweep unmounted nothing while still exiting 0, and the
/// mount then failed with "Resource busy" against the boot automount it was meant to replace.
///
/// macOS prints a mount as `tag on /Volumes/X (AppleVirtIOFS, ...)`, so the `awk` strips the parenthesis to reduce
/// the line to the bare mount point.
pub fn remount_script(settings: &Config, device: &VirtiofsMount) -> String {
    [
        "#!/bin/sh".to_owned(),
        "set -eu".to_owned(),
        format!("MOUNT={}", posix_shell_quote(settings.guest.share_mount)),
        "LIST=/tmp/air-vm-share-mounts.txt".to_owned(),
        // Every view of the device, the boot automount included, so no stale node survives anywhere in the guest:
        // a second mount point would keep its own cache.
        format!(
            r#"{} | /usr/bin/awk -F' on ' '/{}/ {{ sub(/ \(.*/, "", $2); print $2 }}' > "$LIST""#,
            device.mount_binary, device.mounted_filesystem
        ),
        r"while IFS= read -r mounted; do".to_owned(),
        r#"  [ -n "$mounted" ] || continue"#.to_owned(),
        format!(r#"  {0} "$mounted" || {0} -f "$mounted""#, device.umount_binary),
        r#"done < "$LIST""#.to_owned(),
        r#"/bin/rm -f "$LIST""#.to_owned(),
        r#"/bin/mkdir -p "$MOUNT""#.to_owned(),
        device.mount_shares.to_owned(),
        String::new(),
    ]
    .join("\n")
}

impl Guest<'_> {
    /// Makes a VirtioFS guest see the host's current bytes.
    ///
    /// A rebuilt output the guest has already looked at is otherwise unopenable for the lifetime of the mount. The
    /// worker manager's share refresh calls this on Tart and Parallels after a host build, and waits a settle on
    /// Docker instead. Provisioning calls it for [`ShareMount::VirtioFs`](super::ShareMount::VirtioFs). A guest with
    /// no VirtioFS device, which is a Docker worker, is refused before anything runs.
    pub async fn remount_shares(&self) -> Result<(), Refusal> {
        let Some(device) = self.settings.guest.virtiofs else {
            return Err(Refusal::new(
                "unsupported_backend_operation",
                Exit::SOFTWARE,
                format!(
                    "{} has no VirtioFS device to remount: a {} guest holds its shares as bind mounts",
                    self.worker(),
                    self.settings.guest_os
                ),
            ));
        };
        let state = guest_join(&self.settings.vm_data, "state");
        let script = guest_join(&state, "remount-shares.sh");
        self.as_root(&words(["/bin/mkdir", "-p", &state]), &SpawnOptions::within(GUEST_COMMAND_TIMEOUT))
            .await?;
        self.write_file(&script, remount_script(self.settings, device).as_bytes(), "700")
            .await?;
        self.as_root(&words(["/bin/sh", &script]), &SpawnOptions::within(REMOUNT_TIMEOUT))
            .await
            .map(drop)
    }

    /// A share's guest mount point, refused unless something is actually there.
    ///
    /// `probe` is optional because "probe the mount point itself" and "probe a path inside it" are different
    /// questions: the repository share is probed at `.git`, because a mount point that exists and is empty is
    /// exactly what a share declared at the wrong path looks like, while the Bazel share has no entry that is
    /// guaranteed to be there and can only be probed for itself.
    pub async fn require_share_mounted(&self, share_name: &str, probe: Option<&str>) -> Result<String, Refusal> {
        let mount = share_mount_path(self.settings, share_name);
        let target = probe.map_or_else(|| mount.clone(), |inside| guest_join(&mount, inside));
        if !self.succeeds(&words(["/bin/test", "-e", &target]), GUEST_COMMAND_TIMEOUT).await {
            return Err(Refusal::new(
                "guest_share_unmounted",
                Exit::DATA_ERR,
                format!("the {share_name} share is not mounted at {mount} in {}", self.worker()),
            ));
        }
        Ok(mount)
    }
}
