---
topic: testing
---

# 105. The UI-lane controller is Go

Date: 2026-08-24

## Status

**Superseded on 2026-09-26 by [ADR 0059](0059-the-ui-lane-tooling-is-rust.md)**, which moves the controller to
Rust, so this record is the history of the Go implementation.

Accepted. Describes `//plugins/air/tests/integration/vm-lane` — Go module `jetbrains.com/air/vmlane`,
whose `cmd/vm` binary behind `plugins/air/scripts/vm.cmd` is the shipping VM UI-test controller. How the
lane works is [the VM guide](../vm-ui-tests.md); why the daemon exists is
[ADR 0104](../../../../../plugins/air/docs/decisions/0104-the-daemon-caches-a-process-not-a-build.md); the controller's open work is
[`ui-lane-controller-todo.md`](../ui-lane-controller-todo.md). This ADR records why there is one Go
implementation, the gate that let it ship, and what the port paid to learn.

**Amended on 2026-08-25 by [ADR 0108](0108-the-guest-half-of-the-image-pipeline-is-go.md).** The Decision
below records that the skill directory keeps `provision/` because the image pipeline was never part of this
port. It was not, and the sentence stands as written; but the pipeline's two macOS guest steps have since
become verbs of the guest agent, so that clause describes the port's scope on 2026-08-24 rather than the
pipeline's shape today. 0108 owns the image pipeline.

## Context

The controller began as TypeScript under `.agents/skills/vm-ui-tests/scripts/` — `vm.ts`, `vm-core.ts`
and their siblings, run through bun. It grew into the thing that judges a lane: it acquires workers,
stages a daemon, pushes jars, reads JUnit XML and issues the verdict an agent quotes. A tool with that
authority wants a single static binary, hermetic tests, and a compiler between an edit and a run.

It was ported package by package to Go 1.27, **stdlib only**: 32 Bazel test targets over 756 Go test
functions as of 2026-08-24, each stage a library with `go_test` only, so the tree was green at every step.

## Decision

**The VM UI-test controller is one Go implementation.** The cutover landed on 2026-08-23: the TypeScript
was deleted the same day the Go binary became the wrapper's target, and the skill directory keeps only
`provision/` — the Packer and shell image pipeline, which was never part of the port (its macOS guest half
became two verbs of the guest agent a day later, in
[ADR 0108](0108-the-guest-half-of-the-image-pipeline-is-go.md)). **There was never a period with two live
implementations**, by design; that property is what the whole port sequence was arranged around, because two
live controllers judging lanes is two sources of verdicts.

### The gate: parity is measured, not asserted

The pattern, established by the `junit` port and reused since: a `go_test` gated on an environment
variable naming an output file, which dumps this implementation's answer over a named corpus and **skips
when the variable is unset**, so the Bazel target stays hermetic. A comparator under `out/go-parity/` fed
the TypeScript the same inputs — carried *in the dump*, so there was one copy of the corpus and not two
that drift.

| what | gate | result |
|---|---|---|
| `junit` | `AIR_JUNIT_CORPUS`, `AIR_JUNIT_CORPUS_OUT` | 323 documents identical, plus 18 635 non-JUnit refusals |
| `host/config` | `AIR_CONFIG_PARITY_OUT` | 69 environments identical, including all 20 refusals matching code, exit status **and** message |
| `bt` | `AIR_BT_PARITY_OUT` | 21 invocations identical, argv included — see [ADR 0107](../../../../../plugins/air/docs/decisions/0107-bt-is-the-go-binary.md) |
| `host/digest` | `AIR_DIGEST_PARITY_OUT`, `AIR_DIGEST_PARITY_TREE` | 39 refusals and 10 vector groups; primitives cross-checked against a JS reference hex-for-hex |
| the lane | one real `--lane ui` per implementation | 0 structural and 0 verdict differences |

The last row is the gate that opened the cutover: one lane per implementation on worker `air-linux-1` at
tree `0390f9d47c161f7810c3708f56afa651ea9af477`, compared by `out/go-parity/run-parity.mjs` —
0 structural differences, 0 verdict differences, exit 0 on both sides, 23 classes started / 0 failed /
1 container skipped (the `codex` prerequisite) on both, over identical suites and cases, at 208.9 s
TypeScript against 206.8 s Go. Negative-control perturbations were run against the comparator and it
discriminated, so the zeroes are a comparison and not a comparator that answers zero.

