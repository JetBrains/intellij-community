//! The journaled run, and the leased run `run`, `shard` and `flake` share once they take workers of their own.
//!
//! [`Host::journaled`] gives one invocation its id and its journal: `runStarted` first, `runFinished` last. The id is
//! `<user>-<command>-<uuid>`, and it is the default holder of every lease the run takes for itself.
//!
//! [`Host::leased_run`] is the sequence, in this order:
//!
//! 1. the request is checked - the holder, and a borrowed receipt with its guest - so a bad one costs nothing;
//! 2. the host paths are ensured and the pool-scope build runs, with no worker held: a cold analysis inside a held
//!    lease is a worker nobody else can use for minutes;
//! 3. the workers are acquired, inside the lease phase;
//! 4. the command divides its work over the held workers;
//! 5. every worker's body runs at once, each under that worker's lifecycle lock, beside the signal watch;
//! 6. what this run acquired is released, inside the release phase.
//!
//! The persist, the verdict and the reply are each command's fold over the [`Leased`] value this answers.
//!
//! **One release site.** Between the acquisition and the release the only fallible step is the division, and its
//! refusal releases before it returns. A body cannot fail: it answers a value, and a lock refusal is stored as one.
//! So no refusal after the acquisition leaves a worker held unnamed, and the release results always reach the fold.
//! A borrowed receipt is the caller's, and it is never released.
//!
//! **One interrupt policy.** A signal ends the wait. The bodies still in flight are dropped, and every lease this run
//! took is kept, because a release takes the worker's lifecycle lock and proves no run is active, and neither is true
//! of an abandoned iteration. The command then publishes no verdict and refuses `<command>_interrupted`, naming each
//! receipt. The watch is biased, so a signal that arrived before the fan-out starts no body at all.

use std::path::Path;
use std::sync::Arc;

use crate::worker::lease::{
    AcquireRequest, HeldWorker, ReleaseResult, acquire_workers, disposition, receipt_for_path, release_workers, require_one_guest_os,
    with_lease_operation,
};
use crate::worker::worker::Lease;
use avl_base::plain::release_command;
use avl_base::{Refusal, Scope, journal};
use avl_host_sys::Ctx;
use avl_host_sys::guest::ensure_host_paths;
use avl_wire::progress::{Event, LeaseDisposition, Phase, RunStarted, VerdictLease};
use futures::StreamExt;
use futures::stream::FuturesUnordered;

use crate::daemon::build::{BuildScope, PreparedBuild};
use crate::daemon::host::Host;
use crate::daemon::slot::parked_daemon_probe;
use crate::daemon::verdict::release_error;

#[cfg(test)]
#[cfg(unix)]
mod tests;

/// Which workers a leased run holds, and under which holder.
pub(crate) struct Workers<'a> {
    /// A caller's receipt (`flake --lease-file`): held first, chained on, and never released.
    pub(crate) borrowed: Option<&'a Path>,
    /// The holder of the leases this run takes (`shard --holder`); the run id when absent.
    pub(crate) holder: Option<&'a str>,
    /// How many workers the run wants in all, the borrowed one included.
    pub(crate) count: u8,
    /// Requires all of them rather than at most that many.
    pub(crate) exact: bool,
}

/// What became of the workers this run acquired. The borrowed worker is never in it.
pub(crate) struct Settlement {
    /// [`LeaseDisposition::Kept`] after an interrupt, otherwise what the releases answered.
    pub(crate) disposition: LeaseDisposition,
    /// One per acquired worker; empty when kept, or when nothing was acquired.
    pub(crate) releases: Vec<ReleaseResult>,
    /// The receipts of the acquired workers still leased: every one of them when kept.
    pub(crate) still_held: Vec<String>,
}

/// A leased run once every body settled and the acquired workers were released or kept.
#[must_use]
pub(crate) struct Leased<P, T> {
    pub(crate) holder: String,
    /// The borrowed worker first, then the acquired ones in acquisition order.
    pub(crate) held: Vec<HeldWorker>,
    /// How many of `held` are borrowed: none or one.
    pub(crate) borrowed: usize,
    /// What the division answered.
    pub(crate) plan: P,
    /// Per held worker: `None` when the interrupt dropped its body, `Err` when its lifecycle lock refused.
    pub(crate) settled: Vec<Option<Result<T, Refusal>>>,
    /// The signal that abandoned the run, if one did.
    pub(crate) interrupted: Option<&'static str>,
    pub(crate) settlement: Settlement,
}

