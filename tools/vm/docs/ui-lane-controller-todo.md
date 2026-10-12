# UI-lane controller — what is left to do

Open work on the VM UI-test controller itself. The Go migration underneath it is done, then the Rust port
that replaced it, and so is the cost work the hardware runs opened: the ports' record — why there is one
implementation, the parity gate, the Windows stance — is [ADR 0105](decisions/0105-the-ui-lane-controller-is-go.md)
for Go and [ADR 0059](decisions/0059-the-ui-lane-tooling-is-rust.md) for Rust. [`vm-linux-guest.md`](vm-linux-guest.md) is the history of the Linux guest. It holds no open list, because
[ADR 0210](decisions/0210-the-docker-image-carries-node-and-the-tart-linux-worker-is-retired.md) retired the Tart
Linux worker. The sealed-macOS backend has no open list either — its open list was
retired on 2026-08-23 once the golden was rebuilt and the lane measured on it, and what survived it lives
in the [VM guide](vm-ui-tests.md) under `## Parity gate`. Why the daemon exists at all is
[ADR 0104](../../../../plugins/air/docs/decisions/0104-the-daemon-caches-a-process-not-a-build.md). **This file is only the open list.**
A closed item leaves this file. The commit that closed it, named by its subject, and the ADRs under
[`decisions/`](../../../../plugins/air/docs/decisions/) are the record.

Every item below was found by running something, and says so. Where a measurement is quoted it was taken on
`air-linux-1` on 2026-08-23 unless stated otherwise.

## Open

- **An exec call whose agent exits under it waits instead of failing.**

  Measured on 2026-08-26 on `air-linux-3`, while the daemon's own cgroup fix was proved on hardware.
  `vm.cmd exec -- sudo systemctl restart tart-guest-agent` answered `exited with 143` the first time, which
  is the channel reporting its own reaped session. The second time the controller never returned at all, and
  the `vm` process had to be killed by hand before another operation could take the worker's lock.

  Both endings come from one event, so the controller has to choose one of them. The agent cancels its
  vsock context on a loaded host by itself, which `## Not ours` names, so this is a lane that hangs until a
  watchdog rather than a lane that reports a channel failure. What is open is the choice: a guest call
  needs a deadline of its own, or the channel has to read a closed session as a failure.

- **240 of the 255 tracked Kotlin files of `intellij.air.frontend.prompt.ui` get the coarse 19-suite
  answer.**

  Measured on 2026-08-26 with `git ls-files` over that module, against the committed catalog. The module
  holds 255 tracked Kotlin files, and only 15 of them carry a flow tag. The other 240 fall to the module
  relation, which reaches 19 suites over all three lanes, so `run --changed` refuses on each one.

  The exact `@flow` relation is what closes this, and it needs no new relation. A `@flowStep` on the
  performing code carries a `@flow <id>` join tag in the same KDoc block. The path then reaches the suites
  of its own flow. [`flow-authoring-guide.md`](../../../../plugins/air/docs/guides/flow-authoring-guide.md) already asks for that
  placement. The cost is product judgment per flow, which is why this is the widest item on this list.

  **A pilot on 2026-08-26 moved one step, and measured what one move buys.** The flow is
  `flow-add-to-agent-context`. The step `add-context-visible-chips` moved from
  `AgentPromptAddToAgentContextActionService.kt` to `AgentPromptPaletteSessionController`, onto
  `applyAddContextRequest`. Three of the step's four statements name work its former host does not do. The
  implementation twin `add-context-routing-visible-chips` already sits on the new host. The step keeps its
  id, its label, its endpoints and its statements.

  `AgentPromptPaletteSessionController.kt` answered `unmapped: flow_declares_no_suite` before the move. It
  answers 6 suites in the `ui` lane after it, so the lane refusal is gone for that path.
  `AgentPromptAddToAgentContextActionService.kt` still answers the same 6 suites. The other 13 tagged paths
  of the module answer exactly as before. A move inside one module rewrites no committed artifact, so
  `pnpm run check` stayed green with no regeneration diff.

  Status (2026-09-27): a later commit moved the step again, onto
  `AgentPromptPaletteContextController.addExternalContextItems`. That class performs all four statements.

  **The count of 240 stands, because a move relocates a tag rather than adding one.** The ceiling of the
  move mechanism is one path per move, and only where the old host keeps another tag. Every other step of
  the flows this module performs already sits on the code that performs it. `flow-manage-launch-preset` was
  the evidence: its 12 steps sat in 11 files over 4 modules. To lower 240 the flow model needs new steps,
  and a new step changes a documented user flow. That is a product decision, and it is what stays open here.

  Status (2026-09-27): the IJAI-2001 change removed `flow-manage-launch-preset`. At HEAD the module holds
  303 tracked Kotlin files, 164 of them under `src`, and only 7 carry a flow tag.

## Not ours

- **Why the exec agent's vsock context cancels under load is inside Cirrus's `tart-guest-agent`.** It exits
  at its own `main.go:44` with *"context canceled"*, on a loaded host only: three losses in 46 loaded lane
  executions, and none in the quiet twelve. The daemon no longer dies with it. The guest agent starts the
  supervisor as a released, session-detached spawn on every guest, so the daemon outlives its launch channel.
  The systemd unit of the retired Tart Linux worker is gone
  ([ADR 0210](decisions/0210-the-docker-image-carries-node-and-the-tart-linux-worker-is-retired.md)). So the
  cancel now costs a reconnect instead of a lane, and
  [ADR 0110](decisions/0110-the-truncation-chain-is-a-daemon-kill-under-load.md) holds the whole
  measurement. Do not try to fix that binary here.

- The `PackContentModuleJar` remote-cache work belongs to another session. The action declares 159 JBR files
  in `Inputs:`, so adding its mnemonic to `bazel_scrubbing.cfg` cannot produce a cross-platform hit as
  configured; shrinking the declared runtime to the single `bin/java`, as `JvmCompile` already does, is what
  would make the cache usable. Do not edit that config here.

- **`--test_filter=Class#method` cannot select a test method that takes an injected parameter.** Found on
  2026-08-26 while fixing the rerun hint. `JUnit5BazelRunner.getBazelTestMethodSelectors` calls the
  three-argument `DiscoverySelectors.selectMethod`, which means "a method that takes nothing", so a test with
  a `@TempDir`, a `TestInfo` or a `@ParameterizedTest` argument fails discovery. The log says
  `parameterTypes = ''` and `Could not find method with name [...]`. The controlled pair: a spacey backticked
  name with no parameter passes, and a plain name with a `@TempDir` runs nothing. BT reports it as NO TESTS at
  exit 4 and names the class rerun, which is all BT can do — test.xml truncates the name at the first `(`, so
  the parameter list never reaches it. The fix is the four-argument overload, which the same file already uses
  for the IDE-driven path, in `community/platform/testFramework/bootstrap/`. Do not work around it here.
