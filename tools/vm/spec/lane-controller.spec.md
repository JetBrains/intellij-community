---
name: The UI-lane Controller
description: Requirements for the lane controller: the suites a change reaches, the run output and verdict, the daemon start, the guest agent, and the worker lease.
style: plain-1
targets:
  - ../crates/avl-guest/src/supervisor.rs
  - ../crates/avl-guest/src/supervisor/launch.rs
  - ../crates/avl-guest/src/supervisor/state.rs
  - ../crates/avl-guest/src/supervisor/identity.rs
  - ../crates/avl-guest/src/supervisor/supervise.rs
  - ../crates/avl-guest/src/stage.rs
  - ../crates/avl-guest/src/image.rs
  - ../crates/avl-guest/src/image/provision.rs
  - ../crates/avl-guest/src/image/validate.rs
  - ../crates/avl-guest/src/linux.rs
  - ../crates/avl-guest/src/linux/validate.rs
  - ../crates/avl-guest/src/relay.rs
  - ../crates/avl-guest/src/read_file.rs
  - ../crates/avl-guest/src/ide.rs
  - ../crates/avl-guest/src/ide/prepare.rs
  - ../crates/avl-guest/src/ide/launch.rs
  - ../crates/avl-guest/src/ide/gc.rs
  - ../crates/avl-guest/src/ide/reap.rs
  - ../crates/avl-guest/src/supervisor/thread_dump.rs
  - ../crates/avl-wire/src/ide.rs
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
  - ../crates/avl-host-sys/src/proc/identity.rs
  - ../crates/avl-report/src/report.rs
  - ../crates/avl-report/src/aggregate.rs
  - ../crates/avl-report/src/aggregate/persist.rs
  - ../crates/avl-vm/src/report.rs
  - ../crates/avl-vm/src/report/time.rs
  - ../crates/avl-vm/src/worker/hypervisor.rs
  - ../crates/avl-host-sys/src/share.rs
  - ../crates/avl-host-sys/src/paths.rs
  - ../crates/avl-host-sys/src/runfiles.rs
  - ../crates/avl-host-sys/src/guest/runfiles.rs
  - ../crates/avl-guest/src/runfiles.rs
  - ../crates/avl-wire/src/runfiles.rs
  - ../crates/avl-vm/src/worker/tart.rs
  - ../crates/avl-vm/src/worker/docker.rs
  - ../crates/avl-vm/src/worker/container.rs
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
  - ../crates/avl-vm/src/lane/ide.rs
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
  - ../crates/avl-vm/src/worker/run_marker.rs
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

- A macOS host that names no engine runs the Docker workers on Apple `container` from macOS 26 on Apple silicon.
  Another Mac keeps the engine of its host environment. Its first engine command refuses `docker_missing` when that
  engine does not answer. A host that names an engine in `DOCKER_BIN` or `DOCKER_HOST` keeps that engine. A set
  `AIR_VM_DOCKER_ENGINE` is refused `invalid_environment`.
  [@test] ../crates/avl-base/src/config/tests.rs (`the_macos_version_chooses_the_engine`)
  [@test] ../crates/avl-base/src/config/tests.rs (`the_removed_engine_variable_is_refused`)
  [@test] ../crates/avl-vm/src/worker/docker/tests.rs (`an_older_mac_that_names_no_engine_is_told_the_two_ways_out`)

- On Apple `container`, the controller uses the server of the login session and never stops it. It starts a server
  that is down. It refuses a server of another install with another major version.
  [@test] ../crates/avl-vm/src/worker/container/tests.rs

- On Apple `container`, each build and each container gets a nameserver, and each container gets the memory and the
  CPUs of one worker. A container that stops while it starts is refused with its log and its boot log.
  [@test] ../crates/avl-vm/src/worker/container/tests.rs (`a_start_speaks_the_apple_dialect`)
  [@test] ../crates/avl-vm/src/worker/container/tests.rs (`a_container_that_stops_while_starting_quotes_both_logs`)

- The daemon accepts declared directories as data inputs. A changed file, path, permission, or link changes their identity.
  [@test] ../crates/avl-report/src/digest/tests.rs
  [@test] ../crates/avl-vm/src/daemon/build/tests.rs

