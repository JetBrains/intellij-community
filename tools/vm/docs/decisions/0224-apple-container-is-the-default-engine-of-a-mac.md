---
topic: testing
---

# 224. Apple container is the default engine of a Mac

Date: 2026-10-09

## Status

Accepted. It amends [Apple container is a second engine of the Docker backend](2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md), which kept the
Lima engine as the default, and point 1 of [ADR 0190](0190-the-docker-engine-is-the-default-worker.md), which named
the Lima engine as the engine of a macOS host. The Lima engine of
[ADR 0189](0189-the-docker-engine-is-a-lima-vm-the-controller-owns.md) stays in the code, as the engine of an older
macOS and as the opt-out. A later ADR removes it. The number skips 0223, because the docs here also cite the ADRs of
`plugins/air/docs/decisions`, and 0223 is taken there. How the lane uses the engine is [the VM guide](../vm-ui-tests.md).
Superseded in part by [the Lima engine is retired](2026-10-13-the-lima-engine-is-retired.md), which removes the Lima
engine and `AIR_VM_DOCKER_ENGINE`.

## Context

[Apple container is a second engine of the Docker backend](2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md) added Apple `container` as a second engine of the Docker backend on a macOS host, behind
`AIR_VM_DOCKER_ENGINE=container`. It kept the Lima engine as the default until the lanes were green on Apple
`container` on two hosts.

The evidence on 2026-10-09 is from one host: macOS 27.0.1 on Apple silicon, 18 CPUs and 128 GiB, with the CLI 1.5.0.

- The spike of [Apple container is a second engine of the Docker backend](2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md) measured the engine before any code. A worker starts in about 1 s, a restart to a ready
  display takes 2.57 s, and an idle worker holds about 0.75 GiB. The virtiofs dead node expires in 34 ms to 58 ms,
  so the 2 s settle of the share refresh is enough.
- The lane of [Apple container is a second engine of the Docker backend](2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md), `run AgentSessionToolWindowComposerUiTest`, passed 11 of 11 cold in 355.4 s of iteration,
  and 11 of 11 warm in 244.6 s.
- The file mirror of [Apple container is a second engine of the Docker backend](2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md) gave a cold host the image in 45 s, with no build.

The whole lanes ran on Apple `container` on the same day:

| lane | result |
|---|---|
| `ui`, cold daemon | 81 of 82 passed |
| `ui`, warm daemon | 80 of 81 passed, plus one container failure |
| `ui-real` | 7 of 9 passed |

The failures are of the same timeout family that the Lima engine showed on the same day, 0 to 3 per run. So the
evidence shows no failure that is particular to the engine.

Apple `container` 1.5.0 needs macOS 26, and Apple builds it for Apple silicon only. Before this record, a host older
than macOS 26 with `AIR_VM_DOCKER_ENGINE=container` failed in `system start`, and an Intel Mac was refused
`unsupported_backend_operation`.

## Decision

**A macOS host of macOS 26 or newer, on Apple silicon, runs the Docker backend on Apple `container` by default.** The
user took this decision on 2026-10-09, on the evidence of one host.

1. **The macOS version chooses the engine.** `DockerEngine::decide` in `crates/avl-base/src/config.rs` is the rule:
   - When `DOCKER_BIN` or `DOCKER_HOST` is set, the engine is the external one, on every host. This does not change.
   - When neither is set, a macOS host of macOS 26 or newer on Apple silicon runs Apple `container`
     (`MacosHost::runs_apple_container`, `CONTAINER_MACOS_MAJOR`).
   - An older macOS, an Intel Mac, and a Mac whose version cannot be read run the Lima engine.
   - A Linux or a Windows host keeps the external engine.
2. **`AIR_VM_DOCKER_ENGINE` is an override.** `lima` or `container` chooses the engine instead of the version, either
   way. Unset or empty, the version chooses. Any other value stays `invalid_environment`. The variable still applies
   only when neither `DOCKER_BIN` nor `DOCKER_HOST` is set.
