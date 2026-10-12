//! Lease policy: who holds a worker, the mode-0600 receipt that is the only accepted handle to it, and the `lease`
//! commands.
//!
//! The lease *file* is read in [`crate::worker::worker`], next to the other per-worker host state, so that the worker
//! lifecycle can consult it without this module. Everything about *owning* one is here.
//!
//! # Why the acquisition is the hard part
//!
//! Several agents share one checkout and one worker pool, and the failure this module exists to prevent is two
//! holders of one worker: two runs writing one guest's writable state, two daemons fighting over one supervisor slot,
//! and a flake investigation whose evidence is another agent's run. So a lease is placed by an atomic no-clobber
//! publish onto a name that must not already exist ([`try_atomic_lease`]) rather than by a check followed by a
//! write, and `lease acquire --count N` takes the whole shard set under one pool-wide lock or none of it.
//!
//! # What a receipt is for
//!
//! The lease file says a worker is held; the receipt is the only proof that *this* caller is the holder. It is mode
//! 0600, owned by the invoking user, and it carries the token that is compared against the live lease - hashed, so a
//! caller cannot learn a token's length by timing a refusal. Every command that acts on a leased worker takes
//! `--lease-file` and goes through [`receipt_for_path`], which is why losing the receipt is not "the lease is
//! unrecoverable" but "recovery needs the holder to say who it was".

use std::io::Write;
use std::path::Path;
use std::sync::LazyLock;
use std::time::Duration;

use avl_base::fs::{PublishError, private_temporary, publish};
use avl_base::{Backend, Config, Exit, GuestOs, OrRefuse, Outcome, Refusal, Scope};
use avl_host_sys::Ctx;
use avl_host_sys::guest::{Guest, ParkedDaemonProbe};
use regex::Regex;
use serde::Serialize;
use serde_json::json;

use crate::worker::worker::{Lease, Manager, read_lease};
use avl_base::RefusalExt;

mod receipt;
mod set;

#[cfg(test)]
mod tests;

#[cfg(all(test, unix))]
pub(crate) use receipt::LeaseReceipt;
#[cfg(test)]
pub(crate) use receipt::{RECEIPT_KIND, write_lease_receipt};
pub(crate) use receipt::{receipt_backend, receipt_for_path, remove_lease_receipts, required_receipt};
pub(crate) use set::{HeldWorker, ReleaseResult, acquire_workers, disposition, release_workers, require_one_guest_os};

/// How long one `lease acquire` waits for another one to finish before giving up.
///
/// The whole pool survey and every placement happen under this lock, so contention is expected and short: several
/// agents starting at once is the ordinary case, and the alternative to queueing is a refusal that a caller would
/// have to retry with a loop of its own. Ten seconds is enough for a survey of a full pool including the `prlctl` and
/// `ps` probes it makes, and short enough that a wedged holder is reported rather than waited on.
pub(crate) const ACQUIRE_LOCK_TIMEOUT: Duration = Duration::from_secs(10);

/// Bounds a holder name. It is written into a receipt, compared for recovery and printed in messages; the bound
/// exists so that a sharded holder's `#N` suffix cannot silently push it past whatever the next reader assumes.
pub(crate) const MAX_HOLDER_LENGTH: usize = 128;

// --- the request ---------------------------------------------------------------------------------------------

/// What one acquisition asks for: a holder, how many workers, and whether fewer will do.
///
/// The count is *at most* N. A shardable lane and the flake harness both spread trials over whatever the pool can
/// give them, and a run that silently waits for a busy worker is worse than a narrower one; a measurement run is the
/// exception, and that is what `exact` is for. Only [`AcquireRequest::new`] builds one, so every request that exists
/// has passed its rules.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct AcquireRequest {
    holder: String,
    count: u8,
    exact: bool,
}

