# A run marks its worker with a marker file, and the lifecycle lock stays short

## Context

The lifecycle lock of a worker excludes a second lease or lifecycle operation on that worker. If a run holds the lock
for its whole length, the lock also marks the run in flight. That second job has costs:

- The holder cannot watch its own run. `daemon status`, `daemon log`, `lease show`, `ls` and `pull` take the same
  lock, so each answers `lease_busy` until the run ends.
- Every scan of the pool reads a held lock as a busy worker, and each scan needs its own exception.

A short lock needs another guard against a release during a run. The guest check of the run slot covers only an
iteration that a dead controller left. See
[a warm daemon survives a lease release](../2026-10-10-a-warm-daemon-survives-a-lease-release/design.md).

The controller already records a process identity in two places: the owner record of a lock and the Tart pid receipt.
The receipt has a liveness check that reads a zombie as dead.

The contract is in
[the worker lease and the warm daemon](../../../../spec/lane-controller.spec.md#the-worker-lease-and-the-warm-daemon).

## Decisions

**A run writes a marker file on its worker, and the lifecycle lock excludes only a concurrent change.** The marker
names the run for its whole length.

### The three lease shapes

Each shape in [lease.rs](../../../../crates/avl-vm/src/worker/lease.rs) re-reads the lease under the lock:

- `with_lease(.., LeaseUse::LockedAction, ..)` holds the lock for the whole action, and refuses a live run of another
  process. `lease release`, `exec`, `peekaboo` and the `daemon` verbs `start`, `restart` and `stop` take it.
- `with_lease(.., LeaseUse::LockedReread, ..)` holds the lock for the re-read only. `lease show`, `daemon status`,
  `daemon log`, `ls`, `pull` and `vnc` take it.
- `with_run_marker` holds the lock only at the two ends of a run. A run under a receipt and the fan-out of `run`,
  `shard` and `flake` take it.

### The rules of the marker

1. **The marker is a file of the worker:** `<runtime root>/workers/<key>/run.json` (`Config::run_marker_path`),
   mode 0600, written atomically. It names the worker, the run id, the operation, the SHA-256 of the lease token,
   the process identity and the start time. Every field is required. A file that is not a complete marker refuses
   `corrupt_run_marker`, so an unreadable marker never reads as "no run". The type is `RunMarker` in
   [run_marker.rs](../../../../crates/avl-vm/src/worker/run_marker.rs).
2. **A marker is live while its process is.** `Manager::judge_run_marker` answers a `RunMarkerState` and changes
   nothing. The whole identity decides, so a reused pid does not make a stale marker live. A zombie counts as dead.
   A probe without an answer refuses `probe_unanswered`. It is not a stale marker.
3. **A live run of another process blocks every command that changes its worker.** `Manager::require_no_run` is the
   guard, under the lock. The code stays `lease_busy`, so a caller that retries on it needs no change. The message
   names the run, its pid and its start. A holder recovery of `lease acquire` refuses `worker_busy`. A marker of this
   process passes, so a run can restart its own worker.
4. **A read answers during a run.** The reads do not check the marker. `ls` and `pull` keep their readiness gate
   under the lock (`require_ready_for_read`). With a receipt, `lease show` names the run in flight.
5. **A stale marker goes at the next guard.** Every guard under the lock removes a stale marker with a note. The
   reads leave it in place.
6. **A nested marker is an internal error.** A run that finds a marker of its own process refuses `internal_error`,
   because a nested run would remove the marker of the outer run.
7. **The end of a run never changes its answer.** The end waits for the lock up to `ACQUIRE_LOCK_TIMEOUT`. It
   removes the marker only when the marker names this run and this process. Otherwise a note says that the marker
   stays.
8. **An interrupt keeps the marker, as it keeps the lease.** The iteration can still run in the guest. The marker
   goes stale with the process.
9. **One process identity serves three records.** `ProcessIdentity` in
   [identity.rs](../../../../crates/avl-host-sys/src/proc/identity.rs) is carried by `PidReceipt`, `OwnerRecord` and
   the run marker. The receipt and the marker share `Runner::identity_alive`. The lock manager finds the identity of
   its process once for each invocation.

## Consequences

- A holder can watch its own run. `lease show` names it, and the other reads answer.
- A scan of the pool sees the worker of a run as leased, not busy. The lock is busy only during a lifecycle
  operation.
- After a crash of the controller, the marker names a dead process, and the next guard removes it. The guest check of
  the release still refuses `run_active` while the daemon runs the orphaned iteration.
- A corrupt marker stops the commands that check it. An operator removes the file when no run is in flight.
- `status` names no run. A read refuses `lease_busy` at once while another operation holds the lock.
