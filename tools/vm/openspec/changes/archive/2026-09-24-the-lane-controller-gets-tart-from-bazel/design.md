# The lane controller gets Tart from Bazel, and only when it asks

## Context

Tart ships as a signed `tart.app` in one `tart.tar.gz` per release. The bundle carries its signature, its
provisioning profile and the virtualization entitlements, so an unpacked copy runs from any directory. A pin removes
the manual install for every developer who runs a UI lane.

Bazel fetches an external repository when it analyzes a target that names it. The IDE sync and `bazel build //...`
analyze every main-repository target. A Tart label in the `data` of the `vm` binary downloads Tart on every Mac sync,
also for a developer who never runs a lane. A sync is not a request.

## Decisions

1. **Tart is pinned in Bazel.** [The Tart pin](../../../../tart.MODULE.bazel) declares `@air_tart` as an
   `http_archive` with a checksum. The archive is the release `tart.tar.gz`, byte-identical, on the Space file mirror
   `intellij-build-dependencies`. The cache redirector does not serve the GitHub release, so the URL reaches the
   mirror through the redirector.
2. **No target that `//...` analyzes names `@air_tart`.** The only label is a `manual` alias in
   [the workspace package](../../../../BUILD.bazel). So no sync and no wildcard build reaches the archive.
3. **The Tart gate asks Bazel.** `require_available` of the Tart backend resolves the executable before it runs
   `tart --version`. When `TART_BIN` names none, the controller runs one `cquery` for the alias. It roots the answer on
   the output base and keeps the real path for every later command. That `cquery` is the only fetch.
4. **The image pipeline runs the same Tart.** `vm.cmd image validate|build` passes the gate and puts the directory of
   the resolved Tart first on the `PATH` of the scripts.
5. **`TART_BIN` stays the override, and the floor stays its gate.** Tests keep the pin at or above `MINIMUM_VERSION`
   and keep `TART_MIN_VERSION` of `provision/versions.env` equal to it.

The Docker CLI, `docker-buildx` and Apple `container` follow the same rule. The
[worker lease and the warm daemon](../../../../spec/lane-controller.spec.md#the-worker-lease-and-the-warm-daemon)
state the contract.

## Alternatives rejected

- **A `data` label behind a `select` on macOS.** A Mac sync still analyzes `vm` and downloads Tart.
- **A Bazel toolchain.** The toolchain matches on a Mac, so the sync still fetches it.
- **A download in the controller, like `bootstrap-packer.sh`.** It copies the checksum, the downloader config and the
  cache that Bazel already has.
- **Packer from Bazel as well.** The redirector does not serve the Packer releases, so Packer stays with
  `bootstrap-packer.sh`.

## Consequences

- A developer installs no Tart for the default `nat` network. The first Tart command in a checkout downloads the
  archive, and the repository cache answers after that.
- A Tart upgrade is a pin bump: upload the release archive to the mirror, then change the version and the checksum.
- The real path keeps the bundle intact and keeps the `ps -o command=` spelling of a `tart run` stable.
- `softnet` stays a manual install, because it must be setuid root.
- One non-`manual` label that names `@air_tart` brings the sync download back. The comment in the Tart pin is the
  guard.
