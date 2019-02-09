load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.uast.tests",
    srcs = ["src"],
    iml_files = ["intellij.platform.uast.tests.iml"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/uast/uast-common:intellij.platform.uast",
        "//tools/idea/uast/uast-java:intellij.java.uast",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:intellij.java.tests",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/plugins/IntelliLang:intellij.java.langInjection",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/idea/plugins/IntelliLang:intellij.xml.langInjection",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/uast/uast-common:intellij.platform.uast[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/platform/core-impl:intellij.platform.core.impl[module]",
        "//tools/idea/uast/uast-java:intellij.java.uast[module]",
        "//tools/idea/java/java-runtime:intellij.java.rt[module]",
        "//tools/idea/java/java-psi-impl:intellij.java.psi.impl[module]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
    ],
)
