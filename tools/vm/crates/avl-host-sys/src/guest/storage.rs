//! A worker's writable storage, the Linux boot's guest-agent verb, and the readiness a run needs.

use std::time::Duration;

use avl_base::format::words;
use avl_base::{Backend, Exit, Refusal, Reporter, Scope};
use avl_wire::verb::AgentVerb;
use serde::Deserialize;
use serde_json::value::RawValue;

use super::agent::BazelHost;
use super::sshkeys::PeerChannels;
use super::supervisor::AgentAccount;
use super::{GUEST_COMMAND_TIMEOUT, Guest, chown_argv, guest_join};
use crate::proc::SpawnOptions;

/// The timeout of `validate-guest`. It checks the display, the packages and the accounts, seconds of work.
const VALIDATE_TIMEOUT: Duration = Duration::from_mins(5);

/// The timeout of the recursive `chown` of the data root. The root holds the staged runtimes, so the walk visits
/// every file of them.
const DATA_CHOWN_TIMEOUT: Duration = Duration::from_mins(10);

/// The timeout of the storage script. It grows the APFS container of the data volume with `diskutil`.
const STORAGE_INIT_TIMEOUT: Duration = Duration::from_mins(10);

#[cfg(test)]
mod tests;

/// Puts one boot verb's own report where a person can read it, and is the only thing that does.
///
/// The verbs answer a document rather than lines, and their streams do not travel: [`Guest::invoke_agent`] captures
/// both and echoes neither, deliberately. So the fields that argue for their own visibility, such as the window
/// manager's probe count, reach a boot's log through here.
///
/// The `data` half whole, with no struct for it on this side: a copy of the guest's report types here would be the
/// declaration that goes quietly stale, dropping a renamed field out of a note rather than failing to compile.
///
/// A success this cannot read is not an error. The verb exited 0 and that is the contract; this is a progress line.
fn note_verb_report(reporter: &Reporter, worker: &str, verb: AgentVerb, reply: &str) {
    #[derive(Deserialize)]
    struct Reply {
        /// A `data: null` is noted as what it said; only an absent `data` is nothing to note.
        #[serde(default, deserialize_with = "present")]
        data: Option<Box<RawValue>>,
    }
    fn present<'de, D: serde::Deserializer<'de>>(deserializer: D) -> Result<Option<Box<RawValue>>, D::Error> {
        Box::<RawValue>::deserialize(deserializer).map(Some)
    }
    let Some(data) = serde_json::from_str::<Reply>(reply).ok().and_then(|reply| reply.data) else {
        return;
    };
    reporter.note(format!("{verb} reported {}", data.get()), Some(&Scope::worker(worker)));
}

