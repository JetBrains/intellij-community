//! What this controller remembers about the daemon it last started on one worker.

use std::path::PathBuf;

use avl_base::fs::write_atomically;
use avl_base::{Config, Exit, OrRefuse, Refusal};
use avl_host_sys::guest::guest_join;
use serde::{Deserialize, Serialize};

use crate::worker::secure::read_json_record;

#[cfg(test)]
#[cfg(unix)]
mod tests;

/// The daemon one worker runs, as this controller started it: the worker and the guest port it listens on, how to
/// authenticate, and the build identities it was launched and mounted for.
///
/// Every field but [`Self::last_plugins_digest`] and [`Self::agent_digest`] is required, because the next start compares
/// every one: a file missing one is no daemon at all. A record of an older controller can name the runner of its
/// daemon and a runtime digest. The reader ignores both fields, because this controller runs the lane daemon alone and
/// keeps no runtime digest. The file is two-space JSON with a trailing newline, and only this controller reads it.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct HostState {
    pub run_id: String,
    pub port: u16,
    pub token: String,
    /// The worker whose exec channel carries the relay to the daemon.
    pub worker: String,
    pub daemon_boot_stamp: String,
    pub launch_digest: String,
    pub last_product_digest: String,
    pub last_mount_digest: String,
    /// The context plugin identity of the last iteration. A record of an older controller has none, and an empty value
    /// is unknown: the next iteration decides nothing on it and records the value it ran with.
    #[serde(default)]
    pub last_plugins_digest: String,
    /// The digest of the guest agent that the start of the daemon installed or found. A lane daemon runs from the image
    /// of that agent, so a run compares the digest with the agent that the guest holds then. A record of an older
    /// controller has none, and an empty value is unknown: no run decides on it.
    #[serde(default)]
    pub agent_digest: String,
}

impl HostState {
    /// Where one worker's daemon state file lives on the host.
    pub(crate) fn path(settings: &Config, worker: &str) -> PathBuf {
        settings.worker_dir(worker).join("daemon.json")
    }

    /// The daemon this controller last recorded on one worker, or `None`.
    ///
    /// `None` covers every way of not having one - no file, unreadable JSON, a missing
    /// field - because they all mean the same thing: no compatible daemon, start a fresh one.
    pub(crate) fn read(settings: &Config, worker: &str) -> Option<Self> {
        read_json_record(&Self::path(settings, worker)).ok().flatten()
    }

    /// Records the daemon a worker now runs, privately and atomically.
    pub(crate) fn write(&self, settings: &Config, worker: &str) -> Result<(), Refusal> {
        let mut encoded = serde_json::to_string_pretty(self)
            .or_refuse("internal_error", Exit::FAILURE, || "cannot encode the daemon state".to_owned())?;
        encoded.push('\n');
        write_atomically(&Self::path(settings, worker), encoded.as_bytes(), 0o600)
    }

    /// Forgets the daemon one worker ran. Nothing to forget is not a failure.
    pub(crate) fn remove(settings: &Config, worker: &str) -> Result<(), Refusal> {
        match std::fs::remove_file(Self::path(settings, worker)) {
            Err(error) if error.kind() != std::io::ErrorKind::NotFound => Err(Refusal::new(
                "state_write_failed",
                Exit::FAILURE,
                format!("cannot remove the daemon state for {worker}: {error}"),
            )),
            _ => Ok(()),
        }
    }
}

/// The guest directory the daemon publishes its own state file and iteration results into.
pub(crate) fn guest_state_dir(settings: &Config) -> String {
    guest_join(&settings.vm_data, "daemon")
}

/// The state file of the lane daemon in its state directory. It names the protocol, the port, the token and the pid of
/// the daemon. The start removes the file of the previous daemon before the supervisor start, and the boot poll reads
/// the file of the new daemon.
pub(crate) fn guest_state_file(settings: &Config) -> String {
    guest_join(&guest_state_dir(settings), "daemon.json")
}

/// The home of the lane daemon: a directory in its state directory. The lane IDE gets it as `idea.dev.project.root`,
/// and the boot of the lane daemon makes it.
///
/// It is not below the IDE root, because `ide-gc` and the controller read each directory there as a context of the
/// lane IDE.
pub(crate) fn guest_home(settings: &Config) -> String {
    guest_join(&guest_state_dir(settings), "home")
}
