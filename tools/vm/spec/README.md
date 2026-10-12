# Controller specs

The specs in this directory state the requirements of the UI-lane controller, the recorder, the server and the
planner. Each spec follows the shared format of [`SPEC_GUIDE.md`](../../../.ai/spec/SPEC_GUIDE.md).

- [Scenario Traces](scenario-trace.spec.md): the trace bundle, the recorder's protocol, the capture, the pack, the
  server and the planner.
- [The UI-lane Controller](lane-controller.spec.md): the suites a change reaches, the run output and the verdict, the
  daemon start, the guest agent and the worker lease.

The Kotlin side of a UI lane has its own specs under `plugins/air/spec/docs`. Each spec here links its Air partner.

## Path conventions

A path in `targets` or in a `[@test]` link is relative to the spec file. From `community/tools/vm/spec/x.spec.md`,
the paths are these:

| Target | Path |
| --- | --- |
| a crate of this workspace | `../crates/<crate>/...` |
| a wrapper | `../../trace.cmd` or `../../vm.cmd` |
| a BT crate | `../../bt/crates/<crate>/...` |
| an Air file | `../../../../plugins/air/...` |
| a controller ADR | `../docs/decisions/NNNN-<title>.md` |
| an Air ADR | `../../../../plugins/air/docs/decisions/NNNN-<title>.md` |

## Writing rules

- A spec names no implementation language. [`AGENTS.md`](../AGENTS.md) of this workspace states that rule.
- A sentence follows ASD-STE100 Simplified Technical English and has at most 25 words.

## The gate

Run `./community/tools/bt.cmd AirSpecReferencesTest` from the ultimate root after a change of a spec. The test scans
this directory and `plugins/air/spec`. It checks the targets, the test links, the Markdown links, the style tag, the
sentence budget and the section names.

A link into `plugins/air` is dead in a community-only checkout. That is by design, because only the ultimate checkout
has the Air area, as the [workspace README](../README.md) states.
