---
topic: testing
---

# 106. A warm daemon survives a lease release

Date: 2026-08-24

## Status

Accepted. Describes `lease release` in `//plugins/air/tests/integration/vm-lane` —
`crates/avl-worker/src/lease.rs`, the run-slot vocabulary in `crates/avl-wire/src/supervisor.rs`, and the probe in
`crates/avl-daemon/src/slot.rs`. Why the daemon exists at all is
[ADR 0104](../../../../../plugins/air/docs/decisions/0104-the-daemon-caches-a-process-not-a-build.md); why the controller was Go is
[ADR 0105](0105-the-ui-lane-controller-is-go.md), and why it is Rust is
[ADR 0059](0059-the-ui-lane-tooling-is-rust.md); how the lane is driven is
[the VM guide](../vm-ui-tests.md). This ADR records why a release stopped asking the run slot alone.

## Context

A worker lease is the mutual exclusion between agents sharing one worker pool, and `lease release` is a gate
rather than a delete: it exists to prove no run is still using the worker before the next holder can take it.
That gate was `RejectActiveRun` — refuse while *anything* holds the guest run supervisor's slot.

A daemon takes that slot with one `supervisor start` and keeps it until it dies. Iterations do not pass through
the supervisor at all; they go over the daemon's own HTTP control channel. So a run id in the slot says who owns
the worker, and says nothing about whether work is happening. A gate reading only the slot therefore refused
every release on a worker with a warm daemon on it — the exact opposite of the design, which is that a warm
daemon is what a following lane reuses.

### The hardware evidence

Confirmed on 2026-08-23, and not inferred:

- At the moment of refusal `daemon status` answered `busy: false`, `iterationCount: 1`, `ideRunning: true`. No
  iteration was in flight. The refusal nonetheless named the *finished* run
  (`run-ui-daemon-015546b8-…`), which reads as though something were executing. That message was the second
  defect: it named a real fact in a way that told the operator the wrong thing.
- **Not intermittent.** Four for four on the macOS workers — after the solo lane, and after each of the two
  concurrent pairs. On Linux a `flake` run's lease was still held when the next run's preflight looked for it.
  Neither worker-specific, nor guest-specific, nor a race.
- **Reproduced under both implementations.** During the parity comparison the refusal appeared identically
  under the TypeScript and the Go controller, so it was one defect and not two. `daemon stop` before
  `lease release` was the documented workaround until this changelist, and it costs the warm IDE every time.

## Decision

**A release is refused by an executing run, and a parked daemon of this controller's own is not one.**

### The slot is not the only thing asked

`host/guest` now names three dispositions of one worker's run slot — `RunSlotFree`, `RunSlotParkedDaemon`,
`RunSlotExecuting` — and `JudgeRunSlot` decides between them. The slot cannot decide by itself, so the judgment
takes a `ParkedDaemonProbe`: the daemon's own account of itself, compared against the daemon this controller
recorded for that worker. Every way of failing to establish "parked" answers `RunSlotExecuting` — no record,
another run in the slot, a daemon that does not answer, a daemon whose protocol version this controller refuses,
an iteration in flight. The direction is deliberate. Refusing a release costs a retry; freeing a worker out from
under an iteration costs somebody else's run.

### The lifecycle lock already excludes a live iteration

The slot check is not what keeps a release away from work in progress, and it never was. Every iteration path
takes the worker's lifecycle lock (`crates/avl-daemon/src/command.rs`, `shard/command.rs`, `flake/command.rs`,
through `leased.rs`), and `CommandLeaseRelease` takes the same lock — `WithLifecycleLock` refuses rather than
queues, so two lifecycle operations on one worker cannot interleave at all.

What the lock cannot cover is a controller that died holding neither the lock nor its run: a crashed shard, a
killed process, a machine that rebooted. That residue is the slot's remaining job, and it is why the slot is
still consulted rather than dropped.

### `daemon start` and `observe exec` keep the strict check

Both keep `RejectActiveRun` unchanged, for different reasons.

- `daemon start` is about to take the slot itself. A parked daemon in it is precisely the thing it must not
  start on top of; it stops the recorded daemon first, retires a daemon run no record names, and then finds
  the slot empty.
