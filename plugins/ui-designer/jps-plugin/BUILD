load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.java.guiForms.jps",
    srcs = ["src"],
    iml_files = ["intellij.java.guiForms.jps.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/java/java-runtime:intellij.java.rt",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/jps/model-api:intellij.platform.jps.model[module]",
        "//tools/idea/jps/model-impl:intellij.platform.jps.model.impl[module, test]",
        "//tools/idea/jps/model-serialization:intellij.platform.jps.model.serialization[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/idea/java/compiler/instrumentation-util:intellij.java.compiler.instrumentationUtil[module]",
        "//tools/idea/java/compiler/forms-compiler:intellij.java.guiForms.compiler[module]",
        "//tools/idea/platform/forms_rt:intellij.java.guiForms.rt[module]",
        "//tools/idea/jps/model-serialization:intellij.platform.jps.model.serialization.tests[module, test]",
        "//tools/idea/.idea/libraries:Java_Compatibility[test]",
    ],
)
