# A UI lane may run in the container of the testing-ui skill

## Context

The controller has two halves. The lane half resolves a selector, builds on the host, starts the daemon and reads the
verdict. The worker half gives the lane a Linux guest with an X display. It owns a container runtime, an image,
read-only mounts, a guest channel and a live view.

The `testing-ui` skill runs the UI of a program in one Linux container per checkout, with its own X display, VNC and
noVNC. Its guest server listens on one control port on `127.0.0.1` behind a Bearer. The skill maintains most of
what the worker half does, with the same display stack, and its environment starts in seconds. The cost of a lane is
in the daemon and the tests, not in the guest layer.

## Decisions

**`--backend container-linux` runs a UI lane in the container of the `testing-ui` skill.**

1. **The worker is the container of the skill.** The controller starts it through `container.cmd start` of the
   skill, with the sizing of the skill. The runtime is the one that the skill supports on the host. The container is
   one per checkout, so the pool has one slot, `container-linux-1`, and `AIR_VM_MAX_WORKERS` other than 1 refuses.
2. **The guest runs unprivileged** as the container user `ubuntu`, with no `sudo` and no `chown`. The data directory
   is `/home/ubuntu/WorkerData`, on the disk of the container. The agent install streams the binary through
   `POST /v1/execute` of the control port. A pull runs the `read-file` verb of the agent through
   `container.cmd exec`.
3. **The share is a read-only mount of the skill at its host path.** It is the Bazel output root only, because
   [the guest reads no checkout](../2026-10-10-the-guest-reads-no-checkout/design.md). A host path is the guest path,
   so the guest needs no path translation.
4. **The control port is the guest channel, and the daemon port is published.** The controller reaches the guest
   server on the control port with the Bearer that the skill writes to `out/testing-ui/container.ctl_bearer`.
   `start --publish` maps the daemon port to a host loopback port, and the controller connects with plain TCP. A
   published port forwards to the address of the container, not to its loopback. So on this backend the lane daemon
   binds every interface (`--bind 0.0.0.0`), and the token guards each request. This is the one exception to
   [the exec channel](../2026-11-08-the-controller-reaches-the-guest-through-the-exec-channel/design.md).
5. **The display is the Xvnc `:1` of the skill.** `vm vnc` prints the noVNC URL from
   `out/testing-ui/container.json`. `image` and `peekaboo` refuse, because the guest is Linux.
6. **The skill is the one of the checkout that holds the controller.** `AIR_VM_DAEMON_HOST_PORT` names the published
   port, and its default derives from the checkout path.
7. **Every host of the skill runs the backend.** A Windows host runs it through `wslc`, the Windows runtime of the
   skill.
8. **`status` reports the container:** its state from `container.cmd list`, the lease, the parity verdict and the
   noVNC URL.

The worker code is in [container_linux.rs](../../../../crates/avl-vm/src/worker/container_linux.rs). A decision to
make this backend the default waits in the working note
[The container-linux backend becomes the default worker](../../../../docs/the-container-linux-backend-becomes-the-default-worker-plan.md).

## Alternatives rejected

- **The data directory on the shared host directory `/work`.** Apple virtiofs refuses the placeholder files that
  `tar` writes for a symlink.

## Consequences

- A pooled daemon connection is one TCP connection and no child process.
- One worker serves one checkout, so `shard` and `flake` run on one container. A second checkout has a second
  container.
- The lane runs on a Windows PC.
- The privileged guest profile stays for Tart, Parallels and Docker. Their exec channels land as root.
- A pull uses the `exec` of the runtime, as on Docker, and can refuse `pull_digest_mismatch`.
- `pool stop` removes the container and its data directory. The next start fills the data directory again.
