---
topic: testing
---

# 108. The guest half of the image pipeline is Go

Date: 2026-08-25

## Status

**Superseded on 2026-09-26 by [ADR 0059](0059-the-ui-lane-tooling-is-rust.md)** for the language: the verbs below
and the closed set of exit statuses are now the Rust guest agent's, and the Go paths below are history.

**Superseded in part on 2026-10-08 by [ADR 0210](0210-the-docker-image-carries-node-and-the-tart-linux-worker-is-retired.md)**: the guest agent has no Node staging
and no `provision-guest` verb, and `validate-guest` has no Node check.

Accepted. Amends [ADR 0105](0105-the-ui-lane-controller-is-go.md), which recorded `provision/` — the Packer
and shell image pipeline — as the part of the skill directory that was never part of the Go port. That was
true of the port 0105 describes and is no longer true of the pipeline: every guest-side step it had —
provisioning a macOS golden image and proving it, provisioning a Linux worker and proving it — is now a verb of
the same binary.

Describes four verbs of `//plugins/air/tests/integration/vm-lane/cmd/vm-guest-agent` — `provision-image` and
`validate-image`, which run once inside a Packer build, and `provision-guest` and `validate-guest`, which the
controller runs on every Linux worker's boot. After all four, nothing under
`.agents/skills/vm-ui-tests/provision/` runs *inside* a guest any more: what is left there is the Packer
template and its pins, the host-side shell the pipeline is driven by, and the publishability gates that argue
against the image's own filesystem. What the pipeline builds and what the finished image may contain
stays in that directory's [`README.md`](../../provision/README.md); how a
lane uses a worker cloned from the result is [the VM guide](../vm-ui-tests.md).

## Context

The macOS golden image is built by one Packer `build` block whose provisioners were, in order: install and
pin the agents, sanitize the account, prove the result, seal it. The two middle-weight steps —
`provision/scripts/provision-guest.sh` (107 lines) and `provision/scripts/validate-guest.sh` (54) — were
shell run over SSH inside the guest, and they are deleted.

They were the last untyped code in the macOS image build that decides what the image *contains*, and the
validator was the worse of the two. It was a bare `[[ … ]]` cascade under `set -euo pipefail`, and **a failed
`[[ ]]` prints nothing at all**: a
base that shipped a different macOS, a Node the pin no longer named, an `npm install --global` that resolved
another build — each of those ended a 3-minute Packer run (and, on a cold host, the 13-hour base pull before
it) with one nonzero status from an unnamed line, and the operator's only next move was to re-run the checks
by hand over SSH. Nothing could be asserted about it either: reaching a single one of those refusals needed a
macOS guest and a whole image build.

The pins had already started to drift the way a second copy of a default does. `versions.env` pins
`NODE_MAJOR=24`, the Packer template passes it, and *both* scripts additionally spelled `node@24` into their
own PATH — so the Node major lived in three places and only two of them were behind a `:?` guard.

The Linux half was in the same state, one layer further out. `provision/linux/provision-guest.sh` (119 lines)
and `validate-guest.sh` (128) belong to no image build: the controller pushes them into a freshly cloned public
Ubuntu and runs them as root on *every* boot, so they are what decides what a Linux worker *is*. That
validator had gone further than the macOS one and invented a numeric vocabulary — 65, 69, 70, 71 and 72 — for
its five failure classes, which is exactly the shape the closed-set section below argues against. One of those
numbers covered two conditions that need different repairs, and could not say which one an operator had.
Reaching any of the five needed a Linux worker and a boot.

## Decision

**The guest half of the macOS image pipeline is two verbs of the lane's guest agent**, uploaded into the
build by a Packer `file` provisioner and invoked as `shell` provisioners either side of the sanitize step.

The reason is types, not deployment — the same argument `cmd/vm-guest-agent/main.go` already makes for the
run supervisor, applied to the one part of the guest that was still exempt from it. `image.go`,
`provision.go` and `validateimage.go` are 1 353 lines against the scripts' 161, and the difference is almost
entirely what a refusal now says: every check names what it looked at, what it found instead, and which
class of thing is wrong. 40 hermetic test functions reach those refusals in milliseconds on any host, against
a stubbed image and a recorded command transcript, which is coverage the shell version could not have had at
any price.

### The image pipeline takes a Bazel dependency; the image itself takes none