impl AcquireRequest {
    /// Validates a request: a holder of 1-128 printable ASCII characters without `#`, and a count from 1 to 99 whose
    /// shard suffix still fits the bound.
    pub(crate) fn new(holder: impl Into<String>, count: u8, exact: bool) -> Result<Self, Refusal> {
        let holder = holder.into();
        if holder.is_empty() {
            return Err(Refusal::usage("a lease holder must not be empty"));
        }
        if !(1..=99).contains(&count) {
            return Err(invalid_count());
        }
        if holder.len() > MAX_HOLDER_LENGTH || !holder.bytes().all(|byte| (0x20..=0x7e).contains(&byte)) {
            return Err(invalid_holder(format!(
                "--holder must be 1-{MAX_HOLDER_LENGTH} printable ASCII characters"
            )));
        }
        // `#` separates a holder from its shard index, so a holder carrying one could collide with another caller's
        // shard - and a collision there is answered by refusing the acquisition for want of a receipt, which is a
        // confusing way to learn about a naming clash.
        if holder.contains('#') {
            return Err(invalid_holder(
                "--holder must not contain '#'; it separates the per-shard suffix".to_owned(),
            ));
        }
        if count > 1 && holder.len() + format!("#{count}").len() > MAX_HOLDER_LENGTH {
            return Err(invalid_holder(format!(
                "--holder is {} characters; a --count {count} shard suffix would exceed {MAX_HOLDER_LENGTH}",
                holder.len()
            )));
        }
        Ok(Self { holder, count, exact })
    }

    pub(crate) fn holder(&self) -> &str {
        &self.holder
    }

    pub(crate) const fn count(&self) -> u8 {
        self.count
    }

    pub(crate) const fn exact(&self) -> bool {
        self.exact
    }
}

/// Reads a `--count` value: an integer from 1 to 99, in base ten and nothing else. Bounded well above the worker cap
/// rather than at it, because the count is a ceiling, so asking for more than the pool holds is legitimate. For the
/// command line's value parser.
pub(crate) fn parse_count(value: &str) -> Result<u8, Refusal> {
    static COUNT_SHAPE: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"^[1-9][0-9]?$").expect("a constant pattern compiles"));
    if !COUNT_SHAPE.is_match(value) {
        return Err(invalid_count());
    }
    value.parse().or_refuse("invalid_count", Exit::USAGE, || {
        "--count must be an integer from 1 to 99".to_owned()
    })
}

fn invalid_count() -> Refusal {
    Refusal::new("invalid_count", Exit::USAGE, "--count must be an integer from 1 to 99")
}

fn invalid_holder(message: String) -> Refusal {
    Refusal::new("invalid_holder", Exit::USAGE, message)
}

/// One `lease` invocation. `show` is what a bare `lease` means: it is the only one of the three that changes nothing,
/// and an operator finding out what the pool is doing must not be making an acquisition.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum LeaseCommand {
    Acquire(AcquireRequest),
    Show,
    Release,
}

// --- placement -----------------------------------------------------------------------------------------------

/// Places one lease, answering false when a peer got the worker first.
///
/// The heart of this module. A no-clobber move onto a name that must not exist is the whole mutual exclusion: one
/// operation that both creates the name and fails if somebody else created it, so two agents surveying the same free
/// worker cannot both conclude they have it. A check-then-write, or an exclusive open of the final path, both leave a
/// window where the file exists and is empty - and an empty lease file parses as a *corrupt* lease, which refuses
/// every later operation on a worker nobody actually holds.
///
/// So: a private temporary beside the destination, written and synced at mode 0600, then moved into place. The sync
/// is not ceremony - the lease is read back after a host crash to decide whether a worker is still held, and a move
/// that landed while the bytes were still in the page cache leaves a zero-length lease that hands out a leased
/// worker.
pub(crate) fn try_atomic_lease(settings: &Config, worker: &str, lease: &Lease) -> Result<bool, Refusal> {
    let failed = |what: &str, error: &dyn std::fmt::Display| {
        Refusal::new(
            "state_write_failed",
            Exit::FAILURE,
            format!("cannot {what} the lease for {worker}: {error}"),
        )
    };
    let mut encoded = serde_json::to_vec(lease).map_err(|error| failed("describe", &error))?;
    encoded.push(b'\n');
    let mut staged = private_temporary(&settings.worker_dir(worker), "lease.json").map_err(|error| failed("stage", &error))?;
    staged.write_all(&encoded).map_err(|error| failed("write", &error))?;
    match publish(staged, &settings.lease_path(worker)) {
        Ok(()) => Ok(true),
        Err(PublishError::DestinationExists(_)) => Ok(false),
        Err(error) => Err(failed("place", &error)),
    }
}

