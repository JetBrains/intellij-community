# Running UI Tests in a VM

`BT --lane ui`, `--lane ui-real` and `--lane gui-chat` launch a real IDE with `InputEventsRobot`. That robot posts
component-relative events through the IDE event queue, and it never moves the physical pointer. Every lane
document selects that robot. A UI lane is run and judged in a VM, and the controller's default is a Linux container
on a Docker engine. On a Mac of macOS 26 or newer, on Apple
silicon, that engine is Apple `container`. The VM isolates the host session, and it gives a failure a real screenshot. Never move a run onto the host silently.

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
in [Dev-build architecture](../../../../build/docs/guides/dev-build-architecture.md). The guest consumes a
self-contained Bazel distribution, and it needs no checkout of its own. Nothing caches a guest run,
so an iteration runs every suite it selects again. On 2026-09-30 the `ui` lane was 71 tests in 40
classes, and it took 750 s of test time on a Tart Linux worker with a cold daemon. Ask `vm.cmd suites <paths>` what your change reaches, then run `run --changed <paths>`.
A Kotlin file of the test bridge reaches only the suites whose requests reach the declarations that the change
changed, against the commit where `HEAD` meets its upstream. `suites --base <rev>` and `run --changed --base <rev>` compare with another revision.
Only a bridge declaration that every suite reaches, such as the state reset or the HTTP service, reaches the whole lane
set. On 2026-10-27 one changed bridge function selected 86 suites in three lanes before, and 4 suites after. To
test one flow, run `vm.cmd run flow-rename-session`: it selects every generated suite that tells or
walks the flow. `vm.cmd run rename-session` selects one suite by its id. A suite is the smallest unit,
because its scenarios share one managed-agent chain
([ADR 0157](decisions/0157-a-flow-is-a-test-selector.md)).

A `run` without `--lease-file` builds on the host, leases one worker, and releases it on every exit,
so one line is a whole run. Without `--lane`, it runs one iteration for each lane the answer reaches
([ADR 0159](../../../../plugins/air/docs/decisions/0159-one-run-takes-every-lane-it-reaches.md)). A receipt from `lease acquire`
keeps one worker across several iterations, which is the warm inner loop. An interrupt of a self-leased run keeps the lease, because
the iteration can still run on the guest, and the refusal names the receipt that frees it.

## The four guests

All four run tests the same way. The guest executes Bazel outputs built on the host, against a warm
IDE that the lane daemon holds open. They differ in what kind of machine they are, and in what guest
runs in it.

| | Docker (default) | Tart macOS | Parallels | container-linux |
| --- | --- | --- | --- | --- |
| machine | a container on Apple `container`, or on the engine of the host environment, which `DOCKER_BIN` or `DOCKER_HOST` can name | a Tart VM | one Parallels VM | the container of the `testing-ui` skill, on Apple `container`, rootless Podman or `wslc` |
| image | a Dockerfile from a digest-pinned `ubuntu:26.04`, pulled from the JetBrains registry by its content tag, built when the pull fails | a sealed Packer golden | one pre-existing VM | the skill's image, which its `start` builds |
| session | `Xvfb :88` and fluxbox, from the image's entrypoint | Aqua | Aqua | the skill's `Xvnc :1`, with noVNC |
| root disk and memory | on Apple `container` a VM of its own, 8 GiB; on the engine of the host environment the engine's, shared by every container | 120 GB, 32 GiB | the VM's own | the container's disk, 6 GiB |
| `pool stop` | stops the container and never an engine, keeps nothing warm; on Apple `container` an unleased worker also stops by itself an hour after its release | `tart suspend`, keeps the daemon and the IDE | suspends | removes the container and its guest-local state with it, keeps nothing warm |
| Robot screenshot | a real frame | black, see ADR 0113 | a real frame | a real frame |
| Peekaboo | no | no | yes | no |
| provenance | the base digest and the image tag | a seal receipt per worker | none | none |
| concurrent lanes | one per container | one per worker | one, sequential | one per checkout |

Docker is the default on every host
([ADR 0190](decisions/0190-the-docker-engine-is-the-default-worker.md)), and it is the one Linux worker
([the Docker image carries Node, and the Tart Linux worker is retired](decisions/2026-10-08-the-docker-image-carries-node-and-the-tart-linux-worker-is-retired.md)). A red run
there is a verdict. `java.awt.Robot` captures a real frame there, so a failure arrives with a picture. On a Mac of
macOS 26 or newer, on Apple silicon, it needs no installation. One engine holds two lanes. `--backend container-linux` runs the same Linux guest profile
in the container of the `testing-ui` skill, on every host of the skill. The skill's noVNC is the live view
([the lane runs in the testing-ui container](decisions/2026-10-04-the-lane-runs-in-the-testing-ui-container.md)), and
[the container-linux backend becomes the default worker](decisions/2026-10-09-the-container-linux-backend-becomes-the-default-worker.md) decides that it becomes the
default worker with a later switch commit; until then Docker is the default. A macOS guest preserves JCEF,
Terminal, native-menu, WindowServer and Aqua behaviour, and that is where coverage about those things belongs.
Select one with
`--backend tart` or `--backend parallels`. A lease receipt records the selection, so a receipt-bearing command
infers it and rejects a conflict.

