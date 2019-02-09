load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.statsCollector.logEvents",
    srcs = ["src"],
    iml_files = ["intellij.statsCollector.logEvents.iml"],
    test_resources = ["testResources"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:jetbrains-annotations",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
    ],
)
