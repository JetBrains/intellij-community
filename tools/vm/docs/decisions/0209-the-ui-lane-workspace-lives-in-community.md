---
topic: testing
---

# 209. The UI-lane workspace lives in community

Date: 2026-10-07

## Status

Accepted. It moves the UI-lane workspace of [ADR 0059](0059-the-ui-lane-tooling-is-rust.md) beside BT, as
[ADR 0070](../../../../../plugins/air/docs/decisions/0070-bt-core-lives-in-community.md) moved BT. It amends item 4 of ADR 0070: the controller reads the Air
area from `bt.json` at run time and does not compile the lane table in. The crate names, the hub `@avl`, the exit
codes and the command lines do not change.

## Context

The controller, the planner, the recorder and the guest agent are one Cargo workspace. It was a package of the
ultimate root, under `plugins/air/tests/integration`. Its two wrappers were in `plugins/air/scripts`. BT is the
community workspace `community/tools/bt`, and its wrapper is `community/tools/bt.cmd`. So the agent-facing tool
wrappers had two homes, and the Rust tools had two Bazel modules.

Everything under `community/` is the module `@community`. A move into it is a module move. The workspace used five
inputs that the community module cannot see:

- the hub `@avl` and the Tart, Docker CLI and Lima pins, which the root `MODULE.bazel` included;
- the JUnit test rule `rust_test_junit` of `//fleet`;
- the root files that crates took as compile data or test data: `bt.json`, the Air lane table, the runtime
  descriptor's writer, the docs with `vm.cmd` examples, and the image pins of the `vm-ui-tests` skill;
- two `include_str!` paths out of the workspace: the Air lane table and the image pins;
- runfiles paths under `_main/`.

## Decision

1. The workspace is `community/tools/vm`. The wrappers are `community/tools/vm.cmd` and `community/tools/trace.cmd`.
   Each runs `@community//tools/vm:<name>` from an ultimate root and `//tools/vm:<name>` from a community root, with
   the root detection of `bt.cmd`. The wrappers keep exit 78 and `--config=bt --config=air-lane-linux`.
2. `community/MODULE.bazel` includes `avl.MODULE.bazel` and the three pin fragments. The `air-lane-*` configs move to
   `community/common.bazelrc`, which both roots read.
3. `rust_test_junit` and its wrapper binary move to `community/build/rust-tools`, with the wrapper as an argument.
   Fleet keeps a re-export and an alias. This is item 2 of IJPL-257384.
4. The pinned-tool repositories are repositories of the community module, and the root cannot see them by name. So
   the workspace package declares one `manual` alias per repository, with the name of the repository. The
   controller asks Bazel for the alias, and it matches the `cquery` line by the last `+` segment of the canonical
   name, as before.
5. Each program reads `bt.json` of the checkout at its start, through `avl_affected::bridge::install`. The bridge
   keeps the Air area alone. A checkout whose `bt.json` names no Air area is refused with `air_area_missing` and
   exit 65. The controller reads it before the parse, because the help lists the lanes.
6. The image pipeline, `provision/`, moves from the skill into the workspace. The skill keeps `SKILL.md` and
   `references/`.
7. The tests that read root files move to the root package `//plugins/air/tests/integration/vm-contract`: the Air
   area of `bt.json`, the `vm.cmd` examples of the docs, the descriptor version of the runtime writer, and the
   planner tests over the committed suite documents. The planner test is the unit test of `air-trace` again, with
   `crate =` and the flow-profiles jar. `avl_crate_test` in `avl.bzl` declares it with the test dependencies of the
   crate, resolved in the community module.
8. The unit tests of the workspace read the copy of the Air lane table in `bt_core::fake`. Each test that reads the
   Air area installs that copy with `install_fixture`. The live table is checked by `vm-contract:areas-test`.
9. The Air fast lane names `@community//tools/vm/...` beside `//plugins/air/...` and `@community//tools/bt/...`.
   `vm-contract:areas-test` keeps the copy of item 8 equal to the live table.
10. The controller has no Air module after the move, so no lane-wide module holds it. The lane table names its
    directory in the new field `laneWidePaths`: `"community/tools/vm": []`, the harness of every lane. A changed
    path under it reaches every suite of every UI lane, as `lane-wide:community/tools/vm`. The controller reads the
    directory from the table and names no area directory in its code, so it stays area-agnostic.

## Consequences

- A change of the controller runs its tests in the Air fast lane, as before the move.
- `vm run --changed`, `vm suites` and `trace plan` answer every suite of every UI lane for a controller path, as
  before the move.
- The clippy tests of the controller run only from `community/`, as BT's do. From the ultimate root they report a
  skip.
- `vm --help` refuses in a checkout without the Air area. A community-only run of the controller is out of scope.
- A runfiles path of a workspace file is `community+/<path>` from the ultimate root. The Kotlin contract test reads
  the golden transcript there.
- A spec link into the workspace climbs out of `plugins/air`. `AirSpecReferencesTest` matches a glob outside
  `plugins/air` against the files under the fixed directory of the glob.

What this decision leaves out:

- A full decoupling from Air. The controller still runs `//plugins/air/tests/integration/ui:ui_daemon`, reads the
  Air specs and `.iml` files in `avl-affected`, and builds `//build:idea_dist`. These are run-time strings, which the
  community build does not resolve.
- The TeamCity configurations outside this repository that name the old labels.
- The rest of IJPL-257384.
- The generated docs output, which `pnpm run harvest` writes again.
