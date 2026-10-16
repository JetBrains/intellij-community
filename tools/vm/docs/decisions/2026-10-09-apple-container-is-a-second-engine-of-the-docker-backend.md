---
topic: testing
---

# Apple container is a second engine of the Docker backend

Date: 2026-10-09

## Status

Accepted. It amends [ADR 0189](0189-the-docker-engine-is-a-lima-vm-the-controller-owns.md) for the engine of a Docker
pool on a macOS host. It does not move the default: the Lima engine stays the default until a later ADR, after the
lanes are green on Apple `container` on at least two hosts. The number skips the sequence of this directory, because
the docs here also cite the ADRs of `plugins/air/docs/decisions`, and 0211 to 0221 are taken there. How the lane uses
the engine is [the VM guide](../vm-ui-tests.md).

Amended on 2026-10-09: [ADR 0224](0224-apple-container-is-the-default-engine-of-a-mac.md) makes Apple `container` the
default on macOS 26 or newer, on Apple silicon. `AIR_VM_DOCKER_ENGINE` of item 1 becomes an override of that rule.

Superseded in part on 2026-10-13: [the Lima engine is retired](2026-10-13-the-lima-engine-is-retired.md) removes
`AIR_VM_DOCKER_ENGINE` of item 1.

## Context

The Lima engine of ADR 0189 costs a lot for one engine: a 632-line module, a VM template, three Bazel pins (the
Docker CLI, buildx and Lima), two mirrored Ubuntu cloud images, a `docker.io` that `apt-get` installs at the first
boot without a pin, one 16 GiB VM that all containers share, the whole home mounted read-only, and the 104-byte limit
of the socket path.

Apple `container` runs each Linux container in a lightweight VM of its own, on Apple's Virtualization framework. Its
CLI has the shape of the Docker CLI. Version 1.5.0 is from 2026-09-29. The release is a signed and notarized `.pkg`
whose payload is `bin/` and `libexec/`. The tool finds its plugins and its API server from the real path of the CLI:
the install root is the grandparent of `bin/container`.

A spike measured the engine on 2026-10-09, before any code. The host was macOS 27.0.1 on Apple silicon, 18 CPUs and
128 GiB, with the CLI 1.5.0 from the expanded package. Nothing was installed, and `/usr/local` did not change.

| measurement | value |
|---|---|
| first `system start`, with the kernel download | 108.8 s and 98.3 s |
| the kernel download | `kata-static-3.32.0-arm64.tar.zst` from GitHub, 696.6 MB, for a 30.4 MB kernel |
| `system start` with the kernel present, the path after a reboot | 0.20 s |
| `system start` against a running server | 0.04 s |
| `builder start -c 4 -m 4g`, cold, which pulls the builder image and `vminit` | 38.5 s |
| the first build of the worker image | 103.5 s, of which the guest packages took 82.0 s |
| a cached rebuild with `--progress plain` | 4.1 s |
| the worker image | 265 MB |
| `create`, `start`, and the first answering `exec` | 0.43 s, 0.93 s, 1.00 s |
| a restart to a ready display | 2.57 s |
| `stop -t 10` | 0.10 s |
| an idle worker with X up, host footprint | about 0.75 GiB |
| two workers at a 6 GiB peak each | about 13.6 GiB, held until the container stops |
| disk per worker, by `du` | 2.1 GiB, an upper bound because of APFS clones |

The virtiofs probe of ADR 0189 ran in a container: the host replaced a file by rename, and the guest read it in a
loop of about 10 ms. A path that the guest read just before the rename kept a dead node: `open` gave `ENOENT` and
`stat` gave the old size. The dead node expired after 34 ms to 58 ms in six trials, for a path read in a loop and for
a subdirectory made after the mount. A path last read 1.2 s before the rename was fresh at once. Lima took 0.73 s to
1.02 s. So the 2 s settle of the share refresh is enough on this engine too.

The spike also found these facts, and the decision follows them:

- `CONTAINER_APP_ROOT` does not move the data root of `system start`. Only `--app-root` does.
- The launchd label and the Mach service are fixed: `com.apple.container.apiserver`. So one login session has one
  server. A second `system start` from another install exits 0 and attaches to the first server. Its `system stop`
  stops every container of that server, also the containers of another tool such as the `testing-ui` skill.
