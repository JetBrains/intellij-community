//! What one acquisition *is*, as values: the shard set placed under one pool-wide lock, and the set given back.
//!
//! The seam every multi-worker caller wants. The alternative is to run the command and read the workers back out of
//! its envelope, which makes that *document* the contract between two crates that already share a compiler; the
//! envelope stays what it is for, the wire an agent reads.

use std::collections::HashSet;
use std::path::PathBuf;

use avl_base::clock::stamp;
use avl_base::{Config, Exit, Refusal, SCHEMA_VERSION, Scope, new_id};
use avl_host_sys::Ctx;
use avl_host_sys::fs::secret_matches;
use avl_host_sys::guest::ParkedDaemonProbe;
use avl_wire::progress::LeaseDisposition;

use super::receipt::{remove_lease_receipts, reusable_lease_receipt, write_lease_receipt};
use super::{ACQUIRE_LOCK_TIMEOUT, AcquireRequest, release_by_receipt, try_atomic_lease};
use crate::worker::worker::{Lease, Manager, read_lease};

#[cfg(test)]
mod tests;

/// One worker a caller now holds, and the receipt that is its only handle.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct HeldWorker {
    pub lease: Lease,
    pub receipt: PathBuf,
    /// The lease already existed under this holder and was recovered through its original receipt.
    pub recovered: bool,
}

/// Takes at most `request.count()` workers under one pool-wide lock and answers them.
///
/// The pool lock is queued rather than refused, because several agents starting at once is the ordinary case and
/// each of them holds it only for one survey.
pub(crate) async fn acquire_workers(ctx: &Ctx, manager: &Manager, request: &AcquireRequest) -> Result<Vec<HeldWorker>, Refusal> {
    manager.prepare_runtime_dirs()?;
    let pool_lock = manager.settings().runtime_root.join("lease-acquire.lock");
    let _pool = manager
        .locks()
        .acquire_queued(
            ctx,
            &pool_lock,
            "lease-acquire",
            ACQUIRE_LOCK_TIMEOUT,
            "lease_acquire_timeout",
            "timed out waiting for another lease acquisition",
        )
        .await?;
    acquire_held_set(ctx, manager, request).await
}

/// Places the whole shard set, or none of it.
///
/// The caller holds the pool-wide acquisition lock for this entire scan, which is what makes N leases one atomic act:
/// nothing else can claim a worker between the survey and the placements, and anything that refuses the set
/// afterwards - `exact` short of the pool, a guest-OS disagreement - unwinds what this call placed rather than
/// leaving a partly-owned set behind. N sequential single-lease calls cannot do this: the holder-recovery path
/// answers the second call with the first call's worker.
async fn acquire_held_set(ctx: &Ctx, manager: &Manager, request: &AcquireRequest) -> Result<Vec<HeldWorker>, Refusal> {
    let settings = manager.settings();
    let shards = shard_holders(request);
    let mut wanted: HashSet<&str> = shards.iter().map(String::as_str).collect();
    let mut held = Vec::new();
    let mut taken = HashSet::new();
    for worker in &settings.workers {
        let Some(existing) = lease_for_acquire(ctx, manager, worker).await? else {
            continue;
        };
        taken.insert(worker.as_str());
        // Struck off, so a stray second lease under the same shard name is left where it is rather than acquired
        // as an extra worker the caller never asked for.
        if !wanted.remove(existing.holder.as_str()) {
            continue;
        }
        let receipt = reusable_lease_receipt(settings, &existing).ok_or_else(|| {
            Refusal::new(
                "lease_recovery_receipt_missing",
                Exit::NO_PERM,
                "an active lease already exists; recovery requires its original secure receipt",
            )
        })?;
        held.push(HeldWorker {
            lease: existing,
            receipt,
            recovered: true,
        });
        // A single-worker acquisition answers from the first worker that already holds it, without reading the rest
        // of the pool - the behaviour every caller that predates a count already relies on.
        if request.count() == 1 {
            require_one_guest_os(settings, &held)?;
            return Ok(held);
        }
    }
    let pending: Vec<&String> = shards
        .iter()
        .filter(|shard| !held.iter().any(|item| item.lease.holder == **shard))
        .collect();
    let mut placed: Vec<Lease> = Vec::new();
    let outcome = place_pending(ctx, manager, request, &pending, &taken, &mut held, &mut placed).await;
    if let Err(refusal) = outcome {
        rollback_placed_leases(manager, &placed);
        return Err(refusal);
    }
    Ok(held)
}