- The digest cache keeps only the files that its last sweep read. A sweep that read nothing keeps the whole cache.
  [@test] ../crates/avl-report/src/digest/tests.rs (`a_save_keeps_only_the_entries_of_its_sweep`)

- The runtime descriptor names each file of a context plugin with its place below the plugin directory of the IDE
  context. A place that leaves that directory, or that two files claim, is refused. The files have an identity of
  their own, apart from the product and the mount identities. A changed file changes only that identity.
  [@test] ../crates/avl-wire/src/runtime/tests.rs
  [@test] ../crates/avl-vm/src/daemon/build/tests.rs

- VM preparation installs and probes no agent CLI. An existing image can keep an unused installation.
  [@test] ../crates/avl-vm/src/lane/env/tests.rs
  [@test] ../crates/avl-guest/src/image/provision/tests.rs

- The daemon environment is part of the launch identity. A changed value restarts the daemon.
  [@test] ../crates/avl-vm/src/daemon/build/tests.rs

- The event file of the lane build names the runtime descriptor. So the controller asks Bazel nothing between the
  build and the decision about the IDE. A build whose event file names no descriptor makes the controller ask Bazel,
  and a note says so. The lane build builds no pinned tool.
  [@test] ../crates/avl-vm/src/lane/bazel/tests.rs (`a_lane_build_names_the_descriptor_and_no_query_follows`)
  [@test] ../crates/avl-vm/src/lane/bazel/tests.rs (`a_failed_build_forgets_the_files_of_the_build_before_it`)
  [@test] ../crates/avl-vm/src/lane/bazel/tests.rs (`the_lane_build_builds_this_guests_agent`)

- The controller records the path of each pinned tool in its runtime root. The record is valid for the digest of the
  file that pins the tool and for the Bazel user root. A later process reads the record and asks Bazel nothing. A
  changed pin, or a recorded file that is gone, resolves the tool again with one query.
  [@test] ../crates/avl-vm/src/worker/pin/tests.rs (`a_recorded_path_answers_the_next_process_without_a_query`)
  [@test] ../crates/avl-vm/src/worker/pin/tests.rs (`a_changed_pin_resolves_again`)
  [@test] ../crates/avl-vm/src/worker/pin/tests.rs (`a_recorded_file_that_is_gone_resolves_again`)

- No Bazel command of the controller carries a build option of its own. The guest is a target: the controller builds the
  daemon target of its guest OS. The lane builds no runfile links, and the controller reads the MANIFEST of the runtime
  descriptor on every host. A tree beside the MANIFEST is ignored, because it can be one of an earlier build.
  [@test] ../crates/avl-vm/src/lane/bazel/tests.rs (`every_invocation_carries_no_build_option_of_its_own`)
  [@test] ../crates/avl-vm/src/daemon/build/tests.rs (`each_guest_builds_its_own_daemon_target`)
  [@test] ../crates/avl-host-sys/src/runfiles/tests.rs (`a_host_reads_the_manifest_even_beside_a_tree`)

- The journal has the phase `prepare`, from the end of the host build to the end of the digest sweep. Its progress
  names the time of the descriptor lookup, the cache open, the digests, the cache save and the runfiles tree. The
  record `runStarted` names when the process started. So the journal shows the build of the `vm.cmd` wrapper before
  it.
  [@test] ../crates/avl-vm/src/daemon/leased/tests.rs (`the_build_comes_before_the_lease`)
  [@test] ../crates/avl-vm/src/daemon/leased/tests.rs (`a_process_start_reads_as_rfc3339_in_utc`)

- The controller tells the daemon how to launch the lane IDE. It names the installed guest agent and the directory of
  the IDE contexts. It names no JVM, because the IDE runs on the JBR of its distribution.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs

- The daemon JVM runs on a copy of the JBR of the distribution in its staged generation, and never on the share. The
  `jbr` section of the runtime descriptor names the directory of the JBR below the distribution home. The guest
  agent's `stage` copies that directory with its modes and its links. A distribution without it is a refusal that
  names the path, on the host and on the guest. The tree digest of the JBR is part of the runtime digest, so only a
  change of the JBR restages it.
  [@test] ../crates/avl-vm/src/daemon/build/tests.rs (`a_changed_jbr_of_the_distribution_changes_the_runtime_digest`)
  [@test] ../crates/avl-vm/src/daemon/build/tests.rs (`a_distribution_without_its_jbr_refuses_with_the_path`)
  [@test] ../crates/avl-vm/src/daemon/stage/tests.rs (`a_guest_that_cannot_answer_stage_check_falls_through_to_a_full_stage`)
  [@test] ../crates/avl-guest/src/stage/tests.rs (`the_stage_copies_the_jbr_of_the_distribution_into_the_generation`)
  [@test] ../crates/avl-guest/src/stage/tests.rs (`a_distribution_without_its_jbr_is_refused_with_the_path`)
  [@test] ../crates/avl-wire/src/runtime/tests.rs (`the_jbr_section_keys_are_the_ones_the_reader_reads`)

- The guest reads no checkout. A worker has one share, the Bazel output user root, read-only, on every backend. A
  path of the checkout does not map to a guest path. A set `AIR_VM_REPO_SHARE_NAME` is refused.
  [@test] ../crates/avl-host-sys/src/share/tests.rs
  [@test] ../crates/avl-host-sys/src/paths/tests.rs
  [@test] ../crates/avl-base/src/config/tests.rs
  [@test] ../crates/avl-vm/src/worker/docker/tests.rs (`a_second_checkout_declares_the_same_container`)
  [@test] ../crates/avl-vm/src/worker/container_linux/tests.rs

- The parity layout links the Bazel output root and makes the writable roots of a run. It makes no checkout
  directory and probes no `.git`. Its marker and its receipt name no checkout, so a worker serves every checkout of
  the host.
  [@test] ../crates/avl-host-sys/src/guest/parity/tests.rs

- The guest builds its runfiles tree from the MANIFEST on every host. A runfile whose target is on the Bazel share is
  a link. A runfile whose real path lies in the checkout is a staged runfile, and the tree holds a copy of it. The guest
  checks each copy by its length and its sha256. A target under neither is refused. The first request carries no bytes, and
  the guest asks for the bytes only when it has no tree of the digest.
  [@test] ../crates/avl-host-sys/src/runfiles/tests.rs
  [@test] ../crates/avl-host-sys/src/guest/runfiles/tests.rs
  [@test] ../crates/avl-guest/src/runfiles/tests.rs
  [@test] ../crates/avl-wire/src/runfiles/tests.rs
  [@test] ../crates/avl-vm/src/daemon/build/tests.rs (`a_runfile_in_the_checkout_is_staged_with_its_digest`)
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs

- The home of the daemon JVM is an empty directory of its staged generation. Its config, system and log directories
  and the output tree of IDE Starter are on the guest disk. Each one has its own flag, so no path derives from a
  checkout. The daemon refuses a home outside its generation.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs
  [@test] ../../../../plugins/air/tests/integration/uiDaemon/testSrc/AirUiDaemonStagedHomeTest.kt

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

- A test jar that the host rebuilt while the iteration ran is a note, not a verdict diagnostic. The terminal and the
  run journal show the note, and the report carries it in `notes`. The note names the jars. The status follows the
  counts.
  [@test] ../crates/avl-report/src/report/tests.rs
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs

- The run journal holds the life of the lane IDE. An `ideLaunched` record names the pid, the launch name, the
  launch key and the log directory. An `ideExited` record names the pid, the exit code and the signal.
  [@test] ../crates/avl-wire/src/daemon/tests.rs

- The run journal holds each line that the daemon writes to its error stream during a run. Each line is an `output`
  record. An example is the line that names a recycle of the lane IDE. The text has no stamp of the daemon, and the
  record has its own time. An `output` record is not progress. It moves no deadline of the daemon or of the
  controller. No `output` record of a run follows its summary.
  [@test] ../crates/avl-wire/src/daemon/tests.rs
  [@test] ../crates/avl-vm/src/daemon/run/tests.rs
  [@test] ../../../../plugins/air/tests/integration/uiDaemon/testSrc/AirUiDaemonStampedOutputTest.kt
  [@test] ../../../../plugins/air/tests/integration/uiDaemon/testSrc/AirUiDaemonProtocolTest.kt

