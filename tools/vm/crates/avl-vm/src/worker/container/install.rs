//! The install the pinned Apple `container` CLI runs from (the decision
//! the design `community/tools/vm/openspec/changes/archive/2026-11-18-the-docker-backend-runs-on-the-host-engine-or-apple-container/design.md`).
//!
//! The pin lives in the Bazel repository cache. When the Bazel user root is on another volume than the runtime root,
//! the controller runs a copy of the install under the runtime root instead: on 2026-11-18 the vmnet plugin of an
//! install on an external APFS volume exited every 10 s with `vmnet_return_t(rawValue: 1001)`, and the server then
//! answered no command. The same install on the system volume ran.
//!
//! - **The rule** is the device of the two directories (`st_dev`). The copy goes under the runtime root, so the rule
//!   copies exactly when the copy is on another volume than the pin. It names no mount flag, because no flag was
//!   proven to be the cause.
//! - **One copy per pin**: `<runtime root>/apple-container/<name of the install root>`, made once into a temporary
//!   directory beside it, checked with `codesign --verify --strict`, and renamed into place. A record in the copy names
//!   its source, so a later command reuses it after one `stat`.
//! - **Old copies** go once the server of the session runs from the copy of this pin
//!   ([`prune_other_copies`]), so the copy a running server uses is never deleted.

use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use avl_base::{Exit, Refusal, RefusalExt as _, Reporter, SCHEMA_VERSION};
use avl_host_sys::{Ctx, ProcError, Runner, SpawnOptions};
use serde::{Deserialize, Serialize};

use crate::worker::container::install_root_of;

#[cfg(test)]
#[cfg(unix)]
mod tests;

/// The directory of the runtime root that holds the copies of the pinned install.
pub(crate) const INSTALL_COPIES: &str = "apple-container";

/// The record of a copy, inside it.
pub(crate) const COPY_RECORD: &str = "air-copy.json";

/// The verifier of the copy.
pub(crate) const CODESIGN: &str = "/usr/bin/codesign";

/// The timeout of one `codesign --verify`, which took 0.2 s for the network plugin.
const CODESIGN_TIMEOUT: Duration = Duration::from_mins(1);

/// The directories of an install that the tool reads: it finds its plugins and its server from the real path of
/// `bin/container`.
const INSTALL_PARTS: [&str; 2] = ["bin", "libexec"];

/// The executables whose signatures a copy must keep: the CLI, the server and the network plugin.
pub(crate) const SIGNED_PARTS: [&str; 3] = [
    "bin/container",
    "bin/container-apiserver",
    "libexec/container/plugins/container-network-vmnet/bin/container-network-vmnet",
];

/// A temporary directory of a copy that is older than this belongs to no live command, so the prune removes it.
const STALE_TEMPORARY: Duration = Duration::from_hours(1);

/// What a copy was made from: the install root, and the size and the modification time of its CLI. A record that says
/// the same as the source is the copy of that source.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct CopyRecord {
    pub schema_version: u32,
    pub source: PathBuf,
    pub cli_bytes: u64,
    pub cli_modified_nanos: u64,
}

impl CopyRecord {
    fn of(root: &Path, cli: &Path) -> io::Result<Self> {
        let metadata = fs::metadata(cli)?;
        let modified = metadata.modified()?.duration_since(UNIX_EPOCH).unwrap_or_default();
        Ok(Self {
            schema_version: SCHEMA_VERSION,
            source: root.to_path_buf(),
            cli_bytes: metadata.len(),
            cli_modified_nanos: u64::try_from(modified.as_nanos()).unwrap_or(u64::MAX),
        })
    }

    /// The record of the copy at `copy`, or `None` when there is none or it cannot be read.
    fn read(copy: &Path) -> Option<Self> {
        serde_json::from_slice(&fs::read(copy.join(COPY_RECORD)).ok()?).ok()
    }
}

/// Whether two existing paths are on one file system, by their device. A host without devices answers yes for two
/// paths that exist, so it never copies.
pub(crate) fn on_one_volume(left: &Path, right: &Path) -> io::Result<bool> {
    let (left, right) = (fs::metadata(left)?, fs::metadata(right)?);
    #[cfg(unix)]
    {
        use std::os::unix::fs::MetadataExt as _;
        Ok(left.dev() == right.dev())
    }
    #[cfg(not(unix))]
    {
        let _ = (left, right);
        Ok(true)
    }
}

