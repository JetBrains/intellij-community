//! The parity layout: the guest directories that make the absolute paths of the Bazel outputs valid inside the VM,
//! and the receipt that says which host they were built for. The layout holds no checkout: no share holds one.

use std::path::PathBuf;
use std::time::Duration;

use avl_base::format::words;
use avl_base::fs::write_atomically_if_changed;
use avl_base::{Config, Exit, OrRefuse, Refusal, posix_shell_quote};
use avl_wire::supervisor::SCHEMA_VERSION;
use serde::{Deserialize, Serialize};

use super::{GUEST_COMMAND_TIMEOUT, Guest, chown_argv, guest_join, path_text};
use crate::paths::GuestPaths;
use crate::proc::SpawnOptions;
use crate::share;

#[cfg(test)]
mod tests;

/// The timeout of the parity script. It makes the parity directories and the link onto the share, seconds of work.
const PARITY_SCRIPT_TIMEOUT: Duration = Duration::from_mins(5);

/// The name of the file that says what the layout of a worker was built for. It lives in the worker data directory
/// ([`parity_marker_path`]).
pub const PARITY_MARKER: &str = ".air-vm-parity.json";

/// The guest path of the parity marker: `<vmData>/.air-vm-parity.json`.
pub fn parity_marker_path(settings: &Config) -> String {
    guest_join(&settings.vm_data, PARITY_MARKER)
}

/// How the guest holds a worker's shares, which decides whether provisioning remounts them.
///
/// The worker manager passes it, because the manager knows the backend. The guest steps here do not branch on the
/// backend.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ShareMount {
    /// One VirtioFS device, on Tart and Parallels. The guest keeps a dead node for a file the host replaced, and the
    /// remount sweep ([`Guest::remount_shares`]) is the only invalidation the device has.
    VirtioFs,
    /// Bind mounts that `docker create` declared, on Docker. The guest has no device to sweep, and the sweep script
    /// fails at its `mount -t virtiofs`.
    Bind,
}

/// What one worker was provisioned for.
///
/// The host path is the whole point: a controller invoked with a different Bazel output root is asking for a parity
/// layout the worker does not have, and the receipt is what makes that detectable before a run reads outputs through
/// the wrong share. The guest root says where the layout put it: the host path itself on a Unix host, and its
/// [`GuestPaths`] spelling on a Windows host. The receipt names no checkout, so every checkout of the host shares one
/// layout.
///
/// Every field is required: a receipt missing one does not read, and is refused as not provisioned.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InitReceipt {
    pub schema_version: u32,
    pub worker: String,
    /// The *realpath*, as [`super::ensure_host_paths`] resolved it, because that is what the share was declared
    /// with and what the guest's symlink points at.
    pub host_bazel_user_root: String,
    pub guest_bazel_user_root: String,
    pub bazel_share: String,
}

/// Where a worker's provisioning receipt lives on the host.
pub fn init_receipt_path(settings: &Config, worker: &str) -> PathBuf {
    settings.worker_dir(worker).join("guest-init.json")
}

/// The marker's bytes: what the layout under it was built for, as one JSON line.
pub fn parity_marker_content(settings: &Config, worker: &str) -> String {
    #[derive(Serialize)]
    #[serde(rename_all = "camelCase")]
    struct Marker<'a> {
        schema_version: u32,
        backend: &'a str,
        guest_os: &'a str,
        worker: &'a str,
        bazel_share: &'a str,
    }
    let marker = Marker {
        schema_version: SCHEMA_VERSION,
        backend: settings.backend.as_str(),
        guest_os: settings.guest_os.as_str(),
        worker,
        bazel_share: &settings.bazel_share_name,
    };
    // A struct of strings and a number always serializes.
    let mut line = serde_json::to_string(&marker).unwrap_or_default();
    line.push('\n');
    line
}

