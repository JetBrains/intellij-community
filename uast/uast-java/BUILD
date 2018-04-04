load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.java.uast",
    srcs = ["src"],
    iml_files = ["intellij.java.uast.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/uast/uast-common:intellij.platform.uast"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/java-psi-api:intellij.java.psi[module]",
        "//tools/idea/uast/uast-common:intellij.platform.uast[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/java/java-psi-impl:intellij.java.psi.impl[module]",
    ],
)
