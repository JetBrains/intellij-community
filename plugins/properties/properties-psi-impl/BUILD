load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "properties-psi-impl",
    srcs = ["src"],
    iml_files = ["properties-psi-impl.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/plugins/properties/properties-psi-api"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/properties/properties-psi-api[module]",
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/idea/platform/analysis-api[module]",
        "//tools/idea/xml/xml-psi-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/platform/indexing-impl[module]",
        "//tools/idea/platform/structure-view-impl[module]",
        "//tools/idea/platform/analysis-impl[module]",
    ],
)
