---
topic: testing
---

# 110. The truncation chain is a daemon kill under load

Date: 2026-08-26

## Status

Accepted. Records the loaded complement of the quiet twelve-trial baseline in the
[VM guide](../vm-ui-tests.md#what-it-costs), and what it proved
about the lane-truncation chain first seen on 2026-08-23. The sender was named the same day and the
controller-side fix landed with it; see **Consequences**. What is left belongs to Cirrus's own binary, and
[`ui-lane-controller-todo.md`](../ui-lane-controller-todo.md) keeps it under `## Not ours`.

## Context

The chain was first described as three steps: a class fails, the failure triggers a recycle, and the
replacement IDE misses its 20 s RD bind. Every class after it then reports a skipped container, and the
truncation reporting blames whichever class the recycle landed on. Both original observations were taken
on a loaded host. The quiet half ran first: twelve warm `--reset none` trials on a host verified quiet
passed twelve of twelve, with no truncation and `orderSuspects` empty. One experiment remained — the same
twelve trials with a second lane deliberately running.

## The hardware evidence

Run on 2026-08-26, twice, on `air-linux-3`, with a second `--lane ui` looping on `air-linux-2` and a peer
session's lease on `air-linux-1` — three VMs on one host.

- **Run 1 died of the phenomenon.** Trials 1-4 passed. Trial 5 ended as an infrastructure error: the
  daemon answered HTTP and had no result for the iteration. The controller restarted the daemon, and the
  replacement died 18 s into trial 6, its result endpoint refusing connections. The daemon log names the
  mechanism: the daemon JVM caught a SIGTERM during the IDE launch — *"terminating process
  `run-ide-MONOLITH` by request from external process (either SIGTERM or SIGKILL is caught)"*. No test
  failed and no container was skipped in either dead trial. The honesty guard withheld the rate, because
  2 exclusions in 6 trials is over the 20 % floor, and the run left with exit 70.
- **Run 2 passed under the same load.** Twelve of twelve on the same worker, reportable, a lane flake
  rate of 0, and `orderSuspects` empty. Warm trials cost 214-286 s against the quiet 194-229 s.
- **The load lane recycled through its own failures.** 28 iterations: 24 green, three red, and one daemon
  loss of its own. Each red iteration recycled its IDE, and the next iteration was green.

## Decision

**The chain is one event, a daemon kill under load. The three-step story was the observer, not the
mechanism.**

- A class failure does not enter the chain. Three load-lane failures recycled and recovered.
- The chain does not need a class failure. Both run-1 deaths carried zero failures and zero skipped
  containers.
- Load stays required. The quiet twelve had no daemon loss; the loaded executions had three in 46 lane
  executions over two workers.
- Load is not sufficient. A full loaded twelve passed with nothing excluded.

Quote the counts, 3 of 46 loaded against 0 of 12 quiet, rather than a rate. The first loaded run is
censored evidence, because its own death ended it at trial 6.

## The sender, named the same day

The guest journal of the boot that killed both daemons names the sender, so the suspicion phase was
skipped. `tart-guest-agent` — the vsock exec channel — exited with *"context canceled"* at 16:09:29 and
its replacement exited again at 16:09:49, and the two daemon deaths sit on those two seconds exactly.
The unit runs with systemd's default `KillMode=control-group`, so an agent exit makes systemd SIGTERM
every process left in the unit's cgroup. The daemon and its IDE are in that cgroup, because the exec
channel spawned them, and a detached session stays in its parent's cgroup. The journal holds no OOM, no
`systemd-oomd`, and no `earlyoom` entry, so memory pressure is ruled out.

The first death took a daemon that was minutes past its launch, which is the proof that no detach short
of a cgroup move survives this.

## Consequences

- **The controller-side fix landed the same day, in the commit *"fix air vm: the daemon survives its launch
  channel"*.** On a Linux guest the `start` verb now asks systemd for a transient service of its own,
  `air-supervise-<run id>.service`, and the supervisor plus the daemon plus the IDE live in that unit's cgroup.
  `crates/avl-guest/src/supervisor/launch.rs` owns the composition and states why a `systemd-run --scope` is not
  enough: a scope keeps the `sudo` that asked for it as the parent, that parent stays in the agent's cgroup, and a
  measured trial on `air-linux-3` lost the scope's process together with a `setsid` one. The same trial kept the
  transient service alive across a `systemctl restart tart-guest-agent`.
- Why the agent's vsock context cancels under load is inside Cirrus's `tart-guest-agent`, and is not this
  repository's to fix. The daemon now survives that exit, so the cancel costs a reconnect instead of a
  lane. `ui-lane-controller-todo.md` keeps that half under `## Not ours`.
- `orderSuspects` keeps its reading rule: it can name a class that a dead daemon killed, so read it only
  after a check that the trials it compares actually ran.
- The raw evidence — both runs, the daemon log, the journal windows, and the unit definition — sits under
  `out/p8/`, which `bazel clean` removes. This ADR is the durable record.
