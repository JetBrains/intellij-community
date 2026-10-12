---
topic: testing
---

# 184. The worker image is pulled by its content tag

Date: 2026-09-29

## Status

Accepted. It supersedes the last rejected alternative of
[ADR 0183](0183-a-linux-worker-may-be-a-container.md), "an image that the controller does not build". The other
decisions of ADR 0183 stand. How an operator uses the registry is [the VM guide](../vm-ui-tests.md) and the
`vm-ui-tests` skill's settings reference.

Amended 2026-09-29: the recorder encodes its own video and the image drops ffmpeg
([ADR 0185](0185-the-recorder-encodes-its-own-video.md)).

## Context

A Docker worker runs an image the controller builds from `plugins/air/tests/integration/vm-lane/docker/Dockerfile`.
The tag is `air-ui-worker:<12 hex digits>`, a digest over the Dockerfile, its entrypoint `air-display`, the base pin
and `GUEST_PACKAGES`. A host that does not have the tag builds it, and the build installs the guest packages over
the base. On 2026-09-29 that build took 158 s without a cache ([ADR 0183](0183-a-linux-worker-may-be-a-container.md)).
Every cold host, and every CI agent, paid it.

ADR 0183 rejected a registry image because it added a publish step and a second pin. Both objections have an answer
now. The tag is a digest of every input, so the same tag names the same bytes wherever the image was built, and a
pull by that tag hands a worker what a local build would. There is no second pin. The publish step is a push of the
tag the controller just built, and only when the operator asks.

The registry `registry.jetbrains.team/p/ij/containers-public` answers an anonymous `docker manifest inspect` with
`MANIFEST_UNKNOWN`, not `UNAUTHORIZED`, so a lane host pulls without a login. A push needs a login; the measurement
host holds one in the macOS keychain. `versions.env` already reserves a reference there for the macOS golden,
`JETBRAINS_GOLDEN_OCI_REFERENCE`, which nothing pulls or pushes yet.

### The package audit

Nobody had measured what the image installs. `dpkg-query` over `air-ui-worker:c5e6ee2fdde0`, the 26.04 image before
this change: 715 MB installed, 129 MB of it the `ubuntu:26.04` base. The exclusive closure of each guest package,
which is what only that package pulls in:

| package | exclusive | why it is there |
|---|---|---|
| Mesa and LLVM, shared by `xvfb` (through `libgl1`) and `libegl1` | 182 MB | `xvfb` depends on `libgl1`; Skiko needs `libEGL.so.1`. Ubuntu has no Mesa without LLVM |
| `ffmpeg` | 138 MB | the recorder grabs X11 frames in Rust and pipes them to ffmpeg for libx264 and the fragmented MP4 |
| `libgtk-3-0t64` with the icon themes | 60 MB | the JBR's GTK lookup, and `libjcef.so` links `libgtk-3.so.0` (see the consequences) |
| `feh` | 13.5 MB | fluxbox ran `fbsetbg` for the style's wallpaper, and `fbsetbg` opens an `xmessage` without a wallpaper setter |
| `sudo` | 8 MB | the guest steps prefix `sudo -H -u admin` on Tart and Docker alike |
| `fluxbox` | 5 MB | the driver needs an EWMH window manager |
| `xvfb` own files | 3.7 MB | the display |
| `x11-utils` | 1.4 MB | the driver shells out to `xprop` |
| `wmctrl` | 84 KB | the driver's troubleshooting dump only |
| the JCEF libraries | none beyond GTK | `libcef.so` links them. The list lacked `libnss3`: `validate-guest` refused the first image of this change, because the staged `libcef.so` needs `libnss3.so`, `libnssutil3.so`, `libsmime3.so` and `libnspr4.so`, so the list names it now |

## Decision

**The controller pulls the worker image by its content tag before it builds it. The tag stays the only pin.**

1. **The order is present, pulled, built.** `Docker::ensure_image` answers a tag the engine has. Else it pulls
   `<AIR_VM_DOCKER_REGISTRY>/<AIR_VM_DOCKER_IMAGE>:<tag>`, tags the pulled image with the local name and uses it.
   Else it builds, as before.
2. **A pull that fails is a note and a build, not a refusal.** An unreachable registry, or a tag the registry does not
   hold yet, must not stop a start that can build. The note names the pull log, `docker-pull.log` beside the build
   log.
3. **The image carries its digest, and a pulled image is checked against it.** The Dockerfile writes the tag digest
   into the OCI label `org.opencontainers.image.revision` through the build argument `AIR_IMAGE_REVISION`. A pulled
   image whose label is not the tag digest is removed from the engine, noted as `docker_image_mismatch`, and the
   image is built. A registry mistake costs a build and never a worker on other bytes.
4. **The publish is opt-in, and it is for two platforms.** `AIR_VM_DOCKER_PUSH=1` makes the controller build, never
   pull, and then run `docker buildx build --platform linux/arm64,linux/amd64 --push` for the registry reference from
   the same context and build arguments, into `docker-push.log`. The one content tag is then an image index with an
   arm64 half for an Apple-silicon or arm64 Linux host and an amd64 half for an x86_64 host, and `docker pull` picks
   the engine's half. The local build stays for the engine's own platform. A failed publish is the refusal
   `docker_push_failed`, because the operator asked for it. `AIR_VM_DOCKER_REGISTRY=off` builds without a registry
   and refuses a publish.
5. **`status` says where the image came from.** The Docker row's `imageBuilt` becomes `imageSource`:
   `pulled`, `local` or `missing`. The engine cannot tell a pulled image from a built one, because both names exist
   after a pull and after a build with a push, so the controller keeps a pool-wide record, `docker-image.json`.
