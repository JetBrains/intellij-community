load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "uast-common",
    srcs = ["src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/java/java-psi-api"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/java/java-psi-api[module]",
        "//tools/idea/java/java-psi-impl[module]",
    ],
)
