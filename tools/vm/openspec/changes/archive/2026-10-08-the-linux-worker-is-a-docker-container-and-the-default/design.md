# The Linux worker is a Docker container built from the repository, and it is the default worker

## Context

The controller has two axes: the backend that makes and reaches a worker, and the guest OS. A Linux container gives
the Linux guest from a different host layer. The packages come from an image that a Dockerfile describes, so a fresh
worker runs no `apt-get`. A container starts in seconds, and one engine holds several containers.

The Docker lanes are green, at the same order of time as a Linux VM on the same host. A Docker container needs no
installation on a Mac, and Tart does not run on a Linux host. So one Linux worker is enough, and it is the default on
every host.

The image builds from the repository. So the image can carry Node from the start, and the boot needs no Node staging.

## Decisions

1. **Docker is a backend, not a guest-OS value.** The guest is the Linux guest, with the same account, paths,
   display, packages and guest agent. So the guest profile stays `LINUX_PROFILE`, and the guest verbs need no change.
   `Config::load` refuses a macOS guest on Docker and a Linux guest on Tart.
2. **The default selection is one constant on every host.** `Selection::DEFAULT` is the Docker backend with the Linux
   guest, on a macOS, a Linux and a Windows host. An unqualified `pool stop` stops the Docker pool. The engine of the
   backend is the decision
   [the Docker backend runs on the host engine or Apple container](../2026-11-18-the-docker-backend-runs-on-the-host-engine-or-apple-container/design.md).
3. **`--backend` takes `tart`, `parallels`, `docker` and `container-linux`.** The worker prefixes are `air-docker`
   and `air-macos`. The controller has no Tart Linux worker.
4. **The Docker pool has two slots.** The slots are `air-docker-1` and `air-docker-2`, and the containers share one
   engine. `AIR_VM_MAX_WORKERS` or the host memory rule of the engine can size the pool.
5. **The image is a Dockerfile in the repository.** It is [the Dockerfile](../../../../docker/Dockerfile) with the
   entrypoint `air-display` beside it. The base is `DOCKER_BASE_IMAGE` of `provision/versions.env`, pinned by digest.
   `GUEST_PACKAGES` is the package list, and it holds `git`. The tag is `air-ui-worker:<12 hex digits>`, a digest over
   the inputs of the image. The decision
   [the worker image is pulled by its content tag](../2026-09-29-the-worker-image-is-pulled-by-its-content-tag/design.md)
   states how the controller gets the image.
6. **The image carries Node.** The Dockerfile downloads the pinned Node release from nodejs.org, checks its sha256 and
   extracts it into `/usr/local`. A unit test ties it to `NODE_MAJOR` of `provision/versions.env`. The Linux guest
   Node is `/usr/local/bin/node`. `AIR_VM_NODE` stays the override, and `NODE_BIN` stays for the live lane.
7. **A boot runs no Node verb.** The boot installs the guest agent, runs `validate-guest` and makes the parity layout.
8. **The entrypoint owns the display.** `air-display` starts Xvfb and fluxbox as the worker account and waits until
   both answer. The controller creates the container with `docker create --init`, so `docker stop` is one SIGTERM.
9. **The run supervisor has one launch on every guest.** The guest agent starts it as a released, session-detached
   spawn. A Docker worker has no systemd, and `docker-init` adopts the supervisor when the `docker exec` ends.
10. **A remount on Docker is a 2 s settle.** The shares are read-only bind mounts. The engine VirtioFS keeps no dead
    node, and only its attribute cache is late.
11. **The controller recreates a container when its create record changes.** The record holds the `docker create`
    arguments and the container id. A changed record, a missing record or another container id recreates the
    container. The named volume `air-<worker>-data` keeps the staged runtime, so the daemon restarts warm. A warm
    iteration asks `guest_declaration_current` before it reuses a daemon.
12. **Some operations have no Docker meaning.** `vnc`, `peekaboo` and `image validate|build` refuse with
    `unsupported_backend_operation`. `pool stop` stops the container and never suspends. `pool recycle` removes the
    container and its volume. `pool gc` removes stopped, unleased containers and keeps their volumes.

The [daemon build and the launch identity](../../../../spec/lane-controller.spec.md#the-daemon-build-and-the-launch-identity)
state the contract.

## Alternatives rejected

- **A guest-OS value `docker`.** It would copy the Linux profile and every Linux branch that reads it.
- **A recreate for every remount.** The bytes are fresh, so a recreate costs a daemon restart for nothing.
- **Docker as the default on a Linux and a Windows host only.** One constant on every host is simpler, and the
  evidence comes from a macOS host.
- **A Node check at boot.** The tag is a digest over the Dockerfile, so the tag already proves the pin.
- **Keep the Tart Linux worker for a comparison.** It needs a base pin, a provisioning verb, Node staging and pool
  code for no gain.
- **Keep the Node staging for the Docker worker.** The image can carry Node, so the staging only adds a boot step.

## Consequences

- A fresh Docker worker runs no `apt-get`. The image build does, once for each tag.
- A host without the registry image builds it once, with network access to Docker Hub and nodejs.org.
- `shard` and `flake` run on two containers of one engine, so the two lanes share the CPUs and the memory of that
  engine.
- A host whose engine does not answer refuses with `docker_missing`. No Linux worker exists without an engine.
- A script that names `--backend linux` or `air-linux-N` gets a usage refusal.
- The engine is not pinned. A different engine can have a different attribute cache, and then the 2 s settle can be
  wrong.
