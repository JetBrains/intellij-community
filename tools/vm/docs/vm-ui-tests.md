# Running UI Tests in a VM

`BT --lane ui` and `--lane ui-real` launch a real IDE with `InputEventsRobot`. That robot posts
component-relative events through the IDE event queue, and it never moves the physical pointer.
`BT --lane gui-chat` may still use `SmoothRobot` and physical input. A UI lane is run and judged in
a VM, and the controller's default is a Linux container on a Docker engine, which on a Mac is a Lima
VM. The VM isolates the host session, and it gives a failure a real screenshot. Never move a run onto the host silently.

The flow lanes validate Swing and AWT component behaviour. They do not validate native title bars
and menus, OS dialogs, WindowServer hit testing, or physical focus. That coverage stays separate.
The UI hierarchy only locates and inspects components, and it is not an interactive DOM.

The **`vm-ui-tests` skill** (`.agents/skills/vm-ui-tests/SKILL.md`) owns the procedure: what to run,
how to drive a run, the receipt loop, the repair, and what needs the user's choice. Its `references/`
hold the exit codes and the `status` rows (`reading-output.md`), the `AIR_VM_*` settings, `shard` and
`flake`, the macOS pool, and the Parallels VM with Peekaboo. The command grammar is
`./community/tools/vm.cmd --help`. This guide states the model behind them. The controller's
package map is `community/tools/vm/README.md`.

```bash
./community/tools/vm.cmd status
```

You install no Tart. The controller asks Bazel for a pinned Tart at its first Tart command, and only
that request downloads it. An IDE sync or a `bazel build //...` does not
([ADR 0158](decisions/0158-the-lane-controller-gets-tart-from-bazel.md)). `TART_BIN` names another
Tart. Only the `softnet` network mode still needs a manual install, of Softnet.

Leasing a Linux worker is part of running the lane. The user's own choice is still needed for a
macOS guest, for the golden image, and for the Parallels VM. It is needed for a host lane too, which
takes the user's own screen and focus.

The host-side provider, layout-plan, fragment and composition flow is documented
in [Dev-build architecture](../../../../build/dev-build-architecture.md). The guest consumes a
self-contained Bazel distribution, and it needs no checkout of its own. Nothing caches a guest run,
so an iteration runs every suite it selects again. On 2026-09-30 the `ui` lane was 71 tests in 40
classes, and it took 750 s of test time on a Tart Linux worker with a cold daemon. Ask `vm.cmd suites <paths>` what your change reaches, then run `run --changed <paths>`. To
test one flow, run `vm.cmd run flow-rename-session`: it selects every generated suite that tells or
walks the flow. `vm.cmd run rename-session` selects one suite by its id. A suite is the smallest unit,
because its scenarios share one managed-agent chain
([ADR 0157](decisions/0157-a-flow-is-a-test-selector.md)).

A `run` without `--lease-file` builds on the host, leases one worker, and releases it on every exit,
so one line is a whole run. Without `--lane`, it runs one iteration for each lane the answer reaches
([ADR 0159](../../../../plugins/air/docs/decisions/0159-one-run-takes-every-lane-it-reaches.md)). A receipt from `lease acquire`
keeps one worker across several iterations, which is the warm inner loop. An interrupt of a self-leased run keeps the lease, because
the iteration can still run on the guest, and the refusal names the receipt that frees it.

## The three guests

All three run tests the same way. The guest executes Bazel outputs built on the host, against a warm
IDE that a guest daemon holds open. They differ in what kind of machine they are, and in what guest
runs in it.

| | Docker (default) | Tart macOS | Parallels |
| --- | --- | --- | --- |
| machine | a container on the controller's Lima VM, or on the engine that `DOCKER_BIN` or `DOCKER_HOST` names | a Tart VM | one Parallels VM |
| image | a Dockerfile from a digest-pinned `ubuntu:26.04`, pulled from the JetBrains registry by its content tag, built when the pull fails | a sealed Packer golden | one pre-existing VM |
| session | `Xvfb :88` and fluxbox, from the image's entrypoint | Aqua | Aqua |
| root disk and memory | the engine VM's, shared by every container | 120 GB, 32 GiB | the VM's own |
| `pool stop` | stops the container, then the Lima VM when nothing is leased, keeps nothing warm | `tart suspend`, keeps the daemon and the IDE | suspends |
| Robot screenshot | a real frame | black, see ADR 0113 | a real frame |
| Peekaboo | no | no | yes |
| provenance | the base digest and the image tag | a seal receipt per worker | none |
| concurrent lanes | one per container | one per worker | one, sequential |

Docker is the default on every host
([ADR 0190](decisions/0190-the-docker-engine-is-the-default-worker.md)), and it is the one Linux worker
([ADR 0210](decisions/0210-the-docker-image-carries-node-and-the-tart-linux-worker-is-retired.md)). A red run
there is a verdict. `java.awt.Robot` captures a real frame there, so a failure arrives with a picture. It needs
no installation, and one engine holds two lanes. A macOS guest preserves JCEF, Terminal, native-menu,
WindowServer and Aqua behaviour, and that is where coverage about those things belongs. Select one with
`--backend tart` or `--backend parallels`. A lease receipt records the selection, so a receipt-bearing command
infers it and rejects a conflict.

