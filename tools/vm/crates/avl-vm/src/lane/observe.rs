//! Everything that observes or reaches into a worker without running tests: `exec`, the Peekaboo passthrough with
//! its artifact publication, `pull`, `ls`, `vnc`, and `status`.
//!
//! Tests themselves are not here - they go through the daemon. What is left is the set of operations that answer
//! "what is this worker doing" and "get me that file", which the daemon needs but does not own. `exec` is the
//! exception that touches the supervisor's run slot, and is refused while a daemon holds it; `ls` and `pull` are
//! deliberately not - see [`ls::command_ls`].
//!
//! One gate, [`on_leased_worker`], with one [`LeaseUse`] for each kind of command. `ls`, `pull` and `vnc` read the
//! worker, so they pass it with [`LeaseUse::Observe`] and answer while a run is in flight. `exec` and `peekaboo` act on
//! the worker, so they pass it with [`LeaseUse::Act`] and refuse while a run of another process is in flight.
//!
//! [`pull::pull_guest_file`] has one consumer beyond `pull` itself: the daemon publishes run artifacts and trace
//! zips through exactly this primitive.
//!
//! # Guest output never travels in a refusal
//!
//! A guest process can echo the UI-test bridge token, and a refusal ends up inside a JSON envelope an agent reads
//! and may log. So `ls_failed`, `pull_failed` and `guest_command_failed` carry the code and the exit status, and
//! never what the guest printed. This is the module-wide contract [`avl_host_sys::Runner::checked_quietly`] and
//! [`Guest::raw`](avl_host_sys::guest::Guest::raw) already apply; the commands here that read a guest's output
//! directly (to keep it on success) apply it by hand.

use std::path::Path;

use crate::worker::lease::{receipt_for_path, required_receipt, with_lease_operation, with_lease_read};
use crate::worker::worker::{Lease, Manager};
use avl_base::Refusal;
use avl_host_sys::Ctx;

pub(crate) mod exec;
pub(crate) mod ls;
pub(crate) mod peekaboo;
pub(crate) mod pull;
pub(crate) mod status;
pub(crate) mod vnc;

#[cfg(test)]
#[cfg(unix)]
mod testing;

pub(crate) use exec::command_exec;
pub(crate) use ls::{LsArgs, command_ls};
pub(crate) use peekaboo::{PeekabooArgs, command_peekaboo};
pub(crate) use pull::{command_pull, pull_guest_file};
pub(crate) use status::command_status;
pub(crate) use vnc::command_vnc;

/// What a command here does with a leased worker.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum LeaseUse {
    /// It reads the worker. The gate re-reads the lease under the worker's lifecycle lock ([`with_lease_read`]), then
    /// runs the action without the lock. The sequence - not the lock - is what makes a stale receipt refuse instead of
    /// reading somebody else's worker.
    Observe,
    /// It acts on the worker. The gate runs the action under the worker's lifecycle lock with the lease re-read
    /// ([`with_lease_operation`]). A run of another live process refuses the command as `lease_busy`.
    Act,
}

/// The gate every command here that reads or acts on a leased worker runs first. It validates the receipt, then runs
/// `action` as `lease_use` says.
async fn on_leased_worker<T>(
    ctx: &Ctx,
    manager: &Manager,
    lease_file: Option<&Path>,
    operation: &str,
    lease_use: LeaseUse,
    action: impl AsyncFnOnce(Lease) -> Result<T, Refusal>,
) -> Result<T, Refusal> {
    let (_, receipt) = receipt_for_path(manager.settings(), required_receipt(lease_file)?)?;
    match lease_use {
        LeaseUse::Observe => with_lease_read(ctx, manager, &receipt, operation, action).await,
        LeaseUse::Act => with_lease_operation(ctx, manager, &receipt, operation, action).await,
    }
}

/// Splits guest output on `\r?\n`. Guest output is split before a pattern is applied to a line rather than matched
/// with a multiline pattern, so a CRLF stream cannot leave a `\r` inside a match.
fn split_lines(text: &str) -> impl Iterator<Item = &str> {
    text.split('\n').map(|line| line.strip_suffix('\r').unwrap_or(line))
}
