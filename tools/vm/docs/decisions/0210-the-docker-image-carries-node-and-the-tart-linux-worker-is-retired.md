---
topic: testing
---

# 210. The Docker image carries Node, and the Tart Linux worker is retired

Date: 2026-10-08

## Status

Accepted. It supersedes the Node staging of [ADR 0047](0047-agent-clis-are-provisioned-per-boot.md) and of
[ADR 0108](0108-the-guest-half-of-the-image-pipeline-is-go.md): the `stage-node` and `check-node` verbs, and the Node
check of `validate-guest`. It supersedes the Tart Linux half of [ADR 0183](0183-a-linux-worker-may-be-a-container.md).
After it, the Docker container is the one Linux worker. [ADR 0190](0190-the-docker-engine-is-the-default-worker.md)
made it the default. How the lane uses the worker is [the VM guide](../vm-ui-tests.md).

## Context

A Linux worker got its Node 24 at boot. The controller resolved a Node archive from Bazel. The guest agent extracted
it through the read-only share (`stage-node`), and a second verb proved it (`check-node`). That machinery existed
because the Tart Linux worker (`--backend linux`) booted a public Ubuntu image, and the controller could not change
that image. The Docker worker boots an image that the controller builds from a Dockerfile in the repository. So the
image can carry Node from the start.

Only one lane needs the guest Node:

| consumer | its Node |
|---|---|
| `ui-live`: the npm-installed registry ACP agents and the Claude CLI | the guest Node, through `AIR_VM_NODE` and `NODE_BIN` |
| Pi | its own Node in its runfiles |
| Codex | none, it is a native binary |
| the product's runtime manager | its own Node, which it installs |
| `ui` and `ui-real` | none |

ADR 0190 made Docker the default on every host. Since then, the Tart Linux worker served only a comparison, or a
host whose engine was not available. It kept its own base image pin, its own provisioning verb and its own pool code.

The user decided two points on 2026-10-08:

1. `--backend linux` goes. Docker is the only Linux worker. `--backend tart` with the macOS golden and `parallels`
   stay.
2. A boot runs no Node check. The image tag is a digest over the Dockerfile, so the pin in the Dockerfile is the
   proof.

## Decision

**The Docker image carries Node 24.19.0, and the controller has no Tart Linux worker.**

1. **The image installs Node from nodejs.org.** The Dockerfile maps `TARGETARCH` to the Node architecture: `amd64`
   to `x64` and `arm64` to `arm64`. It downloads `node-v24.19.0-linux-<arch>.tar.gz`, checks its pinned sha256, and
   extracts it into `/usr/local`. The checksums are those of the headless runtime-installation tests. The Node layer
   comes before `AIR_IMAGE_REVISION`, so a change of the tag alone keeps the layer in the build cache. A unit test
   asserts that the Dockerfile names the `NODE_MAJOR` of `provision/versions.env`, so the two cannot drift apart.
2. **`vm_node` of a Linux guest is `/usr/local/bin/node`.** `AIR_VM_NODE` stays as the override of an operator.
   `NODE_BIN` in the daemon environment stays, because `ui-live` reads it.
3. **A boot runs no Node verb.** `stage-node`, `check-node` and `provision-guest` are gone from the guest agent and
   from the wire. The boot of a Docker worker installs the guest agent, runs `validate-guest` and makes the parity
   layout. `validate-guest` still proves the display, the window manager and the `ldd` sweep. It has no Node step.
4. **`--backend` takes `tart`, `parallels` and `docker`.** `Config::load` refuses a pair of Tart and a Linux guest by
   name. The worker prefix `air-linux` goes, and the prefixes are `air-docker` and `air-macos`. The settings
   `AIR_VM_NODE_ROOT`, `AIR_VM_NODE_ARCHIVE` and `AIR_VM_LINUX_IMAGE` go. The `LINUX_BASE_*` pins leave
   `provision/versions.env`.
5. **The Linux guest model stays.** `GuestOs`, the Linux guest profile and the `air-lane-linux` Bazel config stay,
   because the Docker guest is a Linux guest. `NODE_MAJOR` stays for the Homebrew Node of the macOS golden. The
   runtime stager stays, because it stages the daemon tiers and the JBR.

## Consequences

- **A host without the registry image builds it once.** The new tag is not in the registry until somebody
  publishes it. Until then, the first start on each host builds the image, with network access to Docker Hub and
  nodejs.org. A publish with `AIR_VM_DOCKER_PUSH=1` is the choice of the user.
- **The run supervisor has one launch on every guest.** The guest agent starts it as a released, session-detached
  spawn, and the systemd launch of the retired worker is gone.
- **No Linux worker exists without a Docker engine.** A host whose engine does not answer refuses with
  `docker_missing`. Before, `--backend linux` was the fallback on a Mac.
- **A host that had Tart Linux workers removes them by hand.** No `pool` command reaches them now. Run
  `tart delete air-linux-1 air-linux-2`, and remove each `<runtime root>/workers/air-linux-N` directory. On macOS
  the default runtime root is `~/Library/Application Support/JetBrains/macos-vm-ui-tests`.
- **A script that names `--backend linux` or `air-linux-N` must change.** The controller refuses `--backend linux` as
  usage (exit 2).
- **Measured rows that name `air-linux-1` stay as history.** The comparisons of ADR 0183 and ADR 0190 still describe
  the runs that they measured.

## Alternatives rejected

- **A Node check at boot.** The pin is in the Dockerfile, and the tag is a digest over the Dockerfile. A check at boot
  would prove again what the tag already proves, and it would cost a verb and a guest call on each boot.
- **Keep the Tart Linux worker for a comparison.** It needs its own base pin, provisioning verb, Node staging and
  pool code. The two workers took times of the same order (ADR 0190), and Docker needs no installation on a Mac.
- **Keep the Node staging for the Docker worker.** The staging existed because the Tart base image could not change.
  The Docker image can, so the staging would only add a boot step.
