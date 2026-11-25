# The lane daemon of the guest agent is the only runner of the Air UI lanes

## Context

An Air UI lane needs a runner for its scenarios. The authored scenario bodies run in the bridge plugin of the lane IDE.
So the runner needs no JVM of its own on the host or in the guest.

A second runner costs a lot. A JVM runner stages a classpath and a JBR for each daemon start. A changed jar restarts
each warm daemon, although the lane IDE does not change. Each fact of a lane then has two owners, and the specs, the
docs and the tests must describe both.

A lane that hangs until the job limit of TeamCity loses its log and its JUnit XML. A muted case that hangs can also make
a cut run look green.

The requirements are in [the lane-daemon verb](../../../../spec/lane-controller.spec.md#the-lane-daemon-verb).

## Decisions

1. **The lane daemon of the guest agent is the only runner.** In a guest it is the verb `lane-daemon`. On a host and on
   TeamCity it is the verb `lane-run`, the executable of the Bazel rule `air_lane_test`. The lane tests are
   `ui_lane_test`, `gui-chat_lane_test`, `ui-real_lane_test`, `ui-perf_lane_test` and `ui-live_lane_test`.
2. **No JVM runner exists.** No JVM daemon, host driver, Kotlin lane service or host control channel exists. The
   controller has no `--runner` option, and a command line that names it is a usage refusal.
3. **The bridge is two plugins.** The resident host plugin never reloads. It holds the port manager, the lifecycle
   service with its long poll, the reload route, the control runtime and the client of the lane services. The test
   plugin holds the handlers, the generated requests, the authored bodies and the drivers. A change of the test plugin
   alone reloads it in the running IDE.
4. **The lane daemon reads the lifecycle only through the long poll of the bridge.** The bridge has no lifecycle
   WebSocket.
5. **The runtime descriptor names no JVM.** It has no main class, no classpath tiers and no JBR directory. Its `jbr`
   section names the platform only. A daemon start stages nothing. The lane daemon reads the distribution in place and
   runs the IDE on the JBR of the distribution.
6. **A run of `lane-run` has a budget and a hard stop.** The budget is the smaller of `--run-budget-sec` and the timeout
   of the Bazel test, less a teardown reserve. The end of the budget ends the run as `run_budget_timeout`. If the run
   thread has not returned one kill grace later, a monitor thread stops the run. It writes the partial JUnit XML,
   cancels each live IDE run with a thread dump, kills each recorder and exits 1.
7. **On TeamCity a budget end is a build problem.** `lane-run` writes a build problem with the identity
   `lane-run-budget`, so a muted case cannot make a cut run green.

## Alternatives rejected

- **Each authored body becomes an instruction program.** It is a second language for 30 suites, and five suites have no
  program form.
- **Keep the JVM runner behind `--runner jvm`.** Each fact of a lane keeps two owners, and the guest keeps the stager
  and the classpath tiers.
- **A job limit as the only stop.** The cancel of the job loses the log and the XML that explain the hang.

## Consequences

- One runner holds each fact of a lane. The specs, the docs and the tests describe that runner only.
- The `ui-real`, `ui-perf` and `ui-live` lanes pass no budget, because their parts run 50 to 110 minutes. Their budget
  is the timeout of their Bazel test.
- A run that hangs ends red inside the job. It leaves the XML of what ran and a hang report every 3 minutes. The report
  names the scenario, the open steps and each thread with its wait.
- An edit of an authored body reloads the test plugin, which is about twice as fast as a relaunch.
- The control runtime runs in the resident plugin, and its waits suspend. So the deadline of a part cancels them.
- A body that cannot get a precondition aborts its scenario through its scope. The run counts the scenario as skipped.
- The live lane runs only in the lane daemon of a Linux guest. A host, `lane-run` and a macOS guest refuse it.
- A lane on a developer machine never reads the screen. `lane-run` refuses the screen property as a JVM flag.
- No lane writes `out/air-traces`.