`image build` and `image validate` now need a working `./bazel.cmd` in this checkout, which they did not
while every provisioner was a file in `provision/`. Before, a host with Tart, Packer, `curl` and `jq` could
build an image. Now both commands build
`//plugins/air/tests/integration/vm-lane/cmd/vm-guest-agent:vm-guest-agent-darwin-arm64` first and hand
Packer its path.

**`image validate` too, and that is not an over-reach:** Packer's `file` provisioner stats its source during
`packer validate`, so a template naming a binary that does not exist is not a valid configuration and there
is no configuration check that can skip the question. A `validate` that reported success for a build that
cannot start would be the dishonest half of the pair.

`AIR_VM_GUEST_AGENT_SOURCE` overrides the build with a hand-built binary. Deliberately that variable and not
a second name for it: it is what the controller already reads for exactly this purpose when installing an
agent into a worker, and an operator who set one of two names would drive the workers with one binary and
build the image with another.

**The image still contains no checkout, no Bazel and no Go toolchain.** The binary is a build *input*: it is
uploaded to `/tmp/air-vm-guest-agent`, used twice, and removed at the end of the `validate-image`
provisioner — before `seal-public-image.sh` runs, and therefore before anything publishable exists. It is
removed there rather than by the verb itself because the verb that would delete it is not the last verb to
run it.

Where the resolution happens is stated where it fails: `provision/scripts/lib.sh` owns
`resolve_guest_agent_binary` and states what the prerequisite costs an operator, and the
`guest_agent_binary` variable in `air-macos.pkr.hcl` states why the pipeline took the dependency on. This ADR
is why it is a dependency worth having: the image is provisioned and then proved by the same typed guest code
the controller drives every worker with, instead of by a second, untyped copy of it that repeated the
controller's own version defaults.

### The agent's exit statuses are a closed set, and a check class is never a status

`wire/supervisor.AgentExitStatuses` is the whole set of statuses `vm-guest-agent` leaves with, and adding to
it is a wire change. The two new verbs answer only statuses already in it —
`AgentExitRefused` (70) for every check that fails, `AgentExitUsage` (64) for an argv the verb cannot act on
at all.

The set has to be closed because the host reads a number as a diagnosis. `host/guest.exitOr` keeps a
refusal's status the guest's own, so a number chosen inside a VM reaches an operator's shell as `vm`'s exit
status; and `host/guest.agentRefusal` reads a 64 as the dispatch's own usage block and therefore as *an agent
older than this controller*. That inference is sound only while every verb failing *inside* the agent answers
70 instead. A verb that invented a status of its own would make both readings ambiguous.

**So there is no status per failure class.** A drifted pin, a base that stopped shipping a tool, a setting
provisioning did not make and an image that must not be published are four different diagnoses and they are
carried in `EnvelopeError.Code` and `EnvelopeError.Message`, which travel whole. Packer's `shell` provisioner
treats every nonzero status alike, so a second numeric vocabulary would distinguish nothing for the one
caller these verbs have.

That rule is written down here because it had already been broken once, one layer out.
`provision/linux/validate-guest.sh` spent **65** on a bad argument, **69** on a display that did not answer,
**70** on a window manager — for two conditions at once — **71** on a run-supervisor Node that does not run
and **72** on an `ldd` that found an unresolved shared object. Those numbers were not a design choice: a
guest *script* has no envelope to speak in, and the host withholds every non-envelope shape of a script's
stderr by contract
(`host/guest.Raw`), so the exit integer was the only channel the checks had left. Nothing in Go named any of
them, so a reader of `exited with 72` had to open the shell to learn what failed. **Moving a self-check
into the agent removes the reason those codes existed, so neither pair has an equivalent of them** — and
two tests pin that it stays that way: `wire/supervisor` asserts the set is closed and declared exactly
once, and
`host/guest` asserts that every status in it is one the controller names *and that 71 and 72 are still named
by nothing*, so the first assertion is load bearing rather than a tautology over a set that happens to fit.
Those two numbers keep the assertion because they were the last two to go; the Linux port took all five with
it, and the section below records that.

### The Linux boot's guest half is two more verbs

