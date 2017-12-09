load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "xml-tests",
    iml_files = ["xml-tests.iml"],
    tags = ["managed"],
    test_srcs = ["src"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/xml/impl:xml[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/.idea/libraries:Xerces",
        "//tools/idea/.idea/libraries:Groovy[test]",
        "//tools/idea:java-impl[module, test]",
        "//tools/idea/plugins/ant[module, test]",
        "//tools/idea/plugins/xpath[module, test]",
        "//tools/idea/xml/dom-impl[module, test]",
        "//tools/idea/.idea/libraries:XmlBeans",
        "//tools/idea/platform/platform-tests[module, test]",
    ],
)
