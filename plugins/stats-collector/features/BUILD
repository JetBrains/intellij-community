load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.statsCollector.features",
    srcs = ["src"],
    iml_files = ["intellij.statsCollector.features.iml"],
    resources = ["resources"],
    test_srcs = ["test"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:gson",
    ],
)

filegroup(
    name = "resources",
    srcs = glob(["resources/**"]),
    visibility = ["//visibility:public"],
)
