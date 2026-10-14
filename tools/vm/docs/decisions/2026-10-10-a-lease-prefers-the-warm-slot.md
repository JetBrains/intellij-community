---
topic: testing
---

# A lease prefers the warm slot

Date: 2026-10-10

## Status

Accepted. It builds on [the guest reads no checkout](2026-10-10-the-guest-reads-no-checkout.md), which lets one slot serve every checkout of
the machine. It also builds on [ADR 0106](0106-a-warm-daemon-survives-a-lease-release.md), which keeps the warm
daemon after a release. The operator view is
[the VM guide](../vm-ui-tests.md#the-four-guests).

## Context

`lease acquire` took the first free slot in the pool order. It did not prefer a running slot, or the slot whose
daemon already serves the build. With five checkouts on four slots, a run often landed on a cold or an absent slot
while a warm one was free. A cold slot costs a container start or a clone, and a daemon start.

## Decision

**An acquisition ranks the free slots before it places a lease.** The classes, from the first choice:

1. a running slot whose daemon record names the launch digest of the build that asks (`warm-daemon`);
2. a running slot with another daemon, or with none (`running`);
3. a stopped slot, which keeps its volume or its disk (`stopped`);
4. a slot with no machine (`absent`).

- **Within one class the pool order stays.** `rank_slots` is a stable sort by class, and a pure function of its input,
  in `crates/avl-vm/src/worker/lease/set.rs`. A one-slot pool, and every pool of one class, keeps its order.
- **The probes start nothing.** `Machine::slot_states` asks Tart for its run process and its VM list. It asks Docker
  for `inspect` once the engine answers, and a Docker engine that is down is not started. Parallels and
  `container-linux` hold one slot and answer no state. A slot without a state, or with a probe that failed, is class 2
  (`unranked`), so it keeps its place. `Manager::slot_preference` adds the daemon record that `run` compares its launch
  digest with.
- **The probes run only for a choice.** One free slot asks the backend nothing.
- **A shard uses the same ranking.** Each pending shard takes the next slot of it.
- **The holder recovery comes first.** A holder that already has a lease gets it back, whatever the ranking says
  (`recovered`).
- **Only a caller that built knows the digest.** `run`, `shard` and `flake` build before they lease, so they ask with
  their launch digest (`AcquireRequest::for_build`). `lease acquire` has no build, so its ranking has no class 1.
- **The reply names the reason.** Each lease of the `lease acquire` reply has `slotReason`, and the text reply adds
  `slot_reason=`. A lease that the caller named with `--lease-file` is `borrowed`.

## Consequences

- **A run reuses a warm slot when one is free.** Two checkouts that alternate keep their containers, and the run
  pays the daemon restart only, because the launch digest differs per checkout.
- **An acquisition of two or more free slots probes each one.** On Docker that is one `inspect` for each slot, under
  the pool-wide acquisition lock. The lock waits 10 s, so the probes of a full pool stay well inside it.
- **Not verified here.** The two-checkout check of the plan, a lease from one checkout, a release, and a lease from
  the second, has not run. Its expected answer is the same running slot with `slotReason` of `running`.
