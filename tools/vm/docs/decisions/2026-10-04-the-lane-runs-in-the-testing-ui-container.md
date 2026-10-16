---
topic: testing
---

# The lane runs in the testing-ui container

Date: 2026-10-04

## Status

Accepted. It adds a fourth backend beside Tart, Parallels and Docker.
[ADR 0183](0183-a-linux-worker-may-be-a-container.md) added the third. For this backend it amends
[ADR 0182](0182-the-daemon-is-reached-through-the-exec-channel.md). The controller reaches the daemon through a port
that the skill's `start` publishes on the host loopback, not through a relay child. It does not move the default of
[ADR 0190](0190-the-docker-engine-is-the-default-worker.md);
[the container-linux backend becomes the default worker](2026-10-09-the-container-linux-backend-becomes-the-default-worker.md) decides that a later switch commit does.
[The VM guide](../vm-ui-tests.md) states how the lane uses the container, and the `vm-ui-tests` skill states how to
run it.

## Context

The controller has two halves. The lane half resolves a selector, builds on the host, stages the daemon, pushes the
hot jars and reads the verdict. The worker half gives the lane a Linux guest with an X display. It holds the three
backends, the pool and its leases, the shares, the guest provisioning, the worker image and the Lima engine.

The `testing-ui` skill (`.agents/skills/testing-ui`) runs a program's UI in one Linux container per checkout, with
its own X display, VNC and noVNC. Its guest server is a Go program, reached through one control port on `127.0.0.1`
behind a Bearer. The worker half duplicates most of that. It too owns a container runtime, an image with an X
display, read-only mounts, a guest channel and a live view. The skill's layer costs less:

| step | the lane, from [the VM guide](../vm-ui-tests.md) | the skill, from [its measurements](../../../../../.agents/skills/testing-ui/references/measurements.md) |
|---|---|---|
| the environment up | the first start of the Lima engine, 147 to 197 s | `start`, about 3 s for a recreate |
| one guest call | a relay spawn, 0.65 s | a runtime `exec`, 0.1 to 0.3 s; one verb on the control port, 0.03 s |

The lane's cost is in the daemon and in the tests, not in the guest layer. The skill already maintains that layer,
with the same display stack. The lane loses nothing when it reuses it.

## Decision

**A UI lane may run in the container of the `testing-ui` skill. `--backend container-linux` selects it.**

1. **The worker is the skill's container.** The controller is a Rust program. It starts the container through the
   skill's `container.cmd start`. The container has the skill's fixed sizing: 6 GiB of memory, 4 CPUs and 1 GiB of
   `/dev/shm`. The container stops after three idle hours, as every testing-ui container does, and the next run
   starts it again. The runtime is what the skill supports on the host: Apple `container` on macOS, rootless Podman
   on Linux. The container is one per checkout, so the pool has one slot, `container-linux-1`, and
   `AIR_VM_MAX_WORKERS` other than 1 refuses. A shard needs a second checkout.
2. **The guest runs unprivileged.** The worker account is the container user `ubuntu`, uid 1000, with no `sudo` and
   no `chown`. `$AIR_VM_DATA` is `/home/ubuntu/WorkerData`, on the container's disk, as on the other backends. The
   agent install streams the binary through `POST /v1/execute`, as the request body on the command's standard input.
   `test.xml`, the trace zips and the evidence leave the container through the runtime's `exec`, as on Docker. A
   pull runs the agent's `read-file` through `container.cmd exec`.
3. **The shares are the skill's read-only mounts at the host's own paths.** The checkout and the Bazel output root
   are mounted at the paths they have on the host, so a host path is a guest path and the guest needs no parity
   layout. The checkout is read-only, so IDE Starter keeps its output tree on the container disk, through
   `ide.starter.out.dir`.
4. **The control port is the channel, and the daemon port is published.** The controller reaches the guest server on
   the skill's control port on `127.0.0.1`, with the Bearer that the skill writes to
   `out/testing-ui/container.ctl_bearer`. A one-shot guest command runs through `POST /v1/execute`. `start --publish`
   maps the daemon's guest port to a host loopback port derived from the checkout (`AIR_VM_DAEMON_HOST_PORT`), and
   the controller connects to it with plain TCP. A published port forwards to the container's address, not to its
   loopback, so on this backend the daemon binds every interface of the container (`air.ui.daemon.bind`). The
   container's network is its own, and the token guards each request.
   `vm exec -- <cmd>`, the developer's terminal verb, still runs through `container.cmd exec`.
