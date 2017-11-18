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
        "//tools/idea:testExtensions",
        "//tools/idea:spellchecker",
        "//tools/idea:images",
    ],
    test_srcs = ["xpath-lang/test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:dom-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
        "//tools/idea:xdebugger-impl",
        "//tools/idea:dom-openapi",
        "//tools/idea:json",
        "//tools/idea:relaxng",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea:platform-impl[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea:xml[module]",
        "//tools/idea/plugins/xpath/xslt-rt[module]",
        "//tools/idea:lvcs-impl[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/.idea/libraries:Jaxen",
        "//tools/idea/resources-en[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea:testFramework[module, test]",
        "//tools/idea/.idea/libraries:Groovy[test]",
    ],
)
