//! The parity layout: the guest directories that make the host's absolute paths valid inside the VM, and the
//! receipt that says which host they were built for.

use std::path::PathBuf;
use std::sync::LazyLock;
use std::time::Duration;

use avl_base::format::words;
use avl_base::fs::write_atomically_if_changed;
use avl_base::{Config, Exit, OrRefuse, Refusal, posix_shell_quote};
use avl_wire::supervisor::SCHEMA_VERSION;
use regex::Regex;
use serde::{Deserialize, Serialize};

use super::{GUEST_COMMAND_TIMEOUT, Guest, guest_join, path_text};
use crate::paths::GuestPaths;
use crate::proc::SpawnOptions;
use crate::share;

#[cfg(test)]
mod tests;

/// The timeout of the parity script. It makes the parity directories and links over the two shares, seconds of work.
const PARITY_SCRIPT_TIMEOUT: Duration = Duration::from_mins(5);

/// The file that says a directory at the host repository's path is this controller's to manage. Its absence in a
/// non-empty directory is a refusal; see [`parity_script`].
pub const PARITY_MARKER: &str = ".air-vm-parity.json";

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
/// The two host paths are the whole point: a controller invoked from a second checkout, or with a different Bazel
/// output root, is asking for a parity layout the worker does not have, and the receipt is what makes that
/// detectable before a run reads outputs through the wrong share. The two guest roots say where the layout put
/// them: the host paths themselves on a Unix host, and their [`GuestPaths`] spelling on a Windows host.
///
/// Every field is required: a receipt missing one does not read, and is refused as not provisioned.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InitReceipt {
    pub schema_version: u32,
    pub worker: String,
    pub host_repo: String,
    /// The *realpath*, as [`super::ensure_host_paths`] resolved it, because that is what the share was declared
    /// with and what the guest's symlink points at.
    pub host_bazel_user_root: String,
    pub guest_repo: String,
    pub guest_bazel_user_root: String,
    pub repo_share: String,
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
        repo_share: &'a str,
        bazel_share: &'a str,
    }
    let marker = Marker {
        schema_version: SCHEMA_VERSION,
        backend: settings.backend.as_str(),
        guest_os: settings.guest_os.as_str(),
        worker,
        repo_share: &settings.repo_share_name,
        bazel_share: &settings.bazel_share_name,
    };
    // A struct of strings and a number always serializes.
    let mut line = serde_json::to_string(&marker).unwrap_or_default();
    line.push('\n');
    line
}

/// The input gate on every repository-root entry the layout will name.
///
/// Fail-closed, because the entry name is interpolated into a shell script the controller writes into the guest: a
/// name carrying a quote, a space or a `$` is not a name this layout can build a symlink for, and quoting it more
/// cleverly would only move the question. The character class is what a POSIX path component can carry unquoted,
/// which every entry of this repository's root satisfies.
pub fn validate_parity_entry_name(entry: &str) -> Result<(), Refusal> {
    static PARITY_ENTRY_NAME: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"^[A-Za-z0-9._@+~-]+$").expect("a constant pattern compiles"));
    if PARITY_ENTRY_NAME.is_match(entry) {
        return Ok(());
    }
    Err(Refusal::new(
        "unsafe_name",
        Exit::USAGE,
        format!("repository root entry has characters the parity layout does not support: {entry:?}"),
    ))
}

