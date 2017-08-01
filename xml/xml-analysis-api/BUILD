load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "xml-analysis-api",
    srcs = ["src"],
    iml_files = ["xml-analysis-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/xml/xml-psi-api",
        "//tools/idea/platform/analysis-api",
        "//tools/idea/platform/annotations",
        "//tools/idea/platform/core-api",
        "//tools/idea/platform/extensions",
        "//tools/idea/platform/util",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/xml/xml-psi-api[module]",
        "//tools/idea/platform/analysis-api[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/extensions[module]",
        "//tools/idea/platform/util[module]",
    ],
)
