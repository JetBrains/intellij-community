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
    test_srcs = ["xpath-lang/test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/plugins/xpath/xslt-rt[module]",
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/.idea/libraries:Jaxen",
        "//tools/idea/resources-en[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Groovy[test]",
    ],
)
