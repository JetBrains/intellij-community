load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "ui-designer-core",
    srcs = ["src"],
    iml_files = ["ui-designer-core.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:images",
    ],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea:lang-impl"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:xdebugger-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:dom-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
        "//tools/idea:lvcs-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/icons[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:platform-impl[module]",
        "//tools/idea:spellchecker[module]",
        "//tools/idea:lang-impl[module]",
    ],
)
