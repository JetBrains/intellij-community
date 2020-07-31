load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.xslt.debugger",
    srcs = ["src"],
    iml_files = ["intellij.xslt.debugger.iml"],
    # do not sort: must match IML order
    resources = ["resources"],
    # do not sort: must match IML order
    test_runtime_deps = ["//tools/idea/images:intellij.platform.images"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/platform/inspect:intellij.platform.inspect",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/platform/statistics/devkit:intellij.platform.statistics.devkit",
        "//tools/idea/platform/workspaceModel/ide:intellij.platform.workspaceModel.ide",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/.idea/libraries:error-prone-annotations",
        "//tools/idea/.idea/libraries:javax.activation",
        "//tools/idea/.idea/libraries:jaxb-api",
        "//tools/idea/.idea/libraries:jaxb-runtime",
        "//tools/idea/.idea/libraries:javax.annotation-api",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-jsr223",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-json",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-templates",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-xml",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/xpath:intellij.xpath[module]",
        "//tools/idea/plugins/xslt-debugger/engine:intellij.xslt.debugger.rt[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/platform/execution-impl:intellij.platform.execution.impl[module]",
        "//tools/idea/platform/xdebugger-api:intellij.platform.debugger[module]",
        "//tools/idea:intellij.platform.debugger.impl_and_others[module]",
        "//tools/idea/xml/openapi:intellij.xml[module]",
        "//tools/idea/xml/impl:intellij.xml.impl[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools/idea/.idea/libraries:fastutil-min",
    ],
)