/// The guest script that builds the layout making the host's paths valid inside the VM, at their [`GuestPaths`]
/// roots. On a Unix host these are the host's own absolute paths.
///
/// Bazel's output user root becomes one symlink onto its read-only mount, and the repository root becomes a real
/// directory of per-entry symlinks - real, so that `out` alone can divert to a guest-local writable directory,
/// which is where IDE Starter and the dev-mode build server write. Build-dependency downloads divert through the
/// test JVM instead (`-Dintellij.build.download.cache.dir`), with the manifests resolving from pinned runfiles.
///
/// The one refusal inside the script is the marker guard: an unmarked non-empty tree at the parity path could be a
/// real user directory, and rewriting it would destroy data outside this controller's ownership. It exits 65, a
/// document on disk this controller will not act on.
pub fn parity_script(settings: &Config, worker: &str, repo_mount: &str, bazel_mount: &str, entries: &[String]) -> Result<String, Refusal> {
    let paths = GuestPaths::of(settings)?;
    let (repo, bazel_user_root) = (paths.repo(), paths.bazel_user_root());
    for entry in entries {
        validate_parity_entry_name(entry)?;
    }
    // `ln -sfh` on a BSD guest, `-sfn` on a GNU one: both replace the link rather than following it into the
    // directory it points at, and getting that wrong creates the new link *inside* the old target.
    let link = format!("/bin/ln {}", settings.guest.link_flags);
    let owned = [
        &settings.vm_data,
        &settings.vm_runs_root,
        &settings.vm_out,
        &settings.vm_tmp,
        &settings.vm_download_cache,
    ]
    .map(|directory| posix_shell_quote(directory))
    .join(" ");

    let mut lines = vec![
        "#!/bin/sh".to_owned(),
        "set -eu".to_owned(),
        "umask 022".to_owned(),
        format!("/bin/mkdir -p {}", posix_shell_quote(guest_parent(bazel_user_root))),
        format!("{link} {} {}", posix_shell_quote(bazel_mount), posix_shell_quote(bazel_user_root)),
        format!("PARITY={}", posix_shell_quote(repo)),
        format!("MARKER={}", posix_shell_quote(&guest_join(repo, PARITY_MARKER))),
        r#"if [ -e "$PARITY" ] && [ ! -f "$MARKER" ] && [ -n "$(ls -A "$PARITY" 2>/dev/null || true)" ]; then"#.to_owned(),
        r#"  echo "refusing to manage $PARITY: it exists without a parity marker" >&2"#.to_owned(),
        "  exit 65".to_owned(),
        "fi".to_owned(),
        format!(r#"/bin/mkdir -p "$PARITY" {owned}"#),
        format!("{} {} {owned}", settings.guest.chown, posix_shell_quote(&settings.vm_user)),
        // Every symlink in the parity directory is controller-managed, so the set is rebuilt from scratch: an entry
        // removed from the repository root must not linger as a link to nothing.
        r#"for existing in "$PARITY"/* "$PARITY"/.[!.]* "$PARITY"/..?*; do"#.to_owned(),
        r#"  [ -L "$existing" ] || continue"#.to_owned(),
        r#"  /bin/rm -f "$existing""#.to_owned(),
        "done".to_owned(),
    ];
    for entry in entries {
        lines.push(format!(
            r#"{link} {} "$PARITY"/{}"#,
            posix_shell_quote(&guest_join(repo_mount, entry)),
            posix_shell_quote(entry)
        ));
    }
    lines.extend([
        format!(r#"{link} {} "$PARITY"/out"#, posix_shell_quote(&settings.vm_out)),
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
    /// Lazy provisioning repairs it: the worker has no receipt, a receipt for another checkout
    /// (`guest_init_required`, `guest_init_stale`), or a path that fails its readiness probe (`guest_parity_missing`).
    Repairable(Refusal),
    /// Provisioning cannot repair it, such as a host repository that is not a Git working tree.
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
        host_repo: path_text(settings.host_repo()?),
        host_bazel_user_root: path_text(settings.host_bazel_user_root()?),
        guest_repo: paths.repo().to_owned(),
        guest_bazel_user_root: paths.bazel_user_root().to_owned(),
        repo_share: settings.repo_share_name.clone(),
        bazel_share: settings.bazel_share_name.clone(),
    };
    let mut encoded = serde_json::to_vec(&receipt).or_refuse("internal_error", Exit::FAILURE, || {
        format!("cannot describe {worker}'s provisioning receipt")
    })?;
    encoded.push(b'\n');
    write_atomically_if_changed(&init_receipt_path(settings, worker), &encoded, 0o600).map(drop)
}

impl Guest<'_> {
    /// Builds the layout over shares the backend has already declared, and records what it was built for.
    ///
    /// The whole of provisioning on Tart, where the shares are arguments to the `tart run` process the controller
    /// owns. Parallels reconciles its own shared-folder configuration first, and calls this once it has. Docker calls
    /// it over the bind mounts that `docker create` declared.
    pub async fn provision_worker(&self, mount: ShareMount) -> Result<(), Refusal> {
        self.provision_parity(mount).await?;
        write_init_receipt(self.settings, self.worker())
    }

    /// Remounts a VirtioFS device, then rebuilds the parity layout over the shares.
    ///
    /// Bind mounts get no remount (see [`ShareMount::Bind`]). The share probes run for both kinds, because a bind
    /// mount of the wrong directory is the same empty mount point.
    pub async fn provision_parity(&self, mount: ShareMount) -> Result<(), Refusal> {
        let settings = self.settings;
        let repo = settings.host_repo()?;
        // The shares the *backend* declared, rather than two names read out of the config a second time: a
        // declaration that drifted then surfaces here, at a mount probe naming the share, instead of at a run
        // reading outputs through a share nobody pointed anywhere.
        let [repo_share, bazel_share] = share::shares(settings)?;
        if mount == ShareMount::VirtioFs {
            self.remount_shares().await?;
        }
        let repo_mount = self.require_share_mounted(&repo_share.name, Some(".git")).await?;
        let bazel_mount = self.require_share_mounted(&bazel_share.name, None).await?;

        let listing = std::fs::read_dir(repo).or_refuse("host_repo_required", Exit::USAGE, || {
            format!("cannot read the host repository {}", repo.display())
        })?;
        let mut entries = Vec::new();
        for item in listing {
            let item = item.or_refuse("host_repo_required", Exit::USAGE, || {
                format!("cannot read the host repository {}", repo.display())
            })?;
            // A name that is not UTF-8 is carried lossily, which the entry gate then refuses by name.
            let name = item.file_name().to_string_lossy().into_owned();
            // `out` diverts to guest-local storage and the marker is this layout's own bookkeeping; neither is a link
            // onto the read-only mount.
            if name != "out" && name != PARITY_MARKER {
                entries.push(name);
            }
        }
        // Sorted, so one checkout always renders one script: a directory listing has no order of its own.
        entries.sort();
        let script = parity_script(settings, self.worker(), &repo_mount, &bazel_mount, &entries)?;

        let state = guest_join(&settings.vm_data, "state");
        let script_path = guest_join(&state, "provision-parity.sh");
        self.as_root(&words(["/bin/mkdir", "-p", &state]), &SpawnOptions::within(GUEST_COMMAND_TIMEOUT))
            .await?;
        // Root makes the directories and the worker user writes into them. Without the chown the very next step - a
        // `tee` running as that user - fails on a root-owned directory.
        self.as_root(
            &words([settings.guest.chown, &settings.vm_user, &settings.vm_data, &state]),
            &SpawnOptions::within(GUEST_COMMAND_TIMEOUT),
        )
        .await?;
        self.write_file(&script_path, script.as_bytes(), "700").await?;
        self.as_root(&words(["/bin/sh", &script_path]), &SpawnOptions::within(PARITY_SCRIPT_TIMEOUT))
            .await
            .map(drop)
    }

    /// Refuses a worker whose layout is not the one this invocation needs.
    ///
    /// Deliberately only guest-observable facts, plus the receipt. How a share is *declared* differs per backend,
    /// and the two things that would go wrong if a declaration drifted are already covered: a moved repository by
    /// the receipt comparison, and an unmounted or empty share by the probes.
    pub async fn ensure_parity_ready(&self) -> Result<(), Refusal> {
        Ok(self.check_parity().await?)
    }

    /// [`Self::ensure_parity_ready`], with the failures that provisioning repairs told apart from the rest.
    async fn check_parity(&self) -> Result<(), ParityError> {
        let settings = self.settings;
        let repo = settings.host_repo().map_err(ParityError::Failed)?;
        let bazel_user_root = settings.host_bazel_user_root().map_err(ParityError::Failed)?;
        let paths = GuestPaths::of(settings).map_err(ParityError::Failed)?;
        let worker = self.worker();
        let receipt = read_init_receipt(settings, worker).map_err(ParityError::Repairable)?;
        if receipt.host_repo != path_text(repo)
            || receipt.host_bazel_user_root != path_text(bazel_user_root)
            || receipt.guest_repo != paths.repo()
            || receipt.guest_bazel_user_root != paths.bazel_user_root()
        {
            return Err(ParityError::Repairable(Refusal::new(
                "guest_init_stale",
                Exit::DATA_ERR,
                format!(
                    "{worker} was provisioned for {}; the next run re-provisions it for {}",
                    receipt.host_repo,
                    repo.display()
                ),
            )));
        }
        // One `/bin/test` per probe. A guest exec carries one shell string, and nesting a quoted compound command
        // inside that string is exactly where the quoting breaks.
        let probes = [
            ("-f", guest_join(paths.repo(), PARITY_MARKER)),
            ("-r", guest_join(paths.repo(), ".git")),
            ("-w", settings.vm_out.clone()),
            ("-w", settings.vm_tmp.clone()),
            ("-w", settings.vm_download_cache.clone()),
            ("-d", paths.bazel_user_root().to_owned()),
        ];
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
    /// The distinction is the point: a stale receipt is a state to repair, and a host repository that is not a Git
    /// working tree is not. Swallowing the second would re-provision forever.
    pub async fn parity_broken(&self) -> Result<bool, Refusal> {
        match self.check_parity().await {
            Ok(()) => Ok(false),
            Err(ParityError::Repairable(_)) => Ok(true),
            Err(ParityError::Failed(refusal)) => Err(refusal),
        }
    }
}
