load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.textmate.core",
    srcs = ["src"],
    iml_files = ["intellij.textmate.core.iml"],
    test_srcs = ["tests"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:joni"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:JDOM",
        "//tools/idea/.idea/libraries:joni",
        "//tools/idea/.idea/libraries:jetbrains-annotations",
        "//tools/idea/.idea/libraries:Trove4j",
    ],
)
