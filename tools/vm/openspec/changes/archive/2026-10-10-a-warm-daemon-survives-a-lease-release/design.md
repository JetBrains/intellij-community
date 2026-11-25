# A lease release keeps a parked warm daemon, and an acquisition prefers the warm slot

## Context

A worker lease is the mutual exclusion between agents that share one worker pool. `lease release` is a gate. It
proves that no run uses the worker before the next holder takes it.

The lane daemon of the guest agent takes the run slot of the guest supervisor with one start. It keeps the slot until
it stops. Iterations do not pass through the supervisor. They go over the control channel of the daemon. So a run id
in the slot names the owner of the worker, but it does not tell whether work is in flight. A gate that reads only the
slot refuses every release on a worker with a warm daemon. A warm daemon is the thing that the next lane reuses.

The pool is per machine, and several checkouts share its slots. One slot serves every checkout, because
[the guest reads no checkout](../2026-10-10-the-guest-reads-no-checkout/design.md). An acquisition that takes the
first free slot often lands on a cold or an absent slot while a warm one is free. A cold slot costs a container start
or a clone, and a daemon start.

The contract is in
[the worker lease and the warm daemon](../../../../spec/lane-controller.spec.md#the-worker-lease-and-the-warm-daemon).

## Decisions

### The release

1. **An executing run refuses a release. A parked daemon of this controller is not an executing run.**
2. **The run slot has three dispositions:** `Free`, `ParkedDaemon` and `Executing` (`RunSlot`).
   `Guest::judge_run_slot` decides with an injected `ParkedDaemonProbe`. Both are in
   [supervisor.rs](../../../../crates/avl-host-sys/src/guest/supervisor.rs). The probe compares the account of the
   daemon with the daemon that this controller recorded for the worker. Every doubt answers `Executing`: no record,
   another run in the slot, a daemon that does not answer, a refused protocol version, an iteration in flight. A
   refused release costs a retry. A worker freed under an iteration costs the run of another agent.
3. **The probe is injected.** The lease code cannot import the daemon code, because a daemon operation takes a lease.
   So the trait lives beside the slot state, and the daemon module implements it. A caller without a probe gets
   `Executing` for every holder.
4. **`exec` keeps the strict check** (`Guest::reject_active_run`). A parked daemon holds a live IDE, and an
   arbitrary guest command can take its pointer, its focus or its screen. `daemon start` asks the slot nothing. It
   stops the recorded daemon and retires a daemon run that no record names.
5. **The refusal names what the controller found.** Work in flight names that run. A recorded daemon that does not
   answer as idle names `daemon stop`. A daemon run that no record names also names `daemon stop`. The record read
   asks the daemon nothing, and it serves only the message. The code stays `run_active` for every shape, because
   callers already retry on it.
6. **A command that leases for itself reports a three-valued disposition:** `released`, `kept` or `release_failed`
   (`LeaseDisposition`). A run that still holds a lease never reports it as released. The exit code follows the
   verdict, because a green lane with a failed release must not make an agent run the suite again.
7. **A start that never reached its daemon leaves no stranded run.**
   - A start writes its daemon record before the health poll, so `daemon log` reads a failed boot.
   - A start that fails after the supervisor started its run cancels that run.
   - `daemon stop` and every start retire a daemon run that no record names, under the lifecycle lock.
   - `daemon stop` keeps a record while its run still holds the slot after a failed cancel, and refuses
     `daemon_retire_failed`.

A run in flight is kept away from a release by its run marker, not by the run slot. The slot check covers an
iteration that a dead controller left in the guest. See
[a run marks its worker](../2026-10-13-a-run-marks-its-worker-and-holds-the-lock-at-its-ends/design.md).

### The acquisition

1. **An acquisition ranks the free slots before it places a lease.** The classes, from the first choice:
   `warm-daemon` (a running slot whose daemon record names the launch digest of the build), `running`, `stopped`
   and `absent`.
2. **Within one class the pool order stays.** `rank_slots` is a stable sort and a pure function, in
   [set.rs](../../../../crates/avl-vm/src/worker/lease/set.rs).
3. **The probes start nothing.** `Machine::slot_states` asks Tart and Docker about their machines, and a Docker
   engine that is down stays down. Parallels and `container-linux` answer no state. A slot without a state is
   `unranked` and keeps its place in the `running` class.
   `Manager::slot_preference` adds the daemon record.
4. **The probes run only for a choice.** One free slot asks the backend nothing.
5. **A shard uses the same ranking.** A holder recovery comes first, whatever the ranking says (`recovered`).
6. **Only a caller that built knows the launch digest.** `run`, `shard` and `flake` ask with
   `AcquireRequest::for_build`. `lease acquire` has no build, so its ranking has no `warm-daemon` class.
7. **The reply names the reason.** Each lease has `slotReason`, and a lease named with `--lease-file` is `borrowed`.

## Consequences

- A lane ends without giving up its warm daemon. `daemon stop` before `lease release` is no release procedure.
- On Apple `container` a released worker keeps its daemon only until the idle deadline. See
  [an idle container worker stops itself](../2026-10-10-an-idle-container-worker-stops-itself/design.md).
- `daemon stop` retires the daemon run of a dead controller with or without a record. Any other run needs a person.
- Two checkouts that alternate keep their containers. The run pays a daemon restart only, because the launch digest
  differs per checkout.
- An acquisition of two or more free slots probes each one under the pool-wide acquisition lock. On Docker that is
  one `inspect` for each slot.
