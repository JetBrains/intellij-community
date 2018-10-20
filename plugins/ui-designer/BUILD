load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java.guiForms.designer",
    srcs = ["src"],
    iml_files = ["intellij.java.guiForms.designer.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    # do not sort: must match IML order
    test_runtime_deps = ["//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/plugins/java-i18n:intellij.java.i18n"],
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
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/java/compiler/forms-compiler:intellij.java.guiForms.compiler[module]",
        "//tools/idea/platform/forms_rt:intellij.java.guiForms.rt[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/java/compiler/openapi:intellij.java.compiler[module]",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl[module]",
        "//tools/idea/java/execution/openapi:intellij.java.execution[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/idea/platform/icons:intellij.platform.icons[module]",
        "//tools/idea/.idea/libraries:jgoodies-forms",
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/plugins/java-i18n:intellij.java.i18n[module]",
        "//tools/idea:intellij.java.impl[module]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/spellchecker:intellij.spellchecker[module]",
        "//tools/idea/xml/impl:intellij.xml.impl[module]",
        "//tools/idea/plugins/properties/properties-psi-api:intellij.properties.psi[module]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea:intellij.java.compiler.tests[module, test]",
        "//tools/idea/.idea/libraries:Java_Compatibility[test]",
    ],
)
