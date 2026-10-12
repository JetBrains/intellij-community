---
topic: testing
---

# 189. The Docker engine is a Lima VM the controller owns

Date: 2026-09-30

## Status

Accepted. It amends [ADR 0183](0183-a-linux-worker-may-be-a-container.md) for the engine of a Docker pool on a macOS
host. It did not move the default. [ADR 0190](0190-the-docker-engine-is-the-default-worker.md) makes the Docker
container the default worker, the same day. [ADR 0191](0191-the-engine-vm-leaves-the-usernet-path.md) amends this
ADR on 2026-10-01: the engine boots Ubuntu 26.04, and its SSH runs over vsock. How the lane uses the engine is
[the VM guide](../vm-ui-tests.md).

## Context

The controller gets Tart from Bazel ([ADR 0158](0158-the-lane-controller-gets-tart-from-bazel.md)), so a developer
installs nothing for the Tart pools. The Docker backend ran the `docker` that `DOCKER_BIN` names, found on `PATH`,
against an engine the developer installed. Without one, it refused `docker_missing` and named OrbStack and Docker
Desktop. Docker Desktop is not free for JetBrains at company scale.

The Docker CLI for macOS is a static tarball, and it holds the CLI only. The engine is a Linux daemon, so the engine
of a Mac is a Linux VM. Lima is a VM manager that ships as one release tarball per Mac architecture:

- Its `vz` driver uses Apple's Virtualization framework. It needs no QEMU, and it mounts a host directory as
  virtiofs.
- It downloads a cloud image itself and checks the image digest.
- It provisions the guest with cloud-init, and it forwards a guest Unix socket to a host socket.
- The release `limactl` is ad-hoc signed with the `com.apple.security.virtualization` entitlement. So `vz` runs from
  an extracted tarball.

The static macOS CLI ships no plugin, and it has no `buildx`. The image build needs BuildKit (`COPY --chmod`), so
the CLI alone cannot build the worker image.

The cache redirector answers 404 for `github.com/lima-vm` and for `download.docker.com`. So the archives go to the
Space file mirror `intellij-build-dependencies`, as the Tart archive does. Ubuntu prunes its dated cloud image
directories, so the two cloud images go there too.

A Tart guest keeps a dead node for a file that the host replaces after the mount, until a remount (the guide).
Lima `vz` also uses Apple's virtiofs. A container bind-mounts the engine VM's mount, so a remount would restart the
container, and that restarts the daemon. The 2 s settle of ADR 0183 is an OrbStack measurement. So a spike repeated
the ADR 0183 probes on a Lima engine before any code. The spike host, on 2026-09-30:

| what | value |
|---|---|
| host | macOS 26.7, Apple silicon |
| Lima | 2.2.0, `vmType: vz`, `mountType: virtiofs` |
| engine VM | Ubuntu 24.04, kernel 6.8.0-134 |
| Docker engine | `docker.io` 29.1.3 from the Ubuntu archive |
| Docker CLI | 29.8.1, which negotiates API 1.52 with the engine |
| container | `ubuntu:24.04`, digest `sha256:008173c2...`, the one of ADR 0183 |
| the mount in `/proc/mounts` | `lima-<id> <path> virtiofs ro,relatime` |

The host replaced a file by rename, as Bazel does. The guest then read the file in a loop of 5 ms steps:

| probe | result |
|---|---|
| `open` and `stat`, when the guest looked the path up in the second before the rename | `ENOENT`, and the old size |
| the first read, when the guest did not look the path up in that second | the new bytes |
| the first fresh read after a stale one, in six loops | at +0.73 s to +1.02 s |
| the same probes in the container | the same results as in the VM |
| a file that the guest held open during the rename | the same results |
| a subdirectory that the host made after the mount | the same results |
| `sync; echo 2 > /proc/sys/vm/drop_caches` in the VM | the stale read clears within 40 ms. `echo 3` also clears it |

So the Lima engine has a dead node, but the dead node expires by itself in about one second. OrbStack never gave
`ENOENT` (ADR 0183). Tart keeps the dead node until a remount.

## Decision