/// Runs `action` holding the worker's lifecycle lock, having re-read the lease under it.
///
/// The re-read is the point, not the lock. A receipt was validated against the live lease *before* the lock was
/// taken, so between those two moments the lease could have been released and the worker handed to somebody else -
/// and the operation would then run against another holder's worker with a receipt that looked valid when it was
/// checked. `lease_changed` is that window closing.
pub(crate) async fn with_lease_operation<T>(
    ctx: &Ctx,
    manager: &Manager,
    expected: &Lease,
    operation: &str,
    action: impl AsyncFnOnce(Lease) -> Result<T, Refusal>,
) -> Result<T, Refusal> {
    let settings = manager.settings();
    manager
        .with_lifecycle_lock(ctx, &expected.worker, operation, async {
            let current = read_lease(&settings.lease_path(&expected.worker))?
                .filter(|current| current.same_lease(expected))
                .ok_or_else(|| {
                    Refusal::new(
                        "lease_changed",
                        Exit::NO_PERM,
                        format!("lease for {} changed during the operation", expected.worker),
                    )
                })?;
            action(current).await
        })
        .await
}

// --- the commands --------------------------------------------------------------------------------------------

/// The `lease` command. `lease_file` is `--lease-file`, which `show` may omit and `release` may not; `probe` is how a
/// release tells a parked daemon of this controller's own from an iteration, and only `release` asks it.
pub(crate) async fn command_lease(
    ctx: &Ctx,
    manager: &Manager,
    command: &LeaseCommand,
    lease_file: Option<&Path>,
    probe: &dyn ParkedDaemonProbe,
) -> Result<Outcome, Refusal> {
    match command {
        LeaseCommand::Acquire(request) => command_lease_acquire(ctx, manager, request).await,
        LeaseCommand::Show => command_lease_show(ctx, manager, lease_file).await,
        LeaseCommand::Release => release_by_receipt(ctx, manager, required_receipt(lease_file)?, probe).await,
    }
}

/// The reply a single-worker acquisition has always sent, extended by the counts and the array every reply carries.
///
/// The envelope's data is a `serde_json::Value`, whose map is sorted, so the keys reach the wire alphabetically and
/// not in this field order; no reader depends on the order.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SingleLeaseData<'a> {
    backend: Backend,
    guest_os: GuestOs,
    worker: &'a str,
    holder: &'a str,
    acquired_at: &'a str,
    lease_file: String,
    recovered: bool,
    requested: u8,
    acquired: usize,
    leases: Vec<AcquiredLeaseData<'a>>,
}

/// The reply for a count above one, which has no single worker to name.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ShardedLeaseData<'a> {
    backend: Backend,
    guest_os: GuestOs,
    requested: u8,
    acquired: usize,
    leases: Vec<AcquiredLeaseData<'a>>,
}

/// One row of either reply.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct AcquiredLeaseData<'a> {
    worker: &'a str,
    holder: &'a str,
    acquired_at: &'a str,
    lease_file: String,
    recovered: bool,
}

