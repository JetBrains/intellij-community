load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.spellchecker",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["intellij.spellchecker.iml"],
    # do not sort: must match IML order
    resources = [
        "resources",
        "compatibilityResources",
    ],
    test_resources = ["testResources"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
        "//tools/idea/images:intellij.platform.images",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/inspect:intellij.platform.inspect",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/platform/statistics/devkit:intellij.platform.statistics.devkit",
        "//tools/idea/platform/workspaceModel/ide:intellij.platform.workspaceModel.ide",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
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
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/xml/openapi:intellij.xml[module]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom[module]",
        "//tools/idea/xml/impl:intellij.xml.impl[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/statistics:intellij.platform.statistics[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//prebuilts/tools/common/m2/repository/com/intellij/grazie/grazie-spellchecker/0.1.9:jar",
        "//prebuilts/tools/common/m2/repository/info/debatty/java-string-similarity/1.2.1:jar",
        "//tools/idea/xml/relaxng:intellij.relaxng[module, test]",
    ],
)