**`provision-guest` and `validate-guest`** replace `provision/linux/provision-guest.sh` (119 lines) and
`validate-guest.sh` (128), which are deleted. They are the same decision as the image pair with one thing
changed: these run on *every* boot, driven by the controller as root against a public base image cloned by
digest, so `guest/linux.go` states the pair's idempotence rule at each of the five provisioning steps and says
which two are guarded and why. Three files replace the two scripts — `linux.go` for what the pair shares, and a
file per verb — and they are several times the scripts' 247 lines, which buys hermetic tests that reach every
refusal of both verbs on a host with no `apt-get`, no systemd, no X server and no root.

The argv is positional — `provision-guest WORKER_DATA DISPLAY USER SHARE_MOUNT PACKAGE...` and
`validate-guest DISPLAY NODE_BINARY IDE_ROOT` — where the image pair takes named flags. That is the same
question answered differently rather than an inconsistency: the image pair's five bare semantic versions have
nothing in their shape to tell them apart, which is the whole argument `parseImageVersions` makes for flags,
while an absolute path, a `:88`, an account name and an absolute path are four shapes no transposition
survives. Three of the four are checked, so a swap refuses by name instead of being provisioned. The package
list arrives as the trailing arguments, which is what keeps `linux.GuestPackages` the one list. The screen
geometry is the one value no argument carries: it is read from the guest's own `AIR_VM_SCREEN`, because nothing
on the host sets it and a controller-side default for it would create the second place for a default to be
wrong that a single argv builder exists to avoid.

**The five numbers are gone.** These verbs answer `AgentExitRefused` and `AgentExitUsage` and nothing else,
like the image pair, and each check answers a code naming the class of thing that is wrong with a message
carrying what it looked at and what it found: `linux_display_not_answering`, `linux_window_manager_missing`,
`linux_window_manager_stale`, `linux_node_not_runnable`, `linux_shared_object_unresolved`,
`linux_ide_root_unreadable`, `linux_package_install_failed`, `linux_netplan_dropin_unwritable`,
`linux_display_unit_unwritable`, `linux_display_service_not_enabled`, `linux_worker_directory_not_created`,
`linux_provision_not_root`. A malformed display, an empty user, an empty package list and a bad screen geometry
refuse as usage, before the verb touches the guest.

**One of those refusals is a distinction the shell could not make.** `validate-guest.sh` exited **70** both
for "no window manager is registered on this display" and for "a `_NET_SUPPORTING_WM_CHECK` property left
behind by a window manager that has exited", because one integer was the entire channel it had. Those are
different repairs — the first is `air-fluxbox.service` to look at, the second is a worker to recycle — and the
verb now says which, as `linux_window_manager_missing` and `linux_window_manager_stale`. The two hops are read
exactly as `XorgWindowManagerHandler.isWmRunning` reads them, unchanged: the root property names a window, and
that window must point the same property back at itself.

**And then the distinction paid for a fix the shell version could not have been given.** Because the two
conditions have separate names, one of them could be given separate treatment: the *absent* property is waited
for — the same one-probe-a-second minute `provision-guest` spends on the X server — while the *stale* one
still refuses on the first pass. That mattered immediately. On 2026-08-25 a `pool recycle` of `air-linux-2`
refused `linux_window_manager_missing` on a guest that was healthy thirty seconds later, with the same fluxbox
pid and an unbroken journal: `air-fluxbox.service` is `Type=simple` and `Requires=air-xvfb.service`, so
systemd starts fluxbox when Xvfb is *forked*, and a first boot's `provision-guest` returns on its first
display probe. The retired script asked once too, so this was a race the port inherited; what the port added
was a name to hang the fix on. Waiting on the stale case as well would have been the same mistake in reverse —
a real fault converted into a sixty-second silence — which is why the absent case is a sentinel in the code
rather than a refusal whose code a loop compares against a string.

#### The cost this ADR quoted for the port was wrong by two thirds

The bullet this section replaces said that porting the pair "moves the agent install into the boot path of
every Linux worker", and called that a change to how a lane starts rather than a language change. That reading
is withdrawn. It is worth saying where it came from, because the mistake is repeatable: it was read off
`host/guest.InstallAgent`'s two prerequisites without checking either against the start path.

- **`EnsureHostPaths` was already there.** It sits in `startTart` at `host/worker/startstop.go:460`, before
  `tart run` is spawned, and it has to: the shares are arguments to that process, so a boot that had not
  resolved them boots a worker with two empty-path shares. A Linux worker is a `tart` clone and takes that same
  path. The install needed nothing added for it.
