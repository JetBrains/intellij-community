load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "ui-designer-core",
    srcs = ["src"],
    iml_files = ["ui-designer-core.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = ["//tools/idea/images"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/lang-impl"],
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
        "//tools/idea/java/openapi[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/icons[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/spellchecker[module]",
        "//tools/idea/platform/lang-impl[module]",
    ],
)
