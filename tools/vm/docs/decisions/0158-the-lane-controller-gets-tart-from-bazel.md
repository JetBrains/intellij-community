---
topic: testing
---

# 158. The lane controller gets Tart from Bazel, and only when it asks

Date: 2026-09-24

## Status

Accepted.

## Context

The vm-lane controller drove the `tart` that the developer installed, usually with Homebrew. It checked only a
floor, `MinimumVersion` 2.32.1, with no upper bound. Every developer who ran a UI lane had to install Tart first.
The floor had two copies, `MinimumVersion` and `TART_MIN_VERSION` in `provision/versions.env`, and no check
compared them.

Tart ships as a signed `tart.app` in one `tart.tar.gz` per release. Homebrew only unpacks the bundle and adds a
wrapper that runs `tart.app/Contents/MacOS/tart`. The bundle carries its signature, its provisioning profile and
the virtualization entitlements, so an unpacked copy runs from any directory.

A Bazel pin is the obvious home, but the usual wiring downloads too much. Bazel fetches an external repository
when it analyzes a target that names it. The IDE sync (`.bazelproject`, `targets: //...`) and
`bazel build //...` analyze every main-repository target. A Tart label in the `data` of the `vm` binary would
download Tart on every Mac sync, also for a developer who never runs a lane.
[build ADR 0009](../../../../../build/decisions/0009-bun-pins-in-the-ultimate-module.md) records the same problem for
bun: a sync is not a request.

## Decision

1. **Tart is pinned in Bazel.** `plugins/air/tests/integration/vm-lane/tart.MODULE.bazel` declares
   `@air_tart` as an `http_archive` with a checksum, and the root `MODULE.bazel` includes it. The archive is the
   release `tart.tar.gz`, byte-identical, on the Space file mirror `intellij-build-dependencies`. The cache
   redirector does not serve `github.com/cirruslabs/tart`, and the downloader config routes the Space mirror
   through the redirector. The first pin is Tart 2.37.0.

2. **No `BUILD` file names `@air_tart`.** `//...` covers only the main repository, and `.bazelproject` does not
   list `@air_tart//...`. So no sync and no wildcard build can reach the archive.

3. **The Tart gate asks Bazel.** `require_available` in `crates/avl-worker/src/tart.rs` resolves the executable
   before it runs `tart --version`. When `TART_BIN` names none, it runs one `cquery` for `TART_LABEL`
   (`avl_base::config`), roots the answer on the output base, and keeps the real path for every later
   command. That `cquery` is the only fetch. Every worker and `status` operation passes the gate before its
   first Tart command, so a usage refusal asks Bazel nothing. `avl_host_sys::guest::external_file` is the
   resolver, and the Linux worker's Node archive uses it too.

4. **The image pipeline runs the same Tart.** `vm.cmd image validate|build` passes the gate, and puts the
   directory of the resolved Tart first on the scripts' `PATH`. The scripts and the Packer Tart plugin call
   `tart` from `PATH`.

5. **`TART_BIN` stays the override, and the floor stays its gate.** Tests keep the pin at or above
   `MinimumVersion` and keep `TART_MIN_VERSION` equal to it.

## Consequences

- A developer installs no Tart for the default `nat` network. The first Tart command in a checkout downloads
  about 23 MB. After that the repository cache answers, also after `bazel clean --expunge`.
- A Tart upgrade is a pin bump: upload the release archive to the mirror, then change the version and the
  checksum in `tart.MODULE.bazel`.
- The real path keeps the bundle intact. It also keeps the `ps -o command=` spelling of a worker's `tart run`
  the same between two commands. A worker that another Tart started stays recognized, because the identity
  compares the live command line with the recorded one.
- `softnet` stays a manual install. It must be setuid root, which a download cannot do. Only
  `AIR_VM_NETWORK=softnet` needs it.
- One `BUILD` label that names `@air_tart` brings the sync download back. No test catches that, because a
  sandboxed test cannot read every `BUILD` file of the repository. The comment in `tart.MODULE.bazel` is the
  guard.

## Rejected alternatives

### A `data` label behind a `select` on macOS

It keeps Linux and Windows hosts from downloading, but a Mac sync still analyzes `vm` and downloads Tart.

### A Bazel toolchain

Toolchain resolution skips a toolchain whose platform does not match. On a Mac the Tart toolchain matches, so
the sync that analyzes `vm` still fetches it. It costs a custom rule and removes nothing that the `select`
does not.

### A download in the controller, like `bootstrap-packer.sh`

It is lazy too, but it copies what Bazel already does: the checksum, the downloader config, the redirector and
the cache. The Bazel pin also puts the version where a repository-wide search for pins finds it.

### Packer from Bazel as well

The redirector does not serve `releases.hashicorp.com/packer`. Packer would need its own mirror upload, so it
stays with `bootstrap-packer.sh` for now.