/// The guest script that builds the layout making the paths of the Bazel outputs valid inside the VM, at their
/// [`GuestPaths`] root. On a Unix host this is the host's own absolute path.
///
/// Bazel's output user root becomes one symlink onto its read-only mount. The writable roots of a run are real
/// directories on the guest disk: the output tree, where IDE Starter and the dev-mode build server write, the temp
/// directory and the download cache. Build-dependency downloads divert through the test JVM
/// (`-Dintellij.build.download.cache.dir`), with the manifests resolving from pinned runfiles. The marker records
/// what the layout was built for.
pub fn parity_script(settings: &Config, worker: &str, bazel_mount: &str) -> Result<String, Refusal> {
    let paths = GuestPaths::of(settings)?;
    let bazel_user_root = paths.bazel_user_root();
    // `ln -sfh` on a BSD guest, `-sfn` on a GNU one: both replace the link rather than following it into the
    // directory it points at, and getting that wrong creates the new link *inside* the old target.
    let link = format!("/bin/ln {}", settings.guest.link_flags);
    let owned_directories = [
        &settings.vm_data,
        &settings.vm_runs_root,
        &settings.vm_out,
        &settings.vm_tmp,
        &settings.vm_download_cache,
    ]
    .map(String::as_str);
    let owned = owned_directories.map(posix_shell_quote).join(" ");

    let mut lines = vec![
        "#!/bin/sh".to_owned(),
        "set -eu".to_owned(),
        "umask 022".to_owned(),
        format!("/bin/mkdir -p {}", posix_shell_quote(guest_parent(bazel_user_root))),
        format!("{link} {} {}", posix_shell_quote(bazel_mount), posix_shell_quote(bazel_user_root)),
        format!("MARKER={}", posix_shell_quote(&parity_marker_path(settings))),
        format!("/bin/mkdir -p {owned}"),
    ];
    if let Some(chown) = chown_argv(settings, &[], &owned_directories) {
        let quoted: Vec<String> = chown[1..].iter().map(|word| posix_shell_quote(word)).collect();
        lines.push(format!("{} {}", chown[0], quoted.join(" ")));
    }
    lines.extend([
        format!(
            r#"printf '%s' {} > "$MARKER""#,
            posix_shell_quote(&parity_marker_content(settings, worker))
        ),
        r#"/bin/chmod 644 "$MARKER""#.to_owned(),
        String::new(),
    ]);
    Ok(lines.join("\n"))
}

/// The directory that holds a guest path: `/a` for `/a/b`, and `/` for `/a`.
fn guest_parent(path: &str) -> &str {
    match path.trim_end_matches('/').rsplit_once('/') {
        Some(("", _)) | None => "/",
        Some((parent, _)) => parent,
    }
}

/// Why a worker's parity layout is not ready.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum ParityError {
    /// Lazy provisioning repairs it: the worker has no receipt, a receipt for another Bazel output root
    /// (`guest_init_required`, `guest_init_stale`), or a path that fails its readiness probe (`guest_parity_missing`).
    Repairable(Refusal),
    /// Provisioning cannot repair it, such as host paths that were never resolved.
    Failed(Refusal),
}

impl From<ParityError> for Refusal {
    fn from(error: ParityError) -> Self {
        match error {
            ParityError::Repairable(refusal) | ParityError::Failed(refusal) => refusal,
        }
    }
}

/// What this worker was provisioned for, or a refusal because it has not been.
///
/// Every way of not being able to read it - absent, unparseable, a schema this half does not speak, a receipt
/// naming another worker - is the same actionable refusal, because the repair is the same: provision it.
pub fn read_init_receipt(settings: &Config, worker: &str) -> Result<InitReceipt, Refusal> {
    std::fs::read(init_receipt_path(settings, worker))
        .ok()
        .and_then(|raw| serde_json::from_slice::<InitReceipt>(&raw).ok())
        .filter(|receipt| receipt.schema_version == SCHEMA_VERSION && receipt.worker == worker)
        .ok_or_else(|| {
            Refusal::new(
                "guest_init_required",
                Exit::DATA_ERR,
                format!("{worker} has not been provisioned yet; a run or pool start provisions it automatically"),
            )
        })
}

