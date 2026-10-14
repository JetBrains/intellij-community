---
topic: testing
---

# The Lima engine is retired

Date: 2026-10-13

## Status

Accepted. It supersedes [ADR 0189](0189-the-docker-engine-is-a-lima-vm-the-controller-owns.md), which made a Lima VM
the engine of a Docker pool on a macOS host. It supersedes [ADR 0191](0191-the-engine-vm-leaves-the-usernet-path.md),
which moved that VM to Ubuntu 26.04. It supersedes point 1 of
[ADR 0190](0190-the-docker-engine-is-the-default-worker.md), which ADR 0224 already amended, and the Lima memory
default of its point 3. It supersedes the Lima half of
[ADR 0224](0224-apple-container-is-the-default-engine-of-a-mac.md): the Lima engine of an older macOS and the
override `AIR_VM_DOCKER_ENGINE`. ADR 0224 said that a later record removes the engine, and this is that record. It
also supersedes item 1 of
[Apple container is a second engine of the Docker backend](2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md),
which added that variable. How the lane uses the engine is [the VM guide](../vm-ui-tests.md#the-docker-guest).

## Context

ADR 0224 made Apple `container` the default engine of a Mac on 2026-10-09. After that, the Lima engine served only a
Mac that does not run Apple `container`. That is a macOS older than 26, an Intel Mac, or a Mac whose version cannot
be read. It also served the opt-out `AIR_VM_DOCKER_ENGINE=lima`. For these hosts the engine cost:

- a module of about 630 lines, and its tests;
- a Bazel module with the two Lima pins, and an engine template;
- the four `LIMA_BASE_IMAGE_*` pins of the two Ubuntu cloud images;
- a fake `limactl` in the test kit;
- `HeldLeases`, a lease bookkeeping type that existed only for the recreate of the engine;
- engine hooks in `pool stop` and `pool recycle`.

ADR 0224 kept the engine as the engine of an older macOS and as the opt-out, and named a later record for its removal.
The user decided the removal on 2026-10-13.

The measurements of the old records stay as history. On 2026-09-30 the whole `ui` lane took times of the same order
on the Lima engine and on the Tart Linux worker
([ADR 0189](0189-the-docker-engine-is-a-lima-vm-the-controller-owns.md)).
On 2026-10-09 the virtiofs dead node of Apple `container` expired after 34 ms to 58 ms. On the Lima engine it took
0.73 s to 1.02 s
([Apple container is a second engine of the Docker backend](2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md)).

## Decision

**The controller has no Lima engine. The Docker backend runs on the engine of the host environment, or on Apple
`container` on a Mac.**

1. **The Docker backend has two engines.** The first is the engine of the host environment: the CLI that `DOCKER_BIN`
   names, the `DOCKER_HOST` address, or the host's own `docker`. The second is Apple `container`, on a Mac.
2. **The engine rule is two-way.** When `DOCKER_BIN` or `DOCKER_HOST` names an engine, the controller runs that CLI
   against that engine, on every host. When neither is set, a Mac of macOS 26 or newer on Apple silicon runs Apple
   `container`. Every other Mac keeps the engine of its host environment: an older macOS, an Intel Mac, and a Mac
   whose version cannot be read. On such a Mac, the pinned Docker CLI asks only `/var/run/docker.sock`. When no engine
   answers on that socket, a command that needs the engine refuses `docker_missing`. The remedy names two ways out.
   The operator names a Docker engine with `DOCKER_BIN` or `DOCKER_HOST`, or runs the lane on a Mac with macOS 26 or
   newer on Apple silicon. `suites` still works, and `status` refuses `docker_missing` with the same remedy. A Linux
   or a Windows host keeps the engine it has.
3. **The macOS version chooses the engine, and nothing overrides it.** `AIR_VM_DOCKER_ENGINE` is removed, and a set
   value is refused `invalid_environment`. So no setting reaches `container_macos_too_old` (exit 2) now. The refusal
   stays only for an Apple `container` command on a Mac older than 26. Its remedy names the same two ways out as
   point 2.
4. **`status` prints `engine=host` or `engine=container` in its first line.** There is no `engine=lima`.
5. **`pool stop` stops the containers and never an engine.** Its reply has no `engine` field and no `engine=` line.
   On Apple `container` an unleased worker still stops by itself after `AIR_VM_IDLE_STOP`. `pool recycle all` on
   Apple `container` still deletes the builder VM, and it deletes no engine.
6. **The refusals and the settings of the Lima engine go.** The refusals `lima_missing`, `engine_unusable`,
   `share_outside_home` and `lima_home_too_long` go. `engine_start_failed` stays for Apple `container` only:
   `system start` failed, and the log is `<runtime root>/container-system.log`. `engine_busy` stays for the server
   start too: another command held the Docker image lock `<runtime root>/docker-image.lock` for longer than the start
   budget. The setting `AIR_VM_LIMA_HOME` goes too, and a set value is refused `invalid_environment`, as for
   `AIR_VM_DOCKER_ENGINE` of point 3. `AIR_VM_MEMORY_MB` loses its Lima default of 16384. It keeps 32768 on macOS and
   8192 on Apple `container`. `AIR_VM_ROOT_DISK_GB` sizes no engine VM now. It sizes only the disk of a Tart macOS
   worker.
7. **The Lima pins go.** The Bazel module `lima.MODULE.bazel`, the engine template `docker/engine.lima.yaml` and the
   `LIMA_BASE_IMAGE_*` pins of `provision/versions.env` are gone. The Space file mirror still holds the Lima tarballs
   and the Ubuntu cloud images. Their deletion is a later cleanup, not this change.
8. **The controller does not know a Lima VM of an earlier controller.** The VM stays on disk. The pinned `limactl` is
   gone, so the operator stops and deletes the VM with a `limactl` of their own. Its `LIMA_HOME` is
   `~/.local/state/JetBrains/air-vm-ui-tests/lima`, or the directory that `AIR_VM_LIMA_HOME` named:

   ```bash
   LIMA_HOME=~/.local/state/JetBrains/air-vm-ui-tests/lima limactl delete --force air-docker-engine
   ```

   When the VM does not run, it is enough to remove that directory.

## Consequences

- **`status` names one of two engines.** Its first line says `engine=host` or `engine=container`. A script that
  matches `engine=lima` finds nothing now. On a Mac that does not run Apple `container`, `status` prints `engine=host`
  when an engine answers. It refuses `docker_missing` when none does.
- **The reply of `pool stop` has no `engine` field.** A script must not expect that field. A Lima VM that runs
  keeps running after `pool stop`.
- **A set `AIR_VM_DOCKER_ENGINE` or `AIR_VM_LIMA_HOME` is refused.** A command refuses `invalid_environment` until the
  shell unsets the variable. A shell that exported `AIR_VM_DOCKER_ENGINE=lima` for an older Mac must name an engine
  instead.
- **A Mac older than macOS 26, and an Intel Mac, need an engine of their own.** When no engine answers on
  `/var/run/docker.sock`, a command that needs the engine refuses `docker_missing`. `suites` still works, and `status`
  refuses `docker_missing` with the same remedy. An engine that answers on that socket serves. An OrbStack that does
  not link that socket needs `DOCKER_HOST`. The other way out is to run the lane on a Mac with macOS 26 or newer on
  Apple silicon.
- **A Lima VM of an earlier controller stays on disk until the operator deletes it.** A running one keeps its memory,
  16 GiB by default. Point 8 has the command.
- **The mirror keeps the Lima files for now.** The Lima tarballs and the two Ubuntu cloud images stay on the Space
  file mirror until the cleanup.
- **The measured rows of the Lima engine stay as history.** ADR 0189, ADR 0190, ADR 0191 and the cost table of the
  guide still describe the runs that they measured.
