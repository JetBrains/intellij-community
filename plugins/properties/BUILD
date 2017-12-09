load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "properties",
    srcs = ["src"],
    iml_files = ["properties.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
        "//tools/idea/java/compiler/instrumentation-util-8",
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
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea/spellchecker[module]",
        "//tools/idea/resources[module, test]",
        "//tools/idea/xml/impl:xml[module]",
        "//tools/idea/plugins/properties/properties-psi-api[module]",
        "//tools/idea/plugins/properties/properties-psi-impl[module]",
        "//tools/idea:jps-builders_and_others[module, test]",
        "//tools/idea/platform/platform-tests[module, test]",
    ],
)
