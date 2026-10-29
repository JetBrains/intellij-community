---
topic: testing
---

# A run marks its worker and holds the lock at its ends

Date: 2026-10-13

## Status

Accepted. It amends [ADR 0106](0106-a-warm-daemon-survives-a-lease-release.md). Its section "The lifecycle lock already
excludes a live iteration" no longer holds, and this record replaces it. The operator view is
[the VM guide](../vm-ui-tests.md#artifacts-and-diagnostics).

## Context

The lifecycle lock of a worker, `<worker>/lease-operation.lock`, had two jobs. It excluded a second lease or lifecycle
operation on the worker. It also marked a run in flight, because a run held it for the whole run.
`with_lease_operation` in `crates/avl-vm/src/worker/lease.rs` held the lock for the length of its action. A run under
a receipt, the fan-out of `run`, `shard` and `flake`, and the `daemon` verbs all ran inside that action.

The second job had three costs:

1. **The holder could not watch its own run.** With the receipt of its lease, `daemon status`, `daemon log`,
   `lease show`, `ls` and `pull` took the same lock. So each answered `lease_busy` until the run ended.
2. **Every scan of the pool had to read a held lock as a busy worker.** On 2026-10-13 two commits patched the two
   scans: "a lease acquisition skips a busy worker and leases a free one" and "pool gc keeps a busy worker as busy
   and goes on with the other workers".
3. **The release safety rested on the lock.** ADR 0106 says that a release cannot interleave with an iteration,
   because both take the lock. The guest check of the run slot covers only a controller that crashed. So a shorter
   lock needs another guard against a release during a run.

A run marker needs a process identity, and the controller had two already. The owner record of a lock named a pid and
a start time. The Tart pid receipt named a pid, a start time and a command, and its liveness check treated a zombie as
dead.

## Decision

**A run writes a marker file on its worker, and the lifecycle lock stays short.** The lock excludes a second operation
while one operation changes the worker. The marker names the run for the whole length of the run.

Three shapes in `crates/avl-vm/src/worker/lease.rs` act on a leased worker, and each re-reads the lease under the lock:

- `with_lease_operation` holds the lock for the whole action, and it refuses a live run of another process.
  `lease release`, `exec`, `peekaboo` and the `daemon` verbs `start`, `restart` and `stop` take it.
- `with_lease_read` holds the lock for the re-read only, and runs the action without it. `lease show`,
  `daemon status`, `daemon log`, `ls`, `pull` and `vnc` take it.
- `with_run_marker` holds the lock only at the two ends of a run. A run under a receipt and the fan-out of `run`,
  `shard` and `flake` take it.

Since 2026-10-24: `with_lease(.., LeaseUse::LockedAction, ..)` replaces `with_lease_operation`, and
`with_lease(.., LeaseUse::LockedReread, ..)` replaces `with_lease_read`.

The rules of the marker:

1. **The marker is a file of the worker.** It is `<runtime root>/workers/<key>/run.json` (`Config::run_marker_path`),
   mode 0600, written atomically. Its fields come in this order:
   - `schemaVersion` is the version of the format, and `worker` names the worker.
   - `runId` is the id of the run. The journal of the run names the same id.
   - `operation` is the word of the lock record: `run`, `shard-run` or `flake`.
   - `leaseDigest` is the hex SHA-256 of the lease token. So the marker holds no second clear copy of the secret.
   - `process` is the identity of the process of the run: `pid`, `processStart` and `processCommand`.
   - `startedAt` is a UTC stamp with milliseconds, the form that the lease documents use.

   Every field is required. A file that does not read as a complete marker refuses `corrupt_run_marker` (exit 1), as an
   unreadable lease refuses `corrupt_lease`. A marker that cannot be read never reads as "no run".
2. **A marker is live while its process is.** `judge_run_marker` answers `None`, `Ours`, `Live` or `Stale`
   (`RunMarkerState`), and it changes nothing.
   - `Ours` is a marker of the process that the lock manager speaks for. The whole identity decides, not the pid
     alone. So a marker of a dead process whose pid the host gave to this process is stale, not ours.
   - Any other marker is `Live` while the process table answers its recorded start time and command for its pid
     (`Runner::identity_alive`). A process whose state starts with `Z` is a zombie, and it counts as dead. Otherwise
     the marker is `Stale`.
   - A probe without an answer is the refusal `probe_unanswered`, and not a stale marker. On Windows the process
     object answers through `OpenProcess`, so the same fields work there.
3. **A live run of another process blocks every command that changes its worker.** The guard is
   `Manager::require_no_run`, and it runs under the lifecycle lock. `lease release`, `pool start`, `pool stop`, `exec`,
   `peekaboo`, `daemon start`, `daemon restart` and `daemon stop` refuse on a live marker. A second run of the same
   lease refuses too, whether it is a `run`, a `shard` or a `flake`.
   - The code stays `lease_busy`, exit 75. That is the code that the held lock gave during a run, so a caller that
     retries on it needs no change. ADR 0106 keeps `run_active` for every busy release for the same reason.
   - The message names the run: `worker <worker> has run <runId> in flight (pid <pid> since <startedAt>); wait for it to finish`.
     The details name `worker`, `operation`, `runId`, `pid` and `startedAt`, where `operation` is the refused operation.
   - A stop checks the run before the lease. So `pool stop <worker>` during a run refuses `lease_busy` and names the
     run. After the run, a stop of a worker that another holder leases refuses `worker_leased` as before.
   - `pool init` and `pool recycle` refuse a leased worker as `worker_leased`, as before. A run holds a lease, so the
     lease guard blocks them.
   - A holder recovery of `lease acquire` refuses `worker_busy` instead:
     `<worker> holds a lease of this holder, and its run <runId> is in flight (pid <pid> since <startedAt>); retry when the run ends`.
     The survey reads the lease under the lifecycle lock, and the recovery takes that lock again to judge the marker.
     So the removal of a stale marker cannot race the write of a new marker.
   - A marker of this process passes. A run restarts its own worker under its own marker, and an authorized stop
     from inside a run finds that marker.
4. **A read answers during a run.** `lease show`, `daemon status`, `daemon log`, `ls`, `pull` and `vnc` do not refuse on
   a live marker. `status`, `pool gc` and `idle-stop` do not read the marker. A run holds a lease, and `pool gc` and
   `idle-stop` already keep a leased worker.
   - `ls` and `pull` keep their readiness gate under the lock (`require_ready_for_read`). The gate can start or
     restart the worker. While a run of another live process is in flight, the gate only resolves the host paths,
     because the run keeps the worker ready. The read itself runs without the lock.
   - With a receipt, `lease show` names the run in flight on the worker of that lease. The JSON row gains `run`, and
     the text line ends with `run=<id>`. A stale marker names no run.
5. **A marker of a dead process is stale, and the next guard removes it.** Every guard under the lock removes a stale
   marker with a note: `removed the run marker of <runId> on <worker>: its process <pid> is gone`. These guards are
   `lease release`, `pool start`, `pool stop`, `exec`, `peekaboo`, the `daemon` verbs that change the daemon, a run
   start and a holder recovery. The reads leave a stale marker in place. The guest check of the release
   (`reject_executing_run`) still decides about an iteration that the dead process left in the guest.
6. **A nested marker is an internal error.** A run that finds a marker of its own process on its worker refuses
   `internal_error`: `a run of this process is already in flight on <worker>`. A nested run would remove the marker of
   the outer run at its end. The source guard of the flake chain still forbids `command_run` and `command_daemon`
   inside a chain.
7. **The end of a run never changes its answer.** The end takes the lock queued for `ACQUIRE_LOCK_TIMEOUT`, 10 s,
   because only short holders can be in the way. It removes the marker only when the marker names this run and this
   process, by the run id and the whole identity. A marker that a release of this process removed already is no
   error. When the end cannot remove the marker, a note says so:
   `the run marker of <runId> stays on <worker>: <why>; it goes stale with this process`. The reason is an interrupt,
   a lock that stayed busy for the whole wait, a marker of another run, or a refusal.
8. **An interrupt keeps the marker, as it keeps the lease.** The iteration of an interrupted run can still be in flight
   in the guest. A run under a receipt sees the cancelled context at its end, and it keeps the marker with a note. The
   fan-out drops its bodies on an interrupt, so their ends never run. Either way the marker goes stale with the
   process, and the next guard removes it.
9. **One process identity serves three records.** `ProcessIdentity` in `crates/avl-host-sys/src/proc/identity.rs`
   holds `pid`, `processStart` and `processCommand`. The Tart pid receipt (`PidReceipt`), the owner record of a lock
   (`OwnerRecord`) and the run marker carry it.
   - The pid receipt flattens the identity, so `tart.pid` keeps its JSON. The lock file gains the key
     `processCommand`.
   - The pid receipt and the run marker share one liveness check, `Runner::identity_alive`, with its zombie rule. The
     owner record of a lock only describes its holder, and nothing reads it back to decide.
   - The lock manager finds the identity of its process once for each invocation (`LockManager::identity`). So the
     lock records and the run marker of one invocation name the same identity.

## Consequences

- **A holder can watch its own run.** With the receipt of the run, `lease show` names the run as `run=<id>`.
  `daemon status`, `daemon log`, `ls` and `pull` answer too.
- **A command that changes the worker waits for the run.** `lease release`, `pool start`, `pool stop`, `exec`,
  `peekaboo` and the `daemon` verbs `start`, `restart` and `stop` refuse `lease_busy`. The message names the run, its
  pid and its start.
- **A scan of the pool sees the worker of a run as leased, not busy.** The lock is busy only while a lifecycle
  operation runs, such as a start or a release. So the acquisition skips that worker as leased, and `pool gc` keeps it
  as `leased`.
- **A crash leaves a stale marker, and the next guard removes it.** After a `kill -9` of the controller, the marker
  names a dead process. The next guarded operation removes it with a note. The guest check of the release still
  refuses `run_active` while the daemon runs the iteration that the dead controller started.
- **A holder recovery waits for the run.** `lease acquire --holder <holder>` refuses `worker_busy` while a run of that
  lease is in flight, and the message names the run.
- **A corrupt marker stops the commands that check it.** They refuse `corrupt_run_marker`, exit 1. An operator removes
  the file when no run is in flight on that worker.
- **The lock file and `lease show` gain a field.** The owner record of a lock gains `processCommand`. The row of
  `lease show` for the caller's own lease gains `run`.
- **Two things stay as they were.** `status` names no run. A read refuses `lease_busy` at once while another operation
  holds the lock, and it does not queue.
- **Verified on a real worker on 2026-10-13.** On the Docker pool on Apple `container`, during a run of eight
  scenarios, `lease show` named the run, and `daemon status`, `daemon log` and `ls` answered. `lease release`,
  `daemon restart` and `pool start` refused `lease_busy` and named the run, its pid and its start.
  `lease acquire --holder` refused `worker_busy`, and `pool stop all` answered `stop_incomplete` with that worker in
  `refused`. After the run the marker was gone and the release passed. A `kill -9` of the controller during a run left
  the marker on disk, and `lease show` named no run. The first `lease release` removed the stale marker, and it
  refused `run_active` while the daemon ran the iteration. The daemon went idle after 150 s, and the release passed.
