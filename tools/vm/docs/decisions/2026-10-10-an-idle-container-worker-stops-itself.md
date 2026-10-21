---
topic: testing
---

# An idle Apple container worker stops itself

Date: 2026-10-10

## Status

Accepted. It amends [ADR 0106](0106-a-warm-daemon-survives-a-lease-release.md) on the Apple `container` engine only:
there the warm daemon survives a release until the idle deadline, and not until `pool stop`.

Amended 2026-10-21 ([IJAI-4425](https://youtrack.jetbrains.com/issue/IJAI-4425)): the detached process runs in the
directory of its worker, `<runtime root>/workers/<key>`, and not in the directory of the release. The release ran in a
checkout or a lease slot, and the process held that directory for the whole grace. `bd lease return` then refused the
slot twice on 2026-10-21 with `slot_busy`. `Runner::spawn_session` now takes the directory, and no caller can leave it
out.

Amended the same day: a release also stops each other worker whose record passed its deadline more than one
`IDLE_STOP_POLL` ago. A live process decides within one poll after its deadline, so such a record has no process. A
kill, a crash or a logout ends the process, and on 2026-10-21 a killed process left `air-docker-1` running. The release
decides through `Manager::idle_stop_worker`, so the lifecycle lock, the nonce and the lease check apply. It only tries
the lock of the other worker, and a busy worker keeps running. A failure is a note, and the release still succeeds.

## Context

A lease release keeps the container and its warm daemon (ADR 0106), so the next run of the same lane pays no daemon
start. The worker runs until a `pool stop`.

On Apple `container`, the default engine of a macOS 26 Mac (ADR 0224), each worker is a VM of 8192 MiB that returns no
memory to the host until it stops ([Apple container is a second engine of the Docker backend](2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md), item 7). [The container pool follows the host memory, and the builder stops](2026-10-10-the-container-pool-follows-the-host-memory-and-the-builder-stops.md) lets a large host run four or more such workers. A
developer who ran a lane in the morning held 8 GiB or more of the host for the rest of the day.

The controller is a command line. No resident process can count an idle period after the release.

The user asked on 2026-10-10 for an idle worker on Apple `container` that stops itself after a grace period. The user
chose a grace of one hour on the same day.

## Decision

1. **`AIR_VM_IDLE_STOP` sets the grace, in seconds, or `off`.** The default is 3600 s (`IDLE_STOP_DEFAULT`) on Apple
   `container`. Every other engine and backend reads `off`: a container stop on the Lima engine frees nothing, an
   external engine belongs to the operator, and a Tart worker suspends with `pool stop`. `0` stops the worker inside
   the release. A malformed value is `invalid_environment` on every pool.
2. **A release writes a record and starts a detached process.** After the lease goes, `Manager::schedule_idle_stop`
   writes `<runtime root>/workers/<worker>/idle-stop.json` with `schemaVersion`, `worker`, `releasedAt`, `deadline`
   and a `nonce`. It then starts `<controller> --backend docker pool idle-stop <worker> --nonce <nonce>` in a session of
   its own, through `Runner::spawn_session`, with its output in `idle-stop.log` beside the record. That is the start of
   the detached trace viewer too. The reply of the release gains `idleStopAt`, the deadline.
3. **`pool idle-stop` is a hidden verb.** It reads the record every 30 s (`IDLE_STOP_POLL`), and it exits at once when
   the record is gone or has another nonce. At the deadline it decides under the lifecycle lock of the worker
   (`Manager::idle_stop_worker`):
   - a record that is gone or has another nonce means that a later operation took the slot, so it exits;
   - a lease means that a holder took the worker, so it exits;
   - otherwise it stops the container through `stop_without_lifecycle_lock`, and the record goes.

   A lifecycle lock that another operation holds makes it ask again after the same pause. The engine is reached and
   never started (`Docker::reach_engine`), so a server that is down stops nothing.
4. **A lease acquisition and a start remove the record.** `place_lease` removes it under the lifecycle lock, and so
   does `start_docker`. So a worker that a holder takes before the deadline keeps running, and the waiting process
   finds no record.
5. **The next run starts the worker again.** `require_docker_ready` starts a stopped container, as before. The data
   volume keeps the staged runtime, so the next run pays the start and a cold daemon, and no staging.
6. **`status` names the deadline.** A worker with a record has ` idle_stop=<deadline>` on its line and `idleStopAt` in
   its row.
7. **The fixtures turn it off.** The host testkit sets `AIR_VM_IDLE_STOP=off` on every Apple `container` pool, so no
   suite starts a detached process. The worker fixture records the start request instead, and a suite of the idle stop
   sets the grace.

## Consequences

- **An idle worker gives its memory back after an hour.** The worker after a lane run in the morning holds no memory in
  the afternoon. Amended 2026-10-21: that is true only while the detached process lives. A worker whose process died
  stops at the next release of another worker of the pool, or at `pool stop`.
- **A run after the deadline pays a cold daemon.** The container start takes about 1 s, and the daemon start with a
  staged runtime took 11.7 s on 2026-10-09 ([Apple container is a second engine of the Docker backend](2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md)). A developer who runs a lane less often than once an hour pays it
  each time. `AIR_VM_IDLE_STOP=off` keeps the old behaviour.
- **Each release starts one small process.** A later release or acquisition makes the earlier process exit within 30 s.
- **The process outlives the controller and the terminal.** It inherits the environment of the controller without the
  bridge credentials, as the viewer does, so it resolves the same pool. It does not inherit the directory of the
  controller (amended 2026-10-21).
- **A `vm.cmd` rebuild does not change a waiting process.** The process runs the binary of the release that started
  it.

## Alternatives rejected

- **A resident pool daemon on the host.** It is a new long-lived process with its own state, start and update. The
  detached process lives only for the grace.
- **A `launchd` timer.** It needs a plist per host and works on macOS only, and the controller starts no installation.
- **Stop the worker at every release.** A run of the same lane a minute later then pays a cold daemon each time.
- **An idle stop on every engine.** On the Lima engine the engine VM keeps its memory, so a container stop frees
  nothing.
- **A sweep in `pool status` or in each command of the controller** (2026-10-21). A release is the one operation that
  ends with an idle worker, and it runs at the end of each lane run. A sweep in every command adds a scan of the pool
  to the commands that read only.
- **A process that the release restarts for a stranded record** (2026-10-21). It adds a second process for a deadline
  that already passed. A stop inside the release is shorter.
