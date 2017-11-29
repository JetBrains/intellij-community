load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "uast-tests",
    srcs = ["src"],
    iml_files = ["uast-tests.iml"],
    tags = ["managed"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/uast/uast-common",
        "//tools/idea/uast/uast-java",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:java-tests",
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea:external-system-impl",
        "//tools/idea/plugins/IntelliLang:IntelliLang-java",
        "//tools/idea/plugins/IntelliLang:IntelliLang-xml",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/uast/uast-common[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea/uast/uast-java[module]",
        "//tools/idea/java/java-runtime[module]",
        "//tools/idea/java/java-psi-impl[module]",
        "//tools/idea:testFramework[module]",
        "//tools/idea:jps-builders_and_others[module]",
    ],
)