- The watchdog evidence may name a file of an IDE context. Examples are a heartbeat screenshot and the thread
  dump before a kill.
  [@test] ../crates/avl-wire/src/report/tests.rs

- The dumps that each `slowStep` record names are evidence too. The controller fetches them beside the report,
  after the files of the expiry, and at most eight. The journal keeps the record as a `runProgress` event.
  [@test] ../crates/avl-wire/src/report/tests.rs
  [@test] ../crates/avl-wire/src/daemon/tests.rs

- `report time <run or iteration>` says where the time of a run went. It reads the journal of the run, the report of
  each iteration and the spans of the trace bundles, and needs no worker. The answer is the total, and the time
  inside the test cases and outside them. It adds the launches of the lane IDE and the time of the `restart` spans.
  It shows the time of each `relaunch` part, such as the wait for the process. It also names the ten longest test
  cases and the ten lane spans with the most self time. An iteration id names that iteration of the newest run that
  reported it. A file that cannot be read is a note of the answer.
  [@test] ../crates/avl-vm/src/report/time/tests.rs
  [@test] ../crates/avl-vm/src/cli/tests.rs

- With `--baseline <run or iteration>`, `report time` adds the ratio of each test class, this run over the
  baseline. It also adds the median ratio over the classes that took more than 5 s in the baseline.
  [@test] ../crates/avl-vm/src/report/time/tests.rs

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
  [@test] ../crates/avl-vm/src/lane/observe/pull/tests.rs (`a_pull_moves_raw_bytes_on_tart_docker_and_container_linux_and_base64_on_parallels`)
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
  a verdict, and `parityError` gives the refusal code. A worker which the controller provisioned for a different
  Bazel output root reports `parityReady` false with `guest_init_stale`. The next run provisions that worker
  again. A worker which a different checkout provisioned is ready, because the layout names no checkout. The controller resolves the host paths of the pool one time. A host path which it cannot resolve
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

- The guest agent reads the two documents of the lane IDE on standard input. `ide-prepare` reads the context
  document, and `ide-launch` reads the launch document. Neither document holds a secret.
  [@test] ../crates/avl-wire/src/ide/tests.rs

- A preparation lays out the context: the data directories, the project, the disabled plugins, and the `home` and
  `bin` directories. Each directory has mode 0700.
  [@test] ../crates/avl-guest/src/ide/prepare/tests.rs

- The lane IDE runs in a closed environment from its context. `HOME` is the `home` directory of the context, and
  `PATH` starts with its `bin` directory.
  [@test] ../crates/avl-guest/src/ide/launch/tests.rs
  [@test] ../crates/avl-guest/src/supervisor/tests.rs

- Beside those two, the environment of the lane IDE holds the fixed constants of the supervisor and an allowlist
  of the agent's own environment. No other variable of the agent reaches the IDE.
  [@test] ../crates/avl-guest/src/supervisor/tests.rs

- The lane IDE always runs under a UTF-8 locale. A UTF-8 locale of the agent's own environment stays as it is. When
  the agent has no locale, or one that is not UTF-8, the IDE gets `LC_ALL=C.UTF-8`.
  [@test] ../crates/avl-guest/src/supervisor/tests.rs

- The run spec of the IDE records the environment policy and its context directory. It holds no variable value.
  A run spec without a policy inherits the environment of the agent, as the daemon run does.
  [@test] ../crates/avl-wire/src/supervisor/tests.rs
  [@test] ../crates/avl-guest/src/ide/launch/tests.rs

- The IDE argv is the JVM, the argument file of the context, and the project directory. The launch record of the
  context holds no property value.
  [@test] ../crates/avl-guest/src/ide/launch/tests.rs

- The agent owns the data directories of the IDE. A launch document or a flags file that sets one of their paths
  is refused.
  [@test] ../crates/avl-wire/src/ide/tests.rs
  [@test] ../crates/avl-guest/src/ide/launch/tests.rs

