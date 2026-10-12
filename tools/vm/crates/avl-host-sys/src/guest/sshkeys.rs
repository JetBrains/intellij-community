//! SSH host-key uniqueness across the workers of one pool.

use std::sync::{Arc, LazyLock};
use std::time::Duration;

use async_trait::async_trait;
use avl_base::format::words;
use avl_base::{Exit, Refusal};
use regex::Regex;

use super::{GUEST_COMMAND_TIMEOUT, Guest};
use crate::ctx::Ctx;
use crate::proc::{Channel, SpawnOptions};

/// The timeout of the host-key script. It runs `ssh-keygen` for the host keys of a fresh clone, about a second of work.
const HOST_KEY_TIMEOUT: Duration = Duration::from_mins(2);

#[cfg(test)]
mod tests;

/// A channel into another worker of this pool, or `None` when that worker is not running.
///
/// A trait rather than a hypervisor handle, because reaching a *second* worker is the only thing in this module
/// that is not about the worker in front of it, and what "running" means is the backend's question: a Tart worker
/// is a host process the controller owns, a Parallels worker is a VM it does not. `None` for a worker that is not
/// up, because a stopped peer cannot share an identity with anything.
#[async_trait]
pub trait PeerChannels: Send + Sync {
    async fn peer(&self, ctx: &Ctx, worker: &str) -> Result<Option<Arc<dyn Channel>>, Refusal>;
}

/// The fingerprint a worker reported about itself.
///
/// The last line, not the first: the guest script prints what it did before it prints the answer. The shape is
/// checked rather than trusted because the comparison this feeds is an equality test - two workers that both
/// answered an empty string would compare equal and be reported as sharing an identity they do not have.
pub fn parse_ssh_host_key_fingerprint(worker: &str, value: &str) -> Result<String, Refusal> {
    static FINGERPRINT: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"^SHA256:[A-Za-z0-9+/=]+$").expect("a constant pattern compiles"));
    let fingerprint = value.trim().lines().last().unwrap_or_default().trim();
    if FINGERPRINT.is_match(fingerprint) {
        return Ok(fingerprint.to_owned());
    }
    Err(Refusal::new(
        "ssh_host_key_invalid",
        Exit::FAILURE,
        format!("worker {worker} did not report a valid SSH host-key fingerprint"),
    ))
}

impl Guest<'_> {
    /// Gives this worker an SSH host identity of its own, and refuses if a running peer already has it.
    ///
    /// Two macOS workers are copy-on-write clones of one sealed image, so they start life sharing the golden's host
    /// keys - and `tart exec` reaches a guest over SSH, so two workers with one identity are two workers a host key
    /// check cannot tell apart. The guest script regenerates the keys and writes a readiness marker holding the
    /// fingerprint; the peers are then *read* rather than regenerated, because a peer with a live run must not have
    /// its host identity changed underneath it.
    ///
    /// A peer that is running but has no marker is a refusal, not a skip: that worker is up without having been
    /// through this step, so nothing has established that the two differ.
    pub async fn ensure_unique_ssh_host_keys(&self, peers: &dyn PeerChannels) -> Result<String, Refusal> {
        let settings = self.settings;
        let worker = self.worker();
        let generated = self
            .as_root(
                &words(["/usr/local/sbin/air-ensure-ssh-host-keys", &settings.vm_ssh_host_key_fingerprint]),
                &SpawnOptions::within(HOST_KEY_TIMEOUT),
            )
            .await?;
        let fingerprint = parse_ssh_host_key_fingerprint(worker, &generated.stdout)?;
        for name in settings.workers.iter().filter(|name| *name != worker) {
            let Some(peer) = peers.peer(self.ctx, name).await? else {
                continue;
            };
            // As root and with no `-H`: the marker is root-owned and this reads one file. Kept exactly this shape
            // because the guest's sudoers rule is written for this argv.
            let answer = peer
                .exec(
                    self.ctx,
                    &words(["/usr/bin/sudo", "/bin/cat", &settings.vm_ssh_host_key_fingerprint]),
                    &SpawnOptions::within(GUEST_COMMAND_TIMEOUT),
                )
                .await?;
            if answer.exit_code != 0 {
                return Err(Refusal::new(
                    "ssh_host_key_peer_not_ready",
                    Exit::FAILURE,
                    format!("running worker {name} has no SSH host-key readiness marker"),
                ));
            }
            if parse_ssh_host_key_fingerprint(name, &answer.stdout)? == fingerprint {
                return Err(Refusal::new(
                    "ssh_host_key_collision",
                    Exit::FAILURE,
                    format!("workers {worker} and {name} share an SSH host identity"),
                ));
            }
        }
        Ok(fingerprint)
    }
}
