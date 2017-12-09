load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "relaxng",
    srcs = ["src"],
    iml_files = ["relaxng.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/xml/impl:xml[module]",
        "//tools/idea/xml/dom-openapi[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea/xml/relaxng/lib:rngom-20051226-patched",
        "//tools/idea/xml/relaxng/lib:isorelax",
        "//tools/idea/xml/relaxng/lib:trang-core",
        "//tools/idea/xml/relaxng/lib:jing",
    ],
)
