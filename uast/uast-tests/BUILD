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
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/platform/external-system-impl",
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
        "//tools/idea/platform/testFramework[module]",
        "//tools/idea:jps-builders_and_others[module]",
    ],
)
