//! Building the guest runfiles tree: the host half of the `runfiles-tree` verb.

use std::time::Duration;

use avl_base::{Exit, OrRefuse, Refusal};
use avl_wire::runfiles::{RunfilesTreeResult, STAGED_BYTES_MISSING_CODE, request_stdin};
use avl_wire::supervisor::ReceivedEnvelope;
use avl_wire::verb::AgentVerb;

use super::Guest;
use super::supervisor::AgentAccount;
use crate::proc::SpawnOptions;
use crate::runfiles::GuestRunfilesTree;

#[cfg(test)]
mod tests;

/// How long the guest may take to build one tree. It makes one link for each MANIFEST line, and a lane has tens
/// of thousands of lines.
const TREE_TIMEOUT: Duration = Duration::from_mins(5);

impl Guest<'_> {
    /// Makes the guest runfiles root that [`GuestRunfilesTree::root`] names, and refuses a guest that answers another
    /// root.
    ///
    /// The first request carries no bytes, so a tree of the same digest is reused at the cost of the MANIFEST text.
    /// A guest without that tree refuses the request, and the second request carries the bytes of every staged
    /// runfile. The verb keeps the tree it answers and the one used before it, and removes every other entry of the
    /// destination.
    pub async fn ensure_runfiles_tree(&self, tree: &GuestRunfilesTree) -> Result<(), Refusal> {
        let data = match self.request_tree(tree, false).await {
            Err(refusal) if refusal.code == STAGED_BYTES_MISSING_CODE => self.request_tree(tree, true).await?,
            answered => answered?,
        };
        let invalid = |detail: String| {
            Refusal::new(
                "guest_runfiles_protocol",
                Exit::SOFTWARE,
                format!("{} answered runfiles-tree with {detail}", self.worker()),
            )
        };
        let result: RunfilesTreeResult =
            serde_json::from_str(&data).map_err(|error| invalid(format!("an unexpected document: {error}")))?;
        if result.root != tree.root {
            return Err(Refusal::new(
                "guest_runfiles_mismatch",
                Exit::SOFTWARE,
                format!(
                    "{} built the runfiles tree at {}, and this controller expects {}",
                    self.worker(),
                    result.root,
                    tree.root
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

    /// Sends one request of `tree`, with the bytes of its staged runfiles or without them, and answers the data of the
    /// reply envelope as JSON text.
    async fn request_tree(&self, tree: &GuestRunfilesTree, with_bytes: bool) -> Result<String, Refusal> {
        let mut request = tree.request.clone();
        request.with_bytes = with_bytes;
        let mut bytes = Vec::new();
        if with_bytes {
            for file in &tree.staged_files {
                bytes.push(std::fs::read(file).or_refuse("runfiles_staged_unreadable", Exit::SOFTWARE, || {
                    format!("cannot read the staged runfile {}", file.display())
                })?);
            }
        }
        let options = SpawnOptions {
            stdin: Some(request_stdin(&request, &bytes)),
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
        Ok(data.get().to_owned())
    }
}
