"""Declares one crate of the Air UI-lane workspace, the Cargo workspace in this directory, and its shipped binaries."""

load("@avl//:data.bzl", "DEP_DATA")
load("@avl//:defs.bzl", "aliases", "all_crate_deps", "crate_name", "edition", "lint_config")
load(
    "//build/rust-tools:defs.bzl",
    _LINUX_MUSL_PLATFORMS = "LINUX_MUSL_PLATFORMS",
    _NOT_ON_WINDOWS = "NOT_ON_WINDOWS",
    _optimized_binary = "optimized_binary",
    _rust_tool_crate = "rust_tool_crate",
    _rust_tool_hub = "rust_tool_hub",
    _rust_tool_test_deps = "rust_tool_test_deps",
)
load("//build/rust-tools:rust_test_junit.bzl", "rust_test_junit")

# The one `target_compatible_with` of every crate that is not `portable`. The controller, the guest agent, the
# viewer's server and the planner are Unix tools; ADR 0059 says which crates the Windows CI host builds.
NOT_ON_WINDOWS = _NOT_ON_WINDOWS

# `community/common.bazelrc` scrubs `PATH` on Windows. A test that starts a host tool needs it back. On Unix
# `--incompatible_strict_action_env` already gives a `PATH` with `/bin` and `/usr/bin`.
_TEST_ENV_INHERIT = select({
    "@platforms//os:windows": ["PATH", "PATHEXT"],
    "//conditions:default": [],
})

# The lint policy of every crate: `[workspace.lints]` of `Cargo.toml` as `@avl` renders it, plus `-Dwarnings` for
# clippy. `BUILD.bazel` of this package declares it. A `Label` resolves it in the community module, so a test package
# of the ultimate root that loads this file gets the same target.
_LINTS = Label("//tools/vm:lints")

# The crates of another workspace that a crate here links through a path dependency, by Cargo package name: BT's, and
# `distpath` and `fscopy` of the dev-dist tools. rules_rs names such a crate `@avl//:<name>-<version>` and would
# build it a second time, without the lint policy. The community module builds it already, so the crate links that
# target instead.
_CROSS_MODULE_CRATES = {
    "bt-core": "//tools/bt/crates/bt-core",
    "bt-junit": "//tools/bt/crates/bt-junit",
    "refusal": "//tools/bt/crates/refusal",
    "distpath": "//build/dev-dist-tools/crates/distpath",
    "fscopy": "//build/dev-dist-tools/crates/fscopy",
}

# The closure of a shipped binary is the closure for x86_64 Linux, where the guest agent and the recorder run. Each host
# adds crates of its own (the Windows console crates, `nix` on Unix, `fsevent-sys` on macOS), so the closure of the host
# would need one `closure.txt` per host.
_CLOSURE_PLATFORM = _LINUX_MUSL_PLATFORMS["x86_64"]

_HUB = _rust_tool_hub(
    aliases = aliases,
    all_crate_deps = all_crate_deps,
    crate_name = crate_name,
    edition = edition,
    lint_config = lint_config,
    workspace_lints = "@avl//:workspace_cargo_lints",
    dep_data = DEP_DATA,
)

