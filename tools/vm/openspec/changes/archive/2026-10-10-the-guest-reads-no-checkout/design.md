# The guest reads no checkout, and one worker serves every checkout of the machine

## Context

The worker pool is per machine, and several checkouts share its slots. A repository share makes the checkout part of
each worker. Then a move of a slot to another checkout creates the Docker container again, or restarts the Tart VM.

The guest needs little of the checkout. The runfiles of the daemon hold some source files of the checkout, such as
lock files, one `.iml` file and mock JDKs. The other reads are paths derived from the checkout. These are the home of
the daemon, the dev project root of the IDE, and the probes of the parity layout. Every other guest input is a Bazel
output on the Bazel share, which is one per machine already, or a copy on the guest disk.

A MANIFEST target name alone does not tell a share target from a checkout target. A target under the external
repository directory of the output base can be a link into the checkout. Only the real path tells.

The contract is in
[the daemon build and the launch identity](../../../../spec/lane-controller.spec.md#the-daemon-build-and-the-launch-identity).

## Decisions

**The guest reads no checkout.** The repository share goes, and a worker serves every checkout of the machine.

1. **The guest builds its own runfiles tree from the MANIFEST on every host.** The `runfiles-tree` verb builds it at
   `<vmData>/runfiles/<digest>`.
   - `HostRunfiles::guest_tree` resolves each absolute target through its links. A target whose real path lies in
     the checkout is a staged runfile. The request names its logical path, its sha256 and its length.
   - The bytes travel on the standard input of the verb, after the request line. The first request carries no bytes,
     so a known tree costs the MANIFEST text only. A guest without the tree refuses `runfiles_staged_bytes_missing`,
     and the second request carries the bytes. The guest checks the length and the sha256 of each copy.
   - `tree_digest` in [runfiles.rs](../../../../crates/avl-wire/src/runfiles.rs) names the tree from the MANIFEST,
     the path table, the staged runfiles and the copy rule. The host and the guest compute the same name, so the host
     knows the root before it asks. A reply with another root refuses `guest_runfiles_mismatch`, and a bad reply
     refuses `guest_runfiles_protocol`.
   - The verb builds the tree beside its final name and publishes it with one rename. It owns its destination: it
     keeps the tree it answers and the newest other tree, and removes every other entry.
2. **The home of the lane daemon is a guest directory, and every derived path is explicit.** The home is `home` in
   the state directory of the daemon (`guest_home`), and the lane IDE gets it as `idea.dev.project.root`. The home
   is not below the IDE root, because `ide-gc` reads each directory there as a context of the lane IDE. No guest path
   derives from a checkout.
3. **The repository share goes.** `shares()` in [share.rs](../../../../crates/avl-host-sys/src/share.rs) answers the
   Bazel share only, and every backend renders that one share. The parity layout makes no checkout directory, and
   its marker and receipt name no checkout. `GuestPaths` in [paths.rs](../../../../crates/avl-host-sys/src/paths.rs)
   maps the Bazel root only, so a checkout path refuses `guest_path_unmapped`. A set `AIR_VM_REPO_SHARE_NAME`
   refuses `invalid_environment`. `AIR_VM_HOST_REPO` stays, because the host build, `suites`, `--changed` and the
   report read the checkout on the host.
4. **`status` keeps `hostRepo`** as the checkout that the command ran from.

## Consequences

- A worker serves every checkout of the machine. A switch to another checkout creates no container and restarts no
  Tart VM. The launch digest still differs per checkout, because the MANIFEST names the output base of each checkout.
  So the daemon restarts on a switch.
- The guest can read no working tree.
- A change of a staged runfile restarts the daemon, because the tree digest covers the bytes and moves the launch
  digest. The staged runfiles change seldom.
- Each daemon start sends the MANIFEST text. A cold tree also sends the staged runfiles once.
- A lease can prefer a warm slot across checkouts. See
  [a warm daemon survives a lease release](../2026-10-10-a-warm-daemon-survives-a-lease-release/design.md).
