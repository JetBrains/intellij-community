# An idle worker on Apple container stops itself after a grace period

## Context

A lease release keeps the container and its warm daemon, so the next run of the same lane pays no daemon start. The
decision [a warm daemon survives a lease release](../2026-10-10-a-warm-daemon-survives-a-lease-release/design.md)
records that rule. On the Apple `container` engine, each worker is a VM that returns no memory to the host until it
stops. A large host runs four or more such workers. So a developer who ran a lane in the morning held 8 GiB or more of
the host for the rest of the day.

The controller is a command line. No resident process counts an idle period after the release.

## Decisions

1. **`AIR_VM_IDLE_STOP` sets the grace, in seconds, or `off`.** The default is `IDLE_STOP_DEFAULT`, one hour, on Apple
   `container`. Every other engine and backend reads `off`. An external engine belongs to the operator, and a Tart
   worker suspends with `pool stop`. `0` stops the worker inside the release.
2. **A release writes a record and starts a detached process.** `Manager::schedule_idle_stop` writes
   `idle-stop.json` with the deadline and a nonce in the directory of the worker. It then starts
   `pool idle-stop <worker> --nonce <nonce>` in a session of its own, in the directory of the worker. The reply of the
   release names the deadline as `idleStopAt`.
3. **`pool idle-stop` is a hidden verb.** It reads the record every `IDLE_STOP_POLL`, 30 s. At the deadline it decides
   under the lifecycle lock of the worker, in `Manager::idle_stop_worker`:
   - a record that is gone or has another nonce means that a later operation took the slot, so it exits;
   - a lease means that a holder took the worker, so it exits;
   - otherwise it stops the container, and the record goes.

   The verb reaches the engine and never starts it, so an engine that is down stops nothing.
4. **A lease acquisition and a start remove the record.** So a worker that a holder takes before the deadline keeps
   running, and the waiting process finds no record.
5. **A release also stops the stranded workers.** A release stops each other worker whose record passed its deadline
   by more than one poll. A live process decides within one poll, so such a record has no process. The release only
   tries the lock of the other worker. A busy worker keeps running, and a failure is a note.
6. **The next run starts the worker again.** The data volume keeps the staged runtime, so the next run pays the start
   and a cold daemon, and no staging.
7. **`status` names the deadline.** A worker with a record shows `idle_stop=<deadline>` on its line.
8. **The fixtures turn it off.** The host testkit sets `AIR_VM_IDLE_STOP=off`, so no suite starts a detached process.
   A suite of the idle stop sets the grace.

The [worker lease and the warm daemon](../../../../spec/lane-controller.spec.md#the-worker-lease-and-the-warm-daemon)
state the contract.

## Alternatives rejected

- **A resident pool daemon on the host.** It is a new long-lived process with its own state, start and update.
- **A `launchd` timer.** It needs a plist per host and works on macOS only.
- **Stop the worker at every release.** The next run of the same lane then pays a cold daemon each time.
- **An idle stop on every engine.** An external engine belongs to the operator, and a Tart worker suspends with
  `pool stop`.
- **A sweep in `pool status` or in each command.** A release is the one operation that ends with an idle worker.
- **A process that the release restarts for a stranded record.** A stop inside the release is shorter.

## Consequences

- An idle worker gives its memory back after an hour. A worker whose process died stops at the next release of
  another worker of the pool, or at `pool stop`.
- A run after the deadline pays a container start and a cold daemon. `AIR_VM_IDLE_STOP=off` keeps the worker warm.
- Each release starts one small process. A later release or acquisition makes the earlier process exit within 30 s.
- The process outlives the controller and the terminal. It inherits the environment of the controller without the
  bridge credentials, so it resolves the same pool.
- The process holds no checkout or lease slot, because it runs in the directory of its worker.
- A `vm.cmd` rebuild does not change a waiting process. The process runs the binary of the release that started it.
