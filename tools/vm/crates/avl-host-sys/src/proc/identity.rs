//! The identity of a host process: its pid, its start time and its command line.
//!
//! A pid alone is no identity, because the host gives the pid of an ended process to a new one. The start time and the
//! command line tell the two processes apart. Three records carry an identity. They are the pid receipt of a Tart run
//! process, the owner record of a lock and the run marker of a worker.

use avl_base::Refusal;
use serde::{Deserialize, Serialize};

use super::{PsField, Runner};
use crate::ctx::Ctx;

#[cfg(test)]
#[cfg(unix)]
mod tests;

/// A host process as the controller recognises it again: the pid, the start time and the command line.
///
/// The process is the same one only while the process table answers all three fields as the identity records them.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProcessIdentity {
    pub pid: i32,
    /// The answer of [`PsField::StartTime`].
    pub process_start: String,
    /// The answer of [`PsField::Command`].
    pub process_command: String,
}

impl Runner {
    /// The identity of the process `pid`, or `None` when the process table holds no such process.
    ///
    /// A probe without an answer is the refusal `probe_unanswered`, as for [`Runner::probe_process`].
    pub async fn identify(&self, ctx: &Ctx, pid: i32) -> Result<Option<ProcessIdentity>, Refusal> {
        let Some(process_start) = self.probe_process(ctx, pid, PsField::StartTime).await? else {
            return Ok(None);
        };
        let Some(process_command) = self.probe_process(ctx, pid, PsField::Command).await? else {
            return Ok(None);
        };
        Ok(Some(ProcessIdentity {
            pid,
            process_start,
            process_command,
        }))
    }

    /// Whether `identity` still names a live process. The process table must answer the recorded start time and
    /// command for the pid. The state of the process must not start with `Z`.
    ///
    /// A zombie is dead here, although the process table still answers for it. A spawn that does not reap its child
    /// leaves a zombie with a stable start time and command. An identity of that zombie matches it for ever. The
    /// zombie flag is the leading `Z` of the state under the BSD `ps`, under procps and in the Windows probe.
    ///
    /// A probe without an answer is the refusal `probe_unanswered`, and not a dead process.
    pub async fn identity_alive(&self, ctx: &Ctx, identity: &ProcessIdentity) -> Result<bool, Refusal> {
        let pid = identity.pid;
        Ok(
            self.probe_process(ctx, pid, PsField::StartTime).await?.as_deref() == Some(identity.process_start.as_str())
                && self.probe_process(ctx, pid, PsField::Command).await?.as_deref() == Some(identity.process_command.as_str())
                && !self
                    .probe_process(ctx, pid, PsField::State)
                    .await?
                    .is_some_and(|state| state.starts_with('Z')),
        )
    }
}
