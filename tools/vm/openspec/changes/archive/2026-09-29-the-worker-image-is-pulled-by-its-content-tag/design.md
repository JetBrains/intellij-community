# The controller pulls the worker image by its content tag before it builds it

## Context

A Docker worker runs an image that the controller builds from [the Dockerfile](../../../../docker/Dockerfile). The tag
is `air-ui-worker:<12 hex digits>`. It is a digest over the Dockerfile, its entrypoint, the base pin and
`GUEST_PACKAGES`. A cold host builds the image, and that build takes minutes.

The tag is a digest of every input. So the same tag names the same bytes on each host. A pull by that tag gives a worker
the same image as a local build. The tag is the only pin.

The registry `registry.jetbrains.team/p/ij/containers-public` lets a lane host pull without a login. A push needs a
login.

The requirements are in [the daemon build and the launch identity](../../../../spec/lane-controller.spec.md#the-daemon-build-and-the-launch-identity).

## Decisions

1. **The order is present, pulled, built.** `Docker::ensure_image` in
   [docker.rs](../../../../crates/avl-vm/src/worker/docker.rs) uses a tag that the engine has. Else it pulls
   `<AIR_VM_DOCKER_REGISTRY>/<AIR_VM_DOCKER_IMAGE>:<tag>` and tags the image with the local name. Else it builds.
2. **A failed pull is a note and a build.** An unreachable registry, or a tag that the registry does not hold yet,
   does not stop a start that can build. The note names `docker-pull.log`.
3. **The image carries its digest, and the controller checks a pulled image against it.** The Dockerfile writes the
   tag digest into the label `org.opencontainers.image.revision` through the build argument `AIR_IMAGE_REVISION`. A
   pulled image with another label gets the note `docker_image_mismatch`. The controller removes it and builds the
   image.
4. **The publish is opt-in, for two platforms.** `AIR_VM_DOCKER_PUSH=1` makes the controller build, never pull. Then
   it pushes the same context for `linux/arm64` and `linux/amd64` under the one tag. `docker pull` then picks the half
   of the engine. A failed publish is the refusal `docker_push_failed`. `AIR_VM_DOCKER_REGISTRY=off` builds without a
   registry and refuses a publish.
5. **`status` says where the image came from.** The Docker row says `pulled`, `local` or `missing`. The engine cannot
   tell a pulled image from a built one. So the controller keeps a pool-wide record, `docker-image.json`.
6. **The default registry is `registry.jetbrains.team/p/ij/containers-public`.** The pull runs under the pool-wide
   image lock with the build, so two starts pull once.
7. **The image has no wallpaper setter.** The fluxbox overlay `background: unset` stops the style from running
   `fbsetbg`. So the image installs neither `feh` nor `wmctrl`.
8. **The guest architecture follows the host.** A Docker worker is `x86_64` on a Linux x86_64 host and `arm64` on
   every other host. `GuestArch` in [config.rs](../../../../crates/avl-base/src/config.rs) carries the choice. An
   engine of another architecture is a named refusal.

On the Apple `container` engine the image comes from the file mirror, as
[the Docker backend decision](../2026-11-18-the-docker-backend-runs-on-the-host-engine-or-apple-container/design.md)
states.

## Alternatives rejected

- **A second pin in `versions.env` for the registry image.** The tag is already a pin over every input, and a copy can
  drift from it.
- **A refusal when the pull fails.** A host without network, or the first host after a Dockerfile change, must still
  get a worker.
- **A `--push` flag on `pool init`.** One setting covers `pool init`, `pool start` and `pool recycle`, which all build
  when the tag is missing.

## Consequences

- A cold host pulls in seconds instead of a build of minutes.
- The label check turns a registry mistake into a note and a build. It does not defend against a registry that lies
  with a matching label. JetBrains owns the registry.
- The lane needs no secret, because a pull is anonymous. Only a job that publishes a new tag needs a `docker login`.
- A changed Dockerfile moves the tag. Each host builds until somebody publishes the new tag. The Dockerfile writes the
  revision label last, so a new digest keeps the package layer in the build cache.
- GTK stays in the image. The JBR loads it at run time, so a drop needs a lane run that measures the loss first.
