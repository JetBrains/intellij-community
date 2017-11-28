load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "xslt-debugger",
    srcs = ["src"],
    iml_files = ["xslt-debugger.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:spellchecker",
        "//tools/idea:images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
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
        "//tools/idea/plugins/xpath[module]",
        "//tools/idea/plugins/xslt-debugger/engine:xslt-debugger-engine[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea:xdebugger-impl[module]",
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea:xml[module]",
        "//tools/idea:platform-impl[module]",
    ],
)