**The Tart macOS lane is known red.** At one tree it was 23 of 23 on Linux and 8 of 24 on macOS, and
every macOS failure is macOS-only. The mechanism is window activation:
[ADR 0113](../../../../plugins/air/docs/decisions/0113-the-macos-lane-is-red-on-window-activation.md) and
[IJAI-1228](https://youtrack.jetbrains.com/issue/IJAI-1228) own it. Ask for that backend explicitly,
and do not read a red run there as a verdict about the product.

A lease takes the warmest free slot ([a lease prefers the warm slot](decisions/2026-10-10-a-lease-prefers-the-warm-slot.md)). The first choice
is a running slot whose daemon record names the launch digest of the build that asks. Then come a running slot, a
stopped slot and a slot with no machine. Within one class the slot order of the pool stays. The probes start nothing: a
Docker engine that is down, Parallels and `container-linux` give no running state, and every slot then keeps its
place. `run`, `shard` and `flake` build first, so they ask with their launch digest. `lease acquire` has no build,
so it ranks from the running state alone. The reply names the class in `slotReason`.

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
([the Docker image carries Node, and the Tart Linux worker is retired](decisions/2026-10-08-the-docker-image-carries-node-and-the-tart-linux-worker-is-retired.md)).
Everything above still applies: one read-only share, the parity layout, the exec channel and a daemon with a
warm IDE. On the `container-linux` backend the Bazel share is a read-only mount at its host path, so the
guest has no parity layout. Four macOS
obligations have no counterpart here, so the controller does not put them to a Linux guest. They are TCC admission, the console-login wait, the APFS storage initializer and sealed-golden
provenance. A Docker row of `status` has no field for any of them.

A Linux guest has no seat until something starts an X server. The entrypoint of the image starts
`Xvfb :88` and `fluxbox`, and it waits for the display to answer. The IDE gets `DISPLAY=:88` from the
environment of the guest agent. The agent's launch of the IDE refuses `ide_display_missing` on a Linux guest when
that environment has no `DISPLAY`. So every IDE of a lane reuses one display. A server image also carries none of the
shared libraries the IDE's own native code links against. `GUEST_PACKAGES` names them, and their absence is quiet.

So each boot ends with the guest proving itself. `validate-guest` refuses the boot unless three things
hold. The C library is glibc, the display answers, and a window manager is registered on it. Each refusal
carries its own code and sentence.
[ADR 0108](decisions/0108-the-guest-half-of-the-image-pipeline-is-go.md) is why.

The lane daemon proves the shared objects at its own boot. It runs `ldd` over each shared object of its
distribution. A library that the guest does not have refuses the boot of the lane daemon with
`linux_shared_object_unresolved`.

The image carries Node 24.19.0 at `/usr/local/bin/node`, and `AIR_VM_NODE` defaults to that path. The
image tag pins the Node, so a boot runs no Node check ([the Docker image carries Node, and the Tart Linux worker is retired](decisions/2026-10-08-the-docker-image-carries-node-and-the-tart-linux-worker-is-retired.md)). Only `ui-live` uses the guest Node.
Its npm-installed registry ACP agents and the Claude CLI run on it, through `AIR_VM_NODE` and `NODE_BIN`
in the daemon environment.

A boot installs no agent CLI. Pi and Codex both come from declared Bazel runtimes under
`plugins/air/tests/tools`. A runtime holds the pinned version and its locked npm dependencies. It also
holds Node when the CLI is a Node program.
The daemon receives these inputs through its runfiles. The IDE receives one launcher directory before
other PATH entries: the `bin` directory of its context.
Missing inputs or a different version fail before the IDE starts. Host overrides cannot replace a
runtime. VM preparation installs and probes no agent CLI, and existing images need no rebuild.
[ADR 0137](../../../../plugins/air/docs/decisions/0137-agent-clis-are-declared-bazel-test-runtimes.md) is why.

## The Docker guest

The default backend, `docker`, runs the Linux guest in a container on a Docker engine.
[ADR 0183](decisions/0183-a-linux-worker-may-be-a-container.md) made it a backend,
[ADR 0190](decisions/0190-the-docker-engine-is-the-default-worker.md) made it the default, and
[the Docker image carries Node, and the Tart Linux worker is retired](decisions/2026-10-08-the-docker-image-carries-node-and-the-tart-linux-worker-is-retired.md) made it the one Linux worker. The guest is the Linux guest of the section above. This section describes the
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

The share is a bind mount at `/mnt/AirVmShares/<share-name>`, read-only, which is the path the
parity layout expects. **There is no remount on Docker.** A spike on 2026-09-29 replaced a file by rename
on the host, as Bazel does. In the container, `cat` returned the new bytes at once, and `open` never
failed with `ENOENT`. Only `stat` was late: it reported the old size at +0 s and the new size from +1 s
on. On Apple `container`, on 2026-10-09, `open` failed with `ENOENT` after the rename when the guest had
read the path just before it. That dead node expired by itself after 34 ms to 58 ms. No remount was
needed ([Apple container is a second engine of the Docker backend](decisions/2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md)).
So a remount on Docker is a 2 s settle for the virtiofs dead nodes and the attribute cache, on both engines.
A lane run on Docker goes through
the same daemon, and the remount step of the daemon becomes the 2 s settle. The controller recreates a
container when its create arguments change. The record of those arguments covers the share, the image
tag, the display and `AIR_VM_SCREEN`. The arguments name no checkout, so every checkout of the host uses the
same container, and a switch to another checkout recreates nothing
([the guest reads no checkout](decisions/2026-10-10-the-guest-reads-no-checkout.md)). A warm iteration compares the record too, before it reuses the
daemon. On a difference it recreates the container under its own lease and starts the daemon cold. A missing record also gives a recreate, and so does an engine
container id that differs from the recorded id. `pool start <worker>` and `pool init` refuse with
`worker_leased` when the stale container runs and another holder leases it. `pool start all` and
`pool stop all` change the other workers and name the refused worker in `refused`. A stopped stale container
is recreated for any caller.

The guest-local state is the named volume `air-<worker>-data` at `/home/admin/WorkerData`, so a
recreate keeps it. `pool stop` stops the container and never suspends. `pool recycle`
removes the container and its volume. `pool gc` removes stopped, unleased containers and keeps their
volumes. `vnc`, `peekaboo` and `image validate|build` have no meaning here, and they refuse with
`unsupported_backend_operation` (exit 2).

One rule decides the engine, and no flag selects it
([the Lima engine is retired](decisions/2026-10-13-the-lima-engine-is-retired.md)). When `DOCKER_BIN` or `DOCKER_HOST`
names an engine, the controller runs that CLI against that engine, on every host. When neither variable is set, a
macOS host of macOS 26 or newer, on Apple silicon, runs Apple `container`. Every other Mac keeps the engine of its
host environment: an older macOS, an Intel Mac, and a Mac whose version cannot be read. On such a Mac, the Docker CLI
asks only `/var/run/docker.sock`. When no engine answers on that socket, a command that needs the engine refuses
`docker_missing`. Its remedy names two ways out. Name a Docker engine with `DOCKER_BIN` or `DOCKER_HOST`, or run the
lane on a Mac with macOS 26 or newer on Apple silicon. `suites` still works, and `status` refuses `docker_missing`
with the same remedy. A Linux or a Windows host keeps the engine it has. The controller reads the version from the
host at each command, and no setting overrides it. So a Mac of macOS 26 or newer on Apple silicon installs nothing, as
for Tart. `status` names the engine in its first line, `engine=host` or `engine=container`, and it never starts the
engine. OrbStack is the fast path, and the first two lane rows of the cost table ran on it. A Mac with OrbStack keeps
it with one variable, and an older Mac names its engine in the same way:

```bash
export DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock   # or DOCKER_BIN=<OrbStack's docker>
```

On a Mac, an unset `DOCKER_BIN` is a Docker CLI that Bazel pins. That CLI has no `buildx`, so the controller also pins
buildx. With the pinned CLI, the controller passes `DOCKER_CONFIG=<runtime root>/docker-config` and names the pinned
plugin in its `config.json`. So the pinned CLI reads no context of `~/.docker`. The controller keeps every other key
of that file, so a `docker login` made there survives. A CLI that `DOCKER_BIN` names keeps its own configuration. A
publish with the pinned CLI needs a login into that configuration first:

```bash
DOCKER_CONFIG="<runtime root>/docker-config" docker login <registry>
```

A Lima VM of an earlier controller stays on disk, and the controller does not know it. A running one keeps its
memory. The pinned `limactl` is gone, so stop and delete the VM with a `limactl` of your own. When the VM does not
run, remove `~/.local/state/JetBrains/air-vm-ui-tests/lima` instead:

```bash
LIMA_HOME=~/.local/state/JetBrains/air-vm-ui-tests/lima limactl delete --force air-docker-engine
```

On Apple `container` ([Apple container is a second engine of the Docker backend](decisions/2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md)),
each container is a VM of its own. The controller runs the Apple CLI that Bazel pins in
`community/tools/vm/container.MODULE.bazel`, or the one that `CONTAINER_BIN` names. It runs no Docker CLI and no buildx.

```bash
env -u DOCKER_HOST ./community/tools/vm.cmd run AgentSessionToolWindowComposerUiTest
```

- **One server per login session.** The controller uses the server that runs, on the default data root of the tool.
  It never stops the server, because a `system stop` stops every container of the session, also the containers of
  the `testing-ui` skill. A server that is down, after a reboot, is started under the image lock. The first start
  downloads the kernel, about 700 MB, and its log is `<runtime root>/container-system.log`.
- **A foreign server.** A server of another install with another major version is refused
  `container_engine_foreign`. A server of another install with the same major version is used, with a note.
- **The nameserver.** The DNS proxy of the engine does not answer on every host. So each build and each container
  gets `--dns`: `AIR_VM_DNS`, else the first nameserver of the host outside the loopback, else `1.1.1.1`. A guest
  that needs a name that only a VPN resolver knows needs `AIR_VM_DNS`.
- **The image.** This engine does not pull from the registry, because the registry answers its anonymous layer
  requests with `406 Not Acceptable`. It downloads the image archive
  `<AIR_VM_IMAGE_MIRROR>/air-ui-worker/<tag digest>-linux-arm64.tar` from the JetBrains file mirror and loads it. It
  builds when the mirror does not hold the tag. `AIR_VM_DOCKER_PUSH=1` with `AIR_VM_IMAGE_MIRROR_TOKEN` uploads the
  archive of the host platform after the build. This engine publishes no other platform.
- **The sizes.** `AIR_VM_MEMORY_MB` is the memory of one worker, 8192 MiB by default, and `AIR_VM_CPU` its CPUs. A
  running worker returns no memory to the host until it stops. Each build gives the builder the CPUs of a worker and
  4 GiB, and the builder stops after the build.
- **The pool size.** The workers get at most a quarter of the host memory, from 2 to 16 slots. A host of 128 GiB with
  workers of 8 GiB has 4 slots, and a host of 64 GiB has 2. `AIR_VM_MAX_WORKERS` sets the count
  ([the Apple container pool follows the host memory, and the builder stops after a build](decisions/2026-10-10-the-container-pool-follows-the-host-memory-and-the-builder-stops.md)).
- **The idle stop.** A release keeps the warm daemon for an hour. Then the unleased worker stops, and it gives its
  memory back. The next run starts it again with a cold daemon, and the volume keeps the guest-local state.
  `AIR_VM_IDLE_STOP` sets the grace in seconds, `off` keeps the worker running, and `0` stops it at the release
  ([an idle Apple container worker stops itself](decisions/2026-10-10-an-idle-container-worker-stops-itself.md)).
- **The pool commands.** `pool stop` stops the containers only. `pool recycle all` deletes the builder too. `status`
  prints `engine=container`, whether the server runs, the pool size with its reason, and the idle stop deadline of
  each worker. It never starts the server.
- **A boot that fails.** The engine keeps no exit code. A container that stops while it starts is refused
  `container_exited`, with its log and its boot log, which holds the code.

A shell that exports `DOCKER_HOST`, for example for OrbStack, keeps that engine. Unset it for the run, as the
example does.

The live lane `ui-live` runs on the Docker guest too, but only when a caller names it, because each of its scenarios
spends a billed turn ([ADR 0200](../../../../plugins/air/docs/decisions/0200-the-live-lane-also-runs-in-a-linux-guest-with-a-per-run-central-login.md)).
Its lane table entry has no catalog lane, so no flow, suite or `--changed` reaches it, and `shard` and `flake` refuse it
with `explicit_only_lane`. The run hands the guest a per-run Central login as a run secret file:

```bash
central login export | ./community/tools/vm.cmd run --lane ui-live --test-env AIR_LIVE_CENTRAL_LOGIN=@-
```

The lane daemon imports that login into the guest account, starts the Central proxy and wires the agents with
`central add`. It tears all of it down at the end of the iteration, before the run reports its verdict. A run without
the login input is refused with `lane_live_input` before any build or lease, and so is a run on a macOS guest.
`lane-run` on a host refuses the lane with the same code, so the lane has no host run. A run that holds the live lane
builds the descriptor `ui_daemon_linux_guest_live`, which adds the pinned Linux CLIs of Claude
Code, Junie and Central. A build of another lane fetches none of them, and no build needs an option of its own. The
skill's live-lane reference has the procedure.

## Windows

A Windows PC runs the controller natively, on x86_64 and on arm64
([ADR 0186](decisions/0186-a-windows-host-runs-the-controller-natively.md)). It drives the Docker and the
container-linux backends.
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

- **The share.** `GuestPaths` (`avl-host-sys/src/paths.rs`) maps the host root of the Bazel share to a guest root.
  On a Unix host that is the root itself, and on Windows the drive form, `/c/ProgramData/...` for `C:\ProgramData\...`. A Windows
  prefix matches without case, because Windows Bazel writes its output root in lower case. A host path outside the
  share is refused as `guest_path_unmapped`, and so is a path of the checkout, because no share holds it. Docker binds
  the share and the data volume with `--mount`, which refuses a source path with `,`, `"` or `=` as
  `unsafe_share_path`. The parity receipt records the guest root too (`guestBazelUserRoot`), so a worker that a Unix
  controller provisioned provisions again once.
- **The runfiles.** Windows Bazel writes the runfiles MANIFEST of a test and no runfiles tree. `HostRunfiles`
  decides from the disk where the host reads a runfile: a `<descriptor>.runfiles` tree as it is, and otherwise
  `<descriptor>.runfiles_manifest`, then `<descriptor>.runfiles/MANIFEST`. A symlink runfile's line holds the link's
  own text, relative to its directory; the host follows it through the MANIFEST to the file, and the guest links it
  as it is. On a Windows host every absolute target is resolved through its junctions before the guest sees it,
  because Bazel's external repositories there are junctions into the repository cache, which the guest's mount
  cannot read. A package directory of a `node_modules` store is copied into the tree rather than linked. Node
  resolves an import from the real path of the importing file, and the dependency links beside a package are
  junctions on the mount. The guest builds its own tree on every host; [How a run reaches the
  guest](#how-a-run-reaches-the-guest) states how.

5. `community\tools\vm.cmd --backend container-linux run <suite>` runs the lane in the testing-ui container through
   `wslc`, the skill's Windows runtime. The controller runs the batch half of `container.cmd`, and the Bazel root is
   mounted read-only at its drive-form path. The checkout is not mounted. The batch half prints the checkout in its
   `list` line as the shell spelled it, so the controller matches that line without case.

The `container-linux` backend has passed the `ui` lane of `flow-new-session` on a Windows 10 PC with WSL 2.9.13:
9 tests, cold in 283 s with a daemon start of 84 s, warm in 134 s
([the lane runs in the testing-ui container](decisions/2026-10-04-the-lane-runs-in-the-testing-ui-container.md)). That run took the MANIFEST branch through
a real daemon start. No `vm.cmd run` has passed on a Windows PC with a Docker engine yet, so `--mount source=C:\…`
on Docker Desktop and the MANIFEST name that the runtime descriptor gets there are not confirmed, and the cost table
below has no Docker row for Windows. The 2 s share settle of ADR 0183 is a VirtioFS measurement on OrbStack, and a
Windows engine needs its own. A bind mount of a Windows path crosses from Windows into the engine's Linux VM, and
the guest reads the IDE distribution and every test jar through it. That cost is not measured yet either.

On a PC with WSL2, the controller can also run in a WSL2 Ubuntu distribution, as a Linux host. Clone the repository
inside the distribution's own filesystem, not under `/mnt/c`, and run
`./community/tools/vm.cmd --backend docker` there. The Linux defaults apply: the Bazel output root is
`~/.cache/JetBrains/MonorepoBazel`, and the runtime root is `~/.local/state/JetBrains/air-vm-ui-tests`.
A Windows VM in Parallels on Apple silicon has no nested virtualization and cannot run WSL2, which is why the native
route exists. No lane time is measured for this route.

## How a run reaches the guest

The runtime descriptor of the lanes names everything a run needs, and its runfiles carry it. The lane test of each
lane, such as `//plugins/air/tests/integration/ui:ui_lane_test`, runs the descriptor
`//plugins/air/tests/integration/ui:ui_daemon` on the host, through the `lane-run` verb of the guest agent. The IDE arrives as an already-assembled distribution: `//plugins/air/tests/integration/ide:idea_air_lane_dist_linux` on
the default guest, and `//plugins/air/tests/integration/ide:idea_air_lane_dist` on a macOS one. The field `devDist.config`
of the descriptor names the file of that distribution's `_ide_config` companion target, not the distribution itself.
The field `ide.flagsFile` names the IDE flags file. The guest agent reads both from these fields. Of the static flags,
it reads only `-Dair.trace.recorder`, `-Dair.flow.trace=off`, `-Dair.flow.trace.screen=on` and
`-Dair.test.<name>.runtime`. The IDE runs on `jbr/` of the distribution, the production JBR, and gets JCEF from the
JCEF plugin of the distribution.

`run` resolves the selector with bt's resolution logic. It runs `bazel build` on the host against
its warm cache, then it launches the built test stub in the guest. The launch environment is the one
Bazel's test runner would provide.

**The guest reads no checkout** ([the guest reads no checkout](decisions/2026-10-10-the-guest-reads-no-checkout.md)). It sees one read-only
share, Bazel's output user root. A **parity layout** makes that root position-identical: the output user root is a
single symlink onto the mount. The guest builds its own runfiles tree from the MANIFEST of the runtime descriptor,
at `<vmData>/runfiles/<digest>`, with the `runfiles-tree` verb in the `parity-probe` phase. A runfile whose target
is on the Bazel share is a link to its guest path. A runfile whose host file lies in the checkout is a **staged
runfile**, such as a `pnpm-lock.yaml` of the descriptor `data`. The tree holds a copy of its bytes. The first
request carries no bytes, so a tree of the same digest is reused. A guest without that tree refuses the request
with `runfiles_staged_bytes_missing`, and the second request carries the bytes. The digest covers the MANIFEST,
the path table and the digest of each staged runfile, so the host predicts the root. A root other than the
predicted one is `guest_runfiles_mismatch`, and a bad reply is `guest_runfiles_protocol`. The verb keeps that tree
and the newest other one.

The lane daemon gets `--home $AIR_VM_DATA/daemon/home`, a directory in its state directory, and its IDE root on the
guest disk. The boot of the verb makes both, so no path of the lane daemon derives from a checkout. The IDE
distribution itself is read, not written, and the IDE writes its data into its context under `$AIR_VM_DATA/ide`.

The share arrives on Apple's one VirtioFS automount device, and the controller mounts that device
itself: at `/mnt/AirVmShares` on a Linux guest and `/Volumes/AirVmShares` on a macOS one
(`GuestOsProfile` in `crates/avl-base/src/config.rs`). Every `ls` and `pull` argument hangs off
that mount point, so read a path off the guest rather than assume it. A space-free mount point
keeps every guest path one shell word. The `container-linux` backend has no automount device: its share
is the skill's read-only mount at the path it has on the host. Its `$AIR_VM_DATA` is on the
container's disk. A pull reads a guest file with the agent's `read-file` through `container.cmd exec`, as
on Docker. The backends differ only in how a share is declared. Tart
declares the share as `--dir=<share-name>:<host-path>:ro`, and both halves are load-bearing. The `<share-name>:` prefix
names the share's directory under the mount point. Passing no `tag=` option keeps the share on the
single `com.apple.virtio-fs.automount` device, which is the only one the guest-side remount mounts. A
tagged share is invisible to the guest, and an unprefixed one lands under the host directory's
basename. `share_argument` in `crates/avl-vm/src/worker/tart.rs` owns that grammar, as `declare_shares` in
`crates/avl-vm/src/worker/parallels.rs` owns prlctl's. A share-set change restarts the
`tart run` process the controller owns, and it power-cycles the Parallels VM once. The share names no checkout, so
a run from another checkout of the host changes no share and restarts no worker. A Parallels VM keeps a share that
an older controller declared until `prlctl set <vm> --shf-host-del air-macos-repo` removes it.

A Linux run builds the daemon target of the Linux guest, `//plugins/air/tests/integration/ui:ui_daemon_linux_guest`,
and a macOS run builds `:ui_daemon`. On a Mac the Linux target names the `linux_aarch64` distribution and JBR. On a
Linux x86_64 host it names the x86_64 guest, whose distribution is the host's own build. **No Bazel command of the
controller or of a wrapper carries a build option of its own.** Bazel keys its analysis cache on build options, so a
lane build with options of its own discarded the analysis of every other command, and every other command discarded
the lane's: one test before a lane build cost 11.6 s of analysis on 2026-10-10. The guest is therefore a target, and
the lane builds no runfile links: the controller reads the MANIFEST of the runtime descriptor on every host.

A lane IDE downloads nothing and stages no JVM. It runs on `jbr/` of its distribution, under the lane daemon
and under `lane-run`. The lane daemon is a verb of the guest agent and runs no JVM, so a daemon start copies no
JBR to the guest. The `jbr` section of the runtime descriptor names only the platform of the guest. No
identity reads a file of the JBR, because the fingerprint of the distribution covers it. The lane daemon refuses a
distribution without the JBR at its boot, and the refusal names the path. No lane target and no descriptor carries a
JBR archive.

**Remounting is a correctness requirement, not hygiene.** A VirtioFS guest keeps a dead node for any
file the host rewrites after the mount. `stat` then still answers from the cache, while `open` fails
with `ENOENT`. A host `bazel build` rewrites exactly the outputs the next run reads. Remounting is
the only invalidation the guest offers, which is why the controller owns the mount instead of using
the boot automount. This is the filesystem's behaviour and not Apple's, and it was reproduced on a
Linux worker on 2026-08-13.

`umount` answers "target is busy" while anything in the guest still holds the mount open. The IDE
holds it, because the IDE runs from the share, and so does any in-flight run that reads share-backed
data. The daemon does not, and the next section states how that is asserted. Each lane IDE is a run of
the guest agent's supervisor, so after the IDE stop the controller asks the supervisor about every IDE
context. A run that is not finished refuses the remount as `daemon_remount_ide_running`.

### Guest-local writable state

Everything a worker writes lives under `$AIR_VM_DATA`, in five directories and the parity marker
`.air-vm-parity.json`:

| directory | holds |
| --- | --- |
| `runfiles` | the runfiles trees the guest builds from the MANIFEST, with the copies of the staged runfiles |
| `ide` | one context for each launch key of the lane IDE: its `config`, `system`, `plugins`, `log`, `project`, `home` and `bin` directories, its argument file `ide-jvm.args`, its launch record `launch.json`, and the run directories of its supervisor slot |
| `tmp` | temporary files |
| `daemon` | the daemon's own state, the `home` that the lane IDE gets as `idea.dev.project.root`, and each iteration's results. The run of the lane daemon starts in this directory |
| `state` | the supervisor run roots, the SSH host-key fingerprint, and the guest agent binary itself |

An older controller also wrote `daemon-runtime`, `out` and `build-download` there. Nothing reads them now, and each
daemon start removes them before its free-space check.

`state` is where the controller's own
guest-side program lives, so a worker with no `state` is one nothing here can reach. A Linux worker
holds no checkout, no credentials and no cache, so a recycle loses nothing.

## The daemon loop

A one-shot run pays an IDE launch with a project open on every iteration. `run` keeps that IDE warm in a
persistent guest process: the **lane daemon**, the `lane-daemon` verb of the guest agent. It reads the runtime
descriptor of the lane build. `lane-run` is the one-shot form of the same library, with no control channel. A Bazel
lane test, `bt --lane` and a TeamCity agent run it on their own host. Why a daemon is kept,
now that Bazel caches the distribution it launches, is [ADR 0104](../../../../plugins/air/docs/decisions/0104-the-daemon-caches-a-process-not-a-build.md).

The rule `plugins/air/tests/integration/air_ui_daemon_runtime.bzl` writes a versioned JSON **runtime
descriptor**, and `//plugins/air/tests/integration/ui:ui_daemon` is that descriptor. It names the static flags, the
distribution, the guest platform, the inputs of the lane IDE and the data inputs. The test code of a run reaches the
lane IDE as the context plugin files and the lane inputs, which the controller pushes by digest. The data inputs are
the runfiles of the `data` of the rule, without the files that another field names. Each file that the descriptor
names is a runfile of the descriptor target. Both ends assert its `schemaVersion`, so the shape is a contract between
Bazel and the controller. `parse_runtime_descriptor` in `crates/avl-wire/src/runtime.rs` validates it fail-closed.
The lane daemon reads the inputs of the lane IDE from it at its boot, and the boot makes the directories of the run.

Every UI lane runs over this one descriptor, which names the suite documents and the lane documents of every lane. The
run request of the lane daemon names the lane, and the lane daemon carries the warm IDE across whole controller
iterations ([ADR 0138](../../../../plugins/air/docs/decisions/0138-air-owns-its-lane-ide.md)).

The guest agent launches the IDE and owns its process
([ADR 0234](../../../../plugins/air/docs/decisions/0234-the-guest-agent-launches-the-lane-ide.md)). The daemon
decides what to launch and when, and it prepares and launches the IDE in its own process. The preparation lays out
the context `$AIR_VM_DATA/ide/<launchKey>`: the data directories, the project, the disabled plugins, and the `home`
and `bin` directories. The daemon then writes the option files, the bridge token, the seeds of the home and the
launchers into the context. The launch composes the argument file of the IDE and starts the supervisor run
`run-ide-<launchName>` in that context, in one call. So the context is also the run slot of the IDE. The preparation
and the launch check their documents first. A launch property that names a data directory of the context is refused.
The IDE argv is `java @<context>/ide-jvm.args <project>`.

The environment of the IDE is closed, and the layout of its context sets it. `HOME` is the `home` directory of the
context, and `PATH` starts with its `bin` directory. The agent copies a short allowlist from its own environment,
for example `DISPLAY`, `LANG` and `TMPDIR`. When the agent has no UTF-8 locale, the IDE gets `LC_ALL=C.UTF-8`.
No other variable reaches the IDE, and the run spec records this policy and no value
([ADR 0221](../../../../plugins/air/docs/decisions/0221-the-lane-ide-runs-in-a-closed-environment-and-no-secret-crosses-the-agent.md)).
The bridge token is a private file of the config directory, `config/air-ui-test/token`, which the daemon
writes. No launch document, argument file or argv holds it.

The IDE has no run budget. It ends only when something stops it: `--fresh-ide`, a remount, the watchdog, the
controller, or its own crash. The lane daemon checks the IDE at the start of each iteration. An IDE that ended on its
own, or that does not answer one lifecycle poll within 5 s, is relaunched on its context. So the lane pays a launch
and no timeout. The lane daemon reads the lifecycle of the IDE only through the long poll
`GET /api/air/ui-test-events` of the bridge. The poll uses the port and the token of the bridge. A readiness wait
ends at once with `lane_ide_not_ready` when the run of its IDE ends.

**The IDE outlives a daemon restart of the same product.** A cancel of the daemon run does not reach the IDE run.
A restart for another launch digest keeps each live IDE whose launch record names the product of the build,
and the timing line reports `ide keep`. The new daemon adopts that IDE on its first attach. Every other daemon
start, `daemon stop`, a lease release and a pool recycle stop every lane IDE of the worker through the agent's
`ide-gc`. The next run on a healthy daemon that holds no running IDE launches the IDE again, and the timing line
reports `ide relaunch`. A start that remounts a VirtioFS share also stops every IDE, because each IDE runs from the
share.

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
stat-keyed content cache. It then pushes only the changed context plugin files and lane inputs into the store of
the lane daemon over HTTP.
One connection carries a push of at most 8 MiB, and each further 8 MiB begun opens one more, up to four. It streams the run back as NDJSON, the run stream. Three levels fall out of the digests, and each
iteration's timing line reports the one it took.

| change | detection | cost |
| --- | --- | --- |
| product inputs, meaning anything inside the prepared distribution | product stamp diff | stop every lane IDE, remount, then launch the IDE again. The daemon survives, and nothing is assembled |
| the context plugins, meaning the in-IDE test bridge and its resident part, which no lane distribution carries | the digest of each plugin directory | a push of the changed plugin files, with **no remount** and no distribution compose. An edit of the bridge directory alone reloads the bridge plugin in the running IDE, and the timing line reports `ide reload`. An edit of the resident part relaunches the IDE on the same context, and so does a reload that fails |
| the launch of the lane daemon, meaning a static flag of the runtime descriptor, a boot setting or the daemon environment | launch digest diff | an automatic daemon restart. The IDE of the same product survives it, and the timing line reports `ide keep` |
| the container's declaration, meaning the image tag, the share or the display | create-record diff | a recreate plus a cold daemon start; Docker only |

The series of 2026-10-15 measured the plugin rows under the lane daemon. It ran on master `64c3e5270948e`, on
the worker `air-docker-2`, under one held lease, with the suite `rename-session`. The other lanes held their builds.
The first row of each kind includes a host build of 7 to 13 s.

| row | edit | decision | total s |
| --- | --- | --- | --- |
| R0 | none | `ide reuse` | 22.9, 10.0, 10.4 |
| R3 | a literal of the resident plugin | `ide relaunch` | 23.1, 19.6, 15.9 |
| R4 | a literal of the bridge plugin | `ide reload` | 20.4, 8.7, 9.1. The reload took 3.6, 2.4 and 2.4 s |

The questions are asked independently, and one file may answer yes to more than one.

The product stamp reads the `fingerprint.txt` that the dev build wrote inside the distribution's
tree artifact. It is an xxh3 over every entry's content hash, so stamping never walks a
multi-gigabyte tree. The stamp folds into the lane config key of the guest agent, so a
relaunch is the host's own key-change path rather than a new mechanism. The launch record of each IDE
context names the product digest, which is how a restart knows which IDE it may keep.

Nothing the lane daemon holds open lives on the share. It runs from the installed guest agent, in its state
directory on the guest disk, so the daemon can outlive a remount, and there are two remount windows:

- **With the daemon alive**, for a product change. `POST /ide/stop` makes the daemon quit its IDE, and it
  answers `409 busy` if a run is in flight. `ide-gc --stop-all` then cancels every other IDE run of the
  worker, and the controller remounts. The controller holds the lease, so no run starts in between. The lane
  daemon serves `/ide/stop`, so the remount needs no route of its own. The timing line reports `ide remount`. On
  Docker, the controller refreshes the shares with the 2 s settle instead of a remount. The timing
  line still reads `ide remount`.
- **Between daemons**, for a change of the launch digest. `start_daemon` in `crates/avl-vm/src/daemon/start.rs`
  remounts after the old daemon stops and before the new one starts. Before the remount, the guest
  agent's `ide-gc` keeps or stops the lane IDEs. On Docker, the controller refreshes the shares with
  the 2 s settle instead of a remount, so an IDE of the same product survives it.

`--fresh-ide` is cheaper than either. No host bytes changed, so it only stops the IDE.

`run` starts or restarts the daemon by itself whenever the build it just prepared does not match the
one running. The daemon holds the guest supervisor's run slot for its whole lifetime, so
`exec` is refused until `daemon stop`. Each lane IDE has a slot of its own, so it does not hold that
slot. A lease release keeps that warm daemon:
[ADR 0106](decisions/0106-a-warm-daemon-survives-a-lease-release.md). The release stops the lane IDEs,
so the next holder gets none of them.

The controller reaches the daemon through the relay, never through the guest network. For each pooled
HTTP connection, the controller spawns `tart exec -i <worker> <agent> relay <port>`. On Parallels, it
spawns `prlctl exec <worker> "'<agent>' 'relay' '<port>'"`. The `relay` verb of the guest agent bridges its
stdin and stdout to `127.0.0.1:<port>` inside the guest. The daemon binds only that loopback address.
So the host needs no route to the guest and no permission for one. Each relay spawn costs about 0.65 s.
[ADR 0182](decisions/0182-the-daemon-is-reached-through-the-exec-channel.md) records the decision.
On the `container-linux` backend the controller spawns no relay. `start --publish` maps the daemon's guest port to a
host loopback port derived from the checkout (`AIR_VM_DAEMON_HOST_PORT`), and the controller connects to it with
plain TCP. The daemon binds the container's interfaces for that, because a published port forwards to the
container's address, not to its loopback; its token guards it. Each one-shot guest command runs through
`POST /v1/execute` on the skill's control port, with the Bearer the skill writes to
`out/testing-ui/container.ctl_bearer`, and a pull runs through `container.cmd exec`
([the lane runs in the testing-ui container](decisions/2026-10-04-the-lane-runs-in-the-testing-ui-container.md)).

A start whose daemon never answers `/status` within `AIR_VM_DAEMON_HEALTH_TIMEOUT` (180 s) cancels the
run it launched and keeps its record, so `daemon log` still reads it. The refusal names what the last
probe saw. When no relay opened, the refusal names the worker and the loopback port. The daemon
published its state file in that case, so read `daemon log` to see why the daemon did not answer.

`daemon stop` also retires a daemon run no record names. It keeps the record, and refuses
`daemon_retire_failed`, while a run the supervisor did not finish still holds the slot.

A worker whose VM Tart no longer lists, while its `tart run` process lives on, releases its lease
without the guest check. `pool recycle <worker>` then ends that process and clones the slot again.

The daemon environment is part of the launch row, because a running daemon cannot change its own environment.
`prepare_build` in `crates/avl-vm/src/daemon/build.rs` folds that environment into the launch digest, so a
changed value restarts the daemon.

Two refusals exist to prevent a false green: `no_tests_discovered` for a selection that matches
nothing, and `all_tests_skipped` for a run that skipped every scenario, each listed with its own
reason. A body that aborts its scenario through its scope ends the scenario as `ABORTED`, and the run counts it as
skipped.

The daemon publishes one `planStarted` record before the first execution. It includes the execution
count and each discovered class. The report lists each class without a later result in
`unreportedClasses`. A non-empty list produces `infrastructure_error`, and `--text` prints the list.

### What a host build during a run takes away

The guest runs the files the iteration pushed, so a host build while a run is in flight does not corrupt the
execution. It corrupts the **attribution**. The verdict then describes bytes the checkout no longer produces,
and a reader maps it to the source in their editor. Bazel replaces a file by rename, so nothing else notices:
the run keeps its open handles and reports green.

So each iteration re-hashes the files it pushed when the run ends, the context plugin files and the lane inputs, and
compares them with what it pushed. A file that moved is a note. The note names the files and says to re-run. The
terminal and the run journal show it, and the report carries it in `notes`. A note never changes the verdict, so the
status follows the counts. The guest ran the bytes that the iteration pushed, so the execution and its counts stay
true. Only the attribution to the checkout is lost. `PreparedBuild::pushed_file_drift` in
`crates/avl-vm/src/daemon/build.rs` is the check. The verdict card shows no note, so read the journal or the report
before you map a verdict to your editor.

### What a remount does not notice

`launchDigest` and `mountDigest` cover Bazel's declared outputs, not the checkout. **An edit to a
repository file that no Bazel output depends on remounts nothing.** The guest keeps answering from
the bytes it cached, and nothing reports a stale state. From the outside you see three things. The
change is in the editor, the timing line says `ide reuse`, and the guest reads the previous content.

The remedy is `daemon restart`, which remounts between the two daemons. A digest over the checkout
was rejected rather than overlooked. The checkout is shared between agents and working copies, so
such a digest would remount for every unrelated edit anybody made. A remount costs a reliable 16 s.

## The watchdog and the report

The watchdog of the lane daemon validates the active scenario and the gaps around scenarios. So another scenario
does not make a lane-size constant stale. An emergency deadline caps the whole run from the count of the
scenarios that the plan names. Only the start and the end of a scenario move the deadlines. Console, log, HTTP and
XML activity never claim that a scenario made progress. The skill owns the budgets and their overrides.

On expiry the watchdog marks the run expired and kills the IDE first, because an in-IDE operation that never
answers holds the run thread. The kill is a cancel of the IDE run through the guest agent. The
cancel first writes a thread dump of the IDE into its log directory. The watchdog writes `watchdogExpired` after the kill, so a blocked
stream cannot hold the kill. It then waits one minute for the run thread to unwind, and a run thread that does not
unwind gets a note. The run thread starts no further scenario. The controller
reports a stream that ends mid-run as `daemon_died` (exit 70). `daemon log` still works after the
daemon is gone. The next run starts a new daemon, and that start stops a lane IDE that the dead daemon
left alive.

`lane-run` has a run budget too: the smaller of `--run-budget-sec` and `$TEST_TIMEOUT`, less a teardown reserve of
180 s. `air_lane_test` passes 2700 s by default, because a TeamCity lane job ends at 60 minutes. The lane tests of
`ui-real`, `ui-perf` and `ui-live` pass none, so the Bazel test timeout is their budget. The budget caps every
deadline, and its end is `run_budget_timeout`. A monitor thread writes a hang report every 3 minutes. One minute
after the end of the budget, the monitor stops the run hard. It writes the partial JUnit XML, cancels each live IDE
run with a thread dump, kills each recorder, writes `lane-hang-report.txt` and exits 1. On TeamCity, the end of the
budget and the hard stop each write a build problem with the identity `lane-run-budget`. Under a Bazel test, the end
of a run that is not green and the hard stop also keep its evidence in `lane/` of `TEST_UNDECLARED_OUTPUTS_DIR`, as
the next section states.

`lane-run` runs each failed scenario of the lane IDE once more, on a recycled IDE, after the last suite of the lane
IDE. A pass of the rerun makes the run green, and the JUnit XML keeps both attempts, so TeamCity shows the test as
flaky when the build has the failure condition "Support test retry". More than 3 failures rerun nothing. The lane
daemon of a worker reruns nothing, so `vm.cmd run` shows each flake.
`env AIR_LANE_INJECT_FAILURE='first:<scenario>'` (or `every:`) with `--test_env` fails a scenario on purpose, to
see the rerun. [lane-run reruns each failed scenario once](decisions/2026-10-29-lane-run-reruns-each-failed-scenario-once.md)
records why.

**The versioned JSON report is the contract, and the run stream is its one source.** The report, the
JUnit XML and the TeamCity service messages are three readers of that stream. The controller writes
the XML of each iteration beside its report, and the XML of the command to the file that
`XML_OUTPUT_FILE` names. It returns the report in both the success and the error envelope. A stream
that ends without a summary, a summary that contradicts its records, and a recorder that contradicts
the stream can never be green. Agents consume the JSON report.

## What it costs

The fixed costs around a lane, measured on `air-linux-1`, each reproduced on the same worker and
day. `air-linux-1` was a Tart Linux worker, which
the decision [the Docker image carries Node, and the Tart Linux worker is retired](decisions/2026-10-08-the-docker-image-carries-node-and-the-tart-linux-worker-is-retired.md) retired, so its rows are history.
The decision [the Lima engine is retired](decisions/2026-10-13-the-lima-engine-is-retired.md) retired the Lima engine, so its rows are history too:

| what | cost |
| --- | --- |
| cold `daemon start`, meaning the host build, staging and boot | 188 s |
| warm `daemon restart`, meaning nothing changed and staging reused | 32 s |
| an IDE launch plus its first-suite readiness dwell | 10-12 s |
| a second run of one class against the warm IDE | 11 s |
| one run of `AgentSessionToolWindowComposerUiTest` on `air-docker-1`, after a cold daemon start | 234 s of tests for 8 scenarios, after a 37.1 s host build |
| a later run of the same class against the warm IDE on `air-docker-1` (`ide reuse`) | 286.6 s of tests for 8 scenarios, after a 75.4 s host build |
| the `ui` lane, 71 tests in 40 classes, on `air-docker-1` on the Lima engine, since retired: cold daemon, then warm daemon | 756.4 s, then 697.0 s of tests |
| the same lane on `air-linux-1`, in the next hour: cold daemon, then warm daemon | 750.4 s, then 860.1 s of tests |
| the `ui-real` lane, 4 tests in 2 classes: twice on the Lima engine, since retired, then on `air-linux-1` | 70.5 s and 70.4 s, then 84.6 s of tests |
| the first start of the Lima engine, since retired, with the cloud image in Lima's cache | 76 s |
| the first start of the Lima engine, since retired, with the cloud image download | 147.4 s, of which the download is 103 s |
| the recreate of the Lima engine, since retired, with the image download from the mirror, plus an image build (`pool start`) | 322 s, of which the engine start is 189 s |
| a warm stop, then a warm start, of the Lima engine, since retired | 11.1 s, then 19.8 s |
| the first start of the Lima engine, since retired, on Ubuntu 26.04, with the image download from the mirror | 197 s, of which the download and its conversion are 137 s |
| in the Lima log, a warm start of the Lima engine, since retired, inside `pool start` or `run`, on Ubuntu 26.04, then on 24.04 | 7 s, then 15 s |
| a warm `pool stop`, then `pool start`, of the Lima engine, since retired, on Ubuntu 26.04 | 20.3 s, then 39.5 s |
| the `ui` lane of `flow-new-session`, 9 tests in 4 classes, on `container-linux-1` on a Mac with Apple `container`: cold daemon, then warm daemon | 152 s, then 84 s; 65 s of tests each |
| the same lane on an Ubuntu 26.04 host with 8 cores and rootless Podman 5.7: cold daemon after a cold Bazel build, then warm daemon | 760 s, of which the Bazel build is 497 s and the tests 200 s; then 223 s, of which the tests are 143 s |
| the same lane on a Windows 10 PC with WSL 2.9.13 and `wslc`: a fresh container with a cold daemon, then warm daemon | 283 s, of which the daemon start is 84 s and the tests 159 s; then 134 s, of which the tests are 102 s |
| `shard --shards 2` of the `ui` lane on two containers of the Lima engine, since retired | 429 s makespan, 770 s serial |

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
2026-10-01, and each is one sample. The lane time on 26.04 was never measured. The runs that ADR 0191
names are no longer owed, because the engine is retired.

The **daemon** is the expensive warm state, several times the IDE it holds. A restart that keeps the
IDE saves the launch and its readiness dwell, and it pays the rest of the restart. Read those rows
against the invalidation ladder. A test-only edit pays the 11 s row. An Air product edit pays about 61 s,
including its host build. Only an edit that changes the launch digest of the lane daemon pays the restart.
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

A failing scenario spends its operation timeouts before it reports, so **read a slow lane as a red
lane** until the per-class numbers say otherwise. A warm full-lane iteration is about 196 s.

## Artifacts and diagnostics

Normal execution requires neither Peekaboo nor guest Screen Recording or Accessibility grants.

| what | where in the guest |
| --- | --- |
| JUnit XML of the lane daemon, per iteration, which `/results` serves. The controller writes its own XML of the stream beside the report on the host | `$AIR_VM_DATA/daemon/iterations/<iterationId>/test.xml` |
| `idea.log`, the heartbeat screenshots and the other IDE logs, per IDE launch | `$AIR_VM_DATA/ide/<launchKey>/log/<launchName>/` |
| the thread dump of an IDE that the watchdog or a stop killed | `threadDump-before-kill-<millis>.txt` in that same directory |
| the standard output and error of one IDE launch, and its supervisor log | `$AIR_VM_DATA/ide/<launchKey>/run-ide-<launchName>/` |

Under a Bazel test the same files stay below `TEST_TMPDIR`, which Bazel deletes after the test. So `lane-run` copies
them into `lane/` of `TEST_UNDECLARED_OUTPUTS_DIR` for a run that is not green, and Bazel keeps them in `test.outputs/`:

| what | where in `lane/` |
| --- | --- |
| the log directory of each IDE launch that ran a failed scenario, an expiry or the hard stop: each `idea.log`, the kill dump, the other IDE logs and the heartbeat screenshots | `ide/<launchKey>/<launchName>/` |
| the standard output and error of the IDE process of each such launch, `run.log`, and its `supervisor.log`. An IDE that dies before its logger starts leaves its reason only there | `ide/<launchKey>/<launchName>/run/` |
| the slow-step dumps and the authored artifacts | `iterations/<iterationId>/slow-steps/`, `iterations/<iterationId>/authored-artifacts/` |
| what the copy kept, and each file that its caps of 32 MiB per file and 256 MiB in all cut or left out | `evidence.txt` |
| a trace attachment that came outside a recorded scenario, such as the dump of a slow launch, in any run | `attachments/<n>-<name>.<ext>` |

A green run copies nothing, so only `attachments/` can hold files then. Three properties of the guest paths are worth
stating. Each IDE launch writes its own log directory, so iterations never overwrite each other.
The first launch of a context is `start-<stamp>`. Under the lane daemon, a relaunch for `restart-ide` is
`restart-N` and a relaunch after a reset that did not hold is `recycle-N`. So list the `log` directory rather than
assuming the path. The guest agent keeps the newest five log directories of each
context, and always the one of a live IDE. A fresh preparation deletes the data directories, the project, the
argument file and the launch record. It keeps the log directories and the run directories of earlier launches, and
`ide-gc` trims them. An iteration id includes the daemon boot identity, and each
result directory is reserved with an atomic create. So a restarted daemon cannot make stale XML
authoritative.

`ls` aims `pull`, and both work throughout the daemon's lifetime, because neither wants the
supervisor's run slot. Both answer during a run too, because a run holds the worker's lifecycle lock
only at its two ends. The run takes the lock to write its run marker
`<runtime root>/workers/<key>/run.json`, and again to remove it. The marker names the run in flight,
and `lease show` with the receipt names it as `run=<id>`. `exec` and `peekaboo` act inside the guest,
so they refuse `lease_busy` (exit 75) until the run ends. Another operation on the worker, such as a
`daemon start` or a `lease release`, holds the lock for its whole length. Then `ls` and `pull` refuse
`lease_busy` until it ends. The fix is to wait, not to stop the daemon that makes the next iteration
fast. [A run marks its worker and holds the lock at its ends](decisions/2026-10-13-a-run-marks-its-worker-and-holds-the-lock-at-its-ends.md)
records the decision.

The trace bundles and the heartbeat screenshots of the IDE log directory are the evidence of record
on Linux and Parallels. On a Tart macOS worker a Robot screenshot is black, and ADR 0113 says why. Read
the Swing tree of the trace there instead.

A macOS worker has two VNC servers with two different auth models. Both are a diagnostic channel and
never test control. The skill's `references/macos-pool.md` says which to open and how.

## Traces

Every UI scenario leaves a **trace bundle**, whichever launcher ran it, in the manner of the Playwright
trace viewer. A bundle holds a video and a picture at every instruction boundary and held check.
It also holds the Swing tree at each of those moments, the gestures, the bridge calls, the scenario's slice
of `idea.log`, and the generated program, so the instructions a failure never reached are visible. The
contract is [`community/tools/vm/spec/scenario-trace.spec.md`](../spec/scenario-trace.spec.md). The lane's side is
[`plugins/air/spec/docs/flow-trace.spec.md`](../../../../plugins/air/spec/docs/flow-trace.spec.md), and
[ADR 0155](../../../../plugins/air/docs/decisions/0155-a-ui-scenario-leaves-a-trace.md) is why the trace is shaped this way.

The recorder is `air-trace-record`, a binary that the lane daemon starts from its runfiles, once per iteration. The
lane daemon tells it what happens, one line per event on its standard input. A lane daemon that dies closes that
pipe. Its bundle then still gets its video and its manifest, marked `truncated` and naming the span that was running.
A trace is evidence and never a verdict: every capture problem becomes a record in the bundle, and a trace
that could not be pulled leaves a green run green.

| launcher | where the bundles land | who cleans up |
| --- | --- | --- |
| the lane daemon on a worker | `$AIR_VM_DATA/daemon/iterations/<iterationId>/air-traces` in the guest, then pulled after each test to `<runtime root>/runs/<runId>/traces/<iterationId>/<NNN>.zip` | the guest's iteration retention; the run directory on the host |
| a `vm.cmd` run with `AIR_VM_EXPORT`, such as a run on the Mac Studio through `bd-host` | a copy of the run's journal, its reports and the zips of the scenarios that did not pass (`all`: every zip) in `out/air/vm-export/runs/<runId>/` of the checkout. `[host]` of `.branchdeck.toml` sets the variable and names the directory in its `outputs`, so `bd-host` copies it back. Under `failed`, a run that failed nothing exports nothing | the next `bd-host` run with an export, which replaces the directory |
| a Bazel test, host `bt.cmd` included | `air-traces/` in the target's `bazel-testlogs/.../test.outputs/`, with `lane/` beside it for a run that is not green. A local Bazel packs them into `outputs.zip` there, and the TeamCity build keeps them as files in `bazel.logs.zip` | Bazel |

A bundle is `<root>/<runId>/<TestClass>/<scenario>/`. A second attempt of the same scenario in one run is
`<scenario>.2`. The controller pulls each finished bundle after the test that finished it, so a failed
scenario's video is on the host while the lane goes on, and each arrival is a `traceReady` record of the run's
journal. `run`'s text verdict ends with a traces block, one line for each scenario with the failed ones first,
or with `traces: not pulled: <reason>`. The JSON report names each zip and its bundles in `traceArchives`, and
a failed pull in `tracesError`.

**Allure results.** The recorder writes Allure results only when you ask for them with
`-Dair.flow.trace.allure=on`. A zip has no hard links, so the results carry each video and picture of the bundles a
second time, and nothing uploads them to Allure TestOps yet. For one Bazel run, give the flag to `lane-run`:

```bash
./bazel.cmd test //plugins/air/tests/integration/ui:ui_lane_test --test_arg=--jvm_flag=-Dair.flow.trace.allure=on
```

On TeamCity, the build parameter `system.pass.air.flow.trace.allure=on` gives the same flag. For a `vm.cmd` run on a
worker, add the flag to the lane flags of `_flags` in `plugins/air/tests/integration/ui_lane_ide.bzl`.

With the flag, the recorder writes one Allure result into `<root>/<runId>/allure-results/` at the end of each
scenario, beside the bundles of the run. The result has a step for each lane span, with the Before and After pictures
of the step and the files that the lane attached to it. It also attaches the video, the `idea.log` slice and the path
of the bundle. The attachment files sit in the same directory. The lane runs no Allure code. A pulled zip holds the
results of the bundles it holds. Open the results of a run with the Allure command line:

```bash
allure serve <root>/<runId>/allure-results
```

Unzip a Bazel `outputs.zip` or a pulled zip first, and pass the `allure-results` directory inside it. `allure serve`
takes more than one directory, so the zips of one run open as one report. On TeamCity, the results of a build with the
flag reach TeamCity inside `bazel.logs.zip`, which holds every Bazel test output.

**Opening them.** One server shows every bundle and every `vm.cmd` run on this machine, live, in the Air docs
site:

```bash
./community/tools/trace.cmd serve           # in the foreground: http://127.0.0.1:7357/air/runs
./community/tools/trace.cmd serve --detach  # one server for this machine, in the background
```

It looks in the Air lanes' `bazel-testlogs`, in the export of the checkout, in every pulled zip, and in the
journal of each run under `<runtime root>/runs`. It serves zips by byte range without unpacking them, and it tells the page
when a run appears or a run's journal grows. It listens on 127.0.0.1 only. `--root DIR` adds a place to look.
`pnpm dev` in `plugins/air/docs` proxies `/__air` to it, so the viewer under development reads the same runs.
A root can be a zip that holds zips: discovery extracts an inner zip once into `out/air-traces-cache`. So
`--root bazel.logs.zip` works for a TeamCity artifact, and so does a local `outputs.zip`.

**Where the time went.** Each relaunch of the IDE in a scenario is a `restart` span, and the flame chart shows its
parts below it: the quit, the work while the IDE is down, the launch, the wait for `READY`, and the robot and ping
chain. `vm.cmd report time` sums those parts over a run. The header of a picture names the time that the recorder spent
on its snapshot, part by part. A step that no bundle holds is a `timing` record of the run stream. Examples are the
launch of a bare IDE and the end of `lane-run`. The Notes of the run page list these records, and the launch, the
readiness and the exit of the lane IDE. The steps that each scenario runs are one sum there, and the notes after the
first 20 fold under one line.

**A CI run.** `--teamcity` opens the traces of a TeamCity build:

```bash
./community/tools/trace.cmd serve --port 0 --teamcity https://buildserver.labs.intellij.net/buildConfiguration/ijplatform_master_AirPluginUiTestsAggregator/1100229901
```

The value is a build id or the URL of a build page, and it repeats. A composite build, such as the aggregator, stands
for the shard builds that ran under it. The server downloads the `bazel.logs.zip` of each shard once into
`out/air-traces-cache/tc-<server hash>-<build id>/`. The listing names the shard and the build of each run, and a
shard that failed to download is a root with its error. For the server of `--teamcity-url`, the token is
`TEAMCITY_TOKEN`. For another server, or without the token, the server asks the TeamCity CLI, `teamcity api`, which
uses the login of `teamcity auth login`. A server is `https`, or `http` on a loopback host. `--teamcity` does not go
with `--detach`, so pick a free `--port` when a detached server runs.

**On TeamCity.** `bazel run @community//tools/vm:air-trace -- teamcity-publish` is the step after `bazel test`. It
writes one archive and publishes it as the artifact `air-traces.zip`. The archive holds `runs.json`, then one zip for
each bundle at `<lane>/<runId>/<TestClass>/<scenario>.zip`. TeamCity serves an entry of an archive artifact itself, with
byte ranges. A loose artifact would not work: TeamCity redirects it to a signed address, where its siblings are refused.
Each test of the Tests tab gets its trace and a `trace viewer` link to the CI page of the hosted site,
`https://ij-void-docs.labs.jb.gg/air/ci/<buildId>/trace?zip=<zip>`. A portal proxy of the site reads `runs.json` and the
zip of that build from TeamCity. The build log names the CI page of the build, `…/air/ci/<buildId>`, with all its runs.
`--site URL` or `AIR_TRACE_SITE` names another site, such as a staging portal. A failed scenario also gets its last
picture and its video as loose files in `air-traces-failed/`, which the Tests tab shows.

**While the test runs.** TeamCity takes the results of a Bazel test from its JUnit XML at the end of the test. So a
step before the Bazel step streams one file into the build log and gives its path to the test as `AIR_LANE_LIVE_LOG`.
`lane-run` appends a `progressMessage` at the start and at the end of each scenario, such as
`ui 12/46: <scenario> failed (3.2 s)`. For a failed or aborted scenario it also publishes the video and the last
picture into `air-traces-failed/` as soon as the bundle is there. The paths are the ones of `teamcity-publish`, which
publishes the same files again after the test. Off TeamCity, or without the variable, `lane-run` writes no such file.

The step builds no viewer and never starts a Bazel, so it needs no node and no pnpm.

The step reads the build from the agent's property files, `TEAMCITY_BUILD_PROPERTIES_FILE` and the configuration file
that it names. `bazel run` passes the environment of the step to the program. The listing then names the build of each
run, with the page of the build on the root of `BUILD_URL`. Without the files, the zips and the listing still publish
with no link, and stderr says why.

The archive of a TeamCity build also holds `affected.json`. For each commit of the build, it names the changed files,
the suites that they reach, and the files that reach no suite. The step reads the changed files from
`teamcity.build.changedFiles.file` and joins each commit as `vm.cmd suites` does, on the checkout of the build.
`--repo DIR` names another checkout. A build without changes has no `affected.json`. To try it locally, set
`TEAMCITY_VERSION` and point `TEAMCITY_BUILD_PROPERTIES_FILE` to a file with `build.vcs.number`,
`teamcity.build.changedFiles.file` and `teamcity.build.checkoutDir`.

An agent keeps its checkout and its Bazel output base across builds, so the testlogs can hold the tests of an earlier
build. The step publishes only a test whose `test.xml` is from the start of the build or later. The first step of the
build sets `AIR_TRACE_SINCE` to the start, with `date -u +%Y-%m-%dT%H:%M:%SZ` on the agent. `--since` overrides it. On
TeamCity without either, a warning says so and every test publishes. A warning counts the skipped tests. The step also
empties its output first. Once the archive is published, it removes the bundle directories that the archive holds from
the testlogs, so `bazel.logs.zip` does not carry them twice. Off TeamCity, only `--remove-packed` asks for that. It
never turns a build red: each problem is a warning in the build log, and the exit is 0. Only a usage error of the
command line exits 2. No Air build configuration runs this step yet; it is one more step in `ultimate-teamcity-config`.

When the site in `out/air-site` is missing, or older than `plugins/air/docs/src` or the `src`, `scripts` and
`styles` of the kernel `community/tools/docs-site`, the server builds it once in the background:
`pnpm install --frozen-lockfile` in the kernel and then in `plugins/air/docs`, each when it has no `node_modules`,
then `pnpm build`. The page says that the site is building, or why the build failed. The node and the pnpm are the
ones `//:MODULE.bazel` pins for the docs sites (`//build:docs_site_node`, `//build:docs_site_pnpm`), which the server asks
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
./community/tools/trace.cmd plan out/bazel-testlogs/plugins/air/tests/integration/ui/ui_lane_test/test.xml
./community/tools/trace.cmd plan plugins/air/frontend/session/toolwindow/src/ui/AgentAttachedProjects.kt
```

An input may also be a bundle, a zip, a flow profile, a `vm.cmd` report piped in as `-`, a flow id, or a
check id. The VM sequence comes first. The host `bt.cmd` run is offered with the warning that it takes your
screen. The planner prints commands and never starts a run, because a run costs a lease or your screen.

**What each guest captures.** The Linux guest records X11 directly: real pixels, native popups, and an H.264 video.
The recorder encodes that video itself, so the guest needs no package for it. A macOS host and the macOS guests take
stills from the IDE's own paint route. A direct lane on your own machine never reads your screen, because it shows
everything else you have open and macOS would ask for Screen Recording permission in the middle of a scenario. It
takes the paint stills and records no video, and `lane-run` refuses `-Dair.flow.trace.screen=on` as a JVM flag. A
TeamCity build on Linux is the exception: no person works on the display of a TeamCity agent, so `lane-run` lets the
recorder read it when `TEAMCITY_VERSION` and `DISPLAY` are set. The display of the build is the GNOME desktop of the
agent, so `lane-run` starts an Xvfb of 1920x1080 on a free display, with a window manager of its own, and the IDE and
the recorder use it. The run stream names that display in a note. A host without Xvfb keeps the display of the build,
and a warning in the run stream and in the build log says so. The recorder reads a display of depth 24, whose pixels are BGRX, up to
3840x2160. At 1920x1080 a video costs about 3 MB per scenario minute. Every bundle's manifest names its capture source
and, when a better one was missing, why.

**Which videos a TeamCity build keeps.** A failed, an aborted or a truncated scenario always keeps its video. A passed
scenario keeps its video only in a build of `master`, because that build is the baseline that a failure is compared
with. A Safe Push check, a personal build and a build of another branch set `AIR_LANE_VIDEO=failed`. The recorder
then deletes the video of each passed scenario at its end. Its manifest names the codec `none` and says why. Every
other run keeps each video that it records. Pass `--test_env=AIR_LANE_VIDEO=failed` to `bazel test` to keep fewer.

**What the IDE did.** A bundle also holds the OpenTelemetry spans that the IDE ended in the scenario, such as
each `air.session.log.read`. The viewer shows each one as an `ide` row under the step it started in. The spans are
evidence only: a check that counts them reads the IDE through the bridge, not the trace.

**Why these formats.** Each is one that current Chrome and Safari decode natively, so the viewer ships no
decoder:

- Spans and events are OTLP JSON Lines, so generic OpenTelemetry tools read a bundle as it is.
- A scenario with a video takes lossy AVIF stills from libaom, because a lossless still of the CI desktop is about
  900 KB. An AVIF still is less than half the size of the lossy WebP still before it, at a better quality. A
  scenario without a video takes lossless WebP stills, pixel-exact UI evidence with crisp text. The manifest names
  lossy stills ([A scenario with a video takes lossy stills](decisions/2026-10-21-a-scenario-with-a-video-takes-lossy-stills.md),
  [A lossy still is AVIF from libaom](decisions/2026-10-23-a-lossy-still-is-avif-from-libaom.md)).
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

**Guest stdout and stderr stay withheld from every refusal**, because a guest command can print the
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
| `start`, `status`, `active`, `log`, `cancel` | the run supervisor, for the daemon and for each lane IDE. `cancel --thread-dump <jcmd>` writes a thread dump of the process before the signal |
| `ide-gc` | the lane IDE: the stop of the IDE runs that a worker must not keep, with the removal of old log directories. The lane daemon prepares and launches the IDE in its own process |
| `lane-daemon`, `lane-run` | the lane daemon. `lane-daemon` serves the control channel of the controller in the run slot. `lane-run` runs one iteration from a runfiles tree on a host, for a Bazel lane test, `bt --lane` and a TeamCity agent |
| `contract` | what the agent declares about itself, its own digest included. An install that would push a binary the guest already has is skipped |
| `validate-guest` | the Linux boot |
| `provision-image`, `validate-image` | the macOS image pipeline |
| `trace-pack-ready` | zips the scenario traces that finished since the last pull, with the code of `air-trace pack` |
| `relay` | bridges its standard input and output to a loopback port inside the guest, so the controller reaches the lane daemon without the guest's network address |
| `runfiles-tree` | builds the runfiles tree of a host MANIFEST on the guest's own disk. A Windows host writes the MANIFEST and no tree |
| `read-file` | writes one guest file on standard output unchanged, and names its size and SHA-256 on standard error. A pull on Tart and Docker reads the file through it. The controller refuses `pull_digest_mismatch` when the bytes that arrived are not the ones it named. A pull on Parallels is a `base64` of the file |
| `supervise` | internal, the agent's own detached mode |

Each verb's name is declared once, as `avl_wire::verb::AgentVerb`, and the agent and the controller both spell
it from there. The list exists so that a log line or a refusal that names a verb is recognizable. It is not an
interface to drive. The controller sends the verbs, and an operator does not. An agent invoked by hand
is how a run slot stops matching what the host believes.

## What is intentionally absent

- **No source copying and no guest build.** No Git bundles, no snapshots, no guest checkout, no guest
  Bazel, no guest netrc, no shared Bazel disk cache. The guest reads the host's Bazel outputs through a read-only
  share and writes only to guest-local storage. The only checkout files it gets are the staged runfiles of the
  runtime descriptor, as copies in its runfiles tree. A run tests exactly what
  `bazel build` on the host sees, and a release cleans nothing.
- **No second execution engine.** Every lane goes through the lane daemon. A lane that the lane
  daemon cannot resolve has no runner. Raw Bazel passthrough is rejected because there is no guest Bazel to receive
  it.
- **No GUI automation on Tart.** Worker start enforces fail-closed, grant-free TCC admission. Peekaboo
  needs Accessibility and Screen Recording grants baked into the golden image, which would fail that
  admission by construction. The trace bundles are Tart's test artifacts. The host-side VNC
  framebuffer is diagnostics only.
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
`community/tools/vm` beside it ([the UI-lane workspace lives in community](decisions/2026-10-07-the-ui-lane-workspace-lives-in-community.md)). The
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
| 4 | a product edit invalidates correctly | **Met.** The stamp moved, the shares were remounted, the IDE relaunched, and the daemon survived it |
| 5 | per-class time is comparable to the default runner | **Met.** A macOS worker is slower around a run, not per class. ADR 0113 has the figures |
| 6 | the controller's own suite passes | **Verified.** Run it with the commands under [Verifying the controller](#verifying-the-controller) |

Controller-shaped open work that is not a gate criterion lives in
[`ui-lane-controller-todo.md`](ui-lane-controller-todo.md).

This is test infrastructure only. It does not change Air product behaviour, and it needs no Air
behaviour-spec update.
