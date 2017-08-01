load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "ui-designer-core",
    srcs = ["src"],
    iml_files = ["ui-designer-core.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea:platform-impl_and_others"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/icons[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
    ],
)
