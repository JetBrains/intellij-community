load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "properties-psi-impl",
    srcs = ["src"],
    iml_files = ["properties-psi-impl.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:xml-structure-view-impl",
        "//tools/idea:spellchecker",
        "//tools/idea:images",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/plugins/properties/properties-psi-api"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:xml-analysis-impl",
        "//tools/idea:xdebugger-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/properties/properties-psi-api[module]",
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/idea/platform/analysis-api[module]",
        "//tools/idea/xml/xml-psi-api[module]",
        "//tools/idea:xml-psi-impl[module]",
        "//tools/idea/platform/indexing-impl[module]",
        "//tools/idea/platform/structure-view-impl[module]",
        "//tools/idea/platform/analysis-impl[module]",
        "//tools/idea:lang-impl[module]",
    ],
)
