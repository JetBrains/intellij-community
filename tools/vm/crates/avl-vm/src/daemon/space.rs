//! Guest disk safety: a run fails before it writes rather than part-way through, and bounds what the last runs left.

use std::time::Duration;

use avl_base::format::words;
use avl_base::{Exit, Refusal, Scope};
use avl_host_sys::SpawnOptions;
use avl_host_sys::guest::{GUEST_COMMAND_TIMEOUT, Guest, guest_join};

use crate::daemon::host::Host;

/// The timeout of one `rm -rf` of a chunk of pruned guest artifacts. A chunk holds whole run directories, each with
/// its traces and its IDE logs.
const PRUNE_TIMEOUT: Duration = Duration::from_mins(10);

#[cfg(test)]
#[cfg(unix)]
mod tests;

const GIB: u64 = 1024 * 1024 * 1024;

/// Below this much free space the run is refused: no staging, no iteration, nothing that writes.
pub(crate) const GUEST_FREE_SPACE_FLOOR_BYTES: u64 = 5 * GIB;

/// Below this much free space retention runs before the iteration does, and says so.
///
/// The floor alone was never a floor for what it guarded. The observed failure was `ENOSPC` *during* staging, with
/// several gigabytes free when the check passed and none left by the time the copy finished - so the question is
/// not "is there space right now" but "is there room for what this run is about to write". Twenty gigabytes is
/// roughly a staged generation plus a lane's artifacts, and the cost of being wrong on the high side is deleting
/// run artifacts nobody was going to read.
pub(crate) const GUEST_FREE_SPACE_SOFT_BYTES: u64 = 20 * GIB;

/// How many of the most recent launch directories survive a retention pass under each reclaimable tree.
pub(crate) const GUEST_ARTIFACT_RETAINED_RUNS: usize = 2;

/// The artifact trees that grow without bound on a worker and belong to nobody.
///
/// IDE Starter copies every run's logs, screenshots and hierarchy dumps into `teamcity-artifacts-for-publish` for a
/// CI publisher to collect. No publisher exists on a local worker, so nothing ever collects or clears it: on
/// 2026-08-16 it held 54 GB of a 123 GB disk while the eleven real launch directories came to under 4 GB between
/// them. Above [`GUEST_FREE_SPACE_SOFT_BYTES`] these are left alone, so a run on a roomy worker deletes nothing.
pub(crate) const GUEST_RECLAIMABLE_ARTIFACT_DIRS: [&str; 2] =
    ["out/ide-tests/tests/teamcity-artifacts-for-publish", "out/ide-tests/allure"];

/// How many paths one `rm` names. These trees accumulate for months unattended, and a `rm` whose argv outgrew the
/// exec channel would fail precisely on the worker that needed it most.
const REMOVAL_CHUNK: usize = 50;

/// The `Available` column of `df -k`, in bytes, or `None` when the output is not what is expected.
///
/// `None` means "do not know", and every caller treats that as "do not block the run": a `df` that cannot be parsed
/// is not evidence of a full disk, and refusing to run on it would be worse than the failure being guarded. That is
/// policy - a `None` from here must never become a refusal.
pub(crate) fn parse_guest_free_bytes(df_stdout: &str) -> Option<u64> {
    let line = df_stdout.trim_ascii().lines().last()?;
    // Filesystem, 1024-blocks, Used, Available: a header-only or truncated reply has no fourth column.
    let available = line.split_ascii_whitespace().nth(3)?;
    if !available.bytes().all(|byte| byte.is_ascii_digit()) {
        return None;
    }
    available.parse::<u64>().ok()?.checked_mul(1024)
}

/// The free bytes on the guest volume holding the worker's data root, or `None` when `df` says something
/// unparsable.
pub(crate) async fn guest_free_bytes(guest: &Guest<'_>) -> Result<Option<u64>, Refusal> {
    let argv = words(["/bin/df", "-k", &guest.settings.vm_data]);
    let captured = guest.as_user(&argv, &SpawnOptions::within(GUEST_COMMAND_TIMEOUT)).await?;
    Ok(parse_guest_free_bytes(&captured.stdout))
}

/// A byte count the way every free-space message spells it.
fn gibibytes(bytes: u64) -> String {
    format!("{:.1} GiB", bytes as f64 / GIB as f64)
}