/// The CLI to run for the pinned CLI `cli`, a real path: `cli` itself when its install root is on the volume of
/// `runtime_root`, else the CLI of the copy of the install under `runtime_root` ([`copy_of`]).
pub(crate) async fn runnable_cli(
    ctx: &Ctx,
    runner: &Runner,
    reporter: &Reporter,
    cli: &Path,
    runtime_root: &Path,
) -> Result<PathBuf, Refusal> {
    let root = install_root_of(cli);
    avl_base::fs::create_private_dir(runtime_root)?;
    let same = on_one_volume(&root, runtime_root).map_err(|error| {
        copy_failed(format!(
            "cannot compare the volume of the pinned Apple container install {} with the volume of the runtime root {}: \
             {error}",
            root.display(),
            runtime_root.display()
        ))
    })?;
    if same {
        return Ok(cli.to_path_buf());
    }
    copy_of(ctx, runner, reporter, CODESIGN, cli, &runtime_root.join(INSTALL_COPIES)).await
}

/// The CLI of the copy of the install of `cli` in `copies`, made now unless a copy of the same source is there.
///
/// The copy holds [`INSTALL_PARTS`] and its record. It is made in a temporary directory beside its place, each of
/// [`SIGNED_PARTS`] passes `codesign --verify --strict`, and one rename publishes it. A copy of another source under
/// the same name is replaced. A command that loses the race to another one uses the copy of the winner.
pub(crate) async fn copy_of(
    ctx: &Ctx,
    runner: &Runner,
    reporter: &Reporter,
    codesign: &str,
    cli: &Path,
    copies: &Path,
) -> Result<PathBuf, Refusal> {
    let root = install_root_of(cli);
    let relative = cli.strip_prefix(&root).map_err(|error| {
        Refusal::internal(format!(
            "{} is not inside its install root {}: {error}",
            cli.display(),
            root.display()
        ))
    })?;
    let Some(name) = root.file_name() else {
        return Err(copy_failed(format!(
            "the Apple container install root {} has no name",
            root.display()
        )));
    };
    let copy = copies.join(name);
    let source = CopyRecord::of(&root, cli)
        .map_err(|error| copy_failed(format!("cannot read the pinned Apple container CLI {}: {error}", cli.display())))?;
    if CopyRecord::read(&copy).as_ref() == Some(&source) {
        return Ok(copy.join(relative));
    }

    reporter.note(
        format!(
            "the pinned Apple container install {} is on another volume than the runtime root, and the network \
             plugin of the engine did not start from such a volume; copying the install to {} once for this pin",
            root.display(),
            copy.display()
        ),
        None,
    );
    avl_base::fs::create_private_dir(copies)?;
    let name = name.to_string_lossy().into_owned();
    let staged = tempfile::Builder::new()
        .prefix(&format!(".{name}-"))
        .suffix(".tmp")
        .tempdir_in(copies)
        .map_err(|error| copy_failed(format!("cannot create a temporary directory in {}: {error}", copies.display())))?;
    let (from, to) = (root.clone(), staged.path().to_path_buf());
    tokio::task::spawn_blocking(move || copy_parts(&from, &to))
        .await
        .map_err(|error| Refusal::internal(format!("the copy of {} did not finish: {error}", root.display())))?
        .map_err(|error| {
            copy_failed(format!(
                "cannot copy the pinned Apple container install {} to {}: {error}; free space on the volume of the \
                 runtime root, or name an installed CLI in CONTAINER_BIN",
                root.display(),
                staged.path().display()
            ))
        })?;
    for part in SIGNED_PARTS {
        verify_signature(ctx, runner, codesign, &staged.path().join(part)).await?;
    }
    let encoded = serde_json::to_vec_pretty(&source).map_err(|error| Refusal::internal(format!("cannot encode {COPY_RECORD}: {error}")))?;
    avl_base::fs::write_atomically(&staged.path().join(COPY_RECORD), &encoded, 0o600)?;
    publish(staged.path(), &copy, copies, &name, &source).map_err(|error| {
        copy_failed(format!(
            "cannot move the copy {} into place at {}: {error}",
            staged.path().display(),
            copy.display()
        ))
    })?;
    Ok(copy.join(relative))
}

/// Renames `staged` to `copy`. A copy there that another command made of the same source stays, and `staged` goes
/// with its temporary directory. A copy of another source is moved aside first and removed.
fn publish(staged: &Path, copy: &Path, copies: &Path, name: &str, source: &CopyRecord) -> io::Result<()> {
    match fs::rename(staged, copy) {
        Ok(()) => return Ok(()),
        Err(error) if fs::symlink_metadata(copy).is_err() => return Err(error),
        Err(_) => {}
    }
    if CopyRecord::read(copy).as_ref() == Some(source) {
        return Ok(());
    }
    let aside = tempfile::Builder::new()
        .prefix(&format!(".{name}-"))
        .suffix(".old")
        .tempdir_in(copies)?;
    // A directory renames onto an empty directory, so the old copy takes the place of `aside`, which its drop removes.
    fs::rename(copy, aside.path())?;
    fs::rename(staged, copy)
}

