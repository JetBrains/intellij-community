load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "properties",
    srcs = ["src"],
    iml_files = ["properties.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:external-system-impl",
        "//tools/idea:execution-impl",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/plugins/properties/properties-psi-api",
        "//tools/idea/plugins/properties/properties-psi-impl",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
        "//tools/idea:xdebugger-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:dom-impl",
        "//tools/idea:lvcs-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea:platform-impl[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea:testFramework[module, test]",
        "//tools/idea:spellchecker[module]",
        "//tools/idea/resources[module, test]",
        "//tools/idea:xml[module]",
        "//tools/idea/plugins/properties/properties-psi-api[module]",
        "//tools/idea/plugins/properties/properties-psi-impl[module]",
        "//tools/idea:compiler-impl_and_others[module, test]",
    ],
)