/// `lease acquire`: take at most the requested count of workers, atomically, and render the two reply shapes that
/// exist for the readers that only have the envelope.
pub(crate) async fn command_lease_acquire(ctx: &Ctx, manager: &Manager, request: &AcquireRequest) -> Result<Outcome, Refusal> {
    let held = acquire_workers(ctx, manager, request).await?;
    let settings = manager.settings();
    let leases: Vec<AcquiredLeaseData<'_>> = held
        .iter()
        .map(|item| AcquiredLeaseData {
            worker: &item.lease.worker,
            holder: &item.lease.holder,
            acquired_at: &item.lease.acquired_at,
            lease_file: item.receipt.display().to_string(),
            recovered: item.recovered,
        })
        .collect();
    let encode = |data: serde_json::Result<serde_json::Value>| {
        data.map_err(|error| Refusal::internal(format!("cannot encode the acquisition: {error}")))
    };
    // Both counts, always: the count is a ceiling, so a caller that reads only the array length cannot tell a
    // two-way run that asked for two from a four-way run that got two.
    if request.count == 1 {
        // The single-worker reply is the one this command has always sent, so every caller that predates a count
        // sees no change.
        let first = held
            .first()
            .ok_or_else(|| Refusal::internal("an acquisition that succeeded answered no worker"))?;
        let text = format!(
            "worker={}\nholder={}\nlease_file={}\nrecovered={}",
            first.lease.worker,
            first.lease.holder,
            first.receipt.display(),
            first.recovered
        );
        let data = encode(serde_json::to_value(SingleLeaseData {
            backend: settings.backend,
            guest_os: settings.guest_os,
            worker: &first.lease.worker,
            holder: &first.lease.holder,
            acquired_at: &first.lease.acquired_at,
            lease_file: first.receipt.display().to_string(),
            recovered: first.recovered,
            requested: request.count,
            acquired: leases.len(),
            leases,
        }))?;
        return Ok(Outcome { data, text });
    }
    let mut rendered = vec![format!("requested={}", request.count), format!("acquired={}", leases.len())];
    rendered.extend(leases.iter().map(|item| {
        format!(
            "worker={} holder={} lease_file={} recovered={}",
            item.worker, item.holder, item.lease_file, item.recovered
        )
    }));
    let data = encode(serde_json::to_value(ShardedLeaseData {
        backend: settings.backend,
        guest_os: settings.guest_os,
        requested: request.count,
        acquired: leases.len(),
        leases,
    }))?;
    Ok(Outcome {
        data,
        text: rendered.join("\n"),
    })
}

/// One worker's row of `lease show`. The holder and the acquisition time are omitted rather than emptied for a
/// worker the caller does not hold: who holds a worker is not something an unauthorized caller is told.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct LeaseShowWorker {
    worker: String,
    state: &'static str,
    #[serde(skip_serializing_if = "Option::is_none")]
    holder: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    acquired_at: Option<String>,
}

/// Renders the pool, naming the holder only of the lease the caller proved it owns.
pub(crate) fn lease_show_outcome(settings: &Config, authorized: Option<&Lease>) -> Result<Outcome, Refusal> {
    let mut workers = Vec::with_capacity(settings.workers.len());
    let mut rendered = Vec::with_capacity(settings.workers.len());
    for worker in &settings.workers {
        let Some(lease) = read_lease(&settings.lease_path(worker))? else {
            rendered.push(format!("{worker}: free"));
            workers.push(LeaseShowWorker {
                worker: worker.clone(),
                state: "free",
                holder: None,
                acquired_at: None,
            });
            continue;
        };
        let ours = authorized.is_some_and(|authorized| authorized.same_lease(&lease));
        if ours {
            rendered.push(format!("{worker}: leased holder={} acquired={}", lease.holder, lease.acquired_at));
            workers.push(LeaseShowWorker {
                worker: worker.clone(),
                state: "leased",
                holder: Some(lease.holder),
                acquired_at: Some(lease.acquired_at),
            });
        } else {
            rendered.push(format!("{worker}: leased"));
            workers.push(LeaseShowWorker {
                worker: worker.clone(),
                state: "leased",
                holder: None,
                acquired_at: None,
            });
        }
    }
    Ok(Outcome {
        data: json!({ "backend": settings.backend, "workers": workers }),
        text: rendered.join("\n"),
    })
}

/// `lease show`: the pool's occupancy, with the holder of the caller's own lease.
///
/// A receipt is optional here and only here, because the free/leased shape is not private - what it buys is the
/// holder and the acquisition time of the one lease the caller can prove is its own.
pub(crate) async fn command_lease_show(ctx: &Ctx, manager: &Manager, lease_file: Option<&Path>) -> Result<Outcome, Refusal> {
    manager.prepare_runtime_dirs()?;
    let settings = manager.settings();
    // No `--lease-file` is no receipt: the pool is shown without holders.
    let Some(lease_file) = lease_file else {
        return lease_show_outcome(settings, None);
    };
    let (_, receipt) = receipt_for_path(settings, lease_file)?;
    with_lease_operation(ctx, manager, &receipt, "lease-show", async |current| {
        lease_show_outcome(settings, Some(&current))
    })
    .await
}