impl Guest<'_> {
    /// Runs one guest-agent verb of a boot and notes what it answered. The one spelling of "run a boot verb", so no
    /// verb can be the one whose report is dropped.
    ///
    /// The account is the caller's. `validate-guest` runs as root.
    pub(crate) async fn invoke_boot_verb(
        &self,
        account: AgentAccount,
        verb: AgentVerb,
        args: &[String],
        timeout: Duration,
    ) -> Result<(), Refusal> {
        let reply = self.invoke_agent(account, verb, args, &SpawnOptions::within(timeout)).await?;
        note_verb_report(self.reporter, self.worker(), verb, &reply);
        Ok(())
    }

    /// Brings a Linux worker to the state a run needs, on every boot: the worker storage, the guest agent, and then
    /// `validate-guest` with `validate_argv`.
    ///
    /// The Docker image installs the packages and the pinned Node, and its entrypoint starts the display. So the boot
    /// ends with the guest proving itself: everything `validate-guest` checks was once discovered from `idea.log` days
    /// later. The verb goes through [`Guest::invoke_agent`] rather than [`Guest::as_root`], so it answers a named
    /// refusal and not an exit integer.
    ///
    /// It *begins* with [`Guest::install_agent`], because the verb is a verb of that binary. The agent's destination
    /// lives under the state directory created just before it, so the install cannot come earlier either. That
    /// ordering is also this boot's trust boundary: the worker account writes the binary and root then executes it,
    /// on a guest that is disposable and holds no credentials.
    pub async fn provision_linux(&self, bazel: &dyn BazelHost, validate_argv: &[String]) -> Result<(), Refusal> {
        // Refused before the guest is touched rather than skipped, because a skipped self-check is invisible in
        // exactly the way the self-check exists to end.
        if validate_argv.is_empty() {
            return Err(Refusal::new(
                "linux_validation_unset",
                Exit::FAILURE,
                format!("worker {} was given no guest self-check to run after provisioning", self.worker()),
            ));
        }
        let settings = self.settings;
        let state = guest_join(&settings.vm_data, "state");
        self.as_root(&words(["/bin/mkdir", "-p", &state]), &SpawnOptions::within(GUEST_COMMAND_TIMEOUT))
            .await?;
        // Root makes the directory; the worker user writes into it.
        if let Some(chown) = chown_argv(settings, &["-R"], &[&settings.vm_data]) {
            self.as_root(&chown, &SpawnOptions::within(DATA_CHOWN_TIMEOUT)).await?;
        }
        self.install_agent(bazel).await?;
        self.invoke_boot_verb(AgentAccount::Root, AgentVerb::ValidateGuest, validate_argv, VALIDATE_TIMEOUT)
            .await
    }

    /// Grows a freshly cloned macOS worker's APFS container to its root-disk size and creates the directories it
    /// owns. Runs once per boot, before anything else touches the guest's filesystem; the guest script is idempotent.
    ///
    /// `root_disk_gb` is the worker's *actual* disk and not the configured default for a new one: the script's
    /// safety gate is that `/` is a single internal APFS store of exactly the size it was told.
    pub async fn ensure_worker_storage(&self, root_disk_gb: u32) -> Result<(), Refusal> {
        let settings = self.settings;
        let bytes = (u64::from(root_disk_gb) * 1_000_000_000).to_string();
        self.as_root(
            &words([
                "/usr/local/sbin/air-init-worker-storage",
                &bytes,
                &settings.vm_data,
                &settings.vm_user,
            ]),
            &SpawnOptions::within(STORAGE_INIT_TIMEOUT),
        )
        .await?;
        if !self
            .succeeds(&words(["/bin/test", "-d", &settings.vm_data]), GUEST_COMMAND_TIMEOUT)
            .await
        {
            return Err(Refusal::new(
                "worker_storage_missing",
                Exit::FAILURE,
                format!("worker {} did not initialize {}", self.worker(), settings.vm_data),
            ));
        }
        self.remove_retired_build_directories().await;
        Ok(())
    }

    /// Deletes the guest-build era's directories from a worker, if its image still makes them.
    ///
    /// A worker has no checkout, no Bazel output root and no Bazel disk cache, but the sealed golden still carries
    /// an initializer that makes them. `rmdir`, never `rm -r`: a non-empty directory means something does use it,
    /// which is a discovery to make loudly later rather than a tree to delete now. Failures are ignored: this is
    /// tidying, and a worker that would not give up a directory is still a worker a run can use.
    async fn remove_retired_build_directories(&self) {
        for name in ["checkout", "bazel-output", "bazel-disk-cache"] {
            let path = guest_join(&self.settings.vm_data, name);
            if !self.succeeds(&words(["/bin/test", "-d", &path]), GUEST_COMMAND_TIMEOUT).await {
                continue;
            }
            if self.succeeds(&words(["/bin/rmdir", &path]), GUEST_COMMAND_TIMEOUT).await {
                self.reporter.note(
                    format!("removed the retired {name} directory from {}", self.worker()),
                    Some(&self.scope()),
                );
            }
        }
    }

    /// Brings the guest to the state a run needs: its writable storage, and the parity layout over the two
    /// read-only shares. One path for both backends, because everything below the mount behaves identically once
    /// the shares are there.
    ///
    /// Tart plus macOS adds the SSH identity check: two macOS workers are clones of one sealed image, and `tart exec`
    /// reaches the guest over SSH. Nothing here reaches a Docker worker over SSH, so it needs no such check.
    pub async fn ensure_ready(&self, peers: &dyn PeerChannels) -> Result<(), Refusal> {
        let settings = self.settings;
        if settings.backend == Backend::Tart && settings.is_macos_guest() {
            if !self
                .succeeds(&words(["/bin/test", "-d", &settings.vm_data]), GUEST_COMMAND_TIMEOUT)
                .await
            {
                return Err(Refusal::new(
                    "worker_storage_missing",
                    Exit::FAILURE,
                    format!(
                        "worker {} has no {}; its storage initializer did not run at boot",
                        self.worker(),
                        settings.vm_data
                    ),
                ));
            }
            self.ensure_unique_ssh_host_keys(peers).await?;
        }
        self.ensure_parity_ready().await?;
        let roots = [settings.vm_data.as_str(), &settings.vm_runs_root];
        let mut mkdir = words(["/bin/mkdir", "-p"]);
        mkdir.extend(roots.iter().map(|root| (*root).to_owned()));
        self.as_root(&mkdir, &SpawnOptions::within(GUEST_COMMAND_TIMEOUT)).await?;
        if let Some(chown) = chown_argv(settings, &[], &roots) {
            self.as_root(&chown, &SpawnOptions::within(GUEST_COMMAND_TIMEOUT)).await?;
        }
        Ok(())
    }
}
