//! The calls of Branch Deck (`bd`) that keep the lease of a holder. Each call runs in `/` with stdin `/dev/null`, as
//! `remote.sh` of BT runs them: bd must not take the directory of the agent for a repository, and it must not wait
//! for an input.

use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::Duration;

use serde::Deserialize;

use crate::child::{self, Stop};

/// The deadline of `bd lease get`. A new slot can need a clone and a checkout of the monorepo.
const GET: Duration = Duration::from_secs(30 * 60);

/// The deadline of `bd lease status`.
const STATUS: Duration = Duration::from_secs(60);

/// The deadline of `bd lease return`. It can stop a Bazel server and clean the slot.
const RETURN: Duration = Duration::from_secs(10 * 60);

/// A lease that `bd lease get` gave.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Leased {
    pub(crate) path: PathBuf,
    pub(crate) lease_id: String,
}

fn bd() -> Command {
    let mut command = Command::new("bd");
    command.current_dir("/");
    command
}

/// Takes or reuses the lease of `holder` in the pool of `repo`. `stop` kills each call of this module early.
pub(crate) fn lease_get(repo: &Path, holder: &str, stop: Stop<'_>) -> Result<Leased, String> {
    let output =
        child::run(bd().args(["lease", "get", "--holder", holder, "-C"]).arg(repo), GET, stop).map_err(|error| error.to_string())?;
    if !output.status.success() {
        return Err(format!(
            "bd lease get failed in {}: {} ({})",
            repo.display(),
            output.diagnostic(),
            output.status
        ));
    }
    let text = output.stdout_text();
    parse_get(&text).ok_or_else(|| format!("bd lease get answered no lease: {text}"))
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

/// The lease of the JSON line of `bd lease get`.
fn parse_get(text: &str) -> Option<Leased> {
    #[derive(Deserialize)]
    struct Get {
        path: Option<PathBuf>,
        lease_id: Option<String>,
    }
    let get: Get = serde_json::from_str(text.lines().last()?).ok()?;
    match (get.path, get.lease_id) {
        (Some(path), Some(lease_id)) if !path.as_os_str().is_empty() && !lease_id.is_empty() => Some(Leased { path, lease_id }),
        _ => None,
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