6. **The default registry is `registry.jetbrains.team/p/ij/containers-public`.** The pull runs under the pool-wide
   image lock with the build, so two starts pull once.
7. **The image drops `feh` and `wmctrl`.** The fluxbox overlay `background: unset` keeps the style from running
   `fbsetbg`, so no wallpaper setter is needed. `provision-guest` writes the same overlay on a Tart worker.
   Debian's template comments out `background: none`, and that value still runs `fbsetbg` on fluxbox 1.3.7.

## Consequences

- **A cold host pulls instead of building.** On 2026-09-29, `pool init` with the push built `air-ui-worker:c196a01159d1`
  and pushed it in 267 s in all, after the registry answered the pull with `MANIFEST_UNKNOWN` and the controller
  noted it and built. With the tag removed from the engine, `pool start air-docker-1` pulled the tag, created the
  container and validated the guest in 23 s, 10 s of which was the host build, and ran no build of the image;
  `status` said `image=pulled`. A `docker pull` of a tag of that size with only the base layer on the engine took
  9 s on this host. The corrected tag `77bf961a7ac3`, with `libnss3`, built, pushed, recreated the container
  and validated in 314 s. The class `AgentSessionToolWindowComposerUiTest` ran three times on it and passed 7 of 8
  each time, with 8 videos each. The three failures were driver races, not one fault: a `PerformTargetInput` that
  did not answer in 10 s, and twice an agent-picker list row that was not selected at mouse release, in two different
  scenarios. The same class passed 8 of 8 on `air-linux-1` the same hour. Whether the Docker worker races more since
  `libnss3` lets JCEF initialize is not measured; the previous image cannot run a worker, because `validate-guest`
  refuses it.
- **The image is smaller.** 691 MB installed against 715 MB before, and `docker image ls` says 970 MB for
  the tag. The 182 MB of Mesa and LLVM stay with any apt-installed X server.
- **Two levers stay open.** `ffmpeg` is 138 MB, and the recorder needs it only for libx264 and the fragmented MP4
  muxer; an `openh264` encoder in `avl-record` would drop the whole chain, and
  [ADR 0185](0185-the-recorder-encodes-its-own-video.md) made that change. GTK is 60 MB, and it is not a link-time
  need: on the pulled worker, `ldd` over the staged `libjcef.so`, `libcef.so` and `libawt_xawt.so` named `libatk`,
  `libatk-bridge` and `libatspi` and no `libgtk-3.so.0`. The JBR loads GTK at run time, and what it loses without
  it, font hinting and the native dialogs, is a lane run to measure before a drop.
- **A pulled image is trusted by its tag.** The label check limits a registry mistake to a refusal and a build. It
  does not defend against a registry that lies with a matching label; the registry is JetBrains-owned.
- **CI needs a write token only to push.** Read access is anonymous, so the lane itself needs no secret. A job that
  publishes a new tag runs `AIR_VM_DOCKER_PUSH=1 pool init` with a `docker login` to the registry.
- **A changed Dockerfile moves the tag, and the registry does not hold the new tag until someone publishes it.**
  Until then every host builds, as before this ADR. The revision label is written last in the Dockerfile, so a new
  digest keeps the package layer in the build cache.
- **The amd64 half has a controller since 2026-09-29.** The published index holds it, and the Dockerfile has no arm64
  step, so an x86_64 host pulls a working image. The controller assumed an arm64 guest in seven places. All seven now
  key on `GuestArch` (`crates/avl-base/src/config.rs`). The engine gate in `docker.rs` accepts the guest's
  architecture under its OCI and `uname -m` spellings. The guest agent and recorder labels (`agent_label`,
  `ui_lane_ide.bzl`) have `x86_64-unknown-linux-musl` builds of `vm-guest-agent` and `air-trace-record` beside the
  arm64 ones. The Node archive label (`node_archive_label`) picks `linux-x64`. The validator reads its loaders from
  its own `target_arch`. On a Linux x86_64 host `--config=air-lane-linux` selects the x86_64 guest and the host's
  own build. The two this ADR missed are `guest_jbr_platform` in `avl-lane/src/env.rs` and the agent CLI runtimes
  of `agent_cli_runtime.bzl`. The architecture follows the host and not the engine. A Docker worker is `x86_64` on a
  Linux x86_64 host and `arm64` everywhere else, and an engine of another architecture is a named refusal. The
  controller itself is Unix-only (`NOT_ON_WINDOWS`), so a Windows PC runs it in WSL2.
  [The Windows lane hand-off](../vm-windows-lane-plan.md) holds the open measurement. On 2026-09-29 the first two-platform publish of `77bf961a7ac3` from this Apple-silicon
  host took 378 s in all, with the arm64 layers from the BuildKit cache and the amd64 half built under emulation;
  `docker buildx imagetools inspect` then listed `linux/arm64` and `linux/amd64` under the one tag.

## Alternatives rejected

- **A second pin in `versions.env` for the registry image.** The tag is already a pin over every input, and a second
  one would be a copy that can drift from it.
- **A refusal when the pull fails.** A developer without network, or the first host after a Dockerfile change, must
  still get a worker. The build is the fallback, and the note says why it ran.
- **A `--push` flag on `pool init`.** One setting, `AIR_VM_DOCKER_PUSH`, covers `pool init`, `pool start` and
  `pool recycle`, which all build when the tag is missing.
- **A Packer pipeline for a Tart Linux image, now.** A Tart image is a whole disk of several GB, and the guest still
  writes its display units from `provision-guest`. That work waits until the guest agent owns the display, so the
  pipeline installs one owner and not the shell units.