- **The lease-operation lock was already held.** `InstallAgent`'s invariant is that every caller holds the
  worker's lease operation lock. `Start` wraps `StartWithoutLifecycleLock` in
  `WithLifecycleLock(ctx, worker, "pool-start", …)`, and `startstop.go:391` states plainly that that lock is
  shared between lease and lifecycle operations. It is one file per worker for both kinds of operation — one
  lock — so a boot was inside it before the port and is inside it after, which is what lets the boot install
  and the install a `lease release` performs exclude each other at all.
- **One dependency was real.** `worker.Manager` held no `BazelHost`, and the install resolves the agent's host
  binary by label. So the cost is a field on `Dependencies`, a field on `Manager`, and a `Bazel()` accessor —
  and the accessor removed a handle rather than adding one: `lease release`, `exec` and a shard's release had
  each been handed a `BazelHost` of their own beside the manager, which is two handles for one collaborator,
  and a fixture that built both would resolve the boot install and the release install through different
  Bazels.

So the gate that bullet asked for was answered by reading the start path, not by a decision. What is genuinely
new in a Linux boot is one install of a binary this controller already installs on a lease operation, an
observation and every daemon start, in a place the ordering makes obvious: the state directory is created and
chowned, the agent lands under it, then `provision-guest` runs, then `validate-guest`.

### What was deliberately not done

- **`sanitize-public-image.sh`, `seal-public-image.sh` and `audit-public-image.sh` stay shell.** They are the
  publishability gates, argued path by path against the image's own filesystem by the reviewers who wrote
  them, and moving that argument into another language would move it away from those reviewers.
  `validate-image` proves the three under `/usr/local/sbin` are executable, execs `air-audit-public-image`,
  and stops there.
- **The wire is unchanged for every verb the scripts had.** The image pair takes named flags where its scripts
  read `AIR_MACOS_VERSION` and four siblings, and that is the one departure: nothing on the host reads those
  variables — the only caller is a Packer template edited in the same changelist. The Linux pair kept the
  positional argv its scripts had, so its host-side builders changed what they name rather than their shape.
  With one addition, which is the rule this repository already applies to the rest of that argv: the script
  hardcoded `install -d -m 0755 /mnt/AirVmShares`, a second copy of a value the controller owns, so
  `provision-guest` is passed `Guest.ShareMount` as a fourth positional and the default keeps one home.
  `validate-guest` passes the same three values it always did.

### Traps this port paid for

In 0105's idiom: each of these cost a session, and each is commented where it lives. This is the index.

- **A `go_cross_binary`'s output is per-configuration, and `out/bazel-bin` is single-valued — so at most one
  of the two cross targets is reachable through it at a time.** Measured on 2026-08-25, macOS arm64:
  `bazel build …:vm-guest-agent-darwin-arm64` then `…:vm-guest-agent-linux-arm64`, each reporting its output
  as `out/bazel-bin/…/cmd/vm-guest-agent/<target>`. After the second, `out/bazel-bin` pointed at
  `bazel-out/linux_arm64-fastbuild/bin` and the directory held no darwin entry at all. Nothing had vanished:
  the darwin artifact was still on disk, untouched, and still linked from its own configuration's bin
  directory — only the spelling that goes *through* `out/bazel-bin` had stopped resolving. **`cquery`
  answered correctly throughout**, because it is keyed to the label and configuration-aware. So the rule is
  not "re-resolve after every build", which would still be broken: it is **never reach a `go_cross_binary`
  output through `bazel-bin` at all.** `resolve_guest_agent_binary` and the controller's own
  `resolveAgentBinary` both resolve by label, through `cquery` plus `bazel info execution_root`, and this was
  confirmed by calling the shell one while `out/bazel-bin` still pointed at the linux configuration: it
  returned the darwin path, and `file` reported a `Mach-O 64-bit executable arm64`.