/// Records what this worker was just provisioned for.
///
/// Mode 0600 and written atomically, like every receipt this controller keeps: it is read back after a crash to
/// decide whether a worker's layout is usable, and a half-written one would parse as "never provisioned" - which is
/// harmless only because the reader treats every unreadable receipt that way.
pub fn write_init_receipt(settings: &Config, worker: &str) -> Result<(), Refusal> {
    let paths = GuestPaths::of(settings)?;
    let receipt = InitReceipt {
        schema_version: SCHEMA_VERSION,
        worker: worker.to_owned(),
        host_bazel_user_root: path_text(settings.host_bazel_user_root()?),
        guest_bazel_user_root: paths.bazel_user_root().to_owned(),
        bazel_share: settings.bazel_share_name.clone(),
    };
    let mut encoded = serde_json::to_vec(&receipt).or_refuse("internal_error", Exit::FAILURE, || {
        format!("cannot describe {worker}'s provisioning receipt")
    })?;
    encoded.push(b'\n');
    write_atomically_if_changed(&init_receipt_path(settings, worker), &encoded, 0o600).map(drop)
}

impl Guest<'_> {
    /// Builds the layout over the share the backend has already declared, and records what it was built for.
    ///
    /// The whole of provisioning on Tart, where the shares are arguments to the `tart run` process the controller
    /// owns. Parallels reconciles its own shared-folder configuration first, and calls this once it has. Docker calls
    /// it over the bind mounts that `docker create` declared.
    pub async fn provision_worker(&self, mount: ShareMount) -> Result<(), Refusal> {
        if self.settings.guest.shares_at_host_paths {
            self.make_writable_roots().await?;
        } else {
            self.provision_parity(mount).await?;
        }
        write_init_receipt(self.settings, self.worker())
    }

    /// The directories a run writes into, for a guest whose shares sit at their host paths and so has no parity
    /// script to make them: the output tree, the temp directory and the download cache, made by the account that uses
    /// them. The receipt still records the roots the guest was provisioned for.
    async fn make_writable_roots(&self) -> Result<(), Refusal> {
        let settings = self.settings;
        let mut mkdir = words(["/bin/mkdir", "-p"]);
        mkdir.extend([settings.vm_out.clone(), settings.vm_tmp.clone(), settings.vm_download_cache.clone()]);
        self.as_root(&mkdir, &SpawnOptions::within(GUEST_COMMAND_TIMEOUT)).await.map(drop)
    }

    /// Remounts a VirtioFS device, then rebuilds the parity layout over the share.
    ///
    /// Bind mounts get no remount (see [`ShareMount::Bind`]). The share probe runs for both kinds, because a bind
    /// mount of the wrong directory is the same empty mount point.
    pub async fn provision_parity(&self, mount: ShareMount) -> Result<(), Refusal> {
        let settings = self.settings;
        // The share the *backend* declared, rather than a name read out of the config a second time: a declaration
        // that drifted then surfaces here, at a mount probe naming the share, instead of at a run reading outputs
        // through a share nobody pointed anywhere.
        let [bazel_share] = share::shares(settings)?;
        if mount == ShareMount::VirtioFs {
            self.remount_shares().await?;
        }
        let bazel_mount = self.require_share_mounted(&bazel_share.name).await?;
        let script = parity_script(settings, self.worker(), &bazel_mount)?;

        let state = guest_join(&settings.vm_data, "state");
        let script_path = guest_join(&state, "provision-parity.sh");
        self.as_root(&words(["/bin/mkdir", "-p", &state]), &SpawnOptions::within(GUEST_COMMAND_TIMEOUT))
            .await?;
        // Root makes the directories and the worker user writes into them. Without the chown the very next step - a
        // `tee` running as that user - fails on a root-owned directory.
        if let Some(chown) = chown_argv(settings, &[], &[&settings.vm_data, &state]) {
            self.as_root(&chown, &SpawnOptions::within(GUEST_COMMAND_TIMEOUT)).await?;
        }
        self.write_file(&script_path, script.as_bytes(), "700").await?;
        self.as_root(&words(["/bin/sh", &script_path]), &SpawnOptions::within(PARITY_SCRIPT_TIMEOUT))
            .await
            .map(drop)
    }

    /// Refuses a worker whose layout is not the one this invocation needs.
    ///
    /// Deliberately only guest-observable facts, plus the receipt. How a share is *declared* differs per backend,
    /// and the two things that would go wrong if a declaration drifted are already covered: a moved Bazel output root
    /// by the receipt comparison, and an unmounted share by the probes. The checkout takes no part, so a worker serves
    /// every checkout of the host.
    pub async fn ensure_parity_ready(&self) -> Result<(), Refusal> {
        Ok(self.check_parity().await?)
    }

    /// [`Self::ensure_parity_ready`], with the failures that provisioning repairs told apart from the rest.
    async fn check_parity(&self) -> Result<(), ParityError> {
        let settings = self.settings;
        let bazel_user_root = settings.host_bazel_user_root().map_err(ParityError::Failed)?;
        let paths = GuestPaths::of(settings).map_err(ParityError::Failed)?;
        let worker = self.worker();
        let receipt = read_init_receipt(settings, worker).map_err(ParityError::Repairable)?;
        if receipt.host_bazel_user_root != path_text(bazel_user_root) || receipt.guest_bazel_user_root != paths.bazel_user_root() {
            return Err(ParityError::Repairable(Refusal::new(
                "guest_init_stale",
                Exit::DATA_ERR,
                format!(
                    "{worker} was provisioned for the Bazel output root {}; the next run re-provisions it for {}",
                    receipt.host_bazel_user_root,
                    bazel_user_root.display()
                ),
            )));
        }
        // One `/bin/test` per probe. A guest exec carries one shell string, and nesting a quoted compound command
        // inside that string is exactly where the quoting breaks.
        let mut probes = vec![
            ("-w", settings.vm_out.clone()),
            ("-w", settings.vm_tmp.clone()),
            ("-w", settings.vm_download_cache.clone()),
            ("-d", paths.bazel_user_root().to_owned()),
        ];
        // The marker is the layout's own bookkeeping; shares at their host paths have no layout to mark.
        if !settings.guest.shares_at_host_paths {
            probes.insert(0, ("-f", parity_marker_path(settings)));
        }
        for (flag, path) in probes {
            // As the worker user, not as root: root can write anywhere, so a root probe would pass on exactly the
            // directory the run cannot write to.
            let argv = super::user_argv(settings, &words(["/bin/test", flag, &path]));
            if !self.succeeds(&argv, GUEST_COMMAND_TIMEOUT).await {
                return Err(ParityError::Repairable(Refusal::new(
                    "guest_parity_missing",
                    Exit::DATA_ERR,
                    format!(
                        "{path} failed its {flag} readiness probe in {worker}; provisioning repairs this \
                         automatically"
                    ),
                )));
            }
        }
        Ok(())
    }

    /// Whether this worker needs provisioning, propagating anything else.
    ///
    /// The distinction is the point: a stale receipt is a state to repair, and host paths that were never resolved
    /// are not. Swallowing the second would re-provision forever.
    pub async fn parity_broken(&self) -> Result<bool, Refusal> {
        match self.check_parity().await {
            Ok(()) => Ok(false),
            Err(ParityError::Repairable(_)) => Ok(true),
            Err(ParityError::Failed(refusal)) => Err(refusal),
        }
    }
}
