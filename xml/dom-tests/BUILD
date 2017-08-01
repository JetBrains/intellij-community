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
    deps = [
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:CGLIB",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/java/java-psi-impl[module]",
        "//tools/idea:java-impl[module, test]",
    ],
)