- **The resolved path is two hops, and the `-ST-` hash is not a platform discriminator.**
  `bazel-out/<os>_arm64-fastbuild/bin/…/cmd/vm-guest-agent/<cross-target>` is itself a symlink into
  `bazel-out/<os>_arm64-fastbuild-ST-<hash>/bin/…/vm-guest-agent-guest_/vm-guest-agent-guest` — note that
  the leaf is named after the *private* target the cross binaries transition, not after the cross target.
  The `-ST-` hash was **identical for both platforms** (`2c5af747e1f8`); the two directories differ only in
  the `darwin_`/`linux_` prefix, so anything matching on the hash to tell the configurations apart is
  matching the wrong field. `resolve_guest_agent_binary`'s `[[ -f ]]` check follows both hops, which is the
  same dereference Packer's upload does — that is what makes the checked file and the uploaded bytes one
  file, and it is the same trap 0105 indexes from the other side, where an `lstat`-based freshness check on
  one of these symlinks degraded silently to "always reinstall", passed its whole suite, and failed on
  hardware.
- **`cquery --output=files` answers empty for a `go_cross_binary`**, so the path is read out of the target
  with `--output=starlark`, and what that prints is relative to the execution root rather than absolute.
  Both readings are the ones `host/guest/agent.go` already documents; `lib.sh` repeats the mechanics because
  it is a second implementation of the same resolution in another language.
- **Three different things happen to the convenience symlinks, and none of them is a deletion. What decides
  is how many configurations one invocation's *top-level* targets span.** Measured on 2026-08-25, macOS
  arm64, reading `out/bazel-bin` before and after each command. One configuration: the symlink is
  *repointed* at it — `bazel build …:vm-guest-agent` alone lands it on
  `bazel-out/darwin_arm64-fastbuild-ST-2c5af747e1f8/bin`, because the host `go_binary` self-transitions too
  (rules_go transitions on `pure = "on"`), and the agent label alone repoints it at
  `bazel-out/linux_arm64-fastbuild/bin` — a plausible-looking symlink into the guest's configuration, in a
  working copy several sessions share. Two configurations where one of them is the baseline: *kept* —
  building both cross targets together leaves it on `bazel-out/darwin_arm64-fastbuild/bin`, with the linux
  entry the unreachable one. Two configurations where neither is: **cleared**, both `out/bazel-bin` and
  `out/bazel-testlogs`, with `WARNING: cleared convenience symlink(s) … because they wouldn't contain
  requested targets' outputs. Those targets self-transition to multiple distinct configurations`. That last
  is the lane build's shape — a `go_cross_binary` beside a host-configuration label — and it is why the
  controller passes `--experimental_convenience_symlinks=ignore` on every command (`host/lane/bazel.go`).
  **`cmd/vm-guest-agent/BUILD.bazel` used to claim that a target shared between the host and a cross build
  would make `bazel build` *delete* the symlink. Both halves of that are now measured and both are wrong:**
  nothing is deleted, and sharing is not the trigger. Pointing the linux cross target at the host binary, so
  the shared target was simultaneously top-level in the host configuration and a transitioned dependency,
  cleared exactly the same two symlinks with exactly the same warning as the separated targets do — the
  split neither causes nor prevents it. The comment now asserts only the measurements, and the split's real
  justification is the `cquery` one below.
- **A transitioned target answers `cquery` once per configuration, and two of its answers can be the same
  path.** Measured 2026-08-25 with one
  `bazel cquery --output=starlark --starlark:expr='… [f.path for f in target.files.to_list()]'` over a
  `set()` of all four targets in `cmd/vm-guest-agent`: `:vm-guest-agent-guest` answered **three** times —
  twice in `darwin_arm64-fastbuild-ST-2c5af747e1f8` (one of those configurations is the host binary's own,
  `c2b2ea1`) and once in `linux_arm64-fastbuild-ST-2c5af747e1f8` — while `:vm-guest-agent` answered exactly
  once. Two of the three answers are byte-identical strings, so a resolver cannot even pick between those
  configurations by inspecting what it got back. This is why the host binary is a target of its own rather
  than the one the cross builds transition: one configured version per label is what makes
  `resolveAgentBinary` and `resolve_guest_agent_binary` single-valued. It is also a third confirmation that
  the `-ST-` hash is not a platform discriminator — the same `2c5af747e1f8` appears in all three.
