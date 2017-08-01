load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "uast-tests",
    srcs = ["src"],
    iml_files = ["uast-tests.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/uast/uast-common[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/platform/core-impl[module]",
        "//tools/idea/uast/uast-java[module]",
        "//tools/idea/java/java-runtime[module]",
        "//tools/idea/java/java-psi-impl[module]",
    ],
)