- A launch on a Linux guest refuses `ide_display_missing` when the agent's own environment has no `DISPLAY`. A
  preparation or a launch for a context whose IDE runs refuses `ide_running`.
  [@test] ../crates/avl-guest/src/ide/prepare/tests.rs
  [@test] ../crates/avl-guest/src/ide/launch/tests.rs

- A launch kills the JCEF helpers that name its context, and no other process.
  [@test] ../crates/avl-guest/src/ide/reap/tests.rs

- When the child of a run exits, the supervisor kills the processes that stay in the process group of the child. The
  supervisor log of the run names each of them. The run then finishes with the exit of the child, and it is not
  orphaned.
  [@test] ../crates/avl-guest/src/supervisor/tests.rs

- `cancel --thread-dump` writes a thread dump of the process before the signal. The dump of an IDE run goes into
  the log directory of its launch. A failed dump keeps its file.
  [@test] ../crates/avl-guest/src/supervisor/thread_dump/tests.rs

- `ide-gc` keeps the newest log directories of each context. It always keeps the log directory of a live IDE.
  [@test] ../crates/avl-guest/src/ide/gc/tests.rs

- A cancel never refuses with `cancel_timeout` and never leaves the slot held. When the supervisor does not end the
  cancel within its deadline, the agent kills the supervisor and the process group of the child. The run then
  finishes as canceled, and `ide-gc` reports the IDE as killed.
  [@test] ../crates/avl-guest/src/supervisor/tests.rs (`a_cancel_past_its_deadline_kills_the_supervisor_and_the_group_and_frees_the_slot`)
  [@test] ../crates/avl-vm/src/lane/ide/tests.rs (`the_gc_note_names_what_the_gc_did`)

- A process group that outlives the kill is abandoned. The run finishes as canceled with the survivors named, the
  slot is free, and `abandoned.json` in the context records the run. Only a recycle of the worker discards these
  processes.
  [@test] ../crates/avl-guest/src/supervisor/tests.rs (`a_group_that_outlives_the_kill_is_abandoned_and_its_slot_freed`)

- `ide-gc` names each abandoned run while its process group lives, and it removes the record of a group that is
  gone. The `status` row of the worker gives these runs in `abandonedIdeRuns`, and its text asks for a recycle.
  [@test] ../crates/avl-guest/src/ide/gc/tests.rs (`an_abandoned_run_is_named_while_its_group_lives`)
  [@test] ../crates/avl-vm/src/lane/observe/status/tests.rs (`a_guest_that_abandoned_an_ide_run_names_it_on_its_row`)

- A fresh preparation deletes the data directories, the project, the `home` and `bin` directories, the argument
  file and the launch record. It keeps the log directories and the run directories of earlier launches, and
  `ide-gc` trims them.
  [@test] ../crates/avl-guest/src/ide/prepare/tests.rs
  [@test] ../crates/avl-guest/src/ide/launch/tests.rs

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

- On the Apple `container` engine, a release keeps the warm daemon until the idle deadline only. The release
  writes the deadline record and starts a detached `pool idle-stop` process, and the reply names `idleStopAt`.
  `AIR_VM_IDLE_STOP` sets the grace, 3600 s by default. `off` writes nothing, and `0` stops the worker inside the
  release.
  [@test] ../crates/avl-vm/src/worker/lease/tests.rs
  [@test] ../crates/avl-base/src/config/tests.rs

- At the deadline the idle stop stops a running worker that no lease holds. It keeps a leased worker, and a
  worker whose record is gone or names another nonce. It never starts a server that is down.
  [@test] ../crates/avl-vm/src/worker/worker/docker/tests.rs

- An acquisition ranks the free slots. The first choice is a running slot whose daemon serves the build that asks.
  Then come a running slot, a stopped slot and an absent slot. Within one class the pool order stays, and a slot without a running
  state keeps its place. The probes start nothing. The holder recovery comes first. The reply names the class in
  `slotReason`.
  [@test] ../crates/avl-vm/src/worker/lease/set/tests.rs
  [@test] ../crates/avl-vm/src/worker/lease/tests.rs