- `inspect` answers JSON only. The state is `[0].status.state`: `running`, `stopped`, `stopping` or `unknown`. A
  created container and an exited container are both `stopped`. The exit code is only in the boot log, as the
  `vminitd` line `status: <n> managed process exit`. `logs` holds the current run only: a `start` clears it.
- The DNS proxy of the vmnet gateway refused queries on this host, whose resolvers are `127.0.2.2` and `127.0.2.3`
  beside a VPN tunnel. So the builder and the worker had no DNS. `--dns 1.1.1.1` fixed both.
- A `build` without `-c`, `-m` and `--dns` makes the builder VM again with 2 CPUs, 2 GiB and no DNS. `builder start`
  alone does not keep them.
- `build -q` hung twice, once for 600 s. The same build with `--progress plain` took 4.1 s.
- The hostname of a container is its name, and the CLI has no `--hostname`.
- The memory of a VM goes back to the host only when the container stops.

## Decision

**On a macOS host, `AIR_VM_DOCKER_ENGINE=container` runs the Docker backend on Apple `container`, through its CLI.**

1. **A transition variable chooses the engine.** `AIR_VM_DOCKER_ENGINE` takes `lima`, the default, or `container`.
   It applies only on a macOS host where neither `DOCKER_BIN` nor `DOCKER_HOST` is set. Otherwise the engine stays
   the external one, as ADR 0189 says. `DockerEngine::decide` in `crates/avl-base/src/config.rs` is the rule, and
   `DockerEngine::AppleContainer` is the new engine. Any other value is refused `invalid_environment`.
2. **The CLI is pinned in Bazel.** `container.MODULE.bazel` declares `@air_container_darwin_arm64`, an
   `http_archive` of the payload of the 1.5.0 package, re-packed as `container-1.5.0-darwin-arm64.tar.gz` on the Space
   mirror `intellij-build-dependencies`. Its sha256 is
   `f7459d9f9fd5604259713d44da75f172828103ae3ec377ea2f1e78ccba84ef63`. Apple builds no x86_64 version, so there is one
   repository. The alias `air_container_darwin_arm64` in `BUILD.bazel` is `manual`, and no `data` or `deps` names
   it, for the reason of ADR 0158. `CONTAINER_BIN` names an installed CLI instead, as `DOCKER_BIN` does. The
   controller runs the real path of either, because the install root follows from it. A CLI that cannot be resolved
   is `container_missing`, exit 69.
3. **The server is the one of the login session, on the default data root.** The controller never passes
   `--app-root` and never runs `system stop`. A server that is down, after a reboot, is started with `system start
   --enable-kernel-install` under the pool-wide image lock, with its log in `<runtime root>/container-system.log` and a
   budget of 15 minutes, as the first Lima start has. A failed start is `engine_start_failed`, exit 69.
4. **Every gate checks the server.** It reads `system status --format json` and compares `paths.installRoot` with
   the install root of its own CLI. A server of another install root whose `server.version` has another major version
   than the CLI is refused `container_engine_foreign`, exit 69. A server of another install root with the same major
   version is used, with one note: the XPC interface is compatible within a major version. A host that is not Apple
   silicon is refused `unsupported_backend_operation`.
5. **One lifecycle, two dialects.** The lifecycle of the Docker backend stays. The module of the Docker backend
   renders each command in the dialect of the engine (`Dialect` in `crates/avl-vm/src/worker/docker.rs`):

   | Docker dialect | Apple dialect |
   |---|---|
   | `inspect --type container --format …` | `inspect <name>`, parsed as JSON |
   | `create --hostname …` | `create` without `--hostname`, with `-m`, `-c` and `--dns` |
   | `stop --time 10`, `rm`, `rm --force` | `stop -t 10`, `delete`, `delete --force` |
   | `volume rm --force` | `volume inspect`, then `volume delete` when the volume exists |
   | `logs --tail 40` | `logs -n 40`, and `logs --boot -n 40` |
   | `build --tag` | `build --progress plain -c <cpus> -m 4g --dns <ip> -t` |
   | `pull`, `tag`, `image rm` | the download of the image archive, `image load`, `image delete` (item 11) |
   | `image inspect --format {{index .Config.Labels …}}` | `image inspect`, the label of `[0].variants[].config.config.Labels` |
   | `buildx build --platform … --push` | `image save --platform linux/<arch>`, then the upload of the archive (item 11) |
   | `version --format …` and the CLI plugins | `system status --format json`, no plugins |

   The engine itself is `crates/avl-vm/src/worker/container.rs`: the status, the start, the check, the builder and
   the nameserver. The exec channel is the same: `exec [-i] <name> <argv…>`, without a tty, as root.