impl Host {
    /// Deletes all but the `retain` newest entries of each reclaimable tree, answering how many were doomed.
    ///
    /// The host decides what goes, from a listing, rather than sending a shell pipeline: the `rm` it then issues
    /// names every path explicitly, so the worker's own call log says exactly what was deleted. `ls -1t` is
    /// newest-first by mtime on both guests, and a tree that does not exist yet lists as nothing - the same answer
    /// as having nothing to prune.
    ///
    /// `retain` is two rather than zero wherever there is a choice, so a post-mortem on the last failure is still
    /// possible; only the refuse-to-run gate, where the alternative is not running at all, keeps nothing.
    pub(crate) async fn prune_guest_artifacts(&self, guest: &Guest<'_>, retain: usize, scope: Option<&Scope>) -> Result<usize, Refusal> {
        let mut doomed: Vec<String> = Vec::new();
        for relative in GUEST_RECLAIMABLE_ARTIFACT_DIRS {
            let tree = guest_join(&self.settings.vm_data, relative);
            let Ok(listed) = guest
                .as_user(&words(["/bin/ls", "-1t", &tree]), &SpawnOptions::within(GUEST_COMMAND_TIMEOUT))
                .await
            else {
                continue;
            };
            doomed.extend(
                listed
                    .stdout
                    .lines()
                    .map(str::trim_ascii)
                    .filter(|entry| !entry.is_empty())
                    .skip(retain)
                    .map(|entry| guest_join(&tree, entry)),
            );
        }
        if doomed.is_empty() {
            return Ok(0);
        }
        self.reporter.note(
            format!(
                "pruning {} stale run artifact tree(s), keeping the {retain} newest of each",
                doomed.len()
            ),
            scope,
        );
        for chunk in doomed.chunks(REMOVAL_CHUNK) {
            let mut argv = words(["/bin/rm", "-rf"]);
            argv.extend_from_slice(chunk);
            guest.as_user(&argv, &SpawnOptions::within(PRUNE_TIMEOUT)).await?;
        }
        Ok(doomed.len())
    }

    /// Fails before writing rather than part-way through, and bounds what the last runs left.
    ///
    /// A full guest surfaces as `ENOSPC` from a copy deep inside the stager, and from there even `exec` stops
    /// working, because it writes a script into the guest before it can run anything. Two thresholds, because the
    /// failure that motivated this happened with the floor intact: below [`GUEST_FREE_SPACE_SOFT_BYTES`] retention
    /// runs eagerly, and only below [`GUEST_FREE_SPACE_FLOOR_BYTES`] is the run refused. Above the soft threshold
    /// the whole thing costs one `df` and deletes nothing.
    pub(crate) async fn require_guest_free_space(&self, guest: &Guest<'_>, scope: Option<&Scope>) -> Result<(), Refusal> {
        let Some(before) = guest_free_bytes(guest).await? else {
            return Ok(());
        };
        if before >= GUEST_FREE_SPACE_SOFT_BYTES {
            return Ok(());
        }
        // At the floor a post-mortem is a luxury: keeping two launches is also the one thing that cannot free the
        // disk when those two launches are themselves what filled it, and then no run would ever start again.
        let retain = if before < GUEST_FREE_SPACE_FLOOR_BYTES {
            0
        } else {
            GUEST_ARTIFACT_RETAINED_RUNS
        };
        let worker = guest.worker();
        self.reporter.note(
            format!(
                "{worker} has {} free, below the {} a run wants; retaining {retain} launch(es) of unpublished run \
                 artifacts",
                gibibytes(before),
                gibibytes(GUEST_FREE_SPACE_SOFT_BYTES)
            ),
            scope,
        );
        self.prune_guest_artifacts(guest, retain, scope).await?;

        let after = guest_free_bytes(guest).await?;
        let vm_data = &self.settings.vm_data;
        if let Some(after) = after
            && after < GUEST_FREE_SPACE_FLOOR_BYTES
        {
            // A resource the run cannot reach, not a wire the controller cannot read: 69, and never the 70 that
            // `daemon_died` carries, or a caller branching on the code would re-run into the same disk.
            return Err(Refusal::new(
                "guest_disk_full",
                Exit::UNAVAILABLE,
                format!(
                    "{worker} has {} free under {vm_data}, below the {} a run needs, and the reclaimable artifact \
                     trees are already gone. Find what is holding the space with `prlctl exec {worker} \"du -sh \
                     {vm_data}/* | sort -rh | head\"`.",
                    gibibytes(after),
                    gibibytes(GUEST_FREE_SPACE_FLOOR_BYTES)
                ),
            ));
        }
        let settled = after.unwrap_or(before);
        self.reporter.note(
            format!(
                "{worker} reclaimed {}, now {} free",
                gibibytes(settled.saturating_sub(before)),
                gibibytes(settled)
            ),
            scope,
        );
        Ok(())
    }
}
