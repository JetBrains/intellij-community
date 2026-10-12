---
name: The UI-lane Controller
description: Requirements for the lane controller: the suites a change reaches, the run output and verdict, the daemon start, the guest agent, and the worker lease.
style: plain-1
targets:
  - ../crates/avl-guest/src/supervisor.rs
  - ../crates/avl-guest/src/supervisor/launch.rs
  - ../crates/avl-guest/src/supervisor/state.rs
  - ../crates/avl-guest/src/supervisor/identity.rs
  - ../crates/avl-guest/src/stage.rs
  - ../crates/avl-guest/src/image.rs
  - ../crates/avl-guest/src/image/provision.rs
  - ../crates/avl-guest/src/image/validate.rs
  - ../crates/avl-guest/src/linux.rs
  - ../crates/avl-guest/src/linux/validate.rs
  - ../crates/avl-guest/src/relay.rs
  - ../crates/avl-guest/src/read_file.rs
  - ../crates/avl-wire/src/supervisor.rs
  - ../crates/avl-wire/src/verb.rs
  - ../crates/avl-wire/src/pull.rs
  - ../crates/avl-wire/src/stage.rs
  - ../crates/avl-wire/src/daemon.rs
  - ../crates/avl-wire/src/runtime.rs
  - ../crates/avl-wire/src/report.rs
  - ../crates/avl-wire/src/report/aggregate.rs
  - ../crates/avl-base/src/report.rs
  - ../crates/avl-base/src/plain.rs
  - ../crates/avl-vm/src/console.rs
  - ../crates/avl-vm/src/console/**
  - ../crates/avl-base/src/history.rs
  - ../crates/avl-wire/src/progress.rs
  - ../crates/avl-base/src/journal.rs
  - ../crates/avl-base/src/config.rs
  - ../crates/avl-base/src/phase.rs
  - ../crates/avl-host-sys/src/proc.rs
  - ../crates/avl-host-sys/src/proc/stream.rs
  - ../crates/avl-base/src/fs.rs
  - ../crates/avl-host-sys/src/fs.rs
  - ../crates/avl-base/src/lib.rs
  - ../crates/avl-host-sys/src/lock.rs
  - ../crates/avl-report/src/report.rs
  - ../crates/avl-report/src/aggregate.rs
  - ../crates/avl-report/src/aggregate/persist.rs
  - ../crates/avl-vm/src/worker/hypervisor.rs
  - ../crates/avl-host-sys/src/share.rs
  - ../crates/avl-vm/src/worker/tart.rs
  - ../crates/avl-vm/src/worker/docker.rs
  - ../crates/avl-testkit/src/tartfake.rs
  - ../crates/avl-vm/src/worker/parallels.rs
  - ../crates/avl-affected/src/affected.rs
  - ../crates/avl-affected/src/affected/tests.rs
  - ../crates/avl-vm/src/daemon/build.rs
  - ../crates/avl-vm/src/daemon/build/tests.rs
  - ../crates/avl-vm/src/daemon/command.rs
  - ../crates/avl-vm/src/daemon/command/tests.rs
  - ../crates/avl-vm/src/daemon/host.rs
  - ../crates/avl-vm/src/daemon/state.rs
  - ../crates/avl-vm/src/daemon/http.rs
  - ../crates/avl-vm/src/daemon/http/connector.rs
  - ../crates/avl-vm/src/daemon/testing.rs
  - ../crates/avl-vm/src/daemon/fixture.rs
  - ../crates/avl-vm/src/daemon/leased.rs
  - ../crates/avl-vm/src/daemon/leased/tests.rs
  - ../crates/avl-vm/src/daemon/iterate.rs
  - ../crates/avl-vm/src/daemon/iterate/tests.rs
  - ../crates/avl-vm/src/daemon/run.rs
  - ../crates/avl-vm/src/daemon/run/tests.rs
  - ../crates/avl-vm/src/daemon/slot.rs
  - ../crates/avl-vm/src/daemon/slot/tests.rs
  - ../crates/avl-vm/src/daemon/space.rs
  - ../crates/avl-vm/src/daemon/space/tests.rs
  - ../crates/avl-vm/src/daemon/stage.rs
  - ../crates/avl-vm/src/daemon/stage/tests.rs
  - ../crates/avl-vm/src/daemon/start.rs
  - ../crates/avl-vm/src/daemon/start/tests.rs
  - ../crates/avl-vm/src/daemon/verdict.rs
  - ../crates/avl-base/src/format.rs
  - ../crates/avl-report/src/digest.rs
  - ../crates/avl-host-sys/src/guest.rs
  - ../crates/avl-host-sys/src/guest/parity.rs
  - ../crates/avl-host-sys/src/guest/agent.rs
  - ../crates/avl-host-sys/src/guest/supervisor.rs
  - ../crates/avl-host-sys/src/guest/storage.rs
  - ../crates/avl-vm/src/lane/lanes.rs
  - ../crates/avl-vm/src/lane/env.rs
  - ../crates/avl-vm/src/lane/bazel.rs
  - ../crates/avl-vm/src/lane/affected.rs
  - ../crates/avl-vm/src/lane/affected/tests.rs
  - ../crates/avl-vm/src/worker/worker.rs
  - ../crates/avl-vm/src/worker/worker/pool.rs
  - ../crates/avl-vm/src/worker/worker/lifecycle.rs
  - ../crates/avl-vm/src/worker/worker/tart.rs
  - ../crates/avl-vm/src/worker/worker/docker.rs
  - ../crates/avl-vm/src/worker/worker/parallels.rs
  - ../crates/avl-vm/src/worker/worker/tests.rs
  - ../crates/avl-vm/src/worker/lease.rs
  - ../crates/avl-vm/src/worker/lease/set.rs
  - ../crates/avl-host-sys/src/interrupt.rs
  - ../crates/avl-vm/src/lane/observe.rs
  - ../crates/avl-vm/src/lane/observe/exec.rs
  - ../crates/avl-vm/src/lane/observe/ls.rs
  - ../crates/avl-vm/src/lane/observe/pull.rs
  - ../crates/avl-vm/src/lane/observe/peekaboo.rs
  - ../crates/avl-vm/src/lane/observe/vnc.rs
  - ../crates/avl-vm/src/lane/observe/status.rs
  - ../crates/avl-vm/src/lane/observe/exec/tests.rs
  - ../crates/avl-vm/src/lane/observe/ls/tests.rs
  - ../crates/avl-vm/src/lane/observe/testing.rs
  - ../crates/avl-vm/src/lane/observe/pull/tests.rs
  - ../crates/avl-vm/src/lane/observe/peekaboo/tests.rs
  - ../crates/avl-vm/src/lane/observe/vnc/tests.rs
  - ../crates/avl-vm/src/lane/observe/status/tests.rs
  - ../crates/avl-vm/src/daemon/shard/plan.rs
  - ../crates/avl-vm/src/daemon/shard/baseline.rs
  - ../crates/avl-vm/src/daemon/shard/plan/tests.rs
  - ../crates/avl-vm/src/daemon/shard/baseline/tests.rs
  - ../crates/avl-vm/src/daemon/shard/command.rs
  - ../crates/avl-vm/src/daemon/shard.rs
  - ../crates/avl-vm/src/daemon/shard/command/tests.rs
  - ../crates/avl-vm/src/daemon/flake/plan.rs
  - ../crates/avl-vm/src/daemon/flake.rs
  - ../crates/avl-vm/src/daemon/flake/plan/tests.rs
  - ../crates/avl-vm/src/daemon/flake/command.rs
  - ../crates/avl-vm/src/daemon/flake/command/tests.rs
  - ../crates/avl-vm/src/main.rs
  - ../crates/avl-vm/src/cli.rs
  - ../crates/avl-vm/src/viewer.rs
  - ../crates/avl-vm/src/cli/tests.rs
---

# The UI-lane Controller

Status: Accepted
Date: 2026-10-07

## Summary

The UI-lane controller runs the Air UI scenarios on a worker. It leases the worker and starts the UI-test daemon
there. Then it runs the suites that a change reaches, and it reports the verdict with the evidence.

[Flow UI Scenarios](../../../../plugins/air/spec/docs/flow-ui-scenarios.spec.md) specifies the scenario programs, the
lane harness and the daemon's side of the control channel. [Scenario Traces](scenario-trace.spec.md) specifies the
trace bundle.

## Goals

- A caller names the changed paths, and the controller runs every suite that those paths reach.
- One run gives one verdict, with the evidence of each failure and a rerun command.
- A failure names its cause, and never only a timeout.
- A worker lease and a warm daemon survive between runs, and the controller gives back what it took.

## Non-goals

- Writing the scenario programs. The generator and the lane harness own them.
- Deciding what counts as an intruder dialog. The IDE decides that.
- Recording the trace bundle. The recorder writes it.

## Requirements

### The suites a change reaches

The generator publishes the suite facts that the controller reads, as
[Flow UI Scenarios](../../../../plugins/air/spec/docs/flow-ui-scenarios.spec.md#generated-scenario-programs) states.

- The controller answers which generated suites the paths a caller names reach. It reads the flow tags in
  those paths and the committed suite documents. The caller names the paths, and the controller reads no diff.
  [@test] ../crates/avl-affected/src/affected/tests.rs

- A declared flow reaches the suites of the story flows it implements. So a path which declares only an
  implementation flow reaches a suite. The controller joins, and it derives that link no second time.
  [@test] ../crates/avl-affected/src/affected/tests.rs

- One implementation flow reaches the suites of every story flow which claims it. The link is many to many.
  [@test] ../crates/avl-affected/src/affected/tests.rs

- An implementation flow which no story flow claims reaches no suite, and keeps the reason for it.
  [@test] ../crates/avl-affected/src/affected/tests.rs

- A path reaches a suite in five ways, in this order of precedence. The suite's own document
  or test class reaches that suite. A path in a lane harness module reaches every suite of the lanes that
  module serves. A profile's `[suite: <id>]` attribute names one suite. A declared flow reaches every suite
  which walks that flow. A path reaches every suite whose routes walk its own module.
  [@test] ../crates/avl-affected/src/affected/tests.rs

- The lane harness modules are the flow driver, the scenario runner, the state reset, the bridge, the daemon
  and the controller. A change there can break every suite of its lanes, so a narrower answer would hide a
  failure.
  [@test] ../crates/avl-affected/src/affected/tests.rs

- The two tag relations answer before the spec and module relations, and a path whose tag answered uses no
  other relation. The module relation is the coarse one, because a `module:` endpoint covers its own module
  and every module under it.
  [@test] ../crates/avl-affected/src/affected/tests.rs

- A path which no tag answered reaches the suites of its owning specs. A spec owns a path through its
  `targets`, and its test links name the suites. For a path with no tag, the spec relation and the module
  relation answer together.
  [@test] ../crates/avl-affected/src/affected/tests.rs

- An authored suite is mapped the same way as a generated suite. The controller reads the authored suites
  from `authored-suites.json`, and a contract test keeps that document equal to `AirJourneySuites`.
  [@test] ../crates/avl-affected/src/affected/tests.rs
  [@test] ../../../../plugins/air/tests/integration/contract/test/flow/AirAuthoredSuitesDocumentTest.kt

- A named directory stands for the files that git tracks or would track under it. A directory with no such
  file reaches no suite, and gives its own reason.
  [@test] ../crates/avl-affected/src/affected/tests.rs

- The answer gives the authored fact which matched each suite. That fact is a suite id, a lane harness
  module, a flow id, a spec or a module.
  [@test] ../crates/avl-affected/src/affected/tests.rs

- The answer gives the lane of each suite, in the lane vocabulary of the controller.
  [@test] ../crates/avl-affected/src/affected/tests.rs

- The answer names each path which reaches no suite, and gives one reason for it. The reasons are separate
  because the next action differs. A path in a module no route walks gives that module with the reason. The
  other reasons are a path with no flow tag and no module, and a flow which declares no suite. A path the
  controller cannot read gives its own reason.
  [@test] ../crates/avl-affected/src/affected/tests.rs

- `run --changed` runs the suites those paths reach. Every bare argument after `--changed` is a changed
  path. For each lane, the filters are the lane's own tag, and then one class filter for each suite.
  [@test] ../crates/avl-vm/src/daemon/run/tests.rs
  [@test] ../crates/avl-vm/src/lane/affected/tests.rs

- Without `--lane`, a `run` whose answer reaches several lanes runs one iteration for each lane. The order is
  `ui`, `ui-real`, `gui-chat`, on one worker, and every lane runs on the selected backend. `--lane` runs one
  lane only.
  [@test] ../crates/avl-vm/src/daemon/run/tests.rs
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs
  [@test] ../crates/avl-vm/src/lane/affected/tests.rs

- A run of several lanes gives one reply, with one entry for each lane. The worst lane gives the exit code.
  An iteration which leaves no report stops the run, and the reply names each later lane as not run.
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs

- `shard`, `flake` and `bt.cmd` run one lane, so they refuse an answer of two lanes as
  `affected_lanes_ambiguous`. The refusal names the lanes and the number of suites of each lane. It orders
  the lanes as the controller declares them, and it recommends no lane.
  [@test] ../crates/avl-vm/src/lane/affected/tests.rs
  [@test] ../../bt/crates/bt-core/src/suites/tests.rs

- The answer says when every reached suite came from a `module:` endpoint. Such an answer covers whole
  modules, so a flow tag on the path narrows it.
  [@test] ../crates/avl-affected/src/affected/tests.rs
  [@test] ../crates/avl-vm/src/lane/affected/tests.rs

- A change which no suite covers is refused as `no_affected_suite`. The refusal names each path
  with the reason for it. An iteration which runs no test is not a passing iteration.
  [@test] ../crates/avl-vm/src/lane/affected/tests.rs

- A flow id selects every generated suite whose scenarios tell or walk that flow. It also selects every suite
  whose story flows implement that flow. A suite id selects that one suite, generated or authored. `vm.cmd run`
  and `bt.cmd` accept both.
  [@test] ../../bt/crates/bt-core/src/suites/tests.rs
  [@test] ../../bt/crates/bt-core/src/selector/tests.rs
  [@test] ../crates/avl-vm/src/daemon/run/tests.rs

- A flow or a suite selector makes the selection that `run --changed` makes, one lane or several.
  [@test] ../../bt/crates/bt-core/src/suites/tests.rs
  [@test] ../crates/avl-vm/src/lane/affected/tests.rs

- An unknown flow id or suite id is refused as `usage`, and the refusal names the nearest known ids. A story
  flow which no generated suite tests is refused as `no_affected_suite`, with the reason "no e2e suite tests
  this flow". The two refusals come before a build and before a lease.
  [@test] ../../bt/crates/bt-core/src/suites/tests.rs
  [@test] ../crates/avl-vm/src/daemon/run/tests.rs
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs

- `run` without `--lease-file` builds on the host first, and then leases one worker for the iteration. It
  releases that lease on every exit, and its reply gives the holder, the worker and the disposition as
  `lease`. An interrupt keeps the lease, and the refusal names its receipt. `run` with `--lease-file` runs on
  the worker of that receipt, and it releases nothing.
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs

- `shard` and `flake` build on the host before they lease, as `run` does, so a failed build costs no
  acquisition.
  [@test] ../crates/avl-vm/src/daemon/leased/tests.rs
  [@test] ../crates/avl-vm/src/daemon/shard/command/tests.rs

- `suites` answers the same resolution, and runs no test. It takes no receipt, and it touches no worker.
  Its last line gives the number of suites, the number of suites of each lane, and the lanes a `run` runs.
  [@test] ../crates/avl-vm/src/lane/affected/tests.rs

- A failed iteration gives one command for each failed class. A failure which names no class gives no
  command.
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs

### The daemon build and the launch identity

The rest of the scenario matrix is in
[Flow UI Scenarios](../../../../plugins/air/spec/docs/flow-ui-scenarios.spec.md#the-scenario-matrix).

- A Docker worker runs the architecture of its host. The controller refuses an engine of another architecture.
  [@test] ../crates/avl-vm/src/worker/docker/tests.rs
  [@test] ../crates/avl-base/src/config/tests.rs

- The daemon accepts declared directories as data inputs. A changed file, path, permission, or link changes their identity.
  [@test] ../crates/avl-report/src/digest/tests.rs
  [@test] ../crates/avl-vm/src/daemon/build/tests.rs

- VM preparation installs and probes no agent CLI. An existing image can keep an unused installation.
  [@test] ../crates/avl-vm/src/lane/env/tests.rs
  [@test] ../crates/avl-guest/src/image/provision/tests.rs

- The daemon environment is part of the launch identity. A changed value restarts the daemon.
  [@test] ../crates/avl-vm/src/daemon/build/tests.rs

- A run that rules out each of its classes is not a run that matched no class. The controller gives the
  reason of each class, and does not tell the person to look at the selector.
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs

### Supervision and evidence

The daemon's half and the IDE's half of supervision are in
[Flow UI Scenarios](../../../../plugins/air/spec/docs/flow-ui-scenarios.spec.md#supervision-and-evidence).

- The output form follows the reader. With neither `--json` nor `--text`, a command answers prose when
  stdout is a terminal, and one JSON envelope when stdout is not a terminal. An agent's shell pipes stdout, so
  the agent reads the envelope.
  [@test] ../crates/avl-vm/src/cli/tests.rs

- A command publishes its progress as one stream of typed events: phases, daemon records, and notes. Each
  reader derives its own form from the one event. The terminal prints lines, `--stream` writes NDJSON on
  stderr, and a sink receives every record in every mode.
  [@test] ../crates/avl-base/src/plain/tests.rs
  [@test] ../crates/avl-wire/src/progress/tests.rs

- `run`, `shard` and `flake` each have a run id and a journal, `<runtime root>/runs/<runId>/events.ndjson`.
  The journal holds every record of the run in order, whatever the output form. It opens with `runStarted`,
  and it closes with `runFinished`, which carries the exit code and the refusal's code. The id is also the
  holder of each lease that the run takes for itself, unless `shard --holder` names one. A journal that cannot
  be written is noted, and the run goes on.
  [@test] ../crates/avl-base/src/journal/tests.rs
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs
  [@test] ../crates/avl-vm/src/daemon/shard/command/tests.rs
  [@test] ../crates/avl-vm/src/daemon/leased/tests.rs

- A `run`, `shard` or `flake` in prose names the run and each scenario's trace by a link into the trace
  viewer on this machine. When no viewer answers, the controller starts `trace.cmd serve` in a session of
  its own and does not wait for it. A command in JSON starts no viewer. `AIR_VM_VIEWER=off` turns the
  start off.
  [@test] ../crates/avl-vm/src/viewer/tests.rs
  [@test] ../crates/avl-base/src/plain/tests.rs

- When the form followed a terminal on stdout and on stderr, `run`, `shard` and `flake` draw a live dashboard.
  The dashboard shows every phase of the run from the start. Each phase is pending, in flight, done, failed,
  or skipped. A skipped phase says why, such as a reused warm daemon.
  [@test] ../crates/avl-vm/src/console/state/tests.rs
  [@test] ../crates/avl-vm/src/cli/tests.rs

- The dashboard asks the terminal for its foreground and background once, before its first frame. It derives
  its colours from them: muted text and each hue read against that background at a contrast of 4.5:1 or more.
  A terminal that does not answer keeps the terminal's own 16 colours. `AIR_VM_THEME=light` or `dark` names the
  side of such a terminal, and no question is asked.
  [@test] ../crates/avl-vm/src/console/palette/tests.rs
  [@test] ../crates/avl-vm/src/cli/tests.rs

- The dashboard shows a progress bar over the discovered tests, and the pass, fail and skip counts. It also
  shows the test that runs now. The lines above the dashboard keep each finished phase and each finished test.
  With one worker, the tests are under a header of their class. With two or more workers, each test line
  names its worker and its class.
  [@test] ../crates/avl-vm/src/console/state/tests.rs
  [@test] ../crates/avl-vm/src/console/dashboard/tests.rs

- A finished test waits for its trace. The trace is a `trace` label on the line of the test, and the label is an
  OSC 8 link to the trace viewer. The test stops waiting when its trace arrives or when the next test of its
  worker finishes. It also stops when its iteration reports, when its traces phase ends, or when the run
  ends. The lines after a waiting test wait too, so the lines keep the order of the events. The live region
  shows each waiting test.
  [@test] ../crates/avl-vm/src/console/state/tests.rs
  [@test] ../crates/avl-vm/src/console/dashboard/tests.rs

- `run` and `shard` publish one `verdict` record when they have read their reports. The record holds the status,
  the one-line summary, the counts, the failures with their traces, and the rerun commands. It also holds the
  lanes, the lease, the tree and the traces. A refusal before any report, such as a failed build, has no
  verdict. `flake` has no verdict, because its answer is a measurement.
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs
  [@test] ../crates/avl-vm/src/daemon/shard/command/tests.rs

- Each human form renders the `verdict` record as the answer of the command. The dashboard draws it as the
  verdict card, and the plain lines write it to stdout as a block. No text answer and no refusal line follow
  it, but a refusal with another code still prints. In JSON, `error.message` is the one-line summary of the
  verdict, and `error.details` holds the data.
  [@test] ../crates/avl-base/src/plain/tests.rs
  [@test] ../crates/avl-vm/src/console/state/tests.rs
  [@test] ../crates/avl-vm/src/console/dashboard/tests.rs

- The verdict card shows the counts, the time of each phase, and each failed phase with its reason. It shows
  the reason of a verdict that is not a list of failures. A run of two or more lanes has one line for each
  lane. The card shows each failed test with its trace link, and a rerun command for each failed class. A lease
  that is still held, a dirty checkout and traces that were not pulled are named on the card.
  [@test] ../crates/avl-vm/src/console/state/tests.rs

- A release that leaves a worker held fails the release phase. The error names each receipt and the command
  that releases it.
  [@test] ../crates/avl-vm/src/daemon/shard/command/tests.rs

- Other commands, an explicit `--text`, `--stream`, `TERM=dumb` and `AIR_VM_DASHBOARD=off` get plain lines.
  A long phase then prints a heartbeat line at most every 15 seconds. Under a terminal, a one-line status
  footer stays under the lines. None of these asks the terminal for its colours.
  [@test] ../crates/avl-base/src/plain/tests.rs
  [@test] ../crates/avl-vm/src/cli/tests.rs

- A run states its typical times, read from the journals of earlier runs on this machine. A typical time is the
  median of the newest seven measurements. A thing measured fewer than two times has no typical time.
  [@test] ../crates/avl-base/src/history/tests.rs

- The dashboard compares each phase with its typical time and marks a phase that took much longer. The card
  names each such phase with its reason. It also shows the time left, from the typical time of each class not
  done yet.
  [@test] ../crates/avl-vm/src/console/state/tests.rs

- The host build publishes a `buildSummary` record from the lines that Bazel writes at its end. It holds the
  actions that ran, the actions that a cache answered, and the critical path. The line of the host build
  shows it, and it is the reason of a host build that took much longer.
  [@test] ../crates/avl-vm/src/lane/bazel/tests.rs
  [@test] ../crates/avl-vm/src/console/state/tests.rs

- A run says why the controller chose each action on the daemon, the shares and the IDE. Examples are a
  restart, a remount, a relaunch, a reuse, and pushed test jars. Each choice is a `decision` record with
  its reason, in every output form.
  [@test] ../crates/avl-wire/src/progress/tests.rs
  [@test] ../crates/avl-vm/src/console/state/tests.rs

- On the daemon control channel, a field that is absent is absent, and is not null. The controller tells
  the two apart. The controller refuses an explicit null on that channel.
  [@test] ../../../../plugins/air/tests/integration/uiDaemon/testSrc/AirUiDaemonProtocolTest.kt
  [@test] ../crates/avl-wire/src/daemon/tests.rs

- The controller refuses a record with an unknown name, or a declared field of the wrong type. It does not
  read such a record as an absent value. An unknown field is kept, because a new field is how the daemon
  grows.
  [@test] ../crates/avl-wire/src/daemon/tests.rs

- A state file that the daemon did not write yet, and a state file that the controller cannot read, are
  different results. The controller continues to wait for the first result. The controller stops for the
  second result, and gives the name of the failure. The controller must not use the full boot period before
  it reports the second result.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs

- The controller prepares the guest for one daemon in one call. The guest assembles the argument file of
  that daemon from the classpath which the guest staged itself.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs
  [@test] ../crates/avl-guest/src/stage/tests.rs

- A daemon start that fails after the supervisor started its run cancels that run. A start that read the state
  file keeps the daemon record, so `daemon log` still works.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs

- The controller reaches the daemon through the hypervisor exec channel and the `relay` verb of the guest
  agent. It never connects to the network address of the guest.
  [@test] ../crates/avl-vm/src/daemon/http/connector/tests.rs
  [@test] ../crates/avl-host-sys/src/proc/tests.rs

- The `relay` verb bridges its standard input and output to one port on the loopback address of the guest.
  [@test] ../crates/avl-guest/src/relay/tests.rs

- The health poll waits `AIR_VM_DAEMON_HEALTH_TIMEOUT`. Its refusal gives the reason of the last probe.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs
  [@test] ../crates/avl-vm/src/daemon/http/tests.rs

- A health poll whose relay never opened names the worker, the loopback port and `daemon log`.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs
  [@test] ../crates/avl-vm/src/daemon/http/tests.rs

- The controller installs the guest agent into a Docker worker on each boot. That install comes before the
  self-check. The agent goes into the state directory of the worker, and the controller makes that directory
  before the install.
  [@test] ../crates/avl-host-sys/src/guest/storage/tests.rs

- The boot of a Docker worker is the agent install, the self-check and the parity layout. The image installs the
  packages and the Node, and its entrypoint starts the display. So the boot runs no provisioning verb and no Node
  verb. The controller starts the self-check after the agent install, and it runs the self-check as root. A
  self-check which refuses the guest stops the boot of that worker.
  [@test] ../crates/avl-host-sys/src/guest/storage/tests.rs

- The controller requires a self-check for each Linux boot. It refuses a boot which has no self-check as
  `linux_validation_unset`, and it touches no guest before that refusal.
  [@test] ../crates/avl-host-sys/src/guest/storage/tests.rs

- The self-check of a Linux worker is a verb of the guest agent. The verb answers the one status for a refusal.
  The class of the failure is the code and the message of that refusal.
  [@test] ../crates/avl-guest/src/linux/validate/tests.rs

- The self-check gives a separate code for each check which refuses. A display which does not answer, an
  absent window manager, and a stale window manager property are three of these codes. A C library which is not
  glibc, and a shared object which the guest cannot find, are two more. The self-check has no Node check,
  because the image tag pins the Node of the image.
  [@test] ../crates/avl-guest/src/linux/validate/tests.rs

- The self-check waits for a window manager on the display. It probes once a second, for one minute. A window
  manager which registers in that time does not stop the boot. The
  report of the self-check gives the number of the try which found it. The self-check does not wait for a
  stale window manager property. It refuses that property at the first try.
  [@test] ../crates/avl-guest/src/linux/validate/tests.rs

- A verb of the guest agent refuses an argument which it cannot act on. That refusal is a usage refusal, and
  the verb touches no guest before it. For the self-check, a display of the wrong shape, a relative runtime root,
  and a trailing package list are such arguments.
  [@test] ../crates/avl-guest/src/linux/validate/tests.rs

- A pull copies one guest file to the host. On a Tart worker and on a Docker worker, the `read-file` verb of the
  guest agent writes the file unchanged. Then the verb names the size and the SHA-256 of the bytes. When the bytes
  that arrived are not the bytes that the agent named, the controller refuses the pull with
  `pull_digest_mismatch` and publishes nothing. On a Parallels worker, the guest sends the file in base64.
  [@test] ../crates/avl-vm/src/lane/observe/pull/tests.rs (`pull_refuses_bytes_the_receipt_does_not_name`)
  [@test] ../crates/avl-vm/src/lane/observe/pull/tests.rs (`a_pull_moves_raw_bytes_on_tart_and_docker_and_base64_on_parallels`)
  [@test] ../crates/avl-guest/src/read_file/tests.rs

- The record of an installed guest agent names the checkout which installed it. A controller of a different
  checkout, and a controller which cannot resolve its own checkout, both refuse that record and install the
  agent again. The workers are shared between the checkouts of one host, and a record of another checkout
  names a build which this controller must not run. A controller which refuses the record of a different
  checkout also names the two checkouts. A record which the controller cannot read is no record, and the
  controller installs the agent again.
  [@test] ../crates/avl-host-sys/src/guest/agent/tests.rs

- A `status` row is an answer the controller gives to a caller, and not a record of the daemon control
  channel. Every field of the row is present. A verdict on the row is true, false or null. Null says that
  nothing asked the question, and false says that the check ran and refused.
  [@test] ../crates/avl-vm/src/lane/observe/status/tests.rs

- The controller answers one row for each worker of the pool. A question which does not apply to a guest is
  null on that row. A Linux guest has no TCC databases, no login session and no SSH host key, and it has no
  provenance receipt. These fields are then null on a Linux row: `provenanceReady`, `tccClean`, `tccError`,
  `consoleLogin`, `sshHostKeyFingerprint`, `sshHostKeyReady` and `sshHostKeyUnique`. The `--text` form of
  the row shows some of these fields, and it prints `n/a` for each one which it shows.
  [@test] ../crates/avl-vm/src/lane/observe/status/tests.rs

- Each verdict which can carry a reason has an error field beside it on the same row. `parityReady` is such
  a verdict, and `parityError` gives the refusal code. A worker which a different checkout provisioned
  reports `parityReady` false with `guest_init_stale`. The next run of this checkout provisions that worker
  again. The controller resolves the host paths of the pool one time. A host path which it cannot resolve
  is `hostPathsError` of the answer, and not a refusal on each row. Every `parityReady` of that pool is
  then null, and `parityError` gives only a code of the guest.
  [@test] ../crates/avl-vm/src/lane/observe/status/tests.rs
  [@test] ../crates/avl-host-sys/src/guest/parity/tests.rs
  [@test] ../crates/avl-host-sys/src/guest/tests.rs

- The controller sends the flags, the properties and the main class with the digest of the bytes that it
  expects. The controller refuses a reply whose digest, path or count of entries does not agree.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs
  [@test] ../crates/avl-wire/src/stage/tests.rs

- A phase reports its most expensive guest call. That report holds no element which carries a value,
  because a guest command line can hold a secret.
  [@test] ../crates/avl-base/src/phase/tests.rs

- A refusal of a guest command names the command, and it holds no output of the guest process. A guest
  process can print the authentication token of a launch. The refusal reaches a JSON envelope, and an agent
  reads that envelope and can log it. A bullet below names each line shape which is an exception.
  [@test] ../crates/avl-host-sys/src/guest/tests.rs

- A refusal names the arguments of the failed command. It holds no element which carries a value. The
  rendered list of arguments has a byte bound.
  [@test] ../crates/avl-host-sys/src/guest/tests.rs

- The guest agent answers a structured refusal, and that refusal travels to the caller in full. That
  refusal is text which the agent declares, and not the output of another program.
  [@test] ../crates/avl-host-sys/src/guest/supervisor/tests.rs

- Each verb of the guest agent answers a structured refusal. An agent which answers a bare line for such a
  verb is older than this controller. A refusal which names exit code 64 gives that diagnosis.
  [@test] ../crates/avl-host-sys/src/guest/supervisor/tests.rs

- The guest agent leaves with a closed set of exit statuses. A verb which fails inside the agent answers the
  one status for a refusal. The class of the failure is the code and the message of the refusal, and never a
  status of its own. The controller names each status of that set.
  [@test] ../crates/avl-wire/src/supervisor/tests.rs
  [@test] ../crates/avl-host-sys/src/guest/supervisor/tests.rs

- The controller quotes an unstructured line only when a prefix of that line names the program which wrote
  it. A usage line of the guest agent is such a line, and a complaint of `sudo` is another. Each program
  writes such a line about itself, and before the work of the command starts. The controller quotes the
  first line alone, and it holds that line inside a byte bound.
  [@test] ../crates/avl-host-sys/src/guest/supervisor/tests.rs
  [@test] ../crates/avl-host-sys/src/guest/tests.rs

- The controller warms the host build of the lane without a worker. A warm reports the configuration which
  it warmed.
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs

- A warm and a daemon start prepare the build with the same options. One of them then cannot discard the
  analysis of the other.
  [@test] ../crates/avl-vm/src/lane/bazel/tests.rs
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs

- The host build of the lane also builds the guest agent, for the guest which that lane runs on. One
  invocation builds both. The agent which the controller installs is then an agent of this checkout. A
  lease-only operation installs the agent and builds nothing.
  [@test] ../crates/avl-vm/src/lane/bazel/tests.rs

- One run identity holds one aggregate. The controller refuses a second aggregate for one run identity, and
  it keeps the first one.
  [@test] ../crates/avl-report/src/aggregate/persist/tests.rs

- A flake run writes its summary to a file beside the aggregate of a sharded run. The duration baseline does
  not read that file.
  [@test] ../crates/avl-vm/src/daemon/flake/command/tests.rs
  [@test] ../crates/avl-report/src/aggregate/persist/tests.rs

- The host controller and the guest run supervisor name every field of the supervisor replies once each.
  The two ends share that one declaration.
  The guest binary tells its schema version, its phases, its outcomes and its run-id pattern when a caller
  asks for its contract. A test holds each of those names against its declared spelling.
  [@test] ../crates/avl-wire/src/supervisor/tests.rs

- The guest agent and the controller name every verb of the agent from one declaration. A test holds each
  name against the spelling the installed agents already send.
  [@test] ../crates/avl-wire/src/verb/tests.rs

- The supervisor gives the schema version in each reply. The controller refuses a reply that has a version
  it does not know.
  [@test] ../crates/avl-host-sys/src/guest/supervisor/tests.rs

- The controller refuses a run phase it does not know, because the controller uses the phase to decide that
  a run stopped. The controller keeps an outcome it does not know, because the controller only reports the
  outcome.
  [@test] ../crates/avl-host-sys/src/guest/supervisor/tests.rs

### The worker lease and the warm daemon

- A release of a worker lease keeps the warm daemon of that worker. The daemon holds the run slot of the
  supervisor for the whole life of the daemon. A release that read only that slot could never give back a
  worker that has a warm daemon.
  [@test] ../crates/avl-vm/src/worker/lease/tests.rs

- A release succeeds when the run slot is free. A release also succeeds when the slot holds the idle daemon of
  this controller.
  [@test] ../crates/avl-vm/src/worker/lease/tests.rs
  [@test] ../crates/avl-host-sys/src/guest/supervisor/tests.rs

- A release is refused when an iteration is in flight. The refusal gives the name of that run. The lease stays
  in place, and the holder keeps the receipt for it.
  [@test] ../crates/avl-vm/src/worker/lease/tests.rs

- A daemon that does not answer is not a parked daemon. The controller refuses the release for such a daemon.
  The refusal gives the name of the daemon record that the controller compared, and the name of the command
  that retires that daemon.
  [@test] ../crates/avl-host-sys/src/guest/supervisor/tests.rs
  [@test] ../crates/avl-vm/src/daemon/slot/tests.rs

- `daemon stop` without a daemon record asks the supervisor which run holds the slot. It cancels a daemon run,
  and it refuses any other run. A daemon start does the same.
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs

- A release that finds a daemon run that no record names refuses. The refusal gives the name of `daemon stop`.
  [@test] ../crates/avl-host-sys/src/guest/supervisor/tests.rs

- `daemon stop` keeps the daemon record when the cancel fails and the recorded run still holds the slot. It
  refuses `daemon_retire_failed`.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs

- A guest command line resolves the Tart executable itself. So `daemon stop` and `daemon log` reach the guest
  without the Tart gate.
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs
  [@test] ../crates/avl-vm/src/worker/tart/pin_tests.rs

- A release of a worker whose VM Tart no longer lists skips the guest check. A stop or a start ends its stale
  run process.
  [@test] ../crates/avl-vm/src/worker/lease/tests.rs
  [@test] ../crates/avl-vm/src/worker/worker/tests.rs

- A run without a receipt gives its lease back when its daemon start fails the health poll.
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs

- A sharded run takes the lease disposition of each worker from the answer that the release of that worker
  gave. A sharded run must not report a lease as released while the run still holds that lease.
  [@test] ../crates/avl-vm/src/daemon/shard/command/tests.rs

- A flake run gives back only the workers which it leased. The run names every release which it could not
  complete. An interrupt keeps the leases it took, and `flake_interrupted` names each receipt.
  [@test] ../crates/avl-vm/src/daemon/flake/command/tests.rs

- `pool recycle` repairs one worker. It stops the worker, deletes the clone, clones the worker again, starts
  it and provisions it. The caller names the target, and the controller never defaults that target. A name
  outside the pool is refused as `unknown_worker`, before the controller opens that worker's directory. The
  Parallels backend refuses a recycle as `unsupported_backend_operation`.
  [@test] ../crates/avl-vm/src/worker/worker/tests.rs

- A recycle never touches a leased worker. `pool recycle <worker>` on a leased worker is refused as
  `worker_leased`, and that refusal names the command which releases the lease. The worker keeps its lease,
  its machine and its clone.
  [@test] ../crates/avl-vm/src/worker/worker/tests.rs

- `pool recycle all` recycles the workers which it can, and reports each worker which it did not recycle.
  The answer names the recycled workers, and gives one refusal code for each other worker. A recycle which
  did not reach every target is refused as `recycle_incomplete`, and that answer is the refusal detail.
  [@test] ../crates/avl-vm/src/worker/worker/tests.rs

- `recycle_incomplete` exits 75 when a lease or another lifecycle operation was the only thing in the way. It
  exits 1 when a worker was broken. A caller must not read a partial recycle as a fresh pool.
  [@test] ../crates/avl-vm/src/worker/worker/tests.rs

## User Experience

The output forms, the dashboard and the verdict card are in [Supervision and evidence](#supervision-and-evidence).

## Error Handling

Each refusal has a code, and the requirement that names the refusal gives the code. Examples are `no_affected_suite`,
`worker_leased` and `recycle_incomplete`.

## Testing / Local Run

- Ask which suites a change reaches with `./community/tools/vm.cmd suites <changed-path>...`.
- Run those suites with `./community/tools/vm.cmd run --changed <changed-path>...`.
- Run the suites of one flow with `./community/tools/vm.cmd run flow-rename-session`, or one suite with
  `./community/tools/vm.cmd run rename-session`.

## Open Questions / Risks

The [controller to-do list](../docs/ui-lane-controller-todo.md) holds the open work.

## References

- [ADR 0157](../docs/decisions/0157-a-flow-is-a-test-selector.md)
- [ADR 0059](../docs/decisions/0059-the-ui-lane-tooling-is-rust.md)
- [ADR 0106](../docs/decisions/0106-a-warm-daemon-survives-a-lease-release.md)
- [ADR 0182](../docs/decisions/0182-the-daemon-is-reached-through-the-exec-channel.md)
- [Flow UI Scenarios](../../../../plugins/air/spec/docs/flow-ui-scenarios.spec.md)
- [Scenario Traces](scenario-trace.spec.md)
