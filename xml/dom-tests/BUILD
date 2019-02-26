load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.xml.dom.tests",
    iml_files = ["intellij.xml.dom.tests.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    test_srcs = ["tests"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl[module]",
        "//tools/idea/.idea/libraries:CGLIB",
        "//tools/idea/xml/impl:intellij.xml.impl[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/idea/java/java-psi-impl:intellij.java.psi.impl[module]",
        "//tools/idea:intellij.java.impl[module, test]",
    ],
)
