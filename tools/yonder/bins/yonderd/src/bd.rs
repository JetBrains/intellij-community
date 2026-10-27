//! The calls of Branch Deck (`bd`): `bd lease host`, which lends the host slots per run (`slots`), and the return of
//! the leases that holders kept before (`lease`). Each call runs in `/` with stdin `/dev/null`, as `remote.sh` of BT
//! runs them: bd must not take the directory of the agent for a repository, and it must not wait for an input.

use std::path::Path;
use std::process::Command;
use std::time::Duration;

use serde::Deserialize;

use crate::child::{self, Stop};

/// The deadline of `bd schema`.
const SCHEMA: Duration = Duration::from_secs(60);

/// The deadline of `bd lease status`.
const STATUS: Duration = Duration::from_secs(60);

/// The deadline of `bd lease return`. It can stop a Bazel server and clean the slot.
const RETURN: Duration = Duration::from_secs(10 * 60);

fn bd() -> Command {
    let mut command = Command::new("bd");
    command.current_dir("/");
    command
}

/// `bd lease host <args> -C <repo>` within `deadline`; its stdout when it succeeded. `stop` kills it early.
pub(crate) fn host(repo: &Path, args: &[&str], deadline: Duration, stop: Stop<'_>) -> Result<String, String> {
    let output =
        child::run(bd().args(["lease", "host"]).args(args).arg("-C").arg(repo), deadline, stop).map_err(|error| error.to_string())?;
    if output.status.success() {
        Ok(output.stdout_text())
    } else {
        Err(format!(
            "bd lease host {} failed in {}: {} ({})",
            args.first().copied().unwrap_or_default(),
            repo.display(),
            output.diagnostic(),
            output.status
        ))
    }
}

/// The output of `bd schema`, `None` when it fails: the names of the outputs say what this bd can do.
pub(crate) fn schema() -> Option<String> {
    let output = child::run(bd().arg("schema"), SCHEMA, &child::never).ok()?;
    output.status.success().then(|| output.stdout_text())
}

/// Whether bd still knows the lease `lease_id` in the pool of `repo`. A failed call answers `false`, as in `remote.sh`.
pub(crate) fn lease_alive(repo: &Path, lease_id: &str, stop: Stop<'_>) -> bool {
    child::run(bd().args(["lease", "status", "-C"]).arg(repo), STATUS, stop)
        .is_ok_and(|output| output.status.success() && status_has(&output.stdout_text(), lease_id))
}

/// Returns the lease `lease_id` of the slot `slot`. `confirm_preserved` gives up the commits and the changes in it.
pub(crate) fn lease_return(slot: &Path, lease_id: &str, confirm_preserved: bool, stop: Stop<'_>) -> Result<(), String> {
    let mut command = bd();
    command.arg("-C").arg(slot).args(["lease", "return", "--lease-id", lease_id]);
    if confirm_preserved {
        command.arg("--confirm-preserved");
    }
    let output = child::run(&mut command, RETURN, stop).map_err(|error| error.to_string())?;
    if output.status.success() {
        Ok(())
    } else {
        Err(format!("{} ({})", output.diagnostic(), output.status))
    }
}

/// Whether the JSON of `bd lease status` names the lease `lease_id` in one of its slots.
fn status_has(text: &str, lease_id: &str) -> bool {
    #[derive(Deserialize)]
    struct Status {
        #[serde(default)]
        slots: Vec<Slot>,
    }
    #[derive(Deserialize)]
    struct Slot {
        lease: Option<SlotLease>,
    }
    #[derive(Deserialize)]
    struct SlotLease {
        lease_id: String,
    }
    serde_json::from_str::<Status>(text).is_ok_and(|status| {
        status
            .slots
            .iter()
            .any(|slot| slot.lease.as_ref().is_some_and(|lease| lease.lease_id == lease_id))
    })
}

#[cfg(test)]
mod tests;
