"""The code gates of a system of the repository: `code_gates`, and the two tests that it declares.

- `jscpd_test` fails on a copy-paste clone. It runs the binary of this package with the pinned jscpd of
  `//tools:jscpd`, and the binary writes a JUnit report with two cases.
- `accio_unused_test` fails on a declaration that nothing uses. It runs the same binary, which runs
  `community/tools/accio.cmd` of the checkout, and writes a JUnit report with one case. The flag
  `//tools/jscpd-test:unused` turns it on; it is off by default.

The module documentation of `src/main.rs` and `src/unused.rs` describes the runs.
"""

# A `Label` resolves each target in the community module, so a caller of another module gets the same files.
_RUNNER = Label("//tools/jscpd-test:runner")
_JSCPD = Label("//tools:jscpd")
_ACCIO = Label("//tools:accio.cmd")
_UNUSED_ON = Label("//tools/jscpd-test:unused_on")

# The variables that an unused test takes from the environment of the client: the index home of accio and the cache of
# the wrapper live under the home of the user, and the wrapper looks for a newer accio on the PATH.
_UNUSED_INHERITED_ENVIRONMENT = [
    "ACCIO_HOME",
    "HOME",
    "LOCALAPPDATA",
    "PATH",
    "TMPDIR",
    "USERPROFILE",
    "XDG_CACHE_HOME",
]

def _repository_path(file):
    """The path of a file inside its repository, which the runner strips from the resolved path to find the root."""
    if file.short_path.startswith("../"):
        return file.short_path.split("/", 2)[2]
    return file.short_path

def _rlocation_path(ctx, file):
    """The runfiles path of a file, which is what the runfiles library of the runner resolves."""
    if file.short_path.startswith("../"):
        return file.short_path[len("../"):]
    return ctx.workspace_name + "/" + file.short_path

def _runner_executable(ctx):
    """The executable of a test: a link to the runner.

    Bazel requires the executable of a target to be an output of that target, so the runner is symlinked. The
    extension of the runner carries the `.exe` suffix that Windows needs.
    """
    runner = ctx.attr._runner[DefaultInfo].files_to_run.executable
    extension = "." + runner.extension if runner.extension else ""
    executable = ctx.actions.declare_file(ctx.label.name + extension)
    ctx.actions.symlink(output = executable, target_file = runner, is_executable = True)
    return executable

def _runner_runfiles(ctx, files):
    return ctx.runfiles(files).merge(ctx.attr._runner[DefaultInfo].default_runfiles)

def _jscpd_test_impl(ctx):
    executable = _runner_executable(ctx)
    config = ctx.file.config
    jscpd = ctx.file._jscpd
    environment = {
        "JSCPD_TEST_JSCPD": _rlocation_path(ctx, jscpd),
        "JSCPD_TEST_CONFIG": _rlocation_path(ctx, config),
        "JSCPD_TEST_CONFIG_PATH": _repository_path(config),
        "JSCPD_TEST_CLASS": ctx.attr.junit_class,
        "JSCPD_TEST_CLONE_CASE": ctx.attr.clone_case,
        "JSCPD_TEST_MARKER_CASE": ctx.attr.marker_case,
        "JSCPD_TEST_GUIDE": ctx.attr.guide,
    }
    if ctx.attr.working_copy:
        environment["JSCPD_TEST_WORKING_COPY"] = "1"

    return [
        DefaultInfo(
            executable = executable,
            runfiles = _runner_runfiles(ctx, [executable, jscpd, config] + ctx.files.srcs),
        ),
        RunEnvironmentInfo(environment = environment),
    ]

_jscpd_test = rule(
    implementation = _jscpd_test_impl,
    test = True,
    attrs = {
        "clone_case": attr.string(mandatory = True),
        "config": attr.label(mandatory = True, allow_single_file = [".json"]),
        "guide": attr.string(),
        "junit_class": attr.string(mandatory = True),
        "marker_case": attr.string(mandatory = True),
        "srcs": attr.label_list(allow_files = True),
        "working_copy": attr.bool(),
        "_jscpd": attr.label(default = _JSCPD, allow_single_file = True, cfg = "target"),
        "_runner": attr.label(default = _RUNNER, executable = True, cfg = "target"),
    },
)

