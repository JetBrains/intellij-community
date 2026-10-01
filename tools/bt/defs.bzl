"""Declares one crate of BT, the Cargo workspace in this directory."""

load("@bt//:data.bzl", "DEP_DATA")
load("@bt//:defs.bzl", "aliases", "all_crate_deps", "crate_name", "edition", "lint_config")
load("//build/rust-tools:defs.bzl", "rust_tool_crate", "rust_tool_hub")

# The lint policy of every crate: `[workspace.lints]` of `Cargo.toml` as `@bt` renders it, plus `-Dwarnings` for
# clippy. `BUILD.bazel` of this package declares it. A `Label` resolves in this repository when the ultimate root
# loads this file.
_LINTS = Label("//tools/bt:lints")

# The crates of another workspace that a crate here links through a path dependency, by Cargo package name: `distpath`
# of the dev-dist tools. `@bt` would build such a crate a second time, without its lint policy, so the crate links the
# target of this module instead.
_CROSS_MODULE_CRATES = {
    "distpath": "//build/dev-dist-tools/crates/distpath",
}

_HUB = rust_tool_hub(
    aliases = aliases,
    all_crate_deps = all_crate_deps,
    crate_name = crate_name,
    edition = edition,
    lint_config = lint_config,
    workspace_lints = "@bt//:workspace_cargo_lints",
    dep_data = DEP_DATA,
)

def bt_rust_crate(name, compile_data = [], testdata_env = None):
    """Declares the crate `<name>` of this workspace with `rust_tool_crate` of `community/build/rust-tools/defs.bzl`.

    The targets are the library or the binary, `<name>_test` and `<name>-clippy`. A crate under `bins/` is a binary.
    A crate of another workspace, such as `distpath`, comes from its target in this module (`_CROSS_MODULE_CRATES`).
    Every crate builds and tests on Windows too, because BT runs there. The Air fast lane runs the unit tests with
    `--test_sharding_strategy=forced=2`, so the rules_rust wrapper splits the libtest cases across the shards.

    Args:
      name: the directory name. `@bt` names a local crate by its package label.
      compile_data: files the crate reads at compile time (`include_str!`).
      testdata_env: the environment variable that gives a test the path of `testdata/`, such as `BT_TESTDATA_DIR`.
    """
    rust_tool_crate(
        name = name,
        hub = _HUB,
        lints = _LINTS,
        bins_prefix = "tools/bt/bins/",
        cross_module_crates = _CROSS_MODULE_CRATES,
        compile_data = compile_data,
        test_sharding = True,
        testdata_env = testdata_env,
    )
