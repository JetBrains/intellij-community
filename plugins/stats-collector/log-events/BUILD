load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "log-events",
    srcs = ["src"],
    iml_files = ["log-events.iml"],
    tags = ["managed"],
    test_resources = ["testResources"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
    ],
)