def _with_working_copy_tags(tags):
    """`tags` with `external` and `no-sandbox`: the test reads the checkout, so Bazel neither sandboxes nor caches it."""
    return tags + [tag for tag in ["external", "no-sandbox"] if tag not in tags]

def jscpd_test(
        name,
        config,
        junit_class,
        srcs = [],
        working_copy = False,
        clone_case = "noCopyPasteClone",
        marker_case = "everyCloneMarkerStatesItsReasonAndEnds",
        guide = "",
        tags = [],
        **kwargs):
    """Declares a test that fails when jscpd finds a clone under `config`, or a clone marker states no reason.

    The configuration has no baseline, so each clone fails the test. The test reads the `path`, `format` and `ignore`
    keys of the configuration for its marker check; jscpd reads the whole file.

    A hermetic target (`working_copy = False`) declares every scanned file in `srcs`. jscpd then scans the runfiles
    and follows their links, and Bazel caches the result until a source changes. A Windows host gets no runfiles
    tree, so such a target needs a constraint against Windows.

    A target with `working_copy = True` scans the checkout that holds `config`, like an unsandboxed `jps_test`: it gets
    the tags `external` and `no-sandbox` when `tags` lacks them, Bazel never caches it, and it also sees the files of
    other sessions that a shared checkout holds. Use it only for a system whose sources Bazel does not declare.

    Args:
      name: the name of the test target.
      config: the jscpd configuration file. Its `path` entries are relative to its directory.
      junit_class: the class name of both cases in the JUnit report, such as the name of an older JUnit test.
      srcs: the scanned files of a hermetic target.
      working_copy: scan the checkout instead of the runfiles.
      clone_case: the name of the case that fails on a clone.
      marker_case: the name of the case that checks the clone markers.
      guide: one sentence that a failure prints, such as the path of the guide to removing a clone.
      tags: the tags of the test.
      **kwargs: the common attributes of a test, such as `size`, `timeout` or `target_compatible_with`.
    """
    _jscpd_test(
        name = name,
        config = config,
        junit_class = junit_class,
        srcs = srcs,
        working_copy = working_copy,
        clone_case = clone_case,
        marker_case = marker_case,
        guide = guide,
        tags = _with_working_copy_tags(tags) if working_copy else tags,
        **kwargs
    )

def _accio_unused_test_impl(ctx):
    executable = _runner_executable(ctx)
    anchor = ctx.file.anchor
    accio = ctx.file._accio
    environment = {
        "ACCIO_UNUSED_TEST_ACCIO": _rlocation_path(ctx, accio),
        "ACCIO_UNUSED_TEST_ANCHOR": _rlocation_path(ctx, anchor),
        "ACCIO_UNUSED_TEST_ANCHOR_PATH": _repository_path(anchor),
        "ACCIO_UNUSED_TEST_SCOPES": json.encode(ctx.attr.scopes),
        "ACCIO_UNUSED_TEST_CLASS": ctx.attr.junit_class,
        "ACCIO_UNUSED_TEST_CASE": ctx.attr.case,
        "ACCIO_UNUSED_TEST_GUIDE": ctx.attr.guide,
    }
    return [
        DefaultInfo(executable = executable, runfiles = _runner_runfiles(ctx, [executable, accio, anchor])),
        RunEnvironmentInfo(environment = environment, inherited_environment = _UNUSED_INHERITED_ENVIRONMENT),
    ]

_accio_unused_test = rule(
    implementation = _accio_unused_test_impl,
    test = True,
    attrs = {
        "anchor": attr.label(mandatory = True, allow_single_file = True),
        "case": attr.string(mandatory = True),
        "guide": attr.string(),
        "junit_class": attr.string(mandatory = True),
        "scopes": attr.string_list(mandatory = True, allow_empty = False),
        "_accio": attr.label(default = _ACCIO, allow_single_file = True, cfg = "target"),
        "_runner": attr.label(default = _RUNNER, executable = True, cfg = "target"),
    },
)

