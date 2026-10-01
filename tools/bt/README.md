# bt: the Bazel test wrapper

This Cargo workspace holds BT, the agent-facing wrapper over `bazel test` that `community/tools/bt.cmd` runs. Bazel
builds every crate through `rules_rs` (repository `@bt`, declared in `community/MODULE.bazel`), from the community
root and from the ultimate root. `cargo` serves the edit-test loop and rust-analyzer.

## The crate map

Run a test target from the ultimate root. From `community/`, drop the `@community` prefix.

| Crate | Content | Bazel test target |
|---|---|---|
| `crates/bt-core` | The areas of `bt.json` and their lane tables, selector resolution, the suite catalog, the bazel command line, and BEP and `test.xml` reading. `fake` is the in-memory runtime the tests share. | `@community//tools/bt/crates/bt-core:bt-core_test` |
| `crates/bt-junit` | The JUnit XML reader: a scanner that keeps the cases of a truncated `test.xml`, and the simple-name class pattern. | `@community//tools/bt/crates/bt-junit:bt-junit_test` |
| `bins/bt` | The binary: the command line and the help, one invocation end to end, the text digest, the `--json` payload, and the process boundary. | `@community//tools/bt/bins/bt:bt_test` |

`bt_rust_crate` in `defs.bzl` declares each crate: its library or binary, its `<crate>_test` and its `<crate>-clippy`.
It binds `rust_tool_crate`, the shared macro core in `community/build/rust-tools/defs.bzl`, to the `@bt` hub.
`optimized_binary` of the core declares a shipped binary: one file, and always optimized. `defs.bzl` re-exports it for
the Air UI-lane workspace. The spec of a Rust tool workspace is `community/build/rust-tools/README.md`.

## The binaries

`BUILD.bazel` declares `:bt`, which `community/tools/bt.cmd` runs: `@community//tools/bt:bt` from an ultimate root and
`//tools/bt:bt` from a community root. `windows_clippy_tests` of the core declares the Windows targets.
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
builds it once. rules_rs reads the `[package]` and `[dependencies]` of such a crate without this workspace, so the
crate spells each `[package]` field and each normal dependency inline. Keep the values equal to `[workspace.package]`
and `[workspace.dependencies]`.

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
