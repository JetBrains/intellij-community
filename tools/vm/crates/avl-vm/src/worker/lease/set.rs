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
use serde::Serialize;

use super::receipt::{remove_lease_receipts, reusable_lease_receipt, write_lease_receipt};
use super::{ACQUIRE_LOCK_TIMEOUT, AcquireRequest, release_by_receipt, try_atomic_lease};
use crate::daemon::state::HostState;
use crate::worker::hypervisor::SlotState;
use crate::worker::run_marker::RunMarkerState;
use crate::worker::worker::{Lease, Manager, read_lease, remove_idle_stop_record};

#[cfg(test)]
mod tests;

/// One worker a caller now holds, and the receipt that is its only handle.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct HeldWorker {
    pub lease: Lease,
    pub receipt: PathBuf,
    /// The lease already existed under this holder and was recovered through its original receipt.
    pub recovered: bool,
    /// Why the acquisition chose this slot.
    pub slot_reason: SlotReason,
}

/// Why a lease is on its slot. An acquisition ranks the free slots by class ([`rank_slots`]), so a lease lands on
/// the warmest slot, and within one class the slot order of the pool stays.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "kebab-case")]
pub(crate) enum SlotReason {
    /// Class 1: a running slot whose daemon record names the launch digest of the build that asks.
    WarmDaemon,
    /// Class 2: a running slot with another daemon, or with none.
    Running,
    /// Class 2 too: the backend gave no running state, so the slot keeps its place in the pool order.
    Unranked,
    /// Class 3: a stopped slot, which keeps its disk or its volume.
    Stopped,
    /// Class 4: a slot with no machine.
    Absent,
    /// The lease already existed under this holder, and the acquisition recovered it.
    Recovered,
    /// The caller named its own lease, so no acquisition chose the slot.
    Borrowed,
}

impl SlotReason {
    /// The class of a free slot from its state, and whether its daemon serves the build that asks.
    pub(crate) const fn of(state: SlotState, warm_daemon: bool) -> Self {
        match state {
            SlotState::Running if warm_daemon => Self::WarmDaemon,
            SlotState::Running => Self::Running,
            SlotState::Unknown => Self::Unranked,
            SlotState::Stopped => Self::Stopped,
            SlotState::Absent => Self::Absent,
        }
    }

    /// The rank of the class: the lower, the earlier a lease takes the slot.
    const fn class(self) -> u8 {
        match self {
            Self::WarmDaemon | Self::Recovered | Self::Borrowed => 1,
            Self::Running | Self::Unranked => 2,
            Self::Stopped => 3,
            Self::Absent => 4,
        }
    }

    /// The word of the `slotReason` field and of the text reply.
    pub(crate) const fn as_str(self) -> &'static str {
        match self {
            Self::WarmDaemon => "warm-daemon",
            Self::Running => "running",
            Self::Unranked => "unranked",
            Self::Stopped => "stopped",
            Self::Absent => "absent",
            Self::Recovered => "recovered",
            Self::Borrowed => "borrowed",
        }
    }
}

/// One free slot as the ranking reads it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct SlotFacts {
    pub worker: String,
    pub reason: SlotReason,
}

/// The free slots in the order a lease takes them: by class, and in the pool order within one class. A pure
/// function, so the order is a fact of its input alone.
pub(crate) fn rank_slots(facts: &[SlotFacts]) -> Vec<&SlotFacts> {
    let mut ranked: Vec<&SlotFacts> = facts.iter().collect();
    // A stable sort keeps the pool order within one class.
    ranked.sort_by_key(|slot| slot.reason.class());
    ranked
}

