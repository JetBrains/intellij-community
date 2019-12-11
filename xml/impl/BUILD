load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.xml.impl",
    srcs = ["src"],
    iml_files = ["intellij.xml.impl.iml"],
    resources = ["resources"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
        "//tools/idea/images:intellij.platform.images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/xml/openapi:intellij.xml",
        "//tools/idea/xml/xml-psi-impl:intellij.xml.psi.impl",
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
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
        "//tools/idea/.idea/libraries:Xerces",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/platform/icons:intellij.platform.icons[module]",
        "//prebuilts/tools/common/m2/repository/org/apache/xmlbeans/xmlbeans/2.6.0:jar",
        "//prebuilts/tools/common/m2/repository/stax/stax-api/1.0.1:jar",
        "//tools/idea/platform/platform-resources:intellij.platform.resources[module]",
        "//tools/idea/platform/vcs-api:intellij.platform.vcs[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/xml/xml-psi-impl:intellij.xml.psi.impl[module]",
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl[module]",
        "//tools/idea/.idea/libraries:swingx",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl[module]",
        "//tools/idea/platform/xdebugger-api:intellij.platform.debugger[module]",
        "//tools/idea/platform/built-in-server-api:intellij.platform.builtInServer[module]",
        "//tools/idea/.idea/libraries:NanoXML",
    ],
)
