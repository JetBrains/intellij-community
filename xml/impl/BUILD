load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "xml",
    srcs = ["src"],
    iml_files = ["xml.iml"],
    resources = ["resources"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/xml/openapi:xml-openapi",
        "//tools/idea/xml/xml-psi-impl",
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/xml/xml-structure-view-impl",
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
        "//tools/idea/xml/openapi:xml-openapi[module]",
        "//tools/idea/.idea/libraries:Xerces",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/platform/icons[module]",
        "//tools/idea/.idea/libraries:XmlBeans",
        "//tools/idea/platform/platform-resources[module]",
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/xml/xml-psi-impl[module]",
        "//tools/idea/xml/xml-analysis-impl[module]",
        "//tools/idea/.idea/libraries:swingx",
        "//tools/idea/xml/xml-structure-view-impl[module]",
        "//tools/idea/.idea/libraries:Netty",
        "//tools/idea/platform/xdebugger-api[module]",
        "//tools/idea/platform/built-in-server-api[module]",
        "//tools/idea/platform/vcs-impl[module]",
    ],
)