async fn place_pending(
    ctx: &Ctx,
    manager: &Manager,
    request: &AcquireRequest,
    pending: &[&String],
    taken: &HashSet<&str>,
    held: &mut Vec<HeldWorker>,
    placed: &mut Vec<Lease>,
) -> Result<(), Refusal> {
    let settings = manager.settings();
    for worker in &settings.workers {
        let Some(holder) = pending.get(placed.len()) else {
            break;
        };
        if taken.contains(worker.as_str()) {
            continue;
        }
        let lease = Lease {
            schema_version: SCHEMA_VERSION,
            backend: settings.backend,
            guest_os: settings.guest_os,
            worker: worker.clone(),
            // A lease's secret is a v4 UUID like every other identity this controller mints: 122 random bits make
            // guessing one strictly worse than reading a receipt nobody protected.
            token: new_id(),
            holder: (*holder).clone(),
            acquired_at: stamp(jiff::Timestamp::now()),
        };
        let Some(receipt) = place_lease(ctx, manager, worker, &lease).await? else {
            continue;
        };
        placed.push(lease.clone());
        held.push(HeldWorker {
            lease,
            receipt,
            recovered: false,
        });
    }
    if held.is_empty() {
        return Err(Refusal::new(
            "pool_exhausted",
            Exit::TEMP_FAIL,
            format!("every {} worker of this pool is leased", settings.guest_os),
        ));
    }
    if request.exact() && held.len() < usize::from(request.count()) {
        return Err(Refusal::new(
            "pool_exhausted",
            Exit::TEMP_FAIL,
            format!(
                "--exact asked for {} workers and only {} could be leased",
                request.count(),
                held.len()
            ),
        ));
    }
    require_one_guest_os(settings, held)
}

/// The per-worker holder names one request stands for.
///
/// A single-worker acquisition keeps the bare holder, which is what preserves idempotent recovery for every caller
/// that predates a count: recovery matches a lease by its holder string. A sharded acquisition suffixes `#1`..`#N`,
/// and each shard then recovers independently through exactly the same path.
fn shard_holders(request: &AcquireRequest) -> Vec<String> {
    if request.count() == 1 {
        return vec![request.holder().to_owned()];
    }
    (1..=request.count()).map(|index| format!("{}#{index}", request.holder())).collect()
}

/// One worker's lease read under its lifecycle lock, or [`worker_busy`] when another operation holds the lock.
async fn lease_for_acquire(ctx: &Ctx, manager: &Manager, worker: &str) -> Result<Option<Lease>, Refusal> {
    manager
        .try_with_lifecycle_lock(ctx, worker, "lease-acquire-check", async {
            read_lease(&manager.settings().lease_path(worker))
        })
        .await?
        .ok_or_else(worker_busy)
}

/// The refusal of an acquisition that finds a worker's lifecycle lock held: somebody is doing something to this
/// worker, and `worker_busy` is the code a caller retries against.
fn worker_busy() -> Refusal {
    Refusal::new("worker_busy", Exit::TEMP_FAIL, "a worker is handling another lifecycle operation")
}

/// Places one lease and writes its receipt, or answers `None` when the worker turned out to be taken.
///
/// The receipt write is inside the lock and the lease is unlinked if it fails, because a lease with no receipt is a
/// worker nobody can use and nobody can free: a release needs the handle this would not have written.
async fn place_lease(ctx: &Ctx, manager: &Manager, worker: &str, lease: &Lease) -> Result<Option<PathBuf>, Refusal> {
    let settings = manager.settings();
    manager
        .try_with_lifecycle_lock(ctx, worker, "lease-acquire", async {
            if !try_atomic_lease(settings, worker, lease)? {
                return Ok(None);
            }
            write_lease_receipt(settings, lease).map(Some).inspect_err(|_| {
                let _ = std::fs::remove_file(settings.lease_path(worker));
            })
        })
        .await?
        .ok_or_else(worker_busy)
}

