---
topic: testing
---

# The guest reads no checkout

Date: 2026-10-10

## Status

Accepted. It amends point 4 and point 5 of [ADR 0186](0186-a-windows-host-runs-the-controller-natively.md). The
path map of point 4 holds one share now, the Bazel output root. The guest-built runfiles tree of point 5 is the rule
on every host now, and not only on a Windows host. It also amends the copy-free guest run of
[the VM guide](../vm-ui-tests.md#how-a-run-reaches-the-guest): the guest gets copies of a few source runfiles.
[A lease prefers the warm slot](2026-10-10-a-lease-prefers-the-warm-slot.md) builds on this record.

## Context

The pool is per machine, and five checkouts share its four slots. A slot moved between checkouts with a container
recreate, because one argument of `docker create` named the checkout: the repository share. A Tart VM restarted on
the same switch. The guest rebuilt the checkout path as a directory of symlinks onto that share, and one receipt per
worker recorded one checkout.

The user asked on 2026-10-10 why the guest knows the checkout at all. A survey of every guest read of the checkout
found only these:

| read | purpose |
| --- | --- |
| 70 source files of the daemon runfiles: 2 `pnpm-lock.yaml`, `intellij.idea.community.main.iml`, `java/mockJDK-*` and `java/jdkAnnotations`, 11.3 MB | `DeclaredCliRuntime.kt` reads the lock files. No UI-lane reader of the other 68 was found |
| `<checkout>` as a directory | `-Didea.home.path=<checkout>` of the daemon JVM. The platform refuses a home that is not a directory |
| `<checkout>` as `idea.dev.project.root` | the lane IDE lists `<checkout>/out/dev-data`, which was the guest-local `out` already |
| `<checkout>/.git` and the parity marker | the mount probe and the readiness probe of the controller |

Of the 70 files, 2 are MANIFEST targets in the checkout. The other 68 are targets under
`<output_base>/external/community+`, and that directory is a link into the checkout. So a target name alone does not
tell a share target from a checkout target, and only its real path does.

Everything else the guest needs is a Bazel output on the Bazel share, which is one per machine already. Or it is a
copy on the guest disk that the stage makes. The guest agent never named the checkout.

## Decision

**The guest reads no checkout.** The repository share goes, and a worker serves every checkout of the machine.

1. **The source runfiles are staged.** The guest builds its own runfiles tree from the MANIFEST on every host, with
   the `runfiles-tree` verb, at `<vmData>/runfiles/<digest>`.
   - `HostRunfiles::guest_tree` resolves each absolute target through its links. A target whose real path lies in
     the checkout is a staged runfile. Its MANIFEST line leaves the request, and the request names the logical path,
     the sha256 and the length. A directory there is one staged runfile per file.
   - The bytes travel on the stdin of the verb, after the JSON line of the request. The first request carries no
     bytes, so a tree of the same digest costs the MANIFEST text only. A guest without the tree refuses it with
     `runfiles_staged_bytes_missing`, and the second request carries the bytes. The guest checks the length and the
     sha256 of each copy.
   - The tree digest covers the MANIFEST, the path table, the staged runfiles and the copy rule. The schema of the
     verb is 4. The copy of a `node_modules` package directory is a Windows rule now (`copyPackageStores`), because a
     Unix host has no junctions.
   - The plan said that the `stage` verb carries the source runfiles. It does not: a start skips the `stage` verb
     when the runtime generation is reused, and the runtime digest does not cover the descriptor `data`. A change of
     a lock file must not restage 852 MiB of jars.
2. **The home of the daemon JVM is guest-local and empty, and every derived path is explicit.** The daemon gets
   `-Didea.home.path=<generation>/home`, an empty directory of its staged generation, which launch-prep makes. It gets
   `-Didea.config.path`, `-Didea.system.path` and `-Didea.log.path` under `<vmOut>/daemon`, and
   `-Dide.starter.out.dir=<vmOut>` on every backend. `AirUiDaemonServer.requireStagedRuntime` refuses a home outside
   the generation, so a checkout path that reaches the guest fails at the daemon start. The plan named
   `<runtime root>/home`. The home is inside the generation instead, so the existing `air.ui.daemon.runtime.root`
   property is the bound of the check. `AirIdeLaunch` passes the same home as `idea.dev.project.root`.
   `ControllerBoot.host_repo` is `guest_home` now.
3. **The repository share goes.** `shares()` answers the Bazel share only, and every renderer follows. These are the
   Docker bind mounts, the Tart `--dir`, the Parallels shared folders, the `container-linux` `--ro` pairs, and the Lima
   `share_outside_home` check. The parity script keeps the Bazel root link and the writable roots, and makes no
   checkout directory. The marker moves to `<vmData>/.air-vm-parity.json`. The marker and the receipt
   `guest-init.json` lose `hostRepo`, `guestRepo` and `repoShare`. `GuestPaths` maps the Bazel root only, so a
   checkout path is refused `guest_path_unmapped`. A set `AIR_VM_REPO_SHARE_NAME` is refused `invalid_environment`.
   `AIR_VM_HOST_REPO` stays, because the host build, `suites`, `--changed` and the `tree` line of the report read the
   checkout on the host.
4. **`status` keeps `hostRepo`** as the checkout that the command ran from. The Parallels row loses `repoShare`. The
   Tart restart compares the Bazel root of the receipt only.

## Consequences

- **The first lease after the change recreates each container once.** The create argument list differs from every
  recorded one. The volume keeps the staged runtime. A Tart worker restarts once, and a receipt of an older
  controller reads, so the readiness probe of the new marker decides the provision. The `@controller-boot` digest
  changes, so every daemon restarts once.
- **A worker serves every checkout of the machine.** The create argument list names no checkout, so a switch to
  another checkout recreates no container and restarts no Tart VM. The launch digest still differs per checkout,
  because the MANIFEST names the output base of each checkout. So the daemon restarts on a switch, as before.
- **The guest can read no working tree.** That isolation is stronger than before. An older Tart VM keeps the old
  parity directory of links into a share that is gone. A Parallels VM keeps the share that an older controller
  declared, until `prlctl set <vm> --shf-host-del air-macos-repo` removes it.
- **A change of a staged runfile restarts the daemon.** The tree digest covers the bytes, so the launch digest moves.
  Before, the guest read a lock file live through the share. The staged runfiles are lock files, an `.iml` file and
  mock JDKs, which change seldom.
- **Each daemon start sends the MANIFEST text,** about 400 KB for the UI daemon, which a Windows host sent before
  too. A cold tree adds the 11.3 MB of the staged runfiles once.
- **Not verified here.** No lane ran on a worker with this change. The live runs of the plan, `run --lane ui` and
  `run --lane ui-real` on Docker and the two-checkout lease, are the next check. A UI-lane class that reads a file by
  its home path fails there and names the file.
- **The follow-up.** `ui/BUILD.bazel` lists `intellij.idea.community.main.iml` and `@community//java:mockJDK` in the
  descriptor `data`, and no UI-lane reader was found. Their removal makes the staged runfiles smaller. It needs a lane
  run of its own, so it is not part of this change.
