load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "ui-designer-jps-plugin",
    srcs = ["src"],
    iml_files = ["ui-designer-jps-plugin.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:execution-impl",
        "//tools/idea:external-system-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea:json",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:credential-store",
    ],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/java/java-runtime",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/jps/model-impl:jps-model-impl[module, test]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/java/compiler/instrumentation-util[module]",
        "//tools/idea/java/compiler/forms-compiler[module]",
        "//tools/idea/platform/forms_rt[module]",
        "//tools/idea/jps/model-serialization:jps-serialization-tests[module, test]",
    ],
)