The dumps and comparison results under `out/go-parity/` are **untracked historical artifacts**: nothing
regenerates or removes them, and none of the comparators can run at all any more — `config-parity.mjs`
imports the deleted `vm-core.ts`, `run-parity.mjs` can no longer produce a TypeScript half, and
`bt-parity.mjs` lost its half when `bazel-test.ts` was deleted. Every one of their gates was consumed
before the deletion.

### Windows is not a supported host

Users on Windows cannot run the UI-test lanes, and no backend change would be small enough to alter that:
the three hypervisor backends are Tart, Parallels and Linux; the guest supervisor detaches children with
`setsid` and cancels by signalling a process group, neither of which Windows has
(`guest/supervisor_other.go`); and there is no Windows lane distribution, JBR, or guest-agent cross-build
— the `Load` guard in `host/config/config.go` spells out why a Windows guest is a project, not a pairing.

Windows keeps exactly two claims on the module:

1. **The Windows CI host builds `//...`, so every target must analyze *and* compile there.** The
   `race = select(...)` blocks, the `target_compatible_with` markers, and the `guest` package's
   `//go:build` seam exist for that. The race select guards **analysis, not execution**: `bazel build
   //...` on the Windows host analyzes even Windows-incompatible targets, and a plain `race = "on"` fails
   `@rules_go//:stdlib` analysis wherever cgo has no C toolchain, before any incompatibility skipping can
   help. Collapsing those selects as dead broke the Windows build once already — Patronus build
   1039490993, 2026-08-24, is the paid-for proof — and the trap is documented at the anchor in
   `tests/integration/vm-lane/defs.bzl`, which is now the only place that select exists;
   `host/exec/BUILD.bazel` keeps just its own Windows-incompatibility essay.
2. **The portable set stays genuinely portable**, with real Windows coverage. `bt` and `cmd/bt` are in it
   because `bt.cmd` is a developer tool used on Windows machines, and after
   [ADR 0107](../../../../../plugins/air/docs/decisions/0107-bt-is-the-go-binary.md) the binary it runs is `cmd/bt`. `host/cli`, `host/phase`,
   `host/config`, `host/fsatomic`, `host/report`, `host/aggregate`, `junit` and `wire/*` are in it too, so
   the set is every package that makes no unix-only call — not a short list of exceptions.

The portable set is a decision each time, not a property that falls out. `host/fsatomic` is the case that
shows what it costs. Extracting the exclusive publish — the private-temporary, `fsync`, `link`, `unlink`
shape that `host/report` and `host/observe` each hand-rolled — into one function put an edge from
`host/report` onto `host/fsatomic`, and `host/fsatomic` was Windows-incompatible for two unix-only calls.
Bazel propagates that, so `host/report` and `host/aggregate` would have left the Windows leg, losing the
coverage of two pure-arithmetic packages for a filesystem call neither of them makes. Splitting the publish
into a second portable package would have kept the coverage and put one concept in two packages side by
side, which is what the Solution Style rule forbids. So `host/fsatomic` was made portable instead: two
`//go:build` file pairs, one per unix-only call, with the shared function bodies staying single.

That produced the rule this ADR records, because the second of those two calls is a security gate:
**a security gate with no implementation on a platform refuses on that platform — it does not pass and it
does not vanish.** `EnsurePrivateDirectory` verifies a guest-named pull destination through a POSIX owner
and permission check that Windows has no equivalent of: there is no `Stat_t` to read a uid from, and an ACL
is not something `os.FileMode` can express. A stub that answered yes there would remove the gate on one
platform while leaving every caller looking gated, so the Windows half is one refusal carrying the unix
gate's own code. It is unreachable in a real Windows build — `host/observe` is the gate's only caller and
stays incompatible on its own account — and it exists so a future caller cannot quietly acquire an ungated
write. The weaker sibling is documented as weaker: `PublishByLink`'s Windows reopen is an `Lstat` and then
an open, two calls where unix has one `O_NOFOLLOW`, and what makes that acceptable is who could plant a
symlink in between — a temporary this package created moments earlier, named with its own pid and a fresh
UUID, in the destination's directory, on a platform no guest runs on.

The same rule came due a second time, and cost one move. `host/aggregate` rendered its persisted JSON through
`host/digest.IndentedJSON`, and `host/digest` reads a `syscall.Stat_t`, so a shared encoder was again about to
carry a pure-arithmetic package off the Windows leg. The encoder moved to `host/cli`, which every host package
already depends on and which makes no unix-only call; `host/digest` keeps `CompactJSON`, whose callers are all
inside that blast radius already. That leaves `host/cli` as the host tree's portable base, and the duty is
stated in its `BUILD.bazel`: a `syscall` added there takes the merge ladder and the Wilson intervals off
Windows with it.

