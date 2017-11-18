load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "tasks-java",
    srcs = ["src"],
    iml_files = ["tasks-java.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:external-system-impl",
        "//tools/idea:execution-impl",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:lvcs-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/tasks/tasks-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/java/debugger/openapi:debugger-openapi[module]",
        "//tools/idea:compiler-impl_and_others[module]",
    ],
)