- `observe exec` runs an arbitrary command in the guest. A parked daemon still holds a live IDE, and a guest
  command can take that IDE's pointer, focus or screen — the same line `crates/avl-lane/src/observe/pull.rs`
  already draws between reading a file out of a guest and acting inside one. `host/observe/observe_test.go` is
  unchanged in this changelist on purpose: an untouched suite is the proof that `exec` was not widened along
  with the release.

### The probe is injected, not imported

`host/lease` cannot import `host/daemon` — that edge runs the other way, since a daemon operation leases. So
`ParkedDaemonProbe` is declared in `host/guest` beside the state it judges and implemented in `host/daemon`,
exactly the seam `BazelHost` uses to keep Bazel out of `host/guest` (`host/lane.Bazel` implements it). A lease
operation therefore asks one question and never learns the daemon protocol. `cmd/vm` is the production
construction site; `host/shard` and `host/flake` build the same probe for the releases they perform on their own
behalf.

A nil probe answers `RunSlotExecuting` for any holder. That is the pre-probe behaviour, and it is the right
answer for a caller that cannot ask: with no probe there are no grounds to call a holder idle.

### The refusal names what was found

Three shapes, because a reader acts differently on each:

- The record names some other run than the slot's holder, or names nothing: work is in flight, and the refusal
  names that run.
- The record names the very run holding the slot, and the daemon behind it does not answer as idle: the refusal
  names that record and says `daemon stop` retires it.
- The slot holds a `run-ui-daemon-` run and no record names it: a start that could not retire its own run, or a
  controller that died. The refusal names `daemon stop`.

Reading the record is a second, non-failing question on the same probe — it asks the daemon nothing. It exists
for the message rather than for the decision, because naming the wrong one of those two cases is the half of
this defect that read as a lie.

### The disposition a sharded run reports

The downstream half of the defect is `shard`'s envelope: `leaseDisposition` answered `released` while
`releaseFailures` carried the refusal, so a caller reading the disposition believed leases had gone back when
they had not, and a `shard` process that then exited stranded them. **The decision is a three-valued
disposition** — `released`, `kept`, `release_failed` — derived from what each release actually answered, with a
run that still holds a lease never reporting it as released. Both halves landed on this one decision rather
than on two: `shard` derives the disposition per lease and prints a `still leased <receipt>` line only when
something stayed held, and `flake` gives back only the workers it leased and names every release it could
not complete. The exit code stays verdict-driven in both, because a green lane with a housekeeping problem
must not make an agent re-run a suite that already answered.

### A start that never reached its daemon (2026-09-27)

Under a host load above 50, three starts in a row published a state file and then never answered `/status`
within the health poll. Each left its run in the slot with no record, so no verb could retire it: `daemon stop`
had nothing to stop, and `daemon restart` and `lease release` refused `run_active`.

- A start writes its record before the health poll, so `daemon log` reads a failed boot.
- A start that fails after the supervisor started its run cancels that run.
- `daemon stop` and every start retire a daemon run no record names, under the lifecycle lock.
- `daemon stop` keeps a record while its run still holds the slot and the cancel did not finish it. It refuses
  `daemon_retire_failed`, so no failed cancel recreates an unrecorded run.
- `run_active` stays the single code for every refusal shape.

A later live walk that day found every probe failing with "No route to host" while `tart exec` worked. That was
macOS Local Network privacy on the controller's parent app, not the load, so a `no_connection` refusal named the
host-side checks too. [ADR 0182](0182-the-daemon-is-reached-through-the-exec-channel.md) moves the daemon
connection onto the exec channel, so those checks no longer apply.

## Consequences

- A lane can end without giving up its warm daemon, which is what the 82 s warm restart and the 11 s warm
  iteration in [ADR 0104](../../../../../plugins/air/docs/decisions/0104-the-daemon-caches-a-process-not-a-build.md) are worth keeping.
- `daemon stop` before `lease release` is no longer the release procedure. The
  [VM guide](../vm-ui-tests.md) states the contract instead, and the defect entry in
  [`ui-lane-controller-todo.md`](../ui-lane-controller-todo.md) is retired.
- `daemon stop` retires a dead controller's daemon run whether or not a record names it; any other run still
  needs a human. The refusal says which situation it is, and `daemon stop` is the command for the common one.
- The refusal code stays `run_active` for every shape. It is what a caller retries against, and splitting it
  would break the callers that already branch on it while telling them nothing they cannot read in the message.
