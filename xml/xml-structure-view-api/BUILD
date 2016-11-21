load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "xml-structure-view-api",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/xml/xml-psi-api"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/xml/xml-psi-api[module]",
        "//tools/idea/platform/editor-ui-api[module]",
    ],
)
