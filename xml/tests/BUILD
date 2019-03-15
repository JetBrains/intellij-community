load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.xml.tests",
    iml_files = ["intellij.xml.tests.iml"],
    javacopts = ["-Xep:TryFailThrowable:WARN"],
    test_srcs = ["src"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/plugins/IntelliLang:intellij.xml.langInjection",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/xml/impl:intellij.xml.impl[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/idea/.idea/libraries:Xerces",
        "//tools/idea/.idea/libraries:Groovy[test]",
        "//tools/idea:intellij.java.impl[module, test]",
        "//tools/idea/plugins/ant:intellij.ant[module, test]",
        "//tools/idea/plugins/xpath:intellij.xpath[module, test]",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl[module, test]",
        "//tools/idea/.idea/libraries:XmlBeans",
        "//tools/idea/platform/platform-tests:intellij.platform.tests[module, test]",
        "//tools/idea/.idea/libraries:xml-resolver[test]",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools/idea/plugins/IntelliLang:intellij.platform.langInjection[module, test]",
        "//tools/idea/plugins/IntelliLang:intellij.java.langInjection[module]",
    ],
)