/// Undoes the leases this call placed, so an acquisition that cannot be completed leaves no worker owned by nobody.
/// Token-checked, because the one thing worse than a leaked lease is reclaiming someone else's.
fn rollback_placed_leases(manager: &Manager, placed: &[Lease]) {
    let settings = manager.settings();
    for lease in placed.iter().rev() {
        let path = settings.lease_path(&lease.worker);
        let released = match read_lease(&path) {
            Ok(Some(current)) if secret_matches(&current.token, &lease.token) => std::fs::remove_file(&path).is_ok(),
            Ok(_) => true,
            Err(_) => false,
        };
        if released {
            remove_lease_receipts(settings, lease);
        } else {
            // The receipt stays: it is what releases the lease this could not take back.
            manager.reporter().note(
                format!(
                    "could not roll back the lease just placed on {}; release it with its receipt",
                    lease.worker
                ),
                Some(&Scope::worker(&lease.worker)),
            );
        }
    }
}

/// Refuses a held set that spans guest OSes.
///
/// Every lease an acquisition places names the invoked guest, so inside one the check bites on a *recovered* lease -
/// one left by an earlier invocation of the other guest under a shared runtime root. It is public because a caller
/// can also assemble a set this module did not: the flake harness mixes a receipt its invoker supplied with the
/// workers it leased itself.
///
/// Handing both guests to one fan-out would make each worker configure Bazel differently, and the two configurations
/// do not merely compete for the analysis cache, they evict each other: an 18.4 s build measured 48.5 s beside a busy
/// worker of the *same* guest, and a mixed set pays that on every later invocation.
pub(crate) fn require_one_guest_os(settings: &Config, held: &[HeldWorker]) -> Result<(), Refusal> {
    for item in held {
        let guest_os = item.lease.guest_os;
        if guest_os != settings.guest_os {
            return Err(Refusal::new(
                "lease_guest_os_mismatch",
                Exit::NO_PERM,
                format!(
                    "{} holds a {guest_os} lease, but this run is {}; one run cannot span guests",
                    item.lease.worker, settings.guest_os
                ),
            ));
        }
    }
    Ok(())
}

/// What one worker's release answered.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct ReleaseResult {
    pub worker: String,
    /// The handle that still frees the worker when the release was refused.
    pub receipt: PathBuf,
    pub released: bool,
    pub failure: Option<String>,
}

/// Gives back exactly the leases a caller placed, one refusal never hiding the others.
///
/// Best effort, and never an error of its own: the work the leases were taken for is already done by the time this
/// runs, and a worker that cannot be freed is something a human finishes by hand rather than a reason to lose a
/// verdict. What it does owe every caller is a per-worker answer, because "released" printed over a refusal is
/// exactly how a still-held worker comes to be reported as free.
pub(crate) async fn release_workers(
    ctx: &Ctx,
    manager: &Manager,
    held: &[HeldWorker],
    probe: &dyn ParkedDaemonProbe,
) -> Vec<ReleaseResult> {
    let mut results = Vec::with_capacity(held.len());
    for item in held {
        let worker = item.lease.worker.clone();
        let failure = release_by_receipt(ctx, manager, &item.receipt, probe)
            .await
            .err()
            .map(|refusal| refusal.message);
        if let Some(failure) = &failure {
            manager.reporter().note(
                format!("could not release {worker} ({failure}); release it with {}", item.receipt.display()),
                Some(&Scope::worker(&worker)),
            );
        }
        results.push(ReleaseResult {
            worker,
            receipt: item.receipt.clone(),
            released: failure.is_none(),
            failure,
        });
    }
    results
}

/// The disposition of a set, derived from what the releases *answered* rather than from the fact that they were
/// attempted: a caller reading `released` over a worker that is still held has been told the pool has a free slot
/// it does not have, and discovers that as an unexplained `pool_exhausted` on some later run.
///
/// Never [`LeaseDisposition::Kept`]: that is the interrupt path's deliberate leak, which releases nothing and says
/// so itself.
pub(crate) fn disposition(results: &[ReleaseResult]) -> LeaseDisposition {
    if results.iter().all(|result| result.released) {
        LeaseDisposition::Released
    } else {
        LeaseDisposition::ReleaseFailed
    }
}