def avl_crate(
        portable = False,
        closure = False,
        compile_data = [],
        test_data = [],
        test_env = {},
        test_size = "small",
        test_tags = [],
        visibility = ["//visibility:public"]):
    """Declares the crate of this package with `rust_tool_crate` of `community/build/rust-tools/defs.bzl`.

    The package is the crate: its directory name is the Cargo package name and the library target name, which is what
    `all_crate_deps` of a dependent crate expects. The targets are the library, a `<bin>-bin` per binary of its
    `Cargo.toml`, `<crate>-test` and `<crate>-clippy`. A package without `src/lib.rs` is a binary crate, as Cargo has
    it: its binary target has the name of its `[[bin]]`, and each `tests/<stem>.rs` is the process test
    `<crate>-<stem>-test`, which `<crate>-clippy` lints too. Dependencies and binaries come from the crate's
    `Cargo.toml`, through `@avl`, so the Bazel file never repeats them. A crate of BT's workspace comes from the
    community module (`_CROSS_MODULE_CRATES`).

    The test runs through `rust_test_junit`: libtest writes no report, Bazel's own report holds one case for the whole
    target, and BT counts cases. `AirTestTargetSandboxTest` does not see this test, because it scans `BUILD.bazel`
    files for rule calls named `*_test`. So nothing gates `test_tags`, and an `external` or `no-sandbox` tag there makes
    the test uncached.

    The library of a crate that is not portable is `manual` and has no `NOT_ON_WINDOWS`, so `//...` does not build it
    for Windows and a guest binary cross-built on a Windows host does not refuse its own library. Its test, its clippy
    and its binaries keep the constraint. `<crate>-clippy` never runs on Windows: that host would lint `cfg(windows)`
    code with a Windows clippy, which the root `clippy-windows-x86_64` and `clippy-windows-arm64` do from a Unix host.

    Args:
      portable: whether the crate is also built and tested on Windows (ADR 0059). A crate that a portable
        crate or its test depends on must be portable too.
      closure: pins the crate closure of the one binary of the crate in `closure.txt`, as `<bin>_closure_test`. The
        closure is the one for x86_64 Linux on every host.
      compile_data: files the crate reads at compile time (`include_str!`); its test finds them at run time
        too, so a file the test also reads is listed here once.
      test_data: files the test reads at run time, found through `avl_testkit`.
      test_env: environment of the test, e.g. an `$(rlocationpath ...)`.
      test_size: the Bazel size of the test.
      test_tags: tags of the test only.
      visibility: visibility of the library and the binaries. The default is public, so the test packages of the
        ultimate root can link a library.
    """
    package = native.package_name()
    crate = package.split("/")[-1]
    target_compatible_with = [] if portable else NOT_ON_WINDOWS
    _rust_tool_crate(
        name = crate,
        hub = _HUB,
        lints = _LINTS,
        cross_module_crates = _CROSS_MODULE_CRATES,
        compile_data = compile_data,
        test_data = test_data,
        closure = closure,
        closure_platform = _CLOSURE_PLATFORM,
        integration_tests = True,
        # Lets `avl_testkit` find this crate's files under Bazel, where `CARGO_MANIFEST_DIR` names the execroot of the
        # compile action rather than the runfiles of the test. `AVL_REPO` is the runfiles directory of the module:
        # `community+` from the ultimate root, and `_main` from the community root.
        rustc_env = {"AVL_PACKAGE": package, "AVL_REPO": native.repo_name() or "_main"},
        target_compatible_with = target_compatible_with,
        library_tags = [] if portable else ["manual"],
        clippy_on_windows = False,
        visibility = visibility,
        test_rule = rust_test_junit,
        test_suffix = "-test",
        test_crate_suffix = "_libtest",
        test_env = test_env,
        test_env_inherit = _TEST_ENV_INHERIT,
        test_size = test_size,
        test_tags = test_tags,
    )

def avl_crate_test(name, package, data = [], env = {}, size = "small"):
    """Declares the unit test of the crate in `package` of this workspace again, for a package of the ultimate root.

    The test of `avl_crate` cannot have a target of the ultimate root as data, because the community module does not
    see it. This test is the same `rust_test` with the data of the caller. The crate, its test dependencies, the lint
    policy and `AVL_PACKAGE` come from this workspace.

    Args:
      name: the name of the test target.
      package: the package of the crate in the community module, such as `tools/vm/crates/air-trace`.
      data: the files that the test reads at run time.
      env: the environment of the test, such as an `$(rlocationpath ...)` of the caller's data.
      size: the Bazel size of the test.
    """
    crate = Label("//{}:{}".format(package, package.split("/")[-1]))
    rust_test_junit(
        name = name,
        crate = crate,
        data = data,
        deps = _rust_tool_test_deps(_HUB, package, _CROSS_MODULE_CRATES),
        env = env,
        lint_config = _LINTS,
        rustc_env = {"AVL_PACKAGE": package, "AVL_REPO": crate.repo_name or "_main"},
        size = size,
        target_compatible_with = NOT_ON_WINDOWS,
    )

# A shipped binary: one file, and always optimized.
avl_binary = _optimized_binary