impl<P, T> Leased<P, T> {
    /// The workers this run acquired, which are all it releases.
    pub(crate) fn acquired(&self) -> &[HeldWorker] {
        &self.held[self.borrowed..]
    }

    /// Whether this run gave `worker` back.
    pub(crate) fn released(&self, worker: &str) -> bool {
        self.settlement
            .releases
            .iter()
            .any(|result| result.worker == worker && result.released)
    }

    /// What became of the acquired leases, as a verdict names it.
    pub(crate) fn verdict_lease(&self) -> VerdictLease {
        VerdictLease {
            workers: self.acquired().iter().map(|item| item.lease.worker.clone()).collect(),
            disposition: self.settlement.disposition,
            receipts: self.settlement.still_held.clone(),
        }
    }

    /// The command that frees each lease still held, for a message.
    pub(crate) fn release_hint(&self, program: &str) -> String {
        self.settlement
            .still_held
            .iter()
            .map(|receipt| release_command(program, receipt))
            .collect::<Vec<_>>()
            .join(", ")
    }
}

impl Host {
    /// One invocation of `command` with an id and a journal, as `run` has always had: `runStarted` first,
    /// `runFinished` last with the exit status and the refusal's code. `body` runs under the id, which it is handed.
    ///
    /// The id is [`avl_base::actor_id`], `<user>-<command>-<uuid>`. It names the journal, the trace directory and
    /// the viewer link, and it is the holder of each lease the run takes for itself.
    pub(crate) async fn journaled<T>(
        &self,
        ctx: &Ctx,
        command: &str,
        args: Vec<String>,
        body: impl AsyncFnOnce(&Ctx, &str) -> Result<T, Refusal>,
    ) -> Result<T, Refusal> {
        let run_id = avl_base::actor_id(&self.environment, command);
        let ctx = ctx.clone().with_run_id(run_id.as_str());
        let _journal = journal::attach(&self.settings.runtime_root, &self.reporter, &run_id);
        self.reporter.publish(
            Event::RunStarted(RunStarted {
                run_id: run_id.clone(),
                command: command.to_owned(),
                args,
                checkout: self.environment.get("BUILD_WORKSPACE_DIRECTORY").map(str::to_owned),
            }),
            None,
        );
        let result = body(&ctx, &run_id).await;
        self.reporter
            .publish(Event::RunFinished(journal::run_finished(result.as_ref().err())), None);
        result
    }

    /// The leased run: see this module's header for the order and the two policies.
    ///
    /// `operation` is the lifecycle-lock description a body runs under. `divide` plans the work over the held
    /// workers; `body` runs one worker's share, handed the plan, the worker's index in `held`, the lease as the lock
    /// re-read it, and the build. The answer is a refusal only before the workers are acquired, or when the
    /// division refused, and then only once they were released.
    pub(crate) async fn leased_run<P, T>(
        &self,
        ctx: &Ctx,
        run_id: &str,
        operation: &'static str,
        workers: Workers<'_>,
        divide: impl FnOnce(&[HeldWorker]) -> Result<P, Refusal>,
        body: impl AsyncFn(&P, usize, Lease, &Arc<PreparedBuild>) -> T,
    ) -> Result<Leased<P, T>, Refusal> {
        let settings = &self.settings;
        let holder = workers.holder.unwrap_or(run_id).to_owned();
        let mut held = Vec::new();
        if let Some(path) = workers.borrowed {
            let (receipt, lease) = receipt_for_path(settings, path)?;
            held.push(HeldWorker {
                lease,
                receipt,
                recovered: false,
            });
            // The acquisition checks the guest of every lease it places or recovers; a receipt from the command line
            // says nothing about which guest this controller was invoked for.
            require_one_guest_os(settings, &held)?;
        }
        let borrowed = held.len();
        let wanted = usize::from(workers.count).saturating_sub(borrowed);
        let request = match wanted {
            0 => None,
            wanted => Some(AcquireRequest::new(
                holder.clone(),
                u8::try_from(wanted).unwrap_or(u8::MAX),
                workers.exact,
            )?),
        };

        ensure_host_paths(ctx, &self.runner, settings).await?;
        let built = Arc::new(self.prepare_build(ctx, &BuildScope::pool(settings)?).await?);

        if let Some(request) = &request {
            let label = match wanted {
                1 => format!("one worker, as {holder}"),
                count => format!("{count} workers, as {holder}"),
            };
            let leasing = self.reporter.start_phase(Phase::Lease, &label, None);
            let acquired = acquire_workers(ctx, &self.manager, request).await;
            leasing.finish(&acquired);
            for item in acquired.iter().flatten() {
                self.reporter.note(
                    format!("leased {} as {}", item.lease.worker, item.lease.holder),
                    Some(&Scope::worker(&item.lease.worker)),
                );
            }
            held.extend(acquired?);
        }

        let plan = match divide(&held) {
            Ok(plan) => plan,
            Err(refusal) => {
                self.release_acquired(ctx, &held[borrowed..]).await;
                return Err(refusal);
            }
        };

        let (settled, interrupted) = self.fan_out(ctx, operation, &held, &plan, &built, &body).await;

        let settlement = if interrupted.is_some() {
            Settlement {
                disposition: LeaseDisposition::Kept,
                releases: Vec::new(),
                still_held: held[borrowed..]
                    .iter()
                    .map(|item| item.receipt.to_string_lossy().into_owned())
                    .collect(),
            }
        } else {
            self.release_acquired(ctx, &held[borrowed..]).await
        };
        Ok(Leased {
            holder,
            held,
            borrowed,
            plan,
            settled,
            interrupted,
            settlement,
        })
    }

