load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "xml-psi-api",
    srcs = ["src"],
    iml_files = ["xml-psi-api.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/platform/analysis-api[module]",
    ],
)
