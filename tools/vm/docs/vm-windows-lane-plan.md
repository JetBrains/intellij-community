# Windows as an Air UI lane host

Note of 2026-10-02: the crates `avl-lane`, `avl-worker` and `avl-daemon` that this plan names are now the modules
`lane`, `worker` and `daemon` of `avl-vm`. `avl-trace-serve` is the module `serve` of `air-trace`
([ADR 0074](decisions/0074-the-ui-lane-crates-follow-the-re-key-domains.md)). The plan keeps its old text.

Status on 2026-09-30: route 2, the native Windows controller, is implemented, and
[ADR 0186](decisions/0186-a-windows-host-runs-the-controller-natively.md) records it. It supersedes the paragraph of
ADR 0059 that made Windows an unsupported host. Route 1 is implemented on 2026-09-29, except its step 3. The first
Windows host, a Windows 11 arm64 VM in Parallels on Apple silicon, has no nested virtualization, so it cannot run
WSL2 ([Why route 2](#why-route-2)). No `vm.cmd run` has passed on a Windows PC with a Docker engine yet, so ADR 0186
stays Proposed. The
controller is `community/tools/vm`, the operator view is [the VM guide](vm-ui-tests.md),
and the Docker worker is [ADR 0183](decisions/0183-a-linux-worker-may-be-a-container.md) with
[ADR 0184](decisions/0184-the-worker-image-is-pulled-by-its-content-tag.md).

## Goal

A developer or a CI agent on an x86_64 or an arm64 Windows PC runs `vm.cmd --backend docker run <suite>` and gets
the same verdict, screenshots and videos a Mac gets. The worker is the published half of the image for the PC's
architecture: `linux/amd64` on an x86_64 PC, `linux/arm64` on an arm64 PC.

## Facts, checked on 2026-09-29

- **The image already runs on Windows.** `registry.jetbrains.team/p/ij/containers-public/air-ui-worker:<tag>` is an
  image index with a `linux/arm64` and a `linux/amd64` half (ADR 0184). Docker Desktop on an x86_64 PC runs the
  amd64 half natively in its WSL2 VM, and on an arm64 PC the arm64 half. The Dockerfile has no arm64 step, and every name of `GUEST_PACKAGES` is an
  Ubuntu 26.04 package on amd64 too. Nothing below is about the container.
- **An arm64 PC needs no code of its own.** WSL2 on an arm64 PC is a Linux arm64 host. `GuestArch::of` answers
  `Arm64` there, because only a Linux x86_64 host gets `X86_64`. `--config=air-lane-linux` selects
  `_LINUX_AARCH64_GUEST` in `ui_lane_ide.bzl`, the guest a Mac uses: `idea_air_lane_dist_linux`, the linux_aarch64
  JBR and `:air-trace-record-linux-arm64`. On that host the distribution is a native build and not a cross build.
  `agent_cli_runtime.bzl` moves the runtimes to `linux_arm64`, which is the host's platform there. `bt` has
  `:bt-windows-arm64` and `:clippy-windows-arm64`, and the recorder has `:clippy-windows-arm64`, for the Windows
  side of the PC.
- **The controller did not build on Windows.** This fact is from 2026-09-29, before route 2. Now every controller
  crate is portable ([ADR 0186](decisions/0186-a-windows-host-runs-the-controller-natively.md)). Of the seventeen
  crates, six were `avl_crate(portable = True)` and
  build and test on Windows: `avl-base`, `avl-wire`, `avl-trace`, `avl-record`, `avl-report` and `avl-testkit`.
  `avl.bzl` gives the rest `NOT_ON_WINDOWS` ([ADR 0059](decisions/0059-the-ui-lane-tooling-is-rust.md): "Windows is
  not a supported host"). `bt` is not one of them; it lives in `community/tools/bt`. The controller path `avl-vm`,
  `avl-lane`, `avl-worker`, `avl-host-sys`, `avl-daemon` and `avl-trace-serve` holds about 36 `std::os::unix` lines,
  13 of them outside tests: file modes, `O_EXCL` creates, process groups, exit-status signals, symlinks. `avl-guest`
  runs only inside a guest and needs no port.
- **The controller assumed an arm64 guest in seven places.** ADR 0184 listed five. The engine gate in `docker.rs`.
  The guest-agent and recorder labels (`agent_label`, `ui_lane_ide.bzl`), which needed `x86_64-unknown-linux-musl`
  builds of `vm-guest-agent` and `air-trace-record` beside the arm64 ones. The Node archive label
  (`node_archive_label`), where `node-v24.19.0-linux-x64.tar.gz` was already pinned. The validator's loader
  constants in `crates/avl-guest/src/linux.rs`. The lane's distribution, because `--config=air-lane-linux` sets
  `air_lane_guest_os=linux`, which `ui_lane_ide.bzl` mapped to the `linux_aarch64` distribution and JBR. Two more
  were found in the implementation. `guest_jbr_platform` in `avl-lane/src/env.rs` is what the daemon's descriptor
  check compares against. `agent_cli_runtime.bzl` moved every agent CLI runtime to the arm64 platform under the
  define. The direct Linux host struct `_LINUX_X64_HOST` has the host distribution and the `linux_x64` JBR
  manifests, but the host recorder and `screen_owned = False`. The amd64 guest got a struct of its own.
- **The share grammar refuses a Windows path.** `Docker::share_arguments` refuses a host path with a `:`, because
  `-v` is `source:target:options`. `C:\Users\...` has one. The parity layout recreates the host's absolute checkout
  and Bazel-root paths inside the guest as symlinks onto the share mounts under `/mnt/AirVmShares/<share>`, so a
  `C:\` path cannot exist as a guest path at all. `std::fs::canonicalize` on Windows answers a `\\?\C:\` prefix.
  Phase 2 of route 2 closed both: the shares are `--mount` binds, and the guest names each host path by its drive
  form (ADR 0186).
- **Docker Desktop's file sharing is the performance trap.** A bind mount of a Windows path (`C:\...`) crosses the
  Windows-to-WSL2 file boundary, and the guest reads the whole IDE distribution and every test jar through the share.
  A bind mount of a path inside the WSL2 distribution's own filesystem is a local ext4 mount in the same kernel. So
  the checkout and the Bazel output root must live inside WSL2, and the same applies to the daemon's staged runtime,
  which the worker reads from the share at each start. The 2 s settle of ADR 0183 is a VirtioFS attribute-cache
  measurement on OrbStack; a WSL2 ext4 bind mount has no such cache, and the number must be measured again there.
- **The host defaults were a Mac's.** The runtime root was `~/Library/Application Support/JetBrains/macos-vm-ui-tests`,
  the Bazel user root default was `~/Library/Caches/JetBrains/MonorepoBazel`, and `HOME` is required.
  `AIR_VM_RUNTIME_ROOT` and `AIR_VM_BAZEL_USER_ROOT` override both.
- **`vm.cmd` has a CMD half**, like `bt.cmd`, so a Windows shell starts the wrapper. It then asked Bazel for a binary
  Bazel refuses to build on Windows.

## Design

Two routes reach the goal. Take the first; it contains everything the second needs except the port.

### Route 1: the controller runs in WSL2, against Docker Desktop's engine

Windows developers and agents install Docker Desktop with the WSL2 backend and enable its WSL integration for an
Ubuntu distribution. Inside that distribution, `docker` is Docker Desktop's engine, Bazel runs as on any Linux x86_64
host, and the checkout and the Bazel output root are ext4 files. The controller is then a Linux x86_64 host, which
the repository already builds for (`x86_64-unknown-linux-gnu` in `platform_triples`, `@community//build:host_linux_x64`).

What it needs, and what was built (the differences from the first draft are named):

1. **The amd64 guest.** One `GuestArch` axis beside `GuestOs`. It follows the host, not the engine. `GuestArch::of`
   is `X86_64` for a Docker worker on a Linux x86_64 host and `Arm64` everywhere else. The reason is that the lane
   build picks the agent, recorder and Node labels before any engine is asked. The engine gate then checks that the
   engine runs that architecture natively and refuses another one by name. The seven seams read it. The gate accepts
   `linux/amd64` and `linux/x86_64` for an x86_64 guest. `agent_label` and the recorder label pick the
   `-linux-x86_64` targets, two `avl_binary` rules on `@rules_rs//rs/platforms:x86_64-unknown-linux-musl` with a
   matching `clippy-linux-x86_64`. `node_archive_label` picks `linux-x64`. The validator reads the loaders of the
   architecture it was built for (`/lib64/ld-linux-x86-64.so.2`, `/lib/ld-musl-x86_64.so.1`). The agent installed
   in a guest is the guest's build, so it needs no argument. `guest_jbr_platform` answers `linux_x64`. There is no
   new Bazel config and no new define. `//plugins/air/tests/integration/ide:air_lane_guest_linux_on_host_linux_x64` mapped a Linux x86_64 host
   to the arm64 guest, which no worker there can serve. It now maps to `_LINUX_X64_GUEST` in `ui_lane_ide.bzl`: host
   distribution, `linux_x64` JBR manifests, the static x86_64 recorder, `screen_owned = True`.
   `agent_cli_runtime.bzl` lets the runtimes follow the host there. `--config=air-lane-linux` stays the one analysis
   key. The pins test does not change, because `versions.env` has no entry per architecture. The test pools pin
   arm64, so the suites assert the same labels on a Mac and on a Linux x86_64 CI agent.
2. **Linux host defaults.** A runtime root at `~/.local/state/JetBrains/air-vm-ui-tests`, and a Bazel user root
   default of `~/.cache/JetBrains/MonorepoBazel`, the `startup:linux --output_user_root` of
   `community/common.bazelrc`. `RUNTIME_ROOT` and `BAZEL_USER_ROOT` are `cfg!(target_os)` constants. The XDG
   variables are not read; the two `AIR_VM_*` overrides stay the way to move them.
3. **A measurement on WSL2.** `pool start air-docker-1`, `status`, and `run AgentSessionToolWindowComposerUiTest`
   on an x86_64 and on an arm64 Windows PC with the checkout inside WSL2. Record the boot, the staging, the share refresh behaviour after a
   rename on the host (the ADR 0183 spike, repeated on ext4), and the lane time in the guide's table. Then try the
   same with the checkout on `C:\` through `/mnt/c`, once, to record why the guide says not to.
4. **The guide and the skill** get a "Windows" section: install Docker Desktop, enable WSL integration, clone inside
   the distribution, run `vm.cmd` there. The CMD halves of `vm.cmd` and `trace.cmd` print that sentence and exit 78
   on Windows, instead of asking Bazel for a binary it cannot build.

### Route 2: a native Windows controller

Everything of route 1, plus the port: the `std::os::unix` uses behind `cfg` gates with Windows equivalents (file
ACLs for the 0600 receipts, job objects for the process groups, no signals in exit statuses), `share_arguments`
accepting a drive letter and the parity layout mapping `C:\` prefixes, `canonicalize` without the `\\?\` prefix, and
`vm.cmd`'s CMD half doing what the sh half does. It also inherits the file-sharing trap, because the checkout and the
Bazel outputs then sit on the Windows side. ADR 0059's reasons for not supporting Windows still hold for the Tart and
Parallels backends, so a Windows host drives the Docker backend only.

#### Why route 2

The first draft took route 1 only. The first Windows host refused it. A Windows 11 arm64 VM in Parallels on Apple
silicon has no nested virtualization, and `wsl --install` fails there with `HCS_E_HYPERV_NOT_INSTALLED`. Parallels
KB 129234 and KB 116239 confirm it. A PC without WSL2 must run the controller as a Windows program. It still needs a
Docker engine: Docker Desktop, or another engine that the `docker` CLI reaches.

#### The phases of route 2

[ADR 0186](decisions/0186-a-windows-host-runs-the-controller-natively.md) records the decisions and the evidence.

1. Done: the controller builds and tests on Windows (commit 7c55a151ea9bb). Each Unix host interface has one seam,
   the Tart and Parallels modules are `cfg(unix)`, and a Windows host defaults to `--backend docker`. A Windows host
   cross-builds the Linux guest binaries (commit 2682f3f75b271).
2. Done: host paths in the guest (commit 8c1d37cfdfd59). `GuestPaths` in `avl-host-sys/src/paths.rs` maps each
   host root to a guest root: itself on Unix, and the drive form `/c/...` on Windows. A path under neither share is
   `guest_path_unmapped`. Docker binds the shares and the volume with `--mount`.
3. Done: the guest builds the runfiles tree from the MANIFEST that Windows Bazel writes (commits 70c1259b2840c and
   37acc1e2c65c6). `HostRunfiles` chooses a tree or a MANIFEST from the disk, and `ensure_runfiles_tree` calls the
   `runfiles-tree` verb in the `parity-probe` phase.
4. Done: an x86_64 Windows host gets the amd64 guest and `idea_air_lane_dist_linux_x64` through
   `air_lane_guest_linux_x64_cross` (commit 74118c7a93fd4).
5. Done: the CMD halves of `vm.cmd` and `trace.cmd` build and run the binaries, as `bt.cmd` does (commit
   9f6c4e284966f).
6. Done: the guide, the skill, the vm-lane README and ADR 0186.
7. Open: a `vm.cmd --backend docker run <suite>` that passes on an x86_64 and on an arm64 Windows PC with a Docker
   engine, with its time in the guide's table. It is the first check of `--mount source=C:\…` on Docker Desktop,
   of the MANIFEST name that the daemon descriptor gets, of the MANIFEST branch through a real daemon start, and of
   the share settle. Then ADR 0186 becomes Accepted.

The evidence of the Windows 11 arm64 VM, with native Bazel on the final tree: `//plugins/air/tests/integration/vm-lane/...`
passes 720 of 720 cases in 15 targets. `avl-trace-serve-test` does not run there, because its Kotlin test data needs
jars from the private Maven repository `tbx/toolbox-dependencies`, which answers 401 without credentials.
`vm.cmd --backend docker status` answers `docker_missing` (exit 69), because the VM has no Docker engine, and
`trace.cmd plan` works.

## Order and size

1. Done: the amd64 guest (route 1, step 1). One crate axis, two Bazel targets and a clippy target, one Starlark
   struct, and the validator's own-architecture loaders. It also makes a Linux x86_64 CI agent a lane host.
2. Done: the Linux host defaults and the `vm.cmd` message (steps 2 and 4).
3. Open: the WSL2 measurement (step 3). The first Windows host cannot run WSL2, so route 2 came first.
4. Done: the successor to ADR 0059 is [ADR 0186](decisions/0186-a-windows-host-runs-the-controller-natively.md).
   It names Windows as a host of the Docker backend. It does not name WSL2 numbers, because none are measured.

## Acceptance

- `vm.cmd --backend docker status` on an x86_64 and on an arm64 Windows PC reports `image=pulled` after a cold
  `pool start`, with no build. For route 2 that is a Windows shell. For route 1 it is a WSL2 Ubuntu shell.
- `run AgentSessionToolWindowComposerUiTest` passes 8 of 8 there, with 8 videos, and the guide's table has the time.
- `BT --lane fast` is green on macOS and on the Linux x86_64 CI agent, with the new clippy target in it.
- The fake-backed tests hold both arches: an engine that answers `linux/x86_64` gets the x86_64 labels and loader.

## Risks

- **The distribution for a `linux_x64` guest on a Linux x64 host is the host build.** That is correct only when host
  and guest arch match. The mapping guarantees it: only a Linux x86_64 host selects that guest. A Mac driving an
  amd64 guest under emulation would need a cross distribution `linux_x64` beside `idea_air_lane_dist_linux`, and
  this note does not ask for it.
- **Emulation is not a lane host.** OrbStack and Docker Desktop on Apple silicon run amd64 containers under
  Rosetta; the JVM and the IDE run, but the timing is not a verdict. The guest arch follows the host, so a Mac
  always runs the arm64 half. An engine that runs another architecture is refused by name. An Intel Mac gets
  the same refusal, because its Starlark selection is the arm64 guest.
- **Docker Desktop licensing.** ADR 0183 notes that Docker Desktop is not free for JetBrains at company scale. Route 1
  needs Docker Desktop, or a Docker engine installed inside the WSL2 distribution directly, which is free and works
  the same way for this controller because it talks to the `docker` CLI only. Measure with the engine inside WSL2
  first.
- **The WSL2 filesystem boundary is easy to cross by accident.** A checkout under `/mnt/c` works and is slow. The
  controller should refuse a checkout or a Bazel user root under `/mnt/<drive>/` with a named refusal, once step 3 has
  the numbers that justify the refusal.