6. **Each build and each container gets a nameserver.** `AIR_VM_DNS` names one IP address. Its default is the first
   nameserver of `scutil --dns` that is not a loopback address, else `1.1.1.1`. The value goes into the create
   argument list, so a change of the host resolver makes the container again.
7. **The memory is per worker.** On this engine `AIR_VM_MEMORY_MB` is the memory of one worker, and the default is
   8192 MiB: the lane peaked at 6.3 GiB (ADR 0200). `AIR_VM_CPU` is the CPU count of one worker. The create passes both,
   and every build passes the CPU count and 4 GiB to the builder.
8. **A stopped container in the boot poll is an exited one.** `ContainerState::Stopped` is the Apple word for a
   created or an exited container, and the Docker dialect never gives it. When the boot poll reads it, the start
   refuses `container_exited` and quotes `logs -n 40` and `logs --boot -n 40`, read before any restart.
9. **The create record stays.** `CreateRecord.argv` is the record on both engines, and `container_is_current` does not
   change. The container id of this engine is `[0].id` of `inspect`, which is the name. An engine switch changes the
   argument list, so the switch makes the container again.
10. **The pool commands own the builder, never the server.** `pool recycle all` runs `builder stop` and `builder
    delete` under the image lock, and accepts a builder that does not exist. `pool stop` stops the containers only.
    `status` prints `engine=container` and the `engineStatus` `running` or `stopped`, and it never starts the server.
11. **The image comes from the file mirror, not from the registry.** The registry pull of this engine fails on every
    blob. Space answers `406 Not Acceptable` instead of `401` to an anonymous blob request whose `Accept` names a layer
    media type, and the client of apple/containerization starts each blob request anonymously and asks for a token
    only after a `401`. The text of the two upstream issues is with the user who filed them. So this engine pulls an
    OCI archive from the Space file mirror `AIR_VM_IMAGE_MIRROR`, whose default is
    `https://packages.jetbrains.team/files/p/ij/intellij-build-dependencies`, and `off` turns the pull off.
    - The archive is `<mirror>/<AIR_VM_DOCKER_IMAGE>/<tag digest>-linux-<arch>.tar`, for example
      `air-ui-worker/3d61c9831be6-linux-arm64.tar`.
    - The host `curl` (`AIR_VM_HOST_CURL`) downloads it into `<runtime root>/docker-context/<digest>/` with `--fail`,
      and the pull log keeps what `curl` says. `image load -i` loads it under the tag, and the archive is removed.
    - The revision label check is the one of every pull. A download that fails is a note and a build, as a failed
      registry pull is: a 404 for a tag that nobody published yet is the ordinary case. A load that fails, an archive
      without the tag, and a label that is not the digest are a note and a build too.
    - `AIR_VM_DOCKER_PUSH` saves the built tag with `image save --platform linux/<arch>` and uploads the archive to
      the same URL with an HTTP PUT. The bearer token comes from `AIR_VM_IMAGE_MIRROR_TOKEN`, through a header file
      of mode 0600 that is removed after the upload, so the token is never in an argv. A push without the token is
      refused `image_mirror_token_missing` before the build. A host publishes only its own platform on this engine,
      and the registry publish of the Docker dialect builds both.
    - The image record `docker-image.json` and `status` keep their shape: a mirror pull is `pulled`.

## Consequences

- A Mac runs the Linux worker with one pinned CLI and no Docker CLI, buildx, Lima or cloud image. Each worker is a VM
  of its own, so two workers do not share one memory budget, and a worker that stops gives its memory back.
