# The image of a Docker Linux worker (`--backend docker`). The lane controller builds it; nobody builds it by hand.
# The controller passes two build arguments. `AIR_BASE_IMAGE` is `DOCKER_BASE_IMAGE` from
# `community/tools/vm/provision/versions.env`: Ubuntu 26.04, pinned by digest.
# `AIR_GUEST_PACKAGES` is `GUEST_PACKAGES` from `crates/avl-vm/src/worker/docker.rs`.
# The image carries Node 24.19.0 from nodejs.org in `/usr/local`. The checksums are those of
# `plugins/air/tests/integration/headless/acp/runtime-installation/runtime_test_dependencies.bzl`.
# The `NODE_MAJOR` pin in `provision/versions.env` must name the same major, and a unit test checks it.
# The image tag is a digest over this file, `air-display`, the base image and the package list.
# A change to one of them gives a new tag, and the controller then builds the image again.
# `AIR_IMAGE_REVISION` is that digest. The label holds it, so the controller can check an image it pulled from the
# registry by its tag before a worker runs on it (ADR 0184).
# The base image has the account `ubuntu` at uid 1000, on Ubuntu 24.04 and 26.04 alike. The worker account `admin`
# needs that uid, so the build removes `ubuntu` first.
# The image installs no recommended package. The fluxbox overlay below keeps the style from setting a wallpaper, so
# fluxbox never runs `fbsetbg`, which needs a wallpaper setter (`feh`) and opens an `xmessage` without one.
# ADR 0183 (`community/tools/vm/docs/decisions/`) records why a Linux worker may be a container.
ARG AIR_BASE_IMAGE

# The Node archive is fetched and extracted on the build platform, natively. A publish builds the other platform
# under emulation, and `tar` fails there with "Function not implemented". The files of an archive do not depend on
# the platform that extracts them.
FROM --platform=${BUILDPLATFORM} ${AIR_BASE_IMAGE} AS node
ARG TARGETARCH
# A change of the version needs the two checksums of that version below.
ARG NODE_VERSION=24.19.0
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl \
 && rm -rf /var/lib/apt/lists/*
# BuildKit sets `TARGETARCH`. An architecture without a pinned checksum stops the build.
RUN set -eu; \
    case "${TARGETARCH}" in \
      amd64) node_arch=x64; node_sha256=f625d97cd707df4ff96254916fbc5ff014f09c09effe5a1e0ca8f6d41a8789d4 ;; \
      arm64) node_arch=arm64; node_sha256=d28c8a5bf0a808f0ed434a1dce8c54ae98f0371c0bd86ac58abc613f73e6643f ;; \
      *) echo "no pinned Node archive for TARGETARCH=${TARGETARCH}" >&2; exit 1 ;; \
    esac; \
    archive="node-v${NODE_VERSION}-linux-${node_arch}.tar.gz"; \
    curl --fail --silent --show-error --location --output "/tmp/${archive}" "https://nodejs.org/dist/v${NODE_VERSION}/${archive}"; \
    printf '%s  %s\n' "${node_sha256}" "/tmp/${archive}" | sha256sum --check -; \
    mkdir -p /opt/node; \
    tar --extract --gzip --file "/tmp/${archive}" --directory /opt/node --strip-components=1 --no-same-owner; \
    rm "/tmp/${archive}"

FROM ${AIR_BASE_IMAGE}
ARG AIR_GUEST_PACKAGES
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
 && apt-get install -y --no-install-recommends sudo ca-certificates xz-utils ${AIR_GUEST_PACKAGES} \
 && rm -rf /var/lib/apt/lists/*
COPY --from=node /opt/node/ /usr/local/
RUN /usr/local/bin/node --version
RUN printf 'background: unset\n' > /etc/X11/fluxbox/overlay
RUN userdel --remove ubuntu && useradd --uid 1000 --create-home --shell /bin/bash admin \
 && printf 'admin ALL=(ALL) NOPASSWD:ALL\n' > /etc/sudoers.d/admin && chmod 440 /etc/sudoers.d/admin
# The build sets the mode of the entrypoint, so the controller writes the build context as plain files on every host.
# `--chmod` needs BuildKit, which is the builder of every engine the controller supports.
COPY --chmod=0755 air-display /usr/local/sbin/air-display
# Last, so a new digest leaves the package layer in the build cache.
ARG AIR_IMAGE_REVISION
LABEL org.opencontainers.image.revision=${AIR_IMAGE_REVISION}
ENTRYPOINT ["/usr/local/sbin/air-display"]
