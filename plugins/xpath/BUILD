load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.xpath",
    # do not sort: must match IML order
    srcs = [
        "xpath-lang/src",
        "xpath-view/src",
        "xpath-lang/gen",
    ],
    iml_files = ["intellij.xpath.iml"],
    # do not sort: must match IML order
    resources = [
        "xpath-lang/resources",
        "xpath-lang/compatibilityResources",
    ],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
        "//tools/idea/images:intellij.platform.images",
    ],
    test_srcs = ["xpath-lang/test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/xml/openapi:intellij.xml[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/xml/impl:intellij.xml.impl[module]",
        "//tools/idea/plugins/xpath/xslt-rt:intellij.xslt.debugger.rt[module]",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl[module]",
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/.idea/libraries:Jaxen",
        "//tools/idea/resources-en:intellij.java.resources.en[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/.idea/libraries:NanoXML",
    ],
)
