# bt: the Bazel test wrapper

This Cargo workspace holds BT, the agent-facing wrapper over `bazel test` that `community/tools/bt.cmd` runs. Bazel
builds every crate through `rules_rs` (repository `@bt`, declared in `community/MODULE.bazel`), from the community
root and from the ultimate root. `cargo` serves the edit-test loop and rust-analyzer.

## The crate map

Run a test target from the ultimate root. From `community/`, drop the `@community` prefix.

| Crate | Content | Bazel test target |
|---|---|---|
| `crates/bt-core` | The areas of `bt.json` and their lane tables, selector resolution, the suite catalog, the bazel command line, BEP and `test.xml` reading, and the exit codes of `bt`. `fake` is the in-memory runtime the tests share. | `@community//tools/bt/crates/bt-core:bt-core_test` |
| `crates/bt-junit` | The JUnit XML reader: a scanner that keeps the cases of a truncated `test.xml`, and the simple-name class pattern. | `@community//tools/bt/crates/bt-junit:bt-junit_test` |
| `crates/refusal` | The refusal of a person- or agent-facing CLI: a stable code, a message, the exit code, and the details as JSON text. It has no dependency, and each tool keeps its own exit codes. | `@community//tools/bt/crates/refusal:refusal_test` |
| `bins/bt` | The binary: the command line and the help, one invocation end to end, the text digest, the `--json` payload, and the process boundary. | `@community//tools/bt/bins/bt:bt_test` |
| `bins/startup-bench` | The start-up controller over the Bazel dev distribution, which `community/tools/startup-bench.cmd` runs: the `welcome`, `open-project` and `replay` commands, the readers of the start-up report, the trace, the FUS log, the class log and the CPU profile, the gate, the digest and `summary.json`. Its tests read `testdata/` through `BT_TESTDATA_DIR`. | `@community//tools/bt/bins/startup-bench:startup-bench_test` |

`bt_rust_crate` in `defs.bzl` declares each crate: its library or binary, its `<crate>_test` and its `<crate>-clippy`.
It binds `rust_tool_crate`, the shared macro core in `community/build/rust-tools/defs.bzl`, to the `@bt` hub. bt-core
takes the slash-path rules from `distpath` of the dev-dist tools, through a path dependency. `_CROSS_MODULE_CRATES` in
`defs.bzl` maps it to its target in this module, so Bazel builds it once.
`optimized_binary` of the core declares a shipped binary: one file, and always optimized. `defs.bzl` re-exports it for
the Air UI-lane workspace. The spec of a Rust tool workspace is `community/build/rust-tools/README.md`.

## The binaries

`BUILD.bazel` declares `:bt`, which `community/tools/bt.cmd` runs: `@community//tools/bt:bt` from an ultimate root and
`//tools/bt:bt` from a community root. It declares `:startup-bench` the same way, for `community/tools/startup-bench.cmd`.

`startup-bench` measures the start-up of an IDE from the Bazel dev distribution. Each run of an arm executes in
`<session>/<arm>-sandbox`, and its sandbox then moves into the run directory. The sandbox settings put the welcome
project into the sandbox, so a session does not touch `~/IdeaProjects`. `open-project` reports no number yet. The IDE
opens the second project through the socket lock without a trace span, and `editor highlighting completed` is only an
instant event of the start-up report, which the IDE writes once. So each run of the command fails with a named
reason and exits with 2. That exit is a gap of the product, not a defect of the tool. `windows_clippy_tests` of the core declares the Windows targets.
`:bt-windows-x86_64` and `:bt-windows-arm64` are compile checks you build by hand (`manual`), and
`:clippy-windows-x86_64` and `:clippy-windows-arm64` lint the `cfg(windows)` code from a Unix host.

## Areas

`bt.json` at the checkout root names each area: a repo-relative directory that resolution scans, and the lane table
of that directory. `crates/bt-core/src/areas.rs` and `crates/bt-core/src/lanes.rs` give the two shapes. A checkout
without `bt.json` has no area: a label and a pattern resolve, and a name, a package, a flow or a suite selector
refuses. The ultimate checkout names `plugins/air`, with its lane table `plugins/air/tests/integration/lanes.json`.

## Links into the Air UI-lane workspace

The Air UI-lane workspace, `plugins/air/tests/integration/vm-lane`, links crates of this workspace through path
dependencies. `avl.bzl` there maps each such crate to its label in this module (`_CROSS_MODULE_CRATES`), so Bazel
builds it once. rules_rs reads the `[package]` and `[dependencies]` of such a crate without this workspace. The same
holds for a crate of this workspace that such a crate links, such as `refusal`. So each of these crates spells each
`[package]` field and each normal dependency inline. Keep the values equal to `[workspace.package]` and
`[workspace.dependencies]`.

That build links this workspace's `serde`, `serde_json` and `regex`, which are other crates than the ones the Air
workspace links. Cargo unifies them, so only Bazel shows the difference. A public API of a linked crate therefore
hands a value across as JSON text (`Refusal::details_json_text`, `Affected::to_json_text`, `LaneCount::to_json_text`),
and takes no `regex::Regex` and no `serde_json::Value` from its caller.

## Verification

```sh
cd community && ./bazel.cmd test //tools/bt/...
cd community/tools/bt && cargo test && cargo clippy --all-targets
./bazel.cmd test @community//tools/bt/... //plugins/air/tests/integration/vm-lane/...    # from the ultimate root
```

The clippy tests run only from `community/`. The clippy aspect of rules_rust skips a target of an external
repository, and from the ultimate root this module is one. There the tests are incompatible, and `bazel test` reports
them as skipped.
