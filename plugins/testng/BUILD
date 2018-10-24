load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.testng",
    srcs = ["src"],
    iml_files = ["intellij.testng.iml"],
    resources = ["resources"],
    # do not sort: must match IML order
    test_runtime_deps = ["//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl"],
    test_srcs = ["testSources"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/smRunner:intellij.platform.smRunner"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi:intellij.java[module]",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/idea/java/debugger/openapi:intellij.java.debugger[module]",
        "//tools/idea/.idea/libraries:JUnit3[test]",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner[module]",
        "//tools/idea/java/java-runtime:intellij.java.rt[module]",
        "//tools/idea/plugins/testng_rt:intellij.testng.rt[module]",
        "//tools/idea/plugins/java-i18n:intellij.java.i18n[module]",
        "//tools/idea/java/execution/openapi:intellij.java.execution[module]",
        "//tools/idea:intellij.java.impl[module]",
        "//tools/idea/java/jsp-openapi:intellij.jsp[module]",
        "//tools/idea/.idea/libraries:TestNG",
        "//tools/idea/java/java-indexing-api:intellij.java.indexing[module]",
        "//tools/idea/xml/impl:intellij.xml.impl[module, test]",
        "//tools/idea/platform/smRunner:intellij.platform.smRunner[module]",
        "//tools/idea/java/typeMigration:intellij.java.typeMigration[module]",
        "//tools/idea/platform/external-system-api:intellij.platform.externalSystem[module]",
    ],
)