**On a macOS host, the Docker backend runs a Bazel-pinned Docker CLI against a Lima VM the controller owns, unless
the environment names an engine.**

1. **The environment names the engine, or the controller owns one.** When `DOCKER_BIN` or `DOCKER_HOST` is set, the
   backend runs against the engine that they name, as before. When neither is set, a macOS host uses the Lima
   engine. No flag selects the engine. `DockerEngine::decide` in `crates/avl-base/src/config.rs` is the rule. The
   CLI is a separate choice: `DOCKER_BIN` names it, and on a macOS host an unset `DOCKER_BIN` is the pinned CLI,
   on any engine.
2. **The tools are pinned in Bazel, as Tart is.** No `BUILD` file names the six repositories, for the reason of
   ADR 0158. The controller asks Bazel with one `cquery` for each at the first use. The root `MODULE.bazel`
   includes both files.

   | pin | file | repositories | on the Space mirror |
   |---|---|---|---|
   | Docker CLI 29.8.1 | `docker.MODULE.bazel` | `@air_docker_darwin_<arm64\|x86_64>` | `docker/29.8.1/` |
   | buildx 0.37.1 | `docker.MODULE.bazel` | `@air_docker_buildx_darwin_<arm64\|x86_64>` | `docker-buildx/v0.37.1/` |
   | Lima 2.2.0 | `lima.MODULE.bazel` | `@air_lima_darwin_<arm64\|x86_64>` | `lima/2.2.0/` |
   | Ubuntu 26.04 cloud images (ADR 0191) | `provision/versions.env` of the `vm-ui-tests` skill | none, Lima downloads them | `ubuntu-cloud-images/resolute-20260720/` |

   The Lima archives match the release `SHA256SUMS`. Docker publishes no checksum for its static tarballs, and the
   `checksums.txt` of buildx lists no darwin build. So those four pins carry the sha256 of the TLS download. The
   mirrored cloud images are byte-identical, so their digests did not change.

   A pinned CLI or plugin that Bazel cannot resolve is `docker_missing`, and a pinned `limactl` that Bazel cannot
   resolve is `lima_missing`, both exit 69. Each message names the label and `./bazel.cmd cquery <label>`.
3. **The pinned buildx follows the pinned CLI, not the engine.** With the pinned CLI (`DOCKER_BIN` unset), the
   controller passes `DOCKER_CONFIG=<runtime root>/docker-config` on every engine. It names the pinned
   `docker-buildx` in `cliPluginsExtraDirs` of the `config.json` there. It sets only that key and keeps every other
   key, so a `docker login` made there survives. A file that is not a JSON object is replaced, with a note. A CLI
   that `DOCKER_BIN` names keeps its own configuration. BuildKit runs in `dockerd`, so the engine VM installs no
   buildx.
4. **The engine is one VM per machine.** Its instance name is `air-docker-engine`. Its `LIMA_HOME` is
   `AIR_VM_LIMA_HOME`, and the default is `~/.local/state/JetBrains/air-vm-ui-tests/lima` on every host. The macOS
   runtime root is not the default, because it is under `~/Library/Application Support`. A Unix socket path must
   stay under 104 bytes, and the socket path there reaches that limit. Lima avoids that directory for the same
   reason. The first engine start refuses `lima_home_too_long` (exit 2) when the socket path is longer than 103
   bytes (`Config::require_short_lima_socket`). So `status` and `suites` still work on a Mac with a long home.