**The Tart macOS lane is known red.** At one tree it was 23 of 23 on Linux and 8 of 24 on macOS, and
every macOS failure is macOS-only. The mechanism is window activation:
[ADR 0113](../../../../plugins/air/docs/decisions/0113-the-macos-lane-is-red-on-window-activation.md) and
[IJAI-1228](https://youtrack.jetbrains.com/issue/IJAI-1228) own it. Ask for that backend explicitly,
and do not read a red run there as a verdict about the product.

A Tart pool is a set of **slots**, not a set of VMs. A slot is a name the controller may use, and
the first operation that needs one clones the golden into it. So `pool init` is an optional
pre-warm, and `pool gc` deletes the clones behind idle slots. Materializing the first slot of an
empty macOS pool measured 82 s, and the second 39 s. The golden's pages stay warm in the host's
cache. A worker's guest-local state lives on the normal macOS Data volume, under `WorkerData`. That
avoids the non-durable ScalablePFK state of an APFS filesystem created inside a guest-attached raw
disk.

Parallels uses one pre-existing Apple Virtualization macOS VM, which the controller never creates,
clones, resizes or provisions. macOS here is this controller's limit rather than Parallels': the
path is macOS-shaped, so `Config::load` rejects any other pairing. A Windows host drives the Docker
backend only ([Windows](#windows) below). Do not remove this
backend. It has the longer demonstrated record, and its Peekaboo passthrough has no Tart equivalent.

`AIR_VM_NETWORK` picks one of three network modes, and `nat` is the default because it asks nothing
of the host.

`softnet` confines a worker. On a host set up for it, Softnet limits the guest to globally routable
IPv4 and its own bridge gateway: the real-agent CLIs still reach the internet, the local network does
not answer, and one worker cannot ARP-spoof another. Incoming traffic stays unrestricted. The daemon
connection and the artifact pulls use the exec channel, not the guest network, so Softnet does not
affect them. It is not the default because it needs passwordless sudo, and `tart run` dies without it.

`bridged` is not about isolation at all. It exists because a running worker breaks a VPN client on
the host. Both `nat` and `softnet` use Apple's shared vmnet bridge, and that bridge starts
`InternetSharing`, which makes `mDNSResponder` hold DNS port 53. Cloudflare WARP cannot then start
its own DNS proxy, and it reports `CF_DNS_PROXY_FAILURE`. Bridged mode attaches the guest to a real
host interface instead, so no shared bridge and no `InternetSharing` appear. The interface is named
by `AIR_VM_BRIDGED_INTERFACE` and it is required: a connected VPN puts the host's active route on a
`utun` device, which cannot be bridged, so there is no default worth guessing. The guest then takes a
LAN address rather than a `192.168.64.*` one, which needs a LAN DHCP lease and makes the worker
visible on the local network. Nothing else in the controller notices, because the controller uses no
guest address and the exec channel runs over vsock.

The mode is part of the `tart run` argv. Changing it restarts a worker, and it also spends a suspended
worker's saved guest state, because `discard_incompatible_suspended_state` compares the recorded argv.

**A `pool stop` after a release must name the pool.** The receipt that would have inferred it is
gone with the release. An unqualified `pool stop` then stops the *Docker* workers, whichever guest
just ran.

### The golden image

Packer builds the Tart macOS golden from a Cirrus base. That base has a pinned OCI manifest digest
and a pinned macOS version, and **the digest is the whole pin**. The build never copies host source
into the image, and it never passes private JetBrains credentials. It does not install
Bazel/Bazelisk, Peekaboo, a checkout, or cache contents. `validate-image` reports what a golden does
contain, including its macOS version and each installed agent CLI build. Provenance and TCC
admission are fail-closed at every step, and no registry publish operation exists. An `image build`
costs about a day of downloading rather than minutes of provisioning, because the cost is the base
pull: run it detached. `community/tools/vm/provision/README.md` and
[ADR 0108](decisions/0108-the-guest-half-of-the-image-pipeline-is-go.md) own the pipeline.

## The Linux guest

The hypervisor and the guest OS are separate axes. The one Linux guest is the Docker worker, and the
section below describes its container layer. The Tart Linux worker was retired on 2026-10-08
([ADR 0210](decisions/0210-the-docker-image-carries-node-and-the-tart-linux-worker-is-retired.md)).
Everything above still applies: two read-only shares, the parity layout, the exec channel and a daemon with a
warm IDE. Four macOS obligations have no counterpart here, so the controller does not put them to a Linux
guest. They are TCC admission, the console-login wait, the APFS storage initializer and sealed-golden
provenance. A Docker row of `status` has no field for any of them.

A Linux guest has no seat until something starts an X server. The entrypoint of the image starts
`Xvfb :88` and `fluxbox`, and it waits for the display to answer. IDE Starter then takes the
branch it already has: `LinuxIdeDistribution.linuxCommandLine` wraps a launch in `xvfb-run` only
when `DISPLAY` is unset, and `XorgWindowManagerHandler` checks the window manager through EWMH. So
every IDE of a lane reuses one display. A server image also carries none of the shared libraries the
IDE's own native code links against. `GUEST_PACKAGES` names them, and their absence is quiet.

So each boot ends with the guest proving itself. `validate-guest` refuses the boot unless four things
hold. The C library is glibc, the display answers, a window manager is registered on it, and `ldd`
resolves every staged shared object. Each refusal carries its own code and sentence.
[ADR 0108](decisions/0108-the-guest-half-of-the-image-pipeline-is-go.md) is why.

The image carries Node 24.19.0 at `/usr/local/bin/node`, and `AIR_VM_NODE` defaults to that path. The
image tag pins the Node, so a boot runs no Node check ([ADR 0210](decisions/0210-the-docker-image-carries-node-and-the-tart-linux-worker-is-retired.md)). Only `ui-live` uses the guest Node.
Its npm-installed registry ACP agents and the Claude CLI run on it, through `AIR_VM_NODE` and `NODE_BIN`
in the daemon environment.

A boot installs no agent CLI. Pi and Codex both come from declared Bazel runtimes under
`plugins/air/tests/tools`. A runtime holds the pinned version and its locked npm dependencies. It also
holds Node when the CLI is a Node program.
The daemon receives these inputs through its runfiles. The IDE receives one launcher directory before
other PATH entries.
Missing inputs or a different version fail before the IDE starts. Host overrides cannot replace a
runtime. VM preparation installs and probes no agent CLI, and existing images need no rebuild.
[ADR 0137](../../../../plugins/air/docs/decisions/0137-agent-clis-are-declared-bazel-test-runtimes.md) is why.

## The Docker guest

The default backend, `docker`, runs the Linux guest in a container on a Docker engine.
[ADR 0183](decisions/0183-a-linux-worker-may-be-a-container.md) made it a backend,
[ADR 0190](decisions/0190-the-docker-engine-is-the-default-worker.md) made it the default, and
[ADR 0210](decisions/0210-the-docker-image-carries-node-and-the-tart-linux-worker-is-retired.md) made it the one Linux worker. The guest is the Linux guest of the section above. This section describes the
container layer below it.

The image is `community/tools/vm/docker/Dockerfile`. The base is `DOCKER_BASE_IMAGE`
in `community/tools/vm/provision/versions.env`, pinned by digest. The package list is `GUEST_PACKAGES`, passed
as a build argument. It holds `git`, because the worktree and merge-conflict scenarios spawn it, and the
Docker base has none. The Dockerfile also installs Node 24.19.0 from nodejs.org for the `TARGETARCH` of
the build, and it checks a pinned sha256. The tag is `air-ui-worker:<12 hex digits>`, a digest over the Dockerfile, its
`air-display` entrypoint, the base image and the package list. A start that finds no such tag on the
engine pulls `registry.jetbrains.team/p/ij/containers-public/air-ui-worker:<tag>` first, and builds only
when the pull fails or the pulled image's `org.opencontainers.image.revision` label is not the tag digest
([ADR 0184](decisions/0184-the-worker-image-is-pulled-by-its-content-tag.md)). The tag is the only pin.
`AIR_VM_DOCKER_REGISTRY=off` builds without a registry, and `AIR_VM_DOCKER_PUSH=1` makes the controller
build and publish the tag for `linux/arm64` and `linux/amd64` as one image index, which is how the
registry gets a new tag. So a fresh worker runs no `apt-get`, and a cold host runs no build. A tag that the
registry does not hold yet is built once on each host, with network access to Docker Hub and nodejs.org. A Docker
worker runs the host's own architecture: arm64 on Apple silicon, amd64 on a Linux x86_64 host. On the
x86_64 host the guest agent, the recorder, the Node of the image and the distribution are the x86_64 builds
(`GuestArch` in `crates/avl-base/src/config.rs`). The engine gate refuses an engine of another
architecture by name, so an emulated container is never a lane. The entrypoint starts `Xvfb :88` and fluxbox and waits until both answer.
`validate-guest` then proves the guest.

A worker is a container, and the container name is the worker name, `air-docker-N`. The pool has two
slots by default, as the Tart macOS pool does, and the two containers share one engine. `docker exec` is the
exec channel. It lands as root, so the `sudo -H -u admin` prefixes apply unchanged, and `docker exec -i`
carries the relay's bytes without a tty.

The two shares are bind mounts at `/mnt/AirVmShares/<share-name>`, read-only, which are the paths the
parity layout expects. **There is no remount on Docker.** A spike on 2026-09-29 replaced a file by rename
on the host, as Bazel does. In the container, `cat` returned the new bytes at once, and `open` never
failed with `ENOENT`. Only `stat` was late: it reported the old size at +0 s and the new size from +1 s
on. On the Lima engine, on 2026-09-30, `open` failed with `ENOENT` after the rename when the guest had
looked the path up just before it. That dead node expired by itself in under one second, and no remount
was needed ([ADR 0189](decisions/0189-the-docker-engine-is-a-lima-vm-the-controller-owns.md)). So a
remount on Docker is a 2 s settle for the virtiofs dead nodes and the attribute cache, on both engines.
A lane run on Docker goes through
the same daemon, and the remount step of the daemon becomes the 2 s settle. The controller recreates a
container when its create arguments change. The record of those arguments covers the shares, the image
tag, the display and `AIR_VM_SCREEN`. A warm iteration compares the record too, before it reuses the
daemon. On a difference it recreates the container under its own lease and starts the daemon cold. A missing record also gives a recreate, and so does an engine
container id that differs from the recorded id. `pool start` and `pool init` refuse with
`worker_leased` when the stale container runs and another holder leases it. A stopped stale container
is recreated for any caller.

The guest-local state is the named volume `air-<worker>-data` at `/home/admin/WorkerData`, so a
recreate keeps the staged runtime. `pool stop` stops the container and never suspends. `pool recycle`
removes the container and its volume. `pool gc` removes stopped, unleased containers and keeps their
volumes. `vnc`, `peekaboo` and `image validate|build` have no meaning here, and they refuse with
`unsupported_backend_operation` (exit 2).

One rule decides the engine, and no flag selects it. When `DOCKER_BIN` or `DOCKER_HOST` names an
engine, the controller runs that CLI against that engine. When neither variable is set, a macOS host
runs a Docker CLI that Bazel pins against a Lima VM that the controller owns. So a Mac installs
nothing, as for Tart. OrbStack is the fast path, and the first two lane rows of the cost table ran on
it. A Mac with OrbStack keeps it with one variable:

```bash
export DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock   # or DOCKER_BIN=<OrbStack's docker>
```

The VM is `air-docker-engine` under `AIR_VM_LIMA_HOME`, made from `community/tools/vm/docker/engine.lima.yaml`.
It boots Ubuntu 26.04 and runs a rootful `dockerd` of the Ubuntu archive. Lima forwards the Docker socket
through its SSH connection, and on 26.04 that connection runs over vsock, not through Lima's userspace
network stack ([ADR 0191](decisions/0191-the-engine-vm-leaves-the-usernet-path.md)). The VM has one
network, the usernet of Lima. Its disk is raw. It mounts the home read-only at its host path, as
the default template of Lima does, so the two shares bind as on any engine. A repository or a Bazel
output user root outside the home is refused `share_outside_home`. The socket reaches the controller
as `DOCKER_HOST`. The pinned CLI has no `buildx`, so the controller also pins buildx. With the pinned
CLI (`DOCKER_BIN` unset), the controller passes `DOCKER_CONFIG=<runtime root>/docker-config` and names
the pinned plugin in its `config.json`, on every engine. It keeps every other key of that file, so a
`docker login` made there survives. A CLI that `DOCKER_BIN` names keeps its own configuration.
`AIR_VM_CPU` and `AIR_VM_ROOT_DISK_GB` size the VM with the Linux defaults. `AIR_VM_MEMORY_MB` has a
larger default for the engine, because the two containers share its memory. The skill's settings
reference lists the values.

A Docker command that needs the engine starts it, under one engine lock, so concurrent commands start
it once. The first start downloads the cloud image and runs `apt-get`, so it takes minutes. Lima keeps
the image in `~/Library/Caches/lima`, which every Lima home shares, so a VM that is made again
downloads no image. A change of the home, a size or an image pin makes the VM again, and a checkout
switch keeps it. A lease of another process blocks that recreate. The leases of one `shard` or `flake` do not. `pool stop` stops the VM after the
containers, when no worker is leased and no container runs. `pool recycle all` deletes the VM unless a
worker holds a lease of another process, and `pool recycle <worker>` keeps it. `pool gc` keeps it, and on a stopped VM it runs
no `docker` command. `pool stop`, `pool gc` and `lease release` never start the VM or make it again. A
`lease release` on a VM that does not run refuses `worker_stopped`. `status` never starts the VM, and it names the engine in its first line. A
publish with the pinned CLI needs a login into that configuration first:

```bash
DOCKER_CONFIG="<runtime root>/docker-config" docker login <registry>
```

A Linux or a Windows host keeps the engine it has, because Lima needs QEMU on Linux and WSL2 on Windows.

The live lane `ui-live` runs on the Docker guest too, but only when a caller names it, because each of its scenarios
spends a billed turn ([ADR 0200](../../../../plugins/air/docs/decisions/0200-the-live-lane-also-runs-in-a-linux-guest-with-a-per-run-central-login.md)).
Its lane table entry has no catalog lane, so no flow, suite or `--changed` reaches it, and `shard` and `flake` refuse it
with `explicit_only_lane`. The run hands the guest a per-run Central login as a run secret file:

```bash
central login export | ./community/tools/vm.cmd run --lane ui-live --test-env AIR_LIVE_CENTRAL_LOGIN=@-
```

The test JVM imports that login into the guest account, starts the Central proxy and wires the agents with
`central add`. It tears all of it down when the iteration's test plan ends, before the run reports its verdict. A
macOS guest refuses the lane by name. The pinned Linux CLIs
reach only a build that opts in with `build:air-lane-linux --define=air_lane_live_runtimes=on` in
`.bazelrc-user.bazelrc`, until the mirror holds them. Every session of this checkout shares that file, so their
Linux-guest builds then fetch the archives too. The skill's live-lane reference has the procedure.

## Windows

A Windows PC runs the controller natively, on x86_64 and on arm64
([ADR 0186](decisions/0186-a-windows-host-runs-the-controller-natively.md)). It drives the Docker backend only.
The CMD halves of `vm.cmd` and `trace.cmd` build and run the binaries, as `bt.cmd` does.

1. Install a Docker engine that the `docker` CLI reaches. That is Docker Desktop, or another engine. The controller
   talks to the `docker` CLI only. Docker Desktop is not free for JetBrains at company scale
   ([ADR 0183](decisions/0183-a-linux-worker-may-be-a-container.md)).
2. In a Windows shell, run `community\tools\vm.cmd status`. With no `--backend`, a Windows host takes `docker`.
   `--backend tart` and `parallels` are refused with `unsupported_host_backend` (exit 2). An engine that
   does not answer is `docker_missing` (exit 69).
3. The runtime root is `%LOCALAPPDATA%\JetBrains\air-vm-ui-tests`, and the Bazel root is `C:/ProgramData/_bazel`, the
   `startup:windows` root of `community/common.bazelrc`. `AIR_VM_RUNTIME_ROOT` and `AIR_VM_BAZEL_USER_ROOT` move
   them. `HOME` falls back to `USERPROFILE`.
4. `community\tools\vm.cmd run <suite>` runs the lane in a worker of the PC's architecture: the `linux/amd64`
   half of the image on an x86_64 PC, and the `linux/arm64` half on an arm64 PC.

The host paths are Windows paths, and the guest is Linux, so two steps differ from a Unix host:

- **The shares.** `GuestPaths` (`avl-host-sys/src/paths.rs`) maps each host root to a guest root: itself on a Unix
  host, and the drive form on Windows, `/c/Users/...` for `C:\Users\...`. A Windows prefix matches without case,
  because Windows Bazel writes its output root in lower case. A host path under neither share is refused as
  `guest_path_unmapped`. Docker binds the shares and the data volume with `--mount`, which refuses a source path with
  `,`, `"` or `=` as `unsafe_share_path`. The parity receipt records the guest roots too (`guestRepo`,
  `guestBazelUserRoot`), so a worker that a Unix controller provisioned provisions again once.
- **The runfiles.** Windows Bazel writes the runfiles MANIFEST of a test and no runfiles tree. `HostRunfiles`
  decides from the disk: a `<descriptor>.runfiles` tree is used as it is, and otherwise the controller reads
  `<descriptor>.runfiles_manifest`, then `<descriptor>.runfiles/MANIFEST`. In the `parity-probe` phase, the guest
  verb `runfiles-tree` builds the tree at `<vmData>/runfiles/<digest>`, the root the host predicted. It keeps that
  tree and the newest other one. A root other than the predicted one is `guest_runfiles_mismatch`, and a bad reply
  is `guest_runfiles_protocol`.

No `vm.cmd run` has passed on a Windows PC with a Docker engine yet. So `--mount source=C:\…` on Docker Desktop, the
MANIFEST name that the daemon descriptor gets, and the MANIFEST branch through a real daemon start are not
confirmed, and the cost table below has no Windows row. The 2 s share settle of ADR 0183 is a VirtioFS measurement
on OrbStack, and a Windows engine needs its own. A bind mount of a Windows path crosses from Windows into the
engine's Linux VM, and the guest reads the IDE distribution and every test jar through it. That cost is not
measured yet either.

On a PC with WSL2, the controller can also run in a WSL2 Ubuntu distribution, as a Linux host. Clone the repository
inside the distribution's own filesystem, not under `/mnt/c`, and run
`./community/tools/vm.cmd --backend docker` there. The Linux defaults apply: the Bazel output root is
`~/.cache/JetBrains/MonorepoBazel`, and the runtime root is `~/.local/state/JetBrains/air-vm-ui-tests`.
A Windows VM in Parallels on Apple silicon has no nested virtualization and cannot run WSL2, which is why the native
route exists. No lane time is measured for this route.

## How a run reaches the guest

Two `jps_test` targets launch an IDE: `//plugins/air/tests/integration/ui:ui_test` and
`//plugins/air/tests/integration/gui-chat:gui-chat_test`. Their runfiles carry everything a run
needs. The IDE arrives as an already-assembled distribution: `//build:idea_air_lane_dist_linux` on
the default guest, and `//build:idea_air_lane_dist` on a macOS one. `-Didea.ide.config.path` names
that distribution's `_ide_config` companion target rather than the distribution itself.

`run` resolves the selector with bt's resolution logic. It runs `bazel build` on the host against
its warm cache, then it launches the built test stub in the guest. The launch environment is the one
Bazel's test runner would provide, plus `-Didea.home.path=<repo>`, so the workspace guess never
lands on a read-only mount.

The guest sees host paths through two read-only shares, the repository root and Bazel's output user
root, and a **parity layout** makes them position-identical. The repository path exists in the guest
as a real directory of per-entry symlinks onto the read-only mount. `out` alone is redirected to a
guest-local writable directory, and the output user root is a single symlink. IDE Starter's
`GlobalPaths` writes everything under `<checkout>/out`, so that one redirect is what makes a
read-only checkout runnable. The IDE itself is read, not written.

Both shares arrive on Apple's one VirtioFS automount device, and the controller mounts that device
itself: at `/mnt/AirVmShares` on a Linux guest and `/Volumes/AirVmShares` on a macOS one
(`GuestOsProfile` in `crates/avl-base/src/config.rs`). Every `ls` and `pull` argument hangs off
that mount point, so read a path off the guest rather than assume it. A space-free mount point
keeps every guest path one shell word. The backends differ only in how a share is declared. Tart
declares each share as `--dir=<share-name>:<host-path>:ro`, and both halves are load-bearing. The `<share-name>:` prefix
names the share's directory under the mount point. Passing no `tag=` option keeps the share on the
single `com.apple.virtio-fs.automount` device, which is the only one the guest-side remount mounts. A
tagged share is invisible to the guest, and an unprefixed one lands under the host directory's
basename. `share_argument` in `crates/avl-vm/src/worker/tart.rs` owns that grammar, as `declare_shares` in
`crates/avl-vm/src/worker/parallels.rs` owns prlctl's. A share-set change restarts the
`tart run` process the controller owns, and it power-cycles the Parallels VM once.

A Linux run adds `--define=air_lane_guest_os=linux` to the host build. On a Mac it selects the
`linux_aarch64` distribution and JBR. On a Linux x86_64 host it selects the x86_64 guest, whose distribution
is the host's own build (`//build:air_lane_guest_linux_on_host_linux_x64`). **That option and
`--build_runfile_links` ride every Bazel command the controller runs**, build, cquery and info
alike. They have to: both are build options, so a `cquery` that omits what the preceding `build`
passed makes Bazel discard the analysis of every configured target. The root `.bazelrc` holds them
as one config per guest, `air-lane-linux` and `air-lane-macos`. `vm.cmd` and `trace.cmd` build
their own binaries with `--config=air-lane-linux`, so a wrapper call keeps the analysis of a Linux
lane build.

IDE Starter still fetches the starter JBR, and the guest never actually downloads it: both targets
expose their dependency set as runfiles and pass the generated manifests through
`intellij.build.download.preloaded.manifest`, so the downloader copies a runfile into a guest-local
cache. The lanes also pass `intellij.build.download.preloaded.only=true`, which makes the manifests
the complete inventory, so an undeclared URL fails before network access. See
`build/spec/dev-launch-downloads.spec.md`.

**Remounting is a correctness requirement, not hygiene.** A VirtioFS guest keeps a dead node for any
file the host rewrites after the mount. `stat` then still answers from the cache, while `open` fails
with `ENOENT`. A host `bazel build` rewrites exactly the outputs the next run reads. Remounting is
the only invalidation the guest offers, which is why the controller owns the mount instead of using
the boot automount. This is the filesystem's behaviour and not Apple's, and it was reproduced on a
Linux worker on 2026-08-13.

`umount` answers "target is busy" while anything in the guest still holds the mount open. The IDE
holds it, because the IDE runs from the share, and so does any in-flight run that reads share-backed
data. The daemon does not, and the next section states how that is asserted.

### Guest-local writable state

Everything a worker writes lives under `$AIR_VM_DATA`, in seven directories and no others:

| directory | holds |
| --- | --- |
| `out` | IDE Starter's whole tree, through the parity layout's one writable redirect |
| `tmp` | temporary files |
| `build-download` | the persistent build-dependency cache |
| `daemon` | the daemon's own state and each iteration's results |
| `daemon-runtime` | content-addressed stable-tier and JBR generations |
| `state` | the supervisor run roots, the SSH host-key fingerprint, and the guest agent binary itself |

`state` is where the controller's own
guest-side program lives, so a worker with no `state` is one nothing here can reach. A Linux worker
holds no checkout, no credentials and no cache, so a recycle loses nothing.

## The daemon loop

A one-shot run would pay fixed costs on every iteration: a fresh test JVM, an IDE assembly inside
it, and an IDE launch with a project open. `run` keeps those paid-for states warm in a persistent
guest process, the **UI-test daemon** (`//plugins/air/tests/integration/ui:ui_daemon`, module
`intellij.air.integrationTests.uiDaemon`). Why it is kept, now that Bazel caches the distribution it
launches, is [ADR 0104](../../../../plugins/air/docs/decisions/0104-the-daemon-caches-a-process-not-a-build.md).

The daemon is the same JVM the `ui_test` target would start, on half its classpath. The **stable**
tier is the platform, the lambda framework, IDE Starter and the daemon's own module, and it is the
JVM's app classpath. The **hot** tier is everything built from `plugins/air` except the daemon's own
module. It is withheld from the app classpath, and each iteration loads it in a child classloader,
from a guest-local content-addressed store the controller pushes into. A hot jar that reaches the
app classpath is not a degraded mode but a silent one: parent-first delegation would answer every
reload with the bytes the daemon booted on.

Bazel declares the split rather than leaving it to be inferred. `_is_hot` in
`plugins/air/tests/integration/air_ui_daemon_runtime.bzl` is "owner contains `//plugins/air/` and
not the daemon's own module". The rule emits the two tiers as a versioned JSON **runtime
descriptor**, and `//plugins/air/tests/integration/ui:ui_daemon` is that descriptor rather than a
Java launcher. Both ends assert its `schemaVersion`, so the shape is a contract between Bazel and
the controller. `parse_runtime_descriptor` in `crates/avl-wire/src/runtime.rs` validates it fail-closed,
`daemon_launch_arg_file` beside it emits the argfile, and `AirUiDaemonServer` re-checks the result against its
own classpath at boot.

Every UI lane runs through this one daemon, because its classpath is the union of both IDE-launching
targets. A lane is only a JUnit filter. Air's own `AirLaneIde`, in the daemon's stable module, then
carries the warm IDE across whole controller iterations
([ADR 0138](../../../../plugins/air/docs/decisions/0138-air-owns-its-lane-ide.md)). The IDE has no run budget a lane can reach. It
ends only when something stops it: `--fresh-ide`, a quiesce, the watchdog, daemon shutdown, or its own
crash. An IDE that ended on its own, or whose driver stopped answering, is found by the host's probe at the
next suite boundary and relaunched on its context, so the lane pays a launch and no timeout. The IDE never survives a daemon restart, because the
daemon's own JVM owns its process: [ADR 0112](../../../../plugins/air/docs/decisions/0112-the-ide-cannot-outlive-its-daemon.md)
records why a reattach was not an option either.

### Two traps of a manual `daemon start`

- Run `daemon start` in the background. A start that builds the product can take longer than the
  10-minute limit of a foreground shell call. The boot budget alone is `AIR_VM_DAEMON_BOOT_TIMEOUT`,
  1,200 s by default.
- A `daemon start` client that is killed before it returns leaves a run in the guest's slot. The host
  wrote its record before the health poll, and `daemon stop` retires that run, with or without the
  record. Until then the next `daemon start` refuses with `has active run`, and `lease release`
  refuses with `in flight`.

### Invalidation

Per `run`, the controller builds on the host and digests the stub's runfiles manifest with a
stat-keyed content cache. It then pushes only changed hot jars into the daemon's store over HTTP.
One connection carries a push of at most 8 MiB, and each further 8 MiB begun opens one more, up to four. It streams the JUnit run back as NDJSON. Three levels fall out of the digests, and each
iteration's timing line reports the one it took.

| change | detection | cost |
| --- | --- | --- |
| test code, meaning the Air test-only modules | hot-jar digest diff | a push plus a fresh classloader. The IDE is reused, and there is **no remount** |
| product inputs, meaning anything inside the prepared distribution | product stamp diff | quiesce, remount, resume, then launch the IDE again. The daemon JVM survives, and nothing is assembled |
| the daemon's parent tier, meaning platform and framework jars | stable digest diff | an automatic daemon restart |
| the container's declaration, meaning the image tag, the shares or the display | create-record diff | a recreate plus a cold daemon start; Docker only |

The three questions are asked independently, and one file may answer yes to more than one. An Air
production jar is both hot and a product input. Treating "hot" as an answer to the product question
is what would let a run report green against an IDE built from the previous commit.

The product stamp reads the `fingerprint.txt` that the dev build wrote inside the distribution's
tree artifact. It is an xxh3 over every entry's content hash, so stamping never walks a
multi-gigabyte tree. The stamp folds into the lane's `IdeStartConfig` key
(`AirFlowLaneHost.configKey`), so a relaunch is the framework's own key-change path rather than a
new mechanism.

Nothing the daemon holds open lives on the share, and `AirUiDaemonServer.requireStagedRuntime`
asserts that rather than arranging it: it refuses to serve unless `user.dir`, every stable classpath
entry and `java.home` are under the guest-local runtime root. So the daemon can outlive a remount,
and there are two remount windows:

- **With the daemon alive**, for a product change. `POST /mount/quiesce` refuses new runs and stops
  the IDE, and it answers `409 busy` if a run is in flight. The controller remounts, and
  `POST  /mount/resume` hands the run endpoint back. The timing line reports `ide remount`. On
  Docker, the controller refreshes the shares with the 2 s settle instead of a remount. The timing
  line still reads `ide remount`.
- **Between daemons**, for a stable-tier change. `start_daemon` in `crates/avl-vm/src/daemon/start.rs`
  remounts after the old daemon stops and before the new one starts. On Docker, the controller
  refreshes the shares with the 2 s settle instead of a remount.

`--fresh-ide` is cheaper than either. No host bytes changed, so it only stops the IDE.

`run` starts or restarts the daemon by itself whenever the build it just prepared does not match the
one running. The daemon holds the guest supervisor's single run slot for its whole lifetime, so
`exec` is refused until `daemon stop`. A lease release keeps that warm daemon:
[ADR 0106](decisions/0106-a-warm-daemon-survives-a-lease-release.md).

The controller reaches the daemon through the relay, never through the guest network. For each pooled
HTTP connection, the controller spawns `tart exec -i <worker> <agent> relay <port>`. On Parallels, it
spawns `prlctl exec <worker> "'<agent>' 'relay' '<port>'"`. The `relay` verb of the guest agent bridges its
stdin and stdout to `127.0.0.1:<port>` inside the guest. The daemon binds only that loopback address.
So the host needs no route to the guest and no permission for one. Each relay spawn costs about 0.65 s.
[ADR 0182](decisions/0182-the-daemon-is-reached-through-the-exec-channel.md) records the decision.

A start whose daemon never answers `/status` within `AIR_VM_DAEMON_HEALTH_TIMEOUT` (180 s) cancels the
run it launched and keeps its record, so `daemon log` still reads it. The refusal names what the last
probe saw. When no relay opened, the refusal names the worker and the loopback port. The daemon
published its state file in that case, so read `daemon log` to see why the daemon did not answer.

`daemon stop` also retires a daemon run no record names. It keeps the record, and refuses
`daemon_retire_failed`, while a run the supervisor did not finish still holds the slot.

A worker whose VM Tart no longer lists, while its `tart run` process lives on, releases its lease
without the guest check. `pool recycle <worker>` then ends that process and clones the slot again.

The daemon environment is a fourth invalidation, because a JVM cannot change its own environment.
`prepare_build` in `crates/avl-vm/src/daemon/build.rs` folds that environment into the launch digest, so a
changed value restarts the daemon.

Two refusals exist to prevent a false green: `no_tests_discovered` for a selection that matches
nothing, and `all_tests_skipped` for classes a JUnit condition ruled out, each listed with its own
reason.

The daemon publishes one `planStarted` record before the first execution. It includes the execution
count and each discovered class. The report lists each class without a later result in
`unreportedClasses`. A non-empty list produces `infrastructure_error`, and `--text` prints the list.

### What a host build during a run takes away

The guest runs the jars the iteration pushed, so a host build while a run is in flight does not corrupt the
execution. It corrupts the **attribution**. The verdict then describes bytes the checkout no longer produces,
and a reader maps it to the source in their editor. Bazel replaces a jar by rename, so nothing else notices:
the run keeps its open handles and reports green.

So each iteration re-hashes its own hot tier when the run ends and compares it with what it pushed. A jar that
moved is a verdict diagnostic, which makes the iteration an `infrastructure_error` and never green. The
diagnostic names the jars and says to re-run. `PreparedBuild::hot_jar_drift` in
`crates/avl-vm/src/daemon/build.rs` is the check, and the reason it is a
diagnostic rather than a note is the rule `crates/avl-report/src/report.rs` opens with: a run whose
subject changed under it looks exactly as authoritative as one whose subject did not.

### What a remount does not notice

`launchDigest` and `mountDigest` cover Bazel's declared outputs, not the checkout. **An edit to a
repository file that no Bazel output depends on remounts nothing.** The guest keeps answering from
the bytes it cached, and nothing reports a stale state. From the outside you see three things. The
change is in the editor, the timing line says `ide reuse`, and the guest reads the previous content.

The remedy is `daemon restart`, which remounts between the two daemons. A digest over the checkout
was rejected rather than overlooked. The checkout is shared between agents and working copies, so
such a digest would remount for every unrelated edit anybody made. A remount costs a reliable 16 s.

## The watchdog and the report

The daemon has no fixed whole-lane timeout. Its watchdog validates the execution that is active and
the gaps around executions, so another scenario does not make a lane-size constant stale. Only JUnit
lifecycle events move its deadlines; console, log, HTTP and XML activity never claim that a scenario
made progress. The skill owns the budgets and their overrides.

On expiry the daemon kills the IDE first, because the remote driver ignores interruption inside an
RMI read. It then waits for the run thread to unwind. The `/run` request can set
`wedgeGraceTimeoutSec`, which defaults to 60 s. This budget has no command-line or environment
override. If the thread remains alive, the daemon flushes partial JUnit XML and exits with code 3.
Measured twice, against a cold IDE and a warm one, the thread did not unwind either time. Expect the
exit rather than the unwind. Recovery is automatic and costs one daemon boot. The controller
reports a stream that ends mid-run as `daemon_died` (exit 70). `daemon log` still works after the
daemon is gone.

**The versioned JSON report is the contract, and JUnit XML is only the daemon's producer format.**
The controller fetches the XML through an authenticated endpoint, and it falls back to a guest pull
after daemon death. It returns the report in both the success and the error envelope. Missing,
stale, truncated, malformed, oversized or summary-mismatched producer evidence can never be green.
Agents consume the JSON report.

## What it costs

The fixed costs around a lane, measured on `air-linux-1`, each reproduced on the same worker and
day. `air-linux-1` was a Tart Linux worker, which
[ADR 0210](decisions/0210-the-docker-image-carries-node-and-the-tart-linux-worker-is-retired.md) retired, so its rows are history:

| what | cost |
| --- | --- |
| cold `daemon start`, meaning the host build, staging and boot | 188 s |
| warm `daemon restart`, meaning nothing changed and staging reused | 32 s |
| an IDE launch plus its first-suite readiness dwell | 10-12 s |
| a second run of one class against the warm IDE | 11 s |
| one run of `AgentSessionToolWindowComposerUiTest` on `air-docker-1`, after a cold daemon start | 234 s of tests for 8 scenarios, after a 37.1 s host build |
| a later run of the same class against the warm IDE on `air-docker-1` (`ide reuse`) | 286.6 s of tests for 8 scenarios, after a 75.4 s host build |
| the `ui` lane, 71 tests in 40 classes, on `air-docker-1` on the Lima engine: cold daemon, then warm daemon | 756.4 s, then 697.0 s of tests |
| the same lane on `air-linux-1`, in the next hour: cold daemon, then warm daemon | 750.4 s, then 860.1 s of tests |
| the `ui-real` lane, 4 tests in 2 classes: twice on the Lima engine, then on `air-linux-1` | 70.5 s and 70.4 s, then 84.6 s of tests |
| the first start of the Lima engine, with the cloud image in Lima's cache | 76 s |
| the first start of the Lima engine, with the cloud image download | 147.4 s, of which the download is 103 s |
| the engine recreate with the image download from the mirror, plus an image build (`pool start`) | 322 s, of which the engine start is 189 s |
| a warm stop, then a warm start, of the Lima engine | 11.1 s, then 19.8 s |
| the first start of the Lima engine on Ubuntu 26.04, with the image download from the mirror | 197 s, of which the download and its conversion are 137 s |
| in the Lima log, a warm start of the engine inside `pool start` or `run`, on Ubuntu 26.04, then on 24.04 | 7 s, then 15 s |
| a warm `pool stop`, then `pool start`, on Ubuntu 26.04 | 20.3 s, then 39.5 s |
| `shard --shards 2` of the `ui` lane on two containers of one engine | 429 s makespan, 770 s serial |

The two single-class rows on `air-docker-1` ran on OrbStack. Each is one iteration on 2026-09-29, and
all 8 scenarios passed in each. In the cold run, every trace has a video. A third iteration also
reused the IDE and passed 7 of 8. Its one failure was a driver race on a recycled IDE, not a container
problem, and the race did not occur again. The same class on `air-linux-1` took a time of the same
order on that day. The lane rows are one iteration per cell on 2026-09-30, on one host, and every
test passed in each. The Lima engine and `air-linux-1` took times of the same order, and the
measurement shows no penalty for the engine. One iteration per cell shows no spread, so this guide
does not call one of them faster. The `shard` row is one iteration on 2026-09-30, and its memory was not
measured ([ADR 0190](decisions/0190-the-docker-engine-is-the-default-worker.md)). The engine rows come from
[ADR 0189](decisions/0189-the-docker-engine-is-a-lima-vm-the-controller-owns.md). The ADR 0189 rows ran on Ubuntu
24.04. The 26.04 rows come from [ADR 0191](decisions/0191-the-engine-vm-leaves-the-usernet-path.md), on
2026-10-01, and each is one sample. The lane time on 26.04 is not measured yet, because the tree held another
session's half-finished edits. ADR 0191 names the runs that are still owed.

The **daemon** is the expensive warm state, several times the IDE it holds. So work aimed at
carrying the IDE across a restart is aimed at the smaller half. Read those rows against the
invalidation ladder. A test-only edit pays the 11 s row. An Air product edit pays about 61 s,
including its host build. Only a platform, framework or `uiDaemon` edit pays the restart.
`ide remount` in the timing line is a label rather than a duration, so one run cannot price the IDE
action.

**Warm the analysis before you take a worker**, because a cold analysis cache turns the 188 s cold
start into 799.6 s. A `run` without a receipt builds before it leases, so it warms for itself. Before
a `lease acquire`, warm by hand:

```bash
./community/tools/vm.cmd daemon warm
```

`daemon warm` builds and stamps the host half of the lane and stops there. It takes no worker and no
lease. It builds through the same `prepare_build` that a `daemon start` calls, so it cannot discard
the analysis a start would then reuse. It is a real build rather than `--nobuild`, because a
`--nobuild` canary passes against a tree whose assembler has failed. Two caveats are load-bearing:

- **Per guest configuration.** A `linux` warm does not warm `macos`, and warming the other guest
  inside one output base evicts what the first one warmed. The command reports the configuration it
  warmed.
- **Host-wide, not per worker.** One warm serves every worker of that guest, and for the same reason
  a warm that races a building `shard` or `flake` refuses (`host_build_log_unavailable`).

Class durations are very uneven. The median class is 3.0 s and the slowest is 41.3 s. That is why
`shard` refuses to split a lane by count without a measurement. Sharding two ways is 1.44x rather
than 2x, because the second worker pays its own daemon start. Twelve quiet trials of the whole lane
passed 12 of 12. The lane flake rate is 0, with a two-sided 95 % Wilson interval of [0, 0.143].
Quote the interval rather than the zero. The loaded complement is
[ADR 0110](decisions/0110-the-truncation-chain-is-a-daemon-kill-under-load.md).

A failing scenario spends its driver timeouts before it reports, so **read a slow lane as a red
lane** until the per-class numbers say otherwise. A warm full-lane iteration is about 196 s.

## Artifacts and diagnostics

Normal execution requires neither Peekaboo nor guest Screen Recording or Accessibility grants.

| what | where in the guest |
| --- | --- |
| JUnit XML, per iteration. A wedged exit flushes partial XML | `$AIR_VM_DATA/daemon/iterations/<iterationId>/test.xml` |
| `idea.log`, screenshots, `ui-hierarchy/`, `acp/`, per IDE launch | `$AIR_VM_DATA/out/ide-tests/tests/IU-LOCAL/air/<launch>/log/` |
| the same launch, as a CI publisher would collect it. **Reclaimable, so never pull from here** | `$AIR_VM_DATA/out/ide-tests/tests/teamcity-artifacts-for-publish/air/` |

Three properties of those paths are worth stating. Each IDE launch writes its own timestamped
directory, so iterations never overwrite each other, and `TEST_UNDECLARED_OUTPUTS_DIR` stays empty
for that reason. A recycle gets its own `recycle-N` directory, so list the suite's directory rather
than assuming the path. An iteration id includes the daemon boot identity, and each result directory
is reserved with an atomic create. So a restarted daemon cannot make stale XML authoritative.

`ls` aims `pull`, and both work throughout the daemon's lifetime, because neither wants the
supervisor's run slot. Both take the worker's operation lock, which `run` holds for the whole lane,
so both fail `lease_busy` (exit 75) during an iteration. The fix is to wait, not to stop the daemon
that makes the next iteration fast.

The driver's in-process Robot screenshots and the Remote Driver UI hierarchies are the evidence of
record on Linux and Parallels. On a Tart macOS worker only the hierarchies are, and ADR 0113 says
why.

A macOS worker has two VNC servers with two different auth models. Both are a diagnostic channel and
never test control. The skill's `references/macos-pool.md` says which to open and how.

## Traces

Every UI scenario leaves a **trace bundle**, whichever launcher ran it, in the manner of the Playwright
trace viewer. A bundle holds a video and a lossless picture at every instruction boundary and held check.
It also holds the Swing tree at each of those moments, the gestures, the bridge calls, the scenario's slice
of `idea.log`, and the generated program, so the instructions a failure never reached are visible. The
contract is [`community/tools/vm/spec/scenario-trace.spec.md`](../spec/scenario-trace.spec.md). The lane's side is
[`plugins/air/spec/docs/flow-trace.spec.md`](../../../../plugins/air/spec/docs/flow-trace.spec.md), and
[ADR 0155](../../../../plugins/air/docs/decisions/0155-a-ui-scenario-leaves-a-trace.md) is why the trace is shaped this way.

The recorder is `air-trace-record`, a binary that the lane JVM starts from its runfiles. The lane tells it
what happens, one line per event on its standard input. A lane the watchdog kills closes that pipe too, so
its bundle still gets its video and its manifest, marked `truncated` and naming the span that was running.
A trace is evidence and never a verdict: every capture problem becomes a record in the bundle, and a trace
that could not be pulled leaves a green run green.

| launcher | where the bundles land | who cleans up |
| --- | --- | --- |
| the daemon on a worker | `$AIR_VM_DATA/daemon/iterations/<iterationId>/air-traces` in the guest, then pulled after each test to `<runtime root>/runs/<runId>/traces/<iterationId>/<NNN>.zip` | the guest's iteration retention; the run directory on the host |
| a Bazel test, host `bt.cmd` included | `air-traces/` inside the target's `bazel-testlogs/.../test.outputs/outputs.zip` | Bazel |
| JUnit run from the IDE | `<checkout>/out/air-traces` | the recorder, which keeps the newest 20 runs |

A bundle is `<root>/<runId>/<TestClass>/<scenario>/`. A second attempt of the same scenario in one run is
`<scenario>.2`. The controller pulls each finished bundle after the test that finished it, so a failed
scenario's video is on the host while the lane goes on, and each arrival is a `traceReady` record of the run's
journal. `run`'s text verdict ends with a traces block, one line for each scenario with the failed ones first,
or with `traces: not pulled: <reason>`. The JSON report names each zip and its bundles in `traceArchives`, and
a failed pull in `tracesError`.

**Opening them.** One server shows every bundle and every `vm.cmd` run on this machine, live, in the Air docs
site:

```bash
./community/tools/trace.cmd serve           # in the foreground: http://127.0.0.1:7357/air/runs
./community/tools/trace.cmd serve --detach  # one server for this machine, in the background
```

It looks in `out/air-traces`, in the Air lanes' `bazel-testlogs`, in every pulled zip, and in the journal of
each run under `<runtime root>/runs`. It serves zips by byte range without unpacking them, and it tells the page
when a run appears or a run's journal grows. It listens on 127.0.0.1 only. `--root DIR` adds a place to look.
`pnpm dev` in `plugins/air/docs` proxies `/__air` to it, so the viewer under development reads the same runs.

When the site in `out/air-site` is missing, or older than `plugins/air/docs/src`, the server builds it once in
the background: `pnpm install --frozen-lockfile` when `plugins/air/docs` has no `node_modules`, then
`pnpm build`. The page says that the site is building, or why the build failed. The node and the pnpm are the
ones `//:MODULE.bazel` pins for the site (`//build:air_docs_node`, `//build:air_docs_pnpm`), which the server asks
Bazel for in the checkout `BUILD_WORKSPACE_DIRECTORY` names; Bazel fetches them the first time, and a regular
build never does. Nothing has to be on the PATH. `AIR_TRACE_NODE` and `AIR_TRACE_PNPM`, set together, name the two
directly; a server started outside a checkout uses the `pnpm` on its PATH.

`--detach` returns when a server answers, and it starts one when none does. That server writes its output to
`<runtime root>/viewer/serve.log`, keeps its process id in `serve.pid` beside it, and stops after 30 minutes
with no request and no open page (`--idle-exit`). The controller starts the same server through
`trace.cmd serve` for a run in a terminal, and does not wait for it.

Each `vm.cmd` run has a page, `/air/runs/run?id=<runId>`. It shows the phases, the tests as they start and end,
a card for each scenario trace as it reaches the host, and at the end the verdict and the report paths. The
Runs page lists the runs above the loose bundles.

A trace opens the way Playwright's trace viewer shows a test: the steps on the left, with the program's
instructions a failure never reached greyed in place; the Before and After picture of the selected instruction,
with the click drawn over it, and the video; the Swing tree of that moment, which a hover draws on the picture;
and the check's expectation, its failure, the bridge calls, the gestures and the `idea.log` lines of that span.
A failed run opens with its verdict: the failing expectation, its Before, After and Diff pictures, and where it
parts from the last green run. The timeline is a flame chart with a brush. The Diff tab lays the two pictures
over each other. **History** lists the runs of the scenario on this machine with Allure's flaky, regressed and
fixed marks. **Compare with…** lines two runs of one scenario up by step and marks where they part. **Share**
copies a link to the moment, a Markdown digest for an issue or an agent, and hands the timeline to Perfetto.
Without the server, drop a zip or a bundle folder on the Runs page, a Bazel `test.outputs` folder included: it is
read in the browser.

While the server runs, the recorded run also shows where the docs tell the story. A flow walkthrough plays the
video of the active step. A feature section shows the newest run of its flows, and a spec page lists the runs of the
scenarios that reach its targets. Home shows a gallery of the newest runs. The static site shows none of them,
because only the server reads the disk.

**Planning new ones.** `trace.cmd plan` takes whatever is in hand. It answers the scenarios it covers, the
bundles the input itself names (a report's traces zip), the bundles of those scenarios already on this machine,
and the commands that record new ones. It looks for bundles where `trace.cmd serve` looks, and `--root DIR` adds
a place. The planner on the Runs page gives the same answer:

```bash
./community/tools/trace.cmd plan plugins/air/docs/flows/flow-rename-session.txt
./community/tools/trace.cmd plan out/bazel-testlogs/plugins/air/tests/integration/ui/ui_test/test.xml
./community/tools/trace.cmd plan plugins/air/frontend/session/toolwindow/src/ui/AgentAttachedProjects.kt
```

An input may also be a bundle, a zip, a flow profile, a `vm.cmd` report piped in as `-`, a flow id, or a
check id. The VM sequence comes first. The host `bt.cmd` run is offered with the warning that it takes your
screen. The planner prints commands and never starts a run, because a run costs a lease or your screen.

**What each guest captures.** The Linux guest records X11 directly: real pixels, native popups, and an H.264 video.
The recorder encodes that video itself, so the guest needs no package for it. A macOS host and the macOS guests take
stills from the IDE's own paint route. A direct lane on your own machine never reads your screen unasked, because it
shows everything else you have open and macOS would ask for Screen Recording permission in the middle of a scenario.
Pass `--jvmopt=-Dair.flow.trace.screen=on`, or set `AIR_TRACE_SCREEN=on`, to let it: a Mac with `ffmpeg` on the path
then records a video too, and a Linux host records X11. Every bundle's manifest names its capture source and, when a
better one was missing, why.

**What the IDE did.** A bundle also holds the OpenTelemetry spans that the IDE ended in the scenario, such as
each `air.session.log.read`. The viewer shows each one as an `ide` row under the step it started in. The spans are
evidence only: a check that counts them reads the IDE through the bridge, not the trace.

**Why these formats.** Each is one that current Chrome and Safari decode natively, so the viewer ships no
decoder:

- Spans and events are OTLP JSON Lines, so generic OpenTelemetry tools read a bundle as it is.
- Stills are lossless WebP, pixel-exact UI evidence with crisp text, from the WebP encoder that the recorder carries.
- Video is H.264 in fragmented MP4, the one codec with hardware decode in both browsers on every Mac. The
  fragmented container survives a killed encoder, and `video.index.json` makes every seek exact.
- A zip stores its media uncompressed, so a server answers byte ranges straight out of it.

## Reading a refusal

A failed guest command is reported against the program that actually failed, which is never the one
the argv starts with. `tart exec` lands as root, so the controller re-targets every privileged guest
command with `sudo -H -u admin`. An Aqua command puts `launchctl asuser <uid>` in front of that. A
refusal therefore walks exactly the wrapper shapes this controller builds, and it names the first
token that is not one of them. The remaining argv is bounded to 240 bytes.

The controller's own guest verbs go further, because it knows what it asked for. A failing verb
names the agent, the verb and the worker, and a structured failure envelope becomes the agent's own
code and message. Exit 64 earns one appended sentence, which names `EX_USAGE` and an agent older
than this controller.

**Guest stdout and stderr stay withheld from every refusal**, because a guest argv can carry the
lane's UI-test bridge token. The contract is stated in `crates/avl-vm/src/lane/observe.rs`. It has two narrow
exceptions, and both go through one helper, so neither can widen alone. The first is the agent's own
usage line, which its argument parsing prints before any guest work ran. The second is a first
stderr line beginning with `sudo:`, which sudo writes before it executes anything, so
`sudo: a password is required` arrives intact. A line beginning `sudoku:` is not it, and a `sudo:`
line behind another line is not it either. Do not harmonize the exception away by quoting more.
The log of a Docker container is not guest-command output, because only pid 1 and its children write
it, so `container_exited` quotes its last lines.

A phase report withholds by the same rule. Each phase names its most expensive single guest call as
a skeleton rather than as the argv: an element that carries a value, meaning one with `=` or `:` or
longer than 48 bytes, is withheld, and the rendering is capped at six elements.

### The guest agent's verbs

The controller's guest-side program is `vm-guest-agent`, one statically linked binary. The controller
installs it into `$AIR_VM_DATA/state` and invokes it over the hypervisor's exec channel. It owns
every guest-side verb:

| verbs | concern |
| --- | --- |
| `start`, `status`, `active`, `log`, `cancel` | the run supervisor |
| `stage`, `stage-check`, `launch-prep`, `gc` | the runtime stager, the probe that answers whether a generation is already staged, and one daemon launch's preparation |
| `contract` | what the agent declares about itself, its own digest included. An install that would push a binary the guest already has is skipped |
| `validate-guest` | the Linux boot |
| `provision-image`, `validate-image` | the macOS image pipeline |
| `trace-pack-ready` | zips the scenario traces that finished since the last pull, with the code of `air-trace pack` |
| `relay` | bridges its standard input and output to a loopback port inside the guest, so the controller reaches the UI daemon without the guest's network address |
| `runfiles-tree` | builds the runfiles tree of a host MANIFEST on the guest's own disk. A Windows host writes the MANIFEST and no tree |
| `read-file` | writes one guest file on standard output unchanged, and names its size and SHA-256 on standard error. A pull on Tart and Docker reads the file through it. The controller refuses `pull_digest_mismatch` when the bytes that arrived are not the ones it named. A pull on Parallels is a `base64` of the file |
| `supervise` | internal, the agent's own detached mode |

Each verb's name is declared once, as `avl_wire::verb::AgentVerb`, and the agent and the controller both spell
it from there. The list exists so that a log line or a refusal that names a verb is recognizable. It is not an
interface to drive. The controller sends the verbs, and an operator does not. An agent invoked by hand
is how a run slot or a staged generation stops matching what the host believes.

## What is intentionally absent

- **No source copying and no guest build.** No Git bundles, no snapshots, no guest checkout, no guest
  Bazel, no guest netrc, no shared Bazel disk cache. The guest reads the host's repository and Bazel
  outputs through read-only shares and writes only to guest-local storage. A run tests exactly what
  `bazel build` on the host sees, and a release cleans nothing.
- **No second execution engine.** Every lane goes through the daemon. A lane the daemon cannot
  resolve has no runner. Raw Bazel passthrough is rejected because there is no guest Bazel to receive
  it.
- **No GUI automation on Tart.** Worker start enforces fail-closed, grant-free TCC admission. Peekaboo
  needs Accessibility and Screen Recording grants baked into the golden image, which would fail that
  admission by construction. Robot screenshots and Remote Driver UI hierarchies are Tart's test
  artifacts. The host-side VNC framebuffer is diagnostics only.
- **No registry publish.** The reference in `community/tools/vm/provision/versions.env` is inert metadata. A denylist
  audit is not proof against an unknown path, so a publication needs a separate explicit review of
  the complete installed-file inventory.

The pool is a trusted pool for callers under one host Unix account, not a tenant-isolation boundary.
A lease serializes controller operations. It does not make persistent guest state safe for mutually
untrusted workloads.

## Verifying the controller

The controller's whole verification, end to end. It is the hermetic suite, the same unit tests
under cargo's own feature resolution, the reference gates, and one live command:

```bash
./bazel.cmd test @community//tools/vm/... //plugins/air/tests/integration/vm-contract/... @community//tools/bt/...
./bazel.cmd build @community//tools/vm:vm-guest-agent-linux-arm64 @community//tools/vm:air-trace-record-linux-arm64
(cd community/tools/vm && cargo test)
./community/tools/bt.cmd AirSpecReferencesTest
(cd plugins/air/docs && pnpm run check)
./community/tools/vm.cmd status
```

Bazel builds with the toolchain version that `community/tools/vm/rust-toolchain` pins, and a local `cargo test`
runs the same unit tests for the host. On a Mac that leaves out the Linux-only code, which the `manual` cross labels
of the second line compile. The clippy tests of the controller, `:clippy-linux-arm64` among them, run only from
`community/`, with `./bazel.cmd test //tools/vm/...` there. The crate map is
[`community/tools/vm/README.md`](../../../../community/tools/vm/README.md). The
last line is the one no hermetic test covers: `vm.cmd status` builds the shipping controller and
reaches a hypervisor, so it fails when either the Bazel target or the wrapper is broken. **The first
line also runs the tests of BT**, through `@community//tools/bt/...`. The clippy tests of BT run only
from `community/`, with `./bazel.cmd test //tools/bt/...` there, because the clippy aspect skips a
target of an external repository. BT is the community workspace `community/tools/bt`, not a part of the
controller ([ADR 0070](../../../../plugins/air/docs/decisions/0070-bt-core-lives-in-community.md)). The controller is the community workspace
`community/tools/vm` beside it ([ADR 0209](decisions/0209-the-ui-lane-workspace-lives-in-community.md)). The
tests that read files of the ultimate root are in `//plugins/air/tests/integration/vm-contract`.

`status` is a report, and it is almost write-free. It resolves the two host paths a parity verdict
is measured against. That resolution creates the configured Bazel output user root if it is absent.
One directory creation on the host is the whole of it: no worker is started, stopped, cloned or provisioned.
The refusal is a field rather than an exit, `hostPathsError`, and every row's parity verdict is then
`null`.

## Parity gate

The controller no longer gates the daemon to one backend. `run` and `daemon` work on all three
workers, because the guest model behind them is one model.

| # | criterion | verdict |
| --- | --- | --- |
| 1 | a worker boots, provisions and reports ready | **Verified** on both pools. A macOS worker also reports console login, TCC admission and a unique SSH host key. A Docker worker has no field for those, and it is never verified by them |
| 2 | the lane is green on a macOS worker | **Not met, and understood.** 8 of 24 classes pass, and not one of the 16 failures reproduces on Linux. See [ADR 0113](../../../../plugins/air/docs/decisions/0113-the-macos-lane-is-red-on-window-activation.md) and [IJAI-1228](https://youtrack.jetbrains.com/issue/IJAI-1228) |
| 3 | two lanes run concurrently | **Open behind criterion 2.** Guest-side work does go in parallel, and every host-side Bazel call queues. Lane truncation under load is [ADR 0110](decisions/0110-the-truncation-chain-is-a-daemon-kill-under-load.md) |
| 4 | a product edit invalidates correctly | **Met.** The stamp moved, the shares were remounted, the IDE relaunched, and the daemon JVM survived it |
| 5 | per-class time is comparable to the default runner | **Met.** A macOS worker is slower around a run, not per class. ADR 0113 has the figures |
| 6 | the controller's own suite passes | **Verified.** Run it with the commands under [Verifying the controller](#verifying-the-controller) |

Controller-shaped open work that is not a gate criterion lives in
[`ui-lane-controller-todo.md`](ui-lane-controller-todo.md).

This is test infrastructure only. It does not change Air product behaviour, and it needs no Air
behaviour-spec update.
