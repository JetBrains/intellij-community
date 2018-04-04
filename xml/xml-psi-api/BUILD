load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.xml.psi",
    srcs = ["src"],
    iml_files = ["intellij.xml.psi.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel[module]",
        "//tools/idea/platform/analysis-api:intellij.platform.analysis[module]",
    ],
)
