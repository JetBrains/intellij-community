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

`bt_rust_crate` in `defs.bzl` declares each crate: its library or binary, its `<crate>_test` and its `<crate>-clippy`.
`optimized_binary` in `defs.bzl` declares a shipped binary: one file, and always optimized.

## Links into the Air UI-lane workspace

The Air UI-lane workspace, `plugins/air/tests/integration/vm-lane`, links crates of this workspace through path
dependencies. `avl.bzl` there maps each such crate to its label in this module (`_CROSS_MODULE_CRATES`), so Bazel
builds it once. rules_rs reads the `[dependencies]` of such a crate without this workspace, so the crate spells each
normal dependency inline. Keep the version and the features equal to `[workspace.dependencies]`.

## Verification

```sh
cd community && ./bazel.cmd test //tools/bt/...
cd community/tools/bt && cargo test && cargo clippy --all-targets
```

The clippy tests run only from `community/`. The clippy aspect of rules_rust skips a target of an external
repository, and from the ultimate root this module is one. There the tests are incompatible, and `bazel test` reports
them as skipped.