def accio_unused_test(
        name,
        anchor,
        scopes,
        junit_class,
        case = "noUnusedDeclaration",
        guide = "",
        tags = [],
        **kwargs):
    """Declares a test that fails when `accio unused` lists a declaration of `scopes`, off unless the flag is on.

    The test reads the checkout that holds `anchor`, with the index home of the user, so it gets the tags `external`
    and `no-sandbox`. A declaration that stays without a use carries the mark of its language that `accio unused --help`
    names, and accio leaves it out. The target is incompatible unless `//tools/jscpd-test:unused` is on, so
    `bazel test //...` skips it.

    Args:
      name: the name of the test target.
      anchor: a file of the package, such as its jscpd configuration; the test finds the checkout through it.
      scopes: the `--scope` entries: a path relative to the directory of `anchor`, or a pattern of accio, such as
        `!**/generated/**`, as it is.
      junit_class: the class name of the case in the JUnit report.
      case: the name of the case.
      guide: one sentence that a failure prints, such as the path of the guide to removing dead code.
      tags: the tags of the test.
      **kwargs: the common attributes of a test, such as `size` or `timeout`.
    """
    _accio_unused_test(
        name = name,
        anchor = anchor,
        scopes = scopes,
        junit_class = junit_class,
        case = case,
        guide = guide,
        tags = _with_working_copy_tags(tags),
        target_compatible_with = select({
            _UNUSED_ON: [],
            "//conditions:default": ["@platforms//:incompatible"],
        }),
        **kwargs
    )

def code_gates(
        name,
        jscpd_config,
        junit_class,
        unused_junit_class,
        unused_scopes = ["."],
        srcs = [],
        working_copy = False,
        duplicate_code_name = None,
        clone_case = "noCopyPasteClone",
        marker_case = "everyCloneMarkerStatesItsReasonAndEnds",
        unused_case = "noUnusedDeclaration",
        guide = "",
        unused_guide = "",
        size = None,
        target_compatible_with = None,
        tags = [],
        **kwargs):
    """Declares the code gates of a system: `<name>-duplicate-code_test` and `<name>-unused-code_test`.

    The first is a `jscpd_test` with `jscpd_config`, `srcs` and `working_copy`. The second is an `accio_unused_test`
    of `unused_scopes`, with `jscpd_config` as its anchor; it reads the checkout whatever `working_copy` says, and it is
    off until the flag `//tools/jscpd-test:unused` is on.

    Args:
      name: the name of the system, the prefix of both targets.
      jscpd_config: the jscpd configuration file of the system.
      junit_class: the class name of the cases of the copy-paste test in the JUnit report.
      unused_junit_class: the class name of the case of the unused test in the JUnit report.
      unused_scopes: the scope entries of the unused test, relative to the directory of `jscpd_config`.
      srcs: the scanned files of a hermetic copy-paste test.
      working_copy: the copy-paste test scans the checkout instead of the runfiles.
      duplicate_code_name: the name of the copy-paste test instead of `<name>-duplicate-code_test`, so an older
        target keeps its name and its history.
      clone_case: the name of the clone case of the copy-paste test.
      marker_case: the name of the marker case of the copy-paste test.
      unused_case: the name of the case of the unused test.
      guide: one sentence that a failure of the copy-paste test prints.
      unused_guide: one sentence that a failure of the unused test prints.
      size: the size of the copy-paste test.
      target_compatible_with: the constraints of the copy-paste test, such as one against Windows for a hermetic one.
      tags: the tags of both tests.
      **kwargs: the other common attributes of both tests.
    """
    jscpd_test(
        name = duplicate_code_name or name + "-duplicate-code_test",
        config = jscpd_config,
        junit_class = junit_class,
        srcs = srcs,
        working_copy = working_copy,
        clone_case = clone_case,
        marker_case = marker_case,
        guide = guide,
        size = size,
        target_compatible_with = target_compatible_with,
        tags = tags,
        **kwargs
    )
    accio_unused_test(
        name = name + "-unused-code_test",
        anchor = jscpd_config,
        scopes = unused_scopes,
        junit_class = unused_junit_class,
        case = unused_case,
        guide = unused_guide,
        timeout = "long",
        tags = tags,
        **kwargs
    )
