---
topic: testing
---

# The Apple container pool follows the host memory, and the builder stops after a build

Date: 2026-10-10

## Status

Accepted. It amends item 7 of [Apple container is a second engine of the Docker backend](2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md), which
gave each worker its own memory and left the pool size at two. It closes the builder follow-up of [Apple container is a second engine of the Docker backend](2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md) and of
[ADR 0224](0224-apple-container-is-the-default-engine-of-a-mac.md). The number skips 0225, because the docs here also
cite the ADRs of `plugins/air/docs/decisions`, and 0225 is taken there. [an idle Apple container worker stops itself](2026-10-10-an-idle-container-worker-stops-itself.md)
records the idle stop of a worker, which the user asked for on the same day.

## Context

The pool had two slots on every host. `worker_slots` in `crates/avl-base/src/config.rs` took `AIR_VM_MAX_WORKERS`,
default 2, cap 16, and nothing read the host memory.

On Apple `container`, the default engine of a macOS 26 Mac (ADR 0224), each worker is a VM of 8192 MiB
(`CONTAINER_WORKER_MEMORY_MIB`). So a host of 128 GiB ran two workers, as a host of 16 GiB did. A `shard` on the large
host got two workers and left most of the memory unused.

The builder VM of the engine kept running after each build. It holds 4 GiB (`BUILDER_MEMORY`), with 1.47 GiB in use on
2026-10-09, and a running VM returns no memory to the host. A cold `builder start` that pulls the builder image took
38.5 s in the spike of [Apple container is a second engine of the Docker backend](2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md).

The user asked on 2026-10-10 for a pool of about 4 slots on 128 GiB, 2 slots on a small host, and a builder that stops
after each build.

## Decision

1. **The default pool size on Apple `container` follows the host memory.** The rule is
   `clamp(host_mib / (4 * worker_mib), 2, 16)`, with `worker_mib` the resolved `AIR_VM_MEMORY_MB`. The workers get at
   most a quarter of the host. `default_docker_slots` is the rule, a pure function beside `DockerEngine::decide`.

   | host memory | worker memory | slots |
   |---|---|---|
   | 32 GiB | 8 GiB | 2 |
   | 64 GiB | 8 GiB | 2 |
   | 128 GiB | 8 GiB | 4 |
   | 128 GiB | 16 GiB | 2 |
   | 256 GiB | 8 GiB | 8 |
   | 1 TiB | 8 GiB | 16 |
   | unknown | any | 2 |

2. **The settings win.** `AIR_VM_MAX_WORKERS` sets the count, and `AIR_VM_WORKERS` names the slots, on every engine.
3. **Every other pool keeps two slots.** A Lima slot shares one engine VM of 16 GiB, which a larger pool would have to
   resize. An external engine has no cap of its own. A Tart macOS worker is 32 GiB, so the rule gives the floor anyway.
4. **The probe lives in `avl-host-sys`.** `avl_host_sys::host::memory_mib` answers `hw.memsize` through
   `sysctlbyname` on macOS, `MemTotal` of `/proc/meminfo` on Linux, and `GlobalMemoryStatusEx` on Windows. A probe that
   fails answers `None`. The workspace enables the `Win32_System_SystemInformation` feature of `windows-sys` for it.
5. **The caller hands the facts in.** `Config::load` and `Config::load_on` take a `HostFacts { macos, memory_mib }`
   value. The controller passes `MacosHost::read()` and the probe. `air-trace` loads the settings for the runtime root
   only, so it passes no memory (`HostFacts::without_memory`). The engine and `AIR_VM_MEMORY_MB` resolve before the
   slots.
6. **The fixtures pin the memory.** The host testkit loads every fixture with 64 GiB (`FIXTURE_HOST_MEMORY_MIB`), so
   every pool keeps two slots, and no suite depends on the memory of its host.
7. **`status` names the size and its reason.** The pool line of a Docker pool reads, for example,
   `pool=4 (host 128 GiB, 8 GiB per worker)` or `pool=2 (AIR_VM_MAX_WORKERS)`. The JSON has `poolSize` and `poolRule`.
8. **The builder stops after each build.** `Docker::ensure_image` runs `builder stop` after a build and its publish on
   the Apple dialect (`AppleContainer::stop_builder`). A stop that fails is a note, because the image is ready. The
   delete of `pool recycle all` calls the same stop first. The Docker dialect has no builder VM and runs nothing.

## Consequences

- **A host of 128 GiB runs four workers.** A `shard` or a second session finds more free slots, and four workers hold
  at most 32 GiB.
- **A stopped builder keeps its image.** The next build starts it again without the pull, so the cost is the start
  of the VM and not the 38.5 s of a cold builder.
- **`avl-base` stays portable.** It still links no `libc` and holds no `unsafe` (ADR 0224).
- **The settings of a test name the memory.** A test that loads without the testkit passes `HostFacts` itself.

## Alternatives rejected

- **Probe the memory in `avl-base`.** The crate is portable and has no `libc` and no `unsafe`.
- **Run `sysctl -n hw.memsize` at the load.** The load starts no process, and every command loads the settings.
- **Size the Lima engine with the pool.** The engine VM is created once with its memory, and a resize stops every
  container of the pool. The Lima engine is the engine of an older macOS only.
- **Give the workers half of the host.** The host runs the IDE, Bazel and the other sessions of the user too. The
  quarter is easy to move: it is `HOST_MEMORY_SHARE_DIVISOR`.
- **Delete the builder after each build.** The next build would pull the builder image and `vminit` again.