### Traps this port already paid for

Each of these cost a debugging session or a hardware run. They are commented where they live; this is the
index.

- **A `go_cross_binary` output is a symlink.** An `lstat`-based freshness check degraded silently to
  "always reinstall", passed its entire suite, and failed on hardware. `hostFileSignature` follows
  symlinks — and `vm.cmd` cites this trap as the reason it caches nothing.
- **`--nobuild` proves analysis, not execution.** A canary built on it launched an hour of VM work
  against a tree whose `dev_dist_reference_assembler` failed. The canary is a real build.
- **JavaScript's `Number` is not `ParseInt`**, its `$` under `/m` matches before `\r`, its `toFixed`
  rounds half away from zero where Go rounds half to even, and `relative()` answers `""` where Go answers
  `"."`. Every one produced a real difference; each is individually commented.
- **`json.Marshal` always HTML-escapes and `JSON.stringify` never does.** Two independent packages hit it
  the same day. Anything comparing bytes needs `SetEscapeHTML(false)`, including inside a nested
  marshaler.
- **A `go_test`'s Bazel summary reported `0 test cases` even when cases ran**, so a genuinely empty target
  and a passing one looked identical — this repository's classic false green, in the one tree that judges
  lanes. That is why every test target here is declared through `vm_go_test` in
  `tests/integration/vm-lane/defs.bzl`, the module's own wrapper around `go_test`: it makes the runner
  report each case, so the summary counts what actually ran and an empty target reads as empty. Declaring a
  bare `go_test` here reintroduces the hole.
- **A test can pass under `go test` and fail under Bazel.** The darwin sandbox silently drops the setuid
  bit, so a test asserting a mode-4600 refusal blamed the reader for what the filesystem refused. Run
  both.
- **The checkout is shared.** `git add` writes a shared index and relative revisions resolve at run time:
  commit with `git commit -F - --only -- <explicit paths>` and verify with
  `git merge-base --is-ancestor`. One commit was swallowed by a peer's and one was dropped by a peer's
  `reset HEAD^`.
- **The `IJ-MR-…` trailer is accumulated by Safe Push, on push.** Two sessions of this port disagreed in
  good faith about its scope and each wrote a different half, which is why three landed port commits are
  inconsistent about it. There is nothing to fix: the trailer's absence on unpushed work is expected rather
  than an omission, all three of those commits are immutable on `origin/master`, and a later pass cannot
  make them agree. Do not re-open this as a cleanup.

## Consequences

- One implementation issues lane verdicts, and an edit to it is guarded by a compiler, a hermetic test
  target per package, and a wrapper that rebuilds on every invocation rather than trusting a cache.
- The parity apparatus is history, not machinery: the env-var-gated dump tests still exist and still
  skip hermetically, but their TypeScript counterpart is gone, so a future rewrite would build a new
  comparator rather than reuse these.
- **Two Go packages are named `guest`** — `vmlane/guest`, which is the guest agent's own code, and
  `vmlane/host/guest`, which is the controller's view of a guest. Nothing imports both, so it compiles, and
  the pair is settled rather than pending: each name is the right one where it lives.
- **`ShardEntry` and `FlakeTrial` write their embedded fields before `shardIndex`/`ordinal`**, where the
  TypeScript spread those first. Semantics identical, bytes not — and with the TypeScript gone there is
  nothing left to be byte-identical to, so this is only a note for reading the untracked dumps under
  `out/go-parity/`.
- The work the handover carried is closed. `bt`'s entry point and its argv-level dump comparison are
  [ADR 0107](../../../../../plugins/air/docs/decisions/0107-bt-is-the-go-binary.md); the two aggregate honesty decisions were taken rather than
  pinned, and their tests now assert the fix; and the lease release that stranded a lease behind a warm
  daemon is [ADR 0106](0106-a-warm-daemon-survives-a-lease-release.md). What is left is measurement and
  one lane-harness question, and it lives in
  [`ui-lane-controller-todo.md`](../ui-lane-controller-todo.md).
- The whole-controller verification recipe lives in the VM guide under
  [Verifying the controller](../vm-ui-tests.md#verifying-the-controller).
