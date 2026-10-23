"""`jscpd_test`: a Bazel test that fails on a copy-paste clone, for any system of the repository.

A system declares one target with its jscpd configuration. The target runs the binary of this package with the
pinned jscpd of `//tools:jscpd`, and the binary writes a JUnit report with two cases. The module documentation of
`src/main.rs` describes the run.
"""

# A `Label` resolves each target in the community module, so a caller of another module gets the same files.
_RUNNER = Label("//tools/jscpd-test:runner")
_JSCPD = Label("//tools:jscpd")

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

def _jscpd_test_impl(ctx):
    runner = ctx.attr._runner[DefaultInfo].files_to_run.executable

    # Bazel requires the executable of a target to be an output of that target, so the runner is symlinked. The
    # extension of the runner carries the `.exe` suffix that Windows needs.
    extension = "." + runner.extension if runner.extension else ""
    executable = ctx.actions.declare_file(ctx.label.name + extension)
    ctx.actions.symlink(output = executable, target_file = runner, is_executable = True)

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
            runfiles = ctx.runfiles([executable, jscpd, config] + ctx.files.srcs).merge(
                ctx.attr._runner[DefaultInfo].default_runfiles,
            ),
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
        tags = tags + [tag for tag in (["external", "no-sandbox"] if working_copy else []) if tag not in tags],
        **kwargs
    )
