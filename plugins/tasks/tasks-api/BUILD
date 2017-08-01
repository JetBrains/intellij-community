load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "tasks-api",
    srcs = ["src"],
    iml_files = ["tasks-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/.idea/libraries:http-client-3.1",
        "//tools/idea:platform-impl_and_others[module]",
    ],
)
