load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "properties",
    srcs = ["src"],
    iml_files = ["properties.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/plugins/properties/properties-psi-api",
        "//tools/idea/plugins/properties/properties-psi-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/resources[module, test]",
        "//tools/idea/plugins/properties/properties-psi-api[module]",
        "//tools/idea/plugins/properties/properties-psi-impl[module]",
        "//tools/idea:compiler-impl_and_others[module]",
    ],
)
