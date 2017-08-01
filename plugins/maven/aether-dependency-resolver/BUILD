load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "aether-dependency-resolver",
    srcs = ["src"],
    iml_files = ["aether-dependency-resolver.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/plugins/maven/lib:aether-1.1.0-all",
        "//tools/idea/plugins/maven/lib:maven-aether-provider-3.3.9-all",
    ],
)
