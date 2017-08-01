load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "testng_rt",
    srcs = ["src"],
    iml_files = ["testng_rt.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:TestNG",
        "//tools/idea/java/java-runtime[module]",
    ],
)
