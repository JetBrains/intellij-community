---
topic: testing
---

# 157. A flow id and a suite id are test selectors, and `run` can take its own lease

Date: 2026-09-24

## Status

Accepted.

Superseded in part by [ADR 0159](../../../../../plugins/air/docs/decisions/0159-one-run-takes-every-lane-it-reaches.md): without `--lane`, `vm.cmd run`
runs one iteration per reached lane. The refusal `affected_lanes_ambiguous` stays for `shard`, `flake` and
`bt.cmd`.

## Context

The flow generator turns most story flows into e2e suites. Each suite document under
`tests/integration/flow-profiles/resources/flow-profiles/` states its suite id, its lane, its test class, and
the flows its scenarios tell or walk. A reader who knows a flow still needed the class names of its suites to
run them.

A VM run of those classes needed five shell lines: `daemon warm`, `lease acquire`, a `jq` to read the receipt
path, one `run <Class>` per suite, and `lease release`. The trace planner printed the same script. A reader
without the `vm-ui-tests` skill could not write it, and a copied script that stopped part of the way left a
worker leased.

The controller already had every part of the answer. `bt.AffectedSuites` reads the suite documents for
`run --changed`. `lane.ResolveAffectedSelection` turns a set of suites into one lane and one class filter per
suite, and it refuses an answer of two lanes. `shard` leases its own workers and releases them. Each part had
one caller.

## Decision

1. **A flow id and a suite id are selectors.** `bt` classifies `flow-<words>` as a flow and any other
   lower-case name of two or more hyphenated words as a suite. The hyphen keeps both kinds apart from a class,
   an FQN, a package, a label and a directory. The same selector works in `vm.cmd run` and in `bt.cmd`:

   ```
   ./plugins/air/scripts/vm.cmd run flow-rename-session
   ./plugins/air/scripts/vm.cmd run rename-session
   ./community/tools/bt.cmd flow-rename-session
   ```

2. **A flow reaches the suites a `@flow` tag with its id reaches.** These are the suites whose scenarios tell
   or walk the flow, and the suites whose story flows implement it. `bt` owns the rule that a scenario walks
   a flow (`ScenarioFlows.Walks`). The trace planner asks the same rule, and no longer keeps a copy.
3. **One function makes the selection.** `lane.SelectSuites` turns a set of suites into the lane's tag and one
   class filter per suite. `run --changed`, a flow selector and a suite selector all call it. The lane choice
   and its refusals moved into `bt` (`bt.ChooseLane`), so the host `bt.cmd` gives the same refusals:
   - Suites of two lanes are `affected_lanes_ambiguous`. `--lane` beside the selector settles the lane.
   - An unknown id is `usage`, and the refusal names the nearest known ids.
   - A story flow that no suite covers is `no_affected_suite`, with the reason "no e2e suite tests this
     flow". `bt` knows the story flows from the committed text under `docs/flows/` (ADR 0153, ADR 0154).
   No selector runs an empty selection.
4. **The host run is the lane, narrowed.** `bt.cmd flow-rename-session` runs the lane's own target and flags,
   with one shard, and passes the class filters as `JB_TEST_JUNIT5_FILTERS`. The daemon and
   `JUnit5BazelRunner` read the same filter syntax.
5. **`run` without `--lease-file` takes its own lease.** It parses the selection first, so a refusal costs
   nothing. It then builds on the host with no worker held, in the pool build scope that `daemon warm` and
   `shard` use. It leases one worker under the holder `<user>-run-<uuid>`, runs the iteration, and releases
   the lease on every exit. The reply adds `lease: {holder, worker, disposition}`. An interrupt keeps the
   lease, as `shard` does, because the iteration can still be in flight, and the refusal names the receipt.
   The release answers and the interrupt watch moved from `host/shard` to `host/lease`, so both commands use
   one copy. Since ADR 0059 they are `release_workers` in `crates/avl-worker/src/lease/set.rs` and the one
   interrupt policy of `crates/avl-daemon/src/leased.rs`.
6. **`run` with `--lease-file` does not change.** A warm inner loop keeps its worker and its receipt.
7. **The planner prints one line per run.** `trace/plan` prints `vm.cmd run <Class>` and `run --changed`
   lines that take their own lease. A GUI-chat line names `--backend parallels`, because that lane belongs on
   the guest the user chose.

## Consequences

- A flow runs from one line, on the host or in a VM. The docs site can print that line for each flow.
- A self-leased run pays the host build and one acquisition for each call. A reader who runs many iterations
  on one worker still takes a receipt, and the skill keeps that sequence for the warm inner loop.
- A GUI-chat suite on the default backend runs on a Linux worker, as `run --lane gui-chat` always did. The
  printed lines name `--backend parallels`, and the run of that line is the user's choice of guest.
- A lower-case hyphenated name that is not a suite id is now a suite selector with a typo, not an unreadable
  selector. The refusal names the nearest suite ids.
- `bt` reads `docs/flows/` only to tell an uncovered story flow from an unknown id. When that directory cannot
  be read, the refusal says "unknown", which is the safe direction.

## Left out

- **One scenario alone.** A suite's scenarios share one managed-agent chain (ADR 0043), and the generator
  resolves the repairs in suite order. One scenario alone is not a valid program yet. The suite is the
  smallest unit that the controller and the site offer.
- **Two lanes in one call.** One iteration runs one lane, and a GUI-chat suite can drive the physical pointer.
  The caller runs one line per lane.

## References

- `../../spec/lane-controller.spec.md`, the selector and self-lease requirements
- `../../spec/scenario-trace.spec.md`, the planner's commands
- `../../tests/integration/vm-lane/crates/avl-bt/src/suites.rs`, `../../tests/integration/vm-lane/crates/avl-lane/src/affected.rs`
- `../../tests/integration/vm-lane/crates/avl-daemon/src/run.rs`, `../../tests/integration/vm-lane/crates/avl-daemon/src/leased.rs`
- [ADR 0043](../../../../../plugins/air/docs/decisions/0043-managed-agent-chain-is-resolved-not-declared.md)
- [ADR 0153](../../../../../plugins/air/docs/decisions/0153-qa-readable-flow-text.md), [ADR 0154](../../../../../plugins/air/docs/decisions/0154-only-story-flows-have-committed-text.md)
