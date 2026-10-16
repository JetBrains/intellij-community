---
topic: testing
---

# The container-linux backend becomes the default worker

Date: 2026-10-09

## Status

Accepted, and not yet in effect. The default of [ADR 0190](0190-the-docker-engine-is-the-default-worker.md), the
Docker engine, stands until the switch commit, which is its own review after the backend lands: the default
selection and its tests, the guide's backend table, and the `vm.cmd` examples of the Air skill, which run without a
backend flag. [the lane runs in the testing-ui container](2026-10-04-the-lane-runs-in-the-testing-ui-container.md) added the backend and left the default
alone; this record decides where the default goes. The switch commit supersedes ADR 0190's default and keeps the
rest of that record until the Docker backend goes.

## Context

[The lane runs in the testing-ui container](2026-10-04-the-lane-runs-in-the-testing-ui-container.md) made the container of the `testing-ui` skill a fourth backend, `--backend container-linux`, as the first step
toward replacing the Docker worker. The skill's layer costs less than the worker half of the controller: a container
start takes about 3 s against the 147 to 197 s of the first Lima engine start, and a verb on the control port takes
0.03 s against a relay spawn of 0.65 s.

On 2026-10-09 the `ui` lane of `flow-new-session`, 9 tests in 4 classes, passed on the three hosts the skill supports.
The times are of one run each, cold with a daemon start and warm with the IDE reused:

| host | cold | warm |
|---|---|---|
| a Mac with Apple `container` | 152 s | 84 s |
| Ubuntu 26.04 with 8 cores and rootless Podman 5.7 | 760 s, of which 497 s is a cold Bazel build | 223 s |
| a Windows 10 PC with WSL 2.9.13 and `wslc` | 283 s, of which 84 s is the daemon start | 134 s |

The Windows runs were the first lane runs on a Windows PC for any backend. They found five gaps in the shared
Windows runfiles path, each fixed in the controller or the guest agent; the Docker backend of
[ADR 0186](0186-a-windows-host-runs-the-controller-natively.md) had never reached them.

ADR 0190 made Docker the default for two reasons: on a Mac the controller installs its own engine, the Lima VM of
[ADR 0189](0189-the-docker-engine-is-a-lima-vm-the-controller-owns.md), so no user installs anything, and one
constant could name the default on every host. The second reason holds for container-linux since the Windows runs.
The first does not: the skill needs its runtime on the host, Apple `container` on macOS 26 with Apple silicon,
rootless Podman 4.3 or newer with the cpu controller delegated on Linux, and a WSL pre-release with `wslc` on
Windows. An Intel Mac or an older macOS has no Apple `container`.

The skill runs one container per checkout, so the pool has one slot. The Docker pool has two.

## Decision

**The default worker becomes the testing-ui container, on a macOS host, on a Linux host and on a Windows host.** The
switch commit makes these five points true:

1. **The default selection is one constant on every host.** `Selection::DEFAULT` names the container-linux backend
   with the Linux guest, and its rustdoc gives the reason. `--backend docker` keeps the Docker backend with its
   engine, its pool and its image until the Docker removal.
2. **A host without the skill's runtime refuses and names the way out.** The skill's `start` prints its install
   hint for the host, the controller passes it on as `container_linux_start_failed` with the start log, and the
   refusal names `--backend docker` for a host the skill does not support, such as an Intel Mac. There is no
   fallback to Docker: a silent fallback would hide which backend ran, and the receipts and the reports would
   describe a worker the user did not choose.
3. **The default pool has one slot.** `AIR_VM_MAX_WORKERS` is 1 on it, and `shard` and `flake` run their bodies
   one after another on the one container. A second checkout has a second container. A pool of several containers
   per checkout is a later change of the skill and of this record.
4. **An unqualified `pool stop` stops the container-linux pool.** A command without `--backend` and without a
   receipt operates on the default pool, as ADR 0190 decided for Docker.
5. **The container keeps the skill's fixed sizing.** 6 GiB of memory, 4 CPUs and 1 GiB of `/dev/shm`;
   `AIR_VM_MEMORY_MB` and `AIR_VM_CPU` do not apply to it, as [the lane runs in the testing-ui container](2026-10-04-the-lane-runs-in-the-testing-ui-container.md) states.

## Consequences

- **Until the switch commit, nothing changes for a user without a backend flag.** The Docker backend stays the
  default, and `--backend container-linux` selects the container.
- **With it, the Air skill's lane commands change their backend.** `vm.cmd run` without a flag runs in the
  testing-ui container. The first start on a host builds the skill's image, one to three minutes, and the host
  needs the runtime above.
- **The Docker backend is the fallback and leaves later.** The removal of the Docker worker, with ADRs 0183, 0184,
  0189, 0191 and 0210, is its own record. Until then `--backend docker` works as before.
- **`shard` and `flake` are serial on the default pool.** A user of those verbs who needs the parallel pool passes
  `--backend docker`.
- **Not decided here.** When the Docker backend is removed; how the skill runs several containers per checkout;
  and a fake `container.cmd` that runs on Windows, so that the container-linux suites run on the Windows CI, which
  the shell-script fake of today cannot.
