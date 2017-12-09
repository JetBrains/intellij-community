load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "xpath",
    # do not sort: must match IML order
    srcs = [
        "xpath-lang/src",
        "xpath-view/src",
        "xpath-lang/gen",
    ],
    iml_files = ["xpath.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    test_srcs = ["xpath-lang/test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/dom-impl",
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/xml/impl:xml[module]",
        "//tools/idea/plugins/xpath/xslt-rt[module]",
        "//tools/idea/platform/lvcs-impl[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/.idea/libraries:Jaxen",
        "//tools/idea/resources-en[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea/.idea/libraries:Groovy[test]",
    ],
)
