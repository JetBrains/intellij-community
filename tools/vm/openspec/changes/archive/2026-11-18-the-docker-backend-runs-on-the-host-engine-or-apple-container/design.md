# The Docker backend runs on the engine of the host or on Apple container, by the macOS version

## Context

The Docker backend runs the Linux worker in a container, so it needs an engine. A Linux engine on a Mac is a Linux
VM. Docker Desktop is not free for JetBrains at company scale. A VM that the controller owns costs a template, cloud
image pins, and one memory budget that all containers share.

Apple `container` runs each Linux container in a lightweight VM of its own, on the Virtualization framework of Apple.
Its CLI has the shape of the Docker CLI. Version 1.5.0 needs macOS 26, and Apple builds it for Apple silicon only.
The tool finds its plugins and its server from the real path of its CLI.

These facts of Apple `container` shape the decisions:

- The launchd label of the server is fixed, so one login session has one server. `system stop` stops every
  container of that server, also the containers of another tool.
- `system start` ignores `CONTAINER_APP_ROOT`. Only `--app-root` moves the data root.
- `inspect` answers JSON only. A created container and an exited container both have the state `stopped`.
- The DNS proxy of the vmnet gateway does not answer on every host.
- The memory of a VM goes back to the host only when its container stops.
- The registry pull of the engine fails on the Space registry, because Space answers an anonymous blob request with
  406 and not with 401.
- A server that runs from an install on an external volume can fail to create its vmnet network. A copy of the same
  install on the system volume runs.

The virtiofs dead node of Apple `container` expires within 60 ms. So the 2 s settle of the share refresh covers it.

The static Docker CLI for macOS holds the CLI only. It has no `buildx`, and the image build needs BuildKit for
`COPY --chmod`.

## Decisions

### 1. The engine rule

`DockerEngine::decide` in [config.rs](../../../../crates/avl-base/src/config.rs) is the rule, a pure function of the
host, its macOS release and two variables:

- When `DOCKER_BIN` or `DOCKER_HOST` names an engine, the backend runs that CLI against that engine, on every host.
  On a Mac an unset `DOCKER_BIN` is the pinned Docker CLI.
- When neither is set, a Mac of macOS 26 or newer on Apple silicon runs Apple `container`
  (`MacosHost::runs_apple_container`, `CONTAINER_MACOS_MAJOR`).
- Every other Mac keeps the engine of its host environment. The pinned Docker CLI then asks the default socket. When
  no engine answers, the command refuses `docker_missing`, and the remedy names the two ways out.
- A Linux or a Windows host keeps the engine of its host environment. An unset `DOCKER_BIN` there is `docker` on
  `PATH`.

No setting overrides the rule. A set `AIR_VM_DOCKER_ENGINE` or `AIR_VM_LIMA_HOME` is refused `invalid_environment`.
`status` prints `engine=host` or `engine=container` in its first line.

