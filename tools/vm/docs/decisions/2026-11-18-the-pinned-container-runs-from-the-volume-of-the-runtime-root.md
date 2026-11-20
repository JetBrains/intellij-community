---
topic: testing
---

# The pinned Apple container runs from the volume of the runtime root

Date: 2026-11-18

## Status

Accepted. It amends item 2 of
[Apple container is a second engine of the Docker backend](2026-10-09-apple-container-is-a-second-engine-of-the-docker-backend.md):
the controller runs the real path of the pinned CLI only when the pin is on the volume of the runtime root. The code
is `crates/avl-vm/src/worker/container/install.rs`.

## Context

The pinned CLI is in the Bazel repository cache, `<Bazel user root>/cache/repos/v1/contents/<hash>/<uuid>`, and the
tool finds its plugins and its server from its real path. On 2026-11-18 the Mac Studio moved its Bazel user root to an
external NVMe volume over Thunderbolt (APFS, mounted `nodev, nosuid`; the owners were enabled later, and that did not
help). From there the launchd job `container-network-vmnet`, which the server registers, exited every 10 s with
`failed to create vmnet network with status vmnet_return_t(rawValue: 1001)`, and InternetSharing logged
`mis_network_get_security_domain: failed to get xpc client attributes`. The server then answered no `system status`,
`list` or `system stop`, and a lane failed with `subprocess_timeout` after 900 s.

The same install, copied with `ditto` to the system volume and started as a probe job with its own label, ran. The
signatures verified on both volumes. So the place of the install decides, and no mount flag was proven to be the
cause.

## Decision

1. **The rule is the device.** The controller compares `st_dev` of the install root and of the runtime root
   (`on_one_volume`). On another device it runs a copy of the install. The copy goes under the runtime root, so the
   rule copies exactly when a copy changes the volume. A rule of mount flags (`MNT_NOSUID`, not `MNT_LOCAL`) would
   name a cause that nobody measured, and the device is a fact a test can show: `/dev` is a file system of its own on
   macOS and on Linux.
2. **One copy per pin.** The copy is `<runtime root>/apple-container/<name of the install root>`, which is the uuid
   of the repository cache, so a bump of the pin gets a new copy. It holds `bin` and `libexec`, the layout the tool
   reads, and the record `air-copy.json` with the source, the size and the modification time of its CLI. A command
   whose source says the same as the record reuses the copy after one `stat`. A copy of another source under the
   same name is replaced.
3. **The copy is published once.** It is made in a temporary directory beside its place. The CLI, the server and the
   vmnet plugin must pass `codesign --verify --strict` there, else the command is refused `container_copy_failed`,
   exit 69, and the temporary directory goes. One rename publishes it. A command that loses the race uses the copy
   of the winner.
4. **Old copies go when the server is this copy's.** A gate whose server runs from the copy of this pin removes the
   copies of other pins and the temporary directories older than an hour. A copy that a running server uses is
   never removed, because only one server runs in a login session.
5. **`CONTAINER_BIN` keeps its meaning.** A CLI that it names runs as it is, from wherever it is.

## Consequences

- The first command of a pin on another volume copies about 420 MB once, with a note that names both places.
- A runtime root on the external volume itself gains nothing: the copy is then on that volume too.
- A server registered from the install on the external volume is of the same major version, so a gate uses it with
  the note of a foreign server. It does not answer, so boot it out of launchd before the first run with the copy.
