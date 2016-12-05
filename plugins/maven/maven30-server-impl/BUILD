load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "maven30-server-impl",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/maven/maven-server-api[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/.idea/libraries:commons-logging",
        "//tools/idea/.idea/libraries:Maven3",
        "//tools/idea/plugins/maven:maven_and_others[module]",
        "//tools/idea/plugins/maven/maven3-server-common[module]",
    ],
)
