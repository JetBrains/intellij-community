load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "xslt-debugger",
    srcs = ["src"],
    iml_files = ["xslt-debugger.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/xpath[module]",
        "//tools/idea/plugins/xslt-debugger/engine:xslt-debugger-engine[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/platform/vcs-api[module]",
    ],
)
