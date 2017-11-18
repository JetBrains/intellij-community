load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "dom-tests",
    iml_files = ["dom-tests.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    tags = ["managed"],
    test_runtime_deps = ["//tools/idea:testExtensions"],
    test_srcs = ["tests"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea:execution-impl",
        "//tools/idea:external-system-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:dom-impl[module]",
        "//tools/idea/.idea/libraries:CGLIB",
        "//tools/idea:xml[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/java/java-psi-impl[module]",
        "//tools/idea:java-impl[module, test]",
    ],
)
