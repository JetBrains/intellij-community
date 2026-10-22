---
topic: testing
---

# The lane daemon is the only runner of the Air UI lanes

Date: 2026-10-19

## Status

Accepted. Issue [IJAI-4182](https://youtrack.jetbrains.com/issue/IJAI-4182), under
[IJAI-3852](https://youtrack.jetbrains.com/issue/IJAI-3852). It completes
[An authored scenario body runs in the IDE](../../../../plugins/air/docs/decisions/2026-10-16-an-authored-scenario-body-runs-in-the-ide.md):
the bodies moved into the IDE, so the runner on the host could go.

It supersedes [ADR 0104](../../../../plugins/air/docs/decisions/0104-the-daemon-caches-a-process-not-a-build.md): no
JVM daemon and no classpath tier exist. It supersedes the lane modules of
[ADR 0037](../../../../plugins/air/docs/decisions/0037-integration-lanes-as-modules.md), the runner of
[ADR 0039](../../../../plugins/air/docs/decisions/0039-one-scenario-runner-two-program-sources.md) and `AirLaneIde` of
[ADR 0138](../../../../plugins/air/docs/decisions/0138-air-owns-its-lane-ide.md). It supersedes the host client and
the lifecycle WebSocket of [ADR 0219](../../../../plugins/air/docs/decisions/0219-the-http-bridge-is-the-only-transport-into-the-lane-ide.md),
and the host-only lane of [ADR 0075](../../../../plugins/air/docs/decisions/0075-a-host-only-live-agent-lane.md).
Each of these records names the part that is superseded in its own status.

The behavior is stated in [the controller spec](../../spec/lane-controller.spec.md) under **The lane-daemon verb** and
in [the flow UI scenarios spec](../../../../plugins/air/spec/docs/flow-ui-scenarios.spec.md). How a run works is
[the VM guide](../vm-ui-tests.md#the-daemon-loop).

## Context

Until this change, two runners could run an Air UI lane. The JVM runner was a Kotlin JVM on the host or in a guest. It
held the authored bodies, the control runtime of the fake ACP agents, the Kotlin lane services and the host drivers.
In a guest it ran as the JVM daemon, which kept the classpath of every lane in one warm JVM. The lane daemon of the
guest agent was the second runner, and the option `--runner` chose between the two.

The JVM runner had these costs:

- The guest staged a runtime generation for each daemon start: the stable classpath tier, a copy of the JBR, and an
  argument file. One Linux stable tier was 852 MiB, and nothing removed an old one.
- The push carried the hot tier, and the runtime digest of the daemon hashed the stable jars and the JBR. A changed
  stable jar restarted each warm daemon, although the lane IDE did not change.
- The controller named the lane of a run by a JUnit tag. It booted the daemon with the union of the values of every
  lane.
- Each fact of a lane had two owners. The specs, the docs and the tests described two runners, and a change had to
  hold for both.

The JVM existed only for the bodies and for the control runtime. Nothing in them needed the host except the restart
window and the lane services. The guest agent served the lane services already. The record of 2026-10-16 moved the
bodies into the bridge plugin of the IDE.

Two failures on TeamCity showed what the lanes lacked. The builds 1097268610 and 1097141239 of `gui-chat_lane_test`
hung after a `restart-ide` until the job limit of 60 minutes canceled them. A cancel loses the log and the JUnit XML.
Later, UI Tests 2 of a Safe Push was green with 66 cases that never ran, because TeamCity muted the case that hung.

## Decision

1. **The lane daemon of the guest agent is the only runner.** In a guest it is the verb `lane-daemon`. On a host and
   on TeamCity it is the verb `lane-run`, the executable of the Bazel rule `air_lane_test`. The five lane tests are
   `ui_lane_test`, `gui-chat_lane_test`, `ui-real_lane_test`, `ui-perf_lane_test` and `ui-live_lane_test`.
2. **The JVM runner goes.** The flow runner, the JVM daemon, the host support module, the Kotlin lane services, the
   host drivers and the host control channel go. The modules `intellij.air.integrationTests`,
   `intellij.air.integrationTests.flow` and `intellij.air.integrationTests.uiDaemon` go, and so do the lane modules.
   The controller has no `--runner` option, and a command line that names it is a usage refusal.
3. **The bridge is two plugins.** The resident host plugin never reloads. It holds the port manager, the lifecycle
   service with its long poll, the reload route, the control runtime and the client of the lane services. The test
   plugin holds the handlers, the generated requests, the authored bodies and the drivers. A change of the test
   plugin alone reloads it in the running IDE.
4. **The lane daemon reads the lifecycle only through the long poll of the bridge.** The lifecycle WebSocket goes,
   because the JVM client was its only subscriber.
5. **The runtime descriptor names no JVM.** Schema 10 has no main class, no classpath tiers and no JBR directory. Its
   `jbr` section names the platform only. A daemon start stages nothing. The lane daemon reads the distribution in
   place and runs the IDE on the JBR of the distribution.
6. **A run of `lane-run` has a budget and a hard stop.** The budget is the smaller of `--run-budget-sec` and the
   timeout of the Bazel test, less a teardown reserve of 180 s. `air_lane_test` passes 2700 s. The end of the budget
   ends the run as `run_budget_timeout`. If the run thread has not returned one kill grace later, a monitor thread
   stops the run. It writes the partial JUnit XML, cancels each live IDE run with a thread dump, kills each recorder
   and exits 1.
7. **On TeamCity a budget end is a build problem.** `lane-run` writes a build problem with the identity
   `lane-run-budget`, so a muted case cannot make a cut run green.

## Consequences

- One runner holds each fact of a lane. The specs, the docs and the tests describe that runner only.
- The `ui-real`, `ui-perf` and `ui-live` lanes pass no budget, because their parts run 50 to 110 minutes. Their
  budget is the timeout of their Bazel test.
- A run that hangs ends red inside the job. It leaves the XML of what ran and a hang report every 3 minutes. The
  report names the scenario, the open steps and each thread with its wait.
- An edit of an authored body reloads the test plugin. On 2026-10-15 the warm totals of that row were 8.7 s and
  9.1 s, against 19.6 s and 15.9 s for a relaunch.
- The control runtime runs in the resident plugin, and its waits suspend under
  [IJAI-4185](https://youtrack.jetbrains.com/issue/IJAI-4185). So the deadline of a part cancels them.
- A body that cannot get a precondition aborts its scenario through its scope. The run counts the scenario as
  skipped, not as failed.
- The live lane runs only in the lane daemon of a Linux guest. The guest agent provisions JetBrains Central before
  the IDE and tears it down before the verdict. A host, `lane-run` and a macOS guest refuse the lane.
- A lane on a developer's machine never reads the screen, and `lane-run` refuses the screen property as a JVM flag.
- The first run after this change restarts each warm daemon once, and each worker remounts once. A guest can still
  hold the JVM daemon of an older controller. The controller retires it as a daemon of another launch.
- No lane writes `out/air-traces`. An old bundle there names the launcher `ide`, and `air-trace serve` lists it as
  invalid.

## Alternatives rejected

- **Decision B: each authored body becomes an instruction program.** Two probes proved the technique. But it was a
  second language for 30 suites, and five suites had no program form.
- **Keep the JVM runner behind `--runner jvm`.** Each fact of a lane would keep two owners. The guest would keep the
  stager, the tiers and the push of the hot tier.
- **A job limit as the only stop.** The limit of 60 minutes cancels the job. The cancel loses the log and the XML
  that explain the hang.