- **`bazel build …:vm-guest-agent …:vm-guest-agent-darwin-arm64` does not build.** Measured 2026-08-25,
  reproduced three times: analysis fails with hundreds of
  `file 'external/llvm++llvm+llvm-project/compiler-rt/_objs/builtins/*.pic.o' is generated by these
  conflicting actions`, two configuration checksums (`4904aba9…`, `7407e7fb…`) whose output directory name
  is the same `darwin_arm64-fastbuild-ST-c91df9300ca3`, ending in `command succeeded, but not all targets
  were analyzed`. The host binary and the *darwin* cross binary cannot be top-level in one invocation on a
  darwin host, and the targets are already separate, so this is a property of the invocation and not
  something the split could fix. The linux pair is unaffected; nothing in the pipeline or the lane asks for
  that pair, and this is recorded so the next reader does not try it as a sanity check and conclude the
  package is broken.
- **An empty Packer variable has to arrive as an empty *argument*.** Each of the five pins is quoted in the
  `image_versions` local, because unquoted an empty value vanishes in word splitting and every later flag
  pairs with its neighbour. The guest refuses either way — it reads five flags in pairs — but only the
  quoted form says which pin is empty (`--macos-version was given an empty value`) instead of blaming the
  token that landed in a flag's place.

## Consequences

- **The two image verbs are covered by `BT --lane fast` like any other package.** Verified on 2026-08-25:
  the lane is green at 12 035 tests over 108 targets, the vm-lane module's own 32 targets pass, `go test
  -race ./...` is clean, both cross-compiles build, and `resolve_guest_agent_binary`, called from a shell
  with `out/bazel-bin` pointing at the *linux* configuration, still returned the darwin path with `file`
  reporting a `Mach-O 64-bit executable arm64`. `vm.cmd --backend tart image validate` was also run end to
  end on that tree, answering `{"ok":true,…}` after building the darwin label, reporting the agent at
  `<execroot>/bazel-out/darwin_arm64-fastbuild/bin/…/vm-guest-agent-darwin-arm64`, then `The configuration is
  valid.`, both digest pins re-resolving, and `no VM was downloaded, created, or changed`. That resolved path
  is a second confirmation of the trap below: it goes through the execution root and the configuration
  directory, never through `out/bazel-bin`.
- **Building a golden image now builds Go first.** On a host without a working `./bazel.cmd`, both `image`
  commands refuse by name rather than failing inside Packer. The build's cost is unchanged in practice: a
  warm agent build is seconds against Packer's minutes and a cold base pull's hours, which is why `lib.sh`
  builds where the controller's own `resolveAgentBinary` deliberately refuses to — that one runs under a
  worker's lease-operation lock, and this does not.
- **The pins have one list, not one per verb.** `air-macos.pkr.hcl` builds the five flags once and passes the
  same string to both verbs, because the pair's entire value is that `validate-image` checks
  `provision-image`'s work: two lists that can be edited apart are two chances to tell the validator the same
  wrong version the provisioner installed, after which it passes while proving nothing.
- **The Linux pair's refusals are reachable on any host.** Every one of the refusals listed above is reached in
  the package's `guest-test` target, against a stubbed `apt-get`, `dpkg-query`, `systemctl`, `netplan`,
  `install`, `xdpyinfo`, `xprop`, `node` and `ldd` plus a temporary directory the sweep walks — both of the
  window manager's included, and both wait loops to exhaustion. Before the port, reaching one of them needed a
  Linux worker, a boot and a lane.
- **A guest self-check has a home now, and it is a verb.** The next check that would have been a line in a
  shell script and an exit code nobody names has somewhere to go instead, and the closed-set tests make the
  shell shape refuse to come back quietly. That home is now the only one: no shell provisioner of either
  guest survives, so a new check has nowhere else to be written.
- **The verbs are named, and the five numbers are argued in one place.** `provision/README.md`, the guide's
  mentions of all four scripts, [`vm-linux-guest.md`](../vm-linux-guest.md)'s "Why the guest is validated and
  not just installed" and every header that argued this decision locally were shrunk to a statement and a
  pointer here. So were the three places on the code side that had restated the whole argument a link away from
  it — `guest/linux.go`, `host/guest.ProvisionLinux` and the test that pins its refusal — because a full
  restatement beside its own owner is the copy that drifts. The *history* of those five numbers stays where the
  port is argued, because it is the argument for the port; a page or a file that only needs to say the numbers
  are gone says that and links here.
- **`stageError` became `verbRefusal` and `exitOnStageError` became `exitOnRefusal`.** The type was never
  staging's — it is what any verb refuses with — and the image verbs are the callers that made the old name
  wrong. `community/tools/vm/spec/lane-controller.spec.md` carries the closed-status contract.