- The kernel download at the first server start is about 700 MB from GitHub, and the builder image and `vminit` come
  from `ghcr.io`. The engine keeps them in its data root, so the cost is once per host.
- The default data root is shared with any other tool of the session. The container names of the controller are the
  slot names, and the `testing-ui` skill names its containers `ui-*`, so they do not meet.
- A worker returns no memory while it runs. A worker that the controller does not need must stop to free the memory.
- `1.1.1.1` resolves public names only. A guest that needs a name that only a VPN resolver knows needs `AIR_VM_DNS`.
- The suite drives the engine over a fake `container` (`avl-testkit`), which prints the JSON shapes the spike
  recorded, and the file mirror over a fake `curl`.
- A cold host gets the worker image as an archive of about 265 MB instead of a build of about 2 minutes, when the
  mirror holds the archive of the tag. A tag that changes needs one publish from a Mac before other hosts skip the
  build.

The live lane on this engine, on 2026-10-09, `run AgentSessionToolWindowComposerUiTest`:

| step | cold run | warm run |
|---|---|---|
| the host build | 14.7 s, and 7.1 s for the guest agent | 11.1 s |
| the pull of the published image | failed after 2.2 s (see item 11) | none |
| the image build, with the first start of the builder VM | 121 s | none |
| `create`, then `start` | 1.0 s | none |
| the agent install and `validate-guest` | 7.0 s | none |
| the daemon start, with a cold runtime stage | 11.7 s | none, the daemon was up |
| the iteration | 355.4 s, 11 of 11 passed | 244.6 s, 11 of 11 passed |
| the whole command | 587 s | 300 s |

The server ran already at the cold run. After a `system stop`, `status` said `engine_status=stopped` and started
nothing, and `pool start air-docker-1` started the server and the kept container in 8 s. On 2026-10-08 the same class
took 267.5 s of iteration in the default runtime root of the same host, 11 of 11 passed. The first attempt of the cold
run went to OrbStack, because the shell exported `DOCKER_HOST`. That is the rule of item 1, so the guide says to unset
it.

## Follow-ups

- The Docker dialect still pulls from the registry. A later change may move it to the file mirror too, so that both
  engines have one image source.
- Done on 2026-10-10: the builder VM stops after each build, `builder stop` after the build and its publish
  ([the Apple container pool follows the host memory, and the builder stops after a build](2026-10-10-the-container-pool-follows-the-host-memory-and-the-builder-stops.md)). It held 1.47 GiB of its 4
  GiB after a build on 2026-10-09.

- Mirror the kernel (`[kernel] url` and `binaryPath` of `config.toml`) and the `vminit` and builder images on
  JetBrains hosts. The first start then downloads 30 MB instead of 700 MB.
- Check the macOS version before the first start. Today a host older than macOS 26 fails in `system start`, and the
  refusal names the log.
- Make Apple `container` the default and retire the Lima engine, its template, its pins and the buildx pin, in a
  separate ADR, after green lanes on two hosts.

## Alternatives rejected

- **A Rust library instead of the CLI.** None is worth linking. `apple-container` 0.1.0 is an XPC client inside a
  devcontainer CLI with 16 stars: tonic and prost, `protoc` at build time, 29 % documented.
  `containerization-framework` needs Xcode 26 in the build and the virtualization entitlement on the `vm` binary, and a
  container dies with the process that booted it. `objc2-virtualization` means a container runtime of our own.
  `bollard` has nothing to talk to, because Apple exposes no Docker socket. The CLI is the public interface, stable
  within a major version, and the controller already models an engine as an argument list and a parser.
- **A Docker API shim (`socktainer`).** It adds a third-party daemon between the controller and the engine, and the
  backend already speaks a CLI.
- **A private data root.** One login session has one server, so `--app-root` of a second start attaches to the first
  server anyway. The default data root lets the controller and the `testing-ui` skill share the server.
- **`CONTAINER_APP_ROOT`.** `system start` 1.5.0 ignores it.
- **Apple `container` as the default now.** One host was measured. The Lima engine stays the default until the lanes
  are green on two hosts.