5. **The engine template is in the repository.** It is `plugins/air/tests/integration/vm-lane/docker/engine.lima.yaml`,
   beside the Dockerfile, and `avl-worker` embeds it. The controller replaces each `@@NAME@@` marker, and the
   template sets these things:
   - `vmType: vz` and `mountType: virtiofs`, with the CPUs, the memory and the disk from `AIR_VM_CPU`,
     `AIR_VM_MEMORY_MB` and `AIR_VM_ROOT_DISK_GB`, with the Linux defaults. ADR 0190 gives the memory its own
     default for the engine. On an external engine
     `AIR_VM_ROOT_DISK_GB` has no use.
   - The Ubuntu cloud image by digest, one entry per architecture. ADR 0191 moves it from 24.04 to 26.04. The pins are
     `LIMA_BASE_IMAGE_ARM64_URL`, `LIMA_BASE_IMAGE_ARM64_SHA256`, `LIMA_BASE_IMAGE_X86_64_URL` and
     `LIMA_BASE_IMAGE_X86_64_SHA256`.
   - The home directory, read-only, at its host path, as the default template of Lima mounts it. The home holds
     the repository and the Bazel output user root. So the two `--mount` binds of a container work as on any other
     engine. A repository or a Bazel output user root outside the home is refused `share_outside_home` (exit 2)
     when the engine starts.
   - A rootful `dockerd`: `docker.io` from the Ubuntu archive.
   - The Docker socket, forwarded to `<LIMA_HOME>/air-docker-engine/sock/docker.sock`. The controller passes it to
     every `docker` command as `DOCKER_HOST`.
6. **A template change makes the engine VM again.** The controller records the digest of the rendered template in
   `<runtime root>/lima-engine.json`. Only the home, the sizes and the image pins change the digest. So a switch
   between two checkouts keeps the engine. A start whose digest differs deletes the VM and starts it again. When
   a worker of the pool holds a lease of another process, the start refuses `worker_leased` instead, because the
   delete takes every container. A lease of this process does not block the recreate. That is the caller's own
   lease, and every lease that the invocation holds, such as the sibling shards of ADR 0190. This is the engine's
   copy of the container record of ADR 0183.
7. **One lock serializes the engine.** Every start, stop and delete of the engine runs under
   `<runtime root>/lima-engine.lock`. A caller that waits for the lock longer than the start budget gets
   `engine_busy` (exit 75). So two concurrent bodies of one `run` start the engine once.
8. **The pool commands own the engine's lifecycle.**
   - A Docker command that needs the engine starts it. `limactl start` gets `--timeout=<budget>s`, and one
     deadline covers the start and the wait for the host socket. The budget is `AIR_VM_BOOT_TIMEOUT`. When the VM
     is absent before the start, the budget is at least 900 s, because the first start downloads the image and runs
     `apt-get`.
   - A failed start is `engine_start_failed` (exit 69). It names `<runtime root>/lima-engine.log` and the budget
     that applied. An engine in a state other than `Running` or `Stopped`, for example `Broken`, is
     `engine_unusable` (exit 1). The remedy is `pool recycle all`.
   - `pool stop`, `pool gc`, `lease release` and the engine stop never start the engine and never make it again.
     A `lease release` on an engine that does not run refuses `worker_stopped` (exit 1), and names `pool start`.
   - `pool stop` on the Docker pool stops the containers. Then it stops the engine, when no worker is leased and no
     container runs. Its outcome names the engine's state in `engine`.
   - `pool recycle all` deletes the engine, unless a worker holds a lease of another process. Then it notes that
     lease and recycles the slots on the running engine. `pool recycle <worker>` keeps the engine.
   - `pool gc` keeps the engine. On a stopped engine it runs no `docker` command, and it keeps every slot as
     `engine-stopped`.
   - `status` never starts the engine. Its JSON has `engine` (`lima` or `host`) and `engineStatus`, and its text
     starts with `pool: engine=host` or `pool: engine=lima engine_status=<word>`. When the Lima engine does not run,
     the `state` and the `imageSource` of every row are `unknown`.
9. **A remount on the Lima engine stays the 2 s settle.** The spike's dead node expires within about one second,
   so the settle covers it. `drop_caches` clears it faster, but it adds a privileged command in the engine VM to
   every refresh and wins nothing inside the settle. So the refresh does not use it.
10. **A Linux or a Windows host keeps the external engine.** Lima needs QEMU on Linux and WSL2 on Windows. Both are
    installations, so a pin there removes nothing. On those hosts an unset `DOCKER_BIN` is `docker` on `PATH`.

## Consequences

