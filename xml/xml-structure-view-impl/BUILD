load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "xml-structure-view-impl",
    srcs = ["src"],
    iml_files = ["xml-structure-view-impl.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    visibility = ["//visibility:public"],
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
        "//tools/idea/xml/xml-structure-view-api[module]",
        "//tools/idea/xml/xml-psi-impl[module]",
        "//tools/idea/platform/structure-view-impl[module]",
        "//tools/idea/platform/core-impl[module]",
    ],
)
