load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "tasks-java",
    srcs = ["src"],
    iml_files = ["tasks-java.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:execution-impl",
        "//tools/idea:external-system-impl",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/tasks/tasks-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/java/debugger/openapi:debugger-openapi[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea:tasks-platform-api[module]",
    ],
)
