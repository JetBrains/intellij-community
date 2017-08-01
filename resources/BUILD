load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "resources",
    srcs = ["src"],
    iml_files = ["resources.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea:platform-impl_and_others",
        "//tools/idea/community-resources",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/community-resources[module]",
        "//tools/idea/platform/util[module]",
    ],
)