5. **The display is the skill's.** The display is `:1`, the skill's Xvnc, and `AIR_VM_DISPLAY` still overrides it.
   The skill's noVNC is the live view: `vm vnc` prints the URL from `out/testing-ui/container.json`. A Docker worker
   still refuses `vnc`. `image` and `peekaboo` refuse, because the guest is Linux.
6. **The skill is the checkout's, and one setting names the published port.** The script is
   `<repo>/.agents/skills/testing-ui/scripts/container.cmd` and its output root `<repo>/out/testing-ui`, of the checkout
   that holds the controller. `AIR_VM_DAEMON_HOST_PORT` defaults to a port in 12000..19999 derived from the checkout
   path. On this backend `AIR_VM_USER` defaults to `ubuntu` and `AIR_VM_DATA` to `/home/ubuntu/WorkerData`.
7. **Every host of the skill.** A Windows host runs the backend through `wslc`, the skill's Windows runtime: the
   controller runs the batch half of `container.cmd`, and the two shares are mounted read-only at their drive-form
   guest paths, `/d/ultimate/intellij` for `D:\ultimate\intellij`, as ADR 0186 maps them for Docker. The Docker,
   Tart and Parallels backends do not change.
8. **`status` reports the container.** The row shows the container state from `container.cmd list`, the lease, the
   parity verdict and the noVNC URL.

## Consequences

- **The daemon path changes for this backend only.** ADR 0182 reaches the daemon through a relay child of the exec
  channel, one spawn per pooled connection. Here the host reaches the daemon through the port `start` publishes on the
  host loopback. A pooled connection is one TCP connection and no child process. The daemon binds the container's
  interfaces here, not its loopback, and still checks its token, which guards the published port as it guards every
  other backend's.
- **The Docker decisions stay in force for the Docker backend.** Until the Docker removal below, these records
  describe that backend:
  - ADR 0183, the container backend;
  - [ADR 0184](0184-the-worker-image-is-pulled-by-its-content-tag.md), the image by its content tag;
  - [ADR 0189](0189-the-docker-engine-is-a-lima-vm-the-controller-owns.md), the Lima engine;
  - [ADR 0191](0191-the-engine-vm-leaves-the-usernet-path.md), SSH over vsock.
- **One worker on one machine.** The pool has one slot, so `shard` and `flake` run on one container here. A second
  checkout has a second container.
- **The lane runs on Windows.** On 2026-10-09 the `ui` lane of `flow-new-session` passed its 9 tests on a Windows 10
  PC with WSL 2.9.13, cold in 283 s and warm in 134 s, the first lane runs on a Windows PC for any backend. Five
  gaps of the shared Windows runfiles path had to go first; the Docker backend of
  [ADR 0186](0186-a-windows-host-runs-the-controller-natively.md) had never reached them.
- **The privileged guest profile stays for Tart, Parallels and Docker.** Their exec channels land as root, and the
  controller re-targets each command with `sudo`. This backend runs every command as the container user. The guest
  needs no `sudo` and no `chown`.
- **A pull uses the runtime's `exec`, as on Docker.** `test.xml`, the trace zips and the evidence come through the
  agent's `read-file` under `container.cmd exec`, and a pull can refuse `pull_digest_mismatch` here too. Only the
  daemon traffic and the one-shot guest commands avoid a runtime `exec`.
- **`pool stop` discards the staged runtime.** The data directory is on the container's disk, and `pool stop`
  removes the container. The next start stages the JBR and the jars again.

What this decision leaves out: the Docker removal. A later change removes the Docker backend, with the Lima engine,
the worker image and the pool slots behind it, and names the records it supersedes. Tart and Parallels stay: they
run the macOS guest, which no container can.

## Alternatives rejected

- **The data directory on the shared host directory `/work`.** Apple's virtiofs refuses the placeholder files
  that `tar` writes for a symlink when an archive
  is extracted there. The IDE's indices would also live on that filesystem. The guest writes only to its
  own disk, and every artifact comes through the controller's channel, as
  [ADR 0164](../../../../../plugins/air/docs/decisions/0164-a-vm-run-is-an-event-stream-with-a-home.md) decided for the traces.
