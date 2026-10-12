//! Building the guest runfiles tree of a host MANIFEST: the host half of the `runfiles-tree` verb.

use std::time::Duration;

use avl_base::{Exit, OrRefuse, Refusal};
use avl_wire::runfiles::{RunfilesTreeRequest, RunfilesTreeResult, SCHEMA_VERSION};
use avl_wire::supervisor::ReceivedEnvelope;
use avl_wire::verb::AgentVerb;

use super::Guest;
use super::supervisor::AgentAccount;
use crate::paths::GuestPaths;
use crate::proc::SpawnOptions;
use crate::runfiles::{HostRunfiles, guest_runfiles_destination};

#[cfg(test)]
mod tests;

/// How long the guest may take to build one tree. It makes one link for each MANIFEST line, and a lane has tens
/// of thousands of lines.
const TREE_TIMEOUT: Duration = Duration::from_mins(5);

impl Guest<'_> {
    /// Makes the guest runfiles root that [`HostRunfiles::guest_root`] named, and refuses a guest that answers
    /// another root.
    ///
    /// A tree needs nothing: the guest opens it through its share. A MANIFEST is sent to the `runfiles-tree` verb,
    /// which builds the tree or reuses the tree of the same digest. The verb keeps that tree and the one used before
    /// it, and removes every other entry of the destination.
    pub async fn ensure_runfiles_tree(&self, runfiles: &HostRunfiles, expected_root: &str) -> Result<(), Refusal> {
        let HostRunfiles::Manifest { path, .. } = runfiles else {
            return Ok(());
        };
        let settings = self.settings;
        let paths = GuestPaths::of(settings)?;
        let request = RunfilesTreeRequest {
            schema_version: SCHEMA_VERSION,
            manifest: paths.to_guest(path)?,
            path_map: paths.map().clone(),
            destination: guest_runfiles_destination(settings),
        };
        let stdin = serde_json::to_vec(&request).or_refuse("internal_error", Exit::FAILURE, || {
            "cannot encode the runfiles tree request".to_owned()
        })?;
        let options = SpawnOptions {
            stdin: Some(stdin),
            ..SpawnOptions::timeout(TREE_TIMEOUT, "guest_runfiles_timeout")
        };
        let stdout = self
            .invoke_agent(AgentAccount::Worker, AgentVerb::RunfilesTree, &[], &options)
            .await?;
        let invalid = |detail: String| {
            Refusal::new(
                "guest_runfiles_protocol",
                Exit::SOFTWARE,
                format!("{} answered runfiles-tree with {detail}", self.worker()),
            )
        };
        let data = ReceivedEnvelope::read(&stdout)
            .map_err(|error| invalid(format!("invalid JSON: {error}")))?
            .data
            .ok_or_else(|| invalid("no data".to_owned()))?;
        let result: RunfilesTreeResult =
            serde_json::from_str(data.get()).map_err(|error| invalid(format!("an unexpected document: {error}")))?;
        if result.root != expected_root {
            return Err(Refusal::new(
                "guest_runfiles_mismatch",
                Exit::SOFTWARE,
                format!(
                    "{} built the runfiles tree at {}, and this controller expects {expected_root}",
                    self.worker(),
                    result.root
                ),
            ));
        }
        // The verb keeps this tree and the one used before it, and removes the rest: the gc of this directory.
        if !result.removed.is_empty() {
            self.reporter.note(
                format!("removed {} old runfiles trees in {}", result.removed.len(), self.worker()),
                Some(&self.scope()),
            );
        }
        let provenance = if result.reused { "reused" } else { "built" };
        self.reporter.note(
            format!(
                "{provenance} the runfiles tree {} of {} runfiles in {}",
                result.root,
                result.entries,
                self.worker()
            ),
            Some(&self.scope()),
        );
        Ok(())
    }
}