3. **The version comes from the host file, once per load.** `Config::load` reads `ProductVersion` from
   `/System/Library/CoreServices/SystemVersion.plist` (`MacosHost::read`), and takes the number before the first dot.
   The load starts no subprocess. `decide` stays a pure function of the host, the release, the two variables and the
   choice. `Config::load_on` takes the release, so a test pins it as it pins the `HostOs`.
4. **The fixtures choose the engine.** The host testkit loads every fixture on macOS 26 on Apple silicon
   (`FIXTURE_MACOS`). `with_lima_engine` sets `AIR_VM_DOCKER_ENGINE=lima`, and `with_container_engine` sets
   `container`. So a suite gets the same engine on every host and on every macOS release.
5. **`status` keeps its words.** It prints `engine=container`, `engine=lima` or `engine=host`, as before.

## Consequences

- **The next `run` on a macOS 26 host moves to Apple `container`.** The engine switch changes the create argument
  list, so the first `run` or `pool start` makes the worker containers again on Apple `container`. It downloads the
  worker image from the file mirror, and builds only when the mirror does not hold the tag.
- **The Lima VM stays on disk.** The controller does not touch the engine that it no longer runs. A running Lima VM
  keeps its memory, 16 GiB by default, until `AIR_VM_DOCKER_ENGINE=lima vm.cmd pool stop`.
  `AIR_VM_DOCKER_ENGINE=lima vm.cmd pool recycle all` deletes it, unless a worker holds a lease of another process.
  `limactl delete` under the `LIMA_HOME` of the controller (`AIR_VM_LIMA_HOME`) also deletes it.
- **The first `system start` downloads the kernel.** That is about 700 MB from GitHub, once per host. It installs
  nothing, and `/usr/local` does not change.
- **The memory is per worker.** `AIR_VM_MEMORY_MB` is the memory of one worker on Apple `container`, 8192 MiB by
  default, and a running worker returns no memory to the host. Two workers hold about 13.6 GiB at their peak.
- **An older macOS and an Intel Mac do not change.** They keep the Lima engine, its template and its pins.
- **A shell that exports `DOCKER_HOST` keeps the external engine.** That is the rule of ADR 0189. The guide tells a
  developer with OrbStack to unset it for a run on Apple `container`.
- **The move rests on one host.** [Apple container is a second engine of the Docker backend](2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md) asked for green lanes on two hosts. The Lima engine stays one variable away,
  so a second host that fails on Apple `container` can opt out at once.

## Follow-ups

- Remove the Lima engine in a later ADR: its module, its template, its pins, the two mirrored cloud images and the
  buildx pin. The removal also drops the fallback of an older macOS, so it waits until such a host is no longer
  supported.
- Run the lanes on a second host, on Apple `container`.
- Find the cause of the timeout family that fails 0 to 3 tests per run on both engines.
- Find the cause of the container failure of the warm `ui` run.
- Done on 2026-10-09: the gate of the engine refuses `AIR_VM_DOCKER_ENGINE=container` on a macOS older than 26 with
  `container_macos_too_old`, exit 2, before any `container` command runs. The gate refuses it, not the load, so a
  command that asks no engine still loads its settings.
- The follow-ups of [Apple container is a second engine of the Docker backend](2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md) stay open: the file mirror for the Docker dialect, and a JetBrains mirror of the kernel,
  `vminit` and the builder image.
- Done on 2026-10-10: the builder stops after each build
  ([the Apple container pool follows the host memory, and the builder stops after a build](2026-10-10-the-container-pool-follows-the-host-memory-and-the-builder-stops.md)).

## Alternatives rejected

- **Keep the Lima engine as the default until a second host is green.** The user chose to move on one host. The
  override keeps the Lima engine for a host that needs it.
- **Remove the Lima engine now.** A macOS older than 26 cannot run Apple `container`, and the Lima engine is its only
  engine that needs no installation.
- **Read `kern.osproductversion` with `sysctlbyname`.** The base crate is portable and has no `libc` and no `unsafe`.
  The version file gives the same value with a file read.
- **Run `sw_vers -productVersion` at the load.** The load starts no process, and every command loads the settings.
- **Ask the version at the first start of the engine.** The engine must be known at the load, because the memory
  default, the disk size and the push rule depend on it.
