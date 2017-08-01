load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "uast-java",
    srcs = ["src"],
    iml_files = ["uast-java.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/uast/uast-common"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/java-psi-api[module]",
        "//tools/idea/uast/uast-common[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/java/java-psi-impl[module]",
    ],
)
