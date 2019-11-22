load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.statsCollector",
    srcs = ["src"],
    iml_files = ["intellij.statsCollector.iml"],
    resources = ["resources"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/images:intellij.platform.images",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/xml-analysis-impl:intellij.xml.analysis.impl",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/xml/impl:intellij.xml.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/plugins/stats-collector/log-events:intellij.statsCollector.logEvents[module]",
        "//tools/idea/.idea/libraries:fluent-hc",
        "//tools/idea/plugins/stats-collector/completion-ranker:intellij.statsCollector.completionRanker[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.statsCollector.tests",
    iml_files = ["intellij.statsCollector.tests.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/idea/plugins/groovy/jps-plugin:intellij.groovy.jps",
        "//tools/idea/plugins/ant/jps-plugin:intellij.ant.jps",
        "//tools/idea/plugins/java-decompiler/plugin:intellij.java.decompiler",
        "//tools/idea/plugins/properties:intellij.properties",
        "//tools/idea/jvm/jvm-analysis-java-tests:intellij.jvm.analysis.java.tests",
        "//tools/idea/uast/uast-tests:intellij.platform.uast.tests",
        "//tools/idea/java/typeMigration:intellij.java.typeMigration",
        "//tools/idea/java/manifest:intellij.java.manifest",
        "//tools/idea/plugins/java-i18n:intellij.java.i18n",
        "//tools/idea/plugins/IntelliLang:intellij.java.langInjection",
        "//tools/idea/java/remote-servers/impl:intellij.java.remoteServers.impl",
        "//tools/idea/plugins/testng:intellij.testng",
        "//tools/idea/plugins/junit:intellij.junit",
        "//tools/idea:intellij.java.ui.tests",
        "//tools/idea/plugins/coverage:intellij.java.coverage",
        "//tools/idea/plugins/ui-designer:intellij.java.guiForms.designer",
        "//tools/idea/plugins/ByteCodeViewer:intellij.java.byteCodeViewer",
        "//tools/idea/plugins/groovy/groovy-psi:intellij.groovy.psi",
        "//tools/idea/plugins/eclipse:intellij.eclipse",
        "//tools/idea/java/plugin:intellij.java.plugin",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//tools/idea/plugins/ui-designer-core:intellij.uiDesigner",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/.idea/libraries:commons-codec[test]",
        "//tools/idea/plugins/stats-collector/log-events:intellij.statsCollector.logEvents[module, test]",
        "//tools/idea/plugins/stats-collector:intellij.statsCollector[module, test]",
    ],
)
