load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "properties-psi-api",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["properties-psi-api.iml"],
    resources = ["resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/idea/platform/analysis-api[module]",
        "//tools/idea/platform/indexing-api[module]",
        "//tools/idea/xml/xml-psi-api[module]",
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/platform/boot[module]",
    ],
)