The rule is in [the daemon build and the launch identity](../../../../spec/lane-controller.spec.md#the-daemon-build-and-the-launch-identity).

### 2. The host facts come in at the load

`Config::load` reads `ProductVersion` from `/System/Library/CoreServices/SystemVersion.plist` once
(`MacosHost::read`). The load starts no process. The caller hands the facts in as `HostFacts { macos, memory_mib }`,
and `Config::load_on` takes them. So a test pins them as it pins the `HostOs`. A caller that sizes no pool passes
`HostFacts::without_memory`. The host testkit loads every fixture on macOS 26 on Apple silicon (`FIXTURE_MACOS`) with
64 GiB (`FIXTURE_HOST_MEMORY_MIB`). So a suite gets the same engine and the same pool on every host.

### 3. The tools are pinned in Bazel

[docker.MODULE.bazel](../../../../docker.MODULE.bazel) pins the Docker CLI and `docker-buildx` for macOS.
[container.MODULE.bazel](../../../../container.MODULE.bazel) pins the Apple `container` CLI, for arm64 only. The pins
follow the rule of [the Tart pin](../2026-09-24-the-lane-controller-gets-tart-from-bazel/design.md). No `BUILD`
file names the repositories in `data` or `deps`, and the controller asks Bazel with one `cquery` at the first use.

1. A pin that Bazel cannot resolve is `docker_missing` or `container_missing`, exit 69. The message names the label
   and `./bazel.cmd cquery <label>`.
2. `DOCKER_BIN` and `CONTAINER_BIN` name an installed CLI instead. The controller runs the CLI as it is.
3. The pinned `buildx` follows the pinned CLI on every engine. The controller passes
   `DOCKER_CONFIG=<runtime root>/docker-config` and merges `cliPluginsExtraDirs` into its `config.json`. Every other
   key stays. A CLI that `DOCKER_BIN` names keeps its own configuration.
4. The controller runs the real path of the Apple CLI, because the tool finds its install root from it. The install
   root is the grandparent of `bin/container`.

### 4. The pinned Apple container runs from the volume of the runtime root

[install.rs](../../../../crates/avl-vm/src/worker/container/install.rs) owns the copy:

1. The rule is the device. `on_one_volume` compares `st_dev` of the install root and of the runtime root. On another
   device the controller runs a copy of the install.
2. The copy is `<runtime root>/apple-container/<name of the install root>`, so each pin gets its own copy. It holds
   `bin`, `libexec` and the record `air-copy.json`. A command reuses a current copy after one `stat`.
3. The copy is made in a temporary directory beside its place. The CLI, the server and the vmnet plugin must pass
   `codesign --verify --strict`, else the command refuses `container_copy_failed`, exit 69. One rename publishes the
   copy, and a command that loses the race uses the copy of the winner.
4. A gate whose server runs from the copy of this pin removes the copies of other pins. It also removes the temporary
   directories older than an hour.
5. A CLI that `CONTAINER_BIN` names runs as it is, from wherever it is.

### 5. The server is the one of the login session

The controller never passes `--app-root` and never runs `system stop`. A server that is down starts with
`system start --enable-kernel-install` under the pool-wide image lock, with a budget of 15 minutes. Its log is
`<runtime root>/container-system.log`. A failed start is `engine_start_failed`, exit 69. A wait for the lock longer
than the budget is `engine_busy`.

Every gate reads `system status --format json` and compares the install root of the server with the install root of
its own CLI. A server of another install with another major version is refused `container_engine_foreign`, exit 69. A
server of another install with the same major version serves, with a note. The XPC interface is compatible within a
major version.

### 6. One lifecycle, two dialects

The lifecycle of the Docker backend stays one. `Dialect` in [docker.rs](../../../../crates/avl-vm/src/worker/docker.rs)
renders each command and parses each answer in the dialect of the engine.
[container.rs](../../../../crates/avl-vm/src/worker/container.rs) owns what the engine adds: the server, the builder
and the nameserver.

1. A `stopped` container in the boot poll is refused `container_exited`. The refusal quotes `logs -n 40` and
   `logs --boot -n 40`.
2. `CreateRecord.argv` is the create record on both engines, and `container_is_current` compares it. An engine switch
   changes the argument list, so the switch makes the container again.
3. `AIR_VM_DNS` gives each build and each container one nameserver. The default is the first nameserver of
   `scutil --dns` that is not a loopback address, else `1.1.1.1`. The nameserver is in the create record, so a new host
   resolver makes the container again.
4. `AIR_VM_MEMORY_MB` and `AIR_VM_CPU` size one worker. The memory default is 8192 MiB
   (`CONTAINER_WORKER_MEMORY_MIB`). Each build gives the builder the CPU count and 4 GiB (`BUILDER_MEMORY`).

### 7. The pool follows the host memory

On Apple `container` the default pool size is `clamp(host_mib / (4 * worker_mib), 2, 16)` (`default_docker_slots`,
`HOST_MEMORY_SHARE_DIVISOR`). So the workers get at most a quarter of the host. `AIR_VM_MAX_WORKERS` and
`AIR_VM_WORKERS` win on every engine. Every other pool keeps two slots, because an external engine has no cap of its
own.

`avl_host_sys::host::memory_mib` in [host.rs](../../../../crates/avl-host-sys/src/host.rs) probes the memory. A probe
that fails answers `None`, and the pool keeps two slots. `status` names the size and its rule, for example
`pool=4 (host 128 GiB, 8 GiB per worker)`, and its JSON has `poolSize` and `poolRule`.

The rule is in [the worker lease and the warm daemon](../../../../spec/lane-controller.spec.md#the-worker-lease-and-the-warm-daemon).

### 8. The pool commands own the builder, never the server

`Docker::ensure_image` stops the builder after each build and its publish (`AppleContainer::stop_builder`). A stop
that fails is a note, because the image is ready. `pool recycle all` stops and deletes the builder under the image
lock. `pool stop` stops the containers and never an engine. `status` never starts the server. An idle worker stops
by itself, as [the idle stop](../2026-10-10-an-idle-container-worker-stops-itself/design.md) says.

### 9. The image comes from the file mirror

On Apple `container` the image comes from the Space file mirror `AIR_VM_IMAGE_MIRROR`, and `off` turns the pull off.

1. The archive is `<mirror>/<AIR_VM_DOCKER_IMAGE>/<tag digest>-linux-<arch>.tar`.
2. The host `curl` (`AIR_VM_HOST_CURL`) downloads it into `<runtime root>/docker-context/<digest>/`. `image load`
   loads it under the tag, and the controller removes the archive.
3. The revision label check is the check of [the content tag](../2026-09-29-the-worker-image-is-pulled-by-its-content-tag/design.md).
   A failed download, a failed load or a wrong label is a note and a build.
4. `AIR_VM_DOCKER_PUSH` saves the tag for the host platform and uploads it with an HTTP PUT. The token
   `AIR_VM_IMAGE_MIRROR_TOKEN` goes through a private header file, never through an argv. A push without the token is
   refused `image_mirror_token_missing` before the build.

## Alternatives rejected

- **A Rust library instead of the CLI.** No library is worth linking, and the CLI is the public interface, stable
  within a major version.
- **A Docker API shim such as `socktainer`.** It puts a third-party daemon between the controller and the engine.
- **A private data root.** One login session has one server, so a second `--app-root` attaches to the first server.
- **`CONTAINER_APP_ROOT`.** `system start` ignores it.
- **Read `kern.osproductversion` with `sysctlbyname`.** The base crate is portable, with no `libc` and no `unsafe`.
- **Probe the memory in `avl-base`.** The crate is portable, with no `libc` and no `unsafe`.
- **Run `sw_vers` or `sysctl -n hw.memsize` at the load.** The load starts no process, and every command loads the
  settings.
- **Ask the version at the first start of the engine.** The memory default and the push rule need the engine at the
  load.
- **Give the workers half of the host.** The host also runs the IDE, Bazel and the other sessions of the user.
- **Delete the builder after each build.** The next build pulls the builder image and `vminit` again.
- **Copy the install by its mount flags.** No mount flag is proven to be the cause. A test can show the device.
- **A Bazel-fetched engine on Linux.** A static `dockerd` needs root, and a rootless one needs `uidmap` and a systemd
  user session. Both are installations.

## Consequences

- A Mac of macOS 26 or newer on Apple silicon installs nothing for `--backend docker`. The first server start
  downloads a kernel of about 700 MB from GitHub, once per host.
- Each worker is a VM of its own. Two workers share no memory budget, but a running worker returns no memory.
- The server and its data root are shared with the other tools of the session. The `testing-ui` skill names its
  containers `ui-*`, and the slot names of the controller differ.
- `1.1.1.1` resolves public names only. A guest that needs a name of a VPN resolver needs `AIR_VM_DNS`.
- An older Mac and an Intel Mac need an engine of their own. An OrbStack that links no default socket needs
  `DOCKER_HOST`.
- A publish with the pinned Docker CLI needs a `docker login` into `<runtime root>/docker-config`.
- A new image tag needs one publish from a Mac before other Macs skip the build. On Apple `container` a host publishes
  its own platform only.
- The first command of a pin on another volume copies about 420 MB. A runtime root on that volume gains nothing.
- The controller does not know a Lima VM of an earlier controller. Such a VM stays on disk until the operator deletes
  it with their own `limactl`.
- One `BUILD` label that names a pinned repository brings the download back to every IDE sync. The comments in the
  `.MODULE.bazel` files are the guard.
- The suite drives the engine over a fake `container` and the file mirror over a fake `curl`.
