//! Guest disk safety: a run fails before it writes rather than part-way through, and a start removes the roots that an
//! older controller left.

use std::time::Duration;

use avl_base::format::words;
use avl_base::{Exit, Refusal, Scope};
use avl_host_sys::SpawnOptions;
use avl_host_sys::guest::{GUEST_COMMAND_TIMEOUT, Guest, guest_join};

use crate::daemon::host::Host;

/// The timeout of the one `rm -rf` of the retired roots. On a worker that an older controller used for months they
/// held tens of gigabytes of run artifacts.
const REMOVAL_TIMEOUT: Duration = Duration::from_mins(10);

#[cfg(test)]
#[cfg(unix)]
mod tests;

const GIB: u64 = 1024 * 1024 * 1024;

/// Below this much free space the run is refused: no daemon start, no iteration, nothing that writes.
pub(crate) const GUEST_FREE_SPACE_FLOOR_BYTES: u64 = 5 * GIB;

/// The retired roots below the guest data directory, which an older controller wrote and nothing reads now.
///
/// - `daemon-runtime` held one staged runtime generation for each runtime digest: the stable classpath tier and a copy
///   of the JBR.
/// - `out` was the output tree of a run. IDE Starter copied the logs, the screenshots and the hierarchy dumps of
///   every run into `out/ide-tests` for a CI publisher that no worker has.
/// - `build-download` was the download cache of a guest build.
///
/// A persistent disk keeps them: the Docker volume across a recreate, and a Tart or Parallels disk until a recycle.
/// So each start removes them.
pub(crate) const RETIRED_ROOTS: [&str; 3] = ["daemon-runtime", "out", "build-download"];

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
    /// Removes the [`RETIRED_ROOTS`] of the guest with one `rm -rf`, as the guest user, who owns them.
    ///
    /// The start calls it before its free-space check, so the check counts the space that they held. A missing
    /// directory is no error. A failed removal is a note and not a refusal, because the free-space check that follows
    /// decides whether the start can go on.
    pub(crate) async fn remove_retired_roots(&self, guest: &Guest<'_>, scope: Option<&Scope>) {
        let roots: Vec<String> = RETIRED_ROOTS.iter().map(|root| guest_join(&self.settings.vm_data, root)).collect();
        let mut argv = words(["/bin/rm", "-rf"]);
        argv.extend_from_slice(&roots);
        let removed = guest.as_user(&argv, &SpawnOptions::within(REMOVAL_TIMEOUT)).await;
        if let Err(refusal) = removed {
            self.reporter.note(
                format!(
                    "{} keeps the retired roots {}: the removal failed: {}",
                    guest.worker(),
                    roots.join(", "),
                    refusal.message
                ),
                scope,
            );
        }
    }

    /// Fails before writing rather than part-way through.
    ///
    /// A full guest surfaces as `ENOSPC` from a copy deep inside a guest verb, and from there even `exec` stops
    /// working, because it writes a script into the guest before it can run anything. So below
    /// [`GUEST_FREE_SPACE_FLOOR_BYTES`] the run is refused. The check costs one `df` and deletes nothing.
    pub(crate) async fn require_guest_free_space(&self, guest: &Guest<'_>) -> Result<(), Refusal> {
        let Some(free) = guest_free_bytes(guest).await? else {
            return Ok(());
        };
        if free >= GUEST_FREE_SPACE_FLOOR_BYTES {
            return Ok(());
        }
        let worker = guest.worker();
        let vm_data = &self.settings.vm_data;
        // A resource the run cannot reach, not a wire the controller cannot read: 69, and never the 70 that
        // `daemon_died` carries, or a caller branching on the code would re-run into the same disk.
        Err(Refusal::new(
            "guest_disk_full",
            Exit::UNAVAILABLE,
            format!(
                "{worker} has {} free under {vm_data}, below the {} a run needs, and the start already removed the \
                 retired roots. Find what is holding the space with `du -sh {vm_data}/* | sort -rh | head` in the \
                 worker, through `vm.cmd exec`.",
                gibibytes(free),
                gibibytes(GUEST_FREE_SPACE_FLOOR_BYTES)
            ),
        ))
    }
}