/// One release, from the handle that authorizes it: the gate every caller goes through, whether it arrived as
/// `lease release` or as one member of a set [`release_workers`] is giving back.
pub(crate) async fn release_by_receipt(
    ctx: &Ctx,
    manager: &Manager,
    lease_file: &Path,
    probe: &dyn ParkedDaemonProbe,
) -> Result<Outcome, Refusal> {
    let (_, receipt) = receipt_for_path(manager.settings(), lease_file)?;
    with_lease_operation(ctx, manager, &receipt, "release", async |lease| {
        release_one_lease(ctx, manager, &lease, probe).await
    })
    .await
}

async fn release_one_lease(ctx: &Ctx, manager: &Manager, lease: &Lease, probe: &dyn ParkedDaemonProbe) -> Result<Outcome, Refusal> {
    let settings = manager.settings();
    let worker = lease.worker.as_str();
    // A stopped Tart worker is released without touching the guest. Release exists to prove no run is still active,
    // and a worker with no run process cannot be running one; the guest holds no checkout, and its writable state is
    // meant to survive for the next lease. Requiring a start here is how a lease outlived its holder on a worker
    // that had crashed, leaving a slot that could be neither used nor freed. A stopped Docker container is the same
    // case: nothing runs in a container that does not run.
    if manager.stopped_for_release(ctx, worker).await? {
        manager.reporter().note(
            format!("{worker} is not running; releasing its lease without starting it"),
            Some(&Scope::worker(worker)),
        );
        remove_lease(settings, lease)?;
        return Ok(Outcome {
            data: json!({ "worker": worker, "released": true, "workerWasStopped": true }),
            text: format!("released={worker}"),
        });
    }
    // A live `tart run` whose VM Tart no longer lists holds no guest: `tart exec` cannot reach one, so no run can be
    // in flight there, and the guest check would refuse `guest_agent_unavailable` for a guest that cannot exist. The
    // lease goes; the stale process is `pool recycle`'s to end.
    #[cfg(unix)]
    if let Some(pid) = manager.missing_vm_run_process(ctx, worker).await? {
        manager.reporter().note(
            format!(
                "{worker}'s VM does not exist; releasing its lease without the guest check. Its run process {pid} \
                 is stale: `pool recycle {worker}` ends it and clones the slot again"
            ),
            Some(&Scope::worker(worker)),
        );
        remove_lease(settings, lease)?;
        return Ok(Outcome {
            data: json!({
                "worker": worker,
                "released": true,
                "workerVmMissing": true,
                "staleRunProcess": pid,
            }),
            text: format!("released={worker}"),
        });
    }
    manager.require_release_ready(ctx, worker).await?;
    let channel = manager.channel(worker);
    let guest = Guest {
        ctx,
        settings,
        channel: channel.as_ref(),
        reporter: manager.reporter(),
    };
    guest.install_agent(manager.bazel()).await?;
    // The contract: a release is refused by an *executing* run, and a warm daemon of this controller's own is not
    // one. The daemon takes the supervisor's run slot with `supervisor start` and keeps it for its whole life, so a
    // check on the slot alone could never free a worker with a warm daemon on it - the opposite of the documented
    // design, where a warm daemon survives a release. The lifecycle lock already excludes every iteration this host
    // can start; the slot's remaining job is a controller that crashed and left a run behind, so the probe answers
    // "executing" for everything it cannot prove idle.
    guest.reject_executing_run(probe, "release the lease").await?;
    // The next holder gets no file of this one's runs: a run removes its own, unless its controller died first.
    crate::lane::secrets::clear_run_secrets(&guest).await?;
    remove_lease(settings, lease)?;
    Ok(Outcome {
        data: json!({ "worker": worker, "released": true }),
        text: format!("released={worker}"),
    })
}

/// Unlinks the lease and every receipt for it, so a released worker leaves no usable handle.
fn remove_lease(settings: &Config, lease: &Lease) -> Result<(), Refusal> {
    std::fs::remove_file(settings.lease_path(&lease.worker)).or_refuse("state_write_failed", Exit::FAILURE, || {
        format!("cannot release the lease on {}", lease.worker)
    })?;
    remove_lease_receipts(settings, lease);
    Ok(())
}
