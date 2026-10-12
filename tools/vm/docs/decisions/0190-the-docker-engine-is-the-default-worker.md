---
topic: testing
---

# 190. The Docker engine is the default worker

Date: 2026-09-30

## Status

Accepted. It supersedes point 7 of [ADR 0183](0183-a-linux-worker-may-be-a-container.md), which kept the Tart Linux
worker as the default until the lane was green on Docker. It also supersedes the one-slot default of point 6 there.
It moves the default of [ADR 0189](0189-the-docker-engine-is-a-lima-vm-the-controller-owns.md). How the lane uses the
worker is [the VM guide](../vm-ui-tests.md).

Superseded in part on 2026-10-08: [ADR 0210](0210-the-docker-image-carries-node-and-the-tart-linux-worker-is-retired.md) removes `--backend linux`, which this record
names as the fallback.

## Context

ADR 0183 made a Docker container a third backend and kept the Tart Linux worker as the default. Its reason was that
no lane had run on Docker. ADR 0189 then gave a Mac an engine that needs no installation: a Lima VM that the
controller owns, with a pinned Docker CLI.

On 2026-09-30, on one host, both workers ran the whole `ui` and `ui-real` lanes. The times are of the tests only,
one iteration per cell:

| lane | Tart Linux `air-linux-1` | Lima engine `air-docker-1` |
|---|---|---|
| `ui`, 40 classes, 71 tests, cold daemon | 750.4 s | 756.4 s |
| `ui`, warm daemon | 860.1 s | 697.0 s |
| `ui-real`, 2 classes, 4 tests | 84.6 s | 70.5 s, 70.4 s |
| median ratio per class, Lima to Tart, over the classes above 2 s, cold | the base | 0.98 |
| the same median ratio, warm | the base | 0.80 |

Each lane was green twice on the Lima engine: `ui` passed 71 of 71, and `ui-real` passed 4 of 4. The two workers
took times of the same order. One iteration per cell shows no spread, so this ADR does not call one of them faster.

The lanes also found two faults, and both are fixed:

- **The Docker base had no `git`.** Five `ui` scenarios spawn it: two worktree scenarios and three merge-conflict
  scenarios. `GUEST_PACKAGES` now holds `git`.
- **A warm daemon stayed on a container of the old image.** A warm iteration now asks
  `guest_declaration_current` before it reuses a daemon. On Docker, a changed image tag, share or display
  recreates the container and starts the daemon cold. The `git` fix reached a warm worker only through this check.

Neither backend needs an installation on a Mac. Tart comes from Bazel (ADR 0158), and so do the Docker CLI and Lima
(ADR 0189). Tart does not run on Linux. So the old default of a Linux host named a backend that cannot exist there. A
Windows host already took Docker (ADR 0186).

## Decision

**The default worker is a Docker container, on a macOS host, on a Linux host and on a Windows host.**

1. **The default selection is one constant on every host.** `Selection::DEFAULT` in `crates/avl-base/src/config.rs`
   is the Docker backend with the Linux guest, and its rustdoc gives the reason for each host. It replaces the
   per-host `Selection::default_on`. On a macOS host the engine is the Lima engine of ADR 0189, unless `DOCKER_BIN`
   or `DOCKER_HOST` names another engine. On a Linux or a Windows host it is the engine that `DOCKER_HOST` names, or
   the host's own. The `--backend` spellings do not change: `--backend linux` still selects the Tart Linux worker.
2. **The Docker pool has two slots.** `AIR_VM_MAX_WORKERS` defaults to 2 on the Docker pool, as on the Tart pools.
   The slots are `air-docker-1` and `air-docker-2`, and the two containers share one engine.
3. **The Lima engine has 10 GiB of memory by default.** `AIR_VM_MEMORY_MB` defaults to `LIMA_ENGINE_MEMORY_MIB`,
   10240, on the Lima engine. Two lanes share one VM, and ADR 0183 measured about 2.5 GiB for one lane. The 6144 of a
   Linux guest was for one lane in one VM. It stays the default of a Tart Linux worker and of a Linux guest on an
   external engine. Tart macOS keeps 32768.
4. **An unqualified `pool stop` stops the Docker pool.** A command without `--backend` and without a receipt now
   operates on the Docker pool.

## Consequences

- **The Tart Linux pool stays available.** `--backend linux` selects it, and it keeps its workers and its base
  image. Use it for a comparison, or when the engine is not available.
- **`shard` and `flake` run on two containers of one engine VM.** They share the engine's CPUs and memory. On the
  Tart pool, each slot was a VM of its own. A recreate of the engine exempts every lease that the process holds
  (`HeldLeases`), because the sibling bodies of one `shard` or `flake` hold one lease each. A lease of another
  process still refuses it with `worker_leased`, and `pool recycle all` notes it and goes on with the running engine.
- **A two-slot `shard` of the `ui` lane passed on the default pool.** On 2026-09-30,
  `vm.cmd shard --lane ui --shards 2 --exact`, with no `--backend`, ran three times:
  - The first run refused with `shard_missing_report`. The engine recreate took the lease of the sibling shard for
    a lease of another holder. That fault gave the rule above.
  - The second run refused with `engine_start_failed` at the 900 s budget of a first start. The guest boot spent
    15 minutes in `pam_systemd: Failed to create session: Connection timed out` before cloud-init reached
    `apt-get`. A failed start writes no engine record, so the next run made the engine again. One boot in five was
    this slow on that day. That is an open observation, not a rule.
  - The third run passed, with exit 0 and status `passed`. The worker image was built, because the tag with `git`
    was not on the registry. Both containers were made and started at once.

  The third run, one iteration:

  | step | time |
  |---|---|
  | the engine recreate, from `limactl start` to `READY` | 33 s |
  | the worker image build | 95 s |
  | both daemons up, after both containers started | 16 s |
  | shard 1, 21 classes, tests | 429.0 s |
  | shard 2, 19 classes, tests | 397.2 s |
  | the predicted makespan | 385.3 s |
  | the serial time of the measured baseline | 770.1 s |
  | the whole `shard`, from the start to the release, with the recreate and the build | 639 s |

  Two IDEs and two daemons ran in the 10 GiB engine. Their memory was not measured.
- **A template change makes the one engine again, with every container in it.** Before, a change of one Tart
  worker touched that worker only. The lease check of ADR 0189 refuses a recreate while another holder leases a
  worker of the pool.
- **The 10 GiB is a starting point.** The daemon's memory in the engine is shared, and it is not measured for two
  lanes at once. A measurement of two concurrent lanes can move the default.
- **A Linux host without a Docker engine now refuses by default.** Before, it named Tart, which cannot run there.
  Now it refuses `docker_missing` with the remedy to install an engine.
- **A caller that relied on the Linux default must name it.** A script that expects `air-linux-N` from an
  unqualified command must add `--backend linux`.

## Alternatives rejected

- **Keep the Tart Linux worker as the default.** The Docker lanes are green twice, at the same order of time on the
  same host. Both backends need no installation. A Tart default on a Linux host also names a backend that cannot
  run there.
- **Docker as the default on a Linux and a Windows host only.** A macOS host would then keep a different default
  from the others. The evidence above is from a macOS host, so it does not argue for the split.
