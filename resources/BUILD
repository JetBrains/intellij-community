load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "resources",
    srcs = ["src"],
    iml_files = ["resources.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:xml",
        "//tools/idea:spellchecker",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea:images",
        "//tools/idea/community-resources",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:RegExpSupport",
        "//tools/idea:xml-analysis-impl",
        "//tools/idea:xdebugger-impl",
        "//tools/idea:dom-openapi",
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
        "//tools/idea:platform-resources[module]",
        "//tools/idea:images[module]",
        "//tools/idea/community-resources[module]",
        "//tools/idea/platform/util[module]",
    ],
)
