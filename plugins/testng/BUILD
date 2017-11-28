load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "testng",
    srcs = ["src"],
    iml_files = ["testng.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = ["//tools/idea:external-system-impl"],
    test_srcs = ["testSources"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/smRunner"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea:execution-impl[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/java/debugger/openapi:debugger-openapi[module]",
        "//tools/idea/.idea/libraries:JUnit3[test]",
        "//tools/idea/platform/testRunner[module]",
        "//tools/idea/java/java-runtime[module]",
        "//tools/idea/plugins/testng_rt[module]",
        "//tools/idea/plugins/java-i18n[module]",
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/java/jsp-openapi[module]",
        "//tools/idea/.idea/libraries:TestNG",
        "//tools/idea/java/java-indexing-api[module]",
        "//tools/idea:xml[module, test]",
        "//tools/idea/platform/smRunner[module]",
        "//tools/idea/java/typeMigration[module]",
    ],
)
