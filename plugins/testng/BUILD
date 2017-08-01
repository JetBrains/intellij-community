load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "testng",
    srcs = ["src"],
    iml_files = ["testng.iml"],
    tags = ["managed"],
    test_srcs = ["testSources"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/platform/smRunner"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea:compiler-impl_and_others[module]",
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
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/platform/smRunner[module]",
        "//tools/idea/java/typeMigration[module]",
    ],
)
