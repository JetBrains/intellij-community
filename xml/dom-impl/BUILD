load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "dom-impl",
    srcs = ["src"],
    iml_files = ["dom-impl.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/xml/dom-openapi",
        "//tools/idea/.idea/libraries:CGLIB",
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
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/xml/dom-openapi[module]",
        "//tools/idea/.idea/libraries:CGLIB",
        "//tools/idea/xml/impl:xml[module]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/platform/platform-resources[module]",
    ],
)
