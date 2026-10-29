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
  - ../crates/avl-guest/src/lane/identity.rs
  - ../crates/avl-guest/src/lane/context.rs
  - ../crates/avl-guest/src/lane/channel.rs
  - ../crates/avl-guest/src/lane/ide.rs
  - ../crates/avl-guest/src/lane/inputs.rs
  - ../crates/avl-guest/src/lane/iteration.rs
  - ../crates/avl-guest/src/lane/readiness.rs
  - ../crates/avl-guest/src/lane/program.rs
  - ../crates/avl-guest/src/lane/values.rs
  - ../crates/avl-guest/src/lane/host_ids.rs
  - ../crates/avl-guest/src/lane/live.rs
  - ../crates/avl-guest/src/lane/live/central.rs
  - ../crates/avl-guest/src/lane/live/home.rs
  - ../crates/avl-guest/src/lane/recycle.rs
  - ../crates/avl-guest/src/lane/watchdog.rs
  - ../crates/avl-guest/src/lane/stream.rs
  - ../crates/avl-guest/src/lane/build_log.rs
  - ../crates/avl-guest/src/lane/ide/restart.rs
  - ../crates/avl-guest/src/lane/context/options/*.xml
  - ../crates/avl-guest/src/supervisor/thread_dump.rs
  - ../crates/avl-bridge/src/lifecycle.rs
  - ../crates/avl-wire/src/ide.rs
  - ../crates/avl-wire/src/supervisor.rs
  - ../crates/avl-wire/src/verb.rs
  - ../crates/avl-wire/src/pull.rs
  - ../crates/avl-wire/src/digest.rs
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
  - ../crates/avl-junit/src/lib.rs
  - ../crates/avl-junit/src/cases.rs
  - ../crates/avl-report/src/teamcity.rs
  - ../crates/avl-junit/src/performance.rs
  - ../crates/avl-guest/src/lane/performance.rs
  - ../crates/avl-guest/src/lane/run_result.rs
  - ../crates/avl-vm/src/daemon/performance.rs
  - ../crates/avl-vm/src/daemon/results.rs
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

The UI-lane controller runs the Air UI scenarios on a worker. It leases the worker and starts the lane daemon
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

- The lane harness modules are the bridge and the fake agents. The bridge holds the state reset and the authored
  bodies. The lane-wide paths are the controller and the lane files of `plugins/air/tests/integration`. A change there can break every suite of its lanes, so a
  narrower answer would hide a failure.
  [@test] ../crates/avl-affected/src/affected/tests.rs

- The two tag relations answer before the spec and module relations, and a path whose tag answered uses no
  other relation. The module relation is the coarse one, because a `module:` endpoint covers its own module
  and every module under it.
  [@test] ../crates/avl-affected/src/affected/tests.rs

- A path which no tag answered reaches the suites of its owning specs. A spec owns a path through its
  `targets`, and its test links name the suites. For a path with no tag, the spec relation and the module
  relation answer together.
  [@test] ../crates/avl-affected/src/affected/tests.rs

- An authored suite is mapped the same way as a generated suite, through its suite document.
  [@test] ../crates/avl-affected/src/affected/tests.rs

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
  path. For each lane, the selection is the lane and one class filter for each suite. `--base <rev>` compares a
  Kotlin file of the test bridge with that revision, as `suites --base` does, and the next command of `suites`
  repeats it.
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

- The lane table names the document lane of each explicit-only lane, `UI_PERF` for `ui-perf`. A changed path, a flow
  and a suite id never reach a suite of such a lane. A suite id of one is refused as `explicit_only_lane`, and the
  refusal names `--lane <lane>`. The table refuses a lane that states both a catalog lane and a document lane.
  [@test] ../../bt/crates/bt-core/src/suites/tests.rs (`no_flow_or_suite_id_reaches_a_suite_of_an_explicit_only_lane`)
  [@test] ../../bt/crates/bt-core/src/lanes/tests.rs (`the_parser_refuses_a_document_lane_that_contradicts_its_lane`)
  [@test] ../crates/avl-affected/src/affected/tests.rs (`a_harness_path_reaches_no_suite_of_an_explicit_only_lane`)

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

- The runtime descriptor names three lane inputs: the fake agent jar, the jar of the suite documents and the jar of
  the lane documents. The controller pushes them by digest with the context plugin files. No identity reads them, so
  an edit of the fake agent refreshes no share, keeps the IDE and keeps the daemon. The descriptor refuses a lane input
  that the data or another field of the descriptor also names. Each iteration names the digest of each lane input in
  the journal.
  [@test] ../crates/avl-wire/src/runtime/tests.rs (`every_way_the_descriptor_can_be_wrong_is_its_own_refusal`)
  [@test] ../crates/avl-vm/src/daemon/build/tests.rs (`a_changed_fake_agent_jar_changes_no_identity_and_only_its_pushed_digest`)
  [@test] ../crates/avl-vm/src/daemon/run/tests.rs (`push_jars_uploads_only_missing_digests`)
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs (`an_iteration_names_the_digests_of_the_lane_inputs`)

- One reader reads the suite documents and the lane documents from a directory or from a zip. The zip can be the
  resource jar that the controller pushes. Both sources give the same names and the same bytes. A source with no
  document is refused, and so is a source that is neither a directory nor a zip.
  [@test] ../crates/avl-wire/src/program/documents/tests.rs
  [@test] ../../../../plugins/air/tests/integration/vm-contract/lane_documents.rs (`the_jar_and_the_directory_give_the_committed_lane_documents`)

- The same reader gives the digest of the flow vocabulary: the lowercase hex SHA-256 of the bytes of
  `flow-vocabulary.json` at the root of the source. A source without that file is refused by its name. No document
  states the digest, and the bridge module holds a copy of the file with the same bytes.
  [@test] ../crates/avl-wire/src/program/documents/tests.rs (`a_source_without_the_flow_vocabulary_file_is_refused_by_name`)
  [@test] ../crates/avl-guest/src/lane/iteration/tests.rs (`the_selection_carries_the_digest_of_the_flow_vocabulary_file_and_a_root_without_it_is_refused`)
  [@test] ../../../../plugins/air/tests/integration/vm-contract/lane_documents.rs (`the_jar_the_directory_and_the_bridge_give_one_flow_vocabulary_file`)

- VM preparation installs and probes no agent CLI. An existing image can keep an unused installation.
  [@test] ../crates/avl-vm/src/lane/env/tests.rs
  [@test] ../crates/avl-guest/src/image/provision/tests.rs

- The launch identity reads the static flags of the runtime descriptor, the boot settings of the controller and the
  daemon environment. A changed static flag restarts the daemon.
  [@test] ../crates/avl-vm/src/daemon/build/tests.rs (`the_launch_digest_is_composed_of_the_static_launch_the_boot_settings_and_the_environment`)
  [@test] ../crates/avl-vm/src/daemon/build/tests.rs (`a_changed_static_flag_changes_the_launch_digest`)

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

- The lane daemon runs no JVM, and a daemon start copies no JBR to the guest. The `jbr` section of the runtime
  descriptor names only the platform of the guest. No identity reads a file of the JBR. The product identity reads the
  fingerprint of the distribution, and the fingerprint covers the JBR.
  [@test] ../crates/avl-vm/src/daemon/build/tests.rs (`a_changed_or_missing_jbr_moves_no_identity`)
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

- The home of the lane daemon is the directory `home` in its state directory, and its IDE root is on the guest disk.
  Each one has its own flag, so no path derives from a checkout. The boot of the verb makes both.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs
  [@test] ../crates/avl-guest/src/lane/channel/tests.rs (`the_boot_makes_the_directories_of_the_run_readable_only_by_its_account`)

- A daemon start stages no runtime generation. In the phase `free-space`, the start first removes the retired roots
  `daemon-runtime`, `out` and `build-download` of the guest data with one `rm -rf`. An older controller wrote them,
  and nothing reads them now. A missing directory is no error, and a failed removal is a note. Then the start checks
  the free space of the guest, before the supervisor start. A guest below the floor refuses the start with
  `guest_disk_full`. The check deletes nothing.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs (`a_full_start_boots_polls_and_records_its_state`)
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs (`a_full_guest_disk_refuses_the_start_before_the_supervisor_start`)
  [@test] ../crates/avl-vm/src/daemon/space/tests.rs (`the_retired_roots_go_with_one_rm_and_a_failure_is_a_note`)
  [@test] ../crates/avl-vm/src/daemon/space/tests.rs (`below_the_floor_the_run_is_refused_with_the_command_that_finds_the_culprit`)

- The run of the lane daemon starts in its state directory. Before the supervisor start, the daemon start makes that
  directory and the directory of the guest agent. Then it removes the state file of the previous daemon.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs (`a_full_start_boots_polls_and_records_its_state`)
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs (`the_start_removes_the_state_file_of_the_previous_daemon_before_the_supervisor_start`)

- A run that rules out each of its classes is not a run that matched no class. The controller gives the
  reason of each class, and does not tell the person to look at the selector.
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs

#### The lane identity and the context files

The lane daemon of the guest agent derives the identity of a lane IDE from the lane document. It hands the derived
values to the IDE as files, so the IDE computes no secret.

- The config key hashes one line for each input. The first lines are the lane, the digest of the lane document and
  the product stamp. Then come each CLI runtime by its name and the digest of the option file templates.
  [@test] ../crates/avl-guest/src/lane/identity/tests.rs (`the_identity_of_the_fixed_key_is_the_golden`)

- A blank product stamp is no stamp. The key is `air-`, the lowercase lane, a dash and 16 hex digits.
  [@test] ../crates/avl-guest/src/lane/identity/tests.rs (`a_lane_without_a_stamp_states_no_stamp_line_and_its_prefix_is_the_lowercase_lane`)

- A change of any input of the key changes the key.
  [@test] ../crates/avl-guest/src/lane/identity/tests.rs (`every_input_changes_the_key`)

The port of each lane service derives from the key, inside the window of the service:

| service | window |
| --- | --- |
| the fake ACP registry | 26000 to 29999 |
| the control server in the IDE | 30000 to 30499 |
| the feedback capture | 30500 to 30999 |
| the JetBrains Cloud emulator | 32000 to 32499 |

The UI-test bridge has the fixed port 31347, and the Codex mock model has the fixed port 31500.

- A derived port that is the port of the lane daemon moves to the next port of its window.
  [@test] ../crates/avl-guest/src/lane/identity/tests.rs (`a_derived_port_steps_over_the_daemon_port_and_wraps_at_the_end_of_its_window`)

- The control server in the IDE takes the port that `<key>|ide-control` picks in its window. No other server reserves a
  port of that window.
  [@test] ../crates/avl-guest/src/lane/identity/tests.rs (`the_ide_control_port_is_the_port_that_its_seed_picks`)

- The windows do not overlap, and they hold no fixed port.
  [@test] ../crates/avl-guest/src/lane/identity/tests.rs (`every_window_stays_clear_of_the_fixed_ports_and_of_the_ephemeral_floor`)

- A lane secret derives from the key and a purpose. Two purposes give two secrets.
  [@test] ../crates/avl-guest/src/lane/identity/tests.rs (`a_secret_tells_two_purposes_apart`)

Before a launch, the lane daemon writes these files into the context:

| file | content |
| --- | --- |
| `config/options/*.xml` | the five option files |
| `config/air-ui-test/token` | the token of the UI-test bridge |
| `config/air-ui-test/lane-services.json` | the lane-service token and the URL of each lane service |
| `config/air-ui-test/control.json` | the control server in the IDE and its credentials |
| `home/.air-lane/acp-scenario-agent.jar` | the copy of the fake agent jar |
| `home/.jetbrains/acp.json` | one entry for each local agent |
| `bin/<runtime>` | one launcher for each CLI runtime |
| `home/.codex/config.toml` | the Codex mock model and the trusted project |
| `project/<name>/.git`, `project/<name>-origin.git` | the Git remote of the project, for a lane with a Cloud agent |

- The files of the context of the `UI` lane are the golden `testdata/lane-context/ui-context.txt`.
  [@test] ../crates/avl-guest/src/lane/context/tests.rs (`the_context_of_the_ui_lane_is_the_golden`)

- The option file templates have a pinned digest.
  [@test] ../crates/avl-guest/src/lane/context/tests.rs (`the_option_templates_have_the_pinned_digest`)

- `acp.json` equals the golden `testdata/lane-context/ui-acp-json.dump`, except the values that derive from the key.
  [@test] ../crates/avl-guest/src/lane/context/tests.rs (`acp_json_equals_the_recorded_dump_except_the_key_derived_values`)

- Only the account can enter `config/air-ui-test` and read its files. Each launcher is executable.
  [@test] ../crates/avl-guest/src/lane/context/tests.rs (`the_private_files_and_the_launchers_get_their_modes`)

- A controlled agent gets the URL and the credential of the control server that serves it.
  [@test] ../crates/avl-guest/src/lane/context/tests.rs (`the_files_carry_the_values_of_the_identity`)

- `control.json` lists each agent whose control owner in the lane document is the IDE, and the terminal sign-in of
  each one.
  [@test] ../crates/avl-guest/src/lane/context/tests.rs (`the_files_carry_the_values_of_the_identity`)

- A lane with a Cloud agent makes its project a Git repository with one commit on `main`.
  [@test] ../crates/avl-guest/src/lane/context/tests.rs (`a_lane_with_a_cloud_agent_makes_its_project_a_repository_with_an_origin`)

- The bare repository `<name>-origin.git` beside the project is its `origin`, and it holds that commit.
  [@test] ../crates/avl-guest/src/lane/context/tests.rs (`a_lane_with_a_cloud_agent_makes_its_project_a_repository_with_an_origin`)

- A lane whose IDE serves no agent has no `control.json`. The daemon deletes an earlier one.
  [@test] ../crates/avl-guest/src/lane/context/tests.rs (`a_second_write_keeps_the_general_settings_and_the_next_start_file_and_drops_a_stale_control_file`)

- The daemon never writes or deletes `control-next-start.json`. An existing `ide.general.xml` stays.
  [@test] ../crates/avl-guest/src/lane/context/tests.rs (`a_second_write_keeps_the_general_settings_and_the_next_start_file_and_drops_a_stale_control_file`)

- The daemon refuses with `lane_context_input` other CLI runtimes and other services, and a live lane outside a live
  run. The reader of the lane document refuses a controlled agent without a control owner.
  [@test] ../crates/avl-guest/src/lane/context/tests.rs (`a_document_that_the_daemon_cannot_launch_as_it_states_is_refused`)

The lane daemon reads the other launch inputs from the runtime descriptor at its boot:

- The home of the distribution is `home.path` of the IDE config. The IDE runs on the JBR below that home, and a home
  without the JBR is refused.
  [@test] ../crates/avl-guest/src/lane/inputs/tests.rs (`the_distribution_home_and_its_jbr_come_from_the_ide_config`)

- A flag `-Dair.test.<name>.runtime` names the manifest of one CLI runtime. Its identity hashes the manifest, the
  interpreter and the lock.
  [@test] ../crates/avl-guest/src/lane/inputs/tests.rs (`a_runtime_flag_names_the_manifest_of_one_cli_runtime`)

- A runtime whose entry point is missing, or cannot run, is refused by name.
  [@test] ../crates/avl-guest/src/lane/inputs/tests.rs (`a_runtime_whose_entry_cannot_run_is_refused`)

#### The live lane

The lane `UI_LIVE` drives real agents on the JetBrains Central account of the developer. Its lane document states a
live context. The live context names the run input of the Central login and the host command that fills it. It also
names the declared Central runtime, the pinned proxy port and the agents that Central wires. Last, it names the live
runtimes and the unpack root of the project. No field of the lane document holds a credential.

- Only the lane `UI_LIVE` states a live context, and it cannot launch without one.
  [@test] ../crates/avl-wire/src/program/lane/tests.rs (`a_live_context_fits_the_live_lane_only`)
  [@test] ../crates/avl-wire/src/program/lane/tests.rs (`a_live_context_that_does_not_fit_its_lane_is_refused`)

- A live run is the lane daemon of a Linux guest whose environment names the run secrets directory. A host, a
  `lane-run` and a macOS guest refuse the live lane by name with `lane_live_input`.
  [@test] ../crates/avl-guest/src/lane/live/central/tests.rs (`only_the_lane_daemon_of_a_linux_guest_has_a_guest_account`)
  [@test] ../crates/avl-guest/src/lane/live/tests.rs (`a_live_lane_outside_a_live_run_is_refused_by_name`)

- `vm.cmd run` refuses a live run without the run input of its Central login before any build or lease. The refusal
  names the command that pipes the login.
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs (`a_live_run_without_its_login_input_is_refused_before_any_build`)

- A run that holds the live lane builds the descriptor `ui_daemon_linux_guest_live`, which adds the CLIs of the live
  lane, in place of `ui_daemon_linux_guest`. A build of another lane fetches none of them, and no build needs an option
  of its own. The lane test of `ui-live` runs over `ui_daemon_live`, which adds them to `ui_daemon`.
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs (`a_live_run_builds_the_live_descriptor_and_another_run_the_one_of_the_lanes`)
  [@test] ../crates/avl-vm/src/daemon/build/tests.rs (`each_guest_builds_its_own_daemon_target`)
  [@test] ../../../../plugins/air/tests/integration/vm-contract/daemon_runtime.rs (`only_the_descriptors_of_the_live_lane_carry_its_clis`)

- `vm.cmd run` refuses a run of the live lane on a macOS guest with `lane_live_input`, before the build and the lease.
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs (`a_live_run_on_a_macos_guest_is_refused_before_the_build_and_the_lease`)

- A runtime descriptor without the Central runtime or a live runtime is refused with `lane_live_input` before any
  Central step. The refusal names the missing runtimes and the descriptor of the live lane.
  [@test] ../crates/avl-guest/src/lane/live/tests.rs (`a_descriptor_without_the_live_runtimes_is_refused_naming_the_live_descriptor`)

- Before the lane IDE launches, the lane daemon provisions the guest account with the declared Central CLI. It pins
  the release track, the analytics and the port, and imports the login on stdin. Then it starts the proxy, wires each
  agent and requires the proxy on the pinned port. The login file is deleted after the import, whatever its outcome.
  [@test] ../crates/avl-guest/src/lane/live/central/tests.rs (`the_provisioning_pins_the_configuration_imports_on_stdin_starts_the_proxy_and_wires_every_agent`)
  [@test] ../crates/avl-guest/src/lane/live/central/tests.rs (`a_proxy_on_another_port_fails_the_provisioning`)
  [@test] ../crates/avl-guest/src/lane/live/central/tests.rs (`an_empty_login_input_fails_before_the_import_runs`)

- A failed Central step fails the iteration with `lane_live_central`. The refusal names the step and its exit, and
  quotes the last line of its standard error with every part of the login redacted.
  [@test] ../crates/avl-guest/src/lane/live/central/tests.rs (`a_failed_import_names_the_step_and_its_exit_code_redacts_the_secret_and_still_deletes_the_input`)
  [@test] ../crates/avl-guest/src/lane/live/central/tests.rs (`a_step_that_outlives_its_bound_fails_by_its_name`)
  [@test] ../crates/avl-guest/src/lane/live/tests.rs (`a_rejected_login_fails_at_the_import_with_the_literal_prefix_and_the_end_still_clears_the_account`)

- The lane home gets only the Central wiring of the guest account. That is the proxy variables and the key helper of
  Claude Code, and the model provider of Codex. The files that carry a token are readable by their owner only.
  [@test] ../crates/avl-guest/src/lane/live/home/tests.rs (`a_central_launch_copies_only_the_wiring_keeps_the_trust_seed_and_writes_no_vendor_key`)
  [@test] ../crates/avl-guest/src/lane/live/home/tests.rs (`a_copied_wiring_that_names_no_local_proxy_fails_the_seed`)

- The bin directory gets the launchers of the live runtimes, the Node executables of the guest, and the Central
  guard. The guard passes only the subcommands that read on to the Central CLI, with the account home.
  [@test] ../crates/avl-guest/src/lane/live/home/tests.rs (`the_central_guard_passes_reads_to_the_central_cli_with_the_account_home_and_refuses_the_rest`)
  [@test] ../crates/avl-guest/src/lane/live/home/tests.rs (`the_launchers_export_their_environment_and_the_node_executables_are_linked`)

- The lane IDE of a live lane gets no fake ACP registry and no Codex mock model. Its project unpacks outside the
  context, below no agent instruction or configuration.
  [@test] ../crates/avl-guest/src/lane/live/tests.rs (`a_live_context_names_no_fake_registry_and_no_codex_mock`)
  [@test] ../crates/avl-guest/src/lane/live/home/tests.rs (`a_project_below_agent_instructions_is_refused`)

- The host id `refresh-live-wiring` copies the wiring again when the account re-wired. The host id
  `adopt-leftover-junie-sessions` hands each Junie session of the live project to the reset.
  [@test] ../crates/avl-guest/src/lane/live/home/tests.rs (`a_refresh_copies_the_wiring_again_only_after_the_account_re_wired`)
  [@test] ../crates/avl-guest/src/lane/live/home/tests.rs (`only_the_junie_sessions_of_the_lane_project_are_leftovers`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`the_live_host_ids_refresh_the_wiring_and_hand_the_junie_leftovers_to_the_reset`)

- At the end of each iteration, the lane daemon deletes the credential copies of the lane home. This also occurs after
  a failed launch, and always before the verdict. Then it stops the proxy, ends the agent processes of the account, and deletes
  the Central state and the agent homes of the account. It never runs `central logout`.
  [@test] ../crates/avl-guest/src/lane/live/home/tests.rs (`the_end_of_a_run_deletes_the_copied_wiring_and_the_account_keeps_its_own`)
  [@test] ../crates/avl-guest/src/lane/live/central/tests.rs (`the_teardown_stops_the_proxy_and_deletes_the_accounts_central_and_agent_homes_whole_never_logging_out`)
  [@test] ../crates/avl-guest/src/lane/live/tests.rs (`one_live_iteration_provisions_seeds_and_tears_down_with_fake_clis`)

- No note, refusal, report or log line of a live run holds a value of the login or of the copied wiring.
  [@test] ../crates/avl-guest/src/lane/live/tests.rs (`one_live_iteration_provisions_seeds_and_tears_down_with_fake_clis`)
  [@test] ../crates/avl-guest/src/lane/live/tests.rs (`a_rejected_login_fails_at_the_import_with_the_literal_prefix_and_the_end_still_clears_the_account`)

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
  restart, a remount, a relaunch, a reuse, and pushed files. Each choice is a `decision` record with
  its reason, in every output form.
  [@test] ../crates/avl-wire/src/progress/tests.rs
  [@test] ../crates/avl-vm/src/console/state/tests.rs

- A pushed file that the host rebuilt while the iteration ran is a note, not a verdict diagnostic. The pushed files
  are the context plugin files and the lane inputs. The terminal and the run journal show the note, and the report
  carries it in `notes`. The note names the files. The status follows the counts.
  [@test] ../crates/avl-report/src/report/tests.rs
  [@test] ../crates/avl-vm/src/daemon/build/tests.rs (`pushed_file_drift_names_a_file_that_was_rebuilt_under_the_run`)
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs

- The run journal holds the life of the lane IDE. An `ideLaunched` record names the pid, the launch name, the
  launch key and the log directory. An `ideExited` record names the pid, the exit code and the signal. Each record
  names its time. The one exception is the `ideLaunched` record of an IDE that outlived the previous daemon.
  [@test] ../crates/avl-wire/src/daemon/tests.rs

- An `ideReady` record follows each wait for `READY` that holds: after a launch, on a reused IDE, and after a reload of
  the bridge plugin. It names the launch, the pid that the bridge answers with, and `readyAfterMs`, the time of the
  wait.
  [@test] ../crates/avl-wire/src/daemon/tests.rs (`the_ide_life_records_carry_their_time_and_the_ready_record_its_wait`)
  [@test] ../crates/avl-guest/src/lane/ide/tests.rs (`the_ready_record_names_the_launch_the_pid_and_the_wait`)

- A `timing` record holds the time of a step that no trace bundle holds: its `step`, its `durationMs` and its end.
  The steps are `bare-launch` and `bare-program` of a suite of a bare IDE, the `services-reset` of each reset, and
  the `recorder-close` of each recorded scenario. Its `subject` names the suite or the scenario. After the summary
  of its iteration, `lane-run` writes `lane-run-xml`, `lane-run-quit`, `lane-run-end` and, for a run that is not
  green or that failed an attempt, `lane-run-evidence`. The JUnit XML and the TeamCity messages ignore every `timing`
  and `ideReady` record.
  [@test] ../crates/avl-wire/src/daemon/tests.rs (`a_timing_record_names_its_step_its_duration_and_its_subject`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_green_scenario_runs_between_two_resets_and_writes_the_golden_transcript`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_bare_suite_runs_last_on_its_own_ide_and_its_one_request_is_its_instruction`)
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`the_steps_after_the_iteration_are_timing_records_after_the_summary`)
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`a_run_that_its_rerun_made_green_keeps_the_evidence_of_its_failed_attempt`)
  [@test] ../crates/avl-report/src/teamcity/tests.rs (`the_recorded_stream_renders_its_golden`)

- The run journal holds each line that the daemon writes to its error stream during a run. Each line is an `output`
  record. An example is the line that names a recycle of the lane IDE. The text has no stamp of the daemon, and the
  record has its own time. An `output` record is not progress. It moves no deadline of the daemon or of the
  controller. No `output` record of a run follows its summary.
  [@test] ../crates/avl-wire/src/daemon/tests.rs
  [@test] ../crates/avl-vm/src/daemon/run/tests.rs

- The watchdog evidence may name a file of an IDE context. Examples are a heartbeat screenshot and the thread
  dump before a kill.
  [@test] ../crates/avl-wire/src/report/tests.rs

- The dumps that each `slowStep` record names are evidence too. The controller fetches them beside the report,
  after the files of the expiry, and at most eight. The journal keeps the record as a `runProgress` event.
  [@test] ../crates/avl-wire/src/report/tests.rs
  [@test] ../crates/avl-wire/src/daemon/tests.rs

- The lane daemon has a watchdog of its own. Between two scenarios the progress gap applies. During a scenario its
  active execution timeout applies.
  [@test] ../crates/avl-guest/src/lane/watchdog/tests.rs (`the_deadlines_follow_the_phase_and_the_emergency_caps_the_run`)

- An emergency deadline caps the run of the lane daemon. It holds a gap for each scenario and two more, the timeout
  of each scenario, and one minute.
  [@test] ../crates/avl-guest/src/lane/watchdog/tests.rs (`the_deadlines_follow_the_phase_and_the_emergency_caps_the_run`)

- On expiry the watchdog of the lane daemon kills the IDE run with a thread dump, and then writes `watchdogExpired`.
  So a stream or a standard error that blocks cannot hold the kill. The scenario in flight ends as `ABORTED`, and the
  run starts no further scenario.
  [@test] ../crates/avl-guest/src/lane/watchdog/tests.rs (`an_intruder_that_survives_its_dismissal_expires_the_run_and_kills_the_ide`)
  [@test] ../crates/avl-guest/src/lane/watchdog/tests.rs (`the_kill_comes_before_the_expiry_record_so_a_blocked_stream_does_not_hold_it`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_scenario_past_its_timeout_is_aborted_and_no_later_scenario_starts`)

- The kill ends the IDE run that the last launch started, also when the expiry comes during a relaunch. A launch
  publishes its run as the target, and a quit clears it.
  [@test] ../crates/avl-guest/src/lane/ide/tests.rs (`the_kill_target_is_the_run_of_the_last_launch_and_a_quit_clears_it`)

- After the kill the watchdog waits one minute for the run thread to complete. A run thread that does not complete in
  that time waits on something other than the IDE, and the run log says so.
  [@test] ../crates/avl-guest/src/lane/watchdog/tests.rs (`a_run_thread_that_completes_after_the_kill_ends_the_wait_without_a_note`)

- `lane-run` and the lane daemon reap each supervisor that they spawned and that ended. A supervisor that nobody
  reaps stays a zombie, and the identity check reads a zombie as a live supervisor.
  [@test] ../crates/avl-guest/src/supervisor/tests.rs (`an_ended_supervisor_stays_a_zombie_until_its_parent_reaps_it`)
  [@test] ../crates/avl-guest/src/supervisor/tests.rs (`a_zombie_supervisor_reads_as_alive_and_only_its_reap_repairs_a_run_it_left_running`)

- The first modal intruder of a run gets one dismissal. An intruder that the next tick still sees expires the run as
  `ide_intrusion`, and the detail names the dismissal.
  [@test] ../crates/avl-guest/src/lane/watchdog/tests.rs (`an_intruder_that_survives_its_dismissal_expires_the_run_and_kills_the_ide`)

- A step of the lane daemon that is open past 30 s gets the dump of the IDE once, at most four for each run. The dump
  is a file under `slow-steps`, a `slowStep` record, and an attachment of the trace.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_failed_slow_instruction_is_typed_photographed_and_dumped`)

- The dump of the IDE comes from the dump route of the resident plugin, not from the request endpoint of the bridge.
  The resident plugin never unloads, so a step that hangs in a reload of the bridge plugin or of Air gets the dump too.
  [@test] ../crates/avl-guest/src/lane/watchdog/tests.rs (`the_ide_dump_comes_from_the_resident_route_while_the_bridge_unloads`)
  [@test] ../../../../plugins/air/tests/integration/bridge/tests/AirUiTestDumpRouteTest.kt

- The controller writes the JUnit XML of a run from its run stream. The XML holds one `testsuite` for each test
  class. Each scenario is one `testcase`: its class is the `testClassName` of the document, and its name is the
  scenario. A failure holds its type, the first line of its message, and the message and the detail as CDATA. A skip
  holds its reason. A prologue failure is one case named after the suite.
  [@test] ../crates/avl-junit/src/tests.rs (`the_recorded_stream_renders_its_golden`)
  [@test] ../crates/avl-junit/src/tests.rs (`the_rendered_document_reads_back_with_the_counts_of_the_stream`)
  [@test] ../crates/avl-junit/src/cases/tests.rs (`a_typed_failure_keeps_its_type_message_and_detail`)

- A run that the stream cuts off fails in the XML. A scenario that started and never finished fails as
  `unfinished`. A class of the plan that no record reached fails as `unreported`, in one case named after the class.
  A class of the plan and a class of a record match by the name without the package.
  [@test] ../crates/avl-junit/src/tests.rs (`a_cut_off_run_fails_its_unfinished_scenario_and_the_suite_it_never_reached`)
  [@test] ../crates/avl-junit/src/cases/tests.rs (`a_plan_of_simple_names_is_reached_by_records_of_fqns`)
  [@test] ../crates/avl-report/src/report/tests.rs (`a_plan_of_simple_names_is_covered_by_records_of_fqns`)
  [@test] ../crates/avl-report/src/teamcity/tests.rs (`a_plan_of_simple_names_is_reached_by_records_of_fqns`)

- The XML of each iteration is `<iterationId>.xml` beside the report of the iteration. The XML of the command goes
  to the file that `JB_XML_OUTPUT_FILE` names, else to the file that `XML_OUTPUT_FILE` names. The controller writes
  it again after each iteration and at the end of the run. A write that fails is a note, and the run goes on.
  [@test] ../crates/avl-vm/src/daemon/results/tests.rs (`a_bazel_test_gets_the_xml_of_the_command_and_each_report_gets_its_own`)
  [@test] ../crates/avl-vm/src/daemon/results/tests.rs (`a_write_that_fails_is_noted_when_the_readers_detach`)
  [@test] ../crates/avl-junit/src/tests.rs (`the_named_file_wins_over_the_bazel_file_and_a_blank_name_is_none`)

- The controller is the build step of a TeamCity build when `TEAMCITY_VERSION` is set and `XML_OUTPUT_FILE` is
  not. Then it writes the TeamCity service messages on stdout as the records arrive. A test class is a suite, and a
  case is `<testClassName>.<scenario>`. A failure is `testFailed` with the failure chain as its details. A skip is
  `testIgnored`. `testFinished` holds the duration. The flow of each message is its worker.
  [@test] ../crates/avl-report/src/teamcity/tests.rs (`the_recorded_stream_renders_its_golden`)
  [@test] ../crates/avl-report/src/teamcity/tests.rs (`each_worker_is_its_own_flow`)
  [@test] ../crates/avl-report/src/teamcity/tests.rs (`the_build_step_is_a_teamcity_agent_outside_a_bazel_test`)
  [@test] ../crates/avl-vm/src/daemon/results/tests.rs (`a_teamcity_build_step_gets_the_messages_of_the_stream_and_of_each_trace`)

- A note of a scenario is a text `testMetadata`, and a metric of a scenario is a `number` one. A trace that
  arrives publishes its zip once under `air-traces/<worker>/<iterationId>`. It names the bundle as an `artifact` and
  its video as a `video`.
  [@test] ../crates/avl-report/src/teamcity/tests.rs (`the_recorded_stream_renders_its_golden`)
  [@test] ../crates/avl-report/src/teamcity/tests.rs (`a_zip_is_published_once_for_its_bundles`)

- For a run on the lane daemon, the controller writes the IJ Perf file `metrics.performance.json` of each scenario
  that reported metrics. `lane-run` writes the same file after its run. The lane document names the series of each
  such scenario. A series holds the test class, the scenario, the dashboard project and method, and an artifact path.
  The controller writes the file to `<iterationId>.performance/<artifactPath>` beside the report of the iteration. Its
  build is the `build.txt` of the IDE distribution without the product code. A TeamCity build step publishes the file
  once under its artifact path.
  [@test] ../crates/avl-junit/src/performance/tests.rs (`the_recorded_stream_renders_its_golden`)
  [@test] ../crates/avl-junit/src/performance/tests.rs (`the_build_drops_the_product_code_and_keeps_every_other_form`)
  [@test] ../crates/avl-report/src/teamcity/tests.rs (`a_file_is_published_once_into_its_directory`)
  [@test] ../crates/avl-junit/src/performance/tests.rs (`a_lane_that_names_series_plans_them_with_the_build_and_another_lane_plans_nothing`)
  [@test] ../crates/avl-junit/src/performance/tests.rs (`the_ide_build_of_a_build_file_drops_the_product_code_and_names_a_read_failure_and_an_empty_file`)
  [@test] ../crates/avl-junit/src/teamcity/tests.rs (`a_file_is_published_into_its_directory_with_its_path_escaped`)
  [@test] ../crates/avl-vm/src/daemon/results/tests.rs (`a_teamcity_build_step_writes_and_publishes_the_ij_perf_file_of_each_planned_scenario`)

- A metric whose name ends with `Ms` is a duration. A metric whose name starts with `growth.` is the counter
  `<name>.x100` of its value times 100. Every other metric is a counter. The file keeps whole numbers, so each value
  is rounded, and a half goes up.
  [@test] ../crates/avl-junit/src/performance/tests.rs (`a_duration_a_growth_and_a_counter_keep_whole_numbers`)
  [@test] ../crates/avl-junit/src/performance/tests.rs (`a_value_out_of_the_range_of_an_int_is_held_to_it`)

- A scenario that reported metrics and has no series, such as a warm-up, gets no file and no note. Its metrics still
  reach TeamCity as `testMetadata`. A plan that the controller or `lane-run` cannot read is a note. A lane document
  whose series collide or leave their directory is refused.
  [@test] ../crates/avl-vm/src/daemon/results/tests.rs (`a_scenario_with_metrics_and_no_series_gets_no_file_and_no_note`)
  [@test] ../crates/avl-junit/src/performance/tests.rs (`a_case_with_metrics_and_no_series_writes_no_file`)
  [@test] ../crates/avl-junit/src/performance/tests.rs (`a_plan_that_cannot_be_made_says_why`)
  [@test] ../crates/avl-guest/src/lane/performance/tests.rs (`a_plan_that_cannot_be_made_is_a_note`)
  [@test] ../crates/avl-wire/src/program/lane/tests.rs (`a_performance_series_that_would_collide_or_leave_its_directory_is_refused`)

- The run report is of schema 3. It holds one case for each scenario, each failed container and each skipped
  container of the run stream, in the order of their start. A case that never finished is an active test instead.
  The report holds no count of a JUnit XML, no source of one and no suite. A report of schema 2 is refused by its
  version.
  [@test] ../crates/avl-report/src/report/tests.rs (`a_healthy_run_is_passed_and_explains_nothing`)
  [@test] ../crates/avl-report/src/report/tests.rs (`the_cases_are_in_start_order`)
  [@test] ../crates/avl-report/src/report/tests.rs (`the_stream_names_the_test_a_killed_run_died_on`)
  [@test] ../crates/avl-wire/src/report/tests.rs (`a_report_of_schema_2_is_refused_by_its_version`)

- A failure of the report keeps the type, the message and the detail that the stream sent.
  [@test] ../crates/avl-report/src/report/tests.rs (`a_typed_failure_keeps_its_type_message_and_detail`)

- Each count of the summary is compared with the records of its stream. A count that differs is a diagnostic, so
  the run is an `infrastructure_error`.
  [@test] ../crates/avl-report/src/report/tests.rs (`the_summary_and_the_stream_are_reconciled_field_by_field`)

- A run that failed as a whole has a `runFailed` or a `watchdogExpired` record and no failed case. Its record is
  its diagnostic, and its `hasFailures` contradicts nothing.
  [@test] ../crates/avl-report/src/report/tests.rs (`a_run_that_failed_as_a_whole_explains_its_has_failures`)

- A skipped case names the reason of its `testSkipped` record as `reason`, as the JUnit XML does. A case that ran
  has no reason.
  [@test] ../crates/avl-report/src/report/tests.rs (`a_skipped_case_carries_the_reason_of_its_skip`)

- The recorder is the second account of each case. The case names the status of its trace bundle as `recorded`. A
  status of the recorder's `done` that contradicts the stream is a diagnostic. A case with no bundle and a bundle the
  recorder never closed are not compared, and a traces error decides nothing.
  [@test] ../crates/avl-report/src/report/tests.rs (`the_recorder_is_the_second_account_of_each_case`)
  [@test] ../crates/avl-report/src/report/tests.rs (`traces_are_carried_and_never_decide_the_verdict`)

- The controller reads no result file of the guest. A shard verdict and a flake summary are of aggregate schema 2.
  A shard verdict holds the cases of every shard in the order of their start.
  [@test] ../crates/avl-report/src/aggregate/tests.rs (`merged_cases_are_ordered_by_instant_across_shards`)
  [@test] ../crates/avl-wire/src/report/aggregate/tests.rs

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

- The boot poll accepts only a state file that names the pid of its run. The supervisor gives that pid. The poll
  continues while the run has no pid or the file names another pid. A boot period that ends on a file of another pid
  refuses with `daemon_state_file_foreign`. The refusal gives both pids.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs (`the_boot_poll_reads_only_the_state_file_of_the_process_of_its_run`)

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
  glibc is one more. The self-check has no Node check, because the image tag pins the Node of the image. It has no
  sweep of the shared objects, because the boot of a worker does not know the distribution of a run.
  [@test] ../crates/avl-guest/src/linux/validate/tests.rs

- The lane daemon on a Linux guest proves the shared objects of its distribution at its boot. It runs `ldd` over each
  shared object below the distribution home. A library which the guest does not have refuses the boot with
  `linux_shared_object_unresolved`, and the refusal names the object and the library. A lane daemon on a macOS guest
  runs no `ldd`.
  [@test] ../crates/avl-guest/src/lane/channel/tests.rs (`the_boot_on_a_linux_guest_refuses_a_distribution_whose_library_is_missing`)
  [@test] ../crates/avl-guest/src/linux/validate/tests.rs (`the_sweep_refuses_a_shared_object_whose_library_the_guest_does_not_have`)
  [@test] ../crates/avl-guest/src/linux/validate/tests.rs (`a_distribution_that_is_a_link_is_swept_through_the_link`)

- The self-check waits for a window manager on the display. It probes once a second, for one minute. A window
  manager which registers in that time does not stop the boot. The
  report of the self-check gives the number of the try which found it. The self-check does not wait for a
  stale window manager property. It refuses that property at the first try.
  [@test] ../crates/avl-guest/src/linux/validate/tests.rs

- A verb of the guest agent refuses an argument which it cannot act on. That refusal is a usage refusal, and
  the verb touches no guest before it. For the self-check, a display of the wrong shape, a second argument and a
  trailing package list are such arguments. The retired runtime root is a second argument.
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

- The lane daemon prepares and launches the lane IDE in its own process. The guest agent has no verb that reads a
  context document or a launch document. Neither document holds a secret.
  [@test] ../crates/avl-guest/src/cli/tests.rs (`the_ide_verbs_check_their_flags`)

- A preparation and a launch check their documents before they change a file, so the lane daemon gets these checks
  too. A preparation refuses a context document that it cannot act on, and a launch refuses such a launch document.
  [@test] ../crates/avl-wire/src/ide/tests.rs
  [@test] ../crates/avl-guest/src/ide/prepare/tests.rs (`a_context_document_that_the_preparation_cannot_act_on_is_refused`)
  [@test] ../crates/avl-guest/src/ide/launch/tests.rs (`a_launch_document_that_names_a_data_directory_is_refused`)

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
  [@test] ../crates/avl-guest/src/ide/launch/tests.rs (`a_launch_document_that_names_a_data_directory_is_refused`)
  [@test] ../crates/avl-guest/src/ide/launch/tests.rs (`a_flag_that_states_a_data_path_is_refused`)

- A launch on a Linux guest refuses `ide_display_missing` when the agent's own environment has no `DISPLAY`. A
  preparation or a launch for a context whose IDE runs refuses `ide_running`.
  [@test] ../crates/avl-guest/src/ide/prepare/tests.rs
  [@test] ../crates/avl-guest/src/ide/launch/tests.rs

- A launch kills the JCEF helpers that name its context, and no other process.
  [@test] ../crates/avl-guest/src/ide/reap/tests.rs

- The bridge client reads the lifecycle state of the lane IDE through the long poll of the bridge. Each poll
  names the sequence number of the last state that it read. A poll without a number gets the current state at
  once. The client asks for a wait of 30 s or less.
  [@test] ../crates/avl-bridge/src/lifecycle/tests.rs (`a_reader_without_the_current_seq_gets_the_current_snapshot_at_once`)
  [@test] ../crates/avl-bridge/src/lifecycle/tests.rs (`a_current_reader_waits_for_the_next_snapshot`)
  [@test] ../crates/avl-bridge/src/lifecycle/tests.rs (`a_wait_ends_with_the_current_snapshot_and_the_client_cuts_it_to_the_cap`)

- The client reads a lifecycle state of schema 2 only, and it refuses an unknown field. The state names the
  digest of the loaded bridge plugin and the digest of the vocabulary.
  [@test] ../crates/avl-bridge/src/lifecycle/tests.rs (`the_snapshot_of_the_ide_fixture_is_the_golden`)
  [@test] ../crates/avl-bridge/src/lifecycle/tests.rs (`a_snapshot_outside_the_contract_is_refused`)
  [@test] ../crates/avl-bridge/src/lifecycle/tests.rs (`the_bridge_plugin_and_the_vocabulary_digest_reach_the_reader`)

- A wait for a phase ends when the IDE stays in that phase for the dwell. A gap of two or more in the
  sequence number starts the dwell again. A new process id and a failed poll also start it again.
  [@test] ../crates/avl-bridge/src/lifecycle/tests.rs (`the_dwell_starts_again_after_each_sign_of_a_missed_state`)
  [@test] ../crates/avl-bridge/src/lifecycle/tests.rs (`a_wait_reaches_the_phase_after_the_dwell`)
  [@test] ../crates/avl-bridge/src/lifecycle/tests.rs (`a_gap_in_the_sequence_starts_the_dwell_again`)
  [@test] ../crates/avl-bridge/src/lifecycle/tests.rs (`a_restarted_ide_starts_the_dwell_again`)

- A wait retries a refused connect, a passed poll deadline and a bridge that unloads until its own deadline.
  A refused token ends the wait at once. An answer outside the contract, another launch and a quitting IDE
  also end it.
  [@test] ../crates/avl-bridge/src/lifecycle/tests.rs (`a_transient_failure_is_retried_within_the_deadline`)
  [@test] ../crates/avl-bridge/src/lifecycle/tests.rs (`a_refused_token_ends_the_wait_at_once`)
  [@test] ../crates/avl-bridge/src/lifecycle/tests.rs (`an_answer_outside_the_contract_ends_a_wait`)
  [@test] ../crates/avl-bridge/src/lifecycle/tests.rs (`a_quitting_ide_and_another_launch_refuse_the_wait`)

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

- A record of a failed test or container carries the kind, the message and the detail of the failure. The record
  keeps its bytes when the controller publishes it again. An `error` text of an older journal is no field of the
  record, so the controller reads that record with no failure.
  [@test] ../crates/avl-wire/src/daemon/tests.rs (`a_failure_record_carries_the_typed_failure_and_no_error_text`)

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

- The detached `pool idle-stop` process runs in the directory of its worker, and never in the directory of the
  release. So it holds no checkout and no lease slot for the grace.
  [@test] ../crates/avl-host-sys/src/proc/tests.rs

- A release also stops each other worker whose record passed its deadline more than one poll (30 s) ago. No
  process waits for that record. It keeps a worker whose record passed its deadline by less, and a leased worker.
  It also keeps a worker whose lifecycle lock another operation holds, and a worker without a record. A failure is a
  note, and the release still succeeds.
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

- `daemon stop` of a worker that runs nothing sends no guest command. It forgets the daemon record and answers
  `stopped`, or `no daemon to stop` without a record.
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs (`a_daemon_stop_of_a_stopped_worker_forgets_the_record_and_sends_no_guest_command`)

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

- The lane daemon spawns the supervisor of a lane IDE from its own image, `/proc/self/exe` on Linux. An install of the
  guest agent renames a new binary onto the agent path while a lane daemon runs. The path of the running binary then
  names no file, and the image still starts. A macOS guest has no `/proc`. There the daemon spawns the binary at the
  path, which a rename makes the new build.
  [@test] ../crates/avl-guest/src/supervisor/tests.rs (`a_supervisor_is_spawned_from_the_image_of_this_process_on_linux`)

- A run restarts a healthy lane daemon before the run when the guest agent was reinstalled under it. An install by
  another checkout does that, such as the release of a lease there. The start of a daemon records the digest of the
  agent that it installed. A run of the lane daemon asks the guest which agent it holds now. Another digest restarts
  the daemon, and the start installs the agent of the checkout of the run. The restart keeps the lane IDE. A note of
  the run stream names both digests, and the decision says `restart`. A record without the digest decides nothing.
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs (`a_lane_daemon_whose_guest_agent_was_reinstalled_restarts`)
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs (`the_run_asks_the_guest_for_its_agent_only_for_a_lane_daemon_with_a_recorded_agent`)
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs (`a_full_start_boots_polls_and_records_its_state`)

- A run restarts a healthy lane daemon when the build made another guest agent than its start. The restart
  stops the lane IDE, so the new daemon makes a fresh context and writes its files. The reason is
  `the guest agent of the build changed`, and a note of the run stream names both digests. A restart for another
  launch digest also stops the lane IDE then.
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs (`a_lane_daemon_whose_build_made_another_guest_agent_restarts_with_a_fresh_ide`)
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs (`the_run_reads_the_agent_of_the_build_only_for_a_lane_daemon_with_a_recorded_agent`)

- A lane daemon whose build made the agent of its start serves the run as it is. So does one whose built agent the
  run cannot read.
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs (`a_lane_daemon_whose_build_made_the_agent_of_its_start_is_reused`)

- A daemon restart for another launch digest keeps each live lane IDE of the same product. Its reason is
  `the launch of the daemon changed`. The decision about the IDE and the timing line say `keep`.
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs
  [@test] ../crates/avl-guest/src/ide/gc/tests.rs

- A run on a healthy daemon that holds no running lane IDE launches the IDE again. A lease release causes this
  state. The decision and the timing line say `relaunch`.
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs

- The push offers the context plugin files and the lane inputs, and uploads only the files that the guest does not
  hold. A change of only the context plugins leaves the IDE to the lane daemon, which compares the digest of each
  plugin directory itself. The controller then relaunches nothing and refreshes no share. No lane distribution carries
  the bridge plugin, so a bridge edit moves no product digest and needs no remount. A daemon record without a plugin
  identity decides nothing on it.
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs
  [@test] ../crates/avl-vm/src/daemon/run/tests.rs

- The controller stamps each context plugin directory into a digest of its own. The run log names each directory
  with its digest. A change of only the context plugins leaves the IDE to the lane daemon. The timing line then says
  the action of its last decision about the IDE.
  [@test] ../crates/avl-vm/src/daemon/build/tests.rs (`each_context_plugin_directory_has_its_own_digest_and_a_note`)
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs (`a_change_of_the_context_plugins_alone_leaves_the_ide_to_the_lane_daemon`)
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs (`the_ide_action_of_the_lane_daemon_is_its_last_decision_about_the_ide`)

- Every other daemon start stops every lane IDE of the worker. A restart that remounts a VirtioFS share also stops
  them, because each IDE runs from the share.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs

- `daemon stop` and a lease release stop every lane IDE of the worker. The next holder gets no IDE of the previous
  runs. A pool recycle stops them too, before it unmakes the worker.
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs
  [@test] ../crates/avl-vm/src/worker/lease/tests.rs

- A daemon start whose stop of the lane IDEs fails refuses. It remounts nothing and starts no daemon.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs

- A remount stops every lane IDE first. The daemon quits its IDE through `/ide/stop`, and `ide-gc --stop-all` cancels
  every other IDE run of the worker. No route of the daemon quiesces the shares.
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs (`a_mount_digest_mismatch_stops_the_ides_and_remounts`)

- A remount fails closed while a lane IDE runs. After the stop, a run that is not finished refuses
  `daemon_remount_ide_running`.
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs (`a_remount_refuses_while_a_lane_ide_still_runs`)

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

#### The lane-daemon verb

The run slot of a worker holds the `lane-daemon` verb of the installed guest agent, for every lane.

- The run slot holds the verb. It gets the state directory, the port, the launch digest, the runtime descriptor, the
  runfiles root, the IDE root and the home. The home is in the state directory.
  [@test] ../crates/avl-vm/src/daemon/start/tests.rs (`the_daemon_runner_starts_the_lane_daemon_verb_of_the_installed_agent`)

- The controller has no `--runner` option any more. A command line that still names it is a usage refusal.
  [@test] ../crates/avl-vm/src/cli/tests.rs (`a_command_line_with_the_retired_runner_option_is_refused`)

- The verb speaks protocol 6. The controller refuses a daemon that speaks another protocol, and it names both versions.
  [@test] ../crates/avl-wire/src/daemon/tests.rs (`the_lane_daemon_speaks_protocol_6`)
  [@test] ../crates/avl-vm/src/daemon/http/tests.rs (`daemon_status_answers_only_the_daemon_this_controller_launched`)

- A guest can still hold the JVM daemon of an older controller, which speaks protocol 5. The controller retires it as a
  daemon of another launch, and a run starts the lane daemon in its place. The status of that daemon still decodes,
  and the slot probe never reads it as parked.
  [@test] ../crates/avl-vm/src/daemon/http/tests.rs (`a_leftover_jvm_daemon_is_another_daemon`)
  [@test] ../crates/avl-vm/src/daemon/iterate/tests.rs (`a_leftover_jvm_daemon_is_retired_and_the_lane_daemon_started`)
  [@test] ../crates/avl-wire/src/daemon/tests.rs (`a_status_of_either_runner_decodes`)
  [@test] ../crates/avl-vm/src/daemon/slot/tests.rs (`only_an_answering_idle_daemon_reads_as_parked`)

- A daemon record of an older controller can name the runner of its daemon and a runtime digest. The reader ignores
  both fields, and `daemon status` names no runner.
  [@test] ../crates/avl-vm/src/daemon/state/tests.rs (`a_state_reads_only_with_every_field`)
  [@test] ../crates/avl-vm/src/daemon/command/tests.rs (`command_daemon_status_reports_the_daemons_own_account`)

- Protocol 6 has no route that quiesces the shares, and its status has no quiesce field and no metaspace field.
  [@test] ../crates/avl-wire/src/daemon/tests.rs (`routes_are_matched_the_way_the_daemon_routes_them`)
  [@test] ../crates/avl-wire/src/daemon/tests.rs (`a_status_of_either_runner_decodes`)

- The run request of the verb names the lane and the digests of the three lane inputs. A selection of no lane is
  refused before the request goes out.
  [@test] ../crates/avl-wire/src/daemon/tests.rs (`the_run_request_names_the_lane_inputs_for_the_lane_daemon_only`)
  [@test] ../crates/avl-vm/src/daemon/run/tests.rs (`the_lane_daemon_gets_the_lane_and_the_digests_of_the_lane_inputs`)

- At its boot the verb empties its jar store and removes the state file of the previous daemon. It then publishes a
  state file with the mode 0600, which names the protocol, the port and the token.
  [@test] ../crates/avl-guest/src/lane/channel/tests.rs (`the_state_file_names_the_channel_and_the_status_answers_protocol_6`)

- A request with another token gets 401. The verb stores a pushed file only under the digest of its bytes.
  [@test] ../crates/avl-guest/src/lane/channel/tests.rs (`the_state_file_names_the_channel_and_the_status_answers_protocol_6`)
  [@test] ../crates/avl-guest/src/lane/channel/tests.rs (`the_jar_push_stores_a_file_under_the_digest_of_its_bytes`)

- The verb runs one iteration at a time, and it refuses a run of another launch digest as `stale-daemon`. After
  `/shutdown` it refuses each run as `shutting-down`.
  [@test] ../crates/avl-guest/src/lane/channel/tests.rs (`one_run_at_a_time_and_an_ide_stop_without_an_ide`)
  [@test] ../crates/avl-guest/src/lane/channel/tests.rs (`a_run_streams_its_records_and_a_stale_one_is_refused`)
  [@test] ../crates/avl-guest/src/lane/channel/tests.rs (`a_shutdown_wakes_the_verb_and_refuses_later_runs`)

- `/run` streams one record per chunk, from `runStarted` to `summary`. A run that names no lane is a failed
  iteration.
  [@test] ../crates/avl-guest/src/lane/channel/tests.rs (`a_run_streams_its_records_and_a_stale_one_is_refused`)
  [@test] ../crates/avl-guest/src/lane/services/http/tests.rs (`a_streamed_answer_sends_one_chunk_per_write_and_skips_an_empty_one`)

- An iteration writes the JUnit XML of its run stream to `test.xml` of its iteration directory, and `/results` serves
  that file. The stream keeps its records for the XML, also after the reader of the stream went away.
  [@test] ../crates/avl-guest/src/lane/stream/tests.rs (`the_cases_of_a_broken_stream_still_render_the_junit_xml`)

- `lane-run` runs one iteration of a lane from a runfiles tree, with no control channel. It stores the lane inputs and
  the context plugins of the descriptor in the jar store by digest. It then runs the iteration that a `/run` runs. It
  quits the IDE at the end, and it exits 0 only for a summary with no failure. A stream without a summary is not green.
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`only_a_summary_without_failures_is_green`)

- `lane-run` writes the JUnit XML of the iteration into its run directory. It also writes the XML to the file that
  `JB_XML_OUTPUT_FILE` names, else to the file that `XML_OUTPUT_FILE` names. Bazel and bt count the cases of that file,
  so a write that fails fails the run.
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`the_junit_xml_goes_to_the_run_directory_and_to_the_file_of_the_bazel_test`)

- `lane-run` runs each failed scenario of the lane IDE once more. The reruns start after the last suite of the lane
  IDE and before the suites of a bare IDE. The IDE is recycled once before the first rerun. So each rerun runs on a
  new IDE process on the same context, after the reset of its scenario. The prologue of a suite runs again before the
  reruns of the suite. A note on the run stream names the scenarios, and a note at the end says how many passed. The
  lane daemon of a worker reruns no scenario, so a run of the controller shows each flake.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_failed_scenario_runs_once_more_on_a_recycled_ide_and_its_pass_clears_the_failure`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_scenario_that_fails_twice_stays_failed_after_one_rerun`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_runner_without_a_rerun_reruns_nothing_and_injects_nothing`)

- No scenario reruns when more than 3 scenarios and suite prologues failed, because that is a break and not a flake.
  No scenario reruns after the watchdog ended the run, on an exhausted isolation, or in the live lane. No scenario
  reruns either when the budget left is shorter than what one recycle can take, 13 minutes. A note names the reason. A failed suite prologue, an aborted scenario, a skipped scenario and a
  scenario of a bare IDE get no rerun. A recycle before the reruns that fails ends the reruns, and a prologue that
  fails ends the reruns of its suite. A note names each, and the failures stay.
  [@test] ../crates/avl-guest/src/lane/rerun/tests.rs (`no_rerun_past_the_limit_after_an_expiry_on_an_exhausted_isolation_in_the_live_lane_or_short_of_budget`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`more_failed_scenarios_than_the_limit_or_too_little_budget_rerun_none`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_recycle_that_fails_before_the_rerun_leaves_the_failure`)

- A rerun is a second case of the same id on the run stream. A pass of the rerun takes the failure of the first
  attempt out of the counts of the summary. So a run whose every failure passed on its rerun is green, and `lane-run`
  exits 0. A scenario that fails both attempts counts as one failure. The JUnit XML keeps both attempts in the order
  of the stream, under the same class and name. The result of the run names no test that passed on its rerun. A
  failed case that a later case of the same class and name passed is flaky for bt, and not a failure.
  [@test] ../crates/avl-guest/src/lane/stream/tests.rs (`a_later_pass_of_a_failed_scenario_clears_its_failure_in_the_counts`)
  [@test] ../crates/avl-junit/src/tests.rs (`a_failure_that_a_later_pass_follows_is_no_failed_case_and_the_xml_keeps_both_attempts`)
  [@test] ../../bt/crates/bt-core/src/result/tests.rs (`a_failed_case_that_a_later_case_of_its_name_passed_is_flaky`)

- The rerun spends the budget of the run. The watchdog ends a rerun at the end of the budget as it ends any scenario.
  The run then keeps its `lane-run-budget` build problem, and the hard stop writes the open rerun as unfinished.
  [@test] ../crates/avl-guest/src/lane/watchdog/tests.rs (`the_run_budget_caps_every_deadline_and_ends_the_run_as_run_budget_timeout`)
  [@test] ../crates/avl-guest/src/lane/monitor/tests.rs (`a_cut_off_run_gets_its_open_case_unfinished_and_its_planned_cases_skipped`)

- `AIR_LANE_INJECT_FAILURE` fails named scenarios on purpose, so a run proves the rerun with a real lane IDE. Its
  value is a list of `first:<scenario>` and `every:<scenario>`, separated by `;`. The scenario is a name or a key.
  `first` fails the first attempt, and `every` fails each attempt. The injected failure has the type
  `lane_injected_failure`, and it comes after the body of the scenario and before its reset after. A note at the
  start of the run names each injection. Another value is refused with `lane_run_inject_failure`, and the refusal
  names the part.
  [@test] ../crates/avl-guest/src/lane/rerun/tests.rs (`the_injected_failures_come_from_the_variable_and_a_malformed_one_is_refused`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_failed_scenario_runs_once_more_on_a_recycled_ide_and_its_pass_clears_the_failure`)

- `lane-run` takes its run directory from `--runtime-root`, then `$AIR_LANE_RUNTIME_ROOT`, then
  `$TEST_TMPDIR/air-lane`. A Bazel `--test_filter` adds selectors, and `$JB_TEST_JUNIT5_FILTERS` adds JUnit5 filters.
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`the_run_directory_comes_from_the_flag_the_variable_or_the_test_directory`)

- `lane-run` refuses a run that Bazel splits into more than one shard, because a lane IDE takes the fixed bridge port.
  The lane test touches no shard status file, so Bazel refuses such a run too where its check is on.
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`a_run_of_more_than_one_shard_is_refused`)

- `lane-run` takes the `--jvm_flag` arguments of a TeamCity UI job, in the joined and in the two-token form. The runner
  index and count of the job split the selected suites of the lane. In the order of the lane, a runner takes each suite
  whose position is its index modulo the count. So the runners of one job run each selected suite once. `lane-run`
  ignores `java.awt.headless` and the two bucketing properties, with one note each in the run stream. It refuses
  another flag with `lane_run_jvm_flag`, and the refusal names the property and never its value.
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`lane_run_takes_the_jvm_flags_of_the_teamcity_ui_job_in_both_forms`)
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`the_runner_flags_split_the_lane_and_each_ignored_flag_gives_one_note`)
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`an_unknown_jvm_flag_or_a_split_that_is_not_one_is_refused_by_name`)
  [@test] ../crates/avl-guest/src/lane/iteration/tests.rs (`the_runners_of_a_split_run_each_selected_suite_once_and_one_runner_runs_the_whole_lane`)
  [@test] ../crates/avl-guest/src/lane/channel/tests.rs (`the_notes_of_a_lane_run_follow_the_start_of_the_run`)
  [@test] ../crates/avl-guest/tests/cli.rs (`lane_run_plan_only_writes_the_plan_and_launches_no_ide`)

- Before the lane IDE starts, `lane-run` on a Linux host that no controller prepared proves the host. A TeamCity
  agent and a developer host are such hosts. `lane-run` skips the probe under `--plan-only` and on a worker, which
  names the run secrets directory. The probe refuses a host without `DISPLAY`. It runs the checks of `validate-guest`,
  and then the sweep of the shared objects of the distribution. A host without an answering display, a window manager
  or a library of the distribution is refused with the code of that check. The refusal names the host. The probe then
  refuses a host without `git` with `lane_host_git_missing`. A screen smaller than 1920x1080 and a distribution
  without a shared object are notes in the run stream, not refusals.
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`only_a_linux_run_on_a_host_that_no_controller_prepared_probes_the_host`)
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`a_host_without_a_display_is_refused_by_name`)
  [@test] ../crates/avl-guest/src/linux/validate/tests.rs (`a_host_that_has_what_the_lane_ide_needs_passes_the_probe_with_no_note`)
  [@test] ../crates/avl-guest/src/linux/validate/tests.rs (`a_host_without_a_window_manager_or_a_library_is_refused_with_the_code_of_the_guest_check`)
  [@test] ../crates/avl-guest/src/linux/validate/tests.rs (`a_host_without_git_is_refused_by_name`)
  [@test] ../crates/avl-guest/src/linux/validate/tests.rs (`a_smaller_screen_is_a_note_and_not_a_refusal`)
  [@test] ../crates/avl-guest/src/linux/validate/tests.rs (`a_distribution_without_a_shared_object_sweeps_nothing_and_the_probe_notes_it`)

- On a TeamCity build on Linux that lets the recorder read the screen, `lane-run` starts an X display of its own before
  the probe. The display of the build is the desktop of the agent, and the recorder would show it around the IDE frame.
  Xvfb picks a free display at 1920x1080x24. `lane-run` sets `DISPLAY` to that display, so the probe, the lane IDE and
  the recorder use it. A note in the run stream names the display. Only a host without Xvfb keeps the display of the
  build. A warning in the run stream and a TeamCity message of the status `WARNING` then name Xvfb and IJAI-4779. An
  Xvfb that names no display is refused with `lane_run_display`. `lane-run` stops Xvfb at its end. On Linux Xvfb also
  ends with the process.
  [@test] ../crates/avl-guest/src/lane/own_display/tests.rs (`the_run_owns_its_display_only_with_xvfb`)
  [@test] ../crates/avl-guest/src/lane/own_display/tests.rs (`the_displayfd_line_names_the_display`)
  [@test] ../crates/avl-guest/src/lane/own_display/tests.rs (`the_display_starts_xvfb_on_a_free_display_and_its_drop_stops_it`)
  [@test] ../crates/avl-guest/src/lane/own_display/tests.rs (`an_xvfb_that_names_no_display_is_refused`)

- The display of `lane-run` has a window manager of `lane-run`, `air-lane-wm`, so the host needs no window manager
  program. It does what the probe and the driver need. It selects the substructure redirect of the root and paints the
  root `#2b2d30`. It maps each window that asks and applies each configure request, so `toFront()` raises a window. It
  gives the input focus to a window that it maps or that `_NET_ACTIVE_WINDOW` names, by `XSetInputFocus` and by
  `WM_TAKE_FOCUS`. A window whose hints refuse the input does not get it. A closed window gives the focus to the
  topmost window that takes it. The window manager sets `_NET_SUPPORTING_WM_CHECK` with its name, `_NET_SUPPORTED`,
  `_NET_ACTIVE_WINDOW`, `_NET_CLIENT_LIST`, and a zero `_NET_FRAME_EXTENTS`. It does not reparent and draws no
  decoration, so the lane IDE gets `_JAVA_AWT_WM_NONREPARENTING=1`.
  [@test] ../crates/avl-guest/src/lane/window_manager/tests.rs (`a_map_request_maps_raises_and_focuses_a_window_that_takes_the_focus`)
  [@test] ../crates/avl-guest/src/lane/window_manager/tests.rs (`an_active_window_message_raises_and_focuses_a_managed_window`)
  [@test] ../crates/avl-guest/src/lane/window_manager/tests.rs (`a_gone_window_with_the_focus_gives_it_to_the_topmost_window_that_takes_it`)
  [@test] ../crates/avl-guest/src/lane/window_manager/tests.rs (`a_raise_moves_the_window_to_the_top_of_the_stack`)
  [@test] ../crates/avl-guest/src/supervisor/tests.rs (`the_context_environment_is_the_layout_and_the_allowlist`)

- The budget of a `lane-run` is the smaller of `--run-budget-sec` and `$TEST_TIMEOUT` of a Bazel test, less a
  teardown reserve of 180 s. A run that names neither has no budget. A `$TEST_TIMEOUT` that is no whole number is
  refused with `lane_run_budget`, and so is a budget that the reserve leaves nothing of. The rule `air_lane_test` passes
  a budget of 2700 s, because a lane job of TeamCity ends at 60 minutes. The lanes ui-perf, ui-live and ui-real pass
  none, because their parts run longer. Their budget is the timeout of the Bazel test.
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`the_run_budget_is_the_smaller_of_the_flag_and_test_timeout_less_the_reserve`)

- The budget of the run caps every deadline of the watchdog. At its end the watchdog ends the run as
  `run_budget_timeout`. No instruction waits past it: the deadline of a part is cut to the budget left, and the IDE
  gets the cut deadline as `timeoutMs`. A reset that fails gets no recycle that cannot end inside the budget left, and
  each scenario left is skipped with the reason.
  [@test] ../crates/avl-guest/src/lane/watchdog/tests.rs (`the_run_budget_caps_every_deadline_and_ends_the_run_as_run_budget_timeout`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_part_deadline_is_cut_to_the_budget_left`)
  [@test] ../crates/avl-guest/src/lane/recycle/tests.rs (`a_recycle_that_cannot_fit_the_budget_skips_the_scenarios_left`)

- A monitor thread of `lane-run` writes a hang report on the standard error every 3 minutes. The report names the
  scenario that runs, the open steps of the run thread and the budget left. It also names each thread of the process
  with its state, its wait channel and its system call. A call on a file descriptor names the target of the
  descriptor. The thread that writes the report is named as the monitor, because its own system call is the read of
  its own `syscall` file. The monitor thread and the watchdog thread have names of their own.
  [@test] ../crates/avl-guest/src/lane/monitor/tests.rs (`each_thread_is_named_with_its_state_its_wait_and_the_target_of_its_descriptor`)
  [@test] ../crates/avl-guest/src/lane/watchdog/tests.rs (`a_snapshot_names_the_scenario_the_open_steps_and_the_budget_left`)

- One minute after the end of the budget, a run thread that did not return gets the hard stop of the monitor. The
  monitor writes the JUnit XML of the records so far. The open scenario is unfinished there, and each planned scenario
  that never ran is skipped with the reason. The monitor then cancels each IDE run that the stream launched and did
  not end, and it kills each recorder. It writes the hang report and one line, and the process exits 1. `planStarted`
  names each planned scenario for it.
  [@test] ../crates/avl-guest/src/lane/monitor/tests.rs (`the_hard_stop_writes_the_partial_xml_cancels_the_live_ide_run_and_kills_the_recorder`)
  [@test] ../crates/avl-guest/src/lane/monitor/tests.rs (`a_cut_off_run_gets_its_open_case_unfinished_and_its_planned_cases_skipped`)

- Under a Bazel test, `lane-run` keeps the evidence of a run that is not green in `$TEST_UNDECLARED_OUTPUTS_DIR/lane`,
  because Bazel deletes `$TEST_TMPDIR`. The run end does it, and the hard stop does it after its cancels. A green run
  that failed an attempt keeps its evidence too, because the rerun passed the scenario. Another green run keeps
  nothing, and the lane daemon of a worker keeps nothing, because the controller pulls its iterations. The copy
  holds `ide/<launchKey>/<launchName>/`, the log directory of each affected IDE launch. That directory holds each
  `idea.log`, the thread dump of a kill, the other IDE logs and the heartbeat screenshots. Its `run/` holds `run.log`
  and `supervisor.log` of the IDE process, also when the log directory is gone. The copy also holds
  `iterations/<iterationId>/slow-steps/` and `iterations/<iterationId>/authored-artifacts/`.
  [@test] ../crates/avl-guest/src/lane/evidence/tests.rs (`the_copy_keeps_the_logs_of_the_affected_launches_and_the_files_of_the_iterations`)
  [@test] ../crates/avl-guest/src/lane/evidence/tests.rs (`the_process_output_is_kept_when_the_log_directory_is_gone`)
  [@test] ../crates/avl-guest/src/lane/monitor/tests.rs (`the_hard_stop_keeps_the_evidence_of_the_ide_run_that_it_canceled`)

- After each run, green or not, `lane-run` writes the IJ Perf file of each case that reported a metric and has a
  series. The file goes to `$TEST_UNDECLARED_OUTPUTS_DIR/performance/<artifactPath>`, or to
  `<runDirectory>/performance/<artifactPath>` outside a Bazel test. A failed case keeps its metrics in the file.
  [@test] ../crates/avl-guest/src/lane/performance/tests.rs (`off_teamcity_the_files_are_written_and_not_published`)
  [@test] ../crates/avl-guest/src/lane/performance/tests.rs (`without_bazel_outputs_the_files_go_to_the_run_directory`)
  [@test] ../crates/avl-guest/src/lane/performance/tests.rs (`a_failed_part_keeps_its_metrics_in_the_file`)

- Beside the IJ Perf file, `lane-run` copies the result files of the authored parts of the case. These are
  `summary.txt`, `air-performance.json`, `watchdog.ndjson`, `trace.ndjson` and `report.json`, each when a part wrote
  it. The parts are copied in the order of the program. When two parts wrote the same file, the later part wins, and
  a log line names the file.
  [@test] ../crates/avl-guest/src/lane/performance/tests.rs (`the_part_files_are_copied_beside_the_file_and_the_later_part_wins`)

- On a TeamCity agent, `lane-run` writes one `publishArtifacts` line on standard output for each file that it wrote.
  The line publishes the file into the artifact path of its series. Off TeamCity, it writes one log line with the
  directory. A file that cannot be written is a log line, and it never changes the verdict.
  [@test] ../crates/avl-guest/src/lane/performance/tests.rs (`the_files_are_written_and_published_on_teamcity`)
  [@test] ../crates/avl-guest/src/lane/performance/tests.rs (`off_teamcity_the_files_are_written_and_not_published`)

- After each run that is not `--plan-only`, green or not, `lane-run` writes the result of the run. The file is
  `$TEST_UNDECLARED_OUTPUTS_DIR/lane/run-result.json`, or `<runDirectory>/run-result.json` outside a Bazel test. A
  TeamCity step reads it and posts it as the failure event of the build, so its field names are a contract. The
  result holds `lane`, `green`, `failedTests`, `summaries` and `performance`. Each failed test is `test`, the class
  and the case name, and `failure`, the text of the failure in the JUnit XML. A summary is the `summary.txt` of a part,
  by `<scenario>/<part>`. A failure and a summary keep at most 4000 bytes, cut at a character boundary. `performance`
  names each file of the IJ Perf bullets above with its artifact path.
  [@test] ../crates/avl-guest/src/lane/run_result/tests.rs (`the_run_result_names_the_failed_tests_and_the_summaries`)
  [@test] ../crates/avl-guest/src/lane/run_result/tests.rs (`a_green_run_has_no_failed_test`)
  [@test] ../crates/avl-guest/src/lane/run_result/tests.rs (`the_run_result_goes_to_the_lane_outputs_or_to_the_run_directory`)
  [@test] ../crates/avl-junit/src/tests.rs (`the_failed_cases_are_those_that_fail_the_xml_with_its_failure`)

- Under a Bazel test, a run that `lane-run` refuses before its end writes a result also. A host probe that finds no
  window manager is such a refusal. Then `green` is false, no test fails, and `refusal` holds the `code` and the
  `message` of the refusal, capped as a failure is. The result that the end of a run writes has no `refusal`, and it stays
  when a later step refuses. A `--plan-only` run writes no result.
  [@test] ../crates/avl-guest/src/lane/run_result/tests.rs (`a_refused_run_names_its_refusal`)
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`a_refused_run_under_a_bazel_test_writes_its_refusal_as_the_run_result`)

- The hard stop of the monitor writes the IJ Perf files and the result of the run too, from the records so far. Then
  `green` is false, and the open scenario is a failed test. A metric of a scenario that ended before the hard stop
  keeps its IJ Perf file.
  [@test] ../crates/avl-guest/src/lane/monitor/tests.rs (`the_hard_stop_writes_the_ij_perf_files_and_the_result_of_the_run`)

- A launch is affected when it ran during a scenario that did not pass. A launch that runs at a watchdog expiry, a
  failed class, a failed run or the hard stop is affected too. A failure when no launch runs affects the newest
  launch. A run that is not green and has no affected launch keeps every launch.
  [@test] ../crates/avl-guest/src/lane/evidence/tests.rs (`a_failed_scenario_affects_each_launch_that_ran_during_it`)
  [@test] ../crates/avl-guest/src/lane/evidence/tests.rs (`an_expiry_a_failed_class_and_the_hard_stop_affect_the_running_launch_or_else_the_newest`)

- The copy takes at most 32 MiB of a file and 256 MiB in all. A log or a text above the cap per file keeps its tail,
  and any other file above it is left out. The kill dumps go first, then each `idea.log` and the output of each IDE
  process. The rotated `idea.<n>.log` files follow, the newest first. The slow steps come next, then the other IDE
  logs, the screenshots newest first, and the authored artifacts last. In each kind, the newest launch comes first.
  A file that does not fit in what is left of the total is left out. `evidence.txt` names each file that was cut or
  left out.
  [@test] ../crates/avl-guest/src/lane/evidence/tests.rs (`the_caps_keep_the_tail_of_a_large_log_and_leave_out_the_files_of_least_worth`)

- When `evidence.txt` cannot be written, the line of the copy names the reason instead of the file.
  [@test] ../crates/avl-guest/src/lane/evidence/tests.rs (`a_manifest_that_cannot_be_written_is_named_in_the_line`)

- On a TeamCity agent, `lane-run` also writes a `buildProblem` with the identity `lane-run-budget` on standard output.
  It does so when the budget ends the run, naming the case and the budget, and at the hard stop, naming its line.
  TeamCity mutes a failed case but not a build problem, so a cut-off run cannot leave the build green.
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`a_run_that_its_budget_ended_is_a_teamcity_build_problem_with_the_case_and_the_budget`)
  [@test] ../crates/avl-guest/src/lane/monitor/tests.rs (`the_hard_stop_writes_the_partial_xml_cancels_the_live_ide_run_and_kills_the_recorder`)
  [@test] ../crates/avl-junit/src/teamcity/tests.rs (`a_build_problem_names_its_escaped_description_and_its_identity`)

- `lane-run` writes the run stream on standard output from the run thread and from the watchdog thread. The agent
  locks standard output for each write, not for the whole run. So no thread of the run waits for a lock of the main
  thread.
  [@test] ../crates/avl-guest/tests/cli.rs (`lane_run_writes_the_record_of_its_watchdog_thread_on_standard_output`)

- On a TeamCity build, `lane-run` appends service messages to the file that `AIR_LANE_LIVE_LOG` names. The build
  streams that file into its build log, so the messages show while the test runs. At the start of each scenario
  `lane-run` writes `progressMessage '<lane> <n>/<total>: <scenario>'`. At its end the message also names the
  status and the duration. A rerun is `<lane> rerun: <scenario>`. Each message is one line in one write, and its
  values are escaped as TeamCity reads them. The file carries no test event, because the results come from the JUnit
  XML.
  [@test] ../crates/avl-guest/src/lane/build_log/tests.rs (`a_run_with_a_passed_and_a_failed_scenario_writes_the_golden_lines`)
  [@test] ../crates/avl-guest/src/lane/build_log/tests.rs (`a_rerun_names_itself_in_the_progress_messages`)

- For a failed or aborted scenario, `lane-run` publishes the video and the last picture of the bundle with
  `publishArtifacts`. A scan of the trace root after each scenario and at the summary of the run finds the bundle.
  The paths are the ones of `air-trace teamcity-publish`: `video.mp4` and `failure.<ext>` in
  `air-traces-failed/<lane>/<runId>/<TestClass>/<stem>/`. The lane is the directory of the `test.xml`. A later bundle
  of the same path takes the stem `<scenario>.<n>`. So the publish after the test replaces the same files. Both files
  are staged under their artifact names beside the file of the variable, because Bazel can remove `test.outputs`
  after the test.
  [@test] ../crates/avl-guest/src/lane/build_log/tests.rs (`a_run_with_a_passed_and_a_failed_scenario_writes_the_golden_lines`)
  [@test] ../crates/avl-guest/src/lane/build_log/tests.rs (`a_second_failed_attempt_publishes_under_the_next_stem`)
  [@test] ../crates/avl-guest/src/lane/build_log/tests.rs (`the_summary_publishes_a_bundle_that_the_recorder_finished_late`)
  [@test] ../crates/avl-trace-tools/src/artifacts/tests.rs

- The channel is off without `TEAMCITY_VERSION`, without the variable, and when the file does not open. Inside a
  Bazel test the channel is on, although Bazel sets `XML_OUTPUT_FILE` there. A failed write is one line of the log,
  and the channel stays off for the rest of the run. A write that takes a part of a line ends that line first. The
  run keeps its verdict and its run file. The lane daemon of a worker opens no channel.
  [@test] ../crates/avl-guest/src/lane/build_log/tests.rs (`the_channel_is_on_only_on_teamcity_with_the_variable`)
  [@test] ../crates/avl-guest/src/lane/build_log/tests.rs (`the_channel_is_on_in_a_bazel_test_of_a_teamcity_build`)
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`a_failed_write_to_the_build_log_keeps_the_run_file_whole_and_green`)
  [@test] ../crates/avl-guest/src/lane/build_log/tests.rs (`a_short_write_ends_its_line_and_turns_the_channel_off`)

- `lane-run` takes which scenarios keep their video from `AIR_LANE_VIDEO`. The value `failed` keeps the video of a
  scenario that did not pass only, and the hello says `keepVideo` `failed`. Unset, empty or `always`, every scenario
  keeps its video. Another value is refused with `lane_run_video`, and the refusal names the value. A TeamCity build
  passes the variable with `--test_env`. It sets `failed` on a branch other than `master` and in a personal build. So a
  passed scenario keeps its video only in a build of `master`. The lane daemon of a worker keeps every video.
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`air_lane_video_keeps_every_video_unless_it_says_failed`)
  [@test] ../crates/avl-guest/src/lane/trace/tests.rs (`the_hello_keeps_the_videos_that_the_inputs_keep`)

- `lane-run --plan-only` stages the inputs and selects the documents as a run does. It then writes the plan on
  standard output: the key of the lane IDE, the digests of the inputs, and the suites in run order. It launches no IDE,
  and a plan of suites of a bare IDE alone names no lane key.
  [@test] ../crates/avl-guest/tests/cli.rs (`lane_run_plan_only_writes_the_plan_and_launches_no_ide`)
  [@test] ../crates/avl-guest/src/lane/run/tests.rs (`the_selectors_and_the_filters_come_from_the_flags_and_the_environment`)

- An iteration runs the suite documents of its lane. A selector narrows them by the test class, with or without a
  package. A `#<scenario>` part keeps that scenario only.
  [@test] ../crates/avl-guest/src/lane/iteration/tests.rs (`a_selection_is_the_documents_of_the_lane_narrowed_by_the_selectors`)

- The JUnit5 filters of the run request narrow the documents by the `testClass` label of each one. A class pattern
  must match the whole class name. A filter that keeps no document is a refusal.
  [@test] ../crates/avl-guest/src/lane/iteration/tests.rs (`the_junit5_filters_narrow_the_documents_by_their_class`)

- The `lane` field selects the documents of the lane, and the documents carry no JUnit tag. So `--filter` of the
  controller takes no tag kind, and the verb refuses a tag filter.
  [@test] ../crates/avl-vm/src/daemon/run/tests.rs (`the_lane_daemon_gets_the_class_filters_of_a_suite`)
  [@test] ../crates/avl-vm/src/daemon/run/tests.rs (`a_filter_is_typed_and_spelled_back_as_written`)
  [@test] ../crates/avl-guest/src/lane/iteration/tests.rs (`the_junit5_filters_narrow_the_documents_by_their_class`)

- The controller sends the lane daemon every suite of the lane that the catalog names: each suite document, by test
  class.
  [@test] ../crates/avl-vm/src/lane/affected/tests.rs (`the_lane_classes_are_the_suite_documents_of_the_lane`)

- The plan names each of these suites, also a suite with no suite document. The selectors and the class filters
  narrow such a suite by its test class, and a package filter drops it. `planStarted` names every planned class, and
  each suite with no document ends as a failed container `lane_unsupported` that names the reason. A class that no
  data file lists is outside the plan.
  [@test] ../crates/avl-guest/src/lane/iteration/tests.rs (`a_lane_class_without_a_suite_document_is_planned_and_narrowed_like_a_document`)
  [@test] ../crates/avl-guest/src/lane/iteration/tests.rs (`an_undocumented_suite_fails_as_unsupported_with_its_reason`)

- The prologue of a suite attaches the Agent Spawner to the Cloud agent of its fixtures. A suite without one detaches it.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_suite_with_a_cloud_agent_attaches_the_spawner_to_its_key_and_runs`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_suite_with_a_local_agent_detaches_the_spawner_and_runs`)

- A suite with two Cloud agents, or with one that is not controlled, fails as one container `lane_document`.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_suite_with_two_cloud_agents_or_an_uncontrolled_one_is_refused_before_any_request`)

- The host ids `refresh-live-wiring` and `adopt-leftover-junie-sessions` run in a live lane only. A suite of another
  lane that runs one in its program or its prologue fails as one container `lane_document`. It fails before its first
  request, and the refusal names the word.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_suite_that_runs_a_live_host_id_outside_the_live_lane_is_refused_before_any_request`)

- The program runner runs each scenario of each selected document. A reset runs before the scenario and after it.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_green_scenario_runs_between_two_resets_and_writes_the_golden_transcript`)

- A suite whose document does not fit its vocabulary fails its prologue as one container failure. A document without
  its reset, or without the bridge requests of a story fixture, fails it too.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_suite_without_its_bridge_requests_fails_its_prologue`)

- The prologue of a suite clears the registry fault and resets the managed agents of the lane, in sorted order.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_green_scenario_runs_between_two_resets_and_writes_the_golden_transcript`)

- A suite whose fixtures start with installed managed agents states `managedAgentInstall`, one bridge request. The
  prologue sends it once, after the managed-agent baseline. An answer other than null fails the suite as `lane_reset`.
  A suite with such a fixture and no request fails as `lane_document`, and a bare suite states none.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_suite_installs_its_managed_agents_once_after_the_baseline`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_failed_install_and_a_missing_install_request_fail_the_suite`)
  [@test] ../crates/avl-wire/src/program/tests.rs (`the_managed_agent_install_is_read_verbatim_and_a_bare_suite_states_none`)

- The reset after a failed scenario of a suite with `managedAgentInstall` sends the managed-agent baseline again. It
  sends `ResetManagedAgents`, then the install, after the reset in the IDE. A green scenario sends neither. A baseline
  that does not hold is a reset that does not hold. So the IDE is recycled, and the reset and the baseline run again.
  The runner does not verify the installed state, because no lifecycle request asks the IDE for it.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_suite_installs_its_managed_agents_once_after_the_baseline`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_failed_scenario_restores_the_managed_agent_baseline_in_its_reset_after`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_failed_baseline_after_a_failed_scenario_recycles_the_ide_through_the_isolation`)

- A story scenario prepares the IDE in one chain of the bridge requests of its document. The terminal tabs after the
  chain are the value `terminalBaseline`.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_green_scenario_runs_between_two_resets_and_writes_the_golden_transcript`)

- An IDE id is one bridge request `<kind>/<id>`. It holds the arguments in camel case, the values it takes, and
  `timeoutMs`.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_green_scenario_runs_between_two_resets_and_writes_the_golden_transcript`)

- The transport of an IDE id waits its deadline, 10 s and the grace of the bridge. A request without an answer
  then fails its scenario, and the failure names the instruction.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`an_instruction_past_its_deadline_fails_and_is_named`)

- An id that takes a value that no earlier instruction answered fails its scenario. The failure names the value and
  the instruction.
  [@test] ../crates/avl-guest/src/lane/values/tests.rs (`a_request_gets_the_values_it_takes_and_a_missing_one_is_named`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_missing_take_fails_the_scenario_with_the_sentence_of_the_host`)

- An authored part gets the values it takes in one `values` object, and it answers exactly the values it declares.
  Its notes and its metrics go into the `report` of the `testFinished` of its scenario.
  [@test] ../crates/avl-guest/src/lane/values/tests.rs (`a_part_takes_and_answers_exactly_its_declared_values`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`an_authored_part_passes_its_values_and_reports_its_notes`)

- A failed part answers no report. A performance body rewrites `air-performance.json` in the artifacts of its part
  after each metric. So the verb reads that file after a failed part, and its numbers go into the same `report`. The
  IJ Perf file of the series then holds them too.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_failed_part_reports_the_metrics_of_its_performance_file`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_part_that_answered_reports_no_metric_of_its_performance_file`)

- A body aborts its scenario when the lane cannot give a precondition. The bridge marks that failure `aborted`. The
  scenario then ends as `ABORTED`, and the run counts it as skipped. The after reset still runs. A failure without
  the mark ends as `FAILED`.
  [@test] ../crates/avl-bridge/src/failure/tests.rs (`a_failure_marked_aborted_decodes_as_an_abort_and_an_unmarked_one_does_not`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`an_authored_part_that_aborts_its_scenario_finishes_as_aborted_and_counts_as_skipped`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_failed_part_reports_the_metrics_of_its_performance_file`)

- A run of the lane daemon takes the recorded sessions that `AIR_UI_REAL_SESSION_LOGS` names on the host. Each entry is
  an `events.jsonl` or a thread directory that holds one, and the path separator divides the entries. The label of a
  log is the name of its thread directory, or its file name without the extension. A label keeps the ASCII letters,
  the digits, `.`, `_` and `-` of the URL-decoded name, and every other character becomes `_`. A variable that names
  no events file, or two logs of one label, is refused.
  [@test] ../crates/avl-wire/src/session_log/tests.rs (`the_variable_names_files_and_thread_directories`)
  [@test] ../crates/avl-wire/src/session_log/tests.rs (`a_label_is_the_thread_directory_or_the_file_name_url_decoded_and_safe`)
  [@test] ../crates/avl-vm/src/daemon/session_logs/tests.rs (`no_variable_packs_nothing_and_a_wrong_entry_is_refused`)

- The controller packs each log as one zip of stored entries, with its `attachments` and `outputs` directories. It
  pushes the zips by digest, and the run request names them in `sessionLogs`. The zips then leave the host.
  [@test] ../crates/avl-wire/src/session_log/tests.rs (`a_log_packs_its_events_and_siblings_and_unpacks_to_the_same_tree`)
  [@test] ../crates/avl-vm/src/daemon/session_logs/tests.rs (`each_named_log_is_one_zip_of_the_push_and_leaves_the_host_after_it`)
  [@test] ../crates/avl-vm/src/daemon/run/tests.rs (`the_lane_daemon_gets_the_recorded_sessions`)

- The lane daemon unpacks each log into `<context>/air-real-session-logs/<label>/` before the first suite. The zip
  then leaves the jar store, and the copies leave the context at the end of the run. `lane-run` reads the variable on
  its own host and stages the logs the same way.
  [@test] ../crates/avl-guest/src/lane/session_logs/tests.rs (`the_logs_go_into_the_context_and_leave_it_when_the_run_ends`)
  [@test] ../crates/avl-guest/src/lane/session_logs/tests.rs (`no_log_no_context_or_a_bad_label_stages_nothing`)
  [@test] ../crates/avl-wire/src/session_log/tests.rs (`an_unpack_refuses_an_entry_outside_and_a_zip_without_events`)

- The runner answers `realSessionLogs` as a list of `{label, eventsPath}` when it has a log, and no value when it has
  none. A scenario names the runner values that it needs in `requiresRunnerValues`. When the runner does not have one
  of them, the scenario is `testSkipped` before its reset. The reason is
  `scenario '<name>' never ran: the runner has no value '<value>'`.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`the_recorded_sessions_are_the_runner_value_of_a_part_that_takes_them`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_scenario_that_requires_an_absent_runner_value_is_skipped_before_its_reset`)
  [@test] ../crates/avl-wire/src/program/tests.rs (`a_required_runner_value_is_one_of_the_runner_answers`)

- After each part of an authored program, and before its `restart-ide` or `reload-air`, the verb lists the trace
  attachments that the IDE queued. It fetches each listed id at once. Each still, capture or file goes to the span of the instruction as a
  named attachment. The run log notes a count of dropped stills and each fetch that fails, and neither fails the
  part.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`an_authored_part_files_the_stills_and_the_captures_that_the_ide_queued`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`the_recycle_proof_recycles_once_and_resets_the_replacement_on_a_new_pid`)

- The verb reads the list in the shape of the IDE side. It refuses an unknown field, and an id that is no path
  segment.
  [@test] ../crates/avl-trace/src/bridge/tests.rs (`the_attachment_list_reads_and_writes_the_document_of_the_kotlin_side`)
  [@test] ../crates/avl-trace/src/bridge/tests.rs (`an_attachment_id_that_is_no_path_segment_and_an_unknown_field_are_refused`)

- A failed scenario has a typed failure. The type is the class of the failure in the IDE, and the detail is its
  stack and its causes.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_failed_slow_instruction_is_typed_photographed_and_dumped`)

- A reset that does not hold recycles the IDE on its context, and the reset runs again. The scenario then runs on
  the new IDE.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_dirty_reset_recycles_once_and_the_scenario_passes_on_a_new_pid`)
  [@test] ../crates/avl-guest/src/lane/recycle/tests.rs (`a_dirty_reset_recycles_once_and_the_next_reset_holds`)

- A run has a budget of three recycles. Each scenario after the budget is `testSkipped` with the reason
  `scenario '<name>' never ran: <reason>`.
  [@test] ../crates/avl-guest/src/lane/recycle/tests.rs (`the_fourth_dirty_reset_exhausts_the_budget_of_three_recycles`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`resets_that_never_hold_spend_the_budget_and_skip_the_rest`)

- The host action `recycle-through-failed-reset` proves the recycle. It fails the next reset on purpose, so the IDE
  recycles and the reset runs again on the replacement. The action fails unless that reset passed on a new IDE
  process that answers the bridge. The proof spends no recycle of the run. An authored program may run this action,
  `restart-ide` and `reload-air`, and no other host id.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`the_recycle_proof_recycles_once_and_resets_the_replacement_on_a_new_pid`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_recycle_that_keeps_the_ide_process_fails_the_proof`)

- The host action `reload-air` reloads Air in place, and the IDE keeps its process. The verb reads the bridge digest
  from the lifecycle first. It posts the reload of `com.intellij.air` to the resident route, with no staged directory,
  within the deadline of the action. It then waits until the lifecycle names that digest again, and it pings the new
  copy. A refusal fails the action, and so does an unload or a load that did not happen. Old class loaders fail
  nothing, because the next part counts them.
  [@test] ../crates/avl-guest/src/lane/reload/tests.rs (`a_reload_of_air_waits_for_the_same_bridge_plugin_and_counts_no_loader`)
  [@test] ../crates/avl-guest/src/lane/reload/tests.rs (`a_reload_of_air_that_did_not_complete_fails_with_its_reason`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_reload_of_air_runs_between_two_parts_on_the_same_ide`)

- A suite document may state `launch: {profile: "bare", key: <launch key>}`. Such a suite runs after the suites of
  the shared IDE, on an IDE of its own. The verb quits the IDE of the lane and launches a bare IDE on a fresh context of
  the launch key. The next run launches the IDE of the lane again on its context.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_bare_suite_runs_last_on_its_own_ide_and_its_one_request_is_its_instruction`)

- The profile is a word of the verb, and `launch` has no third field, so a document of another profile does not parse.
  The launch key is a name of lowercase letters, digits and dashes. A bare suite runs an authored program and no host
  id.
  [@test] ../crates/avl-wire/src/program/tests.rs (`a_bare_launch_is_a_profile_word_and_a_section_with_no_other_field`)
  [@test] ../crates/avl-wire/src/program/tests.rs (`a_launch_key_that_is_no_plain_name_is_refused`)
  [@test] ../crates/avl-wire/src/program/tests.rs (`a_bare_launch_of_a_story_suite_or_of_a_host_id_is_refused`)

- A suite document states its schema version, and the verb reads version 2 only. A document of another version fails to
  parse, so the verb never reads a field that it does not know as an absent field.
  [@test] ../crates/avl-wire/src/program/tests.rs (`a_document_of_another_schema_version_is_refused`)

- A bare IDE gets the option files and the token of the bridge, and no other context file. It gets the stated launch
  properties of the lane document without the internal mode, and no property of a requirement or a lane service.
  [@test] ../crates/avl-guest/src/lane/context/tests.rs (`a_bare_context_holds_the_option_files_and_the_token_alone`)
  [@test] ../crates/avl-guest/src/lane/ide/tests.rs (`a_bare_ide_gets_the_stated_defaults_without_the_internal_mode_or_the_requirements`)

- Every IDE that the daemon launches opens no README at the start, so the editor area starts empty and the empty-state
  composer shows. No lane tests the opening of a README. The option files of every context hold
  `config/options/advancedSettings.xml` with `ide.open.readme.md.on.startup` set to false, the bytes of the start-up
  bench.
  [@test] ../crates/avl-guest/src/lane/context/tests.rs (`a_bare_context_holds_the_option_files_and_the_token_alone`)
  [@test] ../crates/avl-guest/src/lane/context/tests.rs (`a_lane_context_opens_no_readme`)
  [@test] ../crates/avl-guest/src/lane/context/tests.rs (`the_option_templates_have_the_pinned_digest`)

- A recycle of a lane IDE starts on its context with no editor tab of the run before it. A reset that did not hold
  recycles the IDE. The decision of a run recycles it too. It does so for an IDE that exited or does not answer, and
  for a change of the context plugins. It also does so for a failed reload and after a bare suite. While no IDE runs,
  the daemon removes the components `FileEditorManager` and `DockManager` from each product workspace file of the
  context, `config/workspace/*.xml`. The platform reopens the tabs of a project from these two components. Every other
  component stays, and a file that does not parse goes as a whole. A `restart-ide` keeps the tabs, because its
  scenario tests what a restart restores. A fresh context and a bare context hold no such file.
  [@test] ../crates/avl-guest/src/lane/context/editors/tests.rs (`the_clear_removes_the_restored_tabs_and_keeps_every_other_component`)
  [@test] ../crates/avl-guest/src/lane/context/editors/tests.rs (`a_file_with_a_component_that_does_not_close_goes_as_a_whole`)
  [@test] ../crates/avl-guest/src/lane/ide/tests.rs (`a_recycle_removes_the_restored_tabs_and_a_restart_keeps_them`)

- A bare IDE keeps the plugin set of the product, as the IDE of a user does. So it disables no plugin of the lane
  document, and AI Assistant stays on.
  [@test] ../crates/avl-guest/src/lane/ide/tests.rs (`a_bare_context_disables_no_plugin_and_a_lane_context_disables_those_of_the_document`)

- The readiness of a bare IDE is the lifecycle and the vocabulary digest only. Its scenarios run no reset, and the
  watchdog sends it no probe and no dump. So the first request of the bridge that a bare IDE gets is the first
  instruction of its suite. The trace of its scenario opens after the program, because the recorder asks the IDE for
  its facts. Its log slice starts at the first byte of the fresh log, so it holds the records of the start-up.
  [@test] ../crates/avl-guest/src/lane/readiness/tests.rs (`the_readiness_of_a_bare_ide_sends_no_request_of_the_bridge`)
  [@test] ../crates/avl-guest/src/lane/watchdog/tests.rs (`a_quiet_hold_sends_the_ide_no_probe_and_no_dump`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_bare_suite_runs_last_on_its_own_ide_and_its_one_request_is_its_instruction`)

- A bare IDE that does not get ready fails its suite as one container failure `lane_launch`.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_bare_ide_that_does_not_get_ready_fails_its_suite_and_the_lane_takes_its_ide_back`)

- An instruction that answers `attachments` gets each file attached to the trace of its scenario. The text of each file
  goes into the run log.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_bare_suite_runs_last_on_its_own_ide_and_its_one_request_is_its_instruction`)

- `restart-ide` performs the file operations of `whileDown` while the IDE is down, and it answers `whileDownReads`.
  It then states the fixture to the new IDE.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_restart_performs_while_down_and_states_the_fixture_again`)
  [@test] ../crates/avl-guest/src/lane/host_ids/tests.rs (`a_restart_performs_while_down_and_answers_the_reads`)

- A relaunch for `restart-ide` launches as `restart-<n>`, and a recycle launches as `recycle-<n>`. Its line in the run
  log names the cause.
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_restart_performs_while_down_and_states_the_fixture_again`)
  [@test] ../crates/avl-guest/src/lane/program/tests.rs (`a_dirty_reset_recycles_once_and_the_scenario_passes_on_a_new_pid`)

- The registry host ids hold, fail and release a package download, and they count the package requests after a mark.
  [@test] ../crates/avl-guest/src/lane/host_ids/tests.rs (`the_registry_ids_arm_hold_release_and_check_the_package_fault`)
  [@test] ../crates/avl-guest/src/lane/host_ids/tests.rs (`the_request_count_reads_the_package_requests_after_the_mark`)

- `account-approve-sign-in` approves the sign-in in the JetBrains Cloud emulator. The URL must start with the base
  URL of the emulator. The emulator must answer a redirect, and the callback of the IDE must answer 200
  to 399. A callback URL with a query and no path keeps its query, and each address of its host is tried in turn.
  [@test] ../crates/avl-guest/src/lane/host_ids/tests.rs (`the_sign_in_approval_follows_the_redirect_to_a_callback_with_a_query_and_no_path`)
  [@test] ../crates/avl-guest/src/lane/host_ids/tests.rs (`a_url_with_a_query_and_no_path_splits_after_its_port`)
  [@test] ../crates/avl-guest/src/lane/host_ids/tests.rs (`a_sign_in_url_of_another_server_is_refused`)

The verb owns the lane services of one config key. They bind once for the key and live until `/shutdown`. A run of
another key stops them and binds the services of the new key. Each iteration then decides about the IDE of the key:

| the IDE of the key | the decision |
| --- | --- |
| none, or the launch record names another product | `fresh`: every IDE run under the IDE root stops, and the context is made again |
| its run is live, its bridge answers, and it holds the wanted context plugins | `reuse` |
| its run ended, or its bridge does not answer | `relaunch` on the same context |
| only the bridge plugin directory of its context plugins changed | `reload` of the bridge plugin in the running IDE |
| another context plugin directory changed | `relaunch` on the same context |

A daemon that boots again keeps the IDE of a key when the launch record names the same product and the run is live.
The proof of the decisions is a lane run on a worker, because each one needs a real IDE process.

- The verb installs the context plugins with a manifest that names each file and its digest. A set without the bridge
  or its resident part is refused, and so is a plugin that the distribution also carries.
  [@test] ../crates/avl-guest/src/lane/ide/tests.rs (`an_install_replaces_the_plugin_directories_and_writes_the_manifest_last`)
  [@test] ../crates/avl-guest/src/lane/ide/tests.rs (`an_install_refuses_a_missing_bridge_and_a_doubled_plugin`)

- The decision compares the digest of each context plugin directory. A change of the bridge directory alone is a
  `reload`. Each other change is a `relaunch`, and its reason names the directories that changed. The controller, the
  verb and the resident plugin compute the digest of a directory by one formula.
  [@test] ../crates/avl-guest/src/lane/reload/tests.rs (`only_a_change_of_the_bridge_directory_alone_is_a_reload`)
  [@test] ../crates/avl-wire/src/daemon/tests.rs (`the_digest_of_a_plugin_directory_is_over_its_own_files_in_destination_order`)

- A reload writes the bridge directory to `<context>/plugin-staging/<the first 12 hex digits of its digest>`, outside
  the plugin directory. It then asks the resident plugin to reload the bridge plugin from there. It waits up to 30 s
  until the lifecycle state names the new digest. Readiness then pings the new copy. Each step writes a timed line
  into the run log.
  [@test] ../crates/avl-guest/src/lane/reload/tests.rs (`the_staged_copy_holds_the_bridge_files_outside_the_plugin_directory`)
  [@test] ../crates/avl-guest/src/lane/reload/tests.rs (`a_clean_reload_waits_for_the_new_digest_in_the_lifecycle`)
  [@test] ../crates/avl-bridge/src/client/tests.rs (`a_documented_post_answers_the_named_statuses_with_their_document`)

- A reload that fails is a `relaunch`, and its reason names the failure. A refused reload fails. So does an unload or
  a load that did not run. An old class loader that stays reachable fails too. A reason in the answer, another
  digest, and no new digest within 30 s also fail. The relaunch starts the new copy. Either the reload put the
  directory in place, or the install of the relaunch writes it.
  [@test] ../crates/avl-guest/src/lane/reload/tests.rs (`each_answer_that_is_not_a_clean_reload_names_its_relaunch`)
  [@test] ../crates/avl-guest/src/lane/reload/tests.rs (`a_refusal_a_leak_and_a_driver_failure_are_each_a_relaunch`)
  [@test] ../crates/avl-guest/src/lane/reload/tests.rs (`a_digest_that_the_lifecycle_never_names_ends_the_wait`)

- Each decision about the IDE is a `decision` record of the run stream, and its line goes into the run log. The
  controller publishes the record as its own `decision` record.
  [@test] ../crates/avl-wire/src/daemon/tests.rs (`a_decision_record_of_the_lane_daemon_decodes_and_keeps_its_bytes`)
  [@test] ../crates/avl-vm/src/daemon/run/tests.rs (`a_decision_of_the_lane_daemon_is_published_as_a_decision`)

- An IDE is ready when the lifecycle says `READY`, the robot is the input-events robot, and the ping echoes its
  nonce.
  [@test] ../crates/avl-guest/src/lane/readiness/tests.rs (`a_ready_ide_passes_the_gate_after_one_chain_of_the_robot_and_the_ping`)
  [@test] ../crates/avl-guest/src/lane/readiness/tests.rs (`another_robot_is_refused`)

- A bridge that serves another flow vocabulary than the documents jar of the run is refused as `lane_vocabulary_skew`,
  which names both digests. A bridge that names no digest passes, and the daemon notes that once.
  [@test] ../crates/avl-guest/src/lane/readiness/tests.rs (`a_vocabulary_of_another_build_is_a_skew_and_a_missing_one_is_noted_once`)

- An IDE whose control server of the controlled agents did not bind never gets ready. Readiness ends at the first state
  that names the failure, and it refuses as `lane_control_bind_failed` with the text of the failure.
  [@test] ../crates/avl-guest/src/lane/readiness/tests.rs (`a_failed_control_bind_ends_the_gate_at_once_with_its_text`)

- An IDE whose run ended never gets ready. Before each poll, the readiness asks the run supervisor whether the run of
  the held IDE ended. An ended run ends the gate at once with `lane_ide_not_ready`. So an IDE that the watchdog killed ends
  the run in seconds, and not after the 600 s of the gate.
  [@test] ../crates/avl-bridge/src/lifecycle/tests.rs (`a_wait_whose_process_ended_ends_at_once_with_the_reason`)
  [@test] ../crates/avl-guest/src/lane/readiness/tests.rs (`a_wait_on_an_ide_whose_run_ended_is_refused_at_once`)
  [@test] ../crates/avl-guest/tests/cli.rs (`lane_run_writes_the_record_of_its_watchdog_thread_on_standard_output`)

- A quit asks the IDE through its bridge and waits one minute for the run to end. Then a kill with a thread dump
  follows. A process that survives both is a refusal, and no launch lands beside it.
  [@test] ../crates/avl-guest/src/lane/readiness/tests.rs (`a_quit_is_one_request`)

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