    /// Every held worker's body at once, and the signal that abandoned the rest if one did.
    ///
    /// No cancellation between the bodies: one worker's refusal must not take its siblings' answers with it. The
    /// bodies still in flight when a signal arrives are dropped; their children are the interrupt service's to stop.
    async fn fan_out<P, T>(
        &self,
        ctx: &Ctx,
        operation: &'static str,
        held: &[HeldWorker],
        plan: &P,
        built: &Arc<PreparedBuild>,
        body: &impl AsyncFn(&P, usize, Lease, &Arc<PreparedBuild>) -> T,
    ) -> (Vec<Option<Result<T, Refusal>>>, Option<&'static str>) {
        // The whole set is this invocation's while the bodies run, so one body's recreate of the Lima engine does not
        // stop for a sibling's lease.
        let leases: Vec<Lease> = held.iter().map(|item| item.lease.clone()).collect();
        let _held = self.manager.hold_leases(&leases);
        let interrupt = self.interrupts.token();
        let mut settled: Vec<Option<Result<T, Refusal>>> = held.iter().map(|_| None).collect();
        let mut pending: FuturesUnordered<_> = held
            .iter()
            .enumerate()
            .map(|(index, item)| async move {
                let result = with_lease_operation(ctx, &self.manager, &item.lease, operation, async |current: Lease| {
                    Ok(body(plan, index, current, built).await)
                })
                .await;
                (index, result)
            })
            .collect();
        let interrupted = loop {
            if pending.is_empty() {
                break None;
            }
            tokio::select! {
                biased;
                () = interrupt.cancelled() => {
                    break Some(self.interrupts.received().map_or("an interrupt", |signal| signal.name()));
                }
                Some((index, result)) = pending.next() => settled[index] = Some(result),
            }
        };
        drop(pending);
        (settled, interrupted)
    }

    /// Gives back what this run acquired, inside the release phase, which fails naming each receipt still held.
    async fn release_acquired(&self, ctx: &Ctx, acquired: &[HeldWorker]) -> Settlement {
        if acquired.is_empty() {
            return Settlement {
                disposition: LeaseDisposition::Released,
                releases: Vec::new(),
                still_held: Vec::new(),
            };
        }
        let workers: Vec<&str> = acquired.iter().map(|item| item.lease.worker.as_str()).collect();
        let releasing = self.reporter.start_phase(Phase::Release, &workers.join(" "), None);
        let probe = parked_daemon_probe(Arc::clone(&self.settings), self.daemon.clone());
        let releases = release_workers(ctx, &self.manager, acquired, &probe).await;
        let disposition = disposition(&releases);
        let still_held: Vec<String> = releases
            .iter()
            .filter(|result| !result.released)
            .map(|result| result.receipt.to_string_lossy().into_owned())
            .collect();
        releasing.finish(&release_error(&self.reporter.program(), disposition, &still_held));
        Settlement {
            disposition,
            releases,
            still_held,
        }
    }
}
