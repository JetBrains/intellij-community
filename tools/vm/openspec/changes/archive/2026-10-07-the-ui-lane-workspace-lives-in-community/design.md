# The UI-lane workspace lives in community/tools/vm, beside BT

## Context

The controller, the planner, the recorder and the guest agent are one Cargo workspace. BT is the community workspace
`community/tools/bt`, with its wrapper `community/tools/bt.cmd`. The UI-lane workspace sits beside it, so the
agent-facing tool wrappers have one home and the Rust tools have one Bazel module.

Everything under `community/` is the module `@community`. That module cannot see the root files and the repositories
of the ultimate root. So each input of the workspace that crosses the module edge needs a rule.

## Decisions

1. **The workspace is `community/tools/vm`.** The wrappers are `community/tools/vm.cmd` and
   `community/tools/trace.cmd`. Each builds `@community//tools/vm:<name>` from an ultimate root and
   `//tools/vm:<name>` from a community root, with the root detection of `bt.cmd`. A wrapper failure exits 78.
2. **The community module includes the pins.** `community/MODULE.bazel` includes `avl.MODULE.bazel` and the
   pin fragments of Tart, the Docker CLI and Apple `container`.
3. **The JUnit test rule is shared.** `rust_test_junit` and its wrapper binary live in
   [rust-tools](../../../../../../build/rust-tools/README.md).
4. **One `manual` alias per pinned-tool repository.** The root cannot see a repository of the community module by
   name. So [the workspace package](../../../../BUILD.bazel) declares one alias per repository, with the name of the
   repository. The controller asks Bazel for the alias, and it matches the `cquery` line by the last `+` segment of
   the canonical name.
5. **Each program reads `bt.json` at its start.** `avl_affected::bridge::install` reads `bt.json` of the checkout and
   keeps the Air area alone. A checkout whose `bt.json` names no Air area gets `air_area_missing` at exit 65. The
   controller reads it before the parse, because the help lists the lanes.
6. **The image pipeline is in the workspace.** `provision/` lives in `community/tools/vm`. The `vm-ui-tests` skill
   keeps `SKILL.md` and `references/`.
7. **The tests that read root files live in the root.** The package
   [vm-contract](../../../../../../../plugins/air/tests/integration/vm-contract/BUILD.bazel) holds them. They check the
   Air area of `bt.json`, the `vm.cmd` examples of the docs and the descriptor version of the runtime writer. The
   planner tests over the committed suite documents are there too.
8. **The unit tests read a copy of the Air lane table.** The copy lives in `bt_core::fake`, and each test that reads
   the Air area installs it with `install_fixture`. `vm-contract:areas-test` keeps the copy equal to the live table.
9. **The Air fast lane names the workspace.** It names `@community//tools/vm/...` beside `//plugins/air/...` and
   `@community//tools/bt/...`.
10. **The lane table names the controller directory.** The field `laneWidePaths` holds `"community/tools/vm": []`, the
    harness of every lane. A changed path under it reaches every suite of every UI lane. So the controller names no
    area directory in its code.

The [suites a change reaches](../../../../spec/lane-controller.spec.md#the-suites-a-change-reaches) state the
contract.

## Consequences

- The clippy tests of the controller run only from `community/`, as the clippy tests of BT do. From the ultimate root
  they report a skip.
- A runfiles path of a workspace file is `community+/<path>` from the ultimate root.
- The controller still runs Air targets and reads Air files at run time. A full decoupling from Air is out of scope.
