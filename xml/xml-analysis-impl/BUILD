load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "xml-analysis-impl",
    srcs = ["src"],
    iml_files = ["xml-analysis-impl.iml"],
    resources = ["resources"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/xml/xml-analysis-api"],
    # do not sort: must match IML order
    runtime_deps = [
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
        "//tools/idea/xml/xml-psi-impl[module]",
        "//tools/idea/platform/analysis-impl[module]",
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea/xml/xml-analysis-api[module]",
        "//tools/idea/platform/projectModel-api[module]",
    ],
)
