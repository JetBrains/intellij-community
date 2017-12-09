load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "properties-psi-impl",
    srcs = ["src"],
    iml_files = ["properties-psi-impl.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/plugins/properties/properties-psi-api"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/properties/properties-psi-api[module]",
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/idea/platform/analysis-api[module]",
        "//tools/idea/xml/xml-psi-api[module]",
        "//tools/idea/xml/xml-psi-impl[module]",
        "//tools/idea/platform/indexing-impl[module]",
        "//tools/idea/platform/structure-view-impl[module]",
        "//tools/idea/platform/analysis-impl[module]",
        "//tools/idea/platform/lang-impl[module]",
    ],
)
