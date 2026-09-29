"""The Rust declarations of BT, the Cargo workspace in this directory: one macro per crate, one rule per shipped binary."""

load("@bt//:defs.bzl", "aliases", "all_crate_deps", "crate_name", "edition", "lint_config")
load("@rules_rs//rs:rust_binary.bzl", "rust_binary")
load("@rules_rs//rs:rust_library.bzl", "rust_library")
load("@rules_rs//rs:rust_test.bzl", "rust_test")
load("@rules_rust//rust:defs.bzl", "rust_clippy_test")

# The lint policy of every crate: `[workspace.lints]` of `Cargo.toml` as `@bt` renders it, plus `-Dwarnings` for
# clippy. `BUILD.bazel` of this package declares it. A `Label` resolves in this repository when the ultimate root
# loads this file.
_LINTS = Label("//tools/bt:lints")

# What `@bt` names as the `lint_config` of a crate whose `Cargo.toml` says `[lints] workspace = true`.
_WORKSPACE_LINTS = "@bt//:workspace_cargo_lints"

def bt_rust_crate(name, compile_data = []):
    """Declares the crate `<name>`, its unit test `<name>_test`, and the clippy test `<name>-clippy` over both.

    `@bt` supplies the crate name, the edition and the dependencies from `Cargo.toml` and `Cargo.lock`. A crate under
    `bins/` is a `rust_binary` with `src/main.rs`. Any other crate is a `rust_library` with `src/lib.rs`. Every crate
    builds and tests on Windows too, because BT runs there.

    Args:
      name: the directory name. `@bt` names a local crate by its package label.
      compile_data: files the crate reads at compile time (`include_str!`).
    """
    package = native.package_name()
    if not crate_name():
        fail("Add {} to the members of `Cargo.toml`, then run `cargo build` to update `Cargo.lock`.".format(package))

    # Every crate follows the workspace policy. A crate without `[lints] workspace = true` would build under Bazel with
    # no lints at all, and cargo would still lint it, so the two would disagree unnoticed.
    if lint_config() != _WORKSPACE_LINTS:
        fail("{}: Cargo.toml needs `[lints] workspace = true`".format(package))

    is_binary = package.startswith("tools/bt/bins/")
    (rust_binary if is_binary else rust_library)(
        name = name,
        aliases = aliases(),
        crate_name = crate_name(),
        edition = edition(),
        srcs = native.glob(["src/**/*.rs"]),
        compile_data = compile_data,
        deps = all_crate_deps(normal = True),
        lint_config = _LINTS,
        visibility = ["//visibility:public"],
    )

    # A `rust_test` does not take `lint_config` from its `crate`, so it is passed again. The Air fast lane runs these
    # tests with `--test_sharding_strategy=forced=2`, and a test runner that does not report sharding fails there, so
    # the rules_rust wrapper splits the libtest cases across the shards.
    rust_test(
        name = name + "_test",
        crate = ":" + name,
        compile_data = compile_data,
        data = compile_data,
        deps = all_crate_deps(normal_dev = True),
        experimental_enable_sharding = True,
        lint_config = _LINTS,
    )

    # Clippy over this crate only: each crate it depends on has its own `-clippy`. The clippy aspect of rules_rust skips
    # a target of an external repository, and from the ultimate root this module is `external/community+`. So the test
    # runs only when this module is the main repository: `cd community && ./bazel.cmd test //tools/bt/...`. From the
    # ultimate root it is incompatible, and `bazel test` reports it as skipped rather than as a pass that checked
    # nothing.
    rust_clippy_test(
        name = name + "-clippy",
        size = "small",
        targets = [":" + name, ":" + name + "_test"],
        target_compatible_with = ["@platforms//:incompatible"] if native.repo_name() else [],
    )

_COMPILATION_MODE = "//command_line_option:compilation_mode"
_PLATFORMS = "//command_line_option:platforms"

def _optimized_transition_impl(settings, attr):
    return {
        _COMPILATION_MODE: "opt",
        _PLATFORMS: [str(attr.platform)] if attr.platform else settings[_PLATFORMS],
    }

# A shipped binary is always optimized. `bazel run` and a lane's runfiles build in fastbuild, which for Rust
# is `-Copt-level=0` for every crate and every dependency: the WebP stills, the X11 frame copies and the zip
# deflate would then run ten times slower or worse, inside a lane that measures time.
_optimized_transition = transition(
    implementation = _optimized_transition_impl,
    inputs = [_PLATFORMS],
    outputs = [_COMPILATION_MODE, _PLATFORMS],
)

def _optimized_binary_impl(ctx):
    binary = ctx.attr.binary[0][DefaultInfo]
    executable = binary.files_to_run.executable
    extension = "." + executable.extension if executable.extension else ""
    output = ctx.actions.declare_file(ctx.label.name + extension)
    ctx.actions.symlink(output = output, target_file = executable, is_executable = True)
    return [DefaultInfo(
        executable = output,
        files = depset([output]),
        runfiles = ctx.runfiles([output]).merge(binary.default_runfiles),
    )]

optimized_binary = rule(
    doc = """One shipped binary, optimized, for the host or for one named platform.

    Its only file is the binary. `bazel run --script_path` (the wrappers), `$(rlocationpath)`
    (`ui_lane_ide.bzl`) and the controller's `cquery ... [0]` for the guest agent each need exactly one.
    The Air UI-lane workspace loads this rule as `avl_binary`.""",
    implementation = _optimized_binary_impl,
    executable = True,
    attrs = {
        "binary": attr.label(mandatory = True, executable = True, cfg = _optimized_transition),
        "platform": attr.label(doc = "The platform to build for; the host's when unset."),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
    },
)
