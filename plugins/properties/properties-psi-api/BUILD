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
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/RegExpSupport",
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
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/editor-ui-api[module]",
        "//tools/idea/platform/analysis-api[module]",
        "//tools/idea/platform/indexing-api[module]",
        "//tools/idea/xml/xml-psi-api[module]",
        "//tools/idea/platform/projectModel-api[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/platform/boot[module]",
    ],
)
