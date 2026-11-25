# A flow id and a suite id are test selectors, and run takes its own lease

## Context

The flow generator turns most story flows into e2e suites. Each suite document states its suite id, its lane, its
test class and the flows that its scenarios tell or walk. A reader who knows a flow needs one line to run its suites,
on the host or in a VM, without the class names.

A VM run of those classes needs a lease, a host build, one run per suite and a release. A copied script that stops
part of the way leaves a worker leased. The controller already has every part: the suites that a change reaches, the
lane choice and a self-leased command, `shard`.

## Decisions

1. **A flow id and a suite id are selectors.** `bt` classifies `flow-<words>` as a flow and any other lower-case name
   of two or more hyphenated words as a suite. The hyphen keeps both kinds apart from a class, an FQN, a package, a
   label and a directory. The same selector works in `community/tools/vm.cmd run` and in `community/tools/bt.cmd`.
2. **A flow reaches the suites that a `@flow` tag with its id reaches.** These are the suites whose scenarios tell or
   walk the flow, and the suites whose story flows implement it. `bt` owns the walk rule, and the trace planner asks
   the same rule.
3. **One function makes the selection.** `select_suites` of the lane module turns a set of suites into the lane tag
   and one class filter per suite. `run --changed`, a flow selector and a suite selector all call it. The lane choice
   and its refusals are `choose_lane` of `bt`, so `bt.cmd` gives the same refusals:
   - an unknown id is `usage`, and the refusal names the nearest known ids;
   - a story flow that no suite covers is `no_affected_suite`;
   - no selector runs an empty selection.
4. **The host run is the lane, narrowed.** `bt.cmd flow-<id>` runs the target and the flags of the lane, with one
   shard, and passes the class filters as `JB_TEST_JUNIT5_FILTERS`.
5. **`run` without `--lease-file` takes its own lease.** It parses the selection first, so a refusal costs nothing.
   It then builds on the host with no worker held, and leases one worker under the holder `<user>-run-<uuid>`. It
   releases the lease on every exit. An interrupt keeps the lease, as `shard` does, because the iteration can still be
   in flight.
6. **`run` with `--lease-file` does not change.** A warm inner loop keeps its worker and its receipt.
7. **The planner prints one `vm.cmd run` line per run.** Each line takes its own lease. A GUI-chat line names
   `--backend parallels`, because that lane belongs on the guest that the user chose.

The [suites a change reaches](../../../../spec/lane-controller.spec.md#the-suites-a-change-reaches) and
[the planner](../../../../spec/scenario-trace.spec.md#the-planner) state the contract. A selection that reaches two
lanes runs one iteration per lane, as the decision
[one run takes every lane it reaches](../../../../../../../plugins/air/openspec/changes/archive/2026-09-24-one-run-takes-every-lane-it-reaches/design.md)
records. `shard`, `flake` and `bt.cmd` still refuse it with `affected_lanes_ambiguous`.

## Consequences

- A flow runs from one line, on the host or in a VM. The docs site can print that line for each flow.
- A self-leased run pays the host build and one acquisition for each call. A reader who runs many iterations on one
  worker still takes a receipt.
- A lower-case hyphenated name that is not a suite id is a suite selector with a typo. The refusal names the nearest
  suite ids.
- When `bt` cannot read the story flows, the refusal says "unknown", which is the safe direction.
- One scenario alone is no selector. The scenarios of a suite share one managed-agent chain, so the suite is the
  smallest unit.
