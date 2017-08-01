load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "tasks-java",
    srcs = ["src"],
    iml_files = ["tasks-java.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/tasks/tasks-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/java/debugger/openapi:debugger-openapi[module]",
        "//tools/idea:compiler-impl_and_others[module]",
    ],
)