- **A Mac installs nothing for `--backend docker`.** The first start downloads the CLI, buildx, Lima and the cloud
  image. Lima keeps the image in `~/Library/Caches/lima` on macOS, which every `LIMA_HOME` shares. So a VM that is made
  again does not download the image again. One iteration each, on 2026-09-30:

  | step | time |
  |---|---|
  | the first `limactl start`, with the image download (the spike) | 147.4 s |
  | of that, the image download | 103 s |
  | of that, the boot, `apt-get` and the probe | 44 s |
  | the first start by the controller, with the image in Lima's cache | 76 s |
  | a warm `limactl stop` (the spike) | 11.1 s |
  | a warm `limactl start` (the spike) | 19.8 s |

- **The first lane run on the Lima engine is green.** On 2026-09-30,
  `vm.cmd --backend docker run AgentSessionToolWindowComposerUiTest` passed 8 of 8 scenarios, with exit 0. It
  was one iteration, and a cargo run of a reviewer loaded the host. The run journal gave these times:

  | step | time |
  |---|---|
  | the whole `run`, from the start to the release | 459.6 s |
  | the host build | 40.3 s |
  | the first start of the engine, with the image in Lima's cache | 76 s |
  | the pull of the worker image `3f6c4d9395db` from the registry | 31.6 s |
  | the container create and start, `validate-guest` and the Node staging | 34 s |
  | the daemon staging and boot | 18.0 s |
  | the tests, 8 scenarios | 204.2 s |

  The daemon's remount step reported the 2 s settle for the virtiofs dead nodes and the attribute cache.
- **The buildx path is verified live.** A second run on 2026-09-30 tested the design end to end. The mirror URLs had
  moved the template digest. `AIR_VM_DOCKER_REGISTRY=off vm.cmd --backend docker pool start air-docker-1` made the
  engine again and built the worker image on it through the pinned buildx. The image record says `built`. The first
  line of the build log is `building with "default" instance using docker driver`, which is BuildKit's. Then
  `vm.cmd --backend docker run AgentSessionToolWindowComposerUiTest` passed 8 of 8 scenarios, with exit 0. One
  iteration each:

  | step | time |
  |---|---|
  | `pool start`: the engine recreate, the cloud image download from the Space mirror, the image build and the container start | 322 s |
  | of that, the engine start to `READY` | 189 s |
  | the host build of the run | 12.1 s |
  | the daemon start | 14.9 s |
  | the tests, 8 scenarios | 192.9 s |
- **The whole lane on the Lima engine takes the time of a Tart Linux worker.** On 2026-09-30, on one host,
  `run --lane ui` passed 71 of 71 twice on `air-docker-1`: once with a cold daemon after a recycle, then warm.
  `run --lane ui-real` passed 4 of 4 twice. In the next hour, the same lanes ran on the Tart Linux worker
  `air-linux-1`. The times are of the tests only, one iteration per cell:

  | lane | Tart Linux | Lima engine |
  |---|---|---|
  | `ui`, 40 classes, 71 tests, cold daemon | 750.4 s | 756.4 s |
  | `ui`, warm daemon | 860.1 s | 697.0 s |
  | `ui-real`, 2 classes, 4 tests | 84.6 s | 70.5 s, 70.4 s |
  | median ratio per class, Lima to Tart, over the classes above 2 s, cold | the base | 0.98 |
  | the same median ratio, warm | the base | 0.80 |

  So the two workers take times of the same order, and the measurement shows no penalty for the engine. One
  iteration per cell shows no spread, so this ADR does not call the Lima engine faster. ADR 0190 moves the default
  on this evidence.
- **The first two `ui` iterations on the Lima engine were red.** Each had 5 failures, all in the two worktree and
  the three merge-conflict scenarios. The cause was the missing `git`, because the Docker base has none. The fix
  adds `git` to `GUEST_PACKAGES`, which moves the image tag. It reached a warm worker only through the next
  consequence.
- **A warm iteration recreates a container of a changed declaration.** Before it reuses a daemon, a warm iteration
  asks the backend `guest_declaration_current`. On Docker that compares the image tag, the shares and the display
  of the create record with the current arguments. On a difference, the iteration recreates the container under
  its own lease, forgets the old daemon record and starts the daemon cold. Before this, a package-list change left
  a warm daemon on a container of the old image. A Tart or a Parallels worker answers true, because its readiness
  gate checks its shares.
