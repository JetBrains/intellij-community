"""Declares one crate of yonder, the Cargo workspace in this directory."""

load("@yonder//:data.bzl", "DEP_DATA")
load("@yonder//:defs.bzl", "aliases", "all_crate_deps", "crate_name", "edition", "lint_config")
load("//build/rust-tools:defs.bzl", "rust_tool_crate", "rust_tool_hub")

# The lint policy of every crate: `[workspace.lints]` of `Cargo.toml` as `@yonder` renders it, plus `-Dwarnings` for
# clippy. `BUILD.bazel` of this package declares it.
_LINTS = Label("//tools/yonder:lints")

# The crates of another workspace that a crate here links through a path dependency, by Cargo package name: `refusal`
# of BT. `@yonder` would build such a crate a second time, without its lint policy.
_CROSS_MODULE_CRATES = {
    "refusal": "//tools/bt/crates/refusal",
}

_HUB = rust_tool_hub(
    aliases = aliases,
    all_crate_deps = all_crate_deps,
    crate_name = crate_name,
    edition = edition,
    lint_config = lint_config,
    workspace_lints = "@yonder//:workspace_cargo_lints",
    dep_data = DEP_DATA,
)

def yonder_rust_crate(name, compile_data = [], target_compatible_with = None):
    """Declares the crate `<name>` of this workspace with `rust_tool_crate` of `community/build/rust-tools/defs.bzl`.

    The targets are the library or the binary, `<name>_test` and `<name>-clippy`. A crate under `bins/` is a binary.

    Args:
      name: the directory name.
      compile_data: files the crate reads at compile time (`include_str!`).
      target_compatible_with: the constraints of a crate that some hosts do not build, such as the agent.
    """
    rust_tool_crate(
        name = name,
        hub = _HUB,
        lints = _LINTS,
        bins_prefix = "tools/yonder/bins/",
        cross_module_crates = _CROSS_MODULE_CRATES,
        compile_data = compile_data,
        target_compatible_with = target_compatible_with,
    )
