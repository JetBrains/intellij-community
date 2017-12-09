load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "tasks-java",
    srcs = ["src"],
    iml_files = ["tasks-java.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/tasks/tasks-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/java/debugger/openapi:debugger-openapi[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/platform/tasks-platform-api[module]",
    ],
)