- **The engine is pinned on a Mac, but not all of it.** The Lima version and the cloud image digest are pins. The
  `docker.io` package is the one the Ubuntu archive has at the first start. So two engine VMs made on different days
  can run different engine versions. This amends the consequence "The engine is not pinned" of ADR 0183.
- **A container shares the engine VM's memory.** On the Lima engine that is `AIR_VM_MEMORY_MB`, and not the
  15.66 GiB of the OrbStack VM of ADR 0183. The default was 6 GiB, and ADR 0190 makes it 10 GiB for two lanes.
  The container that ADR 0183 measured used 2.465 GiB.
- **A Mac that has OrbStack goes to the Lima engine now.** Before this change, the `docker` on `PATH` reached the
  OrbStack context. Now an unset `DOCKER_BIN` and `DOCKER_HOST` select the Lima engine. OrbStack stays the fast
  path: set `DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock`, or set `DOCKER_BIN` to OrbStack's `docker`.
- **The lease check before a recreate can miss a lease.** A recreate of the engine holds the lifecycle lock of at
  most one slot. So a lease that another slot takes after the check is not seen, and the delete takes its
  container. A recreate follows only a change of the home, the sizes or the image pins, so the window is rare.
  `pool stop` and `pool recycle all` hold the lock of every slot, and they have no such window.
- **`docker_missing` on a Mac has a new meaning.** With no engine named, it means that Bazel could not fetch the
  pinned CLI or the pinned buildx, or that the Lima engine did not answer.
- **The engine VM sees the whole home, read-only.** A container still sees the two shares only. A repository or a
  Bazel output user root outside the home needs an engine that `DOCKER_HOST` or `DOCKER_BIN` names.
- **A publish with the pinned CLI needs its own login.** The pinned CLI reads `<runtime root>/docker-config`, not
  `~/.docker/config.json`. So a publish needs `DOCKER_CONFIG=<runtime root>/docker-config docker login <registry>`
  first. The controller merges its key into that file, so the login survives the next command.
- **A publish needs an engine with emulation.** `AIR_VM_DOCKER_PUSH=1` builds a `linux/amd64` half. The `docker.io`
  of the Lima engine has no QEMU `binfmt` handler for it, so a publish from the Lima engine fails. Use an engine
  with emulation, for example OrbStack, for a publish.
- **The macOS x86_64 archives are pinned, but not tested.** The spike host is Apple silicon.
- **An existing engine is made again once after this change lands.** The moved `yq` entries and the mirror URLs
  each change the template digest. So the next start of an engine made before them deletes it and makes it again.
- **One `BUILD` label that names a new repository brings the sync download back.** No test catches that, as
  ADR 0158 says. The comments in `docker.MODULE.bazel` and `lima.MODULE.bazel` are the guard.

## Alternatives rejected

- **A `dockerd` in a Tart Linux VM.** It has the same Apple virtiofs. The controller would also have to write a
  relay for the Unix socket. Lima ships the socket forward and the cloud-init provisioning.
- **A separate provision step for the socket override.** A review asked whether the `yq` entry that sets
  `SocketUser` can run after the `apt-get` of the `system` script. That was checked. Lima runs the `yq` entries before
  the `system` scripts, in any list order, and the socket of the live run answered at the first start. So the
  template needs no second provision step. The `yq` entries now come first in the file, so the file reads in the
  order that Lima runs it.
- **A Podman machine.** Podman has no `buildx`, and its CLI is a second dialect beside Docker's.
- **The CLI pin alone.** The engine is the part a developer installs, so the CLI pin alone removes no installation.
- **The cloud image as a Bazel `http_file`.** The image is about 600 MB, and Bazel would copy it into the output
  base. Lima checks the image digest itself, as `tart clone` does for `LINUX_BASE_REFERENCE`.
- **A Bazel-fetched engine on Linux.** The static `dockerd` needs root. A rootless `dockerd` needs `uidmap` and a
  systemd user session. Both are installations.