/// Copies [`INSTALL_PARTS`] of `root` into `destination`, which exists.
fn copy_parts(root: &Path, destination: &Path) -> io::Result<()> {
    for part in INSTALL_PARTS {
        copy_tree(&root.join(part), &destination.join(part))?;
    }
    Ok(())
}

/// Copies the tree `source` to `destination`, which must not exist: a file is cloned or copied with its mode and its
/// extended attributes ([`fscopy::clone_or_copy`]), a link is copied as a link, and a directory keeps its mode with
/// the bits of the owner added, so the prune can remove the copy of a read-only pin.
fn copy_tree(source: &Path, destination: &Path) -> io::Result<()> {
    fs::create_dir(destination)?;
    for entry in fs::read_dir(source)? {
        let entry = entry?;
        let (from, to) = (entry.path(), destination.join(entry.file_name()));
        let kind = entry.file_type()?;
        if kind.is_symlink() {
            let is_directory = fs::metadata(&from).is_ok_and(|target| target.is_dir());
            fscopy::symlink(&fs::read_link(&from)?, &to, is_directory)?;
        } else if kind.is_dir() {
            copy_tree(&from, &to)?;
        } else {
            fscopy::clone_or_copy(&from, &to)?;
        }
    }
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt as _;
        let mode = fs::metadata(source)?.permissions().mode() | 0o700;
        fs::set_permissions(destination, fs::Permissions::from_mode(mode))?;
    }
    Ok(())
}

async fn verify_signature(ctx: &Ctx, runner: &Runner, codesign: &str, path: &Path) -> Result<(), Refusal> {
    let argv = vec![
        codesign.to_owned(),
        "--verify".to_owned(),
        "--strict".to_owned(),
        path.to_string_lossy().into_owned(),
    ];
    let captured = match runner.capture(ctx, &argv, &SpawnOptions::within(CODESIGN_TIMEOUT)).await {
        Ok(captured) => captured,
        Err(ProcError::SpawnFailed(refusal)) => {
            return Err(copy_failed(format!(
                "cannot run {codesign} to verify the copy {}: {}",
                path.display(),
                refusal.message
            )));
        }
        Err(other) => return Err(other.into()),
    };
    if captured.exit_code == 0 {
        return Ok(());
    }
    Err(copy_failed(format!(
        "the copy {} fails `codesign --verify --strict` with exit {}: {}; the copy was not used. Check the pin with \
         `codesign --verify --strict` of the same file in the pinned install, or name an installed CLI in \
         CONTAINER_BIN",
        path.display(),
        captured.exit_code,
        captured.stderr.trim()
    )))
}

/// Removes from the directory of `keep` every other copy, and every temporary directory of a copy older than
/// [`STALE_TEMPORARY`]. Only the server of `keep` runs, so no other copy serves the session. Answers the names it
/// removed and the failures, as notes; a failure removes nothing more than it could.
pub(crate) fn prune_other_copies(keep: &Path) -> Vec<String> {
    let (Some(copies), Some(kept)) = (keep.parent(), keep.file_name()) else {
        return Vec::new();
    };
    let Ok(entries) = fs::read_dir(copies) else {
        return Vec::new();
    };
    let now = SystemTime::now();
    let mut notes = Vec::new();
    for entry in entries.flatten() {
        let name = entry.file_name();
        if name == kept {
            continue;
        }
        let text = name.to_string_lossy();
        if text.starts_with('.') {
            let temporary = text.ends_with(".tmp") || text.ends_with(".old");
            let stale = entry
                .metadata()
                .and_then(|metadata| metadata.modified())
                .is_ok_and(|modified| now.duration_since(modified).unwrap_or_default() > STALE_TEMPORARY);
            if !(temporary && stale) {
                continue;
            }
        }
        let path = entry.path();
        match fs::remove_dir_all(&path) {
            Ok(()) => notes.push(format!(
                "removed {}, a copy of the Apple container install that no server uses",
                path.display()
            )),
            Err(error) => notes.push(format!("cannot remove the old Apple container copy {}: {error}", path.display())),
        }
    }
    notes
}

fn copy_failed(message: String) -> Refusal {
    Refusal::new("container_copy_failed", Exit::UNAVAILABLE, message)
}