- An acquisition skips a busy worker and leases a free one. Another operation, such as a start, holds the lifecycle
  lock of a busy worker. A busy worker that may hold a lease of the holder refuses the acquisition as `worker_busy`,
  so the holder gets no second worker. A lease that the controller cannot read counts as such a lease. A busy worker
  can stop the placement of the lease, or of the full set of `--exact`. Then the refusal is `worker_busy`. Without a
  busy worker it is `pool_exhausted`. Both exit 75.
  [@test] ../crates/avl-vm/src/worker/lease/set/tests.rs

- A holder recovery refuses `worker_busy` while a live run of the holder is on the worker. A stale marker does not
  stop the recovery.
  [@test] ../crates/avl-vm/src/worker/lease/set/tests.rs

- A lease acquisition and a start remove the deadline record, so the worker keeps running for its new holder.
  [@test] ../crates/avl-vm/src/worker/lease/tests.rs
  [@test] ../crates/avl-vm/src/worker/worker/docker/tests.rs

- On the Apple `container` engine the default pool size follows the host memory. The workers get at most a
  quarter of it, from 2 to 16 slots. `AIR_VM_MAX_WORKERS` and `AIR_VM_WORKERS` win, and every other engine and
  backend keeps 2 slots. `status` names the size and its reason.
  [@test] ../crates/avl-base/src/config/tests.rs
  [@test] ../crates/avl-host-sys/src/host/tests.rs
  [@test] ../crates/avl-vm/src/lane/observe/status/tests.rs

- On the Apple `container` engine the builder VM stops after each image build. The Docker dialect has no builder
  to stop.
  [@test] ../crates/avl-vm/src/worker/container/tests.rs
  [@test] ../crates/avl-vm/src/worker/docker/tests.rs

- A release succeeds when the run slot is free. A release also succeeds when the slot holds the idle daemon of
  this controller.
  [@test] ../crates/avl-vm/src/worker/lease/tests.rs
  [@test] ../crates/avl-host-sys/src/guest/supervisor/tests.rs

- A release is refused when an iteration is in flight. The refusal gives the name of that run. The lease stays
  in place, and the holder keeps the receipt for it.
  [@test] ../crates/avl-vm/src/worker/lease/tests.rs

- A run takes the lifecycle lock of its worker twice: when it writes its run marker and when it removes it. The
  marker names the run, the lease and the process.
  [@test] ../crates/avl-vm/src/worker/lease/tests.rs
  [@test] ../crates/avl-vm/src/worker/run_marker/tests.rs
  [@test] ../crates/avl-vm/src/daemon/leased/tests.rs

- A live run of another process blocks the commands that change its worker. `lease release`, `pool start`,
  `pool stop`, `exec`, `peekaboo` and the `daemon` verbs `start`, `restart` and `stop` then refuse `lease_busy`.
  The refusal names the run.
  [@test] ../crates/avl-vm/src/worker/lease/tests.rs
  [@test] ../crates/avl-vm/src/worker/worker/tests.rs
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs
  [@test] ../crates/avl-vm/src/lane/observe/exec/tests.rs

- A marker of a dead process is stale. The next operation under the lock removes it, and a note says so. The guest
  check of the release still decides about an iteration that the dead process left.
  [@test] ../crates/avl-vm/src/worker/lease/tests.rs
  [@test] ../crates/avl-vm/src/worker/run_marker/tests.rs

- `lease show`, `daemon status`, `daemon log`, `ls` and `pull` answer during a run. `lease show` with the receipt
  names the run.
  [@test] ../crates/avl-vm/src/worker/lease/tests.rs
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs
  [@test] ../crates/avl-vm/src/lane/observe/ls/tests.rs
  [@test] ../crates/avl-vm/src/lane/observe/pull/tests.rs

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

- A lane IDE is a supervisor run in the slot of its own context. A cancel of the daemon run does not stop it.
  [@test] ../crates/avl-guest/src/ide/launch/tests.rs

- A daemon restart for a change of the stable tier keeps each live lane IDE of the same product. The decision and
  the timing line say `keep`.
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs
  [@test] ../crates/avl-guest/src/ide/gc/tests.rs

- A run on a healthy daemon that holds no running lane IDE launches the IDE again. A lease release causes this
  state. The decision and the timing line say `relaunch`.
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs

- The push offers the context plugin files with the test jars, and uploads only the files that the guest does not
  hold. When only the context plugins changed, the run relaunches the IDE and refreshes no share. The decision and
  the timing line say `relaunch`, and the reason is `the bridge plugin changed`. No lane distribution carries the
  bridge plugin, so a bridge edit moves no product digest and needs no remount. A daemon record without a plugin
  identity decides nothing on it.
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs
  [@test] ../crates/avl-vm/src/daemon/run/tests.rs

- Every other daemon start stops every lane IDE of the worker. A restart that remounts a VirtioFS share also stops
  them, because each IDE runs from the share.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs

- `daemon stop` and a lease release stop every lane IDE of the worker. The next holder gets no IDE of the previous
  runs. A pool recycle stops them too, before it unmakes the worker.
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs
  [@test] ../crates/avl-vm/src/worker/lease/tests.rs

- A daemon start whose stop of the lane IDEs fails refuses. It remounts nothing and stages nothing.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs

- A remount fails closed while a lane IDE runs. After the quiesce, a run that is not finished refuses
  `daemon_mount_quiesce_failed`.
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs

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

- A recycle never touches a worker that another holder leased. `pool recycle <worker>` on such a worker is refused
  as `worker_leased`. That refusal names the command which releases the lease, and the recycle of the holder. The
  worker keeps its lease, its machine and its clone.
  [@test] ../crates/avl-vm/src/worker/worker/tests.rs (`recycle_refuses_a_leased_worker_and_deletes_nothing`)

- The holder recycles the worker of its lease with `--lease-file FILE pool recycle <worker>`, and the recycle gives
  the lease back. A guest that refuses does not stop it, because the recycle discards that guest. This is the
  repair for a lease that `lease release` cannot give back.
  [@test] ../crates/avl-vm/src/worker/worker/docker/tests.rs (`a_holder_recycles_its_leased_worker_under_its_receipt_and_gives_the_lease_back`)

- A receipt authorizes the recycle of its own worker only. A recycle of another worker or of `all` under that receipt
  is refused as `usage`, and nothing is deleted.
  [@test] ../crates/avl-vm/src/worker/worker/docker/tests.rs (`a_receipt_does_not_authorize_the_recycle_of_another_target`)

- `pool recycle all` recycles the workers which it can, and reports each worker which it did not recycle.
  The answer names the recycled workers, and gives one refusal code for each other worker. A recycle which
  did not reach every target is refused as `recycle_incomplete`, and that answer is the refusal detail.
  [@test] ../crates/avl-vm/src/worker/worker/tests.rs

- `pool gc` keeps a busy worker and goes on with the other workers. Another operation holds the lifecycle lock of a
  busy worker, such as a start. The answer names that worker in `kept` as `busy`.
  [@test] ../crates/avl-vm/src/worker/worker/docker/tests.rs

- `recycle_incomplete` exits 75 when a lease or another lifecycle operation was the only thing in the way. It
  exits 1 when a worker was broken. A caller must not read a partial recycle as a fresh pool.
  [@test] ../crates/avl-vm/src/worker/worker/tests.rs

- `pool start all` and `pool stop all` change the workers which they can, and report each worker which they did
  not. The answer names the state of each changed worker, and gives one refusal code for each other worker. A
  start or a stop which did not reach every target is refused as `start_incomplete` or `stop_incomplete`, and that
  answer is the refusal detail. The refusal exits 75 when each refusal of a worker exits 75, as a lease or another
  lifecycle operation does. In all other cases it exits 1. `pool start <worker>` and `pool stop <worker>` answer
  the refusal of that worker.
  [@test] ../crates/avl-vm/src/worker/worker/tests.rs
  [@test] ../crates/avl-vm/src/worker/worker/docker/tests.rs

- An interrupt ends `pool start all`, `pool stop all` and `pool recycle all` at once. The answer is the refusal of
  the interrupted worker. The workers after it stay as they are.
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
- [ADR 0234](../../../../plugins/air/docs/decisions/0234-the-guest-agent-launches-the-lane-ide.md)
- [Flow UI Scenarios](../../../../plugins/air/spec/docs/flow-ui-scenarios.spec.md)
- [Scenario Traces](scenario-trace.spec.md)
