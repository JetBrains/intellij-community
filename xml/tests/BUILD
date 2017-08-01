load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "xml-tests",
    iml_files = ["xml-tests.iml"],
    tags = ["managed"],
    test_srcs = ["src"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/.idea/libraries:Xerces",
        "//tools/idea/.idea/libraries:Groovy[test]",
        "//tools/idea:java-impl[module, test]",
        "//tools/idea/plugins/ant[module, test]",
        "//tools/idea/plugins/xpath[module, test]",
        "//tools/idea/.idea/libraries:XmlBeans",
        "//tools/idea/platform/platform-tests[module, test]",
    ],
)
