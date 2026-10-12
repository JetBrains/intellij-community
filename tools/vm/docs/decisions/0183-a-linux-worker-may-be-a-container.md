---
topic: testing
---

# 183. A Linux worker may be a container

Date: 2026-09-29

## Status

Accepted. It adds a third backend beside Tart and Parallels. It does not move the default, which stays the Tart
Linux worker. It does not reverse the last rejected alternative of
[ADR 0182](0182-the-daemon-is-reached-through-the-exec-channel.md), and the Context below says why.
[IJPL-257026](https://youtrack.jetbrains.com/issue/IJPL-257026) holds the assessment. How the lane uses the container
is [the VM guide](../vm-ui-tests.md).

Amended 2026-09-29: the Docker base is Ubuntu 26.04 and the Linux Tart default is 6 GiB. Amended the same day: the
image installs no `feh`, `wmctrl` left the package list, and
[ADR 0184](0184-the-worker-image-is-pulled-by-its-content-tag.md) supersedes the last rejected alternative.

Amended 2026-09-30: on a macOS host with no engine named, the engine is a Lima VM the controller owns
([ADR 0189](0189-the-docker-engine-is-a-lima-vm-the-controller-owns.md)). Amended the same day: `GUEST_PACKAGES` holds
`git`. The Docker base has none, the Tart base ships it, and five `ui` scenarios failed on a Docker worker without it.

Superseded in part on 2026-09-30: [ADR 0190](0190-the-docker-engine-is-the-default-worker.md) makes the Docker container
the default worker, with two slots. It supersedes point 7 and the one-slot default of point 6.

Superseded in part on 2026-10-08: [ADR 0210](0210-the-docker-image-carries-node-and-the-tart-linux-worker-is-retired.md) retires the Tart Linux worker.
The Docker container is the one Linux worker.

## Context

The default UI-lane worker is a Tart VM with an Ubuntu guest. A fresh worker boots in 11 s, and then
`provision-guest` runs `apt-get` for the package list. The worker is provisioned after 55 s
([the Linux guest note](../vm-linux-guest.md)). A worker is a whole VM, so one VM holds one lane.

A Linux container gives the same guest from a different host layer:

- The packages come from an image that a Dockerfile describes. A fresh worker does not run `apt-get`.
- A container starts in seconds. Nothing boots.
- One Docker engine can later hold several containers, so several lanes can share one VM.

ADR 0182 rejected a Docker backend as a remedy for the Local Network check of macOS. That reason is gone, because
the relay reaches the daemon through the exec channel. The reasons above are different, and the relay works on
Docker too.

The host of the first measurement, on 2026-09-29:

| what | value |
|---|---|
| host | macOS 26 (Darwin 25.6), Apple Silicon |
| Docker CLI | 29.4.0, context `orbstack` |
| Docker Engine | 29.4.0, `linux/aarch64`, 18 CPUs, 15.66 GiB for the whole OrbStack VM |
| base image | `ubuntu:24.04`, digest `sha256:008173c23f95b170204355c12626cb5a965d779a7e1283b09e9cffbb1bf33ca3` |

A Tart guest keeps a dead node for a file that the host replaces after the mount. `stat` then answers the old size,
and `open` fails with `ENOENT`. The remount is the only cure there. A spike repeated that test in a container:

```bash
docker run -d -v "$REPO/out:$REPO/out:ro" ubuntu:24.04 sleep infinity
```

| probe | result |
|---|---|
| the mount in `/proc/mounts` | `mac <path> virtiofs ro,relatime` |
| `cat` after the host replaced the file by rename | the new bytes at once |
| `stat` after the rename | the old size at +0 s, the new size from +1 s on |
| `open` after the rename | never `ENOENT` |
| `printf ABC \| docker exec -i <c> cat` | `ABC`, with no tty |
| the user of `docker exec` | uid 0 |

So OrbStack's VirtioFS keeps no dead node. Only the attribute cache is late, and it is late for about one second.
`docker exec -i` carries a byte stream without a tty, which the relay and the file write need.

## Decision

**A Linux worker may be a Docker container. `--backend docker` selects it, and nothing selects it by default.**

1. **Docker is a third `Backend`, not a guest-OS value.** The controller has two axes: the hypervisor and the guest
   OS. A container changes the first axis only. The guest is the same Linux guest, with the same account, paths,
   display, packages and guest agent. So the guest profile stays `LINUX_PROFILE`, and the guest verbs need no
   change. A macOS guest on Docker is not possible, and `Config::load` refuses it.
2. **The image is a Dockerfile in the repository.** It is `plugins/air/tests/integration/vm-lane/docker/Dockerfile`,
   with the entrypoint `air-display` beside it. The controller builds the image. The base is `DOCKER_BASE_IMAGE` in
   `provision/versions.env` of the `vm-ui-tests` skill, pinned by digest. The package list is `GUEST_PACKAGES`,
   passed as a build argument, so the Tart worker and the image install one list. The tag is
   `air-ui-worker:<12 hex digits>`, a digest over the Dockerfile, `air-display`, the base image and the package list.
3. **The entrypoint owns the display.** `air-display` starts `Xvfb :88` and fluxbox as the worker account, and waits
   until both answer. The controller creates the container with `docker create --init`, so `docker stop` is one
   SIGTERM. `validate-guest` still proves the guest after each start, as on Tart.
4. **A remount on Docker is a 2 s settle.** The shares are bind mounts at `/mnt/AirVmShares/<share-name>`, read-only.
   There is no device to remount, and the spike found no dead node. The settle covers the late attribute cache.
5. **The controller recreates a container when its create arguments change.** The controller keeps a record of the
   `docker create` arguments and of the container id. The record covers the shares, the image tag, the display and
   `AIR_VM_SCREEN`. The controller removes the container and creates it again in three cases: the arguments change,
   the record is missing, or the engine's container id differs from the recorded id. `pool start` and `pool init`
   refuse with `worker_leased` when the stale container runs and another holder leases it. A stopped stale container
   is recreated for any caller. The guest-local state is the named volume `air-<worker>-data` at
   `/home/admin/WorkerData`, so a recreate keeps the staged runtime and the daemon restarts warm.
6. **The first slice has one container.** The Docker pool's slots are `air-docker-N`, and `AIR_VM_MAX_WORKERS`
   defaults to 1 on that pool. The container name is the worker name.
7. **The default stays Tart Linux.** The UI-lane verdict stays on a Tart Linux worker until the lane is green on
   Docker. A later ADR can move the default.
8. **Some operations have no Docker meaning.** `vnc`, `peekaboo` and `image validate|build` refuse with
   `unsupported_backend_operation`, exit 2. `pool stop` stops the container and keeps nothing warm, as on Tart
   Linux. `pool stop` never suspends. `pool recycle` removes the container and its volume. `pool gc` removes
   stopped, unleased containers and keeps their volumes.

## Consequences

- **A fresh Docker worker runs no `apt-get`.** The image build does, once for each tag. On 2026-09-29, on the 24.04
  base, `pool start air-docker-1` built the image in 3 s, because the layers were in the cache from the spike build. It
  then created and started the container, validated the display (`windowManagerProbes: 1`) and staged Node 24.19.0. All
  of it took 15 s after the host build. The time of a first build without a cache is not measured.
- **The first lane run on Docker is green.** On 2026-09-29, on the 24.04 base, `AgentSessionToolWindowComposerUiTest`
  passed 8 of 8 scenarios on `air-docker-1`. The tests took 234 s after a cold daemon start, and every trace has a
  video. The daemon's remount step reported the 2 s settle. A later run against the warm IDE (`ide reuse`) also passed 8
  of 8, in 286.6 s of tests. The host was loaded, so the guide gives these times as one iteration each. On the 26.04
  base, the same day, `pool recycle air-docker-1` built the image without a cache, made the container and staged Node in
  158 s. The same class then passed 8 of 8 in 230.2 s of tests after a cold daemon start.
- **The container shares the memory of the engine's VM.** On OrbStack that is 15.66 GiB for all containers. The
  controller passes no `--memory`, so the 6 GiB Tart default below does not cap a container. On 2026-09-29,
  `docker stats` on the warm worker `air-docker-1`, on the 24.04 base, showed 2.465 GiB in use in the middle of a lane
  run. The IDE JVM took 1.7 GiB RSS with `-Xmx2048m`, and the UI-test daemon JVM took 0.6 GiB RSS. Xvfb took 56 MiB,
  and fluxbox took 4 MiB.
- **The Linux Tart default is 6 GiB.** The earlier default of 16 GiB was half of the macOS default and had no
  measurement behind it. The 6 GiB default leaves about 3.5 GiB above the measured 2.465 GiB. The memory of JCEF and of
  an agent CLI is not measured. The host cost of a Tart VM at a given cap is not measured. The controller runs
  `tart set --memory` at each start, so an existing worker takes the new size at its next cold start. The daemon JVM
  sets no `-Xmx`, so its default maximum heap follows the cap. On 2026-09-29, `air-linux-1` at 6 GiB passed the same
  class 8 of 8 in 322.3 s of tests after a cold daemon start. Its `/proc/meminfo` after the run, with the warm IDE
  held, showed 5.76 GiB total, 3.3 GiB available and 2.1 GiB of anonymous pages.
- **Docker Desktop is not free for JetBrains.** The controller does not need it. The Docker CLI is Apache-2.0, and
  the controller talks to any engine that the `docker` binary on `DOCKER_BIN` reaches. On the measurement host that
  engine is OrbStack.
- **The engine is not pinned.** The base image and the package list are pinned. The Docker engine and its VirtioFS
  are the host's. A different engine can have a different attribute cache, and then the 2 s settle can be wrong.
- **Two digests pin two Ubuntu releases.** The Tart pin is a Tart OCI image of Ubuntu 24.04 on `ghcr.io/cirruslabs`. The
  Docker pin is the Docker Hub image of Ubuntu 26.04. On 2026-09-29, `ghcr.io/cirruslabs/ubuntu` published the tags
  `focal`, `20.04`, `latest`, `22.04`, `docker` and `24.04` only. So the Tart guest cannot follow the Docker guest to
  26.04, and the two Linux guests run different releases. In an arm64 container of the 26.04 base, every name of
  `GUEST_PACKAGES` resolved, and the account `ubuntu` held uid 1000.
- **The image differs from a Tart guest in four ways.** It is Ubuntu 26.04, and a Tart guest is 24.04. It installs no
  recommended package. It removes the base image's `ubuntu` account, which holds uid 1000. It has no systemd, so the
  entrypoint starts the display. Both guests carry the fluxbox overlay `background: unset`, so fluxbox never runs
  `fbsetbg`, and the image needs no wallpaper setter. The Tart guest still gets `feh` as a recommendation of fluxbox.
- **The guest agent starts the run supervisor without systemd.** A container has no systemd, so the guest agent
  looks for `/run/systemd/system`, as `sd_booted(3)` does. When it is absent, the agent starts the supervisor with
  the released, session-detached spawn of the macOS guest. `docker-init` adopts the supervisor when the `docker exec`
  ends. Before this change, the first lane run on Docker refused with `supervise_unit_refused`. The next run passed
  8 of 8.

## Alternatives rejected

- **A guest-OS value `docker`.** The guest does not change, so a new guest value would copy `LINUX_PROFILE` and
  every Linux branch that reads it.
- **A recreate for every remount.** The spike found fresh bytes, so a recreate would cost a daemon restart and win
  nothing.
- **Docker as the default now.** No lane had run on Docker when this ADR was drafted. A default that has no green lane
  is not a verdict.
- **An image that the controller does not build.** A registry image adds a publish step and a second pin. A
  Dockerfile in the repository is reviewed with the code, and its tag follows its inputs.
  [ADR 0184](0184-the-worker-image-is-pulled-by-its-content-tag.md) supersedes this rejection: the controller pulls
  the image by the same content tag before it builds, so the tag stays the only pin, and the push is opt-in.
