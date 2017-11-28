load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "dom-tests",
    iml_files = ["dom-tests.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    tags = ["managed"],
    test_srcs = ["tests"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea:execution-impl",
        "//tools/idea:external-system-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:dom-impl[module]",
        "//tools/idea/.idea/libraries:CGLIB",
        "//tools/idea:xml[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/java/java-psi-impl[module]",
        "//tools/idea:java-impl[module, test]",
    ],
)
