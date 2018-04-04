load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.testng.rt",
    srcs = ["src"],
    iml_files = ["intellij.testng.rt.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:TestNG",
        "//tools/idea/java/java-runtime:intellij.java.rt[module]",
    ],
)