impl Manager {
    /// The facts of each free slot in `workers`: its state ([`crate::worker::hypervisor::Machine::slot_states`]),
    /// and for a running slot whether the daemon record of the slot names `launch_digest`. Nothing is started or
    /// changed.
    pub(crate) async fn slot_preference(&self, ctx: &Ctx, workers: &[&str], launch_digest: Option<&str>) -> Vec<SlotFacts> {
        let states = self.machine().slot_states(ctx, workers).await;
        workers
            .iter()
            .zip(states)
            .map(|(worker, state)| {
                let warm_daemon = state == SlotState::Running
                    && launch_digest.is_some_and(|digest| {
                        HostState::read(self.settings(), worker).is_some_and(|record| record.launch_digest == digest)
                    });
                SlotFacts {
                    worker: (*worker).to_owned(),
                    reason: SlotReason::of(state, warm_daemon),
                }
            })
            .collect()
    }
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
///
/// A busy worker is not available, and the survey goes on with the next one. A busy worker that may hold a lease of
/// the request refuses the acquisition as [`worker_busy`]: the recovery cannot read that lease under the lock, and a
/// new lease would give the holder a second worker. A worker whose lease the request recovers refuses as
/// [`worker_busy`] too, while a run of a live process is in flight on it ([`require_no_run_of_holder`]).
async fn acquire_held_set(ctx: &Ctx, manager: &Manager, request: &AcquireRequest) -> Result<Vec<HeldWorker>, Refusal> {
    let settings = manager.settings();
    let shards = shard_holders(request);
    let mut wanted: HashSet<&str> = shards.iter().map(String::as_str).collect();
    let mut held = Vec::new();
    let mut taken = HashSet::new();
    let mut busy = Vec::new();
    for worker in &settings.workers {
        let existing = match lease_for_acquire(ctx, manager, worker).await? {
            Surveyed::Read(None) => continue,
            Surveyed::Read(Some(existing)) => existing,
            Surveyed::Busy(lease) => {
                let may_be_wanted = match &lease {
                    Ok(None) => false,
                    Ok(Some(lease)) => wanted.contains(lease.holder.as_str()),
                    // A lease that cannot be read may be one of the request.
                    Err(_) => true,
                };
                if may_be_wanted {
                    return Err(holder_worker_busy(worker));
                }
                taken.insert(worker.as_str());
                busy.push(worker.clone());
                continue;
            }
        };
        taken.insert(worker.as_str());
        // Struck off, so a stray second lease under the same shard name is left where it is rather than acquired
        // as an extra worker the caller never asked for.
        if !wanted.remove(existing.holder.as_str()) {
            continue;
        }
        require_no_run_of_holder(ctx, manager, worker).await?;
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
            slot_reason: SlotReason::Recovered,
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
    let free: Vec<&str> = settings
        .workers
        .iter()
        .map(String::as_str)
        .filter(|worker| !taken.contains(worker))
        .collect();
    let mut placed: Vec<Lease> = Vec::new();
    let outcome = async {
        busy.extend(place_pending(ctx, manager, request, &pending, &free, &mut held, &mut placed).await?);
        require_enough(settings, request, &held, &busy)?;
        require_one_guest_os(settings, &held)
    }
    .await;
    if let Err(refusal) = outcome {
        rollback_placed_leases(manager, &placed);
        return Err(refusal);
    }
    Ok(held)
}

/// Places a lease for each pending holder on the free slots, in the ranked order, and answers the slots that turned
/// out to be busy. A busy slot is skipped as a taken one is.
async fn place_pending(
    ctx: &Ctx,
    manager: &Manager,
    request: &AcquireRequest,
    pending: &[&String],
    free: &[&str],
    held: &mut Vec<HeldWorker>,
    placed: &mut Vec<Lease>,
) -> Result<Vec<String>, Refusal> {
    let settings = manager.settings();
    let mut busy = Vec::new();
    // One free slot has no order to choose, so a one-slot pool asks the backend nothing.
    let facts = if free.len() > 1 && !pending.is_empty() {
        manager.slot_preference(ctx, free, request.launch_digest()).await
    } else {
        free.iter()
            .map(|worker| SlotFacts {
                worker: (*worker).to_owned(),
                reason: SlotReason::Unranked,
            })
            .collect()
    };
    for slot in rank_slots(&facts) {
        let Some(holder) = pending.get(placed.len()) else {
            break;
        };
        let worker = &slot.worker;
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
        let receipt = match place_lease(ctx, manager, worker, &lease).await? {
            Placement::Placed(receipt) => receipt,
            Placement::Taken => continue,
            Placement::Busy => {
                busy.push(worker.clone());
                continue;
            }
        };
        placed.push(lease.clone());
        held.push(HeldWorker {
            lease,
            receipt,
            recovered: false,
            slot_reason: slot.reason,
        });
    }
    Ok(busy)
}

/// Refuses a held set that is empty, or short under `exact`. The refusal is [`worker_busy`] when a busy worker was in
/// the way, because that worker becomes available when its operation ends. It is `pool_exhausted` otherwise.
fn require_enough(settings: &Config, request: &AcquireRequest, held: &[HeldWorker], busy: &[String]) -> Result<(), Refusal> {
    let short = if held.is_empty() {
        format!("no {} worker of this pool could be leased", settings.guest_os)
    } else if request.exact() && held.len() < usize::from(request.count()) {
        format!(
            "--exact asked for {} workers and only {} could be leased",
            request.count(),
            held.len()
        )
    } else {
        return Ok(());
    };
    if !busy.is_empty() {
        return Err(worker_busy(format!(
            "{short}; another lifecycle operation holds {}",
            busy.join(", ")
        )));
    }
    if held.is_empty() {
        return Err(Refusal::new(
            "pool_exhausted",
            Exit::TEMP_FAIL,
            format!("every {} worker of this pool is leased", settings.guest_os),
        ));
    }
    Err(Refusal::new("pool_exhausted", Exit::TEMP_FAIL, short))
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

/// What the survey of one worker found.
enum Surveyed {
    /// The lease read under the lifecycle lock of the worker, or `None` for a free worker.
    Read(Option<Lease>),
    /// Another operation holds the lifecycle lock, and the lease is read without it. A placement needs the pool lock
    /// that the survey holds, so no lease appears on the worker during the survey. A release can remove one.
    Busy(Result<Option<Lease>, Refusal>),
}

/// One worker's lease read under its lifecycle lock, or [`Surveyed::Busy`] when another operation holds the lock.
async fn lease_for_acquire(ctx: &Ctx, manager: &Manager, worker: &str) -> Result<Surveyed, Refusal> {
    let path = manager.settings().lease_path(worker);
    let read = manager
        .try_with_lifecycle_lock(ctx, worker, "lease-acquire-check", async { read_lease(&path) })
        .await?;
    Ok(read.map_or_else(|| Surveyed::Busy(read_lease(&path)), Surveyed::Read))
}

/// The refusal of an acquisition that a worker's lifecycle lock stopped: somebody is doing something to this worker,
/// and `worker_busy` is the code a caller retries against.
fn worker_busy(message: String) -> Refusal {
    Refusal::new("worker_busy", Exit::TEMP_FAIL, message)
}

/// The refusal of an acquisition that finds a busy worker which may hold a lease of the request.
fn holder_worker_busy(worker: &str) -> Refusal {
    worker_busy(format!(
        "{worker} is handling another lifecycle operation, and it may hold a lease of this holder; retry when the \
         operation ends"
    ))
}

/// Refuses the recovery of the lease on `worker` as [`worker_busy`] while a run of another live process is in flight
/// on the worker. A recovered receipt would give a second run of the holder the worker of the first one. A stale
/// marker is removed with a note, and the recovery goes on.
///
/// The check runs under the lifecycle lock of the worker. A busy lock refuses as [`holder_worker_busy`].
async fn require_no_run_of_holder(ctx: &Ctx, manager: &Manager, worker: &str) -> Result<(), Refusal> {
    let checked = manager
        .try_with_lifecycle_lock(ctx, worker, "lease-acquire-check", async {
            Ok(match manager.judge_run_marker(ctx, worker).await? {
                RunMarkerState::Live(marker) => Some(marker),
                RunMarkerState::Stale(marker) => {
                    manager.remove_stale_run_marker(worker, &marker)?;
                    None
                }
                RunMarkerState::None | RunMarkerState::Ours(_) => None,
            })
        })
        .await?;
    match checked {
        None => Err(holder_worker_busy(worker)),
        Some(None) => Ok(()),
        Some(Some(marker)) => Err(worker_busy(format!(
            "{worker} holds a lease of this holder, and its run {} is in flight (pid {} since {}); retry when the run \
             ends",
            marker.run_id, marker.process.pid, marker.started_at
        ))),
    }
}

/// What one placement did.
enum Placement {
    /// The lease is on the worker, and this is its receipt.
    Placed(PathBuf),
    /// A lease of another caller is on the worker.
    Taken,
    /// Another operation holds the lifecycle lock of the worker.
    Busy,
}

/// Places one lease and writes its receipt.
///
/// The receipt write is inside the lock and the lease is unlinked if it fails, because a lease with no receipt is a
/// worker nobody can use and nobody can free: a release needs the handle this would not have written.
async fn place_lease(ctx: &Ctx, manager: &Manager, worker: &str, lease: &Lease) -> Result<Placement, Refusal> {
    let settings = manager.settings();
    let placed = manager
        .try_with_lifecycle_lock(ctx, worker, "lease-acquire", async {
            if !try_atomic_lease(settings, worker, lease)? {
                return Ok(Placement::Taken);
            }
            // The new holder keeps the worker running: the idle stop of the last release finds no record.
            remove_idle_stop_record(settings, worker)?;
            write_lease_receipt(settings, lease).map(Placement::Placed).inspect_err(|_| {
                let _ = std::fs::remove_file(settings.lease_path(worker));
            })
        })
        .await?;
    Ok(placed.unwrap_or(Placement::Busy))
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
