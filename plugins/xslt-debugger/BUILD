load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "xslt-debugger",
    srcs = ["src"],
    iml_files = ["xslt-debugger.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
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
        "//tools/idea/plugins/xpath[module]",
        "//tools/idea/plugins/xslt-debugger/engine:xslt-debugger-engine[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea/platform/xdebugger-impl[module]",
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/xml/impl:xml[module]",
        "//tools/idea/platform/platform-impl[module]",
    ],
)
